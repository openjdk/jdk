/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.xml.internal.ws.util.StringUtils;

import java.util.*;


/**
 *
 * @author WS Development Team
 */
public class TypeModeler implements WebServiceConstants {

    public static TypeDeclaration getDeclaration(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType)
            return ((DeclaredType)typeMirror).getDeclaration();
        return null;
    }

    public static TypeDeclaration getDeclaringClassMethod(
        TypeMirror theClass,
        String methodName,
        TypeMirror[] args) {

        return getDeclaringClassMethod(getDeclaration(theClass), methodName, args);
    }

    public static TypeDeclaration getDeclaringClassMethod(
        TypeDeclaration theClass,
        String methodName,
        TypeMirror[] args) {

        TypeDeclaration retClass = null;
        if (theClass instanceof ClassDeclaration) {
            ClassType superClass = ((ClassDeclaration)theClass).getSuperclass();
            if (superClass != null)
                retClass = getDeclaringClassMethod(superClass, methodName, args);
        }
        if (retClass == null) {
            for (InterfaceType interfaceType : theClass.getSuperinterfaces())
                retClass =
                    getDeclaringClassMethod(interfaceType, methodName, args);
        }
        if (retClass == null) {
            Collection<? extends MethodDeclaration> methods;
            methods = theClass.getMethods();
            for (MethodDeclaration method : methods) {
                if (method.getSimpleName().equals(methodName) &&
                    method.getDeclaringType().equals(theClass)) {
                    retClass = theClass;
                    break;
                }
            }
        }
        return retClass;
    }

    public static Collection<InterfaceType> collectInterfaces(TypeDeclaration type) {
        Collection<InterfaceType> superInterfaces = type.getSuperinterfaces();
        Collection<InterfaceType> interfaces = type.getSuperinterfaces();
        for (InterfaceType interfaceType : superInterfaces) {
            interfaces.addAll(collectInterfaces(getDeclaration(interfaceType)));
        }
        return interfaces;
    }

    public static boolean isSubclass(String subTypeName, String superTypeName,
        AnnotationProcessorEnvironment env) {
        return isSubclass(env.getTypeDeclaration(subTypeName),
                          env.getTypeDeclaration(superTypeName));
    }

    public static boolean isSubclass(
        TypeDeclaration subType,
        TypeDeclaration superType) {

        if (subType.equals(superType))
            return false;
        return isSubtype(subType, superType);
    }

    public static TypeMirror getHolderValueType(
        TypeMirror type,
        TypeDeclaration defHolder
    ) {

        TypeDeclaration typeDecl = getDeclaration(type);
        if (typeDecl == null)
            return null;

        if (isSubtype(typeDecl, defHolder)) {
            if  (type instanceof DeclaredType) {
                Collection<TypeMirror> argTypes = ((DeclaredType)type).getActualTypeArguments();
                if (argTypes.size() == 1) {
                    TypeMirror mirror = argTypes.iterator().next();
//                        System.out.println("argsTypes.iterator().next(): "+mirror);
                    return mirror;
                }
                else if (argTypes.size() == 0) {
                    FieldDeclaration member = getValueMember(typeDecl);
                    if (member != null) {
//                            System.out.println("member: "+member+" getType(): "+member.getType());
                        return member.getType();
                    }
                }
            }
        }
        return null;
    }

    public static FieldDeclaration getValueMember(TypeMirror classType) {
        return getValueMember(getDeclaration(classType));
    }

    public static FieldDeclaration getValueMember(TypeDeclaration type) {
        FieldDeclaration member = null;
        for (FieldDeclaration field : type.getFields()){
            if (field.getSimpleName().equals("value")) {
                member = field;
                break;
            }
        }
        if (member == null) {
            if (type instanceof ClassDeclaration)
                member = getValueMember(((ClassDeclaration)type).getSuperclass());
        }
        return member;
    }


    /* is d1 a subtype of d2 */
    public static boolean isSubtype(TypeDeclaration d1, TypeDeclaration d2) {
        if (d1.equals(d2))
            return true;
        ClassDeclaration superClassDecl = null;
        if (d1 instanceof ClassDeclaration) {
            ClassType superClass = ((ClassDeclaration)d1).getSuperclass();
            if (superClass != null) {
                superClassDecl = superClass.getDeclaration();
                if (superClassDecl.equals(d2))
                    return true;
            }
        }
        for (InterfaceType superIntf : d1.getSuperinterfaces()) {
            if (superIntf.getDeclaration().equals(d2)) {
                return true;
            }
            if (isSubtype(superIntf.getDeclaration(), d2)) {
                return true;
            } else if (superClassDecl != null && isSubtype(superClassDecl, d2)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String,MethodDeclaration> getExceptionProperties(TypeMirror type) {
        return getExceptionProperties(getDeclaration(type));
    }

    public static Map<String,MethodDeclaration> getExceptionProperties(TypeDeclaration type) {
        Map<String,MethodDeclaration> members = new HashMap<String,MethodDeclaration>();
        collectExceptionProperties(type, members);
        return members;
    }

    public static void collectExceptionProperties(TypeMirror type, Map<String,MethodDeclaration> members) {
        collectExceptionProperties(getDeclaration(type), members);
    }

    public static void collectExceptionProperties(TypeDeclaration type, Map<String,MethodDeclaration> members) {
//        System.out.println("type: "+type.toString());
        Collection<? extends MethodDeclaration> methods;
        methods = type.getMethods();
        for (MethodDeclaration method : methods) {
            Collection<Modifier> modifiers = method.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC)
                || (modifiers.contains(Modifier.FINAL) &&
                    modifiers.contains(Modifier.STATIC))
                || modifiers.contains(Modifier.TRANSIENT)) { // no final static, transient, non-public
                continue;
            }
            String name = method.getSimpleName();
            if (name.length() <= 3 && !name.startsWith(IS_PREFIX) ||
                skipProperties.contains(name)) {
                // Optimization. Don't bother with invalid propertyNames.
                continue;
            }
            TypeMirror resultType = method.getReturnType();
            Collection<ParameterDeclaration> params = method.getParameters();
            if (params.size() == 0) {
                if (name.startsWith(GET_PREFIX) &&
                    !(resultType instanceof PrimitiveType &&
                           ((PrimitiveType)resultType).getKind() == PrimitiveType.Kind.BOOLEAN)) {
                    // Simple getter
//                    System.out.println("exception property: "+ StringUtils.decapitalize(name.substring(3)));
                members.put(StringUtils.decapitalize(name.substring(3)), method);
            } else if (resultType instanceof PrimitiveType &&
                           ((PrimitiveType)resultType).getKind() == PrimitiveType.Kind.BOOLEAN &&
                           name.startsWith(IS_PREFIX)) {
                    // Boolean getter
//                    System.out.println("exception property: "+ StringUtils.decapitalize(name.substring(2)));
                    members.put(StringUtils.decapitalize(name.substring(2)), method);
                }
            }
        }
//        System.out.println("type class: "+type.getClass().toString());
        if (type instanceof ClassDeclaration && ((ClassDeclaration)type).getSuperclass() != null)  {
            collectExceptionProperties(((ClassDeclaration)type).getSuperclass(), members);
        }
        for (InterfaceType intfType : type.getSuperinterfaces()) {
            collectExceptionProperties(intfType, members);
        }
    }

    private static Set<String> skipProperties = new HashSet<String> ();
    static{
        skipProperties.add("getCause");
        skipProperties.add("getLocalizedMessage");
        skipProperties.add("getClass");
        skipProperties.add("getStackTrace");
    }
}
