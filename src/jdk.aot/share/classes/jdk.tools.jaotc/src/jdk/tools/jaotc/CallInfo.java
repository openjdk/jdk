/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

final class CallInfo {

    static boolean isStaticTarget(Call call) {
        return !((HotSpotResolvedJavaMethod) call.target).hasReceiver();
    }

    private static boolean isStaticOpcode(Call call) {
        int opcode = getByteCode(call) & 0xFF;
        return opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKEDYNAMIC || opcode == Bytecodes.INVOKEVIRTUAL /* invokehandle */;
    }

    static boolean isStaticCall(Call call) {
        if (isJavaCall(call) && isStaticTarget(call)) {
            assert isStaticOpcode(call);
            return true;
        }
        return false;
    }

    static boolean isSpecialCall(Call call) {
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

    static boolean isVirtualCall(CompiledMethodInfo methodInfo, Call call) {
        return isInvokeVirtual(call) && !methodInfo.hasMark(call, MarkId.INVOKESPECIAL) && !isStaticTarget(call);
    }

    static boolean isOptVirtualCall(CompiledMethodInfo methodInfo, Call call) {
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
