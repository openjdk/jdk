/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.impl;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.annotation.FieldLocatable;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeEnumLeafInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeNonElement;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
final class RuntimeEnumLeafInfoImpl<T extends Enum<T>,B> extends EnumLeafInfoImpl<Type,Class,Field,Method>
    implements RuntimeEnumLeafInfo, Transducer<T> {

    public Transducer<T> getTransducer() {
        return this;
    }

    /**
     * {@link Transducer} that knows how to convert a lexical value
     * into the Java value that we can handle.
     */
    private final Transducer<B> baseXducer;

    private final Map<B,T> parseMap = new HashMap<B,T>();
    private final Map<T,B> printMap;

    RuntimeEnumLeafInfoImpl(RuntimeModelBuilder builder, Locatable upstream, Class<T> enumType) {
        super(builder,upstream,enumType,enumType);
        this.printMap = new EnumMap<T,B>(enumType);

        baseXducer = ((RuntimeNonElement)baseType).getTransducer();
    }

    @Override
    public RuntimeEnumConstantImpl createEnumConstant(String name, String literal, Field constant, EnumConstantImpl<Type,Class,Field,Method> last) {
        T t;
        try {
            try {
                constant.setAccessible(true);
            } catch (SecurityException e) {
                // in case the constant is already accessible, swallow this error.
                // if the constant is indeed not accessible, we will get IllegalAccessException
                // in the following line, and that is not too late.
            }
            t = (T)constant.get(null);
        } catch (IllegalAccessException e) {
            // impossible, because this is an enum constant
            throw new IllegalAccessError(e.getMessage());
        }

        B b = null;
        try {
            b = baseXducer.parse(literal);
        } catch (Exception e) {
            builder.reportError(new IllegalAnnotationException(
                Messages.INVALID_XML_ENUM_VALUE.format(literal,baseType.getType().toString()), e,
                    new FieldLocatable<Field>(this,constant,nav()) ));
        }

        parseMap.put(b,t);
        printMap.put(t,b);

        return new RuntimeEnumConstantImpl(this, name, literal, last);
    }

    public QName[] getTypeNames() {
        return new QName[]{getTypeName()};
    }

    public boolean isDefault() {
        return false;
    }

    @Override
    public Class getClazz() {
        return clazz;
    }

    public boolean useNamespace() {
        return baseXducer.useNamespace();
    }

    public void declareNamespace(T t, XMLSerializer w) throws AccessorException {
        baseXducer.declareNamespace(printMap.get(t),w);
    }

    public CharSequence print(T t) throws AccessorException {
        return baseXducer.print(printMap.get(t));
    }

    public T parse(CharSequence lexical) throws AccessorException, SAXException {
        // TODO: error handling

        B b = baseXducer.parse(lexical);

        if (tokenStringType) {
            b = (B) ((String)b).trim();
        }

        return parseMap.get(b);
    }

    public void writeText(XMLSerializer w, T t, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        baseXducer.writeText(w,printMap.get(t),fieldName);
    }

    public void writeLeafElement(XMLSerializer w, Name tagName, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        baseXducer.writeLeafElement(w,tagName,printMap.get(o),fieldName);
    }

    public QName getTypeName(T instance) {
        return null;
    }
}
