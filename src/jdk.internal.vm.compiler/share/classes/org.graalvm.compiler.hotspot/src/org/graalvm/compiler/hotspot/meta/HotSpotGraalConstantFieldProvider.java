/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetCounter;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extends {@link HotSpotConstantFieldProvider} to override the implementation of
 * {@link #readConstantField} with Graal specific semantics.
 */
public class HotSpotGraalConstantFieldProvider extends HotSpotConstantFieldProvider {

    public HotSpotGraalConstantFieldProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        super(config, metaAccess);
        this.metaAccess = metaAccess;
    }

    @Override
    protected boolean isStaticFieldConstant(ResolvedJavaField field, OptionValues options) {
        return super.isStaticFieldConstant(field, options) && (!ImmutableCode.getValue(options) || isEmbeddableField(field));
    }

    /**
     * The set of fields whose values cannot be constant folded in ImmutableCode mode. This is
     * volatile to support double-checked locking lazy initialization.
     */
    private volatile List<ResolvedJavaField> nonEmbeddableFields;

    protected boolean isEmbeddableField(ResolvedJavaField field) {
        if (nonEmbeddableFields == null) {
            synchronized (this) {
                if (nonEmbeddableFields == null) {
                    List<ResolvedJavaField> fields = new ArrayList<>();
                    try {
                        fields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("TRUE")));
                        fields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("FALSE")));

                        Class<?> characterCacheClass = Character.class.getDeclaredClasses()[0];
                        assert "java.lang.Character$CharacterCache".equals(characterCacheClass.getName());
                        fields.add(metaAccess.lookupJavaField(characterCacheClass.getDeclaredField("cache")));

                        Class<?> byteCacheClass = Byte.class.getDeclaredClasses()[0];
                        assert "java.lang.Byte$ByteCache".equals(byteCacheClass.getName());
                        fields.add(metaAccess.lookupJavaField(byteCacheClass.getDeclaredField("cache")));

                        Class<?> shortCacheClass = Short.class.getDeclaredClasses()[0];
                        assert "java.lang.Short$ShortCache".equals(shortCacheClass.getName());
                        fields.add(metaAccess.lookupJavaField(shortCacheClass.getDeclaredField("cache")));

                        Class<?> integerCacheClass = Integer.class.getDeclaredClasses()[0];
                        assert "java.lang.Integer$IntegerCache".equals(integerCacheClass.getName());
                        fields.add(metaAccess.lookupJavaField(integerCacheClass.getDeclaredField("cache")));

                        Class<?> longCacheClass = Long.class.getDeclaredClasses()[0];
                        assert "java.lang.Long$LongCache".equals(longCacheClass.getName());
                        fields.add(metaAccess.lookupJavaField(longCacheClass.getDeclaredField("cache")));

                        fields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("UNASSIGNED_STACK")));
                        fields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("SUPPRESSED_SENTINEL")));
                    } catch (SecurityException | NoSuchFieldException e) {
                        throw new GraalError(e);
                    }
                    nonEmbeddableFields = fields;
                }
            }
        }
        return !nonEmbeddableFields.contains(field);
    }

    @Override
    protected boolean isFinalFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (super.isFinalFieldValueConstant(field, value, tool)) {
            return true;
        }

        if (!field.isStatic()) {
            JavaConstant receiver = tool.getReceiver();
            if (getSnippetCounterType().isInstance(receiver) || getNodeClassType().isInstance(receiver)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean isStableFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (super.isStableFieldValueConstant(field, value, tool)) {
            return true;
        }

        if (!field.isStatic()) {
            JavaConstant receiver = tool.getReceiver();
            if (getHotSpotVMConfigType().isInstance(receiver)) {
                return true;
            }
        }

        return false;
    }

    private final MetaAccessProvider metaAccess;

    private ResolvedJavaType cachedHotSpotVMConfigType;
    private ResolvedJavaType cachedSnippetCounterType;
    private ResolvedJavaType cachedNodeClassType;

    private ResolvedJavaType getHotSpotVMConfigType() {
        if (cachedHotSpotVMConfigType == null) {
            cachedHotSpotVMConfigType = metaAccess.lookupJavaType(GraalHotSpotVMConfig.class);
        }
        return cachedHotSpotVMConfigType;
    }

    private ResolvedJavaType getSnippetCounterType() {
        if (cachedSnippetCounterType == null) {
            cachedSnippetCounterType = metaAccess.lookupJavaType(SnippetCounter.class);
        }
        return cachedSnippetCounterType;
    }

    private ResolvedJavaType getNodeClassType() {
        if (cachedNodeClassType == null) {
            cachedNodeClassType = metaAccess.lookupJavaType(NodeClass.class);
        }
        return cachedNodeClassType;
    }
}
