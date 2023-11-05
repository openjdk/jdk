/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8268725
 * @summary Tests for the --enable-native-access option
 * @modules jdk.jshell
 * @run testng ToolEnableNativeAccessTest
 */

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class ToolEnableNativeAccessTest extends ReplToolTesting {

    @Test
    public void testOptionDebug() {
        test(
                (a) -> assertCommand(a, "/debug b",
                        "RemoteVM Options: []\n"
                        + "Compiler options: []"),
                (a) -> assertCommand(a, "/env --enable-native-access",
                        "|  Setting new options and restoring state."),
                (a) -> assertCommandCheckOutput(a, "/debug b", s -> {
                    assertTrue(s.contains("RemoteVM Options: [--enable-native-access, ALL-UNNAMED]"));
                    assertTrue(s.contains("Compiler options: []"));
                })
        );
    }

    @Test
    public void testCommandLineFlag() {
        test(new String[] {"--enable-native-access"},
                (a) -> assertCommandCheckOutput(a, "/debug b", s -> {
                    assertTrue(s.contains("RemoteVM Options: [--enable-native-access, ALL-UNNAMED]"));
                    assertTrue(s.contains("Compiler options: []"));
                })
        );
    }

}
