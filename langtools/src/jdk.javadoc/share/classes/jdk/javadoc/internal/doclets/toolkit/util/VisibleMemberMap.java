/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Messages;

/**
 * A data structure that encapsulates the visible members of a particular
 * type for a given class tree.  To use this data structure, you must specify
 * the type of member you are interested in (nested class, field, constructor
 * or method) and the leaf of the class tree.  The data structure will map
 * all visible members in the leaf and classes above the leaf in the tree.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 */
public class VisibleMemberMap {

    private boolean noVisibleMembers = true;

    public static enum Kind {
        INNER_CLASSES,
        ENUM_CONSTANTS,
        FIELDS,
        CONSTRUCTORS,
        METHODS,
        ANNOTATION_TYPE_FIELDS,
        ANNOTATION_TYPE_MEMBER_OPTIONAL,
        ANNOTATION_TYPE_MEMBER_REQUIRED,
        PROPERTIES;

        public static final EnumSet<Kind> summarySet = EnumSet.range(INNER_CLASSES, METHODS);
        public static final EnumSet<Kind> detailSet = EnumSet.range(ENUM_CONSTANTS, METHODS);
        public static String getNavLinkLabels(Kind kind) {
            switch (kind) {
                case INNER_CLASSES:
                    return "doclet.navNested";
                case ENUM_CONSTANTS:
                    return "doclet.navEnum";
                case FIELDS:
                    return "doclet.navField";
                case CONSTRUCTORS:
                    return "doclet.navConstructor";
                case METHODS:
                    return "doclet.navMethod";
                default:
                    throw new AssertionError("unknown kind:" + kind);
            }
        }
    }

    public static final String STARTLEVEL = "start";

    // properties aren't named setA* or getA*
    private static final Pattern GETTERSETTERPATTERN = Pattern.compile("[sg]et\\p{Upper}.*");
    /**
     * List of TypeElement objects for which ClassMembers objects are built.
     */
    private final Set<TypeElement> visibleClasses;

    /**
     * Map for each member name on to a map which contains members with same
     * name-signature. The mapped map will contain mapping for each MemberDoc
     * onto it's respecive level string.
     */
    private final Map<Object, Map<Element, String>> memberNameMap = new HashMap<>();

    /**
     * Map of class and it's ClassMembers object.
     */
    private final Map<TypeElement, ClassMembers> classMap = new HashMap<>();

    /**
     * Type whose visible members are requested.  This is the leaf of
     * the class tree being mapped.
     */
    private final TypeElement typeElement;

    /**
     * Member kind: InnerClasses/Fields/Methods?
     */
    private final Kind kind;

    /**
     * The configuration this VisibleMemberMap was created with.
     */
    private final BaseConfiguration configuration;
    private final Messages messages;
    private final Utils utils;
    private final Comparator<Element> comparator;

    private final Map<TypeElement, List<Element>> propertiesCache;
    private final Map<Element, Element> classPropertiesMap;
    private final Map<Element, GetterSetter> getterSetterMap;

    /**
     * Construct a VisibleMemberMap of the given type for the given class.
     *
     * @param typeElement whose members are being mapped.
     * @param kind the kind of member that is being mapped.
     * @param configuration the configuration to use to construct this
     * VisibleMemberMap. If the field configuration.nodeprecated is true the
     * deprecated members are excluded from the map. If the field
     * configuration.javafx is true the JavaFX features are used.
     */
    public VisibleMemberMap(TypeElement typeElement,
                            Kind kind,
                            BaseConfiguration configuration) {
        this.typeElement = typeElement;
        this.kind = kind;
        this.configuration = configuration;
        this.messages = configuration.getMessages();
        this.utils = configuration.utils;
        propertiesCache = configuration.propertiesCache;
        classPropertiesMap = configuration.classPropertiesMap;
        getterSetterMap = configuration.getterSetterMap;
        comparator  = utils.makeGeneralPurposeComparator();
        visibleClasses = new LinkedHashSet<>();
        new ClassMembers(typeElement, STARTLEVEL).build();
    }

    /**
     * Return the list of visible classes in this map.
     *
     * @return the list of visible classes in this map.
     */
    public SortedSet<TypeElement> getVisibleClasses() {
        SortedSet<TypeElement> vClasses = new TreeSet<>(comparator);
        vClasses.addAll(visibleClasses);
        return vClasses;
    }

