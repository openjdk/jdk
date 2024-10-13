/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package compiler.runtime.unloaded;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiPredicate;
import jdk.internal.org.objectweb.asm.ClassWriter;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

// Operates in bootstrap loader context.
public class TestMHUnloadedHelper {
    private static final MethodType METHOD_TYPE = MethodType.methodType(BiPredicate.class,
                                                                        BiPredicate.class, BiPredicate.class);

    static byte[] generateClassFile(Class<?> caller) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String name = caller.getName().replace('.', '/');
        cw.visit(V19, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);
        {
            var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", METHOD_TYPE.toMethodDescriptorString(), null, null);
            mv.visitCode();
            mv.visitIntInsn(ALOAD, 1);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
        }
        return cw.toByteArray();
    }

    public static MethodHandle generateTest(MethodHandles.Lookup caller) {
        // Loaded in the caller context.
        byte[] classBytes = generateClassFile(caller.lookupClass());
        try {
            MethodHandles.Lookup lookup = caller.defineHiddenClass(classBytes, true);
            MethodHandle test = lookup.findStatic(lookup.lookupClass(), "test", METHOD_TYPE);
            test = MethodHandles.permuteArguments(test, test.type(), 1, 0); // mix arguments
            return test;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    static BiPredicate[] ps = new BiPredicate[] { (a, b) -> false,
                                                  (a, b) -> true };

    public static void test(MethodHandles.Lookup caller) {
        MethodHandle test = generateTest(caller);

        for (int i = 0; i < 20_000; i++) {
            try {
                BiPredicate pr = (BiPredicate)test.invokeExact(ps[1], ps[0]);
                if (pr != ps[1]) {
                    throw new AssertionError("mismatch");
                }
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void testConstant(MethodHandles.Lookup caller) {
        MethodHandle test = generateTest(caller);

        // testMH() { return test(ps2, ps1); } where test(a, b) { return b; }.
        test = test.bindTo(ps[1]).bindTo(ps[0]); // make argument concrete types visible to the JIT-compiler

        for (int i = 0; i < 20_000; i++) {
            try {
                BiPredicate pr = (BiPredicate)test.invokeExact();
                if (pr != ps[1]) {
                    throw new AssertionError("mismatch");
                }
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }
    }
}
