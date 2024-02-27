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

import java.util.List;
import java.util.Objects;
import java.lang.classfile.Label;
import java.lang.classfile.TypeAnnotation.*;
import static java.lang.classfile.ClassFile.*;
import static java.util.Objects.requireNonNull;

public final class TargetInfoImpl {

    private TargetInfoImpl() {
    }

    private static TargetType checkValid(TargetType targetType, int rangeFrom, int rangeTo) {
        Objects.requireNonNull(targetType);
        if (targetType.targetTypeValue() < rangeFrom || targetType.targetTypeValue() > rangeTo)
            throw new IllegalArgumentException("Wrong target type specified " + targetType);
        return targetType;
    }

    public record TypeParameterTargetImpl(TargetType targetType, int typeParameterIndex)
            implements TypeParameterTarget {

        public TypeParameterTargetImpl(TargetType targetType, int typeParameterIndex) {
            this.targetType = checkValid(targetType, TAT_CLASS_TYPE_PARAMETER, TAT_METHOD_TYPE_PARAMETER);
            this.typeParameterIndex = typeParameterIndex;
        }
    }

    public record SupertypeTargetImpl(int supertypeIndex) implements SupertypeTarget {
        @Override
        public TargetType targetType() {
            return TargetType.CLASS_EXTENDS;
        }
    }

    public record TypeParameterBoundTargetImpl(TargetType targetType, int typeParameterIndex, int boundIndex)
            implements TypeParameterBoundTarget {

        public TypeParameterBoundTargetImpl(TargetType targetType, int typeParameterIndex, int boundIndex) {
            this.targetType = checkValid(targetType, TAT_CLASS_TYPE_PARAMETER_BOUND, TAT_METHOD_TYPE_PARAMETER_BOUND);
            this.typeParameterIndex = typeParameterIndex;
            this.boundIndex = boundIndex;
        }
    }

    public record EmptyTargetImpl(TargetType targetType) implements EmptyTarget {

        public EmptyTargetImpl(TargetType targetType) {
            this.targetType = checkValid(targetType, TAT_FIELD, TAT_METHOD_RECEIVER);
        }
    }

    public record FormalParameterTargetImpl(int formalParameterIndex) implements FormalParameterTarget {
        @Override
        public TargetType targetType() {
            return TargetType.METHOD_FORMAL_PARAMETER;
        }
    }

    public record ThrowsTargetImpl(int throwsTargetIndex) implements ThrowsTarget {
        @Override
        public TargetType targetType() {
            return TargetType.THROWS;
        }
    }

    public record LocalVarTargetImpl(TargetType targetType, List<LocalVarTargetInfo> table)
            implements LocalVarTarget {

        public LocalVarTargetImpl(TargetType targetType, List<LocalVarTargetInfo> table) {
            this.targetType = checkValid(targetType, TAT_LOCAL_VARIABLE, TAT_RESOURCE_VARIABLE);
            this.table = List.copyOf(table);
        }
        @Override
        public int size() {
            return 2 + 6 * table.size();
        }
    }

    public record LocalVarTargetInfoImpl(Label startLabel, Label endLabel, int index)
            implements LocalVarTargetInfo {

        public LocalVarTargetInfoImpl {
            requireNonNull(startLabel);
            requireNonNull(endLabel);
        }
    }

    public record CatchTargetImpl(int exceptionTableIndex) implements CatchTarget {
        @Override
        public TargetType targetType() {
            return TargetType.EXCEPTION_PARAMETER;
        }
    }

    public record OffsetTargetImpl(TargetType targetType, Label target) implements OffsetTarget {

        public OffsetTargetImpl(TargetType targetType, Label target) {
            this.targetType = checkValid(targetType, TAT_INSTANCEOF, TAT_METHOD_REFERENCE);
            this.target = requireNonNull(target);
        }
    }

    public record TypeArgumentTargetImpl(TargetType targetType, Label target, int typeArgumentIndex)
            implements TypeArgumentTarget {

        public TypeArgumentTargetImpl(TargetType targetType, Label target, int typeArgumentIndex) {
            this.targetType = checkValid(targetType, TAT_CAST, TAT_METHOD_REFERENCE_TYPE_ARGUMENT);
            this.target = requireNonNull(target);
            this.typeArgumentIndex = typeArgumentIndex;
        }
    }
}
