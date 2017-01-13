/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jshell.execution;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Map;
import java.util.TreeMap;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;
import jdk.jshell.spi.ExecutionControl.ClassInstallException;
import jdk.jshell.spi.ExecutionControl.EngineTerminationException;
import jdk.jshell.spi.ExecutionControl.InternalException;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;

/**
 * The standard implementation of {@link LoaderDelegate} using
 * a {@link URLClassLoader}.
 *
 * @author Robert Field
 */
class DefaultLoaderDelegate implements LoaderDelegate {

    private final RemoteClassLoader loader;
    private final Map<String, Class<?>> klasses = new TreeMap<>();

    class RemoteClassLoader extends URLClassLoader {

        private final Map<String, byte[]> classObjects = new TreeMap<>();

        RemoteClassLoader() {
            super(new URL[0]);
        }

        void delare(String name, byte[] bytes) {
            classObjects.put(name, bytes);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = classObjects.get(name);
            if (b == null) {
                return super.findClass(name);
            }
            return super.defineClass(name, b, 0, b.length, (CodeSource) null);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }

    }

    public DefaultLoaderDelegate() {
        this.loader = new RemoteClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
    }

    @Override
    public void load(ClassBytecodes[] cbcs)
            throws ClassInstallException, EngineTerminationException {
        boolean[] loaded = new boolean[cbcs.length];
        try {
            for (ClassBytecodes cbc : cbcs) {
                loader.delare(cbc.name(), cbc.bytecodes());
            }
            for (int i = 0; i < cbcs.length; ++i) {
                ClassBytecodes cbc = cbcs[i];
                Class<?> klass = loader.loadClass(cbc.name());
                klasses.put(cbc.name(), klass);
                loaded[i] = true;
                // Get class loaded to the point of, at least, preparation
                klass.getDeclaredMethods();
            }
        } catch (Throwable ex) {
            throw new ClassInstallException("load: " + ex.getMessage(), loaded);
        }
    }


    @Override
    public void addToClasspath(String cp)
            throws EngineTerminationException, InternalException {
        try {
            for (String path : cp.split(File.pathSeparator)) {
                loader.addURL(new File(path).toURI().toURL());
            }
        } catch (Exception ex) {
            throw new InternalException(ex.toString());
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> klass = klasses.get(name);
        if (klass == null) {
            throw new ClassNotFoundException(name + " not found");
        } else {
            return klass;
        }
    }

}
