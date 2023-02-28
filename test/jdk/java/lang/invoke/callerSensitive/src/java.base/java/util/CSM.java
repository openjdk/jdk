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

package java.util;

import java.lang.StackWalker.StackFrame;
import java.util.stream.Stream;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.CallerSensitiveAdapter;
import jdk.internal.reflect.Reflection;

import static java.lang.StackWalker.Option.*;

public class CSM {
    public final Class<?> caller;
    public final List<StackFrame> stackFrames;
    public final boolean adapter;
    CSM(Class<?> caller, boolean adapter) {
        this.caller = caller;
        StackWalker sw = StackWalker.getInstance(Set.of(RETAIN_CLASS_REFERENCE, SHOW_HIDDEN_FRAMES));
        this.stackFrames = sw.walk(Stream::toList);
        this.adapter = adapter;
    }

    /**
     * Returns the caller of this caller-sensitive method returned by
     * by Reflection::getCallerClass except if this method is called
     * via method handle.
     */
    @CallerSensitive
    public static CSM caller() {
        return caller(Reflection.getCallerClass());
    }

    /**
     * If caller() is invoked via method handle, this alternate method is
     * called instead.  The caller class would be the lookup class.
     */
    @CallerSensitiveAdapter
    private static CSM caller(Class<?> caller) {
        return new CSM(caller, true);
    }

    /**
     * Returns the caller of this caller-sensitive method returned by
     * by Reflection::getCallerClass.
     */
    @CallerSensitive
    public static CSM callerNoAlternateImpl() {
        return new CSM(Reflection.getCallerClass(), false);
    }

    @CallerSensitive
    public static CSM caller(Object o1, Object o2, Object o3, Object o4) {
        return caller(o1, o2, o3, o4, Reflection.getCallerClass());
    }

    /**
     * If caller() is invoked via method handle, this alternate method is
     * called instead.  The caller class would be the lookup class.
     */
    @CallerSensitiveAdapter
    private static CSM caller(Object o1, Object o2, Object o3, Object o4, Class<?> caller) {
        return new CSM(caller, true);
    }
}