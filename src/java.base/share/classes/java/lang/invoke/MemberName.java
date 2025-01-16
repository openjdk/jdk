/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.invoke.util.VerifyAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import static java.lang.invoke.MethodHandleNatives.Constants.*;
import static java.lang.invoke.MethodHandleStatics.newIllegalArgumentException;
import static java.lang.invoke.MethodHandleStatics.newInternalError;

/**
 * A {@code MemberName} is a compact symbolic datum which fully characterizes
 * a method or field reference.
 * A member name refers to a field, method, constructor, or member type.
 * Every member name has a simple name (a string) and a type (either a Class or MethodType).
 * A member name may also have a non-null declaring class, or it may be simply
 * a naked name/type pair.
 * A member name may also have non-zero modifier flags.
 * Finally, a member name may be either resolved or unresolved.
 * If it is resolved, the existence of the named member has been determined by the JVM.
 * <p>
 * Whether resolved or not, a member name provides no access rights or
 * invocation capability to its possessor.  It is merely a compact
 * representation of all symbolic information necessary to link to
 * and properly use the named member.
 * <p>
 * When resolved, a member name's internal implementation may include references to JVM metadata.
 * This representation is stateless and only descriptive.
 * It provides no private information and no capability to use the member.
 * <p>
 * By contrast, a {@linkplain java.lang.reflect.Method} contains fuller information
 * about the internals of a method (except its bytecodes) and also
 * allows invocation.  A MemberName is much lighter than a Method,
 * since it contains about 7 fields to the 16 of Method (plus its sub-arrays),
 * and those seven fields omit much of the information in Method.
 * @author jrose
 */

/*non-public*/
final class MemberName implements Member, Cloneable {
    private Class<?> clazz;       // class in which the member is defined
    private String   name;        // may be null if not yet materialized
    private Object   type;        // may be null if not yet materialized
    private int      flags;       // modifier bits; see reflect.Modifier
    private ResolvedMethodName method;    // cached resolved method information
    //@Injected intptr_t       vmindex;   // vtable index or offset of resolved member
    Object   resolution;  // if null, this guy is resolved

    /** Return the declaring class of this member.
     *  In the case of a bare name and type, the declaring class will be null.
     */
    public Class<?> getDeclaringClass() {
        return clazz;
    }

    /** Utility method producing the class loader of the declaring class. */
    public ClassLoader getClassLoader() {
        return clazz.getClassLoader();
    }

    /** Return the simple name of this member.
     *  For a type, it is the same as {@link Class#getSimpleName}.
     *  For a method or field, it is the simple name of the member.
     *  For a constructor, it is always {@code "<init>"}.
     */
    public String getName() {
        if (name == null) {
            expandFromVM();
            if (name == null) {
                return null;
            }
        }
        return name;
    }

    public MethodType getMethodOrFieldType() {
        if (isInvocable())
            return getMethodType();
        if (isGetter())
            return MethodType.methodType(getFieldType());
        if (isSetter())
            return MethodType.methodType(void.class, getFieldType());
        throw new InternalError("not a method or field: "+this);
    }

    /** Return the declared type of this member, which
     *  must be a method or constructor.
     */
    public MethodType getMethodType() {
        if (type == null) {
            expandFromVM();
            if (type == null) {
                return null;
            }
        }
        if (!isInvocable()) {
            throw newIllegalArgumentException("not invocable, no method type");
        }

        {
            // Get a snapshot of type which doesn't get changed by racing threads.
            final Object type = this.type;
            if (type instanceof MethodType mt) {
                return mt;
            }
        }

        // type is not a MethodType yet.  Convert it thread-safely.
        synchronized (this) {
            if (type instanceof String sig) {
                MethodType res = MethodType.fromDescriptor(sig, getClassLoader());
                type = res;
            } else if (type instanceof Object[] typeInfo) {
                Class<?>[] ptypes = (Class<?>[]) typeInfo[1];
                Class<?> rtype = (Class<?>) typeInfo[0];
                MethodType res = MethodType.methodType(rtype, ptypes, true);
                type = res;
            }
            // Make sure type is a MethodType for racing threads.
            assert type instanceof MethodType : "bad method type " + type;
        }
        return (MethodType) type;
    }

    /** Return the descriptor of this member, which
     *  must be a method or constructor.
     */
    String getMethodDescriptor() {
        if (type == null) {
            expandFromVM();
            if (type == null) {
                return null;
            }
        }
        if (!isInvocable()) {
            throw newIllegalArgumentException("not invocable, no method type");
        }

        // Get a snapshot of type which doesn't get changed by racing threads.
        final Object type = this.type;
        if (type instanceof String str) {
            return str;
        } else {
            return getMethodType().toMethodDescriptorString();
        }
    }

