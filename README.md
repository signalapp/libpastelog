# libpastelog
A simple library that provides an easy fragment allowing users to throw debug logs in a pastebin
(currently [gist](https://gist.github.com)) online.

## Usage
libpastelog can be included in your Android app as a Maven dependency or library module.

### Maven dependency
Add the following lines to the *repositories* block of your *build.gradle* file:

```gradle
maven {
  url "https://raw.github.com/whispersystems/maven/master/libpastelog/releases/"
}
```

### Library module dependency
Copy libpastelog into the base directory of your project and modify *settings.gradle* to contain:  

```gradle
include ':libpastelog'
````

Then within the *dependencies* block of *build.gradle* add the following:  

```gradle
compile project(':libpastelog')
```

## Building
You may use the regular `gradlew build` command to build libpastelog, additionally you may use the
`uploadArchives` task to create your own Maven artifact. Artifacts created by the `uploadArchives`
task will be found in `build/mvn-repo`.

## License

Copyright 2014 Open Whisper Systems

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html