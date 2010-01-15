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

package sun.dyn;

import sun.dyn.util.BytecodeDescriptor;
import java.dyn.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static sun.dyn.MethodHandleNatives.Constants.*;

/**
 * Compact information which fully characterizes a method or field reference.
 * When resolved, it includes a direct pointer to JVM metadata.
 * This representation is stateless and only decriptive.
 * It provides no private information and no capability to use the member.
 * <p>
 * By contrast, a java.lang.reflect.Method contains fuller information
 * about the internals of a method (except its bytecodes) and also
 * allows invocation.  A MemberName is much lighter than a reflect.Method,
 * since it contains about 7 fields to Method's 16 (plus its sub-arrays),
 * and those seven fields omit much of the information in Method.
 * @author jrose
 */
public final class MemberName implements Member, Cloneable {
    private Class<?>   clazz;       // class in which the method is defined
    private String     name;        // may be null if not yet materialized
    private Object     type;        // may be null if not yet materialized
    private int        flags;       // modifier bits; see reflect.Modifier

    private Object     vmtarget;    // VM-specific target value
    private int        vmindex;     // method index within class or interface

    { vmindex = VM_INDEX_UNINITIALIZED; }

    public Class<?> getDeclaringClass() {
        if (clazz == null && isResolved()) {
            expandFromVM();
        }
        return clazz;
    }

    public ClassLoader getClassLoader() {
        return clazz.getClassLoader();
    }

    public String getName() {
        if (name == null) {
            expandFromVM();
            if (name == null)  return null;
        }
        return name;
    }

    public MethodType getMethodType() {
        if (type == null) {
            expandFromVM();
            if (type == null)  return null;
        }
        if (!isInvocable())
            throw newIllegalArgumentException("not invocable, no method type");
        if (type instanceof MethodType) {
            return (MethodType) type;
        }
        if (type instanceof String) {
            String sig = (String) type;
            MethodType res = MethodType.fromMethodDescriptorString(sig, getClassLoader());
            this.type = res;
            return res;
        }
        if (type instanceof Object[]) {
            Object[] typeInfo = (Object[]) type;
            Class<?>[] ptypes = (Class<?>[]) typeInfo[1];
            Class<?> rtype = (Class<?>) typeInfo[0];
            MethodType res = MethodType.methodType(rtype, ptypes);
            this.type = res;
            return res;
        }
        throw new InternalError("bad method type "+type);
    }

    public MethodType getInvocationType() {
        MethodType itype = getMethodType();
        if (!isStatic())
            itype = itype.insertParameterTypes(0, clazz);
        return itype;
    }

    public Class<?>[] getParameterTypes() {
        return getMethodType().parameterArray();
    }

    public Class<?> getReturnType() {
        return getMethodType().returnType();
    }