    /** Return the actual type under which this method or constructor must be invoked.
     *  For non-static methods or constructors, this is the type with a leading parameter,
     *  a reference to declaring class.  For static methods, it is the same as the declared type.
     */
    public MethodType getInvocationType() {
        MethodType itype = getMethodOrFieldType();
        if (isConstructor() && getReferenceKind() == REF_newInvokeSpecial)
            return itype.changeReturnType(clazz);
        if (!isStatic())
            return itype.insertParameterTypes(0, clazz);
        return itype;
    }

    /** Return the declared type of this member, which
     *  must be a field or type.
     *  If it is a type member, that type itself is returned.
     */
    public Class<?> getFieldType() {
        if (type == null) {
            expandFromVM();
            if (type == null) {
                return null;
            }
        }
        if (isInvocable()) {
            throw newIllegalArgumentException("not a field or nested class, no simple type");
        }

        {
            // Get a snapshot of type which doesn't get changed by racing threads.
            final Object type = this.type;
            if (type instanceof Class<?> cl) {
                return cl;
            }
        }

        // type is not a Class yet.  Convert it thread-safely.
        synchronized (this) {
            if (type instanceof String sig) {
                MethodType mtype = MethodType.fromDescriptor("()"+sig, getClassLoader());
                Class<?> res = mtype.returnType();
                type = res;
            }
            // Make sure type is a Class for racing threads.
            assert type instanceof Class<?> : "bad field type " + type;
        }
        return (Class<?>) type;
    }

    /** Utility method to produce either the method type or field type of this member. */
    public Object getType() {
        return (isInvocable() ? getMethodType() : getFieldType());
    }

    /** Return the modifier flags of this member.
     *  @see java.lang.reflect.Modifier
     */
    public int getModifiers() {
        return (flags & RECOGNIZED_MODIFIERS);
    }

    /** Return the reference kind of this member, or zero if none.
     */
    public byte getReferenceKind() {
        return (byte) ((flags >>> MN_REFERENCE_KIND_SHIFT) & MN_REFERENCE_KIND_MASK);
    }
    private boolean referenceKindIsConsistent() {
        byte refKind = getReferenceKind();
        if (refKind == REF_NONE)  return isType();
        if (isField()) {
            assert(staticIsConsistent());
            assert(MethodHandleNatives.refKindIsField(refKind));
        } else if (isConstructor()) {
            assert(refKind == REF_newInvokeSpecial || refKind == REF_invokeSpecial);
        } else if (isMethod()) {
            assert(staticIsConsistent());
            assert(MethodHandleNatives.refKindIsMethod(refKind));
            if (clazz.isInterface())
                assert(refKind == REF_invokeInterface ||
                       refKind == REF_invokeStatic    ||
                       refKind == REF_invokeSpecial   ||
                       refKind == REF_invokeVirtual && isObjectPublicMethod());
        } else {
            assert(false);
        }
        return true;
    }
    private boolean isObjectPublicMethod() {
        if (clazz == Object.class)  return true;
        MethodType mtype = getMethodType();
        if (name.equals("toString") && mtype.returnType() == String.class && mtype.parameterCount() == 0)
            return true;
        if (name.equals("hashCode") && mtype.returnType() == int.class && mtype.parameterCount() == 0)
            return true;
        if (name.equals("equals") && mtype.returnType() == boolean.class && mtype.parameterCount() == 1 && mtype.parameterType(0) == Object.class)
            return true;
        return false;
    }

    /*non-public*/
    boolean referenceKindIsConsistentWith(int originalRefKind) {
        int refKind = getReferenceKind();
        if (refKind == originalRefKind) return true;
        if (getClass().desiredAssertionStatus()) {
            switch (originalRefKind) {
                case REF_invokeInterface -> {
                    // Looking up an interface method, can get (e.g.) Object.hashCode
                    assert (refKind == REF_invokeVirtual || refKind == REF_invokeSpecial) : this;
                }
                case REF_invokeVirtual, REF_newInvokeSpecial -> {
                    // Looked up a virtual, can get (e.g.) final String.hashCode.
                    assert (refKind == REF_invokeSpecial) : this;
                }
                default -> {
                    assert (false) : this + " != " + MethodHandleNatives.refKindName((byte) originalRefKind);
                }
            }
        }
        return true;
    }
    private boolean staticIsConsistent() {
        byte refKind = getReferenceKind();
        return MethodHandleNatives.refKindIsStatic(refKind) == isStatic() || getModifiers() == 0;
    }
    private boolean vminfoIsConsistent() {
        byte refKind = getReferenceKind();
        assert(isResolved());  // else don't call
        Object vminfo = MethodHandleNatives.getMemberVMInfo(this);
        assert(vminfo instanceof Object[]);
        long vmindex = (Long) ((Object[])vminfo)[0];
        Object vmtarget = ((Object[])vminfo)[1];
        if (MethodHandleNatives.refKindIsField(refKind)) {
            assert(vmindex >= 0) : vmindex + ":" + this;
            assert(vmtarget instanceof Class);
        } else {
            if (MethodHandleNatives.refKindDoesDispatch(refKind))
                assert(vmindex >= 0) : vmindex + ":" + this;
            else
                assert(vmindex < 0) : vmindex;
            assert(vmtarget instanceof MemberName) : vmtarget + " in " + this;
        }
        return true;
    }

