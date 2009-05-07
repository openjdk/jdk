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
package com.sun.xml.internal.bind.v2.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.istack.internal.FinalArrayList;
import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeClassInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.property.AttributeProperty;
import com.sun.xml.internal.bind.v2.runtime.property.Property;
import com.sun.xml.internal.bind.v2.runtime.property.PropertyFactory;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.StructureLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiTypeLoader;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;

/**
 * {@link JaxBeanInfo} implementation for j2s bean.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class ClassBeanInfoImpl<BeanT> extends JaxBeanInfo<BeanT> {

    /**
     * Properties of this bean class but not its ancestor classes.
     */
    public final Property<BeanT>[] properties;

    /**
     * Non-null if this bean has an ID property.
     */
    private Property<? super BeanT> idProperty;

    /**
     * Immutable configured loader for this class.
     *
     * <p>
     * Set from the link method, but considered final.
     */
    private Loader loader;
    private Loader loaderWithTypeSubst;

    /**
     * Set only until the link phase to avoid leaking memory.
     */
    private RuntimeClassInfo ci;

    private final Accessor<? super BeanT,Map<QName,String>> inheritedAttWildcard;
    private final Transducer<BeanT> xducer;

    /**
     * {@link ClassBeanInfoImpl} that represents the super class of {@link #jaxbType}.
     */
    public final ClassBeanInfoImpl<? super BeanT> superClazz;

    private final Accessor<? super BeanT,Locator> xmlLocatorField;

    private final Name tagName;

    /**
     * The {@link AttributeProperty}s for this type and all its ancestors.
     * If {@link JAXBContextImpl#c14nSupport} is true, this is sorted alphabetically.
     */
    private /*final*/ AttributeProperty<BeanT>[] attributeProperties;

    /**
     * {@link Property}s that need to receive {@link Property#serializeURIs(Object, XMLSerializer)} callback.
     */
    private /*final*/ Property<BeanT>[] uriProperties;

    private final Method factoryMethod;

    /*package*/ ClassBeanInfoImpl(JAXBContextImpl owner, RuntimeClassInfo ci) {
        super(owner,ci,ci.getClazz(),ci.getTypeName(),ci.isElement(),false,true);

        this.ci = ci;
        this.inheritedAttWildcard = ci.getAttributeWildcard();
        this.xducer = ci.getTransducer();
        this.factoryMethod = ci.getFactoryMethod();
        // make the factory accessible
        if(factoryMethod!=null) {
            int classMod = factoryMethod.getDeclaringClass().getModifiers();

            if(!Modifier.isPublic(classMod) || !Modifier.isPublic(factoryMethod.getModifiers())) {
                // attempt to make it work even if the constructor is not accessible
                try {
                    factoryMethod.setAccessible(true);
                } catch(SecurityException e) {
                    // but if we don't have a permission to do so, work gracefully.
                    logger.log(Level.FINE,"Unable to make the method of "+factoryMethod+" accessible",e);
                    throw e;
                }
            }
        }


        if(ci.getBaseClass()==null)
            this.superClazz = null;
        else
            this.superClazz = owner.getOrCreate(ci.getBaseClass());

        if(superClazz!=null && superClazz.xmlLocatorField!=null)
            xmlLocatorField = superClazz.xmlLocatorField;
        else
            xmlLocatorField = ci.getLocatorField();

        // create property objects
        Collection<? extends RuntimePropertyInfo> ps = ci.getProperties();
        this.properties = new Property[ps.size()];
        int idx=0;
        boolean elementOnly = true;
        for( RuntimePropertyInfo info : ps ) {
            Property p = PropertyFactory.create(owner,info);
            if(info.id()==ID.ID)
                idProperty = p;
            properties[idx++] = p;
            elementOnly &= info.elementOnlyContent();
        }
        // super class' idProperty might not be computed at this point,
        // so check that later

        hasElementOnlyContentModel( elementOnly );
        // again update this value later when we know that of the super class

        if(ci.isElement())
            tagName = owner.nameBuilder.createElementName(ci.getElementName());
        else
            tagName = null;

        setLifecycleFlags();
    }

    @Override
    protected void link(JAXBContextImpl grammar) {
        if(uriProperties!=null)
            return; // avoid linking twice

        super.link(grammar);

        if(superClazz!=null)
            superClazz.link(grammar);

        getLoader(grammar,true);    // make sure to build the loader if we haven't done so.

        // propagate values from super class
        if(superClazz!=null) {
            if(idProperty==null)
                idProperty = superClazz.idProperty;

            if(!superClazz.hasElementOnlyContentModel())
                hasElementOnlyContentModel(false);
        }

        // create a list of attribute/URI handlers
        List<AttributeProperty> attProps = new FinalArrayList<AttributeProperty>();
        List<Property> uriProps = new FinalArrayList<Property>();
        for (ClassBeanInfoImpl bi = this; bi != null; bi = bi.superClazz) {
            for (int i = bi.properties.length - 1; i >= 0; i--) {
                Property p = bi.properties[i];
                if(p instanceof AttributeProperty)
                    attProps.add((AttributeProperty) p);
                if(p.hasSerializeURIAction())
                    uriProps.add(p);
            }
        }
        if(grammar.c14nSupport)
            Collections.sort(attProps);

        if(attProps.isEmpty())
            attributeProperties = EMPTY_PROPERTIES;
        else
            attributeProperties = attProps.toArray(new AttributeProperty[attProps.size()]);

        if(uriProps.isEmpty())
            uriProperties = EMPTY_PROPERTIES;
        else
            uriProperties = uriProps.toArray(new Property[uriProps.size()]);
    }

    public void wrapUp() {
        for (Property p : properties)
            p.wrapUp();
        ci = null;
        super.wrapUp();
    }

    public String getElementNamespaceURI(BeanT bean) {
        return tagName.nsUri;
    }

    public String getElementLocalName(BeanT bean) {
        return tagName.localName;
    }

    public BeanT createInstance(UnmarshallingContext context) throws IllegalAccessException, InvocationTargetException, InstantiationException, SAXException {

        BeanT bean = null;
        if (factoryMethod == null){
           bean = ClassFactory.create0(jaxbType);
        }else {
            Object o = ClassFactory.create(factoryMethod);
            if( jaxbType.isInstance(o) ){
                bean = (BeanT)o;
            } else {
                throw new InstantiationException("The factory method didn't return a correct object");
            }
        }

        if(xmlLocatorField!=null)
            // need to copy because Locator is mutable
            try {
                xmlLocatorField.set(bean,new LocatorImpl(context.getLocator()));
            } catch (AccessorException e) {
                context.handleError(e);
            }
        return bean;
    }

    public boolean reset(BeanT bean, UnmarshallingContext context) throws SAXException {
        try {
            if(superClazz!=null)
                superClazz.reset(bean,context);
            for( Property<BeanT> p : properties )
                p.reset(bean);
            return true;
        } catch (AccessorException e) {
            context.handleError(e);
            return false;
        }
    }

    public String getId(BeanT bean, XMLSerializer target) throws SAXException {
        if(idProperty!=null) {
            try {
                return idProperty.getIdValue(bean);
            } catch (AccessorException e) {
                target.reportError(null,e);
            }
        }
        return null;
    }

    public void serializeRoot(BeanT bean, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        if(tagName==null) {
            target.reportError(
                    new ValidationEventImpl(
                            ValidationEvent.ERROR,
                            Messages.UNABLE_TO_MARSHAL_NON_ELEMENT.format(bean.getClass().getName()),
                            null,
                            null));
        }
        else {
            target.startElement(tagName,bean);
            target.childAsSoleContent(bean,null);
            target.endElement();
        }
    }

    public void serializeBody(BeanT bean, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        if(superClazz!=null)
            superClazz.serializeBody(bean,target);
        try {
            for( Property<BeanT> p : properties )
                p.serializeBody(bean,target, null);
        } catch (AccessorException e) {
            target.reportError(null,e);
        }
    }

    public void serializeAttributes(BeanT bean, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        try {
            for( AttributeProperty<BeanT> p : attributeProperties )
                p.serializeAttributes(bean,target);

            if(inheritedAttWildcard!=null) {
                Map<QName,String> map = inheritedAttWildcard.get(bean);
                target.attWildcardAsAttributes(map,null);
            }
        } catch (AccessorException e) {
            target.reportError(null,e);
        }
    }

    public void serializeURIs(BeanT bean, XMLSerializer target) throws SAXException {
        try {
            for( Property<BeanT> p : uriProperties )
                p.serializeURIs(bean,target);

            if(inheritedAttWildcard!=null) {
                Map<QName,String> map = inheritedAttWildcard.get(bean);
                target.attWildcardAsURIs(map,null);
            }
        } catch (AccessorException e) {
            target.reportError(null,e);
        }
    }

    public Loader getLoader(JAXBContextImpl context, boolean typeSubstitutionCapable) {
        if(loader==null) {
            // these variables have to be set before they are initialized,
            // because the initialization may build other loaders and they may refer to this.
            StructureLoader sl = new StructureLoader(this);
            loader = sl;
            if(ci.hasSubClasses())
                loaderWithTypeSubst = new XsiTypeLoader(this);
            else
                // optimization. we know there can be no @xsi:type
                loaderWithTypeSubst = loader;


            sl.init(context,this,ci.getAttributeWildcard());
        }
        if(typeSubstitutionCapable)
            return loaderWithTypeSubst;
        else
            return loader;
    }

    public Transducer<BeanT> getTransducer() {
        return xducer;
    }

    private static final AttributeProperty[] EMPTY_PROPERTIES = new AttributeProperty[0];

    private static final Logger logger = Util.getClassLogger();
}
