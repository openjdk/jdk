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
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/** A class to compute exhaustiveness of set of switch cases.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ExhaustivenessComputer {
    protected static final Context.Key<ExhaustivenessComputer> exhaustivenessKey = new Context.Key<>();

    private final Symtab syms;
    private final Types types;
    private final Check chk;
    private final Infer infer;

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
    }

    public boolean exhausts(JCExpression selector, List<JCCase> cases) {
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

                        //the set of pending permitted subtypes needed to cover clazz:
                        Set<Symbol> pendingPermitted = new HashSet<>(permitted);

                        for (PatternDescription pdOther : patterns) {
                            if (pdOther instanceof BindingPattern bpOther) {
                                //remove all types from pendingPermitted that we can
                                //cover using bpOther:

                                //all types that are permitted subtypes of bpOther's type:
                                pendingPermitted.removeIf(pending -> types.isSubtype(types.erasure(pending.type),
                                                                                     types.erasure(bpOther.type)));

                                if (bpOther.type.tsym.isAbstract()) {
                                    //all types that are in a diamond hierarchy with bpOther's type
                                    //i.e. there's a common subtype of the given type and bpOther's type:
                                    Predicate<Symbol> check =
                                            pending -> permitted.stream()
                                                                .filter(perm -> types.isSubtype(types.erasure(perm.type),
                                                                                                types.erasure(bpOther.type)))
                                                                .filter(perm -> types.isSubtype(types.erasure(perm.type),
                                                                                                types.erasure(pending.type)))
                                                                .findAny()
                                                                .isPresent();

                                    pendingPermitted.removeIf(check);
                                }
                            }
                        }

                        if (pendingPermitted.isEmpty()) {
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
