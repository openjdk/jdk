/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.LambdaToMethod.LambdaAnalyzer.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.source.tree.MemberReferenceTree.ReferenceMode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.sun.tools.javac.comp.LambdaToMethod.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.code.TypeTag.NONE;
import static com.sun.tools.javac.code.TypeTag.VOID;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * This pass desugars lambda expressions into static methods
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LambdaToMethod extends TreeTranslator {

    private Names names;
    private Symtab syms;
    private Resolve rs;
    private TreeMaker make;
    private Types types;
    private TransTypes transTypes;
    private Env<AttrContext> attrEnv;

    /** the analyzer scanner */
    private LambdaAnalyzer analyzer;

    /** map from lambda trees to translation contexts */
    private Map<JCTree, TranslationContext<?>> contextMap;

    /** current translation context (visitor argument) */
    private TranslationContext<?> context;

    /** list of translated methods
     **/
    private ListBuffer<JCTree> translatedMethodList;

    // <editor-fold defaultstate="collapsed" desc="Instantiating">
    private static final Context.Key<LambdaToMethod> unlambdaKey =
            new Context.Key<LambdaToMethod>();

    public static LambdaToMethod instance(Context context) {
        LambdaToMethod instance = context.get(unlambdaKey);
        if (instance == null) {
            instance = new LambdaToMethod(context);
        }
        return instance;
    }

    private LambdaToMethod(Context context) {
        names = Names.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        transTypes = TransTypes.instance(context);
        this.analyzer = makeAnalyzer();
    }

    private LambdaAnalyzer makeAnalyzer() {
        return new LambdaAnalyzer();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="translate methods">
    @Override
    public <T extends JCTree> T translate(T tree) {
        TranslationContext<?> newContext = contextMap.get(tree);
        return translate(tree, newContext != null ? newContext : context);
    }

    public <T extends JCTree> T translate(T tree, TranslationContext<?> newContext) {
        TranslationContext<?> prevContext = context;
        try {
            context = newContext;
            return super.translate(tree);
        }
        finally {
            context = prevContext;
        }
    }

    public <T extends JCTree> List<T> translate(List<T> trees, TranslationContext<?> newContext) {
        ListBuffer<T> buf = ListBuffer.lb();
        for (T tree : trees) {
            buf.append(translate(tree, newContext));
        }
        return buf.toList();
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        this.make = make;
        this.attrEnv = env;
        this.context = null;
        this.contextMap = new HashMap<JCTree, TranslationContext<?>>();
        return translate(cdef);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="visitor methods">
    /**
     * Visit a class.
     * Maintain the translatedMethodList across nested classes.
     * Append the translatedMethodList to the class after it is translated.
     * @param tree
     */
    @Override
    public void visitClassDef(JCClassDecl tree) {
        if (tree.sym.owner.kind == PCK) {
            //analyze class
            analyzer.analyzeClass(tree);
        }
        ListBuffer<JCTree> prevTranslated = translatedMethodList;
        try {
            translatedMethodList = ListBuffer.lb();
            super.visitClassDef(tree);
            //add all translated instance methods here
            tree.defs = tree.defs.appendList(translatedMethodList.toList());
            for (JCTree lambda : translatedMethodList) {
                tree.sym.members().enter(((JCMethodDecl)lambda).sym);
            }
            result = tree;
        } finally {
            translatedMethodList = prevTranslated;
        }
    }

    /**
     * Translate a lambda into a method to be inserted into the class.
     * Then replace the lambda site with an invokedynamic call of to lambda
     * meta-factory, which will use the lambda method.
     * @param tree
     */
    @Override
    public void visitLambda(JCLambda tree) {
        LambdaTranslationContext localContext = (LambdaTranslationContext)context;
        MethodSymbol sym = (MethodSymbol)localContext.translatedSym;
        MethodType lambdaType = (MethodType) sym.type;

        //create the method declaration hoisting the lambda body
        JCMethodDecl lambdaDecl = make.MethodDef(make.Modifiers(sym.flags_field),
                sym.name,
                make.QualIdent(lambdaType.getReturnType().tsym),
                List.<JCTypeParameter>nil(),
                localContext.syntheticParams,
                lambdaType.getThrownTypes() == null ?
                    List.<JCExpression>nil() :
                    make.Types(lambdaType.getThrownTypes()),
                null,
                null);
        lambdaDecl.sym = sym;
        lambdaDecl.type = lambdaType;

        //translate lambda body
        //As the lambda body is translated, all references to lambda locals,
        //captured variables, enclosing members are adjusted accordingly
        //to refer to the static method parameters (rather than i.e. acessing to
        //captured members directly).
        lambdaDecl.body = translate(makeLambdaBody(tree, lambdaDecl));

        //Add the method to the list of methods to be added to this class.
        translatedMethodList = translatedMethodList.prepend(lambdaDecl);

        //now that we have generated a method for the lambda expression,
        //we can translate the lambda into a method reference pointing to the newly
        //created method.
        //
        //Note that we need to adjust the method handle so that it will match the
        //signature of the SAM descriptor - this means that the method reference
        //should be added the following synthetic arguments:
        //
        // * the "this" argument if it is an instance method
        // * enclosing locals captured by the lambda expression

        ListBuffer<JCExpression> syntheticInits = ListBuffer.lb();

        if (!sym.isStatic()) {
            syntheticInits.append(makeThis(
                    sym.owner.asType(),
                    localContext.owner.enclClass()));
        }

        //add captured locals
        for (Symbol fv : localContext.getSymbolMap(CAPTURED_VAR).keySet()) {
            if (fv != localContext.self) {
                JCTree captured_local = make.Ident(fv).setType(fv.type);
                syntheticInits.append((JCExpression) captured_local);
            }
        }

        //then, determine the arguments to the indy call
        List<JCExpression> indy_args = translate(syntheticInits.toList(), localContext.prev);

        //build a sam instance using an indy call to the meta-factory
        int refKind = referenceKind(sym);

        //convert to an invokedynamic call
        result = makeMetaFactoryIndyCall(tree, refKind, sym, indy_args);
    }

    private JCIdent makeThis(Type type, Symbol owner) {
        VarSymbol _this = new VarSymbol(PARAMETER | FINAL | SYNTHETIC,
                names._this,
                type,
                owner);
        return make.Ident(_this);
    }

    /**
     * Translate a method reference into an invokedynamic call to the
     * meta-factory.
     * @param tree
     */
    @Override
    public void visitReference(JCMemberReference tree) {
        ReferenceTranslationContext localContext = (ReferenceTranslationContext)context;

        //first determine the method symbol to be used to generate the sam instance
        //this is either the method reference symbol, or the bridged reference symbol
        Symbol refSym = localContext.needsBridge() ?
            localContext.bridgeSym :
            tree.sym;

        //build the bridge method, if needed
        if (localContext.needsBridge()) {
            bridgeMemberReference(tree, localContext);
        }

        //the qualifying expression is treated as a special captured arg
        JCExpression init;
        switch(tree.kind) {

            case IMPLICIT_INNER:    /** Inner :: new */
            case SUPER:             /** super :: instMethod */
                init = makeThis(
                    localContext.owner.owner.asType(),
                    localContext.owner);
                break;

            case BOUND:             /** Expr :: instMethod */
                init = tree.getQualifierExpression();
                break;

            case UNBOUND:           /** Type :: instMethod */
            case STATIC:            /** Type :: staticMethod */
            case TOPLEVEL:          /** Top level :: new */
            case ARRAY_CTOR:        /** ArrayType :: new */
                init = null;
                break;

            default:
                throw new InternalError("Should not have an invalid kind");
        }

        List<JCExpression> indy_args = init==null? List.<JCExpression>nil() : translate(List.of(init), localContext.prev);


        //build a sam instance using an indy call to the meta-factory
        result = makeMetaFactoryIndyCall(tree, localContext.referenceKind(), refSym, indy_args);
    }

    /**
     * Translate identifiers within a lambda to the mapped identifier
     * @param tree
     */
    @Override
    public void visitIdent(JCIdent tree) {
        if (context == null || !analyzer.lambdaIdentSymbolFilter(tree.sym)) {
            super.visitIdent(tree);
        } else {
            LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
            if (lambdaContext.getSymbolMap(PARAM).containsKey(tree.sym)) {
                Symbol translatedSym = lambdaContext.getSymbolMap(PARAM).get(tree.sym);
                result = make.Ident(translatedSym).setType(tree.type);
            } else if (lambdaContext.getSymbolMap(LOCAL_VAR).containsKey(tree.sym)) {
                Symbol translatedSym = lambdaContext.getSymbolMap(LOCAL_VAR).get(tree.sym);
                result = make.Ident(translatedSym).setType(tree.type);
            } else if (lambdaContext.getSymbolMap(CAPTURED_VAR).containsKey(tree.sym)) {
                Symbol translatedSym = lambdaContext.getSymbolMap(CAPTURED_VAR).get(tree.sym);
                result = make.Ident(translatedSym).setType(tree.type);
            } else {
                if (tree.sym.owner.kind == Kinds.TYP) {
                    for (Map.Entry<Symbol, Symbol> encl_entry : lambdaContext.getSymbolMap(CAPTURED_THIS).entrySet()) {
                        if (tree.sym.isMemberOf((ClassSymbol) encl_entry.getKey(), types)) {
                            JCExpression enclRef = make.Ident(encl_entry.getValue());
                            result = tree.sym.name == names._this
                                    ? enclRef.setType(tree.type)
                                    : make.Select(enclRef, tree.sym).setType(tree.type);
                            result = tree;
                            return;
                        }
                    }
                }
                //access to untranslated symbols (i.e. compile-time constants,
                //members defined inside the lambda body, etc.) )
                super.visitIdent(tree);
            }
        }
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        LambdaTranslationContext lambdaContext = (LambdaTranslationContext)context;
        if (context != null && lambdaContext.getSymbolMap(LOCAL_VAR).containsKey(tree.sym)) {
            JCExpression init = translate(tree.init);
            result = make.VarDef((VarSymbol)lambdaContext.getSymbolMap(LOCAL_VAR).get(tree.sym), init);
        } else {
            super.visitVarDef(tree);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Translation helper methods">

    private JCBlock makeLambdaBody(JCLambda tree, JCMethodDecl lambdaMethodDecl) {
        return tree.getBodyKind() == JCLambda.BodyKind.EXPRESSION ?
                makeLambdaExpressionBody((JCExpression)tree.body, lambdaMethodDecl) :
                makeLambdaStatementBody((JCBlock)tree.body, lambdaMethodDecl, tree.canCompleteNormally);
    }

    private JCBlock makeLambdaExpressionBody(JCExpression expr, JCMethodDecl lambdaMethodDecl) {
        Type restype = lambdaMethodDecl.type.getReturnType();
        boolean isLambda_void = expr.type.hasTag(VOID);
        boolean isTarget_void = restype.hasTag(VOID);
        boolean isTarget_Void = types.isSameType(restype, types.boxedClass(syms.voidType).type);
        if (isTarget_void) {
            //target is void:
            // BODY;
            JCStatement stat = make.Exec(expr);
            return make.Block(0, List.<JCStatement>of(stat));
        } else if (isLambda_void && isTarget_Void) {
            //void to Void conversion:
            // BODY; return null;
            ListBuffer<JCStatement> stats = ListBuffer.lb();
            stats.append(make.Exec(expr));
            stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
            return make.Block(0, stats.toList());
        } else {
            //non-void to non-void conversion:
            // return (TYPE)BODY;
            JCExpression retExpr = transTypes.coerce(attrEnv, expr, restype);
            return make.Block(0, List.<JCStatement>of(make.Return(retExpr)));
        }
    }

    private JCBlock makeLambdaStatementBody(JCBlock block, final JCMethodDecl lambdaMethodDecl, boolean completeNormally) {
        final Type restype = lambdaMethodDecl.type.getReturnType();
        final boolean isTarget_void = restype.hasTag(VOID);
        boolean isTarget_Void = types.isSameType(restype, types.boxedClass(syms.voidType).type);

        class LambdaBodyTranslator extends TreeTranslator {

            @Override
            public void visitClassDef(JCClassDecl tree) {
                //do NOT recurse on any inner classes
                result = tree;
            }

            @Override
            public void visitLambda(JCLambda tree) {
                //do NOT recurse on any nested lambdas
                result = tree;
            }

            @Override
            public void visitReturn(JCReturn tree) {
                boolean isLambda_void = tree.expr == null;
                if (isTarget_void && !isLambda_void) {
                    //Void to void conversion:
                    // { TYPE $loc = RET-EXPR; return; }
                    VarSymbol loc = makeSyntheticVar(0, names.fromString("$loc"), tree.expr.type, lambdaMethodDecl.sym);
                    JCVariableDecl varDef = make.VarDef(loc, tree.expr);
                    result = make.Block(0, List.<JCStatement>of(varDef, make.Return(null)));
                } else if (!isTarget_void || !isLambda_void) {
                    //non-void to non-void conversion:
                    // return (TYPE)RET-EXPR;
                    tree.expr = transTypes.coerce(attrEnv, tree.expr, restype);
                    result = tree;
                } else {
                    result = tree;
                }

            }
        }

        JCBlock trans_block = new LambdaBodyTranslator().translate(block);
        if (completeNormally && isTarget_Void) {
            //there's no return statement and the lambda (possibly inferred)
            //return type is java.lang.Void; emit a synthetic return statement
            trans_block.stats = trans_block.stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
        }
        return trans_block;
    }

    /**
     * Create new synthetic method with given flags, name, type, owner
     */
    private MethodSymbol makeSyntheticMethod(long flags, Name name, Type type, Symbol owner) {
        return new MethodSymbol(flags | SYNTHETIC, name, type, owner);
    }

    /**
     * Create new synthetic variable with given flags, name, type, owner
     */
    private VarSymbol makeSyntheticVar(long flags, String name, Type type, Symbol owner) {
        return makeSyntheticVar(flags, names.fromString(name), type, owner);
    }

    /**
     * Create new synthetic variable with given flags, name, type, owner
     */
    private VarSymbol makeSyntheticVar(long flags, Name name, Type type, Symbol owner) {
        return new VarSymbol(flags | SYNTHETIC, name, type, owner);
    }

    /**
     * Set varargsElement field on a given tree (must be either a new class tree
     * or a method call tree)
     */
    private void setVarargsIfNeeded(JCTree tree, Type varargsElement) {
        if (varargsElement != null) {
            switch (tree.getTag()) {
                case APPLY: ((JCMethodInvocation)tree).varargsElement = varargsElement; break;
                case NEWCLASS: ((JCNewClass)tree).varargsElement = varargsElement; break;
                default: throw new AssertionError();
            }
        }
    }

    /**
     * Convert method/constructor arguments by inserting appropriate cast
     * as required by type-erasure - this is needed when bridging a lambda/method
     * reference, as the bridged signature might require downcast to be compatible
     * with the generated signature.
     */
    private List<JCExpression> convertArgs(Symbol meth, List<JCExpression> args, Type varargsElement) {
       Assert.check(meth.kind == Kinds.MTH);
       List<Type> formals = types.erasure(meth.type).getParameterTypes();
       if (varargsElement != null) {
           Assert.check((meth.flags() & VARARGS) != 0);
       }
       return transTypes.translateArgs(args, formals, varargsElement, attrEnv);
    }

    // </editor-fold>

    /**
     * Generate an adapter method "bridge" for a method reference which cannot
     * be used directly.
     */
    private class MemberReferenceBridger {

        private final JCMemberReference tree;
        private final ReferenceTranslationContext localContext;
        private final ListBuffer<JCExpression> args = ListBuffer.lb();
        private final ListBuffer<JCVariableDecl> params = ListBuffer.lb();

        MemberReferenceBridger(JCMemberReference tree, ReferenceTranslationContext localContext) {
            this.tree = tree;
            this.localContext = localContext;
        }

        /**
         * Generate the bridge
         */
        JCMethodDecl bridge() {
            int prevPos = make.pos;
            try {
                make.at(tree);
                Type samDesc = localContext.bridgedRefSig();
                List<Type> samPTypes = samDesc.getParameterTypes();

                //an extra argument is prepended to the signature of the bridge in case
                //the member reference is an instance method reference (in which case
                //the receiver expression is passed to the bridge itself).
                Type recType = null;
                switch (tree.kind) {
                    case IMPLICIT_INNER:
                        recType = tree.sym.owner.type.getEnclosingType();
                        break;
                    case BOUND:
                        recType = tree.getQualifierExpression().type;
                        break;
                    case UNBOUND:
                        recType = samPTypes.head;
                        samPTypes = samPTypes.tail;
                        break;
                }

                //generate the parameter list for the bridged member reference - the
                //bridge signature will match the signature of the target sam descriptor

                VarSymbol rcvr = (recType == null)
                        ? null
                        : addParameter("rec$", recType, false);

                List<Type> refPTypes = tree.sym.type.getParameterTypes();
                int refSize = refPTypes.size();
                int samSize = samPTypes.size();
                int last = localContext.needsVarArgsConversion() ? refSize - 1 : refSize;   // Last parameter to copy from referenced method

                List<Type> l = refPTypes;
                // Use parameter types of the referenced method, excluding final var args
                for (int i = 0; l.nonEmpty() && i < last; ++i) {
                    addParameter("x$" + i, l.head, true);
                    l = l.tail;
                }
                // Flatten out the var args
                for (int i = last; i < samSize; ++i) {
                    addParameter("xva$" + i, tree.varargsElement, true);
                }

                //generate the bridge method declaration
                JCMethodDecl bridgeDecl = make.MethodDef(make.Modifiers(localContext.bridgeSym.flags()),
                        localContext.bridgeSym.name,
                        make.QualIdent(samDesc.getReturnType().tsym),
                        List.<JCTypeParameter>nil(),
                        params.toList(),
                        tree.sym.type.getThrownTypes() == null
                        ? List.<JCExpression>nil()
                        : make.Types(tree.sym.type.getThrownTypes()),
                        null,
                        null);
                bridgeDecl.sym = (MethodSymbol) localContext.bridgeSym;
                bridgeDecl.type = localContext.bridgeSym.type = types.createMethodTypeWithParameters(samDesc, TreeInfo.types(params.toList()));

                //bridge method body generation - this can be either a method call or a
                //new instance creation expression, depending on the member reference kind
                JCExpression bridgeExpr = (tree.getMode() == ReferenceMode.INVOKE)
                        ? bridgeExpressionInvoke(rcvr)
                        : bridgeExpressionNew();

                //the body is either a return expression containing a method call,
                //or the method call itself, depending on whether the return type of
                //the bridge is non-void/void.
                bridgeDecl.body = makeLambdaExpressionBody(bridgeExpr, bridgeDecl);

                return bridgeDecl;
            } finally {
                make.at(prevPos);
            }
        }

        /**
         * determine the receiver of the bridged method call - the receiver can
         * be either the synthetic receiver parameter or a type qualifier; the
         * original qualifier expression is never used here, as it might refer
         * to symbols not available in the static context of the bridge
         */
        private JCExpression bridgeExpressionInvoke(VarSymbol rcvr) {
            JCExpression qualifier =
                    tree.sym.isStatic() ?
                        make.Type(tree.sym.owner.type) :
                        (rcvr != null) ?
                            make.Ident(rcvr) :
                            tree.getQualifierExpression();

            //create the qualifier expression
            JCFieldAccess select = make.Select(qualifier, tree.sym.name);
            select.sym = tree.sym;
            select.type = tree.sym.erasure(types);

            //create the method call expression
            JCExpression apply = make.Apply(List.<JCExpression>nil(), select,
                    convertArgs(tree.sym, args.toList(), tree.varargsElement)).setType(tree.sym.erasure(types).getReturnType());

            apply = transTypes.coerce(apply, localContext.generatedRefSig().getReturnType());
            setVarargsIfNeeded(apply, tree.varargsElement);
            return apply;
        }

        /**
         * the enclosing expression is either 'null' (no enclosing type) or set
         * to the first bridge synthetic parameter
         */
        private JCExpression bridgeExpressionNew() {
            if (tree.kind == ReferenceKind.ARRAY_CTOR) {
                //create the array creation expression
                JCNewArray newArr = make.NewArray(make.Type(types.elemtype(tree.getQualifierExpression().type)),
                        List.of(make.Ident(params.first())),
                        null);
                newArr.type = tree.getQualifierExpression().type;
                return newArr;
            } else {
                JCExpression encl = null;
                switch (tree.kind) {
                    case UNBOUND:
                    case IMPLICIT_INNER:
                        encl = make.Ident(params.first());
                }

                //create the instance creation expression
                JCNewClass newClass = make.NewClass(encl,
                        List.<JCExpression>nil(),
                        make.Type(tree.getQualifierExpression().type),
                        convertArgs(tree.sym, args.toList(), tree.varargsElement),
                        null);
                newClass.constructor = tree.sym;
                newClass.constructorType = tree.sym.erasure(types);
                newClass.type = tree.getQualifierExpression().type;
                setVarargsIfNeeded(newClass, tree.varargsElement);
                return newClass;
            }
        }

        private VarSymbol addParameter(String name, Type p, boolean genArg) {
            VarSymbol vsym = new VarSymbol(0, names.fromString(name), p, localContext.bridgeSym);
            params.append(make.VarDef(vsym, null));
            if (genArg) {
                args.append(make.Ident(vsym));
            }
            return vsym;
        }
    }

    /**
     * Bridges a member reference - this is needed when:
     * * Var args in the referenced method need to be flattened away
     * * super is used
     */
    private void bridgeMemberReference(JCMemberReference tree, ReferenceTranslationContext localContext) {
        JCMethodDecl bridgeDecl = (new MemberReferenceBridger(tree, localContext).bridge());
        translatedMethodList = translatedMethodList.prepend(bridgeDecl);
    }

    /**
     * Generate an indy method call to the meta factory
     */
    private JCExpression makeMetaFactoryIndyCall(JCFunctionalExpression tree, int refKind, Symbol refSym, List<JCExpression> indy_args) {
        //determine the static bsm args
        Type mtype = types.erasure(tree.descriptorType);
        MethodSymbol samSym = (MethodSymbol) types.findDescriptorSymbol(tree.type.tsym);
        List<Object> staticArgs = List.<Object>of(
                new Pool.MethodHandle(ClassFile.REF_invokeInterface, types.findDescriptorSymbol(tree.type.tsym), types),
                new Pool.MethodHandle(refKind, refSym, types),
                new MethodType(mtype.getParameterTypes(),
                        mtype.getReturnType(),
                        mtype.getThrownTypes(),
                        syms.methodClass));

        //computed indy arg types
        ListBuffer<Type> indy_args_types = ListBuffer.lb();
        for (JCExpression arg : indy_args) {
            indy_args_types.append(arg.type);
        }

        //finally, compute the type of the indy call
        MethodType indyType = new MethodType(indy_args_types.toList(),
                tree.type,
                List.<Type>nil(),
                syms.methodClass);

        return makeIndyCall(tree, syms.lambdaMetafactory, names.metaFactory, staticArgs, indyType, indy_args);
    }

    /**
     * Generate an indy method call with given name, type and static bootstrap
     * arguments types
     */
    private JCExpression makeIndyCall(DiagnosticPosition pos, Type site, Name bsmName, List<Object> staticArgs, MethodType indyType, List<JCExpression> indyArgs) {
        int prevPos = make.pos;
        try {
            make.at(pos);
            List<Type> bsm_staticArgs = List.of(syms.methodHandleLookupType,
                    syms.stringType,
                    syms.methodTypeType).appendList(bsmStaticArgToTypes(staticArgs));

            Symbol bsm = rs.resolveInternalMethod(pos, attrEnv, site,
                    bsmName, bsm_staticArgs, List.<Type>nil());

            DynamicMethodSymbol dynSym =
                    new DynamicMethodSymbol(names.lambda,
                                            syms.noSymbol,
                                            bsm.isStatic() ? ClassFile.REF_invokeStatic : ClassFile.REF_invokeVirtual,
                                            (MethodSymbol)bsm,
                                            indyType,
                                            staticArgs.toArray());

            JCFieldAccess qualifier = make.Select(make.QualIdent(site.tsym), bsmName);
            qualifier.sym = dynSym;
            qualifier.type = indyType.getReturnType();

            JCMethodInvocation proxyCall = make.Apply(List.<JCExpression>nil(), qualifier, indyArgs);
            proxyCall.type = indyType.getReturnType();
            return proxyCall;
        } finally {
            make.at(prevPos);
        }
    }
    //where
    private List<Type> bsmStaticArgToTypes(List<Object> args) {
        ListBuffer<Type> argtypes = ListBuffer.lb();
        for (Object arg : args) {
            argtypes.append(bsmStaticArgToType(arg));
        }
        return argtypes.toList();
    }

    private Type bsmStaticArgToType(Object arg) {
        Assert.checkNonNull(arg);
        if (arg instanceof ClassSymbol) {
            return syms.classType;
        } else if (arg instanceof Integer) {
            return syms.intType;
        } else if (arg instanceof Long) {
            return syms.longType;
        } else if (arg instanceof Float) {
            return syms.floatType;
        } else if (arg instanceof Double) {
            return syms.doubleType;
        } else if (arg instanceof String) {
            return syms.stringType;
        } else if (arg instanceof Pool.MethodHandle) {
            return syms.methodHandleType;
        } else if (arg instanceof MethodType) {
            return syms.methodTypeType;
        } else {
            Assert.error("bad static arg " + arg.getClass());
            return null;
        }
    }

    /**
     * Get the opcode associated with this method reference
     */
    private int referenceKind(Symbol refSym) {
        if (refSym.isConstructor()) {
            return ClassFile.REF_newInvokeSpecial;
        } else {
            if (refSym.isStatic()) {
                return ClassFile.REF_invokeStatic;
            } else if (refSym.enclClass().isInterface()) {
                return ClassFile.REF_invokeInterface;
            } else {
                return ClassFile.REF_invokeVirtual;
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Lambda/reference analyzer">\
    /**
     * This visitor collects information about translation of a lambda expression.
     * More specifically, it keeps track of the enclosing contexts and captured locals
     * accessed by the lambda being translated (as well as other useful info).
     */
    class LambdaAnalyzer extends TreeScanner {

        /** the frame stack - used to reconstruct translation info about enclosing scopes */
        private List<Frame> frameStack;

        /**
         * keep the count of lambda expression (used to generate unambiguous
         * names)
         */
        private int lambdaCount = 0;

        private void analyzeClass(JCClassDecl tree) {
            frameStack = List.nil();
            scan(tree);
        }

        @Override
        public void visitBlock(JCBlock tree) {
            List<Frame> prevStack = frameStack;
            try {
                if (frameStack.nonEmpty() && frameStack.head.tree.hasTag(CLASSDEF)) {
                    frameStack = frameStack.prepend(new Frame(tree));
                }
                super.visitBlock(tree);
            }
            finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            List<Frame> prevStack = frameStack;
            try {
                if (frameStack.nonEmpty() && enclosingLambda() != null) {
                    tree.sym.owner = owner();
                    LambdaTranslationContext lambdaContext = (LambdaTranslationContext)contextMap.get(enclosingLambda());
                    Type encl = lambdaContext.enclosingType();
                    if (encl.hasTag(NONE)) {
                        //if the translated lambda body occurs in a static context,
                        //any class declaration within it must be made static
                        tree.sym.flags_field |= STATIC;
                        ((ClassType)tree.sym.type).setEnclosingType(Type.noType);
                    } else {
                        //if the translated lambda body is in an instance context
                        //the enclosing type of any class declaration within it
                        //must be updated to point to the new enclosing type (if any)
                        ((ClassType)tree.sym.type).setEnclosingType(encl);
                    }
                }
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitClassDef(tree);
            }
            finally {
                frameStack = prevStack;
            }
            if (!tree.sym.isStatic() && frameStack.nonEmpty() && enclosingLambda() != null) {
                // Any (non-static) class defined within a lambda is an implicit 'this' reference
                // because its constructor will reference the enclosing class
                ((LambdaTranslationContext) context()).addSymbol(tree.sym.type.getEnclosingType().tsym, CAPTURED_THIS);
            }
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (context() == null || !lambdaIdentSymbolFilter(tree.sym)) {
                super.visitIdent(tree);
            } else {
                if (tree.sym.kind == VAR &&
                        tree.sym.owner.kind == MTH &&
                        tree.type.constValue() == null) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null) {
                        if (localContext.tree.getTag() == LAMBDA) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            ((LambdaTranslationContext)localContext).addSymbol(tree.sym, CAPTURED_VAR);
                        }
                        localContext = localContext.prev;
                    }
                } else if (tree.sym.owner.kind == TYP) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null) {
                        if (localContext.tree.hasTag(LAMBDA)) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            switch (block.getTag()) {
                                case CLASSDEF:
                                    JCClassDecl cdecl = (JCClassDecl)block;
                                    ((LambdaTranslationContext)localContext).addSymbol(cdecl.sym, CAPTURED_THIS);
                                    break;
                                default:
                                    Assert.error("bad block kind");
                            }
                        }
                        localContext = localContext.prev;
                    }
                }
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            List<Frame> prevStack = frameStack;
            try {
                LambdaTranslationContext context = (LambdaTranslationContext)makeLambdaContext(tree);
                frameStack = frameStack.prepend(new Frame(tree));
                for (JCVariableDecl param : tree.params) {
                    context.addSymbol(param.sym, PARAM);
                    frameStack.head.addLocal(param.sym);
                }
                contextMap.put(tree, context);
                scan(tree.body);
                context.complete();
            }
            finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            List<Frame> prevStack = frameStack;
            try {
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitMethodDef(tree);
            }
            finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            if (lambdaNewClassFilter(context(), tree)) {
                ((LambdaTranslationContext) context()).addSymbol(tree.type.getEnclosingType().tsym, CAPTURED_THIS);
            }
            super.visitNewClass(tree);
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            scan(tree.getQualifierExpression());
            contextMap.put(tree, makeReferenceContext(tree));
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            if (context() != null && lambdaSelectSymbolFilter(tree.sym)) {
                TranslationContext<?> localContext = context();
                while (localContext != null) {
                    if (localContext.tree.hasTag(LAMBDA)) {
                        JCClassDecl clazz = (JCClassDecl)capturedDecl(localContext.depth, tree.sym);
                        if (clazz == null) break;
                        ((LambdaTranslationContext)localContext).addSymbol(clazz.sym, CAPTURED_THIS);
                    }
                    localContext = localContext.prev;
                }
                scan(tree.selected);
            } else {
                super.visitSelect(tree);
            }
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            if (frameStack.head.tree.hasTag(LAMBDA)) {
                ((LambdaTranslationContext)context()).addSymbol(tree.sym, LOCAL_VAR);
            }
            List<Frame> prevStack = frameStack;
            try {
                if (tree.sym.owner.kind == MTH) {
                    frameStack.head.addLocal(tree.sym);
                }
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitVarDef(tree);
            }
            finally {
                frameStack = prevStack;
            }
        }

        private Name lambdaName() {
            return names.lambda.append(names.fromString("$" + lambdaCount++));
        }

        /**
         * Return a valid owner given the current declaration stack
         * (required to skip synthetic lambda symbols)
         */
        private Symbol owner() {
            return owner(false);
        }

        @SuppressWarnings("fallthrough")
        private Symbol owner(boolean skipLambda) {
            List<Frame> frameStack2 = frameStack;
            while (frameStack2.nonEmpty()) {
                switch (frameStack2.head.tree.getTag()) {
                    case VARDEF:
                        if (((JCVariableDecl)frameStack2.head.tree).sym.isLocal()) {
                            frameStack2 = frameStack2.tail;
                            break;
                        }
                        JCClassDecl cdecl = (JCClassDecl)frameStack2.tail.head.tree;
                        return makeSyntheticMethod(((JCVariableDecl)frameStack2.head.tree).sym.flags() & STATIC, names.empty, null, cdecl.sym);
                    case BLOCK:
                        JCClassDecl cdecl2 = (JCClassDecl)frameStack2.tail.head.tree;
                        return makeSyntheticMethod(((JCBlock)frameStack2.head.tree).flags & STATIC | Flags.BLOCK, names.empty, null, cdecl2.sym);
                    case CLASSDEF:
                        return ((JCClassDecl)frameStack2.head.tree).sym;
                    case METHODDEF:
                        return ((JCMethodDecl)frameStack2.head.tree).sym;
                    case LAMBDA:
                        if (!skipLambda)
                            return ((LambdaTranslationContext)contextMap.get(frameStack2.head.tree)).translatedSym;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            Assert.error();
            return null;
        }

        private JCTree enclosingLambda() {
            List<Frame> frameStack2 = frameStack;
            while (frameStack2.nonEmpty()) {
                switch (frameStack2.head.tree.getTag()) {
                    case CLASSDEF:
                    case METHODDEF:
                        return null;
                    case LAMBDA:
                        return frameStack2.head.tree;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            Assert.error();
            return null;
        }

        /**
         * Return the declaration corresponding to a symbol in the enclosing
         * scope; the depth parameter is used to filter out symbols defined
         * in nested scopes (which do not need to undergo capture).
         */
        private JCTree capturedDecl(int depth, Symbol sym) {
            int currentDepth = frameStack.size() - 1;
            for (Frame block : frameStack) {
                switch (block.tree.getTag()) {
                    case CLASSDEF:
                        ClassSymbol clazz = ((JCClassDecl)block.tree).sym;
                        if (sym.isMemberOf(clazz, types)) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    case VARDEF:
                        if (((JCVariableDecl)block.tree).sym == sym &&
                                sym.owner.kind == MTH) { //only locals are captured
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    case BLOCK:
                    case METHODDEF:
                    case LAMBDA:
                        if (block.locals != null && block.locals.contains(sym)) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    default:
                        Assert.error("bad decl kind " + block.tree.getTag());
                }
                currentDepth--;
            }
            return null;
        }

        private TranslationContext<?> context() {
            for (Frame frame : frameStack) {
                TranslationContext<?> context = contextMap.get(frame.tree);
                if (context != null) {
                    return context;
                }
            }
            return null;
        }

        /**
         *  This is used to filter out those identifiers that needs to be adjusted
         *  when translating away lambda expressions
         */
        private boolean lambdaIdentSymbolFilter(Symbol sym) {
            return (sym.kind == VAR || sym.kind == MTH)
                    && !sym.isStatic()
                    && sym.name != names.init;
        }

        private boolean lambdaSelectSymbolFilter(Symbol sym) {
            return (sym.kind == VAR || sym.kind == MTH) &&
                        !sym.isStatic() &&
                        (sym.name == names._this ||
                        sym.name == names._super);
        }

        /**
         * This is used to filter out those new class expressions that need to
         * be qualified with an enclosing tree
         */
        private boolean lambdaNewClassFilter(TranslationContext<?> context, JCNewClass tree) {
            if (context != null
                    && tree.encl == null
                    && tree.def == null
                    && !tree.type.getEnclosingType().hasTag(NONE)) {
                Type encl = tree.type.getEnclosingType();
                Type current = context.owner.enclClass().type;
                while (!current.hasTag(NONE)) {
                    if (current.tsym.isSubClass(encl.tsym, types)) {
                        return true;
                    }
                    current = current.getEnclosingType();
                }
                return false;
            } else {
                return false;
            }
        }

        private TranslationContext<JCLambda> makeLambdaContext(JCLambda tree) {
            return new LambdaTranslationContext(tree);
        }

        private TranslationContext<JCMemberReference> makeReferenceContext(JCMemberReference tree) {
            return new ReferenceTranslationContext(tree);
        }

        private class Frame {
            final JCTree tree;
            List<Symbol> locals;

            public Frame(JCTree tree) {
                this.tree = tree;
            }

            void addLocal(Symbol sym) {
                if (locals == null) {
                    locals = List.nil();
                }
                locals = locals.prepend(sym);
            }
        }

        /**
         * This class is used to store important information regarding translation of
         * lambda expression/method references (see subclasses).
         */
        private abstract class TranslationContext<T extends JCFunctionalExpression> {

            /** the underlying (untranslated) tree */
            T tree;

            /** points to the adjusted enclosing scope in which this lambda/mref expression occurs */
            Symbol owner;

            /** the depth of this lambda expression in the frame stack */
            int depth;

            /** the enclosing translation context (set for nested lambdas/mref) */
            TranslationContext<?> prev;

            TranslationContext(T tree) {
                this.tree = tree;
                this.owner = owner();
                this.depth = frameStack.size() - 1;
                this.prev = context();
            }
        }

        /**
         * This class retains all the useful information about a lambda expression;
         * the contents of this class are filled by the LambdaAnalyzer visitor,
         * and the used by the main translation routines in order to adjust references
         * to captured locals/members, etc.
         */
        private class LambdaTranslationContext extends TranslationContext<JCLambda> {

            /** variable in the enclosing context to which this lambda is assigned */
            Symbol self;

            /** map from original to translated lambda parameters */
            Map<Symbol, Symbol> lambdaParams = new LinkedHashMap<Symbol, Symbol>();

            /** map from original to translated lambda locals */
            Map<Symbol, Symbol> lambdaLocals = new LinkedHashMap<Symbol, Symbol>();

            /** map from variables in enclosing scope to translated synthetic parameters */
            Map<Symbol, Symbol> capturedLocals  = new LinkedHashMap<Symbol, Symbol>();

            /** map from class symbols to translated synthetic parameters (for captured member access) */
            Map<Symbol, Symbol> capturedThis = new LinkedHashMap<Symbol, Symbol>();

            /** the synthetic symbol for the method hoisting the translated lambda */
            Symbol translatedSym;

            List<JCVariableDecl> syntheticParams;

            LambdaTranslationContext(JCLambda tree) {
                super(tree);
                Frame frame = frameStack.head;
                if (frame.tree.hasTag(VARDEF)) {
                    self = ((JCVariableDecl)frame.tree).sym;
                }
                this.translatedSym = makeSyntheticMethod(0, lambdaName(), null, owner.enclClass());
            }

            /**
             * Translate a symbol of a given kind into something suitable for the
             * synthetic lambda body
             */
            Symbol translate(String name, Symbol sym, LambdaSymbolKind skind) {
                if (skind == CAPTURED_THIS) {
                    return sym;  // self represented
                } else {
                    return makeSyntheticVar(FINAL, name, types.erasure(sym.type), translatedSym);
                }
            }

            void addSymbol(Symbol sym, LambdaSymbolKind skind) {
                Map<Symbol, Symbol> transMap = null;
                String preferredName;
                switch (skind) {
                    case CAPTURED_THIS:
                        transMap = capturedThis;
                        preferredName = "encl$" + capturedThis.size();
                        break;
                    case CAPTURED_VAR:
                        transMap = capturedLocals;
                        preferredName = "cap$" + capturedLocals.size();
                        break;
                    case LOCAL_VAR:
                        transMap = lambdaLocals;
                        preferredName = sym.name.toString();
                        break;
                    case PARAM:
                        transMap = lambdaParams;
                        preferredName = sym.name.toString();
                        break;
                    default: throw new AssertionError();
                }
                if (!transMap.containsKey(sym)) {
                    transMap.put(sym, translate(preferredName, sym, skind));
                }
            }

            Map<Symbol, Symbol> getSymbolMap(LambdaSymbolKind... skinds) {
                LinkedHashMap<Symbol, Symbol> translationMap = new LinkedHashMap<Symbol, Symbol>();
                for (LambdaSymbolKind skind : skinds) {
                    switch (skind) {
                        case CAPTURED_THIS:
                            translationMap.putAll(capturedThis);
                            break;
                        case CAPTURED_VAR:
                            translationMap.putAll(capturedLocals);
                            break;
                        case LOCAL_VAR:
                            translationMap.putAll(lambdaLocals);
                            break;
                        case PARAM:
                            translationMap.putAll(lambdaParams);
                            break;
                        default: throw new AssertionError();
                    }
                }
                return translationMap;
            }

            /**
             * The translatedSym is not complete/accurate until the analysis is
             * finished.  Once the analysis is finished, the translatedSym is
             * "completed" -- updated with type information, access modifiers,
             * and full parameter list.
             */
            void complete() {
                if (syntheticParams != null) {
                    return;
                }
                boolean inInterface = translatedSym.owner.isInterface();
                boolean thisReferenced = !getSymbolMap(CAPTURED_THIS).isEmpty();
                boolean needInstance = thisReferenced || inInterface;

                // If instance access isn't needed, make it static
                // Interface methods much be public default methods, otherwise make it private
                translatedSym.flags_field = SYNTHETIC | (needInstance? 0 : STATIC) | (inInterface? PUBLIC | DEFAULT : PRIVATE);

                //compute synthetic params
                ListBuffer<JCVariableDecl> params = ListBuffer.lb();

                // The signature of the method is augmented with the following
                // synthetic parameters:
                //
                // 1) reference to enclosing contexts captured by the lambda expression
                // 2) enclosing locals captured by the lambda expression
                for (Symbol thisSym : getSymbolMap(CAPTURED_VAR, PARAM).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                }

                syntheticParams = params.toList();

                //prepend synthetic args to translated lambda method signature
                translatedSym.type = (MethodType) types.createMethodTypeWithParameters(
                        (MethodType) generatedLambdaSig(),
                        TreeInfo.types(syntheticParams));
            }

            Type enclosingType() {
                return owner.isStatic() ?
                        Type.noType :
                        owner.enclClass().type;
            }

            Type generatedLambdaSig() {
                return types.erasure(tree.descriptorType);
            }
        }

        /**
         * This class retains all the useful information about a method reference;
         * the contents of this class are filled by the LambdaAnalyzer visitor,
         * and the used by the main translation routines in order to adjust method
         * references (i.e. in case a bridge is needed)
         */
        private class ReferenceTranslationContext extends TranslationContext<JCMemberReference> {

            final boolean isSuper;
            final Symbol bridgeSym;

            ReferenceTranslationContext(JCMemberReference tree) {
                super(tree);
                this.isSuper = tree.hasKind(ReferenceKind.SUPER);
                this.bridgeSym = needsBridge()
                        ? makeSyntheticMethod(isSuper ? 0 : STATIC,
                                              lambdaName().append(names.fromString("$bridge")), null,
                                              owner.enclClass())
                        : null;
            }

            /**
             * Get the opcode associated with this method reference
             */
            int referenceKind() {
                return LambdaToMethod.this.referenceKind(needsBridge() ? bridgeSym : tree.sym);
            }

            boolean needsVarArgsConversion() {
                return tree.varargsElement != null;
            }

            /**
             * @return Is this an array operation like clone()
             */
            boolean isArrayOp() {
                return tree.sym.owner == syms.arrayClass;
            }

            /**
             * Does this reference needs a bridge (i.e. var args need to be
             * expanded or "super" is used)
             */
            final boolean needsBridge() {
                return isSuper || needsVarArgsConversion() || isArrayOp();
            }

            Type generatedRefSig() {
                return types.erasure(tree.sym.type);
            }

            Type bridgedRefSig() {
                return types.erasure(types.findDescriptorSymbol(tree.targets.head).type);
            }
        }
    }
    // </editor-fold>

    enum LambdaSymbolKind {
        CAPTURED_VAR,
        CAPTURED_THIS,
        LOCAL_VAR,
        PARAM;
    }
}
