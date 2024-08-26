/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Symbol.MethodHandleSymbol;
import com.sun.tools.javac.code.Types.SignatureGenerator.InvalidSignatureException;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.LambdaToMethod.LambdaAnalyzerPreprocessor.*;
import com.sun.tools.javac.resources.CompilerProperties.Notes;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.sun.tools.javac.comp.LambdaToMethod.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

import javax.lang.model.element.ElementKind;

import com.sun.tools.javac.main.Option;

/**
 * This pass desugars lambda expressions into static methods
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LambdaToMethod extends TreeTranslator {

    private Attr attr;
    private JCDiagnostic.Factory diags;
    private Log log;
    private Lower lower;
    private Names names;
    private Symtab syms;
    private Resolve rs;
    private Operators operators;
    private TreeMaker make;
    private Types types;
    private TransTypes transTypes;
    private Env<AttrContext> attrEnv;

    /** the analyzer scanner */
    private LambdaAnalyzerPreprocessor analyzer;

    /** map from lambda trees to translation contexts */
    private Map<JCTree, TranslationContext<?>> contextMap;

    /** current translation context (visitor argument) */
    private TranslationContext<?> context;

    /** info about the current class being processed */
    private KlassInfo kInfo;

    /** dump statistics about lambda code generation */
    private final boolean dumpLambdaToMethodStats;

    /** force serializable representation, for stress testing **/
    private final boolean forceSerializable;

    /** true if line or local variable debug info has been requested */
    private final boolean debugLinesOrVars;

    /** dump statistics about lambda method deduplication */
    private final boolean verboseDeduplication;

    /** deduplicate lambda implementation methods */
    private final boolean deduplicateLambdas;

    /** Flag for alternate metafactories indicating the lambda object is intended to be serializable */
    public static final int FLAG_SERIALIZABLE = 1 << 0;

    /** Flag for alternate metafactories indicating the lambda object has multiple targets */
    public static final int FLAG_MARKERS = 1 << 1;

    /** Flag for alternate metafactories indicating the lambda object requires multiple bridges */
    public static final int FLAG_BRIDGES = 1 << 2;

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
        analyzer = new LambdaAnalyzerPreprocessor();
        Options options = Options.instance(context);
        dumpLambdaToMethodStats = options.isSet("debug.dumpLambdaToMethodStats");
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
                this.hashCode = hashCode = TreeHasher.hash(tree, symbol.params());
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof DedupedLambda dedupedLambda)
                    && types.isSameType(symbol.asType(), dedupedLambda.symbol.asType())
                    && new TreeDiffer(symbol.params(), dedupedLambda.symbol.params()).scan(tree, dedupedLambda.tree);
        }
    }

    private class KlassInfo {

        /**
         * list of methods to append
         */
        private ListBuffer<JCTree> appendedMethodList;

        private Map<DedupedLambda, DedupedLambda> dedupedLambdas;

        private Map<Object, DynamicMethodSymbol> dynMethSyms = new HashMap<>();

        /**
         * list of deserialization cases
         */
        private final Map<String, ListBuffer<JCStatement>> deserializeCases;

       /**
         * deserialize method symbol
         */
        private final MethodSymbol deserMethodSym;

        /**
         * deserialize method parameter symbol
         */
        private final VarSymbol deserParamSym;

        private final JCClassDecl clazz;

        private KlassInfo(JCClassDecl clazz) {
            this.clazz = clazz;
            appendedMethodList = new ListBuffer<>();
            dedupedLambdas = new HashMap<>();
            deserializeCases = new HashMap<>();
            MethodType type = new MethodType(List.of(syms.serializedLambdaType), syms.objectType,
                    List.nil(), syms.methodClass);
            deserMethodSym = makePrivateSyntheticMethod(STATIC, names.deserializeLambda, type, clazz.sym);
            deserParamSym = new VarSymbol(FINAL, names.fromString("lambda"),
                    syms.serializedLambdaType, deserMethodSym);
        }

        private void addMethod(JCTree decl) {
            appendedMethodList = appendedMethodList.prepend(decl);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="translate methods">
    @Override
    public <T extends JCTree> T translate(T tree) {
        TranslationContext<?> newContext = contextMap.get(tree);
        return translate(tree, newContext != null ? newContext : context);
    }

    <T extends JCTree> T translate(T tree, TranslationContext<?> newContext) {
        TranslationContext<?> prevContext = context;
        try {
            context = newContext;
            return super.translate(tree);
        }
        finally {
            context = prevContext;
        }
    }

    <T extends JCTree> List<T> translate(List<T> trees, TranslationContext<?> newContext) {
        ListBuffer<T> buf = new ListBuffer<>();
        for (T tree : trees) {
            buf.append(translate(tree, newContext));
        }
        return buf.toList();
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        this.make = make;
        this.attrEnv = env;
        this.context = null;
        this.contextMap = new HashMap<>();
        cdef = analyzer.analyzeAndPreprocessClass((JCClassDecl) cdef);
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
        KlassInfo prevKlassInfo = kInfo;
        try {
            kInfo = new KlassInfo(tree);
            super.visitClassDef(tree);
            if (!kInfo.deserializeCases.isEmpty()) {
                int prevPos = make.pos;
                try {
                    make.at(tree);
                    kInfo.addMethod(makeDeserializeMethod(tree.sym));
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
        MethodSymbol sym = localContext.translatedSym;
        MethodType lambdaType = (MethodType) sym.type;

        {   /* Type annotation management: Based on where the lambda features, type annotations that
               are interior to it, may at this point be attached to the enclosing method, or the first
               constructor in the class, or in the enclosing class symbol or in the field whose
               initializer is the lambda. In any event, gather up the annotations that belong to the
               lambda and attach it to the implementation method.
            */

            Symbol owner = localContext.owner;
            apportionTypeAnnotations(tree,
                    owner::getRawTypeAttributes,
                    owner::setTypeAttributes,
                    sym::setTypeAttributes);


            boolean init;
            if ((init = (owner.name == names.init)) || owner.name == names.clinit) {
                owner = owner.owner;
                apportionTypeAnnotations(tree,
                        init ? owner::getInitTypeAttributes : owner::getClassInitTypeAttributes,
                        init ? owner::setInitTypeAttributes : owner::setClassInitTypeAttributes,
                        sym::appendUniqueTypeAttributes);
            }
            if (localContext.self != null && localContext.self.getKind() == ElementKind.FIELD) {
                owner = localContext.self;
                apportionTypeAnnotations(tree,
                        owner::getRawTypeAttributes,
                        owner::setTypeAttributes,
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

        //translate lambda body
        //As the lambda body is translated, all references to lambda locals,
        //captured variables, enclosing members are adjusted accordingly
        //to refer to the static method parameters (rather than i.e. accessing
        //captured members directly).
        lambdaDecl.body = translate(makeLambdaBody(tree, lambdaDecl));

        boolean dedupe = false;
        if (deduplicateLambdas && !debugLinesOrVars && !localContext.isSerializable()) {
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
                    localContext.owner.enclClass()));
        }

        //add captured locals
        for (Symbol fv : localContext.getSymbolMap(CAPTURED_VAR).keySet()) {
            if (fv != localContext.self) {
                JCExpression captured_local = make.Ident(fv).setType(fv.type);
                syntheticInits.append(captured_local);
            }
        }

        //then, determine the arguments to the indy call
        List<JCExpression> indy_args = translate(syntheticInits.toList(), localContext.prev);

        //convert to an invokedynamic call
        result = makeMetafactoryIndyCall(context, sym.asHandle(), indy_args);
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
     * @param tree
     */
    @Override
    public void visitReference(JCMemberReference tree) {
        ReferenceTranslationContext localContext = (ReferenceTranslationContext)context;

        //first determine the method symbol to be used to generate the sam instance
        //this is either the method reference symbol, or the bridged reference symbol
        MethodSymbol refSym = (MethodSymbol)tree.sym;

        //the qualifying expression is treated as a special captured arg
        JCExpression init;
        switch(tree.kind) {

            case IMPLICIT_INNER:    /** Inner :: new */
            case SUPER:             /** super :: instMethod */
                init = makeThis(
                    localContext.owner.enclClass().asType(),
                    localContext.owner.enclClass());
                break;

            case BOUND:             /** Expr :: instMethod */
                init = transTypes.coerce(attrEnv, tree.getQualifierExpression(),
                    types.erasure(tree.sym.owner.type));
                init = attr.makeNullCheck(init);
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

        List<JCExpression> indy_args = init==null? List.nil() : translate(List.of(init), localContext.prev);


        //build a sam instance using an indy call to the meta-factory
        result = makeMetafactoryIndyCall(localContext, refSym.asHandle(), indy_args);
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
            int prevPos = make.pos;
            try {
                make.at(tree);

                LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
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
        LambdaTranslationContext lambdaContext = (LambdaTranslationContext)context;
        if (context != null && lambdaContext.getSymbolMap(LOCAL_VAR).containsKey(tree.sym)) {
            tree.init = translate(tree.init);
            tree.sym = (VarSymbol) lambdaContext.getSymbolMap(LOCAL_VAR).get(tree.sym);
            result = tree;
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
                    VarSymbol loc = makeSyntheticVar(0, names.fromString("$loc"), tree.expr.type, lambdaMethodDecl.sym);
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

    private JCMethodDecl makeDeserializeMethod(Symbol kSym) {
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

    private void addDeserializationCase(MethodHandleSymbol refSym, Type targetType, MethodSymbol samSym,
                                        DiagnosticPosition pos, List<LoadableConstant> staticArgs, MethodType indyType) {
        String functionalInterfaceClass = classSig(targetType);
        String functionalInterfaceMethodName = samSym.getSimpleName().toString();
        String functionalInterfaceMethodSignature = typeSig(types.erasure(samSym.type));
        Symbol baseMethod = refSym.baseSymbol();
        Symbol origMethod = baseMethod.baseSymbol();
        if (baseMethod != origMethod && origMethod.owner == syms.objectType.tsym) {
            //the implementation method is a java.lang.Object method transferred to an
            //interface that does not declare it. Runtime will refer to this method as to
            //a java.lang.Object method, so do the same:
            refSym = ((MethodSymbol) origMethod).asHandle();
        }
        String implClass = classSig(types.erasure(refSym.owner.type));
        String implMethodName = refSym.getQualifiedName().toString();
        String implMethodSignature = typeSig(types.erasure(refSym.type));

        JCExpression kindTest = eqTest(syms.intType, deserGetter("getImplMethodKind", syms.intType),
                make.Literal(refSym.referenceKind()));
        ListBuffer<JCExpression> serArgs = new ListBuffer<>();
        int i = 0;
        for (Type t : indyType.getParameterTypes()) {
            List<JCExpression> indexAsArg = new ListBuffer<JCExpression>().append(make.Literal(i)).toList();
            List<Type> argTypes = new ListBuffer<Type>().append(syms.intType).toList();
            serArgs.add(make.TypeCast(types.erasure(t), deserGetter("getCapturedArg", syms.objectType, argTypes, indexAsArg)));
            ++i;
        }
        JCStatement stmt = make.If(
                deserTest(deserTest(deserTest(deserTest(deserTest(
                    kindTest,
                    "getFunctionalInterfaceClass", functionalInterfaceClass),
                    "getFunctionalInterfaceMethodName", functionalInterfaceMethodName),
                    "getFunctionalInterfaceMethodSignature", functionalInterfaceMethodSignature),
                    "getImplClass", implClass),
                    "getImplMethodSignature", implMethodSignature),
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
        /* **
        System.err.printf("+++++++++++++++++\n");
        System.err.printf("*functionalInterfaceClass: '%s'\n", functionalInterfaceClass);
        System.err.printf("*functionalInterfaceMethodName: '%s'\n", functionalInterfaceMethodName);
        System.err.printf("*functionalInterfaceMethodSignature: '%s'\n", functionalInterfaceMethodSignature);
        System.err.printf("*implMethodKind: %d\n", implMethodKind);
        System.err.printf("*implClass: '%s'\n", implClass);
        System.err.printf("*implMethodName: '%s'\n", implMethodName);
        System.err.printf("*implMethodSignature: '%s'\n", implMethodSignature);
        ****/
        stmts.append(stmt);
    }

    private JCExpression eqTest(Type argType, JCExpression arg1, JCExpression arg2) {
        JCBinary testExpr = make.Binary(JCTree.Tag.EQ, arg1, arg2);
        testExpr.operator = operators.resolveBinary(testExpr, JCTree.Tag.EQ, argType, argType);
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
        JCBinary compound = make.Binary(JCTree.Tag.AND, prev, eqtest);
        compound.operator = operators.resolveBinary(compound, JCTree.Tag.AND, syms.booleanType, syms.booleanType);
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

    /**
     * Create new synthetic variable with given flags, name, type, owner
     */
    private VarSymbol makeSyntheticVar(long flags, Name name, Type type, Symbol owner) {
        return new VarSymbol(flags | SYNTHETIC, name, type, owner);
    }

    // </editor-fold>

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
    private JCExpression makeMetafactoryIndyCall(TranslationContext<?> context,
            MethodHandleSymbol refSym, List<JCExpression> indy_args) {
        JCFunctionalExpression tree = context.tree;
        //determine the static bsm args
        MethodSymbol samSym = (MethodSymbol) types.findDescriptorSymbol(tree.target.tsym);
        List<LoadableConstant> staticArgs = List.of(
                typeToMethodType(samSym.type),
                refSym.asHandle(),
                typeToMethodType(tree.getDescriptorType(types)));

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

        Name metafactoryName = context.needsAltMetafactory() ?
                names.altMetafactory : names.metafactory;

        if (context.needsAltMetafactory()) {
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
            int flags = context.isSerializable() ? FLAG_SERIALIZABLE : 0;
            boolean hasMarkers = markers.nonEmpty();
            boolean hasBridges = context.bridges.nonEmpty();
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
                staticArgs = staticArgs.append(LoadableConstant.Int(context.bridges.length() - 1));
                for (Symbol s : context.bridges) {
                    Type s_erasure = s.erasure(types);
                    if (!types.isSameType(s_erasure, samSym.erasure(types))) {
                        staticArgs = staticArgs.append(((MethodType)s.erasure(types)));
                    }
                }
            }
            if (context.isSerializable()) {
                int prevPos = make.pos;
                try {
                    make.at(kInfo.clazz);
                    addDeserializationCase(refSym, tree.type, samSym,
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

    // <editor-fold defaultstate="collapsed" desc="Lambda/reference analyzer">
    /**
     * This visitor collects information about translation of a lambda expression.
     * More specifically, it keeps track of the enclosing contexts and captured locals
     * accessed by the lambda being translated (as well as other useful info).
     * It also translates away problems for LambdaToMethod.
     */
    class LambdaAnalyzerPreprocessor extends TreeTranslator {

        /** the frame stack - used to reconstruct translation info about enclosing scopes */
        private List<Frame> frameStack;

        /**
         * keep the count of lambda expression (used to generate unambiguous
         * names)
         */
        private int lambdaCount = 0;

        /**
         * keep the count of lambda expression defined in given context (used to
         * generate unambiguous names for serializable lambdas)
         */
        private class SyntheticMethodNameCounter {
            private Map<String, Integer> map = new HashMap<>();
            int getIndex(StringBuilder buf) {
                String temp = buf.toString();
                Integer count = map.get(temp);
                if (count == null) {
                    count = 0;
                }
                ++count;
                map.put(temp, count);
                return count;
            }
        }
        private SyntheticMethodNameCounter syntheticMethodNameCounts =
                new SyntheticMethodNameCounter();

        private Map<Symbol, JCClassDecl> localClassDefs;

        /**
         * maps for fake clinit symbols to be used as owners of lambda occurring in
         * a static var init context
         */
        private Map<ClassSymbol, Symbol> clinits = new HashMap<>();

        private JCClassDecl analyzeAndPreprocessClass(JCClassDecl tree) {
            frameStack = List.nil();
            localClassDefs = new HashMap<>();
            return translate(tree);
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
            int prevLambdaCount = lambdaCount;
            SyntheticMethodNameCounter prevSyntheticMethodNameCounts =
                    syntheticMethodNameCounts;
            Map<ClassSymbol, Symbol> prevClinits = clinits;
            DiagnosticSource prevSource = log.currentSource();
            try {
                log.useSource(tree.sym.sourcefile);
                lambdaCount = 0;
                syntheticMethodNameCounts = new SyntheticMethodNameCounter();
                prevClinits = new HashMap<>();
                if (tree.sym.owner.kind == MTH) {
                    localClassDefs.put(tree.sym, tree);
                }
                if (directlyEnclosingLambda() != null) {
                    tree.sym.owner = owner();
                }
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitClassDef(tree);
            }
            finally {
                log.useSource(prevSource.getFile());
                frameStack = prevStack;
                lambdaCount = prevLambdaCount;
                syntheticMethodNameCounts = prevSyntheticMethodNameCounts;
                clinits = prevClinits;
            }
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (context() != null && lambdaIdentSymbolFilter(tree.sym)) {
                if (tree.sym.kind == VAR &&
                        tree.sym.owner.kind == MTH &&
                        tree.type.constValue() == null) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null) {
                        if (localContext.tree.getTag() == LAMBDA) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            ((LambdaTranslationContext)localContext)
                                    .addSymbol(tree.sym, CAPTURED_VAR);
                        }
                        localContext = localContext.prev;
                    }
                } else if (tree.sym.owner.kind == TYP) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null  && !localContext.owner.isStatic()) {
                        if (localContext.tree.hasTag(LAMBDA)) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            switch (block.getTag()) {
                                case CLASSDEF:
                                    JCClassDecl cdecl = (JCClassDecl)block;
                                    ((LambdaTranslationContext)localContext)
                                            .addSymbol(cdecl.sym, CAPTURED_THIS);
                                    break;
                                default:
                                    Assert.error("bad block kind");
                            }
                        }
                        localContext = localContext.prev;
                    }
                }
            }
            super.visitIdent(tree);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            analyzeLambda(tree, tree.wasMethodReference ? "mref.stat.1" : "lambda.stat");
        }

        private LambdaTranslationContext analyzeLambda(JCLambda tree, String statKey) {
            List<Frame> prevStack = frameStack;
            try {
                LambdaTranslationContext context = new LambdaTranslationContext(tree);
                frameStack = frameStack.prepend(new Frame(tree));
                for (JCVariableDecl param : tree.params) {
                    context.addSymbol(param.sym, PARAM);
                    frameStack.head.addLocal(param.sym);
                }
                contextMap.put(tree, context);
                super.visitLambda(tree);
                context.complete();
                if (dumpLambdaToMethodStats) {
                    log.note(tree, diags.noteKey(statKey, context.needsAltMetafactory(), context.translatedSym));
                }
                return context;
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

        /**
         * Method references to local class constructors, may, if the local
         * class references local variables, have implicit constructor
         * parameters added in Lower; As a result, the invokedynamic bootstrap
         * information added in the LambdaToMethod pass will have the wrong
         * signature. Hooks between Lower and LambdaToMethod have been added to
         * handle normal "new" in this case. This visitor converts potentially
         * affected method references into a lambda containing a normal
         * expression.
         *
         * @param tree
         */
        @Override
        public void visitReference(JCMemberReference tree) {
            ReferenceTranslationContext rcontext = new ReferenceTranslationContext(tree);
            contextMap.put(tree, rcontext);
            super.visitReference(tree);
            if (dumpLambdaToMethodStats) {
                log.note(tree, Notes.MrefStat(rcontext.needsAltMetafactory(), null));
            }
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            if (context() != null && tree.sym.kind == VAR &&
                        (tree.sym.name == names._this ||
                         tree.sym.name == names._super)) {
                // A select of this or super means, if we are in a lambda,
                // we much have an instance context
                TranslationContext<?> localContext = context();
                while (localContext != null  && !localContext.owner.isStatic()) {
                    if (localContext.tree.hasTag(LAMBDA)) {
                        JCClassDecl clazz = (JCClassDecl)capturedDecl(localContext.depth, tree.sym);
                        if (clazz == null) break;
                        ((LambdaTranslationContext)localContext).addSymbol(clazz.sym, CAPTURED_THIS);
                    }
                    localContext = localContext.prev;
                }
            }
            super.visitSelect(tree);
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            TranslationContext<?> context = context();
            if (context != null && context instanceof LambdaTranslationContext lambdaContext) {
                for (Frame frame : frameStack) {
                    if (frame.tree.hasTag(VARDEF)) {
                        //skip variable frames inside a lambda:
                        continue;
                    } else if (frame.tree.hasTag(LAMBDA)) {
                        lambdaContext.addSymbol(tree.sym, LOCAL_VAR);
                    } else {
                        break;
                    }
                }
                // Check for type variables (including as type arguments).
                // If they occur within class nested in a lambda, mark for erasure
                Type type = tree.sym.asType();
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
                        if (((JCVariableDecl)frameStack2.head.tree).sym.isDirectlyOrIndirectlyLocal()) {
                            frameStack2 = frameStack2.tail;
                            break;
                        }
                        JCClassDecl cdecl = (JCClassDecl)frameStack2.tail.head.tree;
                        return initSym(cdecl.sym,
                                ((JCVariableDecl)frameStack2.head.tree).sym.flags() & STATIC);
                    case BLOCK:
                        JCClassDecl cdecl2 = (JCClassDecl)frameStack2.tail.head.tree;
                        return initSym(cdecl2.sym,
                                ((JCBlock)frameStack2.head.tree).flags & STATIC);
                    case CLASSDEF:
                        return ((JCClassDecl)frameStack2.head.tree).sym;
                    case METHODDEF:
                        return ((JCMethodDecl)frameStack2.head.tree).sym;
                    case LAMBDA:
                        if (!skipLambda)
                            return ((LambdaTranslationContext)contextMap
                                    .get(frameStack2.head.tree)).translatedSym;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            Assert.error();
            return null;
        }

        private Symbol initSym(ClassSymbol csym, long flags) {
            boolean isStatic = (flags & STATIC) != 0;
            if (isStatic) {
                /* static clinits are generated in Gen, so we need to use a fake
                 * one. Attr creates a fake clinit method while attributing
                 * lambda expressions used as initializers of static fields, so
                 * let's use that one.
                 */
                MethodSymbol clinit = attr.removeClinit(csym);
                if (clinit != null) {
                    clinits.put(csym, clinit);
                    return clinit;
                }

                /* if no clinit is found at Attr, then let's try at clinits.
                 */
                clinit = (MethodSymbol)clinits.get(csym);
                if (clinit == null) {
                    /* no luck, let's create a new one
                     */
                    clinit = makePrivateSyntheticMethod(STATIC,
                            names.clinit,
                            new MethodType(List.nil(), syms.voidType,
                                List.nil(), syms.methodClass),
                            csym);
                    clinits.put(csym, clinit);
                }
                return clinit;
            } else {
                //get the first constructor and treat it as the instance init sym
                for (Symbol s : csym.members_field.getSymbolsByName(names.init)) {
                    return s;
                }
            }
            Assert.error("init not found");
            return null;
        }

        private JCTree directlyEnclosingLambda() {
            if (frameStack.isEmpty()) {
                return null;
            }
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

        private boolean inClassWithinLambda() {
            if (frameStack.isEmpty()) {
                return false;
            }
            List<Frame> frameStack2 = frameStack;
            boolean classFound = false;
            while (frameStack2.nonEmpty()) {
                switch (frameStack2.head.tree.getTag()) {
                    case LAMBDA:
                        return classFound;
                    case CLASSDEF:
                        classFound = true;
                        frameStack2 = frameStack2.tail;
                        break;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            // No lambda
            return false;
        }

        /**
         * Return the declaration corresponding to a symbol in the enclosing
         * scope; the depth parameter is used to filter out symbols defined
         * in nested scopes (which do not need to undergo capture).
         */
        private JCTree capturedDecl(int depth, Symbol sym) {
            Assert.check(sym.kind != TYP);
            int currentDepth = frameStack.size() - 1;
            for (Frame block : frameStack) {
                switch (block.tree.getTag()) {
                    case CLASSDEF:
                        ClassSymbol clazz = ((JCClassDecl)block.tree).sym;
                        if (clazz.isSubClass(sym.enclClass(), types)) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    case VARDEF:
                        if ((((JCVariableDecl)block.tree).sym == sym &&
                                sym.owner.kind == MTH) || //only locals are captured
                            (block.locals != null && block.locals.contains(sym))) {
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
        abstract class TranslationContext<T extends JCFunctionalExpression> {

            /** the underlying (untranslated) tree */
            final T tree;

            /** points to the adjusted enclosing scope in which this lambda/mref expression occurs */
            final Symbol owner;

            /** the depth of this lambda expression in the frame stack */
            final int depth;

            /** the enclosing translation context (set for nested lambdas/mref) */
            final TranslationContext<?> prev;

            /** list of methods to be bridged by the meta-factory */
            final List<Symbol> bridges;

            TranslationContext(T tree) {
                this.tree = tree;
                this.owner = owner(true);
                this.depth = frameStack.size() - 1;
                this.prev = context();
                ClassSymbol csym =
                        types.makeFunctionalInterfaceClass(attrEnv, names.empty, tree.target, ABSTRACT | INTERFACE);
                this.bridges = types.functionalInterfaceBridges(csym);
            }

            /** does this functional expression need to be created using alternate metafactory? */
            boolean needsAltMetafactory() {
                return tree.target.isIntersection() ||
                        isSerializable() ||
                        bridges.length() > 1;
            }

            /** does this functional expression require serialization support? */
            boolean isSerializable() {
                if (forceSerializable) {
                    return true;
                }
                return types.asSuper(tree.target, syms.serializableType.tsym) != null;
            }

            /**
             * @return Name of the enclosing method to be folded into synthetic
             * method name
             */
            String enclosingMethodName() {
                return syntheticMethodNameComponent(owner.name);
            }

            /**
             * @return Method name in a form that can be folded into a
             * component of a synthetic method name
             */
            String syntheticMethodNameComponent(Name name) {
                if (name == null) {
                    return "null";
                }
                String methodName = name.toString();
                if (methodName.equals("<clinit>")) {
                    methodName = "static";
                } else if (methodName.equals("<init>")) {
                    methodName = "new";
                }
                return methodName;
            }
        }

        /**
         * This class retains all the useful information about a lambda expression;
         * the contents of this class are filled by the LambdaAnalyzer visitor,
         * and the used by the main translation routines in order to adjust references
         * to captured locals/members, etc.
         */
        class LambdaTranslationContext extends TranslationContext<JCLambda> {

            /** variable in the enclosing context to which this lambda is assigned */
            final Symbol self;

            /** variable in the enclosing context to which this lambda is assigned */
            final Symbol assignedTo;

            Map<LambdaSymbolKind, Map<Symbol, Symbol>> translatedSymbols;

            /** the synthetic symbol for the method hoisting the translated lambda */
            MethodSymbol translatedSym;

            List<JCVariableDecl> syntheticParams;

            LambdaTranslationContext(JCLambda tree) {
                super(tree);
                Frame frame = frameStack.head;
                switch (frame.tree.getTag()) {
                    case VARDEF:
                        assignedTo = self = ((JCVariableDecl) frame.tree).sym;
                        break;
                    case ASSIGN:
                        self = null;
                        assignedTo = TreeInfo.symbol(((JCAssign) frame.tree).getVariable());
                        break;
                    default:
                        assignedTo = self = null;
                        break;
                 }

                // This symbol will be filled-in in complete
                if (owner.kind == MTH) {
                    final MethodSymbol originalOwner = (MethodSymbol)owner.clone(owner.owner);
                    this.translatedSym = new MethodSymbol(SYNTHETIC | PRIVATE, null, null, owner.enclClass()) {
                        @Override
                        public MethodSymbol originalEnclosingMethod() {
                            return originalOwner;
                        }
                    };
                } else {
                    this.translatedSym = makePrivateSyntheticMethod(0, null, null, owner.enclClass());
                }
                translatedSymbols = new EnumMap<>(LambdaSymbolKind.class);

                translatedSymbols.put(PARAM, new LinkedHashMap<>());
                translatedSymbols.put(LOCAL_VAR, new LinkedHashMap<>());
                translatedSymbols.put(CAPTURED_VAR, new LinkedHashMap<>());
                translatedSymbols.put(CAPTURED_THIS, new LinkedHashMap<>());
            }

             /**
             * For a serializable lambda, generate a disambiguating string
             * which maximizes stability across deserialization.
             *
             * @return String to differentiate synthetic lambda method names
             */
            private String serializedLambdaDisambiguation() {
                StringBuilder buf = new StringBuilder();
                // Append the enclosing method signature to differentiate
                // overloaded enclosing methods.  For lambdas enclosed in
                // lambdas, the generated lambda method will not have type yet,
                // but the enclosing method's name will have been generated
                // with this same method, so it will be unique and never be
                // overloaded.
                Assert.check(
                        owner.type != null ||
                        directlyEnclosingLambda() != null);
                if (owner.type != null) {
                    buf.append(typeSig(owner.type, true));
                    buf.append(":");
                }

                // Add target type info
                buf.append(types.findDescriptorSymbol(tree.type.tsym).owner.flatName());
                buf.append(" ");

                // Add variable assigned to
                if (assignedTo != null) {
                    buf.append(assignedTo.flatName());
                    buf.append("=");
                }
                //add captured locals info: type, name, order
                for (Symbol fv : getSymbolMap(CAPTURED_VAR).keySet()) {
                    if (fv != self) {
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
            private Name lambdaName() {
                return names.lambda.append(names.fromString(enclosingMethodName() + "$" + lambdaCount++));
            }

            /**
             * For a serializable lambda, generate a method name which maximizes
             * name stability across deserialization.
             *
             * @return Name to use for the synthetic lambda method name
             */
            private Name serializedLambdaName() {
                StringBuilder buf = new StringBuilder();
                buf.append(names.lambda);
                // Append the name of the method enclosing the lambda.
                buf.append(enclosingMethodName());
                buf.append('$');
                // Append a hash of the disambiguating string : enclosing method
                // signature, etc.
                String disam = serializedLambdaDisambiguation();
                buf.append(Integer.toHexString(disam.hashCode()));
                buf.append('$');
                // The above appended name components may not be unique, append
                // a count based on the above name components.
                buf.append(syntheticMethodNameCounts.getIndex(buf));
                String result = buf.toString();
                //System.err.printf("serializedLambdaName: %s -- %s\n", result, disam);
                return names.fromString(result);
            }

            /**
             * Translate a symbol of a given kind into something suitable for the
             * synthetic lambda body
             */
            Symbol translate(final Symbol sym, LambdaSymbolKind skind) {
                Symbol ret;
                switch (skind) {
                    case CAPTURED_THIS:
                        ret = sym;  // self represented
                        break;
                    case CAPTURED_VAR:
                        ret = new VarSymbol(SYNTHETIC | FINAL | PARAMETER, sym.name, types.erasure(sym.type), translatedSym) {
                            @Override
                            public Symbol baseSymbol() {
                                //keep mapping with original captured symbol
                                return sym;
                            }
                        };
                        break;
                    case LOCAL_VAR:
                        ret = new VarSymbol(sym.flags() & FINAL, sym.name, sym.type, translatedSym) {
                            @Override
                            public Symbol baseSymbol() {
                                //keep mapping with original symbol
                                return sym;
                            }
                        };
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        // If sym.data == ElementKind.EXCEPTION_PARAMETER,
                        // set ret.data = ElementKind.EXCEPTION_PARAMETER too.
                        // Because method com.sun.tools.javac.jvm.Code.fillExceptionParameterPositions and
                        // com.sun.tools.javac.jvm.Code.fillLocalVarPosition would use it.
                        // See JDK-8257740 for more information.
                        if (((VarSymbol) sym).isExceptionParameter()) {
                            ((VarSymbol) ret).setData(ElementKind.EXCEPTION_PARAMETER);
                        }
                        break;
                    case PARAM:
                        ret = new VarSymbol((sym.flags() & FINAL) | PARAMETER, sym.name, types.erasure(sym.type), translatedSym);
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        // Set ret.data. Same as case LOCAL_VAR above.
                        if (((VarSymbol) sym).isExceptionParameter()) {
                            ((VarSymbol) ret).setData(ElementKind.EXCEPTION_PARAMETER);
                        }
                        break;
                    default:
                        Assert.error(skind.name());
                        throw new AssertionError();
                }
                if (ret != sym && skind.propagateAnnotations()) {
                    ret.setDeclarationAttributes(sym.getRawAttributes());
                    ret.setTypeAttributes(sym.getRawTypeAttributes());
                }
                return ret;
            }

            void addSymbol(Symbol sym, LambdaSymbolKind skind) {
                Map<Symbol, Symbol> transMap = getSymbolMap(skind);
                if (!transMap.containsKey(sym)) {
                    transMap.put(sym, translate(sym, skind));
                }
            }

            Map<Symbol, Symbol> getSymbolMap(LambdaSymbolKind skind) {
                Map<Symbol, Symbol> m = translatedSymbols.get(skind);
                Assert.checkNonNull(m);
                return m;
            }

            JCTree translate(JCIdent lambdaIdent) {
                for (LambdaSymbolKind kind : LambdaSymbolKind.values()) {
                    Map<Symbol, Symbol> m = getSymbolMap(kind);
                    switch(kind) {
                        default:
                            if (m.containsKey(lambdaIdent.sym)) {
                                Symbol tSym = m.get(lambdaIdent.sym);
                                JCTree t = make.Ident(tSym).setType(lambdaIdent.type);
                                return t;
                            }
                            break;
                    }
                }
                return null;
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

                // If instance access isn't needed, make it static.
                // Interface instance methods must be default methods.
                // Lambda methods are private synthetic.
                // Inherit ACC_STRICT from the enclosing method, or, for clinit,
                // from the class.
                translatedSym.flags_field = SYNTHETIC | LAMBDA_METHOD |
                        owner.flags_field & STRICTFP |
                        owner.owner.flags_field & STRICTFP |
                        PRIVATE |
                        (thisReferenced? (inInterface? DEFAULT : 0) : STATIC);

                //compute synthetic params
                ListBuffer<JCVariableDecl> params = new ListBuffer<>();
                ListBuffer<VarSymbol> parameterSymbols = new ListBuffer<>();

                // The signature of the method is augmented with the following
                // synthetic parameters:
                //
                // 1) reference to enclosing contexts captured by the lambda expression
                // 2) enclosing locals captured by the lambda expression
                for (Symbol thisSym : getSymbolMap(CAPTURED_VAR).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                    parameterSymbols.append((VarSymbol) thisSym);
                }
                for (Symbol thisSym : getSymbolMap(PARAM).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                    parameterSymbols.append((VarSymbol) thisSym);
                }
                syntheticParams = params.toList();

                translatedSym.params = parameterSymbols.toList();

                // Compute and set the lambda name
                translatedSym.name = isSerializable()
                        ? serializedLambdaName()
                        : lambdaName();

                //prepend synthetic args to translated lambda method signature
                translatedSym.type = types.createMethodTypeWithParameters(
                        generatedLambdaSig(),
                        TreeInfo.types(syntheticParams));
            }

            Type generatedLambdaSig() {
                return types.erasure(tree.getDescriptorType(types));
            }
        }

        /**
         * Simple subclass modelling the translation context of a method reference.
         */
        final class ReferenceTranslationContext extends TranslationContext<JCMemberReference> {

            ReferenceTranslationContext(JCMemberReference tree) {
                super(tree);
            }
        }
    }
    // </editor-fold>

    /*
     * These keys provide mappings for various translated lambda symbols
     * and the prevailing order must be maintained.
     */
    enum LambdaSymbolKind {
        PARAM,          // original to translated lambda parameters
        LOCAL_VAR,      // original to translated lambda locals
        CAPTURED_VAR,   // variables in enclosing scope to translated synthetic parameters
        CAPTURED_THIS;  // class symbols to translated synthetic parameters (for captured member access)

        boolean propagateAnnotations() {
            switch (this) {
                case CAPTURED_VAR:
                case CAPTURED_THIS:
                    return false;
                default:
                    return true;
           }
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
         * Used by {@link LambdaTranslationContext#serializedLambdaDisambiguation()}.
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
