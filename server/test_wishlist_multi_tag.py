import requests
import uuid
import datetime
import json
import time

BASE_URL = "http://localhost:8000/api/v1"

def test_wishlist_multi_tag():
    print("Testing Wishlist Multi-Tag Sync API...")
    
    # Debug: Health check
    try:
        health = requests.get("http://localhost:8000/health")
        print(f"Health Check: {health.status_code} {health.text}")
    except Exception as e:
        print(f"Health Check Failed: {e}")

    # 1. Create a tag
    tag_id_1 = str(uuid.uuid4())
    create_tag_op = {
        "type": "create_tag",
        "name": "Tech",
        "monthlyAmount": 0,
        "currentMonth": 0,
        "currentYear": 0,
        "createdDay": 1,
        "createdMonth": 1,
        "createdYear": 2024,
        "clientId": tag_id_1
    }
    
    # 2. Create a wishlist item
    item_id = str(uuid.uuid4())
    create_item_op = {
        "type": "create_wishlist",
        "name": "Gaming Laptop",
        "expectedPrice": 2000,
        "clientId": item_id
    }
    
    # Batch create tag and item
    payload_1 = {
        "operations": [create_tag_op]
    }
    try:
        resp = requests.post(f"{BASE_URL}/sync/batch/tags", json=payload_1)
        resp.raise_for_status()
        print("Tag created.")
    except Exception as e:
        print(f"Tag Creation Failed: {e}")
        if 'resp' in locals():
            print(f"Response: {resp.status_code} {resp.text}")
        return

    payload_2 = {
        "operations": [create_item_op]
    }
    resp = requests.post(f"{BASE_URL}/sync/batch/wishlist", json=payload_2)
    resp.raise_for_status()
    server_item_id = resp.json()["results"][0]["serverId"]
    print(f"Wishlist Item created. Server ID: {server_item_id}")
    
    # 3. Create Wishlist Tag Relation
    relation_id = str(uuid.uuid4())
    create_rel_op = {
        "type": "create_wishlist_tag",
        "wishlistId": server_item_id,
        "tagId": tag_id_1, # Using client ID of tag as tag ID (assuming server uses same or mapped?) 
                           # Wait, sync returns server_id for tag. I should use server_id if I knew it.
                           # But usually client uses its own ID for tagId reference? 
                           # My backend `batch_sync_tags` returns server_id.
                           # Let's get server_tag_id first.
    }
    
    # Get server tag id?
    # Actually, for `create_wishlist_tag`, we send `tagId`. Is it client ID or server ID?
    # Backend `batch_sync_wishlist_tags` expects `tag_id`. 
    # And `WishlistTagsCrossRef` stores `tag_id`.
    # And `Tag` entity uses UUID on server. 
    # If I sent `clientId` for tag, server might accept it if I sent it as `id` (if server accepts client ID as ID).
    # `batch_sync_tags`: 
    # `new_tag = Tag(id=op.clientId if ... else uuid)` ?
    # Let's check `batch_sync.py` `create_tag`.
    # `new_tag = Tag(id=operation.clientId, ...)`
    # So server uses client ID as ID!
    # So `tag_id_1` is valid.
    
    payload_3 = {
        "operations": [create_rel_op]
    }
    
    print(f"\n3. Sending Create Wishlist Tag Relation...")
    resp = requests.post(f"{BASE_URL}/sync/batch/wishlist-tags", json=payload_3)
    print(f"Response: {resp.text}")
    resp.raise_for_status()
    
    # 4. Verify via get_updated_data
    print(f"\n4. Verifying via get_updated_data...")
    resp = requests.get(f"{BASE_URL}/sync/updated-data?since=0")
    resp.raise_for_status()
    data = resp.json()
    
    wishlist_tags = data.get("wishlist_tags", [])
    found_rel = any(wt["wishlistId"] == server_item_id and wt["tagId"] == tag_id_1 for wt in wishlist_tags)
    print(f"Relation found: {found_rel}")
    
    if not found_rel:
        print("ERROR: Relation not found.")
        print(f"Server data: {wishlist_tags}")
        return

    # 5. Delete Relation
    print(f"\n5. Deleting Relation...")
    delete_rel_op = {
        "type": "delete_wishlist_tag",
        "wishlistId": server_item_id,
        "tagId": tag_id_1,
        "serverId": wishlist_tags[0]["id"] # Need the ID of the relation to delete? 
                                          # `delete_wishlist_tag` schema uses `wishlistId` and `tagId`? Or `serverId` of relation?
                                          # My schema `DeleteWishlistTagBatchRequest`: `wishlistId`, `tagId`, `serverId`.
                                          # Backend logic: `db.query(WishlistTagsCrossRef).filter(...)`.
                                          # It uses `wishlist_id` and `tag_id`.
    }
    
    payload_4 = {
        "operations": [delete_rel_op]
    }
    
    resp = requests.post(f"{BASE_URL}/sync/batch/wishlist-tags", json=payload_4)
    print(f"Response: {resp.text}")
    resp.raise_for_status()
    
    # 6. Verify Deletion
    print(f"\n6. Verifying Deletion...")
    resp = requests.get(f"{BASE_URL}/sync/updated-data?since=0") 
    # Note: Hard delete issue discussed before apply here too.
    # If hard deleted, it won't be in updated-data.
    data = resp.json()
    wishlist_tags = data.get("wishlist_tags", [])
    found_rel = any(wt["wishlistId"] == server_item_id and wt["tagId"] == tag_id_1 for wt in wishlist_tags)
    print(f"Relation found (should be False): {found_rel}")

if __name__ == "__main__":
    test_wishlist_multi_tag()
