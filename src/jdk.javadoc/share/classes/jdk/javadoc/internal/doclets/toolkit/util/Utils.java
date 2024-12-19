/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Documented;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.text.CollationKey;
import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.RequiresDirective;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.lang.model.util.TypeKindVisitor9;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ProvidesTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SpecTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UsesTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils.DocCommentInfo;
import jdk.javadoc.internal.doclets.toolkit.Resources;

import static javax.lang.model.element.ElementKind.*;
import static javax.lang.model.type.TypeKind.*;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 * Utilities Class for Doclets.
 */
public class Utils {
    public final BaseConfiguration configuration;
    private final BaseOptions options;
    private final Resources resources;
    public final DocTrees docTrees;
    public final Elements elementUtils;
    public final Types typeUtils;
    public final Comparators comparators;
    private final JavaScriptScanner javaScriptScanner;
    private final DocFinder docFinder = newDocFinder();
    private final TypeElement JAVA_LANG_OBJECT;

    public Utils(BaseConfiguration c) {
        configuration = c;
        options = configuration.getOptions();
        resources = configuration.getDocResources();
        elementUtils = c.docEnv.getElementUtils();
        JAVA_LANG_OBJECT = elementUtils.getTypeElement("java.lang.Object");
        typeUtils = c.docEnv.getTypeUtils();
        docTrees = c.docEnv.getDocTrees();
        javaScriptScanner = c.isAllowScriptInComments() ? null : new JavaScriptScanner();
        comparators = new Comparators(this);
    }

    // our own little symbol table
    private final Map<String, TypeMirror> symtab = new HashMap<>();

    public TypeMirror getSymbol(String signature) {
        return symtab.computeIfAbsent(signature, s -> {
            var typeElement = elementUtils.getTypeElement(s);
            return typeElement == null ? null : typeElement.asType();
        });
    }

    public TypeMirror getObjectType() {
        return getSymbol("java.lang.Object");
    }

    public TypeMirror getThrowableType() {
        return getSymbol("java.lang.Throwable");
    }

    public TypeMirror getSerializableType() {
        return getSymbol("java.io.Serializable");
    }

    public TypeMirror getExternalizableType() {
        return getSymbol("java.io.Externalizable");
    }

    public TypeMirror getDeprecatedType() {
        return getSymbol("java.lang.Deprecated");
    }

    public TypeMirror getFunctionalInterface() {
        return getSymbol("java.lang.FunctionalInterface");
    }

    /**
     * According to <cite>The Java Language Specification</cite>,
     * all the outer classes and static inner classes are core classes.
     */
    public boolean isCoreClass(TypeElement e) {
        return getEnclosingTypeElement(e) == null || isStatic(e);
    }

    public Location getLocationForPackage(PackageElement pd) {
        ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(pd);

        if (mdle == null)
            return defaultLocation();

        return getLocationForModule(mdle);
    }

    public Location getLocationForModule(ModuleElement mdle) {
        Location loc = configuration.workArounds.getLocationForModule(mdle);
        if (loc != null)
            return loc;

        return defaultLocation();
    }

    private Location defaultLocation() {
        JavaFileManager fm = configuration.docEnv.getJavaFileManager();
        return fm.hasLocation(StandardLocation.SOURCE_PATH)
                ? StandardLocation.SOURCE_PATH
                : StandardLocation.CLASS_PATH;
    }

    public boolean isAnnotated(TypeMirror e) {
        return !e.getAnnotationMirrors().isEmpty();
    }

    public boolean isAnnotationInterface(Element e) {
        return e.getKind() == ANNOTATION_TYPE;
    }

    // Note that e.getKind().isClass() is not the same as e.getKind() == CLASS
    public boolean isClass(Element e) {
        return e.getKind().isClass();
    }

    // Note that e.getKind().isInterface() is not the same as e.getKind() == INTERFACE
    // See Also: isPlainInterface(Element)
    public boolean isInterface(Element e) {
        return e.getKind().isInterface();
    }

    public boolean isConstructor(Element e) {
         return e.getKind() == CONSTRUCTOR;
    }

    public boolean isEnum(Element e) {
        return e.getKind() == ENUM;
    }

    public boolean isField(Element e) {
        return e.getKind() == FIELD;
    }

    public boolean isPlainInterface(Element e) {
        return e.getKind() == INTERFACE;
    }

    public boolean isMethod(Element e) {
        return e.getKind() == METHOD;
    }

    public boolean isModule(Element e) {
        return e.getKind() == ElementKind.MODULE;
    }

    public boolean isPackage(Element e) {
        return e.getKind() == ElementKind.PACKAGE;
    }

    public boolean isAbstract(Element e) {
        return e.getModifiers().contains(Modifier.ABSTRACT);
    }

    public boolean isDefault(Element e) {
        return e.getModifiers().contains(Modifier.DEFAULT);
    }

    public boolean isFinal(Element e) {
        return e.getModifiers().contains(Modifier.FINAL);
    }

    /*
     * A contemporary JLS term for "package private" or "default access" is
     * "package access". For example: "a member is declared with package
     * access" or "a member has package access".
     *
     * This is to avoid confusion with unrelated _default_ methods which
     * appeared in JDK 8.
     */
    public boolean isPackagePrivate(Element e) {
        var m = e.getModifiers();
        return !m.contains(Modifier.PUBLIC)
                && !m.contains(Modifier.PROTECTED)
                && !m.contains(Modifier.PRIVATE);
    }

    public boolean isPrivate(Element e) {
        return e.getModifiers().contains(Modifier.PRIVATE);
    }

    public boolean isProtected(Element e) {
        return e.getModifiers().contains(Modifier.PROTECTED);
    }

    public boolean isPublic(Element e) {
        return e.getModifiers().contains(Modifier.PUBLIC);
    }

    public boolean isProperty(String name) {
        return options.javafx() && name.endsWith("Property");
    }

    public String getPropertyName(String name) {
        return isProperty(name)
                ? name.substring(0, name.length() - "Property".length())
                : name;
    }

    public String getPropertyLabel(String name) {
        return name.substring(0, name.lastIndexOf("Property"));
    }

    public boolean isOverviewElement(Element e) {
        return e.getKind() == ElementKind.OTHER;
    }

    public boolean isStatic(Element e) {
        return e.getModifiers().contains(Modifier.STATIC);
    }

    public boolean isSerializable(TypeElement e) {
        return typeUtils.isSubtype(e.asType(), getSerializableType());
    }

    public boolean isExternalizable(TypeElement e) {
        return typeUtils.isSubtype(e.asType(), getExternalizableType());
    }

    public boolean isRecord(TypeElement e) {
        return e.getKind() == ElementKind.RECORD;
    }

    public boolean isCanonicalRecordConstructor(ExecutableElement ee) {
        TypeElement te = (TypeElement) ee.getEnclosingElement();
        List<? extends RecordComponentElement> stateComps = te.getRecordComponents();
        List<? extends VariableElement> params = ee.getParameters();
        if (stateComps.size() != params.size()) {
            return false;
        }

        Iterator<? extends RecordComponentElement> stateIter = stateComps.iterator();
        Iterator<? extends VariableElement> paramIter = params.iterator();
        while (paramIter.hasNext() && stateIter.hasNext()) {
            VariableElement param = paramIter.next();
            RecordComponentElement comp = stateIter.next();
            if (!Objects.equals(param.getSimpleName(), comp.getSimpleName())
                    || !typeUtils.isSameType(param.asType(), comp.asType())) {
                return false;
            }
        }

        return true;
    }

    public SortedSet<VariableElement> serializableFields(TypeElement aclass) {
        return configuration.workArounds.getSerializableFields(aclass);
    }

    public SortedSet<ExecutableElement> serializationMethods(TypeElement aclass) {
        return configuration.workArounds.getSerializationMethods(aclass);
    }

    public boolean definesSerializableFields(TypeElement aclass) {
        return configuration.workArounds.definesSerializableFields( aclass);
    }

    public boolean isFunctionalInterface(AnnotationMirror amirror) {
        return typeUtils.isSameType(amirror.getAnnotationType(), getFunctionalInterface()) &&
                configuration.docEnv.getSourceVersion()
                        .compareTo(SourceVersion.RELEASE_8) >= 0;
    }

    public boolean isFunctionalInterface(TypeElement typeElement) {
        return typeElement.getAnnotationMirrors().stream()
                .anyMatch(this::isFunctionalInterface);
    }

    public boolean isUndocumentedEnclosure(TypeElement enclosingTypeElement) {
        return (isPackagePrivate(enclosingTypeElement) || isPrivate(enclosingTypeElement)
                    || hasHiddenTag(enclosingTypeElement))
                && !isLinkable(enclosingTypeElement);
    }

    public boolean isNonThrowableClass(TypeElement te) {
        return te.getKind() == CLASS && !isThrowable(te);
    }

    public boolean isThrowable(TypeElement te) {
        return te.getKind() == CLASS && typeUtils.isSubtype(te.asType(), getThrowableType());
    }

    public boolean isExecutableElement(Element e) {
        return e.getKind().isExecutable();
    }

    public boolean isVariableElement(Element e) {
        return e.getKind().isVariable();
    }

    public boolean isTypeElement(Element e) {
        return e.getKind().isDeclaredType();
    }

    /**
     * Get the signature of an executable element with qualified parameter types
     * in the context of type element {@code site}.
     * For instance, for a method {@code mymethod(String x, int y)},
     * it will return {@code (java.lang.String,int)}.
     *
     * @param e the executable element
     * @param site the contextual site
     * @return String signature with qualified parameter types
     */
    public String signature(ExecutableElement e, TypeElement site) {
        return makeSignature(e, site, true);
    }

    /**
     * Get the flat signature of an executable element with simple (unqualified)
     * parameter types in the context of type element {@code site}.
     * For instance, for a method {@code mymethod(String x, int y)},
     * it will return {@code (String, int)}.
     *
     * @param e the executable element
     * @param site the contextual site
     * @return signature with simple (unqualified) parameter types
     */
    public String flatSignature(ExecutableElement e, TypeElement site) {
        return makeSignature(e, site, false);
    }

    public String makeSignature(ExecutableElement e, TypeElement site, boolean full) {
        return makeSignature(e, site, full, false);
    }

    public String makeSignature(ExecutableElement e, TypeElement site, boolean full, boolean ignoreTypeParameters) {
        StringBuilder result = new StringBuilder();
        result.append("(");
        ExecutableType executableType = asInstantiatedMethodType(site, e);
        Iterator<? extends TypeMirror> iterator = executableType.getParameterTypes().iterator();
        while (iterator.hasNext()) {
            TypeMirror type = iterator.next();
            result.append(getTypeSignature(type, full, ignoreTypeParameters));
            if (iterator.hasNext()) {
                result.append(", ");
            }
        }
        if (e.isVarArgs()) {
            int len = result.length();
            result.replace(len - 2, len, "...");
        }
        result.append(")");
        return result.toString();
    }

