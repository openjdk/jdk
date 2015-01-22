/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

/*
 * Tests various zipfs operations.
 *
 * @test
 * @bug 6990846 7009092 7009085 7015391 7014948 7005986 7017840 7007596
 *      7157656 8002390 7012868 7012856 8015728 8038500 8040059 8069211
 * @summary Test Zip filesystem provider
 * @run main ZipFSTester
 * @run main/othervm/java.security.policy=test.policy ZipFSTester
 */

public class ZipFSTester {

    public static void main(String[] args) throws Exception {

        // create JAR file for test, actual contents don't matter
        Path jarFile = Utils.createJarFile("tester.jar",
                "META-INF/MANIFEST.MF",
                "dir1/foo",
                "dir2/bar");

        try (FileSystem fs = newZipFileSystem(jarFile, Collections.emptyMap())) {
            test0(fs);
            test1(fs);
            test2(fs);   // more tests
        }
        testTime(jarFile);
        test8069211();
    }

    static void test0(FileSystem fs)
        throws Exception
    {
        List<String> list = new LinkedList<>();
        try (ZipFile zf = new ZipFile(fs.toString())) {
            Enumeration<? extends ZipEntry> zes = zf.entries();
            while (zes.hasMoreElements()) {
                list.add(zes.nextElement().getName());
            }
            for (String pname : list) {
                Path path = fs.getPath(pname);
                if (!Files.exists(path))
                    throw new RuntimeException("path existence check failed!");
                while ((path = path.getParent()) != null) {
                    if (!Files.exists(path))
                        throw new RuntimeException("parent existence check failed!");
                }
            }
        }
    }

    static void test1(FileSystem fs0)
        throws Exception
    {
        Random rdm = new Random();
        // clone a fs and test on it
        Path tmpfsPath = getTempPath();
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        try (FileSystem copy = newZipFileSystem(tmpfsPath, env)) {
            z2zcopy(fs0, copy, "/", 0);
        }

        try (FileSystem fs = newZipFileSystem(tmpfsPath, new HashMap<String, Object>())) {

            FileSystemProvider provider = fs.provider();
            // newFileSystem(path...) should not throw exception
            try (FileSystem fsPath = provider.newFileSystem(tmpfsPath, new HashMap<String, Object>())){}
            try (FileSystem fsUri = provider.newFileSystem(
                     new URI("jar", tmpfsPath.toUri().toString(), null),
                     new HashMap<String, Object>()))
            {
                throw new RuntimeException("newFileSystem(URI...) does not throw exception");
            } catch (FileSystemAlreadyExistsException fsaee) {}

            // prepare a src
            Path src = getTempPath();
            String tmpName = src.toString();
            OutputStream os = Files.newOutputStream(src);
            byte[] bits = new byte[12345];
            rdm.nextBytes(bits);
            os.write(bits);
            os.close();

            try {
                provider.newFileSystem(new File(System.getProperty("test.src", ".")).toPath(),
                                       new HashMap<String, Object>());
                throw new RuntimeException("newFileSystem() opens a directory as zipfs");
            } catch (UnsupportedOperationException uoe) {}

            try {
                provider.newFileSystem(src, new HashMap<String, Object>());
                throw new RuntimeException("newFileSystem() opens a non-zip file as zipfs");
            } catch (UnsupportedOperationException uoe) {}


            // copyin
            Path dst = getPathWithParents(fs, tmpName);
            Files.copy(src, dst);
            checkEqual(src, dst);

            // copy
            Path dst2 = getPathWithParents(fs, "/xyz" + rdm.nextInt(100) +
                                           "/efg" + rdm.nextInt(100) + "/foo.class");
            Files.copy(dst, dst2);
            //dst.moveTo(dst2);
            checkEqual(src, dst2);

            // delete
            Files.delete(dst);
            if (Files.exists(dst))
                throw new RuntimeException("Failed!");

            // moveout
            Path dst3 = Paths.get(tmpName + "_Tmp");
            Files.move(dst2, dst3);
            checkEqual(src, dst3);
            if (Files.exists(dst2))
                throw new RuntimeException("Failed!");

            // copyback + move
            Files.copy(dst3, dst);
            Path dst4 = getPathWithParents(fs, tmpName + "_Tmp0");
            Files.move(dst, dst4);
            checkEqual(src, dst4);

            // delete
            Files.delete(dst4);
            if (Files.exists(dst4))
                throw new RuntimeException("Failed!");
            Files.delete(dst3);
            if (Files.exists(dst3))
                throw new RuntimeException("Failed!");

            // move (existing entry)
            Path dst5 = fs.getPath("META-INF/MANIFEST.MF");
            if (Files.exists(dst5)) {
                Path dst6 = fs.getPath("META-INF/MANIFEST.MF_TMP");
                Files.move(dst5, dst6);
                walk(fs.getPath("/"));
            }

            // newInputStream on dir
            Path parent = dst2.getParent();
            try {
                Files.newInputStream(parent);
                throw new RuntimeException("Failed");
            } catch (FileSystemException e) {
                e.printStackTrace();    // expected fse
            }

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
            Files.delete(src);
        } finally {
            if (Files.exists(tmpfsPath))
                Files.delete(tmpfsPath);
        }
    }

