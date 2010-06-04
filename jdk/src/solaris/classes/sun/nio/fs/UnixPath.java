/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;
import java.nio.channels.*;
import java.security.AccessController;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.lang.ref.SoftReference;
import sun.security.util.SecurityConstants;

import static sun.nio.fs.UnixNativeDispatcher.*;
import static sun.nio.fs.UnixConstants.*;

/**
 * Solaris/Linux implementation of java.nio.file.Path
 */

class UnixPath
    extends AbstractPath
{
    private static ThreadLocal<SoftReference<CharsetEncoder>> encoder =
        new ThreadLocal<SoftReference<CharsetEncoder>>();

    // FIXME - eliminate this reference to reduce space
    private final UnixFileSystem fs;

    // internal representation
    private final byte[] path;

    // String representation (created lazily)
    private volatile String stringValue;

    // cached hashcode (created lazily, no need to be volatile)
    private int hash;

    // array of offsets of elements in path (created lazily)
    private volatile int[] offsets;

    UnixPath(UnixFileSystem fs, byte[] path) {
        this.fs = fs;
        this.path = path;
    }

    UnixPath(UnixFileSystem fs, String input) {
        // removes redundant slashes and checks for invalid characters
        this(fs, encode(normalizeAndCheck(input)));
    }

    // package-private
    // removes redundant slashes and check input for invalid characters
    static String normalizeAndCheck(String input) {
        int n = input.length();
        if (n == 0)
            throw new InvalidPathException(input, "Path is empty");
        char prevChar = 0;
        for (int i=0; i < n; i++) {
            char c = input.charAt(i);
            if ((c == '/') && (prevChar == '/'))
                return normalize(input, n, i - 1);
            checkNotNul(input, c);
            prevChar = c;
        }
        if (prevChar == '/')
            return normalize(input, n, n - 1);
        return input;
    }

    private static void checkNotNul(String input, char c) {
        if (c == '\u0000')
            throw new InvalidPathException(input, "Nul character not allowed");
    }

    private static String normalize(String input, int len, int off) {
        if (len == 0)
            return input;
        int n = len;
        while ((n > 0) && (input.charAt(n - 1) == '/')) n--;
        if (n == 0)
            return "/";
        StringBuilder sb = new StringBuilder(input.length());
        if (off > 0)
            sb.append(input.substring(0, off));
        char prevChar = 0;
        for (int i=off; i < n; i++) {
            char c = input.charAt(i);
            if ((c == '/') && (prevChar == '/'))
                continue;
            checkNotNul(input, c);
            sb.append(c);
            prevChar = c;
        }
        return sb.toString();
    }

    // encodes the given path-string into a sequence of bytes
    private static byte[] encode(String input) {
        SoftReference<CharsetEncoder> ref = encoder.get();
        CharsetEncoder ce = (ref != null) ? ref.get() : null;
        if (ce == null) {
            ce = Charset.defaultCharset().newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            encoder.set(new SoftReference<CharsetEncoder>(ce));
        }

        char[] ca = input.toCharArray();

        // size output buffer for worse-case size
        byte[] ba = new byte[(int)(ca.length * (double)ce.maxBytesPerChar())];

        // encode
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(ca);
        ce.reset();
        CoderResult cr = ce.encode(cb, bb, true);
        boolean error;
        if (!cr.isUnderflow()) {
            error = true;
        } else {
            cr = ce.flush(bb);
            error = !cr.isUnderflow();
        }
        if (error) {
            throw new InvalidPathException(input,
                "Malformed input or input contains unmappable chacraters");
        }

        // trim result to actual length if required
        int len = bb.position();
        if (len != ba.length)
            ba = Arrays.copyOf(ba, len);

        return ba;
    }

    // package-private
    byte[] asByteArray() {
        return path;
    }

    // use this path when making system/library calls
    byte[] getByteArrayForSysCalls() {
        // resolve against default directory if required (chdir allowed or
        // file system default directory is not working directory)
        if (getFileSystem().needToResolveAgainstDefaultDirectory()) {
            return resolve(getFileSystem().defaultDirectory(), path);
        } else {
            return path;
        }
    }

    // use this message when throwing exceptions
    String getPathForExecptionMessage() {
        return toString();
    }

    // use this path for permission checks
    String getPathForPermissionCheck() {
        if (getFileSystem().needToResolveAgainstDefaultDirectory()) {
            return new String(getByteArrayForSysCalls());
        } else {
            return toString();
        }
    }

    // Checks that the given file is a UnixPath
    private UnixPath checkPath(FileRef obj) {
        if (obj == null)
            throw new NullPointerException();
        if (!(obj instanceof UnixPath))
            throw new ProviderMismatchException();
        return (UnixPath)obj;
    }

    // create offset list if not already created
    private void initOffsets() {
        if (offsets == null) {
            int count, index;

            // count names
            count = 0;
            index = 0;
            while (index < path.length) {
                byte c = path[index++];
                if (c != '/') {
                    count++;
                    while (index < path.length && path[index] != '/')
                        index++;
                }
            }

            // populate offsets
            int[] result = new int[count];
            count = 0;
            index = 0;
            while (index < path.length) {
                byte c = path[index];
                if (c == '/') {
                    index++;
                } else {
                    result[count++] = index++;
                    while (index < path.length && path[index] != '/')
                        index++;
                }
            }
            synchronized (this) {
                if (offsets == null)
                    offsets = result;
            }
        }
    }

    @Override
    public UnixFileSystem getFileSystem() {
        return fs;
    }

    @Override
    public UnixPath getRoot() {
        if (path[0] == '/') {
            return getFileSystem().rootDirectory();
        } else {
            return null;
        }
    }

    @Override
    public UnixPath getName() {
        initOffsets();

        int count = offsets.length;
        if (count == 0)
            return null;  // no elements so no name

        if (count == 1 && path[0] != '/')
            return this;

        int lastOffset = offsets[count-1];
        int len = path.length - lastOffset;
        byte[] result = new byte[len];
        System.arraycopy(path, lastOffset, result, 0, len);
        return new UnixPath(getFileSystem(), result);
    }

    @Override
    public UnixPath getParent() {
        initOffsets();

        int count = offsets.length;
        if (count == 0) {
            // no elements so no parent
            return null;
        }
        int len = offsets[count-1] - 1;
        if (len <= 0) {
            // parent is root only (may be null)
            return getRoot();
        }
        byte[] result = new byte[len];
        System.arraycopy(path, 0, result, 0, len);
        return new UnixPath(getFileSystem(), result);
    }

    @Override
    public int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public UnixPath getName(int index) {
        initOffsets();
        if (index < 0)
            throw new IllegalArgumentException();
        if (index >= offsets.length)
            throw new IllegalArgumentException();

        int begin = offsets[index];
        int len;
        if (index == (offsets.length-1)) {
            len = path.length - begin;
        } else {
            len = offsets[index+1] - begin - 1;
        }

        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new UnixPath(getFileSystem(), result);
    }

    @Override
    public UnixPath subpath(int beginIndex, int endIndex) {
        initOffsets();

        if (beginIndex < 0)
            throw new IllegalArgumentException();
        if (beginIndex >= offsets.length)
            throw new IllegalArgumentException();
        if (endIndex > offsets.length)
            throw new IllegalArgumentException();
        if (beginIndex >= endIndex) {
            throw new IllegalArgumentException();
        }

        // starting offset and length
        int begin = offsets[beginIndex];
        int len;
        if (endIndex == offsets.length) {
            len = path.length - begin;
        } else {
            len = offsets[endIndex] - begin - 1;
        }

        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new UnixPath(getFileSystem(), result);
    }

    @Override
    public boolean isAbsolute() {
        return (path[0] == '/');
    }

    // Resolve child against given base
    private static byte[] resolve(byte[] base, byte[] child) {
        if (child[0] == '/')
            return child;
        byte[] result;
        if (base.length == 1 && base[0] == '/') {
            result = new byte[child.length + 1];
            result[0] = '/';
            System.arraycopy(child, 0, result, 1, child.length);
        } else {
            result = new byte[base.length + 1 + child.length];
            System.arraycopy(base, 0, result, 0, base.length);
            result[base.length] = '/';
            System.arraycopy(child, 0, result,  base.length+1, child.length);
        }
        return result;
    }

    @Override
    public UnixPath resolve(Path obj) {
        if (obj == null)
            return this;
        byte[] other = checkPath(obj).path;
        if (other[0] == '/')
            return ((UnixPath)obj);
        byte[] result = resolve(path, other);
        return new UnixPath(getFileSystem(), result);
    }

    @Override
    public UnixPath resolve(String other) {
        return resolve(new UnixPath(getFileSystem(), other));
    }

    UnixPath resolve(byte[] other) {
        return resolve(new UnixPath(getFileSystem(), other));
    }

    @Override
    public UnixPath relativize(Path obj) {
        UnixPath other = checkPath(obj);
        if (other.equals(this))
            return null;

        // can only relativize paths of the same type
        if (this.isAbsolute() != other.isAbsolute())
            throw new IllegalArgumentException("'other' is different type of Path");

        int bn = this.getNameCount();
        int cn = other.getNameCount();

        // skip matching names
        int n = (bn > cn) ? cn : bn;
        int i = 0;
        while (i < n) {
            if (!this.getName(i).equals(other.getName(i)))
                break;
            i++;
        }

        int dotdots = bn - i;
        if (i < cn) {
            // remaining name components in other
            UnixPath remainder = other.subpath(i, cn);
            if (dotdots == 0)
                return remainder;

            // result is a  "../" for each remaining name in base
            // followed by the remaining names in other
            byte[] result = new byte[dotdots*3 + remainder.path.length];
            int pos = 0;
            while (dotdots > 0) {
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'/';
                dotdots--;
            }
            System.arraycopy(remainder.path, 0, result, pos, remainder.path.length);
            return new UnixPath(getFileSystem(), result);
        } else {
            // no remaining names in other so result is simply a sequence of ".."
            byte[] result = new byte[dotdots*3 - 1];
            int pos = 0;
            while (dotdots > 0) {
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'.';
                // no tailing slash at the end
                if (dotdots > 1)
                    result[pos++] = (byte)'/';
                dotdots--;
            }
            return new UnixPath(getFileSystem(), result);
        }
    }

    @Override
    public Path normalize() {
        final int count = getNameCount();
        if (count == 0)
            return this;

        boolean[] ignore = new boolean[count];      // true => ignore name
        int[] size = new int[count];                // length of name
        int remaining = count;                      // number of names remaining
        boolean hasDotDot = false;                  // has at least one ..
        boolean isAbsolute = path[0] == '/';

        // first pass:
        //   1. compute length of names
        //   2. mark all occurences of "." to ignore
        //   3. and look for any occurences of ".."
        for (int i=0; i<count; i++) {
            int begin = offsets[i];
            int len;
            if (i == (offsets.length-1)) {
                len = path.length - begin;
            } else {
                len = offsets[i+1] - begin - 1;
            }
            size[i] = len;

            if (path[begin] == '.') {
                if (len == 1) {
                    ignore[i] = true;  // ignore  "."
                    remaining--;
                }
                else {
                    if (path[begin+1] == '.')   // ".." found
                        hasDotDot = true;
                }
            }
        }

        // multiple passes to eliminate all occurences of name/..
        if (hasDotDot) {
            int prevRemaining;
            do {
                prevRemaining = remaining;
                int prevName = -1;
                for (int i=0; i<count; i++) {
                    if (ignore[i])
                        continue;

                    // not a ".."
                    if (size[i] != 2) {
                        prevName = i;
                        continue;
                    }

                    int begin = offsets[i];
                    if (path[begin] != '.' || path[begin+1] != '.') {
                        prevName = i;
                        continue;
                    }

                    // ".." found
                    if (prevName >= 0) {
                        // name/<ignored>/.. found so mark name and ".." to be
                        // ignored
                        ignore[prevName] = true;
                        ignore[i] = true;
                        remaining = remaining - 2;
                        prevName = -1;
                    } else {
                        // Case: /<ignored>/.. so mark ".." as ignored
                        if (isAbsolute) {
                            boolean hasPrevious = false;
                            for (int j=0; j<i; j++) {
                                if (!ignore[j]) {
                                    hasPrevious = true;
                                    break;
                                }
                            }
                            if (!hasPrevious) {
                                // all proceeding names are ignored
                                ignore[i] = true;
                                remaining--;
                            }
                        }
                    }
                }
            } while (prevRemaining > remaining);
        }

        // no redundant names
        if (remaining == count)
            return this;

        // corner case - all names removed
        if (remaining == 0) {
            return isAbsolute ? getFileSystem().rootDirectory() : null;
        }

        // compute length of result
        int len = remaining - 1;
        if (isAbsolute)
            len++;

        for (int i=0; i<count; i++) {
            if (!ignore[i])
                len += size[i];
        }
        byte[] result = new byte[len];

        // copy names into result
        int pos = 0;
        if (isAbsolute)
            result[pos++] = '/';
        for (int i=0; i<count; i++) {
            if (!ignore[i]) {
                System.arraycopy(path, offsets[i], result, pos, size[i]);
                pos += size[i];
                if (--remaining > 0) {
                    result[pos++] = '/';
                }
            }
        }
        return new UnixPath(getFileSystem(), result);
    }

    @Override
    public boolean startsWith(Path other) {
        UnixPath that = checkPath(other);

        // other path is longer
        if (that.path.length > path.length)
            return false;

        int thisOffsetCount = getNameCount();
        int thatOffsetCount = that.getNameCount();

        // other path has no name elements
        if (thatOffsetCount == 0 && this.isAbsolute())
            return true;

        // given path has more elements that this path
        if (thatOffsetCount > thisOffsetCount)
            return false;

        // same number of elements so must be exact match
        if ((thatOffsetCount == thisOffsetCount) &&
            (path.length != that.path.length)) {
            return false;
        }

        // check offsets of elements match
        for (int i=0; i<thatOffsetCount; i++) {
            Integer o1 = offsets[i];
            Integer o2 = that.offsets[i];
            if (!o1.equals(o2))
                return false;
        }

        // offsets match so need to compare bytes
        int i=0;
        while (i < that.path.length) {
            if (this.path[i] != that.path[i])
                return false;
            i++;
        }

        // final check that match is on name boundary
        if (i < path.length && this.path[i] != '/')
            return false;

        return true;
    }

    @Override
    public boolean endsWith(Path other) {
        UnixPath that = checkPath(other);

        int thisLen = path.length;
        int thatLen = that.path.length;

        // other path is longer
        if (thatLen > thisLen)
            return false;

        // other path is absolute so this path must be absolute
        if (that.isAbsolute() && !this.isAbsolute())
            return false;

        int thisOffsetCount = getNameCount();
        int thatOffsetCount = that.getNameCount();

        // given path has more elements that this path
        if (thatOffsetCount > thisOffsetCount) {
            return false;
        } else {
            // same number of elements
            if (thatOffsetCount == thisOffsetCount) {
                if (thisOffsetCount == 0)
                    return true;
                int expectedLen = thisLen;
                if (this.isAbsolute() && !that.isAbsolute())
                    expectedLen--;
                if (thatLen != expectedLen)
                    return false;
            } else {
                // this path has more elements so given path must be relative
                if (that.isAbsolute())
                    return false;
            }
        }

        // compare bytes
        int thisPos = offsets[thisOffsetCount - thatOffsetCount];
        int thatPos = that.offsets[0];
        if ((thatLen - thatPos) != (thisLen - thisPos))
            return false;
        while (thatPos < thatLen) {
            if (this.path[thisPos++] != that.path[thatPos++])
                return false;
        }

        return true;
    }

    @Override
    public int compareTo(Path other) {
        int len1 = path.length;
        int len2 = ((UnixPath) other).path.length;

        int n = Math.min(len1, len2);
        byte v1[] = path;
        byte v2[] = ((UnixPath) other).path;

        int k = 0;
        while (k < n) {
            int c1 = v1[k] & 0xff;
            int c2 = v2[k] & 0xff;
            if (c1 != c2) {
                return c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    }

    @Override
    public boolean equals(Object ob) {
        if ((ob != null) && (ob instanceof UnixPath)) {
            return compareTo((Path)ob) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        // OK if two or more threads compute hash
        int h = hash;
        if (h == 0) {
            for (int i = 0; i< path.length; i++) {
                h = 31*h + (path[i] & 0xff);
            }
            hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        // OK if two or more threads create a String
        if (stringValue == null)
            stringValue = new String(path);     // platform encoding
        return stringValue;
    }

    @Override
    public Iterator<Path> iterator() {
        initOffsets();
        return new Iterator<Path>() {
            int i = 0;
            @Override
            public boolean hasNext() {
                return (i < offsets.length);
            }
            @Override
            public Path next() {
                if (i < offsets.length) {
                    Path result = getName(i);
                    i++;
                    return result;
                } else {
                    throw new NoSuchElementException();
                }
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    // -- file operations --

    // package-private
    int openForAttributeAccess(boolean followLinks) throws IOException {
        int flags = O_RDONLY;
        if (!followLinks)
            flags |= O_NOFOLLOW;
        try {
            return open(this, flags, 0);
        } catch (UnixException x) {
            // HACK: EINVAL instead of ELOOP on Solaris 10 prior to u4 (see 6460380)
            if (getFileSystem().isSolaris() && x.errno() == EINVAL)
                x.setError(ELOOP);

            if (x.errno() == ELOOP)
                throw new FileSystemException(getPathForExecptionMessage(), null,
                    x.getMessage() + " or unable to access attributes of symbolic link");

            x.rethrowAsIOException(this);
            return -1; // keep compile happy
        }
    }


    void checkRead() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkRead(getPathForPermissionCheck());
    }

    void checkWrite() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkWrite(getPathForPermissionCheck());
    }

    void checkDelete() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkDelete(getPathForPermissionCheck());
    }

    @Override
    public FileStore getFileStore()
        throws IOException
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("getFileStoreAttributes"));
            checkRead();
        }
        return getFileSystem().getFileStore(this);
    }

    @Override
    public void checkAccess(AccessMode... modes) throws IOException {
        boolean e = false;
        boolean r = false;
        boolean w = false;
        boolean x = false;

        if (modes.length == 0) {
            e = true;
        } else {
            for (AccessMode mode: modes) {
                switch (mode) {
                    case READ : r = true; break;
                    case WRITE : w = true; break;
                    case EXECUTE : x = true; break;
                    default: throw new AssertionError("Should not get here");
                }
            }
        }

        int mode = 0;
        if (e || r) {
            checkRead();
            mode |= (r) ? R_OK : F_OK;
        }
        if (w) {
            checkWrite();
            mode |= W_OK;
        }
        if (x) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // not cached
                sm.checkExec(getPathForPermissionCheck());
            }
            mode |= X_OK;
        }
        try {
            access(this, mode);
        } catch (UnixException exc) {
            exc.rethrowAsIOException(this);
        }
    }

    @Override
    void implDelete(boolean failIfNotExists) throws IOException {
        checkDelete();

        // need file attributes to know if file is directory
        UnixFileAttributes attrs = null;
        try {
            attrs = UnixFileAttributes.get(this, false);
            if (attrs.isDirectory()) {
                rmdir(this);
            } else {
                unlink(this);
            }
        } catch (UnixException x) {
            // no-op if file does not exist
            if (!failIfNotExists && x.errno() == ENOENT)
                return;

            // DirectoryNotEmptyException if not empty
            if (attrs != null && attrs.isDirectory() &&
                (x.errno() == EEXIST || x.errno() == ENOTEMPTY))
                throw new DirectoryNotEmptyException(getPathForExecptionMessage());

            x.rethrowAsIOException(this);
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        if (filter == null)
            throw new NullPointerException();
        checkRead();

        // can't return SecureDirectoryStream on kernels that don't support
        // openat, etc.
        if (!supportsAtSysCalls()) {
            try {
                long ptr = opendir(this);
                return new UnixDirectoryStream(this, ptr, filter);
            } catch (UnixException x) {
                if (x.errno() == ENOTDIR)
                    throw new NotDirectoryException(getPathForExecptionMessage());
                x.rethrowAsIOException(this);
            }
        }

        // open directory and dup file descriptor for use by
        // opendir/readdir/closedir
        int dfd1 = -1;
        int dfd2 = -1;
        long dp = 0L;
        try {
            dfd1 = open(this, O_RDONLY, 0);
            dfd2 = dup(dfd1);
            dp = fdopendir(dfd1);
        } catch (UnixException x) {
            if (dfd1 != -1)
                close(dfd1);
            if (dfd2 != -1)
                close(dfd2);
            if (x.errno() == UnixConstants.ENOTDIR)
                throw new NotDirectoryException(getPathForExecptionMessage());
            x.rethrowAsIOException(this);
        }
        return new UnixSecureDirectoryStream(this, dp, dfd2, filter);
    }

    // invoked by AbstractPath#copyTo
    @Override
    public void implCopyTo(Path obj, CopyOption... options)
        throws IOException
    {
        UnixPath target = (UnixPath)obj;
        UnixCopyFile.copy(this, target, options);
    }

    @Override
    public void implMoveTo(Path obj, CopyOption... options)
        throws IOException
    {
        UnixPath target = (UnixPath)obj;
        UnixCopyFile.move(this, target, options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V
        getFileAttributeView(Class<V> type, LinkOption... options)
    {
        FileAttributeView view = getFileSystem()
            .newFileAttributeView(type, this, options);
        if (view == null)
            return null;
        return (V) view;
    }

    @Override
    public DynamicFileAttributeView getFileAttributeView(String name,
                                                         LinkOption... options)
    {
        return getFileSystem().newFileAttributeView(name, this, options);
    }

    @Override
    public Path createDirectory(FileAttribute<?>... attrs)
        throws IOException
    {
        checkWrite();

        int mode = UnixFileModeAttribute
            .toUnixMode(UnixFileModeAttribute.ALL_PERMISSIONS, attrs);
        try {
            mkdir(this, mode);
        } catch (UnixException x) {
            x.rethrowAsIOException(this);
        }
        return this;
    }

    @Override
    public SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs)
         throws IOException
    {
        int mode = UnixFileModeAttribute
            .toUnixMode(UnixFileModeAttribute.ALL_READWRITE, attrs);
        try {
            return UnixChannelFactory.newFileChannel(this, options, mode);
        } catch (UnixException x) {
            x.rethrowAsIOException(this);
            return null;  // keep compiler happy
        }
    }

    @Override
    public boolean isSameFile(Path obj) throws IOException {
        if (this.equals(obj))
            return true;
        if (!(obj instanceof UnixPath))  // includes null check
            return false;
        UnixPath other = (UnixPath)obj;

        // check security manager access to both files
        this.checkRead();
        other.checkRead();

        UnixFileAttributes thisAttrs;
        UnixFileAttributes otherAttrs;
        try {
             thisAttrs = UnixFileAttributes.get(this, true);
        } catch (UnixException x) {
            x.rethrowAsIOException(this);
            return false;    // keep compiler happy
        }
        try {
            otherAttrs = UnixFileAttributes.get(other, true);
        } catch (UnixException x) {
            x.rethrowAsIOException(other);
            return false;    // keep compiler happy
        }
        return thisAttrs.isSameFile(otherAttrs);
    }

    @Override
    public Path createSymbolicLink(Path obj, FileAttribute<?>... attrs)
        throws IOException
    {
        UnixPath target = checkPath(obj);

        // no attributes supported when creating links
        if (attrs.length > 0) {
            UnixFileModeAttribute.toUnixMode(0, attrs);  // may throw NPE or UOE
            throw new UnsupportedOperationException("Initial file attributes" +
                "not supported when creating symbolic link");
        }

        // permission check
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new LinkPermission("symbolic"));
            checkWrite();
        }

        // create link
        try {
            symlink(target.asByteArray(), this);
        } catch (UnixException x) {
            x.rethrowAsIOException(this);
        }

        return this;
    }

    @Override
    public Path createLink(Path obj) throws IOException {
        UnixPath existing = checkPath(obj);

        // permission check
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new LinkPermission("hard"));
            this.checkWrite();
            existing.checkWrite();
        }
        try {
            link(existing, this);
        } catch (UnixException x) {
            x.rethrowAsIOException(this, existing);
        }
        return this;
    }

    @Override
    public Path readSymbolicLink() throws IOException {
        // permission check
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            FilePermission perm = new FilePermission(getPathForPermissionCheck(),
                SecurityConstants.FILE_READLINK_ACTION);
            AccessController.checkPermission(perm);
        }
        try {
            byte[] target = readlink(this);
            return new UnixPath(getFileSystem(), target);
        } catch (UnixException x) {
           if (x.errno() == UnixConstants.EINVAL)
                throw new NotLinkException(getPathForExecptionMessage());
            x.rethrowAsIOException(this);
            return null;    // keep compiler happy
        }
    }

    @Override
    public UnixPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // The path is relative so need to resolve against default directory,
        // taking care not to reveal the user.dir
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPropertyAccess("user.dir");
        }
        return new UnixPath(getFileSystem(),
            resolve(getFileSystem().defaultDirectory(), path));
    }

    @Override
    public UnixPath toRealPath(boolean resolveLinks) throws IOException {
        checkRead();

        UnixPath absolute = toAbsolutePath();

        // if resolveLinks is true then use realpath
        if (resolveLinks) {
            try {
                byte[] rp = realpath(absolute);
                return new UnixPath(getFileSystem(), rp);
            } catch (UnixException x) {
                x.rethrowAsIOException(this);
            }
        }

        // if resolveLinks is false then eliminate "." and also ".."
        // where the previous element is not a link.
        UnixPath root = getFileSystem().rootDirectory();
        UnixPath result = root;
        for (int i=0; i<absolute.getNameCount(); i++) {
            UnixPath element = absolute.getName(i);

            // eliminate "."
            if ((element.asByteArray().length == 1) && (element.asByteArray()[0] == '.'))
                continue;

            // cannot eliminate ".." if previous element is a link
            if ((element.asByteArray().length == 2) && (element.asByteArray()[0] == '.') &&
                (element.asByteArray()[1] == '.'))
            {
                UnixFileAttributes attrs = null;
                try {
                    attrs = UnixFileAttributes.get(result, false);
                } catch (UnixException x) {
                    x.rethrowAsIOException(result);
                }
                if (!attrs.isSymbolicLink()) {
                    result = result.getParent();
                    if (result == null) {
                        result = root;
                    }
                    continue;
                }
            }
            result = result.resolve(element);
        }
        return result;
    }

    @Override
    public boolean isHidden() {
        checkRead();
        UnixPath name = getName();
        if (name == null)
            return false;
        return (name.asByteArray()[0] == '.');
    }

    @Override
    public URI toUri() {
        return UnixUriUtils.toUri(this);
    }

    @Override
    public WatchKey register(WatchService watcher,
                             WatchEvent.Kind<?>[] events,
                             WatchEvent.Modifier... modifiers)
        throws IOException
    {
        if (watcher == null)
            throw new NullPointerException();
        if (!(watcher instanceof AbstractWatchService))
            throw new ProviderMismatchException();
        checkRead();
        return ((AbstractWatchService)watcher).register(this, events, modifiers);
    }
}
