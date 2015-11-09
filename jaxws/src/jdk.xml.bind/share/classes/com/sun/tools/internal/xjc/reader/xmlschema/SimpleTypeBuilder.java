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

package com.sun.tools.internal.xjc.reader.xmlschema;

import java.io.StringWriter;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.activation.MimeTypeParseException;
import javax.xml.bind.DatatypeConverter;

import com.sun.codemodel.internal.JJavaName;
import com.sun.codemodel.internal.util.JavadocEscapeWriter;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CClassInfoParent;
import com.sun.tools.internal.xjc.model.CClassRef;
import com.sun.tools.internal.xjc.model.CEnumConstant;
import com.sun.tools.internal.xjc.model.CEnumLeafInfo;
import com.sun.tools.internal.xjc.model.CNonElement;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.model.TypeUseFactory;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIConversion;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIEnum;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIEnumMember;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.EnumMemberMode;
import com.sun.tools.internal.xjc.util.MimeTypeRange;

import static com.sun.xml.internal.bind.v2.WellKnownNamespace.XML_MIME_URI;

import com.sun.xml.internal.bind.v2.runtime.SwaRefAdapterMarker;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSListSimpleType;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSVariety;
import com.sun.xml.internal.xsom.impl.util.SchemaWriter;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeFunction;
import com.sun.xml.internal.xsom.visitor.XSVisitor;

import org.xml.sax.Locator;

