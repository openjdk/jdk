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

/**
 * A code cache for persistent caching of compiled scripts.
 */
final class CodeStore {

    private final File dir;
    private final int minSize;

    // Default minimum size for storing a compiled script class
    private final static int DEFAULT_MIN_SIZE = 1000;

    /**
     * Constructor
     * @param path directory to store code in
     * @throws IOException
     */
    public CodeStore(final String path) throws IOException {
        this(path, DEFAULT_MIN_SIZE);
    }

    /**
     * Constructor
     * @param path directory to store code in
     * @param minSize minimum file size for caching scripts
     * @throws IOException
     */
    public CodeStore(final String path, final int minSize) throws IOException {
        this.dir = checkDirectory(path);
        this.minSize = minSize;
    }

    private static File checkDirectory(final String path) throws IOException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<File>() {
                @Override
                public File run() throws IOException {
                    final File dir = new File(path).getAbsoluteFile();
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IOException("Could not create directory: " + dir);
                    } else if (!dir.isDirectory()) {
                        throw new IOException("Not a directory: " + dir);
                    } else if (!dir.canRead() || !dir.canWrite()) {
                        throw new IOException("Directory not readable or writable: " + dir);
                    }
                    return dir;
                }
            });
        } catch (final PrivilegedActionException e) {
            throw (IOException) e.getException();
        }
    }

    /**
     * Return a compiled script from the cache, or null if it isn't found.
     *
     * @param source the source
     * @return the compiled script or null
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public CompiledScript getScript(final Source source) throws IOException, ClassNotFoundException {
        if (source.getLength() < minSize) {
            return null;
        }

        final File file = new File(dir, source.getDigest());

        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<CompiledScript>() {
                @Override
                public CompiledScript run() throws IOException, ClassNotFoundException {
                    if (!file.exists()) {
                        return null;
                    }
                    try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                        final CompiledScript compiledScript = (CompiledScript) in.readObject();
                        compiledScript.setSource(source);
                        return compiledScript;
                    }
                }
            });
        } catch (final PrivilegedActionException e) {
            final Exception ex = e.getException();
            if (ex instanceof IOException) {
                throw  (IOException) ex;
            } else if (ex instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) ex;
            }
            throw (new RuntimeException(ex));
        }
    }

    /**
     * Store a compiled script in the cache.
     *
     * @param source the source
     * @param mainClassName the main class name
     * @param classBytes a map of class bytes
     * @param constants the constants array
     * @throws IOException
     */
    public void putScript(final Source source, final String mainClassName, final Map<String, byte[]> classBytes, final Object[] constants)
            throws IOException {
        if (source.getLength() < minSize) {
            return;
        }
        for (final Object constant : constants) {
            // Make sure all constant data is serializable
            if (! (constant instanceof Serializable)) {
                return;
            }
        }

        final File file = new File(dir, source.getDigest());
        final CompiledScript script = new CompiledScript(source, mainClassName, classBytes, constants);

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws IOException {
                    try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                        out.writeObject(script);
                    }
                    return null;
                }
            });
        } catch (final PrivilegedActionException e) {
             throw (IOException) e.getException();
        }
    }
}

