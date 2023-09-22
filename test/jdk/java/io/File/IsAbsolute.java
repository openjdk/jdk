/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4022397 8287843
 * @summary General test for isAbsolute
 * @run junit IsAbsolute
 */

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class IsAbsolute {
    @EnabledOnOs(OS.WINDOWS)
    @ParameterizedTest
    @ValueSource(strings = {"c:\\foo\\bar", "c:/foo/bar", "\\\\foo\\bar"})
    public void windowsAbsolute(String path) throws IOException {
        assertTrue(new File(path).isAbsolute());
    }

    @EnabledOnOs(OS.WINDOWS)
    @ParameterizedTest
    @ValueSource(strings = {"/foo/bar", "\\foo\\bar", "c:foo\\bar"})
    public void windowsNotAbsolute(String path) throws IOException {
        assertFalse(new File(path).isAbsolute());
    }

    @EnabledOnOs({OS.LINUX, OS.MAC})
    @ParameterizedTest
    @ValueSource(strings = {"/foo", "/foo/bar"})
    public void unixAbsolute(String path) throws IOException {
        assertTrue(new File(path).isAbsolute());
    }

    @EnabledOnOs({OS.LINUX, OS.MAC})
    @ParameterizedTest
    @ValueSource(strings = {"foo", "foo/bar"})
    public void unixNotAbsolute(String path) throws IOException {
        assertFalse(new File(path).isAbsolute());
    }
}
