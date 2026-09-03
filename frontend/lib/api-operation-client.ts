import { ApiClientError, apiRequest } from "./api";
import {
  API_OPERATION_CATALOG,
  type ApiOperationDefinition,
} from "./generated/api-operation-catalog";

export type ApiOperationKey = (typeof API_OPERATION_CATALOG)[number]["key"];
export type ApiPathValues = Record<string, string | number>;
export type ApiQueryValues = Record<string, string | number | boolean | null | undefined>;
export type ApiOperationAuth = {
  capability?: string;
  staffCapability?: string;
  demoRunId?: string;
  accessToken?: string;
  idempotencyKey?: string;
};

export type InvokeApiOperationOptions = ApiOperationAuth & {
  path?: ApiPathValues;
  query?: ApiQueryValues;
  body?: unknown;
  timeoutMs?: number;
  signal?: AbortSignal;
};

const catalogByKey = new Map<string, ApiOperationDefinition>(
  API_OPERATION_CATALOG.map((operation) => [operation.key, operation]),
);

export function findApiOperation(key: string): ApiOperationDefinition {
  const operation = catalogByKey.get(key);
  if (!operation) throw new ApiClientError("parse", `등록되지 않은 API operation입니다: ${key}`);
  return operation;
}

export function buildApiOperationPath(
  operation: ApiOperationDefinition,
  values: ApiPathValues = {},
  query: ApiQueryValues = {},
): string {
  const path = operation.path.replace(/\{([^}]+)\}/g, (_, parameter: string) => {
    const value = values[parameter];
    if (value === undefined || value === null || String(value).length === 0) {
      throw new ApiClientError("parse", `필수 경로 값이 없습니다: ${parameter}`);
    }
    return encodeURIComponent(String(value));
  });
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null) search.set(key, String(value));
  }
  const suffix = search.toString();
  return suffix ? `${path}?${suffix}` : path;
}

export async function invokeApiOperation<T = unknown>(
  key: ApiOperationKey | string,
  options: InvokeApiOperationOptions = {},
) {
  const operation = findApiOperation(key);
  if (operation.implementation !== "IMPLEMENTED") {
    const reason = operation.implementation === "REFERENCE_ONLY"
      ? "외부기관 참고 API는 실행 경로를 제공하지 않습니다."
      : "아직 구현되지 않은 API입니다.";
    throw new ApiClientError("parse", reason);
  }

  const { path, query, body, ...auth } = options;
  return apiRequest<T>(buildApiOperationPath(operation, path, query), {
    method: operation.method,
    sameOriginOnly: operation.authorityMode === "BEARER" && !auth.accessToken,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    ...auth,
  });
}
