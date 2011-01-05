/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.jkernel;

import java.io.*;
import java.net.URLStreamHandlerFactory;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.zip.*;
import sun.misc.BootClassLoaderHook;
import sun.misc.Launcher;
import sun.misc.URLClassPath;
import sun.net.www.ParseUtil;

/**
 * Handles the downloading of additional JRE components.  The bootstrap class
 * loader automatically invokes DownloadManager when it comes across a resource
 * that can't be located.
 *
 *@author Ethan Nicholas
 */
public class DownloadManager extends BootClassLoaderHook {
    public static final String KERNEL_DOWNLOAD_URL_PROPERTY =
            "kernel.download.url";
    public static final String KERNEL_DOWNLOAD_ENABLED_PROPERTY =
            "kernel.download.enabled";

    public static final String KERNEL_DOWNLOAD_DIALOG_PROPERTY =
            "kernel.download.dialog";

    public static final String KERNEL_DEBUG_PROPERTY = "kernel.debug";
    // disables JRE completion when set to true, used as part of the build
    // process
    public static final String KERNEL_NOMERGE_PROPERTY = "kernel.nomerge";

    public static final String KERNEL_SIMULTANEOUS_DOWNLOADS_PROPERTY =
            "kernel.simultaneous.downloads";

    // used to bypass some problems with JAR entry modtimes not matching.
    // originally was set to zero, but apparently the epochs are different
    // for zip and pack so the pack/unpack cycle was causing the modtimes
    // to change.  With some recent changes to the reconstruction, I'm
    // not sure if this is actually necessary anymore.
    public static final int KERNEL_STATIC_MODTIME = 10000000;

    // indicates that bundles should be grabbed using getResource(), rather
    // than downloaded from a network path -- this is used during the build
    // process
    public static final String RESOURCE_URL = "internal-resource/";
    public static final String REQUESTED_BUNDLES_PATH = "lib" + File.separator +
            "bundles" + File.separator + "requested.list";

    private static final boolean disableDownloadDialog = "false".equals(
            System.getProperty(KERNEL_DOWNLOAD_DIALOG_PROPERTY));

    static boolean debug = "true".equals(
            System.getProperty(KERNEL_DEBUG_PROPERTY));
    // points to stderr in case we need to println before System.err is
    // initialized
    private static OutputStream errorStream;
    private static OutputStream logStream;

    static String MUTEX_PREFIX;

    static boolean complete;

    // 1 if jbroker started; 0 otherwise
    private static int _isJBrokerStarted = -1;

    // maps bundle names to URL strings
    private static Properties bundleURLs;

    public static final String JAVA_HOME = System.getProperty("java.home");
    public static final String USER_HOME = System.getProperty("user.home");
    public static final String JAVA_VERSION =
            System.getProperty("java.version");
    static final int BUFFER_SIZE = 2048;

    static volatile boolean jkernelLibLoaded = false;

    public static String DEFAULT_DOWNLOAD_URL =
        "http://javadl.sun.com/webapps/download/GetList/"
        +  System.getProperty("java.runtime.version") + "-kernel/windows-i586/";

    private static final String CUSTOM_PREFIX = "custom";
    private static final String KERNEL_PATH_SUFFIX = "-kernel";

    public static final String JAR_PATH_PROPERTY = "jarpath";
    public static final String SIZE_PROPERTY = "size";
    public static final String DEPENDENCIES_PROPERTY = "dependencies";
    public static final String INSTALL_PROPERTY = "install";

    private static boolean reportErrors = true;

    static final int ERROR_UNSPECIFIED = 0;
    static final int ERROR_DISK_FULL   = 1;
    static final int ERROR_MALFORMED_BUNDLE_PROPERTIES = 2;
    static final int ERROR_DOWNLOADING_BUNDLE_PROPERTIES = 3;
    static final int ERROR_MALFORMED_URL = 4;
    static final int ERROR_RETRY_CANCELLED = 5;
    static final int ERROR_NO_SUCH_BUNDLE = 6;


    // tracks whether the current thread is downloading.  A count of zero means
    // not currently downloading, >0 means the current thread is downloading or
    // installing a bundle.
    static ThreadLocal<Integer> downloading = new ThreadLocal<Integer>() {
        protected Integer initialValue() {
            return 0;
        }
    };

    private static File[] additionalBootStrapPaths = { };

    private static String[] bundleNames;
    private static String[] criticalBundleNames;

    private static String downloadURL;

    private static boolean visitorIdDetermined;
    private static String visitorId;

    /**
     * File and path where the Check value properties are gotten from
     */
    public static String CHECK_VALUES_FILE = "check_value.properties";
    static String CHECK_VALUES_DIR = "sun/jkernel/";
    static String CHECK_VALUES_PATH = CHECK_VALUES_DIR + CHECK_VALUES_FILE;

    /**
     * The contents of the bundle.properties file, which contains various
     * information about individual bundles.
     */
    private static Map<String, Map<String, String>> bundleProperties;


    /**
     * The contents of the resource_map file, which maps resources
     * to their respective bundles.
     */
    private static Map<String, String> resourceMap;


    /**
     * The contents of the file_map file, which maps files
     * to their respective bundles.
     */
    private static Map<String, String> fileMap;

    private static boolean extDirDetermined;
    private static boolean extDirIncluded;

