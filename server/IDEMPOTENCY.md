# Idempotent Sync Operations

## Problem

When a client sends a sync request to the server, the server may successfully process the request and update the database, but the response might not reach the client due to:
- Network timeout
- Connection failure
- Server crash after commit but before response

In these cases, the client will retry the same operation, potentially creating duplicate entities or causing errors.

## Solution

We implement **idempotent sync operations** using persistent entity mappings.

### Key Concepts

1. **Entity Mappings**: A persistent mapping table that tracks `clientId -> serverId` relationships
2. **Idempotency Check**: Before creating an entity, check if a mapping already exists
3. **Safe Retry**: If a mapping exists, return the existing `serverId` instead of creating a duplicate

### Implementation

#### Database Model

```python
class EntityMapping(Base):
    __tablename__ = "entity_mappings"
    
    entity_type = Column(String, primary_key=True)  # "expense", "tag", etc.
    client_id = Column(String, primary_key=True)
    server_id = Column(String, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
```

#### Service Methods

1. **`_get_persistent_mapping(entity_type, client_id)`**
   - Checks if a mapping exists in the database
   - Returns `server_id` if found, `None` otherwise

2. **`_save_persistent_mapping(entity_type, client_id, server_id)`**
   - Saves a new mapping after creating an entity
   - Handles race conditions gracefully (ignore if mapping already exists)

3. **Updated `_create_*` methods**
   - Check for existing mapping before creating
   - If mapping exists, return existing `server_id`
   - If not, create entity and save mapping

### Example Flow

#### First Request (Success)
1. Client sends: `create_expense` with `clientId="1"`
2. Server checks: No mapping exists for `expense:1`
3. Server creates expense with `serverId="uuid-123"`
4. Server saves mapping: `expense:1 -> uuid-123`
5. Server returns: `serverId="uuid-123"`
6. Client receives and stores mapping

#### First Request (Network Failure)
1. Client sends: `create_expense` with `clientId="1"`
2. Server checks: No mapping exists for `expense:1`
3. Server creates expense with `serverId="uuid-123"`
4. Server saves mapping: `expense:1 -> uuid-123`
5. **Network failure - client doesn't receive response**
6. Client retries with same `clientId="1"`

#### Retry Request (Idempotent)
1. Client sends: `create_expense` with `clientId="1"` (retry)
2. Server checks: Mapping exists! `expense:1 -> uuid-123`
3. Server returns existing `serverId="uuid-123"` **without creating duplicate**
4. Client receives response and updates local mapping

### Benefits

- **No Duplicates**: Same request won't create multiple entities
- **Automatic Recovery**: Client can safely retry after network failures
- **Consistent State**: Server and client stay in sync even with failures
- **Minimal Overhead**: Only checks mappings, doesn't repeat expensive operations

### Migration

Run the migration to add the `entity_mappings` table:

```bash
cd server
source venv/bin/activate  # or .\venv\Scripts\Activate.ps1 on Windows
python run_migrations.py
```

This creates the `entity_mappings` table with proper indexes for efficient lookups.

### Testing Idempotency

To test that operations are idempotent:

1. Send a sync request with a `clientId`
2. Verify entity is created and mapping is saved
3. Send the exact same request again
4. Verify no duplicate is created
5. Verify same `serverId` is returned
6. Check database: Only one entity and one mapping exist

### Monitoring

The service logs idempotency events:

- `INFO`: When returning existing entity (idempotency hit)
- `DEBUG`: When checking/saving persistent mappings
- `WARNING`: When there's a mapping conflict (shouldn't happen normally)

Look for log messages like:
```
INFO: Expense already exists for client_id 1, returning existing
DEBUG: Found persistent mapping: expense:1 -> uuid-123
DEBUG: Saved persistent mapping: tag:2 -> uuid-456
```
