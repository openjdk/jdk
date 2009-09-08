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
public final class WsservletMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.wsservlet");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableERROR_WSDL_PUBLISHER_CANNOT_READ_CONFIGURATION() {
        return messageFactory.getMessage("error.wsdlPublisher.cannotReadConfiguration");
    }

    /**
     * WSSERVLET46: cannot read configuration
     *
     */
    public static String ERROR_WSDL_PUBLISHER_CANNOT_READ_CONFIGURATION() {
        return localizer.localize(localizableERROR_WSDL_PUBLISHER_CANNOT_READ_CONFIGURATION());
    }

    public static Localizable localizableWSSERVLET_22_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET22.diag.check.1");
    }

    /**
     * Set endpoint with stub.setTargetEndpoint property
     *
     */
    public static String WSSERVLET_22_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_22_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_33_DIAG_CAUSE_2() {
        return messageFactory.getMessage("WSSERVLET33.diag.cause.2");
    }

    /**
     * When publishing the service wsdl, the http location is patched with the deployed location/endpoint using XSLT transformation. The transformer could not be created to do the transformation.
     *
     */
    public static String WSSERVLET_33_DIAG_CAUSE_2() {
        return localizer.localize(localizableWSSERVLET_33_DIAG_CAUSE_2());
    }

    public static Localizable localizableWSSERVLET_33_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET33.diag.cause.1");
    }

    /**
     * When publishing the service wsdl, the http location is patched with the deployed location/endpoint using XSLT transformation. The transformer could not be created to do the transformation.
     *
     */
    public static String WSSERVLET_33_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_33_DIAG_CAUSE_1());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_REGISTRY_DUPLICATE_NAME(Object arg0) {
        return messageFactory.getMessage("error.implementorRegistry.duplicateName", arg0);
    }

    /**
     * WSSERVLET42: duplicate port name: {0}
     *
     */
    public static String ERROR_IMPLEMENTOR_REGISTRY_DUPLICATE_NAME(Object arg0) {
        return localizer.localize(localizableERROR_IMPLEMENTOR_REGISTRY_DUPLICATE_NAME(arg0));
    }

    public static Localizable localizableERROR_IMPLEMENTOR_REGISTRY_FILE_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("error.implementorRegistry.fileNotFound", arg0);
    }

    /**
     * WSSERVLET45: file not found: {0}
     *
     */
    public static String ERROR_IMPLEMENTOR_REGISTRY_FILE_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableERROR_IMPLEMENTOR_REGISTRY_FILE_NOT_FOUND(arg0));
    }

    public static Localizable localizableSERVLET_TRACE_INVOKING_IMPLEMENTOR(Object arg0) {
        return messageFactory.getMessage("servlet.trace.invokingImplementor", arg0);
    }

    /**
     * WSSERVLET21: invoking implementor: {0}
     *
     */
    public static String SERVLET_TRACE_INVOKING_IMPLEMENTOR(Object arg0) {
        return localizer.localize(localizableSERVLET_TRACE_INVOKING_IMPLEMENTOR(arg0));
    }

    public static Localizable localizableWSSERVLET_17_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET17.diag.cause.1");
    }

    /**
     * Two or more endpoints with the same name where found in the jaxrpc-ri.xml runtime descriptor
     *
     */
    public static String WSSERVLET_17_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_17_DIAG_CAUSE_1());
    }

    public static Localizable localizableHTML_NON_ROOT_PAGE_BODY_2() {
        return messageFactory.getMessage("html.nonRootPage.body2");
    }

    /**
     * <p>Invalid request URI.</p><p>Please check your deployment information.</p>
     *
     */
    public static String HTML_NON_ROOT_PAGE_BODY_2() {
        return localizer.localize(localizableHTML_NON_ROOT_PAGE_BODY_2());
    }

    public static Localizable localizableHTML_NON_ROOT_PAGE_BODY_1() {
        return messageFactory.getMessage("html.nonRootPage.body1");
    }

    /**
     * <p>A Web Service is installed at this URL.</p>
     *
     */
    public static String HTML_NON_ROOT_PAGE_BODY_1() {
        return localizer.localize(localizableHTML_NON_ROOT_PAGE_BODY_1());
    }

    public static Localizable localizablePUBLISHER_INFO_APPLYING_TRANSFORMATION(Object arg0) {
        return messageFactory.getMessage("publisher.info.applyingTransformation", arg0);
    }

    /**
     * WSSERVLET31: applying transformation with actual address: {0}
     *
     */
    public static String PUBLISHER_INFO_APPLYING_TRANSFORMATION(Object arg0) {
        return localizer.localize(localizablePUBLISHER_INFO_APPLYING_TRANSFORMATION(arg0));
    }

    public static Localizable localizableWSSERVLET_29_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET29.diag.check.1");
    }

    /**
     * Is the port valid? Unzip the war file and make sure the tie and serializers are present
     *
     */
    public static String WSSERVLET_29_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_29_DIAG_CHECK_1());
    }

    public static Localizable localizableSERVLET_TRACE_GOT_REQUEST_FOR_ENDPOINT(Object arg0) {
        return messageFactory.getMessage("servlet.trace.gotRequestForEndpoint", arg0);
    }

    /**
     * WSSERVLET19: got request for endpoint: {0}
     *
     */
    public static String SERVLET_TRACE_GOT_REQUEST_FOR_ENDPOINT(Object arg0) {
        return localizer.localize(localizableSERVLET_TRACE_GOT_REQUEST_FOR_ENDPOINT(arg0));
    }

    public static Localizable localizableERROR_SERVLET_INIT_CONFIG_PARAMETER_MISSING(Object arg0) {
        return messageFactory.getMessage("error.servlet.init.config.parameter.missing", arg0);
    }

    /**
     * WSSERVLET47: cannot find configuration parameter: "{0}"
     *
     */
    public static String ERROR_SERVLET_INIT_CONFIG_PARAMETER_MISSING(Object arg0) {
        return localizer.localize(localizableERROR_SERVLET_INIT_CONFIG_PARAMETER_MISSING(arg0));
    }

    public static Localizable localizableERROR_IMPLEMENTOR_FACTORY_SERVANT_INIT_FAILED(Object arg0) {
        return messageFactory.getMessage("error.implementorFactory.servantInitFailed", arg0);
    }

    /**
     * WSSERVLET44: failed to initialize the service implementor for port "{0}"
     *
     */
    public static String ERROR_IMPLEMENTOR_FACTORY_SERVANT_INIT_FAILED(Object arg0) {
        return localizer.localize(localizableERROR_IMPLEMENTOR_FACTORY_SERVANT_INIT_FAILED(arg0));
    }

    public static Localizable localizableWSSERVLET_13_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET13.diag.check.1");
    }

    /**
     * Normal web service shutdown
     *
     */
    public static String WSSERVLET_13_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_13_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_31_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET31.diag.cause.1");
    }

    /**
     * Transformation being applied
     *
     */
    public static String WSSERVLET_31_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_31_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_50_DIAG_CHECK_3() {
        return messageFactory.getMessage("WSSERVLET50.diag.check.3");
    }

    /**
     * Check the server.xml file in the domain directory for failures
     *
     */
    public static String WSSERVLET_50_DIAG_CHECK_3() {
        return localizer.localize(localizableWSSERVLET_50_DIAG_CHECK_3());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_FACTORY_NO_INPUT_STREAM() {
        return messageFactory.getMessage("error.implementorFactory.noInputStream");
    }

    /**
     * WSSERVLET37: no configuration specified
     *
     */
    public static String ERROR_IMPLEMENTOR_FACTORY_NO_INPUT_STREAM() {
        return localizer.localize(localizableERROR_IMPLEMENTOR_FACTORY_NO_INPUT_STREAM());
    }

    public static Localizable localizableWSSERVLET_24_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET24.diag.cause.1");
    }

    /**
     * SOAPFault message is being returned to the client.
     *
     */
    public static String WSSERVLET_24_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_24_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_50_DIAG_CHECK_2() {
        return messageFactory.getMessage("WSSERVLET50.diag.check.2");
    }

    /**
     * Verify that Application server deployment descriptors are correct in the service war file
     *
     */
    public static String WSSERVLET_50_DIAG_CHECK_2() {
        return localizer.localize(localizableWSSERVLET_50_DIAG_CHECK_2());
    }

    public static Localizable localizableWSSERVLET_50_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET50.diag.check.1");
    }

    /**
     * Verify that sun-jaxws.xml and web.xml are correct in the service war file
     *
     */
    public static String WSSERVLET_50_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_50_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_43_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET43.diag.check.1");
    }

    /**
     * Make sure web service is available and public. Examine exception for more details
     *
     */
    public static String WSSERVLET_43_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_43_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_15_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET15.diag.cause.1");
    }

    /**
     * Web Services servlet shutdown.
     *
     */
    public static String WSSERVLET_15_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_15_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_27_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET27.diag.check.1");
    }

    /**
     * Remove the implicit URL
     *
     */
    public static String WSSERVLET_27_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_27_DIAG_CHECK_1());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_REGISTRY_UNKNOWN_NAME(Object arg0) {
        return messageFactory.getMessage("error.implementorRegistry.unknownName", arg0);
    }

    /**
     * WSSERVLET38: unknown port name: {0}
     *
     */
    public static String ERROR_IMPLEMENTOR_REGISTRY_UNKNOWN_NAME(Object arg0) {
        return localizer.localize(localizableERROR_IMPLEMENTOR_REGISTRY_UNKNOWN_NAME(arg0));
    }

    public static Localizable localizableSERVLET_HTML_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("servlet.html.notFound", arg0);
    }

    /**
     * <h1>404 Not Found: {0}</h1>
     *
     */
    public static String SERVLET_HTML_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableSERVLET_HTML_NOT_FOUND(arg0));
    }

    public static Localizable localizableHTML_ROOT_PAGE_TITLE() {
        return messageFactory.getMessage("html.rootPage.title");
    }

    /**
     * Web Service
     *
     */
    public static String HTML_ROOT_PAGE_TITLE() {
        return localizer.localize(localizableHTML_ROOT_PAGE_TITLE());
    }

    public static Localizable localizableWSSERVLET_20_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET20.diag.check.1");
    }

    /**
     * Unzip the war, are the tie and serializer classes found?
     *
     */
    public static String WSSERVLET_20_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_20_DIAG_CHECK_1());
    }

    public static Localizable localizableJAXRPCSERVLET_11_DIAG_CAUSE_1() {
        return messageFactory.getMessage("JAXRPCSERVLET11.diag.cause.1");
    }

    /**
     * WSRuntimeInfoParser cauld not parse sun-jaxws.xml runtime descriptor
     *
     */
    public static String JAXRPCSERVLET_11_DIAG_CAUSE_1() {
        return localizer.localize(localizableJAXRPCSERVLET_11_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_11_DIAG_CHECK_2() {
        return messageFactory.getMessage("WSSERVLET11.diag.check.2");
    }

    /**
     * Please check the jaxrpc-ri.xml file to make sure it is present in the war file
     *
     */
    public static String WSSERVLET_11_DIAG_CHECK_2() {
        return localizer.localize(localizableWSSERVLET_11_DIAG_CHECK_2());
    }

    public static Localizable localizableWSSERVLET_11_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET11.diag.check.1");
    }

    /**
     * Please check the sun-jaxws.xml file to make sure it is correct
     *
     */
    public static String WSSERVLET_11_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_11_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_22_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET22.diag.cause.1");
    }

    /**
     * A request was invoked with no endpoint
     *
     */
    public static String WSSERVLET_22_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_22_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_34_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET34.diag.check.1");
    }

    /**
     * Check the log file(s) for more detailed errors/exceptions.
     *
     */
    public static String WSSERVLET_34_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_34_DIAG_CHECK_1());
    }

    public static Localizable localizableERROR_SERVLET_NO_IMPLEMENTOR_FOR_PORT(Object arg0) {
        return messageFactory.getMessage("error.servlet.noImplementorForPort", arg0);
    }

    /**
     * WSSERVLET52: no implementor registered for port: {0}
     *
     */
    public static String ERROR_SERVLET_NO_IMPLEMENTOR_FOR_PORT(Object arg0) {
        return localizer.localize(localizableERROR_SERVLET_NO_IMPLEMENTOR_FOR_PORT(arg0));
    }

    public static Localizable localizableWSSERVLET_64_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET64.diag.check.1");
    }

    /**
     * Make sure the client request is using text/xml
     *
     */
    public static String WSSERVLET_64_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_64_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_18_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET18.diag.check.1");
    }

    /**
     * This may or may not be intentional. If not examine client program for errors.
     *
     */
    public static String WSSERVLET_18_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_18_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_29_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET29.diag.cause.1");
    }

    /**
     * A port is specified, but a corresponding service implementation is not found
     *
     */
    public static String WSSERVLET_29_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_29_DIAG_CAUSE_1());
    }

    public static Localizable localizableSERVLET_ERROR_NO_RESPONSE_MESSAGE() {
        return messageFactory.getMessage("servlet.error.noResponseMessage");
    }

    /**
     * WSSERVLET23: no response message
     *
     */
    public static String SERVLET_ERROR_NO_RESPONSE_MESSAGE() {
        return localizer.localize(localizableSERVLET_ERROR_NO_RESPONSE_MESSAGE());
    }

    public static Localizable localizableSERVLET_HTML_STATUS_ERROR() {
        return messageFactory.getMessage("servlet.html.status.error");
    }

    /**
     * ERROR
     *
     */
    public static String SERVLET_HTML_STATUS_ERROR() {
        return localizer.localize(localizableSERVLET_HTML_STATUS_ERROR());
    }

    public static Localizable localizableLISTENER_INFO_INITIALIZE() {
        return messageFactory.getMessage("listener.info.initialize");
    }

    /**
     * WSSERVLET12: JAX-WS context listener initializing
     *
     */
    public static String LISTENER_INFO_INITIALIZE() {
        return localizer.localize(localizableLISTENER_INFO_INITIALIZE());
    }

    public static Localizable localizableSERVLET_HTML_NO_INFO_AVAILABLE() {
        return messageFactory.getMessage("servlet.html.noInfoAvailable");
    }

    /**
     * <p>No JAX-WS context information available.</p>
     *
     */
    public static String SERVLET_HTML_NO_INFO_AVAILABLE() {
        return localizer.localize(localizableSERVLET_HTML_NO_INFO_AVAILABLE());
    }

    public static Localizable localizableSERVLET_HTML_INFORMATION_TABLE(Object arg0, Object arg1) {
        return messageFactory.getMessage("servlet.html.information.table", arg0, arg1);
    }

    /**
     * <table border="0"><tr><td>Address:</td><td>{0}</td></tr><tr><td>WSDL:</td><td><a href="{0}?wsdl">{0}?wsdl</a></td></tr><tr><td>Implementation class:</td><td>{1}</td></tr></table>
     *
     */
    public static String SERVLET_HTML_INFORMATION_TABLE(Object arg0, Object arg1) {
        return localizer.localize(localizableSERVLET_HTML_INFORMATION_TABLE(arg0, arg1));
    }

    public static Localizable localizableSERVLET_TRACE_WRITING_FAULT_RESPONSE() {
        return messageFactory.getMessage("servlet.trace.writingFaultResponse");
    }

    /**
     * WSSERVLET24: writing fault response
     *
     */
    public static String SERVLET_TRACE_WRITING_FAULT_RESPONSE() {
        return localizer.localize(localizableSERVLET_TRACE_WRITING_FAULT_RESPONSE());
    }

    public static Localizable localizableSERVLET_ERROR_NO_IMPLEMENTOR_FOR_ENDPOINT(Object arg0) {
        return messageFactory.getMessage("servlet.error.noImplementorForEndpoint", arg0);
    }

    /**
     * WSSERVLET20: no implementor for endpoint: {0}
     *
     */
    public static String SERVLET_ERROR_NO_IMPLEMENTOR_FOR_ENDPOINT(Object arg0) {
        return localizer.localize(localizableSERVLET_ERROR_NO_IMPLEMENTOR_FOR_ENDPOINT(arg0));
    }

    public static Localizable localizableWSSERVLET_13_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET13.diag.cause.1");
    }

    /**
     * Context listener shutdown
     *
     */
    public static String WSSERVLET_13_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_13_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_50_DIAG_CAUSE_3() {
        return messageFactory.getMessage("WSSERVLET50.diag.cause.3");
    }

    /**
     * There may some Application Server initialization problems
     *
     */
    public static String WSSERVLET_50_DIAG_CAUSE_3() {
        return localizer.localize(localizableWSSERVLET_50_DIAG_CAUSE_3());
    }

    public static Localizable localizableWSSERVLET_32_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET32.diag.check.1");
    }

    /**
     * Normal Operation.
     *
     */
    public static String WSSERVLET_32_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_32_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_50_DIAG_CAUSE_2() {
        return messageFactory.getMessage("WSSERVLET50.diag.cause.2");
    }

    /**
     * Application server deployment descriptors may be incorrect
     *
     */
    public static String WSSERVLET_50_DIAG_CAUSE_2() {
        return localizer.localize(localizableWSSERVLET_50_DIAG_CAUSE_2());
    }

    public static Localizable localizableWSSERVLET_50_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET50.diag.cause.1");
    }

    /**
     * WS runtime sun-jaxws.xml or web.xml may be incorrect
     *
     */
    public static String WSSERVLET_50_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_50_DIAG_CAUSE_1());
    }

    public static Localizable localizableSERVLET_HTML_STATUS_ACTIVE() {
        return messageFactory.getMessage("servlet.html.status.active");
    }

    /**
     * ACTIVE
     *
     */
    public static String SERVLET_HTML_STATUS_ACTIVE() {
        return localizer.localize(localizableSERVLET_HTML_STATUS_ACTIVE());
    }

    public static Localizable localizableWSSERVLET_25_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET25.diag.check.1");
    }

    /**
     * Tracing message, normal response.
     *
     */
    public static String WSSERVLET_25_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_25_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_43_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET43.diag.cause.1");
    }

    /**
     * Instantiation of the web service failed.
     *
     */
    public static String WSSERVLET_43_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_43_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_27_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET27.diag.cause.1");
    }

    /**
     * Implicit URLS are not supported in this realease
     *
     */
    public static String WSSERVLET_27_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_27_DIAG_CAUSE_1());
    }

    public static Localizable localizableERROR_SERVLET_CAUGHT_THROWABLE_IN_INIT(Object arg0) {
        return messageFactory.getMessage("error.servlet.caughtThrowableInInit", arg0);
    }

    /**
     * WSSERVLET50: caught throwable during servlet initialization: {0}
     *
     */
    public static String ERROR_SERVLET_CAUGHT_THROWABLE_IN_INIT(Object arg0) {
        return localizer.localize(localizableERROR_SERVLET_CAUGHT_THROWABLE_IN_INIT(arg0));
    }

    public static Localizable localizableSERVLET_HTML_ENDPOINT_TABLE(Object arg0, Object arg1) {
        return messageFactory.getMessage("servlet.html.endpoint.table", arg0, arg1);
    }

    /**
     * <table border="0"><tr><td>Service Name:</td><td>{0}</td></tr><tr><td>Port Name:</td><td>{1}</td></tr></table>
     *
     */
    public static String SERVLET_HTML_ENDPOINT_TABLE(Object arg0, Object arg1) {
        return localizer.localize(localizableSERVLET_HTML_ENDPOINT_TABLE(arg0, arg1));
    }

    public static Localizable localizableERROR_SERVLET_CAUGHT_THROWABLE_WHILE_RECOVERING(Object arg0) {
        return messageFactory.getMessage("error.servlet.caughtThrowableWhileRecovering", arg0);
    }

    /**
     * WSSERVLET51: caught throwable while recovering from a previous exception: {0}
     *
     */
    public static String ERROR_SERVLET_CAUGHT_THROWABLE_WHILE_RECOVERING(Object arg0) {
        return localizer.localize(localizableERROR_SERVLET_CAUGHT_THROWABLE_WHILE_RECOVERING(arg0));
    }

    public static Localizable localizableNO_SUNJAXWS_XML(Object arg0) {
        return messageFactory.getMessage("no.sunjaxws.xml", arg0);
    }

    /**
     * Runtime descriptor "{0}" is mising
     *
     */
    public static String NO_SUNJAXWS_XML(Object arg0) {
        return localizer.localize(localizableNO_SUNJAXWS_XML(arg0));
    }

    public static Localizable localizableSERVLET_HTML_TITLE_2() {
        return messageFactory.getMessage("servlet.html.title2");
    }

    /**
     * <h1>Web Services</h1>
     *
     */
    public static String SERVLET_HTML_TITLE_2() {
        return localizer.localize(localizableSERVLET_HTML_TITLE_2());
    }

    public static Localizable localizableLISTENER_INFO_DESTROY() {
        return messageFactory.getMessage("listener.info.destroy");
    }

    /**
     * WSSERVLET13: JAX-WS context listener destroyed
     *
     */
    public static String LISTENER_INFO_DESTROY() {
        return localizer.localize(localizableLISTENER_INFO_DESTROY());
    }

    public static Localizable localizableEXCEPTION_TEMPLATE_CREATION_FAILED() {
        return messageFactory.getMessage("exception.templateCreationFailed");
    }

    /**
     * WSSERVLET35: failed to create a template object
     *
     */
    public static String EXCEPTION_TEMPLATE_CREATION_FAILED() {
        return localizer.localize(localizableEXCEPTION_TEMPLATE_CREATION_FAILED());
    }

    public static Localizable localizableWSSERVLET_20_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET20.diag.cause.1");
    }

    /**
     * Implementation for this service can not be found
     *
     */
    public static String WSSERVLET_20_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_20_DIAG_CAUSE_1());
    }

    public static Localizable localizableTRACE_SERVLET_WRITING_FAULT_RESPONSE() {
        return messageFactory.getMessage("trace.servlet.writingFaultResponse");
    }

    /**
     * WSSERVLET61: writing fault response
     *
     */
    public static String TRACE_SERVLET_WRITING_FAULT_RESPONSE() {
        return localizer.localize(localizableTRACE_SERVLET_WRITING_FAULT_RESPONSE());
    }

    public static Localizable localizableWSSERVLET_23_DIAG_CHECK_2() {
        return messageFactory.getMessage("WSSERVLET23.diag.check.2");
    }

    /**
     * The request may be malformed and be accepted by the service, yet did not generate a response
     *
     */
    public static String WSSERVLET_23_DIAG_CHECK_2() {
        return localizer.localize(localizableWSSERVLET_23_DIAG_CHECK_2());
    }

    public static Localizable localizableWSSERVLET_23_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET23.diag.check.1");
    }

    /**
     * If a response was expected, check that a request message was actually sent
     *
     */
    public static String WSSERVLET_23_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_23_DIAG_CHECK_1());
    }

    public static Localizable localizableSERVLET_WARNING_MISSING_CONTEXT_INFORMATION() {
        return messageFactory.getMessage("servlet.warning.missingContextInformation");
    }

    /**
     * WSSERVLET16: missing context information
     *
     */
    public static String SERVLET_WARNING_MISSING_CONTEXT_INFORMATION() {
        return localizer.localize(localizableSERVLET_WARNING_MISSING_CONTEXT_INFORMATION());
    }

    public static Localizable localizableWSSERVLET_16_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET16.diag.check.1");
    }

    /**
     * Unjar the service war file; check to see that the jaxrpc-ri-runtime.xml file is present
     *
     */
    public static String WSSERVLET_16_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_16_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_34_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET34.diag.cause.1");
    }

    /**
     * The location patching on the wsdl failed when attempting to transform.
     *
     */
    public static String WSSERVLET_34_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_34_DIAG_CAUSE_1());
    }

    public static Localizable localizableHTML_NON_ROOT_PAGE_TITLE() {
        return messageFactory.getMessage("html.nonRootPage.title");
    }

    /**
     * Web Service
     *
     */
    public static String HTML_NON_ROOT_PAGE_TITLE() {
        return localizer.localize(localizableHTML_NON_ROOT_PAGE_TITLE());
    }

    public static Localizable localizableSERVLET_HTML_COLUMN_HEADER_INFORMATION() {
        return messageFactory.getMessage("servlet.html.columnHeader.information");
    }

    /**
     * Information
     *
     */
    public static String SERVLET_HTML_COLUMN_HEADER_INFORMATION() {
        return localizer.localize(localizableSERVLET_HTML_COLUMN_HEADER_INFORMATION());
    }

    public static Localizable localizableWSSERVLET_18_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET18.diag.cause.1");
    }

    /**
     * Message sent by client is empty
     *
     */
    public static String WSSERVLET_18_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_18_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_64_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET64.diag.cause.1");
    }

    /**
     * Web service requests must be a content type text/xml: WSI BP 1.0
     *
     */
    public static String WSSERVLET_64_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_64_DIAG_CAUSE_1());
    }

    public static Localizable localizableINFO_SERVLET_INITIALIZING() {
        return messageFactory.getMessage("info.servlet.initializing");
    }

    /**
     * WSSERVLET56: JAX-WS servlet: init
     *
     */
    public static String INFO_SERVLET_INITIALIZING() {
        return localizer.localize(localizableINFO_SERVLET_INITIALIZING());
    }

    public static Localizable localizableSERVLET_INFO_EMPTY_REQUEST_MESSAGE() {
        return messageFactory.getMessage("servlet.info.emptyRequestMessage");
    }

    /**
     * WSSERVLET18: got empty request message
     *
     */
    public static String SERVLET_INFO_EMPTY_REQUEST_MESSAGE() {
        return localizer.localize(localizableSERVLET_INFO_EMPTY_REQUEST_MESSAGE());
    }

    public static Localizable localizableSERVLET_ERROR_NO_ENDPOINT_SPECIFIED() {
        return messageFactory.getMessage("servlet.error.noEndpointSpecified");
    }

    /**
     * WSSERVLET22: no endpoint specified
     *
     */
    public static String SERVLET_ERROR_NO_ENDPOINT_SPECIFIED() {
        return localizer.localize(localizableSERVLET_ERROR_NO_ENDPOINT_SPECIFIED());
    }

    public static Localizable localizableWSSERVLET_11_DIAG_CAUSE_2() {
        return messageFactory.getMessage("WSSERVLET11.diag.cause.2");
    }

    /**
     * The sun-jaxws.xml runtime deployment descriptor may be missing
     *
     */
    public static String WSSERVLET_11_DIAG_CAUSE_2() {
        return localizer.localize(localizableWSSERVLET_11_DIAG_CAUSE_2());
    }

    public static Localizable localizableWSSERVLET_30_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET30.diag.check.1");
    }

    /**
     * This could be due to a number of causes. Check the server log file for exceptions.
     *
     */
    public static String WSSERVLET_30_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_30_DIAG_CHECK_1());
    }

    public static Localizable localizableEXCEPTION_CANNOT_CREATE_TRANSFORMER() {
        return messageFactory.getMessage("exception.cannotCreateTransformer");
    }

    /**
     * WSSERVLET33: cannot create transformer
     *
     */
    public static String EXCEPTION_CANNOT_CREATE_TRANSFORMER() {
        return localizer.localize(localizableEXCEPTION_CANNOT_CREATE_TRANSFORMER());
    }

    public static Localizable localizableSERVLET_FAULTSTRING_INVALID_SOAP_ACTION() {
        return messageFactory.getMessage("servlet.faultstring.invalidSOAPAction");
    }

    /**
     * WSSERVLET65: Invalid Header SOAPAction required
     *
     */
    public static String SERVLET_FAULTSTRING_INVALID_SOAP_ACTION() {
        return localizer.localize(localizableSERVLET_FAULTSTRING_INVALID_SOAP_ACTION());
    }

    public static Localizable localizableHTML_ROOT_PAGE_BODY_3_B() {
        return messageFactory.getMessage("html.rootPage.body3b");
    }

    /**
     * '>here.</a></p>
     *
     */
    public static String HTML_ROOT_PAGE_BODY_3_B() {
        return localizer.localize(localizableHTML_ROOT_PAGE_BODY_3_B());
    }

    public static Localizable localizableHTML_ROOT_PAGE_BODY_3_A() {
        return messageFactory.getMessage("html.rootPage.body3a");
    }

    /**
     * <p>A WSDL description of these ports is available <a href='
     *
     */
    public static String HTML_ROOT_PAGE_BODY_3_A() {
        return localizer.localize(localizableHTML_ROOT_PAGE_BODY_3_A());
    }

    public static Localizable localizableWSSERVLET_14_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET14.diag.check.1");
    }

    /**
     * Normal Web Service deployment. Deployment of service complete.
     *
     */
    public static String WSSERVLET_14_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_14_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_32_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET32.diag.cause.1");
    }

    /**
     * WSDL being generated
     *
     */
    public static String WSSERVLET_32_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_32_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_25_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET25.diag.cause.1");
    }

    /**
     * SOAPMessage response is being returned to client
     *
     */
    public static String WSSERVLET_25_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_25_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_44_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET44.diag.check.1");
    }

    /**
     * Check the exception for more details. Make sure all the configuration files are correct.
     *
     */
    public static String WSSERVLET_44_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_44_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_28_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET28.diag.check.1");
    }

    /**
     * Set target endpoint with stub.setTargetEndpoint() property.
     *
     */
    public static String WSSERVLET_28_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_28_DIAG_CHECK_1());
    }

    public static Localizable localizableSERVLET_INFO_INITIALIZE() {
        return messageFactory.getMessage("servlet.info.initialize");
    }

    /**
     * WSSERVLET14: JAX-WS servlet initializing
     *
     */
    public static String SERVLET_INFO_INITIALIZE() {
        return localizer.localize(localizableSERVLET_INFO_INITIALIZE());
    }

    public static Localizable localizableERROR_SERVLET_INIT_CONFIG_FILE_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("error.servlet.init.config.fileNotFound", arg0);
    }

    /**
     * WSSERVLET48: config file: "{0}" not found
     *
     */
    public static String ERROR_SERVLET_INIT_CONFIG_FILE_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableERROR_SERVLET_INIT_CONFIG_FILE_NOT_FOUND(arg0));
    }

    public static Localizable localizableHTML_WSDL_PAGE_TITLE() {
        return messageFactory.getMessage("html.wsdlPage.title");
    }

    /**
     * Web Service
     *
     */
    public static String HTML_WSDL_PAGE_TITLE() {
        return localizer.localize(localizableHTML_WSDL_PAGE_TITLE());
    }

    public static Localizable localizableSERVLET_HTML_COLUMN_HEADER_PORT_NAME() {
        return messageFactory.getMessage("servlet.html.columnHeader.portName");
    }

    /**
     * Endpoint
     *
     */
    public static String SERVLET_HTML_COLUMN_HEADER_PORT_NAME() {
        return localizer.localize(localizableSERVLET_HTML_COLUMN_HEADER_PORT_NAME());
    }

    public static Localizable localizableHTML_ROOT_PAGE_BODY_2_B() {
        return messageFactory.getMessage("html.rootPage.body2b");
    }

    /**
     * </p>
     *
     */
    public static String HTML_ROOT_PAGE_BODY_2_B() {
        return localizer.localize(localizableHTML_ROOT_PAGE_BODY_2_B());
    }

    public static Localizable localizableHTML_ROOT_PAGE_BODY_2_A() {
        return messageFactory.getMessage("html.rootPage.body2a");
    }

    /**
     * <p>It supports the following ports:
     *
     */
    public static String HTML_ROOT_PAGE_BODY_2_A() {
        return localizer.localize(localizableHTML_ROOT_PAGE_BODY_2_A());
    }

    public static Localizable localizableWSSERVLET_21_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET21.diag.check.1");
    }

    /**
     * Normal web service invocation.
     *
     */
    public static String WSSERVLET_21_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_21_DIAG_CHECK_1());
    }

    public static Localizable localizableERROR_SERVLET_NO_PORT_SPECIFIED() {
        return messageFactory.getMessage("error.servlet.noPortSpecified");
    }

    /**
     * WSSERVLET53: no port specified in HTTP POST request URL
     *
     */
    public static String ERROR_SERVLET_NO_PORT_SPECIFIED() {
        return localizer.localize(localizableERROR_SERVLET_NO_PORT_SPECIFIED());
    }

    public static Localizable localizableINFO_SERVLET_GOT_EMPTY_REQUEST_MESSAGE() {
        return messageFactory.getMessage("info.servlet.gotEmptyRequestMessage");
    }

    /**
     * WSSERVLET55: got empty request message
     *
     */
    public static String INFO_SERVLET_GOT_EMPTY_REQUEST_MESSAGE() {
        return localizer.localize(localizableINFO_SERVLET_GOT_EMPTY_REQUEST_MESSAGE());
    }

    public static Localizable localizableWSSERVLET_51_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET51.diag.check.1");
    }

    /**
     * Check the server.xml log file for exception information
     *
     */
    public static String WSSERVLET_51_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_51_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_23_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET23.diag.cause.1");
    }

    /**
     * The request generated no response from the service
     *
     */
    public static String WSSERVLET_23_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_23_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_16_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET16.diag.cause.1");
    }

    /**
     * The jaxrpc-ri.xml file may be missing from the war file
     *
     */
    public static String WSSERVLET_16_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_16_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_35_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET35.diag.check.1");
    }

    /**
     * An exception was thrown during creation of the template. View exception and stacktrace for more details.
     *
     */
    public static String WSSERVLET_35_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_35_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_65_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET65.diag.check.1");
    }

    /**
     * Add SOAPAction and appropriate value
     *
     */
    public static String WSSERVLET_65_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_65_DIAG_CHECK_1());
    }

    public static Localizable localizableTRACE_SERVLET_HANDING_REQUEST_OVER_TO_IMPLEMENTOR(Object arg0) {
        return messageFactory.getMessage("trace.servlet.handingRequestOverToImplementor", arg0);
    }

    /**
     * WSSERVLET59: handing request over to implementor: {0}
     *
     */
    public static String TRACE_SERVLET_HANDING_REQUEST_OVER_TO_IMPLEMENTOR(Object arg0) {
        return localizer.localize(localizableTRACE_SERVLET_HANDING_REQUEST_OVER_TO_IMPLEMENTOR(arg0));
    }

    public static Localizable localizableWSSERVLET_19_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET19.diag.check.1");
    }

    /**
     * Informational message only. Normal operation.
     *
     */
    public static String WSSERVLET_19_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_19_DIAG_CHECK_1());
    }

    public static Localizable localizablePUBLISHER_INFO_GENERATING_WSDL(Object arg0) {
        return messageFactory.getMessage("publisher.info.generatingWSDL", arg0);
    }

    /**
     * WSSERVLET32: generating WSDL for endpoint: {0}
     *
     */
    public static String PUBLISHER_INFO_GENERATING_WSDL(Object arg0) {
        return localizer.localize(localizablePUBLISHER_INFO_GENERATING_WSDL(arg0));
    }

    public static Localizable localizableSERVLET_WARNING_DUPLICATE_ENDPOINT_URL_PATTERN(Object arg0) {
        return messageFactory.getMessage("servlet.warning.duplicateEndpointUrlPattern", arg0);
    }

    /**
     * WSSERVLET26: duplicate URL pattern in endpoint: {0}
     *
     */
    public static String SERVLET_WARNING_DUPLICATE_ENDPOINT_URL_PATTERN(Object arg0) {
        return localizer.localize(localizableSERVLET_WARNING_DUPLICATE_ENDPOINT_URL_PATTERN(arg0));
    }

    public static Localizable localizableHTML_NON_ROOT_PAGE_BODY_3_B() {
        return messageFactory.getMessage("html.nonRootPage.body3b");
    }

    /**
     * '>this page</a> for information about the deployed services.</p>
     *
     */
    public static String HTML_NON_ROOT_PAGE_BODY_3_B() {
        return localizer.localize(localizableHTML_NON_ROOT_PAGE_BODY_3_B());
    }

    public static Localizable localizableWSSERVLET_49_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET49.diag.check.1");
    }

    /**
     * Check the server.xml log file for exception information
     *
     */
    public static String WSSERVLET_49_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_49_DIAG_CHECK_1());
    }

    public static Localizable localizableHTML_NON_ROOT_PAGE_BODY_3_A() {
        return messageFactory.getMessage("html.nonRootPage.body3a");
    }

    /**
     * <p>Please refer to <a href='
     *
     */
    public static String HTML_NON_ROOT_PAGE_BODY_3_A() {
        return localizer.localize(localizableHTML_NON_ROOT_PAGE_BODY_3_A());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_REGISTRY_CANNOT_READ_CONFIGURATION() {
        return messageFactory.getMessage("error.implementorRegistry.cannotReadConfiguration");
    }

    /**
     * WSSERVLET39: cannot read configuration
     *
     */
    public static String ERROR_IMPLEMENTOR_REGISTRY_CANNOT_READ_CONFIGURATION() {
        return localizer.localize(localizableERROR_IMPLEMENTOR_REGISTRY_CANNOT_READ_CONFIGURATION());
    }

    public static Localizable localizableTRACE_SERVLET_GOT_RESPONSE_FROM_IMPLEMENTOR(Object arg0) {
        return messageFactory.getMessage("trace.servlet.gotResponseFromImplementor", arg0);
    }

    /**
     * WSSERVLET60: got response from implementor: {0}
     *
     */
    public static String TRACE_SERVLET_GOT_RESPONSE_FROM_IMPLEMENTOR(Object arg0) {
        return localizer.localize(localizableTRACE_SERVLET_GOT_RESPONSE_FROM_IMPLEMENTOR(arg0));
    }

    public static Localizable localizableERROR_IMPLEMENTOR_REGISTRY_INCOMPLETE_INFORMATION() {
        return messageFactory.getMessage("error.implementorRegistry.incompleteInformation");
    }

    /**
     * WSSERVLET41: configuration information is incomplete
     *
     */
    public static String ERROR_IMPLEMENTOR_REGISTRY_INCOMPLETE_INFORMATION() {
        return localizer.localize(localizableERROR_IMPLEMENTOR_REGISTRY_INCOMPLETE_INFORMATION());
    }

    public static Localizable localizableWSSERVLET_12_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET12.diag.check.1");
    }

    /**
     * Normal web service startup
     *
     */
    public static String WSSERVLET_12_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_12_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_30_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET30.diag.cause.1");
    }

    /**
     * There was a server error processing the request
     *
     */
    public static String WSSERVLET_30_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_30_DIAG_CAUSE_1());
    }

    public static Localizable localizableHTML_WSDL_PAGE_NO_WSDL() {
        return messageFactory.getMessage("html.wsdlPage.noWsdl");
    }

    /**
     * <p>No WSDL document available for publishing.</p><p>Please check your deployment information.</p>
     *
     */
    public static String HTML_WSDL_PAGE_NO_WSDL() {
        return localizer.localize(localizableHTML_WSDL_PAGE_NO_WSDL());
    }

    public static Localizable localizableWSSERVLET_14_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET14.diag.cause.1");
    }

    /**
     * Web Services servlet starting up.
     *
     */
    public static String WSSERVLET_14_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_14_DIAG_CAUSE_1());
    }

    public static Localizable localizableINFO_SERVLET_DESTROYING() {
        return messageFactory.getMessage("info.servlet.destroying");
    }

    /**
     * WSSERVLET57: JAX-WS servlet: destroy
     *
     */
    public static String INFO_SERVLET_DESTROYING() {
        return localizer.localize(localizableINFO_SERVLET_DESTROYING());
    }

    public static Localizable localizableERROR_SERVLET_NO_RESPONSE_WAS_PRODUCED() {
        return messageFactory.getMessage("error.servlet.noResponseWasProduced");
    }

    /**
     * WSSERVLET54: no response was produced (internal error)
     *
     */
    public static String ERROR_SERVLET_NO_RESPONSE_WAS_PRODUCED() {
        return localizer.localize(localizableERROR_SERVLET_NO_RESPONSE_WAS_PRODUCED());
    }

    public static Localizable localizableWSSERVLET_26_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET26.diag.check.1");
    }

    /**
     * This may cause a problem, please remove duplicate endpoints
     *
     */
    public static String WSSERVLET_26_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_26_DIAG_CHECK_1());
    }

    public static Localizable localizableSERVLET_HTML_TITLE() {
        return messageFactory.getMessage("servlet.html.title");
    }

    /**
     * Web Services
     *
     */
    public static String SERVLET_HTML_TITLE() {
        return localizer.localize(localizableSERVLET_HTML_TITLE());
    }

    public static Localizable localizableWSSERVLET_44_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET44.diag.cause.1");
    }

    /**
     * The web service was instantiated, however, it could not be initialized
     *
     */
    public static String WSSERVLET_44_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_44_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_63_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET63.diag.check.1");
    }

    /**
     * Make sure that your HTTP client is using POST requests, not GET requests
     *
     */
    public static String WSSERVLET_63_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_63_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_28_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET28.diag.cause.1");
    }

    /**
     * Target endpoint is null
     *
     */
    public static String WSSERVLET_28_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_28_DIAG_CAUSE_1());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_FACTORY_NO_CONFIGURATION() {
        return messageFactory.getMessage("error.implementorFactory.noConfiguration");
    }

    /**
     * WSSERVLET36: no configuration specified
     *
     */
    public static String ERROR_IMPLEMENTOR_FACTORY_NO_CONFIGURATION() {
        return localizer.localize(localizableERROR_IMPLEMENTOR_FACTORY_NO_CONFIGURATION());
    }

    public static Localizable localizableHTML_ROOT_PAGE_BODY_4() {
        return messageFactory.getMessage("html.rootPage.body4");
    }

    /**
     * <p>This endpoint is incorrectly configured. Please check the location and contents of the configuration file.</p>
     *
     */
    public static String HTML_ROOT_PAGE_BODY_4() {
        return localizer.localize(localizableHTML_ROOT_PAGE_BODY_4());
    }

    public static Localizable localizableHTML_ROOT_PAGE_BODY_1() {
        return messageFactory.getMessage("html.rootPage.body1");
    }

    /**
     * <p>A Web Service is installed at this URL.</p>
     *
     */
    public static String HTML_ROOT_PAGE_BODY_1() {
        return localizer.localize(localizableHTML_ROOT_PAGE_BODY_1());
    }

    public static Localizable localizableEXCEPTION_TRANSFORMATION_FAILED(Object arg0) {
        return messageFactory.getMessage("exception.transformationFailed", arg0);
    }

    /**
     * WSSERVLET34: transformation failed : {0}
     *
     */
    public static String EXCEPTION_TRANSFORMATION_FAILED(Object arg0) {
        return localizer.localize(localizableEXCEPTION_TRANSFORMATION_FAILED(arg0));
    }

    public static Localizable localizableSERVLET_HTML_METHOD() {
        return messageFactory.getMessage("servlet.html.method");
    }

    /**
     * WSSERVLET63: must use Post for this type of request
     *
     */
    public static String SERVLET_HTML_METHOD() {
        return localizer.localize(localizableSERVLET_HTML_METHOD());
    }

    public static Localizable localizableSERVLET_FAULTSTRING_MISSING_PORT() {
        return messageFactory.getMessage("servlet.faultstring.missingPort");
    }

    /**
     * WSSERVLET28: Missing port information
     *
     */
    public static String SERVLET_FAULTSTRING_MISSING_PORT() {
        return localizer.localize(localizableSERVLET_FAULTSTRING_MISSING_PORT());
    }

    public static Localizable localizableWSSERVLET_21_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET21.diag.cause.1");
    }

    /**
     * The Web service is being invoked
     *
     */
    public static String WSSERVLET_21_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_21_DIAG_CAUSE_1());
    }

    public static Localizable localizableSERVLET_TRACE_WRITING_SUCCESS_RESPONSE() {
        return messageFactory.getMessage("servlet.trace.writingSuccessResponse");
    }

    /**
     * WSSERVLET25: writing success response
     *
     */
    public static String SERVLET_TRACE_WRITING_SUCCESS_RESPONSE() {
        return localizer.localize(localizableSERVLET_TRACE_WRITING_SUCCESS_RESPONSE());
    }

    public static Localizable localizableWSSERVLET_33_DIAG_CHECK_2() {
        return messageFactory.getMessage("WSSERVLET33.diag.check.2");
    }

    /**
     * There maybe a tranformation engine may not be supported or compatible. Check the server.xml file for exceptions.
     *
     */
    public static String WSSERVLET_33_DIAG_CHECK_2() {
        return localizer.localize(localizableWSSERVLET_33_DIAG_CHECK_2());
    }

    public static Localizable localizableWSSERVLET_33_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET33.diag.check.1");
    }

    /**
     * There maybe a tranformation engine being used that is not compatible. Make sure you are using the correct transformer and version.
     *
     */
    public static String WSSERVLET_33_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_33_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_51_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET51.diag.cause.1");
    }

    /**
     * Service processing of the request generated an exception; while attempting to return a SOAPPFaultMessage a thowable was again generated
     *
     */
    public static String WSSERVLET_51_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_51_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_24_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET24.diag.check.1");
    }

    /**
     * Tracing message fault recorded.
     *
     */
    public static String WSSERVLET_24_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_24_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_17_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET17.diag.check.1");
    }

    /**
     * Note that this may cause problems with service deployment
     *
     */
    public static String WSSERVLET_17_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_17_DIAG_CHECK_1());
    }

    public static Localizable localizableWSSERVLET_35_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET35.diag.cause.1");
    }

    /**
     * A XSLT stylesheet template is create for the wsdl location patching using transformation. Template create failed.
     *
     */
    public static String WSSERVLET_35_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_35_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_19_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET19.diag.cause.1");
    }

    /**
     * Client request for this endpoint arrived
     *
     */
    public static String WSSERVLET_19_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_19_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_65_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET65.diag.cause.1");
    }

    /**
     * SOAP Action is required
     *
     */
    public static String WSSERVLET_65_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_65_DIAG_CAUSE_1());
    }

    public static Localizable localizableLISTENER_PARSING_FAILED(Object arg0) {
        return messageFactory.getMessage("listener.parsingFailed", arg0);
    }

    /**
     * WSSERVLET11: failed to parse runtime descriptor: {0}
     *
     */
    public static String LISTENER_PARSING_FAILED(Object arg0) {
        return localizer.localize(localizableLISTENER_PARSING_FAILED(arg0));
    }

    public static Localizable localizableSERVLET_WARNING_IGNORING_IMPLICIT_URL_PATTERN(Object arg0) {
        return messageFactory.getMessage("servlet.warning.ignoringImplicitUrlPattern", arg0);
    }

    /**
     * WSSERVLET27: unsupported implicit URL pattern in endpoint: {0}
     *
     */
    public static String SERVLET_WARNING_IGNORING_IMPLICIT_URL_PATTERN(Object arg0) {
        return localizer.localize(localizableSERVLET_WARNING_IGNORING_IMPLICIT_URL_PATTERN(arg0));
    }

    public static Localizable localizableWSSERVLET_49_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET49.diag.cause.1");
    }

    /**
     * Service processing of the request generated an exception; while attempting to return a SOAPFaultMessage a thowable was again generated
     *
     */
    public static String WSSERVLET_49_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_49_DIAG_CAUSE_1());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_FACTORY_NEW_INSTANCE_FAILED(Object arg0) {
        return messageFactory.getMessage("error.implementorFactory.newInstanceFailed", arg0);
    }

    /**
     * WSSERVLET43: failed to instantiate service implementor for port "{0}"
     *
     */
    public static String ERROR_IMPLEMENTOR_FACTORY_NEW_INSTANCE_FAILED(Object arg0) {
        return localizer.localize(localizableERROR_IMPLEMENTOR_FACTORY_NEW_INSTANCE_FAILED(arg0));
    }

    public static Localizable localizableWSSERVLET_12_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET12.diag.cause.1");
    }

    /**
     * Context listener starting
     *
     */
    public static String WSSERVLET_12_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_12_DIAG_CAUSE_1());
    }

    public static Localizable localizableWSSERVLET_31_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET31.diag.check.1");
    }

    /**
     * Normal operation
     *
     */
    public static String WSSERVLET_31_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_31_DIAG_CHECK_1());
    }

    public static Localizable localizableSERVLET_FAULTSTRING_INVALID_CONTENT_TYPE() {
        return messageFactory.getMessage("servlet.faultstring.invalidContentType");
    }

    /**
     * WSSERVLET64: Invalid Content-Type, text/xml required
     *
     */
    public static String SERVLET_FAULTSTRING_INVALID_CONTENT_TYPE() {
        return localizer.localize(localizableSERVLET_FAULTSTRING_INVALID_CONTENT_TYPE());
    }

    public static Localizable localizableERROR_SERVLET_CAUGHT_THROWABLE(Object arg0) {
        return messageFactory.getMessage("error.servlet.caughtThrowable", arg0);
    }

    /**
     * WSSERVLET49: caught throwable: {0}
     *
     */
    public static String ERROR_SERVLET_CAUGHT_THROWABLE(Object arg0) {
        return localizer.localize(localizableERROR_SERVLET_CAUGHT_THROWABLE(arg0));
    }

    public static Localizable localizableTRACE_SERVLET_WRITING_SUCCESS_RESPONSE() {
        return messageFactory.getMessage("trace.servlet.writingSuccessResponse");
    }

    /**
     * WSSERVLET62: writing success response
     *
     */
    public static String TRACE_SERVLET_WRITING_SUCCESS_RESPONSE() {
        return localizer.localize(localizableTRACE_SERVLET_WRITING_SUCCESS_RESPONSE());
    }

    public static Localizable localizableERROR_IMPLEMENTOR_REGISTRY_CLASS_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("error.implementorRegistry.classNotFound", arg0);
    }

    /**
     * WSSERVLET40: class not found: {0}
     *
     */
    public static String ERROR_IMPLEMENTOR_REGISTRY_CLASS_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableERROR_IMPLEMENTOR_REGISTRY_CLASS_NOT_FOUND(arg0));
    }

    public static Localizable localizableWSSERVLET_15_DIAG_CHECK_1() {
        return messageFactory.getMessage("WSSERVLET15.diag.check.1");
    }

    /**
     * Normal Web service undeployment. Undeployment complete.
     *
     */
    public static String WSSERVLET_15_DIAG_CHECK_1() {
        return localizer.localize(localizableWSSERVLET_15_DIAG_CHECK_1());
    }

    public static Localizable localizableSERVLET_FAULTSTRING_PORT_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("servlet.faultstring.portNotFound", arg0);
    }

    /**
     * WSSERVLET29: Port not found ({0})
     *
     */
    public static String SERVLET_FAULTSTRING_PORT_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableSERVLET_FAULTSTRING_PORT_NOT_FOUND(arg0));
    }

    public static Localizable localizableSERVLET_INFO_DESTROY() {
        return messageFactory.getMessage("servlet.info.destroy");
    }

    /**
     * WSSERVLET15: JAX-WS servlet destroyed
     *
     */
    public static String SERVLET_INFO_DESTROY() {
        return localizer.localize(localizableSERVLET_INFO_DESTROY());
    }

    public static Localizable localizableSERVLET_FAULTSTRING_INTERNAL_SERVER_ERROR(Object arg0) {
        return messageFactory.getMessage("servlet.faultstring.internalServerError", arg0);
    }

    /**
     * WSSERVLET30: Internal server error ({0})
     *
     */
    public static String SERVLET_FAULTSTRING_INTERNAL_SERVER_ERROR(Object arg0) {
        return localizer.localize(localizableSERVLET_FAULTSTRING_INTERNAL_SERVER_ERROR(arg0));
    }

    public static Localizable localizableWSSERVLET_26_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET26.diag.cause.1");
    }

    /**
     * The endpoint URL is a duplicate
     *
     */
    public static String WSSERVLET_26_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_26_DIAG_CAUSE_1());
    }

    public static Localizable localizableSERVLET_HTML_COLUMN_HEADER_STATUS() {
        return messageFactory.getMessage("servlet.html.columnHeader.status");
    }

    /**
     * Status
     *
     */
    public static String SERVLET_HTML_COLUMN_HEADER_STATUS() {
        return localizer.localize(localizableSERVLET_HTML_COLUMN_HEADER_STATUS());
    }

    public static Localizable localizableWSSERVLET_63_DIAG_CAUSE_1() {
        return messageFactory.getMessage("WSSERVLET63.diag.cause.1");
    }

    /**
     * Web service requests must use HTTP POST method: WSI BP 1.0
     *
     */
    public static String WSSERVLET_63_DIAG_CAUSE_1() {
        return localizer.localize(localizableWSSERVLET_63_DIAG_CAUSE_1());
    }

    public static Localizable localizableSERVLET_WARNING_DUPLICATE_ENDPOINT_NAME() {
        return messageFactory.getMessage("servlet.warning.duplicateEndpointName");
    }

    /**
     * WSSERVLET17: duplicate endpoint name
     *
     */
    public static String SERVLET_WARNING_DUPLICATE_ENDPOINT_NAME() {
        return localizer.localize(localizableSERVLET_WARNING_DUPLICATE_ENDPOINT_NAME());
    }

    public static Localizable localizableTRACE_SERVLET_REQUEST_FOR_PORT_NAMED(Object arg0) {
        return messageFactory.getMessage("trace.servlet.requestForPortNamed", arg0);
    }

    /**
     * WSSERVLET58: got request for port: {0}
     *
     */
    public static String TRACE_SERVLET_REQUEST_FOR_PORT_NAMED(Object arg0) {
        return localizer.localize(localizableTRACE_SERVLET_REQUEST_FOR_PORT_NAMED(arg0));
    }

    public static Localizable localizableSERVLET_NO_ADDRESS_AVAILABLE(Object arg0) {
        return messageFactory.getMessage("servlet.no.address.available", arg0);
    }

    /**
     * No address is available for {0}
     *
     */
    public static String SERVLET_NO_ADDRESS_AVAILABLE(Object arg0) {
        return localizer.localize(localizableSERVLET_NO_ADDRESS_AVAILABLE(arg0));
    }

}
