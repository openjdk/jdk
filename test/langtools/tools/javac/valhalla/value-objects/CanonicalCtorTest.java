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

/**
 * @test
 * @bug 8208067
 * @enablePreview
 * @summary Verify that instance methods are callable from ctor after all instance fields are DA.
 */

public value class CanonicalCtorTest {

    private final int x, ymx;

    CanonicalCtorTest(int x, int y) {

        ymx = y - x;
        this.x = x;

        // all fields are assigned now, but we need a explicit `super()` invocation before accessing `this`
        super();

        validate();                 // OK: DU = {}
        this.validate();            // OK: DU = {}
        CanonicalCtorTest.this.validate();          // OK: DU = {}

        assert (this.x > 0);        // OK: DU = {}
        assert (this.y() > 0);      // OK: DU = {}
    }

    int x() {
        return x;
    }

    int y() {
        return ymx + x;
    }

    void validate() {
        assert (x() > 0 && y() > 0);
    }

    public static void main(String... av) {
        CanonicalCtorTest z = new CanonicalCtorTest(1, 10);
        assert (z.x() == 1);
        assert (z.y() == 10);
    }
}
