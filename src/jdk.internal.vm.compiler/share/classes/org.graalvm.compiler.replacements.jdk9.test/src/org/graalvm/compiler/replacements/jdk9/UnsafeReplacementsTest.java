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
package org.graalvm.compiler.replacements.jdk9;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeReplacementsTest extends MethodSubstitutionTest {

    // See GR-9819.
    @SuppressWarnings("unused") ResolvedJavaMethod method = null;

    static class Container {
        public volatile boolean booleanField;
        public volatile byte byteField = 17;
        public volatile char charField = 1025;
        public volatile short shortField = 2232;
        public volatile int intField = 0xcafebabe;
        public volatile long longField = 0xdedababafafaL;
        public volatile float floatField = 0.125f;
        public volatile double doubleField = 0.125;
        public volatile Object objectField = dummyValue;
    }

    static jdk.internal.misc.Unsafe unsafe = jdk.internal.misc.Unsafe.getUnsafe();
    static Container dummyValue = new Container();
    static Container newDummyValue = new Container();
    static long booleanOffset;
    static long byteOffset;
    static long charOffset;
    static long shortOffset;
    static long intOffset;
    static long longOffset;
    static long floatOffset;
    static long doubleOffset;
    static long objectOffset;

    static {
        try {
            booleanOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("booleanField"));
            byteOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("byteField"));
            charOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("charField"));
            shortOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("shortField"));
            intOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("intField"));
            longOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("longField"));
            floatOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("floatField"));
            doubleOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("doubleField"));
            objectOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("objectField"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean unsafeCompareAndSetBoolean() {
        Container container = new Container();
        return unsafe.compareAndSetBoolean(container, booleanOffset, false, true);
    }

    public static boolean unsafeCompareAndSetByte() {
        Container container = new Container();
        return unsafe.compareAndSetByte(container, byteOffset, (byte) 17, (byte) 121);
    }

    public static boolean unsafeCompareAndSetChar() {
        Container container = new Container();
        return unsafe.compareAndSetChar(container, charOffset, (char) 1025, (char) 1777);
    }

    public static boolean unsafeCompareAndSetShort() {
        Container container = new Container();
        return unsafe.compareAndSetShort(container, shortOffset, (short) 2232, (short) 12111);
    }

    public static boolean unsafeCompareAndSetInt() {
        Container container = new Container();
        return unsafe.compareAndSetInt(container, intOffset, 0xcafebabe, 0xbabefafa);
    }

    public static boolean unsafeCompareAndSetLong() {
        Container container = new Container();
        return unsafe.compareAndSetLong(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
    }

    public static boolean unsafeCompareAndSetFloat() {
        Container container = new Container();
        return unsafe.compareAndSetFloat(container, floatOffset, 0.125f, 0.25f);
    }

    public static boolean unsafeCompareAndSetDouble() {
        Container container = new Container();
        return unsafe.compareAndSetDouble(container, doubleOffset, 0.125, 0.25);
    }

    public static boolean unsafeCompareAndSetObject() {
        Container container = new Container();
        return unsafe.compareAndSetObject(container, objectOffset, dummyValue, newDummyValue);
    }

    public static boolean unsafeCompareAndExchangeBoolean() {
        Container container = new Container();
        return unsafe.compareAndExchangeBoolean(container, booleanOffset, false, true);
    }

    public static byte unsafeCompareAndExchangeByte() {
        Container container = new Container();
        return unsafe.compareAndExchangeByte(container, byteOffset, (byte) 17, (byte) 31);
    }

    public static char unsafeCompareAndExchangeChar() {
        Container container = new Container();
        return unsafe.compareAndExchangeChar(container, charOffset, (char) 1025, (char) 4502);
    }

    public static short unsafeCompareAndExchangeShort() {
        Container container = new Container();
        return unsafe.compareAndExchangeShort(container, shortOffset, (short) 2232, (short) 8121);
    }

    public static int unsafeCompareAndExchangeInt() {
        Container container = new Container();
        return unsafe.compareAndExchangeInt(container, intOffset, 0xcafebabe, 0xbabefafa);
    }

    public static long unsafeCompareAndExchangeLong() {
        Container container = new Container();
        return unsafe.compareAndExchangeLong(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
    }

    public static float unsafeCompareAndExchangeFloat() {
        Container container = new Container();
        return unsafe.compareAndExchangeFloat(container, floatOffset, 0.125f, 0.25f);
    }

    public static double unsafeCompareAndExchangeDouble() {
        Container container = new Container();
        return unsafe.compareAndExchangeDouble(container, doubleOffset, 0.125, 0.25);
    }

    public static Object unsafeCompareAndExchangeObject() {
        Container container = new Container();
        return unsafe.compareAndExchangeObject(container, objectOffset, dummyValue, newDummyValue);
    }

    @Test
    public void testCompareAndSet() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AMD64) {
            testGraph("unsafeCompareAndSetBoolean");
            testGraph("unsafeCompareAndSetByte");
            testGraph("unsafeCompareAndSetChar");
            testGraph("unsafeCompareAndSetShort");
            testGraph("unsafeCompareAndSetInt");
            testGraph("unsafeCompareAndSetLong");
            testGraph("unsafeCompareAndSetFloat");
            testGraph("unsafeCompareAndSetDouble");
            testGraph("unsafeCompareAndSetObject");
            testGraph("unsafeCompareAndExchangeBoolean");
            testGraph("unsafeCompareAndExchangeByte");
            testGraph("unsafeCompareAndExchangeChar");
            testGraph("unsafeCompareAndExchangeShort");
            testGraph("unsafeCompareAndExchangeInt");
            testGraph("unsafeCompareAndExchangeLong");
            testGraph("unsafeCompareAndExchangeFloat");
            testGraph("unsafeCompareAndExchangeDouble");
            testGraph("unsafeCompareAndExchangeObject");
        }
        test("unsafeCompareAndSetBoolean");
        test("unsafeCompareAndSetByte");
        test("unsafeCompareAndSetChar");
        test("unsafeCompareAndSetShort");
        test("unsafeCompareAndSetInt");
        test("unsafeCompareAndSetLong");
        test("unsafeCompareAndSetFloat");
        test("unsafeCompareAndSetDouble");
        test("unsafeCompareAndSetObject");
        test("unsafeCompareAndExchangeBoolean");
        test("unsafeCompareAndExchangeByte");
        test("unsafeCompareAndExchangeChar");
        test("unsafeCompareAndExchangeShort");
        test("unsafeCompareAndExchangeInt");
        test("unsafeCompareAndExchangeLong");
        test("unsafeCompareAndExchangeFloat");
        test("unsafeCompareAndExchangeDouble");
        test("unsafeCompareAndExchangeObject");
    }

    public static int unsafeGetAndAddByte() {
        Container container = new Container();
        return unsafe.getAndAddByte(container, byteOffset, (byte) 2);
    }

    public static int unsafeGetAndAddChar() {
        Container container = new Container();
        return unsafe.getAndAddChar(container, charOffset, (char) 250);
    }

    public static int unsafeGetAndAddShort() {
        Container container = new Container();
        return unsafe.getAndAddShort(container, shortOffset, (short) 1250);
    }

    public static int unsafeGetAndAddInt() {
        Container container = new Container();
        return unsafe.getAndAddInt(container, intOffset, 104501);
    }

    public static long unsafeGetAndAddLong() {
        Container container = new Container();
        return unsafe.getAndAddLong(container, longOffset, 0x123456abcdL);
    }

    @Test
    public void testGetAndAdd() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AMD64) {
            testGraph("unsafeGetAndAddByte");
            testGraph("unsafeGetAndAddChar");
            testGraph("unsafeGetAndAddShort");
            testGraph("unsafeGetAndAddInt");
            testGraph("unsafeGetAndAddLong");
        }
        test("unsafeGetAndAddByte");
        test("unsafeGetAndAddChar");
        test("unsafeGetAndAddShort");
        test("unsafeGetAndAddInt");
        test("unsafeGetAndAddLong");
    }

    public static boolean unsafeGetAndSetBoolean() {
        Container container = new Container();
        return unsafe.getAndSetBoolean(container, booleanOffset, true);
    }

    public static byte unsafeGetAndSetByte() {
        Container container = new Container();
        return unsafe.getAndSetByte(container, byteOffset, (byte) 129);
    }

    public static char unsafeGetAndSetChar() {
        Container container = new Container();
        return unsafe.getAndSetChar(container, charOffset, (char) 21111);
    }

    public static short unsafeGetAndSetShort() {
        Container container = new Container();
        return unsafe.getAndSetShort(container, shortOffset, (short) 21111);
    }

    public static int unsafeGetAndSetInt() {
        Container container = new Container();
        return unsafe.getAndSetInt(container, intOffset, 0x1234af);
    }

    public static long unsafeGetAndSetLong() {
        Container container = new Container();
        return unsafe.getAndSetLong(container, longOffset, 0x12345678abL);
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
        if (target.arch instanceof AMD64) {
            testGraph("unsafeGetAndSetBoolean");
            testGraph("unsafeGetAndSetByte");
            testGraph("unsafeGetAndSetChar");
            testGraph("unsafeGetAndSetShort");
            testGraph("unsafeGetAndSetInt");
            testGraph("unsafeGetAndSetLong");
            testGraph("unsafeGetAndSetObject");
        }
        test("unsafeGetAndSetBoolean");
        test("unsafeGetAndSetByte");
        test("unsafeGetAndSetChar");
        test("unsafeGetAndSetShort");
        test("unsafeGetAndSetInt");
        test("unsafeGetAndSetLong");
        test("unsafeGetAndSetObject");
    }
}
