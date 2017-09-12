/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.resources;

import java.util.Locale;
import java.util.ResourceBundle;
import javax.annotation.Generated;
import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.LocalizableMessageFactory.ResourceBundleSupplier;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
@Generated("com.sun.istack.internal.maven.ResourceGenMojo")
public final class DispatchMessages {

    private final static String BUNDLE_NAME = "com.sun.xml.internal.ws.resources.dispatch";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new DispatchMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableINVALID_NULLARG_SOAP_MSGMODE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.nullarg.soap.msgmode", arg0, arg1);
    }

    /**
     * SOAP/HTTP Binding in {0} is not allowed with a null invocation argument. Must be: {1}
     *
     */
    public static String INVALID_NULLARG_SOAP_MSGMODE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_NULLARG_SOAP_MSGMODE(arg0, arg1));
    }

    public static Localizable localizableINVALID_QUERY_STRING(Object arg0) {
        return MESSAGE_FACTORY.getMessage("invalid.query.string", arg0);
    }

    /**
     * Unable to resolve endpoint address using the supplied query string: {0}.
     *
     */
    public static String INVALID_QUERY_STRING(Object arg0) {
        return LOCALIZER.localize(localizableINVALID_QUERY_STRING(arg0));
    }

    public static Localizable localizableINVALID_URI_DECODE() {
        return MESSAGE_FACTORY.getMessage("invalid.uri.decode");
    }

    /**
     * Unable to decode the resolved endpoint using UTF-8 encoding.
     *
     */
    public static String INVALID_URI_DECODE() {
        return LOCALIZER.localize(localizableINVALID_URI_DECODE());
    }

    public static Localizable localizableINVALID_URI_RESOLUTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("invalid.uri.resolution", arg0);
    }

    /**
     * Unable to resolve endpoint address using the supplied path: {0}.
     *
     */
    public static String INVALID_URI_RESOLUTION(Object arg0) {
        return LOCALIZER.localize(localizableINVALID_URI_RESOLUTION(arg0));
    }

    public static Localizable localizableINVALID_NULLARG_URI() {
        return MESSAGE_FACTORY.getMessage("invalid.nullarg.uri");
    }

    /**
     * Endpoint address URI is not allowed with a null argument
     *
     */
    public static String INVALID_NULLARG_URI() {
        return LOCALIZER.localize(localizableINVALID_NULLARG_URI());
    }

    public static Localizable localizableINVALID_URI_PATH_QUERY(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.uri.path.query", arg0, arg1);
    }

    /**
     * Unable to construct a URI with this path info {0} and this query string {1}.
     *
     */
    public static String INVALID_URI_PATH_QUERY(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_URI_PATH_QUERY(arg0, arg1));
    }

    public static Localizable localizableINVALID_URI(Object arg0) {
        return MESSAGE_FACTORY.getMessage("invalid.uri", arg0);
    }

    /**
     * Endpoint String: {0} is and invalid URI.
     *
     */
    public static String INVALID_URI(Object arg0) {
        return LOCALIZER.localize(localizableINVALID_URI(arg0));
    }

    public static Localizable localizableINVALID_DATASOURCE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.datasource.dispatch.msgmode", arg0, arg1);
    }

    /**
     * Can not create Dispatch<DataSource> of Service.Mode.PAYLOAD{0}. Must be: {1}
     *
     */
    public static String INVALID_DATASOURCE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_DATASOURCE_DISPATCH_MSGMODE(arg0, arg1));
    }

    public static Localizable localizableDUPLICATE_PORT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("duplicate.port", arg0);
    }

    /**
     * WSDLPort {0} already exists. Can not create a port of the same QName.
     *
     */
    public static String DUPLICATE_PORT(Object arg0) {
        return LOCALIZER.localize(localizableDUPLICATE_PORT(arg0));
    }

    public static Localizable localizableINVALID_SOAPMESSAGE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.soapmessage.dispatch.binding", arg0, arg1);
    }

    /**
     * Can not create Dispatch<SOAPMessage> with {0} Binding. Must be: {1} Binding.
     *
     */
    public static String INVALID_SOAPMESSAGE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_SOAPMESSAGE_DISPATCH_BINDING(arg0, arg1));
    }

    public static Localizable localizableINVALID_QUERY_LEADING_CHAR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("invalid.query.leading.char", arg0);
    }

    /**
     * Leading '?' of MessageContext.QUERY_STRING: {0} is not valid. Remove '?' and run again.
     *
     */
    public static String INVALID_QUERY_LEADING_CHAR(Object arg0) {
        return LOCALIZER.localize(localizableINVALID_QUERY_LEADING_CHAR(arg0));
    }

    public static Localizable localizableINVALID_RESPONSE_DESERIALIZATION() {
        return MESSAGE_FACTORY.getMessage("invalid.response.deserialization");
    }

    /**
     * Failed to deserialize the response.
     *
     */
    public static String INVALID_RESPONSE_DESERIALIZATION() {
        return LOCALIZER.localize(localizableINVALID_RESPONSE_DESERIALIZATION());
    }

    public static Localizable localizableINVALID_RESPONSE() {
        return MESSAGE_FACTORY.getMessage("invalid.response");
    }

    /**
     * No response returned.
     *
     */
    public static String INVALID_RESPONSE() {
        return LOCALIZER.localize(localizableINVALID_RESPONSE());
    }

    public static Localizable localizableINVALID_SOAPMESSAGE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.soapmessage.dispatch.msgmode", arg0, arg1);
    }

    /**
     * Can not create Dispatch<SOAPMessage> of {0}. Must be {1}.
     *
     */
    public static String INVALID_SOAPMESSAGE_DISPATCH_MSGMODE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_SOAPMESSAGE_DISPATCH_MSGMODE(arg0, arg1));
    }

    public static Localizable localizableINVALID_DATASOURCE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.datasource.dispatch.binding", arg0, arg1);
    }

    /**
     * Can not create Dispatch<DataSource> with {0}. Must be: {1}
     *
     */
    public static String INVALID_DATASOURCE_DISPATCH_BINDING(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_DATASOURCE_DISPATCH_BINDING(arg0, arg1));
    }

    public static Localizable localizableINVALID_NULLARG_XMLHTTP_REQUEST_METHOD(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.nullarg.xmlhttp.request.method", arg0, arg1);
    }

    /**
     * A XML/HTTP request using MessageContext.HTTP_REQUEST_METHOD equals {0} with a Null invocation Argument is not allowed. Must be: {1}
     *
     */
    public static String INVALID_NULLARG_XMLHTTP_REQUEST_METHOD(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_NULLARG_XMLHTTP_REQUEST_METHOD(arg0, arg1));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
