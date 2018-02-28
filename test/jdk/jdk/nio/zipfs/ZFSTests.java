/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7156873 8040059 8028480 8034773 8153248 8061777
 * @summary ZipFileSystem regression tests
 *
 * @modules jdk.zipfs
 * @run main ZFSTests
 * @run main/othervm/java.security.policy=test.policy ZFSTests
 */


import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.spi.*;
import java.util.*;

public class ZFSTests {

    public static void main(String[] args) throws Throwable {
        test7156873();
        test8061777();
        tests();
    }

    static void test7156873() throws Throwable {
        String DIRWITHSPACE = "testdir with spaces";
        Path dir = Paths.get(DIRWITHSPACE);
        Path path = Paths.get(DIRWITHSPACE, "file.zip");
        try {
            Files.createDirectory(dir);
            URI uri = URI.create("jar:" + path.toUri());
            Map<String, Object> env = new HashMap<String, Object>();
            env.put("create", "true");
            try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {}
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir);
        }
    }

    static void test8061777() throws Throwable {
        Path path = Paths.get("file.zip");
        try {
            URI uri = URI.create("jar:" + path.toUri());
            Map<String, Object> env = new HashMap<String, Object>();
            env.put("create", "true");
            env.put("encoding", "Shift_JIS");
            try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
                FileSystemProvider fsp = fs.provider();
                Path p = fs.getPath("/\u8868\u7533.txt");  // 0x95 0x5c 0x90 0x5c
                try (OutputStream os = fsp.newOutputStream(p)) {
                    os.write("Hello!".getBytes("ASCII"));
                }
                Path dir = fs.getPath("/");
                Files.list(dir)
                     .forEach( child -> {
                             System.out.println("child:" + child);
                             if (!child.toString().equals(p.toString()))
                                 throw new RuntimeException("wrong path name created");
                          });
                if (!"Hello!".equals(new String(Files.readAllBytes(p), "ASCII")))
                    throw new RuntimeException("wrong content in newly created file");
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    static void tests() throws Throwable {
        Path path = Paths.get("file.zip");
        try {
            URI uri = URI.create("jar:" + path.toUri());
            Map<String, Object> env = new HashMap<String, Object>();
            env.put("create", "true");
            try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
                FileSystemProvider fsp = fs.provider();
                Set<? extends OpenOption> options;
                Path p = fs.getPath("test.txt");
                // 8028480
                options = EnumSet.of(StandardOpenOption.CREATE,
                                     StandardOpenOption.WRITE,
                                     StandardOpenOption.APPEND);
                try (FileChannel ch = fsp.newFileChannel(p, options)) {
                    ch.write(ByteBuffer.wrap("Hello!".getBytes("ASCII")));
                }
                // 8034773
                try (OutputStream os = fsp.newOutputStream(p, new OpenOption[0])) {
                    os.write("Hello2!".getBytes("ASCII"));
                }
                if (!"Hello2!".equals(new String(
                        Files.readAllBytes(fs.getPath("test.txt"))))) {
                    throw new RuntimeException("failed to open as truncate_existing");
                }

                options = EnumSet.of(StandardOpenOption.CREATE,
                                     StandardOpenOption.APPEND,
                                     StandardOpenOption.TRUNCATE_EXISTING);
                try (FileChannel ch = fsp.newFileChannel(p, options)) {
                    throw new RuntimeException("expected IAE not thrown!");
                } catch (IllegalArgumentException x) {
                    // expected x.printStackTrace();
                }

                //8153248
                Path dir = fs.getPath("/dir");
                Path subdir = fs.getPath("/dir/subdir");
                Files.createDirectory(dir);
                Files.createDirectory(subdir);
                Files.list(dir)
                     .forEach( child -> {
                             System.out.println("child:" + child);
                             if (child.toString().endsWith("/"))
                                 throw new RuntimeException("subdir names ends with /");
                          });
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
