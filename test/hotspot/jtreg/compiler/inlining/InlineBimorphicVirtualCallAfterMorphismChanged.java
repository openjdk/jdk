/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8339299
 * @summary C2 doesn't perform bimorphic inlining on a call site that was monomorphic during tier 3 compilation.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.flavor == "server" & !vm.emulatedClient
 *
 * @run driver compiler.inlining.InlineBimorphicVirtualCallAfterMorphismChanged
 */

package compiler.inlining;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class InlineBimorphicVirtualCallAfterMorphismChanged {
    public static abstract class AbstractBase {
        public final int callSiteHolder() {
            return inlinee();
        }

        public abstract int inlinee();

        public static void main(String[] args) {
            AbstractBase[] classes = new AbstractBase[] { firstInstance() };
            // first step: trigger a compilation while call site is monomorphic
            for (int i = 0; i < 10000; i++) {
                for (AbstractBase instance : classes) {
                    instance.callSiteHolder();
                }
            }

            // second step: trigger recompilation by loading a second instance,
            // also make the call site bimorphic
            classes = new AbstractBase[] { firstInstance(), secondInstance() };
            for (int i = 0; i < 10000; i++) {
                for (AbstractBase instance : classes) {
                    instance.callSiteHolder();
                }
            }
        }

        private static AbstractBase firstInstance() {
            return new FirstClass();
        }

        private static AbstractBase secondInstance() {
            return new SecondClass();
        }
    }

    public final static class FirstClass extends AbstractBase {
        public int inlinee() {
            return 1;
        }
    }

    public final static class SecondClass extends AbstractBase {
        public int inlinee() {
            return 2;
        };
    }

    public static void main(String[] args) throws Exception {
        test("-XX:-TieredCompilation");
        test("-XX:+TieredCompilation");
    }

    private static void test(String option) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
            "-XX:CompileCommand=compileonly,*::callSiteHolder", option,
            AbstractBase.class.getName()
        );

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);

        String re = ".*AbstractBase::inlinee.+virtual call.*";
        boolean virtualInliningFailed = analyzer.asLines().stream()
                .anyMatch(s -> s.matches(re));

        if (virtualInliningFailed) {
            analyzer.outputTo(System.out);
            throw new Exception(
                "Bimorphic virtual call was not inlined with '" + option + "'"
            );
        }
    }
}
