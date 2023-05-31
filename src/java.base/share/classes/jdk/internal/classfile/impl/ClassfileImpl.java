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

import java.util.List;
import java.util.function.Function;

import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.Classfile.*;
import jdk.internal.classfile.constantpool.Utf8Entry;

import static jdk.internal.classfile.ClassHierarchyResolver.DEFAULT_CLASS_HIERARCHY_RESOLVER;

public record ClassfileImpl(StackMapsOption stackMapsOption,
                            DebugElementsOption debugElementsOption,
                            LineNumbersOption lineNumbersOption,
                            UnknownAttributesOption unknownAttributesOption,
                            ConstantPoolSharingOption constantPoolSharingOption,
                            ShortJumpsOption shortJumpsOption,
                            DeadCodeOption deadCodeOption,
                            DeadLabelsOption deadLabelsOption,
                            ClassHierarchyResolverOption classHierarchyResolverOption,
                            AttributeMapperOption attributeMapperOption) implements Classfile {

    public ClassfileImpl() {
        this(StackMapsOption.GENERATE_STACK_MAPS_BY_CLASS_VERSION,
             DebugElementsOption.PROCESS_DEBUG_ELEMENTS,
             LineNumbersOption.PROCESS_LINE_NUMBERS,
             UnknownAttributesOption.PROCESS_UNKNOWN_ATTRIBUTES,
             ConstantPoolSharingOption.SHARE_CONSTANT_POOL,
             ShortJumpsOption.FIX_SHORT_JUMPS,
             DeadCodeOption.PATCH_DEAD_CODE,
             DeadLabelsOption.FAIL_ON_DEAD_LABELS,
             new ClassHierarchyResolverOption(DEFAULT_CLASS_HIERARCHY_RESOLVER),
             new AttributeMapperOption(new Function<>() {
                 @Override
                 public AttributeMapper<?> apply(Utf8Entry k) {
                     return null;
                 }
             }));
    }

    @SuppressWarnings("unchecked")
    @Override
    public ClassfileImpl withOptions(Option... options) {
        var smo = stackMapsOption;
        var deo = debugElementsOption;
        var lno = lineNumbersOption;
        var uao = unknownAttributesOption;
        var cpso = constantPoolSharingOption;
        var sjo = shortJumpsOption;
        var dco = deadCodeOption;
        var dlo = deadLabelsOption;
        var chro = classHierarchyResolverOption;
        var amo = attributeMapperOption;
        for (var o : options) {
            switch (o) {
                case StackMapsOption oo -> smo = oo;
                case DebugElementsOption oo -> deo = oo;
                case LineNumbersOption oo -> lno = oo;
                case UnknownAttributesOption oo -> uao = oo;
                case ConstantPoolSharingOption oo -> cpso = oo;
                case ShortJumpsOption oo -> sjo = oo;
                case DeadCodeOption oo -> dco = oo;
                case DeadLabelsOption oo -> dlo = oo;
                case ClassHierarchyResolverOption oo -> chro = oo;
                case AttributeMapperOption oo -> amo = oo;
            }
        }
        return new ClassfileImpl(smo, deo, lno, uao, cpso, sjo, dco, dlo, chro, amo);
    }
}
