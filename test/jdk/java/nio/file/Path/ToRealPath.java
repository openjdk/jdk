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
 * @bug 8295753 8306882
 * @summary Verify correct operation of Path.toRealPath
 * @library .. /test/lib
 * @build ToRealPath jdk.test.lib.Platform
 * @run junit ToRealPath
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.Platform;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static java.nio.file.LinkOption.*;
import static org.junit.jupiter.api.Assertions.*;

public class ToRealPath {
    static final boolean SUPPORTS_LINKS;
    static final Path DIR;
    static final Path SUBDIR;
    static final Path FILE;
    static final Path LINK;

    static {
        try {
            DIR = TestUtil.createTemporaryDirectory();
            SUBDIR = Files.createDirectory(DIR.resolve("subdir"));
            FILE = Files.createFile(DIR.resolve("foo"));
            LINK = DIR.resolve("link");
            SUPPORTS_LINKS = TestUtil.supportsLinks(DIR);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    public boolean supportsLinks() {
        return SUPPORTS_LINKS;
    }

    @Test
    public void locateSameFile() throws IOException {
        assertTrue(Files.isSameFile(FILE.toRealPath(),
                                    FILE.toRealPath(NOFOLLOW_LINKS)));
    }

    @Test
    public void failNotExist() {
        Path doesNotExist = DIR.resolve("DoesNotExist");
        assertThrows(IOException.class, () -> doesNotExist.toRealPath());
    }

    @Test
    public void failNotExistNoFollow() {
        Path doesNotExist = DIR.resolve("DoesNotExist");
        assertThrows(IOException.class,
                     () -> doesNotExist.toRealPath(NOFOLLOW_LINKS));
    }

    @EnabledIf("supportsLinks")
    @Test
    public void shouldResolveLinks() throws IOException {
        Path resolvedFile = FILE;
        if (Platform.isWindows()) {
            // Path::toRealPath does not work with environments using the
            // legacy subst mechanism. This is a workaround to keep the
            // test working if 'dir' points to a location on a subst drive.
            // See JDK-8213216.
            //
            Path tempLink = DIR.resolve("tempLink");
            Files.createSymbolicLink(tempLink, DIR.toAbsolutePath());
            Path resolvedDir = tempLink.toRealPath();
            Files.delete(tempLink);
            resolvedFile = resolvedDir.resolve(FILE.getFileName());
        }

        Files.createSymbolicLink(LINK, resolvedFile.toAbsolutePath());
        assertTrue(LINK.toRealPath().equals(resolvedFile.toRealPath()));
        Files.delete(LINK);
    }

    @Test
    @EnabledIf("supportsLinks")
    public void shouldNotResolveLinks() throws IOException {
        Files.createSymbolicLink(LINK, FILE.toAbsolutePath());
        assertEquals(LINK.toRealPath(NOFOLLOW_LINKS).getFileName(),
                     LINK.getFileName());
        Files.delete(LINK);
    }

    @Test
    public void eliminateDot() throws IOException {
        assertEquals(DIR.resolve(".").toRealPath(),
                     DIR.toRealPath());
    }

    @Test
    public void eliminateDotNoFollow() throws IOException {
        assertEquals(DIR.resolve(".").toRealPath(NOFOLLOW_LINKS),
                     DIR.toRealPath(NOFOLLOW_LINKS));
    }

    @Test
    public void eliminateDots() throws IOException {
        assertEquals(SUBDIR.resolve("..").toRealPath(),
                     DIR.toRealPath());
    }

    @Test
    public void eliminateDotsNoFollow() throws IOException {
        assertEquals(SUBDIR.resolve("..").toRealPath(NOFOLLOW_LINKS),
                     DIR.toRealPath(NOFOLLOW_LINKS));
    }

    @Test
    @EnabledIf("supportsLinks")
    public void noCollapseDots1() throws IOException {
        Path subPath = DIR.resolve(Path.of("dir", "subdir"));
        Path sub = Files.createDirectories(subPath);
        System.out.println("sub: " + sub);
        Files.createSymbolicLink(LINK, sub);
        System.out.println("LINK: " + LINK + " -> " + sub);
        Path p = Path.of("..", "..", FILE.getFileName().toString());
        System.out.println("p: " + p);
        Path path = LINK.resolve(p);
        System.out.println("path:      " + path);
        System.out.println("no follow: " + path.toRealPath(NOFOLLOW_LINKS));
        assertEquals(path.toRealPath(NOFOLLOW_LINKS), path);

        Files.delete(sub);
        Files.delete(sub.getParent());
        Files.delete(LINK);
    }

    @Test
    @EnabledIf("supportsLinks")
    public void noCollapseDots2() throws IOException {
        Path subPath = DIR.resolve(Path.of("dir", "subdir"));
        Path sub = Files.createDirectories(subPath);
        Path out = Files.createFile(DIR.resolve(Path.of("out.txt")));
        Path aaa = DIR.resolve(Path.of("aaa"));
        Files.createSymbolicLink(aaa, sub);
        System.out.println("aaa: " + aaa + " -> " + sub);
        Path bbb = DIR.resolve(Path.of("bbb"));
        Files.createSymbolicLink(bbb, sub);
        System.out.println("bbb: " + bbb + " -> " + sub);
        Path p = Path.of("aaa", "..", "..", "bbb", "..", "..", "out.txt");
        Path path = DIR.resolve(p);
        System.out.println("path:      " + path);
        System.out.println("no follow: " + path.toRealPath(NOFOLLOW_LINKS));
        assertEquals(path.toRealPath(NOFOLLOW_LINKS), path);
        System.out.println(path.toRealPath());

        Files.delete(sub);
        Files.delete(sub.getParent());
        Files.delete(out);
        Files.delete(aaa);
        Files.delete(bbb);
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public final void macOSTests() throws IOException {
        // theTarget = dir/subdir/theTarget
        Path theTarget = Path.of(SUBDIR.toString(), "theTarget");
        Files.createFile(theTarget);

        // dir/theLink -> dir/subdir
        Path theLink = Path.of(DIR.toString(), "theLink");
        Files.createSymbolicLink(theLink, SUBDIR);

        // thePath = dir/thelink/thetarget (all lower case)
        Path thePath = Path.of(DIR.toString(), "thelink", "thetarget");
        Path noFollow = thePath.toRealPath(NOFOLLOW_LINKS);
        int nc = noFollow.getNameCount();

        // Real path should retain case as dir/theLink/theTarget
        assertEquals(noFollow.getName(nc - 2), Path.of("theLink"));
        assertEquals(noFollow.getName(nc - 1), Path.of("theTarget"));
        assertEquals(noFollow.toString(),
                     Path.of(DIR.toString(), "theLink", "theTarget").toString());

        // Test where a link is preceded by ".." in the path
        Path superBeforeLink =
            Path.of(SUBDIR.toString(), "..", "thelink", "thetarget");
        noFollow = superBeforeLink.toRealPath(NOFOLLOW_LINKS);
        nc = noFollow.getNameCount();
        assertEquals(noFollow.getName(nc - 2), Path.of("theLink"));
        assertEquals(noFollow.getName(nc - 1), Path.of("theTarget"));

        // Test where a link is followed by ".." in the path
        Path linkBeforeSuper =
            Path.of(DIR.toString(), "thelink", "..", "subdir", "thetarget");
        noFollow = linkBeforeSuper.toRealPath(NOFOLLOW_LINKS);
        nc = noFollow.getNameCount();
        assertEquals(noFollow.getName(nc - 4), Path.of("theLink"));
        assertEquals(noFollow.getName(nc - 1), Path.of("theTarget"));

        Files.delete(theLink);
        Files.delete(theTarget);
    }

    @AfterAll
    public static void cleanup() throws IOException {
        Files.delete(FILE);
        Files.delete(SUBDIR);
        Files.delete(DIR);
    }
}
