/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.file.JavacFileManager;

/**
 * Test harness whose goal is to simplify the task of writing type-system
 * regression test. It provides functionalities to build custom types as well
 * as to access the underlying javac's symbol table in order to retrieve
 * predefined types. Among the features supported by the harness are: type
 * substitution, type containment, subtyping, cast-conversion, assigment
 * conversion.
 *
 * This class is meant to be a common super class for all concrete type test
 * classes. A subclass can access the type-factory and the test methods so as
 * to write compact tests. An example is reported below:
 *
 * <pre>
 * Type X = fac.TypeVariable();
 * Type Y = fac.TypeVariable();
 * Type A_X_Y = fac.Class(0, X, Y);
 * Type A_Obj_Obj = fac.Class(0,
 *           predef.objectType,
 *           predef.objectType);
 * checkSameType(A_Obj_Obj, subst(A_X_Y,
 *           Mapping(X, predef.objectType),
 *           Mapping(Y, predef.objectType)));
 * </pre>
 *
 * The above code is used to create two class types, namely {@code A<X,Y>} and
 * {@code A<Object,Object>} where both {@code X} and {@code Y} are type-variables.
 * The code then verifies that {@code [X:=Object,Y:=Object]A<X,Y> == A<Object,Object>}.
 *
 * @author mcimadamore
 */
public class TypeHarness {

    protected Types types;
    protected Check chk;
    protected Symtab predef;
    protected Names names;
    protected Factory fac;

    protected TypeHarness() {
        Context ctx = new Context();
        JavacFileManager.preRegister(ctx);
        types = Types.instance(ctx);
        chk = Check.instance(ctx);
        predef = Symtab.instance(ctx);
        names = Names.instance(ctx);
        fac = new Factory();
    }

    // <editor-fold defaultstate="collapsed" desc="type assertions">

    /** assert that 's' is a subtype of 't' */
    public void assertSubtype(Type s, Type t) {
        assertSubtype(s, t, true);
    }

    /** assert that 's' is/is not a subtype of 't' */
    public void assertSubtype(Type s, Type t, boolean expected) {
        if (types.isSubtype(s, t) != expected) {
            String msg = expected ?
                " is not a subtype of " :
                " is a subtype of ";
            error(s + msg + t);
        }
    }

    /** assert that 's' is the same type as 't' */
    public void assertSameType(Type s, Type t) {
        assertSameType(s, t, true);
    }

    /** assert that 's' is/is not the same type as 't' */
    public void assertSameType(Type s, Type t, boolean expected) {
        if (types.isSameType(s, t) != expected) {
            String msg = expected ?
                " is not the same type as " :
                " is the same type as ";
            error(s + msg + t);
        }
    }

    /** assert that 's' is castable to 't' */
    public void assertCastable(Type s, Type t) {
        assertCastable(s, t, true);
    }

    /** assert that 's' is/is not castable to 't' */
    public void assertCastable(Type s, Type t, boolean expected) {
        if (types.isCastable(s, t) != expected) {
            String msg = expected ?
                " is not castable to " :
                " is castable to ";
            error(s + msg + t);
        }
    }

    /** assert that 's' is convertible (method invocation conversion) to 't' */
    public void assertConvertible(Type s, Type t) {
        assertCastable(s, t, true);
    }

    /** assert that 's' is/is not convertible (method invocation conversion) to 't' */
    public void assertConvertible(Type s, Type t, boolean expected) {
        if (types.isConvertible(s, t) != expected) {
            String msg = expected ?
                " is not convertible to " :
                " is convertible to ";
            error(s + msg + t);
        }
    }

    /** assert that 's' is assignable to 't' */
    public void assertAssignable(Type s, Type t) {
        assertCastable(s, t, true);
    }

    /** assert that 's' is/is not assignable to 't' */
    public void assertAssignable(Type s, Type t, boolean expected) {
        if (types.isAssignable(s, t) != expected) {
            String msg = expected ?
                " is not assignable to " :
                " is assignable to ";
            error(s + msg + t);
        }
    }

    /** assert that generic type 't' is well-formed */
    public void assertValidGenericType(Type t) {
        assertValidGenericType(t, true);
    }

    /** assert that 's' is/is not assignable to 't' */
    public void assertValidGenericType(Type t, boolean expected) {
        if (chk.checkValidGenericType(t) != expected) {
            String msg = expected ?
                " is not a valid generic type" :
                " is a valid generic type";
            error(t + msg + "   " + t.tsym.type);
        }
    }
    // </editor-fold>

