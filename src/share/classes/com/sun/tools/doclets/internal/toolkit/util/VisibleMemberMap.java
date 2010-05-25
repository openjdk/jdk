/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import java.util.*;

/**
 * A data structure that encapsulates the visible members of a particular
 * type for a given class tree.  To use this data structor, you must specify
 * the type of member you are interested in (nested class, field, constructor
 * or method) and the leaf of the class tree.  The data structure will map
 * all visible members in the leaf and classes above the leaf in the tree.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 */
public class VisibleMemberMap {

    private boolean noVisibleMembers = true;

    public static final int INNERCLASSES    = 0;
    public static final int ENUM_CONSTANTS  = 1;
    public static final int FIELDS          = 2;
    public static final int CONSTRUCTORS    = 3;
    public static final int METHODS         = 4;
    public static final int ANNOTATION_TYPE_MEMBER_OPTIONAL = 5;
    public static final int ANNOTATION_TYPE_MEMBER_REQUIRED = 6;

    /**
     * The total number of member types is {@value}.
     */
    public static final int NUM_MEMBER_TYPES = 7;

    public static final String STARTLEVEL = "start";

    /**
     * List of ClassDoc objects for which ClassMembers objects are built.
     */
    private final List<ClassDoc> visibleClasses = new ArrayList<ClassDoc>();

    /**
     * Map for each member name on to a map which contains members with same
     * name-signature. The mapped map will contain mapping for each MemberDoc
     * onto it's respecive level string.
     */
    private final Map<Object,Map<ProgramElementDoc,String>> memberNameMap = new HashMap<Object,Map<ProgramElementDoc,String>>();

    /**
     * Map of class and it's ClassMembers object.
     */
    private final Map<ClassDoc,ClassMembers> classMap = new HashMap<ClassDoc,ClassMembers>();

    /**
     * Type whose visible members are requested.  This is the leaf of
     * the class tree being mapped.
     */
    private final ClassDoc classdoc;

    /**
     * Member kind: InnerClasses/Fields/Methods?
     */
    private final int kind;

    /**
     * Deprected members should be excluded or not?
     */
    private final boolean nodepr;

    /**
     * Construct a VisibleMemberMap of the given type for the given
     * class.  If nodepr is true, exclude the deprecated members from
     * the map.
     *
     * @param classdoc the class whose members are being mapped.
     * @param kind the kind of member that is being mapped.
     * @param nodepr if true, exclude the deprecated members from the map.
     */
    public VisibleMemberMap(ClassDoc classdoc, int kind, boolean nodepr) {
        this.classdoc = classdoc;
        this.nodepr = nodepr;
        this.kind = kind;
        new ClassMembers(classdoc, STARTLEVEL).build();
    }

    /**
     * Return the list of visible classes in this map.
     *
     * @return the list of visible classes in this map.
     */
    public List<ClassDoc> getVisibleClassesList() {
        sort(visibleClasses);
        return visibleClasses;
    }

    /**
     * Return the package private members inherited by the class.  Only return
     * if parent is package private and not documented.
     *
     * @param configuation the current configuration of the doclet.
     * @return the package private members inherited by the class.
     */
    private List<ProgramElementDoc> getInheritedPackagePrivateMethods(Configuration configuration) {
        List<ProgramElementDoc> results = new ArrayList<ProgramElementDoc>();
        for (Iterator<ClassDoc> iter = visibleClasses.iterator(); iter.hasNext(); ) {
            ClassDoc currentClass = iter.next();
            if (currentClass != classdoc &&
                currentClass.isPackagePrivate() &&
                !Util.isLinkable(currentClass, configuration)) {
                // Document these members in the child class because
                // the parent is inaccessible.
                results.addAll(getMembersFor(currentClass));
            }
        }
        return results;
    }

