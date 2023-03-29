/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.util.Properties;
import jdk.internal.misc.Blocker;
import jdk.internal.util.StaticProperty;
import sun.security.action.GetPropertyAction;

final class UnixFileSystem extends FileSystem {

    private final char slash;
    private final char colon;
    private final String userDir;

    UnixFileSystem() {
        Properties props = GetPropertyAction.privilegedGetProperties();
        slash = props.getProperty("file.separator").charAt(0);
        colon = props.getProperty("path.separator").charAt(0);
        userDir = StaticProperty.userDir();
    }

    /* -- Normalization and construction -- */

    @Override
    public char getSeparator() {
        return slash;
    }

    @Override
    public char getPathSeparator() {
        return colon;
    }

    /* A normal Unix pathname contains no duplicate slashes and does not end
       with a slash.  It may be the empty string. */

    /**
     * Normalize the given pathname, starting at the given
     * offset; everything before off is already normal, and there's at least
     * one duplicate or trailing slash to be removed
     */
    private String normalize(String pathname, int off) {
        int n = pathname.length();
        while ((n > off) && (pathname.charAt(n - 1) == '/')) n--;
        if (n == 0) return "/";
        if (n == off) return pathname.substring(0, off);

        StringBuilder sb = new StringBuilder(n);
        if (off > 0) sb.append(pathname, 0, off);
        char prevChar = 0;
        for (int i = off; i < n; i++) {
            char c = pathname.charAt(i);
            if ((prevChar == '/') && (c == '/')) continue;
            sb.append(c);
            prevChar = c;
        }
        return sb.toString();
    }

    /* Check that the given pathname is normal.  If not, invoke the real
       normalizer on the part of the pathname that requires normalization.
       This way we iterate through the whole pathname string only once. */
    @Override
    public String normalize(String pathname) {
        int doubleSlash = pathname.indexOf("//");
        if (doubleSlash >= 0) {
            return normalize(pathname, doubleSlash);
        }
        if (pathname.endsWith("/")) {
            return normalize(pathname, pathname.length() - 1);
        }
        return pathname;
    }

    @Override
    public int prefixLength(String pathname) {
        return pathname.startsWith("/") ? 1 : 0;
    }

    @Override
    public String resolve(String parent, String child) {
        if (child.isEmpty()) return parent;
        if (child.charAt(0) == '/') {
            if (parent.equals("/")) return child;
            return parent + child;
        }
        if (parent.equals("/")) return parent + child;
        return parent + '/' + child;
    }

    @Override
    public String getDefaultParent() {
        return "/";
    }

    @Override
    public String fromURIPath(String path) {
        String p = path;
        if (p.endsWith("/") && (p.length() > 1)) {
            // "/foo/" --> "/foo", but "/" --> "/"
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }


    /* -- Path operations -- */

    @Override
    public boolean isAbsolute(File f) {
        return (f.getPrefixLength() != 0);
    }

    @Override
    public boolean isInvalid(File f) {
        return f.getPath().indexOf('\u0000') >= 0;
    }

    @Override
    public String resolve(File f) {
        if (isAbsolute(f)) return f.getPath();
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPropertyAccess("user.dir");
        }
        return resolve(userDir, f.getPath());
    }

    @Override
    public String canonicalize(String path) throws IOException {
        long comp = Blocker.begin();
        try {
            return canonicalize0(path);
        } finally {
            Blocker.end(comp);
        }
    }
    private native String canonicalize0(String path) throws IOException;

    /* -- Attribute accessors -- */

    private native int getBooleanAttributes0(File f);

    @Override
    public int getBooleanAttributes(File f) {
        int rv;
        long comp = Blocker.begin();
        try {
            rv = getBooleanAttributes0(f);
        } finally {
            Blocker.end(comp);
        }
        return rv | isHidden(f);
    }

    @Override
    public boolean hasBooleanAttributes(File f, int attributes) {
        int rv;
        long comp = Blocker.begin();
        try {
            rv = getBooleanAttributes0(f);
        } finally {
            Blocker.end(comp);
        }
        if ((attributes & BA_HIDDEN) != 0) {
            rv |= isHidden(f);
        }
        return (rv & attributes) == attributes;
    }

    private static int isHidden(File f) {
        return f.getName().startsWith(".") ? BA_HIDDEN : 0;
    }

    @Override
    public boolean checkAccess(File f, int access) {
        long comp = Blocker.begin();
        try {
            return checkAccess0(f, access);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean checkAccess0(File f, int access);

    @Override
    public long getLastModifiedTime(File f) {
        long comp = Blocker.begin();
        try {
            return getLastModifiedTime0(f);
        } finally {
            Blocker.end(comp);
        }
    }
    private native long getLastModifiedTime0(File f);

    @Override
    public long getLength(File f) {
        long comp = Blocker.begin();
        try {
            return getLength0(f);
        } finally {
            Blocker.end(comp);
        }
    }
    private native long getLength0(File f);

    @Override
    public boolean setPermission(File f, int access, boolean enable, boolean owneronly) {
        long comp = Blocker.begin();
        try {
            return setPermission0(f, access, enable, owneronly);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean setPermission0(File f, int access, boolean enable, boolean owneronly);

    /* -- File operations -- */

    @Override
    public boolean createFileExclusively(String path) throws IOException {
        long comp = Blocker.begin();
        try {
            return createFileExclusively0(path);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean createFileExclusively0(String path) throws IOException;

    @Override
    public boolean delete(File f) {
        long comp = Blocker.begin();
        try {
            return delete0(f);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean delete0(File f);

    @Override
    public String[] list(File f) {
        long comp = Blocker.begin();
        try {
            return list0(f);
        } finally {
            Blocker.end(comp);
        }
    }
    private native String[] list0(File f);

    @Override
    public boolean createDirectory(File f) {
        long comp = Blocker.begin();
        try {
            return createDirectory0(f);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean createDirectory0(File f);

    @Override
    public boolean rename(File f1, File f2) {
        long comp = Blocker.begin();
        try {
            return rename0(f1, f2);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean rename0(File f1, File f2);

    @Override
    public boolean setLastModifiedTime(File f, long time) {
        long comp = Blocker.begin();
        try {
            return setLastModifiedTime0(f, time);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean setLastModifiedTime0(File f, long time);

    @Override
    public boolean setReadOnly(File f) {
        long comp = Blocker.begin();
        try {
            return setReadOnly0(f);
        } finally {
            Blocker.end(comp);
        }
    }
    private native boolean setReadOnly0(File f);

    /* -- Filesystem interface -- */

    @Override
    public File[] listRoots() {
        try {
            @SuppressWarnings("removal")
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkRead("/");
            }
            return new File[] { new File("/") };
        } catch (SecurityException x) {
            return new File[0];
        }
    }

    /* -- Disk usage -- */

    @Override
    public long getSpace(File f, int t) {
        long comp = Blocker.begin();
        try {
            return getSpace0(f, t);
        } finally {
            Blocker.end(comp);
        }
    }
    private native long getSpace0(File f, int t);

    /* -- Basic infrastructure -- */

    private native long getNameMax0(String path);

    @Override
    public int getNameMax(String path) {
        long nameMax = getNameMax0(path);
        if (nameMax > Integer.MAX_VALUE) {
            nameMax = Integer.MAX_VALUE;
        }
        return (int)nameMax;
    }

    @Override
    public int compare(File f1, File f2) {
        return f1.getPath().compareTo(f2.getPath());
    }

    @Override
    public int hashCode(File f) {
        return f.getPath().hashCode() ^ 1234321;
    }

    private static native void initIDs();

    static {
        initIDs();
    }
}
