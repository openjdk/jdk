/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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

import java.lang.reflect.Field;

/**
 * @test
 * @bug 8271623
 * @compile OptimizeOuterThis.java
 * @run main OptimizeOuterThis
 */
public class OptimizeOuterThis {

    public static void main(String[] args) {
        new OptimizeOuterThis().test();
    }

    public void test() {
        checkInner(localCapturesParameter(0), false);
        checkInner(localCapturesLocal(), false);
        checkInner(localCapturesEnclosing(), true);

        checkInner(anonCapturesParameter(0), false);
        checkInner(anonCapturesLocal(), false);
        checkInner(anonCapturesEnclosing(), true);

        checkInner(StaticMemberClass.class, false);
        checkInner(NonStaticMemberClass.class, false);
        checkInner(NonStaticMemberClassCapturesEnclosing.class, true);
    }

    public Class<?> localCapturesParameter(final int x) {
        class Local {
            public void f() {
                System.err.println(x);
            }
        }
        return Local.class;
    }

    public Class<?> localCapturesLocal() {
        final int x = 0;
        class Local {
            public void f() {
                System.err.println(x);
            }
        }
        return Local.class;
    }

    public Class<?> localCapturesEnclosing() {
        class Local {
            public void f() {
                System.err.println(OptimizeOuterThis.this);
            }
        }
        return Local.class;
    }

    public Class<?> anonCapturesParameter(final int x) {
        return new Object() {
            public void f() {
                System.err.println(x);
            }
        }.getClass();
    }

    public Class<?> anonCapturesLocal() {
        final int x = 0;
        return new Object() {
            public void f() {
                System.err.println(x);
            }
        }.getClass();
    }

    public Class<?> anonCapturesEnclosing() {
        return new Object() {
            public void f() {
                System.err.println(OptimizeOuterThis.this);
            }
        }.getClass();
    }

    static class StaticMemberClass {}

    class NonStaticMemberClass {}

    class NonStaticMemberClassCapturesEnclosing {
        public void f() {
            System.err.println(OptimizeOuterThis.this);
        }
    }

    private static void checkInner(Class<?> clazz, boolean expectOuterThis) {
        Field outerThis;
        try {
            outerThis = clazz.getDeclaredField("this$0");
        } catch (NoSuchFieldException e) {
            outerThis = null;
        }
        if (expectOuterThis) {
            if (outerThis == null) {
                throw new AssertionError(
                        String.format(
                                "expected %s to have an enclosing instance", clazz.getName()));
            }
        } else {
            if (outerThis != null) {
                throw new AssertionError(
                        String.format("%s had an unexpected enclosing instance", clazz.getName()));
            }
        }
    }
}
