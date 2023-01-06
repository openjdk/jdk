/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.resources.CompilerProperties.Warnings;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * Looks for possible 'this' escapes and generates corresponding warnings.
 *
 * <p>
 * A 'this' escape is when a constructor invokes a method that could be overridden in a
 * subclass, in which case the method will execute before the subclass constructor has
 * finished initializing the instance.
 *
 * <p>
 * This class attempts to identify possible 'this' escapes while also striking a balance
 * between false positives, false negatives, and code complexity. We do this by "executing"
 * the code in candidate constructors and tracking where the original 'this' reference goes.
 * If it ever passes to code outside of the current compilation unit, we declare a possible leak.
 * On the other hand, when constructors and non-overridable methods within the same compilation
 * unit are invoked, we "invoke" them to follow references.
 *
 * <p>
 * When tracking references, we distinguish between direct references and indirect references,
 * but do no further refinement. In particular, we do not attempt to track references stored
 * in fields at all. So we are mainly just trying to track what's on the Java stack.
 *
 * <p>
 * A few notes on this implementation:
 * <ul>
 *  <li>We "execute" constructors and track where the 'this' reference goes as the constructor executes.
 *  <li>We use a very simplified flow analysis that you might call a "flood analysis", where the union
 *      of every possible code branch is taken.
 *  <li>A "leak" is defined as the possible passing of a subclassed 'this' reference to code defined
 *      outside of the current compilation unit.
 *  <ul>
 *      <li>In other words, we don't try to protect the current compilation unit from itself.
 *      <li>For example, we ignore private constructors because they can never be directly invoked
 *          by external subclasses, etc. However, they can be indirectly invoked by other constructors.
 *  </ul>
 *  <li>If a constructor invokes a method defined in the same compilation unit, and that method cannot
 *      be overridden, then our analysis can safely "recurse" into the method.
 *  <ul>
 *      <li>When this occurs the warning displays each step in the stack trace to help in comprehension.
 *  </ul>
 *  <li>The possible locations for a 'this' reference that we try to track are:
 *  <ul>
 *      <li>Current 'this' instance
 *      <li>Current outer 'this' instance
 *      <li>Local parameter/variable
 *      <li>Method return value
 *      <li>Current expression value (i.e. top of stack)
 *  </ul>
 *  <li>We assume that native methods do not leak.
 *  <li>We don't try to track assignments to &amp; from fields (for future study).
 *  <li>We don't try to follow {@code super()} invocations.
 *  <li>We categorize tracked references as direct or indirect to add a tiny bit of nuance.
 *  </ul>
 */
class ThisEscapeAnalyzer extends TreeScanner {

    private final Names names;
    private final Types types;
    private final Log log;
    private       Lint lint;

// These fields are scoped to the entire COMPILATION UNIT

    /** Maps symbols of all methods to their corresponding declarations.
     */
    private final Map<Symbol, MethodInfo> methodMap = new LinkedHashMap<>();

    /** Contains symbols of fields and methods that have warnings suppressed.
     */
    private final Set<Symbol> suppressed = new HashSet<>();

    /** The declaring class of the constructor we're currently analyzing.
     *  This is the 'this' type we're trying to detect leaks of.
     */
    private JCClassDecl targetClass;

    /** Snapshots of {@link #callStack} where possible 'this' escapes occur.
     */
    private ArrayList<DiagnosticPosition[]> warningList = new ArrayList<>();

// These fields are scoped to the CONSTRUCTOR BEING ANALYZED

    /** The declaring class of the "invoked" method we're currently analyzing.
     *  This is either the analyzed constructor or some method it invokes.
     */
    private JCClassDecl methodClass;

    /** The current "call stack" during our analysis. The first entry is some method
     *  invoked from the target constructor; if empty, we're still in the constructor.
     */
    private final ArrayDeque<DiagnosticPosition> callStack = new ArrayDeque<>();

    /** Used to terminate recursion in {@link #invokeInvokable invokeInvokable()}.
     */
    private final Set<Pair<JCTree, RefSet<Ref>>> invocations = new HashSet<>();

    /** Snapshot of {@link #callStack} where a possible 'this' escape occurs.
     *  If non-null, a 'this' escape warning has been found in the current
     *  constructor statement, initialization block statement, or field initializer.
     */
    private DiagnosticPosition[] pendingWarning;

// These fields are scoped to the CONSTRUCTOR OR INVOKED METHOD BEING ANALYZED

    /** Current lexical scope depth in the constructor or method we're currently analyzing.
     *  Depth zero is the outermost scope. Depth -1 means we're not analyzing.
     */
    private int depth = -1;

    /** Possible 'this' references in the constructor or method we're currently analyzing.
     *  Null value means we're not analyzing.
     */
    private RefSet<Ref> refs;

// Constructor

    ThisEscapeAnalyzer(Names names, Types types, Log log, Lint lint) {
        this.names = names;
        this.types = types;
        this.log = log;
        this.lint = lint;
    }

//
// Main method
//

    public void analyzeTree(Env<AttrContext> env) {
        this.analyzeTree(env, env.tree);
    }

