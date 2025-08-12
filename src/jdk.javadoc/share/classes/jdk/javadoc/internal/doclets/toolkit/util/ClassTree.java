/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Messages;

/**
 * Class and interface hierarchies.
 */
public class ClassTree {

    /**
     * The kind of hierarchy.
     * Currently, the kinds correspond to the kinds of declared types,
     * although other partitions could be possible.
     */
    private enum HierarchyKind {
        CLASSES, ENUM_CLASSES, RECORD_CLASSES, INTERFACES, ANNOTATION_INTERFACES
    }

    /**
     * A "hierarchy" provides the subtypes of all the supertypes of a set of leaf elements,
     * together with the root supertype(s).
     */
    public static class Hierarchy {
        private final SortedSet<TypeElement> roots;
        private final SubtypeMap subtypes;
        private final Comparator<Element> comparator;

        Hierarchy(Comparator<Element> comparator) {
            this.comparator = comparator;
            roots = new TreeSet<>(comparator);
            subtypes = new SubtypeMap(comparator);
        }

        /**
         * {@return the roots of the hierarchy}
         * Each root is a class or interface with no superclass or superinterfaces.
         * If the hierarchy just contains classes, the root will be {@code java.lang.Object}.
         */
        public SortedSet<TypeElement> roots() {
            return roots;
        }

        /**
         * {@return the immediate subtypes of the given type element, or an empty set if there are none}
         * @param typeElement the type element
         */
        public SortedSet<TypeElement> subtypes(TypeElement typeElement) {
            return subtypes.getOrDefault(typeElement, Collections.emptySortedSet());
        }

        /**
         * {@return the set of all subtypes of the given type element, or an empty set if there are none}
         *
         * The set of all subtypes is the transitive closure of the {@linkplain #subtypes(TypeElement) immediate subtypes}
         * of the given type element.
         *
         * @param typeElement the type element
         */
        public SortedSet<TypeElement> allSubtypes(TypeElement typeElement) {
            // new entries added to the collection are searched as well;
            // this is really a work queue.
            List<TypeElement> list = new ArrayList<>(subtypes(typeElement));
            for (int i = 0; i < list.size(); i++) {
                var subtypes = subtypes(list.get(i));
                for (var subtype : subtypes) {
                    if (!list.contains(subtype)) {
                        list.add(subtype);
                    }
                }
            }
            SortedSet<TypeElement> out = new TreeSet<>(comparator);
            out.addAll(list);
            return out;
        }
    }

    /**
     * A map giving the subtypes for each of a collection of type elements.
     * The subtypes may be subclasses or subinterfaces, depending on the context.
     *
     * The subtypes are recorded by calling {@link SubtypeMap#addSubtype(TypeElement, TypeElement) addSubtype}.
     */
    @SuppressWarnings("serial")
    private static class SubtypeMap extends HashMap<TypeElement, SortedSet<TypeElement>> {
        private final Comparator<Element> comparator;

        /**
         * Creates a map to provide the subtypes of a type element,
         * sorted according to a given comparator.
         *
         * An alternate implementation would be to store the subtypes unsorted,
         * and to lazily sort them when needed, such as when generating documentation.
         *
         * @param comparator the comparator
         */
        SubtypeMap(Comparator<Element> comparator) {
            this.comparator = comparator;
        }

        /**
         * {@return the subtypes for a type element, or an empty set if there are none}
         *
         * @param typeElement the type element
         */
        SortedSet<TypeElement> getSubtypes(TypeElement typeElement) {
            return computeIfAbsent(typeElement, te -> new TreeSet<>(comparator));
        }

        /**
         * Adds a subtype into the set of subtypes for a type element
         *
         * @param typeElement the type element
         * @param subtype the subtype
         * @return {@code true} if this set did not already contain the specified element
         */
        boolean addSubtype(TypeElement typeElement, TypeElement subtype) {
            return getSubtypes(typeElement).add(subtype);
        }
    }

