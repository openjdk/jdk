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
 * @bug 8335817
 * @summary Verify synthetic catches for deconstruction patterns work properly in expression lambdas
 * @compile MatchExceptionLambdaExpression.java
 * @run main MatchExceptionLambdaExpression
 */
public class MatchExceptionLambdaExpression {

    public static void main(String[] args) {
        try {
            doRunPrimitiveVoid(new A("", true), o -> checkPrimitiveVoid(o instanceof A(String s, _), true));
            throw new AssertionError("Didn't gete the expected exception!");
        } catch (MatchException ex) {
            if (ex.getCause() instanceof RequestedException) {
                //correct
            } else {
                throw ex;
            }
        }
        try {
            doRunPrimitiveVoid(new A("", true), o -> checkVoidBox(o instanceof A(String s, _), true));
            throw new AssertionError("Didn't gete the expected exception!");
        } catch (MatchException ex) {
            if (ex.getCause() instanceof RequestedException) {
                //correct
            } else {
                throw ex;
            }
        }
        try {
            doRunPrimitiveVoid(new A("", true), o -> checkNonVoid(o instanceof A(String s, _), true));
            throw new AssertionError("Didn't gete the expected exception!");
        } catch (MatchException ex) {
            if (ex.getCause() instanceof RequestedException) {
                //correct
            } else {
                throw ex;
            }
        }
        try {
            doRunVoidBox(new A("", true), o -> checkVoidBox(o instanceof A(String s, _), true));
            throw new AssertionError("Didn't gete the expected exception!");
        } catch (MatchException ex) {
            if (ex.getCause() instanceof RequestedException) {
                //correct
            } else {
                throw ex;
            }
        }
        try {
            doRunNonVoid(new A("", true), o -> checkVoidBox(o instanceof A(String s, _), true));
            throw new AssertionError("Didn't gete the expected exception!");
        } catch (MatchException ex) {
            if (ex.getCause() instanceof RequestedException) {
                //correct
            } else {
                throw ex;
            }
        }
        try {
            doRunNonVoid(new A("", true), o -> checkNonVoid(o instanceof A(String s, _), true));
            throw new AssertionError("Didn't gete the expected exception!");
        } catch (MatchException ex) {
            if (ex.getCause() instanceof RequestedException) {
                //correct
            } else {
                throw ex;
            }
        }
    }

    static void doRunPrimitiveVoid(Object inp, PrimitiveVoidFI toRun) {
       toRun.run(inp);
    }

    static void doRunVoidBox(Object inp, VoidBoxFI toRun) {
       toRun.run(inp);
    }

    static void doRunNonVoid(Object inp, NonVoidFI toRun) {
       toRun.run(inp);
    }

    static void checkPrimitiveVoid(boolean a, boolean shouldNotBeCalled) {
        if (shouldNotBeCalled) {
            throw new AssertionError("Should not be called.");
        }
    }

    static Void checkVoidBox(boolean a, boolean shouldNotBeCalled) {
        if (shouldNotBeCalled) {
            throw new AssertionError("Should not be called.");
        }
        return null;
    }

    static Object checkNonVoid(boolean a, boolean shouldNotBeCalled) {
        if (shouldNotBeCalled) {
            throw new AssertionError("Should not be called.");
        }
        return null;
    }

    interface PrimitiveVoidFI {
        public void run(Object o);
    }

    interface VoidBoxFI {
        public Void run(Object o);
    }

    interface NonVoidFI {
        public Object run(Object o);
    }

    record A(String s, boolean fail) {
        public String s() {
            if (fail) {
                throw new RequestedException();
            }
            return s;
        }
    }

    static class RequestedException extends RuntimeException {}
}
