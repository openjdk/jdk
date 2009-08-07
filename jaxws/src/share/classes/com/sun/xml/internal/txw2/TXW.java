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

package com.sun.xml.internal.txw2;

import com.sun.xml.internal.txw2.output.XmlSerializer;
import com.sun.xml.internal.txw2.output.TXWSerializer;
import com.sun.xml.internal.txw2.annotation.XmlElement;
import com.sun.xml.internal.txw2.annotation.XmlNamespace;

import javax.xml.namespace.QName;

/**
 * Entry point to TXW.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TXW {
    private TXW() {}    // no instanciation please


    /*package*/ static QName getTagName( Class<?> c ) {
        String localName="";
        String nsUri="##default";

        XmlElement xe = c.getAnnotation(XmlElement.class);
        if(xe!=null) {
            localName = xe.value();
            nsUri = xe.ns();
        }

        if(localName.length()==0) {
            localName = c.getName();
            int idx = localName.lastIndexOf('.');
            if(idx>=0)
                localName = localName.substring(idx+1);

            localName = Character.toLowerCase(localName.charAt(0))+localName.substring(1);
        }

        if(nsUri.equals("##default")) {
            Package pkg = c.getPackage();
            if(pkg!=null) {
                XmlNamespace xn = pkg.getAnnotation(XmlNamespace.class);
                if(xn!=null)
                    nsUri = xn.value();
            }
        }
        if(nsUri.equals("##default"))
            nsUri = "";

        return new QName(nsUri,localName);
    }

    /**
     * Creates a new {@link TypedXmlWriter} to write a new instance of a document.
     *
     * @param rootElement
     *      The {@link TypedXmlWriter} interface that declares the content model of the root element.
     *      This interface must have {@link XmlElement} annotation on it to designate the tag name
     *      of the root element.
     * @param out
     *      The target of the writing.
     */
    public static <T extends TypedXmlWriter> T create( Class<T> rootElement, XmlSerializer out ) {
        if (out instanceof TXWSerializer) {
            TXWSerializer txws = (TXWSerializer) out;
            return txws.txw._element(rootElement);
        }

        Document doc = new Document(out);
        QName n = getTagName(rootElement);
        return new ContainerElement(doc,null,n.getNamespaceURI(),n.getLocalPart())._cast(rootElement);
    }

    /**
     * Creates a new {@link TypedXmlWriter} to write a new instance of a document.
     *
     * <p>
     * Similar to the other method, but this version allows the caller to set the
     * tag name at the run-time.
     *
     * @param tagName
     *      The tag name of the root document.
     *
     * @see #create(Class,XmlSerializer)
     */
    public static <T extends TypedXmlWriter> T create( QName tagName, Class<T> rootElement, XmlSerializer out ) {
        if (out instanceof TXWSerializer) {
            TXWSerializer txws = (TXWSerializer) out;
            return txws.txw._element(tagName,rootElement);
        }
        return new ContainerElement(new Document(out),null,tagName.getNamespaceURI(),tagName.getLocalPart())._cast(rootElement);
    }
}
