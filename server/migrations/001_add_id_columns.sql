-- Migration: Add UUID id columns to tables with composite primary keys
-- Date: 2025-12-10
-- Description: Adds id column to expense_tags, targets, and graph_edges tables

-- ============================================================================
-- 1. ExpenseTagsCrossRef (expense_tags table)
-- ============================================================================

-- Add id column
ALTER TABLE expense_tags ADD COLUMN id VARCHAR(36);

-- Generate UUIDs for existing rows
UPDATE expense_tags SET id = (SELECT UUID()) WHERE id IS NULL;

-- Make id NOT NULL after populating
ALTER TABLE expense_tags MODIFY COLUMN id VARCHAR(36) NOT NULL;

-- Drop old primary key
ALTER TABLE expense_tags DROP PRIMARY KEY;

-- Add new primary key on id
ALTER TABLE expense_tags ADD PRIMARY KEY (id);

-- Add unique constraint on expense_id + tag_id
CREATE UNIQUE INDEX idx_expense_tag_unique ON expense_tags(expense_id, tag_id);

-- Add updated_at column
ALTER TABLE expense_tags ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;


-- ============================================================================
-- 2. Target (targets table)
-- ============================================================================

-- Add id column
ALTER TABLE targets ADD COLUMN id VARCHAR(36);

-- Generate UUIDs for existing rows
UPDATE targets SET id = (SELECT UUID()) WHERE id IS NULL;

-- Make id NOT NULL after populating
ALTER TABLE targets MODIFY COLUMN id VARCHAR(36) NOT NULL;

-- Drop old primary key
ALTER TABLE targets DROP PRIMARY KEY;

-- Add new primary key on id
ALTER TABLE targets ADD PRIMARY KEY (id);

-- Make old primary key columns regular columns (already done by DROP PRIMARY KEY)
ALTER TABLE targets MODIFY COLUMN month INT NOT NULL;
ALTER TABLE targets MODIFY COLUMN year INT NOT NULL;
ALTER TABLE targets MODIFY COLUMN tag_id VARCHAR(36) NOT NULL;

-- Add unique constraint on month + year + tag_id
CREATE UNIQUE INDEX idx_target_unique ON targets(month, year, tag_id);


-- ============================================================================
-- 3. GraphEdge (graph_edges table)
-- ============================================================================

-- Add id column
ALTER TABLE graph_edges ADD COLUMN id VARCHAR(36);

-- Generate UUIDs for existing rows
UPDATE graph_edges SET id = (SELECT UUID()) WHERE id IS NULL;

-- Make id NOT NULL after populating
ALTER TABLE graph_edges MODIFY COLUMN id VARCHAR(36) NOT NULL;

-- Drop old primary key
ALTER TABLE graph_edges DROP PRIMARY KEY;

-- Add new primary key on id
ALTER TABLE graph_edges ADD PRIMARY KEY (id);

-- Make old primary key columns regular columns
ALTER TABLE graph_edges MODIFY COLUMN from_tag_id VARCHAR(36) NOT NULL;
ALTER TABLE graph_edges MODIFY COLUMN to_tag_id VARCHAR(36) NOT NULL;

-- Add unique constraint on from_tag_id + to_tag_id
CREATE UNIQUE INDEX idx_graph_unique ON graph_edges(from_tag_id, to_tag_id);
