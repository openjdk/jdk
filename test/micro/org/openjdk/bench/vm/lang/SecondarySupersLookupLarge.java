/*
 * Copyright 2026 IBM Corporation. All rights reserved.
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
package org.openjdk.bench.vm.lang;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.security.SecureClassLoader;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import static java.lang.classfile.ClassFile.*;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 5)
public class SecondarySupersLookupLarge {
    @Param("100")
    private int interfaceCount;

    private static final MethodTypeDesc MTD_void_String
        = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_boolean_Class
        = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_Object_boolean
        = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object);

    static class MyLoader extends SecureClassLoader {
        MyLoader(ClassLoader parent) {
            super(parent);
        }
        public Class defineClass(String name, byte[] bytes) {
            try {
                try (FileOutputStream fos = new FileOutputStream("/tmp/" + name + ".class")) {
                    fos.write(bytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    MyLoader LOADER;

    static String interfaceName(Integer suffix) {
        return String.format("I%04d", suffix);
    }

    private Class<?> genInterface(String name) {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(name),
                (ClassBuilder clb) -> clb.withFlags(ACC_INTERFACE | ACC_PUBLIC | ACC_ABSTRACT));
        return LOADER.defineClass(name, bytes);
    }

    private Class<?>[] genInterfaces(Function<Integer, String> name, int first, int last) {
        Class<?>[] interfaces = new Class[last + 1];
        for (int n = first; n <= last; n++) {
            interfaces[n] = genInterface(name.apply(n));
        }
        return interfaces;
    }

    Class<?>[] goodInterfaces, badInterfaces;

    public Class<?>[] genClasses(Class<?>[] interfaces, int length) {
        Class<?>[] klasses = new Class[length];
        for (int n = 0; n < length; n++) {
            var klass = genClass(List.of(interfaces).stream()
                .map(iface -> ClassDesc.of(iface.getName())).toList(), "Impl" + n);
            klasses[n] = klass;
        }
        return klasses;
    }

    private void genClassBody(ClassBuilder clb, List<ClassDesc> interfaceDescriptors) {
        clb = clb.withFlags(ACC_PUBLIC);
        var cpool = clb.constantPool();
        var interfaceEntries = interfaceDescriptors.stream().map(cpool::classEntry).toList();
        var CD_AClass = ClassDesc.of(AClass.class.getName());
        clb.withSuperclass(CD_AClass)
            .withInterfaces(interfaceEntries)
            .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void,
                ACC_PUBLIC,
                mb -> mb.withCode(cob -> cob
                    .aload(0)
                    .invokespecial(CD_AClass,
                        ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                    .return_()))
            .withMethod("isInstance", MTD_boolean_Class,
                ACC_PUBLIC,
                mb -> mb.withCode(cob -> cob
                    .aload(1)
                    .aload(0)
                    .invokevirtual(ConstantDescs.CD_Class, "isInstance",
                        MethodTypeDesc.of(ConstantDescs.CD_boolean,
                                          ConstantDescs.CD_Object))
                    .ireturn()))
            .withMethod("instanceOfPositive",
                MethodTypeDesc.of(ConstantDescs.CD_boolean,
                                  ConstantDescs.CD_Object),
                ACC_PUBLIC,
                mb -> mb.withCode(cob -> cob
                    .aload(1)
                    .instanceOf(interfaceEntries.get(random.nextInt(interfaceEntries.size())))
                    .ireturn()
                    ))
            .withMethod("instanceOfNegative",
                MethodTypeDesc.of(ConstantDescs.CD_boolean,
                                  ConstantDescs.CD_Object),
                ACC_PUBLIC,
                mb -> mb.withCode(cob -> cob
                    .aload(1)
                    .instanceOf(ClassDesc.of("J"))
                    .ireturn()
                    ))
            .withMethod("checkCast",
                MethodTypeDesc.of(ConstantDescs.CD_Object,
                                  ConstantDescs.CD_Object),
                ACC_PUBLIC,
                mb -> mb.withCode(cob -> cob
                    .aload(1)
                    .checkcast(interfaceEntries.get(random.nextInt(interfaceEntries.size())))
                    .areturn()
                    ));
    }

    private Class<?> genClass(List<ClassDesc> interfaceDescriptors, String name) {
        byte[] bytes = ClassFile.of()
            .build(ClassDesc.of(name), clb -> genClassBody(clb, interfaceDescriptors));
        return LOADER.defineClass(name, bytes);
    }

    private Class<?> genClass(Class<?>[] interfaces, String name) {
        return genClass(List.of(interfaces).stream()
            .map(iface -> ClassDesc.of(iface.getName())).toList(), name);
    }

    final Random random = new Random(0);

    AClass testInstance;
    AClass[] testInstances;
    public final static int TESTINSTANCES=10;
    Class<?> targetInterface;
    Class testInterface;

    @Setup
    public void init() throws Exception {
        LOADER = new MyLoader(AClass.class.getClassLoader());

        if (J.class.getClassLoader() != AClass.class.getClassLoader())
            throw new RuntimeException();

        goodInterfaces = genInterfaces(SecondarySupersLookupLarge::interfaceName, 0, interfaceCount);
        badInterfaces = genInterfaces(n -> interfaceName(n) + "Bad", 0, interfaceCount);
        genInterface("J");

        Class<?> barf = genClass(goodInterfaces, "Humongous");
        testInstance = (AClass)(barf.getConstructor().newInstance());

        testInstances = new AClass[TESTINSTANCES];
        for (int i = 0; i < testInstances.length; i++) {
            var anObject = genClass(goodInterfaces, String.format("Humongous%02d", i))
                .getConstructor().newInstance();
            testInstances[i] = (AClass)anObject;
        }
    }

    private static void test(Object obj, Class<?> cls, boolean expected) {
        if (cls.isInstance(obj) != expected) {
            throw new InternalError(obj.getClass() + " " + cls + " " + expected);
        }
    }


    // Arbitrary-sized conformance tests.
    @Benchmark public void testPositiveN() {
        for (var iFace : goodInterfaces) {
            test(testInstance, iFace, true);
        }
    }
    @Benchmark public void testNegativeN() {
        for (var iFace : badInterfaces) {
            test(testInstance, iFace, false);
        }
    }

    // These invoke obj instanceof/checkcast T. We use an array of
    // testInstances instead of a single instance in order to prevent
    // C2 from removing our lookup.
    @Benchmark
    @OperationsPerInvocation(TESTINSTANCES)
    public void checkCast(Blackhole bh) {
        for (Object obj : testInstances) {
            bh.consume(testInstance.checkCast(obj));
        }
    }
    @Benchmark
    @OperationsPerInvocation(TESTINSTANCES)
    public void instanceOfPositive(Blackhole bh) {
        for (var obj : testInstances) {
            var found = testInstance.instanceOfPositive(obj);
            if (!found)  throw new RuntimeException();
            bh.consume(testInstance.instanceOfPositive(obj));
        }
    }
    @Benchmark
    @OperationsPerInvocation(TESTINSTANCES)
    public void instanceOfNegative(Blackhole bh) {
        var found = false;
        for (Object obj : testInstances) {
            found |= testInstance.instanceOfNegative(obj);
            bh.consume(found);
        }
        if (found)  throw new RuntimeException();
    }
}
