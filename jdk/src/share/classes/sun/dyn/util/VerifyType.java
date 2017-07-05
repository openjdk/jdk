/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.dyn.util;

import java.dyn.MethodType;

/**
 * This class centralizes information about the JVM verifier
 * and its requirements about type correctness.
 * @author jrose
 */
public class VerifyType {

    private VerifyType() { }  // cannot instantiate

    /**
     * True if a value can be stacked as the source type and unstacked as the
     * destination type, without violating the JVM's type consistency.
     *
     * @param call the type of a stacked value
     * @param recv the type by which we'd like to treat it
     * @return whether the retyping can be done without motion or reformatting
     */
    public static boolean isNullConversion(Class<?> src, Class<?> dst) {
        if (src == dst)            return true;
        // Verifier allows any interface to be treated as Object:
        if (dst.isInterface())     dst = Object.class;
        if (src.isInterface())     src = Object.class;
        if (src == dst)            return true;  // check again
        if (dst == void.class)     return true;  // drop any return value
        if (isNullType(src))       return !dst.isPrimitive();
        if (!src.isPrimitive())    return dst.isAssignableFrom(src);
        // Verifier allows an int to carry byte, short, char, or even boolean:
        if (dst == int.class)      return Wrapper.forPrimitiveType(src).isSubwordOrInt();
        return false;
    }

    /**
     * Specialization of isNullConversion to reference types.

     * @param call the type of a stacked value
     * @param recv the reference type by which we'd like to treat it
     * @return whether the retyping can be done without a cast
     */
    public static boolean isNullReferenceConversion(Class<?> src, Class<?> dst) {
        assert(!dst.isPrimitive());
        if (dst.isInterface())  return true;   // verifier allows this
        if (isNullType(src))    return true;
        return dst.isAssignableFrom(src);
    }

    /**
     * Is the given type either java.lang.Void or java.lang.Null?
     * These types serve as markers for bare nulls and therefore
     * may be promoted to any type.  This is secure, since
     */
    public static boolean isNullType(Class<?> type) {
        if (type == null)  return false;
        return type == NULL_CLASS_1 || type == NULL_CLASS_2;
    }
    private static final Class<?> NULL_CLASS_1, NULL_CLASS_2;
    static {
        Class<?> nullClass1 = null, nullClass2 = null;
        try {
            nullClass1 = Class.forName("java.lang.Null");
        } catch (ClassNotFoundException ex) {
            // OK, we'll cope
        }
        NULL_CLASS_1 = nullClass1;

        // This one may also be used as a null type.
        // TO DO: Decide if we really want to legitimize it here.
        // Probably we do, unless java.lang.Null really makes it into Java 7
        nullClass2 = Void.class;
        NULL_CLASS_2 = nullClass2;
    }

    /**
     * True if a method handle can receive a call under a slightly different
     * method type, without moving or reformatting any stack elements.
     *
     * @param call the type of call being made
     * @param recv the type of the method handle receiving the call
     * @return whether the retyping can be done without motion or reformatting
     */
    public static boolean isNullConversion(MethodType call, MethodType recv) {
        if (call == recv)  return true;
        int len = call.parameterCount();
        if (len != recv.parameterCount())  return false;
        for (int i = 0; i < len; i++)
            if (!isNullConversion(call.parameterType(i), recv.parameterType(i)))
                return false;
        return isNullConversion(recv.returnType(), call.returnType());
    }

    //TO DO: isRawConversion

    /**
     * Determine if the JVM verifier allows a value of type call to be
     * passed to a formal parameter (or return variable) of type recv.
     * Returns 1 if the verifier allows the types to match without conversion.
     * Returns -1 if the types can be made to match by a JVM-supported adapter.
     * Cases supported are:
     * <ul><li>checkcast
     * </li><li>conversion between any two integral types (but not floats)
     * </li><li>unboxing from a wrapper to its corresponding primitive type
     * </li><li>conversion in either direction between float and double
     * </li></ul>
     * (Autoboxing is not supported here; it must be done via Java code.)
     * Returns 0 otherwise.
     */
    public static int canPassUnchecked(Class<?> src, Class<?> dst) {
        if (src == dst)
            return 1;

        if (dst.isPrimitive()) {
            if (dst == void.class)
                // Return anything to a caller expecting void.
                // This is a property of the implementation, which links
                // return values via a register rather than via a stack push.
                // This makes it possible to ignore cleanly.
                return 1;
            if (src == void.class)
                return 0;  // void-to-something?
            if (!src.isPrimitive())
                // Cannot pass a reference to any primitive type (exc. void).
                return 0;
            Wrapper sw = Wrapper.forPrimitiveType(src);
            Wrapper dw = Wrapper.forPrimitiveType(dst);
            if (sw.isSubwordOrInt() && dw.isSubwordOrInt()) {
                if (sw.bitWidth() >= dw.bitWidth())
                    return -1;   // truncation may be required
                if (!dw.isSigned() && sw.isSigned())
                    return -1;   // sign elimination may be required
            }
            if (src == float.class || dst == float.class) {
                if (src == double.class || dst == double.class)
                    return -1;   // floating conversion may be required
                else
                    return 0;    // other primitive conversions NYI
            } else {
                // all fixed-point conversions are supported
                return 0;
            }
        } else if (src.isPrimitive()) {
            // Cannot pass a primitive to any reference type.
            // (Maybe allow null.class?)
            return 0;
        }

        // Handle reference types in the rest of the block:

        // The verifier treats interfaces exactly like Object.
        if (isNullReferenceConversion(src, dst))
            // pass any reference to object or an arb. interface
            return 1;
        // else it's a definite "maybe" (cast is required)
        return -1;
    }

    public static int canPassRaw(Class<?> src, Class<?> dst) {
        if (dst.isPrimitive()) {
            if (dst == void.class)
                // As above, return anything to a caller expecting void.
                return 1;
            if (src == void.class)
                // Special permission for raw conversions: allow a void
                // to be captured as a garbage int.
                // Caller promises that the actual value will be disregarded.
                return dst == int.class ? 1 : 0;
            if (!src.isPrimitive())
                return 0;
            Wrapper sw = Wrapper.forPrimitiveType(src);
            Wrapper dw = Wrapper.forPrimitiveType(dst);
            if (sw.stackSlots() == dw.stackSlots())
                return 1;  // can do a reinterpret-cast on a stacked primitive
            if (sw.isSubwordOrInt() && dw == Wrapper.VOID)
                return 1;  // can drop an outgoing int value
            return 0;
        } else if (src.isPrimitive()) {
            return 0;
        }

        // Both references.
        if (isNullReferenceConversion(src, dst))
            return 1;
        return -1;
    }

    public static boolean isSpreadArgType(Class<?> spreadArg) {
        return spreadArg.isArray();
    }
    public static Class<?> spreadArgElementType(Class<?> spreadArg, int i) {
        return spreadArg.getComponentType();
    }
}
