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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeInfo;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallerImpl;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;

import org.xml.sax.SAXException;

/**
 * Encapsulates various JAXB operations on objects bound by JAXB.
 * Immutable and thread-safe.
 *
 * <p>
 * Each JAXB-bound class has a corresponding {@link JaxBeanInfo} object,
 * which performs all the JAXB related operations on behalf of
 * the JAXB-bound object.
 *
 * <p>
 * Given a class, the corresponding {@link JaxBeanInfo} can be located
 * via {@link JAXBContextImpl#getBeanInfo(Class,boolean)}.
 *
 * <p>
 * Typically, {@link JaxBeanInfo} implementations should be generated
 * by XJC/JXC. Those impl classes will register themselves to their
 * master <tt>ObjectFactory</tt> class.
 *
 * <p>
 * The type parameter BeanT is the Java class of the bean that this represents.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class JaxBeanInfo<BeanT> {

    /**
     * For {@link JaxBeanInfo} that has multiple type names.
     */
    protected JaxBeanInfo(JAXBContextImpl grammar, RuntimeTypeInfo rti, Class<BeanT> jaxbType, QName[] typeNames, boolean isElement,boolean isImmutable, boolean hasLifecycleEvents) {
        this(grammar,rti,jaxbType,(Object)typeNames,isElement,isImmutable,hasLifecycleEvents);
    }

    /**
     * For {@link JaxBeanInfo} that has one type name.
     */
    protected JaxBeanInfo(JAXBContextImpl grammar, RuntimeTypeInfo rti, Class<BeanT> jaxbType, QName typeName, boolean isElement,boolean isImmutable, boolean hasLifecycleEvents) {
        this(grammar,rti,jaxbType,(Object)typeName,isElement,isImmutable,hasLifecycleEvents);
    }

    /**
     * For {@link JaxBeanInfo} that has no type names.
     */
    protected JaxBeanInfo(JAXBContextImpl grammar, RuntimeTypeInfo rti, Class<BeanT> jaxbType, boolean isElement,boolean isImmutable, boolean hasLifecycleEvents) {
        this(grammar,rti,jaxbType,(Object)null,isElement,isImmutable,hasLifecycleEvents);
    }

    private JaxBeanInfo(JAXBContextImpl grammar, RuntimeTypeInfo rti, Class<BeanT> jaxbType, Object typeName, boolean isElement,boolean isImmutable, boolean hasLifecycleEvents) {
        grammar.beanInfos.put(rti,this);

        this.jaxbType = jaxbType;
        this.typeName = typeName;
        this.flag = (short)((isElement?FLAG_IS_ELEMENT:0)
                |(isImmutable?FLAG_IS_IMMUTABLE:0)
                |(hasLifecycleEvents?FLAG_HAS_LIFECYCLE_EVENTS:0));
    }

    /**
     * Various boolean flags combined into one field to improve memory footprint.
     */
    protected short flag;

    private static final short FLAG_IS_ELEMENT = 1;
    private static final short FLAG_IS_IMMUTABLE = 2;
    private static final short FLAG_HAS_ELEMENT_ONLY_CONTENTMODEL = 4;
    private static final short FLAG_HAS_BEFORE_UNMARSHAL_METHOD = 8;
    private static final short FLAG_HAS_AFTER_UNMARSHAL_METHOD = 16;
    private static final short FLAG_HAS_BEFORE_MARSHAL_METHOD = 32;
    private static final short FLAG_HAS_AFTER_MARSHAL_METHOD = 64;
    private static final short FLAG_HAS_LIFECYCLE_EVENTS = 128;

    /** cache of lifecycle methods */
    private LifecycleMethods lcm = null;

    /**
     * True if {@link #jaxbType} has the  lifecycle method.
     */
    public final boolean hasBeforeUnmarshalMethod() {
        return (flag&FLAG_HAS_BEFORE_UNMARSHAL_METHOD) != 0;
    }

    /**
     * True if {@link #jaxbType} has the  lifecycle method.
     */
    public final boolean hasAfterUnmarshalMethod() {
        return (flag&FLAG_HAS_AFTER_UNMARSHAL_METHOD) != 0;
    }

    /**
     * True if {@link #jaxbType} has the  lifecycle method.
     */
    public final boolean hasBeforeMarshalMethod() {
        return (flag&FLAG_HAS_BEFORE_MARSHAL_METHOD) != 0;
    }

    /**
     * True if {@link #jaxbType} has the  lifecycle method.
     */
    public final boolean hasAfterMarshalMethod() {
        return (flag&FLAG_HAS_AFTER_MARSHAL_METHOD) != 0;
    }

    /**
     * Gets the JAXB bound class type that this {@link JaxBeanInfo}
     * handles.
     *
     * <p>
     * IOW, when a bean info object is requested for T,
     * sometimes the bean info for one of its base classes might be
     * returned.
     */
    public final Class<BeanT> jaxbType;

    /**
     * Returns true if the bean is mapped to/from an XML element.
     *
     * <p>
     * When this method returns true, {@link #getElementNamespaceURI(Object)}
     * and {@link #getElementLocalName(Object)} returns the element name of
     * the bean.
     */
    public final boolean isElement() {
        return (flag&FLAG_IS_ELEMENT)!=0;
    }

    /**
     * Returns true if the bean is immutable.
     *
     * <p>
     * If this is true, Binder won't try to ueuse this object, and the unmarshaller
     * won't create a new instance of it before it starts.
     */
    public final boolean isImmutable() {
        return (flag&FLAG_IS_IMMUTABLE)!=0;
    }

    /**
     * True if this bean has an element-only content model.
     * <p>
     * If this flag is true, the unmarshaller can work
     * faster by ignoring whitespaces more efficiently.
     */
    public final boolean hasElementOnlyContentModel() {
        return (flag&FLAG_HAS_ELEMENT_ONLY_CONTENTMODEL)!=0;
    }

    /**
     * True if this bean has an element-only content model.
     * <p>
     * Should be considered immutable, though I can't mark it final
     * because it cannot be computed in this constructor.
     */
    protected final void hasElementOnlyContentModel(boolean value) {
        if(value)
            flag |= FLAG_HAS_ELEMENT_ONLY_CONTENTMODEL;
        else
            flag &= ~FLAG_HAS_ELEMENT_ONLY_CONTENTMODEL;
    }

    /**
     * This method is used to determine which of the sub-classes should be
     * interrogated for the existence of lifecycle methods.
     *
     * @return true if the un|marshaller should look for lifecycle methods
     *         on this beanInfo, false otherwise.
     */
    public boolean lookForLifecycleMethods() {
        return (flag&FLAG_HAS_LIFECYCLE_EVENTS)!=0;
    }

    /**
     * Returns the namespace URI portion of the element name,
     * if the bean that this class represents is mapped from/to
     * an XML element.
     *
     * @throws UnsupportedOperationException
     *      if {@link #isElement} is false.
     */
    public abstract String getElementNamespaceURI(BeanT o);

    /**
     * Returns the local name portion of the element name,
     * if the bean that this class represents is mapped from/to
     * an XML element.
     *
     * @throws UnsupportedOperationException
     *      if {@link #isElement} is false.
     */
    public abstract String getElementLocalName(BeanT o);

    /**
     * Type names associated with this {@link JaxBeanInfo}.
     *
     * @see #getTypeNames()
     */
    private final Object typeName; // either null, QName, or QName[]. save memory since most of them have just one.

    /**
     * Returns XML Schema type names if the bean is mapped from
     * a complex/simple type of XML Schema.
     *
     * <p>
     * This is an ugly necessity to correctly handle
     * the type substitution semantics of XML Schema.
     *
     * <p>
     * A single Java class maybe mapped to more than one
     * XML types. All the types listed here are recognized
     * when we are unmarshalling XML.
     *
     * <p>
     * null if the class is not bound to a named schema type.
     *
     * <p>
     */
    public Collection<QName> getTypeNames() {
        if(typeName==null)  return Collections.emptyList();
        if(typeName instanceof QName)   return Collections.singletonList((QName)typeName);
        return Arrays.asList((QName[])typeName);
    }

    /**
     * Returns the XML type name to be used to marshal the specified instance.
     *
     * <P>
     * Most of the times the type can be determined regardless of the actual
     * instance, but there's a few exceptions (most notably {@link XMLGregorianCalendar}),
     * so as a general rule we need an instance to determine it.
     */
    public QName getTypeName(@NotNull BeanT instance) {
        if(typeName==null)  return null;
        if(typeName instanceof QName)   return (QName)typeName;
        return ((QName[])typeName)[0];
    }

    /**
     * Creates a new instance of the bean.
     *
     * <p>
     * This operation is only supported when {@link #isImmutable} is false.
     *
     * @param context
     *      Sometimes the created bean remembers the corresponding source location,
     */
    public abstract BeanT createInstance(UnmarshallingContext context) throws IllegalAccessException, InvocationTargetException, InstantiationException, SAXException;

    /**
     * Resets the object to the initial state, as if the object
     * is created fresh.
     *
     * <p>
     * This is used to reuse an existing object for unmarshalling.
     *
     * @param context
     *      used for reporting any errors.
     *
     * @return
     *      true if the object was successfuly resetted.
     *      False if the object is not resettable, in which case the object will be
     *      discarded and new one will be created.
     *      <p>
     *      If the object is resettable but failed by an error, it should be reported to the context,
     *      then return false. If the object is not resettable to begin with, do not report an error.
     *
     * @throws SAXException
     *      as a result of reporting an error, the context may throw a {@link SAXException}.
     */
    public abstract boolean reset( BeanT o, UnmarshallingContext context ) throws SAXException;

    /**
     * Gets the ID value of the given bean, if it has an ID value.
     * Otherwise return null.
     */
    public abstract String getId(BeanT o, XMLSerializer target) throws SAXException;

    /**
     * Serializes child elements and texts into the specified target.
     */
    public abstract void serializeBody( BeanT o, XMLSerializer target ) throws SAXException, IOException, XMLStreamException;

    /**
     * Serializes attributes into the specified target.
     */
    public abstract void serializeAttributes( BeanT o, XMLSerializer target ) throws SAXException, IOException, XMLStreamException;

    /**
     * Serializes the bean as the root element.
     *
     * <p>
     * In the java-to-schema binding, an object might marshal in two different
     * ways depending on whether it is used as the root of the graph or not.
     * In the former case, an object could marshal as an element, whereas
     * in the latter case, it marshals as a type.
     *
     * <p>
     * This method is used to marshal the root of the object graph to allow
     * this semantics to be implemented.
     *
     * <p>
     * It is doubtful to me if it's a good idea for an object to marshal
     * in two ways depending on the context.
     *
     * <p>
     * For schema-to-java, this is equivalent to {@link #serializeBody(Object, XMLSerializer)}.
     */
    public abstract void serializeRoot( BeanT o, XMLSerializer target ) throws SAXException, IOException, XMLStreamException;

    /**
     * Declares all the namespace URIs this object is using at
     * its top-level scope into the specified target.
     */
    public abstract void serializeURIs( BeanT o, XMLSerializer target ) throws SAXException;

    /**
     * Gets the {@link Loader} that will unmarshall the given object.
     *
     * @param context
     *      The {@link JAXBContextImpl} object that governs this object.
     *      This object is taken as a parameter so that {@link JaxBeanInfo} doesn't have
     *      to store them on its own.
     *
     *      When this method is invoked from within the unmarshaller, tihs parameter can be
     *      null (because the loader is constructed already.)
     *
     * @param typeSubstitutionCapable
     *      If true, the returned {@link Loader} is capable of recognizing @xsi:type (if necessary)
     *      and unmarshals a subtype. This allowes an optimization where this bean info
     *      is guaranteed not to have a type substitution.
     *      If false, the returned {@link Loader} doesn't look for @xsi:type.
     * @return
     *      must return non-null valid object
     */
    public abstract Loader getLoader(JAXBContextImpl context, boolean typeSubstitutionCapable);

    /**
     * If the bean's representation in XML is just a text,
     * this method return a {@link Transducer} that lets you convert
     * values between the text and the bean.
     */
    public abstract Transducer<BeanT> getTransducer();


    /**
     * Called after all the {@link JaxBeanInfo}s are created.
     * @param grammar
     */
    protected  void link(JAXBContextImpl grammar) {
    }

    /**
     * Called at the end of the {@link JAXBContext} initialization phase
     * to clean up any unnecessary references.
     */
    public void wrapUp() {}


    private static final Class[] unmarshalEventParams = { Unmarshaller.class, Object.class };
    private static Class[] marshalEventParams = { Marshaller.class };

    /**
     * use reflection to determine which of the 4 object lifecycle methods exist on
     * the JAXB bound type.
     */
    protected final void setLifecycleFlags() {
        try {
            Class<BeanT> jt = jaxbType;

            if (lcm == null) {
                lcm = new LifecycleMethods();
            }

            while (jt != null) {
                for (Method m : jt.getDeclaredMethods()) {
                    String name = m.getName();

                    if (lcm.beforeUnmarshal == null) {
                        if (name.equals("beforeUnmarshal")) {
                            if (match(m, unmarshalEventParams)) {
                                cacheLifecycleMethod(m, FLAG_HAS_BEFORE_UNMARSHAL_METHOD);
                            }
                        }
                    }

                    if (lcm.afterUnmarshal == null) {
                        if (name.equals("afterUnmarshal")) {
                            if (match(m, unmarshalEventParams)) {
                                cacheLifecycleMethod(m, FLAG_HAS_AFTER_UNMARSHAL_METHOD);
                            }
                        }
                    }

                    if (lcm.beforeMarshal == null) {
                        if (name.equals("beforeMarshal")) {
                            if (match(m, marshalEventParams)) {
                                cacheLifecycleMethod(m, FLAG_HAS_BEFORE_MARSHAL_METHOD);
                            }
                        }
                    }

                    if (lcm.afterMarshal == null) {
                        if (name.equals("afterMarshal")) {
                            if (match(m, marshalEventParams)) {
                                cacheLifecycleMethod(m, FLAG_HAS_AFTER_MARSHAL_METHOD);
                            }
                        }
                    }
                }
                jt = (Class<BeanT>) jt.getSuperclass();
            }
        } catch (SecurityException e) {
            // this happens when we don't have enough permission.
            logger.log(Level.WARNING, Messages.UNABLE_TO_DISCOVER_EVENTHANDLER.format(
                    jaxbType.getName(), e));
        }
    }

    private boolean match(Method m, Class[] params) {
        return Arrays.equals(m.getParameterTypes(),params);
    }

    /**
     * Cache a reference to the specified lifecycle method for the jaxbType
     * associated with this beanInfo.
     *
     * @param m Method reference
     * @param lifecycleFlag byte representing which of the 4 lifecycle methods
     *        is being cached
     */
    private void cacheLifecycleMethod(Method m, short lifecycleFlag) {
        //LifecycleMethods lcm = getLifecycleMethods();
        if(lcm==null) {
            lcm = new LifecycleMethods();
            //lcmCache.put(jaxbType, lcm);
        }

        m.setAccessible(true);

        flag |= lifecycleFlag;

        switch (lifecycleFlag) {
        case FLAG_HAS_BEFORE_UNMARSHAL_METHOD:
            lcm.beforeUnmarshal = m;
            break;
        case FLAG_HAS_AFTER_UNMARSHAL_METHOD:
            lcm.afterUnmarshal = m;
            break;
        case FLAG_HAS_BEFORE_MARSHAL_METHOD:
            lcm.beforeMarshal = m;
            break;
        case FLAG_HAS_AFTER_MARSHAL_METHOD:
            lcm.afterMarshal = m;
            break;
        }
    }

    /**
     * Return the LifecycleMethods cache for this ClassBeanInfo's corresponding
     * jaxbType if it exists, else return null.
     *
     */
    public final LifecycleMethods getLifecycleMethods() {
        return lcm;
    }

    /**
     * Invokes the beforeUnmarshal method if applicable.
     */
    public final void invokeBeforeUnmarshalMethod(UnmarshallerImpl unm, Object child, Object parent) throws SAXException {
        Method m = getLifecycleMethods().beforeUnmarshal;
        invokeUnmarshallCallback(m, child, unm, parent);
    }

    /**
     * Invokes the afterUnmarshal method if applicable.
     */
    public final void invokeAfterUnmarshalMethod(UnmarshallerImpl unm, Object child, Object parent) throws SAXException {
        Method m = getLifecycleMethods().afterUnmarshal;
        invokeUnmarshallCallback(m, child, unm, parent);
    }

    private void invokeUnmarshallCallback(Method m, Object child, UnmarshallerImpl unm, Object parent) throws SAXException {
        try {
            m.invoke(child,unm,parent);
        } catch (IllegalAccessException e) {
            UnmarshallingContext.getInstance().handleError(e, false);
        } catch (InvocationTargetException e) {
            UnmarshallingContext.getInstance().handleError(e, false);
        }
    }

    private static final Logger logger = Util.getClassLogger();
}
