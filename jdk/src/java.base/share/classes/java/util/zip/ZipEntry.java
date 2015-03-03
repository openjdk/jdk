/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.zip.ZipUtils.*;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.zip.ZipConstants64.*;

/**
 * This class is used to represent a ZIP file entry.
 *
 * @author      David Connelly
 */
public
class ZipEntry implements ZipConstants, Cloneable {

    String name;        // entry name
    long time = -1;     // last modification time
    FileTime mtime;     // last modification time, from extra field data
    FileTime atime;     // last access time, from extra field data
    FileTime ctime;     // creation time, from extra field data
    long crc = -1;      // crc-32 of entry data
    long size = -1;     // uncompressed size of entry data
    long csize = -1;    // compressed size of entry data
    int method = -1;    // compression method
    int flag = 0;       // general purpose flag
    byte[] extra;       // optional extra field data for entry
    String comment;     // optional comment string for entry

    /**
     * Compression method for uncompressed entries.
     */
    public static final int STORED = 0;

    /**
     * Compression method for compressed (deflated) entries.
     */
    public static final int DEFLATED = 8;

    /**
     * Creates a new zip entry with the specified name.
     *
     * @param  name
     *         The entry name
     *
     * @throws NullPointerException if the entry name is null
     * @throws IllegalArgumentException if the entry name is longer than
     *         0xFFFF bytes
     */
    public ZipEntry(String name) {
        Objects.requireNonNull(name, "name");
        if (name.length() > 0xFFFF) {
            throw new IllegalArgumentException("entry name too long");
        }
        this.name = name;
    }

    /**
     * Creates a new zip entry with fields taken from the specified
     * zip entry.
     *
     * @param  e
     *         A zip Entry object
     *
     * @throws NullPointerException if the entry object is null
     */
    public ZipEntry(ZipEntry e) {
        Objects.requireNonNull(e, "entry");
        name = e.name;
        time = e.time;
        mtime = e.mtime;
        atime = e.atime;
        ctime = e.ctime;
        crc = e.crc;
        size = e.size;
        csize = e.csize;
        method = e.method;
        flag = e.flag;
        extra = e.extra;
        comment = e.comment;
    }

    /**
     * Creates a new un-initialized zip entry
     */
    ZipEntry() {}

    /**
     * Returns the name of the entry.
     * @return the name of the entry
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the last modification time of the entry.
     *
     * <p> If the entry is output to a ZIP file or ZIP file formatted
     * output stream the last modification time set by this method will
     * be stored into the {@code date and time fields} of the zip file
     * entry and encoded in standard {@code MS-DOS date and time format}.
     * The {@link java.util.TimeZone#getDefault() default TimeZone} is
     * used to convert the epoch time to the MS-DOS data and time.
     *
     * @param  time
     *         The last modification time of the entry in milliseconds
     *         since the epoch
     *
     * @see #getTime()
     * @see #getLastModifiedTime()
     */
    public void setTime(long time) {
        this.time = time;
        this.mtime = null;
    }

    /**
     * Returns the last modification time of the entry.
     *
     * <p> If the entry is read from a ZIP file or ZIP file formatted
     * input stream, this is the last modification time from the {@code
     * date and time fields} of the zip file entry. The
     * {@link java.util.TimeZone#getDefault() default TimeZone} is used
     * to convert the standard MS-DOS formatted date and time to the
     * epoch time.
     *
     * @return  The last modification time of the entry in milliseconds
     *          since the epoch, or -1 if not specified
     *
     * @see #setTime(long)
     * @see #setLastModifiedTime(FileTime)
     */
    public long getTime() {
        return time;
    }

    /**
     * Sets the last modification time of the entry.
     *
     * <p> When output to a ZIP file or ZIP file formatted output stream
     * the last modification time set by this method will be stored into
     * zip file entry's {@code date and time fields} in {@code standard
     * MS-DOS date and time format}), and the extended timestamp fields
     * in {@code optional extra data} in UTC time.
     *
     * @param  time
     *         The last modification time of the entry
     * @return This zip entry
     *
     * @throws NullPointerException if the {@code time} is null
     *
     * @see #getLastModifiedTime()
     * @since 1.8
     */
    public ZipEntry setLastModifiedTime(FileTime time) {
        this.mtime = Objects.requireNonNull(time, "lastModifiedTime");
        this.time = time.to(TimeUnit.MILLISECONDS);
        return this;
    }

    /**
     * Returns the last modification time of the entry.
     *
     * <p> If the entry is read from a ZIP file or ZIP file formatted
     * input stream, this is the last modification time from the zip
     * file entry's {@code optional extra data} if the extended timestamp
     * fields are present. Otherwise the last modification time is read
     * from the entry's {@code date and time fields}, the {@link
     * java.util.TimeZone#getDefault() default TimeZone} is used to convert
     * the standard MS-DOS formatted date and time to the epoch time.
     *
     * @return The last modification time of the entry, null if not specified
     *
     * @see #setLastModifiedTime(FileTime)
     * @since 1.8
     */
    public FileTime getLastModifiedTime() {
        if (mtime != null)
            return mtime;
        if (time == -1)
            return null;
        return FileTime.from(time, TimeUnit.MILLISECONDS);
    }

