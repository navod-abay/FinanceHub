# Development Quick Start Guide

## Server Setup (Local Development)

### 1. Prerequisites
- Python 3.9+
- PostgreSQL or SQLite

### 2. Setup Virtual Environment
```bash
cd server
python -m venv venv

# Windows
venv\Scripts\activate

# Linux/Mac
source venv/bin/activate
```

### 3. Install Dependencies
```bash
pip install -r requirements.txt
```

### 4. Configure Environment
```bash
# Copy example env file
cp .env.example .env

# Edit .env for development
# For SQLite (easier for dev):
DATABASE_URL=sqlite:///./financehub.db
HOST=0.0.0.0
PORT=8000
DEBUG=true
```

### 5. Run Server
```bash
# Method 1: Using uvicorn directly
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Method 2: Using the dev start script
python dev_start.py
```

Server will be available at: `http://localhost:8000`

Test it: `curl http://localhost:8000/health`

### 6. View API Documentation
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## Android App Setup (Emulator)

### 1. Prerequisites
- Android Studio
- Android SDK (API 26+)
- Emulator or physical device

### 2. Open Project
```bash
# Open in Android Studio
# File > Open > select mobile/ folder
```

### 3. Configure Build Variant
- Bottom left of Android Studio
- Select: `debug` (for local development)
- The debug variant automatically uses `10.0.2.2:8000` to connect to host machine

### 4. Sync Project
- Click "Sync Now" when prompted
- Or: File > Sync Project with Gradle Files

### 5. Run App
- Click Run button (green play icon)
- Select emulator or connected device
- App will connect to local server at `10.0.2.2:8000`

## Testing the Connection

### 1. Start Server
```bash
cd server
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. Verify Server
From your computer:
```bash
curl http://localhost:8000/health
# Should return: {"status":"healthy","timestamp":...}
```

### 3. Run Android App
- Launch app in emulator
- Check Logcat for connection logs:
  ```
  adb logcat NetworkConfig:D *:S
  ```

### 4. Monitor Server Logs
Watch server terminal for incoming requests:
```
2026-02-24 10:15:32 - app.main - INFO - Request: GET /health
2026-02-24 10:15:32 - app.main - INFO - Response: GET /health Status: 200 Duration: 0.004s
```

## Common Development Tasks

### View Server Logs
All requests and responses are logged:
```bash
# Logs appear in terminal where server is running
# Look for:
# - Request: METHOD /path
# - Response: METHOD /path Status: CODE Duration: Xs
```

### View Client Logs
```bash
# Filter by component
adb logcat NetworkConfig:D SyncManager:D *:S

# View all app logs
adb logcat com.example.financehub:D *:S
```

### Reset Database
```bash
# For SQLite
rm financehub.db

# For PostgreSQL
docker-compose down -v
docker-compose up -d
```

### Clear App Data
```bash
adb shell pm clear com.example.financehub
```

## Build Variants Explained

### Debug Build (Development)
- **Server URL**: `http://10.0.2.2:8000/` (emulator) or `http://localhost:8000/` (physical device on same machine)
- **Logging**: Full request/response logging
- **ProGuard**: Disabled
- **Debuggable**: Yes

### Release Build (Production)
- **Server URL**: `http://192.168.1.101:8000/` (configure in build.gradle.kts)
- **Logging**: Basic logging only
- **ProGuard**: Can be enabled
- **Debuggable**: No

To switch: Change build variant in Android Studio (bottom left) and rebuild.

## Switching to Production Server

### For Physical Device on Same Network

1. Find your server's IP address:
   ```bash
   # Linux/Mac
   ip addr show
   
   # Windows
   ipconfig
   ```

2. Update `mobile/app/build.gradle.kts`:
   ```kotlin
   release {
       buildConfigField("String", "API_BASE_URL", "\"http://YOUR_SERVER_IP:8000/\"")
       // ...
   }
   ```

3. Switch to Release build variant in Android Studio

4. Rebuild and run

## Troubleshooting

### Emulator Can't Connect to Server
- Ensure server is running: `curl http://localhost:8000/health`
- Verify server uses `HOST=0.0.0.0` in .env
- Check you're using debug build variant
- Windows: Allow port 8000 through firewall

### Database Connection Errors
- Check DATABASE_URL in .env
- For PostgreSQL: ensure database exists and credentials are correct
- For SQLite: check file permissions

### Build Configuration Not Working
- Clean project: `Build > Clean Project`
- Rebuild: `Build > Rebuild Project`
- Invalidate caches: `File > Invalidate Caches > Invalidate and Restart`

## Next Steps

- Read [mobile/NETWORK_CONFIG.md](../mobile/NETWORK_CONFIG.md) for detailed network configuration
- Read [server/LOGGING.md](../server/LOGGING.md) for logging details
- Read [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md) for production deployment

## Development Tips

1. **Keep server logs visible** in one terminal while developing
2. **Use debug build** for development (better logging)
3. **Monitor Logcat** for Android issues
4. **Test sync operations** with server logs to see what's happening
5. **Use Swagger UI** at http://localhost:8000/docs to test API directly
