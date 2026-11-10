# Ktor Client Scope (automation)

## Overview
Minimal Ktor bootstrap shared by the `ci-actions` executables. Provides the `KtorClientScope`
interface the action entry points are written against, a factory for a JSON-configured
`HttpClient`, and a `ktorClientScope { }` helper that guarantees the client is released.

## Interface
- `interface KtorClientScope { val ktorClient: HttpClient }`
- `fun newKtorClient(): HttpClient` — installs `ContentNegotiation` with `Json { ignoreUnknownKeys = true; isLenient = true }`
- `suspend fun ktorClientScope(block: suspend KtorClientScope.() -> Unit)`

## Invariants
- `ktorClientScope` creates exactly one `HttpClient` per invocation (`by lazy` inside the call) and
  `use { }`-closes it when `block` returns or throws.
- No base URL, auth header or timeout is configured here — those belong to the callers
  (`GitHubServiceImpl` owns its own base URL and bearer header).

## Side Effects
- Allocates a Ktor `HttpClient` (engine + connection pool) per invocation.
- Shuts the client down on scope exit; a client escaping the scope is a bug.

## Verification
- Run: `./gradlew :ci-actions:base:test`
