/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CaseTree.CaseKind;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Kinds.Kind;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.BindingSymbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCBindingPattern;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCThrow;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.RecordComponent;
import com.sun.tools.javac.code.Type;
import static com.sun.tools.javac.code.TypeTag.BOT;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCBlock.PatternMatchingCatch;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCConstantCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCParenthesizedPattern;
import com.sun.tools.javac.tree.JCTree.JCPattern;
import com.sun.tools.javac.tree.JCTree.JCPatternCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCRecordPattern;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.LetExpr;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;

/**
 * This pass translates pattern-matching constructs, such as instanceof <pattern>.
 */
public class TransPatterns extends TreeTranslator {

    protected static final Context.Key<TransPatterns> transPatternsKey = new Context.Key<>();

    public static TransPatterns instance(Context context) {
        TransPatterns instance = context.get(transPatternsKey);
        if (instance == null)
            instance = new TransPatterns(context);
        return instance;
    }

    private final Symtab syms;
    private final Attr attr;
    private final Resolve rs;
    private final Types types;
    private final Operators operators;
    private final Names names;
    private final Target target;
    private final Preview preview;
    private TreeMaker make;
    private Env<AttrContext> env;

    BindingContext bindingContext = new BindingContext() {
        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            return null;
        }

        @Override
        VarSymbol getBindingFor(BindingSymbol varSymbol) {
            return null;
        }

        @Override
        List<JCStatement> bindingVars(int diagPos) {
            return List.nil();
        }

        @Override
        JCStatement decorateStatement(JCStatement stat) {
            return stat;
        }

        @Override
        JCExpression decorateExpression(JCExpression expr) {
            return expr;
        }

        @Override
        BindingContext pop() {
            //do nothing
            return this;
        }

