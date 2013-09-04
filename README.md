android-async-logger
====================

An easy-to-use, asynchronous data logging library for Android. In contrast to other logging frameworks, this is meant as a convenient way of logging data, even though it could be used to log traditional debug/info/warning/error log messages with some tweaking.

To improve preformance a background thread takes care of all disk operations, and each log statement only enqueues the requested data to be logged in the future. When a call to `close()` returns, all data has been written to disk. Further calls to `log()` will then return `false` to indicate that data will not be persisted.

When instantiating a logger, a path must be specified where log files will be generated. Typically, this will be on the SD card or other external storage. Please remember to grant `android.permission.WRITE_EXTERNAL_STORAGE` permissions to your app.

Log files are named `yyyy-mm-dd-hh-mm-ss-i.log` by default, where date and time are fixed during instantiation, and i increments when `roll()` is called, or the file has grown past a pre-defined size (10MB by default). Rotated files can be gzipped by calling the `gzip()` method.

Notably, gzipping is done in a way that ensures content is not truncated. The Android implementation of `GzipOutputStream` sometimes silently truncates output files without raising any exceptions, which will lead to data loss if not checked for. This library checks if truncation occurred and repeatedly compresses until successful (or a predefined number of attempts was made).

```java
Context ctx; // provide this yourself
AsyncLogger logger = new AsyncLogger(ctx, ctx.getExternalFilesDir(null));
logger.log("hello world");
logger.log("hello %s %d", "world", 2);
logger.close(); // when this call returns, all data is persisted.
```
## TODOs

- It would be good to have serializers that take in an Object and turn that into a byte stream rather than relying on `Object.toString()` as an intermediate form that produces garbage.
- Make time string configurable

## License

This software is released under the Apache License, Version 2.0
