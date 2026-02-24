-- Migration: Add UUID id columns to tables with composite primary keys (PostgreSQL)
-- Date: 2026-02-24
-- Description: Adds id column to expense_tags, targets, and graph_edges tables

-- ============================================================================
-- 1. ExpenseTagsCrossRef (expense_tags table)
-- ============================================================================

-- Check if id column already exists, if not add it
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'expense_tags' AND column_name = 'id'
    ) THEN
        -- Add id column
        ALTER TABLE expense_tags ADD COLUMN id VARCHAR(36);
        
        -- Generate UUIDs for existing rows
        UPDATE expense_tags SET id = gen_random_uuid()::text WHERE id IS NULL;
        
        -- Make id NOT NULL
        ALTER TABLE expense_tags ALTER COLUMN id SET NOT NULL;
        
        -- Drop old primary key if exists
        ALTER TABLE expense_tags DROP CONSTRAINT IF EXISTS expense_tags_pkey;
        
        -- Add new primary key on id
        ALTER TABLE expense_tags ADD PRIMARY KEY (id);
        
        -- Create unique constraint on expense_id + tag_id (drop first if exists)
        DROP INDEX IF EXISTS idx_expense_tag_unique;
        CREATE UNIQUE INDEX idx_expense_tag_unique ON expense_tags(expense_id, tag_id);
        
        -- Add updated_at column if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'expense_tags' AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE expense_tags ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
        END IF;
        
        -- Add created_at column if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'expense_tags' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE expense_tags ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
        END IF;
        
        RAISE NOTICE 'Added id column to expense_tags table';
    ELSE
        RAISE NOTICE 'Column id already exists in expense_tags table, skipping';
    END IF;
END $$;


-- ============================================================================
-- 2. Target (targets table)
-- ============================================================================

DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'targets' AND column_name = 'id'
    ) THEN
        -- Add id column
        ALTER TABLE targets ADD COLUMN id VARCHAR(36);
        
        -- Generate UUIDs for existing rows
        UPDATE targets SET id = gen_random_uuid()::text WHERE id IS NULL;
        
        -- Make id NOT NULL
        ALTER TABLE targets ALTER COLUMN id SET NOT NULL;
        
        -- Drop old primary key if exists
        ALTER TABLE targets DROP CONSTRAINT IF EXISTS targets_pkey;
        
        -- Add new primary key on id
        ALTER TABLE targets ADD PRIMARY KEY (id);
        
        -- Create unique constraint on month + year + tag_id
        DROP INDEX IF EXISTS idx_target_unique;
        CREATE UNIQUE INDEX idx_target_unique ON targets(month, year, tag_id);
        
        RAISE NOTICE 'Added id column to targets table';
    ELSE
        RAISE NOTICE 'Column id already exists in targets table, skipping';
    END IF;
END $$;


-- ============================================================================
-- 3. GraphEdge (graph_edges table)
-- ============================================================================

DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'graph_edges' AND column_name = 'id'
    ) THEN
        -- Add id column
        ALTER TABLE graph_edges ADD COLUMN id VARCHAR(36);
        
        -- Generate UUIDs for existing rows
        UPDATE graph_edges SET id = gen_random_uuid()::text WHERE id IS NULL;
        
        -- Make id NOT NULL
        ALTER TABLE graph_edges ALTER COLUMN id SET NOT NULL;
        
        -- Drop old primary key if exists
        ALTER TABLE graph_edges DROP CONSTRAINT IF EXISTS graph_edges_pkey;
        
        -- Add new primary key on id
        ALTER TABLE graph_edges ADD PRIMARY KEY (id);
        
        -- Create unique constraint on from_tag_id + to_tag_id
        DROP INDEX IF EXISTS idx_graph_unique;
        CREATE UNIQUE INDEX idx_graph_unique ON graph_edges(from_tag_id, to_tag_id);
        
        RAISE NOTICE 'Added id column to graph_edges table';
    ELSE
        RAISE NOTICE 'Column id already exists in graph_edges table, skipping';
    END IF;
END $$;
