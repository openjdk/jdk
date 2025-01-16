/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4899022 8003887
 * @summary Look for erroneous representation of drive letter
 * @run junit GetCanonicalPath
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class GetCanonicalPath {
    private static Stream<Arguments> pathProviderWindows() {
        List<Arguments> list = new ArrayList<Arguments>();

        File dir = new File(System.getProperty("user.dir", "."));
        char drive = dir.getPath().charAt(0);

        String pathname = drive + ":\\";
        list.add(Arguments.of(pathname, pathname));

        list.add(Arguments.of(drive + ":", dir.toString()));

        String name = "foo";
        pathname = "\\\\?\\" + name;
        list.add(Arguments.of(pathname, new File(dir, name).toString()));
        pathname = "\\\\?\\" + drive + ":" + name;
        list.add(Arguments.of(pathname, new File(dir, name).toString()));

        pathname = "foo\\bar\\gus";
        list.add(Arguments.of(pathname, new File(dir, pathname).toString()));

        pathname = drive + ":\\foo\\bar\\gus";
        list.add(Arguments.of(pathname, pathname));

        pathname = "\\\\server\\share\\foo\\bar\\gus";
        list.add(Arguments.of(pathname, pathname));

        pathname = "\\\\localhost\\" + drive + "$\\Users\\file.dat";
        list.add(Arguments.of(pathname, pathname));

        list.add(Arguments.of("\\\\?\\" + drive + ":\\Users\\file.dat",
                              drive + ":\\Users\\file.dat"));
        list.add(Arguments.of("\\\\?\\UNC\\localhost\\" + drive + "$\\Users\\file.dat",
                              "\\\\localhost\\" + drive + "$\\Users\\file.dat"));

        return list.stream();
    }

    private static Stream<Arguments> pathProviderUnix() {
        return Stream.of(
            Arguments.of("/../../../../../a/b/c", "/a/b/c"),
            Arguments.of("/../../../../../a/../b/c", "/b/c"),
            Arguments.of("/../../../../../a/../../b/c", "/b/c"),
            Arguments.of("/../../../../../a/../../../b/c", "/b/c"),
            Arguments.of("/../../../../../a/../../../../b/c", "/b/c")
        );
    }

    @ParameterizedTest
    @EnabledOnOs({OS.AIX, OS.LINUX, OS.MAC})
    @MethodSource("pathProviderUnix")
    void goodPathsUnix(String pathname, String expected) throws IOException {
        File file = new File(pathname);
        String canonicalPath = file.getCanonicalPath();
        assertEquals(expected, canonicalPath);
    }

    @ParameterizedTest
    @EnabledOnOs(OS.WINDOWS)
    @ValueSource(strings = {"\\\\?", "\\\\?\\UNC", "\\\\?\\UNC\\"})
    void badPathsWindows(String pathname) {
        assertThrows(IOException.class, () -> new File(pathname).getCanonicalPath());
    }

    @ParameterizedTest
    @EnabledOnOs(OS.WINDOWS)
    @MethodSource("pathProviderWindows")
    void goodPathsWindows(String pathname, String expected) throws IOException {
        File file = new File(pathname);
        String canonicalPath = file.getCanonicalPath();
        assertEquals(expected, canonicalPath);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void driveLetter() throws IOException {
        String path = new File("c:/").getCanonicalPath();
        assertFalse(path.length() > 3, "Drive letter incorrectly represented");
    }

    // Create a File with the given pathname and return the File as a Path
    private static Path createFile(String pathname) throws IOException {
        File file = new File(pathname);
        file.deleteOnExit();
        return file.toPath();
    }

    private static boolean supportsLinks = true;
    private static String linkMessage;

    private static Path link;
    private static Path sublink;
    private static Path subsub;

    @BeforeAll
    static void createSymlinks() throws IOException {
        final String DIR     = "dir";
        final String SUBDIR  = "subdir";
        final String TARGET  = "target.txt";
        final String LINK    = "link";
        final String SUBLINK = "sublink";
        final String FILE    = "file.txt";

        // Create directories dir/subdir
        Path dir = createFile(DIR);
        Path subdir = createFile(dir.resolve(SUBDIR).toString());
        Files.createDirectories(subdir);

        // Create file dir/subdir/target.txt
        Path target = createFile(subdir.resolve(TARGET).toString());
        Files.createFile(target);

        // Create symbolic link link -> dir
        link = createFile(Path.of(LINK).toString());
        try {
            Files.createSymbolicLink(link, dir);
        } catch (UnsupportedOperationException | IOException x) {
            if (OS.WINDOWS.isCurrentOs()) {
                supportsLinks = false;
                linkMessage = "\"" + x.getMessage() + "\"";
                return;
            } else {
                throw x;
            }
        }

        sublink = createFile(Path.of(DIR, SUBDIR, SUBLINK).toString());
        Path file = createFile(Path.of(DIR, SUBDIR, FILE).toString());
        Files.createFile(file);

        // Create symbolic link dir/subdir/sublink -> file.txt
        Files.createSymbolicLink(sublink, Path.of(FILE));
        sublink.toFile().deleteOnExit();

        subsub = createFile(Path.of(LINK, SUBDIR, SUBLINK).toString());
    }

    @Test
    void linkToDir() throws IOException {
        Assumptions.assumeTrue(supportsLinks, linkMessage);

        // Check link evaluates to dir
        assertEquals(link.toRealPath().toString(),
                     link.toFile().getCanonicalPath());
    }

    @Test
    void linkToFile() throws IOException {
        Assumptions.assumeTrue(supportsLinks, linkMessage);

        // Check sublink evaluates to file.txt
        assertEquals(sublink.toRealPath().toString(),
                     sublink.toFile().getCanonicalPath());
    }

    @Test
    void linkToFileInSubdir() throws IOException {
        Assumptions.assumeTrue(supportsLinks, linkMessage);

        // Check link/subdir/sublink evaluates to dir/subdir/file.txt
        assertEquals(subsub.toRealPath().toString(),
                     subsub.toFile().getCanonicalPath());
    }
}