    public void analyzeTree(Env<AttrContext> env, JCTree tree) {

        // Sanity check
        Assert.check(this.checkInvariants(false, false));

        // Short circuit if warnings are totally disabled
        if (!this.lint.isEnabled(Lint.LintCategory.THIS_ESCAPE))
            return;

        // Build a mapping from symbols of methods to their declarations.
        // Classify all ctors and methods as analyzable and/or invokable.
        // Track which methods and variables have warnings are suppressed.
        new TreeScanner() {

            private Lint lint = ThisEscapeAnalyzer.this.lint;
            private JCClassDecl currentClass;
            private boolean privateOuter;

            @Override
            public void visitClassDef(JCClassDecl tree) {
                final JCClassDecl currentClassPrev = this.currentClass;
                final boolean privateOuterPrev = this.privateOuter;
                final Lint lintPrev = this.lint;
                this.lint = this.lint.augment(tree.sym);
                try {
                    this.currentClass = tree;
                    this.privateOuter |= tree.sym.isAnonymous();
                    this.privateOuter |= (tree.mods.flags & Flags.PRIVATE) != 0;

                    // Recurse
                    super.visitClassDef(tree);
                } finally {
                    this.currentClass = currentClassPrev;
                    this.privateOuter = privateOuterPrev;
                    this.lint = lintPrev;
                }
            }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                final Lint lintPrev = this.lint;
                this.lint = this.lint.augment(tree.sym);
                try {
                    // Track warning suppression
                    if (!this.lint.isEnabled(Lint.LintCategory.THIS_ESCAPE))
                        ThisEscapeAnalyzer.this.suppressed.add(tree.sym);

                    // Recurse
                    super.visitVarDef(tree);
                } finally {
                    this.lint = lintPrev;
                }
            }

            @Override
            public void visitMethodDef(JCMethodDecl tree) {
                final Lint lintPrev = this.lint;
                this.lint = this.lint.augment(tree.sym);
                try {
                    // Track warning suppression
                    if (!this.lint.isEnabled(Lint.LintCategory.THIS_ESCAPE))
                        ThisEscapeAnalyzer.this.suppressed.add(tree.sym);

                    // Determine if this is a constructor we should analyze
                    final boolean analyzable = this.currentClassIsExternallyExtendable() &&
                        TreeInfo.isConstructor(tree) &&
                        !tree.sym.isPrivate() &&
                        !ThisEscapeAnalyzer.this.suppressed.contains(tree.sym);

                    // Determine if this method is "invokable" in an analysis (can't be overridden)
                    final boolean invokable = !this.currentClassIsExternallyExtendable() ||
                        TreeInfo.isConstructor(tree) ||
                        (tree.mods.flags & (Flags.STATIC | Flags.PRIVATE | Flags.FINAL)) != 0;

                    // Add method or constructor to map
                    final MethodInfo info = new MethodInfo(this.currentClass, tree, analyzable, invokable);
                    ThisEscapeAnalyzer.this.methodMap.put(tree.sym, info);

                    // Recurse
                    super.visitMethodDef(tree);
                } finally {
                    this.lint = lintPrev;
                }
            }

            // Determines if the current class could be extended in some external compilation unit
            private boolean currentClassIsExternallyExtendable() {
                return !this.currentClass.sym.isFinal() &&
                  !(this.currentClass.sym.isSealed() && this.currentClass.permitting.isEmpty()) &&
                  !(this.currentClass.sym.owner.kind == MTH) &&
                  !this.privateOuter;
            }
        }.scan(tree);

        // TODO: eliminate sealed classes where all permitted subclasses are in this compilation unit

        // Now analyze all of the analyzable constructors we found
        for (Map.Entry<Symbol, MethodInfo> entry : this.methodMap.entrySet()) {

            // We are looking for analyzable constructors only
            final Symbol sym = entry.getKey();
            final MethodInfo methodInfo = entry.getValue();
            if (!methodInfo.isAnalyzable())
                continue;

            // Analyze constructor body
            this.targetClass = methodInfo.getDeclaringClass();
            this.methodClass = this.targetClass;
            Assert.check(this.depth == -1);
            Assert.check(this.refs == null);
            this.pushScope();
            try {

                // Add the initial 'this' reference
                this.refs = RefSet.newEmpty();
                this.refs.add(ThisRef.direct());

                // Scan constructor statements
                this.analyzeStatements(methodInfo.getDeclaration().body.stats);
            } finally {
                this.popScope();
                this.methodClass = null;
                this.targetClass = null;
                this.refs = null;
            }
        }

        // Eliminate duplicate warnings. Warning B duplicates warning A if the stack trace of A is a prefix
        // of the stack trace of B. For example, if constructor Foo(int x) has a leak, and constructor
        // Foo() invokes this(0), then emitting a warning for Foo() would be redundant.
        final BiPredicate<DiagnosticPosition[], DiagnosticPosition[]> extendsAsPrefix = (warning1, warning2) -> {
            if (warning2.length < warning1.length)
                return false;
            for (int index = 0; index < warning1.length; index++) {
                if (warning2[index].getPreferredPosition() != warning1[index].getPreferredPosition())
                    return false;
            }
            return true;
        };

        // Stack traces are ordered top to bottom, and so duplicates always have the same first element(s).
        // Sort the stack traces lexicographically, so that duplicates immediately follow what they duplicate.
        final Comparator<DiagnosticPosition[]> ordering = (warning1, warning2) -> {
            for (int index1 = 0, index2 = 0; true; index1++, index2++) {
                final boolean end1 = index1 >= warning1.length;
                final boolean end2 = index2 >= warning2.length;
                if (end1 && end2)
                    return 0;
                if (end1)
                    return -1;
                if (end2)
                    return 1;
                final int posn1 = warning1[index1].getPreferredPosition();
                final int posn2 = warning2[index2].getPreferredPosition();
                final int diff = Integer.compare(posn1, posn2);
                if (diff != 0)
                    return diff;
            }
        };
        this.warningList.sort(ordering);

