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
package com.sun.tools.internal.xjc.generator.bean;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JPackage;
import com.sun.tools.internal.xjc.generator.annotation.spec.XmlSchemaWriter;
import com.sun.tools.internal.xjc.model.CAttributePropertyInfo;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CElement;
import com.sun.tools.internal.xjc.model.CElementPropertyInfo;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CPropertyVisitor;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.tools.internal.xjc.model.CTypeRef;
import com.sun.tools.internal.xjc.model.CValuePropertyInfo;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.outline.PackageOutline;
import com.sun.tools.internal.xjc.outline.Aspect;

/**
 * {@link PackageOutline} enhanced with schema2java specific
 * information.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class PackageOutlineImpl implements PackageOutline {
    private final Model _model;
    private final JPackage _package;
    private final ObjectFactoryGenerator objectFactoryGenerator;

    /*package*/ final Set<ClassOutlineImpl> classes = new HashSet<ClassOutlineImpl>();
    private final Set<ClassOutlineImpl> classesView = Collections.unmodifiableSet(classes);

    private String mostUsedNamespaceURI;
    private XmlNsForm elementFormDefault;

    /**
     * The namespace URI most commonly used in classes in this package.
     * This should be used as the namespace URI for {@link XmlSchema#namespace()}.
     *
     * <p>
     * Null if no default
     *
     * @see #calcDefaultValues().
     */
    public String getMostUsedNamespaceURI() {
        return mostUsedNamespaceURI;
    }

    /**
     * The element form default for this package.
     * <p>
     * The value is computed by examining what would yield the smallest generated code.
     */
    public XmlNsForm getElementFormDefault() {
        assert elementFormDefault!=null;
        return elementFormDefault;
    }

    public JPackage _package() {
        return _package;
    }

    public ObjectFactoryGenerator objectFactoryGenerator() {
        return objectFactoryGenerator;
    }

    public Set<ClassOutlineImpl> getClasses() {
        return classesView;
    }

    public JDefinedClass objectFactory() {
        return objectFactoryGenerator.getObjectFactory();
    }

    protected PackageOutlineImpl( BeanGenerator outline, Model model, JPackage _pkg ) {
        this._model = model;
        this._package = _pkg;
        switch(model.strategy) {
        case BEAN_ONLY:
            objectFactoryGenerator = new PublicObjectFactoryGenerator(outline,model,_pkg);
            break;
        case INTF_AND_IMPL:
            objectFactoryGenerator = new DualObjectFactoryGenerator(outline,model,_pkg);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Compute the most common namespace URI in this package
     * (to put into {@link XmlSchema#namespace()} and what value
     * we should put into {@link XmlSchema#elementFormDefault()}.
     *
     * This method is called after {@link #classes} field is filled up.
     */
    public void calcDefaultValues() {
        // short-circuit if xjc was told not to generate package level annotations in
        // package-info.java
        if(!_model.isPackageLevelAnnotations()) {
            mostUsedNamespaceURI = "";
            elementFormDefault = XmlNsForm.UNQUALIFIED;
            return;
        }

        // used to visit properties
        CPropertyVisitor<Void> propVisitor = new CPropertyVisitor<Void>() {
            public Void onElement(CElementPropertyInfo p) {
                for (CTypeRef tr : p.getTypes()) {
                    countURI(propUriCountMap, tr.getTagName());
                }
                return null;
            }

            public Void onReference(CReferencePropertyInfo p) {
                for (CElement e : p.getElements()) {
                    countURI(propUriCountMap, e.getElementName());
                }
                return null;
            }

            public Void onAttribute(CAttributePropertyInfo p) {
                return null;
            }

            public Void onValue(CValuePropertyInfo p) {
                return null;
            }
        };


        for (ClassOutlineImpl co : classes) {
            CClassInfo ci = co.target;
            countURI(uriCountMap, ci.getTypeName());
            countURI(uriCountMap, ci.getElementName());

            for( CPropertyInfo p : ci.getProperties() )
                p.accept(propVisitor);
        }
        mostUsedNamespaceURI = getMostUsedURI(uriCountMap);
        elementFormDefault = getFormDefault();

        // generate package-info.java
        // we won't get this far if the user specified -npa
        if(!mostUsedNamespaceURI.equals("") || elementFormDefault==XmlNsForm.QUALIFIED) {
            XmlSchemaWriter w = _model.strategy.getPackage(_package, Aspect.IMPLEMENTATION).annotate2(XmlSchemaWriter.class);
            if(!mostUsedNamespaceURI.equals(""))
                w.namespace(mostUsedNamespaceURI);
            if(elementFormDefault==XmlNsForm.QUALIFIED)
                w.elementFormDefault(elementFormDefault);
        }
    }

    // Map to keep track of how often each type or element uri is used in this package
    // mostly used to calculate mostUsedNamespaceURI
    private HashMap<String, Integer> uriCountMap = new HashMap<String, Integer>();

    // Map to keep track of how often each property uri is used in this package
    // used to calculate elementFormDefault
    private HashMap<String, Integer> propUriCountMap = new HashMap<String, Integer>();

    /**
     * pull the uri out of the specified QName and keep track of it in the
     * specified hash map
     *
     * @param qname
     */
    private void countURI(HashMap<String, Integer> map, QName qname) {
        if (qname == null) return;

        String uri = qname.getNamespaceURI();

        if (map.containsKey(uri)) {
            map.put(uri, map.get(uri) + 1);
        } else {
            map.put(uri, 1);
        }
    }

    /**
     * Iterate through the hash map looking for the namespace used
     * most frequently.  Ties are arbitrarily broken by the order
     * in which the map keys are iterated over.
     *
     * <p>
     * Because JAX-WS often reassigns the "" namespace URI,
     * and when that happens it unintentionally also renames (normally
     * unqualified) local elements, prefer non-"" URI when there's a tie.
     */
    private String getMostUsedURI(HashMap<String, Integer> map) {
        String mostPopular = null;
        int count = 0;

        for (Map.Entry<String,Integer> e : map.entrySet()) {
            String uri = e.getKey();
            int uriCount = e.getValue();
            if (mostPopular == null) {
                mostPopular = uri;
                count = uriCount;
            } else {
                if (uriCount > count || (uriCount==count && mostPopular.equals(""))) {
                    mostPopular = uri;
                    count = uriCount;
                }
            }
        }

        if (mostPopular == null) return "";
        return mostPopular;
    }

    /**
     * Calculate the element form defaulting.
     *
     * Compare the most frequently used property URI to the most frequently used
     * element/type URI.  If they match, then return QUALIFIED
     */
    private XmlNsForm getFormDefault() {
        if (getMostUsedURI(propUriCountMap).equals("")) return XmlNsForm.UNQUALIFIED;
        else return XmlNsForm.QUALIFIED;
    }
}
