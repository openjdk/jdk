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
package com.sun.tools.internal.xjc.reader.xmlschema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.QName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.fmt.JTextFile;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.generator.bean.field.FieldRendererFactory;
import com.sun.tools.internal.xjc.model.CClassInfoParent;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.reader.ModelChecker;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIDeclaration;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIDom;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIGlobalBinding;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BISchemaBinding;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BISerializable;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.tools.internal.xjc.util.CodeModelClassFactory;
import com.sun.tools.internal.xjc.util.ErrorReceiverFilter;
import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSTerm;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.util.XSFinder;

import org.xml.sax.Locator;

/**
 * Root of the XML Schema binder.
 *
 * <div><img src="doc-files/binding_chart.png"/></div>
 *
 * @author Kohsuke Kawaguchi
 */
public class BGMBuilder extends BindingComponent {

    /**
     * Entry point.
     */
    public static Model build( XSSchemaSet _schemas, JCodeModel codeModel,
            ErrorReceiver _errorReceiver, Options opts ) {
        // set up a ring
        final Ring old = Ring.begin();
        try {
            ErrorReceiverFilter ef = new ErrorReceiverFilter(_errorReceiver);

            Ring.add(XSSchemaSet.class,_schemas);
            Ring.add(codeModel);
            Model model = new Model(opts, codeModel, null/*set later*/, opts.classNameAllocator, _schemas);
            Ring.add(model);
            Ring.add(ErrorReceiver.class,ef);
            Ring.add(CodeModelClassFactory.class,new CodeModelClassFactory(ef));

            BGMBuilder builder = new BGMBuilder(opts.defaultPackage,opts.defaultPackage2,
                opts.isExtensionMode(),opts.getFieldRendererFactory());
            builder._build();

            if(ef.hadError())   return null;
            else                return model;
        } finally {
            Ring.end(old);
        }
    }


    /**
     * True if the compiler is running in the extension mode
     * (as opposed to the strict conformance mode.)
     */
    public final boolean inExtensionMode;

    /**
     * If this is non-null, this package name takes over
     * all the schema customizations.
     */
    public final String defaultPackage1;

    /**
     * If this is non-null, this package name will be
     * used when no customization is specified.
     */
    public final String defaultPackage2;

    private final BindGreen green = Ring.get(BindGreen.class);
    private final BindPurple purple = Ring.get(BindPurple.class);

    public final Model model = Ring.get(Model.class);

    public final FieldRendererFactory fieldRendererFactory;

    /**
     * Lazily computed {@link RefererFinder}.
     *
     * @see #getReferer
     */
    private RefererFinder refFinder;




    protected BGMBuilder(String defaultPackage1, String defaultPackage2, boolean _inExtensionMode, FieldRendererFactory fieldRendererFactory) {
        this.inExtensionMode = _inExtensionMode;
        this.defaultPackage1 = defaultPackage1;
        this.defaultPackage2 = defaultPackage2;
        this.fieldRendererFactory = fieldRendererFactory;

        DatatypeConverter.setDatatypeConverter(DatatypeConverterImpl.theInstance);

        promoteGlobalBindings();
    }




    private void _build() {
        // do the binding
        buildContents();
        getClassSelector().executeTasks();

        // additional error check
        // Reports unused customizations to the user as errors.
        Ring.get(UnusedCustomizationChecker.class).run();

        Ring.get(ModelChecker.class).check();
    }


    /** List up all the global bindings. */
    private void promoteGlobalBindings() {
        // promote any global bindings in the schema
        XSSchemaSet schemas = Ring.get(XSSchemaSet.class);

        for( XSSchema s : schemas.getSchemas() ) {
            BindInfo bi = getBindInfo(s);

            // collect all global customizations
            model.getCustomizations().addAll(bi.toCustomizationList());

            BIGlobalBinding gb = bi.get(BIGlobalBinding.class);
            if(gb==null)
                continue;

            if(globalBinding==null) {
                globalBinding = gb;
                globalBinding.markAsAcknowledged();
            } else {
                // acknowledge this customization and report an error
                // otherwise the user will see "customization is attached to a wrong place" error,
                // which is incorrect
                gb.markAsAcknowledged();
                getErrorReporter().error( gb.getLocation(),
                    Messages.ERR_MULTIPLE_GLOBAL_BINDINGS);
                getErrorReporter().error( globalBinding.getLocation(),
                    Messages.ERR_MULTIPLE_GLOBAL_BINDINGS_OTHER);
            }
        }

        if( globalBinding==null ) {
            // no global customization is present.
            // use the default one
            globalBinding = new BIGlobalBinding();
            BindInfo big = new BindInfo();
            big.addDecl(globalBinding);
            big.setOwner(this,null);
        }

        // code generation mode
        model.strategy = globalBinding.getCodeGenerationStrategy();
        model.rootClass = globalBinding.getSuperClass();
        model.rootInterface = globalBinding.getSuperInterface();

        particleBinder = globalBinding.isSimpleMode() ? new ExpressionParticleBinder() : new DefaultParticleBinder();

        // check XJC extensions and realize them
        BISerializable serial = globalBinding.getSerializable();
        if(serial!=null) {
            model.serializable = true;
            model.serialVersionUID = serial.uid;
        }

        // obtain the name conversion mode
        if(globalBinding.nameConverter!=null)
            model.setNameConverter(globalBinding.nameConverter);

        // attach global conversions to the appropriate simple types
        globalBinding.dispatchGlobalConversions(schemas);

        globalBinding.errorCheck();
    }

