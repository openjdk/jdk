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
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.bind.Binder;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Validator;
import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.Pool;
import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.bind.api.CompositeStructure;
import com.sun.xml.internal.bind.api.ErrorListener;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.RawAccessor;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.bind.util.Which;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.model.annotation.RuntimeAnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.RuntimeInlineAnnotationReader;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.Ref;
import com.sun.xml.internal.bind.v2.model.impl.RuntimeBuiltinLeafInfoImpl;
import com.sun.xml.internal.bind.v2.model.impl.RuntimeModelBuilder;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.model.nav.ReflectionNavigator;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeArrayInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeBuiltinLeafInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeClassInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeEnumLeafInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeLeafInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeInfoSet;
import com.sun.xml.internal.bind.v2.runtime.output.Encoded;
import com.sun.xml.internal.bind.v2.runtime.property.AttributeProperty;
import com.sun.xml.internal.bind.v2.runtime.property.Property;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TagName;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallerImpl;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.schemagen.XmlSchemaGenerator;
import com.sun.xml.internal.bind.v2.util.EditDistance;
import com.sun.xml.internal.bind.v2.util.QNameMap;
import com.sun.xml.internal.txw2.output.ResultFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class provides the implementation of JAXBContext.
 *
 * @version $Revision$
 */
public final class JAXBContextImpl extends JAXBRIContext {

    /**
     * All the bridge classes.
     */
    private final Map<TypeReference,Bridge> bridges = new LinkedHashMap<TypeReference,Bridge>();

    /**
     * Shared instance of {@link TransformerFactory}.
     * Lock before use, because a {@link TransformerFactory} is not thread-safe
     * whereas {@link JAXBContextImpl} is.
     * Lazily created.
     */
    private static SAXTransformerFactory tf;

    /**
     * Shared instance of {@link DocumentBuilder}.
     * Lock before use. Lazily created.
     */
    private static DocumentBuilder db;

    private final QNameMap<JaxBeanInfo> rootMap = new QNameMap<JaxBeanInfo>();
    private final HashMap<QName,JaxBeanInfo> typeMap = new HashMap<QName,JaxBeanInfo>();

    /**
     * Map from JAXB-bound {@link Class} to its {@link JaxBeanInfo}.
     */
    private final Map<Class,JaxBeanInfo> beanInfoMap = new LinkedHashMap<Class,JaxBeanInfo>();

    /**
     * All created {@link JaxBeanInfo}s.
     * Updated from each {@link JaxBeanInfo}s constructors to avoid infinite recursion
     * for a cyclic reference.
     *
     * <p>
     * This map is only used while the {@link JAXBContextImpl} is built and set to null
     * to avoid keeping references too long.
     */
    protected Map<RuntimeTypeInfo,JaxBeanInfo> beanInfos = new LinkedHashMap<RuntimeTypeInfo, JaxBeanInfo>();

    private final Map<Class/*scope*/,Map<QName,ElementBeanInfoImpl>> elements = new LinkedHashMap<Class, Map<QName, ElementBeanInfoImpl>>();

    /**
     * Pool of {@link Marshaller}s.
     */
    public final Pool<Marshaller> marshallerPool = new Pool.Impl<Marshaller>() {
        protected @NotNull Marshaller create() {
            return createMarshaller();
        }
    };

    public final Pool<Unmarshaller> unmarshallerPool = new Pool.Impl<Unmarshaller>() {
        protected @NotNull Unmarshaller create() {
            return createUnmarshaller();
        }
    };

    /**
     * Used to assign indices to known names in this grammar.
     * Reset to null once the build phase is completed.
     */
    public NameBuilder nameBuilder = new NameBuilder();

    /**
     * Keeps the list of known names.
     * This field is set once the build pahse is completed.
     */
    public final NameList nameList;

    /**
     * Input to the JAXBContext.newInstance, so that we can recreate
     * {@link RuntimeTypeInfoSet} whenever we need.
     */
    private final String defaultNsUri;
    private final Class[] classes;

    /**
     * true to reorder attributes lexicographically in preparation of the c14n support.
     */
    protected final boolean c14nSupport;

    /**
     * Flag that user has provided a custom AccessorFactory for JAXB to use
     */
    public final boolean xmlAccessorFactorySupport;

    /**
     * @see JAXBRIContext#TREAT_EVERYTHING_NILLABLE
     */
    public final boolean allNillable;


