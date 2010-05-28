/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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

/*
 */

package java.io;

/**
 * Unicode-aware FileSystem for Windows NT/2000.
 *
 * @author Konstantin Kladko
 * @since 1.4
 */
class WinNTFileSystem extends Win32FileSystem {

    protected native String canonicalize0(String path)
                                                throws IOException;
    protected native String canonicalizeWithPrefix0(String canonicalPrefix,
                                                    String pathWithCanonicalPrefix)
                                                throws IOException;

    /* -- Attribute accessors -- */

    public native int getBooleanAttributes(File f);
    public native boolean checkAccess(File f, int access);
    public native long getLastModifiedTime(File f);
    public native long getLength(File f);
    public native boolean setPermission(File f, int access, boolean enable, boolean owneronly);


    public long getSpace(File f, int t) {
        if (f.exists()) {
            return getSpace0(f, t);
        }
        return 0;
    }

    private native long getSpace0(File f, int t);

    /* -- File operations -- */

    public native boolean createFileExclusively(String path)
                                               throws IOException;
    protected native boolean delete0(File f);
    public native String[] list(File f);
    public native boolean createDirectory(File f);
    protected native boolean rename0(File f1, File f2);
    public native boolean setLastModifiedTime(File f, long time);
    public native boolean setReadOnly(File f);
    protected native String getDriveDirectory(int drive);
    private static native void initIDs();

    static {
            initIDs();
    }
}
