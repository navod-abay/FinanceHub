# FinanceHub Sync Operation (Client-Authoritative Android + FastAPI)

## 1. Purpose and Design Goals

FinanceHub sync is built for an offline-first Android app where the client remains the source of truth for user intent, and the server acts as a durable backup plus cross-device data source.

Primary goals:
- Keep the app fully usable while offline.
- Push local changes in dependency-safe batches.
- Avoid duplicate server objects when requests are retried.
- Pull server changes back without overwriting unsynced local edits.
- Preserve referential integrity for related objects (expense + tags + cross refs, wishlist + tags).

The implementation currently uses an atomic group push model (`/api/v1/sync/atomic`) plus delta pull (`/api/v1/sync/updated-data`).

## 2. High-Level Architecture

### Client (Android/Kotlin)
- Local DB: Room (`AppDatabase`) with sync metadata on entities.
- Sync orchestrator: `SyncManager`.
- Grouping logic: `AtomicSyncGroupBuilder`.
- Background execution: WorkManager (`SyncWorker`, `ConnectivityMonitorWorker`, `SyncTrigger`).
- API client: Retrofit (`FinanceHubApiService`).

### Server (FastAPI/Python)
- HTTP API under `/api/v1`.
- Atomic endpoint: `POST /api/v1/sync/atomic`.
- Delta endpoint: `GET /api/v1/sync/updated-data?since=...`.
- Atomic transaction engine: `AtomicSyncService`.
- DB layer: SQLAlchemy models with UUID/string server IDs.
- Idempotency store: `entity_mappings` table.

## 3. Data Model and Identity Strategy

### 3.1 Local IDs vs Server IDs

The client stores local primary keys (mostly Int/Long) and, after sync, stores `serverId` (UUID-like String) for each entity.

Entity mapping happens in two layers:
- Client-side mapping table (`entity_mappings` in Room): local ID -> server ID.
- Server-side mapping table (`entity_mappings` in Postgres): clientId -> serverId for idempotency/retry safety.

### 3.2 Client Sync Metadata

Most client entities include:
- `serverId: String?`
- `lastSyncedAt: Long?`
- `pendingSync: Boolean`
- `syncOperation: String?` (`CREATE`, `UPDATE`, `DELETE`)
- `createdAt`, `updatedAt`

This metadata drives both push selection and pull conflict resolution.

### 3.3 Sync Group Tables (Client)

The Android client persists sync batches in two tables:
- `sync_groups`: group lifecycle (`PENDING`, `SUCCESS`, `FAILED`).
- `sync_group_entities`: membership list of entities per group.

This allows deterministic batch construction and later per-group result processing.

## 4. End-to-End Sync Lifecycle

`SyncManager.performFullSync()` executes three phases:

1. Push local pending changes (atomic groups).
2. Pull server updates since last successful sync timestamp.
3. Cleanup local old data (90-day retention).

### 4.1 Phase A: Marking Changes as Pending (Client)

Repositories mark changed entities for sync as part of regular write operations:
- Example paths: expense create/update/delete, tag updates, target updates, relationship updates.
- `SyncManager.markForSync(...)` sets `pendingSync = true` and operation type in the relevant DAO.

Result: local state is immediately updated and visible to user; sync is deferred.

### 4.2 Phase B: Build Atomic Groups (Client)

`AtomicSyncGroupBuilder.createPendingSyncGroups()` creates dependency-aware groups:
- Expense groups: expense + pending expense_tag links + related pending tags.
- Standalone tag groups (if not already bundled with pending expense flow).
- Target groups.
- Graph edge groups.
- Wishlist groups: wishlist item + pending wishlist_tag links + related pending tags.

Group type examples:
- `EXPENSE_WITH_TAGS`
- `TAG`
- `TARGET`
- `GRAPH_EDGE`
- `WISHLIST_WITH_TAGS`

### 4.3 Phase C: Convert Groups to Network Operations (Client)

For each `SyncGroupEntity`, `SyncManager` builds one network operation object, then flattens all operations in that group.

