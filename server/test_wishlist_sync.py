import requests
import uuid
import datetime
import json

BASE_URL = "http://localhost:8000"

def test_wishlist_sync():
    print("Testing Wishlist Sync API...")
    
    # 1. Create a wishlist item
    item_id = str(uuid.uuid4())
    create_op = {
        "type": "create_wishlist",
        "name": "Test Laptop",
        "expectedPrice": 1500,
        "tagId": None,
        "clientId": item_id
    }
    
    payload = {
        "operations": [create_op]
    }
    
    print(f"\n1. Sending Create Request: {json.dumps(payload, indent=2)}")
    try:
        response = requests.post(f"{BASE_URL}/sync/batch/wishlist", json=payload)
        print(f"Response ({response.status_code}): {response.text}")
        response.raise_for_status()
        result = response.json()["results"][0]
        server_id = result["server_id"]
        print(f"Created Item Server ID: {server_id}")
    except Exception as e:
        print(f"Create Failed: {e}")
        return

    # 2. Verify existence via get_updated_data
    print(f"\n2. Verifying via get_updated_data...")
    try:
        response = requests.get(f"{BASE_URL}/sync/updated-data?since=0")
        response.raise_for_status()
        data = response.json()
        wishlist = data.get("wishlist", [])
        found = any(item["id"] == server_id for item in wishlist)
        print(f"Item found in updated-data: {found}")
        if not found:
            print("ERROR: Item not found in updated-data")
            return
    except Exception as e:
        print(f"Get Updated Data Failed: {e}")
        return

    # 3. Update the item
    print(f"\n3. Updating Item...")
    update_op = {
        "type": "update_wishlist",
        "serverId": server_id,
        "name": "Gaming Laptop",
        "expectedPrice": 2000,
        "tagId": None
    }
    
    payload = {
        "operations": [update_op]
    }
    
    try:
        response = requests.post(f"{BASE_URL}/sync/batch/wishlist", json=payload)
        print(f"Response ({response.status_code}): {response.text}")
        response.raise_for_status()
    except Exception as e:
        print(f"Update Failed: {e}")
        return
        
    # 4. Verify update
    print(f"\n4. Verifying Update...")
    try:
        # Wait a bit or use recent timestamp? Using 0 again to see all
        response = requests.get(f"{BASE_URL}/sync/updated-data?since=0") 
        response.raise_for_status()
        data = response.json()
        item = next((i for i in data.get("wishlist", []) if i["id"] == server_id), None)
        if item and item["name"] == "Gaming Laptop" and item["expectedPrice"] == 2000:
            print("Update Verified: Name updated to 'Gaming Laptop', Price to 2000")
        else:
            print(f"ERROR: Update verify failed. Item: {item}")
            return
    except Exception as e:
        print(f"Verify Update Failed: {e}")
        return

    # 5. Delete the item
    print(f"\n5. Deleting Item...")
    delete_op = {
        "type": "delete_wishlist",
        "serverId": server_id
    }
    
    payload = {
        "operations": [delete_op]
    }
    
    try:
        response = requests.post(f"{BASE_URL}/sync/batch/wishlist", json=payload)
        print(f"Response ({response.status_code}): {response.text}")
        response.raise_for_status()
    except Exception as e:
        print(f"Delete Failed: {e}")
        return

    # 6. Verify Deletion (Should not be returned if deleted? Or returned with deleted_at/metadata?
    # Our get_updated_data implementation returns WishlistItem where updated_at > since.
    # The models show 'deleted_at'. But get_updated_data query:
    # `wishlist_items = db.query(WishlistItem).filter(WishlistItem.updated_at > since_datetime).all()`
    # It returns deleted items too if updated_at is recent?
    # Actually, usually soft-deleted items are returned so client can process deletion.
    # But wait, my SyncManager logic handles deletes by `applyServerWishlist`.
    # `WishlistDao.deleteWishlist` removes row.
    # But how does `applyServerWishlist` know it's a delete?
    # In `SyncManager.kt` or `batch_sync.py`:
    # Server side DELETE op: deletes row from DB?
    # `db.delete(item)`. It's HARD DELETE in `batch_sync.py`.
    # `server/app/routes/batch_sync.py`: 
    # `elif operation.type == "delete_wishlist": ... db.delete(item)`
    # This is HARD DELETE.
    # So `get_updated_data` will NOT find it.
    # So `updated-data` will NOT return the deleted item.
    # This means other clients won't know about the deletion!
    # This is a FLAW in the existing sync design if it relies on hard deletes without tombstoning.
    # However, maybe `UpdatedDataResponse` is for delta sync of *existing* items.
    # How are deletes propagated?
    # In `SyncManager`, push uses specific DELETE op.
    # But pull? `applyServerExpenses` etc only insert/update?
    # Let's check `applyServerExpenses`.
    # It iterates `ApiExpense`. If missing locally, insert. If present, update.
    # It DOES NOT handle deletions from server!
    # If server hard deletes, client will never know.
    # Wait, `UpdatedDataResponse` has `expenses: List[ApiExpense]`.
    # If an item is deleted on server, it won't be in the list.
    # So client keeps it forever?
    # Yes, unless there's a separate "deleted items" list in response or full sync strategy handles it.
    # `performFullSync` calls `pullServerChanges` (delta sync).
    # So deletions are NOT synced from server to client with current hard delete logic.
    # This seems to be an existing architecture issue or I missed something.
    # Let's check `batch_sync.py` `delete_expense`.
    # `db.query(Expense).filter...delete()`. Hard delete.
    # So yes, broken sync for deletes server->client.
    # But I followed the pattern.
    # I should verify if I should change to soft delete or if this is intended (client authoritative, so maybe server deletes aren't pushed back?).
    # If client A deletes, it pushes DELETE to server (server deletes).
    # Client B pulls. Server doesn't send item. Client B keeps item.
    # Client B modifies item. Pushes UPDATE.
    # Server: Update fails (item not found)? `update_wishlist` returns `WishlistItem not found`.
    # SyncResult success=False.
    # Client B gets error.
    # So Client B is stuck with an item it can't sync.
    # This is definitely a potential issue, but out of scope for "Wishlist feature" unless I fix the whole sync system.
    # I will stick to the pattern.
    
    print(f"\n6. Verifying Deletion...")
    try:
        response = requests.get(f"{BASE_URL}/sync/updated-data?since=0")
        data = response.json()
        wishlist = data.get("wishlist", [])
        found = any(item["id"] == server_id for item in wishlist)
        print(f"Item found in updated-data (should be False): {found}")
    except Exception as e:
        print(f"Verify Delete Failed: {e}")

if __name__ == "__main__":
    test_wishlist_sync()
