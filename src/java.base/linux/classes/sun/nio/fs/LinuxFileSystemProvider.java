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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileTypeDetector;
import jdk.internal.misc.Blocker;
import jdk.internal.util.StaticProperty;
import sun.nio.ch.IOStatus;

import static sun.nio.fs.LinuxNativeDispatcher.*;
import static sun.nio.fs.UnixConstants.*;

/**
 * Linux implementation of FileSystemProvider
 */

class LinuxFileSystemProvider extends UnixFileSystemProvider {
    public LinuxFileSystemProvider() {
        super();
    }

    @Override
    LinuxFileSystem newFileSystem(String dir) {
        return new LinuxFileSystem(this, dir);
    }

    @Override
    LinuxFileStore getFileStore(UnixPath path) throws IOException {
        return new LinuxFileStore(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path obj,
                                                                Class<V> type,
                                                                LinkOption... options)
    {
        if (type == DosFileAttributeView.class) {
            return (V) new LinuxDosFileAttributeView(UnixPath.toUnixPath(obj),
                                                     Util.followLinks(options));
        }
        if (type == UserDefinedFileAttributeView.class) {
            return (V) new LinuxUserDefinedFileAttributeView(UnixPath.toUnixPath(obj),
                                                             Util.followLinks(options));
        }
        return super.getFileAttributeView(obj, type, options);
    }

    @Override
    public DynamicFileAttributeView getFileAttributeView(Path obj,
                                                         String name,
                                                         LinkOption... options)
    {
        if (name.equals("dos")) {
            return new LinuxDosFileAttributeView(UnixPath.toUnixPath(obj),
                                                 Util.followLinks(options));
        }
        if (name.equals("user")) {
            return new LinuxUserDefinedFileAttributeView(UnixPath.toUnixPath(obj),
                                                         Util.followLinks(options));
        }
        return super.getFileAttributeView(obj, name, options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path file,
                                                            Class<A> type,
                                                            LinkOption... options)
        throws IOException
    {
        if (type == DosFileAttributes.class) {
            DosFileAttributeView view =
                getFileAttributeView(file, DosFileAttributeView.class, options);
            return (A) view.readAttributes();
        } else {
            return super.readAttributes(file, type, options);
        }
    }

    @Override
    FileTypeDetector getFileTypeDetector() {
        String userHome = StaticProperty.userHome();
        Path userMimeTypes = Path.of(userHome, ".mime.types");
        Path etcMimeTypes = Path.of("/etc/mime.types");

        return chain(new MimeTypesFileTypeDetector(userMimeTypes),
                     new MimeTypesFileTypeDetector(etcMimeTypes));
    }

    @Override
    public int clone(Path source, Path target, boolean noFollowLinks)
        throws IOException {
        UnixPath src = UnixPath.toUnixPath(source);
        int srcFD = 0;
        try {
            srcFD = open(src, O_RDONLY, 0);
        } catch (UnixException x) {
            x.rethrowAsIOException(src);
            return IOStatus.THROWN;
        }

        UnixPath dst = UnixPath.toUnixPath(target);
        int dstFD = 0;
        try {
            dstFD = open(dst, O_CREAT | O_WRONLY, 0666);
        } catch (UnixException x) {
            try {
                close(srcFD);
            } catch (UnixException y) {
                x.addSuppressed(y);
                x.rethrowAsIOException(src, dst);
                return IOStatus.THROWN;
            }
            x.rethrowAsIOException(dst);
            return IOStatus.THROWN;
        }

        try {
            return ioctl_ficlone(dstFD, srcFD);
        } catch (UnixException x) {
            try {
                close(dstFD);
                dstFD = 0;
            } catch (UnixException y) {
                x.rethrowAsIOException(dst);
                return IOStatus.THROWN;
            }
            // delete dst to avoid later exception in Java layer
            try {
                unlink(dst);
            } catch (UnixException y) {
                x.rethrowAsIOException(dst);
                return IOStatus.THROWN;
            }
            switch (x.errno()) {
                case EINVAL:
                    return IOStatus.UNSUPPORTED;
                case EPERM:
                    x.rethrowAsIOException(src, dst);
                    return IOStatus.THROWN;
                default:
                    return IOStatus.UNSUPPORTED_CASE;
            }
        } finally {
            UnixException ue = null;
            UnixPath s = null;
            UnixPath d = null;
            if (dstFD != 0) {
                try {
                    close(dstFD);
                } catch (UnixException x) {
                    ue = x;
                    d = dst;
                }
            }
            try {
                close(srcFD);
            } catch (UnixException x) {
                if (ue != null)
                    ue.addSuppressed(x);
                else
                    ue = x;
                s = src;
            }
            if (ue != null) {
                if (s != null && d != null)
                    ue.rethrowAsIOException(s, d);
                else
                    ue.rethrowAsIOException(s != null ? s : d);
            }
        }
    }
}
