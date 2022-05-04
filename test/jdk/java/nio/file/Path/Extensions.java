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
        Object[][] pairs = {
            {".",              EMPTY},
            {"..",             EMPTY},
            {".a.b",           of("b")},
            {"......",         EMPTY_STRING},
            {".....a",         of("a")},
            {"...a.b",         of("b")},
            {"..foo",          of("foo")},
            {"test.rb",        of("rb")},
            {"a/b/d/test.rb" , of("rb")},
            {".a/b/d/test.rb", of("rb")},
            {"foo.",           EMPTY_STRING},
            {"test",           EMPTY},
            {".profile",       EMPTY},
            {".profile.sh",    of("sh")},
            {"..foo",          of("foo")},
            {".....foo",       of("foo")},
            {".vimrc",         EMPTY},
            {"test.",          EMPTY_STRING},
            {"test..",         EMPTY_STRING},
            {"test...",        EMPTY_STRING},
            {"foo.tar.gz",     of("gz")},
            {"foo.bar.",       EMPTY_STRING},
            {"image.jpg",      of("jpg")},
            {"music.mp3",      of("mp3")},
            {"video.mp4",      of("mp4")},
            {"document.txt",   of("txt")},
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
        Object[][] v = {
            {"image.jpg", of("jpg"), "JPEG", "jpeg", "JPG", "jpg"},
            {"image.jpg", EMPTY, "jpG", "jPG", "JPG"},
            {"image.jpg", EMPTY, "gif", "png", "tiff"},
            {"nullext", EMPTY, "gif", "jpg", "png", "tiff"},
            {"emptyext.", EMPTY_STRING, "gif", "jpg", "png", ""},
            {"doc.txt", EMPTY, "doc"},
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
        Object[][] v = {
            {"image.png", "png", Path.of("image.png")},
            {"image.tiff", "jpg", Path.of("image.jpg")},
            {"nullext", "dat", Path.of("nullext.dat")},
            {"emptyext.", "dat", Path.of("emptyext.dat")},
            {"foo.tar", "tar.gz", Path.of("foo.tar.gz")},
            {"foo.", "bar", Path.of("foo.bar")},
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
