/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @compile Exceptions.java
 * @run main Exceptions
 */
public class Exceptions {
    public static void main(String... args) {
        R r;
        r = new R("", "a");
        try {
            r = r with {
                throw new AssertionError("Should not get here!");
            };
        } catch (Intentional ex) {
            if (!"s1".equals(ex.getMessage())) {
                throw new AssertionError(ex);
            }
        }
        r = new R("a", "");
        try {
            r = r with {
                throw new AssertionError("Should not get here!");
            };
        } catch (Intentional ex) {
            if (!"s2".equals(ex.getMessage())) {
                throw new AssertionError(ex);
            }
        }
        r = new R("a", "a");
        try {
            r = r with {
                throw new Intentional("in body");
            };
        } catch (Intentional ex) {
            if (!"in body".equals(ex.getMessage())) {
                throw new AssertionError(ex);
            }
        }
        try {
            keepOnStack(0, r with {
                try {
                    throw new Intentional("in body");
                } catch (Intentional ex) {
                    //ignored...
                }
            });
        } catch (Intentional ex) {
            if (!"in body".equals(ex.getMessage())) {
                throw new AssertionError(ex);
            }
        }
        r = null;
        try {
            r = r with {};
            throw new AssertionError("Should not get here!");
        } catch (NullPointerException ex) {
            //OK
        }
    }

    private static void keepOnStack(int value, R r) { }

    record R(String s1, String s2) {

        public String s1() {
            if (s1.isEmpty()) {
                throw new Intentional("s1");
            }
            return s1;
        }

        public String s2() {
            if (s2.isEmpty()) {
                throw new Intentional("s2");
            }
            return s2;
        }
        
    }

    public static class Intentional extends RuntimeException {

        public Intentional(String msg) {
            super(msg);
        }

    }

}