    private void error(String msg) {
        throw new AssertionError("Unexpected result: " + msg);
    }

    // <editor-fold defaultstate="collapsed" desc="type functions">

    /** compute the erasure of a type 't' */
    public Type erasure(Type t) {
        return types.erasure(t);
    }

    /** compute the capture of a type 't' */
    public Type capture(Type t) {
        return types.capture(t);
    }

    /** compute the boxed type associated with 't' */
    public Type box(Type t) {
        if (!t.isPrimitive()) {
            throw new AssertionError("Cannot box non-primitive type: " + t);
        }
        return types.boxedClass(t).type;
    }

    /** compute the unboxed type associated with 't' */
    public Type unbox(Type t) {
        Type u = types.unboxedType(t);
        if (t == null) {
            throw new AssertionError("Cannot unbox reference type: " + t);
        } else {
            return u;
        }
    }

    /** compute a type substitution on 't' given a list of type mappings */
    public Type subst(Type t, Mapping... maps) {
        ListBuffer<Type> from = new ListBuffer<>();
        ListBuffer<Type> to = new ListBuffer<>();
        for (Mapping tm : maps) {
            from.append(tm.from);
            to.append(tm.to);
        }
        return types.subst(t, from.toList(), to.toList());
    }

    /** create a fresh type mapping from a type to another */
    public Mapping Mapping(Type from, Type to) {
        return new Mapping(from, to);
    }

    public static class Mapping {
        Type from;
        Type to;
        private Mapping(Type from, Type to) {
            this.from = from;
            this.to = to;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="type factory">

    /**
     * This class is used to create Java types in a simple way. All main
     * kinds of type are supported: primitive, reference, non-denotable. The
     * factory also supports creation of constant types (used by the compiler
     * to represent the type of a literal).
     */
    public class Factory {

        private int synthNameCount = 0;

        private Name syntheticName() {
            return names.fromString("A$" + synthNameCount++);
        }

        public ClassType Class(long flags, Type... typeArgs) {
            ClassSymbol csym = new ClassSymbol(flags, syntheticName(), predef.noSymbol);
            csym.type = new ClassType(Type.noType, List.from(typeArgs), csym);
            ((ClassType)csym.type).supertype_field = predef.objectType;
            return (ClassType)csym.type;
        }

        public ClassType Class(Type... typeArgs) {
            return Class(0, typeArgs);
        }

        public ClassType Interface(Type... typeArgs) {
            return Class(Flags.INTERFACE, typeArgs);
        }

        public ClassType Interface(long flags, Type... typeArgs) {
            return Class(Flags.INTERFACE | flags, typeArgs);
        }

        public Type Constant(byte b) {
            return predef.byteType.constType(b);
        }

        public Type Constant(short s) {
            return predef.shortType.constType(s);
        }

        public Type Constant(int i) {
            return predef.intType.constType(i);
        }

        public Type Constant(long l) {
            return predef.longType.constType(l);
        }

        public Type Constant(float f) {
            return predef.floatType.constType(f);
        }

        public Type Constant(double d) {
            return predef.doubleType.constType(d);
        }

        public Type Constant(char c) {
            return predef.charType.constType(c + 0);
        }

        public ArrayType Array(Type elemType) {
            return new ArrayType(elemType, predef.arrayClass);
        }

        public TypeVar TypeVariable() {
            return TypeVariable(predef.objectType);
        }

        public TypeVar TypeVariable(Type bound) {
            TypeSymbol tvsym = new TypeVariableSymbol(0, syntheticName(), null, predef.noSymbol);
            tvsym.type = new TypeVar(tvsym, bound, null);
            return (TypeVar)tvsym.type;
        }

        public WildcardType Wildcard(BoundKind bk, Type bound) {
            return new WildcardType(bound, bk, predef.boundClass);
        }

        public CapturedType CapturedVariable(Type upper, Type lower) {
            return new CapturedType(syntheticName(), predef.noSymbol, upper, lower, null);
        }

        public ClassType Intersection(Type classBound, Type... intfBounds) {
            ClassType ct = Class(Flags.COMPOUND);
            ct.supertype_field = classBound;
            ct.interfaces_field = List.from(intfBounds);
            return ct;
        }
    }
    // </editor-fold>
}
