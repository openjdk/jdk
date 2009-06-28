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

/**
 * Represents an entry in the mount table.
 */

class UnixMountEntry {
    private byte[] name;        // file system name
    private byte[] dir;         // directory (mount point)
    private byte[] fstype;      // ufs, nfs, ...
    private byte[] opts;        // mount options
    private long dev;           // device ID

    private volatile String fstypeAsString;
    private volatile String optionsAsString;

    UnixMountEntry() {
    }

    String name() {
        return new String(name);
    }

    String fstype() {
        if (fstypeAsString == null)
            fstypeAsString = new String(fstype);
        return fstypeAsString;
    }

    byte[] dir() {
        return dir;
    }

    long dev() {
        return dev;
    }

    /**
     * Tells whether the mount entry has the given option.
     */
    boolean hasOption(String requested) {
        if (optionsAsString == null)
            optionsAsString = new String(opts);
        for (String opt: Util.split(optionsAsString, ',')) {
            if (opt.equals(requested))
                return true;
        }
        return false;
    }

    // generic option
    boolean isIgnored() {
        return hasOption("ignore");
    }

    // generic option
    boolean isReadOnly() {
        return hasOption("ro");
    }
}