Operation families:
- Expense: create/update/delete
- Tag: create/update/delete
- Target: create/update/delete
- ExpenseTag: create/delete
- GraphEdge: create/update/delete
- Wishlist: create/update/delete
- WishlistTag: create/delete

Important behavior:
- For relation creates (expense_tag, graph_edge, wishlist_tag), the client tries to resolve referenced IDs to server IDs using local mapping table first, then entity table `serverId`, then falls back to local ID string.

### 4.4 Phase D: Atomic Push Request (Client -> Server)

Client calls:
- `POST /api/v1/sync/atomic`

Request body (`AtomicSyncRequest`):
- `groups[]`
  - `groupId`
  - `groupType`
  - `operations[]` (discriminated by `type`)
- `clientTimestamp`

Server response (`AtomicSyncResponse`):
- `groupResults[]`
  - `groupId`
  - `success`
  - `error` (if any)
  - `entityMappings[]` (`entityType`, `clientId`, `serverId`)
  - `rolledBack`
- `serverTimestamp`

### 4.5 Phase E: Group Processing and ACID Guarantees (Server)

`AtomicSyncService.process_groups()` processes each group independently.

For each group:
- Create nested transaction/savepoint (`begin_nested`).
- Execute operations in order.
- If all succeed: commit savepoint.
- If any fail: rollback savepoint and mark group failed.

After all groups:
- Outer transaction is committed once in route handler.

Semantics:
- Atomicity within group: all-or-nothing.
- Isolation between groups: one group failing does not rollback successful groups.

### 4.6 Phase F: Idempotency (Server)

On create operations, server checks persistent mapping table first:
- If `entity_type + client_id` exists and entity exists, return existing `server_id`.
- If mapping exists but entity is missing (orphan), server removes mapping and recreates entity.
- If no mapping exists, create entity and save mapping.

This enables safe retries when client times out after server commit.

### 4.7 Phase G: Apply Push Results Locally (Client)

`SyncManager.processAtomicSyncResults(...)`:
- Marks group `SUCCESS` or `FAILED`.
- Persists returned `entityMappings` into local Room `entity_mappings`.
- Calls `clearPendingSyncForGroup(groupId)` for successful groups.

### 4.8 Phase H: Pull Delta (Client <- Server)

Client calls:
- `GET /api/v1/sync/updated-data?since=<lastSyncTimestampMs>`

Server returns `UpdatedDataResponse` lists for all entity types.

Client applies per-entity conflict strategy:
- If local row does not exist: insert from server.
- If local row has `pendingSync = true`: keep local changes (client-authoritative).
- Else if server `updatedAt` is newer: apply server values.
- Else: keep local values and refresh sync metadata.

This pattern is implemented for expenses, tags, targets, graph edges, wishlist, and partially for cross-ref entities.

### 4.9 Phase I: Timestamp and Retention

On successful pull:
- Client updates `last_sync_timestamp` in SharedPreferences.

Cleanup:
- Client deletes data older than 90 days via DAO cleanup methods.

## 5. API Surface Relevant to Sync

### 5.1 Current Primary Path
- `POST /api/v1/sync/atomic`
- `GET /api/v1/sync/updated-data`

### 5.2 Legacy/Secondary Paths Still Present
- `GET /api/v1/sync/delta`
- `POST /api/v1/sync/push`
- `POST /api/v1/sync/full`
- `POST /api/v1/sync/batch/*` endpoints

The Android `SyncManager` currently uses atomic push + updated-data pull for full sync.

## 6. WorkManager and Runtime Triggering

### Connectivity monitoring
- `ConnectivityMonitorWorker` runs periodically and calls `health` endpoint.
- Updates global reachability in `ConnectivityState`.

### Sync triggering
- Manual or background triggers call `SyncTrigger.triggerSync(...)`.
- `SyncWorker` checks reachability, then runs `SyncManager.performFullSync()`.
- Worker returns retry on failure, enabling backoff/retry policy.

## 7. Conflict Resolution Policy

Policy intent is client-authoritative:
- Unsynced local changes win over incoming server updates.
- Server updates are applied only when local row is not pending sync and server is newer.

Practical effect:
- User edits offline are preserved until pushed.
- Pull phase does not clobber local pending edits.

