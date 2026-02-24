# Network Configuration Guide

## Overview

The FinanceHub Android app automatically switches between development and production server configurations based on the build type.

## Build Configurations

### Debug Build (Development)
- **API URL**: `http://10.0.2.2:8000/`
- **Purpose**: Connect to local server running on host machine
- **Usage**: Android Emulator/AVD development
- **Note**: `10.0.2.2` is the special IP address that Android emulator uses to reach the host machine's localhost

To run in debug mode:
```bash
# In Android Studio, select "debug" build variant
# Or from command line:
./gradlew assembleDebug
./gradlew installDebug
```

### Release Build (Production)
- **API URL**: `http://192.168.1.101:8000/` (default, customize in build.gradle.kts)
- **Purpose**: Connect to production/homelab server
- **Usage**: Physical devices on same network
- **Logging**: Reduced logging (BASIC level only)

To run in release mode:
```bash
./gradlew assembleRelease
./gradlew installRelease
```

## Customizing Production Server IP

Edit [build.gradle.kts](build.gradle.kts) in the `buildTypes.release` section:

```kotlin
release {
    buildConfigField("String", "API_BASE_URL", "\"http://YOUR_SERVER_IP:8000/\"")
    buildConfigField("String", "API_ENDPOINT", "\"http://YOUR_SERVER_IP:8000/api/v1/\"")
    // ...
}
```

## Testing Local Server Connection

### 1. Start the Server
```bash
cd server
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. Verify Server is Running
From host machine:
```bash
curl http://localhost:8000/health
```

### 3. Test from Emulator
With the app in debug mode, the app will automatically connect to `http://10.0.2.2:8000/`

## Network Logging

### Client-Side Logging
- **Debug builds**: Full request/response body logging
- **Release builds**: Basic logging (URL, status code, timing)

Check Android Logcat with tag `NetworkConfig` to see connection details:
```
adb logcat NetworkConfig:D *:S
```

### Server-Side Logging
The server now includes comprehensive logging:
- Request method and path
- Response status and duration
- Error details with stack traces
- Database operation logs (in debug mode)

Server logs appear in the console where you run the server.

## Troubleshooting

### Emulator Can't Connect to Server

**Problem**: App shows connection errors in emulator

**Solutions**:
1. Verify server is running: `curl http://localhost:8000/health`
2. Ensure server is bound to `0.0.0.0` not `127.0.0.1`
3. Check that you're using debug build variant
4. Verify firewall isn't blocking port 8000

### Physical Device Can't Connect

**Problem**: App on physical device shows connection errors

**Solutions**:
1. Ensure device and server are on same network
2. Use release build variant
3. Update production IP in build.gradle.kts to match server's network IP
4. Find server IP: `ip addr show` (Linux) or `ipconfig` (Windows)
5. Verify firewall allows inbound connections on port 8000

### Wrong Server Configuration

**Problem**: App connecting to wrong server

**Solution**: 
1. Check current build variant in Android Studio (bottom left)
2. Rebuild app: `Build > Clean Project` then `Build > Rebuild Project`
3. Reinstall app to device/emulator

## Advanced Configuration

### Adding Custom Build Flavors

To add more environments (e.g., staging), edit build.gradle.kts:

```kotlin
flavorDimensions += "environment"
productFlavors {
    create("dev") {
        dimension = "environment"
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
    }
    create("staging") {
        dimension = "environment"
        buildConfigField("String", "API_BASE_URL", "\"http://staging.example.com/\"")
    }
    create("production") {
        dimension = "environment"
        buildConfigField("String", "API_BASE_URL", "\"http://prod.example.com/\"")
    }
}
```

### Runtime Configuration

For dynamic server switching without rebuilding, consider implementing a settings screen that allows users to manually enter a server URL, storing it in SharedPreferences.
