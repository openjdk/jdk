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
public final class AddressingMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.addressing");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_ONEWAY() {
        return messageFactory.getMessage("nonAnonymous.response.oneway");
    }

    /**
     * Ignoring non-anonymous response for one-way message
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_ONEWAY() {
        return localizer.localize(localizableNON_ANONYMOUS_RESPONSE_ONEWAY());
    }

    public static Localizable localizableNULL_WSA_HEADERS() {
        return messageFactory.getMessage("null.wsa.headers");
    }

    /**
     * No WS-Addressing headers found processing the server inbound request
     *
     */
    public static String NULL_WSA_HEADERS() {
        return localizer.localize(localizableNULL_WSA_HEADERS());
    }

    public static Localizable localizableUNKNOWN_WSA_HEADER() {
        return messageFactory.getMessage("unknown.wsa.header");
    }

    /**
     * Unknown WS-Addressing header
     *
     */
    public static String UNKNOWN_WSA_HEADER() {
        return localizer.localize(localizableUNKNOWN_WSA_HEADER());
    }

    public static Localizable localizableNULL_ACTION() {
        return messageFactory.getMessage("null.action");
    }

    /**
     * Populating request Addressing headers and found null Action
     *
     */
    public static String NULL_ACTION() {
        return localizer.localize(localizableNULL_ACTION());
    }

    public static Localizable localizableINVALID_WSAW_ANONYMOUS(Object arg0) {
        return messageFactory.getMessage("invalid.wsaw.anonymous", arg0);
    }

    /**
     * Invalid value obtained from wsaw:Anonymous: "{0}"
     *
     */
    public static String INVALID_WSAW_ANONYMOUS(Object arg0) {
        return localizer.localize(localizableINVALID_WSAW_ANONYMOUS(arg0));
    }

    public static Localizable localizableNULL_SOAP_VERSION() {
        return messageFactory.getMessage("null.soap.version");
    }

    /**
     * Unexpected null SOAP version
     *
     */
    public static String NULL_SOAP_VERSION() {
        return localizer.localize(localizableNULL_SOAP_VERSION());
    }

    public static Localizable localizableWSDL_BOUND_OPERATION_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("wsdlBoundOperation.notFound", arg0);
    }

    /**
     * Cannot find an operation in wsdl:binding for "{0}"
     *
     */
    public static String WSDL_BOUND_OPERATION_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableWSDL_BOUND_OPERATION_NOT_FOUND(arg0));
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE() {
        return messageFactory.getMessage("nonAnonymous.response");
    }

    /**
     * Sending 202 and processing non-anonymous response
     *
     */
    public static String NON_ANONYMOUS_RESPONSE() {
        return localizer.localize(localizableNON_ANONYMOUS_RESPONSE());
    }

    public static Localizable localizableVALIDATION_SERVER_NULL_ACTION() {
        return messageFactory.getMessage("validation.server.nullAction");
    }

    /**
     * Validating inbound Addressing headers on server and found null Action
     *
     */
    public static String VALIDATION_SERVER_NULL_ACTION() {
        return localizer.localize(localizableVALIDATION_SERVER_NULL_ACTION());
    }

    public static Localizable localizableFAULT_TO_CANNOT_PARSE() {
        return messageFactory.getMessage("faultTo.cannot.parse");
    }

    /**
     * FaultTo header cannot be parsed
     *
     */
    public static String FAULT_TO_CANNOT_PARSE() {
        return localizer.localize(localizableFAULT_TO_CANNOT_PARSE());
    }

    public static Localizable localizableVALIDATION_CLIENT_NULL_ACTION() {
        return messageFactory.getMessage("validation.client.nullAction");
    }

    /**
     * Validating inbound Addressing headers on client and found null Action
     *
     */
    public static String VALIDATION_CLIENT_NULL_ACTION() {
        return localizer.localize(localizableVALIDATION_CLIENT_NULL_ACTION());
    }

    public static Localizable localizableNULL_MESSAGE() {
        return messageFactory.getMessage("null.message");
    }

    /**
     * Null message found when processing the server inbound request and WS-Addressing is required
     *
     */
    public static String NULL_MESSAGE() {
        return localizer.localize(localizableNULL_MESSAGE());
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_NULL_HEADERS(Object arg0) {
        return messageFactory.getMessage("nonAnonymous.response.nullHeaders", arg0);
    }

    /**
     * No response headers found in non-anonymous response from "{0}"
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_NULL_HEADERS(Object arg0) {
        return localizer.localize(localizableNON_ANONYMOUS_RESPONSE_NULL_HEADERS(arg0));
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_SENDING(Object arg0) {
        return messageFactory.getMessage("nonAnonymous.response.sending", arg0);
    }

    /**
     * Sending non-anonymous reply to "{0}"
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_SENDING(Object arg0) {
        return localizer.localize(localizableNON_ANONYMOUS_RESPONSE_SENDING(arg0));
    }

    public static Localizable localizableREPLY_TO_CANNOT_PARSE() {
        return messageFactory.getMessage("replyTo.cannot.parse");
    }

    /**
     * ReplyTo header cannot be parsed
     *
     */
    public static String REPLY_TO_CANNOT_PARSE() {
        return localizer.localize(localizableREPLY_TO_CANNOT_PARSE());
    }

    public static Localizable localizableWSAW_ANONYMOUS_PROHIBITED() {
        return messageFactory.getMessage("wsaw.anonymousProhibited");
    }

    /**
     * Operation has "prohibited" value for wsaw:anonymous in the WSDL, Addressing must be disabled and SOAP message need to be hand-crafted
     *
     */
    public static String WSAW_ANONYMOUS_PROHIBITED() {
        return localizer.localize(localizableWSAW_ANONYMOUS_PROHIBITED());
    }

    public static Localizable localizableNULL_WSDL_PORT() {
        return messageFactory.getMessage("null.wsdlPort");
    }

    /**
     * Populating request Addressing headers and found null WSDLPort
     *
     */
    public static String NULL_WSDL_PORT() {
        return localizer.localize(localizableNULL_WSDL_PORT());
    }

    public static Localizable localizableNULL_ADDRESSING_VERSION() {
        return messageFactory.getMessage("null.addressing.version");
    }

    /**
     * Unexpected null Addressing version
     *
     */
    public static String NULL_ADDRESSING_VERSION() {
        return localizer.localize(localizableNULL_ADDRESSING_VERSION());
    }

    public static Localizable localizableNULL_PACKET() {
        return messageFactory.getMessage("null.packet");
    }

    /**
     * Populating request Addressing headers and found null Packet
     *
     */
    public static String NULL_PACKET() {
        return localizer.localize(localizableNULL_PACKET());
    }

    public static Localizable localizableWRONG_ADDRESSING_VERSION(Object arg0, Object arg1) {
        return messageFactory.getMessage("wrong.addressing.version", arg0, arg1);
    }

    /**
     * Expected "{0}" version of WS-Addressing but found "{1}"
     *
     */
    public static String WRONG_ADDRESSING_VERSION(Object arg0, Object arg1) {
        return localizer.localize(localizableWRONG_ADDRESSING_VERSION(arg0, arg1));
    }

    public static Localizable localizableADDRESSING_NOT_ENABLED(Object arg0) {
        return messageFactory.getMessage("addressing.notEnabled", arg0);
    }

    /**
     * Addressing is not enabled, {0} should not be included in the pipeline"
     *
     */
    public static String ADDRESSING_NOT_ENABLED(Object arg0) {
        return localizer.localize(localizableADDRESSING_NOT_ENABLED(arg0));
    }

    public static Localizable localizableNON_ANONYMOUS_UNKNOWN_PROTOCOL(Object arg0) {
        return messageFactory.getMessage("nonAnonymous.unknown.protocol", arg0);
    }

    /**
     * Unknown protocol: "{0}"
     *
     */
    public static String NON_ANONYMOUS_UNKNOWN_PROTOCOL(Object arg0) {
        return localizer.localize(localizableNON_ANONYMOUS_UNKNOWN_PROTOCOL(arg0));
    }

    public static Localizable localizableNON_ANONYMOUS_RESPONSE_NULL_MESSAGE(Object arg0) {
        return messageFactory.getMessage("nonAnonymous.response.nullMessage", arg0);
    }

    /**
     * No message for non-anonymous response from "{0}"
     *
     */
    public static String NON_ANONYMOUS_RESPONSE_NULL_MESSAGE(Object arg0) {
        return localizer.localize(localizableNON_ANONYMOUS_RESPONSE_NULL_MESSAGE(arg0));
    }

    public static Localizable localizableNULL_HEADERS() {
        return messageFactory.getMessage("null.headers");
    }

    /**
     * No headers found when processing the server inbound request and WS-Addressing is required
     *
     */
    public static String NULL_HEADERS() {
        return localizer.localize(localizableNULL_HEADERS());
    }

    public static Localizable localizableNULL_BINDING() {
        return messageFactory.getMessage("null.binding");
    }

    /**
     * Populating request Addressing headers and found null Binding
     *
     */
    public static String NULL_BINDING() {
        return localizer.localize(localizableNULL_BINDING());
    }

}
