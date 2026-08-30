const REQUEST_TIMEOUT_MS = 10_000;
const DEFAULT_MAX_REQUEST_BODY_BYTES = 32 * 1024;
const KNOWLEDGE_IMPORT_MAX_REQUEST_BODY_BYTES = 4 * 1024 * 1024;
const KNOWLEDGE_IMPORT_PATH = "/api/v1/admin/knowledge/ingestion-imports";

const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "forwarded",
  "keep-alive",
  "proxy-connection",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

type ProxyFetch = (request: Request) => Promise<Response>;

type ProxyOptions = {
  fetchImpl?: ProxyFetch;
  timeoutMs?: number;
  trustedClientAddress?: string | null;
  proxySharedSecret?: string;
};

export function isApiProxyPath(pathname: string): boolean {
  return pathname === "/api" || pathname.startsWith("/api/");
}

export function resolveBackendOrigin(rawOrigin: string | undefined, requestUrl: string): string {
  if (!rawOrigin || rawOrigin !== rawOrigin.trim()) throw new Error("invalid backend origin");

  let backend: URL;
  let incoming: URL;
  try {
    backend = new URL(rawOrigin);
    incoming = new URL(requestUrl);
  } catch {
    throw new Error("invalid backend origin");
  }

  if (
    !["http:", "https:"].includes(backend.protocol) ||
    backend.username ||
    backend.password ||
    backend.search ||
    backend.hash ||
    backend.pathname !== "/" ||
    backend.origin === incoming.origin
  ) {
    throw new Error("invalid backend origin");
  }

  const loopbackHttp = incoming.protocol === "http:" && backend.protocol === "http:"
    && isLoopback(incoming.hostname) && isLoopback(backend.hostname);
  if (
    (incoming.protocol !== "https:" && !loopbackHttp) ||
    (incoming.protocol === "https:" && backend.protocol !== "https:")
  ) {
    throw new Error("invalid backend origin");
  }

  return backend.origin;
}

export async function proxyApiRequest(
  request: Request,
  rawOrigin: string | undefined,
  options: ProxyOptions = {},
): Promise<Response> {
  const traceId = crypto.randomUUID();
  const incoming = new URL(request.url);
  const browserOrigin = request.headers.get("origin");
  if (browserOrigin && !sameOrigin(browserOrigin, incoming.origin)) {
    return proxyError(403, "BACKEND_PROXY_ORIGIN_REJECTED", "요청 출처를 확인할 수 없습니다.", traceId);
  }
  let backendOrigin: string;
  try {
    backendOrigin = resolveBackendOrigin(rawOrigin, request.url);
  } catch {
    return proxyError(503, "BACKEND_PROXY_CONFIGURATION_INVALID", "API 연결 설정을 확인할 수 없습니다.", traceId);
  }

  const target = new URL(backendOrigin);
  target.pathname = incoming.pathname;
  target.search = incoming.search;

  const proxySharedSecret = options.proxySharedSecret;
  if (!proxySharedSecret || !/^[a-f0-9]{64}$/.test(proxySharedSecret)) {
    return proxyError(503, "BACKEND_PROXY_CONFIGURATION_INVALID", "API 연결 설정을 확인할 수 없습니다.", traceId);
  }

  const maxRequestBodyBytes = incoming.pathname === KNOWLEDGE_IMPORT_PATH
    ? KNOWLEDGE_IMPORT_MAX_REQUEST_BODY_BYTES
    : DEFAULT_MAX_REQUEST_BODY_BYTES;
  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null && (!/^\d+$/.test(declaredLength) || Number(declaredLength) > maxRequestBodyBytes)) {
    return proxyError(413, "BACKEND_PROXY_REQUEST_TOO_LARGE", "요청 본문이 허용 크기를 초과했습니다.", traceId);
  }

  const timeoutSignal = AbortSignal.timeout(options.timeoutMs ?? REQUEST_TIMEOUT_MS);
  const bodyLimitController = new AbortController();
  let bodyLimitExceeded = false;
  const signal = AbortSignal.any([request.signal, timeoutSignal, bodyLimitController.signal]);
  try {
    const headers = sanitizedRequestHeaders(request.headers);
    headers.set("X-Alzs-Proxy-Secret", proxySharedSecret);
    headers.set("X-Alzs-Client-Key", await signedClientKey(proxySharedSecret, options.trustedClientAddress));
    const body = request.method === "GET" || request.method === "HEAD" || request.body === null
      ? undefined
      : limitedBody(request.body, maxRequestBodyBytes, () => {
        bodyLimitExceeded = true;
        bodyLimitController.abort(new Error("request body too large"));
      });
    const requestInit: RequestInit & { duplex?: "half" } = {
      method: request.method,
      headers,
      body,
      redirect: "manual",
      signal,
    };
    if (body) requestInit.duplex = "half";
    const upstreamRequest = new Request(target, requestInit);
    const upstream = await (options.fetchImpl ?? ((value) => fetch(value)))(upstreamRequest);
    if (upstream.status >= 300 && upstream.status < 400) {
      upstream.body?.cancel().catch(() => undefined);
      return proxyError(502, "BACKEND_INVALID_RESPONSE", "API 서버 응답을 안전하게 처리할 수 없습니다.", traceId);
    }

    const responseHeaders = sanitizedResponseHeaders(upstream.headers);
    responseHeaders.set("Cache-Control", "no-store");
    if (!responseHeaders.has("X-Trace-Id")) responseHeaders.set("X-Trace-Id", traceId);
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  } catch {
    if (bodyLimitExceeded) {
      return proxyError(413, "BACKEND_PROXY_REQUEST_TOO_LARGE", "요청 본문이 허용 크기를 초과했습니다.", traceId);
    }
    if (timeoutSignal.aborted && !request.signal.aborted) {
      return proxyError(504, "BACKEND_TIMEOUT", "API 서버 응답 시간이 초과되었습니다.", traceId);
    }
    return proxyError(502, "BACKEND_UNAVAILABLE", "API 서버에 연결할 수 없습니다.", traceId);
  }
}

