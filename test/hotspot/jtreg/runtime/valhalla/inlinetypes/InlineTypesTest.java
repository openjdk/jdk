/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.*;
import java.lang.ref.*;
import java.nio.ByteBuffer;
import java.time.chrono.ThaiBuddhistChronology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static jdk.test.lib.Asserts.*;

import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Platform;

import javax.tools.*;

import test.java.lang.invoke.lib.InstructionHelper;
import static test.java.lang.invoke.lib.InstructionHelper.classDesc;

/**
 * @test id=default
 * @summary Test data movement with inline types
 * @modules java.base/jdk.internal.value
 * @library /test/lib /test/jdk/java/lang/invoke/common
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile InlineTypesTest.java
 * @run main/othervm -Xmx128m -XX:+ExplicitGCInvokesConcurrent
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   runtime.valhalla.inlinetypes.InlineTypesTest
 */

/**
 * @test id=force-non-tearable
 * @summary Test data movement with inline types
 * @modules java.base/jdk.internal.value
 * @library /test/lib /test/jdk/java/lang/invoke/common
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile InlineTypesTest.java
 * @run main/othervm -Xmx128m -XX:+ExplicitGCInvokesConcurrent
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:ForceNonTearable=*
 *                   runtime.valhalla.inlinetypes.InlineTypesTest
 */

 final class ContainerValue1 {
    static TestValue1 staticInlineField;
    @NullRestricted
    TestValue1 nonStaticInlineField;
    TestValue1[] valueArray;

    ContainerValue1() {
        nonStaticInlineField = new TestValue1();
        super();
    }
}

@LooselyConsistentValue
value class TestValue1 {

    static TestValue1 staticValue = getInstance();

    final int i;
    final String name;

    public TestValue1() {
        int now =  (int)System.nanoTime();
        i = now;
        name = Integer.valueOf(now).toString();
    }

    public TestValue1(int i) {
        this.i = i;
        name = Integer.valueOf(i).toString();
    }

    public static TestValue1 getInstance() {
        return new TestValue1();
    }

    public static TestValue1 getNonBufferedInstance() {
        return (TestValue1) staticValue;
    }

    public boolean verify() {
        if (name == null) return i == 0;
        return Integer.valueOf(i).toString().compareTo(name) == 0;
    }
}

final class ContainerValue2 {
    static TestValue2 staticInlineField;
    @NullRestricted
    TestValue2 nonStaticInlineField;
    TestValue2[] valueArray;

    ContainerValue2() {
        nonStaticInlineField = new TestValue2();
        super();
    }
}

@LooselyConsistentValue
value class TestValue2 {
    static TestValue2 staticValue = getInstance();

    final long l;
    final double d;
    final String s;

    public TestValue2() {
        long now = System.nanoTime();
        l = now;
        String stringNow = Long.valueOf(now).toString();
        s = stringNow;
        d = Double.parseDouble(stringNow);
    }

    public TestValue2(long l) {
        this.l = l;
        String txt = Long.valueOf(l).toString();
        s = txt;
        d = Double.parseDouble(txt);
    }

    public static TestValue2 getInstance() {
        return new TestValue2();
    }

    public static TestValue2 getNonBufferedInstance() {
        return (TestValue2) staticValue;
    }

    public boolean verify() {
        if (s == null) {
            return d == 0 && l == 0;
        }
        return Long.valueOf(l).toString().compareTo(s) == 0
                && Double.parseDouble(s) == d;
    }
}

final class ContainerValue3 {
    static TestValue3 staticInlineField;
    @NullRestricted
    TestValue3 nonStaticInlineField;
    TestValue3[] valueArray;

    ContainerValue3() {
        nonStaticInlineField = new TestValue3();
        super();
    }
}

@LooselyConsistentValue
value class TestValue3 {

    static TestValue3 staticValue = getInstance();

    final byte b;

    public TestValue3() {
        b = 123;
    }

    public TestValue3(byte b) {
        this.b = b;
    }

    public static TestValue3 getInstance() {
        return new TestValue3();
    }

    public static TestValue3 getNonBufferedInstance() {
        return (TestValue3) staticValue;
    }

    public boolean verify() {
        return b == 0 || b == 123;
    }
}

