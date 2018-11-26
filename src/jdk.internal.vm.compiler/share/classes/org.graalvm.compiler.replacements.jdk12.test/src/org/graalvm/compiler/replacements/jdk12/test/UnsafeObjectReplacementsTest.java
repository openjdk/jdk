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


package org.graalvm.compiler.replacements.jdk12.test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;

/**
 * As of JDK 12 {@code jdk.internal.misc.Unsafe::.*Object()} methods were renamed to
 * {@code .*Reference()}.
 *
 * @see "https://bugs.openjdk.java.net/browse/JDK-8207146"
 */
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

    public static Object unsafeCompareAndExchangeReference() {
        Container container = new Container();
        return unsafe.compareAndExchangeReference(container, objectOffset, dummyValue, newDummyValue);
    }

    @Test
    public void testCompareAndSet() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AMD64) {
            testGraph("unsafeCompareAndExchangeReference");
        }
        test("unsafeCompareAndExchangeReference");
    }

    public static Object unsafeGetAndSetReference() {
        Container container = new Container();
        container.objectField = null;
        Container other = new Container();
        return unsafe.getAndSetReference(container, objectOffset, other);
    }

    @Test
    public void testGetAndSet() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AMD64 || target.arch instanceof AArch64) {
            testGraph("unsafeGetAndSetReference");
        }
        test("unsafeGetAndSetReference");
    }

    public static Object unsafeGetPutReference() {
        Container container = new Container();
        unsafe.putReference(container, objectOffset, "Hello there");
        return unsafe.getReference(container, objectOffset);
    }

    public static Object unsafeGetPutReferenceOpaque() {
        Container container = new Container();
        unsafe.putReferenceOpaque(container, objectOffset, "Hello there");
        return unsafe.getReferenceOpaque(container, objectOffset);
    }

    public static Object unsafeGetPutReferenceRA() {
        Container container = new Container();
        unsafe.putReferenceRelease(container, objectOffset, "Hello there");
        return unsafe.getReferenceAcquire(container, objectOffset);
    }

    public static Object unsafeGetPutReferenceVolatile() {
        Container container = new Container();
        unsafe.putReferenceVolatile(container, objectOffset, "Hello there");
        return unsafe.getReferenceVolatile(container, objectOffset);
    }

    @Test
    public void testUnsafeGetPutPlain() {
        testGraph("unsafeGetPutReference");
        test("unsafeGetPutReference");
    }

    @Test
    public void testUnsafeGetPutOpaque() {
        testGraph("unsafeGetPutReferenceOpaque");
        test("unsafeGetPutReferenceOpaque");
    }

    @Test
    public void testUnsafeGetPutReleaseAcquire() {
        testGraph("unsafeGetPutReferenceRA");
        test("unsafeGetPutReferenceRA");
    }

    @Test
    public void testUnsafeGetPutVolatile() {
        testGraph("unsafeGetPutReferenceVolatile");
        test("unsafeGetPutReferenceVolatile");
    }
}
