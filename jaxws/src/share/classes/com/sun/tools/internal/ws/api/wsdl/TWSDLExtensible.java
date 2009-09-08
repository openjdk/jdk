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

package com.sun.tools.internal.ws.api.wsdl;


import javax.xml.namespace.QName;

/**
 * A WSDL element or attribute that can be extended.
 *
 * @author Vivek Pandey
 * @deprecated This interface is deprecated, will be removed in JAX-WS 2.2 RI.
 *
 */
public interface TWSDLExtensible {
    /**
     * Gives the wsdl extensiblity element's name attribute value. It can be null as @name on some of the wsdl
     * extensibility elements are optinal such as wsdl:input
     */
    String getNameValue();

    /**
     * Gives namespace URI of a wsdl extensibility element.
     */
    String getNamespaceURI();

    /**
     * Gives the WSDL element or WSDL extensibility element name
     */
    QName getWSDLElementName();

    /**
     * An {@link TWSDLExtensionHandler} will call this method to add an {@link TWSDLExtension} object
     *
     * @param e non-null extension object
     */
    void addExtension(TWSDLExtension e);

    /**
     * Gives iterator over {@link TWSDLExtension}s
     */
    Iterable<? extends TWSDLExtension> extensions();

    /**
     * Gives the parent of a wsdl extensibility element.
     * <pre>
     * For example,
     *
     *     <wsdl:portType>
     *         <wsdl:operation>
     *     ...
     * Here, the {@link TWSDLExtensible}representing wsdl:operation's parent would be wsdl:portType
     *
     * @return null if the {@link TWSDLExtensible} has no parent, root of wsdl document - wsdl:definition.
     */
    TWSDLExtensible getParent();
}
