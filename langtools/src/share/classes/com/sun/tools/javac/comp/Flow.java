/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

//todo: one might eliminate uninits.andSets when monotonic

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;

/** This pass implements dataflow analysis for Java programs.
 *  Liveness analysis checks that every statement is reachable.
 *  Exception analysis ensures that every checked exception that is
 *  thrown is declared or caught.  Definite assignment analysis
 *  ensures that each variable is assigned when used.  Definite
 *  unassignment analysis ensures that no final variable is assigned
 *  more than once.
 *
 *  <p>The second edition of the JLS has a number of problems in the
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
 *  concept is only used for construcrors.
 *
 *  <p>There is no spec in JLS2 for when a variable is definitely
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
 *  global variable "Set<Type> thrown" that records the type of all
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
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Flow extends TreeScanner {
    protected static final Context.Key<Flow> flowKey =
        new Context.Key<Flow>();

    private final Names names;
    private final Log log;
    private final Symtab syms;
    private final Types types;
    private final Check chk;
    private       TreeMaker make;
    private       Lint lint;

    public static Flow instance(Context context) {
        Flow instance = context.get(flowKey);
        if (instance == null)
            instance = new Flow(context);
        return instance;
    }

    protected Flow(Context context) {
        context.put(flowKey, this);

        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        chk = Check.instance(context);
        lint = Lint.instance(context);
    }

    /** A flag that indicates whether the last statement could
     *  complete normally.
     */
    private boolean alive;

    /** The set of definitely assigned variables.
     */
    Bits inits;

    /** The set of definitely unassigned variables.
     */
    Bits uninits;

    /** The set of variables that are definitely unassigned everywhere
     *  in current try block. This variable is maintained lazily; it is
     *  updated only when something gets removed from uninits,
     *  typically by being assigned in reachable code.  To obtain the
     *  correct set of variables which are definitely unassigned
     *  anywhere in current try block, intersect uninitsTry and
     *  uninits.
     */
    Bits uninitsTry;

    /** When analyzing a condition, inits and uninits are null.
     *  Instead we have:
     */
    Bits initsWhenTrue;
    Bits initsWhenFalse;
    Bits uninitsWhenTrue;
    Bits uninitsWhenFalse;

    /** A mapping from addresses to variable symbols.
     */
    VarSymbol[] vars;

    /** The current class being defined.
     */
    JCClassDecl classDef;

    /** The first variable sequence number in this class definition.
     */
    int firstadr;

    /** The next available variable sequence number.
     */
    int nextadr;

    /** The list of possibly thrown declarable exceptions.
     */
    List<Type> thrown;

    /** The list of exceptions that are either caught or declared to be
     *  thrown.
     */
    List<Type> caught;

    /** Set when processing a loop body the second time for DU analysis. */
    boolean loopPassTwo = false;

    /*-------------------- Environments ----------------------*/

    /** A pending exit.  These are the statements return, break, and
     *  continue.  In addition, exception-throwing expressions or
     *  statements are put here when not known to be caught.  This
     *  will typically result in an error unless it is within a
     *  try-finally whose finally block cannot complete normally.
     */
    static class PendingExit {
        JCTree tree;
        Bits inits;
        Bits uninits;
        Type thrown;
        PendingExit(JCTree tree, Bits inits, Bits uninits) {
            this.tree = tree;
            this.inits = inits.dup();
            this.uninits = uninits.dup();
        }
        PendingExit(JCTree tree, Type thrown) {
            this.tree = tree;
            this.thrown = thrown;
        }
    }

    /** The currently pending exits that go from current inner blocks
     *  to an enclosing block, in source order.
     */
    ListBuffer<PendingExit> pendingExits;

    /*-------------------- Exceptions ----------------------*/

    /** Complain that pending exceptions are not caught.
     */
    void errorUncaught() {
        for (PendingExit exit = pendingExits.next();
             exit != null;
             exit = pendingExits.next()) {
            boolean synthetic = classDef != null &&
                classDef.pos == exit.tree.pos;
            log.error(exit.tree.pos(),
                      synthetic
                      ? "unreported.exception.default.constructor"
                      : "unreported.exception.need.to.catch.or.throw",
                      exit.thrown);
        }
    }

    /** Record that exception is potentially thrown and check that it
     *  is caught.
     */
    void markThrown(JCTree tree, Type exc) {
        if (!chk.isUnchecked(tree.pos(), exc)) {
            if (!chk.isHandled(exc, caught))
                pendingExits.append(new PendingExit(tree, exc));
            thrown = chk.incl(exc, thrown);
        }
    }

    /*-------------- Processing variables ----------------------*/

    /** Do we need to track init/uninit state of this symbol?
     *  I.e. is symbol either a local or a blank final variable?
     */
    boolean trackable(VarSymbol sym) {
        return
            (sym.owner.kind == MTH ||
             ((sym.flags() & (FINAL | HASINIT | PARAMETER)) == FINAL &&
              classDef.sym.isEnclosedBy((ClassSymbol)sym.owner)));
    }

    /** Initialize new trackable variable by setting its address field
     *  to the next available sequence number and entering it under that
     *  index into the vars array.
     */
    void newVar(VarSymbol sym) {
        if (nextadr == vars.length) {
            VarSymbol[] newvars = new VarSymbol[nextadr * 2];
            System.arraycopy(vars, 0, newvars, 0, nextadr);
            vars = newvars;
        }
        sym.adr = nextadr;
        vars[nextadr] = sym;
        inits.excl(nextadr);
        uninits.incl(nextadr);
        nextadr++;
    }

    /** Record an initialization of a trackable variable.
     */
    void letInit(DiagnosticPosition pos, VarSymbol sym) {
        if (sym.adr >= firstadr && trackable(sym)) {
            if ((sym.flags() & FINAL) != 0) {
                if ((sym.flags() & PARAMETER) != 0) {
                    log.error(pos, "final.parameter.may.not.be.assigned",
                              sym);
                } else if (!uninits.isMember(sym.adr)) {
                    log.error(pos,
                              loopPassTwo
                              ? "var.might.be.assigned.in.loop"
                              : "var.might.already.be.assigned",
                              sym);
                } else if (!inits.isMember(sym.adr)) {
                    // reachable assignment
                    uninits.excl(sym.adr);
                    uninitsTry.excl(sym.adr);
                } else {
                    //log.rawWarning(pos, "unreachable assignment");//DEBUG
                    uninits.excl(sym.adr);
                }
            }
            inits.incl(sym.adr);
        } else if ((sym.flags() & FINAL) != 0) {
            log.error(pos, "var.might.already.be.assigned", sym);
        }
    }

    /** If tree is either a simple name or of the form this.name or
     *  C.this.name, and tree represents a trackable variable,
     *  record an initialization of the variable.
     */
    void letInit(JCTree tree) {
        tree = TreeInfo.skipParens(tree);
        if (tree.getTag() == JCTree.IDENT || tree.getTag() == JCTree.SELECT) {
            Symbol sym = TreeInfo.symbol(tree);
            letInit(tree.pos(), (VarSymbol)sym);
        }
    }

    /** Check that trackable variable is initialized.
     */
    void checkInit(DiagnosticPosition pos, VarSymbol sym) {
        if ((sym.adr >= firstadr || sym.owner.kind != TYP) &&
            trackable(sym) &&
            !inits.isMember(sym.adr)) {
            log.error(pos, "var.might.not.have.been.initialized",
                      sym);
            inits.incl(sym.adr);
        }
    }

    /*-------------------- Handling jumps ----------------------*/

    /** Record an outward transfer of control. */
    void recordExit(JCTree tree) {
        pendingExits.append(new PendingExit(tree, inits, uninits));
        markDead();
    }

    /** Resolve all breaks of this statement. */
    boolean resolveBreaks(JCTree tree,
                          ListBuffer<PendingExit> oldPendingExits) {
        boolean result = false;
        List<PendingExit> exits = pendingExits.toList();
        pendingExits = oldPendingExits;
        for (; exits.nonEmpty(); exits = exits.tail) {
            PendingExit exit = exits.head;
            if (exit.tree.getTag() == JCTree.BREAK &&
                ((JCBreak) exit.tree).target == tree) {
                inits.andSet(exit.inits);
                uninits.andSet(exit.uninits);
                result = true;
            } else {
                pendingExits.append(exit);
            }
        }
        return result;
    }

    /** Resolve all continues of this statement. */
    boolean resolveContinues(JCTree tree) {
        boolean result = false;
        List<PendingExit> exits = pendingExits.toList();
        pendingExits = new ListBuffer<PendingExit>();
        for (; exits.nonEmpty(); exits = exits.tail) {
            PendingExit exit = exits.head;
            if (exit.tree.getTag() == JCTree.CONTINUE &&
                ((JCContinue) exit.tree).target == tree) {
                inits.andSet(exit.inits);
                uninits.andSet(exit.uninits);
                result = true;
            } else {
                pendingExits.append(exit);
            }
        }
        return result;
    }

    /** Record that statement is unreachable.
     */
    void markDead() {
        inits.inclRange(firstadr, nextadr);
        uninits.inclRange(firstadr, nextadr);
        alive = false;
    }

    /** Split (duplicate) inits/uninits into WhenTrue/WhenFalse sets
     */
    void split() {
        initsWhenFalse = inits.dup();
        uninitsWhenFalse = uninits.dup();
        initsWhenTrue = inits;
        uninitsWhenTrue = uninits;
        inits = uninits = null;
    }

    /** Merge (intersect) inits/uninits from WhenTrue/WhenFalse sets.
     */
    void merge() {
        inits = initsWhenFalse.andSet(initsWhenTrue);
        uninits = uninitsWhenFalse.andSet(uninitsWhenTrue);
    }

