/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.code.TypeTag.TYPEVAR;
import static com.sun.tools.javac.code.TypeTag.VOID;

/** This pass translates Generic Java to conventional Java.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class TransTypes extends TreeTranslator {
    /** The context key for the TransTypes phase. */
    protected static final Context.Key<TransTypes> transTypesKey =
        new Context.Key<TransTypes>();

    /** Get the instance for this context. */
    public static TransTypes instance(Context context) {
        TransTypes instance = context.get(transTypesKey);
        if (instance == null)
            instance = new TransTypes(context);
        return instance;
    }

    private Names names;
    private Log log;
    private Symtab syms;
    private TreeMaker make;
    private Enter enter;
    private boolean allowEnums;
    private Types types;
    private final Resolve resolve;

    /**
     * Flag to indicate whether or not to generate bridge methods.
     * For pre-Tiger source there is no need for bridge methods, so it
     * can be skipped to get better performance for -source 1.4 etc.
     */
    private final boolean addBridges;

    protected TransTypes(Context context) {
        context.put(transTypesKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        enter = Enter.instance(context);
        overridden = new HashMap<MethodSymbol,MethodSymbol>();
        Source source = Source.instance(context);
        allowEnums = source.allowEnums();
        addBridges = source.addBridges();
        types = Types.instance(context);
        make = TreeMaker.instance(context);
        resolve = Resolve.instance(context);
    }

    /** A hashtable mapping bridge methods to the methods they override after
     *  type erasure.
     */
    Map<MethodSymbol,MethodSymbol> overridden;

    /** Construct an attributed tree for a cast of expression to target type,
     *  unless it already has precisely that type.
     *  @param tree    The expression tree.
     *  @param target  The target type.
     */
    JCExpression cast(JCExpression tree, Type target) {
        int oldpos = make.pos;
        make.at(tree.pos);
        if (!types.isSameType(tree.type, target)) {
            if (!resolve.isAccessible(env, target.tsym))
                resolve.logAccessErrorInternal(env, tree, target);
            tree = make.TypeCast(make.Type(target), tree).setType(target);
        }
        make.pos = oldpos;
        return tree;
    }

    /** Construct an attributed tree to coerce an expression to some erased
     *  target type, unless the expression is already assignable to that type.
     *  If target type is a constant type, use its base type instead.
     *  @param tree    The expression tree.
     *  @param target  The target type.
     */
    public JCExpression coerce(Env<AttrContext> env, JCExpression tree, Type target) {
        Env<AttrContext> prevEnv = this.env;
        try {
            this.env = env;
            return coerce(tree, target);
        }
        finally {
            this.env = prevEnv;
        }
    }
    JCExpression coerce(JCExpression tree, Type target) {
        Type btarget = target.baseType();
        if (tree.type.isPrimitive() == target.isPrimitive()) {
            return types.isAssignable(tree.type, btarget, types.noWarnings)
                ? tree
                : cast(tree, btarget);
        }
        return tree;
    }

    /** Given an erased reference type, assume this type as the tree's type.
     *  Then, coerce to some given target type unless target type is null.
     *  This operation is used in situations like the following:
     *
     *  <pre>{@code
     *  class Cell<A> { A value; }
     *  ...
     *  Cell<Integer> cell;
     *  Integer x = cell.value;
     *  }</pre>
     *
     *  Since the erasure of Cell.value is Object, but the type
     *  of cell.value in the assignment is Integer, we need to
     *  adjust the original type of cell.value to Object, and insert
     *  a cast to Integer. That is, the last assignment becomes:
     *
     *  <pre>{@code
     *  Integer x = (Integer)cell.value;
     *  }</pre>
     *
     *  @param tree       The expression tree whose type might need adjustment.
     *  @param erasedType The expression's type after erasure.
     *  @param target     The target type, which is usually the erasure of the
     *                    expression's original type.
     */
    JCExpression retype(JCExpression tree, Type erasedType, Type target) {
//      System.err.println("retype " + tree + " to " + erasedType);//DEBUG
        if (!erasedType.isPrimitive()) {
            if (target != null && target.isPrimitive())
                target = erasure(tree.type);
            tree.type = erasedType;
            if (target != null) return coerce(tree, target);
        }
        return tree;
    }

    /** Translate method argument list, casting each argument
     *  to its corresponding type in a list of target types.
     *  @param _args            The method argument list.
     *  @param parameters       The list of target types.
     *  @param varargsElement   The erasure of the varargs element type,
     *  or null if translating a non-varargs invocation
     */
    <T extends JCTree> List<T> translateArgs(List<T> _args,
                                           List<Type> parameters,
                                           Type varargsElement) {
        if (parameters.isEmpty()) return _args;
        List<T> args = _args;
        while (parameters.tail.nonEmpty()) {
            args.head = translate(args.head, parameters.head);
            args = args.tail;
            parameters = parameters.tail;
        }
        Type parameter = parameters.head;
        Assert.check(varargsElement != null || args.length() == 1);
        if (varargsElement != null) {
            while (args.nonEmpty()) {
                args.head = translate(args.head, varargsElement);
                args = args.tail;
            }
        } else {
            args.head = translate(args.head, parameter);
        }
        return _args;
    }

    public <T extends JCTree> List<T> translateArgs(List<T> _args,
                                           List<Type> parameters,
                                           Type varargsElement,
                                           Env<AttrContext> localEnv) {
        Env<AttrContext> prevEnv = env;
        try {
            env = localEnv;
            return translateArgs(_args, parameters, varargsElement);
        }
        finally {
            env = prevEnv;
        }
    }

    /** Add a bridge definition and enter corresponding method symbol in
     *  local scope of origin.
     *
     *  @param pos     The source code position to be used for the definition.
     *  @param meth    The method for which a bridge needs to be added
     *  @param impl    That method's implementation (possibly the method itself)
     *  @param origin  The class to which the bridge will be added
     *  @param hypothetical
     *                 True if the bridge method is not strictly necessary in the
     *                 binary, but is represented in the symbol table to detect
     *                 erasure clashes.
     *  @param bridges The list buffer to which the bridge will be added
     */
    void addBridge(DiagnosticPosition pos,
                   MethodSymbol meth,
                   MethodSymbol impl,
                   ClassSymbol origin,
                   boolean hypothetical,
                   ListBuffer<JCTree> bridges) {
        make.at(pos);
        Type origType = types.memberType(origin.type, meth);
        Type origErasure = erasure(origType);

        // Create a bridge method symbol and a bridge definition without a body.
        Type bridgeType = meth.erasure(types);
        long flags = impl.flags() & AccessFlags | SYNTHETIC | BRIDGE;
        if (hypothetical) flags |= HYPOTHETICAL;
        MethodSymbol bridge = new MethodSymbol(flags,
                                               meth.name,
                                               bridgeType,
                                               origin);
        if (!hypothetical) {
            JCMethodDecl md = make.MethodDef(bridge, null);

            // The bridge calls this.impl(..), if we have an implementation
            // in the current class, super.impl(...) otherwise.
            JCExpression receiver = (impl.owner == origin)
                ? make.This(origin.erasure(types))
                : make.Super(types.supertype(origin.type).tsym.erasure(types), origin);

            // The type returned from the original method.
            Type calltype = erasure(impl.type.getReturnType());

            // Construct a call of  this.impl(params), or super.impl(params),
            // casting params and possibly results as needed.
            JCExpression call =
                make.Apply(
                           null,
                           make.Select(receiver, impl).setType(calltype),
                           translateArgs(make.Idents(md.params), origErasure.getParameterTypes(), null))
                .setType(calltype);
            JCStatement stat = (origErasure.getReturnType().hasTag(VOID))
                ? make.Exec(call)
                : make.Return(coerce(call, bridgeType.getReturnType()));
            md.body = make.Block(0, List.of(stat));

            // Add bridge to `bridges' buffer
            bridges.append(md);
        }

        // Add bridge to scope of enclosing class and `overridden' table.
        origin.members().enter(bridge);
        overridden.put(bridge, meth);
    }

    /** Add bridge if given symbol is a non-private, non-static member
     *  of the given class, which is either defined in the class or non-final
     *  inherited, and one of the two following conditions holds:
     *  1. The method's type changes in the given class, as compared to the
     *     class where the symbol was defined, (in this case
     *     we have extended a parameterized class with non-trivial parameters).
     *  2. The method has an implementation with a different erased return type.
     *     (in this case we have used co-variant returns).
     *  If a bridge already exists in some other class, no new bridge is added.
     *  Instead, it is checked that the bridge symbol overrides the method symbol.
     *  (Spec ???).
     *  todo: what about bridges for privates???
     *
     *  @param pos     The source code position to be used for the definition.
     *  @param sym     The symbol for which a bridge might have to be added.
     *  @param origin  The class in which the bridge would go.
     *  @param bridges The list buffer to which the bridge would be added.
     */
    void addBridgeIfNeeded(DiagnosticPosition pos,
                           Symbol sym,
                           ClassSymbol origin,
                           ListBuffer<JCTree> bridges) {
        if (sym.kind == MTH &&
            sym.name != names.init &&
            (sym.flags() & (PRIVATE | STATIC)) == 0 &&
            (sym.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) != SYNTHETIC &&
            sym.isMemberOf(origin, types))
        {
            MethodSymbol meth = (MethodSymbol)sym;
            MethodSymbol bridge = meth.binaryImplementation(origin, types);
            MethodSymbol impl = meth.implementation(origin, types, true, overrideBridgeFilter);
            if (bridge == null ||
                bridge == meth ||
                (impl != null && !bridge.owner.isSubClass(impl.owner, types))) {
                // No bridge was added yet.
                if (impl != null && isBridgeNeeded(meth, impl, origin.type)) {
                    addBridge(pos, meth, impl, origin, bridge==impl, bridges);
                } else if (impl == meth
                           && impl.owner != origin
                           && (impl.flags() & FINAL) == 0
                           && (meth.flags() & (ABSTRACT|PUBLIC)) == PUBLIC
                           && (origin.flags() & PUBLIC) > (impl.owner.flags() & PUBLIC)) {
                    // this is to work around a horrible but permanent
                    // reflection design error.
                    addBridge(pos, meth, impl, origin, false, bridges);
                }
            } else if ((bridge.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) == SYNTHETIC) {
                MethodSymbol other = overridden.get(bridge);
                if (other != null && other != meth) {
                    if (impl == null || !impl.overrides(other, origin, types, true)) {
                        // Bridge for other symbol pair was added
                        log.error(pos, "name.clash.same.erasure.no.override",
                                  other, other.location(origin.type, types),
                                  meth,  meth.location(origin.type, types));
                    }
                }
            } else if (!bridge.overrides(meth, origin, types, true)) {
                // Accidental binary override without source override.
                if (bridge.owner == origin ||
                    types.asSuper(bridge.owner.type, meth.owner) == null)
                    // Don't diagnose the problem if it would already
                    // have been reported in the superclass
                    log.error(pos, "name.clash.same.erasure.no.override",
                              bridge, bridge.location(origin.type, types),
                              meth,  meth.location(origin.type, types));
            }
        }
    }
    // where
        Filter<Symbol> overrideBridgeFilter = new Filter<Symbol>() {
            public boolean accepts(Symbol s) {
                return (s.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) != SYNTHETIC;
            }
        };
        /**
         * @param method The symbol for which a bridge might have to be added
         * @param impl The implementation of method
         * @param dest The type in which the bridge would go
         */
        private boolean isBridgeNeeded(MethodSymbol method,
                                       MethodSymbol impl,
                                       Type dest) {
            if (impl != method) {
                // If either method or impl have different erasures as
                // members of dest, a bridge is needed.
                Type method_erasure = method.erasure(types);
                if (!isSameMemberWhenErased(dest, method, method_erasure))
                    return true;
                Type impl_erasure = impl.erasure(types);
                if (!isSameMemberWhenErased(dest, impl, impl_erasure))
                    return true;

                // If the erasure of the return type is different, a
                // bridge is needed.
                return !types.isSameType(impl_erasure.getReturnType(),
                                         method_erasure.getReturnType());
            } else {
               // method and impl are the same...
                if ((method.flags() & ABSTRACT) != 0) {
                    // ...and abstract so a bridge is not needed.
                    // Concrete subclasses will bridge as needed.
                    return false;
                }

                // The erasure of the return type is always the same
                // for the same symbol.  Reducing the three tests in
                // the other branch to just one:
                return !isSameMemberWhenErased(dest, method, method.erasure(types));
            }
        }
        /**
         * Lookup the method as a member of the type.  Compare the
         * erasures.
         * @param type the class where to look for the method
         * @param method the method to look for in class
         * @param erasure the erasure of method
         */
        private boolean isSameMemberWhenErased(Type type,
                                               MethodSymbol method,
                                               Type erasure) {
            return types.isSameType(erasure(types.memberType(type, method)),
                                    erasure);
        }

    void addBridges(DiagnosticPosition pos,
                    TypeSymbol i,
                    ClassSymbol origin,
                    ListBuffer<JCTree> bridges) {
        for (Scope.Entry e = i.members().elems; e != null; e = e.sibling)
            addBridgeIfNeeded(pos, e.sym, origin, bridges);
        for (List<Type> l = types.interfaces(i.type); l.nonEmpty(); l = l.tail)
            addBridges(pos, l.head.tsym, origin, bridges);
    }

    /** Add all necessary bridges to some class appending them to list buffer.
     *  @param pos     The source code position to be used for the bridges.
     *  @param origin  The class in which the bridges go.
     *  @param bridges The list buffer to which the bridges are added.
     */
    void addBridges(DiagnosticPosition pos, ClassSymbol origin, ListBuffer<JCTree> bridges) {
        Type st = types.supertype(origin.type);
        while (st.hasTag(CLASS)) {
//          if (isSpecialization(st))
            addBridges(pos, st.tsym, origin, bridges);
            st = types.supertype(st);
        }
        for (List<Type> l = types.interfaces(origin.type); l.nonEmpty(); l = l.tail)
//          if (isSpecialization(l.head))
            addBridges(pos, l.head.tsym, origin, bridges);
    }

/* ************************************************************************
 * Visitor methods
 *************************************************************************/

    /** Visitor argument: proto-type.
     */
    private Type pt;

    /** Visitor method: perform a type translation on tree.
     */
    public <T extends JCTree> T translate(T tree, Type pt) {
        Type prevPt = this.pt;
        try {
            this.pt = pt;
            return translate(tree);
        } finally {
            this.pt = prevPt;
        }
    }

    /** Visitor method: perform a type translation on list of trees.
     */
    public <T extends JCTree> List<T> translate(List<T> trees, Type pt) {
        Type prevPt = this.pt;
        List<T> res;
        try {
            this.pt = pt;
            res = translate(trees);
        } finally {
            this.pt = prevPt;
        }
        return res;
    }

    public void visitClassDef(JCClassDecl tree) {
        translateClass(tree.sym);
        result = tree;
    }

    JCTree currentMethod = null;
    public void visitMethodDef(JCMethodDecl tree) {
        JCTree previousMethod = currentMethod;
        try {
            currentMethod = tree;
            tree.restype = translate(tree.restype, null);
            tree.typarams = List.nil();
            tree.params = translateVarDefs(tree.params);
            tree.recvparam = translate(tree.recvparam, null);
            tree.thrown = translate(tree.thrown, null);
            tree.body = translate(tree.body, tree.sym.erasure(types).getReturnType());
            tree.type = erasure(tree.type);
            result = tree;
        } finally {
            currentMethod = previousMethod;
        }

        // Check that we do not introduce a name clash by erasing types.
        for (Scope.Entry e = tree.sym.owner.members().lookup(tree.name);
             e.sym != null;
             e = e.next()) {
            if (e.sym != tree.sym &&
                types.isSameType(erasure(e.sym.type), tree.type)) {
                log.error(tree.pos(),
                          "name.clash.same.erasure", tree.sym,
                          e.sym);
                return;
            }
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        tree.vartype = translate(tree.vartype, null);
        tree.init = translate(tree.init, tree.sym.erasure(types));
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        tree.body = translate(tree.body);
        tree.cond = translate(tree.cond, syms.booleanType);
        result = tree;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitForLoop(JCForLoop tree) {
        tree.init = translate(tree.init, null);
        if (tree.cond != null)
            tree.cond = translate(tree.cond, syms.booleanType);
        tree.step = translate(tree.step, null);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        tree.var = translate(tree.var, null);
        Type iterableType = tree.expr.type;
        tree.expr = translate(tree.expr, erasure(tree.expr.type));
        if (types.elemtype(tree.expr.type) == null)
            tree.expr.type = iterableType; // preserve type for Lower
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitLambda(JCLambda tree) {
        JCTree prevMethod = currentMethod;
        try {
            currentMethod = null;
            tree.params = translate(tree.params);
            tree.body = translate(tree.body, null);
            tree.type = erasure(tree.type);
            result = tree;
        }
        finally {
            currentMethod = prevMethod;
        }
    }

    public void visitSwitch(JCSwitch tree) {
        Type selsuper = types.supertype(tree.selector.type);
        boolean enumSwitch = selsuper != null &&
            selsuper.tsym == syms.enumSym;
        Type target = enumSwitch ? erasure(tree.selector.type) : syms.intType;
        tree.selector = translate(tree.selector, target);
        tree.cases = translateCases(tree.cases);
        result = tree;
    }

    public void visitCase(JCCase tree) {
        tree.pat = translate(tree.pat, null);
        tree.stats = translate(tree.stats);
        result = tree;
    }

    public void visitSynchronized(JCSynchronized tree) {
        tree.lock = translate(tree.lock, erasure(tree.lock.type));
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitTry(JCTry tree) {
        tree.resources = translate(tree.resources, syms.autoCloseableType);
        tree.body = translate(tree.body);
        tree.catchers = translateCatchers(tree.catchers);
        tree.finalizer = translate(tree.finalizer);
        result = tree;
    }

    public void visitConditional(JCConditional tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.truepart = translate(tree.truepart, erasure(tree.type));
        tree.falsepart = translate(tree.falsepart, erasure(tree.type));
        tree.type = erasure(tree.type);
        result = retype(tree, tree.type, pt);
    }

   public void visitIf(JCIf tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.thenpart = translate(tree.thenpart);
        tree.elsepart = translate(tree.elsepart);
        result = tree;
    }

    public void visitExec(JCExpressionStatement tree) {
        tree.expr = translate(tree.expr, null);
        result = tree;
    }

    public void visitReturn(JCReturn tree) {
        tree.expr = translate(tree.expr, currentMethod != null ? types.erasure(currentMethod.type).getReturnType() : null);
        result = tree;
    }

    public void visitThrow(JCThrow tree) {
        tree.expr = translate(tree.expr, erasure(tree.expr.type));
        result = tree;
    }

    public void visitAssert(JCAssert tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        if (tree.detail != null)
            tree.detail = translate(tree.detail, erasure(tree.detail.type));
        result = tree;
    }

    public void visitApply(JCMethodInvocation tree) {
        tree.meth = translate(tree.meth, null);
        Symbol meth = TreeInfo.symbol(tree.meth);
        Type mt = meth.erasure(types);
        List<Type> argtypes = mt.getParameterTypes();
        if (allowEnums &&
            meth.name==names.init &&
            meth.owner == syms.enumSym)
            argtypes = argtypes.tail.tail;
        if (tree.varargsElement != null)
            tree.varargsElement = types.erasure(tree.varargsElement);
        else
            Assert.check(tree.args.length() == argtypes.length());
        tree.args = translateArgs(tree.args, argtypes, tree.varargsElement);

        tree.type = types.erasure(tree.type);
        // Insert casts of method invocation results as needed.
        result = retype(tree, mt.getReturnType(), pt);
    }

    public void visitNewClass(JCNewClass tree) {
        if (tree.encl != null)
            tree.encl = translate(tree.encl, erasure(tree.encl.type));
        tree.clazz = translate(tree.clazz, null);
        if (tree.varargsElement != null)
            tree.varargsElement = types.erasure(tree.varargsElement);
        tree.args = translateArgs(
            tree.args, tree.constructor.erasure(types).getParameterTypes(), tree.varargsElement);
        tree.def = translate(tree.def, null);
        if (tree.constructorType != null)
            tree.constructorType = erasure(tree.constructorType);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitNewArray(JCNewArray tree) {
        tree.elemtype = translate(tree.elemtype, null);
        translate(tree.dims, syms.intType);
        if (tree.type != null) {
            tree.elems = translate(tree.elems, erasure(types.elemtype(tree.type)));
            tree.type = erasure(tree.type);
        } else {
            tree.elems = translate(tree.elems, null);
        }

        result = tree;
    }

    public void visitParens(JCParens tree) {
        tree.expr = translate(tree.expr, pt);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitAssign(JCAssign tree) {
        tree.lhs = translate(tree.lhs, null);
        tree.rhs = translate(tree.rhs, erasure(tree.lhs.type));
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitAssignop(JCAssignOp tree) {
        tree.lhs = translate(tree.lhs, null);
        tree.rhs = translate(tree.rhs, tree.operator.type.getParameterTypes().tail.head);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitUnary(JCUnary tree) {
        tree.arg = translate(tree.arg, tree.operator.type.getParameterTypes().head);
        result = tree;
    }

    public void visitBinary(JCBinary tree) {
        tree.lhs = translate(tree.lhs, tree.operator.type.getParameterTypes().head);
        tree.rhs = translate(tree.rhs, tree.operator.type.getParameterTypes().tail.head);
        result = tree;
    }

    public void visitTypeCast(JCTypeCast tree) {
        tree.clazz = translate(tree.clazz, null);
        tree.type = erasure(tree.type);
        tree.expr = translate(tree.expr, tree.type);
        result = tree;
    }

    public void visitTypeTest(JCInstanceOf tree) {
        tree.expr = translate(tree.expr, null);
        tree.clazz = translate(tree.clazz, null);
        result = tree;
    }

    public void visitIndexed(JCArrayAccess tree) {
        tree.indexed = translate(tree.indexed, erasure(tree.indexed.type));
        tree.index = translate(tree.index, syms.intType);

        // Insert casts of indexed expressions as needed.
        result = retype(tree, types.elemtype(tree.indexed.type), pt);
    }

    // There ought to be nothing to rewrite here;
    // we don't generate code.
    public void visitAnnotation(JCAnnotation tree) {
        result = tree;
    }

    public void visitIdent(JCIdent tree) {
        Type et = tree.sym.erasure(types);

        // Map type variables to their bounds.
        if (tree.sym.kind == TYP && tree.sym.type.hasTag(TYPEVAR)) {
            result = make.at(tree.pos).Type(et);
        } else
        // Map constants expressions to themselves.
        if (tree.type.constValue() != null) {
            result = tree;
        }
        // Insert casts of variable uses as needed.
        else if (tree.sym.kind == VAR) {
            result = retype(tree, et, pt);
        }
        else {
            tree.type = erasure(tree.type);
            result = tree;
        }
    }

    public void visitSelect(JCFieldAccess tree) {
        Type t = tree.selected.type;
        while (t.hasTag(TYPEVAR))
            t = t.getUpperBound();
        if (t.isCompound()) {
            if ((tree.sym.flags() & IPROXY) != 0) {
                tree.sym = ((MethodSymbol)tree.sym).
                    implemented((TypeSymbol)tree.sym.owner, types);
            }
            tree.selected = coerce(
                translate(tree.selected, erasure(tree.selected.type)),
                erasure(tree.sym.owner.type));
        } else
            tree.selected = translate(tree.selected, erasure(t));

        // Map constants expressions to themselves.
        if (tree.type.constValue() != null) {
            result = tree;
        }
        // Insert casts of variable uses as needed.
        else if (tree.sym.kind == VAR) {
            result = retype(tree, tree.sym.erasure(types), pt);
        }
        else {
            tree.type = erasure(tree.type);
            result = tree;
        }
    }

    public void visitReference(JCMemberReference tree) {
        tree.expr = translate(tree.expr, null);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitTypeArray(JCArrayTypeTree tree) {
        tree.elemtype = translate(tree.elemtype, null);
        tree.type = erasure(tree.type);
        result = tree;
    }

    /** Visitor method for parameterized types.
     */
    public void visitTypeApply(JCTypeApply tree) {
        JCTree clazz = translate(tree.clazz, null);
        result = clazz;
    }

    public void visitTypeIntersection(JCTypeIntersection tree) {
        tree.bounds = translate(tree.bounds, null);
        tree.type = erasure(tree.type);
        result = tree;
    }

/**************************************************************************
 * utility methods
 *************************************************************************/

    private Type erasure(Type t) {
        return types.erasure(t);
    }

    private boolean boundsRestricted(ClassSymbol c) {
        Type st = types.supertype(c.type);
        if (st.isParameterized()) {
            List<Type> actuals = st.allparams();
            List<Type> formals = st.tsym.type.allparams();
            while (!actuals.isEmpty() && !formals.isEmpty()) {
                Type actual = actuals.head;
                Type formal = formals.head;

                if (!types.isSameType(types.erasure(actual),
                        types.erasure(formal)))
                    return true;

                actuals = actuals.tail;
                formals = formals.tail;
            }
        }
        return false;
    }

    private List<JCTree> addOverrideBridgesIfNeeded(DiagnosticPosition pos,
                                    final ClassSymbol c) {
        ListBuffer<JCTree> buf = ListBuffer.lb();
        if (c.isInterface() || !boundsRestricted(c))
            return buf.toList();
        Type t = types.supertype(c.type);
            Scope s = t.tsym.members();
            if (s.elems != null) {
                for (Symbol sym : s.getElements(new NeedsOverridBridgeFilter(c))) {

                    MethodSymbol m = (MethodSymbol)sym;
                    MethodSymbol member = (MethodSymbol)m.asMemberOf(c.type, types);
                    MethodSymbol impl = m.implementation(c, types, false);

                    if ((impl == null || impl.owner != c) &&
                            !types.isSameType(member.erasure(types), m.erasure(types))) {
                        addOverrideBridges(pos, m, member, c, buf);
                    }
                }
            }
        return buf.toList();
    }
    // where
        class NeedsOverridBridgeFilter implements Filter<Symbol> {

            ClassSymbol c;

            NeedsOverridBridgeFilter(ClassSymbol c) {
                this.c = c;
            }
            public boolean accepts(Symbol s) {
                return s.kind == MTH &&
                            !s.isConstructor() &&
                            s.isInheritedIn(c, types) &&
                            (s.flags() & FINAL) == 0 &&
                            (s.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) != SYNTHETIC;
            }
        }

    private void addOverrideBridges(DiagnosticPosition pos,
                                    MethodSymbol impl,
                                    MethodSymbol member,
                                    ClassSymbol c,
                                    ListBuffer<JCTree> bridges) {
        Type implErasure = impl.erasure(types);
        long flags = (impl.flags() & AccessFlags) | SYNTHETIC | BRIDGE | OVERRIDE_BRIDGE;
        member = new MethodSymbol(flags, member.name, member.type, c);
        JCMethodDecl md = make.MethodDef(member, null);
        JCExpression receiver = make.Super(types.supertype(c.type).tsym.erasure(types), c);
        Type calltype = erasure(impl.type.getReturnType());
        JCExpression call =
            make.Apply(null,
                       make.Select(receiver, impl).setType(calltype),
                       translateArgs(make.Idents(md.params),
                                     implErasure.getParameterTypes(), null))
            .setType(calltype);
        JCStatement stat = (member.getReturnType().hasTag(VOID))
            ? make.Exec(call)
            : make.Return(coerce(call, member.erasure(types).getReturnType()));
        md.body = make.Block(0, List.of(stat));
        c.members().enter(member);
        bridges.append(md);
    }

/**************************************************************************
 * main method
 *************************************************************************/

    private Env<AttrContext> env;

    void translateClass(ClassSymbol c) {
        Type st = types.supertype(c.type);

        // process superclass before derived
        if (st.hasTag(CLASS))
            translateClass((ClassSymbol)st.tsym);

        Env<AttrContext> myEnv = enter.typeEnvs.remove(c);
        if (myEnv == null)
            return;
        Env<AttrContext> oldEnv = env;
        try {
            env = myEnv;
            // class has not been translated yet

            TreeMaker savedMake = make;
            Type savedPt = pt;
            make = make.forToplevel(env.toplevel);
            pt = null;
            try {
                JCClassDecl tree = (JCClassDecl) env.tree;
                tree.typarams = List.nil();
                super.visitClassDef(tree);
                make.at(tree.pos);
                if (addBridges) {
                    ListBuffer<JCTree> bridges = new ListBuffer<JCTree>();
                    if (false) //see CR: 6996415
                        bridges.appendList(addOverrideBridgesIfNeeded(tree, c));
                    if ((tree.sym.flags() & INTERFACE) == 0)
                        addBridges(tree.pos(), tree.sym, bridges);
                    tree.defs = bridges.toList().prependList(tree.defs);
                }
                tree.type = erasure(tree.type);
            } finally {
                make = savedMake;
                pt = savedPt;
            }
        } finally {
            env = oldEnv;
        }
    }

    /** Translate a toplevel class definition.
     *  @param cdef    The definition to be translated.
     */
    public JCTree translateTopLevelClass(JCTree cdef, TreeMaker make) {
        // note that this method does NOT support recursion.
        this.make = make;
        pt = null;
        return translate(cdef, null);
    }
}
