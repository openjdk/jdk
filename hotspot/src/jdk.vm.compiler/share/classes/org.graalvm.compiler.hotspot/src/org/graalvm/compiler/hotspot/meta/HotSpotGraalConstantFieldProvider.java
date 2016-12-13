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
import static org.graalvm.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider.ImmutableCodeLazy.isCalledForSnippets;
import static org.graalvm.compiler.hotspot.stubs.SnippetStub.SnippetGraphUnderConstruction;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.options.StableOptionValue;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;

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
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        assert !ImmutableCode.getValue() || isCalledForSnippets(metaAccess) || SnippetGraphUnderConstruction.get() != null ||
                        FieldReadEnabledInImmutableCode.get() == Boolean.TRUE : tool.getReceiver();
        if (!field.isStatic() && field.getName().equals("value")) {
            if (getStableOptionValueType().isInstance(tool.getReceiver())) {
                JavaConstant ret = tool.readValue();
                return tool.foldConstant(ret);
            }
        }

        return super.readConstantField(field, tool);
    }

    /**
     * In AOT mode, some fields should never be embedded even for snippets/replacements.
     */
    @Override
    protected boolean isStaticFieldConstant(ResolvedJavaField field) {
        return super.isStaticFieldConstant(field) && (!ImmutableCode.getValue() || ImmutableCodeLazy.isEmbeddable(field));
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

    private ResolvedJavaType cachedStableOptionValueType;
    private ResolvedJavaType cachedHotSpotVMConfigType;
    private ResolvedJavaType cachedSnippetCounterType;
    private ResolvedJavaType cachedNodeClassType;

    private ResolvedJavaType getStableOptionValueType() {
        if (cachedStableOptionValueType == null) {
            cachedStableOptionValueType = metaAccess.lookupJavaType(StableOptionValue.class);
        }
        return cachedStableOptionValueType;
    }

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

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public static final ThreadLocal<Boolean> FieldReadEnabledInImmutableCode = assertionsEnabled() ? new ThreadLocal<>() : null;

    /**
     * Compares two {@link StackTraceElement}s for equality, ignoring differences in
     * {@linkplain StackTraceElement#getLineNumber() line number}.
     */
    private static boolean equalsIgnoringLine(StackTraceElement left, StackTraceElement right) {
        return left.getClassName().equals(right.getClassName()) && left.getMethodName().equals(right.getMethodName()) && left.getFileName().equals(right.getFileName());
    }

    /**
     * Separate out the static initialization of {@linkplain #isEmbeddable(ResolvedJavaField)
     * embeddable fields} to eliminate cycles between clinit and other locks that could lead to
     * deadlock. Static code that doesn't call back into type or field machinery is probably ok but
     * anything else should be made lazy.
     */
    static class ImmutableCodeLazy {

        /**
         * If the compiler is configured for AOT mode, {@link #readConstantField} should be only
         * called for snippets or replacements.
         */
        static boolean isCalledForSnippets(MetaAccessProvider metaAccess) {
            assert ImmutableCode.getValue();
            ResolvedJavaMethod makeGraphMethod = null;
            ResolvedJavaMethod initMethod = null;
            try {
                Class<?> rjm = ResolvedJavaMethod.class;
                makeGraphMethod = metaAccess.lookupJavaMethod(ReplacementsImpl.class.getDeclaredMethod("makeGraph", rjm, Object[].class, rjm));
                initMethod = metaAccess.lookupJavaMethod(SnippetTemplate.AbstractTemplates.class.getDeclaredMethod("template", Arguments.class));
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalError(e);
            }
            StackTraceElement makeGraphSTE = makeGraphMethod.asStackTraceElement(0);
            StackTraceElement initSTE = initMethod.asStackTraceElement(0);

            StackTraceElement[] stackTrace = new Exception().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                // Ignoring line numbers should not weaken this check too much while at
                // the same time making it more robust against source code changes
                if (equalsIgnoringLine(makeGraphSTE, element) || equalsIgnoringLine(initSTE, element)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Determine if it's ok to embed the value of {@code field}.
         */
        static boolean isEmbeddable(ResolvedJavaField field) {
            assert ImmutableCode.getValue();
            return !embeddableFields.contains(field);
        }

        private static final List<ResolvedJavaField> embeddableFields = new ArrayList<>();
        static {
            try {
                MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
                embeddableFields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("TRUE")));
                embeddableFields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("FALSE")));

                Class<?> characterCacheClass = Character.class.getDeclaredClasses()[0];
                assert "java.lang.Character$CharacterCache".equals(characterCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(characterCacheClass.getDeclaredField("cache")));

                Class<?> byteCacheClass = Byte.class.getDeclaredClasses()[0];
                assert "java.lang.Byte$ByteCache".equals(byteCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(byteCacheClass.getDeclaredField("cache")));

                Class<?> shortCacheClass = Short.class.getDeclaredClasses()[0];
                assert "java.lang.Short$ShortCache".equals(shortCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(shortCacheClass.getDeclaredField("cache")));

                Class<?> integerCacheClass = Integer.class.getDeclaredClasses()[0];
                assert "java.lang.Integer$IntegerCache".equals(integerCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(integerCacheClass.getDeclaredField("cache")));

                Class<?> longCacheClass = Long.class.getDeclaredClasses()[0];
                assert "java.lang.Long$LongCache".equals(longCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(longCacheClass.getDeclaredField("cache")));

                embeddableFields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("UNASSIGNED_STACK")));
                embeddableFields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("SUPPRESSED_SENTINEL")));
            } catch (SecurityException | NoSuchFieldException e) {
                throw new GraalError(e);
            }
        }
    }

}
