const base = (process.env.AVIANTO_BASE_URL ?? "http://127.0.0.1:8080").replace(/\/$/, "");
const username = process.env.AVIANTO_USERNAME;
const password = process.env.AVIANTO_PASSWORD;

if (!username || !password) {
  throw new Error("Definí AVIANTO_USERNAME y AVIANTO_PASSWORD para el smoke test.");
}

async function request(path, init = {}) {
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
  const body = await response.text();
  if (!response.ok) throw new Error(`${init.method ?? "GET"} ${path} devolvió ${response.status}: ${body}`);
  return body ? JSON.parse(body) : undefined;
}

const session = await request("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ username, password }),
});
const auth = { Authorization: `Bearer ${session.accessToken}` };

for (const path of ["/api/auth/me", "/api/ventas", "/api/fichas", "/api/dashboard"]) {
  await request(path, { headers: auth });
  console.log(`OK ${path}`);
}
