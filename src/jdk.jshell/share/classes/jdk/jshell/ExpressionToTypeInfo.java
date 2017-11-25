/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.List;
import jdk.jshell.TaskFactory.AnalyzeTask;

/**
 * Compute information about an expression string, particularly its type name.
 */
class ExpressionToTypeInfo {

    private static final String OBJECT_TYPE_NAME = "Object";

    final AnalyzeTask at;
    final CompilationUnitTree cu;
    final JShell state;
    final Symtab syms;
    final Types types;

    private ExpressionToTypeInfo(AnalyzeTask at, CompilationUnitTree cu, JShell state) {
        this.at = at;
        this.cu = cu;
        this.state = state;
        this.syms = Symtab.instance(at.context);
        this.types = Types.instance(at.context);
    }

    public static class ExpressionInfo {
        ExpressionTree tree;
        String typeName;
        String accessibleTypeName;
        String fullTypeName;
        List<String> parameterTypes;
        String enclosingInstanceType;
        boolean isClass;
        boolean isNonVoid;
    }

    // return mechanism and other general structure from TreePath.getPath()
    private static class Result extends Error {

        static final long serialVersionUID = -5942088234594905629L;
        final TreePath expressionPath;

        Result(TreePath path) {
            this.expressionPath = path;
        }
    }

    private static class PathFinder extends TreePathScanner<TreePath, Boolean> {

        // Optimize out imports etc
        @Override
        public TreePath visitCompilationUnit(CompilationUnitTree node, Boolean isTargetContext) {
            return scan(node.getTypeDecls(), isTargetContext);
        }

        // Only care about members
        @Override
        public TreePath visitClass(ClassTree node, Boolean isTargetContext) {
            return scan(node.getMembers(), isTargetContext);
        }

        // Only want the doit method where the code is
        @Override
        public TreePath visitMethod(MethodTree node, Boolean isTargetContext) {
            if (Util.isDoIt(node.getName())) {
                return scan(node.getBody(), true);
            } else {
                return null;
            }
        }

        @Override
        public TreePath visitReturn(ReturnTree node, Boolean isTargetContext) {
            ExpressionTree tree = node.getExpression();
            TreePath tp = new TreePath(getCurrentPath(), tree);
            if (isTargetContext) {
                throw new Result(tp);
            } else {
                return null;
            }
        }

        @Override
        public TreePath visitVariable(VariableTree node, Boolean isTargetContext) {
            if (isTargetContext) {
                throw new Result(getCurrentPath());
            } else {
                return null;
            }
        }

    }

    private Type pathToType(TreePath tp) {
        return (Type) at.trees().getTypeMirror(tp);
    }

    private Type pathToType(TreePath tp, Tree tree) {
        if (tree instanceof ConditionalExpressionTree) {
            // Conditionals always wind up as Object -- this corrects
            ConditionalExpressionTree cet = (ConditionalExpressionTree) tree;
            Type tmt = pathToType(new TreePath(tp, cet.getTrueExpression()));
            Type tmf = pathToType(new TreePath(tp, cet.getFalseExpression()));
            if (!tmt.isPrimitive() && !tmf.isPrimitive()) {
                Type lub = types.lub(tmt, tmf);
                // System.err.printf("cond ? %s : %s  --  lub = %s\n",
                //             varTypeName(tmt), varTypeName(tmf), varTypeName(lub));
                return lub;
            }
        }
        return pathToType(tp);
    }