    /**
     * Returns the property field documentation belonging to the given member.
     * @param element the member for which the property documentation is needed.
     * @return the property field documentation, null if there is none.
     */
    public Element getPropertyMemberDoc(Element element) {
        return classPropertiesMap.get(element);
    }

    /**
     * Returns the getter documentation belonging to the given property method.
     * @param propertyMethod the method for which the getter is needed.
     * @return the getter documentation, null if there is none.
     */
    public Element getGetterForProperty(Element propertyMethod) {
        return getterSetterMap.get(propertyMethod).getGetter();
    }

    /**
     * Returns the setter documentation belonging to the given property method.
     * @param propertyMethod the method for which the setter is needed.
     * @return the setter documentation, null if there is none.
     */
    public Element getSetterForProperty(Element propertyMethod) {
        return getterSetterMap.get(propertyMethod).getSetter();
    }

    /**
     * Return the package private members inherited by the class.  Only return
     * if parent is package private and not documented.
     *
     * @return the package private members inherited by the class.
     */
    private List<Element> getInheritedPackagePrivateMethods() {
        List<Element> results = new ArrayList<>();
        for (TypeElement currentClass : visibleClasses) {
            if (currentClass != typeElement &&
                utils.isPackagePrivate(currentClass) &&
                !utils.isLinkable(currentClass)) {
                // Document these members in the child class because
                // the parent is inaccessible.
                results.addAll(classMap.get(currentClass).members);
            }
        }
        return results;
    }

    /**
     * Returns a list of visible enclosed members of the type being mapped.
     * This list may also contain appended members, inherited by inaccessible
     * super types. These members are documented in the subtype when the
     * super type is not documented.
     *
     * @return a list of visible enclosed members
     */

    public List<Element> getLeafMembers() {
        List<Element> result = new ArrayList<>();
        result.addAll(classMap.get(typeElement).members);
        result.addAll(getInheritedPackagePrivateMethods());
        return result;
    }

    /**
     * Returns a list of enclosed members for the given type.
     *
     * @param typeElement the given type
     *
     * @return a list of enclosed members
     */
    public List<Element> getMembers(TypeElement typeElement) {
        return classMap.get(typeElement).members;
    }

    public boolean hasMembers(TypeElement typeElement) {
        return !classMap.get(typeElement).members.isEmpty();
    }

    private void fillMemberLevelMap(List<? extends Element> list, String level) {
        for (Element element : list) {
            Object key = getMemberKey(element);
            Map<Element, String> memberLevelMap = memberNameMap.get(key);
            if (memberLevelMap == null) {
                memberLevelMap = new HashMap<>();
                memberNameMap.put(key, memberLevelMap);
            }
            memberLevelMap.put(element, level);
        }
    }

    private void purgeMemberLevelMap(Iterable<? extends Element> list, String level) {
        for (Element element : list) {
            Object key = getMemberKey(element);
            Map<Element, String> memberLevelMap = memberNameMap.get(key);
            if (memberLevelMap != null && level.equals(memberLevelMap.get(element)))
                memberLevelMap.remove(element);
        }
    }

    /**
     * Represents a class member.
     */
    private class ClassMember {
        private Set<Element> members;

        public ClassMember(Element element) {
            members = new HashSet<>();
            members.add(element);
        }

