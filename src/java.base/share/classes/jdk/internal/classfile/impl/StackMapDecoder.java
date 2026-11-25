/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassReader;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.ObjectVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.SimpleVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.UninitializedVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.VerificationTypeInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.SharedSecrets;

import static java.lang.classfile.ClassFile.*;
import static java.util.Objects.requireNonNull;
import static jdk.internal.classfile.impl.StackMapGenerator.*;

public class StackMapDecoder {

    private static final StackMapFrameInfo[] NO_STACK_FRAME_INFOS = {};

    private final ClassReader classReader;
    private final int pos;
    private final LabelContext ctx;
    private final List<VerificationTypeInfo> initFrameLocals;
    private final List<NameAndTypeEntry> initFrameUnsets;
    private int p;

    StackMapDecoder(ClassReader classReader, int pos, LabelContext ctx, List<VerificationTypeInfo> initFrameLocals,
                    List<NameAndTypeEntry> initFrameUnsets) {
        this.classReader = classReader;
        this.pos = pos;
        this.ctx = ctx;
        this.initFrameLocals = initFrameLocals;
        this.initFrameUnsets = initFrameUnsets;
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

    static List<NameAndTypeEntry> initFrameUnsets(MethodModel method) {
        return initFrameUnsets(method.parent().orElseThrow(),
                method.methodName());
    }

    private static List<NameAndTypeEntry> initFrameUnsets(ClassModel clazz, Utf8Entry methodName) {
        if (!methodName.equalsString(ConstantDescs.INIT_NAME))
            return List.of();
        if (clazz.minorVersion() != PREVIEW_MINOR_VERSION || clazz.majorVersion() < Util.VALUE_OBJECTS_MAJOR)
            return List.of();
        var l = new ArrayList<NameAndTypeEntry>(clazz.fields().size());
        for (var field : clazz.fields()) {
            if ((field.flags().flagsMask() & (ACC_STATIC | ACC_STRICT_INIT)) == ACC_STRICT_INIT) { // instance strict
                l.add(TemporaryConstantPool.INSTANCE.nameAndTypeEntry(field.fieldName(), field.fieldType()));
            }
        }
        return List.copyOf(l);
    }

    private static List<NameAndTypeEntry> initFrameUnsets(MethodInfo mi, WritableField.UnsetField[] unsets) {
        if (!mi.methodName().equalsString(ConstantDescs.INIT_NAME))
            return List.of();
        var l = new ArrayList<NameAndTypeEntry>(unsets.length);
        for (var field : unsets) {
            l.add(TemporaryConstantPool.INSTANCE.nameAndTypeEntry(field.name(), field.type()));
        }
        return List.copyOf(l);
    }

    public static void writeFrames(BufWriter b, List<StackMapFrameInfo> entries) {
        var buf = (BufWriterImpl)b;
        var dcb = (DirectCodeBuilder)buf.labelContext();
        var mi = dcb.methodInfo();
        var prevLocals = StackMapDecoder.initFrameLocals(buf.thisClass(),
                mi.methodName().stringValue(),
                mi.methodTypeSymbol(),
                (mi.methodFlags() & ACC_STATIC) != 0);
        var prevUnsets = initFrameUnsets(mi, buf.getStrictInstanceFields());
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
            writeFrame(buf, offset - prevOffset - 1, prevLocals, prevUnsets, fr);
            prevOffset = offset;
            prevLocals = fr.locals();
            prevUnsets = fr.unsetFields();
        }
    }

    // In sync with StackMapGenerator::needsLarvalFrame
    private static boolean needsLarvalFrameForTransition(List<NameAndTypeEntry> prevUnsets, StackMapFrameInfo fr) {
        if (prevUnsets.equals(fr.unsetFields()))
            return false;
        if (!fr.locals().contains(SimpleVerificationTypeInfo.UNINITIALIZED_THIS)) {
            assert fr.unsetFields().isEmpty() : fr; // should be checked in StackMapFrameInfo constructor
            return false;
        }
        return true;
    }

