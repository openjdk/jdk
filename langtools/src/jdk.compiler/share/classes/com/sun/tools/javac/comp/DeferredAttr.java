/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.LambdaExpressionTree.BodyKind;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.Resolve.ResolveError;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.Attr.ResultInfo;
import com.sun.tools.javac.comp.Infer.InferenceContext;
import com.sun.tools.javac.comp.Resolve.MethodResolutionPhase;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.Log.DeferredDiagnosticHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;

/**
 * This is an helper class that is used to perform deferred type-analysis.
 * Each time a poly expression occurs in argument position, javac attributes it
 * with a temporary 'deferred type' that is checked (possibly multiple times)
 * against an expected formal type.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DeferredAttr extends JCTree.Visitor {
    protected static final Context.Key<DeferredAttr> deferredAttrKey = new Context.Key<>();

    final Attr attr;
    final Check chk;
    final JCDiagnostic.Factory diags;
    final Enter enter;
    final Infer infer;
    final Resolve rs;
    final Log log;
    final Symtab syms;
    final TreeMaker make;
    final Types types;
    final Flow flow;
    final Names names;
    final TypeEnvs typeEnvs;

    public static DeferredAttr instance(Context context) {
        DeferredAttr instance = context.get(deferredAttrKey);
        if (instance == null)
            instance = new DeferredAttr(context);
        return instance;
    }

    protected DeferredAttr(Context context) {
        context.put(deferredAttrKey, this);
        attr = Attr.instance(context);
        chk = Check.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        enter = Enter.instance(context);
        infer = Infer.instance(context);
        rs = Resolve.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        flow = Flow.instance(context);
        names = Names.instance(context);
        stuckTree = make.Ident(names.empty).setType(Type.stuckType);
        typeEnvs = TypeEnvs.instance(context);
        emptyDeferredAttrContext =
            new DeferredAttrContext(AttrMode.CHECK, null, MethodResolutionPhase.BOX, infer.emptyContext, null, null) {
                @Override
                void addDeferredAttrNode(DeferredType dt, ResultInfo ri, DeferredStuckPolicy deferredStuckPolicy) {
                    Assert.error("Empty deferred context!");
                }
                @Override
                void complete() {
                    Assert.error("Empty deferred context!");
                }

                @Override
                public String toString() {
                    return "Empty deferred context!";
                }
            };
    }

    /** shared tree for stuck expressions */
    final JCTree stuckTree;

    /**
     * This type represents a deferred type. A deferred type starts off with
     * no information on the underlying expression type. Such info needs to be
     * discovered through type-checking the deferred type against a target-type.
     * Every deferred type keeps a pointer to the AST node from which it originated.
     */
    public class DeferredType extends Type {

        public JCExpression tree;
        Env<AttrContext> env;
        AttrMode mode;
        SpeculativeCache speculativeCache;

        DeferredType(JCExpression tree, Env<AttrContext> env) {
            super(null, TypeMetadata.empty);
            this.tree = tree;
            this.env = attr.copyEnv(env);
            this.speculativeCache = new SpeculativeCache();
        }

        @Override
        public DeferredType clone(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a deferred type");
        }

        @Override
        public TypeTag getTag() {
            return DEFERRED;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return "DeferredType";
        }

        /**
         * A speculative cache is used to keep track of all overload resolution rounds
         * that triggered speculative attribution on a given deferred type. Each entry
         * stores a pointer to the speculative tree and the resolution phase in which the entry
         * has been added.
         */
        class SpeculativeCache {

            private Map<Symbol, List<Entry>> cache = new WeakHashMap<>();

            class Entry {
                JCTree speculativeTree;
                ResultInfo resultInfo;

                public Entry(JCTree speculativeTree, ResultInfo resultInfo) {
                    this.speculativeTree = speculativeTree;
                    this.resultInfo = resultInfo;
                }

                boolean matches(MethodResolutionPhase phase) {
                    return resultInfo.checkContext.deferredAttrContext().phase == phase;
                }
            }

            /**
             * Retrieve a speculative cache entry corresponding to given symbol
             * and resolution phase
             */
            Entry get(Symbol msym, MethodResolutionPhase phase) {
                List<Entry> entries = cache.get(msym);
                if (entries == null) return null;
                for (Entry e : entries) {
                    if (e.matches(phase)) return e;
                }
                return null;
            }

            /**
             * Stores a speculative cache entry corresponding to given symbol
             * and resolution phase
             */
            void put(JCTree speculativeTree, ResultInfo resultInfo) {
                Symbol msym = resultInfo.checkContext.deferredAttrContext().msym;
                List<Entry> entries = cache.get(msym);
                if (entries == null) {
                    entries = List.nil();
                }
                cache.put(msym, entries.prepend(new Entry(speculativeTree, resultInfo)));
            }
        }

        /**
         * Get the type that has been computed during a speculative attribution round
         */
        Type speculativeType(Symbol msym, MethodResolutionPhase phase) {
            SpeculativeCache.Entry e = speculativeCache.get(msym, phase);
            return e != null ? e.speculativeTree.type : Type.noType;
        }

        /**
         * Check a deferred type against a potential target-type. Depending on
         * the current attribution mode, a normal vs. speculative attribution
         * round is performed on the underlying AST node. There can be only one
         * speculative round for a given target method symbol; moreover, a normal
         * attribution round must follow one or more speculative rounds.
         */
        Type check(ResultInfo resultInfo) {
            DeferredStuckPolicy deferredStuckPolicy;
            if (resultInfo.pt.hasTag(NONE) || resultInfo.pt.isErroneous()) {
                deferredStuckPolicy = dummyStuckPolicy;
            } else if (resultInfo.checkContext.deferredAttrContext().mode == AttrMode.SPECULATIVE ||
                    resultInfo.checkContext.deferredAttrContext().insideOverloadPhase()) {
                deferredStuckPolicy = new OverloadStuckPolicy(resultInfo, this);
            } else {
                deferredStuckPolicy = new CheckStuckPolicy(resultInfo, this);
            }
            return check(resultInfo, deferredStuckPolicy, basicCompleter);
        }

        private Type check(ResultInfo resultInfo, DeferredStuckPolicy deferredStuckPolicy,
                DeferredTypeCompleter deferredTypeCompleter) {
            DeferredAttrContext deferredAttrContext =
                    resultInfo.checkContext.deferredAttrContext();
            Assert.check(deferredAttrContext != emptyDeferredAttrContext);
            if (deferredStuckPolicy.isStuck()) {
                deferredAttrContext.addDeferredAttrNode(this, resultInfo, deferredStuckPolicy);
                return Type.noType;
            } else {
                try {
                    return deferredTypeCompleter.complete(this, resultInfo, deferredAttrContext);
                } finally {
                    mode = deferredAttrContext.mode;
                }
            }
        }
    }

    /**
     * A completer for deferred types. Defines an entry point for type-checking
     * a deferred type.
     */
    interface DeferredTypeCompleter {
        /**
         * Entry point for type-checking a deferred type. Depending on the
         * circumstances, type-checking could amount to full attribution
         * or partial structural check (aka potential applicability).
         */
        Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext);
    }


    /**
     * A basic completer for deferred types. This completer type-checks a deferred type
     * using attribution; depending on the attribution mode, this could be either standard
     * or speculative attribution.
     */
    DeferredTypeCompleter basicCompleter = new DeferredTypeCompleter() {
        public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            switch (deferredAttrContext.mode) {
                case SPECULATIVE:
                    //Note: if a symbol is imported twice we might do two identical
                    //speculative rounds...
                    Assert.check(dt.mode == null || dt.mode == AttrMode.SPECULATIVE);
                    JCTree speculativeTree = attribSpeculative(dt.tree, dt.env, resultInfo);
                    dt.speculativeCache.put(speculativeTree, resultInfo);
                    return speculativeTree.type;
                case CHECK:
                    Assert.check(dt.mode != null);
                    return attr.attribTree(dt.tree, dt.env, resultInfo);
            }
            Assert.error();
            return null;
        }
    };

    DeferredTypeCompleter dummyCompleter = new DeferredTypeCompleter() {
        public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            Assert.check(deferredAttrContext.mode == AttrMode.CHECK);
            return dt.tree.type = Type.stuckType;
        }
    };

    /**
     * Policy for detecting stuck expressions. Different criteria might cause
     * an expression to be judged as stuck, depending on whether the check
     * is performed during overload resolution or after most specific.
     */
    interface DeferredStuckPolicy {
        /**
         * Has the policy detected that a given expression should be considered stuck?
         */
        boolean isStuck();
        /**
         * Get the set of inference variables a given expression depends upon.
         */
        Set<Type> stuckVars();
        /**
         * Get the set of inference variables which might get new constraints
         * if a given expression is being type-checked.
         */
        Set<Type> depVars();
    }

    /**
     * Basic stuck policy; an expression is never considered to be stuck.
     */
    DeferredStuckPolicy dummyStuckPolicy = new DeferredStuckPolicy() {
        @Override
        public boolean isStuck() {
            return false;
        }
        @Override
        public Set<Type> stuckVars() {
            return Collections.emptySet();
        }
        @Override
        public Set<Type> depVars() {
            return Collections.emptySet();
        }
    };

    /**
     * The 'mode' in which the deferred type is to be type-checked
     */
    public enum AttrMode {
        /**
         * A speculative type-checking round is used during overload resolution
         * mainly to generate constraints on inference variables. Side-effects
         * arising from type-checking the expression associated with the deferred
         * type are reversed after the speculative round finishes. This means the
         * expression tree will be left in a blank state.
         */
        SPECULATIVE,
        /**
         * This is the plain type-checking mode. Produces side-effects on the underlying AST node
         */
        CHECK
    }

    /**
     * Routine that performs speculative type-checking; the input AST node is
     * cloned (to avoid side-effects cause by Attr) and compiler state is
     * restored after type-checking. All diagnostics (but critical ones) are
     * disabled during speculative type-checking.
     */
    JCTree attribSpeculative(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        return attribSpeculative(tree, env, resultInfo, new TreeCopier<>(make),
                (newTree)->new DeferredAttrDiagHandler(log, newTree));
    }

    <Z> JCTree attribSpeculative(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo, TreeCopier<Z> deferredCopier,
                                 Function<JCTree, DeferredDiagnosticHandler> diagHandlerCreator) {
        final JCTree newTree = deferredCopier.copy(tree);
        Env<AttrContext> speculativeEnv = env.dup(newTree, env.info.dup(env.info.scope.dupUnshared(env.info.scope.owner)));
        speculativeEnv.info.isSpeculative = true;
        Log.DeferredDiagnosticHandler deferredDiagnosticHandler = diagHandlerCreator.apply(newTree);
        try {
            attr.attribTree(newTree, speculativeEnv, resultInfo);
            unenterScanner.scan(newTree);
            return newTree;
        } finally {
            unenterScanner.scan(newTree);
            log.popDiagnosticHandler(deferredDiagnosticHandler);
        }
    }
    //where
        protected UnenterScanner unenterScanner = new UnenterScanner();

        class UnenterScanner extends TreeScanner {
            @Override
            public void visitClassDef(JCClassDecl tree) {
                ClassSymbol csym = tree.sym;
                //if something went wrong during method applicability check
                //it is possible that nested expressions inside argument expression
                //are left unchecked - in such cases there's nothing to clean up.
                if (csym == null) return;
                typeEnvs.remove(csym);
                chk.compiled.remove(csym.flatname);
                syms.classes.remove(csym.flatname);
                super.visitClassDef(tree);
            }
        }

        static class DeferredAttrDiagHandler extends Log.DeferredDiagnosticHandler {

            static class PosScanner extends TreeScanner {
                DiagnosticPosition pos;
                boolean found = false;

                PosScanner(DiagnosticPosition pos) {
                    this.pos = pos;
                }

                @Override
                public void scan(JCTree tree) {
                    if (tree != null &&
                            tree.pos() == pos) {
                        found = true;
                    }
                    super.scan(tree);
                }
            }

            DeferredAttrDiagHandler(Log log, JCTree newTree) {
                super(log, new Filter<JCDiagnostic>() {
                    public boolean accepts(JCDiagnostic d) {
                        PosScanner posScanner = new PosScanner(d.getDiagnosticPosition());
                        posScanner.scan(newTree);
                        return posScanner.found;
                    }
                });
            }
        }

    /**
     * A deferred context is created on each method check. A deferred context is
     * used to keep track of information associated with the method check, such as
     * the symbol of the method being checked, the overload resolution phase,
     * the kind of attribution mode to be applied to deferred types and so forth.
     * As deferred types are processed (by the method check routine) stuck AST nodes
     * are added (as new deferred attribution nodes) to this context. The complete()
     * routine makes sure that all pending nodes are properly processed, by
     * progressively instantiating all inference variables on which one or more
     * deferred attribution node is stuck.
     */
    class DeferredAttrContext {

        /** attribution mode */
        final AttrMode mode;

        /** symbol of the method being checked */
        final Symbol msym;

        /** method resolution step */
        final Resolve.MethodResolutionPhase phase;

        /** inference context */
        final InferenceContext inferenceContext;

        /** parent deferred context */
        final DeferredAttrContext parent;

        /** Warner object to report warnings */
        final Warner warn;

        /** list of deferred attribution nodes to be processed */
        ArrayList<DeferredAttrNode> deferredAttrNodes = new ArrayList<>();

        DeferredAttrContext(AttrMode mode, Symbol msym, MethodResolutionPhase phase,
                InferenceContext inferenceContext, DeferredAttrContext parent, Warner warn) {
            this.mode = mode;
            this.msym = msym;
            this.phase = phase;
            this.parent = parent;
            this.warn = warn;
            this.inferenceContext = inferenceContext;
        }

        /**
         * Adds a node to the list of deferred attribution nodes - used by Resolve.rawCheckArgumentsApplicable
         * Nodes added this way act as 'roots' for the out-of-order method checking process.
         */
        void addDeferredAttrNode(final DeferredType dt, ResultInfo resultInfo,
                DeferredStuckPolicy deferredStuckPolicy) {
            deferredAttrNodes.add(new DeferredAttrNode(dt, resultInfo, deferredStuckPolicy));
        }

        /**
         * Incrementally process all nodes, by skipping 'stuck' nodes and attributing
         * 'unstuck' ones. If at any point no progress can be made (no 'unstuck' nodes)
         * some inference variable might get eagerly instantiated so that all nodes
         * can be type-checked.
         */
        void complete() {
            while (!deferredAttrNodes.isEmpty()) {
                Map<Type, Set<Type>> depVarsMap = new LinkedHashMap<>();
                List<Type> stuckVars = List.nil();
                boolean progress = false;
                //scan a defensive copy of the node list - this is because a deferred
                //attribution round can add new nodes to the list
                for (DeferredAttrNode deferredAttrNode : List.from(deferredAttrNodes)) {
                    if (!deferredAttrNode.process(this)) {
                        List<Type> restStuckVars =
                                List.from(deferredAttrNode.deferredStuckPolicy.stuckVars())
                                .intersect(inferenceContext.restvars());
                        stuckVars = stuckVars.prependList(restStuckVars);
                        //update dependency map
                        for (Type t : List.from(deferredAttrNode.deferredStuckPolicy.depVars())
                                .intersect(inferenceContext.restvars())) {
                            Set<Type> prevDeps = depVarsMap.get(t);
                            if (prevDeps == null) {
                                prevDeps = new LinkedHashSet<>();
                                depVarsMap.put(t, prevDeps);
                            }
                            prevDeps.addAll(restStuckVars);
                        }
                    } else {
                        deferredAttrNodes.remove(deferredAttrNode);
                        progress = true;
                    }
                }
                if (!progress) {
                    if (insideOverloadPhase()) {
                        for (DeferredAttrNode deferredNode: deferredAttrNodes) {
                            deferredNode.dt.tree.type = Type.noType;
                        }
                        return;
                    }
                    //remove all variables that have already been instantiated
                    //from the list of stuck variables
                    try {
                        inferenceContext.solveAny(stuckVars, depVarsMap, warn);
                        inferenceContext.notifyChange();
                    } catch (Infer.GraphStrategy.NodeNotFoundException ex) {
                        //this means that we are in speculative mode and the
                        //set of contraints are too tight for progess to be made.
                        //Just leave the remaining expressions as stuck.
                        break;
                    }
                }
            }
        }

        private boolean insideOverloadPhase() {
            DeferredAttrContext dac = this;
            if (dac == emptyDeferredAttrContext) {
                return false;
            }
            if (dac.mode == AttrMode.SPECULATIVE) {
                return true;
            }
            return dac.parent.insideOverloadPhase();
        }
    }

    /**
     * Class representing a deferred attribution node. It keeps track of
     * a deferred type, along with the expected target type information.
     */
    class DeferredAttrNode {

        /** underlying deferred type */
        DeferredType dt;

        /** underlying target type information */
        ResultInfo resultInfo;

        /** stuck policy associated with this node */
        DeferredStuckPolicy deferredStuckPolicy;

        DeferredAttrNode(DeferredType dt, ResultInfo resultInfo, DeferredStuckPolicy deferredStuckPolicy) {
            this.dt = dt;
            this.resultInfo = resultInfo;
            this.deferredStuckPolicy = deferredStuckPolicy;
        }

        /**
         * Process a deferred attribution node.
         * Invariant: a stuck node cannot be processed.
         */
        @SuppressWarnings("fallthrough")
        boolean process(final DeferredAttrContext deferredAttrContext) {
            switch (deferredAttrContext.mode) {
                case SPECULATIVE:
                    if (deferredStuckPolicy.isStuck()) {
                        dt.check(resultInfo, dummyStuckPolicy, new StructuralStuckChecker());
                        return true;
                    } else {
                        Assert.error("Cannot get here");
                    }
                case CHECK:
                    if (deferredStuckPolicy.isStuck()) {
                        //stuck expression - see if we can propagate
                        if (deferredAttrContext.parent != emptyDeferredAttrContext &&
                                Type.containsAny(deferredAttrContext.parent.inferenceContext.inferencevars,
                                        List.from(deferredStuckPolicy.stuckVars()))) {
                            deferredAttrContext.parent.addDeferredAttrNode(dt,
                                    resultInfo.dup(new Check.NestedCheckContext(resultInfo.checkContext) {
                                @Override
                                public InferenceContext inferenceContext() {
                                    return deferredAttrContext.parent.inferenceContext;
                                }
                                @Override
                                public DeferredAttrContext deferredAttrContext() {
                                    return deferredAttrContext.parent;
                                }
                            }), deferredStuckPolicy);
                            dt.tree.type = Type.stuckType;
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        Assert.check(!deferredAttrContext.insideOverloadPhase(),
                                "attribution shouldn't be happening here");
                        ResultInfo instResultInfo =
                                resultInfo.dup(deferredAttrContext.inferenceContext.asInstType(resultInfo.pt));
                        dt.check(instResultInfo, dummyStuckPolicy, basicCompleter);
                        return true;
                    }
                default:
                    throw new AssertionError("Bad mode");
            }
        }

        /**
         * Structural checker for stuck expressions
         */
        class StructuralStuckChecker extends TreeScanner implements DeferredTypeCompleter {

            ResultInfo resultInfo;
            InferenceContext inferenceContext;
            Env<AttrContext> env;

            public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
                this.resultInfo = resultInfo;
                this.inferenceContext = deferredAttrContext.inferenceContext;
                this.env = dt.env;
                dt.tree.accept(this);
                dt.speculativeCache.put(stuckTree, resultInfo);
                return Type.noType;
            }

            @Override
            public void visitLambda(JCLambda tree) {
                Check.CheckContext checkContext = resultInfo.checkContext;
                Type pt = resultInfo.pt;
                if (!inferenceContext.inferencevars.contains(pt)) {
                    //must be a functional descriptor
                    Type descriptorType = null;
                    try {
                        descriptorType = types.findDescriptorType(pt);
                    } catch (Types.FunctionDescriptorLookupError ex) {
                        checkContext.report(null, ex.getDiagnostic());
                    }

                    if (descriptorType.getParameterTypes().length() != tree.params.length()) {
                        checkContext.report(tree,
                                diags.fragment("incompatible.arg.types.in.lambda"));
                    }

                    Type currentReturnType = descriptorType.getReturnType();
                    boolean returnTypeIsVoid = currentReturnType.hasTag(VOID);
                    if (tree.getBodyKind() == BodyKind.EXPRESSION) {
                        boolean isExpressionCompatible = !returnTypeIsVoid ||
                            TreeInfo.isExpressionStatement((JCExpression)tree.getBody());
                        if (!isExpressionCompatible) {
                            resultInfo.checkContext.report(tree.pos(),
                                diags.fragment("incompatible.ret.type.in.lambda",
                                    diags.fragment("missing.ret.val", currentReturnType)));
                        }
                    } else {
                        LambdaBodyStructChecker lambdaBodyChecker =
                                new LambdaBodyStructChecker();

                        tree.body.accept(lambdaBodyChecker);
                        boolean isVoidCompatible = lambdaBodyChecker.isVoidCompatible;

                        if (returnTypeIsVoid) {
                            if (!isVoidCompatible) {
                                resultInfo.checkContext.report(tree.pos(),
                                    diags.fragment("unexpected.ret.val"));
                            }
                        } else {
                            boolean isValueCompatible = lambdaBodyChecker.isPotentiallyValueCompatible
                                && !canLambdaBodyCompleteNormally(tree);
                            if (!isValueCompatible && !isVoidCompatible) {
                                log.error(tree.body.pos(),
                                    "lambda.body.neither.value.nor.void.compatible");
                            }

                            if (!isValueCompatible) {
                                resultInfo.checkContext.report(tree.pos(),
                                    diags.fragment("incompatible.ret.type.in.lambda",
                                        diags.fragment("missing.ret.val", currentReturnType)));
                            }
                        }
                    }
                }
            }

            boolean canLambdaBodyCompleteNormally(JCLambda tree) {
                JCLambda newTree = new TreeCopier<>(make).copy(tree);
                /* attr.lambdaEnv will create a meaningful env for the
                 * lambda expression. This is specially useful when the
                 * lambda is used as the init of a field. But we need to
                 * remove any added symbol.
                 */
                Env<AttrContext> localEnv = attr.lambdaEnv(newTree, env);
                try {
                    List<JCVariableDecl> tmpParams = newTree.params;
                    while (tmpParams.nonEmpty()) {
                        tmpParams.head.vartype = make.at(tmpParams.head).Type(syms.errType);
                        tmpParams = tmpParams.tail;
                    }

                    attr.attribStats(newTree.params, localEnv);

                    /* set pt to Type.noType to avoid generating any bound
                     * which may happen if lambda's return type is an
                     * inference variable
                     */
                    Attr.ResultInfo bodyResultInfo = attr.new ResultInfo(KindSelector.VAL, Type.noType);
                    localEnv.info.returnResult = bodyResultInfo;

                    // discard any log output
                    Log.DiagnosticHandler diagHandler = new Log.DiscardDiagnosticHandler(log);
                    try {
                        JCBlock body = (JCBlock)newTree.body;
                        /* we need to attribute the lambda body before
                         * doing the aliveness analysis. This is because
                         * constant folding occurs during attribution
                         * and the reachability of some statements depends
                         * on constant values, for example:
                         *
                         *     while (true) {...}
                         */
                        attr.attribStats(body.stats, localEnv);

                        attr.preFlow(newTree);
                        /* make an aliveness / reachability analysis of the lambda
                         * to determine if it can complete normally
                         */
                        flow.analyzeLambda(localEnv, newTree, make, true);
                    } finally {
                        log.popDiagnosticHandler(diagHandler);
                    }
                    return newTree.canCompleteNormally;
                } finally {
                    JCBlock body = (JCBlock)newTree.body;
                    unenterScanner.scan(body.stats);
                    localEnv.info.scope.leave();
                }
            }

            @Override
            public void visitNewClass(JCNewClass tree) {
                //do nothing
            }

            @Override
            public void visitApply(JCMethodInvocation tree) {
                //do nothing
            }

            @Override
            public void visitReference(JCMemberReference tree) {
                Check.CheckContext checkContext = resultInfo.checkContext;
                Type pt = resultInfo.pt;
                if (!inferenceContext.inferencevars.contains(pt)) {
                    try {
                        types.findDescriptorType(pt);
                    } catch (Types.FunctionDescriptorLookupError ex) {
                        checkContext.report(null, ex.getDiagnostic());
                    }
                    Env<AttrContext> localEnv = env.dup(tree);
                    JCExpression exprTree = (JCExpression)attribSpeculative(tree.getQualifierExpression(), localEnv,
                            attr.memberReferenceQualifierResult(tree));
                    ListBuffer<Type> argtypes = new ListBuffer<>();
                    for (Type t : types.findDescriptorType(pt).getParameterTypes()) {
                        argtypes.append(Type.noType);
                    }
                    JCMemberReference mref2 = new TreeCopier<Void>(make).copy(tree);
                    mref2.expr = exprTree;
                    Symbol lookupSym =
                            rs.resolveMemberReference(localEnv, mref2, exprTree.type,
                                    tree.name, argtypes.toList(), List.nil(), rs.arityMethodCheck,
                                    inferenceContext, rs.structuralReferenceChooser).fst;
                    switch (lookupSym.kind) {
                        case WRONG_MTH:
                        case WRONG_MTHS:
                            //note: as argtypes are erroneous types, type-errors must
                            //have been caused by arity mismatch
                            checkContext.report(tree, diags.fragment(Fragments.IncompatibleArgTypesInMref));
                            break;
                        case ABSENT_MTH:
                        case STATICERR:
                            //if no method found, or method found with wrong staticness, report better message
                            checkContext.report(tree, ((ResolveError)lookupSym).getDiagnostic(DiagnosticType.FRAGMENT,
                                    tree, exprTree.type.tsym, exprTree.type, tree.name, argtypes.toList(), List.nil()));
                            break;
                    }
                }
            }
        }

        /* This visitor looks for return statements, its analysis will determine if
         * a lambda body is void or value compatible. We must analyze return
         * statements contained in the lambda body only, thus any return statement
         * contained in an inner class or inner lambda body, should be ignored.
         */
        class LambdaBodyStructChecker extends TreeScanner {
            boolean isVoidCompatible = true;
            boolean isPotentiallyValueCompatible = true;

            @Override
            public void visitClassDef(JCClassDecl tree) {
                // do nothing
            }

            @Override
            public void visitLambda(JCLambda tree) {
                // do nothing
            }

            @Override
            public void visitNewClass(JCNewClass tree) {
                // do nothing
            }

            @Override
            public void visitReturn(JCReturn tree) {
                if (tree.expr != null) {
                    isVoidCompatible = false;
                } else {
                    isPotentiallyValueCompatible = false;
                }
            }
        }
    }

    /** an empty deferred attribution context - all methods throw exceptions */
    final DeferredAttrContext emptyDeferredAttrContext;

    /** The AttrMode to descriptive name mapping */
    private static final EnumMap<AttrMode, String> deferredTypeMapDescriptions;
    static {
        deferredTypeMapDescriptions = new EnumMap<>(AttrMode.class);
        deferredTypeMapDescriptions.put(AttrMode.CHECK, "deferredTypeMap[CHECK]");
        deferredTypeMapDescriptions.put(AttrMode.SPECULATIVE, "deferredTypeMap[SPECULATIVE]");
    }

    /**
     * Map a list of types possibly containing one or more deferred types
     * into a list of ordinary types. Each deferred type D is mapped into a type T,
     * where T is computed by retrieving the type that has already been
     * computed for D during a previous deferred attribution round of the given kind.
     */
    class DeferredTypeMap extends Type.Mapping {
        DeferredAttrContext deferredAttrContext;

        protected DeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(deferredTypeMapDescriptions.get(mode));
            this.deferredAttrContext = new DeferredAttrContext(mode, msym, phase,
                    infer.emptyContext, emptyDeferredAttrContext, types.noWarnings);
        }

        @Override
        public Type apply(Type t) {
            if (!t.hasTag(DEFERRED)) {
                return t.map(this);
            } else {
                DeferredType dt = (DeferredType)t;
                return typeOf(dt);
            }
        }

        protected Type typeOf(DeferredType dt) {
            switch (deferredAttrContext.mode) {
                case CHECK:
                    return dt.tree.type == null ? Type.noType : dt.tree.type;
                case SPECULATIVE:
                    return dt.speculativeType(deferredAttrContext.msym, deferredAttrContext.phase);
            }
            Assert.error();
            return null;
        }
    }

    /**
     * Specialized recovery deferred mapping.
     * Each deferred type D is mapped into a type T, where T is computed either by
     * (i) retrieving the type that has already been computed for D during a previous
     * attribution round (as before), or (ii) by synthesizing a new type R for D
     * (the latter step is useful in a recovery scenario).
     */
    public class RecoveryDeferredTypeMap extends DeferredTypeMap {

        public RecoveryDeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(mode, msym, phase != null ? phase : MethodResolutionPhase.BOX);
        }

        @Override
        protected Type typeOf(DeferredType dt) {
            Type owntype = super.typeOf(dt);
            return owntype == Type.noType ?
                        recover(dt) : owntype;
        }

        /**
         * Synthesize a type for a deferred type that hasn't been previously
         * reduced to an ordinary type. Functional deferred types and conditionals
         * are mapped to themselves, in order to have a richer diagnostic
         * representation. Remaining deferred types are attributed using
         * a default expected type (j.l.Object).
         */
        private Type recover(DeferredType dt) {
            dt.check(attr.new RecoveryInfo(deferredAttrContext) {
                @Override
                protected Type check(DiagnosticPosition pos, Type found) {
                    return chk.checkNonVoid(pos, super.check(pos, found));
                }
            });
            return super.apply(dt);
        }
    }

    /**
     * A special tree scanner that would only visit portions of a given tree.
     * The set of nodes visited by the scanner can be customized at construction-time.
     */
    abstract static class FilterScanner extends TreeScanner {

        final Filter<JCTree> treeFilter;

        FilterScanner(final Set<JCTree.Tag> validTags) {
            this.treeFilter = new Filter<JCTree>() {
                public boolean accepts(JCTree t) {
                    return validTags.contains(t.getTag());
                }
            };
        }

        @Override
        public void scan(JCTree tree) {
            if (tree != null) {
                if (treeFilter.accepts(tree)) {
                    super.scan(tree);
                } else {
                    skip(tree);
                }
            }
        }

        /**
         * handler that is executed when a node has been discarded
         */
        void skip(JCTree tree) {}
    }

    /**
     * A tree scanner suitable for visiting the target-type dependent nodes of
     * a given argument expression.
     */
    static class PolyScanner extends FilterScanner {

        PolyScanner() {
            super(EnumSet.of(CONDEXPR, PARENS, LAMBDA, REFERENCE));
        }
    }

    /**
     * A tree scanner suitable for visiting the target-type dependent nodes nested
     * within a lambda expression body.
     */
    static class LambdaReturnScanner extends FilterScanner {

        LambdaReturnScanner() {
            super(EnumSet.of(BLOCK, CASE, CATCH, DOLOOP, FOREACHLOOP,
                    FORLOOP, IF, RETURN, SYNCHRONIZED, SWITCH, TRY, WHILELOOP));
        }
    }

    /**
     * This visitor is used to check that structural expressions conform
     * to their target - this step is required as inference could end up
     * inferring types that make some of the nested expressions incompatible
     * with their corresponding instantiated target
     */
    class CheckStuckPolicy extends PolyScanner implements DeferredStuckPolicy, Infer.FreeTypeListener {

        Type pt;
        Infer.InferenceContext inferenceContext;
        Set<Type> stuckVars = new LinkedHashSet<>();
        Set<Type> depVars = new LinkedHashSet<>();

        @Override
        public boolean isStuck() {
            return !stuckVars.isEmpty();
        }

        @Override
        public Set<Type> stuckVars() {
            return stuckVars;
        }

        @Override
        public Set<Type> depVars() {
            return depVars;
        }

        public CheckStuckPolicy(ResultInfo resultInfo, DeferredType dt) {
            this.pt = resultInfo.pt;
            this.inferenceContext = resultInfo.checkContext.inferenceContext();
            scan(dt.tree);
            if (!stuckVars.isEmpty()) {
                resultInfo.checkContext.inferenceContext()
                        .addFreeTypeListener(List.from(stuckVars), this);
            }
        }

        @Override
        public void typesInferred(InferenceContext inferenceContext) {
            stuckVars.clear();
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (inferenceContext.inferenceVars().contains(pt)) {
                stuckVars.add(pt);
            }
            if (!types.isFunctionalInterface(pt)) {
                return;
            }
            Type descType = types.findDescriptorType(pt);
            List<Type> freeArgVars = inferenceContext.freeVarsIn(descType.getParameterTypes());
            if (tree.paramKind == JCLambda.ParameterKind.IMPLICIT &&
                    freeArgVars.nonEmpty()) {
                stuckVars.addAll(freeArgVars);
                depVars.addAll(inferenceContext.freeVarsIn(descType.getReturnType()));
            }
            scanLambdaBody(tree, descType.getReturnType());
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            scan(tree.expr);
            if (inferenceContext.inferenceVars().contains(pt)) {
                stuckVars.add(pt);
                return;
            }
            if (!types.isFunctionalInterface(pt)) {
                return;
            }

            Type descType = types.findDescriptorType(pt);
            List<Type> freeArgVars = inferenceContext.freeVarsIn(descType.getParameterTypes());
            if (freeArgVars.nonEmpty() &&
                    tree.overloadKind == JCMemberReference.OverloadKind.OVERLOADED) {
                stuckVars.addAll(freeArgVars);
                depVars.addAll(inferenceContext.freeVarsIn(descType.getReturnType()));
            }
        }

        void scanLambdaBody(JCLambda lambda, final Type pt) {
            if (lambda.getBodyKind() == JCTree.JCLambda.BodyKind.EXPRESSION) {
                Type prevPt = this.pt;
                try {
                    this.pt = pt;
                    scan(lambda.body);
                } finally {
                    this.pt = prevPt;
                }
            } else {
                LambdaReturnScanner lambdaScanner = new LambdaReturnScanner() {
                    @Override
                    public void visitReturn(JCReturn tree) {
                        if (tree.expr != null) {
                            Type prevPt = CheckStuckPolicy.this.pt;
                            try {
                                CheckStuckPolicy.this.pt = pt;
                                CheckStuckPolicy.this.scan(tree.expr);
                            } finally {
                                CheckStuckPolicy.this.pt = prevPt;
                            }
                        }
                    }
                };
                lambdaScanner.scan(lambda.body);
            }
        }
    }

    /**
     * This visitor is used to check that structural expressions conform
     * to their target - this step is required as inference could end up
     * inferring types that make some of the nested expressions incompatible
     * with their corresponding instantiated target
     */
    class OverloadStuckPolicy extends CheckStuckPolicy implements DeferredStuckPolicy {

        boolean stuck;

        @Override
        public boolean isStuck() {
            return super.isStuck() || stuck;
        }

        public OverloadStuckPolicy(ResultInfo resultInfo, DeferredType dt) {
            super(resultInfo, dt);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            super.visitLambda(tree);
            if (tree.paramKind == JCLambda.ParameterKind.IMPLICIT) {
                stuck = true;
            }
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            super.visitReference(tree);
            if (tree.overloadKind == JCMemberReference.OverloadKind.OVERLOADED) {
                stuck = true;
            }
        }
    }

    /**
     * Does the argument expression {@code expr} need speculative type-checking?
     */
    boolean isDeferred(Env<AttrContext> env, JCExpression expr) {
        DeferredChecker dc = new DeferredChecker(env);
        dc.scan(expr);
        return dc.result.isPoly();
    }

    /**
     * The kind of an argument expression. This is used by the analysis that
     * determines as to whether speculative attribution is necessary.
     */
    enum ArgumentExpressionKind {

        /** kind that denotes poly argument expression */
        POLY,
        /** kind that denotes a standalone expression */
        NO_POLY,
        /** kind that denotes a primitive/boxed standalone expression */
        PRIMITIVE;

        /**
         * Does this kind denote a poly argument expression
         */
        public final boolean isPoly() {
            return this == POLY;
        }

        /**
         * Does this kind denote a primitive standalone expression
         */
        public final boolean isPrimitive() {
            return this == PRIMITIVE;
        }

        /**
         * Compute the kind of a standalone expression of a given type
         */
        static ArgumentExpressionKind standaloneKind(Type type, Types types) {
            return types.unboxedTypeOrType(type).isPrimitive() ?
                    ArgumentExpressionKind.PRIMITIVE :
                    ArgumentExpressionKind.NO_POLY;
        }

        /**
         * Compute the kind of a method argument expression given its symbol
         */
        static ArgumentExpressionKind methodKind(Symbol sym, Types types) {
            Type restype = sym.type.getReturnType();
            if (sym.type.hasTag(FORALL) &&
                    restype.containsAny(((ForAll)sym.type).tvars)) {
                return ArgumentExpressionKind.POLY;
            } else {
                return ArgumentExpressionKind.standaloneKind(restype, types);
            }
        }
    }

    /**
     * Tree scanner used for checking as to whether an argument expression
     * requires speculative attribution
     */
    final class DeferredChecker extends FilterScanner {

        Env<AttrContext> env;
        ArgumentExpressionKind result;

        public DeferredChecker(Env<AttrContext> env) {
            super(deferredCheckerTags);
            this.env = env;
        }

        @Override
        public void visitLambda(JCLambda tree) {
            //a lambda is always a poly expression
            result = ArgumentExpressionKind.POLY;
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            //perform arity-based check
            Env<AttrContext> localEnv = env.dup(tree);
            JCExpression exprTree = (JCExpression)attribSpeculative(tree.getQualifierExpression(), localEnv,
                    attr.memberReferenceQualifierResult(tree));
            JCMemberReference mref2 = new TreeCopier<Void>(make).copy(tree);
            mref2.expr = exprTree;
            Symbol res =
                    rs.getMemberReference(tree, localEnv, mref2,
                        exprTree.type, tree.name);
            tree.sym = res;
            if (res.kind.isOverloadError() ||
                    res.type.hasTag(FORALL) ||
                    (res.flags() & Flags.VARARGS) != 0 ||
                    (TreeInfo.isStaticSelector(exprTree, tree.name.table.names) &&
                    exprTree.type.isRaw())) {
                tree.overloadKind = JCMemberReference.OverloadKind.OVERLOADED;
            } else {
                tree.overloadKind = JCMemberReference.OverloadKind.UNOVERLOADED;
            }
            //a method reference is always a poly expression
            result = ArgumentExpressionKind.POLY;
        }

        @Override
        public void visitTypeCast(JCTypeCast tree) {
            //a cast is always a standalone expression
            result = ArgumentExpressionKind.NO_POLY;
        }

        @Override
        public void visitConditional(JCConditional tree) {
            scan(tree.truepart);
            if (!result.isPrimitive()) {
                result = ArgumentExpressionKind.POLY;
                return;
            }
            scan(tree.falsepart);
            result = reduce(ArgumentExpressionKind.PRIMITIVE).isPrimitive() ?
                    ArgumentExpressionKind.PRIMITIVE :
                    ArgumentExpressionKind.POLY;

        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            result = TreeInfo.isDiamond(tree) ?
                    ArgumentExpressionKind.POLY : ArgumentExpressionKind.NO_POLY;
        }

        @Override
        public void visitApply(JCMethodInvocation tree) {
            Name name = TreeInfo.name(tree.meth);

            //fast path
            if (tree.typeargs.nonEmpty() ||
                    name == name.table.names._this ||
                    name == name.table.names._super) {
                result = ArgumentExpressionKind.NO_POLY;
                return;
            }

            //slow path
            Symbol sym = quicklyResolveMethod(env, tree);

            if (sym == null) {
                result = ArgumentExpressionKind.POLY;
                return;
            }

            result = analyzeCandidateMethods(sym, ArgumentExpressionKind.PRIMITIVE,
                    argumentKindAnalyzer);
        }
        //where
            private boolean isSimpleReceiver(JCTree rec) {
                switch (rec.getTag()) {
                    case IDENT:
                        return true;
                    case SELECT:
                        return isSimpleReceiver(((JCFieldAccess)rec).selected);
                    case TYPEAPPLY:
                    case TYPEARRAY:
                        return true;
                    case ANNOTATED_TYPE:
                        return isSimpleReceiver(((JCAnnotatedType)rec).underlyingType);
                    case APPLY:
                        return true;
                    default:
                        return false;
                }
            }
            private ArgumentExpressionKind reduce(ArgumentExpressionKind kind) {
                return argumentKindAnalyzer.reduce(result, kind);
            }
            MethodAnalyzer<ArgumentExpressionKind> argumentKindAnalyzer =
                    new MethodAnalyzer<ArgumentExpressionKind>() {
                @Override
                public ArgumentExpressionKind process(MethodSymbol ms) {
                    return ArgumentExpressionKind.methodKind(ms, types);
                }
                @Override
                public ArgumentExpressionKind reduce(ArgumentExpressionKind kind1,
                                                     ArgumentExpressionKind kind2) {
                    switch (kind1) {
                        case PRIMITIVE: return kind2;
                        case NO_POLY: return kind2.isPoly() ? kind2 : kind1;
                        case POLY: return kind1;
                        default:
                            Assert.error();
                            return null;
                    }
                }
                @Override
                public boolean shouldStop(ArgumentExpressionKind result) {
                    return result.isPoly();
                }
            };

        @Override
        public void visitLiteral(JCLiteral tree) {
            Type litType = attr.litType(tree.typetag);
            result = ArgumentExpressionKind.standaloneKind(litType, types);
        }

        @Override
        void skip(JCTree tree) {
            result = ArgumentExpressionKind.NO_POLY;
        }

        private Symbol quicklyResolveMethod(Env<AttrContext> env, final JCMethodInvocation tree) {
            final JCExpression rec = tree.meth.hasTag(SELECT) ?
                    ((JCFieldAccess)tree.meth).selected :
                    null;

            if (rec != null && !isSimpleReceiver(rec)) {
                return null;
            }

            Type site;

            if (rec != null) {
                if (rec.hasTag(APPLY)) {
                    Symbol recSym = quicklyResolveMethod(env, (JCMethodInvocation) rec);
                    if (recSym == null)
                        return null;
                    Symbol resolvedReturnType =
                            analyzeCandidateMethods(recSym, syms.errSymbol, returnSymbolAnalyzer);
                    if (resolvedReturnType == null)
                        return null;
                    site = resolvedReturnType.type;
                } else {
                    site = attribSpeculative(rec, env, attr.unknownTypeExprInfo).type;
                }
            } else {
                site = env.enclClass.sym.type;
            }

            while (site.hasTag(TYPEVAR)) {
                site = site.getUpperBound();
            }

            site = types.capture(site);

            List<Type> args = rs.dummyArgs(tree.args.length());
            Name name = TreeInfo.name(tree.meth);

            Resolve.LookupHelper lh = rs.new LookupHelper(name, site, args, List.<Type>nil(), MethodResolutionPhase.VARARITY) {
                @Override
                Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                    return rec == null ?
                        rs.findFun(env, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired()) :
                        rs.findMethod(env, site, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
                }
                @Override
                Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                    return sym;
                }
            };

            return rs.lookupMethod(env, tree, site.tsym, rs.arityMethodCheck, lh);
        }
        //where:
            MethodAnalyzer<Symbol> returnSymbolAnalyzer = new MethodAnalyzer<Symbol>() {
                @Override
                public Symbol process(MethodSymbol ms) {
                    ArgumentExpressionKind kind = ArgumentExpressionKind.methodKind(ms, types);
                    if (kind == ArgumentExpressionKind.POLY || ms.getReturnType().hasTag(TYPEVAR))
                        return null;
                    return ms.getReturnType().tsym;
                }
                @Override
                public Symbol reduce(Symbol s1, Symbol s2) {
                    return s1 == syms.errSymbol ? s2 : s1 == s2 ? s1 : null;
                }
                @Override
                public boolean shouldStop(Symbol result) {
                    return result == null;
                }
            };

        /**
         * Process the result of Resolve.lookupMethod. If sym is a method symbol, the result of
         * MethodAnalyzer.process is returned. If sym is an ambiguous symbol, all the candidate
         * methods are inspected one by one, using MethodAnalyzer.process. The outcomes are
         * reduced using MethodAnalyzer.reduce (using defaultValue as the first value over which
         * the reduction runs). MethodAnalyzer.shouldStop can be used to stop the inspection early.
         */
        <E> E analyzeCandidateMethods(Symbol sym, E defaultValue, MethodAnalyzer<E> analyzer) {
            switch (sym.kind) {
                case MTH:
                    return analyzer.process((MethodSymbol) sym);
                case AMBIGUOUS:
                    Resolve.AmbiguityError err = (Resolve.AmbiguityError)sym.baseSymbol();
                    E res = defaultValue;
                    for (Symbol s : err.ambiguousSyms) {
                        if (s.kind == MTH) {
                            res = analyzer.reduce(res, analyzer.process((MethodSymbol) s));
                            if (analyzer.shouldStop(res))
                                return res;
                        }
                    }
                    return res;
                default:
                    return defaultValue;
            }
        }
    }

    /** Analyzer for methods - used by analyzeCandidateMethods. */
    interface MethodAnalyzer<E> {
        E process(MethodSymbol ms);
        E reduce(E e1, E e2);
        boolean shouldStop(E result);
    }

    //where
    private EnumSet<JCTree.Tag> deferredCheckerTags =
            EnumSet.of(LAMBDA, REFERENCE, PARENS, TYPECAST,
                    CONDEXPR, NEWCLASS, APPLY, LITERAL);
}
