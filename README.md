# Lia AI
Run `gradle wrapper --gradle-version 9.1.0` once (or let Android Studio sync) to create gradlew + gradle-wrapper.jar.

    ./gradlew assembleDirectDebug
    ./gradlew assemblePlayDebug

Release: copy keystore.properties.example -> keystore.properties, then ./gradlew assemblePlayRelease
