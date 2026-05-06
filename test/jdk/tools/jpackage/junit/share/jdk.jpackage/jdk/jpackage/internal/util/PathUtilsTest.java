/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


class PathUtilsTest {

    @ParameterizedTest
    @CsvSource({
        "foo,''",
        "foo.bar,.bar",
        "foo..bar,.bar",
        ".bar,.bar",
        "foo.bar.buz,.buz",
        ".,.",
        "...,.",
        "..,.",
    })
    void test_getSuffix(Path path, String expected) {

        var suffix = PathUtils.getSuffix(path);

        assertEquals(expected, suffix);
    }

    @Test
    void test_getSuffix_null() {
        assertThrowsExactly(NullPointerException.class, () -> {
            PathUtils.getSuffix(null);
        });
    }

    @ParameterizedTest
    @CsvSource({
        "foo,'',foo",
        "a/b/foo.exe,.ico,a/b/foo.exe.ico",
        "foo,bar,foobar",
        "'',bar,bar",
        ".,bar,.bar",
    })
    void test_addSuffix(Path path, String suffix, Path expected) {

        var newPath = PathUtils.addSuffix(path, suffix);

        assertEquals(expected, newPath);
    }

    @Test
    void test_addSuffix_null() {
        assertThrowsExactly(NullPointerException.class, () -> {
            PathUtils.addSuffix(null, "foo");
        });
        assertThrowsExactly(NullPointerException.class, () -> {
            PathUtils.addSuffix(Path.of("foo"), null);
        });
    }

    @ParameterizedTest
    @CsvSource({
        "foo.exe,.ico,foo.ico",
        "foo.exe,,foo",
        "foo.exe,'',foo",
        "a/b/foo.exe,.ico,a/b/foo.ico",
        "foo,'',foo",
        "foo,bar,foobar",
        "'',bar,bar",
        ".,bar,bar",
        ".,.bar,.bar",
    })
    void test_replaceSuffix(Path path, String newSuffix, Path expected) {

        var newPath = PathUtils.replaceSuffix(path, newSuffix);

        assertEquals(expected, newPath);
    }

    @Test
    void test_replaceSuffix_null() {
        assertThrowsExactly(NullPointerException.class, () -> {
            PathUtils.replaceSuffix(null, "foo");
        });

        assertEquals(Path.of("foo"), PathUtils.replaceSuffix(Path.of("foo.a"), null));
    }

    @ParameterizedTest
    @CsvSource({
        "IDENTITY,a,a",
        "IDENTITY,,",
        "RETURN_NULL,a,",
        "RETURN_NULL,,",
        "FOO,a,foo",
        "FOO,,",
    })
    void test_mapNullablePath(PathMapper mapper, Path path, Path expected) {

        var newPath = PathUtils.mapNullablePath(mapper, path);

        assertEquals(expected, newPath);
    }

    @Test
    void test_mapNullablePath_null() {
        assertThrowsExactly(NullPointerException.class, () -> {
            PathUtils.mapNullablePath(null, Path.of(""));
        });
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"N/A"}, value = {
        "foo.exe",
        "N/A",
    })
    void test_normalizedAbsolutePath(Path path) {

        var newPath = PathUtils.normalizedAbsolutePath(path);

        var expected = Optional.ofNullable(path).map(v -> {
            return v.normalize().toAbsolutePath();
        }).orElse(null);

        assertEquals(expected, newPath);
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"N/A"}, value = {
        "foo.exe",
        "N/A",
    })
    void test_normalizedAbsolutePathString(Path path) {

        var newPath = PathUtils.normalizedAbsolutePathString(path);

        var expected = Optional.ofNullable(path).map(v -> {
            return v.normalize().toAbsolutePath().toString();
        }).orElse(null);

        assertEquals(expected, newPath);
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"N/A"}, value = {
        "N/A",
        "foo",
        "*",
        ":",
    })
    void test_asPath(String str) {

        var path = PathUtils.asPath(str);

        var expected = Optional.ofNullable(str).flatMap(v -> {
            return Result.of(() -> {
                return Path.of(v);
            }).value();
        });

        assertEquals(expected, path);
    }

    enum PathMapper implements UnaryOperator<Path> {
        IDENTITY {
            @Override
            public Path apply(Path path) {
                return path;
            }
        },
        RETURN_NULL {
            @Override
            public Path apply(Path path) {
                return null;
            }
        },
        FOO {
            @Override
            public Path apply(Path path) {
                return Path.of("foo");
            }
        },
        ;
    }
}
