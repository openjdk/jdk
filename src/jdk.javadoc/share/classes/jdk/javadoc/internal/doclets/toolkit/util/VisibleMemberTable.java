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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor14;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.PropertyUtils;

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

    /** Tables for direct and indirect superclasses. */
    private final List<VisibleMemberTable> allSuperclasses;
    /**
     * Tables for direct and indirect superinterfaces.
     * Tables for superinterfaces might not be unique.
     */
    private final List<VisibleMemberTable> allSuperinterfaces;
    /**
     * Tables for direct superclass and direct superinterfaces.
     *
     * The position of a table for the superclass in the list is unspecified.
     * Tables for the superinterfaces might not be unique (i.e. an interface
     * may be added from different lineages).
     */
    private final List<VisibleMemberTable> parents;

    private Map<Kind, List<Element>> visibleMembers;
    private final Map<ExecutableElement, PropertyMembers> propertyMap = new HashMap<>();

    //  FIXME: Figure out why it is one-one and not one-to-many.
    /**
     * Maps a method m declared in {@code te} to a visible method m' in a
     * {@code te}'s supertype such that m overrides m'.
     */
    private final Map<ExecutableElement, OverrideInfo> overriddenMethodTable
            = new LinkedHashMap<>();

    protected VisibleMemberTable(TypeElement typeElement, BaseConfiguration configuration,
                                 VisibleMemberCache mcache) {
        config = configuration;
        utils = configuration.utils;
        options = configuration.getOptions();
        te = typeElement;
        parent = (TypeElement) utils.typeUtils.asElement(te.getSuperclass());
        this.mcache = mcache;
        allSuperclasses = new ArrayList<>();
        allSuperinterfaces = new ArrayList<>();
        parents = new ArrayList<>();
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

    private List<VisibleMemberTable> getAllSuperclasses() {
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

    /**
     * Returns the method overridden by the provided method, or {@code null}.
     *
     * Sometimes it's not possible to link to a method that a link, linkplain,
     * or see tag mentions. This is because the method is a "simple override"
     * and, thus, has no useful documentation, or because the method is
     * declared in a type that has package access and, thus, has no visible
     * documentation.
     *
     * Call this method to determine if any of the above is the case. If the
     * call returns a method element, link to that method element instead of
     * the provided method.
     *
     * @param e the method to check
     * @return the method found or {@code null}
     */
    public ExecutableElement getOverriddenMethod(ExecutableElement e) {
        // TODO: consider possible ambiguities: multiple overridden methods
        ensureInitialized();
        assert !overriddenMethodTable.containsKey(null);
        OverrideInfo found = overriddenMethodTable.get(e);
        if (found != null
                && (found.simpleOverride || utils.isUndocumentedEnclosure(utils.getEnclosingTypeElement(e)))) {
            return found.overriddenMethod;
        }
        return null;
    }

    /**
     * {@return true if the specified method is NOT a simple override of some
     * other method, otherwise false}
     *
     * @param e the method to check
     */
    private boolean isNotSimpleOverride(ExecutableElement e) {
        ensureInitialized();

        var info = overriddenMethodTable.get(e);
        return info == null || !info.simpleOverride;
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
                parents.add(vmt);
                allSuperinterfaces.addAll(vmt.getAllSuperinterfaces());
            }
        }

        if (parent != null) {
            VisibleMemberTable vmt = mcache.getVisibleMemberTable(parent);
            allSuperclasses.add(vmt);
            allSuperclasses.addAll(vmt.getAllSuperclasses());
            // Add direct and indirect superinterfaces of a superclass.
            allSuperinterfaces.addAll(vmt.getAllSuperinterfaces());
            parents.add(vmt);
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
        return isInherited(e) && !isMemberHidden(e, kind, lmt);
    }

    private boolean isInherited(Element e) {
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
                List<Element> list = lmt.getMembers(inheritedMember, kind);
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

    private void computeVisibleMethods(LocalMemberTable lmt) {
        // A union of visible methods from all parents. Used to compute methods
        // inherited by this type as the difference of itself and those methods
        // that aren't inherited.
        Set<Element> parentMethods = new LinkedHashSet<>();
        // - a key is a method overridden by one or more parent methods
        // - the value is a list of parent methods that override the key
        Map<ExecutableElement, List<ExecutableElement>> overriddenByTable = new HashMap<>();
        for (VisibleMemberTable pvmt : parents) {
            // Merge the lineage overrides into local table
            pvmt.overriddenMethodTable.forEach((method, methodInfo) -> {
                if (!methodInfo.simpleOverride) { // consider only real overrides
                    List<ExecutableElement> list = overriddenByTable.computeIfAbsent(methodInfo.overriddenMethod,
                            k -> new ArrayList<>());
                    list.add(method);
                }
            });
            // Lists of visible methods from different parents may share some
            // methods. These are the methods that such parents inherited from
            // their common ancestor.
            //
            // Such methods won't result in duplicates in parentMethods as we
            // purposefully don't track duplicates.
            // FIXME: add a test to assert order (LinkedHashSet)
            parentMethods.addAll(pvmt.getAllVisibleMembers(Kind.METHODS));
        }

        // filter out methods that aren't inherited
        //
        // nb. This statement has side effects that can initialize
        // members of the overriddenMethodTable field, so it must be
        // evaluated eagerly with toList().
        List<Element> inheritedMethods = parentMethods.stream()
                .filter(e -> allowInheritedMethod((ExecutableElement) e, overriddenByTable, lmt))
                .toList();

        // filter out "simple overrides" from local methods
        Predicate<ExecutableElement> nonSimpleOverride = m -> {
            OverrideInfo i = overriddenMethodTable.get(m);
            return i == null || !i.simpleOverride;
        };

        Stream<ExecutableElement> localStream = lmt.getOrderedMembers(Kind.METHODS)
                .stream()
                .map(m -> (ExecutableElement)m)
                .filter(nonSimpleOverride);

        // Merge the above list and stream, making sure the local methods precede the others
        // Final filtration of elements // FIXME why is order important?
        List<Element> list = Stream.concat(localStream, inheritedMethods.stream())
                .filter(this::mustDocument)
                .toList();

        visibleMembers.put(Kind.METHODS, list);

        // copy over overridden tables from the lineage
        for (VisibleMemberTable pvmt : parents) {
            // a key in overriddenMethodTable is a method _declared_ in the respective parent;
            // no two _different_ parents can share a declared method, by definition;
            // if parents in the list are different (i.e. the list of parents doesn't contain duplicates),
            //   then no keys are equal and thus no replace happens
            // if the list of parents contains duplicates, values for the equal keys are equal,
            //   so no harm if they are replaced in the map
            assert putAllIsNonReplacing(overriddenMethodTable, pvmt.overriddenMethodTable);
            overriddenMethodTable.putAll(pvmt.overriddenMethodTable);
        }
    }

    private static <K, V> boolean putAllIsNonReplacing(Map<K, V> dst, Map<K, V> src) {
        for (var e : src.entrySet()) {
            if (dst.containsKey(e.getKey())
                    && !Objects.equals(dst.get(e.getKey()), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean isEnclosureInterface(Element e) {
        TypeElement enclosing = utils.getEnclosingTypeElement(e);
        return utils.isPlainInterface(enclosing);
    }

    private boolean allowInheritedMethod(ExecutableElement inheritedMethod,
                                         Map<ExecutableElement, List<ExecutableElement>> overriddenByTable,
                                         LocalMemberTable lmt) {
        if (!isInherited(inheritedMethod))
            return false;

        final boolean haveStatic = utils.isStatic(inheritedMethod);
        final boolean inInterface = isEnclosureInterface(inheritedMethod);

        // Static interface methods are never inherited (JLS 8.4.8 and 9.1.3)
        if (haveStatic && inInterface) {
            return false;
        }

        // Multiple-Inheritance: remove the interface method that may have
        // been overridden by another interface method in the hierarchy
        //
        // Note: The following approach is very simplistic and is compatible
        // with old VMM. A future enhancement, may include a contention breaker,
        // to correctly eliminate those methods that are merely definitions
        // in favor of concrete overriding methods, for instance those that have
        // API documentation and are not abstract OR default methods.
        if (inInterface) {
            List<ExecutableElement> list = overriddenByTable.get(inheritedMethod);
            if (list != null) {
                boolean found = list.stream()
                        .anyMatch(this::isEnclosureInterface);
                if (found)
                    return false;
            }
        }

        Elements elementUtils = config.docEnv.getElementUtils();

        // Check the local methods in this type.
        // List contains overloads and probably something else, but one match is enough, hence short-circuiting
        List<Element> lMethods = lmt.getMembers(inheritedMethod, Kind.METHODS);
        for (Element le : lMethods) {
            ExecutableElement lMethod = (ExecutableElement) le;
            // Ignore private methods or those methods marked with
            // a "hidden" tag. // FIXME I cannot see where @hidden is ignored
            if (utils.isPrivate(lMethod))
                continue;

            // Remove methods that are "hidden", in JLS terms.
            if (haveStatic && utils.isStatic(lMethod) &&
                    elementUtils.hides(lMethod, inheritedMethod)) {
                return false;
            }

            // Check for overriding methods.
            if (elementUtils.overrides(lMethod, inheritedMethod,
                    utils.getEnclosingTypeElement(lMethod))) {

                assert utils.getEnclosingTypeElement(lMethod).equals(te);

                // Disallow package-private super methods to leak in
                TypeElement encl = utils.getEnclosingTypeElement(inheritedMethod);
                if (utils.isUndocumentedEnclosure(encl)) {
                    // FIXME
                    //  is simpleOverride=false here to force to be used because
                    //  it cannot be linked to, because package-private?
                    overriddenMethodTable.computeIfAbsent(lMethod,
                            l -> new OverrideInfo(inheritedMethod, false));
                    return false;
                }

                // Even with --override-methods=summary we want to include details of
                // overriding method if something noteworthy has been added or changed
                // either in the local overriding method or an in-between overriding method
                // (as evidenced by an entry in overriddenByTable).
                boolean simpleOverride = utils.isSimpleOverride(lMethod)
                        && !overridingSignatureChanged(lMethod, inheritedMethod)
                        && !overriddenByTable.containsKey(inheritedMethod);
                overriddenMethodTable.computeIfAbsent(lMethod,
                        l -> new OverrideInfo(inheritedMethod, simpleOverride));
                return simpleOverride;
            }
        }
        return true;
    }

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
        // Documented annotations added anywhere in the method signature
        return !getDocumentedAnnotations(method).equals(getDocumentedAnnotations(overriddenMethod));
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
     * This class encapsulates the details of local members. orderedMembers
     * contains the members in the declaration order, additionally a
     * HashMap is maintained for performance optimization to look up
     * members. As a future enhancement is perhaps to consolidate the ordering
     * into a Map, capturing the insertion order, thereby eliminating an
     * ordered list.
     */
    class LocalMemberTable {

        // Maintains declaration order
        private final Map<Kind, List<Element>> orderedMembers;

        // Performance optimization
        private final Map<Kind, Map<String, List<Element>>> memberMap;

        LocalMemberTable() {
            orderedMembers = new EnumMap<>(Kind.class);
            memberMap = new EnumMap<>(Kind.class);
            // elements directly declared by te
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
                    case METHOD:
                        if (utils.isAnnotationInterface(te)) {
                            ExecutableElement ee = (ExecutableElement) e;
                            addMember(e, Kind.ANNOTATION_TYPE_MEMBER);
                            addMember(e, ee.getDefaultValue() == null
                                    ? Kind.ANNOTATION_TYPE_MEMBER_REQUIRED
                                    : Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL);
                        } else {
                            addMember(e, Kind.METHODS);
                        }
                        break;
                    case CONSTRUCTOR:
                        addMember(e, Kind.CONSTRUCTORS);
                        break;
                    case ENUM_CONSTANT:
                        addMember(e, Kind.ENUM_CONSTANTS);
                        break;
                }
            }

            // Freeze the data structures
            for (Kind kind : Kind.values()) {
                orderedMembers.computeIfPresent(kind, (k, v) -> Collections.unmodifiableList(v));
                orderedMembers.computeIfAbsent(kind, t -> List.of());

                memberMap.computeIfPresent(kind, (k, v) -> Collections.unmodifiableMap(v));
                memberMap.computeIfAbsent(kind, t -> Map.of());
            }
        }

        String getMemberKey(Element e) {
            return new SimpleElementVisitor14<String, Void>() {
                @Override
                public String visitExecutable(ExecutableElement e, Void aVoid) {
                    return e.getSimpleName() + ":" + e.getParameters().size();
                }

                @Override
                protected String defaultAction(Element e, Void aVoid) {
                    return e.getSimpleName().toString();
                }
            }.visit(e);
        }

        void addMember(Element e, Kind kind) {
            List<Element> list = orderedMembers.computeIfAbsent(kind, k -> new ArrayList<>());
            list.add(e);

            Map<String, List<Element>> map = memberMap.computeIfAbsent(kind, k -> new HashMap<>());
            list = map.computeIfAbsent(getMemberKey(e), l -> new ArrayList<>());
            list.add(e);
        }

        List<Element> getOrderedMembers(Kind kind) {
            return orderedMembers.get(kind);
        }

        List<Element> getMembers(Element e, Kind kind) {
            String key = getMemberKey(e);
            return getMembers(key, kind);
        }

        List<Element> getMembers(String key, Kind kind) {
            Map<String, List<Element>> map = memberMap.get(kind);
            return map.getOrDefault(key, List.of());
        }

        <T extends Element> List<T> getMembers(String key, Kind kind, Class<T> clazz) {
            Map<String, List<Element>> map = memberMap.get(kind);
            return map.getOrDefault(key, List.of())
                    .stream()
                    .map(clazz::cast)
                    .toList();
        }

        List<ExecutableElement> getPropertyMethods(String methodName, int argcount) {
            return getMembers(methodName + ":" + argcount, Kind.METHODS).stream()
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
            List<VariableElement> flist = lmt.getMembers(baseName, Kind.FIELDS, VariableElement.class);
            VariableElement field = flist.isEmpty() ? null : flist.get(0);

            ExecutableElement getter = null, setter = null;
            List<ExecutableElement> found = lmt.getPropertyMethods(pUtils.getGetName(propertyMethod), 0);
            if (!found.isEmpty()) {
                // Getters have zero params, no overloads! pick the first.
                getter = found.get(0);
            }
            if (getter == null) {
                // Check if isProperty methods are present ?
                found = lmt.getPropertyMethods(pUtils.getIsName(propertyMethod), 0);
                if (!found.isEmpty()) {
                    // Check if the return type of property method matches an isProperty method.
                    if (pUtils.hasIsMethod(propertyMethod)) {
                        // Getters have zero params, no overloads!, pick the first.
                        getter = found.get(0);
                    }
                }
            }
            found = lmt.getPropertyMethods(pUtils.getSetName(propertyMethod), 1);
            if (found != null) {
                for (ExecutableElement e : found) {
                    if (pUtils.isValidSetterMethod(e)) {
                        setter = e;
                        break;
                    }
                }
            }

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


    // Future cleanups

    private final Map<ExecutableElement, SoftReference<ImplementedMethods>>
            implementMethodsFinders = new HashMap<>();

    private ImplementedMethods getImplementedMethodsFinder(ExecutableElement method) {
        SoftReference<ImplementedMethods> ref = implementMethodsFinders.get(method);
        ImplementedMethods imf = ref == null ? null : ref.get();
        // imf does not exist or was gc'ed away?
        if (imf == null) {
            imf = new ImplementedMethods(method);
            implementMethodsFinders.put(method, new SoftReference<>(imf));
        }
        return imf;
    }

    public List<ExecutableElement> getImplementedMethods(ExecutableElement method) {
        ImplementedMethods imf = getImplementedMethodsFinder(method);
        return imf.getImplementedMethods().stream()
                .filter(this::isNotSimpleOverride)
                .toList();
    }

    public TypeMirror getImplementedMethodHolder(ExecutableElement method,
                                                 ExecutableElement implementedMethod) {
        ImplementedMethods imf = getImplementedMethodsFinder(method);
        return imf.getMethodHolder(implementedMethod);
    }

    private class ImplementedMethods {

        private final Map<ExecutableElement, TypeMirror> interfaces = new LinkedHashMap<>();

        public ImplementedMethods(ExecutableElement implementer) {
            var typeElement = (TypeElement) implementer.getEnclosingElement();
            for (TypeMirror i : utils.getAllInterfaces(typeElement)) {
                TypeElement dst = utils.asTypeElement(i); // a type element to look an implemented method in
                ExecutableElement implemented = findImplementedMethod(dst, implementer);
                if (implemented == null) {
                    continue;
                }
                var prev = interfaces.put(implemented, i);
                // no two type elements declare the same method
                assert prev == null;
                // dst can be generic, while i might be parameterized; but they
                // must the same type element. For example, if dst is Set<T>,
                // then i is Set<String>
                assert Objects.equals(((DeclaredType) i).asElement(), dst);
            }
        }

        private ExecutableElement findImplementedMethod(TypeElement te, ExecutableElement implementer) {
            var typeElement = (TypeElement) implementer.getEnclosingElement();
            for (var m : utils.getMethods(te)) {
                if (utils.elementUtils.overrides(implementer, m, typeElement)) {
                    return m;
                }
            }
            return null;
        }

        /**
         * Returns a collection of interface methods which the method passed in the
         * constructor is implementing. The search/build order is as follows:
         * <pre>
         * 1. Search in all the immediate interfaces which this method's class is
         *    implementing. Do it recursively for the superinterfaces as well.
         * 2. Traverse all the superclasses and search recursively in the
         *    interfaces which those superclasses implement.
         * </pre>
         *
         * @return a collection of implemented methods
         */
        Collection<ExecutableElement> getImplementedMethods() {
            return interfaces.keySet();
        }

        TypeMirror getMethodHolder(ExecutableElement ee) {
            return interfaces.get(ee);
        }
    }

    /*
     * (Here "override" used as a noun, not a verb, for a short and descriptive
     * name. Sadly, we cannot use "Override" as a complete name because a clash
     * with @java.lang.Override would make it inconvenient.)
     *
     * Used to provide additional attributes to the otherwise boolean
     * "overrides(a, b)" relationship.
     *
     * Overriding method could be a key in a map and an instance of this
     * record could be the value.
     */
    private record OverrideInfo(ExecutableElement overriddenMethod,
                                boolean simpleOverride) {
        @Override // for debugging
        public String toString() {
            return overriddenMethod.getEnclosingElement()
                    + "::" + overriddenMethod + ", simple=" + simpleOverride;
        }
    }
}
