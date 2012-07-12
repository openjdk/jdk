/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import sun.invoke.util.Wrapper;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import sun.invoke.util.BytecodeDescriptor;
import static java.lang.invoke.MethodHandleStatics.*;

/**
 * A method type represents the arguments and return type accepted and
 * returned by a method handle, or the arguments and return type passed
 * and expected  by a method handle caller.  Method types must be properly
 * matched between a method handle and all its callers,
 * and the JVM's operations enforce this matching at, specifically
 * during calls to {@link MethodHandle#invokeExact MethodHandle.invokeExact}
 * and {@link MethodHandle#invoke MethodHandle.invoke}, and during execution
 * of {@code invokedynamic} instructions.
 * <p>
 * The structure is a return type accompanied by any number of parameter types.
 * The types (primitive, {@code void}, and reference) are represented by {@link Class} objects.
 * (For ease of exposition, we treat {@code void} as if it were a type.
 * In fact, it denotes the absence of a return type.)
 * <p>
 * All instances of {@code MethodType} are immutable.
 * Two instances are completely interchangeable if they compare equal.
 * Equality depends on pairwise correspondence of the return and parameter types and on nothing else.
 * <p>
 * This type can be created only by factory methods.
 * All factory methods may cache values, though caching is not guaranteed.
 * Some factory methods are static, while others are virtual methods which
 * modify precursor method types, e.g., by changing a selected parameter.
 * <p>
 * Factory methods which operate on groups of parameter types
 * are systematically presented in two versions, so that both Java arrays and
 * Java lists can be used to work with groups of parameter types.
 * The query methods {@code parameterArray} and {@code parameterList}
 * also provide a choice between arrays and lists.
 * <p>
 * {@code MethodType} objects are sometimes derived from bytecode instructions
 * such as {@code invokedynamic}, specifically from the type descriptor strings associated
 * with the instructions in a class file's constant pool.
 * <p>
 * Like classes and strings, method types can also be represented directly
 * in a class file's constant pool as constants.
 * A method type may be loaded by an {@code ldc} instruction which refers
 * to a suitable {@code CONSTANT_MethodType} constant pool entry.
 * The entry refers to a {@code CONSTANT_Utf8} spelling for the descriptor string.
 * For more details, see the <a href="package-summary.html#mtcon">package summary</a>.
 * <p>
 * When the JVM materializes a {@code MethodType} from a descriptor string,
 * all classes named in the descriptor must be accessible, and will be loaded.
 * (But the classes need not be initialized, as is the case with a {@code CONSTANT_Class}.)
 * This loading may occur at any time before the {@code MethodType} object is first derived.
 * @author John Rose, JSR 292 EG
 */
public final
class MethodType implements java.io.Serializable {
    private static final long serialVersionUID = 292L;  // {rtype, {ptype...}}

    // The rtype and ptypes fields define the structural identity of the method type:
    private final Class<?>   rtype;
    private final Class<?>[] ptypes;

    // The remaining fields are caches of various sorts:
    private MethodTypeForm form; // erased form, plus cached data about primitives
    private MethodType wrapAlt;  // alternative wrapped/unwrapped version
    private Invokers invokers;   // cache of handy higher-order adapters

    /**
     * Check the given parameters for validity and store them into the final fields.
     */
    private MethodType(Class<?> rtype, Class<?>[] ptypes) {
        checkRtype(rtype);
        checkPtypes(ptypes);
        this.rtype = rtype;
        this.ptypes = ptypes;
    }

    /*trusted*/ MethodTypeForm form() { return form; }
    /*trusted*/ Class<?> rtype() { return rtype; }
    /*trusted*/ Class<?>[] ptypes() { return ptypes; }

    private static void checkRtype(Class<?> rtype) {
        rtype.equals(rtype);  // null check
    }
    private static int checkPtype(Class<?> ptype) {
        ptype.getClass();  //NPE
        if (ptype == void.class)
            throw newIllegalArgumentException("parameter type cannot be void");
        if (ptype == double.class || ptype == long.class)  return 1;
        return 0;
    }
    /** Return number of extra slots (count of long/double args). */
    private static int checkPtypes(Class<?>[] ptypes) {
        int slots = 0;
        for (Class<?> ptype : ptypes) {
            slots += checkPtype(ptype);
        }
        checkSlotCount(ptypes.length + slots);
        return slots;
    }
    private static void checkSlotCount(int count) {
        if ((count & 0xFF) != count)
            throw newIllegalArgumentException("bad parameter count "+count);
    }
    private static IndexOutOfBoundsException newIndexOutOfBoundsException(Object num) {
        if (num instanceof Integer)  num = "bad index: "+num;
        return new IndexOutOfBoundsException(num.toString());
    }

    static final WeakInternSet internTable = new WeakInternSet();

    static final Class<?>[] NO_PTYPES = {};

    /**
     * Finds or creates an instance of the given method type.
     * @param rtype  the return type
     * @param ptypes the parameter types
     * @return a method type with the given components
     * @throws NullPointerException if {@code rtype} or {@code ptypes} or any element of {@code ptypes} is null
     * @throws IllegalArgumentException if any element of {@code ptypes} is {@code void.class}
     */
    public static
    MethodType methodType(Class<?> rtype, Class<?>[] ptypes) {
        return makeImpl(rtype, ptypes, false);
    }

