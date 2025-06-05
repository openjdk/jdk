/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8332245
 * @summary C2: missing record_for_ign() call in GraphKit::must_be_not_null()
 *
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestBackToBackMustBeNotNull
 */

package compiler.c2.irTests;
import jdk.internal.misc.Unsafe;
import java.lang.reflect.Field;
import compiler.lib.ir_framework.*;


public class TestBackToBackMustBeNotNull {
    static final jdk.internal.misc.Unsafe UNSAFE = Unsafe.getUnsafe();
    static final long F_OFFSET;
    private static A fieldA = new A();

    static {
        try {
            Field fField = A.class.getDeclaredField("f");
            F_OFFSET = UNSAFE.objectFieldOffset(fField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static public void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
    }

    @Test
    @IR(phase = { CompilePhase.ITER_GVN1 }, counts = { IRNode.IF, "1" })
    private static void test1() {
        final Object o1 = UNSAFE.getReference(fieldA, F_OFFSET);
        final Object o2 = UNSAFE.getReference(fieldA, F_OFFSET);
        notInlined(o1, o2);
    }

    @DontInline
    private static void notInlined(Object o1, Object o2) {

    }

    static class A {
        Object f;
    }
}
