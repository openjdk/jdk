/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import java.io.IOException;
import java.io.StringWriter;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.source.tree.*;

import static com.sun.tools.javac.code.BoundKind.*;

/**
 * Root class for abstract syntax tree nodes. It provides definitions
 * for specific tree nodes as subclasses nested inside.
 *
 * <p>Each subclass is highly standardized.  It generally contains
 * only tree fields for the syntactic subcomponents of the node.  Some
 * classes that represent identifier uses or definitions also define a
 * Symbol field that denotes the represented identifier.  Classes for
 * non-local jumps also carry the jump target as a field.  The root
 * class Tree itself defines fields for the tree's type and position.
 * No other fields are kept in a tree node; instead parameters are
 * passed to methods accessing the node.
 *
 * <p>Except for the methods defined by com.sun.source, the only
 * method defined in subclasses is `visit' which applies a given
 * visitor to the tree. The actual tree processing is done by visitor
 * classes in other packages. The abstract class Visitor, as well as
 * an Factory interface for trees, are defined as inner classes in
 * Tree.
 *
 * <p>To avoid ambiguities with the Tree API in com.sun.source all sub
 * classes should, by convention, start with JC (javac).
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 *
 * @see TreeMaker
 * @see TreeInfo
 * @see TreeTranslator
 * @see Pretty
 */
public abstract class JCTree implements Tree, Cloneable, DiagnosticPosition {

    /* Tree tag values, identifying kinds of trees */

    /** Toplevel nodes, of type TopLevel, representing entire source files.
     */
    public static final int  TOPLEVEL = 1;

    /** Import clauses, of type Import.
     */
    public static final int IMPORT = TOPLEVEL + 1;

    /** Class definitions, of type ClassDef.
     */
    public static final int CLASSDEF = IMPORT + 1;

    /** Method definitions, of type MethodDef.
     */
    public static final int METHODDEF = CLASSDEF + 1;

    /** Variable definitions, of type VarDef.
     */
    public static final int VARDEF = METHODDEF + 1;

    /** The no-op statement ";", of type Skip
     */
    public static final int SKIP = VARDEF + 1;

    /** Blocks, of type Block.
     */
    public static final int BLOCK = SKIP + 1;

    /** Do-while loops, of type DoLoop.
     */
    public static final int DOLOOP = BLOCK + 1;

    /** While-loops, of type WhileLoop.
     */
    public static final int WHILELOOP = DOLOOP + 1;

    /** For-loops, of type ForLoop.
     */
    public static final int FORLOOP = WHILELOOP + 1;

    /** Foreach-loops, of type ForeachLoop.
     */
    public static final int FOREACHLOOP = FORLOOP + 1;

    /** Labelled statements, of type Labelled.
     */
    public static final int LABELLED = FOREACHLOOP + 1;

    /** Switch statements, of type Switch.
     */
    public static final int SWITCH = LABELLED + 1;

    /** Case parts in switch statements, of type Case.
     */
    public static final int CASE = SWITCH + 1;

    /** Synchronized statements, of type Synchonized.
     */
    public static final int SYNCHRONIZED = CASE + 1;

    /** Try statements, of type Try.
     */
    public static final int TRY = SYNCHRONIZED + 1;

    /** Catch clauses in try statements, of type Catch.
     */
    public static final int CATCH = TRY + 1;

    /** Conditional expressions, of type Conditional.
     */
    public static final int CONDEXPR = CATCH + 1;

    /** Conditional statements, of type If.
     */
    public static final int IF = CONDEXPR + 1;

    /** Expression statements, of type Exec.
     */
    public static final int EXEC = IF + 1;

    /** Break statements, of type Break.
     */
    public static final int BREAK = EXEC + 1;

    /** Continue statements, of type Continue.
     */
    public static final int CONTINUE = BREAK + 1;

    /** Return statements, of type Return.
     */
    public static final int RETURN = CONTINUE + 1;

    /** Throw statements, of type Throw.
     */
    public static final int THROW = RETURN + 1;

    /** Assert statements, of type Assert.
     */
    public static final int ASSERT = THROW + 1;

    /** Method invocation expressions, of type Apply.
     */
    public static final int APPLY = ASSERT + 1;

    /** Class instance creation expressions, of type NewClass.
     */
    public static final int NEWCLASS = APPLY + 1;

    /** Array creation expressions, of type NewArray.
     */
    public static final int NEWARRAY = NEWCLASS + 1;

    /** Parenthesized subexpressions, of type Parens.
     */
    public static final int PARENS = NEWARRAY + 1;

    /** Assignment expressions, of type Assign.
     */
    public static final int ASSIGN = PARENS + 1;

    /** Type cast expressions, of type TypeCast.
     */
    public static final int TYPECAST = ASSIGN + 1;

    /** Type test expressions, of type TypeTest.
     */
    public static final int TYPETEST = TYPECAST + 1;

    /** Indexed array expressions, of type Indexed.
     */
    public static final int INDEXED = TYPETEST + 1;

    /** Selections, of type Select.
     */
    public static final int SELECT = INDEXED + 1;

    /** Simple identifiers, of type Ident.
     */
    public static final int IDENT = SELECT + 1;

    /** Literals, of type Literal.
     */
    public static final int LITERAL = IDENT + 1;

    /** Basic type identifiers, of type TypeIdent.
     */
    public static final int TYPEIDENT = LITERAL + 1;

    /** Array types, of type TypeArray.
     */
    public static final int TYPEARRAY = TYPEIDENT + 1;

    /** Parameterized types, of type TypeApply.
     */
    public static final int TYPEAPPLY = TYPEARRAY + 1;

    /** Disjunctive types, of type TypeDisjoint.
     */
    public static final int TYPEDISJOINT = TYPEAPPLY + 1;

    /** Formal type parameters, of type TypeParameter.
     */
    public static final int TYPEPARAMETER = TYPEDISJOINT + 1;

    /** Type argument.
     */
    public static final int WILDCARD = TYPEPARAMETER + 1;

    /** Bound kind: extends, super, exact, or unbound
     */
    public static final int TYPEBOUNDKIND = WILDCARD + 1;

    /** metadata: Annotation.
     */
    public static final int ANNOTATION = TYPEBOUNDKIND + 1;

    /** metadata: Modifiers
     */
    public static final int MODIFIERS = ANNOTATION + 1;

    public static final int ANNOTATED_TYPE = MODIFIERS + 1;

    /** Error trees, of type Erroneous.
     */
    public static final int ERRONEOUS = ANNOTATED_TYPE + 1;

    /** Unary operators, of type Unary.
     */
    public static final int POS = ERRONEOUS + 1;             // +
    public static final int NEG = POS + 1;                   // -
    public static final int NOT = NEG + 1;                   // !
    public static final int COMPL = NOT + 1;                 // ~
    public static final int PREINC = COMPL + 1;              // ++ _
    public static final int PREDEC = PREINC + 1;             // -- _
    public static final int POSTINC = PREDEC + 1;            // _ ++
    public static final int POSTDEC = POSTINC + 1;           // _ --

    /** unary operator for null reference checks, only used internally.
     */
    public static final int NULLCHK = POSTDEC + 1;

