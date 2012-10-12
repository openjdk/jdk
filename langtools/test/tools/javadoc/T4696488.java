/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.Configuration;

/**
 * @test
 * @bug     4696488
 * @summary javadoc doesn't handle UNC paths for destination directory
 * @author  Jesse Glick
 * @run main T4696488 T4696488.java
 */
public class T4696488 {

    public static void main(String... args) {
        System.setProperty("file.separator", "/");
        assertAddTrailingFileSep("/path/to/dir", "/path/to/dir/");
        assertAddTrailingFileSep("/path/to/dir/", "/path/to/dir/");
        assertAddTrailingFileSep("/path/to/dir//", "/path/to/dir/");
        System.setProperty("file.separator", "\\");
        assertAddTrailingFileSep("C:\\path\\to\\dir", "C:\\path\\to\\dir\\");
        assertAddTrailingFileSep("C:\\path\\to\\dir\\", "C:\\path\\to\\dir\\");
        assertAddTrailingFileSep("C:\\path\\to\\dir\\\\", "C:\\path\\to\\dir\\");
        assertAddTrailingFileSep("\\\\server\\share\\path\\to\\dir", "\\\\server\\share\\path\\to\\dir\\");
        assertAddTrailingFileSep("\\\\server\\share\\path\\to\\dir\\", "\\\\server\\share\\path\\to\\dir\\");
        assertAddTrailingFileSep("\\\\server\\share\\path\\to\\dir\\\\", "\\\\server\\share\\path\\to\\dir\\");
    }

    private static void assertAddTrailingFileSep(String input, String expectedOutput) {
        String output = Configuration.addTrailingFileSep(input);
        if (!expectedOutput.equals(output)) {
            throw new Error("expected " + expectedOutput + " but was " + output);
        }
    }

}
