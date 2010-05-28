/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.api;

import java.io.IOException;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacMessager;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;

/**
 * Provides an implementation of Trees.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 */
public class JavacTrees extends Trees {

    private final Resolve resolve;
    private final Enter enter;
    private final Log log;
    private final MemberEnter memberEnter;
    private final Attr attr;
    private final TreeMaker treeMaker;
    private final JavacElements elements;
    private final JavacTaskImpl javacTaskImpl;

    public static JavacTrees instance(JavaCompiler.CompilationTask task) {
        if (!(task instanceof JavacTaskImpl))
            throw new IllegalArgumentException();
        return instance(((JavacTaskImpl)task).getContext());
    }

    public static JavacTrees instance(ProcessingEnvironment env) {
        if (!(env instanceof JavacProcessingEnvironment))
            throw new IllegalArgumentException();
        return instance(((JavacProcessingEnvironment)env).getContext());
    }

    public static JavacTrees instance(Context context) {
        JavacTrees instance = context.get(JavacTrees.class);
        if (instance == null)
            instance = new JavacTrees(context);
        return instance;
    }

    private JavacTrees(Context context) {
        context.put(JavacTrees.class, this);
        attr = Attr.instance(context);
        enter = Enter.instance(context);
        elements = JavacElements.instance(context);
        log = Log.instance(context);
        resolve = Resolve.instance(context);
        treeMaker = TreeMaker.instance(context);
        memberEnter = MemberEnter.instance(context);
        javacTaskImpl = context.get(JavacTaskImpl.class);
    }

    public SourcePositions getSourcePositions() {
        return new SourcePositions() {
                public long getStartPosition(CompilationUnitTree file, Tree tree) {
                    return TreeInfo.getStartPos((JCTree) tree);
                }

                public long getEndPosition(CompilationUnitTree file, Tree tree) {
                    Map<JCTree,Integer> endPositions = ((JCCompilationUnit) file).endPositions;
                    return TreeInfo.getEndPos((JCTree) tree, endPositions);
                }
            };
    }

    public JCClassDecl getTree(TypeElement element) {
        return (JCClassDecl) getTree((Element) element);
    }

    public JCMethodDecl getTree(ExecutableElement method) {
        return (JCMethodDecl) getTree((Element) method);
    }

    public JCTree getTree(Element element) {
        Symbol symbol = (Symbol) element;
        TypeSymbol enclosing = symbol.enclClass();
        Env<AttrContext> env = enter.getEnv(enclosing);
        if (env == null)
            return null;
        JCClassDecl classNode = env.enclClass;
        if (classNode != null) {
            if (TreeInfo.symbolFor(classNode) == element)
                return classNode;
            for (JCTree node : classNode.getMembers())
                if (TreeInfo.symbolFor(node) == element)
                    return node;
        }
        return null;
    }

    public JCTree getTree(Element e, AnnotationMirror a) {
        return getTree(e, a, null);
    }

    public JCTree getTree(Element e, AnnotationMirror a, AnnotationValue v) {
        Pair<JCTree, JCCompilationUnit> treeTopLevel = elements.getTreeAndTopLevel(e, a, v);
        if (treeTopLevel == null)
            return null;
        return treeTopLevel.fst;
    }

    public TreePath getPath(CompilationUnitTree unit, Tree node) {
        return TreePath.getPath(unit, node);
    }

    public TreePath getPath(Element e) {
        return getPath(e, null, null);
    }

    public TreePath getPath(Element e, AnnotationMirror a) {
        return getPath(e, a, null);
    }

    public TreePath getPath(Element e, AnnotationMirror a, AnnotationValue v) {
        final Pair<JCTree, JCCompilationUnit> treeTopLevel = elements.getTreeAndTopLevel(e, a, v);
        if (treeTopLevel == null)
            return null;
        return TreePath.getPath(treeTopLevel.snd, treeTopLevel.fst);
    }

    public Element getElement(TreePath path) {
        Tree t = path.getLeaf();
        return TreeInfo.symbolFor((JCTree) t);
    }

    public TypeMirror getTypeMirror(TreePath path) {
        Tree t = path.getLeaf();
        return ((JCTree)t).type;
    }

    public JavacScope getScope(TreePath path) {
        return new JavacScope(getAttrContext(path));
    }

    public boolean isAccessible(Scope scope, TypeElement type) {
        if (scope instanceof JavacScope && type instanceof ClassSymbol) {
            Env<AttrContext> env = ((JavacScope) scope).env;
            return resolve.isAccessible(env, (ClassSymbol)type);
        } else
            return false;
    }

    public boolean isAccessible(Scope scope, Element member, DeclaredType type) {
        if (scope instanceof JavacScope
                && member instanceof Symbol
                && type instanceof com.sun.tools.javac.code.Type) {
            Env<AttrContext> env = ((JavacScope) scope).env;
            return resolve.isAccessible(env, (com.sun.tools.javac.code.Type)type, (Symbol)member);
        } else
            return false;
    }

