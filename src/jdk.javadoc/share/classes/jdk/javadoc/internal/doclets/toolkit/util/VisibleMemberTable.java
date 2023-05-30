/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.PropertyUtils;

import static javax.lang.model.element.ElementKind.ANNOTATION_TYPE;

/**
 * This class computes the main data structure for the doclet's
 * operations. Essentially, the implementation encapsulating the
 * javax.lang.model's view of what can be documented about a
 * type element's members.
 * <p>
 * The general operations are as follows:
 * <p>
 * Members: these are the members from j.l.m's view but
 * are structured along the kinds of this class.
 * <p>
 * Extra Members: these are members enclosed in an undocumented
 * package-private type element, and may not be linkable (or documented),
 * however, the members of such a type element may be documented, as if
 * declared in the subtype, only if the enclosing type is not being
 * documented by a filter such as -public, -protected, etc.
 * <p>
 * Visible Members: these are the members that are "visible"
 * and available and should be documented, in a type element.
 * <p>
 * The basic rule for computation: when considering a type element,
 * besides its immediate direct types and interfaces, the computation
 * should not expand to any other type in the inheritance hierarchy.
 * <p>
 * This table generates all the data structures it needs for each
 * type, as its own view, and will present some form of this to the
 * doclet as and when required to.
 */
public class VisibleMemberTable {

    public enum Kind {
        NESTED_CLASSES,
        ENUM_CONSTANTS,
        FIELDS,
        CONSTRUCTORS,
        METHODS,
        ANNOTATION_TYPE_MEMBER,
        ANNOTATION_TYPE_MEMBER_REQUIRED,
        ANNOTATION_TYPE_MEMBER_OPTIONAL,
        PROPERTIES;

        private static final EnumSet<Kind> defaultSummarySet = EnumSet.of(
                NESTED_CLASSES, FIELDS, CONSTRUCTORS, METHODS);
        private static final EnumSet<Kind> enumSummarySet = EnumSet.of(
                NESTED_CLASSES, ENUM_CONSTANTS, FIELDS, METHODS);
        private static final EnumSet<Kind> annotationSummarySet = EnumSet.of(
                FIELDS, ANNOTATION_TYPE_MEMBER_REQUIRED, ANNOTATION_TYPE_MEMBER_OPTIONAL);
        private static final EnumSet<Kind> defaultDetailSet = EnumSet.of(
                FIELDS, CONSTRUCTORS, METHODS);
        private static final EnumSet<Kind> enumDetailSet = EnumSet.of(
                ENUM_CONSTANTS, FIELDS, METHODS);
        private static final EnumSet<Kind> annotationDetailSet = EnumSet.of(
                FIELDS, ANNOTATION_TYPE_MEMBER);

        /**
         * {@return the set of possible member kinds for the summaries section of a type element}
         * @param kind the kind of type element being documented
         */
        public static Set<Kind> forSummariesOf(ElementKind kind) {
            return switch (kind) {
                case ANNOTATION_TYPE -> annotationSummarySet;
                case ENUM -> enumSummarySet;
                default -> defaultSummarySet;
            };
        }

        /**
         * {@return the set of possible member kinds for the details section of a type element}
         * @param kind the kind of type element being documented
         */
        public static Set<Kind> forDetailsOf(ElementKind kind) {
            return switch (kind) {
                case ANNOTATION_TYPE -> annotationDetailSet;
                case ENUM -> enumDetailSet;
                default -> defaultDetailSet;
            };
        }
    }

    /** The class or interface described by this table. */
    private final TypeElement te;
    /**
     * The superclass of {@link #te} or null if {@code te} is an
     * interface or {@code java.lang.Object}.
     */
    private final TypeElement parent;

    private final BaseConfiguration config;
    private final BaseOptions options;
    private final Utils utils;
    private final VisibleMemberCache mcache;

    /**
     * Tables for direct and indirect superclasses.
     *
     * Tables for superclasses must be unique: no class can appear multiple
     * times in the inheritance hierarchy for some other class.
     */
    private final Set<VisibleMemberTable> allSuperclasses;
    /**
     * Tables for direct and indirect superinterfaces.
     *
     * Tables for superinterfaces might not be unique (i.e. an interface
     * may be added from different lineages).
     */
    private final List<VisibleMemberTable> allSuperinterfaces;
    /**
     * Tables for direct superclass and direct superinterfaces.
     *
     * The position of a table for the superclass in the list is unspecified.
     */
    private final Set<VisibleMemberTable> parents;

    private Map<Kind, List<Element>> visibleMembers;
    private final Map<ExecutableElement, PropertyMembers> propertyMap = new HashMap<>();

    private final Map<ExecutableElement, OverrideSequence> overriddenMethods = new HashMap<>();
    private Set<ExecutableElement> methodMembers;

    protected VisibleMemberTable(TypeElement typeElement, BaseConfiguration configuration,
                                 VisibleMemberCache mcache) {
        config = configuration;
        utils = configuration.utils;
        JAVA_LANG_OBJECT = utils.elementUtils.getTypeElement("java.lang.Object");
        options = configuration.getOptions();
        te = typeElement;
        parent = (TypeElement) utils.typeUtils.asElement(te.getSuperclass());
        this.mcache = mcache;
        allSuperclasses = new LinkedHashSet<>();
        allSuperinterfaces = new ArrayList<>();
        parents = new LinkedHashSet<>();
    }

