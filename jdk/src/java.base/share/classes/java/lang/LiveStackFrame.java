/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
import java.util.EnumSet;
import java.util.Set;

import static java.lang.StackWalker.ExtendedOption.LOCALS_AND_OPERANDS;

/**
 * <em>UNSUPPORTED</em> This interface is intended to be package-private
 * or move to an internal package.<p>
 *
 * {@code LiveStackFrame} represents a frame storing data and partial results.
 * Each frame has its own array of local variables (JVMS section 2.6.1),
 * its own operand stack (JVMS section 2.6.2) for a method invocation.
 *
 * @jvms 2.6 Frames
 */
/* package-private */
interface LiveStackFrame extends StackFrame {
    /**
     * Return the monitors held by this stack frame. This method returns
     * an empty array if no monitor is held by this stack frame.
     *
     * @return the monitors held by this stack frames
     */
    public Object[] getMonitors();

    /**
     * Gets the local variable array of this stack frame.
     *
     * <p>A single local variable can hold a value of type boolean, byte, char,
     * short, int, float, reference or returnAddress.  A pair of local variables
     * can hold a value of type long or double.  In other words,
     * a value of type long or type double occupies two consecutive local
     * variables.  For a value of primitive type, the element in the
     * local variable array is an {@link PrimitiveValue} object;
     * otherwise, the element is an {@code Object}.
     *
     * @return  the local variable array of this stack frame.
     */
    public Object[] getLocals();

    /**
     * Gets the operand stack of this stack frame.
     *
     * <p>
     * The 0-th element of the returned array represents the top of the operand stack.
     * This method returns an empty array if the operand stack is empty.
     *
     * <p>Each entry on the operand stack can hold a value of any Java Virtual
     * Machine Type.
     * For a value of primitive type, the element in the returned array is
     * an {@link PrimitiveValue} object; otherwise, the element is the {@code Object}
     * on the operand stack.
     *
     * @return the operand stack of this stack frame.
     */
    public Object[] getStack();

    /**
     * <em>UNSUPPORTED</em> This interface is intended to be package-private
     * or move to an internal package.<p>
     *
     * Represents a local variable or an entry on the operand whose value is
     * of primitive type.
     */
    public abstract class PrimitiveValue {
        /**
         * Returns the base type of this primitive value, one of
         * {@code B, D, C, F, I, J, S, Z}.
         *
         * @return Name of a base type
         * @jvms table 4.3-A
         */
        abstract char type();

        /**
         * Returns the boolean value if this primitive value is of type boolean.
         * @return the boolean value if this primitive value is of type boolean.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type boolean.
         */
        public boolean booleanValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the int value if this primitive value is of type int.
         * @return the int value if this primitive value is of type int.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type int.
         */
        public int intValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the long value if this primitive value is of type long.
         * @return the long value if this primitive value is of type long.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type long.
         */
        public long longValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the char value if this primitive value is of type char.
         * @return the char value if this primitive value is of type char.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type char.
         */
        public char charValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the byte value if this primitive value is of type byte.
         * @return the byte value if this primitive value is of type byte.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type byte.
         */
        public byte byteValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the short value if this primitive value is of type short.
         * @return the short value if this primitive value is of type short.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type short.
         */
        public short shortValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the float value if this primitive value is of type float.
         * @return the float value if this primitive value is of type float.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type float.
         */
        public float floatValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }

        /**
         * Returns the double value if this primitive value is of type double.
         * @return the double value if this primitive value is of type double.
         *
         * @throws UnsupportedOperationException if this primitive value is not
         * of type double.
         */
        public double doubleValue() {
            throw new UnsupportedOperationException("this primitive of type " + type());
        }
    }


    /**
     * Gets {@code StackWalker} that can get locals and operands.
     *
     * @throws SecurityException if the security manager is present and
     * denies access to {@code RuntimePermission("liveStackFrames")}
     */
    public static StackWalker getStackWalker() {
        return getStackWalker(EnumSet.noneOf(StackWalker.Option.class));
    }

    /**
     * Gets a {@code StackWalker} instance with the given options specifying
     * the stack frame information it can access, and which will traverse at most
     * the given {@code maxDepth} number of stack frames.  If no option is
     * specified, this {@code StackWalker} obtains the method name and
     * the class name with all
     * {@linkplain StackWalker.Option#SHOW_HIDDEN_FRAMES hidden frames} skipped.
     * The returned {@code StackWalker} can get locals and operands.
     *
     * @param options stack walk {@link StackWalker.Option options}
     *
     * @throws SecurityException if the security manager is present and
     * it denies access to {@code RuntimePermission("liveStackFrames")}; or
     * or if the given {@code options} contains
     * {@link StackWalker.Option#RETAIN_CLASS_REFERENCE Option.RETAIN_CLASS_REFERENCE}
     * and it denies access to {@code StackFramePermission("retainClassReference")}.
     */
    public static StackWalker getStackWalker(Set<StackWalker.Option> options) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("liveStackFrames"));
        }
        return StackWalker.newInstance(options, LOCALS_AND_OPERANDS);
    }
}
