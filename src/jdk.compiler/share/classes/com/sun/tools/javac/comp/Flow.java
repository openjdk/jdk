/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

//todo: one might eliminate uninits.andSets when monotonic

package com.sun.tools.javac.comp;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.sun.source.tree.LambdaExpressionTree.BodyKind;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.LintWarnings;
import com.sun.tools.javac.resources.CompilerProperties.Warnings;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.Error;
import com.sun.tools.javac.util.JCDiagnostic.Warning;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.BLOCK;
import com.sun.tools.javac.code.Kinds.Kind;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import com.sun.tools.javac.code.Type.TypeVar;
import static com.sun.tools.javac.code.TypeTag.BOOLEAN;
import static com.sun.tools.javac.code.TypeTag.VOID;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import static com.sun.tools.javac.tree.JCTree.Tag.*;
import com.sun.tools.javac.util.JCDiagnostic.Fragment;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/** This pass implements dataflow analysis for Java programs though
 *  different AST visitor steps. Liveness analysis (see AliveAnalyzer) checks that
 *  every statement is reachable. Exception analysis (see FlowAnalyzer) ensures that
 *  every checked exception that is thrown is declared or caught.  Definite assignment analysis
 *  (see AssignAnalyzer) ensures that each variable is assigned when used.  Definite
 *  unassignment analysis (see AssignAnalyzer) in ensures that no final variable
 *  is assigned more than once. Finally, local variable capture analysis (see CaptureAnalyzer)
 *  determines that local variables accessed within the scope of an inner class/lambda
 *  are either final or effectively-final.
 *
 *  <p>The JLS has a number of problems in the
 *  specification of these flow analysis problems. This implementation
 *  attempts to address those issues.
 *
 *  <p>First, there is no accommodation for a finally clause that cannot
 *  complete normally. For liveness analysis, an intervening finally
 *  clause can cause a break, continue, or return not to reach its
 *  target.  For exception analysis, an intervening finally clause can
 *  cause any exception to be "caught".  For DA/DU analysis, the finally
 *  clause can prevent a transfer of control from propagating DA/DU
 *  state to the target.  In addition, code in the finally clause can
 *  affect the DA/DU status of variables.
 *
 *  <p>For try statements, we introduce the idea of a variable being
 *  definitely unassigned "everywhere" in a block.  A variable V is
 *  "unassigned everywhere" in a block iff it is unassigned at the
 *  beginning of the block and there is no reachable assignment to V
 *  in the block.  An assignment V=e is reachable iff V is not DA
 *  after e.  Then we can say that V is DU at the beginning of the
 *  catch block iff V is DU everywhere in the try block.  Similarly, V
 *  is DU at the beginning of the finally block iff V is DU everywhere
 *  in the try block and in every catch block.  Specifically, the
 *  following bullet is added to 16.2.2
 *  <pre>
 *      V is <em>unassigned everywhere</em> in a block if it is
 *      unassigned before the block and there is no reachable
 *      assignment to V within the block.
 *  </pre>
 *  <p>In 16.2.15, the third bullet (and all of its sub-bullets) for all
 *  try blocks is changed to
 *  <pre>
 *      V is definitely unassigned before a catch block iff V is
 *      definitely unassigned everywhere in the try block.
 *  </pre>
 *  <p>The last bullet (and all of its sub-bullets) for try blocks that
 *  have a finally block is changed to
 *  <pre>
 *      V is definitely unassigned before the finally block iff
 *      V is definitely unassigned everywhere in the try block
 *      and everywhere in each catch block of the try statement.
 *  </pre>
 *  <p>In addition,
 *  <pre>
 *      V is definitely assigned at the end of a constructor iff
 *      V is definitely assigned after the block that is the body
 *      of the constructor and V is definitely assigned at every
 *      return that can return from the constructor.
 *  </pre>
 *  <p>In addition, each continue statement with the loop as its target
 *  is treated as a jump to the end of the loop body, and "intervening"
 *  finally clauses are treated as follows: V is DA "due to the
 *  continue" iff V is DA before the continue statement or V is DA at
 *  the end of any intervening finally block.  V is DU "due to the
 *  continue" iff any intervening finally cannot complete normally or V
 *  is DU at the end of every intervening finally block.  This "due to
 *  the continue" concept is then used in the spec for the loops.
 *
 *  <p>Similarly, break statements must consider intervening finally
 *  blocks.  For liveness analysis, a break statement for which any
 *  intervening finally cannot complete normally is not considered to
 *  cause the target statement to be able to complete normally. Then
 *  we say V is DA "due to the break" iff V is DA before the break or
 *  V is DA at the end of any intervening finally block.  V is DU "due
 *  to the break" iff any intervening finally cannot complete normally
 *  or V is DU at the break and at the end of every intervening
 *  finally block.  (I suspect this latter condition can be
 *  simplified.)  This "due to the break" is then used in the spec for
 *  all statements that can be "broken".
 *
 *  <p>The return statement is treated similarly.  V is DA "due to a
 *  return statement" iff V is DA before the return statement or V is
 *  DA at the end of any intervening finally block.  Note that we
 *  don't have to worry about the return expression because this
 *  concept is only used for constructors.
 *
 *  <p>There is no spec in the JLS for when a variable is definitely
 *  assigned at the end of a constructor, which is needed for final
 *  fields (8.3.1.2).  We implement the rule that V is DA at the end
 *  of the constructor iff it is DA and the end of the body of the
 *  constructor and V is DA "due to" every return of the constructor.
 *
 *  <p>Intervening finally blocks similarly affect exception analysis.  An
 *  intervening finally that cannot complete normally allows us to ignore
 *  an otherwise uncaught exception.
 *
 *  <p>To implement the semantics of intervening finally clauses, all
 *  nonlocal transfers (break, continue, return, throw, method call that
 *  can throw a checked exception, and a constructor invocation that can
 *  thrown a checked exception) are recorded in a queue, and removed
 *  from the queue when we complete processing the target of the
 *  nonlocal transfer.  This allows us to modify the queue in accordance
 *  with the above rules when we encounter a finally clause.  The only
 *  exception to this [no pun intended] is that checked exceptions that
 *  are known to be caught or declared to be caught in the enclosing
 *  method are not recorded in the queue, but instead are recorded in a
 *  global variable "{@code Set<Type> thrown}" that records the type of all
 *  exceptions that can be thrown.
 *
 *  <p>Other minor issues the treatment of members of other classes
 *  (always considered DA except that within an anonymous class
 *  constructor, where DA status from the enclosing scope is
 *  preserved), treatment of the case expression (V is DA before the
 *  case expression iff V is DA after the switch expression),
 *  treatment of variables declared in a switch block (the implied
 *  DA/DU status after the switch expression is DU and not DA for
 *  variables defined in a switch block), the treatment of boolean ?:
 *  expressions (The JLS rules only handle b and c non-boolean; the
 *  new rule is that if b and c are boolean valued, then V is
 *  (un)assigned after a?b:c when true/false iff V is (un)assigned
 *  after b when true/false and V is (un)assigned after c when
 *  true/false).
 *
 *  <p>There is the remaining question of what syntactic forms constitute a
 *  reference to a variable.  It is conventional to allow this.x on the
 *  left-hand-side to initialize a final instance field named x, yet
 *  this.x isn't considered a "use" when appearing on a right-hand-side
 *  in most implementations.  Should parentheses affect what is
 *  considered a variable reference?  The simplest rule would be to
 *  allow unqualified forms only, parentheses optional, and phase out
 *  support for assigning to a final field via this.x.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Flow {
    protected static final Context.Key<Flow> flowKey = new Context.Key<>();

    private final Names names;
    private final Log log;
    private final Symtab syms;
    private final Types types;
    private final Check chk;
    private       TreeMaker make;
    private final Resolve rs;
    private final JCDiagnostic.Factory diags;
    private Env<AttrContext> attrEnv;
    private       Lint lint;
    private final Infer infer;

    public static Flow instance(Context context) {
        Flow instance = context.get(flowKey);
        if (instance == null)
            instance = new Flow(context);
        return instance;
    }

    public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
        new AliveAnalyzer().analyzeTree(env, make);
        new AssignAnalyzer().analyzeTree(env, make);
        new FlowAnalyzer().analyzeTree(env, make);
        new CaptureAnalyzer().analyzeTree(env, make);
    }

    public void analyzeLambda(Env<AttrContext> env, JCLambda that, TreeMaker make, boolean speculative) {
        Log.DiagnosticHandler diagHandler = null;
        //we need to disable diagnostics temporarily; the problem is that if
        //a lambda expression contains e.g. an unreachable statement, an error
        //message will be reported and will cause compilation to skip the flow analysis
        //step - if we suppress diagnostics, we won't stop at Attr for flow-analysis
        //related errors, which will allow for more errors to be detected
        if (!speculative) {
            diagHandler = log.new DiscardDiagnosticHandler();
        }
        try {
            new LambdaAliveAnalyzer().analyzeTree(env, that, make);
        } finally {
            if (!speculative) {
                log.popDiagnosticHandler(diagHandler);
            }
        }
    }

    public List<Type> analyzeLambdaThrownTypes(final Env<AttrContext> env,
            JCLambda that, TreeMaker make) {
        //we need to disable diagnostics temporarily; the problem is that if
        //a lambda expression contains e.g. an unreachable statement, an error
        //message will be reported and will cause compilation to skip the flow analysis
        //step - if we suppress diagnostics, we won't stop at Attr for flow-analysis
        //related errors, which will allow for more errors to be detected
        Log.DiagnosticHandler diagHandler = log.new DiscardDiagnosticHandler();
        try {
            new LambdaAssignAnalyzer(env).analyzeTree(env, that, make);
            LambdaFlowAnalyzer flowAnalyzer = new LambdaFlowAnalyzer();
            flowAnalyzer.analyzeTree(env, that, make);
            return flowAnalyzer.inferredThrownTypes;
        } finally {
            log.popDiagnosticHandler(diagHandler);
        }
    }

    public boolean aliveAfter(Env<AttrContext> env, JCTree that, TreeMaker make) {
        //we need to disable diagnostics temporarily; the problem is that if
        //"that" contains e.g. an unreachable statement, an error
        //message will be reported and will cause compilation to skip the flow analysis
        //step - if we suppress diagnostics, we won't stop at Attr for flow-analysis
        //related errors, which will allow for more errors to be detected
        Log.DiagnosticHandler diagHandler = log.new DiscardDiagnosticHandler();
        try {
            SnippetAliveAnalyzer analyzer = new SnippetAliveAnalyzer();

            analyzer.analyzeTree(env, that, make);
            return analyzer.isAlive();
        } finally {
            log.popDiagnosticHandler(diagHandler);
        }
    }

    public boolean breaksToTree(Env<AttrContext> env, JCTree breakTo, JCTree body, TreeMaker make) {
        //we need to disable diagnostics temporarily; the problem is that if
        //"that" contains e.g. an unreachable statement, an error
        //message will be reported and will cause compilation to skip the flow analysis
        //step - if we suppress diagnostics, we won't stop at Attr for flow-analysis
        //related errors, which will allow for more errors to be detected
        Log.DiagnosticHandler diagHandler = log.new DiscardDiagnosticHandler();
        try {
            SnippetBreakToAnalyzer analyzer = new SnippetBreakToAnalyzer(breakTo);

            analyzer.analyzeTree(env, body, make);
            return analyzer.breaksTo();
        } finally {
            log.popDiagnosticHandler(diagHandler);
        }
    }

    /**
     * Definite assignment scan mode
     */
    enum FlowKind {
        /**
         * This is the normal DA/DU analysis mode
         */
        NORMAL("var.might.already.be.assigned", false),
        /**
         * This is the speculative DA/DU analysis mode used to speculatively
         * derive assertions within loop bodies
         */
        SPECULATIVE_LOOP("var.might.be.assigned.in.loop", true);

        final String errKey;
        final boolean isFinal;

        FlowKind(String errKey, boolean isFinal) {
            this.errKey = errKey;
            this.isFinal = isFinal;
        }

        boolean isFinal() {
            return isFinal;
        }
    }

    @SuppressWarnings("this-escape")
    protected Flow(Context context) {
        context.put(flowKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        chk = Check.instance(context);
        lint = Lint.instance(context);
        infer = Infer.instance(context);
        rs = Resolve.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Source source = Source.instance(context);
    }

    /**
     * Base visitor class for all visitors implementing dataflow analysis logic.
     * This class define the shared logic for handling jumps (break/continue statements).
     */
    abstract static class BaseAnalyzer extends TreeScanner {

        enum JumpKind {
            BREAK(JCTree.Tag.BREAK) {
                @Override
                JCTree getTarget(JCTree tree) {
                    return ((JCBreak)tree).target;
                }
            },
            CONTINUE(JCTree.Tag.CONTINUE) {
                @Override
                JCTree getTarget(JCTree tree) {
                    return ((JCContinue)tree).target;
                }
            },
            YIELD(JCTree.Tag.YIELD) {
                @Override
                JCTree getTarget(JCTree tree) {
                    return ((JCYield)tree).target;
                }
            };

            final JCTree.Tag treeTag;

            private JumpKind(Tag treeTag) {
                this.treeTag = treeTag;
            }

            abstract JCTree getTarget(JCTree tree);
        }

        /** The currently pending exits that go from current inner blocks
         *  to an enclosing block, in source order.
         */
        ListBuffer<PendingExit> pendingExits;

        /** A class whose initializers we are scanning. Because initializer
         *  scans can be triggered out of sequence when visiting certain nodes
         *  (e.g., super()), we protect against infinite loops that could be
         *  triggered by incorrect code (e.g., super() inside initializer).
         */
        JCClassDecl initScanClass;

        /** A pending exit.  These are the statements return, break, and
         *  continue.  In addition, exception-throwing expressions or
         *  statements are put here when not known to be caught.  This
         *  will typically result in an error unless it is within a
         *  try-finally whose finally block cannot complete normally.
         */
        static class PendingExit {
            JCTree tree;

            PendingExit(JCTree tree) {
                this.tree = tree;
            }

            void resolveJump() {
                //do nothing
            }
        }

        abstract void markDead();

        /** Record an outward transfer of control. */
        void recordExit(PendingExit pe) {
            pendingExits.append(pe);
            markDead();
        }

        /** Resolve all jumps of this statement. */
        private Liveness resolveJump(JCTree tree,
                         ListBuffer<PendingExit> oldPendingExits,
                         JumpKind jk) {
            boolean resolved = false;
            List<PendingExit> exits = pendingExits.toList();
            pendingExits = oldPendingExits;
            for (; exits.nonEmpty(); exits = exits.tail) {
                PendingExit exit = exits.head;
                if (exit.tree.hasTag(jk.treeTag) &&
                        jk.getTarget(exit.tree) == tree) {
                    exit.resolveJump();
                    resolved = true;
                } else {
                    pendingExits.append(exit);
                }
            }
            return Liveness.from(resolved);
        }

        /** Resolve all continues of this statement. */
        Liveness resolveContinues(JCTree tree) {
            return resolveJump(tree, new ListBuffer<PendingExit>(), JumpKind.CONTINUE);
        }

        /** Resolve all breaks of this statement. */
        Liveness resolveBreaks(JCTree tree, ListBuffer<PendingExit> oldPendingExits) {
            return resolveJump(tree, oldPendingExits, JumpKind.BREAK);
        }

        /** Resolve all yields of this statement. */
        Liveness resolveYields(JCTree tree, ListBuffer<PendingExit> oldPendingExits) {
            return resolveJump(tree, oldPendingExits, JumpKind.YIELD);
        }

        @Override
        public void scan(JCTree tree) {
            if (tree != null && (
                    tree.type == null ||
                    tree.type != Type.stuckType)) {
                super.scan(tree);
            }
        }

        public void visitPackageDef(JCPackageDecl tree) {
            // Do nothing for PackageDecl
        }

        protected void scanSyntheticBreak(TreeMaker make, JCTree swtch) {
            if (swtch.hasTag(SWITCH_EXPRESSION)) {
                JCYield brk = make.at(Position.NOPOS).Yield(make.Erroneous()
                                                                .setType(swtch.type));
                brk.target = swtch;
                scan(brk);
            } else {
                JCBreak brk = make.at(Position.NOPOS).Break(null);
                brk.target = swtch;
                scan(brk);
            }
        }

        // Do something with all static or non-static field initializers and initialization blocks.
        protected void forEachInitializer(JCClassDecl classDef, boolean isStatic, Consumer<? super JCTree> handler) {
            if (classDef == initScanClass)          // avoid infinite loops
                return;
            JCClassDecl initScanClassPrev = initScanClass;
            initScanClass = classDef;
            try {
                for (List<JCTree> defs = classDef.defs; defs.nonEmpty(); defs = defs.tail) {
                    JCTree def = defs.head;

                    // Don't recurse into nested classes
                    if (def.hasTag(CLASSDEF))
                        continue;

                    /* we need to check for flags in the symbol too as there could be cases for which implicit flags are
                     * represented in the symbol but not in the tree modifiers as they were not originally in the source
                     * code
                     */
                    boolean isDefStatic = ((TreeInfo.flags(def) | (TreeInfo.symbolFor(def) == null ? 0 : TreeInfo.symbolFor(def).flags_field)) & STATIC) != 0;
                    if (!def.hasTag(METHODDEF) && (isDefStatic == isStatic))
                        handler.accept(def);
                }
            } finally {
                initScanClass = initScanClassPrev;
            }
        }
    }

    /**
     * This pass implements the first step of the dataflow analysis, namely
     * the liveness analysis check. This checks that every statement is reachable.
     * The output of this analysis pass are used by other analyzers. This analyzer
     * sets the 'finallyCanCompleteNormally' field in the JCTry class.
     */
    class AliveAnalyzer extends BaseAnalyzer {

        /** A flag that indicates whether the last statement could
         *  complete normally.
         */
        private Liveness alive;

        @Override
        void markDead() {
            alive = Liveness.DEAD;
        }

    /* ***********************************************************************
     * Visitor methods for statements and definitions
     *************************************************************************/

        /** Analyze a definition.
         */
        void scanDef(JCTree tree) {
            scanStat(tree);
            if (tree != null && tree.hasTag(JCTree.Tag.BLOCK) && alive == Liveness.DEAD) {
                log.error(tree.pos(),
                          Errors.InitializerMustBeAbleToCompleteNormally);
            }
        }

        /** Analyze a statement. Check that statement is reachable.
         */
        void scanStat(JCTree tree) {
            if (alive == Liveness.DEAD && tree != null) {
                log.error(tree.pos(), Errors.UnreachableStmt);
                if (!tree.hasTag(SKIP)) alive = Liveness.RECOVERY;
            }
            scan(tree);
        }

        /** Analyze list of statements.
         */
        void scanStats(List<? extends JCStatement> trees) {
            if (trees != null)
                for (List<? extends JCStatement> l = trees; l.nonEmpty(); l = l.tail)
                    scanStat(l.head);
        }

        /* ------------ Visitor methods for various sorts of trees -------------*/

        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) return;
            Liveness alivePrev = alive;
            ListBuffer<PendingExit> pendingExitsPrev = pendingExits;
            Lint lintPrev = lint;

            pendingExits = new ListBuffer<>();
            lint = lint.augment(tree.sym);

            try {
                // process all the nested classes
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(CLASSDEF)) {
                        scan(l.head);
                    }
                }

                // process all the static initializers
                forEachInitializer(tree, true, def -> {
                    scanDef(def);
                    clearPendingExits(false);
                });

                // process all the instance initializers
                forEachInitializer(tree, false, def -> {
                    scanDef(def);
                    clearPendingExits(false);
                });

                // process all the methods
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(METHODDEF)) {
                        scan(l.head);
                    }
                }
            } finally {
                pendingExits = pendingExitsPrev;
                alive = alivePrev;
                lint = lintPrev;
            }
        }

        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) return;
            Lint lintPrev = lint;

            lint = lint.augment(tree.sym);

            Assert.check(pendingExits.isEmpty());

            try {
                alive = Liveness.ALIVE;
                scanStat(tree.body);
                tree.completesNormally = alive != Liveness.DEAD;

                if (alive == Liveness.ALIVE && !tree.sym.type.getReturnType().hasTag(VOID))
                    log.error(TreeInfo.diagEndPos(tree.body), Errors.MissingRetStmt);

                clearPendingExits(true);
            } finally {
                lint = lintPrev;
            }
        }

        private void clearPendingExits(boolean inMethod) {
            List<PendingExit> exits = pendingExits.toList();
            pendingExits = new ListBuffer<>();
            while (exits.nonEmpty()) {
                PendingExit exit = exits.head;
                exits = exits.tail;
                Assert.check((inMethod && exit.tree.hasTag(RETURN)) ||
                                log.hasErrorOn(exit.tree.pos()));
            }
        }

        public void visitVarDef(JCVariableDecl tree) {
            if (tree.init != null) {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try{
                    scan(tree.init);
                } finally {
                    lint = lintPrev;
                }
            }
        }

        public void visitBlock(JCBlock tree) {
            scanStats(tree.stats);
        }

        public void visitDoLoop(JCDoWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scanStat(tree.body);
            alive = alive.or(resolveContinues(tree));
            scan(tree.cond);
            alive = alive.and(!tree.cond.type.isTrue());
            alive = alive.or(resolveBreaks(tree, prevPendingExits));
        }

        public void visitWhileLoop(JCWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.cond);
            alive = Liveness.from(!tree.cond.type.isFalse());
            scanStat(tree.body);
            alive = alive.or(resolveContinues(tree));
            alive = resolveBreaks(tree, prevPendingExits).or(
                !tree.cond.type.isTrue());
        }

        public void visitForLoop(JCForLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            scanStats(tree.init);
            pendingExits = new ListBuffer<>();
            if (tree.cond != null) {
                scan(tree.cond);
                alive = Liveness.from(!tree.cond.type.isFalse());
            } else {
                alive = Liveness.ALIVE;
            }
            scanStat(tree.body);
            alive = alive.or(resolveContinues(tree));
            scan(tree.step);
            alive = resolveBreaks(tree, prevPendingExits).or(
                tree.cond != null && !tree.cond.type.isTrue());
        }

        public void visitForeachLoop(JCEnhancedForLoop tree) {
            visitVarDef(tree.var);
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            scan(tree.expr);
            pendingExits = new ListBuffer<>();
            scanStat(tree.body);
            alive = alive.or(resolveContinues(tree));
            resolveBreaks(tree, prevPendingExits);
            alive = Liveness.ALIVE;
        }

        public void visitLabelled(JCLabeledStatement tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scanStat(tree.body);
            alive = alive.or(resolveBreaks(tree, prevPendingExits));
        }

        public void visitSwitch(JCSwitch tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.selector);
            boolean exhaustiveSwitch = TreeInfo.expectedExhaustive(tree);
            for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                alive = Liveness.ALIVE;
                JCCase c = l.head;
                for (JCCaseLabel pat : c.labels) {
                    scan(pat);
                }
                scanStats(c.stats);
                if (alive != Liveness.DEAD && c.caseKind == JCCase.RULE) {
                    scanSyntheticBreak(make, tree);
                    alive = Liveness.DEAD;
                }
                // Warn about fall-through if lint switch fallthrough enabled.
                if (alive == Liveness.ALIVE &&
                    c.stats.nonEmpty() && l.tail.nonEmpty())
                    lint.logIfEnabled(l.tail.head.pos(),
                                LintWarnings.PossibleFallThroughIntoCase);
            }
            tree.isExhaustive = tree.hasUnconditionalPattern ||
                                TreeInfo.isErrorEnumSwitch(tree.selector, tree.cases);
            if (exhaustiveSwitch) {
                tree.isExhaustive |= exhausts(tree.selector, tree.cases);
                if (!tree.isExhaustive) {
                    log.error(tree, Errors.NotExhaustiveStatement);
                }
            }
            if (!tree.hasUnconditionalPattern && !exhaustiveSwitch) {
                alive = Liveness.ALIVE;
            }
            alive = alive.or(resolveBreaks(tree, prevPendingExits));
        }

        @Override
        public void visitSwitchExpression(JCSwitchExpression tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.selector);
            Liveness prevAlive = alive;
            for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                alive = Liveness.ALIVE;
                JCCase c = l.head;
                for (JCCaseLabel pat : c.labels) {
                    scan(pat);
                }
                scanStats(c.stats);
                if (alive == Liveness.ALIVE) {
                    if (c.caseKind == JCCase.RULE) {
                        log.error(TreeInfo.diagEndPos(c.body),
                                  Errors.RuleCompletesNormally);
                    } else if (l.tail.isEmpty()) {
                        log.error(TreeInfo.diagEndPos(tree),
                                  Errors.SwitchExpressionCompletesNormally);
                    }
                }
            }

            if (tree.hasUnconditionalPattern ||
                TreeInfo.isErrorEnumSwitch(tree.selector, tree.cases)) {
                tree.isExhaustive = true;
            } else {
                tree.isExhaustive = exhausts(tree.selector, tree.cases);
            }

            if (!tree.isExhaustive) {
                log.error(tree, Errors.NotExhaustive);
            }
            alive = prevAlive;
            alive = alive.or(resolveYields(tree, prevPendingExits));
        }

        private boolean exhausts(JCExpression selector, List<JCCase> cases) {
            Set<PatternDescription> patternSet = new HashSet<>();
            Map<Symbol, Set<Symbol>> enum2Constants = new HashMap<>();
            Set<Object> booleanLiterals = new HashSet<>(Set.of(0, 1));
            for (JCCase c : cases) {
                if (!TreeInfo.unguardedCase(c))
                    continue;

                for (var l : c.labels) {
                    if (l instanceof JCPatternCaseLabel patternLabel) {
                        for (Type component : components(selector.type)) {
                            patternSet.add(makePatternDescription(component, patternLabel.pat));
                        }
                    } else if (l instanceof JCConstantCaseLabel constantLabel) {
                        if (types.unboxedTypeOrType(selector.type).hasTag(TypeTag.BOOLEAN)) {
                            Object value = ((JCLiteral) constantLabel.expr).value;
                            booleanLiterals.remove(value);
                        } else {
                            Symbol s = TreeInfo.symbol(constantLabel.expr);
                            if (s != null && s.isEnum()) {
                                enum2Constants.computeIfAbsent(s.owner, x -> {
                                    Set<Symbol> result = new HashSet<>();
                                    s.owner.members()
                                            .getSymbols(sym -> sym.kind == Kind.VAR && sym.isEnum())
                                            .forEach(result::add);
                                    return result;
                                }).remove(s);
                            }
                        }
                    }
                }
            }

            if (types.unboxedTypeOrType(selector.type).hasTag(TypeTag.BOOLEAN) && booleanLiterals.isEmpty()) {
                return true;
            }

            for (Entry<Symbol, Set<Symbol>> e : enum2Constants.entrySet()) {
                if (e.getValue().isEmpty()) {
                    patternSet.add(new BindingPattern(e.getKey().type));
                }
            }
            Set<PatternDescription> patterns = patternSet;
            boolean useHashes = true;
            try {
                boolean repeat = true;
                while (repeat) {
                    Set<PatternDescription> updatedPatterns;
                    updatedPatterns = reduceBindingPatterns(selector.type, patterns);
                    updatedPatterns = reduceNestedPatterns(updatedPatterns, useHashes);
                    updatedPatterns = reduceRecordPatterns(updatedPatterns);
                    updatedPatterns = removeCoveredRecordPatterns(updatedPatterns);
                    repeat = !updatedPatterns.equals(patterns);
                    if (checkCovered(selector.type, patterns)) {
                        return true;
                    }
                    if (!repeat) {
                        //there may be situation like:
                        //class B permits S1, S2
                        //patterns: R(S1, B), R(S2, S2)
                        //this might be joined to R(B, S2), as B could be rewritten to S2
                        //but hashing in reduceNestedPatterns will not allow that
                        //disable the use of hashing, and use subtyping in
                        //reduceNestedPatterns to handle situations like this:
                        repeat = useHashes;
                        useHashes = false;
                    } else {
                        //if a reduction happened, make sure hashing in reduceNestedPatterns
                        //is enabled, as the hashing speeds up the process significantly:
                        useHashes = true;
                    }
                    patterns = updatedPatterns;
                }
                return checkCovered(selector.type, patterns);
            } catch (CompletionFailure cf) {
                chk.completionError(selector.pos(), cf);
                return true; //error recovery
            }
        }

        private boolean checkCovered(Type seltype, Iterable<PatternDescription> patterns) {
            for (Type seltypeComponent : components(seltype)) {
                for (PatternDescription pd : patterns) {
                    if(isBpCovered(seltypeComponent, pd)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private List<Type> components(Type seltype) {
            return switch (seltype.getTag()) {
                case CLASS -> {
                    if (seltype.isCompound()) {
                        if (seltype.isIntersection()) {
                            yield ((Type.IntersectionClassType) seltype).getComponents()
                                                                        .stream()
                                                                        .flatMap(t -> components(t).stream())
                                                                        .collect(List.collector());
                        }
                        yield List.nil();
                    }
                    yield List.of(types.erasure(seltype));
                }
                case TYPEVAR -> components(((TypeVar) seltype).getUpperBound());
                default -> List.of(types.erasure(seltype));
            };
        }

        /* In a set of patterns, search for a sub-set of binding patterns that
         * in combination exhaust their sealed supertype. If such a sub-set
         * is found, it is removed, and replaced with a binding pattern
         * for the sealed supertype.
         */
        private Set<PatternDescription> reduceBindingPatterns(Type selectorType, Set<PatternDescription> patterns) {
            Set<Symbol> existingBindings = patterns.stream()
                                                   .filter(pd -> pd instanceof BindingPattern)
                                                   .map(pd -> ((BindingPattern) pd).type.tsym)
                                                   .collect(Collectors.toSet());

            for (PatternDescription pdOne : patterns) {
                if (pdOne instanceof BindingPattern bpOne) {
                    Set<PatternDescription> toAdd = new HashSet<>();

                    for (Type sup : types.directSupertypes(bpOne.type)) {
                        ClassSymbol clazz = (ClassSymbol) types.erasure(sup).tsym;

                        clazz.complete();

                        if (clazz.isSealed() && clazz.isAbstract() &&
                            //if a binding pattern for clazz already exists, no need to analyze it again:
                            !existingBindings.contains(clazz)) {
                            ListBuffer<PatternDescription> bindings = new ListBuffer<>();
                            //do not reduce to types unrelated to the selector type:
                            Type clazzErasure = types.erasure(clazz.type);
                            if (components(selectorType).stream()
                                                        .map(types::erasure)
                                                        .noneMatch(c -> types.isSubtype(clazzErasure, c))) {
                                continue;
                            }

                            Set<Symbol> permitted = allPermittedSubTypes(clazz, csym -> {
                                Type instantiated;
                                if (csym.type.allparams().isEmpty()) {
                                    instantiated = csym.type;
                                } else {
                                    instantiated = infer.instantiatePatternType(selectorType, csym);
                                }

                                return instantiated != null && types.isCastable(selectorType, instantiated);
                            });

                            for (PatternDescription pdOther : patterns) {
                                if (pdOther instanceof BindingPattern bpOther) {
                                    Set<Symbol> currentPermittedSubTypes =
                                            allPermittedSubTypes(bpOther.type.tsym, s -> true);

                                    PERMITTED: for (Iterator<Symbol> it = permitted.iterator(); it.hasNext();) {
                                        Symbol perm = it.next();

                                        for (Symbol currentPermitted : currentPermittedSubTypes) {
                                            if (types.isSubtype(types.erasure(currentPermitted.type),
                                                                types.erasure(perm.type))) {
                                                it.remove();
                                                continue PERMITTED;
                                            }
                                        }
                                        if (types.isSubtype(types.erasure(perm.type),
                                                            types.erasure(bpOther.type))) {
                                            it.remove();
                                        }
                                    }
                                }
                            }

                            if (permitted.isEmpty()) {
                                toAdd.add(new BindingPattern(clazz.type));
                            }
                        }
                    }

                    if (!toAdd.isEmpty()) {
                        Set<PatternDescription> newPatterns = new HashSet<>(patterns);
                        newPatterns.addAll(toAdd);
                        return newPatterns;
                    }
                }
            }
            return patterns;
        }

        private Set<Symbol> allPermittedSubTypes(TypeSymbol root, Predicate<ClassSymbol> accept) {
            Set<Symbol> permitted = new HashSet<>();
            List<ClassSymbol> permittedSubtypesClosure = baseClasses(root);

            while (permittedSubtypesClosure.nonEmpty()) {
                ClassSymbol current = permittedSubtypesClosure.head;

                permittedSubtypesClosure = permittedSubtypesClosure.tail;

                current.complete();

                if (current.isSealed() && current.isAbstract()) {
                    for (Type t : current.getPermittedSubclasses()) {
                        ClassSymbol csym = (ClassSymbol) t.tsym;

                        if (accept.test(csym)) {
                            permittedSubtypesClosure = permittedSubtypesClosure.prepend(csym);
                            permitted.add(csym);
                        }
                    }
                }
            }

            return permitted;
        }

        private List<ClassSymbol> baseClasses(TypeSymbol root) {
            if (root instanceof ClassSymbol clazz) {
                return List.of(clazz);
            } else if (root instanceof TypeVariableSymbol tvar) {
                ListBuffer<ClassSymbol> result = new ListBuffer<>();
                for (Type bound : tvar.getBounds()) {
                    result.appendList(baseClasses(bound.tsym));
                }
                return result.toList();
            } else {
                return List.nil();
            }
        }

        /* Among the set of patterns, find sub-set of patterns such:
         * $record($prefix$, $nested, $suffix$)
         * Where $record, $prefix$ and $suffix$ is the same for each pattern
         * in the set, and the patterns only differ in one "column" in
         * the $nested pattern.
         * Then, the set of $nested patterns is taken, and passed recursively
         * to reduceNestedPatterns and to reduceBindingPatterns, to
         * simplify the pattern. If that succeeds, the original found sub-set
         * of patterns is replaced with a new set of patterns of the form:
         * $record($prefix$, $resultOfReduction, $suffix$)
         *
         * useHashes: when true, patterns will be subject to exact equivalence;
         *            when false, two binding patterns will be considered equivalent
         *            if one of them is more generic than the other one;
         *            when false, the processing will be significantly slower,
         *            as pattern hashes cannot be used to speed up the matching process
         */
        private Set<PatternDescription> reduceNestedPatterns(Set<PatternDescription> patterns,
                                                             boolean useHashes) {
            /* implementation note:
             * finding a sub-set of patterns that only differ in a single
             * column is time-consuming task, so this method speeds it up by:
             * - group the patterns by their record class
             * - for each column (nested pattern) do:
             * -- group patterns by their hash
             * -- in each such by-hash group, find sub-sets that only differ in
             *    the chosen column, and then call reduceBindingPatterns and reduceNestedPatterns
             *    on patterns in the chosen column, as described above
             */
            var groupByRecordClass =
                    patterns.stream()
                            .filter(pd -> pd instanceof RecordPattern)
                            .map(pd -> (RecordPattern) pd)
                            .collect(groupingBy(pd -> (ClassSymbol) pd.recordType.tsym));

            for (var e : groupByRecordClass.entrySet()) {
                int nestedPatternsCount = e.getKey().getRecordComponents().size();
                Set<RecordPattern> current = new HashSet<>(e.getValue());

                for (int mismatchingCandidate = 0;
                     mismatchingCandidate < nestedPatternsCount;
                     mismatchingCandidate++) {
                    int mismatchingCandidateFin = mismatchingCandidate;
                    var groupEquivalenceCandidates =
                            current
                             .stream()
                             //error recovery, ignore patterns with incorrect number of nested patterns:
                             .filter(pd -> pd.nested.length == nestedPatternsCount)
                             .collect(groupingBy(pd -> useHashes ? pd.hashCode(mismatchingCandidateFin) : 0));
                    for (var candidates : groupEquivalenceCandidates.values()) {
                        var candidatesArr = candidates.toArray(RecordPattern[]::new);

                        for (int firstCandidate = 0;
                             firstCandidate < candidatesArr.length;
                             firstCandidate++) {
                            RecordPattern rpOne = candidatesArr[firstCandidate];
                            ListBuffer<RecordPattern> join = new ListBuffer<>();

                            join.append(rpOne);

                            NEXT_PATTERN: for (int nextCandidate = 0;
                                               nextCandidate < candidatesArr.length;
                                               nextCandidate++) {
                                if (firstCandidate == nextCandidate) {
                                    continue;
                                }

                                RecordPattern rpOther = candidatesArr[nextCandidate];
                                if (rpOne.recordType.tsym == rpOther.recordType.tsym) {
                                    for (int i = 0; i < rpOne.nested.length; i++) {
                                        if (i != mismatchingCandidate) {
                                            if (!rpOne.nested[i].equals(rpOther.nested[i])) {
                                                if (useHashes ||
                                                    //when not using hashes,
                                                    //check if rpOne.nested[i] is
                                                    //a subtype of rpOther.nested[i]:
                                                    !(rpOne.nested[i] instanceof BindingPattern bpOne) ||
                                                    !(rpOther.nested[i] instanceof BindingPattern bpOther) ||
                                                    !types.isSubtype(types.erasure(bpOne.type), types.erasure(bpOther.type))) {
                                                    continue NEXT_PATTERN;
                                                }
                                            }
                                        }
                                    }
                                    join.append(rpOther);
                                }
                            }

                            var nestedPatterns = join.stream().map(rp -> rp.nested[mismatchingCandidateFin]).collect(Collectors.toSet());
                            var updatedPatterns = reduceNestedPatterns(nestedPatterns, useHashes);

                            updatedPatterns = reduceRecordPatterns(updatedPatterns);
                            updatedPatterns = removeCoveredRecordPatterns(updatedPatterns);
                            updatedPatterns = reduceBindingPatterns(rpOne.fullComponentTypes()[mismatchingCandidateFin], updatedPatterns);

                            if (!nestedPatterns.equals(updatedPatterns)) {
                                if (useHashes) {
                                    current.removeAll(join);
                                }

                                for (PatternDescription nested : updatedPatterns) {
                                    PatternDescription[] newNested =
                                            Arrays.copyOf(rpOne.nested, rpOne.nested.length);
                                    newNested[mismatchingCandidateFin] = nested;
                                    current.add(new RecordPattern(rpOne.recordType(),
                                                                    rpOne.fullComponentTypes(),
                                                                    newNested));
                                }
                            }
                        }
                    }
                }

                if (!current.equals(new HashSet<>(e.getValue()))) {
                    Set<PatternDescription> result = new HashSet<>(patterns);
                    result.removeAll(e.getValue());
                    result.addAll(current);
                    return result;
                }
            }
            return patterns;
        }

        /* In the set of patterns, find those for which, given:
         * $record($nested1, $nested2, ...)
         * all the $nestedX pattern cover the given record component,
         * and replace those with a simple binding pattern over $record.
         */
        private Set<PatternDescription> reduceRecordPatterns(Set<PatternDescription> patterns) {
            var newPatterns = new HashSet<PatternDescription>();
            boolean modified = false;
            for (PatternDescription pd : patterns) {
                if (pd instanceof RecordPattern rpOne) {
                    PatternDescription reducedPattern = reduceRecordPattern(rpOne);
                    if (reducedPattern != rpOne) {
                        newPatterns.add(reducedPattern);
                        modified = true;
                        continue;
                    }
                }
                newPatterns.add(pd);
            }
            return modified ? newPatterns : patterns;
        }

        private PatternDescription reduceRecordPattern(PatternDescription pattern) {
            if (pattern instanceof RecordPattern rpOne) {
                Type[] componentType = rpOne.fullComponentTypes();
                //error recovery, ignore patterns with incorrect number of nested patterns:
                if (componentType.length != rpOne.nested.length) {
                    return pattern;
                }
                PatternDescription[] reducedNestedPatterns = null;
                boolean covered = true;
                for (int i = 0; i < componentType.length; i++) {
                    PatternDescription newNested = reduceRecordPattern(rpOne.nested[i]);
                    if (newNested != rpOne.nested[i]) {
                        if (reducedNestedPatterns == null) {
                            reducedNestedPatterns = Arrays.copyOf(rpOne.nested, rpOne.nested.length);
                        }
                        reducedNestedPatterns[i] = newNested;
                    }

                    covered &= checkCovered(componentType[i], List.of(newNested));
                }
                if (covered) {
                    return new BindingPattern(rpOne.recordType);
                } else if (reducedNestedPatterns != null) {
                    return new RecordPattern(rpOne.recordType, rpOne.fullComponentTypes(), reducedNestedPatterns);
                }
            }
            return pattern;
        }

        private Set<PatternDescription> removeCoveredRecordPatterns(Set<PatternDescription> patterns) {
            Set<Symbol> existingBindings = patterns.stream()
                                                   .filter(pd -> pd instanceof BindingPattern)
                                                   .map(pd -> ((BindingPattern) pd).type.tsym)
                                                   .collect(Collectors.toSet());
            Set<PatternDescription> result = new HashSet<>(patterns);

            for (Iterator<PatternDescription> it = result.iterator(); it.hasNext();) {
                PatternDescription pd = it.next();
                if (pd instanceof RecordPattern rp && existingBindings.contains(rp.recordType.tsym)) {
                    it.remove();
                }
            }

            return result;
        }

        public void visitTry(JCTry tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            for (JCTree resource : tree.resources) {
                if (resource instanceof JCVariableDecl variableDecl) {
                    visitVarDef(variableDecl);
                } else if (resource instanceof JCExpression expression) {
                    scan(expression);
                } else {
                    throw new AssertionError(tree);  // parser error
                }
            }

            scanStat(tree.body);
            Liveness aliveEnd = alive;

            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                alive = Liveness.ALIVE;
                JCVariableDecl param = l.head.param;
                scan(param);
                scanStat(l.head.body);
                aliveEnd = aliveEnd.or(alive);
            }
            if (tree.finalizer != null) {
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                alive = Liveness.ALIVE;
                scanStat(tree.finalizer);
                tree.finallyCanCompleteNormally = alive != Liveness.DEAD;
                if (alive == Liveness.DEAD) {
                    lint.logIfEnabled(TreeInfo.diagEndPos(tree.finalizer),
                                LintWarnings.FinallyCannotComplete);
                } else {
                    while (exits.nonEmpty()) {
                        pendingExits.append(exits.next());
                    }
                    alive = aliveEnd;
                }
            } else {
                alive = aliveEnd;
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                while (exits.nonEmpty()) pendingExits.append(exits.next());
            }
        }

        @Override
        public void visitIf(JCIf tree) {
            scan(tree.cond);
            scanStat(tree.thenpart);
            if (tree.elsepart != null) {
                Liveness aliveAfterThen = alive;
                alive = Liveness.ALIVE;
                scanStat(tree.elsepart);
                alive = alive.or(aliveAfterThen);
            } else {
                alive = Liveness.ALIVE;
            }
        }

        public void visitBreak(JCBreak tree) {
            recordExit(new PendingExit(tree));
        }

        @Override
        public void visitYield(JCYield tree) {
            scan(tree.value);
            recordExit(new PendingExit(tree));
        }

        public void visitContinue(JCContinue tree) {
            recordExit(new PendingExit(tree));
        }

        public void visitReturn(JCReturn tree) {
            scan(tree.expr);
            recordExit(new PendingExit(tree));
        }

        public void visitThrow(JCThrow tree) {
            scan(tree.expr);
            markDead();
        }

        public void visitApply(JCMethodInvocation tree) {
            scan(tree.meth);
            scan(tree.args);
        }

        public void visitNewClass(JCNewClass tree) {
            scan(tree.encl);
            scan(tree.args);
            if (tree.def != null) {
                scan(tree.def);
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (tree.type != null &&
                    tree.type.isErroneous()) {
                return;
            }

            ListBuffer<PendingExit> prevPending = pendingExits;
            Liveness prevAlive = alive;
            try {
                pendingExits = new ListBuffer<>();
                alive = Liveness.ALIVE;
                scanStat(tree.body);
                tree.canCompleteNormally = alive != Liveness.DEAD;
            }
            finally {
                pendingExits = prevPending;
                alive = prevAlive;
            }
        }

        public void visitModuleDef(JCModuleDecl tree) {
            // Do nothing for modules
        }

    /* ************************************************************************
     * main method
     *************************************************************************/

        /** Perform definite assignment/unassignment analysis on a tree.
         */
        public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
        }
        public void analyzeTree(Env<AttrContext> env, JCTree tree, TreeMaker make) {
            try {
                attrEnv = env;
                Flow.this.make = make;
                pendingExits = new ListBuffer<>();
                alive = Liveness.ALIVE;
                scan(tree);
            } finally {
                pendingExits = null;
                Flow.this.make = null;
            }
        }
    }

    private boolean isBpCovered(Type componentType, PatternDescription newNested) {
        if (newNested instanceof BindingPattern bp) {
            Type seltype = types.erasure(componentType);
            Type pattype = types.erasure(bp.type);

            return seltype.isPrimitive() ?
                    types.isUnconditionallyExact(seltype, pattype) :
                    (bp.type.isPrimitive() && types.isUnconditionallyExact(types.unboxedType(seltype), bp.type)) || types.isSubtype(seltype, pattype);
        }
        return false;
    }

    /**
     * This pass implements the second step of the dataflow analysis, namely
     * the exception analysis. This is to ensure that every checked exception that is
     * thrown is declared or caught. The analyzer uses some info that has been set by
     * the liveliness analyzer.
     */
    class FlowAnalyzer extends BaseAnalyzer {

        /** A flag that indicates whether the last statement could
         *  complete normally.
         */
        HashMap<Symbol, List<Type>> preciseRethrowTypes;

        /** The current class being defined.
         */
        JCClassDecl classDef;

        /** The list of possibly thrown declarable exceptions.
         */
        List<Type> thrown;

        /** The list of exceptions that are either caught or declared to be
         *  thrown.
         */
        List<Type> caught;

        class ThrownPendingExit extends BaseAnalyzer.PendingExit {

            Type thrown;

            ThrownPendingExit(JCTree tree, Type thrown) {
                super(tree);
                this.thrown = thrown;
            }
        }

        @Override
        void markDead() {
            //do nothing
        }

        /*-------------------- Exceptions ----------------------*/

        /** Complain that pending exceptions are not caught.
         */
        void errorUncaught() {
            for (PendingExit exit = pendingExits.next();
                 exit != null;
                 exit = pendingExits.next()) {
                if (exit instanceof ThrownPendingExit thrownExit) {
                    if (classDef != null &&
                        classDef.pos == exit.tree.pos) {
                        log.error(exit.tree.pos(),
                                  Errors.UnreportedExceptionDefaultConstructor(thrownExit.thrown));
                    } else if (exit.tree.hasTag(VARDEF) &&
                            ((JCVariableDecl)exit.tree).sym.isResourceVariable()) {
                        log.error(exit.tree.pos(),
                                  Errors.UnreportedExceptionImplicitClose(thrownExit.thrown,
                                                                          ((JCVariableDecl)exit.tree).sym.name));
                    } else {
                        log.error(exit.tree.pos(),
                                  Errors.UnreportedExceptionNeedToCatchOrThrow(thrownExit.thrown));
                    }
                } else {
                    Assert.check(log.hasErrorOn(exit.tree.pos()));
                }
            }
        }

        /** Record that exception is potentially thrown and check that it
         *  is caught.
         */
        void markThrown(JCTree tree, Type exc) {
            if (!chk.isUnchecked(tree.pos(), exc)) {
                if (!chk.isHandled(exc, caught)) {
                    pendingExits.append(new ThrownPendingExit(tree, exc));
                }
                thrown = chk.incl(exc, thrown);
            }
        }

    /* ***********************************************************************
     * Visitor methods for statements and definitions
     *************************************************************************/

        /* ------------ Visitor methods for various sorts of trees -------------*/

        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) return;

            JCClassDecl classDefPrev = classDef;
            List<Type> thrownPrev = thrown;
            List<Type> caughtPrev = caught;
            ListBuffer<PendingExit> pendingExitsPrev = pendingExits;
            Lint lintPrev = lint;
            boolean anonymousClass = tree.name == names.empty;
            pendingExits = new ListBuffer<>();
            if (!anonymousClass) {
                caught = List.nil();
            }
            classDef = tree;
            thrown = List.nil();
            lint = lint.augment(tree.sym);

            try {
                // process all the nested classes
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(CLASSDEF)) {
                        scan(l.head);
                    }
                }

                // process all the static initializers
                forEachInitializer(tree, true, def -> {
                    scan(def);
                    errorUncaught();
                });

                // in an anonymous class, add the set of thrown exceptions to
                // the throws clause of the synthetic constructor and propagate
                // outwards.
                // Changing the throws clause on the fly is okay here because
                // the anonymous constructor can't be invoked anywhere else,
                // and its type hasn't been cached.
                if (anonymousClass) {
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (TreeInfo.isConstructor(l.head)) {
                            JCMethodDecl mdef = (JCMethodDecl)l.head;
                            scan(mdef);
                            mdef.thrown = make.Types(thrown);
                            mdef.sym.type = types.createMethodTypeWithThrown(mdef.sym.type, thrown);
                        }
                    }
                    thrownPrev = chk.union(thrown, thrownPrev);
                }

                // process all the methods
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (anonymousClass && TreeInfo.isConstructor(l.head))
                        continue; // there can never be an uncaught exception.
                    if (l.head.hasTag(METHODDEF)) {
                        scan(l.head);
                        errorUncaught();
                    }
                }

                thrown = thrownPrev;
            } finally {
                pendingExits = pendingExitsPrev;
                caught = caughtPrev;
                classDef = classDefPrev;
                lint = lintPrev;
            }
        }

        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) return;

            List<Type> caughtPrev = caught;
            List<Type> mthrown = tree.sym.type.getThrownTypes();
            Lint lintPrev = lint;

            lint = lint.augment(tree.sym);

            Assert.check(pendingExits.isEmpty());

            try {
                for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                    JCVariableDecl def = l.head;
                    scan(def);
                }
                if (TreeInfo.hasConstructorCall(tree, names._super))
                    caught = chk.union(caught, mthrown);
                else if ((tree.sym.flags() & (BLOCK | STATIC)) != BLOCK)
                    caught = mthrown;
                // else we are in an instance initializer block;
                // leave caught unchanged.

                scan(tree.body);

                List<PendingExit> exits = pendingExits.toList();
                pendingExits = new ListBuffer<>();
                while (exits.nonEmpty()) {
                    PendingExit exit = exits.head;
                    exits = exits.tail;
                    if (!(exit instanceof ThrownPendingExit)) {
                        Assert.check(exit.tree.hasTag(RETURN) ||
                                         log.hasErrorOn(exit.tree.pos()));
                    } else {
                        // uncaught throws will be reported later
                        pendingExits.append(exit);
                    }
                }
            } finally {
                caught = caughtPrev;
                lint = lintPrev;
            }
        }

        public void visitVarDef(JCVariableDecl tree) {
            if (tree.init != null) {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try{
                    scan(tree.init);
                } finally {
                    lint = lintPrev;
                }
            }
        }

        public void visitBlock(JCBlock tree) {
            scan(tree.stats);
        }

        public void visitDoLoop(JCDoWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.body);
            resolveContinues(tree);
            scan(tree.cond);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitWhileLoop(JCWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.cond);
            scan(tree.body);
            resolveContinues(tree);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitForLoop(JCForLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            scan(tree.init);
            pendingExits = new ListBuffer<>();
            if (tree.cond != null) {
                scan(tree.cond);
            }
            scan(tree.body);
            resolveContinues(tree);
            scan(tree.step);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitForeachLoop(JCEnhancedForLoop tree) {
            visitVarDef(tree.var);
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            scan(tree.expr);
            pendingExits = new ListBuffer<>();
            scan(tree.body);
            resolveContinues(tree);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitLabelled(JCLabeledStatement tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.body);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitSwitch(JCSwitch tree) {
            handleSwitch(tree, tree.selector, tree.cases);
        }

        @Override
        public void visitSwitchExpression(JCSwitchExpression tree) {
            handleSwitch(tree, tree.selector, tree.cases);
        }

        private void handleSwitch(JCTree tree, JCExpression selector, List<JCCase> cases) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(selector);
            for (List<JCCase> l = cases; l.nonEmpty(); l = l.tail) {
                JCCase c = l.head;
                scan(c.labels);
                scan(c.stats);
            }
            if (tree.hasTag(SWITCH_EXPRESSION)) {
                resolveYields(tree, prevPendingExits);
            } else {
                resolveBreaks(tree, prevPendingExits);
            }
        }

        public void visitTry(JCTry tree) {
            List<Type> caughtPrev = caught;
            List<Type> thrownPrev = thrown;
            thrown = List.nil();
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                        ((JCTypeUnion)l.head.param.vartype).alternatives :
                        List.of(l.head.param.vartype);
                for (JCExpression ct : subClauses) {
                    caught = chk.incl(ct.type, caught);
                }
            }

            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            for (JCTree resource : tree.resources) {
                if (resource instanceof JCVariableDecl variableDecl) {
                    visitVarDef(variableDecl);
                } else if (resource instanceof JCExpression expression) {
                    scan(expression);
                } else {
                    throw new AssertionError(tree);  // parser error
                }
            }
            for (JCTree resource : tree.resources) {
                List<Type> closeableSupertypes = resource.type.isCompound() ?
                    types.interfaces(resource.type).prepend(types.supertype(resource.type)) :
                    List.of(resource.type);
                for (Type sup : closeableSupertypes) {
                    if (types.asSuper(sup, syms.autoCloseableType.tsym) != null) {
                        Symbol closeMethod = rs.resolveQualifiedMethod(tree,
                                attrEnv,
                                types.skipTypeVars(sup, false),
                                names.close,
                                List.nil(),
                                List.nil());
                        Type mt = types.memberType(resource.type, closeMethod);
                        if (closeMethod.kind == MTH) {
                            for (Type t : mt.getThrownTypes()) {
                                markThrown(resource, t);
                            }
                        }
                    }
                }
            }
            scan(tree.body);
            List<Type> thrownInTry = chk.union(thrown, List.of(syms.runtimeExceptionType, syms.errorType));
            thrown = thrownPrev;
            caught = caughtPrev;

            List<Type> caughtInTry = List.nil();
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                JCVariableDecl param = l.head.param;
                List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                        ((JCTypeUnion)l.head.param.vartype).alternatives :
                        List.of(l.head.param.vartype);
                List<Type> ctypes = List.nil();
                List<Type> rethrownTypes = chk.diff(thrownInTry, caughtInTry);
                for (JCExpression ct : subClauses) {
                    Type exc = ct.type;
                    if (exc != syms.unknownType) {
                        ctypes = ctypes.append(exc);
                        if (types.isSameType(exc, syms.objectType))
                            continue;
                        var pos = subClauses.size() > 1 ? ct.pos() : l.head.pos();
                        checkCaughtType(pos, exc, thrownInTry, caughtInTry);
                        caughtInTry = chk.incl(exc, caughtInTry);
                    }
                }
                scan(param);
                preciseRethrowTypes.put(param.sym, chk.intersect(ctypes, rethrownTypes));
                scan(l.head.body);
                preciseRethrowTypes.remove(param.sym);
            }
            if (tree.finalizer != null) {
                List<Type> savedThrown = thrown;
                thrown = List.nil();
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                scan(tree.finalizer);
                if (!tree.finallyCanCompleteNormally) {
                    // discard exits and exceptions from try and finally
                    thrown = chk.union(thrown, thrownPrev);
                } else {
                    thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
                    thrown = chk.union(thrown, savedThrown);
                    // FIX: this doesn't preserve source order of exits in catch
                    // versus finally!
                    while (exits.nonEmpty()) {
                        pendingExits.append(exits.next());
                    }
                }
            } else {
                thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                while (exits.nonEmpty()) pendingExits.append(exits.next());
            }
        }

        @Override
        public void visitIf(JCIf tree) {
            scan(tree.cond);
            scan(tree.thenpart);
            if (tree.elsepart != null) {
                scan(tree.elsepart);
            }
        }

        void checkCaughtType(DiagnosticPosition pos, Type exc, List<Type> thrownInTry, List<Type> caughtInTry) {
            if (chk.subset(exc, caughtInTry)) {
                log.error(pos, Errors.ExceptAlreadyCaught(exc));
            } else if (!chk.isUnchecked(pos, exc) &&
                    !isExceptionOrThrowable(exc) &&
                    !chk.intersects(exc, thrownInTry)) {
                log.error(pos, Errors.ExceptNeverThrownInTry(exc));
            } else {
                List<Type> catchableThrownTypes = chk.intersect(List.of(exc), thrownInTry);
                // 'catchableThrownTypes' cannot possibly be empty - if 'exc' was an
                // unchecked exception, the result list would not be empty, as the augmented
                // thrown set includes { RuntimeException, Error }; if 'exc' was a checked
                // exception, that would have been covered in the branch above
                if (chk.diff(catchableThrownTypes, caughtInTry).isEmpty() &&
                        !isExceptionOrThrowable(exc)) {
                    Warning key = catchableThrownTypes.length() == 1 ?
                            Warnings.UnreachableCatch(catchableThrownTypes) :
                            Warnings.UnreachableCatch1(catchableThrownTypes);
                    log.warning(pos, key);
                }
            }
        }
        //where
            private boolean isExceptionOrThrowable(Type exc) {
                return exc.tsym == syms.throwableType.tsym ||
                    exc.tsym == syms.exceptionType.tsym;
            }

        public void visitBreak(JCBreak tree) {
            recordExit(new PendingExit(tree));
        }

        public void visitYield(JCYield tree) {
            scan(tree.value);
            recordExit(new PendingExit(tree));
        }

        public void visitContinue(JCContinue tree) {
            recordExit(new PendingExit(tree));
        }

        public void visitReturn(JCReturn tree) {
            scan(tree.expr);
            recordExit(new PendingExit(tree));
        }

        public void visitThrow(JCThrow tree) {
            scan(tree.expr);
            Symbol sym = TreeInfo.symbol(tree.expr);
            if (sym != null &&
                sym.kind == VAR &&
                (sym.flags() & (FINAL | EFFECTIVELY_FINAL)) != 0 &&
                preciseRethrowTypes.get(sym) != null) {
                for (Type t : preciseRethrowTypes.get(sym)) {
                    markThrown(tree, t);
                }
            }
            else {
                markThrown(tree, tree.expr.type);
            }
            markDead();
        }

        public void visitApply(JCMethodInvocation tree) {
            scan(tree.meth);
            scan(tree.args);

            // Mark as thrown the exceptions thrown by the method being invoked
            for (List<Type> l = tree.meth.type.getThrownTypes(); l.nonEmpty(); l = l.tail)
                markThrown(tree, l.head);

            // After super(), scan initializers to uncover any exceptions they throw
            if (TreeInfo.name(tree.meth) == names._super) {
                forEachInitializer(classDef, false, def -> {
                    scan(def);
                    errorUncaught();
                });
            }
        }

        public void visitNewClass(JCNewClass tree) {
            scan(tree.encl);
            scan(tree.args);
           // scan(tree.def);
            for (List<Type> l = tree.constructorType.getThrownTypes();
                 l.nonEmpty();
                 l = l.tail) {
                markThrown(tree, l.head);
            }
            List<Type> caughtPrev = caught;
            try {
                // If the new class expression defines an anonymous class,
                // analysis of the anonymous constructor may encounter thrown
                // types which are unsubstituted type variables.
                // However, since the constructor's actual thrown types have
                // already been marked as thrown, it is safe to simply include
                // each of the constructor's formal thrown types in the set of
                // 'caught/declared to be thrown' types, for the duration of
                // the class def analysis.
                if (tree.def != null)
                    for (List<Type> l = tree.constructor.type.getThrownTypes();
                         l.nonEmpty();
                         l = l.tail) {
                        caught = chk.incl(l.head, caught);
                    }
                scan(tree.def);
            }
            finally {
                caught = caughtPrev;
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (tree.type != null &&
                    tree.type.isErroneous()) {
                return;
            }
            List<Type> prevCaught = caught;
            List<Type> prevThrown = thrown;
            ListBuffer<PendingExit> prevPending = pendingExits;
            try {
                pendingExits = new ListBuffer<>();
                caught = tree.getDescriptorType(types).getThrownTypes();
                thrown = List.nil();
                scan(tree.body);
                List<PendingExit> exits = pendingExits.toList();
                pendingExits = new ListBuffer<>();
                while (exits.nonEmpty()) {
                    PendingExit exit = exits.head;
                    exits = exits.tail;
                    if (!(exit instanceof ThrownPendingExit)) {
                        Assert.check(exit.tree.hasTag(RETURN) ||
                                        log.hasErrorOn(exit.tree.pos()));
                    } else {
                        // uncaught throws will be reported later
                        pendingExits.append(exit);
                    }
                }

                errorUncaught();
            } finally {
                pendingExits = prevPending;
                caught = prevCaught;
                thrown = prevThrown;
            }
        }

        public void visitModuleDef(JCModuleDecl tree) {
            // Do nothing for modules
        }

    /* ************************************************************************
     * main method
     *************************************************************************/

        /** Perform definite assignment/unassignment analysis on a tree.
         */
        public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
        }
        public void analyzeTree(Env<AttrContext> env, JCTree tree, TreeMaker make) {
            try {
                attrEnv = env;
                Flow.this.make = make;
                pendingExits = new ListBuffer<>();
                preciseRethrowTypes = new HashMap<>();
                this.thrown = this.caught = null;
                this.classDef = null;
                scan(tree);
            } finally {
                pendingExits = null;
                Flow.this.make = null;
                this.thrown = this.caught = null;
                this.classDef = null;
            }
        }
    }

    /**
     * Specialized pass that performs reachability analysis on a lambda
     */
    class LambdaAliveAnalyzer extends AliveAnalyzer {

        boolean inLambda;

        @Override
        public void visitReturn(JCReturn tree) {
            //ignore lambda return expression (which might not even be attributed)
            recordExit(new PendingExit(tree));
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (inLambda || tree.getBodyKind() == BodyKind.EXPRESSION) {
                return;
            }
            inLambda = true;
            try {
                super.visitLambda(tree);
            } finally {
                inLambda = false;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            //skip
        }
    }

    /**
     * Determine if alive after the given tree.
     */
    class SnippetAliveAnalyzer extends AliveAnalyzer {
        @Override
        public void visitClassDef(JCClassDecl tree) {
            //skip
        }
        @Override
        public void visitLambda(JCLambda tree) {
            //skip
        }
        public boolean isAlive() {
            return super.alive != Liveness.DEAD;
        }
    }

    class SnippetBreakToAnalyzer extends AliveAnalyzer {
        private final JCTree breakTo;
        private boolean breaksTo;

        public SnippetBreakToAnalyzer(JCTree breakTo) {
            this.breakTo = breakTo;
        }

        @Override
        public void visitBreak(JCBreak tree) {
            breaksTo |= breakTo == tree.target && super.alive == Liveness.ALIVE;
        }

        public boolean breaksTo() {
            return breaksTo;
        }
    }

    /**
     * Specialized pass that performs DA/DU on a lambda
     */
    class LambdaAssignAnalyzer extends AssignAnalyzer {
        WriteableScope enclosedSymbols;
        boolean inLambda;

        LambdaAssignAnalyzer(Env<AttrContext> env) {
            enclosedSymbols = WriteableScope.create(env.enclClass.sym);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (inLambda) {
                return;
            }
            inLambda = true;
            try {
                super.visitLambda(tree);
            } finally {
                inLambda = false;
            }
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            enclosedSymbols.enter(tree.sym);
            super.visitVarDef(tree);
        }
        @Override
        protected boolean trackable(VarSymbol sym) {
            return enclosedSymbols.includes(sym) &&
                   sym.owner.kind == MTH;
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            //skip
        }
    }

    /**
     * Specialized pass that performs inference of thrown types for lambdas.
     */
    class LambdaFlowAnalyzer extends FlowAnalyzer {
        List<Type> inferredThrownTypes;
        boolean inLambda;
        @Override
        public void visitLambda(JCLambda tree) {
            if ((tree.type != null &&
                    tree.type.isErroneous()) || inLambda) {
                return;
            }
            List<Type> prevCaught = caught;
            List<Type> prevThrown = thrown;
            ListBuffer<PendingExit> prevPending = pendingExits;
            inLambda = true;
            try {
                pendingExits = new ListBuffer<>();
                caught = List.of(syms.throwableType);
                thrown = List.nil();
                scan(tree.body);
                inferredThrownTypes = thrown;
            } finally {
                pendingExits = prevPending;
                caught = prevCaught;
                thrown = prevThrown;
                inLambda = false;
            }
        }
        @Override
        public void visitClassDef(JCClassDecl tree) {
            //skip
        }
    }

    /**
     * This pass implements (i) definite assignment analysis, which ensures that
     * each variable is assigned when used and (ii) definite unassignment analysis,
     * which ensures that no final variable is assigned more than once. This visitor
     * depends on the results of the liveliness analyzer. This pass is also used to mark
     * effectively-final local variables/parameters.
     */

    public class AssignAnalyzer extends BaseAnalyzer {

        /** The set of definitely assigned variables.
         */
        final Bits inits;

        /** The set of definitely unassigned variables.
         */
        final Bits uninits;

        /** The set of variables that are definitely unassigned everywhere
         *  in current try block. This variable is maintained lazily; it is
         *  updated only when something gets removed from uninits,
         *  typically by being assigned in reachable code.  To obtain the
         *  correct set of variables which are definitely unassigned
         *  anywhere in current try block, intersect uninitsTry and
         *  uninits.
         */
        final Bits uninitsTry;

        /** When analyzing a condition, inits and uninits are null.
         *  Instead we have:
         */
        final Bits initsWhenTrue;
        final Bits initsWhenFalse;
        final Bits uninitsWhenTrue;
        final Bits uninitsWhenFalse;

        /** A mapping from addresses to variable symbols.
         */
        protected JCVariableDecl[] vardecls;

        /** The current class being defined.
         */
        JCClassDecl classDef;

        /** The first variable sequence number in this class definition.
         */
        int firstadr;

        /** The next available variable sequence number.
         */
        protected int nextadr;

        /** The first variable sequence number in a block that can return.
         */
        protected int returnadr;

        /** The list of unreferenced automatic resources.
         */
        WriteableScope unrefdResources;

        /** Modified when processing a loop body the second time for DU analysis. */
        FlowKind flowKind = FlowKind.NORMAL;

        /** The starting position of the analyzed tree */
        int startPos;

        public class AssignPendingExit extends BaseAnalyzer.PendingExit {

            final Bits inits;
            final Bits uninits;
            final Bits exit_inits = new Bits(true);
            final Bits exit_uninits = new Bits(true);

            public AssignPendingExit(JCTree tree, final Bits inits, final Bits uninits) {
                super(tree);
                this.inits = inits;
                this.uninits = uninits;
                this.exit_inits.assign(inits);
                this.exit_uninits.assign(uninits);
            }

            @Override
            public void resolveJump() {
                inits.andSet(exit_inits);
                uninits.andSet(exit_uninits);
            }
        }

        public AssignAnalyzer() {
            this.inits = new Bits();
            uninits = new Bits();
            uninitsTry = new Bits();
            initsWhenTrue = new Bits(true);
            initsWhenFalse = new Bits(true);
            uninitsWhenTrue = new Bits(true);
            uninitsWhenFalse = new Bits(true);
        }

        private boolean isConstructor;

        @Override
        protected void markDead() {
            inits.inclRange(returnadr, nextadr);
            uninits.inclRange(returnadr, nextadr);
        }

        /*-------------- Processing variables ----------------------*/

        /** Do we need to track init/uninit state of this symbol?
         *  I.e. is symbol either a local or a blank final variable?
         */
        protected boolean trackable(VarSymbol sym) {
            return
                sym.pos >= startPos &&
                ((sym.owner.kind == MTH || sym.owner.kind == VAR ||
                isFinalUninitializedField(sym)));
        }

        boolean isFinalUninitializedField(VarSymbol sym) {
            return sym.owner.kind == TYP &&
                   ((sym.flags() & (FINAL | HASINIT | PARAMETER)) == FINAL &&
                   classDef.sym.isEnclosedBy((ClassSymbol)sym.owner));
        }

        /** Initialize new trackable variable by setting its address field
         *  to the next available sequence number and entering it under that
         *  index into the vars array.
         */
        void newVar(JCVariableDecl varDecl) {
            VarSymbol sym = varDecl.sym;
            vardecls = ArrayUtils.ensureCapacity(vardecls, nextadr);
            if ((sym.flags() & FINAL) == 0) {
                sym.flags_field |= EFFECTIVELY_FINAL;
            }
            sym.adr = nextadr;
            vardecls[nextadr] = varDecl;
            inits.excl(nextadr);
            uninits.incl(nextadr);
            nextadr++;
        }

        /** Record an initialization of a trackable variable.
         */
        void letInit(DiagnosticPosition pos, VarSymbol sym) {
            if (sym.adr >= firstadr && trackable(sym)) {
                if ((sym.flags() & EFFECTIVELY_FINAL) != 0) {
                    if (!uninits.isMember(sym.adr)) {
                        //assignment targeting an effectively final variable
                        //makes the variable lose its status of effectively final
                        //if the variable is _not_ definitively unassigned
                        sym.flags_field &= ~EFFECTIVELY_FINAL;
                    } else {
                        uninit(sym);
                    }
                }
                else if ((sym.flags() & FINAL) != 0) {
                    if ((sym.flags() & PARAMETER) != 0) {
                        if ((sym.flags() & UNION) != 0) { //multi-catch parameter
                            log.error(pos, Errors.MulticatchParameterMayNotBeAssigned(sym));
                        }
                        else {
                            log.error(pos,
                                      Errors.FinalParameterMayNotBeAssigned(sym));
                        }
                    } else if (!uninits.isMember(sym.adr)) {
                        log.error(pos, diags.errorKey(flowKind.errKey, sym));
                    } else {
                        uninit(sym);
                    }
                }
                inits.incl(sym.adr);
            } else if ((sym.flags() & FINAL) != 0) {
                log.error(pos, Errors.VarMightAlreadyBeAssigned(sym));
            }
        }
        //where
            void uninit(VarSymbol sym) {
                if (!inits.isMember(sym.adr)) {
                    // reachable assignment
                    uninits.excl(sym.adr);
                    uninitsTry.excl(sym.adr);
                } else {
                    //log.rawWarning(pos, "unreachable assignment");//DEBUG
                    uninits.excl(sym.adr);
                }
            }

        /** If tree is either a simple name or of the form this.name or
         *  C.this.name, and tree represents a trackable variable,
         *  record an initialization of the variable.
         */
        void letInit(JCTree tree) {
            tree = TreeInfo.skipParens(tree);
            if (tree.hasTag(IDENT) || tree.hasTag(SELECT)) {
                Symbol sym = TreeInfo.symbol(tree);
                if (sym.kind == VAR) {
                    letInit(tree.pos(), (VarSymbol)sym);
                }
            }
        }

        /** Check that trackable variable is initialized.
         */
        void checkInit(DiagnosticPosition pos, VarSymbol sym) {
            checkInit(pos, sym, Errors.VarMightNotHaveBeenInitialized(sym));
        }

        void checkInit(DiagnosticPosition pos, VarSymbol sym, Error errkey) {
            if ((sym.adr >= firstadr || sym.owner.kind != TYP) &&
                trackable(sym) &&
                !inits.isMember(sym.adr) &&
                (sym.flags_field & CLASH) == 0) {
                    log.error(pos, errkey);
                inits.incl(sym.adr);
            }
        }

        /** Utility method to reset several Bits instances.
         */
        private void resetBits(Bits... bits) {
            for (Bits b : bits) {
                b.reset();
            }
        }

        /** Split (duplicate) inits/uninits into WhenTrue/WhenFalse sets
         */
        void split(boolean setToNull) {
            initsWhenFalse.assign(inits);
            uninitsWhenFalse.assign(uninits);
            initsWhenTrue.assign(inits);
            uninitsWhenTrue.assign(uninits);
            if (setToNull) {
                resetBits(inits, uninits);
            }
        }

        /** Merge (intersect) inits/uninits from WhenTrue/WhenFalse sets.
         */
        protected void merge() {
            inits.assign(initsWhenFalse.andSet(initsWhenTrue));
            uninits.assign(uninitsWhenFalse.andSet(uninitsWhenTrue));
        }

    /* ************************************************************************
     * Visitor methods for statements and definitions
     *************************************************************************/

        /** Analyze an expression. Make sure to set (un)inits rather than
         *  (un)initsWhenTrue(WhenFalse) on exit.
         */
        void scanExpr(JCTree tree) {
            if (tree != null) {
                scan(tree);
                if (inits.isReset()) {
                    merge();
                }
            }
        }

        /** Analyze a list of expressions.
         */
        void scanExprs(List<? extends JCExpression> trees) {
            if (trees != null)
                for (List<? extends JCExpression> l = trees; l.nonEmpty(); l = l.tail)
                    scanExpr(l.head);
        }

        void scanPattern(JCTree tree) {
            scan(tree);
        }

        /** Analyze a condition. Make sure to set (un)initsWhenTrue(WhenFalse)
         *  rather than (un)inits on exit.
         */
        void scanCond(JCTree tree) {
            if (tree.type.isFalse()) {
                if (inits.isReset()) merge();
                initsWhenTrue.assign(inits);
                initsWhenTrue.inclRange(firstadr, nextadr);
                uninitsWhenTrue.assign(uninits);
                uninitsWhenTrue.inclRange(firstadr, nextadr);
                initsWhenFalse.assign(inits);
                uninitsWhenFalse.assign(uninits);
            } else if (tree.type.isTrue()) {
                if (inits.isReset()) merge();
                initsWhenFalse.assign(inits);
                initsWhenFalse.inclRange(firstadr, nextadr);
                uninitsWhenFalse.assign(uninits);
                uninitsWhenFalse.inclRange(firstadr, nextadr);
                initsWhenTrue.assign(inits);
                uninitsWhenTrue.assign(uninits);
            } else {
                scan(tree);
                if (!inits.isReset())
                    split(tree.type != syms.unknownType);
            }
            if (tree.type != syms.unknownType) {
                resetBits(inits, uninits);
            }
        }

        /* ------------ Visitor methods for various sorts of trees -------------*/

        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) {
                return;
            }

            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            try {
                JCClassDecl classDefPrev = classDef;
                int firstadrPrev = firstadr;
                int nextadrPrev = nextadr;
                ListBuffer<PendingExit> pendingExitsPrev = pendingExits;

                pendingExits = new ListBuffer<>();
                if (tree.name != names.empty) {
                    firstadr = nextadr;
                }
                classDef = tree;
                try {
                    // define all the static fields
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (l.head.hasTag(VARDEF)) {
                            JCVariableDecl def = (JCVariableDecl)l.head;
                            if ((def.mods.flags & STATIC) != 0) {
                                VarSymbol sym = def.sym;
                                if (trackable(sym)) {
                                    newVar(def);
                                }
                            }
                        }
                    }

                    // process all the static initializers
                    forEachInitializer(tree, true, def -> {
                        scan(def);
                        clearPendingExits(false);
                    });

                    // verify all static final fields got initialized
                    for (int i = firstadr; i < nextadr; i++) {
                        JCVariableDecl vardecl = vardecls[i];
                        VarSymbol var = vardecl.sym;
                        if (var.owner == classDef.sym && var.isStatic()) {
                            checkInit(TreeInfo.diagnosticPositionFor(var, vardecl), var);
                        }
                    }

                    // define all the instance fields
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (l.head.hasTag(VARDEF)) {
                            JCVariableDecl def = (JCVariableDecl)l.head;
                            if ((def.mods.flags & STATIC) == 0) {
                                VarSymbol sym = def.sym;
                                if (trackable(sym)) {
                                    newVar(def);
                                }
                            }
                        }
                    }

                    // process all the methods
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (l.head.hasTag(METHODDEF)) {
                            scan(l.head);
                        }
                    }

                    // process all the nested classes
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (l.head.hasTag(CLASSDEF)) {
                            scan(l.head);
                        }
                    }
                } finally {
                    pendingExits = pendingExitsPrev;
                    nextadr = nextadrPrev;
                    firstadr = firstadrPrev;
                    classDef = classDefPrev;
                }
            } finally {
                lint = lintPrev;
            }
        }

        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) {
                return;
            }

            /*  MemberEnter can generate synthetic methods ignore them
             */
            if ((tree.sym.flags() & SYNTHETIC) != 0) {
                return;
            }

            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            try {
                final Bits initsPrev = new Bits(inits);
                final Bits uninitsPrev = new Bits(uninits);
                int nextadrPrev = nextadr;
                int firstadrPrev = firstadr;
                int returnadrPrev = returnadr;

                Assert.check(pendingExits.isEmpty());
                boolean isConstructorPrev = isConstructor;
                try {
                    isConstructor = TreeInfo.isConstructor(tree);

                    // We only track field initialization inside constructors
                    if (!isConstructor) {
                        firstadr = nextadr;
                    }

                    // Mark all method parameters as DA
                    for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                        JCVariableDecl def = l.head;
                        scan(def);
                        Assert.check((def.sym.flags() & PARAMETER) != 0, "Method parameter without PARAMETER flag");
                        /*  If we are executing the code from Gen, then there can be
                         *  synthetic or mandated variables, ignore them.
                         */
                        initParam(def);
                    }
                    // else we are in an instance initializer block;
                    // leave caught unchanged.
                    scan(tree.body);

                    boolean isCompactOrGeneratedRecordConstructor = (tree.sym.flags() & Flags.COMPACT_RECORD_CONSTRUCTOR) != 0 ||
                            (tree.sym.flags() & (GENERATEDCONSTR | RECORD)) == (GENERATEDCONSTR | RECORD);
                    if (isConstructor) {
                        boolean isSynthesized = (tree.sym.flags() &
                                                 GENERATEDCONSTR) != 0;
                        for (int i = firstadr; i < nextadr; i++) {
                            JCVariableDecl vardecl = vardecls[i];
                            VarSymbol var = vardecl.sym;
                            if (var.owner == classDef.sym && !var.isStatic()) {
                                // choose the diagnostic position based on whether
                                // the ctor is default(synthesized) or not
                                if (isSynthesized && !isCompactOrGeneratedRecordConstructor) {
                                    checkInit(TreeInfo.diagnosticPositionFor(var, vardecl),
                                            var, Errors.VarNotInitializedInDefaultConstructor(var));
                                } else if (isCompactOrGeneratedRecordConstructor) {
                                    boolean isInstanceRecordField = var.enclClass().isRecord() &&
                                            (var.flags_field & (Flags.PRIVATE | Flags.FINAL | Flags.GENERATED_MEMBER | Flags.RECORD)) != 0 &&
                                            var.owner.kind == TYP;
                                    if (isInstanceRecordField) {
                                        boolean notInitialized = !inits.isMember(var.adr);
                                        if (notInitialized && uninits.isMember(var.adr) && tree.completesNormally) {
                                        /*  this way we indicate Lower that it should generate an initialization for this field
                                         *  in the compact constructor
                                         */
                                            var.flags_field |= UNINITIALIZED_FIELD;
                                        } else {
                                            checkInit(TreeInfo.diagEndPos(tree.body), var);
                                        }
                                    } else {
                                        checkInit(TreeInfo.diagnosticPositionFor(var, vardecl), var);
                                    }
                                } else {
                                    checkInit(TreeInfo.diagEndPos(tree.body), var);
                                }
                            }
                        }
                    }
                    clearPendingExits(true);
                } finally {
                    inits.assign(initsPrev);
                    uninits.assign(uninitsPrev);
                    nextadr = nextadrPrev;
                    firstadr = firstadrPrev;
                    returnadr = returnadrPrev;
                    isConstructor = isConstructorPrev;
                }
            } finally {
                lint = lintPrev;
            }
        }

        private void clearPendingExits(boolean inMethod) {
            List<PendingExit> exits = pendingExits.toList();
            pendingExits = new ListBuffer<>();
            while (exits.nonEmpty()) {
                PendingExit exit = exits.head;
                exits = exits.tail;
                Assert.check((inMethod && exit.tree.hasTag(RETURN)) ||
                                 log.hasErrorOn(exit.tree.pos()),
                             exit.tree);
                if (inMethod && isConstructor) {
                    Assert.check(exit instanceof AssignPendingExit);
                    inits.assign(((AssignPendingExit) exit).exit_inits);
                    for (int i = firstadr; i < nextadr; i++) {
                        checkInit(exit.tree.pos(), vardecls[i].sym);
                    }
                }
            }
        }
        protected void initParam(JCVariableDecl def) {
            inits.incl(def.sym.adr);
            uninits.excl(def.sym.adr);
        }

        public void visitVarDef(JCVariableDecl tree) {
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            try{
                boolean track = trackable(tree.sym);
                if (track && (tree.sym.owner.kind == MTH || tree.sym.owner.kind == VAR)) {
                    newVar(tree);
                }
                if (tree.init != null) {
                    scanExpr(tree.init);
                    if (track) {
                        letInit(tree.pos(), tree.sym);
                    }
                }
            } finally {
                lint = lintPrev;
            }
        }

        public void visitBlock(JCBlock tree) {
            int nextadrPrev = nextadr;
            scan(tree.stats);
            nextadr = nextadrPrev;
        }

        public void visitDoLoop(JCDoWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            final Bits initsSkip = new Bits(true);
            final Bits uninitsSkip = new Bits(true);
            pendingExits = new ListBuffer<>();
            int prevErrors = log.nerrors;
            do {
                final Bits uninitsEntry = new Bits(uninits);
                uninitsEntry.excludeFrom(nextadr);
                scan(tree.body);
                resolveContinues(tree);
                scanCond(tree.cond);
                if (!flowKind.isFinal()) {
                    initsSkip.assign(initsWhenFalse);
                    uninitsSkip.assign(uninitsWhenFalse);
                }
                if (log.nerrors !=  prevErrors ||
                    flowKind.isFinal() ||
                    new Bits(uninitsEntry).diffSet(uninitsWhenTrue).nextBit(firstadr)==-1)
                    break;
                inits.assign(initsWhenTrue);
                uninits.assign(uninitsEntry.andSet(uninitsWhenTrue));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;
            inits.assign(initsSkip);
            uninits.assign(uninitsSkip);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitWhileLoop(JCWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            final Bits initsSkip = new Bits(true);
            final Bits uninitsSkip = new Bits(true);
            pendingExits = new ListBuffer<>();
            int prevErrors = log.nerrors;
            final Bits uninitsEntry = new Bits(uninits);
            uninitsEntry.excludeFrom(nextadr);
            do {
                scanCond(tree.cond);
                if (!flowKind.isFinal()) {
                    initsSkip.assign(initsWhenFalse) ;
                    uninitsSkip.assign(uninitsWhenFalse);
                }
                inits.assign(initsWhenTrue);
                uninits.assign(uninitsWhenTrue);
                scan(tree.body);
                resolveContinues(tree);
                if (log.nerrors != prevErrors ||
                    flowKind.isFinal() ||
                    new Bits(uninitsEntry).diffSet(uninits).nextBit(firstadr) == -1) {
                    break;
                }
                uninits.assign(uninitsEntry.andSet(uninits));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;
            //a variable is DA/DU after the while statement, if it's DA/DU assuming the
            //branch is not taken AND if it's DA/DU before any break statement
            inits.assign(initsSkip);
            uninits.assign(uninitsSkip);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitForLoop(JCForLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            int nextadrPrev = nextadr;
            scan(tree.init);
            final Bits initsSkip = new Bits(true);
            final Bits uninitsSkip = new Bits(true);
            pendingExits = new ListBuffer<>();
            int prevErrors = log.nerrors;
            do {
                final Bits uninitsEntry = new Bits(uninits);
                uninitsEntry.excludeFrom(nextadr);
                if (tree.cond != null) {
                    scanCond(tree.cond);
                    if (!flowKind.isFinal()) {
                        initsSkip.assign(initsWhenFalse);
                        uninitsSkip.assign(uninitsWhenFalse);
                    }
                    inits.assign(initsWhenTrue);
                    uninits.assign(uninitsWhenTrue);
                } else if (!flowKind.isFinal()) {
                    initsSkip.assign(inits);
                    initsSkip.inclRange(firstadr, nextadr);
                    uninitsSkip.assign(uninits);
                    uninitsSkip.inclRange(firstadr, nextadr);
                }
                scan(tree.body);
                resolveContinues(tree);
                scan(tree.step);
                if (log.nerrors != prevErrors ||
                    flowKind.isFinal() ||
                    new Bits(uninitsEntry).diffSet(uninits).nextBit(firstadr) == -1)
                    break;
                uninits.assign(uninitsEntry.andSet(uninits));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;
            //a variable is DA/DU after a for loop, if it's DA/DU assuming the
            //branch is not taken AND if it's DA/DU before any break statement
            inits.assign(initsSkip);
            uninits.assign(uninitsSkip);
            resolveBreaks(tree, prevPendingExits);
            nextadr = nextadrPrev;
        }

        public void visitForeachLoop(JCEnhancedForLoop tree) {
            visitVarDef(tree.var);

            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            int nextadrPrev = nextadr;
            scan(tree.expr);
            final Bits initsStart = new Bits(inits);
            final Bits uninitsStart = new Bits(uninits);

            letInit(tree.pos(), tree.var.sym);
            pendingExits = new ListBuffer<>();
            int prevErrors = log.nerrors;
            do {
                final Bits uninitsEntry = new Bits(uninits);
                uninitsEntry.excludeFrom(nextadr);
                scan(tree.body);
                resolveContinues(tree);
                if (log.nerrors != prevErrors ||
                    flowKind.isFinal() ||
                    new Bits(uninitsEntry).diffSet(uninits).nextBit(firstadr) == -1)
                    break;
                uninits.assign(uninitsEntry.andSet(uninits));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;
            inits.assign(initsStart);
            uninits.assign(uninitsStart.andSet(uninits));
            resolveBreaks(tree, prevPendingExits);
            nextadr = nextadrPrev;
        }

        public void visitLabelled(JCLabeledStatement tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            scan(tree.body);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitSwitch(JCSwitch tree) {
            handleSwitch(tree, tree.selector, tree.cases, tree.isExhaustive);
        }

        public void visitSwitchExpression(JCSwitchExpression tree) {
            handleSwitch(tree, tree.selector, tree.cases, tree.isExhaustive);
        }

        private void handleSwitch(JCTree tree, JCExpression selector,
                                  List<JCCase> cases, boolean isExhaustive) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            int nextadrPrev = nextadr;
            scanExpr(selector);
            final Bits initsSwitch = new Bits(inits);
            final Bits uninitsSwitch = new Bits(uninits);
            for (List<JCCase> l = cases; l.nonEmpty(); l = l.tail) {
                inits.assign(initsSwitch);
                uninits.assign(uninits.andSet(uninitsSwitch));
                JCCase c = l.head;
                for (JCCaseLabel pat : c.labels) {
                    scanPattern(pat);
                }
                scan(c.guard);
                if (inits.isReset()) {
                    inits.assign(initsWhenTrue);
                    uninits.assign(uninitsWhenTrue);
                }
                scan(c.stats);
                if (c.completesNormally && c.caseKind == JCCase.RULE) {
                    scanSyntheticBreak(make, tree);
                }
                addVars(c.stats, initsSwitch, uninitsSwitch);
                // Warn about fall-through if lint switch fallthrough enabled.
            }
            if (!isExhaustive) {
                if (tree.hasTag(SWITCH_EXPRESSION)) {
                    markDead();
                } else if (tree.hasTag(SWITCH) && !TreeInfo.expectedExhaustive((JCSwitch) tree)) {
                    inits.assign(initsSwitch);
                    uninits.assign(uninits.andSet(uninitsSwitch));
                }
            }
            if (tree.hasTag(SWITCH_EXPRESSION)) {
                resolveYields(tree, prevPendingExits);
            } else {
                resolveBreaks(tree, prevPendingExits);
            }
            nextadr = nextadrPrev;
        }
        // where
            /** Add any variables defined in stats to inits and uninits. */
            private void addVars(List<JCStatement> stats, final Bits inits,
                                        final Bits uninits) {
                for (;stats.nonEmpty(); stats = stats.tail) {
                    JCTree stat = stats.head;
                    if (stat.hasTag(VARDEF)) {
                        int adr = ((JCVariableDecl) stat).sym.adr;
                        inits.excl(adr);
                        uninits.incl(adr);
                    }
                }
            }

        public void visitTry(JCTry tree) {
            ListBuffer<JCVariableDecl> resourceVarDecls = new ListBuffer<>();
            final Bits uninitsTryPrev = new Bits(uninitsTry);
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            final Bits initsTry = new Bits(inits);
            uninitsTry.assign(uninits);
            for (JCTree resource : tree.resources) {
                if (resource instanceof JCVariableDecl variableDecl) {
                    visitVarDef(variableDecl);
                    unrefdResources.enter(variableDecl.sym);
                    resourceVarDecls.append(variableDecl);
                } else if (resource instanceof JCExpression expression) {
                    scanExpr(expression);
                } else {
                    throw new AssertionError(tree);  // parser error
                }
            }
            scan(tree.body);
            uninitsTry.andSet(uninits);
            final Bits initsEnd = new Bits(inits);
            final Bits uninitsEnd = new Bits(uninits);
            int nextadrCatch = nextadr;

            if (!resourceVarDecls.isEmpty() &&
                    lint.isEnabled(Lint.LintCategory.TRY)) {
                for (JCVariableDecl resVar : resourceVarDecls) {
                    if (unrefdResources.includes(resVar.sym) && !resVar.sym.isUnnamedVariable()) {
                        log.warning(resVar.pos(),
                                    LintWarnings.TryResourceNotReferenced(resVar.sym));
                        unrefdResources.remove(resVar.sym);
                    }
                }
            }

            /*  The analysis of each catch should be independent.
             *  Each one should have the same initial values of inits and
             *  uninits.
             */
            final Bits initsCatchPrev = new Bits(initsTry);
            final Bits uninitsCatchPrev = new Bits(uninitsTry);

            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                JCVariableDecl param = l.head.param;
                inits.assign(initsCatchPrev);
                uninits.assign(uninitsCatchPrev);
                scan(param);
                /* If this is a TWR and we are executing the code from Gen,
                 * then there can be synthetic variables, ignore them.
                 */
                initParam(param);
                scan(l.head.body);
                initsEnd.andSet(inits);
                uninitsEnd.andSet(uninits);
                nextadr = nextadrCatch;
            }
            if (tree.finalizer != null) {
                inits.assign(initsTry);
                uninits.assign(uninitsTry);
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                scan(tree.finalizer);
                if (!tree.finallyCanCompleteNormally) {
                    // discard exits and exceptions from try and finally
                } else {
                    uninits.andSet(uninitsEnd);
                    // FIX: this doesn't preserve source order of exits in catch
                    // versus finally!
                    while (exits.nonEmpty()) {
                        PendingExit exit = exits.next();
                        if (exit instanceof AssignPendingExit assignPendingExit) {
                            assignPendingExit.exit_inits.orSet(inits);
                            assignPendingExit.exit_uninits.andSet(uninits);
                        }
                        pendingExits.append(exit);
                    }
                    inits.orSet(initsEnd);
                }
            } else {
                inits.assign(initsEnd);
                uninits.assign(uninitsEnd);
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                while (exits.nonEmpty()) pendingExits.append(exits.next());
            }
            uninitsTry.andSet(uninitsTryPrev).andSet(uninits);
        }

        public void visitConditional(JCConditional tree) {
            scanCond(tree.cond);
            final Bits initsBeforeElse = new Bits(initsWhenFalse);
            final Bits uninitsBeforeElse = new Bits(uninitsWhenFalse);
            inits.assign(initsWhenTrue);
            uninits.assign(uninitsWhenTrue);
            if (tree.truepart.type.hasTag(BOOLEAN) &&
                tree.falsepart.type.hasTag(BOOLEAN)) {
                // if b and c are boolean valued, then
                // v is (un)assigned after a?b:c when true iff
                //    v is (un)assigned after b when true and
                //    v is (un)assigned after c when true
                scanCond(tree.truepart);
                final Bits initsAfterThenWhenTrue = new Bits(initsWhenTrue);
                final Bits initsAfterThenWhenFalse = new Bits(initsWhenFalse);
                final Bits uninitsAfterThenWhenTrue = new Bits(uninitsWhenTrue);
                final Bits uninitsAfterThenWhenFalse = new Bits(uninitsWhenFalse);
                inits.assign(initsBeforeElse);
                uninits.assign(uninitsBeforeElse);
                scanCond(tree.falsepart);
                initsWhenTrue.andSet(initsAfterThenWhenTrue);
                initsWhenFalse.andSet(initsAfterThenWhenFalse);
                uninitsWhenTrue.andSet(uninitsAfterThenWhenTrue);
                uninitsWhenFalse.andSet(uninitsAfterThenWhenFalse);
            } else {
                scanExpr(tree.truepart);
                final Bits initsAfterThen = new Bits(inits);
                final Bits uninitsAfterThen = new Bits(uninits);
                inits.assign(initsBeforeElse);
                uninits.assign(uninitsBeforeElse);
                scanExpr(tree.falsepart);
                inits.andSet(initsAfterThen);
                uninits.andSet(uninitsAfterThen);
            }
        }

        public void visitIf(JCIf tree) {
            scanCond(tree.cond);
            final Bits initsBeforeElse = new Bits(initsWhenFalse);
            final Bits uninitsBeforeElse = new Bits(uninitsWhenFalse);
            inits.assign(initsWhenTrue);
            uninits.assign(uninitsWhenTrue);
            scan(tree.thenpart);
            if (tree.elsepart != null) {
                final Bits initsAfterThen = new Bits(inits);
                final Bits uninitsAfterThen = new Bits(uninits);
                inits.assign(initsBeforeElse);
                uninits.assign(uninitsBeforeElse);
                scan(tree.elsepart);
                inits.andSet(initsAfterThen);
                uninits.andSet(uninitsAfterThen);
            } else {
                inits.andSet(initsBeforeElse);
                uninits.andSet(uninitsBeforeElse);
            }
        }

        @Override
        public void visitBreak(JCBreak tree) {
            recordExit(new AssignPendingExit(tree, inits, uninits));
        }

        @Override
        public void visitYield(JCYield tree) {
            JCSwitchExpression expr = (JCSwitchExpression) tree.target;
            if (expr != null && expr.type.hasTag(BOOLEAN)) {
                scanCond(tree.value);
                Bits initsAfterBreakWhenTrue = new Bits(initsWhenTrue);
                Bits initsAfterBreakWhenFalse = new Bits(initsWhenFalse);
                Bits uninitsAfterBreakWhenTrue = new Bits(uninitsWhenTrue);
                Bits uninitsAfterBreakWhenFalse = new Bits(uninitsWhenFalse);
                PendingExit exit = new PendingExit(tree) {
                    @Override
                    void resolveJump() {
                        if (!inits.isReset()) {
                            split(true);
                        }
                        initsWhenTrue.andSet(initsAfterBreakWhenTrue);
                        initsWhenFalse.andSet(initsAfterBreakWhenFalse);
                        uninitsWhenTrue.andSet(uninitsAfterBreakWhenTrue);
                        uninitsWhenFalse.andSet(uninitsAfterBreakWhenFalse);
                    }
                };
                merge();
                recordExit(exit);
                return ;
            } else {
                scanExpr(tree.value);
                recordExit(new AssignPendingExit(tree, inits, uninits));
            }
        }

        @Override
        public void visitContinue(JCContinue tree) {
            recordExit(new AssignPendingExit(tree, inits, uninits));
        }

        @Override
        public void visitReturn(JCReturn tree) {
            scanExpr(tree.expr);
            recordExit(new AssignPendingExit(tree, inits, uninits));
        }

        public void visitThrow(JCThrow tree) {
            scanExpr(tree.expr);
            markDead();
        }

        public void visitApply(JCMethodInvocation tree) {
            scanExpr(tree.meth);
            scanExprs(tree.args);

            // Handle superclass constructor invocations
            if (isConstructor) {

                // If super(): at this point all initialization blocks will execute
                Name name = TreeInfo.name(tree.meth);
                if (name == names._super) {
                    forEachInitializer(classDef, false, def -> {
                        scan(def);
                        clearPendingExits(false);
                    });
                }

                // If this(): at this point all final uninitialized fields will get initialized
                else if (name == names._this) {
                    for (int address = firstadr; address < nextadr; address++) {
                        VarSymbol sym = vardecls[address].sym;
                        if (isFinalUninitializedField(sym) && !sym.isStatic())
                            letInit(tree.pos(), sym);
                    }
                }
            }
        }

        public void visitNewClass(JCNewClass tree) {
            scanExpr(tree.encl);
            scanExprs(tree.args);
            scan(tree.def);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            final Bits prevUninits = new Bits(uninits);
            final Bits prevUninitsTry = new Bits(uninitsTry);
            final Bits prevInits = new Bits(inits);
            int returnadrPrev = returnadr;
            int nextadrPrev = nextadr;
            ListBuffer<PendingExit> prevPending = pendingExits;
            try {
                // JLS 16.1.10: No rule allows V to be definitely unassigned before a lambda
                // body. This is by design: a variable that was definitely unassigned before the
                // lambda body may end up being assigned to later on, so we cannot conclude that
                // the variable will be unassigned when the body is executed.
                uninits.excludeFrom(firstadr);
                returnadr = nextadr;
                pendingExits = new ListBuffer<>();
                for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                    JCVariableDecl def = l.head;
                    scan(def);
                    inits.incl(def.sym.adr);
                    uninits.excl(def.sym.adr);
                }
                if (tree.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
                    scanExpr(tree.body);
                } else {
                    scan(tree.body);
                }
            }
            finally {
                returnadr = returnadrPrev;
                uninits.assign(prevUninits);
                uninitsTry.assign(prevUninitsTry);
                inits.assign(prevInits);
                pendingExits = prevPending;
                nextadr = nextadrPrev;
            }
        }

        public void visitNewArray(JCNewArray tree) {
            scanExprs(tree.dims);
            scanExprs(tree.elems);
        }

        public void visitAssert(JCAssert tree) {
            final Bits initsExit = new Bits(inits);
            final Bits uninitsExit = new Bits(uninits);
            scanCond(tree.cond);
            uninitsExit.andSet(uninitsWhenTrue);
            if (tree.detail != null) {
                inits.assign(initsWhenFalse);
                uninits.assign(uninitsWhenFalse);
                scanExpr(tree.detail);
            }
            inits.assign(initsExit);
            uninits.assign(uninitsExit);
        }

        public void visitAssign(JCAssign tree) {
            if (!TreeInfo.isIdentOrThisDotIdent(tree.lhs))
                scanExpr(tree.lhs);
            scanExpr(tree.rhs);
            letInit(tree.lhs);
        }

        // check fields accessed through this.<field> are definitely
        // assigned before reading their value
        public void visitSelect(JCFieldAccess tree) {
            super.visitSelect(tree);
            if (TreeInfo.isThisQualifier(tree.selected) &&
                tree.sym.kind == VAR) {
                checkInit(tree.pos(), (VarSymbol)tree.sym);
            }
        }

        public void visitAssignop(JCAssignOp tree) {
            scanExpr(tree.lhs);
            scanExpr(tree.rhs);
            letInit(tree.lhs);
        }

        public void visitUnary(JCUnary tree) {
            switch (tree.getTag()) {
            case NOT:
                scanCond(tree.arg);
                final Bits t = new Bits(initsWhenFalse);
                initsWhenFalse.assign(initsWhenTrue);
                initsWhenTrue.assign(t);
                t.assign(uninitsWhenFalse);
                uninitsWhenFalse.assign(uninitsWhenTrue);
                uninitsWhenTrue.assign(t);
                break;
            case PREINC: case POSTINC:
            case PREDEC: case POSTDEC:
                scanExpr(tree.arg);
                letInit(tree.arg);
                break;
            default:
                scanExpr(tree.arg);
            }
        }

        public void visitBinary(JCBinary tree) {
            switch (tree.getTag()) {
            case AND:
                scanCond(tree.lhs);
                final Bits initsWhenFalseLeft = new Bits(initsWhenFalse);
                final Bits uninitsWhenFalseLeft = new Bits(uninitsWhenFalse);
                inits.assign(initsWhenTrue);
                uninits.assign(uninitsWhenTrue);
                scanCond(tree.rhs);
                initsWhenFalse.andSet(initsWhenFalseLeft);
                uninitsWhenFalse.andSet(uninitsWhenFalseLeft);
                break;
            case OR:
                scanCond(tree.lhs);
                final Bits initsWhenTrueLeft = new Bits(initsWhenTrue);
                final Bits uninitsWhenTrueLeft = new Bits(uninitsWhenTrue);
                inits.assign(initsWhenFalse);
                uninits.assign(uninitsWhenFalse);
                scanCond(tree.rhs);
                initsWhenTrue.andSet(initsWhenTrueLeft);
                uninitsWhenTrue.andSet(uninitsWhenTrueLeft);
                break;
            default:
                scanExpr(tree.lhs);
                scanExpr(tree.rhs);
            }
        }

        public void visitIdent(JCIdent tree) {
            if (tree.sym.kind == VAR) {
                checkInit(tree.pos(), (VarSymbol)tree.sym);
                referenced(tree.sym);
            }
        }

        @Override
        public void visitTypeTest(JCInstanceOf tree) {
            scanExpr(tree.expr);
            scan(tree.pattern);
        }

        @Override
        public void visitBindingPattern(JCBindingPattern tree) {
            scan(tree.var);
            initParam(tree.var);
        }

        void referenced(Symbol sym) {
            unrefdResources.remove(sym);
        }

        public void visitAnnotatedType(JCAnnotatedType tree) {
            // annotations don't get scanned
            tree.underlyingType.accept(this);
        }

        public void visitModuleDef(JCModuleDecl tree) {
            // Do nothing for modules
        }

    /* ************************************************************************
     * main method
     *************************************************************************/

        /** Perform definite assignment/unassignment analysis on a tree.
         */
        public void analyzeTree(Env<?> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
         }

        public void analyzeTree(Env<?> env, JCTree tree, TreeMaker make) {
            try {
                startPos = tree.pos().getStartPosition();

                if (vardecls == null)
                    vardecls = new JCVariableDecl[32];
                else
                    for (int i=0; i<vardecls.length; i++)
                        vardecls[i] = null;
                firstadr = 0;
                nextadr = 0;
                Flow.this.make = make;
                pendingExits = new ListBuffer<>();
                this.classDef = null;
                unrefdResources = WriteableScope.create(env.enclClass.sym);
                scan(tree);
            } finally {
                // note that recursive invocations of this method fail hard
                startPos = -1;
                resetBits(inits, uninits, uninitsTry, initsWhenTrue,
                        initsWhenFalse, uninitsWhenTrue, uninitsWhenFalse);
                if (vardecls != null) {
                    for (int i=0; i<vardecls.length; i++)
                        vardecls[i] = null;
                }
                firstadr = 0;
                nextadr = 0;
                Flow.this.make = null;
                pendingExits = null;
                this.classDef = null;
                unrefdResources = null;
            }
        }
    }

    /**
     * This pass implements the last step of the dataflow analysis, namely
     * the effectively-final analysis check. This checks that every local variable
     * reference from a lambda body/local inner class is either final or effectively final.
     * Additional this also checks that every variable that is used as an operand to
     * try-with-resources is final or effectively final.
     * As effectively final variables are marked as such during DA/DU, this pass must run after
     * AssignAnalyzer.
     */
    class CaptureAnalyzer extends BaseAnalyzer {

        JCTree currentTree; //local class or lambda
        WriteableScope declaredInsideGuard;

        @Override
        void markDead() {
            //do nothing
        }

        void checkEffectivelyFinal(DiagnosticPosition pos, VarSymbol sym) {
            if (currentTree != null &&
                    sym.owner.kind == MTH &&
                    sym.pos < getCurrentTreeStartPosition()) {
                switch (currentTree.getTag()) {
                    case CLASSDEF:
                    case CASE:
                    case LAMBDA:
                        if ((sym.flags() & (EFFECTIVELY_FINAL | FINAL)) == 0) {
                           reportEffectivelyFinalError(pos, sym);
                        }
                }
            }
        }

        int getCurrentTreeStartPosition() {
            return currentTree instanceof JCCase cse ? cse.guard.getStartPosition()
                                                     : currentTree.getStartPosition();
        }

        void letInit(JCTree tree) {
            tree = TreeInfo.skipParens(tree);
            if (tree.hasTag(IDENT) || tree.hasTag(SELECT)) {
                Symbol sym = TreeInfo.symbol(tree);
                if (currentTree != null) {
                    switch (currentTree.getTag()) {
                        case CLASSDEF, LAMBDA -> {
                            if (sym.kind == VAR &&
                                sym.owner.kind == MTH &&
                                ((VarSymbol)sym).pos < currentTree.getStartPosition()) {
                                reportEffectivelyFinalError(tree, sym);
                            }
                        }
                        case CASE -> {
                            if (!declaredInsideGuard.includes(sym)) {
                                log.error(tree.pos(), Errors.CannotAssignNotDeclaredGuard(sym));
                            }
                        }
                    }
                }
            }
        }

        void reportEffectivelyFinalError(DiagnosticPosition pos, Symbol sym) {
            Fragment subKey = switch (currentTree.getTag()) {
                case LAMBDA -> Fragments.Lambda;
                case CASE -> Fragments.Guard;
                case CLASSDEF -> Fragments.InnerCls;
                default -> throw new AssertionError("Unexpected tree kind: " + currentTree.getTag());
            };
            log.error(pos, Errors.CantRefNonEffectivelyFinalVar(sym, diags.fragment(subKey)));
        }

    /* ***********************************************************************
     * Visitor methods for statements and definitions
     *************************************************************************/

        /* ------------ Visitor methods for various sorts of trees -------------*/

        public void visitClassDef(JCClassDecl tree) {
            JCTree prevTree = currentTree;
            try {
                currentTree = tree.sym.isDirectlyOrIndirectlyLocal() ? tree : null;
                super.visitClassDef(tree);
            } finally {
                currentTree = prevTree;
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            JCTree prevTree = currentTree;
            try {
                currentTree = tree;
                super.visitLambda(tree);
            } finally {
                currentTree = prevTree;
            }
        }

        @Override
        public void visitBindingPattern(JCBindingPattern tree) {
            scan(tree.var);
        }

        @Override
        public void visitCase(JCCase tree) {
            scan(tree.labels);
            if (tree.guard != null) {
                JCTree prevTree = currentTree;
                WriteableScope prevDeclaredInsideGuard = declaredInsideGuard;
                try {
                    currentTree = tree;
                    declaredInsideGuard = WriteableScope.create(attrEnv.enclClass.sym);
                    scan(tree.guard);
                } finally {
                    currentTree = prevTree;
                    declaredInsideGuard = prevDeclaredInsideGuard;
                }
            }
            scan(tree.stats);
        }

        @Override
        public void visitRecordPattern(JCRecordPattern tree) {
            scan(tree.deconstructor);
            scan(tree.nested);
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (tree.sym.kind == VAR) {
                checkEffectivelyFinal(tree, (VarSymbol)tree.sym);
            }
        }

        public void visitAssign(JCAssign tree) {
            JCTree lhs = TreeInfo.skipParens(tree.lhs);
            if (!(lhs instanceof JCIdent)) {
                scan(lhs);
            }
            scan(tree.rhs);
            letInit(lhs);
        }

        public void visitAssignop(JCAssignOp tree) {
            scan(tree.lhs);
            scan(tree.rhs);
            letInit(tree.lhs);
        }

        public void visitUnary(JCUnary tree) {
            switch (tree.getTag()) {
                case PREINC: case POSTINC:
                case PREDEC: case POSTDEC:
                    scan(tree.arg);
                    letInit(tree.arg);
                    break;
                default:
                    scan(tree.arg);
            }
        }

        public void visitTry(JCTry tree) {
            for (JCTree resource : tree.resources) {
                if (!resource.hasTag(VARDEF)) {
                    Symbol var = TreeInfo.symbol(resource);
                    if (var != null && (var.flags() & (FINAL | EFFECTIVELY_FINAL)) == 0) {
                        log.error(resource.pos(), Errors.TryWithResourcesExprEffectivelyFinalVar(var));
                    }
                }
            }
            super.visitTry(tree);
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            if (declaredInsideGuard != null) {
                declaredInsideGuard.enter(tree.sym);
            }
            super.visitVarDef(tree);
        }

        @Override
        public void visitYield(JCYield tree) {
            scan(tree.value);
        }

        public void visitModuleDef(JCModuleDecl tree) {
            // Do nothing for modules
        }

    /* ************************************************************************
     * main method
     *************************************************************************/

        /** Perform definite assignment/unassignment analysis on a tree.
         */
        public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
        }
        public void analyzeTree(Env<AttrContext> env, JCTree tree, TreeMaker make) {
            try {
                attrEnv = env;
                Flow.this.make = make;
                pendingExits = new ListBuffer<>();
                scan(tree);
            } finally {
                pendingExits = null;
                Flow.this.make = null;
            }
        }
    }

    enum Liveness {
        ALIVE {
            @Override
            public Liveness or(Liveness other) {
                return this;
            }
            @Override
            public Liveness and(Liveness other) {
                return other;
            }
        },
        DEAD {
            @Override
            public Liveness or(Liveness other) {
                return other;
            }
            @Override
            public Liveness and(Liveness other) {
                return this;
            }
        },
        RECOVERY {
            @Override
            public Liveness or(Liveness other) {
                if (other == ALIVE) {
                    return ALIVE;
                } else {
                    return this;
                }
            }
            @Override
            public Liveness and(Liveness other) {
                if (other == DEAD) {
                    return DEAD;
                } else {
                    return this;
                }
            }
        };

        public abstract Liveness or(Liveness other);
        public abstract Liveness and(Liveness other);
        public Liveness or(boolean value) {
            return or(from(value));
        }
        public Liveness and(boolean value) {
            return and(from(value));
        }
        public static Liveness from(boolean value) {
            return value ? ALIVE : DEAD;
        }
    }

    sealed interface PatternDescription { }
    public PatternDescription makePatternDescription(Type selectorType, JCPattern pattern) {
        if (pattern instanceof JCBindingPattern binding) {
            Type type = !selectorType.isPrimitive() && types.isSubtype(selectorType, binding.type)
                    ? selectorType : binding.type;
            return new BindingPattern(type);
        } else if (pattern instanceof JCRecordPattern record) {
            Type[] componentTypes;

            if (!record.type.isErroneous()) {
                componentTypes = ((ClassSymbol) record.type.tsym).getRecordComponents()
                        .map(r -> types.memberType(record.type, r))
                        .toArray(s -> new Type[s]);
            }
            else {
                componentTypes = record.nested.map(t -> types.createErrorType(t.type)).toArray(s -> new Type[s]);;
            }

            PatternDescription[] nestedDescriptions =
                    new PatternDescription[record.nested.size()];
            int i = 0;
            for (List<JCPattern> it = record.nested;
                 it.nonEmpty();
                 it = it.tail, i++) {
                Type componentType = i < componentTypes.length ? componentTypes[i]
                                                               : syms.errType;
                nestedDescriptions[i] = makePatternDescription(types.erasure(componentType), it.head);
            }
            return new RecordPattern(record.type, componentTypes, nestedDescriptions);
        } else if (pattern instanceof JCAnyPattern) {
            return new BindingPattern(selectorType);
        } else {
            throw Assert.error();
        }
    }
    record BindingPattern(Type type) implements PatternDescription {
        @Override
        public int hashCode() {
            return type.tsym.hashCode();
        }
        @Override
        public boolean equals(Object o) {
            return o instanceof BindingPattern other &&
                    type.tsym == other.type.tsym;
        }
        @Override
        public String toString() {
            return type.tsym + " _";
        }
    }
    record RecordPattern(Type recordType, int _hashCode, Type[] fullComponentTypes, PatternDescription... nested) implements PatternDescription {

        public RecordPattern(Type recordType, Type[] fullComponentTypes, PatternDescription[] nested) {
            this(recordType, hashCode(-1, recordType, nested), fullComponentTypes, nested);
        }

        @Override
        public int hashCode() {
            return _hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RecordPattern other &&
                    recordType.tsym == other.recordType.tsym &&
                    Arrays.equals(nested, other.nested);
        }

        public int hashCode(int excludeComponent) {
            return hashCode(excludeComponent, recordType, nested);
        }

        public static int hashCode(int excludeComponent, Type recordType, PatternDescription... nested) {
            int hash = 5;
            hash =  41 * hash + recordType.tsym.hashCode();
            for (int  i = 0; i < nested.length; i++) {
                if (i != excludeComponent) {
                    hash = 41 * hash + nested[i].hashCode();
                }
            }
            return hash;
        }
        @Override
        public String toString() {
            return recordType.tsym + "(" + Arrays.stream(nested)
                    .map(pd -> pd.toString())
                    .collect(Collectors.joining(", ")) + ")";
        }
    }
}