    private WeakReference<RuntimeTypeInfoSet> typeInfoSetCache;

    private @NotNull RuntimeAnnotationReader annotaitonReader;

    private /*almost final*/ boolean hasSwaRef;
    private final @NotNull Map<Class,Class> subclassReplacements;

    /**
     * If true, we aim for faster {@link JAXBContext} instanciation performance,
     * instead of going after efficient sustained unmarshalling/marshalling performance.
     *
     * @since 2.0.4
     */
    public final boolean fastBoot;

    /**
     *
     * @param typeRefs
     *      used to build {@link Bridge}s. Can be empty.
     * @param c14nSupport
     *      {@link #c14nSupport}.
     * @param xmlAccessorFactorySupport
     *      Use custom com.sun.xml.internal.bind.v2.runtime.reflect.Accessor implementation.
     */
    public JAXBContextImpl(Class[] classes, Collection<TypeReference> typeRefs,
        Map<Class,Class> subclassReplacements, String defaultNsUri, boolean c14nSupport,
        @Nullable RuntimeAnnotationReader ar, boolean xmlAccessorFactorySupport, boolean allNillable) throws JAXBException {
        // initialize datatype converter with ours
        DatatypeConverter.setDatatypeConverter(DatatypeConverterImpl.theInstance);

        if(defaultNsUri==null)      defaultNsUri="";    // fool-proof

        if(ar==null)
            ar = new RuntimeInlineAnnotationReader();

        if(subclassReplacements==null)  subclassReplacements=Collections.emptyMap();
        if(typeRefs==null)              typeRefs=Collections.emptyList();

        this.annotaitonReader = ar;
        this.subclassReplacements = subclassReplacements;

        boolean fastBoot;
        try {
            fastBoot = Boolean.getBoolean(JAXBContextImpl.class.getName()+".fastBoot");
        } catch (SecurityException e) {
            fastBoot = false;
        }
        this.fastBoot = fastBoot;

        this.defaultNsUri = defaultNsUri;
        this.c14nSupport = c14nSupport;
        this.xmlAccessorFactorySupport = xmlAccessorFactorySupport;
        this.allNillable = allNillable;
        this.classes = new Class[classes.length];
        System.arraycopy(classes,0,this.classes,0,classes.length);

        RuntimeTypeInfoSet typeSet = getTypeInfoSet();


        // at least prepare the empty table so that we don't have to check for null later
        elements.put(null,new LinkedHashMap<QName, ElementBeanInfoImpl>());

        // recognize leaf bean infos
        for( RuntimeBuiltinLeafInfo leaf : RuntimeBuiltinLeafInfoImpl.builtinBeanInfos ) {
            LeafBeanInfoImpl<?> bi = new LeafBeanInfoImpl(this,leaf);
            beanInfoMap.put(leaf.getClazz(),bi);
            for( QName t : bi.getTypeNames() )
                typeMap.put(t,bi);
        }

        for (RuntimeEnumLeafInfo e : typeSet.enums().values()) {
            JaxBeanInfo<?> bi = getOrCreate(e);
            for (QName qn : bi.getTypeNames())
                typeMap.put( qn, bi );
            if(e.isElement())
                rootMap.put( e.getElementName(), bi );
        }

        for (RuntimeArrayInfo a : typeSet.arrays().values()) {
            JaxBeanInfo<?> ai = getOrCreate(a);
            for (QName qn : ai.getTypeNames())
                typeMap.put( qn, ai );
        }

        for( RuntimeClassInfo ci : typeSet.beans().values() ) {
            ClassBeanInfoImpl<?> bi = getOrCreate(ci);

            if(bi.isElement())
                rootMap.put( ci.getElementName(), bi );

            for (QName qn : bi.getTypeNames())
                typeMap.put( qn, bi );
        }

        // fill in element mappings
        for( RuntimeElementInfo n : typeSet.getAllElements() ) {
            ElementBeanInfoImpl bi = getOrCreate(n);
            if(n.getScope()==null)
                rootMap.put(n.getElementName(),bi);

            RuntimeClassInfo scope = n.getScope();
            Class scopeClazz = scope==null?null:scope.getClazz();
            Map<QName,ElementBeanInfoImpl> m = elements.get(scopeClazz);
            if(m==null) {
                m = new LinkedHashMap<QName, ElementBeanInfoImpl>();
                elements.put(scopeClazz,m);
            }
            m.put(n.getElementName(),bi);
        }

        // this one is so that we can handle plain JAXBElements.
        beanInfoMap.put(JAXBElement.class,new ElementBeanInfoImpl(this));
        // another special BeanInfoImpl just for marshalling
        beanInfoMap.put(CompositeStructure.class,new CompositeStructureBeanInfo(this));

        getOrCreate(typeSet.getAnyTypeInfo());

        // then link them all!
        for (JaxBeanInfo bi : beanInfos.values())
            bi.link(this);

        // register primitives for boxed types just to make GrammarInfo fool-proof
        for( Map.Entry<Class,Class> e : RuntimeUtil.primitiveToBox.entrySet() )
            beanInfoMap.put( e.getKey(), beanInfoMap.get(e.getValue()) );

        // build bridges
        ReflectionNavigator nav = typeSet.getNavigator();

        for (TypeReference tr : typeRefs) {
            XmlJavaTypeAdapter xjta = tr.get(XmlJavaTypeAdapter.class);
            Adapter<Type,Class> a=null;
            XmlList xl = tr.get(XmlList.class);

            // eventually compute the in-memory type
            Class erasedType = nav.erasure(tr.type);

            if(xjta!=null) {
                a = new Adapter<Type,Class>(xjta.value(),nav);
            }
            if(tr.get(XmlAttachmentRef.class)!=null) {
                a = new Adapter<Type,Class>(SwaRefAdapter.class,nav);
                hasSwaRef = true;
            }

            if(a!=null) {
                erasedType = nav.erasure(a.defaultType);
            }

            Name name = nameBuilder.createElementName(tr.tagName);

            InternalBridge bridge;
            if(xl==null)
                bridge = new BridgeImpl(this, name,getBeanInfo(erasedType,true),tr);
            else
                bridge = new BridgeImpl(this, name,new ValueListBeanInfoImpl(this,erasedType),tr);

            if(a!=null)
                bridge = new BridgeAdapter(bridge,a.adapterType);

            bridges.put(tr,bridge);
        }


        this.nameList = nameBuilder.conclude();

        for (JaxBeanInfo bi : beanInfos.values())
            bi.wrapUp();

        // no use for them now
        nameBuilder = null;
        beanInfos = null;
    }

