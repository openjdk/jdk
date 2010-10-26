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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.*;
import java.net.URI;
import java.util.*;
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

    private final Object lock = new Object();

    // configurable by env map
    private final String  defaultDir;    // default dir for the file system
    private final String  nameEncoding;  // default encoding for name/comment
    private final boolean buildDirTree;  // build a dir tree for directoryStream ops
    private final boolean useTempFile;   // use a temp file for newOS, default
                                         // is to use BAOS for better performance
    private final boolean createNew;     // create a new zip if not exists

    ZipFileSystem(ZipFileSystemProvider provider,
                  Path zfpath,
                  Map<String, ?> env)
        throws IOException
    {
        // configurable env setup
        this.buildDirTree = TRUE.equals(env.get("buildDirTree"));
        this.useTempFile = TRUE.equals(env.get("useTempFile"));
        this.createNew = TRUE.equals(env.get("createNew"));
        this.nameEncoding = env.containsKey("nameEncoding") ?
                            (String)env.get("nameEncoding") : "UTF-8";
        this.defaultDir = env.containsKey("default.dir") ?
                          (String)env.get("default.dir") : "/";
        if (this.defaultDir.charAt(0) != '/')
            throw new IllegalArgumentException("default dir should be absolute");

        this.provider = provider;
        this.zfpath = zfpath;
        if (zfpath.notExists()) {
            if (createNew) {
                OutputStream os = zfpath.newOutputStream(CREATE_NEW, WRITE);
                new END().write(os, 0);
                os.close();
            } else {
                throw new FileSystemNotFoundException(zfpath.toString());
            }
        }
        zfpath.checkAccess(AccessMode.READ); // sm and existence check
        try {
            zfpath.checkAccess(AccessMode.WRITE);
        } catch (AccessDeniedException x) {
            this.readOnly = true;
        }
        this.zc = ZipCoder.get(nameEncoding);
        this.defaultdir = new ZipPath(this, getBytes(defaultDir));
        initZipFile();
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
    public ZipPath getPath(String path) {
        if (path.length() == 0)
            throw new InvalidPathException(path, "path should not be empty");
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
        ArrayList<FileStore> list = new ArrayList<FileStore>(1);
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
        synchronized (lock) {
            if (!isOpen)
                return;
            isOpen = false;
            if (!streams.isEmpty()) {
                synchronized(streams) {
                    for (InputStream is: streams)
                    is.close();
                }
            }
            sync();
            ch.close();
        }
        synchronized (inflaters) {
            for (Inflater inf : inflaters)
                inf.end();
        }
        synchronized (deflaters) {
            for (Deflater def : deflaters)
                def.end();
        }
        for (Path p: tmppaths) {
            try {
                p.deleteIfExists();
            } catch (IOException x) {
                x.printStackTrace();
            }
        }
        provider.removeFileSystem(zfpath);
    }

    ZipFileAttributes[] getAllAttributes() throws IOException {
        ensureOpen();
        int n = inodes.size();
        ZipFileAttributes[] zes = new ZipFileAttributes[n];
        Iterator<IndexNode> itr = inodes.values().iterator();
        int i = 0;
        while(itr.hasNext()) {
            zes[i++] = new ZipFileAttributes(Entry.readCEN(cen, itr.next().pos));
        }
        return zes;
    }

    EntryName[] getEntryNames() throws IOException {
        ensureOpen();
        return inodes.keySet().toArray(new EntryName[0]);
    }

    ZipFileAttributes getFileAttributes(byte[] path)
        throws IOException
    {
        synchronized (lock) {
            Entry e = getEntry0(path);
            if (e == null) {
                if (path.length == 0) {
                    e = new Entry(new byte[0]);  // root
                } else if (buildDirTree) {
                    IndexNode inode = getDirs().get(new EntryName(path));
                    if (inode == null)
                        return null;
                    e = new Entry(inode.name);
                } else {
                    return null;
                }
                e.method = METHOD_STORED;        // STORED for dir
                BasicFileAttributes bfas = Attributes.readBasicFileAttributes(zfpath);
                if (bfas.lastModifiedTime() != null)
                    e.mtime = javaToDosTime(bfas.lastModifiedTime().toMillis());
                if (bfas.lastAccessTime() != null)
                    e.atime = javaToDosTime(bfas.lastAccessTime().toMillis());
                if (bfas.creationTime() != null)
                    e.ctime = javaToDosTime(bfas.creationTime().toMillis());
            }
            return new ZipFileAttributes(e);
        }
    }

    void setTimes(byte[] path, FileTime mtime, FileTime atime, FileTime ctime)
        throws IOException
    {
        checkWritable();
        synchronized (lock) {
            Entry e = getEntry0(path);    // ensureOpen checked
            if (e == null)
                throw new NoSuchFileException(getString(path));
            if (e.type == Entry.CEN)
                e.type = Entry.COPY;      // copy e
            if (mtime != null)
                e.mtime = javaToDosTime(mtime.toMillis());
            if (atime != null)
                e.atime = javaToDosTime(atime.toMillis());
            if (ctime != null)
                e.ctime = javaToDosTime(ctime.toMillis());
            update(e);
        }
    }

    boolean exists(byte[] path)
        throws IOException
    {
        return getEntry0(path) != null;
    }

    boolean isDirectory(byte[] path)
        throws IOException
    {
        synchronized (lock) {
            if (buildDirTree) {
                return getDirs().containsKey(new EntryName(path));
            }
            Entry e = getEntry0(path);
            return (e != null && e.isDir()) || path.length == 0;
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
        synchronized (lock) {
            if (buildDirTree) {
                IndexNode inode = getDirs().get(new EntryName(path));
                if (inode == null)
                    throw new NotDirectoryException(getString(path));
                List<Path> list = new ArrayList<Path>();
                IndexNode child = inode.child;
                while (child != null) {
                    ZipPath zp = toZipPath(child.name);
                    if (filter == null || filter.accept(zp))
                        list.add(zp);
                    child = child.sibling;
                }
                return list.iterator();
            }

            if (!isDirectory(path))
                throw new NotDirectoryException(getString(path));
            List<Path> list = new ArrayList<Path>();
            EntryName[] entries = getEntryNames();
            path = toDirectoryPath(path);
            for (EntryName en :entries) {
                if (!isParentOf(path, en.name))  // is "path" the parent of "name"
                    continue;
                int off = path.length;
                while (off < en.name.length) {
                    if (en.name[off] == '/')
                        break;
                    off++;
                }
                if (off < (en.name.length - 1))
                    continue;
                ZipPath zp = toZipPath(en.name);
                if (filter == null || filter.accept(zp))
                    list.add(zp);
            }
            return list.iterator();
        }
    }

    void createDirectory(byte[] dir, FileAttribute<?>... attrs)
        throws IOException
    {
        checkWritable();
        dir = toDirectoryPath(dir);
        synchronized (lock) {
            ensureOpen();
            // pseudo root dir, or exiting dir
            if (dir.length == 0 || exists(dir))
                throw new FileAlreadyExistsException(getString(dir));
            checkParents(dir);

            Entry e = new Entry(dir, Entry.NEW);
            e.method = METHOD_STORED;  // STORED for dir
            update(e);
        }
    }

    void copyFile(boolean deletesrc, byte[]src, byte[] dst, CopyOption... options)
        throws IOException
    {
        checkWritable();
        if (Arrays.equals(src, dst))
            return;    // do nothing, src and dst are the same
        synchronized (lock) {
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
            u.name = dst;                             // change name
            // don't touch the "nlen and elen" here. writeLOC() always
            // re-calculate from "name" and "extra" for the correct length,
            // copyLOCEntry however needs the original lengths to skip the
            // loc header.
            // u.nlen = dst.length;
            if (eSrc.type == Entry.NEW || eSrc.type == Entry.FILECH)
            {
                u.type = eSrc.type;    // make it the same type
                if (!deletesrc) {      // if it's not "rename", just take the data
                    if (eSrc.bytes != null)
                        u.bytes = Arrays.copyOf(eSrc.bytes, eSrc.bytes.length);
                    else if (eSrc.file != null) {
                        u.file = getTempPathForEntry(null);
                        eSrc.file.copyTo(u.file, REPLACE_EXISTING);
                    }
                }
            }
            if (!hasCopyAttrs)
                u.mtime = u.atime= u.ctime = javaToDosTime(System.currentTimeMillis());
            update(u);
            if (deletesrc)
                updateDelete(eSrc);
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
        synchronized (lock) {
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
        }
    }

    // Returns an input stream for reading the contents of the specified
    // file entry.
    InputStream newInputStream(byte[] path) throws IOException {
        synchronized (lock) {
            Entry e = getEntry0(path);
            if (e == null)
                throw new NoSuchFileException(getString(path));
            if (e.isDir())
                throw new FileSystemException(getString(path), "is a directory", null);
            return getInputStream(e);
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
            final WritableByteChannel wbc = Channels.newChannel(newOutputStream(path,
                                                options.toArray(new OpenOption[0])));
            long leftover = 0;;
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
                public SeekableByteChannel position(long pos) throws IOException {
                    throw new UnsupportedOperationException();
                }
                public int read(ByteBuffer dst) throws IOException {
                    throw new UnsupportedOperationException();
                }
                public SeekableByteChannel truncate(long size) throws IOException {
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
        } else {
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
                public SeekableByteChannel position(long pos) throws IOException {
                    throw new UnsupportedOperationException();
                }
                public int read(ByteBuffer dst) throws IOException {
                    return rbc.read(dst);
                }
                public SeekableByteChannel truncate(long size) throws IOException {
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
                    u.mtime = javaToDosTime(System.currentTimeMillis());
                    u.size = Attributes.readBasicFileAttributes(u.file).size();
                    update(u);
                } else {
                    if (!isFCH)    // if this is a new fch for reading
                        removeTempPathForEntry(tmpfile);
                }
            }
        };
    }

    // the outstanding input streams that need to be closed
    private Set<InputStream> streams =
        Collections.synchronizedSet(new HashSet<InputStream>());

    // the ex-channel and ex-path that need to close when their outstanding
    // input streams are all closed by the obtainers.
    private Set<ExChannelCloser> exChClosers = new HashSet<>();

    private Set<Path> tmppaths = new HashSet<>();
    private Path getTempPathForEntry(byte[] path) throws IOException {
        Path tmpPath = createTempFileInSameDirectoryAs(zfpath);
        tmppaths.add(tmpPath);

        if (path != null) {
            Entry e = getEntry0(path);
            if (e != null) {
                InputStream is = newInputStream(path);
                OutputStream os = tmpPath.newOutputStream(WRITE);
                try {
                    copyStream(is, os);
                } finally {
                    is.close();
                    os.close();
                }
            }
        }
        return tmpPath;
    }

    private void removeTempPathForEntry(Path path) throws IOException {
        path.delete();
        tmppaths.remove(path);
    }

    // check if all parents really exit. ZIP spec does not require
    // the existence of any "parent directory".
    private void checkParents(byte[] path) throws IOException {
        while ((path = getParent(path)) != null) {
            if (!inodes.containsKey(new EntryName(path)))
                throw new NoSuchFileException(getString(path));
        }
    }

    private static byte[] getParent(byte[] path) {
        int off = path.length - 1;
        if (off > 0 && path[off] == '/')  // isDirectory
            off--;
        while (off > 0 && path[off] != '/') { off--; }
        if (off == 0)
            return null;                  // top entry
        return Arrays.copyOf(path, off + 1);
    }

    // If "starter" is the parent directory of "path"
    private static boolean isParentOf(byte[] p, byte[] c) {
        final int plen = p.length;
        if (plen == 0)          // root dir
            return true;
        if (plen  >= c.length)
            return false;
        int n = 0;
        while (n < plen) {
            if (p[n] != c[n])
                return false;
            n++;
        }
        if (p[n - 1] != '/' && (c[n] != '/' || n == c.length - 1))
            return false;
        return true;
    }

    ///////////////////////////////////////////////////////////////////
    private void initZipFile() throws IOException {
        ch = zfpath.newByteChannel(READ);
        initCEN();
    }

    private volatile boolean isOpen = true;
    private SeekableByteChannel ch; // channel to the zipfile
    ByteBuffer cen;        // CEN & ENDHDR
    private END  end;
    private long locpos;   // position of first LOC header (usually 0)

    // name -> pos (in cen), package private for ZipInfo
    LinkedHashMap<EntryName, IndexNode> inodes;

    byte[] getBytes(String name) {
        return zc.getBytes(name);
    }
    String getString(byte[] name) {
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
    private long readFullyAt(byte[] buf, int off, long len, long pos)
        throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(buf);
        bb.position(off);
        bb.limit((int)(off + len));
        return readFullyAt(bb, pos);
    }

    private long readFullyAt(ByteBuffer bb, long pos)
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
    // CEN header, otherwise returns -1 if an error occured. If zip->msg != NULL
    // then the error was a zip format error and zip->msg has the error text.
    // Always pass in -1 for knownTotal; it's used for a recursive call.
    private long initCEN() throws IOException {
        end = findEND();
        if (end.endpos == 0) {
            inodes = new LinkedHashMap<EntryName, IndexNode>(10);
            locpos = 0;
            return 0;         // only END header present
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
        cen = ByteBuffer.allocate((int)(end.cenlen + ENDHDR));
        if (readFullyAt(cen, cenpos) != end.cenlen + ENDHDR) {
            zerror("read CEN tables failed");
        }
        cen.order(ByteOrder.LITTLE_ENDIAN).flip();

        // Iterate through the entries in the central directory
        inodes = new LinkedHashMap<EntryName, IndexNode>(end.centot + 1);
        int pos = 0;
        int limit = cen.remaining() - ENDHDR;
        int i = 0;
        byte[] bBuf = new byte[1024];
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
                zerror("invalid CEN header (bad compression method: " + method + ")");
            if (pos + CENHDR + nlen > limit)
                zerror("invalid CEN header (bad header size)");
            if (bBuf.length < nlen)
                 bBuf = new byte[nlen];
            cen.position(pos + CENHDR);
            byte[] name = new byte[nlen];
            cen.get(name);
            inodes.put(new EntryName(name), new IndexNode(name, pos));
            // skip ext and comment
            cen.position(pos += (CENHDR + nlen + elen + clen));
            i++;
        }
        if (cen.remaining() != ENDHDR) {
            zerror("invalid CEN header (bad header size)");
        }
        dirs = null;  // clear the dir map
        return cenpos;
    }

    private void ensureOpen() throws IOException {
        if (!isOpen)
            throw new ClosedFileSystemException();
    }

    // Creates a new empty temporary file in the same directory as the
    // specified file.  A variant of File.createTempFile.
    private static Path createTempFileInSameDirectoryAs(Path path)
        throws IOException
    {
        Path parent = path.toAbsolutePath().getParent();
        String dir = (parent == null)? "." : parent.toString();
        return File.createTempFile("zipfstmp", null, new File(dir)).toPath();
    }

    ////////////////////update & sync //////////////////////////////////////

    private boolean hasUpdate = false;
    private void updateDelete(Entry e) {
        EntryName en = new EntryName(e.name);
        inodes.remove(en);
        hasUpdate = true;
    }

    private void update(Entry e) {
        EntryName en = new EntryName(e.name);
        inodes.put(en, e);
        hasUpdate = true;
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
        if (updateHeader) {       // if we need update the loc header
            locoff += LOCHDR + e.nlen + e.elen;  // skip header
            size += e.csize;
            written = e.writeLOC(os) + size;
        } else {
            size += LOCHDR + e.nlen + e.elen + e.csize;
            written = size;
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
        assert Thread.holdsLock(this);

        // check ex-closer
        if (!exChClosers.isEmpty()) {
            for (ExChannelCloser ecc : exChClosers) {
                if (ecc.streams.isEmpty()) {
                    ecc.ch.close();
                    ecc.path.delete();
                    exChClosers.remove(ecc);
                }
            }
        }
        if (!hasUpdate)
            return;

        Path tmpFile = createTempFileInSameDirectoryAs(zfpath);
        OutputStream os = tmpFile.newOutputStream(WRITE);
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
                    } else {                          // NEW or FILECH
                        e.locoff = written;
                        written += e.writeLOC(os);    // write loc header
                        if (e.bytes != null) {        // in-memory, deflated
                            os.write(e.bytes);        // already
                            written += e.bytes.length;
                        } else if (e.file != null) {  // tmp file
                            InputStream is = e.file.newInputStream();
                            int n;
                            if (e.type == Entry.NEW) {  // deflated already
                                while ((n = is.read(buf)) != -1) {
                                    os.write(buf, 0, n);
                                    written += n;
                                }
                            } else if (e.type == Entry.FILECH) {
                                // the data are not deflated, use ZEOS
                                OutputStream os2 = new EntryOutputStream(e, os);
                                while ((n = is.read(buf)) != -1) {
                                    os2.write(buf, 0, n);
                                }
                                os2.close();
                                written += e.csize;
                                if ((e.flag & FLAG_DATADESCR) != 0)
                                    written += e.writeEXT(os);
                            }
                            is.close();
                            e.file.delete();
                            tmppaths.remove(e.file);
                        } else {
                            // dir, 0-length data
                        }
                    }
                    elist.add(e);
                } catch (IOException x) {
                    x.printStackTrace();    // skip any in-accurate entry
                }
            } else {    // unchanged inode
                e = Entry.readCEN(cen, inode.pos);
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
        os.close();

        if (!streams.isEmpty()) {
            // There are outstanding input streams open on existing "ch",
            // so, don't close the "cha" and delete the "file for now, let
            // the "ex-channel-closer" to handle them
            ExChannelCloser ecc = new ExChannelCloser(
                                      createTempFileInSameDirectoryAs(zfpath),
                                      ch,
                                      streams);
            zfpath.moveTo(ecc.path, REPLACE_EXISTING);
            exChClosers.add(ecc);
            streams = Collections.synchronizedSet(new HashSet<InputStream>());
        } else {
            ch.close();
            zfpath.delete();
        }
        tmpFile.moveTo(zfpath, REPLACE_EXISTING);
        hasUpdate = false;    // clear

        if (isOpen) {
            ch = zfpath.newByteChannel(READ); // re-fresh "ch" and "cen"
            initCEN();
        }
        //System.out.println("->sync() done!");
    }

    private Entry getEntry0(byte[] path) throws IOException {
        assert Thread.holdsLock(this);

        if (path == null)
            throw new NullPointerException("path");
        if (path.length == 0)
            return null;
        EntryName en = new EntryName(path);
        IndexNode inode = null;
        synchronized (lock) {
            ensureOpen();
            if ((inode = inodes.get(en)) == null) {
                if (path[path.length -1] == '/')  // already has a slash
                    return null;
                path = Arrays.copyOf(path, path.length + 1);
                path[path.length - 1] = '/';
                en.name(path);
                if ((inode = inodes.get(en)) == null)
                    return null;
            }
            if (inode instanceof Entry)
                return (Entry)inode;
            return Entry.readCEN(cen, inode.pos);
        }
    }

    // Test if the "name" a parent directory of any entry (dir empty)
    boolean isAncestor(byte[] name) {
        for (Map.Entry<EntryName, IndexNode> entry : inodes.entrySet()) {
            byte[] ename = entry.getKey().name;
            if (isParentOf(name, ename))
                return true;
        }
        return false;
    }

    public void deleteFile(byte[] path, boolean failIfNotExists)
        throws IOException
    {
        checkWritable();
        synchronized(lock) {
            Entry e = getEntry0(path);
            if (e == null) {
                if (path != null && path.length == 0)
                    throw new ZipException("root directory </> can't not be delete");
                if (failIfNotExists)
                    throw new NoSuchFileException(getString(path));
            } else {
                if (e.isDir() && isAncestor(path))
                    throw new DirectoryNotEmptyException(getString(path));
                updateDelete(e);
            }
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

        ensureOpen();
        if (e.mtime == -1)
            e.mtime = javaToDosTime(System.currentTimeMillis());
        if (e.method == -1)
            e.method = METHOD_DEFLATED;  // TBD:  use default method
        // store size, compressed size, and crc-32 in LOC header
        e.flag = 0;
        if (zc.isUTF8())
            e.flag |= FLAG_EFS;
        OutputStream os;
        if (useTempFile) {
            e.file = getTempPathForEntry(null);
            os = e.file.newOutputStream(WRITE);
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
                eis = e.file.newInputStream();
            else
                throw new ZipException("update entry data is missing");
        } else if (e.type == Entry.FILECH) {
            // FILECH result is un-compressed.
            eis = e.file.newInputStream();
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
            final long size = e.size;;
            eis = new InflaterInputStream(eis, getInflater(), (int)bufSize) {

                private boolean isClosed = false;
                public void close() throws IOException {
                    if (!isClosed) {
                        releaseInflater(inf);
                        this.in.close();
                        isClosed = true;
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
        } else if (e.method != METHOD_STORED) {
            throw new ZipException("invalid compression method");
        }
        streams.add(eis);
        return eis;
    }

    // Inner class implementing the input stream used to read
    // a (possibly compressed) zip file entry.
    private class EntryInputStream extends InputStream {
        private SeekableByteChannel zfch; // local ref to zipfs's "ch". zipfs.ch might
                                          // point to a new channel after sync()
        private   long pos;               // current position within entry data
        protected long rem;               // number of remaining bytes within entry
        protected long size;              // uncompressed size of this entry

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

    private static void zerror(String msg) {
        throw new ZipError(msg);
    }

    // Maxmum number of de/inflater we cache
    private final int MAX_FLATER = 20;
    // List of available Inflater objects for decompression
    private List<Inflater> inflaters = new ArrayList<>();

    // Gets an inflater from the list of available inflaters or allocates
    // a new one.
    private Inflater getInflater() {
        synchronized (inflaters) {
            int size = inflaters.size();
            if (size > 0) {
                Inflater inf = (Inflater)inflaters.remove(size - 1);
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
    private List<Deflater> deflaters = new ArrayList<>();

    // Gets an deflater from the list of available deflaters or allocates
    // a new one.
    private Deflater getDeflater() {
        synchronized (deflaters) {
            int size = deflaters.size();
            if (size > 0) {
                Deflater def = (Deflater)deflaters.remove(size - 1);
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

    // wrapper for the byte[] name
    static class EntryName {
        byte[] name;
        int hashcode;    // node is hashable/hashed by its name

        public EntryName (byte[] name) {
            name(name);
        }

        void name(byte[] name) {
            this.name = name;
            this.hashcode = Arrays.hashCode(name);
        }

        public boolean equals(Object other) {
            if (!(other instanceof EntryName))
                return false;
            return Arrays.equals(name, ((EntryName)other).name);
        }

        public int hashCode() {
            return hashcode;
        }
    }

    // can simply use Integer instead, if we don't use it to
    // build a internal node tree.
    static class IndexNode {
        byte[] name;
        int pos = -1;     // postion in cen table, -1 menas the
                          // entry does not exists in zip file
        IndexNode(byte[] name, int pos) {
            this.name = name;
            this.pos = pos;
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
        int    nlen;
        int    elen;
        byte[] extra;

        // loc
        long   startPos;
        long   endPos;         // exclusive

        // cen
        int    versionMade;
        int    disk;
        int    attrs;
        long   attrsEx;
        long   locoff;

        int    clen;
        byte[] comment;

        // ZIP64 flag
        boolean hasZip64;

        Entry() {}

        Entry(byte[] name) {
            this.name = name;
            //this.nlen = name.length;
            this.mtime  = javaToDosTime(System.currentTimeMillis());
            this.crc       = 0;
            this.size      = 0;
            this.csize     = 0;
            this.method    = METHOD_DEFLATED;
        }

        Entry(byte[] name, int type) {
            this(name);
            this.type = type;
        }

        Entry (byte[] name, Path file, int type) {
            this(name, type);
            this.file = file;
            this.method = METHOD_STORED;
        }

        Entry(Entry e) {
            this.version   = e.version;
            this.name      = e.name;  // copyOf?
            this.nlen      = e.nlen;
            this.ctime     = e.ctime;
            this.atime     = e.atime;
            this.mtime     = e.mtime;
            this.crc       = e.crc;
            this.size      = e.size;
            this.csize     = e.csize;
            this.method    = e.method;
            this.extra     = (e.extra == null)?
                             null:Arrays.copyOf(e.extra, e.extra.length);
            this.elen      = e.elen;
            this.versionMade = e.versionMade;
            this.disk      = e.disk;
            this.attrs     = e.attrs;
            this.attrsEx   = e.attrsEx;
            this.locoff    = e.locoff;
            this.clen      = e.clen;
            this.comment   = (e.comment == null)?
                             null:Arrays.copyOf(e.comment, e.comment.length);
            this.startPos = e.startPos;
            this.endPos   = e.endPos;
            this.hasZip64 = e.hasZip64;;
        }

        Entry (Entry e, int type) {
            this(e);
            this.type = type;
        }

        boolean isDir() {
            return name != null &&
                   (name.length == 0 ||
                    name[name.length - 1] == '/');
        }

        int version() throws ZipException {
            if (method == METHOD_DEFLATED)
                return 20;
            else if (method == METHOD_STORED)
                return 10;
            throw new ZipException("unsupported compression method");
        }

        ///////////////////// CEN //////////////////////
        static Entry readCEN(ByteBuffer cen, int pos) throws IOException
        {
            return new Entry().cen(cen, pos);
        }

        private Entry cen(ByteBuffer cen, int pos) throws IOException
        {
            if (CENSIG(cen, pos) != CENSIG)
                zerror("invalid CEN header (bad signature)");
            versionMade = CENVEM(cen, pos);
            version     = CENVER(cen, pos);
            flag        = CENFLG(cen, pos);
            method      = CENHOW(cen, pos);
            mtime       = CENTIM(cen, pos);
            crc         = CENCRC(cen, pos);
            csize       = CENSIZ(cen, pos);
            size        = CENLEN(cen, pos);
            nlen        = CENNAM(cen, pos);
            elen        = CENEXT(cen, pos);
            clen        = CENCOM(cen, pos);
            disk        = CENDSK(cen, pos);
            attrs       = CENATT(cen, pos);
            attrsEx     = CENATX(cen, pos);
            locoff      = CENOFF(cen, pos);

            cen.position(pos + CENHDR);
            name = new byte[nlen];
            cen.get(name);

            if (elen > 0) {
                extra = new byte[elen];
                cen.get(extra);
                if (csize == ZIP64_MINVAL || size == ZIP64_MINVAL ||
                    locoff == ZIP64_MINVAL) {
                    int off = 0;
                    while (off + 4 < elen) {
                        // extra spec: HeaderID+DataSize+Data
                        int sz = SH(extra, off + 2);
                        if (SH(extra, off) == EXTID_ZIP64) {
                            off += 4;
                            if (size == ZIP64_MINVAL) {
                                // if invalid zip64 extra fields, just skip
                                if (sz < 8 || (off + 8) > elen)
                                    break;
                                size = LL(extra, off);
                                sz -= 8;
                                off += 8;
                            }
                            if (csize == ZIP64_MINVAL) {
                                if (sz < 8 || (off + 8) > elen)
                                    break;
                                csize = LL(extra, off);
                                sz -= 8;
                                off += 8;
                            }
                            if (locoff == ZIP64_MINVAL) {
                                if (sz < 8 || (off + 8) > elen)
                                    break;
                                locoff = LL(extra, off);
                                sz -= 8;
                                off += 8;
                            }
                            break;
                        }
                        off += (sz + 4);
                    }
                }
            }
            if (clen > 0) {
                comment = new byte[clen];
                cen.get(comment);
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
            int e64len   = 0;

            // confirm size/length
            nlen = (name != null) ? name.length : 0;
            elen = (extra != null) ? extra.length : 0;
            clen = (comment != null) ? comment.length : 0;

            boolean hasZip64 = false;
            if (csize >= ZIP64_MINVAL) {
                csize0 = ZIP64_MINVAL;
                e64len += 8;                 // csize(8)
                hasZip64 = true;
            }
            if (size >= ZIP64_MINVAL) {
                size0 = ZIP64_MINVAL;        // size(8)
                e64len += 8;
                hasZip64 = true;
            }
            if (locoff >= ZIP64_MINVAL) {
                locoff0 = ZIP64_MINVAL;
                e64len += 8;                 // offset(8)
                hasZip64 = true;
            }
            writeInt(os, CENSIG);            // CEN header signature
            if (hasZip64) {
                writeShort(os, 45);          // ver 4.5 for zip64
                writeShort(os, 45);
            } else {
                writeShort(os, version0);    // version made by
                writeShort(os, version0);    // version needed to extract
            }
            writeShort(os, flag);            // general purpose bit flag
            writeShort(os, method);          // compression method
            writeInt(os, mtime);             // last modification time
            writeInt(os, crc);               // crc-32
            writeInt(os, csize0);            // compressed size
            writeInt(os, size0);             // uncompressed size
            writeShort(os, name.length);

            if (hasZip64) {
                // + headid(2) + datasize(2)
                writeShort(os, e64len + 4 + elen);
            } else {
                writeShort(os, elen);
            }
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
            if (hasZip64) {
                writeShort(os, EXTID_ZIP64);// Zip64 extra
                writeShort(os, e64len);
                if (size0 == ZIP64_MINVAL)
                    writeLong(os, size);
                if (csize0 == ZIP64_MINVAL)
                    writeLong(os, csize);
                if (locoff0 == ZIP64_MINVAL)
                    writeLong(os, locoff);
            }
            if (extra != null) {
                writeBytes(os, extra);
            }
            if (comment != null) {
                //TBD: 0, Math.min(commentBytes.length, 0xffff));
                writeBytes(os, comment);
            }
            return CENHDR + nlen + elen + clen + (hasZip64?(e64len + 4):0);
        }

        ///////////////////// LOC //////////////////////
        static Entry readLOC(ZipFileSystem zf, long pos)
            throws IOException
        {
            return readLOC(zf, pos, new byte[1024]);
        }

        static Entry readLOC(ZipFileSystem zf, long pos, byte[] buf)
            throws IOException
        {
            return new Entry().loc(zf, pos, buf);
        }

        Entry loc(ZipFileSystem zf, long pos, byte[] buf)
            throws IOException
        {
            assert (buf.length >= LOCHDR);
            if (zf.readFullyAt(buf, 0, LOCHDR , pos) != LOCHDR) {
                throw new ZipException("loc: reading failed");
            }
            if (LOCSIG(buf) != LOCSIG) {
                throw new ZipException("loc: wrong sig ->"
                                       + Long.toString(LOCSIG(buf), 16));
            }
            startPos = pos;
            version  = LOCVER(buf);
            flag     = LOCFLG(buf);
            method   = LOCHOW(buf);
            mtime    = LOCTIM(buf);
            crc      = LOCCRC(buf);
            csize    = LOCSIZ(buf);
            size     = LOCLEN(buf);
            nlen     = LOCNAM(buf);
            elen     = LOCEXT(buf);

            name = new byte[nlen];
            if (zf.readFullyAt(name, 0, nlen, pos + LOCHDR) != nlen) {
                throw new ZipException("loc: name reading failed");
            }
            if (elen > 0) {
                extra = new byte[elen];
                if (zf.readFullyAt(extra, 0, elen, pos + LOCHDR + nlen)
                    != elen) {
                    throw new ZipException("loc: ext reading failed");
                }
            }
            pos += (LOCHDR + nlen + elen);
            if ((flag & FLAG_DATADESCR) != 0) {
                // Data Descriptor
                Entry e = zf.getEntry0(name);  // get the size/csize from cen
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
                boolean hasZip64 = false;
                if (extra != null &&
                    (size == ZIP64_MINVAL || csize == ZIP64_MINVAL)) {
                    // zip64 ext: must include both size and csize
                    int off = 0;
                    while (off + 20 < elen) {    // HeaderID+DataSize+Data
                        int sz = SH(extra, off + 2);
                        if (SH(extra, off) == EXTID_ZIP64 && sz == 16) {
                            size = LL(extra, off + 4);
                            csize = LL(extra, off + 12);
                            hasZip64 = true;
                            break;
                        }
                        off += (sz + 4);
                    }
                }
                pos += (method == METHOD_STORED ? size : csize);
            }
            endPos = pos;
            return this;
        }

        int writeLOC(OutputStream os)
            throws IOException
        {
            writeInt(os, LOCSIG);               // LOC header signature

            int version = version();
            if ((flag & FLAG_DATADESCR) != 0) {
                writeShort(os, version());      // version needed to extract
                writeShort(os, flag);           // general purpose bit flag
                writeShort(os, method);         // compression method
                writeInt(os, mtime);            // last modification time

                // store size, uncompressed size, and crc-32 in data descriptor
                // immediately following compressed entry data
                writeInt(os, 0);
                writeInt(os, 0);
                writeInt(os, 0);
            } else {
                if (csize >= ZIP64_MINVAL || size >= ZIP64_MINVAL) {
                    hasZip64 = true;
                    writeShort(os, 45);         // ver 4.5 for zip64
                } else {
                    writeShort(os, version());  // version needed to extract
                }
                writeShort(os, flag);           // general purpose bit flag
                writeShort(os, method);         // compression method
                writeInt(os, mtime);            // last modification time
                writeInt(os, crc);              // crc-32
                if (hasZip64) {
                    writeInt(os, ZIP64_MINVAL);
                    writeInt(os, ZIP64_MINVAL);
                    //TBD:  e.elen += 20;       //headid(2) + size(2) + size(8) + csize(8)
                } else {
                    writeInt(os, csize);        // compressed size
                    writeInt(os, size);         // uncompressed size
                }
            }
            writeShort(os, name.length);
            writeShort(os, elen + (hasZip64 ? 20 : 0));
            writeBytes(os, name);
            if (hasZip64) {
                // TBD: should we update extra directory?
                writeShort(os, EXTID_ZIP64);
                writeShort(os, 16);
                writeLong(os, size);
                writeLong(os, csize);
            }
            if (extra != null) {
                writeBytes(os, extra);
            }
            return LOCHDR + name.length + elen + (hasZip64 ? 20 : 0);
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
        void readExtra() {
            if (extra == null)
                return;
            int elen = extra.length;
            int off = 0;
            while (off + 4 < elen) {
                // extra spec: HeaderID+DataSize+Data
                int sz = SH(extra, off + 2);
                int tag = SH(extra, off);
                off += 4;
                int pos = off;
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
                    mtime = LL(extra, pos + 4);
                    atime = LL(extra, pos + 12);
                    ctime = LL(extra, pos + 20);
                    break;
                case EXTID_UNIX:
                    atime = LG(extra, pos);
                    mtime = LG(extra, pos + 4);
                    break;
                default:    // unknow
                }
                off += sz;
            }
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
    private HashMap<EntryName, IndexNode> dirs;
    private IndexNode root;
    private IndexNode addToDir(EntryName child) {
        IndexNode cinode = dirs.get(child);
        if (cinode != null)
            return cinode;

        byte[] cname = child.name;
        byte[] pname = getParent(cname);
        IndexNode pinode;

        if (pname != null)
            pinode = addToDir(new EntryName(pname));
        else
            pinode = root;
        cinode = inodes.get(child);
        if (cname[cname.length -1] != '/') {  // not a dir
            cinode.sibling = pinode.child;
            pinode.child = cinode;
            return null;
        }
        cinode = dirs.get(child);
        if (cinode == null)  // pseudo directry entry
            cinode = new IndexNode(cname, -1);
        cinode.sibling = pinode.child;
        pinode.child = cinode;

        dirs.put(child, cinode);
        return cinode;
    }

    private HashMap<EntryName, IndexNode> getDirs()
        throws IOException
    {
        if (hasUpdate)
            sync();
        if (dirs != null)
            return dirs;
        dirs = new HashMap<EntryName, IndexNode>();
        byte[] empty = new byte[0];
        root = new IndexNode(empty, -1);
        dirs.put(new EntryName(empty), root);

        EntryName[] names = inodes.keySet().toArray(new EntryName[0]);
        int i = names.length;
        while (--i >= 0) {
            addToDir(names[i]);
        }
        // for (int i EntryName en : inodes.keySet()) {
        //     addToDir(en);
        // }
        return dirs;
    }
}
