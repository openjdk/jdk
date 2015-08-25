/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.Types.UniqueType;

import com.sun.tools.javac.util.ArrayUtils;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.Name;

import java.util.*;

import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;

/** An internal structure that corresponds to the constant pool of a classfile.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Pool {

    public static final int MAX_ENTRIES = 0xFFFF;
    public static final int MAX_STRING_LENGTH = 0xFFFF;

    /** Index of next constant to be entered.
     */
    int pp;

    /** The initial pool buffer.
     */
    Object[] pool;

    /** A hashtable containing all constants in the pool.
     */
    Map<Object,Integer> indices;

    Types types;

    /** Construct a pool with given number of elements and element array.
     */
    public Pool(int pp, Object[] pool, Types types) {
        this.pp = pp;
        this.pool = pool;
        this.types = types;
        this.indices = new HashMap<>(pool.length);
        for (int i = 1; i < pp; i++) {
            if (pool[i] != null) indices.put(pool[i], i);
        }
    }

    /** Construct an empty pool.
     */
    public Pool(Types types) {
        this(1, new Object[64], types);
    }

    /** Return the number of entries in the constant pool.
     */
    public int numEntries() {
        return pp;
    }

    /** Remove everything from this pool.
     */
    public void reset() {
        pp = 1;
        indices.clear();
    }

    /** Place an object in the pool, unless it is already there.
     *  If object is a symbol also enter its owner unless the owner is a
     *  package.  Return the object's index in the pool.
     */
    public int put(Object value) {
        value = makePoolValue(value);
        Assert.check(!(value instanceof Type.TypeVar));
        Assert.check(!(value instanceof Types.UniqueType &&
                       ((UniqueType) value).type instanceof Type.TypeVar));
        Integer index = indices.get(value);
        if (index == null) {
            index = pp;
            indices.put(value, index);
            pool = ArrayUtils.ensureCapacity(pool, pp);
            pool[pp++] = value;
            if (value instanceof Long || value instanceof Double) {
                pool = ArrayUtils.ensureCapacity(pool, pp);
                pool[pp++] = null;
            }
        }
        return index.intValue();
    }

    Object makePoolValue(Object o) {
        if (o instanceof DynamicMethodSymbol) {
            return new DynamicMethod((DynamicMethodSymbol)o, types);
        } else if (o instanceof MethodSymbol) {
            return new Method((MethodSymbol)o, types);
        } else if (o instanceof VarSymbol) {
            return new Variable((VarSymbol)o, types);
        } else if (o instanceof Type) {
            Type t = (Type)o;
            // ClassRefs can come from ClassSymbols or from Types.
            // Return the symbol for these types to avoid duplicates
            // in the constant pool
            if (t.hasTag(TypeTag.CLASS))
                return t.tsym;
            else
                return new UniqueType(t, types);
        } else {
            return o;
        }
    }

    /** Return the given object's index in the pool,
     *  or -1 if object is not in there.
     */
    public int get(Object o) {
        Integer n = indices.get(o);
        return n == null ? -1 : n.intValue();
    }

    static class Method extends DelegatedSymbol<MethodSymbol> {
        UniqueType uniqueType;
        Method(MethodSymbol m, Types types) {
            super(m);
            this.uniqueType = new UniqueType(m.type, types);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean equals(Object any) {
            if (!(any instanceof Method)) return false;
            MethodSymbol o = ((Method)any).other;
            MethodSymbol m = this.other;
            return
                o.name == m.name &&
                o.owner == m.owner &&
                ((Method)any).uniqueType.equals(uniqueType);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public int hashCode() {
            MethodSymbol m = this.other;
            return
                m.name.hashCode() * 33 +
                m.owner.hashCode() * 9 +
                uniqueType.hashCode();
        }
    }

    static class DynamicMethod extends Method {
        public Object[] uniqueStaticArgs;

        DynamicMethod(DynamicMethodSymbol m, Types types) {
            super(m, types);
            uniqueStaticArgs = getUniqueTypeArray(m.staticArgs, types);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean equals(Object any) {
            return equalsImpl(any, true);
        }

        protected boolean equalsImpl(Object any, boolean includeDynamicArgs) {
            if (includeDynamicArgs && !super.equals(any)) return false;
            if (!(any instanceof DynamicMethod)) return false;
            DynamicMethodSymbol dm1 = (DynamicMethodSymbol)other;
            DynamicMethodSymbol dm2 = (DynamicMethodSymbol)((DynamicMethod)any).other;
            return dm1.bsm == dm2.bsm &&
                        dm1.bsmKind == dm2.bsmKind &&
                        Arrays.equals(uniqueStaticArgs,
                            ((DynamicMethod)any).uniqueStaticArgs);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public int hashCode() {
            return hashCodeImpl(true);
        }

        protected int hashCodeImpl(boolean includeDynamicArgs) {
            int hash = includeDynamicArgs ? super.hashCode() : 0;
            DynamicMethodSymbol dm = (DynamicMethodSymbol)other;
            hash += dm.bsmKind * 7 +
                    dm.bsm.hashCode() * 11;
            for (int i = 0; i < dm.staticArgs.length; i++) {
                hash += (uniqueStaticArgs[i].hashCode() * 23);
            }
            return hash;
        }

        private Object[] getUniqueTypeArray(Object[] objects, Types types) {
            Object[] result = new Object[objects.length];
            for (int i = 0; i < objects.length; i++) {
                if (objects[i] instanceof Type) {
                    result[i] = new UniqueType((Type)objects[i], types);
                } else {
                    result[i] = objects[i];
                }
            }
            return result;
        }

        static class BootstrapMethodsKey extends DynamicMethod {
            BootstrapMethodsKey(DynamicMethodSymbol m, Types types) {
                super(m, types);
            }

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public boolean equals(Object any) {
                return equalsImpl(any, false);
            }

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public int hashCode() {
                return hashCodeImpl(false);
            }

            Object[] getUniqueArgs() {
                return uniqueStaticArgs;
            }
        }

        static class BootstrapMethodsValue {
            final MethodHandle mh;
            final int index;

            public BootstrapMethodsValue(MethodHandle mh, int index) {
                this.mh = mh;
                this.index = index;
            }
        }
    }

    static class Variable extends DelegatedSymbol<VarSymbol> {
        UniqueType uniqueType;
        Variable(VarSymbol v, Types types) {
            super(v);
            this.uniqueType = new UniqueType(v.type, types);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean equals(Object any) {
            if (!(any instanceof Variable)) return false;
            VarSymbol o = ((Variable)any).other;
            VarSymbol v = other;
            return
                o.name == v.name &&
                o.owner == v.owner &&
                ((Variable)any).uniqueType.equals(uniqueType);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public int hashCode() {
            VarSymbol v = other;
            return
                v.name.hashCode() * 33 +
                v.owner.hashCode() * 9 +
                uniqueType.hashCode();
        }
    }

    public static class MethodHandle {

        /** Reference kind - see ClassFile */
        int refKind;

        /** Reference symbol */
        Symbol refSym;

        UniqueType uniqueType;

        public MethodHandle(int refKind, Symbol refSym, Types types) {
            this.refKind = refKind;
            this.refSym = refSym;
            this.uniqueType = new UniqueType(this.refSym.type, types);
            checkConsistent();
        }
        public boolean equals(Object other) {
            if (!(other instanceof MethodHandle)) return false;
            MethodHandle mr = (MethodHandle) other;
            if (mr.refKind != refKind)  return false;
            Symbol o = mr.refSym;
            return
                o.name == refSym.name &&
                o.owner == refSym.owner &&
                ((MethodHandle)other).uniqueType.equals(uniqueType);
        }
        public int hashCode() {
            return
                refKind * 65 +
                refSym.name.hashCode() * 33 +
                refSym.owner.hashCode() * 9 +
                uniqueType.hashCode();
        }

        /**
         * Check consistency of reference kind and symbol (see JVMS 4.4.8)
         */
        @SuppressWarnings("fallthrough")
        private void checkConsistent() {
            boolean staticOk = false;
            Kind expectedKind = null;
            Filter<Name> nameFilter = nonInitFilter;
            boolean interfaceOwner = false;
            switch (refKind) {
                case ClassFile.REF_getStatic:
                case ClassFile.REF_putStatic:
                    staticOk = true;
                case ClassFile.REF_getField:
                case ClassFile.REF_putField:
                    expectedKind = VAR;
                    break;
                case ClassFile.REF_newInvokeSpecial:
                    nameFilter = initFilter;
                    expectedKind = MTH;
                    break;
                case ClassFile.REF_invokeInterface:
                    interfaceOwner = true;
                    expectedKind = MTH;
                    break;
                case ClassFile.REF_invokeStatic:
                    interfaceOwner = true;
                    staticOk = true;
                case ClassFile.REF_invokeVirtual:
                    expectedKind = MTH;
                    break;
                case ClassFile.REF_invokeSpecial:
                    interfaceOwner = true;
                    expectedKind = MTH;
                    break;
            }
            Assert.check(!refSym.isStatic() || staticOk);
            Assert.check(refSym.kind == expectedKind);
            Assert.check(nameFilter.accepts(refSym.name));
            Assert.check(!refSym.owner.isInterface() || interfaceOwner);
        }
        //where
                Filter<Name> nonInitFilter = new Filter<Name>() {
                    public boolean accepts(Name n) {
                        return n != n.table.names.init && n != n.table.names.clinit;
                    }
                };

                Filter<Name> initFilter = new Filter<Name>() {
                    public boolean accepts(Name n) {
                        return n == n.table.names.init;
                    }
                };
    }
}
