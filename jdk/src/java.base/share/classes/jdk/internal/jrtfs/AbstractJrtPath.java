/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Base class for Path implementation of jrt file systems.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
abstract class AbstractJrtPath implements Path {

    protected final AbstractJrtFileSystem jrtfs;
    private final byte[] path;
    private volatile int[] offsets;
    private int hashcode = 0;  // cached hashcode (created lazily)

    AbstractJrtPath(AbstractJrtFileSystem jrtfs, byte[] path) {
        this(jrtfs, path, false);
        this.resolved = null;
    }

    AbstractJrtPath(AbstractJrtFileSystem jrtfs, byte[] path, boolean normalized) {
        this.resolved = null;
        this.jrtfs = jrtfs;
        if (normalized) {
            this.path = path;
        } else {
            this.path = normalize(path);
        }
    }

    // factory methods to create subtypes of AbstractJrtPath
    protected abstract AbstractJrtPath newJrtPath(byte[] path);

    protected abstract AbstractJrtPath newJrtPath(byte[] path, boolean normalized);

    final byte[] getName() {
        return path;
    }

    @Override
    public final AbstractJrtPath getRoot() {
        if (this.isAbsolute()) {
            return jrtfs.getRootPath();
        } else {
            return null;
        }
    }

    @Override
    public final AbstractJrtPath getFileName() {
        initOffsets();
        int count = offsets.length;
        if (count == 0) {
            return null;  // no elements so no name
        }
        if (count == 1 && path[0] != '/') {
            return this;
        }
        int lastOffset = offsets[count - 1];
        int len = path.length - lastOffset;
        byte[] result = new byte[len];
        System.arraycopy(path, lastOffset, result, 0, len);
        return newJrtPath(result);
    }

    @Override
    public final AbstractJrtPath getParent() {
        initOffsets();
        int count = offsets.length;
        if (count == 0) // no elements so no parent
        {
            return null;
        }
        int len = offsets[count - 1] - 1;
        if (len <= 0) // parent is root only (may be null)
        {
            return getRoot();
        }
        byte[] result = new byte[len];
        System.arraycopy(path, 0, result, 0, len);
        return newJrtPath(result);
    }

    @Override
    public final int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public final AbstractJrtPath getName(int index) {
        initOffsets();
        if (index < 0 || index >= offsets.length) {
            throw new IllegalArgumentException();
        }
        int begin = offsets[index];
        int len;
        if (index == (offsets.length - 1)) {
            len = path.length - begin;
        } else {
            len = offsets[index + 1] - begin - 1;
        }
        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return newJrtPath(result);
    }

