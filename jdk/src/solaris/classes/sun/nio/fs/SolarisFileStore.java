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

import java.io.IOException;

import static sun.nio.fs.UnixNativeDispatcher.*;
import static sun.nio.fs.SolarisConstants.*;

/**
 * Solaris implementation of FileStore
 */

class SolarisFileStore
    extends UnixFileStore
{
    private final boolean xattrEnabled;

    SolarisFileStore(UnixPath file) throws IOException {
        super(file);
        this.xattrEnabled = xattrEnabled();
    }

    SolarisFileStore(UnixFileSystem fs, UnixMountEntry entry) throws IOException {
        super(fs, entry);
        this.xattrEnabled = xattrEnabled();
    }

    // returns true if extended attributes enabled
    private boolean xattrEnabled() {
        long res = 0L;
        try {
            res = pathconf(file(), _PC_XATTR_ENABLED);
        } catch (UnixException x) {
            // ignore
        }
        return (res != 0L);
    }

    @Override
    UnixMountEntry findMountEntry() throws IOException {
        // On Solaris iterate over the entries in the mount table to find device
        for (UnixMountEntry entry: file().getFileSystem().getMountEntries()) {
            if (entry.dev() == dev()) {
                return entry;
            }
        }
        throw new IOException("Device not found in mnttab");
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        if (name.equals("acl")) {
            // lookup fstypes.properties
            FeatureStatus status = checkIfFeaturePresent("nfsv4acl");
            if (status == FeatureStatus.PRESENT)
                return true;
            if (status == FeatureStatus.NOT_PRESENT)
                return false;
            // AclFileAttributeView available on ZFS
            return (type().equals("zfs"));
        }
        if (name.equals("user")) {
            // lookup fstypes.properties
            FeatureStatus status = checkIfFeaturePresent("xattr");
            if (status == FeatureStatus.PRESENT)
                return true;
            if (status == FeatureStatus.NOT_PRESENT)
                return false;
            return xattrEnabled;
        }

        return super.supportsFileAttributeView(name);
    }

    @Override
    boolean isLoopback() {
        return type().equals("lofs");
    }
}
