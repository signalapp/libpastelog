# libpastelog
A simple library that provides an easy fragment allowing users to throw debug logs in a pastebin (currently [gist](https://gist.github.com)) online.

## Usage
Include libpastelog in your Android app as a Maven dependency by adding the following lines to your `build.gradle` file:

```gradle
...
repositories {
  ...
  maven {
    url "https://raw.github.com/whispersystems/maven/master/libpastelog/releases/"
  }
}
...
dependencies {
  ...
  compile 'org.whispersystems:libpastelog:1.0.0'
}
```

Check out the sample application in `/sample`, note that:
  1. `@color/libpastelog_confirmation_background` can overridden within your app.
  2. `android.permission.INTERNET` is required.
  3. the parent activity must not be recreated by orientation changes `android:configChanges="touchscreen|keyboard|keyboardHidden|orientation|screenLayout|screenSize"`.

## Building
`gradle build` - build the sample and library projects.  
`gradle uploadArchives` - create a maven artifact in `/library/build/mvn-repo`.

## License

Copyright 2014 Open Whisper Systems

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html