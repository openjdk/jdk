/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.tree;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import java.util.Map;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;

/** Utility class containing inspector methods for trees.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class TreeInfo {
    protected static final Context.Key<TreeInfo> treeInfoKey =
        new Context.Key<TreeInfo>();

    public static TreeInfo instance(Context context) {
        TreeInfo instance = context.get(treeInfoKey);
        if (instance == null)
            instance = new TreeInfo(context);
        return instance;
    }

    /** The names of all operators.
     */
    private Name[] opname = new Name[JCTree.MOD - JCTree.POS + 1];

    private TreeInfo(Context context) {
        context.put(treeInfoKey, this);

        Names names = Names.instance(context);
        opname[JCTree.POS     - JCTree.POS] = names.fromString("+");
        opname[JCTree.NEG     - JCTree.POS] = names.hyphen;
        opname[JCTree.NOT     - JCTree.POS] = names.fromString("!");
        opname[JCTree.COMPL   - JCTree.POS] = names.fromString("~");
        opname[JCTree.PREINC  - JCTree.POS] = names.fromString("++");
        opname[JCTree.PREDEC  - JCTree.POS] = names.fromString("--");
        opname[JCTree.POSTINC - JCTree.POS] = names.fromString("++");
        opname[JCTree.POSTDEC - JCTree.POS] = names.fromString("--");
        opname[JCTree.NULLCHK - JCTree.POS] = names.fromString("<*nullchk*>");
        opname[JCTree.OR      - JCTree.POS] = names.fromString("||");
        opname[JCTree.AND     - JCTree.POS] = names.fromString("&&");
        opname[JCTree.EQ      - JCTree.POS] = names.fromString("==");
        opname[JCTree.NE      - JCTree.POS] = names.fromString("!=");
        opname[JCTree.LT      - JCTree.POS] = names.fromString("<");
        opname[JCTree.GT      - JCTree.POS] = names.fromString(">");
        opname[JCTree.LE      - JCTree.POS] = names.fromString("<=");
        opname[JCTree.GE      - JCTree.POS] = names.fromString(">=");
        opname[JCTree.BITOR   - JCTree.POS] = names.fromString("|");
        opname[JCTree.BITXOR  - JCTree.POS] = names.fromString("^");
        opname[JCTree.BITAND  - JCTree.POS] = names.fromString("&");
        opname[JCTree.SL      - JCTree.POS] = names.fromString("<<");
        opname[JCTree.SR      - JCTree.POS] = names.fromString(">>");
        opname[JCTree.USR     - JCTree.POS] = names.fromString(">>>");
        opname[JCTree.PLUS    - JCTree.POS] = names.fromString("+");
        opname[JCTree.MINUS   - JCTree.POS] = names.hyphen;
        opname[JCTree.MUL     - JCTree.POS] = names.asterisk;
        opname[JCTree.DIV     - JCTree.POS] = names.slash;
        opname[JCTree.MOD     - JCTree.POS] = names.fromString("%");
    }


    /** Return name of operator with given tree tag.
     */
    public Name operatorName(int tag) {
        return opname[tag - JCTree.POS];
    }

    /** Is tree a constructor declaration?
     */
    public static boolean isConstructor(JCTree tree) {
        if (tree.getTag() == JCTree.METHODDEF) {
            Name name = ((JCMethodDecl) tree).name;
            return name == name.table.names.init;
        } else {
            return false;
        }
    }

    /** Is there a constructor declaration in the given list of trees?
     */
    public static boolean hasConstructors(List<JCTree> trees) {
        for (List<JCTree> l = trees; l.nonEmpty(); l = l.tail)
            if (isConstructor(l.head)) return true;
        return false;
    }

    public static boolean isMultiCatch(JCCatch catchClause) {
        return catchClause.param.vartype.getTag() == JCTree.TYPEDISJOINT;
    }

    /** Is statement an initializer for a synthetic field?
     */
    public static boolean isSyntheticInit(JCTree stat) {
        if (stat.getTag() == JCTree.EXEC) {
            JCExpressionStatement exec = (JCExpressionStatement)stat;
            if (exec.expr.getTag() == JCTree.ASSIGN) {
                JCAssign assign = (JCAssign)exec.expr;
                if (assign.lhs.getTag() == JCTree.SELECT) {
                    JCFieldAccess select = (JCFieldAccess)assign.lhs;
                    if (select.sym != null &&
                        (select.sym.flags() & SYNTHETIC) != 0) {
                        Name selected = name(select.selected);
                        if (selected != null && selected == selected.table.names._this)
                            return true;
                    }
                }
            }
        }
        return false;
    }

    /** If the expression is a method call, return the method name, null
     *  otherwise. */
    public static Name calledMethodName(JCTree tree) {
        if (tree.getTag() == JCTree.EXEC) {
            JCExpressionStatement exec = (JCExpressionStatement)tree;
            if (exec.expr.getTag() == JCTree.APPLY) {
                Name mname = TreeInfo.name(((JCMethodInvocation) exec.expr).meth);
                return mname;
            }
        }
        return null;
    }

    /** Is this a call to this or super?
     */
    public static boolean isSelfCall(JCTree tree) {
        Name name = calledMethodName(tree);
        if (name != null) {
            Names names = name.table.names;
            return name==names._this || name==names._super;
        } else {
            return false;
        }
    }

    /** Is this a call to super?
     */
    public static boolean isSuperCall(JCTree tree) {
        Name name = calledMethodName(tree);
        if (name != null) {
            Names names = name.table.names;
            return name==names._super;
        } else {
            return false;
        }
    }

    /** Is this a constructor whose first (non-synthetic) statement is not
     *  of the form this(...)?
     */
    public static boolean isInitialConstructor(JCTree tree) {
        JCMethodInvocation app = firstConstructorCall(tree);
        if (app == null) return false;
        Name meth = name(app.meth);
        return meth == null || meth != meth.table.names._this;
    }

    /** Return the first call in a constructor definition. */
    public static JCMethodInvocation firstConstructorCall(JCTree tree) {
        if (tree.getTag() != JCTree.METHODDEF) return null;
        JCMethodDecl md = (JCMethodDecl) tree;
        Names names = md.name.table.names;
        if (md.name != names.init) return null;
        if (md.body == null) return null;
        List<JCStatement> stats = md.body.stats;
        // Synthetic initializations can appear before the super call.
        while (stats.nonEmpty() && isSyntheticInit(stats.head))
            stats = stats.tail;
        if (stats.isEmpty()) return null;
        if (stats.head.getTag() != JCTree.EXEC) return null;
        JCExpressionStatement exec = (JCExpressionStatement) stats.head;
        if (exec.expr.getTag() != JCTree.APPLY) return null;
        return (JCMethodInvocation)exec.expr;
    }

    /** Return true if a tree represents a diamond new expr. */
    public static boolean isDiamond(JCTree tree) {
        switch(tree.getTag()) {
            case JCTree.TYPEAPPLY: return ((JCTypeApply)tree).getTypeArguments().isEmpty();
            case JCTree.NEWCLASS: return isDiamond(((JCNewClass)tree).clazz);
            default: return false;
        }
    }

    /** Return true if a tree represents the null literal. */
    public static boolean isNull(JCTree tree) {
        if (tree.getTag() != JCTree.LITERAL)
            return false;
        JCLiteral lit = (JCLiteral) tree;
        return (lit.typetag == TypeTags.BOT);
    }

    /** The position of the first statement in a block, or the position of
     *  the block itself if it is empty.
     */
    public static int firstStatPos(JCTree tree) {
        if (tree.getTag() == JCTree.BLOCK && ((JCBlock) tree).stats.nonEmpty())
            return ((JCBlock) tree).stats.head.pos;
        else
            return tree.pos;
    }

    /** The end position of given tree, if it is a block with
     *  defined endpos.
     */
    public static int endPos(JCTree tree) {
        if (tree.getTag() == JCTree.BLOCK && ((JCBlock) tree).endpos != Position.NOPOS)
            return ((JCBlock) tree).endpos;
        else if (tree.getTag() == JCTree.SYNCHRONIZED)
            return endPos(((JCSynchronized) tree).body);
        else if (tree.getTag() == JCTree.TRY) {
            JCTry t = (JCTry) tree;
            return endPos((t.finalizer != null)
                          ? t.finalizer
                          : t.catchers.last().body);
        } else
            return tree.pos;
    }


    /** Get the start position for a tree node.  The start position is
     * defined to be the position of the first character of the first
     * token of the node's source text.
     * @param tree  The tree node
     */
    public static int getStartPos(JCTree tree) {
        if (tree == null)
            return Position.NOPOS;

        switch(tree.getTag()) {
        case(JCTree.APPLY):
            return getStartPos(((JCMethodInvocation) tree).meth);
        case(JCTree.ASSIGN):
            return getStartPos(((JCAssign) tree).lhs);
        case(JCTree.BITOR_ASG): case(JCTree.BITXOR_ASG): case(JCTree.BITAND_ASG):
        case(JCTree.SL_ASG): case(JCTree.SR_ASG): case(JCTree.USR_ASG):
        case(JCTree.PLUS_ASG): case(JCTree.MINUS_ASG): case(JCTree.MUL_ASG):
        case(JCTree.DIV_ASG): case(JCTree.MOD_ASG):
            return getStartPos(((JCAssignOp) tree).lhs);
        case(JCTree.OR): case(JCTree.AND): case(JCTree.BITOR):
        case(JCTree.BITXOR): case(JCTree.BITAND): case(JCTree.EQ):
        case(JCTree.NE): case(JCTree.LT): case(JCTree.GT):
        case(JCTree.LE): case(JCTree.GE): case(JCTree.SL):
        case(JCTree.SR): case(JCTree.USR): case(JCTree.PLUS):
        case(JCTree.MINUS): case(JCTree.MUL): case(JCTree.DIV):
        case(JCTree.MOD):
            return getStartPos(((JCBinary) tree).lhs);
        case(JCTree.CLASSDEF): {
            JCClassDecl node = (JCClassDecl)tree;
            if (node.mods.pos != Position.NOPOS)
                return node.mods.pos;
            break;
        }
        case(JCTree.CONDEXPR):
            return getStartPos(((JCConditional) tree).cond);
        case(JCTree.EXEC):
            return getStartPos(((JCExpressionStatement) tree).expr);
        case(JCTree.INDEXED):
            return getStartPos(((JCArrayAccess) tree).indexed);
        case(JCTree.METHODDEF): {
            JCMethodDecl node = (JCMethodDecl)tree;
            if (node.mods.pos != Position.NOPOS)
                return node.mods.pos;
            if (node.typarams.nonEmpty()) // List.nil() used for no typarams
                return getStartPos(node.typarams.head);
            return node.restype == null ? node.pos : getStartPos(node.restype);
        }
        case(JCTree.SELECT):
            return getStartPos(((JCFieldAccess) tree).selected);
        case(JCTree.TYPEAPPLY):
            return getStartPos(((JCTypeApply) tree).clazz);
        case(JCTree.TYPEARRAY):
            return getStartPos(((JCArrayTypeTree) tree).elemtype);
        case(JCTree.TYPETEST):
            return getStartPos(((JCInstanceOf) tree).expr);
        case(JCTree.POSTINC):
        case(JCTree.POSTDEC):
            return getStartPos(((JCUnary) tree).arg);
        case(JCTree.ANNOTATED_TYPE): {
            JCAnnotatedType node = (JCAnnotatedType) tree;
            if (node.annotations.nonEmpty())
                return getStartPos(node.annotations.head);
            return getStartPos(node.underlyingType);
        }
        case(JCTree.NEWCLASS): {
            JCNewClass node = (JCNewClass)tree;
            if (node.encl != null)
                return getStartPos(node.encl);
            break;
        }
        case(JCTree.VARDEF): {
            JCVariableDecl node = (JCVariableDecl)tree;
            if (node.mods.pos != Position.NOPOS) {
                return node.mods.pos;
            } else {
                return getStartPos(node.vartype);
            }
        }
        case(JCTree.ERRONEOUS): {
            JCErroneous node = (JCErroneous)tree;
            if (node.errs != null && node.errs.nonEmpty())
                return getStartPos(node.errs.head);
        }
        }
        return tree.pos;
    }

    /** The end position of given tree, given  a table of end positions generated by the parser
     */
    public static int getEndPos(JCTree tree, Map<JCTree, Integer> endPositions) {
        if (tree == null)
            return Position.NOPOS;

        if (endPositions == null) {
            // fall back on limited info in the tree
            return endPos(tree);
        }

        Integer mapPos = endPositions.get(tree);
        if (mapPos != null)
            return mapPos;

        switch(tree.getTag()) {
        case(JCTree.BITOR_ASG): case(JCTree.BITXOR_ASG): case(JCTree.BITAND_ASG):
        case(JCTree.SL_ASG): case(JCTree.SR_ASG): case(JCTree.USR_ASG):
        case(JCTree.PLUS_ASG): case(JCTree.MINUS_ASG): case(JCTree.MUL_ASG):
        case(JCTree.DIV_ASG): case(JCTree.MOD_ASG):
            return getEndPos(((JCAssignOp) tree).rhs, endPositions);
        case(JCTree.OR): case(JCTree.AND): case(JCTree.BITOR):
        case(JCTree.BITXOR): case(JCTree.BITAND): case(JCTree.EQ):
        case(JCTree.NE): case(JCTree.LT): case(JCTree.GT):
        case(JCTree.LE): case(JCTree.GE): case(JCTree.SL):
        case(JCTree.SR): case(JCTree.USR): case(JCTree.PLUS):
        case(JCTree.MINUS): case(JCTree.MUL): case(JCTree.DIV):
        case(JCTree.MOD):
            return getEndPos(((JCBinary) tree).rhs, endPositions);
        case(JCTree.CASE):
            return getEndPos(((JCCase) tree).stats.last(), endPositions);
        case(JCTree.CATCH):
            return getEndPos(((JCCatch) tree).body, endPositions);
        case(JCTree.CONDEXPR):
            return getEndPos(((JCConditional) tree).falsepart, endPositions);
        case(JCTree.FORLOOP):
            return getEndPos(((JCForLoop) tree).body, endPositions);
        case(JCTree.FOREACHLOOP):
            return getEndPos(((JCEnhancedForLoop) tree).body, endPositions);
        case(JCTree.IF): {
            JCIf node = (JCIf)tree;
            if (node.elsepart == null) {
                return getEndPos(node.thenpart, endPositions);
            } else {
                return getEndPos(node.elsepart, endPositions);
            }
        }
        case(JCTree.LABELLED):
            return getEndPos(((JCLabeledStatement) tree).body, endPositions);
        case(JCTree.MODIFIERS):
            return getEndPos(((JCModifiers) tree).annotations.last(), endPositions);
        case(JCTree.SYNCHRONIZED):
            return getEndPos(((JCSynchronized) tree).body, endPositions);
        case(JCTree.TOPLEVEL):
            return getEndPos(((JCCompilationUnit) tree).defs.last(), endPositions);
        case(JCTree.TRY): {
            JCTry node = (JCTry)tree;
            if (node.finalizer != null) {
                return getEndPos(node.finalizer, endPositions);
            } else if (!node.catchers.isEmpty()) {
                return getEndPos(node.catchers.last(), endPositions);
            } else {
                return getEndPos(node.body, endPositions);
            }
        }
        case(JCTree.WILDCARD):
            return getEndPos(((JCWildcard) tree).inner, endPositions);
        case(JCTree.TYPECAST):
            return getEndPos(((JCTypeCast) tree).expr, endPositions);
        case(JCTree.TYPETEST):
            return getEndPos(((JCInstanceOf) tree).clazz, endPositions);
        case(JCTree.POS):
        case(JCTree.NEG):
        case(JCTree.NOT):
        case(JCTree.COMPL):
        case(JCTree.PREINC):
        case(JCTree.PREDEC):
            return getEndPos(((JCUnary) tree).arg, endPositions);
        case(JCTree.WHILELOOP):
            return getEndPos(((JCWhileLoop) tree).body, endPositions);
        case(JCTree.ANNOTATED_TYPE):
            return getEndPos(((JCAnnotatedType) tree).underlyingType, endPositions);
        case(JCTree.ERRONEOUS): {
            JCErroneous node = (JCErroneous)tree;
            if (node.errs != null && node.errs.nonEmpty())
                return getEndPos(node.errs.last(), endPositions);
        }
        }
        return Position.NOPOS;
    }


    /** A DiagnosticPosition with the preferred position set to the
     *  end position of given tree, if it is a block with
     *  defined endpos.
     */
    public static DiagnosticPosition diagEndPos(final JCTree tree) {
        final int endPos = TreeInfo.endPos(tree);
        return new DiagnosticPosition() {
            public JCTree getTree() { return tree; }
            public int getStartPosition() { return TreeInfo.getStartPos(tree); }
            public int getPreferredPosition() { return endPos; }
            public int getEndPosition(Map<JCTree, Integer> endPosTable) {
                return TreeInfo.getEndPos(tree, endPosTable);
            }
        };
    }

    /** The position of the finalizer of given try/synchronized statement.
     */
    public static int finalizerPos(JCTree tree) {
        if (tree.getTag() == JCTree.TRY) {
            JCTry t = (JCTry) tree;
            assert t.finalizer != null;
            return firstStatPos(t.finalizer);
        } else if (tree.getTag() == JCTree.SYNCHRONIZED) {
            return endPos(((JCSynchronized) tree).body);
        } else {
            throw new AssertionError();
        }
    }

    /** Find the position for reporting an error about a symbol, where
     *  that symbol is defined somewhere in the given tree. */
    public static int positionFor(final Symbol sym, final JCTree tree) {
        JCTree decl = declarationFor(sym, tree);
        return ((decl != null) ? decl : tree).pos;
    }

    /** Find the position for reporting an error about a symbol, where
     *  that symbol is defined somewhere in the given tree. */
    public static DiagnosticPosition diagnosticPositionFor(final Symbol sym, final JCTree tree) {
        JCTree decl = declarationFor(sym, tree);
        return ((decl != null) ? decl : tree).pos();
    }

    /** Find the declaration for a symbol, where
     *  that symbol is defined somewhere in the given tree. */
    public static JCTree declarationFor(final Symbol sym, final JCTree tree) {
        class DeclScanner extends TreeScanner {
            JCTree result = null;
            public void scan(JCTree tree) {
                if (tree!=null && result==null)
                    tree.accept(this);
            }
            public void visitTopLevel(JCCompilationUnit that) {
                if (that.packge == sym) result = that;
                else super.visitTopLevel(that);
            }
            public void visitClassDef(JCClassDecl that) {
                if (that.sym == sym) result = that;
                else super.visitClassDef(that);
            }
            public void visitMethodDef(JCMethodDecl that) {
                if (that.sym == sym) result = that;
                else super.visitMethodDef(that);
            }
            public void visitVarDef(JCVariableDecl that) {
                if (that.sym == sym) result = that;
                else super.visitVarDef(that);
            }
        }
        DeclScanner s = new DeclScanner();
        tree.accept(s);
        return s.result;
    }

    public static Env<AttrContext> scopeFor(JCTree node, JCCompilationUnit unit) {
        return scopeFor(pathFor(node, unit));
    }

    public static Env<AttrContext> scopeFor(List<JCTree> path) {
        // TODO: not implemented yet
        throw new UnsupportedOperationException("not implemented yet");
    }

    public static List<JCTree> pathFor(final JCTree node, final JCCompilationUnit unit) {
        class Result extends Error {
            static final long serialVersionUID = -5942088234594905625L;
            List<JCTree> path;
            Result(List<JCTree> path) {
                this.path = path;
            }
        }
        class PathFinder extends TreeScanner {
            List<JCTree> path = List.nil();
            public void scan(JCTree tree) {
                if (tree != null) {
                    path = path.prepend(tree);
                    if (tree == node)
                        throw new Result(path);
                    super.scan(tree);
                    path = path.tail;
                }
            }
        }
        try {
            new PathFinder().scan(unit);
        } catch (Result result) {
            return result.path;
        }
        return List.nil();
    }

    /** Return the statement referenced by a label.
     *  If the label refers to a loop or switch, return that switch
     *  otherwise return the labelled statement itself
     */
    public static JCTree referencedStatement(JCLabeledStatement tree) {
        JCTree t = tree;
        do t = ((JCLabeledStatement) t).body;
        while (t.getTag() == JCTree.LABELLED);
        switch (t.getTag()) {
        case JCTree.DOLOOP: case JCTree.WHILELOOP: case JCTree.FORLOOP: case JCTree.FOREACHLOOP: case JCTree.SWITCH:
            return t;
        default:
            return tree;
        }
    }

    /** Skip parens and return the enclosed expression
     */
    public static JCExpression skipParens(JCExpression tree) {
        while (tree.getTag() == JCTree.PARENS) {
            tree = ((JCParens) tree).expr;
        }
        return tree;
    }

    /** Skip parens and return the enclosed expression
     */
    public static JCTree skipParens(JCTree tree) {
        if (tree.getTag() == JCTree.PARENS)
            return skipParens((JCParens)tree);
        else
            return tree;
    }

    /** Return the types of a list of trees.
     */
    public static List<Type> types(List<? extends JCTree> trees) {
        ListBuffer<Type> ts = new ListBuffer<Type>();
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
            ts.append(l.head.type);
        return ts.toList();
    }

    /** If this tree is an identifier or a field or a parameterized type,
     *  return its name, otherwise return null.
     */
    public static Name name(JCTree tree) {
        switch (tree.getTag()) {
        case JCTree.IDENT:
            return ((JCIdent) tree).name;
        case JCTree.SELECT:
            return ((JCFieldAccess) tree).name;
        case JCTree.TYPEAPPLY:
            return name(((JCTypeApply) tree).clazz);
        default:
            return null;
        }
    }

    /** If this tree is a qualified identifier, its return fully qualified name,
     *  otherwise return null.
     */
    public static Name fullName(JCTree tree) {
        tree = skipParens(tree);
        switch (tree.getTag()) {
        case JCTree.IDENT:
            return ((JCIdent) tree).name;
        case JCTree.SELECT:
            Name sname = fullName(((JCFieldAccess) tree).selected);
            return sname == null ? null : sname.append('.', name(tree));
        default:
            return null;
        }
    }

    public static Symbol symbolFor(JCTree node) {
        node = skipParens(node);
        switch (node.getTag()) {
        case JCTree.CLASSDEF:
            return ((JCClassDecl) node).sym;
        case JCTree.METHODDEF:
            return ((JCMethodDecl) node).sym;
        case JCTree.VARDEF:
            return ((JCVariableDecl) node).sym;
        default:
            return null;
        }
    }

    /** If this tree is an identifier or a field, return its symbol,
     *  otherwise return null.
     */
    public static Symbol symbol(JCTree tree) {
        tree = skipParens(tree);
        switch (tree.getTag()) {
        case JCTree.IDENT:
            return ((JCIdent) tree).sym;
        case JCTree.SELECT:
            return ((JCFieldAccess) tree).sym;
        case JCTree.TYPEAPPLY:
            return symbol(((JCTypeApply) tree).clazz);
        default:
            return null;
        }
    }

    /** Return true if this is a nonstatic selection. */
    public static boolean nonstaticSelect(JCTree tree) {
        tree = skipParens(tree);
        if (tree.getTag() != JCTree.SELECT) return false;
        JCFieldAccess s = (JCFieldAccess) tree;
        Symbol e = symbol(s.selected);
        return e == null || (e.kind != Kinds.PCK && e.kind != Kinds.TYP);
    }

    /** If this tree is an identifier or a field, set its symbol, otherwise skip.
     */
    public static void setSymbol(JCTree tree, Symbol sym) {
        tree = skipParens(tree);
        switch (tree.getTag()) {
        case JCTree.IDENT:
            ((JCIdent) tree).sym = sym; break;
        case JCTree.SELECT:
            ((JCFieldAccess) tree).sym = sym; break;
        default:
        }
    }

    /** If this tree is a declaration or a block, return its flags field,
     *  otherwise return 0.
     */
    public static long flags(JCTree tree) {
        switch (tree.getTag()) {
        case JCTree.VARDEF:
            return ((JCVariableDecl) tree).mods.flags;
        case JCTree.METHODDEF:
            return ((JCMethodDecl) tree).mods.flags;
        case JCTree.CLASSDEF:
            return ((JCClassDecl) tree).mods.flags;
        case JCTree.BLOCK:
            return ((JCBlock) tree).flags;
        default:
            return 0;
        }
    }

    /** Return first (smallest) flag in `flags':
     *  pre: flags != 0
     */
    public static long firstFlag(long flags) {
        int flag = 1;
        while ((flag & StandardFlags) != 0 && (flag & flags) == 0)
            flag = flag << 1;
        return flag;
    }

    /** Return flags as a string, separated by " ".
     */
    public static String flagNames(long flags) {
        return Flags.toString(flags & StandardFlags).trim();
    }

    /** Operator precedences values.
     */
    public static final int
        notExpression = -1,   // not an expression
        noPrec = 0,           // no enclosing expression
        assignPrec = 1,
        assignopPrec = 2,
        condPrec = 3,
        orPrec = 4,
        andPrec = 5,
        bitorPrec = 6,
        bitxorPrec = 7,
        bitandPrec = 8,
        eqPrec = 9,
        ordPrec = 10,
        shiftPrec = 11,
        addPrec = 12,
        mulPrec = 13,
        prefixPrec = 14,
        postfixPrec = 15,
        precCount = 16;


    /** Map operators to their precedence levels.
     */
    public static int opPrec(int op) {
        switch(op) {
        case JCTree.POS:
        case JCTree.NEG:
        case JCTree.NOT:
        case JCTree.COMPL:
        case JCTree.PREINC:
        case JCTree.PREDEC: return prefixPrec;
        case JCTree.POSTINC:
        case JCTree.POSTDEC:
        case JCTree.NULLCHK: return postfixPrec;
        case JCTree.ASSIGN: return assignPrec;
        case JCTree.BITOR_ASG:
        case JCTree.BITXOR_ASG:
        case JCTree.BITAND_ASG:
        case JCTree.SL_ASG:
        case JCTree.SR_ASG:
        case JCTree.USR_ASG:
        case JCTree.PLUS_ASG:
        case JCTree.MINUS_ASG:
        case JCTree.MUL_ASG:
        case JCTree.DIV_ASG:
        case JCTree.MOD_ASG: return assignopPrec;
        case JCTree.OR: return orPrec;
        case JCTree.AND: return andPrec;
        case JCTree.EQ:
        case JCTree.NE: return eqPrec;
        case JCTree.LT:
        case JCTree.GT:
        case JCTree.LE:
        case JCTree.GE: return ordPrec;
        case JCTree.BITOR: return bitorPrec;
        case JCTree.BITXOR: return bitxorPrec;
        case JCTree.BITAND: return bitandPrec;
        case JCTree.SL:
        case JCTree.SR:
        case JCTree.USR: return shiftPrec;
        case JCTree.PLUS:
        case JCTree.MINUS: return addPrec;
        case JCTree.MUL:
        case JCTree.DIV:
        case JCTree.MOD: return mulPrec;
        case JCTree.TYPETEST: return ordPrec;
        default: throw new AssertionError();
        }
    }

    static Tree.Kind tagToKind(int tag) {
        switch (tag) {
        // Postfix expressions
        case JCTree.POSTINC:           // _ ++
            return Tree.Kind.POSTFIX_INCREMENT;
        case JCTree.POSTDEC:           // _ --
            return Tree.Kind.POSTFIX_DECREMENT;

        // Unary operators
        case JCTree.PREINC:            // ++ _
            return Tree.Kind.PREFIX_INCREMENT;
        case JCTree.PREDEC:            // -- _
            return Tree.Kind.PREFIX_DECREMENT;
        case JCTree.POS:               // +
            return Tree.Kind.UNARY_PLUS;
        case JCTree.NEG:               // -
            return Tree.Kind.UNARY_MINUS;
        case JCTree.COMPL:             // ~
            return Tree.Kind.BITWISE_COMPLEMENT;
        case JCTree.NOT:               // !
            return Tree.Kind.LOGICAL_COMPLEMENT;

        // Binary operators

        // Multiplicative operators
        case JCTree.MUL:               // *
            return Tree.Kind.MULTIPLY;
        case JCTree.DIV:               // /
            return Tree.Kind.DIVIDE;
        case JCTree.MOD:               // %
            return Tree.Kind.REMAINDER;

        // Additive operators
        case JCTree.PLUS:              // +
            return Tree.Kind.PLUS;
        case JCTree.MINUS:             // -
            return Tree.Kind.MINUS;

        // Shift operators
        case JCTree.SL:                // <<
            return Tree.Kind.LEFT_SHIFT;
        case JCTree.SR:                // >>
            return Tree.Kind.RIGHT_SHIFT;
        case JCTree.USR:               // >>>
            return Tree.Kind.UNSIGNED_RIGHT_SHIFT;

        // Relational operators
        case JCTree.LT:                // <
            return Tree.Kind.LESS_THAN;
        case JCTree.GT:                // >
            return Tree.Kind.GREATER_THAN;
        case JCTree.LE:                // <=
            return Tree.Kind.LESS_THAN_EQUAL;
        case JCTree.GE:                // >=
            return Tree.Kind.GREATER_THAN_EQUAL;

        // Equality operators
        case JCTree.EQ:                // ==
            return Tree.Kind.EQUAL_TO;
        case JCTree.NE:                // !=
            return Tree.Kind.NOT_EQUAL_TO;

        // Bitwise and logical operators
        case JCTree.BITAND:            // &
            return Tree.Kind.AND;
        case JCTree.BITXOR:            // ^
            return Tree.Kind.XOR;
        case JCTree.BITOR:             // |
            return Tree.Kind.OR;

        // Conditional operators
        case JCTree.AND:               // &&
            return Tree.Kind.CONDITIONAL_AND;
        case JCTree.OR:                // ||
            return Tree.Kind.CONDITIONAL_OR;

        // Assignment operators
        case JCTree.MUL_ASG:           // *=
            return Tree.Kind.MULTIPLY_ASSIGNMENT;
        case JCTree.DIV_ASG:           // /=
            return Tree.Kind.DIVIDE_ASSIGNMENT;
        case JCTree.MOD_ASG:           // %=
            return Tree.Kind.REMAINDER_ASSIGNMENT;
        case JCTree.PLUS_ASG:          // +=
            return Tree.Kind.PLUS_ASSIGNMENT;
        case JCTree.MINUS_ASG:         // -=
            return Tree.Kind.MINUS_ASSIGNMENT;
        case JCTree.SL_ASG:            // <<=
            return Tree.Kind.LEFT_SHIFT_ASSIGNMENT;
        case JCTree.SR_ASG:            // >>=
            return Tree.Kind.RIGHT_SHIFT_ASSIGNMENT;
        case JCTree.USR_ASG:           // >>>=
            return Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT;
        case JCTree.BITAND_ASG:        // &=
            return Tree.Kind.AND_ASSIGNMENT;
        case JCTree.BITXOR_ASG:        // ^=
            return Tree.Kind.XOR_ASSIGNMENT;
        case JCTree.BITOR_ASG:         // |=
            return Tree.Kind.OR_ASSIGNMENT;

        // Null check (implementation detail), for example, __.getClass()
        case JCTree.NULLCHK:
            return Tree.Kind.OTHER;

        default:
            return null;
        }
    }

    /**
     * Returns the underlying type of the tree if it is annotated type,
     * or the tree itself otherwise
     */
    public static JCExpression typeIn(JCExpression tree) {
        switch (tree.getTag()) {
        case JCTree.ANNOTATED_TYPE:
            return ((JCAnnotatedType)tree).underlyingType;
        case JCTree.IDENT: /* simple names */
        case JCTree.TYPEIDENT: /* primitive name */
        case JCTree.SELECT: /* qualified name */
        case JCTree.TYPEARRAY: /* array types */
        case JCTree.WILDCARD: /* wild cards */
        case JCTree.TYPEPARAMETER: /* type parameters */
        case JCTree.TYPEAPPLY: /* parameterized types */
            return tree;
        default:
            throw new AssertionError("Unexpected type tree: " + tree);
        }
    }

    public static JCTree innermostType(JCTree type) {
        switch (type.getTag()) {
        case JCTree.TYPEARRAY:
            return innermostType(((JCArrayTypeTree)type).elemtype);
        case JCTree.WILDCARD:
            return innermostType(((JCWildcard)type).inner);
        case JCTree.ANNOTATED_TYPE:
            return innermostType(((JCAnnotatedType)type).underlyingType);
        default:
            return type;
        }
    }
}
