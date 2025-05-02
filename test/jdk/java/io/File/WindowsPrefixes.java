/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8287843
 * @summary Basic test for Windows path prefixes
 * @requires (os.family == "windows")
 * @run junit WindowsPrefixes
 */

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class WindowsPrefixes {

    private static Stream<Arguments> paths() {
        return Stream.of(Arguments.of(""),
                         Arguments.of("C:\\"),
                         Arguments.of("C:"),
                         Arguments.of("\\foo"),
                         Arguments.of("foo"),
                         Arguments.of("foo\\bar"),
                         Arguments.of("C:\\foo"),
                         Arguments.of("C:foo"),
                         Arguments.of("C:\\foo\\bar"));
    }

    @ParameterizedTest
    @MethodSource("paths")
    public void getAbsolutePath(String path) throws IOException {
        File file = new File(path);
        File that = new File("\\\\?\\" + path);
        assertEquals(file.getAbsolutePath(), that.getAbsolutePath());
    }

    @ParameterizedTest
    @MethodSource("paths")
    public void getCanonicalPath(String path) throws IOException {
        File file = new File(path);
        File that = new File("\\\\?\\" + path);
        assertEquals(file.getCanonicalPath(), that.getCanonicalPath());
    }

    @ParameterizedTest
    @MethodSource("paths")
    public void getName(String path) throws IOException {
        File file = new File(path);
        File that = new File("\\\\?\\" + path);
        assertEquals(file.getName(), that.getName());
    }

    @ParameterizedTest
    @MethodSource("paths")
    public void getParent(String path) throws IOException {
        File file = new File(path);
        File that = new File("\\\\?\\" + path);
        assertEquals(file.getParent(), that.getParent());
    }

    @ParameterizedTest
    @MethodSource("paths")
    public void isAbsolute(String path) throws IOException {
        File file = new File(path);
        File that = new File("\\\\?\\" + path);
        assertEquals(file.isAbsolute(), that.isAbsolute());
    }
}
