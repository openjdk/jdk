/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8154364
 * @summary Test of Files.isSameFile
 * @requires (os.family != "windows")
 * @library .. /test/lib
 * @build IsSameFile jdk.test.lib.util.FileUtils
 * @run junit IsSameFile
 */
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jdk.test.lib.util.FileUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class IsSameFile {
    private Path home;
    private Path a;
    private Path aa;
    private Path b;
    private Path c;
    private List<Path> allFiles;

    @BeforeAll
    public void init() throws IOException {
        home = Files.createTempDirectory("TestIsSameFile");

        allFiles = new ArrayList();
        allFiles.add(a = home.resolve("a"));
        allFiles.add(aa = home.resolve("a"));
        allFiles.add(b = home.resolve("b"));
        allFiles.add(c = home.resolve("c"));
    }

    public void deleteFiles() throws IOException {
        for (Path p : allFiles)
            Files.deleteIfExists(p);
    }

    @AfterAll
    public void deleteHome() throws IOException {
        TestUtil.removeAll(home);
    }

    public void test(boolean expect, Path x, Path y) throws IOException {
        assertTrue(Files.isSameFile(x, y) == expect);
    }

    private Stream<Arguments> stringCompareSource() throws IOException {
        deleteFiles();
        List<Arguments> list = new ArrayList<Arguments>();
        Path x = Path.of("x/y/z");
        list.add(Arguments.of(true, x, x));
        list.add(Arguments.of(false, Path.of("w/x/y/z"), x));
        Path y = Path.of("v/w/x/../y/z");
        list.add(Arguments.of(false, y, Path.of("v/w/y/z")));
        list.add(Arguments.of(false, y, Path.of("v/w/x/y/../z")));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("stringCompareSource")
    public void stringCompare(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    private Stream<Arguments> noneExistSource() throws IOException {
        deleteFiles();
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(true, a, a));
        list.add(Arguments.of(true, a, aa));
        list.add(Arguments.of(false, a, b));
        list.add(Arguments.of(true, b, b));
        return list.stream();
    }

    @Test
    public void obj2Null() {
        Path x = Path.of("x/y");
        assertThrows(NullPointerException.class, () -> Files.isSameFile(x, null));
    }

    private static void zipStringToFile(String entry, String content,
                                        Path path)
        throws IOException
    {
        FileOutputStream fos = new FileOutputStream(path.toString());
        ZipOutputStream zos = new ZipOutputStream(fos);

        ZipEntry zipEntry = new ZipEntry(entry);
        zos.putNextEntry(zipEntry);
        zos.write(content.getBytes());

        zos.close();
        fos.close();
    }

    private Stream<Arguments> obj2ZipSource() throws IOException {
        deleteFiles();
        Files.createFile(a);
        zipStringToFile("quote.txt", "To be determined", b);
        FileSystem zipfs = FileSystems.newFileSystem(b);
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(false, a, zipfs.getPath(b.toString())));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("obj2ZipSource")
    public void obj2Zip(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    @ParameterizedTest
    @MethodSource("noneExistSource")
    public void noneExist(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    private Stream<Arguments> aExistsSource() throws IOException {
        deleteFiles();
        Files.createFile(a);
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(true, a, a));
        list.add(Arguments.of(true, a, aa));
        list.add(Arguments.of(false, a, b));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("aExistsSource")
    public void aExists(boolean expect, Path x, Path y) throws IOException {
        test(expect, x, y);
    }

    private Stream<Arguments> abExistSource() throws IOException {
        deleteFiles();
        Files.createFile(a);
        Files.createFile(b);
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(false, a, b));
        list.add(Arguments.of(true, b, b));
        list.add(Arguments.of(false, a, c));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("abExistSource")
    public void abExist(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    private Stream<Arguments> abcExistSource() throws IOException {
        deleteFiles();
        Files.createFile(a);
        Files.createFile(b);
        Files.createSymbolicLink(c, a);
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(true, a, c));
        list.add(Arguments.of(false, a, b));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("abcExistSource")
    public void abcExist(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    private Stream<Arguments> bcExistSource() throws IOException {
        deleteFiles();
        Files.createFile(b);
        Files.createSymbolicLink(c, a);
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(true, a, a));
        list.add(Arguments.of(true, a, aa));
        list.add(Arguments.of(false, a, b));
        list.add(Arguments.of(true, b, b));
        list.add(Arguments.of(false, a, c));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("bcExistSource")
    public void bcExist(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    //
    // L1 => L2 => target
    // L3 => L4 => target
    //
    private Stream<Arguments> equalFollowingSource() throws IOException {
        deleteFiles();
        Path target = home.resolve("target");
        Files.createFile(target);
        allFiles.add(target);

        Path L2 = Path.of("link2");
        Files.createSymbolicLink(L2, target);
        allFiles.add(L2);

        Path L1 = Path.of("link1");
        Files.createSymbolicLink(L1, L2);
        allFiles.add(L1);

        Path L4 = Path.of("link4");
        Files.createSymbolicLink(L4, target);
        allFiles.add(L4);

        Path L3 = Path.of("link3");
        Files.createSymbolicLink(L3, L4);
        allFiles.add(L3);

        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(true, L1, L3));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("equalFollowingSource")
    public void equalFollowing(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    //
    // L1 => L2 => target
    // L3 => L4 => cible
    //
    private Stream<Arguments> unequalFollowingSource() throws IOException {
        deleteFiles();
        Path target = home.resolve("target");
        Files.createFile(target);
        allFiles.add(target);

        Path L2 = Path.of("link2");
        Files.createSymbolicLink(L2, target);
        allFiles.add(L2);

        Path L1 = Path.of("link1");
        Files.createSymbolicLink(L1, L2);
        allFiles.add(L1);

        Path cible = home.resolve("cible");
        Files.createFile(cible);
        allFiles.add(cible);

        Path L4 = Path.of("link4");
        Files.createSymbolicLink(L4, cible);
        allFiles.add(L4);

        Path L3 = Path.of("link3");
        Files.createSymbolicLink(L3, L4);
        allFiles.add(L3);

        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(false, L1, L3));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("unequalFollowingSource")
    public void unequalFollowing(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    //
    // L1 => L2 => <does not exist>
    // L3 => L4 => <does not exist>
    //
    private Stream<Arguments> unequalNotFollowingSource() throws IOException {
        deleteFiles();

        Path doesNotExist = Path.of("doesNotExist");

        Path L2 = Path.of("link2");
        Files.createSymbolicLink(L2, doesNotExist);
        allFiles.add(L2);

        Path L1 = Path.of("link1");
        Files.createSymbolicLink(L1, L2);
        allFiles.add(L1);

        Path L4 = Path.of("link4");
        Files.createSymbolicLink(L4, doesNotExist);
        allFiles.add(L4);

        Path L3 = Path.of("link3");
        Files.createSymbolicLink(L3, L4);
        allFiles.add(L3);

        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(false, L1, L3));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("unequalNotFollowingSource")
    public void unequalNotFollowing(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    //
    // L1 => L2 => L3 => L4 => target
    //
    // isSameFile(LX,LY) should be true for all X, Y
    //
    private Stream<Arguments> multiLinkSource() throws IOException {
        deleteFiles();
        Path target = home.resolve("target");
        Files.createFile(target);
        allFiles.add(target);
        Path[] links = new Path[4];
        links[3] = Files.createSymbolicLink(Path.of("link4"), target);
        allFiles.add(links[3]);
        for (int i = 3; i > 0; i--) {
            links[i-1] = Files.createSymbolicLink(Path.of("link"+i), links[i]);
            allFiles.add(links[i-1]);
        }

        List<Arguments> list = new ArrayList<Arguments>();
        for (int i = 0; i < 4; i++) {
            list.add(Arguments.of(true, links[i], target));
            for (int j = i+1; j < 4; j++)
                list.add(Arguments.of(true, links[i], links[j]));
        }

        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("multiLinkSource")
    public void multiLink(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    //
    // L1 => L2 => L3 => L4 => <does not exist>
    //
    // isSameFile(LX,LY) should be true for all X, Y
    //
    private Stream<Arguments> multiLinkNoTargetSource() throws IOException {
        deleteFiles();
        Path target = home.resolve("target");
        Files.createFile(target);
        allFiles.add(target);
        Path[] links = new Path[4];
        links[3] = Files.createSymbolicLink(Path.of("link4"), target);
        allFiles.add(links[3]);
        Files.delete(target);
        allFiles.remove(target);
        for (int i = 3; i > 0; i--) {
            links[i-1] = Files.createSymbolicLink(Path.of("link"+i), links[i]);
            allFiles.add(links[i-1]);
        }

        List<Arguments> list = new ArrayList<Arguments>();
        for (int i = 0; i < 4; i++) {
            list.add(Arguments.of(false, links[i], target));
            for (int j = i+1; j < 4; j++)
                list.add(Arguments.of(true, links[i], links[j]));
        }

        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("multiLinkNoTargetSource")
    public void multiLinkNoTarget(boolean expect, Path x, Path y)
        throws IOException {
        test(expect, x, y);
    }

    //
    // L1 -> L2 -> L3 -> L1...
    //
    // This is a loop and should throw FileSystemException.
    //
    private Stream<Arguments> linkLoopSource() throws IOException {
        deleteFiles();

        Path link1 = home.resolve("L1");
        Path link2 = home.resolve("L2");
        Path link3 = home.resolve("L3");
        allFiles.add(Files.createSymbolicLink(link1, link2));
        allFiles.add(Files.createSymbolicLink(link2, link3));
        allFiles.add(Files.createSymbolicLink(link3, link1));

        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(true, link1, link2));
        list.add(Arguments.of(true, link2, link3));
        list.add(Arguments.of(true, link3, link1));

        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("linkLoopSource")
    public void linkLoop(boolean expect, Path x, Path y) throws IOException {
        assertThrows(FileSystemException.class, () -> Files.isSameFile(x, y));
    }
}
