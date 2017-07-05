/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;

import com.sun.codemodel.internal.ClassType;
import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.generator.bean.ImplStructureStrategy;
import static com.sun.tools.internal.xjc.generator.bean.ImplStructureStrategy.BEAN_ONLY;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.SimpleTypeBuilder;
import com.sun.tools.internal.xjc.util.ReadOnlyAdapter;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;

/**
 * Global binding customization. The code is highly temporary.
 *
 * <p>
 * One of the information contained in a global customization
 * is the default binding for properties. This object contains a
 * BIProperty object to keep this information.
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
@XmlRootElement(name="globalBindings")
public final class BIGlobalBinding extends AbstractDeclarationImpl {


    /**
     * Gets the name converter that will govern the {@code XML -> Java}
     * name conversion process for this compilation.
     *
     * <p>
     * The "underscoreBinding" customization will determine
     * the exact object returned from this method. The rest of XJC
     * should just use the NameConverter interface.
     *
     * <p>
     * Always non-null.
     */
    @XmlTransient
    public NameConverter nameConverter = NameConverter.standard;

    // JAXB will use this property to set nameConverter
    @XmlAttribute
    void setUnderscoreBinding( UnderscoreBinding ub ) {
        nameConverter = ub.nc;
    }

    UnderscoreBinding getUnderscoreBinding() {
        throw new IllegalStateException();  // no need for this
    }

    public JDefinedClass getSuperClass() {
        if(superClass==null)    return null;
        return superClass.getClazz(ClassType.CLASS);
    }

    public JDefinedClass getSuperInterface() {
        if(superInterface==null)    return null;
        return superInterface.getClazz(ClassType.INTERFACE);
    }

    public BIProperty getDefaultProperty() {
        return defaultProperty;
    }

    public boolean isJavaNamingConventionEnabled() {
        return isJavaNamingConventionEnabled;
    }

    public BISerializable getSerializable() {
        return serializable;
    }

    public boolean isGenerateElementClass() {
        return generateElementClass;
    }

    public boolean isGenerateMixedExtensions() {
        return generateMixedExtensions;
    }

    public boolean isChoiceContentPropertyEnabled() {
        return choiceContentProperty;
    }

    public int getDefaultEnumMemberSizeCap() {
        return defaultEnumMemberSizeCap;
    }

    public boolean isSimpleMode() {
        return simpleMode!=null;
    }

    public boolean isRestrictionFreshType() {
        return treatRestrictionLikeNewType !=null;
    }

    public EnumMemberMode getEnumMemberMode() {
        return generateEnumMemberName;
    }

    public boolean isSimpleTypeSubstitution() {
        return simpleTypeSubstitution;
    }

    public ImplStructureStrategy getCodeGenerationStrategy() {
        return codeGenerationStrategy;
    }

    public LocalScoping getFlattenClasses() {
        return flattenClasses;
    }

    /**
     * Performs error check
     */
    public void errorCheck() {
        ErrorReceiver er = Ring.get(ErrorReceiver.class);
        for (QName n : enumBaseTypes) {
            XSSchemaSet xs = Ring.get(XSSchemaSet.class);
            XSSimpleType st = xs.getSimpleType(n.getNamespaceURI(), n.getLocalPart());
            if(st==null) {
                er.error(loc,Messages.ERR_UNDEFINED_SIMPLE_TYPE.format(n));
                continue;
            }

            if(!SimpleTypeBuilder.canBeMappedToTypeSafeEnum(st)) {
                er.error(loc,Messages.ERR_CANNOT_BE_BOUND_TO_SIMPLETYPE.format(n));
                continue;
            }
        }
    }

    private static enum UnderscoreBinding {
        @XmlEnumValue("asWordSeparator")
        WORD_SEPARATOR(NameConverter.standard),
        @XmlEnumValue("asCharInWord")
        CHAR_IN_WORD(NameConverter.jaxrpcCompatible);

        final NameConverter nc;

        UnderscoreBinding(NameConverter nc) {
            this.nc = nc;
        }
    }

    /**
     * Returns true if the "isJavaNamingConventionEnabled" option is turned on.
     *
     * In this mode, the compiler is expected to apply XML-to-Java name
     * conversion algorithm even to names given by customizations.
     *
     * This method is intended to be called by other BIXXX classes.
     * The effect of this switch should be hidden inside this package.
     * IOW, the reader.xmlschema package shouldn't be aware of this switch.
     */
    @XmlAttribute(name="enableJavaNamingConventions")
    /*package*/ boolean isJavaNamingConventionEnabled = true;

    /**
     * True to generate classes for every simple type.
     */
    @XmlAttribute(name="mapSimpleTypeDef")
    boolean simpleTypeSubstitution = false;

    /**
     * Gets the default defaultProperty customization.
     */
    @XmlTransient
    private BIProperty defaultProperty;

    /*
        Three properties used to construct a default property
    */
    @XmlAttribute
    private boolean fixedAttributeAsConstantProperty = false;
    @XmlAttribute
    private CollectionTypeAttribute collectionType = new CollectionTypeAttribute();
    @XmlAttribute
    void setGenerateIsSetMethod(boolean b) {
        optionalProperty = b ? OptionalPropertyMode.ISSET : OptionalPropertyMode.WRAPPER;
    }


    /**
     * Returns true if the compiler needs to generate type-safe enum
     * member names when enumeration values cannot be used as constant names.
     */
    @XmlAttribute(name="typesafeEnumMemberName")
    EnumMemberMode generateEnumMemberName = EnumMemberMode.SKIP;

    /**
     * The code generation strategy.
     */
    @XmlAttribute(name="generateValueClass")
    ImplStructureStrategy codeGenerationStrategy = BEAN_ONLY;

    /**
     * Set of datatype names. For a type-safe enum class
     * to be generated, the underlying XML datatype must be derived from
     * one of the types in this set.
     */
    // default value is set in the post-init action
    @XmlAttribute(name="typesafeEnumBase")
    private Set<QName> enumBaseTypes;

    /**
     * Returns {@link BISerializable} if the extension is specified,
     * or null otherwise.
     */
    @XmlElement
    private BISerializable serializable = null;

    /**
     * If {@code <xjc:superClass>} extension is specified,
     * returns the specified root class. Otherwise null.
     */
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    ClassNameBean superClass = null;

    /**
     * If {@code <xjc:superInterface>} extension is specified,
     * returns the specified root class. Otherwise null.
     */
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    ClassNameBean superInterface = null;

    /**
     * Generate the simpler optimized code, but not necessarily
     * conforming to the spec.
     */
    @XmlElement(name="simple",namespace=Const.XJC_EXTENSION_URI)
    String simpleMode = null;

    /**
     * Handles complex type restriction as if it were a new type.
     */
    @XmlElement(name="treatRestrictionLikeNewType",namespace=Const.XJC_EXTENSION_URI)
    String treatRestrictionLikeNewType = null;

    /**
     * True to generate a class for elements by default.
     */
    @XmlAttribute
    boolean generateElementClass = false;

    @XmlAttribute
    boolean generateMixedExtensions = false;

    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    Boolean generateElementProperty = null;

    @XmlAttribute(name="generateElementProperty")     // for JAXB unmarshaller
    private void setGenerateElementPropertyStd(boolean value) {
        generateElementProperty = value;
    }

    @XmlAttribute
    boolean choiceContentProperty = false;

    @XmlAttribute
    OptionalPropertyMode optionalProperty = OptionalPropertyMode.WRAPPER;

    /**
     * Default cap to the number of constants in the enum.
     * We won't attempt to produce a type-safe enum by default
     * if there are more enumeration facets than specified in this field.
     */
    @XmlAttribute(name="typesafeEnumMaxMembers")
    int defaultEnumMemberSizeCap = 256;

    /**
     * If true, interfaces/classes that are normally generated as a nested interface/class
     * will be generated into the package, allowing the generated classes to be flat.
     *
     * See <a href="http://monaco.sfbay/detail.jsf?cr=4969415">Bug 4969415</a> for the motivation.
     */
    @XmlAttribute(name="localScoping")
    LocalScoping flattenClasses = LocalScoping.NESTED;

    /**
     * Globally-defined conversion customizations.
     *
     * @see #setGlobalConversions
     */
    @XmlTransient
    private final Map<QName,BIConversion> globalConversions = new HashMap<QName, BIConversion>();

    // method for JAXB unmarshaller
    @XmlElement(name="javaType")
    private void setGlobalConversions(GlobalStandardConversion[] convs) {
        for (GlobalStandardConversion u : convs) {
            globalConversions.put(u.xmlType,u);
        }
    }

    @XmlElement(name="javaType",namespace=Const.XJC_EXTENSION_URI)
    private void setGlobalConversions2(GlobalVendorConversion[] convs) {
        for (GlobalVendorConversion u : convs) {
            globalConversions.put(u.xmlType,u);
        }
    }

    //
    // these customizations were valid in 1.0, but in 2.0 we don't
    // use them. OTOH, we don't want to issue an error for them,
    // so we just define a mapping and ignore the value.
    //
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    String noMarshaller = null;
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    String noUnmarshaller = null;
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    String noValidator = null;
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    String noValidatingUnmarshaller = null;
    @XmlElement(namespace=Const.XJC_EXTENSION_URI)
    TypeSubstitutionElement typeSubstitution = null;

    /**
     * Another 1.0 compatibility customization (but we accept it
     * and treat it as {@link #serializable})
     */
    @XmlElement(name="serializable",namespace=Const.XJC_EXTENSION_URI)
    void setXjcSerializable(BISerializable s) {
        this.serializable = s;
    }



    private static final class TypeSubstitutionElement {
        @XmlAttribute
        String type;
    }

    public void onSetOwner() {
        super.onSetOwner();
        // if one is given by options, use that
        NameConverter nc = Ring.get(Model.class).options.getNameConverter();
        if(nc!=null)
            nameConverter = nc;
    }

    /**
     * Creates a bind info object with the default values
     */
    public BIGlobalBinding() {
    }

    public void setParent(BindInfo parent) {
        super.setParent(parent);
        // fill in the remaining default values
        if(enumBaseTypes==null)
            enumBaseTypes = Collections.singleton(new QName(WellKnownNamespace.XML_SCHEMA,"string"));

        this.defaultProperty = new BIProperty(getLocation(),null,null,null,
                collectionType, fixedAttributeAsConstantProperty, optionalProperty, generateElementProperty );
        defaultProperty.setParent(parent); // don't forget to initialize the defaultProperty
    }

    /**
     * Moves global BIConversion to the right object.
     */
    public void dispatchGlobalConversions( XSSchemaSet schema ) {
        // also set parent to the global conversions
        for( Map.Entry<QName,BIConversion> e : globalConversions.entrySet() ) {

            QName name = e.getKey();
            BIConversion conv = e.getValue();

            XSSimpleType st = schema.getSimpleType(name.getNamespaceURI(),name.getLocalPart());
            if(st==null) {
                Ring.get(ErrorReceiver.class).error(
                    getLocation(),
                    Messages.ERR_UNDEFINED_SIMPLE_TYPE.format(name)
                );
                continue; // abort
            }

            getBuilder().getOrCreateBindInfo(st).addDecl(conv);
        }
    }


    /**
     * Checks if the given XML Schema built-in type can be mapped to
     * a type-safe enum class.
     *
     * @param typeName
     */
    public boolean canBeMappedToTypeSafeEnum( QName typeName ) {
        return enumBaseTypes.contains(typeName);
    }

    public boolean canBeMappedToTypeSafeEnum( String nsUri, String localName ) {
        return canBeMappedToTypeSafeEnum(new QName(nsUri,localName));
    }

    public boolean canBeMappedToTypeSafeEnum( XSDeclaration decl ) {
        return canBeMappedToTypeSafeEnum( decl.getTargetNamespace(), decl.getName() );
    }


    public QName getName() { return NAME; }
    public static final QName NAME = new QName(
        Const.JAXB_NSURI, "globalBindings" );


    /**
     * Used to unmarshal
     * <pre>{@code
     * <[element] name="className" />
     * }</pre>
     */
    static final class ClassNameBean {
        @XmlAttribute(required=true)
        String name;

        /**
         * Computed from {@link #name} on demand.
         */
        @XmlTransient
        JDefinedClass clazz;

        JDefinedClass getClazz(ClassType t) {
            if (clazz != null) return clazz;
            try {
                JCodeModel codeModel = Ring.get(JCodeModel.class);
                clazz = codeModel._class(name, t);
                clazz.hide();
                return clazz;
            } catch (JClassAlreadyExistsException e) {
                return e.getExistingClass();
            }
        }
    }

    static final class ClassNameAdapter extends ReadOnlyAdapter<ClassNameBean,String> {
        public String unmarshal(ClassNameBean bean) throws Exception {
            return bean.name;
        }
    }

    /**
     * Global {@code <jaxb:javaType>}.
     */
    static final class GlobalStandardConversion extends BIConversion.User {
        @XmlAttribute
        QName xmlType;

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof GlobalStandardConversion) {
                return ((GlobalStandardConversion)obj).xmlType.equals(xmlType);
    }

            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 73 * hash + (this.xmlType != null ? this.xmlType.hashCode() : 0);
            return hash;
        }
    }

    /**
     * Global {@code <xjc:javaType>}.
     */
    static final class GlobalVendorConversion extends BIConversion.UserAdapter {
        @XmlAttribute
        QName xmlType;

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof GlobalVendorConversion) {
                return ((GlobalVendorConversion)obj).xmlType.equals(xmlType);
    }

            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 73 * hash + (this.xmlType != null ? this.xmlType.hashCode() : 0);
            return hash;
        }
    }

    /* don't want to override equals to avoid overriding hashcode for this complex object, too */
    public boolean isEqual(BIGlobalBinding b) {
        boolean equal =
            this.isJavaNamingConventionEnabled == b.isJavaNamingConventionEnabled &&
            this.simpleTypeSubstitution == b.simpleTypeSubstitution &&
            this.fixedAttributeAsConstantProperty == b.fixedAttributeAsConstantProperty &&
            this.generateEnumMemberName == b.generateEnumMemberName &&
            this.codeGenerationStrategy == b.codeGenerationStrategy &&
            this.serializable == b.serializable &&
            this.superClass == b.superClass &&
            this.superInterface == b.superInterface &&
            this.generateElementClass == b.generateElementClass &&
            this.generateMixedExtensions == b.generateMixedExtensions &&
            this.generateElementProperty == b.generateElementProperty &&
            this.choiceContentProperty == b.choiceContentProperty &&
            this.optionalProperty == b.optionalProperty &&
            this.defaultEnumMemberSizeCap == b.defaultEnumMemberSizeCap &&
            this.flattenClasses == b.flattenClasses;

        if (!equal) return false;

        return isEqual(this.nameConverter, b.nameConverter) &&
               isEqual(this.noMarshaller, b.noMarshaller) &&
               isEqual(this.noUnmarshaller, b.noUnmarshaller) &&
               isEqual(this.noValidator, b.noValidator) &&
               isEqual(this.noValidatingUnmarshaller, b.noValidatingUnmarshaller) &&
               isEqual(this.typeSubstitution, b.typeSubstitution) &&
               isEqual(this.simpleMode, b.simpleMode) &&
               isEqual(this.enumBaseTypes, b.enumBaseTypes) &&
               isEqual(this.treatRestrictionLikeNewType, b.treatRestrictionLikeNewType) &&
               isEqual(this.globalConversions, b.globalConversions);
    }

    private boolean isEqual(Object a, Object b) {
        if (a != null) {
            return a.equals(b);
        }
        return (b == null);
    }
}