    /**
     * Entry method: get expression info
     * @param code the expression as a string
     * @param state a JShell instance
     * @return type information
     */
    public static ExpressionInfo expressionInfo(String code, JShell state) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        OuterWrap codeWrap = state.outerMap.wrapInTrialClass(Wrap.methodReturnWrap(code));
        try {
            return state.taskFactory.analyze(codeWrap, at -> {
                CompilationUnitTree cu = at.firstCuTree();
                if (at.hasErrors() || cu == null) {
                    return null;
                }
                return new ExpressionToTypeInfo(at, cu, state).typeOfExpression();
            });
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Entry method: get expression info corresponding to a local variable declaration if its type
     * has been inferred automatically from the given initializer.
     * @param code the initializer as a string
     * @param state a JShell instance
     * @return type information
     */
    public static ExpressionInfo localVariableTypeForInitializer(String code, JShell state) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        try {
            OuterWrap codeWrap = state.outerMap.wrapInTrialClass(Wrap.methodWrap("var $$$ = " + code));
            return state.taskFactory.analyze(codeWrap, at -> {
                CompilationUnitTree cu = at.firstCuTree();
                if (at.hasErrors() || cu == null) {
                    return null;
                }
                return new ExpressionToTypeInfo(at, cu, state).typeOfExpression();
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private ExpressionInfo typeOfExpression() {
        return treeToInfo(findExpressionPath());
    }

    private TreePath findExpressionPath() {
        try {
            new PathFinder().scan(new TreePath(cu), false);
        } catch (Result result) {
            return result.expressionPath;
        }
        return null;
    }

    /**
     * A type is accessible if it is public or if it is package-private and is a
     * type defined in JShell.  Additionally, all its type arguments must be
     * accessible
     *
     * @param type the type to check for accessibility
     * @return true if the type name can be referenced
     */
    private boolean isAccessible(Type type) {
        Symbol.TypeSymbol tsym = type.asElement();
        return ((tsym.flags() & Flags.PUBLIC) != 0 ||
                ((tsym.flags() & Flags.PRIVATE) == 0 &&
                Util.isInJShellClass(tsym.flatName().toString()))) &&
                 type.getTypeArguments().stream()
                        .allMatch(this::isAccessible);
    }

    /**
     * Return the superclass.
     *
     * @param type the type
     * @return the superclass, or Object on error
     */
    private Type supertype(Type type) {
        Type sup = types.supertype(type);
        if (sup == Type.noType || sup == null) {
            return syms.objectType;
        }
        return sup;
    }

    /**
     * Find an accessible supertype.
     *
     * @param type the type
     * @return the type, if it is accessible, otherwise a superclass or
     * interface which is
     */
    private Type findAccessibleSupertype(Type type) {
        // Iterate up the superclasses, see if any are accessible
        for (Type sup = type; !types.isSameType(sup, syms.objectType); sup = supertype(sup)) {
            if (isAccessible(sup)) {
                return sup;
            }
        }
        // Failing superclasses, look through superclasses for accessible interfaces
        for (Type sup = type; !types.isSameType(sup, syms.objectType); sup = supertype(sup)) {
            for (Type itf : types.interfaces(sup)) {
                if (isAccessible(itf)) {
                    return itf;
                }
            }
        }
        // Punt, return Object which is the supertype of everything
        return syms.objectType;
    }

    private ExpressionInfo treeToInfo(TreePath tp) {
        if (tp != null) {
            Tree tree = tp.getLeaf();
            boolean isExpression = tree instanceof ExpressionTree;
            if (isExpression || tree.getKind() == Kind.VARIABLE) {
                ExpressionInfo ei = new ExpressionInfo();
                if (isExpression)
                    ei.tree = (ExpressionTree) tree;
                Type type = pathToType(tp, tree);
                if (type != null) {
                    switch (type.getKind()) {
                        case VOID:
                        case NONE:
                        case ERROR:
                        case OTHER:
                            break;
                        case NULL:
                            ei.isNonVoid = true;
                            ei.typeName = OBJECT_TYPE_NAME;
                            ei.accessibleTypeName = OBJECT_TYPE_NAME;
                            break;
                        default: {
                            ei.isNonVoid = true;
                            ei.typeName = varTypeName(type, false);
                            ei.accessibleTypeName = varTypeName(findAccessibleSupertype(type), false);
                            ei.fullTypeName = varTypeName(type, true);
                            break;
                        }
                    }
                }
                if (tree.getKind() == Tree.Kind.VARIABLE) {
                    Tree init = ((VariableTree) tree).getInitializer();
                    if (init.getKind() == Tree.Kind.NEW_CLASS &&
                        ((NewClassTree) init).getClassBody() != null) {
                        NewClassTree nct = (NewClassTree) init;
                        ClassTree clazz = nct.getClassBody();
                        MethodTree constructor = (MethodTree) clazz.getMembers().get(0);
                        ExpressionStatementTree superCallStatement =
                                (ExpressionStatementTree) constructor.getBody().getStatements().get(0);
                        MethodInvocationTree superCall =
                                (MethodInvocationTree) superCallStatement.getExpression();
                        TreePath superCallPath =
                                at.trees().getPath(tp.getCompilationUnit(), superCall.getMethodSelect());
                        Type constrType = pathToType(superCallPath);
                        ei.parameterTypes = constrType.getParameterTypes()
                                                      .stream()
                                                      .map(t -> varTypeName(t, false))
                                                      .collect(List.collector());
                        if (nct.getEnclosingExpression() != null) {
                            TreePath enclPath = new TreePath(tp, nct.getEnclosingExpression());
                            ei.enclosingInstanceType = varTypeName(pathToType(enclPath), false);
                        }
                        ei.isClass = at.task.getTypes().directSupertypes(type).size() == 1;
                    }
                }
                return ei;
            }
        }
        return null;
    }

    private String varTypeName(Type type, boolean printIntersectionTypes) {
        try {
            TypePrinter tp = new TypePrinter(at.messages(),
                    state.maps::fullClassNameAndPackageToClass, printIntersectionTypes);
            List<Type> captures = types.captures(type);
            String res = tp.toString(types.upward(type, captures));

            if (res == null)
                res = OBJECT_TYPE_NAME;

            return res;
        } catch (Exception ex) {
            return OBJECT_TYPE_NAME;
        }
    }

}
