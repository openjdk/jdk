/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package compiler.jvmci.code;

import java.lang.reflect.Method;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Location;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.hotspot.HotSpotReferenceMap;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test code installation with debug information.
 */
public class DebugInfoTest extends CodeInstallationTest {

    protected interface DebugInfoCompiler {

        VirtualObject[] compile(TestAssembler asm, JavaValue[] frameValues);
    }

    protected void test(DebugInfoCompiler compiler, Method method, int bci, JavaKind... slotKinds) {
        ResolvedJavaMethod resolvedMethod = metaAccess.lookupJavaMethod(method);

        int numLocals = resolvedMethod.getMaxLocals();
        int numStack = slotKinds.length - numLocals;
        JavaValue[] values = new JavaValue[slotKinds.length];
        test(asm -> {
            VirtualObject[] vobjs = compiler.compile(asm, values);

            BytecodeFrame frame = new BytecodeFrame(null, resolvedMethod, bci, false, false, values, slotKinds, numLocals, numStack, 0);
            DebugInfo info = new DebugInfo(frame, vobjs);
            info.setReferenceMap(new HotSpotReferenceMap(new Location[0], new Location[0], new int[0], 8));

            asm.emitTrap(info);
        }, method);
    }
}
