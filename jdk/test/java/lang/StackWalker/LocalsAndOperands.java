/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8140450
 * @summary Sanity test for locals and operands
 * @run main LocalsAndOperands
 */

import java.lang.StackWalker.StackFrame;
import java.lang.reflect.*;
import java.util.List;
import java.util.stream.Collectors;

public class LocalsAndOperands {
    static Class<?> liveStackFrameClass;
    static Class<?> primitiveValueClass;
    static StackWalker extendedWalker;
    static Method getLocals;
    static Method getOperands;
    static Method getMonitors;
    static Method primitiveType;
    public static void main(String... args) throws Exception {
        liveStackFrameClass = Class.forName("java.lang.LiveStackFrame");
        primitiveValueClass = Class.forName("java.lang.LiveStackFrame$PrimitiveValue");

        getLocals = liveStackFrameClass.getDeclaredMethod("getLocals");
        getLocals.setAccessible(true);

        getOperands = liveStackFrameClass.getDeclaredMethod("getStack");
        getOperands.setAccessible(true);

        getMonitors = liveStackFrameClass.getDeclaredMethod("getMonitors");
        getMonitors.setAccessible(true);

        primitiveType = primitiveValueClass.getDeclaredMethod("type");
        primitiveType.setAccessible(true);

        Method method = liveStackFrameClass.getMethod("getStackWalker");
        method.setAccessible(true);
        extendedWalker = (StackWalker) method.invoke(null);
        new LocalsAndOperands(extendedWalker, true).test();

        // no access to local and operands.
        new LocalsAndOperands(StackWalker.getInstance(), false).test();
    }

    private final StackWalker walker;
    private final boolean extended;
    LocalsAndOperands(StackWalker walker, boolean extended) {
        this.walker = walker;
        this.extended = extended;
    }

    synchronized void test() throws Exception {
        int x = 10;
        char c = 'z';
        String hi = "himom";
        long l = 1000000L;
        double d =  3.1415926;

        List<StackWalker.StackFrame> frames = walker.walk(s -> s.collect(Collectors.toList()));
        if (extended) {
            for (StackWalker.StackFrame f : frames) {
                System.out.println("frame: " + f);
                Object[] locals = (Object[]) getLocals.invoke(f);
                for (int i = 0; i < locals.length; i++) {
                    System.out.format("local %d: %s type %s%n", i, locals[i], type(locals[i]));
                }

                Object[] operands = (Object[]) getOperands.invoke(f);
                for (int i = 0; i < operands.length; i++) {
                    System.out.format("operand %d: %s type %s%n", i, operands[i], type(operands[i]));
                }

                Object[] monitors = (Object[]) getMonitors.invoke(f);
                for (int i = 0; i < monitors.length; i++) {
                    System.out.format("monitor %d: %s%n", i, monitors[i]);
                }
            }
        } else {
            for (StackFrame f : frames) {
                if (liveStackFrameClass.isInstance(f))
                    throw new RuntimeException("should not be LiveStackFrame");
            }
        }
    }

    String type(Object o) throws Exception {
        if (primitiveValueClass.isInstance(o)) {
            char c = (char)primitiveType.invoke(o);
            return String.valueOf(c);
        } else {
            return o.getClass().getName();
        }
    }
}
