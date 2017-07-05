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

package com.sun.tools.internal.xjc.reader.internalizer;

import javax.xml.XMLConstants;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * {@link XMLReader} filter for supporting
 * <tt>http://xml.org/sax/features/namespace-prefixes</tt> feature.
 *
 * @author Kohsuke Kawaguchi
 */
final class ContentHandlerNamespacePrefixAdapter extends XMLFilterImpl {
    /**
     * True if <tt>http://xml.org/sax/features/namespace-prefixes</tt> is set to true.
     */
    private boolean namespacePrefixes = false;

    private String[] nsBinding = new String[8];
    private int len;

    public ContentHandlerNamespacePrefixAdapter() {
    }

    public ContentHandlerNamespacePrefixAdapter(XMLReader parent) {
        setParent(parent);
    }

    @Override
    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if(name.equals(PREFIX_FEATURE))
            return namespacePrefixes;
        return super.getFeature(name);
    }

    @Override
    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if(name.equals(PREFIX_FEATURE)) {
            this.namespacePrefixes = value;
            return;
        }
        if(name.equals(NAMESPACE_FEATURE) && value)
            return;
        super.setFeature(name, value);
    }


    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (XMLConstants.XML_NS_URI.equals(uri)) return; //xml prefix shall not be declared based on jdk api javadoc
        if(len==nsBinding.length) {
            // reallocate
            String[] buf = new String[nsBinding.length*2];
            System.arraycopy(nsBinding,0,buf,0,nsBinding.length);
            nsBinding = buf;
        }
        nsBinding[len++] = prefix;
        nsBinding[len++] = uri;
        super.startPrefixMapping(prefix,uri);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if(namespacePrefixes) {
            this.atts.setAttributes(atts);
            // add namespace bindings back as attributes
            for( int i=0; i<len; i+=2 ) {
                String prefix = nsBinding[i];
                if(prefix.length()==0)
                    this.atts.addAttribute(XMLConstants.XML_NS_URI,"xmlns","xmlns","CDATA",nsBinding[i+1]);
                else
                    this.atts.addAttribute(XMLConstants.XML_NS_URI,prefix,"xmlns:"+prefix,"CDATA",nsBinding[i+1]);
            }
            atts = this.atts;
        }
        len=0;
        super.startElement(uri, localName, qName, atts);
    }

    private final AttributesImpl atts = new AttributesImpl();

    private static final String PREFIX_FEATURE = "http://xml.org/sax/features/namespace-prefixes";
    private static final String NAMESPACE_FEATURE = "http://xml.org/sax/features/namespaces";
}
