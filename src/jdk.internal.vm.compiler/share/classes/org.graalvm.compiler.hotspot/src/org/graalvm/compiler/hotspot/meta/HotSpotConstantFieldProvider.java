/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.spi.JavaConstantFieldProvider;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements the default constant folding semantics for Java fields in the HotSpot VM.
 */
public class HotSpotConstantFieldProvider extends JavaConstantFieldProvider {

    private final GraalHotSpotVMConfig config;

    public HotSpotConstantFieldProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        super(metaAccess);
        this.config = config;
    }

    @Override
    protected boolean isStableField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (!config.foldStableValues) {
            return false;
        }
        if (field.isStatic() && !isStaticFieldConstant(field, tool.getOptions())) {
            return false;
        }

        if (((HotSpotResolvedJavaField) field).isStable()) {
            return true;
        }
        return super.isStableField(field, tool);
    }

    @Override
    protected boolean isFinalField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (field.isStatic() && !isStaticFieldConstant(field, tool.getOptions())) {
            return false;
        }

        return super.isFinalField(field, tool);
    }

    private static final String SystemClassName = "Ljava/lang/System;";

    protected boolean isStaticFieldConstant(ResolvedJavaField field, @SuppressWarnings("unused") OptionValues options) {
        ResolvedJavaType declaringClass = field.getDeclaringClass();
        return declaringClass.isInitialized() && !declaringClass.getName().equals(SystemClassName);
    }
}
