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
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * A code cache for persistent caching of compiled scripts.
 */
@Logger(name="codestore")
final class CodeStore implements Loggable {

    private final File dir;
    private final int minSize;
    private final DebugLogger log;

    // Default minimum size for storing a compiled script class
    private final static int DEFAULT_MIN_SIZE = 1000;

    /**
     * Constructor
     * @throws IOException
     */
    public CodeStore(final Context context, final String path) throws IOException {
        this(context, path, DEFAULT_MIN_SIZE);
    }

    /**
     * Constructor
     * @param path directory to store code in
     * @param minSize minimum file size for caching scripts
     * @throws IOException
     */
    public CodeStore(final Context context, final String path, final int minSize) throws IOException {
        this.dir = checkDirectory(path);
        this.minSize = minSize;
        this.log = initLogger(context);
    }

    @Override
    public DebugLogger initLogger(final Context context) {
         return context.getLogger(getClass());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    private static File checkDirectory(final String path) throws IOException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<File>() {
                @Override
                public File run() throws IOException {
                    final File dir = new File(path).getAbsoluteFile();
                    if (!dir.exists() && !dir.mkdirs()) {
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

    private File getCacheFile(final Source source, final String functionKey) {
        return new File(dir, source.getDigest() + '-' + functionKey);
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
     * Return a compiled script from the cache, or null if it isn't found.
     *
     * @param source the source
     * @param functionKey the function key
     * @return the stored script or null
     */
    public StoredScript loadScript(final Source source, final String functionKey) {
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

    /**
     * Store a compiled script in the cache.
     *
     * @param functionKey the function key
     * @param source the source
     * @param mainClassName the main class name
     * @param classBytes a map of class bytes
     * @param constants the constants array
     */
    public void storeScript(final String functionKey, final Source source, final String mainClassName, final Map<String, byte[]> classBytes,
                          final Map<Integer, FunctionInitializer> initializers, final Object[] constants, final int compilationId) {
        if (source.getLength() < minSize) {
            return;
        }
        for (final Object constant : constants) {
            // Make sure all constant data is serializable
            if (! (constant instanceof Serializable)) {
                getLogger().warning("cannot store ", source, " non serializable constant ", constant);
                return;
            }
        }

        final File file = getCacheFile(source, functionKey);
        final StoredScript script = new StoredScript(compilationId, mainClassName, classBytes, initializers, constants);

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws IOException {
                    try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                        out.writeObject(script);
                    }
                    getLogger().info("stored ", source, "-", functionKey);
                    return null;
                }
            });
        } catch (final PrivilegedActionException e) {
            getLogger().warning("failed to store ", script, "-", functionKey, ": ", e.getException());
        }
    }
}

