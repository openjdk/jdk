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

package com.sun.tools.internal.jxc.api.impl.j2s;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import com.sun.tools.internal.xjc.api.ErrorListener;
import com.sun.tools.internal.xjc.api.J2SJAXBModel;
import com.sun.tools.internal.xjc.api.Reference;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.core.ArrayInfo;
import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.model.core.Element;
import com.sun.xml.internal.bind.v2.model.core.ElementInfo;
import com.sun.xml.internal.bind.v2.model.core.EnumLeafInfo;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.Ref;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.schemagen.XmlSchemaGenerator;
import com.sun.xml.internal.txw2.output.ResultFactory;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class JAXBModelImpl implements J2SJAXBModel {

    private final Map<QName,Reference> additionalElementDecls;

    private final List<String> classList = new ArrayList<String>();

    private final TypeInfoSet<TypeMirror, TypeElement, VariableElement, ExecutableElement> types;

    private final AnnotationReader<TypeMirror, TypeElement, VariableElement, ExecutableElement> reader;

    /**
     * Lazily created schema generator.
     */
    private XmlSchemaGenerator<TypeMirror, TypeElement, VariableElement, ExecutableElement> xsdgen;

    /**
     * Look up table from an externally visible {@link Reference} object
     * to our internal format.
     */
    private final Map<Reference, NonElement<TypeMirror, TypeElement>> refMap = new HashMap<Reference, NonElement<TypeMirror, TypeElement>>();

    public JAXBModelImpl(TypeInfoSet<TypeMirror, TypeElement, VariableElement, ExecutableElement> types,
                         AnnotationReader<TypeMirror, TypeElement, VariableElement, ExecutableElement> reader,
                         Collection<Reference> rootClasses,
                         Map<QName, Reference> additionalElementDecls) {
        this.types = types;
        this.reader = reader;
        this.additionalElementDecls = additionalElementDecls;

        Navigator<TypeMirror, TypeElement, VariableElement, ExecutableElement> navigator = types.getNavigator();

        for (ClassInfo<TypeMirror, TypeElement> i : types.beans().values()) {
            classList.add(i.getName());
        }

        for (ArrayInfo<TypeMirror, TypeElement> a : types.arrays().values()) {
            String javaName = navigator.getTypeName(a.getType());
            classList.add(javaName);
        }

        for (EnumLeafInfo<TypeMirror, TypeElement> l : types.enums().values()) {
            QName tn = l.getTypeName();
            if(tn!=null) {
                String javaName = navigator.getTypeName(l.getType());
                classList.add(javaName);
            }
        }

        for (Reference ref : rootClasses)
            refMap.put(ref,getXmlType(ref));

        // check for collision between "additional" ones and the ones given to JAXB
        // and eliminate duplication
        Iterator<Map.Entry<QName, Reference>> itr = additionalElementDecls.entrySet().iterator();
        while(itr.hasNext()) {
            Map.Entry<QName, Reference> entry = itr.next();
            if(entry.getValue()==null)      continue;

            NonElement<TypeMirror, TypeElement> xt = getXmlType(entry.getValue());

            assert xt!=null;
            refMap.put(entry.getValue(),xt);
            if(xt instanceof ClassInfo) {
                ClassInfo<TypeMirror, TypeElement> xct = (ClassInfo<TypeMirror, TypeElement>) xt;
                Element<TypeMirror, TypeElement> elem = xct.asElement();
                if(elem!=null && elem.getElementName().equals(entry.getKey())) {
                    itr.remove();
                    continue;
                }
            }
            ElementInfo<TypeMirror, TypeElement> ei = types.getElementInfo(null, entry.getKey());
            if(ei!=null && ei.getContentType()==xt)
                itr.remove();
        }
    }

    public List<String> getClassList() {
        return classList;
    }

    public QName getXmlTypeName(Reference javaType) {
        NonElement<TypeMirror, TypeElement> ti = refMap.get(javaType);

        if(ti!=null)
            return ti.getTypeName();

        return null;
    }

    private NonElement<TypeMirror, TypeElement> getXmlType(Reference r) {
        if(r==null)
            throw new IllegalArgumentException();

        XmlJavaTypeAdapter xjta = r.annotations.getAnnotation(XmlJavaTypeAdapter.class);
        XmlList xl = r.annotations.getAnnotation(XmlList.class);

        Ref<TypeMirror, TypeElement> ref = new Ref<TypeMirror, TypeElement>(
            reader,types.getNavigator(),r.type,xjta,xl);

        return types.getTypeInfo(ref);
    }

    public void generateSchema(SchemaOutputResolver outputResolver, ErrorListener errorListener) throws IOException {
        getSchemaGenerator().write(outputResolver,errorListener);
    }

    public void generateEpisodeFile(Result output) {
        getSchemaGenerator().writeEpisodeFile(ResultFactory.createSerializer(output));
    }

    private synchronized XmlSchemaGenerator<TypeMirror, TypeElement, VariableElement, ExecutableElement> getSchemaGenerator() {
        if(xsdgen==null) {
            xsdgen = new XmlSchemaGenerator<TypeMirror, TypeElement, VariableElement, ExecutableElement>(types.getNavigator(), types);

            for (Map.Entry<QName, Reference> e : additionalElementDecls.entrySet()) {
                Reference value = e.getValue();
                if(value!=null) {
                    NonElement<TypeMirror, TypeElement> typeInfo = refMap.get(value);
                    if(typeInfo==null)
                        throw new IllegalArgumentException(e.getValue()+" was not specified to JavaCompiler.bind");
                    TypeMirror type = value.type;
                    xsdgen.add(e.getKey(), !(type != null && type.getKind().isPrimitive()), typeInfo);
                } else {
                    xsdgen.add(e.getKey(),false,null);
                }
            }
        }
        return xsdgen;
    }
}