    /**
     * True if this JAXBContext has {@link XmlAttachmentRef}.
     */
    public boolean hasSwaRef() {
        return hasSwaRef;
    }

    /**
     * Creates a {@link RuntimeTypeInfoSet}.
     */
    private RuntimeTypeInfoSet getTypeInfoSet() throws IllegalAnnotationsException {

        // check cache
        if(typeInfoSetCache!=null) {
            RuntimeTypeInfoSet r = typeInfoSetCache.get();
            if(r!=null)
                return r;
        }

        final RuntimeModelBuilder builder = new RuntimeModelBuilder(this,annotaitonReader,subclassReplacements,defaultNsUri);

        IllegalAnnotationsException.Builder errorHandler = new IllegalAnnotationsException.Builder();
        builder.setErrorHandler(errorHandler);

        for( Class c : classes ) {
            if(c==CompositeStructure.class)
                // CompositeStructure doesn't have TypeInfo, so skip it.
                // We'll add JaxBeanInfo for this later automatically
                continue;
            builder.getTypeInfo(new Ref<Type,Class>(c));
        }

        this.hasSwaRef |= builder.hasSwaRef;
        RuntimeTypeInfoSet r = builder.link();

        errorHandler.check();
        assert r!=null : "if no error was reported, the link must be a success";

        typeInfoSetCache = new WeakReference<RuntimeTypeInfoSet>(r);

        return r;
    }


    public ElementBeanInfoImpl getElement(Class scope, QName name) {
        Map<QName,ElementBeanInfoImpl> m = elements.get(scope);
        if(m!=null) {
            ElementBeanInfoImpl bi = m.get(name);
            if(bi!=null)
                return bi;
        }
        m = elements.get(null);
        return m.get(name);
    }





    private ElementBeanInfoImpl getOrCreate( RuntimeElementInfo rei ) {
        JaxBeanInfo bi = beanInfos.get(rei);
        if(bi!=null)    return (ElementBeanInfoImpl)bi;

        // all elements share the same type, so we can't register them to beanInfoMap
        return new ElementBeanInfoImpl(this, rei);
    }

