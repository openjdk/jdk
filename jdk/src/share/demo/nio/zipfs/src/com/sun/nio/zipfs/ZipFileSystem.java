/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.nio.zipfs;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipException;
import java.util.zip.ZipError;
import static java.lang.Boolean.*;
import static com.sun.nio.zipfs.ZipConstants.*;
import static com.sun.nio.zipfs.ZipUtils.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

/**
 * A FileSystem built on a zip file
 *
 * @author Xueming Shen
 */

public class ZipFileSystem extends FileSystem {

    private final ZipFileSystemProvider provider;
    private final ZipPath defaultdir;
    private boolean readOnly = false;
    private final Path zfpath;
    private final ZipCoder zc;

    // configurable by env map
    private final String  defaultDir;    // default dir for the file system
    private final String  nameEncoding;  // default encoding for name/comment
    private final boolean useTempFile;   // use a temp file for newOS, default
                                         // is to use BAOS for better performance
    private final boolean createNew;     // create a new zip if not exists
    private static final boolean isWindows =
        System.getProperty("os.name").startsWith("Windows");

    ZipFileSystem(ZipFileSystemProvider provider,
                  Path zfpath,
                  Map<String, ?> env)
        throws IOException
    {
        // configurable env setup
        this.createNew    = "true".equals(env.get("create"));
        this.nameEncoding = env.containsKey("encoding") ?
                            (String)env.get("encoding") : "UTF-8";
        this.useTempFile  = TRUE.equals(env.get("useTempFile"));
        this.defaultDir   = env.containsKey("default.dir") ?
                            (String)env.get("default.dir") : "/";
        if (this.defaultDir.charAt(0) != '/')
            throw new IllegalArgumentException("default dir should be absolute");

        this.provider = provider;
        this.zfpath = zfpath;
        if (Files.notExists(zfpath)) {
            if (createNew) {
                try (OutputStream os = Files.newOutputStream(zfpath, CREATE_NEW, WRITE)) {
                    new END().write(os, 0);
                }
            } else {
                throw new FileSystemNotFoundException(zfpath.toString());
            }
        }
        // sm and existence check
        zfpath.getFileSystem().provider().checkAccess(zfpath, AccessMode.READ);
        if (!Files.isWritable(zfpath))
            this.readOnly = true;
        this.zc = ZipCoder.get(nameEncoding);
        this.defaultdir = new ZipPath(this, getBytes(defaultDir));
        this.ch = Files.newByteChannel(zfpath, READ);
        this.cen = initCEN();
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    private void checkWritable() throws IOException {
        if (readOnly)
            throw new ReadOnlyFileSystemException();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        ArrayList<Path> pathArr = new ArrayList<>();
        pathArr.add(new ZipPath(this, new byte[]{'/'}));
        return pathArr;
    }

    ZipPath getDefaultDir() {  // package private
        return defaultdir;
    }

    @Override
    public ZipPath getPath(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment: more) {
                if (segment.length() > 0) {
                    if (sb.length() > 0)
                        sb.append('/');
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return new ZipPath(this, getBytes(path));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    FileStore getFileStore(ZipPath path) {
        return new ZipFileStore(path);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        ArrayList<FileStore> list = new ArrayList<>(1);
        list.add(new ZipFileStore(new ZipPath(this, new byte[]{'/'})));
        return list;
    }

    private static final Set<String> supportedFileAttributeViews =
            Collections.unmodifiableSet(
                new HashSet<String>(Arrays.asList("basic", "zip")));

    @Override
    public Set<String> supportedFileAttributeViews() {
        return supportedFileAttributeViews;
    }

    @Override
    public String toString() {
        return zfpath.toString();
    }

    Path getZipFile() {
        return zfpath;
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0 || pos == syntaxAndInput.length()) {
            throw new IllegalArgumentException();
        }
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos + 1);
        String expr;
        if (syntax.equals(GLOB_SYNTAX)) {
            expr = toRegexPattern(input);
        } else {
            if (syntax.equals(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                    "' not recognized");
            }
        }
        // return matcher
        final Pattern pattern = Pattern.compile(expr);
        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return pattern.matcher(path.toString()).matches();
            }
        };
    }

    @Override
    public void close() throws IOException {
        beginWrite();
        try {
            if (!isOpen)
                return;
            isOpen = false;             // set closed
        } finally {
            endWrite();
        }
        if (!streams.isEmpty()) {       // unlock and close all remaining streams
            Set<InputStream> copy = new HashSet<>(streams);
            for (InputStream is: copy)
                is.close();
        }
        beginWrite();                   // lock and sync
        try {
            sync();
            ch.close();                 // close the ch just in case no update
        } finally {                     // and sync dose not close the ch
            endWrite();
        }

        synchronized (inflaters) {
            for (Inflater inf : inflaters)
                inf.end();
        }
        synchronized (deflaters) {
            for (Deflater def : deflaters)
                def.end();
        }

        IOException ioe = null;
        synchronized (tmppaths) {
            for (Path p: tmppaths) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException x) {
                    if (ioe == null)
                        ioe = x;
                    else
                        ioe.addSuppressed(x);
                }
            }
        }
        provider.removeFileSystem(zfpath, this);
        if (ioe != null)
           throw ioe;
    }

    ZipFileAttributes getFileAttributes(byte[] path)
        throws IOException
    {
        Entry e;
        beginRead();
        try {
            ensureOpen();
            e = getEntry0(path);
            if (e == null) {
                IndexNode inode = getInode(path);
                if (inode == null)
                    return null;
                e = new Entry(inode.name);       // pseudo directory
                e.method = METHOD_STORED;        // STORED for dir
                e.mtime = e.atime = e.ctime = -1;// -1 for all times
            }
        } finally {
            endRead();
        }
        return new ZipFileAttributes(e);
    }

    void setTimes(byte[] path, FileTime mtime, FileTime atime, FileTime ctime)
        throws IOException
    {
        checkWritable();
        beginWrite();
        try {
            ensureOpen();
            Entry e = getEntry0(path);    // ensureOpen checked
            if (e == null)
                throw new NoSuchFileException(getString(path));
            if (e.type == Entry.CEN)
                e.type = Entry.COPY;      // copy e
            if (mtime != null)
                e.mtime = mtime.toMillis();
            if (atime != null)
                e.atime = atime.toMillis();
            if (ctime != null)
                e.ctime = ctime.toMillis();
            update(e);
        } finally {
            endWrite();
        }
    }

    boolean exists(byte[] path)
        throws IOException
    {
        beginRead();
        try {
            ensureOpen();
            return getInode(path) != null;
        } finally {
            endRead();
        }
    }

    boolean isDirectory(byte[] path)
        throws IOException
    {
        beginRead();
        try {
            IndexNode n = getInode(path);
            return n != null && n.isDir();
        } finally {
            endRead();
        }
    }

    private ZipPath toZipPath(byte[] path) {
        // make it absolute
        byte[] p = new byte[path.length + 1];
        p[0] = '/';
        System.arraycopy(path, 0, p, 1, path.length);
        return new ZipPath(this, p);
    }

    // returns the list of child paths of "path"
    Iterator<Path> iteratorOf(byte[] path,
                              DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        beginWrite();    // iteration of inodes needs exclusive lock
        try {
            ensureOpen();
            IndexNode inode = getInode(path);
            if (inode == null)
                throw new NotDirectoryException(getString(path));
            List<Path> list = new ArrayList<>();
            IndexNode child = inode.child;
            while (child != null) {
                ZipPath zp = toZipPath(child.name);
                if (filter == null || filter.accept(zp))
                    list.add(zp);
                child = child.sibling;
            }
            return list.iterator();
        } finally {
            endWrite();
        }
    }

    void createDirectory(byte[] dir, FileAttribute<?>... attrs)
        throws IOException
    {
        checkWritable();
        dir = toDirectoryPath(dir);
        beginWrite();
        try {
            ensureOpen();
            if (dir.length == 0 || exists(dir))  // root dir, or exiting dir
                throw new FileAlreadyExistsException(getString(dir));
            checkParents(dir);
            Entry e = new Entry(dir, Entry.NEW);
            e.method = METHOD_STORED;            // STORED for dir
            update(e);
        } finally {
            endWrite();
        }
    }

    void copyFile(boolean deletesrc, byte[]src, byte[] dst, CopyOption... options)
        throws IOException
    {
        checkWritable();
        if (Arrays.equals(src, dst))
            return;    // do nothing, src and dst are the same

        beginWrite();
        try {
            ensureOpen();
            Entry eSrc = getEntry0(src);  // ensureOpen checked
            if (eSrc == null)
                throw new NoSuchFileException(getString(src));
            if (eSrc.isDir()) {    // spec says to create dst dir
                createDirectory(dst);
                return;
            }
            boolean hasReplace = false;
            boolean hasCopyAttrs = false;
            for (CopyOption opt : options) {
                if (opt == REPLACE_EXISTING)
                    hasReplace = true;
                else if (opt == COPY_ATTRIBUTES)
                    hasCopyAttrs = true;
            }
            Entry eDst = getEntry0(dst);
            if (eDst != null) {
                if (!hasReplace)
                    throw new FileAlreadyExistsException(getString(dst));
            } else {
                checkParents(dst);
            }
            Entry u = new Entry(eSrc, Entry.COPY);    // copy eSrc entry
            u.name(dst);                              // change name
            if (eSrc.type == Entry.NEW || eSrc.type == Entry.FILECH)
            {
                u.type = eSrc.type;    // make it the same type
                if (deletesrc) {       // if it's a "rename", take the data
                    u.bytes = eSrc.bytes;
                    u.file = eSrc.file;
                } else {               // if it's not "rename", copy the data
                    if (eSrc.bytes != null)
                        u.bytes = Arrays.copyOf(eSrc.bytes, eSrc.bytes.length);
                    else if (eSrc.file != null) {
                        u.file = getTempPathForEntry(null);
                        Files.copy(eSrc.file, u.file, REPLACE_EXISTING);
                    }
                }
            }
            if (!hasCopyAttrs)
                u.mtime = u.atime= u.ctime = System.currentTimeMillis();
            update(u);
            if (deletesrc)
                updateDelete(eSrc);
        } finally {
            endWrite();
        }
    }

    // Returns an output stream for writing the contents into the specified
    // entry.
    OutputStream newOutputStream(byte[] path, OpenOption... options)
        throws IOException
    {
        checkWritable();
        boolean hasCreateNew = false;
        boolean hasCreate = false;
        boolean hasAppend = false;
        for (OpenOption opt: options) {
            if (opt == READ)
                throw new IllegalArgumentException("READ not allowed");
            if (opt == CREATE_NEW)
                hasCreateNew = true;
            if (opt == CREATE)
                hasCreate = true;
            if (opt == APPEND)
                hasAppend = true;
        }
        beginRead();                 // only need a readlock, the "update()" will
        try {                        // try to obtain a writelock when the os is
            ensureOpen();            // being closed.
            Entry e = getEntry0(path);
            if (e != null) {
                if (e.isDir() || hasCreateNew)
                    throw new FileAlreadyExistsException(getString(path));
                if (hasAppend) {
                    InputStream is = getInputStream(e);
                    OutputStream os = getOutputStream(new Entry(e, Entry.NEW));
                    copyStream(is, os);
                    is.close();
                    return os;
                }
                return getOutputStream(new Entry(e, Entry.NEW));
            } else {
                if (!hasCreate && !hasCreateNew)
                    throw new NoSuchFileException(getString(path));
                checkParents(path);
                return getOutputStream(new Entry(path, Entry.NEW));
            }
        } finally {
            endRead();
        }
    }

    // Returns an input stream for reading the contents of the specified
    // file entry.
    InputStream newInputStream(byte[] path) throws IOException {
        beginRead();
        try {
            ensureOpen();
            Entry e = getEntry0(path);
            if (e == null)
                throw new NoSuchFileException(getString(path));
            if (e.isDir())
                throw new FileSystemException(getString(path), "is a directory", null);
            return getInputStream(e);
        } finally {
            endRead();
        }
    }

    private void checkOptions(Set<? extends OpenOption> options) {
        // check for options of null type and option is an intance of StandardOpenOption
        for (OpenOption option : options) {
            if (option == null)
                throw new NullPointerException();
            if (!(option instanceof StandardOpenOption))
                throw new IllegalArgumentException();
        }
    }

    // Returns a Writable/ReadByteChannel for now. Might consdier to use
    // newFileChannel() instead, which dump the entry data into a regular
    // file on the default file system and create a FileChannel on top of
    // it.
    SeekableByteChannel newByteChannel(byte[] path,
                                       Set<? extends OpenOption> options,
                                       FileAttribute<?>... attrs)
        throws IOException
    {
        checkOptions(options);
        if (options.contains(StandardOpenOption.WRITE) ||
            options.contains(StandardOpenOption.APPEND)) {
            checkWritable();
            beginRead();
            try {
                final WritableByteChannel wbc = Channels.newChannel(
                    newOutputStream(path, options.toArray(new OpenOption[0])));
                long leftover = 0;
                if (options.contains(StandardOpenOption.APPEND)) {
                    Entry e = getEntry0(path);
                    if (e != null && e.size >= 0)
                        leftover = e.size;
                }
                final long offset = leftover;
                return new SeekableByteChannel() {
                    long written = offset;
                    public boolean isOpen() {
                        return wbc.isOpen();
                    }

                    public long position() throws IOException {
                        return written;
                    }

                    public SeekableByteChannel position(long pos)
                        throws IOException
                    {
                        throw new UnsupportedOperationException();
                    }

                    public int read(ByteBuffer dst) throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    public SeekableByteChannel truncate(long size)
                        throws IOException
                    {
                        throw new UnsupportedOperationException();
                    }

                    public int write(ByteBuffer src) throws IOException {
                        int n = wbc.write(src);
                        written += n;
                        return n;
                    }

                    public long size() throws IOException {
                        return written;
                    }

                    public void close() throws IOException {
                        wbc.close();
                    }
                };
            } finally {
                endRead();
            }
        } else {
            beginRead();
            try {
                ensureOpen();
                Entry e = getEntry0(path);
                if (e == null || e.isDir())
                    throw new NoSuchFileException(getString(path));
                final ReadableByteChannel rbc =
                    Channels.newChannel(getInputStream(e));
                final long size = e.size;
                return new SeekableByteChannel() {
                    long read = 0;
                    public boolean isOpen() {
                        return rbc.isOpen();
                    }

                    public long position() throws IOException {
                        return read;
                    }

                    public SeekableByteChannel position(long pos)
                        throws IOException
                    {
                        throw new UnsupportedOperationException();
                    }

                    public int read(ByteBuffer dst) throws IOException {
                        int n = rbc.read(dst);
                        if (n > 0) {
                            read += n;
                        }
                        return n;
                    }

                    public SeekableByteChannel truncate(long size)
                    throws IOException
                    {
                        throw new NonWritableChannelException();
                    }

                    public int write (ByteBuffer src) throws IOException {
                        throw new NonWritableChannelException();
                    }

                    public long size() throws IOException {
                        return size;
                    }

                    public void close() throws IOException {
                        rbc.close();
                    }
                };
            } finally {
                endRead();
            }
        }
    }

    // Returns a FileChannel of the specified entry.
    //
    // This implementation creates a temporary file on the default file system,
    // copy the entry data into it if the entry exists, and then create a
    // FileChannel on top of it.
    FileChannel newFileChannel(byte[] path,
                               Set<? extends OpenOption> options,
                               FileAttribute<?>... attrs)
        throws IOException
    {
        checkOptions(options);
        final  boolean forWrite = (options.contains(StandardOpenOption.WRITE) ||
                                   options.contains(StandardOpenOption.APPEND));
        beginRead();
        try {
            ensureOpen();
            Entry e = getEntry0(path);
            if (forWrite) {
                checkWritable();
                if (e == null) {
                if (!options.contains(StandardOpenOption.CREATE_NEW))
                    throw new NoSuchFileException(getString(path));
                } else {
                    if (options.contains(StandardOpenOption.CREATE_NEW))
                        throw new FileAlreadyExistsException(getString(path));
                    if (e.isDir())
                        throw new FileAlreadyExistsException("directory <"
                            + getString(path) + "> exists");
                }
                options.remove(StandardOpenOption.CREATE_NEW); // for tmpfile
            } else if (e == null || e.isDir()) {
                throw new NoSuchFileException(getString(path));
            }

            final boolean isFCH = (e != null && e.type == Entry.FILECH);
            final Path tmpfile = isFCH ? e.file : getTempPathForEntry(path);
            final FileChannel fch = tmpfile.getFileSystem()
                                           .provider()
                                           .newFileChannel(tmpfile, options, attrs);
            final Entry u = isFCH ? e : new Entry(path, tmpfile, Entry.FILECH);
            if (forWrite) {
                u.flag = FLAG_DATADESCR;
                u.method = METHOD_DEFLATED;
            }
            // is there a better way to hook into the FileChannel's close method?
            return new FileChannel() {
                public int write(ByteBuffer src) throws IOException {
                    return fch.write(src);
                }
                public long write(ByteBuffer[] srcs, int offset, int length)
                    throws IOException
                {
                    return fch.write(srcs, offset, length);
                }
                public long position() throws IOException {
                    return fch.position();
                }
                public FileChannel position(long newPosition)
                    throws IOException
                {
                    fch.position(newPosition);
                    return this;
                }
                public long size() throws IOException {
                    return fch.size();
                }
                public FileChannel truncate(long size)
                    throws IOException
                {
                    fch.truncate(size);
                    return this;
                }
                public void force(boolean metaData)
                    throws IOException
                {
                    fch.force(metaData);
                }
                public long transferTo(long position, long count,
                                       WritableByteChannel target)
                    throws IOException
                {
                    return fch.transferTo(position, count, target);
                }
                public long transferFrom(ReadableByteChannel src,
                                         long position, long count)
                    throws IOException
                {
                    return fch.transferFrom(src, position, count);
                }
                public int read(ByteBuffer dst) throws IOException {
                    return fch.read(dst);
                }
                public int read(ByteBuffer dst, long position)
                    throws IOException
                {
                    return fch.read(dst, position);
                }
                public long read(ByteBuffer[] dsts, int offset, int length)
                    throws IOException
                {
                    return fch.read(dsts, offset, length);
                }
                public int write(ByteBuffer src, long position)
                    throws IOException
                    {
                   return fch.write(src, position);
                }
                public MappedByteBuffer map(MapMode mode,
                                            long position, long size)
                    throws IOException
                {
                    throw new UnsupportedOperationException();
                }
                public FileLock lock(long position, long size, boolean shared)
                    throws IOException
                {
                    return fch.lock(position, size, shared);
                }
                public FileLock tryLock(long position, long size, boolean shared)
                    throws IOException
                {
                    return fch.tryLock(position, size, shared);
                }
                protected void implCloseChannel() throws IOException {
                    fch.close();
                    if (forWrite) {
                        u.mtime = System.currentTimeMillis();
                        u.size = Files.size(u.file);

                        update(u);
                    } else {
                        if (!isFCH)    // if this is a new fch for reading
                            removeTempPathForEntry(tmpfile);
                    }
               }
            };
        } finally {
            endRead();
        }
    }

    // the outstanding input streams that need to be closed
    private Set<InputStream> streams =
        Collections.synchronizedSet(new HashSet<InputStream>());

    // the ex-channel and ex-path that need to close when their outstanding
    // input streams are all closed by the obtainers.
    private Set<ExChannelCloser> exChClosers = new HashSet<>();

    private Set<Path> tmppaths = Collections.synchronizedSet(new HashSet<Path>());
    private Path getTempPathForEntry(byte[] path) throws IOException {
        Path tmpPath = createTempFileInSameDirectoryAs(zfpath);
        if (path != null) {
            Entry e = getEntry0(path);
            if (e != null) {
                try (InputStream is = newInputStream(path)) {
                    Files.copy(is, tmpPath, REPLACE_EXISTING);
                }
            }
        }
        return tmpPath;
    }

    private void removeTempPathForEntry(Path path) throws IOException {
        Files.delete(path);
        tmppaths.remove(path);
    }

    // check if all parents really exit. ZIP spec does not require
    // the existence of any "parent directory".
    private void checkParents(byte[] path) throws IOException {
        beginRead();
        try {
            while ((path = getParent(path)) != null && path.length != 0) {
                if (!inodes.containsKey(IndexNode.keyOf(path))) {
                    throw new NoSuchFileException(getString(path));
                }
            }
        } finally {
            endRead();
        }
    }

    private static byte[] ROOTPATH = new byte[0];
    private static byte[] getParent(byte[] path) {
        int off = path.length - 1;
        if (off > 0 && path[off] == '/')  // isDirectory
            off--;
        while (off > 0 && path[off] != '/') { off--; }
        if (off <= 0)
            return ROOTPATH;
        return Arrays.copyOf(path, off + 1);
    }

    private final void beginWrite() {
        rwlock.writeLock().lock();
    }

    private final void endWrite() {
        rwlock.writeLock().unlock();
    }

    private final void beginRead() {
        rwlock.readLock().lock();
    }

    private final void endRead() {
        rwlock.readLock().unlock();
    }

    ///////////////////////////////////////////////////////////////////

    private volatile boolean isOpen = true;
    private final SeekableByteChannel ch; // channel to the zipfile
    final byte[]  cen;     // CEN & ENDHDR
    private END  end;
    private long locpos;   // position of first LOC header (usually 0)

    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    // name -> pos (in cen), IndexNode itself can be used as a "key"
    private LinkedHashMap<IndexNode, IndexNode> inodes;

    final byte[] getBytes(String name) {
        return zc.getBytes(name);
    }

    final String getString(byte[] name) {
        return zc.toString(name);
    }

    protected void finalize() throws IOException {
        close();
    }

    private long getDataPos(Entry e) throws IOException {
        if (e.locoff == -1) {
            Entry e2 = getEntry0(e.name);
            if (e2 == null)
                throw new ZipException("invalid loc for entry <" + e.name + ">");
            e.locoff = e2.locoff;
        }
        byte[] buf = new byte[LOCHDR];
        if (readFullyAt(buf, 0, buf.length, e.locoff) != buf.length)
            throw new ZipException("invalid loc for entry <" + e.name + ">");
        return locpos + e.locoff + LOCHDR + LOCNAM(buf) + LOCEXT(buf);
    }

    // Reads len bytes of data from the specified offset into buf.
    // Returns the total number of bytes read.
    // Each/every byte read from here (except the cen, which is mapped).
    final long readFullyAt(byte[] buf, int off, long len, long pos)
        throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(buf);
        bb.position(off);
        bb.limit((int)(off + len));
        return readFullyAt(bb, pos);
    }

    private final long readFullyAt(ByteBuffer bb, long pos)
        throws IOException
    {
        synchronized(ch) {
            return ch.position(pos).read(bb);
        }
    }

    // Searches for end of central directory (END) header. The contents of
    // the END header will be read and placed in endbuf. Returns the file
    // position of the END header, otherwise returns -1 if the END header
    // was not found or an error occurred.
    private END findEND() throws IOException
    {
        byte[] buf = new byte[READBLOCKSZ];
        long ziplen = ch.size();
        long minHDR = (ziplen - END_MAXLEN) > 0 ? ziplen - END_MAXLEN : 0;
        long minPos = minHDR - (buf.length - ENDHDR);

        for (long pos = ziplen - buf.length; pos >= minPos; pos -= (buf.length - ENDHDR))
        {
            int off = 0;
            if (pos < 0) {
                // Pretend there are some NUL bytes before start of file
                off = (int)-pos;
                Arrays.fill(buf, 0, off, (byte)0);
            }
            int len = buf.length - off;
            if (readFullyAt(buf, off, len, pos + off) != len)
                zerror("zip END header not found");

            // Now scan the block backwards for END header signature
            for (int i = buf.length - ENDHDR; i >= 0; i--) {
                if (buf[i+0] == (byte)'P'    &&
                    buf[i+1] == (byte)'K'    &&
                    buf[i+2] == (byte)'\005' &&
                    buf[i+3] == (byte)'\006' &&
                    (pos + i + ENDHDR + ENDCOM(buf, i) == ziplen)) {
                    // Found END header
                    buf = Arrays.copyOfRange(buf, i, i + ENDHDR);
                    END end = new END();
                    end.endsub = ENDSUB(buf);
                    end.centot = ENDTOT(buf);
                    end.cenlen = ENDSIZ(buf);
                    end.cenoff = ENDOFF(buf);
                    end.comlen = ENDCOM(buf);
                    end.endpos = pos + i;
                    if (end.cenlen == ZIP64_MINVAL ||
                        end.cenoff == ZIP64_MINVAL ||
                        end.centot == ZIP64_MINVAL32)
                    {
                        // need to find the zip64 end;
                        byte[] loc64 = new byte[ZIP64_LOCHDR];
                        if (readFullyAt(loc64, 0, loc64.length, end.endpos - ZIP64_LOCHDR)
                            != loc64.length) {
                            return end;
                        }
                        long end64pos = ZIP64_LOCOFF(loc64);
                        byte[] end64buf = new byte[ZIP64_ENDHDR];
                        if (readFullyAt(end64buf, 0, end64buf.length, end64pos)
                            != end64buf.length) {
                            return end;
                        }
                        // end64 found, re-calcualte everything.
                        end.cenlen = ZIP64_ENDSIZ(end64buf);
                        end.cenoff = ZIP64_ENDOFF(end64buf);
                        end.centot = (int)ZIP64_ENDTOT(end64buf); // assume total < 2g
                        end.endpos = end64pos;
                    }
                    return end;
                }
            }
        }
        zerror("zip END header not found");
        return null; //make compiler happy
    }

    // Reads zip file central directory. Returns the file position of first
    // CEN header, otherwise returns -1 if an error occurred. If zip->msg != NULL
    // then the error was a zip format error and zip->msg has the error text.
    // Always pass in -1 for knownTotal; it's used for a recursive call.
    private byte[] initCEN() throws IOException {
        end = findEND();
        if (end.endpos == 0) {
            inodes = new LinkedHashMap<>(10);
            locpos = 0;
            buildNodeTree();
            return null;         // only END header present
        }
        if (end.cenlen > end.endpos)
            zerror("invalid END header (bad central directory size)");
        long cenpos = end.endpos - end.cenlen;     // position of CEN table

        // Get position of first local file (LOC) header, taking into
        // account that there may be a stub prefixed to the zip file.
        locpos = cenpos - end.cenoff;
        if (locpos < 0)
            zerror("invalid END header (bad central directory offset)");

        // read in the CEN and END
        byte[] cen = new byte[(int)(end.cenlen + ENDHDR)];
        if (readFullyAt(cen, 0, cen.length, cenpos) != end.cenlen + ENDHDR) {
            zerror("read CEN tables failed");
        }
        // Iterate through the entries in the central directory
        inodes = new LinkedHashMap<>(end.centot + 1);
        int pos = 0;
        int limit = cen.length - ENDHDR;
        while (pos < limit) {
            if (CENSIG(cen, pos) != CENSIG)
                zerror("invalid CEN header (bad signature)");
            int method = CENHOW(cen, pos);
            int nlen   = CENNAM(cen, pos);
            int elen   = CENEXT(cen, pos);
            int clen   = CENCOM(cen, pos);
            if ((CENFLG(cen, pos) & 1) != 0)
                zerror("invalid CEN header (encrypted entry)");
            if (method != METHOD_STORED && method != METHOD_DEFLATED)
                zerror("invalid CEN header (unsupported compression method: " + method + ")");
            if (pos + CENHDR + nlen > limit)
                zerror("invalid CEN header (bad header size)");
            byte[] name = Arrays.copyOfRange(cen, pos + CENHDR, pos + CENHDR + nlen);
            IndexNode inode = new IndexNode(name, pos);
            inodes.put(inode, inode);
            // skip ext and comment
            pos += (CENHDR + nlen + elen + clen);
        }
        if (pos + ENDHDR != cen.length) {
            zerror("invalid CEN header (bad header size)");
        }
        buildNodeTree();
        return cen;
    }

    private void ensureOpen() throws IOException {
        if (!isOpen)
            throw new ClosedFileSystemException();
    }

    // Creates a new empty temporary file in the same directory as the
    // specified file.  A variant of Files.createTempFile.
    private Path createTempFileInSameDirectoryAs(Path path)
        throws IOException
    {
        Path parent = path.toAbsolutePath().getParent();
        Path dir = (parent == null) ? path.getFileSystem().getPath(".") : parent;
        Path tmpPath = Files.createTempFile(dir, "zipfstmp", null);
        tmppaths.add(tmpPath);
        return tmpPath;
    }

    ////////////////////update & sync //////////////////////////////////////

    private boolean hasUpdate = false;

    // shared key. consumer guarantees the "writeLock" before use it.
    private final IndexNode LOOKUPKEY = IndexNode.keyOf(null);

    private void updateDelete(IndexNode inode) {
        beginWrite();
        try {
            removeFromTree(inode);
            inodes.remove(inode);
            hasUpdate = true;
        } finally {
             endWrite();
        }
    }

    private void update(Entry e) {
        beginWrite();
        try {
            IndexNode old = inodes.put(e, e);
            if (old != null) {
                removeFromTree(old);
            }
            if (e.type == Entry.NEW || e.type == Entry.FILECH || e.type == Entry.COPY) {
                IndexNode parent = inodes.get(LOOKUPKEY.as(getParent(e.name)));
                e.sibling = parent.child;
                parent.child = e;
            }
            hasUpdate = true;
        } finally {
            endWrite();
        }
    }

    // copy over the whole LOC entry (header if necessary, data and ext) from
    // old zip to the new one.
    private long copyLOCEntry(Entry e, boolean updateHeader,
                              OutputStream os,
                              long written, byte[] buf)
        throws IOException
    {
        long locoff = e.locoff;  // where to read
        e.locoff = written;      // update the e.locoff with new value

        // calculate the size need to write out
        long size = 0;
        //  if there is A ext
        if ((e.flag & FLAG_DATADESCR) != 0) {
            if (e.size >= ZIP64_MINVAL || e.csize >= ZIP64_MINVAL)
                size = 24;
            else
                size = 16;
        }
        // read loc, use the original loc.elen/nlen
        if (readFullyAt(buf, 0, LOCHDR , locoff) != LOCHDR)
            throw new ZipException("loc: reading failed");
        if (updateHeader) {
            locoff += LOCHDR + LOCNAM(buf) + LOCEXT(buf);  // skip header
            size += e.csize;
            written = e.writeLOC(os) + size;
        } else {
            os.write(buf, 0, LOCHDR);    // write out the loc header
            locoff += LOCHDR;
            // use e.csize,  LOCSIZ(buf) is zero if FLAG_DATADESCR is on
            // size += LOCNAM(buf) + LOCEXT(buf) + LOCSIZ(buf);
            size += LOCNAM(buf) + LOCEXT(buf) + e.csize;
            written = LOCHDR + size;
        }
        int n;
        while (size > 0 &&
            (n = (int)readFullyAt(buf, 0, buf.length, locoff)) != -1)
        {
            if (size < n)
                n = (int)size;
            os.write(buf, 0, n);
            size -= n;
            locoff += n;
        }
        return written;
    }

    // sync the zip file system, if there is any udpate
    private void sync() throws IOException {
        //System.out.printf("->sync(%s) starting....!%n", toString());
        // check ex-closer
        if (!exChClosers.isEmpty()) {
            for (ExChannelCloser ecc : exChClosers) {
                if (ecc.streams.isEmpty()) {
                    ecc.ch.close();
                    Files.delete(ecc.path);
                    exChClosers.remove(ecc);
                }
            }
        }
        if (!hasUpdate)
            return;
        Path tmpFile = createTempFileInSameDirectoryAs(zfpath);
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmpFile, WRITE)))
        {
            ArrayList<Entry> elist = new ArrayList<>(inodes.size());
            long written = 0;
            byte[] buf = new byte[8192];
            Entry e = null;

            // write loc
            for (IndexNode inode : inodes.values()) {
                if (inode instanceof Entry) {    // an updated inode
                    e = (Entry)inode;
                    try {
                        if (e.type == Entry.COPY) {
                            // entry copy: the only thing changed is the "name"
                            // and "nlen" in LOC header, so we udpate/rewrite the
                            // LOC in new file and simply copy the rest (data and
                            // ext) without enflating/deflating from the old zip
                            // file LOC entry.
                            written += copyLOCEntry(e, true, os, written, buf);
                        } else {                          // NEW, FILECH or CEN
                            e.locoff = written;
                            written += e.writeLOC(os);    // write loc header
                            if (e.bytes != null) {        // in-memory, deflated
                                os.write(e.bytes);        // already
                                written += e.bytes.length;
                            } else if (e.file != null) {  // tmp file
                                try (InputStream is = Files.newInputStream(e.file)) {
                                    int n;
                                    if (e.type == Entry.NEW) {  // deflated already
                                        while ((n = is.read(buf)) != -1) {
                                            os.write(buf, 0, n);
                                            written += n;
                                        }
                                    } else if (e.type == Entry.FILECH) {
                                        // the data are not deflated, use ZEOS
                                        try (OutputStream os2 = new EntryOutputStream(e, os)) {
                                            while ((n = is.read(buf)) != -1) {
                                                os2.write(buf, 0, n);
                                            }
                                        }
                                        written += e.csize;
                                        if ((e.flag & FLAG_DATADESCR) != 0)
                                            written += e.writeEXT(os);
                                    }
                                }
                                Files.delete(e.file);
                                tmppaths.remove(e.file);
                            } else {
                                // dir, 0-length data
                            }
                        }
                        elist.add(e);
                    } catch (IOException x) {
                        x.printStackTrace();    // skip any in-accurate entry
                    }
                } else {                        // unchanged inode
                    if (inode.pos == -1) {
                        continue;               // pseudo directory node
                    }
                    e = Entry.readCEN(this, inode.pos);
                    try {
                        written += copyLOCEntry(e, false, os, written, buf);
                        elist.add(e);
                    } catch (IOException x) {
                        x.printStackTrace();    // skip any wrong entry
                    }
                }
            }

            // now write back the cen and end table
            end.cenoff = written;
            for (Entry entry : elist) {
                written += entry.writeCEN(os);
            }
            end.centot = elist.size();
            end.cenlen = written - end.cenoff;
            end.write(os, written);
        }
        if (!streams.isEmpty()) {
            //
            // TBD: ExChannelCloser should not be necessary if we only
            // sync when being closed, all streams should have been
            // closed already. Keep the logic here for now.
            //
            // There are outstanding input streams open on existing "ch",
            // so, don't close the "cha" and delete the "file for now, let
            // the "ex-channel-closer" to handle them
            ExChannelCloser ecc = new ExChannelCloser(
                                      createTempFileInSameDirectoryAs(zfpath),
                                      ch,
                                      streams);
            Files.move(zfpath, ecc.path, REPLACE_EXISTING);
            exChClosers.add(ecc);
            streams = Collections.synchronizedSet(new HashSet<InputStream>());
        } else {
            ch.close();
            Files.delete(zfpath);
        }

        Files.move(tmpFile, zfpath, REPLACE_EXISTING);
        hasUpdate = false;    // clear
        /*
        if (isOpen) {
            ch = zfpath.newByteChannel(READ); // re-fresh "ch" and "cen"
            cen = initCEN();
        }
         */
        //System.out.printf("->sync(%s) done!%n", toString());
    }

    private IndexNode getInode(byte[] path) {
        if (path == null)
            throw new NullPointerException("path");
        IndexNode key = IndexNode.keyOf(path);
        IndexNode inode = inodes.get(key);
        if (inode == null &&
            (path.length == 0 || path[path.length -1] != '/')) {
            // if does not ends with a slash
            path = Arrays.copyOf(path, path.length + 1);
            path[path.length - 1] = '/';
            inode = inodes.get(key.as(path));
        }
        return inode;
    }

    private Entry getEntry0(byte[] path) throws IOException {
        IndexNode inode = getInode(path);
        if (inode instanceof Entry)
            return (Entry)inode;
        if (inode == null || inode.pos == -1)
            return null;
        return Entry.readCEN(this, inode.pos);
    }

    public void deleteFile(byte[] path, boolean failIfNotExists)
        throws IOException
    {
        checkWritable();

        IndexNode inode = getInode(path);
        if (inode == null) {
            if (path != null && path.length == 0)
                throw new ZipException("root directory </> can't not be delete");
            if (failIfNotExists)
                throw new NoSuchFileException(getString(path));
        } else {
            if (inode.isDir() && inode.child != null)
                throw new DirectoryNotEmptyException(getString(path));
            updateDelete(inode);
        }
    }

    private static void copyStream(InputStream is, OutputStream os)
        throws IOException
    {
        byte[] copyBuf = new byte[8192];
        int n;
        while ((n = is.read(copyBuf)) != -1) {
            os.write(copyBuf, 0, n);
        }
    }

    // Returns an out stream for either
    // (1) writing the contents of a new entry, if the entry exits, or
    // (2) updating/replacing the contents of the specified existing entry.
    private OutputStream getOutputStream(Entry e) throws IOException {

        if (e.mtime == -1)
            e.mtime = System.currentTimeMillis();
        if (e.method == -1)
            e.method = METHOD_DEFLATED;  // TBD:  use default method
        // store size, compressed size, and crc-32 in LOC header
        e.flag = 0;
        if (zc.isUTF8())
            e.flag |= FLAG_EFS;
        OutputStream os;
        if (useTempFile) {
            e.file = getTempPathForEntry(null);
            os = Files.newOutputStream(e.file, WRITE);
        } else {
            os = new ByteArrayOutputStream((e.size > 0)? (int)e.size : 8192);
        }
        return new EntryOutputStream(e, os);
    }

    private InputStream getInputStream(Entry e)
        throws IOException
    {
        InputStream eis = null;

        if (e.type == Entry.NEW) {
            if (e.bytes != null)
                eis = new ByteArrayInputStream(e.bytes);
            else if (e.file != null)
                eis = Files.newInputStream(e.file);
            else
                throw new ZipException("update entry data is missing");
        } else if (e.type == Entry.FILECH) {
            // FILECH result is un-compressed.
            eis = Files.newInputStream(e.file);
            // TBD: wrap to hook close()
            // streams.add(eis);
            return eis;
        } else {  // untouced  CEN or COPY
            eis = new EntryInputStream(e, ch);
        }
        if (e.method == METHOD_DEFLATED) {
            // MORE: Compute good size for inflater stream:
            long bufSize = e.size + 2; // Inflater likes a bit of slack
            if (bufSize > 65536)
                bufSize = 8192;
            final long size = e.size;
            eis = new InflaterInputStream(eis, getInflater(), (int)bufSize) {

                private boolean isClosed = false;
                public void close() throws IOException {
                    if (!isClosed) {
                        releaseInflater(inf);
                        this.in.close();
                        isClosed = true;
                        streams.remove(this);
                    }
                }
                // Override fill() method to provide an extra "dummy" byte
                // at the end of the input stream. This is required when
                // using the "nowrap" Inflater option. (it appears the new
                // zlib in 7 does not need it, but keep it for now)
                protected void fill() throws IOException {
                    if (eof) {
                        throw new EOFException(
                            "Unexpected end of ZLIB input stream");
                    }
                    len = this.in.read(buf, 0, buf.length);
                    if (len == -1) {
                        buf[0] = 0;
                        len = 1;
                        eof = true;
                    }
                    inf.setInput(buf, 0, len);
                }
                private boolean eof;

                public int available() throws IOException {
                    if (isClosed)
                        return 0;
                    long avail = size - inf.getBytesWritten();
                    return avail > (long) Integer.MAX_VALUE ?
                        Integer.MAX_VALUE : (int) avail;
                }
            };
        } else if (e.method == METHOD_STORED) {
            // TBD: wrap/ it does not seem necessary
        } else {
            throw new ZipException("invalid compression method");
        }
        streams.add(eis);
        return eis;
    }

    // Inner class implementing the input stream used to read
    // a (possibly compressed) zip file entry.
    private class EntryInputStream extends InputStream {
        private final SeekableByteChannel zfch; // local ref to zipfs's "ch". zipfs.ch might
                                          // point to a new channel after sync()
        private   long pos;               // current position within entry data
        protected long rem;               // number of remaining bytes within entry
        protected final long size;        // uncompressed size of this entry

        EntryInputStream(Entry e, SeekableByteChannel zfch)
            throws IOException
        {
            this.zfch = zfch;
            rem = e.csize;
            size = e.size;
            pos = getDataPos(e);
        }
        public int read(byte b[], int off, int len) throws IOException {
            ensureOpen();
            if (rem == 0) {
                return -1;
            }
            if (len <= 0) {
                return 0;
            }
            if (len > rem) {
                len = (int) rem;
            }
            // readFullyAt()
            long n = 0;
            ByteBuffer bb = ByteBuffer.wrap(b);
            bb.position(off);
            bb.limit(off + len);
            synchronized(zfch) {
                n = zfch.position(pos).read(bb);
            }
            if (n > 0) {
                pos += n;
                rem -= n;
            }
            if (rem == 0) {
                close();
            }
            return (int)n;
        }
        public int read() throws IOException {
            byte[] b = new byte[1];
            if (read(b, 0, 1) == 1) {
                return b[0] & 0xff;
            } else {
                return -1;
            }
        }
        public long skip(long n) throws IOException {
            ensureOpen();
            if (n > rem)
                n = rem;
            pos += n;
            rem -= n;
            if (rem == 0) {
                close();
            }
            return n;
        }
        public int available() {
            return rem > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rem;
        }
        public long size() {
            return size;
        }
        public void close() {
            rem = 0;
            streams.remove(this);
        }
    }

    class EntryOutputStream extends DeflaterOutputStream
    {
        private CRC32 crc;
        private Entry e;
        private long written;

        EntryOutputStream(Entry e, OutputStream os)
            throws IOException
        {
            super(os, getDeflater());
            if (e == null)
                throw new NullPointerException("Zip entry is null");
            this.e = e;
            crc = new CRC32();
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            if (e.type != Entry.FILECH)    // only from sync
                ensureOpen();
            if (off < 0 || len < 0 || off > b.length - len) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }
            switch (e.method) {
            case METHOD_DEFLATED:
                super.write(b, off, len);
                break;
            case METHOD_STORED:
                written += len;
                out.write(b, off, len);
                break;
            default:
                throw new ZipException("invalid compression method");
            }
            crc.update(b, off, len);
        }

        @Override
        public void close() throws IOException {
            // TBD ensureOpen();
            switch (e.method) {
            case METHOD_DEFLATED:
                finish();
                e.size  = def.getBytesRead();
                e.csize = def.getBytesWritten();
                e.crc = crc.getValue();
                break;
            case METHOD_STORED:
                // we already know that both e.size and e.csize are the same
                e.size = e.csize = written;
                e.crc = crc.getValue();
                break;
            default:
                throw new ZipException("invalid compression method");
            }
            //crc.reset();
            if (out instanceof ByteArrayOutputStream)
                e.bytes = ((ByteArrayOutputStream)out).toByteArray();

            if (e.type == Entry.FILECH) {
                releaseDeflater(def);
                return;
            }
            super.close();
            releaseDeflater(def);
            update(e);
        }
    }

    static void zerror(String msg) {
        throw new ZipError(msg);
    }

    // Maxmum number of de/inflater we cache
    private final int MAX_FLATER = 20;
    // List of available Inflater objects for decompression
    private final List<Inflater> inflaters = new ArrayList<>();

    // Gets an inflater from the list of available inflaters or allocates
    // a new one.
    private Inflater getInflater() {
        synchronized (inflaters) {
            int size = inflaters.size();
            if (size > 0) {
                Inflater inf = inflaters.remove(size - 1);
                return inf;
            } else {
                return new Inflater(true);
            }
        }
    }

    // Releases the specified inflater to the list of available inflaters.
    private void releaseInflater(Inflater inf) {
        synchronized (inflaters) {
            if (inflaters.size() < MAX_FLATER) {
                inf.reset();
                inflaters.add(inf);
            } else {
                inf.end();
            }
        }
    }

    // List of available Deflater objects for compression
    private final List<Deflater> deflaters = new ArrayList<>();

    // Gets an deflater from the list of available deflaters or allocates
    // a new one.
    private Deflater getDeflater() {
        synchronized (deflaters) {
            int size = deflaters.size();
            if (size > 0) {
                Deflater def = deflaters.remove(size - 1);
                return def;
            } else {
                return new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            }
        }
    }

    // Releases the specified inflater to the list of available inflaters.
    private void releaseDeflater(Deflater def) {
        synchronized (deflaters) {
            if (inflaters.size() < MAX_FLATER) {
               def.reset();
               deflaters.add(def);
            } else {
               def.end();
            }
        }
    }

    // End of central directory record
    static class END {
        int  disknum;
        int  sdisknum;
        int  endsub;     // endsub
        int  centot;     // 4 bytes
        long cenlen;     // 4 bytes
        long cenoff;     // 4 bytes
        int  comlen;     // comment length
        byte[] comment;

        /* members of Zip64 end of central directory locator */
        int diskNum;
        long endpos;
        int disktot;

        void write(OutputStream os, long offset) throws IOException {
            boolean hasZip64 = false;
            long xlen = cenlen;
            long xoff = cenoff;
            if (xlen >= ZIP64_MINVAL) {
                xlen = ZIP64_MINVAL;
                hasZip64 = true;
            }
            if (xoff >= ZIP64_MINVAL) {
                xoff = ZIP64_MINVAL;
                hasZip64 = true;
            }
            int count = centot;
            if (count >= ZIP64_MINVAL32) {
                count = ZIP64_MINVAL32;
                hasZip64 = true;
            }
            if (hasZip64) {
                long off64 = offset;
                //zip64 end of central directory record
                writeInt(os, ZIP64_ENDSIG);       // zip64 END record signature
                writeLong(os, ZIP64_ENDHDR - 12); // size of zip64 end
                writeShort(os, 45);               // version made by
                writeShort(os, 45);               // version needed to extract
                writeInt(os, 0);                  // number of this disk
                writeInt(os, 0);                  // central directory start disk
                writeLong(os, centot);            // number of directory entires on disk
                writeLong(os, centot);            // number of directory entires
                writeLong(os, cenlen);            // length of central directory
                writeLong(os, cenoff);            // offset of central directory

                //zip64 end of central directory locator
                writeInt(os, ZIP64_LOCSIG);       // zip64 END locator signature
                writeInt(os, 0);                  // zip64 END start disk
                writeLong(os, off64);             // offset of zip64 END
                writeInt(os, 1);                  // total number of disks (?)
            }
            writeInt(os, ENDSIG);                 // END record signature
            writeShort(os, 0);                    // number of this disk
            writeShort(os, 0);                    // central directory start disk
            writeShort(os, count);                // number of directory entries on disk
            writeShort(os, count);                // total number of directory entries
            writeInt(os, xlen);                   // length of central directory
            writeInt(os, xoff);                   // offset of central directory
            if (comment != null) {            // zip file comment
                writeShort(os, comment.length);
                writeBytes(os, comment);
            } else {
                writeShort(os, 0);
            }
        }
    }

    // Internal node that links a "name" to its pos in cen table.
    // The node itself can be used as a "key" to lookup itself in
    // the HashMap inodes.
    static class IndexNode {
        byte[] name;
        int    hashcode;  // node is hashable/hashed by its name
        int    pos = -1;  // position in cen table, -1 menas the
                          // entry does not exists in zip file
        IndexNode(byte[] name, int pos) {
            name(name);
            this.pos = pos;
        }

        final static IndexNode keyOf(byte[] name) { // get a lookup key;
            return new IndexNode(name, -1);
        }

        final void name(byte[] name) {
            this.name = name;
            this.hashcode = Arrays.hashCode(name);
        }

        final IndexNode as(byte[] name) {           // reuse the node, mostly
            name(name);                             // as a lookup "key"
            return this;
        }

        boolean isDir() {
            return name != null &&
                   (name.length == 0 || name[name.length - 1] == '/');
        }

        public boolean equals(Object other) {
            if (!(other instanceof IndexNode)) {
                return false;
            }
            return Arrays.equals(name, ((IndexNode)other).name);
        }

        public int hashCode() {
            return hashcode;
        }

        IndexNode() {}
        IndexNode sibling;
        IndexNode child;  // 1st child
    }

    static class Entry extends IndexNode {

        static final int CEN    = 1;    // entry read from cen
        static final int NEW    = 2;    // updated contents in bytes or file
        static final int FILECH = 3;    // fch update in "file"
        static final int COPY   = 4;    // copy of a CEN entry


        byte[] bytes;      // updated content bytes
        Path   file;       // use tmp file to store bytes;
        int    type = CEN; // default is the entry read from cen

        // entry attributes
        int    version;
        int    flag;
        int    method = -1;    // compression method
        long   mtime  = -1;    // last modification time (in DOS time)
        long   atime  = -1;    // last access time
        long   ctime  = -1;    // create time
        long   crc    = -1;    // crc-32 of entry data
        long   csize  = -1;    // compressed size of entry data
        long   size   = -1;    // uncompressed size of entry data
        byte[] extra;

        // cen
        int    versionMade;
        int    disk;
        int    attrs;
        long   attrsEx;
        long   locoff;
        byte[] comment;

        Entry() {}

        Entry(byte[] name) {
            name(name);
            this.mtime  = this.ctime = this.atime = System.currentTimeMillis();
            this.crc    = 0;
            this.size   = 0;
            this.csize  = 0;
            this.method = METHOD_DEFLATED;
        }

        Entry(byte[] name, int type) {
            this(name);
            this.type = type;
        }

        Entry (Entry e, int type) {
            name(e.name);
            this.version   = e.version;
            this.ctime     = e.ctime;
            this.atime     = e.atime;
            this.mtime     = e.mtime;
            this.crc       = e.crc;
            this.size      = e.size;
            this.csize     = e.csize;
            this.method    = e.method;
            this.extra     = e.extra;
            this.versionMade = e.versionMade;
            this.disk      = e.disk;
            this.attrs     = e.attrs;
            this.attrsEx   = e.attrsEx;
            this.locoff    = e.locoff;
            this.comment   = e.comment;
            this.type      = type;
        }

        Entry (byte[] name, Path file, int type) {
            this(name, type);
            this.file = file;
            this.method = METHOD_STORED;
        }

        int version() throws ZipException {
            if (method == METHOD_DEFLATED)
                return 20;
            else if (method == METHOD_STORED)
                return 10;
            throw new ZipException("unsupported compression method");
        }

        ///////////////////// CEN //////////////////////
        static Entry readCEN(ZipFileSystem zipfs, int pos)
            throws IOException
        {
            return new Entry().cen(zipfs, pos);
        }

        private Entry cen(ZipFileSystem zipfs, int pos)
            throws IOException
        {
            byte[] cen = zipfs.cen;
            if (CENSIG(cen, pos) != CENSIG)
                zerror("invalid CEN header (bad signature)");
            versionMade = CENVEM(cen, pos);
            version     = CENVER(cen, pos);
            flag        = CENFLG(cen, pos);
            method      = CENHOW(cen, pos);
            mtime       = dosToJavaTime(CENTIM(cen, pos));
            crc         = CENCRC(cen, pos);
            csize       = CENSIZ(cen, pos);
            size        = CENLEN(cen, pos);
            int nlen    = CENNAM(cen, pos);
            int elen    = CENEXT(cen, pos);
            int clen    = CENCOM(cen, pos);
            disk        = CENDSK(cen, pos);
            attrs       = CENATT(cen, pos);
            attrsEx     = CENATX(cen, pos);
            locoff      = CENOFF(cen, pos);

            pos += CENHDR;
            name(Arrays.copyOfRange(cen, pos, pos + nlen));

            pos += nlen;
            if (elen > 0) {
                extra = Arrays.copyOfRange(cen, pos, pos + elen);
                pos += elen;
                readExtra(zipfs);
            }
            if (clen > 0) {
                comment = Arrays.copyOfRange(cen, pos, pos + clen);
            }
            return this;
        }

        int writeCEN(OutputStream os) throws IOException
        {
            int written  = CENHDR;
            int version0 = version();
            long csize0  = csize;
            long size0   = size;
            long locoff0 = locoff;
            int elen64   = 0;                // extra for ZIP64
            int elenNTFS = 0;                // extra for NTFS (a/c/mtime)
            int elenEXTT = 0;                // extra for Extended Timestamp
            boolean foundExtraTime = false;  // if time stamp NTFS, EXTT present

            // confirm size/length
            int nlen = (name != null) ? name.length : 0;
            int elen = (extra != null) ? extra.length : 0;
            int eoff = 0;
            int clen = (comment != null) ? comment.length : 0;
            if (csize >= ZIP64_MINVAL) {
                csize0 = ZIP64_MINVAL;
                elen64 += 8;                 // csize(8)
            }
            if (size >= ZIP64_MINVAL) {
                size0 = ZIP64_MINVAL;        // size(8)
                elen64 += 8;
            }
            if (locoff >= ZIP64_MINVAL) {
                locoff0 = ZIP64_MINVAL;
                elen64 += 8;                 // offset(8)
            }
            if (elen64 != 0) {
                elen64 += 4;                 // header and data sz 4 bytes
            }
            while (eoff + 4 < elen) {
                int tag = SH(extra, eoff);
                int sz = SH(extra, eoff + 2);
                if (tag == EXTID_EXTT || tag == EXTID_NTFS) {
                    foundExtraTime = true;
                }
                eoff += (4 + sz);
            }
            if (!foundExtraTime) {
                if (isWindows) {             // use NTFS
                    elenNTFS = 36;           // total 36 bytes
                } else {                     // Extended Timestamp otherwise
                    elenEXTT = 9;            // only mtime in cen
                }
            }
            writeInt(os, CENSIG);            // CEN header signature
            if (elen64 != 0) {
                writeShort(os, 45);          // ver 4.5 for zip64
                writeShort(os, 45);
            } else {
                writeShort(os, version0);    // version made by
                writeShort(os, version0);    // version needed to extract
            }
            writeShort(os, flag);            // general purpose bit flag
            writeShort(os, method);          // compression method
                                             // last modification time
            writeInt(os, (int)javaToDosTime(mtime));
            writeInt(os, crc);               // crc-32
            writeInt(os, csize0);            // compressed size
            writeInt(os, size0);             // uncompressed size
            writeShort(os, name.length);
            writeShort(os, elen + elen64 + elenNTFS + elenEXTT);

            if (comment != null) {
                writeShort(os, Math.min(clen, 0xffff));
            } else {
                writeShort(os, 0);
            }
            writeShort(os, 0);              // starting disk number
            writeShort(os, 0);              // internal file attributes (unused)
            writeInt(os, 0);                // external file attributes (unused)
            writeInt(os, locoff0);          // relative offset of local header
            writeBytes(os, name);
            if (elen64 != 0) {
                writeShort(os, EXTID_ZIP64);// Zip64 extra
                writeShort(os, elen64 - 4); // size of "this" extra block
                if (size0 == ZIP64_MINVAL)
                    writeLong(os, size);
                if (csize0 == ZIP64_MINVAL)
                    writeLong(os, csize);
                if (locoff0 == ZIP64_MINVAL)
                    writeLong(os, locoff);
            }
            if (elenNTFS != 0) {
                writeShort(os, EXTID_NTFS);
                writeShort(os, elenNTFS - 4);
                writeInt(os, 0);            // reserved
                writeShort(os, 0x0001);     // NTFS attr tag
                writeShort(os, 24);
                writeLong(os, javaToWinTime(mtime));
                writeLong(os, javaToWinTime(atime));
                writeLong(os, javaToWinTime(ctime));
            }
            if (elenEXTT != 0) {
                writeShort(os, EXTID_EXTT);
                writeShort(os, elenEXTT - 4);
                if (ctime == -1)
                    os.write(0x3);          // mtime and atime
                else
                    os.write(0x7);          // mtime, atime and ctime
                writeInt(os, javaToUnixTime(mtime));
            }
            if (extra != null)              // whatever not recognized
                writeBytes(os, extra);
            if (comment != null)            //TBD: 0, Math.min(commentBytes.length, 0xffff));
                writeBytes(os, comment);
            return CENHDR + nlen + elen + clen + elen64 + elenNTFS + elenEXTT;
        }

        ///////////////////// LOC //////////////////////
        static Entry readLOC(ZipFileSystem zipfs, long pos)
            throws IOException
        {
            return readLOC(zipfs, pos, new byte[1024]);
        }

        static Entry readLOC(ZipFileSystem zipfs, long pos, byte[] buf)
            throws IOException
        {
            return new Entry().loc(zipfs, pos, buf);
        }

        Entry loc(ZipFileSystem zipfs, long pos, byte[] buf)
            throws IOException
        {
            assert (buf.length >= LOCHDR);
            if (zipfs.readFullyAt(buf, 0, LOCHDR , pos) != LOCHDR)
                throw new ZipException("loc: reading failed");
            if (LOCSIG(buf) != LOCSIG)
                throw new ZipException("loc: wrong sig ->"
                                       + Long.toString(LOCSIG(buf), 16));
            //startPos = pos;
            version  = LOCVER(buf);
            flag     = LOCFLG(buf);
            method   = LOCHOW(buf);
            mtime    = dosToJavaTime(LOCTIM(buf));
            crc      = LOCCRC(buf);
            csize    = LOCSIZ(buf);
            size     = LOCLEN(buf);
            int nlen = LOCNAM(buf);
            int elen = LOCEXT(buf);

            name = new byte[nlen];
            if (zipfs.readFullyAt(name, 0, nlen, pos + LOCHDR) != nlen) {
                throw new ZipException("loc: name reading failed");
            }
            if (elen > 0) {
                extra = new byte[elen];
                if (zipfs.readFullyAt(extra, 0, elen, pos + LOCHDR + nlen)
                    != elen) {
                    throw new ZipException("loc: ext reading failed");
                }
            }
            pos += (LOCHDR + nlen + elen);
            if ((flag & FLAG_DATADESCR) != 0) {
                // Data Descriptor
                Entry e = zipfs.getEntry0(name);  // get the size/csize from cen
                if (e == null)
                    throw new ZipException("loc: name not found in cen");
                size = e.size;
                csize = e.csize;
                pos += (method == METHOD_STORED ? size : csize);
                if (size >= ZIP64_MINVAL || csize >= ZIP64_MINVAL)
                    pos += 24;
                else
                    pos += 16;
            } else {
                if (extra != null &&
                    (size == ZIP64_MINVAL || csize == ZIP64_MINVAL)) {
                    // zip64 ext: must include both size and csize
                    int off = 0;
                    while (off + 20 < elen) {    // HeaderID+DataSize+Data
                        int sz = SH(extra, off + 2);
                        if (SH(extra, off) == EXTID_ZIP64 && sz == 16) {
                            size = LL(extra, off + 4);
                            csize = LL(extra, off + 12);
                            break;
                        }
                        off += (sz + 4);
                    }
                }
                pos += (method == METHOD_STORED ? size : csize);
            }
            return this;
        }

        int writeLOC(OutputStream os)
            throws IOException
        {
            writeInt(os, LOCSIG);               // LOC header signature
            int version = version();
            int nlen = (name != null) ? name.length : 0;
            int elen = (extra != null) ? extra.length : 0;
            boolean foundExtraTime = false;     // if extra timestamp present
            int eoff = 0;
            int elen64 = 0;
            int elenEXTT = 0;
            int elenNTFS = 0;
            if ((flag & FLAG_DATADESCR) != 0) {
                writeShort(os, version());      // version needed to extract
                writeShort(os, flag);           // general purpose bit flag
                writeShort(os, method);         // compression method
                // last modification time
                writeInt(os, (int)javaToDosTime(mtime));
                // store size, uncompressed size, and crc-32 in data descriptor
                // immediately following compressed entry data
                writeInt(os, 0);
                writeInt(os, 0);
                writeInt(os, 0);
            } else {
                if (csize >= ZIP64_MINVAL || size >= ZIP64_MINVAL) {
                    elen64 = 20;    //headid(2) + size(2) + size(8) + csize(8)
                    writeShort(os, 45);         // ver 4.5 for zip64
                } else {
                    writeShort(os, version());  // version needed to extract
                }
                writeShort(os, flag);           // general purpose bit flag
                writeShort(os, method);         // compression method
                                                // last modification time
                writeInt(os, (int)javaToDosTime(mtime));
                writeInt(os, crc);              // crc-32
                if (elen64 != 0) {
                    writeInt(os, ZIP64_MINVAL);
                    writeInt(os, ZIP64_MINVAL);
                } else {
                    writeInt(os, csize);        // compressed size
                    writeInt(os, size);         // uncompressed size
                }
            }
            while (eoff + 4 < elen) {
                int tag = SH(extra, eoff);
                int sz = SH(extra, eoff + 2);
                if (tag == EXTID_EXTT || tag == EXTID_NTFS) {
                    foundExtraTime = true;
                }
                eoff += (4 + sz);
            }
            if (!foundExtraTime) {
                if (isWindows) {
                    elenNTFS = 36;              // NTFS, total 36 bytes
                } else {                        // on unix use "ext time"
                    elenEXTT = 9;
                    if (atime != -1)
                        elenEXTT += 4;
                    if (ctime != -1)
                        elenEXTT += 4;
                }
            }
            writeShort(os, name.length);
            writeShort(os, elen + elen64 + elenNTFS + elenEXTT);
            writeBytes(os, name);
            if (elen64 != 0) {
                writeShort(os, EXTID_ZIP64);
                writeShort(os, 16);
                writeLong(os, size);
                writeLong(os, csize);
            }
            if (elenNTFS != 0) {
                writeShort(os, EXTID_NTFS);
                writeShort(os, elenNTFS - 4);
                writeInt(os, 0);            // reserved
                writeShort(os, 0x0001);     // NTFS attr tag
                writeShort(os, 24);
                writeLong(os, javaToWinTime(mtime));
                writeLong(os, javaToWinTime(atime));
                writeLong(os, javaToWinTime(ctime));
            }
            if (elenEXTT != 0) {
                writeShort(os, EXTID_EXTT);
                writeShort(os, elenEXTT - 4);// size for the folowing data block
                int fbyte = 0x1;
                if (atime != -1)           // mtime and atime
                    fbyte |= 0x2;
                if (ctime != -1)           // mtime, atime and ctime
                    fbyte |= 0x4;
                os.write(fbyte);           // flags byte
                writeInt(os, javaToUnixTime(mtime));
                if (atime != -1)
                    writeInt(os, javaToUnixTime(atime));
                if (ctime != -1)
                    writeInt(os, javaToUnixTime(ctime));
            }
            if (extra != null) {
                writeBytes(os, extra);
            }
            return LOCHDR + name.length + elen + elen64 + elenNTFS + elenEXTT;
        }

        // Data Descriptior
        int writeEXT(OutputStream os)
            throws IOException
        {
            writeInt(os, EXTSIG);           // EXT header signature
            writeInt(os, crc);              // crc-32
            if (csize >= ZIP64_MINVAL || size >= ZIP64_MINVAL) {
                writeLong(os, csize);
                writeLong(os, size);
                return 24;
            } else {
                writeInt(os, csize);        // compressed size
                writeInt(os, size);         // uncompressed size
                return 16;
            }
        }

        // read NTFS, UNIX and ZIP64 data from cen.extra
        void readExtra(ZipFileSystem zipfs) throws IOException {
            if (extra == null)
                return;
            int elen = extra.length;
            int off = 0;
            int newOff = 0;
            while (off + 4 < elen) {
                // extra spec: HeaderID+DataSize+Data
                int pos = off;
                int tag = SH(extra, pos);
                int sz = SH(extra, pos + 2);
                pos += 4;
                if (pos + sz > elen)         // invalid data
                    break;
                switch (tag) {
                case EXTID_ZIP64 :
                    if (size == ZIP64_MINVAL) {
                        if (pos + 8 > elen)  // invalid zip64 extra
                            break;           // fields, just skip
                        size = LL(extra, pos);
                        pos += 8;
                    }
                    if (csize == ZIP64_MINVAL) {
                        if (pos + 8 > elen)
                            break;
                        csize = LL(extra, pos);
                        pos += 8;
                    }
                    if (locoff == ZIP64_MINVAL) {
                        if (pos + 8 > elen)
                            break;
                        locoff = LL(extra, pos);
                        pos += 8;
                    }
                    break;
                case EXTID_NTFS:
                    pos += 4;    // reserved 4 bytes
                    if (SH(extra, pos) !=  0x0001)
                        break;
                    if (SH(extra, pos + 2) != 24)
                        break;
                    // override the loc field, datatime here is
                    // more "accurate"
                    mtime  = winToJavaTime(LL(extra, pos + 4));
                    atime  = winToJavaTime(LL(extra, pos + 12));
                    ctime  = winToJavaTime(LL(extra, pos + 20));
                    break;
                case EXTID_EXTT:
                    // spec says the Extened timestamp in cen only has mtime
                    // need to read the loc to get the extra a/ctime
                    byte[] buf = new byte[LOCHDR];
                    if (zipfs.readFullyAt(buf, 0, buf.length , locoff)
                        != buf.length)
                        throw new ZipException("loc: reading failed");
                    if (LOCSIG(buf) != LOCSIG)
                        throw new ZipException("loc: wrong sig ->"
                                           + Long.toString(LOCSIG(buf), 16));

                    int locElen = LOCEXT(buf);
                    if (locElen < 9)    // EXTT is at lease 9 bytes
                        break;
                    int locNlen = LOCNAM(buf);
                    buf = new byte[locElen];
                    if (zipfs.readFullyAt(buf, 0, buf.length , locoff + LOCHDR + locNlen)
                        != buf.length)
                        throw new ZipException("loc extra: reading failed");
                    int locPos = 0;
                    while (locPos + 4 < buf.length) {
                        int locTag = SH(buf, locPos);
                        int locSZ  = SH(buf, locPos + 2);
                        locPos += 4;
                        if (locTag  != EXTID_EXTT) {
                            locPos += locSZ;
                             continue;
                        }
                        int flag = CH(buf, locPos++);
                        if ((flag & 0x1) != 0) {
                            mtime = unixToJavaTime(LG(buf, locPos));
                            locPos += 4;
                        }
                        if ((flag & 0x2) != 0) {
                            atime = unixToJavaTime(LG(buf, locPos));
                            locPos += 4;
                        }
                        if ((flag & 0x4) != 0) {
                            ctime = unixToJavaTime(LG(buf, locPos));
                            locPos += 4;
                        }
                        break;
                    }
                    break;
                default:    // unknown tag
                    System.arraycopy(extra, off, extra, newOff, sz + 4);
                    newOff += (sz + 4);
                }
                off += (sz + 4);
            }
            if (newOff != 0 && newOff != extra.length)
                extra = Arrays.copyOf(extra, newOff);
            else
                extra = null;
        }
    }

    private static class ExChannelCloser  {
        Path path;
        SeekableByteChannel ch;
        Set<InputStream> streams;
        ExChannelCloser(Path path,
                        SeekableByteChannel ch,
                        Set<InputStream> streams)
        {
            this.path = path;
            this.ch = ch;
            this.streams = streams;
        }
    }

    // ZIP directory has two issues:
    // (1) ZIP spec does not require the ZIP file to include
    //     directory entry
    // (2) all entries are not stored/organized in a "tree"
    //     structure.
    // A possible solution is to build the node tree ourself as
    // implemented below.
    private IndexNode root;

    private void addToTree(IndexNode inode, HashSet<IndexNode> dirs) {
        if (dirs.contains(inode)) {
            return;
        }
        IndexNode parent;
        byte[] name = inode.name;
        byte[] pname = getParent(name);
        if (inodes.containsKey(LOOKUPKEY.as(pname))) {
            parent = inodes.get(LOOKUPKEY);
        } else {    // pseudo directory entry
            parent = new IndexNode(pname, -1);
            inodes.put(parent, parent);
        }
        addToTree(parent, dirs);
        inode.sibling = parent.child;
        parent.child = inode;
        if (name[name.length -1] == '/')
            dirs.add(inode);
    }

    private void removeFromTree(IndexNode inode) {
        IndexNode parent = inodes.get(LOOKUPKEY.as(getParent(inode.name)));
        IndexNode child = parent.child;
        if (child.equals(inode)) {
            parent.child = child.sibling;
        } else {
            IndexNode last = child;
            while ((child = child.sibling) != null) {
                if (child.equals(inode)) {
                    last.sibling = child.sibling;
                    break;
                } else {
                    last = child;
                }
            }
        }
    }

    private void buildNodeTree() throws IOException {
        beginWrite();
        try {
            HashSet<IndexNode> dirs = new HashSet<>();
            IndexNode root = new IndexNode(ROOTPATH, -1);
            inodes.put(root, root);
            dirs.add(root);
            for (IndexNode node : inodes.keySet().toArray(new IndexNode[0])) {
                addToTree(node, dirs);
            }
        } finally {
            endWrite();
        }
    }
}