    /**
     * Return the visible members of the class being mapped.  Also append at the
     * end of the list members that are inherited by inaccessible parents. We
     * document these members in the child because the parent is not documented.
     *
     * @param configuation the current configuration of the doclet.
     */
    public List<ProgramElementDoc> getLeafClassMembers(Configuration configuration) {
        List<ProgramElementDoc> result = getMembersFor(classdoc);
        result.addAll(getInheritedPackagePrivateMethods(configuration));
        return result;
    }

    /**
     * Retrn the list of members for the given class.
     *
     * @param cd the class to retrieve the list of visible members for.
     *
     * @return the list of members for the given class.
     */
    public List<ProgramElementDoc> getMembersFor(ClassDoc cd) {
        ClassMembers clmembers = classMap.get(cd);
        if (clmembers == null) {
            return new ArrayList<ProgramElementDoc>();
        }
        return clmembers.getMembers();
    }

    /**
     * Sort the given mixed list of classes and interfaces to a list of
     * classes followed by interfaces traversed. Don't sort alphabetically.
     */
    private void sort(List<ClassDoc> list) {
        List<ClassDoc> classes = new ArrayList<ClassDoc>();
        List<ClassDoc> interfaces = new ArrayList<ClassDoc>();
        for (int i = 0; i < list.size(); i++) {
            ClassDoc cd = list.get(i);
            if (cd.isClass()) {
                classes.add(cd);
            } else {
                interfaces.add(cd);
            }
        }
        list.clear();
        list.addAll(classes);
        list.addAll(interfaces);
    }

    private void fillMemberLevelMap(List<ProgramElementDoc> list, String level) {
        for (int i = 0; i < list.size(); i++) {
            Object key = getMemberKey(list.get(i));
            Map<ProgramElementDoc,String> memberLevelMap = memberNameMap.get(key);
            if (memberLevelMap == null) {
                memberLevelMap = new HashMap<ProgramElementDoc,String>();
                memberNameMap.put(key, memberLevelMap);
            }
            memberLevelMap.put(list.get(i), level);
        }
    }

    private void purgeMemberLevelMap(List<ProgramElementDoc> list, String level) {
        for (int i = 0; i < list.size(); i++) {
            Object key = getMemberKey(list.get(i));
            Map<ProgramElementDoc, String> memberLevelMap = memberNameMap.get(key);
            if (level.equals(memberLevelMap.get(list.get(i))))
                memberLevelMap.remove(list.get(i));
        }
    }

    /**
     * Represents a class member.  We should be able to just use a
     * ProgramElementDoc instead of this class, but that doesn't take
     * type variables in consideration when comparing.
     */
    private class ClassMember {
        private Set<ProgramElementDoc> members;

        public ClassMember(ProgramElementDoc programElementDoc) {
            members = new HashSet<ProgramElementDoc>();
            members.add(programElementDoc);
        }

        public void addMember(ProgramElementDoc programElementDoc) {
            members.add(programElementDoc);
        }

        public boolean isEqual(MethodDoc member) {
            for (Iterator<ProgramElementDoc> iter = members.iterator(); iter.hasNext(); ) {
                MethodDoc member2 = (MethodDoc) iter.next();
                if (Util.executableMembersEqual(member, member2)) {
                    members.add(member);
                        return true;
                }
            }
            return false;
        }
    }

    /**
     * A data structure that represents the class members for
     * a visible class.
     */
    private class ClassMembers {

        /**
         * The mapping class, whose inherited members are put in the
         * {@link #members} list.
         */
        private ClassDoc mappingClass;

        /**
         * List of inherited members from the mapping class.
         */
        private List<ProgramElementDoc> members = new ArrayList<ProgramElementDoc>();

        /**
         * Level/Depth of inheritance.
         */
        private String level;

        /**
         * Return list of inherited members from mapping class.
         *
         * @return List Inherited members.
         */
        public List<ProgramElementDoc> getMembers() {
            return members;
        }

