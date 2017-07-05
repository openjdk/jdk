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
 *
 */

/**
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library /testlibrary /
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *      -Dcompiler.jvmci.JVM_GetJVMCIRuntimeTest.positive=true
 *      -XX:+EnableJVMCI
 *      compiler.jvmci.JVM_GetJVMCIRuntimeTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *      -Dcompiler.jvmci.JVM_GetJVMCIRuntimeTest.positive=false
 *      -XX:-EnableJVMCI
 *      compiler.jvmci.JVM_GetJVMCIRuntimeTest

 */

package compiler.jvmci;

import jdk.vm.ci.runtime.JVMCI;
import jdk.test.lib.Asserts;

import java.lang.reflect.Method;

public class JVM_GetJVMCIRuntimeTest {
    private static final boolean IS_POSITIVE = Boolean.getBoolean(
            "compiler.jvmci.JVM_GetJVMCIRuntimeTest.positive");

    private final Method initializeRuntime;

    public static void main(String[] args) {
        new JVM_GetJVMCIRuntimeTest().runTest();
    }

    private void runTest() {
        Object result;
        try {
            result = invoke();
        } catch (InternalError e) {
            if (IS_POSITIVE) {
                throw new AssertionError("unexpected exception", e);
            }
            return;
        }
        if (!IS_POSITIVE) {
            throw new AssertionError("didn't get expected exception");
        }
        Asserts.assertNotNull(result,
                "initializeRuntime returned null");
        Asserts.assertEQ(result, invoke(),
                "initializeRuntime returns different results");

    }
    private Object invoke() {
        Object result;
        try {
            result = initializeRuntime.invoke(JVMCI.class);
        } catch (ReflectiveOperationException e) {
            throw new Error("can't invoke initializeRuntime", e);
        }
        return result;
    }

    private JVM_GetJVMCIRuntimeTest() {
        Method method;
        try {
            method = JVMCI.class.getDeclaredMethod("initializeRuntime");
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new Error("can't find JVMCI::initializeRuntime", e);
        }
        initializeRuntime = method;
    }
}
