# Test Batch Sync Endpoints

## Prerequisites
1. Run the SQL migration: `mysql -u root -p financehub < server/migrations/001_add_id_columns.sql`
2. Start the server: `cd server && python -m app.main`

## Test Commands (using curl or httpie)

### 1. Test Tags Batch Sync
```bash
curl -X POST http://192.168.1.101:8000/api/v1/sync/batch/tags \
  -H "Content-Type: application/json" \
  -d '{
    "operations": [
      {
        "type": "create_tag",
        "name": "Test Tag",
        "monthlyAmount": 1000,
        "currentMonth": 1,
        "currentYear": 2024,
        "createdDay": 15,
        "createdMonth": 1,
        "createdYear": 2024,
        "clientId": "test-client-123"
      }
    ]
  }'
```

Expected response:
```json
{
  "results": [
    {
      "success": true,
      "clientId": "test-client-123",
      "serverId": "uuid-from-server",
      "error": null
    }
  ]
}
```

### 2. Test Targets Batch Sync
```bash
curl -X POST http://192.168.1.101:8000/api/v1/sync/batch/targets \
  -H "Content-Type: application/json" \
  -d '{
    "operations": [
      {
        "type": "create_target",
        "month": 1,
        "year": 2024,
        "tagId": "put-actual-tag-id-here",
        "amount": 5000,
        "spent": 0,
        "clientId": "target-client-456"
      }
    ]
  }'
```

### 3. Test Expense Tags Batch Sync
```bash
curl -X POST http://192.168.1.101:8000/api/v1/sync/batch/expense-tags \
  -H "Content-Type: application/json" \
  -d '{
    "operations": [
      {
        "type": "create_expense_tag",
        "expenseId": "put-expense-id-here",
        "tagId": "put-tag-id-here",
        "clientId": "et-client-789"
      }
    ]
  }'
```

### 4. Test Graph Edges Batch Sync
```bash
curl -X POST http://192.168.1.101:8000/api/v1/sync/batch/graph-edges \
  -H "Content-Type: application/json" \
  -d '{
    "operations": [
      {
        "type": "create_graph_edge",
        "fromTagId": "tag-id-1",
        "toTagId": "tag-id-2",
        "weight": 5,
        "clientId": "edge-client-999"
      }
    ]
  }'
```

### 5. Test Updated Data Endpoint
```bash
# Get all data updated since timestamp (e.g., 1 day ago)
curl -X GET "http://192.168.1.101:8000/api/v1/sync/updated-data?since=1704067200000"
```

Expected response should include all entity types:
```json
{
  "expenses": [...],
  "tags": [...],
  "targets": [...],
  "expenseTags": [...],
  "graphEdges": [...]
}
```

## Testing on Android Client

1. Ensure app database version is 10
2. Sync should automatically upgrade from version 9 to 10
3. Test syncing each entity type:
   - Create local expense → sync → verify server ID
   - Create local tag → sync → verify server ID
   - Create local target → sync → verify server ID
   - Create expense-tag relationship → sync
   - Graph edges should sync automatically

## Troubleshooting

### Migration Errors
If migration fails:
- Check MySQL version supports UUID() function (MySQL 5.7+)
- Verify no duplicate composite key values exist
- Check foreign key constraints are satisfied

### Pydantic Errors
The type checker warnings about `client_id` vs `clientId` are false positives.
Pydantic with `populate_by_name = True` accepts both formats.

### Android Migration Errors
If app crashes on upgrade:
- Check logcat for migration errors
- Verify no data corruption
- May need to clear app data and re-sync