    private Env<AttrContext> getAttrContext(TreePath path) {
        if (!(path.getLeaf() instanceof JCTree))  // implicit null-check
            throw new IllegalArgumentException();

        // if we're being invoked via from a JSR199 client, we need to make sure
        // all the classes have been entered; if we're being invoked from JSR269,
        // then the classes will already have been entered.
        if (javacTaskImpl != null) {
            try {
                javacTaskImpl.enter(null);
            } catch (IOException e) {
                throw new Error("unexpected error while entering symbols: " + e);
            }
        }


        JCCompilationUnit unit = (JCCompilationUnit) path.getCompilationUnit();
        Copier copier = new Copier(treeMaker.forToplevel(unit));

        Env<AttrContext> env = null;
        JCMethodDecl method = null;
        JCVariableDecl field = null;

        List<Tree> l = List.nil();
        TreePath p = path;
        while (p != null) {
            l = l.prepend(p.getLeaf());
            p = p.getParentPath();
        }

        for ( ; l.nonEmpty(); l = l.tail) {
            Tree tree = l.head;
            switch (tree.getKind()) {
                case COMPILATION_UNIT:
//                    System.err.println("COMP: " + ((JCCompilationUnit)tree).sourcefile);
                    env = enter.getTopLevelEnv((JCCompilationUnit)tree);
                    break;
                case CLASS:
//                    System.err.println("CLASS: " + ((JCClassDecl)tree).sym.getSimpleName());
                    env = enter.getClassEnv(((JCClassDecl)tree).sym);
                    break;
                case METHOD:
//                    System.err.println("METHOD: " + ((JCMethodDecl)tree).sym.getSimpleName());
                    method = (JCMethodDecl)tree;
                    break;
                case VARIABLE:
//                    System.err.println("FIELD: " + ((JCVariableDecl)tree).sym.getSimpleName());
                    field = (JCVariableDecl)tree;
                    break;
                case BLOCK: {
//                    System.err.println("BLOCK: ");
                    if (method != null)
                        env = memberEnter.getMethodEnv(method, env);
                    JCTree body = copier.copy((JCTree)tree, (JCTree) path.getLeaf());
                    env = attribStatToTree(body, env, copier.leafCopy);
                    return env;
                }
                default:
//                    System.err.println("DEFAULT: " + tree.getKind());
                    if (field != null && field.getInitializer() == tree) {
                        env = memberEnter.getInitEnv(field, env);
                        JCExpression expr = copier.copy((JCExpression)tree, (JCTree) path.getLeaf());
                        env = attribExprToTree(expr, env, copier.leafCopy);
                        return env;
                    }
            }
        }
        return field != null ? memberEnter.getInitEnv(field, env) : env;
    }

    private Env<AttrContext> attribStatToTree(JCTree stat, Env<AttrContext>env, JCTree tree) {
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            return attr.attribStatToTree(stat, env, tree);
        } finally {
            log.useSource(prev);
        }
    }

    private Env<AttrContext> attribExprToTree(JCExpression expr, Env<AttrContext>env, JCTree tree) {
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            return attr.attribExprToTree(expr, env, tree);
        } finally {
            log.useSource(prev);
        }
    }

    /**
     * Makes a copy of a tree, noting the value resulting from copying a particular leaf.
     **/
    static class Copier extends TreeCopier<JCTree> {
        JCTree leafCopy = null;

        Copier(TreeMaker M) {
            super(M);
        }

        public <T extends JCTree> T copy(T t, JCTree leaf) {
            T t2 = super.copy(t, leaf);
            if (t == leaf)
                leafCopy = t2;
            return t2;
        }
    }

    /**
     * Gets the original type from the ErrorType object.
     * @param errorType The errorType for which we want to get the original type.
     * @returns TypeMirror corresponding to the original type, replaced by the ErrorType.
     *          noType (type.tag == NONE) is returned if there is no original type.
     */
    public TypeMirror getOriginalType(javax.lang.model.type.ErrorType errorType) {
        if (errorType instanceof com.sun.tools.javac.code.Type.ErrorType) {
            return ((com.sun.tools.javac.code.Type.ErrorType)errorType).getOriginalType();
        }

        return com.sun.tools.javac.code.Type.noType;
    }

    /**
     * Prints a message of the specified kind at the location of the
     * tree within the provided compilation unit
     *
     * @param kind the kind of message
     * @param msg  the message, or an empty string if none
     * @param t    the tree to use as a position hint
     * @param root the compilation unit that contains tree
     */
    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
            com.sun.source.tree.Tree t,
            com.sun.source.tree.CompilationUnitTree root) {
        JavaFileObject oldSource = null;
        JavaFileObject newSource = null;
        JCDiagnostic.DiagnosticPosition pos = null;

        newSource = root.getSourceFile();
        if (newSource != null) {
            oldSource = log.useSource(newSource);
            pos = ((JCTree) t).pos();
        }

        try {
            switch (kind) {
            case ERROR:
                boolean prev = log.multipleErrors;
                try {
                    log.error(pos, "proc.messager", msg.toString());
                } finally {
                    log.multipleErrors = prev;
                }
                break;

            case WARNING:
                log.warning(pos, "proc.messager", msg.toString());
                break;

            case MANDATORY_WARNING:
                log.mandatoryWarning(pos, "proc.messager", msg.toString());
                break;

            default:
                log.note(pos, "proc.messager", msg.toString());
            }
        } finally {
            if (oldSource != null)
                log.useSource(oldSource);
        }
    }
}
