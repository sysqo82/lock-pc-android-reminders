# LockPC Reminders - Companion Android App

A companion Android application for the LockPC system that syncs reminders and provides silent device location tracking capabilities.

## Features

- **Reminder Sync**: Automatically syncs reminders from the backend server
- **Notifications**: Displays notifications when tasks need to be completed
- **Silent Location Tracking**: Allows the admin app to locate the device without user notification
- **Boot Completion**: Automatically starts syncing reminders when the device boots
- **Background Services**: Responsive reminder sync and location tracking services

## Project Structure

```
lock-pc-android-reminders/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/locpc/reminders/
│   │       │   ├── App.kt                          # Application class with notification channels
│   │       │   ├── MainActivity.kt                 # Main activity
│   │       │   ├── api/
│   │       │   │   ├── ApiService.kt              # Retrofit API interfaces
│   │       │   │   └── ApiClient.kt               # API client singleton
│   │       │   ├── data/
│   │       │   │   └── Models.kt                  # Data classes
│   │       │   ├── service/
│   │       │   │   ├── ReminderSyncService.kt     # Reminder synchronization service
│   │       │   │   └── LocationService.kt         # Silent location tracking service
│   │       │   ├── receiver/
│   │       │   │   ├── BootReceiver.kt            # Boot completion receiver
│   │       │   │   └── RemoteCommandReceiver.kt   # Remote command handler
│   │       │   ├── ui/
│   │       │   │   └── ReminderAdapter.kt         # RecyclerView adapter for reminders
│   │       │   └── util/
│   │       │       ├── NotificationHelper.kt      # Notification helper
│   │       │       └── LocationHelper.kt          # Location helper
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   └── item_reminder.xml
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   ├── colors.xml
│   │       │   │   └── styles.xml
│   │       │   ├── drawable/
│   │       │   │   ├── reminder_card_background.xml
│   │       │   │   ├── status_badge_background.xml
│   │       │   │   └── ic_launcher_foreground.xml
│   │       │   └── xml/
│   │       │       ├── data_extraction_rules.xml
│   │       │       └── backup_descriptor.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## API Integration

The app communicates with the LockPC backend server (lock-pc-local) through the following endpoints:

- `GET /api/reminders` - Fetch list of reminders
- `POST /api/reminders/sync` - Sync reminder statuses back to server
- `POST /api/location/update` - Send device location (silent, no notification)

## Remote Commands

The app listens for the following broadcast intents from the admin app:

- `com.locpc.reminders.LOCATE_DEVICE` - Start silent location tracking
- `com.locpc.reminders.SYNC_REMINDERS` - Trigger reminder synchronization

## Permissions

Required permissions are declared in AndroidManifest.xml:

- `INTERNET` - Backend communication
- `ACCESS_FINE_LOCATION` - Precise device location
- `ACCESS_COARSE_LOCATION` - Approximate device location
- `POST_NOTIFICATIONS` - Reminder notifications (Android 13+)
- `FOREGROUND_SERVICE` - Background services
- `RECEIVE_BOOT_COMPLETED` - Start after device boot

## Configuration

### Base URL Configuration

The default base URL is set to `http://10.0.2.2:3000` for Android emulator development. 

To change it for physical devices or different environments:

```kotlin
val apiClient = ApiClient.getInstance(context)
apiClient.setBaseUrl("http://your-server-ip:3000")
```

### Authentication

To set authentication token:

```kotlin
val apiClient = ApiClient.getInstance(context)
apiClient.setAuthToken("your-auth-token")
```

## Dependencies

Key dependencies:

- **Retrofit** - REST API calls
- **OKHttp** - HTTP client
- **GSON** - JSON serialization
- **Google Play Services Location** - Location services
- **Coroutines** - Asynchronous operations
- **AndroidX Libraries** - Modern Android support
- **Timber** - Logging

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build the project (Build > Make Project)
4. Run on emulator or physical device

## Testing

Use Android Studio to test:

1. Run the app on emulator/device
2. Grant required permissions when prompted
3. Verify reminders appear and notifications show
4. Test silent location tracking via broadcast commands

### Sending Broadcast Commands

To test remote commands:

```bash
adb shell am broadcast -a com.locpc.reminders.LOCATE_DEVICE -n com.locpc.reminders/.receiver.RemoteCommandReceiver
adb shell am broadcast -a com.locpc.reminders.SYNC_REMINDERS -n com.locpc.reminders/.receiver.RemoteCommandReceiver
```

## Integration with Admin App

The admin app can:

1. Send `LOCATE_DEVICE` broadcast to silently track device location
2. Send `SYNC_REMINDERS` broadcast to force reminder synchronization
3. Configure location tracking settings (to be implemented)
4. View location history on the admin dashboard (backend implementation)

## Future Enhancements

- [ ] Settings screen for base URL and authentication
- [ ] Local database for reminders (Room)
- [ ] Scheduled reminder syncing using WorkManager
- [ ] Enhanced notification actions (complete, dismiss, snooze)
- [ ] Encryption for sensitive data at rest
- [ ] Battery optimization modes
- [ ] Widget for quick access to reminders

## Troubleshooting

### Location not updating
- Verify location permissions are granted
- Check that GPS is enabled
- Verify backend server is accessible
- Check logcat for Timber logs

### Notifications not showing
- Verify notification permission is granted (Android 13+)
- Check notification channels are properly created
- Verify reminder status is "pending"

### App crashes on startup
- Check AndroidManifest.xml for typos
- Verify all resource files are properly created
- Check for missing dependencies in build.gradle
- Review Timber logs in logcat

## License

Part of the LockPC system
