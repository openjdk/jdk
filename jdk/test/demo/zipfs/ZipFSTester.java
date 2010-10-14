/*
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.*;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

/*
 * Tests various zipfs operations.
 */

public class ZipFSTester {

    public static void main(String[] args) throws Throwable {
        FileSystem fs = null;
        try {
            fs = newZipFileSystem(Paths.get(args[0]), new HashMap<String, Object>());
            test(fs);
            test2(fs);   // more tests
        } finally {
            if (fs != null)
                fs.close();
        }
    }

    static void test(FileSystem fs)
        throws Exception
    {
        Random rdm = new Random();

        // clone a fs and test on it
        Path tmpfsPath = getTempPath();
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("createNew", true);
        FileSystem fs0 = newZipFileSystem(tmpfsPath, env);
        z2zcopy(fs, fs0, "/", 0);
        fs0.close();                // sync to file

        fs = newZipFileSystem(tmpfsPath, new HashMap<String, Object>());

        try {
            // prepare a src
            Path src = getTempPath();
            String tmpName = src.toString();
            OutputStream os = src.newOutputStream();
            byte[] bits = new byte[12345];
            rdm.nextBytes(bits);
            os.write(bits);
            os.close();

            // copyin
            Path dst = getPathWithParents(fs, tmpName);
            src.copyTo(dst);
            checkEqual(src, dst);

            // copy
            Path dst2 = getPathWithParents(fs, "/xyz" + rdm.nextInt(100) +
                                           "/efg" + rdm.nextInt(100) + "/foo.class");
            dst.copyTo(dst2);
            //dst.moveTo(dst2);
            checkEqual(src, dst2);

            // delete
            dst.delete();
            if (dst.exists())
                throw new RuntimeException("Failed!");

            // moveout
            Path dst3 = Paths.get(tmpName + "_Tmp");
            dst2.moveTo(dst3);
            checkEqual(src, dst3);

            // delete
            if (dst2.exists())
                throw new RuntimeException("Failed!");
            dst3.delete();
            if (dst3.exists())
                throw new RuntimeException("Failed!");

            // newInputStream on dir
            Path parent = dst2.getParent();
            try {
                parent.newInputStream();
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
            tmp.delete();

            // test channels
            channel(fs, dst);
            dst.delete();
            src.delete();
        } finally {
            if (fs != null)
                fs.close();
            if (tmpfsPath.exists())
                tmpfsPath.delete();
        }
    }

    static void test2(FileSystem fs) throws Exception {

        Path fs1Path = getTempPath();
        Path fs2Path = getTempPath();
        Path fs3Path = getTempPath();

        if (fs1Path.exists())
            fs1Path.delete();
        if (fs2Path.exists())
            fs2Path.delete();
        if (fs3Path.exists())
            fs3Path.delete();

        // create a new filesystem, copy everything from fs
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("createNew", true);
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
                            if (fs2.getPath(path).exists()) {
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

        fs1Path.delete();
        fs2Path.delete();
        fs3Path.delete();
    }

    private static FileSystem newZipFileSystem(Path path, Map<String, ?> env)
        throws IOException
    {
        return FileSystems.newFileSystem(
                   URI.create("zip" +
                               path.toUri().toString().substring(4)),
                   env,
                   null);
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
        if (Attributes.readBasicFileAttributes(path).isDirectory()) {
            DirectoryStream<Path> ds = path.newDirectoryStream();
            for (Path child : ds)
                list(child, files, dirs);
            ds.close();
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

        if (Boolean.TRUE.equals(srcPath.getAttribute("isDirectory"))) {
            if (!dstPath.exists()) {
                try {
                    mkdirs(dstPath);
                } catch (FileAlreadyExistsException x) {}
            }
            DirectoryStream<Path> ds = srcPath.newDirectoryStream();
            for (Path child : ds) {
                z2zcopy(src, dst,
                        path + (path.endsWith("/")?"":"/") + child.getName(),
                        method);
            }
            ds.close();
        } else {
            try {
                if (dstPath.exists())
                    return;
                switch (method) {
                case 0:
                    srcPath.copyTo(dstPath);
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

        if (Boolean.TRUE.equals(srcPath.getAttribute("isDirectory"))) {
            if (!dstPath.exists())
                mkdirs(dstPath);
            DirectoryStream<Path> ds = srcPath.newDirectoryStream();
            for (Path child : ds) {
                z2zmove(src, dst,
                        path + (path.endsWith("/")?"":"/") + child.getName());
            }
            ds.close();
        } else {
            //System.out.println("moving..." + path);
            Path parent = dstPath.getParent();
            if (parent != null && parent.notExists())
                mkdirs(parent);
            srcPath.moveTo(dstPath);
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
                    System.out.printf("%s%n", file.getName().toString());
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
        path = path.toAbsolutePath();
        Path parent = path.getParent();
        if (parent != null) {
            if (parent.notExists())
                mkdirs(parent);
        }
        path.createDirectory();
    }

    private static void rmdirs(Path path) throws IOException {
        while (path != null && path.getNameCount() != 0) {
            path.delete();
            path = path.getParent();
        }
    }

    // check the content of two paths are equal
    private static void checkEqual(Path src, Path dst) throws IOException
    {
        //System.out.printf("checking <%s> vs <%s>...%n",
        //                  src.toString(), dst.toString());

        //streams
        InputStream isSrc = src.newInputStream();
        InputStream isDst = dst.newInputStream();
        byte[] bufSrc = new byte[8192];
        byte[] bufDst = new byte[8192];

        try {
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
        } finally {
            isSrc.close();
            isDst.close();
        }

        // channels
        SeekableByteChannel chSrc = src.newByteChannel();
        SeekableByteChannel chDst = dst.newByteChannel();
        if (chSrc.size() != chDst.size()) {
            System.out.printf("src[%s].size=%d, dst[%s].size=%d%n",
                              chSrc.toString(), chSrc.size(),
                              chDst.toString(), chDst.size());
            throw new RuntimeException("CHECK FAILED!");
        }
        ByteBuffer bbSrc = ByteBuffer.allocate(8192);
        ByteBuffer bbDst = ByteBuffer.allocate(8192);

        try {
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
        } catch (IOException x) {
            x.printStackTrace();
        } finally {
            chSrc.close();
            chDst.close();
        }
    }

    private static void fchCopy(Path src, Path dst) throws IOException
    {
        Set<OpenOption> read = new HashSet<>();
        read.add(READ);
        Set<OpenOption> openwrite = new HashSet<>();
        openwrite.add(CREATE_NEW);
        openwrite.add(WRITE);

        FileChannel srcFc = src.getFileSystem()
                               .provider()
                               .newFileChannel(src, read);
        FileChannel dstFc = dst.getFileSystem()
                               .provider()
                               .newFileChannel(dst, openwrite);

        try {
            ByteBuffer bb = ByteBuffer.allocate(8192);
            while (srcFc.read(bb) >= 0) {
                bb.flip();
                dstFc.write(bb);
                bb.clear();
            }
        } finally {
            srcFc.close();
            dstFc.close();
        }
    }

    private static void chCopy(Path src, Path dst) throws IOException
    {
        Set<OpenOption> read = new HashSet<>();
        read.add(READ);
        Set<OpenOption> openwrite = new HashSet<>();
        openwrite.add(CREATE_NEW);
        openwrite.add(WRITE);

        SeekableByteChannel srcCh = src.newByteChannel(read);
        SeekableByteChannel dstCh = dst.newByteChannel(openwrite);

        try {
            ByteBuffer bb = ByteBuffer.allocate(8192);
            while (srcCh.read(bb) >= 0) {
                bb.flip();
                dstCh.write(bb);
                bb.clear();
            }
        } finally {
            srcCh.close();
            dstCh.close();
        }
    }

    private static void streamCopy(Path src, Path dst) throws IOException
    {
        InputStream isSrc = src.newInputStream();
        OutputStream osDst = dst.newOutputStream();
        byte[] buf = new byte[8192];
        try {
            int n = 0;
            while ((n = isSrc.read(buf)) != -1) {
                osDst.write(buf, 0, n);
            }
        } finally {
            isSrc.close();
            osDst.close();
        }
    }

    static void channel(FileSystem fs, Path path)
        throws Exception
    {
        System.out.println("test ByteChannel...");
        SeekableByteChannel sbc = path.newByteChannel();
        Set<OpenOption> read = new HashSet<>();
        read.add(READ);
        System.out.printf("   sbc[0]: pos=%d, size=%d%n", sbc.position(), sbc.size());
        ByteBuffer bb = ByteBuffer.allocate((int)sbc.size());
        int n = sbc.read(bb);
        System.out.printf("   sbc[1]: read=%d, pos=%d, size=%d%n",
                          n, sbc.position(), sbc.size());
        ByteBuffer bb2 = ByteBuffer.allocate((int)sbc.size());
        int N = 120;
        sbc.close();

        // sbc.position(pos) is not supported in current version
        // try the FileChannel
        sbc = fs.provider().newFileChannel(path, read);
        sbc.position(N);
        System.out.printf("   sbc[2]: pos=%d, size=%d%n",
                          sbc.position(), sbc.size());
        bb2.limit(100);
        n = sbc.read(bb2);
        System.out.printf("   sbc[3]: read=%d, pos=%d, size=%d%n",
                          n, sbc.position(), sbc.size());
        System.out.printf("   sbc[4]: bb[%d]=%d, bb1[0]=%d%n",
                          N, bb.get(N) & 0xff, bb2.get(0) & 0xff);
        sbc.close();
    }

    // create parents if does not exist
    static Path getPathWithParents(FileSystem fs, String name)
        throws Exception
    {
        Path path = fs.getPath(name);
        Path parent = path.getParent();
        if (parent != null && parent.notExists())
            mkdirs(parent);
        return path;
    }
}
