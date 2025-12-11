# Database Migration Summary

## Overview
Migrated database schema from composite primary keys to UUID-based primary keys for tables: `Target`, `ExpenseTagsCrossRef`, and `GraphEdge`. This change enables proper batch synchronization with individual server IDs.

## Changes Made

### 1. Server-Side Changes

#### Database Models (`server/app/models/__init__.py`)
- **ExpenseTagsCrossRef**: Added `id` (UUID) as primary key, made `(expense_id, tag_id)` a unique index
- **Target**: Added `id` (UUID) as primary key, made `(month, year, tag_id)` a unique index
- **GraphEdge**: Added `id` (UUID) as primary key, made `(from_tag_id, to_tag_id)` a unique index, added `updated_at` timestamp

#### SQL Migration (`server/migrations/001_add_id_columns.sql`)
MySQL migration script that:
1. Adds `id` VARCHAR(36) columns to all three tables
2. Generates UUIDs for existing rows using `UUID()` function
3. Drops old composite PRIMARY KEYs
4. Adds new PRIMARY KEY on `id` column
5. Creates UNIQUE INDEXes on old composite key columns
6. Adds `updated_at` timestamp to `expense_tags` table

**IMPORTANT**: Run this migration before starting the server with new code.

```sql
mysql -u your_username -p your_database < server/migrations/001_add_id_columns.sql
```

#### Pydantic Schemas (`server/app/schemas.py`)
Updated all batch operation types with:
- Discriminated unions using `Annotated[Union[...], Field(discriminator='type')]`
- `Config` classes with `populate_by_name = True` for camelCase↔snake_case conversion
- Applied to: `TagOperation`, `TargetOperation`, `ExpenseTagOperation`, `GraphEdgeOperation`

#### Batch Sync Endpoints (`server/app/routes/batch_sync.py`)
Implemented complete batch sync for all entities:
- ✅ `POST /api/v1/sync/batch/expenses` - CREATE/UPDATE/DELETE expenses
- ✅ `POST /api/v1/sync/batch/tags` - CREATE/UPDATE/DELETE tags
- ✅ `POST /api/v1/sync/batch/targets` - CREATE/UPDATE/DELETE targets
- ✅ `POST /api/v1/sync/batch/expense-tags` - CREATE/DELETE expense-tag relationships
- ✅ `POST /api/v1/sync/batch/graph-edges` - CREATE/UPDATE/DELETE graph edges
- ✅ `GET /api/v1/sync/updated-data?since={timestamp}` - Returns all updated entities

### 2. Client-Side Changes (Android)

#### Database Version (`mobile/app/src/main/java/com/example/financehub/data/database/AppDatabase.kt`)
- Updated from version 9 to version 10
- Added `MIGRATION_9_10` to handle schema restructuring

#### Migration 9→10 Strategy
For each table:
1. Create new table with `id TEXT PRIMARY KEY`
2. Create unique index on old composite key columns
3. Copy data, using `serverId` if exists, otherwise generate UUID with `hex(randomblob(16))`
4. Drop old table and rename new table

#### Entity Classes
Updated all three entities to use UUID primary key:

**ExpenseTagsCrossRef.kt**:
- Added `@PrimaryKey val id: String = UUID.randomUUID().toString()`
- Removed `primaryKeys = ["expenseID", "tagID"]`
- Added `@Index(value = ["expenseID", "tagID"], unique = true)`

**Target.kt**:
- Added `@PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString()`
- Removed `primaryKeys = ["month", "year", "tagID"]`
- Added `@Index(value = ["month", "year", "tagID"], unique = true)`

**GraphEdge.kt**:
- Added `@PrimaryKey val id: String = UUID.randomUUID().toString()`
- Removed `primaryKeys = ["fromTagId", "toTagId"]`
- Added `@Index(value = ["fromTagId", "toTagId"], unique = true)`

## Testing Checklist

### Server Testing
- [ ] Run SQL migration script successfully
- [ ] Start server and verify no errors
- [ ] Test expense batch sync (already working)
- [ ] Test tags batch sync endpoint
- [ ] Test targets batch sync endpoint
- [ ] Test expense-tags batch sync endpoint
- [ ] Test graph-edges batch sync endpoint
- [ ] Test `/updated-data` endpoint returns all entity types

### Client Testing
- [ ] App upgrades from version 9 to 10 without crashes
- [ ] Existing data preserved after migration
- [ ] New records created with proper UUID ids
- [ ] Batch sync works for all entity types
- [ ] Unique constraint violations handled properly (duplicate composite keys)

## Migration Notes

### Data Preservation
- All existing data is preserved during migration
- Server migration generates UUIDs for existing records
- Client migration reuses `serverId` if available, otherwise generates UUID

### Unique Constraints
The unique indexes on composite keys ensure:
- No duplicate targets for same (month, year, tag)
- No duplicate expense-tag relationships
- No duplicate graph edges between same tags

### Rollback Strategy
If issues arise:
1. Server: Keep backup before running migration, restore from backup if needed
2. Client: Uninstall and reinstall app (will lose local data), or downgrade database version

## API Compatibility

All batch sync endpoints follow the same pattern:

**Request**:
```json
{
  "operations": [
    {
      "type": "create_target",
      "month": 1,
      "year": 2024,
      "tagId": "tag-uuid",
      "amount": 1000,
      "spent": 0,
      "clientId": "client-uuid"
    }
  ]
}
```

**Response**:
```json
{
  "results": [
    {
      "success": true,
      "clientId": "client-uuid",
      "serverId": "server-uuid",
      "error": null
    }
  ]
}
```

## Next Steps
1. Run server SQL migration
2. Test server endpoints
3. Deploy server
4. Release Android app update with migration
5. Monitor for migration errors in production
