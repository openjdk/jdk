/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ArrayLengthProviderTest extends GraalCompilerTest {

    public static Object test0Snippet(ArrayList<?> list, boolean a) {
        while (true) {
            Object[] array = toArray(list);
            if (array.length < 1) {
                return null;
            }
            if (array[0] instanceof String || a) {
                /*
                 * This code is outside of the loop. Accessing the array reqires a ValueProxyNode.
                 * When the simplification of the ArrayLengthNode replaces the length access with
                 * the ArrayList.size used to create the array, then the value needs to have a
                 * ValueProxyNode too. In addition, the two parts of the if-condition actually lead
                 * to two separate loop exits, with two separate proxy nodes. A ValuePhiNode is
                 * present originally for the array, and the array length simplification needs to
                 * create a new ValuePhiNode for the two newly introduced ValueProxyNode.
                 */
                if (array.length < 1) {
                    return null;
                }
                return array[0];
            }
        }
    }

    public static Object test1Snippet(ArrayList<?> list, boolean a, boolean b) {
        while (true) {
            Object[] array = toArray(list);
            if (a || b) {
                if (array.length < 1) {
                    return null;
                }
                return array[0];
            }
        }
    }

    public static Object[] toArray(List<?> list) {
        return new Object[list.size()];
    }

    @Test
    public void test0() {
        test("test0Snippet", new ArrayList<>(Arrays.asList("a", "b")), true);
    }

    @Test
    public void test1() {
        test("test1Snippet", new ArrayList<>(Arrays.asList("a", "b")), true, true);
    }
}
