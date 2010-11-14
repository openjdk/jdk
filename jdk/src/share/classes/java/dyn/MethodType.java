/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import sun.dyn.Access;
import sun.dyn.Invokers;
import sun.dyn.MethodTypeImpl;
import sun.dyn.util.BytecodeDescriptor;
import static sun.dyn.MemberName.newIllegalArgumentException;

/**
 * A method type represents the arguments and return type accepted and
 * returned by a method handle, or the arguments and return type passed
 * and expected  by a method handle caller.  Method types must be properly
 * matched between a method handle and all its callers,
 * and the JVM's operations enforce this matching at, specifically
 * during calls to {@link MethodHandle#invokeExact}
 * and {@link MethodHandle#invokeGeneric}, and during execution
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
 * <p>
 * {@code MethodType} objects are sometimes derived from bytecode instructions
 * such as {@code invokedynamic}, specifically from the type descriptor strings associated
 * with the instructions in a class file's constant pool.
 * When this occurs, any classes named in the descriptor strings must be loaded.
 * (But they need not be initialized.)
 * This loading may occur at any time before the {@code MethodType} object is first derived.
 * <p>
 * Like classes and strings, method types can be represented directly
 * in a class file's constant pool as constants to be loaded by {@code ldc} bytecodes.
 * Loading such a constant causes its component classes to be loaded as necessary.
 * @author John Rose, JSR 292 EG
 */
public final
class MethodType implements java.lang.reflect.Type {
    private final Class<?>   rtype;
    private final Class<?>[] ptypes;
    private MethodTypeForm form; // erased form, plus cached data about primitives
    private MethodType wrapAlt;  // alternative wrapped/unwrapped version
    private Invokers invokers;   // cache of handy higher-order adapters

    private static final Access IMPL_TOKEN = Access.getToken();

    // share a cache with a friend in this package
    Invokers getInvokers() { return invokers; }
    void setInvokers(Invokers inv) { invokers = inv; }

    static {
        // This hack allows the implementation package special access to
        // the internals of MethodType.  In particular, the MTImpl has all sorts
        // of cached information useful to the implementation code.
        MethodTypeImpl.setMethodTypeFriend(IMPL_TOKEN, new MethodTypeImpl.MethodTypeFriend() {
            public Class<?>[] ptypes(MethodType mt)        { return mt.ptypes; }
            public MethodTypeImpl form(MethodType mt)      { return mt.form; }
            public void setForm(MethodType mt, MethodTypeImpl form) {
                assert(mt.form == null);
                mt.form = (MethodTypeForm) form;
            }
            public MethodType makeImpl(Class<?> rtype, Class<?>[] ptypes, boolean trusted) {
                return MethodType.makeImpl(rtype, ptypes, trusted);
            }
            public MethodTypeImpl newMethodTypeForm(MethodType mt) {
                return new MethodTypeForm(mt);
            }
            public Invokers getInvokers(MethodType mt)    { return mt.invokers; }
            public void setInvokers(MethodType mt, Invokers inv) { mt.invokers = inv; }
        });
    }

    private MethodType(Class<?> rtype, Class<?>[] ptypes) {
        checkRtype(rtype);
        checkPtypes(ptypes);
        this.rtype = rtype;
        this.ptypes = ptypes;
    }

    private void checkRtype(Class<?> rtype) {
        rtype.equals(rtype);  // null check
    }
    private void checkPtypes(Class<?>[] ptypes) {
        for (Class<?> ptype : ptypes) {
            ptype.equals(ptype);  // null check
            if (ptype == void.class)
                throw newIllegalArgumentException("void parameter: "+this);
        }
    }

    static final HashMap<MethodType,MethodType> internTable
            = new HashMap<MethodType, MethodType>();

    static final Class<?>[] NO_PTYPES = {};

