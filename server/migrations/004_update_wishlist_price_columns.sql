-- Migration: Update wishlist table to use min_price and max_price instead of expected_price
-- This migration safely handles existing data

-- Step 1: Wrap in DO block to only execute if expected_price exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'wishlist' AND column_name = 'expected_price') THEN
        -- Add new columns (allow NULL initially)
        ALTER TABLE wishlist ADD COLUMN IF NOT EXISTS min_price INTEGER;
        ALTER TABLE wishlist ADD COLUMN IF NOT EXISTS max_price INTEGER;

        -- Migrate existing data (copy expected_price to both min_price and max_price)
        EXECUTE 'UPDATE wishlist SET min_price = expected_price, max_price = expected_price WHERE min_price IS NULL OR max_price IS NULL';

        -- Make the new columns NOT NULL
        ALTER TABLE wishlist ALTER COLUMN min_price SET NOT NULL;
        ALTER TABLE wishlist ALTER COLUMN max_price SET NOT NULL;

        -- Drop the old expected_price column
        ALTER TABLE wishlist DROP COLUMN IF EXISTS expected_price;
    END IF;
END $$;
