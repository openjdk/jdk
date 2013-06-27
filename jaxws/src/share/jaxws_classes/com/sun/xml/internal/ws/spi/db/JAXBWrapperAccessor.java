/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.spi.db;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.namespace.QName;

/**
 * JAXBWrapperAccessor
 *
 * @author shih-chang.chen@oracle.com
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class JAXBWrapperAccessor extends WrapperAccessor {

    protected Class<?> contentClass;
    protected HashMap<Object, Class> elementDeclaredTypes;

    public JAXBWrapperAccessor(Class<?> wrapperBean) {
        contentClass = (Class<?>) wrapperBean;

        HashMap<Object, PropertySetter> setByQName = new HashMap<Object, PropertySetter>();
        HashMap<Object, PropertySetter> setByLocalpart = new HashMap<Object, PropertySetter>();
        HashMap<String, Method> publicSetters = new HashMap<String, Method>();

        HashMap<Object, PropertyGetter> getByQName = new HashMap<Object, PropertyGetter>();
        HashMap<Object, PropertyGetter> getByLocalpart = new HashMap<Object, PropertyGetter>();
        HashMap<String, Method> publicGetters = new HashMap<String, Method>();

        HashMap<Object, Class> elementDeclaredTypesByQName = new HashMap<Object, Class>();
        HashMap<Object, Class> elementDeclaredTypesByLocalpart = new HashMap<Object, Class>();

        for (Method method : contentClass.getMethods()) {
            if (PropertySetterBase.setterPattern(method)) {
                String key = method.getName()
                        .substring(3, method.getName().length()).toLowerCase();
                publicSetters.put(key, method);
            }
            if (PropertyGetterBase.getterPattern(method)) {
                String methodName = method.getName();
                String key = methodName.startsWith("is") ? methodName
                        .substring(2, method.getName().length()).toLowerCase()
                        : methodName.substring(3, method.getName().length())
                                .toLowerCase();
                publicGetters.put(key, method);
            }
        }
        HashSet<String> elementLocalNames = new HashSet<String>();
        for (Field field : getAllFields(contentClass)) {
            XmlElementWrapper xmlElemWrapper = field.getAnnotation(XmlElementWrapper.class);
            XmlElement xmlElem = field.getAnnotation(XmlElement.class);
            XmlElementRef xmlElemRef = field.getAnnotation(XmlElementRef.class);
            String fieldName = field.getName().toLowerCase();
            String namespace = "";
            String localName = field.getName();
            if (xmlElemWrapper != null) {
                namespace = xmlElemWrapper.namespace();
                if (xmlElemWrapper.name() != null && !xmlElemWrapper.name().equals("")
                        && !xmlElemWrapper.name().equals("##default")) {
                    localName = xmlElemWrapper.name();
                }
            }else if (xmlElem != null) {
                namespace = xmlElem.namespace();
                if (xmlElem.name() != null && !xmlElem.name().equals("")
                        && !xmlElem.name().equals("##default")) {
                    localName = xmlElem.name();
                }
            } else if (xmlElemRef != null) {
                namespace = xmlElemRef.namespace();
                if (xmlElemRef.name() != null && !xmlElemRef.name().equals("")
                        && !xmlElemRef.name().equals("##default")) {
                    localName = xmlElemRef.name();
                }
            }
            if (elementLocalNames.contains(localName)) {
                this.elementLocalNameCollision = true;
            } else {
                elementLocalNames.add(localName);
            }

            QName qname = new QName(namespace, localName);
            if (field.getType().equals(JAXBElement.class)) {
                if (field.getGenericType() instanceof ParameterizedType) {
                    Type arg = ((ParameterizedType) field.getGenericType())
                            .getActualTypeArguments()[0];
                    if (arg instanceof Class) {
                        elementDeclaredTypesByQName.put(qname, (Class) arg);
                        elementDeclaredTypesByLocalpart.put(localName,
                                (Class) arg);
                    } else if (arg instanceof GenericArrayType) {
                        Type componentType = ((GenericArrayType) arg)
                                .getGenericComponentType();
                        if (componentType instanceof Class) {
                            Class arrayClass = Array.newInstance(
                                    (Class) componentType, 0).getClass();
                            elementDeclaredTypesByQName.put(qname, arrayClass);
                            elementDeclaredTypesByLocalpart.put(localName,
                                    arrayClass);
                        }
                    }
                }

            }
            // _return
            if (fieldName.startsWith("_") && !localName.startsWith("_")) {
                fieldName = fieldName.substring(1);
            }
            Method setMethod = publicSetters.get(fieldName);
            Method getMethod = publicGetters.get(fieldName);
            PropertySetter setter = createPropertySetter(field, setMethod);
            PropertyGetter getter = createPropertyGetter(field, getMethod);
            setByQName.put(qname, setter);
            setByLocalpart.put(localName, setter);
            getByQName.put(qname, getter);
            getByLocalpart.put(localName, getter);
        }
        if (this.elementLocalNameCollision) {
            this.propertySetters = setByQName;
            this.propertyGetters = getByQName;
            elementDeclaredTypes = elementDeclaredTypesByQName;
        } else {
            this.propertySetters = setByLocalpart;
            this.propertyGetters = getByLocalpart;
            elementDeclaredTypes = elementDeclaredTypesByLocalpart;
        }
    }

    static protected List<Field> getAllFields(Class<?> clz) {
        List<Field> list = new ArrayList<Field>();
        while (!Object.class.equals(clz)) {
            list.addAll(Arrays.asList(getDeclaredFields(clz)));
            clz = clz.getSuperclass();
        }
        return list;
    }

    static protected Field[] getDeclaredFields(final Class<?> clz) {
        try {
            return (System.getSecurityManager() == null) ? clz .getDeclaredFields() :
                AccessController.doPrivileged(new PrivilegedExceptionAction<Field[]>() {
                        @Override
                        public Field[] run() throws IllegalAccessException {
                            return clz.getDeclaredFields();
                        }
                    });
        } catch (PrivilegedActionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    static protected PropertyGetter createPropertyGetter(Field field, Method getMethod) {
        if (!field.isAccessible()) {
            if (getMethod != null) {
                MethodGetter methodGetter = new MethodGetter(getMethod);
                if (methodGetter.getType().toString().equals(field.getType().toString())) {
                    return methodGetter;
                }
            }
        }
        return new FieldGetter(field);
    }

    static protected PropertySetter createPropertySetter(Field field,
            Method setter) {
        if (!field.isAccessible()) {
            if (setter != null) {
                MethodSetter injection = new MethodSetter(setter);
                if (injection.getType().toString().equals(field.getType().toString())) {
                    return injection;
                }
            }
        }
        return new FieldSetter(field);
    }

    private Class getElementDeclaredType(QName name) {
        Object key = (this.elementLocalNameCollision) ? name : name
                .getLocalPart();
        return elementDeclaredTypes.get(key);
    }

    @Override
    public PropertyAccessor getPropertyAccessor(String ns, String name) {
        final QName n = new QName(ns, name);
        final PropertySetter setter = getPropertySetter(n);
        final PropertyGetter getter = getPropertyGetter(n);
        final boolean isJAXBElement = setter.getType()
                .equals(JAXBElement.class);
        final boolean isListType = java.util.List.class.isAssignableFrom(setter
                .getType());
        final Class elementDeclaredType = isJAXBElement ? getElementDeclaredType(n)
                : null;
        return new PropertyAccessor() {
            @Override
            public Object get(Object bean) throws DatabindingException {
                Object val;
                if (isJAXBElement) {
                    JAXBElement<Object> jaxbElement = (JAXBElement<Object>) getter.get(bean);
                    val = (jaxbElement == null) ? null : jaxbElement.getValue();
                } else {
                    val = getter.get(bean);
                }
                if (val == null && isListType) {
                    val = new java.util.ArrayList();
                    set(bean, val);
                }
                return val;
            }

            @Override
            public void set(Object bean, Object value) throws DatabindingException {
                if (isJAXBElement) {
                    JAXBElement<Object> jaxbElement = new JAXBElement<Object>(
                            n, elementDeclaredType, contentClass, value);
                    setter.set(bean, jaxbElement);
                } else {
                    setter.set(bean, value);
                }
            }
        };
    }
}