    /**
     * Finds or creates a method type with the given components.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @return a method type with the given components
     * @throws NullPointerException if {@code rtype} or {@code ptypes} or any element of {@code ptypes} is null
     * @throws IllegalArgumentException if any element of {@code ptypes} is {@code void.class}
     */
    public static
    MethodType methodType(Class<?> rtype, List<Class<?>> ptypes) {
        boolean notrust = false;  // random List impl. could return evil ptypes array
        return makeImpl(rtype, listToArray(ptypes), notrust);
    }

    private static Class<?>[] listToArray(List<Class<?>> ptypes) {
        // sanity check the size before the toArray call, since size might be huge
        checkSlotCount(ptypes.size());
        return ptypes.toArray(NO_PTYPES);
    }

    /**
     * Finds or creates a method type with the given components.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * The leading parameter type is prepended to the remaining array.
     * @return a method type with the given components
     * @throws NullPointerException if {@code rtype} or {@code ptype0} or {@code ptypes} or any element of {@code ptypes} is null
     * @throws IllegalArgumentException if {@code ptype0} or {@code ptypes} or any element of {@code ptypes} is {@code void.class}
     */
    public static
    MethodType methodType(Class<?> rtype, Class<?> ptype0, Class<?>... ptypes) {
        Class<?>[] ptypes1 = new Class<?>[1+ptypes.length];
        ptypes1[0] = ptype0;
        System.arraycopy(ptypes, 0, ptypes1, 1, ptypes.length);
        return makeImpl(rtype, ptypes1, true);
    }

    /**
     * Finds or creates a method type with the given components.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * The resulting method has no parameter types.
     * @return a method type with the given return value
     * @throws NullPointerException if {@code rtype} is null
     */
    public static
    MethodType methodType(Class<?> rtype) {
        return makeImpl(rtype, NO_PTYPES, true);
    }

    /**
     * Finds or creates a method type with the given components.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * The resulting method has the single given parameter type.
     * @return a method type with the given return value and parameter type
     * @throws NullPointerException if {@code rtype} or {@code ptype0} is null
     * @throws IllegalArgumentException if {@code ptype0} is {@code void.class}
     */
    public static
    MethodType methodType(Class<?> rtype, Class<?> ptype0) {
        return makeImpl(rtype, new Class<?>[]{ ptype0 }, true);
    }

    /**
     * Finds or creates a method type with the given components.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * The resulting method has the same parameter types as {@code ptypes},
     * and the specified return type.
     * @throws NullPointerException if {@code rtype} or {@code ptypes} is null
     */
    public static
    MethodType methodType(Class<?> rtype, MethodType ptypes) {
        return makeImpl(rtype, ptypes.ptypes, true);
    }

    /**
     * Sole factory method to find or create an interned method type.
     * @param rtype desired return type
     * @param ptypes desired parameter types
     * @param trusted whether the ptypes can be used without cloning
     * @return the unique method type of the desired structure
     */
    /*trusted*/ static
    MethodType makeImpl(Class<?> rtype, Class<?>[] ptypes, boolean trusted) {
        if (ptypes.length == 0) {
            ptypes = NO_PTYPES; trusted = true;
        }
        MethodType mt1 = new MethodType(rtype, ptypes);
        MethodType mt0;
        mt0 = internTable.get(mt1);
        if (mt0 != null)
            return mt0;
        if (!trusted)
            // defensively copy the array passed in by the user
            mt1 = new MethodType(rtype, ptypes.clone());
        // promote the object to the Real Thing, and reprobe
        MethodTypeForm form = MethodTypeForm.findForm(mt1);
        mt1.form = form;
        if (form.erasedType == mt1) {
            // This is a principal (erased) type; show it to the JVM.
            MethodHandleNatives.init(mt1);
        }
        return internTable.add(mt1);
    }
    private static final MethodType[] objectOnlyTypes = new MethodType[20];

    /**
     * Finds or creates a method type whose components are {@code Object} with an optional trailing {@code Object[]} array.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * All parameters and the return type will be {@code Object},
     * except the final array parameter if any, which will be {@code Object[]}.
     * @param objectArgCount number of parameters (excluding the final array parameter if any)
     * @param finalArray whether there will be a trailing array parameter, of type {@code Object[]}
     * @return a generally applicable method type, for all calls of the given fixed argument count and a collected array of further arguments
     * @throws IllegalArgumentException if {@code objectArgCount} is negative or greater than 255 (or 254, if {@code finalArray} is true)
     * @see #genericMethodType(int)
     */
    public static
    MethodType genericMethodType(int objectArgCount, boolean finalArray) {
        MethodType mt;
        checkSlotCount(objectArgCount);
        int ivarargs = (!finalArray ? 0 : 1);
        int ootIndex = objectArgCount*2 + ivarargs;
        if (ootIndex < objectOnlyTypes.length) {
            mt = objectOnlyTypes[ootIndex];
            if (mt != null)  return mt;
        }
        Class<?>[] ptypes = new Class<?>[objectArgCount + ivarargs];
        Arrays.fill(ptypes, Object.class);
        if (ivarargs != 0)  ptypes[objectArgCount] = Object[].class;
        mt = makeImpl(Object.class, ptypes, true);
        if (ootIndex < objectOnlyTypes.length) {
            objectOnlyTypes[ootIndex] = mt;     // cache it here also!
        }
        return mt;
    }

