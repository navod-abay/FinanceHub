-- Migration: Add UUID id columns to tables with composite primary keys
-- Date: 2025-12-10
-- Description: Adds id column to expense_tags, targets, and graph_edges tables

DO $$ 
BEGIN
    -- 1. ExpenseTagsCrossRef (expense_tags table)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'expense_tags') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'expense_tags' AND column_name = 'id') THEN
            ALTER TABLE expense_tags ADD COLUMN id VARCHAR(36);
            UPDATE expense_tags SET id = uuid_generate_v4() WHERE id IS NULL;
            ALTER TABLE expense_tags ALTER COLUMN id SET NOT NULL;
            
            -- Drop old primary key constraints by looking them up or just assuming default names
            -- Assuming the old constraint name might be unknown, but let's try dropping the explicit constraint names if they exist
            ALTER TABLE expense_tags DROP CONSTRAINT IF EXISTS expense_tags_pkey;
            
            ALTER TABLE expense_tags ADD PRIMARY KEY (id);
            CREATE UNIQUE INDEX IF NOT EXISTS idx_expense_tag_unique ON expense_tags(expense_id, tag_id);
            ALTER TABLE expense_tags ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
        END IF;
    END IF;

    -- 2. Target (targets table)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'targets') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'targets' AND column_name = 'id') THEN
            ALTER TABLE targets ADD COLUMN id VARCHAR(36);
            UPDATE targets SET id = uuid_generate_v4() WHERE id IS NULL;
            ALTER TABLE targets ALTER COLUMN id SET NOT NULL;
            
            ALTER TABLE targets DROP CONSTRAINT IF EXISTS targets_pkey;
            ALTER TABLE targets ADD PRIMARY KEY (id);
            
            CREATE UNIQUE INDEX IF NOT EXISTS idx_target_unique ON targets(month, year, tag_id);
        END IF;
    END IF;

    -- 3. GraphEdge (graph_edges table)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'graph_edges') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'graph_edges' AND column_name = 'id') THEN
            ALTER TABLE graph_edges ADD COLUMN id VARCHAR(36);
            UPDATE graph_edges SET id = uuid_generate_v4() WHERE id IS NULL;
            ALTER TABLE graph_edges ALTER COLUMN id SET NOT NULL;
            
            ALTER TABLE graph_edges DROP CONSTRAINT IF EXISTS graph_edges_pkey;
            ALTER TABLE graph_edges ADD PRIMARY KEY (id);
            
            CREATE UNIQUE INDEX IF NOT EXISTS idx_graph_unique ON graph_edges(from_tag_id, to_tag_id);
        END IF;
    END IF;
END $$;
