/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/* @test
 * @bug 8317678
 * @modules java.base/java.util.zip:open
 * @summary Fix up hashCode() for ZipFile.Source.Key
 * @run junit/othervm ZipSourceCache
 */

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

public class ZipSourceCache {

    private static final String ZIPFILE_NAME =
            System.currentTimeMillis() + "-bug8317678.zip";
    private static final String ZIPENTRY_NAME = "random.txt";
    private static final String INVALID_LOC_EXCEPTION =
            "ZipFile invalid LOC header (bad signature)";
    private static final boolean DEBUG = false;

    private static File relativeFile = new File(ZIPFILE_NAME);
    private static File absoluteFile = new File(ZIPFILE_NAME).getAbsoluteFile();
    private static boolean hasfileKeySupport;

    @BeforeAll
    public static void setup() throws Exception {
        createZipFile("test1");
        var attrs = Files.readAttributes(relativeFile.toPath(), BasicFileAttributes.class);
        hasfileKeySupport = (attrs.fileKey() != null);
    }

    @AfterAll
    public static void cleanup() throws IOException {
        Files.deleteIfExists(Path.of(ZIPFILE_NAME));
    }

    /*
     * Monitor the internal "files" HashMap to ensure that we only
     * create one <Key, Source> mapping per unique zip file.
     *
     * This test also ensures that a new <Key, Source> mapping is created
     * if an update to an existing zip file is detected.
     */
    @Test
    public void testKeySourceMapping() throws Exception {
        ZipFile absoluteZipFile;
        HashMap internalMap;
        int numSources;
        try (ZipFile zipFile = new ZipFile(relativeFile)) {
            Class source = Class.forName("java.util.zip.ZipFile$Source");
            Field filesMap = source.getDeclaredField("files");
            filesMap.setAccessible(true);
            internalMap = (HashMap) filesMap.get(zipFile);
            numSources = internalMap.size();
            // opening of same zip file shouldn't cause new Source
            // to be constructed on filesystems which support fileKey()
            absoluteZipFile = new ZipFile(absoluteFile);
            if (hasfileKeySupport) {
                assertEquals(numSources, internalMap.size());
            } else {
                assertEquals(++numSources, internalMap.size());
            }

            // update the zip file, should expect a new Source Object
            // ignore this part of test if file can't be updated (can't overwrite)
            if (createZipFile("differentContent")) {
                ZipFile z = new ZipFile(relativeFile);
                // update of file should trigger new <Key, Source> mapping
                assertEquals(++numSources, internalMap.size());
                // new Source created, CEN structure should map fine
                readZipFileContents(z);
                // the old Source in use for old file, should no longer map correctly
                IOException ioe = assertThrows(IOException.class, () -> readZipFileContents(absoluteZipFile));
                assertEquals(INVALID_LOC_EXCEPTION, ioe.getMessage());
                z.close();
                assertEquals(--numSources, internalMap.size());
            }
        }
        // with fileKey() support, the close() call shouldn't remove the
        // Source entry just yet since we still have one reference to the file
        if (hasfileKeySupport) {
            assertEquals(numSources, internalMap.size());
        } else {
            assertEquals(--numSources, internalMap.size());
        }
        if (absoluteZipFile != null) {
            absoluteZipFile.close();
        }
        // now, the Source entry key should be removed
        assertEquals(--numSources, internalMap.size());
    }

    private static void readZipFileContents(ZipFile zf) throws IOException {
        var e = zf.entries();
        while (e.hasMoreElements()) {
            InputStream is = zf.getInputStream(e.nextElement());
            String s = new String(is.readAllBytes());
            if (DEBUG) System.err.println(s);
        }
    }

    private static boolean createZipFile(String content) {
        CRC32 crc32 = new CRC32();
        long t = System.currentTimeMillis();
        // let's have at least 2 entries created to ensure
        // that a bad zip structure is detected if file is updated
        int numEntries = new Random().nextInt(10) + 2;
        File zipFile = new File(ZIPFILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i < numEntries; i++) {
                ZipEntry e = new ZipEntry(ZIPENTRY_NAME + i);
                e.setMethod(ZipEntry.STORED);
                byte[] toWrite = content.repeat(i+1).getBytes();
                e.setTime(t);
                e.setSize(toWrite.length);
                crc32.reset();
                crc32.update(toWrite);
                e.setCrc(crc32.getValue());
                zos.putNextEntry(e);
                zos.write(toWrite);
            }
        } catch (IOException e) {
            // some systems mightn't allow file to be updated while open
            System.err.println("error updating file. " + e);
            return false;
        }
        return true;
    }
}
