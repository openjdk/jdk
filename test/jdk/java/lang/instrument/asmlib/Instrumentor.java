/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

package asmlib;

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.MethodBuilder;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.MethodTransform;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;

import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static jdk.internal.classfile.Classfile.ACC_NATIVE;

public class Instrumentor {

    public static Instrumentor instrFor(byte[] classData) {
        return new Instrumentor(classData);
    }

    private final ClassModel model;
    private ClassTransform transform = ClassTransform.ACCEPT_ALL;
    private final AtomicInteger matches = new AtomicInteger(0);

    private Instrumentor(byte[] classData) {
        model = Classfile.parse(classData);
    }

    public synchronized Instrumentor addMethodEntryInjection(String methodName, Consumer<CodeBuilder> injector) {
        transform = transform.andThen(ClassTransform.transformingMethodBodies(mm -> {
            if (mm.methodName().equalsString(methodName)) {
                matches.getAndIncrement();
                return true;
            }
            return false;
        }, new CodeTransform() {
            @Override
            public void atStart(CodeBuilder builder) {
                injector.accept(builder);
            }

            @Override
            public void accept(CodeBuilder builder, CodeElement element) {
                builder.accept(element);
            }
        }));
        return this;
    }

    public synchronized Instrumentor addNativeMethodTrackingInjection(String prefix, BiConsumer<String, CodeBuilder> injector) {
        transform = transform.andThen(new ClassTransform() {
            private final Set<Consumer<ClassBuilder>> wmGenerators = new HashSet<>();

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                if (element instanceof MethodModel mm && mm.flags().has(AccessFlag.NATIVE)) {
                    matches.getAndIncrement();

                    String name = mm.methodName().stringValue();
                    String newName = prefix + name;
                    MethodTypeDesc mt = mm.methodTypeSymbol();
                    wmGenerators.add(clb -> clb.transformMethod(mm, new MethodTransform() {
                        @Override
                        public void accept(MethodBuilder mb, MethodElement me) {
                            if (me instanceof AccessFlags flags) {
                                mb.withFlags(flags.flagsMask() & ~ACC_NATIVE);
                            } else if (!(me instanceof CodeModel)) {
                                mb.with(me);
                            }
                        }

                        @Override
                        public void atEnd(MethodBuilder mb) {
                            Consumer<CodeBuilder> injection = cb -> injector.accept(name, cb);
                            mb.withCode(injection.andThen(cb -> {
                                int ptr;
                                boolean isStatic = mm.flags().has(AccessFlag.STATIC);
                                if (!isStatic) {
                                    cb.aload(0); // load "this"
                                    ptr = 1;
                                } else {
                                    ptr = 0;
                                }

                                // load method parameters
                                for (int i = 0; i < mt.parameterCount(); i++) {
                                    TypeKind kind = TypeKind.fromDescriptor(mt.parameterType(i).descriptorString());
                                    cb.loadInstruction(kind, ptr);
                                    ptr += kind.slotSize();
                                }

                                cb.invokeInstruction(isStatic ? Opcode.INVOKESTATIC : Opcode.INVOKESPECIAL,
                                        model.thisClass().asSymbol(), newName, mt, false);
                                cb.returnInstruction(TypeKind.fromDescriptor(mt.returnType().descriptorString()));
                            }));
                        }
                    }));

                    builder.withMethod(newName, mt, mm.flags().flagsMask(), mm::forEachElement);
                } else {
                    builder.accept(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                wmGenerators.forEach(e -> e.accept(builder));
            }
        });
        return this;
    }

    public synchronized byte[] apply() {
        var bytes = model.transform(transform);

        return matches.get() == 0 ? null : bytes;
    }
}