    /** Binary operators, of type Binary.
     */
    public static final int OR = NULLCHK + 1;                // ||
    public static final int AND = OR + 1;                    // &&
    public static final int BITOR = AND + 1;                 // |
    public static final int BITXOR = BITOR + 1;              // ^
    public static final int BITAND = BITXOR + 1;             // &
    public static final int EQ = BITAND + 1;                 // ==
    public static final int NE = EQ + 1;                     // !=
    public static final int LT = NE + 1;                     // <
    public static final int GT = LT + 1;                     // >
    public static final int LE = GT + 1;                     // <=
    public static final int GE = LE + 1;                     // >=
    public static final int SL = GE + 1;                     // <<
    public static final int SR = SL + 1;                     // >>
    public static final int USR = SR + 1;                    // >>>
    public static final int PLUS = USR + 1;                  // +
    public static final int MINUS = PLUS + 1;                // -
    public static final int MUL = MINUS + 1;                 // *
    public static final int DIV = MUL + 1;                   // /
    public static final int MOD = DIV + 1;                   // %

    /** Assignment operators, of type Assignop.
     */
    public static final int BITOR_ASG = MOD + 1;             // |=
    public static final int BITXOR_ASG = BITOR_ASG + 1;      // ^=
    public static final int BITAND_ASG = BITXOR_ASG + 1;     // &=

    public static final int SL_ASG = SL + BITOR_ASG - BITOR; // <<=
    public static final int SR_ASG = SL_ASG + 1;             // >>=
    public static final int USR_ASG = SR_ASG + 1;            // >>>=
    public static final int PLUS_ASG = USR_ASG + 1;          // +=
    public static final int MINUS_ASG = PLUS_ASG + 1;        // -=
    public static final int MUL_ASG = MINUS_ASG + 1;         // *=
    public static final int DIV_ASG = MUL_ASG + 1;           // /=
    public static final int MOD_ASG = DIV_ASG + 1;           // %=

    /** A synthetic let expression, of type LetExpr.
     */
    public static final int LETEXPR = MOD_ASG + 1;           // ala scheme


    /** The offset between assignment operators and normal operators.
     */
    public static final int ASGOffset = BITOR_ASG - BITOR;

    /* The (encoded) position in the source file. @see util.Position.
     */
    public int pos;

    /* The type of this node.
     */
    public Type type;

    /* The tag of this node -- one of the constants declared above.
     */
    public abstract int getTag();

    /** Convert a tree to a pretty-printed string. */
    @Override
    public String toString() {
        StringWriter s = new StringWriter();
        try {
            new Pretty(s, false).printExpr(this);
        }
        catch (IOException e) {
            // should never happen, because StringWriter is defined
            // never to throw any IOExceptions
            throw new AssertionError(e);
        }
        return s.toString();
    }

    /** Set position field and return this tree.
     */
    public JCTree setPos(int pos) {
        this.pos = pos;
        return this;
    }

    /** Set type field and return this tree.
     */
    public JCTree setType(Type type) {
        this.type = type;
        return this;
    }

    /** Visit this tree with a given visitor.
     */
    public abstract void accept(Visitor v);

    public abstract <R,D> R accept(TreeVisitor<R,D> v, D d);

    /** Return a shallow copy of this tree.
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch(CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a default position for this tree node.
     */
    public DiagnosticPosition pos() {
        return this;
    }

    // for default DiagnosticPosition
    public JCTree getTree() {
        return this;
    }

    // for default DiagnosticPosition
    public int getStartPosition() {
        return TreeInfo.getStartPos(this);
    }

    // for default DiagnosticPosition
    public int getPreferredPosition() {
        return pos;
    }

    // for default DiagnosticPosition
    public int getEndPosition(Map<JCTree, Integer> endPosTable) {
        return TreeInfo.getEndPos(this, endPosTable);
    }

    /**
     * Everything in one source file is kept in a TopLevel structure.
     * @param pid              The tree representing the package clause.
     * @param sourcefile       The source file name.
     * @param defs             All definitions in this file (ClassDef, Import, and Skip)
     * @param packge           The package it belongs to.
     * @param namedImportScope A scope for all named imports.
     * @param starImportScope  A scope for all import-on-demands.
     * @param lineMap          Line starting positions, defined only
     *                         if option -g is set.
     * @param docComments      A hashtable that stores all documentation comments
     *                         indexed by the tree nodes they refer to.
     *                         defined only if option -s is set.
     * @param endPositions     A hashtable that stores ending positions of source
     *                         ranges indexed by the tree nodes they belong to.
     *                         Defined only if option -Xjcov is set.
     */
    public static class JCCompilationUnit extends JCTree implements CompilationUnitTree {
        public List<JCAnnotation> packageAnnotations;
        public JCExpression pid;
        public List<JCTree> defs;
        public JavaFileObject sourcefile;
        public PackageSymbol packge;
        public Scope namedImportScope;
        public Scope starImportScope;
        public long flags;
        public Position.LineMap lineMap = null;
        public Map<JCTree, String> docComments = null;
        public Map<JCTree, Integer> endPositions = null;
        protected JCCompilationUnit(List<JCAnnotation> packageAnnotations,
                        JCExpression pid,
                        List<JCTree> defs,
                        JavaFileObject sourcefile,
                        PackageSymbol packge,
                        Scope namedImportScope,
                        Scope starImportScope) {
            this.packageAnnotations = packageAnnotations;
            this.pid = pid;
            this.defs = defs;
            this.sourcefile = sourcefile;
            this.packge = packge;
            this.namedImportScope = namedImportScope;
            this.starImportScope = starImportScope;
        }
        @Override
        public void accept(Visitor v) { v.visitTopLevel(this); }

        public Kind getKind() { return Kind.COMPILATION_UNIT; }
        public List<JCAnnotation> getPackageAnnotations() {
            return packageAnnotations;
        }
        public List<JCImport> getImports() {
            ListBuffer<JCImport> imports = new ListBuffer<JCImport>();
            for (JCTree tree : defs) {
                if (tree.getTag() == IMPORT)
                    imports.append((JCImport)tree);
                else
                    break;
            }
            return imports.toList();
        }
        public JCExpression getPackageName() { return pid; }
        public JavaFileObject getSourceFile() {
            return sourcefile;
        }
        public Position.LineMap getLineMap() {
            return lineMap;
        }
        public List<JCTree> getTypeDecls() {
            List<JCTree> typeDefs;
            for (typeDefs = defs; !typeDefs.isEmpty(); typeDefs = typeDefs.tail)
                if (typeDefs.head.getTag() != IMPORT)
                    break;
            return typeDefs;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCompilationUnit(this, d);
        }

        @Override
        public int getTag() {
            return TOPLEVEL;
        }
    }

    /**
     * An import clause.
     * @param qualid    The imported class(es).
     */
    public static class JCImport extends JCTree implements ImportTree {
        public boolean staticImport;
        public JCTree qualid;
        protected JCImport(JCTree qualid, boolean importStatic) {
            this.qualid = qualid;
            this.staticImport = importStatic;
        }
        @Override
        public void accept(Visitor v) { v.visitImport(this); }

        public boolean isStatic() { return staticImport; }
        public JCTree getQualifiedIdentifier() { return qualid; }

        public Kind getKind() { return Kind.IMPORT; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitImport(this, d);
        }

        @Override
        public int getTag() {
            return IMPORT;
        }
    }