    /**
     * Finds or creates a method type whose components are all {@code Object}.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * All parameters and the return type will be Object.
     * @param objectArgCount number of parameters
     * @return a generally applicable method type, for all calls of the given argument count
     * @throws IllegalArgumentException if {@code objectArgCount} is negative or greater than 255
     * @see #genericMethodType(int, boolean)
     */
    public static
    MethodType genericMethodType(int objectArgCount) {
        return genericMethodType(objectArgCount, false);
    }

    /**
     * Finds or creates a method type with a single different parameter type.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param num    the index (zero-based) of the parameter type to change
     * @param nptype a new parameter type to replace the old one with
     * @return the same type, except with the selected parameter changed
     * @throws IndexOutOfBoundsException if {@code num} is not a valid index into {@code parameterArray()}
     * @throws IllegalArgumentException if {@code nptype} is {@code void.class}
     * @throws NullPointerException if {@code nptype} is null
     */
    public MethodType changeParameterType(int num, Class<?> nptype) {
        if (parameterType(num) == nptype)  return this;
        checkPtype(nptype);
        Class<?>[] nptypes = ptypes.clone();
        nptypes[num] = nptype;
        return makeImpl(rtype, nptypes, true);
    }

    /**
     * Finds or creates a method type with additional parameter types.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param num    the position (zero-based) of the inserted parameter type(s)
     * @param ptypesToInsert zero or more new parameter types to insert into the parameter list
     * @return the same type, except with the selected parameter(s) inserted
     * @throws IndexOutOfBoundsException if {@code num} is negative or greater than {@code parameterCount()}
     * @throws IllegalArgumentException if any element of {@code ptypesToInsert} is {@code void.class}
     *                                  or if the resulting method type would have more than 255 parameter slots
     * @throws NullPointerException if {@code ptypesToInsert} or any of its elements is null
     */
    public MethodType insertParameterTypes(int num, Class<?>... ptypesToInsert) {
        int len = ptypes.length;
        if (num < 0 || num > len)
            throw newIndexOutOfBoundsException(num);
        int ins = checkPtypes(ptypesToInsert);
        checkSlotCount(parameterSlotCount() + ptypesToInsert.length + ins);
        int ilen = ptypesToInsert.length;
        if (ilen == 0)  return this;
        Class<?>[] nptypes = Arrays.copyOfRange(ptypes, 0, len+ilen);
        System.arraycopy(nptypes, num, nptypes, num+ilen, len-num);
        System.arraycopy(ptypesToInsert, 0, nptypes, num, ilen);
        return makeImpl(rtype, nptypes, true);
    }

    /**
     * Finds or creates a method type with additional parameter types.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param ptypesToInsert zero or more new parameter types to insert after the end of the parameter list
     * @return the same type, except with the selected parameter(s) appended
     * @throws IllegalArgumentException if any element of {@code ptypesToInsert} is {@code void.class}
     *                                  or if the resulting method type would have more than 255 parameter slots
     * @throws NullPointerException if {@code ptypesToInsert} or any of its elements is null
     */
    public MethodType appendParameterTypes(Class<?>... ptypesToInsert) {
        return insertParameterTypes(parameterCount(), ptypesToInsert);
    }

    /**
     * Finds or creates a method type with additional parameter types.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param num    the position (zero-based) of the inserted parameter type(s)
     * @param ptypesToInsert zero or more new parameter types to insert into the parameter list
     * @return the same type, except with the selected parameter(s) inserted
     * @throws IndexOutOfBoundsException if {@code num} is negative or greater than {@code parameterCount()}
     * @throws IllegalArgumentException if any element of {@code ptypesToInsert} is {@code void.class}
     *                                  or if the resulting method type would have more than 255 parameter slots
     * @throws NullPointerException if {@code ptypesToInsert} or any of its elements is null
     */
    public MethodType insertParameterTypes(int num, List<Class<?>> ptypesToInsert) {
        return insertParameterTypes(num, listToArray(ptypesToInsert));
    }

    /**
     * Finds or creates a method type with additional parameter types.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param ptypesToInsert zero or more new parameter types to insert after the end of the parameter list
     * @return the same type, except with the selected parameter(s) appended
     * @throws IllegalArgumentException if any element of {@code ptypesToInsert} is {@code void.class}
     *                                  or if the resulting method type would have more than 255 parameter slots
     * @throws NullPointerException if {@code ptypesToInsert} or any of its elements is null
     */
    public MethodType appendParameterTypes(List<Class<?>> ptypesToInsert) {
        return insertParameterTypes(parameterCount(), ptypesToInsert);
    }

