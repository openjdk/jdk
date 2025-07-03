/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.flag;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import static compiler.lib.ir_framework.CompilePhase.*;

/*
 * @test
 * @requires vm.flagless
 * @summary Test compile command file writer.
 * @library /test/lib /testlibrary_tests /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm -Xbootclasspath/a:. -DSkipWhiteBoxInstall=true -XX:+UnlockDiagnosticVMOptions
 *                    -XX:+WhiteBoxAPI compiler.lib.ir_framework.flag.TestCompileCommandFileWriter
 */
public class TestCompileCommandFileWriter {

    @org.junit.Test
    public void testIdeal() throws IOException {
        check(IdealOnly1.class, true, false);
        check(IdealOnly2.class, true, false);
    }

    @org.junit.Test
    public void testOpto() throws IOException {
        check(OptoOnly1.class, false, true);
        check(OptoOnly2.class, false, true);
    }

    @org.junit.Test
    public void testBoth() throws IOException {
        check(Both1.class, true, true);
        check(Both2.class, true, true);
    }

    @org.junit.Test
    public void testOtherOnly() throws IOException {
        check(OtherOnly1.class, false,false, AFTER_PARSING);
        check(OtherOnly2.class, false,false, AFTER_PARSING, FINAL_CODE);
    }

    @org.junit.Test
    public void testMix() throws IOException {
        check(Mix1.class, true,false, AFTER_PARSING);
        check(Mix2.class, false,true, AFTER_PARSING);
        check(Mix3.class, true,true, AFTER_PARSING);
        check(Mix4.class, true,true, AFTER_PARSING);
    }

    private void check(Class<?> testClass, boolean findIdeal, boolean findOpto, CompilePhase... compilePhases) throws IOException {
        var compilerDirectivesFlagBuilder = new CompilerDirectivesFlagBuilder(testClass);
        compilerDirectivesFlagBuilder.build();
        try (Scanner scanner = new Scanner(Paths.get(FlagVM.TEST_VM_COMPILE_COMMANDS_FILE))) {
            boolean foundIdeal = false;
            boolean foundOpto = false;
            boolean foundPhase = false;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (isPrintIdeal(line)) {
                    foundIdeal = true;
                } else if (isPrintOptoAssembly(line)) {
                    foundOpto = true;
                } else if (isPrintIdealPhase(line, compilePhases)) {
                    foundPhase = true;
                }
            }
            Assert.assertEquals("PrintIdeal mismatch", findIdeal, foundIdeal);
            Assert.assertEquals("PrintOptoAssembly mismatch", findOpto, foundOpto);
            Assert.assertEquals("PrintIdealPhase mismatch", compilePhases.length > 0, foundPhase);
        }
    }

    private boolean isPrintIdeal(String line) {
        return line.contains("PrintIdeal : true");
    }

    private boolean isPrintOptoAssembly(String line) {
        return line.contains("PrintOptoAssembly : true");
    }

    private boolean isPrintIdealPhase(String line, CompilePhase... compilePhases) {
        if (!line.contains("PrintIdealPhase : \"")) {
            return false;
        }
        return Arrays.stream(compilePhases).allMatch(compilePhase -> line.contains(compilePhase.name()));
    }

    static class IdealOnly1 {
        @Test
        @IR(failOn = IRNode.STORE)
        public void test() {
        }
    }

    static class IdealOnly2 {
        @Test
        @IR(failOn = IRNode.STORE, phase = PRINT_IDEAL)
        public void test() {
        }
    }

    static class OptoOnly1 {
        @Test
        @IR(failOn = IRNode.FIELD_ACCESS)
        public void test() {
        }
    }

    static class OptoOnly2 {
        @Test
        @IR(failOn = IRNode.FIELD_ACCESS, phase = PRINT_OPTO_ASSEMBLY)
        public void test() {
        }
    }

    static class Both1 {
        @Test
        @IR(failOn = IRNode.STORE)
        @IR(failOn = IRNode.FIELD_ACCESS)
        public void test() {
        }
    }

    static class Both2 {
        @Test
        @IR(failOn = {IRNode.STORE, IRNode. FIELD_ACCESS})
        public void test() {
        }
    }

    static class OtherOnly1 {
        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        public void test() {
        }
    }

    static class OtherOnly2 {
        @Test
        @IR(failOn = IRNode.STORE, phase = {AFTER_PARSING, FINAL_CODE})
        public void test() {
        }
    }

    static class Mix1 {
        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        @IR(failOn = IRNode.STORE, phase = PRINT_IDEAL)
        public void test() {
        }
    }

    static class Mix2 {
        @Test
        @IR(failOn = IRNode.FIELD_ACCESS, phase = AFTER_PARSING)
        @IR(failOn = IRNode.STORE, phase = PRINT_OPTO_ASSEMBLY)
        public void test() {
        }
    }

    static class Mix3 {
        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        @IR(failOn = IRNode.STORE, phase = PRINT_IDEAL)
        @IR(failOn = IRNode.FIELD_ACCESS, phase = PRINT_OPTO_ASSEMBLY)
        public void test() {
        }
    }

    static class Mix4 {
        @Test
        @IR(failOn = IRNode.STORE, phase = {AFTER_PARSING, PRINT_IDEAL})
        @IR(failOn = IRNode.FIELD_ACCESS, phase = PRINT_OPTO_ASSEMBLY)
        public void test() {
        }
    }
}