final class ContainerValue4 {
    static TestValue4 staticInlineField;
    @NullRestricted
    TestValue4 nonStaticInlineField;
    TestValue4[] valueArray;

    ContainerValue4() {
        nonStaticInlineField = new TestValue4();
        super();
    }
}

@LooselyConsistentValue
value class TestValue4 {

    static TestValue4 staticValue = getInstance();

    final byte b1;
    final byte b2;
    final byte b3;
    final byte b4;
    final short s1;
    final short s2;
    final int i;
    final long l;
    final String val;

    public TestValue4() {
        this((int) System.nanoTime());
    }

    public TestValue4(int i) {
        this.i = i;
        val = Integer.valueOf(i).toString();
        ByteBuffer bf = ByteBuffer.allocate(8);
        bf.putInt(0, i);
        bf.putInt(4, i);
        l = bf.getLong(0);
        s1 = bf.getShort(2);
        s2 = bf.getShort(0);
        b1 = bf.get(3);
        b2 = bf.get(2);
        b3 = bf.get(1);
        b4 = bf.get(0);
    }

    public static TestValue4 getInstance() {
        return new TestValue4();
    }

    public static TestValue4 getNonBufferedInstance() {
        return (TestValue4) staticValue;
    }

    public boolean verify() {
        if (val == null) {
            return i == 0 && l == 0 && b1 == 0 && b2 == 0 && b3 == 0 && b4 == 0
                    && s1 == 0 && s2 == 0;
        }
        ByteBuffer bf = ByteBuffer.allocate(8);
        bf.putInt(0, i);
        bf.putInt(4, i);
        long nl =  bf.getLong(0);
        bf.clear();
        bf.putShort(0, s2);
        bf.putShort(2, s1);
        int from_s = bf.getInt(0);
        bf.clear();
        bf.put(0, b4);
        bf.put(1, b3);
        bf.put(2, b2);
        bf.put(3, b1);
        int from_b = bf.getInt(0);
        return l == nl && Integer.valueOf(i).toString().compareTo(val) == 0
                && from_s == i && from_b == i;
    }
}

public class InlineTypesTest {

    public static void main(String[] args) {
        Class<?> inlineClass = runtime.valhalla.inlinetypes.TestValue1.class;
        Class<?> testClasses[] = {
                runtime.valhalla.inlinetypes.TestValue1.class,
                runtime.valhalla.inlinetypes.TestValue2.class,
                runtime.valhalla.inlinetypes.TestValue3.class,
                runtime.valhalla.inlinetypes.TestValue4.class
        };
        Class<?> containerClasses[] = {
                runtime.valhalla.inlinetypes.ContainerValue1.class,
                runtime.valhalla.inlinetypes.ContainerValue2.class,
                runtime.valhalla.inlinetypes.ContainerValue3.class,
                runtime.valhalla.inlinetypes.ContainerValue4.class
        };

        for (int i = 0; i < testClasses.length; i++) {
            try {
                testExecutionStackToLocalVariable(testClasses[i]);
                testExecutionStackToFields(testClasses[i], containerClasses[i]);
                testExecutionStackToInlineArray(testClasses[i], containerClasses[i]);
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            }
        }
    }

