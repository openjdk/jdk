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

package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.xml.internal.bind.api.impl.NameConverter;

import org.w3c.dom.Element;
import org.xml.sax.Locator;


/**
 * &lt;element> declaration in the binding file.
 */
public final class BIElement
{
    /**
     * Wraps a given &lt;element> element in the binding file.
     *
     * <p>
     * Should be created only from {@link BindInfo}.
     */
    BIElement( BindInfo bi, Element _e ) {
        this.parent = bi;
        this.e = _e;

        {
            Element c = DOMUtil.getElement(e,"content");
            if(c!=null) {
                if(DOMUtil.getAttribute(c,"property")!=null) {
                    // if @property is there, this is a general declaration
                    this.rest = BIContent.create(c,this);
                } else {
                    // this must be a model-based declaration
                    for( Element p : DOMUtil.getChildElements(c) ) {
                        if(p.getLocalName().equals("rest"))
                            this.rest = BIContent.create(p,this);
                        else
                            this.contents.add(BIContent.create(p,this));
                    }
                }
            }
        }

        // parse <attribute>s
        for( Element atr : DOMUtil.getChildElements(e,"attribute") ) {
            BIAttribute a = new BIAttribute( this, atr );
            attributes.put(a.name(),a);
        }

        if(isClass()) {
            // if this is a class-declaration, create JClass object now
            String className = DOMUtil.getAttribute(e,"class");
            if(className==null)
                // none was specified. infer the name.
                className = NameConverter.standard.toClassName(name());
            this.className = className;
        } else {
            // this is not an element-class declaration
            className = null;
        }

        // process conversion declarations
        for( Element conv : DOMUtil.getChildElements(e,"conversion") ) {
            BIConversion c = new BIUserConversion(bi,conv);
            conversions.put(c.name(),c);
        }
        for( Element en : DOMUtil.getChildElements(e,"enumeration") ) {
            BIConversion c = BIEnumeration.create(en,this);
            conversions.put(c.name(),c);
        }

        // parse <constructor>s
        for( Element c : DOMUtil.getChildElements(e,"constructor") ) {
            constructors.add( new BIConstructor(c) );
        }

        String name = name();
        QName tagName = new QName("",name);

        this.clazz = new CClassInfo(parent.model,parent.getTargetPackage(),className,getLocation(),null,tagName,null,null/*TODO*/);
    }

    /**
     * Gets the source location where this element is declared.
     */
    public Locator getLocation() {
        return DOMLocator.getLocationInfo(e);
    }


    /** The parent {@link BindInfo} object to which this object belongs. */
    final BindInfo parent;

    /** &lt;element> element which this object is wrapping. */
    private final Element e;

    /**
     * The bean representation for this element.
     */
    public final CClassInfo clazz;

    /**
     * Content-property declarations.
     * <p>
     * This vector will be empty if no content-property declaration is made.
     */
    private final List<BIContent> contents = new ArrayList<BIContent>();

    /** Conversion declarations. */
    private final Map<String,BIConversion> conversions = new HashMap<String,BIConversion>();

    /**
     * The "rest" content-property declaration.
     * <p>
     * This field is null when there was no "rest" declaration.
     */
    private BIContent rest;

    /** Attribute-property declarations. */
    private final Map<String,BIAttribute> attributes = new HashMap<String,BIAttribute>();

    /** Constructor declarations. */
    private final List<BIConstructor> constructors = new ArrayList<BIConstructor>();

    /**
     * the class which is generated by this declaration.
     * This field will be null if this declaration is an element-property
     * declaration.
     */
    private final String className;



    /** Gets the element name. */
    public String name() { return DOMUtil.getAttribute(e,"name"); }

    /**
     * Checks if the element type is "class".
     * If false, that means this element will be a value.
     */
    public boolean isClass() {
        return "class".equals(e.getAttribute("type"));
    }

    /**
     * Checks if this element is designated as a root element.
     */
    public boolean isRoot() {
        return "true".equals(e.getAttribute("root"));
    }

    /**
     * Gets the JClass object that represents this declaration.
     *
     * <p>
     * This method returns null if this declaration
     * is an element-property declaration.
     */
    public String getClassName() {
        return className;
    }

    /**
     * Creates constructor declarations for this element.
     *
     * <p>
     * This method should only be called by DTDReader <b>after</b>
     * the normalization has completed.
     *
     * @param   src
     *      The ClassItem object that corresponds to this declaration
     */
    public void declareConstructors( CClassInfo src ) {
        for( BIConstructor c : constructors )
            c.createDeclaration(src);
    }

    /**
     * Gets the conversion method for this element.
     *
     * <p>
     * This method can be called only when this element
     * declaration is designated as element-value.
     *
     * @return
     *        If the convert attribute is not specified, this
     *        method returns null.
     */
    public BIConversion getConversion() {
          String cnv = DOMUtil.getAttribute(e,"convert");
          if(cnv==null)        return null;

          return conversion(cnv);
    }

    /**
     * Resolves the conversion name to the conversion declaration.
     *
     * <p>
     * Element-local declarations are checked first.
     *
     * @return
     *        A non-null valid BIConversion object.
     */
    public BIConversion conversion( String name ) {
        BIConversion r = conversions.get(name);
        if(r!=null)     return r;

        // check the global conversion declarations
        return parent.conversion(name);
    }


    /**
     * Iterates all content-property declarations (except 'rest').
     */
    public List<BIContent> getContents() {
        return contents;
    }

    /**
     * Gets the attribute-property declaration, if any.
     *
     * @return
     *      null if attribute declaration was not given by that name.
     */
    public BIAttribute attribute( String name ) {
        return attributes.get(name);
    }

    /**
     * Gets the 'rest' content-property declaration, if any.
     * @return
     *      if there is no 'rest' declaration, return null.
     */
    public BIContent getRest() { return this.rest; }

    /** Gets the location where this declaration is declared. */
    public Locator getSourceLocation() {
        return DOMLocator.getLocationInfo(e);
    }
}
