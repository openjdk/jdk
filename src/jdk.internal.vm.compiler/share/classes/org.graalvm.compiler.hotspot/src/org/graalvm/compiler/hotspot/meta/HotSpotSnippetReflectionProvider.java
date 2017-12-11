/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.hotspot.meta;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotSnippetReflectionProvider implements SnippetReflectionProvider {

    private final HotSpotGraalRuntimeProvider runtime;
    private final HotSpotConstantReflectionProvider constantReflection;
    private final WordTypes wordTypes;

    public HotSpotSnippetReflectionProvider(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, WordTypes wordTypes) {
        this.runtime = runtime;
        this.constantReflection = constantReflection;
        this.wordTypes = wordTypes;
    }

    @Override
    public JavaConstant forObject(Object object) {
        return constantReflection.forObject(object);
    }

    @Override
    public Object asObject(ResolvedJavaType type, JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
        return hsConstant.asObject(type);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
        return hsConstant.asObject(type);
    }

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        } else {
            return JavaConstant.forBoxedPrimitive(value);
        }
    }

    // Lazily initialized
    private Class<?> wordTypesType;
    private Class<?> runtimeType;
    private Class<?> configType;

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        // Need to test all fields since there no guarantee under the JMM
        // about the order in which these fields are written.
        GraalHotSpotVMConfig config = runtime.getVMConfig();
        if (configType == null || wordTypesType == null || runtimeType == null) {
            wordTypesType = wordTypes.getClass();
            runtimeType = runtime.getClass();
            configType = config.getClass();
        }

        if (type.isAssignableFrom(wordTypesType)) {
            return type.cast(wordTypes);
        }
        if (type.isAssignableFrom(runtimeType)) {
            return type.cast(runtime);
        }
        if (type.isAssignableFrom(configType)) {
            return type.cast(config);
        }
        return null;
    }
}
