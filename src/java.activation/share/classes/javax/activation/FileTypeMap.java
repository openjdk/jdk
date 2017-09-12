/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.activation;

import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The FileTypeMap is an abstract class that provides a data typing
 * interface for files. Implementations of this class will
 * implement the getContentType methods which will derive a content
 * type from a file name or a File object. FileTypeMaps could use any
 * scheme to determine the data type, from examining the file extension
 * of a file (like the MimetypesFileTypeMap) to opening the file and
 * trying to derive its type from the contents of the file. The
 * FileDataSource class uses the default FileTypeMap (a MimetypesFileTypeMap
 * unless changed) to determine the content type of files.
 *
 * @see javax.activation.FileTypeMap
 * @see javax.activation.FileDataSource
 * @see javax.activation.MimetypesFileTypeMap
 *
 * @since 1.6
 */

public abstract class FileTypeMap {

    private static FileTypeMap defaultMap = null;
    private static Map<ClassLoader,FileTypeMap> map =
                                new WeakHashMap<ClassLoader,FileTypeMap>();

    /**
     * The default constructor.
     */
    public FileTypeMap() {
        super();
    }

    /**
     * Return the type of the file object. This method should
     * always return a valid MIME type.
     *
     * @param file A file to be typed.
     * @return The content type.
     */
    abstract public String getContentType(File file);

    /**
     * Return the type of the file passed in.  This method should
     * always return a valid MIME type.
     *
     * @param filename the pathname of the file.
     * @return The content type.
     */
    abstract public String getContentType(String filename);

    /**
     * Sets the default FileTypeMap for the system. This instance
     * will be returned to callers of getDefaultFileTypeMap.
     *
     * @param fileTypeMap The FileTypeMap.
     * @exception SecurityException if the caller doesn't have permission
     *                                  to change the default
     */
    public static synchronized void setDefaultFileTypeMap(FileTypeMap fileTypeMap) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            try {
                // if it's ok with the SecurityManager, it's ok with me...
                security.checkSetFactory();
            } catch (SecurityException ex) {
                // otherwise, we also allow it if this code and the
                // factory come from the same (non-system) class loader (e.g.,
                // the JAF classes were loaded with the applet classes).
                ClassLoader cl = FileTypeMap.class.getClassLoader();
                if (cl == null || cl.getParent() == null ||
                    cl != fileTypeMap.getClass().getClassLoader())
                    throw ex;
            }
        }
        // remove any per-thread-context-class-loader FileTypeMap
        map.remove(SecuritySupport.getContextClassLoader());
        defaultMap = fileTypeMap;
    }

    /**
     * Return the default FileTypeMap for the system.
     * If setDefaultFileTypeMap was called, return
     * that instance, otherwise return an instance of
     * <code>MimetypesFileTypeMap</code>.
     *
     * @return The default FileTypeMap
     * @see javax.activation.FileTypeMap#setDefaultFileTypeMap
     */
    public static synchronized FileTypeMap getDefaultFileTypeMap() {
        if (defaultMap != null)
            return defaultMap;

        // fetch per-thread-context-class-loader default
        ClassLoader tccl = SecuritySupport.getContextClassLoader();
        FileTypeMap def = map.get(tccl);
        if (def == null) {
            def = new MimetypesFileTypeMap();
            map.put(tccl, def);
        }
        return def;
    }
}