function sanitizedRequestHeaders(source: Headers): Headers {
  const headers = new Headers(source);
  const connectionHeaders = (source.get("connection") ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean);

  for (const name of [...headers.keys()]) {
    const normalized = name.toLowerCase();
    if (
      HOP_BY_HOP_HEADERS.has(normalized) ||
      connectionHeaders.includes(normalized) ||
      normalized === "host" ||
      normalized === "content-length" ||
      normalized === "cookie" ||
      normalized === "origin" ||
      normalized === "referer" ||
      normalized === "x-real-ip" ||
      normalized.startsWith("x-forwarded-") ||
      normalized.startsWith("cf-") ||
      normalized.startsWith("x-alzs-") ||
      normalized.startsWith("oai-authenticated-user-")
    ) {
      headers.delete(name);
    }
  }
  return headers;
}

function limitedBody(
  source: ReadableStream<Uint8Array>,
  maxBytes: number,
  onLimitExceeded: () => void,
): ReadableStream<Uint8Array> {
  let bytesRead = 0;
  return source.pipeThrough(new TransformStream<Uint8Array, Uint8Array>({
    transform(chunk, controller) {
      bytesRead += chunk.byteLength;
      if (bytesRead > maxBytes) {
        onLimitExceeded();
        controller.error(new Error("request body too large"));
        return;
      }
      controller.enqueue(chunk);
    },
  }));
}

async function signedClientKey(secretHex: string, trustedClientAddress: string | null | undefined): Promise<string> {
  const source = trustedClientAddress && /^[0-9a-f:.]{1,64}$/i.test(trustedClientAddress)
    ? trustedClientAddress.toLowerCase()
    : "anonymous";
  const key = await crypto.subtle.importKey(
    "raw",
    Uint8Array.from(secretHex.match(/.{2}/g) ?? [], (value) => Number.parseInt(value, 16)),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`alzs-client-rate-v1:${source}`));
  return Array.from(new Uint8Array(signature), (value) => value.toString(16).padStart(2, "0")).join("");
}

function sanitizedResponseHeaders(source: Headers): Headers {
  const headers = new Headers(source);
  const connectionHeaders = (source.get("connection") ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean);
  for (const name of [...headers.keys()]) {
    const normalized = name.toLowerCase();
    if (
      HOP_BY_HOP_HEADERS.has(normalized) ||
      connectionHeaders.includes(normalized) ||
      normalized === "set-cookie" ||
      normalized === "location"
    ) {
      headers.delete(name);
    }
  }
  return headers;
}

function proxyError(status: number, code: string, message: string, traceId: string): Response {
  return Response.json({
    success: false,
    status,
    code,
    message,
    data: null,
    errors: [],
    timestamp: new Date().toISOString(),
    traceId,
  }, {
    status,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      "X-Trace-Id": traceId,
    },
  });
}

function isLoopback(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "[::1]";
}

function sameOrigin(value: string, expectedOrigin: string): boolean {
  try {
    const parsed = new URL(value);
    return parsed.origin === expectedOrigin && parsed.pathname === "/" && !parsed.search && !parsed.hash;
  }
  catch { return false; }
}
