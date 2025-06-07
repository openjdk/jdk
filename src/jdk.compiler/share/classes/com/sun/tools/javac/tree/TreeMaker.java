/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ModuleTree.ModuleKind;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Attribute.UnresolvedClass;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;

/** Factory class for trees.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class TreeMaker implements JCTree.Factory {

    /** The context key for the tree factory. */
    protected static final Context.Key<TreeMaker> treeMakerKey = new Context.Key<>();

    /** Get the TreeMaker instance. */
    public static TreeMaker instance(Context context) {
        TreeMaker instance = context.get(treeMakerKey);
        if (instance == null)
            instance = new TreeMaker(context);
        return instance;
    }

    /** The start position at which subsequent trees will be created.
     */
    public int pos;

    /** The end position at which subsequent trees will be created.
     */
    public int endPos;

    /** The end position table into which subsequent end positions will be stored.
     */
    public EndPosTable endPosTable;

    /** The toplevel tree to which created trees belong.
     */
    public JCCompilationUnit toplevel;

    /** The current name table. */
    Names names;

    Types types;

    /** The current symbol table. */
    Symtab syms;

    /** Create a tree maker with null toplevel and NOPOS as initial position.
     */
    @SuppressWarnings("this-escape")
    protected TreeMaker(Context context) {
        context.put(treeMakerKey, this);
        this.pos = Position.NOPOS;
        this.endPos = Position.NOPOS;
        this.toplevel = null;
        this.names = Names.instance(context);
        this.syms = Symtab.instance(context);
        this.types = Types.instance(context);
    }

    /** Create a tree maker with a given toplevel and FIRSTPOS as initial position.
     */
    protected TreeMaker(JCCompilationUnit toplevel, Names names, Types types, Symtab syms) {
        this.pos = Position.FIRSTPOS;
        this.endPos = Position.NOPOS;
        this.toplevel = toplevel;
        this.names = names;
        this.types = types;
        this.syms = syms;
    }

    /** Create a new tree maker for a given toplevel.
     */
    public TreeMaker forToplevel(JCCompilationUnit toplevel) {
        return new TreeMaker(toplevel, names, types, syms);
    }

    /** Reassign current start position and set current end position to NPPOS.
     */
    public TreeMaker at(int pos) {
        this.pos = pos;
        this.endPos = Position.NOPOS;
        this.endPosTable = null;
        return this;
    }

    /** Reassign current start position and set current end position to NPPOS.
     */
    public TreeMaker at(DiagnosticPosition pos) {
        return at(pos, null);
    }

    /** Reassign current start and end positions based on the given DiagnosticPosition.
     */
    public TreeMaker at(DiagnosticPosition pos, EndPosTable endPosTable) {
        return at(pos != null ? pos.getStartPosition() : Position.NOPOS,
                  pos != null && endPosTable != null ? pos.getEndPosition(endPosTable) : Position.NOPOS,
                  endPosTable);
    }

    /** Reassign current start and end positions.
     *  @param pos start position, or {@link Position#NOPOS} for none
     *  @param endPos ending position, or {@link Position#NOPOS} for none
     *  @param endPosTable ending position table, or null for none
     */
    public TreeMaker at(int pos, int endPos, EndPosTable endPosTable) {
        this.pos = pos;
        this.endPos = endPos;
        this.endPosTable = endPosTable;
        return this;
    }

    /**
     * Create given tree node at current position.
     * @param defs a list of PackageDef, ClassDef, Import, and Skip
     */
    public JCCompilationUnit TopLevel(List<JCTree> defs) {
        for (JCTree node : defs)
            Assert.check(node instanceof JCClassDecl
                || node instanceof JCPackageDecl
                || node instanceof JCImport
                || node instanceof JCModuleImport
                || node instanceof JCModuleDecl
                || node instanceof JCSkip
                || node instanceof JCErroneous
                || node instanceof JCMethodDecl
                || node instanceof JCVariableDecl
                || (node instanceof JCExpressionStatement expressionStatement
                    && expressionStatement.expr instanceof JCErroneous),
                    () -> node.getClass().getSimpleName());
        return setPos(new JCCompilationUnit(defs));
    }

    public JCPackageDecl PackageDecl(List<JCAnnotation> annotations,
                                     JCExpression pid) {
        Assert.checkNonNull(annotations);
        Assert.checkNonNull(pid);
        return setPos(new JCPackageDecl(annotations, pid));
    }

    public JCImport Import(JCFieldAccess qualid, boolean staticImport) {
        return setPos(new JCImport(qualid, staticImport));
    }

    public JCModuleImport ModuleImport(JCExpression moduleName) {
        return setPos(new JCModuleImport(moduleName));
    }

    public JCClassDecl ClassDef(JCModifiers mods,
                                Name name,
                                List<JCTypeParameter> typarams,
                                JCExpression extending,
                                List<JCExpression> implementing,
                                List<JCTree> defs)
    {
        return ClassDef(mods, name, typarams, extending, implementing, List.nil(), defs);
    }

    public JCClassDecl ClassDef(JCModifiers mods,
                                Name name,
                                List<JCTypeParameter> typarams,
                                JCExpression extending,
                                List<JCExpression> implementing,
                                List<JCExpression> permitting,
                                List<JCTree> defs)
    {
        return setPos(new JCClassDecl(mods,
                                     name,
                                     typarams,
                                     extending,
                                     implementing,
                                     permitting,
                                     defs,
                                     null));
    }

    public JCMethodDecl MethodDef(JCModifiers mods,
                               Name name,
                               JCExpression restype,
                               List<JCTypeParameter> typarams,
                               List<JCVariableDecl> params,
                               List<JCExpression> thrown,
                               JCBlock body,
                               JCExpression defaultValue) {
        return MethodDef(
                mods, name, restype, typarams, null, params,
                thrown, body, defaultValue);
    }

    public JCMethodDecl MethodDef(JCModifiers mods,
                               Name name,
                               JCExpression restype,
                               List<JCTypeParameter> typarams,
                               JCVariableDecl recvparam,
                               List<JCVariableDecl> params,
                               List<JCExpression> thrown,
                               JCBlock body,
                               JCExpression defaultValue)
    {
        return setPos(new JCMethodDecl(mods,
                                       name,
                                       restype,
                                       typarams,
                                       recvparam,
                                       params,
                                       thrown,
                                       body,
                                       defaultValue,
                                       null));
    }

    public JCVariableDecl VarDef(JCModifiers mods, Name name, JCExpression vartype, JCExpression init) {
        return setPos(new JCVariableDecl(mods, name, vartype, init, null));
    }

    public JCVariableDecl VarDef(JCModifiers mods, Name name, JCExpression vartype, JCExpression init,
      JCVariableDecl.DeclKind declKind, int typePos) {
        return setPos(new JCVariableDecl(mods, name, vartype, init, null, declKind, typePos));
    }

    public JCVariableDecl ReceiverVarDef(JCModifiers mods, JCExpression name, JCExpression vartype) {
        return setPos(new JCVariableDecl(mods, name, vartype));
    }

    public JCSkip Skip() {
        return setPos(new JCSkip());
    }

    public JCBlock Block(long flags, List<JCStatement> stats) {
        return setPos(new JCBlock(flags, stats));
    }

    public JCDoWhileLoop DoLoop(JCStatement body, JCExpression cond) {
        return setPos(new JCDoWhileLoop(body, cond));
    }

    public JCWhileLoop WhileLoop(JCExpression cond, JCStatement body) {
        return setPos(new JCWhileLoop(cond, body));
    }

    public JCForLoop ForLoop(List<JCStatement> init,
                           JCExpression cond,
                           List<JCExpressionStatement> step,
                           JCStatement body)
    {
        return setPos(new JCForLoop(init, cond, step, body));
    }

    public JCEnhancedForLoop ForeachLoop(JCVariableDecl var, JCExpression expr, JCStatement body) {
        return setPos(new JCEnhancedForLoop(var, expr, body));
    }

    public JCLabeledStatement Labelled(Name label, JCStatement body) {
        return setPos(new JCLabeledStatement(label, body));
    }

    public JCSwitch Switch(JCExpression selector, List<JCCase> cases) {
        return setPos(new JCSwitch(selector, cases));
    }

    public JCCase Case(CaseTree.CaseKind caseKind, List<JCCaseLabel> labels,
                       JCExpression guard, List<JCStatement> stats, JCTree body) {
        return setPos(new JCCase(caseKind, labels, guard, stats, body));
    }

    public JCSwitchExpression SwitchExpression(JCExpression selector, List<JCCase> cases) {
        return setPos(new JCSwitchExpression(selector, cases));
    }

    public JCSynchronized Synchronized(JCExpression lock, JCBlock body) {
        return setPos(new JCSynchronized(lock, body));
    }

    public JCTry Try(JCBlock body, List<JCCatch> catchers, JCBlock finalizer) {
        return Try(List.nil(), body, catchers, finalizer);
    }

    public JCTry Try(List<JCTree> resources,
                     JCBlock body,
                     List<JCCatch> catchers,
                     JCBlock finalizer) {
        return setPos(new JCTry(resources, body, catchers, finalizer));
    }

    public JCCatch Catch(JCVariableDecl param, JCBlock body) {
        return setPos(new JCCatch(param, body));
    }

    public JCConditional Conditional(JCExpression cond,
                                   JCExpression thenpart,
                                   JCExpression elsepart)
    {
        return setPos(new JCConditional(cond, thenpart, elsepart));
    }

    public JCIf If(JCExpression cond, JCStatement thenpart, JCStatement elsepart) {
        return setPos(new JCIf(cond, thenpart, elsepart));
    }

    public JCExpressionStatement Exec(JCExpression expr) {
        return setPos(new JCExpressionStatement(expr));
    }

    public JCBreak Break(Name label) {
        return setPos(new JCBreak(label, null));
    }

    public JCYield Yield(JCExpression value) {
        return setPos(new JCYield(value, null));
    }

    public JCContinue Continue(Name label) {
        return setPos(new JCContinue(label, null));
    }

    public JCReturn Return(JCExpression expr) {
        return setPos(new JCReturn(expr));
    }

    public JCThrow Throw(JCExpression expr) {
        return setPos(new JCThrow(expr));
    }

    public JCAssert Assert(JCExpression cond, JCExpression detail) {
        return setPos(new JCAssert(cond, detail));
    }

    public JCMethodInvocation Apply(List<JCExpression> typeargs,
                       JCExpression fn,
                       List<JCExpression> args)
    {
        return setPos(new JCMethodInvocation(typeargs, fn, args));
    }

    public JCNewClass NewClass(JCExpression encl,
                             List<JCExpression> typeargs,
                             JCExpression clazz,
                             List<JCExpression> args,
                             JCClassDecl def)
    {
        return SpeculativeNewClass(encl, typeargs, clazz, args, def, false);
    }

    public JCNewClass SpeculativeNewClass(JCExpression encl,
                             List<JCExpression> typeargs,
                             JCExpression clazz,
                             List<JCExpression> args,
                             JCClassDecl def,
                             boolean classDefRemoved)
    {
        return setPos(classDefRemoved ?
                new JCNewClass(encl, typeargs, clazz, args, def) {
                    @Override
                    public boolean classDeclRemoved() {
                        return true;
                    }
                } :
                new JCNewClass(encl, typeargs, clazz, args, def));
    }

    public JCNewArray NewArray(JCExpression elemtype,
                             List<JCExpression> dims,
                             List<JCExpression> elems)
    {
        return setPos(new JCNewArray(elemtype, dims, elems));
    }

    public JCLambda Lambda(List<JCVariableDecl> params,
                           JCTree body)
    {
        return setPos(new JCLambda(params, body));
    }

    public JCParens Parens(JCExpression expr) {
        return setPos(new JCParens(expr));
    }

    public JCAssign Assign(JCExpression lhs, JCExpression rhs) {
        return setPos(new JCAssign(lhs, rhs));
    }

    public JCAssignOp Assignop(JCTree.Tag opcode, JCTree lhs, JCTree rhs) {
        return setPos(new JCAssignOp(opcode, lhs, rhs, null));
    }

    public JCUnary Unary(JCTree.Tag opcode, JCExpression arg) {
        return setPos(new JCUnary(opcode, arg));
    }

    public JCBinary Binary(JCTree.Tag opcode, JCExpression lhs, JCExpression rhs) {
        return setPos(new JCBinary(opcode, lhs, rhs, null));
    }

    public JCTypeCast TypeCast(JCTree clazz, JCExpression expr) {
        return setPos(new JCTypeCast(clazz, expr));
    }

    public JCInstanceOf TypeTest(JCExpression expr, JCTree clazz) {
        return setPos(new JCInstanceOf(expr, clazz));
    }

    public JCAnyPattern AnyPattern() {
        return setPos(new JCAnyPattern());
    }

    public JCBindingPattern BindingPattern(JCVariableDecl var) {
        return setPos(new JCBindingPattern(var));
    }

    public JCDefaultCaseLabel DefaultCaseLabel() {
        return setPos(new JCDefaultCaseLabel());
    }

    public JCConstantCaseLabel ConstantCaseLabel(JCExpression expr) {
        return setPos(new JCConstantCaseLabel(expr));
    }

    public JCPatternCaseLabel PatternCaseLabel(JCPattern pat) {
        return setPos(new JCPatternCaseLabel(pat));
    }

    public JCRecordPattern RecordPattern(JCExpression deconstructor, List<JCPattern> nested) {
        return setPos(new JCRecordPattern(deconstructor, nested));
    }

    public JCArrayAccess Indexed(JCExpression indexed, JCExpression index) {
        return setPos(new JCArrayAccess(indexed, index));
    }

    public JCFieldAccess Select(JCExpression selected, Name selector) {
        return setPos(new JCFieldAccess(selected, selector, null));
    }

    public JCMemberReference Reference(JCMemberReference.ReferenceMode mode, Name name,
            JCExpression expr, List<JCExpression> typeargs) {
        return setPos(new JCMemberReference(mode, name, expr, typeargs));
    }

    public JCIdent Ident(Name name) {
        return setPos(new JCIdent(name, null));
    }

    public JCLiteral Literal(TypeTag tag, Object value) {
        return setPos(new JCLiteral(tag, value));
    }

    public JCPrimitiveTypeTree TypeIdent(TypeTag typetag) {
        return setPos(new JCPrimitiveTypeTree(typetag));
    }

    public JCArrayTypeTree TypeArray(JCExpression elemtype) {
        return setPos(new JCArrayTypeTree(elemtype));
    }

    public JCTypeApply TypeApply(JCExpression clazz, List<JCExpression> arguments) {
        return setPos(new JCTypeApply(clazz, arguments));
    }

    public JCTypeUnion TypeUnion(List<JCExpression> components) {
        return setPos(new JCTypeUnion(components));
    }

    public JCTypeIntersection TypeIntersection(List<JCExpression> components) {
        return setPos(new JCTypeIntersection(components));
    }

    public JCTypeParameter TypeParameter(Name name, List<JCExpression> bounds) {
        return TypeParameter(name, bounds, List.nil());
    }

    public JCTypeParameter TypeParameter(Name name, List<JCExpression> bounds, List<JCAnnotation> annos) {
        return setPos(new JCTypeParameter(name, bounds, annos));
    }

    public JCWildcard Wildcard(TypeBoundKind kind, JCTree type) {
        return setPos(new JCWildcard(kind, type));
    }

    public TypeBoundKind TypeBoundKind(BoundKind kind) {
        return setPos(new TypeBoundKind(kind));
    }

    public JCAnnotation Annotation(JCTree annotationType, List<JCExpression> args) {
        return setPos(new JCAnnotation(Tag.ANNOTATION, annotationType, args));
    }

    public JCAnnotation TypeAnnotation(JCTree annotationType, List<JCExpression> args) {
        return setPos(new JCAnnotation(Tag.TYPE_ANNOTATION, annotationType, args));
    }

    public JCModifiers Modifiers(long flags, List<JCAnnotation> annotations) {
        JCModifiers tree = new JCModifiers(flags, annotations);
        boolean noFlags = (flags & (Flags.ModifierFlags | Flags.ANNOTATION)) == 0;
        if (noFlags && annotations.isEmpty()) {
            tree.pos = Position.NOPOS;
            return tree;
        }
        return setPos(tree);
    }

    public JCModifiers Modifiers(long flags) {
        return Modifiers(flags, List.nil());
    }

    @Override
    public JCModuleDecl ModuleDef(JCModifiers mods, ModuleKind kind,
            JCExpression qualid, List<JCDirective> directives) {
        return setPos(new JCModuleDecl(mods, kind, qualid, directives));
    }

    @Override
    public JCExports Exports(JCExpression qualId, List<JCExpression> moduleNames) {
        return setPos(new JCExports(qualId, moduleNames));
    }

    @Override
    public JCOpens Opens(JCExpression qualId, List<JCExpression> moduleNames) {
        return setPos(new JCOpens(qualId, moduleNames));
    }

    @Override
    public JCProvides Provides(JCExpression serviceName, List<JCExpression> implNames) {
        return setPos(new JCProvides(serviceName, implNames));
    }

    @Override
    public JCRequires Requires(boolean isTransitive, boolean isStaticPhase, JCExpression qualId) {
        return setPos(new JCRequires(isTransitive, isStaticPhase, qualId));
    }

    @Override
    public JCUses Uses(JCExpression qualId) {
        return setPos(new JCUses(qualId));
    }

    public JCAnnotatedType AnnotatedType(List<JCAnnotation> annotations, JCExpression underlyingType) {
        return setPos(new JCAnnotatedType(annotations, underlyingType));
    }

    public JCErroneous Erroneous() {
        return Erroneous(List.nil());
    }

    public JCErroneous Erroneous(List<? extends JCTree> errs) {
        return setPos(new JCErroneous(errs));
    }

    public LetExpr LetExpr(List<JCStatement> defs, JCExpression expr) {
        return setPos(new LetExpr(defs, expr));
    }

