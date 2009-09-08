/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

import java.nio.file.attribute.*;
import java.util.concurrent.TimeUnit;
import java.security.AccessController;
import sun.misc.Unsafe;
import sun.security.action.GetPropertyAction;

import static sun.nio.fs.WindowsNativeDispatcher.*;
import static sun.nio.fs.WindowsConstants.*;

/**
 * Windows implementation of DosFileAttributes/BasicFileAttributes
 */

class WindowsFileAttributes
    implements DosFileAttributes
{
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /*
     * typedef struct _BY_HANDLE_FILE_INFORMATION {
     *     DWORD    dwFileAttributes;
     *     FILETIME ftCreationTime;
     *     FILETIME ftLastAccessTime;
     *     FILETIME ftLastWriteTime;
     *     DWORD    dwVolumeSerialNumber;
     *     DWORD    nFileSizeHigh;
     *     DWORD    nFileSizeLow;
     *     DWORD    nNumberOfLinks;
     *     DWORD    nFileIndexHigh;
     *     DWORD    nFileIndexLow;
     * } BY_HANDLE_FILE_INFORMATION;
     */
    private static final short SIZEOF_FILE_INFORMATION  = 52;
    private static final short OFFSETOF_FILE_INFORMATION_ATTRIBUTES      = 0;
    private static final short OFFSETOF_FILE_INFORMATION_CREATETIME      = 4;
    private static final short OFFSETOF_FILE_INFORMATION_LASTACCESSTIME  = 12;
    private static final short OFFSETOF_FILE_INFORMATION_LASTWRITETIME   = 20;
    private static final short OFFSETOF_FILE_INFORMATION_VOLSERIALNUM    = 28;
    private static final short OFFSETOF_FILE_INFORMATION_SIZEHIGH        = 32;
    private static final short OFFSETOF_FILE_INFORMATION_SIZELOW         = 36;
    private static final short OFFSETOF_FILE_INFORMATION_INDEXHIGH       = 44;
    private static final short OFFSETOF_FILE_INFORMATION_INDEXLOW        = 48;

    /*
     * typedef struct _WIN32_FILE_ATTRIBUTE_DATA {
     *   DWORD dwFileAttributes;
     *   FILETIME ftCreationTime;
     *   FILETIME ftLastAccessTime;
     *   FILETIME ftLastWriteTime;
     *   DWORD nFileSizeHigh;
     *   DWORD nFileSizeLow;
     * } WIN32_FILE_ATTRIBUTE_DATA;
     */
    private static final short SIZEOF_FILE_ATTRIBUTE_DATA = 36;
    private static final short OFFSETOF_FILE_ATTRIBUTE_DATA_ATTRIBUTES      = 0;
    private static final short OFFSETOF_FILE_ATTRIBUTE_DATA_CREATETIME      = 4;
    private static final short OFFSETOF_FILE_ATTRIBUTE_DATA_LASTACCESSTIME  = 12;
    private static final short OFFSETOF_FILE_ATTRIBUTE_DATA_LASTWRITETIME   = 20;
    private static final short OFFSETOF_FILE_ATTRIBUTE_DATA_SIZEHIGH        = 28;
    private static final short OFFSETOF_FILE_ATTRIBUTE_DATA_SIZELOW         = 32;

    /**
     * typedef struct _WIN32_FIND_DATA {
     *   DWORD dwFileAttributes;
     *   FILETIME ftCreationTime;
     *   FILETIME ftLastAccessTime;
     *   FILETIME ftLastWriteTime;
     *   DWORD nFileSizeHigh;
     *   DWORD nFileSizeLow;
     *   DWORD dwReserved0;
     *   DWORD dwReserved1;
     *   TCHAR cFileName[MAX_PATH];
     *   TCHAR cAlternateFileName[14];
     * } WIN32_FIND_DATA;
     */
    private static final short SIZEOF_FIND_DATA = 592;
    private static final short OFFSETOF_FIND_DATA_ATTRIBUTES = 0;
    private static final short OFFSETOF_FIND_DATA_CREATETIME = 4;
    private static final short OFFSETOF_FIND_DATA_LASTACCESSTIME = 12;
    private static final short OFFSETOF_FIND_DATA_LASTWRITETIME = 20;
    private static final short OFFSETOF_FIND_DATA_SIZEHIGH = 28;
    private static final short OFFSETOF_FIND_DATA_SIZELOW = 32;
    private static final short OFFSETOF_FIND_DATA_RESERVED0 = 36;

    // used to adjust values between Windows and java epoch
    private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;

    // indicates if accurate metadata is required (interesting on NTFS only)
    private static final boolean ensureAccurateMetadata;
    static {
        String propValue = AccessController.doPrivileged(
            new GetPropertyAction("sun.nio.fs.ensureAccurateMetadata", "false"));
        ensureAccurateMetadata = (propValue.length() == 0) ?
            true : Boolean.valueOf(propValue);
    }

    // attributes
    private final int fileAttrs;
    private final long creationTime;
    private final long lastAccessTime;
    private final long lastWriteTime;
    private final long size;
    private final int reparseTag;

    // additional attributes when using GetFileInformationByHandle
    private final int volSerialNumber;
    private final int fileIndexHigh;
    private final int fileIndexLow;

    /**
     * Convert 64-bit value representing the number of 100-nanosecond intervals
     * since January 1, 1601 to a FileTime.
     */
    static FileTime toFileTime(long time) {
        // 100ns -> us
        time /= 10L;
        // adjust to java epoch
        time += WINDOWS_EPOCH_IN_MICROSECONDS;
        return FileTime.from(time, TimeUnit.MICROSECONDS);
    }

    /**
     * Convert FileTime to 64-bit value representing the number of 100-nanosecond
     * intervals since January 1, 1601.
     */
    static long toWindowsTime(FileTime time) {
        long value = time.to(TimeUnit.MICROSECONDS);
        // adjust to Windows epoch+= 11644473600000000L;
        value -= WINDOWS_EPOCH_IN_MICROSECONDS;
        // us -> 100ns
        value *= 10L;
        return value;
    }

    /**
     * Initialize a new instance of this class
     */
    private WindowsFileAttributes(int fileAttrs,
                                  long creationTime,
                                  long lastAccessTime,
                                  long lastWriteTime,
                                  long size,
                                  int reparseTag,
                                  int volSerialNumber,
                                  int fileIndexHigh,
                                  int fileIndexLow)
    {
        this.fileAttrs = fileAttrs;
        this.creationTime = creationTime;
        this.lastAccessTime = lastAccessTime;
        this.lastWriteTime = lastWriteTime;
        this.size = size;
        this.reparseTag = reparseTag;
        this.volSerialNumber = volSerialNumber;
        this.fileIndexHigh = fileIndexHigh;
        this.fileIndexLow = fileIndexLow;
    }

    /**
     * Create a WindowsFileAttributes from a BY_HANDLE_FILE_INFORMATION structure
     */
    private static WindowsFileAttributes fromFileInformation(long address, int reparseTag) {
        int fileAttrs = unsafe.getInt(address + OFFSETOF_FILE_INFORMATION_ATTRIBUTES);
        long creationTime = unsafe.getLong(address + OFFSETOF_FILE_INFORMATION_CREATETIME);
        long lastAccessTime = unsafe.getLong(address + OFFSETOF_FILE_INFORMATION_LASTACCESSTIME);
        long lastWriteTime = unsafe.getLong(address + OFFSETOF_FILE_INFORMATION_LASTWRITETIME);
        long size = ((long)(unsafe.getInt(address + OFFSETOF_FILE_INFORMATION_SIZEHIGH)) << 32)
            + (unsafe.getInt(address + OFFSETOF_FILE_INFORMATION_SIZELOW) & 0xFFFFFFFFL);
        int volSerialNumber = unsafe.getInt(address + OFFSETOF_FILE_INFORMATION_VOLSERIALNUM);
        int fileIndexHigh = unsafe.getInt(address + OFFSETOF_FILE_INFORMATION_INDEXHIGH);
        int fileIndexLow = unsafe.getInt(address + OFFSETOF_FILE_INFORMATION_INDEXLOW);
        return new WindowsFileAttributes(fileAttrs,
                                         creationTime,
                                         lastAccessTime,
                                         lastWriteTime,
                                         size,
                                         reparseTag,
                                         volSerialNumber,
                                         fileIndexHigh,
                                         fileIndexLow);
    }

    /**
     * Create a WindowsFileAttributes from a WIN32_FILE_ATTRIBUTE_DATA structure
     */
    private static WindowsFileAttributes fromFileAttributeData(long address, int reparseTag) {
        int fileAttrs = unsafe.getInt(address + OFFSETOF_FILE_ATTRIBUTE_DATA_ATTRIBUTES);
        long creationTime = unsafe.getLong(address + OFFSETOF_FILE_ATTRIBUTE_DATA_CREATETIME);
        long lastAccessTime = unsafe.getLong(address + OFFSETOF_FILE_ATTRIBUTE_DATA_LASTACCESSTIME);
        long lastWriteTime = unsafe.getLong(address + OFFSETOF_FILE_ATTRIBUTE_DATA_LASTWRITETIME);
        long size = ((long)(unsafe.getInt(address + OFFSETOF_FILE_ATTRIBUTE_DATA_SIZEHIGH)) << 32)
            + (unsafe.getInt(address + OFFSETOF_FILE_ATTRIBUTE_DATA_SIZELOW) & 0xFFFFFFFFL);
        return new WindowsFileAttributes(fileAttrs,
                                         creationTime,
                                         lastAccessTime,
                                         lastWriteTime,
                                         size,
                                         reparseTag,
                                         0,  // volSerialNumber
                                         0,  // fileIndexHigh
                                         0); // fileIndexLow
    }


    /**
     * Allocates a native buffer for a WIN32_FIND_DATA structure
     */
    static NativeBuffer getBufferForFindData() {
        return NativeBuffers.getNativeBuffer(SIZEOF_FIND_DATA);
    }

    /**
     * Create a WindowsFileAttributes from a WIN32_FIND_DATA structure
     */
    static WindowsFileAttributes fromFindData(long address) {
        int fileAttrs = unsafe.getInt(address + OFFSETOF_FIND_DATA_ATTRIBUTES);
        long creationTime = unsafe.getLong(address + OFFSETOF_FIND_DATA_CREATETIME);
        long lastAccessTime = unsafe.getLong(address + OFFSETOF_FIND_DATA_LASTACCESSTIME);
        long lastWriteTime = unsafe.getLong(address + OFFSETOF_FIND_DATA_LASTWRITETIME);
        long size = ((long)(unsafe.getInt(address + OFFSETOF_FIND_DATA_SIZEHIGH)) << 32)
            + (unsafe.getInt(address + OFFSETOF_FIND_DATA_SIZELOW) & 0xFFFFFFFFL);
        int reparseTag = isReparsePoint(fileAttrs) ?
            unsafe.getInt(address + OFFSETOF_FIND_DATA_RESERVED0) : 0;
        return new WindowsFileAttributes(fileAttrs,
                                         creationTime,
                                         lastAccessTime,
                                         lastWriteTime,
                                         size,
                                         reparseTag,
                                         0,  // volSerialNumber
                                         0,  // fileIndexHigh
                                         0); // fileIndexLow
    }

    /**
     * Reads the attributes of an open file
     */
    static WindowsFileAttributes readAttributes(long handle)
        throws WindowsException
    {
        NativeBuffer buffer = NativeBuffers
            .getNativeBuffer(SIZEOF_FILE_INFORMATION);
        try {
            long address = buffer.address();
            GetFileInformationByHandle(handle, address);

            // if file is a reparse point then read the tag
            int reparseTag = 0;
            int fileAttrs = unsafe
                .getInt(address + OFFSETOF_FILE_INFORMATION_ATTRIBUTES);
            if (isReparsePoint(fileAttrs)) {
                int size = MAXIMUM_REPARSE_DATA_BUFFER_SIZE;
                NativeBuffer reparseBuffer = NativeBuffers.getNativeBuffer(size);
                try {
                    DeviceIoControlGetReparsePoint(handle, reparseBuffer.address(), size);
                    reparseTag = (int)unsafe.getLong(reparseBuffer.address());
                } finally {
                    reparseBuffer.release();
                }
            }

            return fromFileInformation(address, reparseTag);
        } finally {
            buffer.release();
        }
    }

    /**
     * Returns attributes of given file.
     */
    static WindowsFileAttributes get(WindowsPath path, boolean followLinks)
        throws WindowsException
    {
        if (!ensureAccurateMetadata) {
            WindowsException firstException = null;

            // GetFileAttributesEx is the fastest way to read the attributes
            NativeBuffer buffer =
                NativeBuffers.getNativeBuffer(SIZEOF_FILE_ATTRIBUTE_DATA);
            try {
                long address = buffer.address();
                GetFileAttributesEx(path.getPathForWin32Calls(), address);
                // if reparse point then file may be a sym link; otherwise
                // just return the attributes
                int fileAttrs = unsafe
                    .getInt(address + OFFSETOF_FILE_ATTRIBUTE_DATA_ATTRIBUTES);
                if (!isReparsePoint(fileAttrs))
                    return fromFileAttributeData(address, 0);
            } catch (WindowsException x) {
                if (x.lastError() != ERROR_SHARING_VIOLATION)
                    throw x;
                firstException = x;
            } finally {
                buffer.release();
            }

            // For sharing violations, fallback to FindFirstFile if the file
            // is not a root directory.
            if (firstException != null) {
                String search = path.getPathForWin32Calls();
                char last = search.charAt(search.length() -1);
                if (last == ':' || last == '\\')
                    throw firstException;
                buffer = getBufferForFindData();
                try {
                    long handle = FindFirstFile(search, buffer.address());
                    FindClose(handle);
                    WindowsFileAttributes attrs = fromFindData(buffer.address());
                    // FindFirstFile does not follow sym links. Even if
                    // followLinks is false, there isn't sufficient information
                    // in the WIN32_FIND_DATA structure to know if the reparse
                    // point is a sym link.
                    if (attrs.isReparsePoint())
                        throw firstException;
                    return attrs;
                } catch (WindowsException ignore) {
                    throw firstException;
                } finally {
                    buffer.release();
                }
            }
        }

        // file is reparse point so need to open file to get attributes
        long handle = path.openForReadAttributeAccess(followLinks);
        try {
            return readAttributes(handle);
        } finally {
            CloseHandle(handle);
        }
    }

    /**
     * Returns true if the attributes are of the same file - both files must
     * be open.
     */
    static boolean isSameFile(WindowsFileAttributes attrs1,
                              WindowsFileAttributes attrs2)
    {
        // volume serial number and file index must be the same
        return (attrs1.volSerialNumber == attrs2.volSerialNumber) &&
               (attrs1.fileIndexHigh == attrs2.fileIndexHigh) &&
               (attrs1.fileIndexLow == attrs2.fileIndexLow);
    }

    /**
     * Returns true if the attributes are of a file with a reparse point.
     */
    static boolean isReparsePoint(int attributes) {
        return (attributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
    }

    // package-private
    int attributes() {
        return fileAttrs;
    }

    int volSerialNumber() {
        if (volSerialNumber == 0)
            throw new AssertionError("Should not get here");
        return volSerialNumber;
    }

    int fileIndexHigh() {
        if (volSerialNumber == 0)
            throw new AssertionError("Should not get here");
        return fileIndexHigh;
    }

    int fileIndexLow() {
        if (volSerialNumber == 0)
            throw new AssertionError("Should not get here");
        return fileIndexLow;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public FileTime lastModifiedTime() {
        return toFileTime(lastWriteTime);
    }

    @Override
    public FileTime lastAccessTime() {
        return toFileTime(lastAccessTime);
    }

    @Override
    public FileTime creationTime() {
        return toFileTime(creationTime);
    }

    @Override
    public Object fileKey() {
        return null;
    }

    // package private
    boolean isReparsePoint() {
        return isReparsePoint(fileAttrs);
    }

    boolean isDirectoryLink() {
        return isSymbolicLink() && ((fileAttrs & FILE_ATTRIBUTE_DIRECTORY) != 0);
    }

    @Override
    public boolean isSymbolicLink() {
        return reparseTag == IO_REPARSE_TAG_SYMLINK;
    }

    @Override
    public boolean isDirectory() {
        // ignore FILE_ATTRIBUTE_DIRECTORY attribute if file is a sym link
        if (isSymbolicLink())
            return false;
        return ((fileAttrs & FILE_ATTRIBUTE_DIRECTORY) != 0);
    }

    @Override
    public boolean isOther() {
        if (isSymbolicLink())
            return false;
        // return true if device or reparse point
        return ((fileAttrs & (FILE_ATTRIBUTE_DEVICE | FILE_ATTRIBUTE_REPARSE_POINT)) != 0);
    }

    @Override
    public boolean isRegularFile() {
        return !isSymbolicLink() && !isDirectory() && !isOther();
    }

    @Override
    public boolean isReadOnly() {
        return (fileAttrs & FILE_ATTRIBUTE_READONLY) != 0;
    }

    @Override
    public boolean isHidden() {
        return (fileAttrs & FILE_ATTRIBUTE_HIDDEN) != 0;
    }

    @Override
    public boolean isArchive() {
        return (fileAttrs & FILE_ATTRIBUTE_ARCHIVE) != 0;
    }

    @Override
    public boolean isSystem() {
        return (fileAttrs & FILE_ATTRIBUTE_SYSTEM) != 0;
    }
}
