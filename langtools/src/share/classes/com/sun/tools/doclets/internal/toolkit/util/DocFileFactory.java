/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.util.Map;
import java.util.WeakHashMap;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.doclets.internal.toolkit.Configuration;

/**
 * Factory for DocFile objects.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.8
 */
abstract class DocFileFactory {
    private static final Map<Configuration, DocFileFactory> factories =
            new WeakHashMap<Configuration, DocFileFactory>();

    /**
     * Get the appropriate factory, based on the file manager given in the
     * configuration.
     */
    static synchronized DocFileFactory getFactory(Configuration configuration) {
        DocFileFactory f = factories.get(configuration);
        if (f == null) {
            JavaFileManager fm = configuration.getFileManager();
            if (fm instanceof StandardJavaFileManager)
                f = new StandardDocFileFactory(configuration);
            else {
                try {
                    Class<?> pathFileManagerClass =
                            Class.forName("com.sun.tools.javac.nio.PathFileManager");
                    if (pathFileManagerClass.isAssignableFrom(fm.getClass()))
                        f = new PathDocFileFactory(configuration);
                } catch (Throwable t) {
                    throw new IllegalStateException(t);
                }
            }
            factories.put(configuration, f);
        }
        return f;
    }

    protected Configuration configuration;

    protected DocFileFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    /** Create a DocFile for a directory. */
    abstract DocFile createFileForDirectory(String file);

    /** Create a DocFile for a file that will be opened for reading. */
    abstract DocFile createFileForInput(String file);

    /** Create a DocFile for a file that will be opened for writing. */
    abstract DocFile createFileForOutput(DocPath path);

    /**
     * List the directories and files found in subdirectories along the
     * elements of the given location.
     * @param location currently, only {@link StandardLocation#SOURCE_PATH} is supported.
     * @param path the subdirectory of the directories of the location for which to
     *  list files
     */
    abstract Iterable<DocFile> list(Location location, DocPath path);
}
