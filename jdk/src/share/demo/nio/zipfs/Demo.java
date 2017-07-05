/*
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

/*
 * ZipFileSystem usage demo
 *
 * java [-cp .../zipfs.jar:./] Demo action ZipfileName [...]
 *
 * To deploy the provider, either copy the zipfs.jar into JDK/JRE
 * extensions directory or add
 *      <JDK_HOME>/demo/nio/ZipFileSystem/zipfs.jar
 * into your class path as showed above.
 *
 * @author Xueming Shen
 */

public class Demo {

    static enum Action {
        rename,          // <java Demo rename zipfile src dst>
                         // rename entry src to dst inside zipfile

        movein,          // <java Demo movein zipfile src dst>
                         // move an external src file into zipfile
                         // as entry dst

        moveout,         // <java Demo moveout zipfile src dst>
                         // move a zipfile entry src out to dst

        copy,            // <java Demo copy zipfile src dst>
                         // copy entry src to dst inside zipfile

        copyin,          // <java Demo copyin zipfile src dst>
                         // copy an external src file into zipfile
                         // as entry dst

        copyout,         // <java Demo copyout zipfile src dst>
                         // copy zipfile entry src" out to file dst

        zzmove,          // <java Demo zzmove zfsrc zfdst path>
                         // move entry path/dir from zfsrc to zfdst

        zzcopy,          // <java Demo zzcopy zfsrc zfdst path>
                         // copy path from zipfile zfsrc to zipfile
                         // zfdst

        attrs,           // <java Demo attrs zipfile path>
                         // printout the attributes of entry path

        attrsspace,      // <java Demo attrsspace zipfile path>
                         // printout the storespace attrs of entry path

        setmtime,        // <java Demo setmtime zipfile "MM/dd/yy-HH:mm:ss" path...>
                         // set the lastModifiedTime of entry path

        lsdir,           // <java Demo lsdir zipfile dir>
                         // list dir's direct child files/dirs

        mkdir,           // <java Demo mkdir zipfile dir>

        mkdirs,          // <java Demo mkdirs zipfile dir>

        rmdirs,          // <java Demo rmdirs zipfile dir>

        list,            // <java Demo list zipfile [dir]>
                         // recursively list all entries of dir
                         // via DirectoryStream

        tlist,           // <java Demo tlist zipfile [dir]>
                         // list with buildDirTree=true

        vlist,           // <java Demo vlist zipfile [dir]>
                         // recursively verbose list all entries of
                         // dir via DirectoryStream

        walk,            // <java Demo walk zipfile [dir]>
                         // recursively walk all entries of dir
                         // via Files.walkFileTree

        twalk,           // <java Demo twalk zipfile [dir]>
                         // walk with buildDirTree=true

        extract,         // <java Demo extract zipfile file [...]>

        update,          // <java Demo extract zipfile file [...]>

        delete,          // <java Demo delete zipfile file [...]>

        add,             // <java Demo add zipfile file [...]>

        create,          // <java Demo create zipfile file [...]>
                         // create a new zipfile if it doesn't exit
                         // and then add the file(s) into it.

        attrs2,          // <java Demo attrs2 zipfile file [...]>
                         // test different ways to print attrs
    }

