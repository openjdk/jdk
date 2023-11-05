/*
* Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.StackWalker.StackFrame;
import java.lang.StackWalker.Option;
import java.lang.LiveStackFrame.PrimitiveSlot;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

public final class StackWalkerHelper {
    private static final Set<Option> OPTS = EnumSet.of(Option.SHOW_REFLECT_FRAMES); // EnumSet.noneOf(Option.class);

    public static StackWalker getInstance(ContinuationScope scope) {
        return StackWalker.newInstance(Set.of(), scope);
    }

    public static StackFrame[] getStackFrames(ContinuationScope scope)     { return getStackFrames(StackWalker.newInstance(OPTS, scope)); }
    public static StackFrame[] getStackFrames(Continuation cont)           { return getStackFrames(cont.stackWalker(OPTS)); }

    public static StackFrame[] getLiveStackFrames(ContinuationScope scope) { return getStackFrames(LiveStackFrame.getStackWalker(OPTS, scope)); }
    public static StackFrame[] getLiveStackFrames(Continuation cont)       { return getStackFrames(LiveStackFrame.getStackWalker(OPTS, cont.getScope(), cont)); }

    public static StackFrame[] getStackFrames(StackWalker walker) {
        return walker.walk(fs -> fs.collect(Collectors.toList())).toArray(StackFrame[]::new);
    }

    public static StackTraceElement[] toStackTraceElement(StackFrame[] fs) {
        StackTraceElement[] out = new StackTraceElement[fs.length];
        for (int i = 0; i < fs.length; i++) {
            out[i] = fs[i].toStackTraceElement();
        }
        return out;
    }

    public static boolean equals(StackFrame[] a, StackFrame[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!equals(a[i], b[i])) return false;
        return true;
    }

    public static boolean equals(StackFrame a, StackFrame b) {
        if ( !(Objects.equals(a.getClassName(),     b.getClassName())
            && Objects.equals(a.getMethodName(),    b.getMethodName())
            )) {
            System.out.println("XXXX\ta: " + a + " a.bci: " + a.getByteCodeIndex() + " a.toSTE: " + a.toStackTraceElement()
                               + "\n\tb: " + b + " b.bci: " + b.getByteCodeIndex() + " b.toSTE: " + b.toStackTraceElement());
            return false;
        }
        try {
            if ( !(Objects.equals(a.getDeclaringClass(), b.getDeclaringClass())
                && Objects.equals(a.getMethodType(),     b.getMethodType()))) {
            System.out.println("YYYY\ta: " + a + " a.declaringClass: " + a.getDeclaringClass() + " a.methodType: " + a.getMethodType()
                               + "\n\tb: " + b + " b.declaringClass: " + b.getDeclaringClass() + " b.methodType: " + b.getMethodType());
            return false;
        }
        } catch(UnsupportedOperationException e) {}

        if (!Objects.equals(a.toStackTraceElement(), b.toStackTraceElement()))
            return false;

        if (!(a instanceof LiveStackFrame && b instanceof LiveStackFrame))
            return true;

        LiveStackFrame la = (LiveStackFrame)a;
        LiveStackFrame lb = (LiveStackFrame)b;

        // if (!Arrays.equals(la.getMonitors(), lb.getMonitors())) return false;
        // if (!slotsEqual(la.getLocals(), lb.getLocals())) return false;
        // if (!slotsEqual(la.getStack(),  lb.getStack()))  return false;

        return true;
    }

    public static String frameToString(StackFrame sf) {
        if (!(sf instanceof LiveStackFrame))
            return sf.toString();

        LiveStackFrame lsf = (LiveStackFrame) sf;
        var sb = new StringBuilder();
        sb.append(sf.toString()).append("\n");
        if (lsf.getMonitors() != null) sb.append("Monitors: ").append(Arrays.toString(lsf.getMonitors())).append("\n");
        if (lsf.getLocals() != null)   sb.append("Locals: ").append(slotsToString(lsf.getLocals())).append("\n");
        if (lsf.getStack() != null)    sb.append("Stack:  ").append(slotsToString(lsf.getStack())).append("\n");
        return sb.toString();
    }

    private static boolean slotsEqual(Object[] a, Object[] b) {
        if (a == b) return true;
        if (a.length != b.length) return false;
        for (int i=0; i<a.length; i++) if (!slotEquals(a[i], b[i])) return false;
        return true;
    }

    private static boolean slotEquals(Object a, Object b) {
        if (!(a instanceof PrimitiveSlot || b instanceof PrimitiveSlot))
            return Objects.equals(a, b);

        if (!(a instanceof PrimitiveSlot && b instanceof PrimitiveSlot))
            return false;
        PrimitiveSlot pa = (PrimitiveSlot)a;
        PrimitiveSlot pb = (PrimitiveSlot)b;

        return pa.size() == pb.size() && switch(pa.size()) {
            case 4 -> pa.intValue()  == pb.intValue();
            case 8 -> pa.longValue() == pb.longValue();
            default -> throw new AssertionError("Slot size is " + pa.size());
        };
    }

    private static String slotsToString(Object[] x) {
        return "[" + Arrays.stream(x).map(StackWalkerHelper::slotToString).collect(Collectors.joining(", ")) + "]";
    }

    private static String slotToString(Object x) {
        if (!(x instanceof PrimitiveSlot)) {
            return (x != null && x.getClass().isArray()) ? arrayToString(x) : Objects.toString(x);
        }
        PrimitiveSlot p = (PrimitiveSlot)x;
        return switch(p.size()) {
            case 4 -> intOrFloatToString(p.intValue());
            case 8 -> longOrDoubleToString(p.longValue());
            default -> throw new AssertionError("Slot size is " + p.size());
        };
    }

    private static String intOrFloatToString(int x) { return Integer.toString(x) + "/" + Float.toString(Float.intBitsToFloat(x)); }
    private static String longOrDoubleToString(long x) { return Long.toString(x) + "/" + Double.toString(Double.longBitsToDouble(x)); }

    private static String arrayToString(Object array) {
        assert array != null && array.getClass().isArray();
        if (array.getClass().componentType().isPrimitive()) {
            try {
                return (String)Arrays.class.getMethod("toString", new Class<?>[]{array.getClass()}).invoke(array);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        } else return Arrays.toString((Object[])array);
    }
}
