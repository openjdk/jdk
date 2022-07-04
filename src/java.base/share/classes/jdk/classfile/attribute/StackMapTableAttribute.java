/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.classfile.attribute;

import java.util.List;

import jdk.classfile.Attribute;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.impl.BoundAttribute;
import jdk.classfile.impl.StackMapDecoder;
import static jdk.classfile.Classfile.*;

/**
 * Models the {@code StackMapTable} attribute (JVMS 4.7.4), which can appear
 * on a {@code Code} attribute.
 */
public sealed interface StackMapTableAttribute
        extends Attribute<StackMapTableAttribute>
        permits BoundAttribute.BoundStackMapTableAttribute {

    /**
     * {@return the stack map frames}
     */
    List<StackMapFrame> entries();

    /**
     * {@return the initial frame}
     */
    StackMapFrame.Full initFrame();

    /**
     * The possible types for a stack slot.
     */
    enum VerificationType {
        ITEM_TOP(VT_TOP),
        ITEM_INTEGER(VT_INTEGER),
        ITEM_FLOAT(VT_FLOAT),
        ITEM_DOUBLE(VT_DOUBLE, 2),
        ITEM_LONG(VT_LONG, 2),
        ITEM_NULL(VT_NULL),
        ITEM_UNINITIALIZED_THIS(VT_UNINITIALIZED_THIS),
        ITEM_OBJECT(VT_OBJECT),
        ITEM_UNINITIALIZED(VT_UNINITIALIZED);

        private final int tag;
        private final int width;

        VerificationType(int tag) {
            this(tag, 1);
        }

        VerificationType(int tag, int width) {
            this.tag = tag;
            this.width = width;
        }

        public int tag() {
            return tag;
        }
    }

    /**
     * Kinds of stack values.
     */
    enum FrameKind {
        SAME(0, 63),
        SAME_LOCALS_1_STACK_ITEM(64, 127),
        RESERVED_FOR_FUTURE_USE(128, 246),
        SAME_LOCALS_1_STACK_ITEM_EXTENDED(247, 247),
        CHOP(248, 250),
        SAME_FRAME_EXTENDED(251, 251),
        APPEND(252, 254),
        FULL_FRAME(255, 255);

        int start;
        int end;

        public int start() { return start; }
        public int end() { return end; }

        FrameKind(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * The type of a stack value.
     */
    sealed interface VerificationTypeInfo {
        VerificationType type();
    }

    /**
     * A simple stack value.
     */
    sealed interface SimpleVerificationTypeInfo extends VerificationTypeInfo
            permits StackMapDecoder.SimpleVerificationTypeInfoImpl {
    }

    /**
     * A stack value for an object type.
     */
    sealed interface ObjectVerificationTypeInfo extends VerificationTypeInfo
            permits StackMapDecoder.ObjectVerificationTypeInfoImpl {
        /**
         * {@return the class of the value}
         */
        ClassEntry className();
    }

    /**
     * An uninitialized stack value.
     */
    sealed interface UninitializedVerificationTypeInfo extends VerificationTypeInfo
            permits StackMapDecoder.UninitializedVerificationTypeInfoImpl {
        int offset();
    }

    /**
     * A stack map frame.
     */
    sealed interface StackMapFrame
            permits StackMapFrame.Same, StackMapFrame.Same1, StackMapFrame.Append, StackMapFrame.Chop, StackMapFrame.Full {

        int frameType();
        FrameKind frameKind();
        int offsetDelta();
        int absoluteOffset();
        List<VerificationTypeInfo> effectiveLocals();
        List<VerificationTypeInfo> effectiveStack();

        sealed interface Same extends StackMapFrame permits StackMapDecoder.StackMapFrameSameImpl {

            boolean extended();
        }
        sealed interface Same1 extends StackMapFrame permits StackMapDecoder.StackMapFrameSame1Impl {

            boolean extended();

            VerificationTypeInfo declaredStack();
        }

        sealed interface Append extends StackMapFrame permits StackMapDecoder.StackMapFrameAppendImpl {

            List<VerificationTypeInfo> declaredLocals();
        }

        sealed interface Chop extends StackMapFrame permits StackMapDecoder.StackMapFrameChopImpl {

            List<VerificationTypeInfo> choppedLocals();
        }

        sealed interface Full extends StackMapFrame permits StackMapDecoder.StackMapFrameFullImpl {

            default List<VerificationTypeInfo> declaredStack() {
                return effectiveStack();
            }
            default List<VerificationTypeInfo> declaredLocals() {
                return effectiveLocals();
            }
        }
    }
}