    public static void main(String[] args) throws Throwable {

        Action action = Action.valueOf(args[0]);;
        Map<String, Object> env = env = new HashMap<String, Object>();
        if (action == Action.create)
            env.put("createNew", true);
        if (action == Action.tlist || action == Action.twalk)
            env.put("buildDirTree", true);

        FileSystem fs = FileSystems.newFileSystem(
                            URI.create("zip" + Paths.get(args[1]).toUri().toString().substring(4)),
                            env,
                            null);
        try {
            FileSystem fs2;
            Path path, src, dst;
            boolean isRename = false;
            switch (action) {
            case rename:
                src = fs.getPath(args[2]);
                dst = fs.getPath(args[3]);
                src.moveTo(dst);
                break;
            case moveout:
                src = fs.getPath(args[2]);
                dst = Paths.get(args[3]);
                src.moveTo(dst);
                break;
            case movein:
                src = Paths.get(args[2]);
                dst = fs.getPath(args[3]);
                src.moveTo(dst);
                break;
            case copy:
                src = fs.getPath(args[2]);
                dst = fs.getPath(args[3]);
                src.copyTo(dst);
                break;
            case copyout:
                src = fs.getPath(args[2]);
                dst = Paths.get(args[3]);
                src.copyTo(dst);
                break;
            case copyin:
                src = Paths.get(args[2]);
                dst = fs.getPath(args[3]);
                src.copyTo(dst);
                break;
            case zzmove:
                fs2 = FileSystems.newFileSystem(
                    URI.create("zip" + Paths.get(args[2]).toUri().toString().substring(4)),
                    env,
                    null);
                //sf1.getPath(args[3]).moveTo(fs2.getPath(args[3]));
                z2zmove(fs, fs2, args[3]);
                fs2.close();
                break;
            case zzcopy:
                fs2 = FileSystems.newFileSystem(
                    URI.create("zip" + Paths.get(args[2]).toUri().toString().substring(4)),
                    env,
                    null);
                //sf1.getPath(args[3]).copyTo(fs2.getPath(args[3]));
                z2zcopy(fs, fs2, args[3]);
                fs2.close();
                break;
            case attrs:
                for (int i = 2; i < args.length; i++) {
                    path = fs.getPath(args[i]);
                    System.out.println(
                        Attributes.readBasicFileAttributes(path).toString());
                }
                break;
            case setmtime:
                DateFormat df = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss");
                Date newDatetime = df.parse(args[2]);
                for (int i = 3; i < args.length; i++) {
                    path = fs.getPath(args[i]);
                    path.setAttribute("lastModifiedTime",
                                      FileTime.fromMillis(newDatetime.getTime()));
                    System.out.println(
                        Attributes.readBasicFileAttributes(path).toString());
                }
                break;
            case attrsspace:
                path = fs.getPath("/");
                FileStore fstore = path.getFileStore();
                //System.out.println(fstore.getFileStoreAttributeView(FileStoreSpaceAttributeView.class)
                //                         .readAttributes());
                // or
                System.out.printf("filestore[%s]%n", fstore.name());
                System.out.printf("    totalSpace: %d%n",
                                  (Long)fstore.getAttribute("space:totalSpace"));
                System.out.printf("   usableSpace: %d%n",
                                  (Long)fstore.getAttribute("space:usableSpace"));
                System.out.printf("  unallocSpace: %d%n",
                                  (Long)fstore.getAttribute("space:unallocatedSpace"));
                break;
            case list:
            case tlist:
                if (args.length < 3)
                    list(fs.getPath("/"), false);
                else
                    list(fs.getPath(args[2]), false);
                break;
            case vlist:
                if (args.length < 3)
                    list(fs.getPath("/"), true);
                else
                    list(fs.getPath(args[2]), true);
                break;
            case twalk:
            case walk:
                walk(fs.getPath((args.length > 2)? args[2] : "/"));
                break;
            case extract:
                if (args.length == 2) {
                     extract(fs, "/");
                } else {
                    for (int i = 2; i < args.length; i++) {
                        extract(fs, args[i]);
                    }
                }
                break;
            case delete:
                for (int i = 2; i < args.length; i++)
                    fs.getPath(args[i]).delete();
                break;
            case create:
            case add:
            case update:
                for (int i = 2; i < args.length; i++) {
                    update(fs, args[i]);
                }
                break;
            case lsdir:
                path = fs.getPath(args[2]);
                final String fStr = (args.length > 3)?args[3]:"";
                DirectoryStream<Path> ds = path.newDirectoryStream(
                    new DirectoryStream.Filter<Path>() {
                        public boolean accept(Path path) {
                            return path.toString().contains(fStr);
                        }
                    });
                for (Path p : ds)
                    System.out.println(p);
                break;
            case mkdir:
                fs.getPath(args[2]).createDirectory();
                break;
            case mkdirs:
                mkdirs(fs.getPath(args[2]));
                break;
            case attrs2:
                for (int i = 2; i < args.length; i++) {
                    path = fs.getPath(args[i]);
                    System.out.println("-------(1)---------");
                    System.out.println(
                        Attributes.readBasicFileAttributes(path).toString());
                    System.out.println("-------(2)---------");
                    Map<String, ?> map = path.readAttributes("zip:*");
                    for (Map.Entry<String, ?> e : map.entrySet()) {
                        System.out.printf("    %s : %s%n", e.getKey(), e.getValue());
                    }
                    System.out.println("-------(3)---------");
                    map = path.readAttributes("size,lastModifiedTime,isDirectory");
                    for (Map.Entry<String, ?> e : map.entrySet()) {
                        System.out.printf("    %s : %s%n", e.getKey(), e.getValue());
                    }
                }
                break;
            }
        } catch (Exception x) {
            x.printStackTrace();
        } finally {
            if (fs != null)
                fs.close();
        }
    }

