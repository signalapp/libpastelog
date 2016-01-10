# libpastelog
A simple library that provides an easy fragment allowing users to throw debug logs in a pastebin (currently [gist](https://gist.github.com)) online.

## Download
Gradle:
```gradle
compile 'org.whispersystems:libpastelog:1.0.7'
```

Maven:
```xml
<dependency>
  <groupId>org.whispersystems</groupId>
  <artifactId>libpastelog</artifactId>
  <version>1.0.7</version>
</dependency>
```

Or download the [latest JAR directly](https://repo1.maven.org/maven2/org/whispersystems/libpastelog/1.0.7/libpastelog-1.0.7.aar).

## Usage
Check out the sample application in `/sample`, note that:
  1. `@color/libpastelog_confirmation_background` can overridden within your app.
  2. `android.permission.INTERNET` is required.
  3. the parent activity must not be recreated by orientation changes `android:configChanges="touchscreen|keyboard|keyboardHidden|orientation|screenLayout|screenSize"`.

## Building
`./gradlew build` - build the sample and library projects.

## License

Copyright 2014 Open Whisper Systems

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html
