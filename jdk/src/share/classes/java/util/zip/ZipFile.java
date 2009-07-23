/*
 * Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.util.zip;

import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Set;
import java.util.HashSet;
import java.util.NoSuchElementException;
import static java.util.zip.ZipConstants64.*;

/**
 * This class is used to read entries from a zip file.
 *
 * <p> Unless otherwise noted, passing a <tt>null</tt> argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 *
 * @author      David Connelly
 */
public
class ZipFile implements ZipConstants, Closeable {
    private long jzfile;  // address of jzfile data
    private String name;  // zip file name
    private int total;    // total number of entries
    private boolean closeRequested;

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
     * <tt>ZipFile</tt> object until either the close method is invoked or the
     * virtual machine exits.
     */
    public static final int OPEN_DELETE = 0x4;

    static {
        /* Zip library is loaded from System.initializeSystemClass */
        initIDs();
    }

    private static native void initIDs();

    /**
     * Opens a zip file for reading.
     *
     * <p>First, if there is a security manager, its <code>checkRead</code>
     * method is called with the <code>name</code> argument as its argument
     * to ensure the read is allowed.
     *
     * <p>The UTF-8 {@link java.nio.charset.Charset charset} is used to
     * decode the entry names and comments.
     *
     * @param name the name of the zip file
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     * @throws SecurityException if a security manager exists and its
     *         <code>checkRead</code> method doesn't allow read access to the file.
     *
     * @see SecurityManager#checkRead(java.lang.String)
     */
    public ZipFile(String name) throws IOException {
        this(new File(name), OPEN_READ);
    }

    /**
     * Opens a new <code>ZipFile</code> to read from the specified
     * <code>File</code> object in the specified mode.  The mode argument
     * must be either <tt>OPEN_READ</tt> or <tt>OPEN_READ | OPEN_DELETE</tt>.
     *
     * <p>First, if there is a security manager, its <code>checkRead</code>
     * method is called with the <code>name</code> argument as its argument to
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
     *         its <code>checkRead</code> method
     *         doesn't allow read access to the file,
     *         or its <code>checkDelete</code> method doesn't allow deleting
     *         the file when the <tt>OPEN_DELETE</tt> flag is set.
     * @throws IllegalArgumentException if the <tt>mode</tt> argument is invalid
     * @see SecurityManager#checkRead(java.lang.String)
     * @since 1.3
     */
    public ZipFile(File file, int mode) throws IOException {
        this(file, mode, Charset.forName("UTF-8"));
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

    private ZipCoder zc;

    /**
     * Opens a new <code>ZipFile</code> to read from the specified
     * <code>File</code> object in the specified mode.  The mode argument
     * must be either <tt>OPEN_READ</tt> or <tt>OPEN_READ | OPEN_DELETE</tt>.
     *
     * <p>First, if there is a security manager, its <code>checkRead</code>
     * method is called with the <code>name</code> argument as its argument to
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
     *         if a security manager exists and its <code>checkRead</code>
     *         method doesn't allow read access to the file,or its
     *         <code>checkDelete</code> method doesn't allow deleting the
     *         file when the <tt>OPEN_DELETE</tt> flag is set
     *
     * @throws IllegalArgumentException if the <tt>mode</tt> argument is invalid
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
        if (charset == null)
            throw new NullPointerException("charset is null");
        this.zc = ZipCoder.get(charset);
        jzfile = open(name, mode, file.lastModified());
        this.name = name;
        this.total = getTotal(jzfile);
    }

    /**
     * Opens a zip file for reading.
     *
     * <p>First, if there is a security manager, its <code>checkRead</code>
     * method is called with the <code>name</code> argument as its argument
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
     *         if a security manager exists and its <code>checkRead</code>
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
            byte[] bcomm = getCommentBytes(jzfile);
            if (bcomm == null)
                return null;
            return zc.toString(bcomm, bcomm.length);
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
        if (name == null) {
            throw new NullPointerException("name");
        }
        long jzentry = 0;
        synchronized (this) {
            ensureOpen();
            jzentry = getEntry(jzfile, zc.getBytes(name), true);
            if (jzentry != 0) {
                ZipEntry ze = getZipEntry(name, jzentry);
                freeEntry(jzfile, jzentry);
                return ze;
            }
        }
        return null;
    }

    private static native long getEntry(long jzfile, byte[] name,
                                        boolean addSlash);

    // freeEntry releases the C jzentry struct.
    private static native void freeEntry(long jzfile, long jzentry);

    // the outstanding inputstreams that need to be closed.
    private Set<ZipFileInputStream> streams = new HashSet<ZipFileInputStream>();

    /**
     * Returns an input stream for reading the contents of the specified
     * zip file entry.
     *
     * <p> Closing this ZIP file will, in turn, close all input
     * streams that have been returned by invocations of this method.
     *
     * @param entry the zip file entry
     * @return the input stream for reading the contents of the specified
     * zip file entry.
     * @throws ZipException if a ZIP format error has occurred
     * @throws IOException if an I/O error has occurred
     * @throws IllegalStateException if the zip file has been closed
     */
    public InputStream getInputStream(ZipEntry entry) throws IOException {
        if (entry == null) {
            throw new NullPointerException("entry");
        }
        long jzentry = 0;
        ZipFileInputStream in = null;
        synchronized (this) {
            ensureOpen();
            if (!zc.isUTF8() && (entry.flag & EFS) != 0) {
                jzentry = getEntry(jzfile, zc.getBytesUTF8(entry.name), false);
            } else {
                jzentry = getEntry(jzfile, zc.getBytes(entry.name), false);
            }
            if (jzentry == 0) {
                return null;
            }
            in = new ZipFileInputStream(jzentry);
            streams.add(in);
        }
        final ZipFileInputStream zfin = in;
        switch (getEntryMethod(jzentry)) {
        case STORED:
            return zfin;
        case DEFLATED:
            // MORE: Compute good size for inflater stream:
            long size = getEntrySize(jzentry) + 2; // Inflater likes a bit of slack
            if (size > 65536) size = 8192;
            if (size <= 0) size = 4096;
            return new InflaterInputStream(zfin, getInflater(), (int)size) {
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
                // using the "nowrap" Inflater option.
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
                    long avail = zfin.size() - inf.getBytesWritten();
                    return avail > (long) Integer.MAX_VALUE ?
                        Integer.MAX_VALUE : (int) avail;
                }
            };
        default:
            throw new ZipException("invalid compression method");
        }
    }

