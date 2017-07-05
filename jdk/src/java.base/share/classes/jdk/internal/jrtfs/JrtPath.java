/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jrtfs;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.attribute.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

final class JrtPath implements Path {

    private final JrtFileSystem jrtfs;
    private final byte[] path;
    private volatile int[] offsets;
    private int hashcode = 0;  // cached hashcode (created lazily)

    JrtPath(JrtFileSystem jrtfs, byte[] path) {
        this(jrtfs, path, false);
    }

    JrtPath(JrtFileSystem jrtfs, byte[] path, boolean normalized) {
        this.jrtfs = jrtfs;
        if (normalized)
            this.path = path;
        else
            this.path = normalize(path);
    }

    @Override
    public JrtPath getRoot() {
        if (this.isAbsolute())
            return jrtfs.getRootPath();
        else
            return null;
    }

    @Override
    public Path getFileName() {
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
        return new JrtPath(jrtfs, result);
    }

    @Override
    public JrtPath getParent() {
        initOffsets();
        int count = offsets.length;
        if (count == 0)    // no elements so no parent
            return null;
        int len = offsets[count-1] - 1;
        if (len <= 0)      // parent is root only (may be null)
            return getRoot();
        byte[] result = new byte[len];
        System.arraycopy(path, 0, result, 0, len);
        return new JrtPath(jrtfs, result);
    }

    @Override
    public int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public JrtPath getName(int index) {
        initOffsets();
        if (index < 0 || index >= offsets.length)
            throw new IllegalArgumentException();
        int begin = offsets[index];
        int len;
        if (index == (offsets.length-1))
            len = path.length - begin;
        else
            len = offsets[index+1] - begin - 1;
        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new JrtPath(jrtfs, result);
    }

    @Override
    public JrtPath subpath(int beginIndex, int endIndex) {
        initOffsets();
        if (beginIndex < 0 ||
            beginIndex >=  offsets.length ||
            endIndex > offsets.length ||
            beginIndex >= endIndex)
            throw new IllegalArgumentException();

        // starting offset and length
        int begin = offsets[beginIndex];
        int len;
        if (endIndex == offsets.length)
            len = path.length - begin;
        else
            len = offsets[endIndex] - begin - 1;
        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new JrtPath(jrtfs, result);
    }

    @Override
    public JrtPath toRealPath(LinkOption... options) throws IOException {
        JrtPath realPath = new JrtPath(jrtfs, getResolvedPath()).toAbsolutePath();
        realPath.checkAccess();
        return realPath;
    }

    boolean isHidden() {
        return false;
    }

