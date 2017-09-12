/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8080679
 * @summary Verify the conversion from key events to escape sequences works properly.
 * @modules jdk.internal.le/jdk.internal.jline
 * @requires os.family == "windows"
 */

import jdk.internal.jline.WindowsTerminal;
import jdk.internal.jline.WindowsTerminal.KEY_EVENT_RECORD;

public class KeyConversionTest {
    public static void main(String... args) throws Exception {
        new KeyConversionTest().run();
    }

    void run() throws Exception {
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 256, 37, 1), "\033[D"); //LEFT
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 264, 37, 1), "\033[1;5D"); //Ctrl-LEFT
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 258, 37, 1), "\033[1;3D"); //Alt-LEFT
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 256, 46, 1), "\033[3~"); //delete
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 264, 46, 1), "\033[3;5~"); //Ctrl-delete
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 258, 46, 1), "\033[3;3~"); //Alt-delete
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 272, 46, 1), "\033[3;2~"); //Shift-delete
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 280, 46, 1), "\033[3;6~"); //Ctrl-Shift-delete
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 274, 46, 1), "\033[3;4~"); //Alt-Shift-delete
        checkKeyConversion(new KEY_EVENT_RECORD(true, '\0', 282, 46, 1), "\033[3;8~"); //Ctrl-Alt-Shift-delete
    }

    void checkKeyConversion(KEY_EVENT_RECORD event, String expected) {
        String actual = WindowsTerminal.convertKeys(event);

        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + "; actual: " + actual);
        }
    }
}
