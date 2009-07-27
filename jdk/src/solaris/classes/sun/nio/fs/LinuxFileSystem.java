/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;
import java.security.AccessController;
import sun.security.action.GetPropertyAction;
import static sun.nio.fs.LinuxNativeDispatcher.*;

/**
 * Linux implementation of FileSystem
 */

class LinuxFileSystem extends UnixFileSystem {
    private final boolean hasInotify;

    LinuxFileSystem(UnixFileSystemProvider provider, String dir) {
        super(provider, dir);

        // assume X.Y[-Z] format
        String osversion = AccessController
            .doPrivileged(new GetPropertyAction("os.version"));
        String[] vers = Util.split(osversion, '.');
        assert vers.length >= 2;

        int majorVersion = Integer.parseInt(vers[0]);
        int minorVersion = Integer.parseInt(vers[1]);
        int microVersion = 0;
        if (vers.length > 2) {
            String[] microVers = Util.split(vers[2], '-');
            microVersion = (microVers.length > 0) ?
                Integer.parseInt(microVers[0]) : 0;
        }

        // inotify available since 2.6.13
        this.hasInotify = ((majorVersion > 2) ||
            (majorVersion == 2 && minorVersion > 6) ||
            ((majorVersion == 2) && (minorVersion == 6) && (microVersion >= 13)));
    }

    @Override
    public WatchService newWatchService()
        throws IOException
    {
        if (hasInotify) {
            return new LinuxWatchService(this);
        } else {
            // use polling implementation on older kernels
            return new PollingWatchService();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V newFileAttributeView(Class<V> view,
                                                                UnixPath file,
                                                                LinkOption... options)
    {
        if (view == DosFileAttributeView.class)
            return (V) new LinuxDosFileAttributeView(file, followLinks(options));
        if (view == UserDefinedFileAttributeView.class)
            return (V) new LinuxUserDefinedFileAttributeView(file, followLinks(options));
        return super.newFileAttributeView(view, file, options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DynamicFileAttributeView newFileAttributeView(String name,
                                                         UnixPath file,
                                                         LinkOption... options)
    {
        if (name.equals("dos"))
            return new LinuxDosFileAttributeView(file, followLinks(options));
        if (name.equals("user"))
            return new LinuxUserDefinedFileAttributeView(file, followLinks(options));
        return super.newFileAttributeView(name, file, options);
    }

    // lazy initialization of the list of supported attribute views
    private static class SupportedFileFileAttributeViewsHolder {
        static final Set<String> supportedFileAttributeViews =
            supportedFileAttributeViews();
        private static Set<String> supportedFileAttributeViews() {
            Set<String> result = new HashSet<String>();
            result.addAll(UnixFileSystem.standardFileAttributeViews());
            // additional Linux-specific views
            result.add("dos");
            result.add("user");
            return Collections.unmodifiableSet(result);
        }
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SupportedFileFileAttributeViewsHolder.supportedFileAttributeViews;
    }

    @Override
    void copyNonPosixAttributes(int ofd, int nfd) {
        LinuxUserDefinedFileAttributeView.copyExtendedAttributes(ofd, nfd);
    }

    /**
     * Returns object to iterate over the mount entries in the given fstab file.
     */
    Iterable<UnixMountEntry> getMountEntries(String fstab) {
        ArrayList<UnixMountEntry> entries = new ArrayList<UnixMountEntry>();
        try {
            long fp = setmntent(fstab.getBytes(), "r".getBytes());
            try {
                for (;;) {
                    UnixMountEntry entry = new UnixMountEntry();
                    int res = getextmntent(fp, entry);
                    if (res < 0)
                        break;
                    entries.add(entry);
                }
            } finally {
                endmntent(fp);
            }

        } catch (UnixException x) {
            // nothing we can do
        }
        return entries;
    }

    /**
     * Returns object to iterate over the mount entries in /etc/mtab
     */
    @Override
    Iterable<UnixMountEntry> getMountEntries() {
        return getMountEntries("/etc/mtab");
    }

    @Override
    FileStore getFileStore(UnixPath path) throws IOException {
        return new LinuxFileStore(path);
    }

    @Override
    FileStore getFileStore(UnixMountEntry entry) throws IOException {
        return new LinuxFileStore(this, entry);
    }
}
