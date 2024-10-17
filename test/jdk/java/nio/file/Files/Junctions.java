/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Set;
import static java.nio.file.StandardOpenOption.*;

import jdk.test.lib.util.FileUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 1234567
 * @summary Verifies java.io support for Windows directory junctions
 * @requires os.family == "windows"
 * @library /test/lib
 * @run junit/othervm/native Junctions
 */
@TestMethodOrder(OrderAnnotation.class)
@EnabledOnOs(OS.WINDOWS)
public class Junctions {
    private static final String SOME_WORDS = "Voici des mots";

    private static final String DIR = "dir";
    private static final String SUB = "sub";
    private static final String FILE = "file.txt";
    private static final String JUNCTION = "junction";
    private static final String RENAMED = "renamed";

    private static Path dir = Path.of(DIR);
    private static Path sub = dir.resolve(SUB);
    private static Path file = sub.resolve(FILE);
    private static Path junction = Path.of(JUNCTION); // junction -> dir\sub
    private static Path renamed = Path.of(RENAMED); // renamed -> dir\sub

    // Create DIR\SUB\FILE
    @BeforeAll
    static void before() throws IOException {
        assertTrue(Files.exists(Files.createDirectories(sub)));
        assertTrue(Files.exists(Files.createFile(file)));
    }

    // Create JUNCTION -> DIR\SUB
    @Test
    @Order(1)
    void createJunction() throws IOException {
        assertTrue(FileUtils.createDirectoryJunction(JUNCTION, sub.toString()));
        assertTrue(Files.exists(junction));
    }

    // Verify that output to linked file ends up in file
    @Test
    @Order(2)
    void write() throws IOException {
        Path linkedFile = junction.resolve(FILE);
        try (FileChannel fc = FileChannel.open(linkedFile, WRITE)) {
            fc.write(ByteBuffer.wrap(SOME_WORDS.getBytes()));
        }
        try (FileChannel fc = FileChannel.open(file, READ)) {
            byte[] b = new byte[SOME_WORDS.length()];
            ByteBuffer buf = ByteBuffer.wrap(b);
            fc.read(buf);
            assertEquals(SOME_WORDS, new String(b));
        }
    }

    // Verify that input from linked file is as expected
    @Test
    @Order(3)
    void read() throws IOException {
        Path linkedFile = junction.resolve(FILE);
        try (FileChannel fc = FileChannel.open(linkedFile, READ)) {
            byte[] b = new byte[SOME_WORDS.length()];
            ByteBuffer buf = ByteBuffer.wrap(b);
            fc.read(buf);
            assertEquals(SOME_WORDS, new String(b));
        }
    }

    // Verify that the junction is not hidden
    @Test
    @Order(4)
    void isNotHidden() throws IOException {
        DosFileAttributeView dview = Files.getFileAttributeView(junction,
            DosFileAttributeView.class);
        DosFileAttributes dattr = dview.readAttributes();
        assertFalse(dattr.isHidden());
        dview = Files.getFileAttributeView(junction, DosFileAttributeView.class,
            LinkOption.NOFOLLOW_LINKS);
        dattr = dview.readAttributes();
        assertFalse(dattr.isHidden());
    }

    // Hide the junction target and verify that the junction is hidden
    @Test
    @Order(5)
    void isHidden() throws IOException {
        DosFileAttributeView sview = Files.getFileAttributeView(sub,
            DosFileAttributeView.class);
        sview.setHidden(true);
        DosFileAttributeView jview = Files.getFileAttributeView(junction,
            DosFileAttributeView.class);
        DosFileAttributes jattr = jview.readAttributes();
        assertTrue(jattr.isHidden());
        sview.setHidden(false);
        jview = Files.getFileAttributeView(junction,
            DosFileAttributeView.class);
        jattr = jview.readAttributes();
        assertFalse(jattr.isHidden());
    }

    // Rename the junction and verify the non-existence of the old junction,
    // the existence of the new one, and the target of the new onw
    @Test
    @Order(6)
    void move() throws IOException {
        Files.move(junction, renamed);
        assertTrue(Files.notExists(junction));
        assertTrue(Files.exists(renamed));
        Path linkedFile = renamed.resolve(FILE);
        try (FileChannel fc = FileChannel.open(linkedFile, READ)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fc.read(ByteBuffer.wrap(b));
            assertEquals(SOME_WORDS, new String(b));
        }
    }

    // Delete the renamed junction and verify that the target still exists
    @Test
    @Order(7)
    void delete() throws IOException {
        assertTrue(Files.exists(renamed));
        Files.delete(renamed);
        assertTrue(Files.notExists(renamed));
        assertTrue(Files.exists(sub));
    }

    @Test
    @Order(8)
    void junctionToJunction() throws IOException {
        Path j1 = Path.of("jcn1");
        Path j2 = dir.resolve("jcn2");
        FileUtils.createDirectoryJunction(j2.toString(), sub.toString());
        FileUtils.createDirectoryJunction(j1.toString(), j2.toString());
        Path linkedFile = j1.resolve(FILE);
        try (FileChannel fc = FileChannel.open(linkedFile, READ)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fc.read(ByteBuffer.wrap(b));
            assertEquals(SOME_WORDS, new String(b));
        } finally {
            Files.deleteIfExists(j1);
            Files.deleteIfExists(j2);
        }
    }

    @Test
    @Order(9)
    void linkToJunction() throws IOException {
        Path link = Path.of("link");
        Path junc = dir.resolve("junc");
        try {
            Files.createSymbolicLink(link, junc);
        } catch (IOException ioe) {
            if (ioe.getMessage().contains("privilege"))
                Assumptions.assumeFalse(true, "No privilege to create links");
            else
                throw ioe;
        }
        FileUtils.createDirectoryJunction(junc.toString(), sub.toString());
        Path linkedFile = link.resolve(FILE);
        try (FileChannel fc = FileChannel.open(linkedFile, READ)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fc.read(ByteBuffer.wrap(b));
            assertEquals(SOME_WORDS, new String(b));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(junc);
        }
    }

    @Test
    @Order(10)
    void readSymbolicLink() throws IOException {
        Path junc = dir.resolve("junc");
        try {
            FileUtils.createDirectoryJunction(junc.toString(), sub.toString());
            Path subAbs = sub.toAbsolutePath();
            Path target = Files.readSymbolicLink(junc);
            assertEquals(subAbs, target);
        } finally {
            Files.deleteIfExists(junc);
        }
    }

    @Test
    @Order(11)
    void takeAWalk() throws IOException {
        Path juncdir = dir.resolve("juncdir");
        Path juncfile = dir.resolve("juncfile");
        try {
            FileUtils.createDirectoryJunction(juncdir.toString(), sub.toString());
            FileUtils.createDirectoryJunction(juncfile.toString(), file.toString());
            Path path = dir.toAbsolutePath();
            Path parent = path.getParent();
            System.out.println("path: " + path + "\nparent: " + parent);
            Files.walkFileTree(parent,
                Set.of(FileVisitOption.FOLLOW_LINKS),
                1000,
                new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                                                         BasicFileAttributes attrs) {
                    System.out.println("Visiting directory " + dir);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs) {
                    System.out.println("Visiting file " + file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } finally {
            Files.deleteIfExists(juncdir);
            Files.deleteIfExists(juncfile);
        }
    }

    @AfterAll
    static void after() throws IOException {
        Files.delete(file);
        Files.delete(sub);
        Files.delete(dir);
    }
}
