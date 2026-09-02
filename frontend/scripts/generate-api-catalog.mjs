import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(frontendRoot, "..");
const specPath = join(repositoryRoot, "docs", "FINAL_BACKEND_API_SPEC.md");
const controllerRoot = join(repositoryRoot, "backend", "src", "main", "java", "com", "alzswell");
const outputPath = join(frontendRoot, "lib", "generated", "api-operation-catalog.ts");

const spec = readFileSync(specPath, "utf8");
const catalogSection = spec.split("### 3.3 도메인별 API", 2)[1]?.split("### 3.4 구현 순서", 1)[0];
if (!catalogSection) throw new Error("API 카탈로그 3.3 절을 찾을 수 없습니다.");

const rowPattern = /^\|\s*(P0-A|P0-B|P1|P2)\s*\|\s*(GET|POST|PUT|PATCH|DELETE)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*(OWNED|EXTERNAL_INTEGRATION|REFERENCE_ONLY)\s*\|$/;
const headingPattern = /^####\s+3\.3\.[^\s]+\s+(.+?)\s+—\s+\d+개\s*$/;
const documented = [];
let domain = "기타";
for (const line of catalogSection.split("\n")) {
  const heading = line.match(headingPattern);
  if (heading) domain = heading[1].trim();
  const row = line.match(rowPattern);
  if (!row) continue;
  const [, priority, method, rawPath, purpose, boundary] = row;
  documented.push({ priority, method, path: rawPath.trim(), purpose: purpose.trim(), boundary, domain });
}
if (documented.length !== 283) throw new Error(`문서 operation 수가 283이 아니라 ${documented.length}입니다.`);

const controllers = walk(controllerRoot).filter((path) => path.endsWith("Controller.java"));
const implemented = [];
for (const controller of controllers) {
  const source = readFileSync(controller, "utf8");
  const base = source.match(/@RequestMapping\(\s*"([^"]+)"\s*\)/)?.[1] ?? "";
  const mapping = /@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*"([^"]*)"\s*\))?/g;
  for (const match of source.matchAll(mapping)) {
    implemented.push({
      method: match[1].toUpperCase(),
      path: normalizePath(`${base}${match[2] ?? ""}`),
      controller: relative(repositoryRoot, controller),
    });
  }
}
const implementedKeys = new Set(implemented.map(({ method, path }) => `${method} ${path}`));
if (implemented.length !== 239 || implementedKeys.size !== 239) {
  throw new Error(`구현 operation 수가 239가 아닙니다: total=${implemented.length}, unique=${implementedKeys.size}`);
}

const documentedKeys = new Set(documented.map(({ method, path }) => `${method} ${path}`));
const codeOnly = implemented.filter(({ method, path }) => !documentedKeys.has(`${method} ${path}`));
const implementedDocumented = documented.filter(({ method, path }) => implementedKeys.has(`${method} ${path}`));
if (implementedDocumented.length !== 238 || codeOnly.length !== 1) {
  throw new Error(`카탈로그·코드 교집합이 예상과 다릅니다: documented=${implementedDocumented.length}, codeOnly=${codeOnly.length}`);
}

const operations = [
  ...documented.map((item) => definition(item, implementedKeys.has(`${item.method} ${item.path}`))),
  ...codeOnly.map((item) => definition(codeOnlyMetadata(item), true)),
];

const output = `// 이 파일은 scripts/generate-api-catalog.mjs로 생성합니다. 직접 수정하지 마세요.\n\nexport type ApiMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";\nexport type ApiPriority = "P0-A" | "P0-B" | "P1" | "P2";\nexport type ApiBoundary = "OWNED" | "EXTERNAL_INTEGRATION" | "REFERENCE_ONLY";\nexport type ApiImplementation = "IMPLEMENTED" | "PLANNED" | "REFERENCE_ONLY";\nexport type ApiAudience = "PUBLIC" | "CUSTOMER" | "STAFF" | "ADMIN";\nexport type ApiAuthorityMode = "PUBLIC" | "DEMO_CAPABILITY" | "STAFF_BOOTSTRAP" | "BEARER";\n\nexport type ApiOperationDefinition = {\n  key: string;\n  method: ApiMethod;\n  path: string;\n  purpose: string;\n  domain: string;\n  domainId: string;\n  priority: ApiPriority;\n  boundary: ApiBoundary;\n  implementation: ApiImplementation;\n  audience: ApiAudience;\n  authorityMode: ApiAuthorityMode;\n  pathParameters: readonly string[];\n  externalActionAllowed: false;\n};\n\nexport const API_OPERATION_CATALOG = ${JSON.stringify(operations, null, 2)} as const satisfies readonly ApiOperationDefinition[];\n`;

