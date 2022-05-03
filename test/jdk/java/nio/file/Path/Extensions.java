/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8057113
 * @summary Verify extension methods
 * @run testng Extensions
 */
public class Extensions {
    private static final Optional EMPTY = Optional.empty();
    private static final Optional EMPTY_STRING = of("");

    private static Optional<String> of(String s) {
        return Optional.of(s);
    }

    @Test
    public static void exceptions() {
        Path path = Path.of("file.ext");

        Assert.assertThrows(NullPointerException.class,
                            () -> {path.hasExtension(null);});

        Assert.assertThrows(NullPointerException.class,
                            () -> {path.replaceExtension(null);});
        Assert.assertThrows(IllegalArgumentException.class,
                            () -> {path.replaceExtension(".leading");});
        Assert.assertThrows(IllegalArgumentException.class,
                            () -> {path.replaceExtension("trailing.");});
    }

    /**
     * Returns path name string and expected extension pairs.
     *
     * @return {@code {{"pathname", "extension"},...}}
     */
    @DataProvider
    static Object[][] getProvider() {
        Object[][] pairs = new Object[][] {
            new Object[] {".",              EMPTY},
            new Object[] {"..",             EMPTY},
            new Object[] {".a.b",           of("b")},
            new Object[] {"......",         EMPTY_STRING},
            new Object[] {".....a",         of("a")},
            new Object[] {"...a.b",         of("b")},
            new Object[] {"..foo",          of("foo")},
            new Object[] {"test.rb",        of("rb")},
            new Object[] {"a/b/d/test.rb" , of("rb")},
            new Object[] {".a/b/d/test.rb", of("rb")},
            new Object[] {"foo.",           EMPTY_STRING},
            new Object[] {"test",           EMPTY},
            new Object[] {".profile",       EMPTY},
            new Object[] {".profile.sh",    of("sh")},
            new Object[] {"..foo",          of("foo")},
            new Object[] {".....foo",       of("foo")},
            new Object[] {".vimrc",         EMPTY},
            new Object[] {"test.",          EMPTY_STRING},
            new Object[] {"test..",         EMPTY_STRING},
            new Object[] {"test...",        EMPTY_STRING},
            new Object[] {"foo.tar.gz",     of("gz")},
            new Object[] {"foo.bar.",       EMPTY_STRING},
            new Object[] {"image.jpg",      of("jpg")},
            new Object[] {"music.mp3",      of("mp3")},
            new Object[] {"video.mp4",      of("mp4")},
            new Object[] {"document.txt",   of("txt")},
        };
        return pairs;
    }

    @Test(dataProvider = "getProvider")
    public static void get(String pathname, Optional<String> extension) {
        Assert.assertEquals(Path.of(pathname).getExtension(), extension);
    }

    /**
     * Returns path name string, expected result, and extensions to search for.
     *
     * @return {@code {{"pathname", of("expected"), "ext0", "ext1", ...},...}}
     */
    @DataProvider
    static Object[][] hasProvider() {
        Object[][] v = new Object[][] {
            new Object[] {"image.jpg", of("jpg"), "JPEG", "jpeg", "JPG", "jpg"},
            new Object[] {"image.jpg", EMPTY, "jpG", "jPG", "JPG"},
            new Object[] {"image.jpg", EMPTY, "gif", "png", "tiff"},
            new Object[] {"nullext", EMPTY, "gif", "jpg", "png", "tiff"},
            new Object[] {"emptyext.", EMPTY_STRING, "gif", "jpg", "png", ""},
            new Object[] {"doc.txt", EMPTY, "doc"},
        };
        return v;
    }

    @Test(dataProvider = "hasProvider")
    public static void has(String pathname, Optional<String> expected,
        String[] extensions) {
        String ext = extensions[0];
        String[] exts = extensions.length > 1 ?
            Arrays.copyOfRange(extensions, 1, extensions.length) : null;
        Optional<String> actual = Path.of(pathname).hasExtension(ext, exts);
        Assert.assertEquals(actual, expected);
    }

    /**
     * Returns path name string, new extension, and expected result.
     *
     * @return {@code {{"pathname", "extension", "expected"},...}}
     */
    @DataProvider
    static Object[][] replaceProvider() {
        Object[][] v = new Object[][] {
            new Object[] {"image.png", "png", Path.of("image.png")},
            new Object[] {"image.tiff", "jpg", Path.of("image.jpg")},
            new Object[] {"nullext", "dat", Path.of("nullext.dat")},
            new Object[] {"emptyext.", "dat", Path.of("emptyext.dat")},
            new Object[] {"foo.tar", "tar.gz", Path.of("foo.tar.gz")},
            new Object[] {"foo.", "bar", Path.of("foo.bar")},
        };
        return v;
    }

    @Test(dataProvider = "replaceProvider")
    public static void replace(String pathname, String extension,
        Path expected) {
        Path actual = Path.of(pathname).replaceExtension(extension);
        Assert.assertEquals(actual, expected);
    }
}
