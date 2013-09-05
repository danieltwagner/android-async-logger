android-async-logger
====================

An easy-to-use, asynchronous data logging library for Android. In contrast to other logging frameworks, this is meant as a convenient way of logging data, even though it could be used to log traditional debug/info/warning/error log messages with some tweaking.

To improve preformance a background thread takes care of all disk operations, and each log statement only enqueues the requested data to be logged in the future. When a call to `close()` returns, all data has been written to disk. Further calls to `log()` will then return `false` to indicate that data will not be persisted.

When instantiating a logger, a path must be specified where log files will be generated. Typically, this will be on the SD card or other external storage. Please remember to grant `android.permission.WRITE_EXTERNAL_STORAGE` permissions to your app.

Log files are named `yyyy-MM-dd-HH-mm-ss-i.log` by default, where date and time are fixed during instantiation, and i increments when `roll()` is called, or the file has grown past a pre-defined size (10MB by default). Rotated files can be gzipped by calling the `gzip()` method.

Notably, gzipping is done in a way that ensures content is not truncated. The Android implementation of `GzipOutputStream` sometimes silently truncates output files without raising any exceptions, which will lead to data loss if not checked for. This library checks if truncation occurred and repeatedly compresses until successful (or a predefined number of attempts was made).

## Installation and usage

Gradle:
```
dependencies {
    compile 'com.github.danieltwagner:android-async-logger:0.1.0@aar'
}
```

Maven:
```
<dependency>
    <groupId>com.github.danieltwagner</groupId>
    <artifactId>android-async-logger</artifactId>
    <version>0.1.0</version>
    <classifier>apklib</classifier>
    <type>apklib</type>
</dependency>
```

```java
Context ctx; // provide this yourself
AsyncLogger logger = new AsyncLogger(ctx, ctx.getExternalFilesDir(null));
logger.log("hello world");
logger.log("hello %s %d", "world", 2);
logger.close(); // when this call returns, all data is persisted.
```

## TODOs

- It would be good to have serializers that take in an Object and turn that into a byte stream rather than relying on `Object.toString()` as an intermediate form that produces garbage.
- Make time string configurable and not produce lots of garbage

## License

    Copyright 2013 Daniel Wagner

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
