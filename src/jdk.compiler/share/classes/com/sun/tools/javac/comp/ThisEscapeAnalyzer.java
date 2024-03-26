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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Directive;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symtab;
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
 * If it passes to code outside of the current module, we declare a possible leak.
 *
 * <p>
 * As we analyze constructors and the methods they invoke, we track the various things in scope
 * that could possibly reference the 'this' instance we are following. Such references are
 * represented by {@link Ref} instances, of which there are these varieties:
 * <ul>
 *  <li>The current 'this' reference; see {@link ThisRef}
 *  <li>The current outer 'this' reference; see {@link OuterRef}
 *  <li>Local variables and method parameters; see {@link VarRef}
 *  <li>The current expression being evaluated, i.e.,what's on top of the Java stack; see {@link ExprRef}
 *  <li>The current switch expressions's yield value; see {@link YieldRef}
 *  <li>The current method's return value; see {@link ReturnRef}
 * </ul>
 *
 * <p>
 * For each type of reference, we distinguish between <i>direct</i> and <i>indirect</i> references.
 * A direct reference means the reference directly refers to the 'this' instance we are tracking.
 * An indirect reference means the reference refers to the 'this' instance we are tracking through
 * at least one level of indirection.
 *
 * <p>
 * Currently we do not attempt to explicitly track references stored in fields (for future study).
 *
 * <p>
 * A few notes on this implementation:
 * <ul>
 *  <li>We "execute" constructors and track where the 'this' reference goes as the constructor executes.
 *  <li>We use a very simplified flow analysis that you might call a "flood analysis", where the union
 *      of every possible code branch is taken.
 *  <li>A "leak" is defined as the possible passing of a subclassed 'this' reference to code defined
 *      outside of the current module.
 *  <ul>
 *      <li>In other words, we don't try to protect the current module's code from itself.
 *      <li>For example, we ignore private constructors because they can never be directly invoked
 *          by external subclasses, etc. However, they can be indirectly invoked by other constructors.
 *  </ul>
 *  <li>If a constructor invokes a method defined in the same compilation unit, and that method cannot
 *      be overridden, then our analysis can safely "recurse" into the method.
 *  <ul>
 *      <li>When this occurs the warning displays each step in the stack trace to help in comprehension.
 *  </ul>
 *  <li>We assume that native methods do not leak.
 *  <li>We don't try to follow {@code super()} invocations; that's for the superclass analysis to handle.
 *  </ul>
 */
class ThisEscapeAnalyzer extends TreeScanner {

    private final Names names;
    private final Symtab syms;
    private final Types types;
    private final Log log;
    private       Lint lint;

// These fields are scoped to the entire compilation unit

    /** Maps symbols of all methods to their corresponding declarations.
     */
    private final Map<Symbol, MethodInfo> methodMap = new LinkedHashMap<>();

    /** Contains symbols of fields and constructors that have warnings suppressed.
     */
    private final Set<Symbol> suppressed = new HashSet<>();

    /** The declaring class of the constructor we're currently analyzing.
     *  This is the 'this' type we're trying to detect leaks of.
     */
    private JCClassDecl targetClass;

    /** Snapshots of {@link #callStack} where possible 'this' escapes occur.
     */
    private final ArrayList<DiagnosticPosition[]> warningList = new ArrayList<>();

// These fields are scoped to the constructor being analyzed

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
    private final Set<Pair<JCMethodDecl, RefSet<Ref>>> invocations = new HashSet<>();

    /** Snapshot of {@link #callStack} where a possible 'this' escape occurs.
     *  If non-null, a 'this' escape warning has been found in the current
     *  constructor statement, initialization block statement, or field initializer.
     */
    private DiagnosticPosition[] pendingWarning;

// These fields are scoped to the constructor or invoked method being analyzed

    /** Current lexical scope depth in the constructor or method we're currently analyzing.
     *  Depth zero is the outermost scope. Depth -1 means we're not analyzing.
     */
    private int depth = -1;

    /** Possible 'this' references in the constructor or method we're currently analyzing.
     *  Null value means we're not analyzing.
     */
    private RefSet<Ref> refs;

// Constructor

    ThisEscapeAnalyzer(Names names, Symtab syms, Types types, Log log, Lint lint) {
        this.names = names;
        this.syms = syms;
        this.types = types;
        this.log = log;
        this.lint = lint;
    }

//
// Main method
//

