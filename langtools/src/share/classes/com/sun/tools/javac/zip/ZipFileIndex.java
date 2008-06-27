/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.zip;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.*;

/** This class implements building of index of a zip archive and access to it's context.
 *  It also uses prebuild index if available. It supports invocations where it will
 *  serialize an optimized zip index file to disk.
 *
 *  In oreder to use secondary index file make sure the option "usezipindex" is in the Options object,
 *  when JavacFileManager is invoked. (You can pass "-XDusezipindex" on the command line.
 *
 *  Location where to look for/generate optimized zip index files can be provided using
 *  "-XDcachezipindexdir=<directory>". If this flag is not provided, the dfault location is
 *  the value of the "java.io.tmpdir" system property.
 *
 *  If key "-XDwritezipindexfiles" is specified, there will be new optimized index file
 *  created for each archive, used by the compiler for compilation, at location,
 *  specified by "cachezipindexdir" option.
 *
 * If nonBatchMode option is specified (-XDnonBatchMode) the compiler will use timestamp
 * checking to reindex the zip files if it is needed. In batch mode the timestamps are not checked
 * and the compiler uses the cached indexes.
 */
public class ZipFileIndex {
    private static final String MIN_CHAR = String.valueOf(Character.MIN_VALUE);
    private static final String MAX_CHAR = String.valueOf(Character.MAX_VALUE);

    public final static long NOT_MODIFIED = Long.MIN_VALUE;

    private static Map<File, ZipFileIndex> zipFileIndexCache = new HashMap<File, ZipFileIndex>();
    private static ReentrantLock lock = new ReentrantLock();

    private static boolean NON_BATCH_MODE = System.getProperty("nonBatchMode") != null;// TODO: Use -XD compiler switch for this.

    private Map<String, DirectoryEntry> directories = Collections.<String, DirectoryEntry>emptyMap();
    private Set<String> allDirs = Collections.<String>emptySet();

    // ZipFileIndex data entries
    private File zipFile;
    private long zipFileLastModified = NOT_MODIFIED;
    private RandomAccessFile zipRandomFile;
    private ZipFileIndexEntry[] entries;

    private boolean readFromIndex = false;
    private File zipIndexFile = null;
    private boolean triedToReadIndex = false;
    private int symbolFilePrefixLength = 0;
    private boolean hasPopulatedData = false;
    private long lastReferenceTimeStamp = NOT_MODIFIED;

    private boolean usePreindexedCache = false;
    private String preindexedCacheLocation = null;

    private boolean writeIndex = false;

    /**
     * Returns a list of all ZipFileIndex entries
     *
     * @return A list of ZipFileIndex entries, or an empty list
     */
    public static List<ZipFileIndex> getZipFileIndexes() {
        return getZipFileIndexes(false);
    }

