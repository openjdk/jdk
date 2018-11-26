/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.jdk9_11.test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeObjectReplacementsTest extends MethodSubstitutionTest {

    static class Container {
        public volatile Object objectField = dummyValue;
    }

    static jdk.internal.misc.Unsafe unsafe = jdk.internal.misc.Unsafe.getUnsafe();
    static Container dummyValue = new Container();
    static Container newDummyValue = new Container();
    static long objectOffset;

    static {
        try {
            objectOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("objectField"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object unsafeCompareAndExchangeObject() {
        Container container = new Container();
        return unsafe.compareAndExchangeObject(container, objectOffset, dummyValue, newDummyValue);
    }

    @Test
    public void testCompareAndSet() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AMD64) {
            testGraph("unsafeCompareAndExchangeObject");
        }
        test("unsafeCompareAndExchangeObject");
    }

    public static Object unsafeGetAndSetObject() {
        Container container = new Container();
        container.objectField = null;
        Container other = new Container();
        return unsafe.getAndSetObject(container, objectOffset, other);
    }

    @Test
    public void testGetAndSet() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AMD64 || target.arch instanceof AArch64) {
            testGraph("unsafeGetAndSetObject");
        }
        test("unsafeGetAndSetObject");
    }

    public static Object unsafeGetPutObject() {
        Container container = new Container();
        unsafe.putObject(container, objectOffset, "Hello there");
        return unsafe.getObject(container, objectOffset);
    }

    public static Object unsafeGetPutObjectOpaque() {
        Container container = new Container();
        unsafe.putObjectOpaque(container, objectOffset, "Hello there");
        return unsafe.getObjectOpaque(container, objectOffset);
    }

    public static Object unsafeGetPutObjectRA() {
        Container container = new Container();
        unsafe.putObjectRelease(container, objectOffset, "Hello there");
        return unsafe.getObjectAcquire(container, objectOffset);
    }

    public static Object unsafeGetPutObjectVolatile() {
        Container container = new Container();
        unsafe.putObjectVolatile(container, objectOffset, "Hello there");
        return unsafe.getObjectVolatile(container, objectOffset);
    }

    @Test
    public void testUnsafeGetPutPlain() {
        testGraph("unsafeGetPutObject");
        test("unsafeGetPutObject");
    }

    @Test
    public void testUnsafeGetPutOpaque() {
        testGraph("unsafeGetPutObjectOpaque");
        test("unsafeGetPutObjectOpaque");
    }

    @Test
    public void testUnsafeGetPutReleaseAcquire() {
        testGraph("unsafeGetPutObjectRA");
        test("unsafeGetPutObjectRA");
    }

    @Test
    public void testUnsafeGetPutVolatile() {
        testGraph("unsafeGetPutObjectVolatile");
        test("unsafeGetPutObjectVolatile");
    }
}
