/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * Tests various zipfs operations.
 *
 * @test
 * @bug 6990846 7009092 7009085 7015391 7014948 7005986 7017840 7007596
 *      7157656 8002390 7012868 7012856 8015728 8038500 8040059 8069211
 *      8131067 8034802 8210899 8273961 8271079 8299864
 * @summary Test Zip filesystem provider
 * @modules jdk.zipfs
 * @run junit ZipFSTester
 * @run junit/othervm ZipFSTester
 */
public class ZipFSTester {

    private static final Random RDM = new Random();

    // create JAR file for test, actual contents don't matter
    static Path jarFile;
    static FileSystem fs;

    @BeforeAll
    static void setup() throws Exception {
        jarFile = Utils.createJarFile("tester.jar",
                "META-INF/MANIFEST.MF",
                "dir1/foo",
                "dir2/bar",
                "dir1/dir3/fooo");
        fs = newZipFileSystem(jarFile, Map.of());
    }

    @AfterAll
    static void cleanup() throws IOException {
        fs.close();
    }

    @Test
    void test0() throws Exception {
        List<String> list = new LinkedList<>();
        try (ZipFile zf = new ZipFile(fs.toString())) {
            Enumeration<? extends ZipEntry> zes = zf.entries();
            while (zes.hasMoreElements()) {
                list.add(zes.nextElement().getName());
            }
            for (String pname : list) {
                Path path = fs.getPath(pname);
                assertTrue(Files.exists(path), "path existence check failed!");
                while ((path = path.getParent()) != null) {
                    assertTrue(Files.exists(path), "parent existence check failed!");
                }
            }
        }
    }

    @Test
    void test1() throws Exception {
        // prepare a src for testing
        Path src = getTempPath();
        String tmpName = src.toString();
        try (OutputStream os = Files.newOutputStream(src)) {
            byte[] bits = new byte[12345];
            RDM.nextBytes(bits);
            os.write(bits);
        }

        // clone a fs from fs0 and test on it
        Path tmpfsPath = getTempPath();
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        try (FileSystem copy = newZipFileSystem(tmpfsPath, env)) {
            z2zcopy(fs, copy, "/", 0);

            // copy the test jar itself in
            Files.copy(Paths.get(fs.toString()), copy.getPath("/foo.jar"));
            Path zpath = copy.getPath("/foo.jar");
            try (FileSystem zzfs = FileSystems.newFileSystem(zpath)) {
                Files.copy(src, zzfs.getPath("/srcInjarjar"));
            }
        }

        try (FileSystem fs = newZipFileSystem(tmpfsPath, new HashMap<String, Object>())) {

            FileSystemProvider provider = fs.provider();
            // newFileSystem(path...) should not throw exception
            try (FileSystem fsPath = provider.newFileSystem(tmpfsPath, new HashMap<String, Object>())){}
            assertThrows(FileSystemAlreadyExistsException.class,
                    () -> provider.newFileSystem(new URI("jar", tmpfsPath.toUri().toString(), null),
                    new HashMap<>()), "newFileSystem(URI...) does not throw exception");
            assertThrows(UnsupportedOperationException.class,
                    () -> provider.newFileSystem(new File(System.getProperty("test.src", ".")).toPath(),
                    new HashMap<>()), "newFileSystem() opens a directory as zipfs");
            assertThrows(UnsupportedOperationException.class,
                    () -> provider.newFileSystem(src, new HashMap<String, Object>()),
                    "newFileSystem() opens a non-zip file as zipfs");

            // walk
            walk(fs.getPath("/"));

            // copyin
            Path dst = getPathWithParents(fs, tmpName);
            Files.copy(src, dst);
            checkEqual(src, dst);

            // copy
            Path dst2 = getPathWithParents(fs, "/xyz" + RDM.nextInt(100) +
                                           "/efg" + RDM.nextInt(100) + "/foo.class");
            Files.copy(dst, dst2);
            //dst.moveTo(dst2);
            checkEqual(src, dst2);

            // delete
            Files.delete(dst);
            assertFalse(Files.exists(dst));

            // moveout
            Path dst3 = Paths.get(tmpName + "_Tmp");
            Files.move(dst2, dst3);
            checkEqual(src, dst3);
            assertFalse(Files.exists(dst2));

            // copyback + move
            Files.copy(dst3, dst);
            Path dst4 = getPathWithParents(fs, tmpName + "_Tmp0");
            Files.move(dst, dst4);
            checkEqual(src, dst4);

            // delete
            Files.delete(dst4);
            assertFalse(Files.exists(dst4));
            Files.delete(dst3);
            assertFalse(Files.exists(dst3));

            // move (existing entry)
            Path dst5 = fs.getPath("META-INF/MANIFEST.MF");
            if (Files.exists(dst5)) {
                Path dst6 = fs.getPath("META-INF/MANIFEST.MF_TMP");
                Files.move(dst5, dst6);
                walk(fs.getPath("/"));
            }

            // newInputStream on dir
            Path parent = dst2.getParent();
            assertThrows(FileSystemException.class, () -> Files.newInputStream(parent));

            // rmdirs
            try {
                rmdirs(parent);
            } catch (IOException x) {
                x.printStackTrace();
            }

            // newFileChannel() copy in, out and verify via fch
            fchCopy(src, dst);    // in
            checkEqual(src, dst);
            Path tmp = Paths.get(tmpName + "_Tmp");
            fchCopy(dst, tmp);   //  out
            checkEqual(src, tmp);
            Files.delete(tmp);

            // test channels
            channel(fs, dst);
            Files.delete(dst);

            // test foo.jar in jar/zipfs #8034802
            Path jpath = fs.getPath("/foo.jar");
            System.out.println("walking: " + jpath);
            try (FileSystem zzfs = FileSystems.newFileSystem(jpath)) {
                walk(zzfs.getPath("/"));
                // foojar:/srcInjarjar
                checkEqual(src, zzfs.getPath("/srcInjarjar"));

                dst = getPathWithParents(zzfs, tmpName);
                fchCopy(src, dst);
                checkEqual(src, dst);
                tmp = Paths.get(tmpName + "_Tmp");
                fchCopy(dst, tmp);   //  out
                checkEqual(src, tmp);
                Files.delete(tmp);

                channel(zzfs, dst);
                Files.delete(dst);
            }
        } finally {
            Files.deleteIfExists(tmpfsPath);
            Files.deleteIfExists(src);
        }
    }