    static void test2(FileSystem fs) throws Exception {

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
                        } catch (FileAlreadyExistsException x){
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

    // test file stamp
    static void testTime(Path src) throws Exception {
        BasicFileAttributes attrs = Files
                        .getFileAttributeView(src, BasicFileAttributeView.class)
                        .readAttributes();
        // create a new filesystem, copy this file into it
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        Path fsPath = getTempPath();
        FileSystem fs = newZipFileSystem(fsPath, env);

        System.out.println("test copy with timestamps...");
        // copyin
        Path dst = getPathWithParents(fs, "me");
        Files.copy(src, dst, COPY_ATTRIBUTES);
        checkEqual(src, dst);
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
        if (attrs.lastModifiedTime().to(TimeUnit.SECONDS) !=
            dstAttrs.lastModifiedTime().to(TimeUnit.SECONDS) ||
            attrs.lastAccessTime().to(TimeUnit.SECONDS) !=
            dstAttrs.lastAccessTime().to(TimeUnit.SECONDS) ||
            attrs.creationTime().to(TimeUnit.SECONDS) !=
            dstAttrs.creationTime().to(TimeUnit.SECONDS)) {
            throw new RuntimeException("Timestamp Copy Failed!");
        }
        Files.delete(fsPath);
    }

    static void test8069211() throws Exception {
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
            if (!Arrays.equals(Files.readAllBytes(fs.getPath("/foo")),
                               "hello".getBytes())) {
                throw new RuntimeException("entry close() failed");
            }
        } catch (Exception x) {
            throw new RuntimeException("entry close() failed", x);
        } finally {
            Files.delete(fsPath);
        }
    }

    private static FileSystem newZipFileSystem(Path path, Map<String, ?> env)
        throws Exception
    {
        return FileSystems.newFileSystem(
            new URI("jar", path.toUri().toString(), null), env, null);
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
        //System.out.printf("checking <%s> vs <%s>...%n",
        //                  src.toString(), dst.toString());

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
                    if (n == -1) {
                        System.out.printf("checking <%s> vs <%s>...%n",
                                          src.toString(), dst.toString());
                        throw new RuntimeException("CHECK FAILED!");
                    }
                    nDst += n;
                }
                while (--nSrc >= 0) {
                    if (bufSrc[nSrc] != bufDst[nSrc]) {
                        System.out.printf("checking <%s> vs <%s>...%n",
                                          src.toString(), dst.toString());
                        throw new RuntimeException("CHECK FAILED!");
                    }
                    nSrc--;
                }
            }
        }

        // channels
        try (SeekableByteChannel chSrc = Files.newByteChannel(src);
             SeekableByteChannel chDst = Files.newByteChannel(dst))
        {
            if (chSrc.size() != chDst.size()) {
                System.out.printf("src[%s].size=%d, dst[%s].size=%d%n",
                                  chSrc.toString(), chSrc.size(),
                                  chDst.toString(), chDst.size());
                throw new RuntimeException("CHECK FAILED!");
            }
            ByteBuffer bbSrc = ByteBuffer.allocate(8192);
            ByteBuffer bbDst = ByteBuffer.allocate(8192);

            int nSrc = 0;
            while ((nSrc = chSrc.read(bbSrc)) != -1) {
                int nDst = chDst.read(bbDst);
                if (nSrc != nDst) {
                    System.out.printf("checking <%s> vs <%s>...%n",
                                      src.toString(), dst.toString());
                    throw new RuntimeException("CHECK FAILED!");
                }
                while (--nSrc >= 0) {
                    if (bbSrc.get(nSrc) != bbDst.get(nSrc)) {
                        System.out.printf("checking <%s> vs <%s>...%n",
                                          src.toString(), dst.toString());
                        throw new RuntimeException("CHECK FAILED!");
                    }
                    nSrc--;
                }
                bbSrc.flip();
                bbDst.flip();
            }

            // Check if source read position is at the end
            if (chSrc.position() != chSrc.size()) {
                System.out.printf("src[%s]: size=%d, position=%d%n",
                                  chSrc.toString(), chSrc.size(), chSrc.position());
                throw new RuntimeException("CHECK FAILED!");
            }

            // Check if destination read position is at the end
            if (chDst.position() != chDst.size()) {
                System.out.printf("dst[%s]: size=%d, position=%d%n",
                                  chDst.toString(), chDst.size(), chDst.position());
                throw new RuntimeException("CHECK FAILED!");
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
            if (srcCh.position() != srcCh.size()) {
                System.out.printf("src[%s]: size=%d, position=%d%n",
                                  srcCh.toString(), srcCh.size(), srcCh.position());
                throw new RuntimeException("CHECK FAILED!");
            }

            // Check if destination write position is at the end
            if (dstCh.position() != dstCh.size()) {
                System.out.printf("dst[%s]: size=%d, position=%d%n",
                                  dstCh.toString(), dstCh.size(), dstCh.position());
                throw new RuntimeException("CHECK FAILED!");
            }
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
            if (sbc.position() != 0) {
                throw new RuntimeException("CHECK FAILED!");
            }

            bb = ByteBuffer.allocate((int)sbc.size());
            n = sbc.read(bb);
            System.out.printf("   sbc[1]: read=%d, pos=%d, size=%d%n",
                              n, sbc.position(), sbc.size());
            if (sbc.position() != sbc.size()) {
                throw new RuntimeException("CHECK FAILED!");
            }
            bb2 = ByteBuffer.allocate((int)sbc.size());
        }

        // sbc.position(pos) is not supported in current version
        // try the FileChannel
        try (SeekableByteChannel sbc = fs.provider().newFileChannel(path, read)) {
            sbc.position(N);
            System.out.printf("   sbc[2]: pos=%d, size=%d%n",
                              sbc.position(), sbc.size());
            if (sbc.position() != N) {
                throw new RuntimeException("CHECK FAILED!");
            }
            bb2.limit(100);
            n = sbc.read(bb2);
            System.out.printf("   sbc[3]: read=%d, pos=%d, size=%d%n",
                              n, sbc.position(), sbc.size());
            if (n < 0 || sbc.position() != (N + n)) {
                throw new RuntimeException("CHECK FAILED!");
            }
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
}
