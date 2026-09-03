import fs from "node:fs";
import path from "node:path";

const frontendRoot = path.resolve(import.meta.dirname, "..");
const catalogPath = path.join(frontendRoot, "lib/generated/api-operation-catalog.ts");
const catalogSource = fs.readFileSync(catalogPath, "utf8");
const catalogMatch = catalogSource.match(
  /export const API_OPERATION_CATALOG = (\[[\s\S]*\]) as const satisfies/,
);

if (!catalogMatch) {
  throw new Error("생성된 API 카탈로그를 읽을 수 없습니다.");
}

const catalog = JSON.parse(catalogMatch[1]);
const sourceFiles = [];

function collectSourceFiles(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (["node_modules", ".next", "generated"].includes(entry.name)) continue;
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      collectSourceFiles(entryPath);
    } else if (/\.(?:ts|tsx|mjs)$/.test(entry.name)) {
      sourceFiles.push(entryPath);
    }
  }
}

collectSourceFiles(frontendRoot);

const operationPattern = /["'`]((?:GET|POST|PUT|PATCH|DELETE) \/api\/v1\/[^"'`]+)["'`]/g;
const references = new Map();

for (const sourceFile of sourceFiles) {
  const source = fs.readFileSync(sourceFile, "utf8");
  for (const match of source.matchAll(operationPattern)) {
    const relativePath = path.relative(frontendRoot, sourceFile);
    const locations = references.get(match[1]) ?? [];
    locations.push(relativePath);
    references.set(match[1], locations);
  }
}

const implemented = catalog.filter((operation) => operation.implementation === "IMPLEMENTED");
const connected = implemented.filter((operation) => references.has(operation.key));
const unconnected = implemented.filter((operation) => !references.has(operation.key));
const unexpected = [...references.keys()].filter(
  (operationKey) => !catalog.some((operation) => operation.key === operationKey),
);

function groupBy(items, key) {
  return Object.fromEntries(
    [...new Set(items.map((item) => item[key]))]
      .sort()
      .map((value) => [value, items.filter((item) => item[key] === value).length]),
  );
}

const report = {
  generatedAt: new Date().toISOString(),
  catalogOperations: catalog.length,
  implementedOperations: implemented.length,
  connectedOperations: connected.length,
  unconnectedOperations: unconnected.length,
  coveragePercent: Number(((connected.length / implemented.length) * 100).toFixed(1)),
  unconnectedByAudience: groupBy(unconnected, "audience"),
  unconnectedByDomain: groupBy(unconnected, "domain"),
  connected: connected.map((operation) => ({
    key: operation.key,
    audience: operation.audience,
    domain: operation.domain,
    sources: [...new Set(references.get(operation.key))].sort(),
  })),
  unconnected: unconnected.map((operation) => ({
    key: operation.key,
    audience: operation.audience,
    domain: operation.domain,
    purpose: operation.purpose,
  })),
  unexpected,
};

if (process.argv.includes("--json")) {
  console.log(JSON.stringify(report, null, 2));
} else {
  console.log(
    `구현 API ${report.implementedOperations}개 중 프론트 연결 ${report.connectedOperations}개 `
      + `(${report.coveragePercent}%), 미연결 ${report.unconnectedOperations}개`,
  );
  console.log("미연결 역할별:", report.unconnectedByAudience);
  console.log("미연결 도메인별:", report.unconnectedByDomain);
  for (const operation of report.unconnected) {
    console.log(`- [${operation.audience}] ${operation.key} · ${operation.purpose}`);
  }
  if (unexpected.length > 0) {
    console.log("카탈로그에 없는 참조:", unexpected);
  }
}