    protected JaxBeanInfo getOrCreate( RuntimeEnumLeafInfo eli ) {
        JaxBeanInfo bi = beanInfos.get(eli);
        if(bi!=null)    return bi;
        bi = new LeafBeanInfoImpl(this,eli);
        beanInfoMap.put(bi.jaxbType,bi);
        return bi;
    }

    protected ClassBeanInfoImpl getOrCreate( RuntimeClassInfo ci ) {
        ClassBeanInfoImpl bi = (ClassBeanInfoImpl)beanInfos.get(ci);
        if(bi!=null)    return bi;
        bi = new ClassBeanInfoImpl(this,ci);
        beanInfoMap.put(bi.jaxbType,bi);
        return bi;
    }

    protected JaxBeanInfo getOrCreate( RuntimeArrayInfo ai ) {
        JaxBeanInfo abi = beanInfos.get(ai.getType());
        if(abi!=null)   return abi;

        abi = new ArrayBeanInfoImpl(this,ai);

        beanInfoMap.put(ai.getType(),abi);
        return abi;
    }

    public JaxBeanInfo getOrCreate(RuntimeTypeInfo e) {
        if(e instanceof RuntimeElementInfo)
            return getOrCreate((RuntimeElementInfo)e);
        if(e instanceof RuntimeClassInfo)
            return getOrCreate((RuntimeClassInfo)e);
        if(e instanceof RuntimeLeafInfo) {
            JaxBeanInfo bi = beanInfos.get(e); // must have been created
            assert bi!=null;
            return bi;
        }
        if(e instanceof RuntimeArrayInfo)
            return getOrCreate((RuntimeArrayInfo)e);
        if(e.getType()==Object.class) {
            // anyType
            JaxBeanInfo bi = beanInfoMap.get(Object.class);
            if(bi==null) {
                bi = new AnyTypeBeanInfo(this,e);
                beanInfoMap.put(Object.class,bi);
            }
            return bi;
        }

        throw new IllegalArgumentException();
    }

    /**
     * Gets the {@link JaxBeanInfo} object that can handle
     * the given JAXB-bound object.
     *
     * <p>
     * This method traverses the base classes of the given object.
     *
     * @return null
     *      if <tt>c</tt> isn't a JAXB-bound class and <tt>fatal==false</tt>.
     */
    public final JaxBeanInfo getBeanInfo(Object o) {
        // don't allow xs:anyType beanInfo to handle all the unbound objects
        for( Class c=o.getClass(); c!=Object.class; c=c.getSuperclass()) {
            JaxBeanInfo bi = beanInfoMap.get(c);
            if(bi!=null)    return bi;
        }
        if(o instanceof Element)
            return beanInfoMap.get(Object.class);   // return the BeanInfo for xs:anyType
        return null;
    }

    /**
     * Gets the {@link JaxBeanInfo} object that can handle
     * the given JAXB-bound object.
     *
     * @param fatal
     *      if true, the failure to look up will throw an exception.
     *      Otherwise it will just return null.
     */
    public final JaxBeanInfo getBeanInfo(Object o,boolean fatal) throws JAXBException {
        JaxBeanInfo bi = getBeanInfo(o);
        if(bi!=null)    return bi;
        if(fatal) {
            if(o instanceof Document)
                throw new JAXBException(Messages.ELEMENT_NEEDED_BUT_FOUND_DOCUMENT.format(o.getClass()));
            throw new JAXBException(Messages.UNKNOWN_CLASS.format(o.getClass()));
        }
        return null;
    }

    /**
     * Gets the {@link JaxBeanInfo} object that can handle
     * the given JAXB-bound class.
     *
     * <p>
     * This method doesn't look for base classes.
     *
     * @return null
     *      if <tt>c</tt> isn't a JAXB-bound class and <tt>fatal==false</tt>.
     */
    public final <T> JaxBeanInfo<T> getBeanInfo(Class<T> clazz) {
        return (JaxBeanInfo<T>)beanInfoMap.get(clazz);
    }

