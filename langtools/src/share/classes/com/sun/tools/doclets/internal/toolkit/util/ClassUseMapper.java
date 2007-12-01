/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.*;
import java.util.*;

/**
 * Map all class uses for a given class.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @since 1.2
 * @author Robert G. Field
 */
public class ClassUseMapper {

    private final ClassTree classtree;

    /**
     * Mapping of ClassDocs to set of PackageDoc used by that class.
     * Entries may be null.
     */
    public Map classToPackage = new HashMap();

    /**
     * Mapping of Annotations to set of PackageDoc that use the annotation.
     */
    public Map classToPackageAnnotations = new HashMap();

    /**
     * Mapping of ClassDocs to set of ClassDoc used by that class.
     * Entries may be null.
     */
    public Map classToClass = new HashMap();

    /**
     * Mapping of ClassDocs to list of ClassDoc which are direct or
     * indirect subclasses of that class.
     * Entries may be null.
     */
    public Map classToSubclass = new HashMap();

    /**
     * Mapping of ClassDocs to list of ClassDoc which are direct or
     * indirect subinterfaces of that interface.
     * Entries may be null.
     */
    public Map classToSubinterface = new HashMap();

    /**
     * Mapping of ClassDocs to list of ClassDoc which implement
     * this interface.
     * Entries may be null.
     */
    public Map classToImplementingClass = new HashMap();

    /**
     * Mapping of ClassDocs to list of FieldDoc declared as that class.
     * Entries may be null.
     */
    public Map classToField = new HashMap();

    /**
     * Mapping of ClassDocs to list of MethodDoc returning that class.
     * Entries may be null.
     */
    public Map classToMethodReturn = new HashMap();

    /**
     * Mapping of ClassDocs to list of MethodDoc having that class
     * as an arg.
     * Entries may be null.
     */
    public Map classToMethodArgs = new HashMap();

    /**
     * Mapping of ClassDocs to list of MethodDoc which throws that class.
     * Entries may be null.
     */
    public Map classToMethodThrows = new HashMap();

    /**
     * Mapping of ClassDocs to list of ConstructorDoc having that class
     * as an arg.
     * Entries may be null.
     */
    public Map classToConstructorArgs = new HashMap();

    /**
     * Mapping of ClassDocs to list of ConstructorDoc which throws that class.
     * Entries may be null.
     */
    public Map classToConstructorThrows = new HashMap();

    /**
     * The mapping of AnnotationTypeDocs to constructors that use them.
     */
    public Map classToConstructorAnnotations = new HashMap();

    /**
     * The mapping of AnnotationTypeDocs to Constructor parameters that use them.
     */
    public Map classToConstructorParamAnnotation = new HashMap();

    /**
     * The mapping of ClassDocs to Constructor arguments that use them as type parameters.
     */
    public Map classToConstructorDocArgTypeParam = new HashMap();

    /**
     * The mapping of ClassDocs to ClassDocs that use them as type parameters.
     */
    public Map classToClassTypeParam = new HashMap();

    /**
     * The mapping of AnnotationTypeDocs to ClassDocs that use them.
     */
    public Map classToClassAnnotations = new HashMap();

    /**
     * The mapping of ClassDocs to ExecutableMemberDocs that use them as type parameters.
     */
    public Map classToExecMemberDocTypeParam = new HashMap();

    /**
     * The mapping of ClassDocs to ExecutableMemberDocs arguments that use them as type parameters.
     */
    public Map classToExecMemberDocArgTypeParam = new HashMap();

    /**
     * The mapping of AnnotationTypeDocs to ExecutableMemberDocs that use them.
     */
    public Map classToExecMemberDocAnnotations = new HashMap();

    /**
     * The mapping of ClassDocs to ExecutableMemberDocs that have return type
     * with type parameters of that class.
     */
    public Map classToExecMemberDocReturnTypeParam = new HashMap();

    /**
     * The mapping of AnnotationTypeDocs to MethodDoc parameters that use them.
     */
    public Map classToExecMemberDocParamAnnotation = new HashMap();

    /**
     * The mapping of ClassDocs to FieldDocs that use them as type parameters.
     */
    public Map classToFieldDocTypeParam = new HashMap();

    /**
     * The mapping of AnnotationTypeDocs to FieldDocs that use them.
     */
    public Map annotationToFieldDoc = new HashMap();


