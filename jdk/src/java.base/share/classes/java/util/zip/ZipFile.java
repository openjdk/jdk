/*
 * Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.nio.file.Files;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.misc.JavaUtilZipFileAccess;
import jdk.internal.misc.SharedSecrets;

import static java.util.zip.ZipConstants.*;
import static java.util.zip.ZipConstants64.*;
import static java.util.zip.ZipUtils.*;

/**
 * This class is used to read entries from a zip file.
 *
 * <p> Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 *
 * @author      David Connelly
 */
public
class ZipFile implements ZipConstants, Closeable {

    private final String name;     // zip file name
    private volatile boolean closeRequested = false;
    private Source zsrc;
    private ZipCoder zc;

    private static final int STORED = ZipEntry.STORED;
    private static final int DEFLATED = ZipEntry.DEFLATED;

    /**
     * Mode flag to open a zip file for reading.
     */
    public static final int OPEN_READ = 0x1;

    /**
     * Mode flag to open a zip file and mark it for deletion.  The file will be
     * deleted some time between the moment that it is opened and the moment
     * that it is closed, but its contents will remain accessible via the
     * {@code ZipFile} object until either the close method is invoked or the
     * virtual machine exits.
     */
    public static final int OPEN_DELETE = 0x4;

    /**
     * Opens a zip file for reading.
     *
     * <p>First, if there is a security manager, its {@code checkRead}
     * method is called with the {@code name} argument as its argument
     * to ensure the read is allowed.
     *
     * <p>The UTF-8 {@link java.nio.charset.Charset charset} is used to
     * decode the entry names and comments.
     *
     * @param name the name of the zip file
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     * @throws SecurityException if a security manager exists and its
     *         {@code checkRead} method doesn't allow read access to the file.
     *
     * @see SecurityManager#checkRead(java.lang.String)
     */
    public ZipFile(String name) throws IOException {
        this(new File(name), OPEN_READ);
    }

    /**
     * Opens a new {@code ZipFile} to read from the specified
     * {@code File} object in the specified mode.  The mode argument
     * must be either {@code OPEN_READ} or {@code OPEN_READ | OPEN_DELETE}.
     *
     * <p>First, if there is a security manager, its {@code checkRead}
     * method is called with the {@code name} argument as its argument to
     * ensure the read is allowed.
     *
     * <p>The UTF-8 {@link java.nio.charset.Charset charset} is used to
     * decode the entry names and comments
     *
     * @param file the ZIP file to be opened for reading
     * @param mode the mode in which the file is to be opened
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     * @throws SecurityException if a security manager exists and
     *         its {@code checkRead} method
     *         doesn't allow read access to the file,
     *         or its {@code checkDelete} method doesn't allow deleting
     *         the file when the {@code OPEN_DELETE} flag is set.
     * @throws IllegalArgumentException if the {@code mode} argument is invalid
     * @see SecurityManager#checkRead(java.lang.String)
     * @since 1.3
     */
    public ZipFile(File file, int mode) throws IOException {
        this(file, mode, StandardCharsets.UTF_8);
    }

    /**
     * Opens a ZIP file for reading given the specified File object.
     *
     * <p>The UTF-8 {@link java.nio.charset.Charset charset} is used to
     * decode the entry names and comments.
     *
     * @param file the ZIP file to be opened for reading
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     */
    public ZipFile(File file) throws ZipException, IOException {
        this(file, OPEN_READ);
    }

