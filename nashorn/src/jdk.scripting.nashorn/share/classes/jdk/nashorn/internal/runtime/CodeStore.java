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

package jdk.nashorn.internal.runtime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import jdk.nashorn.internal.codegen.OptimisticTypesPersistence;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * A code cache for persistent caching of compiled scripts.
 */
@Logger(name="codestore")
public abstract class CodeStore implements Loggable {

    /**
     * Permission needed to provide a CodeStore instance via ServiceLoader.
     */
    public final static String NASHORN_PROVIDE_CODE_STORE = "nashorn.provideCodeStore";

    private DebugLogger log;

    /**
     * Constructor
     */
    protected CodeStore() {
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        log = context.getLogger(getClass());
        return log;
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    /**
     * Returns a new code store instance.
     *
     * @param context the current context
     * @return The instance, or null if code store could not be created
     */
    public static CodeStore newCodeStore(final Context context) {
        final Class<CodeStore> baseClass = CodeStore.class;
        try {
            // security check first
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(new RuntimePermission(NASHORN_PROVIDE_CODE_STORE));
            }
            final ServiceLoader<CodeStore> services = ServiceLoader.load(baseClass);
            final Iterator<CodeStore> iterator = services.iterator();
            if (iterator.hasNext()) {
                final CodeStore store = iterator.next();
                store.initLogger(context).info("using code store provider ", store.getClass().getCanonicalName());
                return store;
            }
        } catch (final AccessControlException e) {
            context.getLogger(CodeStore.class).warning("failed to load code store provider ", e);
        }
        try {
            final CodeStore store = new DirectoryCodeStore(context);
            store.initLogger(context);
            return store;
        } catch (final IOException e) {
            context.getLogger(CodeStore.class).warning("failed to create cache directory ", e);
            return null;
        }
    }


    /**
     * Store a compiled script in the cache.
     *
     * @param functionKey   the function key
     * @param source        the source
     * @param mainClassName the main class name
     * @param classBytes    a map of class bytes
     * @param initializers  the function initializers
     * @param constants     the constants array
     * @param compilationId the compilation id
     *
     * @return stored script
     */
    public StoredScript store(final String functionKey,
                              final Source source,
                              final String mainClassName,
                              final Map<String, byte[]> classBytes,
                              final Map<Integer, FunctionInitializer> initializers,
                              final Object[] constants,
                              final int compilationId) {
        return store(functionKey, source, storedScriptFor(source, mainClassName, classBytes, initializers, constants, compilationId));
    }

    /**
     * Stores a compiled script.
     *
     * @param functionKey the function key
     * @param source the source
     * @param script The compiled script
     * @return The compiled script or {@code null} if not stored
     */
    public abstract StoredScript store(final String functionKey,
                                       final Source source,
                                       final StoredScript script);

    /**
     * Return a compiled script from the cache, or null if it isn't found.
     *
     * @param source      the source
     * @param functionKey the function key
     * @return the stored script or null
     */
    public abstract StoredScript load(final Source source, final String functionKey);

    /**
     * Returns a new StoredScript instance.
     *
     * @param source the source
     * @param mainClassName the main class name
     * @param classBytes a map of class bytes
     * @param initializers function initializers
     * @param constants the constants array
     * @param compilationId the compilation id
     *
     * @return The compiled script
     */
    public StoredScript storedScriptFor(final Source source, final String mainClassName,
                                        final Map<String, byte[]> classBytes,
                                        final Map<Integer, FunctionInitializer> initializers,
                                        final Object[] constants, final int compilationId) {
        for (final Object constant : constants) {
            // Make sure all constant data is serializable
            if (!(constant instanceof Serializable)) {
                getLogger().warning("cannot store ", source, " non serializable constant ", constant);
                return null;
            }
        }
        return new StoredScript(compilationId, mainClassName, classBytes, initializers, constants);
    }

    /**
     * Generate a string representing the function with {@code functionId} and {@code paramTypes}.
     * @param functionId function id
     * @param paramTypes parameter types
     * @return a string representing the function
     */
    public static String getCacheKey(final int functionId, final Type[] paramTypes) {
        final StringBuilder b = new StringBuilder().append(functionId);
        if(paramTypes != null && paramTypes.length > 0) {
            b.append('-');
            for(final Type t: paramTypes) {
                b.append(Type.getShortSignatureDescriptor(t));
            }
        }
        return b.toString();
    }