        private ClassMembers(ClassDoc mappingClass, String level) {
            this.mappingClass = mappingClass;
            this.level = level;
            if (classMap.containsKey(mappingClass) &&
                        level.startsWith(classMap.get(mappingClass).level)) {
                //Remove lower level class so that it can be replaced with
                //same class found at higher level.
                purgeMemberLevelMap(getClassMembers(mappingClass, false),
                    classMap.get(mappingClass).level);
                classMap.remove(mappingClass);
                visibleClasses.remove(mappingClass);
            }
            if (!classMap.containsKey(mappingClass)) {
                classMap.put(mappingClass, this);
                visibleClasses.add(mappingClass);
            }

        }

        private void build() {
            if (kind == CONSTRUCTORS) {
                addMembers(mappingClass);
            } else {
                mapClass();
            }
        }

        private void mapClass() {
            addMembers(mappingClass);
            ClassDoc[] interfaces = mappingClass.interfaces();
            for (int i = 0; i < interfaces.length; i++) {
                String locallevel = level + 1;
                ClassMembers cm = new ClassMembers(interfaces[i], locallevel);
                cm.mapClass();
            }
            if (mappingClass.isClass()) {
                ClassDoc superclass = mappingClass.superclass();
                if (!(superclass == null || mappingClass.equals(superclass))) {
                    ClassMembers cm = new ClassMembers(superclass,
                                                       level + "c");
                    cm.mapClass();
                }
            }
        }

        /**
         * Get all the valid members from the mapping class. Get the list of
         * members for the class to be included into(ctii), also get the level
         * string for ctii. If mapping class member is not already in the
         * inherited member list and if it is visible in the ctii and not
         * overridden, put such a member in the inherited member list.
         * Adjust member-level-map, class-map.
         */
        private void addMembers(ClassDoc fromClass) {
            List<ProgramElementDoc> cdmembers = getClassMembers(fromClass, true);
            List<ProgramElementDoc> incllist = new ArrayList<ProgramElementDoc>();
            for (int i = 0; i < cdmembers.size(); i++) {
                ProgramElementDoc pgmelem = cdmembers.get(i);
                if (!found(members, pgmelem) &&
                    memberIsVisible(pgmelem) &&
                    !isOverridden(pgmelem, level)) {
                    incllist.add(pgmelem);
                }
            }
            if (incllist.size() > 0) {
                noVisibleMembers = false;
            }
            members.addAll(incllist);
            fillMemberLevelMap(getClassMembers(fromClass, false), level);
        }

        /**
         * Is given doc item visible in given classdoc in terms fo inheritance?
         * The given doc item is visible in the given classdoc if it is public
         * or protected and if it is package-private if it's containing class
         * is in the same package as the given classdoc.
         */
        private boolean memberIsVisible(ProgramElementDoc pgmdoc) {
            if (pgmdoc.containingClass().equals(classdoc)) {
                //Member is in class that we are finding visible members for.
                //Of course it is visible.
                return true;
            } else if (pgmdoc.isPrivate()) {
                //Member is in super class or implemented interface.
                //Private, so not inherited.
                return false;
            } else if (pgmdoc.isPackagePrivate()) {
                //Member is package private.  Only return true if its class is in
                //same package.
                return pgmdoc.containingClass().containingPackage().equals(
                    classdoc.containingPackage());
            } else {
                //Public members are always inherited.
                return true;
            }
        }