    /**
     * The collection of hierarchies, indexed by kind.
     */
    private final Map<HierarchyKind, Hierarchy> hierarchies;

   /**
    * Mapping for each interface with classes that implement it.
    */
    private final SubtypeMap implementingClasses;

    private final BaseConfiguration configuration;
    private final Utils utils;

    /**
     * Constructor. Build the tree for all the included type elements.
     *
     * @param configuration the configuration of the doclet
     */
    public ClassTree(BaseConfiguration configuration) {
        this.configuration = configuration;
        this.utils = configuration.utils;

        Messages messages = configuration.getMessages();
        messages.notice("doclet.Building_Tree");

        Comparator<Element> comparator = utils.comparators.classUseComparator();

        hierarchies = new EnumMap<>(HierarchyKind.class);
        for (var hk : HierarchyKind.values()) {
            hierarchies.put(hk, new Hierarchy(comparator));
        }
        implementingClasses = new SubtypeMap(comparator);

        buildTree(configuration.getIncludedTypeElements());
    }

    /**
     * Constructor. Build the tree for the given collection of classes.
     *
     * @param classesSet a set of classes
     * @param configuration The current configuration of the doclet.
     */
    public ClassTree(SortedSet<TypeElement> classesSet, BaseConfiguration configuration) {
        this.configuration = configuration;
        this.utils = configuration.utils;

        Comparator<Element> comparator = utils.comparators.classUseComparator();

        hierarchies = new EnumMap<>(HierarchyKind.class);
        for (var hk : HierarchyKind.values()) {
            hierarchies.put(hk, new Hierarchy(comparator));
        }
        implementingClasses = new SubtypeMap(comparator);

        buildTree(classesSet);
    }

    /**
     * Generate the hierarchies for the given type elements.
     *
     * @param typeElements the type elements
     */
    private void buildTree(Iterable<TypeElement> typeElements) {
        for (TypeElement te : typeElements) {
            // In the tree page (e.g overview-tree.html) do not include
            // information of classes which are deprecated or are a part of a
            // deprecated package.
            if (configuration.getOptions().noDeprecated() &&
                    (utils.isDeprecated(te) ||
                    utils.isDeprecated(utils.containingPackage(te)))) {
                continue;
            }

            if (utils.isHidden(te)) {
                continue;
            }

            if (utils.isEnum(te)) {
                processType(te, hierarchies.get(HierarchyKind.ENUM_CLASSES));
            } else if (utils.isRecord(te)) {
                processType(te, hierarchies.get(HierarchyKind.RECORD_CLASSES));
            } else if (utils.isClass(te)) {
                processType(te, hierarchies.get(HierarchyKind.CLASSES));
            } else if (utils.isPlainInterface(te)) {
                processInterface(te);
            } else if (utils.isAnnotationInterface(te)) {
                processType(te, hierarchies.get(HierarchyKind.ANNOTATION_INTERFACES));
            }
        }
    }

    /**
     * Adds details for a type element into a given hierarchy.
     *
     * The subtypes are updated for the transitive closure of all supertypes of this type element.
     * If this type element has no superclass or superinterfaces, it is marked as a root.
     *
     * @param typeElement for which the hierarchy is to be generated
     * @param hierarchy the hierarchy
     */
    private void processType(TypeElement typeElement, Hierarchy hierarchy) {
        TypeElement superclass = utils.getFirstVisibleSuperClassAsTypeElement(typeElement);
        if (superclass != null) {
            if (!hierarchy.subtypes.addSubtype(superclass, typeElement)) {
                return;
            } else {
                processType(superclass, hierarchy);
            }
        } else {     // typeElement is java.lang.Object, add it once to the set
            hierarchy.roots.add(typeElement);
        }

        Set<TypeMirror> interfaces = utils.getAllInterfaces(typeElement);
        for (TypeMirror t : interfaces) {
            implementingClasses.addSubtype(utils.asTypeElement(t), typeElement);
        }
    }

