/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCBindingPattern;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;


public class MatchBindingsComputer extends TreeScanner {
    protected static final Context.Key<MatchBindingsComputer> matchBindingsComputerKey = new Context.Key<>();

    private final Log log;
    private final Types types;
    boolean whenTrue;
    List<BindingSymbol> bindings;

    public static MatchBindingsComputer instance(Context context) {
        MatchBindingsComputer instance = context.get(matchBindingsComputerKey);
        if (instance == null)
            instance = new MatchBindingsComputer(context);
        return instance;
    }

    protected MatchBindingsComputer(Context context) {
        this.log = Log.instance(context);
        this.types = Types.instance(context);
    }

    public List<BindingSymbol> getMatchBindings(JCTree expression, boolean whenTrue) {
        this.whenTrue = whenTrue;
        this.bindings = List.nil();
        scan(expression);
        return bindings;
    }

    @Override
    public void visitBindingPattern(JCBindingPattern tree) {
        bindings = whenTrue ? List.of(tree.symbol) : List.nil();
    }

    @Override
    public void visitBinary(JCBinary tree) {
        switch (tree.getTag()) {
            case AND:
                // e.T = union(x.T, y.T)
                // e.F = intersection(x.F, y.F)
                scan(tree.lhs);
                List<BindingSymbol> lhsBindings = bindings;
                scan(tree.rhs);
                List<BindingSymbol> rhsBindings = bindings;
                bindings = whenTrue ? union(tree, lhsBindings, rhsBindings) : intersection(tree, lhsBindings, rhsBindings);
                break;
            case OR:
                // e.T = intersection(x.T, y.T)
                // e.F = union(x.F, y.F)
                scan(tree.lhs);
                lhsBindings = bindings;
                scan(tree.rhs);
                rhsBindings = bindings;
                bindings = whenTrue ? intersection(tree, lhsBindings, rhsBindings) : union(tree, lhsBindings, rhsBindings);
                break;
            default:
                super.visitBinary(tree);
                break;
        }
    }

    @Override
    public void visitUnary(JCUnary tree) {
        switch (tree.getTag()) {
            case NOT:
                // e.T = x.F  // flip 'em
                // e.F = x.T
                whenTrue = !whenTrue;
                scan(tree.arg);
                whenTrue = !whenTrue;
                break;
            default:
                super.visitUnary(tree);
                break;
        }
    }

    @Override
    public void visitConditional(JCConditional tree) {
        /* if e = "x ? y : z", then:
               e.T = union(intersect(y.T, z.T), intersect(x.T, z.T), intersect(x.F, y.T))
               e.F = union(intersect(y.F, z.F), intersect(x.T, z.F), intersect(x.F, y.F))
        */
        if (whenTrue) {
            List<BindingSymbol> xT, yT, zT, xF;
            scan(tree.cond);
            xT = bindings;
            scan(tree.truepart);
            yT = bindings;
            scan(tree.falsepart);
            zT = bindings;
            whenTrue = false;
            scan(tree.cond);
            xF = bindings;
            whenTrue = true;
            bindings = union(tree, intersection(tree, yT, zT), intersection(tree, xT, zT), intersection(tree, xF, yT));
        } else {
            List<BindingSymbol> xF, yF, zF, xT;
            scan(tree.cond);
            xF = bindings;
            scan(tree.truepart);
            yF = bindings;
            scan(tree.falsepart);
            zF = bindings;
            whenTrue = true;
            scan(tree.cond);
            xT = bindings;
            whenTrue = false;
            bindings = union(tree, intersection(tree, yF, zF), intersection(tree, xT, zF), intersection(tree, xF, yF));
        }
    }

    private List<BindingSymbol> intersection(JCTree tree, List<BindingSymbol> lhsBindings, List<BindingSymbol> rhsBindings) {
        // It is an error if, for intersection(a,b), if a and b contain the same variable name (may be eventually relaxed to merge variables of same type)
        List<BindingSymbol> list = List.nil();
        for (BindingSymbol v1 : lhsBindings) {
            for (BindingSymbol v2 : rhsBindings) {
                if (v1.name == v2.name) {
                    log.error(tree.pos(), Errors.MatchBindingExists);
                    list = list.append(v2);
                }
            }
        }
        return list;
    }

    @SafeVarargs
    private final List<BindingSymbol> union(JCTree tree, List<BindingSymbol> lhsBindings, List<BindingSymbol> ... rhsBindings_s) {
        // It is an error if for union(a,b), a and b contain the same name (disjoint union).
        List<BindingSymbol> list = lhsBindings;
        for (List<BindingSymbol> rhsBindings : rhsBindings_s) {
            for (BindingSymbol v : rhsBindings) {
                for (BindingSymbol ov : list) {
                    if (ov.name == v.name) {
                        log.error(tree.pos(), Errors.MatchBindingExists);
                    }
                }
                list = list.append(v);
            }
        }
        return list;
    }

    @Override
    public void scan(JCTree tree) {
        bindings = List.nil();
        super.scan(tree);
    }

    public static class BindingSymbol extends VarSymbol {

        public BindingSymbol(Name name, Type type, Symbol owner) {
            super(Flags.FINAL | Flags.HASINIT | Flags.MATCH_BINDING, name, type, owner);
        }

        public boolean isAliasFor(BindingSymbol b) {
            return aliases().containsAll(b.aliases());
        }

        List<BindingSymbol> aliases() {
            return List.of(this);
        }

        public void preserveBinding() {
            flags_field |= Flags.MATCH_BINDING_TO_OUTER;
        }

        public boolean isPreserved() {
            return (flags_field & Flags.MATCH_BINDING_TO_OUTER) != 0;
        }
    }

}
