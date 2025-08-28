/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.arguments;

/*
 * @test TestCompileTaskTimeout
 * @bug 8308094 8365909
 * @requires vm.debug & vm.flagless & os.name == "Linux"
 * @summary Check functionality of CompileTaskTimeout
 * @library /test/lib
 * @run driver compiler.arguments.TestCompileTaskTimeout
 */

import jdk.test.lib.process.ProcessTools;

public class TestCompileTaskTimeout {

    public static void main(String[] args) throws Throwable {
        ProcessTools.executeTestJava("-Xcomp", "-XX:CompileTaskTimeout=1", "--version")
                    .shouldHaveExitValue(134)
                    .shouldContain("timed out after");

        ProcessTools.executeTestJava("-Xcomp", "-XX:CompileTaskTimeout=1", "-XX:TieredStopAtLevel=3", "--version")
                    .shouldHaveExitValue(134)
                    .shouldContain("timed out after");

        ProcessTools.executeTestJava("-Xcomp", "-XX:CompileTaskTimeout=1", "-XX:-TieredCompilation", "--version")
                    .shouldHaveExitValue(134)
                    .shouldContain("timed out after");

        ProcessTools.executeTestJava("-Xcomp", "-XX:CompileTaskTimeout=2000", "--version")
                    .shouldHaveExitValue(0);
    }
}