/**
 * Builds {@link TypeUse} from simple types.
 *
 * <p>
 * This code consists of two main portions. The {@link #compose(XSSimpleType)} method
 * and {@link #composer} forms an outer cycle, which gradually ascends the type
 * inheritance chain until it finds the suitable binding. When it does this
 * {@link #initiatingType} is set to the type which started binding, so that we can refer
 * to the actual constraint facets and such that are applicable on the type.
 *
 * <p>
 * For each intermediate type in the chain, the {@link #find(XSSimpleType)} method
 * is used to find the binding on that type, sine the outer loop is doing the ascending,
 * this method only sees if the current type has some binding available.
 *
 * <p>
 * There is at least one ugly code that you need to aware of
 * when you are modifying the code. See the documentation
 * about <a href="package.html#stref_cust">
 * "simple type customization at the point of reference."</a>
 *
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class SimpleTypeBuilder extends BindingComponent {

    protected final BGMBuilder builder = Ring.get(BGMBuilder.class);

    private final Model model = Ring.get(Model.class);

    /**
     * The component that is refering to the simple type
     * which we are building. This is ugly but necessary
     * to support the customization of simple types at
     * its point of reference. See my comment at the header
     * of this class for details.
     *
     * UGLY: Implemented as a Stack of XSComponent to fix a bug
     */
    public final Stack<XSComponent> refererStack = new Stack<XSComponent>();

    /**
     * Records what xmime:expectedContentTypes annotations we honored and processed,
     * so that we can later check if the user had these annotations in the places
     * where we didn't anticipate them.
     */
    private final Set<XSComponent> acknowledgedXmimeContentTypes = new HashSet<XSComponent>();

    /**
     * The type that was originally passed to this {@link SimpleTypeBuilder#build(XSSimpleType)}.
     * Never null.
     */
    private XSSimpleType initiatingType;

    /** {@link TypeUse}s for the built-in types. Read-only. */
    public static final Map<String,TypeUse> builtinConversions;


    /**
     * Entry point from outside. Builds a BGM type expression
     * from a simple type schema component.
     *
     * @param type
     *      the simple type to be bound.
     */
    public TypeUse build( XSSimpleType type ) {
        XSSimpleType oldi = initiatingType;
        this.initiatingType = type;

        TypeUse e = checkRefererCustomization(type);
        if(e==null)
            e = compose(type);

        initiatingType = oldi;

        return e;
    }

    /**
     * A version of the {@link #build(XSSimpleType)} method
     * used to bind the definition of a class generated from
     * the given simple type.
     */
    public TypeUse buildDef( XSSimpleType type ) {
        XSSimpleType oldi = initiatingType;
        this.initiatingType = type;

        TypeUse e = type.apply(composer);

        initiatingType = oldi;

        return e;
    }


    /**
     * Returns a javaType customization specified to the referer, if present.
     * @return can be null.
     */
    private BIConversion getRefererCustomization() {
        BindInfo info = builder.getBindInfo(getReferer());
        BIProperty prop = info.get(BIProperty.class);
        if(prop==null)  return null;
        return prop.getConv();
    }

    public XSComponent getReferer() {
        return refererStack.peek();
    }

    /**
     * Checks if the referer has a conversion customization or not.
     * If it does, use it to bind this simple type. Otherwise
     * return null;
     */
    private TypeUse checkRefererCustomization( XSSimpleType type ) {

        // assertion check. referer must be set properly
        // before the build method is called.
        // since the handling of the simple type point-of-reference
        // customization is very error prone, it deserves a strict
        // assertion check.
        // UGLY CODE WARNING
        XSComponent top = getReferer();

        if( top instanceof XSElementDecl ) {
            // if the parent is element type, its content type must be us.
            XSElementDecl eref = (XSElementDecl)top;
            assert eref.getType()==type;

            // for elements, you can't use <property>,
            // so we allow javaType to appear directly.
            BindInfo info = builder.getBindInfo(top);
            BIConversion conv = info.get(BIConversion.class);
            if(conv!=null) {
                conv.markAsAcknowledged();
                // the conversion is given.
                return conv.getTypeUse(type);
            }
            detectJavaTypeCustomization();
        } else
        if( top instanceof XSAttributeDecl ) {
            XSAttributeDecl aref = (XSAttributeDecl)top;
            assert aref.getType()==type;
            detectJavaTypeCustomization();
        } else
        if( top instanceof XSComplexType ) {
            XSComplexType tref = (XSComplexType)top;
            assert tref.getBaseType()==type || tref.getContentType()==type;
            detectJavaTypeCustomization();
        } else
        if( top == type ) {
            // this means the simple type is built by itself and
            // not because it's referenced by something.
        } else
            // unexpected referer type.
            assert false;

        // now we are certain that the referer is OK.
        // see if it has a conversion customization.
        BIConversion conv = getRefererCustomization();
        if(conv!=null) {
            conv.markAsAcknowledged();
            // the conversion is given.
            return conv.getTypeUse(type);
        } else
            // not found
            return null;
    }

    /**
     * Detect "javaType" customizations placed directly on simple types, rather
     * than being enclosed by "property" and "baseType" customizations (see
     * sec 6.8.1 of the spec).
     *
     * Report an error if any exist.
     */
    private void detectJavaTypeCustomization() {
        BindInfo info = builder.getBindInfo(getReferer());
        BIConversion conv = info.get(BIConversion.class);

        if( conv != null ) {
            // ack this conversion to prevent further error messages
            conv.markAsAcknowledged();

            // report the error
            getErrorReporter().error( conv.getLocation(),
                    Messages.ERR_UNNESTED_JAVATYPE_CUSTOMIZATION_ON_SIMPLETYPE );
        }
    }

    /**
     * Recursively decend the type inheritance chain to find a binding.
     */
    TypeUse compose( XSSimpleType t ) {
        TypeUse e = find(t);
        if(e!=null)     return e;
        return t.apply(composer);
    }

    public final XSSimpleTypeFunction<TypeUse> composer = new XSSimpleTypeFunction<TypeUse>() {

        public TypeUse listSimpleType(XSListSimpleType type) {
            // bind item type individually and then compose them into a list
            // facets on the list shouldn't be taken account when binding item types,
            // so weed to call build(), not compose().
            XSSimpleType itemType = type.getItemType();
            refererStack.push(itemType);
            TypeUse tu = TypeUseFactory.makeCollection(build(type.getItemType()));
            refererStack.pop();
            return tu;
        }

        public TypeUse unionSimpleType(XSUnionSimpleType type) {
            boolean isCollection = false;
            for( int i=0; i<type.getMemberSize(); i++ )
                if(type.getMember(i).getVariety()==XSVariety.LIST || type.getMember(i).getVariety()==XSVariety.UNION) {
                    isCollection = true;
                    break;
                }

            TypeUse r = CBuiltinLeafInfo.STRING;
            if(isCollection)
                r = TypeUseFactory.makeCollection(r);
            return r;
        }

        public TypeUse restrictionSimpleType(XSRestrictionSimpleType type) {
            // just process the base type.
            return compose(type.getSimpleBaseType());
        }
    };


    /**
     * Checks if there's any binding available on the given type.
     *
     * @return
     *      null if not (which causes the {@link #compose(XSSimpleType)} method
     *      to do ascending.
     */
    private TypeUse find( XSSimpleType type ) {
        TypeUse r;
        boolean noAutoEnum = false;

        // check for user specified conversion
        BindInfo info = builder.getBindInfo(type);
        BIConversion conv = info.get(BIConversion.class);

        if( conv!=null ) {
            // a conversion was found
            conv.markAsAcknowledged();
            return conv.getTypeUse(type);
        }

        // look for enum customization, which is another user specified conversion
        BIEnum en = info.get(BIEnum.class);
        if( en!=null ) {
            en.markAsAcknowledged();

            if(!en.isMapped()) {
                noAutoEnum = true;
            } else {
                // if an enum customization is specified, make sure
                // the type is OK
                if( !canBeMappedToTypeSafeEnum(type) ) {
                    getErrorReporter().error( en.getLocation(),
                        Messages.ERR_CANNOT_BE_TYPE_SAFE_ENUM );
                    getErrorReporter().error( type.getLocator(),
                        Messages.ERR_CANNOT_BE_TYPE_SAFE_ENUM_LOCATION );
                    // recover by ignoring this customization
                    return null;
                }

                // reference?
                if(en.ref!=null) {
                    if(!JJavaName.isFullyQualifiedClassName(en.ref)) {
                        Ring.get(ErrorReceiver.class).error( en.getLocation(),
                            Messages.format(Messages.ERR_INCORRECT_CLASS_NAME, en.ref) );
                        // recover by ignoring @ref
                        return null;
                    }

                    return new CClassRef(model, type, en, info.toCustomizationList() );
                }

                // list and union cannot be mapped to a type-safe enum,
                // so in this stage we can safely cast it to XSRestrictionSimpleType
                return bindToTypeSafeEnum( (XSRestrictionSimpleType)type,
                        en.className, en.javadoc, en.members,
                        getEnumMemberMode().getModeWithEnum(),
                        en.getLocation() );
            }
        }


        // if the type is built in, look for the default binding
        if(type.getTargetNamespace().equals(WellKnownNamespace.XML_SCHEMA)) {
            String name = type.getName();
            if(name!=null) {
                r = lookupBuiltin(name);
                if(r!=null)
                    return r;
            }
        }

        // also check for swaRef
        if(type.getTargetNamespace().equals(WellKnownNamespace.SWA_URI)) {
            String name = type.getName();
            if(name!=null && name.equals("swaRef"))
                return CBuiltinLeafInfo.STRING.makeAdapted(SwaRefAdapterMarker.class,false);
        }


        // see if this type should be mapped to a type-safe enumeration by default.
        // if so, built a EnumXDucer from it and return it.
        if(type.isRestriction() && !noAutoEnum) {
            XSRestrictionSimpleType rst = type.asRestriction();
            if(shouldBeMappedToTypeSafeEnumByDefault(rst)) {
                r = bindToTypeSafeEnum(rst,null,null, Collections.<String, BIEnumMember>emptyMap(),
                            getEnumMemberMode(),null);
                if(r!=null)
                    return r;
            }
        }

        return (CNonElement)getClassSelector()._bindToClass(type,null,false);
    }

    private static Set<XSRestrictionSimpleType> reportedEnumMemberSizeWarnings;

    /**
     * Returns true if a type-safe enum should be created from
     * the given simple type by default without an explicit {@code <jaxb:enum>} customization.
     */
    private boolean shouldBeMappedToTypeSafeEnumByDefault( XSRestrictionSimpleType type ) {

        // if not, there will be a problem wrt the class name of this type safe enum type.
        if( type.isLocal() )    return false;

        // if redefined, we should map the new definition, not the old one.
        if( type.getRedefinedBy()!=null )   return false;

        List<XSFacet> facets = type.getDeclaredFacets(XSFacet.FACET_ENUMERATION);
        if( facets.isEmpty() )
            // if the type itself doesn't have the enumeration facet,
            // it won't be mapped to a type-safe enum.
            return false;

        if(facets.size() > builder.getGlobalBinding().getDefaultEnumMemberSizeCap()) {
            // if there are too many facets, it's not very useful
            // produce warning when simple type is not mapped to enum
            // see issue https://jaxb.dev.java.net/issues/show_bug.cgi?id=711

            if(reportedEnumMemberSizeWarnings == null)
                reportedEnumMemberSizeWarnings = new HashSet<XSRestrictionSimpleType>();

            if(!reportedEnumMemberSizeWarnings.contains(type)) {
                getErrorReporter().warning(type.getLocator(), Messages.WARN_ENUM_MEMBER_SIZE_CAP,
                        type.getName(), facets.size(), builder.getGlobalBinding().getDefaultEnumMemberSizeCap());

                reportedEnumMemberSizeWarnings.add(type);
            }

            return false;
        }

        if( !canBeMappedToTypeSafeEnum(type) )
            // we simply can't map this to an enumeration
            return false;

        // check for collisions among constant names. if a collision will happen,
        // don't try to bind it to an enum.

        // return true only when this type is derived from one of the "enum base type".
        for( XSSimpleType t = type; t!=null; t=t.getSimpleBaseType() )
            if( t.isGlobal() && builder.getGlobalBinding().canBeMappedToTypeSafeEnum(t) )
                return true;

        return false;
    }


    private static final Set<String> builtinTypeSafeEnumCapableTypes;

    static {
        Set<String> s = new HashSet<String>();

        // see a bullet of 6.5.1 of the spec.
        String[] typeNames = new String[] {
            "string", "boolean", "float", "decimal", "double", "anyURI"
        };
        s.addAll(Arrays.asList(typeNames));

        builtinTypeSafeEnumCapableTypes = Collections.unmodifiableSet(s);
    }


    /**
     * Returns true if the given simple type can be mapped to a
     * type-safe enum class.
     *
     * <p>
     * JAXB spec places a restrictrion as to what type can be
     * mapped to a type-safe enum. This method enforces this
     * constraint.
     */
    public static boolean canBeMappedToTypeSafeEnum( XSSimpleType type ) {
        do {
            if( WellKnownNamespace.XML_SCHEMA.equals(type.getTargetNamespace()) ) {
                // type must be derived from one of these types
                String localName = type.getName();
                if( localName!=null ) {
                    if( localName.equals("anySimpleType") )
                        return false;   // catch all case
                    if( localName.equals("ID") || localName.equals("IDREF") )
                        return false;   // not ID/IDREF

                    // other allowed list
                    if( builtinTypeSafeEnumCapableTypes.contains(localName) )
                        return true;
                }
            }

            type = type.getSimpleBaseType();
        } while( type!=null );

        return false;
    }



    /**
     * Builds a type-safe enum conversion from a simple type
     * with enumeration facets.
     *
     * @param className
     *      The class name of the type-safe enum. Or null to
     *      create a default name.
     * @param javadoc
     *      Additional javadoc that will be added at the beginning of the
     *      class, or null if none is necessary.
     * @param members
     *      A map from enumeration values (as String) to BIEnumMember objects.
     *      if some of the value names need to be overrided.
     *      Cannot be null, but the map may not contain entries
     *      for all enumeration values.
     * @param loc
     *      The source location where the above customizations are
     *      specified, or null if none is available.
     */
    private TypeUse bindToTypeSafeEnum( XSRestrictionSimpleType type,
                                        String className, String javadoc, Map<String,BIEnumMember> members,
                                        EnumMemberMode mode, Locator loc ) {

        if( loc==null )  // use the location of the simple type as the default
            loc = type.getLocator();

        if( className==null ) {
            // infer the class name. For this to be possible,
            // the simple type must be a global one.
            if( !type.isGlobal() ) {
                getErrorReporter().error( loc, Messages.ERR_NO_ENUM_NAME_AVAILABLE );
                // recover by returning a meaningless conversion
                return CBuiltinLeafInfo.STRING;
            }
            className = type.getName();
        }

        // we apply name conversion in any case
        className = builder.deriveName(className,type);

        {// compute Javadoc
            StringWriter out = new StringWriter();
            SchemaWriter sw = new SchemaWriter(new JavadocEscapeWriter(out));
            type.visit((XSVisitor)sw);

            if(javadoc!=null)   javadoc += "\n\n";
            else                javadoc = "";

            javadoc += Messages.format( Messages.JAVADOC_HEADING, type.getName() )
                +"\n<p>\n<pre>\n"+out.getBuffer()+"</pre>";

        }

        // build base type
        refererStack.push(type.getSimpleBaseType());
        TypeUse use = build(type.getSimpleBaseType());
        refererStack.pop();

        if(use.isCollection())
            return null;    // can't bind a list to enum constant

        CNonElement baseDt = use.getInfo();   // for now just ignore that case

        if(baseDt instanceof CClassInfo)
            return null;    // can't bind to an enum if the base is a class, since we don't have the value constrctor

        // if the member names collide, re-generate numbered constant names.
        XSFacet[] errorRef = new XSFacet[1];
        List<CEnumConstant> memberList = buildCEnumConstants(type, false, members, errorRef);
        if(memberList==null || checkMemberNameCollision(memberList)!=null) {
            switch(mode) {
            case SKIP:
                // abort
                return null;
            case ERROR:
                // error
                if(memberList==null) {
                    getErrorReporter().error( errorRef[0].getLocator(),
                        Messages.ERR_CANNOT_GENERATE_ENUM_NAME,
                        errorRef[0].getValue() );
                } else {
                    CEnumConstant[] collision = checkMemberNameCollision(memberList);
                    getErrorReporter().error( collision[0].getLocator(),
                        Messages.ERR_ENUM_MEMBER_NAME_COLLISION,
                        collision[0].getName() );
                    getErrorReporter().error( collision[1].getLocator(),
                        Messages.ERR_ENUM_MEMBER_NAME_COLLISION_RELATED );
                }
                return null;    // recover from error
            case GENERATE:
                // generate
                memberList = buildCEnumConstants(type,true,members,null);
                break;
            }
        }
        if(memberList.isEmpty()) {
            getErrorReporter().error( loc, Messages.ERR_NO_ENUM_FACET );
            return null;
        }

        // use the name of the simple type as the name of the class.
        CClassInfoParent scope;
        if(type.isGlobal())
            scope = new CClassInfoParent.Package(getClassSelector().getPackage(type.getTargetNamespace()));
        else
            scope = getClassSelector().getClassScope();
        CEnumLeafInfo xducer = new CEnumLeafInfo( model, BGMBuilder.getName(type), scope,
            className, baseDt, memberList, type,
            builder.getBindInfo(type).toCustomizationList(), loc );
        xducer.javadoc = javadoc;

        BIConversion conv = new BIConversion.Static( type.getLocator(),xducer);
        conv.markAsAcknowledged();

        // attach this new conversion object to this simple type
        // so that successive look up will use the same object.
        builder.getOrCreateBindInfo(type).addDecl(conv);

        return conv.getTypeUse(type);
    }

    /**
     *
     * @param errorRef
     *      if constant names couldn't be generated, return a reference to that enum facet.
     * @return
     *      null if unable to generate names for some of the constants.
     */
    private List<CEnumConstant> buildCEnumConstants(XSRestrictionSimpleType type, boolean needsToGenerateMemberName, Map<String, BIEnumMember> members, XSFacet[] errorRef) {
        List<CEnumConstant> memberList = new ArrayList<CEnumConstant>();
        int idx=1;
        Set<String> enums = new HashSet<String>(); // to avoid duplicates. See issue #366

        for( XSFacet facet : type.getDeclaredFacets(XSFacet.FACET_ENUMERATION)) {
            String name=null;
            String mdoc=builder.getBindInfo(facet).getDocumentation();

            if(!enums.add(facet.getValue().value))
                continue;   // ignore the 2nd occasion

            if( needsToGenerateMemberName ) {
                // generate names for all member names.
                // this will even override names specified by the user. that's crazy.
                name = "VALUE_"+(idx++);
            } else {
                String facetValue = facet.getValue().value;
                BIEnumMember mem = members.get(facetValue);
                if( mem==null )
                    // look at the one attached to the facet object
                    mem = builder.getBindInfo(facet).get(BIEnumMember.class);

                if (mem!=null) {
                    name = mem.name;
                    if (mdoc == null) {
                        mdoc = mem.javadoc;
                    }
                }

                if(name==null) {
                    StringBuilder sb = new StringBuilder();
                    for( int i=0; i<facetValue.length(); i++) {
                        char ch = facetValue.charAt(i);
                        if(Character.isJavaIdentifierPart(ch))
                            sb.append(ch);
                        else
                            sb.append('_');
                    }
                    name = model.getNameConverter().toConstantName(sb.toString());
                }
            }

            if(!JJavaName.isJavaIdentifier(name)) {
                if(errorRef!=null)  errorRef[0] = facet;
                return null;    // unable to generate a name
            }

            memberList.add(new CEnumConstant(name,mdoc,facet.getValue().value,facet,builder.getBindInfo(facet).toCustomizationList(),facet.getLocator()));
        }
        return memberList;
    }

    /**
     * Returns non-null if {@link CEnumConstant}s have name collisions among them.
     *
     * @return
     *      if there's a collision, return two {@link CEnumConstant}s that collided.
     *      otherwise return null.
     */
    private CEnumConstant[] checkMemberNameCollision( List<CEnumConstant> memberList ) {
        Map<String,CEnumConstant> names = new HashMap<String,CEnumConstant>();
        for (CEnumConstant c : memberList) {
            CEnumConstant old = names.put(c.getName(),c);
            if(old!=null)
                // collision detected
                return new CEnumConstant[]{old,c};
        }
        return null;
    }



    private EnumMemberMode getEnumMemberMode() {
        return builder.getGlobalBinding().getEnumMemberMode();
    }

    private TypeUse lookupBuiltin( String typeLocalName ) {
        if(typeLocalName.equals("integer") || typeLocalName.equals("long")) {
            /*
                attempt an optimization so that we can
                improve the binding for types like this:

                <simpleType>
                  <restriciton baseType="integer">
                    <maxInclusive value="100" />
                  </
                </

                ... to int, not BigInteger.
            */

            BigInteger xe = readFacet(XSFacet.FACET_MAXEXCLUSIVE,-1);
            BigInteger xi = readFacet(XSFacet.FACET_MAXINCLUSIVE,0);
            BigInteger max = min(xe,xi);    // most restrictive one takes precedence

            if(max!=null) {
                BigInteger ne = readFacet(XSFacet.FACET_MINEXCLUSIVE,+1);
                BigInteger ni = readFacet(XSFacet.FACET_MININCLUSIVE,0);
                BigInteger min = max(ne,ni);

                if(min!=null) {
                    if(min.compareTo(INT_MIN )>=0 && max.compareTo(INT_MAX )<=0)
                        typeLocalName = "int";
                    else
                    if(min.compareTo(LONG_MIN)>=0 && max.compareTo(LONG_MAX)<=0)
                        typeLocalName = "long";
                }
            }
        } else
        if(typeLocalName.equals("boolean") && isRestrictedTo0And1()) {
            // this is seen in the SOAP schema and too common to ignore
            return CBuiltinLeafInfo.BOOLEAN_ZERO_OR_ONE;
        } else
        if(typeLocalName.equals("base64Binary")) {
            return lookupBinaryTypeBinding();
        } else
        if(typeLocalName.equals("anySimpleType")) {
            if(getReferer() instanceof XSAttributeDecl || getReferer() instanceof XSSimpleType)
                return CBuiltinLeafInfo.STRING;
            else
                return CBuiltinLeafInfo.ANYTYPE;
        }
        return builtinConversions.get(typeLocalName);
    }

    /**
     * Decides the way xs:base64Binary binds.
     *
     * This method checks the expected media type.
     */
    private TypeUse lookupBinaryTypeBinding() {
        XSComponent referer = getReferer();
        String emt = referer.getForeignAttribute(XML_MIME_URI, Const.EXPECTED_CONTENT_TYPES);
        if(emt!=null) {
            acknowledgedXmimeContentTypes.add(referer);
            try {
                // see http://www.xml.com/lpt/a/2004/07/21/dive.html
                List<MimeTypeRange> types = MimeTypeRange.parseRanges(emt);
                MimeTypeRange mt = MimeTypeRange.merge(types);

                // see spec table I-1 in appendix I section 2.1.1 for bindings
                if(mt.majorType.equalsIgnoreCase("image"))
                    return CBuiltinLeafInfo.IMAGE.makeMimeTyped(mt.toMimeType());

                if(( mt.majorType.equalsIgnoreCase("application") || mt.majorType.equalsIgnoreCase("text"))
                        && isXml(mt.subType))
                    return CBuiltinLeafInfo.XML_SOURCE.makeMimeTyped(mt.toMimeType());

                if((mt.majorType.equalsIgnoreCase("text") && (mt.subType.equalsIgnoreCase("plain")) )) {
                    return CBuiltinLeafInfo.STRING.makeMimeTyped(mt.toMimeType());
                }

                return CBuiltinLeafInfo.DATA_HANDLER.makeMimeTyped(mt.toMimeType());
            } catch (ParseException e) {
                getErrorReporter().error( referer.getLocator(),
                    Messages.format(Messages.ERR_ILLEGAL_EXPECTED_MIME_TYPE,emt, e.getMessage()) );
                // recover by using the default
            } catch (MimeTypeParseException e) {
                getErrorReporter().error( referer.getLocator(),
                    Messages.format(Messages.ERR_ILLEGAL_EXPECTED_MIME_TYPE,emt, e.getMessage()) );
            }
        }
        // default
        return CBuiltinLeafInfo.BASE64_BYTE_ARRAY;
    }

    public boolean isAcknowledgedXmimeContentTypes(XSComponent c) {
        return acknowledgedXmimeContentTypes.contains(c);
    }

    /**
     * Returns true if the specified sub-type is an XML type.
     */
    private boolean isXml(String subType) {
        return subType.equals("xml") || subType.endsWith("+xml");
    }

    /**
     * Returns true if the {@link #initiatingType} is restricted
     * to '0' and '1'. This logic is not complete, but it at least
     * finds the such definition in SOAP @mustUnderstand.
     */
    private boolean isRestrictedTo0And1() {
        XSFacet pattern = initiatingType.getFacet(XSFacet.FACET_PATTERN);
        if(pattern!=null) {
            String v = pattern.getValue().value;
            if(v.equals("0|1") || v.equals("1|0") || v.equals("\\d"))
                return true;
        }
        XSFacet enumf = initiatingType.getFacet(XSFacet.FACET_ENUMERATION);
        if(enumf!=null) {
            String v = enumf.getValue().value;
            if(v.equals("0") || v.equals("1"))
                return true;
        }
        return false;
    }

    private BigInteger readFacet(String facetName,int offset) {
        XSFacet me = initiatingType.getFacet(facetName);
        if(me==null)
            return null;
        BigInteger bi = DatatypeConverter.parseInteger(me.getValue().value);
        if(offset!=0)
            bi = bi.add(BigInteger.valueOf(offset));
        return bi;
    }

    private BigInteger min(BigInteger a, BigInteger b) {
        if(a==null) return b;
        if(b==null) return a;
        return a.min(b);
    }

    private BigInteger max(BigInteger a, BigInteger b) {
        if(a==null) return b;
        if(b==null) return a;
        return a.max(b);
    }

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    static {
        // list of datatypes which have built-in conversions.
        // note that although xs:token and xs:normalizedString are not
        // specified in the spec, they need to be here because they
        // have different whitespace normalization semantics.
        Map<String,TypeUse> m = new HashMap<String,TypeUse>();

        // TODO: this is so dumb
        m.put("string",         CBuiltinLeafInfo.STRING);
        m.put("anyURI",         CBuiltinLeafInfo.STRING);
        m.put("boolean",        CBuiltinLeafInfo.BOOLEAN);
        // we'll also look at the expected media type, so don't just add this to the map
        // m.put("base64Binary",   CBuiltinLeafInfo.BASE64_BYTE_ARRAY);
        m.put("hexBinary",      CBuiltinLeafInfo.HEXBIN_BYTE_ARRAY);
        m.put("float",          CBuiltinLeafInfo.FLOAT);
        m.put("decimal",        CBuiltinLeafInfo.BIG_DECIMAL);
        m.put("integer",        CBuiltinLeafInfo.BIG_INTEGER);
        m.put("long",           CBuiltinLeafInfo.LONG);
        m.put("unsignedInt",    CBuiltinLeafInfo.LONG);
        m.put("int",            CBuiltinLeafInfo.INT);
        m.put("unsignedShort",  CBuiltinLeafInfo.INT);
        m.put("short",          CBuiltinLeafInfo.SHORT);
        m.put("unsignedByte",   CBuiltinLeafInfo.SHORT);
        m.put("byte",           CBuiltinLeafInfo.BYTE);
        m.put("double",         CBuiltinLeafInfo.DOUBLE);
        m.put("QName",          CBuiltinLeafInfo.QNAME);
        m.put("NOTATION",       CBuiltinLeafInfo.QNAME);
        m.put("dateTime",       CBuiltinLeafInfo.CALENDAR);
        m.put("date",           CBuiltinLeafInfo.CALENDAR);
        m.put("time",           CBuiltinLeafInfo.CALENDAR);
        m.put("gYearMonth",     CBuiltinLeafInfo.CALENDAR);
        m.put("gYear",          CBuiltinLeafInfo.CALENDAR);
        m.put("gMonthDay",      CBuiltinLeafInfo.CALENDAR);
        m.put("gDay",           CBuiltinLeafInfo.CALENDAR);
        m.put("gMonth",         CBuiltinLeafInfo.CALENDAR);
        m.put("duration",       CBuiltinLeafInfo.DURATION);
        m.put("token",          CBuiltinLeafInfo.TOKEN);
        m.put("normalizedString",CBuiltinLeafInfo.NORMALIZED_STRING);
        m.put("ID",             CBuiltinLeafInfo.ID);
        m.put("IDREF",          CBuiltinLeafInfo.IDREF);

        builtinConversions = Collections.unmodifiableMap(m);
        // TODO: handling dateTime, time, and date type
//        String[] names = {
//            "date", "dateTime", "time", "hexBinary" };
    }
}