/* ***************************************************************************
 * Derived building blocks.
 ****************************************************************************/

    public JCClassDecl AnonymousClassDef(JCModifiers mods,
                                         List<JCTree> defs)
    {
        return ClassDef(mods,
                        names.empty,
                        List.nil(),
                        null,
                        List.nil(),
                        defs);
    }

    public LetExpr LetExpr(JCVariableDecl def, JCExpression expr) {
        return setPos(new LetExpr(List.of(def), expr));
    }

    private <T extends JCTree> T setPos(T tree) {
        tree.setPos(pos, endPos, endPosTable);
        return tree;
    }

    /** Create an identifier from a symbol.
     */
    public JCIdent Ident(Symbol sym) {
        return (JCIdent)new JCIdent((sym.name != names.empty)
                                ? sym.name
                                : sym.flatName(), sym)
            .setPos(pos, endPos, endPosTable)
            .setType(sym.type);
    }

    /** Create a selection node from a qualifier tree and a symbol.
     *  @param base   The qualifier tree.
     */
    public JCFieldAccess Select(JCExpression base, Symbol sym) {
        return (JCFieldAccess)new JCFieldAccess(base, sym.name, sym).setPos(pos, endPos, endPosTable).setType(sym.type);
    }

    /** Create a qualified identifier from a symbol, adding enough qualifications
     *  to make the reference unique. The types in the AST nodes will be erased.
     */
    public JCExpression QualIdent(Symbol sym) {
        JCExpression result = isUnqualifiable(sym)
            ? Ident(sym)
            : Select(QualIdent(sym.owner), sym);

        if (sym.kind == TYP) {
            result.setType(types.erasure(sym.type));
        }

        return result;
    }

    /** Create an identifier that refers to the variable declared in given variable
     *  declaration.
     */
    public JCExpression Ident(JCVariableDecl param) {
        return Ident(param.sym);
    }

    /** Create a list of identifiers referring to the variables declared
     *  in given list of variable declarations.
     */
    public List<JCExpression> Idents(List<JCVariableDecl> params) {
        ListBuffer<JCExpression> ids = new ListBuffer<>();
        for (List<JCVariableDecl> l = params; l.nonEmpty(); l = l.tail)
            ids.append(Ident(l.head));
        return ids.toList();
    }

    /** Create a tree representing `this', given its type.
     */
    public JCExpression This(Type t) {
        return Ident(new VarSymbol(FINAL, names._this, t, t.tsym));
    }

    /** Create a tree representing qualified `this' given its type
     */
    public JCExpression QualThis(Type t) {
        return Select(Type(t), new VarSymbol(FINAL, names._this, t, t.tsym));
    }

    /** Create a tree representing a class literal.
     */
    public JCExpression ClassLiteral(ClassSymbol clazz) {
        return ClassLiteral(clazz.type);
    }

    /** Create a tree representing a class literal.
     */
    public JCExpression ClassLiteral(Type t) {
        VarSymbol lit = new VarSymbol(STATIC | PUBLIC | FINAL,
                                      names._class,
                                      t,
                                      t.tsym);
        return Select(Type(t), lit);
    }

    /** Create a tree representing `super', given its type and owner.
     */
    public JCIdent Super(Type t, TypeSymbol owner) {
        return Ident(new VarSymbol(FINAL, names._super, t, owner));
    }

    /**
     * Create a method invocation from a method tree and a list of
     * argument trees.
     */
    public JCMethodInvocation App(JCExpression meth, List<JCExpression> args) {
        return Apply(null, meth, args).setType(meth.type.getReturnType());
    }

    /**
     * Create a no-arg method invocation from a method tree
     */
    public JCMethodInvocation App(JCExpression meth) {
        return Apply(null, meth, List.nil()).setType(meth.type.getReturnType());
    }

    /** Create a method invocation from a method tree and a list of argument trees.
     */
    public JCExpression Create(Symbol ctor, List<JCExpression> args) {
        Type t = ctor.owner.erasure(types);
        JCNewClass newclass = NewClass(null, null, Type(t), args, null);
        newclass.constructor = ctor;
        newclass.setType(t);
        return newclass;
    }

    /** Create a tree representing given type.
     */
    public JCExpression Type(Type t) {
        if (t == null) return null;
        JCExpression tp;
        switch (t.getTag()) {
        case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
        case DOUBLE: case BOOLEAN: case VOID:
            tp = TypeIdent(t.getTag());
            break;
        case TYPEVAR:
            tp = Ident(t.tsym);
            break;
        case WILDCARD: {
            WildcardType a = ((WildcardType) t);
            tp = Wildcard(TypeBoundKind(a.kind), a.kind == BoundKind.UNBOUND ? null : Type(a.type));
            break;
        }
        case CLASS:
            switch (t.getKind()) {
            case UNION: {
                UnionClassType tu = (UnionClassType)t;
                ListBuffer<JCExpression> la = new ListBuffer<>();
                for (Type ta : tu.getAlternativeTypes()) {
                    la.add(Type(ta));
                }
                tp = TypeUnion(la.toList());
                break;
            }
            case INTERSECTION: {
                IntersectionClassType it = (IntersectionClassType)t;
                ListBuffer<JCExpression> la = new ListBuffer<>();
                for (Type ta : it.getExplicitComponents()) {
                    la.add(Type(ta));
                }
                tp = TypeIntersection(la.toList());
                break;
            }
            default: {
                Type outer = t.getEnclosingType();
                JCExpression clazz = outer.hasTag(CLASS) && t.tsym.owner.kind == TYP
                        ? Select(Type(outer), t.tsym)
                        : QualIdent(t.tsym);
                tp = t.getTypeArguments().isEmpty()
                        ? clazz
                        : TypeApply(clazz, Types(t.getTypeArguments()));
                break;
            }
            }
            break;
        case ARRAY:
            tp = TypeArray(Type(types.elemtype(t)));
            break;
        case ERROR:
            tp = TypeIdent(ERROR);
            break;
        default:
            throw new AssertionError("unexpected type: " + t);
        }
        return tp.setType(t);
    }

    /** Create a list of trees representing given list of types.
     */
    public List<JCExpression> Types(List<Type> ts) {
        ListBuffer<JCExpression> lb = new ListBuffer<>();
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            lb.append(Type(l.head));
        return lb.toList();
    }

    /** Create a variable definition from a variable symbol and an initializer
     *  expression.
     */
    public JCVariableDecl VarDef(VarSymbol v, JCExpression init) {
        return (JCVariableDecl)
            new JCVariableDecl(
                Modifiers(v.flags(), Annotations(v.getRawAttributes())),
                v.name,
                Type(v.type),
                init,
                v).setPos(pos, endPos, endPosTable).setType(v.type);
    }

    /** Create annotation trees from annotations.
     */
    public List<JCAnnotation> Annotations(List<Attribute.Compound> attributes) {
        if (attributes == null) return List.nil();
        ListBuffer<JCAnnotation> result = new ListBuffer<>();
        for (List<Attribute.Compound> i = attributes; i.nonEmpty(); i=i.tail) {
            Attribute a = i.head;
            result.append(Annotation(a));
        }
        return result.toList();
    }

    public JCLiteral Literal(Object value) {
        JCLiteral result = null;
        if (value instanceof String) {
            result = Literal(CLASS, value).
                setType(syms.stringType.constType(value));
        } else if (value instanceof Integer) {
            result = Literal(INT, value).
                setType(syms.intType.constType(value));
        } else if (value instanceof Long) {
            result = Literal(LONG, value).
                setType(syms.longType.constType(value));
        } else if (value instanceof Byte) {
            result = Literal(BYTE, value).
                setType(syms.byteType.constType(value));
        } else if (value instanceof Character charVal) {
            int v = charVal.toString().charAt(0);
            result = Literal(CHAR, v).
                setType(syms.charType.constType(v));
        } else if (value instanceof Double) {
            result = Literal(DOUBLE, value).
                setType(syms.doubleType.constType(value));
        } else if (value instanceof Float) {
            result = Literal(FLOAT, value).
                setType(syms.floatType.constType(value));
        } else if (value instanceof Short) {
            result = Literal(SHORT, value).
                setType(syms.shortType.constType(value));
        } else if (value instanceof Boolean boolVal) {
            int v = boolVal ? 1 : 0;
            result = Literal(BOOLEAN, v).
                setType(syms.booleanType.constType(v));
        } else {
            throw new AssertionError(value);
        }
        return result;
    }

    class AnnotationBuilder implements Attribute.Visitor {
        JCExpression result = null;
        public void visitConstant(Attribute.Constant v) {
            result = Literal(v.type.getTag(), v.value);
        }
        public void visitClass(Attribute.Class clazz) {
            result = ClassLiteral(clazz.classType).setType(syms.classType);
        }
        public void visitEnum(Attribute.Enum e) {
            result = QualIdent(e.value);
        }
        public void visitError(Attribute.Error e) {
            if (e instanceof UnresolvedClass unresolvedClass) {
                result = ClassLiteral(unresolvedClass.classType).setType(syms.classType);
            } else {
                result = Erroneous();
            }
        }
        public void visitCompound(Attribute.Compound compound) {
            if (compound instanceof Attribute.TypeCompound typeCompound) {
                result = visitTypeCompoundInternal(typeCompound);
            } else {
                result = visitCompoundInternal(compound);
            }
        }
        public JCAnnotation visitCompoundInternal(Attribute.Compound compound) {
            ListBuffer<JCExpression> args = new ListBuffer<>();
            for (List<Pair<Symbol.MethodSymbol,Attribute>> values = compound.values; values.nonEmpty(); values=values.tail) {
                Pair<MethodSymbol,Attribute> pair = values.head;
                JCExpression valueTree = translate(pair.snd);
                args.append(Assign(Ident(pair.fst), valueTree).setType(valueTree.type));
            }
            return Annotation(Type(compound.type), args.toList());
        }
        public JCAnnotation visitTypeCompoundInternal(Attribute.TypeCompound compound) {
            ListBuffer<JCExpression> args = new ListBuffer<>();
            for (List<Pair<Symbol.MethodSymbol,Attribute>> values = compound.values; values.nonEmpty(); values=values.tail) {
                Pair<MethodSymbol,Attribute> pair = values.head;
                JCExpression valueTree = translate(pair.snd);
                args.append(Assign(Ident(pair.fst), valueTree).setType(valueTree.type));
            }
            return TypeAnnotation(Type(compound.type), args.toList());
        }
        public void visitArray(Attribute.Array array) {
            ListBuffer<JCExpression> elems = new ListBuffer<>();
            for (int i = 0; i < array.values.length; i++)
                elems.append(translate(array.values[i]));
            result = NewArray(null, List.nil(), elems.toList()).setType(array.type);
        }
        JCExpression translate(Attribute a) {
            a.accept(this);
            return result;
        }
        JCAnnotation translate(Attribute.Compound a) {
            return visitCompoundInternal(a);
        }
        JCAnnotation translate(Attribute.TypeCompound a) {
            return visitTypeCompoundInternal(a);
        }
    }

    AnnotationBuilder annotationBuilder = new AnnotationBuilder();

    /** Create an annotation tree from an attribute.
     */
    public JCAnnotation Annotation(Attribute a) {
        return annotationBuilder.translate((Attribute.Compound)a);
    }

    public JCAnnotation TypeAnnotation(Attribute a) {
        return annotationBuilder.translate((Attribute.TypeCompound) a);
    }

    /** Create a method definition from a method symbol and a method body.
     */
    public JCMethodDecl MethodDef(MethodSymbol m, JCBlock body) {
        return MethodDef(m, m.type, body);
    }

    /** Create a method definition from a method symbol, method type
     *  and a method body.
     */
    public JCMethodDecl MethodDef(MethodSymbol m, Type mtype, JCBlock body) {
        return (JCMethodDecl)
            new JCMethodDecl(
                Modifiers(m.flags(), Annotations(m.getRawAttributes())),
                m.name,
                m.name != names.init ? Type(mtype.getReturnType()) : null,
                TypeParams(mtype.getTypeArguments()),
                null, // receiver type
                m.params != null ? Params(m) : Params(m, mtype.getParameterTypes()),
                Types(mtype.getThrownTypes()),
                body,
                null,
                m).setPos(pos, endPos, endPosTable).setType(mtype);
    }

    /** Create a type parameter tree from its name and type.
     */
    public JCTypeParameter TypeParam(Name name, TypeVar tvar) {
        return (JCTypeParameter)
            TypeParameter(name, Types(types.getBounds(tvar))).setPos(pos, endPos, endPosTable).setType(tvar);
    }

    /** Create a list of type parameter trees from a list of type variables.
     */
    public List<JCTypeParameter> TypeParams(List<Type> typarams) {
        ListBuffer<JCTypeParameter> tparams = new ListBuffer<>();
        for (List<Type> l = typarams; l.nonEmpty(); l = l.tail)
            tparams.append(TypeParam(l.head.tsym.name, (TypeVar)l.head));
        return tparams.toList();
    }

    /** Create a value parameter tree from its name, type, and owner.
     */
    public JCVariableDecl Param(Name name, Type argtype, Symbol owner) {
        return VarDef(new VarSymbol(PARAMETER, name, argtype, owner), null);
    }

    /** Create a list of value parameter trees for a method's parameters
     *  using the same names as the method's existing parameters.
     */
    public List<JCVariableDecl> Params(MethodSymbol mth) {
        Assert.check(mth.params != null);
        ListBuffer<JCVariableDecl> params = new ListBuffer<>();
        for (VarSymbol param : mth.params)
            params.append(VarDef(param, null));
        return params.toList();
    }

    /** Synthesize a list of parameter trees for a method's parameters.
     *  Used for methods with no parameters defined, e.g. bridge methods.
     *  The placeholder names will be x0, x1, ..., xn.
     */
    public List<JCVariableDecl> Params(MethodSymbol mth, List<Type> argtypes) {
        Assert.check(mth.params == null);
        ListBuffer<JCVariableDecl> params = new ListBuffer<>();
        int i = 0;
        for (List<Type> l = argtypes; l.nonEmpty(); l = l.tail)
            params.append(Param(paramName(i++), l.head, mth));
        return params.toList();
    }

    /** Wrap a method invocation in an expression statement or return statement,
     *  depending on whether the method invocation expression's type is void.
     */
    public JCStatement Call(JCExpression apply) {
        return apply.type.hasTag(VOID) ? Exec(apply) : Return(apply);
    }

    /** Construct an assignment from a variable symbol and a right hand side.
     */
    public JCStatement Assignment(Symbol v, JCExpression rhs) {
        return Exec(Assign(Ident(v), rhs).setType(v.type));
    }

    /** Construct an index expression from a variable and an expression.
     */
    public JCArrayAccess Indexed(Symbol v, JCExpression index) {
        JCArrayAccess tree = new JCArrayAccess(QualIdent(v), index);
        tree.type = ((ArrayType)v.type).elemtype;
        return tree;
    }

    /** Make an attributed type cast expression.
     */
    public JCTypeCast TypeCast(Type type, JCExpression expr) {
        return (JCTypeCast)TypeCast(Type(type), expr).setType(type);
    }

/* ***************************************************************************
 * Helper methods.
 ****************************************************************************/

    /** Can given symbol be referred to in unqualified form?
     */
    boolean isUnqualifiable(Symbol sym) {
        if (sym.name == names.empty ||
            sym.owner == null ||
            sym.owner == syms.rootPackage ||
            sym.owner.kind == MTH || sym.owner.kind == VAR) {
            return true;
        } else if (sym.kind == TYP && toplevel != null) {
            for (Scope scope : new Scope[] {toplevel.namedImportScope,
                                            toplevel.packge.members(),
                                            toplevel.starImportScope,
                                            toplevel.moduleImportScope}) {
                Iterator<Symbol> it = scope.getSymbolsByName(sym.name).iterator();
                if (it.hasNext()) {
                    Symbol s = it.next();
                    return
                      s == sym &&
                      !it.hasNext();
                }
            }
        }
        return sym.kind == TYP && sym.isImplicit();
    }

    /** The name of synthetic parameter number `i'.
     */
    public Name paramName(int i)   { return names.fromString("x" + i); }

    /** The name of synthetic type parameter number `i'.
     */
    public Name typaramName(int i) { return names.fromString("A" + i); }
}