if (process.argv.includes("--check")) {
  let current = "";
  try { current = readFileSync(outputPath, "utf8"); } catch { /* 생성 전 */ }
  if (current !== output) {
    console.error("생성된 API operation 카탈로그가 최신이 아닙니다. npm run catalog:generate를 실행하세요.");
    process.exit(1);
  }
  console.log("API operation catalog valid: 283 documented, 239 implemented, 1 code-only operation");
} else {
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, output);
  console.log(`generated ${relative(repositoryRoot, outputPath)} (${operations.length} operations)`);
}

function definition(item, isImplemented) {
  const implementation = isImplemented ? "IMPLEMENTED" : item.boundary === "REFERENCE_ONLY" ? "REFERENCE_ONLY" : "PLANNED";
  return {
    key: `${item.method} ${item.path}`,
    method: item.method,
    path: item.path,
    purpose: item.purpose,
    domain: item.domain,
    domainId: slug(item.domain),
    priority: item.priority,
    boundary: item.boundary,
    implementation,
    audience: audience(item.path),
    authorityMode: authorityMode(item.method, item.path),
    pathParameters: [...item.path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]),
    externalActionAllowed: false,
  };
}

function codeOnlyMetadata(item) {
  if (item.path.endsWith("/alerts/{alertId}/defer")) return {
    ...item, priority: "P0-A", purpose: "고객 알림 확인 유예", boundary: "OWNED", domain: "고객 확인·이의신청",
  };
  if (item.path === "/api/v1/system/core-readiness") return {
    ...item, priority: "P0-A", purpose: "핵심 업무 readiness 확인", boundary: "OWNED", domain: "시스템·데모",
  };
  if (item.path === "/api/v1/system/ai-readiness") return {
    ...item, priority: "P0-A", purpose: "AI 의존성 readiness 확인", boundary: "OWNED", domain: "시스템·데모",
  };
  return {
    ...item, priority: "P0-A", purpose: "직원 데모 capability 발급", boundary: "OWNED", domain: "시스템·데모",
  };
}

function authorityMode(method, path) {
  if (path.startsWith("/api/v1/system/") || path === "/api/v1/demo/scenarios"
      || (method === "POST" && path === "/api/v1/demo/sessions")
      || path === "/api/v1/auth/login" || path === "/api/v1/auth/token/refresh") return "PUBLIC";
  if (path === "/api/v1/demo/staff/sessions/{sessionId}/capability") return "STAFF_BOOTSTRAP";
  if (path.startsWith("/api/v1/demo/sessions/{sessionId}")) return "DEMO_CAPABILITY";
  return "BEARER";
}

function audience(path) {
  if (path.startsWith("/api/v1/demo/") && (path.includes("/staff/") || path.includes("/cases/"))) return "STAFF";
  if (path.startsWith("/api/v1/system/") || path.startsWith("/api/v1/demo/")) return "PUBLIC";
  if (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/audit/")
      || path.startsWith("/api/v1/compliance/") || path.startsWith("/api/v1/detection-")) return "ADMIN";
  if (path.startsWith("/api/v1/staff/") || path.startsWith("/api/v1/staff-access-")) return "STAFF";
  return "CUSTOMER";
}

function slug(value) {
  return value.toLowerCase().replace(/[^a-z0-9가-힣]+/g, "-").replace(/^-|-$/g, "");
}

function normalizePath(value) {
  const normalized = value.replace(/\/{2,}/g, "/");
  return normalized.startsWith("/") ? normalized : `/${normalized}`;
}

function walk(directory) {
  const result = [];
  for (const name of readdirSync(directory)) {
    const path = join(directory, name);
    if (statSync(path).isDirectory()) result.push(...walk(path));
    else result.push(path);
  }
  return result;
}
