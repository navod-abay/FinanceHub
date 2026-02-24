# Server Logging Guide

## Overview

The FinanceHub server includes comprehensive logging to help debug issues and monitor operations.

## Log Levels

The server uses different log levels based on the `DEBUG` environment variable:

- **Debug Mode** (`DEBUG=true`): `DEBUG` level - shows all details including SQL queries
- **Production Mode** (`DEBUG=false`): `INFO` level - shows important events only

## What Gets Logged

### Request/Response Logging
Every HTTP request is logged with:
```
2026-02-24 10:15:32 - app.main - INFO - Request: POST /api/v1/sync/atomic
2026-02-24 10:15:32 - app.main - INFO - Response: POST /api/v1/sync/atomic Status: 200 Duration: 0.234s
```

### Startup Information
On server startup:
```
2026-02-24 10:00:00 - app.main - INFO - Starting FinanceHub API server...
2026-02-24 10:00:00 - app.main - INFO - Debug mode: False
2026-02-24 10:00:00 - app.main - INFO - Host: 0.0.0.0:8000
2026-02-24 10:00:00 - app.main - INFO - Database: postgresql://...
2026-02-24 10:00:00 - app.main - INFO - Database tables created/verified
```

### Error Logging
Errors include full stack traces for debugging:
```
2026-02-24 10:20:00 - app.services.atomic_sync_service - ERROR - Failed to process group abc123: ValidationError
[Full stack trace...]
```

## Viewing Logs

### Local Development
Logs appear directly in the terminal where you run the server:
```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Docker Container
View logs from the running container:
```bash
# Follow logs in real-time
docker logs -f financehub-server

# View last 100 lines
docker logs --tail 100 financehub-server

# View logs with timestamps
docker logs -t financehub-server
```

### Docker Compose
```bash
# View server logs
docker-compose -f docker-compose.prod.yml logs -f api

# View all service logs
docker-compose -f docker-compose.prod.yml logs -f
```

## Filtering Logs

### By Log Level
```bash
# Show only errors and warnings
docker logs financehub-server 2>&1 | grep -E "ERROR|WARNING"

# Show only INFO and above
docker logs financehub-server 2>&1 | grep -v "DEBUG"
```

### By Component
```bash
# Show only request/response logs
docker logs financehub-server 2>&1 | grep "Request:\|Response:"

# Show only sync operations
docker logs financehub-server 2>&1 | grep "sync"

# Show only database operations
docker logs financehub-server 2>&1 | grep "sqlalchemy"
```

## Configuring Log Levels

### Via Environment Variable
Set in your `.env` file:
```bash
# Enable debug logging
DEBUG=true

# Disable debug logging (production)
DEBUG=false
```

### Programmatically
Edit `app/logging_config.py` to customize:
```python
# Reduce SQL logging in development
logging.getLogger("sqlalchemy.engine").setLevel(logging.WARNING)

# Add more verbose logging for specific module
logging.getLogger("app.services.atomic_sync_service").setLevel(logging.DEBUG)
```

## Log Format

All logs follow this format:
```
%(asctime)s - %(name)s - %(levelname)s - %(message)s
```

Example:
```
2026-02-24 10:15:32 - app.main - INFO - Request: GET /health
```

Where:
- `asctime`: Timestamp
- `name`: Logger name (usually module path)
- `levelname`: DEBUG, INFO, WARNING, ERROR, CRITICAL
- `message`: The log message

## Performance Monitoring

The server automatically adds processing time to every response header:
```
X-Process-Time: 0.234
```

Monitor slow requests:
```bash
# Find requests taking over 1 second
docker logs financehub-server 2>&1 | grep "Duration:" | awk '$NF > 1.0'
```

## Troubleshooting with Logs

### Sync Failures
Look for atomic sync errors:
```bash
docker logs financehub-server 2>&1 | grep -A 10 "atomic.*ERROR"
```

### Connection Issues
Check for connection attempts:
```bash
docker logs financehub-server 2>&1 | grep "Request:"
```

### Database Problems
Enable SQL logging in `.env`:
```bash
DEBUG=true
```

Then check SQL queries:
```bash
docker logs financehub-server 2>&1 | grep "sqlalchemy"
```

## Log Retention

### Docker Logs
By default, Docker stores all logs. Limit log size in `docker-compose.prod.yml`:

```yaml
services:
  api:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

This keeps only last 3 files of 10MB each (30MB total).

### Log Rotation
For production deployments, consider using a log rotation solution:

1. **Docker logging driver**: Use `syslog` or `journald` driver
2. **External aggregation**: Use services like Loki, ELK stack, or CloudWatch
3. **Manual rotation**: Use `logrotate` on the host system

## Best Practices

1. **Development**: Enable `DEBUG=true` for full visibility
2. **Production**: Set `DEBUG=false` to reduce log volume
3. **Monitoring**: Regularly check logs for errors and warnings
4. **Retention**: Implement log rotation to prevent disk space issues
5. **Security**: Ensure logs don't contain sensitive data (passwords, tokens)
