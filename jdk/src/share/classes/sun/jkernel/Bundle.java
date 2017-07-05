/*
 * Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.net.HttpRetryException;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.zip.GZIPInputStream;

/**
 * Represents a bundle which may or may not currently be installed.
 *
 *@author Ethan Nicholas
 */
public class Bundle {
    static {
        if (!DownloadManager.jkernelLibLoaded) {
            // This code can be invoked directly by the deploy build.
            System.loadLibrary("jkernel");
        }
    }
    /**
     * Compress file sourcePath with "extra" algorithm (e.g. 7-Zip LZMA)
     * if available, put the uncompressed data into file destPath and
     * return true. If not available return false and do nothing with destPath.
     *
     * @param srcPath path to existing uncompressed file
     * @param destPath path for the compressed file to be created
     * @returns true if extra algorithm used, false if not
     * @throws IOException if the extra compression code should be available
     *     but cannot be located or linked to, the destination file already
     *     exists or cannot be opened for writing, or the compression fails
     */
    public static native boolean extraCompress(String srcPath,
        String destPath) throws IOException;

    /**
     * Decompress file sourcePath with "extra" algorithm (e.g. 7-Zip LZMA)
     * if available, put the uncompressed data into file destPath and
     * return true. If not available return false and do nothing with
     * destPath.
     * @param srcPath path to existing compressed file
     * @param destPath path to uncompressed file to be created
     * @returns true if extra algorithm used, false if not
     * @throws IOException if the extra uncompression code should be available
     *     but cannot be located or linked to, the destination file already
     *     exists or cannot be opened for writing, or the uncompression fails
     */
    public static native boolean extraUncompress(String srcPath,
        String destPath) throws IOException;

    private static final String BUNDLE_JAR_ENTRY_NAME = "classes.jar";

    /** The bundle is not present. */
    protected static final int NOT_DOWNLOADED = 0;

    /**
     * The bundle is in the download queue but has not finished downloading.
     */
    protected static final int QUEUED = 1;

    /** The bundle has finished downloading but is not installed. */
    protected static final int DOWNLOADED = 2;

    /** The bundle is fully installed and functional. */
    protected static final int INSTALLED = 3;

    /** Thread pool used to manage dependency downloads. */
    private static ExecutorService threadPool;

    /** Size of thread pool. */
    static final int THREADS;

    static {
        String downloads = System.getProperty(
                DownloadManager.KERNEL_SIMULTANEOUS_DOWNLOADS_PROPERTY);
        if (downloads != null)
            THREADS = Integer.parseInt(downloads.trim());
        else
            THREADS = 1;
    }

    /** Mutex used to safely access receipts file. */
    private static Mutex receiptsMutex;

    /** Maps bundle names to known bundle instances. */
    private static Map<String, Bundle> bundles =
            new HashMap<String, Bundle>();

    /** Contains the names of currently-installed bundles. */
    static Set<String> receipts = new HashSet<String>();

    private static int bytesDownloaded;

    /** Path where bundle receipts are written. */
    private static File receiptPath = new File(DownloadManager.getBundlePath(),
            "receipts");

    /** The size of the receipts file the last time we saw it. */
    private static int receiptsSize;

    /** The bundle name, e.g. "java_awt". */
    private String name;

    /** The path to which we are saving the downloaded bundle file. */
    private File localPath;

    /**
     * The path of the extracted JAR file containing the bundle's classes.
     */
    private File jarPath;

    // for vista IE7 protected mode
    private File lowJarPath;
    private File lowJavaPath = null;

    /** The current state (DOWNLOADED, INSTALLED, etc.). */
    protected int state;

    /**
     * True if we should delete the downloaded bundle after installing it.
     */
    protected boolean deleteOnInstall = true;

    private static Mutex getReceiptsMutex() {
        if (receiptsMutex == null)
            receiptsMutex = Mutex.create(DownloadManager.MUTEX_PREFIX +
                    "receipts");
        return receiptsMutex;
    }