    @Override
    public final AbstractJrtPath subpath(int beginIndex, int endIndex) {
        initOffsets();
        if (beginIndex < 0
                || beginIndex >= offsets.length
                || endIndex > offsets.length
                || beginIndex >= endIndex) {
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
        return newJrtPath(result);
    }

    @Override
    public final AbstractJrtPath toRealPath(LinkOption... options) throws IOException {
        AbstractJrtPath realPath = newJrtPath(getResolvedPath()).toAbsolutePath();
        realPath = JrtFileSystem.followLinks(options) ? jrtfs.resolveLink(this) : realPath;
        realPath.checkAccess();
        return realPath;
    }

    final AbstractJrtPath readSymbolicLink() throws IOException {
        if (!jrtfs.isLink(this)) {
            throw new IOException("not a symbolic link");
        }

        return jrtfs.resolveLink(this);
    }

    final boolean isHidden() {
        return false;
    }

    @Override
    public final AbstractJrtPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        } else {
            //add / bofore the existing path
            byte[] tmp = new byte[path.length + 1];
            tmp[0] = '/';
            System.arraycopy(path, 0, tmp, 1, path.length);
            return newJrtPath(tmp).normalize();
        }
    }

    @Override
    public final URI toUri() {
        try {
            return new URI("jrt",
                    JrtFileSystem.getString(toAbsolutePath().path),
                    null);
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    private boolean equalsNameAt(AbstractJrtPath other, int index) {
        int mbegin = offsets[index];
        int mlen;
        if (index == (offsets.length - 1)) {
            mlen = path.length - mbegin;
        } else {
            mlen = offsets[index + 1] - mbegin - 1;
        }
        int obegin = other.offsets[index];
        int olen;
        if (index == (other.offsets.length - 1)) {
            olen = other.path.length - obegin;
        } else {
            olen = other.offsets[index + 1] - obegin - 1;
        }
        if (mlen != olen) {
            return false;
        }
        int n = 0;
        while (n < mlen) {
            if (path[mbegin + n] != other.path[obegin + n]) {
                return false;
            }
            n++;
        }
        return true;
    }

    @Override
    public final AbstractJrtPath relativize(Path other) {
        final AbstractJrtPath o = checkPath(other);
        if (o.equals(this)) {
            return newJrtPath(new byte[0], true);
        }
        if (/* this.getFileSystem() != o.getFileSystem() || */this.isAbsolute() != o.isAbsolute()) {
            throw new IllegalArgumentException();
        }
        int mc = this.getNameCount();
        int oc = o.getNameCount();
        int n = Math.min(mc, oc);
        int i = 0;
        while (i < n) {
            if (!equalsNameAt(o, i)) {
                break;
            }
            i++;
        }
        int dotdots = mc - i;
        int len = dotdots * 3 - 1;
        if (i < oc) {
            len += (o.path.length - o.offsets[i] + 1);
        }
        byte[] result = new byte[len];

        int pos = 0;
        while (dotdots > 0) {
            result[pos++] = (byte) '.';
            result[pos++] = (byte) '.';
            if (pos < len) // no tailing slash at the end
            {
                result[pos++] = (byte) '/';
            }
            dotdots--;
        }
        if (i < oc) {
            System.arraycopy(o.path, o.offsets[i],
                    result, pos,
                    o.path.length - o.offsets[i]);
        }
        return newJrtPath(result);
    }

    @Override
    public AbstractJrtFileSystem getFileSystem() {
        return jrtfs;
    }

    @Override
    public final boolean isAbsolute() {
        return (this.path.length > 0 && path[0] == '/');
    }

    @Override
    public final AbstractJrtPath resolve(Path other) {
        final AbstractJrtPath o = checkPath(other);
        if (o.isAbsolute()) {
            return o;
        }
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
        return newJrtPath(res);
    }

    @Override
    public final Path resolveSibling(Path other) {
        if (other == null) {
            throw new NullPointerException();
        }
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
    }

    @Override
    public final boolean startsWith(Path other) {
        final AbstractJrtPath o = checkPath(other);
        if (o.isAbsolute() != this.isAbsolute()
                || o.path.length > this.path.length) {
            return false;
        }
        int olast = o.path.length;
        for (int i = 0; i < olast; i++) {
            if (o.path[i] != this.path[i]) {
                return false;
            }
        }
        olast--;
        return o.path.length == this.path.length
                || o.path[olast] == '/'
                || this.path[olast + 1] == '/';
    }

    @Override
    public final boolean endsWith(Path other) {
        final AbstractJrtPath o = checkPath(other);
        int olast = o.path.length - 1;
        if (olast > 0 && o.path[olast] == '/') {
            olast--;
        }
        int last = this.path.length - 1;
        if (last > 0 && this.path[last] == '/') {
            last--;
        }
        if (olast == -1) // o.path.length == 0
        {
            return last == -1;
        }
        if ((o.isAbsolute() && (!this.isAbsolute() || olast != last))
                || (last < olast)) {
            return false;
        }
        for (; olast >= 0; olast--, last--) {
            if (o.path[olast] != this.path[last]) {
                return false;
            }
        }
        return o.path[olast + 1] == '/'
                || last == -1 || this.path[last] == '/';
    }

    @Override
    public final AbstractJrtPath resolve(String other) {
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
    public final AbstractJrtPath normalize() {
        byte[] res = getResolved();
        if (res == path) // no change
        {
            return this;
        }
        return newJrtPath(res, true);
    }

    private AbstractJrtPath checkPath(Path path) {
        if (path == null) {
            throw new NullPointerException();
        }
        if (!(path instanceof AbstractJrtPath)) {
            throw new ProviderMismatchException();
        }
        return (AbstractJrtPath) path;
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
                    while (index < path.length && path[index] != '/') {
                        index++;
                    }
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
                    while (index < path.length && path[index] != '/') {
                        index++;
                    }
                }
            }
            synchronized (this) {
                if (offsets == null) {
                    offsets = result;
                }
            }
        }
    }

    private volatile byte[] resolved;

    final byte[] getResolvedPath() {
        byte[] r = resolved;
        if (r == null) {
            if (isAbsolute()) {
                r = getResolved();
            } else {
                r = toAbsolutePath().getResolvedPath();
            }
            resolved = r;
        }
        return resolved;
    }

    // removes redundant slashs, replace "\" to separator "/"
    // and check for invalid characters
    private static byte[] normalize(byte[] path) {
        if (path.length == 0) {
            return path;
        }
        byte prevC = 0;
        for (int i = 0; i < path.length; i++) {
            byte c = path[i];
            if (c == '\\') {
                return normalize(path, i);
            }
            if (c == (byte) '/' && prevC == '/') {
                return normalize(path, i - 1);
            }
            if (c == '\u0000') {
                throw new InvalidPathException(JrtFileSystem.getString(path),
                        "Path: nul character not allowed");
            }
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
            if (c == (byte) '\\') {
                c = (byte) '/';
            }
            if (c == (byte) '/' && prevC == (byte) '/') {
                continue;
            }
            if (c == '\u0000') {
                throw new InvalidPathException(JrtFileSystem.getString(path),
                        "Path: nul character not allowed");
            }
            to[m++] = c;
            prevC = c;
        }
        if (m > 1 && to[m - 1] == '/') {
            m--;
        }
        return (m == to.length) ? to : Arrays.copyOf(to, m);
    }

    // Remove DotSlash(./) and resolve DotDot (..) components
    private byte[] getResolved() {
        if (path.length == 0) {
            return path;
        }
        for (int i = 0; i < path.length; i++) {
            byte c = path[i];
            if (c == (byte) '.') {
                return resolve0();
            }
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
            int len = (i == offsets.length - 1)
                    ? (path.length - n) : (offsets[i + 1] - n - 1);
            if (len == 1 && path[n] == (byte) '.') {
                if (m == 0 && path[0] == '/') // absolute path
                {
                    to[m++] = '/';
                }
                continue;
            }
            if (len == 2 && path[n] == '.' && path[n + 1] == '.') {
                if (lastMOff >= 0) {
                    m = lastM[lastMOff--];  // retreat
                    continue;
                }
                if (path[0] == '/') {  // "/../xyz" skip
                    if (m == 0) {
                        to[m++] = '/';
                    }
                } else {               // "../xyz" -> "../xyz"
                    if (m != 0 && to[m - 1] != '/') {
                        to[m++] = '/';
                    }
                    while (len-- > 0) {
                        to[m++] = path[n++];
                    }
                }
                continue;
            }
            if (m == 0 && path[0] == '/' || // absolute path
                    m != 0 && to[m - 1] != '/') {   // not the first name
                to[m++] = '/';
            }
            lastM[++lastMOff] = m;
            while (len-- > 0) {
                to[m++] = path[n++];
            }
        }
        if (m > 1 && to[m - 1] == '/') {
            m--;
        }
        return (m == to.length) ? to : Arrays.copyOf(to, m);
    }

    @Override
    public final String toString() {
        return JrtFileSystem.getString(path);
    }

    @Override
    public final int hashCode() {
        int h = hashcode;
        if (h == 0) {
            hashcode = h = Arrays.hashCode(path);
        }
        return h;
    }

    @Override
    public final boolean equals(Object obj) {
        return obj != null
                && obj instanceof AbstractJrtPath
                && this.jrtfs == ((AbstractJrtPath) obj).jrtfs
                && compareTo((Path) obj) == 0;
    }

    @Override
    public final int compareTo(Path other) {
        final AbstractJrtPath o = checkPath(other);
        int len1 = this.path.length;
        int len2 = o.path.length;

        int n = Math.min(len1, len2);
        byte v1[] = this.path;
        byte v2[] = o.path;

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
    public final WatchKey register(
            WatchService watcher,
            WatchEvent.Kind<?>[] events,
            WatchEvent.Modifier... modifiers) {
        if (watcher == null || events == null || modifiers == null) {
            throw new NullPointerException();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public final WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) {
        return register(watcher, events, new WatchEvent.Modifier[0]);
    }

    @Override
    public final File toFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Iterator<Path> iterator() {
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
    final int getPathLength() {
        return path.length;
    }

    final void createDirectory(FileAttribute<?>... attrs)
            throws IOException {
        jrtfs.createDirectory(this, attrs);
    }

    final InputStream newInputStream(OpenOption... options) throws IOException {
        if (options.length > 0) {
            for (OpenOption opt : options) {
                if (opt != READ) {
                    throw new UnsupportedOperationException("'" + opt + "' not allowed");
                }
            }
        }
        return jrtfs.newInputStream(this);
    }

    final DirectoryStream<Path> newDirectoryStream(Filter<? super Path> filter)
            throws IOException {
        return new JrtDirectoryStream(this, filter);
    }

    final void delete() throws IOException {
        jrtfs.deleteFile(this, true);
    }

    final void deleteIfExists() throws IOException {
        jrtfs.deleteFile(this, false);
    }

    final AbstractJrtFileAttributes getAttributes(LinkOption... options) throws IOException {
        AbstractJrtFileAttributes zfas = jrtfs.getFileAttributes(this, options);
        if (zfas == null) {
            throw new NoSuchFileException(toString());
        }
        return zfas;
    }

    final void setAttribute(String attribute, Object value, LinkOption... options)
            throws IOException {
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
        JrtFileAttributeView view = JrtFileAttributeView.get(this, type, options);
        if (view == null) {
            throw new UnsupportedOperationException("view <" + view + "> is not supported");
        }
        view.setAttribute(attr, value);
    }

    final void setTimes(FileTime mtime, FileTime atime, FileTime ctime)
            throws IOException {
        jrtfs.setTimes(this, mtime, atime, ctime);
    }

    final Map<String, Object> readAttributes(String attributes, LinkOption... options)
            throws IOException {
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
        JrtFileAttributeView jrtfv = JrtFileAttributeView.get(this, view, options);
        if (jrtfv == null) {
            throw new UnsupportedOperationException("view not supported");
        }
        return jrtfv.readAttributes(attrs);
    }

    final FileStore getFileStore() throws IOException {
        // each JrtFileSystem only has one root (as requested for now)
        if (exists()) {
            return jrtfs.getFileStore(this);
        }
        throw new NoSuchFileException(JrtFileSystem.getString(path));
    }

    final boolean isSameFile(Path other) throws IOException {
        if (this.equals(other)) {
            return true;
        }
        if (other == null
                || this.getFileSystem() != other.getFileSystem()) {
            return false;
        }
        this.checkAccess();
        AbstractJrtPath target = (AbstractJrtPath) other;
        target.checkAccess();
        return Arrays.equals(this.getResolvedPath(), target.getResolvedPath())
                || jrtfs.isSameFile(this, target);
    }

    final SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException {
        return jrtfs.newByteChannel(this, options, attrs);
    }

    final FileChannel newFileChannel(Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException {
        return jrtfs.newFileChannel(this, options, attrs);
    }

    final void checkAccess(AccessMode... modes) throws IOException {
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

        BasicFileAttributes attrs = jrtfs.getFileAttributes(this);
        if (attrs == null && (path.length != 1 || path[0] != '/')) {
            throw new NoSuchFileException(toString());
        }
        if (w) {
//            if (jrtfs.isReadOnly())
            throw new AccessDeniedException(toString());
        }
        if (x) {
            throw new AccessDeniedException(toString());
        }
    }

    final boolean exists() {
        try {
            return jrtfs.exists(this);
        } catch (IOException x) {
        }
        return false;
    }

    final OutputStream newOutputStream(OpenOption... options) throws IOException {
        if (options.length == 0) {
            return jrtfs.newOutputStream(this,
                    CREATE_NEW, WRITE);
        }
        return jrtfs.newOutputStream(this, options);
    }

    final void move(AbstractJrtPath target, CopyOption... options)
            throws IOException {
        if (this.jrtfs == target.jrtfs) {
            jrtfs.copyFile(true,
                    this, target,
                    options);
        } else {
            copyToTarget(target, options);
            delete();
        }
    }

    final void copy(AbstractJrtPath target, CopyOption... options)
            throws IOException {
        if (this.jrtfs == target.jrtfs) {
            jrtfs.copyFile(false,
                    this, target,
                    options);
        } else {
            copyToTarget(target, options);
        }
    }

    private void copyToTarget(AbstractJrtPath target, CopyOption... options)
            throws IOException {
        boolean replaceExisting = false;
        boolean copyAttrs = false;
        for (CopyOption opt : options) {
            if (opt == REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (opt == COPY_ATTRIBUTES) {
                copyAttrs = true;
            }
        }
        // attributes of source file
        BasicFileAttributes jrtfas = getAttributes();
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
        if (exists) {
            throw new FileAlreadyExistsException(target.toString());
        }

        if (jrtfas.isDirectory()) {
            // create directory or file
            target.createDirectory();
        } else {
            try (InputStream is = jrtfs.newInputStream(this); OutputStream os = target.newOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
            }
        }
        if (copyAttrs) {
            BasicFileAttributeView view
                    = JrtFileAttributeView.get(target, BasicFileAttributeView.class);
            try {
                view.setTimes(jrtfas.lastModifiedTime(),
                        jrtfas.lastAccessTime(),
                        jrtfas.creationTime());
            } catch (IOException x) {
                // rollback?
                try {
                    target.delete();
                } catch (IOException ignore) {
                }
                throw x;
            }
        }
    }
}