    /**
     * Gets the {@link JaxBeanInfo} object that can handle
     * the given JAXB-bound class.
     *
     * @param fatal
     *      if true, the failure to look up will throw an exception.
     *      Otherwise it will just return null.
     */
    public final <T> JaxBeanInfo<T> getBeanInfo(Class<T> clazz,boolean fatal) throws JAXBException {
        JaxBeanInfo<T> bi = getBeanInfo(clazz);
        if(bi!=null)    return bi;
        if(fatal)
            throw new JAXBException(clazz.getName()+" is not known to this context");
        return null;
    }

    /**
     * Based on the tag name, determine what object to unmarshal,
     * and then set a new object and its loader to the current unmarshaller state.
     *
     * @return
     *      null if the given name pair is not recognized.
     */
    public final Loader selectRootLoader( UnmarshallingContext.State state, TagName tag ) {
        JaxBeanInfo beanInfo = rootMap.get(tag.uri,tag.local);
        if(beanInfo==null)
            return null;

        return beanInfo.getLoader(this,true);
    }

    /**
     * Gets the {@link JaxBeanInfo} for the given named XML Schema type.
     *
     * @return
     *      null if the type name is not recognized. For schema
     *      languages other than XML Schema, this method always
     *      returns null.
     */
    public JaxBeanInfo getGlobalType(QName name) {
        return typeMap.get(name);
    }

    /**
     * Finds a type name that this context recognizes which is
     * "closest" to the given type name.
     *
     * <p>
     * This method is used for error recovery.
     */
    public String getNearestTypeName(QName name) {
        String[] all = new String[typeMap.size()];
        int i=0;
        for (QName qn : typeMap.keySet()) {
            if(qn.getLocalPart().equals(name.getLocalPart()))
                return qn.toString();  // probably a match, as people often gets confused about namespace.
            all[i++] = qn.toString();
        }

        String nearest = EditDistance.findNearest(name.toString(), all);

        if(EditDistance.editDistance(nearest,name.toString())>10)
            return null;    // too far apart.

        return nearest;
    }

    /**
     * Returns the set of valid root tag names.
     * For diagnostic use.
     */
    public Set<QName> getValidRootNames() {
        Set<QName> r = new TreeSet<QName>(QNAME_COMPARATOR);
        for (QNameMap.Entry e : rootMap.entrySet()) {
            r.add(e.createQName());
        }
        return r;
    }

    /**
     * Cache of UTF-8 encoded local names to improve the performance for the marshalling.
     */
    private Encoded[] utf8nameTable;

    public synchronized Encoded[] getUTF8NameTable() {
        if(utf8nameTable==null) {
            Encoded[] x = new Encoded[nameList.localNames.length];
            for( int i=0; i<x.length; i++ ) {
                Encoded e = new Encoded(nameList.localNames[i]);
                e.compact();
                x[i] = e;
            }
            utf8nameTable = x;
        }
        return utf8nameTable;
    }

    public int getNumberOfLocalNames() {
        return nameList.localNames.length;
    }

    public int getNumberOfElementNames() {
        return nameList.numberOfElementNames;
    }

    public int getNumberOfAttributeNames() {
        return nameList.numberOfAttributeNames;
    }