    /** Find or create an instance of the given method type.
     * @param rtype  the return type
     * @param ptypes the parameter types
     * @return a method type with the given parts
     * @throws NullPointerException if rtype or any ptype is null
     * @throws IllegalArgumentException if any of the ptypes is void
     */
    public static
    MethodType methodType(Class<?> rtype, Class<?>[] ptypes) {
        return makeImpl(rtype, ptypes, false);
    }
    @Deprecated public static
    MethodType make(Class<?> rtype, Class<?>[] ptypes) {
        return methodType(rtype, ptypes);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}. */
    public static
    MethodType methodType(Class<?> rtype, List<? extends Class<?>> ptypes) {
        boolean notrust = false;  // random List impl. could return evil ptypes array
        return makeImpl(rtype, ptypes.toArray(NO_PTYPES), notrust);
    }
    @Deprecated public static
    MethodType make(Class<?> rtype, List<? extends Class<?>> ptypes) {
        return methodType(rtype, ptypes);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     *  The leading parameter type is prepended to the remaining array.
     */
    public static
    MethodType methodType(Class<?> rtype, Class<?> ptype0, Class<?>... ptypes) {
        Class<?>[] ptypes1 = new Class<?>[1+ptypes.length];
        ptypes1[0] = ptype0;
        System.arraycopy(ptypes, 0, ptypes1, 1, ptypes.length);
        return makeImpl(rtype, ptypes1, true);
    }
    @Deprecated public static
    MethodType make(Class<?> rtype, Class<?> ptype0, Class<?>... ptypes) {
        return methodType(rtype, ptype0, ptypes);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     *  The resulting method has no parameter types.
     */
    public static
    MethodType methodType(Class<?> rtype) {
        return makeImpl(rtype, NO_PTYPES, true);
    }
    @Deprecated public static
    MethodType make(Class<?> rtype) {
        return methodType(rtype);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     *  The resulting method has the single given parameter type.
     */
    public static
    MethodType methodType(Class<?> rtype, Class<?> ptype0) {
        return makeImpl(rtype, new Class<?>[]{ ptype0 }, true);
    }
    @Deprecated public static
    MethodType make(Class<?> rtype, Class<?> ptype0) {
        return methodType(rtype, ptype0);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     *  The resulting method has the same parameter types as {@code ptypes},
     *  and the specified return type.
     */
    public static
    MethodType methodType(Class<?> rtype, MethodType ptypes) {
        return makeImpl(rtype, ptypes.ptypes, true);
    }
    @Deprecated public static
    MethodType make(Class<?> rtype, MethodType ptypes) {
        return methodType(rtype, ptypes);
    }

    /**
     * Sole factory method to find or create an interned method type.
     * @param rtype desired return type
     * @param ptypes desired parameter types
     * @param trusted whether the ptypes can be used without cloning
     * @return the unique method type of the desired structure
     */
    private static
    MethodType makeImpl(Class<?> rtype, Class<?>[] ptypes, boolean trusted) {
        if (ptypes == null || ptypes.length == 0) {
            ptypes = NO_PTYPES; trusted = true;
        }
        MethodType mt1 = new MethodType(rtype, ptypes);
        MethodType mt0;
        synchronized (internTable) {
            mt0 = internTable.get(mt1);
            if (mt0 != null)
                return mt0;
        }
        if (!trusted)
            // defensively copy the array passed in by the user
            mt1 = new MethodType(rtype, ptypes.clone());
        // promote the object to the Real Thing, and reprobe
        MethodTypeImpl.initForm(IMPL_TOKEN, mt1);
        synchronized (internTable) {
            mt0 = internTable.get(mt1);
            if (mt0 != null)
                return mt0;
            internTable.put(mt1, mt1);
        }
        return mt1;
    }

    // Entry point from JVM.  TODO: Change the name & signature.
    private static MethodType makeImpl(Class<?> rtype, Class<?>[] ptypes,
            boolean ignore1, boolean ignore2) {
        return makeImpl(rtype, ptypes, true);
    }

    private static final MethodType[] objectOnlyTypes = new MethodType[20];

    /**
     * Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * All parameters and the return type will be {@code Object},
     * except the final varargs parameter if any, which will be {@code Object[]}.
     * @param objectArgCount number of parameters (excluding the varargs parameter if any)
     * @param varargs whether there will be a varargs parameter, of type {@code Object[]}
     * @return a totally generic method type, given only its count of parameters and varargs
     * @see #genericMethodType(int)
     */
    public static
    MethodType genericMethodType(int objectArgCount, boolean varargs) {
        MethodType mt;
        int ivarargs = (!varargs ? 0 : 1);
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
    @Deprecated public static
    MethodType makeGeneric(int objectArgCount, boolean varargs) {
        return genericMethodType(objectArgCount, varargs);
    }

    /**
     * All parameters and the return type will be Object.
     * @param objectArgCount number of parameters
     * @return a totally generic method type, given only its count of parameters
     * @see #genericMethodType(int, boolean)
     */
    public static
    MethodType genericMethodType(int objectArgCount) {
        return genericMethodType(objectArgCount, false);
    }
    @Deprecated public static
    MethodType makeGeneric(int objectArgCount) {
        return genericMethodType(objectArgCount);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * @param num    the index (zero-based) of the parameter type to change
     * @param nptype a new parameter type to replace the old one with
     * @return the same type, except with the selected parameter changed
     */
    public MethodType changeParameterType(int num, Class<?> nptype) {
        if (parameterType(num) == nptype)  return this;
        Class<?>[] nptypes = ptypes.clone();
        nptypes[num] = nptype;
        return makeImpl(rtype, nptypes, true);
    }

    /** Convenience method for {@link #insertParameterTypes}.
     * @deprecated Use {@link #insertParameterTypes} instead.
     */
    @Deprecated
    public MethodType insertParameterType(int num, Class<?> nptype) {
        int len = ptypes.length;
        Class<?>[] nptypes = Arrays.copyOfRange(ptypes, 0, len+1);
        System.arraycopy(nptypes, num, nptypes, num+1, len-num);
        nptypes[num] = nptype;
        return makeImpl(rtype, nptypes, true);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * @param num    the position (zero-based) of the inserted parameter type(s)
     * @param ptypesToInsert zero or more a new parameter types to insert into the parameter list
     * @return the same type, except with the selected parameter(s) inserted
     */
    public MethodType insertParameterTypes(int num, Class<?>... ptypesToInsert) {
        int len = ptypes.length;
        if (num < 0 || num > len)
            throw newIllegalArgumentException("num="+num); //SPECME
        int ilen = ptypesToInsert.length;
        if (ilen == 0)  return this;
        Class<?>[] nptypes = Arrays.copyOfRange(ptypes, 0, len+ilen);
        System.arraycopy(nptypes, num, nptypes, num+ilen, len-num);
        System.arraycopy(ptypesToInsert, 0, nptypes, num, ilen);
        return makeImpl(rtype, nptypes, true);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * @param num    the position (zero-based) of the inserted parameter type(s)
     * @param ptypesToInsert zero or more a new parameter types to insert into the parameter list
     * @return the same type, except with the selected parameter(s) inserted
     */
    public MethodType insertParameterTypes(int num, List<Class<?>> ptypesToInsert) {
        return insertParameterTypes(num, ptypesToInsert.toArray(NO_PTYPES));
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * @param start  the index (zero-based) of the first parameter type to remove
     * @param end    the index (greater than {@code start}) of the first parameter type after not to remove
     * @return the same type, except with the selected parameter(s) removed
     */
    public MethodType dropParameterTypes(int start, int end) {
        int len = ptypes.length;
        if (!(0 <= start && start <= end && end <= len))
            throw newIllegalArgumentException("start="+start+" end="+end); //SPECME
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

    /** Convenience method for {@link #dropParameterTypes}.
     * @deprecated Use {@link #dropParameterTypes} instead.
     */
    @Deprecated
    public MethodType dropParameterType(int num) {
        return dropParameterTypes(num, num+1);
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * @param nrtype a return parameter type to replace the old one with
     * @return the same type, except with the return type change
     */
    public MethodType changeReturnType(Class<?> nrtype) {
        if (returnType() == nrtype)  return this;
        return makeImpl(nrtype, ptypes, true);
    }

    /** Convenience method.
     * Report if this type contains a primitive argument or return value.
     * The return type {@code void} counts as a primitive.
     * @return true if any of the types are primitives
     */
    public boolean hasPrimitives() {
        return form.hasPrimitives();
    }

    /** Convenience method.
     * Report if this type contains a wrapper argument or return value.
     * Wrappers are types which box primitive values, such as {@link Integer}.
     * The reference type {@code java.lang.Void} counts as a wrapper.
     * @return true if any of the types are wrappers
     */
    public boolean hasWrappers() {
        return unwrap() != this;
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * Erase all reference types to {@code Object}.
     * All primitive types (including {@code void}) will remain unchanged.
     * @return a version of the original type with all reference types replaced
     */
    public MethodType erase() {
        return form.erasedType();
    }

    /** Convenience method for {@link #genericMethodType(int)}.
     * Convert all types, both reference and primitive, to {@code Object}.
     * The expression {@code type.wrap().erase()} produces the same value
     * as {@code type.generic()}.
     * @return a version of the original type with all types replaced
     */
    public MethodType generic() {
        return genericMethodType(parameterCount());
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * Convert all primitive types to their corresponding wrapper types.
     * All reference types (including wrapper types) will remain unchanged.
     * A {@code void} return type is changed to the type {@code java.lang.Void}.
     * The expression {@code type.wrap().erase()} produces the same value
     * as {@code type.generic()}.
     * @return a version of the original type with all primitive types replaced
     */
    public MethodType wrap() {
        return hasPrimitives() ? wrapWithPrims(this) : this;
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * Convert all wrapper types to their corresponding primitive types.
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
            wt = MethodTypeImpl.canonicalize(pt, MethodTypeImpl.WRAP, MethodTypeImpl.WRAP);
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
            uwt = MethodTypeImpl.canonicalize(wt, MethodTypeImpl.UNWRAP, MethodTypeImpl.UNWRAP);
            if (uwt == null)
                uwt = wt;    // type has no wrappers or prims at all
            wt.wrapAlt = uwt;
        }
        return uwt;
    }

    /** @param num the index (zero-based) of the desired parameter type
     *  @return the selected parameter type
     */
    public Class<?> parameterType(int num) {
        return ptypes[num];
    }
    /** @return the number of parameter types */
    public int parameterCount() {
        return ptypes.length;
    }
    /** @return the return type */
    public Class<?> returnType() {
        return rtype;
    }

    /**
     * Convenience method to present the arguments as a list.
     * @return the parameter types (as an immutable list)
     */
    public List<Class<?>> parameterList() {
        return Collections.unmodifiableList(Arrays.asList(ptypes));
    }

    /**
     * Convenience method to present the arguments as an array.
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
     * The string representation of a method type is a
     * parenthesis enclosed, comma separated list of type names,
     * followed immediately by the return type.
     * <p>
     * If a type name is array, it the base type followed
     * by [], rather than the Class.getName of the array type.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < ptypes.length; i++) {
            if (i > 0)  sb.append(",");
            putName(sb, ptypes[i]);
        }
        sb.append(")");
        putName(sb, rtype);
        return sb.toString();
    }

    static void putName(StringBuilder sb, Class<?> cls) {
        int brackets = 0;
        while (cls.isArray()) {
            cls = cls.getComponentType();
            brackets++;
        }
        String n = cls.getName();
        /*
        if (n.startsWith("java.lang.")) {
            String nb = n.substring("java.lang.".length());
            if (nb.indexOf('.') < 0)  n = nb;
        } else if (n.indexOf('.') < 0) {
            n = "."+n;          // anonymous package
        }
        */
        sb.append(n);
        while (brackets > 0) {
            sb.append("[]");
            brackets--;
        }
    }

    /// Queries which have to do with the bytecode architecture

    /** The number of JVM stack slots required to invoke a method
     * of this type.  Note that (for historic reasons) the JVM requires
     * a second stack slot to pass long and double arguments.
     * So this method returns {@link #parameterCount()} plus the
     * number of long and double parameters (if any).
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * @return the number of JVM stack slots for this type's parameters
     */
    public int parameterSlotCount() {
        return form.parameterSlotCount();
    }

    /** Number of JVM stack slots which carry all parameters including and after
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
     */
    public int parameterSlotDepth(int num) {
        if (num < 0 || num > ptypes.length)
            parameterType(num);  // force a range check
        return form.parameterToArgSlot(num-1);
    }

    /** The number of JVM stack slots required to receive a return value
     * from a method of this type.
     * If the {@link #returnType() return type} is void, it will be zero,
     * else if the return type is long or double, it will be two, else one.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * @return the number of JVM stack slots (0, 1, or 2) for this type's return value
     */
    public int returnSlotCount() {
        return form.returnSlotCount();
    }

    /** Convenience method for {@link #methodType(java.lang.Class, java.lang.Class[])}.
     * Find or create an instance of the given method type.
     * Any class or interface name embedded in the descriptor string
     * will be resolved by calling {@link ClassLoader#loadClass(java.lang.String)}
     * on the given loader (or if it is null, on the system class loader).
     * <p>
     * Note that it is possible to encounter method types which cannot be
     * constructed by this method, because their component types are
     * not all reachable from a common class loader.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * @param descriptor a bytecode-level signature string "(T...)T"
     * @param loader the class loader in which to look up the types
     * @return a method type matching the bytecode-level signature
     * @throws IllegalArgumentException if the string is not well-formed
     * @throws TypeNotPresentException if a named type cannot be found
     */
    public static MethodType fromMethodDescriptorString(String descriptor, ClassLoader loader)
        throws IllegalArgumentException, TypeNotPresentException
    {
        List<Class<?>> types = BytecodeDescriptor.parseMethod(descriptor, loader);
        Class<?> rtype = types.remove(types.size() - 1);
        Class<?>[] ptypes = types.toArray(NO_PTYPES);
        return makeImpl(rtype, ptypes, true);
    }

    /**
     * Create a bytecode descriptor representation of the method type.
     * <p>
     * Note that this is not a strict inverse of {@link #fromMethodDescriptorString}.
     * Two distinct classes which share a common name but have different class loaders
     * will appear identical when viewed within descriptor strings.
     * <p>
     * This method is included for the benfit of applications that must
     * generate bytecodes that process method handles and invokedynamic.
     * {@link #fromMethodDescriptorString(java.lang.String, java.lang.ClassLoader)},
     * because the latter requires a suitable class loader argument.
     * @return the bytecode signature representation
     */
    public String toMethodDescriptorString() {
        return BytecodeDescriptor.unparse(this);
    }

    /** Temporary alias for toMethodDescriptorString; delete after M3. */
    public String toBytecodeString() {
        return toMethodDescriptorString();
    }
    /** Temporary alias for fromMethodDescriptorString; delete after M3. */
    public static MethodType fromBytecodeString(String descriptor, ClassLoader loader)
        throws IllegalArgumentException, TypeNotPresentException {
        return fromMethodDescriptorString(descriptor, loader);
    }
}
