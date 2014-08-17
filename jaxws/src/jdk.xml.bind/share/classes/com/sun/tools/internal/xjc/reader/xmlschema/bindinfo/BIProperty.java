/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import java.util.Collection;
import java.util.Collections;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JJavaName;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.generator.bean.field.FieldRenderer;
import com.sun.tools.internal.xjc.generator.bean.field.FieldRendererFactory;
import com.sun.tools.internal.xjc.generator.bean.field.IsSetFieldRenderer;
import com.sun.tools.internal.xjc.model.CAttributePropertyInfo;
import com.sun.tools.internal.xjc.model.CCustomizations;
import com.sun.tools.internal.xjc.model.CElementPropertyInfo;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.tools.internal.xjc.model.CValuePropertyInfo;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.reader.RawTypeSet;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.TypeUtil;
import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.util.XSFinder;
import com.sun.xml.internal.xsom.visitor.XSFunction;

import org.xml.sax.Locator;

/**
 * Property customization.
 *
 * This customization turns an arbitrary schema component
 * into a Java property (some restrictions apply.)
 *
 * <p>
 * All the getter methods (such as <code>getBaseType</code> or
 * <code>getBindStyle</code>) honors the delegation chain of
 * property customization specified in the spec. Namely,
 * if two property customizations are attached to an attribute
 * use and an attribute decl, then anything unspecified in the
 * attribute use defaults to attribute decl.
 *
 * <p>
 * Property customizations are acknowledged
 * (1) when they are actually used, and
 * (2) when they are given at the component, which is mapped to a class.
 *     (so-called "point of declaration" customization)
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
@XmlRootElement(name="property")
public final class BIProperty extends AbstractDeclarationImpl {

    // can be null
    @XmlAttribute
    private String name = null;

    // can be null
    @XmlElement
    private String javadoc = null;

    // can be null
    @XmlElement
    private BaseTypeBean baseType = null;

    // TODO: report 'unsupported' error if this is true
    @XmlAttribute
    private boolean generateFailFastSetterMethod = false;



    public BIProperty(Locator loc, String _propName, String _javadoc,
                      BaseTypeBean _baseType, CollectionTypeAttribute collectionType, Boolean isConst,
                      OptionalPropertyMode optionalProperty, Boolean genElemProp) {
        super(loc);

        this.name = _propName;
        this.javadoc = _javadoc;
        this.baseType = _baseType;
        this.collectionType = collectionType;
        this.isConstantProperty = isConst;
        this.optionalProperty = optionalProperty;
        this.generateElementProperty = genElemProp;
    }

    protected BIProperty() {}

    @Override
    public Collection<BIDeclaration> getChildren() {
        BIConversion conv = getConv();
        if(conv==null)
            return super.getChildren();
        else
            return Collections.<BIDeclaration>singleton(conv);
    }

    public void setParent( BindInfo parent ) {
        super.setParent(parent);
        if(baseType!=null && baseType.conv!=null)
            baseType.conv.setParent(parent);
    }



    /**
     * Returns the customized property name.
     *
     * This method honors the "enableJavaNamingConvention" customization
     * and formats the property name accordingly if necessary.
     *
     * Thus the caller should <em>NOT</em> apply the XML-to-Java name
     * conversion algorithm to the value returned from this method.
     *
     * @param forConstant
     *      If the property name is intended for a constant property name,
     *      set to true. This will change the result
     *
     * @return
     *      This method can return null if the customization doesn't
     *      specify the name.
     */
    public String getPropertyName( boolean forConstant ) {
        if(name!=null) {
            BIGlobalBinding gb = getBuilder().getGlobalBinding();
            NameConverter nc = getBuilder().model.getNameConverter();

            if( gb.isJavaNamingConventionEnabled() && !forConstant )
                // apply XML->Java conversion
                return nc.toPropertyName(name);
            else
                return name;    // ... or don't change the value
        }
        BIProperty next = getDefault();
        if(next!=null)  return next.getPropertyName(forConstant);
        else            return null;
    }

    /**
     * Gets the associated javadoc.
     *
     * @return
     *      null if none is specfieid.
     */
    public String getJavadoc() {
        return javadoc;
    }

    // can be null
    public JType getBaseType() {
        if(baseType!=null && baseType.name!=null) {
            return TypeUtil.getType(getCodeModel(),
                    baseType.name,
                    Ring.get(ErrorReceiver.class),getLocation());
        }
        BIProperty next = getDefault();
        if(next!=null)  return next.getBaseType();
        else            return null;
    }


    // can be null
    @XmlAttribute
    private CollectionTypeAttribute collectionType = null;

    /**
     * Gets the realization of this field.
     * @return Always return non-null.
     */
    CollectionTypeAttribute getCollectionType() {
        if(collectionType!=null)   return collectionType;
        return getDefault().getCollectionType();
    }


    @XmlAttribute
    private OptionalPropertyMode optionalProperty = null;

    // virtual property for @generateIsSetMethod
    @XmlAttribute
    void setGenerateIsSetMethod(boolean b) {
        optionalProperty = b ? OptionalPropertyMode.ISSET : OptionalPropertyMode.WRAPPER;
    }

    public OptionalPropertyMode getOptionalPropertyMode() {
        if(optionalProperty!=null)   return optionalProperty;
        return getDefault().getOptionalPropertyMode();
    }

    // null if delegated
    @XmlAttribute
    private Boolean generateElementProperty = null;
    /**
     * If true, the property will automatically be a reference property.
     * (Talk about confusing names!)
     */
    private Boolean generateElementProperty() {
        if(generateElementProperty!=null)   return generateElementProperty;
        BIProperty next = getDefault();
        if(next!=null)      return next.generateElementProperty();

        return null;
    }


    // true, false, or null (which means the value should be inherited.)
    @XmlAttribute(name="fixedAttributeAsConstantProperty")
    private Boolean isConstantProperty;
    /**
     * Gets the inherited value of the "fixedAttrToConstantProperty" customization.
     *
     * <p>
     * Note that returning true from this method doesn't necessarily mean
     * that a property needs to be mapped to a constant property.
     * It just means that it's mapped to a constant property
     * <b>if an attribute use carries a fixed value.</b>
     *
     * <p>
     * I don't like this semantics but that's what the spec implies.
     */
    public boolean isConstantProperty() {
        if(isConstantProperty!=null)    return isConstantProperty;

        BIProperty next = getDefault();
        if(next!=null)      return next.isConstantProperty();

        // globalBinding always has true or false in this property,
        // so this can't happen
        throw new AssertionError();
    }

    public CValuePropertyInfo createValueProperty(String defaultName,boolean forConstant,
        XSComponent source,TypeUse tu, QName typeName) {

        markAsAcknowledged();
        constantPropertyErrorCheck();

        String name = getPropertyName(forConstant);
        if(name==null) {
            name = defaultName;
            if(tu.isCollection() && getBuilder().getGlobalBinding().isSimpleMode())
                name = JJavaName.getPluralForm(name);
        }

        CValuePropertyInfo prop = wrapUp(new CValuePropertyInfo(name, source, getCustomizations(source), source.getLocator(), tu, typeName), source);
        BIInlineBinaryData.handle(source, prop);
        return prop;
    }

    public CAttributePropertyInfo createAttributeProperty( XSAttributeUse use, TypeUse tu ) {

        boolean forConstant =
            getCustomization(use).isConstantProperty() &&
            use.getFixedValue()!=null;

        String name = getPropertyName(forConstant);
        if(name==null) {
            NameConverter conv = getBuilder().getNameConverter();
            if(forConstant)
                name = conv.toConstantName(use.getDecl().getName());
            else
                name = conv.toPropertyName(use.getDecl().getName());
            if(tu.isCollection() && getBuilder().getGlobalBinding().isSimpleMode())
                name = JJavaName.getPluralForm(name);
        }

        markAsAcknowledged();
        constantPropertyErrorCheck();

        return wrapUp(new CAttributePropertyInfo(name,use,getCustomizations(use),use.getLocator(),
                BGMBuilder.getName(use.getDecl()), tu,
                BGMBuilder.getName(use.getDecl().getType()), use.isRequired() ),use);
    }

    /**
     *
     *
     * @param defaultName
     *      If the name is not customized, this name will be used
     *      as the default. Note that the name conversion <b>MUST</b>
     *      be applied before this method is called if necessary.
     * @param source
     *      Source schema component from which a field is built.
     */
    public CElementPropertyInfo createElementProperty(String defaultName, boolean forConstant, XSParticle source,
                                                      RawTypeSet types) {

        if(!types.refs.isEmpty())
            // if this property is empty, don't acknowleedge the customization
            // this allows pointless property customization to be reported as an error
            markAsAcknowledged();
        constantPropertyErrorCheck();

        String name = getPropertyName(forConstant);
        if(name==null)
            name = defaultName;

        CElementPropertyInfo prop = wrapUp(
            new CElementPropertyInfo(
                name, types.getCollectionMode(),
                types.id(),
                types.getExpectedMimeType(),
                source, getCustomizations(source),
                source.getLocator(), types.isRequired()),
            source);

        types.addTo(prop);

        BIInlineBinaryData.handle(source.getTerm(), prop);
        return prop;
    }

    public CReferencePropertyInfo createDummyExtendedMixedReferenceProperty(
            String defaultName, XSComponent source, RawTypeSet types) {
            return createReferenceProperty(
                    defaultName,
                    false,
                    source,
                    types,
                    true,
                    true,
                    false,
                    true);
    }

    public CReferencePropertyInfo createContentExtendedMixedReferenceProperty(
            String defaultName, XSComponent source, RawTypeSet types) {
            return createReferenceProperty(
                    defaultName,
                    false,
                    source,
                    types,
                    true,
                    false,
                    true,
                    true);
    }

    public CReferencePropertyInfo createReferenceProperty(
            String defaultName, boolean forConstant, XSComponent source,
            RawTypeSet types, boolean isMixed, boolean dummy, boolean content, boolean isMixedExtended) {

        if (types == null) {    // this is a special case where we need to generate content because potential subtypes would need to be able to override what's store inside
            content = true;
        } else {
            if(!types.refs.isEmpty())
                // if this property is empty, don't acknowleedge the customization
                // this allows pointless property customization to be reported as an error
                markAsAcknowledged();
        }
        constantPropertyErrorCheck();

        String name = getPropertyName(forConstant);
        if(name==null)
            name = defaultName;

        CReferencePropertyInfo prop = wrapUp(
                                            new CReferencePropertyInfo(
                                                name,
                                                (types == null) ? true : types.getCollectionMode().isRepeated()||isMixed,
                                                (types == null) ? false : types.isRequired(),
                                                isMixed,
                                                source,
                                                getCustomizations(source), source.getLocator(), dummy, content, isMixedExtended),
                                        source);
        if (types != null) {
            types.addTo(prop);
        }

        BIInlineBinaryData.handle(source, prop);
        return prop;
    }

    public CPropertyInfo createElementOrReferenceProperty(
            String defaultName, boolean forConstant, XSParticle source,
            RawTypeSet types) {

        boolean generateRef;

        switch(types.canBeTypeRefs) {
        case CAN_BE_TYPEREF:
        case SHOULD_BE_TYPEREF:
            // it's up to the use
            Boolean b = generateElementProperty();
            if(b==null) // follow XJC recommendation
                generateRef = types.canBeTypeRefs== RawTypeSet.Mode.CAN_BE_TYPEREF;
            else // use the value user gave us
                generateRef = b;
            break;
        case MUST_BE_REFERENCE:
            generateRef = true;
            break;
        default:
            throw new AssertionError();
        }

        if(generateRef) {
            return createReferenceProperty(defaultName,forConstant,source,types, false, false, false, false);
        } else {
            return createElementProperty(defaultName,forConstant,source,types);
        }
    }

    /**
     * Common finalization of {@link CPropertyInfo} for the create***Property methods.
     */
    private <T extends CPropertyInfo> T wrapUp(T prop, XSComponent source) {
        prop.javadoc = concat(javadoc,
            getBuilder().getBindInfo(source).getDocumentation());
        if(prop.javadoc==null)
            prop.javadoc="";

        // decide the realization.
        FieldRenderer r;
        OptionalPropertyMode opm = getOptionalPropertyMode();
        if(prop.isCollection()) {
            CollectionTypeAttribute ct = getCollectionType();
            r = ct.get(getBuilder().model);
        } else {
            FieldRendererFactory frf = getBuilder().fieldRendererFactory;
            // according to the spec we should bahave as in jaxb1. So we ignore possiblity that property could be nullable
            switch(opm) {
                // the property type can be primitive type if we are to ignore absence
                case PRIMITIVE:
                    r = frf.getRequiredUnboxed();
                    break;
                case WRAPPER:
                    // force the wrapper type
                    r = prop.isOptionalPrimitive() ? frf.getSingle() : frf.getDefault();
                    break;
                case ISSET:
                    r = prop.isOptionalPrimitive() ? frf.getSinglePrimitiveAccess() : frf.getDefault();
                    break;
                default:
                    throw new Error();

            }
        }
        if(opm==OptionalPropertyMode.ISSET) {
            // only isSet is allowed on a collection. these 3 modes aren't really symmetric.

            // if the property is a primitive type, we need an explicit unset because
            // we can't overload the meaning of set(null).
            // if it's a collection, we need to be able to unset it so that we can distinguish
            // null list and empty list.
            r = new IsSetFieldRenderer( r, prop.isOptionalPrimitive()||prop.isCollection(), true );
        }

        prop.realization = r;

        JType bt = getBaseType();
        if(bt!=null)
            prop.baseType = bt;

        return prop;
    }

    private CCustomizations getCustomizations( XSComponent src ) {
        return getBuilder().getBindInfo(src).toCustomizationList();
    }

    private CCustomizations getCustomizations( XSComponent... src ) {
        CCustomizations c = null;
        for (XSComponent s : src) {
            CCustomizations r = getCustomizations(s);
            if(c==null)     c = r;
            else            c = CCustomizations.merge(c,r);
        }
        return c;
    }

    private CCustomizations getCustomizations( XSAttributeUse src ) {
        // customizations for an attribute use should include those defined in the local attribute.
        // this is so that the schema like:
        //
        // <xs:attribute name="foo" type="xs:int">
        //   <xs:annotation><xs:appinfo>
        //     <hyperjaxb:... />
        //
        // would be picked up
        if(src.getDecl().isLocal())
            return getCustomizations(src,src.getDecl());
        else
            return getCustomizations((XSComponent)src);
    }

    private CCustomizations getCustomizations( XSParticle src ) {
        // customizations for a particle  should include those defined in the term unless it's global
        // this is so that the schema like:
        //
        // <xs:sequence>
        //   <xs:element name="foo" type="xs:int">
        //     <xs:annotation><xs:appinfo>
        //       <hyperjaxb:... />
        //
        // would be picked up
        if(src.getTerm().isElementDecl()) {
            XSElementDecl xed = src.getTerm().asElementDecl();
            if(xed.isGlobal())
                return getCustomizations((XSComponent)src);
        }

        return getCustomizations(src,src.getTerm());
    }



    public void markAsAcknowledged() {
        if( isAcknowledged() )  return;

        // mark the parent as well.
        super.markAsAcknowledged();

        BIProperty def = getDefault();
        if(def!=null)   def.markAsAcknowledged();
    }

    private void constantPropertyErrorCheck() {
        if( isConstantProperty!=null && getOwner()!=null ) {
            // run additional check on the isCOnstantProperty value.
            // this value is not allowed if the schema component doesn't have
            // a fixed value constraint.
            //
            // the setParent method associates a customization with the rest of
            // XSOM object graph, so this is the earliest possible moment where
            // we can test this.

            if( !hasFixedValue.find(getOwner()) ) {
                Ring.get(ErrorReceiver.class).error(
                    getLocation(),
                    Messages.ERR_ILLEGAL_FIXEDATTR.format()
                );
                // set this value to null to avoid the same error to be reported more than once.
                isConstantProperty = null;
            }
        }
    }

    /**
     * Function object that returns true if a component has
     * a fixed value constraint.
     */
    private final XSFinder hasFixedValue = new XSFinder() {
        public Boolean attributeDecl(XSAttributeDecl decl) {
            return decl.getFixedValue()!=null;
        }

        public Boolean attributeUse(XSAttributeUse use) {
            return use.getFixedValue()!=null;
        }

        public Boolean schema(XSSchema s) {
            // we allow globalBindings to have isConstantProperty==true,
            // so this method returns true to allow this.
            return true;
        }
    };

    /**
     * Finds a BIProperty which this object should delegate to.
     *
     * @return
     *      always return non-null for normal BIProperties.
     *      If this object is contained in the BIGlobalBinding, then
     *      this method returns null to indicate that there's no more default.
     */
    protected BIProperty getDefault() {
        if(getOwner()==null)    return null;
        BIProperty next = getDefault(getBuilder(),getOwner());
        if(next==this)  return null;    // global.
        else            return next;
    }

    private static BIProperty getDefault( BGMBuilder builder, XSComponent c ) {
        while(c!=null) {
            c = c.apply(defaultCustomizationFinder);
            if(c!=null) {
                BIProperty prop = builder.getBindInfo(c).get(BIProperty.class);
                if(prop!=null)  return prop;
            }
        }

        // default to the global one
        return builder.getGlobalBinding().getDefaultProperty();
    }


    /**
     * Finds a property customization that describes how the given
     * component should be mapped to a property (if it's mapped to
     * a property at all.)
     *
     * <p>
     * Consider an attribute use that does NOT carry a property
     * customization. This schema component is nonetheless considered
     * to carry a (sort of) implicit property customization, whose values
     * are defaulted.
     *
     * <p>
     * This method can be think of the method that returns this implied
     * property customization.
     *
     * <p>
     * Note that this doesn't mean the given component needs to be
     * mapped to a property. But if it does map to a property, it needs
     * to follow this customization.
     *
     * I think this semantics is next to non-sense but I couldn't think
     * of any other way to follow the spec.
     *
     * @param c
     *      A customization effective on this component will be returned.
     *      Can be null just to get the global customization.
     * @return
     *      Always return non-null valid object.
     */
    public static BIProperty getCustomization( XSComponent c ) {
        BGMBuilder builder = Ring.get(BGMBuilder.class);

        // look for a customization on this component
        if( c!=null ) {
            BIProperty prop = builder.getBindInfo(c).get(BIProperty.class);
            if(prop!=null)  return prop;
        }

        // if no such thing exists, defeault.
        return getDefault(builder,c);
    }

    private final static XSFunction<XSComponent> defaultCustomizationFinder = new XSFunction<XSComponent>() {

        public XSComponent attributeUse(XSAttributeUse use) {
            return use.getDecl();   // inherit from the declaration
        }

        public XSComponent particle(XSParticle particle) {
            return particle.getTerm(); // inherit from the term
        }

        public XSComponent schema(XSSchema schema) {
            // no more delegation
            return null;
        }

        // delegates to the context schema object
        public XSComponent attributeDecl(XSAttributeDecl decl) { return decl.getOwnerSchema(); }
        public XSComponent wildcard(XSWildcard wc) { return wc.getOwnerSchema(); }
        public XSComponent modelGroupDecl(XSModelGroupDecl decl) { return decl.getOwnerSchema(); }
        public XSComponent modelGroup(XSModelGroup group) { return group.getOwnerSchema(); }
        public XSComponent elementDecl(XSElementDecl decl) { return decl.getOwnerSchema(); }
        public XSComponent complexType(XSComplexType type) { return type.getOwnerSchema(); }
        public XSComponent simpleType(XSSimpleType st) { return st.getOwnerSchema(); }

        // property customizations are not allowed on these components.
        public XSComponent attGroupDecl(XSAttGroupDecl decl) { throw new IllegalStateException(); }
        public XSComponent empty(XSContentType empty) { throw new IllegalStateException(); }
        public XSComponent annotation(XSAnnotation xsAnnotation) { throw new IllegalStateException(); }
        public XSComponent facet(XSFacet xsFacet) { throw new IllegalStateException(); }
        public XSComponent notation(XSNotation xsNotation) { throw new IllegalStateException(); }
        public XSComponent identityConstraint(XSIdentityConstraint x) { throw new IllegalStateException(); }
        public XSComponent xpath(XSXPath xsxPath) { throw new IllegalStateException(); }
    };


    private static String concat( String s1, String s2 ) {
        if(s1==null)    return s2;
        if(s2==null)    return s1;
        return s1+"\n\n"+s2;
    }

    public QName getName() { return NAME; }

    /** Name of this declaration. */
    public static final QName NAME = new QName(
        Const.JAXB_NSURI, "property" );

    public BIConversion getConv() {
        if(baseType!=null)
            return baseType.conv;
        else
            return null;
    }

    private static final class BaseTypeBean {
        /**
         * If there's a nested javaType customization, this field
         * will keep that customization. Otherwise null.
         *
         * This customization, if present, is used to customize
         * the simple type mapping at the point of reference.
         */
        @XmlElementRef
        BIConversion conv;

        /**
         * Java type name.
         */
        @XmlAttribute
        String name;
    }
}