    /**
     * Returns a list of all ZipFileIndex entries
     *
     * @param openedOnly If true it returns a list of only opened ZipFileIndex entries, otherwise
     *                   all ZipFileEntry(s) are included into the list.
     * @return A list of ZipFileIndex entries, or an empty list
     */
    public static List<ZipFileIndex> getZipFileIndexes(boolean openedOnly) {
        List<ZipFileIndex> zipFileIndexes = new ArrayList<ZipFileIndex>();
        lock.lock();
        try {
            zipFileIndexes.addAll(zipFileIndexCache.values());

            if (openedOnly) {
                for(ZipFileIndex elem : zipFileIndexes) {
                    if (!elem.isOpen()) {
                        zipFileIndexes.remove(elem);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
        return zipFileIndexes;
    }

    public boolean isOpen() {
        lock.lock();
        try {
            return zipRandomFile != null;
        }
        finally {
            lock.unlock();
        }
    }

    public static ZipFileIndex getZipFileIndex(File zipFile, int symbolFilePrefixLen, boolean useCache, String cacheLocation, boolean writeIndex) throws IOException {
        ZipFileIndex zi = null;
        lock.lock();
        try {
            zi = getExistingZipIndex(zipFile);

            if (zi == null || (zi != null && zipFile.lastModified() != zi.zipFileLastModified)) {
                zi = new ZipFileIndex(zipFile, symbolFilePrefixLen, writeIndex,
                        useCache, cacheLocation);
                zipFileIndexCache.put(zipFile, zi);
            }
        }
        finally {
            lock.unlock();
        }
        return zi;
    }

    public static ZipFileIndex getExistingZipIndex(File zipFile) {
        lock.lock();
        try {
            return zipFileIndexCache.get(zipFile);
        }
        finally {
            lock.unlock();
        }
    }

    public static void clearCache() {
        lock.lock();
        try {
            zipFileIndexCache.clear();
        }
        finally {
            lock.unlock();
        }
    }

    public static void clearCache(long timeNotUsed) {
        lock.lock();
        try {
            Iterator<File> cachedFileIterator = zipFileIndexCache.keySet().iterator();
            while (cachedFileIterator.hasNext()) {
                File cachedFile = cachedFileIterator.next();
                ZipFileIndex cachedZipIndex = zipFileIndexCache.get(cachedFile);
                if (cachedZipIndex != null) {
                    long timeToTest = cachedZipIndex.lastReferenceTimeStamp + timeNotUsed;
                    if (timeToTest < cachedZipIndex.lastReferenceTimeStamp || // Overflow...
                            System.currentTimeMillis() > timeToTest) {
                        zipFileIndexCache.remove(cachedFile);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    public static void removeFromCache(File file) {
        lock.lock();
        try {
            zipFileIndexCache.remove(file);
        }
        finally {
            lock.unlock();
        }
    }

    /** Sets already opened list of ZipFileIndexes from an outside client
      * of the compiler. This functionality should be used in a non-batch clients of the compiler.
      */
    public static void setOpenedIndexes(List<ZipFileIndex>indexes) throws IllegalStateException {
        lock.lock();
        try {
            if (zipFileIndexCache.isEmpty()) {
                throw new IllegalStateException("Setting opened indexes should be called only when the ZipFileCache is empty. Call JavacFileManager.flush() before calling this method.");
            }

            for (ZipFileIndex zfi : indexes) {
                zipFileIndexCache.put(zfi.zipFile, zfi);
            }
        }
        finally {
            lock.unlock();
        }
    }

    private ZipFileIndex(File zipFile, int symbolFilePrefixLen, boolean writeIndex,
            boolean useCache, String cacheLocation) throws IOException {
        this.zipFile = zipFile;
        this.symbolFilePrefixLength = symbolFilePrefixLen;
        this.writeIndex = writeIndex;
        this.usePreindexedCache = useCache;
        this.preindexedCacheLocation = cacheLocation;

        if (zipFile != null) {
            this.zipFileLastModified = zipFile.lastModified();
        }

        // Validate integrity of the zip file
        checkIndex();
    }

    public String toString() {
        return "ZipFileIndex of file:(" + zipFile + ")";
    }

    // Just in case...
    protected void finalize() {
        closeFile();
    }

    private boolean isUpToDate() {
        if (zipFile != null &&
                ((!NON_BATCH_MODE) || zipFileLastModified == zipFile.lastModified()) &&
                hasPopulatedData) {
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

        directories = Collections.<String, DirectoryEntry>emptyMap();
        allDirs = Collections.<String>emptySet();

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
        entries = ZipFileIndexEntry.EMPTY_ARRAY;
        directories = Collections.<String, DirectoryEntry>emptyMap();
        zipFileLastModified = NOT_MODIFIED;
        allDirs = Collections.<String>emptySet();
    }

    public void close() {
        lock.lock();
        try {
            writeIndex();
            closeFile();
        }
        finally {
            lock.unlock();
        }
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
     * Returns the ZipFileIndexEntry for an absolute path, if there is one.
     */
    public ZipFileIndexEntry getZipIndexEntry(String path) {
        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }
        lock.lock();
        try {
            checkIndex();
            String lookFor = "";
            int lastSepIndex = path.lastIndexOf(File.separatorChar);
            boolean noSeparator = false;
            if (lastSepIndex == -1) {
                noSeparator = true;
            }

            DirectoryEntry de = directories.get(noSeparator ? "" : path.substring(0, lastSepIndex));

            lookFor = path.substring(noSeparator ? 0 : lastSepIndex + 1);

            return de == null ? null : de.getEntry(lookFor);
        }
        catch (IOException e) {
            return null;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Returns a javac List of filenames within an absolute path in the ZipFileIndex.
     */
    public com.sun.tools.javac.util.List<String> getFiles(String path) {
        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }

        lock.lock();
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
        finally {
            lock.unlock();
        }
    }

    public List<String> getAllDirectories(String path) {

        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }

        lock.lock();
        try {
            checkIndex();
            path = path.intern();

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
        finally {
            lock.unlock();
        }
    }

    public Set<String> getAllDirectories() {
        lock.lock();
        try {
            checkIndex();
            if (allDirs == Collections.EMPTY_SET) {
                Set<String> alldirs = new HashSet<String>();
                Iterator<String> dirsIter = directories.keySet().iterator();
                while (dirsIter.hasNext()) {
                    alldirs.add(new String(dirsIter.next()));
                }

                allDirs = alldirs;
            }

            return allDirs;
        }
        catch (IOException e) {
            return Collections.<String>emptySet();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Tests if a specific path exists in the zip.  This method will return true
     * for file entries and directories.
     *
     * @param path A path within the zip.
     * @return True if the path is a file or dir, false otherwise.
     */
    public boolean contains(String path) {
        lock.lock();
        try {
            checkIndex();
            return getZipIndexEntry(path) != null;
        }
        catch (IOException e) {
            return false;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isDirectory(String path) throws IOException {
        lock.lock();
        try {
            // The top level in a zip file is always a directory.
            if (path.length() == 0) {
                lastReferenceTimeStamp = System.currentTimeMillis();
                return true;
            }

            if (File.separatorChar != '/')
                path = path.replace('/', File.separatorChar);
            checkIndex();
            return directories.get(path) != null;
        }
        finally {
            lock.unlock();
        }
    }

    public long getLastModified(String path) throws IOException {
        lock.lock();
        try {
            ZipFileIndexEntry entry = getZipIndexEntry(path);
            if (entry == null)
                throw new FileNotFoundException();
            return entry.getLastModified();
        }
        finally {
            lock.unlock();
        }
    }

    public int length(String path) throws IOException {
        lock.lock();
        try {
            ZipFileIndexEntry entry = getZipIndexEntry(path);
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
        finally {
            lock.unlock();
        }
    }

    public byte[] read(String path) throws IOException {
        lock.lock();
        try {
            ZipFileIndexEntry entry = getZipIndexEntry(path);
            if (entry == null)
                throw new FileNotFoundException(MessageFormat.format("Path not found in ZIP: {0}", path));
            return read(entry);
        }
        finally {
            lock.unlock();
        }
    }

    public byte[] read(ZipFileIndexEntry entry) throws IOException {
        lock.lock();
        try {
            openFile();
            byte[] result = readBytes(entry);
            closeFile();
            return result;
        }
        finally {
            lock.unlock();
        }
    }

    public int read(String path, byte[] buffer) throws IOException {
        lock.lock();
        try {
            ZipFileIndexEntry entry = getZipIndexEntry(path);
            if (entry == null)
                throw new FileNotFoundException();
            return read(entry, buffer);
        }
        finally {
            lock.unlock();
        }
    }

    public int read(ZipFileIndexEntry entry, byte[] buffer)
            throws IOException {
        lock.lock();
        try {
            int result = readBytes(entry, buffer);
            return result;
        }
        finally {
            lock.unlock();
        }
    }

    private byte[] readBytes(ZipFileIndexEntry entry) throws IOException {
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
    private int readBytes(ZipFileIndexEntry entry, byte[] buffer) throws IOException {
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

    private byte[] getHeader(ZipFileIndexEntry entry) throws IOException {
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
    private static Inflater inflater;
    private int inflate(byte[] src, byte[] dest) {

        // construct the inflater object or reuse an existing one
        if (inflater == null)
            inflater = new Inflater(true);

        synchronized (inflater) {
            inflater.reset();
            inflater.setInput(src);
            try {
                return inflater.inflate(dest);
            } catch (DataFormatException ex) {
                return -1;
            }
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
        private String lastDir;
        private int lastStart;
        private int lastLen;

        byte[] zipDir;
        RandomAccessFile zipRandomFile = null;
        ZipFileIndex zipFileIndex = null;

        public ZipDirectory(RandomAccessFile zipRandomFile, long start, long end, ZipFileIndex index) throws IOException {
            this.zipRandomFile = zipRandomFile;
            this.zipFileIndex = index;

            findCENRecord(start, end);
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
                    zipRandomFile.seek(start + get4ByteLittleEndian(endbuf, i + 16));
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

            entries = new ZipFileIndexEntry[entryCount];
            // Add each of the files
            if (entryCount > 0) {
                directories = new HashMap<String, DirectoryEntry>();
                ArrayList<ZipFileIndexEntry> entryList = new ArrayList<ZipFileIndexEntry>();
                int pos = 2;
                for (int i = 0; i < entryCount; i++) {
                    pos = readEntry(pos, entryList, directories);
                }

                // Add the accumulated dirs into the same list
                Iterator i = directories.keySet().iterator();
                while (i.hasNext()) {
                    ZipFileIndexEntry zipFileIndexEntry = new ZipFileIndexEntry( (String) i.next());
                    zipFileIndexEntry.isDir = true;
                    entryList.add(zipFileIndexEntry);
                }

                entries = entryList.toArray(new ZipFileIndexEntry[entryList.size()]);
                Arrays.sort(entries);
            } else {
                cleanupState();
            }
        }

        private int readEntry(int pos, List<ZipFileIndexEntry> entryList,
                Map<String, DirectoryEntry> directories) throws IOException {
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

            // Use the OS's path separator. Keep the position of the last one.
            for (int index = fileStart; index < fileEnd; index++) {
                byte nextByte = zipDir[index];
                if (nextByte == (byte)'\\' || nextByte == (byte)'/') {
                    zipDir[index] = (byte)File.separatorChar;
                    fileStart = index + 1;
                }
            }

            String directory = null;
            if (fileStart == dirStart)
                directory = "";
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

                directory = new String(zipDir, dirStart, lastLen, "UTF-8").intern();
                lastDir = directory;

                // Enter also all the parent directories
                String tempDirectory = directory;

                while (directories.get(tempDirectory) == null) {
                    directories.put(tempDirectory, new DirectoryEntry(tempDirectory, zipFileIndex));
                    int separator = tempDirectory.lastIndexOf(File.separatorChar);
                    if (separator == -1)
                        break;
                    tempDirectory = tempDirectory.substring(0, separator);
                }
            }
            else {
                directory = directory.intern();
                if (directories.get(directory) == null) {
                    directories.put(directory, new DirectoryEntry(directory, zipFileIndex));
                }
            }

            // For each dir create also a file
            if (fileStart != fileEnd) {
                ZipFileIndexEntry entry = new ZipFileIndexEntry(directory,
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
        lock.lock();
        try {
            checkIndex();
            return zipFileLastModified;
        }
        finally {
            lock.unlock();
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

        private String dirName;

        private com.sun.tools.javac.util.List<String> zipFileEntriesFiles = com.sun.tools.javac.util.List.<String>nil();
        private com.sun.tools.javac.util.List<String> zipFileEntriesDirectories = com.sun.tools.javac.util.List.<String>nil();
        private com.sun.tools.javac.util.List<ZipFileIndexEntry>  zipFileEntries = com.sun.tools.javac.util.List.<ZipFileIndexEntry>nil();

        private List<ZipFileIndexEntry> entries = new ArrayList<ZipFileIndexEntry>();

        private ZipFileIndex zipFileIndex;

        private int numEntries;

        DirectoryEntry(String dirName, ZipFileIndex index) {
        filesInited = false;
            directoriesInited = false;
            entriesInited = false;

            if (File.separatorChar == '/') {
                dirName.replace('\\', '/');
            }
            else {
                dirName.replace('/', '\\');
            }

            this.dirName = dirName.intern();
            this.zipFileIndex = index;
        }

        private com.sun.tools.javac.util.List<String> getFiles() {
            if (filesInited) {
                return zipFileEntriesFiles;
            }

            initEntries();

            for (ZipFileIndexEntry e : entries) {
                if (!e.isDir) {
                    zipFileEntriesFiles = zipFileEntriesFiles.append(e.name);
                }
            }
            filesInited = true;
            return zipFileEntriesFiles;
        }

        private com.sun.tools.javac.util.List<String> getDirectories() {
            if (directoriesInited) {
                return zipFileEntriesFiles;
            }

            initEntries();

            for (ZipFileIndexEntry e : entries) {
                if (e.isDir) {
                    zipFileEntriesDirectories = zipFileEntriesDirectories.append(e.name);
                }
            }

            directoriesInited = true;

            return zipFileEntriesDirectories;
        }

        private com.sun.tools.javac.util.List<ZipFileIndexEntry> getEntries() {
            if (zipFileEntriesInited) {
                return zipFileEntries;
            }

            initEntries();

            zipFileEntries = com.sun.tools.javac.util.List.nil();
            for (ZipFileIndexEntry zfie : entries) {
                zipFileEntries = zipFileEntries.append(zfie);
            }

            zipFileEntriesInited = true;

            return zipFileEntries;
        }

        private ZipFileIndexEntry getEntry(String rootName) {
            initEntries();
            int index = Collections.binarySearch(entries, new ZipFileIndexEntry(dirName, rootName));
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
                        new ZipFileIndexEntry(dirName, ZipFileIndex.MIN_CHAR)) - 1;
                int to = -Arrays.binarySearch(zipFileIndex.entries,
                        new ZipFileIndexEntry(dirName, MAX_CHAR)) - 1;

                boolean emptyList = false;

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

                            ZipFileIndexEntry rfie = new ZipFileIndexEntry(dirName, eName);
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
                            if (raf == null) {
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

        List<ZipFileIndexEntry> getEntriesAsCollection() {
            initEntries();

            return entries;
        }
    }

    private boolean readIndex() {
        if (triedToReadIndex || !usePreindexedCache) {
            return false;
        }

        boolean ret = false;
        lock.lock();
        try {
            triedToReadIndex = true;
            RandomAccessFile raf = null;
            try {
                File indexFileName = getIndexFile();
                raf = new RandomAccessFile(indexFileName, "r");

                long fileStamp = raf.readLong();
                if (zipFile.lastModified() != fileStamp) {
                    ret = false;
                } else {
                    directories = new HashMap<String, DirectoryEntry>();
                    int numDirs = raf.readInt();
                    for (int nDirs = 0; nDirs < numDirs; nDirs++) {
                        int dirNameBytesLen = raf.readInt();
                        byte [] dirNameBytes = new byte[dirNameBytesLen];
                        raf.read(dirNameBytes);

                        String dirNameStr = new String(dirNameBytes, "UTF-8");
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
        finally {
            lock.unlock();
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


            Iterator<String> iterDirName = directories.keySet().iterator();
            List<DirectoryEntry> directoriesToWrite = new ArrayList<DirectoryEntry>();
            Map<String, Long> offsets = new HashMap<String, Long>();
            raf.writeInt(directories.keySet().size());
            writtenSoFar += 4;

            while(iterDirName.hasNext()) {
                String dirName = iterDirName.next();
                DirectoryEntry dirEntry = directories.get(dirName);

                directoriesToWrite.add(dirEntry);

                // Write the dir name bytes
                byte [] dirNameBytes = dirName.getBytes("UTF-8");
                int dirNameBytesLen = dirNameBytes.length;
                raf.writeInt(dirNameBytesLen);
                writtenSoFar += 4;

                raf.write(dirNameBytes);
                writtenSoFar += dirNameBytesLen;

                // Write the number of files in the dir
                List dirEntries = dirEntry.getEntriesAsCollection();
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
                List<ZipFileIndexEntry> entries = de.getEntriesAsCollection();
                for (ZipFileIndexEntry zfie : entries) {
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
        lock.lock();
        try {
            return writeIndex();
        }
        finally {
            lock.unlock();
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
}