    public void analyzeTree(Env<AttrContext> env) {

        // Sanity check
        Assert.check(checkInvariants(false, false));
        Assert.check(methodMap.isEmpty());      // we are not prepared to be used more than once

        // Short circuit if warnings are totally disabled
        if (!lint.isEnabled(Lint.LintCategory.THIS_ESCAPE))
            return;

        // Determine which packages are exported by the containing module, if any.
        // A null set indicates the unnamed module: all packages are implicitly exported.
        Set<PackageSymbol> exportedPackages = Optional.ofNullable(env.toplevel.modle)
            .filter(mod -> mod != syms.noModule)
            .filter(mod -> mod != syms.unnamedModule)
            .map(mod -> mod.exports.stream()
                            .map(Directive.ExportsDirective::getPackage)
                            .collect(Collectors.toSet()))
            .orElse(null);

        // Build a set of symbols for classes declared in this file
        final Set<Symbol> classSyms = new HashSet<>();
        new TreeScanner() {
            @Override
            public void visitClassDef(JCClassDecl tree) {
                classSyms.add(tree.sym);
                super.visitClassDef(tree);
            }
        }.scan(env.tree);

        // Build a mapping from symbols of methods to their declarations.
        // Classify all ctors and methods as analyzable and/or invokable.
        // Track which constructors and fields have warnings suppressed.
        new TreeScanner() {

            private Lint lint = ThisEscapeAnalyzer.this.lint;
            private JCClassDecl currentClass;
            private boolean nonPublicOuter;

            @Override
            public void visitClassDef(JCClassDecl tree) {
                JCClassDecl currentClassPrev = currentClass;
                boolean nonPublicOuterPrev = nonPublicOuter;
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try {
                    currentClass = tree;
                    nonPublicOuter |= tree.sym.isAnonymous();
                    nonPublicOuter |= (tree.mods.flags & Flags.PUBLIC) == 0;

                    // Recurse
                    super.visitClassDef(tree);
                } finally {
                    currentClass = currentClassPrev;
                    nonPublicOuter = nonPublicOuterPrev;
                    lint = lintPrev;
                }
            }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try {

                    // Track warning suppression of fields
                    if (tree.sym.owner.kind == TYP && !lint.isEnabled(Lint.LintCategory.THIS_ESCAPE))
                        suppressed.add(tree.sym);

                    // Recurse
                    super.visitVarDef(tree);
                } finally {
                    lint = lintPrev;
                }
            }

            @Override
            public void visitMethodDef(JCMethodDecl tree) {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try {

                    // Track warning suppression of constructors
                    if (TreeInfo.isConstructor(tree) && !lint.isEnabled(Lint.LintCategory.THIS_ESCAPE))
                        suppressed.add(tree.sym);

                    // Determine if this is a constructor we should analyze
                    boolean extendable = currentClassIsExternallyExtendable();
                    boolean analyzable = extendable &&
                        TreeInfo.isConstructor(tree) &&
                        (tree.sym.flags() & (Flags.PUBLIC | Flags.PROTECTED)) != 0 &&
                        !suppressed.contains(tree.sym);

                    // Determine if this method is "invokable" in an analysis (can't be overridden)
                    boolean invokable = !extendable ||
                        TreeInfo.isConstructor(tree) ||
                        (tree.mods.flags & (Flags.STATIC | Flags.PRIVATE | Flags.FINAL)) != 0;

                    // Add method or constructor to map
                    methodMap.put(tree.sym, new MethodInfo(currentClass, tree, analyzable, invokable));

                    // Recurse
                    super.visitMethodDef(tree);
                } finally {
                    lint = lintPrev;
                }
            }

            // Determines if the current class could be extended in some other package/module
            private boolean currentClassIsExternallyExtendable() {
                return !currentClass.sym.isFinal() &&
                  currentClass.sym.isPublic() &&
                  (exportedPackages == null || exportedPackages.contains(currentClass.sym.packge())) &&
                  !currentClass.sym.isSealed() &&
                  !currentClass.sym.isDirectlyOrIndirectlyLocal() &&
                  !nonPublicOuter;
            }
        }.scan(env.tree);

        // Analyze non-static field initializers and initialization blocks,
        // but only for classes having at least one analyzable constructor.
        methodMap.values().stream()
                .filter(MethodInfo::analyzable)
                .map(MethodInfo::declaringClass)
                .distinct()
                .forEach(klass -> {
            for (List<JCTree> defs = klass.defs; defs.nonEmpty(); defs = defs.tail) {

                // Ignore static stuff
                if ((TreeInfo.flags(defs.head) & Flags.STATIC) != 0)
                    continue;

                // Handle field initializers
                if (defs.head instanceof JCVariableDecl vardef) {
                    visitTopLevel(klass, () -> {
                        scan(vardef);
                        copyPendingWarning();
                    });
                    continue;
                }

                // Handle initialization blocks
                if (defs.head instanceof JCBlock block) {
                    visitTopLevel(klass, () -> analyzeStatements(block.stats));
                    continue;
                }
            }
        });

