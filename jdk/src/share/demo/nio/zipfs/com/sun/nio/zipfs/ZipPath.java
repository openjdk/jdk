/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.nio.zipfs;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.spi.FileSystemProvider;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

/**
 *
 * @author  Xueming Shen, Rajendra Gutupalli,Jaya Hangal
 */

public class ZipPath extends Path {

    private final ZipFileSystem zfs;
    private final byte[] path;
    private volatile int[] offsets;
    private int hashcode = 0;  // cached hashcode (created lazily)

    ZipPath(ZipFileSystem zfs, byte[] path) {
        this(zfs, path, false);
    }

    ZipPath(ZipFileSystem zfs, byte[] path, boolean normalized)
    {
        this.zfs = zfs;
        if (normalized)
            this.path = path;
        else
            this.path = normalize(path);
    }

    @Override
    public ZipPath getRoot() {
        if (this.isAbsolute())
            return new ZipPath(zfs, new byte[]{path[0]});
        else
            return null;
    }

    @Override
    public Path getName() {
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
        return new ZipPath(zfs, result);
    }

    @Override
    public ZipPath getParent() {
        initOffsets();
        int count = offsets.length;
        if (count == 0)    // no elements so no parent
            return null;
        int len = offsets[count-1] - 1;
        if (len <= 0)      // parent is root only (may be null)
            return getRoot();
        byte[] result = new byte[len];
        System.arraycopy(path, 0, result, 0, len);
        return new ZipPath(zfs, result);
    }

    @Override
    public int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public ZipPath getName(int index) {
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
        return new ZipPath(zfs, result);
    }

    @Override
    public ZipPath subpath(int beginIndex, int endIndex) {
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
        return new ZipPath(zfs, result);
    }