    /**
     * Creates a new identity transformer.
     */
    static Transformer createTransformer() {
        try {
            synchronized(JAXBContextImpl.class) {
                if(tf==null)
                    tf = (SAXTransformerFactory)TransformerFactory.newInstance();
                return tf.newTransformer();
            }
        } catch (TransformerConfigurationException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Creates a new identity transformer.
     */
    public static TransformerHandler createTransformerHandler() {
        try {
            synchronized(JAXBContextImpl.class) {
                if(tf==null)
                    tf = (SAXTransformerFactory)TransformerFactory.newInstance();
                return tf.newTransformerHandler();
            }
        } catch (TransformerConfigurationException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Creates a new DOM document.
     */
    static Document createDom() {
        synchronized(JAXBContextImpl.class) {
            if(db==null) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    db = dbf.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    // impossible
                    throw new FactoryConfigurationError(e);
                }
            }
            return db.newDocument();
        }
    }

    public MarshallerImpl createMarshaller() {
        return new MarshallerImpl(this,null);
    }

    public UnmarshallerImpl createUnmarshaller() {
        return new UnmarshallerImpl(this,null);
    }

    public Validator createValidator() {
        throw new UnsupportedOperationException(Messages.NOT_IMPLEMENTED_IN_2_0.format());
    }

    @Override
    public JAXBIntrospector createJAXBIntrospector() {
        return new JAXBIntrospector() {
            public boolean isElement(Object object) {
                return getElementName(object)!=null;
            }

            public QName getElementName(Object jaxbElement) {
                try {
                    return JAXBContextImpl.this.getElementName(jaxbElement);
                } catch (JAXBException e) {
                    return null;
                }
            }
        };
    }

    private NonElement<Type,Class> getXmlType(RuntimeTypeInfoSet tis, TypeReference tr) {
        if(tr==null)
            throw new IllegalArgumentException();

        XmlJavaTypeAdapter xjta = tr.get(XmlJavaTypeAdapter.class);
        XmlList xl = tr.get(XmlList.class);

        Ref<Type,Class> ref = new Ref<Type,Class>(annotaitonReader, tis.getNavigator(), tr.type, xjta, xl );

        return tis.getTypeInfo(ref);
    }

    @Override
    public void generateEpisode(Result output) {
        if(output==null)
            throw new IllegalArgumentException();
        createSchemaGenerator().writeEpisodeFile(ResultFactory.createSerializer(output));
    }

    @Override
    public void generateSchema(SchemaOutputResolver outputResolver) throws IOException {
        if(outputResolver==null)
            throw new IOException(Messages.NULL_OUTPUT_RESOLVER.format());

        final SAXParseException[] e = new SAXParseException[1];

        createSchemaGenerator().write(outputResolver, new ErrorListener() {
            public void error(SAXParseException exception) {
                e[0] = exception;
            }

            public void fatalError(SAXParseException exception) {
                e[0] = exception;
            }

            public void warning(SAXParseException exception) {}
            public void info(SAXParseException exception) {}
        });

        if(e[0]!=null) {
            IOException x = new IOException(Messages.FAILED_TO_GENERATE_SCHEMA.format());
            x.initCause(e[0]);
            throw x;
        }
    }

    private XmlSchemaGenerator<Type,Class,Field,Method> createSchemaGenerator() {
        RuntimeTypeInfoSet tis;
        try {
            tis = getTypeInfoSet();
        } catch (IllegalAnnotationsException e) {
            // this shouldn't happen because we've already
            throw new AssertionError(e);
        }

        XmlSchemaGenerator<Type,Class,Field,Method> xsdgen =
                new XmlSchemaGenerator<Type,Class,Field,Method>(tis.getNavigator(),tis);

        // JAX-RPC uses Bridge objects that collide with
        // @XmlRootElement.
        // we will avoid collision here
        Set<QName> rootTagNames = new HashSet<QName>();
        for (RuntimeElementInfo ei : tis.getAllElements()) {
            rootTagNames.add(ei.getElementName());
        }
        for (RuntimeClassInfo ci : tis.beans().values()) {
            if(ci.isElement())
                rootTagNames.add(ci.asElement().getElementName());
        }

        for (TypeReference tr : bridges.keySet()) {
            if(rootTagNames.contains(tr.tagName))
                continue;

            if(tr.type==void.class || tr.type==Void.class) {
                xsdgen.add(tr.tagName,false,null);
            } else
            if(tr.type==CompositeStructure.class) {
                // this is a special class we introduced for JAX-WS that we *don't* want in the schema
            } else {
                NonElement<Type,Class> typeInfo = getXmlType(tis,tr);
                xsdgen.add(tr.tagName, !Navigator.REFLECTION.isPrimitive(tr.type),typeInfo);
            }
        }
        return xsdgen;
    }

    public QName getTypeName(TypeReference tr) {
        try {
            NonElement<Type,Class> xt = getXmlType(getTypeInfoSet(),tr);
            if(xt==null)    throw new IllegalArgumentException();
            return xt.getTypeName();
        } catch (IllegalAnnotationsException e) {
            // impossible given that JAXBRIContext has been successfully built in the first place
            throw new AssertionError(e);
        }
    }

    /**
     * Used for testing.
     */
    public SchemaOutputResolver createTestResolver() {
        return new SchemaOutputResolver() {
            public Result createOutput(String namespaceUri, String suggestedFileName) {
                SAXResult r = new SAXResult(new DefaultHandler());
                r.setSystemId(suggestedFileName);
                return r;
            }
        };
    }

    @Override
    public <T> Binder<T> createBinder(Class<T> domType) {
        if(domType==Node.class)
            return (Binder<T>)createBinder();
        else
            return super.createBinder(domType);
    }

    @Override
    public Binder<Node> createBinder() {
        return new BinderImpl<Node>(this,new DOMScanner());
    }

    public QName getElementName(Object o) throws JAXBException {
        JaxBeanInfo bi = getBeanInfo(o,true);
        if(!bi.isElement())
            return null;
        return new QName(bi.getElementNamespaceURI(o),bi.getElementLocalName(o));
    }

    public Bridge createBridge(TypeReference ref) {
        return bridges.get(ref);
    }

    public @NotNull BridgeContext createBridgeContext() {
        return new BridgeContextImpl(this);
    }

    public RawAccessor getElementPropertyAccessor(Class wrapperBean, String nsUri, String localName) throws JAXBException {
        JaxBeanInfo bi = getBeanInfo(wrapperBean,true);
        if(!(bi instanceof ClassBeanInfoImpl))
            throw new JAXBException(wrapperBean+" is not a bean");

        for( ClassBeanInfoImpl cb = (ClassBeanInfoImpl) bi; cb!=null; cb=cb.superClazz) {
            for (Property p : cb.properties) {
                final Accessor acc = p.getElementPropertyAccessor(nsUri,localName);
                if(acc!=null)
                    return new RawAccessor() {
                        // Accessor.set/get are designed for unmarshaller/marshaller, and hence
                        // they go through an adapter behind the scene.
                        // this isn't desirable for JAX-WS, which essentially uses this method
                        // just as a reflection library. So use the "unadapted" version to
                        // achieve the desired semantics
                        public Object get(Object bean) throws AccessorException {
                            return acc.getUnadapted(bean);
                        }

                        public void set(Object bean, Object value) throws AccessorException {
                            acc.setUnadapted(bean,value);
                        }
                    };
            }
        }
        throw new JAXBException(new QName(nsUri,localName)+" is not a valid property on "+wrapperBean);
    }

    public List<String> getKnownNamespaceURIs() {
        return Arrays.asList(nameList.namespaceURIs);
    }

    public String getBuildId() {
        Package pkg = getClass().getPackage();
        if(pkg==null)   return null;
        return pkg.getImplementationVersion();
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(Which.which(getClass()) + " Build-Id: " + getBuildId());
        buf.append("\nClasses known to this context:\n");

        Set<String> names = new TreeSet<String>();  // sort them so that it's easy to read

        for (Class key : beanInfoMap.keySet())
            names.add(key.getName());

        for(String name: names)
            buf.append("  ").append(name).append('\n');

        return buf.toString();
    }

    /**
     * Gets the value of the xmime:contentType attribute on the given object, or null
     * if for some reason it couldn't be found, including any error.
     */
    public String getXMIMEContentType( Object o ) {
        JaxBeanInfo bi = getBeanInfo(o);
        if(!(bi instanceof ClassBeanInfoImpl))
            return null;

        ClassBeanInfoImpl cb = (ClassBeanInfoImpl) bi;
        for (Property p : cb.properties) {
            if (p instanceof AttributeProperty) {
                AttributeProperty ap = (AttributeProperty) p;
                if(ap.attName.equals(WellKnownNamespace.XML_MIME_URI,"contentType"))
                    try {
                        return (String)ap.xacc.print(o);
                    } catch (AccessorException e) {
                        return null;
                    } catch (SAXException e) {
                        return null;
                    } catch (ClassCastException e) {
                        return null;
                    }
            }
        }
        return null;
    }

    /**
     * Creates a {@link JAXBContextImpl} that includes the specified additional classes.
     */
    public JAXBContextImpl createAugmented(Class<?> clazz) throws JAXBException {
        Class[] newList = new Class[classes.length+1];
        System.arraycopy(classes,0,newList,0,classes.length);
        newList[classes.length] = clazz;

        return new JAXBContextImpl(newList,bridges.keySet(),subclassReplacements,
        defaultNsUri,c14nSupport,annotaitonReader, xmlAccessorFactorySupport, allNillable);
    }

    private static final Comparator<QName> QNAME_COMPARATOR = new Comparator<QName>() {
        public int compare(QName lhs, QName rhs) {
            int r = lhs.getLocalPart().compareTo(rhs.getLocalPart());
            if(r!=0)    return r;

            return lhs.getNamespaceURI().compareTo(rhs.getNamespaceURI());
        }
    };
}
