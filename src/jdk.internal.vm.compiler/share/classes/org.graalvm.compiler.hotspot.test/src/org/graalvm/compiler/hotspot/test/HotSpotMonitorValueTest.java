/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotMonitorValueTest extends GraalCompilerTest {

    @Override
    protected InstalledCode addMethod(DebugContext debug, ResolvedJavaMethod method, CompilationResult compResult) {
        for (Infopoint i : compResult.getInfopoints()) {
            if (i instanceof Call) {
                Call call = (Call) i;
                if (call.target instanceof ResolvedJavaMethod) {
                    ResolvedJavaMethod target = (ResolvedJavaMethod) call.target;
                    if (target.equals(lookupObjectWait())) {
                        BytecodeFrame frame = call.debugInfo.frame();
                        BytecodeFrame caller = frame.caller();
                        assertNotNull(caller);
                        assertNull(caller.caller());
                        assertDeepEquals(2, frame.numLocks);
                        assertDeepEquals(2, caller.numLocks);
                        StackLockValue lock1 = (StackLockValue) frame.getLockValue(0);
                        StackLockValue lock2 = (StackLockValue) frame.getLockValue(1);
                        StackLockValue lock3 = (StackLockValue) caller.getLockValue(0);
                        StackLockValue lock4 = (StackLockValue) caller.getLockValue(1);

                        List<StackLockValue> locks = Arrays.asList(lock1, lock2, lock3, lock4);
                        for (StackLockValue lock : locks) {
                            for (StackLockValue other : locks) {
                                if (other != lock) {
                                    // Every lock must have a different stack slot
                                    assertThat(lock.getSlot(), not(other.getSlot()));
                                }
                            }
                        }
                        assertDeepEquals(lock3.getOwner(), lock4.getOwner());
                        assertThat(lock1.getOwner(), not(lock2.getOwner()));
                        return super.addMethod(debug, method, compResult);
                    }
                }
            }
        }
        throw new AssertionError("Could not find debug info for call to Object.wait(long)");
    }

    private ResolvedJavaMethod lookupObjectWait() {
        try {
            return getMetaAccess().lookupJavaMethod(Object.class.getDeclaredMethod("wait", long.class));
        } catch (Exception e) {
            throw new GraalError("Could not find Object.wait(long): %s", e);
        }
    }

    @Test
    public void test() {
        test("testSnippet", "a", "b");
    }

    private static void locks2(Object a, Object b) throws InterruptedException {
        synchronized (a) {
            synchronized (b) {
                a.wait(5);
            }
        }
    }

    public static void testSnippet(Object a, Object b) throws InterruptedException {
        synchronized (a) {
            synchronized (a) {
                locks2(a, b);
            }
        }
    }
}
