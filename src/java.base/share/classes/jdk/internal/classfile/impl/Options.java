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

import java.util.Collection;
import java.util.function.Function;

import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.constantpool.Utf8Entry;

import static jdk.internal.classfile.ClassHierarchyResolver.DEFAULT_CLASS_HIERARCHY_RESOLVER;

/**
 * Options
 */
public class Options {

    public record OptionValue<V>(Classfile.Option.Key key, V value) implements Classfile.Option<V> { }

    public Boolean generateStackmaps = true;
    public Boolean processDebug = true;
    public Boolean processLineNumbers = true;
    public Boolean processUnknownAttributes = true;
    public Boolean cpSharing = true;
    public Boolean fixJumps = true;
    public Boolean patchCode = true;
    public Boolean filterDeadLabels = false;
    public ClassHierarchyResolver classHierarchyResolver = DEFAULT_CLASS_HIERARCHY_RESOLVER;
    public Function<Utf8Entry, AttributeMapper<?>> attributeMapper = new Function<>() {
        @Override
        public AttributeMapper<?> apply(Utf8Entry k) {
            return null;
        }
    };

    @SuppressWarnings("unchecked")
    public Options(Collection<Classfile.Option<?>> options) {
        for (Classfile.Option<?> v : options)
            switch (((Options.OptionValue<?>) v).key()) {
                case GENERATE_STACK_MAPS -> generateStackmaps = (Boolean) v.value();
                case PROCESS_DEBUG -> processDebug = (Boolean) v.value();
                case PROCESS_LINE_NUMBERS -> processLineNumbers = (Boolean) v.value();
                case PROCESS_UNKNOWN_ATTRIBUTES -> processUnknownAttributes = (Boolean) v.value();
                case CP_SHARING -> cpSharing = (Boolean) v.value();
                case FIX_SHORT_JUMPS -> fixJumps = (Boolean) v.value();
                case PATCH_DEAD_CODE -> patchCode = (Boolean) v.value();
                case HIERARCHY_RESOLVER -> classHierarchyResolver = (ClassHierarchyResolver) v.value();
                case ATTRIBUTE_MAPPER -> attributeMapper = (Function<Utf8Entry, AttributeMapper<?>>) v.value();
                case FILTER_DEAD_LABELS -> filterDeadLabels = (Boolean) v.value();
            }
    }

    @SuppressWarnings("unchecked")
    public <T> T value(Classfile.Option.Key key) {
        return switch (key) {
            case PROCESS_DEBUG -> (T) processDebug;
            case PROCESS_LINE_NUMBERS -> (T) processLineNumbers;
            case PROCESS_UNKNOWN_ATTRIBUTES -> (T) processUnknownAttributes;
            case CP_SHARING -> (T) cpSharing;
            case FIX_SHORT_JUMPS -> (T) fixJumps;
            case PATCH_DEAD_CODE -> (T) patchCode;
            case ATTRIBUTE_MAPPER -> (T) attributeMapper;
            case GENERATE_STACK_MAPS -> (T) generateStackmaps;
            case HIERARCHY_RESOLVER -> (T) classHierarchyResolver;
            case FILTER_DEAD_LABELS -> (T) filterDeadLabels;
        };
    }
}