    public Class<?> getFieldType() {
        if (type == null) {
            expandFromVM();
            if (type == null)  return null;
        }
        if (isInvocable())
            throw newIllegalArgumentException("not a field or nested class, no simple type");
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof String) {
            String sig = (String) type;
            MethodType mtype = MethodType.fromMethodDescriptorString("()"+sig, getClassLoader());
            Class<?> res = mtype.returnType();
            this.type = res;
            return res;
        }
        throw new InternalError("bad field type "+type);
    }

    public Object getType() {
        return (isInvocable() ? getMethodType() : getFieldType());
    }

    public String getSignature() {
        if (type == null) {
            expandFromVM();
            if (type == null)  return null;
        }
        if (type instanceof String)
            return (String) type;
        if (isInvocable())
            return BytecodeDescriptor.unparse(getMethodType());
        else
            return BytecodeDescriptor.unparse(getFieldType());
    }

    public int getModifiers() {
        return (flags & RECOGNIZED_MODIFIERS);
    }

    private void setFlags(int flags) {
        this.flags = flags;
        assert(testAnyFlags(ALL_KINDS));
    }

    private boolean testFlags(int mask, int value) {
        return (flags & mask) == value;
    }
    private boolean testAllFlags(int mask) {
        return testFlags(mask, mask);
    }
    private boolean testAnyFlags(int mask) {
        return !testFlags(mask, 0);
    }

    public boolean isStatic() {
        return Modifier.isStatic(flags);
    }
    public boolean isPublic() {
        return Modifier.isPublic(flags);
    }
    public boolean isPrivate() {
        return Modifier.isPrivate(flags);
    }
    public boolean isProtected() {
        return Modifier.isProtected(flags);
    }
    public boolean isFinal() {
        return Modifier.isFinal(flags);
    }
    public boolean isAbstract() {
        return Modifier.isAbstract(flags);
    }
    // let the rest (native, volatile, transient, etc.) be tested via Modifier.isFoo

    // unofficial modifier flags, used by HotSpot:
    static final int BRIDGE    = 0x00000040;
    static final int VARARGS   = 0x00000080;
    static final int SYNTHETIC = 0x00001000;
    static final int ANNOTATION= 0x00002000;
    static final int ENUM      = 0x00004000;
    public boolean isBridge() {
        return testAllFlags(IS_METHOD | BRIDGE);
    }
    public boolean isVarargs() {
        return testAllFlags(VARARGS) && isInvocable();
    }
    public boolean isSynthetic() {
        return testAllFlags(SYNTHETIC);
    }

    static final String CONSTRUCTOR_NAME = "<init>";  // the ever-popular

    // modifiers exported by the JVM:
    static final int RECOGNIZED_MODIFIERS = 0xFFFF;

    // private flags, not part of RECOGNIZED_MODIFIERS:
    static final int
            IS_METHOD      = MN_IS_METHOD,      // method (not constructor)
            IS_CONSTRUCTOR = MN_IS_CONSTRUCTOR, // constructor
            IS_FIELD       = MN_IS_FIELD,       // field
            IS_TYPE        = MN_IS_TYPE;        // nested type
    static final int  // for MethodHandleNatives.getMembers
            SEARCH_SUPERCLASSES = MN_SEARCH_SUPERCLASSES,
            SEARCH_INTERFACES   = MN_SEARCH_INTERFACES;

    static final int ALL_ACCESS = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED;
    static final int ALL_KINDS = IS_METHOD | IS_CONSTRUCTOR | IS_FIELD | IS_TYPE;
    static final int IS_INVOCABLE = IS_METHOD | IS_CONSTRUCTOR;
    static final int IS_FIELD_OR_METHOD = IS_METHOD | IS_FIELD;
    static final int SEARCH_ALL_SUPERS = SEARCH_SUPERCLASSES | SEARCH_INTERFACES;

    public boolean isInvocable() {
        return testAnyFlags(IS_INVOCABLE);
    }
    public boolean isFieldOrMethod() {
        return testAnyFlags(IS_FIELD_OR_METHOD);
    }
    public boolean isMethod() {
        return testAllFlags(IS_METHOD);
    }
    public boolean isConstructor() {
        return testAllFlags(IS_CONSTRUCTOR);
    }
    public boolean isField() {
        return testAllFlags(IS_FIELD);
    }
    public boolean isType() {
        return testAllFlags(IS_TYPE);
    }
    public boolean isPackage() {
        return !testAnyFlags(ALL_ACCESS);
    }

    /** Initialize a query.   It is not resolved. */
    private void init(Class<?> defClass, String name, Object type, int flags) {
        // defining class is allowed to be null (for a naked name/type pair)
        name.toString();  // null check
        type.equals(type);  // null check
        // fill in fields:
        this.clazz = defClass;
        this.name = name;
        this.type = type;
        setFlags(flags);
        assert(!isResolved());
    }

    private void expandFromVM() {
        if (!isResolved())  return;
        if (type instanceof Object[])
            type = null;  // don't saddle JVM w/ typeInfo
        MethodHandleNatives.expand(this);
    }

    // Capturing information from the Core Reflection API:
    private static int flagsMods(int flags, int mods) {
        assert((flags & RECOGNIZED_MODIFIERS) == 0);
        assert((mods & ~RECOGNIZED_MODIFIERS) == 0);
        return flags | mods;
    }
    public MemberName(Method m) {
        Object[] typeInfo = { m.getReturnType(), m.getParameterTypes() };
        init(m.getDeclaringClass(), m.getName(), typeInfo, flagsMods(IS_METHOD, m.getModifiers()));
        // fill in vmtarget, vmindex while we have m in hand:
        MethodHandleNatives.init(this, m);
        assert(isResolved());
    }
    public MemberName(Constructor ctor) {
        Object[] typeInfo = { void.class, ctor.getParameterTypes() };
        init(ctor.getDeclaringClass(), CONSTRUCTOR_NAME, typeInfo, flagsMods(IS_CONSTRUCTOR, ctor.getModifiers()));
        // fill in vmtarget, vmindex while we have ctor in hand:
        MethodHandleNatives.init(this, ctor);
        assert(isResolved());
    }
    public MemberName(Field fld) {
        init(fld.getDeclaringClass(), fld.getName(), fld.getType(), flagsMods(IS_FIELD, fld.getModifiers()));
        // fill in vmtarget, vmindex while we have fld in hand:
        MethodHandleNatives.init(this, fld);
        assert(isResolved());
    }
    public MemberName(Class<?> type) {
        init(type.getDeclaringClass(), type.getSimpleName(), type, flagsMods(IS_TYPE, type.getModifiers()));
        vmindex = 0;  // isResolved
        assert(isResolved());
    }

    // bare-bones constructor; the JVM will fill it in
    MemberName() { }

    // locally useful cloner
    @Override protected MemberName clone() {
        try {
            return (MemberName) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InternalError();
        }
     }

    // %%% define equals/hashcode?

    // Construction from symbolic parts, for queries:
    public MemberName(Class<?> defClass, String name, Class<?> type, int modifiers) {
        init(defClass, name, type, IS_FIELD | (modifiers & RECOGNIZED_MODIFIERS));
    }
    public MemberName(Class<?> defClass, String name, Class<?> type) {
        this(defClass, name, type, 0);
    }
    public MemberName(Class<?> defClass, String name, MethodType type, int modifiers) {
        int flagBit = (name.equals(CONSTRUCTOR_NAME) ? IS_CONSTRUCTOR : IS_METHOD);
        init(defClass, name, type, flagBit | (modifiers & RECOGNIZED_MODIFIERS));
    }
    public MemberName(Class<?> defClass, String name, MethodType type) {
        this(defClass, name, type, 0);
    }

    boolean isResolved() {
        return (vmindex != VM_INDEX_UNINITIALIZED);
    }

    public boolean hasReceiverTypeDispatch() {
        return (isMethod() && getVMIndex(Access.TOKEN) >= 0);
    }

    @Override
    public String toString() {
        if (isType())
            return type.toString();  // class java.lang.String
        // else it is a field, method, or constructor
        StringBuilder buf = new StringBuilder();
        if (!isResolved())
            buf.append("*.");
        if (getDeclaringClass() != null) {
            buf.append(getName(clazz));
            buf.append('.');
        }
        buf.append(getName());
        if (!isInvocable())  buf.append('/');
        buf.append(getName(getType()));
        /*
        buf.append('/');
        // key: Public, private, pRotected, sTatic, Final, sYnchronized,
        // transient/Varargs, native, (interface), abstract, sTrict, sYnthetic,
        // (annotation), Enum, (unused)
        final String FIELD_MOD_CHARS  = "PprTF?vt????Y?E?";
        final String METHOD_MOD_CHARS = "PprTFybVn?atY???";
        String modChars = (isInvocable() ? METHOD_MOD_CHARS : FIELD_MOD_CHARS);
        for (int i = 0; i < modChars.length(); i++) {
            if ((flags & (1 << i)) != 0) {
                char mc = modChars.charAt(i);
                if (mc != '.')
                    buf.append(mc);
            }
        }
         */
        return buf.toString();
    }
    private static String getName(Object obj) {
        if (obj instanceof Class<?>)
            return ((Class<?>)obj).getName();
        return String.valueOf(obj);
    }

    // Queries to the JVM:
    public int getVMIndex(Access token) {
        Access.check(token);
        if (!isResolved())
            throw newIllegalStateException("not resolved");
        return vmindex;
    }
