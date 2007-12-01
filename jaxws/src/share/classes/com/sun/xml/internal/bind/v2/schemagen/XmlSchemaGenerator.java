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

package com.sun.xml.internal.bind.v2.schemagen;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.MimeType;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.api.CompositeStructure;
import com.sun.xml.internal.bind.v2.TODO;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import static com.sun.xml.internal.bind.v2.WellKnownNamespace.XML_SCHEMA;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.core.ArrayInfo;
import com.sun.xml.internal.bind.v2.model.core.AttributePropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.model.core.Element;
import com.sun.xml.internal.bind.v2.model.core.ElementInfo;
import com.sun.xml.internal.bind.v2.model.core.ElementPropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.EnumConstant;
import com.sun.xml.internal.bind.v2.model.core.EnumLeafInfo;
import com.sun.xml.internal.bind.v2.model.core.MapPropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.NonElementRef;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.ReferencePropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;
import com.sun.xml.internal.bind.v2.model.core.ValuePropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.WildcardMode;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.SwaRefAdapter;
import static com.sun.xml.internal.bind.v2.schemagen.Util.*;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Any;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.AttrDecls;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ComplexExtension;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ComplexType;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ComplexTypeHost;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ExplicitGroup;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Import;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.List;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.LocalAttribute;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.LocalElement;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Occurs;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Schema;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.SimpleExtension;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.SimpleRestrictionModel;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.SimpleType;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.SimpleTypeHost;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.TopLevelAttribute;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.TopLevelElement;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.TypeHost;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.TxwException;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.output.ResultFactory;