        public boolean isEqual(ExecutableElement member) {
            for (Element element : members) {
                if (utils.executableMembersEqual(member, (ExecutableElement) element)) {
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
        private final TypeElement typeElement;

        /**
         * List of members from the mapping class.
         */
        private List<Element> members = null;

        /**
         * Level/Depth of inheritance.
         */
        private final String level;

        private ClassMembers(TypeElement mappingClass, String level) {
            this.typeElement = mappingClass;
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
            if (kind == Kind.CONSTRUCTORS) {
                addMembers(typeElement);
            } else {
                mapClass();
            }
        }

        private void mapClass() {
            addMembers(typeElement);
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                String locallevel = level + 1;
                ClassMembers cm = new ClassMembers(utils.asTypeElement(anInterface), locallevel);
                cm.mapClass();
            }
            if (utils.isClass(typeElement)) {
                TypeElement superclass = utils.getSuperClass(typeElement);
                if (!(superclass == null || typeElement.equals(superclass))) {
                    ClassMembers cm = new ClassMembers(superclass, level + "c");
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
        private void addMembers(TypeElement fromClass) {
            List<Element> result = new ArrayList<>();
            for (Element element : getClassMembers(fromClass, true)) {
                if (memberIsVisible(element)) {
                    if (!isOverridden(element, level)) {
                        if (!utils.isHidden(element)) {
                            result.add(element);
                        }
                    }
                }
            }
            if (members != null) {
                throw new AssertionError("members should not be null");
            }
            members = Collections.unmodifiableList(result);
            if (!members.isEmpty()) {
                noVisibleMembers = false;
            }
            fillMemberLevelMap(getClassMembers(fromClass, false), level);
        }

        /**
         * Is given element visible in given typeElement in terms of inheritance? The given element
         * is visible in the given typeElement if it is public or protected and if it is
         * package-private if it's containing class is in the same package as the given typeElement.
         */
        private boolean memberIsVisible(Element element) {
            if (utils.getEnclosingTypeElement(element).equals(VisibleMemberMap.this.typeElement)) {
                //Member is in class that we are finding visible members for.
                //Of course it is visible.
                return true;
            } else if (utils.isPrivate(element)) {
                //Member is in super class or implemented interface.
                //Private, so not inherited.
                return false;
            } else if (utils.isPackagePrivate(element)) {
                //Member is package private.  Only return true if its class is in
                //same package.
                return utils.containingPackage(element).equals(utils.containingPackage(VisibleMemberMap.this.typeElement));
            } else {
                //Public members are always inherited.
                return true;
            }
        }

        /**
         * Return all available class members.
         */
        private List<? extends Element> getClassMembers(TypeElement te, boolean filter) {
            if (utils.isEnum(te) && kind == Kind.CONSTRUCTORS) {
                //If any of these rules are hit, return empty array because
                //we don't document these members ever.
                return Collections.emptyList();
            }
            List<? extends Element> list;
            switch (kind) {
                case ANNOTATION_TYPE_FIELDS:
                    list = (filter)
                            ? utils.getAnnotationFields(te)
                            : utils.getAnnotationFieldsUnfiltered(te);
                    break;
                case ANNOTATION_TYPE_MEMBER_OPTIONAL:
                    list = utils.isAnnotationType(te)
                            ? filterAnnotations(te, false)
                            : Collections.emptyList();
                    break;
                case ANNOTATION_TYPE_MEMBER_REQUIRED:
                    list = utils.isAnnotationType(te)
                            ? filterAnnotations(te, true)
                            : Collections.emptyList();
                    break;
                case INNER_CLASSES:
                    List<TypeElement> xlist = filter
                            ? utils.getInnerClasses(te)
                            : utils.getInnerClassesUnfiltered(te);
                    list = new ArrayList<>(xlist);
                    break;
                case ENUM_CONSTANTS:
                    list = utils.getEnumConstants(te);
                    break;
                case FIELDS:
                    if (filter) {
                        list = utils.isAnnotationType(te)
                                ? utils.getAnnotationFields(te)
                                : utils.getFields(te);
                    } else {
                        list = utils.isAnnotationType(te)
                                ? utils.getAnnotationFieldsUnfiltered(te)
                                : utils.getFieldsUnfiltered(te);
                    }
                    break;
                case CONSTRUCTORS:
                    list = utils.getConstructors(te);
                    break;
                case METHODS:
                    list = filter ? utils.getMethods(te) : utils.getMethodsUnfiltered(te);
                    checkOnPropertiesTags(list);
                    break;
                case PROPERTIES:
                    list = properties(te, filter);
                    break;
                default:
                    list = Collections.emptyList();
            }
            // Deprected members should be excluded or not?
            if (configuration.nodeprecated) {
                return utils.excludeDeprecatedMembers(list);
            }
            return list;
        }

        /**
         * Filter the annotation type members and return either the required
         * members or the optional members, depending on the value of the
         * required parameter.
         *
         * @param typeElement The annotation type to process.
         * @param required
         * @return the annotation type members and return either the required
         * members or the optional members, depending on the value of the
         * required parameter.
         */
        private List<Element> filterAnnotations(TypeElement typeElement, boolean required) {
            List<Element> members = utils.getAnnotationMethods(typeElement);
            List<Element> targetMembers = new ArrayList<>();
            for (Element member : members) {
                ExecutableElement ee = (ExecutableElement)member;
                if ((required && ee.getDefaultValue() == null)
                        || ((!required) && ee.getDefaultValue() != null)) {
                    targetMembers.add(member);
                }
            }
            return targetMembers;
        }

        /**
         * Is member overridden? The member is overridden if it is found in the
         * same level hierarchy e.g. member at level "11" overrides member at
         * level "111".
         */
        private boolean isOverridden(Element element, String level) {
            Object key = getMemberKey(element);
            Map<?, String> memberLevelMap = (Map<?, String>) memberNameMap.get(key);
            if (memberLevelMap == null)
                return false;
            for (String mappedlevel : memberLevelMap.values()) {
                if (mappedlevel.equals(STARTLEVEL)
                        || (level.startsWith(mappedlevel)
                        && !level.equals(mappedlevel))) {
                    return true;
                }
            }
            return false;
        }

        private List<Element> properties(final TypeElement typeElement, final boolean filter) {
            final List<ExecutableElement> allMethods = filter
                    ? utils.getMethods(typeElement)
                    : utils.getMethodsUnfiltered(typeElement);
            final List<VariableElement> allFields = utils.getFieldsUnfiltered(typeElement);

            if (propertiesCache.containsKey(typeElement)) {
                return propertiesCache.get(typeElement);
            }

            final List<Element> result = new ArrayList<>();

            for (final Element propertyMethod : allMethods) {
                ExecutableElement ee = (ExecutableElement)propertyMethod;
                if (!isPropertyMethod(ee)) {
                    continue;
                }

                final ExecutableElement getter = getterForField(allMethods, ee);
                final ExecutableElement setter = setterForField(allMethods, ee);
                final VariableElement field = fieldForProperty(allFields, ee);

                addToPropertiesMap(setter, getter, ee, field);
                getterSetterMap.put(propertyMethod, new GetterSetter(getter, setter));
                result.add(ee);
            }
            propertiesCache.put(typeElement, result);
            return result;
        }

        private void addToPropertiesMap(ExecutableElement setter,
                                        ExecutableElement getter,
                                        ExecutableElement propertyMethod,
                                        VariableElement field) {
            if (field == null || utils.getDocCommentTree(field) == null) {
                addToPropertiesMap(setter, propertyMethod);
                addToPropertiesMap(getter, propertyMethod);
                addToPropertiesMap(propertyMethod, propertyMethod);
            } else {
                addToPropertiesMap(getter, field);
                addToPropertiesMap(setter, field);
                addToPropertiesMap(propertyMethod, field);
            }
        }

        private void addToPropertiesMap(Element propertyMethod,
                                        Element commentSource) {
            if (null == propertyMethod || null == commentSource) {
                return;
            }
            DocCommentTree docTree = utils.getDocCommentTree(propertyMethod);

            /* The second condition is required for the property buckets. In
             * this case the comment is at the property method (not at the field)
             * and it needs to be listed in the map.
             */
            if ((docTree == null) || propertyMethod.equals(commentSource)) {
                classPropertiesMap.put(propertyMethod, commentSource);
            }
        }

        private ExecutableElement getterForField(List<ExecutableElement> methods,
                                         ExecutableElement propertyMethod) {
            final String propertyMethodName = utils.getSimpleName(propertyMethod);
            final String fieldName = propertyMethodName.substring(0,
                            propertyMethodName.lastIndexOf("Property"));
            final String fieldNameUppercased =
                    "" + Character.toUpperCase(fieldName.charAt(0))
                                            + fieldName.substring(1);
            final String getterNamePattern;
            final String fieldTypeName = propertyMethod.getReturnType().toString();
            if ("boolean".equals(fieldTypeName)
                    || fieldTypeName.endsWith("BooleanProperty")) {
                getterNamePattern = "(is|get)" + fieldNameUppercased;
            } else {
                getterNamePattern = "get" + fieldNameUppercased;
            }

            for (ExecutableElement method : methods) {
                if (Pattern.matches(getterNamePattern, utils.getSimpleName(method))) {
                    if (method.getParameters().isEmpty() &&
                            utils.isPublic(method) || utils.isProtected(method)) {
                        return method;
                    }
                }
            }
            return null;
        }

        private ExecutableElement setterForField(List<ExecutableElement> methods,
                                         ExecutableElement propertyMethod) {
            final String propertyMethodName = utils.getSimpleName(propertyMethod);
            final String fieldName =
                    propertyMethodName.substring(0,
                            propertyMethodName.lastIndexOf("Property"));
            final String fieldNameUppercased =
                    "" + Character.toUpperCase(fieldName.charAt(0))
                                             + fieldName.substring(1);
            final String setter = "set" + fieldNameUppercased;

            for (ExecutableElement method : methods) {
                if (setter.equals(utils.getSimpleName(method))) {
                    if (method.getParameters().size() == 1
                            && method.getReturnType().getKind() == TypeKind.VOID
                            && (utils.isPublic(method) || utils.isProtected(method))) {
                        return method;
                    }
                }
            }
            return null;
        }

        private VariableElement fieldForProperty(List<VariableElement> fields, ExecutableElement property) {

            for (VariableElement field : fields) {
                final String fieldName = utils.getSimpleName(field);
                final String propertyName = fieldName + "Property";
                if (propertyName.equals(utils.getSimpleName(property))) {
                    return field;
                }
            }
            return null;
        }

        private boolean isPropertyMethod(ExecutableElement method) {
            if (!configuration.javafx) {
               return false;
            }
            if (!utils.getSimpleName(method).endsWith("Property")) {
                return false;
            }

            if (!memberIsVisible(method)) {
                return false;
            }

            if (GETTERSETTERPATTERN.matcher(utils.getSimpleName(method)).matches()) {
                return false;
            }
            if (!method.getTypeParameters().isEmpty()) {
                return false;
            }
            return method.getParameters().isEmpty()
                    && method.getReturnType().getKind() != TypeKind.VOID;
        }

        private void checkOnPropertiesTags(List<? extends Element> members) {
            for (Element e: members) {
                ExecutableElement ee = (ExecutableElement)e;
                if (utils.isIncluded(ee)) {
                    CommentHelper ch = utils.getCommentHelper(ee);
                    for (DocTree tree: utils.getBlockTags(ee)) {
                        String tagName = ch.getTagName(tree);
                        if (tagName.equals("@propertySetter")
                                || tagName.equals("@propertyGetter")
                                || tagName.equals("@propertyDescription")) {
                            if (!isPropertyGetterOrSetter(members, ee)) {
                                messages.warning(ch.getDocTreePath(tree),
                                        "doclet.javafx_tag_misuse");
                            }
                            break;
                        }
                    }
                }
            }
        }

        private boolean isPropertyGetterOrSetter(List<? extends Element> members,
                                                 ExecutableElement method) {
            String propertyName = utils.propertyName(method);
            if (!propertyName.isEmpty()) {
                String propertyMethodName = propertyName + "Property";
                for (Element member: members) {
                    if (utils.getSimpleName(member).equals(propertyMethodName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public class GetterSetter {
        private final Element getter;
        private final Element setter;

        public GetterSetter(Element getter, Element setter) {
            this.getter = getter;
            this.setter = setter;
        }

        public Element getGetter() {
            return getter;
        }

        public Element getSetter() {
            return setter;
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

    private ClassMember getClassMember(ExecutableElement member) {
        for (Object key : memberNameMap.keySet()) {
            if (key instanceof String) {
                continue;
            }
            if (((ClassMember) key).isEqual(member)) {
                return (ClassMember) key;
            }
        }
        return new ClassMember(member);
    }

    /**
     * Return the key to the member map for the given member.
     */
    private Object getMemberKey(Element element) {
        if (utils.isConstructor(element)) {
            return utils.getSimpleName(element) + utils.flatSignature((ExecutableElement)element);
        } else if (utils.isMethod(element)) {
            return getClassMember((ExecutableElement) element);
        } else if (utils.isField(element) || utils.isEnumConstant(element) || utils.isAnnotationType(element)) {
            return utils.getSimpleName(element);
        } else { // it's a class or interface
            String classOrIntName = utils.getSimpleName(element);
            //Strip off the containing class name because we only want the member name.
            classOrIntName = classOrIntName.indexOf('.') != 0
                    ? classOrIntName.substring(classOrIntName.lastIndexOf('.'))
                    : classOrIntName;
            return "clint" + classOrIntName;
        }
    }
}