    /**
     * Reads the receipts file in order to seed the list of currently
     * installed bundles.
     */
    static synchronized void loadReceipts() {
        getReceiptsMutex().acquire();
        try {
            if (receiptPath.exists()) {
                int size = (int) receiptPath.length();
                if (size != receiptsSize) { // ensure that it has actually
                                            // been modified
                    DataInputStream in = null;
                    try {
                        receipts.clear();
                        for (String bundleName : DownloadManager.getBundleNames()) {
                            if ("true".equals(DownloadManager.getBundleProperty(bundleName,
                                    DownloadManager.INSTALL_PROPERTY)))
                                receipts.add(bundleName);
                        }
                        if (receiptPath.exists()) {
                            in = new DataInputStream(new BufferedInputStream(
                                    new FileInputStream(receiptPath)));
                            String line;
                            while ((line = in.readLine()) != null) {
                                receipts.add(line.trim());
                            }
                        }
                        receiptsSize = size;
                    }
                    catch (IOException e) {
                        DownloadManager.log(e);
                        // safe to continue, as the worst that happens is
                        // we re-download existing bundles
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException ioe) {
                                DownloadManager.log(ioe);
                            }
                        }
                    }
                }
            }
        }
        finally {
            getReceiptsMutex().release();
        }
    }


    /** Returns the bundle corresponding to the specified name. */
    public static synchronized Bundle getBundle(String bundleId)
            throws IOException {
        Bundle result =(Bundle) bundles.get(bundleId);
        if (result == null && (bundleId.equals("merged") ||
                Arrays.asList(DownloadManager.getBundleNames()).contains(bundleId))) {
            result = new Bundle();
            result.name = bundleId;

            if (DownloadManager.isWindowsVista()) {
                result.localPath =
                        new File(DownloadManager.getLocalLowTempBundlePath(),
                                 bundleId + ".zip");
                result.lowJavaPath = new File(
                        DownloadManager.getLocalLowKernelJava() + bundleId);
            } else {
                result.localPath = new File(DownloadManager.getBundlePath(),
                        bundleId + ".zip");
            }

            String jarPath = DownloadManager.getBundleProperty(bundleId,
                    DownloadManager.JAR_PATH_PROPERTY);
            if (jarPath != null) {
                if (DownloadManager.isWindowsVista()) {
                    result.lowJarPath = new File(
                        DownloadManager.getLocalLowKernelJava() + bundleId,
                        jarPath);
                }
                result.jarPath = new File(DownloadManager.JAVA_HOME,
                        jarPath);

            } else {

                if (DownloadManager.isWindowsVista()) {
                    result.lowJarPath = new File(
                        DownloadManager.getLocalLowKernelJava() + bundleId +
                            "\\lib\\bundles",
                        bundleId + ".jar");
                }

                result.jarPath = new File(DownloadManager.getBundlePath(),
                        bundleId + ".jar");

            }

            bundles.put(bundleId, result);
        }
        return result;
    }


    /**
     * Returns the name of this bundle.  The name is typically defined by
     * the bundles.xml file.
     */
    public String getName() {
        return name;
    }


    /**
     * Sets the name of this bundle.
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     * Returns the path to the bundle file on the local filesystem.  The file
     * will only exist if the bundle has already been downloaded;  otherwise
     * it will be created when download() is called.
     */
    public File getLocalPath() {
        return localPath;
    }


    /**
     * Sets the location of the bundle file on the local filesystem.  If the
     * file already exists, the bundle will be considered downloaded;
     * otherwise the file will be created when download() is called.
     */
    public void setLocalPath(File localPath) {
        this.localPath = localPath;
    }


    /**
     * Returns the path to the extracted JAR file containing this bundle's
     * classes.  This file should only exist after the bundle has been
     * installed.
     */
    public File getJarPath() {
        return jarPath;
    }


    /**
     * Sets the path to the extracted JAR file containing this bundle's
     * classes.  This file will be created as part of installing the bundle.
     */
    public void setJarPath(File jarPath) {
        this.jarPath = jarPath;
    }


    /**
     * Returns the size of the bundle download in bytes.
     */
    public int getSize() {
        return Integer.valueOf(DownloadManager.getBundleProperty(getName(),
                DownloadManager.SIZE_PROPERTY));
    }


    /**
     * Returns true if the bundle file (getLocalPath()) should be deleted
     * when the bundle is successfully installed.  Defaults to true.
     */
    public boolean getDeleteOnInstall() {
        return deleteOnInstall;
    }


    /**
     * Sets whether the bundle file (getLocalPath()) should be deleted
     * when the bundle is successfully installed.  Defaults to true.
     */
    public void setDeleteOnInstall(boolean deleteOnInstall) {
        this.deleteOnInstall = deleteOnInstall;
    }


    /** Sets the current state of this bundle to match reality. */
    protected void updateState() {
        synchronized(Bundle.class) {
            loadReceipts();
            if (receipts.contains(name) ||
                    "true".equals(DownloadManager.getBundleProperty(name,
                    DownloadManager.INSTALL_PROPERTY)))
                state = Bundle.INSTALLED;
            else if (localPath.exists())
                state = Bundle.DOWNLOADED;
        }
    }


    private String getURL(boolean showUI) throws IOException {
        Properties urls = DownloadManager.getBundleURLs(showUI);
        String result = urls.getProperty(name + ".zip");
        if (result == null) {
            result = urls.getProperty(name);
            if (result == null) {
                DownloadManager.log("Unable to determine bundle URL for " + this);
                DownloadManager.log("Bundle URLs: " + urls);
                DownloadManager.sendErrorPing(DownloadManager.ERROR_NO_SUCH_BUNDLE);

                throw new NullPointerException("Unable to determine URL " +
                        "for bundle: " + this);
            }
        }
        return result;
    }


    /**
     * Downloads the bundle.  This method blocks until the download is
     * complete.
     *
     *@param showProgress true to display a progress dialog
     */
    private void download(boolean showProgress) {
        if (DownloadManager.isJREComplete())
            return;
        Mutex mutex = Mutex.create(DownloadManager.MUTEX_PREFIX + name +
                ".download");
        mutex.acquire();
        try {
            long start = System.currentTimeMillis();

            boolean retry;

            do {
                retry = false;
                updateState();
                if (state == DOWNLOADED || state == INSTALLED) {
                    return;
                }
                File tmp = null;
                try {
                    tmp = new File(localPath + ".tmp");

                    // tmp.deleteOnExit();

                    if (DownloadManager.getBaseDownloadURL().equals(
                            DownloadManager.RESOURCE_URL)) {
                        // RESOURCE_URL is used during build process, to
                        // avoid actual network traffic.  This is called in
                        // the SplitJRE DownloadTest to determine which
                        // classes are needed to support downloads, but we
                        // bypass the actual HTTP download to simplify the
                        // build process (it's all native code, so from
                        // DownloadTest's standpoint it doesn't matter if we
                        // really call it or not).
                        String path = "/" + name + ".zip";
                        InputStream in =
                                getClass().getResourceAsStream(path);
                        if (in == null)
                            throw new IOException("could not locate " +
                                    "resource: " + path);
                        FileOutputStream out = new FileOutputStream(tmp);
                        DownloadManager.send(in, out);
                        in.close();
                        out.close();
                    }
                    else {
                        try {
                            String bundleURL = getURL(showProgress);
                            DownloadManager.log("Downloading from: " +
                                        bundleURL);
                            DownloadManager.downloadFromURL(bundleURL, tmp,
                                    name.replace('_', '.'), showProgress);
                        }
                        catch (HttpRetryException e) {
                            // Akamai returned a 403, get new URL
                            DownloadManager.flushBundleURLs();
                            String bundleURL = getURL(showProgress);
                            DownloadManager.log("Retrying at new " +
                                        "URL: " + bundleURL);
                            DownloadManager.downloadFromURL(bundleURL, tmp,
                                    name.replace('_', '.'),
                                    showProgress);
                            // we intentionally don't do a 403 retry
                            // again, to avoid infinite retries
                        }
                    }
                    if (!tmp.exists() || tmp.length() == 0) {
                        if (showProgress) {
                            // since showProgress = true, native code should
                            // have offered to retry.  Since we ended up here,
                            // we conclude that download failed & user opted to
                            // cancel.  Set complete to true to stop bugging
                            // him in the future (if one bundle fails, the
                            // rest are virtually certain to).
                            DownloadManager.complete = true;
                        }
                        DownloadManager.fatalError(DownloadManager.ERROR_UNSPECIFIED);
                    }

                    /**
                     * Bundle security
                     *
                     * Check for corruption/spoofing
                     */


                    /* Create a bundle check from the tmp file */
                    BundleCheck gottenCheck = BundleCheck.getInstance(tmp);

                    /* Get the check expected for the Bundle */
                    BundleCheck expectedCheck = BundleCheck.getInstance(name);

                    // Do they match?

                    if (expectedCheck.equals(gottenCheck)) {

                        // Security check OK, uncompress the bundle file
                        // into the local path

                        long uncompressedLength = tmp.length();
                        localPath.delete();

                        File uncompressedPath = new File(tmp.getPath() +
                            ".jar0");
                        if (! extraUncompress(tmp.getPath(),
                            uncompressedPath.getPath())) {
                            // Extra uncompression not available, fall
                            // back to alternative if it is enabled.
                            if (DownloadManager.debug) {
                                DownloadManager.log("Uncompressing with GZIP");
                            }
                            GZIPInputStream in = new GZIPInputStream( new
                                BufferedInputStream(new FileInputStream(tmp),
                                DownloadManager.BUFFER_SIZE));
                            BufferedOutputStream out = new BufferedOutputStream(
                                new FileOutputStream(uncompressedPath),
                                DownloadManager.BUFFER_SIZE);
                            DownloadManager.send(in,out);
                            in.close();
                            out.close();
                            if (! uncompressedPath.renameTo(localPath)) {
                                throw new IOException("unable to rename " +
                                    uncompressedPath + " to " + localPath);
                            }
                        } else {
                            if (DownloadManager.debug) {
                                DownloadManager.log("Uncompressing with LZMA");
                            }
                            if (! uncompressedPath.renameTo(localPath)) {
                                throw new IOException("unable to rename " +
                                    uncompressedPath + " to " + localPath);
                            }
                        }
                        state = DOWNLOADED;
                        bytesDownloaded += uncompressedLength;
                        long time = (System.currentTimeMillis() -
                                start);
                        DownloadManager.log("Downloaded " + name +
                                " in " + time + "ms.  Downloaded " +
                                bytesDownloaded + " bytes this session.");

                        // Normal completion
                    } else {

                        // Security check not OK: remove the temp file
                        // and consult the user

                        tmp.delete();

                        DownloadManager.log(
                                "DownloadManager: Security check failed for " +
                                "bundle " + name);

                        // only show dialog if we are not in silent mode
                        if (showProgress) {
                            retry = DownloadManager.askUserToRetryDownloadOrQuit(
                                    DownloadManager.ERROR_UNSPECIFIED);
                        }

                        if (!retry) {
                            // User wants to give up
                            throw new RuntimeException(
                                "Failed bundle security check and user " +
                                "canceled");
                        }
                    }
                }
                catch (IOException e) {
                    // Look for "out of space" using File.getUsableSpace()
                    // here when downloadFromURL starts throwing IOException
                    // (or preferably a distinct exception for this case).
                    DownloadManager.log(e);
                }
            } while (retry);
        } finally {
            mutex.release();
        }
    }


    /**
     * Calls {@link #queueDownload()} on all of this bundle's dependencies.
     */
    void queueDependencies(boolean showProgress) {
        try {
            String dependencies =
                    DownloadManager.getBundleProperty(name,
                    DownloadManager.DEPENDENCIES_PROPERTY);
            if (dependencies != null) {
                StringTokenizer st = new StringTokenizer(dependencies,
                        " ,");
                while (st.hasMoreTokens()) {
                    Bundle b = getBundle(st.nextToken());
                    if (b != null && !b.isInstalled()) {
                        if (DownloadManager.debug) {
                            DownloadManager.log("Queueing " + b.name +
                                    " as a dependency of " + name + "...");
                        }
                        b.install(showProgress, true, false);
                    }
                }
            }
        } catch (IOException e) {
            // shouldn't happen
            DownloadManager.log(e);
        }
    }


    static synchronized ExecutorService getThreadPool() {
        if (threadPool == null) {
            threadPool = Executors.newFixedThreadPool(THREADS,
                            new ThreadFactory () {
                                public Thread newThread(Runnable r) {
                                    Thread result = new Thread(r);
                                    result.setDaemon(true);
                                    return result;
                                }
                            }
                        );
        }
        return threadPool;
    }


    private void unpackBundle() throws IOException {
        File useJarPath = null;
        if (DownloadManager.isWindowsVista()) {
            useJarPath = lowJarPath;
            File jarDir = useJarPath.getParentFile();
            if (jarDir != null) {
                jarDir.mkdirs();
            }
        } else {
            useJarPath = jarPath;
        }

        DownloadManager.log("Unpacking " + this + " to " + useJarPath);

        InputStream rawStream = new FileInputStream(localPath);
        JarInputStream in = new JarInputStream(rawStream) {
            public void close() throws IOException {
                // prevent any sub-processes here from actually closing the
                // input stream; we'll use rawsStream.close() when we're
                // done with it
            }
        };

        try {
            File jarTmp = null;
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.equals("classes.pack")) {
                    File packTmp = new File(useJarPath + ".pack");
                    packTmp.getParentFile().mkdirs();
                    DownloadManager.log("Writing temporary .pack file " + packTmp);
                    OutputStream tmpOut = new FileOutputStream(packTmp);
                    try {
                        DownloadManager.send(in, tmpOut);
                    } finally {
                        tmpOut.close();
                    }
                    // we unpack to a temporary file and then, towards the end
                    // of this method, use a (hopefully atomic) rename to put it
                    // into its final location; this should avoid the problem of
                    // partially-completed downloads.  Doing the rename last
                    // allows us to check for the presence of the JAR file to
                    // see whether the bundle has in fact been downloaded.
                    jarTmp = new File(useJarPath + ".tmp");
                    DownloadManager.log("Writing temporary .jar file " + jarTmp);
                    unpack(packTmp, jarTmp);
                    packTmp.delete();
                } else if (!entryName.startsWith("META-INF")) {
                    File dest;
                    if (DownloadManager.isWindowsVista()) {
                        dest = new File(lowJavaPath,
                            entryName.replace('/', File.separatorChar));
                    } else {
                        dest = new File(DownloadManager.JAVA_HOME,
                            entryName.replace('/', File.separatorChar));
                    }
                    if (entryName.equals(BUNDLE_JAR_ENTRY_NAME))
                        dest = useJarPath;
                    File destTmp = new File(dest + ".tmp");
                    boolean exists = dest.exists();
                    if (!exists) {
                        DownloadManager.log(dest + ".mkdirs()");
                        dest.getParentFile().mkdirs();
                    }
                    try {
                        DownloadManager.log("Using temporary file " + destTmp);
                        FileOutputStream out =
                                new FileOutputStream(destTmp);
                        try {
                            byte[] buffer = new byte[2048];
                            int c;
                            while ((c = in.read(buffer)) > 0)
                                out.write(buffer, 0, c);
                        } finally {
                            out.close();
                        }
                        if (exists)
                            dest.delete();
                        DownloadManager.log("Renaming from " + destTmp + " to " + dest);
                        if (!destTmp.renameTo(dest)) {
                            throw new IOException("unable to rename " +
                                    destTmp + " to " + dest);
                        }

                    } catch (IOException e) {
                        if (!exists)
                            throw e;
                        // otherwise the file already existed and the fact
                        // that we failed to re-write it probably just
                        // means that it was in use
                    }
                }
            }

            // rename the temporary jar into its final location
            if (jarTmp != null) {
                if (useJarPath.exists())
                    jarTmp.delete();
                else if (!jarTmp.renameTo(useJarPath)) {
                    throw new IOException("unable to rename " + jarTmp +
                            " to " + useJarPath);
                }
            }
            if (DownloadManager.isWindowsVista()) {
                // move bundle to real location
                DownloadManager.log("Using broker to move " + name);
                if (!DownloadManager.moveDirWithBroker(
                        DownloadManager.getKernelJREDir() + name)) {
                    throw new IOException("unable to create " + name);
                }
                DownloadManager.log("Broker finished " + name);
            }
            DownloadManager.log("Finished unpacking " + this);
        } finally {
            rawStream.close();
        }
        if (deleteOnInstall) {
            localPath.delete();
        }

    }


    public static void unpack(File pack, File jar) throws IOException {
        Process p = Runtime.getRuntime().exec(DownloadManager.JAVA_HOME + File.separator +
                "bin" + File.separator + "unpack200 -Hoff \"" + pack + "\" \"" + jar + "\"");
        try {
            p.waitFor();
        }
        catch (InterruptedException e) {
        }
    }


    /**
     * Unpacks and installs the bundle.  The bundle's classes are not
     * immediately added to the boot class path; this happens when the VM
     * attempts to load a class and calls getBootClassPathEntryForClass().
     */
    public void install() throws IOException {
        install(true, false, true);
    }


    /**
     * Unpacks and installs the bundle, optionally hiding the progress
     * indicator.  The bundle's classes are not immediately added to the
     * boot class path; this happens when the VM attempts to load a class
     * and calls getBootClassPathEntryForClass().
     *
     *@param showProgress true to display a progress dialog
     *@param downloadOnly true to download but not install
     *@param block true to wait until the operation is complete before returning
     */
    public synchronized void install(final boolean showProgress,
            final boolean downloadOnly, boolean block) throws IOException {
        if (DownloadManager.isJREComplete())
            return;
        if (state == NOT_DOWNLOADED || state == QUEUED) {
            // we allow an already-queued bundle to be placed into the queue
            // again, to handle the case where the bundle is queued with
            // downloadOnly true and then we try to queue it again with
            // downloadOnly false -- the second queue entry will actually
            // install it.
            if (state != QUEUED) {
                DownloadManager.addToTotalDownloadSize(getSize());
                state = QUEUED;
            }
            if (getThreadPool().isShutdown()) {
                if (state == NOT_DOWNLOADED || state == QUEUED)
                    doInstall(showProgress, downloadOnly);
            }
            else {
                Future task = getThreadPool().submit(new Runnable() {
                    public void run() {
                        try {
                            if (state == NOT_DOWNLOADED || state == QUEUED ||
                                    (!downloadOnly && state == DOWNLOADED)) {
                                doInstall(showProgress, downloadOnly);
                            }
                        }
                        catch (IOException e) {
                            // ignore
                        }
                    }
                });
                queueDependencies(showProgress);
                if (block) {
                    try {
                        task.get();
                    }
                    catch (Exception e) {
                        throw new Error(e);
                    }
                }
            }
        }
        else if (state == DOWNLOADED && !downloadOnly)
            doInstall(showProgress, false);
    }


    private void doInstall(boolean showProgress, boolean downloadOnly)
            throws IOException {
        Mutex mutex = Mutex.create(DownloadManager.MUTEX_PREFIX + name +
                ".install");
        DownloadManager.bundleInstallStart();
        try {
            mutex.acquire();
            updateState();
            if (state == NOT_DOWNLOADED || state == QUEUED) {
                download(showProgress);
            }

            if (state == DOWNLOADED && downloadOnly) {
                return;
            }

            if (state == INSTALLED) {
                return;
            }
            if (state != DOWNLOADED) {
                DownloadManager.fatalError(DownloadManager.ERROR_UNSPECIFIED);
            }

            DownloadManager.log("Calling unpackBundle for " + this);
            unpackBundle();
            DownloadManager.log("Writing receipt for " + this);
            writeReceipt();
            updateState();
            DownloadManager.log("Finished installing " + this + ", state=" + state);
        } finally {
            if (lowJavaPath != null) {
                lowJavaPath.delete();
            }
            mutex.release();
            DownloadManager.bundleInstallComplete();
        }
    }


    synchronized void setState(int state) {
        this.state = state;
    }


    /** Returns <code>true</code> if this bundle has been installed. */
    public boolean isInstalled() {
        synchronized (Bundle.class) {
            updateState();
            return state == INSTALLED;
        }
    }


    /**
     * Adds an entry to the receipts file indicating that this bundle has
     * been successfully downloaded.
     */
    private void writeReceipt() {
        getReceiptsMutex().acquire();
        File useReceiptPath = null;
        try {

            try {

                receipts.add(name);

                if (DownloadManager.isWindowsVista()) {
                    // write out receipts to locallow
                    useReceiptPath = new File(
                            DownloadManager.getLocalLowTempBundlePath(),
                            "receipts");

                    if (receiptPath.exists()) {
                        // copy original file to locallow location
                        DownloadManager.copyReceiptFile(receiptPath,
                                useReceiptPath);
                    }

                    // update receipt in locallow path
                    // only append if original receipt path exists
                    FileOutputStream out = new FileOutputStream(useReceiptPath,
                            receiptPath.exists());
                    out.write((name + System.getProperty("line.separator")).getBytes("utf-8"));
                    out.close();

                    // use broker to move back to real path
                    if (!DownloadManager.moveFileWithBroker(
                            DownloadManager.getKernelJREDir()
                        + "-bundles" + File.separator + "receipts")) {
                        throw new IOException("failed to write receipts");
                    }
                } else {
                    useReceiptPath = receiptPath;
                    FileOutputStream out = new FileOutputStream(useReceiptPath,
                            true);
                    out.write((name + System.getProperty("line.separator")).getBytes("utf-8"));
                    out.close();
                }


            } catch (IOException e) {
                DownloadManager.log(e);
                // safe to continue, as the worst that happens is we
                // re-download existing bundles
            }
        }
        finally {
            getReceiptsMutex().release();
        }
    }


    public String toString() {
        return "Bundle[" + name + "]";
    }
}
