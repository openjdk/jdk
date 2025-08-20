/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.runtime.test;

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import org.junit.Test;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isStatic;

/**
 * Context for type related tests.
 */
public class TypeUniverse {

    public static final Unsafe unsafe;
    public static final double JAVA_VERSION = Double.valueOf(System.getProperty("java.specification.version"));

    public static final MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
    public static final ConstantReflectionProvider constantReflection = JVMCI.getRuntime().getHostJVMCIBackend().getConstantReflection();
    public static final Collection<Class<?>> classes = new HashSet<>();
    public static final Set<ResolvedJavaType> javaTypes;
    public static final ResolvedJavaType predicateType;
    public static final Map<Class<?>, Class<?>> arrayClasses = new HashMap<>();

    private static List<ConstantValue> constants;

    // Define a type-use annotation
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TypeQualifier {
        String comment() default "";
        int id() default -1;
    }

    // Define a parameter annotation
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ParameterQualifier {
        String value() default "";
        int tag() default -1;
    }

    public class InnerClass {
        public class InnerInnerClass {
        }
    }

    public static class InnerStaticClass {

    }

    public static final class InnerStaticFinalClass {

    }

    private class PrivateInnerClass {

    }

    protected class ProtectedInnerClass {

    }

    static {
        Unsafe theUnsafe = null;
        try {
            theUnsafe = Unsafe.getUnsafe();
        } catch (Exception e) {
            try {
                Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                theUnsafe = (Unsafe) theUnsafeField.get(null);
            } catch (Exception e1) {
                throw (InternalError) new InternalError("unable to initialize unsafe").initCause(e1);
            }
        }
        unsafe = theUnsafe;

        Class<?>[] initialClasses = {void.class, boolean.class, byte.class, short.class, char.class, int.class, float.class, long.class, double.class, Object.class, Class.class, boolean[].class,
                        byte[].class, short[].class, char[].class, int[].class, float[].class, long[].class, double[].class, Object[].class, Class[].class, List[].class, boolean[][].class,
                        byte[][].class, short[][].class, char[][].class, int[][].class, float[][].class, long[][].class, double[][].class, Object[][].class, Class[][].class, List[][].class,
                        ClassLoader.class, String.class, Serializable.class, Cloneable.class, Test.class, TestMetaAccessProvider.class, List.class, Collection.class, Map.class, Queue.class,
                        HashMap.class, LinkedHashMap.class, IdentityHashMap.class, AbstractCollection.class, AbstractList.class, ArrayList.class, InnerClass.class, InnerStaticClass.class,
                        InnerStaticFinalClass.class, PrivateInnerClass.class, ProtectedInnerClass.class, ScopedMemoryAccess.class};
        for (Class<?> c : initialClasses) {
            addClass(c);
        }
        Predicate<String> predicate = s -> s.length() == 1;
        addClass(predicate.getClass());
        predicateType = metaAccess.lookupJavaType(predicate.getClass());

        javaTypes = Collections.unmodifiableSet(classes.stream().map(c -> metaAccess.lookupJavaType(c)).collect(Collectors.toSet()));
    }

    static class ConstantsUniverse {
        static final Object[] ARRAYS = classes.stream().map(c -> c != void.class && !c.isArray() ? Array.newInstance(c, 42) : null).filter(o -> o != null).collect(Collectors.toList()).toArray();
        static final Object CONST1 = new ArrayList<>();
        static final Object CONST2 = new ArrayList<>();
        static final Object CONST3 = new IdentityHashMap<>();
        static final Object CONST4 = new LinkedHashMap<>();
        static final Object CONST5 = new TreeMap<>();
        static final Object CONST6 = new ArrayDeque<>();
        static final Object CONST7 = new LinkedList<>();
        static final Object CONST8 = "a string";
        static final Object CONST9 = 42;
        static final Object CONST10 = String.class;
        static final Object CONST11 = String[].class;
    }

    public static List<ConstantValue> constants() {
        if (constants == null) {
            List<ConstantValue> res = readConstants(JavaConstant.class);
            res.addAll(readConstants(ConstantsUniverse.class));
            constants = res;
        }
        return constants;
    }

    public static class ConstantValue {
        public final String name;
        public final JavaConstant value;
        public final Object boxed;

        public ConstantValue(String name, JavaConstant value, Object boxed) {
            this.name = name;
            this.value = value;
            this.boxed = boxed;
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }

        public String getSimpleName() {
            return name.substring(name.lastIndexOf('.') + 1);
        }
    }

