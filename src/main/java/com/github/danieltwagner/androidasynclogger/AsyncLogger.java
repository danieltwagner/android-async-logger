package com.github.danieltwagner.androidasynclogger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.io.output.CountingOutputStream;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

public class AsyncLogger {
    public static final String TAG = "AsyncLogger";
	private static final long ROTATE_SIZE = 10*1024*1024L; // 10MB
	
	private BlockingQueue<String> storeQ = new LinkedBlockingQueue<String>();

    private Context mCtx;
	private String storageDir;
    private String filePrefix;
    private int fileNumber = 0;
	private File currentFile;
	private CountingOutputStream cos;
	private OutputStreamWriter fos;
    private boolean persist = true;

    /**
	 * Instantiates a new AsyncLogger and starts persisting to a new file.
	 * @param path The absolute path of a directory where new files should be placed.
	 */
	public AsyncLogger(Context ctx, String path) {
        this.mCtx = ctx;
		this.storageDir = path;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-");
        filePrefix = df.format(new Date());
		logWorker.setDaemon(true);
		logWorker.start();
	}
	
	public boolean log(String what) {
		try {
			storeQ.add(new Date() + " " + what);
            return true;
		} catch (Exception e) {
    		Log.e(TAG, "Caught exception while adding to store queue! " + e);
    		e.printStackTrace();
    	}
		return false;
	}
	
	public boolean log(String format, Object... args) {
		return log(String.format(format, args));
	}
	
	public boolean roll() {
        try {
            internalClose();
            startNewFile();
            // gzip();
            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
	}

    public void close() {
        internalClose();
        persist = false;
    }
	
	private void internalClose() {
        if(fos == null) return;

        try {
            // close the old stream
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Caught exception while closing stream! " + e);
            e.printStackTrace();
        }
	}
	
	/**
     * Gzip all *.log files in the storage directory that are not gzipped.
     */
    public void gzip() {
    	PowerManager.WakeLock wl = null;
		try {
			Log.d(TAG, "Acquiring wake lock...");
			PowerManager pm = (PowerManager) mCtx.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
			wl.acquire();
	    	
	    	int count = 0;
	    	
	    	// make sure to do this in the correct order and don't continue if an error occurs, to preserve upload order
			try {
				File[] files = getFilesWithSuffix(storageDir, ".log");
	    		for(File f : files) {
	    			if((currentFile == null) || !f.getAbsolutePath().equals(currentFile.getAbsolutePath())) {
	    				//Gzip.gzip(f);
	    				count++;
	    				Log.d(TAG, "Finished gzipping '" + f.getAbsolutePath() + "'");
	    			}
	    		}
			} catch (Exception e) {
				Log.e(TAG, "Exception when gzipping file: " + e);
				e.printStackTrace();
			}
			
			Log.i(TAG, "Finished gzipping " + count + " files.");
		} finally {
			if((wl != null) && (wl.isHeld())) {
				wl.release();
				Log.d(TAG, "Released wake lock.");
			}
		}
    }

    private File[] getFilesWithSuffix(final String directory, final String suffix) {
        File[] files = mCtx.getDir(directory, Context.MODE_PRIVATE).listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getAbsolutePath().endsWith(suffix);
            }
        });
        if((files == null) || (files.length == 0)) return new File[0];
        return files;
    }

    // ------------------- internal methods ----------------------
	
	/**
     * Writes data to disk.
     * @param what The String that should be stored.
     */
    private synchronized void realStore(String what) {
        try {
            if(currentFile == null) {
                startNewFile();
                if(currentFile == null) throw new IllegalStateException("No current file set in realStore!");
            }

            if(cos.getByteCount() > ROTATE_SIZE) {
                Log.d(TAG, "File '" + currentFile.getAbsolutePath() + "' is larger than " + ROTATE_SIZE + " bytes. Rotating file.");
                roll();
            }

            // Write to the FileOutputStream
            fos.write(what);
        } catch (Exception e) {
            Log.e(TAG, "Caught exception while writing to stream! " + e);
            e.printStackTrace();
        }
    }

    /**
     * Creates a new file and sets up cos and fos to operate on that file.
     * @throws IOException
     */
    private void startNewFile() throws IOException {
		String name = filePrefix + fileNumber++ + ".log";
        currentFile = new File(storageDir, name);
        if(!currentFile.createNewFile()) throw new IOException("Cannot create file " + currentFile.getAbsolutePath());
		Log.d(TAG, "Created file '" + currentFile.getAbsolutePath() + "'");
        cos = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(currentFile, true)));
        fos = new OutputStreamWriter(cos, Charset.forName("UTF-8"));
	}
	
	private Thread logWorker = new Thread("Log Worker") {
    	
		@Override
		public void run() {
			String entry;
			Log.i(TAG, "Log Worker is now running.");
			
			while(persist) {
				try {
					// if the queue is empty, flush to disk.
					if(storeQ.size() == 0) fos.flush();
					// get the next element; block if the queue is empty
					entry = storeQ.take();
					
					// store the data and tell anyone who's interested
					realStore(entry);
					
				} catch (Exception e) {
					Log.w(TAG, "Caught exception in log worker: " + e);
		    		e.printStackTrace();
		    	}
			}
		}
    };
}
