/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.file;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.RelativePath.RelativeFile;

/**
 * This class implements the building of index of a zip archive and access to
 * its context. It also uses a prebuilt index if available.
 * It supports invocations where it will serialize an optimized zip index file
 * to disk.
 *
 * In order to use a secondary index file, set "usezipindex" in the Options
 * object when JavacFileManager is invoked. (You can pass "-XDusezipindex" on
 * the command line.)
 *
 * Location where to look for/generate optimized zip index files can be
 * provided using "-XDcachezipindexdir=<directory>". If this flag is not
 * provided, the default location is the value of the "java.io.tmpdir" system
 * property.
 *
 * If "-XDwritezipindexfiles" is specified, there will be new optimized index
 * file created for each archive, used by the compiler for compilation, at the
 * location specified by the "cachezipindexdir" option.
 *
 * If system property nonBatchMode option is specified the compiler will use
 * timestamp checking to reindex the zip files if it is needed. In batch mode
 * the timestamps are not checked and the compiler uses the cached indexes.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class ZipFileIndex {
    private static final String MIN_CHAR = String.valueOf(Character.MIN_VALUE);
    private static final String MAX_CHAR = String.valueOf(Character.MAX_VALUE);

    public final static long NOT_MODIFIED = Long.MIN_VALUE;


    private static boolean NON_BATCH_MODE = System.getProperty("nonBatchMode") != null;// TODO: Use -XD compiler switch for this.

    private Map<RelativeDirectory, DirectoryEntry> directories =
            Collections.<RelativeDirectory, DirectoryEntry>emptyMap();
    private Set<RelativeDirectory> allDirs =
            Collections.<RelativeDirectory>emptySet();

    // ZipFileIndex data entries
    final File zipFile;
    private Reference<File> absFileRef;
    long zipFileLastModified = NOT_MODIFIED;
    private RandomAccessFile zipRandomFile;
    private Entry[] entries;

    private boolean readFromIndex = false;
    private File zipIndexFile = null;
    private boolean triedToReadIndex = false;
    final RelativeDirectory symbolFilePrefix;
    private final int symbolFilePrefixLength;
    private boolean hasPopulatedData = false;
    long lastReferenceTimeStamp = NOT_MODIFIED;

    private final boolean usePreindexedCache;
    private final String preindexedCacheLocation;

    private boolean writeIndex = false;

    private Map<String, SoftReference<RelativeDirectory>> relativeDirectoryCache =
            new HashMap<String, SoftReference<RelativeDirectory>>();


    public synchronized boolean isOpen() {
        return (zipRandomFile != null);
    }

    ZipFileIndex(File zipFile, RelativeDirectory symbolFilePrefix, boolean writeIndex,
            boolean useCache, String cacheLocation) throws IOException {
        this.zipFile = zipFile;
        this.symbolFilePrefix = symbolFilePrefix;
        this.symbolFilePrefixLength = (symbolFilePrefix == null ? 0 :
            symbolFilePrefix.getPath().getBytes("UTF-8").length);
        this.writeIndex = writeIndex;
        this.usePreindexedCache = useCache;
        this.preindexedCacheLocation = cacheLocation;

        if (zipFile != null) {
            this.zipFileLastModified = zipFile.lastModified();
        }

        // Validate integrity of the zip file
        checkIndex();
    }

    @Override
    public String toString() {
        return "ZipFileIndex[" + zipFile + "]";
    }

    // Just in case...
    @Override
    protected void finalize() throws Throwable {
        closeFile();
        super.finalize();
    }

    private boolean isUpToDate() {
        if (zipFile != null
                && ((!NON_BATCH_MODE) || zipFileLastModified == zipFile.lastModified())
                && hasPopulatedData) {
            return true;
        }

        return false;
    }

    /**
     * Here we need to make sure that the ZipFileIndex is valid. Check the timestamp of the file and
     * if its the same as the one at the time the index was build we don't need to reopen anything.
     */
    private void checkIndex() throws IOException {
        boolean isUpToDate = true;
        if (!isUpToDate()) {
            closeFile();
            isUpToDate = false;
        }

        if (zipRandomFile != null || isUpToDate) {
            lastReferenceTimeStamp = System.currentTimeMillis();
            return;
        }

        hasPopulatedData = true;

        if (readIndex()) {
            lastReferenceTimeStamp = System.currentTimeMillis();
            return;
        }

        directories = Collections.<RelativeDirectory, DirectoryEntry>emptyMap();
        allDirs = Collections.<RelativeDirectory>emptySet();

        try {
            openFile();
            long totalLength = zipRandomFile.length();
            ZipDirectory directory = new ZipDirectory(zipRandomFile, 0L, totalLength, this);
            directory.buildIndex();
        } finally {
            if (zipRandomFile != null) {
                closeFile();
            }
        }

        lastReferenceTimeStamp = System.currentTimeMillis();
    }

    private void openFile() throws FileNotFoundException {
        if (zipRandomFile == null && zipFile != null) {
            zipRandomFile = new RandomAccessFile(zipFile, "r");
        }
    }

    private void cleanupState() {
        // Make sure there is a valid but empty index if the file doesn't exist
        entries = Entry.EMPTY_ARRAY;
        directories = Collections.<RelativeDirectory, DirectoryEntry>emptyMap();
        zipFileLastModified = NOT_MODIFIED;
        allDirs = Collections.<RelativeDirectory>emptySet();
    }

    public synchronized void close() {
        writeIndex();
        closeFile();
    }

    private void closeFile() {
        if (zipRandomFile != null) {
            try {
                zipRandomFile.close();
            } catch (IOException ex) {
            }
            zipRandomFile = null;
        }
    }

    /**
     * Returns the ZipFileIndexEntry for a path, if there is one.
     */
    synchronized Entry getZipIndexEntry(RelativePath path) {
        try {
            checkIndex();
            DirectoryEntry de = directories.get(path.dirname());
            String lookFor = path.basename();
            return (de == null) ? null : de.getEntry(lookFor);
        }
        catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns a javac List of filenames within a directory in the ZipFileIndex.
     */
    public synchronized com.sun.tools.javac.util.List<String> getFiles(RelativeDirectory path) {
        try {
            checkIndex();

            DirectoryEntry de = directories.get(path);
            com.sun.tools.javac.util.List<String> ret = de == null ? null : de.getFiles();

            if (ret == null) {
                return com.sun.tools.javac.util.List.<String>nil();
            }
            return ret;
        }
        catch (IOException e) {
            return com.sun.tools.javac.util.List.<String>nil();
        }
    }

    public synchronized List<String> getDirectories(RelativeDirectory path) {
        try {
            checkIndex();

            DirectoryEntry de = directories.get(path);
            com.sun.tools.javac.util.List<String> ret = de == null ? null : de.getDirectories();

            if (ret == null) {
                return com.sun.tools.javac.util.List.<String>nil();
            }

            return ret;
        }
        catch (IOException e) {
            return com.sun.tools.javac.util.List.<String>nil();
        }
    }

    public synchronized Set<RelativeDirectory> getAllDirectories() {
        try {
            checkIndex();
            if (allDirs == Collections.EMPTY_SET) {
                allDirs = new HashSet<RelativeDirectory>(directories.keySet());
            }

            return allDirs;
        }
        catch (IOException e) {
            return Collections.<RelativeDirectory>emptySet();
        }
    }

    /**
     * Tests if a specific path exists in the zip.  This method will return true
     * for file entries and directories.
     *
     * @param path A path within the zip.
     * @return True if the path is a file or dir, false otherwise.
     */
    public synchronized boolean contains(RelativePath path) {
        try {
            checkIndex();
            return getZipIndexEntry(path) != null;
        }
        catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean isDirectory(RelativePath path) throws IOException {
        // The top level in a zip file is always a directory.
        if (path.getPath().length() == 0) {
            lastReferenceTimeStamp = System.currentTimeMillis();
            return true;
        }

        checkIndex();
        return directories.get(path) != null;
    }

    public synchronized long getLastModified(RelativeFile path) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null)
            throw new FileNotFoundException();
        return entry.getLastModified();
    }

    public synchronized int length(RelativeFile path) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null)
            throw new FileNotFoundException();

        if (entry.isDir) {
            return 0;
        }

        byte[] header = getHeader(entry);
        // entry is not compressed?
        if (get2ByteLittleEndian(header, 8) == 0) {
            return entry.compressedSize;
        } else {
            return entry.size;
        }
    }

    public synchronized byte[] read(RelativeFile path) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null)
            throw new FileNotFoundException("Path not found in ZIP: " + path.path);
        return read(entry);
    }

    synchronized byte[] read(Entry entry) throws IOException {
        openFile();
        byte[] result = readBytes(entry);
        closeFile();
        return result;
    }

    public synchronized int read(RelativeFile path, byte[] buffer) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null)
            throw new FileNotFoundException();
        return read(entry, buffer);
    }

    synchronized int read(Entry entry, byte[] buffer)
            throws IOException {
        int result = readBytes(entry, buffer);
        return  result;
    }

    private byte[] readBytes(Entry entry) throws IOException {
        byte[] header = getHeader(entry);
        int csize = entry.compressedSize;
        byte[] cbuf = new byte[csize];
        zipRandomFile.skipBytes(get2ByteLittleEndian(header, 26) + get2ByteLittleEndian(header, 28));
        zipRandomFile.readFully(cbuf, 0, csize);

        // is this compressed - offset 8 in the ZipEntry header
        if (get2ByteLittleEndian(header, 8) == 0)
            return cbuf;

        int size = entry.size;
        byte[] buf = new byte[size];
        if (inflate(cbuf, buf) != size)
            throw new ZipException("corrupted zip file");

        return buf;
    }

    /**
     *
     */
    private int readBytes(Entry entry, byte[] buffer) throws IOException {
        byte[] header = getHeader(entry);

        // entry is not compressed?
        if (get2ByteLittleEndian(header, 8) == 0) {
            zipRandomFile.skipBytes(get2ByteLittleEndian(header, 26) + get2ByteLittleEndian(header, 28));
            int offset = 0;
            int size = buffer.length;
            while (offset < size) {
                int count = zipRandomFile.read(buffer, offset, size - offset);
                if (count == -1)
                    break;
                offset += count;
            }
            return entry.size;
        }

        int csize = entry.compressedSize;
        byte[] cbuf = new byte[csize];
        zipRandomFile.skipBytes(get2ByteLittleEndian(header, 26) + get2ByteLittleEndian(header, 28));
        zipRandomFile.readFully(cbuf, 0, csize);

        int count = inflate(cbuf, buffer);
        if (count == -1)
            throw new ZipException("corrupted zip file");

        return entry.size;
    }

    //----------------------------------------------------------------------------
    // Zip utilities
    //----------------------------------------------------------------------------

    private byte[] getHeader(Entry entry) throws IOException {
        zipRandomFile.seek(entry.offset);
        byte[] header = new byte[30];
        zipRandomFile.readFully(header);
        if (get4ByteLittleEndian(header, 0) != 0x04034b50)
            throw new ZipException("corrupted zip file");
        if ((get2ByteLittleEndian(header, 6) & 1) != 0)
            throw new ZipException("encrypted zip file"); // offset 6 in the header of the ZipFileEntry
        return header;
    }

  /*
   * Inflate using the java.util.zip.Inflater class
   */
    private SoftReference<Inflater> inflaterRef;
    private int inflate(byte[] src, byte[] dest) {
        Inflater inflater = (inflaterRef == null ? null : inflaterRef.get());

        // construct the inflater object or reuse an existing one
        if (inflater == null)
            inflaterRef = new SoftReference<Inflater>(inflater = new Inflater(true));

        inflater.reset();
        inflater.setInput(src);
        try {
            return inflater.inflate(dest);
        } catch (DataFormatException ex) {
            return -1;
        }
    }

    /**
     * return the two bytes buf[pos], buf[pos+1] as an unsigned integer in little
     * endian format.
     */
    private static int get2ByteLittleEndian(byte[] buf, int pos) {
        return (buf[pos] & 0xFF) + ((buf[pos+1] & 0xFF) << 8);
    }

    /**
     * return the 4 bytes buf[i..i+3] as an integer in little endian format.
     */
    private static int get4ByteLittleEndian(byte[] buf, int pos) {
        return (buf[pos] & 0xFF) + ((buf[pos + 1] & 0xFF) << 8) +
                ((buf[pos + 2] & 0xFF) << 16) + ((buf[pos + 3] & 0xFF) << 24);
    }

    /* ----------------------------------------------------------------------------
     * ZipDirectory
     * ----------------------------------------------------------------------------*/

    private class ZipDirectory {
        private RelativeDirectory lastDir;
        private int lastStart;
        private int lastLen;

        byte[] zipDir;
        RandomAccessFile zipRandomFile = null;
        ZipFileIndex zipFileIndex = null;

        public ZipDirectory(RandomAccessFile zipRandomFile, long start, long end, ZipFileIndex index) throws IOException {
            this.zipRandomFile = zipRandomFile;
            this.zipFileIndex = index;
            hasValidHeader();
            findCENRecord(start, end);
        }

        /*
         * the zip entry signature should be at offset 0, otherwise allow the
         * calling logic to take evasive action by throwing ZipFormatException.
         */
        private boolean hasValidHeader() throws IOException {
            final long pos = zipRandomFile.getFilePointer();
            try {
                if (zipRandomFile.read() == 'P') {
                    if (zipRandomFile.read() == 'K') {
                        if (zipRandomFile.read() == 0x03) {
                            if (zipRandomFile.read() == 0x04) {
                                return true;
                            }
                        }
                    }
                }
            } finally {
                zipRandomFile.seek(pos);
            }
            throw new ZipFormatException("invalid zip magic");
        }

        /*
         * Reads zip file central directory.
         * For more details see readCEN in zip_util.c from the JDK sources.
         * This is a Java port of that function.
         */
        private void findCENRecord(long start, long end) throws IOException {
            long totalLength = end - start;
            int endbuflen = 1024;
            byte[] endbuf = new byte[endbuflen];
            long endbufend = end - start;

            // There is a variable-length field after the dir offset record. We need to do consequential search.
            while (endbufend >= 22) {
                if (endbufend < endbuflen)
                    endbuflen = (int)endbufend;
                long endbufpos = endbufend - endbuflen;
                zipRandomFile.seek(start + endbufpos);
                zipRandomFile.readFully(endbuf, 0, endbuflen);
                int i = endbuflen - 22;
                while (i >= 0 &&
                        !(endbuf[i] == 0x50 &&
                        endbuf[i + 1] == 0x4b &&
                        endbuf[i + 2] == 0x05 &&
                        endbuf[i + 3] == 0x06 &&
                        endbufpos + i + 22 +
                        get2ByteLittleEndian(endbuf, i + 20) == totalLength)) {
                    i--;
                }

                if (i >= 0) {
                    zipDir = new byte[get4ByteLittleEndian(endbuf, i + 12) + 2];
                    zipDir[0] = endbuf[i + 10];
                    zipDir[1] = endbuf[i + 11];
                    int sz = get4ByteLittleEndian(endbuf, i + 16);
                    // a negative offset or the entries field indicates a
                    // potential zip64 archive
                    if (sz < 0 || get2ByteLittleEndian(zipDir, 0) == 0xffff) {
                        throw new ZipFormatException("detected a zip64 archive");
                    }
                    zipRandomFile.seek(start + sz);
                    zipRandomFile.readFully(zipDir, 2, zipDir.length - 2);
                    return;
                } else {
                    endbufend = endbufpos + 21;
                }
            }
            throw new ZipException("cannot read zip file");
        }

        private void buildIndex() throws IOException {
            int entryCount = get2ByteLittleEndian(zipDir, 0);

            // Add each of the files
            if (entryCount > 0) {
                directories = new HashMap<RelativeDirectory, DirectoryEntry>();
                ArrayList<Entry> entryList = new ArrayList<Entry>();
                int pos = 2;
                for (int i = 0; i < entryCount; i++) {
                    pos = readEntry(pos, entryList, directories);
                }

                // Add the accumulated dirs into the same list
                for (RelativeDirectory d: directories.keySet()) {
                    // use shared RelativeDirectory objects for parent dirs
                    RelativeDirectory parent = getRelativeDirectory(d.dirname().getPath());
                    String file = d.basename();
                    Entry zipFileIndexEntry = new Entry(parent, file);
                    zipFileIndexEntry.isDir = true;
                    entryList.add(zipFileIndexEntry);
                }

                entries = entryList.toArray(new Entry[entryList.size()]);
                Arrays.sort(entries);
            } else {
                cleanupState();
            }
        }

        private int readEntry(int pos, List<Entry> entryList,
                Map<RelativeDirectory, DirectoryEntry> directories) throws IOException {
            if (get4ByteLittleEndian(zipDir, pos) != 0x02014b50) {
                throw new ZipException("cannot read zip file entry");
            }

            int dirStart = pos + 46;
            int fileStart = dirStart;
            int fileEnd = fileStart + get2ByteLittleEndian(zipDir, pos + 28);

            if (zipFileIndex.symbolFilePrefixLength != 0 &&
                    ((fileEnd - fileStart) >= symbolFilePrefixLength)) {
                dirStart += zipFileIndex.symbolFilePrefixLength;
               fileStart += zipFileIndex.symbolFilePrefixLength;
            }
            // Force any '\' to '/'. Keep the position of the last separator.
            for (int index = fileStart; index < fileEnd; index++) {
                byte nextByte = zipDir[index];
                if (nextByte == (byte)'\\') {
                    zipDir[index] = (byte)'/';
                    fileStart = index + 1;
                } else if (nextByte == (byte)'/') {
                    fileStart = index + 1;
                }
            }

            RelativeDirectory directory = null;
            if (fileStart == dirStart)
                directory = getRelativeDirectory("");
            else if (lastDir != null && lastLen == fileStart - dirStart - 1) {
                int index = lastLen - 1;
                while (zipDir[lastStart + index] == zipDir[dirStart + index]) {
                    if (index == 0) {
                        directory = lastDir;
                        break;
                    }
                    index--;
                }
            }

            // Sub directories
            if (directory == null) {
                lastStart = dirStart;
                lastLen = fileStart - dirStart - 1;

                directory = getRelativeDirectory(new String(zipDir, dirStart, lastLen, "UTF-8"));
                lastDir = directory;

                // Enter also all the parent directories
                RelativeDirectory tempDirectory = directory;

                while (directories.get(tempDirectory) == null) {
                    directories.put(tempDirectory, new DirectoryEntry(tempDirectory, zipFileIndex));
                    if (tempDirectory.path.indexOf("/") == tempDirectory.path.length() - 1)
                        break;
                    else {
                        // use shared RelativeDirectory objects for parent dirs
                        tempDirectory = getRelativeDirectory(tempDirectory.dirname().getPath());
                    }
                }
            }
            else {
                if (directories.get(directory) == null) {
                    directories.put(directory, new DirectoryEntry(directory, zipFileIndex));
                }
            }

            // For each dir create also a file
            if (fileStart != fileEnd) {
                Entry entry = new Entry(directory,
                        new String(zipDir, fileStart, fileEnd - fileStart, "UTF-8"));

                entry.setNativeTime(get4ByteLittleEndian(zipDir, pos + 12));
                entry.compressedSize = get4ByteLittleEndian(zipDir, pos + 20);
                entry.size = get4ByteLittleEndian(zipDir, pos + 24);
                entry.offset = get4ByteLittleEndian(zipDir, pos + 42);
                entryList.add(entry);
            }

            return pos + 46 +
                    get2ByteLittleEndian(zipDir, pos + 28) +
                    get2ByteLittleEndian(zipDir, pos + 30) +
                    get2ByteLittleEndian(zipDir, pos + 32);
        }
    }

    /**
     * Returns the last modified timestamp of a zip file.
     * @return long
     */
    public long getZipFileLastModified() throws IOException {
        synchronized (this) {
            checkIndex();
            return zipFileLastModified;
        }
    }

    /** ------------------------------------------------------------------------
     *  DirectoryEntry class
     * -------------------------------------------------------------------------*/

    static class DirectoryEntry {
        private boolean filesInited;
        private boolean directoriesInited;
        private boolean zipFileEntriesInited;
        private boolean entriesInited;

        private long writtenOffsetOffset = 0;

        private RelativeDirectory dirName;

        private com.sun.tools.javac.util.List<String> zipFileEntriesFiles = com.sun.tools.javac.util.List.<String>nil();
        private com.sun.tools.javac.util.List<String> zipFileEntriesDirectories = com.sun.tools.javac.util.List.<String>nil();
        private com.sun.tools.javac.util.List<Entry>  zipFileEntries = com.sun.tools.javac.util.List.<Entry>nil();

        private List<Entry> entries = new ArrayList<Entry>();

        private ZipFileIndex zipFileIndex;

        private int numEntries;

        DirectoryEntry(RelativeDirectory dirName, ZipFileIndex index) {
            filesInited = false;
            directoriesInited = false;
            entriesInited = false;

            this.dirName = dirName;
            this.zipFileIndex = index;
        }

        private com.sun.tools.javac.util.List<String> getFiles() {
            if (!filesInited) {
                initEntries();
                for (Entry e : entries) {
                    if (!e.isDir) {
                        zipFileEntriesFiles = zipFileEntriesFiles.append(e.name);
                    }
                }
                filesInited = true;
            }
            return zipFileEntriesFiles;
        }

        private com.sun.tools.javac.util.List<String> getDirectories() {
            if (!directoriesInited) {
                initEntries();
                for (Entry e : entries) {
                    if (e.isDir) {
                        zipFileEntriesDirectories = zipFileEntriesDirectories.append(e.name);
                    }
                }
                directoriesInited = true;
            }
            return zipFileEntriesDirectories;
        }

        private com.sun.tools.javac.util.List<Entry> getEntries() {
            if (!zipFileEntriesInited) {
                initEntries();
                zipFileEntries = com.sun.tools.javac.util.List.nil();
                for (Entry zfie : entries) {
                    zipFileEntries = zipFileEntries.append(zfie);
                }
                zipFileEntriesInited = true;
            }
            return zipFileEntries;
        }

        private Entry getEntry(String rootName) {
            initEntries();
            int index = Collections.binarySearch(entries, new Entry(dirName, rootName));
            if (index < 0) {
                return null;
            }

            return entries.get(index);
        }

        private void initEntries() {
            if (entriesInited) {
                return;
            }

            if (!zipFileIndex.readFromIndex) {
                int from = -Arrays.binarySearch(zipFileIndex.entries,
                        new Entry(dirName, ZipFileIndex.MIN_CHAR)) - 1;
                int to = -Arrays.binarySearch(zipFileIndex.entries,
                        new Entry(dirName, MAX_CHAR)) - 1;

                for (int i = from; i < to; i++) {
                    entries.add(zipFileIndex.entries[i]);
                }
            } else {
                File indexFile = zipFileIndex.getIndexFile();
                if (indexFile != null) {
                    RandomAccessFile raf = null;
                    try {
                        raf = new RandomAccessFile(indexFile, "r");
                        raf.seek(writtenOffsetOffset);

                        for (int nFiles = 0; nFiles < numEntries; nFiles++) {
                            // Read the name bytes
                            int zfieNameBytesLen = raf.readInt();
                            byte [] zfieNameBytes = new byte[zfieNameBytesLen];
                            raf.read(zfieNameBytes);
                            String eName = new String(zfieNameBytes, "UTF-8");

                            // Read isDir
                            boolean eIsDir = raf.readByte() == (byte)0 ? false : true;

                            // Read offset of bytes in the real Jar/Zip file
                            int eOffset = raf.readInt();

                            // Read size of the file in the real Jar/Zip file
                            int eSize = raf.readInt();

                            // Read compressed size of the file in the real Jar/Zip file
                            int eCsize = raf.readInt();

                            // Read java time stamp of the file in the real Jar/Zip file
                            long eJavaTimestamp = raf.readLong();

                            Entry rfie = new Entry(dirName, eName);
                            rfie.isDir = eIsDir;
                            rfie.offset = eOffset;
                            rfie.size = eSize;
                            rfie.compressedSize = eCsize;
                            rfie.javatime = eJavaTimestamp;
                            entries.add(rfie);
                        }
                    } catch (Throwable t) {
                        // Do nothing
                    } finally {
                        try {
                            if (raf != null) {
                                raf.close();
                            }
                        } catch (Throwable t) {
                            // Do nothing
                        }
                    }
                }
            }

            entriesInited = true;
        }

        List<Entry> getEntriesAsCollection() {
            initEntries();

            return entries;
        }
    }

    private boolean readIndex() {
        if (triedToReadIndex || !usePreindexedCache) {
            return false;
        }

        boolean ret = false;
        synchronized (this) {
            triedToReadIndex = true;
            RandomAccessFile raf = null;
            try {
                File indexFileName = getIndexFile();
                raf = new RandomAccessFile(indexFileName, "r");

                long fileStamp = raf.readLong();
                if (zipFile.lastModified() != fileStamp) {
                    ret = false;
                } else {
                    directories = new HashMap<RelativeDirectory, DirectoryEntry>();
                    int numDirs = raf.readInt();
                    for (int nDirs = 0; nDirs < numDirs; nDirs++) {
                        int dirNameBytesLen = raf.readInt();
                        byte [] dirNameBytes = new byte[dirNameBytesLen];
                        raf.read(dirNameBytes);

                        RelativeDirectory dirNameStr = getRelativeDirectory(new String(dirNameBytes, "UTF-8"));
                        DirectoryEntry de = new DirectoryEntry(dirNameStr, this);
                        de.numEntries = raf.readInt();
                        de.writtenOffsetOffset = raf.readLong();
                        directories.put(dirNameStr, de);
                    }
                    ret = true;
                    zipFileLastModified = fileStamp;
                }
            } catch (Throwable t) {
                // Do nothing
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (Throwable tt) {
                        // Do nothing
                    }
                }
            }
            if (ret == true) {
                readFromIndex = true;
            }
        }

        return ret;
    }

    private boolean writeIndex() {
        boolean ret = false;
        if (readFromIndex || !usePreindexedCache) {
            return true;
        }

        if (!writeIndex) {
            return true;
        }

        File indexFile = getIndexFile();
        if (indexFile == null) {
            return false;
        }

        RandomAccessFile raf = null;
        long writtenSoFar = 0;
        try {
            raf = new RandomAccessFile(indexFile, "rw");

            raf.writeLong(zipFileLastModified);
            writtenSoFar += 8;

            List<DirectoryEntry> directoriesToWrite = new ArrayList<DirectoryEntry>();
            Map<RelativeDirectory, Long> offsets = new HashMap<RelativeDirectory, Long>();
            raf.writeInt(directories.keySet().size());
            writtenSoFar += 4;

            for (RelativeDirectory dirName: directories.keySet()) {
                DirectoryEntry dirEntry = directories.get(dirName);

                directoriesToWrite.add(dirEntry);

                // Write the dir name bytes
                byte [] dirNameBytes = dirName.getPath().getBytes("UTF-8");
                int dirNameBytesLen = dirNameBytes.length;
                raf.writeInt(dirNameBytesLen);
                writtenSoFar += 4;

                raf.write(dirNameBytes);
                writtenSoFar += dirNameBytesLen;

                // Write the number of files in the dir
                List<Entry> dirEntries = dirEntry.getEntriesAsCollection();
                raf.writeInt(dirEntries.size());
                writtenSoFar += 4;

                offsets.put(dirName, new Long(writtenSoFar));

                // Write the offset of the file's data in the dir
                dirEntry.writtenOffsetOffset = 0L;
                raf.writeLong(0L);
                writtenSoFar += 8;
            }

            for (DirectoryEntry de : directoriesToWrite) {
                // Fix up the offset in the directory table
                long currFP = raf.getFilePointer();

                long offsetOffset = offsets.get(de.dirName).longValue();
                raf.seek(offsetOffset);
                raf.writeLong(writtenSoFar);

                raf.seek(currFP);

                // Now write each of the files in the DirectoryEntry
                List<Entry> list = de.getEntriesAsCollection();
                for (Entry zfie : list) {
                    // Write the name bytes
                    byte [] zfieNameBytes = zfie.name.getBytes("UTF-8");
                    int zfieNameBytesLen = zfieNameBytes.length;
                    raf.writeInt(zfieNameBytesLen);
                    writtenSoFar += 4;
                    raf.write(zfieNameBytes);
                    writtenSoFar += zfieNameBytesLen;

                    // Write isDir
                    raf.writeByte(zfie.isDir ? (byte)1 : (byte)0);
                    writtenSoFar += 1;

                    // Write offset of bytes in the real Jar/Zip file
                    raf.writeInt(zfie.offset);
                    writtenSoFar += 4;

                    // Write size of the file in the real Jar/Zip file
                    raf.writeInt(zfie.size);
                    writtenSoFar += 4;

                    // Write compressed size of the file in the real Jar/Zip file
                    raf.writeInt(zfie.compressedSize);
                    writtenSoFar += 4;

                    // Write java time stamp of the file in the real Jar/Zip file
                    raf.writeLong(zfie.getLastModified());
                    writtenSoFar += 8;
                }
            }
        } catch (Throwable t) {
            // Do nothing
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                }
            } catch(IOException ioe) {
                // Do nothing
            }
        }

        return ret;
    }

    public boolean writeZipIndex() {
        synchronized (this) {
            return writeIndex();
        }
    }

    private File getIndexFile() {
        if (zipIndexFile == null) {
            if (zipFile == null) {
                return null;
            }

            zipIndexFile = new File((preindexedCacheLocation == null ? "" : preindexedCacheLocation) +
                    zipFile.getName() + ".index");
        }

        return zipIndexFile;
    }

    public File getZipFile() {
        return zipFile;
    }

    File getAbsoluteFile() {
        File absFile = (absFileRef == null ? null : absFileRef.get());
        if (absFile == null) {
            absFile = zipFile.getAbsoluteFile();
            absFileRef = new SoftReference<File>(absFile);
        }
        return absFile;
    }

    private RelativeDirectory getRelativeDirectory(String path) {
        RelativeDirectory rd;
        SoftReference<RelativeDirectory> ref = relativeDirectoryCache.get(path);
        if (ref != null) {
            rd = ref.get();
            if (rd != null)
                return rd;
        }
        rd = new RelativeDirectory(path);
        relativeDirectoryCache.put(path, new SoftReference<RelativeDirectory>(rd));
        return rd;
    }

    static class Entry implements Comparable<Entry> {
        public static final Entry[] EMPTY_ARRAY = {};

        // Directory related
        RelativeDirectory dir;
        boolean isDir;

        // File related
        String name;

        int offset;
        int size;
        int compressedSize;
        long javatime;

        private int nativetime;

        public Entry(RelativePath path) {
            this(path.dirname(), path.basename());
        }

        public Entry(RelativeDirectory directory, String name) {
            this.dir = directory;
            this.name = name;
        }

        public String getName() {
            return new RelativeFile(dir, name).getPath();
        }

        public String getFileName() {
            return name;
        }

        public long getLastModified() {
            if (javatime == 0) {
                    javatime = dosToJavaTime(nativetime);
            }
            return javatime;
        }

        // based on dosToJavaTime in java.util.Zip, but avoiding the
        // use of deprecated Date constructor
        private static long dosToJavaTime(int dtime) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR,        ((dtime >> 25) & 0x7f) + 1980);
            c.set(Calendar.MONTH,       ((dtime >> 21) & 0x0f) - 1);
            c.set(Calendar.DATE,        ((dtime >> 16) & 0x1f));
            c.set(Calendar.HOUR_OF_DAY, ((dtime >> 11) & 0x1f));
            c.set(Calendar.MINUTE,      ((dtime >>  5) & 0x3f));
            c.set(Calendar.SECOND,      ((dtime <<  1) & 0x3e));
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }

        void setNativeTime(int natTime) {
            nativetime = natTime;
        }

        public boolean isDirectory() {
            return isDir;
        }

        public int compareTo(Entry other) {
            RelativeDirectory otherD = other.dir;
            if (dir != otherD) {
                int c = dir.compareTo(otherD);
                if (c != 0)
                    return c;
            }
            return name.compareTo(other.name);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry other = (Entry) o;
            return dir.equals(other.dir) && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (this.dir != null ? this.dir.hashCode() : 0);
            hash = 97 * hash + (this.name != null ? this.name.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            return isDir ? ("Dir:" + dir + " : " + name) :
                (dir + ":" + name);
        }
    }

    /*
     * Exception primarily used to implement a failover, used exclusively here.
     */

    static final class ZipFormatException extends IOException {
        private static final long serialVersionUID = 8000196834066748623L;
        protected ZipFormatException(String message) {
            super(message);
        }

        protected ZipFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