        @Override
        boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
            return false;
        }
    };

    private ClassSymbol currentClass = null;
    private JCClassDecl currentClassTree = null;
    private ListBuffer<JCTree> pendingMethods = null;
    private MethodSymbol currentMethodSym = null;
    private VarSymbol currentValue = null;
    private Map<RecordComponent, MethodSymbol> component2Proxy = null;
    private Set<JCMethodInvocation> deconstructorCalls;
    private int variableIndex = 0;

    protected TransPatterns(Context context) {
        context.put(transPatternsKey, this);
        syms = Symtab.instance(context);
        attr = Attr.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        operators = Operators.instance(context);
        names = Names.instance(context);
        target = Target.instance(context);
        preview = Preview.instance(context);
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        if (tree.pattern instanceof JCPattern pattern) {
            //first, resolve any parenthesized and record patterns:
            pattern = TreeInfo.skipParens(pattern);
            JCExpression extraConditions = null;
            if (pattern instanceof JCRecordPattern recordPattern) {
                UnrolledRecordPattern unrolledRecordPattern = unrollRecordPattern(recordPattern);
                pattern = unrolledRecordPattern.primaryPattern();
                extraConditions = unrolledRecordPattern.newGuard();
            }
            //$pattern is now always a binding pattern, $extraConditions are possibly additional tests
            //implementing to the record pattern
            //
            //E instanceof $patternType $patternName && $extraConditions
            //=>
            //(let $patternType N$temp = E; N$temp instanceof $patternType &&
            //                              (let $patternName = ($patternType) N$temp; true) &&
            //                              $extraConditions)
            Type tempType = tree.expr.type.hasTag(BOT) ?
                    syms.objectType
                    : tree.expr.type;
            VarSymbol prevCurrentValue = currentValue;
            bindingContext = new BasicBindingContext();
            try {
                JCExpression translatedExpr = translate(tree.expr);
                Symbol exprSym = TreeInfo.symbol(translatedExpr);

                if (exprSym != null &&
                    exprSym.kind == Kind.VAR &&
                    exprSym.owner.kind.matches(Kinds.KindSelector.VAL_MTH)) {
                    currentValue = (VarSymbol) exprSym;
                } else {
                    currentValue = new VarSymbol(Flags.FINAL | Flags.SYNTHETIC,
                            names.fromString("patt" + variableIndex++ + target.syntheticNameChar() + "temp"),
                            tempType,
                            currentMethodSym);
                }

                Type principalType = types.erasure(TreeInfo.primaryPatternType((pattern)));
                 JCExpression resultExpression= (JCExpression) this.<JCTree>translate(pattern);
                if (!tree.allowNull || !types.isSubtype(currentValue.type, principalType)) {
                    resultExpression =
                            makeBinary(Tag.AND,
                                       makeTypeTest(make.Ident(currentValue), make.Type(principalType)),
                                       resultExpression);
                }
                if (extraConditions != null) {
                    extraConditions = translate(extraConditions);
                    resultExpression = makeBinary(Tag.AND, resultExpression, extraConditions);
                }
                if (currentValue != exprSym) {
                    resultExpression =
                            make.at(tree.pos).LetExpr(make.VarDef(currentValue, translatedExpr),
                                                      resultExpression).setType(syms.booleanType);
                    ((LetExpr) resultExpression).needsCond = true;
                }
                result = bindingContext.decorateExpression(resultExpression);
            } finally {
                currentValue = prevCurrentValue;
                bindingContext.pop();
            }
        } else {
            super.visitTypeTest(tree);
        }
    }

    @Override
    public void visitBindingPattern(JCBindingPattern tree) {
        //it is assumed the primary type has already been checked:
        BindingSymbol binding = (BindingSymbol) tree.var.sym;
        Type castTargetType = types.erasure(TreeInfo.primaryPatternType(tree));
        VarSymbol bindingVar = bindingContext.bindingDeclared(binding);

        if (bindingVar != null) {
            JCAssign fakeInit = (JCAssign)make.at(TreeInfo.getStartPos(tree)).Assign(
                    make.Ident(bindingVar), convert(make.Ident(currentValue), castTargetType)).setType(bindingVar.erasure(types));
            LetExpr nestedLE = make.LetExpr(List.of(make.Exec(fakeInit)),
                                            make.Literal(true));
            nestedLE.needsCond = true;
            nestedLE.setType(syms.booleanType);
            result = nestedLE;
        } else {
            result = make.Literal(true);
        }
    }

    @Override
    public void visitParenthesizedPattern(JCParenthesizedPattern tree) {
        result = translate(tree.pattern);
    }

    @Override
    public void visitRecordPattern(JCRecordPattern tree) {
        //record patterns should be resolved by the constructs that use them.
        Assert.error();
    }

    private UnrolledRecordPattern unrollRecordPattern(JCRecordPattern recordPattern) {
        //Convert a record pattern in the basic binding pattern and additional conditions
        //implementing the record pattern:
        //$record($nestedPattern1, $nestedPattern2, ...) $r
        //=>
        //$record $r; type-test-of($nestedPattern1) && type-test-of($nestedPattern2) && ... &&
        //            nested-conditions-of($nestedPattern1) && nested-conditions-of($nestedPattern2)
        Type recordType = recordPattern.record.erasure(types);
        BindingSymbol tempBind = new BindingSymbol(Flags.SYNTHETIC,
            names.fromString(target.syntheticNameChar() + "b" + target.syntheticNameChar() + variableIndex++), recordType,
                             currentMethodSym);
        JCVariableDecl recordBindingVar = make.VarDef(tempBind, null);

        VarSymbol recordBinding = recordBindingVar.sym;
        List<? extends RecordComponent> components = recordPattern.record.getRecordComponents();
        List<? extends Type> nestedFullComponentTypes = recordPattern.fullComponentTypes;
        List<? extends JCPattern> nestedPatterns = recordPattern.nested;
        JCExpression firstLevelChecks = null;
        JCExpression secondLevelChecks = null;

        while (components.nonEmpty()) {
            RecordComponent component = components.head;
            Type componentType = types.erasure(nestedFullComponentTypes.head);
            JCPattern nestedPattern = TreeInfo.skipParens(nestedPatterns.head);
            JCBindingPattern nestedBinding;
            boolean allowNull;
            if (nestedPattern instanceof JCRecordPattern nestedRecordPattern) {
                UnrolledRecordPattern nestedDesugared = unrollRecordPattern(nestedRecordPattern);
                JCExpression newGuard = nestedDesugared.newGuard();
                if (newGuard != null) {
                    if (secondLevelChecks == null) {
                        secondLevelChecks = newGuard;
                    } else {
                        secondLevelChecks = mergeConditions(secondLevelChecks, newGuard);
                    }
                }
                nestedBinding = nestedDesugared.primaryPattern();
                allowNull = false;
            } else {
                nestedBinding = (JCBindingPattern) nestedPattern;
                allowNull = true;
            }
            JCMethodInvocation componentAccessor =
                    make.App(make.Select(convert(make.Ident(recordBinding), recordBinding.type), //TODO - cast needed????
                             component.accessor));
            if (deconstructorCalls == null) {
                deconstructorCalls = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            deconstructorCalls.add(componentAccessor);
            JCExpression accessedComponentValue =
                    convert(componentAccessor, componentType);
            JCInstanceOf firstLevelCheck = (JCInstanceOf) make.TypeTest(accessedComponentValue, nestedBinding).setType(syms.booleanType);
            //TODO: verify deep/complex nesting with nulls
            firstLevelCheck.allowNull = allowNull;
            if (firstLevelChecks == null) {
                firstLevelChecks = firstLevelCheck;
            } else {
                firstLevelChecks = mergeConditions(firstLevelChecks, firstLevelCheck);
            }
            components = components.tail;
            nestedFullComponentTypes = nestedFullComponentTypes.tail;
            nestedPatterns = nestedPatterns.tail;
        }

        Assert.check(components.isEmpty() == nestedPatterns.isEmpty());
        JCExpression guard = null;
        if (firstLevelChecks != null) {
            guard = firstLevelChecks;
            if (secondLevelChecks != null) {
                guard = mergeConditions(guard, secondLevelChecks);
            }
        }
        return new UnrolledRecordPattern((JCBindingPattern) make.BindingPattern(recordBindingVar).setType(recordBinding.type), guard);
    }

    record UnrolledRecordPattern(JCBindingPattern primaryPattern, JCExpression newGuard) {}

    @Override
    public void visitSwitch(JCSwitch tree) {
        handleSwitch(tree, tree.selector, tree.cases,
                     tree.hasUnconditionalPattern, tree.patternSwitch);
    }

    @Override
    public void visitSwitchExpression(JCSwitchExpression tree) {
        handleSwitch(tree, tree.selector, tree.cases,
                     tree.hasUnconditionalPattern, tree.patternSwitch);
    }

    private void handleSwitch(JCTree tree,
                              JCExpression selector,
                              List<JCCase> cases,
                              boolean hasUnconditionalPattern,
                              boolean patternSwitch) {
        if (patternSwitch) {
            Type seltype = selector.type.hasTag(BOT)
                    ? syms.objectType
                    : selector.type;
            Assert.check(preview.isEnabled());
            Assert.check(preview.usesPreview(env.toplevel.sourcefile));

            //rewrite pattern matching switches, performed in several steps:
            //1. record patterns are unrolled into a binding pattern and guards using unrollRecordPattern
            //   (guards implement the nested pattern checks)
            //   the switch only has constants and binding patterns as the
            //2. the cases are processed through processCases, that will group cases with the same
            //   binding pattern and similar guards, and will factor out the common binding pattern,
            //   creating nested switches.
            //3. the simplified binding-only switch with guards is then converted to an ordinary switch:
            //switch ($obj) {
            //     case $constant: $stats1$
            //     case $pattern2 when $guard2: $stats2$
            //     case $pattern3, null: $stats3$
            //     case $pattern4: $stats4$
            //}
            //=>
            //int $idx = 0;
            //$RESTART: switch (invokeDynamic typeSwitch($constant, typeof($pattern1), typeof($pattern2), typeof($pattern3))($obj, $idx)) {
            //     case 0:
            //         $stats1$
            //     case 1:
            //         if (!(<desugared $pattern2> && $guard2)) { $idx = 2; continue $RESTART; }
            //         $stats$
            //     case 2, -1:
            //         if (!(<desugared $pattern3>)) { $idx = 3; continue $RESTART; }
            //         $stats$
            //     case 3:
            //         if (!(<desugared $pattern4>)) { $idx = 4; continue $RESTART; }
            //         $stats$
            //}
            //notes:
            //-pattern desugaring performs assignment to the binding variables
            //-the selector is evaluated only once and stored in a temporary variable
            //-typeSwitch bootstrap method can restart matching at specified index. The bootstrap will
            // categorize the input, and return the case index whose type or constant matches the input.
            // The bootstrap does not evaluate guards, which are injected at the beginning of the case's
            // statement list, and if the guard fails, the switch is "continued" and matching is
            // restarted from the next index.
            //-case null is always desugared to case -1, as the typeSwitch bootstrap method will
            // return -1 when the input is null
            //
            //note the selector is evaluated only once and stored in a temporary variable
            ListBuffer<JCCase> newCases = new ListBuffer<>();
            for (List<JCCase> c = cases; c.nonEmpty(); c = c.tail) {
                c.head.labels = c.head.labels.map(l -> {
                    if (l instanceof JCPatternCaseLabel patternLabel) {
                        JCPattern pattern = TreeInfo.skipParens(patternLabel.pat);
                        if (pattern instanceof JCRecordPattern recordPattern) {
                            UnrolledRecordPattern deconstructed = unrollRecordPattern(recordPattern);
                            JCExpression guard = deconstructed.newGuard();
                            if (patternLabel.guard != null) {
                                guard = mergeConditions(guard, patternLabel.guard);
                            }
                            return make.PatternCaseLabel(deconstructed.primaryPattern(), guard);
                        }
                    }
                    return l;
                });
                newCases.add(c.head);
            }
            cases = processCases(tree, newCases.toList());
            ListBuffer<JCStatement> statements = new ListBuffer<>();
            VarSymbol temp = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString("selector" + variableIndex++ + target.syntheticNameChar() + "temp"),
                    seltype,
                    currentMethodSym);
            boolean hasNullCase = cases.stream()
                                       .flatMap(c -> c.labels.stream())
                                       .anyMatch(p -> TreeInfo.isNullCaseLabel(p));

            JCCase lastCase = cases.last();

            selector = translate(selector);
            boolean needsNullCheck = !hasNullCase && !seltype.isPrimitive();
            statements.append(make.at(tree.pos).VarDef(temp, needsNullCheck ? attr.makeNullCheck(selector)
                                                                            : selector));
            VarSymbol index = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString("index" + target.syntheticNameChar() + variableIndex++),
                    syms.intType,
                    currentMethodSym);
            statements.append(make.at(tree.pos).VarDef(index, makeLit(syms.intType, 0)));

            List<Type> staticArgTypes = List.of(syms.methodHandleLookupType,
                                                syms.stringType,
                                                syms.methodTypeType,
                                                types.makeArrayType(new ClassType(syms.classType.getEnclosingType(),
                                                                    List.of(new WildcardType(syms.objectType, BoundKind.UNBOUND,
                                                                                             syms.boundClass)),
                                                                    syms.classType.tsym)));
            LoadableConstant[] staticArgValues =
                    cases.stream()
                         .flatMap(c -> c.labels.stream())
                         .map(l -> toLoadableConstant(l, seltype))
                         .filter(c -> c != null)
                         .toArray(s -> new LoadableConstant[s]);

            boolean enumSelector = seltype.tsym.isEnum();
            Name bootstrapName = enumSelector ? names.enumSwitch : names.typeSwitch;
            MethodSymbol bsm = rs.resolveInternalMethod(tree.pos(), env, syms.switchBootstrapsType,
                    bootstrapName, staticArgTypes, List.nil());

            MethodType indyType = new MethodType(
                    List.of(enumSelector ? seltype : syms.objectType, syms.intType),
                    syms.intType,
                    List.nil(),
                    syms.methodClass
            );
            DynamicMethodSymbol dynSym = new DynamicMethodSymbol(bootstrapName,
                    syms.noSymbol,
                    bsm.asHandle(),
                    indyType,
                    staticArgValues);

            JCFieldAccess qualifier = make.Select(make.QualIdent(bsm.owner), dynSym.name);
            qualifier.sym = dynSym;
            qualifier.type = syms.intType;
            selector = make.Apply(List.nil(),
                                  qualifier,
                                  List.of(make.Ident(temp), make.Ident(index)))
                           .setType(syms.intType);

            int i = 0;
            boolean previousCompletesNormally = false;
            boolean hasDefault = false;

            for (var c : cases) {
                List<JCCaseLabel> clearedPatterns = c.labels;
                boolean hasJoinedNull =
                        c.labels.size() > 1 && c.labels.stream().anyMatch(l -> TreeInfo.isNullCaseLabel(l));
                if (hasJoinedNull) {
                    clearedPatterns = c.labels.stream()
                                              .filter(l -> !TreeInfo.isNullCaseLabel(l))
                                              .collect(List.collector());
                }
                if (clearedPatterns.size() == 1 && clearedPatterns.head.hasTag(Tag.PATTERNCASELABEL) && !previousCompletesNormally) {
                    JCPatternCaseLabel label = (JCPatternCaseLabel) clearedPatterns.head;
                    bindingContext = new BasicBindingContext();
                    VarSymbol prevCurrentValue = currentValue;
                    try {
                        currentValue = temp;
                        JCExpression test = (JCExpression) this.<JCTree>translate(label.pat);
                        if (label.guard != null) {
                            JCExpression guard = translate(label.guard);
                            if (hasJoinedNull) {
                                JCPattern pattern = label.pat;
                                while (pattern instanceof JCParenthesizedPattern parenthesized) {
                                    pattern = parenthesized.pattern;
                                }
                                Assert.check(pattern.hasTag(Tag.BINDINGPATTERN));
                                VarSymbol binding = ((JCBindingPattern) pattern).var.sym;
                                guard = makeBinary(Tag.OR,
                                                   makeBinary(Tag.EQ,
                                                              make.Ident(binding),
                                                              makeNull()),
                                                   guard);
                            }
                            test = makeBinary(Tag.AND, test, guard);
                        }
                        c.stats = translate(c.stats);
                        JCContinue continueSwitch = make.at(clearedPatterns.head.pos()).Continue(null);
                        continueSwitch.target = tree;
                        c.stats = c.stats.prepend(make.If(makeUnary(Tag.NOT, test).setType(syms.booleanType),
                                                           make.Block(0, List.of(make.Exec(make.Assign(make.Ident(index),
                                                                                                       makeLit(syms.intType, i + 1))
                                                                                     .setType(syms.intType)),
                                                                                 continueSwitch)),
                                                           null));
                        c.stats = c.stats.prependList(bindingContext.bindingVars(c.pos));
                    } finally {
                        currentValue = prevCurrentValue;
                        bindingContext.pop();
                    }
                } else {
                    c.stats = translate(c.stats);
                }

                fixupContinue(tree, c, index, i);

                ListBuffer<JCCaseLabel> translatedLabels = new ListBuffer<>();
                for (var p : c.labels) {
                    if (p.hasTag(Tag.DEFAULTCASELABEL)) {
                        translatedLabels.add(p);
                        hasDefault = true;
                    } else if (hasUnconditionalPattern && !hasDefault &&
                               c == lastCase && p.hasTag(Tag.PATTERNCASELABEL)) {
                        //If the switch has unconditional pattern,
                        //the last case will contain it.
                        //Convert the unconditional pattern to default:
                        translatedLabels.add(make.DefaultCaseLabel());
                    } else {
                        int value;
                        if (TreeInfo.isNullCaseLabel(p)) {
                            value = -1;
                        } else {
                            value = i++;
                        }
                        translatedLabels.add(make.ConstantCaseLabel(make.Literal(value)));
                    }
                }
                c.labels = translatedLabels.toList();
                previousCompletesNormally =
                        c.caseKind == CaseTree.CaseKind.STATEMENT &&
                        c.completesNormally;
                appendBreakIfNeeded(tree, c);
            }

            if (tree.hasTag(Tag.SWITCH)) {
                ((JCSwitch) tree).selector = selector;
                ((JCSwitch) tree).cases = cases;
                ((JCSwitch) tree).wasEnumSelector = enumSelector;
                statements.append((JCSwitch) tree);
                result = make.Block(0, statements.toList());
            } else {
                ((JCSwitchExpression) tree).selector = selector;
                ((JCSwitchExpression) tree).cases = cases;
                ((JCSwitchExpression) tree).wasEnumSelector = enumSelector;
                LetExpr r = (LetExpr) make.LetExpr(statements.toList(), (JCSwitchExpression) tree)
                                          .setType(tree.type);

                r.needsCond = true;
                result = r;
            }
            return ;
        }
        if (tree.hasTag(Tag.SWITCH)) {
            super.visitSwitch((JCSwitch) tree);
        } else {
            super.visitSwitchExpression((JCSwitchExpression) tree);
        }
    }
    //where:
        private void fixupContinue(JCTree switchTree, JCCase c, VarSymbol indexVariable, int currentCaseIndex) {
            //inject 'index = currentCaseIndex + 1;` before continue which has the current switch as the target
            new TreeScanner() {
                @Override
                public void visitCase(JCCase c) {
                    if (c.stats.size() == 1 && c.stats.head instanceof JCContinue cont &&
                        cont.target == switchTree) {
                        JCExpressionStatement setIndex =
                                make.Exec(make.Assign(make.Ident(indexVariable),
                                                      makeLit(syms.intType, currentCaseIndex + 1))
                                              .setType(syms.intType));
                        c.stats = c.stats.prepend(setIndex);
                    }
                }
            }.scan(c.stats);
        }

    private void appendBreakIfNeeded(JCTree switchTree, JCCase c) {
        if (c.caseKind == CaseTree.CaseKind.RULE) {
            JCBreak brk = make.at(TreeInfo.endPos(c.stats.last())).Break(null);
            brk.target = switchTree;
            c.stats = c.stats.append(brk);
        }
    }

    JCMethodInvocation makeApply(JCExpression selector, Name name, List<JCExpression> args) {
        MethodSymbol method = rs.resolveInternalMethod(
                currentClassTree.pos(), env,
                selector.type, name,
                TreeInfo.types(args), List.nil());
        JCMethodInvocation tree = make.App( make.Select(selector, method), args)
                                      .setType(types.erasure(method.getReturnType()));
        return tree;
    }

    JCNewClass makeNewClass(Type ctype, List<JCExpression> args) {
        JCNewClass tree = make.NewClass(null,
            null, make.QualIdent(ctype.tsym), args, null);
        tree.constructor = rs.resolveConstructor(
            currentClassTree.pos(), this.env, ctype, TreeInfo.types(args), List.nil());
        tree.type = ctype;
        return tree;
    }

    /**
     * Considering a list of cases, find consecutive cases with the same binding pattern as their label,
     * and type tests with binding patterns as the first element in the guard. These cases are then
     * merged into a single case, and a nested switch is generated from the first element of the guard.
     *
     * For example:
     *
     * OUTER:
     * switch (selector) {
     *     case Box b when b.o() instanceof String s -> {}
     *     case Box b when b.o() instanceof Integer i-> {}
     *     case Box b when b.o() instanceof Number n -> {}
     *     ...
     * }
     * =>
     * OUTER:
     * switch (selector) {
     *     case Box b ->
     *         switch (b.o()) {
     *             case String s -> {}
     *             case Integer i -> {}
     *             case Number n -> {}
     *             default -> continue OUTER; //continue matching on next case of the outer switch
     *         }
     *     ...
     * }
     */
    private List<JCCase> processCases(JCTree currentSwitch, List<JCCase> inputCases) {
        interface AccummulatorResolver {
            public void resolve(VarSymbol commonBinding,
                                JCExpression commonNestedExpression,
                                VarSymbol commonNestedBinding);
        }
        ListBuffer<JCCase> accummulator = new ListBuffer<>();
        ListBuffer<JCCase> result = new ListBuffer<>();
        AccummulatorResolver resolveAccummulator = (commonBinding, commonNestedExpression, commonNestedBinding) -> {
                boolean hasUnconditional = false;
                if (accummulator.size() > 1) {
                    Assert.check(commonBinding != null &&
                                 commonNestedExpression != null &&
                                 commonNestedBinding != null,
                                 () -> "commonBinding: " + commonBinding +
                                       "commonNestedExpression: " + commonNestedExpression +
                                       "commonNestedBinding: " + commonNestedBinding);
                    ListBuffer<JCCase> nestedCases = new ListBuffer<>();

                    for(List<JCCase> accList = accummulator.toList(); accList.nonEmpty(); accList = accList.tail) {
                        var accummulated = accList.head;
                        JCPatternCaseLabel accummulatedFirstLabel =
                                (JCPatternCaseLabel) accummulated.labels.head;
                        JCBindingPattern accummulatedPattern =
                                (JCBindingPattern) accummulatedFirstLabel.pat;
                        VarSymbol accummulatedBinding = accummulatedPattern.var.sym;
                        TreeScanner replaceNested =
                                new ReplaceVar(Map.of(accummulatedBinding, commonBinding));

                        replaceNested.scan(accummulated);
                        JCExpression newGuard;
                        JCInstanceOf instanceofCheck;
                        if (accummulatedFirstLabel.guard instanceof JCBinary binOp) {
                            newGuard = binOp.rhs;
                            instanceofCheck = (JCInstanceOf) binOp.lhs;
                        } else {
                            newGuard = null;
                            instanceofCheck = (JCInstanceOf) accummulatedFirstLabel.guard;
                        }
                        JCBindingPattern binding = (JCBindingPattern) instanceofCheck.pattern;
                        hasUnconditional =
                                instanceofCheck.allowNull &&
                                types.isSubtype(commonNestedExpression.type,
                                                types.boxedTypeOrType(types.erasure(binding.type))) &&
                                accList.tail.isEmpty();
                        List<JCCaseLabel> newLabel;
                        if (hasUnconditional) {
                            newLabel = List.of(make.ConstantCaseLabel(makeNull()),
                                               make.DefaultCaseLabel());
                        } else {
                            newLabel = List.of(make.PatternCaseLabel(binding, newGuard));
                        }
                        appendBreakIfNeeded(currentSwitch, accummulated);
                        nestedCases.add(make.Case(CaseKind.STATEMENT, newLabel, accummulated.stats, null));
                    }
                    if (!hasUnconditional) {
                        JCContinue continueSwitch = make.Continue(null);
                        continueSwitch.target = currentSwitch;
                        nestedCases.add(make.Case(CaseKind.STATEMENT,
                                                  List.of(make.ConstantCaseLabel(makeNull()),
                                                          make.DefaultCaseLabel()),
                                                  List.of(continueSwitch),
                                                  null));
                    }
                    JCSwitch newSwitch = make.Switch(commonNestedExpression, nestedCases.toList());
                    newSwitch.patternSwitch = true;
                    JCPatternCaseLabel leadingTest =
                            (JCPatternCaseLabel) accummulator.first().labels.head;
                    leadingTest.guard = null;
                    result.add(make.Case(CaseKind.STATEMENT,
                                         List.of(leadingTest),
                                         List.of(newSwitch),
                                         null));
                } else {
                    result.addAll(accummulator);
                }
                accummulator.clear();
        };

        VarSymbol commonBinding = null;
        JCExpression commonNestedExpression = null;
        VarSymbol commonNestedBinding = null;

        for (List<JCCase> c = inputCases; c.nonEmpty(); c = c.tail) {
            VarSymbol currentBinding = null;
            JCExpression currentNestedExpression = null;
            VarSymbol currentNestedBinding = null;

            if (c.head.labels.size() == 1 &&
                c.head.labels.head instanceof JCPatternCaseLabel patternLabel) {
                if (patternLabel.guard instanceof JCBinary binOp &&
                    binOp.lhs instanceof JCInstanceOf instanceofCheck &&
                    instanceofCheck.pattern instanceof JCBindingPattern binding) {
                    currentBinding = ((JCBindingPattern) patternLabel.pat).var.sym;
                    currentNestedExpression = instanceofCheck.expr;
                    currentNestedBinding = binding.var.sym;
                } else if (patternLabel.guard instanceof JCInstanceOf instanceofCheck &&
                    instanceofCheck.pattern instanceof JCBindingPattern binding) {
                    currentBinding = ((JCBindingPattern) patternLabel.pat).var.sym;
                    currentNestedExpression = instanceofCheck.expr;
                    currentNestedBinding = binding.var.sym;
                }
            }
            if (commonBinding == null) {
                if (currentBinding != null) {
                    commonBinding = currentBinding;
                    commonNestedExpression = currentNestedExpression;
                    commonNestedBinding = currentNestedBinding;
                    accummulator.add(c.head);
                } else {
                    result.add(c.head);
                }
            } else if (currentBinding != null &&
                       commonBinding.type.tsym == currentBinding.type.tsym &&
                       new TreeDiffer(List.of(commonBinding), List.of(currentBinding))
                               .scan(commonNestedExpression, currentNestedExpression)) {
                accummulator.add(c.head);
            } else {
                resolveAccummulator.resolve(commonBinding, commonNestedExpression, commonNestedBinding);
                if (currentBinding != null) {
                    accummulator.add(c.head);
                } else {
                    result.add(c.head);
                }
                commonBinding = currentBinding;
                commonNestedExpression = currentNestedExpression;
                commonNestedBinding = currentNestedBinding;
            }
        }
        resolveAccummulator.resolve(commonBinding, commonNestedExpression, commonNestedBinding);
        return result.toList();
    }

    private Type principalType(JCTree p) {
        return types.boxedTypeOrType(types.erasure(TreeInfo.primaryPatternType(p)));
    }

    private LoadableConstant toLoadableConstant(JCCaseLabel l, Type selector) {
        if (l.hasTag(Tag.PATTERNCASELABEL)) {
            Type principalType = principalType(((JCPatternCaseLabel) l).pat);
            if (types.isSubtype(selector, principalType)) {
                return (LoadableConstant) selector;
            } else {
                return (LoadableConstant) principalType;
            }
        } else if (l.hasTag(Tag.CONSTANTCASELABEL)&& !TreeInfo.isNullCaseLabel(l)) {
            JCExpression expr = ((JCConstantCaseLabel) l).expr;
            if ((expr.type.tsym.flags_field & Flags.ENUM) != 0) {
                return LoadableConstant.String(((JCIdent) expr).name.toString());
            } else {
                Assert.checkNonNull(expr.type.constValue());

                return switch (expr.type.getTag()) {
                    case BYTE, CHAR,
                         SHORT, INT -> LoadableConstant.Int((Integer) expr.type.constValue());
                    case CLASS -> LoadableConstant.String((String) expr.type.constValue());
                    default -> throw new AssertionError();
                };
            }
        } else {
            return null;
        }
    }

    @Override
    public void visitBinary(JCBinary tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitBinary(tree);
            result = bindingContext.decorateExpression(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitConditional(JCConditional tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitConditional(tree);
            result = bindingContext.decorateExpression(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitIf(JCIf tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitIf(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitForLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        bindingContext = new BasicBindingContext();
        VarSymbol prevCurrentValue = currentValue;
        try {
            if (tree.varOrRecordPattern instanceof JCRecordPattern jcRecordPattern) {
                /**
                 * A statement of the form
                 *
                 * <pre>
                 *     for (<pattern> : coll ) stmt ;
                 * </pre>
                 *
                 * (where coll implements {@code Iterable<R>}) gets translated to
                 *
                 * <pre>{@code
                 *     for (<type-of-coll-item> N$temp : coll) {
                 *     switch (N$temp) {
                 *         case <pattern>: stmt;
                 *         case null: throw new MatchException();
                 *     }
                 * }</pre>
                 *
                 */
                Type selectorType = types.classBound(tree.elementType);

                currentValue = new VarSymbol(Flags.FINAL | Flags.SYNTHETIC,
                        names.fromString("patt" + tree.pos + target.syntheticNameChar() + "temp"),
                        selectorType,
                        currentMethodSym);

                JCStatement newForVariableDeclaration =
                        make.at(tree.pos).VarDef(currentValue, null).setType(selectorType);

                List<JCExpression> nestedNPEParams = List.of(makeNull());
                JCNewClass nestedNPE = makeNewClass(syms.nullPointerExceptionType, nestedNPEParams);

                List<JCExpression> matchExParams = List.of(makeNull(), nestedNPE);
                JCThrow thr = make.Throw(makeNewClass(syms.matchExceptionType, matchExParams));

                JCCase caseNull = make.Case(JCCase.STATEMENT, List.of(make.ConstantCaseLabel(makeNull())), List.of(thr), null);

                JCCase casePattern = make.Case(CaseTree.CaseKind.STATEMENT,
                        List.of(make.PatternCaseLabel(jcRecordPattern, null)),
                        List.of(translate(tree.body)),
                        null);

                JCSwitch switchBody =
                        make.Switch(make.Ident(currentValue).setType(selectorType),
                                List.of(caseNull, casePattern));

                switchBody.patternSwitch = true;

                // re-using the same node to eliminate the need to re-patch targets (break/continue)
                tree.varOrRecordPattern = newForVariableDeclaration.setType(selectorType);
                tree.expr = translate(tree.expr);
                tree.body = translate(switchBody);

                JCTree.JCEnhancedForLoop newForEach = tree;

                result = bindingContext.decorateStatement(newForEach);
            } else {
                super.visitForeachLoop(tree);
                result = bindingContext.decorateStatement(tree);
            }
        } finally {
            currentValue = prevCurrentValue;
            bindingContext.pop();
        }
    }

    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitWhileLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitDoLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        int prevVariableIndex = variableIndex;
        Set<JCMethodInvocation> prevDeconstructorCalls = deconstructorCalls;
        try {
            currentMethodSym = tree.sym;
            variableIndex = 0;
            deconstructorCalls = null;
            super.visitMethodDef(tree);
            preparePatternMatchingCatchIfNeeded(tree.body);
        } finally {
            variableIndex = prevVariableIndex;
            currentMethodSym = prevMethodSym;
            deconstructorCalls = prevDeconstructorCalls;
        }
    }

    @Override
    public void visitIdent(JCIdent tree) {
        VarSymbol bindingVar = null;
        if ((tree.sym.flags() & Flags.MATCH_BINDING) != 0) {
            bindingVar = bindingContext.getBindingFor((BindingSymbol)tree.sym);
        }
        if (bindingVar == null) {
            super.visitIdent(tree);
        } else {
            result = make.at(tree.pos).Ident(bindingVar).setType(bindingVar.erasure(types));
        }
    }

    @Override
    public void visitBlock(JCBlock tree) {
        ListBuffer<JCStatement> statements = new ListBuffer<>();
        bindingContext = new BindingDeclarationFenceBindingContext() {
            boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
                //{
                //    if (E instanceof T N) {
                //        return ;
                //    }
                //    //use of N:
                //}
                //=>
                //{
                //    T N;
                //    if ((let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp))) {
                //        return ;
                //    }
                //    //use of N:
                //}
                hoistedVarMap.put(binding, var.sym);
                statements.append(var);
                return true;
            }
        };
        MethodSymbol oldMethodSym = currentMethodSym;
        int prevVariableIndex = variableIndex;
        try {
            boolean isInit = currentMethodSym == null;
            Set<JCMethodInvocation> prevDeconstructorCalls = deconstructorCalls;
            try {
                if (isInit) {
                    // Block is a static or instance initializer.
                    currentMethodSym =
                        new MethodSymbol(tree.flags | Flags.BLOCK,
                                         names.empty, null,
                                         currentClass);
                    variableIndex = 0;
                    deconstructorCalls = null;
                }

                for (List<JCStatement> l = tree.stats; l.nonEmpty(); l = l.tail) {
                    statements.append(translate(l.head));
                }

                if (isInit) {
                    preparePatternMatchingCatchIfNeeded(tree);
                }
            } finally {
                if (isInit) {
                    deconstructorCalls = prevDeconstructorCalls;
                }
            }

            tree.stats = statements.toList();
            result = tree;
        } finally {
            variableIndex = prevVariableIndex;
            currentMethodSym = oldMethodSym;
            bindingContext.pop();
        }
    }

    @Override
    public void visitLambda(JCLambda tree) {
        BindingContext prevContent = bindingContext;
        int prevVariableIndex = variableIndex;
        try {
            bindingContext = new BindingDeclarationFenceBindingContext();
            variableIndex = 0;
            tree.params = translate(tree.params);
            Set<JCMethodInvocation> prevDeconstructorCalls = deconstructorCalls;
            try {
                deconstructorCalls = null;
                tree.body = translate(tree.body);
                if (deconstructorCalls != null) {
                    if (tree.body instanceof JCExpression value) {
                        tree.body = make.Block(0, List.of(make.Return(value)));
                    }
                    if (tree.body instanceof JCBlock block) {
                        preparePatternMatchingCatchIfNeeded(block);
                    } else {
                        throw Assert.error("Unexpected lambda body type: " + tree.body.getKind());
                    }
                }
            } finally {
                deconstructorCalls = prevDeconstructorCalls;
            }
            result = tree;
        } finally {
            variableIndex = prevVariableIndex;
            bindingContext = prevContent;
        }
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        ClassSymbol prevCurrentClass = currentClass;
        JCClassDecl prevCurrentClassTree = currentClassTree;
        ListBuffer<JCTree> prevPendingMethods = pendingMethods;
        MethodSymbol prevMethodSym = currentMethodSym;
        Map<RecordComponent, MethodSymbol> prevAccessor2Proxy = component2Proxy;
        try {
            currentClass = tree.sym;
            currentClassTree = tree;
            pendingMethods = new ListBuffer<>();
            currentMethodSym = null;
            component2Proxy = new HashMap<>();
            super.visitClassDef(tree);
            tree.defs = tree.defs.prependList(pendingMethods.toList());
        } finally {
            currentClass = prevCurrentClass;
            currentClassTree = prevCurrentClassTree;
            pendingMethods = prevPendingMethods;
            currentMethodSym = prevMethodSym;
            component2Proxy = prevAccessor2Proxy;
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        int prevVariableIndex = variableIndex;
        try {
            tree.mods = translate(tree.mods);
            tree.vartype = translate(tree.vartype);
            if (currentMethodSym == null) {
                // A class or instance field initializer.
                currentMethodSym =
                    new MethodSymbol((tree.mods.flags&Flags.STATIC) | Flags.BLOCK,
                                     names.empty, null,
                                     currentClass);
                variableIndex = 0;
            }
            if (tree.init != null) tree.init = translate(tree.init);
            result = tree;
        } finally {
            variableIndex = prevVariableIndex;
            currentMethodSym = prevMethodSym;
        }
    }

    @Override
    public void visitTry(JCTry tree) {
        tree.resources = translate(tree.resources);
        Set<JCMethodInvocation> prevDeconstructorCalls = deconstructorCalls;
        try {
            deconstructorCalls = null;
            tree.body = translate(tree.body);
            preparePatternMatchingCatchIfNeeded(tree.body);
        } finally {
            deconstructorCalls = prevDeconstructorCalls;
        }
        tree.catchers = translateCatchers(tree.catchers);
        tree.finalizer = translate(tree.finalizer);
        result = tree;
    }

    private void preparePatternMatchingCatchIfNeeded(JCBlock tree) {
        if (deconstructorCalls != null) {
            VarSymbol ctch = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString("catch" + variableIndex++ + target.syntheticNameChar()),
                    syms.throwableType,
                    currentMethodSym);

            JCCatch patternMatchingCatch =
                    make.Catch(make.VarDef(ctch, null),
                               make.Block(0,
                                          List.of(make.Throw(makeNewClass(syms.matchExceptionType,
                                                                          List.of(makeApply(make.Ident(ctch),
                                                                                            names.toString,
                                                                                            List.nil()),
                                                                                  make.Ident(ctch)))))));
            tree.patternMatchingCatch =
                    new PatternMatchingCatch(patternMatchingCatch, deconstructorCalls);
            deconstructorCalls = null;
        }
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            this.make = make;
            this.env = env;
            translate(cdef);
        } finally {
            // note that recursive invocations of this method fail hard
            this.make = null;
            this.env = null;
        }

        return cdef;
    }

    /** Make an instanceof expression.
     *  @param lhs      The expression.
     *  @param type     The type to be tested.
     */

    JCInstanceOf makeTypeTest(JCExpression lhs, JCExpression type) {
        JCInstanceOf tree = make.TypeTest(lhs, type);
        tree.type = syms.booleanType;
        return tree;
    }

    /** Make an attributed binary expression (copied from Lower).
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCBinary makeBinary(JCTree.Tag optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = operators.resolveBinary(tree, optag, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    /** Make an attributed unary expression.
     *  @param optag    The operators tree tag.
     *  @param arg      The operator's argument.
     */
    JCTree.JCUnary makeUnary(JCTree.Tag optag, JCExpression arg) {
        JCTree.JCUnary tree = make.Unary(optag, arg);
        tree.operator = operators.resolveUnary(tree, optag, arg.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    JCExpression convert(JCExpression expr, Type target) {
        if (types.isSubtype(expr.type, target)) {
            //cast not needed
            return expr;
        }
        JCExpression result = make.at(expr.pos()).TypeCast(make.Type(target), expr);
        result.type = target;
        return result;
    }

    JCExpression mergeConditions(JCExpression left, JCExpression right) {
        if (left instanceof JCBinary lastBinary) {
            while (lastBinary.rhs instanceof JCBinary nextBinary) {
                lastBinary = nextBinary;
            }
            lastBinary.rhs = makeBinary(Tag.AND, lastBinary.rhs, right);
            return left;
        } else {
            return makeBinary(Tag.AND, left, right);
        }
    }

    abstract class BindingContext {
        abstract VarSymbol bindingDeclared(BindingSymbol varSymbol);
        abstract VarSymbol getBindingFor(BindingSymbol varSymbol);
        abstract List<JCStatement> bindingVars(int diagPos);
        abstract JCStatement decorateStatement(JCStatement stat);
        abstract JCExpression decorateExpression(JCExpression expr);
        abstract BindingContext pop();
        abstract boolean tryPrepend(BindingSymbol binding, JCVariableDecl var);
    }

    class BasicBindingContext extends BindingContext {
        Map<BindingSymbol, VarSymbol> hoistedVarMap;
        BindingContext parent;

        public BasicBindingContext() {
            this.parent = bindingContext;
            this.hoistedVarMap = new LinkedHashMap<>();
        }

        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            VarSymbol res = parent.bindingDeclared(varSymbol);
            if (res == null) {
                res = new VarSymbol(varSymbol.flags() & ~Flags.MATCH_BINDING, varSymbol.name, varSymbol.type, currentMethodSym);
                res.setTypeAttributes(varSymbol.getRawTypeAttributes());
                hoistedVarMap.put(varSymbol, res);
            }
            return res;
        }

        @Override
        VarSymbol getBindingFor(BindingSymbol varSymbol) {
            VarSymbol res = parent.getBindingFor(varSymbol);
            if (res != null) {
                return res;
            }
            return hoistedVarMap.entrySet().stream()
                    .filter(e -> e.getKey().isAliasFor(varSymbol))
                    .findFirst()
                    .map(e -> e.getValue()).orElse(null);
        }

        @Override
        List<JCStatement> bindingVars(int diagPos) {
            if (hoistedVarMap.isEmpty()) return List.nil();
            ListBuffer<JCStatement> stats = new ListBuffer<>();
            for (Entry<BindingSymbol, VarSymbol> e : hoistedVarMap.entrySet()) {
                JCVariableDecl decl = makeHoistedVarDecl(diagPos, e.getValue());
                if (!e.getKey().isPreserved() ||
                    !parent.tryPrepend(e.getKey(), decl)) {
                    stats.add(decl);
                }
            }
            return stats.toList();
        }

        @Override
        JCStatement decorateStatement(JCStatement stat) {
            //if (E instanceof T N) {
            //     //use N
            //}
            //=>
            //{
            //    T N;
            //    if ((let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp))) {
            //        //use N
            //    }
            //}
            List<JCStatement> stats = bindingVars(stat.pos);
            if (stats.nonEmpty()) {
                stat = make.at(stat.pos).Block(0, stats.append(stat));
            }
            return stat;
        }

        @Override
        JCExpression decorateExpression(JCExpression expr) {
            //E instanceof T N && /*use of N*/
            //=>
            //(let T N; (let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp)) && /*use of N*/)
            for (VarSymbol vsym : hoistedVarMap.values()) {
                expr = make.at(expr.pos).LetExpr(makeHoistedVarDecl(expr.pos, vsym), expr).setType(expr.type);
            }
            return expr;
        }

        @Override
        BindingContext pop() {
            return bindingContext = parent;
        }

        @Override
        boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
            return false;
        }

        private JCVariableDecl makeHoistedVarDecl(int pos, VarSymbol varSymbol) {
            return make.at(pos).VarDef(varSymbol, null);
        }
    }

    private class BindingDeclarationFenceBindingContext extends BasicBindingContext {

        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            return null;
        }

    }

    /** Make an attributed tree representing a literal. This will be an
     *  Ident node in the case of boolean literals, a Literal node in all
     *  other cases.
     *  @param type       The literal's type.
     *  @param value      The literal's value.
     */
    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value).setType(type.constType(value));
    }

    /** Make an attributed tree representing null.
     */
    JCExpression makeNull() {
        return makeLit(syms.botType, null);
    }

    private class ReplaceVar extends TreeScanner {

        private final Map<Symbol, Symbol> fromTo;

        public ReplaceVar(Map<Symbol, Symbol> fromTo) {
            this.fromTo = fromTo;
        }

        @Override
        public void visitIdent(JCIdent tree) {
            tree.sym = fromTo.getOrDefault(tree.sym, tree.sym);
            super.visitIdent(tree);
        }
    }
}
