/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke.util;

import java.lang.invoke.MethodType;

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
     * <p>
     * If both types are references, we apply the verifier's subclass check
     * (or subtyping, if keepInterfaces).
     * If the src type is a type guaranteed to be null (Void) it can be converted
     * to any other reference type.
     * <p>
     * If both types are primitives, we apply the verifier's primitive conversions.
     * These do not include Java conversions such as long to double, since those
     * require computation and (in general) stack depth changes.
     * But very simple 32-bit viewing changes, such as byte to int,
     * are null conversions, because they do not require any computation.
     * These conversions are from any type to a wider type up to 32 bits,
     * as long as the conversion is not signed to unsigned (byte to char).
     * <p>
     * The primitive type 'void' does not interconvert with any other type,
     * even though it is legal to drop any type from the stack and "return void".
     * The stack effects, though are different between void and any other type,
     * so it is safer to report a non-trivial conversion.
     *
     * @param src the type of a stacked value
     * @param dst the type by which we'd like to treat it
     * @param keepInterfaces if false, we treat any interface as if it were Object
     * @return whether the retyping can be done without motion or reformatting
     */
    public static boolean isNullConversion(Class<?> src, Class<?> dst, boolean keepInterfaces) {
        if (src == dst)            return true;
        // Verifier allows any interface to be treated as Object:
        if (!keepInterfaces) {
            if (dst.isInterface())  dst = Object.class;
            if (src.isInterface())  src = Object.class;
            if (src == dst)         return true;  // check again
        }
        if (isNullType(src))       return !dst.isPrimitive();
        if (!src.isPrimitive())    return dst.isAssignableFrom(src);
        if (!dst.isPrimitive())    return false;
        // Verifier allows an int to carry byte, short, char, or even boolean:
        Wrapper sw = Wrapper.forPrimitiveType(src);
        if (dst == int.class)      return sw.isSubwordOrInt();
        Wrapper dw = Wrapper.forPrimitiveType(dst);
        if (!sw.isSubwordOrInt())  return false;
        if (!dw.isSubwordOrInt())  return false;
        if (!dw.isSigned() && sw.isSigned())  return false;
        return dw.bitWidth() > sw.bitWidth();
    }

    /**
     * Is the given type java.lang.Null or an equivalent null-only type?
     */
    public static boolean isNullType(Class<?> type) {
        // Any reference statically typed as Void is guaranteed to be null.
        // Therefore, it can be safely treated as a value of any
        // other type that admits null, i.e., a reference type.
        if (type == Void.class)  return true;
        return false;
    }

    /**
     * True if a method handle can receive a call under a slightly different
     * method type, without moving or reformatting any stack elements.
     *
     * @param call the type of call being made
     * @param recv the type of the method handle receiving the call
     * @return whether the retyping can be done without motion or reformatting
     */
    public static boolean isNullConversion(MethodType call, MethodType recv, boolean keepInterfaces) {
        if (call == recv)  return true;
        int len = call.parameterCount();
        if (len != recv.parameterCount())  return false;
        for (int i = 0; i < len; i++)
            if (!isNullConversion(call.parameterType(i), recv.parameterType(i), keepInterfaces))
                return false;
        return isNullConversion(recv.returnType(), call.returnType(), keepInterfaces);
    }
}