    /**
     * Opens a new {@code ZipFile} to read from the specified
     * {@code File} object in the specified mode.  The mode argument
     * must be either {@code OPEN_READ} or {@code OPEN_READ | OPEN_DELETE}.
     *
     * <p>First, if there is a security manager, its {@code checkRead}
     * method is called with the {@code name} argument as its argument to
     * ensure the read is allowed.
     *
     * @param file the ZIP file to be opened for reading
     * @param mode the mode in which the file is to be opened
     * @param charset
     *        the {@linkplain java.nio.charset.Charset charset} to
     *        be used to decode the ZIP entry name and comment that are not
     *        encoded by using UTF-8 encoding (indicated by entry's general
     *        purpose flag).
     *
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     *
     * @throws SecurityException
     *         if a security manager exists and its {@code checkRead}
     *         method doesn't allow read access to the file,or its
     *         {@code checkDelete} method doesn't allow deleting the
     *         file when the {@code OPEN_DELETE} flag is set
     *
     * @throws IllegalArgumentException if the {@code mode} argument is invalid
     *
     * @see SecurityManager#checkRead(java.lang.String)
     *
     * @since 1.7
     */
    public ZipFile(File file, int mode, Charset charset) throws IOException
    {
        if (((mode & OPEN_READ) == 0) ||
            ((mode & ~(OPEN_READ | OPEN_DELETE)) != 0)) {
            throw new IllegalArgumentException("Illegal mode: 0x"+
                                               Integer.toHexString(mode));
        }
        String name = file.getPath();
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkRead(name);
            if ((mode & OPEN_DELETE) != 0) {
                sm.checkDelete(name);
            }
        }
        Objects.requireNonNull(charset, "charset");
        this.zc = ZipCoder.get(charset);
        this.name = name;
        long t0 = System.nanoTime();
        this.zsrc = Source.get(file, (mode & OPEN_DELETE) != 0);
        sun.misc.PerfCounter.getZipFileOpenTime().addElapsedTimeFrom(t0);
        sun.misc.PerfCounter.getZipFileCount().increment();
    }

    /**
     * Opens a zip file for reading.
     *
     * <p>First, if there is a security manager, its {@code checkRead}
     * method is called with the {@code name} argument as its argument
     * to ensure the read is allowed.
     *
     * @param name the name of the zip file
     * @param charset
     *        the {@linkplain java.nio.charset.Charset charset} to
     *        be used to decode the ZIP entry name and comment that are not
     *        encoded by using UTF-8 encoding (indicated by entry's general
     *        purpose flag).
     *
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     * @throws SecurityException
     *         if a security manager exists and its {@code checkRead}
     *         method doesn't allow read access to the file
     *
     * @see SecurityManager#checkRead(java.lang.String)
     *
     * @since 1.7
     */
    public ZipFile(String name, Charset charset) throws IOException
    {
        this(new File(name), OPEN_READ, charset);
    }

    /**
     * Opens a ZIP file for reading given the specified File object.
     *
     * @param file the ZIP file to be opened for reading
     * @param charset
     *        The {@linkplain java.nio.charset.Charset charset} to be
     *        used to decode the ZIP entry name and comment (ignored if
     *        the <a href="package-summary.html#lang_encoding"> language
     *        encoding bit</a> of the ZIP entry's general purpose bit
     *        flag is set).
     *
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     *
     * @since 1.7
     */
    public ZipFile(File file, Charset charset) throws IOException
    {
        this(file, OPEN_READ, charset);
    }

    /**
     * Returns the zip file comment, or null if none.
     *
     * @return the comment string for the zip file, or null if none
     *
     * @throws IllegalStateException if the zip file has been closed
     *
     * Since 1.7
     */
    public String getComment() {
        synchronized (this) {
            ensureOpen();
            if (zsrc.comment == null) {
                return null;
            }
            return zc.toString(zsrc.comment);
        }
    }

    /**
     * Returns the zip file entry for the specified name, or null
     * if not found.
     *
     * @param name the name of the entry
     * @return the zip file entry, or null if not found
     * @throws IllegalStateException if the zip file has been closed
     */
    public ZipEntry getEntry(String name) {
        Objects.requireNonNull(name, "name");
        synchronized (this) {
            ensureOpen();
            int pos = zsrc.getEntryPos(zc.getBytes(name), true);
            if (pos != -1) {
                return getZipEntry(name, pos);
            }
        }
        return null;
    }

    // The outstanding inputstreams that need to be closed,
    // mapped to the inflater objects they use.
    private final Map<InputStream, Inflater> streams = new WeakHashMap<>();

    /**
     * Returns an input stream for reading the contents of the specified
     * zip file entry.
     * <p>
     * Closing this ZIP file will, in turn, close all input streams that
     * have been returned by invocations of this method.
     *
     * @param entry the zip file entry
     * @return the input stream for reading the contents of the specified
     * zip file entry.
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     * @throws IllegalStateException if the zip file has been closed
     */
    public InputStream getInputStream(ZipEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        int pos = -1;
        ZipFileInputStream in = null;
        synchronized (this) {
            ensureOpen();
            if (!zc.isUTF8() && (entry.flag & EFS) != 0) {
                pos = zsrc.getEntryPos(zc.getBytesUTF8(entry.name), false);
            } else {
                pos = zsrc.getEntryPos(zc.getBytes(entry.name), false);
            }
            if (pos == -1) {
                return null;
            }
            in = new ZipFileInputStream(zsrc.cen, pos);
            switch (CENHOW(zsrc.cen, pos)) {
            case STORED:
                synchronized (streams) {
                    streams.put(in, null);
                }
                return in;
            case DEFLATED:
                // Inflater likes a bit of slack
                // MORE: Compute good size for inflater stream:
                long size = CENLEN(zsrc.cen, pos) + 2;
                if (size > 65536) {
                    size = 8192;
                }
                if (size <= 0) {
                    size = 4096;
                }
                Inflater inf = getInflater();
                InputStream is = new ZipFileInflaterInputStream(in, inf, (int)size);
                synchronized (streams) {
                    streams.put(is, inf);
                }
                return is;
            default:
                throw new ZipException("invalid compression method");
            }
        }
    }

    private class ZipFileInflaterInputStream extends InflaterInputStream {
        private volatile boolean closeRequested = false;
        private boolean eof = false;
        private final ZipFileInputStream zfin;

        ZipFileInflaterInputStream(ZipFileInputStream zfin, Inflater inf,
                int size) {
            super(zfin, inf, size);
            this.zfin = zfin;
        }

        public void close() throws IOException {
            if (closeRequested)
                return;
            closeRequested = true;

            super.close();
            Inflater inf;
            synchronized (streams) {
                inf = streams.remove(this);
            }
            if (inf != null) {
                releaseInflater(inf);
            }
        }

        // Override fill() method to provide an extra "dummy" byte
        // at the end of the input stream. This is required when
        // using the "nowrap" Inflater option.
        protected void fill() throws IOException {
            if (eof) {
                throw new EOFException("Unexpected end of ZLIB input stream");
            }
            len = in.read(buf, 0, buf.length);
            if (len == -1) {
                buf[0] = 0;
                len = 1;
                eof = true;
            }
            inf.setInput(buf, 0, len);
        }

        public int available() throws IOException {
            if (closeRequested)
                return 0;
            long avail = zfin.size() - inf.getBytesWritten();
            return (avail > (long) Integer.MAX_VALUE ?
                    Integer.MAX_VALUE : (int) avail);
        }

        protected void finalize() throws Throwable {
            close();
        }
    }

    /*
     * Gets an inflater from the list of available inflaters or allocates
     * a new one.
     */
    private Inflater getInflater() {
        Inflater inf;
        synchronized (inflaterCache) {
            while ((inf = inflaterCache.poll()) != null) {
                if (!inf.ended()) {
                    return inf;
                }
            }
        }
        return new Inflater(true);
    }

    /*
     * Releases the specified inflater to the list of available inflaters.
     */
    private void releaseInflater(Inflater inf) {
        if (!inf.ended()) {
            inf.reset();
            synchronized (inflaterCache) {
                inflaterCache.add(inf);
            }
        }
    }

    // List of available Inflater objects for decompression
    private final Deque<Inflater> inflaterCache = new ArrayDeque<>();

    /**
     * Returns the path name of the ZIP file.
     * @return the path name of the ZIP file
     */
    public String getName() {
        return name;
    }

    private class ZipEntryIterator implements Enumeration<ZipEntry>, Iterator<ZipEntry> {
        private int i = 0;

        public ZipEntryIterator() {
            ensureOpen();
        }

        public boolean hasMoreElements() {
            return hasNext();
        }

        public boolean hasNext() {
            synchronized (ZipFile.this) {
                ensureOpen();
                return i < zsrc.total;
            }
        }

        public ZipEntry nextElement() {
            return next();
        }

        public ZipEntry next() {
            synchronized (ZipFile.this) {
                ensureOpen();
                if (i >= zsrc.total) {
                    throw new NoSuchElementException();
                }
                // each "entry" has 3 ints in table entries
                return getZipEntry(null, zsrc.getEntryPos(i++ * 3));
            }
        }

        public Iterator<ZipEntry> asIterator() {
            return this;
        }
    }

    /**
     * Returns an enumeration of the ZIP file entries.
     * @return an enumeration of the ZIP file entries
     * @throws IllegalStateException if the zip file has been closed
     */
    public Enumeration<? extends ZipEntry> entries() {
        return new ZipEntryIterator();
    }

    /**
     * Returns an ordered {@code Stream} over the ZIP file entries.
     * Entries appear in the {@code Stream} in the order they appear in
     * the central directory of the ZIP file.
     *
     * @return an ordered {@code Stream} of entries in this ZIP file
     * @throws IllegalStateException if the zip file has been closed
     * @since 1.8
     */
    public Stream<? extends ZipEntry> stream() {
        return StreamSupport.stream(Spliterators.spliterator(
                new ZipEntryIterator(), size(),
                Spliterator.ORDERED | Spliterator.DISTINCT |
                        Spliterator.IMMUTABLE | Spliterator.NONNULL), false);
    }

    /* Checks ensureOpen() before invoke this method */
    private ZipEntry getZipEntry(String name, int pos) {
        byte[] cen = zsrc.cen;
        ZipEntry e = new ZipEntry();
        int nlen = CENNAM(cen, pos);
        int elen = CENEXT(cen, pos);
        int clen = CENCOM(cen, pos);
        e.flag = CENFLG(cen, pos);  // get the flag first
        if (name != null) {
            e.name = name;
        } else {
            if (!zc.isUTF8() && (e.flag & EFS) != 0) {
                e.name = zc.toStringUTF8(cen, pos + CENHDR, nlen);
            } else {
                e.name = zc.toString(cen, pos + CENHDR, nlen);
            }
        }
        e.xdostime = CENTIM(cen, pos);
        e.crc = CENCRC(cen, pos);
        e.size = CENLEN(cen, pos);
        e.csize = CENSIZ(cen, pos);
        e.method = CENHOW(cen, pos);
        if (elen != 0) {
            e.setExtra0(Arrays.copyOfRange(cen, pos + CENHDR + nlen,
                                           pos + CENHDR + nlen + elen), true);
        }
        if (clen != 0) {
            if (!zc.isUTF8() && (e.flag & EFS) != 0) {
                e.comment = zc.toStringUTF8(cen, pos + CENHDR + nlen + elen, clen);
            } else {
                e.comment = zc.toString(cen, pos + CENHDR + nlen + elen, clen);
            }
        }
        return e;
    }

    /**
     * Returns the number of entries in the ZIP file.
     *
     * @return the number of entries in the ZIP file
     * @throws IllegalStateException if the zip file has been closed
     */
    public int size() {
        synchronized (this) {
            ensureOpen();
            return zsrc.total;
        }
    }

    /**
     * Closes the ZIP file.
     * <p> Closing this ZIP file will close all of the input streams
     * previously returned by invocations of the {@link #getInputStream
     * getInputStream} method.
     *
     * @throws IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (closeRequested) {
            return;
        }
        closeRequested = true;

        synchronized (this) {
            // Close streams, release their inflaters
            synchronized (streams) {
                if (!streams.isEmpty()) {
                    Map<InputStream, Inflater> copy = new HashMap<>(streams);
                    streams.clear();
                    for (Map.Entry<InputStream, Inflater> e : copy.entrySet()) {
                        e.getKey().close();
                        Inflater inf = e.getValue();
                        if (inf != null) {
                            inf.end();
                        }
                    }
                }
            }
            // Release cached inflaters
            synchronized (inflaterCache) {
                Inflater inf;
                while ((inf = inflaterCache.poll()) != null) {
                    inf.end();
                }
            }
            // Release zip src
            if (zsrc != null) {
                Source.close(zsrc);
                zsrc = null;
            }
        }
    }

    /**
     * Ensures that the system resources held by this ZipFile object are
     * released when there are no more references to it.
     *
     * <p>
     * Since the time when GC would invoke this method is undetermined,
     * it is strongly recommended that applications invoke the {@code close}
     * method as soon they have finished accessing this {@code ZipFile}.
     * This will prevent holding up system resources for an undetermined
     * length of time.
     *
     * @throws IOException if an I/O error has occurred
     * @see    java.util.zip.ZipFile#close()
     */
    protected void finalize() throws IOException {
        close();
    }

    private void ensureOpen() {
        if (closeRequested) {
            throw new IllegalStateException("zip file closed");
        }
        if (zsrc == null) {
            throw new IllegalStateException("The object is not initialized.");
        }
    }

    private void ensureOpenOrZipException() throws IOException {
        if (closeRequested) {
            throw new ZipException("ZipFile closed");
        }
    }

    /*
     * Inner class implementing the input stream used to read a
     * (possibly compressed) zip file entry.
     */
   private class ZipFileInputStream extends InputStream {
        private volatile boolean closeRequested = false;
        private   long pos;     // current position within entry data
        protected long rem;     // number of remaining bytes within entry
        protected long size;    // uncompressed size of this entry

        ZipFileInputStream(byte[] cen, int cenpos) throws IOException {
            rem = CENSIZ(cen, cenpos);
            size = CENLEN(cen, cenpos);
            pos = CENOFF(cen, cenpos);
            // zip64
            if (rem == ZIP64_MAGICVAL || size == ZIP64_MAGICVAL ||
                pos == ZIP64_MAGICVAL) {
                checkZIP64(cen, cenpos);
            }
            // negative for lazy initialization, see getDataOffset();
            pos = - (pos + ZipFile.this.zsrc.locpos);
        }

        private void checkZIP64(byte[] cen, int cenpos) throws IOException {
            int off = cenpos + CENHDR + CENNAM(cen, cenpos);
            int end = off + CENEXT(cen, cenpos);
            while (off + 4 < end) {
                int tag = get16(cen, off);
                int sz = get16(cen, off + 2);
                off += 4;
                if (off + sz > end)         // invalid data
                    break;
                if (tag == EXTID_ZIP64) {
                    if (size == ZIP64_MAGICVAL) {
                        if (sz < 8 || (off + 8) > end)
                            break;
                        size = get64(cen, off);
                        sz -= 8;
                        off += 8;
                    }
                    if (rem == ZIP64_MAGICVAL) {
                        if (sz < 8 || (off + 8) > end)
                            break;
                        rem = get64(cen, off);
                        sz -= 8;
                        off += 8;
                    }
                    if (pos == ZIP64_MAGICVAL) {
                        if (sz < 8 || (off + 8) > end)
                            break;
                        pos = get64(cen, off);
                        sz -= 8;
                        off += 8;
                    }
                    break;
                }
                off += sz;
            }
        }

       /* The Zip file spec explicitly allows the LOC extra data size to
        * be different from the CEN extra data size. Since we cannot trust
        * the CEN extra data size, we need to read the LOC to determine
        * the entry data offset.
        */
        private long initDataOffset() throws IOException {
            if (pos <= 0) {
                byte[] loc = new byte[LOCHDR];
                pos = -pos;
                int len = ZipFile.this.zsrc.readFullyAt(loc, 0, loc.length, pos);
                if (len != LOCHDR) {
                    throw new ZipException("ZipFile error reading zip file");
                }
                if (LOCSIG(loc) != LOCSIG) {
                    throw new ZipException("ZipFile invalid LOC header (bad signature)");
                }
                pos += LOCHDR + LOCNAM(loc) + LOCEXT(loc);
            }
            return pos;
        }

        public int read(byte b[], int off, int len) throws IOException {
            synchronized (ZipFile.this) {
                ensureOpenOrZipException();
                initDataOffset();
                if (rem == 0) {
                    return -1;
                }
                if (len <= 0) {
                    return 0;
                }
                if (len > rem) {
                    len = (int) rem;
                }
                len = ZipFile.this.zsrc.readAt(b, off, len, pos);
                if (len > 0) {
                    pos += len;
                    rem -= len;
                }
            }
            if (rem == 0) {
                close();
            }
            return len;
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
            synchronized (ZipFile.this) {
                ensureOpenOrZipException();
                initDataOffset();
                if (n > rem) {
                    n = rem;
                }
                pos += n;
                rem -= n;
            }
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
            if (closeRequested) {
                return;
            }
            closeRequested = true;
            rem = 0;
            synchronized (streams) {
                streams.remove(this);
            }
        }

        protected void finalize() {
            close();
        }
    }

    static {
        SharedSecrets.setJavaUtilZipFileAccess(
            new JavaUtilZipFileAccess() {
                public boolean startsWithLocHeader(ZipFile zip) {
                    return zip.zsrc.locsig;
                }
                public String[] getMetaInfEntryNames(ZipFile zip) {
                    return zip.getMetaInfEntryNames();
                }
             }
        );
    }

    /*
     * Returns an array of strings representing the names of all entries
     * that begin with "META-INF/" (case ignored). This method is used
     * in JarFile, via SharedSecrets, as an optimization when looking up
     * manifest and signature file entries. Returns null if no entries
     * were found.
     */
    private String[] getMetaInfEntryNames() {
        synchronized (this) {
            ensureOpen();
            if (zsrc.metanames.size() == 0) {
                return null;
            }
            String[] names = new String[zsrc.metanames.size()];
            byte[] cen = zsrc.cen;
            for (int i = 0; i < names.length; i++) {
                int pos = zsrc.metanames.get(i);
                names[i] = zc.toStringUTF8(cen, pos + CENHDR,  CENNAM(cen, pos));
            }
            return names;
        }
    }

    private static class Source {
        private final Key key;               // the key in files
        private int refs = 1;

        private RandomAccessFile zfile;      // zfile of the underlying zip file
        private byte[] cen;                  // CEN & ENDHDR
        private long locpos;                 // position of first LOC header (usually 0)
        private byte[] comment;              // zip file comment
                                             // list of meta entries in META-INF dir
        private ArrayList<Integer> metanames = new ArrayList<>();
        private final boolean locsig;        // true, if zip file starts with LOCSIG (usually true)

        // A Hashmap for all entries.
        //
        // A cen entry of Zip/JAR file. As we have one for every entry in every active Zip/JAR,
        // We might have a lot of these in a typical system. In order to save space we don't
        // keep the name in memory, but merely remember a 32 bit {@code hash} value of the
        // entry name and its offset {@code pos} in the central directory hdeader.
        //
        // private static class Entry {
        //     int hash;       // 32 bit hashcode on name
        //     int next;       // hash chain: index into entries
        //     int pos;        // Offset of central directory file header
        // }
        // private Entry[] entries;             // array of hashed cen entry
        //
        // To reduce the total size of entries further, we use a int[] here to store 3 "int"
        // {@code hash}, {@code next and {@code "pos for each entry. The entry can then be
        // referred by their index of their positions in the {@code entries}.
        //
        private int[] entries;                  // array of hashed cen entry
        private int addEntry(int index, int hash, int next, int pos) {
            entries[index++] = hash;
            entries[index++] = next;
            entries[index++] = pos;
            return index;
        }
        private int getEntryHash(int index) { return entries[index]; }
        private int getEntryNext(int index) { return entries[index + 1]; }
        private int getEntryPos(int index)  { return entries[index + 2]; }
        private static final int ZIP_ENDCHAIN  = -1;
        private int total;                   // total number of entries
        private int[] table;                 // Hash chain heads: indexes into entries
        private int tablelen;                // number of hash heads

        private static class Key {
            BasicFileAttributes attrs;
            File file;

            public Key(File file, BasicFileAttributes attrs) {
                this.attrs = attrs;
                this.file = file;
            }

            public int hashCode() {
                long t = attrs.lastModifiedTime().toMillis();
                return ((int)(t ^ (t >>> 32))) + file.hashCode();
            }

            public boolean equals(Object obj) {
                if (obj instanceof Key) {
                    Key key = (Key)obj;
                    if (!attrs.lastModifiedTime().equals(key.attrs.lastModifiedTime())) {
                        return false;
                    }
                    Object fk = attrs.fileKey();
                    if (fk != null) {
                        return  fk.equals(key.attrs.fileKey());
                    } else {
                        return file.equals(key.file);
                    }
                }
                return false;
            }
        }
        private static final HashMap<Key, Source> files = new HashMap<>();

        public static Source get(File file, boolean toDelete) throws IOException {
            Key key = new Key(file,
                              Files.readAttributes(file.toPath(), BasicFileAttributes.class));
            Source src = null;
            synchronized (files) {
                src = files.get(key);
                if (src != null) {
                    src.refs++;
                    return src;
                }
            }
            src = new Source(key, toDelete);
            synchronized (files) {
                if (files.containsKey(key)) {    // someone else put in first
                    src.close();                 // close the newly created one
                    src = files.get(key);
                    src.refs++;
                    return src;
                }
                files.put(key, src);
                return src;
            }
        }

        private static void close(Source src) throws IOException {
            synchronized (files) {
                if (--src.refs == 0) {
                    files.remove(src.key);
                    src.close();
                }
            }
        }

        private Source(Key key, boolean toDelete) throws IOException {
            this.key = key;
            this.zfile = new RandomAccessFile(key.file, "r");
            if (toDelete) {
                key.file.delete();
            }
            initCEN(-1);
            byte[] buf = new byte[4];
            readFullyAt(buf, 0, 4, 0);
            this.locsig = (LOCSIG(buf) != LOCSIG);
        }

        private void close() throws IOException {
            zfile.close();
            zfile = null;
            cen = null;
            entries = null;
            table = null;
            metanames = null;
        }

        private static final int BUF_SIZE = 8192;
        private final int readFullyAt(byte[] buf, int off, int len, long pos)
            throws IOException
        {
            synchronized(zfile) {
                zfile.seek(pos);
                int N = len;
                while (N > 0) {
                    int n = Math.min(BUF_SIZE, N);
                    zfile.readFully(buf, off, n);
                    off += n;
                    N -= n;
                }
                return len;
            }
        }

        private final int readAt(byte[] buf, int off, int len, long pos)
            throws IOException
        {
            synchronized(zfile) {
                zfile.seek(pos);
                return zfile.read(buf, off, len);
            }
        }

        private static final int hashN(byte[] a, int off, int len) {
            int h = 1;
            while (len-- > 0) {
                h = 31 * h + a[off++];
            }
            return h;
        }

        private static final int hash_append(int hash, byte b) {
            return hash * 31 + b;
        }

        private static class End {
            int  centot;     // 4 bytes
            long cenlen;     // 4 bytes
            long cenoff;     // 4 bytes
            long endpos;     // 4 bytes
        }

        /*
         * Searches for end of central directory (END) header. The contents of
         * the END header will be read and placed in endbuf. Returns the file
         * position of the END header, otherwise returns -1 if the END header
         * was not found or an error occurred.
         */
        private End findEND() throws IOException {
            long ziplen = zfile.length();
            if (ziplen <= 0)
                zerror("zip file is empty");
            End end = new End();
            byte[] buf = new byte[READBLOCKSZ];
            long minHDR = (ziplen - END_MAXLEN) > 0 ? ziplen - END_MAXLEN : 0;
            long minPos = minHDR - (buf.length - ENDHDR);
            for (long pos = ziplen - buf.length; pos >= minPos; pos -= (buf.length - ENDHDR)) {
                int off = 0;
                if (pos < 0) {
                    // Pretend there are some NUL bytes before start of file
                    off = (int)-pos;
                    Arrays.fill(buf, 0, off, (byte)0);
                }
                int len = buf.length - off;
                if (readFullyAt(buf, off, len, pos + off) != len ) {
                    zerror("zip END header not found");
                }
                // Now scan the block backwards for END header signature
                for (int i = buf.length - ENDHDR; i >= 0; i--) {
                    if (buf[i+0] == (byte)'P'    &&
                        buf[i+1] == (byte)'K'    &&
                        buf[i+2] == (byte)'\005' &&
                        buf[i+3] == (byte)'\006') {
                        // Found ENDSIG header
                        byte[] endbuf = Arrays.copyOfRange(buf, i, i + ENDHDR);
                        end.centot = ENDTOT(endbuf);
                        end.cenlen = ENDSIZ(endbuf);
                        end.cenoff = ENDOFF(endbuf);
                        end.endpos = pos + i;
                        int comlen = ENDCOM(endbuf);
                        if (end.endpos + ENDHDR + comlen != ziplen) {
                            // ENDSIG matched, however the size of file comment in it does
                            // not match the real size. One "common" cause for this problem
                            // is some "extra" bytes are padded at the end of the zipfile.
                            // Let's do some extra verification, we don't care about the
                            // performance in this situation.
                            byte[] sbuf = new byte[4];
                            long cenpos = end.endpos - end.cenlen;
                            long locpos = cenpos - end.cenoff;
                            if  (cenpos < 0 ||
                                 locpos < 0 ||
                                 readFullyAt(sbuf, 0, sbuf.length, cenpos) != 4 ||
                                 GETSIG(sbuf) != CENSIG ||
                                 readFullyAt(sbuf, 0, sbuf.length, locpos) != 4 ||
                                 GETSIG(sbuf) != LOCSIG) {
                                continue;
                            }
                        }
                        if (comlen > 0) {    // this zip file has comlen
                            comment = new byte[comlen];
                            if (readFullyAt(comment, 0, comlen, end.endpos + ENDHDR) != comlen) {
                                zerror("zip comment read failed");
                            }
                        }
                        if (end.cenlen == ZIP64_MAGICVAL ||
                            end.cenoff == ZIP64_MAGICVAL ||
                            end.centot == ZIP64_MAGICCOUNT)
                        {
                            // need to find the zip64 end;
                            try {
                                byte[] loc64 = new byte[ZIP64_LOCHDR];
                                if (readFullyAt(loc64, 0, loc64.length, end.endpos - ZIP64_LOCHDR)
                                    != loc64.length || GETSIG(loc64) != ZIP64_LOCSIG) {
                                    return end;
                                }
                                long end64pos = ZIP64_LOCOFF(loc64);
                                byte[] end64buf = new byte[ZIP64_ENDHDR];
                                if (readFullyAt(end64buf, 0, end64buf.length, end64pos)
                                    != end64buf.length || GETSIG(end64buf) != ZIP64_ENDSIG) {
                                    return end;
                                }
                                // end64 found, re-calcualte everything.
                                end.cenlen = ZIP64_ENDSIZ(end64buf);
                                end.cenoff = ZIP64_ENDOFF(end64buf);
                                end.centot = (int)ZIP64_ENDTOT(end64buf); // assume total < 2g
                                end.endpos = end64pos;
                            } catch (IOException x) {}    // no zip64 loc/end
                        }
                        return end;
                    }
                }
            }
            zerror("zip END header not found");
            return null; //make compiler happy
        }

        // Reads zip file central directory.
        private void initCEN(int knownTotal) throws IOException {
            if (knownTotal == -1) {
                End end = findEND();
                if (end.endpos == 0) {
                    locpos = 0;
                    total = 0;
                    entries  = new int[0];
                    cen = null;
                    return;         // only END header present
                }
                if (end.cenlen > end.endpos)
                    zerror("invalid END header (bad central directory size)");
                long cenpos = end.endpos - end.cenlen;     // position of CEN table
                // Get position of first local file (LOC) header, taking into
                // account that there may be a stub prefixed to the zip file.
                locpos = cenpos - end.cenoff;
                if (locpos < 0) {
                    zerror("invalid END header (bad central directory offset)");
                }
                // read in the CEN and END
                cen = new byte[(int)(end.cenlen + ENDHDR)];
                if (readFullyAt(cen, 0, cen.length, cenpos) != end.cenlen + ENDHDR) {
                    zerror("read CEN tables failed");
                }
                total = end.centot;
            } else {
                total = knownTotal;
            }
            // hash table for entries
            entries  = new int[total * 3];
            tablelen = ((total/2) | 1); // Odd -> fewer collisions
            table    =  new int[tablelen];
            Arrays.fill(table, ZIP_ENDCHAIN);
            int idx = 0;
            int hash = 0;
            int next = -1;

            // list for all meta entries
            metanames = new ArrayList<>();

            // Iterate through the entries in the central directory
            int i = 0;
            int hsh = 0;
            int pos = 0;
            int limit = cen.length - ENDHDR;
            while (pos + CENHDR  <= limit) {
                if (i >= total) {
                    // This will only happen if the zip file has an incorrect
                    // ENDTOT field, which usually means it contains more than
                    // 65535 entries.
                    initCEN(countCENHeaders(cen, limit));
                    return;
                }
                if (CENSIG(cen, pos) != CENSIG)
                    zerror("invalid CEN header (bad signature)");
                int method = CENHOW(cen, pos);
                int nlen   = CENNAM(cen, pos);
                int elen   = CENEXT(cen, pos);
                int clen   = CENCOM(cen, pos);
                if ((CENFLG(cen, pos) & 1) != 0)
                    zerror("invalid CEN header (encrypted entry)");
                if (method != STORED && method != DEFLATED)
                    zerror("invalid CEN header (bad compression method: " + method + ")");
                if (pos + CENHDR + nlen > limit)
                    zerror("invalid CEN header (bad header size)");
                // Record the CEN offset and the name hash in our hash cell.
                hash = hashN(cen, pos + CENHDR, nlen);
                hsh = (hash & 0x7fffffff) % tablelen;
                next = table[hsh];
                table[hsh] = idx;
                idx = addEntry(idx, hash, next, pos);
                // Adds name to metanames.
                if (isMetaName(cen, pos + CENHDR, nlen)) {
                    metanames.add(pos);
                }
                // skip ext and comment
                pos += (CENHDR + nlen + elen + clen);
                i++;
            }
            total = i;
            if (pos + ENDHDR != cen.length) {
                zerror("invalid CEN header (bad header size)");
            }
        }

        private static void zerror(String msg) throws ZipException {
            throw new ZipException(msg);
        }

        /*
         * Returns the {@code pos} of the zip cen entry corresponding to the
         * specified entry name, or -1 if not found.
         */
        private int getEntryPos(byte[] name, boolean addSlash) {
            if (total == 0) {
                return -1;
            }
            int hsh = hashN(name, 0, name.length);
            int idx = table[(hsh & 0x7fffffff) % tablelen];
            /*
             * This while loop is an optimization where a double lookup
             * for name and name+/ is being performed. The name char
             * array has enough room at the end to try again with a
             * slash appended if the first table lookup does not succeed.
             */
            while(true) {
                /*
                 * Search down the target hash chain for a entry whose
                 * 32 bit hash matches the hashed name.
                 */
                while (idx != ZIP_ENDCHAIN) {
                    if (getEntryHash(idx) == hsh) {
                        // The CEN name must match the specfied one
                        int pos = getEntryPos(idx);
                        if (name.length == CENNAM(cen, pos)) {
                            boolean matched = true;
                            int nameoff = pos + CENHDR;
                            for (int i = 0; i < name.length; i++) {
                                if (name[i] != cen[nameoff++]) {
                                    matched = false;
                                    break;
                                }
                            }
                            if (matched) {
                                return pos;
                            }
                         }
                    }
                    idx = getEntryNext(idx);
                }
                /* If not addSlash, or slash is already there, we are done */
                if (!addSlash  || name[name.length - 1] == '/') {
                     return -1;
                }
                /* Add slash and try once more */
                name = Arrays.copyOf(name, name.length + 1);
                name[name.length - 1] = '/';
                hsh = hash_append(hsh, (byte)'/');
                //idx = table[hsh % tablelen];
                idx = table[(hsh & 0x7fffffff) % tablelen];
                addSlash = false;
            }
        }

        private static byte[] metainf = new byte[] {
            'M', 'E', 'T', 'A', '-', 'I' , 'N', 'F', '/',
        };

        /*
         * Returns true if the specified entry's name begins with the string
         * "META-INF/" irrespective of case.
         */
        private static boolean isMetaName(byte[] name,  int off, int len) {
            if (len < 9 || (name[off] != 'M' && name[off] != 'm')) {  //  sizeof("META-INF/") - 1
                return false;
            }
            off++;
            for (int i = 1; i < metainf.length; i++) {
                byte c = name[off++];
                // Avoid toupper; it's locale-dependent
                if (c >= 'a' && c <= 'z') {
                    c += 'A' - 'a';
                }
                if (metainf[i] != c) {
                    return false;
                }
            }
            return true;
        }

        /*
         * Counts the number of CEN headers in a central directory extending
         * from BEG to END.  Might return a bogus answer if the zip file is
         * corrupt, but will not crash.
         */
        static int countCENHeaders(byte[] cen, int end) {
            int count = 0;
            int pos = 0;
            while (pos + CENHDR <= end) {
                count++;
                pos += (CENHDR + CENNAM(cen, pos) + CENEXT(cen, pos) + CENCOM(cen, pos));
            }
            return count;
        }
    }
}
