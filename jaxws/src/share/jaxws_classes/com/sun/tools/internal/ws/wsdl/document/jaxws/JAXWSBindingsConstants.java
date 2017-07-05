/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wsdl.document.jaxws;

import com.sun.tools.internal.ws.wsdl.parser.Constants;

import javax.xml.namespace.QName;

/**
 * @author Vivek Pandey
 *
 */
public interface JAXWSBindingsConstants {

    static final String NS_JAXWS_BINDINGS = "http://java.sun.com/xml/ns/jaxws";
    static final String NS_JAXB_BINDINGS = "http://java.sun.com/xml/ns/jaxb";
    static final String NS_XJC_BINDINGS = "http://java.sun.com/xml/ns/jaxb/xjc";

    /**
     * jaxws:bindings schema component
     *
     * <jaxws:bindings wsdlLocation="xs:anyURI"? node="xs:string"?
     *      version="string"?> binding declarations...
     * </jaxws:bindings>
     *
     * wsdlLocation="xs:anyURI"? node="xs:string"? version="string"?> binding
     * declarations... </jaxws:bindings>
     *
     * <code>@wsdlLocation</code> A URI pointing to a WSDL file establishing the scope of the
     *               contents of this binding declaration. It MUST NOT be
     *               present if the binding declaration is used as an extension
     *               inside a WSDL document or if there is an ancestor binding
     *               declaration that contains this attribute.
     *
     * <code>@node</code> An XPath expression pointing to the element in the WSDL file in
     *       scope that this binding declaration is attached to.
     *
     * <code>@version</code> A version identifier. It MAY only appear on jaxws:bindings
     *          elements that don't have any jaxws:bindings ancestors (i.e. on
     *          outermost binding declarations).
     */
    static final QName JAXWS_BINDINGS = new QName(NS_JAXWS_BINDINGS, "bindings");
    static final String WSDL_LOCATION_ATTR = "wsdlLocation";
    static final String NODE_ATTR = "node";
    static final String VERSION_ATTR = "version";

    /*
     * <jaxws:package name="xs:string">? <jaxws:javadoc>xs:string
     * </jaxws:javadoc> </jaxws:package>
     */
    static final QName PACKAGE = new QName(NS_JAXWS_BINDINGS, "package");
    static final String NAME_ATTR = "name";
    static final QName JAVADOC = new QName(NS_JAXWS_BINDINGS, "javadoc");

    /*
     * <jaxws:enableWrapperStyle>xs:boolean </jaxws:enableWrapperStyle>?
     */
    static final QName ENABLE_WRAPPER_STYLE = new QName(NS_JAXWS_BINDINGS, "enableWrapperStyle");

    /*
     * <jaxws:enableAsynchronousMapping>xs:boolean
     *      </jaxws:enableAsynchronousMapping>?
     */
    static final QName ENABLE_ASYNC_MAPPING = new QName(NS_JAXWS_BINDINGS, "enableAsyncMapping");

    /*
     * <jaxws:enableAdditionalSOAPHeaderMapping>xs:boolean</jaxws:enableAdditionalSOAPHeaderMapping>?
     */
    static final QName ENABLE_ADDITIONAL_SOAPHEADER_MAPPING = new QName(NS_JAXWS_BINDINGS, "enableAdditionalSOAPHeaderMapping");

    /*
     * <jaxws:enableMIMEContent>xs:boolean</jaxws:enableMIMEContent>?
     */
    static final QName ENABLE_MIME_CONTENT = new QName(NS_JAXWS_BINDINGS, "enableMIMEContent");

    /*
     * <jaxwsc:provider>xs:boolean</jaxws:provider>?
     */
    static final QName PROVIDER = new QName(NS_JAXWS_BINDINGS, "provider");

    /*
     * PortType
     *
     * <jaxws:class name="xs:string">?
     *  <jaxws:javadoc>xs:string</jaxws:javadoc>?
     * </jaxws:class>
     *
     * <jaxws:enableWrapperStyle>
     *  xs:boolean
     * </jaxws:enableWrapperStyle>?
     *
     * <jaxws:enableAsynchronousMapping>
     *  xs:boolean
     * </jaxws:enableAsynchronousMapping>?
     *
     */

    static final QName CLASS = new QName(NS_JAXWS_BINDINGS, "class");

    /*
     * PortType WSDLOperation
     *
     * <jaxws:method name="xs:string">?
     *   <jaxws:javadoc>xs:string</jaxws:javadoc>?
     * </jaxws:method>
     *
     * <jaxws:enableWrapperStyle>
     *  xs:boolean
     * </jaxws:enableWrapperStyle>?
     *
     * <jaxws:enableAsyncMapping>
     *  xs:boolean
     * </jaxws:enableAsyncMapping>?
     *
     * <jaxws:parameter part="xs:string"
     *      childElementName="xs:QName"?
     *      name="xs:string"/>*
     */



    static final QName METHOD = new QName(NS_JAXWS_BINDINGS, "method");
    static final QName PARAMETER = new QName(NS_JAXWS_BINDINGS, "parameter");
    static final String PART_ATTR = "part";
    static final String ELEMENT_ATTR = "childElementName";

    /*
     * Binding
     *
     * <jaxws:enableAdditionalSOAPHeaderMapping>
     *  xs:boolean
     * </jaxws:enableAdditionalSOAPHeaderMapping>?
     *
     * <jaxws:enableMIMEContent>
     *  xs:boolean
     * </jaxws:enableMIMEContent>?
     */

    /*
     * WSDLBoundOperation
     *
     * <jaxws:enableAdditionalSOAPHeaderMapping>
     *  xs:boolean
     * </jaxws:enableAdditionalSOAPHeaderMapping>?
     *
     * <jaxws:enableMIMEContent>
     *  xs:boolean
     * </jaxws:enableMIMEContent>?
     *
     * <jaxws:parameter part="xs:string"
     *                  element="xs:QName"?
     *                  name="xs:string"/>*
     *
     * <jaxws:exception part="xs:string">*
     *  <jaxws:class name="xs:string">?
     *      <jaxws:javadoc>xs:string</jaxws:javadoc>?
     *  </jaxws:class>
     * </jaxws:exception>
     */

    static final QName EXCEPTION = new QName(NS_JAXWS_BINDINGS, "exception");


    /*
     * jaxb:bindgs QName
     */
    static final QName JAXB_BINDINGS = new QName(NS_JAXB_BINDINGS, "bindings");
    static final String JAXB_BINDING_VERSION = "2.0";
    static final QName XSD_APPINFO = new QName(Constants.NS_XSD, "appinfo");
    static final QName XSD_ANNOTATION = new QName(Constants.NS_XSD, "annotation");
}
