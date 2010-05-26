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
 * @compile Finally.java
 * @compile -Xlint:finally Finally.java
 * @compile -Xlint:all Finally.java
 * @compile -Werror Finally.java
 * @compile/fail -Werror -Xlint:finally Finally.java
 * @compile/fail -Werror -Xlint:all,-path Finally.java
 */

// control: this class should generate a warning
class Finally
{
    int m1(int i) {
        try {
            return 0;
        }
        finally {
            throw new IllegalArgumentException();
        }
    }
}

// tests: the warnings that would otherwise be generated should all be suppressed
@SuppressWarnings("finally")
class Finally1
{
    int m1(int i) {
        try {
            return 0;
        }
        finally {
            throw new IllegalArgumentException();
        }
    }
}

class Finally2
{
    @SuppressWarnings("finally")
    class Bar {
        int m1(int i) {
            try {
                return 0;
            }
            finally {
                throw new IllegalArgumentException();
            }
        }
    }

    @SuppressWarnings("finally")
    int m2(int i) {
        try {
            return 0;
        }
        finally {
            throw new IllegalArgumentException();
        }
    }


    @SuppressWarnings("finally")
    static int x = new Finally2() {
            int m1(int i) {
                try {
                    return 0;
                }
                finally {
                    throw new IllegalArgumentException();
                }
            }
        }.m1(0);

}
