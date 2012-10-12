/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;

import com.sun.tools.javac.util.ArrayUtils;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.Name;

import java.util.*;

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

    /** Construct a pool with given number of elements and element array.
     */
    public Pool(int pp, Object[] pool) {
        this.pp = pp;
        this.pool = pool;
        this.indices = new HashMap<Object,Integer>(pool.length);
        for (int i = 1; i < pp; i++) {
            if (pool[i] != null) indices.put(pool[i], i);
        }
    }

    /** Construct an empty pool.
     */
    public Pool() {
        this(1, new Object[64]);
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
        if (value instanceof MethodSymbol)
            value = new Method((MethodSymbol)value);
        else if (value instanceof VarSymbol)
            value = new Variable((VarSymbol)value);
//      assert !(value instanceof Type.TypeVar);
        Integer index = indices.get(value);
        if (index == null) {
//          System.err.println("put " + value + " " + value.getClass());//DEBUG
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

    /** Return the given object's index in the pool,
     *  or -1 if object is not in there.
     */
    public int get(Object o) {
        Integer n = indices.get(o);
        return n == null ? -1 : n.intValue();
    }

    static class Method extends DelegatedSymbol {
        MethodSymbol m;
        Method(MethodSymbol m) {
            super(m);
            this.m = m;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Method)) return false;
            MethodSymbol o = ((Method)other).m;
            return
                o.name == m.name &&
                o.owner == m.owner &&
                o.type.equals(m.type);
        }
        public int hashCode() {
            return
                m.name.hashCode() * 33 +
                m.owner.hashCode() * 9 +
                m.type.hashCode();
        }
    }

    static class Variable extends DelegatedSymbol {
        VarSymbol v;
        Variable(VarSymbol v) {
            super(v);
            this.v = v;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Variable)) return false;
            VarSymbol o = ((Variable)other).v;
            return
                o.name == v.name &&
                o.owner == v.owner &&
                o.type.equals(v.type);
        }
        public int hashCode() {
            return
                v.name.hashCode() * 33 +
                v.owner.hashCode() * 9 +
                v.type.hashCode();
        }
    }

    public static class MethodHandle {

        /** Reference kind - see ClassFile */
        int refKind;

        /** Reference symbol */
        Symbol refSym;

        public MethodHandle(int refKind, Symbol refSym) {
            this.refKind = refKind;
            this.refSym = refSym;
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
                o.type.equals(refSym.type);
        }
        public int hashCode() {
            return
                refKind * 65 +
                refSym.name.hashCode() * 33 +
                refSym.owner.hashCode() * 9 +
                refSym.type.hashCode();
        }

        /**
         * Check consistency of reference kind and symbol (see JVMS 4.4.8)
         */
        @SuppressWarnings("fallthrough")
        private void checkConsistent() {
            boolean staticOk = false;
            int expectedKind = -1;
            Filter<Name> nameFilter = nonInitFilter;
            boolean interfaceOwner = false;
            switch (refKind) {
                case ClassFile.REF_getStatic:
                case ClassFile.REF_putStatic:
                    staticOk = true;
                case ClassFile.REF_getField:
                case ClassFile.REF_putField:
                    expectedKind = Kinds.VAR;
                    break;
                case ClassFile.REF_newInvokeSpecial:
                    nameFilter = initFilter;
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeInterface:
                    interfaceOwner = true;
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeStatic:
                    staticOk = true;
                case ClassFile.REF_invokeVirtual:
                case ClassFile.REF_invokeSpecial:
                    expectedKind = Kinds.MTH;
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