    /*
     * Gets an inflater from the list of available inflaters or allocates
     * a new one.
     */
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

    /*
     * Releases the specified inflater to the list of available inflaters.
     */
    private void releaseInflater(Inflater inf) {
        synchronized (inflaters) {
            inf.reset();
            inflaters.add(inf);
        }
    }

    // List of available Inflater objects for decompression
    private Vector inflaters = new Vector();

    /**
     * Returns the path name of the ZIP file.
     * @return the path name of the ZIP file
     */
    public String getName() {
        return name;
    }

    /**
     * Returns an enumeration of the ZIP file entries.
     * @return an enumeration of the ZIP file entries
     * @throws IllegalStateException if the zip file has been closed
     */
    public Enumeration<? extends ZipEntry> entries() {
        ensureOpen();
        return new Enumeration<ZipEntry>() {
                private int i = 0;
                public boolean hasMoreElements() {
                    synchronized (ZipFile.this) {
                        ensureOpen();
                        return i < total;
                    }
                }
                public ZipEntry nextElement() throws NoSuchElementException {
                    synchronized (ZipFile.this) {
                        ensureOpen();
                        if (i >= total) {
                            throw new NoSuchElementException();
                        }
                        long jzentry = getNextEntry(jzfile, i++);
                        if (jzentry == 0) {
                            String message;
                            if (closeRequested) {
                                message = "ZipFile concurrently closed";
                            } else {
                                message = getZipMessage(ZipFile.this.jzfile);
                            }
                            throw new ZipError("jzentry == 0" +
                                               ",\n jzfile = " + ZipFile.this.jzfile +
                                               ",\n total = " + ZipFile.this.total +
                                               ",\n name = " + ZipFile.this.name +
                                               ",\n i = " + i +
                                               ",\n message = " + message
                                );
                        }
                        ZipEntry ze = getZipEntry(null, jzentry);
                        freeEntry(jzfile, jzentry);
                        return ze;
                    }
                }
            };
    }

