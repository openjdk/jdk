/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import org.graalvm.compiler.bytecode.Bytecodes;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MiscUtils {

    /**
     * Name a java method with class and signature to make it unique.
     *
     * @param method to generate unique identifier for
     * @return Unique name for this method including class and signature
     **/
    public static String uniqueMethodName(ResolvedJavaMethod method) {
        String className = method.getDeclaringClass().toClassName();
        String name = className + "." + method.getName() + method.getSignature().toMethodDescriptor();
        return name;
    }

    public static boolean isStaticCall(Call call) {
        if (isJavaCall(call)) {
            return ((getByteCode(call) & 0xFF) == Bytecodes.INVOKESTATIC);
        }
        return false;
    }

    public static boolean isSpecialCall(Call call) {
        if (isJavaCall(call)) {
            return ((getByteCode(call) & 0xFF) == Bytecodes.INVOKESPECIAL);
        }
        return false;
    }

    private static boolean isInvokeVirtual(Call call) {
        if (isJavaCall(call)) {
            return ((getByteCode(call) & 0xFF) == Bytecodes.INVOKEVIRTUAL) || ((getByteCode(call) & 0xFF) == Bytecodes.INVOKEINTERFACE);
        }
        return false;
    }

    public static boolean isVirtualCall(CompiledMethodInfo methodInfo, Call call) {
        return isInvokeVirtual(call) && !methodInfo.hasMark(call, MarkId.INVOKESPECIAL);
    }

    public static boolean isOptVirtualCall(CompiledMethodInfo methodInfo, Call call) {
        return isInvokeVirtual(call) && methodInfo.hasMark(call, MarkId.INVOKESPECIAL);
    }

    private static boolean isJavaCall(Call call) {
        // If there is no associated debug info return false
        if (call.debugInfo == null) {
            return false;
        }
        BytecodePosition bcpos = call.debugInfo.getBytecodePosition();
        ResolvedJavaMethod method = bcpos.getMethod();
        // If bytecode position indicates a special value (negative value) it is
        // not a normal java call
        if (bcpos.getBCI() < 0) {
            return false;
        }
        // If there is no method associated with the debuginfo, return false
        if (method == null) {
            return false;
        }
        assert (method instanceof HotSpotResolvedJavaMethod) : "Not a resolved Java call";
        return true;
    }

    private static byte getByteCode(Call call) {
        ResolvedJavaMethod m = call.debugInfo.getBytecodePosition().getMethod();
        int callPosition = call.debugInfo.getBytecodePosition().getBCI();
        byte[] code = m.getCode();
        return code[callPosition];
    }

}