/**
 * Generates a set of W3C XML Schema documents from a set of Java classes.
 *
 * <p>
 * A client must invoke methods in the following order:
 * <ol>
 *  <li>Create a new {@link XmlSchemaGenerator}
 *  <li>Invoke {@link #add} methods, multiple times if necessary.
 *  <li>Invoke {@link #write}
 *  <li>Discard the {@link XmlSchemaGenerator}.
 * </ol>
 *
 * @author Ryan Shoemaker
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class XmlSchemaGenerator<T,C,F,M> {

    private static final Logger logger = Util.getClassLogger();

    /**
     * Java classes to be written, organized by their namespace.
     *
     * <p>
     * We use a {@link TreeMap} here so that the suggested names will
     * be consistent across JVMs.
     *
     * @see SchemaOutputResolver#createOutput(String, String)
     */
    private final Map<String,Namespace> namespaces = new TreeMap<String,Namespace>(NAMESPACE_COMPARATOR);

    /** model navigator **/
    private Navigator<T,C,F,M> navigator;

    private final TypeInfoSet<T,C,F,M> types;

    /**
     * Representation for xs:string.
     */
    private final NonElement<T,C> stringType;

    /**
     * Represents xs:anyType.
     */
    private final NonElement<T,C> anyType;

    public XmlSchemaGenerator( Navigator<T,C,F,M> navigator, TypeInfoSet<T,C,F,M> types ) {
        this.navigator = navigator;
        this.types = types;

        this.stringType = types.getTypeInfo(navigator.ref(String.class));
        this.anyType = types.getAnyTypeInfo();

        // populate the object
        for( ClassInfo<T,C> ci : types.beans().values() )
            add(ci);
        for( ElementInfo<T,C> ei1 : types.getElementMappings(null).values() )
            add(ei1);
        for( EnumLeafInfo<T,C> ei : types.enums().values() )
            add(ei);
        for( ArrayInfo<T,C> a : types.arrays().values())
            add(a);
    }

    private Namespace getNamespace(String uri) {
        Namespace n = namespaces.get(uri);
        if(n==null)
            namespaces.put(uri,n=new Namespace(uri));
        return n;
    }

    /**
     * Adds a new class to the list of classes to be written.
     *
     * <p>
     * A {@link ClassInfo} may have two namespaces --- one for the element name
     * and the other for the type name. If they are different, we put the same
     * {@link ClassInfo} to two {@link Namespace}s.
     */
    public void add( ClassInfo<T,C> clazz ) {
        assert clazz!=null;

        String nsUri = null;

        if(clazz.getClazz()==navigator.asDecl(CompositeStructure.class))
            return; // this is a special class we introduced for JAX-WS that we *don't* want in the schema

        if(clazz.isElement()) {
            // put element -> type reference
            nsUri = clazz.getElementName().getNamespaceURI();
            Namespace ns = getNamespace(nsUri);
            ns.classes.add(clazz);
            ns.addDependencyTo(clazz.getTypeName());

            // schedule writing this global element
            add(clazz.getElementName(),false,clazz);
        }

        QName tn = clazz.getTypeName();
        if(tn!=null) {
            nsUri = tn.getNamespaceURI();
        } else {
            // anonymous type
            if(nsUri==null)
                return;
        }

        Namespace n = getNamespace(nsUri);
        n.classes.add(clazz);

        // search properties for foreign namespace references
        for( PropertyInfo<T,C> p : clazz.getProperties()) {
            n.processForeignNamespaces(p);
            if (p instanceof AttributePropertyInfo) {
                AttributePropertyInfo<T,C> ap = (AttributePropertyInfo<T,C>) p;
                String aUri = ap.getXmlName().getNamespaceURI();
                if(aUri.length()>0) {
                    // global attribute
                    getNamespace(aUri).addGlobalAttribute(ap);
                    n.addDependencyTo(ap.getXmlName());
                }
            }
            if (p instanceof ElementPropertyInfo) {
                ElementPropertyInfo<T,C> ep = (ElementPropertyInfo<T,C>) p;
                for (TypeRef<T,C> tref : ep.getTypes()) {
                    String eUri = tref.getTagName().getNamespaceURI();
                    if(eUri.length()>0 && !eUri.equals(n.uri)) {
                        getNamespace(eUri).addGlobalElement(tref);
                        n.addDependencyTo(tref.getTagName());
                    }
                }
            }
        }

        // recurse on baseTypes to make sure that we can refer to them in the schema
        ClassInfo<T,C> bc = clazz.getBaseClass();
        if (bc != null)
            add(bc);
    }

    /**
     * Adds a new element to the list of elements to be written.
     */
    public void add( ElementInfo<T,C> elem ) {
        assert elem!=null;

        QName name = elem.getElementName();
        Namespace n = getNamespace(name.getNamespaceURI());
        n.elementDecls.put(name.getLocalPart(),n.new ElementWithType(true,elem.getContentType()));

        // search for foreign namespace references
        n.processForeignNamespaces(elem.getProperty());
    }

    public void add( EnumLeafInfo<T,C> envm ) {
        assert envm!=null;

        String nsUri = null;

        if(envm.isElement()) {
            // put element -> type reference
            nsUri = envm.getElementName().getNamespaceURI();
            Namespace ns = getNamespace(nsUri);
            ns.enums.add(envm);
            ns.addDependencyTo(envm.getTypeName());

            // schedule writing this global element
            add(envm.getElementName(),false,envm);
        }

        final QName typeName = envm.getTypeName();
        if (typeName != null) {
            nsUri = typeName.getNamespaceURI();
        } else {
            if(nsUri==null)
                return; // anonymous type
        }

        Namespace n = getNamespace(nsUri);
        n.enums.add(envm);

        // search for foreign namespace references
        n.addDependencyTo(envm.getBaseType().getTypeName());
    }

    public void add( ArrayInfo<T,C> a ) {
        assert a!=null;

        final String namespaceURI = a.getTypeName().getNamespaceURI();
        Namespace n = getNamespace(namespaceURI);
        n.arrays.add(a);

        // search for foreign namespace references
        n.addDependencyTo(a.getItemType().getTypeName());
    }

    /**
     * Adds an additional element declaration.
     *
     * @param tagName
     *      The name of the element declaration to be added.
     * @param type
     *      The type this element refers to.
     *      Can be null, in which case the element refers to an empty anonymous complex type.
     */
    public void add( QName tagName, boolean isNillable, NonElement<T,C> type ) {

        if(type!=null && type.getType()==navigator.ref(CompositeStructure.class))
            return; // this is a special class we introduced for JAX-WS that we *don't* want in the schema


        Namespace n = getNamespace(tagName.getNamespaceURI());
        n.elementDecls.put(tagName.getLocalPart(), n.new ElementWithType(isNillable,type));

        // search for foreign namespace references
        if(type!=null)
            n.addDependencyTo(type.getTypeName());
    }

    /**
     * Write out the schema documents.
     */
    public void write(SchemaOutputResolver resolver) throws IOException {
        if(resolver==null)
            throw new IllegalArgumentException();

        // make it fool-proof
        resolver = new FoolProofResolver(resolver);

        Map<Namespace,Result> out = new HashMap<Namespace,Result>();

        // we create a Namespace object for the XML Schema namespace
        // as a side-effect, but we don't want to generate it.
        namespaces.remove(WellKnownNamespace.XML_SCHEMA);

        // first create the outputs for all so that we can resolve references among
        // schema files when we write
        for( Namespace n : namespaces.values() ) {
            final Result output = resolver.createOutput(n.uri,"schema"+(out.size()+1)+".xsd");
            if(output!=null) {  // null result means no schema for that namespace
                out.put(n,output);
            }
        }

        // then write'em all
        for( Namespace n : namespaces.values() ) {
            Result result = out.get(n);
            if(result!=null) {
                n.writeTo( result, out );
                if(result instanceof StreamResult) {
                    OutputStream outputStream = ((StreamResult)result).getOutputStream();
                    if(outputStream != null) {
                        outputStream.close(); // fix for bugid: 6291301
                    } else {
                        final Writer writer = ((StreamResult)result).getWriter();
                        if(writer != null) writer.close();
                    }
                }
            }
        }
    }



    /**
     * Schema components are organized per namespace.
     */
    private class Namespace {
        final String uri;

        /**
         * Other {@link Namespace}s that this namespace depends on.
         */
        private final Set<Namespace> depends = new LinkedHashSet<Namespace>();

        /**
         * If this schema refers to components from this schema by itself.
         */
        private boolean selfReference;

        /**
         * List of classes in this namespace.
         */
        private final Set<ClassInfo<T,C>> classes = new LinkedHashSet<ClassInfo<T,C>>();

        /**
         * Set of enums in this namespace
         */
        private final Set<EnumLeafInfo<T,C>> enums = new LinkedHashSet<EnumLeafInfo<T,C>>();

        /**
         * Set of arrays in this namespace
         */
        private final Set<ArrayInfo<T,C>> arrays = new LinkedHashSet<ArrayInfo<T,C>>();

        /**
         * Global attribute declarations keyed by their local names.
         */
        private final MultiMap<String,NonElement<T,C>> attributeDecls = new MultiMap<String,NonElement<T,C>>(stringType);

        /**
         * Global element declarations to be written, keyed by their local names.
         */
        private final MultiMap<String,ElementDeclaration> elementDecls =
                new MultiMap<String,ElementDeclaration>(new ElementWithType(true,anyType));

        private Form attributeFormDefault;
        private Form elementFormDefault;

        public Namespace(String uri) {
            this.uri = uri;
            assert !XmlSchemaGenerator.this.namespaces.containsKey(uri);
            XmlSchemaGenerator.this.namespaces.put(uri,this);
        }

        /**
         * Process the given PropertyInfo looking for references to namespaces that
         * are foreign to the given namespace.  Any foreign namespace references
         * found are added to the given namespaces dependency list and an &lt;import>
         * is generated for it.
         *
         * @param p the PropertyInfo
         */
        private void processForeignNamespaces(PropertyInfo<T, C> p) {
            // TODO: missing the correct handling of anonymous type,
            // which requires recursive checks
            for( TypeInfo<T, C> t : p.ref()) {
                if(t instanceof Element) {
                    addDependencyTo(((Element)t).getElementName());
                }
                if(t instanceof NonElement) {
                    addDependencyTo(((NonElement)t).getTypeName());
                }
            }
        }

        private void addDependencyTo(@Nullable QName qname) {
            // even though the Element interface says getElementName() returns non-null,
            // ClassInfo always implements Element (even if an instance of ClassInfo might not be an Element).
            // so this check is still necessary
            if(qname==null)   return;

            String nsUri = qname.getNamespaceURI();

            if(nsUri.equals(XML_SCHEMA))
                // no need to explicitly refer to XSD namespace
                return;

            if(nsUri.equals(uri)) {
                selfReference = true;
                return;
            }

            // found a type in a foreign namespace, so make sure we generate an import for it
            depends.add(getNamespace(nsUri));
        }

        /**
         * Writes the schema document to the specified result.
         */
        private void writeTo(Result result, Map<Namespace,Result> out) throws IOException {
            try {
                Schema schema = TXW.create(Schema.class,ResultFactory.createSerializer(result));

                // additional namespace declarations to be made.
                Map<String, String> xmlNs = types.getXmlNs(uri);

                for (Map.Entry<String, String> e : xmlNs.entrySet()) {
                    schema._namespace(e.getValue(),e.getKey());
                }

                attributeFormDefault = Form.get(types.getAttributeFormDefault(uri));
                attributeFormDefault.declare("attributeFormDefault",schema);

                elementFormDefault = Form.get(types.getElementFormDefault(uri));
                // TODO: if elementFormDefault is UNSET, figure out the right default value to use
                elementFormDefault.declare("elementFormDefault",schema);


                // declare XML Schema namespace to be xs, but allow the user to override it.
                // if 'xs' is used for other things, we'll just let TXW assign a random prefix
                if(!xmlNs.containsValue(WellKnownNamespace.XML_SCHEMA)
                && !xmlNs.containsKey("xs"))
                    schema._namespace(WellKnownNamespace.XML_SCHEMA,"xs");
                schema.version("1.0");

                if(uri.length()!=0)
                    schema.targetNamespace(uri);

                // declare prefixes for them at this level, so that we can avoid redundant
                // namespace declarations
                for (Namespace ns : depends) {
                    schema._namespace(ns.uri);
                }

                if(selfReference && uri.length()!=0) {
                    // use common 'tns' prefix for the own namespace
                    // if self-reference is needed
                    schema._namespace(uri,"tns");
                }

                schema._pcdata(newline);

                // refer to other schemas
                for( Namespace n : depends ) {
                    Import imp = schema._import();
                    if(n.uri.length()!=0)
                        imp.namespace(n.uri);
                    imp.schemaLocation(relativize(out.get(n).getSystemId(),result.getSystemId()));
                    schema._pcdata(newline);
                }

                // then write each component
                for (Map.Entry<String,ElementDeclaration> e : elementDecls.entrySet()) {
                    e.getValue().writeTo(e.getKey(),schema);
                    schema._pcdata(newline);
                }
                for (ClassInfo<T, C> c : classes) {
                    if (c.getTypeName()==null) {
                        // don't generate anything if it's an anonymous type
                        continue;
                    }
                    if(uri.equals(c.getTypeName().getNamespaceURI()))
                        writeClass(c, schema);
                    schema._pcdata(newline);
                }
                for (EnumLeafInfo<T, C> e : enums) {
                    if (e.getTypeName()==null) {
                        // don't generate anything if it's an anonymous type
                        continue;
                    }
                    if(uri.equals(e.getTypeName().getNamespaceURI()))
                        writeEnum(e,schema);
                    schema._pcdata(newline);
                }
                for (ArrayInfo<T, C> a : arrays) {
                    writeArray(a,schema);
                    schema._pcdata(newline);
                }
                for (Map.Entry<String,NonElement<T,C>> e : attributeDecls.entrySet()) {
                    TopLevelAttribute a = schema.attribute();
                    a.name(e.getKey());
                    writeTypeRef(a,e.getValue(),"type");
                    schema._pcdata(newline);
                }

                // close the schema
                schema.commit();
            } catch( TxwException e ) {
                logger.log(Level.INFO,e.getMessage(),e);
                throw new IOException(e.getMessage());
            }
        }

        /**
         * Writes a type attribute (if the referenced type is a global type)
         * or writes out the definition of the anonymous type in place (if the referenced
         * type is not a global type.)
         *
         * Also provides processing for ID/IDREF, MTOM @xmime, and swa:ref
         *
         * ComplexTypeHost and SimpleTypeHost don't share an api for creating
         * and attribute in a type-safe way, so we will compromise for now and
         * use _attribute().
         */
        private void writeTypeRef(TypeHost th, NonElementRef<T, C> typeRef, String refAttName) {
            // ID / IDREF handling
            switch(typeRef.getSource().id()) {
            case ID:
                th._attribute(refAttName, new QName(WellKnownNamespace.XML_SCHEMA, "ID"));
                return;
            case IDREF:
                th._attribute(refAttName, new QName(WellKnownNamespace.XML_SCHEMA, "IDREF"));
                return;
            case NONE:
                // no ID/IDREF, so continue on and generate the type
                break;
            default:
                throw new IllegalStateException();
            }

            // MTOM handling
            MimeType mimeType = typeRef.getSource().getExpectedMimeType();
            if( mimeType != null ) {
                th._attribute(new QName(WellKnownNamespace.XML_MIME_URI, "expectedContentTypes", "xmime"), mimeType.toString());
            }

            // ref:swaRef handling
            if(generateSwaRefAdapter(typeRef)) {
                th._attribute(refAttName, new QName(WellKnownNamespace.SWA_URI, "swaRef", "ref"));
                return;
            }

            // type name override
            if(typeRef.getSource().getSchemaType()!=null) {
                th._attribute(refAttName,typeRef.getSource().getSchemaType());
                return;
            }


            // normal type generation
            writeTypeRef(th, typeRef.getTarget(), refAttName);
        }

        /**
         * Examine the specified element ref and determine if a swaRef attribute needs to be generated
         * @param typeRef
         */
        private boolean generateSwaRefAdapter(NonElementRef<T,C> typeRef) {
            final Adapter<T,C> adapter = typeRef.getSource().getAdapter();
            if (adapter == null) return false;
            final Object o = navigator.asDecl(SwaRefAdapter.class);
            if (o == null) return false;
            return (o.equals(adapter.adapterType));
        }

        /**
         * Writes a type attribute (if the referenced type is a global type)
         * or writes out the definition of the anonymous type in place (if the referenced
         * type is not a global type.)
         *
         * @param th
         *      the TXW interface to which the attribute will be written.
         * @param type
         *      type to be referenced.
         * @param refAttName
         *      The name of the attribute used when referencing a type by QName.
         */
        private void writeTypeRef(TypeHost th, NonElement<T,C> type, String refAttName) {
            if(type.getTypeName()==null) {
                if(type instanceof ClassInfo) {
                    writeClass( (ClassInfo<T,C>)type, th );
                } else {
                    writeEnum( (EnumLeafInfo<T,C>)type, (SimpleTypeHost)th);
                }
            } else {
                th._attribute(refAttName,type.getTypeName());
            }
        }

        /**
         * writes the schema definition for the given array class
         */
        private void writeArray(ArrayInfo<T, C> a, Schema schema) {
            ComplexType ct = schema.complexType().name(a.getTypeName().getLocalPart());
            ct._final("#all");
            LocalElement le = ct.sequence().element().name("item");
            le.type(a.getItemType().getTypeName());
            le.minOccurs(0).maxOccurs("unbounded");
            le.nillable(true);
            ct.commit();
        }

        /**
         * writes the schema definition for the specified type-safe enum in the given TypeHost
         */
        private void writeEnum(EnumLeafInfo<T, C> e, SimpleTypeHost th) {
            SimpleType st = th.simpleType();
            writeName(e,st);

            SimpleRestrictionModel base = st.restriction();
            writeTypeRef(base, e.getBaseType(), "base");

            for (EnumConstant c : e.getConstants()) {
                base.enumeration().value(c.getLexicalValue());
            }
            st.commit();
        }

        /**
         * Writes the schema definition for the specified class to the schema writer.
         *
         * @param c the class info
         * @param parent the writer of the parent element into which the type will be defined
         */
        private void writeClass(ClassInfo<T,C> c, TypeHost parent) {
            // special handling for value properties
            if (containsValueProp(c)) {
                if (c.getProperties().size() == 1) {
                    // [RESULT 2 - simpleType if the value prop is the only prop]
                    //
                    // <simpleType name="foo">
                    //   <xs:restriction base="xs:int"/>
                    // </>
                    ValuePropertyInfo<T,C> vp = (ValuePropertyInfo<T,C>)c.getProperties().get(0);
                    SimpleType st = ((SimpleTypeHost)parent).simpleType();
                    writeName(c, st);
                    if(vp.isCollection()) {
                        writeTypeRef(st.list(),vp.getTarget(),"itemType");
                    } else {
                        writeTypeRef(st.restriction(),vp.getTarget(),"base");
                    }
                    return;
                } else {
                    // [RESULT 1 - complexType with simpleContent]
                    //
                    // <complexType name="foo">
                    //   <simpleContent>
                    //     <extension base="xs:int"/>
                    //       <attribute name="b" type="xs:boolean"/>
                    //     </>
                    //   </>
                    // </>
                    // ...
                    //   <element name="f" type="foo"/>
                    // ...
                    ComplexType ct = ((ComplexTypeHost)parent).complexType();
                    writeName(c,ct);
                    if(c.isFinal())
                        ct._final("extension restriction");

                    SimpleExtension se = ct.simpleContent().extension();
                    se.block(); // because we might have attribute before value
                    for (PropertyInfo<T,C> p : c.getProperties()) {
                        switch (p.kind()) {
                        case ATTRIBUTE:
                            handleAttributeProp((AttributePropertyInfo<T,C>)p,se);
                            break;
                        case VALUE:
                            TODO.checkSpec("what if vp.isCollection() == true?");
                            ValuePropertyInfo vp = (ValuePropertyInfo) p;
                            se.base(vp.getTarget().getTypeName());
                            break;
                        case ELEMENT:   // error
                        case REFERENCE: // error
                        default:
                            assert false;
                            throw new IllegalStateException();
                        }
                    }
                    se.commit();
                }
                TODO.schemaGenerator("figure out what to do if bc != null");
                TODO.checkSpec("handle sec 8.9.5.2, bullet #4");
                // Java types containing value props can only contain properties of type
                // ValuePropertyinfo and AttributePropertyInfo which have just been handled,
                // so return.
                return;
            }

            // we didn't fall into the special case for value props, so we
            // need to initialize the ct.
            // generate the complexType
            ComplexType ct = ((ComplexTypeHost)parent).complexType();
            writeName(c,ct);
            if(c.isFinal())
                ct._final("extension restriction");
            if(c.isAbstract())
                ct._abstract(true);

            // hold the ct open in case we need to generate @mixed below...
            ct.block();

            // either <sequence> or <all>
            ExplicitGroup compositor = null;

            // only necessary if this type has a base class we need to extend from
            AttrDecls contentModel = ct;

            // if there is a base class, we need to generate an extension in the schema
            final ClassInfo<T,C> bc = c.getBaseClass();
            if (bc != null) {
                ComplexExtension ce = ct.complexContent().extension();
                contentModel = ce;

                ce.base(bc.getTypeName());
                // TODO: what if the base type is anonymous?
                // ordered props go in a sequence, unordered go in an all
                if( c.isOrdered() ) {
                    compositor = ce.sequence();
                } else {
                    compositor = ce.all();
                }
            }

            // iterate over the properties
            if (c.hasProperties()) {
                if( compositor == null ) { // if there is no extension base, create a top level seq
                    // ordered props go in a sequence, unordered go in an all
                    if( c.isOrdered() ) {
                        compositor = ct.sequence();
                    } else {
                        compositor = ct.all();
                    }
                }

                // block writing the compositor because we might need to
                // write some out of order attributes to handle min/maxOccurs
                compositor.block();

                for (PropertyInfo<T,C> p : c.getProperties()) {
                    // handling for <complexType @mixed='true' ...>
                    if(p instanceof ReferencePropertyInfo && ((ReferencePropertyInfo)p).isMixed()) {
                        ct.mixed(true);
                    }
                    writeProperty(p, contentModel, compositor);
                }

                compositor.commit();
            }

            // look for wildcard attributes
            if( c.hasAttributeWildcard()) {
                // TODO: not type safe
                contentModel.anyAttribute().namespace("##other").processContents("skip");
            }

            // finally commit the ct
            ct.commit();
        }

        /**
         * Writes the name attribute if it's named.
         */
        private void writeName(NonElement<T,C> c, TypedXmlWriter xw) {
            QName tn = c.getTypeName();
            if(tn!=null)
                xw._attribute("name",tn.getLocalPart());  // named
        }

        private boolean containsValueProp(ClassInfo<T, C> c) {
            for (PropertyInfo p : c.getProperties()) {
                if (p instanceof ValuePropertyInfo) return true;
            }
            return false;
        }

        /**
         * write the schema definition(s) for the specified property
         */
        private void writeProperty(PropertyInfo<T,C> p, AttrDecls attr, ExplicitGroup compositor) {
            switch(p.kind()) {
            case ELEMENT:
                handleElementProp((ElementPropertyInfo<T,C>)p, compositor);
                break;
            case ATTRIBUTE:
                handleAttributeProp((AttributePropertyInfo<T,C>)p, attr);
                break;
            case REFERENCE:
                handleReferenceProp((ReferencePropertyInfo<T,C>)p, compositor);
                break;
            case MAP:
                handleMapProp((MapPropertyInfo<T,C>)p, compositor);
                break;
            case VALUE:
                // value props handled above in writeClass()
                assert false;
                throw new IllegalStateException();
                // break();
            default:
                assert false;
                throw new IllegalStateException();
            }
        }

        /**
         * Generate the proper schema fragment for the given element property into the
         * specified schema compositor.
         *
         * The element property may or may not represent a collection and it may or may
         * not be wrapped.
         *
         * @param ep the element property
         * @param compositor the schema compositor (sequence or all)
         */
        private void handleElementProp(ElementPropertyInfo<T,C> ep, ExplicitGroup compositor) {
            QName ename = ep.getXmlName();
            Occurs occurs = null;

            if (ep.isValueList()) {
                TypeRef<T,C> t = ep.getTypes().get(0);
                LocalElement e = compositor.element();

                QName tn = t.getTagName();
                e.name(tn.getLocalPart());
                List lst = e.simpleType().list();
                writeTypeRef(lst,t, "itemType");
                elementFormDefault.writeForm(e,tn);
                return;
            }

            if (ep.isCollection()) {
                if (ename != null) { // wrapped collection
                    LocalElement e = compositor.element();
                    if(ename.getNamespaceURI().length()>0) {
                        if (!ename.getNamespaceURI().equals(this.uri)) {
                            // TODO: we need to generate the corresponding element declaration for this
                            // table 8-25: Property/field element wrapper with ref attribute
                            e.ref(new QName(ename.getNamespaceURI(), ename.getLocalPart()));
                            return;
                        }
                    }
                    elementFormDefault.writeForm(e,ename);

                    ComplexType p = e.name(ename.getLocalPart()).complexType();
                    if(ep.isCollectionNillable()) {
                        e.nillable(true);
                    } else {
                        e.minOccurs(0);
                    }
                    if (ep.getTypes().size() == 1) {
                        compositor = p.sequence();
                    } else {
                        compositor = p.choice();
                        occurs = compositor;
                    }
                } else { // unwrapped collection
                    if (ep.getTypes().size() > 1) {
                        compositor = compositor.choice();
                        occurs = compositor;
                    }
                }
            } else {
                if (ep.getTypes().size() > 1) {
                    compositor = compositor.choice();
                    occurs = compositor;
                }
            }


            // fill in the content model
            for (TypeRef<T,C> t : ep.getTypes()) {
                LocalElement e = compositor.element();
                if (occurs == null) occurs = e;
                QName tn = t.getTagName();

                if(canBeDirectElementRef(t,tn) || (!tn.getNamespaceURI().equals(uri) && tn.getNamespaceURI().length()>0)) {
                    e.ref(tn);
                } else {
                    e.name(tn.getLocalPart());
                    writeTypeRef(e,t, "type");
                    elementFormDefault.writeForm(e,tn);
                }

                if (t.isNillable()) {
                    e.nillable(true);
                }
                if(t.getDefaultValue()!=null)
                    e._default(t.getDefaultValue());
            }

            if (ep.isCollection())
                occurs.maxOccurs("unbounded");

            if (!ep.isRequired())
                // see Spec table 8-13
                occurs.minOccurs(0);
            // else minOccurs defaults to 1
        }

        /**
         * Checks if we can collapse
         * &lt;element name='foo' type='t' /> to &lt;element ref='foo' />.
         *
         * This is possible if we already have such declaration to begin with.
         */
        private boolean canBeDirectElementRef(TypeRef<T, C> t, QName tn) {
            if(t.isNillable() || t.getDefaultValue()!=null)
                // can't put those attributes on <element ref>
                return false;

            if(t.getTarget() instanceof Element) {
                Element te = (Element) t.getTarget();
                QName targetTagName = te.getElementName();
                return targetTagName!=null && targetTagName.equals(tn);
            }

            return false;
        }


        /**
         * Generate an attribute for the specified property on the specified complexType
         *
         * @param ap the attribute
         * @param attr the schema definition to which the attribute will be added
         */
        private void handleAttributeProp(AttributePropertyInfo<T,C> ap, AttrDecls attr) {
            // attr is either a top-level ComplexType or a ComplexExtension
            //
            // [RESULT]
            //
            // <complexType ...>
            //   <...>...</>
            //   <attribute name="foo" type="xs:int"/>
            // </>
            //
            // or
            //
            // <complexType ...>
            //   <complexContent>
            //     <extension ...>
            //       <...>...</>
            //     </>
            //   </>
            //   <attribute name="foo" type="xs:int"/>
            // </>
            //
            // or it could also be an in-lined type (attr ref)
            //
            LocalAttribute localAttribute = attr.attribute();

            final String attrURI = ap.getXmlName().getNamespaceURI();
            if (attrURI.equals("") || attrURI.equals(uri)) {
                localAttribute.name(ap.getXmlName().getLocalPart());

                TypeHost th; String refAtt;
                if( ap.isCollection() ) {
                    th = localAttribute.simpleType().list();
                    refAtt = "itemType";
                } else {
                    th = localAttribute;
                    refAtt = "type";
                }
                writeTypeRef(th, ap, refAtt);

                attributeFormDefault.writeForm(localAttribute,ap.getXmlName());
            } else { // generate an attr ref
                localAttribute.ref(ap.getXmlName());
            }

            if(ap.isRequired()) {
                // TODO: not type safe
                localAttribute.use("required");
            }
        }

        /**
         * Generate the proper schema fragment for the given reference property into the
         * specified schema compositor.
         *
         * The reference property may or may not refer to a collection and it may or may
         * not be wrapped.
         *
         * @param rp
         * @param compositor
         */
        private void handleReferenceProp(ReferencePropertyInfo<T,C> rp, ExplicitGroup compositor) {
            QName ename = rp.getXmlName();
            Occurs occurs = null;

            if (rp.isCollection()) {
                if (ename != null) { // wrapped collection
                    LocalElement e = compositor.element();
                    ComplexType p = e.name(ename.getLocalPart()).complexType();
                    elementFormDefault.writeForm(e,ename);
                    if(rp.isCollectionNillable())
                        e.nillable(true);
                    if (rp.getElements().size() == 1) {
                        compositor = p.sequence();
                    } else {
                        compositor = p.choice();
                        occurs = compositor;
                    }
                } else { // unwrapped collection
                    if (rp.getElements().size() > 1) {
                        compositor = compositor.choice();
                        occurs = compositor;
                    }
                }
            }

            // fill in content model
            TODO.checkSpec("should we loop in the case of a non-collection ep?");
            for (Element<T,C> e : rp.getElements()) {
                LocalElement eref = compositor.element();
                if (occurs == null) occurs = eref;

                QName en = e.getElementName();
                if(e.getScope()!=null) {
                    // scoped. needs to be inlined
                    boolean qualified = en.getNamespaceURI().equals(uri);
                    boolean unqualified = en.getNamespaceURI().equals("");
                    if(qualified || unqualified) {
                        // can be inlined indeed

                        // write form="..." if necessary
                        if(unqualified) {
                            if(elementFormDefault.isEffectivelyQualified)
                                eref.form("unqualified");
                        } else {
                            if(!elementFormDefault.isEffectivelyQualified)
                                eref.form("qualified");
                        }

                        eref.name(en.getLocalPart());

                        // write out type reference
                        if(e instanceof ClassInfo) {
                            writeTypeRef(eref,(ClassInfo<T,C>)e,"type");
                        } else {
                            writeTypeRef(eref,((ElementInfo<T,C>)e).getContentType(),"type");
                        }
                        continue;
                    }
                }
                eref.ref(en);
            }

            WildcardMode wc = rp.getWildcard();
            if( wc != null ) {
                Any any = compositor.any();
                final String pcmode = getProcessContentsModeName(wc);
                if( pcmode != null ) any.processContents(pcmode);
                TODO.schemaGenerator("generate @namespace ???");
                if( occurs == null ) occurs = any;
            }

            if(rp.isCollection())
                occurs.maxOccurs("unbounded");

        }

        /**
         * Generate the proper schema fragment for the given map property into the
         * specified schema compositor.
         *
         * @param mp the map property
         * @param compositor the schema compositor (sequence or all)
         */
        private void handleMapProp(MapPropertyInfo<T,C> mp, ExplicitGroup compositor) {
            QName ename = mp.getXmlName();

            LocalElement e = compositor.element();
            elementFormDefault.writeForm(e,ename);
            if(mp.isCollectionNillable())
                e.nillable(true);
            ComplexType p = e.name(ename.getLocalPart()).complexType();

            // TODO: entry, key, and value are always unqualified. that needs to be fixed, too.
            // TODO: we need to generate the corresponding element declaration, if they are qualified
            e = p.sequence().element();
            e.name("entry").minOccurs(0).maxOccurs("unbounded");

            ExplicitGroup seq = e.complexType().sequence();
            writeKeyOrValue(seq, "key", mp.getKeyType());
            writeKeyOrValue(seq, "value", mp.getValueType());
        }

        private void writeKeyOrValue(ExplicitGroup seq, String tagName, NonElement<T, C> typeRef) {
            LocalElement key = seq.element().name(tagName);
            key.minOccurs(0);
            writeTypeRef(key, typeRef, "type");
        }

        public void addGlobalAttribute(AttributePropertyInfo<T,C> ap) {
            attributeDecls.put( ap.getXmlName().getLocalPart(), ap.getTarget() );
            addDependencyTo(ap.getTarget().getTypeName());
        }

        public void addGlobalElement(TypeRef<T,C> tref) {
            elementDecls.put( tref.getTagName().getLocalPart(), new ElementWithType(false,tref.getTarget()) );
            addDependencyTo(tref.getTarget().getTypeName());
        }

        /**
         * Represents a global element declaration to be written.
         *
         * <p>
         * Because multiple properties can name the same global element even if
         * they have different Java type, the schema generator first needs to
         * walk through the model and decide what to generate for the given
         * element declaration.
         *
         * <p>
         * This class represents what will be written, and its {@link #equals(Object)}
         * method is implemented in such a way that two identical declarations
         * are considered as the same.
         */
        abstract class ElementDeclaration {
            /**
             * Returns true if two {@link ElementDeclaration}s are representing
             * the same schema fragment.
             */
            public abstract boolean equals(Object o);
            public abstract int hashCode();

            /**
             * Generates the declaration.
             */
            public abstract void writeTo(String localName, Schema schema);
        }

        /**
         * {@link ElementDeclaration} that refers to a {@link NonElement}.
         */
        class ElementWithType extends ElementDeclaration {
            private final boolean nillable;
            private final NonElement<T,C> type;

            public ElementWithType(boolean nillable,NonElement<T, C> type) {
                this.type = type;
                this.nillable = nillable;
            }

            public void writeTo(String localName, Schema schema) {
                TopLevelElement e = schema.element().name(localName);
                if(nillable)
                    e.nillable(true);
                if(type!=null) {
                    writeTypeRef(e,type, "type");
                } else {
                    e.complexType();    // refer to the nested empty complex type
                }
                e.commit();
            }

            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                final ElementWithType that = (ElementWithType) o;
                return type.equals(that.type);
            }

            public int hashCode() {
                return type.hashCode();
            }
        }
    }


    /**
     * return the string representation of the processContents mode of the
     * give wildcard, or null if it is the schema default "strict"
     *
     */
    private static String getProcessContentsModeName(WildcardMode wc) {
        switch(wc) {
        case LAX:
        case SKIP:
            return wc.name().toLowerCase();
        case STRICT:
            return null;
        default:
            throw new IllegalStateException();
        }
    }


    /**
     * TODO: JAX-WS dependency on this method - consider moving this method into com.sun.tools.internal.jxc.util.Util
     *
     * Relativizes a URI by using another URI (base URI.)
     *
     * <p>
     * For example, {@code relative("http://www.sun.com/abc/def","http://www.sun.com/pqr/stu") => "../abc/def"}
     *
     * <p>
     * This method only works on hierarchical URI's, not opaque URI's (refer to the
     * <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/net/URI.html">java.net.URI</a>
     * javadoc for complete definitions of these terms.
     *
     * <p>
     * This method will not normalize the relative URI.
     *
     * @return the relative URI or the original URI if a relative one could not be computed
     */
    protected static String relativize(String uri, String baseUri) {
        try {
            assert uri!=null;

            if(baseUri==null)   return uri;

            URI theUri = new URI(escapeURI(uri));
            URI theBaseUri = new URI(escapeURI(baseUri));

            if (theUri.isOpaque() || theBaseUri.isOpaque())
                return uri;

            if (!equalsIgnoreCase(theUri.getScheme(), theBaseUri.getScheme()) ||
                    !equal(theUri.getAuthority(), theBaseUri.getAuthority()))
                return uri;

            String uriPath = theUri.getPath();
            String basePath = theBaseUri.getPath();

            // normalize base path
            if (!basePath.endsWith("/")) {
                basePath = normalizeUriPath(basePath);
            }

            if( uriPath.equals(basePath))
                return ".";

            String relPath = calculateRelativePath(uriPath, basePath);

            if (relPath == null)
                return uri; // recursion found no commonality in the two uris at all
            StringBuffer relUri = new StringBuffer();
            relUri.append(relPath);
            if (theUri.getQuery() != null)
                relUri.append('?').append(theUri.getQuery());
            if (theUri.getFragment() != null)
                relUri.append('#').append(theUri.getFragment());

            return relUri.toString();
        } catch (URISyntaxException e) {
            throw new InternalError("Error escaping one of these uris:\n\t"+uri+"\n\t"+baseUri);
        }
    }

    private static String calculateRelativePath(String uri, String base) {
        if (base == null) {
            return null;
        }
        if (uri.startsWith(base)) {
            return uri.substring(base.length());
        } else {
            return "../" + calculateRelativePath(uri, getParentUriPath(base));
        }
    }

    /**
     * JAX-RPC wants the namespaces to be sorted in the reverse order
     * so that the empty namespace "" comes to the very end. Don't ask me why.
     */
    private static final Comparator<String> NAMESPACE_COMPARATOR = new Comparator<String>() {
        public int compare(String lhs, String rhs) {
            return -lhs.compareTo(rhs);
        }
    };

    private static final String newline = "\n";
}
