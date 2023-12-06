/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.TreeMap;
import java.lang.classfile.BufWriter;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.*;
import java.lang.classfile.ClassReader;

import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;

public class StackMapDecoder {

    private static final int
                    SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247,
                    SAME_EXTENDED = 251;

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
                vtis[i++] = SimpleVerificationTypeInfo.ITEM_UNINITIALIZED_THIS;
            } else {
                vtis[i++] = new StackMapDecoder.ObjectVerificationTypeInfoImpl(thisClass);
            }
        } else {
            vtis = new VerificationTypeInfo[methodType.parameterCount()];
        }
        for(var arg : methodType.parameterList()) {
            vtis[i++] = switch (arg.descriptorString().charAt(0)) {
                case 'I', 'S', 'C' ,'B', 'Z' -> SimpleVerificationTypeInfo.ITEM_INTEGER;
                case 'J' -> SimpleVerificationTypeInfo.ITEM_LONG;
                case 'F' -> SimpleVerificationTypeInfo.ITEM_FLOAT;
                case 'D' -> SimpleVerificationTypeInfo.ITEM_DOUBLE;
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
        var map = new TreeMap<Integer, StackMapFrameInfo>();
        //sort by resolved label offsets first to allow unordered entries
        for (var fr : entries) {
            map.put(dcb.labelToBci(fr.target()), fr);
        }
        b.writeU2(map.size());
        for (var me : map.entrySet()) {
            int offset = me.getKey();
            var fr = me.getValue();
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
                    out.writeU1(251 + diffLocalsSize);
                    out.writeU2(offsetDelta);
                    for (int i=commonLocalsSize; i<fr.locals().size(); i++) writeTypeInfo(out, fr.locals().get(i));
                }
                return;
            }
        } else if (fr.stack().size() == 1 && fr.locals().equals(prevLocals)) {
            if (offsetDelta < 64) {  //same locals 1 stack item frame
                out.writeU1(64 + offsetDelta);
            } else {  //same locals 1 stack item extended frame
                out.writeU1(247);
                out.writeU2(offsetDelta);
            }
            writeTypeInfo(out, fr.stack().get(0));
            return;
        }
        //full frame
        out.writeU1(255);
        out.writeU2(offsetDelta);
        out.writeU2(fr.locals().size());
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
        bw.writeU1(vti.tag());
        switch (vti) {
            case SimpleVerificationTypeInfo svti ->
                {}
            case ObjectVerificationTypeInfo ovti ->
                bw.writeIndex(ovti.className());
            case UninitializedVerificationTypeInfo uvti ->
                bw.writeU2(bw.labelContext().labelToBci(uvti.newTarget()));
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
            case VT_TOP -> SimpleVerificationTypeInfo.ITEM_TOP;
            case VT_INTEGER -> SimpleVerificationTypeInfo.ITEM_INTEGER;
            case VT_FLOAT -> SimpleVerificationTypeInfo.ITEM_FLOAT;
            case VT_DOUBLE -> SimpleVerificationTypeInfo.ITEM_DOUBLE;
            case VT_LONG -> SimpleVerificationTypeInfo.ITEM_LONG;
            case VT_NULL -> SimpleVerificationTypeInfo.ITEM_NULL;
            case VT_UNINITIALIZED_THIS -> SimpleVerificationTypeInfo.ITEM_UNINITIALIZED_THIS;
            case VT_OBJECT -> new ObjectVerificationTypeInfoImpl((ClassEntry)classReader.entryByIndex(u2()));
            case VT_UNINITIALIZED -> new UninitializedVerificationTypeInfoImpl(ctx.getLabel(u2()));
            default -> throw new IllegalArgumentException("Invalid verification type tag: " + tag);
        };
    }

    public static record ObjectVerificationTypeInfoImpl(
            ClassEntry className) implements ObjectVerificationTypeInfo {

        @Override
        public int tag() { return VT_OBJECT; }

        @Override
        public String toString() {
            return className.asInternalName();
        }
    }

    public static record UninitializedVerificationTypeInfoImpl(Label newTarget) implements UninitializedVerificationTypeInfo {

        @Override
        public int tag() { return VT_UNINITIALIZED; }

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
    }
}
