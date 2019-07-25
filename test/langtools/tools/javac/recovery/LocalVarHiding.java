/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8227010
 * @summary Verify error recovery after variable redeclaration
 * @compile/fail/ref=LocalVarHiding.out -XDrawDiagnostics LocalVarHiding.java
 */

public class LocalVarHiding {
    public void test(Decl l1) {
        System.err.println(l1.originalMethod());

        OverrideVar l1 = new OverrideVar();

        System.err.println(l1.overrideMethod());

        Decl l2 = new Decl();

        System.err.println(l2.originalMethod());

        OverrideVar l2 = new OverrideVar();

        System.err.println(l2.overrideMethod());

        Decl l3 = new Decl();

        System.err.println(l3.originalMethod());

        {
            OverrideVar l3 = new OverrideVar();

            System.err.println(l3.overrideMethod());
        }

        Decl l4 = new Decl();

        System.err.println(l4.originalMethod());

        try {
            throw new OverrideVar();
        } catch (OverrideVar l4) {
            System.err.println(l4.overrideMethod());
        }

        Decl l5 = new Decl();

        System.err.println(l5.originalMethod());

        try (OverrideVar l5 = null) {
            System.err.println(l5.overrideMethod());
        }

        Decl l6 = new Decl();

        System.err.println(l6.originalMethod());

        I i1 = l6 -> {
            System.err.println(l6.overrideMethod());
        };
        I i2 = (OverrideVar l6) -> {
            System.err.println(l6.overrideMethod());
        };
    }

    public class Decl {
        public int originalMethod() {}
    }

    public class OverrideVar extends Exception implements AutoCloseable {
        @Override public void close() throws Exception {}
        public int overrideMethod() {}
    }

    public interface I {
        public void run(OverrideVar ov);
    }
}