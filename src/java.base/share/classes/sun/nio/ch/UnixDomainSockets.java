/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.NetPermission;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.nio.fs.AbstractFileSystemProvider;

class UnixDomainSockets {

    static {
        // Load all required native libs
        IOUtil.load();
    }

    static void init() {}

    static final UnixDomainSocketAddress UNNAMED
        = UnixDomainSocketAddress.of("");;

    private static final boolean supported =
        socketSupported();

    static {
        PrivilegedAction<Void> pa = () -> {
            // -1 if unsupported or +ve integer otherwise. Prop set after Net initialization
            // Undocumented. Just use for testing
            System.setProperty("jdk.nio.channels.unixdomain.maxnamelength",
                               Integer.toString(maxNameLen()));
            return null;
        };
        AccessController.doPrivileged(pa);
    }

    private static final NetPermission np = new NetPermission("accessUnixDomainSocket");

    static void checkCapability() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null)
            return;
        sm.checkPermission(np);
    }

    static UnixDomainSocketAddress getRevealedLocalAddress(UnixDomainSocketAddress addr) {
        try{
            checkCapability();
            // Security check passed
        } catch (SecurityException e) {
            // Return unnamed address only if security check fails
            addr = UNNAMED;
        }
        return addr;
    }

    public static UnixDomainSocketAddress localAddress(FileDescriptor fd) throws IOException {
        byte[] bytes = localAddress0(fd);
        return UnixDomainSocketAddress.of(
                    new String(bytes, UnixDomainSocketsUtil.getCharset()));
    }

    static native byte[] localAddress0(FileDescriptor fd)
        throws IOException;

    public static UnixDomainSocketAddress remoteAddress(FileDescriptor fd) throws IOException {
        byte[] bytes = remoteAddress0(fd);
        return UnixDomainSocketAddress.of(new String(bytes,
                    UnixDomainSocketsUtil.getCharset()));
    }

    static native byte[] remoteAddress0(FileDescriptor fd)
        throws IOException;

    static String getRevealedLocalAddressAsString(UnixDomainSocketAddress addr) {
        return System.getSecurityManager() == null ? addr.toString() : "";
    }

    public static UnixDomainSocketAddress checkAddress(SocketAddress sa) {
        if (sa == null)
            return null;
        if (!(sa instanceof UnixDomainSocketAddress))
            throw new UnsupportedAddressTypeException();
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        return usa;
    }

    public static boolean isSupported() {
        return supported;
    }

    public static int maxNameLen() {
        return supported ? maxNameLen0() : -1;
    }

    public static boolean inTempDirectory(Path path) {
        Path parent = AccessController.doPrivileged(
            (PrivilegedAction<Path>) () -> {
                return path
                    .normalize()
                    .toAbsolutePath()
                    .getParent();
            }
        );
        return parent.equals(tempDir);
    }

    static final Path tempDir = UnixDomainSocketsUtil.getTempDir();

    static byte[] getPathBytes(Path path) throws IOException {
        AbstractFileSystemProvider provider = (AbstractFileSystemProvider)
            FileSystems.getDefault().provider();
        return provider.getSunPathForSocketFile(path);
    }

    public static FileDescriptor socket() throws IOException {
        return IOUtil.newFD(socket0());
    }

    public static void bind(FileDescriptor fd, Path addr) throws IOException {
        byte[] path = getPathBytes(addr);
        bind0(fd, path);
    }

    public static int connect(FileDescriptor fd, Path addr) throws IOException {
        byte[] path = getPathBytes(addr);
        return connect0(fd, path);
    }

    static int accept(FileDescriptor fd, FileDescriptor newfd, String[] isaa)
        throws IOException
    {
        Object[] barray  = new Object[1];
        int ret = accept0(fd, newfd, barray);
        byte[] bytes = (byte[])barray[0];
        isaa[0] = bytes == null ? null : new String(bytes, UnixDomainSocketsUtil.getCharset());
        return ret;
    }

    private static native int socket0();

    private static native boolean socketSupported();

    private static native void bind0(FileDescriptor fd, byte[] path)
        throws IOException;

    private static native int connect0(FileDescriptor fd, byte[] path)
        throws IOException;

    static native int accept0(FileDescriptor fd, FileDescriptor newfd, Object[] isaa)
        throws IOException;

    static native int maxNameLen0();

}
