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
package com.sun.xml.internal.xsom.impl.util;

import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSListSimpleType;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.impl.Const;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor;
import com.sun.xml.internal.xsom.visitor.XSTermVisitor;
import com.sun.xml.internal.xsom.visitor.XSVisitor;

import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Iterator;

/**
 * Generates approximated XML Schema representation from
 * a schema component. This is not intended to be a fully-fledged
 * round-trippable schema writer.
 *
 * <h2>Usage of this class</h2>
 * <ol>
 *  <li>Create a new instance with whatever Writer
 *      you'd like to send the output to.
 *  <li>Call one of the overloaded dump methods.
 *      You can repeat this process as many times as you want.
 * </ol>
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @author Kirill Grouchnikov (kirillcool@yahoo.com)
 */
public class SchemaWriter implements XSVisitor, XSSimpleTypeVisitor {
    public SchemaWriter( Writer _out ) {
        this.out=_out;
    }

    /** output is sent to this object. */
    private final Writer out;

    /** indentation. */
    private int indent;

    private void println(String s) {
        try {
            for( int i=0; i<indent; i++)    out.write("  ");
            out.write(s);
            out.write('\n');
            // flush stream to make the output appear immediately
            out.flush();
        } catch( IOException e ) {
            // ignore IOException.
            hadError = true;
        }
    }
    private void println() { println(""); }

    /** If IOException is encountered, this flag is set to true. */
    private boolean hadError =false;

    /** Flush the stream and check its error state. */
    public boolean checkError() {
        try {
            out.flush();
        } catch( IOException e ) {
            hadError=true;
        }
        return hadError;
    }

    public void visit( XSSchemaSet s ) {
        Iterator itr =  s.iterateSchema();
        while(itr.hasNext()) {
            schema((XSSchema)itr.next());
            println();
        }
    }

    public void schema( XSSchema s ) {

        // QUICK HACK: don't print the built-in components
        if(s.getTargetNamespace().equals(Const.schemaNamespace))
            return;

        println(MessageFormat.format("<schema targetNamespace=\"{0}\">",
            new Object[]{
                s.getTargetNamespace(),
            }));
        indent++;

        Iterator itr;

        itr = s.iterateAttGroupDecls();
        while(itr.hasNext())
            attGroupDecl( (XSAttGroupDecl)itr.next() );

        itr = s.iterateAttributeDecls();
        while(itr.hasNext())
            attributeDecl( (XSAttributeDecl)itr.next() );

        itr = s.iterateComplexTypes();
        while(itr.hasNext())
            complexType( (XSComplexType)itr.next() );

        itr = s.iterateElementDecls();
        while(itr.hasNext())
            elementDecl( (XSElementDecl)itr.next() );

        itr = s.iterateModelGroupDecls();
        while(itr.hasNext())
            modelGroupDecl( (XSModelGroupDecl)itr.next() );

        itr = s.iterateSimpleTypes();
        while(itr.hasNext())
            simpleType( (XSSimpleType)itr.next() );

        indent--;
        println("</schema>");
    }

    public void attGroupDecl( XSAttGroupDecl decl ) {
        Iterator itr;

        println(MessageFormat.format("<attGroup name=\"{0}\">",
            new Object[]{ decl.getName() }));
        indent++;

        // TODO: wildcard

        itr = decl.iterateAttGroups();
        while(itr.hasNext())
            dumpRef( (XSAttGroupDecl)itr.next() );

        itr = decl.iterateDeclaredAttributeUses();
        while(itr.hasNext())
            attributeUse( (XSAttributeUse)itr.next() );

        indent--;
        println("</attGroup>");
    }

    public void dumpRef( XSAttGroupDecl decl ) {
        println(MessageFormat.format("<attGroup ref=\"'{'{0}'}'{1}\"/>",
            new Object[]{ decl.getTargetNamespace(), decl.getName() }));
    }

