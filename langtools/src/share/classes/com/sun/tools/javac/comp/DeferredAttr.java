/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.Attr.ResultInfo;
import com.sun.tools.javac.comp.Infer.InferenceContext;
import com.sun.tools.javac.comp.Resolve.MethodResolutionPhase;
import com.sun.tools.javac.comp.Resolve.ReferenceLookupHelper;
import com.sun.tools.javac.tree.JCTree.*;


import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

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
    protected static final Context.Key<DeferredAttr> deferredAttrKey =
        new Context.Key<DeferredAttr>();

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
        Names names = Names.instance(context);
        stuckTree = make.Ident(names.empty).setType(Type.stuckType);
        emptyDeferredAttrContext =
            new DeferredAttrContext(AttrMode.CHECK, null, MethodResolutionPhase.BOX, infer.emptyContext, null, null) {
                @Override
                void addDeferredAttrNode(DeferredType dt, ResultInfo ri, List<Type> stuckVars) {
                    Assert.error("Empty deferred context!");
                }
                @Override
                void complete() {
                    Assert.error("Empty deferred context!");
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
            super(null);
            this.tree = tree;
            this.env = env.dup(tree, env.info.dup());
            this.speculativeCache = new SpeculativeCache();
        }

        @Override
        public TypeTag getTag() {
            return DEFERRED;
        }

        /**
         * A speculative cache is used to keep track of all overload resolution rounds
         * that triggered speculative attribution on a given deferred type. Each entry
         * stores a pointer to the speculative tree and the resolution phase in which the entry
         * has been added.
         */
        class SpeculativeCache {

            private Map<Symbol, List<Entry>> cache =
                    new WeakHashMap<Symbol, List<Entry>>();

            class Entry {
                JCTree speculativeTree;
                Resolve.MethodResolutionPhase phase;

                public Entry(JCTree speculativeTree, MethodResolutionPhase phase) {
                    this.speculativeTree = speculativeTree;
                    this.phase = phase;
                }

                boolean matches(Resolve.MethodResolutionPhase phase) {
                    return this.phase == phase;
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
            void put(Symbol msym, JCTree speculativeTree, MethodResolutionPhase phase) {
                List<Entry> entries = cache.get(msym);
                if (entries == null) {
                    entries = List.nil();
                }
                cache.put(msym, entries.prepend(new Entry(speculativeTree, phase)));
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
            return check(resultInfo, stuckVars(tree, env, resultInfo), basicCompleter);
        }

        Type check(ResultInfo resultInfo, List<Type> stuckVars, DeferredTypeCompleter deferredTypeCompleter) {
            DeferredAttrContext deferredAttrContext =
                    resultInfo.checkContext.deferredAttrContext();
            Assert.check(deferredAttrContext != emptyDeferredAttrContext);
            if (stuckVars.nonEmpty()) {
                deferredAttrContext.addDeferredAttrNode(this, resultInfo, stuckVars);
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
                    dt.speculativeCache.put(deferredAttrContext.msym, speculativeTree, deferredAttrContext.phase);
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
            return dt.tree.type = Type.noType;
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
        CHECK;
    }

    /**
     * Routine that performs speculative type-checking; the input AST node is
     * cloned (to avoid side-effects cause by Attr) and compiler state is
     * restored after type-checking. All diagnostics (but critical ones) are
     * disabled during speculative type-checking.
     */
    JCTree attribSpeculative(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        final JCTree newTree = new TreeCopier<Object>(make).copy(tree);
        Env<AttrContext> speculativeEnv = env.dup(newTree, env.info.dup(env.info.scope.dupUnshared()));
        speculativeEnv.info.scope.owner = env.info.scope.owner;
        Log.DeferredDiagnosticHandler deferredDiagnosticHandler =
                new Log.DeferredDiagnosticHandler(log, new Filter<JCDiagnostic>() {
            public boolean accepts(final JCDiagnostic d) {
                class PosScanner extends TreeScanner {
                    boolean found = false;

                    @Override
                    public void scan(JCTree tree) {
                        if (tree != null &&
                                tree.pos() == d.getDiagnosticPosition()) {
                            found = true;
                        }
                        super.scan(tree);
                    }
                };
                PosScanner posScanner = new PosScanner();
                posScanner.scan(newTree);
                return posScanner.found;
            }
        });
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
        protected TreeScanner unenterScanner = new TreeScanner() {
            @Override
            public void visitClassDef(JCClassDecl tree) {
                ClassSymbol csym = tree.sym;
                //if something went wrong during method applicability check
                //it is possible that nested expressions inside argument expression
                //are left unchecked - in such cases there's nothing to clean up.
                if (csym == null) return;
                enter.typeEnvs.remove(csym);
                chk.compiled.remove(csym.flatname);
                syms.classes.remove(csym.flatname);
                super.visitClassDef(tree);
            }
        };

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
        ArrayList<DeferredAttrNode> deferredAttrNodes = new ArrayList<DeferredAttrNode>();

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
        void addDeferredAttrNode(final DeferredType dt, ResultInfo resultInfo, List<Type> stuckVars) {
            deferredAttrNodes.add(new DeferredAttrNode(dt, resultInfo, stuckVars));
        }

        /**
         * Incrementally process all nodes, by skipping 'stuck' nodes and attributing
         * 'unstuck' ones. If at any point no progress can be made (no 'unstuck' nodes)
         * some inference variable might get eagerly instantiated so that all nodes
         * can be type-checked.
         */
        void complete() {
            while (!deferredAttrNodes.isEmpty()) {
                Set<Type> stuckVars = new LinkedHashSet<Type>();
                boolean progress = false;
                //scan a defensive copy of the node list - this is because a deferred
                //attribution round can add new nodes to the list
                for (DeferredAttrNode deferredAttrNode : List.from(deferredAttrNodes)) {
                    if (!deferredAttrNode.process(this)) {
                        stuckVars.addAll(deferredAttrNode.stuckVars);
                    } else {
                        deferredAttrNodes.remove(deferredAttrNode);
                        progress = true;
                    }
                }
                if (!progress) {
                    //remove all variables that have already been instantiated
                    //from the list of stuck variables
                    inferenceContext.solveAny(List.from(stuckVars), warn);
                    inferenceContext.notifyChange();
                }
            }
        }
    }

    /**
     * Class representing a deferred attribution node. It keeps track of
     * a deferred type, along with the expected target type information.
     */
    class DeferredAttrNode implements Infer.FreeTypeListener {

        /** underlying deferred type */
        DeferredType dt;

        /** underlying target type information */
        ResultInfo resultInfo;

        /** list of uninferred inference variables causing this node to be stuck */
        List<Type> stuckVars;

        DeferredAttrNode(DeferredType dt, ResultInfo resultInfo, List<Type> stuckVars) {
            this.dt = dt;
            this.resultInfo = resultInfo;
            this.stuckVars = stuckVars;
            if (!stuckVars.isEmpty()) {
                resultInfo.checkContext.inferenceContext().addFreeTypeListener(stuckVars, this);
            }
        }

        @Override
        public void typesInferred(InferenceContext inferenceContext) {
            stuckVars = List.nil();
            resultInfo = resultInfo.dup(inferenceContext.asInstType(resultInfo.pt));
        }

        /**
         * Process a deferred attribution node.
         * Invariant: a stuck node cannot be processed.
         */
        @SuppressWarnings("fallthrough")
        boolean process(DeferredAttrContext deferredAttrContext) {
            switch (deferredAttrContext.mode) {
                case SPECULATIVE:
                    dt.check(resultInfo, List.<Type>nil(), new StructuralStuckChecker());
                    return true;
                case CHECK:
                    if (stuckVars.nonEmpty()) {
                        //stuck expression - see if we can propagate
                        if (deferredAttrContext.parent != emptyDeferredAttrContext &&
                                Type.containsAny(deferredAttrContext.parent.inferenceContext.inferencevars, List.from(stuckVars))) {
                            deferredAttrContext.parent.deferredAttrNodes.add(this);
                            dt.check(resultInfo, List.<Type>nil(), dummyCompleter);
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        dt.check(resultInfo, stuckVars, basicCompleter);
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

            public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
                this.resultInfo = resultInfo;
                this.inferenceContext = deferredAttrContext.inferenceContext;
                dt.tree.accept(this);
                dt.speculativeCache.put(deferredAttrContext.msym, stuckTree, deferredAttrContext.phase);
                return Type.noType;
            }

            @Override
            public void visitLambda(JCLambda tree) {
                Check.CheckContext checkContext = resultInfo.checkContext;
                Type pt = resultInfo.pt;
                if (inferenceContext.inferencevars.contains(pt)) {
                    //ok
                    return;
                } else {
                    //must be a functional descriptor
                    try {
                        Type desc = types.findDescriptorType(pt);
                        if (desc.getParameterTypes().length() != tree.params.length()) {
                            checkContext.report(tree, diags.fragment("incompatible.arg.types.in.lambda"));
                        }
                    } catch (Types.FunctionDescriptorLookupError ex) {
                        checkContext.report(null, ex.getDiagnostic());
                    }
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
                if (inferenceContext.inferencevars.contains(pt)) {
                    //ok
                    return;
                } else {
                    try {
                        types.findDescriptorType(pt);
                    } catch (Types.FunctionDescriptorLookupError ex) {
                        checkContext.report(null, ex.getDiagnostic());
                    }
                    switch (tree.sym.kind) {
                        //note: as argtypes are erroneous types, type-errors must
                        //have been caused by arity mismatch
                        case Kinds.ABSENT_MTH:
                        case Kinds.WRONG_MTH:
                        case Kinds.WRONG_MTHS:
                        case Kinds.STATICERR:
                        case Kinds.MISSING_ENCL:
                           checkContext.report(null, diags.fragment("incompatible.arg.types.in.mref"));
                    }
                }
            }
        }
    }

    /** an empty deferred attribution context - all methods throw exceptions */
    final DeferredAttrContext emptyDeferredAttrContext;

    /**
     * Map a list of types possibly containing one or more deferred types
     * into a list of ordinary types. Each deferred type D is mapped into a type T,
     * where T is computed by retrieving the type that has already been
     * computed for D during a previous deferred attribution round of the given kind.
     */
    class DeferredTypeMap extends Type.Mapping {

        DeferredAttrContext deferredAttrContext;

        protected DeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(String.format("deferredTypeMap[%s]", mode));
            this.deferredAttrContext = new DeferredAttrContext(mode, msym, phase,
                    infer.emptyContext, emptyDeferredAttrContext, types.noWarnings);
        }

        protected boolean validState(DeferredType dt) {
            return dt.mode != null &&
                    deferredAttrContext.mode.ordinal() <= dt.mode.ordinal();
        }

        @Override
        public Type apply(Type t) {
            if (!t.hasTag(DEFERRED)) {
                return t.map(this);
            } else {
                DeferredType dt = (DeferredType)t;
                Assert.check(validState(dt));
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

        @Override
        protected boolean validState(DeferredType dt) {
            return true;
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
     * Retrieves the list of inference variables that need to be inferred before
     * an AST node can be type-checked
     */
    @SuppressWarnings("fallthrough")
    List<Type> stuckVars(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
                if (resultInfo.pt.hasTag(NONE) || resultInfo.pt.isErroneous()) {
            return List.nil();
        } else {
            return stuckVarsInternal(tree, resultInfo.pt, env, resultInfo.checkContext.inferenceContext());
        }
    }
    //where
        private List<Type> stuckVarsInternal(JCTree tree, Type pt, Env<AttrContext> env, Infer.InferenceContext inferenceContext) {
            StuckChecker sc = new StuckChecker(pt, env, inferenceContext);
            sc.scan(tree);
            return List.from(sc.stuckVars);
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
        abstract void skip(JCTree tree);
    }

    /**
     * A tree scanner suitable for visiting the target-type dependent nodes of
     * a given argument expression.
     */
    static class PolyScanner extends FilterScanner {

        PolyScanner() {
            super(EnumSet.of(CONDEXPR, PARENS, LAMBDA, REFERENCE));
        }

        @Override
        void skip(JCTree tree) {
            //do nothing
        }
    }

    /**
     * A tree scanner suitable for visiting the target-type dependent nodes nested
     * within a lambda expression body.
     */
    static class LambdaReturnScanner extends FilterScanner {

        LambdaReturnScanner() {
            super(EnumSet.of(BLOCK, CASE, CATCH, DOLOOP, FOREACHLOOP,
                    FORLOOP, RETURN, SYNCHRONIZED, SWITCH, TRY, WHILELOOP));
        }

        @Override
        void skip(JCTree tree) {
            //do nothing
        }
    }

    /**
     * This visitor is used to check that structural expressions conform
     * to their target - this step is required as inference could end up
     * inferring types that make some of the nested expressions incompatible
     * with their corresponding instantiated target
     */
    class StuckChecker extends PolyScanner {

        Type pt;
        Env<AttrContext> env;
        Infer.InferenceContext inferenceContext;
        Set<Type> stuckVars = new LinkedHashSet<Type>();

        StuckChecker(Type pt, Env<AttrContext> env, Infer.InferenceContext inferenceContext) {
            this.pt = pt;
            this.env = env;
            this.inferenceContext = inferenceContext;
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
            Env<AttrContext> localEnv = env.dup(tree, env.info.dup());
            if (freeArgVars.nonEmpty()) {
                //perform arity-based check
                JCExpression exprTree = (JCExpression)attribSpeculative(tree.getQualifierExpression(), localEnv,
                        attr.memberReferenceQualifierResult(tree));
                ListBuffer<Type> argtypes = ListBuffer.lb();
                for (Type t : descType.getParameterTypes()) {
                    argtypes.append(Type.noType);
                }
                JCMemberReference mref2 = new TreeCopier<Void>(make).copy(tree);
                mref2.expr = exprTree;
                Pair<Symbol, ReferenceLookupHelper> lookupRes =
                        rs.resolveMemberReference(tree, localEnv, mref2, exprTree.type,
                            tree.name, argtypes.toList(), null, true, rs.arityMethodCheck,
                            inferenceContext);
                Symbol res = tree.sym = lookupRes.fst;
                if (res.kind >= Kinds.ERRONEOUS ||
                        res.type.hasTag(FORALL) ||
                        (res.flags() & Flags.VARARGS) != 0 ||
                        (TreeInfo.isStaticSelector(exprTree, tree.name.table.names) &&
                        exprTree.type.isRaw())) {
                    stuckVars.addAll(freeArgVars);
                }
            }
        }

        void scanLambdaBody(JCLambda lambda, final Type pt) {
            if (lambda.getBodyKind() == JCTree.JCLambda.BodyKind.EXPRESSION) {
                stuckVars.addAll(stuckVarsInternal(lambda.body, pt, env, inferenceContext));
            } else {
                LambdaReturnScanner lambdaScanner = new LambdaReturnScanner() {
                    @Override
                    public void visitReturn(JCReturn tree) {
                        if (tree.expr != null) {
                            stuckVars.addAll(stuckVarsInternal(tree.expr, pt, env, inferenceContext));
                        }
                    }
                };
                lambdaScanner.scan(lambda.body);
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
            result = reduce(ArgumentExpressionKind.PRIMITIVE);
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            result = (TreeInfo.isDiamond(tree) || attr.findDiamonds) ?
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
            final JCExpression rec = tree.meth.hasTag(SELECT) ?
                    ((JCFieldAccess)tree.meth).selected :
                    null;

            if (rec != null && !isSimpleReceiver(rec)) {
                //give up if receiver is too complex (to cut down analysis time)
                result = ArgumentExpressionKind.POLY;
                return;
            }

            Type site = rec != null ?
                    attribSpeculative(rec, env, attr.unknownTypeExprInfo).type :
                    env.enclClass.sym.type;

            while (site.hasTag(TYPEVAR)) {
                site = site.getUpperBound();
            }

            List<Type> args = rs.dummyArgs(tree.args.length());

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

            Symbol sym = rs.lookupMethod(env, tree, site.tsym, rs.arityMethodCheck, lh);

            if (sym.kind == Kinds.AMBIGUOUS) {
                Resolve.AmbiguityError err = (Resolve.AmbiguityError)sym.baseSymbol();
                result = ArgumentExpressionKind.PRIMITIVE;
                for (Symbol s : err.ambiguousSyms) {
                    if (result.isPoly()) break;
                    if (s.kind == Kinds.MTH) {
                        result = reduce(ArgumentExpressionKind.methodKind(s, types));
                    }
                }
            } else {
                result = (sym.kind == Kinds.MTH) ?
                    ArgumentExpressionKind.methodKind(sym, types) :
                    ArgumentExpressionKind.NO_POLY;
            }
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
                    default:
                        return false;
                }
            }
            private ArgumentExpressionKind reduce(ArgumentExpressionKind kind) {
                switch (result) {
                    case PRIMITIVE: return kind;
                    case NO_POLY: return kind.isPoly() ? kind : result;
                    case POLY: return result;
                    default:
                        Assert.error();
                        return null;
                }
            }

        @Override
        public void visitLiteral(JCLiteral tree) {
            Type litType = attr.litType(tree.typetag);
            result = ArgumentExpressionKind.standaloneKind(litType, types);
        }

        @Override
        void skip(JCTree tree) {
            result = ArgumentExpressionKind.NO_POLY;
        }
    }
    //where
    private EnumSet<JCTree.Tag> deferredCheckerTags =
            EnumSet.of(LAMBDA, REFERENCE, PARENS, TYPECAST,
                    CONDEXPR, NEWCLASS, APPLY, LITERAL);
}