    /**
     * Finds or creates a method type with some parameter types omitted.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param start  the index (zero-based) of the first parameter type to remove
     * @param end    the index (greater than {@code start}) of the first parameter type after not to remove
     * @return the same type, except with the selected parameter(s) removed
     * @throws IndexOutOfBoundsException if {@code start} is negative or greater than {@code parameterCount()}
     *                                  or if {@code end} is negative or greater than {@code parameterCount()}
     *                                  or if {@code start} is greater than {@code end}
     */
    public MethodType dropParameterTypes(int start, int end) {
        int len = ptypes.length;
        if (!(0 <= start && start <= end && end <= len))
            throw newIndexOutOfBoundsException("start="+start+" end="+end);
        if (start == end)  return this;
        Class<?>[] nptypes;
        if (start == 0) {
            if (end == len) {
                // drop all parameters
                nptypes = NO_PTYPES;
            } else {
                // drop initial parameter(s)
                nptypes = Arrays.copyOfRange(ptypes, end, len);
            }
        } else {
            if (end == len) {
                // drop trailing parameter(s)
                nptypes = Arrays.copyOfRange(ptypes, 0, start);
            } else {
                int tail = len - end;
                nptypes = Arrays.copyOfRange(ptypes, 0, start + tail);
                System.arraycopy(ptypes, end, nptypes, start, tail);
            }
        }
        return makeImpl(rtype, nptypes, true);
    }

    /**
     * Finds or creates a method type with a different return type.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * @param nrtype a return parameter type to replace the old one with
     * @return the same type, except with the return type change
     * @throws NullPointerException if {@code nrtype} is null
     */
    public MethodType changeReturnType(Class<?> nrtype) {
        if (returnType() == nrtype)  return this;
        return makeImpl(nrtype, ptypes, true);
    }

    /**
     * Reports if this type contains a primitive argument or return value.
     * The return type {@code void} counts as a primitive.
     * @return true if any of the types are primitives
     */
    public boolean hasPrimitives() {
        return form.hasPrimitives();
    }

    /**
     * Reports if this type contains a wrapper argument or return value.
     * Wrappers are types which box primitive values, such as {@link Integer}.
     * The reference type {@code java.lang.Void} counts as a wrapper,
     * if it occurs as a return type.
     * @return true if any of the types are wrappers
     */
    public boolean hasWrappers() {
        return unwrap() != this;
    }

    /**
     * Erases all reference types to {@code Object}.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * All primitive types (including {@code void}) will remain unchanged.
     * @return a version of the original type with all reference types replaced
     */
    public MethodType erase() {
        return form.erasedType();
    }

    /**
     * Converts all types, both reference and primitive, to {@code Object}.
     * Convenience method for {@link #genericMethodType(int) genericMethodType}.
     * The expression {@code type.wrap().erase()} produces the same value
     * as {@code type.generic()}.
     * @return a version of the original type with all types replaced
     */
    public MethodType generic() {
        return genericMethodType(parameterCount());
    }

    /**
     * Converts all primitive types to their corresponding wrapper types.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * All reference types (including wrapper types) will remain unchanged.
     * A {@code void} return type is changed to the type {@code java.lang.Void}.
     * The expression {@code type.wrap().erase()} produces the same value
     * as {@code type.generic()}.
     * @return a version of the original type with all primitive types replaced
     */
    public MethodType wrap() {
        return hasPrimitives() ? wrapWithPrims(this) : this;
    }

    /**
     * Converts all wrapper types to their corresponding primitive types.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * All primitive types (including {@code void}) will remain unchanged.
     * A return type of {@code java.lang.Void} is changed to {@code void}.
     * @return a version of the original type with all wrapper types replaced
     */
    public MethodType unwrap() {
        MethodType noprims = !hasPrimitives() ? this : wrapWithPrims(this);
        return unwrapWithNoPrims(noprims);
    }

    private static MethodType wrapWithPrims(MethodType pt) {
        assert(pt.hasPrimitives());
        MethodType wt = pt.wrapAlt;
        if (wt == null) {
            // fill in lazily
            wt = MethodTypeForm.canonicalize(pt, MethodTypeForm.WRAP, MethodTypeForm.WRAP);
            assert(wt != null);
            pt.wrapAlt = wt;
        }
        return wt;
    }

    private static MethodType unwrapWithNoPrims(MethodType wt) {
        assert(!wt.hasPrimitives());
        MethodType uwt = wt.wrapAlt;
        if (uwt == null) {
            // fill in lazily
            uwt = MethodTypeForm.canonicalize(wt, MethodTypeForm.UNWRAP, MethodTypeForm.UNWRAP);
            if (uwt == null)
                uwt = wt;    // type has no wrappers or prims at all
            wt.wrapAlt = uwt;
        }
        return uwt;
    }

    /**
     * Returns the parameter type at the specified index, within this method type.
     * @param num the index (zero-based) of the desired parameter type
     * @return the selected parameter type
     * @throws IndexOutOfBoundsException if {@code num} is not a valid index into {@code parameterArray()}
     */
    public Class<?> parameterType(int num) {
        return ptypes[num];
    }
    /**
     * Returns the number of parameter types in this method type.
     * @return the number of parameter types
     */
    public int parameterCount() {
        return ptypes.length;
    }
    /**
     * Returns the return type of this method type.
     * @return the return type
     */
    public Class<?> returnType() {
        return rtype;
    }

