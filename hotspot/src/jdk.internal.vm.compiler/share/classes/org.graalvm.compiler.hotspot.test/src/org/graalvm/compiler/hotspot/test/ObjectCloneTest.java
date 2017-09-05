/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.util.ArrayList;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.junit.Test;

/**
 * Exercise intrinsification of {@link Object#clone}.
 */
public class ObjectCloneTest extends GraalCompilerTest {

    public static Object cloneArray(int[] array) {
        return array.clone();
    }

    public static Object cloneList(ArrayList<?> list) {
        return list.clone();
    }

    static class ObjectCloneable implements Cloneable {
        int field;

        @Override
        protected Object clone() throws CloneNotSupportedException {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    static class CloneableSubclass extends ObjectCloneable {

    }

    /*
     * This test checks that the ObjectCloneNode doesn't accidentally inject non-nullness into the
     * graph which is later removed.
     */
    public static Object notCloneable(ObjectCloneable cloneable) throws CloneNotSupportedException {
        ObjectCloneable clone = (ObjectCloneable) cloneable.clone();
        return clone.getClass();
    }

    @Test
    public void testNotIntrinsified() throws Throwable {
        test("notCloneable", new CloneableSubclass());
    }

    @Test
    public void testArray() throws Throwable {
        test("cloneArray", new int[]{1, 2, 4, 3});
    }

    @Test
    public void testList() throws Throwable {
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list.add(i);
        }
        test("cloneList", list);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf.withNodeSourcePosition(true));
    }

    static final int[] ARRAY = new int[]{1, 2, 4, 3};

    public static int[] cloneConstantArray() {
        return ARRAY.clone();
    }

    @Test
    public void testCloneConstantArray() {
        test("cloneConstantArray");
    }
}