    private ZipEntry getZipEntry(String name, long jzentry) {
        ZipEntry e = new ZipEntry();
        e.flag = getEntryFlag(jzentry);  // get the flag first
        if (name != null) {
            e.name = name;
        } else {
            byte[] bname = getEntryBytes(jzentry, JZENTRY_NAME);
            if (!zc.isUTF8() && (e.flag & EFS) != 0) {
                e.name = zc.toStringUTF8(bname, bname.length);
            } else {
                e.name = zc.toString(bname, bname.length);
            }
        }
        e.time = getEntryTime(jzentry);
        e.crc = getEntryCrc(jzentry);
        e.size = getEntrySize(jzentry);
        e. csize = getEntryCSize(jzentry);
        e.method = getEntryMethod(jzentry);
        e.extra = getEntryBytes(jzentry, JZENTRY_EXTRA);
        byte[] bcomm = getEntryBytes(jzentry, JZENTRY_COMMENT);
        if (bcomm == null) {
            e.comment = null;
        } else {
            if (!zc.isUTF8() && (e.flag & EFS) != 0) {
                e.comment = zc.toStringUTF8(bcomm, bcomm.length);
            } else {
                e.comment = zc.toString(bcomm, bcomm.length);
            }
        }
        return e;
    }

    private static native long getNextEntry(long jzfile, int i);

    /**
     * Returns the number of entries in the ZIP file.
     * @return the number of entries in the ZIP file
     * @throws IllegalStateException if the zip file has been closed
     */
    public int size() {
        ensureOpen();
        return total;
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
        synchronized (this) {
            closeRequested = true;

            if (streams.size() !=0) {
                Set<ZipFileInputStream> copy = streams;
                streams = new HashSet<ZipFileInputStream>();
                for (ZipFileInputStream is: copy)
                    is.close();
            }

            if (jzfile != 0) {
                // Close the zip file
                long zf = this.jzfile;
                jzfile = 0;

                close(zf);

                // Release inflaters
                synchronized (inflaters) {
                    int size = inflaters.size();
                    for (int i = 0; i < size; i++) {
                        Inflater inf = (Inflater)inflaters.get(i);
                        inf.end();
                    }
                }
            }
        }
    }


    /**
     * Ensures that the <code>close</code> method of this ZIP file is
     * called when there are no more references to it.
     *
     * <p>
     * Since the time when GC would invoke this method is undetermined,
     * it is strongly recommended that applications invoke the <code>close</code>
     * method as soon they have finished accessing this <code>ZipFile</code>.
     * This will prevent holding up system resources for an undetermined
     * length of time.
     *
     * @throws IOException if an I/O error has occurred
     * @see    java.util.zip.ZipFile#close()
     */
    protected void finalize() throws IOException {
        close();
    }

    private static native void close(long jzfile);

    private void ensureOpen() {
        if (closeRequested) {
            throw new IllegalStateException("zip file closed");
        }

        if (jzfile == 0) {
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
        protected long jzentry; // address of jzentry data
        private   long pos;     // current position within entry data
        protected long rem;     // number of remaining bytes within entry
        protected long size;    // uncompressed size of this entry

        ZipFileInputStream(long jzentry) {
            pos = 0;
            rem = getEntryCSize(jzentry);
            size = getEntrySize(jzentry);
            this.jzentry = jzentry;
        }

        public int read(byte b[], int off, int len) throws IOException {
            if (rem == 0) {
                return -1;
            }
            if (len <= 0) {
                return 0;
            }
            if (len > rem) {
                len = (int) rem;
            }
            synchronized (ZipFile.this) {
                ensureOpenOrZipException();

                len = ZipFile.read(ZipFile.this.jzfile, jzentry, pos, b,
                                   off, len);
            }
            if (len > 0) {
                pos += len;
                rem -= len;
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

        public long skip(long n) {
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
            synchronized (ZipFile.this) {
                if (jzentry != 0 && ZipFile.this.jzfile != 0) {
                    freeEntry(ZipFile.this.jzfile, jzentry);
                    jzentry = 0;
                }
                streams.remove(this);
            }
        }
    }


    private static native long open(String name, int mode, long lastModified)
        throws IOException;
    private static native int getTotal(long jzfile);
    private static native int read(long jzfile, long jzentry,
                                   long pos, byte[] b, int off, int len);

    // access to the native zentry object
    private static native long getEntryTime(long jzentry);
    private static native long getEntryCrc(long jzentry);
    private static native long getEntryCSize(long jzentry);
    private static native long getEntrySize(long jzentry);
    private static native int getEntryMethod(long jzentry);
    private static native int getEntryFlag(long jzentry);
    private static native byte[] getCommentBytes(long jzfile);

    private static final int JZENTRY_NAME = 0;
    private static final int JZENTRY_EXTRA = 1;
    private static final int JZENTRY_COMMENT = 2;
    private static native byte[] getEntryBytes(long jzentry, int type);

    private static native String getZipMessage(long jzfile);
}