    /**
     * Sets the last access time of the entry.
     *
     * <p> If set, the last access time will be stored into the extended
     * timestamp fields of entry's {@code optional extra data}, when output
     * to a ZIP file or ZIP file formatted stream.
     *
     * @param  time
     *         The last access time of the entry
     * @return This zip entry
     *
     * @throws NullPointerException if the {@code time} is null
     *
     * @see #getLastAccessTime()
     * @since 1.8
     */
    public ZipEntry setLastAccessTime(FileTime time) {
        this.atime = Objects.requireNonNull(time, "lastAccessTime");
        return this;
    }

    /**
     * Returns the last access time of the entry.
     *
     * <p> The last access time is from the extended timestamp fields
     * of entry's {@code optional extra data} when read from a ZIP file
     * or ZIP file formatted stream.
     *
     * @return The last access time of the entry, null if not specified

     * @see #setLastAccessTime(FileTime)
     * @since 1.8
     */
    public FileTime getLastAccessTime() {
        return atime;
    }

    /**
     * Sets the creation time of the entry.
     *
     * <p> If set, the creation time will be stored into the extended
     * timestamp fields of entry's {@code optional extra data}, when
     * output to a ZIP file or ZIP file formatted stream.
     *
     * @param  time
     *         The creation time of the entry
     * @return This zip entry
     *
     * @throws NullPointerException if the {@code time} is null
     *
     * @see #getCreationTime()
     * @since 1.8
     */
    public ZipEntry setCreationTime(FileTime time) {
        this.ctime = Objects.requireNonNull(time, "creationTime");
        return this;
    }

    /**
     * Returns the creation time of the entry.
     *
     * <p> The creation time is from the extended timestamp fields of
     * entry's {@code optional extra data} when read from a ZIP file
     * or ZIP file formatted stream.
     *
     * @return the creation time of the entry, null if not specified
     * @see #setCreationTime(FileTime)
     * @since 1.8
     */
    public FileTime getCreationTime() {
        return ctime;
    }

