/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.codegen;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * <p>Static utility that encapsulates persistence of type information for functions compiled with optimistic
 * typing. With this feature enabled, when a JavaScript function is recompiled because it gets deoptimized,
 * the type information for deoptimization is stored in a cache file. If the same function is compiled in a
 * subsequent JVM invocation, the type information is used for initial compilation, thus allowing the system
 * to skip a lot of intermediate recompilations and immediately emit a version of the code that has its
 * optimistic types at (or near) the steady state.
 * </p><p>
 * Normally, the type info persistence feature is disabled. When the {@code nashorn.typeInfo.maxFiles} system
 * property is specified with a value greater than 0, it is enabled and operates in an operating-system
 * specific per-user cache directory. You can override the directory by specifying it in the
 * {@code nashorn.typeInfo.cacheDir} directory. The maximum number of files is softly enforced by a task that
 * cleans up the directory periodically on a separate thread. It is run after some delay after a new file is
 * added to the cache. The default delay is 20 seconds, and can be set using the
 * {@code nashorn.typeInfo.cleanupDelaySeconds} system property. You can also specify the word
 * {@code unlimited} as the value for {@code nashorn.typeInfo.maxFiles} in which case the type info cache is
 * allowed to grow without limits.
 * </p>
 */
public final class OptimisticTypesPersistence {
    // Default is 0, for disabling the feature when not specified. A reasonable default when enabled is
    // dependent on the application; setting it to e.g. 20000 is probably good enough for most uses and will
    // usually cap the cache directory to about 80MB presuming a 4kB filesystem allocation unit. There is one
    // file per JavaScript function.
    private static final int DEFAULT_MAX_FILES = 0;
    // Constants for signifying that the cache should not be limited
    private static final int UNLIMITED_FILES = -1;
    // Maximum number of files that should be cached on disk. The maximum will be softly enforced.
    private static final int MAX_FILES = getMaxFiles();
    // Number of seconds to wait between adding a new file to the cache and running a cleanup process
    private static final int DEFAULT_CLEANUP_DELAY = 20;
    private static final int CLEANUP_DELAY = Math.max(0, Options.getIntProperty(
            "nashorn.typeInfo.cleanupDelaySeconds", DEFAULT_CLEANUP_DELAY));
    // The name of the default subdirectory within the system cache directory where we store type info.
    private static final String DEFAULT_CACHE_SUBDIR_NAME = "com.oracle.java.NashornTypeInfo";
    // The directory where we cache type info
    private static final File baseCacheDir = createBaseCacheDir();
    private static final File cacheDir = createCacheDir(baseCacheDir);
    // In-process locks to make sure we don't have a cross-thread race condition manipulating any file.
    private static final Object[] locks = cacheDir == null ? null : createLockArray();
    // Only report one read/write error every minute
    private static final long ERROR_REPORT_THRESHOLD = 60000L;

    private static volatile long lastReportedError;
    private static final AtomicBoolean scheduledCleanup;
    private static final Timer cleanupTimer;
    static {
        if (baseCacheDir == null || MAX_FILES == UNLIMITED_FILES) {
            scheduledCleanup = null;
            cleanupTimer = null;
        } else {
            scheduledCleanup = new AtomicBoolean();
            cleanupTimer = new Timer(true);
        }
    }
    /**
     * Retrieves an opaque descriptor for the persistence location for a given function. It should be passed
     * to {@link #load(Object)} and {@link #store(Object, Map)} methods.
     * @param source the source where the function comes from
     * @param functionId the unique ID number of the function within the source
     * @param paramTypes the types of the function parameters (as persistence is per parameter type
     * specialization).
     * @return an opaque descriptor for the persistence location. Can be null if persistence is disabled.
     */
    public static Object getLocationDescriptor(final Source source, final int functionId, final Type[] paramTypes) {
        if(cacheDir == null) {
            return null;
        }
        final StringBuilder b = new StringBuilder(48);
        // Base64-encode the digest of the source, and append the function id.
        b.append(source.getDigest()).append('-').append(functionId);
        // Finally, if this is a parameter-type specialized version of the function, add the parameter types
        // to the file name.
        if(paramTypes != null && paramTypes.length > 0) {
            b.append('-');
            for(final Type t: paramTypes) {
                b.append(Type.getShortSignatureDescriptor(t));
            }
        }
        return new LocationDescriptor(new File(cacheDir, b.toString()));
    }

    private static final class LocationDescriptor {
        private final File file;

        LocationDescriptor(final File file) {
            this.file = file;
        }
    }