    public static abstract class JCStatement extends JCTree implements StatementTree {
        @Override
        public JCStatement setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCStatement setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    public static abstract class JCExpression extends JCTree implements ExpressionTree {
        @Override
        public JCExpression setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCExpression setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    /**
     * A class definition.
     * @param modifiers the modifiers
     * @param name the name of the class
     * @param typarams formal class parameters
     * @param extending the classes this class extends
     * @param implementing the interfaces implemented by this class
     * @param defs all variables and methods defined in this class
     * @param sym the symbol
     */
    public static class JCClassDecl extends JCStatement implements ClassTree {
        public JCModifiers mods;
        public Name name;
        public List<JCTypeParameter> typarams;
        public JCTree extending;
        public List<JCExpression> implementing;
        public List<JCTree> defs;
        public ClassSymbol sym;
        protected JCClassDecl(JCModifiers mods,
                           Name name,
                           List<JCTypeParameter> typarams,
                           JCTree extending,
                           List<JCExpression> implementing,
                           List<JCTree> defs,
                           ClassSymbol sym)
        {
            this.mods = mods;
            this.name = name;
            this.typarams = typarams;
            this.extending = extending;
            this.implementing = implementing;
            this.defs = defs;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitClassDef(this); }

        public Kind getKind() {
            if ((mods.flags & Flags.ANNOTATION) != 0)
                return Kind.ANNOTATION_TYPE;
            else if ((mods.flags & Flags.INTERFACE) != 0)
                return Kind.INTERFACE;
            else if ((mods.flags & Flags.ENUM) != 0)
                return Kind.ENUM;
            else
                return Kind.CLASS;
        }

        public JCModifiers getModifiers() { return mods; }
        public Name getSimpleName() { return name; }
        public List<JCTypeParameter> getTypeParameters() {
            return typarams;
        }
        public JCTree getExtendsClause() { return extending; }
        public List<JCExpression> getImplementsClause() {
            return implementing;
        }
        public List<JCTree> getMembers() {
            return defs;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitClass(this, d);
        }

        @Override
        public int getTag() {
            return CLASSDEF;
        }
    }

    /**
     * A method definition.
     * @param modifiers method modifiers
     * @param name method name
     * @param restype type of method return value
     * @param typarams type parameters
     * @param params value parameters
     * @param thrown exceptions thrown by this method
     * @param stats statements in the method
     * @param sym method symbol
     */
    public static class JCMethodDecl extends JCTree implements MethodTree {
        public JCModifiers mods;
        public Name name;
        public JCExpression restype;
        public List<JCTypeParameter> typarams;
        public List<JCVariableDecl> params;
        public List<JCTypeAnnotation> receiverAnnotations;
        public List<JCExpression> thrown;
        public JCBlock body;
        public JCExpression defaultValue; // for annotation types
        public MethodSymbol sym;
        protected JCMethodDecl(JCModifiers mods,
                            Name name,
                            JCExpression restype,
                            List<JCTypeParameter> typarams,
                            List<JCVariableDecl> params,
                            List<JCTypeAnnotation> receiver,
                            List<JCExpression> thrown,
                            JCBlock body,
                            JCExpression defaultValue,
                            MethodSymbol sym)
        {
            this.mods = mods;
            this.name = name;
            this.restype = restype;
            this.typarams = typarams;
            this.params = params;
            this.receiverAnnotations = (receiver != null ? receiver : List.<JCTypeAnnotation>nil());
            this.thrown = thrown;
            this.body = body;
            this.defaultValue = defaultValue;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitMethodDef(this); }

        public Kind getKind() { return Kind.METHOD; }
        public JCModifiers getModifiers() { return mods; }
        public Name getName() { return name; }
        public JCTree getReturnType() { return restype; }
        public List<JCTypeParameter> getTypeParameters() {
            return typarams;
        }
        public List<JCVariableDecl> getParameters() {
            return params;
        }
        public List<JCTypeAnnotation> getReceiverAnnotations() { return receiverAnnotations; }
        public List<JCExpression> getThrows() {
            return thrown;
        }
        public JCBlock getBody() { return body; }
        public JCTree getDefaultValue() { // for annotation types
            return defaultValue;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMethod(this, d);
        }

        @Override
        public int getTag() {
            return METHODDEF;
        }
  }

    /**
     * A variable definition.
     * @param modifiers variable modifiers
     * @param name variable name
     * @param vartype type of the variable
     * @param init variables initial value
     * @param sym symbol
     */
    public static class JCVariableDecl extends JCStatement implements VariableTree {
        public JCModifiers mods;
        public Name name;
        public JCExpression vartype;
        public JCExpression init;
        public VarSymbol sym;
        protected JCVariableDecl(JCModifiers mods,
                         Name name,
                         JCExpression vartype,
                         JCExpression init,
                         VarSymbol sym) {
            this.mods = mods;
            this.name = name;
            this.vartype = vartype;
            this.init = init;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitVarDef(this); }

        public Kind getKind() { return Kind.VARIABLE; }
        public JCModifiers getModifiers() { return mods; }
        public Name getName() { return name; }
        public JCTree getType() { return vartype; }
        public JCExpression getInitializer() {
            return init;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitVariable(this, d);
        }

        @Override
        public int getTag() {
            return VARDEF;
        }
    }

      /**
     * A no-op statement ";".
     */
    public static class JCSkip extends JCStatement implements EmptyStatementTree {
        protected JCSkip() {
        }
        @Override
        public void accept(Visitor v) { v.visitSkip(this); }

        public Kind getKind() { return Kind.EMPTY_STATEMENT; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitEmptyStatement(this, d);
        }

        @Override
        public int getTag() {
            return SKIP;
        }
    }

    /**
     * A statement block.
     * @param stats statements
     * @param flags flags
     */
    public static class JCBlock extends JCStatement implements BlockTree {
        public long flags;
        public List<JCStatement> stats;
        /** Position of closing brace, optional. */
        public int endpos = Position.NOPOS;
        protected JCBlock(long flags, List<JCStatement> stats) {
            this.stats = stats;
            this.flags = flags;
        }
        @Override
        public void accept(Visitor v) { v.visitBlock(this); }

        public Kind getKind() { return Kind.BLOCK; }
        public List<JCStatement> getStatements() {
            return stats;
        }
        public boolean isStatic() { return (flags & Flags.STATIC) != 0; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBlock(this, d);
        }

        @Override
        public int getTag() {
            return BLOCK;
        }
    }

    /**
     * A do loop
     */
    public static class JCDoWhileLoop extends JCStatement implements DoWhileLoopTree {
        public JCStatement body;
        public JCExpression cond;
        protected JCDoWhileLoop(JCStatement body, JCExpression cond) {
            this.body = body;
            this.cond = cond;
        }
        @Override
        public void accept(Visitor v) { v.visitDoLoop(this); }

        public Kind getKind() { return Kind.DO_WHILE_LOOP; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitDoWhileLoop(this, d);
        }

        @Override
        public int getTag() {
            return DOLOOP;
        }
    }

    /**
     * A while loop
     */
    public static class JCWhileLoop extends JCStatement implements WhileLoopTree {
        public JCExpression cond;
        public JCStatement body;
        protected JCWhileLoop(JCExpression cond, JCStatement body) {
            this.cond = cond;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitWhileLoop(this); }

        public Kind getKind() { return Kind.WHILE_LOOP; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitWhileLoop(this, d);
        }

        @Override
        public int getTag() {
            return WHILELOOP;
        }
    }

    /**
     * A for loop.
     */
    public static class JCForLoop extends JCStatement implements ForLoopTree {
        public List<JCStatement> init;
        public JCExpression cond;
        public List<JCExpressionStatement> step;
        public JCStatement body;
        protected JCForLoop(List<JCStatement> init,
                          JCExpression cond,
                          List<JCExpressionStatement> update,
                          JCStatement body)
        {
            this.init = init;
            this.cond = cond;
            this.step = update;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitForLoop(this); }

        public Kind getKind() { return Kind.FOR_LOOP; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getStatement() { return body; }
        public List<JCStatement> getInitializer() {
            return init;
        }
        public List<JCExpressionStatement> getUpdate() {
            return step;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitForLoop(this, d);
        }

        @Override
        public int getTag() {
            return FORLOOP;
        }
    }

    /**
     * The enhanced for loop.
     */
    public static class JCEnhancedForLoop extends JCStatement implements EnhancedForLoopTree {
        public JCVariableDecl var;
        public JCExpression expr;
        public JCStatement body;
        protected JCEnhancedForLoop(JCVariableDecl var, JCExpression expr, JCStatement body) {
            this.var = var;
            this.expr = expr;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitForeachLoop(this); }

        public Kind getKind() { return Kind.ENHANCED_FOR_LOOP; }
        public JCVariableDecl getVariable() { return var; }
        public JCExpression getExpression() { return expr; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitEnhancedForLoop(this, d);
        }
        @Override
        public int getTag() {
            return FOREACHLOOP;
        }
    }

    /**
     * A labelled expression or statement.
     */
    public static class JCLabeledStatement extends JCStatement implements LabeledStatementTree {
        public Name label;
        public JCStatement body;
        protected JCLabeledStatement(Name label, JCStatement body) {
            this.label = label;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitLabelled(this); }
        public Kind getKind() { return Kind.LABELED_STATEMENT; }
        public Name getLabel() { return label; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitLabeledStatement(this, d);
        }
        @Override
        public int getTag() {
            return LABELLED;
        }
    }

    /**
     * A "switch ( ) { }" construction.
     */
    public static class JCSwitch extends JCStatement implements SwitchTree {
        public JCExpression selector;
        public List<JCCase> cases;
        protected JCSwitch(JCExpression selector, List<JCCase> cases) {
            this.selector = selector;
            this.cases = cases;
        }
        @Override
        public void accept(Visitor v) { v.visitSwitch(this); }

        public Kind getKind() { return Kind.SWITCH; }
        public JCExpression getExpression() { return selector; }
        public List<JCCase> getCases() { return cases; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitSwitch(this, d);
        }
        @Override
        public int getTag() {
            return SWITCH;
        }
    }

    /**
     * A "case  :" of a switch.
     */
    public static class JCCase extends JCStatement implements CaseTree {
        public JCExpression pat;
        public List<JCStatement> stats;
        protected JCCase(JCExpression pat, List<JCStatement> stats) {
            this.pat = pat;
            this.stats = stats;
        }
        @Override
        public void accept(Visitor v) { v.visitCase(this); }

        public Kind getKind() { return Kind.CASE; }
        public JCExpression getExpression() { return pat; }
        public List<JCStatement> getStatements() { return stats; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCase(this, d);
        }
        @Override
        public int getTag() {
            return CASE;
        }
    }

    /**
     * A synchronized block.
     */
    public static class JCSynchronized extends JCStatement implements SynchronizedTree {
        public JCExpression lock;
        public JCBlock body;
        protected JCSynchronized(JCExpression lock, JCBlock body) {
            this.lock = lock;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitSynchronized(this); }

        public Kind getKind() { return Kind.SYNCHRONIZED; }
        public JCExpression getExpression() { return lock; }
        public JCBlock getBlock() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitSynchronized(this, d);
        }
        @Override
        public int getTag() {
            return SYNCHRONIZED;
        }
    }

    /**
     * A "try { } catch ( ) { } finally { }" block.
     */
    public static class JCTry extends JCStatement implements TryTree {
        public JCBlock body;
        public List<JCCatch> catchers;
        public JCBlock finalizer;
        public List<JCTree> resources;
        protected JCTry(List<JCTree> resources,
                        JCBlock body,
                        List<JCCatch> catchers,
                        JCBlock finalizer) {
            this.body = body;
            this.catchers = catchers;
            this.finalizer = finalizer;
            this.resources = resources;
        }
        @Override
        public void accept(Visitor v) { v.visitTry(this); }

        public Kind getKind() { return Kind.TRY; }
        public JCBlock getBlock() { return body; }
        public List<JCCatch> getCatches() {
            return catchers;
        }
        public JCBlock getFinallyBlock() { return finalizer; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTry(this, d);
        }
        @Override
        public List<? extends JCTree> getResources() {
            return resources;
        }
        @Override
        public int getTag() {
            return TRY;
        }
    }

    /**
     * A catch block.
     */
    public static class JCCatch extends JCTree implements CatchTree {
        public JCVariableDecl param;
        public JCBlock body;
        protected JCCatch(JCVariableDecl param, JCBlock body) {
            this.param = param;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitCatch(this); }

        public Kind getKind() { return Kind.CATCH; }
        public JCVariableDecl getParameter() { return param; }
        public JCBlock getBlock() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCatch(this, d);
        }
        @Override
        public int getTag() {
            return CATCH;
        }
    }