    /**
     * Global bindings.
     *
     * The empty global binding is set as the default, so that
     * there will be no need to test if the value is null.
     */
    private BIGlobalBinding globalBinding;

    /**
     * Gets the global bindings.
     */
    public @NotNull BIGlobalBinding getGlobalBinding() { return globalBinding; }


    private ParticleBinder particleBinder;

    /**
     * Gets the particle binder for this binding.
     */
    public @NotNull ParticleBinder getParticleBinder() { return particleBinder; }


    /**
     * Name converter that implements "XML->Java name conversion"
     * as specified in the spec.
     *
     * This object abstracts the detail that we use different name
     * conversion depending on the customization.
     *
     * <p>
     * This object should be used to perform any name conversion
     * needs, instead of the JJavaName class in CodeModel.
     */
    public NameConverter getNameConverter() { return model.getNameConverter(); }





    /** Fill-in the contents of each classes. */
    private void buildContents() {
        ClassSelector cs = getClassSelector();
        SimpleTypeBuilder stb = Ring.get(SimpleTypeBuilder.class);

        for( XSSchema s : Ring.get(XSSchemaSet.class).getSchemas() ) {
            BISchemaBinding sb = getBindInfo(s).get(BISchemaBinding.class);

            if(sb!=null && !sb.map) {
                sb.markAsAcknowledged();
                continue;       // no mapping for this package
            }

            getClassSelector().pushClassScope( new CClassInfoParent.Package(
                getClassSelector().getPackage(s.getTargetNamespace())) );

            checkMultipleSchemaBindings(s);
            processPackageJavadoc(s);
            populate(s.getAttGroupDecls(),s);
            populate(s.getAttributeDecls(),s);
            populate(s.getElementDecls(),s);
            populate(s.getModelGroupDecls(),s);

            // fill in typeUses
            for (XSType t : s.getTypes().values()) {
                stb.refererStack.push(t);
                model.typeUses().put( getName(t), cs.bindToType(t,s) );
                stb.refererStack.pop();
            }

            getClassSelector().popClassScope();
        }
    }

    /** Reports an error if there are more than one jaxb:schemaBindings customization. */
    private void checkMultipleSchemaBindings( XSSchema schema ) {
        ArrayList<Locator> locations = new ArrayList<Locator>();

        BindInfo bi = getBindInfo(schema);
        for( BIDeclaration bid : bi ) {
            if( bid.getName()==BISchemaBinding.NAME )
                locations.add( bid.getLocation() );
        }
        if(locations.size()<=1)    return; // OK

        // error
        getErrorReporter().error( locations.get(0),
            Messages.ERR_MULTIPLE_SCHEMA_BINDINGS,
            schema.getTargetNamespace() );
        for( int i=1; i<locations.size(); i++ )
            getErrorReporter().error( (Locator)locations.get(i),
                Messages.ERR_MULTIPLE_SCHEMA_BINDINGS_LOCATION);
    }

    /**
     * Calls {@link ClassSelector} for each item in the iterator
     * to populate class items if there is any.
     */
    private void populate( Map<String,? extends XSComponent> col, XSSchema schema ) {
        ClassSelector cs = getClassSelector();
        for( XSComponent sc : col.values() )
            cs.bindToType(sc,schema);
    }

    /**
     * Generates <code>package.html</code> if the customization
     * says so.
     */
    private void processPackageJavadoc( XSSchema s ) {
        // look for the schema-wide customization
        BISchemaBinding cust = getBindInfo(s).get(BISchemaBinding.class);
        if(cust==null)      return; // not present

        cust.markAsAcknowledged();
        if( cust.getJavadoc()==null )   return;     // no javadoc customization

        // produce a HTML file
        JTextFile html = new JTextFile("package.html");
        html.setContents(cust.getJavadoc());
        getClassSelector().getPackage(s.getTargetNamespace()).addResourceFile(html);
    }






    /**
     * Gets or creates the BindInfo object associated to a schema component.
     *
     * @return
     *      Always return a non-null valid BindInfo object.
     *      Even if no declaration was specified, this method creates
     *      a new BindInfo so that new decls can be added.
     */
    public BindInfo getOrCreateBindInfo( XSComponent schemaComponent ) {

        BindInfo bi = _getBindInfoReadOnly(schemaComponent);
        if(bi!=null)    return bi;

        // XSOM is read-only, so we cannot add new annotations.
        // for components that didn't have annotations,
        // we maintain an external map.
        bi = new BindInfo();
        bi.setOwner(this,schemaComponent);
        externalBindInfos.put(schemaComponent,bi);
        return bi;
    }


