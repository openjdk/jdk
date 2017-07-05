/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.*;
import sun.misc.Unsafe;

import static sun.nio.fs.UnixConstants.*;
import static sun.nio.fs.LinuxNativeDispatcher.*;

/**
 * Linux implementation of UserDefinedFileAttributeView using extended attributes.
 */

class LinuxUserDefinedFileAttributeView
    extends AbstractUserDefinedFileAttributeView
{
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    // namespace for extended user attributes
    private static final String USER_NAMESPACE = "user.";

    // maximum bytes in extended attribute name (includes namespace)
    private static final int XATTR_NAME_MAX = 255;

    private byte[] nameAsBytes(UnixPath file, String name) throws IOException {
        if (name == null)
            throw new NullPointerException("'name' is null");
        name = USER_NAMESPACE + name;
        byte[] bytes = name.getBytes();
        if (bytes.length > XATTR_NAME_MAX) {
            throw new FileSystemException(file.getPathForExceptionMessage(),
                null, "'" + name + "' is too big");
        }
        return bytes;
    }

    // Parses buffer as array of NULL-terminated C strings.
    private List<String> asList(long address, int size) {
        List<String> list = new ArrayList<>();
        int start = 0;
        int pos = 0;
        while (pos < size) {
            if (unsafe.getByte(address + pos) == 0) {
                int len = pos - start;
                byte[] value = new byte[len];
                unsafe.copyMemory(null, address+start, value,
                    Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
                String s = new String(value);
                if (s.startsWith(USER_NAMESPACE)) {
                    s = s.substring(USER_NAMESPACE.length());
                    list.add(s);
                }
                start = pos + 1;
            }
            pos++;
        }
        return list;
    }

    private final UnixPath file;
    private final boolean followLinks;

    LinuxUserDefinedFileAttributeView(UnixPath file, boolean followLinks) {
        this.file = file;
        this.followLinks = followLinks;
    }

    @Override
    public List<String> list() throws IOException  {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), true, false);

        int fd = file.openForAttributeAccess(followLinks);
        NativeBuffer buffer = null;
        try {
            int size = 1024;
            buffer = NativeBuffers.getNativeBuffer(size);
            for (;;) {
                try {
                    int n = flistxattr(fd, buffer.address(), size);
                    List<String> list = asList(buffer.address(), n);
                    return Collections.unmodifiableList(list);
                } catch (UnixException x) {
                    // allocate larger buffer if required
                    if (x.errno() == ERANGE && size < 32*1024) {
                        buffer.release();
                        size *= 2;
                        buffer = null;
                        buffer = NativeBuffers.getNativeBuffer(size);
                        continue;
                    }
                    throw new FileSystemException(file.getPathForExceptionMessage(),
                        null, "Unable to get list of extended attributes: " +
                        x.getMessage());
                }
            }
        } finally {
            if (buffer != null)
                buffer.release();
            close(fd);
        }
    }

    @Override
    public int size(String name) throws IOException  {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), true, false);

        int fd = file.openForAttributeAccess(followLinks);
        try {
            // fgetxattr returns size if called with size==0
            return fgetxattr(fd, nameAsBytes(file,name), 0L, 0);
        } catch (UnixException x) {
            throw new FileSystemException(file.getPathForExceptionMessage(),
                null, "Unable to get size of extended attribute '" + name +
                "': " + x.getMessage());
        } finally {
            close(fd);
        }
    }

    @Override
    public int read(String name, ByteBuffer dst) throws IOException {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), true, false);

        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        int pos = dst.position();
        int lim = dst.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        NativeBuffer nb;
        long address;
        if (dst instanceof sun.nio.ch.DirectBuffer) {
            nb = null;
            address = ((sun.nio.ch.DirectBuffer)dst).address() + pos;
        } else {
            // substitute with native buffer
            nb = NativeBuffers.getNativeBuffer(rem);
            address = nb.address();
        }

        int fd = file.openForAttributeAccess(followLinks);
        try {
            try {
                int n = fgetxattr(fd, nameAsBytes(file,name), address, rem);

                // if remaining is zero then fgetxattr returns the size
                if (rem == 0) {
                    if (n > 0)
                        throw new UnixException(ERANGE);
                    return 0;
                }

                // copy from buffer into backing array if necessary
                if (nb != null) {
                    int off = dst.arrayOffset() + pos + Unsafe.ARRAY_BYTE_BASE_OFFSET;
                    unsafe.copyMemory(null, address, dst.array(), off, n);
                }
                dst.position(pos + n);
                return n;
            } catch (UnixException x) {
                String msg = (x.errno() == ERANGE) ?
                    "Insufficient space in buffer" : x.getMessage();
                throw new FileSystemException(file.getPathForExceptionMessage(),
                    null, "Error reading extended attribute '" + name + "': " + msg);
            } finally {
                close(fd);
            }
        } finally {
            if (nb != null)
                nb.release();
        }
    }

    @Override
    public int write(String name, ByteBuffer src) throws IOException {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), false, true);

        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        NativeBuffer nb;
        long address;
        if (src instanceof sun.nio.ch.DirectBuffer) {
            nb = null;
            address = ((sun.nio.ch.DirectBuffer)src).address() + pos;
        } else {
            // substitute with native buffer
            nb = NativeBuffers.getNativeBuffer(rem);
            address = nb.address();

            if (src.hasArray()) {
                // copy from backing array into buffer
                int off = src.arrayOffset() + pos + Unsafe.ARRAY_BYTE_BASE_OFFSET;
                unsafe.copyMemory(src.array(), off, null, address, rem);
            } else {
                // backing array not accessible so transfer via temporary array
                byte[] tmp = new byte[rem];
                src.get(tmp);
                src.position(pos);  // reset position as write may fail
                unsafe.copyMemory(tmp, Unsafe.ARRAY_BYTE_BASE_OFFSET, null,
                    address, rem);
            }
        }

        int fd = file.openForAttributeAccess(followLinks);
        try {
            try {
                fsetxattr(fd, nameAsBytes(file,name), address, rem);
                src.position(pos + rem);
                return rem;
            } catch (UnixException x) {
                throw new FileSystemException(file.getPathForExceptionMessage(),
                    null, "Error writing extended attribute '" + name + "': " +
                    x.getMessage());
            } finally {
                close(fd);
            }
        } finally {
            if (nb != null)
                nb.release();
        }
    }

    @Override
    public void delete(String name) throws IOException {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), false, true);

        int fd = file.openForAttributeAccess(followLinks);
        try {
            fremovexattr(fd, nameAsBytes(file,name));
        } catch (UnixException x) {
            throw new FileSystemException(file.getPathForExceptionMessage(),
                null, "Unable to delete extended attribute '" + name + "': " + x.getMessage());
        } finally {
            close(fd);
        }
    }

    /**
     * Used by copyTo/moveTo to copy extended attributes from source to target.
     *
     * @param   ofd
     *          file descriptor for source file
     * @param   nfd
     *          file descriptor for target file
     */
    static void copyExtendedAttributes(int ofd, int nfd) {
        NativeBuffer buffer = null;
        try {

            // call flistxattr to get list of extended attributes.
            int size = 1024;
            buffer = NativeBuffers.getNativeBuffer(size);
            for (;;) {
                try {
                    size = flistxattr(ofd, buffer.address(), size);
                    break;
                } catch (UnixException x) {
                    // allocate larger buffer if required
                    if (x.errno() == ERANGE && size < 32*1024) {
                        buffer.release();
                        size *= 2;
                        buffer = null;
                        buffer = NativeBuffers.getNativeBuffer(size);
                        continue;
                    }

                    // unable to get list of attributes
                    return;
                }
            }

            // parse buffer as array of NULL-terminated C strings.
            long address = buffer.address();
            int start = 0;
            int pos = 0;
            while (pos < size) {
                if (unsafe.getByte(address + pos) == 0) {
                    // extract attribute name and copy attribute to target.
                    // FIXME: We can avoid needless copying by using address+pos
                    // as the address of the name.
                    int len = pos - start;
                    byte[] name = new byte[len];
                    unsafe.copyMemory(null, address+start, name,
                        Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
                    try {
                        copyExtendedAttribute(ofd, name, nfd);
                    } catch (UnixException ignore) {
                        // ignore
                    }
                    start = pos + 1;
                }
                pos++;
            }

        } finally {
            if (buffer != null)
                buffer.release();
        }
    }

    private static void copyExtendedAttribute(int ofd, byte[] name, int nfd)
        throws UnixException
    {
        int size = fgetxattr(ofd, name, 0L, 0);
        NativeBuffer buffer = NativeBuffers.getNativeBuffer(size);
        try {
            long address = buffer.address();
            size = fgetxattr(ofd, name, address, size);
            fsetxattr(nfd, name, address, size);
        } finally {
            buffer.release();
        }
    }
}
