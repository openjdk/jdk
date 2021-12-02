/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278078
 * @summary error: cannot reference super before supertype constructor has been called
 * @compile ValidThisAndSuperInConstructorArgTest.java
 * @run main ValidThisAndSuperInConstructorArgTest
 */
public class ValidThisAndSuperInConstructorArgTest  {

    static final String SUPER = "unexpected super call";
    static final String THIS = "unexpected this call";

    public String get() {
        return SUPER;
    }

    static class StaticSubClass extends ValidThisAndSuperInConstructorArgTest {
        @Override
        public String get() {
            return THIS;
        }

        class InnerClass extends AssertionError {
            InnerClass() {
                super(StaticSubClass.super.get());
            }
            InnerClass(int i) {
                this(StaticSubClass.super.get());
            }
            InnerClass(boolean b) {
                super(StaticSubClass.this.get());
            }
            InnerClass(double d) {
                this(StaticSubClass.this.get());
            }
            InnerClass(String s) {
                super(s);
            }
            void assertThis() {
                if (!THIS.equals(getMessage())) throw this;
            }
            void assertSuper() {
                if (!SUPER.equals(getMessage())) throw this;
            }
        }
    }

    public static void main(String...args) {
        var test = new StaticSubClass();
        test.new InnerClass().assertSuper();
        test.new InnerClass(1).assertSuper();
        test.new InnerClass(true).assertThis();
        test.new InnerClass(1.0).assertThis();
    }
}
