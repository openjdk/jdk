/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires vm.jvmci
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot:open
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.common
 * @library /compiler/jvmci/jdk.vm.ci.hotspot.test/src
 *          /compiler/jvmci/jdk.vm.ci.code.test/src
 * @run testng/othervm
 *      -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler
 *      jdk.vm.ci.runtime.test.ConstantPoolTest
 */
package jdk.vm.ci.runtime.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.runtime.JVMCI;

public class ConstantPoolTest {

    static Object cloneByteArray(byte[] arr) {
        return arr.clone();
    }

    static Object cloneCharArray(char[] arr) {
        return arr.clone();
    }

    static Object cloneShortArray(short[] arr) {
        return arr.clone();
    }

    static Object cloneIntArray(int[] arr) {
        return arr.clone();
    }

    static Object cloneFloatArray(float[] arr) {
        return arr.clone();
    }

    static Object cloneLongArray(long[] arr) {
        return arr.clone();
    }

    static Object cloneDoubleArray(double[] arr) {
        return arr.clone();
    }

    static Object cloneObjectArray(Object[] arr) {
        return arr.clone();
    }

    public static final int ICONST_0 = 3;
    public static final int ALOAD_0 = 42;
    public static final int ALOAD_1 = 43;
    public static final int GETSTATIC = 178;
    public static final int INVOKEVIRTUAL = 182;
    public static final int INVOKEDYNAMIC = 186;

    public static int beU2(byte[] data, int bci) {
        return ((data[bci] & 0xff) << 8) | (data[bci + 1] & 0xff);
    }

    public static int beU1(byte[] data, int bci) {
        return data[bci] & 0xff;
    }

    public static int beS4(byte[] data, int bci) {
        return (data[bci] << 24) | ((data[bci + 1] & 0xff) << 16) | ((data[bci + 2] & 0xff) << 8) | (data[bci + 3] & 0xff);
    }

    @Test
    public void lookupArrayCloneMethodTest() throws Exception {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(ConstantPoolTest.class);
        for (ResolvedJavaMethod m : type.getDeclaredMethods()) {
            if (m.getName().startsWith("clone")) {
                byte[] bytecode = m.getCode();
                Assert.assertNotNull(bytecode, m.toString());
                Assert.assertEquals(5, bytecode.length, m.toString());
                Assert.assertEquals(ALOAD_0, beU1(bytecode, 0), m.toString());
                Assert.assertEquals(INVOKEVIRTUAL, beU1(bytecode, 1), m.toString());
                int cpi = beU2(bytecode, 2);
                JavaMethod callee = m.getConstantPool().lookupMethod(cpi, INVOKEVIRTUAL);
                Assert.assertTrue(callee instanceof ResolvedJavaMethod, callee.toString());
            }
        }
    }

    static int someStaticField = 1;
    static int getStaticField() {
        return someStaticField;
    }

    @Test
    public void lookupFieldTest() throws Exception {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(ConstantPoolTest.class);

        String methodName = "getStaticField";
        Signature methodSig = metaAccess.parseMethodDescriptor("()I");
        ResolvedJavaMethod m = type.findMethod(methodName, methodSig);
        Assert.assertNotNull(m);

        // Expected:
        // 0: getstatic "someStaticField":"I";
        // 3: ireturn;
        byte[] bytecode = m.getCode();
        Assert.assertNotNull(bytecode);
        Assert.assertEquals(4, bytecode.length);
        Assert.assertEquals(GETSTATIC, beU1(bytecode, 0));
        int rawIndex = beU2(bytecode, 1);
        JavaField field =  m.getConstantPool().lookupField(rawIndex, m, GETSTATIC);
        Assert.assertEquals("someStaticField", field.getName(), "Wrong field name; rawIndex = " + rawIndex + ";");
    }

    static String concatString1(String a, String b) {
        return a + b;
    }

    static String concatString2(String a, String b) {
        return a + b;
    }

    static void invokeHandle(MethodHandle mh) throws Throwable  {
        mh.invokeExact(0);
    }

    static void intFunc(int t) {}

    @Test
    public void lookupAppendixTest() throws Throwable {
        // We want at least two indy bytecodes -- with a single indy, the rawIndex is -1,
        // or 0xffffffff. Even if we load it with the wrong endianness, it will still come
        // "correctly" out as -1.
        concatString1("aaa", "bbb"); // force the indy to be resolved
        concatString2("aaa", "bbb"); // force the indy to be resolved

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(void.class, int.class);
        MethodHandle mh = lookup.findStatic(ConstantPoolTest.class, "intFunc", mt);
        invokeHandle(mh);

        lookupAppendixTest_dynamic("concatString1");
        lookupAppendixTest_dynamic("concatString2");
        lookupAppendixTest_virtual();
    }

    public void lookupAppendixTest_dynamic(String methodName) throws Exception {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(ConstantPoolTest.class);
        Signature methodSig = metaAccess.parseMethodDescriptor("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        ResolvedJavaMethod m = type.findMethod(methodName, methodSig);
        Assert.assertNotNull(m);

        // Expected:
        // aload_0;
        // aload_1;
        // invokedynamic ...StringConcatFactory.makeConcatWithConstants...
        byte[] bytecode = m.getCode();
        Assert.assertNotNull(bytecode);
        Assert.assertEquals(8, bytecode.length);
        Assert.assertEquals(ALOAD_0, beU1(bytecode, 0));
        Assert.assertEquals(ALOAD_1, beU1(bytecode, 1));
        Assert.assertEquals(INVOKEDYNAMIC, beU1(bytecode, 2));

        // Note: internally HotSpot stores the indy index as a native int32, but m.getCode() byte-swaps all such
        // indices so they appear to be big-endian.
        int rawIndex = beS4(bytecode, 3);
        JavaConstant constant = m.getConstantPool().lookupAppendix(rawIndex, INVOKEDYNAMIC);
        Assert.assertTrue(constant.toString().startsWith("Object["), "wrong appendix: " + constant);
    }

    public void lookupAppendixTest_virtual() throws Exception {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(ConstantPoolTest.class);
        Signature methodSig = metaAccess.parseMethodDescriptor("(Ljava/lang/invoke/MethodHandle;)V");
        ResolvedJavaMethod m = type.findMethod("invokeHandle", methodSig);
        Assert.assertNotNull(m);

        // Expected
        // aload_0
        // iconst_0
        // invokevirtual #rawIndex // Method java/lang/invoke/MethodHandle.invokeExact:(I)V
        byte[] bytecode = m.getCode();
        Assert.assertNotNull(bytecode);
        Assert.assertEquals(6, bytecode.length);
        Assert.assertEquals(ALOAD_0, beU1(bytecode, 0));
        Assert.assertEquals(ICONST_0, beU1(bytecode, 1));
        Assert.assertEquals(INVOKEVIRTUAL, beU1(bytecode, 2));

        int rawIndex = beU2(bytecode, 3);
        //System.out.println("rawIndex = " + rawIndex);
        JavaConstant constant = m.getConstantPool().lookupAppendix(rawIndex, INVOKEVIRTUAL);
        //System.out.println("constant = " + constant);
        Assert.assertTrue(constant.toString().startsWith("Object["), "wrong appendix: " + constant);
    }
}
