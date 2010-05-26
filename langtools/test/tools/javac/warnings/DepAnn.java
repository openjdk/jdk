/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4986256
 * @compile DepAnn.java
 * @compile -Xlint:dep-ann DepAnn.java
 * @compile -Xlint:all DepAnn.java
 * @compile -Werror DepAnn.java
 * @compile/fail -Werror -Xlint:dep-ann DepAnn.java
 * @compile/fail -Werror -Xlint:all,-path DepAnn.java
 */

// control: this class should generate warnings
/** @deprecated */
class DepAnn
{
    /** @deprecated */
    void m1(int i) {
    }
}

// tests: the warnings that would otherwise be generated should all be suppressed
@SuppressWarnings("dep-ann")
/** @deprecated */
class DepAnn1
{
    /** @deprecated */
    void m1(int i) {
        /** @deprecated */
        int x = 3;
    }
}

class DepAnn2
{
    @SuppressWarnings("dep-ann")
    /** @deprecated */
    class Bar {
        /** @deprecated */
        void m1(int i) {
        }
    }

    @SuppressWarnings("dep-ann")
    /** @deprecated */
    void m2(int i) {
    }


    @SuppressWarnings("dep-ann")
    static int x = new DepAnn2() {
            /** @deprecated */
            int m1(int i) {
                return 0;
            }
        }.m1(0);

}

// this class should produce warnings because @SuppressWarnings should not be inherited
/** @deprecated */
class DepAnn3 extends DepAnn1
{
    /** @deprecated */
    void m1(int i) {
    }
}