        // Now emit the warnings, but skipping over duplicates as we go through the list
        DiagnosticPosition[] previous = null;
        for (DiagnosticPosition[] warning : this.warningList) {

            // Skip duplicates
            if (previous != null && extendsAsPrefix.test(previous, warning))
                continue;
            previous = warning;

            // Emit warnings showing the entire stack trace
            JCDiagnostic.Warning key = Warnings.PossibleThisEscape;
            int remain = warning.length;
            do {
                final DiagnosticPosition pos = warning[--remain];
                this.log.warning(Lint.LintCategory.THIS_ESCAPE, pos, key);
                key = Warnings.PossibleThisEscapeLocation;
            } while (remain > 0);
        }
        this.warningList.clear();
    }

    private void analyzeStatements(List<JCStatement> stats) {
        for (JCStatement stat : stats) {

            // Analyze statement
            this.scan(stat);

            // Capture any pending warning generated
            if (this.copyPendingWarning())
                break;                      // report at most one warning per constructor
        }
    }

    @Override
    public void scan(JCTree tree) {

        // Check node
        if (tree == null || tree.type == Type.stuckType)
            return;

        // Sanity check
        Assert.check(this.checkInvariants(true, false));

        // Can this expression node possibly leave a 'this' reference on the stack?
        final boolean referenceExpressionNode;
        switch (tree.getTag()) {
        case CASE:
        case SWITCH_EXPRESSION:
        case CONDEXPR:
        case YIELD:
        case APPLY:
        case NEWCLASS:
        case NEWARRAY:
        case LAMBDA:
        case PARENS:
        case ASSIGN:
        case TYPECAST:
        case INDEXED:
        case SELECT:
        case REFERENCE:
        case IDENT:
        case NULLCHK:
        case LETEXPR:
            referenceExpressionNode = true;
            break;
        default:
            referenceExpressionNode = false;
            break;
        }

        // Scan node
        super.scan(tree);

        // Sanity check
        Assert.check(this.checkInvariants(true, referenceExpressionNode));

        // Discard any direct 'this' reference that's incompatible with the target type
        if (referenceExpressionNode) {

            // We treat instance methods as having a "value" equal to their instance
            Type type = tree.type;
            final Symbol sym = TreeInfo.symbolFor(tree);
            if (sym != null &&
                sym.kind == MTH &&
                (sym.flags() & Flags.STATIC) == 0) {
                type = sym.owner.type;
            }

            // If the expression type is incompatible with 'this', discard it
            if (type != null && !this.isSubtype(this.targetClass.sym.type, type))
                this.refs.remove(ExprRef.direct(this.depth));
        }
    }

//
// Visitor methods - Class Declarations
//

    @Override
    public void visitClassDef(JCClassDecl tree) {
        return;     // we're busy analyzing another class - skip
    }

//
// Visitor methods - Variable Declarations
//

    @Override
    public void visitVarDef(JCVariableDecl tree) {

        // Skip if ignoring warnings for this variable
        if (this.suppressed.contains(tree.sym))
            return;

        // Scan initializer, if any
        this.scan(tree.init);
        if (this.isParamOrVar(tree.sym))
            this.refs.replaceExprs(this.depth, direct -> new VarRef(tree.sym, direct));
        else
            this.refs.discardExprs(this.depth);         // we don't track fields yet
    }

