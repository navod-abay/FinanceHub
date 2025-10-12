-- Initialize FinanceHub database
-- This script runs automatically when the PostgreSQL container starts

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set timezone
SET timezone = 'UTC';

-- Create indexes for better performance (these will be created by SQLAlchemy as well)
-- Additional custom indexes can be added here

-- Insert some sample data for testing (optional)
-- Uncomment the following lines if you want sample data

/*
-- Sample tags
INSERT INTO tags (id, tag, monthly_amount, current_month, current_year, created_day, created_month, created_year) VALUES
    (uuid_generate_v4(), 'Groceries', 0, 10, 2025, 8, 10, 2025),
    (uuid_generate_v4(), 'Transportation', 0, 10, 2025, 8, 10, 2025),
    (uuid_generate_v4(), 'Entertainment', 0, 10, 2025, 8, 10, 2025),
    (uuid_generate_v4(), 'Utilities', 0, 10, 2025, 8, 10, 2025);
*/

-- Create a function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Note: Triggers will be added after tables are created by SQLAlchemy