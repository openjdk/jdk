/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import java.lang.reflect.Modifier;
import java.text.CollationKey;

import com.sun.javadoc.*;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Position;

/**
 * Represents a java program element: class, interface, field,
 * constructor, or method.
 * This is an abstract class dealing with information common to
 * these elements.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see MemberDocImpl
 * @see ClassDocImpl
 *
 * @author Robert Field
 * @author Neal Gafter (rewrite)
 * @author Scott Seligman (generics, enums, annotations)
 */
@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public abstract class ProgramElementDocImpl
        extends DocImpl implements ProgramElementDoc {

    private final Symbol sym;

    // For source position information.
    JCTree tree = null;
    Position.LineMap lineMap = null;


    // Cache for getModifiers().
    private int modifiers = -1;

    protected ProgramElementDocImpl(DocEnv env, Symbol sym, TreePath treePath) {
        super(env, treePath);
        this.sym = sym;
        if (treePath != null) {
            tree = (JCTree) treePath.getLeaf();
            lineMap = ((JCCompilationUnit) treePath.getCompilationUnit()).lineMap;
        }
    }

    @Override
    void setTreePath(TreePath treePath) {
        super.setTreePath(treePath);
        this.tree = (JCTree) treePath.getLeaf();
        this.lineMap = ((JCCompilationUnit) treePath.getCompilationUnit()).lineMap;
    }

    /**
     * Subclasses override to identify the containing class
     */
    protected abstract ClassSymbol getContainingClass();

    /**
     * Returns the flags in terms of javac's flags
     */
    abstract protected long getFlags();

    /**
     * Returns the modifier flags in terms of java.lang.reflect.Modifier.
     */
    protected int getModifiers() {
        if (modifiers == -1) {
            modifiers = DocEnv.translateModifiers(getFlags());
        }
        return modifiers;
    }

    /**
     * Get the containing class of this program element.
     *
     * @return a ClassDocImpl for this element's containing class.
     * If this is a class with no outer class, return null.
     */
    public ClassDoc containingClass() {
        if (getContainingClass() == null) {
            return null;
        }
        return env.getClassDoc(getContainingClass());
    }

    /**
     * Return the package that this member is contained in.
     * Return "" if in unnamed package.
     */
    public PackageDoc containingPackage() {
        return env.getPackageDoc(getContainingClass().packge());
    }

    /**
     * Get the modifier specifier integer.
     *
     * @see java.lang.reflect.Modifier
     */
    public int modifierSpecifier() {
        int modifiers = getModifiers();
        if (isMethod() && containingClass().isInterface())
            // Remove the implicit abstract modifier.
            return modifiers & ~Modifier.ABSTRACT;
        return modifiers;
    }

    /**
     * Get modifiers string.
     * <pre>
     * Example, for:
     *   public abstract int foo() { ... }
     * modifiers() would return:
     *   'public abstract'
     * </pre>
     * Annotations are not included.
     */
    public String modifiers() {
        int modifiers = getModifiers();
        if (isAnnotationTypeElement() ||
                (isMethod() && containingClass().isInterface())) {
            // Remove the implicit abstract modifier.
            return Modifier.toString(modifiers & ~Modifier.ABSTRACT);
        } else {
            return Modifier.toString(modifiers);
        }
    }

    /**
     * Get the annotations of this program element.
     * Return an empty array if there are none.
     */
    public AnnotationDesc[] annotations() {
        AnnotationDesc res[] = new AnnotationDesc[sym.getRawAttributes().length()];
        int i = 0;
        for (Attribute.Compound a : sym.getRawAttributes()) {
            res[i++] = new AnnotationDescImpl(env, a);
        }
        return res;
    }

    /**
     * Return true if this program element is public
     */
    public boolean isPublic() {
        int modifiers = getModifiers();
        return Modifier.isPublic(modifiers);
    }

    /**
     * Return true if this program element is protected
     */
    public boolean isProtected() {
        int modifiers = getModifiers();
        return Modifier.isProtected(modifiers);
    }

    /**
     * Return true if this program element is private
     */
    public boolean isPrivate() {
        int modifiers = getModifiers();
        return Modifier.isPrivate(modifiers);
    }

    /**
     * Return true if this program element is package private
     */
    public boolean isPackagePrivate() {
        return !(isPublic() || isPrivate() || isProtected());
    }

    /**
     * Return true if this program element is static
     */
    public boolean isStatic() {
        int modifiers = getModifiers();
        return Modifier.isStatic(modifiers);
    }

    /**
     * Return true if this program element is final
     */
    public boolean isFinal() {
        int modifiers = getModifiers();
        return Modifier.isFinal(modifiers);
    }

    /**
     * Generate a key for sorting.
     */
    CollationKey generateKey() {
        String k = name();
        // System.out.println("COLLATION KEY FOR " + this + " is \"" + k + "\"");
        return env.doclocale.collator.getCollationKey(k);
    }

}
