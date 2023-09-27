/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4899022
 * @requires (os.family == "windows")
 * @summary Look for erroneous representation of drive letter
 * @run junit GetCanonicalPath
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class GetCanonicalPath {
    private static Stream<Arguments> pathProvider() {
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

    @ParameterizedTest
    @ValueSource(strings = {"\\\\?", "\\\\?\\UNC", "\\\\?\\UNC\\"})
    void badPaths(String pathname) {
        assertThrows(IOException.class, () -> new File(pathname).getCanonicalPath());
    }

    @ParameterizedTest
    @MethodSource("pathProvider")
    void goodPaths(String pathname, String expected) throws IOException {
        File file = new File(pathname);
        String canonicalPath = file.getCanonicalPath();
        assertEquals(expected, canonicalPath);
    }

    @Test
    void driveLetter() throws IOException {
        String path = new File("c:/").getCanonicalPath();
        assertFalse(path.length() > 3, "Drive letter incorrectly represented");
    }
}
