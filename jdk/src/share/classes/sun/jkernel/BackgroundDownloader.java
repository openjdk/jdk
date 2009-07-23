/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package sun.jkernel;

import java.io.*;

/**
 * Invoked by DownloadManager to begin (in a new JRE) the process of downloading
 * all remaining JRE components in the background.  A mutex is used to ensure
 * that only one BackgroundDownloader can be active at a time.
 *
 */
public class BackgroundDownloader {
    public static final String BACKGROUND_DOWNLOAD_PROPERTY = "kernel.background.download";
    // relative to the bundle directory
    public static final String PID_PATH = "tmp" + File.separator + "background.pid";

    // Time to wait before beginning to download components.  Gives the JRE
    // which spawned this one a chance to get its downloads going.
    private static final int WAIT_TIME = 10000;

    private static Mutex backgroundMutex;

    static synchronized Mutex getBackgroundMutex() {
        if (backgroundMutex == null)
            backgroundMutex = Mutex.create(DownloadManager.MUTEX_PREFIX + "background");
        return backgroundMutex;
    }

    private static void doBackgroundDownloads() {
        if (DownloadManager.isJREComplete())
            return;
        if (getBackgroundMutex().acquire(0)) { // give up and exit immediately if we can't acquire mutex
            try {
                writePid();
                Thread.sleep(WAIT_TIME);
                DownloadManager.doBackgroundDownloads(false);
                DownloadManager.performCompletionIfNeeded();
            }
            catch (InterruptedException e) {
            }
            finally {
                getBackgroundMutex().release();
            }
        }
        else {
            System.err.println("Unable to acquire background download mutex.");
            System.exit(1);
        }
    }


    /**
     * Writes the current process ID to a file, so that the uninstaller can
     * find and kill this process if needed.
     */
    private static void writePid() {
        try {
            File pid = new File(DownloadManager.getBundlePath(), PID_PATH);
            pid.getParentFile().mkdirs();
            PrintStream out = new PrintStream(new FileOutputStream(pid));
            pid.deleteOnExit();
            out.println(DownloadManager.getCurrentProcessId());
            out.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * Reads from an InputStream until exhausted, writing all data to the
     * specified OutputStream.
     */
    private static void send(InputStream in, OutputStream out)
                                throws IOException {
        int c;
        byte[] buffer = new byte[2048];
        while ((c = in.read(buffer)) > 0)
            out.write(buffer, 0, c);
    }

     /*
      * Returns the value of the BACKGROUND_DOWNLOAD_PROPERTY.
      * Checks if system property has been set first
      * then checks if registry key to disable background download
      * has been set.
      */
     public static boolean  getBackgroundDownloadProperty(){
         /*
          * Check registry key value
          */
         boolean bgDownloadEnabled = getBackgroundDownloadKey();

         /*
          * Check system property - it should override the registry
          * key value.
          */
         if (System.getProperty(BACKGROUND_DOWNLOAD_PROPERTY) != null){
             bgDownloadEnabled = Boolean.valueOf(
                      System.getProperty(BACKGROUND_DOWNLOAD_PROPERTY));
         }
         return bgDownloadEnabled;

    }

    // This method is to retrieve the value of registry key
    // that disables background download.
    static native boolean getBackgroundDownloadKey();


    static void startBackgroundDownloads() {
        if (!getBackgroundDownloadProperty()){
            // If getBackgroundDownloadProperty() returns false
            // we're doing the downloads from this VM; we don't want to
            // spawn another one
            return;
        }

        // if System.err isn't initialized yet, it means the charsets aren't
        // available yet and we're going to run into trouble down below.  Wait
        // until it's ready.
        while (System.err == null) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                return;
            }
        }

        try {
            String args = "-D" + BACKGROUND_DOWNLOAD_PROPERTY + "=false -Xmx256m";
            String backgroundDownloadURL = DownloadManager.getBaseDownloadURL();

            // only set KERNEL_DOWNLOAD_URL_PROPERTY if we override
            // the default download url
            if (backgroundDownloadURL != null &&
                    backgroundDownloadURL.equals(
                    DownloadManager.DEFAULT_DOWNLOAD_URL) == false) {
                args += " -D" + DownloadManager.KERNEL_DOWNLOAD_URL_PROPERTY +
                        "=" + backgroundDownloadURL;
            };
            args += " sun.jkernel.BackgroundDownloader";
            final Process jvm = Runtime.getRuntime().exec("\"" + new File(System.getProperty("java.home"), "bin" +
                   File.separator + "java.exe") + "\" " + args);
            Thread outputReader = new Thread("kernelOutputReader") {
                public void run() {
                    try {
                        InputStream in = jvm.getInputStream();
                        send(in, new PrintStream(new ByteArrayOutputStream()));
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            outputReader.setDaemon(true);
            outputReader.start();

            Thread errorReader = new Thread("kernelErrorReader") {
                public void run() {
                    try {
                        InputStream in = jvm.getErrorStream();
                        send(in, new PrintStream(new ByteArrayOutputStream()));
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            errorReader.setDaemon(true);
            errorReader.start();
        }
        catch (Exception e) {
            e.printStackTrace();
            // TODO: error handling
        }
    }


    public static void main(String[] arg) {
        doBackgroundDownloads();
    }
}
