/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.classfile.attribute;

import java.lang.classfile.Label;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.util.List;

import jdk.internal.classfile.impl.StackMapDecoder;
import jdk.internal.classfile.impl.TemporaryConstantPool;

/**
 * Models stack map frame of {@code StackMapTable} attribute (JVMS {@jvms 4.7.4}).
 *
 * @since 24
 */
public sealed interface StackMapFrameInfo
            permits StackMapDecoder.StackMapFrameImpl {

    /**
     * {@return the frame compact form type}
     */
    int frameType();

    /**
     * {@return the frame target label}
     */
    Label target();

    /**
     * {@return the expanded local variable types}
     */
    List<VerificationTypeInfo> locals();

    /**
     * {@return the expanded stack types}
     */
    List<VerificationTypeInfo> stack();

    /**
     * {@return a new stack map frame}
     * @param target the location of the frame
     * @param locals the complete list of frame locals
     * @param stack the complete frame stack
     */
    public static StackMapFrameInfo of(Label target,
            List<VerificationTypeInfo> locals,
            List<VerificationTypeInfo> stack) {

        return new StackMapDecoder.StackMapFrameImpl(255, target, locals, stack);
    }

    /**
     * The type of a stack value.
     *
     * @since 24
     */
    sealed interface VerificationTypeInfo {

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#TOP TOP}. */
        int ITEM_TOP = 0;

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#INTEGER INTEGER}. */
        int ITEM_INTEGER = 1;

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#FLOAT FLOAT}. */
        int ITEM_FLOAT = 2;

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#DOUBLE DOUBLE}. */
        int ITEM_DOUBLE = 3;

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#LONG LONG}. */
        int ITEM_LONG = 4;

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#NULL NULL}. */
        int ITEM_NULL = 5;

        /** The {@link #tag() tag} for verification type info {@link SimpleVerificationTypeInfo#UNINITIALIZED_THIS UNINITIALIZED_THIS}. */
        int ITEM_UNINITIALIZED_THIS = 6;

        /** The {@link #tag() tag} for verification type info {@link ObjectVerificationTypeInfo OBJECT}. */
        int ITEM_OBJECT = 7;

        /** The {@link #tag() tag} for verification type info {@link UninitializedVerificationTypeInfo UNINITIALIZED}. */
        int ITEM_UNINITIALIZED = 8;

        /**
         * {@return the tag of the type info}
         *
         * @apiNote
         * {@code ITEM_}-prefixed constants in this class, such as {@link #ITEM_TOP}, describe the
         * possible return values of this method.
         */
        int tag();
    }

    /**
     * A simple stack value.
     *
     * @since 24
     */
    public enum SimpleVerificationTypeInfo implements VerificationTypeInfo {

        /** verification type top */
        TOP(ITEM_TOP),

        /** verification type int */
        INTEGER(ITEM_INTEGER),

        /** verification type float */
        FLOAT(ITEM_FLOAT),

        /** verification type double */
        DOUBLE(ITEM_DOUBLE),

        /** verification type long */
        LONG(ITEM_LONG),

        /** verification type null */
        NULL(ITEM_NULL),

        /** verification type uninitializedThis */
        UNINITIALIZED_THIS(ITEM_UNINITIALIZED_THIS);


        private final int tag;

        SimpleVerificationTypeInfo(int tag) {
            this.tag = tag;
        }

        @Override
        public int tag() {
            return tag;
        }
    }

    /**
     * A stack value for an object type. Its {@link #tag() tag} is {@value #ITEM_OBJECT}.
     *
     * @since 24
     */
    sealed interface ObjectVerificationTypeInfo extends VerificationTypeInfo
            permits StackMapDecoder.ObjectVerificationTypeInfoImpl {

        /**
         * {@return a new object verification type info}
         * @param className the class of the object
         */
        public static ObjectVerificationTypeInfo of(ClassEntry className) {
            return new StackMapDecoder.ObjectVerificationTypeInfoImpl(className);
        }

        /**
         * {@return a new object verification type info}
         * @param classDesc the class of the object
         * @throws IllegalArgumentException if {@code classDesc} represents a primitive type
         */
        public static ObjectVerificationTypeInfo of(ClassDesc classDesc) {
            return of(TemporaryConstantPool.INSTANCE.classEntry(classDesc));
        }

        /**
         * {@return the class of the object}
         */
        ClassEntry className();

        /**
         * {@return the class of the object}
         */
        default ClassDesc classSymbol() {
            return className().asSymbol();
        }
    }

    /**
     * An uninitialized stack value. Its {@link #tag() tag} is {@value #ITEM_UNINITIALIZED}.
     *
     * @since 24
     */
    sealed interface UninitializedVerificationTypeInfo extends VerificationTypeInfo
            permits StackMapDecoder.UninitializedVerificationTypeInfoImpl {

        /**
         * {@return the {@code new} instruction position that creates this unitialized object}
         */
        Label newTarget();

        /**
         * {@return an unitialized verification type info}
         * @param newTarget the {@code new} instruction position that creates this unitialized object
         */
        public static UninitializedVerificationTypeInfo of(Label newTarget) {
            return new StackMapDecoder.UninitializedVerificationTypeInfoImpl(newTarget);
        }
    }
}