    public String getTypeSignature(TypeMirror t, boolean qualifiedName, boolean noTypeParameters) {
        return new SimpleTypeVisitor14<StringBuilder, Void>() {
            final StringBuilder sb = new StringBuilder();

            @Override
            public StringBuilder visitArray(ArrayType t, Void p) {
                TypeMirror componentType = t.getComponentType();
                visit(componentType);
                sb.append("[]");
                return sb;
            }

            @Override
            public StringBuilder visitDeclared(DeclaredType t, Void p) {
                Element e = t.asElement();
                sb.append(qualifiedName ? getFullyQualifiedName(e) : getSimpleName(e));
                List<? extends TypeMirror> typeArguments = t.getTypeArguments();
                if (typeArguments.isEmpty() || noTypeParameters) {
                    return sb;
                }
                sb.append("<");
                Iterator<? extends TypeMirror> iterator = typeArguments.iterator();
                while (iterator.hasNext()) {
                    TypeMirror ta = iterator.next();
                    visit(ta);
                    if (iterator.hasNext()) {
                        sb.append(", ");
                    }
                }
                sb.append(">");
                return sb;
            }

            @Override
            public StringBuilder visitPrimitive(PrimitiveType t, Void p) {
                sb.append(t.getKind().toString().toLowerCase(Locale.ROOT));
                return sb;
            }

            @Override
            public StringBuilder visitTypeVariable(TypeVariable t, Void p) {
                Element e = t.asElement();
                sb.append(qualifiedName ? getFullyQualifiedName(e, false) : getSimpleName(e));
                return sb;
            }

            @Override
            public StringBuilder visitWildcard(WildcardType t, Void p) {
                sb.append("?");
                TypeMirror upperBound = t.getExtendsBound();
                if (upperBound != null) {
                    sb.append(" extends ");
                    visit(upperBound);
                }
                TypeMirror superBound = t.getSuperBound();
                if (superBound != null) {
                    sb.append(" super ");
                    visit(superBound);
                }
                return sb;
            }

            @Override
            protected StringBuilder defaultAction(TypeMirror e, Void p) {
                return sb.append(e);
            }
        }.visit(t).toString();
    }

    public boolean isArrayType(TypeMirror t) {
        return t.getKind() == ARRAY;
    }

    public boolean isDeclaredType(TypeMirror t) {
        return t.getKind() == DECLARED;
    }

    public boolean isTypeParameterElement(Element e) {
        return e.getKind() == TYPE_PARAMETER;
    }

    public boolean isTypeVariable(TypeMirror t) {
        return t.getKind() == TYPEVAR;
    }

    public boolean isVoid(TypeMirror t) {
        return t.getKind() == VOID;
    }

    public boolean ignoreBounds(TypeMirror bound) {
        return typeUtils.isSameType(bound, getObjectType()) && !isAnnotated(bound);
    }

    /*
     * a direct port of TypeVariable.getBounds
     */
    public List<? extends TypeMirror> getBounds(TypeParameterElement tpe) {
        List<? extends TypeMirror> bounds = tpe.getBounds();
        if (!bounds.isEmpty()) {
            TypeMirror upperBound = bounds.get(bounds.size() - 1);
            if (ignoreBounds(upperBound)) {
                return List.of();
            }
        }
        return bounds;
    }

    /**
     * Returns the TypeMirror of the ExecutableElement if it is a method, or null
     * if it is a constructor.
     * @param site the contextual type
     * @param ee the ExecutableElement
     * @return the return type
     */
    public TypeMirror getReturnType(TypeElement site, ExecutableElement ee) {
        return ee.getKind() == CONSTRUCTOR ? null : asInstantiatedMethodType(site, ee).getReturnType();
    }

    /**
     * Returns the ExecutableType corresponding to the type of the method declaration seen as a
     * member of a given declared type. This might cause type-variable substitution to kick in.
     * @param site the contextual type.
     * @param ee the method declaration.
     * @return the instantiated method type.
     */
    public ExecutableType asInstantiatedMethodType(TypeElement site, ExecutableElement ee) {
        return shouldInstantiate(site, ee) ?
                (ExecutableType)typeUtils.asMemberOf((DeclaredType)site.asType(), ee) :
                (ExecutableType)ee.asType();
    }

    /**
     * Returns the TypeMirror corresponding to the type of the field declaration seen as a
     * member of a given declared type. This might cause type-variable substitution to kick in.
     * @param site the contextual type.
     * @param ve the field declaration.
     * @return the instantiated field type.
     */
    public TypeMirror asInstantiatedFieldType(TypeElement site, VariableElement ve) {
        return shouldInstantiate(site, ve) ?
                typeUtils.asMemberOf((DeclaredType)site.asType(), ve) :
                ve.asType();
    }

    /*
     * We should not instantiate if (i) there's no contextual type declaration, (ii) the declaration
     * to which the member belongs to is the same as the one under consideration, (iii) if the
     * declaration to which the member belongs to is not generic.
     */
    private boolean shouldInstantiate(TypeElement site, Element e) {
        return site != null &&
                site != e.getEnclosingElement() &&
               !((DeclaredType)e.getEnclosingElement().asType()).getTypeArguments().isEmpty();
    }

    /*
     * The record is used to pass the method along with the type where that method is visible.
     * Passing the type explicitly allows to preserve a complete type information, including
     * parameterization.
     */
    public record OverrideInfo(ExecutableElement overriddenMethod,
                               DeclaredType overriddenMethodOwner) { }

    /*
     * Returns the closest superclass (not the superinterface) that contains
     * a method that is both:
     *
     *   - overridden by the specified method, and
     *   - is not itself a *simple* override
     *
     * If no such class can be found, returns null.
     *
     * If the specified method belongs to an interface, the only considered
     * superclass is java.lang.Object no matter how many other interfaces
     * that interface extends.
     */
    public OverrideInfo overriddenMethod(ExecutableElement method) {
        var t = method.getEnclosingElement().asType();
        // in this context, consider java.lang.Object to be the superclass of an interface
        while (true) {
            var supertypes = typeUtils.directSupertypes(t);
            if (supertypes.isEmpty()) {
                // reached the top of the hierarchy
                assert typeUtils.isSameType(getObjectType(), t);
                return null;
            }
            t = supertypes.get(0);
            // if non-empty, the first element is always the superclass
            var te = (TypeElement) ((DeclaredType) t).asElement();
            assert te.getKind().isClass();
            VisibleMemberTable vmt = configuration.getVisibleMemberTable(te);
            for (Element e : vmt.getMembers(VisibleMemberTable.Kind.METHODS)) {
                var ee = (ExecutableElement) e;
                if (elementUtils.overrides(method, ee, (TypeElement) method.getEnclosingElement()) &&
                        !isSimpleOverride(ee)) {
                    return new OverrideInfo(ee, (DeclaredType) t);
                }
            }
        }
    }

    public SortedSet<TypeElement> getTypeElementsAsSortedSet(Iterable<TypeElement> typeElements) {
        SortedSet<TypeElement> set = new TreeSet<>(comparators.generalPurposeComparator());
        typeElements.forEach(set::add);
        return set;
    }

    public List<? extends SerialDataTree> getSerialDataTrees(ExecutableElement member) {
        return getBlockTags(member, SERIAL_DATA, SerialDataTree.class);
    }

    public FileObject getFileObject(TypeElement te) {
        return docTrees.getPath(te).getCompilationUnit().getSourceFile();
    }

    public TypeMirror getDeclaredType(TypeElement enclosing, TypeMirror target) {
        return getDeclaredType(List.of(), enclosing, target);
    }

    /**
     * Finds the declaration of the enclosing's type parameter.
     *
     * @param values
     * @param enclosing a TypeElement whose type arguments  we desire
     * @param target the TypeMirror of the type as described by the enclosing
     * @return
     */
    public TypeMirror getDeclaredType(Collection<TypeMirror> values,
                                      TypeElement enclosing, TypeMirror target) {
        TypeElement targetElement = asTypeElement(target);
        List<? extends TypeParameterElement> targetTypeArgs = targetElement.getTypeParameters();
        if (targetTypeArgs.isEmpty()) {
            return target;
        }

        List<? extends TypeParameterElement> enclosingTypeArgs = enclosing.getTypeParameters();
        List<TypeMirror> targetTypeArgTypes = new ArrayList<>(targetTypeArgs.size());

        if (enclosingTypeArgs.isEmpty()) {
            for (TypeMirror te : values) {
                List<? extends TypeMirror> typeArguments = ((DeclaredType)te).getTypeArguments();
                if (typeArguments.size() >= targetTypeArgs.size()) {
                    for (int i = 0 ; i < targetTypeArgs.size(); i++) {
                        targetTypeArgTypes.add(typeArguments.get(i));
                    }
                    break;
                }
            }
            // we found no matches in the hierarchy
            if (targetTypeArgTypes.isEmpty()) {
                return target;
            }
        } else {
            if (targetTypeArgs.size() > enclosingTypeArgs.size()) {
                return target;
            }
            for (int i = 0; i < targetTypeArgs.size(); i++) {
                TypeParameterElement tpe = enclosingTypeArgs.get(i);
                targetTypeArgTypes.add(tpe.asType());
            }
        }
        TypeMirror dt = typeUtils.getDeclaredType(targetElement,
                targetTypeArgTypes.toArray(new TypeMirror[targetTypeArgTypes.size()]));
        return dt;
    }

    /**
     * Returns all the implemented superinterfaces of a given type,
     * in the case of classes, include all the superinterfaces of
     * the supertype. The superinterfaces are collected before the
     * superinterfaces of the supertype.
     *
     * @param  te the type element to get the superinterfaces for.
     * @return the list of superinterfaces.
     */
    public Set<TypeMirror> getAllInterfaces(TypeElement te) {
        Set<TypeMirror> results = new LinkedHashSet<>();
        addSuperInterfaces(te.asType(), results, new HashSet<>());
        assert noSameTypes(results);
        return results;
    }