    static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static void testExecutionStackToLocalVariable(Class<?> inlineClass) throws Throwable {
        String sig = "()L" + inlineClass.getName().replace('.', '/') + ";";
        final MethodTypeDesc voidReturnClass = MethodTypeDesc.ofDescriptor(sig);
        final ClassDesc systemClassDesc = classDesc(System.class);
        final ClassDesc inlineClassDesc = classDesc(inlineClass);
        MethodHandle fromExecStackToLocalVar = InstructionHelper.buildMethodHandle(
                LOOKUP,
                "execStackToLocalVar",
                MethodType.methodType(boolean.class),
                CODE -> {
                    CODE.invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"));
                    int n = -1;
                    while (n < 1024) {
                        n++;
                        CODE
                        .invokestatic(inlineClassDesc, "getInstance", voidReturnClass)
                        .astore(n);
                        n++;
                        CODE
                        .invokestatic(inlineClassDesc, "getNonBufferedInstance", voidReturnClass)
                        .astore(n);
                    }
                    CODE.invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"));
                    Label endLabel = CODE.newLabel();
                    while (n > 0) {
                        CODE
                        .aload(n)
                        .invokevirtual(inlineClassDesc, "verify", MethodTypeDesc.ofDescriptor("()Z"))
                        .iconst_1()
                        .if_icmpne(endLabel);
                        n--;
                    }
                    CODE
                    .iconst_1()
                    .return_(TypeKind.BOOLEAN)
                    .labelBinding(endLabel)
                    .iconst_0()
                    .return_(TypeKind.BOOLEAN);
                });
        boolean result = (boolean) fromExecStackToLocalVar.invokeExact();
        System.out.println(result);
        assertTrue(result, "Invariant");
    }

    static void testExecutionStackToFields(Class<?> inlineClass, Class<?> containerClass) throws Throwable {
        final int ITERATIONS = Platform.isDebugBuild() ? 3 : 512;
        String sig = "()L" + inlineClass.getName().replace('.', '/') + ";";
        final MethodTypeDesc voidReturnClass = MethodTypeDesc.ofDescriptor(sig);
        final ClassDesc systemClassDesc = classDesc(System.class);
        final ClassDesc inlineClassDesc = classDesc(inlineClass);
        final ClassDesc containerClassDesc = classDesc(containerClass);

        MethodHandle fromExecStackToFields = InstructionHelper.buildMethodHandle(
                LOOKUP,
                "execStackToFields",
                MethodType.methodType(boolean.class),
                CODE -> {
                    Label loop = CODE.newLabel();
                    Label end = CODE.newLabel();
                    Label failed = CODE.newLabel();
                    CODE
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"), false)
                    .new_(containerClassDesc)
                    .dup()
                    .invokespecial(containerClassDesc, "<init>", MethodTypeDesc.ofDescriptor("()V"))
                    .astore(1)
                    .iconst_m1()
                    .istore(2)
                    .labelBinding(loop)
                    .iload(2)
                    .ldc(ITERATIONS)
                    .if_icmpeq(end)
                    .aload(1)
                    .invokestatic(inlineClassDesc, "getInstance", voidReturnClass)
                    .putfield(containerClassDesc, "nonStaticInlineField", inlineClassDesc)
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"))
                    .aload(1)
                    .getfield(containerClassDesc, "nonStaticInlineField", inlineClassDesc)
                    .invokevirtual(inlineClassDesc, "verify", MethodTypeDesc.ofDescriptor("()Z"))
                    .iconst_1()
                    .if_icmpne(failed)
                    .aload(1)
                    .invokestatic(inlineClassDesc, "getNonBufferedInstance", voidReturnClass)
                    .putfield(containerClassDesc, "nonStaticInlineField", inlineClassDesc)
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"))
                    .aload(1)
                    .getfield(containerClassDesc, "nonStaticInlineField", inlineClassDesc)
                    .invokevirtual(inlineClassDesc, "verify", MethodTypeDesc.ofDescriptor("()Z"))
                    .iconst_1()
                    .if_icmpne(failed)
                    .invokestatic(inlineClassDesc, "getInstance", voidReturnClass)
                    .putstatic(containerClassDesc, "staticInlineField", inlineClassDesc)
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"))
                    .getstatic(containerClassDesc, "staticInlineField", inlineClassDesc)
                    .checkcast(inlineClassDesc)
                    .invokevirtual(inlineClassDesc, "verify", MethodTypeDesc.ofDescriptor("()Z"))
                    .iconst_1()
                    .if_icmpne(failed)
                    .invokestatic(inlineClassDesc, "getNonBufferedInstance", voidReturnClass)
                    .putstatic(containerClassDesc, "staticInlineField", inlineClassDesc)
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"))
                    .getstatic(containerClassDesc, "staticInlineField", inlineClassDesc)
                    .checkcast(inlineClassDesc)
                    .invokevirtual(inlineClassDesc, "verify", MethodTypeDesc.ofDescriptor("()Z"))
                    .iconst_1()
                    .if_icmpne(failed)
                    .iinc(2, 1)
                    .goto_(loop)
                    .labelBinding(end)
                    .iconst_1()
                    .return_(TypeKind.BOOLEAN)
                    .labelBinding(failed)
                    .iconst_0()
                    .return_(TypeKind.BOOLEAN);
                });
        boolean result = (boolean) fromExecStackToFields.invokeExact();
        System.out.println(result);
        assertTrue(result, "Invariant");
    }