//
// Visitor methods - Methods
//

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        Assert.check(false);        // we should never get here
    }

    @Override
    public void visitApply(JCMethodInvocation invoke) {

        // Get method symbol
        final MethodSymbol sym = (MethodSymbol)TreeInfo.symbolFor(invoke.meth);

        // Recurse on method expression
        this.scan(invoke.meth);
        final boolean direct = this.refs.remove(ExprRef.direct(this.depth));
        final boolean indirect = this.refs.remove(ExprRef.indirect(this.depth));

        // Determine if method receiver represents a possible reference
        final RefSet<ThisRef> receiverRefs = RefSet.newEmpty();
        if (sym != null && !sym.isStatic()) {
            if (direct)
                receiverRefs.add(ThisRef.direct());
            if (indirect)
                receiverRefs.add(ThisRef.indirect());
        }

        // If "super()": ignore - we don't try to track into superclasses.
        // However, we do need to "invoke" non-static initializers/blocks.
        final Name name = TreeInfo.name(invoke.meth);
        if (name == this.names._super) {
            this.scanInitializers();
            return;
        }

        // "Invoke" the method
        this.invoke(invoke, sym, invoke.args, receiverRefs);
    }

    private void invoke(JCTree site, MethodSymbol sym, List<JCExpression> args, RefSet<?> receiverRefs) {

        // Skip if ignoring warnings for the invoked method
        if (this.suppressed.contains(sym))
            return;

        // Ignore final methods in java.lang.Object (getClass(), notify(), etc.)
        if (sym != null &&
            sym.owner.kind == TYP &&
            ((ClassSymbol)sym.owner).fullname == this.names.java_lang_Object &&
            sym.isFinal()) {
            return;
        }

        // Analyze method if possible, otherwise assume nothing
        final MethodInfo methodInfo = this.methodMap.get(sym);
        if (methodInfo != null && methodInfo.isInvokable())
            this.invokeInvokable(site, args, receiverRefs, methodInfo);
        else
            this.invokeUnknown(site, args, receiverRefs);
    }

    // Scan field initializers and initialization blocks
    private void scanInitializers() {
        final DiagnosticPosition[] pendingWarningPrev = this.pendingWarning;
        this.pendingWarning = null;
        try {
            for (List<JCTree> defs = this.methodClass.defs; defs.nonEmpty(); defs = defs.tail) {

                // Ignore static stuff
                if ((TreeInfo.flags(defs.head) & Flags.STATIC) != 0)
                    continue;

                // Handle field initializers
                if (defs.head.hasTag(VARDEF)) {
                    this.scan((JCVariableDecl)defs.head);
                    this.copyPendingWarning();
                    continue;
                }

                // Handle initialization block
                if (defs.head.hasTag(BLOCK)) {
                    this.visitScoped((JCBlock)defs.head, false, block -> this.analyzeStatements(block.stats));
                    continue;
                }
            }
        } finally {
            this.pendingWarning = pendingWarningPrev;
        }
    }

    // Handle the invocation of a local analyzable method or constructor
    private void invokeInvokable(JCTree site, List<JCExpression> args,
        RefSet<?> receiverRefs, MethodInfo methodInfo) {
        Assert.check(methodInfo.isInvokable());

        // Collect 'this' references found in method parameters
        final JCMethodDecl method = methodInfo.getDeclaration();
        final RefSet<VarRef> paramRefs = RefSet.newEmpty();
        List<JCVariableDecl> params = method.params;
        while (args.nonEmpty() && params.nonEmpty()) {
            final VarSymbol sym = params.head.sym;
            this.scan(args.head);
            this.refs.removeExprs(this.depth, direct -> paramRefs.add(new VarRef(sym, direct)));
            args = args.tail;
            params = params.tail;
        }

        // "Invoke" the method
        final JCClassDecl methodClassPrev = this.methodClass;
        this.methodClass = methodInfo.getDeclaringClass();
        final RefSet<Ref> refsPrev = this.refs;
        this.refs = RefSet.newEmpty();
        final int depthPrev = this.depth;
        this.depth = 0;
        this.callStack.push(site);
        try {

            // Add initial references from method receiver
            this.refs.addAll(receiverRefs);

            // Add initial references from parameters
            this.refs.addAll(paramRefs);

            // Stop trivial cases here
            if (this.refs.isEmpty())
                return;

            // Stop infinite recursion here
            final Pair<JCTree, RefSet<Ref>> invocation = Pair.of(site, this.refs.clone());
            if (!this.invocations.add(invocation))
                return;

            // Scan method body to "execute" it
            try {
                this.scan(method.body);
            } finally {
                this.invocations.remove(invocation);
            }

            // "Return" any references from method return value
            if (this.refs.remove(ReturnRef.direct()))
                refsPrev.add(ExprRef.direct(depthPrev));
            if (this.refs.remove(ReturnRef.indirect()))
                refsPrev.add(ExprRef.indirect(depthPrev));
        } finally {
            this.callStack.pop();
            this.depth = depthPrev;
            this.refs = refsPrev;
            this.methodClass = methodClassPrev;
        }
    }

    // Handle invocation of an unknown or overridable method or constructor
    private void invokeUnknown(JCTree invoke, List<JCExpression> args, RefSet<?> receiverRefs) {

        // Detect leak via receiver
        if (!receiverRefs.isEmpty())
            this.leakAt(invoke);

        // Detect leaks via method parameters
        for (JCExpression arg : args) {
            this.scan(arg);
            if (this.refs.discardExprs(this.depth))
                this.leakAt(arg);
        }
    }

//
// Visitor methods - new Foo()
//

    @Override
    public void visitNewClass(JCNewClass tree) {
        final MethodInfo methodInfo = this.methodMap.get(tree.constructor);
        if (methodInfo != null && methodInfo.isInvokable())
            this.invokeInvokable(tree, tree.args, this.outerThisRefs(tree.encl, tree.clazz.type), methodInfo);
        else
            this.invokeUnknown(tree, tree.args, this.outerThisRefs(tree.encl, tree.clazz.type));
    }

    // Determine 'this' references passed to a constructor via the outer 'this' instance
    private RefSet<OuterRef> outerThisRefs(JCExpression explicitOuterThis, Type type) {
        final RefSet<OuterRef> outerRefs = RefSet.newEmpty();
        if (explicitOuterThis != null) {
            this.scan(explicitOuterThis);
            this.refs.removeExprs(this.depth, direct -> outerRefs.add(new OuterRef(direct)));
        } else if (this.types.hasOuterClass(type, this.methodClass.type)) {
            if (this.refs.contains(ThisRef.direct()))
                outerRefs.add(OuterRef.direct());
            if (this.refs.contains(ThisRef.indirect()))
                outerRefs.add(OuterRef.indirect());
        }
        return outerRefs;
    }

