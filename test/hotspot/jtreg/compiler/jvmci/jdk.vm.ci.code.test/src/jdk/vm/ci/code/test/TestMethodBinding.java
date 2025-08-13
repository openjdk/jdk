/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.jvmci
 * @requires vm.simpleArch == "x64"
 * @library /
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot
 * jdk.internal.vm.ci/jdk.vm.ci.meta
 * jdk.internal.vm.ci/jdk.vm.ci.code
 * jdk.internal.vm.ci/jdk.vm.ci.code.site
 * jdk.internal.vm.ci/jdk.vm.ci.runtime
 * jdk.internal.vm.ci/jdk.vm.ci.aarch64
 * jdk.internal.vm.ci/jdk.vm.ci.amd64
 * jdk.internal.vm.ci/jdk.vm.ci.riscv64
 * @compile CodeInstallationTest.java DebugInfoTest.java TestAssembler.java TestHotSpotVMConfig.java amd64/AMD64TestAssembler.java aarch64/AArch64TestAssembler.java riscv64/RISCV64TestAssembler.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.code.test.TestMethodBinding
 */

package jdk.vm.ci.code.test;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;

import jdk.vm.ci.code.*;
import jdk.vm.ci.hotspot.HotSpotReferenceMap;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.junit.Test;

import jdk.vm.ci.meta.JavaType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TestMethodBinding extends CodeInstallationTest {


    static class MethodProvider {
        public static int invokeStatic() {
            return 1 + 2;
        }
    }

    @Test
    public void test() {
        Class<?> returnType = int.class;
        Class<?>[] staticArgumentTypes = new Class<?>[]{};
        Object[] staticArguments = new Object[]{};

        test(getMethod(MethodProvider.class, "invokeStatic"), returnType, staticArgumentTypes, staticArguments, config.MARKID_INVOKESTATIC);
    }


    public void test(Method method, Class<?> returnClazz, Class<?>[] types, Object[] values, int markId) {
        try {
            ResolvedJavaMethod resolvedMethod = metaAccess.lookupJavaMethod(method);
            Object obj = null;
            List<Object> args = new ArrayList<>(List.of(values));
            if (!resolvedMethod.isStatic()) {
                obj = values[0];
                args.removeFirst();
            }
            test(asm -> {
                JavaType[] argTypes = new JavaType[types.length];
                int i = 0;
                for (Class<?> clazz : types) {
                    argTypes[i++] = metaAccess.lookupJavaType(clazz);
                }
                JavaType returnType = metaAccess.lookupJavaType(returnClazz);
                CallingConvention cc = codeCache.getRegisterConfig().getCallingConvention(JavaCall, returnType, argTypes, asm.valueKindFactory);
                asm.emitCallPrologue(cc, values);

                asm.recordMark(markId);
                int[] pos = new int[2];
                // duringCall has to be false to trigger our bind logic in SharedRuntime::find_callee_info_helper
                // we are allowed to do this because the call has no side-effect
                BytecodeFrame frame = new BytecodeFrame(null, resolvedMethod, 0, false, false, new JavaValue[0], new JavaKind[0], 0, 0, 0);
                DebugInfo info = new DebugInfo(frame, new VirtualObject[0]);
                if (resolvedMethod.isStatic()) {
                    info.setReferenceMap(new HotSpotReferenceMap(new Location[0], new Location[0], new int[0], 8));
                } else {
                    assert values.length == 1 : "Only a receiver argument is allowed";
                    info.setReferenceMap(new HotSpotReferenceMap(new Location[]{Location.register(((RegisterValue) cc.getArgument(0)).getRegister())}, new Location[]{null}, new int[]{asm.config.heapWordSize}, 8));
                }
                asm.emitJavaCall(pos, info, markId);

                asm.recordCall(pos[0], pos[1], resolvedMethod, true, true, info);
                asm.emitCallEpilogue(cc);
                if (returnClazz == float.class) {
                    asm.emitFloatRet(((RegisterValue) cc.getReturn()).getRegister());
                } else if (returnClazz == int.class) {
                    asm.emitIntRet(((RegisterValue) cc.getReturn()).getRegister());
                } else {
                    assert false : "Unimplemented return type: " + returnClazz;
                }

            }, method, obj, args.toArray(new Object[args.size()]));
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}