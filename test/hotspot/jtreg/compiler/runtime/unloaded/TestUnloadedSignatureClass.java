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

/*
 * @test
 * @library /test/lib
 * @run driver compiler.runtime.unloaded.TestUnloadedSignatureClass
 */

package compiler.runtime.unloaded;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestUnloadedSignatureClass {
    static class Test {
        static int test(Integer i) {
            // Bound to a wrapper around a method with (Ljava/lang/Object;ILjava/util/function/BiPredicate;Ljava/util/List;)I signature.
            // Neither BiPredicate nor List are guaranteed to be resolved by the context class loader.
            return switch (i) {
                case null -> -1;
                case 0    ->  0;
                default   ->  1;
            };
        }

        public static void main(String[] args) {
            for (int i = 0; i < 20_000; i++) {
                test(i);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-Xbatch", "-XX:-TieredCompilation",
                "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly,*::test",
                "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintCompilation", "-XX:+PrintInlining",
                Test.class.getName()
        );

        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
        out.shouldNotContain("unloaded signature classes");
    }
}
