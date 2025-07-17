/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Directive;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.resources.CompilerProperties.LintWarnings;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Lint.LintCategory.THIS_ESCAPE;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * Looks for possible 'this' escapes and generates corresponding warnings.
 *
 * <p>
 * A 'this' escape occurs in the following scenario:
 * <ul>
 *  <li>There is some class {@code A} and some subclass {@code B} that extends it
 *  <li>{@code A} defines an instance method {@code m()} which is overridden in {@code B}
 *  <li>Some constructor {@code B()} invokes some superclass constructor {@code A()}
 *  <li>At some point during the execution of {@code A()}, method {@code m()} is invoked and the
 *      reciever for the instance method is the new instance being constructed by {@code B()}
 * </ul>
 * This represents a problem because the method {@code B.m()} will execute before the constructor
 * {@code B()} has performed any of its own initialization.
 *
 * <p>
 * This class attempts to identify possible 'this' escapes while also striking a balance
 * between false positives, false negatives, and code complexity. We do this by "executing"
 * the code in candidate constructors and tracking where the original 'this' reference goes.
 * If it passes to code outside of the current module, we declare a possible leak.
 *
 * <p>
 * As we analyze constructors and the methods they invoke, we track the various object references that
 * might reference the 'this' instance we are watching (i.e., the one under construction). Such object
 * references are represented by the {@link Ref} class hierarchy, which models the various ways in which,
 * at any point during the execution of a constructor or some other method or constructor that it invokes,
 * there can live references to the object under construction lying around. In a nutshell, the analyzer
 * keeps track of these references and watches what happens to them as the code executes so it can catch
 * them in the act of trying to "escape".
 *
 * <p>
 * The {@link Ref} sub-types are:
 * <ul>
 *  <li>{@link ThisRef} - The current 'this' instance of the (instance) method being analyzed
 *  <li>{@link ExprRef} - The current expression being evaluated, i.e., what's on top of the Java stack
 *  <li>{@link VarRef} - Local variables and method parameters currently in scope
 *  <li>{@link YieldRef} - The current switch expression's yield value(s)
 *  <li>{@link ReturnRef} - The current method's return value(s)
 * </ul>
 *
 * <p>
 * Currently we don't attempt to explicitly track references stored in fields (for future study).
 *
 * <p>
 * For each object reference represented by a {@link Ref}, we track up to three distinct ways in which
 * it might refer to the new 'this' instance: the reference can be direct, indirect, or via an associated
 * enclosing instance (see {@link Indirection}).
 *
 * <p>
 * A few notes on this implementation:
 * <ul>
 *  <li>We "execute" constructors and track how the {@link Ref}'s evolve as the constructor executes.
 *  <li>We use a simplified "flooding" flow analysis where every possible code branch is taken and
 *      we take the union of the resulting {@link Ref}'s that are generated.
 *  <li>Loops are repeated until the set of {@link Ref}'s stabilizes; the maximum number of iterations
 *      possible is proportional to the number of variables in scope.
 *  <li>An "escape" is defined as the possible passing of a subclassed 'this' reference to code defined
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
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class ThisEscapeAnalyzer extends TreeScanner {

    protected static final Context.Key<ThisEscapeAnalyzer> contextKey = new Context.Key<>();

// Other singletons we utilize

    private final Names names;
    private final Symtab syms;
    private final Types types;
    private final Resolve rs;
    private final Log log;
    private       Lint lint;

// These fields are scoped to the entire compilation unit

    /** Environment for symbol lookup.
     */
    private Env<AttrContext> topLevelEnv;

    /** Maps symbols of all methods to their corresponding declarations.
     */
    private final Map<Symbol, MethodInfo> methodMap = new LinkedHashMap<>();

    /** Contains symbols of fields and constructors that have warnings suppressed.
     */
    private final Set<Symbol> suppressed = new HashSet<>();

    /** Contains classes whose outer instance (if any) is non-public.
     */
    private final Set<ClassSymbol> nonPublicOuters = new HashSet<>();

    /** The declaring class of the constructor we're currently analyzing.
     *  This is the 'this' type we're trying to detect leaks of.
     */
    private JCClassDecl targetClass;

    /** Snapshots of {@link #callStack} where possible 'this' escapes occur.
     */
    private final ArrayList<Warning> warningList = new ArrayList<>();

// These fields are scoped to the constructor being analyzed

    /** The method we're currently analyzing.
     *  This is either the analyzed constructor or some method it invokes.
     */
    private MethodInfo currentMethod;

    /** The current "call stack" during our analysis. The first entry is the initial
     *  constructor we started with, and subsequent entries correspond to invoked methods.
     *  If we're still in the initial constructor, the list will be empty.
     */
    private final ArrayList<StackFrame> callStack = new ArrayList<>();

    /** Used to terminate recursion in {@link #invokeInvokable invokeInvokable()}.
     */
    private final Set<Pair<JCMethodDecl, RefSet<Ref>>> invocations = new HashSet<>();

// These fields are scoped to the constructor or invoked method being analyzed

    /** Current lexical scope depth in the constructor or method we're currently analyzing.
     *  Depth zero is the outermost scope. Depth -1 means we're not analyzing.
     */
    private int depth = -1;

    /** Possible 'this' references in the constructor or method we're currently analyzing.
     *  Null value means we're not analyzing.
     */
    private RefSet<Ref> refs;

// Access

    public static ThisEscapeAnalyzer instance(Context context) {
        ThisEscapeAnalyzer instance = context.get(contextKey);
        if (instance == null)
            instance = new ThisEscapeAnalyzer(context);
        return instance;
    }

    @SuppressWarnings("this-escape")
    protected ThisEscapeAnalyzer(Context context) {
        context.put(contextKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        rs = Resolve.instance(context);
        lint = Lint.instance(context);
    }

//
// Main method
//

    public void analyzeTree(Env<AttrContext> env) {
        topLevelEnv = env;
        try {
            doAnalyzeTree(env);
        } finally {
            topLevelEnv = null;
            methodMap.clear();
            nonPublicOuters.clear();
            targetClass = null;
            warningList.clear();
            currentMethod = null;
            callStack.clear();
            invocations.clear();
            depth = -1;
            refs = null;
        }
    }

    private void doAnalyzeTree(Env<AttrContext> env) {

        // Sanity check
        Assert.check(checkInvariants(false, false));
        Assert.check(methodMap.isEmpty());      // we are not prepared to be used more than once

        // Short circuit if warnings are totally disabled
        if (!lint.isEnabled(THIS_ESCAPE))
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

        // Build a mapping from symbols of methods to their declarations.
        // Classify all ctors and methods as analyzable and/or invokable.
        // Track which constructors and fields have warnings suppressed.
        // Record classes whose outer instance (if any) is non-public.
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

                    // Track which clases have non-public outer instances
                    nonPublicOuter |= tree.sym.isAnonymous();
                    nonPublicOuter |= (tree.mods.flags & Flags.PUBLIC) == 0;
                    if (nonPublicOuter)
                        nonPublicOuters.add(currentClass.sym);

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
                    if (tree.sym.owner.kind == TYP && !lint.isEnabled(THIS_ESCAPE))
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
                    if (TreeInfo.isConstructor(tree) && !lint.isEnabled(THIS_ESCAPE))
                        suppressed.add(tree.sym);

                    // Gather some useful info
                    boolean constructor = TreeInfo.isConstructor(tree);
                    boolean extendableClass = currentClassIsExternallyExtendable();
                    boolean nonPrivate = (tree.sym.flags() & (Flags.PUBLIC | Flags.PROTECTED)) != 0;
                    boolean finalish = (tree.mods.flags & (Flags.STATIC | Flags.PRIVATE | Flags.FINAL)) != 0;

                    // Determine if this is a constructor we should analyze
                    boolean analyzable = extendableClass && constructor && nonPrivate;

                    // Determine if it's safe to "invoke" the method in an analysis (i.e., it can't be overridden)
                    boolean invokable = !extendableClass || constructor || finalish;

                    // Add this method or constructor to our map
                    methodMap.put(tree.sym, new MethodInfo(currentClass, tree, constructor, analyzable, invokable));

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

        // Analyze the analyzable constructors we found
        methodMap.values().stream()
          .filter(MethodInfo::analyzable)
          .forEach(this::analyzeConstructor);

        // Manually apply any Lint suppression
        filterWarnings(warning -> !warning.isSuppressed());

        // Field intitializers and initialization blocks will generate a separate warning for each primary constructor.
        // Trim off stack frames up through the super() call so these will have identical stacks and get de-duplicated below.
        warningList.forEach(Warning::trimInitializerFrames);

        // Sort warnings so redundant warnings immediately follow whatever they are redundant for, then remove them
        warningList.sort(Warning::sortByStackFrames);
        AtomicReference<Warning> previousRef = new AtomicReference<>();
        filterWarnings(warning -> {
            Warning previous = previousRef.get();
            if (previous != null && warning.isRedundantWith(previous))
                return false;
            previousRef.set(warning);
            return true;
        });

        // Limit output to one warning per constructor, field initializer, or initializer block
        Set<JCTree> thingsWarnedAbout = new HashSet<>();
        filterWarnings(warning -> thingsWarnedAbout.add(warning.origin));

        // Emit warnings
        for (Warning warning : warningList) {
            LintWarning key = LintWarnings.PossibleThisEscape;
            for (StackFrame frame : warning.stack) {
                log.warning(frame.site.pos(), key);
                key = LintWarnings.PossibleThisEscapeLocation;
            }
        }

        // Done
        warningList.clear();
    }

    // Warning list editor (this is slightly more efficient than removeIf())
    private void filterWarnings(Predicate<Warning> filter) {
        int numRetained = 0;
        for (Warning warning : warningList) {
            if (filter.test(warning))
                warningList.set(numRetained++, warning);
        }
        warningList.subList(numRetained, warningList.size()).clear();
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
        visitVarDef(tree.sym, tree.init);
    }

    private void visitVarDef(VarSymbol sym, JCExpression expr) {

        // Scan initializer, if any
        scan(expr);
        if (isParamOrVar(sym))
            refs.replaceExprs(depth, ref -> new VarRef(sym, ref));
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

        // Recurse on method expression and gather references from the method itself (if non-static)
        scan(invoke.meth);
        RefSet<ThisRef> receiverRefs = RefSet.newEmpty();
        if (sym != null && !sym.isStatic()) {
            refs.removeExprs(depth)
              .map(ThisRef::new)
              .forEach(receiverRefs::add);
        } else
            refs.discardExprs(depth);

        // If "super()": we don't invoke it (we don't track into superclasses) but we do execute any
        // non-static field initializers and initialization blocks because this is when they happen.
        if (TreeInfo.name(invoke.meth) == names._super) {
            currentMethod.declaringClass.defs.stream()
              .filter(def -> (TreeInfo.flags(def) & Flags.STATIC) == 0)
              .forEach(def -> {
                switch (def) {
                case JCBlock block          -> analyzeInitializer(invoke, block, receiverRefs, () -> visitBlock(block));
                case JCVariableDecl varDecl -> analyzeInitializer(invoke, varDecl, receiverRefs, () -> scan(varDecl));
                default -> { }
                }
              });
            return;
        }

        // "Invoke" the method
        invoke(invoke, sym, invoke.args, receiverRefs);
    }

    // Analyze a field initializer or initialization block after encountering a super() invocation
    private void analyzeInitializer(JCMethodInvocation site, JCTree initializer, RefSet<ThisRef> receiverRefs, Runnable action) {
        RefSet<Ref> refsPrev = refs;
        refs = RefSet.newEmpty();
        int depthPrev = depth;
        depth = 0;
        callStack.add(new StackFrame(currentMethod, initializer, site));
        try {
            refs.addAll(receiverRefs);
            action.run();
        } finally {
            callStack.remove(callStack.size() - 1);
            depth = depthPrev;
            refs = refsPrev;
        }
    }

    private void invoke(JCTree site, Symbol sym, List<JCExpression> args, RefSet<ThisRef> receiverRefs) {

        // Ignore final methods in java.lang.Object (getClass(), notify(), etc.)
        if (sym != null &&
            sym.owner.kind == TYP &&
            sym.owner.type.tsym == syms.objectType.tsym &&
            sym.isFinal()) {
            return;
        }

        // See if this method is known because it's declared somewhere in our file
        MethodInfo methodInfo = methodMap.get(sym);

        // If the method is not matched exactly, look a little harder. This especially helps
        // with anonymous interface classes, where the method symbols won't match.
        //
        // For example:
        //
        //  public Leaker() {
        //      Runnable r = new Runnable() {
        //          public void run() {
        //              Leaker.this.mightLeak();
        //          }
        //      };
        //      r.run();    // "r" has type Runnable, but we know it's really a Leaker$1
        //  }
        //
        if (methodInfo == null && receiverRefs.size() == 1) {
            ThisRef receiverRef = receiverRefs.iterator().next();
            methodInfo = methodMap.values().stream()
              .filter(info -> isTargetMethod(info, sym, receiverRef.tsym))
              .findFirst()
              .orElse(null);
        }

        // Analyze method if possible, otherwise assume nothing
        if (methodInfo != null && methodInfo.invokable)
            invokeInvokable(site, args, receiverRefs, methodInfo);
        else
            invokeUnknown(site, args, receiverRefs);
    }

    // Can we conclude that "info" represents the actual method invoked?
    private boolean isTargetMethod(MethodInfo info, Symbol method, TypeSymbol receiverType) {
        return method.kind == MTH &&                                            // not an error symbol, etc.
          info.declaration.name == method.name &&                               // method name matches
          info.declaringClass.sym == receiverType &&                            // same class as receiver
          !info.declaration.sym.isConstructor() &&                              // not a constructor
          (info.declaration.sym.flags() & Flags.STATIC) == 0 &&                 // not a static method
          info.declaration.sym.overrides(method, receiverType, types, false);   // method overrides
    }

    // Handle the invocation of a local analyzable method or constructor
    private void invokeInvokable(JCTree site, List<JCExpression> args, RefSet<ThisRef> receiverRefs, MethodInfo methodInfo) {
        Assert.check(methodInfo.invokable);

        // Collect 'this' references found in method parameters
        JCMethodDecl method = methodInfo.declaration;
        RefSet<VarRef> paramRefs = RefSet.newEmpty();
        List<JCVariableDecl> params = method.params;
        while (args.nonEmpty() && params.nonEmpty()) {
            VarSymbol sym = params.head.sym;
            scan(args.head);
            refs.removeExprs(depth)
              .map(ref -> new VarRef(sym, ref))
              .forEach(paramRefs::add);
            args = args.tail;
            params = params.tail;
        }

        // "Invoke" the method
        MethodInfo currentMethodPrev = currentMethod;
        currentMethod = methodInfo;
        RefSet<Ref> refsPrev = refs;
        refs = RefSet.newEmpty();
        int depthPrev = depth;
        depth = 0;
        callStack.add(new StackFrame(currentMethodPrev, null, site));
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

            // Constructors "return" their new instances
            if (TreeInfo.isConstructor(methodInfo.declaration)) {
                refs.remove(ThisRef.class)
                  .map(ReturnRef::new)
                  .forEach(refs::add);
            }

            // "Return" any references from method return statements
            refs.remove(ReturnRef.class)
              .map(ref -> new ExprRef(depthPrev, ref))
              .forEach(refsPrev::add);
        } finally {
            callStack.remove(callStack.size() - 1);
            depth = depthPrev;
            refs = refsPrev;
            currentMethod = currentMethodPrev;
        }
    }

    // Handle invocation of an unknown or overridable method or constructor.
    private void invokeUnknown(JCTree invoke, List<JCExpression> args, RefSet<ThisRef> receiverRefs) {

        // Detect leak via receiver
        if (receiverRefs.stream().anyMatch(this::triggersUnknownInvokeLeak))
            leakAt(invoke);

        // Detect leaks via method parameters (except via non-public outer instance)
        for (JCExpression arg : args) {
            scan(arg);
            if (refs.removeExprs(depth).anyMatch(this::triggersUnknownInvokeLeak))
                leakAt(arg);
        }

        // Constructors "return" their new instance, so we should return the receiver refs
        if (invoke.hasTag(NEWCLASS)) {
            receiverRefs.stream()
              .map(ref -> new ExprRef(depth, ref))
              .forEach(refs::add);
        }
    }

    // Determine if a reference should qualify as a leak if it's passed to an unknown method.
    // To avoid false positives, we exclude references from non-public outer instances.
    private boolean triggersUnknownInvokeLeak(Ref ref) {
        return !nonPublicOuters.contains(ref.tsym) ||
          ref.indirections.stream().anyMatch(i -> i != Indirection.OUTER);
    }

//
// Visitor methods - new Foo()
//

    @Override
    public void visitNewClass(JCNewClass tree) {
        MethodInfo methodInfo = methodMap.get(tree.constructor);
        TypeSymbol tsym = tree.def != null ? tree.def.sym : tree.clazz.type.tsym;

        // Gather 'this' reference that the new instance itself will have
        RefSet<ThisRef> receiverRefs = receiverRefsForConstructor(tree.encl, tsym);

        // "Invoke" the constructor
        if (methodInfo != null && methodInfo.invokable)
            invokeInvokable(tree, tree.args, receiverRefs, methodInfo);
        else
            invokeUnknown(tree, tree.args, receiverRefs);
    }

    // Determine the references a constructor will inherit from its outer 'this' instance, if any
    private RefSet<ThisRef> receiverRefsForConstructor(JCExpression explicitOuterThis, TypeSymbol tsym) {

        // Create references based on explicit outer instance, if any
        if (explicitOuterThis != null) {
            scan(explicitOuterThis);
            return refs.removeExprs(depth)
              .map(ref -> ref.toOuter(explicitOuterThis.type.tsym))
              .flatMap(Optional::stream)
              .collect(RefSet.collector());
        }

        // Create references based on current outer instance, if any
        if (hasImplicitOuterInstance(tsym)) {
            return refs.find(ThisRef.class)
              .map(ref -> ref.toOuter(tsym))
              .flatMap(Optional::stream)
              .collect(RefSet.collector());
        }

        // None
        return RefSet.newEmpty();
    }

    // Determine if an unqualified "new Foo()" constructor gets 'this' as an implicit outer instance
    private boolean hasImplicitOuterInstance(TypeSymbol tsym) {
        ClassSymbol currentClassSym = currentMethod.declaringClass.sym;
        return tsym != currentClassSym
          && tsym.hasOuterInstance()
          && tsym.isEnclosedBy(currentClassSym);
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

        // Check for loop on array
        Type elemType = types.elemtype(tree.expr.type);

        // If not array, resolve the Iterable and Iterator methods
        record ForeachMethods(MethodSymbol iterator, MethodSymbol hasNext, MethodSymbol next) { }
        MethodSymbol iterator = null;
        MethodSymbol hasNext = null;
        MethodSymbol next = null;
        if (elemType == null) {
            Symbol iteratorSym = rs.resolveQualifiedMethod(tree.expr.pos(), topLevelEnv,
              tree.expr.type, names.iterator, List.nil(), List.nil());
            if (iteratorSym instanceof MethodSymbol) {
                iterator = (MethodSymbol)iteratorSym;
                Symbol hasNextSym = rs.resolveQualifiedMethod(tree.expr.pos(), topLevelEnv,
                  iterator.getReturnType(), names.hasNext, List.nil(), List.nil());
                Symbol nextSym = rs.resolveQualifiedMethod(tree.expr.pos(), topLevelEnv,
                  iterator.getReturnType(), names.next, List.nil(), List.nil());
                if (hasNextSym instanceof MethodSymbol)
                    hasNext = (MethodSymbol)hasNextSym;
                if (nextSym instanceof MethodSymbol)
                    next = (MethodSymbol)nextSym;
            }
        }
        ForeachMethods foreachMethods = iterator != null && hasNext != null && next != null ?
          new ForeachMethods(iterator, hasNext, next) : null;

        // Iterate loop
        visitLooped(tree, foreach -> {

            // Scan iteration target
            scan(foreach.expr);
            if (elemType != null) {                     // array iteration
                if (isParamOrVar(foreach.var.sym)) {
                    refs.removeExprs(depth)
                      .map(ref -> ref.toIndirect(elemType.tsym))
                      .flatMap(Optional::stream)
                      .map(ref -> new VarRef(foreach.var.sym, ref))
                      .forEach(refs::add);
                } else
                    refs.discardExprs(depth);           // we don't track fields yet
            } else if (foreachMethods != null) {        // Iterable iteration

                // "Invoke" the iterator() method
                RefSet<ThisRef> receiverRefs = refs.removeExprs(depth)
                  .map(ThisRef::new)
                  .collect(RefSet.collector());
                invoke(foreach.expr, foreachMethods.iterator, List.nil(), receiverRefs);

                // "Invoke" the hasNext() method
                receiverRefs = refs.removeExprs(depth)
                  .map(ThisRef::new)
                  .collect(RefSet.collector());
                invoke(foreach.expr, foreachMethods.hasNext, List.nil(), receiverRefs);
                refs.discardExprs(depth);

                // "Invoke" the next() method
                invoke(foreach.expr, foreachMethods.next, List.nil(), receiverRefs);
                if (isParamOrVar(foreach.var.sym))
                    refs.replaceExprs(depth, ref -> new VarRef(foreach.var.sym, ref));
                else
                    refs.discardExprs(depth);           // we don't track fields yet
            } else                                      // what is it???
                refs.discardExprs(depth);

            // Scan loop body
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
            RefSet<ExprRef> combinedRefs = RefSet.newEmpty();
            for (List<JCCase> cases = tree.cases; cases.nonEmpty(); cases = cases.tail) {
                scan(cases.head.stats);
                refs.remove(YieldRef.class)
                  .map(ref -> new ExprRef(depth, ref))
                  .forEach(combinedRefs::add);
                refs.removeExprs(depth)
                  .forEach(combinedRefs::add);
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
        visitDeferred(() -> visitScoped(true, () -> scan(lambda.body)));
    }

    @Override
    public void visitAssign(JCAssign tree) {
        VarSymbol sym = (VarSymbol)TreeInfo.symbolFor(tree.lhs);
        scan(tree.lhs);
        refs.discardExprs(depth);
        scan(tree.rhs);
        if (isParamOrVar(sym))
            refs.replaceExprs(depth, ref -> new VarRef(sym, ref));
        else
            refs.discardExprs(depth);         // we don't track fields yet
    }

    @Override
    public void visitIndexed(JCArrayAccess tree) {
        scan(tree.index);
        refs.discardExprs(depth);
        scan(tree.indexed);
        refs.removeExprs(depth)
          .map(ref -> ref.toDirect(tree.type.tsym))
          .flatMap(Optional::stream)
          .forEach(refs::add);
    }

    @Override
    public void visitSelect(JCFieldAccess tree) {

        // Scan the selection target (i.e., the method)
        scan(tree.selected);
        Stream<ExprRef> methodRefs = refs.removeExprs(depth);

        // Explicit 'this' reference? The expression references whatever 'this' references
        Type.ClassType currentClassType = (Type.ClassType)currentMethod.declaringClass.sym.type;
        if (TreeInfo.isExplicitThisReference(types, currentClassType, tree)) {
            refs.find(ThisRef.class)
              .map(ref -> new ExprRef(depth, ref))
              .forEach(refs::add);
            return;
        }

        // Explicit outer 'this' reference? The expression references whatever the outer 'this' references
        if (isExplicitOuterThisReference(types, currentClassType, tree)) {
            refs.find(ThisRef.class)
              .map(ref -> ref.fromOuter(depth))
              .flatMap(Optional::stream)
              .forEach(refs::add);
            return;
        }

        // For regular non-static methods, our expression "value" is the method's target instance
        if (tree.sym.kind == MTH && (tree.sym.flags() & Flags.STATIC) == 0)
            methodRefs.forEach(refs::add);
    }

    @Override
    public void visitReference(JCMemberReference tree) {
        if (tree.type.isErroneous()) {
            //error recovery - ignore erroneous member references
            return ;
        }

        // Scan target expression and extract 'this' references, if any
        scan(tree.expr);

        // Gather receiver references for deferred invocation
        RefSet<ThisRef> receiverRefs = RefSet.newEmpty();
        switch (tree.kind) {
        case UNBOUND:
        case STATIC:
        case TOPLEVEL:
        case ARRAY_CTOR:
            refs.discardExprs(depth);
            return;
        case SUPER:
        case BOUND:
            refs.removeExprs(depth)
              .map(ThisRef::new)
              .forEach(receiverRefs::add);
            break;
        case IMPLICIT_INNER:
            receiverRefsForConstructor(null, tree.expr.type.tsym)
              .forEach(receiverRefs::add);
            break;
        default:
            throw new RuntimeException("non-exhaustive?");
        }

        // Treat method reference just like the equivalent lambda
        visitDeferred(() -> invoke(tree, (MethodSymbol)tree.sym, List.nil(), receiverRefs));
    }

    @Override
    public void visitIdent(JCIdent tree) {

        // Explicit 'this' reference? The expression references whatever 'this' references
        if (tree.name == names._this || tree.name == names._super) {
            refs.find(ThisRef.class)
              .map(ref -> new ExprRef(depth, ref))
              .forEach(refs::add);
            return;
        }

        // Parameter or local variable? The expression references whatever the variable references
        if (isParamOrVar(tree.sym)) {
            VarSymbol sym = (VarSymbol)tree.sym;
            refs.find(VarRef.class, ref -> ref.sym == sym)
              .map(ref -> new ExprRef(depth, ref))
              .forEach(refs::add);
            return;
        }

        // An unqualified, non-static method invocation must reference 'this' or outer 'this'.
        // The expression "value" of a non-static method is a reference to its target instance.
        if (tree.sym.kind == MTH && (tree.sym.flags() & Flags.STATIC) == 0) {
            MethodSymbol sym = (MethodSymbol)tree.sym;

            // Check for implicit 'this' reference
            ClassSymbol methodClassSym = currentMethod.declaringClass.sym;
            if (methodClassSym.isSubClass(sym.owner, types)) {
                refs.find(ThisRef.class)
                  .map(ref -> new ExprRef(depth, ref))
                  .forEach(refs::add);
                return;
            }

            // Check for implicit outer 'this' reference
            if (methodClassSym.isEnclosedBy((ClassSymbol)sym.owner)) {
                refs.find(ThisRef.class)
                  .map(ref -> ref.fromOuter(depth))
                  .flatMap(Optional::stream)
                  .forEach(refs::add);
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
        RefSet<ExprRef> combinedRefs = RefSet.newEmpty();
        scan(tree.truepart);
        refs.removeExprs(depth)
          .forEach(combinedRefs::add);
        scan(tree.falsepart);
        refs.removeExprs(depth)
          .forEach(combinedRefs::add);
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
        RefSet<ExprRef> combinedRefs = RefSet.newEmpty();
        if (tree.elems != null) {
            for (List<JCExpression> elems = tree.elems; elems.nonEmpty(); elems = elems.tail) {
                scan(elems.head);
                refs.removeExprs(depth)
                  .map(ref -> ref.toIndirect(tree.type.tsym))
                  .flatMap(Optional::stream)
                  .forEach(combinedRefs::add);
            }
        }
        combinedRefs.stream()
          .forEach(refs::add);
    }

    @Override
    public void visitTypeCast(JCTypeCast tree) {
        scan(tree.expr);
        refs.replaceExprs(depth, ref -> ref.withType(tree.expr.type.tsym));
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

    private void analyzeConstructor(MethodInfo constructor) {
        Assert.check(targetClass == null);
        Assert.check(currentMethod == null);
        Assert.check(depth == -1);
        Assert.check(refs == null);
        targetClass = constructor.declaringClass;
        currentMethod = constructor;
        try {

            // Add the initial 'this' reference
            refs = RefSet.newEmpty();
            refs.add(new ThisRef(targetClass.sym, EnumSet.of(Indirection.DIRECT)));

            // Analyze constructor
            visitScoped(false, () -> scan(constructor.declaration.body));
        } finally {
            Assert.check(depth == -1);
            currentMethod = null;
            targetClass = null;
            refs = null;
        }
    }

    // Recurse through indirect code that might get executed later, e.g., a lambda.
    // We record the current number of (real) warnings, then recurse into the deferred
    // code (still using the current RefSet) to see if that number increases, i.e., to
    // see if it would leak. Then we discard any new warnings and the lambda's RefSet.
    // Finally, if the deferred code would have leaked, we create an indirect ExprRef
    // because the lambda must be holding a 'this' reference. If not, no leak is possible.
    private <T extends JCTree> void visitDeferred(Runnable deferredCode) {
        int numWarningsPrev = warningList.size();
        RefSet<Ref> refsPrev = refs.clone();
        boolean deferredCodeLeaks;
        try {
            deferredCode.run();
            deferredCodeLeaks = warningList.size() > numWarningsPrev;

            // There can be ExprRef's if the deferred code returns something.
            // Don't let them escape unnoticed.
            deferredCodeLeaks |= refs.discardExprs(depth);
        } finally {
            refs = refsPrev;
            warningList.subList(numWarningsPrev, warningList.size()).clear();
        }
        if (deferredCodeLeaks)
            refs.add(new ExprRef(depth, syms.objectType.tsym, EnumSet.of(Indirection.INDIRECT)));
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
                refs.removeExprs(depth)
                  .map(ref -> new ExprRef(depth - 1, ref))
                  .forEach(refs::add);
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
        refs.discardExprs(depth);
        depth--;
    }

    // Note a possible 'this' reference leak at the specified location
    private void leakAt(JCTree tree) {
        callStack.add(new StackFrame(currentMethod, null, tree));         // include the point of leakage in the stack
        warningList.add(new Warning(targetClass, new ArrayList<>(callStack)));
        callStack.remove(callStack.size() - 1);
    }

    // Does the symbol correspond to a parameter or local variable (not a field)?
    private boolean isParamOrVar(Symbol sym) {
        return sym != null &&
            sym.kind == VAR &&
            (sym.owner.kind == MTH || sym.owner.kind == VAR);
    }

    /** Check if the given tree is an explicit reference to the outer 'this' instance of the
     *  class currently being compiled. This is true if tree is 'Foo.this' where 'Foo' is
     *  the immediately enclosing class of the current class.
     */
    private boolean isExplicitOuterThisReference(Types types, Type.ClassType currentClass, JCFieldAccess select) {
        Type selectedType = types.erasure(select.selected.type);
        if (selectedType.hasTag(CLASS)) {
            ClassSymbol currentClassSym = (ClassSymbol)currentClass.tsym;
            ClassSymbol selectedTypeSym = (ClassSymbol)selectedType.tsym;
            if (select.name == names._this &&
                    currentClassSym.hasOuterInstance() &&
                    currentClassSym.owner.enclClass() == selectedTypeSym)
                return true;
        }
        return false;
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
            Assert.check(currentMethod != null);
            Assert.check(targetClass != null);
            Assert.check(refs != null);
            Assert.check(depth >= 0);
            Assert.check(refs.find(ExprRef.class)
              .allMatch(ref -> allowExpr && ref.depth <= depth));
        } else {
            Assert.check(targetClass == null);
            Assert.check(refs == null);
            Assert.check(depth == -1);
            Assert.check(callStack.isEmpty());
            Assert.check(invocations.isEmpty());
        }
        return true;
    }

// Ref's

    /** Describes how the 'this' we care about is referenced by a {@link Ref} that is being tracked.
     */
    enum Indirection {

        /** The {@link Ref} directly references 'this'. */
        DIRECT,

        /** The {@link Ref} references 'this' via its outer instance. */
        OUTER,

        /** The {@link Ref} references 'this' indirectly somehow through
            at least one level of indirection. */
        INDIRECT;
    }

    /** Represents an object reference that could refer to the 'this' we care about.
     */
    private abstract static class Ref {

        final TypeSymbol tsym;
        final EnumSet<Indirection> indirections;

        Ref(Ref ref) {
            this(ref.tsym, ref.indirections);
        }

        Ref(TypeSymbol tsym, EnumSet<Indirection> indirections) {
            Assert.check(tsym != null);
            Assert.check(indirections != null);
            this.tsym = tsym;
            this.indirections = EnumSet.copyOf(indirections);
        }

        public abstract Ref withType(TypeSymbol tsym);

        @Override
        public int hashCode() {
            return getClass().hashCode()
                ^ tsym.hashCode()
                ^ indirections.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != getClass())
                return false;
            Ref that = (Ref)obj;
            return tsym == that.tsym
              && indirections.equals(that.indirections);
        }

        @Override
        public final String toString() {
            ArrayList<String> properties = new ArrayList<>();
            properties.add("tsym=" + tsym);
            addProperties(properties);
            properties.add(indirections.stream()
              .map(Indirection::name)
              .collect(Collectors.joining(",")));
            return getClass().getSimpleName()
              + "[" + properties.stream().collect(Collectors.joining(",")) + "]";
        }

        protected void addProperties(ArrayList<String> properties) {
        }

        // Return a modified copy of this Ref's Indirections. The modified set must not be empty.
        public EnumSet<Indirection> modifiedIndirections(Consumer<? super EnumSet<Indirection>> modifier) {
            EnumSet<Indirection> newIndirections = EnumSet.copyOf(indirections);
            modifier.accept(newIndirections);
            Assert.check(!newIndirections.isEmpty());
            return newIndirections;
        }

        // Add one level of indirection through an outer instance
        //  - DIRECT references become OUTER
        //  - OUTER references disappear (we don't try to track indirect outer 'this' references)
        //  - INDIRECT references disappear (we don't try to track outer indirect 'this' references)
        public Optional<ThisRef> toOuter(TypeSymbol tsym) {
            return Optional.of(this)
              .filter(ref -> ref.indirections.contains(Indirection.DIRECT))
              .map(ref -> new ThisRef(tsym, ref.modifiedIndirections(indirections -> {
                indirections.remove(Indirection.DIRECT);
                indirections.remove(Indirection.INDIRECT);
                indirections.add(Indirection.OUTER);
              })));
        }
    }

    /** A reference originating from the current 'this' instance.
     */
    private static class ThisRef extends Ref {

        ThisRef(Ref ref) {
            super(ref);
        }

        ThisRef(TypeSymbol tsym, EnumSet<Indirection> indirections) {
            super(tsym, indirections);
        }

        @Override
        public ThisRef withType(TypeSymbol tsym) {
            return new ThisRef(tsym, indirections);
        }

        // Remove one level of indirection through the outer instance
        //  - DIRECT references disappear
        //  - OUTER references become DIRECT
        //  - INDIRECT references disappear
        public Optional<ExprRef> fromOuter(int depth) {
            ClassSymbol outerType = Optional.of(tsym.owner)
              .map(Symbol::enclClass)
              .orElse(null);
            if (outerType == null)
                return Optional.empty();        // weird
            return Optional.of(this)
              .filter(ref -> ref.indirections.contains(Indirection.OUTER))
              .map(ref -> new ExprRef(depth, outerType, ref.modifiedIndirections(indirections -> {
                indirections.remove(Indirection.OUTER);
                indirections.remove(Indirection.INDIRECT);
                indirections.add(Indirection.DIRECT);
              })));
        }
    }

    /** A reference originating from the expression that was just evaluated.
     *  In other words, a reference that's sitting on top of the stack.
     */
    private static class ExprRef extends Ref {

        final int depth;

        ExprRef(int depth, Ref ref) {
            super(ref);
            this.depth = depth;
        }

        ExprRef(int depth, TypeSymbol tsym, EnumSet<Indirection> indirections) {
            super(tsym, indirections);
            this.depth = depth;
        }

        @Override
        public ExprRef withType(TypeSymbol tsym) {
            return new ExprRef(depth, tsym, indirections);
        }

        // Add one level of indirection
        //  - DIRECT references convert to INDIRECT
        //  - OUTER references disappear (we don't try to track indirect outer 'this' references)
        //  - INDIRECT references stay INDIRECT
        public Optional<ExprRef> toIndirect(TypeSymbol indirectType) {
            return Optional.of(this)
              .filter(ref -> ref.indirections.contains(Indirection.DIRECT) ||
                             ref.indirections.contains(Indirection.INDIRECT))
              .map(ref -> new ExprRef(depth, indirectType, ref.modifiedIndirections(indirections -> {
                indirections.remove(Indirection.DIRECT);
                indirections.remove(Indirection.OUTER);
                indirections.add(Indirection.INDIRECT);
              })));
        }

        // Remove one level of indirection
        //  - DIRECT references disappear
        //  - OUTER references disappear
        //  - INDIRECT references become both DIRECT and INDIRECT
        public Optional<ExprRef> toDirect(TypeSymbol directType) {
            return Optional.of(this)
              .filter(ref -> ref.indirections.contains(Indirection.INDIRECT))
              .map(ref -> new ExprRef(depth, directType, ref.modifiedIndirections(indirections -> {
                indirections.remove(Indirection.OUTER);
                indirections.add(Indirection.DIRECT);
              })));
        }

        @Override
        public int hashCode() {
            return super.hashCode()
                ^ Integer.hashCode(depth);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            ExprRef that = (ExprRef)obj;
            return depth == that.depth;
        }

        @Override
        protected void addProperties(ArrayList<String> properties) {
            super.addProperties(properties);
            properties.add("depth=" + depth);
        }
    }

    /** A reference from the return value of the current method being "invoked".
     */
    private static class ReturnRef extends Ref {

        ReturnRef(Ref ref) {
            super(ref);
        }

        ReturnRef(TypeSymbol tsym, EnumSet<Indirection> indirections) {
            super(tsym, indirections);
        }

        @Override
        public ReturnRef withType(TypeSymbol tsym) {
            return new ReturnRef(tsym, indirections);
        }
    }

    /** A reference from the yield value of the current switch expression.
     */
    private static class YieldRef extends Ref {

        YieldRef(Ref ref) {
            super(ref);
        }

        YieldRef(TypeSymbol tsym, EnumSet<Indirection> indirections) {
            super(tsym, indirections);
        }

        @Override
        public YieldRef withType(TypeSymbol tsym) {
            return new YieldRef(tsym, indirections);
        }
    }

    /** A reference from a variable.
     */
    private static class VarRef extends Ref {

        final VarSymbol sym;

        VarRef(VarSymbol sym, Ref ref) {
            super(ref);
            this.sym = sym;
        }

        VarRef(VarSymbol sym, TypeSymbol tsym, EnumSet<Indirection> indirections) {
            super(tsym, indirections);
            this.sym = sym;
        }

        @Override
        public VarRef withType(TypeSymbol tsym) {
            return new VarRef(sym, tsym, indirections);
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
     *  All methods that return Stream return a copy to avoid ConcurrentModificationException.
     */
    @SuppressWarnings("serial")
    private static class RefSet<T extends Ref> extends HashSet<T> {

        private RefSet() {
            super(8);
        }

        public static <T extends Ref> RefSet<T> newEmpty() {
            return new RefSet<>();
        }

        /** Find all {@link Ref}'s of the given type.
         */
        public <T extends Ref> Stream<T> find(Class<T> refType) {
            return find(refType, ref -> true);
        }

        /** Find all {@link Ref}'s of the given type and matching the given predicate.
         */
        public <T extends Ref> Stream<T> find(Class<T> refType, Predicate<? super T> filter) {
            return stream()
              .filter(refType::isInstance)
              .map(refType::cast)
              .filter(filter)
              .collect(Collectors.toList())         // avoid ConcurrentModificationException
              .stream();
        }

        /** Find the {@link ExprRef} at the given depth, if any.
         */
        public Stream<ExprRef> findExprs(int depth) {
            return find(ExprRef.class, ref -> ref.depth == depth);
        }

        /** Extract (i.e., find and remove) all {@link Ref}'s of the given type.
         */
        public <T extends Ref> Stream<T> remove(Class<T> refType) {
            return remove(refType, ref -> true);
        }

        /** Extract (i.e., find and remove) all {@link Ref}'s of the given type
         *  and matching the given predicate.
         */
        public <T extends Ref> Stream<T> remove(Class<T> refType, Predicate<? super T> filter) {
            ArrayList<T> list = stream()
              .filter(refType::isInstance)
              .map(refType::cast)
              .filter(filter)
              .collect(Collectors.toCollection(ArrayList::new)); // avoid ConcurrentModificationException
            removeAll(list);
            return list.stream();
        }

        /** Extract (i.e., find and remove) all {@link ExprRef}'s at the given depth.
         */
        public Stream<ExprRef> removeExprs(int depth) {
            return remove(ExprRef.class, ref -> ref.depth == depth);
        }

        /** Discard all {@link ExprRef}'s at the given depth.
         */
        public boolean discardExprs(int depth) {
            return removeIf(ref -> ref instanceof ExprRef exprRef && exprRef.depth == depth);
        }

        /** Replace all {@link ExprRef}'s at the given depth after mapping them somehow.
         */
        public void replaceExprs(int depth, Function<? super ExprRef, ? extends T> mapper) {
            removeExprs(depth)
              .map(mapper)
              .forEach(this::add);
        }

        @Override
        @SuppressWarnings("unchecked")
        public RefSet<T> clone() {
            return (RefSet<T>)super.clone();
        }

        // Return a collector that builds a RefSet
        public static <T extends Ref> Collector<T, ?, RefSet<T>> collector() {
            return Collectors.toCollection(RefSet::new);
        }
    }

// StackFrame

    // Information about one frame on the call stack
    private class StackFrame {

        final MethodInfo method;                    // the method containing the statement
        final JCTree site;                          // the call site within the method
        final JCTree initializer;                   // originating field or initialization block, else null
        final boolean suppressible;                 // whether warning can be suppressed at this frame

        StackFrame(MethodInfo method, JCTree initializer, JCTree site) {
            this.method = method;
            this.initializer = initializer;
            this.site = site;
            this.suppressible = initializer != null || (method.constructor && method.declaringClass == targetClass);
        }

        boolean isSuppressed() {
            return suppressible &&
              suppressed.contains(initializer instanceof JCVariableDecl v ? v.sym : method.declaration.sym);
        }

        int comparePos(StackFrame that) {
            return Integer.compare(this.site.pos().getPreferredPosition(), that.site.pos().getPreferredPosition());
        }

        @Override
        public String toString() {
            return "StackFrame"
              + "[" + method.declaration.sym + "@" + site.pos().getPreferredPosition()
              + (initializer != null ? ",init@" + initializer.pos().getPreferredPosition() : "")
              + "]";
        }
    }

// Warning

    // Information about one warning we have generated
    private class Warning {

        final JCClassDecl declaringClass;           // the class whose instance is leaked
        final ArrayList<StackFrame> stack;          // the call stack where the leak happens
        final JCTree origin;                        // the originating ctor, field, or init block

        Warning(JCClassDecl declaringClass, ArrayList<StackFrame> stack) {
            this.declaringClass = declaringClass;
            this.stack = stack;
            this.origin = stack.stream()
              .map(frame -> frame.initializer)
              .filter(Objects::nonNull)
              .findFirst()
              .orElseGet(() -> stack.get(0).method.declaration);    // default to the initial constructor
        }

        // Used to eliminate redundant warnings. Warning A is redundant with warning B if the call stack of A includes
        // the call stack of B plus additional initial frame(s). For example, if constructor B = Foo(int x) generates a
        // warning, then generating warning for some other constructor A when it invokes this(123) would be redundant.
        boolean isRedundantWith(Warning that) {
            int numExtra = this.stack.size() - that.stack.size();
            return numExtra >= 0 &&
              IntStream.range(0, that.stack.size())
                .allMatch(index -> this.stack.get(numExtra + index).comparePos(that.stack.get(index)) == 0);
        }

        // Order warnings by their stack frames, lexicographically in reverse calling order, which will cause
        // all warnings that are isRedundantWith() some other warning to immediately follow that warning.
        static int sortByStackFrames(Warning warning1, Warning warning2) {
            int index1 = warning1.stack.size();
            int index2 = warning2.stack.size();
            while (true) {
                boolean end1 = --index1 < 0;
                boolean end2 = --index2 < 0;
                if (end1 && end2)
                    return 0;
                if (end1)
                    return -1;
                if (end2)
                    return 1;
                int diff = warning1.stack.get(index1).comparePos(warning2.stack.get(index2));
                if (diff != 0)
                    return diff;
            }
        }

        // Determine whether this warning is suppressed. A single "this-escape" warning involves multiple source code
        // positions, so we must determine suppression manually. We do this as follows: A warning is suppressed if
        // "this-escape" is disabled at any position in the stack where that stack frame corresponds to a constructor
        // or field initializer in the target class. That means, for example, @SuppressWarnings("this-escape") annotations
        // on regular methods are ignored. Here we work our way back up the call stack from the point of the leak until
        // we encounter a suppressible stack frame.
        boolean isSuppressed() {
            for (int index = stack.size() - 1; index >= 0; index--) {
                if (stack.get(index).isSuppressed())
                    return true;
            }
            return false;
        }

        // If this is a field or initializer warning, trim the initial stack frame(s) up through the super() call
        void trimInitializerFrames() {
            for (int i = 0; i < stack.size(); i++) {
                if (stack.get(i).initializer != null) {
                    stack.subList(0, i + 1).clear();
                    break;
                }
            }
        }

        @Override
        public String toString() {
            return "Warning"
              + "[class=" + declaringClass.sym.flatname
              + ",stack=[\n    " + stack.stream().map(StackFrame::toString).collect(Collectors.joining("\n    ")) + "]"
              + "]";
        }
    }

// MethodInfo

    // Information about a constructor or method in the compilation unit
    private record MethodInfo(
        JCClassDecl declaringClass,     // the class declaring "declaration"
        JCMethodDecl declaration,       // the method or constructor itself
        boolean constructor,            // the method is a constructor
        boolean analyzable,             // it's a constructor that we should analyze
        boolean invokable) {            // it may be safely "invoked" during analysis

        @Override
        public String toString() {
            return "MethodInfo"
              + "[method=" + declaringClass.sym.flatname + "." + declaration.sym
              + ",constructor=" + constructor
              + ",analyzable=" + analyzable
              + ",invokable=" + invokable
              + "]";
        }
    }
}
