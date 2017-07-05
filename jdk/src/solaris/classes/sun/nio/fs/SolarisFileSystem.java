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
import static sun.nio.fs.UnixNativeDispatcher.*;

/**
 * Solaris implementation of FileSystem
 */

class SolarisFileSystem extends UnixFileSystem {
    private final boolean hasSolaris11Features;

    SolarisFileSystem(UnixFileSystemProvider provider, String dir) {
        super(provider, dir);

        // check os.version
        String osversion = AccessController
            .doPrivileged(new GetPropertyAction("os.version"));
        String[] vers = Util.split(osversion, '.');
        assert vers.length >= 2;
        int majorVersion = Integer.parseInt(vers[0]);
        int minorVersion = Integer.parseInt(vers[1]);
        this.hasSolaris11Features =
            (majorVersion > 5 || (majorVersion == 5 && minorVersion >= 11));
    }

    @Override
    boolean isSolaris() {
        return true;
    }

    @Override
    public WatchService newWatchService()
        throws IOException
    {
        // FEN available since Solaris 11
        if (hasSolaris11Features) {
            return new SolarisWatchService(this);
        } else {
            return new PollingWatchService();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V newFileAttributeView(Class<V> view,
                                                                UnixPath file, LinkOption... options)
    {
        if (view == AclFileAttributeView.class)
            return (V) new SolarisAclFileAttributeView(file, followLinks(options));
        if (view == UserDefinedFileAttributeView.class) {
            return(V) new SolarisUserDefinedFileAttributeView(file, followLinks(options));
        }
        return super.newFileAttributeView(view, file, options);
    }

    @Override
    protected DynamicFileAttributeView newFileAttributeView(String name,
                                                            UnixPath file,
                                                            LinkOption... options)
    {
        if (name.equals("acl"))
            return new SolarisAclFileAttributeView(file, followLinks(options));
        if (name.equals("user"))
            return new SolarisUserDefinedFileAttributeView(file, followLinks(options));
        return super.newFileAttributeView(name, file, options);
    }

    // lazy initialization of the list of supported attribute views
    private static class SupportedFileFileAttributeViewsHolder {
        static final Set<String> supportedFileAttributeViews =
            supportedFileAttributeViews();
        private static Set<String> supportedFileAttributeViews() {
            Set<String> result = new HashSet<String>();
            result.addAll(UnixFileSystem.standardFileAttributeViews());
            // additional Solaris-specific views
            result.add("acl");
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
        SolarisUserDefinedFileAttributeView.copyExtendedAttributes(ofd, nfd);
        // TDB: copy ACL from source to target
    }

    /**
     * Returns object to iterate over entries in /etc/mnttab
     */
    @Override
    Iterable<UnixMountEntry> getMountEntries() {
        ArrayList<UnixMountEntry> entries = new ArrayList<UnixMountEntry>();
        try {
            UnixPath mnttab = new UnixPath(this, "/etc/mnttab");
            long fp = fopen(mnttab, "r");
            try {
                for (;;) {
                    UnixMountEntry entry = new UnixMountEntry();
                    int res = getextmntent(fp, entry);
                    if (res < 0)
                        break;
                    entries.add(entry);
                }
            } finally {
                fclose(fp);
            }
        } catch (UnixException x) {
            // nothing we can do
        }
        return entries;
    }

    @Override
    FileStore getFileStore(UnixPath path) throws IOException {
        return new SolarisFileStore(path);
    }

    @Override
    FileStore getFileStore(UnixMountEntry entry) throws IOException {
        return new SolarisFileStore(this, entry);
    }
}