    private static byte[] getBytes(String name) {
        return name.getBytes();
    }

    private static String getString(byte[] name) {
        return new String(name);
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
                {
                    indent -= 2;
                    return FileVisitResult.CONTINUE;
                }
        });
    }

    private static void update(FileSystem fs, String path) throws Throwable{
        Path src = FileSystems.getDefault().getPath(path);
        if (Boolean.TRUE.equals(src.getAttribute("isDirectory"))) {
            DirectoryStream<Path> ds = src.newDirectoryStream();
            for (Path child : ds)
                update(fs, child.toString());
            ds.close();
        } else {
            Path dst = fs.getPath(path);
            Path parent = dst.getParent();
            if (parent != null && parent.notExists())
                mkdirs(parent);
            src.copyTo(dst, REPLACE_EXISTING);
        }
    }

    private static void extract(FileSystem fs, String path) throws Throwable{
        Path src = fs.getPath(path);
        if (Boolean.TRUE.equals(src.getAttribute("isDirectory"))) {
            DirectoryStream<Path> ds = src.newDirectoryStream();
            for (Path child : ds)
                extract(fs, child.toString());
            ds.close();
        } else {
            if (path.startsWith("/"))
                path = path.substring(1);
            Path dst = FileSystems.getDefault().getPath(path);
            Path parent = dst.getParent();
            if (parent.notExists())
                mkdirs(parent);
            src.copyTo(dst, REPLACE_EXISTING);
        }
    }

    // use DirectoryStream
    private static void z2zcopy(FileSystem src, FileSystem dst, String path)
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
                        path + (path.endsWith("/")?"":"/") + child.getName());
            }
            ds.close();
        } else {
            //System.out.println("copying..." + path);
            srcPath.copyTo(dstPath);
        }
    }

    // use TreeWalk to move
    private static void z2zmove(FileSystem src, FileSystem dst, String path)
        throws IOException
    {
        final Path srcPath = src.getPath(path).toAbsolutePath();
        final Path dstPath = dst.getPath(path).toAbsolutePath();

        Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file,
                                            BasicFileAttributes attrs)
            {
                Path dst = srcPath.relativize(file);
                dst = dstPath.resolve(dst);
                try {
                    Path parent = dstPath.getParent();
                    if (parent != null && parent.notExists())
                        mkdirs(parent);
                    file.moveTo(dst);
                } catch (IOException x) {
                    x.printStackTrace();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                                                     BasicFileAttributes attrs)
            {
                Path dst = srcPath.relativize(dir);
                dst = dstPath.resolve(dst);
                try {

                    if (dst.notExists())
                        mkdirs(dst);
                } catch (IOException x) {
                    x.printStackTrace();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                                                      IOException ioe)
                throws IOException
            {
                try {
                    dir.delete();
                } catch (IOException x) {
                    //x.printStackTrace();
                }
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

    private static void list(Path path, boolean verbose ) throws IOException {
        if (verbose)
            System.out.println(Attributes.readBasicFileAttributes(path).toString());
        else
            System.out.printf("  %s%n", path.toString());
        if (path.notExists())
            return;
        if (Attributes.readBasicFileAttributes(path).isDirectory()) {
            DirectoryStream<Path> ds = path.newDirectoryStream();
            for (Path child : ds)
                list(child, verbose);
            ds.close();
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
}
