/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8313713
 * @summary Test if the following CompileCommand options support compilation
 *          level bitmask argument: break, compileonly, exclude, print
 * @library /test/lib
 * @run main ${test.main.class}
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.process.ProcessTools;

import java.util.List;

public class CompileLevelParseTest {
    private static final List<String> commandsWithCompileLevel = List.of("break", "compileonly", "exclude", "print");
    private static final List<String> compLevels = List.of("0", "1", "11", "111", "10", "100", "101", "1000", "1111");
    private static final List<String> invalidCompLevels = List.of("-9223372036854775808", "-1", "-1111", "10000", "2", "20000", "01012",
            "91", "9", "c1", "true", "false");
    private static final String DEFAULT_COMP_LEVEL = "1111";
    private static final String METHOD_EXP = "java/lang/Object.toString";

    public static void main(String[] args) throws Exception {
        for (String cmd : commandsWithCompileLevel) {
            ProcessTools.executeTestJava("-XX:CompileCommand=" + cmd + "," + METHOD_EXP, "-version")
                    .shouldHaveExitValue(0)
                    .shouldNotContain("CompileCommand: An error occurred during parsing")
                    .shouldContain("CompileCommand: " + cmd + " " + METHOD_EXP + " intx " + cmd + " = " + DEFAULT_COMP_LEVEL); // should be registered
            for (String level : compLevels) {
                ProcessTools.executeTestJava("-XX:CompileCommand=" + cmd + "," + METHOD_EXP + "," + level, "-version")
                        .shouldHaveExitValue(0)
                        .shouldNotContain("CompileCommand: An error occurred during parsing")
                        .shouldContain("CompileCommand: " + cmd + " " + METHOD_EXP + " intx " + cmd + " = " + level); // should be registered
            }
            // Note that values like "1suffix" are still accepted
            for (String incorrectLevel : invalidCompLevels) {
                ProcessTools.executeTestJava("-XX:CompileCommand=" + cmd + "," + METHOD_EXP + "," +incorrectLevel, "-version")
                        .shouldHaveExitValue(1)
                        .shouldContain("CompileCommand: An error occurred during parsing")
                        .shouldNotContain("CompileCommand: " + cmd + " " + METHOD_EXP + " intx " + cmd + " = " + incorrectLevel);
            }
        }
    }
}