    /**
     * Presents the parameter types as a list (a convenience method).
     * The list will be immutable.
     * @return the parameter types (as an immutable list)
     */
    public List<Class<?>> parameterList() {
        return Collections.unmodifiableList(Arrays.asList(ptypes));
    }

    /**
     * Presents the parameter types as an array (a convenience method).
     * Changes to the array will not result in changes to the type.
     * @return the parameter types (as a fresh copy if necessary)
     */
    public Class<?>[] parameterArray() {
        return ptypes.clone();
    }

    /**
     * Compares the specified object with this type for equality.
     * That is, it returns <tt>true</tt> if and only if the specified object
     * is also a method type with exactly the same parameters and return type.
     * @param x object to compare
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object x) {
        return this == x || x instanceof MethodType && equals((MethodType)x);
    }

    private boolean equals(MethodType that) {
        return this.rtype == that.rtype
            && Arrays.equals(this.ptypes, that.ptypes);
    }

    /**
     * Returns the hash code value for this method type.
     * It is defined to be the same as the hashcode of a List
     * whose elements are the return type followed by the
     * parameter types.
     * @return the hash code value for this method type
     * @see Object#hashCode()
     * @see #equals(Object)
     * @see List#hashCode()
     */
    @Override
    public int hashCode() {
      int hashCode = 31 + rtype.hashCode();
      for (Class<?> ptype : ptypes)
          hashCode = 31*hashCode + ptype.hashCode();
      return hashCode;
    }

    /**
     * Returns a string representation of the method type,
     * of the form {@code "(PT0,PT1...)RT"}.
     * The string representation of a method type is a
     * parenthesis enclosed, comma separated list of type names,
     * followed immediately by the return type.
     * <p>
     * Each type is represented by its
     * {@link java.lang.Class#getSimpleName simple name}.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < ptypes.length; i++) {
            if (i > 0)  sb.append(",");
            sb.append(ptypes[i].getSimpleName());
        }
        sb.append(")");
        sb.append(rtype.getSimpleName());
        return sb.toString();
    }


    /*non-public*/
    boolean isConvertibleTo(MethodType newType) {
        if (!canConvert(returnType(), newType.returnType()))
            return false;
        int argc = parameterCount();
        if (argc != newType.parameterCount())
            return false;
        for (int i = 0; i < argc; i++) {
            if (!canConvert(newType.parameterType(i), parameterType(i)))
                return false;
        }
        return true;
    }
    /*non-public*/
    static boolean canConvert(Class<?> src, Class<?> dst) {
        // short-circuit a few cases:
        if (src == dst || dst == Object.class)  return true;
        // the remainder of this logic is documented in MethodHandle.asType
        if (src.isPrimitive()) {
            // can force void to an explicit null, a la reflect.Method.invoke
            // can also force void to a primitive zero, by analogy
            if (src == void.class)  return true;  //or !dst.isPrimitive()?
            Wrapper sw = Wrapper.forPrimitiveType(src);
            if (dst.isPrimitive()) {
                // P->P must widen
                return Wrapper.forPrimitiveType(dst).isConvertibleFrom(sw);
            } else {
                // P->R must box and widen
                return dst.isAssignableFrom(sw.wrapperType());
            }
        } else if (dst.isPrimitive()) {
            // any value can be dropped
            if (dst == void.class)  return true;
            Wrapper dw = Wrapper.forPrimitiveType(dst);
            // R->P must be able to unbox (from a dynamically chosen type) and widen
            // For example:
            //   Byte/Number/Comparable/Object -> dw:Byte -> byte.
            //   Character/Comparable/Object -> dw:Character -> char
            //   Boolean/Comparable/Object -> dw:Boolean -> boolean
            // This means that dw must be cast-compatible with src.
            if (src.isAssignableFrom(dw.wrapperType())) {
                return true;
            }
            // The above does not work if the source reference is strongly typed
            // to a wrapper whose primitive must be widened.  For example:
            //   Byte -> unbox:byte -> short/int/long/float/double
            //   Character -> unbox:char -> int/long/float/double
            if (Wrapper.isWrapperType(src) &&
                dw.isConvertibleFrom(Wrapper.forWrapperType(src))) {
                // can unbox from src and then widen to dst
                return true;
            }
            // We have already covered cases which arise due to runtime unboxing
            // of a reference type which covers several wrapper types:
            //   Object -> cast:Integer -> unbox:int -> long/float/double
            //   Serializable -> cast:Byte -> unbox:byte -> byte/short/int/long/float/double
            // An marginal case is Number -> dw:Character -> char, which would be OK if there were a
            // subclass of Number which wraps a value that can convert to char.
            // Since there is none, we don't need an extra check here to cover char or boolean.
            return false;
        } else {
            // R->R always works, since null is always valid dynamically
            return true;
        }
    }