    /**
     * A store using a file system directory.
     */
    public static class DirectoryCodeStore extends CodeStore {

        // Default minimum size for storing a compiled script class
        private final static int DEFAULT_MIN_SIZE = 1000;

        private final File dir;
        private final boolean readOnly;
        private final int minSize;

        /**
         * Constructor
         *
         * @param context the current context
         * @throws IOException if there are read/write problems with the cache and cache directory
         */
        public DirectoryCodeStore(final Context context) throws IOException {
            this(context, Options.getStringProperty("nashorn.persistent.code.cache", "nashorn_code_cache"), false, DEFAULT_MIN_SIZE);
        }

        /**
         * Constructor
         *
         * @param context the current context
         * @param path    directory to store code in
         * @param readOnly is this a read only code store
         * @param minSize minimum file size for caching scripts
         * @throws IOException if there are read/write problems with the cache and cache directory
         */
        public DirectoryCodeStore(final Context context, final String path, final boolean readOnly, final int minSize) throws IOException {
            this.dir = checkDirectory(path, context.getEnv(), readOnly);
            this.readOnly = readOnly;
            this.minSize = minSize;
        }

        private static File checkDirectory(final String path, final ScriptEnvironment env, final boolean readOnly) throws IOException {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<File>() {
                    @Override
                    public File run() throws IOException {
                        final File dir = new File(path, getVersionDir(env)).getAbsoluteFile();
                        if (readOnly) {
                            if (!dir.exists() || !dir.isDirectory()) {
                                throw new IOException("Not a directory: " + dir.getPath());
                            } else if (!dir.canRead()) {
                                throw new IOException("Directory not readable: " + dir.getPath());
                            }
                        } else if (!dir.exists() && !dir.mkdirs()) {
                            throw new IOException("Could not create directory: " + dir.getPath());
                        } else if (!dir.isDirectory()) {
                            throw new IOException("Not a directory: " + dir.getPath());
                        } else if (!dir.canRead() || !dir.canWrite()) {
                            throw new IOException("Directory not readable or writable: " + dir.getPath());
                        }
                        return dir;
                    }
                });
            } catch (final PrivilegedActionException e) {
                throw (IOException) e.getException();
            }
        }

        private static String getVersionDir(final ScriptEnvironment env) throws IOException {
            try {
                final String versionDir = OptimisticTypesPersistence.getVersionDirName();
                return env._optimistic_types ? versionDir + "_opt" : versionDir;
            } catch (final Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public StoredScript load(final Source source, final String functionKey) {
            if (source.getLength() < minSize) {
                return null;
            }

            final File file = getCacheFile(source, functionKey);

            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<StoredScript>() {
                    @Override
                    public StoredScript run() throws IOException, ClassNotFoundException {
                        if (!file.exists()) {
                            return null;
                        }
                        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                            final StoredScript storedScript = (StoredScript) in.readObject();
                            getLogger().info("loaded ", source, "-", functionKey);
                            return storedScript;
                        }
                    }
                });
            } catch (final PrivilegedActionException e) {
                getLogger().warning("failed to load ", source, "-", functionKey, ": ", e.getException());
                return null;
            }
        }

        @Override
        public StoredScript store(final String functionKey, final Source source, final StoredScript script) {
            if (readOnly || script == null || belowThreshold(source)) {
                return null;
            }

            final File file = getCacheFile(source, functionKey);

            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<StoredScript>() {
                    @Override
                    public StoredScript run() throws IOException {
                        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                            out.writeObject(script);
                        }
                        getLogger().info("stored ", source, "-", functionKey);
                        return script;
                    }
                });
            } catch (final PrivilegedActionException e) {
                getLogger().warning("failed to store ", script, "-", functionKey, ": ", e.getException());
                return null;
            }
        }


        private File getCacheFile(final Source source, final String functionKey) {
            return new File(dir, source.getDigest() + '-' + functionKey);
        }

        private boolean belowThreshold(final Source source) {
            if (source.getLength() < minSize) {
                getLogger().info("below size threshold ", source);
                return true;
            }
            return false;
        }
    }
}