//
// Visitor methods - Codey Bits
//

    @Override
    public void visitBlock(JCBlock tree) {
        this.visitScoped(tree, false, super::visitBlock);
        Assert.check(this.checkInvariants(true, false));
    }

    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        this.visitLooped(tree, super::visitDoLoop);
    }

    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        this.visitLooped(tree, super::visitWhileLoop);
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        this.visitLooped(tree, super::visitForLoop);
    }

    @Override
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        this.visitLooped(tree, super::visitForeachLoop);
    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        this.visitScoped(tree, false, t -> {
            this.scan(t.selector);
            this.refs.discardExprs(this.depth);
            this.scan(t.cases);
        });
    }

    @Override
    public void visitSwitchExpression(JCSwitchExpression tree) {
        this.visitScoped(tree, true, t -> {
            this.scan(t.selector);
            this.refs.discardExprs(this.depth);
            final RefSet<ExprRef> combinedRefs = new RefSet<>();
            for (List<JCCase> cases = t.cases; cases.nonEmpty(); cases = cases.tail) {
                this.scan(cases.head);
                combinedRefs.addAll(this.refs.removeExprs(this.depth));
            }
            this.refs.addAll(combinedRefs);
        });
    }

    @Override
    public void visitCase(JCCase tree) {
        this.scan(tree.stats);          // no need to scan labels
    }

    @Override
    public void visitLetExpr(LetExpr tree) {
        this.visitScoped(tree, true, super::visitLetExpr);
    }

    @Override
    public void visitReturn(JCReturn tree) {
        this.scan(tree.expr);
        this.refs.replaceExprs(this.depth, ReturnRef::new);
    }

    @Override
    public void visitLambda(JCLambda lambda) {
        this.visitDeferred(() -> this.visitScoped(lambda, false, super::visitLambda));
    }

    @Override
    public void visitAssign(JCAssign tree) {
        this.scan(tree.lhs);
        this.refs.discardExprs(this.depth);
        this.scan(tree.rhs);
        final VarSymbol sym = (VarSymbol)TreeInfo.symbolFor(tree.lhs);
        if (this.isParamOrVar(sym))
            this.refs.replaceExprs(this.depth, direct -> new VarRef(sym, direct));
        else
            this.refs.discardExprs(this.depth);         // we don't track fields yet
    }

    @Override
    public void visitIndexed(JCArrayAccess tree) {
        this.scan(tree.indexed);
        this.refs.remove(ExprRef.direct(this.depth));
        final boolean indirectRef = this.refs.remove(ExprRef.indirect(this.depth));
        this.scan(tree.index);
        this.refs.discardExprs(this.depth);
        if (indirectRef) {
            this.refs.add(ExprRef.direct(this.depth));
            this.refs.add(ExprRef.indirect(this.depth));
        }
    }

    @Override
    public void visitSelect(JCFieldAccess tree) {

        // Scan the selected thing
        this.scan(tree.selected);
        final boolean selectedDirectRef = this.refs.remove(ExprRef.direct(this.depth));
        final boolean selectedIndirectRef = this.refs.remove(ExprRef.indirect(this.depth));

        // Explicit 'this' reference?
        final Type.ClassType currentClassType = (Type.ClassType)this.methodClass.sym.type;
        if (TreeInfo.isExplicitThisReference(this.types, currentClassType, tree)) {
            if (this.refs.contains(ThisRef.direct()))
                this.refs.add(ExprRef.direct(this.depth));
            if (this.refs.contains(ThisRef.indirect()))
                this.refs.add(ExprRef.indirect(this.depth));
            return;
        }

        // Explicit outer 'this' reference?
        final Type selectedType = this.types.erasure(tree.selected.type);
        if (selectedType.hasTag(CLASS)) {
            final Type.ClassType selectedClassType = (Type.ClassType)selectedType;
            if (tree.name == this.names._this &&
                this.types.hasOuterClass(currentClassType, selectedClassType)) {
                if (this.refs.contains(OuterRef.direct()))
                    this.refs.add(ExprRef.direct(this.depth));
                if (this.refs.contains(OuterRef.indirect()))
                    this.refs.add(ExprRef.indirect(this.depth));
                return;
            }
        }

        // Methods - the "value" of a non-static method is a reference to its instance
        final Symbol sym = tree.sym;
        if (sym.kind == MTH) {
            if ((sym.flags() & Flags.STATIC) == 0) {
                if (selectedDirectRef)
                    this.refs.add(ExprRef.direct(this.depth));
                if (selectedIndirectRef)
                    this.refs.add(ExprRef.indirect(this.depth));
            }
            return;
        }

        // Unknown
        return;
    }

    @Override
    public void visitReference(JCMemberReference tree) {

        // Scan target expression and extract 'this' references, if any
        this.scan(tree.expr);
        final boolean direct = this.refs.remove(ExprRef.direct(this.depth));
        final boolean indirect = this.refs.remove(ExprRef.indirect(this.depth));

        // Gather receiver references for deferred invocation
        final RefSet<Ref> receiverRefs = RefSet.newEmpty();
        switch (tree.kind) {
        case UNBOUND:
        case STATIC:
        case TOPLEVEL:
        case ARRAY_CTOR:
            return;
        case SUPER:
            if (this.refs.contains(ThisRef.direct()))
                receiverRefs.add(ThisRef.direct());
            if (this.refs.contains(ThisRef.indirect()))
                receiverRefs.add(ThisRef.indirect());
            break;
        case BOUND:
            if (direct)
                receiverRefs.add(ThisRef.direct());
            if (indirect)
                receiverRefs.add(ThisRef.indirect());
            break;
        case IMPLICIT_INNER:
            receiverRefs.addAll(this.outerThisRefs(null, tree.expr.type));
            break;
        default:
            throw new RuntimeException("non-exhaustive?");
        }

        // Treat method reference just like the equivalent lambda
        this.visitDeferred(() -> this.invoke(tree, (MethodSymbol)tree.sym, List.nil(), receiverRefs));
    }

    @Override
    public void visitIdent(JCIdent tree) {

        // Reference to this?
        if (tree.name == names._this || tree.name == names._super) {
            if (this.refs.contains(ThisRef.direct()))
                this.refs.add(ExprRef.direct(this.depth));
            if (this.refs.contains(ThisRef.indirect()))
                this.refs.add(ExprRef.indirect(this.depth));
            return;
        }

        // Parameter or local variable?
        if (this.isParamOrVar(tree.sym)) {
            final VarSymbol sym = (VarSymbol)tree.sym;
            if (this.refs.contains(VarRef.direct(sym)))
                this.refs.add(ExprRef.direct(this.depth));
            if (this.refs.contains(VarRef.indirect(sym)))
                this.refs.add(ExprRef.indirect(this.depth));
            return;
        }

        // An unqualified, non-static method invocation must reference 'this' or outer 'this'.
        // The "value" of a non-static method is a reference to its instance.
        if (tree.sym.kind == MTH && (tree.sym.flags() & Flags.STATIC) == 0) {
            final MethodSymbol sym = (MethodSymbol)tree.sym;

            // Check for implicit 'this' reference
            final Type.ClassType currentClassType = (Type.ClassType)this.methodClass.sym.type;
            final Type methodOwnerType = sym.owner.type;
            if (this.isSubtype(currentClassType, methodOwnerType)) {
                if (this.refs.contains(ThisRef.direct()))
                    this.refs.add(ExprRef.direct(this.depth));
                if (this.refs.contains(ThisRef.indirect()))
                    this.refs.add(ExprRef.indirect(this.depth));
                return;
            }

            // Check for implicit outer 'this' reference
            if (this.types.hasOuterClass(currentClassType, methodOwnerType)) {
                if (this.refs.contains(OuterRef.direct()))
                    this.refs.add(ExprRef.direct(this.depth));
                if (this.refs.contains(OuterRef.indirect()))
                    this.refs.add(ExprRef.indirect(this.depth));
                return;
            }

            // What could it be?
            //Assert.check(false);
            return;
        }

        // Unknown
        return;
    }

    @Override
    public void visitSynchronized(JCSynchronized tree) {
        this.scan(tree.lock);
        this.refs.discardExprs(this.depth);
        this.scan(tree.body);
    }

    @Override
    public void visitConditional(JCConditional tree) {
        this.scan(tree.cond);
        this.refs.discardExprs(this.depth);
        final RefSet<ExprRef> combinedRefs = new RefSet<>();
        this.scan(tree.truepart);
        combinedRefs.addAll(this.refs.removeExprs(this.depth));
        this.scan(tree.falsepart);
        combinedRefs.addAll(this.refs.removeExprs(this.depth));
        this.refs.addAll(combinedRefs);
    }

    @Override
    public void visitIf(JCIf tree) {
        this.scan(tree.cond);
        this.refs.discardExprs(this.depth);
        this.scan(tree.thenpart);
        this.scan(tree.elsepart);
    }

    @Override
    public void visitExec(JCExpressionStatement tree) {
        this.scan(tree.expr);
        this.refs.discardExprs(this.depth);
    }

    @Override
    public void visitThrow(JCThrow tree) {
        this.scan(tree.expr);
        this.refs.discardExprs(this.depth);      // we don't try to follow refs from thrown exceptions
    }

    @Override
    public void visitAssert(JCAssert tree) {
        this.scan(tree.cond);
        this.refs.discardExprs(this.depth);
    }

    @Override
    public void visitNewArray(JCNewArray tree) {
        boolean ref = false;
        if (tree.elems != null) {
            for (List<JCExpression> elems = tree.elems; elems.nonEmpty(); elems = elems.tail) {
                this.scan(elems.head);
                ref |= this.refs.discardExprs(this.depth);
            }
        }
        if (ref)
            this.refs.add(ExprRef.indirect(this.depth));
    }

    @Override
    public void visitTypeCast(JCTypeCast tree) {
        this.scan(tree.expr);
    }

    @Override
    public void visitConstantCaseLabel(JCConstantCaseLabel tree) {
    }

    @Override
    public void visitPatternCaseLabel(JCPatternCaseLabel tree) {
    }

    @Override
    public void visitParenthesizedPattern(JCParenthesizedPattern tree) {
    }

    @Override
    public void visitRecordPattern(JCRecordPattern that) {
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        this.scan(tree.expr);
        this.refs.discardExprs(this.depth);
    }

    @Override
    public void visitTypeArray(JCArrayTypeTree tree) {
    }

    @Override
    public void visitTypeApply(JCTypeApply tree) {
    }

    @Override
    public void visitTypeUnion(JCTypeUnion tree) {
    }

    @Override
    public void visitTypeIntersection(JCTypeIntersection tree) {
    }

    @Override
    public void visitTypeParameter(JCTypeParameter tree) {
    }

    @Override
    public void visitWildcard(JCWildcard tree) {
    }

    @Override
    public void visitTypeBoundKind(TypeBoundKind that) {
    }

    @Override
    public void visitModifiers(JCModifiers tree) {
    }

    @Override
    public void visitAnnotation(JCAnnotation tree) {
    }

    @Override
    public void visitAnnotatedType(JCAnnotatedType tree) {
    }

