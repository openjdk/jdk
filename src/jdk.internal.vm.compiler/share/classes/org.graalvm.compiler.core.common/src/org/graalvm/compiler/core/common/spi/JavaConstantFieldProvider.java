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


package org.graalvm.compiler.core.common.spi;

import java.util.Arrays;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utility for default constant folding semantics for Java fields.
 */
public abstract class JavaConstantFieldProvider implements ConstantFieldProvider {

    static class Options {
        @Option(help = "Determines whether to treat final fields with default values as constant.")//
        public static final OptionKey<Boolean> TrustFinalDefaultFields = new OptionKey<>(true);
    }

    protected JavaConstantFieldProvider(MetaAccessProvider metaAccess) {
        try {
            ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);
            ResolvedJavaField[] stringFields = stringType.getInstanceFields(false);
            ResolvedJavaField valueField = null;
            ResolvedJavaField hashField = null;
            for (ResolvedJavaField field : stringFields) {
                if (field.getName().equals("value")) {
                    valueField = field;
                } else if (field.getName().equals("hash")) {
                    hashField = field;
                }
            }
            if (valueField == null) {
                throw new GraalError("missing field value " + Arrays.toString(stringFields));
            }
            if (hashField == null) {
                throw new GraalError("missing field hash " + Arrays.toString(stringFields));
            }
            stringValueField = valueField;
            stringHashField = hashField;
        } catch (SecurityException e) {
            throw new GraalError(e);
        }
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        if (isStableField(field, tool)) {
            JavaConstant value = tool.readValue();
            if (value != null && isStableFieldValueConstant(field, value, tool)) {
                return foldStableArray(value, field, tool);
            }
        }
        if (isFinalField(field, tool)) {
            JavaConstant value = tool.readValue();
            if (value != null && isFinalFieldValueConstant(field, value, tool)) {
                return tool.foldConstant(value);
            }
        }
        return null;
    }

    protected <T> T foldStableArray(JavaConstant value, ResolvedJavaField field, ConstantFieldTool<T> tool) {
        return tool.foldStableArray(value, getArrayDimension(field.getType()), isDefaultStableField(field, tool));
    }

    private static int getArrayDimension(JavaType type) {
        int dimensions = 0;
        JavaType componentType = type;
        while ((componentType = componentType.getComponentType()) != null) {
            dimensions++;
        }
        return dimensions;
    }

    private static boolean isArray(ResolvedJavaField field) {
        JavaType fieldType = field.getType();
        return fieldType instanceof ResolvedJavaType && ((ResolvedJavaType) fieldType).isArray();
    }

    @SuppressWarnings("unused")
    protected boolean isStableFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        return !value.isDefaultForKind();
    }

    @SuppressWarnings("unused")
    protected boolean isFinalFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        return !value.isDefaultForKind() || Options.TrustFinalDefaultFields.getValue(tool.getOptions());
    }

    @SuppressWarnings("unused")
    protected boolean isStableField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (isSyntheticEnumSwitchMap(field)) {
            return true;
        }
        if (isWellKnownImplicitStableField(field)) {
            return true;
        }
        if (field.equals(stringHashField)) {
            return true;
        }
        return false;
    }

    protected boolean isDefaultStableField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        assert isStableField(field, tool);
        if (isSyntheticEnumSwitchMap(field)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unused")
    protected boolean isFinalField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        return field.isFinal();
    }

    protected boolean isSyntheticEnumSwitchMap(ResolvedJavaField field) {
        if (field.isSynthetic() && field.isStatic() && isArray(field)) {
            String name = field.getName();
            if (field.isFinal() && name.equals("$VALUES") || name.equals("ENUM$VALUES")) {
                // generated int[] field for EnumClass::values()
                return true;
            } else if (name.startsWith("$SwitchMap$") || name.startsWith("$SWITCH_TABLE$")) {
                // javac and ecj generate a static field in an inner class for a switch on an enum
                // named $SwitchMap$p$k$g$EnumClass and $SWITCH_TABLE$p$k$g$EnumClass, respectively
                return true;
            }
        }
        return false;
    }

    private final ResolvedJavaField stringValueField;
    private final ResolvedJavaField stringHashField;

    protected boolean isWellKnownImplicitStableField(ResolvedJavaField field) {
        return field.equals(stringValueField);
    }
}
