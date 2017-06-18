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
public final class AddressingMessages {

    private final static String BUNDLE_NAME = "com.sun.xml.internal.ws.resources.addressing";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new AddressingMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableADDRESSING_NOT_ENABLED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("addressing.notEnabled", arg0);
    }

    /**
     * Addressing is not enabled, {0} should not be included in the pipeline"
     *
     */
    public static String ADDRESSING_NOT_ENABLED(Object arg0) {
        return LOCALIZER.localize(localizableADDRESSING_NOT_ENABLED(arg0));
    }

    public static Localizable localizableWSAW_ANONYMOUS_PROHIBITED() {
        return MESSAGE_FACTORY.getMessage("wsaw.anonymousProhibited");
    }

    /**
     * Operation has "prohibited" value for wsaw:anonymous in the WSDL, Addressing must be disabled and SOAP message need to be hand-crafted
     *
     */
    public static String WSAW_ANONYMOUS_PROHIBITED() {
        return LOCALIZER.localize(localizableWSAW_ANONYMOUS_PROHIBITED());
    }

    public static Localizable localizableNULL_SOAP_VERSION() {
        return MESSAGE_FACTORY.getMessage("null.soap.version");
    }

    /**
     * Unexpected null SOAP version
     *
     */
    public static String NULL_SOAP_VERSION() {
        return LOCALIZER.localize(localizableNULL_SOAP_VERSION());
    }

    public static Localizable localizableNULL_HEADERS() {
        return MESSAGE_FACTORY.getMessage("null.headers");
    }

    /**
     * No headers found when processing the server inbound request and WS-Addressing is required
     *
     */
    public static String NULL_HEADERS() {
        return LOCALIZER.localize(localizableNULL_HEADERS());
    }

    public static Localizable localizableFAULT_TO_CANNOT_PARSE() {
        return MESSAGE_FACTORY.getMessage("faultTo.cannot.parse");
    }

    /**
     * FaultTo header cannot be parsed
     *
     */
    public static String FAULT_TO_CANNOT_PARSE() {
        return LOCALIZER.localize(localizableFAULT_TO_CANNOT_PARSE());
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_NULL_HEADERS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("nonAnonymous.response.nullHeaders", arg0);
    }

    /**
     * No response headers found in non-anonymous response from "{0}"
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_NULL_HEADERS(Object arg0) {
        return LOCALIZER.localize(localizableNON_ANONYMOUS_RESPONSE_NULL_HEADERS(arg0));
    }

    public static Localizable localizableUNKNOWN_WSA_HEADER() {
        return MESSAGE_FACTORY.getMessage("unknown.wsa.header");
    }

    /**
     * Unknown WS-Addressing header
     *
     */
    public static String UNKNOWN_WSA_HEADER() {
        return LOCALIZER.localize(localizableUNKNOWN_WSA_HEADER());
    }

    public static Localizable localizableINVALID_ADDRESSING_HEADER_EXCEPTION(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.addressing.header.exception", arg0, arg1);
    }

    /**
     * Invalid WS-Addressing header: "{0}",Reason: "{1}"
     *
     */
    public static String INVALID_ADDRESSING_HEADER_EXCEPTION(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_ADDRESSING_HEADER_EXCEPTION(arg0, arg1));
    }

    public static Localizable localizableNULL_WSDL_PORT() {
        return MESSAGE_FACTORY.getMessage("null.wsdlPort");
    }

    /**
     * Populating request Addressing headers and found null WSDLPort
     *
     */
    public static String NULL_WSDL_PORT() {
        return LOCALIZER.localize(localizableNULL_WSDL_PORT());
    }

    public static Localizable localizableNON_ANONYMOUS_UNKNOWN_PROTOCOL(Object arg0) {
        return MESSAGE_FACTORY.getMessage("nonAnonymous.unknown.protocol", arg0);
    }

    /**
     * Unknown protocol: "{0}"
     *
     */
    public static String NON_ANONYMOUS_UNKNOWN_PROTOCOL(Object arg0) {
        return LOCALIZER.localize(localizableNON_ANONYMOUS_UNKNOWN_PROTOCOL(arg0));
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_SENDING(Object arg0) {
        return MESSAGE_FACTORY.getMessage("nonAnonymous.response.sending", arg0);
    }

    /**
     * Sending non-anonymous reply to "{0}"
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_SENDING(Object arg0) {
        return LOCALIZER.localize(localizableNON_ANONYMOUS_RESPONSE_SENDING(arg0));
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE() {
        return MESSAGE_FACTORY.getMessage("nonAnonymous.response");
    }

    /**
     * Sending 202 and processing non-anonymous response
     *
     */
    public static String NON_ANONYMOUS_RESPONSE() {
        return LOCALIZER.localize(localizableNON_ANONYMOUS_RESPONSE());
    }

    public static Localizable localizableREPLY_TO_CANNOT_PARSE() {
        return MESSAGE_FACTORY.getMessage("replyTo.cannot.parse");
    }

    /**
     * ReplyTo header cannot be parsed
     *
     */
    public static String REPLY_TO_CANNOT_PARSE() {
        return LOCALIZER.localize(localizableREPLY_TO_CANNOT_PARSE());
    }

    public static Localizable localizableINVALID_WSAW_ANONYMOUS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("invalid.wsaw.anonymous", arg0);
    }

    /**
     * Invalid value obtained from wsaw:Anonymous: "{0}"
     *
     */
    public static String INVALID_WSAW_ANONYMOUS(Object arg0) {
        return LOCALIZER.localize(localizableINVALID_WSAW_ANONYMOUS(arg0));
    }

    public static Localizable localizableVALIDATION_CLIENT_NULL_ACTION() {
        return MESSAGE_FACTORY.getMessage("validation.client.nullAction");
    }

    /**
     * Validating inbound Addressing headers on client and found null Action
     *
     */
    public static String VALIDATION_CLIENT_NULL_ACTION() {
        return LOCALIZER.localize(localizableVALIDATION_CLIENT_NULL_ACTION());
    }

    public static Localizable localizableWSDL_BOUND_OPERATION_NOT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlBoundOperation.notFound", arg0);
    }

    /**
     * Cannot find an operation in wsdl:binding for "{0}"
     *
     */
    public static String WSDL_BOUND_OPERATION_NOT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableWSDL_BOUND_OPERATION_NOT_FOUND(arg0));
    }

    public static Localizable localizableMISSING_HEADER_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("missing.header.exception", arg0);
    }

    /**
     * Missing WS-Addressing header: "{0}"
     *
     */
    public static String MISSING_HEADER_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizableMISSING_HEADER_EXCEPTION(arg0));
    }

    public static Localizable localizableNULL_BINDING() {
        return MESSAGE_FACTORY.getMessage("null.binding");
    }

    /**
     * Populating request Addressing headers and found null Binding
     *
     */
    public static String NULL_BINDING() {
        return LOCALIZER.localize(localizableNULL_BINDING());
    }

    public static Localizable localizableNULL_WSA_HEADERS() {
        return MESSAGE_FACTORY.getMessage("null.wsa.headers");
    }

    /**
     * No WS-Addressing headers found processing the server inbound request
     *
     */
    public static String NULL_WSA_HEADERS() {
        return LOCALIZER.localize(localizableNULL_WSA_HEADERS());
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_ONEWAY() {
        return MESSAGE_FACTORY.getMessage("nonAnonymous.response.oneway");
    }

    /**
     * Ignoring non-anonymous response for one-way message
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_ONEWAY() {
        return LOCALIZER.localize(localizableNON_ANONYMOUS_RESPONSE_ONEWAY());
    }

    public static Localizable localizableVALIDATION_SERVER_NULL_ACTION() {
        return MESSAGE_FACTORY.getMessage("validation.server.nullAction");
    }

    /**
     * Validating inbound Addressing headers on server and found null Action
     *
     */
    public static String VALIDATION_SERVER_NULL_ACTION() {
        return LOCALIZER.localize(localizableVALIDATION_SERVER_NULL_ACTION());
    }

    public static Localizable localizableWRONG_ADDRESSING_VERSION(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wrong.addressing.version", arg0, arg1);
    }

    /**
     * Expected "{0}" version of WS-Addressing but found "{1}"
     *
     */
    public static String WRONG_ADDRESSING_VERSION(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWRONG_ADDRESSING_VERSION(arg0, arg1));
    }

    public static Localizable localizableACTION_NOT_SUPPORTED_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("action.not.supported.exception", arg0);
    }

    /**
     * Action: "{0}" not supported
     *
     */
    public static String ACTION_NOT_SUPPORTED_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizableACTION_NOT_SUPPORTED_EXCEPTION(arg0));
    }

    public static Localizable localizableNULL_MESSAGE() {
        return MESSAGE_FACTORY.getMessage("null.message");
    }

    /**
     * Null message found when processing the server inbound request and WS-Addressing is required
     *
     */
    public static String NULL_MESSAGE() {
        return LOCALIZER.localize(localizableNULL_MESSAGE());
    }

    public static Localizable localizableADDRESSING_SHOULD_BE_ENABLED() {
        return MESSAGE_FACTORY.getMessage("addressing.should.be.enabled.");
    }

    /**
     * Addressing is not enabled
     *
     */
    public static String ADDRESSING_SHOULD_BE_ENABLED() {
        return LOCALIZER.localize(localizableADDRESSING_SHOULD_BE_ENABLED());
    }

    public static Localizable localizableNULL_PACKET() {
        return MESSAGE_FACTORY.getMessage("null.packet");
    }

    /**
     * Populating request Addressing headers and found null Packet
     *
     */
    public static String NULL_PACKET() {
        return LOCALIZER.localize(localizableNULL_PACKET());
    }

    public static Localizable localizableNULL_ADDRESSING_VERSION() {
        return MESSAGE_FACTORY.getMessage("null.addressing.version");
    }

    /**
     * Unexpected null Addressing version
     *
     */
    public static String NULL_ADDRESSING_VERSION() {
        return LOCALIZER.localize(localizableNULL_ADDRESSING_VERSION());
    }

    public static Localizable localizableNULL_ACTION() {
        return MESSAGE_FACTORY.getMessage("null.action");
    }

    /**
     * Populating request Addressing headers and found null Action
     *
     */
    public static String NULL_ACTION() {
        return LOCALIZER.localize(localizableNULL_ACTION());
    }

    public static Localizable localizableNON_UNIQUE_OPERATION_SIGNATURE(Object arg0, Object arg1, Object arg2, Object arg3) {
        return MESSAGE_FACTORY.getMessage("non.unique.operation.signature", arg0, arg1, arg2, arg3);
    }

    /**
     * Operations in a port should have unique operation signature to successfuly identify a associated wsdl operation for a message. WSDL operation {0} and {1} have the same operation signature, wsa:Action "{2}" and request body block "{3}", Method dispatching may fail at runtime. Use unique wsa:Action for each operation
     *
     */
    public static String NON_UNIQUE_OPERATION_SIGNATURE(Object arg0, Object arg1, Object arg2, Object arg3) {
        return LOCALIZER.localize(localizableNON_UNIQUE_OPERATION_SIGNATURE(arg0, arg1, arg2, arg3));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