    static void testExecutionStackToInlineArray(Class<?> inlineClass, Class<?> containerClass) throws Throwable {
        final int ITERATIONS = Platform.isDebugBuild() ? 3 : 100;
        String sig = "()L" + inlineClass.getName().replace('.', '/') + ";";
        final MethodTypeDesc voidReturnClass = MethodTypeDesc.ofDescriptor(sig);
        final ClassDesc systemClassDesc = classDesc(System.class);
        final ClassDesc inlineClassDesc = classDesc(inlineClass);
        final ClassDesc containerClassDesc = classDesc(containerClass);

        MethodHandle fromExecStackToInlineArray = InstructionHelper.buildMethodHandle(
                LOOKUP,
                "execStackToInlineArray",
                MethodType.methodType(boolean.class),
                CODE -> {
                    Label loop1 = CODE.newLabel();
                    Label loop2 = CODE.newLabel();
                    Label end1 = CODE.newLabel();
                    Label end2 = CODE.newLabel();
                    Label failed = CODE.newLabel();
                    CODE
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"))
                    .new_(containerClassDesc)
                    .dup()
                    .invokespecial(containerClassDesc, "<init>", MethodTypeDesc.ofDescriptor("()V"))
                    .astore(1)
                    .ldc(ITERATIONS * 3)
                    .anewarray(inlineClassDesc)
                    .astore(2)
                    .aload(2)
                    .aload(1)
                    .swap()
                    .putfield(containerClassDesc, "valueArray", inlineClassDesc.arrayType())
                    .iconst_0()
                    .istore(3)
                    .labelBinding(loop1)
                    .iload(3)
                    .ldc(ITERATIONS *3)
                    .if_icmpge(end1)
                    .aload(2)
                    .iload(3)
                    .invokestatic(inlineClassDesc, "getInstance", voidReturnClass)
                    .aastore()
                    .iinc(3, 1)
                    .aload(2)
                    .iload(3)
                    .invokestatic(inlineClassDesc, "getNonBufferedInstance", voidReturnClass)
                    .aastore()
                    .iinc(3, 1)
                    .aload(2)
                    .iload(3)
                    .new_(inlineClassDesc)
                    .dup()
                    .invokespecial(inlineClassDesc, "<init>", MethodTypeDesc.ofDescriptor("()V"))
                    .aastore()
                    .iinc(3, 1)
                    .goto_(loop1)
                    .labelBinding(end1)
                    .invokestatic(systemClassDesc, "gc", MethodTypeDesc.ofDescriptor("()V"))
                    .iconst_0()
                    .istore(3)
                    .labelBinding(loop2)
                    .iload(3)
                    .ldc(ITERATIONS * 3)
                    .if_icmpge(end2)
                    .aload(2)
                    .iload(3)
                    .aaload()
                    .invokevirtual(inlineClassDesc, "verify", MethodTypeDesc.ofDescriptor("()Z"))
                    .iconst_1()
                    .if_icmpne(failed)
                    .iinc(3, 1)
                    .goto_(loop2)
                    .labelBinding(end2)
                    .iconst_1()
                    .return_(TypeKind.BOOLEAN)
                    .labelBinding(failed)
                    .iconst_0()
                    .return_(TypeKind.BOOLEAN);
                });
        boolean result = (boolean) fromExecStackToInlineArray.invokeExact();
        System.out.println(result);
        assertTrue(result, "Invariant");
    }

}
