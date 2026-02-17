/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import com.sun.tools.javac.code.Kinds.Kind;
import com.sun.tools.javac.code.Type.TypeVar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static com.sun.tools.javac.code.Flags.RECORD;

/** A class to compute exhaustiveness of set of switch cases.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ExhaustivenessComputer {
    private static final long DEFAULT_MAX_BASE_CHECKS = 4_000_000;

    //when baseChecks is set to a value different that this, the checks
    //will be counter, and if too many will happen, the process will be stopped
    //when baseChecks is set to this value, there's no counting, and the
    //process will not continue as long as needed
    private static final long NO_BASE_CHECKS_COUNTING = -1;

    protected static final Context.Key<ExhaustivenessComputer> exhaustivenessKey = new Context.Key<>();

    private final Symtab syms;
    private final Types types;
    private final Check chk;
    private final Infer infer;
    private final Map<Pair<Type, Type>, Boolean> isSubtypeCache = new HashMap<>();
    private final long maxBaseChecks;
    private long baseChecks = NO_BASE_CHECKS_COUNTING;

    public static ExhaustivenessComputer instance(Context context) {
        ExhaustivenessComputer instance = context.get(exhaustivenessKey);
        if (instance == null)
            instance = new ExhaustivenessComputer(context);
        return instance;
    }

    @SuppressWarnings("this-escape")
    protected ExhaustivenessComputer(Context context) {
        context.put(exhaustivenessKey, this);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        chk = Check.instance(context);
        infer = Infer.instance(context);
        Options options = Options.instance(context);
        String baseChecks = options.get("exhaustivityMaxBaseChecks");
        long computedMaxBaseChecks = DEFAULT_MAX_BASE_CHECKS;

        if (baseChecks != null) {
            try {
                computedMaxBaseChecks = Long.parseLong(baseChecks);
            } catch (NumberFormatException _) {
                //ignore invalid values and use the default maximum number of checks
            }
        }

        maxBaseChecks = computedMaxBaseChecks;
    }

    public ExhaustivenessResult exhausts(JCExpression selector, List<JCCase> cases) {
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
            return ExhaustivenessResult.ofExhaustive();
        }

        for (Entry<Symbol, Set<Symbol>> e : enum2Constants.entrySet()) {
            if (e.getValue().isEmpty()) {
                patternSet.add(new BindingPattern(e.getKey().type));
            }
        }
        try {
            CoverageResult coveredResult = computeCoverage(selector.type, patternSet, PatternEquivalence.STRICT);
            if (coveredResult.covered()) {
                return ExhaustivenessResult.ofExhaustive();
            }

            Set<PatternDescription> details =
                    this.computeMissingPatternDescriptions(selector.type, coveredResult.incompletePatterns())
                        .stream()
                        .flatMap(pd -> {
                            if (pd instanceof BindingPattern bp && enum2Constants.containsKey(bp.type.tsym)) {
                                Symbol enumType = bp.type.tsym;
                                return enum2Constants.get(enumType).stream().map(c -> new EnumConstantPattern(bp.type, c.name));
                            } else {
                                return Stream.of(pd);
                            }
                        })
                        .collect(Collectors.toSet());

            return ExhaustivenessResult.ofDetails(details);
        } catch (CompletionFailure cf) {
            chk.completionError(selector.pos(), cf);
            return ExhaustivenessResult.ofExhaustive(); //error recovery
        }
    }

    /* Given the set of patterns, runs the reductions of it as long as possible.
     * If the (reduced) set of patterns covers the given selector type, returns
     * covered == true, and incompletePatterns == null.
     * If the (reduced) set of patterns does not cover the given selector type,
     * returns covered == false, and incompletePatterns == the reduced set of patterns.
     */
    private CoverageResult computeCoverage(Type selectorType, Set<PatternDescription> patterns, PatternEquivalence patternEquivalence) {
        Set<PatternDescription> updatedPatterns;
        Set<Set<PatternDescription>> seenPatterns = new HashSet<>();
        boolean useHashes = true;
        boolean repeat = true;
        do {
            updatedPatterns = reduceBindingPatterns(selectorType, patterns);
            updatedPatterns = reduceNestedPatterns(updatedPatterns, useHashes, patternEquivalence);
            updatedPatterns = reduceRecordPatterns(updatedPatterns);
            updatedPatterns = removeCoveredRecordPatterns(updatedPatterns);
            repeat = !updatedPatterns.equals(patterns);
            if (checkCovered(selectorType, patterns)) {
                return new CoverageResult(true, null);
            }
            if (!repeat) {
                //there may be situation like:
                //class B permits S1, S2
                //patterns: R(S1, B), R(S2, S2)
                //this might be joined to R(B, S2), as B could be rewritten to S2
                //but hashing in reduceNestedPatterns will not allow that
                //disable the use of hashing, and use subtyping in
                //reduceNestedPatterns to handle situations like this:
                repeat = useHashes && seenPatterns.add(updatedPatterns);
                useHashes = false;
            } else {
                //if a reduction happened, make sure hashing in reduceNestedPatterns
                //is enabled, as the hashing speeds up the process significantly:
                useHashes = true;
            }
            patterns = updatedPatterns;
        } while (repeat);
        if (checkCovered(selectorType, patterns)) {
            return new CoverageResult(true, null);
        }
        return new CoverageResult(false, patterns);
    }

    private record CoverageResult(boolean covered, Set<PatternDescription> incompletePatterns) {}

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
                        Type clazzType = clazz.type;
                        if (components(selectorType).stream()
                                                    .noneMatch(c -> isSubtypeErasure(clazzType, c))) {
                            continue;
                        }

                        Set<Symbol> permitted = allPermittedSubTypes(clazz, isApplicableSubtypePredicate(selectorType));

                        //the set of pending permitted subtypes needed to cover clazz:
                        Set<Symbol> pendingPermitted = new HashSet<>(permitted);

                        for (PatternDescription pdOther : patterns) {
                            if (pdOther instanceof BindingPattern bpOther) {
                                //remove all types from pendingPermitted that we can
                                //cover using bpOther:

                                //all types that are permitted subtypes of bpOther's type:
                                pendingPermitted.removeIf(pending -> isSubtypeErasure(pending.type,
                                                                                      bpOther.type));

                                if (bpOther.type.tsym.isAbstract()) {
                                    //all types that are in a diamond hierarchy with bpOther's type
                                    //i.e. there's a common subtype of the given type and bpOther's type:
                                    Predicate<Symbol> check =
                                            pending -> permitted.stream()
                                                                .filter(perm -> isSubtypeErasure(perm.type,
                                                                                                 bpOther.type))
                                                                .filter(perm -> isSubtypeErasure(perm.type,
                                                                                                 pending.type))
                                                                .findAny()
                                                                .isPresent();

                                    pendingPermitted.removeIf(check);
                                }
                            }
                        }

                        if (pendingPermitted.isEmpty()) {
                            toAdd.add(new BindingPattern(clazz.type, Set.of()));
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

    private <C extends TypeSymbol> Predicate<C> isApplicableSubtypePredicate(Type targetType) {
        return csym -> {
            Type instantiated = instantiatePatternType(targetType, csym);

            return instantiated != null && types.isCastable(targetType, instantiated);
        };
    }

    private Type instantiatePatternType(Type targetType, TypeSymbol csym) {
        if (csym.type.allparams().isEmpty()) {
            return csym.type;
        } else {
            return infer.instantiatePatternType(targetType, csym);
        }
    }

    private Set<ClassSymbol> leafPermittedSubTypes(TypeSymbol root, Predicate<ClassSymbol> accept) {
        Set<ClassSymbol> permitted = new HashSet<>();
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
                    }
                }
            } else {
                permitted.add(current);
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
                                                         boolean useHashes,
                                                         PatternEquivalence patternEquivalence) {
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

                        for (int nextCandidate = 0; nextCandidate < candidatesArr.length; nextCandidate++) {
                            if (firstCandidate == nextCandidate) {
                                continue;
                            }

                            RecordPattern rpOther = candidatesArr[nextCandidate];

                            if (rpOne.recordType.tsym == rpOther.recordType.tsym &&
                                nestedComponentsEquivalent(rpOne, rpOther, mismatchingCandidate, useHashes, patternEquivalence)) {
                                join.append(rpOther);
                            }
                        }

                        var nestedPatterns = join.stream().map(rp -> rp.nested[mismatchingCandidateFin]).collect(Collectors.toSet());
                        var updatedPatterns = reduceNestedPatterns(nestedPatterns, useHashes, patternEquivalence);

                        updatedPatterns = reduceRecordPatterns(updatedPatterns);
                        updatedPatterns = removeCoveredRecordPatterns(updatedPatterns);
                        updatedPatterns = reduceBindingPatterns(rpOne.fullComponentTypes()[mismatchingCandidateFin], updatedPatterns);

                        if (!nestedPatterns.equals(updatedPatterns)) {
                            if (useHashes) {
                                current.removeAll(join);
                            }

                            generatePatternsWithReplacedNestedPattern(rpOne,
                                                                      mismatchingCandidateFin,
                                                                      updatedPatterns,
                                                                      Set.copyOf(join),
                                                                      current::add);
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

    /* Returns true if all nested components of existing and candidate are
     * equivalent (if useHashes == true), or "substitutable" (if useHashes == false).
     * A candidate pattern is "substitutable" if it is a binding pattern, and:
     * - it's type is a supertype of the existing pattern's type
     * - it was produced by a reduction from a record pattern that is equivalent to
     *   the existing pattern
     * - only if PatternEquivalence is LOOSE and the type is the same of the type
     *   of an existing record pattern (the binding pattern may stand in place of
     *   a record pattern). This is only used to compute the missing patterns that
     *   would make the original pattern set exhaustive.
     *
     * For example, having (with mismatchingCandidate == 0):
     * existing: R(A _, Box(var _)) {}
     * cadidate: R(B _, Box(var _)) {}
     * these are always equivalent; as all nested patterns except of
     * component 0 are exactly equivalent
     *
     * existing: R(A _, SubtypeOfBox _) {}
     * cadidate: R(A _, Box _) {}
     * this is only equivalent when useHashes == false; Box _ could be replaced
     * with a more specific SubtypeOfBox _
     *
     * existing: R(A _, Box(var _)) {}
     * cadidate: R(A _, Box _) {}
     * this is only equivalent when useHashes == false and patternEquivalence == LOOSE;
     * Box _ is accepted in place of the more specific record pattern
     */
    private boolean nestedComponentsEquivalent(RecordPattern existing,
                                               RecordPattern candidate,
                                               int mismatchingCandidate,
                                               boolean useHashes,
                                               PatternEquivalence patternEquivalence) {
        NEXT_NESTED:
        for (int i = 0; i < existing.nested.length; i++) {
            if (i != mismatchingCandidate) {
                if (!existing.nested[i].equals(candidate.nested[i])) {
                    if (useHashes) {
                        return false;
                    }
                    //when not using hashes,
                    //check if rpOne.nested[i] is
                    //a subtype of rpOther.nested[i]:
                    if (!(candidate.nested[i] instanceof BindingPattern nestedCandidate)) {
                        return false;
                    }
                    if (existing.nested[i] instanceof BindingPattern nestedExisting) {
                        if (!isSubtypeErasure(nestedExisting.type, nestedCandidate.type)) {
                            return false;
                        }
                    } else if (existing.nested[i] instanceof RecordPattern nestedExisting) {
                        if (patternEquivalence == PatternEquivalence.LOOSE) {
                            if (!isSubtypeErasure(nestedExisting.recordType(), nestedCandidate.type)) {
                                return false;
                            }
                        } else {
                            java.util.List<PatternDescription> pendingReplacedPatterns =
                                    new ArrayList<>(nestedCandidate.sourcePatterns());

                            while (!pendingReplacedPatterns.isEmpty()) {
                                PatternDescription currentReplaced = pendingReplacedPatterns.removeLast();

                                if (nestedExisting.equals(currentReplaced)) {
                                    //candidate.nested[i] is substitutable for existing.nested[i]
                                    //continue with the next nested pattern:
                                    continue NEXT_NESTED;
                                }

                                pendingReplacedPatterns.addAll(currentReplaced.sourcePatterns());
                            }

                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /*The same as types.isSubtype(types.erasure(t), types.erasure(s)), but cached.
    */
    private boolean isSubtypeErasure(Type t, Type s) {
        Pair<Type, Type> key = Pair.of(t, s);

        return isSubtypeCache.computeIfAbsent(key, _ ->
                types.isSubtype(types.erasure(t), types.erasure(s)));
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
                PatternDescription pd = new BindingPattern(rpOne.recordType, Set.of(pattern));
                return pd;
            } else if (reducedNestedPatterns != null) {
                PatternDescription pd = new RecordPattern(rpOne.recordType, rpOne.fullComponentTypes(), reducedNestedPatterns, Set.of(pattern));
                return pd;
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

    private boolean isBpCovered(Type componentType, PatternDescription newNested) {
        reportCheck();

        if (newNested instanceof BindingPattern bp) {
            Type seltype = types.erasure(componentType);
            Type pattype = types.erasure(bp.type);

            return seltype.isPrimitive() ?
                    types.isUnconditionallyExactTypeBased(seltype, pattype) :
                    (bp.type.isPrimitive() && types.isUnconditionallyExactTypeBased(types.unboxedType(seltype), bp.type)) || types.isSubtype(seltype, pattype);
        }
        return false;
    }

    protected void reportCheck() {
        if (baseChecks != NO_BASE_CHECKS_COUNTING &&
            ++baseChecks > maxBaseChecks) {
            throw new TooManyChecksException(null);
        }
    }

    public sealed interface PatternDescription {
        public Type type();
        public Set<PatternDescription> sourcePatterns();
    }

    public PatternDescription makePatternDescription(Type selectorType, JCPattern pattern) {
        if (pattern instanceof JCBindingPattern binding) {
            Type type = !selectorType.isPrimitive() && types.isSubtype(selectorType, binding.type)
                    ? selectorType : binding.type;
            return new BindingPattern(type);
        } else if (pattern instanceof JCRecordPattern record) {
            Type[] componentTypes;

            if (!record.type.isErroneous()) {
                componentTypes = instantiatedComponentTypes(record.type);
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
    public record BindingPattern(Type type, Set<PatternDescription> sourcePatterns) implements PatternDescription {

        public BindingPattern(Type type) {
            this(type, Set.of());
        }

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
    public record RecordPattern(Type recordType, int _hashCode, Type[] fullComponentTypes, PatternDescription[] nested, Set<PatternDescription> sourcePatterns) implements PatternDescription {

        public RecordPattern(Type recordType, Type[] fullComponentTypes, PatternDescription[] nested) {
            this(recordType, fullComponentTypes, nested, Set.of());
        }

        public RecordPattern(Type recordType, Type[] fullComponentTypes, PatternDescription[] nested, Set<PatternDescription> sourcePatterns) {
            this(recordType, hashCode(-1, recordType, nested), fullComponentTypes, nested, sourcePatterns);
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

        @Override
        public Type type() {
            return recordType;
        }
    }

    public record EnumConstantPattern(Type enumType, Name enumConstant) implements PatternDescription {

        @Override
        public Type type() {
            return enumType();
        }

        @Override
        public Set<PatternDescription> sourcePatterns() {
            return Set.of();
        }
        public String toString() {
            return enumType() + "." + enumConstant();
        }
    }

    public record ExhaustivenessResult(boolean exhaustive, Set<PatternDescription> notExhaustiveDetails) {
        public static ExhaustivenessResult ofExhaustive() {
            return new ExhaustivenessResult(true, null);
        }
        public static ExhaustivenessResult ofDetails(Set<PatternDescription> notExhaustiveDetails) {
            return new ExhaustivenessResult(false, notExhaustiveDetails != null ? notExhaustiveDetails : Set.of());
        }
    }

    //computation of missing patterns:
    protected Set<PatternDescription> computeMissingPatternDescriptions(Type selectorType,
                                                                        Set<PatternDescription> incompletePatterns) {
        if (maxBaseChecks == 0) {
            return Set.of();
        }
        try {
            baseChecks = 0;
            PatternDescription defaultPattern = new BindingPattern(selectorType);
            return expandMissingPatternDescriptions(selectorType,
                                                    selectorType,
                                                    defaultPattern,
                                                    incompletePatterns,
                                                    Set.of(defaultPattern));
        } catch (TooManyChecksException ex) {
            return ex.missingPatterns != null ? ex.missingPatterns : Set.of();
        } finally {
            baseChecks = NO_BASE_CHECKS_COUNTING;
        }
    }

    private Set<PatternDescription> expandMissingPatternDescriptions(Type selectorType,
                                                                     Type targetType,
                                                                     PatternDescription toExpand,
                                                                     Set<? extends PatternDescription> basePatterns,
                                                                     Set<PatternDescription> inMissingPatterns) {
        try {
            return doExpandMissingPatternDescriptions(selectorType, targetType,
                                                      toExpand, basePatterns,
                                                      inMissingPatterns);
        } catch (TooManyChecksException ex) {
            if (ex.missingPatterns == null) {
                ex = new TooManyChecksException(inMissingPatterns);
            }
            throw ex;
        }
    }

    private Set<PatternDescription> doExpandMissingPatternDescriptions(Type selectorType,
                                                                       Type targetType,
                                                                       PatternDescription toExpand,
                                                                       Set<? extends PatternDescription> basePatterns,
                                                                       Set<PatternDescription> inMissingPatterns) {
        if (toExpand instanceof BindingPattern bp) {
            if (bp.type.tsym.isSealed()) {
                //try to replace binding patterns for sealed types with all their immediate permitted applicable types:
                List<Type> permitted = ((ClassSymbol) bp.type.tsym).getPermittedSubclasses();
                Set<PatternDescription> applicableDirectPermittedPatterns =
                        permitted.stream()
                                 .map(type -> type.tsym)
                                 .filter(isApplicableSubtypePredicate(targetType))
                                 .map(csym -> new BindingPattern(types.erasure(csym.type)))
                                 .collect(Collectors.toCollection(LinkedHashSet::new));

                //remove the permitted subtypes that are not needed to achieve exhaustivity
                boolean reduced =
                        removeUnnecessaryPatterns(selectorType, bp, basePatterns, inMissingPatterns, applicableDirectPermittedPatterns);

                if (!reduced && !hasMatchingRecordPattern(basePatterns, inMissingPatterns, toExpand)) {
                    //if all immediate permitted subtypes are needed
                    //give up, and simply use the current pattern:
                    return inMissingPatterns;
                }

                Set<PatternDescription> currentMissingPatterns =
                        replace(inMissingPatterns, toExpand, applicableDirectPermittedPatterns);

                //try to recursively expand on each viable pattern:
                for (PatternDescription viable : applicableDirectPermittedPatterns) {
                    currentMissingPatterns = expandMissingPatternDescriptions(selectorType, targetType,
                                                                              viable, basePatterns,
                                                                              currentMissingPatterns);
                }

                return currentMissingPatterns;
            } else if ((bp.type.tsym.flags_field & Flags.RECORD) != 0 &&
                       //only expand record types into record patterns if there's a chance it may change the outcome
                       //i.e. there is a record pattern in at the spot in the original base patterns:
                       hasMatchingRecordPattern(basePatterns, inMissingPatterns, toExpand)) {
                //if there is a binding pattern at a place where the original based patterns
                //have a record pattern, try to expand the binding pattern into a record pattern
                //create all possible combinations of record pattern components:
                Type[] componentTypes = instantiatedComponentTypes(bp.type);
                List<List<Type>> combinatorialNestedTypes = List.of(List.nil());

                for (Type componentType : componentTypes) {
                    List<Type> applicableLeafPermittedSubtypes;

                    if (componentType.tsym.isSealed()) {
                        applicableLeafPermittedSubtypes =
                                leafPermittedSubTypes(componentType.tsym,
                                                      isApplicableSubtypePredicate(componentType))
                                    .stream()
                                    .map(csym -> instantiatePatternType(componentType, csym))
                                    .collect(List.collector());
                    } else {
                        applicableLeafPermittedSubtypes = List.of(componentType);
                    }

                    List<List<Type>> newCombinatorialNestedTypes = List.nil();

                    for (List<Type> existing : combinatorialNestedTypes) {
                        for (Type nue : applicableLeafPermittedSubtypes) {
                            newCombinatorialNestedTypes = newCombinatorialNestedTypes.prepend(existing.append(nue));
                        }
                    }

                    combinatorialNestedTypes = newCombinatorialNestedTypes;
                }

                Set<PatternDescription> combinatorialPatterns =
                        combinatorialNestedTypes.stream()
                                                .map(combination -> new RecordPattern(bp.type,
                                                                                      componentTypes,
                                                                                      combination.map(BindingPattern::new)
                                                                                                 .toArray(PatternDescription[]::new)))
                                                .collect(Collectors.toCollection(LinkedHashSet::new));

                removeUnnecessaryPatterns(selectorType, bp, basePatterns, inMissingPatterns, combinatorialPatterns);

                CoverageResult coverageResult = computeCoverage(targetType, combinatorialPatterns, PatternEquivalence.LOOSE);

                if (!coverageResult.covered()) {
                    //use the partially merged/combined patterns:
                    combinatorialPatterns = coverageResult.incompletePatterns();
                }

                //combine sealed subtypes into the supertype, if all is covered.
                //but preserve more specific record types in positions where there are record patterns in the original patterns
                //this is particularly important for the case where the sealed supertype only has one permitted type, the record
                //the base type could be used instead of the record otherwise, which would produce less specific missing pattern:
                Set<PatternDescription> sortedCandidates =
                        partialSortPattern(combinatorialPatterns, basePatterns, replace(inMissingPatterns, toExpand, combinatorialPatterns));

                removeUnnecessaryPatterns(selectorType, bp, basePatterns, inMissingPatterns, sortedCandidates);

                Set<PatternDescription> currentMissingPatterns =
                        replace(inMissingPatterns, toExpand, sortedCandidates);

                for (PatternDescription addedPattern : sortedCandidates) {
                    if (addedPattern instanceof RecordPattern addedRP) {
                        for (int c = 0; c < addedRP.nested.length; c++) {
                            currentMissingPatterns = expandMissingPatternDescriptions(selectorType,
                                                                                      addedRP.fullComponentTypes[c],
                                                                                      addedRP.nested[c],
                                                                                      basePatterns,
                                                                                      currentMissingPatterns);
                        }
                    }
                }

                return currentMissingPatterns;
            }
        }
        return inMissingPatterns;
    }

    /*
     * Inside any pattern in {@code in}, in any nesting depth, replace
     * pattern {@code what} with patterns {@code to}.
     */
    private Set<PatternDescription> replace(Iterable<? extends PatternDescription> in,
                                            PatternDescription what,
                                            Collection<? extends PatternDescription> to) {
        Set<PatternDescription> result = new HashSet<>();

        for (PatternDescription pd : in) {
            Collection<? extends PatternDescription> replaced = replace(pd, what, to);
            if (replaced != null) {
                result.addAll(replaced);
            } else {
                result.add(pd);
            }
        }

        return result;
    }
    //where:
        //null: no change
        private Collection<? extends PatternDescription> replace(PatternDescription in,
                                                                 PatternDescription what,
                                                                 Collection<? extends PatternDescription> to) {
            if (in == what) {
                return to;
            } else if (in instanceof RecordPattern rp) {
                for (int c = 0; c < rp.nested.length; c++) {
                    Collection<? extends PatternDescription> replaced = replace(rp.nested[c], what, to);
                    if (replaced != null) {
                        Set<PatternDescription> withReplaced = new HashSet<>();

                        generatePatternsWithReplacedNestedPattern(rp, c, replaced, Set.of(), withReplaced::add);

                        return replace(withReplaced, what, to);
                    }
                }
                return null;
            } else {
                return null; //binding patterns have no children
            }
        }

    /* Out of "candidates" remove patterns that are not necessary to achieve exhaustiveness.
     * Note that iteration order of "candidates" is important - if the set contains
     * two pattern, out of which either, but not both, is needed to achieve exhaustiveness,
     * the first one in the iteration order will be removed.
     */
    private boolean removeUnnecessaryPatterns(Type selectorType,
                                              PatternDescription toExpand,
                                              Set<? extends PatternDescription> basePatterns,
                                              Set<PatternDescription> inMissingPatterns,
                                              Set<PatternDescription> candidates) {
        boolean reduced = false;

        for (Iterator<PatternDescription> it = candidates.iterator(); it.hasNext(); ) {
            PatternDescription current = it.next();
            Set<PatternDescription> reducedAdded = new HashSet<>(candidates);

            reducedAdded.remove(current);

            Set<PatternDescription> combinedPatterns =
                    Stream.concat(basePatterns.stream(),
                                  replace(inMissingPatterns, toExpand, reducedAdded).stream())
                          .collect(Collectors.toSet());

            if (computeCoverage(selectorType, combinedPatterns, PatternEquivalence.LOOSE).covered()) {
                it.remove();
                reduced = true;
            }
        }

        return reduced;
    }
    /*
     * Sort patterns so that those that are preferred for removal are in front
     * of those that are preferred to remain (when there's a choice).
     */
    private SequencedSet<PatternDescription> partialSortPattern(Set<PatternDescription> candidates,
                                                                Set<? extends PatternDescription> basePatterns,
                                                                Set<PatternDescription> missingPatterns) {
        SequencedSet<PatternDescription> sortedCandidates = new LinkedHashSet<>();

        while (!candidates.isEmpty()) {
            PatternDescription mostSpecific = null;
            for (PatternDescription current : candidates) {
                if (mostSpecific == null ||
                    shouldAppearBefore(current, mostSpecific, basePatterns, missingPatterns)) {
                    mostSpecific = current;
                }
            }
            sortedCandidates.add(mostSpecific);
            candidates.remove(mostSpecific);
        }
        return sortedCandidates;
    }
    //where:
        //true iff pd1 should appear before pd2
        //false otherwise
        private boolean shouldAppearBefore(PatternDescription pd1,
                                           PatternDescription pd2,
                                           Set<? extends PatternDescription> basePatterns,
                                           Set<? extends PatternDescription> missingPatterns) {
            if (pd1 instanceof RecordPattern rp1 && pd2 instanceof RecordPattern rp2) {
                for (int c = 0; c < rp1.nested.length; c++) {
                    if (shouldAppearBefore((BindingPattern) rp1.nested[c],
                                           (BindingPattern) rp2.nested[c],
                                           basePatterns,
                                           missingPatterns)) {
                        return true;
                    }
                }
            } else if (pd1 instanceof BindingPattern bp1 && pd2 instanceof BindingPattern bp2) {
                Type t1 = bp1.type();
                Type t2 = bp2.type();
                boolean t1IsImportantRecord =
                        (t1.tsym.flags_field & RECORD) != 0 &&
                        hasMatchingRecordPattern(basePatterns, missingPatterns, bp1);
                boolean t2IsImportantRecord =
                        (t2.tsym.flags_field & RECORD) != 0 &&
                        hasMatchingRecordPattern(basePatterns, missingPatterns, bp2);
                if (t1IsImportantRecord && !t2IsImportantRecord) {
                    return false;
                }
                if (!t1IsImportantRecord && t2IsImportantRecord) {
                    return true;
                }
                if (!types.isSameType(t1, t2) && types.isSubtype(t1, t2)) {
                    return true;
                }
            }

            return false;
        }

    /*
     * Do the {@code basePatterns} have a record pattern at a place that corresponds to
     * position of pattern {@code query} inside {@code missingPatterns}?
     */
    private boolean hasMatchingRecordPattern(Set<? extends PatternDescription> basePatterns,
                                             Set<? extends PatternDescription> missingPatterns,
                                             PatternDescription query) {
        PatternDescription root = findRootContaining(missingPatterns, query);

        if (root == null) {
            return false;
        }
        return basePatternsHaveRecordPatternOnThisSpot(basePatterns, root, query);
    }
    //where:
        private PatternDescription findRootContaining(Set<? extends PatternDescription> rootPatterns,
                                                      PatternDescription added) {
            for (PatternDescription pd : rootPatterns) {
                if (isUnderRoot(pd, added)) {
                    return pd;
                }
            }

            return null;
        }

        private boolean basePatternsHaveRecordPatternOnThisSpot(Set<? extends PatternDescription> basePatterns,
                                                                PatternDescription rootPattern,
                                                                PatternDescription added) {
            if (rootPattern == added) {
                return basePatterns.stream().anyMatch(pd -> pd instanceof RecordPattern);
            }
            if (!(rootPattern instanceof RecordPattern rootPatternRecord)) {
                return false;
            }
            int index = -1;
            for (int c = 0; c < rootPatternRecord.nested.length; c++) {
                if (isUnderRoot(rootPatternRecord.nested[c], added)) {
                    index = c;
                    break;
                }
            }
            // 'index' must be one of rootPatternRecord.nested; if not, `isUnderRoot` is inconsistent.
            Assert.check(index != (-1));

            int indexFin = index;
            Set<PatternDescription> filteredBasePatterns =
                    basePatterns.stream()
                                .filter(pd -> pd instanceof RecordPattern)
                                .map(rp -> (RecordPattern) rp)
                                .filter(rp -> types.isSameType(rp.recordType(), rootPatternRecord.recordType()))
                                .map(rp -> rp.nested[indexFin])
                                .collect(Collectors.toSet());

            return basePatternsHaveRecordPatternOnThisSpot(filteredBasePatterns, rootPatternRecord.nested[index], added);
        }

        private boolean isUnderRoot(PatternDescription root, PatternDescription searchFor) {
            if (root == searchFor) {
                return true;
            } else if (root instanceof RecordPattern rp) {
                for (int c = 0; c < rp.nested.length; c++) {
                    if (isUnderRoot(rp.nested[c], searchFor)) {
                        return true;
                    }
                }
            }
            return false;
        }

    /*
     * Using {@code basePattern} as a starting point, generate new {@code
     * RecordPattern}s, such that all corresponding components but one, are the
     * same. The component described by the {@code replaceComponent} index is
     * replaced with all {@code PatternDescription}s taken from {@code
     * updatedNestedPatterns} and the resulting {@code RecordPatterns}s are sent
     * to {@code target}.
     */
    private void generatePatternsWithReplacedNestedPattern(RecordPattern basePattern,
                                                           int replaceComponent,
                                                           Iterable<? extends PatternDescription> updatedNestedPatterns,
                                                           Set<PatternDescription> sourcePatterns,
                                                           Consumer<RecordPattern> target) {
        for (PatternDescription nested : updatedNestedPatterns) {
            PatternDescription[] newNested =
                    Arrays.copyOf(basePattern.nested, basePattern.nested.length);
            newNested[replaceComponent] = nested;
            target.accept(new RecordPattern(basePattern.recordType(),
                                            basePattern.fullComponentTypes(),
                                            newNested,
                                            sourcePatterns));
        }
    }

    /* For a given record type, return the record's component types, with their
     * types instatiated according to the exact record type.
     */
    private Type[] instantiatedComponentTypes(Type recordType) {
        Type[] componentTypes = ((ClassSymbol) recordType.tsym).getRecordComponents()
                .map(r -> types.memberType(recordType, r))
                .toArray(s -> new Type[s]);
        return componentTypes;
    }

    /* The strictness of determining the equivalent of patterns, used in
     * nestedComponentsEquivalent.
     */
    private enum PatternEquivalence {
        STRICT,
        LOOSE;
    }

    protected static class TooManyChecksException extends RuntimeException {
        private static final long serialVersionUID = 0L;
        private transient final Set<PatternDescription> missingPatterns;

        public TooManyChecksException(Set<PatternDescription> missingPatterns) {
            super(null, null, false, false);
            this.missingPatterns = missingPatterns;
        }
    }
}