    public void attributeUse( XSAttributeUse use ) {
        XSAttributeDecl decl = use.getDecl();

        String additionalAtts="";

        if(use.isRequired())
            additionalAtts += " use=\"required\"";
        if(use.getFixedValue()!=null && use.getDecl().getFixedValue()==null)
            additionalAtts += " fixed=\""+use.getFixedValue()+'\"';
        if(use.getDefaultValue()!=null && use.getDecl().getDefaultValue()==null)
            additionalAtts += " default=\""+use.getDefaultValue()+'\"';

        if(decl.isLocal()) {
            // this is anonymous attribute use
            dump(decl,additionalAtts);
        } else {
            // reference to a global one
            println(MessageFormat.format("<attribute ref=\"'{'{0}'}'{1}{2}\"/>",
                new Object[]{ decl.getTargetNamespace(), decl.getName(),
                    additionalAtts }));
        }
    }

    public void attributeDecl( XSAttributeDecl decl ) {
        dump(decl,"");
    }

    private void dump( XSAttributeDecl decl, String additionalAtts ) {
        XSSimpleType type=decl.getType();

        println(MessageFormat.format("<attribute name=\"{0}\"{1}{2}{3}{4}{5}>",
            new Object[]{
                decl.getName(),
                additionalAtts,
                type.isLocal()?"":
                MessageFormat.format(" type=\"'{'{0}'}'{1}\"",
                new Object[]{
                    type.getTargetNamespace(),
                    type.getName()
                }),
                decl.getFixedValue()==null ?
                    "":" fixed=\""+decl.getFixedValue()+'\"',
                decl.getDefaultValue()==null ?
                    "":" default=\""+decl.getDefaultValue()+'\"',
                type.isLocal()?"":" /"
            }));

        if(type.isLocal()) {
            indent++;
            simpleType(type);
            indent--;
            println("</attribute>");
        }
    }

    public void simpleType( XSSimpleType type ) {
        println(MessageFormat.format("<simpleType{0}>",
            new Object[]{
                type.isLocal()?"":" name=\""+type.getName()+'\"'
            }));
        indent++;

        type.visit((XSSimpleTypeVisitor)this);

        indent--;
        println("</simpleType>");
    }

    public void listSimpleType( XSListSimpleType type ) {
        XSSimpleType itemType = type.getItemType();

        if(itemType.isLocal()) {
            println("<list>");
            indent++;
            simpleType(itemType);
            indent--;
            println("</list>");
        } else {
            // global type
            println(MessageFormat.format("<list itemType=\"'{'{0}'}'{1}\" />",
                new Object[]{
                    itemType.getTargetNamespace(),
                    itemType.getName()
                }));
        }
    }

    public void unionSimpleType( XSUnionSimpleType type ) {
        final int len = type.getMemberSize();
        StringBuffer ref = new StringBuffer();

        for( int i=0; i<len; i++ ) {
            XSSimpleType member = type.getMember(i);
            if(member.isGlobal())
                ref.append(MessageFormat.format(" '{'{0}'}'{1}",
                    new Object[]{member.getTargetNamespace(),member.getName()}));
        }

        if(ref.length()==0)
            println("<union>");
        else
            println("<union memberTypes=\""+ref+"\">");
        indent++;

        for( int i=0; i<len; i++ ) {
            XSSimpleType member = type.getMember(i);
            if(member.isLocal())
                simpleType(member);
        }
        indent--;
        println("</union>");
    }

    public void restrictionSimpleType( XSRestrictionSimpleType type ) {

        if(type.getBaseType()==null) {
            // don't print anySimpleType
            if(!type.getName().equals("anySimpleType"))
                throw new InternalError();
            if(!Const.schemaNamespace.equals(type.getTargetNamespace()))
                throw new InternalError();
            return;
        }

        XSSimpleType baseType = type.getSimpleBaseType();

        println(MessageFormat.format("<restriction{0}>",
            new Object[]{
                baseType.isLocal()?"":" base=\"{"+
                baseType.getTargetNamespace()+'}'+
                baseType.getName()+'\"'
            }));
        indent++;

        if(baseType.isLocal())
            simpleType(baseType);

        Iterator itr = type.iterateDeclaredFacets();
        while(itr.hasNext())
            facet( (XSFacet)itr.next() );

        indent--;
        println("</restriction>");
    }

