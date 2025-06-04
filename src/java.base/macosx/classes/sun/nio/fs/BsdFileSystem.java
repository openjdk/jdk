/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileStore;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import sun.nio.ch.IOStatus;

import static sun.nio.fs.UnixConstants.*;
import static sun.nio.fs.UnixNativeDispatcher.chown;
import static sun.nio.fs.UnixNativeDispatcher.unlink;

/**
 * Bsd implementation of FileSystem
 */

class BsdFileSystem extends UnixFileSystem {

    BsdFileSystem(UnixFileSystemProvider provider, String dir) {
        super(provider, dir);
    }

    @Override
    public WatchService newWatchService()
        throws IOException
    {
        // use polling implementation until we implement a BSD/kqueue one
        return new PollingWatchService();
    }

    // lazy initialization of the list of supported attribute views
    private static class SupportedFileFileAttributeViewsHolder {
        static final Set<String> supportedFileAttributeViews =
            supportedFileAttributeViews();
        private static Set<String> supportedFileAttributeViews() {
            Set<String> result = new HashSet<String>();
            result.addAll(standardFileAttributeViews());
            // additional BSD-specific views
            result.add("user");
            return Collections.unmodifiableSet(result);
        }
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SupportedFileFileAttributeViewsHolder.supportedFileAttributeViews;
    }

    /**
     * Clones the file whose path name is {@code src} to that whose path
     * name is {@code dst} using the {@code clonefile} system call.
     *
     * @param src the path of the source file
     * @param dst the path of the destination file (clone)
     * @param followLinks whether to follow links
     *
     * @return 0 on success, or IOStatus.UNSUPPORTED_CASE if the call
     *         does not work with the given parameters
     */
    private int clone(UnixPath src, UnixPath dst, boolean followLinks)
        throws IOException
    {
        int flags = followLinks ? 0 : CLONE_NOFOLLOW;
        try {
            BsdNativeDispatcher.clonefile(src, dst, flags);
        } catch (UnixException x) {
            switch (x.errno()) {
                case ENOTSUP: // cloning not supported by filesystem
                case EXDEV:   // src and dst on different filesystems
                case ENOTDIR: // problematic path parameter(s)
                    return IOStatus.UNSUPPORTED_CASE;
                default:
                    x.rethrowAsIOException(src, dst);
                    return IOStatus.THROWN;
            }
        }

        return 0;
    }

    @Override
    protected int directCopy(int dst, int src, long addressToPollForCancel)
        throws UnixException
    {
        return directCopy0(dst, src, addressToPollForCancel);
    }

    @Override
    protected void copyFile(UnixPath source,
                            UnixFileAttributes attrs,
                            UnixPath target,
                            Flags flags,
                            long addressToPollForCancel)
        throws IOException
    {
        // Attempt to clone the source unless cancellation is not possible,
        // or attributes are not to be copied
        if (addressToPollForCancel == 0 && flags.copyPosixAttributes) {
            try {
                int res = clone(source, target, flags.followLinks);

                if (res == 0) {
                    // copy owner (not done by clonefile)
                    try {
                        chown(target, attrs.uid(), attrs.gid());
                    } catch (UnixException x) {
                        if (flags.failIfUnableToCopyPosix)
                            x.rethrowAsIOException(target);
                    }
                    return;
                }
            } catch (IOException e) {
                // clone or chown failed so roll back
                try {
                    unlink(target);
                } catch (UnixException ignore) { }

                throw e;
            }

            // fall through to superclass method
       }

        super.copyFile(source, attrs, target, flags, addressToPollForCancel);
    }

    @Override
    void copyNonPosixAttributes(int ofd, int nfd) {
        UnixUserDefinedFileAttributeView.copyExtendedAttributes(ofd, nfd);
    }

    /**
     * Returns object to iterate over mount entries
     */
    @Override
    Iterable<UnixMountEntry> getMountEntries() {
        ArrayList<UnixMountEntry> entries = new ArrayList<UnixMountEntry>();
        try {
            long iter = BsdNativeDispatcher.getfsstat();
            try {
                for (;;) {
                    UnixMountEntry entry = new UnixMountEntry();
                    int res = BsdNativeDispatcher.fsstatEntry(iter, entry);
                    if (res < 0)
                        break;
                    entries.add(entry);
                }
            } finally {
                BsdNativeDispatcher.endfsstat(iter);
            }

        } catch (UnixException x) {
            // nothing we can do
        }
        return entries;
    }

    @Override
    FileStore getFileStore(UnixMountEntry entry) throws IOException {
        return new BsdFileStore(this, entry);
    }

    // -- native methods --

    private static native int directCopy0(int dst, int src,
                                          long addressToPollForCancel)
        throws UnixException;
}