    /**
     * A ( ) ? ( ) : ( ) conditional expression
     */
    public static class JCConditional extends JCExpression implements ConditionalExpressionTree {
        public JCExpression cond;
        public JCExpression truepart;
        public JCExpression falsepart;
        protected JCConditional(JCExpression cond,
                              JCExpression truepart,
                              JCExpression falsepart)
        {
            this.cond = cond;
            this.truepart = truepart;
            this.falsepart = falsepart;
        }
        @Override
        public void accept(Visitor v) { v.visitConditional(this); }

        public Kind getKind() { return Kind.CONDITIONAL_EXPRESSION; }
        public JCExpression getCondition() { return cond; }
        public JCExpression getTrueExpression() { return truepart; }
        public JCExpression getFalseExpression() { return falsepart; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitConditionalExpression(this, d);
        }
        @Override
        public int getTag() {
            return CONDEXPR;
        }
    }

    /**
     * An "if ( ) { } else { }" block
     */
    public static class JCIf extends JCStatement implements IfTree {
        public JCExpression cond;
        public JCStatement thenpart;
        public JCStatement elsepart;
        protected JCIf(JCExpression cond,
                     JCStatement thenpart,
                     JCStatement elsepart)
        {
            this.cond = cond;
            this.thenpart = thenpart;
            this.elsepart = elsepart;
        }
        @Override
        public void accept(Visitor v) { v.visitIf(this); }

        public Kind getKind() { return Kind.IF; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getThenStatement() { return thenpart; }
        public JCStatement getElseStatement() { return elsepart; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIf(this, d);
        }
        @Override
        public int getTag() {
            return IF;
        }
    }

    /**
     * an expression statement
     * @param expr expression structure
     */
    public static class JCExpressionStatement extends JCStatement implements ExpressionStatementTree {
        public JCExpression expr;
        protected JCExpressionStatement(JCExpression expr)
        {
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitExec(this); }

        public Kind getKind() { return Kind.EXPRESSION_STATEMENT; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitExpressionStatement(this, d);
        }
        @Override
        public int getTag() {
            return EXEC;
        }
    }

    /**
     * A break from a loop or switch.
     */
    public static class JCBreak extends JCStatement implements BreakTree {
        public Name label;
        public JCTree target;
        protected JCBreak(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }
        @Override
        public void accept(Visitor v) { v.visitBreak(this); }

        public Kind getKind() { return Kind.BREAK; }
        public Name getLabel() { return label; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBreak(this, d);
        }
        @Override
        public int getTag() {
            return BREAK;
        }
    }

