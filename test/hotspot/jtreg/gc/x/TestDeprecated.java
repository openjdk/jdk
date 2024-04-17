/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package gc.x;

/*
 * @test TestDeprecated
 * @requires vm.gc.ZSinglegen
 * @summary Test ZGenerational Deprecated
 * @library /test/lib
 * @run driver gc.x.TestDeprecated
 */

import java.util.LinkedList;
import jdk.test.lib.process.ProcessTools;

public class TestDeprecated {
    static class Test {
        public static void main(String[] args) throws Exception {}
    }
    public static void main(String[] args) throws Exception {
        ProcessTools.executeLimitedTestJava("-XX:+UseZGC",
                                            "-XX:-ZGenerational",
                                            "-Xlog:gc+init",
                                            Test.class.getName())
                    .shouldContain("Option ZGenerational was deprecated")
                    .shouldContain("Using deprecated non-generational mode")
                    .shouldHaveExitValue(0);
    }
}
