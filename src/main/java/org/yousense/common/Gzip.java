package org.yousense.common;

import android.util.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Stand-alone version based on https://github.com/mattiaslinnap/yousense-android-upload
 * Redistributed with permission from Matthias Linnap, as the library is not available from
 * Maven Central.
 * 
 * @author ml421
 *
 */
public class Gzip {
    public static final String TAG = "Gzip";
    public static final int GZIP_ATTEMPTS = 3;  // How many times gzipping is attempted for a single call. It sometimes fails.

    private static TestCallback doNothing = new TestCallback();
    /**
     * Gzips the given file and deletes the original.
     * The returned filename has a .gz suffix.
     *
     * NOT thread-safe.
     */
    public static File gzip(File file) throws IOException {
        return testableGzip(file, doNothing);
    }

    /**
     * Returns true if the uncompressed file and the gzipped file have the same contents (apart from compression).
     */
    public static boolean contentEqualsGzip(File uncompressed, File gzippedTemp) throws IOException {
        if (uncompressed.getName().endsWith(".gz"))
            throw new IOException(String.format("Refusing to compare an uncompressed file that ends with .gz: %s", uncompressed.getAbsolutePath()));
        if (!gzippedTemp.getName().endsWith(".gz.temp"))
            throw new IOException(String.format("Refusing to compare a gzip file that does not end with .gz.temp: %s", gzippedTemp.getAbsolutePath()));
        FileInputStream uncompressedStream = new FileInputStream(uncompressed);
        GZIPInputStream gzipStream = new GZIPInputStream(new FileInputStream(gzippedTemp));
        try {
            try {
                return IOUtils.contentEquals(uncompressedStream, gzipStream);
            } finally {
                gzipStream.close();
            }
        } finally {
            uncompressedStream.close();
        }
    }

    /**
     * Reads the entire contents of a gzipped file into memory as a String.
     * UTF-8 encoding is assumed.
     * WARNING: Many Android versions have a low, <16MB memory limit for apps. Do not use on big files.
     */
    public static String readToString(File gzipped) throws IOException {
        if (!gzipped.getName().endsWith(".gz"))
            throw new IOException(String.format("Refusing to read a gzip file that does not end with .gz: %s", gzipped.getAbsolutePath()));
        StringWriter contents = new StringWriter();
        GZIPInputStream stream = new GZIPInputStream(new FileInputStream(gzipped));
        IOUtils.copy(stream, contents, "UTF-8");
        stream.close();
        return contents.toString();
    }

    /**
     * Actual gzip code. Callback is called right before the gzipping and checks are done, to enable simulating failures.
     */
    static File testableGzip(File file, TestCallback callback) throws IOException {
        if (file.getName().endsWith(".gz"))
            throw new IOException(String.format("Refusing to gzip a file that already ends with .gz: %s", file.getAbsolutePath()));

        File finalFile = appendSuffix(file, ".gz");
        File tempFile  = appendSuffix(finalFile, ".temp");
        for (int i = 0; i < GZIP_ATTEMPTS; ++i) {
            callback.openGzip();
            GZIPOutputStream gzipStream = new GZIPOutputStream(new FileOutputStream(tempFile));
            try {
            	try {
                    callback.copy();
            		FileUtils.copyFile(file, gzipStream);
            	} finally {
                    callback.closeGzip();
            		gzipStream.close();
            	}

                callback.compare();
                if (!contentEqualsGzip(file, tempFile)) {
                    throw new IOException(String.format("Gzip output does not match original on attempt %d", i + 1));
                }
                
                // rename the .gz.temp file to .gz
                tempFile.renameTo(finalFile);

                // All ok. Delete original.
                file.delete();  // TODO: failure is ignored. But if the original survives, it is presumed to be re-gzipped and then deleted later.
                return finalFile;
            } catch (IOException e) {
                Log.e(TAG, String.format("Failed attempt %d of gzipping %s", i + 1, file.getAbsolutePath()), e);
                tempFile.delete();  // TODO: failure is ignored. But if the gzipped survives, it is presumed to be overwritten by a later gzip call.
            }
        }
        // Failed.
        throw new IOException(String.format("Failed all %d attempts to gzip %s", GZIP_ATTEMPTS, file.getAbsolutePath()));
    }

    private static File appendSuffix(File file, String suffix) throws IOException {
        if (file == null) {
            throw new IOException("File is null.");
        }
        checkValidSuffix(suffix);
        if (file.getAbsolutePath().endsWith(suffix))
            throw new IOException(String.format("File already has suffix %s: %s", suffix, file.getAbsolutePath()));
        return new File(file.getAbsolutePath() + suffix);
    }

    private static void checkValidSuffix(String suffix) throws IOException {
        if (suffix == null) {
            throw new IOException("Suffix is null.");
        } else {
            if ("".equals(suffix))
                throw new IOException("Suffix is empty string.");
            if (!suffix.startsWith("."))
                throw new IOException(String.format("Suffix must start with a dot: \"%s\".", suffix));
            if (suffix.length() < 2)
                throw new IOException(String.format("Suffix \"%s\" is too short. Must be (dot)[a-z]{1,10}.", suffix));
            if (suffix.length() > 11)
                throw new IOException(String.format("Suffix \"%s\" is too long. Must be (dot)[a-z]{1,10}.", suffix));
            if (!StringUtils.containsOnly(suffix.substring(1, suffix.length()), "abcdefghijklmnopqrstuvwxyz"))
                throw new IOException(String.format("Suffix \"%s\" contains weird characters. Must be (dot)[a-z]{1,10}.", suffix));
        }
    }

    static class TestCallback {
        int attempts;  // This is not updated unless a child class overrides callback methods.
        void openGzip() throws IOException {}
        void copy() throws IOException {}
        void closeGzip() throws IOException {}
        void compare() throws IOException {}
    }
}
