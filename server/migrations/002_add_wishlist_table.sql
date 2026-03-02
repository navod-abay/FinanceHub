-- Create Wishlist table (without tag_id)
CREATE TABLE IF NOT EXISTS wishlist (
    id VARCHAR PRIMARY KEY,
    name VARCHAR NOT NULL,
    min_price INTEGER NOT NULL,
    max_price INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create Wishlist-Tags Cross Reference table
CREATE TABLE IF NOT EXISTS wishlist_tags (
    id VARCHAR PRIMARY KEY,
    wishlist_id VARCHAR NOT NULL REFERENCES wishlist(id),
    tag_id VARCHAR NOT NULL REFERENCES tags(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(wishlist_id, tag_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_wishlist_tags_wishlist_id ON wishlist_tags(wishlist_id);
CREATE INDEX IF NOT EXISTS idx_wishlist_tags_tag_id ON wishlist_tags(tag_id);
