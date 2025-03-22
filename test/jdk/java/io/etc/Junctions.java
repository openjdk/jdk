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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;

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

    private static File dir = new File(DIR);
    private static File sub = new File(dir, SUB);
    private static File file = new File(sub, FILE);
    private static File junction = new File(JUNCTION); // junction -> dir\sub
    private static File renamed = new File(RENAMED); // renamed -> dir\sub

    // Create DIR\SUB\FILE
    @BeforeAll
    static void before() throws IOException {
        assertTrue(sub.mkdirs());
        assertTrue(file.createNewFile());
    }

    // Create JUNCTION -> DIR\SUB
    @Test
    @Order(1)
    void createJunction() throws IOException {
        assertTrue(FileUtils.createDirectoryJunction(JUNCTION, sub.toString()));
        assertTrue(junction.exists());
    }

    // Verify that output to linked file ends up in file
    @Test
    @Order(2)
    void write() throws IOException {
        File linkedFile = new File(junction, FILE);
        try (FileOutputStream fos = new FileOutputStream(linkedFile)) {
            fos.write(SOME_WORDS.getBytes());
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fis.read(b);
            assertEquals(SOME_WORDS, new String(b));
        }
    }

    // Verify that input from linked file is as expected
    @Test
    @Order(3)
    void read() throws IOException {
        File linkedFile = new File(junction, FILE);
        try (FileInputStream fis = new FileInputStream(linkedFile)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fis.read(b);
            assertEquals(SOME_WORDS, new String(b));
        }
    }

    // Verify that the junction is not hidden
    @Test
    @Order(4)
    void isNotHidden() throws IOException {
        assertFalse(junction.isHidden());
    }

    // Hide the junction target and verify that the junction is hidden
    @Test
    @Order(5)
    void isHidden() throws IOException {
        DosFileAttributeView dview = Files.getFileAttributeView(sub.toPath(),
            DosFileAttributeView.class);
        dview.setHidden(true);
        assertTrue(junction.isHidden());
        dview.setHidden(false);
        assertFalse(junction.isHidden());
    }

    // Rename the junction and verify the non-existence of the old junction,
    // the existence of the new one, and the target of the new onw
    @Test
    @Order(6)
    void rename() throws IOException {
        assertTrue(junction.renameTo(renamed));
        assertFalse(junction.exists());
        assertTrue(renamed.exists());
        File linkedFile = new File(renamed, FILE);
        try (FileInputStream fis = new FileInputStream(linkedFile)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fis.read(b);
            assertEquals(SOME_WORDS, new String(b));
        }
    }

    // Delete the renamed junction and verify that the target still exists
    @Test
    @Order(7)
    void delete() throws IOException {
        assertTrue(renamed.exists());
        assertTrue(renamed.delete());
        assertFalse(renamed.exists());
        assertTrue(sub.exists());
    }

    @Test
    @Order(8)
    void junctionToJunction() throws IOException {
        File j1 = new File("jcn1");
        File j2 = new File(dir, "jcn2");
        FileUtils.createDirectoryJunction(j2.toString(), sub.toString());
        FileUtils.createDirectoryJunction(j1.toString(), j2.toString());
        File linkedFile = new File(j1, FILE);
        try (FileInputStream fis = new FileInputStream(linkedFile)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fis.read(b);
            assertEquals(SOME_WORDS, new String(b));
        } finally {
            j1.delete();
            j2.delete();
        }
    }

    @Test
    @Order(9)
    void linkToJunction() throws IOException {
        Path link = Path.of("link");
        File junc = new File(dir, "junc");
        try {
            Files.createSymbolicLink(link, junc.toPath());
        } catch (IOException ioe) {
            if (ioe.getMessage().contains("privilege"))
                Assumptions.assumeFalse(true, "No privilege to create links");
            else
                throw ioe;
        }
        FileUtils.createDirectoryJunction(junc.toString(), sub.toString());
        File linkedFile = new File(link.toFile(), FILE);
        try (FileInputStream fis = new FileInputStream(linkedFile)) {
            byte[] b = new byte[SOME_WORDS.length()];
            fis.read(b);
            assertEquals(SOME_WORDS, new String(b));
        } finally {
            Files.delete(link);
            junc.delete();
        }
    }

    @AfterAll
    static void after() {
        file.delete();
        sub.delete();
        dir.delete();
    }
}