## 8. Failure Handling and Recovery

### Client-side
- Push and pull wrapped in try/catch with state transitions (`IDLE`, `SYNCING`, `ERROR`).
- Worker retries failed sync.

### Server-side
- Per-group savepoints prevent partial writes inside one group.
- Fatal route exceptions rollback outer transaction and return HTTP 500.

### Network partitions
- Safe retry on creates via server idempotency mapping table.

## 9. Observability

Server has extensive logging in atomic route/service:
- Request summaries (group counts, operation counts).
- Per-group success/failure and rollback status.
- Mapping creation/hits.
- Transaction boundaries and commit/rollback traces.

This is useful for diagnosing missing mappings, duplicate retry behavior, and group rollback causes.

## 10. Important Implementation Notes and Current Gaps

These are behaviorally important details observed in current code:

1. Partial pending-flag clearing for successful groups
- `clearPendingSyncForGroup` currently has implemented metadata reset only for `expense` and `tag`.
- Other entity types in the `when` branch are placeholders.
- Impact: groups may report success, but some entities can remain pending locally.

2. Server ID handling during pending clear
- For expense/tag, pending-clear update sets `serverId = null` in current code path.
- Impact: may drop known server linkage if not restored elsewhere.

3. Wishlist/wishlist_tag local ID conversion quirks
- Group builder and operation builders use hash-based conversions in multiple places (`hashCode` paths).
- `wishlist_tag` grouping references tag hash while fetch-by-sync-id expects UUID-like sync id.
- Impact: wishlist tag operations may fail to load/build reliably for sync.

4. Pending detection excludes wishlist entities in repository sync check
- `ExpenseRepository.hasPendingSync()` checks expense/tag/target/expense_tag/graph_edge, not wishlist/wishlist_tag.
- Impact: pending indicators and auto-trigger logic can miss wishlist-related pending work.

5. Legacy and atomic paths coexist
- Codebase still contains legacy sync and batch sync paths alongside atomic path.
- Impact: higher maintenance complexity; endpoint behavior may diverge over time.

## 11. Practical Sequence Example

Example: user creates an expense with a new tag while offline.

1. Client inserts expense locally with `pendingSync=1`, `syncOperation=CREATE`.
2. Client inserts tag locally with pending sync.
3. Client inserts expense_tag cross-ref locally with pending sync.
4. Group builder creates one `EXPENSE_WITH_TAGS` group containing all three.
5. Client sends one atomic group.
6. Server creates tag and expense, resolves references, creates cross-ref in one savepoint.
7. Server returns mappings for newly created entities.
8. Client stores mappings, clears pending flags for successful entities.
9. Pull phase fetches any remote updates since last sync timestamp.

## 12. Operational Recommendations

For reliable production behavior, prioritize these fixes:

1. Complete `clearPendingSyncForGroup` handling for all entity types.
2. Preserve/propagate correct `serverId` when clearing pending sync metadata.
3. Normalize wishlist/wishlist_tag ID handling (remove hash-based surrogate conversion).
4. Include wishlist tables in pending-sync aggregation.
5. Decide a single canonical sync path (atomic vs legacy/batch) and deprecate the rest.

## 13. Source Pointers

Client:
- `mobile/app/src/main/java/com/example/financehub/sync/SyncManager.kt`
- `mobile/app/src/main/java/com/example/financehub/sync/AtomicSyncGroupBuilder.kt`
- `mobile/app/src/main/java/com/example/financehub/sync/SyncWorker.kt`
- `mobile/app/src/main/java/com/example/financehub/network/ApiService.kt`
- `mobile/app/src/main/java/com/example/financehub/data/database/*.kt`
- `mobile/app/src/main/java/com/example/financehub/data/dao/*.kt`

Server:
- `server/app/routes/atomic_sync.py`
- `server/app/services/atomic_sync_service.py`
- `server/app/routes/batch_sync.py`
- `server/app/routes/sync.py`
- `server/app/schemas.py`
- `server/app/schemas_atomic.py`
- `server/app/models/__init__.py`
- `server/migrations/003_add_entity_mappings_table.sql`