    public void facet( XSFacet facet ) {
        println(MessageFormat.format("<{0} value=\"{1}\"/>",
            new Object[]{
                facet.getName(), facet.getValue(),
            }));
    }

    public void notation( XSNotation notation ) {
        println(MessageFormat.format("<notation name='\"0}\" public =\"{1}\" system=\"{2}\" />",
            new Object[] {
                notation.getName(),
                notation.getPublicId(),
                notation.getSystemId() } ));
    }



    public void complexType( XSComplexType type ) {
        println(MessageFormat.format("<complexType{0}>",
            new Object[]{
                type.isLocal()?"":" name=\""+type.getName()+'\"'
            }));
        indent++;

        // TODO: wildcard

        if(type.getContentType().asSimpleType()!=null) {
            // simple content
            println("<simpleContent>");
            indent++;

            XSType baseType = type.getBaseType();

            if(type.getDerivationMethod()==XSType.RESTRICTION) {
                // restriction
                println(MessageFormat.format("<restriction base=\"<{0}>{1}\">",
                    new Object[]{
                        baseType.getTargetNamespace(),
                        baseType.getName() }));
                indent++;

                dumpComplexTypeAttribute(type);

                indent--;
                println("</restriction>");
            } else {
                // extension
                println(MessageFormat.format("<extension base=\"<{0}>{1}\">",
                    new Object[]{
                        baseType.getTargetNamespace(),
                        baseType.getName() }));

                // check if have redefine tag - Kirill
                if( type.isGlobal()
                && type.getTargetNamespace().equals(baseType.getTargetNamespace())
                && type.getName().equals(baseType.getName())) {
                    indent++;
                    println("<redefine>");
                    indent++;
                    baseType.visit(this);
                    indent--;
                    println("</redefine>");
                    indent--;
                }

                indent++;

                dumpComplexTypeAttribute(type);

                indent--;
                println("</extension>");
            }

            indent--;
            println("</simpleContent>");
        } else {
            // complex content
            println("<complexContent>");
            indent++;

            XSComplexType baseType = type.getBaseType().asComplexType();

            if(type.getDerivationMethod()==XSType.RESTRICTION) {
                // restriction
                println(MessageFormat.format("<restriction base=\"'{'{0}'}'{1}\">",
                    new Object[]{
                        baseType.getTargetNamespace(),
                        baseType.getName() }));
                indent++;

                type.getContentType().visit(this);
                dumpComplexTypeAttribute(type);

                indent--;
                println("</restriction>");
            } else {
                // extension
                println(MessageFormat.format("<extension base=\"'{'{0}'}'{1}\">",
                    new Object[]{
                        baseType.getTargetNamespace(),
                        baseType.getName() }));

                // check if have redefine - Kirill
                if( type.isGlobal()
                && type.getTargetNamespace().equals(baseType.getTargetNamespace())
                && type.getName().equals(baseType.getName())) {
                    indent++;
                    println("<redefine>");
                    indent++;
                    baseType.visit(this);
                    indent--;
                    println("</redefine>");
                    indent--;
                }

                indent++;

                type.getExplicitContent().visit(this);
                dumpComplexTypeAttribute(type);

                indent--;
                println("</extension>");
            }

            indent--;
            println("</complexContent>");
        }

        indent--;
        println("</complexType>");
    }

