/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8051045 8166974
 * @enablePreview
 * @summary Test exceptions from invokedynamic and the bootstrap method
 * @run main BootstrapMethodErrorTest
 */


import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.*;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

public class BootstrapMethodErrorTest {

    static abstract class IndyClassloader extends ClassLoader {

        public IndyClassloader() {
            super(BootstrapMethodErrorTest.class.getClassLoader());
        }

        @Override
        public Class findClass(String name) throws ClassNotFoundException {
            byte[] b;
            try {
                b = loadClassData(name);
            }
            catch (Throwable th) {
                throw new ClassNotFoundException("Loading error", th);
            }
            return defineClass(name, b, 0, b.length);
        }

        static final String BOOTSTRAP_METHOD_CLASS_NAME = "C";

        static final String BOOTSTRAP_METHOD_NAME = "bsm";

        static final String INDY_CALLER_CLASS_NAME = "Exec";

        static final String BOOTSTRAP_METHOD_DESC = MethodType.methodType(
                Object.class, MethodHandles.Lookup.class, String.class, MethodType.class).
                toMethodDescriptorString();

        private byte[] loadClassData(String name) throws Exception {
            if (name.equals(BOOTSTRAP_METHOD_CLASS_NAME)) {
                return defineIndyBootstrapMethodClass();

            }
            else if (name.equals("Exec")) {
                return defineIndyCallingClass();
            }
            return null;
        }

        byte[] defineIndyCallingClass() {
                return ClassFile.of().build(ClassDesc.of(INDY_CALLER_CLASS_NAME),
                        clb -> clb
                                .withVersion(JAVA_8_VERSION, 0)
                                .withFlags(ACC_SUPER | ACC_PUBLIC)
                                .withSuperclass(CD_Object)
                                .withMethodBody("invoke", MethodTypeDesc.of(CD_void), ACC_PUBLIC | ACC_STATIC,
                                        cob -> {
                                            DirectMethodHandleDesc h = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.STATIC,
                                                    ClassDesc.of(BOOTSTRAP_METHOD_CLASS_NAME),
                                                    BOOTSTRAP_METHOD_NAME,
                                                    BOOTSTRAP_METHOD_DESC);
                                            cob.invokedynamic(DynamicCallSiteDesc.of(h, MethodTypeDesc.of(CD_void)));
                                            cob.return_();
                                        }
                                )
                );
        }

        byte[] defineIndyBootstrapMethodClass() {
            return ClassFile.of().build(ClassDesc.of(BOOTSTRAP_METHOD_CLASS_NAME),
                    clb -> clb
                            .withVersion(JAVA_8_VERSION, 0)
                            .withFlags(ACC_SUPER | ACC_PUBLIC)
                            .withSuperclass(CD_Object)
                            .withMethodBody(BOOTSTRAP_METHOD_NAME, MethodTypeDesc.ofDescriptor(BOOTSTRAP_METHOD_DESC), ACC_PUBLIC | ACC_STATIC,
                                    this::defineIndyBootstrapMethodBody
                            )
            );
        }

        void defineIndyBootstrapMethodBody(CodeBuilder cob) {
            cob.aconst_null();
            cob.areturn();
        }

        void invoke() throws Exception {
            Class.forName(BOOTSTRAP_METHOD_CLASS_NAME, true, this);
            Class<?> exec = Class.forName(INDY_CALLER_CLASS_NAME, true, this);
            exec.getMethod("invoke").invoke(null);
        }

        void test() throws Exception {
            Class.forName(BOOTSTRAP_METHOD_CLASS_NAME, true, this);
            Class<?> exec = Class.forName(INDY_CALLER_CLASS_NAME, true, this);
            try {
                exec.getMethod("invoke").invoke(null);
                throw new RuntimeException("Expected InvocationTargetException but no exception at all was thrown");
            } catch (InvocationTargetException e) {
                Throwable t = e.getCause();
                for (Class<? extends Throwable> etc : expectedThrowableClasses()) {
                    if (!etc.isInstance(t)) {
                        throw new RuntimeException(
                                "Expected " + etc.getName() + " but got another exception: "
                                + t.getClass().getName(),
                                t);
                    }
                    t = t.getCause();
                }
            }
        }