    private boolean noSameTypes(Set<TypeMirror> results) {
        for (TypeMirror t1 : results) {
            for (TypeMirror t2 : results) {
                if (t1 == t2) {
                    continue;
                }
                if (typeUtils.isSameType(t1, t2)) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * Instances of TypeMirror should be compared using
     * Types.isSameType. However, there's no hash function
     * consistent with that method. This makes it problematic to
     * store TypeMirror in a collection that relies on hashing.
     *
     * To work around that, along with accumulating the resulting set of type
     * mirrors, we also maintain a set of elements that correspond to those
     * type mirrors. Element provides strong equals and hashCode. We only add
     * a type mirror into the result set if we don't already have an element
     * that corresponds to this type mirror in the set of seen elements.
     *
     * Although this might seem wrong, as an instance of Element corresponds
     * to multiple instances of TypeMirror (one-to-many), in an
     * inheritance hierarchy the correspondence is effectively one-to-one.
     * This is because it is NOT possible for a type to be a subtype
     * of different generic invocations of the same supertype; e.g.,
     *
     *     interface X extends G<A>, G<B>
     */
    private void addSuperInterfaces(TypeMirror type, Set<TypeMirror> results, Set<Element> visited) {
        TypeMirror superType = null;
        for (TypeMirror t : typeUtils.directSupertypes(type)) {
            if (typeUtils.isSameType(t, getObjectType()))
                continue;
            TypeElement e = asTypeElement(t);
            if (isPlainInterface(e)) {
                if (!visited.add(e)) {
                    continue; // seen it before
                }
                if (isPublic(e) || isLinkable(e)) {
                    results.add(t);
                }
                addSuperInterfaces(t, results, visited);
            } else {
                // there can be at most one superclass and it is not null
                assert superType == null && t != null : superType;
                // Save the supertype for later.
                superType = t;
            }
        }
        // Collect the super-interfaces of the supertype.
        if (superType != null)
            addSuperInterfaces(superType, results, visited);
    }

    /**
     * Returns true if {@code type} or any of its enclosing types has non-empty type arguments.
     * @param type the type
     * @return {@code true} if type arguments were found
     */
    public boolean isGenericType(TypeMirror type) {
        while (type instanceof DeclaredType dt) {
            if (!dt.getTypeArguments().isEmpty()) {
                return true;
            }
            type = dt.getEnclosingType();
        }
        return false;
    }

    /**
     * Given an annotation, return true if it should be documented and false
     * otherwise.
     *
     * @param annotation the annotation to check.
     *
     * @return true return true if it should be documented and false otherwise.
     */
    public boolean isDocumentedAnnotation(TypeElement annotation) {
        for (AnnotationMirror anno : annotation.getAnnotationMirrors()) {
            if (getFullyQualifiedName(anno.getAnnotationType().asElement()).equals(
                    Documented.class.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if this class is linkable and false if we can't link to it.
     *
     * <p>
     * <b>NOTE:</b>  You can only link to external classes if they are public or
     * protected.
     *
     * @return true if this class is linkable and false if we can't link to the
     * desired class.
     */
    public boolean isLinkable(TypeElement typeElem) {
        return
            typeElem != null &&
            ((isIncluded(typeElem) && configuration.isGeneratedDoc(typeElem) &&
                    !hasHiddenTag(typeElem)) ||
            (configuration.extern.isExternal(typeElem) &&
                    (isPublic(typeElem) || isProtected(typeElem))));
    }

    /**
     * Returns true if an element is linkable in the context of a given type element.
     *
     * If the element is a type element, it delegates to {@link #isLinkable(TypeElement)}.
     * Otherwise, the element is linkable if any of the following are true:
     * <ul>
     * <li>it is "included" (see {@link jdk.javadoc.doclet})
     * <li>it is inherited from an undocumented supertype
     * <li>it is a public or protected member of an external API
     * </ul>
     *
     * @param typeElem the type element
     * @param elem the element
     * @return whether or not the element is linkable
     */
    public boolean isLinkable(TypeElement typeElem, Element elem) {
        if (isTypeElement(elem)) {
            return isLinkable((TypeElement) elem); // defer to existing behavior
        }

        if (isIncluded(elem) && !hasHiddenTag(elem)) {
            return true;
        }

        // Allow for the behavior that members of undocumented supertypes
        // may be included in documented types
        if (isUndocumentedEnclosure(getEnclosingTypeElement(elem))) {
            return true;
        }

        // Allow for external members
        return isLinkable(typeElem)
                    && configuration.extern.isExternal(typeElem)
                    && (isPublic(elem) || isProtected(elem));
    }

    /**
     * Return this type as a {@code TypeElement} if it represents a class
     * interface or annotation.  Array dimensions are ignored.
     * If this type {@code ParameterizedType} or {@code WildcardType}, return
     * the {@code TypeElement} of the type's erasure.  If this is an
     * annotation, return this as a {@code TypeElement}.
     * If this is a primitive type, return null.
     *
     * @return the {@code TypeElement} of this type,
     *         or null if it is a primitive type.
     */
    public TypeElement asTypeElement(TypeMirror t) {
        return new SimpleTypeVisitor14<TypeElement, Void>() {

            @Override
            public TypeElement visitDeclared(DeclaredType t, Void p) {
                return (TypeElement) t.asElement();
            }

            @Override
            public TypeElement visitArray(ArrayType t, Void p) {
                return visit(t.getComponentType());
            }

            @Override
            public TypeElement visitTypeVariable(TypeVariable t, Void p) {
               /* TODO, this may not be an optimal fix.
                * if we have an annotated type @DA T, then erasure returns a
                * none, in this case we use asElement instead.
                */
                if (isAnnotated(t)) {
                    return visit(typeUtils.asElement(t).asType());
                }
                return visit(typeUtils.erasure(t));
            }

            @Override
            public TypeElement visitWildcard(WildcardType t, Void p) {
                return visit(typeUtils.erasure(t));
            }

            @Override
            public TypeElement visitError(ErrorType t, Void p) {
                return (TypeElement)t.asElement();
            }

            @Override
            protected TypeElement defaultAction(TypeMirror e, Void p) {
                return super.defaultAction(e, p);
            }
        }.visit(t);
    }

    public TypeMirror getComponentType(TypeMirror t) {
        while (isArrayType(t)) {
            t = ((ArrayType) t).getComponentType();
        }
        return t;
    }

    /**
     * Return the type's dimension information, as a string.
     * <p>
     * For example, a two-dimensional array of String returns "{@code [][]}".
     *
     * @return the type's dimension information as a string.
     */
    public String getDimension(TypeMirror t) {
        return new SimpleTypeVisitor14<String, Void>() {
            StringBuilder dimension = new StringBuilder();
            @Override
            public String visitArray(ArrayType t, Void p) {
                dimension.append("[]");
                return visit(t.getComponentType());
            }

            @Override
            protected String defaultAction(TypeMirror e, Void p) {
                return dimension.toString();
            }

        }.visit(t);
    }

    private boolean checkType(TypeElement te) {
        return isInterface(te) || typeUtils.isSameType(te.asType(), getObjectType());
    }

    public TypeElement getFirstVisibleSuperClassAsTypeElement(TypeElement te) {
        if (checkType(te)) {
            return null;
        }
        TypeMirror firstVisibleSuperClass = getFirstVisibleSuperClass(te);
        return firstVisibleSuperClass == null ? null : asTypeElement(firstVisibleSuperClass);
    }

    /**
     * Given a class, return the closest visible superclass.
     * @param type the TypeMirror to be interrogated
     * @return  the closest visible superclass.  Return null if it cannot
     *          be found.
     */
    public TypeMirror getFirstVisibleSuperClass(TypeMirror type) {
        // TODO: this computation should be eventually delegated to VisibleMemberTable
        Set<TypeElement> alreadySeen = null;
        // create a set iff assertions are enabled, to assert that no class
        // appears more than once in a superclass hierarchy
        assert (alreadySeen = new HashSet<>()) != null;
        for (var t = type; ;) {
            var supertypes = typeUtils.directSupertypes(t);
            if (supertypes.isEmpty()) { // end of hierarchy
                return null;
            }
            t = supertypes.get(0); // if non-empty, the first element is always the superclass
            var te = asTypeElement(t);
            assert alreadySeen.add(te); // it should be the first time we see `te`
            if (!hasHiddenTag(te) && (isPublic(te) || isLinkable(te))) {
                return t;
            }
        }
    }

    /**
     * Given a class, return the closest visible superclass.
     *
     * @param te the TypeElement to be interrogated
     * @return the closest visible superclass.  Return null if it cannot
     *         be found.
     */
    public TypeMirror getFirstVisibleSuperClass(TypeElement te) {
        return getFirstVisibleSuperClass(te.asType());
    }

    /**
     * Returns the name of the kind of a type element (Class, Interface, etc.).
     *
     * @param te the type element
     * @param lowerCaseOnly true if you want the name returned in lower case;
     *                      if false, the first letter of the name is capitalized
     * @return the name
     */
    public String getTypeElementKindName(TypeElement te, boolean lowerCaseOnly) {
        String kindName = switch (te.getKind()) {
            case ANNOTATION_TYPE ->
                    "doclet.AnnotationType";
            case ENUM ->
                    "doclet.Enum";
            case INTERFACE ->
                    "doclet.Interface";
            case RECORD ->
                    "doclet.RecordClass";
            case CLASS ->
                    isThrowable(te) ? "doclet.ExceptionClass"
                    : "doclet.Class";
            default ->
                    throw new IllegalArgumentException(te.getKind().toString());
        };
        kindName = lowerCaseOnly ? toLowerCase(kindName) : kindName;
        return kindNameMap.computeIfAbsent(kindName, resources::getText);
    }

    private final Map<String, String> kindNameMap = new HashMap<>();

    public String getTypeName(TypeMirror t, boolean fullyQualified) {
        return new SimpleTypeVisitor14<String, Void>() {

            @Override
            public String visitArray(ArrayType t, Void p) {
                return visit(t.getComponentType());
            }

            @Override
            public String visitDeclared(DeclaredType t, Void p) {
                TypeElement te = asTypeElement(t);
                return fullyQualified
                        ? te.getQualifiedName().toString()
                        : getSimpleName(te);
            }

            @Override
            public String visitExecutable(ExecutableType t, Void p) {
                return t.toString();
            }

            @Override
            public String visitPrimitive(PrimitiveType t, Void p) {
                return t.toString();
            }

            @Override
            public String visitTypeVariable(javax.lang.model.type.TypeVariable t, Void p) {
                return getSimpleName(t.asElement());
            }

            @Override
            public String visitWildcard(javax.lang.model.type.WildcardType t, Void p) {
                return t.toString();
            }

            @Override
            protected String defaultAction(TypeMirror e, Void p) {
                return e.toString();
            }
        }.visit(t);
    }

    /**
     * Replace all tabs in a string with the appropriate number of spaces.
     * The string may be a multi-line string.
     * @param text the text for which the tabs should be expanded
     * @return the text with all tabs expanded
     */
    public String replaceTabs(String text) {
        if (!text.contains("\t"))
            return text;

        final int tabLength = options.sourceTabSize();
        final String whitespace = " ".repeat(tabLength);
        final int textLength = text.length();
        StringBuilder result = new StringBuilder(textLength);
        int pos = 0;
        int lineLength = 0;
        for (int i = 0; i < textLength; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\n', '\r' -> lineLength = 0;

                case '\t' -> {
                    result.append(text, pos, i);
                    int spaceCount = tabLength - lineLength % tabLength;
                    result.append(whitespace, 0, spaceCount);
                    lineLength += spaceCount;
                    pos = i + 1;
                }

                default -> lineLength++;
            }
        }
        result.append(text, pos, textLength);
        return result.toString();
    }

    /**
     * Returns a locale independent lower cased String. That is, it
     * always uses US locale, this is a clone of the one in StringUtils.
     * @param s to convert
     * @return converted String
     */
    public static String toLowerCase(String s) {
        return s.toLowerCase(Locale.US);
    }

    /**
     * Return true if the given Element is deprecated.
     *
     * @param e the Element to check.
     * @return true if the given Element is deprecated.
     */
    public boolean isDeprecated(Element e) {
        if (isPackage(e)) {
            return configuration.workArounds.isDeprecated0(e);
        }
        return elementUtils.isDeprecated(e);
    }

    /**
     * Returns true if the given Element is deprecated for removal.
     *
     * @param e the Element to check.
     * @return true if the given Element is deprecated for removal.
     */
    public boolean isDeprecatedForRemoval(Element e) {
        Object forRemoval = getAnnotationElement(e, getDeprecatedType(), "forRemoval");
        return forRemoval != null && (boolean) forRemoval;
    }

    /**
     * Returns the value of the {@code Deprecated.since} element if it is set on the given Element.
     *
     * @param e the Element to check.
     * @return the Deprecated.since value for e, or null.
     */
    public String getDeprecatedSince(Element e) {
        return (String) getAnnotationElement(e, getDeprecatedType(), "since");
    }

    /**
     * Returns the value of the internal {@code PreviewFeature.feature} element.
     *
     * @param e the Element to check
     * @return the PreviewFeature.feature for e, or null
     */
    public Object getPreviewFeature(Element e) {
        return getAnnotationElement(e, getSymbol("jdk.internal.javac.PreviewFeature"), "feature");
    }

    /**
     * Returns the Deprecated annotation element value of the given element, or null.
     */
    private Object getAnnotationElement(Element e, TypeMirror annotationType, String annotationElementName) {
        List<? extends AnnotationMirror> annotationList = e.getAnnotationMirrors();
        for (AnnotationMirror anno : annotationList) {
            if (typeUtils.isSameType(anno.getAnnotationType(), annotationType)) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> pairs = anno.getElementValues();
                if (!pairs.isEmpty()) {
                    for (ExecutableElement element : pairs.keySet()) {
                        if (element.getSimpleName().contentEquals(annotationElementName)) {
                            return (pairs.get(element)).getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * A convenience method to get property name from the name of the
     * getter or setter method.
     * @param e the input method.
     * @return the name of the property of the given setter of getter.
     */
    public String propertyName(ExecutableElement e) {
        String name = getSimpleName(e);
        String propertyName = null;
        if (name.startsWith("get") || name.startsWith("set")) {
            propertyName = name.substring(3);
        } else if (name.startsWith("is")) {
            propertyName = name.substring(2);
        }
        if ((propertyName == null) || propertyName.isEmpty()){
            return "";
        }
        return propertyName.substring(0, 1).toLowerCase(configuration.getLocale())
                + propertyName.substring(1);
    }

    /**
     * Returns true if the element is included or selected, contains &#64;hidden tag,
     * or if javafx flag is present and element contains &#64;treatAsPrivate
     * tag.
     * @param e the queried element
     * @return true if it exists, false otherwise
     */
    public boolean hasHiddenTag(Element e) {
        // Non-included elements may still be visible via "transclusion" from undocumented enclosures,
        // but we don't want to run doclint on them, possibly causing warnings or errors.
        if (!isIncluded(e)) {
            return hasBlockTagUnchecked(e, HIDDEN);
        }
        if (options.javafx() &&
                hasBlockTag(e, DocTree.Kind.UNKNOWN_BLOCK_TAG, "treatAsPrivate")) {
            return true;
        }
        return hasBlockTag(e, DocTree.Kind.HIDDEN);
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
    public boolean isSimpleOverride(ExecutableElement m) {
        if (!options.summarizeOverriddenMethods() || !isIncluded(m)) {
            return false;
        }

        if (!getBlockTags(m).isEmpty() || isDeprecated(m))
            return false;

        List<? extends DocTree> fullBody = getFullBody(m);
        return fullBody.isEmpty() ||
                (fullBody.size() == 1 && fullBody.get(0).getKind().equals(Kind.INHERIT_DOC));
    }

    /**
     * In case of JavaFX mode on, filters out classes that are private,
     * package private, these are not documented in JavaFX mode, also
     * remove those classes that have &#64;hidden or &#64;treatAsPrivate comment tag.
     *
     * @param classlist a collection of TypeElements
     * @param javafx set to true if in JavaFX mode.
     * @return list of filtered classes.
     */
    public SortedSet<TypeElement> filterOutPrivateClasses(Iterable<TypeElement> classlist,
            boolean javafx) {
        SortedSet<TypeElement> filteredOutClasses =
                new TreeSet<>(comparators.generalPurposeComparator());
        if (!javafx) {
            for (TypeElement te : classlist) {
                if (!hasHiddenTag(te)) {
                    filteredOutClasses.add(te);
                }
            }
            return filteredOutClasses;
        }
        for (TypeElement e : classlist) {
            if (isPrivate(e) || isPackagePrivate(e) || hasHiddenTag(e)) {
                continue;
            }
            filteredOutClasses.add(e);
        }
        return filteredOutClasses;
    }

    /**
     * A general purpose case insensitive String comparator, which compares
     * two Strings using a Collator strength of "TERTIARY".
     *
     * @param s1 first String to compare.
     * @param s2 second String to compare.
     * @return a negative integer, zero, or a positive integer as the first
     *         argument is less than, equal to, or greater than the second.
     */
    public int compareStrings(String s1, String s2) {
        return compareStrings(true, s1, s2);
    }

    private DocCollator tertiaryCollator = null;
    private DocCollator secondaryCollator = null;

    int compareStrings(boolean caseSensitive, String s1, String s2) {
        if (caseSensitive) {
            if (tertiaryCollator == null) {
                tertiaryCollator = new DocCollator(configuration.locale, Collator.TERTIARY);
            }
            return tertiaryCollator.compare(s1, s2);
        }
        if (secondaryCollator == null) {
            secondaryCollator = new DocCollator(configuration.locale, Collator.SECONDARY);
        }
        return secondaryCollator.compare(s1, s2);
    }

    private static class DocCollator {
        private final Map<String, CollationKey> keys;
        private final Collator instance;
        private final int MAX_SIZE = 1000;
        private DocCollator(Locale locale, int strength) {
            instance = createCollator(locale);
            instance.setStrength(strength);

            keys = new LinkedHashMap<>(MAX_SIZE + 1, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Entry<String, CollationKey> eldest) {
                    return size() > MAX_SIZE;
                }
            };
        }

        CollationKey getKey(String s) {
            return keys.computeIfAbsent(s, instance :: getCollationKey);
        }

        public int compare(String s1, String s2) {
            return getKey(s1).compareTo(getKey(s2));
        }

        private Collator createCollator(Locale locale) {
            Collator baseCollator = Collator.getInstance(locale);
            if (baseCollator instanceof RuleBasedCollator rbc) {
                // Extend collator to sort signatures with additional args and var-args in a well-defined order:
                // () < (int) < (int, int) < (int...)
                try {
                    return new RuleBasedCollator(rbc.getRules()
                            + "& ')' < ',' < '.','['");
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
            return baseCollator;
        }
    }

    /**
     * Get the qualified type name of a TypeMirror compatible with the Element's
     * getQualified name, returns  the qualified name of the Reference type
     * otherwise the primitive name.
     * @param t the type whose name is to be obtained.
     * @return the fully qualified name of Reference type or the primitive name
     */
    public String getQualifiedTypeName(TypeMirror t) {
        return new SimpleTypeVisitor14<String, Void>() {
            @Override
            public String visitDeclared(DeclaredType t, Void p) {
                return getFullyQualifiedName(t.asElement());
            }

            @Override
            public String visitArray(ArrayType t, Void p) {
               return visit(t.getComponentType());
            }

            @Override
            public String visitTypeVariable(javax.lang.model.type.TypeVariable t, Void p) {
                // The knee jerk reaction is to do this but don't!, as we would like
                // it to be compatible with the old world, now if we decide to do so
                // care must be taken to avoid collisions.
                // return getFullyQualifiedName(t.asElement());
                return t.toString();
            }

            @Override
            protected String defaultAction(TypeMirror t, Void p) {
                return t.toString();
            }

        }.visit(t);
    }

    /**
     * A generic utility which returns the fully qualified names of an entity,
     * if the entity is not qualifiable then its enclosing entity, it is up to
     * the caller to add the elements name as required.
     * @param e the element to get FQN for.
     * @return the name
     */
    public String getFullyQualifiedName(Element e) {
        return getFullyQualifiedName(e, true);
    }

    public String getFullyQualifiedName(Element e, final boolean outer) {
        return new SimpleElementVisitor14<String, Void>() {
            @Override
            public String visitModule(ModuleElement e, Void p) {
                return e.getQualifiedName().toString();
            }

            @Override
            public String visitPackage(PackageElement e, Void p) {
                return e.getQualifiedName().toString();
            }

            @Override
            public String visitType(TypeElement e, Void p) {
                return e.getQualifiedName().toString();
            }

            @Override
            protected String defaultAction(Element e, Void p) {
                return outer ? visit(e.getEnclosingElement()) : e.getSimpleName().toString();
            }
        }.visit(e);
    }


    /**
     * Returns the recursively enclosed documented type elements in a package
     *
     * @param pkg the package
     * @return the elements
     */
    public Iterable<TypeElement> getEnclosedTypeElements(PackageElement pkg) {
        return getItems(pkg, false, this::isTypeElement, TypeElement.class);
    }

    // Element related methods

    /**
     * Returns the fields and methods declared in an annotation interface.
     *
     * @param te the annotation interface
     * @return the fields and methods
     */
    public List<Element> getAnnotationMembers(TypeElement te) {
        return getItems(te, false, e_ ->
                        switch (e_.getKind()) {
                            case FIELD, METHOD -> shouldDocument(e_);
                            default -> false;
                        },
                Element.class);

    }

    /**
     * Returns the documented fields in a type element.
     *
     * @param te the element
     * @return the fields
     */
    public List<VariableElement> getFields(TypeElement te) {
        return getDocumentedItems(te, FIELD, VariableElement.class);
    }

    /**
     * Returns the fields in a type element.
     *
     * @param te the element
     * @return the fields
     */
    public List<VariableElement> getFieldsUnfiltered(TypeElement te) {
        return getAllItems(te, FIELD, VariableElement.class);
    }

    /**
     * Returns the documented constructors in a type element.
     *
     * @param te the type element
     * @return the constructors
     */
    public List<ExecutableElement> getConstructors(TypeElement te) {
        return getDocumentedItems(te, CONSTRUCTOR, ExecutableElement.class);
    }


    /**
     * Returns the documented methods in a type element.
     *
     * @param te the type element
     * @return the methods
     */
    public List<ExecutableElement> getMethods(TypeElement te) {
        return getDocumentedItems(te, METHOD, ExecutableElement.class);
    }

    private Map<ModuleElement, Set<PackageElement>> modulePackageMap = null;
    public Map<ModuleElement, Set<PackageElement>> getModulePackageMap() {
        if (modulePackageMap == null) {
            modulePackageMap = new HashMap<>();
            Set<PackageElement> pkgs = configuration.getIncludedPackageElements();
            pkgs.forEach(pkg -> {
                ModuleElement mod = elementUtils.getModuleOf(pkg);
                modulePackageMap.computeIfAbsent(mod, m -> new HashSet<>()).add(pkg);
            });
        }
        return modulePackageMap;
    }

    public Map<ModuleElement, String> getDependentModules(ModuleElement mdle) {
        Map<ModuleElement, String> result = new TreeMap<>(comparators.moduleComparator());
        Deque<ModuleElement> queue = new ArrayDeque<>();
        // get all the requires for the element in question
        for (RequiresDirective rd : ElementFilter.requiresIn(mdle.getDirectives())) {
            ModuleElement dep = rd.getDependency();
            // add the dependency to work queue
            if (!result.containsKey(dep)) {
                if (rd.isTransitive()) {
                    queue.addLast(dep);
                }
            }
            // add all exports for the primary module
            result.put(rd.getDependency(), getModifiers(rd));
        }

        // add only requires public for subsequent module dependencies
        for (ModuleElement m = queue.poll(); m != null; m = queue.poll()) {
            for (RequiresDirective rd : ElementFilter.requiresIn(m.getDirectives())) {
                ModuleElement dep = rd.getDependency();
                if (!result.containsKey(dep)) {
                    if (rd.isTransitive()) {
                        result.put(dep, getModifiers(rd));
                        queue.addLast(dep);
                    }
                }
            }
        }
        return result;
    }

    public String getModifiers(RequiresDirective rd) {
        StringBuilder modifiers = new StringBuilder();
        String sep = "";
        if (rd.isTransitive()) {
            modifiers.append("transitive");
            sep = " ";
        }
        if (rd.isStatic()) {
            modifiers.append(sep);
            modifiers.append("static");
        }
        return (modifiers.length() == 0) ? " " : modifiers.toString();
    }

    public long getLineNumber(Element e) {
        TreePath path = getTreePath(e);
        if (path == null) { // maybe null if synthesized
            TypeElement encl = getEnclosingTypeElement(e);
            path = getTreePath(encl);
        }
        CompilationUnitTree cu = path.getCompilationUnit();
        LineMap lineMap = cu.getLineMap();
        DocSourcePositions spos = docTrees.getSourcePositions();
        long pos = spos.getStartPosition(cu, path.getLeaf());
        return lineMap.getLineNumber(pos);
    }

    /**
     * Returns the documented enum constants in a type element.
     *
     * @param te the element
     * @return the interfaces
     */
    public List<VariableElement> getEnumConstants(TypeElement te) {
        return getDocumentedItems(te, ENUM_CONSTANT, VariableElement.class);
    }

    /**
     * Returns all the classes in a package.
     *
     * @param pkg the package
     * @return the interfaces
     */
    public SortedSet<TypeElement> getAllClassesUnfiltered(PackageElement pkg) {
        SortedSet<TypeElement> set = new TreeSet<>(comparators.generalPurposeComparator());
        set.addAll(getItems(pkg, true, this::isTypeElement, TypeElement.class));
        return set;
    }

    private final HashMap<Element, SortedSet<TypeElement>> cachedClasses = new HashMap<>();

    /**
     * Returns a sorted set containing the documented classes and interfaces in a package.
     *
     * @param pkg the element
     * @return the classes and interfaces
     */
    public SortedSet<TypeElement> getAllClasses(PackageElement pkg) {
        return cachedClasses.computeIfAbsent(pkg, p_ -> {
            List<TypeElement> clist = getItems(pkg, false, this::isTypeElement, TypeElement.class);
            SortedSet<TypeElement>oset = new TreeSet<>(comparators.generalPurposeComparator());
            oset.addAll(clist);
            return oset;
        });
    }

    /**
     * Returns a list of documented elements of a given type with a given kind.
     * If the root of the search is a package, the search is recursive.
     *
     * @param e      the element, such as a package element or type element
     * @param kind   the element kind
     * @param clazz  the class of the filtered members
     * @param <T>    the class of the filtered members
     *
     * @return the list of enclosed elements
     */
    private <T extends Element> List<T> getDocumentedItems(Element e, ElementKind kind, Class<T> clazz) {
        return getItems(e, false, e_ -> e_.getKind() == kind && shouldDocument(e_), clazz);
    }

    /**
     * Returns a list of elements of a given type with a given kind.
     * If the root of the search is a package, the search is recursive.
     *
     * @param e      the element, such as a package element or type element
     * @param kind   the element kind
     * @param clazz  the class of the filtered members
     * @param <T>    the class of the filtered members
     *
     * @return the list of enclosed elements
     */
    private <T extends Element> List<T> getAllItems(Element e, ElementKind kind, Class<T> clazz) {
        return getItems(e, true, e_ -> e_.getKind() == kind, clazz);
    }

    /**
     * Returns a list of elements of a given type that match a predicate.
     * If the root of the search is a package, the search is recursive through packages
     * and classes.
     *
     * @param e      the element, such as a package element or type element
     * @param all    whether to search through all packages and classes, or just documented ones
     * @param select the predicate to select members
     * @param clazz  the class of the filtered members
     * @param <T>    the class of the filtered members
     *
     * @return the list of enclosed elements
     */
    private <T extends Element> List<T> getItems(Element e, boolean all, Predicate<Element> select, Class<T> clazz) {
        if (e.getKind() == ElementKind.PACKAGE) {
            List<T> elements = new ArrayList<>();
            recursiveGetItems(elements, e, all, select, clazz);
            return elements;
        } else {
            return getItems0(e, all, select, clazz);
        }
    }

    /**
     * Searches for a list of recursively enclosed elements of a given class that match a predicate.
     * The recursion is through nested types.
     *
     * @param e      the element, such as a package element or type element
     * @param all    whether to search all packages and classes, or just documented ones
     * @param filter the filter
     * @param clazz  the class of the filtered members
     * @param <T>    the class of the filtered members
     */
    private <T extends Element> void recursiveGetItems(Collection<T> list, Element e, boolean all, Predicate<Element> filter, Class<T> clazz) {
        list.addAll(getItems0(e, all, filter, clazz));
        List<TypeElement> classes = getItems0(e, all, this::isTypeElement, TypeElement.class);
        for (TypeElement c : classes) {
            recursiveGetItems(list, c, all, filter, clazz);
        }
    }

    /**
     * Returns a list of immediately enclosed elements of a given class that match a predicate.
     *
     * @param e      the element, such as a package element or type element
     * @param all    whether to search all packages and classes, or just documented ones
     * @param select the predicate for the selected members
     * @param clazz  the class of the filtered members
     * @param <T>    the class of the filtered members
     *
     * @return the list of enclosed elements
     */
    private <T extends Element> List<T> getItems0(Element e, boolean all, Predicate<Element> select, Class<T> clazz) {
        return e.getEnclosedElements().stream()
                .filter(e_ -> select.test(e_) && (all || shouldDocument(e_)))
                .map(clazz::cast)
                .toList();
    }

    private SimpleElementVisitor14<Boolean, Void> shouldDocumentVisitor = null;

    public boolean shouldDocument(Element e) {
        if (shouldDocumentVisitor == null) {
            shouldDocumentVisitor = new SimpleElementVisitor14<>() {
                private boolean hasSource(TypeElement e) {
                    return configuration.docEnv.getFileKind(e) ==
                            javax.tools.JavaFileObject.Kind.SOURCE;
                }

                // handle types
                @Override
                public Boolean visitType(TypeElement e, Void p) {
                    // treat inner classes etc. as members
                    if (e.getNestingKind().isNested()) {
                        return defaultAction(e, p);
                    }
                    return configuration.docEnv.isSelected(e) && hasSource(e);
                }

                // handle everything else
                @Override
                protected Boolean defaultAction(Element e, Void p) {
                    return configuration.docEnv.isSelected(e);
                }

                @Override
                public Boolean visitUnknown(Element e, Void p) {
                    throw new AssertionError("unknown element: " + e);
                }
            };
        }
        return shouldDocumentVisitor.visit(e);
    }

    /*
     * nameCache is maintained for improving the comparator
     * performance, noting that the Collator used by the comparators
     * use Strings, as of this writing.
     * TODO: when those APIs handle charSequences, the use of
     * this nameCache must be re-investigated and removed.
     */
    private final Map<Element, String> nameCache = new LinkedHashMap<>();

    /**
     * Returns the name of the element after the last dot of the package name.
     * This emulates the behavior of the old doclet.
     * @param e an element whose name is required
     * @return the name
     */
    public String getSimpleName(Element e) {
        return nameCache.computeIfAbsent(e, this::getSimpleName0);
    }

    private SimpleElementVisitor14<String, Void> snvisitor = null;

    // If `e` is a static nested class, this method will return e's simple name
    // preceded by `.` and an outer type; this is not how JLS defines "simple
    // name". See "Simple Name", "Qualified Name", "Fully Qualified Name".
    private String getSimpleName0(Element e) {
        if (snvisitor == null) {
            snvisitor = new SimpleElementVisitor14<>() {
                @Override
                public String visitModule(ModuleElement e, Void p) {
                    return e.getQualifiedName().toString();  // temp fix for 8182736
                }

                @Override
                public String visitType(TypeElement e, Void p) {
                    StringBuilder sb = new StringBuilder(e.getSimpleName().toString());
                    Element enclosed = e.getEnclosingElement();
                    while (enclosed != null
                            && (enclosed.getKind().isDeclaredType())) {
                        sb.insert(0, enclosed.getSimpleName() + ".");
                        enclosed = enclosed.getEnclosingElement();
                    }
                    return sb.toString();
                }

                @Override
                public String visitExecutable(ExecutableElement e, Void p) {
                    if (e.getKind() == CONSTRUCTOR || e.getKind() == STATIC_INIT) {
                        return e.getEnclosingElement().getSimpleName().toString();
                    }
                    return e.getSimpleName().toString();
                }

                @Override
                protected String defaultAction(Element e, Void p) {
                    return e.getSimpleName().toString();
                }
            };
        }
        return snvisitor.visit(e);
    }

    public TypeElement getEnclosingTypeElement(Element e) {
        if (isPackage(e) || isModule(e)) {
            return null;
        }
        Element encl = e.getEnclosingElement();
        if (isPackage(encl)) {
            return null;
        }
        ElementKind kind = encl.getKind();
        while (!(kind.isClass() || kind.isInterface())) {
            encl = encl.getEnclosingElement();
            kind = encl.getKind();
        }
        return (TypeElement)encl;
    }

    private ConstantValueExpression cve = null;

    public String constantValueExpression(VariableElement ve) {
        if (cve == null)
            cve = new ConstantValueExpression();
        return cve.visit(ve.asType(), ve.getConstantValue());
    }

    // We could also use Elements.getConstantValueExpression, which provides
    // similar functionality, but which also includes casts to provide valid
    // compilable constants:  e.g. (byte) 0x7f
    private static class ConstantValueExpression extends TypeKindVisitor9<String, Object> {
        @Override
        public String visitPrimitiveAsBoolean(PrimitiveType t, Object val) {
            return ((boolean) val) ? "true" : "false";
        }

        @Override
        public String visitPrimitiveAsByte(PrimitiveType t, Object val) {
            return "0x" + Integer.toString(((Byte) val) & 0xff, 16);
        }

        @Override
        public String visitPrimitiveAsChar(PrimitiveType t, Object val) {
            StringBuilder buf = new StringBuilder(8);
            buf.append('\'');
            sourceChar((char) val, buf);
            buf.append('\'');
            return buf.toString();
        }

        @Override
        public String visitPrimitiveAsDouble(PrimitiveType t, Object val) {
            return sourceForm(((Double) val), 'd');
        }

        @Override
        public String visitPrimitiveAsFloat(PrimitiveType t, Object val) {
            return sourceForm(((Float) val).doubleValue(), 'f');
        }

        @Override
        public String visitPrimitiveAsLong(PrimitiveType t, Object val) {
            return val + "L";
        }

        @Override
        protected String defaultAction(TypeMirror e, Object val) {
            if (val == null)
                return null;
            else if (val instanceof String s)
                return sourceForm(s);
            return val.toString(); // covers int, short
        }

        private String sourceForm(double v, char suffix) {
            if (Double.isNaN(v))
                return "0" + suffix + "/0" + suffix;
            if (v == Double.POSITIVE_INFINITY)
                return "1" + suffix + "/0" + suffix;
            if (v == Double.NEGATIVE_INFINITY)
                return "-1" + suffix + "/0" + suffix;
            return v + (suffix == 'f' || suffix == 'F' ? "" + suffix : "");
        }

        private String sourceForm(String s) {
            StringBuilder buf = new StringBuilder(s.length() + 5);
            buf.append('\"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                sourceChar(c, buf);
            }
            buf.append('\"');
            return buf.toString();
        }

        private void sourceChar(char c, StringBuilder buf) {
            switch (c) {
                case '\b' -> buf.append("\\b");
                case '\t' -> buf.append("\\t");
                case '\n' -> buf.append("\\n");
                case '\f' -> buf.append("\\f");
                case '\r' -> buf.append("\\r");
                case '\"' -> buf.append("\\\"");
                case '\'' -> buf.append("\\\'");
                case '\\' -> buf.append("\\\\");
                default -> {
                    if (isPrintableAscii(c)) {
                        buf.append(c);
                        return;
                    }
                    unicodeEscape(c, buf);
                }
            }
        }

        private void unicodeEscape(char c, StringBuilder buf) {
            final String chars = "0123456789abcdef";
            buf.append("\\u");
            buf.append(chars.charAt(15 & (c >> 12)));
            buf.append(chars.charAt(15 & (c >> 8)));
            buf.append(chars.charAt(15 & (c >> 4)));
            buf.append(chars.charAt(15 & (c >> 0)));
        }

        private boolean isPrintableAscii(char c) {
            return c >= ' ' && c <= '~';
        }
    }

    public boolean isEnclosingPackageIncluded(TypeElement te) {
        return isIncluded(containingPackage(te));
    }

    public boolean isIncluded(Element e) {
        return configuration.docEnv.isIncluded(e);
    }

    private SimpleElementVisitor14<Boolean, Void> specifiedVisitor = null;
    public boolean isSpecified(Element e) {
        if (specifiedVisitor == null) {
            specifiedVisitor = new SimpleElementVisitor14<>() {
                @Override
                public Boolean visitModule(ModuleElement e, Void p) {
                    return configuration.getSpecifiedModuleElements().contains(e);
                }

                @Override
                public Boolean visitPackage(PackageElement e, Void p) {
                    return configuration.getSpecifiedPackageElements().contains(e);
                }

                @Override
                public Boolean visitType(TypeElement e, Void p) {
                    return configuration.getSpecifiedTypeElements().contains(e);
                }

                @Override
                protected Boolean defaultAction(Element e, Void p) {
                    return false;
                }
            };
        }
        return specifiedVisitor.visit(e);
    }

    /**
     * Get the package name for a given package element. An unnamed package is returned as &lt;Unnamed&gt;
     * Use {@link jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter#getLocalizedPackageName(PackageElement)}
     * to get a localized string for the unnamed package instead.
     *
     * @param pkg
     * @return
     */
    public String getPackageName(PackageElement pkg) {
        if (pkg == null || pkg.isUnnamed()) {
            return DocletConstants.DEFAULT_ELEMENT_NAME;
        }
        return pkg.getQualifiedName().toString();
    }

    /**
     * Get the module name for a given module element. An unnamed module is returned as &lt;Unnamed&gt;
     *
     * @param mdle a ModuleElement
     * @return
     */
    public String getModuleName(ModuleElement mdle) {
        if (mdle == null || mdle.isUnnamed()) {
            return DocletConstants.DEFAULT_ELEMENT_NAME;
        }
        return mdle.getQualifiedName().toString();
    }

    private final CommentHelperCache commentHelperCache = new CommentHelperCache(this);

    public CommentHelper getCommentHelper(Element element) {
        return commentHelperCache.computeIfAbsent(element);
    }

    public void removeCommentHelper(Element element) {
        commentHelperCache.remove(element);
    }

    /**
     * Returns the "raw" list of block tags from the doc-comment tree for an element,
     * or an empty list if there is no such comment.
     *
     * Note: The list may include {@code ErroneousTree} nodes.
     *
     * @param element the element
     * @return the list
     */
    public List<? extends DocTree> getBlockTags(Element element) {
        return getBlockTags(getDocCommentTree(element));
    }

    /**
     * Returns the "raw" list of block tags from a {@code DocCommentTree}, or an empty list
     * if the doc-comment tree is {@code null}.
     *
     * Note: The list may include {@code ErroneousTree} nodes.
     *
     * @param dcTree the doc-comment tree
     * @return the list
     */
    public List<? extends DocTree> getBlockTags(DocCommentTree dcTree) {
        return dcTree == null ? List.of() : dcTree.getBlockTags();
    }

    /**
     * Returns the list of block tags for the doc-comment tree for an element that match
     * a given filter, or an empty list if there is no such doc-comment.
     *
     * @param element the element
     * @param filter  the filter
     * @return the list
     */
    public List<? extends BlockTagTree> getBlockTags(Element element, Predicate<? super BlockTagTree> filter) {
        return getBlockTags(element).stream()
                .filter(t -> t.getKind() != ERRONEOUS)
                .map(t -> (BlockTagTree) t)
                .filter(filter)
                .toList();
    }

    /**
     * Returns the list of block tags for the doc-comment tree for an element that match
     * a given filter, or an empty list if there is no such doc-comment.
     *
     * @param <T> the type of the required block tags
     * @param element the element
     * @param filter  the filter
     * @return the list
     */
    public <T extends BlockTagTree> List<T> getBlockTags(Element element,
                                                         Predicate<? super BlockTagTree> filter,
                                                         Class<T> tClass) {
        return getBlockTags(element).stream()
                .filter(t -> t.getKind() != ERRONEOUS)
                .map(t -> (BlockTagTree) t)
                .filter(filter)
                .map(tClass::cast)
                .toList();
    }

    /**
     * Returns the list of block tags for the doc-comment tree for an element,
     * or an empty list if there is no such doc-comment.
     *
     * @param element the element
     * @return the list
     */
    public List<? extends BlockTagTree> getBlockTags(Element element, DocTree.Kind kind) {
        return getBlockTags(element, t -> t.getKind() == kind);
    }

    /**
     * Returns the list of block tags for the doc-comment tree for an element that match a given kind,
     * or an empty list if there is no such doc-comment.
     *
     * @param <T> the type of the required block tags
     * @param element the element
     * @param kind the kind for the required block tags
     * @return the list
     */
    public <T extends BlockTagTree> List<? extends T> getBlockTags(Element element, DocTree.Kind kind, Class<T> tClass) {
        return getBlockTags(element, t -> t.getKind() == kind, tClass);
    }

    /**
     * Returns the list of block tags for the doc-comment tree for an element that match a given name,
     * or an empty list if there is no such doc-comment.
     *
     * @param element the element
     * @param tagName the name of the required block tags
     * @return the list
     */
    public List<? extends BlockTagTree> getBlockTags(Element element, String tagName) {
        return getBlockTags(element, t -> t.getTagName().equals(tagName));
    }

    public boolean hasBlockTag(Element element, DocTree.Kind kind) {
        return hasBlockTag(element, kind, null);
    }

    public boolean hasBlockTag(Element element, DocTree.Kind kind, final String tagName) {
        if (hasDocCommentTree(element)) {
            CommentHelper ch = getCommentHelper(element);
            for (DocTree dt : getBlockTags(ch.dcTree)) {
                if (dt.getKind() == kind && (tagName == null || ch.getTagName(dt).equals(tagName))) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Tests whether an element's doc comment contains a block tag without caching it or
     * running doclint on it. This is done by using getDocCommentInfo(Element) to retrieve
     * the doc comment info.
     */
    boolean hasBlockTagUnchecked(Element element, DocTree.Kind kind) {
        DocCommentInfo dcInfo = getDocCommentInfo(element);
        if (dcInfo != null && dcInfo.dcTree != null) {
            for (DocTree dt : getBlockTags(dcInfo.dcTree)) {
                if (dt.getKind() == kind) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets a TreePath for an Element. Note this method is called very
     * frequently, care must be taken to ensure this method is lithe
     * and efficient.
     * @param e an Element
     * @return TreePath
     */
    public TreePath getTreePath(Element e) {
        DocCommentInfo info = dcTreeCache.get(e);
        if (info != null && info.treePath != null) {
            return info.treePath;
        }
        info = configuration.cmtUtils.getSyntheticCommentInfo(e);
        if (info != null && info.treePath != null) {
            return info.treePath;
        }
        Map<Element, TreePath> elementToTreePath = configuration.workArounds.getElementToTreePath();
        TreePath path = elementToTreePath.get(e);
        if (path != null || elementToTreePath.containsKey(e)) {
            // expedite the path and one that is a null
            return path;
        } else {
            var p = docTrees.getPath(e);
            // if docTrees.getPath itself has put a path for e into elementToTreePath
            // (see 8304878), we assume that the path already in the map is equivalent
            // to the path we are about to put: hence, no harm if replaced
            elementToTreePath.put(e, p);
            return p;
        }
    }

    /**
     * A cache of doc comment info objects for elements.
     * The entries may come from the AST and DocCommentParser, or may be automatically
     * generated comments for mandated elements and JavaFX properties.
     *
     * @see CommentUtils#dcInfoMap
     */
    private final Map<Element, DocCommentInfo> dcTreeCache = new LinkedHashMap<>();

    /**
     * Checks whether an element has an associated doc comment.
     * @param element the element
     * @return {@code true} if the element has a comment, and false otherwise
     */
    public boolean hasDocCommentTree(Element element) {
        DocCommentInfo info = getDocCommentInfo(element);
        return info != null && info.dcTree != null;
    }

    /**
     * Retrieves the doc comments for a given element.
     * @param element the element
     * @return DocCommentTree for the Element
     */
    public DocCommentTree getDocCommentTree0(Element element) {

        DocCommentInfo info = getDocCommentInfo(element);

        DocCommentTree docCommentTree = info == null ? null : info.dcTree;
        if (!dcTreeCache.containsKey(element)) {
            TreePath path = info == null ? null : info.treePath;
            if (path != null) {
                if (docCommentTree != null && !configuration.isAllowScriptInComments()) {
                    try {
                        javaScriptScanner.scan(docCommentTree, path, p -> {
                            throw new JavaScriptScanner.Fault();
                        });
                    } catch (JavaScriptScanner.Fault jsf) {
                        String text = resources.getText("doclet.JavaScript_in_comment");
                        throw new UncheckedDocletException(new SimpleDocletException(text, jsf));
                    }
                }
                // run doclint even if docCommentTree is null, to trigger checks for missing comments
                configuration.runDocLint(path);
            }
            dcTreeCache.put(element, info);
        }
        return docCommentTree;
    }

    private DocCommentInfo getDocCommentInfo(Element element) {
        DocCommentInfo info = null;

        ElementKind kind = element.getKind();
        if (kind == ElementKind.PACKAGE || kind == ElementKind.OTHER) {
            info = dcTreeCache.get(element); // local cache
            if (info == null && kind == ElementKind.PACKAGE) {
                // package-info.java
                info = getDocCommentInfo0(element);
            }
            if (info == null) {
                // package.html or overview.html
                info = configuration.cmtUtils.getHtmlCommentInfo(element); // html source
            }
        } else {
            info = configuration.cmtUtils.getSyntheticCommentInfo(element);
            if (info == null) {
                info = dcTreeCache.get(element); // local cache
            }
            if (info == null) {
                info = getDocCommentInfo0(element); // get the real mccoy
            }
        }

        return info;
    }

    private DocCommentInfo getDocCommentInfo0(Element element) {
        // prevent nasty things downstream with overview element
        if (!isOverviewElement(element)) {
            TreePath path = getTreePath(element);
            if (path != null) {
                DocCommentTree docCommentTree = docTrees.getDocCommentTree(path);
                return new DocCommentInfo(path, docCommentTree);
            }
        }
        return null;
    }

    public void checkJavaScriptInOption(String name, String value) {
        if (!configuration.isAllowScriptInComments()) {
            DocCommentTree dct = configuration.cmtUtils.parse(
                    URI.create("option://" + name.replace("-", "")), "<body>" + value + "</body>");

            if (dct == null)
                return;

            try {
                javaScriptScanner.scan(dct, null, p -> {
                    throw new JavaScriptScanner.Fault();
                });
            } catch (JavaScriptScanner.Fault jsf) {
                String text = resources.getText("doclet.JavaScript_in_option", name);
                throw new UncheckedDocletException(new SimpleDocletException(text, jsf));
            }
        }
    }

    public DocCommentTree getDocCommentTree(Element element) {
        CommentHelper ch = commentHelperCache.get(element);
        if (ch != null) {
            return ch.dcTree;
        }
        DocCommentTree dcTree = getDocCommentTree0(element);
        if (dcTree != null) {
            commentHelperCache.put(element, new CommentHelper(configuration, element, getTreePath(element), dcTree));
        }
        return dcTree;
    }

    public List<? extends DocTree> getPreamble(Element element) {
        DocCommentTree docCommentTree = getDocCommentTree(element);
        return docCommentTree == null
                ? List.of()
                : docCommentTree.getPreamble();
    }

    public List<? extends DocTree> getFullBody(Element element) {
        DocCommentTree docCommentTree = getDocCommentTree(element);
            return (docCommentTree == null)
                    ? List.of()
                    : docCommentTree.getFullBody();
    }

    public List<? extends DocTree> getBody(Element element) {
        DocCommentTree docCommentTree = getDocCommentTree(element);
        return (docCommentTree == null)
                ? List.of()
                : docCommentTree.getFullBody();
    }

    public List<? extends DeprecatedTree> getDeprecatedTrees(Element element) {
        return getBlockTags(element, DEPRECATED, DeprecatedTree.class);
    }

    public List<? extends ProvidesTree> getProvidesTrees(Element element) {
        return getBlockTags(element, PROVIDES, ProvidesTree.class);
    }

    public List<? extends SeeTree> getSeeTrees(Element element) {
        return getBlockTags(element, SEE, SeeTree.class);
    }

    public List<? extends SerialTree> getSerialTrees(Element element) {
        return getBlockTags(element, SERIAL, SerialTree.class);
    }

    public List<? extends SerialFieldTree> getSerialFieldTrees(VariableElement field) {
        return getBlockTags(field, DocTree.Kind.SERIAL_FIELD, SerialFieldTree.class);
    }

    public List<? extends SpecTree> getSpecTrees(Element element) {
        return getBlockTags(element, SPEC, SpecTree.class);
    }

    public List<ThrowsTree> getThrowsTrees(Element element) {
        return getBlockTags(element,
                t -> switch (t.getKind()) { case EXCEPTION, THROWS -> true; default -> false; },
                ThrowsTree.class);
    }

    public List<ParamTree> getTypeParamTrees(Element element) {
        return getParamTrees(element, true);
    }

    public List<ParamTree> getParamTrees(Element element) {
        return getParamTrees(element, false);
    }

    private  List<ParamTree> getParamTrees(Element element, boolean isTypeParameters) {
        return getBlockTags(element,
                t -> t.getKind() == PARAM && ((ParamTree) t).isTypeParameter() == isTypeParameters,
                ParamTree.class);
    }

    public  List<? extends ReturnTree> getReturnTrees(Element element) {
        return getBlockTags(element, RETURN, ReturnTree.class);
    }

    public List<? extends UsesTree> getUsesTrees(Element element) {
        return getBlockTags(element, USES, UsesTree.class);
    }

    public List<? extends DocTree> getFirstSentenceTrees(Element element) {
        DocCommentTree dcTree = getDocCommentTree(element);
        if (dcTree == null) {
            return List.of();
        }
        return new ArrayList<>(dcTree.getFirstSentence());
    }

    public ModuleElement containingModule(Element e) {
        // TODO: remove this short-circuit after JDK-8302545 has been fixed
        //  or --ignore-source-errors has been removed
        if (e.getKind() == ElementKind.PACKAGE
                && e.getEnclosingElement() == null) {
            return null;
        }
        return elementUtils.getModuleOf(e);
    }

    public PackageElement containingPackage(Element e) {
        // TODO: remove this short-circuit after JDK-8302545 has been fixed
        //  or --ignore-source-errors has been removed
        if (e.getKind() == ElementKind.PACKAGE) {
            return (PackageElement) e;
        }
        return elementUtils.getPackageOf(e);
    }

    /**
     * A memory-sensitive cache for {@link CommentHelper} objects,
     * which are expensive to compute.
     */
    private static class CommentHelperCache {

        private final Map<Element, SoftReference<CommentHelper>> map;
        private final Utils utils;

        public CommentHelperCache(Utils utils) {
            map = new HashMap<>();
            this.utils = utils;
        }

        public CommentHelper remove(Element key) {
            SoftReference<CommentHelper> value = map.remove(key);
            return value == null ? null : value.get();
        }

        public CommentHelper put(Element key, CommentHelper value) {
            SoftReference<CommentHelper> prev = map.put(key, new SoftReference<>(value));
            return prev == null ? null : prev.get();
        }

        public CommentHelper get(Element key) {
            SoftReference<CommentHelper> value = map.get(key);
            return value == null ? null : value.get();
        }

        public CommentHelper computeIfAbsent(Element key) {
            SoftReference<CommentHelper> refValue = map.get(key);
            if (refValue != null) {
                CommentHelper value = refValue.get();
                if (value != null) {
                    return value;
                }
            }
            CommentHelper newValue = new CommentHelper(utils.configuration, key, utils.getTreePath(key),
                    utils.getDocCommentTree(key));
            map.put(key, new SoftReference<>(newValue));
            return newValue;
        }
    }

    /**
     * A container holding a pair of values (tuple).
     *
     * @param <K> the type of the first value
     * @param <L> the type of the second value
     */
    public static class Pair<K, L> {
        public final K first;
        public final L second;

        public Pair(K first, L second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public String toString() {
            return first + ":" + second;
        }
    }

    /**
     * Return the set of preview language features used to declare the given element.
     *
     * @param e the Element to check.
     * @return the set of preview language features used to declare the given element
     */
    public Set<DeclarationPreviewLanguageFeatures> previewLanguageFeaturesUsed(Element e) {
        return new HashSet<>();
    }

    public enum DeclarationPreviewLanguageFeatures {
        NONE(List.of(""));
        public final List<String> features;

        DeclarationPreviewLanguageFeatures(List<String> features) {
            this.features = features;
        }
    }

    public PreviewSummary declaredUsingPreviewAPIs(Element el) {
        if (el.asType().getKind() == ERROR) {
            // Can happen with undocumented --ignore-source-errors option
            return new PreviewSummary(Set.of(), Set.of(), Set.of());
        }
        List<TypeElement> usedInDeclaration = new ArrayList<>(annotations2Classes(el));
        switch (el.getKind()) {
            case ANNOTATION_TYPE, CLASS, ENUM, INTERFACE, RECORD -> {
                TypeElement te = (TypeElement) el;
                for (TypeParameterElement tpe : te.getTypeParameters()) {
                    usedInDeclaration.addAll(types2Classes(tpe.getBounds()));
                }
                usedInDeclaration.addAll(types2Classes(List.of(te.getSuperclass())));
                usedInDeclaration.addAll(types2Classes(te.getPermittedSubclasses()));
                usedInDeclaration.addAll(types2Classes(te.getRecordComponents().stream().map(Element::asType).toList())); //TODO: annotations on record components???
            }
            case CONSTRUCTOR, METHOD -> {
                ExecutableElement ee = (ExecutableElement) el;
                for (TypeParameterElement tpe : ee.getTypeParameters()) {
                    usedInDeclaration.addAll(types2Classes(tpe.getBounds()));
                }
                usedInDeclaration.addAll(types2Classes(List.of(ee.getReturnType())));
                usedInDeclaration.addAll(types2Classes(List.of(ee.getReceiverType())));
                usedInDeclaration.addAll(types2Classes(ee.getThrownTypes()));
                usedInDeclaration.addAll(types2Classes(ee.getParameters().stream().map(VariableElement::asType).toList()));
                usedInDeclaration.addAll(annotationValue2Classes(ee.getDefaultValue()));
            }
            case FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> {
                VariableElement ve = (VariableElement) el;
                usedInDeclaration.addAll(types2Classes(List.of(ve.asType())));
            }
            case MODULE, PACKAGE -> {
            }
            default -> throw new IllegalArgumentException("Unexpected: " + el.getKind());
        }

        Set<TypeElement> previewAPI = new HashSet<>();
        Set<TypeElement> reflectivePreviewAPI = new HashSet<>();
        Set<TypeElement> declaredUsingPreviewFeature = new HashSet<>();

        for (TypeElement type : usedInDeclaration) {
            if (!isIncluded(type) && !configuration.extern.isExternal(type)) {
                continue;
            }
            if (isPreviewAPI(type)) {
                if (isReflectivePreviewAPI(type)) {
                    reflectivePreviewAPI.add(type);
                } else {
                    previewAPI.add(type);
                }
            }
            if (!previewLanguageFeaturesUsed(type).isEmpty()) {
                declaredUsingPreviewFeature.add(type);
            }
        }

        return new PreviewSummary(previewAPI, reflectivePreviewAPI, declaredUsingPreviewFeature);
    }

    private Collection<TypeElement> types2Classes(List<? extends TypeMirror> types) {
        List<TypeElement> result = new ArrayList<>();
        List<TypeMirror> todo = new ArrayList<>(types);

        while (!todo.isEmpty()) {
            TypeMirror type = todo.remove(todo.size() - 1);

            result.addAll(annotations2Classes(type));

            if (type.getKind() == DECLARED) {
                DeclaredType dt = (DeclaredType) type;
                result.add((TypeElement) dt.asElement());
                todo.addAll(dt.getTypeArguments());
            }
        }

        return result;
    }

    private Collection<TypeElement> annotations2Classes(AnnotatedConstruct annotated) {
        List<TypeElement> result = new ArrayList<>();

        for (AnnotationMirror am : annotated.getAnnotationMirrors()) {
            result.addAll(annotation2Classes(am));
        }

        return result;
    }

    private Collection<TypeElement> annotation2Classes(AnnotationMirror am) {
        List<TypeElement> result = new ArrayList<>();

        result.addAll(types2Classes(List.of(am.getAnnotationType())));
        am.getElementValues()
          .values()
          .stream()
          .flatMap(av -> annotationValue2Classes(av).stream())
          .forEach(result::add);

        return result;
    }

    private Collection<TypeElement> annotationValue2Classes(AnnotationValue value) {
        if (value == null) {
            return List.of();
        }

        List<TypeElement> result = new ArrayList<>();

        value.accept(new SimpleAnnotationValueVisitor14<>() {
            @Override
            public Object visitArray(List<? extends AnnotationValue> vals, Object p) {
                vals.stream()
                    .forEach(v -> v.accept(this, null));
                return super.visitArray(vals, p);
            }
            @Override
            public Object visitAnnotation(AnnotationMirror a, Object p) {
                result.addAll(annotation2Classes(a));
                return super.visitAnnotation(a, p);
            }

            @Override
            public Object visitType(TypeMirror t, Object p) {
                result.addAll(types2Classes(List.of(t)));
                return super.visitType(t, p);
            }

        }, null);

        return result;
    }

    public static final class PreviewSummary {
        public final Set<TypeElement> previewAPI;
        public final Set<TypeElement> reflectivePreviewAPI;
        public final Set<TypeElement> declaredUsingPreviewFeature;

        public PreviewSummary(Set<TypeElement> previewAPI, Set<TypeElement> reflectivePreviewAPI, Set<TypeElement> declaredUsingPreviewFeature) {
            this.previewAPI = previewAPI;
            this.reflectivePreviewAPI = reflectivePreviewAPI;
            this.declaredUsingPreviewFeature = declaredUsingPreviewFeature;
        }

        @Override
        public String toString() {
            return "PreviewSummary{" + "previewAPI=" + previewAPI + ", reflectivePreviewAPI=" + reflectivePreviewAPI + ", declaredUsingPreviewFeature=" + declaredUsingPreviewFeature + '}';
        }

    }

    /**
     * Checks whether the given Element should be marked as a preview API.
     *
     * Note that if a type is marked as a preview, its members are not.
     *
     * @param el the element to check
     * @return true if and only if the given element should be marked as a preview API
     */
    public boolean isPreviewAPI(Element el) {
        boolean parentPreviewAPI = false;
        if (!isClassOrInterface(el)) {
            Element enclosing = el.getEnclosingElement();
            if (isClassOrInterface(enclosing)) {
                parentPreviewAPI = configuration.workArounds.isPreviewAPI(enclosing);
            }
        }
        boolean previewAPI = configuration.workArounds.isPreviewAPI(el);
        return !parentPreviewAPI && previewAPI;
    }

    /**
     * Checks whether the given Element should be marked as a reflective preview API.
     *
     * Note that if a type is marked as a preview, its members are not.
     *
     * @param el the element to check
     * @return true if and only if the given element should be marked
     *              as a reflective preview API
     */
    public boolean isReflectivePreviewAPI(Element el) {
        return isPreviewAPI(el) && configuration.workArounds.isReflectivePreviewAPI(el);
    }

    /**
     * Checks whether the given ExecutableElement should be marked as a restricted API.
     *
     * @param el the element to check
     * @return true if and only if the given element should be marked as a restricted API
     */
    public boolean isRestrictedAPI(Element el) {
        return configuration.workArounds.isRestrictedAPI(el);
    }

    /**
     * Return all flags for the given Element.
     *
     * @param el the element to test
     * @return the set of all the element's flags.
     */
    public Set<ElementFlag> elementFlags(Element el) {
        Set<ElementFlag> flags = EnumSet.noneOf(ElementFlag.class);

        if (isDeprecated(el)) {
            flags.add(ElementFlag.DEPRECATED);
        }

        if (el.getKind() == ElementKind.METHOD && configuration.workArounds.isRestrictedAPI((ExecutableElement)el)) {
            flags.add(ElementFlag.RESTRICTED);
        }

        if (previewFlagProvider.isPreview(el)) {
            flags.add(ElementFlag.PREVIEW);
        }

        return flags;
    }

    /**
     * An element can have flags that place it into some subcategories, like
     * being a preview or a deprecated element.
     */
    public enum ElementFlag {
        DEPRECATED,
        PREVIEW,
        RESTRICTED
    }

    private boolean isClassOrInterface(Element el) {
        return el != null && (el.getKind().isClass() || el.getKind().isInterface());
    }

    private boolean hasNoPreviewAnnotation(Element el) {
        return el.getAnnotationMirrors()
                 .stream()
                 .anyMatch(am -> "jdk.internal.javac.NoPreview".equals(getQualifiedTypeName(am.getAnnotationType())));
    }

    private PreviewFlagProvider previewFlagProvider = new PreviewFlagProvider() {
        @Override
        public boolean isPreview(Element el) {
            PreviewSummary previewAPIs = declaredUsingPreviewAPIs(el);
            Element enclosing = el.getEnclosingElement();

            return    (   !previewLanguageFeaturesUsed(el).isEmpty()
                       || configuration.workArounds.isPreviewAPI(el)
                       || (   !isClassOrInterface(el) && isClassOrInterface(enclosing)
                           && configuration.workArounds.isPreviewAPI(enclosing))
                       || !previewAPIs.previewAPI.isEmpty()
                       || !previewAPIs.reflectivePreviewAPI.isEmpty()
                       || !previewAPIs.declaredUsingPreviewFeature.isEmpty())
                   && !hasNoPreviewAnnotation(el);
        }
    };

    public PreviewFlagProvider setPreviewFlagProvider(PreviewFlagProvider provider) {
        Objects.requireNonNull(provider);
        PreviewFlagProvider old = previewFlagProvider;
        previewFlagProvider = provider;
        return old;
    }

    public interface PreviewFlagProvider {
        boolean isPreview(Element el);
    }

    public DocFinder docFinder() {
        return docFinder;
    }

    private DocFinder newDocFinder() {
        return new DocFinder(this::overriddenMethods);
    }

    /*
     * Returns an iterable over all unique methods overridden by the given
     * method from its enclosing type element. The methods encounter order
     * is that of described in the "Automatic Supertype Search" section of
     * the Documentation Comment Specification for the Standard Doclet.
     */
    private Iterable<? extends ExecutableElement> overriddenMethods(ExecutableElement method) {
        return () -> new Overrides(method);
    }

    private class Overrides implements Iterator<ExecutableElement> {

        // prefer java.util.Deque to java.util.Stack API for stacks
        final Deque<TypeElement> searchStack = new ArrayDeque<>();
        final Set<TypeElement> visited = new HashSet<>();

        final ExecutableElement overrider;
        ExecutableElement next;

        public Overrides(ExecutableElement method) {
            if (method.getKind() != ElementKind.METHOD) {
                throw new IllegalArgumentException(diagnosticDescriptionOf(method));
            }
            overrider = method;
            // java.lang.Object is to be searched for overrides last
            searchStack.push(JAVA_LANG_OBJECT);
            searchStack.push((TypeElement) method.getEnclosingElement());
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            updateNext();
            return next != null;
        }

        @Override
        public ExecutableElement next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            var n = next;
            updateNext();
            return n;
        }

        private void updateNext() {
            while (!searchStack.isEmpty()) {
                // replace the top class or interface with its supertypes
                var t = searchStack.pop();

                // <TODO refactor once java.util.List.reversed() from
                //   SequencedCollection is available>
                var filteredSupertypes = typeUtils.directSupertypes(t.asType()).stream()
                        .map(t_ -> (TypeElement) ((DeclaredType) t_).asElement())
                        // filter out java.lang.Object using the fact that at
                        // most one class type comes first in the stream of
                        // direct supertypes
                        .dropWhile(JAVA_LANG_OBJECT::equals)
                        .filter(visited::add) // idempotent side effect
                        .collect(Collectors.toCollection(ArrayList::new));
                // push supertypes in reverse order, so that they are popped
                // back in the initial order
                Collections.reverse(filteredSupertypes);
                filteredSupertypes.forEach(searchStack::push);
                // </TODO>

                // consider only the declared methods for consistency with
                // the existing facilities, such as Utils.overriddenMethod
                // and VisibleMemberTable.getImplementedMethods
                TypeElement peek = searchStack.peek();
                if (peek == null) {
                    next = null; // end-of-hierarchy
                    break;
                }
                if (isPlainInterface(peek) && !isPublic(peek) && !isLinkable(peek)) {
                    // we don't consider such interfaces directly, but may consider
                    // their supertypes (subject to this check for each of them)
                    continue;
                }
                List<Element> declaredMethods = configuration.getVisibleMemberTable(peek)
                        .getMembers(VisibleMemberTable.Kind.METHODS);
                var overridden = declaredMethods.stream()
                        .filter(candidate -> elementUtils.overrides(overrider, (ExecutableElement) candidate,
                                (TypeElement) overrider.getEnclosingElement()))
                        .findFirst();
                // assume a method may override at most one method in any
                // given class or interface; hence findFirst
                assert declaredMethods.stream()
                        .filter(candidate -> elementUtils.overrides(overrider, (ExecutableElement) candidate,
                                (TypeElement) overrider.getEnclosingElement()))
                        .count() <= 1 : diagnosticDescriptionOf(overrider);

                if (overridden.isPresent()) {
                    next = (ExecutableElement) overridden.get();
                    break;
                }

                // TODO we're currently ignoring simpleOverride
                //  (it's unavailable in this data structure)
            }

            // if the stack is empty, there's no unconsumed override:
            // if that ever fails, an iterator's client will be stuck
            // in an infinite loop
            assert !searchStack.isEmpty() || next == null
                    : diagnosticDescriptionOf(overrider);
        }
    }

    public static String diagnosticDescriptionOf(Element e) {
        if (e == null) // shouldn't NPE if passed null
            return "null";
        return e + ", " + (e instanceof QualifiedNameable q ? q.getQualifiedName() : e.getSimpleName())
                + ", " + e.getKind() + ", " + Objects.toIdentityString(e);
    }
}
