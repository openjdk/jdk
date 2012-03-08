/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JPackage;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.api.ClassNameAllocator;
import com.sun.tools.internal.xjc.generator.bean.BeanGenerator;
import com.sun.tools.internal.xjc.generator.bean.ImplStructureStrategy;
import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.model.nav.NavigatorImpl;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.reader.xmlschema.Messages;
import com.sun.tools.internal.xjc.util.ErrorReceiverFilter;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.xml.internal.bind.v2.model.core.Ref;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.util.FlattenIterator;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSSchemaSet;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;

/**
 * Root of the object model that represents the code that needs to be generated.
 *
 * <p>
 * A {@link Model} is a schema language neutral representation of the
 * result of a schema parsing. The back-end then works against this model
 * to turn this into a series of Java source code.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Model implements TypeInfoSet<NType,NClass,Void,Void>, CCustomizable {

    /**
     * Generated beans.
     */
    private final Map<NClass,CClassInfo> beans = new LinkedHashMap<NClass,CClassInfo>();

    /**
     * Generated enums.
     */
    private final Map<NClass,CEnumLeafInfo> enums = new LinkedHashMap<NClass,CEnumLeafInfo>();

    /**
     * The element mappings.
     */
    private final Map<NClass/*scope*/,Map<QName,CElementInfo>> elementMappings =
        new HashMap<NClass,Map<QName,CElementInfo>>();

    private final Iterable<? extends CElementInfo> allElements =
        new Iterable<CElementInfo>() {
            public Iterator<CElementInfo> iterator() {
                return new FlattenIterator<CElementInfo>(elementMappings.values());
            }
        };

    /**
     * {@link TypeUse}s for all named types.
     * <p>
     * I really don't want to promote the notion of a 'type' in any place except in the XML Schema code,
     * but this needs to be exposed for JAX-RPC. A reference to a named XML type will be converted into
     * a reference to a Java type with annotations.
     */
    private final Map<QName,TypeUse> typeUses = new LinkedHashMap<QName, TypeUse>();

    /**
     * {@link NameConverter} to be used.
     */
    private NameConverter nameConverter;

    /**
     * Single linked list that connects all {@link CCustomizations} that belong to this model.
     *
     * @see CCustomizations#next
     */
    /*package*/ CCustomizations customizations;

    /**
     * This field controls the generation of package level annotations for s2j
     */
    private boolean packageLevelAnnotations = true;

    /**
     * If this model was built from XML Schema, this field
     * stores the root object of the parse schema model.
     * Otherwise null.
     *
     * @sine 2.1.1
     */
    public final XSSchemaSet schemaComponent;

    private CCustomizations gloablCustomizations = new CCustomizations();

    /**
     * @param nc
     *      Usually this should be set in the constructor, but we do allow this parameter
     *      to be initially null, and then set later.
     * @param schemaComponent
     *      The source schema model, if this is built from XSD.
     */
    public Model( Options opts, JCodeModel cm, NameConverter nc, ClassNameAllocator allocator, XSSchemaSet schemaComponent ) {
        this.options = opts;
        this.codeModel = cm;
        this.nameConverter = nc;
        this.defaultSymbolSpace = new SymbolSpace(codeModel);
        defaultSymbolSpace.setType(codeModel.ref(Object.class));

        elementMappings.put(null,new HashMap<QName,CElementInfo>());

        if(opts.automaticNameConflictResolution)
            allocator = new AutoClassNameAllocator(allocator);
        this.allocator = new ClassNameAllocatorWrapper(allocator);
        this.schemaComponent = schemaComponent;
        this.gloablCustomizations.setParent(this,this);
    }

    public void setNameConverter(NameConverter nameConverter) {
        assert this.nameConverter==null;
        assert nameConverter!=null;
        this.nameConverter = nameConverter;
    }

    /**
     * Gets the name converter that shall be used to parse XML names into Java names.
     */
    public final NameConverter getNameConverter() {
        return nameConverter;
    }

    public boolean isPackageLevelAnnotations() {
        return packageLevelAnnotations;
    }

    public void setPackageLevelAnnotations(boolean packageLevelAnnotations) {
        this.packageLevelAnnotations = packageLevelAnnotations;
    }

    /**
     * This model uses this code model exclusively.
     */
    @XmlTransient
    public final JCodeModel codeModel;

    /**
     * Command-line options used for building this model.
     */
    public final Options options;

    /**
     * True to generate serializable classes.
     */
    @XmlAttribute
    public boolean serializable;

    /**
     * serial version UID to be generated.
     *
     * null if not to generate serialVersionUID field.
     */
    @XmlAttribute
    public Long serialVersionUID;

    /**
     * If non-null, all the generated classes should eventually derive from this class.
     */
    @XmlTransient
    public JClass rootClass;

    /**
     * If non-null, all the generated interfaces should eventually derive from this interface.
     */
    @XmlTransient
    public JClass rootInterface;

    /**
     * Specifies the code generation strategy.
     * Must not be null.
     */
    public ImplStructureStrategy strategy = ImplStructureStrategy.BEAN_ONLY;

    /**
     * This allocator has the final say on deciding the class name.
     * Must not be null.
     *
     * <p>
     * Model classes are responsible for using the allocator.
     * This allocator interaction should be transparent to the user/builder
     * of the model.
     */
    /*package*/ final ClassNameAllocatorWrapper allocator;

    /**
     * Default ID/IDREF symbol space. Any ID/IDREF without explicit
     * reference to a symbol space is assumed to use this default
     * symbol space.
     */
    @XmlTransient
    public final SymbolSpace defaultSymbolSpace;

    /** All the defined {@link SymbolSpace}s keyed by their name. */
    private final Map<String,SymbolSpace> symbolSpaces = new HashMap<String,SymbolSpace>();

    public SymbolSpace getSymbolSpace( String name ) {
        SymbolSpace ss = symbolSpaces.get(name);
        if(ss==null)
            symbolSpaces.put(name,ss=new SymbolSpace(codeModel));
        return ss;
    }

    /**
     * Fully-generate the source code into the given model.
     *
     * @return
     *      null if there was any errors. Otherwise it returns a valid
     *      {@link Outline} object, which captures how the model objects
     *      are mapped to the generated source code.
     *      <p>
     *      Add-ons can use those information to further augment the generated
     *      source code.
     */
    public Outline generateCode(Options opt,ErrorReceiver receiver) {
        ErrorReceiverFilter ehf = new ErrorReceiverFilter(receiver);

        // run extensions // moved to BGMBuilder._build() - issue with hyperjaxb3
//        for( Plugin ma : opt.activePlugins )
//            ma.postProcessModel(this,ehf);

        Outline o = BeanGenerator.generate(this, ehf);

        try {// run extensions
            for( Plugin ma : opt.activePlugins )
                ma.run(o,opt,ehf);
        } catch (SAXException e) {
            // fatal error. error should have been reported
            return null;
        }

        // check for unused plug-in customizations.
        // these can be only checked after the plug-ins run, so it's here.
        // the JAXB bindings are checked by XMLSchema's builder.
        Set<CCustomizations> check = new HashSet<CCustomizations>();
        for( CCustomizations c=customizations; c!=null; c=c.next ) {
            if(!check.add(c)) {
                throw new AssertionError(); // detect a loop
            }
            for (CPluginCustomization p : c) {
                if(!p.isAcknowledged()) {
                    ehf.error(
                        p.locator,
                        Messages.format(
                            Messages.ERR_UNACKNOWLEDGED_CUSTOMIZATION,
                            p.element.getNodeName()
                        ));
                    ehf.error(
                        c.getOwner().getLocator(),
                        Messages.format(
                            Messages.ERR_UNACKNOWLEDGED_CUSTOMIZATION_LOCATION));
                }
            }
        }

        if(ehf.hadError())
            o = null;
        return o;
    }

    /**
     * Represents the "top-level binding".
     *
     * <p>
     * This is used to support the use of a schema inside WSDL.
     * For XML Schema, the top-level binding is a map from
     * global element declarations to its representation class.
     *
     * <p>
     * For other schema languages, it should follow the appendicies in
     * WSDL (but in practice no one would use WSDL with a schema language
     * other than XML Schema, so it doesn't really matter.)
     *
     * <p>
     * This needs to be filled by the front-end.
     */
    public final Map<QName,CClassInfo> createTopLevelBindings() {
        Map<QName,CClassInfo> r = new HashMap<QName,CClassInfo>();
        for( CClassInfo b : beans().values() ) {
            if(b.isElement())
                r.put(b.getElementName(),b);
        }
        return r;
    }

    public Navigator<NType,NClass,Void,Void> getNavigator() {
        return NavigatorImpl.theInstance;
    }

    public CNonElement getTypeInfo(NType type) {
        CBuiltinLeafInfo leaf = CBuiltinLeafInfo.LEAVES.get(type);
        if(leaf!=null)      return leaf;

        return getClassInfo(getNavigator().asDecl(type));
    }

    public CBuiltinLeafInfo getAnyTypeInfo() {
        return CBuiltinLeafInfo.ANYTYPE;
    }

    public CNonElement getTypeInfo(Ref<NType,NClass> ref) {
        // TODO: handle XmlValueList
        assert !ref.valueList;
        return getTypeInfo(ref.type);
    }

    public Map<NClass,CClassInfo> beans() {
        return beans;
    }

    public Map<NClass,CEnumLeafInfo> enums() {
        return enums;
    }

    public Map<QName,TypeUse> typeUses() {
        return typeUses;
    }

    /**
     * No array mapping generation for XJC.
     */
    public Map<NType, ? extends CArrayInfo> arrays() {
        return Collections.emptyMap();
    }

    public Map<NType, ? extends CBuiltinLeafInfo> builtins() {
        return CBuiltinLeafInfo.LEAVES;
    }

    public CClassInfo getClassInfo(NClass t) {
        return beans.get(t);
    }

    public CElementInfo getElementInfo(NClass scope,QName name) {
        Map<QName,CElementInfo> m = elementMappings.get(scope);
        if(m!=null) {
            CElementInfo r = m.get(name);
            if(r!=null)     return r;
        }
        return elementMappings.get(null).get(name);
    }

    public Map<QName,CElementInfo> getElementMappings(NClass scope) {
        return elementMappings.get(scope);
    }

    public Iterable<? extends CElementInfo> getAllElements() {
        return allElements;
    }

    /**
     * @deprecated
     *      Always return null. Perhaps you are interested in {@link #schemaComponent}?
     */
    public XSComponent getSchemaComponent() {
        return null;
    }

    /**
     * @deprecated
     *      No line number available for the "root" component.
     */
    public Locator getLocator() {
        LocatorImpl r = new LocatorImpl();
        r.setLineNumber(-1);
        r.setColumnNumber(-1);
        return r;
    }

    /**
     * Gets the global customizations.
     */
    public CCustomizations getCustomizations() {
        return gloablCustomizations;
    }

    /**
     * Not implemented in the compile-time model.
     */
    public Map<String, String> getXmlNs(String namespaceUri) {
        return Collections.emptyMap();
    }

    public Map<String, String> getSchemaLocations() {
        return Collections.emptyMap();
    }

    public XmlNsForm getElementFormDefault(String nsUri) {
        throw new UnsupportedOperationException();
    }

    public XmlNsForm getAttributeFormDefault(String nsUri) {
        throw new UnsupportedOperationException();
    }

    public void dump(Result out) {
        // TODO
        throw new UnsupportedOperationException();
    }

    /*package*/ void add( CEnumLeafInfo e ) {
        enums.put( e.getClazz(), e );
    }

    /*package*/ void add( CClassInfo ci ) {
        beans.put( ci.getClazz(), ci );
    }

    /*package*/ void add( CElementInfo ei ) {
        NClass clazz = null;
        if(ei.getScope()!=null)
            clazz = ei.getScope().getClazz();

        Map<QName,CElementInfo> m = elementMappings.get(clazz);
        if(m==null)
            elementMappings.put(clazz,m=new HashMap<QName,CElementInfo>());
        m.put(ei.getElementName(),ei);
    }


    private final Map<JPackage,CClassInfoParent.Package> cache = new HashMap<JPackage,CClassInfoParent.Package>();

    public CClassInfoParent.Package getPackage(JPackage pkg) {
        CClassInfoParent.Package r = cache.get(pkg);
        if(r==null)
            cache.put(pkg,r=new CClassInfoParent.Package(pkg));
        return r;
    }

    /*package*/ static final Locator EMPTY_LOCATOR;

    static {
        LocatorImpl l = new LocatorImpl();
        l.setColumnNumber(-1);
        l.setLineNumber(-1);
        EMPTY_LOCATOR = l;
    }
}