    /// Queries which have to do with the bytecode architecture

    /** Reports the number of JVM stack slots required to invoke a method
     * of this type.  Note that (for historical reasons) the JVM requires
     * a second stack slot to pass long and double arguments.
     * So this method returns {@link #parameterCount() parameterCount} plus the
     * number of long and double parameters (if any).
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * @return the number of JVM stack slots for this type's parameters
     */
    /*non-public*/ int parameterSlotCount() {
        return form.parameterSlotCount();
    }

    /*non-public*/ Invokers invokers() {
        Invokers inv = invokers;
        if (inv != null)  return inv;
        invokers = inv = new Invokers(this);
        return inv;
    }

    /** Reports the number of JVM stack slots which carry all parameters including and after
     * the given position, which must be in the range of 0 to
     * {@code parameterCount} inclusive.  Successive parameters are
     * more shallowly stacked, and parameters are indexed in the bytecodes
     * according to their trailing edge.  Thus, to obtain the depth
     * in the outgoing call stack of parameter {@code N}, obtain
     * the {@code parameterSlotDepth} of its trailing edge
     * at position {@code N+1}.
     * <p>
     * Parameters of type {@code long} and {@code double} occupy
     * two stack slots (for historical reasons) and all others occupy one.
     * Therefore, the number returned is the number of arguments
     * <em>including</em> and <em>after</em> the given parameter,
     * <em>plus</em> the number of long or double arguments
     * at or after after the argument for the given parameter.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * @param num an index (zero-based, inclusive) within the parameter types
     * @return the index of the (shallowest) JVM stack slot transmitting the
     *         given parameter
     * @throws IllegalArgumentException if {@code num} is negative or greater than {@code parameterCount()}
     */
    /*non-public*/ int parameterSlotDepth(int num) {
        if (num < 0 || num > ptypes.length)
            parameterType(num);  // force a range check
        return form.parameterToArgSlot(num-1);
    }

    /** Reports the number of JVM stack slots required to receive a return value
     * from a method of this type.
     * If the {@link #returnType() return type} is void, it will be zero,
     * else if the return type is long or double, it will be two, else one.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * @return the number of JVM stack slots (0, 1, or 2) for this type's return value
     * Will be removed for PFD.
     */
    /*non-public*/ int returnSlotCount() {
        return form.returnSlotCount();
    }

    /**
     * Finds or creates an instance of a method type, given the spelling of its bytecode descriptor.
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[]) methodType}.
     * Any class or interface name embedded in the descriptor string
     * will be resolved by calling {@link ClassLoader#loadClass(java.lang.String)}
     * on the given loader (or if it is null, on the system class loader).
     * <p>
     * Note that it is possible to encounter method types which cannot be
     * constructed by this method, because their component types are
     * not all reachable from a common class loader.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and {@code invokedynamic}.
     * @param descriptor a bytecode-level type descriptor string "(T...)T"
     * @param loader the class loader in which to look up the types
     * @return a method type matching the bytecode-level type descriptor
     * @throws NullPointerException if the string is null
     * @throws IllegalArgumentException if the string is not well-formed
     * @throws TypeNotPresentException if a named type cannot be found
     */
    public static MethodType fromMethodDescriptorString(String descriptor, ClassLoader loader)
        throws IllegalArgumentException, TypeNotPresentException
    {
        if (!descriptor.startsWith("(") ||  // also generates NPE if needed
            descriptor.indexOf(')') < 0 ||
            descriptor.indexOf('.') >= 0)
            throw new IllegalArgumentException("not a method descriptor: "+descriptor);
        List<Class<?>> types = BytecodeDescriptor.parseMethod(descriptor, loader);
        Class<?> rtype = types.remove(types.size() - 1);
        checkSlotCount(types.size());
        Class<?>[] ptypes = listToArray(types);
        return makeImpl(rtype, ptypes, true);
    }

    /**
     * Produces a bytecode descriptor representation of the method type.
     * <p>
     * Note that this is not a strict inverse of {@link #fromMethodDescriptorString fromMethodDescriptorString}.
     * Two distinct classes which share a common name but have different class loaders
     * will appear identical when viewed within descriptor strings.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and {@code invokedynamic}.
     * {@link #fromMethodDescriptorString(java.lang.String, java.lang.ClassLoader) fromMethodDescriptorString},
     * because the latter requires a suitable class loader argument.
     * @return the bytecode type descriptor representation
     */
    public String toMethodDescriptorString() {
        return BytecodeDescriptor.unparse(this);
    }

    /// Serialization.

    /**
     * There are no serializable fields for {@code MethodType}.
     */
    private static final java.io.ObjectStreamField[] serialPersistentFields = { };

