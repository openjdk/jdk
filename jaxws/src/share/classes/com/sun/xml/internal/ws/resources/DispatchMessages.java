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
package com.sun.xml.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class DispatchMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.dispatch");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableINVALID_NULLARG_XMLHTTP_REQUEST_METHOD(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.nullarg.xmlhttp.request.method", arg0, arg1);
    }

    /**
     * A XML/HTTP request using MessageContext.HTTP_REQUEST_METHOD equals {0} with a Null invocation Argument is not allowed. Must be: {1}
     *
     */
    public static String INVALID_NULLARG_XMLHTTP_REQUEST_METHOD(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_NULLARG_XMLHTTP_REQUEST_METHOD(arg0, arg1));
    }

    public static Localizable localizableINVALID_SOAPMESSAGE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.soapmessage.dispatch.msgmode", arg0, arg1);
    }

    /**
     * Can not create Dispatch<SOAPMessage> of {0}. Must be {1}.
     *
     */
    public static String INVALID_SOAPMESSAGE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_SOAPMESSAGE_DISPATCH_MSGMODE(arg0, arg1));
    }

    public static Localizable localizableINVALID_RESPONSE_DESERIALIZATION() {
        return messageFactory.getMessage("invalid.response.deserialization");
    }

    /**
     * Failed to deserialize the response.
     *
     */
    public static String INVALID_RESPONSE_DESERIALIZATION() {
        return localizer.localize(localizableINVALID_RESPONSE_DESERIALIZATION());
    }

    public static Localizable localizableINVALID_QUERY_LEADING_CHAR(Object arg0) {
        return messageFactory.getMessage("invalid.query.leading.char", arg0);
    }

    /**
     * Leading '?' of MessageContext.QUERY_STRING: {0} is not valid. Remove '?' and run again.
     *
     */
    public static String INVALID_QUERY_LEADING_CHAR(Object arg0) {
        return localizer.localize(localizableINVALID_QUERY_LEADING_CHAR(arg0));
    }

    public static Localizable localizableINVALID_QUERY_STRING(Object arg0) {
        return messageFactory.getMessage("invalid.query.string", arg0);
    }

    /**
     * Unable to resolve endpoint address using the supplied query string: {0}.
     *
     */
    public static String INVALID_QUERY_STRING(Object arg0) {
        return localizer.localize(localizableINVALID_QUERY_STRING(arg0));
    }

    public static Localizable localizableDUPLICATE_PORT(Object arg0) {
        return messageFactory.getMessage("duplicate.port", arg0);
    }

    /**
     * WSDLPort {0} already exists. Can not create a port of the same QName.
     *
     */
    public static String DUPLICATE_PORT(Object arg0) {
        return localizer.localize(localizableDUPLICATE_PORT(arg0));
    }

    public static Localizable localizableINVALID_DATASOURCE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.datasource.dispatch.binding", arg0, arg1);
    }

    /**
     * Can not create Dispatch<DataSource> with {0}. Must be: {1}
     *
     */
    public static String INVALID_DATASOURCE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_DATASOURCE_DISPATCH_BINDING(arg0, arg1));
    }

    public static Localizable localizableINVALID_DATASOURCE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.datasource.dispatch.msgmode", arg0, arg1);
    }

    /**
     * Can not create Dispatch<DataSource> of Service.Mode.PAYLOAD{0}. Must be: {1}
     *
     */
    public static String INVALID_DATASOURCE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_DATASOURCE_DISPATCH_MSGMODE(arg0, arg1));
    }

    public static Localizable localizableINVALID_NULLARG_SOAP_MSGMODE(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.nullarg.soap.msgmode", arg0, arg1);
    }

    /**
     * SOAP/HTTP Binding in {0} is not allowed with a null invocation argument. Must be: {1}
     *
     */
    public static String INVALID_NULLARG_SOAP_MSGMODE(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_NULLARG_SOAP_MSGMODE(arg0, arg1));
    }

    public static Localizable localizableINVALID_URI(Object arg0) {
        return messageFactory.getMessage("invalid.uri", arg0);
    }

    /**
     * Endpoint String: {0} is and invalid URI.
     *
     */
    public static String INVALID_URI(Object arg0) {
        return localizer.localize(localizableINVALID_URI(arg0));
    }

    public static Localizable localizableINVALID_SOAPMESSAGE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.soapmessage.dispatch.binding", arg0, arg1);
    }

    /**
     * Can not create Dispatch<SOAPMessage> with {0} Binding. Must be: {1} Binding.
     *
     */
    public static String INVALID_SOAPMESSAGE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_SOAPMESSAGE_DISPATCH_BINDING(arg0, arg1));
    }

    public static Localizable localizableINVALID_URI_PATH_QUERY(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.uri.path.query", arg0, arg1);
    }

    /**
     * Unable to construct a URI with this path info {0} and this query string {1}.
     *
     */
    public static String INVALID_URI_PATH_QUERY(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_URI_PATH_QUERY(arg0, arg1));
    }

    public static Localizable localizableINVALID_RESPONSE() {
        return messageFactory.getMessage("invalid.response");
    }

    /**
     * No response returned.
     *
     */
    public static String INVALID_RESPONSE() {
        return localizer.localize(localizableINVALID_RESPONSE());
    }

    public static Localizable localizableINVALID_URI_RESOLUTION(Object arg0) {
        return messageFactory.getMessage("invalid.uri.resolution", arg0);
    }

    /**
     * Unable to resolve endpoint address using the supplied path: {0}.
     *
     */
    public static String INVALID_URI_RESOLUTION(Object arg0) {
        return localizer.localize(localizableINVALID_URI_RESOLUTION(arg0));
    }

    public static Localizable localizableINVALID_URI_DECODE() {
        return messageFactory.getMessage("invalid.uri.decode");
    }

    /**
     * Unable to decode the resolved endpoint using UTF-8 encoding.
     *
     */
    public static String INVALID_URI_DECODE() {
        return localizer.localize(localizableINVALID_URI_DECODE());
    }

}
