/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.*;
import java.util.Set;
import java.util.EnumSet;

/**
 * Helper class to support creation of temporary files and directory with
 * initial attributes.
 */

class TempFileHelper {
    private TempFileHelper() { }

    // default file and directory permissions (lazily initialized)
    private static class PermissionsHolder {
        static final boolean hasPosixPermissions = FileSystems.getDefault()
            .supportedFileAttributeViews().contains("posix");
        static final FileAttribute<Set<PosixFilePermission>> filePermissions =
            PosixFilePermissions.asFileAttribute(EnumSet.of(OWNER_READ, OWNER_WRITE));
        static final FileAttribute<Set<PosixFilePermission>> directoryPermissions =
            PosixFilePermissions.asFileAttribute(EnumSet
                .of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
    }

    /**
     * Creates a file or directory in the temporary directory.
     */
    private static File create(String prefix,
                               String suffix,
                               FileAttribute[] attrs,
                               boolean isDirectory)
        throws IOException
    {
        // in POSIX environments use default file and directory permissions
        // if initial permissions not given by caller.
        if (PermissionsHolder.hasPosixPermissions) {
            if (attrs.length == 0) {
                // no attributes so use default permissions
                attrs = new FileAttribute<?>[1];
                attrs[0] = (isDirectory) ? PermissionsHolder.directoryPermissions :
                    PermissionsHolder.filePermissions;
            } else {
                // check if posix permissions given; if not use default
                boolean hasPermissions = false;
                for (int i=0; i<attrs.length; i++) {
                    if (attrs[i].name().equals("posix:permissions")) {
                        hasPermissions = true;
                        break;
                    }
                }
                if (!hasPermissions) {
                    FileAttribute<?>[] copy = new FileAttribute<?>[attrs.length+1];
                    System.arraycopy(attrs, 0, copy, 0, attrs.length);
                    attrs = copy;
                    attrs[attrs.length-1] = (isDirectory) ?
                        PermissionsHolder.directoryPermissions :
                        PermissionsHolder.filePermissions;
                }
            }
        }

        // loop generating random names until file or directory can be created
        SecurityManager sm = System.getSecurityManager();
        for (;;) {
            File tmpdir = File.TempDirectory.location();
            File f = File.TempDirectory.generateFile(prefix, suffix, tmpdir);
            try {
                if (isDirectory) {
                    f.toPath().createDirectory(attrs);
                } else {
                    f.toPath().createFile(attrs);
                }
                return f;
            } catch (InvalidPathException e) {
                // don't reveal temporary directory location
                if (sm != null)
                    throw new IllegalArgumentException("Invalid prefix or suffix");
                throw e;
            } catch (SecurityException e) {
                // don't reveal temporary directory location
                if (sm != null)
                    throw new SecurityException("Unable to create temporary file");
                throw e;
            } catch (FileAlreadyExistsException e) {
                // ignore
            }
        }
    }

    /**
     * Creates a file in the temporary directory.
     */
    static File createFile(String prefix,  String suffix, FileAttribute[] attrs)
        throws IOException
    {
        return create(prefix, suffix, attrs, false);
    }

    /**
     * Creates a directory in the temporary directory.
     */
    static File createDirectory(String prefix, FileAttribute[] attrs)
        throws IOException
    {
        return create(prefix, "", attrs, true);
    }
}
