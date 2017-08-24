/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

public class ConstantArrayReadFoldingTest extends GraalCompilerTest {

    enum E {
        A(0.001),
        B(0.01),
        C(0.5),
        D(2.0),
        E(3.0),
        F(4.0),
        G(5.0);

        public final double ceiling;
        public double weight;

        E(double ceiling) {
            this.ceiling = ceiling;
        }
    }

    public Object test1Snippet(double value) {
        for (E kind : E.values()) {
            if (value <= kind.ceiling) {
                return kind;
            }
        }
        throw new IllegalArgumentException();
    }

    @Test
    public void test1() {
        test("test1Snippet", 1.0);
        test("test1Snippet", 2.0);
    }

}