    @Override
    public ZipPath toRealPath(boolean resolveLinks) throws IOException {
        ZipPath realPath = new ZipPath(zfs, getResolvedPath());
        realPath.checkAccess();
        return realPath;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public ZipPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        } else {
            //add / bofore the existing path
            byte[] defaultdir = zfs.getDefaultDir().path;
            int defaultlen = defaultdir.length;
            boolean endsWith = (defaultdir[defaultlen - 1] == '/');
            byte[] t = null;
            if (endsWith)
                t = new byte[defaultlen + path.length];
            else
                t = new byte[defaultlen + 1 + path.length];
            System.arraycopy(defaultdir, 0, t, 0, defaultlen);
            if (!endsWith)
                t[defaultlen++] = '/';
            System.arraycopy(path, 0, t, defaultlen, path.length);
            return new ZipPath(zfs, t, true);  // normalized
        }
    }

    @Override
    public URI toUri() {
        String zfPath = zfs.toString();
        if (File.separatorChar == '\\')  // replace all separators by '/'
            zfPath = "/" + zfPath.replace("\\", "/");
        try {
            return new URI("zip", "",
                           zfPath,
                           zfs.getString(toAbsolutePath().path));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private boolean equalsNameAt(ZipPath other, int index) {
        int mbegin = offsets[index];
        int mlen = 0;
        if (index == (offsets.length-1))
            mlen = path.length - mbegin;
        else
            mlen = offsets[index + 1] - mbegin - 1;
        int obegin = other.offsets[index];
        int olen = 0;
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
        final ZipPath o = checkPath(other);
        if (o.equals(this))
            return null;
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
        return new ZipPath(getFileSystem(), result);
    }

    @Override
    public ZipFileSystem getFileSystem() {
        return zfs;
    }

    @Override
    public boolean isAbsolute() {
        return (this.path[0] == '/');
    }

    @Override
    public ZipPath resolve(Path other) {
        if (other == null)
            return this;
        final ZipPath o = checkPath(other);
        if (o.isAbsolute())
            return o;
        byte[] resolved = null;
        if (this.path[path.length - 1] == '/') {
            resolved = new byte[path.length + o.path.length];
            System.arraycopy(path, 0, resolved, 0, path.length);
            System.arraycopy(o.path, 0, resolved, path.length, o.path.length);
        } else {
            resolved = new byte[path.length + 1 + o.path.length];
            System.arraycopy(path, 0, resolved, 0, path.length);
            resolved[path.length] = '/';
            System.arraycopy(o.path, 0, resolved, path.length + 1, o.path.length);
        }
        return new ZipPath(zfs, resolved);
    }

    @Override
    public ZipPath resolve(String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @Override
    public boolean startsWith(Path other) {
        final ZipPath o = checkPath(other);
        if (o.isAbsolute() != this.isAbsolute())
            return false;
        final int oCount = o.getNameCount();
        if (getNameCount() < oCount)
            return false;
        for (int i = 0; i < oCount; i++) {
            if (!o.getName(i).equals(getName(i)))
                return false;
        }
        return true;
    }

    @Override
    public boolean endsWith(Path other) {
        final ZipPath o = checkPath(other);
        if (o.isAbsolute())
            return this.isAbsolute() ? this.equals(o) : false;
        int i = o.getNameCount();
        int j = this.getNameCount();
        if (j < i)
            return false;
        for (--i, --j; i >= 0; i--, j--) {
            if (!o.getName(i).equals(this.getName(j)))
                return false;
        }
        return true;
    }

    @Override
    public Path normalize() {
        byte[] resolved = getResolved();
        if (resolved == path)    // no change
            return this;
        if (resolved.length == 0)
            return null;
        return new ZipPath(zfs, resolved, true);
    }

    private ZipPath checkPath(Path path) {
        if (path == null)
            throw new NullPointerException();
        if (!(path instanceof ZipPath))
            throw new ProviderMismatchException();
        return (ZipPath) path;
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

    // resolved path for locating zip entry inside the zip file,
    // the result path does not contain ./ and .. components
    private volatile byte[] resolved = null;
    byte[] getResolvedPath() {
        byte[] r = resolved;
        if (r == null) {
            if (isAbsolute())
                r = getResolved();
            else
                r = toAbsolutePath().getResolvedPath();
            if (r[0] == '/')
                r = Arrays.copyOfRange(r, 1, r.length);
            resolved = r;
        }
        return resolved;
    }

    // removes redundant slashs, replace "\" to zip separator "/"
    // and check for invalid characters
    private byte[] normalize(byte[] path) {
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
                throw new InvalidPathException(zfs.getString(path),
                                               "Path: nul character not allowed");
            prevC = c;
        }
        return path;
    }

    private byte[] normalize(byte[] path, int off) {
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
                throw new InvalidPathException(zfs.getString(path),
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
            if (len == 1 && path[n] == (byte)'.')
                continue;
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
        return zfs.getString(path);
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
               obj instanceof ZipPath &&
               this.zfs == ((ZipPath)obj).zfs &&
               compareTo((Path) obj) == 0;
    }

    @Override
    public int compareTo(Path other) {
        final ZipPath o = checkPath(other);
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
    public Path createSymbolicLink(
            Path target, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Path createLink(
            Path existing) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Path readSymbolicLink() throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Path createDirectory(FileAttribute<?>... attrs)
        throws IOException
    {
        zfs.createDirectory(getResolvedPath(), attrs);
        return this;
    }

    public final Path createFile(FileAttribute<?>... attrs)
        throws IOException
    {
        OutputStream os = newOutputStream(CREATE_NEW, WRITE);
        try {
            os.close();
        } catch (IOException x) {}
        return this;
    }

    @Override
    public InputStream newInputStream(OpenOption... options)
            throws IOException {
        if (options.length > 0) {
            for (OpenOption opt : options) {
                if (opt != READ)
                    throw new UnsupportedOperationException("'" + opt + "' not allowed");
            }
        }
        return zfs.newInputStream(getResolvedPath());
    }

    private static final DirectoryStream.Filter<Path> acceptAllFilter =
        new DirectoryStream.Filter<Path>() {
            @Override public boolean accept(Path entry) { return true; }
        };

    @Override
    public final DirectoryStream<Path> newDirectoryStream() throws IOException {
        return newDirectoryStream(acceptAllFilter);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Filter<? super Path> filter)
        throws IOException
    {
        return new ZipDirectoryStream(this, filter);
    }

    @Override
    public final DirectoryStream<Path> newDirectoryStream(String glob)
        throws IOException
    {
        // avoid creating a matcher if all entries are required.
        if (glob.equals("*"))
            return newDirectoryStream();

        // create a matcher and return a filter that uses it.
        final PathMatcher matcher = getFileSystem().getPathMatcher("glob:" + glob);
        DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry)  {
                return matcher.matches(entry.getName());
            }
        };
        return newDirectoryStream(filter);
    }

    @Override
    public final void delete() throws IOException {
        zfs.deleteFile(getResolvedPath(), true);
    }

    @Override
    public final void deleteIfExists() throws IOException {
        zfs.deleteFile(getResolvedPath(), false);
    }

    ZipFileAttributes getAttributes() throws IOException
    {
        ZipFileAttributes zfas = zfs.getFileAttributes(getResolvedPath());
        if (zfas == null)
            throw new NoSuchFileException(toString());
        return zfas;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Class<V> type,
                                                                LinkOption... options)
    {
        return (V)ZipFileAttributeView.get(this, type);
    }

    @Override
    public void setAttribute(String attribute,
                             Object value,
                             LinkOption... options)
        throws IOException
    {
        String type = null;
        String attr = null;
        int colonPos = attribute.indexOf(':');
        if (colonPos == -1) {
            type = "basic";
            attr = attribute;
        } else {
            type = attribute.substring(0, colonPos++);
            attr = attribute.substring(colonPos);
        }
        ZipFileAttributeView view = ZipFileAttributeView.get(this, type);
        if (view == null)
            throw new UnsupportedOperationException("view <" + view + "> is not supported");
        view.setAttribute(attr, value);
    }

    void setTimes(FileTime mtime, FileTime atime, FileTime ctime)
        throws IOException
    {
        zfs.setTimes(getResolvedPath(), mtime, atime, ctime);
    }

    private Object getAttributesImpl(String attribute, boolean domap)
        throws IOException
    {
        String view = null;
        String attr = null;
        int colonPos = attribute.indexOf(':');
        if (colonPos == -1) {
            view = "basic";
            attr = attribute;
        } else {
            view = attribute.substring(0, colonPos++);
            attr = attribute.substring(colonPos);
        }
        ZipFileAttributeView zfv = ZipFileAttributeView.get(this, view);
        if (zfv == null) {
            throw new UnsupportedOperationException("view not supported");
        }
        return zfv.getAttribute(attr, domap);
    }

    @Override
    public Object getAttribute(String attribute, LinkOption... options)
        throws IOException
    {
        return getAttributesImpl(attribute, false);
    }

    @Override
    public Map<String,?> readAttributes(String attribute, LinkOption... options)
        throws IOException
    {
        return (Map<String, ?>)getAttributesImpl(attribute, true);
    }

    @Override
    public FileStore getFileStore() throws IOException {
        // each ZipFileSystem only has one root (as requested for now)
        if (exists())
            return zfs.getFileStore(this);
        throw new NoSuchFileException(zfs.getString(path));
    }

    @Override
    public boolean isSameFile(Path other) throws IOException {
        if (other == null ||
            this.getFileSystem() != other.getFileSystem())
            return false;
        this.checkAccess();
        other.checkAccess();
        return Arrays.equals(this.getResolvedPath(),
                             ((ZipPath)other).getResolvedPath());
    }

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

    @Override
    public SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs)
        throws IOException
    {
        return zfs.newByteChannel(getResolvedPath(), options, attrs);
    }


    FileChannel newFileChannel(Set<? extends OpenOption> options,
                               FileAttribute<?>... attrs)
        throws IOException
    {
        return zfs.newFileChannel(getResolvedPath(), options, attrs);
    }

    @Override
    public SeekableByteChannel newByteChannel(OpenOption... options)
            throws IOException {
        Set<OpenOption> set = new HashSet<OpenOption>(options.length);
        Collections.addAll(set, options);
        return newByteChannel(set);
    }

    @Override
    public void checkAccess(AccessMode... modes) throws IOException {
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
        ZipFileAttributes attrs = zfs.getFileAttributes(getResolvedPath());
        if (attrs == null && (path.length != 1 || path[0] != '/'))
            throw new NoSuchFileException(toString());
        if (w) {
            if (zfs.isReadOnly())
                throw new AccessDeniedException(toString());
        }
        if (x)
            throw new AccessDeniedException(toString());

    }

    @Override
    public boolean exists() {
        if (path.length == 1 && path[0] == '/')
            return true;
        try {
            return zfs.exists(getResolvedPath());
        } catch (IOException x) {}
        return false;
    }

    @Override
    public boolean notExists() {
        return !exists();
    }


    @Override
    public OutputStream newOutputStream(OpenOption... options)
        throws IOException
    {
        if (options.length == 0)
            return zfs.newOutputStream(getResolvedPath(),
                                       CREATE_NEW, WRITE);
        return zfs.newOutputStream(getResolvedPath(), options);
    }

    @Override
    public Path moveTo(Path target, CopyOption... options)
        throws IOException
    {
        if (this.zfs.provider() == target.getFileSystem().provider() &&
            this.zfs.getZipFile().isSameFile(((ZipPath)target).zfs.getZipFile()))
        {
            zfs.copyFile(true,
                         getResolvedPath(),
                         ((ZipPath)target).getResolvedPath(),
                         options);
        } else {
            copyToTarget(target, options);
            delete();
        }
        return target;
    }

    @Override
    public Path copyTo(Path target, CopyOption... options)
        throws IOException
    {
        if (this.zfs.provider() == target.getFileSystem().provider() &&
            this.zfs.getZipFile().isSameFile(((ZipPath)target).zfs.getZipFile()))
        {
            zfs.copyFile(false,
                         getResolvedPath(),
                         ((ZipPath)target).getResolvedPath(),
                         options);
        } else {
            copyToTarget(target, options);
        }
        return target;
    }

    private void copyToTarget(Path target, CopyOption... options)
        throws IOException
    {
        boolean replaceExisting = false;
        boolean copyAttrs = false;
        for (CopyOption opt : options) {
            if (opt == REPLACE_EXISTING)
                replaceExisting = true;
            else if (opt == COPY_ATTRIBUTES)
                copyAttrs = false;
        }
        // attributes of source file
        ZipFileAttributes zfas = getAttributes();
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

        if (zfas.isDirectory()) {
            // create directory or file
            target.createDirectory();
        } else {
            InputStream is = zfs.newInputStream(getResolvedPath());
            try {
                OutputStream os = target.newOutputStream();
                try {
                    byte[] buf = new byte[8192];
                    int n = 0;
                    while ((n = is.read(buf)) != -1) {
                        os.write(buf, 0, n);
                    }
                } finally {
                    os.close();
                }
            } finally {
                is.close();
            }
        }
        if (copyAttrs) {
            BasicFileAttributeView view =
                target.getFileAttributeView(BasicFileAttributeView.class);
            try {
                view.setTimes(zfas.lastModifiedTime(), null, null);
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