        abstract List<Class<? extends Throwable>> expectedThrowableClasses();
    }

    // Methods called by a bootstrap method

    public static CallSite getCallSite() {
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                    BootstrapMethodErrorTest.class,
                    "target",
                    MethodType.methodType(Object.class, Object.class));
            return new ConstantCallSite(mh);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Object target(Object o) {
        return null;
    }

    static class TestThrowable extends Throwable {}
    public static void throwsTestThrowable() throws Throwable {
        throw new TestThrowable();
    }

    static class TestError extends Error {}
    public static void throwsTestError() {
        throw new TestError();
    }

    static class TestRuntimeException extends RuntimeException {}
    public static void throwsTestRuntimeException() {
        throw new TestRuntimeException();
    }

    static class TestCheckedException extends Exception {}
    public static void throwsTestCheckedException() throws TestCheckedException {
        throw new TestCheckedException();
    }


    // Test classes

    static class InaccessibleBootstrapMethod extends IndyClassloader {

        byte[] defineIndyBootstrapMethodClass() {
            return ClassFile.of().build(ClassDesc.of(BOOTSTRAP_METHOD_CLASS_NAME),
                    clb -> clb
                            .withVersion(JAVA_8_VERSION, 0)
                            .withFlags(ACC_SUPER | ACC_PUBLIC)
                            .withSuperclass(CD_Object)
                            .withMethodBody(BOOTSTRAP_METHOD_NAME, MethodTypeDesc.ofDescriptor(BOOTSTRAP_METHOD_DESC), ACC_PRIVATE | ACC_STATIC,
                                    this::defineIndyBootstrapMethodBody
                            )
            );
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(IllegalAccessError.class);
        }
    }

    static class BootstrapMethodDoesNotReturnCallSite extends IndyClassloader {

        void defineIndyBootstrapMethodBody(CodeBuilder cob) {
            // return null from the bootstrap method,
            // which cannot be cast to CallSite
            cob.aconst_null();
            cob.areturn();
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(BootstrapMethodError.class, ClassCastException.class);
        }
    }

    static class BootstrapMethodCallSiteHasWrongTarget extends IndyClassloader {

        @Override
        void defineIndyBootstrapMethodBody(CodeBuilder cob) {
            // Invoke the method BootstrapMethodErrorTest.getCallSite to obtain
            // a CallSite instance whose target is different from that of
            // the indy call site
            cob.invokestatic(ClassDesc.of("BootstrapMethodErrorTest"), "getCallSite",
                             MethodTypeDesc.ofDescriptor("()Ljava/lang/invoke/CallSite;"));
            cob.areturn();
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(BootstrapMethodError.class, WrongMethodTypeException.class);
        }
    }

    abstract static class BootstrapMethodThrows extends IndyClassloader {
        final String methodName;

        public BootstrapMethodThrows(Class<? extends Throwable> t) {
            this.methodName = "throws" + t.getSimpleName();
        }

        @Override
        void defineIndyBootstrapMethodBody(CodeBuilder cob) {
            // Invoke the method whose name is methodName which will throw
            // an exception
            cob.invokestatic(ClassDesc.of("BootstrapMethodErrorTest"), methodName,
                             MethodTypeDesc.of(CD_void));
            cob.aconst_null();
            cob.areturn();
        }
    }

    static class BootstrapMethodThrowsThrowable extends BootstrapMethodThrows {

        public BootstrapMethodThrowsThrowable() {
            super(TestThrowable.class);
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(BootstrapMethodError.class, TestThrowable.class);
        }
    }

    static class BootstrapMethodThrowsError extends BootstrapMethodThrows {

        public BootstrapMethodThrowsError() {
            super(TestError.class);
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(TestError.class);
        }
    }

    static class BootstrapMethodThrowsRuntimeException extends BootstrapMethodThrows {

        public BootstrapMethodThrowsRuntimeException() {
            super(TestRuntimeException.class);
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(BootstrapMethodError.class, TestRuntimeException.class);
        }
    }

    static class BootstrapMethodThrowsCheckedException extends BootstrapMethodThrows {

        public BootstrapMethodThrowsCheckedException() {
            super(TestCheckedException.class);
        }

        @Override
        List<Class<? extends Throwable>> expectedThrowableClasses() {
            return List.of(BootstrapMethodError.class, TestCheckedException.class);
        }
    }


    public static void main(String[] args) throws Exception {
        new InaccessibleBootstrapMethod().test();
        new BootstrapMethodDoesNotReturnCallSite().test();
        new BootstrapMethodCallSiteHasWrongTarget().test();
        new BootstrapMethodThrowsThrowable().test();
        new BootstrapMethodThrowsError().test();
        new BootstrapMethodThrowsRuntimeException().test();
        new BootstrapMethodThrowsCheckedException().test();
    }
}
