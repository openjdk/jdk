/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.HashSet;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.util.JavacMessages;
import java.util.Locale;
import java.util.Set;
import java.util.function.BinaryOperator;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.CapturedType;
import com.sun.tools.javac.code.Type.StructuralTypeMapping;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.Types.SimpleVisitor;
import com.sun.tools.javac.util.List;
import static com.sun.tools.javac.code.BoundKind.EXTENDS;
import static com.sun.tools.javac.code.BoundKind.SUPER;
import static com.sun.tools.javac.code.BoundKind.UNBOUND;
import static com.sun.tools.javac.code.Type.ArrayType;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.code.TypeTag.WILDCARD;

/**
 * Print variable types in source form.
 * TypeProjection and CaptureScanner are copied from Types in the JEP-286
 * Sandbox by Maurizio.  The checks for Non-Denotable in TypePrinter are
 * cribbed from denotableChecker of the same source.
 *
 * @author Maurizio Cimadamore
 * @author Robert Field
 */
class VarTypePrinter extends TypePrinter {
    private static final String WILD = "?";

    private final Symtab syms;
    private final Types types;

    VarTypePrinter(JavacMessages messages, BinaryOperator<String> fullClassNameAndPackageToClass,
            Symtab syms, Types types) {
        super(messages, fullClassNameAndPackageToClass);
        this.syms = syms;
        this.types = types;
    }

    @Override
    String toString(Type t) {
        return super.toString(upward(t));
    }

    @Override
    public String visitTypeVar(TypeVar t, Locale locale) {
        /* Any type variable mentioned in the inferred type must have been declared as a type parameter
                  (i.e cannot have been produced by inference (18.4))
         */
        // and beyond that, there are no global type vars, so if there are any
        // type variables left, they need to be eliminated
        return WILD; // Non-denotable
    }

    @Override
    public String visitCapturedType(CapturedType t, Locale locale) {
        /* Any type variable mentioned in the inferred type must have been declared as a type parameter
                  (i.e cannot have been produced by capture conversion (5.1.10))
         */
        return WILD; // Non-denotable
    }

    public Type upward(Type t) {
        List<Type> captures = captures(t);
        return upward(t, captures);
    }

    /************* Following from JEP-286 Types.java ***********/

    public Type upward(Type t, List<Type> vars) {
        return t.map(new TypeProjection(vars), true);
    }

    public List<Type> captures(Type t) {
        CaptureScanner cs = new CaptureScanner();
        Set<Type> captures = new HashSet<>();
        cs.visit(t, captures);
        return List.from(captures);
    }

    class CaptureScanner extends SimpleVisitor<Void, Set<Type>> {

        @Override
        public Void visitType(Type t, Set<Type> types) {
            return null;
        }

        @Override
        public Void visitClassType(ClassType t, Set<Type> seen) {
            if (t.isCompound()) {
                types.directSupertypes(t).forEach(s -> visit(s, seen));
            } else {
                t.allparams().forEach(ta -> visit(ta, seen));
            }
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType t, Set<Type> seen) {
            return visit(t.elemtype, seen);
        }

        @Override
        public Void visitWildcardType(WildcardType t, Set<Type> seen) {
            visit(t.type, seen);
            return null;
        }

        @Override
        public Void visitTypeVar(TypeVar t, Set<Type> seen) {
            if ((t.tsym.flags() & Flags.SYNTHETIC) != 0 && seen.add(t)) {
                visit(t.getUpperBound(), seen);
            }
            return null;
        }

        @Override
        public Void visitCapturedType(CapturedType t, Set<Type> seen) {
            if (seen.add(t)) {
                visit(t.getUpperBound(), seen);
                visit(t.getLowerBound(), seen);
            }
            return null;
        }
    }

    class TypeProjection extends StructuralTypeMapping<Boolean> {

        List<Type> vars;
        Set<Type> seen = new HashSet<>();

        public TypeProjection(List<Type> vars) {
            this.vars = vars;
        }

        @Override
        public Type visitClassType(ClassType t, Boolean upward) {
            if (upward && !t.isCompound() && t.tsym.name.isEmpty()) {
                //lift anonymous class type to first supertype (class or interface)
                return types.directSupertypes(t).last();
            } else if (t.isCompound()) {
                List<Type> components = types.directSupertypes(t);
                List<Type> components1 = components.map(c -> c.map(this, upward));
                if (components == components1) return t;
                else return types.makeIntersectionType(components1);
            } else {
                Type outer = t.getEnclosingType();
                Type outer1 = visit(outer, upward);
                List<Type> typarams = t.getTypeArguments();
                List<Type> typarams1 = typarams.map(ta -> mapTypeArgument(ta, upward));
                if (typarams1.stream().anyMatch(ta -> ta.hasTag(BOT))) {
                    //not defined
                    return syms.botType;
                }
                if (outer1 == outer && typarams1 == typarams) return t;
                else return new ClassType(outer1, typarams1, t.tsym, t.getMetadata()) {
                    @Override
                    protected boolean needsStripping() {
                        return true;
                    }
                };
            }
        }

        protected Type makeWildcard(Type upper, Type lower) {
            BoundKind bk;
            Type bound;
            if (upper.hasTag(BOT)) {
                upper = syms.objectType;
            }
            boolean isUpperObject = types.isSameType(upper, syms.objectType);
            if (!lower.hasTag(BOT) && isUpperObject) {
                bound = lower;
                bk = SUPER;
            } else {
                bound = upper;
                bk = isUpperObject ? UNBOUND : EXTENDS;
            }
            return new WildcardType(bound, bk, syms.boundClass);
        }

        @Override
        public Type visitTypeVar(TypeVar t, Boolean upward) {
            if (vars.contains(t)) {
                try {
                    if (seen.add(t)) {
                        return (upward ?
                                t.getUpperBound() :
                                (t.getLowerBound() == null) ?
                                        syms.botType :
                                        t.getLowerBound())
                                    .map(this, upward);
                    } else {
                        //cycle
                        return syms.objectType;
                    }
                } finally {
                    seen.remove(t);
                }
            } else {
                return t;
            }
        }

        @Override
        public Type visitWildcardType(WildcardType wt, Boolean upward) {
            if (upward) {
                return wt.isExtendsBound() ?
                        wt.type.map(this, upward) :
                        syms.objectType;
            } else {
                return wt.isSuperBound() ?
                        wt.type.map(this, upward) :
                        syms.botType;
            }
        }

        private Type mapTypeArgument(Type t, boolean upward) {
            if (!t.containsAny(vars)) {
                return t;
            } else if (!t.hasTag(WILDCARD) && !upward) {
                //not defined
                return syms.botType;
            } else {
                Type upper = t.map(this, upward);
                Type lower = t.map(this, !upward);
                return makeWildcard(upper, lower);
            }
        }
    }
}