    /**
     * Used as a constant instance to represent the empty {@link BindInfo}.
     */
    private final BindInfo emptyBindInfo = new BindInfo();

    /**
     * Gets the BindInfo object associated to a schema component.
     *
     * @return
     *      always return a valid {@link BindInfo} object. If none
     *      is specified for the given component, a dummy empty BindInfo
     *      will be returned.
     */
    public BindInfo getBindInfo( XSComponent schemaComponent ) {
        BindInfo bi = _getBindInfoReadOnly(schemaComponent);
        if(bi!=null)    return bi;
        else            return emptyBindInfo;
    }

    /**
     * Gets the BindInfo object associated to a schema component.
     *
     * @return
     *      null if no bind info is associated to this schema component.
     */
    private BindInfo _getBindInfoReadOnly( XSComponent schemaComponent ) {

        BindInfo bi = externalBindInfos.get(schemaComponent);
        if(bi!=null)    return bi;

        XSAnnotation annon = schemaComponent.getAnnotation();
        if(annon!=null) {
            bi = (BindInfo)annon.getAnnotation();
            if(bi!=null) {
                if(bi.getOwner()==null)
                    bi.setOwner(this,schemaComponent);
                return bi;
            }
        }

        return null;
    }

    /**
     * A map that stores binding declarations augmented by XJC.
     */
    private final Map<XSComponent,BindInfo> externalBindInfos = new HashMap<XSComponent,BindInfo>();

    /**
     * Gets the {@link BIDom} object that applies to the given particle.
     */
    protected final BIDom getLocalDomCustomization( XSParticle p ) {
        BIDom dom = getBindInfo(p).get(BIDom.class);
        if(dom!=null)  return dom;

        // if not, the term might have one.
        dom = getBindInfo(p.getTerm()).get(BIDom.class);
        if(dom!=null)  return dom;

        XSTerm t = p.getTerm();
        // type could also have one, in case of the dom customization
        if(t.isElementDecl())
            return getBindInfo(t.asElementDecl().getType()).get(BIDom.class);
        // similarly the model group in a model group definition may have one.
        if(t.isModelGroupDecl())
            return getBindInfo(t.asModelGroupDecl().getModelGroup()).get(BIDom.class);

        return null;
    }

    /**
     * Returns true if the component should be processed by purple.
     */
    private final XSFinder toPurple = new XSFinder() {
        public Boolean attributeUse(XSAttributeUse use) {
            // attribute use always maps to a property
            return true;
        }

        public Boolean simpleType(XSSimpleType xsSimpleType) {
            // simple type always maps to a type, hence we should take purple
            return true;
        }

        public Boolean wildcard(XSWildcard xsWildcard) {
            // attribute wildcards always maps to a property.
            // element wildcards should have been processed with particle binders
            return true;
        }
    };
    /**
     * If the component maps to a property, forwards to purple, otherwise to green.
     *
     * If the component is mapped to a type, this method needs to return true.
     * See the chart at the class javadoc.
     */
    public void ying( XSComponent sc, @Nullable XSComponent referer ) {
        if(sc.apply(toPurple)==true || getClassSelector().bindToType(sc,referer)!=null)
            sc.visit(purple);
        else
            sc.visit(green);
    }

    private Transformer identityTransformer;

    /**
     * Gets the shared instance of the identity transformer.
     */
    public Transformer getIdentityTransformer() {
        try {
            if(identityTransformer==null)
                identityTransformer = TransformerFactory.newInstance().newTransformer();
            return identityTransformer;
        } catch (TransformerConfigurationException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Find all types that refer to the given complex type.
     */
    public Set<XSComponent> getReferer(XSType c) {
        if(refFinder==null) {
            refFinder = new RefererFinder();
            refFinder.schemaSet(Ring.get(XSSchemaSet.class));
        }
        return refFinder.getReferer(c);
    }

    /**
     * Returns the QName of the declaration.
     * @return null
     *      if the declaration is anonymous.
     */
    public static QName getName(XSDeclaration decl) {
        String local = decl.getName();
        if(local==null) return null;
        return new QName(decl.getTargetNamespace(),local);
    }

    /**
     * Derives a name from a schema component.
     *
     * This method handles prefix/suffix modification and
     * XML-to-Java name conversion.
     *
     * @param name
     *      The base name. This should be things like element names
     *      or type names.
     * @param comp
     *      The component from which the base name was taken.
     *      Used to determine how names are modified.
     */
    public String deriveName( String name, XSComponent comp ) {
        XSSchema owner = comp.getOwnerSchema();

        name = getNameConverter().toClassName(name);

        if( owner!=null ) {
            BISchemaBinding sb = getBindInfo(owner).get(BISchemaBinding.class);

            if(sb!=null)    name = sb.mangleClassName(name,comp);
        }

        return name;
    }
}