    @Test
    void test2() throws Exception {
        Path fs1Path = getTempPath();
        Path fs2Path = getTempPath();
        Path fs3Path = getTempPath();

        // create a new filesystem, copy everything from fs
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        FileSystem fs0 = newZipFileSystem(fs1Path, env);

        final FileSystem fs2 = newZipFileSystem(fs2Path, env);
        final FileSystem fs3 = newZipFileSystem(fs3Path, env);

        System.out.println("copy src: fs -> fs0...");
        z2zcopy(fs, fs0, "/", 0);   // copy fs -> fs1
        fs0.close();                // dump to file

        System.out.println("open fs0 as fs1");
        env = new HashMap<String, Object>();
        final FileSystem fs1 = newZipFileSystem(fs1Path, env);

        System.out.println("listing...");
        final ArrayList<String> files = new ArrayList<>();
        final ArrayList<String> dirs = new ArrayList<>();
        list(fs1.getPath("/"), files, dirs);

        Thread t0 = new Thread(new Runnable() {
            public void run() {
                List<String> list = new ArrayList<>(dirs);
                Collections.shuffle(list);
                for (String path : list) {
                    try {
                        z2zcopy(fs1, fs2, path, 0);
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }

        });

        Thread t1 = new Thread(new Runnable() {
            public void run() {
                List<String> list = new ArrayList<>(dirs);
                Collections.shuffle(list);
                for (String path : list) {
                    try {
                        z2zcopy(fs1, fs2, path, 1);
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }

        });

        Thread t2 = new Thread(new Runnable() {
            public void run() {
                List<String> list = new ArrayList<>(dirs);
                Collections.shuffle(list);
                for (String path : list) {
                    try {
                        z2zcopy(fs1, fs2, path, 2);
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }

        });

        Thread t3 = new Thread(new Runnable() {
            public void run() {
                List<String> list = new ArrayList<>(files);
                Collections.shuffle(list);
                while (!list.isEmpty()) {
                    Iterator<String> itr = list.iterator();
                    while (itr.hasNext()) {
                        String path = itr.next();
                        try {
                            if (Files.exists(fs2.getPath(path))) {
                                z2zmove(fs2, fs3, path);
                                itr.remove();
                            }
                        } catch (FileAlreadyExistsException x) {
                            itr.remove();
                        } catch (Exception x) {
                            x.printStackTrace();
                        }
                    }
                }
            }

        });

        System.out.println("copying/removing...");
        t0.start(); t1.start(); t2.start(); t3.start();
        t0.join(); t1.join(); t2.join(); t3.join();

        System.out.println("closing: fs1, fs2");
        fs1.close();
        fs2.close();

        int failed = 0;
        System.out.println("checkEqual: fs vs fs3");
        for (String path : files) {
            try {
                checkEqual(fs.getPath(path), fs3.getPath(path));
            } catch (IOException x) {
                //x.printStackTrace();
                failed++;
            }
        }
        System.out.println("closing: fs3");
        fs3.close();

        System.out.println("opening: fs3 as fs4");
        FileSystem fs4 = newZipFileSystem(fs3Path, env);


        ArrayList<String> files2 = new ArrayList<>();
        ArrayList<String> dirs2 = new ArrayList<>();
        list(fs4.getPath("/"), files2, dirs2);

        System.out.println("checkEqual: fs vs fs4");
        for (String path : files2) {
            checkEqual(fs.getPath(path), fs4.getPath(path));
        }
        System.out.println("walking: fs4");
        walk(fs4.getPath("/"));
        System.out.println("closing: fs4");
        fs4.close();
        System.out.printf("failed=%d%n", failed);

        Files.delete(fs1Path);
        Files.delete(fs2Path);
        Files.delete(fs3Path);
    }

    static final int METHOD_STORED     = 0;
    static final int METHOD_DEFLATED   = 8;

    static Object[][] getEntries() {
        Object[][] entries = new Object[10 + RDM.nextInt(20)][3];
        // first entries shall test the corner case of 0 bytes of data
        entries[0][0] = "entries" + 0;
        entries[0][1] = METHOD_STORED;
        entries[0][2] = new byte[0];
        entries[1][0] = "entries" + 1;
        entries[1][1] = METHOD_DEFLATED;
        entries[1][2] = new byte[0];
        // the rest is random data
        for (int i = 2; i < entries.length; i++) {
            entries[i][0] = "entries" + i;
            entries[i][1] = RDM.nextInt(10) % 2 == 0 ?
                    METHOD_STORED : METHOD_DEFLATED;
            entries[i][2] = new byte[RDM.nextInt(8192)];
            RDM.nextBytes((byte[]) entries[i][2]);
        }
        return entries;
    }

    // check the content of read from zipfs is equal to the "bytes"
    private static void checkRead(Path path, byte[] expected) throws IOException {

        // fileAttribute
        CRC32 crc32 = new CRC32();
        crc32.update(expected);

        assertEquals((int)crc32.getValue(), ((Long)Files.getAttribute(path, "zip:crc")).intValue(),
                " getAttribute.crc <%s> failed %x vs %x ...%n".formatted(
                              path.toString(),
                              ((Long)Files.getAttribute(path, "zip:crc")).intValue(),
                              (int)crc32.getValue()));

        assertEquals(expected.length, ((Long)Files.getAttribute(path, "zip:size")).intValue(),
                " getAttribute.size <%s> failed %x vs %x ...%n".formatted(
                              path.toString(),
                              ((Long)Files.getAttribute(path, "zip:size")).intValue(),
                              expected.length));

        //streams
        try (InputStream is = Files.newInputStream(path)) {
            assertArrayEquals(expected, is.readAllBytes(), " newInputStream <%s> failed...%n".formatted(path.toString()));
        }

        // channels -- via sun.nio.ch.ChannelInputStream
        try (SeekableByteChannel sbc = Files.newByteChannel(path);
            InputStream is = Channels.newInputStream(sbc)) {

            // check all bytes match
            assertArrayEquals(expected, is.readAllBytes(),
                    " newByteChannel <%s> failed...%n".formatted(path.toString()));

            // Check if read position is at the end
            assertEquals(expected.length, sbc.position(), "pos [%s]: size=%d, position=%d%n".formatted(
                                  path.toString(), expected.length, sbc.position()));

            // Check position(x) + read() at the random/specific pos/len
            byte[] buf = new byte[1024];
            ByteBuffer bb = ByteBuffer.wrap(buf);
            for (int i = 0; i < 10; i++) {
                int pos = 0;
                int len = 0;
                if (expected.length > 0) {
                    pos = RDM.nextInt((int) sbc.size());
                    len = RDM.nextInt(Math.min(buf.length, expected.length - pos));
                }
                // System.out.printf("  --> %d, %d%n", pos, len);
                bb.position(0).limit(len);    // bb.flip().limit(len);
                int expectedReadResult = sbc.size() == 0 ? -1 : len;
                assertFalse(sbc.position(pos).position() != pos ||
                    sbc.read(bb) != expectedReadResult ||
                    !Arrays.equals(buf, 0, bb.position(), expected, pos, pos + len),
                        "read()/position() failed%n");
            }
        } catch (IOException x) {
            x.printStackTrace();
            fail("CHECK FAILED!");
        }
    }

    // test entry stream/channel reading
    @Test
    void testStreamChannel() throws Exception {
        Path zpath = getTempPath();
        try {
            var crc = new CRC32();
            Object[][] entries = getEntries();

            // [1] create zip via ZipOutputStream
            try (var os = Files.newOutputStream(zpath);
                 var zos = new ZipOutputStream(os)) {
                for (Object[] entry : entries) {
                   var ze = new ZipEntry((String)entry[0]);
                   int method = (int)entry[1];
                   byte[] bytes = (byte[])entry[2];
                   if (method == METHOD_STORED) {
                       ze.setSize(bytes.length);
                       crc.reset();
                       crc.update(bytes);
                       ze.setCrc(crc.getValue());
                   }
                   ze.setMethod(method);
                   zos.putNextEntry(ze);
                   zos.write(bytes);
                   zos.closeEntry();
                }
            }
            try (var zfs = newZipFileSystem(zpath, Map.of())) {
                for (Object[] e : entries) {
                    Path path = zfs.getPath((String)e[0]);
                    byte[] bytes = (byte[])e[2];
                    checkRead(path, bytes);
                }
            }
            Files.deleteIfExists(zpath);

            // [2] create zip via zfs.newByteChannel
            try (var zfs = newZipFileSystem(zpath, Map.of("create", "true"))) {
                for (Object[] e : entries) {
                    //  tbd: method is not used
                    try (var sbc = Files.newByteChannel(zfs.getPath((String)e[0]),
                                                        CREATE_NEW, WRITE)) {
                        sbc.write(ByteBuffer.wrap((byte[])e[2]));
                    }
                }
            }
            try (var zfs = newZipFileSystem(zpath, Map.of())) {
                for (Object[] e : entries) {
                    checkRead(zfs.getPath((String)e[0]), (byte[])e[2]);
                }
            }
            Files.deleteIfExists(zpath);

            // [3] create zip via Files.write()/newoutputStream/
            try (var zfs = newZipFileSystem(zpath, Map.of("create", "true"))) {
                for (Object[] e : entries) {
                    Files.write(zfs.getPath((String)e[0]), (byte[])e[2]);
                }
            }
            try (var zfs = newZipFileSystem(zpath, Map.of())) {
                for (Object[] e : entries) {
                    checkRead(zfs.getPath((String)e[0]), (byte[])e[2]);
                }
            }
            Files.deleteIfExists(zpath);

            // [4] create zip via zfs.newByteChannel, with "method_stored"
            try (var zfs = newZipFileSystem(zpath,
                    Map.of("create", true, "noCompression", true))) {
                for (Object[] e : entries) {
                    try (var sbc = Files.newByteChannel(zfs.getPath((String)e[0]),
                                                        CREATE_NEW, WRITE)) {
                        sbc.write(ByteBuffer.wrap((byte[])e[2]));
                    }
                }
            }
            try (var zfs = newZipFileSystem(zpath, Map.of())) {
                for (Object[] e : entries) {
                    checkRead(zfs.getPath((String)e[0]), (byte[])e[2]);
                }
            }
            Files.deleteIfExists(zpath);

        } finally {
            Files.deleteIfExists(zpath);
        }
    }

    // test file stamp
    @Test
    void testTime() throws Exception {
        BasicFileAttributes attrs = Files
                        .getFileAttributeView(jarFile, BasicFileAttributeView.class)
                        .readAttributes();
        // create a new filesystem, copy this file into it
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        Path fsPath = getTempPath();
        try (FileSystem fs = newZipFileSystem(fsPath, env)) {
            System.out.println("test copy with timestamps...");
            // copyin
            Path dst = getPathWithParents(fs, "me");
            Files.copy(jarFile, dst, COPY_ATTRIBUTES);
            checkEqual(jarFile, dst);
            System.out.println("mtime: " + attrs.lastModifiedTime());
            System.out.println("ctime: " + attrs.creationTime());
            System.out.println("atime: " + attrs.lastAccessTime());
            System.out.println(" ==============>");
            BasicFileAttributes dstAttrs = Files
                            .getFileAttributeView(dst, BasicFileAttributeView.class)
                            .readAttributes();
            System.out.println("mtime: " + dstAttrs.lastModifiedTime());
            System.out.println("ctime: " + dstAttrs.creationTime());
            System.out.println("atime: " + dstAttrs.lastAccessTime());

            // 1-second granularity
            assertFalse(attrs.lastModifiedTime().to(TimeUnit.SECONDS) !=
                dstAttrs.lastModifiedTime().to(TimeUnit.SECONDS) ||
                attrs.lastAccessTime().to(TimeUnit.SECONDS) !=
                dstAttrs.lastAccessTime().to(TimeUnit.SECONDS) ||
                attrs.creationTime().to(TimeUnit.SECONDS) !=
                dstAttrs.creationTime().to(TimeUnit.SECONDS), "Timestamp Copy Failed!");
        } finally {
            Files.delete(fsPath);
        }
    }

    @Test
    void test8069211() throws Exception {
        // create a new filesystem, copy this file into it
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        Path fsPath = getTempPath();
        try (FileSystem fs = newZipFileSystem(fsPath, env);) {
            OutputStream out = Files.newOutputStream(fs.getPath("/foo"));
            out.write("hello".getBytes());
            out.close();
            out.close();
        }
        try (FileSystem fs = newZipFileSystem(fsPath, new HashMap<String, Object>())) {
            assertArrayEquals("hello".getBytes(), Files.readAllBytes(fs.getPath("/foo")),
                    "entry close() failed");
        } catch (Exception x) {
            fail("entry close() failed", x);
        } finally {
            Files.delete(fsPath);
        }
    }

    @Test
    void test8131067() throws Exception {
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");

        // file name with space character for URI to quote it
        File tmp = File.createTempFile("test zipfs", "zip");
        tmp.delete();    // we need a clean path, no file
        Path fsPath = tmp.toPath();
        try (FileSystem fs = newZipFileSystem(fsPath, env);) {
            Files.write(fs.getPath("/foo"), "hello".getBytes());
            URI fooUri = fs.getPath("/foo").toUri();
            assertArrayEquals("hello".getBytes(), Files.readAllBytes(Paths.get(fooUri)),
                    "entry close() failed");
        } finally {
            Files.delete(fsPath);
        }
    }

    private static FileSystem newZipFileSystem(Path path, Map<String, ?> env)
        throws Exception
    {
        // Use URLDecoder (for test only) to remove the double escaped space
        // character. For the path which is not encoded by UTF-8, we need to
        // replace "+" by "%2b" manually before feeding into the decoder.
        // Otherwise, the URLDecoder would replace "+" by " ", which may
        // raise NoSuchFileException.
        //
        // eg. var path = "file:///jdk-18+9/basic.jar";
        //     URLDecoder.decode(path, "utf8") -> file:///jdk-18 9/basic.jar
        //
        // Also, we should not use URLEncoder in case of the path has been
        // encoded.
        return FileSystems.newFileSystem(
            new URI("jar", URLDecoder.decode(path.toUri().toString()
                            .replace("+", "%2b"), "utf8"), null), env, null);
    }

    private static Path getTempPath() throws IOException
    {
        File tmp = File.createTempFile("testzipfs_", "zip");
        tmp.delete();    // we need a clean path, no file
        return tmp.toPath();
    }

    private static void list(Path path, List<String> files, List<String> dirs )
        throws IOException
    {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
                for (Path child : ds)
                    list(child, files, dirs);
            }
            dirs.add(path.toString());
        } else {
            files.add(path.toString());
        }
    }

    private static void z2zcopy(FileSystem src, FileSystem dst, String path,
                                int method)
        throws IOException
    {
        Path srcPath = src.getPath(path);
        Path dstPath = dst.getPath(path);

        if (Files.isDirectory(srcPath)) {
            if (!Files.exists(dstPath)) {
                try {
                    mkdirs(dstPath);
                } catch (FileAlreadyExistsException x) {}
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(srcPath)) {
                for (Path child : ds) {
                    z2zcopy(src, dst,
                           path + (path.endsWith("/")?"":"/") + child.getFileName(),
                           method);
                }
            }
        } else {
            try {
                if (Files.exists(dstPath))
                    return;
                switch (method) {
                case 0:
                    Files.copy(srcPath, dstPath);
                    break;
                case 1:
                    chCopy(srcPath, dstPath);
                    break;
                case 2:
                    //fchCopy(srcPath, dstPath);
                    streamCopy(srcPath, dstPath);
                    break;
                }
            } catch (FileAlreadyExistsException x) {}
        }
    }

    private static void z2zmove(FileSystem src, FileSystem dst, String path)
        throws IOException
    {
        Path srcPath = src.getPath(path);
        Path dstPath = dst.getPath(path);

        if (Files.isDirectory(srcPath)) {
            if (!Files.exists(dstPath))
                mkdirs(dstPath);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(srcPath)) {
                for (Path child : ds) {
                    z2zmove(src, dst,
                            path + (path.endsWith("/")?"":"/") + child.getFileName());
                }
            }
        } else {
            //System.out.println("moving..." + path);
            Path parent = dstPath.getParent();
            if (parent != null && Files.notExists(parent))
                mkdirs(parent);
            Files.move(srcPath, dstPath);
        }
    }

    private static void walk(Path path) throws IOException
    {
        Files.walkFileTree(
            path,
            new SimpleFileVisitor<Path>() {
                private int indent = 0;
                private void indent() {
                    int n = 0;
                    while (n++ < indent)
                        System.out.printf(" ");
                }

                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs)
                {
                    indent();
                    System.out.printf("%s%n", file.getFileName().toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                                                         BasicFileAttributes attrs)
                {
                    indent();
                    System.out.printf("[%s]%n", dir.toString());
                    indent += 2;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir,
                                                          IOException ioe)
                    throws IOException
                {
                    indent -= 2;
                    return FileVisitResult.CONTINUE;
                }
        });
    }

    private static void mkdirs(Path path) throws IOException {
        if (Files.exists(path))
            return;
        path = path.toAbsolutePath();
        Path parent = path.getParent();
        if (parent != null) {
            if (Files.notExists(parent))
                mkdirs(parent);
        }
        Files.createDirectory(path);
    }

    private static void rmdirs(Path path) throws IOException {
        while (path != null && path.getNameCount() != 0) {
            Files.delete(path);
            path = path.getParent();
        }
    }

    // check the content of two paths are equal
    private static void checkEqual(Path src, Path dst) throws IOException
    {
        System.out.printf("checking <%s> vs <%s>...%n",
                          src.toString(), dst.toString());

        //streams
        byte[] bufSrc = new byte[8192];
        byte[] bufDst = new byte[8192];
        try (InputStream isSrc = Files.newInputStream(src);
             InputStream isDst = Files.newInputStream(dst))
        {
            int nSrc = 0;
            while ((nSrc = isSrc.read(bufSrc)) != -1) {
                int nDst = 0;
                while (nDst < nSrc) {
                    int n = isDst.read(bufDst, nDst, nSrc - nDst);
                    assertNotEquals(-1, n, "checking <%s> vs <%s>...%n".formatted(
                                          src.toString(), dst.toString()));
                    nDst += n;
                }
                while (--nSrc >= 0) {
                    assertEquals(bufSrc[nSrc], bufDst[nSrc], "checking <%s> vs <%s>...%n".formatted(
                                          src.toString(), dst.toString()));
                    nSrc--;
                }
            }
        }

        // channels
        try (SeekableByteChannel chSrc = Files.newByteChannel(src);
             SeekableByteChannel chDst = Files.newByteChannel(dst))
        {
            assertEquals(chSrc.size(), chDst.size(), "src[%s].size=%d, dst[%s].size=%d%n".formatted(
                                  chSrc.toString(), chSrc.size(),
                                  chDst.toString(), chDst.size()));
            ByteBuffer bbSrc = ByteBuffer.allocate(8192);
            ByteBuffer bbDst = ByteBuffer.allocate(8192);

            int nSrc = 0;
            while ((nSrc = chSrc.read(bbSrc)) != -1) {
                int nDst = chDst.read(bbDst);
                assertEquals(nSrc, nDst,
                        "checking <%s> vs <%s>...%n".formatted(src.toString(), dst.toString()));
                while (--nSrc >= 0) {
                    assertEquals(bbSrc.get(nSrc), bbDst.get(nSrc),
                            "checking <%s> vs <%s>...%n".formatted(src.toString(), dst.toString()));
                    nSrc--;
                }
                bbSrc.flip();
                bbDst.flip();
            }

            // Check if source read position is at the end
            assertEquals(chSrc.position(), chSrc.size(), "src[%s]: size=%d, position=%d%n".formatted(
                                  chSrc.toString(), chSrc.size(), chSrc.position()));

            // Check if destination read position is at the end
            assertEquals(chDst.position(), chDst.size(), "dst[%s]: size=%d, position=%d%n".formatted(
                                  chDst.toString(), chDst.size(), chDst.position()));

            // Check position(x) + read() at the specific pos/len
            for (int i = 0; i < 10; i++) {
                int pos = RDM.nextInt((int) chSrc.size());
                int limit = RDM.nextInt(1024);
                if (chSrc.position(pos).position() != chDst.position(pos).position()) {
                    System.out.printf("dst/src.position(pos failed%n");
                }
                bbSrc.clear().limit(limit);
                bbDst.clear().limit(limit);
                if (chSrc.read(bbSrc) != chDst.read(bbDst) ||
                    !bbSrc.flip().equals(bbDst.flip())) {
                    System.out.printf("dst/src.read() failed%n");
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private static void fchCopy(Path src, Path dst) throws IOException
    {
        Set<OpenOption> read = new HashSet<>();
        read.add(READ);
        Set<OpenOption> openwrite = new HashSet<>();
        openwrite.add(CREATE_NEW);
        openwrite.add(WRITE);

        try (FileChannel srcFc = src.getFileSystem()
                                    .provider()
                                    .newFileChannel(src, read);
             FileChannel dstFc = dst.getFileSystem()
                                    .provider()
                                    .newFileChannel(dst, openwrite))
        {
            ByteBuffer bb = ByteBuffer.allocate(8192);
            while (srcFc.read(bb) >= 0) {
                bb.flip();
                dstFc.write(bb);
                bb.clear();
            }
        }
    }

    private static void chCopy(Path src, Path dst) throws IOException
    {
        Set<OpenOption> read = new HashSet<>();
        read.add(READ);
        Set<OpenOption> openwrite = new HashSet<>();
        openwrite.add(CREATE_NEW);
        openwrite.add(WRITE);

        try (SeekableByteChannel srcCh = Files.newByteChannel(src, read);
             SeekableByteChannel dstCh = Files.newByteChannel(dst, openwrite))
        {

            ByteBuffer bb = ByteBuffer.allocate(8192);
            while (srcCh.read(bb) >= 0) {
                bb.flip();
                dstCh.write(bb);
                bb.clear();
            }

            // Check if source read position is at the end
            assertEquals(srcCh.position(), srcCh.size(), "src[%s]: size=%d, position=%d%n".formatted(
                                  srcCh.toString(), srcCh.size(), srcCh.position()));

            // Check if destination write position is at the end
            assertEquals(dstCh.position(), dstCh.size(), "dst[%s]: size=%d, position=%d%n".formatted(
                                  dstCh.toString(), dstCh.size(), dstCh.position()));
        }
    }

    private static void streamCopy(Path src, Path dst) throws IOException
    {
        byte[] buf = new byte[8192];
        try (InputStream isSrc = Files.newInputStream(src);
             OutputStream osDst = Files.newOutputStream(dst))
        {
            int n = 0;
            while ((n = isSrc.read(buf)) != -1) {
                osDst.write(buf, 0, n);
            }
        }
    }

    static void channel(FileSystem fs, Path path)
        throws Exception
    {
        System.out.println("test ByteChannel...");
        Set<OpenOption> read = new HashSet<>();
        read.add(READ);
        int n = 0;
        ByteBuffer bb = null;
        ByteBuffer bb2 = null;
        int N = 120;

        try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
            System.out.printf("   sbc[0]: pos=%d, size=%d%n", sbc.position(), sbc.size());
            assertEquals(0, sbc.position());

            bb = ByteBuffer.allocate((int)sbc.size());
            n = sbc.read(bb);
            System.out.printf("   sbc[1]: read=%d, pos=%d, size=%d%n",
                              n, sbc.position(), sbc.size());
            assertEquals(sbc.position(), sbc.size());
            bb2 = ByteBuffer.allocate((int)sbc.size());
        }

        // sbc.position(pos) is not supported in current version
        // try the FileChannel
        try (SeekableByteChannel sbc = fs.provider().newFileChannel(path, read)) {
            sbc.position(N);
            System.out.printf("   sbc[2]: pos=%d, size=%d%n",
                              sbc.position(), sbc.size());
            assertEquals(N, sbc.position());
            bb2.limit(100);
            n = sbc.read(bb2);
            System.out.printf("   sbc[3]: read=%d, pos=%d, size=%d%n",
                              n, sbc.position(), sbc.size());
            assertFalse(n < 0);
            assertFalse(sbc.position() != (N + n));
            System.out.printf("   sbc[4]: bb[%d]=%d, bb1[0]=%d%n",
                              N, bb.get(N) & 0xff, bb2.get(0) & 0xff);
        }
    }

    // create parents if does not exist
    static Path getPathWithParents(FileSystem fs, String name)
        throws Exception
    {
        Path path = fs.getPath(name);
        Path parent = path.getParent();
        if (parent != null && Files.notExists(parent))
            mkdirs(parent);
        return path;
    }

    /**
     * Tests if certain methods throw a NullPointerException if invoked with null
     * as specified in java.nio.file.package-info.java
     *
     * @see 8299864
     */
    @Test
    void testFileStoreNullArgs() {
        // file system containing at least one ZipFileStore
        FileStore store = fs.getFileStores().iterator().next();

        // Make sure we are testing the right thing
        assertEquals("jdk.nio.zipfs.ZipFileStore", store.getClass().getName());

        assertThrows(NullPointerException.class, () -> store.supportsFileAttributeView((String) null));
        assertThrows(NullPointerException.class, () -> store.supportsFileAttributeView((Class<? extends FileAttributeView>) null));
        assertThrows(NullPointerException.class, () -> store.getAttribute(null));
        assertThrows(NullPointerException.class, () -> store.getFileStoreAttributeView(null));
    }
}
