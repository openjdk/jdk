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

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.util.BitSet;
import java.util.Map;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8357185
 * @enablePreview
 * @summary No unused local variable in unconditionally exact primitive patterns
 * @library /test/lib
 * @run junit PrimitiveInstanceOfBytecodeTest
 */
public class PrimitiveInstanceOfBytecodeTest {

    private static final String SOURCE = """
            public class Test {
                public record A(int i) {}
                public Integer get(A a) {
                    if (a instanceof A(int i)) {
                        return i;
                    }
                    return null;
                }
            }
            """;

    @Test
    public void testNoUnusedVarInRecordPattern() {
        var testBytes = InMemoryJavaCompiler.compile(Map.of("Test", SOURCE)).get("Test");
        var code = ClassFile.of().parse(testBytes).methods().stream()
                .filter(m -> m.methodName().equalsString("get")).findFirst()
                .orElseThrow().findAttribute(Attributes.code()).orElseThrow();
        BitSet stores = new BitSet(code.maxLocals());
        BitSet loads = new BitSet(code.maxLocals());
        code.forEach(ce -> {
            switch (ce) {
                case StoreInstruction store -> stores.set(store.slot());
                case LoadInstruction load -> loads.set(load.slot());
                default -> {}
            }
        });
        // [this, a] are built-in locals that may be unused
        loads.clear(0, 2);
        stores.clear(0, 2);
        if (!loads.equals(stores)) {
            System.err.println("Loads: " + loads);
            System.err.println("Stores: " + stores);
            System.err.println(code.toDebugString());
            fail("Store and load mismatch, see stderr");
        }
    }
}
