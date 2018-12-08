/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.junit.Test;

import java.util.Arrays;

public class ArrayEqualsConstantLengthTest extends ArraysSubstitutionsTestBase {

    private static final int[] LENGTHS = {0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 24, 31, 32, 33, 48, 63, 64, 65, 127, 128, 129, 255, 256, 257};

    private void testEquals(String methodName, Class<?>[] parameterTypes, ArrayBuilder builder) {
        for (int length : LENGTHS) {
            testSubstitution(methodName, ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false,
                            true, new Object[]{
                                            builder.newArray(length, 0, 1),
                                            builder.newArray(length, 0, 1),
                                            builder.newArray(length, 0, 1)},
                            new Object[]{
                                            builder.newArray(length, 0, 1),
                                            builder.newArray(length, 1, 1),
                                            builder.newArray(length, 0, 2)});
        }
    }

    @Test
    public void testEqualsBoolean() {
        testEquals("arraysEqualsBoolean", new Class<?>[]{boolean[].class, boolean[].class}, ArraysSubstitutionsTestBase::booleanArray);
    }

    @Test
    public void testEqualsByte() {
        testEquals("arraysEqualsByte", new Class<?>[]{byte[].class, byte[].class}, ArraysSubstitutionsTestBase::byteArray);
    }

    @Test
    public void testEqualsChar() {
        testEquals("arraysEqualsChar", new Class<?>[]{char[].class, char[].class}, ArraysSubstitutionsTestBase::charArray);
    }

    @Test
    public void testEqualsShort() {
        testEquals("arraysEqualsShort", new Class<?>[]{short[].class, short[].class}, ArraysSubstitutionsTestBase::shortArray);
    }

    @Test
    public void testEqualsInt() {
        testEquals("arraysEqualsInt", new Class<?>[]{int[].class, int[].class}, ArraysSubstitutionsTestBase::intArray);
    }

    @Test
    public void testEqualsLong() {
        testEquals("arraysEqualsLong", new Class<?>[]{long[].class, long[].class}, ArraysSubstitutionsTestBase::longArray);
    }

    private Object[] constantArgs;

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        if (constantArgs != null) {
            ConstantBindingParameterPlugin constantBinding = new ConstantBindingParameterPlugin(constantArgs, this.getMetaAccess(), this.getSnippetReflection());
            conf.getPlugins().appendParameterPlugin(constantBinding);
        }
        return super.editGraphBuilderConfiguration(conf);
    }

    @Override
    protected void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, Class<?>[] parameterTypes, boolean optional, boolean forceCompilation,
                    Object[] args1, Object[] args2) {
        constantArgs = new Object[]{args1[0], null};
        super.testSubstitution(testMethodName, intrinsicClass, holder, methodName, parameterTypes, optional, forceCompilation, args1, args2);
    }
}
