/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import static sun.nio.fs.BsdNativeDispatcher.setattrlist;

class BsdFileAttributeViews {
    //
    // Use setattrlist(2) system call which can set creation, modification,
    // and access times.
    //
    private static void setTimes(UnixPath path, FileTime lastModifiedTime,
                                 FileTime lastAccessTime, FileTime createTime,
                                 boolean followLinks) throws IOException
    {
        // null => don't change
        if (lastModifiedTime == null && lastAccessTime == null &&
            createTime == null) {
            // no effect
            return;
        }

        // permission check
        path.checkWrite();

        // not all volumes support setattrlist(2), so set the last
        // modified and last access times using the Unix implementation
        if (lastModifiedTime != null || lastAccessTime != null) {
            UnixFileAttributeViews.Basic basic =
                new UnixFileAttributeViews.Basic(path, followLinks);
            basic.setTimes(lastModifiedTime, lastAccessTime, null);
        }

        // set the creation time using setattrlist
        if (createTime != null) {
            long createValue = createTime.to(TimeUnit.NANOSECONDS);
            int commonattr = UnixConstants.ATTR_CMN_CRTIME;
            try {
                setattrlist(path, commonattr, 0L, 0L, createValue,
                            followLinks ?  0 : UnixConstants.FSOPT_NOFOLLOW);
            } catch (UnixException x) {
                x.rethrowAsIOException(path);
            }
        }
    }

    static class Basic extends UnixFileAttributeViews.Basic {
        Basic(UnixPath file, boolean followLinks) {
            super(file, followLinks);
        }

        @Override
        public void setTimes(FileTime lastModifiedTime,
                             FileTime lastAccessTime,
                             FileTime createTime) throws IOException
        {
            BsdFileAttributeViews.setTimes(file, lastModifiedTime,
                                           lastAccessTime, createTime,
                                           followLinks);
        }
    }

    static class Posix extends UnixFileAttributeViews.Posix {
        Posix(UnixPath file, boolean followLinks) {
            super(file, followLinks);
        }

        @Override
        public void setTimes(FileTime lastModifiedTime,
                             FileTime lastAccessTime,
                             FileTime createTime) throws IOException
        {
            BsdFileAttributeViews.setTimes(file, lastModifiedTime,
                                           lastAccessTime, createTime,
                                           followLinks);
        }
    }

    static class Unix extends UnixFileAttributeViews.Unix {
        Unix(UnixPath file, boolean followLinks) {
            super(file, followLinks);
        }

        @Override
        public void setTimes(FileTime lastModifiedTime,
                             FileTime lastAccessTime,
                             FileTime createTime) throws IOException
        {
            BsdFileAttributeViews.setTimes(file, lastModifiedTime,
                                           lastAccessTime, createTime,
                                           followLinks);
        }
    }

    static Basic createBasicView(UnixPath file, boolean followLinks) {
        return new Basic(file, followLinks);
    }

    static Posix createPosixView(UnixPath file, boolean followLinks) {
        return new Posix(file, followLinks);
    }

    static Unix createUnixView(UnixPath file, boolean followLinks) {
        return new Unix(file, followLinks);
    }
}