        // Analyze all of the analyzable constructors we found
        methodMap.values().stream()
                .filter(MethodInfo::analyzable)
                .forEach(methodInfo -> {
            visitTopLevel(methodInfo.declaringClass(),
                () -> analyzeStatements(methodInfo.declaration().body.stats));
        });

        // Eliminate duplicate warnings. Warning B duplicates warning A if the stack trace of A is a prefix
        // of the stack trace of B. For example, if constructor Foo(int x) has a leak, and constructor
        // Foo() invokes this(0), then emitting a warning for Foo() would be redundant.
        BiPredicate<DiagnosticPosition[], DiagnosticPosition[]> extendsAsPrefix = (warning1, warning2) -> {
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
        Comparator<DiagnosticPosition[]> ordering = (warning1, warning2) -> {
            for (int index1 = 0, index2 = 0; true; index1++, index2++) {
                boolean end1 = index1 >= warning1.length;
                boolean end2 = index2 >= warning2.length;
                if (end1 && end2)
                    return 0;
                if (end1)
                    return -1;
                if (end2)
                    return 1;
                int posn1 = warning1[index1].getPreferredPosition();
                int posn2 = warning2[index2].getPreferredPosition();
                int diff = Integer.compare(posn1, posn2);
                if (diff != 0)
                    return diff;
            }
        };
        warningList.sort(ordering);

        // Now emit the warnings, but skipping over duplicates as we go through the list
        DiagnosticPosition[] previous = null;
        for (DiagnosticPosition[] warning : warningList) {

            // Skip duplicates
            if (previous != null && extendsAsPrefix.test(previous, warning))
                continue;
            previous = warning;

            // Emit warnings showing the entire stack trace
            JCDiagnostic.Warning key = Warnings.PossibleThisEscape;
            int remain = warning.length;
            do {
                DiagnosticPosition pos = warning[--remain];
                log.warning(Lint.LintCategory.THIS_ESCAPE, pos, key);
                key = Warnings.PossibleThisEscapeLocation;
            } while (remain > 0);
        }
        warningList.clear();
    }

    // Analyze statements, but stop at (and record) the first warning generated
    private void analyzeStatements(List<JCStatement> stats) {
        for (JCStatement stat : stats) {
            scan(stat);
            if (copyPendingWarning())
                break;
        }
    }