    static {
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                if (debug)
                    println("DownloadManager startup");

                 // this mutex is global and will apply to all different
                // version of java kernel installed on the local machine
                MUTEX_PREFIX = "jkernel";
                boolean downloadEnabled = !"false".equals(
                        System.getProperty(KERNEL_DOWNLOAD_ENABLED_PROPERTY));
                complete = !getBundlePath().exists() ||
                        !downloadEnabled;

                // only load jkernel.dll if we are not "complete".
                // DownloadManager will be loaded during build time, before
                // jkernel.dll is built.  We only need to load jkernel.dll
                // when DownloadManager needs to download something, which is
                // not necessary during build time
                if (!complete) {
                    loadJKernelLibrary();
                    log("Log opened");

                    if (isWindowsVista()) {
                        getLocalLowTempBundlePath().mkdirs();
                    }

                    new Thread() {
                        public void run() {
                            startBackgroundDownloads();
                        }
                    }.start();

                    try {
                        String dummyPath;
                        if (isWindowsVista()) {
                            dummyPath = USER_HOME +
                                    "\\appdata\\locallow\\dummy.kernel";
                        } else {
                            dummyPath = USER_HOME + "\\dummy.kernel";
                        }

                        File f = new File(dummyPath);
                        FileOutputStream out = new FileOutputStream(f, true);
                        out.close();
                        f.deleteOnExit();

                    } catch (IOException e) {
                        log(e);
                    }
                    // end of warm up code

                    new Thread("BundleDownloader") {
                        public void run() {
                            downloadRequestedBundles();
                        }
                    }.start();
                }
                return null;
            }
        });
    }


    static synchronized void loadJKernelLibrary() {
        if (!jkernelLibLoaded) {
            try {
                System.loadLibrary("jkernel");
                jkernelLibLoaded = true;
                debug = getDebugProperty();
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    static String appendTransactionId(String url) {
        StringBuilder result = new StringBuilder(url);
        String visitorId = DownloadManager.getVisitorId();
        if (visitorId != null) {
            if (url.indexOf("?") == -1)
                result.append('?');
            else
                result.append('&');
            result.append("transactionId=");
            result.append(DownloadManager.getVisitorId());
        }
        return result.toString();
    }


    /**
     * Returns the URL for the directory from which bundles should be
     * downloaded.
     */
    static synchronized String getBaseDownloadURL() {
        if (downloadURL == null) {
            log("Determining download URL...");
            loadJKernelLibrary();

            /*
             * First check if system property has been set - system
             * property should take over registry key setting.
             */
            downloadURL = System.getProperty(
                          DownloadManager.KERNEL_DOWNLOAD_URL_PROPERTY);
            log("System property kernel.download.url = " + downloadURL);

            /*
             * Now check if registry key has been set
             */
            if (downloadURL == null){
                downloadURL = getUrlFromRegistry();
                log("getUrlFromRegistry = " + downloadURL);
            }

            /*
             * Use default download url
             */
            if (downloadURL == null)
                downloadURL = DEFAULT_DOWNLOAD_URL;
            log("Final download URL: " + downloadURL);
        }
        return downloadURL;
    }


    /**
     * Loads a file representing a node tree.  The format is described in
     * SplitJRE.writeTreeMap().  The node paths (such as
     * core/java/lang/Object.class) are interpreted with the root node as the
     * value and the remaining nodes as
     * the key, so the mapping for this entry would be java/lang/Object.class =
     * core.
     */
    static Map<String, String> readTreeMap(InputStream rawIn)
            throws IOException {
        // "token level" refers to the 0-31 byte that occurs prior to every
        // token in the stream, and would be e.g. <0> core <1> java <2> lang
        // <3> Object.class <3> String.class, which gives us two mappings:
        // java/lang/Object.class = core, and java/lang/String.class = core.
        // See the format description in SplitJRE.writeTreeMap for more details.
        Map<String, String> result = new HashMap<String, String>();
        InputStream in = new BufferedInputStream(rawIn);
        // holds the current token sequence,
        // e.g. {"core", "java", "lang", "Object.class"}
        List<String> tokens = new ArrayList<String>();
        StringBuilder currentToken = new StringBuilder();
        for (;;) {
            int c = in.read();
            if (c  == -1) // eof
                break;
            if (c < 32) { // new token level
                if (tokens.size() > 0) {
                    // replace the null at the end of the list with the token
                    // we just finished reading
                    tokens.set(tokens.size() - 1, currentToken.toString());
                }

                currentToken.setLength(0);

                if (c > tokens.size()) {
                    // can't increase by more than one token level at a step
                    throw new InternalError("current token level is " +
                            (tokens.size() - 1) + " but encountered token " +
                            "level " + c);
                }
                else if (c == tokens.size()) {
                    // token level increased by 1; this means we are still
                    // adding tokens for the current mapping -- e.g. we have
                    // read "core", "java", "lang" and are just about to read
                    // "Object.class"
                    // add a placeholder for the new token
                    tokens.add(null);
                }
                else {
                    // we just stayed at the same level or backed up one or more
                    // token levels; this means that the current sequence is
                    // complete and needs to be added to the result map
                    StringBuilder key = new StringBuilder();
                    // combine all tokens except the first into a single string
                    for (int i = 1; i < tokens.size(); i++) {
                        if (i > 1)
                            key.append('/');
                        key.append(tokens.get(i));
                    }
                    // map the combined string to the first token, e.g.
                    // java/lang/Object.class = core
                    result.put(key.toString(), tokens.get(0));
                    // strip off tokens until we get back to the current token
                    // level
                    while (c < tokens.size())
                        tokens.remove(c);
                    // placeholder for upcoming token
                    tokens.add(null);
                }
            }
            else if (c < 254) // character
                currentToken.append((char) c);
            else if (c == 255)
                currentToken.append(".class");
            else { // out-of-band value
                throw new InternalError("internal error processing " +
                        "resource_map (can't-happen error)");
            }
        }
        if (tokens.size() > 0) // add token we just finished reading
            tokens.set(tokens.size() - 1, currentToken.toString());
        StringBuilder key = new StringBuilder();
        // add the last entry to the map
        for (int i = 1; i < tokens.size(); i++) {
            if (i > 1)
                key.append('/');
            key.append(tokens.get(i));
        }
        if (!tokens.isEmpty())
            result.put(key.toString(), tokens.get(0));
        in.close();
        return Collections.unmodifiableMap(result);
    }


    /**
     * Returns the contents of the resource_map file, which maps
     * resources names to their respective bundles.
     */
    public static Map<String, String> getResourceMap() throws IOException {
        if (resourceMap == null) {
            InputStream in = DownloadManager.class.getResourceAsStream("resource_map");
            if (in != null) {
                in = new BufferedInputStream(in);
                try {
                    resourceMap = readTreeMap(in);
                    in.close();
                }
                catch (IOException e) {
                    // turns out we can be returned a broken stream instead of
                    // just null
                    resourceMap = new HashMap<String, String>();
                    complete = true;
                    log("Can't find resource_map, forcing complete to true");
                }
                in.close();
            }
            else {
                resourceMap = new HashMap<String, String>();
                complete = true;
                log("Can't find resource_map, forcing complete to true");
            }

            for (int i = 1; ; i++) { // run through the numbered custom bundles
                String name = CUSTOM_PREFIX + i;
                File customPath = new File(getBundlePath(), name + ".jar");
                if (customPath.exists()) {
                    JarFile custom = new JarFile(customPath);
                    Enumeration entries = custom.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = (JarEntry) entries.nextElement();
                        if (!entry.isDirectory())
                            resourceMap.put(entry.getName(), name);
                    }
                }
                else
                    break;
            }
        }
        return resourceMap;
    }


    /**
     * Returns the contents of the file_map file, which maps
     * file names to their respective bundles.
     */
    public static Map<String, String> getFileMap() throws IOException {
        if (fileMap == null) {
            InputStream in = DownloadManager.class.getResourceAsStream("file_map");
            if (in != null) {
                in = new BufferedInputStream(in);
                try {
                    fileMap = readTreeMap(in);
                    in.close();
                }
                catch (IOException e) {
                    // turns out we can be returned a broken stream instead of
                    // just null
                    fileMap = new HashMap<String, String>();
                    complete = true;
                    log("Can't find file_map, forcing complete to true");
                }
                in.close();
            }
            else {
                fileMap = new HashMap<String, String>();
                complete = true;
                log("Can't find file_map, forcing complete to true");
            }
        }
        return fileMap;
    }


    /**
     * Returns the contents of the bundle.properties file, which maps
     * bundle names to a pipe-separated list of their properties.  Properties
     * include:
     * jarpath - By default, the JAR files (unpacked from classes.pack in the
     *           bundle) are stored under lib/bundles.  The jarpath property
     *           overrides this default setting, causing the JAR to be unpacked
     *           at the specified location.  This is used to preserve the
     *           identity of JRE JAR files such as lib/deploy.jar.
     * size    - The size of the download in bytes.
     */
    private static synchronized Map<String, Map<String, String>> getBundleProperties()
            throws IOException {
        if (bundleProperties == null) {
            InputStream in = DownloadManager.class.getResourceAsStream("bundle.properties");
            if (in == null) {
                complete = true;
                log("Can't find bundle.properties, forcing complete to true");
                return null;
            }
            in = new BufferedInputStream(in);
            Properties tmp = new Properties();
            tmp.load(in);
            bundleProperties = new HashMap<String, Map<String, String>>();
            for (Map.Entry e : tmp.entrySet()) {
                String key = (String) e.getKey();
                String[] properties = ((String) e.getValue()).split("\\|");
                Map<String, String> map = new HashMap<String, String>();
                for (String entry : properties) {
                    int equals = entry.indexOf("=");
                    if (equals == -1)
                        throw new InternalError("error parsing bundle.properties: " +
                            entry);
                    map.put(entry.substring(0, equals).trim(),
                        entry.substring(equals + 1).trim());
                }
                bundleProperties.put(key, map);
            }
            in.close();
        }
        return bundleProperties;
    }


    /**
     * Returns a single bundle property value loaded from the bundle.properties
     * file.
     */
    static String getBundleProperty(String bundleName, String property) {
        try {
            Map<String, Map<String, String>> props = getBundleProperties();
            Map/*<String, String>*/ map = props != null ? props.get(bundleName) : null;
            return map != null ? (String) map.get(property) : null;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /** Returns an array of all supported bundle names. */
    static String[] getBundleNames() throws IOException {
        if (bundleNames == null) {
            Set<String> result = new HashSet<String>();
            Map<String, String> resourceMap = getResourceMap();
            if (resourceMap != null)
                result.addAll(resourceMap.values());
            Map<String, String> fileMap = getFileMap();
            if (fileMap != null)
                result.addAll(fileMap.values());
            bundleNames = result.toArray(new String[result.size()]);
        }
        return bundleNames;
    }


    /**
     * Returns an array of all "critical" (must be downloaded prior to
     * completion) bundle names.
     */
    private static String[] getCriticalBundleNames() throws IOException {
        if (criticalBundleNames == null) {
            Set<String> result = new HashSet<String>();
            Map<String, String> fileMap = getFileMap();
            if (fileMap != null)
                result.addAll(fileMap.values());
            criticalBundleNames = result.toArray(new String[result.size()]);
        }
        return criticalBundleNames;
    }


    public static void send(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int c;
        while ((c = in.read(buffer)) > 0)
            out.write(buffer, 0, c);
    }


    /**
     * Determine whether all bundles have been downloaded, and if so create
     * the merged jars that will eventually replace rt.jar and resoures.jar.
     * IMPORTANT: this method should only be called from the background
     * download process.
     */
    static void performCompletionIfNeeded() {
        if (debug)
            log("DownloadManager.performCompletionIfNeeded: checking (" +
                    complete + ", " + System.getProperty(KERNEL_NOMERGE_PROPERTY)
                    + ")");
        if (complete ||
                "true".equals(System.getProperty(KERNEL_NOMERGE_PROPERTY)))
            return;
        Bundle.loadReceipts();
        try {
            if (debug) {
                List critical = new ArrayList(Arrays.asList(getCriticalBundleNames()));
                critical.removeAll(Bundle.receipts);
                log("DownloadManager.performCompletionIfNeeded: still need " +
                        critical.size() + " bundles (" + critical + ")");
            }
            if (Bundle.receipts.containsAll(Arrays.asList(getCriticalBundleNames()))) {
                log("DownloadManager.performCompletionIfNeeded: running");
                // all done!
                new Thread("JarMerger") {
                    public void run() {
                        createMergedJars();
                    }
                }.start();
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Returns the bundle corresponding to a given resource path (e.g.
     * "java/lang/Object.class").  If the resource does not appear in a bundle,
     * null is returned.
     */
    public static Bundle getBundleForResource(String resource)
            throws IOException {
        String bundleName = getResourceMap().get(resource);
        return bundleName != null ? Bundle.getBundle(bundleName) : null;
    }


    /**
     * Returns the bundle corresponding to a given JRE file path (e.g.
     * "bin/awt.dll").  If the file does not appear in a bundle, null is
     * returned.
     */
    private static Bundle getBundleForFile(String file) throws IOException {
        String bundleName = getFileMap().get(file);
        return bundleName != null ? Bundle.getBundle(bundleName) : null;
    }


    /**
     * Returns the path to the lib/bundles directory.
     */
    static File getBundlePath() {
        return new File(JAVA_HOME, "lib" + File.separatorChar + "bundles");
    }

    private static String getAppDataLocalLow() {
        return USER_HOME + "\\appdata\\locallow\\";
    }

    public static String getKernelJREDir() {
        return "kerneljre" + JAVA_VERSION;
    }

    static File getLocalLowTempBundlePath() {
        return new File(getLocalLowKernelJava() + "-bundles");
    }

    static String getLocalLowKernelJava() {
        return getAppDataLocalLow() + getKernelJREDir();
    }

    // To be revisited:
    // How DownloadManager maintains its bootstrap class path.
    // sun.misc.Launcher.getBootstrapClassPath() returns
    // DownloadManager.getBootstrapClassPath() instead.
    //
    // So should no longer need to lock the Launcher.class.
    // In addition, additionalBootStrapPaths is not really needed
    // if it obtains the initial bootclasspath during DownloadManager's
    // initialization.
    private static void addEntryToBootClassPath(File path) {
        // Must acquire these locks in this order
        synchronized(Launcher.class) {
            synchronized(DownloadManager.class) {
                File[] newBootStrapPaths = new File[
                    additionalBootStrapPaths.length + 1];
                System.arraycopy(additionalBootStrapPaths, 0, newBootStrapPaths,
                        0, additionalBootStrapPaths.length);
                newBootStrapPaths[newBootStrapPaths.length - 1] = path;
                additionalBootStrapPaths = newBootStrapPaths;
                if (bootstrapClassPath != null)
                    bootstrapClassPath.addURL(getFileURL(path));
           }
       }
    }

    /**
     * Returns the kernel's bootstrap class path which includes the additional
     * JARs downloaded
     */
    private static URLClassPath bootstrapClassPath = null;
    private synchronized static
           URLClassPath getBootClassPath(URLClassPath bcp,
                                         URLStreamHandlerFactory factory)
    {
        if (bootstrapClassPath == null) {
            bootstrapClassPath = new URLClassPath(bcp.getURLs(), factory);
            for (File path : additionalBootStrapPaths) {
                bootstrapClassPath.addURL(getFileURL(path));
            }
        }
        return bootstrapClassPath;
    }

    private static URL getFileURL(File file) {
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {}

        try {
            return ParseUtil.fileToEncodedURL(file);
        } catch (MalformedURLException e) {
            // Should never happen since we specify the protocol...
            throw new InternalError();
        }
    }

    /**
     * Scan through java.ext.dirs to see if the lib/ext directory is included.
     * If not, we shouldn't be "finding" lib/ext jars for download.
     */
    private static synchronized boolean extDirIsIncluded() {
        if (!extDirDetermined) {
            extDirDetermined = true;
            String raw = System.getProperty("java.ext.dirs");
            String ext = JAVA_HOME + File.separator + "lib" + File.separator + "ext";
            int index = 0;
            while (index < raw.length()) {
                int newIndex = raw.indexOf(File.pathSeparator, index);
                if (newIndex == -1)
                    newIndex = raw.length();
                String path = raw.substring(index, newIndex);
                if (path.equals(ext)) {
                    extDirIncluded = true;
                    break;
                }
                index = newIndex + 1;
            }
        }
        return extDirIncluded;
    }


    private static String doGetBootClassPathEntryForResource(
            String resourceName) {
        boolean retry = false;
        do {
            Bundle bundle = null;
            try {
                bundle = getBundleForResource(resourceName);
                if (bundle != null) {
                    File path = bundle.getJarPath();
                    boolean isExt = path.getParentFile().getName().equals("ext");
                    if (isExt && !extDirIsIncluded()) // this is a lib/ext jar, but
                        return null;                  // lib/ext isn't in the path
                    if (getBundleProperty(bundle.getName(), JAR_PATH_PROPERTY) == null) {
                        // if the bundle doesn't have its own JAR path, that means it's
                        // going to be merged into rt.jar.  If we already have the
                        // merged rt.jar, we can simply point to that.
                        Bundle merged = Bundle.getBundle("merged");
                        if (merged != null && merged.isInstalled()) {
                            File jar;
                            if (resourceName.endsWith(".class"))
                                jar = merged.getJarPath();
                            else
                                jar = new File(merged.getJarPath().getPath().replaceAll("merged-rt.jar",
                                        "merged-resources.jar"));
                            addEntryToBootClassPath(jar);
                            return jar.getPath();
                        }
                    }
                    if (!bundle.isInstalled()) {
                        bundle.queueDependencies(true);
                        log("On-demand downloading " +
                                bundle.getName() + " for resource " +
                                resourceName + "...");
                        bundle.install();
                        log(bundle + " install finished.");
                    }
                    log("Double-checking " + bundle + " state...");
                    if (!bundle.isInstalled()) {
                        throw new IllegalStateException("Expected state of " +
                                bundle + " to be INSTALLED");
                    }
                    if (isExt) {
                        // don't add lib/ext entries to the boot class path, add
                        // them to the extension classloader instead
                        Launcher.addURLToExtClassLoader(path.toURL());
                        return null;
                    }

                    if ("javaws".equals(bundle.getName())) {
                        Launcher.addURLToAppClassLoader(path.toURL());
                        log("Returning null for javaws");
                        return null;
                    }

                    if ("core".equals(bundle.getName()))
                        return null;

                    // else add to boot class path
                    addEntryToBootClassPath(path);

                    return path.getPath();
                }
                return null; // not one of the JRE's classes
            }
            catch (Throwable e) {
                retry = handleException(e);
                log("Error downloading bundle for " +
                        resourceName + ":");
                log(e);
                if (e instanceof IOException) {
                    // bundle did not get installed correctly, remove incomplete
                    // bundle files
                    if (bundle != null) {
                        if (bundle.getJarPath() != null) {
                            File packTmp = new File(bundle.getJarPath() + ".pack");
                            packTmp.delete();
                            bundle.getJarPath().delete();
                        }
                        if (bundle.getLocalPath() != null) {
                            bundle.getLocalPath().delete();
                        }
                        bundle.setState(Bundle.NOT_DOWNLOADED);
                    }
                }
            }
        } while (retry);
        sendErrorPing(ERROR_RETRY_CANCELLED); // bundle failed to install, user cancelled

        return null; // failed, user chose not to retry
    }

    static synchronized void sendErrorPing(int code) {
        try {
            File bundlePath;
            if (isWindowsVista()) {
                bundlePath = getLocalLowTempBundlePath();
            } else {
                bundlePath = getBundlePath();
            }
            File tmp = new File(bundlePath, "tmp");
            File errors = new File(tmp, "errors");
            String errorString = String.valueOf(code);
            if (errors.exists()) {
                BufferedReader in = new BufferedReader(new FileReader(errors));
                String line = in.readLine();
                while (line != null) {
                    if (line.equals(errorString))
                        return; // we have already pinged this error
                    line = in.readLine();
                }
            }
            tmp.mkdirs();
            Writer out = new FileWriter(errors, true);
            out.write(errorString + System.getProperty("line.separator"));
            out.close();
            postDownloadError(code);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Displays an error dialog and prompts the user to retry or cancel.
     * Returns true if the user chose to retry, false if he chose to cancel.
     */
    static boolean handleException(Throwable e) {
        if (e instanceof IOException) {
            // I don't know of a better method to determine the root cause of
            // the exception, unfortunately...
            int code = ERROR_UNSPECIFIED;
            if (e.getMessage().indexOf("not enough space") != -1)
                code = ERROR_DISK_FULL;
            return askUserToRetryDownloadOrQuit(code);
        }
        else
            return false;
    }


    static synchronized void flushBundleURLs() {
        bundleURLs = null;
    }


    static synchronized Properties getBundleURLs(boolean showUI)
            throws IOException {
        if (bundleURLs == null) {
            log("Entering DownloadManager.getBundleURLs");
            String base = getBaseDownloadURL();
            String url = appendTransactionId(base);
            // use PID instead of createTempFile or other random filename so as
            // to avoid dependencies on the random number generator libraries
            File bundlePath = null;
            // write temp file to locallow directory on vista
            if (isWindowsVista()) {
                bundlePath = getLocalLowTempBundlePath();
            } else {
                bundlePath = getBundlePath();
            }
            File tmp = new File(bundlePath, "urls." + getCurrentProcessId() +
                    ".properties");
            try {
                log("Downloading from " + url + " to " + tmp);
                downloadFromURL(url, tmp, "", showUI);
                bundleURLs = new Properties();
                if (tmp.exists()) {
                    addToTotalDownloadSize((int) tmp.length()); // better late than never
                    InputStream in = new FileInputStream(tmp);
                    in = new BufferedInputStream(in);
                    bundleURLs.load(in);
                    in.close();
                    if (bundleURLs.isEmpty()) {
                        fatalError(ERROR_MALFORMED_BUNDLE_PROPERTIES);
                    }
                } else {
                    fatalError(ERROR_DOWNLOADING_BUNDLE_PROPERTIES);
                }
            } finally {
                // delete the temp file
                if (!debug)
                    tmp.delete();
            }
            log("Leaving DownloadManager.getBundleURLs");
            // else an error occurred and user chose not to retry; leave
            // bundleURLs empty so we don't continually try to re-download it
        }
        return bundleURLs;
    }

    /**
     * Checks to see if the specified resource is part of a bundle, and if so
     * downloads it.  Returns either a string which should be added to the boot
     * class path (the newly-downloaded JAR's location), or null to indicate
     * that it isn't one of the JRE's resources or could not be downloaded.
     */
    public static String getBootClassPathEntryForResource(
            final String resourceName) {
        if (debug)
            log("Entering getBootClassPathEntryForResource(" + resourceName + ")");
        if (isJREComplete() || downloading == null ||
                resourceName.startsWith("sun/jkernel")) {
            if (debug)
                log("Bailing: " + isJREComplete() + ", " + (downloading == null));
            return null;
        }
        incrementDownloadCount();
        try {
            String result = (String) AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        return (String) doGetBootClassPathEntryForResource(
                                resourceName);
                    }
                }
            );
            log("getBootClassPathEntryForResource(" + resourceName + ") == " + result);
            return result;
        }
        finally {
            decrementDownloadCount();
        }
    }


    /**
     * Called by the boot class loader when it encounters a class it can't find.
     * This method will check to see if the class is part of a bundle, and if so
     * download it.  Returns either a string which should be added to the boot
     * class path (the newly-downloaded JAR's location), or null to indicate
     * that it isn't one of the JRE's classes or could not be downloaded.
     */
    public static String getBootClassPathEntryForClass(final String className) {
        return getBootClassPathEntryForResource(className.replace('.', '/') +
                ".class");
    }


    private static boolean doDownloadFile(String relativePath)
            throws IOException {
        Bundle bundle = getBundleForFile(relativePath);
        if (bundle != null) {
            bundle.queueDependencies(true);
            log("On-demand downloading " + bundle.getName() +
                    " for file " + relativePath + "...");
            bundle.install();
            return true;
        }
        return false;
    }


    /**
     * Locates the bundle for the specified JRE file (e.g. "bin/awt.dll") and
     * installs it.  Returns true if the file is indeed part of the JRE and has
     * now been installed, false if the file is not part of the JRE, and throws
     * an IOException if the file is part of the JRE but could not be
     * downloaded.
     */
    public static boolean downloadFile(final String relativePath)
            throws IOException {
        if (isJREComplete() || downloading == null)
            return false;

        incrementDownloadCount();
        try {
            Object result =
                    AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    File path = new File(JAVA_HOME,
                            relativePath.replace('/', File.separatorChar));
                    if (path.exists())
                        return true;
                    try {
                        return new Boolean(doDownloadFile(relativePath));
                    }
                    catch (IOException e) {
                        return e;
                    }
                }
            });
            if (result instanceof Boolean)
                return ((Boolean) result).booleanValue();
            else
                throw (IOException) result;
        }
        finally {
            decrementDownloadCount();
        }
    }


    // increments the counter that tracks whether the current thread is involved
    // in any download-related activities.  A non-zero count indicates that the
    // thread is currently downloading or installing a bundle.
    static void incrementDownloadCount() {
        downloading.set(downloading.get() + 1);
    }


    // increments the counter that tracks whether the current thread is involved
    // in any download-related activities.  A non-zero count indicates that the
    // thread is currently downloading or installing a bundle.
    static void decrementDownloadCount() {
        // will generate an exception if incrementDownloadCount() hasn't been
        // called first, this is intentional
        downloading.set(downloading.get() - 1);
    }


    /**
     * Returns <code>true</code> if the current thread is in the process of
     * downloading a bundle.  This is called by DownloadManager.loadLibrary()
     * that is called by System.loadLibrary(), so
     * that when we run into a library required by the download process itself,
     * we don't call back into DownloadManager in an attempt to download it
     * (which would lead to infinite recursion).
     *
     * All classes and libraries required to download classes must by
     * definition already be present.  So if this method returns true, we are
     * currently in the middle of performing a download, and the class or
     * library load must be happening due to the download itself.  We can
     * immediately abort such requests -- the class or library should already
     * be present.  If it isn't, we're not going to be able to download it,
     * since we have just established that it is required to perform a
     * download, and we might as well just let the NoClassDefFoundError /
     * UnsatisfiedLinkError occur.
     */
    public static boolean isCurrentThreadDownloading() {
        return downloading != null ? downloading.get() > 0 : false;
    }


    /**
     * Returns true if everything is downloaded and the JRE has been
     * reconstructed.  Also returns true if kernel functionality is disabled
     * for any other reason.
     */
    public static boolean isJREComplete() {
        return complete;
    }


    // called by BackgroundDownloader
    static void doBackgroundDownloads(boolean showProgress) {
        if (!complete) {
            if (!showProgress && !debug)
                reportErrors = false;
            try {
                // install swing first for ergonomic reasons
                Bundle swing = Bundle.getBundle("javax_swing_core");
                if (!swing.isInstalled())
                    swing.install(showProgress, false, false);
                // install remaining bundles
                for (String name : getCriticalBundleNames()) {
                    Bundle bundle = Bundle.getBundle(name);
                    if (!bundle.isInstalled()) {
                        bundle.install(showProgress, false, true);
                    }
                }
                shutdown();
            }
            catch (IOException e) {
                log(e);
            }
        }
    }

    // copy receipt file to destination path specified
    static void copyReceiptFile(File from, File to) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(from)));
        OutputStream out = new FileOutputStream(to);
        String line = in.readLine();
        while (line != null) {
            out.write((line + '\n').getBytes("utf-8"));
            line = in.readLine();
        }
        in.close();
        out.close();
    }


    private static void downloadRequestedBundles() {
        log("Checking for requested bundles...");
        try {
            File list = new File(JAVA_HOME, REQUESTED_BUNDLES_PATH);
            if (list.exists()) {
                FileInputStream in = new FileInputStream(list);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                send(in, buffer);
                in.close();

                // split string manually to avoid relying on regexes or
                // StringTokenizer
                String raw = new String(buffer.toByteArray(), "utf-8");
                List/*<String>*/ bundles = new ArrayList/*<String>*/();
                StringBuilder token = new StringBuilder();
                for (int i = 0; i < raw.length(); i++) {
                    char c = raw.charAt(i);
                    if (c == ',' || Character.isWhitespace(c)) {
                        if (token.length() > 0) {
                            bundles.add(token.toString());
                            token.setLength(0);
                        }
                    }
                    else
                        token.append(c);
                }
                if (token.length() > 0)
                    bundles.add(token.toString());
                log("Requested bundles: " + bundles);
                for (int i = 0; i < bundles.size(); i++) {
                    Bundle bundle = Bundle.getBundle((String) bundles.get(i));
                    if (bundle != null && !bundle.isInstalled()) {
                        log("Downloading " + bundle + " due to requested.list");
                        bundle.install(true, false, false);
                    }
                }
            }
        }
        catch (IOException e) {
            log(e);
        }
    }


    static void fatalError(int code) {
        fatalError(code, null);
    }


    /**
     * Called to cleanly shut down the VM when a fatal download error has
     * occurred.  Calls System.exit() if outside of the Java Plug-In, otherwise
     * throws an error.
     */
    static void fatalError(int code, String arg) {
        sendErrorPing(code);

        for (int i = 0; i < Bundle.THREADS; i++)
            bundleInstallComplete();
        if (reportErrors)
            displayError(code, arg);
        // inPlugIn check isn't 100% reliable but should be close enough.
        // headless is for the browser side of things in the out-of-process
        // plug-in
        boolean inPlugIn = (Boolean.getBoolean("java.awt.headless") ||
           System.getProperty("javaplugin.version") != null);
        KernelError error = new KernelError("Java Kernel bundle download failed");
        if (inPlugIn)
            throw error;
        else {
            log(error);
            System.exit(1);
        }
    }


    // start the background download process using the jbroker broker process
    // the method will first launch the broker process, if it is not already
    // running
    // it will then send the command necessary to start the background download
    // process to the broker process
    private static void startBackgroundDownloadWithBroker() {

        if (!BackgroundDownloader.getBackgroundDownloadProperty()) {
            // If getBackgroundDownloadProperty() returns false
            // we're doing the downloads from this VM; we don't want to
            // spawn another one
            return;
        }

        // launch broker process if necessary
        if (!launchBrokerProcess()) {
            return;
        }


        String kernelDownloadURLProperty = getBaseDownloadURL();

        String kernelDownloadURL;

        // only set KERNEL_DOWNLOAD_URL_PROPERTY if we override
        // the default download url
        if (kernelDownloadURLProperty == null ||
                kernelDownloadURLProperty.equals(DEFAULT_DOWNLOAD_URL)) {
            kernelDownloadURL = " ";
        } else {
            kernelDownloadURL = kernelDownloadURLProperty;
        }

        startBackgroundDownloadWithBrokerImpl(kernelDownloadURLProperty);
    }

    private static void startBackgroundDownloads() {
        if (!complete) {
            if (BackgroundDownloader.getBackgroundMutex().acquire(0)) {
                // we don't actually need to hold the mutex -- it was just a
                // quick check to see if there is any point in even attempting
                // to start the background downloader
                BackgroundDownloader.getBackgroundMutex().release();
                if (isWindowsVista()) {
                    // use broker process to start background download
                    // at high integrity
                    startBackgroundDownloadWithBroker();
                } else {
                    BackgroundDownloader.startBackgroundDownloads();
                }
            }
        }
    }


    /**
     * Increases the total download size displayed in the download progress
     * dialog.
     */
    static native void addToTotalDownloadSize(int size);


    /**
     * Displays a progress dialog while downloading from the specified URL.
     *
     *@param url the URL string from which to download
     *@param file the destination path
     *@param name the user-visible name of the component we are downloading
     */
    static void downloadFromURL(String url, File file, String name,
            boolean showProgress) {
        // do not show download dialog if kernel.download.dialog is false
        downloadFromURLImpl(url, file, name,
                disableDownloadDialog ? false : showProgress);
    }

    private static native void downloadFromURLImpl(String url, File file,
            String name, boolean showProgress);

    // This is for testing purposes only - allows to specify URL
    // to download kernel bundles from through the registry key.
    static native String getUrlFromRegistry();

    static native String getVisitorId0();

    static native void postDownloadComplete();

    static native void postDownloadError(int code);

    // Returns the visitor ID set by the installer, will be sent to the server
    // during bundle downloads for logging purposes.
    static synchronized String getVisitorId() {
        if (!visitorIdDetermined) {
            visitorIdDetermined = true;
            visitorId = getVisitorId0();
        }
        return visitorId;
    }

    // display an error message using a native dialog
    public static native void displayError(int code, String arg);

    // prompt user whether to retry download, or quit
    // returns true if the user chose to retry
    public static native boolean askUserToRetryDownloadOrQuit(int code);

    // returns true if we are running Windows Vista; false otherwise
    static native boolean isWindowsVista();

    private static native void startBackgroundDownloadWithBrokerImpl(
            String command);

    private static int isJBrokerStarted() {
        if (_isJBrokerStarted == -1) {
            // initialize state of jbroker
            _isJBrokerStarted = isJBrokerRunning() ? 1 : 0;
        }
        return _isJBrokerStarted;
    }

    // returns true if broker process (jbroker) is running; false otherwise
    private static native boolean isJBrokerRunning();

    // returns true if we are running in IE protected mode; false otherwise
    private static native boolean isIEProtectedMode();

    private static native boolean launchJBroker(String jbrokerPath);

    static native void bundleInstallStart();

    static native void bundleInstallComplete();

    private static native boolean moveFileWithBrokerImpl(String fromPath,
            String userHome);

    private static native boolean moveDirWithBrokerImpl(String fromPath,
            String userHome);

    static boolean moveFileWithBroker(String fromPath) {
        // launch jbroker if necessary
        if (!launchBrokerProcess()) {
            return false;
        }

        return moveFileWithBrokerImpl(fromPath, USER_HOME);
    }

    static boolean moveDirWithBroker(String fromPath) {
        // launch jbroker if necessary
        if (!launchBrokerProcess()) {
            return false;
        }

        return moveDirWithBrokerImpl(fromPath, USER_HOME);
    }

    private static synchronized boolean launchBrokerProcess() {
        // launch jbroker if necessary
        if (isJBrokerStarted() == 0) {
            // launch jbroker if needed
            boolean ret = launchJBroker(JAVA_HOME);
            // set state of jbroker
            _isJBrokerStarted = ret ? 1 : 0;
            return ret;
        }
        return true;
    }

    private static class StreamMonitor implements Runnable {
        private InputStream istream;
        public StreamMonitor(InputStream stream) {
            istream = new BufferedInputStream(stream);
            new Thread(this).start();
        }
        public void run() {
            byte[] buffer = new byte[4096];
            try {
                int ret = istream.read(buffer);
                while (ret != -1) {
                    ret = istream.read(buffer);
                }
            } catch (IOException e) {
                try {
                    istream.close();
                } catch (IOException e2) {
                } // Should allow clean exit when process shuts down
            }
        }
    }


    /** Copy a file tree, excluding certain named files. */
    private static void copyAll(File src, File dest, Set/*<String>*/ excludes)
                            throws IOException {
        if (!excludes.contains(src.getName())) {
            if (src.isDirectory()) {
                File[] children = src.listFiles();
                if (children != null) {
                    for (int i = 0; i < children.length; i++)
                        copyAll(children[i],
                                new File(dest, children[i].getName()),
                                excludes);
                }
            }
            else {
                dest.getParentFile().mkdirs();
                FileInputStream in = new FileInputStream(src);
                FileOutputStream out = new FileOutputStream(dest);
                send(in, out);
                in.close();
                out.close();
            }
        }
    }


    public static void dumpOutput(final Process p) {
        Thread outputReader = new Thread("outputReader") {
            public void run() {
                try {
                    InputStream in = p.getInputStream();
                    DownloadManager.send(in, System.out);
                } catch (IOException e) {
                    log(e);
                }
            }
        };
        outputReader.start();
        Thread errorReader = new Thread("errorReader") {
            public void run() {
                try {
                    InputStream in = p.getErrorStream();
                    DownloadManager.send(in, System.err);
                } catch (IOException e) {
                    log(e);
                }
            }
        };
        errorReader.start();
    }


    /**
     * Creates the merged rt.jar and resources.jar files.
     */
    private static void createMergedJars() {
        log("DownloadManager.createMergedJars");
        File bundlePath;
        if (isWindowsVista()) {
            bundlePath = getLocalLowTempBundlePath();
        } else {
            bundlePath = getBundlePath();
        }
        File tmp = new File(bundlePath, "tmp");
        // explicitly check the final location, not the (potentially) local-low
        // location -- a local-low finished isn't good enough to call it done
        if (new File(getBundlePath(), "tmp" + File.separator + "finished").exists())
            return; // already done
        log("DownloadManager.createMergedJars: running");
        tmp.mkdirs();
        boolean retry = false;
        do {
            try {
                Bundle.getBundle("merged").install(false, false, true);
                postDownloadComplete();
                // done, write an empty "finished" file to flag completion
                File finished = new File(tmp, "finished");
                new FileOutputStream(finished).close();
                if (isWindowsVista()) {
                    if (!moveFileWithBroker(getKernelJREDir() +
                            "-bundles\\tmp\\finished")) {
                        throw new IOException("unable to create 'finished' file");
                    }
                }
                log("DownloadManager.createMergedJars: created " + finished);
                // next JRE startup will move these files into their final
                // locations, as long as no other JREs are running

                // clean up the local low bundle directory on vista
                if (isWindowsVista()) {
                    File tmpDir = getLocalLowTempBundlePath();
                    File[] list = tmpDir.listFiles();
                    if (list != null) {
                        for (int i = 0; i < list.length; i++) {
                            list[i].delete();
                        }
                    }
                    tmpDir.delete();
                    log("Finished cleanup, " + tmpDir + ".exists(): " + tmpDir.exists());
                }
            }
            catch (IOException e) {
                log(e);
            }
        }
        while (retry);
        log("DownloadManager.createMergedJars: finished");
    }


    private static void shutdown() {
        try {
            ExecutorService e = Bundle.getThreadPool();
            e.shutdown();
            e.awaitTermination(60 * 60 * 24, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
        }
    }


    // returns the registry key for kernel.debug
    static native boolean getDebugKey();


    // returns the final value for the kernel debug property
    public static boolean getDebugProperty(){
         /*
          * Check registry key value
          */
         boolean debugEnabled = getDebugKey();

         /*
          * Check system property - it should override the registry
          * key value.
          */
         if (System.getProperty(KERNEL_DEBUG_PROPERTY) != null) {
             debugEnabled = Boolean.valueOf(
                      System.getProperty(KERNEL_DEBUG_PROPERTY));
         }
         return debugEnabled;

    }


    /**
     * Outputs to the error stream even when System.err has not yet been
     * initialized.
     */
    static void println(String msg) {
        if (System.err != null)
            System.err.println(msg);
        else {
            try {
                if (errorStream == null)
                    errorStream = new FileOutputStream(FileDescriptor.err);
                errorStream.write((msg +
                        System.getProperty("line.separator")).getBytes("utf-8"));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    static void log(String msg) {
        if (debug) {
            println(msg);
            try {
                if (logStream == null) {
                    loadJKernelLibrary();
                    File path = isWindowsVista() ? getLocalLowTempBundlePath() :
                            getBundlePath();
                    path = new File(path, "kernel." + getCurrentProcessId() + ".log");
                    logStream = new FileOutputStream(path);
                }
                logStream.write((msg +
                        System.getProperty("line.separator")).getBytes("utf-8"));
                logStream.flush();
            }
            catch (IOException e) {
                // ignore
            }
        }
    }


    static void log(Throwable e) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream p = new PrintStream(buffer);
        e.printStackTrace(p);
        p.close();
        log(buffer.toString(0));
    }


    /** Dump the contents of a map to System.out. */
    private static void printMap(Map/*<String, String>*/ map) {
        int size = 0;
        Set<Integer> identityHashes = new HashSet<Integer>();
        Iterator/*<Map.Entry<String, String>>*/ i = map.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry/*<String, String>*/ e = (Map.Entry) i.next();
            String key = (String) e.getKey();
            String value = (String) e.getValue();
            System.out.println(key + ": " + value);
            Integer keyHash = Integer.valueOf(System.identityHashCode(key));
            if (!identityHashes.contains(keyHash)) {
                identityHashes.add(keyHash);
                size += key.length();
            }
            Integer valueHash = Integer.valueOf(System.identityHashCode(value));
            if (!identityHashes.contains(valueHash)) {
                identityHashes.add(valueHash);
                size += value.length();
            }
        }
        System.out.println(size + " bytes");
    }


    /** Process the "-dumpmaps" command-line argument. */
    private static void dumpMaps() throws IOException {
        System.out.println("Resources:");
        System.out.println("----------");
        printMap(getResourceMap());
        System.out.println();
        System.out.println("Files:");
        System.out.println("----------");
        printMap(getFileMap());
    }


    /** Process the "-download" command-line argument. */
    private static void processDownload(String bundleName) throws IOException {
        if (bundleName.equals("all")) {
            debug = true;
            doBackgroundDownloads(true);
            performCompletionIfNeeded();
        }
        else {
            Bundle bundle = Bundle.getBundle(bundleName);
            if (bundle == null) {
                println("Unknown bundle: " + bundleName);
                System.exit(1);
            }
            else
                bundle.install();
        }
    }


    static native int getCurrentProcessId();

    private DownloadManager() {
    }

    // Invoked by jkernel VM after the VM is initialized
    static void setBootClassLoaderHook() {
        if (!isJREComplete()) {
            sun.misc.BootClassLoaderHook.setHook(new DownloadManager());
        }
    }

    // Implementation of the BootClassLoaderHook interface
    public String loadBootstrapClass(String name) {
        // Check for download before we look for it.  If
        // DownloadManager ends up downloading it, it will add it to
        // our search path before we proceed to the findClass().
        return DownloadManager.getBootClassPathEntryForClass(name);
    }

    public boolean loadLibrary(String name) {
       try {
            if (!DownloadManager.isJREComplete() &&
                    !DownloadManager.isCurrentThreadDownloading()) {
                return DownloadManager.downloadFile("bin/" +
                    System.mapLibraryName(name));
                // it doesn't matter if the downloadFile call returns false --
                // it probably just means that this is a user library, as
                // opposed to a JRE library
            }
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Error downloading library " +
                                                name + ": " + e);
        } catch (NoClassDefFoundError e) {
            // This happens while Java itself is being compiled; DownloadManager
            // isn't accessible when this code is first invoked.  It isn't an
            // issue, as if we can't find DownloadManager, we can safely assume
            // that additional code is not available for download.
        }
        return false;
    }

    public boolean prefetchFile(String name) {
        try {
            return sun.jkernel.DownloadManager.downloadFile(name);
        } catch (IOException ioe) {
            return false;
        }
    }

    public String getBootstrapResource(String name) {
        try {
            // If this is a known JRE resource, ensure that its bundle is
            // downloaded.  If it isn't known, we just ignore the download
            // failure and check to see if we can find the resource anyway
            // (which is possible if the boot class path has been modified).
            return DownloadManager.getBootClassPathEntryForResource(name);
        } catch (NoClassDefFoundError e) {
            // This happens while Java itself is being compiled; DownloadManager
            // isn't accessible when this code is first invoked.  It isn't an
            // issue, as if we can't find DownloadManager, we can safely assume
            // that additional code is not available for download.
            return null;
        }
    }

    public URLClassPath getBootstrapClassPath(URLClassPath bcp,
                                              URLStreamHandlerFactory factory)
    {
        return DownloadManager.getBootClassPath(bcp, factory);
    }

    public boolean isCurrentThreadPrefetching() {
        return DownloadManager.isCurrentThreadDownloading();
    }

    public static void main(String[] arg) throws Exception {
        AccessController.checkPermission(new AllPermission());

        boolean valid = false;
        if (arg.length == 2 && arg[0].equals("-install")) {
            valid = true;
            Bundle bundle = new Bundle() {
                protected void updateState() {
                    // the bundle path was provided on the command line, so we
                    // just claim it has already been "downloaded" to the local
                    // filesystem
                    state = DOWNLOADED;
                }
            };

            File jarPath;
            int index = 0;
            do {
                index++;
                jarPath = new File(getBundlePath(),
                        CUSTOM_PREFIX + index + ".jar");
            }
            while (jarPath.exists());
            bundle.setName(CUSTOM_PREFIX + index);
            bundle.setLocalPath(new File(arg[1]));
            bundle.setJarPath(jarPath);
            bundle.setDeleteOnInstall(false);
            bundle.install();
        }
        else if (arg.length == 2 && arg[0].equals("-download")) {
            valid = true;
            processDownload(arg[1]);
        }
        else if (arg.length == 1 && arg[0].equals("-dumpmaps")) {
            valid = true;
            dumpMaps();
        }
        else if (arg.length == 2 && arg[0].equals("-sha1")) {
            valid = true;
            System.out.println(BundleCheck.getInstance(new File(arg[1])));
        }
        else if (arg.length == 1 && arg[0].equals("-downloadtest")) {
            valid = true;
            File file = File.createTempFile("download", ".test");
            for (;;) {
                file.delete();
                downloadFromURL(getBaseDownloadURL(), file, "URLS", true);
                System.out.println("Downloaded " + file.length() + " bytes");
            }
        }
        if (!valid) {
            System.out.println("usage: DownloadManager -install <path>.zip |");
            System.out.println("       DownloadManager -download " +
                    "<bundle_name> |");
            System.out.println("       DownloadManager -dumpmaps");
            System.exit(1);
        }
    }
}
