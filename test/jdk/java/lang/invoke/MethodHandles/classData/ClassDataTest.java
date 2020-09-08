/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @build  java.base/*
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
import java.util.Map;
import java.util.stream.Stream;

import jdk.internal.org.objectweb.asm.*;
import org.testng.annotations.Test;

import static jdk.internal.org.objectweb.asm.Opcodes.*;
import static org.testng.Assert.*;

public class ClassDataTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();
    private void classData(Lookup lookup, int value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            int v = (int)m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void classData(Lookup lookup, float value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            float v = (float)m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void classData(Lookup lookup, Class<?> value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            Class<?> v = (Class<?>)m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void classData(Lookup lookup, Object value) throws Throwable {
        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            Object v = m.invoke(null);
            assertEquals(value, v);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void intClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T1");
        byte[] bytes = builder.classData(int.class).build();
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, 99);
        classData(lookup, 99);  // call through condy
        int value = ClassDataHelper.classData(lookup, "dummy", int.class);
        assertEquals(value, 99);
    }

    @Test
    public void floatClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T1a");
        byte[] bytes = builder.classData(float.class).build();
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, 0.1234f);
        classData(lookup, 0.1234f);  // call through condy
        float value = ClassDataHelper.classData(lookup, "dummy", float.class);
        assertEquals(value, 0.1234f);
    }

    @Test
    public void classClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T2");
        byte[] bytes = builder.classData(Class.class).build();
        Class<?> hc = hiddenClass();
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, hc);
        classData(lookup, hc);   // call through condy
        Class<?> value = ClassDataHelper.classData(lookup, "dummy", Class.class);
        assertEquals(value, hc);
    }

    @Test
    public void arrayClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T3");
        byte[] bytes = builder.classData(String[].class).build();
        String[] colors = new String[] { "red", "yellow", "blue"};
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, colors);
        classData(lookup, colors.clone());
        // modify the content of class data
        colors[0] = "black";
        classData(lookup, colors);    // call through condy
        String[] value = ClassDataHelper.classData(lookup, "dummy", String[].class);
        assertEquals(value, colors);
    }

    private Class<?> hiddenClass() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("HC");
        byte[] bytes = builder.classData(int.class).build();
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, 100);
        classData(lookup, 100);
        return lookup.lookupClass();
    }

    @Test
    public void listClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T4");
        byte[] bytes = builder.classDataAt(Integer.class, List.class, "2").build();
        List<Integer> cd = List.of(100, 101, 102, 103);
        int expected = 102;  // element at index=2
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, cd);
        classData(lookup, expected);   // call through condy
        int value = ClassDataHelper.classDataAt(lookup, "2", int.class, List.class);
        assertEquals(value, expected);
    }

    @Test
    public void mapClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T5");
        byte[] bytes = builder.classDataAt(String.class, Map.class, "color").build();
        Map<String, String> cd = Map.of("name", "Bob", "color", "orange", "grade", "9");
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, cd);
        classData(lookup, "orange");   // call through condy
        String value = ClassDataHelper.classDataAt(lookup, "color", String.class, Map.class);
        assertEquals(value, "orange");
    }

    @Test
    public void arrayListClassData() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T4");
        byte[] bytes = builder.classDataAt(Integer.class, ArrayList.class, "1").build();
        ArrayList<Integer> cd = new ArrayList<>();
        Stream.of(100, 101, 102, 103).forEach(cd::add);
        int expected = 101;  // element at index=1
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, cd);
        classData(lookup, expected);   // call through condy
        int value = ClassDataHelper.classDataAt(lookup, "1", int.class, ArrayList.class);
        assertEquals(value, expected);
    }

    @Test
    public void classClassDataVirtualCall() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T5", ACC_PUBLIC);
        byte[] bytes = builder.classData(Class.class).build();
        Class<?> hc = hiddenClass();
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, hc);
        Class<?> value = ClassDataHelper.classData(lookup, "dummy", Class.class);
        assertEquals(value, hc);

        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            value = (Class<?>)m.invoke(c.newInstance());
            assertEquals(value, hc);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void listClassDataVirtualCall() throws Throwable {
        ClassByteBuilder builder = new ClassByteBuilder("T6", ACC_PUBLIC);
        byte[] bytes = builder.classDataAt(Integer.class, List.class, "2").build();
        List<Integer> cd = List.of(100, 101, 102, 103);
        int expected = 102;  // element at index=2
        Lookup lookup = ClassDataHelper.defineHiddenClassWithClassData(LOOKUP, bytes, cd);
        int value = ClassDataHelper.classDataAt(lookup, "2", int.class, List.class);
        assertEquals(value, expected);

        try {
            Class<?> c = lookup.lookupClass();
            Method m = c.getMethod("classData");
            value = (int)m.invoke(c.newInstance());
            assertEquals(value, expected);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }


    static class ClassByteBuilder {
        private static final String OBJECT_CLS = "java/lang/Object";
        private static final String MHS_CLS = "java/lang/invoke/ClassDataHelper";
        private static final String CLASS_DATA_BSM_DESCR =
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;";
        private static final String CLASS_DATA_AT_BSM_DESCR =
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;";
        private final ClassWriter cw;
        private final String classname;
        private final int accessFlags;
        ClassByteBuilder(String classname) {
            this(classname, ACC_PUBLIC|ACC_STATIC);
        }

        /**
         * A builder to generate a class file to access class data
         * @param classname
         * @param accessFlags access flags for classData and classDataAt methods
         */
        ClassByteBuilder(String classname, int accessFlags) {
            this.classname = classname;
            this.accessFlags = accessFlags;
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

        ClassByteBuilder classData(Class<?> returnType) {
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

        ClassByteBuilder classDataAt(Class<?> returnType, Class<?> classDataType, String key) {
            MethodType mtype = MethodType.methodType(returnType);
            MethodVisitor mv = cw.visitMethod(accessFlags,
                                              "classData",
                                               mtype.descriptorString(), null, null);
            mv.visitCode();
            Handle bsm = new Handle(H_INVOKESTATIC, MHS_CLS, "classDataAt",
                                    CLASS_DATA_AT_BSM_DESCR,
                                    false);
            ConstantDynamic dynamic = new ConstantDynamic(key, Type.getDescriptor(returnType), bsm,
                                                          Type.getType(classDataType));
            mv.visitLdcInsn(dynamic);
            mv.visitInsn(returnType == int.class? IRETURN : ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return this;
        }
    }
}