    /**
     * For the interface passed get the interfaces which it extends, and then
     * put this interface in the subinterface set of those interfaces. Do it
     * recursively. If an interface doesn't have superinterface just attach
     * that interface in the set of all the baseInterfaces.
     *
     * @param typeElement Interface under consideration.
     */
    private void processInterface(TypeElement typeElement) {
        Hierarchy interfacesHierarchy = hierarchies.get(HierarchyKind.INTERFACES);
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
        if (!interfaces.isEmpty()) {
            for (TypeMirror t : interfaces) {
                if (!interfacesHierarchy.subtypes.addSubtype(utils.asTypeElement(t), typeElement)) {
                    return;
                } else {
                    processInterface(utils.asTypeElement(t));   // Recurse
                }
            }
        } else {
            // we need to add all the interfaces who do not have
            // super-interfaces to the root set to traverse them
            interfacesHierarchy.roots.add(typeElement);
        }
    }

    /**
     * Return the subclass set for the class passed.
     *
     * @param typeElement class whose subclass set is required.
     */
    public SortedSet<TypeElement> subClasses(TypeElement typeElement) {
        return hierarchies.get(HierarchyKind.CLASSES).subtypes(typeElement);
    }

    /**
     * Return the subinterface set for the interface passed.
     *
     * @param typeElement interface whose subinterface set is required.
     */
    public SortedSet<TypeElement> subInterfaces(TypeElement typeElement) {
        return hierarchies.get(HierarchyKind.INTERFACES).subtypes(typeElement);
    }

    /**
     * Return the set of classes which implement the interface passed.
     *
     * @param typeElement interface whose implementing-classes set is required.
     */
    public SortedSet<TypeElement> implementingClasses(TypeElement typeElement) {
        SortedSet<TypeElement> result = implementingClasses.getSubtypes(typeElement);
        SortedSet<TypeElement> interfaces = hierarchy(typeElement).allSubtypes(typeElement);

        // If class x implements a subinterface of typeElement, then it follows
        // that class x implements typeElement.
        for (TypeElement te : interfaces) {
            result.addAll(implementingClasses(te));
        }
        return result;
    }

    public Hierarchy hierarchy(TypeElement typeElement) {
        HierarchyKind hk = switch (typeElement.getKind()) {
            case CLASS -> HierarchyKind.CLASSES;
            case ENUM -> HierarchyKind.ENUM_CLASSES;
            case RECORD -> HierarchyKind.RECORD_CLASSES;
            case INTERFACE -> HierarchyKind.INTERFACES;
            case ANNOTATION_TYPE -> HierarchyKind.ANNOTATION_INTERFACES;
            default -> throw new IllegalArgumentException(typeElement.getKind().name() + " " + typeElement.getQualifiedName());
        };
        return hierarchies.get(hk);
    }

    /**
     * {@return the hierarchy for which the leaf nodes are plain classes}
     */
    public Hierarchy classes() {
        return hierarchies.get(HierarchyKind.CLASSES);
    }

    /**
     * {@return the hierarchy for which the leaf nodes are enum classes}
     */
    public Hierarchy enumClasses() {
        return hierarchies.get(HierarchyKind.ENUM_CLASSES);
    }

    /**
     * {@return the hierarchy for which the leaf nodes are record classes}
     */
    public Hierarchy recordClasses() {
        return hierarchies.get(HierarchyKind.RECORD_CLASSES);
    }

    /**
     * {@return the hierarchy for which the leaf nodes are plain interfaces}
     */
    public Hierarchy interfaces() {
        return hierarchies.get(HierarchyKind.INTERFACES);
    }

    /**
     * {@return the hierarchy for which the leaf nodes are annotation interfaces}
     */
    public Hierarchy annotationInterfaces() {
        return hierarchies.get(HierarchyKind.ANNOTATION_INTERFACES);
    }
}