    /**
     * A continue of a loop.
     */
    public static class JCContinue extends JCStatement implements ContinueTree {
        public Name label;
        public JCTree target;
        protected JCContinue(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }
        @Override
        public void accept(Visitor v) { v.visitContinue(this); }

        public Kind getKind() { return Kind.CONTINUE; }
        public Name getLabel() { return label; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitContinue(this, d);
        }
        @Override
        public int getTag() {
            return CONTINUE;
        }
    }

    /**
     * A return statement.
     */
    public static class JCReturn extends JCStatement implements ReturnTree {
        public JCExpression expr;
        protected JCReturn(JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitReturn(this); }

        public Kind getKind() { return Kind.RETURN; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitReturn(this, d);
        }
        @Override
        public int getTag() {
            return RETURN;
        }
    }

    /**
     * A throw statement.
     */
    public static class JCThrow extends JCStatement implements ThrowTree {
        public JCExpression expr;
        protected JCThrow(JCTree expr) {
            this.expr = (JCExpression)expr;
        }
        @Override
        public void accept(Visitor v) { v.visitThrow(this); }

        public Kind getKind() { return Kind.THROW; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitThrow(this, d);
        }
        @Override
        public int getTag() {
            return THROW;
        }
    }

    /**
     * An assert statement.
     */
    public static class JCAssert extends JCStatement implements AssertTree {
        public JCExpression cond;
        public JCExpression detail;
        protected JCAssert(JCExpression cond, JCExpression detail) {
            this.cond = cond;
            this.detail = detail;
        }
        @Override
        public void accept(Visitor v) { v.visitAssert(this); }

        public Kind getKind() { return Kind.ASSERT; }
        public JCExpression getCondition() { return cond; }
        public JCExpression getDetail() { return detail; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAssert(this, d);
        }
        @Override
        public int getTag() {
            return ASSERT;
        }
    }

    /**
     * A method invocation
     */
    public static class JCMethodInvocation extends JCExpression implements MethodInvocationTree {
        public List<JCExpression> typeargs;
        public JCExpression meth;
        public List<JCExpression> args;
        public Type varargsElement;
        protected JCMethodInvocation(List<JCExpression> typeargs,
                        JCExpression meth,
                        List<JCExpression> args)
        {
            this.typeargs = (typeargs == null) ? List.<JCExpression>nil()
                                               : typeargs;
            this.meth = meth;
            this.args = args;
        }
        @Override
        public void accept(Visitor v) { v.visitApply(this); }

        public Kind getKind() { return Kind.METHOD_INVOCATION; }
        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }
        public JCExpression getMethodSelect() { return meth; }
        public List<JCExpression> getArguments() {
            return args;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMethodInvocation(this, d);
        }
        @Override
        public JCMethodInvocation setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public int getTag() {
            return(APPLY);
        }
    }

    /**
     * A new(...) operation.
     */
    public static class JCNewClass extends JCExpression implements NewClassTree {
        public JCExpression encl;
        public List<JCExpression> typeargs;
        public JCExpression clazz;
        public List<JCExpression> args;
        public JCClassDecl def;
        public Symbol constructor;
        public Type varargsElement;
        public Type constructorType;
        protected JCNewClass(JCExpression encl,
                           List<JCExpression> typeargs,
                           JCExpression clazz,
                           List<JCExpression> args,
                           JCClassDecl def)
        {
            this.encl = encl;
            this.typeargs = (typeargs == null) ? List.<JCExpression>nil()
                                               : typeargs;
            this.clazz = clazz;
            this.args = args;
            this.def = def;
        }
        @Override
        public void accept(Visitor v) { v.visitNewClass(this); }

        public Kind getKind() { return Kind.NEW_CLASS; }
        public JCExpression getEnclosingExpression() { // expr.new C< ... > ( ... )
            return encl;
        }
        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }
        public JCExpression getIdentifier() { return clazz; }
        public List<JCExpression> getArguments() {
            return args;
        }
        public JCClassDecl getClassBody() { return def; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitNewClass(this, d);
        }
        @Override
        public int getTag() {
            return NEWCLASS;
        }
    }

    /**
     * A new[...] operation.
     */
    public static class JCNewArray extends JCExpression implements NewArrayTree {
        public JCExpression elemtype;
        public List<JCExpression> dims;
        public List<JCTypeAnnotation> annotations;
        public List<List<JCTypeAnnotation>> dimAnnotations;
        public List<JCExpression> elems;
        protected JCNewArray(JCExpression elemtype,
                           List<JCExpression> dims,
                           List<JCExpression> elems)
        {
            this.elemtype = elemtype;
            this.dims = dims;
            this.annotations = List.nil();
            this.dimAnnotations = List.nil();
            this.elems = elems;
        }
        @Override
        public void accept(Visitor v) { v.visitNewArray(this); }

        public Kind getKind() { return Kind.NEW_ARRAY; }
        public JCExpression getType() { return elemtype; }
        public List<JCExpression> getDimensions() {
            return dims;
        }
        public List<JCExpression> getInitializers() {
            return elems;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitNewArray(this, d);
        }
        @Override
        public int getTag() {
            return NEWARRAY;
        }
    }

    /**
     * A parenthesized subexpression ( ... )
     */
    public static class JCParens extends JCExpression implements ParenthesizedTree {
        public JCExpression expr;
        protected JCParens(JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitParens(this); }

        public Kind getKind() { return Kind.PARENTHESIZED; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitParenthesized(this, d);
        }
        @Override
        public int getTag() {
            return PARENS;
        }
    }

    /**
     * A assignment with "=".
     */
    public static class JCAssign extends JCExpression implements AssignmentTree {
        public JCExpression lhs;
        public JCExpression rhs;
        protected JCAssign(JCExpression lhs, JCExpression rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }
        @Override
        public void accept(Visitor v) { v.visitAssign(this); }

        public Kind getKind() { return Kind.ASSIGNMENT; }
        public JCExpression getVariable() { return lhs; }
        public JCExpression getExpression() { return rhs; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAssignment(this, d);
        }
        @Override
        public int getTag() {
            return ASSIGN;
        }
    }

    /**
     * An assignment with "+=", "|=" ...
     */
    public static class JCAssignOp extends JCExpression implements CompoundAssignmentTree {
        private int opcode;
        public JCExpression lhs;
        public JCExpression rhs;
        public Symbol operator;
        protected JCAssignOp(int opcode, JCTree lhs, JCTree rhs, Symbol operator) {
            this.opcode = opcode;
            this.lhs = (JCExpression)lhs;
            this.rhs = (JCExpression)rhs;
            this.operator = operator;
        }
        @Override
        public void accept(Visitor v) { v.visitAssignop(this); }

        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        public JCExpression getVariable() { return lhs; }
        public JCExpression getExpression() { return rhs; }
        public Symbol getOperator() {
            return operator;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCompoundAssignment(this, d);
        }
        @Override
        public int getTag() {
            return opcode;
        }
    }

    /**
     * A unary operation.
     */
    public static class JCUnary extends JCExpression implements UnaryTree {
        private int opcode;
        public JCExpression arg;
        public Symbol operator;
        protected JCUnary(int opcode, JCExpression arg) {
            this.opcode = opcode;
            this.arg = arg;
        }
        @Override
        public void accept(Visitor v) { v.visitUnary(this); }

        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        public JCExpression getExpression() { return arg; }
        public Symbol getOperator() {
            return operator;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitUnary(this, d);
        }
        @Override
        public int getTag() {
            return opcode;
        }

        public void setTag(int tag) {
            opcode = tag;
        }
    }

