/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.xjc.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JPackage;
import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.model.core.Element;
import com.sun.xml.internal.xsom.XSComponent;

import org.xml.sax.Locator;

/**
 * Mutable {@link ClassInfo} represenatation.
 *
 * <p>
 * Schema parsers build these objects.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CClassInfo extends AbstractCTypeInfoImpl implements ClassInfo<NType,NClass>, CClassInfoParent, CElement, CNonElement, NClass, CTypeInfo {

    @XmlIDREF
    private CClassInfo baseClass;

    /**
     * @see #getTypeName()
     */
    private final QName typeName;
    /**
     * Can be null.
     */
    private final QName elementName;

    private boolean isOrdered = true;

    private final List<CPropertyInfo> properties = new ArrayList<CPropertyInfo>();

    /**
     * TODO: revisit this design.
     * we should at least do a basic encapsulation to avoid careless
     * mistakes. Maybe we should even differ the javadoc generation
     * by queueing runners.
     */
    public String javadoc;

    @XmlIDREF
    private final CClassInfoParent parent;

    /**
     * short name.
     */
    public final String shortName;

    /**
     * The location in the source file where this class was declared.
     */
    @XmlTransient
    private final Locator location;

    private boolean isAbstract;

    /**
     * Optional user-specified implementation override class.
     */
    private String implClass;

    /**
     * The {@link Model} object to which this bean belongs.
     */
    public final Model model;

    /**
     * @see #hasAttributeWildcard()
     */
    private boolean hasAttributeWildcard;


    public CClassInfo(Model model,JPackage pkg, String shortName, Locator location, QName typeName, QName elementName, XSComponent source, CCustomizations customizations) {
        this(model,model.getPackage(pkg),shortName,location,typeName,elementName,source,customizations);
    }

    public CClassInfo(Model model,CClassInfoParent p, String shortName, Locator location, QName typeName, QName elementName, XSComponent source, CCustomizations customizations) {
        super(model,source,customizations);
        this.model = model;
        this.parent = p;
        this.shortName = model.allocator.assignClassName(parent,shortName);
        this.location = location;
        this.typeName = typeName;
        this.elementName = elementName;

        model.add(this);
    }

    public CClassInfo(Model model,JCodeModel cm, String fullName, Locator location, QName typeName, QName elementName, XSComponent source, CCustomizations customizations) {
        super(model,source,customizations);
        this.model = model;
        int idx = fullName.indexOf('.');
        if(idx<0) {
            this.parent = model.getPackage(cm.rootPackage());
            this.shortName = model.allocator.assignClassName(parent,fullName);
        } else {
            this.parent = model.getPackage(cm._package(fullName.substring(0,idx)));
            this.shortName = model.allocator.assignClassName(parent,fullName.substring(idx+1));
        }
        this.location = location;
        this.typeName = typeName;
        this.elementName = elementName;

        model.add(this);
    }

    public Locator getLocator() {
        return location;
    }

    public boolean hasAttributeWildcard() {
        return hasAttributeWildcard;
    }

    public void hasAttributeWildcard(boolean hasAttributeWildcard) {
        this.hasAttributeWildcard = hasAttributeWildcard;
    }

    public boolean hasSubClasses() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true iff a new attribute wildcard property needs to be
     * declared on this class.
     */
    public boolean declaresAttributeWildcard() {
        return hasAttributeWildcard && !inheritsAttributeWildcard();
    }

    /**
     * Returns true iff this class inherits a wildcard attribute property
     * from its ancestor classes.
     */
    public boolean inheritsAttributeWildcard() {
        for( CClassInfo c=getBaseClass(); c!=null; c=c.getBaseClass() ) {
            if(hasAttributeWildcard)
                return true;
        }
        return false;
    }


    public NClass getClazz() {
        return this;
    }

    public CClassInfo getScope() {
        return null;
    }

    @XmlID
    public String getName() {
        return fullName();
    }

    /**
     * Returns the "squeezed name" of this bean token.
     * <p>
     * The squeezed name of a bean is the concatenation of
     * the names of its outer classes and itself.
     * <p>
     * Thus if the bean is "org.acme.foo.Bean", then the squeezed name is "Bean",
     * if the bean is "org.acme.foo.Outer1.Outer2.Bean", then "Outer1Outer2Bean".
     * <p>
     * This is used by the code generator
     */
    @XmlElement
    public String getSqueezedName() {
        return calcSqueezedName.onBean(this);
    }

    private static final CClassInfoParent.Visitor<String> calcSqueezedName = new Visitor<String>() {
        public String onBean(CClassInfo bean) {
            return bean.parent.accept(this)+bean.shortName;
        }

        public String onElement(CElementInfo element) {
            return element.parent.accept(this)+element.shortName();
        }

        public String onPackage(JPackage pkg) {
            return "";
        }
    };

    /**
     * Returns a mutable list.
     */
    public List<CPropertyInfo> getProperties() {
        return properties;
    }

    /**
     * Gets a propery by name.
     *
     * TODO: consider moving this up to {@link ClassInfo}
     */
    public CPropertyInfo getProperty(String name) {
        // TODO: does this method need to be fast?
        for( CPropertyInfo p : properties )
            if(p.getName(false).equals(name))
                return p;
        return null;
    }

    public boolean hasProperties() {
        return !getProperties().isEmpty();
    }

    public boolean isElement() {
        return elementName!=null;
    }

    public Element<NType,NClass> asElement() {
        if(isElement())
            return this;
        else
            return null;
    }

    public boolean isOrdered() {
        return isOrdered;
    }

    /**
     * @deprecated
     *      if you are calling this method directly, you must be doing something wrong.
     */
    public boolean isFinal() {
        return false;
    }

    public void setOrdered(boolean value) {
        isOrdered = value;
    }

    public QName getElementName() {
        return elementName;
    }

    public QName getTypeName() {
        return typeName;
    }

    public boolean isSimpleType() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the FQCN of this bean.
     */
    public String fullName() {
        String r = parent.fullName();
        if(r.length()==0)   return shortName;
        else                return r+'.'+shortName;
    }

    public CClassInfoParent parent() {
        return parent;
    }

    public void setUserSpecifiedImplClass(String implClass) {
        assert this.implClass==null;
        assert implClass!=null;
        this.implClass = implClass;
    }

    public String getUserSpecifiedImplClass() {
        return implClass;
    }


    /**
     * Adds a new property.
     */
    public void addProperty(CPropertyInfo prop) {
        if(prop.ref().isEmpty())
            // this property isn't contributing anything
            // this happens when you try to map an empty sequence to a property
            return;
        prop.setParent(this);
        properties.add(prop);
    }

    public void setBaseClass(CClassInfo base) {
        assert baseClass==null;
        assert base!=null;
        baseClass = base;
    }

    public CClassInfo getBaseClass() {
        return baseClass;
    }

    public CClassInfo getSubstitutionHead() {
        CClassInfo c=baseClass;
        while(c!=null && !c.isElement())
            c=c.baseClass;
        return c;
    }


    /**
     * Interfaces to be implemented.
     * Lazily constructed.
     */
    private Set<JClass> _implements = null;

    public void _implements(JClass c) {
        if(_implements==null)
            _implements = new HashSet<JClass>();
        _implements.add(c);
    }


    /** Constructor declarations. array of {@link Constructor}s. */
    private final List<Constructor> constructors = new ArrayList<Constructor>(1);

    /** Creates a new constructor declaration and adds it. */
    public void addConstructor( String... fieldNames ) {
        constructors.add(new Constructor(fieldNames));
    }

    /** list all constructor declarations. */
    public Collection<? extends Constructor> getConstructors() {
        return constructors;
    }

    public final <T> T accept(Visitor<T> visitor) {
        return visitor.onBean(this);
    }

    public JPackage getOwnerPackage() {
        return parent.getOwnerPackage();
    }

    public final NClass getType() {
        return this;
    }

    public final JClass toType(Outline o, Aspect aspect) {
        switch(aspect) {
        case IMPLEMENTATION:
            return o.getClazz(this).implRef;
        case EXPOSED:
            return o.getClazz(this).ref;
        default:
            throw new IllegalStateException();
        }
    }

    public boolean isBoxedType() {
        return false;
    }

    public String toString() {
        return fullName();
    }

    public void setAbstract() {
        isAbstract = true;
    }

    public boolean isAbstract() {
        return isAbstract;
    }
}
