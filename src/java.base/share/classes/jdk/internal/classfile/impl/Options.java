/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.function.Function;

import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.Classfile.*;
import jdk.internal.classfile.constantpool.Utf8Entry;

import static jdk.internal.classfile.ClassHierarchyResolver.DEFAULT_CLASS_HIERARCHY_RESOLVER;

public final class Options implements Classfile.Context {

    public StackMapsOption generateStackmaps = StackMapsOption.GENERATE_BY_CLASS_VERSION;
    public DebugElementsOption processDebug = DebugElementsOption.PROCESS_DEBUG_ELEMENTS;
    public LineNumbersOption processLineNumbers = LineNumbersOption.PROCESS_LINE_NUMBERS;
    public UnknownAttributesOption processUnknownAttributes = UnknownAttributesOption.PROCESS_UNKNOWN_ATTRIBUTES;
    public ConstantPoolSharingOption cpSharing = ConstantPoolSharingOption.SHARE_CONSTANT_POOL;
    public ShortJumpsOption fixJumps = ShortJumpsOption.FIX_SHORT_JUMPS;
    public DeadCodeOption patchCode = DeadCodeOption.PATCH_DEAD_CODE;
    public DeadLabelsOption filterDeadLabels = DeadLabelsOption.FAIL_ON_DEAD_LABELS;
    public ClassHierarchyResolverOption classHierarchyResolver = new ClassHierarchyResolverOption(DEFAULT_CLASS_HIERARCHY_RESOLVER);
    public AttributeMapperOption attributeMapper = new AttributeMapperOption(new Function<>() {
        @Override
        public AttributeMapper<?> apply(Utf8Entry k) {
            return null;
        }
    });

    @SuppressWarnings("unchecked")
    public Options(Collection<Classfile.Option> options) {
        for (var o : options) {
            switch (o) {
                case StackMapsOption oo -> generateStackmaps = oo;
                case DebugElementsOption oo -> processDebug = oo;
                case LineNumbersOption oo -> processLineNumbers = oo;
                case UnknownAttributesOption oo -> processUnknownAttributes = oo;
                case ConstantPoolSharingOption oo -> cpSharing = oo;
                case ShortJumpsOption oo -> fixJumps = oo;
                case DeadCodeOption oo -> patchCode = oo;
                case DeadLabelsOption oo -> filterDeadLabels = oo;
                case ClassHierarchyResolverOption oo -> classHierarchyResolver = oo;
                case AttributeMapperOption oo -> attributeMapper = oo;
            }
        }
    }
}