    /**
     * Stores the map of optimistic types for a given function.
     * @param locationDescriptor the opaque persistence location descriptor, retrieved by calling
     * {@link #getLocationDescriptor(Source, int, Type[])}.
     * @param optimisticTypes the map of optimistic types.
     */
    @SuppressWarnings("resource")
    public static void store(final Object locationDescriptor, final Map<Integer, Type> optimisticTypes) {
        if(locationDescriptor == null || optimisticTypes.isEmpty()) {
            return;
        }
        final File file = ((LocationDescriptor)locationDescriptor).file;

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                synchronized(getFileLock(file)) {
                    if (!file.exists()) {
                        // If the file already exists, we aren't increasing the number of cached files, so
                        // don't schedule cleanup.
                        scheduleCleanup();
                    }
                    try (final FileOutputStream out = new FileOutputStream(file)) {
                        out.getChannel().lock(); // lock exclusive
                        final DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(out));
                        Type.writeTypeMap(optimisticTypes, dout);
                        dout.flush();
                    } catch(final Exception e) {
                        reportError("write", file, e);
                    }
                }
                return null;
            }
        });
    }

    /**
     * Loads the map of optimistic types for a given function.
     * @param locationDescriptor the opaque persistence location descriptor, retrieved by calling
     * {@link #getLocationDescriptor(Source, int, Type[])}.
     * @return the map of optimistic types, or null if persisted type information could not be retrieved.
     */
    @SuppressWarnings("resource")
    public static Map<Integer, Type> load(final Object locationDescriptor) {
        if (locationDescriptor == null) {
            return null;
        }
        final File file = ((LocationDescriptor)locationDescriptor).file;
        return AccessController.doPrivileged(new PrivilegedAction<Map<Integer, Type>>() {
            @Override
            public Map<Integer, Type> run() {
                try {
                    if(!file.isFile()) {
                        return null;
                    }
                    synchronized(getFileLock(file)) {
                        try (final FileInputStream in = new FileInputStream(file)) {
                            in.getChannel().lock(0, Long.MAX_VALUE, true); // lock shared
                            final DataInputStream din = new DataInputStream(new BufferedInputStream(in));
                            return Type.readTypeMap(din);
                        }
                    }
                } catch (final Exception e) {
                    reportError("read", file, e);
                    return null;
                }
            }
        });
    }

    private static void reportError(final String msg, final File file, final Exception e) {
        final long now = System.currentTimeMillis();
        if(now - lastReportedError > ERROR_REPORT_THRESHOLD) {
            reportError(String.format("Failed to %s %s", msg, file), e);
            lastReportedError = now;
        }
    }

    /**
     * Logs an error message with warning severity (reasoning being that we're reporting an error that'll disable the
     * type info cache, but it's only logged as a warning because that doesn't prevent Nashorn from running, it just
     * disables a performance-enhancing cache).
     * @param msg the message to log
     * @param e the exception that represents the error.
     */
    private static void reportError(final String msg, final Exception e) {
        getLogger().warning(msg, "\n", exceptionToString(e));
    }

    /**
     * A helper that prints an exception stack trace into a string. We have to do this as if we just pass the exception
     * to {@link DebugLogger#warning(Object...)}, it will only log the exception message and not the stack, making
     * problems harder to diagnose.
     * @param e the exception
     * @return the string representation of {@link Exception#printStackTrace()} output.
     */
    private static String exceptionToString(final Exception e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, false);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static File createBaseCacheDir() {
        if(MAX_FILES == 0 || Options.getBooleanProperty("nashorn.typeInfo.disabled")) {
            return null;
        }
        try {
            return createBaseCacheDirPrivileged();
        } catch(final Exception e) {
            reportError("Failed to create cache dir", e);
            return null;
        }
    }

    private static File createBaseCacheDirPrivileged() {
        return AccessController.doPrivileged(new PrivilegedAction<File>() {
            @Override
            public File run() {
                final String explicitDir = System.getProperty("nashorn.typeInfo.cacheDir");
                final File dir;
                if(explicitDir != null) {
                    dir = new File(explicitDir);
                } else {
                    // When no directory is explicitly specified, get an operating system specific cache
                    // directory, and create "com.oracle.java.NashornTypeInfo" in it.
                    final File systemCacheDir = getSystemCacheDir();
                    dir = new File(systemCacheDir, DEFAULT_CACHE_SUBDIR_NAME);
                    if (isSymbolicLink(dir)) {
                        return null;
                    }
                }
                return dir;
            }
        });
    }

    private static File createCacheDir(final File baseDir) {
        if (baseDir == null) {
            return null;
        }
        try {
            return createCacheDirPrivileged(baseDir);
        } catch(final Exception e) {
            reportError("Failed to create cache dir", e);
            return null;
        }
    }

    private static File createCacheDirPrivileged(final File baseDir) {
        return AccessController.doPrivileged(new PrivilegedAction<File>() {
            @Override
            public File run() {
                final String versionDirName;
                try {
                    versionDirName = getVersionDirName();
                } catch(final Exception e) {
                    reportError("Failed to calculate version dir name", e);
                    return null;
                }
                final File versionDir = new File(baseDir, versionDirName);
                if (isSymbolicLink(versionDir)) {
                    return null;
                }
                versionDir.mkdirs();
                if (versionDir.isDirectory()) {
                    getLogger().info("Optimistic type persistence directory is " + versionDir);
                    return versionDir;
                }
                getLogger().warning("Could not create optimistic type persistence directory " + versionDir);
                return null;
            }
        });
    }

    /**
     * Returns an operating system specific root directory for cache files.
     * @return an operating system specific root directory for cache files.
     */
    private static File getSystemCacheDir() {
        final String os = System.getProperty("os.name", "generic");
        if("Mac OS X".equals(os)) {
            // Mac OS X stores caches in ~/Library/Caches
            return new File(new File(System.getProperty("user.home"), "Library"), "Caches");
        } else if(os.startsWith("Windows")) {
            // On Windows, temp directory is the best approximation of a cache directory, as its contents
            // persist across reboots and various cleanup utilities know about it. java.io.tmpdir normally
            // points to a user-specific temp directory, %HOME%\LocalSettings\Temp.
            return new File(System.getProperty("java.io.tmpdir"));
        } else {
            // In other cases we're presumably dealing with a UNIX flavor (Linux, Solaris, etc.); "~/.cache"
            return new File(System.getProperty("user.home"), ".cache");
        }
    }

    private static final String ANCHOR_PROPS = "anchor.properties";

    /**
     * In order to ensure that changes in Nashorn code don't cause corruption in the data, we'll create a
     * per-code-version directory. Normally, this will create the SHA-1 digest of the nashorn.jar. In case the classpath
     * for nashorn is local directory (e.g. during development), this will create the string "dev-" followed by the
     * timestamp of the most recent .class file.
     *
     * @return digest of currently running nashorn
     * @throws Exception if digest could not be created
     */
    public static String getVersionDirName() throws Exception {
        // NOTE: getResource("") won't work if the JAR file doesn't have directory entries (and JAR files in JDK distro
        // don't, or at least it's a bad idea counting on it). Alternatively, we could've tried
        // getResource("OptimisticTypesPersistence.class") but behavior of getResource with regard to its willingness
        // to hand out URLs to .class files is also unspecified. Therefore, the most robust way to obtain an URL to our
        // package is to have a small non-class anchor file and start out from its URL.
        final URL url = OptimisticTypesPersistence.class.getResource(ANCHOR_PROPS);
        final String protocol = url.getProtocol();
        if (protocol.equals("jar")) {
            // Normal deployment: nashorn.jar
            final String jarUrlFile = url.getFile();
            final String filePath = jarUrlFile.substring(0, jarUrlFile.indexOf('!'));
            final URL file = new URL(filePath);
            try (final InputStream in = file.openStream()) {
                final byte[] buf = new byte[128*1024];
                final MessageDigest digest = MessageDigest.getInstance("SHA-1");
                for(;;) {
                    final int l = in.read(buf);
                    if(l == -1) {
                        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
                    }
                    digest.update(buf, 0, l);
                }
            }
        } else if(protocol.equals("file")) {
            // Development
            final String fileStr = url.getFile();
            final String className = OptimisticTypesPersistence.class.getName();
            final int packageNameLen = className.lastIndexOf('.');
            final String dirStr = fileStr.substring(0, fileStr.length() - packageNameLen - 1 - ANCHOR_PROPS.length());
            final File dir = new File(dirStr);
            return "dev-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(getLastModifiedClassFile(
                    dir, 0L)));
        } else if(protocol.equals("jrt")) {
            return getJrtVersionDirName();
        } else {
            throw new AssertionError("unknown protocol");
        }
    }

    private static long getLastModifiedClassFile(final File dir, final long max) {
        long currentMax = max;
        for(final File f: dir.listFiles()) {
            if(f.getName().endsWith(".class")) {
                final long lastModified = f.lastModified();
                if (lastModified > currentMax) {
                    currentMax = lastModified;
                }
            } else if (f.isDirectory()) {
                final long lastModified = getLastModifiedClassFile(f, currentMax);
                if (lastModified > currentMax) {
                    currentMax = lastModified;
                }
            }
        }
        return currentMax;
    }

    /**
     * Returns true if the specified file is a symbolic link, and also logs a warning if it is.
     * @param file the file
     * @return true if file is a symbolic link, false otherwise.
     */
    private static boolean isSymbolicLink(final File file) {
        if (Files.isSymbolicLink(file.toPath())) {
            getLogger().warning("Directory " + file + " is a symlink");
            return true;
        }
        return false;
    }

    private static Object[] createLockArray() {
        final Object[] lockArray = new Object[Runtime.getRuntime().availableProcessors() * 2];
        for (int i = 0; i < lockArray.length; ++i) {
            lockArray[i] = new Object();
        }
        return lockArray;
    }

    private static Object getFileLock(final File file) {
        return locks[(file.hashCode() & Integer.MAX_VALUE) % locks.length];
    }

    private static DebugLogger getLogger() {
        try {
            return Context.getContext().getLogger(RecompilableScriptFunctionData.class);
        } catch (final Exception e) {
            e.printStackTrace();
            return DebugLogger.DISABLED_LOGGER;
        }
    }

    private static void scheduleCleanup() {
        if (MAX_FILES != UNLIMITED_FILES && scheduledCleanup.compareAndSet(false, true)) {
            cleanupTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    scheduledCleanup.set(false);
                    try {
                        doCleanup();
                    } catch (final IOException e) {
                        // Ignore it. While this is unfortunate, we don't have good facility for reporting
                        // this, as we're running in a thread that has no access to Context, so we can't grab
                        // a DebugLogger.
                    }
                }
            }, TimeUnit.SECONDS.toMillis(CLEANUP_DELAY));
        }
    }

    private static void doCleanup() throws IOException {
        final Path[] files = getAllRegularFilesInLastModifiedOrder();
        final int nFiles = files.length;
        final int filesToDelete = Math.max(0, nFiles - MAX_FILES);
        int filesDeleted = 0;
        for (int i = 0; i < nFiles && filesDeleted < filesToDelete; ++i) {
            try {
                Files.deleteIfExists(files[i]);
                // Even if it didn't exist, we increment filesDeleted; it existed a moment earlier; something
                // else deleted it for us; that's okay with us.
                filesDeleted++;
            } catch (final Exception e) {
                // does not increase filesDeleted
            }
            files[i] = null; // gc eligible
        }
    }

    private static Path[] getAllRegularFilesInLastModifiedOrder() throws IOException {
        try (final Stream<Path> filesStream = Files.walk(baseCacheDir.toPath())) {
            // TODO: rewrite below once we can use JDK8 syntactic constructs
            return filesStream
            .filter(new Predicate<Path>() {
                @Override
                public boolean test(final Path path) {
                    return !Files.isDirectory(path);
                }
            })
            .map(new Function<Path, PathAndTime>() {
                @Override
                public PathAndTime apply(final Path path) {
                    return new PathAndTime(path);
                }
            })
            .sorted()
            .map(new Function<PathAndTime, Path>() {
                @Override
                public Path apply(final PathAndTime pathAndTime) {
                    return pathAndTime.path;
                }
            })
            .toArray(new IntFunction<Path[]>() { // Replace with Path::new
                @Override
                public Path[] apply(final int length) {
                    return new Path[length];
                }
            });
        }
    }

    private static class PathAndTime implements Comparable<PathAndTime> {
        private final Path path;
        private final long time;

        PathAndTime(final Path path) {
            this.path = path;
            this.time = getTime(path);
        }

        @Override
        public int compareTo(final PathAndTime other) {
            return Long.compare(time, other.time);
        }

        private static long getTime(final Path path) {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (final IOException e) {
                // All files for which we can't retrieve the last modified date will be considered oldest.
                return -1L;
            }
        }
    }

    private static int getMaxFiles() {
        final String str = Options.getStringProperty("nashorn.typeInfo.maxFiles", null);
        if (str == null) {
            return DEFAULT_MAX_FILES;
        } else if ("unlimited".equals(str)) {
            return UNLIMITED_FILES;
        }
        return Math.max(0, Integer.parseInt(str));
    }

    private static final String JRT_NASHORN_DIR = "/modules/jdk.scripting.nashorn";

    // version directory name if nashorn is loaded from jrt:/ URL
    private static String getJrtVersionDirName() throws Exception {
        final FileSystem fs = getJrtFileSystem();
        // consider all .class resources under nashorn module to compute checksum
        final Path nashorn = fs.getPath(JRT_NASHORN_DIR);
        if (! Files.isDirectory(nashorn)) {
            throw new FileNotFoundException("missing " + JRT_NASHORN_DIR + " dir in jrt fs");
        }
        final MessageDigest digest = MessageDigest.getInstance("SHA-1");
        Files.walk(nashorn).forEach(new Consumer<Path>() {
            @Override
            public void accept(final Path p) {
                // take only the .class resources.
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    try {
                        digest.update(Files.readAllBytes(p));
                    } catch (final IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    }
                }
            }
        });
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
    }

    // get the default jrt FileSystem instance
    private static FileSystem getJrtFileSystem() {
        return AccessController.doPrivileged(
            new PrivilegedAction<FileSystem>() {
                @Override
                public FileSystem run() {
                    return FileSystems.getFileSystem(URI.create("jrt:/"));
                }
            });
    }
}