    /**
     * Save the {@code MethodType} instance to a stream.
     *
     * @serialData
     * For portability, the serialized format does not refer to named fields.
     * Instead, the return type and parameter type arrays are written directly
     * from the {@code writeObject} method, using two calls to {@code s.writeObject}
     * as follows:
     * <blockquote><pre>
s.writeObject(this.returnType());
s.writeObject(this.parameterArray());
     * </pre></blockquote>
     * <p>
     * The deserialized field values are checked as if they were
     * provided to the factory method {@link #methodType(Class,Class[]) methodType}.
     * For example, null values, or {@code void} parameter types,
     * will lead to exceptions during deserialization.
     * @param the stream to write the object to
     */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();  // requires serialPersistentFields to be an empty array
        s.writeObject(returnType());
        s.writeObject(parameterArray());
    }

    /**
     * Reconstitute the {@code MethodType} instance from a stream (that is,
     * deserialize it).
     * This instance is a scratch object with bogus final fields.
     * It provides the parameters to the factory method called by
     * {@link #readResolve readResolve}.
     * After that call it is discarded.
     * @param the stream to read the object from
     * @see #MethodType()
     * @see #readResolve
     * @see #writeObject
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();  // requires serialPersistentFields to be an empty array

        Class<?>   returnType     = (Class<?>)   s.readObject();
        Class<?>[] parameterArray = (Class<?>[]) s.readObject();

        // Probably this object will never escape, but let's check
        // the field values now, just to be sure.
        checkRtype(returnType);
        checkPtypes(parameterArray);

        parameterArray = parameterArray.clone();  // make sure it is unshared
        MethodType_init(returnType, parameterArray);
    }

    /**
     * For serialization only.
     * Sets the final fields to null, pending {@code Unsafe.putObject}.
     */
    private MethodType() {
        this.rtype = null;
        this.ptypes = null;
    }
    private void MethodType_init(Class<?> rtype, Class<?>[] ptypes) {
        // In order to communicate these values to readResolve, we must
        // store them into the implementation-specific final fields.
        checkRtype(rtype);
        checkPtypes(ptypes);
        unsafe.putObject(this, rtypeOffset, rtype);
        unsafe.putObject(this, ptypesOffset, ptypes);
    }

    // Support for resetting final fields while deserializing
    private static final sun.misc.Unsafe unsafe = sun.misc.Unsafe.getUnsafe();
    private static final long rtypeOffset, ptypesOffset;
    static {
        try {
            rtypeOffset = unsafe.objectFieldOffset
                (MethodType.class.getDeclaredField("rtype"));
            ptypesOffset = unsafe.objectFieldOffset
                (MethodType.class.getDeclaredField("ptypes"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * Resolves and initializes a {@code MethodType} object
     * after serialization.
     * @return the fully initialized {@code MethodType} object
     */
    private Object readResolve() {
        // Do not use a trusted path for deserialization:
        //return makeImpl(rtype, ptypes, true);
        // Verify all operands, and make sure ptypes is unshared:
        return methodType(rtype, ptypes);
    }

    /**
     * Weak intern set based on implementation of the <tt>HashSet</tt> and
     * <tt>WeakHashMap</tt>, with <em>weak values</em>.  Note: <tt>null</tt>
     * values will yield <tt>NullPointerException</tt>
     * Refer to implementation of WeakInternSet for details.
     *
     * @see         java.util.HashMap
     * @see         java.util.HashSet
     * @see         java.util.WeakHashMap
     * @see         java.lang.ref.WeakReference
     */
    private static class WeakInternSet {
        // The default initial capacity -- MUST be a power of two.
        private static final int DEFAULT_INITIAL_CAPACITY = 16;

        // The maximum capacity, used if a higher value is implicitly specified
        // by either of the constructors with arguments.
        // MUST be a power of two <= 1<<30.
        private static final int MAXIMUM_CAPACITY = 1 << 30;

        // The load factor used when none specified in constructor.
        private static final float DEFAULT_LOAD_FACTOR = 0.75f;

        // The table, resized as necessary. Length MUST Always be a power of two.
        private Entry[] table;

        // The number of entries contained in this set.
        private int size;

        // The next size value at which to resize (capacity * load factor).
        private int threshold;

        // The load factor for the hash table.
        private final float loadFactor;

        // Reference queue for cleared WeakEntries
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

        private Entry[] newTable(int n) {
            return new Entry[n];
        }

        /**
         * Constructs a new, empty <tt>WeakInternSet</tt> with the default initial
         * capacity (16) and load factor (0.75).
         */
        WeakInternSet() {
            this.loadFactor = DEFAULT_LOAD_FACTOR;
            threshold = DEFAULT_INITIAL_CAPACITY;
            table = newTable(DEFAULT_INITIAL_CAPACITY);
        }

        /**
         * Applies a supplemental hash function to a given hashCode, which
         * defends against poor quality hash functions.  This is critical
         * because hashing uses power-of-two length hash tables, that
         * otherwise encounter collisions for hashCodes that do not differ
         * in lower bits.
         * @param h preliminary hash code value
         * @return supplemental hash code value
         */
        private static int hash(int h) {
            // This function ensures that hashCodes that differ only by
            // constant multiples at each bit position have a bounded
            // number of collisions (approximately 8 at default load factor).
            h ^= (h >>> 20) ^ (h >>> 12);
            return h ^ (h >>> 7) ^ (h >>> 4);
        }

        /**
         * Checks for equality of non-null reference x and possibly-null y.  By
         * default uses Object.equals.
         * @param x first object to compare
         * @param y second object to compare
         * @return <tt>true</tt> if objects are equal
         */
        private static boolean eq(Object x, Object y) {
            return x == y || x.equals(y);
        }

        /**
         * Returns index for hash code h.
         * @param h      raw hash code
         * @param length length of table (power of 2)
         * @return index in table
         */
        private static int indexFor(int h, int length) {
            return h & (length-1);
        }

        /**
         * Expunges stale entries from the table.
         */
        private void expungeStaleEntries() {
            for (Object x; (x = queue.poll()) != null; ) {
                synchronized (queue) {
                    Entry entry = (Entry) x;
                    int i = indexFor(entry.hash, table.length);
                    Entry prev = table[i];
                    Entry p = prev;
                    while (p != null) {
                        Entry next = p.next;
                        if (p == entry) {
                            if (prev == entry)
                                table[i] = next;
                            else
                                prev.next = next;
                            entry.next = null;
                            size--;
                            break;
                        }
                        prev = p;
                        p = next;
                    }
                }
            }
        }

        /**
         * Returns the table after first expunging stale entries.
         * @return an expunged hash table
         */
        private Entry[] getTable() {
            expungeStaleEntries();
            return table;
        }

        /**
         * Returns the entry to which the specified value is mapped,
         * or {@code null} if this set contains no entry for the value.
         *
         * <p>More formally, if this set contains an entry for value
         * {@code entry} to a value {@code value} such that
         * {@code entry.equals(value)}, then this method returns {@code entry};
         * otherwise it returns {@code null}.
         *
         * @param value value to search for in set
         * @return interned value if in set, otherwise <tt>null</tt>
         */
        synchronized MethodType get(MethodType value) {
            int h = hash(value.hashCode());
            Entry[] tab = getTable();
            int index = indexFor(h, tab.length);
            Entry e = tab[index];
            MethodType g;
            while (e != null) {
                if (e.hash == h && eq(value, g = e.get()))
                    return g;
                e = e.next;
            }
            return null;
        }

        /**
         * Attempts to add the specified value to the set and returns same value.
         * If the set previously contained an entry for this value, the old
         * value is left untouched and returned as the result.
         *
         * @param value value to be added
         * @return the previous entry associated with <tt>value</tt>, or
         *         <tt>value</tt> if there was no previous entry found
         */
        synchronized MethodType add(MethodType value) {
            int h = hash(value.hashCode());
            Entry[] tab = getTable();
            int i = indexFor(h, tab.length);
            MethodType g;
            for (Entry e = tab[i]; e != null; e = e.next) {
                if (h == e.hash && eq(value, g = e.get())) {
                    return g;
                }
            }
            Entry e = tab[i];
            tab[i] = new Entry(value, queue, h, e);
            if (++size >= threshold)
                resize(tab.length * 2);
            return value;
        }

        /**
         * Rehashes the contents of this set into a new array with a
         * larger capacity.  This method is called automatically when the
         * number of keys in this set reaches its threshold.
         *
         * If current capacity is MAXIMUM_CAPACITY, this method does not
         * resize the set, but sets threshold to Integer.MAX_VALUE.
         * This has the effect of preventing future calls.
         *
         * @param newCapacity the new capacity, MUST be a power of two;
         *        must be greater than current capacity unless current
         *        capacity is MAXIMUM_CAPACITY (in which case value
         *        is irrelevant)
         */
        private void resize(int newCapacity) {
            Entry[] oldTable = getTable();
            int oldCapacity = oldTable.length;
            if (oldCapacity == MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return;
            }

            Entry[] newTable = newTable(newCapacity);
            transfer(oldTable, newTable);
            table = newTable;

            /*
             * If ignoring null elements and processing ref queue caused massive
             * shrinkage, then restore old table.  This should be rare, but avoids
             * unbounded expansion of garbage-filled tables.
             */
            if (size >= threshold / 2) {
                threshold = (int)(newCapacity * loadFactor);
            } else {
                expungeStaleEntries();
                transfer(newTable, oldTable);
                table = oldTable;
            }
        }

        /**
         * Transfers all entries from src to dest tables
         * @param src  original table
         * @param dest new table
         */
        private void transfer(Entry[] src, Entry[] dest) {
            for (int j = 0; j < src.length; ++j) {
                Entry e = src[j];
                src[j] = null;
                while (e != null) {
                    Entry next = e.next;
                    MethodType key = e.get();
                    if (key == null) {
                        e.next = null;  // Help GC
                        size--;
                    } else {
                        int i = indexFor(e.hash, dest.length);
                        e.next = dest[i];
                        dest[i] = e;
                    }
                    e = next;
                }
            }
        }

        /**
         * The entries in this hash table extend WeakReference, using its main ref
         * field as the key.
         */
        private static class Entry extends WeakReference<MethodType> {
            final int hash;
            Entry next;

            /**
             * Creates new entry.
             */
            Entry(MethodType key,
                  ReferenceQueue<Object> queue,
                  int hash, Entry next) {
                super(key, queue);
                this.hash  = hash;
                this.next  = next;
            }
        }
    }
}