//
// Visitor methods - Non-Reference Stuff
//

    @Override
    public void visitAssignop(JCAssignOp tree) {
        this.scan(tree.lhs);
        this.refs.discardExprs(this.depth);
        this.scan(tree.rhs);
        this.refs.discardExprs(this.depth);
    }

    @Override
    public void visitUnary(JCUnary tree) {
        this.scan(tree.arg);
        this.refs.discardExprs(this.depth);
    }

    @Override
    public void visitBinary(JCBinary tree) {
        this.scan(tree.lhs);
        this.refs.discardExprs(this.depth);
        this.scan(tree.rhs);
        this.refs.discardExprs(this.depth);
    }

// Helper methods

    // Recurse through indirect code that might get executed later, e.g., a lambda.
    // We stash any pending warning and the current RefSet, then recurse into the deferred
    // code (still using the current RefSet) to see if it would leak. Then we restore the
    // pending warning and the current RefSet. Finally, if the deferred code would have
    // leaked, we create an indirect ExprRef because it must be holding a 'this' reference.
    // If the deferred code would not leak, then obviously no leak is possible, period.
    private <T extends JCTree> void visitDeferred(Runnable recurse) {
        final DiagnosticPosition[] pendingWarningPrev = this.pendingWarning;
        this.pendingWarning = null;
        final RefSet<Ref> refsPrev = this.refs.clone();
        final boolean deferredCodeLeaks;
        try {
            recurse.run();
            deferredCodeLeaks = this.pendingWarning != null;
        } finally {
            this.refs = refsPrev;
            this.pendingWarning = pendingWarningPrev;
        }
        if (deferredCodeLeaks)
            this.refs.add(ExprRef.indirect(this.depth));
    }

    // Repeat loop as needed until the current set of references converges
    private <T extends JCTree> void visitLooped(T tree, Consumer<T> visitor) {
        this.visitScoped(tree, false, t -> {
            while (true) {
                final RefSet<Ref> prevRefs = this.refs.clone();
                visitor.accept(t);
                if (this.refs.equals(prevRefs))
                    break;
            }
        });
    }

    // Handle the tree node within a new scope
    private <T extends JCTree> void visitScoped(T tree, boolean promote, Consumer<T> handler) {
        this.pushScope();
        try {

            // Invoke handler
            Assert.check(this.checkInvariants(true, false));
            handler.accept(tree);
            Assert.check(this.checkInvariants(true, promote));

            // "Promote" any remaining ExprRef's to the enclosing lexical scope
            if (promote) {
                this.refs.removeExprs(this.depth,
                    direct -> this.refs.add(new ExprRef(this.depth - 1, direct)));
            }
        } finally {
            this.popScope();
        }
    }

    private void pushScope() {
        this.depth++;
    }

    private void popScope() {
        Assert.check(this.depth >= 0);
        this.depth--;
        this.refs.removeIf(ref -> ref.getDepth() > this.depth);
    }

    // Note a possible 'this' reference leak at the specified location
    private void leakAt(JCTree tree) {

        // Generate at most one warning per statement
        if (this.pendingWarning != null)
            return;

        // Snapshot the current stack trace
        this.callStack.push(tree.pos());
        this.pendingWarning = this.callStack.toArray(new DiagnosticPosition[0]);
        this.callStack.pop();
    }

    // Copy pending warning, if any, to the warning list and reset
    private boolean copyPendingWarning() {
        if (this.pendingWarning == null)
            return false;
        this.warningList.add(this.pendingWarning);
        this.pendingWarning = null;
        return true;
    }

    // Does the symbol correspond to a parameter or local variable (not a field)?
    private boolean isParamOrVar(Symbol sym) {
        return sym != null &&
            sym.kind == VAR &&
            (sym.owner.kind == MTH || sym.owner.kind == VAR);
    }

    // Is type A a subtype of B when both types are erased?
    private boolean isSubtype(Type a, Type b) {
        return this.types.isSubtypeUnchecked(this.types.erasure(a), this.types.erasure(b));
    }

    // When scanning nodes we can be in one of two modes:
    //  (a) Looking for constructors - we do not recurse into any code blocks
    //  (b) Analyzing a constructor - we are tracing its possible execution paths
    private boolean isAnalyzing() {
        return this.targetClass != null;
    }

