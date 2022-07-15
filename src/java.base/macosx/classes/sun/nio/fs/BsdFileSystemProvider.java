/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
import sun.nio.ch.IOStatus;
import static sun.nio.fs.UnixConstants.*;

/**
 * Bsd implementation of FileSystemProvider
 */

class BsdFileSystemProvider extends UnixFileSystemProvider {
    public BsdFileSystemProvider() {
        super();
    }

    @Override
    BsdFileSystem newFileSystem(String dir) {
        return new BsdFileSystem(this, dir);
    }

    @Override
    BsdFileStore getFileStore(UnixPath path) throws IOException {
        return new BsdFileStore(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path obj,
                                                                Class<V> type,
                                                                LinkOption... options)
    {
        if (type == UserDefinedFileAttributeView.class) {
            return (V) new BsdUserDefinedFileAttributeView(UnixPath.toUnixPath(obj),
                    Util.followLinks(options));
        }
        return super.getFileAttributeView(obj, type, options);
    }

    @Override
    public DynamicFileAttributeView getFileAttributeView(Path obj,
                                                         String name,
                                                         LinkOption... options)
    {
        if (name.equals("user")) {
            return new BsdUserDefinedFileAttributeView(UnixPath.toUnixPath(obj),
                    Util.followLinks(options));
        }
        return super.getFileAttributeView(obj, name, options);
    }

    @Override
    public int clone(Path source, Path target, boolean followLinks)
        throws IOException {
        UnixPath src = UnixPath.toUnixPath(source);
        UnixPath dst = UnixPath.toUnixPath(target);
        int flags = followLinks ? 0 : CLONE_NOFOLLOW;
        try {
            return BsdNativeDispatcher.clonefile(src, dst, flags);
        } catch (UnixException x) {
            switch (x.errno()) {
                case ENOTSUP: // cloning not supported by filesystem
                    return IOStatus.UNSUPPORTED;
                case EXDEV:   // src and dst on different filesystems
                case ENOTDIR: // problematic path parameter(s)
                    return IOStatus.UNSUPPORTED_CASE;
                default:
                    x.rethrowAsIOException(src, dst);
                    return IOStatus.THROWN;
            }
        }
    }
}