    @Override
    public JrtPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        } else {
            //add / bofore the existing path
            byte[] tmp = new byte[path.length + 1];
            tmp[0] = '/';
            System.arraycopy(path, 0, tmp, 1, path.length);
            return (JrtPath) new JrtPath(jrtfs, tmp).normalize();
        }
    }

    @Override
    public URI toUri() {
        try {
            return new URI("jrt",
                           JrtFileSystem.getString(toAbsolutePath().path),
                           null);
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    private boolean equalsNameAt(JrtPath other, int index) {
        int mbegin = offsets[index];
        int mlen;
        if (index == (offsets.length-1))
            mlen = path.length - mbegin;
        else
            mlen = offsets[index + 1] - mbegin - 1;
        int obegin = other.offsets[index];
        int olen;
        if (index == (other.offsets.length - 1))
            olen = other.path.length - obegin;
        else
            olen = other.offsets[index + 1] - obegin - 1;
        if (mlen != olen)
            return false;
        int n = 0;
        while(n < mlen) {
            if (path[mbegin + n] != other.path[obegin + n])
                return false;
            n++;
        }
        return true;
    }

    @Override
    public Path relativize(Path other) {
        final JrtPath o = checkPath(other);
        if (o.equals(this))
            return new JrtPath(getFileSystem(), new byte[0], true);
        if (/* this.getFileSystem() != o.getFileSystem() || */
            this.isAbsolute() != o.isAbsolute()) {
            throw new IllegalArgumentException();
        }
        int mc = this.getNameCount();
        int oc = o.getNameCount();
        int n = Math.min(mc, oc);
        int i = 0;
        while (i < n) {
            if (!equalsNameAt(o, i))
                break;
            i++;
        }
        int dotdots = mc - i;
        int len = dotdots * 3 - 1;
        if (i < oc)
            len += (o.path.length - o.offsets[i] + 1);
        byte[] result = new byte[len];

        int pos = 0;
        while (dotdots > 0) {
            result[pos++] = (byte)'.';
            result[pos++] = (byte)'.';
            if (pos < len)       // no tailing slash at the end
                result[pos++] = (byte)'/';
            dotdots--;
        }
        if (i < oc)
            System.arraycopy(o.path, o.offsets[i],
                             result, pos,
                             o.path.length - o.offsets[i]);
        return new JrtPath(getFileSystem(), result);
    }

    @Override
    public JrtFileSystem getFileSystem() {
        return jrtfs;
    }

    @Override
    public boolean isAbsolute() {
        return (this.path.length > 0 && path[0] == '/');
    }

    @Override
    public JrtPath resolve(Path other) {
        final JrtPath o = checkPath(other);
        if (o.isAbsolute())
            return o;
        byte[] res;
        if (this.path[path.length - 1] == '/') {
            res = new byte[path.length + o.path.length];
            System.arraycopy(path, 0, res, 0, path.length);
            System.arraycopy(o.path, 0, res, path.length, o.path.length);
        } else {
            res = new byte[path.length + 1 + o.path.length];
            System.arraycopy(path, 0, res, 0, path.length);
            res[path.length] = '/';
            System.arraycopy(o.path, 0, res, path.length + 1, o.path.length);
        }
        return new JrtPath(jrtfs, res);
    }

    @Override
    public Path resolveSibling(Path other) {
        if (other == null)
            throw new NullPointerException();
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
    }

    @Override
    public boolean startsWith(Path other) {
        final JrtPath o = checkPath(other);
        if (o.isAbsolute() != this.isAbsolute() ||
            o.path.length > this.path.length)
            return false;
        int olast = o.path.length;
        for (int i = 0; i < olast; i++) {
            if (o.path[i] != this.path[i])
                return false;
        }
        olast--;
        return o.path.length == this.path.length ||
               o.path[olast] == '/' ||
               this.path[olast + 1] == '/';
    }

    @Override
    public boolean endsWith(Path other) {
        final JrtPath o = checkPath(other);
        int olast = o.path.length - 1;
        if (olast > 0 && o.path[olast] == '/')
            olast--;
        int last = this.path.length - 1;
        if (last > 0 && this.path[last] == '/')
            last--;
        if (olast == -1)    // o.path.length == 0
            return last == -1;
        if ((o.isAbsolute() &&(!this.isAbsolute() || olast != last)) ||
            (last < olast))
            return false;
        for (; olast >= 0; olast--, last--) {
            if (o.path[olast] != this.path[last])
                return false;
        }
        return o.path[olast + 1] == '/' ||
               last == -1 || this.path[last] == '/';
    }

    @Override
    public JrtPath resolve(String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @Override
    public final Path resolveSibling(String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public final boolean startsWith(String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public final boolean endsWith(String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public Path normalize() {
        byte[] res = getResolved();
        if (res == path)    // no change
            return this;
        return new JrtPath(jrtfs, res, true);
    }

    private JrtPath checkPath(Path path) {
        if (path == null)
            throw new NullPointerException();
        if (!(path instanceof JrtPath))
            throw new ProviderMismatchException();
        return (JrtPath) path;
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

    // resolved path for locating jrt entry inside the jrt file,
    // the result path does not contain ./ and .. components
    // resolved bytes will always start with '/'
    private volatile byte[] resolved = null;
    byte[] getResolvedPath() {
        byte[] r = resolved;
        if (r == null) {
            if (isAbsolute())
                r = getResolved();
            else
                r = toAbsolutePath().getResolvedPath();
            resolved = r;
        }
        return resolved;
    }

    // removes redundant slashs, replace "\" to separator "/"
    // and check for invalid characters
    private static byte[] normalize(byte[] path) {
        if (path.length == 0)
            return path;
        byte prevC = 0;
        for (int i = 0; i < path.length; i++) {
            byte c = path[i];
            if (c == '\\')
                return normalize(path, i);
            if (c == (byte)'/' && prevC == '/')
                return normalize(path, i - 1);
            if (c == '\u0000')
                throw new InvalidPathException(JrtFileSystem.getString(path),
                                               "Path: nul character not allowed");
            prevC = c;
        }

        if (path.length > 1 && path[path.length - 1] == '/') {
            return Arrays.copyOf(path, path.length - 1);
        }

        return path;
    }

    private static byte[] normalize(byte[] path, int off) {
        byte[] to = new byte[path.length];
        int n = 0;
        while (n < off) {
            to[n] = path[n];
            n++;
        }
        int m = n;
        byte prevC = 0;
        while (n < path.length) {
            byte c = path[n++];
            if (c == (byte)'\\')
                c = (byte)'/';
            if (c == (byte)'/' && prevC == (byte)'/')
                continue;
            if (c == '\u0000')
                throw new InvalidPathException(JrtFileSystem.getString(path),
                                               "Path: nul character not allowed");
            to[m++] = c;
            prevC = c;
        }
        if (m > 1 && to[m - 1] == '/')
            m--;
        return (m == to.length)? to : Arrays.copyOf(to, m);
    }

    // Remove DotSlash(./) and resolve DotDot (..) components
    private byte[] getResolved() {
        if (path.length == 0)
            return path;
        for (int i = 0; i < path.length; i++) {
            byte c = path[i];
            if (c == (byte)'.')
                return resolve0();
        }

        return path;
    }

    // TBD: performance, avoid initOffsets
    private byte[] resolve0() {
        byte[] to = new byte[path.length];
        int nc = getNameCount();
        int[] lastM = new int[nc];
        int lastMOff = -1;
        int m = 0;
        for (int i = 0; i < nc; i++) {
            int n = offsets[i];
            int len = (i == offsets.length - 1)?
                      (path.length - n):(offsets[i + 1] - n - 1);
            if (len == 1 && path[n] == (byte)'.') {
                if (m == 0 && path[0] == '/')   // absolute path
                    to[m++] = '/';
                continue;
            }
            if (len == 2 && path[n] == '.' && path[n + 1] == '.') {
                if (lastMOff >= 0) {
                    m = lastM[lastMOff--];  // retreat
                    continue;
                }
                if (path[0] == '/') {  // "/../xyz" skip
                    if (m == 0)
                        to[m++] = '/';
                } else {               // "../xyz" -> "../xyz"
                    if (m != 0 && to[m-1] != '/')
                        to[m++] = '/';
                    while (len-- > 0)
                        to[m++] = path[n++];
                }
                continue;
            }
            if (m == 0 && path[0] == '/' ||   // absolute path
                m != 0 && to[m-1] != '/') {   // not the first name
                to[m++] = '/';
            }
            lastM[++lastMOff] = m;
            while (len-- > 0)
                to[m++] = path[n++];
        }
        if (m > 1 && to[m - 1] == '/')
            m--;
        return (m == to.length)? to : Arrays.copyOf(to, m);
    }

    @Override
    public String toString() {
        return JrtFileSystem.getString(path);
    }

    @Override
    public int hashCode() {
        int h = hashcode;
        if (h == 0)
            hashcode = h = Arrays.hashCode(path);
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null &&
               obj instanceof JrtPath &&
               this.jrtfs == ((JrtPath)obj).jrtfs &&
               compareTo((Path) obj) == 0;
    }

    @Override
    public int compareTo(Path other) {
        final JrtPath o = checkPath(other);
        int len1 = this.path.length;
        int len2 = o.path.length;

        int n = Math.min(len1, len2);
        byte v1[] = this.path;
        byte v2[] = o.path;

        int k = 0;
        while (k < n) {
            int c1 = v1[k] & 0xff;
            int c2 = v2[k] & 0xff;
            if (c1 != c2)
                return c1 - c2;
            k++;
        }
        return len1 - len2;
    }

    @Override
    public WatchKey register(
            WatchService watcher,
            WatchEvent.Kind<?>[] events,
            WatchEvent.Modifier... modifiers) {
        if (watcher == null || events == null || modifiers == null) {
            throw new NullPointerException();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) {
        return register(watcher, events, new WatchEvent.Modifier[0]);
    }

    @Override
    public final File toFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator() {
        return new Iterator<Path>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return (i < getNameCount());
            }

            @Override
            public Path next() {
                if (i < getNameCount()) {
                    Path result = getName(i);
                    i++;
                    return result;
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new ReadOnlyFileSystemException();
            }
        };
    }

    /////////////////////////////////////////////////////////////////////
    // Helpers for JrtFileSystemProvider and JrtFileSystem

    int getPathLength() {
        return path.length;
    }


    void createDirectory(FileAttribute<?>... attrs)
        throws IOException
    {
        jrtfs.createDirectory(getResolvedPath(), attrs);
    }

    InputStream newInputStream(OpenOption... options) throws IOException
    {
        if (options.length > 0) {
            for (OpenOption opt : options) {
                if (opt != READ)
                    throw new UnsupportedOperationException("'" + opt + "' not allowed");
            }
        }
        return jrtfs.newInputStream(getResolvedPath());
    }

    DirectoryStream<Path> newDirectoryStream(Filter<? super Path> filter)
        throws IOException
    {
        return new JrtDirectoryStream(this, filter);
    }

    void delete() throws IOException {
        jrtfs.deleteFile(getResolvedPath(), true);
    }

    void deleteIfExists() throws IOException {
        jrtfs.deleteFile(getResolvedPath(), false);
    }

    JrtFileAttributes getAttributes() throws IOException
    {
        JrtFileAttributes zfas = jrtfs.getFileAttributes(getResolvedPath());
        if (zfas == null)
            throw new NoSuchFileException(toString());
        return zfas;
    }

    void setAttribute(String attribute, Object value, LinkOption... options)
        throws IOException
    {
        String type;
        String attr;
        int colonPos = attribute.indexOf(':');
        if (colonPos == -1) {
            type = "basic";
            attr = attribute;
        } else {
            type = attribute.substring(0, colonPos++);
            attr = attribute.substring(colonPos);
        }
        JrtFileAttributeView view = JrtFileAttributeView.get(this, type);
        if (view == null)
            throw new UnsupportedOperationException("view <" + view + "> is not supported");
        view.setAttribute(attr, value);
    }

    void setTimes(FileTime mtime, FileTime atime, FileTime ctime)
        throws IOException
    {
        jrtfs.setTimes(getResolvedPath(), mtime, atime, ctime);
    }

    Map<String, Object> readAttributes(String attributes, LinkOption... options)
        throws IOException

    {
        String view;
        String attrs;
        int colonPos = attributes.indexOf(':');
        if (colonPos == -1) {
            view = "basic";
            attrs = attributes;
        } else {
            view = attributes.substring(0, colonPos++);
            attrs = attributes.substring(colonPos);
        }
        JrtFileAttributeView jrtfv = JrtFileAttributeView.get(this, view);
        if (jrtfv == null) {
            throw new UnsupportedOperationException("view not supported");
        }
        return jrtfv.readAttributes(attrs);
    }

    FileStore getFileStore() throws IOException {
        // each JrtFileSystem only has one root (as requested for now)
        if (exists())
            return jrtfs.getFileStore(this);
        throw new NoSuchFileException(JrtFileSystem.getString(path));
    }

    boolean isSameFile(Path other) throws IOException {
        if (this.equals(other))
            return true;
        if (other == null ||
            this.getFileSystem() != other.getFileSystem())
            return false;
        this.checkAccess();
        ((JrtPath)other).checkAccess();
        return Arrays.equals(this.getResolvedPath(),
                             ((JrtPath)other).getResolvedPath());
    }

    SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
                                       FileAttribute<?>... attrs)
        throws IOException
    {
        return jrtfs.newByteChannel(getResolvedPath(), options, attrs);
    }


    FileChannel newFileChannel(Set<? extends OpenOption> options,
                               FileAttribute<?>... attrs)
        throws IOException
    {
        return jrtfs.newFileChannel(getResolvedPath(), options, attrs);
    }

    void checkAccess(AccessMode... modes) throws IOException {
        boolean w = false;
        boolean x = false;
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                    break;
                case WRITE:
                    w = true;
                    break;
                case EXECUTE:
                    x = true;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        JrtFileAttributes attrs = jrtfs.getFileAttributes(getResolvedPath());
        if (attrs == null && (path.length != 1 || path[0] != '/'))
            throw new NoSuchFileException(toString());
        if (w) {
            if (jrtfs.isReadOnly())
                throw new AccessDeniedException(toString());
        }
        if (x)
            throw new AccessDeniedException(toString());
    }

    boolean exists() {
        if (isAbsolute())
            return true;
        try {
            return jrtfs.exists(getResolvedPath());
        } catch (IOException x) {}
        return false;
    }

    OutputStream newOutputStream(OpenOption... options) throws IOException
    {
        if (options.length == 0)
            return jrtfs.newOutputStream(getResolvedPath(),
                                       CREATE_NEW, WRITE);
        return jrtfs.newOutputStream(getResolvedPath(), options);
    }

    void move(JrtPath target, CopyOption... options)
        throws IOException
    {
        if (this.jrtfs == target.jrtfs)
        {
            jrtfs.copyFile(true,
                         getResolvedPath(), target.getResolvedPath(),
                         options);
        } else {
            copyToTarget(target, options);
            delete();
        }
    }

    void copy(JrtPath target, CopyOption... options)
        throws IOException
    {
        if (this.jrtfs == target.jrtfs)
            jrtfs.copyFile(false,
                         getResolvedPath(), target.getResolvedPath(),
                         options);
        else
            copyToTarget(target, options);
    }

    private void copyToTarget(JrtPath target, CopyOption... options)
        throws IOException
    {
        boolean replaceExisting = false;
        boolean copyAttrs = false;
        for (CopyOption opt : options) {
            if (opt == REPLACE_EXISTING)
                replaceExisting = true;
            else if (opt == COPY_ATTRIBUTES)
                copyAttrs = true;
        }
        // attributes of source file
        JrtFileAttributes jrtfas = getAttributes();
        // check if target exists
        boolean exists;
        if (replaceExisting) {
            try {
                target.deleteIfExists();
                exists = false;
            } catch (DirectoryNotEmptyException x) {
                exists = true;
            }
        } else {
            exists = target.exists();
        }
        if (exists)
            throw new FileAlreadyExistsException(target.toString());

        if (jrtfas.isDirectory()) {
            // create directory or file
            target.createDirectory();
        } else {
            try (InputStream is = jrtfs.newInputStream(getResolvedPath()); OutputStream os = target.newOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
            }
        }
        if (copyAttrs) {
            BasicFileAttributeView view =
                JrtFileAttributeView.get(target, BasicFileAttributeView.class);
            try {
                view.setTimes(jrtfas.lastModifiedTime(),
                              jrtfas.lastAccessTime(),
                              jrtfas.creationTime());
            } catch (IOException x) {
                // rollback?
                try {
                    target.delete();
                } catch (IOException ignore) { }
                throw x;
            }
        }
    }
}