// Debugging

    // Invariant checks
    private boolean checkInvariants(boolean analyzing, boolean allowExpr) {
        Assert.check(analyzing == this.isAnalyzing());
        if (this.isAnalyzing()) {
            Assert.check(this.methodClass != null);
            Assert.check(this.targetClass != null);
            Assert.check(this.refs != null);
            Assert.check(this.depth >= 0);
            Assert.check(this.refs.stream().noneMatch(ref -> ref.getDepth() > this.depth));
            Assert.check(allowExpr || !this.refs.contains(ExprRef.direct(this.depth)));
            Assert.check(allowExpr || !this.refs.contains(ExprRef.indirect(this.depth)));
        } else {
            Assert.check(this.targetClass == null);
            Assert.check(this.refs == null);
            Assert.check(this.depth == -1);
            Assert.check(this.callStack.isEmpty());
            Assert.check(this.pendingWarning == null);
            Assert.check(this.invocations.isEmpty());
        }
        return true;
    }

// Ref's

    /** Represents a location that could possibly hold a 'this' reference.
     *
     *  <p>
     *  If not "direct", the reference is found through at least one indirection.
     */
    private abstract static class Ref {

        private final int depth;
        private final boolean direct;

        Ref(int depth, boolean direct) {
            this.depth = depth;
            this.direct = direct;
        }

        public int getDepth() {
            return this.depth;
        }

        public boolean isDirect() {
            return this.direct;
        }

        @Override
        public int hashCode() {
            return this.getClass().hashCode()
                ^ Integer.hashCode(this.depth)
                ^ Boolean.hashCode(this.direct);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final Ref that = (Ref)obj;
            return this.depth == that.depth
              && this.direct == that.direct;
        }

        @Override
        public String toString() {
            final ArrayList<String> properties = new ArrayList<>();
            this.addProperties(properties);
            return this.getClass().getSimpleName()
              + "[" + properties.stream().collect(Collectors.joining(",")) + "]";
        }

        protected void addProperties(ArrayList<String> properties) {
            properties.add("depth=" + this.depth);
            properties.add(this.direct ? "direct" : "indirect");
        }
    }

    /** A reference from the current 'this' instance.
     */
    private static class ThisRef extends Ref {

        ThisRef(boolean direct) {
            super(0, direct);
        }

        public static ThisRef direct() {
            return new ThisRef(true);
        }

        public static ThisRef indirect() {
            return new ThisRef(false);
        }
    }

    /** A reference from the current outer 'this' instance.
     */
    private static class OuterRef extends Ref {

        OuterRef(boolean direct) {
            super(0, direct);
        }

        public static OuterRef direct() {
            return new OuterRef(true);
        }

        public static OuterRef indirect() {
            return new OuterRef(false);
        }
    }

    /** A reference from the expression that was just evaluated.
     *  In other words, a reference that's sitting on top of the stack.
     */
    private static class ExprRef extends Ref {

        ExprRef(int depth, boolean direct) {
            super(depth, direct);
        }

        public static ExprRef direct(int depth) {
            return new ExprRef(depth, true);
        }

        public static ExprRef indirect(int depth) {
            return new ExprRef(depth, false);
        }
    }

    /** A reference from the return value of the current method being "invoked".
     */
    private static class ReturnRef extends Ref {

        ReturnRef(boolean direct) {
            super(0, direct);
        }

        public static ReturnRef direct() {
            return new ReturnRef(true);
        }

        public static ReturnRef indirect() {
            return new ReturnRef(false);
        }
    }

    /** A reference from a variable.
     */
    private static class VarRef extends Ref {

        private final VarSymbol sym;

        VarRef(VarSymbol sym, boolean direct) {
            super(0, direct);
            this.sym = sym;
        }

        public VarSymbol getSymbol() {
            return this.sym;
        }

        public static VarRef direct(VarSymbol sym) {
            return new VarRef(sym, true);
        }

        public static VarRef indirect(VarSymbol sym) {
            return new VarRef(sym, false);
        }

        @Override
        public int hashCode() {
            return super.hashCode()
                ^ Objects.hashCode(this.sym);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final VarRef that = (VarRef)obj;
            return Objects.equals(this.sym, that.sym);
        }

        @Override
        protected void addProperties(ArrayList<String> properties) {
            super.addProperties(properties);
            properties.add("sym=" + this.sym);
        }
    }

