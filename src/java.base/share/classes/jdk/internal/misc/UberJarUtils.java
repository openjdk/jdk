/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package jdk.internal.misc;

import java.io.File;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.WeakHashMap;
import jdk.internal.loader.ClassLoaders;

/**
 * This class is being used by VM during CDS static dump time for loading classes
 * by custom loaders.
 * It supports loading classes from source in the following format:
 * 1. directory path, e.g. /some/dir
 * 2. jar file, e.g. /some/file.jar
 * 3. jar file with jar protocol, e.g. jar:file:/some/file.jar!/
 * 4. jar protocol with embedded jar, e.g. jar:file:/some/file.jar!/another/file2.jar!/
 * 5. jar protocol with embedded directory, e.g. jar:file:/some/file.jar!/another/dir!/
 */
public class UberJarUtils {
    final static String JAR_NAME_SEPARATOR = "!/";
    final static String JAR_SUFFIX = ".jar";
    final static String CLASS_SUFFIX = ".class";
    final static String JAR_PROTOCOL = "jar:file:";

    /**
     * Load a class based on the source and class name.
     * This is being called from VM during CDS static dump time.
     */
    public static Class<?> loadClass(String source, String className)
        throws IOException, ClassNotFoundException {
        return getReader(source).loadClass(className);
    }

    /**
     * Check if the source contains an embedded jar file name,
     * e.g. jar:file:/some/file.jar!/another/file2.jar!/
     */
    static boolean isEmbeddedJar(String source) {
        if (source.indexOf(JAR_PROTOCOL) < 0) {
            return false;
        }
        if (source.indexOf(JAR_NAME_SEPARATOR) < 0) {
            return false;
        }
        if (source.endsWith(CLASS_SUFFIX + JAR_NAME_SEPARATOR)) {
            return false;
        }
        if (source.length() - JAR_NAME_SEPARATOR.length() != source.lastIndexOf(JAR_NAME_SEPARATOR)) {
            return false;
        }
        if (isSimpleJarURL(source) || isJarProtocolWithDirectory(source)) {
            return false;
        }
        return true;
    }

    static WeakHashMap<String, AbstractJarReader> readerCache =
        new WeakHashMap<String, AbstractJarReader>();

    /**
      * Create an appropriate jar reader based on the source.
      * The jar reader could be an EmbeddedJarReader or a SimpleJarReader.
      */
    static AbstractJarReader getReader(String source)
        throws IOException, ClassNotFoundException, IllegalArgumentException, MalformedURLException {
        AbstractJarReader r = readerCache.get(source);
        if (r != null) {
            // this means the source has already been checked, no need to check it again
            return r;
        }
        if (isEmbeddedJar(source)) {
            // assume source = "jar:file:/some/path/outer.jar!/dir/inner.jar!/"
            // String parentPath = "jar:file:/some/path/outer.jar!/";
            // String subPath = "dir/inner.jar";
            String[] urls = source.split("!");
            int endIndex = source.indexOf(urls[urls.length - 2]);
            String parentPath = source.substring(0, endIndex + 1);
            String subPath = urls[urls.length - 2].substring(1);
            AbstractJarReader parentReader = getReader(parentPath);
            byte[] jarBytes = parentReader.getEntry(subPath);

            r = new EmbeddedJarReader(subPath, jarBytes);
        } else {
            String u = source;
            URL url;
            if (isJarProtocolWithDirectory(source)) {
                // Remove the last '!' and end the url with '/'.
                // e.g source = "jar:file:test.jar!/a/dir!/
                //     url = "jar:file:test.jar!/a/dir/
                u = source.substring(0, source.lastIndexOf('!')) + "/";
                url = new URL(u);
            } else if (isSimpleJarURL(source)) {
                url = new URL(u);
            } else {
                File f = new File(source);
                if (f.exists()) {
                    // The source of CDS unregister classes can be either a dir or a jar file.
                    url = ClassLoaders.toFileURL(source);
                } else {
                    throw new IllegalArgumentException("unsupported source: " + source);
                }
            }
            r = new SimpleJarReader(url);
        }
        readerCache.put(source, r);
        return r;
    }

    /**
      * Check if the source is a simple jar URL.
      * e.g. jar:file:/some/file.jar!/
      */
    static boolean isSimpleJarURL(final String source) {
        if (!source.endsWith(JAR_SUFFIX + JAR_NAME_SEPARATOR)) {
            return false;
        }
        int firstIndex = source.indexOf(JAR_NAME_SEPARATOR);
        return firstIndex >= 0 && firstIndex == source.lastIndexOf(JAR_NAME_SEPARATOR);
    }

    /**
      * Check if the source is a directory within a jar protocol.
      * e.g. jar:file:/some/file.jar!/another/dir!/
      */
    static boolean isJarProtocolWithDirectory(final String source) {
        return source.startsWith(JAR_PROTOCOL) &&
               source.endsWith(JAR_NAME_SEPARATOR) &&
               !source.endsWith(JAR_SUFFIX + JAR_NAME_SEPARATOR) &&
               !source.endsWith(CLASS_SUFFIX + JAR_NAME_SEPARATOR);
    }
}
