/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.classfile;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import java.lang.constant.ConstantDescs;
import java.util.stream.Stream;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import jdk.internal.classfile.components.ClassRemapper;

/**
 * Transforms
 */
public class Transforms {

    public static final ClassTransform threeLevelNoop = (cb, ce) -> {
        if (ce instanceof MethodModel mm) {
            cb.transformMethod(mm, (mb, me) -> {
                if (me instanceof CodeModel xm) {
                    mb.transformCode(xm, CodeTransform.ACCEPT_ALL);
                }
                else
                    mb.with(me);
            });
        }
        else
            cb.with(ce);
    };

    private static final ClassTransform threeLevelNoopPipedCMC_seed = (cb, ce) -> {
        if (ce instanceof MethodModel mm) {
            MethodTransform transform = (mb, me) -> {
                if (me instanceof CodeModel xm) {
                    mb.transformCode(xm, CodeTransform.ACCEPT_ALL.andThen(CodeTransform.ACCEPT_ALL));
                }
                else
                    mb.with(me);
            };
            cb.transformMethod(mm, transform);
        }
        else
            cb.with(ce);
    };

    static final ClassTransform twoLevelNoop = (cb, ce) -> {
        if (ce instanceof MethodModel mm) {
            cb.transformMethod(mm, MethodTransform.ACCEPT_ALL);
        }
        else
            cb.with(ce);
    };

    static final ClassTransform oneLevelNoop = ClassTransform.ACCEPT_ALL;

    public static final List<ClassTransform> noops = List.of(threeLevelNoop, twoLevelNoop, oneLevelNoop);

    public enum NoOpTransform {
        ARRAYCOPY(bytes -> {
            byte[] bs = new byte[bytes.length];
            System.arraycopy(bytes, 0, bs, 0, bytes.length);
            return bs;
        }),
        SHARED_1(true, oneLevelNoop),
        SHARED_2(true, twoLevelNoop),
        SHARED_3(true, threeLevelNoop),
        SHARED_3P(true, threeLevelNoop.andThen(threeLevelNoop)),
        SHARED_3L(true, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)),
        SHARED_3Sx(true, threeLevelNoopPipedCMC_seed.andThen(ClassTransform.ACCEPT_ALL)),
        SHARED_3bc(true, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)
                .andThen(ClassTransform.ACCEPT_ALL)
                .andThen(ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL))),
        UNSHARED_1(false, oneLevelNoop),
        UNSHARED_2(false, twoLevelNoop),
        UNSHARED_3(false, threeLevelNoop),
        SHARED_3_NO_STACKMAP(true, threeLevelNoop, ClassFile.StackMapsOption.DROP_STACK_MAPS),
        SHARED_3_NO_DEBUG(true, threeLevelNoop, ClassFile.DebugElementsOption.DROP_DEBUG, ClassFile.LineNumbersOption.DROP_LINE_NUMBERS),
        CLASS_REMAPPER(bytes ->
                ClassRemapper.of(Map.of()).remapClass(ClassFile.of(), ClassFile.of().parse(bytes)));

        // Need ASM, LOW_UNSHARED

        public final UnaryOperator<byte[]> transform;
        public final boolean shared;
        public final ClassTransform classTransform;
        public final ClassFile cc;

        NoOpTransform(UnaryOperator<byte[]> transform) {
            this.transform = transform;
            classTransform = null;
            shared = false;
            cc = ClassFile.of();
        }

        NoOpTransform(boolean shared,
                      ClassTransform classTransform,
                      ClassFile.Option... options) {
            this.shared = shared;
            this.classTransform = classTransform;
            this.cc = ClassFile.of(
                    shared
                    ? options
                    : Stream.concat(Stream.of(options), Stream.of(ClassFile.ConstantPoolSharingOption.NEW_POOL)).toArray(ClassFile.Option[]::new));
            this.transform = bytes -> cc.transformClass(cc.parse(bytes), classTransform);
        }
    }

    public enum InjectNopTransform {
        NOP_SHARED(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.transformClass(cm, (cb, ce) -> {
                if (ce instanceof MethodModel mm) {
                    cb.transformMethod(mm, (mb, me) -> {
                        if (me instanceof CodeModel xm) {
                            mb.withCode(xb -> {
                                xb.nop();
                                xm.forEach(new Consumer<>() {
                                    @Override
                                    public void accept(CodeElement e) {
                                        xb.with(e);
                                    }
                                });
                            });
                        }
                        else
                            mb.with(me);
                    });
                }
                else
                    cb.with(ce);
            });
        });

        public final UnaryOperator<byte[]> transform;

        InjectNopTransform(UnaryOperator<byte[]> transform) {
            this.transform = transform;
        }
    }

    public enum SimpleTransform {
        HIGH_SHARED_ADD_FIELD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.transformClass(cm, new ClassTransform() {
                @Override
                public void accept(ClassBuilder builder, ClassElement element) {
                    builder.with(element);
                }

                @Override
                public void atEnd(ClassBuilder builder) {
                    builder.withField("argleBargleWoogaWooga", ConstantDescs.CD_int, b -> { });
                }
            });
        }),
        HIGH_UNSHARED_ADD_FIELD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.build(cm.thisClass().asSymbol(),
                                   cb -> {
                                       cm.forEach(cb);
                                       cb.withField("argleBargleWoogaWooga", ConstantDescs.CD_int, b -> { });
                                   });
        }),
        HIGH_SHARED_DEL_METHOD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.transformClass(cm, (builder, element) -> {
                if (!(element instanceof MethodModel mm))
                    builder.with(element);
            });
        }),
        HIGH_UNSHARED_DEL_METHOD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.build(cm.thisClass().asSymbol(),
                                   cb -> {
                                       cm.forEach(element -> {
                                           if (element instanceof MethodModel mm
                                               && mm.methodName().stringValue().equals("hashCode")
                                               && mm.methodType().stringValue().equals("()Z")) {

                                           }
                                           else
                                               cb.with(element);
                                       });
                                   });
        });

        public final UnaryOperator<byte[]> transform;

        SimpleTransform(UnaryOperator<byte[]> transform) {
            this.transform = transform;
        }
    }

}