    /**
     * Reads the value of all {@code static final} fields from a given class into an array of
     * {@link ConstantValue}s.
     */
    public static List<ConstantValue> readConstants(Class<?> fromClass) {
        try {
            List<ConstantValue> res = new ArrayList<>();
            for (Field field : fromClass.getDeclaredFields()) {
                if (isStatic(field.getModifiers()) && isFinal(field.getModifiers())) {
                    ResolvedJavaField javaField = metaAccess.lookupJavaField(field);
                    Object boxed = field.get(null);
                    if (boxed instanceof JavaConstant) {
                        res.add(new ConstantValue(javaField.format("%H.%n"), (JavaConstant) boxed, boxed));
                    } else {
                        JavaConstant value = constantReflection.readFieldValue(javaField, null);
                        if (value != null) {
                            res.add(new ConstantValue(javaField.format("%H.%n"), value, boxed));
                            if (boxed instanceof Object[]) {
                                Object[] arr = (Object[]) boxed;
                                for (int i = 0; i < arr.length; i++) {
                                    JavaConstant element = constantReflection.readArrayElement(value, i);
                                    if (element != null) {
                                        res.add(new ConstantValue(javaField.format("%H.%n[" + i + "]"), element, arr[i]));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return res;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // Annotates the class type String
    public @TypeQualifier String[][] typeAnnotatedField1;
    // Annotates the array type String[][]
    public String @TypeQualifier [][]  typeAnnotatedField2;
    // Annotates the array type String[]
    String[] @TypeQualifier [] typeAnnotatedField3;

    public @TypeQualifier(comment = "comment1", id = 42) TypeUniverse.InnerClass.InnerInnerClass typeAnnotatedField4;
    public TypeUniverse.@TypeQualifier InnerClass.InnerInnerClass typeAnnotatedField5;
    public TypeUniverse.InnerClass.@TypeQualifier(comment = "47", id = -10) InnerInnerClass typeAnnotatedField6;

    public @TypeQualifier(comment = "comment2", id = 52) TypeUniverse.InnerClass.InnerInnerClass typeAnnotatedMethod1() { return null; }
    public TypeUniverse.@TypeQualifier InnerClass.InnerInnerClass typeAnnotatedMethod2() { return null; }
    public TypeUniverse.InnerClass.@TypeQualifier(comment = "57", id = -20) InnerInnerClass typeAnnotatedMethod3() { return null; }

    public void annotatedParameters1(
            @TypeQualifier(comment = "comment3", id = 62) TypeUniverse this,
            @TypeQualifier(comment = "comment4", id = 72) @ParameterQualifier String annotatedParam1,
            int notAnnotatedParam2,
            @ParameterQualifier(value = "foo", tag = 123)  Thread annotatedParam3) {
    }

    public synchronized Class<?> getArrayClass(Class<?> componentType) {
        Class<?> arrayClass = arrayClasses.get(componentType);
        if (arrayClass == null) {
            arrayClass = Array.newInstance(componentType, 0).getClass();
            arrayClasses.put(componentType, arrayClass);
        }
        return arrayClass;
    }

    public static int dimensions(Class<?> c) {
        if (c.getComponentType() != null) {
            return 1 + dimensions(c.getComponentType());
        }
        return 0;
    }

    private static void addClass(Class<?> c) {
        if (classes.add(c)) {
            if (c.getSuperclass() != null) {
                addClass(c.getSuperclass());
            }
            for (Class<?> sc : c.getInterfaces()) {
                addClass(sc);
            }
            for (Class<?> enclosing = c.getEnclosingClass(); enclosing != null; enclosing = enclosing.getEnclosingClass()) {
                addClass(enclosing);
            }
            for (Class<?> dc : c.getDeclaredClasses()) {
                addClass(dc);
            }
            for (Method m : c.getDeclaredMethods()) {
                addClass(m.getReturnType());
                for (Class<?> p : m.getParameterTypes()) {
                    addClass(p);
                }
            }

            if (c != void.class && dimensions(c) < 2) {
                Class<?> arrayClass = Array.newInstance(c, 0).getClass();
                arrayClasses.put(c, arrayClass);
                addClass(arrayClass);
            }
        }
    }

    public static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException | LinkageError cause) {
            throw new AssertionError(cause);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Method method, Object receiver, Object... arguments) {
        try {
            method.setAccessible(true);
            return (T) method.invoke(receiver, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause != null) {
                throw rethrow(cause);
            }
            throw new AssertionError(ex);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
