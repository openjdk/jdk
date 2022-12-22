/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

package boot;

import static java.lang.System.out;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetCallerClass {

    public Class<?> missingCallerSensitiveAnnotation() {
        return jdk.internal.reflect.Reflection.getCallerClass();
    }

    @jdk.internal.reflect.CallerSensitive
    public Class<?> getCallerClass() {
        var caller = jdk.internal.reflect.Reflection.getCallerClass();
        out.println("caller: " + caller);
        out.println(StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(toStackTrace()));
        return caller;
    }

    private Class<?> getCallerClass(Class<?> caller) {
        out.println("caller: " + caller);
        out.println(StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(toStackTrace()));
        return caller;
    }

    @jdk.internal.reflect.CallerSensitive
    public static Class<?> getCallerClassStatic() {
        var caller = jdk.internal.reflect.Reflection.getCallerClass();
        out.println("caller: " + caller);
        out.println(StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(toStackTrace()));
        return caller;
    }

    private static Class<?> getCallerClassStatic(Class<?> caller) {
        out.println("caller: " + caller);
        out.println(StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(toStackTrace()));
        return caller;
    }

    @jdk.internal.reflect.CallerSensitive
    public Class<?> getCallerClassNoAlt() {
        var caller = jdk.internal.reflect.Reflection.getCallerClass();
        out.println("caller: " + caller);
        out.println(StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(toStackTrace()));
        return caller;
    }

    @jdk.internal.reflect.CallerSensitive
    public static Class<?> getCallerClassStaticNoAlt() {
        var caller = jdk.internal.reflect.Reflection.getCallerClass();
        out.println("caller: " + caller);
        out.println(StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(toStackTrace()));
        return caller;
    }

    private static Function<Stream<StackWalker.StackFrame>, String> toStackTrace() {
        return frames -> frames
            .takeWhile(
                frame -> !frame.getClassName().equals("GetCallerClassTest") ||
                         !frame.getMethodName().equals("main"))
            .map(Object::toString)
            .collect(Collectors.joining("\n  ", "  ", "\n"));
    }
}

