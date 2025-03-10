/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.classfile.impl;

import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassReader;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.ObjectVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.SimpleVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.UninitializedVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.VerificationTypeInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.attribute.StackMapFrameInfo.VerificationTypeInfo.*;
import static java.util.Objects.requireNonNull;

public class StackMapDecoder {

    private static final int
                    SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247,
                    SAME_EXTENDED = 251;
    private static final StackMapFrameInfo[] NO_STACK_FRAME_INFOS = {};

    private final ClassReader classReader;
    private final int pos;
    private final LabelContext ctx;
    private final List<VerificationTypeInfo> initFrameLocals;
    private int p;

    StackMapDecoder(ClassReader classReader, int pos, LabelContext ctx, List<VerificationTypeInfo> initFrameLocals) {
        this.classReader = classReader;
        this.pos = pos;
        this.ctx = ctx;
        this.initFrameLocals = initFrameLocals;
    }

    static List<VerificationTypeInfo> initFrameLocals(MethodModel method) {
        return initFrameLocals(method.parent().orElseThrow().thisClass(),
                method.methodName().stringValue(),
                method.methodTypeSymbol(),
                method.flags().has(AccessFlag.STATIC));
    }

    public static List<VerificationTypeInfo> initFrameLocals(ClassEntry thisClass, String methodName, MethodTypeDesc methodType, boolean isStatic) {
        VerificationTypeInfo vtis[];
        int i = 0;
        if (!isStatic) {
            vtis = new VerificationTypeInfo[methodType.parameterCount() + 1];
            if ("<init>".equals(methodName) && !ConstantDescs.CD_Object.equals(thisClass.asSymbol())) {
                vtis[i++] = SimpleVerificationTypeInfo.UNINITIALIZED_THIS;
            } else {
                vtis[i++] = new StackMapDecoder.ObjectVerificationTypeInfoImpl(thisClass);
            }
        } else {
            vtis = new VerificationTypeInfo[methodType.parameterCount()];
        }
        for (int pi = 0; pi < methodType.parameterCount(); pi++) {
            var arg = methodType.parameterType(pi);
            vtis[i++] = switch (arg.descriptorString().charAt(0)) {
                case 'I', 'S', 'C' ,'B', 'Z' -> SimpleVerificationTypeInfo.INTEGER;
                case 'J' -> SimpleVerificationTypeInfo.LONG;
                case 'F' -> SimpleVerificationTypeInfo.FLOAT;
                case 'D' -> SimpleVerificationTypeInfo.DOUBLE;
                case 'V' -> throw new IllegalArgumentException("Illegal method argument type: " + arg);
                default -> new StackMapDecoder.ObjectVerificationTypeInfoImpl(TemporaryConstantPool.INSTANCE.classEntry(arg));
            };
        }
        return List.of(vtis);
    }

    public static void writeFrames(BufWriter b, List<StackMapFrameInfo> entries) {
        var buf = (BufWriterImpl)b;
        var dcb = (DirectCodeBuilder)buf.labelContext();
        var mi = dcb.methodInfo();
        var prevLocals = StackMapDecoder.initFrameLocals(buf.thisClass(),
                mi.methodName().stringValue(),
                mi.methodTypeSymbol(),
                (mi.methodFlags() & ACC_STATIC) != 0);
        int prevOffset = -1;
        // avoid using method handles due to early bootstrap
        StackMapFrameInfo[] infos = entries.toArray(NO_STACK_FRAME_INFOS);
        //sort by resolved label offsets first to allow unordered entries
        Arrays.sort(infos, new Comparator<StackMapFrameInfo>() {
            public int compare(final StackMapFrameInfo o1, final StackMapFrameInfo o2) {
                return Integer.compare(dcb.labelToBci(o1.target()), dcb.labelToBci(o2.target()));
            }
        });
        b.writeU2(infos.length);
        for (var fr : infos) {
            int offset = dcb.labelToBci(fr.target());
            if (offset == prevOffset) {
                throw new IllegalArgumentException("Duplicated stack frame bytecode index: " + offset);
            }
            writeFrame(buf, offset - prevOffset - 1, prevLocals, fr);
            prevOffset = offset;
            prevLocals = fr.locals();
        }
    }

    private static void writeFrame(BufWriterImpl out, int offsetDelta, List<VerificationTypeInfo> prevLocals, StackMapFrameInfo fr) {
        if (offsetDelta < 0) throw new IllegalArgumentException("Invalid stack map frames order");
        if (fr.stack().isEmpty()) {
            int commonLocalsSize = Math.min(prevLocals.size(), fr.locals().size());
            int diffLocalsSize = fr.locals().size() - prevLocals.size();
            if (-3 <= diffLocalsSize && diffLocalsSize <= 3 && equals(fr.locals(), prevLocals, commonLocalsSize)) {
                if (diffLocalsSize == 0 && offsetDelta < 64) { //same frame
                    out.writeU1(offsetDelta);
                } else {   //chop, same extended or append frame
                    out.writeU1U2(251 + diffLocalsSize, offsetDelta);
                    for (int i=commonLocalsSize; i<fr.locals().size(); i++) writeTypeInfo(out, fr.locals().get(i));
                }
                return;
            }
        } else if (fr.stack().size() == 1 && fr.locals().equals(prevLocals)) {
            if (offsetDelta < 64) {  //same locals 1 stack item frame
                out.writeU1(64 + offsetDelta);
            } else {  //same locals 1 stack item extended frame
                out.writeU1U2(247, offsetDelta);
            }
            writeTypeInfo(out, fr.stack().get(0));
            return;
        }
        //full frame
        out.writeU1U2U2(255, offsetDelta, fr.locals().size());
        for (var l : fr.locals()) writeTypeInfo(out, l);
        out.writeU2(fr.stack().size());
        for (var s : fr.stack()) writeTypeInfo(out, s);
    }

