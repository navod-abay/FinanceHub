-- Migration: Add entity_mappings table for idempotent sync operations
-- Date: 2026-02-25
-- Description: Creates a table to track clientId -> serverId mappings for idempotency

-- Create entity_mappings table
CREATE TABLE IF NOT EXISTS entity_mappings (
    entity_type VARCHAR(50) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    server_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_type, client_id)
);

-- Create indexes for efficient lookups
CREATE INDEX IF NOT EXISTS idx_entity_mapping_lookup ON entity_mappings(entity_type, client_id);
CREATE INDEX IF NOT EXISTS idx_entity_mapping_server ON entity_mappings(server_id);

-- Add comment
COMMENT ON TABLE entity_mappings IS 'Maps client-side entity IDs to server-side UUIDs for idempotent sync operations';