    /**
     * Sets the uncompressed size of the entry data.
     *
     * @param size the uncompressed size in bytes
     *
     * @throws IllegalArgumentException if the specified size is less
     *         than 0, is greater than 0xFFFFFFFF when
     *         <a href="package-summary.html#zip64">ZIP64 format</a> is not supported,
     *         or is less than 0 when ZIP64 is supported
     * @see #getSize()
     */
    public void setSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("invalid entry size");
        }
        this.size = size;
    }

    /**
     * Returns the uncompressed size of the entry data.
     *
     * @return the uncompressed size of the entry data, or -1 if not known
     * @see #setSize(long)
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the size of the compressed entry data.
     *
     * <p> In the case of a stored entry, the compressed size will be the same
     * as the uncompressed size of the entry.
     *
     * @return the size of the compressed entry data, or -1 if not known
     * @see #setCompressedSize(long)
     */
    public long getCompressedSize() {
        return csize;
    }

    /**
     * Sets the size of the compressed entry data.
     *
     * @param csize the compressed size to set to
     *
     * @see #getCompressedSize()
     */
    public void setCompressedSize(long csize) {
        this.csize = csize;
    }

    /**
     * Sets the CRC-32 checksum of the uncompressed entry data.
     *
     * @param crc the CRC-32 value
     *
     * @throws IllegalArgumentException if the specified CRC-32 value is
     *         less than 0 or greater than 0xFFFFFFFF
     * @see #getCrc()
     */
    public void setCrc(long crc) {
        if (crc < 0 || crc > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("invalid entry crc-32");
        }
        this.crc = crc;
    }

    /**
     * Returns the CRC-32 checksum of the uncompressed entry data.
     *
     * @return the CRC-32 checksum of the uncompressed entry data, or -1 if
     * not known
     *
     * @see #setCrc(long)
     */
    public long getCrc() {
        return crc;
    }

    /**
     * Sets the compression method for the entry.
     *
     * @param method the compression method, either STORED or DEFLATED
     *
     * @throws  IllegalArgumentException if the specified compression
     *          method is invalid
     * @see #getMethod()
     */
    public void setMethod(int method) {
        if (method != STORED && method != DEFLATED) {
            throw new IllegalArgumentException("invalid compression method");
        }
        this.method = method;
    }

    /**
     * Returns the compression method of the entry.
     *
     * @return the compression method of the entry, or -1 if not specified
     * @see #setMethod(int)
     */
    public int getMethod() {
        return method;
    }

    /**
     * Sets the optional extra field data for the entry.
     *
     * <p> Invoking this method may change this entry's last modification
     * time, last access time and creation time, if the {@code extra} field
     * data includes the extensible timestamp fields, such as {@code NTFS tag
     * 0x0001} or {@code Info-ZIP Extended Timestamp}, as specified in
     * <a href="http://www.info-zip.org/doc/appnote-19970311-iz.zip">Info-ZIP
     * Application Note 970311</a>.
     *
     * @param  extra
     *         The extra field data bytes
     *
     * @throws IllegalArgumentException if the length of the specified
     *         extra field data is greater than 0xFFFF bytes
     *
     * @see #getExtra()
     */
    public void setExtra(byte[] extra) {
        setExtra0(extra, false);
    }

    /**
     * Sets the optional extra field data for the entry.
     *
     * @param extra
     *        the extra field data bytes
     * @param doZIP64
     *        if true, set size and csize from ZIP64 fields if present
     */
    void setExtra0(byte[] extra, boolean doZIP64) {
        if (extra != null) {
            if (extra.length > 0xFFFF) {
                throw new IllegalArgumentException("invalid extra field length");
            }
            // extra fields are in "HeaderID(2)DataSize(2)Data... format
            int off = 0;
            int len = extra.length;
            while (off + 4 < len) {
                int tag = get16(extra, off);
                int sz = get16(extra, off + 2);
                off += 4;
                if (off + sz > len)         // invalid data
                    break;
                switch (tag) {
                case EXTID_ZIP64:
                    if (doZIP64) {
                        // LOC extra zip64 entry MUST include BOTH original
                        // and compressed file size fields.
                        // If invalid zip64 extra fields, simply skip. Even
                        // it's rare, it's possible the entry size happens to
                        // be the magic value and it "accidently" has some
                        // bytes in extra match the id.
                        if (sz >= 16) {
                            size = get64(extra, off);
                            csize = get64(extra, off + 8);
                        }
                    }
                    break;
                case EXTID_NTFS:
                    int pos = off + 4;               // reserved 4 bytes
                    if (get16(extra, pos) !=  0x0001 || get16(extra, pos + 2) != 24)
                        break;
                    mtime = winTimeToFileTime(get64(extra, pos + 4));
                    atime = winTimeToFileTime(get64(extra, pos + 12));
                    ctime = winTimeToFileTime(get64(extra, pos + 20));
                    break;
                case EXTID_EXTT:
                    int flag = Byte.toUnsignedInt(extra[off]);
                    int sz0 = 1;
                    // The CEN-header extra field contains the modification
                    // time only, or no timestamp at all. 'sz' is used to
                    // flag its presence or absence. But if mtime is present
                    // in LOC it must be present in CEN as well.
                    if ((flag & 0x1) != 0 && (sz0 + 4) <= sz) {
                        mtime = unixTimeToFileTime(get32(extra, off + sz0));
                        sz0 += 4;
                    }
                    if ((flag & 0x2) != 0 && (sz0 + 4) <= sz) {
                        atime = unixTimeToFileTime(get32(extra, off + sz0));
                        sz0 += 4;
                    }
                    if ((flag & 0x4) != 0 && (sz0 + 4) <= sz) {
                        ctime = unixTimeToFileTime(get32(extra, off + sz0));
                        sz0 += 4;
                    }
                    break;
                 default:
                }
                off += sz;
            }
        }
        this.extra = extra;
    }

    /**
     * Returns the extra field data for the entry.
     *
     * @return the extra field data for the entry, or null if none
     *
     * @see #setExtra(byte[])
     */
    public byte[] getExtra() {
        return extra;
    }

    /**
     * Sets the optional comment string for the entry.
     *
     * <p>ZIP entry comments have maximum length of 0xffff. If the length of the
     * specified comment string is greater than 0xFFFF bytes after encoding, only
     * the first 0xFFFF bytes are output to the ZIP file entry.
     *
     * @param comment the comment string
     *
     * @see #getComment()
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Returns the comment string for the entry.
     *
     * @return the comment string for the entry, or null if none
     *
     * @see #setComment(String)
     */
    public String getComment() {
        return comment;
    }

    /**
     * Returns true if this is a directory entry. A directory entry is
     * defined to be one whose name ends with a '/'.
     * @return true if this is a directory entry
     */
    public boolean isDirectory() {
        return name.endsWith("/");
    }

    /**
     * Returns a string representation of the ZIP entry.
     */
    public String toString() {
        return getName();
    }

    /**
     * Returns the hash code value for this entry.
     */
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Returns a copy of this entry.
     */
    public Object clone() {
        try {
            ZipEntry e = (ZipEntry)super.clone();
            e.extra = (extra == null) ? null : extra.clone();
            return e;
        } catch (CloneNotSupportedException e) {
            // This should never happen, since we are Cloneable
            throw new InternalError(e);
        }
    }
}
