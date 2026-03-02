-- Migration: Update wishlist table to use min_price and max_price instead of expected_price
-- This migration safely handles existing data

-- Step 1: Add new columns (allow NULL initially)
ALTER TABLE wishlist ADD COLUMN IF NOT EXISTS min_price INTEGER;
ALTER TABLE wishlist ADD COLUMN IF NOT EXISTS max_price INTEGER;

-- Step 2: Migrate existing data (copy expected_price to both min_price and max_price)
UPDATE wishlist 
SET min_price = expected_price, 
    max_price = expected_price 
WHERE min_price IS NULL OR max_price IS NULL;

-- Step 3: Make the new columns NOT NULL
ALTER TABLE wishlist ALTER COLUMN min_price SET NOT NULL;
ALTER TABLE wishlist ALTER COLUMN max_price SET NOT NULL;

-- Step 4: Drop the old expected_price column
ALTER TABLE wishlist DROP COLUMN IF EXISTS expected_price;
