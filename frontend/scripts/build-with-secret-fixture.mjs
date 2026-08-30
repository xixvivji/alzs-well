import { spawnSync } from "node:child_process";

const npmCommand = process.platform === "win32" ? "npm.cmd" : "npm";
const proxySecretFixture = "abcdef0123456789".repeat(4);
const result = spawnSync(npmCommand, ["run", "build"], {
  env: {
    ...process.env,
    BACKEND_PROXY_SHARED_SECRET: proxySecretFixture,
  },
  stdio: "inherit",
});

if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
