/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;

import java.util.EnumMap;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

public class ObjectCloneSnippets implements Snippets {

    public static class Templates extends AbstractTemplates {

        final EnumMap<JavaKind, SnippetInfo> arrayCloneMethods = new EnumMap<>(JavaKind.class);

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
            arrayCloneMethods.put(JavaKind.Boolean, snippet(ObjectCloneSnippets.class, "booleanArrayClone"));
            arrayCloneMethods.put(JavaKind.Byte, snippet(ObjectCloneSnippets.class, "byteArrayClone"));
            arrayCloneMethods.put(JavaKind.Char, snippet(ObjectCloneSnippets.class, "charArrayClone"));
            arrayCloneMethods.put(JavaKind.Short, snippet(ObjectCloneSnippets.class, "shortArrayClone"));
            arrayCloneMethods.put(JavaKind.Int, snippet(ObjectCloneSnippets.class, "intArrayClone"));
            arrayCloneMethods.put(JavaKind.Float, snippet(ObjectCloneSnippets.class, "floatArrayClone"));
            arrayCloneMethods.put(JavaKind.Long, snippet(ObjectCloneSnippets.class, "longArrayClone"));
            arrayCloneMethods.put(JavaKind.Double, snippet(ObjectCloneSnippets.class, "doubleArrayClone"));
            arrayCloneMethods.put(JavaKind.Object, snippet(ObjectCloneSnippets.class, "objectArrayClone"));
        }
    }

    @Snippet
    public static boolean[] booleanArrayClone(boolean[] src) {
        boolean[] result = (boolean[]) NewArrayNode.newUninitializedArray(Boolean.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Boolean, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static byte[] byteArrayClone(byte[] src) {
        byte[] result = (byte[]) NewArrayNode.newUninitializedArray(Byte.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Byte, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static short[] shortArrayClone(short[] src) {
        short[] result = (short[]) NewArrayNode.newUninitializedArray(Short.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Short, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static char[] charArrayClone(char[] src) {
        char[] result = (char[]) NewArrayNode.newUninitializedArray(Character.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Char, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static int[] intArrayClone(int[] src) {
        int[] result = (int[]) NewArrayNode.newUninitializedArray(Integer.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Int, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static float[] floatArrayClone(float[] src) {
        float[] result = (float[]) NewArrayNode.newUninitializedArray(Float.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Float, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static long[] longArrayClone(long[] src) {
        long[] result = (long[]) NewArrayNode.newUninitializedArray(Long.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Long, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static double[] doubleArrayClone(double[] src) {
        double[] result = (double[]) NewArrayNode.newUninitializedArray(Double.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Double, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }

    @Snippet
    public static Object[] objectArrayClone(Object[] src) {
        /* Since this snippet is lowered early the array must be initialized */
        Object[] result = (Object[]) DynamicNewArrayNode.newArray(GraalDirectives.guardingNonNull(src.getClass().getComponentType()), src.length, JavaKind.Object);
        ArrayCopyCallNode.disjointUninitializedArraycopy(src, 0, result, 0, src.length, JavaKind.Object, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }
}
