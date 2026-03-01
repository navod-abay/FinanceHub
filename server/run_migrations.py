#!/usr/bin/env python3
"""
Run database migrations
"""

import psycopg2
from pathlib import Path
import sys

from app.config import settings

def run_migration(cursor, migration_file: Path):
    """Run a single migration file"""
    print(f"Running migration: {migration_file.name}")
    
    with open(migration_file, 'r') as f:
        sql = f.read()
    
    try:
        cursor.execute(sql)
        print(f"✅ Successfully applied {migration_file.name}")
        return True
    except Exception as e:
        print(f"❌ Failed to apply {migration_file.name}: {e}")
        return False

def main():
    # Connect to database
    print(f"Connecting to database...")
    try:
        conn = psycopg2.connect(settings.database_url)
        conn.autocommit = True
        cursor = conn.cursor()
        print("✅ Connected to database")
    except Exception as e:
        print(f"❌ Failed to connect to database: {e}")
        return 1
    
    # Get migrations directory
    migrations_dir = Path(__file__).parent / "migrations"
    
    # Get all migration files in order
    migration_files = sorted(migrations_dir.glob("*.sql"))
    
    if not migration_files:
        print("⚠️  No migration files found")
        return 0
    
    print(f"\nFound {len(migration_files)} migration(s)")
    print("-" * 50)
    
    # Run each migration
    success_count = 0
    for migration_file in migration_files:
        if run_migration(cursor, migration_file):
            success_count += 1
        print()
    
    # Close connection
    cursor.close()
    conn.close()
    
    print("-" * 50)
    print(f"✅ Successfully applied {success_count}/{len(migration_files)} migration(s)")
    
    if success_count == len(migration_files):
        return 0
    else:
        return 1

if __name__ == "__main__":
    sys.exit(main())
