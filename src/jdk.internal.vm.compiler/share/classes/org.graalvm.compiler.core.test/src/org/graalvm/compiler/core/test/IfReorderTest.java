/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class IfReorderTest extends GraalCompilerTest {

    private static Object fieldA = Integer.class;
    private static Object fieldB = Double.class;

    @Test
    public void test1() {
        test("test1Snippet", new ArrayList<>());
    }

    public static Object test1Snippet(Object o) {
        /*
         * Serializable and List are not mutually exclusive, so these two IFs should never be
         * reordered.
         */
        if (branchProbability(0.1, o instanceof Serializable)) {
            return fieldA;
        }
        if (branchProbability(0.9, o instanceof List)) {
            return fieldB;
        }
        return null;
    }

}
