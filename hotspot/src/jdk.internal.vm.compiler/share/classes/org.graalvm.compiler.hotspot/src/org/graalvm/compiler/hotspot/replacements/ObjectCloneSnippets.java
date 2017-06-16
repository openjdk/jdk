/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.util.EnumMap;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.meta.JavaKind;

public class ObjectCloneSnippets implements Snippets {

    public static final EnumMap<JavaKind, Method> arrayCloneMethods = new EnumMap<>(JavaKind.class);

    static {
        arrayCloneMethods.put(JavaKind.Boolean, getCloneMethod("booleanArrayClone", boolean[].class));
        arrayCloneMethods.put(JavaKind.Byte, getCloneMethod("byteArrayClone", byte[].class));
        arrayCloneMethods.put(JavaKind.Char, getCloneMethod("charArrayClone", char[].class));
        arrayCloneMethods.put(JavaKind.Short, getCloneMethod("shortArrayClone", short[].class));
        arrayCloneMethods.put(JavaKind.Int, getCloneMethod("intArrayClone", int[].class));
        arrayCloneMethods.put(JavaKind.Float, getCloneMethod("floatArrayClone", float[].class));
        arrayCloneMethods.put(JavaKind.Long, getCloneMethod("longArrayClone", long[].class));
        arrayCloneMethods.put(JavaKind.Double, getCloneMethod("doubleArrayClone", double[].class));
        arrayCloneMethods.put(JavaKind.Object, getCloneMethod("objectArrayClone", Object[].class));
    }

    private static Method getCloneMethod(String name, Class<?> param) {
        try {
            return ObjectCloneSnippets.class.getDeclaredMethod(name, param);
        } catch (SecurityException | NoSuchMethodException e) {
            throw new GraalError(e);
        }
    }

    @Snippet
    public static boolean[] booleanArrayClone(boolean[] src) {
        boolean[] result = (boolean[]) NewArrayNode.newUninitializedArray(Boolean.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Boolean);
        return result;
    }

    @Snippet
    public static byte[] byteArrayClone(byte[] src) {
        byte[] result = (byte[]) NewArrayNode.newUninitializedArray(Byte.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Byte);
        return result;
    }

    @Snippet
    public static short[] shortArrayClone(short[] src) {
        short[] result = (short[]) NewArrayNode.newUninitializedArray(Short.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Short);
        return result;
    }

    @Snippet
    public static char[] charArrayClone(char[] src) {
        char[] result = (char[]) NewArrayNode.newUninitializedArray(Character.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Char);
        return result;
    }

    @Snippet
    public static int[] intArrayClone(int[] src) {
        int[] result = (int[]) NewArrayNode.newUninitializedArray(Integer.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Int);
        return result;
    }

    @Snippet
    public static float[] floatArrayClone(float[] src) {
        float[] result = (float[]) NewArrayNode.newUninitializedArray(Float.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Float);
        return result;
    }

    @Snippet
    public static long[] longArrayClone(long[] src) {
        long[] result = (long[]) NewArrayNode.newUninitializedArray(Long.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Long);
        return result;
    }

    @Snippet
    public static double[] doubleArrayClone(double[] src) {
        double[] result = (double[]) NewArrayNode.newUninitializedArray(Double.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Double);
        return result;
    }

    @Snippet
    public static Object[] objectArrayClone(Object[] src) {
        /* Since this snippet is lowered early the array must be initialized */
        Object[] result = (Object[]) DynamicNewArrayNode.newArray(GraalDirectives.guardingNonNull(src.getClass().getComponentType()), src.length, JavaKind.Object);
        ArrayCopyCallNode.disjointUninitializedArraycopy(src, 0, result, 0, src.length, JavaKind.Object);
        return result;
    }
}