    @Override
    public void scan(JCTree tree) {

        // Check node
        if (tree == null || tree.type == Type.stuckType)
            return;

        // Sanity check
        Assert.check(checkInvariants(true, false));

        // Can this expression node possibly leave a 'this' reference on the stack?
        boolean referenceExpressionNode;
        switch (tree.getTag()) {
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
        Assert.check(checkInvariants(true, referenceExpressionNode));
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

        // Skip if ignoring warnings for this field
        if (suppressed.contains(tree.sym))
            return;

        // Scan initializer, if any
        scan(tree.init);
        if (isParamOrVar(tree.sym))
            refs.replaceExprs(depth, direct -> new VarRef(tree.sym, direct));
        else
            refs.discardExprs(depth);           // we don't track fields yet
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
        Symbol sym = TreeInfo.symbolFor(invoke.meth);

        // Recurse on method expression
        scan(invoke.meth);
        boolean direct = refs.remove(ExprRef.direct(depth));
        boolean indirect = refs.remove(ExprRef.indirect(depth));

        // Determine if method receiver represents a possible reference
        RefSet<ThisRef> receiverRefs = RefSet.newEmpty();
        if (sym != null && !sym.isStatic()) {
            if (direct)
                receiverRefs.add(ThisRef.direct());
            if (indirect)
                receiverRefs.add(ThisRef.indirect());
        }

        // If "super()": ignore - we don't try to track into superclasses
        if (TreeInfo.name(invoke.meth) == names._super)
            return;

        // "Invoke" the method
        invoke(invoke, sym, invoke.args, receiverRefs);
    }

    private void invoke(JCTree site, Symbol sym, List<JCExpression> args, RefSet<?> receiverRefs) {

        // Skip if ignoring warnings for a constructor invoked via 'this()'
        if (suppressed.contains(sym))
            return;

        // Ignore final methods in java.lang.Object (getClass(), notify(), etc.)
        if (sym != null &&
            sym.owner.kind == TYP &&
            sym.owner.type.tsym == syms.objectType.tsym &&
            sym.isFinal()) {
            return;
        }

        // Analyze method if possible, otherwise assume nothing
        MethodInfo methodInfo = methodMap.get(sym);
        if (methodInfo != null && methodInfo.invokable())
            invokeInvokable(site, args, receiverRefs, methodInfo);
        else
            invokeUnknown(site, args, receiverRefs);
    }

    // Handle the invocation of a local analyzable method or constructor
    private void invokeInvokable(JCTree site, List<JCExpression> args,
        RefSet<?> receiverRefs, MethodInfo methodInfo) {
        Assert.check(methodInfo.invokable());

        // Collect 'this' references found in method parameters
        JCMethodDecl method = methodInfo.declaration();
        RefSet<VarRef> paramRefs = RefSet.newEmpty();
        List<JCVariableDecl> params = method.params;
        while (args.nonEmpty() && params.nonEmpty()) {
            VarSymbol sym = params.head.sym;
            scan(args.head);
            refs.removeExprs(depth, direct -> paramRefs.add(new VarRef(sym, direct)));
            args = args.tail;
            params = params.tail;
        }

        // "Invoke" the method
        JCClassDecl methodClassPrev = methodClass;
        methodClass = methodInfo.declaringClass();
        RefSet<Ref> refsPrev = refs;
        refs = RefSet.newEmpty();
        int depthPrev = depth;
        depth = 0;
        callStack.push(site);
        try {

            // Add initial references from method receiver
            refs.addAll(receiverRefs);

            // Add initial references from parameters
            refs.addAll(paramRefs);

            // Stop trivial cases here
            if (refs.isEmpty())
                return;

            // Stop infinite recursion here
            Pair<JCMethodDecl, RefSet<Ref>> invocation = Pair.of(methodInfo.declaration, refs.clone());
            if (!invocations.add(invocation))
                return;

            // Scan method body to "execute" it
            try {
                scan(method.body);
            } finally {
                invocations.remove(invocation);
            }

            // "Return" any references from method return value
            refs.mapInto(refsPrev, ReturnRef.class, direct -> new ExprRef(depthPrev, direct));
        } finally {
            callStack.pop();
            depth = depthPrev;
            refs = refsPrev;
            methodClass = methodClassPrev;
        }
    }

    // Handle invocation of an unknown or overridable method or constructor
    private void invokeUnknown(JCTree invoke, List<JCExpression> args, RefSet<?> receiverRefs) {

        // Detect leak via receiver
        if (!receiverRefs.isEmpty())
            leakAt(invoke);

        // Detect leaks via method parameters
        for (JCExpression arg : args) {
            scan(arg);
            if (refs.discardExprs(depth))
                leakAt(arg);
        }
    }

//
// Visitor methods - new Foo()
//

    @Override
    public void visitNewClass(JCNewClass tree) {
        MethodInfo methodInfo = methodMap.get(tree.constructor);
        if (methodInfo != null && methodInfo.invokable())
            invokeInvokable(tree, tree.args, outerThisRefs(tree.encl, tree.clazz.type), methodInfo);
        else
            invokeUnknown(tree, tree.args, outerThisRefs(tree.encl, tree.clazz.type));
    }

    // Determine 'this' references passed to a constructor via the outer 'this' instance
    private RefSet<OuterRef> outerThisRefs(JCExpression explicitOuterThis, Type type) {
        RefSet<OuterRef> outerRefs = RefSet.newEmpty();
        if (explicitOuterThis != null) {
            scan(explicitOuterThis);
            refs.removeExprs(depth, direct -> outerRefs.add(new OuterRef(direct)));
        } else if (type.tsym != methodClass.sym && type.tsym.isEnclosedBy(methodClass.sym)) {
            refs.mapInto(outerRefs, ThisRef.class, OuterRef::new);
        }
        return outerRefs;
    }

//
// Visitor methods - Codey Bits
//

    @Override
    public void visitBlock(JCBlock tree) {
        visitScoped(false, () -> super.visitBlock(tree));
        Assert.check(checkInvariants(true, false));
    }

    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        visitLooped(tree, super::visitDoLoop);
    }

    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        visitLooped(tree, super::visitWhileLoop);
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        visitLooped(tree, super::visitForLoop);
    }

    @Override
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        visitLooped(tree, foreach -> {
            scan(foreach.expr);
            refs.discardExprs(depth);       // we don't handle iterator() yet
            scan(foreach.body);
        });
    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        visitScoped(false, () -> {
            scan(tree.selector);
            refs.discardExprs(depth);
            scan(tree.cases);
        });
    }

    @Override
    public void visitSwitchExpression(JCSwitchExpression tree) {
        visitScoped(true, () -> {
            scan(tree.selector);
            refs.discardExprs(depth);
            RefSet<ExprRef> combinedRefs = new RefSet<>();
            for (List<JCCase> cases = tree.cases; cases.nonEmpty(); cases = cases.tail) {
                scan(cases.head.stats);
                refs.replace(YieldRef.class, direct -> new ExprRef(depth, direct));
                combinedRefs.addAll(refs.removeExprs(depth));
            }
            refs.addAll(combinedRefs);
        });
    }

    @Override
    public void visitCase(JCCase tree) {
        scan(tree.stats);          // no need to scan labels
    }

    @Override
    public void visitYield(JCYield tree) {
        scan(tree.value);
        refs.replaceExprs(depth, YieldRef::new);
    }

    @Override
    public void visitLetExpr(LetExpr tree) {
        visitScoped(true, () -> super.visitLetExpr(tree));
    }

    @Override
    public void visitReturn(JCReturn tree) {
        scan(tree.expr);
        refs.replaceExprs(depth, ReturnRef::new);
    }

    @Override
    public void visitLambda(JCLambda lambda) {
        visitDeferred(() -> visitScoped(false, () -> {
            scan(lambda.body);
            refs.discardExprs(depth);       // needed in case body is a JCExpression
        }));
    }

    @Override
    public void visitAssign(JCAssign tree) {
        scan(tree.lhs);
        refs.discardExprs(depth);
        scan(tree.rhs);
        VarSymbol sym = (VarSymbol)TreeInfo.symbolFor(tree.lhs);
        if (isParamOrVar(sym))
            refs.replaceExprs(depth, direct -> new VarRef(sym, direct));
        else
            refs.discardExprs(depth);         // we don't track fields yet
    }

    @Override
    public void visitIndexed(JCArrayAccess tree) {
        scan(tree.indexed);
        refs.remove(ExprRef.direct(depth));
        boolean indirectRef = refs.remove(ExprRef.indirect(depth));
        scan(tree.index);
        refs.discardExprs(depth);
        if (indirectRef) {
            refs.add(ExprRef.direct(depth));
            refs.add(ExprRef.indirect(depth));
        }
    }

    @Override
    public void visitSelect(JCFieldAccess tree) {

        // Scan the selected thing
        scan(tree.selected);
        boolean selectedDirectRef = refs.remove(ExprRef.direct(depth));
        boolean selectedIndirectRef = refs.remove(ExprRef.indirect(depth));

        // Explicit 'this' reference?
        Type.ClassType currentClassType = (Type.ClassType)methodClass.sym.type;
        if (isExplicitThisReference(types, currentClassType, tree)) {
            refs.mapInto(refs, ThisRef.class, direct -> new ExprRef(depth, direct));
            return;
        }

        // Explicit outer 'this' reference?
        Type selectedType = types.erasure(tree.selected.type);
        if (selectedType.hasTag(CLASS)) {
            ClassSymbol currentClassSym = (ClassSymbol)currentClassType.tsym;
            ClassSymbol selectedTypeSym = (ClassSymbol)selectedType.tsym;
            if (tree.name == names._this &&
                    selectedTypeSym != currentClassSym &&
                    currentClassSym.isEnclosedBy(selectedTypeSym)) {
                refs.mapInto(refs, OuterRef.class, direct -> new ExprRef(depth, direct));
                return;
            }
        }

        // Methods - the "value" of a non-static method is a reference to its instance
        Symbol sym = tree.sym;
        if (sym.kind == MTH) {
            if ((sym.flags() & Flags.STATIC) == 0) {
                if (selectedDirectRef)
                    refs.add(ExprRef.direct(depth));
                if (selectedIndirectRef)
                    refs.add(ExprRef.indirect(depth));
            }
            return;
        }

        // Unknown
        return;
    }

    @Override
    public void visitReference(JCMemberReference tree) {
        if (tree.type.isErroneous()) {
            //error recovery - ignore erroneous member references
            return ;
        }

        // Scan target expression and extract 'this' references, if any
        scan(tree.expr);
        boolean direct = refs.remove(ExprRef.direct(depth));
        boolean indirect = refs.remove(ExprRef.indirect(depth));

        // Gather receiver references for deferred invocation
        RefSet<Ref> receiverRefs = RefSet.newEmpty();
        switch (tree.kind) {
        case UNBOUND:
        case STATIC:
        case TOPLEVEL:
        case ARRAY_CTOR:
            return;
        case SUPER:
            refs.mapInto(receiverRefs, ThisRef.class, ThisRef::new);
            break;
        case BOUND:
            if (direct)
                receiverRefs.add(ThisRef.direct());
            if (indirect)
                receiverRefs.add(ThisRef.indirect());
            break;
        case IMPLICIT_INNER:
            receiverRefs.addAll(outerThisRefs(null, tree.expr.type));
            break;
        default:
            throw new RuntimeException("non-exhaustive?");
        }

        // Treat method reference just like the equivalent lambda
        visitDeferred(() -> invoke(tree, (MethodSymbol)tree.sym, List.nil(), receiverRefs));
    }

    @Override
    public void visitIdent(JCIdent tree) {

        // Reference to this?
        if (tree.name == names._this || tree.name == names._super) {
            refs.mapInto(refs, ThisRef.class, direct -> new ExprRef(depth, direct));
            return;
        }

        // Parameter or local variable?
        if (isParamOrVar(tree.sym)) {
            VarSymbol sym = (VarSymbol)tree.sym;
            if (refs.contains(VarRef.direct(sym)))
                refs.add(ExprRef.direct(depth));
            if (refs.contains(VarRef.indirect(sym)))
                refs.add(ExprRef.indirect(depth));
            return;
        }

        // An unqualified, non-static method invocation must reference 'this' or outer 'this'.
        // The "value" of a non-static method is a reference to its instance.
        if (tree.sym.kind == MTH && (tree.sym.flags() & Flags.STATIC) == 0) {
            MethodSymbol sym = (MethodSymbol)tree.sym;

            // Check for implicit 'this' reference
            ClassSymbol methodClassSym = methodClass.sym;
            if (methodClassSym.isSubClass(sym.owner, types)) {
                refs.mapInto(refs, ThisRef.class, direct -> new ExprRef(depth, direct));
                return;
            }

            // Check for implicit outer 'this' reference
            if (methodClassSym.isEnclosedBy((ClassSymbol)sym.owner)) {
                refs.mapInto(refs, OuterRef.class, direct -> new ExprRef(depth, direct));
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
        scan(tree.lock);
        refs.discardExprs(depth);
        scan(tree.body);
    }

    @Override
    public void visitConditional(JCConditional tree) {
        scan(tree.cond);
        refs.discardExprs(depth);
        RefSet<ExprRef> combinedRefs = new RefSet<>();
        scan(tree.truepart);
        combinedRefs.addAll(refs.removeExprs(depth));
        scan(tree.falsepart);
        combinedRefs.addAll(refs.removeExprs(depth));
        refs.addAll(combinedRefs);
    }

    @Override
    public void visitIf(JCIf tree) {
        scan(tree.cond);
        refs.discardExprs(depth);
        scan(tree.thenpart);
        scan(tree.elsepart);
    }

    @Override
    public void visitExec(JCExpressionStatement tree) {
        scan(tree.expr);
        refs.discardExprs(depth);
    }

    @Override
    public void visitThrow(JCThrow tree) {
        scan(tree.expr);
        if (refs.discardExprs(depth))     // we don't try to "catch" refs from thrown exceptions
            leakAt(tree);
    }

    @Override
    public void visitAssert(JCAssert tree) {
        scan(tree.cond);
        refs.discardExprs(depth);
        scan(tree.detail);
        refs.discardExprs(depth);
    }

    @Override
    public void visitNewArray(JCNewArray tree) {
        boolean ref = false;
        if (tree.elems != null) {
            for (List<JCExpression> elems = tree.elems; elems.nonEmpty(); elems = elems.tail) {
                scan(elems.head);
                ref |= refs.discardExprs(depth);
            }
        }
        if (ref)
            refs.add(ExprRef.indirect(depth));
    }

    @Override
    public void visitTypeCast(JCTypeCast tree) {
        scan(tree.expr);
    }

    @Override
    public void visitConstantCaseLabel(JCConstantCaseLabel tree) {
    }

    @Override
    public void visitPatternCaseLabel(JCPatternCaseLabel tree) {
    }

    @Override
    public void visitRecordPattern(JCRecordPattern that) {
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        scan(tree.expr);
        refs.discardExprs(depth);
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
        scan(tree.lhs);
        refs.discardExprs(depth);
        scan(tree.rhs);
        refs.discardExprs(depth);
    }

    @Override
    public void visitUnary(JCUnary tree) {
        scan(tree.arg);
        refs.discardExprs(depth);
    }

    @Override
    public void visitBinary(JCBinary tree) {
        scan(tree.lhs);
        refs.discardExprs(depth);
        scan(tree.rhs);
        refs.discardExprs(depth);
    }

// Helper methods

    private void visitTopLevel(JCClassDecl klass, Runnable action) {
        Assert.check(targetClass == null);
        Assert.check(methodClass == null);
        Assert.check(depth == -1);
        Assert.check(refs == null);
        targetClass = klass;
        methodClass = klass;
        try {

            // Add the initial 'this' reference
            refs = RefSet.newEmpty();
            refs.add(ThisRef.direct());

            // Perform action
            this.visitScoped(false, action);
        } finally {
            Assert.check(depth == -1);
            methodClass = null;
            targetClass = null;
            refs = null;
        }
    }

    // Recurse through indirect code that might get executed later, e.g., a lambda.
    // We stash any pending warning and the current RefSet, then recurse into the deferred
    // code (still using the current RefSet) to see if it would leak. Then we restore the
    // pending warning and the current RefSet. Finally, if the deferred code would have
    // leaked, we create an indirect ExprRef because it must be holding a 'this' reference.
    // If the deferred code would not leak, then obviously no leak is possible, period.
    private <T extends JCTree> void visitDeferred(Runnable recurse) {
        DiagnosticPosition[] pendingWarningPrev = pendingWarning;
        pendingWarning = null;
        RefSet<Ref> refsPrev = refs.clone();
        boolean deferredCodeLeaks;
        try {
            recurse.run();
            deferredCodeLeaks = pendingWarning != null;
        } finally {
            refs = refsPrev;
            pendingWarning = pendingWarningPrev;
        }
        if (deferredCodeLeaks)
            refs.add(ExprRef.indirect(depth));
    }

    // Repeat loop as needed until the current set of references converges
    private <T extends JCTree> void visitLooped(T tree, Consumer<T> visitor) {
        visitScoped(false, () -> {
            while (true) {
                RefSet<Ref> prevRefs = refs.clone();
                visitor.accept(tree);
                if (refs.equals(prevRefs))
                    break;
            }
        });
    }

    // Perform the given action within a new scope
    private void visitScoped(boolean promote, Runnable action) {
        pushScope();
        try {

            // Perform action
            Assert.check(checkInvariants(true, false));
            action.run();
            Assert.check(checkInvariants(true, promote));

            // "Promote" ExprRef's to the enclosing lexical scope, if requested
            if (promote) {
                Assert.check(depth > 0);
                refs.removeExprs(depth, direct -> refs.add(new ExprRef(depth - 1, direct)));
            }
        } finally {
            popScope();
        }
    }

    private void pushScope() {
        depth++;
    }

    private void popScope() {
        Assert.check(depth >= 0);
        depth--;
        refs.removeIf(ref -> ref.getDepth() > depth);
    }

    // Note a possible 'this' reference leak at the specified location
    private void leakAt(JCTree tree) {

        // Generate at most one warning per statement
        if (pendingWarning != null)
            return;

        // Snapshot the current stack trace
        callStack.push(tree.pos());
        pendingWarning = callStack.toArray(new DiagnosticPosition[0]);
        callStack.pop();
    }

    // Copy pending warning, if any, to the warning list and reset
    private boolean copyPendingWarning() {
        if (pendingWarning == null)
            return false;
        warningList.add(pendingWarning);
        pendingWarning = null;
        return true;
    }

    // Does the symbol correspond to a parameter or local variable (not a field)?
    private boolean isParamOrVar(Symbol sym) {
        return sym != null &&
            sym.kind == VAR &&
            (sym.owner.kind == MTH || sym.owner.kind == VAR);
    }

    /** Check if the given tree is an explicit reference to the 'this' instance of the
     *  class currently being compiled. This is true if tree is:
     *  - An unqualified 'this' identifier
     *  - A 'super' identifier qualified by a class name whose type is 'currentClass' or a supertype
     *  - A 'this' identifier qualified by a class name whose type is 'currentClass' or a supertype
     *    but also NOT an enclosing outer class of 'currentClass'.
     */
    private boolean isExplicitThisReference(Types types, Type.ClassType currentClass, JCTree tree) {
        switch (tree.getTag()) {
            case PARENS:
                return isExplicitThisReference(types, currentClass, TreeInfo.skipParens(tree));
            case IDENT:
            {
                JCIdent ident = (JCIdent)tree;
                Names names = ident.name.table.names;
                return ident.name == names._this;
            }
            case SELECT:
            {
                JCFieldAccess select = (JCFieldAccess)tree;
                Type selectedType = types.erasure(select.selected.type);
                if (!selectedType.hasTag(CLASS))
                    return false;
                ClassSymbol currentClassSym = (ClassSymbol)((Type.ClassType)types.erasure(currentClass)).tsym;
                ClassSymbol selectedClassSym = (ClassSymbol)((Type.ClassType)selectedType).tsym;
                Names names = select.name.table.names;
                return currentClassSym.isSubClass(selectedClassSym, types) &&
                        (select.name == names._super ||
                        (select.name == names._this &&
                            (currentClassSym == selectedClassSym ||
                            !currentClassSym.isEnclosedBy(selectedClassSym))));
            }
            default:
                return false;
        }
    }

    // When scanning nodes we can be in one of two modes:
    //  (a) Looking for constructors - we do not recurse into any code blocks
    //  (b) Analyzing a constructor - we are tracing its possible execution paths
    private boolean isAnalyzing() {
        return targetClass != null;
    }

// Debugging

    // Invariant checks
    private boolean checkInvariants(boolean analyzing, boolean allowExpr) {
        Assert.check(analyzing == isAnalyzing());
        if (isAnalyzing()) {
            Assert.check(methodClass != null);
            Assert.check(targetClass != null);
            Assert.check(refs != null);
            Assert.check(depth >= 0);
            Assert.check(refs.stream().noneMatch(ref -> ref.getDepth() > depth));
            Assert.check(allowExpr || !refs.contains(ExprRef.direct(depth)));
            Assert.check(allowExpr || !refs.contains(ExprRef.indirect(depth)));
        } else {
            Assert.check(targetClass == null);
            Assert.check(refs == null);
            Assert.check(depth == -1);
            Assert.check(callStack.isEmpty());
            Assert.check(pendingWarning == null);
            Assert.check(invocations.isEmpty());
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
            return depth;
        }

        public boolean isDirect() {
            return direct;
        }

        @Override
        public int hashCode() {
            return getClass().hashCode()
                ^ Integer.hashCode(depth)
                ^ Boolean.hashCode(direct);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != getClass())
                return false;
            Ref that = (Ref)obj;
            return depth == that.depth
              && direct == that.direct;
        }

        @Override
        public String toString() {
            ArrayList<String> properties = new ArrayList<>();
            addProperties(properties);
            return getClass().getSimpleName()
              + "[" + properties.stream().collect(Collectors.joining(",")) + "]";
        }

        protected void addProperties(ArrayList<String> properties) {
            properties.add("depth=" + depth);
            properties.add(direct ? "direct" : "indirect");
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
    }

    /** A reference from the yield value of the current switch expression.
     */
    private static class YieldRef extends Ref {

        YieldRef(boolean direct) {
            super(0, direct);
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
            return sym;
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
                ^ Objects.hashCode(sym);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            VarRef that = (VarRef)obj;
            return Objects.equals(sym, that.sym);
        }

        @Override
        protected void addProperties(ArrayList<String> properties) {
            super.addProperties(properties);
            properties.add("sym=" + sym);
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
            return remove(ExprRef.direct(depth)) | remove(ExprRef.indirect(depth));
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
         * Replace any references of the given type.
         */
        public void replace(Class<? extends Ref> type, Function<Boolean, ? extends T> mapper) {
            final List<Ref> oldRefs = this.stream()
              .filter(type::isInstance)
              .collect(List.collector());             // avoid ConcurrentModificationException
            this.removeAll(oldRefs);
            oldRefs.stream()
              .map(Ref::isDirect)
              .map(mapper)
              .forEach(this::add);
        }

        /**
         * Replace any {@link ExprRef}'s at the specified depth.
         */
        public void replaceExprs(int depth, Function<Boolean, ? extends T> mapper) {
            removeExprs(depth, direct -> add(mapper.apply(direct)));
        }

        /**
         * Find references of the given type, map them, and add them to {@code dest}.
         */
        public <S extends Ref> void mapInto(RefSet<S> dest, Class<? extends Ref> type,
                Function<Boolean, ? extends S> mapper) {
            final List<S> newRefs = this.stream()
              .filter(type::isInstance)
              .map(Ref::isDirect)
              .map(mapper)
              .collect(List.collector());             // avoid ConcurrentModificationException
            dest.addAll(newRefs);
        }

        @Override
        @SuppressWarnings("unchecked")
        public RefSet<T> clone() {
            return (RefSet<T>)super.clone();
        }
    }

// MethodInfo

    // Information about a constructor or method in the compilation unit
    private record MethodInfo(
        JCClassDecl declaringClass,     // the class declaring "declaration"
        JCMethodDecl declaration,       // the method or constructor itself
        boolean analyzable,             // it's a constructor that we should analyze
        boolean invokable) {            // it may be safely "invoked" during analysis
    }
}