    private void dumpComplexTypeAttribute( XSComplexType type ) {
        Iterator itr;

        itr = type.iterateAttGroups();
        while(itr.hasNext())
            dumpRef( (XSAttGroupDecl)itr.next() );

        itr = type.iterateDeclaredAttributeUses();
        while(itr.hasNext())
            attributeUse( (XSAttributeUse)itr.next() );
    }

    public void elementDecl( XSElementDecl decl ) {
        elementDecl(decl,"");
    }
    private void elementDecl( XSElementDecl decl, String extraAtts ) {
        XSType type = decl.getType();

        // TODO: various other attributes

        println(MessageFormat.format("<element name=\"{0}\"{1}{2}{3}>",
            new Object[]{
                decl.getName(),
                type.isLocal()?"":" type=\"{"+
                    type.getTargetNamespace()+'}'+
                    type.getName()+'\"',
                extraAtts,
                type.isLocal()?"":"/"
            }));

        if(type.isLocal()) {
            indent++;

            if(type.isLocal())  type.visit(this);

            indent--;
            println("</element>");
        }
    }

    public void modelGroupDecl( XSModelGroupDecl decl ) {
        println(MessageFormat.format("<group name=\"{0}\">",
            new Object[]{
                decl.getName()
            }));
        indent++;

        modelGroup(decl.getModelGroup());

        indent--;
        println("</group>");
    }

    public void modelGroup( XSModelGroup group ) {
        modelGroup(group,"");
    }
    private void modelGroup( XSModelGroup group, String extraAtts ) {
        println(MessageFormat.format("<{0}{1}>",
            new Object[]{ group.getCompositor(), extraAtts }));
        indent++;

        final int len = group.getSize();
        for( int i=0; i<len; i++ )
            particle(group.getChild(i));

        indent--;
        println(MessageFormat.format("</{0}>",
            new Object[]{ group.getCompositor() }));
    }

    public void particle( XSParticle part ) {
        int i;

        StringBuffer buf = new StringBuffer();

        i = part.getMaxOccurs();
        if(i==XSParticle.UNBOUNDED)
            buf.append(" maxOccurs=\"unbounded\"");
        else if(i!=1)
            buf.append(" maxOccurs=\""+i+'\"');

        i = part.getMinOccurs();
        if(i!=1)
            buf.append(" minOccurs=\""+i+'\"');

        final String extraAtts = buf.toString();

        part.getTerm().visit(new XSTermVisitor(){
            public void elementDecl( XSElementDecl decl ) {
                if(decl.isLocal())
                    SchemaWriter.this.elementDecl(decl,extraAtts);
                else {
                    // reference
                    println(MessageFormat.format("<element ref=\"'{'{0}'}'{1}\"{2}/>",
                        new Object[]{
                            decl.getTargetNamespace(),
                            decl.getName(),
                            extraAtts
                        }));
                }
            }
            public void modelGroupDecl( XSModelGroupDecl decl ) {
                // reference
                println(MessageFormat.format("<group ref=\"'{'{0}'}'{1}\"{2}/>",
                    new Object[]{
                        decl.getTargetNamespace(),
                        decl.getName(),
                        extraAtts
                    }));
            }
            public void modelGroup( XSModelGroup group ) {
                SchemaWriter.this.modelGroup(group,extraAtts);
            }
            public void wildcard( XSWildcard wc ) {
                SchemaWriter.this.wildcard(wc,extraAtts);
            }
        });
    }

    public void wildcard( XSWildcard wc ) {
        wildcard(wc,"");
    }

    private void wildcard( XSWildcard wc, String extraAtts ) {
        // TODO
        println(MessageFormat.format("<any/>", new Object[]{extraAtts}));
    }

    public void annotation( XSAnnotation ann ) {
        // TODO: it would be nice even if we just put <xs:documentation>
    }

    public void identityConstraint(XSIdentityConstraint decl) {
        // TODO
    }

    public void xpath(XSXPath xp) {
        // TODO
    }

    public void empty( XSContentType t ) {}
}