    public ClassUseMapper(RootDoc root, ClassTree classtree) {
        this.classtree = classtree;

        // Map subclassing, subinterfacing implementing, ...
        for (Iterator it = classtree.baseclasses().iterator(); it.hasNext();) {
            subclasses((ClassDoc)it.next());
        }
        for (Iterator it = classtree.baseinterfaces().iterator(); it.hasNext();) {
            // does subinterfacing as side-effect
            implementingClasses((ClassDoc)it.next());
        }
        // Map methods, fields, constructors using a class.
        ClassDoc[] classes = root.classes();
        for (int i = 0; i < classes.length; i++) {
            PackageDoc pkg = classes[i].containingPackage();
            mapAnnotations(classToPackageAnnotations, pkg, pkg);
            ClassDoc cd = classes[i];
            mapTypeParameters(classToClassTypeParam, cd, cd);
            mapAnnotations(classToClassAnnotations, cd, cd);
            FieldDoc[] fields = cd.fields();
            for (int j = 0; j < fields.length; j++) {
                FieldDoc fd = fields[j];
                mapTypeParameters(classToFieldDocTypeParam, fd, fd);
                mapAnnotations(annotationToFieldDoc, fd, fd);
                if (! fd.type().isPrimitive()) {
                    add(classToField, fd.type().asClassDoc(), fd);
                }
            }
            ConstructorDoc[] cons = cd.constructors();
            for (int j = 0; j < cons.length; j++) {
                mapAnnotations(classToConstructorAnnotations, cons[j], cons[j]);
                mapExecutable(cons[j]);
            }
            MethodDoc[] meths = cd.methods();
            for (int j = 0; j < meths.length; j++) {
                MethodDoc md = meths[j];
                mapExecutable(md);
                mapTypeParameters(classToExecMemberDocTypeParam, md, md);
                mapAnnotations(classToExecMemberDocAnnotations, md, md);
                if (! (md.returnType().isPrimitive() || md.returnType() instanceof TypeVariable)) {
                    mapTypeParameters(classToExecMemberDocReturnTypeParam,
                        md.returnType(), md);
                    add(classToMethodReturn, md.returnType().asClassDoc(), md);
                }
            }
        }
    }

    /**
     * Return all subclasses of a class AND fill-in classToSubclass map.
     */
    private Collection subclasses(ClassDoc cd) {
        Collection ret = (Collection)classToSubclass.get(cd.qualifiedName());
        if (ret == null) {
            ret = new TreeSet();
            List subs = classtree.subclasses(cd);
            if (subs != null) {
                ret.addAll(subs);
                for (Iterator it = subs.iterator(); it.hasNext();) {
                    ret.addAll(subclasses((ClassDoc)it.next()));
                }
            }
            addAll(classToSubclass, cd, ret);
        }
        return ret;
    }

    /**
     * Return all subinterfaces of an interface AND fill-in classToSubinterface map.
     */
    private Collection subinterfaces(ClassDoc cd) {
        Collection ret = (Collection)classToSubinterface.get(cd.qualifiedName());
        if (ret == null) {
            ret = new TreeSet();
            List subs = classtree.subinterfaces(cd);
            if (subs != null) {
                ret.addAll(subs);
                for (Iterator it = subs.iterator(); it.hasNext();) {
                    ret.addAll(subinterfaces((ClassDoc)it.next()));
                }
            }
            addAll(classToSubinterface, cd, ret);
        }
        return ret;
    }

    /**
     * Return all implementing classes of an interface (including
     * all subclasses of implementing classes and all classes
     * implementing subinterfaces) AND fill-in both classToImplementingClass
     * and classToSubinterface maps.
     */
    private Collection implementingClasses(ClassDoc cd) {
        Collection ret = (List)classToImplementingClass.get(cd.qualifiedName());
        if (ret == null) {
            ret = new TreeSet();
            List impl = classtree.implementingclasses(cd);
            if (impl != null) {
                ret.addAll(impl);
                for (Iterator it = impl.iterator(); it.hasNext();) {
                    ret.addAll(subclasses((ClassDoc)it.next()));
                }
            }
            for (Iterator it = subinterfaces(cd).iterator(); it.hasNext();) {
                ret.addAll(implementingClasses((ClassDoc)it.next()));
            }
            addAll(classToImplementingClass, cd, ret);
        }
        return ret;
    }

    /**
     * Determine classes used by a method or constructor, so they can be
     * inverse mapped.
     */
    private void mapExecutable(ExecutableMemberDoc em) {
        Parameter[] params = em.parameters();
        boolean isConstructor = em.isConstructor();
        List classArgs = new ArrayList();
        for (int k = 0; k < params.length; k++) {
            Type pcd = params[k].type();
            // primitives don't get mapped, also avoid dups
            if ((! params[k].type().isPrimitive()) &&
                 ! classArgs.contains(pcd) &&
                 ! (pcd instanceof TypeVariable)) {
                add(isConstructor? classToConstructorArgs :classToMethodArgs,
                        pcd.asClassDoc(), em);
                classArgs.add(pcd);
                mapTypeParameters(isConstructor?
                   classToConstructorDocArgTypeParam : classToExecMemberDocArgTypeParam,
                   pcd, em);
            }
            mapAnnotations(
                isConstructor ?
                    classToConstructorParamAnnotation :
                    classToExecMemberDocParamAnnotation,
                params[k], em);
        }
        ClassDoc[] thr = em.thrownExceptions();
        for (int k = 0; k < thr.length; k++) {
            add(isConstructor? classToConstructorThrows : classToMethodThrows,
                    thr[k], em);
        }
    }