    /**
     * A binary operation.
     */
    public static class JCBinary extends JCExpression implements BinaryTree {
        private int opcode;
        public JCExpression lhs;
        public JCExpression rhs;
        public Symbol operator;
        protected JCBinary(int opcode,
                         JCExpression lhs,
                         JCExpression rhs,
                         Symbol operator) {
            this.opcode = opcode;
            this.lhs = lhs;
            this.rhs = rhs;
            this.operator = operator;
        }
        @Override
        public void accept(Visitor v) { v.visitBinary(this); }

        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        public JCExpression getLeftOperand() { return lhs; }
        public JCExpression getRightOperand() { return rhs; }
        public Symbol getOperator() {
            return operator;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBinary(this, d);
        }
        @Override
        public int getTag() {
            return opcode;
        }
    }

    /**
     * A type cast.
     */
    public static class JCTypeCast extends JCExpression implements TypeCastTree {
        public JCTree clazz;
        public JCExpression expr;
        protected JCTypeCast(JCTree clazz, JCExpression expr) {
            this.clazz = clazz;
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeCast(this); }

        public Kind getKind() { return Kind.TYPE_CAST; }
        public JCTree getType() { return clazz; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTypeCast(this, d);
        }
        @Override
        public int getTag() {
            return TYPECAST;
        }
    }

    /**
     * A type test.
     */
    public static class JCInstanceOf extends JCExpression implements InstanceOfTree {
        public JCExpression expr;
        public JCTree clazz;
        protected JCInstanceOf(JCExpression expr, JCTree clazz) {
            this.expr = expr;
            this.clazz = clazz;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeTest(this); }

        public Kind getKind() { return Kind.INSTANCE_OF; }
        public JCTree getType() { return clazz; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitInstanceOf(this, d);
        }
        @Override
        public int getTag() {
            return TYPETEST;
        }
    }

    /**
     * An array selection
     */
    public static class JCArrayAccess extends JCExpression implements ArrayAccessTree {
        public JCExpression indexed;
        public JCExpression index;
        protected JCArrayAccess(JCExpression indexed, JCExpression index) {
            this.indexed = indexed;
            this.index = index;
        }
        @Override
        public void accept(Visitor v) { v.visitIndexed(this); }

        public Kind getKind() { return Kind.ARRAY_ACCESS; }
        public JCExpression getExpression() { return indexed; }
        public JCExpression getIndex() { return index; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitArrayAccess(this, d);
        }
        @Override
        public int getTag() {
            return INDEXED;
        }
    }

    /**
     * Selects through packages and classes
     * @param selected selected Tree hierarchie
     * @param selector name of field to select thru
     * @param sym symbol of the selected class
     */
    public static class JCFieldAccess extends JCExpression implements MemberSelectTree {
        public JCExpression selected;
        public Name name;
        public Symbol sym;
        protected JCFieldAccess(JCExpression selected, Name name, Symbol sym) {
            this.selected = selected;
            this.name = name;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitSelect(this); }

        public Kind getKind() { return Kind.MEMBER_SELECT; }
        public JCExpression getExpression() { return selected; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMemberSelect(this, d);
        }
        public Name getIdentifier() { return name; }
        @Override
        public int getTag() {
            return SELECT;
        }
    }

    /**
     * An identifier
     * @param idname the name
     * @param sym the symbol
     */
    public static class JCIdent extends JCExpression implements IdentifierTree {
        public Name name;
        public Symbol sym;
        protected JCIdent(Name name, Symbol sym) {
            this.name = name;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitIdent(this); }

        public Kind getKind() { return Kind.IDENTIFIER; }
        public Name getName() { return name; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIdentifier(this, d);
        }
        public int getTag() {
            return IDENT;
        }
    }

    /**
     * A constant value given literally.
     * @param value value representation
     */
    public static class JCLiteral extends JCExpression implements LiteralTree {
        public int typetag;
        public Object value;
        protected JCLiteral(int typetag, Object value) {
            this.typetag = typetag;
            this.value = value;
        }
        @Override
        public void accept(Visitor v) { v.visitLiteral(this); }

        public Kind getKind() {
            switch (typetag) {
            case TypeTags.INT:
                return Kind.INT_LITERAL;
            case TypeTags.LONG:
                return Kind.LONG_LITERAL;
            case TypeTags.FLOAT:
                return Kind.FLOAT_LITERAL;
            case TypeTags.DOUBLE:
                return Kind.DOUBLE_LITERAL;
            case TypeTags.BOOLEAN:
                return Kind.BOOLEAN_LITERAL;
            case TypeTags.CHAR:
                return Kind.CHAR_LITERAL;
            case TypeTags.CLASS:
                return Kind.STRING_LITERAL;
            case TypeTags.BOT:
                return Kind.NULL_LITERAL;
            default:
                throw new AssertionError("unknown literal kind " + this);
            }
        }
        public Object getValue() {
            switch (typetag) {
                case TypeTags.BOOLEAN:
                    int bi = (Integer) value;
                    return (bi != 0);
                case TypeTags.CHAR:
                    int ci = (Integer) value;
                    char c = (char) ci;
                    if (c != ci)
                        throw new AssertionError("bad value for char literal");
                    return c;
                default:
                    return value;
            }
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitLiteral(this, d);
        }
        @Override
        public JCLiteral setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public int getTag() {
            return LITERAL;
        }
    }

    /**
     * Identifies a basic type.
     * @param tag the basic type id
     * @see TypeTags
     */
    public static class JCPrimitiveTypeTree extends JCExpression implements PrimitiveTypeTree {
        public int typetag;
        protected JCPrimitiveTypeTree(int typetag) {
            this.typetag = typetag;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeIdent(this); }

        public Kind getKind() { return Kind.PRIMITIVE_TYPE; }
        public TypeKind getPrimitiveTypeKind() {
            switch (typetag) {
            case TypeTags.BOOLEAN:
                return TypeKind.BOOLEAN;
            case TypeTags.BYTE:
                return TypeKind.BYTE;
            case TypeTags.SHORT:
                return TypeKind.SHORT;
            case TypeTags.INT:
                return TypeKind.INT;
            case TypeTags.LONG:
                return TypeKind.LONG;
            case TypeTags.CHAR:
                return TypeKind.CHAR;
            case TypeTags.FLOAT:
                return TypeKind.FLOAT;
            case TypeTags.DOUBLE:
                return TypeKind.DOUBLE;
            case TypeTags.VOID:
                return TypeKind.VOID;
            default:
                throw new AssertionError("unknown primitive type " + this);
            }
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitPrimitiveType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEIDENT;
        }
    }

    /**
     * An array type, A[]
     */
    public static class JCArrayTypeTree extends JCExpression implements ArrayTypeTree {
        public JCExpression elemtype;
        protected JCArrayTypeTree(JCExpression elemtype) {
            this.elemtype = elemtype;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeArray(this); }

        public Kind getKind() { return Kind.ARRAY_TYPE; }
        public JCTree getType() { return elemtype; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitArrayType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEARRAY;
        }
    }

    /**
     * A parameterized type, T<...>
     */
    public static class JCTypeApply extends JCExpression implements ParameterizedTypeTree {
        public JCExpression clazz;
        public List<JCExpression> arguments;
        protected JCTypeApply(JCExpression clazz, List<JCExpression> arguments) {
            this.clazz = clazz;
            this.arguments = arguments;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeApply(this); }

