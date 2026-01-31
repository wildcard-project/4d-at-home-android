# AI Agent Development Guidelines for 4D@HOME Android

## Project Overview
4D@HOME Android is an award-winning project that enhances video viewing experiences by synchronizing physical effects (e.g., wind, vibration) with video playback. The system integrates:
- **Android App**: Built with Kotlin and Jetpack Compose for UI.
- **ESP32 Microcontrollers**: Control physical devices like motors and LEDs.
- **BLE Communication**: Facilitates communication between the Android app and ESP32 devices.
- **JSON Timelines**: Define synchronized effects for videos.

## Key Components
- **Android App**: Located in `app/src/main/java/com/wildcard/`. Implements the UI and BLE communication logic.
- **ESP32 Firmware**: Found in `esp32/`. Contains firmware for effect stations and action drives.
- **JSON Timelines**: Stored in `app/src/main/assets/timelines/`. These files define the timing and type of effects.
- **Specifications**: Detailed documentation in `docs/specifications/`.

## Developer Workflows
### Build and Run
- Use Gradle wrapper scripts:
  ```bash
  ./gradlew assembleDebug
  ./gradlew installDebug
  ```
- Open the Android project in Android Studio for development and debugging.

### Testing
- Unit tests are located in `app/src/test/java/`.
- Instrumentation tests are in `app/src/androidTest/java/`.
- Run tests with:
  ```bash
  ./gradlew testDebug
  ./gradlew connectedAndroidTest
  ```

### Debugging BLE Communication
- Use the `Logcat` tool in Android Studio to monitor BLE communication logs.
- JSON timelines can be modified in `app/src/main/assets/timelines/` for testing.

## Project-Specific Conventions
- **Dependency Injection**: Uses Dagger/Hilt for managing dependencies.
- **JSON Format**: Timelines follow the structure defined in `docs/specifications/03_TIMELINE_FORMAT.md`.
- **BLE Protocol**: Refer to `docs/specifications/02_BLE_COMMUNICATION.md` for details.
- **Code Style**: Follow Kotlin coding conventions. Use Android Studio's formatter.

## Integration Points
- **BLE Communication**: Android app communicates with ESP32 devices using BLE. Ensure UUIDs match those defined in `docs/specifications/02_BLE_COMMUNICATION.md`.
- **Effect Synchronization**: JSON timelines are parsed and sent to ESP32 devices for synchronized playback.

## External Dependencies
- **Gradle**: Build system for the Android app.
- **PlatformIO**: Used for ESP32 firmware development.
- **Android Studio**: Recommended IDE for Android development.

## Examples
### Adding a New Effect
1. Define the effect in a JSON timeline file under `app/src/main/assets/timelines/`.
2. Update the Android app to handle the new effect type in the BLE communication logic.
3. Modify the ESP32 firmware to implement the effect.

### Debugging a Timeline Issue
1. Check the JSON file in `app/src/main/assets/timelines/`.
2. Use `Logcat` to verify the timeline is sent correctly over BLE.
3. Debug the ESP32 firmware to ensure the effect is executed as expected.

---

For more details, refer to the [README.md](../README.md) and the documentation in `docs/specifications/`.