    private void ensureInitialized() {
        if (visibleMembers != null)
            return;

        visibleMembers = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            visibleMembers.put(kind, new ArrayList<>());
        }
        computeParents();
        computeVisibleMembers();
    }

    private Set<VisibleMemberTable> getAllSuperclasses() {
        ensureInitialized();
        return allSuperclasses;
    }

    private List<VisibleMemberTable> getAllSuperinterfaces() {
        ensureInitialized();
        return allSuperinterfaces;
    }

    /**
     * Returns a list of all visible enclosed members of a type element,
     * and inherited members.
     * <p>
     * Notes:
     * a. The list may or may not contain simple overridden methods.
     * A simple overridden method is one that overrides a super method
     * with no specification changes as indicated by the existence of a
     * sole {@code {@inheritDoc}} or devoid of any API comments.
     * <p>
     * b.The list may contain (extra) members, inherited by inaccessible
     * supertypes, primarily package private types. These members are
     * required to be documented in the subtype when the supertype is
     * not documented.
     *
     * @param kind the member kind
     * @return a list of all visible members
     */
    public List<Element> getAllVisibleMembers(Kind kind) {
        ensureInitialized();
        return visibleMembers.getOrDefault(kind, List.of());
    }

    /**
     * Returns a list of visible enclosed members of a specified kind,
     * filtered by the specified predicate.
     * @param kind the member kind
     * @param p the predicate used to filter the output
     * @return a list of visible enclosed members
     */
    public List<Element> getVisibleMembers(Kind kind, Predicate<Element> p) {
        ensureInitialized();
        return visibleMembers.getOrDefault(kind, List.of()).stream()
                .filter(p)
                .toList();
    }

    /**
     * Returns a list of all enclosed members including any extra members.
     * Typically called by various builders.
     *
     * @param kind the member kind
     * @return a list of visible enclosed members
     */
    public List<Element> getVisibleMembers(Kind kind) {
        Predicate<Element> declaredAndLeafMembers = e -> {
            TypeElement encl = utils.getEnclosingTypeElement(e);
            return Objects.equals(encl, te) || utils.isUndocumentedEnclosure(encl);
        };
        return getVisibleMembers(kind, declaredAndLeafMembers);
    }

    /**
     * Returns a list of visible enclosed members of given kind,
     * declared in this type element, and does not include
     * any inherited members or extra members.
     *
     * @return a list of visible enclosed members in this type
     */
    public List<Element> getMembers(Kind kind) {
        Predicate<Element> onlyLocallyDeclaredMembers = e -> Objects.equals(utils.getEnclosingTypeElement(e), te);
        return getVisibleMembers(kind, onlyLocallyDeclaredMembers);
    }

    /*
     * Returns an override sequence element that corresponds to the given method.
     *
     * An override sequence is an ordered set of one or more methods, each of
     * which, starting from the second, are overridden by the first, from this
     * class or interface. The order of methods in the sequence is the
     * following order of members discovery in supertypes: depth-first,
     * preferring classes to interfaces.
     *
     * The assumption is that there's a unique override sequence for each
     * method. If a method does not override anything, the sequence trivially
     * consists of just that method.
     *
     * Example 1
     * =========
     *
     *     public class A implements X { }
     *     public class B extends A implements Y { }
     *
     * Example 2
     * =========
     *
     *     public interface X { }
     *     public interface Y extends X { }
     *
     * API
     * ===
     *
     *   - If the method is not a member of this class or interface, an empty
     *     sequence is returned (this saves us from using an optional or
     *     throwing an exception, yet allows to conveniently check if a method
     *     participates in an override sequence).
     *
     *   - The overrider method (that is, the first method) is a part of the
     *     sequence because it's sometimes useful to view all the methods of
     *     the subsequence uniformly. The cursor is positioned at the given
     *     method for the same reason.
     */
    public OverrideSequence overrideAt(ExecutableElement method) {
        if (method.getKind() != ElementKind.METHOD)
            throw new IllegalArgumentException(diagnosticDescriptionOf(method));
        var result = overriddenMethods.get(method);
        if (result != null)
            return result;
        for (ExecutableElement m : getMethodMembers()) {
            // Is `method` a member of this class or interface or is `method`
            // overridden by a member of this class or interface?
            if (method.equals(m) || utils.elementUtils.overrides(m, method, te)) {
                sequence(m); // sequence overrides starting from that member
                return overriddenMethods.get(method);
            }
        }
        return new OverrideSequence();
    }

    /*
     * An element that provides access to its adjacent elements and describes
     * the override.
     *
     * API
     * ===
     *
     *  - OverrideData is not leaked to the client of this class for robustness:
     *    parts of data (e.g. isSimpleOverride) is sensitive to the order of
     *    elements, which if accessed through the element is fixed.
     *
     *  - Both type mirror and executable element are required to accurately
     *    and fully preserve information:
     *
     *     - the _type_ of a class or interface that declares the method is
     *       not available from method.getEnclosingElement()
     *
     *     - javax.lang.model.type.ExecutableType cannot be translated to
     *       ExecutableElement: javax.lang.model.util.Types.asElement()
     *       returns null if passed such a type
     *
     *  - Compared to alternatives, such as general-purpose java.util.ListIterator
     *    or a pair consisting of a list and an index in that list, this class
     *    has some benefits; for example:
     *
     *     - it has methods with domain-specific names (no need to remember the
     *       order in a list)
     *
     *     - it does not have useless methods
     *
     *     - its objects are immutable (moreover, the class behaves as if it
     *       were annotated with @jdk.internal.ValueBased), which allows them,
     *       for example, to be used as keys in maps
     *
     *  - Sadly, cannot call this Override, as it will clash with
     *    java.lang.Override.
     */
    public static final class OverrideSequence {

        private final List<OverrideData> seq;
        private final int idx;

        private OverrideSequence(List<OverrideData> seq, int idx) {
            this.seq = List.copyOf(seq);
            // Unmodifiable lists are random-access: the lists and their
            // subList views implement the RandomAccess interface.
            //
            // If that weren't guaranteed we would need to make a local
            // reference to the respective list element, as sometimes
            // we access it
            assert this.isEmpty() || this.seq instanceof RandomAccess;
            this.idx = Objects.checkIndex(idx, seq.size());
        }

        // Creates an empty sequence
        private OverrideSequence() {
            this.seq = List.of();
            this.idx = 0;
        }

        public boolean isEmpty() {
            return seq.isEmpty();
        }

        public boolean hasMoreSpecific() {
            return idx > 0;
        }

        public OverrideSequence moreSpecific() {
            if (!hasMoreSpecific())
                throw new NoSuchElementException();
            return new OverrideSequence(seq, idx - 1);
        }

        public boolean hasLessSpecific() {
            return idx < seq.size() - 1;
        }

        public OverrideSequence lessSpecific() {
            if (!hasLessSpecific())
                throw new NoSuchElementException();
            return new OverrideSequence(seq, idx + 1);
        }

        /*
         * Elements are streamed starting from this (inclusive) towards the
         * least specific, i.e. "order by specificity desc". Returns an empty
         * stream if called on an empty element.
         */
        public Stream<OverrideSequence> descending() {
            return IntStream.range(idx, seq.size())
                    .mapToObj(i -> new OverrideSequence(seq, i));
            // Outside this class, the same is achievable through:
            //
            // return Stream.iterate(this, OverrideSequence::hasLessSpecific,
            //        OverrideSequence::lessSpecific);
        }

        private OverrideData data() {
            if (isEmpty())
                throw new NoSuchElementException();
            return seq.get(idx);
        }

        public boolean isSimpleOverride() {
            return data().simpleOverride;
        }

        public DeclaredType getEnclosingType() {
            return data().enclosing;
        }

        public ExecutableElement getMethod() {
            return data().method;
        }

        @Override
        public int hashCode() {
            return isEmpty() ? 0 : Objects.hash(idx, seq);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof OverrideSequence that))
                return false;
            if (this.isEmpty()) {
                return that.isEmpty();
            } else {
                return !that.isEmpty() && ( this.idx == that.idx && this.seq.equals(that.seq) );
            }
        }

        /*
         * For debugging purposes.
         */
        @Override
        public String toString() {
            if (seq.isEmpty())
                return "(empty)";
            var res = new StringBuilder();
            // list elements in the order of decreasing specificity:
            // leftmost is the most specific while the rightmost
            // is the least specific
            var iterator = seq.listIterator();
            while (iterator.hasNext()) {
                // mark this element by enclosing it in square brackets
                var thisElement = idx == iterator.nextIndex();
                if (thisElement)
                    res.append("[");
                res.append(toString(iterator.next().method()));
                if (thisElement)
                    res.append("]");
                if (iterator.hasNext()) {
                    res.append("  ");
                }
            }
            return res.toString();
        }

        private String toString(ExecutableElement m) {
            // parameterization can change mid sequence, which might be
            // important to spot: add type parameters to description
            List<? extends TypeParameterElement> tp = m.getTypeParameters();
            // sadly, Collectors.joining adds prefix and suffix regardless of
            // whether or not the stream is empty: work around it by using
            // emptiness pre-check
            var typeParametersDesc = tp.isEmpty() ? "" : tp.stream()
                    .map(p -> p.asType().toString())
                    .collect(Collectors.joining(",", "<", ">"));
            return m.getEnclosingElement().getSimpleName() + "."
                    + typeParametersDesc + m.getSimpleName()
                    + "(" + m.getParameters().stream()
                    .map(p -> p.asType().toString())
                    .collect(Collectors.joining(",")) + ")";
        }
    }

    /*
     * Computes all method members of this class or interface.
     */
    private Set<ExecutableElement> getMethodMembers() {
        if (methodMembers == null) {
            // Elements.getAllMembers has bugs which preclude us from using it.
            // Once they have been fixed, consider using it instead of ad-hoc
            // computation of a set of method members:
            //
            // methodMembers = new LinkedHashSet<>(
            //        ElementFilter.methodsIn(elements().getAllMembers(te)));
            methodMembers = getMethodMembers0(te);
        }
        return methodMembers;
    }

    /*
     * This algorithm of computation of method members is jdk.javadoc's
     * interpretation of "The Java Language Specification, Java SE 20 Edition".
     *
     * Corresponds to 8. Classes, 8.2. Class Members, and 9.2. Interface Members.
     *
     * 8. Classes
     *
     *     The body of a class declares members (fields, methods, classes, and
     *     interfaces)...
     *
     * 8.2. Class Members:
     *
     *     The members of a class are all of the following:
     *
     *       - Members inherited from its direct superclass type (8.1.4),
     *         except in the class Object, which has no direct superclass type
     *
     *       - Members inherited from any direct superinterface types (8.1.5)
     *
     *       - Members declared in the body of the class (8.1.7)
     *
     *      Members of a class that are declared private are not inherited by
     *      subclasses of that class.
     *
     *      Only members of a class that are declared protected or public are
     *      inherited by subclasses declared in a package other than the one
     *      in which the class is declared.
     *
     * 9.2. Interface Members:
     *
     *     The members of an interface are:
     *
     *       - Members declared in the body of the interface declaration (9.1.5).
     *
     *       - Members inherited from any direct superinterface types (9.1.3).
     *
     *       - If an interface has no direct superinterface types, then the
     *         interface implicitly declares a public abstract member method m
     *         with signature s, return type r, and throws clause t
     *         corresponding to each public instance method m with signature s,
     *         return type r, and throws clause t declared in Object (4.3.2),
     *         unless an abstract method with the same signature, same return
     *         type, and a compatible throws clause is explicitly declared by
     *         the interface.
     */
    private Set<ExecutableElement> getMethodMembers0(TypeElement t) {
        var declaredMethods = Collections.unmodifiableList(ElementFilter.methodsIn(t.getEnclosedElements()));
        var result = new ArrayList<ExecutableElement>();
        result.addAll(declaredMethods);
        if (t.getKind().isClass()) {
            var concreteSuperclassMethods = new ArrayList<ExecutableElement>();
            classInheritConcreteMethods(t, declaredMethods, concreteSuperclassMethods, result);
            classInheritAbstractAndDefaultMethods(t, declaredMethods, concreteSuperclassMethods, result);
        } else if (t.getKind().isInterface()) {
            interfaceInheritMethods(t, declaredMethods, result);
        } else {
            throw new AssertionError(diagnosticDescriptionOf(t));
        }
        return new LinkedHashSet<>(result); // return a set, but preserve the order
    }

    /*
     * Corresponds to 8.4.8 Inheritance, Overriding, and Hiding:
     *
     *     A class C inherits from its direct superclass type D all concrete
     *     methods m (both static and instance) for which all of the following
     *     are true:
     *
     *       - m is a member of D.
     *
     *       - m is public, protected, or declared with package access in
     *         the same package as C.
     *
     *       - No method declared in C has a signature that is a subsignature
     *         (8.4.2) of the signature of m as a member of D.
     */
    private void classInheritConcreteMethods(TypeElement t,
                                             Iterable<? extends ExecutableElement> declaredMethods,
                                             Collection<? super ExecutableElement> concreteSuperclassMethods,
                                             Collection<? super ExecutableElement> result) {
        var superclassType = t.getSuperclass();
        if (superclassType.getKind() == TypeKind.NONE) {
            // comment out because it interferes with the IgnoreSourceErrors test:
            // assert JAVA_LANG_OBJECT.equals(t) : diagnosticDescriptionOf(t);
        } else {
            var superclassMethods = mcache.getVisibleMemberTable(asTypeElement(superclassType)).getMethodMembers();
            for (var sm : superclassMethods) {
                var m = sm.getModifiers();
                if (m.contains(Modifier.ABSTRACT))
                    continue;
                if (m.contains(Modifier.PRIVATE))
                    continue;
                if (!m.contains(Modifier.PUBLIC) && !m.contains(Modifier.PROTECTED)
                        && isInTheSamePackage(sm, t))
                    continue;
                if (isSubsignatured(declaredMethods, (DeclaredType) t.asType(), (DeclaredType) superclassType, sm))
                    continue;
                concreteSuperclassMethods.add(sm);
                result.add(sm);
            }
        }
    }

    private final TypeElement JAVA_LANG_OBJECT;

    /*
     * Corresponds to 8.4.8 Inheritance, Overriding, and Hiding:
     *
     *     A class C inherits from its direct superclass type and direct
     *     superinterface types all abstract and default (9.4) methods m
     *     for which all of the following are true:
     *
     *       - m is a member of the direct superclass type or a direct
     *         superinterface type of C, known in either case as D.
     *
     *       - m is public, protected, or declared with package access
     *         in the same package as C.
     *
     *       - No method declared in C has a signature that is a subsignature
     *         (8.4.2) of the signature of m as a member of D.
     *
     *       - No concrete method inherited by C from its direct superclass
     *         type has a signature that is a subsignature of the signature
     *         of m as a member of D.
     *
     *       - There exists no method m' that is a member of the direct
     *         superclass type or a direct superinterface type of C, D'
     *         (m distinct from m', D distinct from D'), such that m'
     *         overrides from the class or interface of D' the declaration
     *         of the method m (8.4.8.1, 9.4.1.1).
     */
    private void classInheritAbstractAndDefaultMethods(TypeElement t,
                                                       Iterable<? extends ExecutableElement> declaredMethods,
                                                       Iterable<? extends ExecutableElement> concreteSuperclassMethods,
                                                       Collection<? super ExecutableElement> inheritedMethods) {
        var directSupertypes = directSupertypes(t.asType());
        for (var s : directSupertypes) {
            var supertypeElement = asTypeElement(s);
            // FIXME: parameterization information is lost:
            var supertypeMethods = mcache.getVisibleMemberTable(supertypeElement).getMethodMembers();
            for (var sm : supertypeMethods) {
                var m = sm.getModifiers();
                if (!m.contains(Modifier.ABSTRACT) && !m.contains(Modifier.DEFAULT))
                    continue;
                if (m.contains(Modifier.PRIVATE))
                    continue;
                if (!m.contains(Modifier.PUBLIC) && !m.contains(Modifier.PROTECTED)
                        && !isInTheSamePackage(sm, t))
                    continue;
                if (isSubsignatured(declaredMethods, (DeclaredType) t.asType(), s, sm))
                    continue;
                if (isSubsignatured(concreteSuperclassMethods, (DeclaredType) t.asType(), s, sm))
                    continue;
                if (isOverridden(sm, directSupertypes))
                    continue;
                // while 8.4.8 mentions such methods explicitly, by now
                // they should have been already filtered out
                assert !(supertypeElement.getKind().isInterface()
                        && (m.contains(Modifier.PRIVATE) || m.contains(Modifier.STATIC)));
                inheritedMethods.add(sm);
            }
        }
    }

    /*
     * Corresponds to 9.4.1 Inheritance and Overriding:
     *
     *     An interface I inherits from its direct superinterface types all
     *     abstract and default methods m for which all of the following
     *     are true:
     *
     *       - m is a member of a direct superinterface type of I, J.
     *
     *       - No method declared in I has a signature that is a subsignature
     *         (8.4.2) of the signature of m as a member of J.
     *
     *       - There exists no method m' that is a member of a direct
     *         superinterface of I, J' (m distinct from m', J distinct from J'),
     *         such that m' overrides from the interface of J' the declaration
     *         of the method m (9.4.1.1).
     *
     *      An interface does not inherit private or static methods from
     *      its superinterfaces.
     */
    private void interfaceInheritMethods(TypeElement i,
                                         Iterable<? extends ExecutableElement> declaredMethods,
                                         Collection<? super ExecutableElement> inheritedMethods) {
        var directSuperinterfaces = i.getInterfaces();
        for (var j : directSuperinterfaces) {
            var superinterfaceMethods = mcache.getVisibleMemberTable(asTypeElement(j)).getMethodMembers();
            for (var m : superinterfaceMethods) {
                var access = m.getModifiers();
                if (!access.contains(Modifier.ABSTRACT) && !access.contains(Modifier.DEFAULT))
                    continue;
                // don't need to check for static because of 8.4.3. Method Modifiers:
                //
                //     It is a compile-time error if a method declaration that
                //     contains the keyword abstract also contains any one of
                //     the keywords private, static, final, native, strictfp,
                //     or synchronized.
                assert !access.contains(Modifier.STATIC) : diagnosticDescriptionOf(m);
                if (access.contains(Modifier.PRIVATE))
                    continue;
                if (isSubsignatured(declaredMethods, (DeclaredType) i.asType(), (DeclaredType) j, m))
                    continue;
                if (isOverridden(m, directSuperinterfaces))
                    continue;
                inheritedMethods.add(m);
            }
        }
        if (!directSuperinterfaces.isEmpty())
            return;
        // comment out because our model does not currently require it:
        // that is, the Object-like methods are not tracked as members of
        // interfaces (getMethodMembers), they are only tracked as overrides
        // (overrideAt):
        // interfaceProvideObjectLikeMethods(declaredMethods, (DeclaredType) i.asType(), inheritedMethods);
    }

    /*
     * Corresponds to 9.2. Interface Members:
     *
     *     If an interface has no direct superinterface types, then the
     *     interface implicitly declares a public abstract member method m with
     *     signature s, return type r, and throws clause t corresponding to
     *     each public instance method m with signature s, return type r, and
     *     throws clause t declared in Object (4.3.2), unless an abstract
     *     method with the same signature, same return type, and a compatible
     *     throws clause is explicitly declared by the interface.
     */
    private void interfaceProvideObjectLikeMethods(Iterable<? extends ExecutableElement> declaredMethods,
                                                   DeclaredType type,
                                                   Collection<? super ExecutableElement> inheritedMethods) {
        // We currently inherit from Object similarly to how javax.lang.model.element.Elements.getAllMembers FIXME reword
        // does it. Later, we might revisit to consider re-declaring abstract
        // methods as per 9.2. Interface Members
        for (var m : mcache.getVisibleMemberTable(JAVA_LANG_OBJECT).getMethodMembers()) {
            var access = m.getModifiers();
            if (access.contains(Modifier.STATIC))
                continue;
            if (!access.contains(Modifier.PUBLIC))
                continue;
            if (isSubsignatured(declaredMethods, type, (DeclaredType) JAVA_LANG_OBJECT.asType(), m))
                continue; // actually, it should be the _same_ signature (not a subsignature)
            inheritedMethods.add(m);
        }
    }

    private boolean isSubsignatured(Iterable<? extends ExecutableElement> candidateSubsignatures,
                                    DeclaredType candidateSubsignaturesType,
                                    DeclaredType s,
                                    ExecutableElement sm) {
        var mAsMemberOfS = (ExecutableType) types().asMemberOf(s, sm);
        for (var c : candidateSubsignatures) {
            if (sm.getSimpleName().equals(c.getSimpleName())
                    && types().isSubsignature((ExecutableType) types().asMemberOf(candidateSubsignaturesType, c), mAsMemberOfS))
                return true;
        }
        return false;
    }

    private boolean isInTheSamePackage(ExecutableElement m, TypeElement t) {
        return !Objects.equals(elements().getPackageOf(m), elements().getPackageOf(t));
    }

    /*
     * This method is equivalent to javax.lang.model.util.Types.directSupertypes(t)
     * except when t is an interface type that has (direct) superinterface types:
     * in that case, the returned collection does not contain java.lang.Object.
     *
     * The behavior of this method is better aligned with 4.10.2. Subtyping among
     * Class and Interface Types:
     *
     *     Given ... interface C, the direct supertypes of the type of C
     *     are all of the following:
     *
     *         ...
     *
     *       - The type Object, if C is an interface with no direct
     *         superinterface types (9.1.3).
     *
     * This method could be revisited once JDK-8299917 has been resolved.
     */
    @SuppressWarnings("unchecked")
    private List<DeclaredType> directSupertypes(TypeMirror t) {
        Collection<? extends TypeMirror> result;
        var e = asTypeElement(t);
        if (e.getKind().isInterface() && !e.getInterfaces().isEmpty()) {
            result = types().directSupertypes(t).stream()
//                    .dropWhile(m -> types().asElement(m).equals(JAVA_LANG_OBJECT))
                    .toList();
//            assert result.stream()
//                    .allMatch(i -> types().asElement(i).getKind().isInterface());
        } else {
            result = types().directSupertypes(t);
        }
        // comment out because it interferes with the IgnoreSourceErrors test:
        // assert result.stream().allMatch(r -> r.getKind() == TypeKind.DECLARED) : diagnosticDescriptionOf(t);
        return List.copyOf((Collection<DeclaredType>) result);
    }

    /*
     * Is m overridden from any of the classes or interfaces (which correspond
     * to provided types)?
     */
    private boolean isOverridden(ExecutableElement m, Iterable<? extends TypeMirror> from) {
        var r1 = isOverridden1(m, from);
        // cross-check the main implementation an alternative one
        assert r1 == isOverridden2(m, from) : diagnosticDescriptionOf(m)
                + "; " + diagnosticDescriptionOf(te);
        return r1;
    }

    private boolean isOverridden1(ExecutableElement m, Iterable<? extends TypeMirror> from) {
        for (var s : from) {
            // piggyback on override sequences in the hope of better performance
            var r = mcache.getVisibleMemberTable(asTypeElement(s))
                    .overrideAt(m).hasMoreSpecific();
            if (r)
                return true;
        }
        return false;
    }

    private boolean isOverridden2(ExecutableElement m, Iterable<? extends TypeMirror> from) {
        for (var s : from)
            for (var m_ : mcache.getVisibleMemberTable(asTypeElement(s)).getMethodMembers())
                if (elements().overrides(m_, m, asTypeElement(s)))
                    return true;
        return false;
    }

    private TypeElement asTypeElement(TypeMirror m) {
        return (TypeElement) types().asElement(m);
    }

    private static String diagnosticDescriptionOf(Element e) {
        if (e == null) // shouldn't NPE if passed null
            return "null";
        return e + ", " + (e instanceof QualifiedNameable q ? q.getQualifiedName() : e.getSimpleName())
                + ", " + e.getKind() + ", " + Objects.toIdentityString(e);
    }

    private Elements elements() { return utils.elementUtils; }

    private Types types() { return utils.typeUtils; }

    private void sequence(ExecutableElement m) {
        var rawSequence = findOverriddenBy(m);
        // Prepend the member from which the exploration started, unconditionally
        rawSequence = rawSequence.prepend(new OverrideData(
                (DeclaredType) m.getEnclosingElement().asType(), m, false /* this flag's value is immaterial */));

//        // impose an order
//        var order = createSupertypeOrderMap(te);
//        var copyList = new ArrayList<>(rawSequence);
//        copyList.sort(Comparator.comparingInt(o -> order.get(o.enclosing.asElement())));
//        rawSequence = com.sun.tools.javac.util.List.from(copyList);

        // Fix the sequence by computing isSimpleOverride for all the sequence
        // members and hashing those members.
        //
        // Start from the least specific member, which by convention is not
        // a simple override (even if it has no documentation), and work our
        // way _backwards_ to the most specific
        // member. Enter each member into a hash map as an entry point for
        // a subsequence starring from that member.
        // FIXME: what if we have initial method enclosed in an undocumented class or interface?
        //  i.e. what if we cannot guarantee that at least one member is not a simple override?
        var fixedSequence = com.sun.tools.javac.util.List.<OverrideData>nil();
        rawSequence = rawSequence.reverse();
        boolean simpleOverride = false;
        while (!rawSequence.isEmpty()) {
            var f = new OverrideData(rawSequence.head.enclosing, rawSequence.head.method(), simpleOverride);
            fixedSequence = fixedSequence.prepend(f);
            rawSequence = rawSequence.tail;
            if (!rawSequence.isEmpty()) {
                // Even with --override-methods=summary we want to include details of
                // overriding method if something noteworthy has been added or changed
                // in the local overriding method
                var enclosing = (TypeElement) rawSequence.head.method.getEnclosingElement();
                simpleOverride = isSimpleOverride(rawSequence.head.method)
                        && !utils.isUndocumentedEnclosure(enclosing)
                        && !overridingSignatureChanged(rawSequence.head.method, f.method);
            }
        }
        // hash sequences as entry points
        var t = new OverrideSequence(fixedSequence, 0);
        while (true) {
            overriddenMethods.put(t.getMethod(), t);
            if (!t.hasLessSpecific()) {
                break;
            } else {
                t = t.lessSpecific();
            }
        }
    }

    private com.sun.tools.javac.util.List<OverrideData> findOverriddenBy(ExecutableElement m) {
        return findOverriddenBy(m, (DeclaredType) te.asType(), com.sun.tools.javac.util.List.nil(), new HashSet<>());
    }

    private com.sun.tools.javac.util.List<OverrideData> findOverriddenBy(ExecutableElement m,
                                                                         DeclaredType declaredType,
                                                                         com.sun.tools.javac.util.List<OverrideData> result,
                                                                         Set<ExecutableElement> foundOverriddenSoFar) {
        for (var s : directSupertypes(declaredType)) {
            var supertypeElement = (TypeElement) s.asElement();
            for (var m1 : mcache.getVisibleMemberTable(supertypeElement).getMethodMembers())
                if (utils.elementUtils.overrides(m, m1, te) && foundOverriddenSoFar.add(m1)) {
                    // use any value for `simpleOverride` for now: the actual value
                    // will be computed once the complete sequence is available
                    var simpleOverride = false;
                    result = result.append(new OverrideData(toDeclaringType(s,
                            (TypeElement) m1.getEnclosingElement()), m1, simpleOverride));
                }
            result = findOverriddenBy(m, s, result, foundOverriddenSoFar);
        }
        return result;
    }

    /*
     * Translates the provided class or interface to a type given its subtype.
     *
     * The correctness relies of no more than 1 parameterization from JLS. (TODO link the correct section)
     *
     * There's probably a better way of doing it using javax.lang.model.util.Types.getDeclaredType(),
     * but for now this will do.
     */
    private DeclaredType toDeclaringType(DeclaredType subtype, TypeElement element) {
        var r = toDeclaringType0(subtype, element);
        if (r == null)
            throw new IllegalArgumentException(); // not a subtype
        return r;
    }

    private DeclaredType toDeclaringType0(DeclaredType subtype, TypeElement element) {
        if (subtype.asElement().equals(element))
            return subtype;
        for (var s : utils.typeUtils.directSupertypes(subtype)) {
            var r = toDeclaringType0((DeclaredType) s, element);
            if (r != null)
                return r;
        }
        return null;
    }

    private record OverrideData(DeclaredType enclosing,
                                ExecutableElement method,
                                boolean simpleOverride) {
        private OverrideData {
            if (!enclosing.asElement().equals(method.getEnclosingElement())) {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Returns a set of visible type elements in this type element's lineage.
     * <p>
     * This method returns the supertypes in the inheritance
     * order C, B, A, j.l.O. The superinterfaces however are
     * alpha sorted and appended to the resulting set.
     *
     * @return the set of visible classes in this map
     */
    public Set<TypeElement> getVisibleTypeElements() {
        ensureInitialized();
        Set<TypeElement> result = new LinkedHashSet<>();

        // Add this type element first.
        result.add(te);

        // Add the superclasses.
        allSuperclasses.stream()
                .map(vmt -> vmt.te)
                .forEach(result::add);

        // ... and finally the sorted superinterfaces.
        allSuperinterfaces.stream()
                .map(vmt -> vmt.te)
                .sorted(utils.comparators.makeGeneralPurposeComparator())
                .forEach(result::add);

        return result;
    }

    /**
     * Returns true if this table contains visible members of
     * any kind, including inherited members.
     *
     * @return true if visible members are present.
     */
    public boolean hasVisibleMembers() {
        for (Kind kind : Kind.values()) {
            if (hasVisibleMembers(kind))
                return true;
        }
        return false;
    }

    /**
     * Returns true if this table contains visible members of
     * the specified kind, including inherited members.
     *
     * @return true if visible members are present.
     */
    public boolean hasVisibleMembers(Kind kind) {
        ensureInitialized();
        List<Element> elements = visibleMembers.get(kind);
        return elements != null && !elements.isEmpty();
    }

    /**
     * Returns the field for a property identified by any of the methods
     * for that property.
     *
     * @param ee the method
     * @return the field or null if absent
     */
    public VariableElement getPropertyField(ExecutableElement ee) {
        ensureInitialized();
        PropertyMembers pm =  propertyMap.get(ee);
        return pm == null ? null : pm.field;
    }

    /**
     * Returns the getter method for a property identified by any of the methods
     * for that property.
     *
     * @param ee the method
     * @return the getter or null if absent
     */
    public ExecutableElement getPropertyGetter(ExecutableElement ee) {
        ensureInitialized();
        PropertyMembers pm =  propertyMap.get(ee);
        return pm == null ? null : pm.getter;
    }

    /**
     * Returns the setter method for a property identified by any of the methods
     * for that property.
     *
     * @param ee the method
     * @return the setter or null if absent
     */
    public ExecutableElement getPropertySetter(ExecutableElement ee) {
        ensureInitialized();
        PropertyMembers pm =  propertyMap.get(ee);
        return pm == null ? null : pm.setter;
    }

    /**
     * Returns the property method for a property identified by any of the methods
     * for that property.
     *
     * @param ee the method
     * @return the property method or null if absent
     */
    public ExecutableElement getPropertyMethod(ExecutableElement ee) {
        ensureInitialized();
        PropertyMembers pm =  propertyMap.get(ee);
        return pm == null ? null : pm.propertyMethod;
    }

    private void computeParents() {
        // suppress parents of annotation interfaces
        if (utils.isAnnotationInterface(te)) {
            return;
        }

        for (TypeMirror intfType : te.getInterfaces()) {
            TypeElement intfc = utils.asTypeElement(intfType);
            if (intfc != null) {
                VisibleMemberTable vmt = mcache.getVisibleMemberTable(intfc);
                allSuperinterfaces.add(vmt);
                boolean added = parents.add(vmt);
                assert added; // no duplicates
                allSuperinterfaces.addAll(vmt.getAllSuperinterfaces());
            }
        }

        if (parent != null) {
            VisibleMemberTable vmt = mcache.getVisibleMemberTable(parent);
            allSuperclasses.add(vmt);
            assert Collections.disjoint(allSuperclasses, vmt.getAllSuperclasses()); // no duplicates
            allSuperclasses.addAll(vmt.getAllSuperclasses());
            // Add direct and indirect superinterfaces of a superclass.
            allSuperinterfaces.addAll(vmt.getAllSuperinterfaces());
            boolean added = parents.add(vmt);
            assert added; // no duplicates
        }
    }

    private void computeVisibleMembers() {

        // Note: these have some baggage, and are redundant,
        // allow this to be GC'ed.
        LocalMemberTable lmt = new LocalMemberTable();

        for (Kind k : Kind.values()) {
            computeVisibleMembers(lmt, k);
        }
        // All members have been computed, compute properties.
        computeVisibleProperties(lmt);
    }


    void computeVisibleMembers(LocalMemberTable lmt, Kind kind) {
        switch (kind) {
            case FIELDS: case NESTED_CLASSES:
                computeVisibleFieldsAndInnerClasses(lmt, kind);
                return;

            case METHODS:
                computeVisibleMethods(lmt);
                return;

            // Defer properties related computations for later.
            case PROPERTIES:
                return;

            default:
                List<Element> list = lmt.getOrderedMembers(kind).stream()
                        .filter(this::mustDocument)
                        .toList();
                visibleMembers.put(kind, list);
                break;
        }
    }

    private boolean mustDocument(Element e) {
        // these checks are ordered in a particular way to avoid parsing unless absolutely necessary
        return utils.shouldDocument(e) && !utils.hasHiddenTag(e);
    }

    private boolean allowInheritedMembers(Element e, Kind kind, LocalMemberTable lmt) {
        return isAccessible(e) && !isMemberHidden(e, kind, lmt);
    }

    private boolean isAccessible(Element e) {
        if (utils.isPrivate(e))
            return false;

        if (utils.isPackagePrivate(e))
            // Allowed iff this type-element is in the same package as the element
            return utils.containingPackage(e).equals(utils.containingPackage(te));

        return true;
    }

    private boolean isMemberHidden(Element inheritedMember, Kind kind, LocalMemberTable lmt) {
        Elements elementUtils = config.docEnv.getElementUtils();
        switch(kind) {
            default:
                List<Element> list = lmt.getMembers(inheritedMember.getSimpleName(), kind);
                if (list.isEmpty())
                    return false;
                return elementUtils.hides(list.get(0), inheritedMember);
            case METHODS: case CONSTRUCTORS: // Handled elsewhere.
                throw new IllegalArgumentException("incorrect kind");
        }
    }

    private void computeVisibleFieldsAndInnerClasses(LocalMemberTable lmt, Kind kind) {
        Set<Element> result = new LinkedHashSet<>();
        for (VisibleMemberTable pvmt : parents) {
            result.addAll(pvmt.getAllVisibleMembers(kind));
        }

        // Filter out members in the inherited list that are hidden
        // by this type or should not be inherited at all.
        Stream<Element> inheritedStream = result.stream()
                .filter(e -> allowInheritedMembers(e, kind, lmt));

        // Filter out elements that should not be documented
        // Prefix local results first
        List<Element> list = Stream.concat(lmt.getOrderedMembers(kind).stream(), inheritedStream)
                                   .filter(this::mustDocument)
                                   .toList();

        visibleMembers.put(kind, list);
    }

    // This method computes data structures related to method members
    // of a class or an interface.
    //
    // TODO The computation is performed manually, by applying JLS rules.
    //  While jdk.javadoc does need custom and specialized data structures,
    //  this method does not feel DRY. It should be possible to improve
    //  it by delegating some, if not most, of the JLS wrestling to
    //  javax.lang.model. For example, while it cannot help us get the
    //  structures, such as overriddenMethodTable, javax.lang.model can
    //  help us get all method members of a class or an interface t by calling
    //  ElementFilter.methodsIn(Elements.getAllMembers(t)).
    private void computeVisibleMethods(LocalMemberTable ignored) {
        // dark tunnel (consisting of undocumented enclosures):
        //   for every candidate method let's find the most specific non-simple
        //   override included
        List<Element> list = getMethodMembers().stream()
                .filter(m -> te.getKind() != ANNOTATION_TYPE) // if te is annotation, ignore its methods altogether
                .filter(m -> !options.noDeprecated() || !utils.isDeprecated(m)) // exclude deprecated if requested
                .flatMap(m -> overrideAt(m).descending() // find the most specific non-simple override
                        .dropWhile(OverrideSequence::isSimpleOverride)
                        .map(OverrideSequence::getMethod)
                        .findFirst()
                        .stream())
                .filter(this::mustDocument)
                .map(m -> (Element) m)
                .toList();

        visibleMembers.put(Kind.METHODS, list);
    }

    /*
     * Returns true if the passed method does not change the specification it
     * inherited.
     *
     * If the passed method is not deprecated and has either no comment or a
     * comment consisting of single {@inheritDoc} tag, the inherited
     * specification is deemed unchanged and this method returns true;
     * otherwise this method returns false.
     */
    private boolean isSimpleOverride(ExecutableElement m) {
        if (!options.summarizeOverriddenMethods() || !utils.isIncluded(m)) {
            return false;
        }

        if (!utils.getBlockTags(m).isEmpty() || utils.isDeprecated(m))
            return false;

        List<? extends DocTree> fullBody = utils.getFullBody(m);
        return fullBody.isEmpty() ||
                (fullBody.size() == 1 && fullBody.get(0).getKind().equals(DocTree.Kind.INHERIT_DOC));
    }

    // fixme: consider comparing type parameters?
    // Check whether the signature of an overriding method has any changes worth
    // being documented compared to the overridden method.
    private boolean overridingSignatureChanged(ExecutableElement method, ExecutableElement overriddenMethod) {
        // Covariant return type
        TypeMirror overriddenMethodReturn = overriddenMethod.getReturnType();
        TypeMirror methodReturn = method.getReturnType();
        if (methodReturn.getKind() == TypeKind.DECLARED
                && overriddenMethodReturn.getKind() == TypeKind.DECLARED
                && !utils.typeUtils.isSameType(methodReturn, overriddenMethodReturn)
                && utils.typeUtils.isSubtype(methodReturn, overriddenMethodReturn)) {
            return true;
        }
        // TODO: should we consider changes to synchronized as a significant change
        //  (e.g. consider StringBuffer.length(), which is otherwise a simple override)?
        // Modifiers changed from protected to public, non-final to final, or change in abstractness
        Set<Modifier> modifiers = method.getModifiers();
        Set<Modifier> overriddenModifiers = overriddenMethod.getModifiers();
        if ((modifiers.contains(Modifier.PUBLIC) && overriddenModifiers.contains(Modifier.PROTECTED))
                || modifiers.contains(Modifier.FINAL)
                || modifiers.contains(Modifier.ABSTRACT) != overriddenModifiers.contains(Modifier.ABSTRACT)) {
            return true;
        }
        // Change in thrown types
        if (!method.getThrownTypes().equals(overriddenMethod.getThrownTypes())) {
            return true;
        }
        // Documented annotations, other than java.lang.Override, added anywhere in the method signature
        var JAVA_LANG_OVERRIDE = elements().getTypeElement("java.lang.Override");
        var overriderAnnotations = getDocumentedAnnotations(method).stream()
                .filter(am -> !am.getAnnotationType().asElement().equals(JAVA_LANG_OVERRIDE))
                .collect(Collectors.toSet());
        var overriddenAnnotations = getDocumentedAnnotations(overriddenMethod).stream()
                .filter(am -> !am.getAnnotationType().asElement().equals(JAVA_LANG_OVERRIDE))
                .collect(Collectors.toSet());
        return !overriderAnnotations.equals(overriddenAnnotations);
    }

    private Set<AnnotationMirror> getDocumentedAnnotations(ExecutableElement element) {
        Set<AnnotationMirror> annotations = new HashSet<>();
        addDocumentedAnnotations(annotations, element.getAnnotationMirrors());

        new SimpleTypeVisitor14<Void, Void>() {
            @Override
            protected Void defaultAction(TypeMirror e, Void v) {
                addDocumentedAnnotations(annotations, e.getAnnotationMirrors());
                return null;
            }

            @Override
            public Void visitArray(ArrayType t, Void unused) {
                if (t.getComponentType() != null) {
                    visit(t.getComponentType());
                }
                return super.visitArray(t, unused);
            }

            @Override
            public Void visitDeclared(DeclaredType t, Void unused) {
                t.getTypeArguments().forEach(this::visit);
                return super.visitDeclared(t, unused);
            }

            @Override
            public Void visitWildcard(WildcardType t, Void unused) {
                if (t.getExtendsBound() != null) {
                    visit(t.getExtendsBound());
                }
                if (t.getSuperBound() != null) {
                    visit(t.getSuperBound());
                }
                return super.visitWildcard(t, unused);
            }

            @Override
            public Void visitExecutable(ExecutableType t, Void unused) {
                t.getParameterTypes().forEach(this::visit);
                t.getTypeVariables().forEach(this::visit);
                visit(t.getReturnType());
                return super.visitExecutable(t, unused);
            }
        }.visit(element.asType());

        return annotations;
    }

    private void addDocumentedAnnotations(Set<AnnotationMirror> annotations, List<? extends AnnotationMirror> annotationMirrors) {
        annotationMirrors.forEach(annotation -> {
            if (utils.isDocumentedAnnotation((TypeElement) annotation.getAnnotationType().asElement())) {
                annotations.add(annotation);
            }
        });
    }

    /*
     * A container of members declared in this class or interface. Members of
     * the same kind stored in declaration order. The container supports
     * efficient lookup by a member's simple name.
     */
    private class LocalMemberTable {

        final Map<Kind, List<Element>> orderedMembers = new EnumMap<>(Kind.class);
        final Map<Kind, Map<Name, List<Element>>> namedMembers = new EnumMap<>(Kind.class);

        LocalMemberTable() {
            // elements directly declared by this class or interface,
            // listed in declaration order
            List<? extends Element> elements = te.getEnclosedElements();
            for (Element e : elements) {
                if (options.noDeprecated() && utils.isDeprecated(e)) {
                    continue;
                }
                switch (e.getKind()) {
                    case CLASS:
                    case INTERFACE:
                    case ENUM:
                    case ANNOTATION_TYPE:
                    case RECORD:
                        addMember(e, Kind.NESTED_CLASSES);
                        break;
                    case FIELD:
                        addMember(e, Kind.FIELDS);
                        break;
                    case ENUM_CONSTANT:
                        addMember(e, Kind.ENUM_CONSTANTS);
                        break;
                    case METHOD:
                        if (utils.isAnnotationInterface(te)) {
                            addMember(e, Kind.ANNOTATION_TYPE_MEMBER);
                            addMember(e, ((ExecutableElement) e).getDefaultValue() == null
                                    ? Kind.ANNOTATION_TYPE_MEMBER_REQUIRED
                                    : Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL);
                        } else {
                            addMember(e, Kind.METHODS);
                        }
                        break;
                    case CONSTRUCTOR:
                        addMember(e, Kind.CONSTRUCTORS);
                        break;
                }
            }

            // protect element lists from unintended changes by clients
            orderedMembers.replaceAll(this::sealList);
            namedMembers.values().forEach(m -> m.replaceAll(this::sealList));
        }

        private <K, V> List<V> sealList(K unusedKey, List<V> v) {
            return Collections.unmodifiableList(v);
        }

        void addMember(Element e, Kind kind) {
            orderedMembers.computeIfAbsent(kind, k -> new ArrayList<>()).add(e);
            namedMembers.computeIfAbsent(kind, k -> new HashMap<>())
                    .computeIfAbsent(e.getSimpleName(), l -> new ArrayList<>())
                    .add(e);
        }

        List<Element> getOrderedMembers(Kind kind) {
            return orderedMembers.getOrDefault(kind, List.of());
        }

        List<Element> getMembers(Name simpleName, Kind kind) {
            return namedMembers.getOrDefault(kind, Map.of())
                    .getOrDefault(simpleName, List.of());
        }

        <T extends Element> List<T> getMembers(Name simpleName, Kind kind, Class<T> clazz) {
            return getMembers(simpleName, kind)
                    .stream()
                    .map(clazz::cast)
                    .toList();
        }

        List<ExecutableElement> getPropertyMethods(Name simpleName) {
            return getMembers(simpleName, Kind.METHODS).stream()
                    .filter(m -> (utils.isPublic(m) || utils.isProtected(m)))
                    .map(m -> (ExecutableElement) m)
                    .toList();
        }
    }

    private record PropertyMembers(ExecutableElement propertyMethod, VariableElement field,
                                   ExecutableElement getter, ExecutableElement setter) { }

    /*
     * JavaFX convention notes.
     * A JavaFX property-method is a method, which ends with "Property" in
     * its name, takes no parameters and typically returns a subtype of javafx.beans.
     * ReadOnlyProperty, in the strictest sense. However, it may not always
     * be possible for the doclet to have access to j.b.ReadOnlyProperty,
     * for this reason the strict check is disabled via an undocumented flag.
     *
     * Note, a method should not be considered as a property-method,
     * if it satisfied the previously stated conditions AND if the
     * method begins with "set", "get" or "is".
     *
     * Supposing we have  {@code BooleanProperty acmeProperty()}, then the
     * property-name  is "acme".
     *
     * Property field, one may or may not exist and could be private, and
     * should match the property-method.
     *
     * A property-setter is a method starting with "set", and the
     * first character of the upper-cased starting character of the property name, the
     * method must take 1 argument and must return a <code>void</code>.
     *
     * Using the above example {@code void setAcme(Something s)} can be
     * considered as a property-setter of the property "acme".
     *
     * A property-getter is a method  starting with "get" and the first character
     * upper-cased property-name, having no parameters. A method that does not take any
     * parameters and starting with "is" and an upper-cased property-name,
     * returning a primitive type boolean or BooleanProperty can also be
     * considered as a getter, however there must be only one getter for every property.
     *
     * For example {@code Object getAcme()} is a property-getter, and
     * {@code boolean isFoo()}
     */
    private void computeVisibleProperties(LocalMemberTable lmt) {
        if (!options.javafx())
            return;

        PropertyUtils pUtils = config.propertyUtils;
        List<Element> list = visibleMembers.getOrDefault(Kind.METHODS, List.of())
                .stream()
                .filter(e -> pUtils.isPropertyMethod((ExecutableElement) e))
                .toList();

        visibleMembers.put(Kind.PROPERTIES, list);

        List<ExecutableElement> propertyMethods = list.stream()
                .map(e -> (ExecutableElement) e)
                .filter(e -> Objects.equals(utils.getEnclosingTypeElement(e), te))
                .toList();

        // Compute additional properties related sundries.
        for (ExecutableElement propertyMethod : propertyMethods) {
            String baseName = pUtils.getBaseName(propertyMethod);
            List<VariableElement> flist = lmt.getMembers(utils.elementUtils.getName(baseName), Kind.FIELDS, VariableElement.class);
            VariableElement field = flist.isEmpty() ? null : flist.get(0);

            // TODO: this code does not seem to be covered by tests well (JDK-8304170)
            ExecutableElement getter = null;
            var g = lmt.getPropertyMethods(utils.elementUtils.getName(pUtils.getGetName(propertyMethod))).stream()
                    .filter(m -> m.getParameters().isEmpty()) // Getters have zero params, no overloads!
                    .findAny();
            if (g.isPresent()) {
                getter = g.get();
            } else {
                // Check if isProperty methods are present ?
                var i = lmt.getPropertyMethods(utils.elementUtils.getName(pUtils.getIsName(propertyMethod))).stream()
                        .filter(m -> m.getParameters().isEmpty())
                        .findAny();
                if (i.isPresent()) {
                    // Check if the return type of property method matches an isProperty method.
                    if (pUtils.hasIsMethod(propertyMethod)) {
                        // Getters have zero params, no overloads!
                        getter = i.get();
                    }
                }
            }

            var setter = lmt.getPropertyMethods(utils.elementUtils.getName(pUtils.getSetName(propertyMethod))).stream()
                    // TODO: the number and the types of parameters a setter takes is not tested (JDK-8304170)
                    .filter(m -> m.getParameters().size() == 1 && pUtils.isValidSetterMethod(m))
                    .findAny()
                    .orElse(null);

            PropertyMembers pm = new PropertyMembers(propertyMethod, field, getter, setter);
            propertyMap.put(propertyMethod, pm);
            if (getter != null) {
                propertyMap.put(getter, pm);
            }
            if (setter != null) {
                propertyMap.put(setter, pm);
            }

            // Debugging purposes
            // System.out.println("te: " + te + ": " + utils.getEnclosingTypeElement(propertyMethod) +
            //        ":" + propertyMethod.toString() + "->" + propertyMap.get(propertyMethod));
        }
    }

    @Override
    public int hashCode() {
        return te.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VisibleMemberTable other))
            return false;
        return te.equals(other.te);
    }

    @Override
    public String toString() {
        // output the simple name, not the fully qualified name, which is needlessly long
        return getClass().getSimpleName() + "@" + Integer.toHexString(super.hashCode())
                + "[" + te.getQualifiedName().toString() + "]";
    }
}