        /**
         * Return all available class members.
         */
        private List<ProgramElementDoc> getClassMembers(ClassDoc cd, boolean filter) {
            if (cd.isEnum() && kind == CONSTRUCTORS) {
                //If any of these rules are hit, return empty array because
                //we don't document these members ever.
                return Arrays.asList(new ProgramElementDoc[] {});
            }
            ProgramElementDoc[] members = null;
            switch (kind) {
                case ANNOTATION_TYPE_MEMBER_OPTIONAL:
                    members = cd.isAnnotationType() ?
                        filter((AnnotationTypeDoc) cd, false) :
                        new AnnotationTypeElementDoc[] {};
                    break;
                case ANNOTATION_TYPE_MEMBER_REQUIRED:
                    members = cd.isAnnotationType() ?
                        filter((AnnotationTypeDoc) cd, true) :
                        new AnnotationTypeElementDoc[] {};
                    break;
                case INNERCLASSES:
                    members = cd.innerClasses(filter);
                    break;
                case ENUM_CONSTANTS:
                    members = cd.enumConstants();
                    break;
                case FIELDS:
                    members = cd.fields(filter);
                    break;
                case CONSTRUCTORS:
                    members = cd.constructors();
                    break;
                case METHODS:
                    members = cd.methods(filter);
                    break;
                default:
                    members = new ProgramElementDoc[0];
            }
            if (nodepr) {
                return Util.excludeDeprecatedMembersAsList(members);
            }
            return Arrays.asList(members);
        }

        /**
         * Filter the annotation type members and return either the required
         * members or the optional members, depending on the value of the
         * required parameter.
         *
         * @param doc The annotation type to process.
         * @param required
         * @return the annotation type members and return either the required
         * members or the optional members, depending on the value of the
         * required parameter.
         */
        private AnnotationTypeElementDoc[] filter(AnnotationTypeDoc doc,
            boolean required) {
            AnnotationTypeElementDoc[] members = doc.elements();
            List<AnnotationTypeElementDoc> targetMembers = new ArrayList<AnnotationTypeElementDoc>();
            for (int i = 0; i < members.length; i++) {
                if ((required && members[i].defaultValue() == null) ||
                     ((!required) && members[i].defaultValue() != null)){
                    targetMembers.add(members[i]);
                }
            }
            return targetMembers.toArray(new AnnotationTypeElementDoc[]{});
        }

        private boolean found(List<ProgramElementDoc> list, ProgramElementDoc elem) {
            for (int i = 0; i < list.size(); i++) {
                ProgramElementDoc pgmelem = list.get(i);
                if (Util.matches(pgmelem, elem)) {
                    return true;
                }
            }
            return false;
        }


        /**
         * Is member overridden? The member is overridden if it is found in the
         * same level hierarchy e.g. member at level "11" overrides member at
         * level "111".
         */
        private boolean isOverridden(ProgramElementDoc pgmdoc, String level) {
            Map<?,String> memberLevelMap = (Map<?,String>) memberNameMap.get(getMemberKey(pgmdoc));
            if (memberLevelMap == null)
                return false;
            String mappedlevel = null;
            Iterator<String> iterator = memberLevelMap.values().iterator();
            while (iterator.hasNext()) {
                mappedlevel = iterator.next();
                if (mappedlevel.equals(STARTLEVEL) ||
                    (level.startsWith(mappedlevel) &&
                     !level.equals(mappedlevel))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Return true if this map has no visible members.
     *
     * @return true if this map has no visible members.
     */
    public boolean noVisibleMembers() {
        return noVisibleMembers;
    }

    private ClassMember getClassMember(MethodDoc member) {
        for (Iterator<?> iter = memberNameMap.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            if (key instanceof String) {
                continue;
            } else if (((ClassMember) key).isEqual(member)) {
                return (ClassMember) key;
            }
        }
        return new ClassMember(member);
    }

    /**
     * Return the key to the member map for the given member.
     */
    private Object getMemberKey(ProgramElementDoc doc) {
        if (doc.isConstructor()) {
            return doc.name() + ((ExecutableMemberDoc)doc).signature();
        } else if (doc.isMethod()) {
            return getClassMember((MethodDoc) doc);
        } else if (doc.isField() || doc.isEnumConstant() || doc.isAnnotationTypeElement()) {
            return doc.name();
        } else { // it's a class or interface
            String classOrIntName = doc.name();
            //Strip off the containing class name because we only want the member name.
            classOrIntName = classOrIntName.indexOf('.') != 0 ? classOrIntName.substring(classOrIntName.lastIndexOf('.'), classOrIntName.length()) : classOrIntName;
            return "clint" + classOrIntName;
        }
    }
}
