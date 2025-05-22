/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.launcher;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import jdk.internal.module.Resources;

/**
 * An in-memory classloader, that uses an in-memory cache of classes written by
 * {@link MemoryFileManager}.
 *
 * <p>The classloader inverts the standard parent-delegation model, giving preference
 * to classes defined in the source file before classes known to the parent (such
 * as any like-named classes that might be found on the application class path.)
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
final class MemoryClassLoader extends ClassLoader {
    /**
     * The parent class loader instance.
     */
    private final ClassLoader parentClassLoader;

    /**
     * The map of all classes found in the source file, indexed by
     * {@link Class#getName()} binary name.
     */
    private final Map<String, byte[]> sourceFileClasses;

    /**
     * A minimal protection domain, specifying a code source of the source file itself,
     * used for classes found in the source file and defined by this loader.
     */
    private final ProtectionDomain domain;

    private final ModuleDescriptor moduleDescriptor;
    private final ProgramDescriptor programDescriptor;
    private final Function<String, byte[]> compileSourceFile;

    MemoryClassLoader(Map<String, byte[]> sourceFileClasses,
                      ClassLoader parentClassLoader,
                      ModuleDescriptor moduleDescriptor,
                      ProgramDescriptor programDescriptor,
                      Function<String, byte[]> compileSourceFile) {
        super(parentClassLoader);
        this.parentClassLoader = parentClassLoader;
        this.sourceFileClasses = sourceFileClasses;
        CodeSource codeSource;
        try {
            codeSource = new CodeSource(programDescriptor.fileObject().getFile().toUri().toURL(), (CodeSigner[])null);
        } catch (MalformedURLException e) {
            codeSource = null;
        }
        domain = new ProtectionDomain(codeSource, null, this, null);
        this.moduleDescriptor = moduleDescriptor;
        this.programDescriptor = programDescriptor;
        this.compileSourceFile = compileSourceFile;
    }

    /**
     * Override loadClass to check for classes defined in the source file
     * before checking for classes in the parent class loader,
     * including those on the classpath.
     * <p>
     * {@code loadClass(String name)} calls this method, and so will have the same behavior.
     *
     * @param name    the name of the class to load
     * @param resolve whether to resolve the class
     * @return the class
     * @throws ClassNotFoundException if the class is not found
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = findOrCompileClass(name);
                if (c == null) {
                    c = parentClassLoader.loadClass(name);
                }
                if (resolve) {
                    resolveClass(c);
                }
            }
            return c;
        }
    }


    /**
     * Override getResource to check for resources (i.e. class files) defined in the
     * source file before checking resources in the parent class loader,
     * including those on the class path.
     * <p>
     * {@code getResourceAsStream(String name)} calls this method,
     * and so will have the same behavior.
     *
     * @param name the name of the resource
     * @return a URL for the resource, or null if not found
     */
    @Override
    public URL getResource(String name) {
        if (sourceFileClasses.containsKey(toBinaryName(name))) {
            return findResource(name);
        }
        URL resource = toResourceInRootPath(name);
        if (resource != null) {
            return resource;
        }
        return parentClassLoader.getResource(name);
    }

    /**
     * Override getResources to check for resources (i.e. class files) defined in the
     * source file before checking resources in the parent class loader,
     * including those on the class path.
     *
     * @param name the name of the resource
     * @return an enumeration of the resources in this loader and in the application class loader
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        URL u = findResource(name);
        Enumeration<URL> e = parentClassLoader.getResources(name);
        if (u == null) {
            return e;
        } else {
            List<URL> list = new ArrayList<>();
            list.add(u);
            while (e.hasMoreElements()) {
                list.add(e.nextElement());
            }
            return Collections.enumeration(list);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        var foundOrCompiledClass = findOrCompileClass(name);
        if (foundOrCompiledClass == null) {
            throw new ClassNotFoundException(name);
        }
        return foundOrCompiledClass;
    }

    @Override
    protected Class<?> findClass(String moduleName, String name) {
        try {
            if (moduleName == null) {
                return findClass(name);
            }
            if (moduleDescriptor != null && moduleDescriptor.name().equals(moduleName)) {
                return findClass(name);
            }
            return super.findClass(moduleName, name);
        } catch (ClassNotFoundException ignore) { }
        return null;
    }

    private Class<?> findOrCompileClass(String name) {
        byte[] bytes = sourceFileClasses.get(name);
        if (bytes == null) {
            bytes = compileSourceFile.apply(name);
            if (bytes == null) {
                return null;
            }
        }
        return defineClass(name, bytes, 0, bytes.length, domain);
    }

    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        if (moduleName == null) {
            return getResource(name);
        }
        if (moduleDescriptor != null && moduleDescriptor.name().equals(moduleName)) {
            return getResource(name);
        }
        return super.findResource(moduleName, name);
    }

    @Override
    public URL findResource(String name) {
        String binaryName = toBinaryName(name);
        if (binaryName == null || sourceFileClasses.get(binaryName) == null) {
            return toResourceInRootPath(name);
        }

        URLStreamHandler handler = this.handler;
        if (handler == null) {
            this.handler = handler = new MemoryURLStreamHandler();
        }

        try {
            var uri = new URI(PROTOCOL, name, null);
            return URL.of(uri, handler);
        } catch (URISyntaxException | MalformedURLException e) {
            return null;
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) {
        return new Enumeration<URL>() {
            private URL next = findResource(name);

            @Override
            public boolean hasMoreElements() {
                return (next != null);
            }

            @Override
            public URL nextElement() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                URL u = next;
                next = null;
                return u;
            }
        };
    }

    /**
     * Resolves a "resource name" (as used in the getResource* methods)
     * to an existing file relative to source root path, or null otherwise.
     *
     * @param name the resource name
     * @return the URL of the resource, or null
     */
    private URL toResourceInRootPath(String name) {
        try {
            var path = Resources.toFilePath(programDescriptor.sourceRootPath(), name);
            return path == null ? null : path.toUri().toURL();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IOError error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException e) {
                throw new UncheckedIOException(e);
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * Converts a "resource name" (as used in the getResource* methods)
     * to a binary name if the name identifies a class, or null otherwise.
     *
     * @param name the resource name
     * @return the binary name
     */
    private String toBinaryName(String name) {
        if (!name.endsWith(".class")) {
            return null;
        }
        return name.substring(0, name.length() - DOT_CLASS_LENGTH).replace('/', '.');
    }

    private static final int DOT_CLASS_LENGTH = ".class".length();
    private final String PROTOCOL = "sourcelauncher-" + getClass().getSimpleName() + hashCode();
    private URLStreamHandler handler;

    /**
     * A URLStreamHandler for use with URLs returned by MemoryClassLoader.getResource.
     */
    private class MemoryURLStreamHandler extends URLStreamHandler {
        @Override
        public URLConnection openConnection(URL u) {
            if (!u.getProtocol().equalsIgnoreCase(PROTOCOL)) {
                throw new IllegalArgumentException(u.toString());
            }
            return new MemoryURLConnection(u, sourceFileClasses.get(toBinaryName(u.getPath())));
        }

    }

    /**
     * A URLConnection for use with URLs returned by MemoryClassLoader.getResource.
     */
    private static class MemoryURLConnection extends URLConnection {
        private final byte[] bytes;
        private InputStream in;

        MemoryURLConnection(URL u, byte[] bytes) {
            super(u);
            this.bytes = bytes;
        }

        @Override
        public void connect() throws IOException {
            if (!connected) {
                if (bytes == null) {
                    throw new FileNotFoundException(getURL().getPath());
                }
                in = new ByteArrayInputStream(bytes);
                connected = true;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            return in;
        }

        @Override
        public long getContentLengthLong() {
            return bytes.length;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }
    }
}
