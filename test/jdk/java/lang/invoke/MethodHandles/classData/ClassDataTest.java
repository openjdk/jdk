/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8230501
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @build java.base/*
 * @run testng/othervm ClassDataTest
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.ClassDataHelper;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.internal.org.objectweb.asm.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.invoke.MethodHandles.Lookup.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import static org.testng.Assert.*;

public class ClassDataTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void testOriginalAccess() throws Throwable {
        Lookup lookup = hiddenClass(20);
        assertTrue(lookup.hasFullPrivilegeAccess());

        int value = MethodHandles.classData(lookup, "dummy", int.class);
        assertEquals(value, 20);

        Integer i = MethodHandles.classData(lookup, "dummy", Integer.class);
        assertEquals(i.intValue(), 20);
    }

    /*
     * A lookup class with no class data.
     */
    @Test
    public void noClassData() throws Throwable {
        assertTrue(MethodHandles.classData(LOOKUP, "dummy", Object.class) == null);
    }

    @DataProvider(name = "teleportedLookup")
    private Object[][] teleportedLookup() throws Throwable {
        Lookup lookup = hiddenClass(30);
        Class<?> hc = lookup.lookupClass();
        assertClassData(lookup, 30);

        int fullAccess = PUBLIC|PROTECTED|PACKAGE|MODULE|PRIVATE;
        return new Object[][] {
                new Object[] { MethodHandles.privateLookupIn(hc, LOOKUP), fullAccess},
                new Object[] { LOOKUP.in(hc), fullAccess & ~(PROTECTED|PRIVATE) },
                new Object[] { lookup.dropLookupMode(PRIVATE), fullAccess & ~(PROTECTED|PRIVATE) },
        };
    }

    @Test(dataProvider = "teleportedLookup", expectedExceptions = { IllegalAccessException.class })
    public void illegalAccess(Lookup lookup, int access) throws Throwable {
        int lookupModes = lookup.lookupModes();
        assertTrue((lookupModes & ORIGINAL) == 0);
        assertEquals(lookupModes, access);
        MethodHandles.classData(lookup, "no original access", int.class);
    }

    @Test(expectedExceptions = { ClassCastException.class })
    public void incorrectType() throws Throwable {
        Lookup lookup = hiddenClass(20);
        MethodHandles.classData(lookup, "incorrect type", Long.class);
    }

    @Test(expectedExceptions = { IndexOutOfBoundsException.class })
    public void invalidIndex() throws Throwable {
        Lookup lookup = hiddenClass(List.of());
        ClassDataHelper.classDataAt(lookup, "OOB", Object.class, 0);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void unboxNull() throws Throwable {
        List<Integer> list = new ArrayList<>();
        list.add(null);
        Lookup lookup = hiddenClass(list);
        ClassDataHelper.classDataAt(lookup, "null element", int.class, 0);
    }

    @Test
    public void nullElement() throws Throwable {
        List<Object> list = new ArrayList<>();
        list.add(null);
        Lookup lookup = hiddenClass(list);
        assertTrue(ClassDataHelper.classDataAt(lookup, "null", Object.class, 0) == null);
    }

    @Test
    public void intClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T1-int");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, int.class).build();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, 100, true);
        int value = MethodHandles.classData(lookup, "dummy", int.class);
        assertEquals(value, 100);
        // call through condy
        assertClassData(lookup, 100);
    }

    @Test
    public void floatClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T1-float");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, float.class).build();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, 0.1234f, true);
        float value = MethodHandles.classData(lookup, "dummy", float.class);
        assertEquals(value, 0.1234f);
        // call through condy
        assertClassData(lookup, 0.1234f);
    }

    @Test
    public void classClassData() throws Throwable {
        Class<?> hc = hiddenClass(100).lookupClass();
        ClassByteBuilder builder = new ClassByteBuilder("T2");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, Class.class).build();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, hc, true);
        Class<?> value = MethodHandles.classData(lookup, "dummy", Class.class);
        assertEquals(value, hc);
        // call through condy
        assertClassData(lookup, hc);
    }

    @Test
    public void arrayClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T3");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, String[].class).build();
        String[] colors = new String[] { "red", "yellow", "blue"};
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, colors, true);
        assertClassData(lookup, colors.clone());
        // class data is modifiable and not a constant
        colors[0] = "black";
        // it will get back the modified class data
        String[] value = MethodHandles.classData(lookup, "dummy", String[].class);
        assertEquals(value, colors);
        // even call through condy as it's not a constant
        assertClassData(lookup, colors);
    }

    @Test
    public void listClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T4");
        byte[] bytes = builder.classDataAt(ACC_PUBLIC|ACC_STATIC, Integer.class, 2).build();
        List<Integer> cd = List.of(100, 101, 102, 103);
        int expected = 102;  // element at index=2
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        int value = ClassDataHelper.classDataAt(lookup, "2", int.class, 2);
        assertEquals(value, expected);
        // call through condy
        assertClassData(lookup, expected);
    }

    @Test
    public void arrayListClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T4");
        byte[] bytes = builder.classDataAt(ACC_PUBLIC|ACC_STATIC, Integer.class, 1).build();
        ArrayList<Integer> cd = new ArrayList<>();
        Stream.of(100, 101, 102, 103).forEach(cd::add);
        int expected = 101;  // element at index=1
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        int value = ClassDataHelper.classDataAt(lookup, "1", int.class, 1);
        assertEquals(value, expected);
        // call through condy
        assertClassData(lookup, expected);
    }

    private static Lookup hiddenClass(int value) {
        ClassByteBuilder builder = new ClassByteBuilder("HC");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, int.class).build();
        try {
            return LOOKUP.defineHiddenClassWithClassData(bytes, value, true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    private static Lookup hiddenClass(List<?> list) {
        ClassByteBuilder builder = new ClassByteBuilder("HC");
        byte[] bytes = builder.classData(ACC_PUBLIC|ACC_STATIC, List.class).build();
        try {
            return LOOKUP.defineHiddenClassWithClassData(bytes, list, true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void condyInvokedFromVirtualMethod() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T5");
        // generate classData instance method
        byte[] bytes = builder.classData(ACC_PUBLIC, Class.class).build();
        Lookup hcLookup = hiddenClass(100);
        assertClassData(hcLookup, 100);
        Class<?> hc = hcLookup.lookupClass();
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, hc, true);
        Class<?> value = MethodHandles.classData(lookup, "dummy", Class.class);
        assertEquals(value, hc);
        // call through condy
        Class<?> c = lookup.lookupClass();
        assertClassData(lookup, c.newInstance(), hc);
    }

    @Test
    public void immutableListClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T6");
        // generate classDataAt instance method
        byte[] bytes = builder.classDataAt(ACC_PUBLIC, Integer.class, 2).build();
        List<Integer> cd = List.of(100, 101, 102, 103);
        int expected = 102;  // element at index=2
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        int value = ClassDataHelper.classDataAt(lookup, "2", int.class, 2);
        assertEquals(value, expected);
        // call through condy
        Class<?> c = lookup.lookupClass();
        assertClassData(lookup, c.newInstance() ,expected);
    }

    /*
     * The return value of MethodHandles::classDataAt is the element
     * contained in the list when the method is called.
     * If MethodHandles::classDataAt is called via condy, the value
     * will be captured as a constant.  If the class data is modified
     * after the element at the given index is computed via condy,
     * subsequent LDC of such ConstantDynamic entry will return the same
     * value. However, direct invocation of MethodHandles::classDataAt
     * will return the modified value.
     */
    @Test
    public void mutableListClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T7");
        // generate classDataAt instance method
        byte[] bytes = builder.classDataAt(ACC_PUBLIC, MethodType.class, 0).build();
        MethodType mtype = MethodType.methodType(int.class, String.class);
        List<MethodType> cd = new ArrayList<>(List.of(mtype));
        Lookup lookup = LOOKUP.defineHiddenClassWithClassData(bytes, cd, true);
        // call through condy
        Class<?> c = lookup.lookupClass();
        assertClassData(lookup, c.newInstance(), mtype);
        // modify the class data
        assertTrue(cd.remove(0) == mtype);
        cd.add(0,  MethodType.methodType(void.class));
        MethodType newMType = cd.get(0);
        // loading the element using condy returns the original value
        assertClassData(lookup, c.newInstance(), mtype);
        // direct invocation of MethodHandles.classDataAt returns the modified value
        assertEquals(ClassDataHelper.classDataAt(lookup, "new MethodType", MethodType.class, 0), newMType);
    }

    static class ClassByteBuilder {
        private static final String OBJECT_CLS = "java/lang/Object";
        private static final String MHS_CLS = "java/lang/invoke/MethodHandles";
        private static final String CLASS_DATA_BSM_DESCR =
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;";
        private final ClassWriter cw;
        private final String classname;

        /**
         * A builder to generate a class file to access class data
         * @param classname
         */
        ClassByteBuilder(String classname) {
            this.classname = classname;
            this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(V14, ACC_FINAL, classname, null, OBJECT_CLS, null);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, OBJECT_CLS, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        byte[] build() {
            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            Path p = Paths.get(classname + ".class");
                try (OutputStream os = Files.newOutputStream(p)) {
                os.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return bytes;
        }

        /*
         * Generate classData method to load class data via condy
         */
        ClassByteBuilder classData(int accessFlags, Class<?> returnType) {
            MethodType mtype = MethodType.methodType(returnType);
            MethodVisitor mv = cw.visitMethod(accessFlags,
                                             "classData",
                                              mtype.descriptorString(), null, null);
            mv.visitCode();
            Handle bsm = new Handle(H_INVOKESTATIC, MHS_CLS, "classData",
                                    CLASS_DATA_BSM_DESCR,
                                    false);
            ConstantDynamic dynamic = new ConstantDynamic("dummy", Type.getDescriptor(returnType), bsm);
            mv.visitLdcInsn(dynamic);
            mv.visitInsn(returnType == int.class ? IRETURN :
                            (returnType == float.class ? FRETURN : ARETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return this;
        }

        /*
         * Generate classDataAt method to load an element from class data via condy
         */
        ClassByteBuilder classDataAt(int accessFlags, Class<?> returnType, int index) {
            MethodType mtype = MethodType.methodType(returnType);
            MethodVisitor mv = cw.visitMethod(accessFlags,
                                              "classData",
                                               mtype.descriptorString(), null, null);
            mv.visitCode();
            Handle bsm = new Handle(H_INVOKESTATIC, "java/lang/invoke/ClassDataHelper", "classDataAt",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;",
                        false);
            ConstantDynamic dynamic = new ConstantDynamic("classDataAt", Type.getDescriptor(returnType), bsm, index);
            mv.visitLdcInsn(dynamic);
            mv.visitInsn(returnType == int.class? IRETURN : ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return this;
        }
    }

    /*
     * Load an int constant from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, int value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            int v = (int)m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /*
     * Load an int constant from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, Object o, int value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            int v = (int)m.invoke(o);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /*
     * Load a float constant from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, float value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            float v = (float)m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /*
     * Load a Class constant from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, Class<?> value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            Class<?> v = (Class<?>)m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /*
     * Load a Class from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, Object o, Class<?> value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            Object v = m.invoke(o);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /*
     * Load an Object from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, Object value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            Object v = m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /*
     * Load an Object from class data via condy and
     * verify it matches the given value.
     */
    private void assertClassData(Lookup lookup, Object o, Object value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            Object v = m.invoke(o);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}


