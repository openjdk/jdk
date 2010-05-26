/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror.declaration;


import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collection;
import java.util.EnumSet;
import javax.tools.JavaFileObject;

import com.sun.mirror.declaration.*;
import com.sun.mirror.util.*;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.apt.mirror.util.SourcePositionImpl;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;

import static com.sun.mirror.declaration.Modifier.*;
import static com.sun.tools.javac.code.Kinds.*;


/**
 * Implementation of Declaration
 */
@SuppressWarnings("deprecation")
public abstract class DeclarationImpl implements Declaration {

    protected final AptEnv env;
    public final Symbol sym;

    protected static final DeclarationFilter identityFilter =
            new DeclarationFilter();


    /**
     * "sym" should be completed before this constructor is called.
     */
    protected DeclarationImpl(AptEnv env, Symbol sym) {
        this.env = env;
        this.sym = sym;
    }


    /**
     * {@inheritDoc}
     * <p> ParameterDeclarationImpl overrides this implementation.
     */
    public boolean equals(Object obj) {
        if (obj instanceof DeclarationImpl) {
            DeclarationImpl that = (DeclarationImpl) obj;
            return sym == that.sym && env == that.env;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * <p> ParameterDeclarationImpl overrides this implementation.
     */
    public int hashCode() {
        return sym.hashCode() + env.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String getDocComment() {
        // Our doc comment is contained in a map in our toplevel,
        // indexed by our tree.  Find our enter environment, which gives
        // us our toplevel.  It also gives us a tree that contains our
        // tree:  walk it to find our tree.  This is painful.
        Env<AttrContext> enterEnv = getEnterEnv();
        if (enterEnv == null)
            return null;
        JCTree tree = TreeInfo.declarationFor(sym, enterEnv.tree);
        return enterEnv.toplevel.docComments.get(tree);
    }

    /**
     * {@inheritDoc}
     */
    public Collection<AnnotationMirror> getAnnotationMirrors() {
        Collection<AnnotationMirror> res =
            new ArrayList<AnnotationMirror>();
        for (Attribute.Compound a : sym.getAnnotationMirrors()) {
            res.add(env.declMaker.getAnnotationMirror(a, this));
        }
        return res;
    }

    /**
     * {@inheritDoc}
     * Overridden by ClassDeclarationImpl to handle @Inherited.
     */
    public <A extends Annotation> A getAnnotation(Class<A> annoType) {
        return getAnnotation(annoType, sym);
    }

    protected <A extends Annotation> A getAnnotation(Class<A> annoType,
                                                     Symbol annotated) {
        if (!annoType.isAnnotation()) {
            throw new IllegalArgumentException(
                                "Not an annotation type: " + annoType);
        }
        String name = annoType.getName();
        for (Attribute.Compound attr : annotated.getAnnotationMirrors()) {
            if (name.equals(attr.type.tsym.flatName().toString())) {
                return AnnotationProxyMaker.generateAnnotation(env, attr,
                                                               annoType);
            }
        }
        return null;
    }

    // Cache for modifiers.
    private EnumSet<Modifier> modifiers = null;

    /**
     * {@inheritDoc}
     */
    public Collection<Modifier> getModifiers() {
        if (modifiers == null) {
            modifiers = EnumSet.noneOf(Modifier.class);
            long flags = AptEnv.getFlags(sym);

            if (0 != (flags & Flags.PUBLIC))       modifiers.add(PUBLIC);
            if (0 != (flags & Flags.PROTECTED))    modifiers.add(PROTECTED);
            if (0 != (flags & Flags.PRIVATE))      modifiers.add(PRIVATE);
            if (0 != (flags & Flags.ABSTRACT))     modifiers.add(ABSTRACT);
            if (0 != (flags & Flags.STATIC))       modifiers.add(STATIC);
            if (0 != (flags & Flags.FINAL))        modifiers.add(FINAL);
            if (0 != (flags & Flags.TRANSIENT))    modifiers.add(TRANSIENT);
            if (0 != (flags & Flags.VOLATILE))     modifiers.add(VOLATILE);
            if (0 != (flags & Flags.SYNCHRONIZED)) modifiers.add(SYNCHRONIZED);
            if (0 != (flags & Flags.NATIVE))       modifiers.add(NATIVE);
            if (0 != (flags & Flags.STRICTFP))     modifiers.add(STRICTFP);
        }
        return modifiers;
    }

    /**
     * {@inheritDoc}
     * Overridden in some subclasses.
     */
    public String getSimpleName() {
        return sym.name.toString();
    }

    /**
     * {@inheritDoc}
     */
    public SourcePosition getPosition() {
        // Find the toplevel.  From there use a tree-walking utility
        // that finds the tree for our symbol, and with it the position.
        Env<AttrContext> enterEnv = getEnterEnv();
        if (enterEnv == null)
            return null;
        JCTree.JCCompilationUnit toplevel = enterEnv.toplevel;
        JavaFileObject sourcefile = toplevel.sourcefile;
        if (sourcefile == null)
            return null;
        int pos = TreeInfo.positionFor(sym, toplevel);

        return new SourcePositionImpl(sourcefile, pos, toplevel.lineMap);
    }

    /**
     * Applies a visitor to this declaration.
     *
     * @param v the visitor operating on this declaration
     */
    public void accept(DeclarationVisitor v) {
        v.visitDeclaration(this);
    }


    private Collection<Symbol> members = null;  // cache for getMembers()

    /**
     * Returns the symbols of type or package members (and constructors)
     * that are not synthetic or otherwise unwanted.
     * Caches the result if "cache" is true.
     */
    protected Collection<Symbol> getMembers(boolean cache) {
        if (members != null) {
            return members;
        }
        LinkedList<Symbol> res = new LinkedList<Symbol>();
        for (Scope.Entry e = sym.members().elems; e != null; e = e.sibling) {
            if (e.sym != null && !unwanted(e.sym)) {
                res.addFirst(e.sym);
            }
        }
        return cache ? (members = res) : res;
    }

    /**
     * Tests whether this is a symbol that should never be seen by clients,
     * such as a synthetic class.
     * Note that a class synthesized by the compiler may not be flagged as
     * synthetic:  see bugid 4959932.
     */
    private static boolean unwanted(Symbol s) {
        return AptEnv.hasFlag(s, Flags.SYNTHETIC) ||
               (s.kind == TYP &&
                !DeclarationMaker.isJavaIdentifier(s.name.toString()));
    }

    /**
     * Returns this declaration's enter environment, or null if it
     * has none.
     */
    private Env<AttrContext> getEnterEnv() {
        // Get enclosing class of sym, or sym itself if it is a class
        // or package.
        TypeSymbol ts = (sym.kind != PCK)
                        ? sym.enclClass()
                        : (PackageSymbol) sym;
        return (ts != null)
                ? env.enter.getEnv(ts)
                : null;
    }
}