    private static void writeFrame(BufWriterImpl out, int offsetDelta, List<VerificationTypeInfo> prevLocals, List<NameAndTypeEntry> prevUnsets, StackMapFrameInfo fr) {
        if (offsetDelta < 0) throw new IllegalArgumentException("Invalid stack map frames order");
        // enclosing frames
        if (needsLarvalFrameForTransition(prevUnsets, fr)) {
            out.writeU1(EARLY_LARVAL);
            Util.writeListIndices(out, fr.unsetFields());
        }
        // base frame
        if (fr.stack().isEmpty()) {
            int commonLocalsSize = Math.min(prevLocals.size(), fr.locals().size());
            int diffLocalsSize = fr.locals().size() - prevLocals.size();
            if (-3 <= diffLocalsSize && diffLocalsSize <= 3 && equals(fr.locals(), prevLocals, commonLocalsSize)) {
                if (diffLocalsSize == 0 && offsetDelta <= SAME_FRAME_END) { //same frame
                    out.writeU1(offsetDelta);
                } else {   //chop, same extended or append frame
                    out.writeU1U2(SAME_FRAME_EXTENDED + diffLocalsSize, offsetDelta);
                    for (int i=commonLocalsSize; i<fr.locals().size(); i++) writeTypeInfo(out, fr.locals().get(i));
                }
                return;
            }
        } else if (fr.stack().size() == 1 && fr.locals().equals(prevLocals)) {
            if (offsetDelta <= SAME_LOCALS_1_STACK_ITEM_FRAME_END  - SAME_LOCALS_1_STACK_ITEM_FRAME_START) {  //same locals 1 stack item frame
                out.writeU1(SAME_LOCALS_1_STACK_ITEM_FRAME_START + offsetDelta);
            } else {  //same locals 1 stack item extended frame
                out.writeU1U2(SAME_LOCALS_1_STACK_ITEM_EXTENDED, offsetDelta);
            }
            writeTypeInfo(out, fr.stack().get(0));
            return;
        }
        //full frame
        out.writeU1U2U2(FULL_FRAME, offsetDelta, fr.locals().size());
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

    // Copied from BoundAttribute
    <E extends PoolEntry> List<E> readEntryList(int p, Class<E> type) {
        int cnt = classReader.readU2(p);
        p += 2;
        var entries = new Object[cnt];
        int end = p + (cnt * 2);
        for (int i = 0; p < end; i++, p += 2) {
            entries[i] = classReader.readEntry(p, type);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(entries);
    }

    List<StackMapFrameInfo> entries() {
        p = pos;
        List<VerificationTypeInfo> locals = initFrameLocals, stack = List.of();
        List<NameAndTypeEntry> unsetFields = initFrameUnsets;
        int bci = -1;
        var entries = new StackMapFrameInfo[u2()];
        for (int ei = 0; ei < entries.length; ei++) {
            int actualFrameType = classReader.readU1(p++);
            int frameType = actualFrameType; // effective frame type for parsing
            // enclosing frames handling
            if (frameType == EARLY_LARVAL) {
                unsetFields = readEntryList(p, NameAndTypeEntry.class);
                p += 2 + unsetFields.size() * 2;
                frameType = classReader.readU1(p++);
            }
            // base frame handling
            if (frameType <= SAME_FRAME_END) {
                bci += frameType + 1;
                stack = List.of();
            } else if (frameType <= SAME_LOCALS_1_STACK_ITEM_FRAME_END) {
                bci += frameType - SAME_LOCALS_1_STACK_ITEM_FRAME_START + 1;
                stack = List.of(readVerificationTypeInfo());
            } else {
                if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED)
                    throw new IllegalArgumentException("Invalid base frame type: " + frameType);
                bci += u2() + 1;
                if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                    stack = List.of(readVerificationTypeInfo());
                } else if (frameType < SAME_FRAME_EXTENDED) {
                    locals = locals.subList(0, locals.size() + frameType - SAME_FRAME_EXTENDED);
                    stack = List.of();
                } else if (frameType == SAME_FRAME_EXTENDED) {
                    stack = List.of();
                } else if (frameType <= APPEND_FRAME_END) {
                    int actSize = locals.size();
                    var newLocals = locals.toArray(new VerificationTypeInfo[actSize + frameType - SAME_FRAME_EXTENDED]);
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
            if (actualFrameType != EARLY_LARVAL && !unsetFields.isEmpty() && !locals.contains(SimpleVerificationTypeInfo.UNINITIALIZED_THIS)) {
                // clear unsets post larval
                unsetFields = List.of();
            }
            entries[ei] = new StackMapFrameImpl(actualFrameType,
                    ctx.getLabel(bci),
                    locals,
                    stack,
                    unsetFields);
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
                                           List<VerificationTypeInfo> stack,
                                           List<NameAndTypeEntry> unsetFields)
            implements StackMapFrameInfo {
        public StackMapFrameImpl {
            requireNonNull(target);
            locals = Util.sanitizeU2List(locals);
            stack = Util.sanitizeU2List(stack);
            unsetFields = Util.sanitizeU2List(unsetFields);

            uninitializedThisCheck:
            if (!unsetFields.isEmpty()) {
                for (var local : locals) {
                    if (local == SimpleVerificationTypeInfo.UNINITIALIZED_THIS) {
                        break uninitializedThisCheck;
                    }
                }
                throw new IllegalArgumentException("unset fields requires uninitializedThis in locals");
            }
        }

        public StackMapFrameImpl(int frameType,
                                 Label target,
                                 List<VerificationTypeInfo> locals,
                                 List<VerificationTypeInfo> stack) {
            this(frameType, target, locals, stack, List.of());
        }
    }
}