        public Kind getKind() { return Kind.PARAMETERIZED_TYPE; }
        public JCTree getType() { return clazz; }
        public List<JCExpression> getTypeArguments() {
            return arguments;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitParameterizedType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEAPPLY;
        }
    }

    /**
     * A disjoint type, T1 | T2 | ... Tn (used in multicatch statements)
     */
    public static class JCTypeDisjoint extends JCExpression implements DisjointTypeTree {

        public List<JCExpression> components;

        protected JCTypeDisjoint(List<JCExpression> components) {
            this.components = components;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeDisjoint(this); }

        public Kind getKind() { return Kind.DISJOINT_TYPE; }

        public List<JCExpression> getTypeComponents() {
            return components;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitDisjointType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEDISJOINT;
        }
    }

    /**
     * A formal class parameter.
     * @param name name
     * @param bounds bounds
     */
    public static class JCTypeParameter extends JCTree implements TypeParameterTree {
        public Name name;
        public List<JCExpression> bounds;
        public List<JCTypeAnnotation> annotations;
        protected JCTypeParameter(Name name, List<JCExpression> bounds, List<JCTypeAnnotation> annotations) {
            this.name = name;
            this.bounds = bounds;
            this.annotations = annotations;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeParameter(this); }

        public Kind getKind() { return Kind.TYPE_PARAMETER; }
        public Name getName() { return name; }
        public List<JCExpression> getBounds() {
            return bounds;
        }
        public List<JCTypeAnnotation> getAnnotations() {
            return annotations;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTypeParameter(this, d);
        }
        @Override
        public int getTag() {
            return TYPEPARAMETER;
        }
    }

    public static class JCWildcard extends JCExpression implements WildcardTree {
        public TypeBoundKind kind;
        public JCTree inner;
        protected JCWildcard(TypeBoundKind kind, JCTree inner) {
            kind.getClass(); // null-check
            this.kind = kind;
            this.inner = inner;
        }
        @Override
        public void accept(Visitor v) { v.visitWildcard(this); }

        public Kind getKind() {
            switch (kind.kind) {
            case UNBOUND:
                return Kind.UNBOUNDED_WILDCARD;
            case EXTENDS:
                return Kind.EXTENDS_WILDCARD;
            case SUPER:
                return Kind.SUPER_WILDCARD;
            default:
                throw new AssertionError("Unknown wildcard bound " + kind);
            }
        }
        public JCTree getBound() { return inner; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitWildcard(this, d);
        }
        @Override
        public int getTag() {
            return WILDCARD;
        }
    }

    public static class TypeBoundKind extends JCTree {
        public BoundKind kind;
        protected TypeBoundKind(BoundKind kind) {
            this.kind = kind;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeBoundKind(this); }

        public Kind getKind() {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }
        @Override
        public int getTag() {
            return TYPEBOUNDKIND;
        }
    }

    public static class JCAnnotation extends JCExpression implements AnnotationTree {
        public JCTree annotationType;
        public List<JCExpression> args;
        protected JCAnnotation(JCTree annotationType, List<JCExpression> args) {
            this.annotationType = annotationType;
            this.args = args;
        }
        @Override
        public void accept(Visitor v) { v.visitAnnotation(this); }

        public Kind getKind() { return Kind.ANNOTATION; }
        public JCTree getAnnotationType() { return annotationType; }
        public List<JCExpression> getArguments() {
            return args;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAnnotation(this, d);
        }
        @Override
        public int getTag() {
            return ANNOTATION;
        }
    }

    public static class JCTypeAnnotation extends JCAnnotation {
        public TypeAnnotationPosition annotation_position;
        public Attribute.TypeCompound attribute_field;

        protected JCTypeAnnotation(JCTree annotationType, List<JCExpression> args) {
            super(annotationType, args);
            this.annotation_position = new TypeAnnotationPosition();
        }
    }

    public static class JCModifiers extends JCTree implements com.sun.source.tree.ModifiersTree {
        public long flags;
        public List<JCAnnotation> annotations;
        protected JCModifiers(long flags, List<JCAnnotation> annotations) {
            this.flags = flags;
            this.annotations = annotations;
        }
        @Override
        public void accept(Visitor v) { v.visitModifiers(this); }

        public Kind getKind() { return Kind.MODIFIERS; }
        public Set<Modifier> getFlags() {
            return Flags.asModifierSet(flags);
        }
        public List<JCAnnotation> getAnnotations() {
            return annotations;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitModifiers(this, d);
        }
        @Override
        public int getTag() {
            return MODIFIERS;
        }
    }

    public static class JCAnnotatedType extends JCExpression
//308            implements com.sun.source.tree.AnnotatedTypeTree
    {
        public List<JCTypeAnnotation> annotations;
        public JCExpression underlyingType;
        protected JCAnnotatedType(List<JCTypeAnnotation> annotations, JCExpression underlyingType) {
            throw new UnsupportedOperationException();
//308            this.annotations = annotations;
//308            this.underlyingType = underlyingType;
        }
        @Override
        public void accept(Visitor v) { v.visitAnnotatedType(this); }

        public Kind getKind() {
            throw new UnsupportedOperationException();
//308            return Kind.ANNOTATED_TYPE;
        }
        public List<JCTypeAnnotation> getAnnotations() {
            return annotations;
        }
        public JCExpression getUnderlyingType() {
            return underlyingType;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new UnsupportedOperationException();
//308            return v.visitAnnotatedType(this, d);
        }
        @Override
        public int getTag() {
            return ANNOTATED_TYPE;
        }
    }

