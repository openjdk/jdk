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
package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JPackage;
import com.sun.codemodel.internal.JPrimitiveType;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.xjc.model.CAdapter;
import com.sun.tools.internal.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.model.TypeUseFactory;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * &lt;conversion> declaration in the binding file.
 * This declaration declares a conversion by user-specified methods.
 */
public class BIUserConversion implements BIConversion
{
    /**
     * Wraps a given &lt;conversion> element in the binding file.
     */
    BIUserConversion( BindInfo bi, Element _e ) {
        this.owner = bi;
        this.e = _e;
    }

    private static void add( Map<String,BIConversion> m, BIConversion c ) {
        m.put( c.name(), c );
    }

    /** Adds all built-in conversions into the given map. */
    static void addBuiltinConversions( BindInfo bi, Map<String,BIConversion> m ) {
        add( m, new BIUserConversion( bi, parse("<conversion name='boolean' type='java.lang.Boolean' parse='getBoolean' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='byte' type='java.lang.Byte' parse='parseByte' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='short' type='java.lang.Short' parse='parseShort' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='int' type='java.lang.Integer' parse='parseInt' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='long' type='java.lang.Long' parse='parseLong' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='float' type='java.lang.Float' parse='parseFloat' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='double' type='java.lang.Double' parse='parseDouble' />")));
    }

    private static Element parse(String text) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            InputSource is = new InputSource(new StringReader(text));
            return dbf.newDocumentBuilder().parse(is).getDocumentElement();
        } catch (SAXException x) {
            throw new Error(x);
        } catch (IOException x) {
            throw new Error(x);
        } catch (ParserConfigurationException x) {
            throw new Error(x);
        }
    }


    /** The owner {@link BindInfo} object to which this object belongs. */
    private final BindInfo owner;

    /** &lt;conversion> element which this object is wrapping. */
    private final Element e;



    /** Gets the location where this declaration is declared. */
    public Locator getSourceLocation() {
        return DOMLocator.getLocationInfo(e);
    }

    /** Gets the conversion name. */
    public String name() { return DOMUtil.getAttribute(e,"name"); }

    /** Gets a transducer for this conversion. */
    public TypeUse getTransducer() {

        String ws = DOMUtil.getAttribute(e,"whitespace");
        if(ws==null)    ws = "collapse";

        String type = DOMUtil.getAttribute(e,"type");
        if(type==null)  type=name();
        JType t=null;

        int idx = type.lastIndexOf('.');
        if(idx<0) {
            // no package name is specified.
            try {
                t = JPrimitiveType.parse(owner.codeModel,type);
            } catch( IllegalArgumentException e ) {
                // otherwise treat it as a class name in the current package
                type = owner.getTargetPackage().name()+'.'+type;
            }
        }
        if(t==null) {
            try {
                // TODO: revisit this later
                JDefinedClass cls = owner.codeModel._class(type);
                cls.hide();
                t = cls;
            } catch( JClassAlreadyExistsException e ) {
                t = e.getExistingClass();
            }
        }

        String parse = DOMUtil.getAttribute(e,"parse");
        if(parse==null)  parse="new";

        String print = DOMUtil.getAttribute(e,"print");
        if(print==null)  print="toString";

        JDefinedClass adapter = generateAdapter(owner.codeModel, parse, print, t.boxify());

        // XmlJavaType customization always converts between string and an user-defined type.
        return TypeUseFactory.adapt(CBuiltinLeafInfo.STRING,new CAdapter(adapter));
    }

    // TODO: anyway to reuse this code between XML Schema compiler?
    private JDefinedClass generateAdapter(JCodeModel cm, String parseMethod, String printMethod, JClass inMemoryType) {
        JDefinedClass adapter = null;

        int id = 1;
        while(adapter==null) {
            try {
                JPackage pkg = owner.getTargetPackage();
                adapter = pkg._class("Adapter"+id);
            } catch (JClassAlreadyExistsException e) {
                // try another name in search for an unique name.
                // this isn't too efficient, but we expect people to usually use
                // a very small number of adapters.
                id++;
            }
        }

        adapter._extends(cm.ref(XmlAdapter.class).narrow(String.class).narrow(inMemoryType));

        JMethod unmarshal = adapter.method(JMod.PUBLIC, inMemoryType, "unmarshal");
        JVar $value = unmarshal.param(String.class, "value");

        JExpression inv;

        if( parseMethod.equals("new") ) {
            // "new" indicates that the constructor of the target type
            // will do the unmarshalling.

            // RESULT: new <type>()
            inv = JExpr._new(inMemoryType).arg($value);
        } else {
            int idx = parseMethod.lastIndexOf('.');
            if(idx<0) {
                // parseMethod specifies the static method of the target type
                // which will do the unmarshalling.

                // because of an error check at the constructor,
                // we can safely assume that this cast works.
                inv = inMemoryType.staticInvoke(parseMethod).arg($value);
            } else {
                inv = JExpr.direct(parseMethod+"(value)");
            }
        }
        unmarshal.body()._return(inv);


        JMethod marshal = adapter.method(JMod.PUBLIC, String.class, "marshal");
        $value = marshal.param(inMemoryType,"value");

        int idx = printMethod.lastIndexOf('.');
        if(idx<0) {
            // printMethod specifies a method in the target type
            // which performs the serialization.

            // RESULT: <value>.<method>()
            inv = $value.invoke(printMethod);
        } else {
            // RESULT: <className>.<method>(<value>)
            inv = JExpr.direct(printMethod+"(value)");
        }
        marshal.body()._return(inv);

        return adapter;
    }
}