    private List refList(Map map, ClassDoc cd) {
        List list = (List)map.get(cd.qualifiedName());
        if (list == null) {
            list = new ArrayList();
            map.put(cd.qualifiedName(), list);
        }
        return list;
    }

    private Set packageSet(ClassDoc cd) {
        Set pkgSet = (Set)classToPackage.get(cd.qualifiedName());
        if (pkgSet == null) {
            pkgSet = new TreeSet();
            classToPackage.put(cd.qualifiedName(), pkgSet);
        }
        return pkgSet;
    }

    private Set classSet(ClassDoc cd) {
        Set clsSet = (Set)classToClass.get(cd.qualifiedName());
        if (clsSet == null) {
            clsSet = new TreeSet();
            classToClass.put(cd.qualifiedName(), clsSet);
        }
        return clsSet;
    }

    private void add(Map map, ClassDoc cd, ProgramElementDoc ref) {
        // add to specified map
        refList(map, cd).add(ref);

        // add ref's package to package map and class map
        packageSet(cd).add(ref.containingPackage());

        classSet(cd).add(ref instanceof MemberDoc?
                ((MemberDoc)ref).containingClass() :
                    ref);
    }

    private void addAll(Map map, ClassDoc cd, Collection refs) {
        if (refs == null) {
            return;
        }
        // add to specified map
        refList(map, cd).addAll(refs);

        Set pkgSet = packageSet(cd);
        Set clsSet = classSet(cd);
        // add ref's package to package map and class map
        for (Iterator it = refs.iterator(); it.hasNext();) {
            ProgramElementDoc pedoc = (ProgramElementDoc)it.next();
            pkgSet.add(pedoc.containingPackage());
            clsSet.add(pedoc instanceof MemberDoc?
                    ((MemberDoc)pedoc).containingClass() :
                        pedoc);

        }
    }

    /**
     * Map the ClassDocs to the ProgramElementDocs that use them as
     * type parameters.
     *
     * @param map the map the insert the information into.
     * @param doc the doc whose type parameters are being checked.
     * @param holder the holder that owns the type parameters.
     */
    private void mapTypeParameters(Map map, Object doc,
            ProgramElementDoc holder) {
        TypeVariable[] typeVariables;
        if (doc instanceof ClassDoc) {
            typeVariables = ((ClassDoc) doc).typeParameters();
        } else if (doc instanceof WildcardType) {
            Type[] extendsBounds = ((WildcardType) doc).extendsBounds();
            for (int k = 0; k < extendsBounds.length; k++) {
                addTypeParameterToMap(map, extendsBounds[k], holder);
            }
            Type[] superBounds = ((WildcardType) doc).superBounds();
            for (int k = 0; k < superBounds.length; k++) {
                addTypeParameterToMap(map, superBounds[k], holder);
            }
            return;
        } else if (doc instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) doc).typeArguments();
            for (int k = 0; k < typeArguments.length; k++) {
                addTypeParameterToMap(map, typeArguments[k], holder);
            }
            return;
        } else if (doc instanceof ExecutableMemberDoc) {
            typeVariables = ((ExecutableMemberDoc) doc).typeParameters();
        } else if (doc instanceof FieldDoc) {
            Type fieldType = ((FieldDoc) doc).type();
            mapTypeParameters(map, fieldType, holder);
            return;
        } else {
            return;
        }
        for (int i = 0; i < typeVariables.length; i++) {
            Type[] bounds = typeVariables[i].bounds();
            for (int j = 0; j < bounds.length; j++) {
                addTypeParameterToMap(map, bounds[j], holder);
            }
        }
    }

    /**
     * Map the AnnotationType to the ProgramElementDocs that use them as
     * type parameters.
     *
     * @param map the map the insert the information into.
     * @param doc the doc whose type parameters are being checked.
     * @param holder the holder that owns the type parameters.
     */
    private void mapAnnotations(Map map, Object doc,
            Object holder) {
        TypeVariable[] typeVariables;
        AnnotationDesc[] annotations;
        boolean isPackage = false;
        if (doc instanceof ProgramElementDoc) {
            annotations = ((ProgramElementDoc) doc).annotations();
        } else if (doc instanceof PackageDoc) {
            annotations = ((PackageDoc) doc).annotations();
            isPackage = true;
        } else if (doc instanceof Parameter) {
            annotations = ((Parameter) doc).annotations();
        } else {
            throw new DocletAbortException();
        }
        for (int i = 0; i < annotations.length; i++) {
            AnnotationTypeDoc annotationDoc = annotations[i].annotationType();
            if (isPackage)
                refList(map, annotationDoc).add(holder);
            else
                add(map, annotationDoc, (ProgramElementDoc) holder);
        }
    }

    private void addTypeParameterToMap(Map map, Type type,
            ProgramElementDoc holder) {
        if (type instanceof ClassDoc) {
            add(map, (ClassDoc) type, holder);
        } else if (type instanceof ParameterizedType) {
            add(map, ((ParameterizedType) type).asClassDoc(), holder);
        }
        mapTypeParameters(map, type, holder);
    }
}
