/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;

import com.sun.istack.internal.Nullable;

/**
 * {@link ClassLoader} that loads Annotation Processing and specified classes
 * both into the same classloader, so that they can reference each other.
 *
 * @author Bhakti Mehta
 * @since 2.0 beta
 */
public final class ApClassLoader extends URLClassLoader {
    /**
     * List of package prefixes we want to mask the
     * parent classLoader from loading
     */
    private final String[] packagePrefixes;

    /**
     *
     * @param packagePrefixes
     *      The package prefixes that are forced to resolve within this class loader.
     * @param parent
     *      The parent class loader to delegate to. Null to indicate bootstrap classloader.
     */
    public ApClassLoader(@Nullable ClassLoader parent, String[] packagePrefixes) throws ToolsJarNotFoundException {
        super(getToolsJar(parent),parent);
        if(getURLs().length==0)
            // if tools.jar was found in our classloader, no need to create
            // a parallel classes
            this.packagePrefixes = new String[0];
        else
            this.packagePrefixes = packagePrefixes;
    }

    public Class loadClass(String className) throws ClassNotFoundException {
        for( String prefix : packagePrefixes ) {
            if (className.startsWith(prefix) ) {
                // we need to load those classes in this class loader
                // without delegation.
                return findClass(className);
            }
        }

        return super.loadClass(className);

    }

    protected Class findClass(String name) throws ClassNotFoundException {

        StringBuilder sb = new StringBuilder(name.length() + 6);
        sb.append(name.replace('.','/')).append(".class");

        InputStream is = getResourceAsStream(sb.toString());
        if (is==null)
            throw new ClassNotFoundException("Class not found" + sb);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while((len=is.read(buf))>=0)
                baos.write(buf,0,len);

            buf = baos.toByteArray();

            // define package if not defined yet
            int i = name.lastIndexOf('.');
            if (i != -1) {
                String pkgname = name.substring(0, i);
                Package pkg = getPackage(pkgname);
                if(pkg==null)
                    definePackage(pkgname, null, null, null, null, null, null, null);
            }

            return defineClass(name,buf,0,buf.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name,e);
        } finally {
            try {
                is.close();
            } catch (IOException ioe) {
                //ignore
            }
        }
    }

    /**
     * Returns a class loader that can load classes from JDK tools.jar.
     * @param parent
     */
    private static URL[] getToolsJar(@Nullable ClassLoader parent) throws ToolsJarNotFoundException {

        try {
            Class.forName("com.sun.tools.javac.Main", false, parent);
            return new URL[0];
            // we can already load them in the parent class loader.
            // so no need to look for tools.jar.
            // this happens when we are run inside IDE/Ant, or
            // in Mac OS.
        } catch (ClassNotFoundException e) {
            // otherwise try to find tools.jar
        }

        File jreHome = new File(System.getProperty("java.home"));
        File toolsJar = new File( jreHome.getParent(), "lib/tools.jar" );

        if (!toolsJar.exists()) {
            throw new ToolsJarNotFoundException(toolsJar);
        }

        try {
            return new URL[]{toolsJar.toURL()};
        } catch (MalformedURLException e) {
            // impossible
            throw new AssertionError(e);
        }
    }
}