//    public Object getVMTarget(Access token) {
//        Access.check(token);
//        if (!isResolved())
//            throw newIllegalStateException("not resolved");
//        return vmtarget;
//    }
    private RuntimeException newIllegalStateException(String message) {
        return new IllegalStateException(message+": "+this);
    }

    // handy shared exception makers (they simplify the common case code)
    public static RuntimeException newIllegalArgumentException(String message) {
        return new IllegalArgumentException(message);
    }
    public static NoAccessException newNoAccessException(MemberName name, Class<?> lookupClass) {
        return newNoAccessException("cannot access", name, lookupClass);
    }
    public static NoAccessException newNoAccessException(MemberName name, MethodHandles.Lookup lookup) {
        return newNoAccessException(name, lookup.lookupClass());
    }
    public static NoAccessException newNoAccessException(String message,
            MemberName name, Class<?> lookupClass) {
        message += ": " + name;
        if (lookupClass != null)  message += ", from " + lookupClass.getName();
        return new NoAccessException(message);
    }

    /** Actually making a query requires an access check. */
    public static Factory getFactory(Access token) {
        Access.check(token);
        return Factory.INSTANCE;
    }
    public static Factory getFactory() {
        return getFactory(Access.getToken());
    }
    public static class Factory {
        private Factory() { } // singleton pattern
        static Factory INSTANCE = new Factory();

        private static int ALLOWED_FLAGS = SEARCH_ALL_SUPERS | ALL_KINDS;

        /// Queries
        List<MemberName> getMembers(Class<?> defc,
                String matchName, Object matchType,
                int matchFlags, Class<?> lookupClass) {
            matchFlags &= ALLOWED_FLAGS;
            String matchSig = null;
            if (matchType != null) {
                matchSig = BytecodeDescriptor.unparse(matchType);
                if (matchSig.startsWith("("))
                    matchFlags &= ~(ALL_KINDS & ~IS_INVOCABLE);
                else
                    matchFlags &= ~(ALL_KINDS & ~IS_FIELD);
            }
            final int BUF_MAX = 0x2000;
            int len1 = matchName == null ? 10 : matchType == null ? 4 : 1;
            MemberName[] buf = newMemberBuffer(len1);
            int totalCount = 0;
            ArrayList<MemberName[]> bufs = null;
            int bufCount = 0;
            for (;;) {
                bufCount = MethodHandleNatives.getMembers(defc,
                        matchName, matchSig, matchFlags,
                        lookupClass,
                        totalCount, buf);
                if (bufCount <= buf.length) {
                    if (bufCount < 0)  bufCount = 0;
                    totalCount += bufCount;
                    break;
                }
                // JVM returned to us with an intentional overflow!
                totalCount += buf.length;
                int excess = bufCount - buf.length;
                if (bufs == null)  bufs = new ArrayList<MemberName[]>(1);
                bufs.add(buf);
                int len2 = buf.length;
                len2 = Math.max(len2, excess);
                len2 = Math.max(len2, totalCount / 4);
                buf = newMemberBuffer(Math.min(BUF_MAX, len2));
            }
            ArrayList<MemberName> result = new ArrayList<MemberName>(totalCount);
            if (bufs != null) {
                for (MemberName[] buf0 : bufs) {
                    Collections.addAll(result, buf0);
                }
            }
            result.addAll(Arrays.asList(buf).subList(0, bufCount));
            // Signature matching is not the same as type matching, since
            // one signature might correspond to several types.
            // So if matchType is a Class or MethodType, refilter the results.
            if (matchType != null && matchType != matchSig) {
                for (Iterator<MemberName> it = result.iterator(); it.hasNext();) {
                    MemberName m = it.next();
                    if (!matchType.equals(m.getType()))
                        it.remove();
                }
            }
            return result;
        }
        boolean resolveInPlace(MemberName m, boolean searchSupers, Class<?> lookupClass) {
            MethodHandleNatives.resolve(m, lookupClass);
            if (m.isResolved())  return true;
            int matchFlags = m.flags | (searchSupers ? SEARCH_ALL_SUPERS : 0);
            String matchSig = m.getSignature();
            MemberName[] buf = { m };
            int n = MethodHandleNatives.getMembers(m.getDeclaringClass(),
                    m.getName(), matchSig, matchFlags, lookupClass, 0, buf);
            if (n != 1)  return false;
            return m.isResolved();
        }
        public MemberName resolveOrNull(MemberName m, boolean searchSupers, Class<?> lookupClass) {
            MemberName result = m.clone();
            if (resolveInPlace(result, searchSupers, lookupClass))
                return result;
            return null;
        }
        public MemberName resolveOrFail(MemberName m, boolean searchSupers, Class<?> lookupClass) {
            MemberName result = resolveOrNull(m, searchSupers, lookupClass);
            if (result != null)
                return result;
            throw newNoAccessException(m, lookupClass);
        }
        public List<MemberName> getMethods(Class<?> defc, boolean searchSupers,
                Class<?> lookupClass) {
            return getMethods(defc, searchSupers, null, null, lookupClass);
        }
        public List<MemberName> getMethods(Class<?> defc, boolean searchSupers,
                String name, MethodType type, Class<?> lookupClass) {
            int matchFlags = IS_METHOD | (searchSupers ? SEARCH_ALL_SUPERS : 0);
            return getMembers(defc, name, type, matchFlags, lookupClass);
        }
        public List<MemberName> getConstructors(Class<?> defc, Class<?> lookupClass) {
            return getMembers(defc, null, null, IS_CONSTRUCTOR, lookupClass);
        }
        public List<MemberName> getFields(Class<?> defc, boolean searchSupers,
                Class<?> lookupClass) {
            return getFields(defc, searchSupers, null, null, lookupClass);
        }
        public List<MemberName> getFields(Class<?> defc, boolean searchSupers,
                String name, Class<?> type, Class<?> lookupClass) {
            int matchFlags = IS_FIELD | (searchSupers ? SEARCH_ALL_SUPERS : 0);
            return getMembers(defc, name, type, matchFlags, lookupClass);
        }
        public List<MemberName> getNestedTypes(Class<?> defc, boolean searchSupers,
                Class<?> lookupClass) {
            int matchFlags = IS_TYPE | (searchSupers ? SEARCH_ALL_SUPERS : 0);
            return getMembers(defc, null, null, matchFlags, lookupClass);
        }
        private static MemberName[] newMemberBuffer(int length) {
            MemberName[] buf = new MemberName[length];
            // fill the buffer with dummy structs for the JVM to fill in
            for (int i = 0; i < length; i++)
                buf[i] = new MemberName();
            return buf;
        }
    }

//    static {
//        System.out.println("Hello world!  My methods are:");
//        System.out.println(Factory.INSTANCE.getMethods(MemberName.class, true, null));
//    }
}