/* ************************************************************************
 * Visitor methods for statements and definitions
 *************************************************************************/

    /** Analyze a definition.
     */
    void scanDef(JCTree tree) {
        scanStat(tree);
        if (tree != null && tree.getTag() == JCTree.BLOCK && !alive) {
            log.error(tree.pos(),
                      "initializer.must.be.able.to.complete.normally");
        }
    }

    /** Analyze a statement. Check that statement is reachable.
     */
    void scanStat(JCTree tree) {
        if (!alive && tree != null) {
            log.error(tree.pos(), "unreachable.stmt");
            if (tree.getTag() != JCTree.SKIP) alive = true;
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

    /** Analyze an expression. Make sure to set (un)inits rather than
     *  (un)initsWhenTrue(WhenFalse) on exit.
     */
    void scanExpr(JCTree tree) {
        if (tree != null) {
            scan(tree);
            if (inits == null) merge();
        }
    }

    /** Analyze a list of expressions.
     */
    void scanExprs(List<? extends JCExpression> trees) {
        if (trees != null)
            for (List<? extends JCExpression> l = trees; l.nonEmpty(); l = l.tail)
                scanExpr(l.head);
    }

    /** Analyze a condition. Make sure to set (un)initsWhenTrue(WhenFalse)
     *  rather than (un)inits on exit.
     */
    void scanCond(JCTree tree) {
        if (tree.type.isFalse()) {
            if (inits == null) merge();
            initsWhenTrue = inits.dup();
            initsWhenTrue.inclRange(firstadr, nextadr);
            uninitsWhenTrue = uninits.dup();
            uninitsWhenTrue.inclRange(firstadr, nextadr);
            initsWhenFalse = inits;
            uninitsWhenFalse = uninits;
        } else if (tree.type.isTrue()) {
            if (inits == null) merge();
            initsWhenFalse = inits.dup();
            initsWhenFalse.inclRange(firstadr, nextadr);
            uninitsWhenFalse = uninits.dup();
            uninitsWhenFalse.inclRange(firstadr, nextadr);
            initsWhenTrue = inits;
            uninitsWhenTrue = uninits;
        } else {
            scan(tree);
            if (inits != null) split();
        }
        inits = uninits = null;
    }

    /* ------------ Visitor methods for various sorts of trees -------------*/

    public void visitClassDef(JCClassDecl tree) {
        if (tree.sym == null) return;

        JCClassDecl classDefPrev = classDef;
        List<Type> thrownPrev = thrown;
        List<Type> caughtPrev = caught;
        boolean alivePrev = alive;
        int firstadrPrev = firstadr;
        int nextadrPrev = nextadr;
        ListBuffer<PendingExit> pendingExitsPrev = pendingExits;
        Lint lintPrev = lint;

        pendingExits = new ListBuffer<PendingExit>();
        if (tree.name != names.empty) {
            caught = List.nil();
            firstadr = nextadr;
        }
        classDef = tree;
        thrown = List.nil();
        lint = lint.augment(tree.sym.attributes_field);

        try {
            // define all the static fields
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() == JCTree.VARDEF) {
                    JCVariableDecl def = (JCVariableDecl)l.head;
                    if ((def.mods.flags & STATIC) != 0) {
                        VarSymbol sym = def.sym;
                        if (trackable(sym))
                            newVar(sym);
                    }
                }
            }

            // process all the static initializers
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() != JCTree.METHODDEF &&
                    (TreeInfo.flags(l.head) & STATIC) != 0) {
                    scanDef(l.head);
                    errorUncaught();
                }
            }

            // add intersection of all thrown clauses of initial constructors
            // to set of caught exceptions, unless class is anonymous.
            if (tree.name != names.empty) {
                boolean firstConstructor = true;
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (TreeInfo.isInitialConstructor(l.head)) {
                        List<Type> mthrown =
                            ((JCMethodDecl) l.head).sym.type.getThrownTypes();
                        if (firstConstructor) {
                            caught = mthrown;
                            firstConstructor = false;
                        } else {
                            caught = chk.intersect(mthrown, caught);
                        }
                    }
                }
            }

            // define all the instance fields
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() == JCTree.VARDEF) {
                    JCVariableDecl def = (JCVariableDecl)l.head;
                    if ((def.mods.flags & STATIC) == 0) {
                        VarSymbol sym = def.sym;
                        if (trackable(sym))
                            newVar(sym);
                    }
                }
            }

            // process all the instance initializers
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() != JCTree.METHODDEF &&
                    (TreeInfo.flags(l.head) & STATIC) == 0) {
                    scanDef(l.head);
                    errorUncaught();
                }
            }

            // in an anonymous class, add the set of thrown exceptions to
            // the throws clause of the synthetic constructor and propagate
            // outwards.
            if (tree.name == names.empty) {
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (TreeInfo.isInitialConstructor(l.head)) {
                        JCMethodDecl mdef = (JCMethodDecl)l.head;
                        mdef.thrown = make.Types(thrown);
                        mdef.sym.type.setThrown(thrown);
                    }
                }
                thrownPrev = chk.union(thrown, thrownPrev);
            }

            // process all the methods
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() == JCTree.METHODDEF) {
                    scan(l.head);
                    errorUncaught();
                }
            }

            thrown = thrownPrev;
        } finally {
            pendingExits = pendingExitsPrev;
            alive = alivePrev;
            nextadr = nextadrPrev;
            firstadr = firstadrPrev;
            caught = caughtPrev;
            classDef = classDefPrev;
            lint = lintPrev;
        }
    }

    public void visitMethodDef(JCMethodDecl tree) {
        if (tree.body == null) return;

        List<Type> caughtPrev = caught;
        List<Type> mthrown = tree.sym.type.getThrownTypes();
        Bits initsPrev = inits.dup();
        Bits uninitsPrev = uninits.dup();
        int nextadrPrev = nextadr;
        int firstadrPrev = firstadr;
        Lint lintPrev = lint;

        lint = lint.augment(tree.sym.attributes_field);

        assert pendingExits.isEmpty();

        try {
            boolean isInitialConstructor =
                TreeInfo.isInitialConstructor(tree);

            if (!isInitialConstructor)
                firstadr = nextadr;
            for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                JCVariableDecl def = l.head;
                scan(def);
                inits.incl(def.sym.adr);
                uninits.excl(def.sym.adr);
            }
            if (isInitialConstructor)
                caught = chk.union(caught, mthrown);
            else if ((tree.sym.flags() & (BLOCK | STATIC)) != BLOCK)
                caught = mthrown;
            // else we are in an instance initializer block;
            // leave caught unchanged.

            alive = true;
            scanStat(tree.body);

            if (alive && tree.sym.type.getReturnType().tag != VOID)
                log.error(TreeInfo.diagEndPos(tree.body), "missing.ret.stmt");

            if (isInitialConstructor) {
                for (int i = firstadr; i < nextadr; i++)
                    if (vars[i].owner == classDef.sym)
                        checkInit(TreeInfo.diagEndPos(tree.body), vars[i]);
            }
            List<PendingExit> exits = pendingExits.toList();
            pendingExits = new ListBuffer<PendingExit>();
            while (exits.nonEmpty()) {
                PendingExit exit = exits.head;
                exits = exits.tail;
                if (exit.thrown == null) {
                    assert exit.tree.getTag() == JCTree.RETURN;
                    if (isInitialConstructor) {
                        inits = exit.inits;
                        for (int i = firstadr; i < nextadr; i++)
                            checkInit(exit.tree.pos(), vars[i]);
                    }
                } else {
                    // uncaught throws will be reported later
                    pendingExits.append(exit);
                }
            }
        } finally {
            inits = initsPrev;
            uninits = uninitsPrev;
            nextadr = nextadrPrev;
            firstadr = firstadrPrev;
            caught = caughtPrev;
            lint = lintPrev;
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        boolean track = trackable(tree.sym);
        if (track && tree.sym.owner.kind == MTH) newVar(tree.sym);
        if (tree.init != null) {
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym.attributes_field);
            try{
                scanExpr(tree.init);
                if (track) letInit(tree.pos(), tree.sym);
            } finally {
                lint = lintPrev;
            }
        }
    }

    public void visitBlock(JCBlock tree) {
        int nextadrPrev = nextadr;
        scanStats(tree.stats);
        nextadr = nextadrPrev;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        pendingExits = new ListBuffer<PendingExit>();
        do {
            Bits uninitsEntry = uninits.dup();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            scanCond(tree.cond);
            if (log.nerrors != 0 ||
                loopPassTwo ||
                uninitsEntry.diffSet(uninitsWhenTrue).nextBit(firstadr)==-1)
                break;
            inits = initsWhenTrue;
            uninits = uninitsEntry.andSet(uninitsWhenTrue);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsWhenFalse;
        uninits = uninitsWhenFalse;
        alive = alive && !tree.cond.type.isTrue();
        alive |= resolveBreaks(tree, prevPendingExits);
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        Bits initsCond;
        Bits uninitsCond;
        pendingExits = new ListBuffer<PendingExit>();
        do {
            Bits uninitsEntry = uninits.dup();
            scanCond(tree.cond);
            initsCond = initsWhenFalse;
            uninitsCond = uninitsWhenFalse;
            inits = initsWhenTrue;
            uninits = uninitsWhenTrue;
            alive = !tree.cond.type.isFalse();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            if (log.nerrors != 0 ||
                loopPassTwo ||
                uninitsEntry.diffSet(uninits).nextBit(firstadr) == -1)
                break;
            uninits = uninitsEntry.andSet(uninits);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsCond;
        uninits = uninitsCond;
        alive = resolveBreaks(tree, prevPendingExits) ||
            !tree.cond.type.isTrue();
    }

    public void visitForLoop(JCForLoop tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        int nextadrPrev = nextadr;
        scanStats(tree.init);
        Bits initsCond;
        Bits uninitsCond;
        pendingExits = new ListBuffer<PendingExit>();
        do {
            Bits uninitsEntry = uninits.dup();
            if (tree.cond != null) {
                scanCond(tree.cond);
                initsCond = initsWhenFalse;
                uninitsCond = uninitsWhenFalse;
                inits = initsWhenTrue;
                uninits = uninitsWhenTrue;
                alive = !tree.cond.type.isFalse();
            } else {
                initsCond = inits.dup();
                initsCond.inclRange(firstadr, nextadr);
                uninitsCond = uninits.dup();
                uninitsCond.inclRange(firstadr, nextadr);
                alive = true;
            }
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            scan(tree.step);
            if (log.nerrors != 0 ||
                loopPassTwo ||
                uninitsEntry.dup().diffSet(uninits).nextBit(firstadr) == -1)
                break;
            uninits = uninitsEntry.andSet(uninits);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsCond;
        uninits = uninitsCond;
        alive = resolveBreaks(tree, prevPendingExits) ||
            tree.cond != null && !tree.cond.type.isTrue();
        nextadr = nextadrPrev;
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        visitVarDef(tree.var);

        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        int nextadrPrev = nextadr;
        scan(tree.expr);
        Bits initsStart = inits.dup();
        Bits uninitsStart = uninits.dup();

        letInit(tree.pos(), tree.var.sym);
        pendingExits = new ListBuffer<PendingExit>();
        do {
            Bits uninitsEntry = uninits.dup();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            if (log.nerrors != 0 ||
                loopPassTwo ||
                uninitsEntry.diffSet(uninits).nextBit(firstadr) == -1)
                break;
            uninits = uninitsEntry.andSet(uninits);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsStart;
        uninits = uninitsStart.andSet(uninits);
        resolveBreaks(tree, prevPendingExits);
        alive = true;
        nextadr = nextadrPrev;
    }

    public void visitLabelled(JCLabeledStatement tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        pendingExits = new ListBuffer<PendingExit>();
        scanStat(tree.body);
        alive |= resolveBreaks(tree, prevPendingExits);
    }

    public void visitSwitch(JCSwitch tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        pendingExits = new ListBuffer<PendingExit>();
        int nextadrPrev = nextadr;
        scanExpr(tree.selector);
        Bits initsSwitch = inits;
        Bits uninitsSwitch = uninits.dup();
        boolean hasDefault = false;
        for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
            alive = true;
            inits = initsSwitch.dup();
            uninits = uninits.andSet(uninitsSwitch);
            JCCase c = l.head;
            if (c.pat == null)
                hasDefault = true;
            else
                scanExpr(c.pat);
            scanStats(c.stats);
            addVars(c.stats, initsSwitch, uninitsSwitch);
            // Warn about fall-through if lint switch fallthrough enabled.
            if (!loopPassTwo &&
                alive &&
                lint.isEnabled(Lint.LintCategory.FALLTHROUGH) &&
                c.stats.nonEmpty() && l.tail.nonEmpty())
                log.warning(l.tail.head.pos(),
                            "possible.fall-through.into.case");
        }
        if (!hasDefault) {
            inits.andSet(initsSwitch);
            alive = true;
        }
        alive |= resolveBreaks(tree, prevPendingExits);
        nextadr = nextadrPrev;
    }
    // where
        /** Add any variables defined in stats to inits and uninits. */
        private static void addVars(List<JCStatement> stats, Bits inits,
                                    Bits uninits) {
            for (;stats.nonEmpty(); stats = stats.tail) {
                JCTree stat = stats.head;
                if (stat.getTag() == JCTree.VARDEF) {
                    int adr = ((JCVariableDecl) stat).sym.adr;
                    inits.excl(adr);
                    uninits.incl(adr);
                }
            }
        }

    public void visitTry(JCTry tree) {
        List<Type> caughtPrev = caught;
        List<Type> thrownPrev = thrown;
        thrown = List.nil();
        for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail)
            caught = chk.incl(l.head.param.type, caught);
        Bits uninitsTryPrev = uninitsTry;
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        pendingExits = new ListBuffer<PendingExit>();
        Bits initsTry = inits.dup();
        uninitsTry = uninits.dup();
        scanStat(tree.body);
        List<Type> thrownInTry = thrown;
        thrown = thrownPrev;
        caught = caughtPrev;
        boolean aliveEnd = alive;
        uninitsTry.andSet(uninits);
        Bits initsEnd = inits;
        Bits uninitsEnd = uninits;
        int nextadrCatch = nextadr;

        List<Type> caughtInTry = List.nil();
        for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
            alive = true;
            JCVariableDecl param = l.head.param;
            Type exc = param.type;
            if (chk.subset(exc, caughtInTry)) {
                log.error(l.head.pos(),
                          "except.already.caught", exc);
            } else if (!chk.isUnchecked(l.head.pos(), exc) &&
                       exc.tsym != syms.throwableType.tsym &&
                       exc.tsym != syms.exceptionType.tsym &&
                       !chk.intersects(exc, thrownInTry)) {
                log.error(l.head.pos(),
                          "except.never.thrown.in.try", exc);
            }
            caughtInTry = chk.incl(exc, caughtInTry);
            inits = initsTry.dup();
            uninits = uninitsTry.dup();
            scan(param);
            inits.incl(param.sym.adr);
            uninits.excl(param.sym.adr);
            scanStat(l.head.body);
            initsEnd.andSet(inits);
            uninitsEnd.andSet(uninits);
            nextadr = nextadrCatch;
            aliveEnd |= alive;
        }
        if (tree.finalizer != null) {
            List<Type> savedThrown = thrown;
            thrown = List.nil();
            inits = initsTry.dup();
            uninits = uninitsTry.dup();
            ListBuffer<PendingExit> exits = pendingExits;
            pendingExits = prevPendingExits;
            alive = true;
            scanStat(tree.finalizer);
            if (!alive) {
                // discard exits and exceptions from try and finally
                thrown = chk.union(thrown, thrownPrev);
                if (!loopPassTwo &&
                    lint.isEnabled(Lint.LintCategory.FINALLY)) {
                    log.warning(TreeInfo.diagEndPos(tree.finalizer),
                                "finally.cannot.complete");
                }
            } else {
                thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
                thrown = chk.union(thrown, savedThrown);
                uninits.andSet(uninitsEnd);
                // FIX: this doesn't preserve source order of exits in catch
                // versus finally!
                while (exits.nonEmpty()) {
                    PendingExit exit = exits.next();
                    if (exit.inits != null) {
                        exit.inits.orSet(inits);
                        exit.uninits.andSet(uninits);
                    }
                    pendingExits.append(exit);
                }
                inits.orSet(initsEnd);
                alive = aliveEnd;
            }
        } else {
            thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
            inits = initsEnd;
            uninits = uninitsEnd;
            alive = aliveEnd;
            ListBuffer<PendingExit> exits = pendingExits;
            pendingExits = prevPendingExits;
            while (exits.nonEmpty()) pendingExits.append(exits.next());
        }
        uninitsTry.andSet(uninitsTryPrev).andSet(uninits);
    }

    public void visitConditional(JCConditional tree) {
        scanCond(tree.cond);
        Bits initsBeforeElse = initsWhenFalse;
        Bits uninitsBeforeElse = uninitsWhenFalse;
        inits = initsWhenTrue;
        uninits = uninitsWhenTrue;
        if (tree.truepart.type.tag == BOOLEAN &&
            tree.falsepart.type.tag == BOOLEAN) {
            // if b and c are boolean valued, then
            // v is (un)assigned after a?b:c when true iff
            //    v is (un)assigned after b when true and
            //    v is (un)assigned after c when true
            scanCond(tree.truepart);
            Bits initsAfterThenWhenTrue = initsWhenTrue.dup();
            Bits initsAfterThenWhenFalse = initsWhenFalse.dup();
            Bits uninitsAfterThenWhenTrue = uninitsWhenTrue.dup();
            Bits uninitsAfterThenWhenFalse = uninitsWhenFalse.dup();
            inits = initsBeforeElse;
            uninits = uninitsBeforeElse;
            scanCond(tree.falsepart);
            initsWhenTrue.andSet(initsAfterThenWhenTrue);
            initsWhenFalse.andSet(initsAfterThenWhenFalse);
            uninitsWhenTrue.andSet(uninitsAfterThenWhenTrue);
            uninitsWhenFalse.andSet(uninitsAfterThenWhenFalse);
        } else {
            scanExpr(tree.truepart);
            Bits initsAfterThen = inits.dup();
            Bits uninitsAfterThen = uninits.dup();
            inits = initsBeforeElse;
            uninits = uninitsBeforeElse;
            scanExpr(tree.falsepart);
            inits.andSet(initsAfterThen);
            uninits.andSet(uninitsAfterThen);
        }
    }

    public void visitIf(JCIf tree) {
        scanCond(tree.cond);
        Bits initsBeforeElse = initsWhenFalse;
        Bits uninitsBeforeElse = uninitsWhenFalse;
        inits = initsWhenTrue;
        uninits = uninitsWhenTrue;
        scanStat(tree.thenpart);
        if (tree.elsepart != null) {
            boolean aliveAfterThen = alive;
            alive = true;
            Bits initsAfterThen = inits.dup();
            Bits uninitsAfterThen = uninits.dup();
            inits = initsBeforeElse;
            uninits = uninitsBeforeElse;
            scanStat(tree.elsepart);
            inits.andSet(initsAfterThen);
            uninits.andSet(uninitsAfterThen);
            alive = alive | aliveAfterThen;
        } else {
            inits.andSet(initsBeforeElse);
            uninits.andSet(uninitsBeforeElse);
            alive = true;
        }
    }



    public void visitBreak(JCBreak tree) {
        recordExit(tree);
    }

    public void visitContinue(JCContinue tree) {
        recordExit(tree);
    }

    public void visitReturn(JCReturn tree) {
        scanExpr(tree.expr);
        // if not initial constructor, should markDead instead of recordExit
        recordExit(tree);
    }

    public void visitThrow(JCThrow tree) {
        scanExpr(tree.expr);
        markThrown(tree, tree.expr.type);
        markDead();
    }

    public void visitApply(JCMethodInvocation tree) {
        scanExpr(tree.meth);
        scanExprs(tree.args);
        for (List<Type> l = tree.meth.type.getThrownTypes(); l.nonEmpty(); l = l.tail)
            markThrown(tree, l.head);
    }

    public void visitNewClass(JCNewClass tree) {
        scanExpr(tree.encl);
        scanExprs(tree.args);
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

    public void visitNewArray(JCNewArray tree) {
        scanExprs(tree.dims);
        scanExprs(tree.elems);
    }

    public void visitAssert(JCAssert tree) {
        Bits initsExit = inits.dup();
        Bits uninitsExit = uninits.dup();
        scanCond(tree.cond);
        uninitsExit.andSet(uninitsWhenTrue);
        if (tree.detail != null) {
            inits = initsWhenFalse;
            uninits = uninitsWhenFalse;
            scanExpr(tree.detail);
        }
        inits = initsExit;
        uninits = uninitsExit;
    }

    public void visitAssign(JCAssign tree) {
        JCTree lhs = TreeInfo.skipParens(tree.lhs);
        if (!(lhs instanceof JCIdent)) scanExpr(lhs);
        scanExpr(tree.rhs);
        letInit(lhs);
    }

    public void visitAssignop(JCAssignOp tree) {
        scanExpr(tree.lhs);
        scanExpr(tree.rhs);
        letInit(tree.lhs);
    }

    public void visitUnary(JCUnary tree) {
        switch (tree.getTag()) {
        case JCTree.NOT:
            scanCond(tree.arg);
            Bits t = initsWhenFalse;
            initsWhenFalse = initsWhenTrue;
            initsWhenTrue = t;
            t = uninitsWhenFalse;
            uninitsWhenFalse = uninitsWhenTrue;
            uninitsWhenTrue = t;
            break;
        case JCTree.PREINC: case JCTree.POSTINC:
        case JCTree.PREDEC: case JCTree.POSTDEC:
            scanExpr(tree.arg);
            letInit(tree.arg);
            break;
        default:
            scanExpr(tree.arg);
        }
    }

    public void visitBinary(JCBinary tree) {
        switch (tree.getTag()) {
        case JCTree.AND:
            scanCond(tree.lhs);
            Bits initsWhenFalseLeft = initsWhenFalse;
            Bits uninitsWhenFalseLeft = uninitsWhenFalse;
            inits = initsWhenTrue;
            uninits = uninitsWhenTrue;
            scanCond(tree.rhs);
            initsWhenFalse.andSet(initsWhenFalseLeft);
            uninitsWhenFalse.andSet(uninitsWhenFalseLeft);
            break;
        case JCTree.OR:
            scanCond(tree.lhs);
            Bits initsWhenTrueLeft = initsWhenTrue;
            Bits uninitsWhenTrueLeft = uninitsWhenTrue;
            inits = initsWhenFalse;
            uninits = uninitsWhenFalse;
            scanCond(tree.rhs);
            initsWhenTrue.andSet(initsWhenTrueLeft);
            uninitsWhenTrue.andSet(uninitsWhenTrueLeft);
            break;
        default:
            scanExpr(tree.lhs);
            scanExpr(tree.rhs);
        }
    }

    public void visitAnnotatedType(JCAnnotatedType tree) {
        // annotations don't get scanned
        tree.underlyingType.accept(this);
    }

    public void visitIdent(JCIdent tree) {
        if (tree.sym.kind == VAR)
            checkInit(tree.pos(), (VarSymbol)tree.sym);
    }

    public void visitTypeCast(JCTypeCast tree) {
        super.visitTypeCast(tree);
        if (!tree.type.isErroneous()
            && lint.isEnabled(Lint.LintCategory.CAST)
            && types.isSameType(tree.expr.type, tree.clazz.type)
            && !(ignoreAnnotatedCasts && containsTypeAnnotation(tree.clazz))) {
            log.warning(tree.pos(), "redundant.cast", tree.expr.type);
        }
    }

    public void visitTopLevel(JCCompilationUnit tree) {
        // Do nothing for TopLevel since each class is visited individually
    }

/**************************************************************************
 * utility methods for ignoring type-annotated casts lint checking
 *************************************************************************/
    private static final boolean ignoreAnnotatedCasts = true;
    private static class AnnotationFinder extends TreeScanner {
        public boolean foundTypeAnno = false;
        public void visitAnnotation(JCAnnotation tree) {
            foundTypeAnno = foundTypeAnno || (tree instanceof JCTypeAnnotation);
        }
    }

    private boolean containsTypeAnnotation(JCTree e) {
        AnnotationFinder finder = new AnnotationFinder();
        finder.scan(e);
        return finder.foundTypeAnno;
    }

/**************************************************************************
 * main method
 *************************************************************************/

    /** Perform definite assignment/unassignment analysis on a tree.
     */
    public void analyzeTree(JCTree tree, TreeMaker make) {
        try {
            this.make = make;
            inits = new Bits();
            uninits = new Bits();
            uninitsTry = new Bits();
            initsWhenTrue = initsWhenFalse =
                uninitsWhenTrue = uninitsWhenFalse = null;
            if (vars == null)
                vars = new VarSymbol[32];
            else
                for (int i=0; i<vars.length; i++)
                    vars[i] = null;
            firstadr = 0;
            nextadr = 0;
            pendingExits = new ListBuffer<PendingExit>();
            alive = true;
            this.thrown = this.caught = null;
            this.classDef = null;
            scan(tree);
        } finally {
            // note that recursive invocations of this method fail hard
            inits = uninits = uninitsTry = null;
            initsWhenTrue = initsWhenFalse =
                uninitsWhenTrue = uninitsWhenFalse = null;
            if (vars != null) for (int i=0; i<vars.length; i++)
                vars[i] = null;
            firstadr = 0;
            nextadr = 0;
            pendingExits = null;
            this.make = null;
            this.thrown = this.caught = null;
            this.classDef = null;
        }
    }
}
