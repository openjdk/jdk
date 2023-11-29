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
 * @bug 4131169 4109131 8287843
 * @summary Basic test for getAbsolutePath method
 * @run junit GetAbsolutePath
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

public class GetAbsolutePath {

    private static final String USER_DIR = System.getProperty("user.dir");

    private static char driveLetter() {
        assert System.getProperty("os.name").startsWith("Windows");

        if ((USER_DIR.length() > 2) && (USER_DIR.charAt(1) == ':')
            && (USER_DIR.charAt(2) == '\\'))
            return USER_DIR.charAt(0);

        throw new RuntimeException("Current directory has no drive");
    }

    private static Stream<Arguments> windowsSource() {
        char drive = driveLetter();
        return Stream.of(Arguments.of("/foo/bar", drive + ":\\foo\\bar"),
                         Arguments.of("\\foo\\bar", drive + ":\\foo\\bar"),
                         Arguments.of("c:\\foo\\bar", "c:\\foo\\bar"),
                         Arguments.of("c:/foo/bar", "c:\\foo\\bar"),
                         Arguments.of("\\\\foo\\bar", "\\\\foo\\bar"),
                         Arguments.of("", USER_DIR), // empty path
                         Arguments.of("\\\\?\\foo", USER_DIR + "\\foo"),
                         Arguments.of("\\\\?\\C:\\Users\\x", "C:\\Users\\x"),
                         Arguments.of("\\\\?\\" + drive + ":", USER_DIR),
                         Arguments.of("\\\\?\\" + drive + ":bar", USER_DIR + "\\bar"));
    }

    @EnabledOnOs(OS.WINDOWS)
    @ParameterizedTest
    @MethodSource("windowsSource")
    public void windows(String path, String absolute) throws IOException {
        File file = new File(path);
        assertEquals(0, absolute.compareToIgnoreCase(file.getAbsolutePath()));
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    public void windowsDriveRelative() throws IOException {
        // Tricky directory-relative case
        char d = Character.toLowerCase(driveLetter());
        char z = 0;
        if (d != 'c') z = 'c';
        else if (d != 'd') z = 'd';
        if (z != 0) {
            File f = new File(z + ":.");
            if (f.exists()) {
                String zUSER_DIR = f.getCanonicalPath();
                File path = new File(z + ":foo");
                String p = path.getAbsolutePath();
                String ans = zUSER_DIR + "\\foo";
                assertEquals(0, p.compareToIgnoreCase(ans));
            }
        }
    }

    private static Stream<Arguments> unixSource() {
        return Stream.of(Arguments.of("foo", USER_DIR + "/foo"),
                         Arguments.of("foo/bar", USER_DIR + "/foo/bar"),
                         Arguments.of("/foo", "/foo"),
                         Arguments.of("/foo/bar", "/foo/bar"),
                         Arguments.of("", USER_DIR));
    }

    @EnabledOnOs({OS.LINUX, OS.MAC})
    @ParameterizedTest
    @MethodSource("unixSource")
    public void unix(String path, String absolute) throws IOException {
        assertEquals(absolute, new File(path).getAbsolutePath());
    }
}