// RefSet

    /** Contains locations currently known to hold a possible 'this' reference.
     */
    @SuppressWarnings("serial")
    private static class RefSet<T extends Ref> extends HashSet<T> {

        public static <T extends Ref> RefSet<T> newEmpty() {
            return new RefSet<>();
        }

        /**
         * Discard any {@link ExprRef}'s at the specified depth.
         * Do this when discarding whatever is on top of the stack.
         */
        public boolean discardExprs(int depth) {
            return this.remove(ExprRef.direct(depth)) | this.remove(ExprRef.indirect(depth));
        }

        /**
         * Extract any {@link ExprRef}'s at the specified depth.
         */
        public RefSet<ExprRef> removeExprs(int depth) {
            return Stream.of(ExprRef.direct(depth), ExprRef.indirect(depth))
              .filter(this::remove)
              .collect(Collectors.toCollection(RefSet::new));
        }

        /**
         * Extract any {@link ExprRef}'s at the specified depth and do something with them.
         */
        public void removeExprs(int depth, Consumer<? super Boolean> handler) {
            Stream.of(ExprRef.direct(depth), ExprRef.indirect(depth))
              .filter(this::remove)
              .map(ExprRef::isDirect)
              .forEach(handler);
        }

        /**
         * Replace any {@link ExprRef}'s at the specified depth.
         */
        public void replaceExprs(int depth, Function<Boolean, ? extends T> mapper) {
            this.removeExprs(depth, direct -> this.add(mapper.apply(direct)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public RefSet<T> clone() {
            return (RefSet<T>)super.clone();
        }
    }

// MethodInfo

    // Information about a constructor or method in the compilation unit
    private static class MethodInfo {

        private final JCClassDecl declaringClass;
        private final JCMethodDecl declaration;
        private final boolean analyzable;           // it's a constructor we should analyze
        private final boolean invokable;            // it may be "invoked" during analysis

        MethodInfo(JCClassDecl declaringClass, JCMethodDecl declaration,
            boolean analyzable, boolean invokable) {
            this.declaringClass = declaringClass;
            this.declaration = declaration;
            this.analyzable = analyzable;
            this.invokable = invokable;
        }

        public JCClassDecl getDeclaringClass() {
            return this.declaringClass;
        }

        public JCMethodDecl getDeclaration() {
            return this.declaration;
        }

        public boolean isAnalyzable() {
            return this.analyzable;
        }

        public boolean isInvokable() {
            return this.invokable;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
              + "[meth=" + this.declaringClass.name + "." + this.declaration.name + "()"
              + (this.analyzable ? ",analyzable" : "")
              + (this.invokable ? ",invokable" : "")
              + "]";
        }
    }
}