    private MemberName changeReferenceKind(byte refKind, byte oldKind) {
        assert(getReferenceKind() == oldKind && MethodHandleNatives.refKindIsValid(refKind));
        flags += (((int)refKind - oldKind) << MN_REFERENCE_KIND_SHIFT);
        return this;
    }

    private boolean matchingFlagsSet(int mask, int flags) {
        return (this.flags & mask) == flags;
    }
    private boolean allFlagsSet(int flags) {
        return (this.flags & flags) == flags;
    }
    private boolean anyFlagSet(int flags) {
        return (this.flags & flags) != 0;
    }

    /** Utility method to query if this member is a method handle invocation (invoke or invokeExact).
     */
    public boolean isMethodHandleInvoke() {
        final int bits = MH_INVOKE_MODS &~ Modifier.PUBLIC;
        final int negs = Modifier.STATIC;
        if (matchingFlagsSet(bits | negs, bits) && clazz == MethodHandle.class) {
            return isMethodHandleInvokeName(name);
        }
        return false;
    }
    public static boolean isMethodHandleInvokeName(String name) {
        return switch (name) {
            case "invoke", "invokeExact" -> true;
            default -> false;
        };
    }
    public boolean isVarHandleMethodInvoke() {
        final int bits = MH_INVOKE_MODS &~ Modifier.PUBLIC;
        final int negs = Modifier.STATIC;
        if (matchingFlagsSet(bits | negs, bits) && clazz == VarHandle.class) {
            return isVarHandleMethodInvokeName(name);
        }
        return false;
    }
    public static boolean isVarHandleMethodInvokeName(String name) {
        try {
            VarHandle.AccessMode.valueFromMethodName(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    private static final int MH_INVOKE_MODS = Modifier.NATIVE | Modifier.FINAL | Modifier.PUBLIC;

    /** Utility method to query the modifier flags of this member. */
    public boolean isStatic() {
        return Modifier.isStatic(flags);
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isPublic() {
        return Modifier.isPublic(flags);
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isPrivate() {
        return Modifier.isPrivate(flags);
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isProtected() {
        return Modifier.isProtected(flags);
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isFinal() {
        return Modifier.isFinal(flags);
    }
    /** Utility method to query whether this member or its defining class is final. */
    public boolean canBeStaticallyBound() {
        return Modifier.isFinal(flags | clazz.getModifiers());
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isVolatile() {
        return Modifier.isVolatile(flags);
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isAbstract() {
        return Modifier.isAbstract(flags);
    }
    /** Utility method to query the modifier flags of this member. */
    public boolean isNative() {
        return Modifier.isNative(flags);
    }
    // let the rest (native, volatile, transient, etc.) be tested via Modifier.isFoo

    // unofficial modifier flags, used by HotSpot:
    static final int BRIDGE    = 0x00000040;
    static final int VARARGS   = 0x00000080;
    static final int SYNTHETIC = 0x00001000;
    static final int ANNOTATION= 0x00002000;
    static final int ENUM      = 0x00004000;
    /** Utility method to query the modifier flags of this member; returns false if the member is not a method. */
    public boolean isBridge() {
        return allFlagsSet(IS_METHOD | BRIDGE);
    }
    /** Utility method to query the modifier flags of this member; returns false if the member is not a method. */
    public boolean isVarargs() {
        return allFlagsSet(VARARGS) && isInvocable();
    }
    /** Utility method to query the modifier flags of this member; returns false if the member is not a method. */
    public boolean isSynthetic() {
        return allFlagsSet(SYNTHETIC);
    }

    static final String CONSTRUCTOR_NAME = "<init>";  // the ever-popular

    // modifiers exported by the JVM:
    static final int RECOGNIZED_MODIFIERS = 0xFFFF;

    // private flags, not part of RECOGNIZED_MODIFIERS:
    static final int
            IS_METHOD        = MN_IS_METHOD,        // method (not constructor)
            IS_CONSTRUCTOR   = MN_IS_CONSTRUCTOR,   // constructor
            IS_FIELD         = MN_IS_FIELD,         // field
            IS_TYPE          = MN_IS_TYPE,          // nested type
            CALLER_SENSITIVE = MN_CALLER_SENSITIVE, // @CallerSensitive annotation detected
            TRUSTED_FINAL    = MN_TRUSTED_FINAL;    // trusted final field

    static final int ALL_ACCESS = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED;
    static final int ALL_KINDS = IS_METHOD | IS_CONSTRUCTOR | IS_FIELD | IS_TYPE;
    static final int IS_INVOCABLE = IS_METHOD | IS_CONSTRUCTOR;

    /** Utility method to query whether this member is a method or constructor. */
    public boolean isInvocable() {
        return anyFlagSet(IS_INVOCABLE);
    }
    /** Query whether this member is a method. */
    public boolean isMethod() {
        return allFlagsSet(IS_METHOD);
    }
    /** Query whether this member is a constructor. */
    public boolean isConstructor() {
        return allFlagsSet(IS_CONSTRUCTOR);
    }
    /** Query whether this member is a field. */
    public boolean isField() {
        return allFlagsSet(IS_FIELD);
    }
    /** Query whether this member is a type. */
    public boolean isType() {
        return allFlagsSet(IS_TYPE);
    }
    /** Utility method to query whether this member is neither public, private, nor protected. */
    public boolean isPackage() {
        return !anyFlagSet(ALL_ACCESS);
    }
    /** Query whether this member has a CallerSensitive annotation. */
    public boolean isCallerSensitive() {
        return allFlagsSet(CALLER_SENSITIVE);
    }
    /** Query whether this member is a trusted final field. */
    public boolean isTrustedFinalField() {
        return allFlagsSet(TRUSTED_FINAL | IS_FIELD);
    }

    /**
     * Check if MemberName is a call to a method named {@code name} in class {@code declaredClass}.
     */
    public boolean refersTo(Class<?> declc, String n) {
        return clazz == declc && getName().equals(n);
    }

    /** Initialize a query.   It is not resolved. */
    private void init(Class<?> defClass, String name, Object type, int flags) {
        // defining class is allowed to be null (for a naked name/type pair)
        //name.toString();  // null check
        //type.equals(type);  // null check
        // fill in fields:
        this.clazz = defClass;
        this.name = name;
        this.type = type;
        this.flags = flags;
        assert(anyFlagSet(ALL_KINDS) && this.resolution == null);  // nobody should have touched this yet
        //assert(referenceKindIsConsistent());  // do this after resolution
    }

    /**
     * Calls down to the VM to fill in the fields.  This method is
     * synchronized to avoid racing calls.
     */
    private void expandFromVM() {
        if (type != null) {
            return;
        }
        if (!isResolved()) {
            return;
        }
        MethodHandleNatives.expand(this);
    }

    // Capturing information from the Core Reflection API:
    private static int flagsMods(int flags, int mods, byte refKind) {
        assert((flags & RECOGNIZED_MODIFIERS) == 0
                && (mods & ~RECOGNIZED_MODIFIERS) == 0
                && (refKind & ~MN_REFERENCE_KIND_MASK) == 0);
        return flags | mods | (refKind << MN_REFERENCE_KIND_SHIFT);
    }
    /** Create a name for the given reflected method.  The resulting name will be in a resolved state. */
    public MemberName(Method m) {
        this(m, false);
    }
    @SuppressWarnings("LeakingThisInConstructor")
    public MemberName(Method m, boolean wantSpecial) {
        Objects.requireNonNull(m);
        // fill in vmtarget, vmindex while we have m in hand:
        MethodHandleNatives.init(this, m);
        if (clazz == null) {  // MHN.init failed
            if (m.getDeclaringClass() == MethodHandle.class &&
                isMethodHandleInvokeName(m.getName())) {
                // The JVM did not reify this signature-polymorphic instance.
                // Need a special case here.
                // See comments on MethodHandleNatives.linkMethod.
                MethodType type = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
                int flags = flagsMods(IS_METHOD, m.getModifiers(), REF_invokeVirtual);
                init(MethodHandle.class, m.getName(), type, flags);
                if (isMethodHandleInvoke())
                    return;
            }
            if (m.getDeclaringClass() == VarHandle.class &&
                isVarHandleMethodInvokeName(m.getName())) {
                // The JVM did not reify this signature-polymorphic instance.
                // Need a special case here.
                // See comments on MethodHandleNatives.linkMethod.
                MethodType type = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
                int flags = flagsMods(IS_METHOD, m.getModifiers(), REF_invokeVirtual);
                init(VarHandle.class, m.getName(), type, flags);
                if (isVarHandleMethodInvoke())
                    return;
            }
            throw new LinkageError(m.toString());
        }
        assert(isResolved());
        this.name = m.getName();
        if (this.type == null)
            this.type = new Object[] { m.getReturnType(), m.getParameterTypes() };
        if (wantSpecial) {
            if (isAbstract())
                throw new AbstractMethodError(this.toString());
            if (getReferenceKind() == REF_invokeVirtual)
                changeReferenceKind(REF_invokeSpecial, REF_invokeVirtual);
            else if (getReferenceKind() == REF_invokeInterface)
                // invokeSpecial on a default method
                changeReferenceKind(REF_invokeSpecial, REF_invokeInterface);
        }
    }
    public MemberName asSpecial() {
        switch (getReferenceKind()) {
        case REF_invokeSpecial:     return this;
        case REF_invokeVirtual:     return clone().changeReferenceKind(REF_invokeSpecial, REF_invokeVirtual);
        case REF_invokeInterface:   return clone().changeReferenceKind(REF_invokeSpecial, REF_invokeInterface);
        case REF_newInvokeSpecial:  return clone().changeReferenceKind(REF_invokeSpecial, REF_newInvokeSpecial);
        }
        throw new IllegalArgumentException(this.toString());
    }
    /** If this MN is not REF_newInvokeSpecial, return a clone with that ref. kind.
     *  In that case it must already be REF_invokeSpecial.
     */
    public MemberName asConstructor() {
        switch (getReferenceKind()) {
        case REF_invokeSpecial:     return clone().changeReferenceKind(REF_newInvokeSpecial, REF_invokeSpecial);
        case REF_newInvokeSpecial:  return this;
        }
        throw new IllegalArgumentException(this.toString());
    }
    /** If this MN is a REF_invokeSpecial, return a clone with the "normal" kind
     *  REF_invokeVirtual; also switch either to REF_invokeInterface if clazz.isInterface.
     *  The end result is to get a fully virtualized version of the MN.
     *  (Note that resolving in the JVM will sometimes devirtualize, changing
     *  REF_invokeVirtual of a final to REF_invokeSpecial, and REF_invokeInterface
     *  in some corner cases to either of the previous two; this transform
     *  undoes that change under the assumption that it occurred.)
     */
    public MemberName asNormalOriginal() {
        byte refKind = getReferenceKind();
        byte newRefKind = switch (refKind) {
            case REF_invokeInterface,
                 REF_invokeVirtual,
                 REF_invokeSpecial -> clazz.isInterface() ? REF_invokeInterface : REF_invokeVirtual;
            default -> refKind;
        };
        if (newRefKind == refKind)
            return this;
        MemberName result = clone().changeReferenceKind(newRefKind, refKind);
        assert(this.referenceKindIsConsistentWith(result.getReferenceKind()));
        return result;
    }
    /** Create a name for the given reflected constructor.  The resulting name will be in a resolved state. */
    @SuppressWarnings("LeakingThisInConstructor")
    public MemberName(Constructor<?> ctor) {
        Objects.requireNonNull(ctor);
        // fill in vmtarget, vmindex while we have ctor in hand:
        MethodHandleNatives.init(this, ctor);
        assert(isResolved() && this.clazz != null);
        this.name = CONSTRUCTOR_NAME;
        if (this.type == null)
            this.type = new Object[] { void.class, ctor.getParameterTypes() };
    }
    /** Create a name for the given reflected field.  The resulting name will be in a resolved state.
     */
    public MemberName(Field fld) {
        this(fld, false);
    }
    static {
        // the following MemberName constructor relies on these ranges matching up
        assert((REF_putStatic - REF_getStatic) == (REF_putField - REF_getField));
    }
    @SuppressWarnings("LeakingThisInConstructor")
    public MemberName(Field fld, boolean makeSetter) {
        Objects.requireNonNull(fld);
        // fill in vmtarget, vmindex while we have fld in hand:
        MethodHandleNatives.init(this, fld);
        assert(isResolved() && this.clazz != null);
        this.name = fld.getName();
        this.type = fld.getType();
        byte refKind = this.getReferenceKind();
        assert(refKind == (isStatic() ? REF_getStatic : REF_getField));
        if (makeSetter) {
            changeReferenceKind((byte)(refKind + (REF_putStatic - REF_getStatic)), refKind);
        }
    }
    public boolean isGetter() {
        return MethodHandleNatives.refKindIsGetter(getReferenceKind());
    }
    public boolean isSetter() {
        return MethodHandleNatives.refKindIsSetter(getReferenceKind());
    }

    /** Create a name for the given class.  The resulting name will be in a resolved state. */
    public MemberName(Class<?> type) {
        init(type.getDeclaringClass(), type.getSimpleName(), type,
                flagsMods(IS_TYPE, type.getModifiers(), REF_NONE));
        initResolved(true);
    }

    /**
     * Create a name for a signature-polymorphic invoker.
     * This is a placeholder for a signature-polymorphic instance
     * (of MH.invokeExact, etc.) that the JVM does not reify.
     * See comments on {@link MethodHandleNatives#linkMethod}.
     */
    static MemberName makeMethodHandleInvoke(String name, MethodType type) {
        return makeMethodHandleInvoke(name, type, MH_INVOKE_MODS | SYNTHETIC);
    }
    static MemberName makeMethodHandleInvoke(String name, MethodType type, int mods) {
        MemberName mem = new MemberName(MethodHandle.class, name, type, REF_invokeVirtual);
        mem.flags |= mods;  // it's not resolved, but add these modifiers anyway
        assert(mem.isMethodHandleInvoke()) : mem;
        return mem;
    }

    static MemberName makeVarHandleMethodInvoke(String name, MethodType type) {
        return makeVarHandleMethodInvoke(name, type, MH_INVOKE_MODS | SYNTHETIC);
    }
    static MemberName makeVarHandleMethodInvoke(String name, MethodType type, int mods) {
        MemberName mem = new MemberName(VarHandle.class, name, type, REF_invokeVirtual);
        mem.flags |= mods;  // it's not resolved, but add these modifiers anyway
        assert(mem.isVarHandleMethodInvoke()) : mem;
        return mem;
    }

    // bare-bones constructor; the JVM will fill it in
    MemberName() { }

    // locally useful cloner
    @Override protected MemberName clone() {
        try {
            return (MemberName) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw newInternalError(ex);
        }
     }

    /** Get the definition of this member name.
     *  This may be in a super-class of the declaring class of this member.
     */
    public MemberName getDefinition() {
        if (!isResolved())  throw new IllegalStateException("must be resolved: "+this);
        if (isType())  return this;
        MemberName res = this.clone();
        res.clazz = null;
        res.type = null;
        res.name = null;
        res.resolution = res;
        res.expandFromVM();
        assert(res.getName().equals(this.getName()));
        return res;
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public int hashCode() {
        // Avoid autoboxing getReferenceKind(), since this is used early and will force
        // early initialization of Byte$ByteCache
        return Objects.hash(clazz, new Byte(getReferenceKind()), name, getType());
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof MemberName mn && this.equals(mn);
    }

    /** Decide if two member names have exactly the same symbolic content.
     *  Does not take into account any actual class members, so even if
     *  two member names resolve to the same actual member, they may
     *  be distinct references.
     */
    public boolean equals(MemberName that) {
        if (this == that)  return true;
        if (that == null)  return false;
        return this.clazz == that.clazz
                && this.getReferenceKind() == that.getReferenceKind()
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.getType(), that.getType());
    }

    // Construction from symbolic parts, for queries:
    /** Create a field or type name from the given components:
     *  Declaring class, name, type, reference kind.
     *  The declaring class may be supplied as null if this is to be a bare name and type.
     *  The resulting name will in an unresolved state.
     */
    public MemberName(Class<?> defClass, String name, Class<?> type, byte refKind) {
        init(defClass, name, type, flagsMods(IS_FIELD, 0, refKind));
        initResolved(false);
    }
    /** Create a method or constructor name from the given components:
     *  Declaring class, name, type, reference kind.
     *  It will be a constructor if and only if the name is {@code "<init>"}.
     *  The declaring class may be supplied as null if this is to be a bare name and type.
     *  The last argument is optional, a boolean which requests REF_invokeSpecial.
     *  The resulting name will in an unresolved state.
     */
    public MemberName(Class<?> defClass, String name, MethodType type, byte refKind) {
        int initFlags = (name != null && name.equals(CONSTRUCTOR_NAME) ? IS_CONSTRUCTOR : IS_METHOD);
        init(defClass, name, type, flagsMods(initFlags, 0, refKind));
        initResolved(false);
    }
    /** Create a method, constructor, or field name from the given components:
     *  Reference kind, declaring class, name, type.
     */
    public MemberName(byte refKind, Class<?> defClass, String name, Object type) {
        int kindFlags;
        if (MethodHandleNatives.refKindIsField(refKind)) {
            kindFlags = IS_FIELD;
            if (!(type instanceof Class))
                throw newIllegalArgumentException("not a field type");
        } else if (MethodHandleNatives.refKindIsMethod(refKind)) {
            kindFlags = IS_METHOD;
            if (!(type instanceof MethodType))
                throw newIllegalArgumentException("not a method type");
        } else if (refKind == REF_newInvokeSpecial) {
            kindFlags = IS_CONSTRUCTOR;
            if (!(type instanceof MethodType) ||
                !CONSTRUCTOR_NAME.equals(name))
                throw newIllegalArgumentException("not a constructor type or name");
        } else {
            throw newIllegalArgumentException("bad reference kind "+refKind);
        }
        init(defClass, name, type, flagsMods(kindFlags, 0, refKind));
        initResolved(false);
    }

    /** Query whether this member name is resolved.
     *  A resolved member name is one for which the JVM has found
     *  a method, constructor, field, or type binding corresponding exactly to the name.
     *  (Document?)
     */
    public boolean isResolved() {
        return resolution == null;
    }

    void initResolved(boolean isResolved) {
        assert(this.resolution == null);  // not initialized yet!
        if (!isResolved)
            this.resolution = this;
        assert(isResolved() == isResolved);
    }

    void ensureTypeVisible(Class<?> refc) {
        if (isInvocable()) {
            MethodType type;
            if (this.type instanceof MethodType mt)
                type = mt;
            else
                this.type = type = getMethodType();
            if (type.erase() == type)  return;
            if (VerifyAccess.ensureTypeVisible(type, refc))  return;
            throw new LinkageError("bad method type alias: "+type+" not visible from "+refc);
        } else {
            Class<?> type;
            if (this.type instanceof Class<?> cl)
                type = cl;
            else
                this.type = type = getFieldType();
            if (VerifyAccess.ensureTypeVisible(type, refc))  return;
            throw new LinkageError("bad field type alias: "+type+" not visible from "+refc);
        }
    }


    /** Produce a string form of this member name.
     *  For types, it is simply the type's own string (as reported by {@code toString}).
     *  For fields, it is {@code "DeclaringClass.name/type"}.
     *  For methods and constructors, it is {@code "DeclaringClass.name(ptype...)rtype"}.
     *  If the declaring class is null, the prefix {@code "DeclaringClass."} is omitted.
     *  If the member is unresolved, a prefix {@code "*."} is prepended.
     */
    @SuppressWarnings("LocalVariableHidesMemberVariable")
    @Override
    public String toString() {
        if (isType())
            return type.toString();  // class java.lang.String
        // else it is a field, method, or constructor
        StringBuilder buf = new StringBuilder();
        if (getDeclaringClass() != null) {
            buf.append(getName(clazz));
            buf.append('.');
        }
        String name = this.name; // avoid expanding from VM
        buf.append(name == null ? "*" : name);
        Object type = this.type; // avoid expanding from VM
        if (!isInvocable()) {
            buf.append('/');
            buf.append(type == null ? "*" : getName(type));
        } else {
            buf.append(type == null ? "(*)*" : getName(type));
        }
        byte refKind = getReferenceKind();
        if (refKind != REF_NONE) {
            buf.append('/');
            buf.append(MethodHandleNatives.refKindName(refKind));
        }
        //buf.append("#").append(System.identityHashCode(this));
        return buf.toString();
    }
    private static String getName(Object obj) {
        if (obj instanceof Class<?> cl)
            return cl.getName();
        return String.valueOf(obj);
    }

    public IllegalAccessException makeAccessException(String message, Object from) {
        message = message + ": " + this;
        if (from != null)  {
            if (from == MethodHandles.publicLookup()) {
                message += ", from public Lookup";
            } else {
                Module m;
                Class<?> plc;
                if (from instanceof MethodHandles.Lookup lookup) {
                    from = lookup.lookupClass();
                    m = lookup.lookupClass().getModule();
                    plc = lookup.previousLookupClass();
                } else {
                    m = ((Class<?>)from).getModule();
                    plc = null;
                }
                message += ", from " + from + " (" + m + ")";
                if (plc != null) {
                    message += ", previous lookup " +
                        plc.getName() + " (" + plc.getModule() + ")";
                }
            }
        }
        return new IllegalAccessException(message);
    }
    private String message() {
        if (isResolved())
            return "no access";
        else if (isConstructor())
            return "no such constructor";
        else if (isMethod())
            return "no such method";
        else
            return "no such field";
    }
    public ReflectiveOperationException makeAccessException() {
        String message = message() + ": " + this;
        ReflectiveOperationException ex;
        if (isResolved() || !(resolution instanceof NoSuchMethodError ||
                              resolution instanceof NoSuchFieldError))
            ex = new IllegalAccessException(message);
        else if (isConstructor())
            ex = new NoSuchMethodException(message);
        else if (isMethod())
            ex = new NoSuchMethodException(message);
        else
            ex = new NoSuchFieldException(message);
        if (resolution instanceof Throwable res)
            ex.initCause(res);
        return ex;
    }

    /** Actually making a query requires an access check. */
    /*non-public*/
    static Factory getFactory() {
        return Factory.INSTANCE;
    }
    /** A factory type for resolving member names with the help of the VM.
     *  TBD: Define access-safe public constructors for this factory.
     */
    /*non-public*/
    static class Factory {
        private Factory() { } // singleton pattern
        static final Factory INSTANCE = new Factory();

        /** Produce a resolved version of the given member.
         *  Super types are searched (for inherited members) if {@code searchSupers} is true.
         *  Access checking is performed on behalf of the given {@code lookupClass}.
         *  If lookup fails or access is not permitted, null is returned.
         *  Otherwise a fresh copy of the given member is returned, with modifier bits filled in.
         */
        private MemberName resolve(byte refKind, MemberName ref, Class<?> lookupClass, int allowedModes,
                                   boolean speculativeResolve) {
            MemberName m = ref.clone();  // JVM will side-effect the ref
            assert(refKind == m.getReferenceKind());
            try {
                // There are 4 entities in play here:
                //   * LC: lookupClass
                //   * REFC: symbolic reference class (MN.clazz before resolution);
                //   * DEFC: resolved method holder (MN.clazz after resolution);
                //   * PTYPES: parameter types (MN.type)
                //
                // What we care about when resolving a MemberName is consistency between DEFC and PTYPES.
                // We do type alias (TA) checks on DEFC to ensure that. DEFC is not known until the JVM
                // finishes the resolution, so do TA checks right after MHN.resolve() is over.
                //
                // All parameters passed by a caller are checked against MH type (PTYPES) on every invocation,
                // so it is safe to call a MH from any context.
                //
                // REFC view on PTYPES doesn't matter, since it is used only as a starting point for resolution and doesn't
                // participate in method selection.
                m = MethodHandleNatives.resolve(m, lookupClass, allowedModes, speculativeResolve);
                if (m == null && speculativeResolve) {
                    return null;
                }
                m.ensureTypeVisible(m.getDeclaringClass());
                m.resolution = null;
            } catch (ClassNotFoundException | LinkageError ex) {
                // JVM reports that the "bytecode behavior" would get an error
                assert(!m.isResolved());
                m.resolution = ex;
                return m;
            }
            assert(m.referenceKindIsConsistent());
            m.initResolved(true);
            assert(m.vminfoIsConsistent());
            return m;
        }
        /** Produce a resolved version of the given member.
         *  Super types are searched (for inherited members) if {@code searchSupers} is true.
         *  Access checking is performed on behalf of the given {@code lookupClass}.
         *  If lookup fails or access is not permitted, a {@linkplain ReflectiveOperationException} is thrown.
         *  Otherwise a fresh copy of the given member is returned, with modifier bits filled in.
         */
        public <NoSuchMemberException extends ReflectiveOperationException>
                MemberName resolveOrFail(byte refKind, MemberName m,
                                         Class<?> lookupClass, int allowedModes,
                                         Class<NoSuchMemberException> nsmClass)
                throws IllegalAccessException, NoSuchMemberException {
            assert lookupClass != null || allowedModes == LM_TRUSTED;
            MemberName result = resolve(refKind, m, lookupClass, allowedModes, false);
            if (result.isResolved())
                return result;
            ReflectiveOperationException ex = result.makeAccessException();
            if (ex instanceof IllegalAccessException iae) throw iae;
            throw nsmClass.cast(ex);
        }
        /** Produce a resolved version of the given member.
         *  Super types are searched (for inherited members) if {@code searchSupers} is true.
         *  Access checking is performed on behalf of the given {@code lookupClass}.
         *  If lookup fails or access is not permitted, return null.
         *  Otherwise a fresh copy of the given member is returned, with modifier bits filled in.
         */
        public MemberName resolveOrNull(byte refKind, MemberName m, Class<?> lookupClass, int allowedModes) {
            assert lookupClass != null || allowedModes == LM_TRUSTED;
            MemberName result = resolve(refKind, m, lookupClass, allowedModes, true);
            if (result != null && result.isResolved())
                return result;
            return null;
        }
    }
}
