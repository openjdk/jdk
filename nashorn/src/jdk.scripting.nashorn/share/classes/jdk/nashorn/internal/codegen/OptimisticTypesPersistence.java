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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Static utility that encapsulates persistence of decompilation information for functions. Normally, the type info
 * persistence feature is enabled and operates in an operating-system specific per-user cache directory. You can
 * override the directory by specifying it in the {@code nashorn.typeInfo.cacheDir} directory. Also, you can disable the
 * type info persistence altogether by specifying the {@code nashorn.typeInfo.disabled} system property.
 */
public final class OptimisticTypesPersistence {
    // The name of the default subdirectory within the system cache directory where we store type info.
    private static final String DEFAULT_CACHE_SUBDIR_NAME = "com.oracle.java.NashornTypeInfo";
    // The directory where we cache type info
    private static final File cacheDir = createCacheDir();
    // In-process locks to make sure we don't have a cross-thread race condition manipulating any file.
    private static final Object[] locks = cacheDir == null ? null : createLockArray();

    // Only report one read/write error every minute
    private static final long ERROR_REPORT_THRESHOLD = 60000L;

    private static volatile long lastReportedError;

    /**
     * Retrieves an opaque descriptor for the persistence location for a given function. It should be passed to
     * {@link #load(Object)} and {@link #store(Object, Map)} methods.
     * @param source the source where the function comes from
     * @param functionId the unique ID number of the function within the source
     * @param paramTypes the types of the function parameters (as persistence is per parameter type specialization).
     * @return an opaque descriptor for the persistence location. Can be null if persistence is disabled.
     */
    public static Object getLocationDescriptor(final Source source, final int functionId, final Type[] paramTypes) {
        if(cacheDir == null) {
            return null;
        }
        final StringBuilder b = new StringBuilder(48);
        // Base64-encode the digest of the source, and append the function id.
        b.append(source.getDigest()).append('-').append(functionId);
        // Finally, if this is a parameter-type specialized version of the function, add the parameter types to the file name.
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
            getLogger().warning(String.format("Failed to %s %s", msg, file), e);
            lastReportedError = now;
        }
    }

    private static File createCacheDir() {
        if(Options.getBooleanProperty("nashorn.typeInfo.disabled")) {
            return null;
        }
        try {
            return createCacheDirPrivileged();
        } catch(final Exception e) {
            getLogger().warning("Failed to create cache dir", e);
            return null;
        }
    }

    private static File createCacheDirPrivileged() {
        return AccessController.doPrivileged(new PrivilegedAction<File>() {
            @Override
            public File run() {
                final String explicitDir = System.getProperty("nashorn.typeInfo.cacheDir");
                final File dir;
                if(explicitDir != null) {
                    dir = new File(explicitDir);
                } else {
                    // When no directory is explicitly specified, get an operating system specific cache directory,
                    // and create "com.oracle.java.NashornTypeInfo" in it.
                    final File systemCacheDir = getSystemCacheDir();
                    dir = new File(systemCacheDir, DEFAULT_CACHE_SUBDIR_NAME);
                    if (isSymbolicLink(dir)) {
                        return null;
                    }
                }
                final String versionDirName;
                try {
                    versionDirName = getVersionDirName();
                } catch(final Exception e) {
                    getLogger().warning("Failed to calculate version dir name", e);
                    return null;
                }
                final File versionDir = new File(dir, versionDirName);
                if (isSymbolicLink(versionDir)) {
                    return null;
                }
                versionDir.mkdirs();
                if(versionDir.isDirectory()) {
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
            // On Windows, temp directory is the best approximation of a cache directory, as its contents persist across
            // reboots and various cleanup utilities know about it. java.io.tmpdir normally points to a user-specific
            // temp directory, %HOME%\LocalSettings\Temp.
            return new File(System.getProperty("java.io.tmpdir"));
        } else {
            // In all other cases we're presumably dealing with a UNIX flavor (Linux, Solaris, etc.); "~/.cache"
            return new File(System.getProperty("user.home"), ".cache");
        }
    }

    /**
     * In order to ensure that changes in Nashorn code don't cause corruption in the data, we'll create a
     * per-code-version directory. Normally, this will create the SHA-1 digest of the nashorn.jar. In case the classpath
     * for nashorn is local directory (e.g. during development), this will create the string "dev-" followed by the
     * timestamp of the most recent .class file.
     * @return
     */
    private static String getVersionDirName() throws Exception {
        final URL url = OptimisticTypesPersistence.class.getResource("");
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
            final String dirStr = fileStr.substring(0, fileStr.length() - packageNameLen - 1);
            final File dir = new File(dirStr);
            return "dev-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(getLastModifiedClassFile(dir, 0L)));
        } else {
            throw new AssertionError();
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
}
