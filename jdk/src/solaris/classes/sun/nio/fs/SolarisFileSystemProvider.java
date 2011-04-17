/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;

/**
 * Solaris implementation of FileSystemProvider
 */

public class SolarisFileSystemProvider extends UnixFileSystemProvider {
    public SolarisFileSystemProvider() {
        super();
    }

    @Override
    SolarisFileSystem newFileSystem(String dir) {
        return new SolarisFileSystem(this, dir);
    }

    @Override
    SolarisFileStore getFileStore(UnixPath path) throws IOException {
        return new SolarisFileStore(path);
    }


    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path obj,
                                                                Class<V> type,
                                                                LinkOption... options)
    {
        if (type == AclFileAttributeView.class) {
            return (V) new SolarisAclFileAttributeView(UnixPath.toUnixPath(obj),
                                                       Util.followLinks(options));
        }
        if (type == UserDefinedFileAttributeView.class) {
            return(V) new SolarisUserDefinedFileAttributeView(UnixPath.toUnixPath(obj),
                                                              Util.followLinks(options));
        }
        return super.getFileAttributeView(obj, type, options);
    }

    @Override
    public DynamicFileAttributeView getFileAttributeView(Path obj,
                                                         String name,
                                                         LinkOption... options)
    {
        if (name.equals("acl"))
            return new SolarisAclFileAttributeView(UnixPath.toUnixPath(obj),
                                                   Util.followLinks(options));
        if (name.equals("user"))
            return new SolarisUserDefinedFileAttributeView(UnixPath.toUnixPath(obj),
                                                           Util.followLinks(options));
        return super.getFileAttributeView(obj, name, options);
    }
}
