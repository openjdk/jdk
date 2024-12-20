/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2023, 2024, Red Hat, Inc.
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
 * @bug 8318671
 * @summary Tests various ways to call memstat
 * @library /test/lib /
 *
 * @run driver compiler.compilercontrol.commands.MemStatTest
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.process.ProcessTools;

public class MemStatTest {
    record Expected (String subcommand, int value) {}
    static Expected expectedValues[] = {
            new Expected("", 1), // default => collect
            new Expected("collect", 1),
            new Expected("print", 2 | 1),
            new Expected("details", 4 | 1),
            new Expected("print+details", 4 | 2 | 1),
            new Expected("details+print", 4 | 2 | 1),
            new Expected("details+collect+print", 4 | 2 | 1),
    };

    static String invalid[] = new String[] {
            "invalid", "collect,invalid", "collect,print"
    };

    public static void main(String[] args) throws Exception {
        for (Expected e : expectedValues) {
            String comma = e.subcommand.isEmpty() ? "" : ",";
            ProcessTools.executeTestJava("-XX:CompileCommand=MemStat,*.*" + comma + e.subcommand, "-version")
                    .shouldHaveExitValue(0)
                    .shouldNotContain("CompileCommand: An error occurred during parsing")
                    .shouldContain("CompileCommand: MemStat *.* uintx MemStat = " + e.value); // should be registered
        }

        for (String s : invalid) {
            // invalid suboption should be rejected
            ProcessTools.executeTestJava("-XX:CompileCommand=MemStat,*.*," + s, "-version")
                    .shouldNotHaveExitValue(0)
                    .shouldContain("CompileCommand: An error occurred during parsing")
                    .shouldContain("Error: Value cannot be read for option 'MemStat'")
                    .shouldNotContain("CompileCommand: MemStat"); // should *NOT* be registered
        }
    }
}