    private static boolean equals(List<VerificationTypeInfo> l1, List<VerificationTypeInfo> l2, int compareSize) {
        for (int i = 0; i < compareSize; i++) {
            if (!l1.get(i).equals(l2.get(i))) return false;
        }
        return true;
    }

    private static void writeTypeInfo(BufWriterImpl bw, VerificationTypeInfo vti) {
        int tag = vti.tag();
        switch (tag) {
            case ITEM_TOP, ITEM_INTEGER, ITEM_FLOAT, ITEM_DOUBLE, ITEM_LONG, ITEM_NULL,
                 ITEM_UNINITIALIZED_THIS ->
                bw.writeU1(tag);
            case ITEM_OBJECT ->
                bw.writeU1U2(tag, bw.cpIndex(((ObjectVerificationTypeInfo)vti).className()));
            case ITEM_UNINITIALIZED ->
                bw.writeU1U2(tag, bw.labelContext().labelToBci(((UninitializedVerificationTypeInfo)vti).newTarget()));
            default -> throw new IllegalArgumentException("Invalid verification type tag: " + vti.tag());
        }
    }

    List<StackMapFrameInfo> entries() {
        p = pos;
        List<VerificationTypeInfo> locals = initFrameLocals, stack = List.of();
        int bci = -1;
        var entries = new StackMapFrameInfo[u2()];
        for (int ei = 0; ei < entries.length; ei++) {
            int frameType = classReader.readU1(p++);
            if (frameType < 64) {
                bci += frameType + 1;
                stack = List.of();
            } else if (frameType < 128) {
                bci += frameType - 63;
                stack = List.of(readVerificationTypeInfo());
            } else {
                if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED)
                    throw new IllegalArgumentException("Invalid stackmap frame type: " + frameType);
                bci += u2() + 1;
                if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                    stack = List.of(readVerificationTypeInfo());
                } else if (frameType < SAME_EXTENDED) {
                    locals = locals.subList(0, locals.size() + frameType - SAME_EXTENDED);
                    stack = List.of();
                } else if (frameType == SAME_EXTENDED) {
                    stack = List.of();
                } else if (frameType < SAME_EXTENDED + 4) {
                    int actSize = locals.size();
                    var newLocals = locals.toArray(new VerificationTypeInfo[actSize + frameType - SAME_EXTENDED]);
                    for (int i = actSize; i < newLocals.length; i++)
                        newLocals[i] = readVerificationTypeInfo();
                    locals = List.of(newLocals);
                    stack = List.of();
                } else {
                    var newLocals = new VerificationTypeInfo[u2()];
                    for (int i=0; i<newLocals.length; i++)
                        newLocals[i] = readVerificationTypeInfo();
                    var newStack = new VerificationTypeInfo[u2()];
                    for (int i=0; i<newStack.length; i++)
                        newStack[i] = readVerificationTypeInfo();
                    locals = List.of(newLocals);
                    stack = List.of(newStack);
                }
            }
            entries[ei] = new StackMapFrameImpl(frameType,
                        ctx.getLabel(bci),
                        locals,
                        stack);
        }
        return List.of(entries);
    }

    private VerificationTypeInfo readVerificationTypeInfo() {
        int tag = classReader.readU1(p++);
        return switch (tag) {
            case ITEM_TOP -> SimpleVerificationTypeInfo.TOP;
            case ITEM_INTEGER -> SimpleVerificationTypeInfo.INTEGER;
            case ITEM_FLOAT -> SimpleVerificationTypeInfo.FLOAT;
            case ITEM_DOUBLE -> SimpleVerificationTypeInfo.DOUBLE;
            case ITEM_LONG -> SimpleVerificationTypeInfo.LONG;
            case ITEM_NULL -> SimpleVerificationTypeInfo.NULL;
            case ITEM_UNINITIALIZED_THIS -> SimpleVerificationTypeInfo.UNINITIALIZED_THIS;
            case ITEM_OBJECT -> new ObjectVerificationTypeInfoImpl(classReader.entryByIndex(u2(), ClassEntry.class));
            case ITEM_UNINITIALIZED -> new UninitializedVerificationTypeInfoImpl(ctx.getLabel(u2()));
            default -> throw new IllegalArgumentException("Invalid verification type tag: " + tag);
        };
    }

    public static record ObjectVerificationTypeInfoImpl(
            ClassEntry className) implements ObjectVerificationTypeInfo {
        public ObjectVerificationTypeInfoImpl {
            requireNonNull(className);
        }

        @Override
        public int tag() { return ITEM_OBJECT; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ObjectVerificationTypeInfoImpl that) {
                return Objects.equals(className, that.className);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(className);
        }

        @Override
        public String toString() {
            return className.asInternalName();
        }
    }

    public static record UninitializedVerificationTypeInfoImpl(Label newTarget) implements UninitializedVerificationTypeInfo {
        public UninitializedVerificationTypeInfoImpl {
            requireNonNull(newTarget);
        }

        @Override
        public int tag() { return ITEM_UNINITIALIZED; }

        @Override
        public String toString() {
            return "UNINIT(" + newTarget +")";
        }
    }

    private int u2() {
        int v = classReader.readU2(p);
        p += 2;
        return v;
    }

    public static record StackMapFrameImpl(int frameType,
                                           Label target,
                                           List<VerificationTypeInfo> locals,
                                           List<VerificationTypeInfo> stack)
            implements StackMapFrameInfo {
        public StackMapFrameImpl {
            requireNonNull(target);
            locals = List.copyOf(locals);
            stack = List.copyOf(stack);
        }
    }
}
