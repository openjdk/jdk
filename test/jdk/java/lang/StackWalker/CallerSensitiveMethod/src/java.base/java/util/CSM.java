/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.lang.reflect.Method;
import static java.lang.StackWalker.Option.*;
import java.lang.StackWalker.StackFrame;
import java.util.stream.Collectors;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

public class CSM {
    private static StackWalker walker =
        StackWalker.getInstance(EnumSet.of(RETAIN_CLASS_REFERENCE,
                                           SHOW_HIDDEN_FRAMES,
                                           SHOW_REFLECT_FRAMES));

    public static class Result {
        public final List<Class<?>> callers;
        public final List<StackWalker.StackFrame> frames;
        Result(List<Class<?>> callers,
               List<StackFrame> frames) {
            this.callers = callers;
            this.frames = frames;
        }
    }

    /**
     * Returns the caller of this caller-sensitive method returned by
     * by Reflection::getCallerClass.
     *
     * StackWalker::getCallerClass is expected to throw UOE
     */
    @CallerSensitive
    public static Class<?> caller() {
        Class<?> c1 = Reflection.getCallerClass();

        try {
            Class<?> c2 = walker.getCallerClass();
            throw new RuntimeException("Exception not thrown by StackWalker::getCallerClass");
        } catch (UnsupportedOperationException e) {}
        return c1;
    }

    /**
     * Returns the caller of this non-caller-sensitive method.
     */
    public static Result getCallerClass() {
        Class<?> caller = walker.getCallerClass();
        return new Result(List.of(caller), dump());
    }

    /*
     * All invocations of StackWalker::getCallerClass() from this @CallerSensitive method
     * should fail with an UnsupportedOperationException.
     */
    @CallerSensitive
    public static void getCallerClassReflectively(Method inv, List<Object[]> parameters) {
        String msg = "when calling StackWalker::getCallerClass() from @CallerSensitive method";
        for (Object[] params : parameters) {
            try {
                Object res;
                if (params[0] != inv) {
                    // First invocation is a direct reflective call of StackWalker::getCallerClass.
                    // params[0]    = StackWalker::getCallerClass()
                    // params[1][0] = StackWalker instance
                    // params[1][1] = null
                    res = ((Method)params[0]).invoke(((Object[])params[1])[0], (Object[])((Object[])params[1])[1]);
                } else {
                    // Subsequent invocations reflectively call StackWalker::getCallerClass via reflective calls to Method::invoke.
                    // inv    = Method::invoke()
                    // params = { .. { inv, { StackWalker::getCallerClass, new Object[] { WALKER, null } } } }
                    res = inv.invoke(inv, inv, params);
                }
                System.out.println("CallerSensitiveMethod::getCallerClass() called from " + res);
            } catch (Throwable expected) {
                while (expected.getCause() != null) {
                    expected = expected.getCause();
                }
                if (expected instanceof UnsupportedOperationException) {
                    System.out.println("Caught expected UnsupportedOperationException " + msg + " reflectively:");
                    expected.printStackTrace(System.out);
                    continue;
                } else {
                    System.out.println("Unexpected exception " + msg + " reflectively:");
                    expected.printStackTrace(System.out);
                }
            }
            throw new RuntimeException("Expected UnsupportedOperationException " + msg + " reflectively");
        }
    }

    static List<StackFrame> dump() {
        return walker.walk(s -> s.collect(Collectors.toList()));
    }
}
