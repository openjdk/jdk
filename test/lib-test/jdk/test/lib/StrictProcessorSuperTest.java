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

/* @test
 * @bug 8351362
 * @summary Unit Test for StrictProcessor super rewrite
 * @enablePreview
 * @library /test/lib
 * @compile StrictProcessorSuperTest.java
 * @run driver jdk.test.lib.helpers.StrictProcessor --deferSuperCall
 *             StrictProcessorSuperTest$Rec StrictProcessorSuperTest$Exp
 *             StrictProcessorSuperTest$Inner
 * @run junit StrictProcessorSuperTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.stream.Stream;

import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static org.junit.jupiter.api.Assertions.*;

class StrictProcessorSuperTest {
    static Stream<Class<?>> testClasses() {
        return Stream.of(Rec.class, Exp.class, Inner.class);
    }

    static Stream<ClassModel> testClassModels() {
        return testClasses().map(cls -> {
            try (var in = StrictProcessorSuperTest.class.getResourceAsStream("/" + cls.getName() + ".class")) {
                return ClassFile.of().parse(in.readAllBytes());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    @MethodSource("testClasses")
    @ParameterizedTest
    void testReflectRewrittenRecord(Class<?> cls) throws Throwable {
        for (var field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                continue;
            assertEquals(ACC_PRIVATE | ACC_STRICT_INIT | ACC_FINAL, field.getModifiers(), () -> "For field: " + field.getName());
        }
    }

    @MethodSource("testClassModels")
    @ParameterizedTest
    void testRewrittenStrictAccessInClassFile(ClassModel cm) throws Throwable {
        for (var f : cm.fields()) {
            if (f.flags().has(AccessFlag.STATIC) || f.flags().has(AccessFlag.SYNTHETIC))
                continue;
            assertEquals(ACC_PRIVATE | ACC_STRICT_INIT | ACC_FINAL, f.flags().flagsMask(), () -> "Field " + f);
        }
    }

    @MethodSource("testClassModels")
    @ParameterizedTest
    void testRewrittenCtorBytecode(ClassModel cm) throws Throwable {
        var ctor = cm.methods().stream().filter(m -> m.methodName().equalsString(INIT_NAME)).findFirst().orElseThrow();
        var insts = new ArrayList<Instruction>();
        ctor.findAttribute(Attributes.code()).orElseThrow().forEach(ce -> {
            if (ce instanceof Instruction inst) {
                insts.add(inst);
            }
        });
        assertSame(Opcode.RETURN, insts.getLast().opcode());
        assertSame(Opcode.INVOKESPECIAL, insts.get(insts.size() - 2).opcode());
    }

    record Rec(@StrictInit int a, @StrictInit long b) {
        static final String NOISE = "noise";
    }

    static class Exp {
        private @StrictInit final int a;
        private @StrictInit final long b;

        Exp(int a, long b) {
            this.a = a;
            this.b = b;
        }
    }

    class Inner {
        private @StrictInit final int a;
        private @StrictInit final long b;

        Inner(int a, long b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public String toString() {
            return a + " " + StrictProcessorSuperTest.this + " " + b;
        }
    }
}
