/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodHandleSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.Types.SignatureGenerator.InvalidSignatureException;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.resources.CompilerProperties.Notes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCFunctionalExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.InvalidUtfException;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import javax.lang.model.element.ElementKind;
import java.lang.invoke.LambdaMetafactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.sun.tools.javac.code.Flags.ABSTRACT;
import static com.sun.tools.javac.code.Flags.BLOCK;
import static com.sun.tools.javac.code.Flags.DEFAULT;
import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.INTERFACE;
import static com.sun.tools.javac.code.Flags.LAMBDA_METHOD;
import static com.sun.tools.javac.code.Flags.LOCAL_CAPTURE_FIELD;
import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.STRICTFP;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.Kinds.Kind.MTH;
import static com.sun.tools.javac.code.Kinds.Kind.TYP;
import static com.sun.tools.javac.code.Kinds.Kind.VAR;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.code.TypeTag.VOID;

/**
 * This pass desugars lambda expressions into static methods
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LambdaToMethod extends TreeTranslator {

    private final Attr attr;
    private final JCDiagnostic.Factory diags;
    private final Log log;
    private final Lower lower;
    private final Names names;
    private final Symtab syms;
    private final Resolve rs;
    private final Operators operators;
    private TreeMaker make;
    private final Types types;
    private final TransTypes transTypes;
    private Env<AttrContext> attrEnv;

    /** info about the current class being processed */
    private KlassInfo kInfo;

    /** translation context of the current lambda expression */
    private LambdaTranslationContext lambdaContext;

    /** the variable whose initializer is pending */
    private VarSymbol pendingVar;

    /** dump statistics about lambda code generation */
    private final boolean dumpLambdaToMethodStats;

    /** dump statistics about lambda deserialization code generation */
    private final boolean dumpLambdaDeserializationStats;

    /** force serializable representation, for stress testing **/
    private final boolean forceSerializable;

    /** true if line or local variable debug info has been requested */
    private final boolean debugLinesOrVars;

    /** dump statistics about lambda method deduplication */
    private final boolean verboseDeduplication;

    /** deduplicate lambda implementation methods */
    private final boolean deduplicateLambdas;

    /** Flag for alternate metafactories indicating the lambda object is intended to be serializable */
    public static final int FLAG_SERIALIZABLE = LambdaMetafactory.FLAG_SERIALIZABLE;

    /** Flag for alternate metafactories indicating the lambda object has multiple targets */
    public static final int FLAG_MARKERS = LambdaMetafactory.FLAG_MARKERS;

    /** Flag for alternate metafactories indicating the lambda object requires multiple bridges */
    public static final int FLAG_BRIDGES = LambdaMetafactory.FLAG_BRIDGES;

    // <editor-fold defaultstate="collapsed" desc="Instantiating">
    protected static final Context.Key<LambdaToMethod> unlambdaKey = new Context.Key<>();

    public static LambdaToMethod instance(Context context) {
        LambdaToMethod instance = context.get(unlambdaKey);
        if (instance == null) {
            instance = new LambdaToMethod(context);
        }
        return instance;
    }
    private LambdaToMethod(Context context) {
        context.put(unlambdaKey, this);
        diags = JCDiagnostic.Factory.instance(context);
        log = Log.instance(context);
        lower = Lower.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        operators = Operators.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        transTypes = TransTypes.instance(context);
        Options options = Options.instance(context);
        dumpLambdaToMethodStats = options.isSet("debug.dumpLambdaToMethodStats");
        dumpLambdaDeserializationStats = options.isSet("debug.dumpLambdaDeserializationStats");
        attr = Attr.instance(context);
        forceSerializable = options.isSet("forceSerializable");
        boolean lineDebugInfo =
                options.isUnset(Option.G_CUSTOM) ||
                        options.isSet(Option.G_CUSTOM, "lines");
        boolean varDebugInfo =
                options.isUnset(Option.G_CUSTOM)
                        ? options.isSet(Option.G)
                        : options.isSet(Option.G_CUSTOM, "vars");
        debugLinesOrVars = lineDebugInfo || varDebugInfo;
        verboseDeduplication = options.isSet("debug.dumpLambdaToMethodDeduplication");
        deduplicateLambdas = options.getBoolean("deduplicateLambdas", true);
    }
    // </editor-fold>

    class DedupedLambda {
        private final MethodSymbol symbol;
        private final JCTree tree;

        private int hashCode;

        DedupedLambda(MethodSymbol symbol, JCTree tree) {
            this.symbol = symbol;
            this.tree = tree;
        }

        @Override
        public int hashCode() {
            int hashCode = this.hashCode;
            if (hashCode == 0) {
                this.hashCode = hashCode = TreeHasher.hash(types, tree, symbol.params());
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof DedupedLambda dedupedLambda)
                    && types.isSameType(symbol.asType(), dedupedLambda.symbol.asType())
                    && new TreeDiffer(types, symbol.params(), dedupedLambda.symbol.params()).scan(tree, dedupedLambda.tree);
        }
    }

    private class KlassInfo {

        /**
         * list of methods to append
         */
        private ListBuffer<JCTree> appendedMethodList = new ListBuffer<>();

        private final Map<DedupedLambda, DedupedLambda> dedupedLambdas = new HashMap<>();

        private final Map<Object, DynamicMethodSymbol> dynMethSyms = new HashMap<>();

        /**
         * list of deserialization cases
         */
        private final Map<String, ListBuffer<JCStatement>> deserializeCases = new HashMap<>();

        /**
         * deserialize method symbol
         */
        private final MethodSymbol deserMethodSym;

        /**
         * deserialize method parameter symbol
         */
        private final VarSymbol deserParamSym;

        private final JCClassDecl clazz;

        private final Map<String, Integer> syntheticNames = new HashMap<>();

        private KlassInfo(JCClassDecl clazz) {
            this.clazz = clazz;
            MethodType type = new MethodType(List.of(syms.serializedLambdaType), syms.objectType,
                    List.nil(), syms.methodClass);
            deserMethodSym = makePrivateSyntheticMethod(STATIC, names.deserializeLambda, type, clazz.sym);
            deserParamSym = new VarSymbol(FINAL, names.fromString("lambda"),
                    syms.serializedLambdaType, deserMethodSym);
        }

        private void addMethod(JCTree decl) {
            appendedMethodList = appendedMethodList.prepend(decl);
        }

        int syntheticNameIndex(StringBuilder buf, int start) {
            String temp = buf.toString();
            Integer count = syntheticNames.get(temp);
            if (count == null) {
                count = start;
            }
            syntheticNames.put(temp, count + 1);
            return count;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="visitor methods">
    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        this.make = make;
        this.attrEnv = env;
        return translate(cdef);
    }

    /**
     * Visit a class.
     * Maintain the translatedMethodList across nested classes.
     * Append the translatedMethodList to the class after it is translated.
     */
    @Override
    public void visitClassDef(JCClassDecl tree) {
        KlassInfo prevKlassInfo = kInfo;
        DiagnosticSource prevSource = log.currentSource();
        LambdaTranslationContext prevLambdaContext = lambdaContext;
        VarSymbol prevPendingVar = pendingVar;
        try {
            kInfo = new KlassInfo(tree);
            log.useSource(tree.sym.sourcefile);
            lambdaContext = null;
            pendingVar = null;
            super.visitClassDef(tree);
            if (prevLambdaContext != null) {
                tree.sym.owner = prevLambdaContext.translatedSym;
            }
            if (!kInfo.deserializeCases.isEmpty()) {
                int prevPos = make.pos;
                try {
                    make.at(tree);
                    kInfo.addMethod(makeDeserializeMethod());
                } finally {
                    make.at(prevPos);
                }
            }
            //add all translated instance methods here
            List<JCTree> newMethods = kInfo.appendedMethodList.toList();
            tree.defs = tree.defs.appendList(newMethods);
            for (JCTree lambda : newMethods) {
                tree.sym.members().enter(((JCMethodDecl)lambda).sym);
            }
            result = tree;
        } finally {
            kInfo = prevKlassInfo;
            log.useSource(prevSource.getFile());
            lambdaContext = prevLambdaContext;
            pendingVar = prevPendingVar;
        }
    }

    /**
     * Translate a lambda into a method to be inserted into the class.
     * Then replace the lambda site with an invokedynamic call of to lambda
     * meta-factory, which will use the lambda method.
     */
    @Override
    public void visitLambda(JCLambda tree) {
        LambdaTranslationContext localContext = new LambdaTranslationContext(tree);
        MethodSymbol sym = localContext.translatedSym;
        MethodType lambdaType = (MethodType) sym.type;

        {   /* Type annotation management: Based on where the lambda features, type annotations that
               are interior to it, may at this point be attached to the enclosing method, or the first
               constructor in the class, or in the enclosing class symbol or in the field whose
               initializer is the lambda. In any event, gather up the annotations that belong to the
               lambda and attach it to the implementation method.
            */

            Symbol owner = tree.owner;
            apportionTypeAnnotations(tree,
                    owner::getRawTypeAttributes,
                    owner::setTypeAttributes,
                    sym::setTypeAttributes);

            final long ownerFlags = owner.flags();
            if ((ownerFlags & Flags.BLOCK) != 0) {
                ClassSymbol cs = (ClassSymbol) owner.owner;
                boolean isStaticInit = (ownerFlags & Flags.STATIC) != 0;
                apportionTypeAnnotations(tree,
                        isStaticInit ? cs::getClassInitTypeAttributes : cs::getInitTypeAttributes,
                        isStaticInit ? cs::setClassInitTypeAttributes : cs::setInitTypeAttributes,
                        sym::appendUniqueTypeAttributes);
            }

            if (pendingVar != null && pendingVar.getKind() == ElementKind.FIELD) {
                apportionTypeAnnotations(tree,
                        pendingVar::getRawTypeAttributes,
                        pendingVar::setTypeAttributes,
                        sym::appendUniqueTypeAttributes);
            }
        }

        //create the method declaration hoisting the lambda body
        JCMethodDecl lambdaDecl = make.MethodDef(make.Modifiers(sym.flags_field),
                sym.name,
                make.QualIdent(lambdaType.getReturnType().tsym),
                List.nil(),
                localContext.syntheticParams,
                lambdaType.getThrownTypes() == null ?
                        List.nil() :
                        make.Types(lambdaType.getThrownTypes()),
                null,
                null);
        lambdaDecl.sym = sym;
        lambdaDecl.type = lambdaType;

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

        ListBuffer<JCExpression> syntheticInits = new ListBuffer<>();

        if (!sym.isStatic()) {
            syntheticInits.append(makeThis(
                    sym.owner.enclClass().asType(),
                    tree.owner.enclClass()));
        }

        //add captured locals
        for (Symbol fv : localContext.capturedVars) {
            JCExpression captured_local = make.Ident(fv).setType(fv.type);
            syntheticInits.append(captured_local);
        }

        //then, determine the arguments to the indy call
        List<JCExpression> indy_args = translate(syntheticInits.toList());

        LambdaTranslationContext prevLambdaContext = lambdaContext;
        try {
            lambdaContext = localContext;
            //translate lambda body
            //As the lambda body is translated, all references to lambda locals,
            //captured variables, enclosing members are adjusted accordingly
            //to refer to the static method parameters (rather than i.e. accessing
            //captured members directly).
            lambdaDecl.body = translate(makeLambdaBody(tree, lambdaDecl));
        } finally {
            lambdaContext = prevLambdaContext;
        }

        boolean dedupe = false;
        if (deduplicateLambdas && !debugLinesOrVars && !isSerializable(tree)) {
            DedupedLambda dedupedLambda = new DedupedLambda(lambdaDecl.sym, lambdaDecl.body);
            DedupedLambda existing = kInfo.dedupedLambdas.putIfAbsent(dedupedLambda, dedupedLambda);
            if (existing != null) {
                sym = existing.symbol;
                dedupe = true;
                if (verboseDeduplication) log.note(tree, Notes.VerboseL2mDeduplicate(sym));
            }
        }
        if (!dedupe) {
            //Add the method to the list of methods to be added to this class.
            kInfo.addMethod(lambdaDecl);
        }

        //convert to an invokedynamic call
        result = makeMetafactoryIndyCall(tree, sym.asHandle(), localContext.translatedSym, indy_args);
    }

    // where
    // Reassign type annotations from the source that should really belong to the lambda
    private void apportionTypeAnnotations(JCLambda tree,
                                          Supplier<List<Attribute.TypeCompound>> source,
                                          Consumer<List<Attribute.TypeCompound>> owner,
                                          Consumer<List<Attribute.TypeCompound>> lambda) {

        ListBuffer<Attribute.TypeCompound> ownerTypeAnnos = new ListBuffer<>();
        ListBuffer<Attribute.TypeCompound> lambdaTypeAnnos = new ListBuffer<>();

        for (Attribute.TypeCompound tc : source.get()) {
            if (tc.hasUnknownPosition()) {
                // Handle container annotations
                tc.tryFixPosition();
            }
            if (tc.position.onLambda == tree) {
                lambdaTypeAnnos.append(tc);
            } else {
                ownerTypeAnnos.append(tc);
            }
        }
        if (lambdaTypeAnnos.nonEmpty()) {
            owner.accept(ownerTypeAnnos.toList());
            lambda.accept(lambdaTypeAnnos.toList());
        }
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
     */
    @Override
    public void visitReference(JCMemberReference tree) {
        //first determine the method symbol to be used to generate the sam instance
        //this is either the method reference symbol, or the bridged reference symbol
        MethodSymbol refSym = (MethodSymbol)tree.sym;

        //the qualifying expression is treated as a special captured arg
        JCExpression init = switch (tree.kind) {
            case IMPLICIT_INNER,    /* Inner :: new */
                 SUPER ->           /* super :: instMethod */
                    makeThis(tree.owner.enclClass().asType(), tree.owner.enclClass());
            case BOUND ->           /* Expr :: instMethod */
                    attr.makeNullCheck(transTypes.coerce(attrEnv, tree.getQualifierExpression(),
                            types.erasure(tree.sym.owner.type)));
            case UNBOUND,           /* Type :: instMethod */
                 STATIC,            /* Type :: staticMethod */
                 TOPLEVEL,          /* Top level :: new */
                 ARRAY_CTOR ->      /* ArrayType :: new */
                    null;
        };

        List<JCExpression> indy_args = (init == null) ?
                List.nil() : translate(List.of(init));

        //build a sam instance using an indy call to the meta-factory
        result = makeMetafactoryIndyCall(tree, refSym.asHandle(), refSym, indy_args);
    }

    /**
     * Translate identifiers within a lambda to the mapped identifier
     */
    @Override
    public void visitIdent(JCIdent tree) {
        if (lambdaContext == null) {
            super.visitIdent(tree);
        } else {
            int prevPos = make.pos;
            try {
                make.at(tree);
                JCTree ltree = lambdaContext.translate(tree);
                if (ltree != null) {
                    result = ltree;
                } else {
                    //access to untranslated symbols (i.e. compile-time constants,
                    //members defined inside the lambda body, etc.) )
                    super.visitIdent(tree);
                }
            } finally {
                make.at(prevPos);
            }
        }
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        VarSymbol prevPendingVar = pendingVar;
        try {
            pendingVar = tree.sym;
            if (lambdaContext != null) {
                tree.sym = lambdaContext.addLocal(tree.sym);
                tree.init = translate(tree.init);
                result = tree;
            } else {
                super.visitVarDef(tree);
            }
        } finally {
            pendingVar = prevPendingVar;
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
        int prevPos = make.pos;
        try {
            if (isTarget_void) {
                //target is void:
                // BODY;
                JCStatement stat = make.at(expr).Exec(expr);
                return make.Block(0, List.of(stat));
            } else if (isLambda_void && isTarget_Void) {
                //void to Void conversion:
                // BODY; return null;
                ListBuffer<JCStatement> stats = new ListBuffer<>();
                stats.append(make.at(expr).Exec(expr));
                stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
                return make.Block(0, stats.toList());
            } else {
                //non-void to non-void conversion:
                // return BODY;
                return make.at(expr).Block(0, List.of(make.Return(expr)));
            }
        } finally {
            make.at(prevPos);
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
                    VarSymbol loc = new VarSymbol(SYNTHETIC, names.fromString("$loc"), tree.expr.type, lambdaMethodDecl.sym);
                    JCVariableDecl varDef = make.VarDef(loc, tree.expr);
                    result = make.Block(0, List.of(varDef, make.Return(null)));
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

    private JCMethodDecl makeDeserializeMethod() {
        ListBuffer<JCCase> cases = new ListBuffer<>();
        ListBuffer<JCBreak> breaks = new ListBuffer<>();
        for (Map.Entry<String, ListBuffer<JCStatement>> entry : kInfo.deserializeCases.entrySet()) {
            JCBreak br = make.Break(null);
            breaks.add(br);
            List<JCStatement> stmts = entry.getValue().append(br).toList();
            cases.add(make.Case(JCCase.STATEMENT, List.of(make.ConstantCaseLabel(make.Literal(entry.getKey()))), null, stmts, null));
        }
        JCSwitch sw = make.Switch(deserGetter("getImplMethodName", syms.stringType), cases.toList());
        for (JCBreak br : breaks) {
            br.target = sw;
        }
        JCBlock body = make.Block(0L, List.of(
                sw,
                make.Throw(makeNewClass(
                        syms.illegalArgumentExceptionType,
                        List.of(make.Literal("Invalid lambda deserialization"))))));
        JCMethodDecl deser = make.MethodDef(make.Modifiers(kInfo.deserMethodSym.flags()),
                names.deserializeLambda,
                make.QualIdent(kInfo.deserMethodSym.getReturnType().tsym),
                List.nil(),
                List.of(make.VarDef(kInfo.deserParamSym, null)),
                List.nil(),
                body,
                null);
        deser.sym = kInfo.deserMethodSym;
        deser.type = kInfo.deserMethodSym.type;
        //System.err.printf("DESER: '%s'\n", deser);
        return lower.translateMethod(attrEnv, deser, make);
    }

    /** Make an attributed class instance creation expression.
     *  @param ctype    The class type.
     *  @param args     The constructor arguments.
     *  @param cons     The constructor symbol
     */
    JCNewClass makeNewClass(Type ctype, List<JCExpression> args, Symbol cons) {
        JCNewClass tree = make.NewClass(null,
                null, make.QualIdent(ctype.tsym), args, null);
        tree.constructor = cons;
        tree.type = ctype;
        return tree;
    }

    /** Make an attributed class instance creation expression.
     *  @param ctype    The class type.
     *  @param args     The constructor arguments.
     */
    JCNewClass makeNewClass(Type ctype, List<JCExpression> args) {
        return makeNewClass(ctype, args,
                rs.resolveConstructor(null, attrEnv, ctype, TreeInfo.types(args), List.nil()));
    }

    private void addDeserializationCase(MethodHandleSymbol refSym, Type targetType, MethodSymbol samSym, Type samType,
                                        DiagnosticPosition pos, List<LoadableConstant> staticArgs, MethodType indyType) {
        String functionalInterfaceClass = classSig(targetType);
        String functionalInterfaceMethodName = samSym.getSimpleName().toString();
        String functionalInterfaceMethodSignature = typeSig(types.erasure(samSym.type));
        if (refSym.enclClass().isInterface()) {
            Symbol baseMethod = types.overriddenObjectMethod(refSym.enclClass(), refSym);
            if (baseMethod != null) {
                // The implementation method is a java.lang.Object method, runtime will resolve this method to
                // a java.lang.Object method, so do the same.
                // This case can be removed if JDK-8172817 is fixed.
                refSym = ((MethodSymbol) baseMethod).asHandle();
            }
        }
        String implClass = classSig(types.erasure(refSym.owner.type));
        String implMethodName = refSym.getQualifiedName().toString();
        String implMethodSignature = typeSig(types.erasure(refSym.type));
        String instantiatedMethodType = typeSig(types.erasure(samType));

        int implMethodKind = refSym.referenceKind();
        JCExpression kindTest = eqTest(syms.intType, deserGetter("getImplMethodKind", syms.intType),
                make.Literal(implMethodKind));
        ListBuffer<JCExpression> serArgs = new ListBuffer<>();
        int i = 0;
        for (Type t : indyType.getParameterTypes()) {
            List<JCExpression> indexAsArg = new ListBuffer<JCExpression>().append(make.Literal(i)).toList();
            List<Type> argTypes = new ListBuffer<Type>().append(syms.intType).toList();
            serArgs.add(make.TypeCast(types.erasure(t), deserGetter("getCapturedArg", syms.objectType, argTypes, indexAsArg)));
            ++i;
        }
        JCStatement stmt = make.If(
                deserTest(deserTest(deserTest(deserTest(deserTest(deserTest(
                                                                kindTest,
                                                                "getFunctionalInterfaceClass", functionalInterfaceClass),
                                                        "getFunctionalInterfaceMethodName", functionalInterfaceMethodName),
                                                "getFunctionalInterfaceMethodSignature", functionalInterfaceMethodSignature),
                                        "getImplClass", implClass),
                                "getImplMethodSignature", implMethodSignature),
                        "getInstantiatedMethodType", instantiatedMethodType),
                make.Return(makeIndyCall(
                        pos,
                        syms.lambdaMetafactory,
                        names.altMetafactory,
                        staticArgs, indyType, serArgs.toList(), samSym.name)),
                null);
        ListBuffer<JCStatement> stmts = kInfo.deserializeCases.get(implMethodName);
        if (stmts == null) {
            stmts = new ListBuffer<>();
            kInfo.deserializeCases.put(implMethodName, stmts);
        }
        if (dumpLambdaDeserializationStats) {
            log.note(pos, Notes.LambdaDeserializationStat(
                    functionalInterfaceClass,
                    functionalInterfaceMethodName,
                    functionalInterfaceMethodSignature,
                    implMethodKind,
                    implClass,
                    implMethodName,
                    implMethodSignature,
                    instantiatedMethodType));
        }
        stmts.append(stmt);
    }

    private JCExpression eqTest(Type argType, JCExpression arg1, JCExpression arg2) {
        JCBinary testExpr = make.Binary(Tag.EQ, arg1, arg2);
        testExpr.operator = operators.resolveBinary(testExpr, Tag.EQ, argType, argType);
        testExpr.setType(syms.booleanType);
        return testExpr;
    }

    private JCExpression deserTest(JCExpression prev, String func, String lit) {
        MethodType eqmt = new MethodType(List.of(syms.objectType), syms.booleanType, List.nil(), syms.methodClass);
        Symbol eqsym = rs.resolveQualifiedMethod(null, attrEnv, syms.objectType, names.equals, List.of(syms.objectType), List.nil());
        JCMethodInvocation eqtest = make.Apply(
                List.nil(),
                make.Select(deserGetter(func, syms.stringType), eqsym).setType(eqmt),
                List.of(make.Literal(lit)));
        eqtest.setType(syms.booleanType);
        JCBinary compound = make.Binary(Tag.AND, prev, eqtest);
        compound.operator = operators.resolveBinary(compound, Tag.AND, syms.booleanType, syms.booleanType);
        compound.setType(syms.booleanType);
        return compound;
    }

    private JCExpression deserGetter(String func, Type type) {
        return deserGetter(func, type, List.nil(), List.nil());
    }

    private JCExpression deserGetter(String func, Type type, List<Type> argTypes, List<JCExpression> args) {
        MethodType getmt = new MethodType(argTypes, type, List.nil(), syms.methodClass);
        Symbol getsym = rs.resolveQualifiedMethod(null, attrEnv, syms.serializedLambdaType, names.fromString(func), argTypes, List.nil());
        return make.Apply(
                List.nil(),
                make.Select(make.Ident(kInfo.deserParamSym).setType(syms.serializedLambdaType), getsym).setType(getmt),
                args).setType(type);
    }

    /**
     * Create new synthetic method with given flags, name, type, owner
     */
    private MethodSymbol makePrivateSyntheticMethod(long flags, Name name, Type type, Symbol owner) {
        return new MethodSymbol(flags | SYNTHETIC | PRIVATE, name, type, owner);
    }

    private MethodType typeToMethodType(Type mt) {
        Type type = types.erasure(mt);
        return new MethodType(type.getParameterTypes(),
                type.getReturnType(),
                type.getThrownTypes(),
                syms.methodClass);
    }

    /**
     * Generate an indy method call to the meta factory
     */
    private JCExpression makeMetafactoryIndyCall(JCFunctionalExpression tree,
                                                 MethodHandleSymbol refSym, MethodSymbol nonDedupedRefSym,
                                                 List<JCExpression> indy_args) {
        //determine the static bsm args
        MethodSymbol samSym = (MethodSymbol) types.findDescriptorSymbol(tree.target.tsym);
        MethodType samType = typeToMethodType(tree.getDescriptorType(types));
        List<LoadableConstant> staticArgs = List.of(
                typeToMethodType(samSym.type),
                refSym.asHandle(),
                samType);

        //computed indy arg types
        ListBuffer<Type> indy_args_types = new ListBuffer<>();
        for (JCExpression arg : indy_args) {
            indy_args_types.append(arg.type);
        }

        //finally, compute the type of the indy call
        MethodType indyType = new MethodType(indy_args_types.toList(),
                tree.type,
                List.nil(),
                syms.methodClass);

        List<Symbol> bridges = bridges(tree);
        boolean isSerializable = isSerializable(tree);
        boolean needsAltMetafactory = tree.target.isIntersection() ||
                isSerializable || bridges.length() > 1;

        dumpStats(tree, needsAltMetafactory, nonDedupedRefSym);

        Name metafactoryName = needsAltMetafactory ?
                names.altMetafactory : names.metafactory;

        if (needsAltMetafactory) {
            ListBuffer<Type> markers = new ListBuffer<>();
            List<Type> targets = tree.target.isIntersection() ?
                    types.directSupertypes(tree.target) :
                    List.nil();
            for (Type t : targets) {
                t = types.erasure(t);
                if (t.tsym != syms.serializableType.tsym &&
                        t.tsym != tree.type.tsym &&
                        t.tsym != syms.objectType.tsym) {
                    markers.append(t);
                }
            }
            int flags = isSerializable ? FLAG_SERIALIZABLE : 0;
            boolean hasMarkers = markers.nonEmpty();
            boolean hasBridges = bridges.nonEmpty();
            if (hasMarkers) {
                flags |= FLAG_MARKERS;
            }
            if (hasBridges) {
                flags |= FLAG_BRIDGES;
            }
            staticArgs = staticArgs.append(LoadableConstant.Int(flags));
            if (hasMarkers) {
                staticArgs = staticArgs.append(LoadableConstant.Int(markers.length()));
                staticArgs = staticArgs.appendList(List.convert(LoadableConstant.class, markers.toList()));
            }
            if (hasBridges) {
                staticArgs = staticArgs.append(LoadableConstant.Int(bridges.length() - 1));
                for (Symbol s : bridges) {
                    Type s_erasure = s.erasure(types);
                    if (!types.isSameType(s_erasure, samSym.erasure(types))) {
                        staticArgs = staticArgs.append(((MethodType)s.erasure(types)));
                    }
                }
            }
            if (isSerializable) {
                int prevPos = make.pos;
                try {
                    make.at(kInfo.clazz);
                    addDeserializationCase(refSym, tree.type, samSym, samType,
                            tree, staticArgs, indyType);
                } finally {
                    make.at(prevPos);
                }
            }
        }

        return makeIndyCall(tree, syms.lambdaMetafactory, metafactoryName, staticArgs, indyType, indy_args, samSym.name);
    }

    /**
     * Generate an indy method call with given name, type and static bootstrap
     * arguments types
     */
    private JCExpression makeIndyCall(DiagnosticPosition pos, Type site, Name bsmName,
                                      List<LoadableConstant> staticArgs, MethodType indyType, List<JCExpression> indyArgs,
                                      Name methName) {
        int prevPos = make.pos;
        try {
            make.at(pos);
            List<Type> bsm_staticArgs = List.of(syms.methodHandleLookupType,
                    syms.stringType,
                    syms.methodTypeType).appendList(staticArgs.map(types::constantType));

            MethodSymbol bsm = rs.resolveInternalMethod(pos, attrEnv, site,
                    bsmName, bsm_staticArgs, List.nil());

            DynamicMethodSymbol dynSym =
                    new DynamicMethodSymbol(methName,
                            syms.noSymbol,
                            bsm.asHandle(),
                            indyType,
                            staticArgs.toArray(new LoadableConstant[staticArgs.length()]));
            JCFieldAccess qualifier = make.Select(make.QualIdent(site.tsym), bsmName);
            DynamicMethodSymbol existing = kInfo.dynMethSyms.putIfAbsent(
                    dynSym.poolKey(types), dynSym);
            qualifier.sym = existing != null ? existing : dynSym;
            qualifier.type = indyType.getReturnType();

            JCMethodInvocation proxyCall = make.Apply(List.nil(), qualifier, indyArgs);
            proxyCall.type = indyType.getReturnType();
            return proxyCall;
        } finally {
            make.at(prevPos);
        }
    }

    List<Symbol> bridges(JCFunctionalExpression tree) {
        ClassSymbol csym =
                types.makeFunctionalInterfaceClass(attrEnv, names.empty, tree.target, ABSTRACT | INTERFACE);
        return types.functionalInterfaceBridges(csym);
    }

    /** does this functional expression require serialization support? */
    boolean isSerializable(JCFunctionalExpression tree) {
        if (forceSerializable) {
            return true;
        }
        return types.asSuper(tree.target, syms.serializableType.tsym) != null;
    }

    void dumpStats(JCFunctionalExpression tree, boolean needsAltMetafactory, Symbol sym) {
        if (dumpLambdaToMethodStats) {
            if (tree instanceof JCLambda lambda) {
                log.note(tree, diags.noteKey(lambda.wasMethodReference ? "mref.stat.1" : "lambda.stat",
                        needsAltMetafactory, sym));
            } else if (tree instanceof JCMemberReference) {
                log.note(tree, Notes.MrefStat(needsAltMetafactory, null));
            }
        }
    }

    /**
     * This class retains all the useful information about a lambda expression,
     * and acts as a translation map that is used by the main translation routines
     * in order to adjust references to captured locals/members, etc.
     */
    class LambdaTranslationContext {

        /** the underlying (untranslated) tree */
        final JCFunctionalExpression tree;

        /** a translation map from source symbols to translated symbols */
        final Map<VarSymbol, VarSymbol> lambdaProxies = new HashMap<>();

        /** the list of symbols captured by this lambda expression */
        final List<VarSymbol> capturedVars;

        /** the synthetic symbol for the method hoisting the translated lambda */
        final MethodSymbol translatedSym;

        /** the list of parameter declarations of the translated lambda method */
        final List<JCVariableDecl> syntheticParams;

        LambdaTranslationContext(JCLambda tree) {
            this.tree = tree;
            // This symbol will be filled-in in complete
            Symbol owner = tree.owner;
            if (owner.kind == MTH) {
                final MethodSymbol originalOwner = (MethodSymbol)owner.clone(owner.owner);
                this.translatedSym = new MethodSymbol(0, null, null, owner.enclClass()) {
                    @Override
                    public MethodSymbol originalEnclosingMethod() {
                        return originalOwner;
                    }
                };
            } else {
                this.translatedSym = makePrivateSyntheticMethod(0, null, null, owner.enclClass());
            }
            ListBuffer<JCVariableDecl> params = new ListBuffer<>();
            ListBuffer<VarSymbol> parameterSymbols = new ListBuffer<>();
            LambdaCaptureScanner captureScanner = new LambdaCaptureScanner(tree);
            capturedVars = captureScanner.analyzeCaptures();
            for (VarSymbol captured : capturedVars) {
                VarSymbol trans = addSymbol(captured, LambdaSymbolKind.CAPTURED_VAR);
                params.append(make.VarDef(trans, null));
                parameterSymbols.add(trans);
            }
            for (JCVariableDecl param : tree.params) {
                VarSymbol trans = addSymbol(param.sym, LambdaSymbolKind.PARAM);
                params.append(make.VarDef(trans, null));
                parameterSymbols.add(trans);
            }
            syntheticParams = params.toList();
            completeLambdaMethodSymbol(owner, captureScanner.capturesThis);
            translatedSym.params = parameterSymbols.toList();
        }

        void completeLambdaMethodSymbol(Symbol owner, boolean thisReferenced) {
            boolean inInterface = owner.enclClass().isInterface();

            // Compute and set the lambda name
            Name name = isSerializable(tree)
                    ? serializedLambdaName(owner)
                    : lambdaName(owner);

            //prepend synthetic args to translated lambda method signature
            Type type = types.createMethodTypeWithParameters(
                    generatedLambdaSig(),
                    TreeInfo.types(syntheticParams));

            // If instance access isn't needed, make it static.
            // Interface instance methods must be default methods.
            // Lambda methods are private synthetic.
            // Inherit ACC_STRICT from the enclosing method, or, for clinit,
            // from the class.
            long flags = SYNTHETIC | LAMBDA_METHOD |
                    owner.flags_field & STRICTFP |
                    owner.owner.flags_field & STRICTFP |
                    PRIVATE |
                    (thisReferenced? (inInterface? DEFAULT : 0) : STATIC);

            translatedSym.type = type;
            translatedSym.name = name;
            translatedSym.flags_field = flags;
        }

        /**
         * For a serializable lambda, generate a disambiguating string
         * which maximizes stability across deserialization.
         *
         * @return String to differentiate synthetic lambda method names
         */
        private String serializedLambdaDisambiguation(Symbol owner) {
            StringBuilder buf = new StringBuilder();
            // Append the enclosing method signature to differentiate
            // overloaded enclosing methods.  For lambdas enclosed in
            // lambdas, the generated lambda method will not have type yet,
            // but the enclosing method's name will have been generated
            // with this same method, so it will be unique and never be
            // overloaded.
            Assert.check(
                    owner.type != null ||
                            lambdaContext != null);
            if (owner.type != null) {
                buf.append(typeSig(owner.type, true));
                buf.append(":");
            }

            // Add target type info
            buf.append(types.findDescriptorSymbol(tree.type.tsym).owner.flatName());
            buf.append(" ");

            // Add variable assigned to
            if (pendingVar != null) {
                buf.append(pendingVar.flatName());
                buf.append("=");
            }
            //add captured locals info: type, name, order
            for (Symbol fv : capturedVars) {
                if (fv != owner) {
                    buf.append(typeSig(fv.type, true));
                    buf.append(" ");
                    buf.append(fv.flatName());
                    buf.append(",");
                }
            }

            return buf.toString();
        }

        /**
         * For a non-serializable lambda, generate a simple method.
         *
         * @return Name to use for the synthetic lambda method name
         */
        private Name lambdaName(Symbol owner) {
            StringBuilder buf = new StringBuilder();
            buf.append(names.lambda);
            buf.append(syntheticMethodNameComponent(owner));
            buf.append("$");
            buf.append(kInfo.syntheticNameIndex(buf, 0));
            return names.fromString(buf.toString());
        }

        /**
         * @return Method name in a form that can be folded into a
         * component of a synthetic method name
         */
        String syntheticMethodNameComponent(Symbol owner) {
            long ownerFlags = owner.flags();
            if ((ownerFlags & BLOCK) != 0) {
                return (ownerFlags & STATIC) != 0 ?
                        "static" : "new";
            } else if (owner.isConstructor()) {
                return "new";
            } else {
                return owner.name.toString();
            }
        }

        /**
         * For a serializable lambda, generate a method name which maximizes
         * name stability across deserialization.
         *
         * @return Name to use for the synthetic lambda method name
         */
        private Name serializedLambdaName(Symbol owner) {
            StringBuilder buf = new StringBuilder();
            buf.append(names.lambda);
            // Append the name of the method enclosing the lambda.
            buf.append(syntheticMethodNameComponent(owner));
            buf.append('$');
            // Append a hash of the disambiguating string : enclosing method
            // signature, etc.
            String disam = serializedLambdaDisambiguation(owner);
            buf.append(Integer.toHexString(disam.hashCode()));
            buf.append('$');
            // The above appended name components may not be unique, append
            // a count based on the above name components.
            buf.append(kInfo.syntheticNameIndex(buf, 1));
            String result = buf.toString();
            //System.err.printf("serializedLambdaName: %s -- %s\n", result, disam);
            return names.fromString(result);
        }

        /**
         * Translate a symbol of a given kind into something suitable for the
         * synthetic lambda body
         */
        VarSymbol translate(final VarSymbol sym, LambdaSymbolKind skind) {
            VarSymbol ret;
            boolean propagateAnnos = true;
            switch (skind) {
                case CAPTURED_VAR:
                    Name name = (sym.flags() & LOCAL_CAPTURE_FIELD) != 0 ?
                            sym.baseSymbol().name : sym.name;
                    ret = new VarSymbol(SYNTHETIC | FINAL | PARAMETER, name, types.erasure(sym.type), translatedSym);
                    propagateAnnos = false;
                    break;
                case LOCAL_VAR:
                    ret = new VarSymbol(sym.flags(), sym.name, sym.type, translatedSym);
                    ret.pos = sym.pos;
                    // If sym.data == ElementKind.EXCEPTION_PARAMETER,
                    // set ret.data = ElementKind.EXCEPTION_PARAMETER too.
                    // Because method com.sun.tools.javac.jvm.Code.fillExceptionParameterPositions and
                    // com.sun.tools.javac.jvm.Code.fillLocalVarPosition would use it.
                    // See JDK-8257740 for more information.
                    if (sym.isExceptionParameter()) {
                        ret.setData(ElementKind.EXCEPTION_PARAMETER);
                    }
                    break;
                case PARAM:
                    Assert.check((sym.flags() & PARAMETER) != 0);
                    ret = new VarSymbol(sym.flags(), sym.name, types.erasure(sym.type), translatedSym);
                    ret.pos = sym.pos;
                    break;
                default:
                    Assert.error(skind.name());
                    throw new AssertionError();
            }
            if (ret != sym && propagateAnnos) {
                ret.setDeclarationAttributes(sym.getRawAttributes());
                ret.setTypeAttributes(sym.getRawTypeAttributes());
            }
            return ret;
        }

        VarSymbol addLocal(VarSymbol sym) {
            return addSymbol(sym, LambdaSymbolKind.LOCAL_VAR);
        }

        private VarSymbol addSymbol(VarSymbol sym, LambdaSymbolKind skind) {
            return lambdaProxies.computeIfAbsent(sym, s -> translate(s, skind));
        }

        JCTree translate(JCIdent lambdaIdent) {
            Symbol tSym = lambdaProxies.get(lambdaIdent.sym);
            return tSym != null ?
                    make.Ident(tSym).setType(lambdaIdent.type) :
                    null;
        }

        Type generatedLambdaSig() {
            return types.erasure(tree.getDescriptorType(types));
        }

        /**
         * Compute the set of local variables captured by this lambda expression.
         * Also determines whether this lambda expression captures the enclosing 'this'.
         */
        class LambdaCaptureScanner extends CaptureScanner {
            boolean capturesThis;
            Set<ClassSymbol> seenClasses = new HashSet<>();

            LambdaCaptureScanner(JCLambda ownerTree) {
                super(ownerTree);
            }

            @Override
            public void visitClassDef(JCClassDecl tree) {
                seenClasses.add(tree.sym);
                super.visitClassDef(tree);
            }

            @Override
            public void visitIdent(JCIdent tree) {
                if (!tree.sym.isStatic() &&
                        tree.sym.owner.kind == TYP &&
                        (tree.sym.kind == VAR || tree.sym.kind == MTH) &&
                        !seenClasses.contains(tree.sym.owner)) {
                    if ((tree.sym.flags() & LOCAL_CAPTURE_FIELD) != 0) {
                        // a local, captured by Lower - re-capture!
                        addFreeVar((VarSymbol) tree.sym);
                    } else {
                        // a reference to an enclosing field or method, we need to capture 'this'
                        capturesThis = true;
                    }
                } else {
                    // might be a local capture
                    super.visitIdent(tree);
                }
            }

            @Override
            public void visitSelect(JCFieldAccess tree) {
                if (tree.sym.kind == VAR &&
                        (tree.sym.name == names._this ||
                                tree.sym.name == names._super) &&
                        !seenClasses.contains(tree.sym.type.tsym)) {
                    capturesThis = true;
                }
                super.visitSelect(tree);
            }

            @Override
            public void visitAnnotation(JCAnnotation tree) {
                // do nothing (annotation values look like captured instance fields)
            }
        }

        /*
         * These keys provide mappings for various translated lambda symbols
         * and the prevailing order must be maintained.
         */
        enum LambdaSymbolKind {
            PARAM,          // original to translated lambda parameters
            LOCAL_VAR,      // original to translated lambda locals
            CAPTURED_VAR;   // variables in enclosing scope to translated synthetic parameters
        }
    }

    /**
     * ****************************************************************
     * Signature Generation
     * ****************************************************************
     */

    private String typeSig(Type type) {
        return typeSig(type, false);
    }

    private String typeSig(Type type, boolean allowIllegalSignature) {
        try {
            L2MSignatureGenerator sg = new L2MSignatureGenerator(allowIllegalSignature);
            sg.assembleSig(type);
            return sg.toString();
        } catch (InvalidSignatureException ex) {
            Symbol c = attrEnv.enclClass.sym;
            log.error(Errors.CannotGenerateClass(c, Fragments.IllegalSignature(c, ex.type())));
            return "<ERRONEOUS>";
        }
    }

    private String classSig(Type type) {
        try {
            L2MSignatureGenerator sg = new L2MSignatureGenerator(false);
            sg.assembleClassSig(type);
            return sg.toString();
        } catch (InvalidSignatureException ex) {
            Symbol c = attrEnv.enclClass.sym;
            log.error(Errors.CannotGenerateClass(c, Fragments.IllegalSignature(c, ex.type())));
            return "<ERRONEOUS>";
        }
    }

    /**
     * Signature Generation
     */
    private class L2MSignatureGenerator extends Types.SignatureGenerator {

        /**
         * An output buffer for type signatures.
         */
        StringBuilder sb = new StringBuilder();

        /**
         * Are signatures incompatible with JVM spec allowed?
         * Used by {@link LambdaTranslationContext#serializedLambdaDisambiguation(Symbol)}}.
         */
        boolean allowIllegalSignatures;

        L2MSignatureGenerator(boolean allowIllegalSignatures) {
            types.super();
            this.allowIllegalSignatures = allowIllegalSignatures;
        }

        @Override
        protected void reportIllegalSignature(Type t) {
            if (!allowIllegalSignatures) {
                super.reportIllegalSignature(t);
            }
        }

        @Override
        protected void append(char ch) {
            sb.append(ch);
        }

        @Override
        protected void append(byte[] ba) {
            Name name;
            try {
                name = names.fromUtf(ba);
            } catch (InvalidUtfException e) {
                throw new AssertionError(e);
            }
            sb.append(name.toString());
        }

        @Override
        protected void append(Name name) {
            sb.append(name.toString());
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
