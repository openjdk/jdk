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

package jdk.classfile.impl;

import java.lang.constant.ConstantDescs;
import java.util.List;

import jdk.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import jdk.classfile.attribute.StackMapTableAttribute.*;
import jdk.classfile.ClassReader;

import static jdk.classfile.Classfile.*;
import jdk.classfile.MethodModel;
import static jdk.classfile.attribute.StackMapTableAttribute.VerificationType.*;

public class StackMapDecoder {
    static final VerificationTypeInfo soleTopVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_TOP);
    static final VerificationTypeInfo soleIntegerVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_INTEGER);
    static final VerificationTypeInfo soleFloatVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_FLOAT);
    static final VerificationTypeInfo soleDoubleVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_DOUBLE);
    static final VerificationTypeInfo soleLongVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_LONG);
    static final VerificationTypeInfo soleNullVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_NULL);
    static final VerificationTypeInfo soleUninitializedThisVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_UNINITIALIZED_THIS);

    private static final int
                    SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247,
                    SAME_EXTENDED = 251,
                    FULL = 255;

    private final ClassReader classReader;
    private final int pos;
    private final StackMapFrame.Full initFrame;
    private int p;

    StackMapDecoder(ClassReader classReader, int pos, StackMapFrame.Full initFrame) {
        this.classReader = classReader;
        this.pos = pos;
        this.initFrame = initFrame;
    }

    static StackMapFrame.Full initFrame(MethodModel method) {
        VerificationTypeInfo vtis[];
        var mdesc = method.methodTypeSymbol();
        int i = 0;
        if (!method.flags().has(AccessFlag.STATIC)) {
            vtis = new VerificationTypeInfo[mdesc.parameterCount() + 1];
            var thisClass = method.parent().orElseThrow().thisClass();
            if ("<init>".equals(method.methodName().stringValue()) && !ConstantDescs.CD_Object.equals(thisClass.asSymbol())) {
                vtis[i++] = StackMapDecoder.soleUninitializedThisVerificationTypeInfo;
            } else {
                vtis[i++] = new StackMapDecoder.ObjectVerificationTypeInfoImpl(thisClass);
            }
        } else {
            vtis = new VerificationTypeInfo[mdesc.parameterCount()];
        }
        for(var arg : mdesc.parameterList()) {
            vtis[i++] = switch (arg.descriptorString()) {
                case "I", "S", "C" ,"B", "Z" ->  StackMapDecoder.soleIntegerVerificationTypeInfo;
                case "J" -> StackMapDecoder.soleLongVerificationTypeInfo;
                case "F" -> StackMapDecoder.soleFloatVerificationTypeInfo;
                case "D" -> StackMapDecoder.soleDoubleVerificationTypeInfo;
                case "V" -> throw new IllegalArgumentException("Illegal method argument type: " + arg);
                default -> new StackMapDecoder.ObjectVerificationTypeInfoImpl(TemporaryConstantPool.INSTANCE.classEntry(arg));
            };
        }
        return new StackMapFrameFullImpl(FULL, FrameKind.FULL_FRAME, -1, -1, List.of(vtis), List.of());
    }

    List<StackMapFrame> entries() {
        p = pos;
        StackMapFrame frame = initFrame;
        var entries = new StackMapFrame[u2()];
        for (int ei = 0; ei < entries.length; ei++) {
            int frameType = classReader.readU1(p++);
            if (frameType < 64) {
                frame = new StackMapFrameSameImpl(frameType, FrameKind.SAME,
                        frameType, frame.absoluteOffset() + frameType + 1,
                        false,
                        frame.effectiveLocals(), List.of());
            } else if (frameType < 128) {
                var stack = readVerificationTypeInfo();
                frame = new StackMapFrameSame1Impl(frameType, FrameKind.SAME_LOCALS_1_STACK_ITEM,
                        frameType - 64, frame.absoluteOffset() + frameType - 63,
                        false,
                        stack,
                        frame.effectiveLocals(), List.of(stack));
            } else {
                if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED)
                    throw new IllegalArgumentException("Invalid stackmap frame type: " + frameType);
                int offsetDelta = u2();
                if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                    var stack = readVerificationTypeInfo();
                    frame = new StackMapFrameSame1Impl(frameType, FrameKind.SAME_LOCALS_1_STACK_ITEM_EXTENDED,
                            offsetDelta, frame.absoluteOffset() + offsetDelta + 1,
                            true,
                            stack,
                            frame.effectiveLocals(), List.of(stack));
                } else if (frameType < SAME_EXTENDED) {
                    frame = new StackMapFrameChopImpl(frameType, FrameKind.CHOP,
                            offsetDelta, frame.absoluteOffset() + offsetDelta + 1,
                            frame.effectiveLocals().subList(frame.effectiveLocals().size() + frameType - SAME_EXTENDED, frame.effectiveLocals().size()),
                            frame.effectiveLocals().subList(0, frame.effectiveLocals().size() + frameType - SAME_EXTENDED), List.of());
                } else if (frameType == SAME_EXTENDED) {
                    frame = new StackMapFrameSameImpl(frameType, FrameKind.SAME_FRAME_EXTENDED,
                            offsetDelta, frame.absoluteOffset() + offsetDelta + 1,
                            true,
                            frame.effectiveLocals(), List.of());
                } else if (frameType < SAME_EXTENDED + 4) {
                    int actSize = frame.effectiveLocals().size();
                    var locals = frame.effectiveLocals().toArray(new VerificationTypeInfo[actSize + frameType - SAME_EXTENDED]);
                    for (int i = actSize; i < locals.length; i++)
                        locals[i] = readVerificationTypeInfo();
                    var locList = List.of(locals);
                    frame = new StackMapFrameAppendImpl(frameType, FrameKind.APPEND,
                            offsetDelta, frame.absoluteOffset() + offsetDelta + 1,
                            locList.subList(actSize, locList.size()),
                            locList, List.of());
                } else {
                    var locals = new VerificationTypeInfo[u2()];
                    for (int i=0; i<locals.length; i++)
                        locals[i] = readVerificationTypeInfo();
                    var stack = new VerificationTypeInfo[u2()];
                    for (int i=0; i<stack.length; i++)
                        stack[i] = readVerificationTypeInfo();
                    var locList = List.of(locals);
                    var stackList = List.of(stack);
                    frame = new StackMapFrameFullImpl(frameType, FrameKind.FULL_FRAME,
                            offsetDelta, frame.absoluteOffset() + offsetDelta + 1,
                            locList, stackList);
                }
            }
            entries[ei] = frame;
        }
        return List.of(entries);
    }

    private VerificationTypeInfo readVerificationTypeInfo() {
        int tag = classReader.readU1(p++);
        return switch (tag) {
            case VT_TOP -> soleTopVerificationTypeInfo;
            case VT_INTEGER -> soleIntegerVerificationTypeInfo;
            case VT_FLOAT -> soleFloatVerificationTypeInfo;
            case VT_DOUBLE -> soleDoubleVerificationTypeInfo;
            case VT_LONG -> soleLongVerificationTypeInfo;
            case VT_NULL -> soleNullVerificationTypeInfo;
            case VT_UNINITIALIZED_THIS -> soleUninitializedThisVerificationTypeInfo;
            case VT_OBJECT -> new ObjectVerificationTypeInfoImpl((ClassEntry)classReader.entryByIndex(u2()));
            case VT_UNINITIALIZED -> new UninitializedVerificationTypeInfoImpl(u2());
            default -> throw new IllegalArgumentException("Invalid verification type tag: " + tag);
        };
    }

    public static record SimpleVerificationTypeInfoImpl(VerificationType type) implements SimpleVerificationTypeInfo {

        @Override
        public String toString() {
            return switch (type) {
                case ITEM_DOUBLE -> "D";
                case ITEM_FLOAT -> "F";
                case ITEM_INTEGER -> "I";
                case ITEM_LONG -> "J";
                case ITEM_NULL -> "null";
                case ITEM_TOP -> "?";
                case ITEM_UNINITIALIZED_THIS -> "THIS";
                default -> throw new AssertionError("should never happen");
            };
        }
    }

    public static record ObjectVerificationTypeInfoImpl(
            ClassEntry className) implements ObjectVerificationTypeInfo {

        @Override
        public VerificationType type() { return VerificationType.ITEM_OBJECT; }

        @Override
        public String toString() {
            return className.asInternalName();
        }
    }

    public static record UninitializedVerificationTypeInfoImpl(int offset) implements UninitializedVerificationTypeInfo {

        @Override
        public VerificationType type() { return VerificationType.ITEM_UNINITIALIZED; }

        @Override
        public String toString() {
            return "UNINIT(" + offset +")";
        }
    }

    private int u2() {
        int v = classReader.readU2(p);
        p += 2;
        return v;
    }

    public static record StackMapFrameSameImpl(int frameType,
        FrameKind frameKind,
        int offsetDelta,
        int absoluteOffset,
        boolean extended,
        List<VerificationTypeInfo> effectiveLocals,
        List<VerificationTypeInfo> effectiveStack)
            implements StackMapFrame.Same {
    }

    public static record StackMapFrameSame1Impl(int frameType,
                                               FrameKind frameKind,
                                               int offsetDelta,
                                               int absoluteOffset,
                                               boolean extended,
                                               VerificationTypeInfo declaredStack,
                                               List<VerificationTypeInfo> effectiveLocals,
                                               List<VerificationTypeInfo> effectiveStack)
            implements StackMapFrame.Same1 {
    }

    public static record StackMapFrameAppendImpl(int frameType,
                                                 FrameKind frameKind,
                                                 int offsetDelta,
                                                 int absoluteOffset,
                                                 List<VerificationTypeInfo> declaredLocals,
                                                 List<VerificationTypeInfo> effectiveLocals,
                                                 List<VerificationTypeInfo> effectiveStack)
            implements StackMapFrame.Append {
    }

    public static record StackMapFrameChopImpl(int frameType,
                                                 FrameKind frameKind,
                                                 int offsetDelta,
                                                 int absoluteOffset,
                                                 List<VerificationTypeInfo> choppedLocals,
                                                 List<VerificationTypeInfo> effectiveLocals,
                                                 List<VerificationTypeInfo> effectiveStack)
            implements StackMapFrame.Chop {
    }

    public static record StackMapFrameFullImpl(int frameType,
                                               FrameKind frameKind,
                                               int offsetDelta,
                                               int absoluteOffset,
                                               List<VerificationTypeInfo> effectiveLocals,
                                               List<VerificationTypeInfo> effectiveStack)
            implements StackMapFrame.Full {
    }
}
