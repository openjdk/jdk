/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.util.Map;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8294713
 * @summary Verify that simple boolean expressions do not generate convoluted bytecode
 * @library /test/lib
 * @run junit GenCondReturn
 */
public class GenCondReturn {
    private static final String SOURCE = """
            public class Test {
                public boolean inRange(int start, int end, int value) {
                    return (value > start && value < end);
                }
            }
            """;

    @Test
    public void testNoGotoInSimpleBoolean() {
        var testBytes = InMemoryJavaCompiler.compile(Map.of("Test", SOURCE)).get("Test");
        var code = ClassFile.of().parse(testBytes).methods().stream()
                .filter(m -> m.methodName().equalsString("inRange")).findFirst()
                .orElseThrow().findAttribute(Attributes.code()).orElseThrow();
        code.forEach(ce -> {
            if (ce instanceof Instruction ins && (ins.opcode() == Opcode.GOTO || ins.opcode() == Opcode.GOTO_W)) {
                fail("Unexpected jump" + ins.opcode() + "found");
            }
        });
    }
}
