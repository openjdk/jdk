/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Verifies a class declaring one or more {@linkplain OptionValue options} has a class initializer
 * that only initializes the option(s). This sanity check mitigates the possibility of an option
 * value being used before being set.
 */
public class OptionsVerifierTest {

    @Test
    public void verifyOptions() throws IOException {
        try (Classpath cp = new Classpath()) {
            HashSet<Class<?>> checked = new HashSet<>();
            for (OptionDescriptors opts : ServiceLoader.load(OptionDescriptors.class, getClass().getClassLoader())) {
                for (OptionDescriptor desc : opts) {
                    OptionsVerifier.checkClass(desc.getDeclaringClass(), desc, checked, cp);
                }
            }
        }
    }

    static class Classpath implements AutoCloseable {
        private final Map<String, Object> entries = new LinkedHashMap<>();

        Classpath() throws IOException {
            List<String> names = new ArrayList<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
            if (GraalTest.Java8OrEarlier) {
                names.addAll(Arrays.asList(System.getProperty("sun.boot.class.path").split(File.pathSeparator)));
            } else {
                names.addAll(Arrays.asList(System.getProperty("jdk.module.path").split(File.pathSeparator)));
            }
            for (String n : names) {
                File path = new File(n);
                if (path.exists()) {
                    if (path.isDirectory()) {
                        entries.put(n, path);
                    } else if (n.endsWith(".jar") || n.endsWith(".zip")) {
                        URL url = new URL("jar", "", "file:" + n + "!/");
                        entries.put(n, new URLClassLoader(new URL[]{url}));
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            for (Object e : entries.values()) {
                if (e instanceof URLClassLoader) {
                    ((URLClassLoader) e).close();
                }
            }
        }

        public byte[] getInputStream(String classFilePath) throws IOException {
            for (Object e : entries.values()) {
                if (e instanceof File) {
                    File path = new File((File) e, classFilePath.replace('/', File.separatorChar));
                    if (path.exists()) {
                        return Files.readAllBytes(path.toPath());
                    }
                } else {
                    assert e instanceof URLClassLoader;
                    URLClassLoader ucl = (URLClassLoader) e;
                    try (InputStream in = ucl.getResourceAsStream(classFilePath)) {
                        if (in != null) {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            int nRead;
                            byte[] data = new byte[1024];
                            while ((nRead = in.read(data, 0, data.length)) != -1) {
                                buffer.write(data, 0, nRead);
                            }
                            return buffer.toByteArray();
                        }
                    }
                }
            }
            return null;
        }
    }

    static final class OptionsVerifier extends ClassVisitor {

        public static void checkClass(Class<?> cls, OptionDescriptor option, Set<Class<?>> checked, Classpath cp) throws IOException {
            if (!checked.contains(cls)) {
                checked.add(cls);
                Class<?> superclass = cls.getSuperclass();
                if (superclass != null && !superclass.equals(Object.class)) {
                    checkClass(superclass, option, checked, cp);
                }

                String classFilePath = cls.getName().replace('.', '/') + ".class";
                ClassReader cr = new ClassReader(Objects.requireNonNull(cp.getInputStream(classFilePath), "Could not find class file for " + cls.getName()));

                ClassVisitor cv = new OptionsVerifier(cls, option);
                cr.accept(cv, 0);
            }
        }

        /**
         * The option field context of the verification.
         */
        private final OptionDescriptor option;

        /**
         * The class in which {@link #option} is declared or a super-class of that class. This is
         * the class whose {@code <clinit>} method is being verified.
         */
        private final Class<?> cls;

        /**
         * Source file context for error reporting.
         */
        String sourceFile = null;

        /**
         * Line number for error reporting.
         */
        int lineNo = -1;

        final Class<?>[] boxingTypes = {Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Float.class, Long.class, Double.class};

        private static Class<?> resolve(String name) {
            try {
                return Class.forName(name.replace('/', '.'));
            } catch (ClassNotFoundException e) {
                throw new InternalError(e);
            }
        }

        OptionsVerifier(Class<?> cls, OptionDescriptor desc) {
            super(Opcodes.ASM5);
            this.cls = cls;
            this.option = desc;
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFile = source;
        }

        void verify(boolean condition, String message) {
            if (!condition) {
                error(message);
            }
        }

        void error(String message) {
            String errorMessage = format(
                            "%s:%d: Illegal code in %s.<clinit> which may be executed when %s.%s is initialized:%n%n    %s%n%n" + "The recommended solution is to move " + option.getName() +
                                            " into a separate class (e.g., %s.Options).%n",
                            sourceFile, lineNo, cls.getSimpleName(), option.getDeclaringClass().getSimpleName(), option.getName(),
                            message, option.getDeclaringClass().getSimpleName());
            throw new InternalError(errorMessage);

        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String d, String signature, String[] exceptions) {
            if (name.equals("<clinit>")) {
                return new MethodVisitor(Opcodes.ASM5) {

                    @Override
                    public void visitLineNumber(int line, Label start) {
                        lineNo = line;
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                            verify(resolve(owner).equals(option.getDeclaringClass()), format("store to field %s.%s", resolve(owner).getSimpleName(), fieldName));
                            verify(opcode != Opcodes.PUTFIELD, format("store to non-static field %s.%s", resolve(owner).getSimpleName(), fieldName));
                        }
                    }

                    private Executable resolveMethod(String owner, String methodName, String methodDesc) {
                        Class<?> declaringClass = resolve(owner);
                        if (methodName.equals("<init>")) {
                            for (Constructor<?> c : declaringClass.getDeclaredConstructors()) {
                                if (methodDesc.equals(Type.getConstructorDescriptor(c))) {
                                    return c;
                                }
                            }
                        } else {
                            Type[] argumentTypes = Type.getArgumentTypes(methodDesc);
                            for (Method m : declaringClass.getDeclaredMethods()) {
                                if (m.getName().equals(methodName)) {
                                    if (Arrays.equals(argumentTypes, Type.getArgumentTypes(m))) {
                                        if (Type.getReturnType(methodDesc).equals(Type.getReturnType(m))) {
                                            return m;
                                        }
                                    }
                                }
                            }
                        }
                        throw new NoSuchMethodError(declaringClass + "." + methodName + methodDesc);
                    }

                    /**
                     * Checks whether a given method is allowed to be called.
                     */
                    private boolean checkInvokeTarget(Executable method) {
                        Class<?> holder = method.getDeclaringClass();
                        if (method instanceof Constructor) {
                            if (OptionValue.class.isAssignableFrom(holder)) {
                                return true;
                            }
                        } else if (Arrays.asList(boxingTypes).contains(holder)) {
                            return method.getName().equals("valueOf");
                        } else if (method.getDeclaringClass().equals(Class.class)) {
                            return method.getName().equals("desiredAssertionStatus");
                        }
                        return false;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
                        Executable callee = resolveMethod(owner, methodName, methodDesc);
                        verify(checkInvokeTarget(callee), "invocation of " + callee);
                    }
                };
            } else {
                return null;
            }
        }
    }

}