    public static class JCErroneous extends JCExpression
            implements com.sun.source.tree.ErroneousTree {
        public List<? extends JCTree> errs;
        protected JCErroneous(List<? extends JCTree> errs) {
            this.errs = errs;
        }
        @Override
        public void accept(Visitor v) { v.visitErroneous(this); }

        public Kind getKind() { return Kind.ERRONEOUS; }

        public List<? extends JCTree> getErrorTrees() {
            return errs;
        }

        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitErroneous(this, d);
        }
        @Override
        public int getTag() {
            return ERRONEOUS;
        }
    }

    /** (let int x = 3; in x+2) */
    public static class LetExpr extends JCExpression {
        public List<JCVariableDecl> defs;
        public JCTree expr;
        protected LetExpr(List<JCVariableDecl> defs, JCTree expr) {
            this.defs = defs;
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitLetExpr(this); }

        public Kind getKind() {
            throw new AssertionError("LetExpr is not part of a public API");
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new AssertionError("LetExpr is not part of a public API");
        }
        @Override
        public int getTag() {
            return LETEXPR;
        }
    }

    /** An interface for tree factories
     */
    public interface Factory {
        JCCompilationUnit TopLevel(List<JCAnnotation> packageAnnotations,
                                   JCExpression pid,
                                   List<JCTree> defs);
        JCImport Import(JCTree qualid, boolean staticImport);
        JCClassDecl ClassDef(JCModifiers mods,
                          Name name,
                          List<JCTypeParameter> typarams,
                          JCTree extending,
                          List<JCExpression> implementing,
                          List<JCTree> defs);
        JCMethodDecl MethodDef(JCModifiers mods,
                            Name name,
                            JCExpression restype,
                            List<JCTypeParameter> typarams,
                            List<JCVariableDecl> params,
                            List<JCTypeAnnotation> receiver,
                            List<JCExpression> thrown,
                            JCBlock body,
                            JCExpression defaultValue);
        JCVariableDecl VarDef(JCModifiers mods,
                      Name name,
                      JCExpression vartype,
                      JCExpression init);
        JCSkip Skip();
        JCBlock Block(long flags, List<JCStatement> stats);
        JCDoWhileLoop DoLoop(JCStatement body, JCExpression cond);
        JCWhileLoop WhileLoop(JCExpression cond, JCStatement body);
        JCForLoop ForLoop(List<JCStatement> init,
                        JCExpression cond,
                        List<JCExpressionStatement> step,
                        JCStatement body);
        JCEnhancedForLoop ForeachLoop(JCVariableDecl var, JCExpression expr, JCStatement body);
        JCLabeledStatement Labelled(Name label, JCStatement body);
        JCSwitch Switch(JCExpression selector, List<JCCase> cases);
        JCCase Case(JCExpression pat, List<JCStatement> stats);
        JCSynchronized Synchronized(JCExpression lock, JCBlock body);
        JCTry Try(JCBlock body, List<JCCatch> catchers, JCBlock finalizer);
        JCTry Try(List<JCTree> resources,
                  JCBlock body,
                  List<JCCatch> catchers,
                  JCBlock finalizer);
        JCCatch Catch(JCVariableDecl param, JCBlock body);
        JCConditional Conditional(JCExpression cond,
                                JCExpression thenpart,
                                JCExpression elsepart);
        JCIf If(JCExpression cond, JCStatement thenpart, JCStatement elsepart);
        JCExpressionStatement Exec(JCExpression expr);
        JCBreak Break(Name label);
        JCContinue Continue(Name label);
        JCReturn Return(JCExpression expr);
        JCThrow Throw(JCTree expr);
        JCAssert Assert(JCExpression cond, JCExpression detail);
        JCMethodInvocation Apply(List<JCExpression> typeargs,
                    JCExpression fn,
                    List<JCExpression> args);
        JCNewClass NewClass(JCExpression encl,
                          List<JCExpression> typeargs,
                          JCExpression clazz,
                          List<JCExpression> args,
                          JCClassDecl def);
        JCNewArray NewArray(JCExpression elemtype,
                          List<JCExpression> dims,
                          List<JCExpression> elems);
        JCParens Parens(JCExpression expr);
        JCAssign Assign(JCExpression lhs, JCExpression rhs);
        JCAssignOp Assignop(int opcode, JCTree lhs, JCTree rhs);
        JCUnary Unary(int opcode, JCExpression arg);
        JCBinary Binary(int opcode, JCExpression lhs, JCExpression rhs);
        JCTypeCast TypeCast(JCTree expr, JCExpression type);
        JCInstanceOf TypeTest(JCExpression expr, JCTree clazz);
        JCArrayAccess Indexed(JCExpression indexed, JCExpression index);
        JCFieldAccess Select(JCExpression selected, Name selector);
        JCIdent Ident(Name idname);
        JCLiteral Literal(int tag, Object value);
        JCPrimitiveTypeTree TypeIdent(int typetag);
        JCArrayTypeTree TypeArray(JCExpression elemtype);
        JCTypeApply TypeApply(JCExpression clazz, List<JCExpression> arguments);
        JCTypeParameter TypeParameter(Name name, List<JCExpression> bounds);
        JCWildcard Wildcard(TypeBoundKind kind, JCTree type);
        TypeBoundKind TypeBoundKind(BoundKind kind);
        JCAnnotation Annotation(JCTree annotationType, List<JCExpression> args);
        JCModifiers Modifiers(long flags, List<JCAnnotation> annotations);
        JCErroneous Erroneous(List<? extends JCTree> errs);
        LetExpr LetExpr(List<JCVariableDecl> defs, JCTree expr);
    }

    /** A generic visitor class for trees.
     */
    public static abstract class Visitor {
        public void visitTopLevel(JCCompilationUnit that)    { visitTree(that); }
        public void visitImport(JCImport that)               { visitTree(that); }
        public void visitClassDef(JCClassDecl that)          { visitTree(that); }
        public void visitMethodDef(JCMethodDecl that)        { visitTree(that); }
        public void visitVarDef(JCVariableDecl that)         { visitTree(that); }
        public void visitSkip(JCSkip that)                   { visitTree(that); }
        public void visitBlock(JCBlock that)                 { visitTree(that); }
        public void visitDoLoop(JCDoWhileLoop that)          { visitTree(that); }
        public void visitWhileLoop(JCWhileLoop that)         { visitTree(that); }
        public void visitForLoop(JCForLoop that)             { visitTree(that); }
        public void visitForeachLoop(JCEnhancedForLoop that) { visitTree(that); }
        public void visitLabelled(JCLabeledStatement that)   { visitTree(that); }
        public void visitSwitch(JCSwitch that)               { visitTree(that); }
        public void visitCase(JCCase that)                   { visitTree(that); }
        public void visitSynchronized(JCSynchronized that)   { visitTree(that); }
        public void visitTry(JCTry that)                     { visitTree(that); }
        public void visitCatch(JCCatch that)                 { visitTree(that); }
        public void visitConditional(JCConditional that)     { visitTree(that); }
        public void visitIf(JCIf that)                       { visitTree(that); }
        public void visitExec(JCExpressionStatement that)    { visitTree(that); }
        public void visitBreak(JCBreak that)                 { visitTree(that); }
        public void visitContinue(JCContinue that)           { visitTree(that); }
        public void visitReturn(JCReturn that)               { visitTree(that); }
        public void visitThrow(JCThrow that)                 { visitTree(that); }
        public void visitAssert(JCAssert that)               { visitTree(that); }
        public void visitApply(JCMethodInvocation that)      { visitTree(that); }
        public void visitNewClass(JCNewClass that)           { visitTree(that); }
        public void visitNewArray(JCNewArray that)           { visitTree(that); }
        public void visitParens(JCParens that)               { visitTree(that); }
        public void visitAssign(JCAssign that)               { visitTree(that); }
        public void visitAssignop(JCAssignOp that)           { visitTree(that); }
        public void visitUnary(JCUnary that)                 { visitTree(that); }
        public void visitBinary(JCBinary that)               { visitTree(that); }
        public void visitTypeCast(JCTypeCast that)           { visitTree(that); }
        public void visitTypeTest(JCInstanceOf that)         { visitTree(that); }
        public void visitIndexed(JCArrayAccess that)         { visitTree(that); }
        public void visitSelect(JCFieldAccess that)          { visitTree(that); }
        public void visitIdent(JCIdent that)                 { visitTree(that); }
        public void visitLiteral(JCLiteral that)             { visitTree(that); }
        public void visitTypeIdent(JCPrimitiveTypeTree that) { visitTree(that); }
        public void visitTypeArray(JCArrayTypeTree that)     { visitTree(that); }
        public void visitTypeApply(JCTypeApply that)         { visitTree(that); }
        public void visitTypeDisjoint(JCTypeDisjoint that)   { visitTree(that); }
        public void visitTypeParameter(JCTypeParameter that) { visitTree(that); }
        public void visitWildcard(JCWildcard that)           { visitTree(that); }
        public void visitTypeBoundKind(TypeBoundKind that)   { visitTree(that); }
        public void visitAnnotation(JCAnnotation that)       { visitTree(that); }
        public void visitModifiers(JCModifiers that)         { visitTree(that); }
        public void visitAnnotatedType(JCAnnotatedType that) { visitTree(that); }
        public void visitErroneous(JCErroneous that)         { visitTree(that); }
        public void visitLetExpr(LetExpr that)               { visitTree(that); }

        public void visitTree(JCTree that)                   { assert false; }
    }

}
