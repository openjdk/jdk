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
public final class UtilMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.util");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableUTIL_LOCATION(Object arg0, Object arg1) {
        return messageFactory.getMessage("util.location", arg0, arg1);
    }

    /**
     * at line {0} of {1}
     *
     */
    public static String UTIL_LOCATION(Object arg0, Object arg1) {
        return localizer.localize(localizableUTIL_LOCATION(arg0, arg1));
    }

    public static Localizable localizableUTIL_FAILED_TO_PARSE_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return messageFactory.getMessage("util.failed.to.parse.handlerchain.file", arg0, arg1);
    }

    /**
     * Could not parse handler chain file {1} for class {0}
     *
     */
    public static String UTIL_FAILED_TO_PARSE_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return localizer.localize(localizableUTIL_FAILED_TO_PARSE_HANDLERCHAIN_FILE(arg0, arg1));
    }

    public static Localizable localizableUTIL_PARSER_WRONG_ELEMENT(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("util.parser.wrong.element", arg0, arg1, arg2);
    }

    /**
     * found element "{1}", expected "{2}" in handler chain configuration (line {0})
     *
     */
    public static String UTIL_PARSER_WRONG_ELEMENT(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableUTIL_PARSER_WRONG_ELEMENT(arg0, arg1, arg2));
    }

    public static Localizable localizableUTIL_HANDLER_CLASS_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("util.handler.class.not.found", arg0);
    }

    /**
     * "Class: {0} could not be found"
     *
     */
    public static String UTIL_HANDLER_CLASS_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableUTIL_HANDLER_CLASS_NOT_FOUND(arg0));
    }

    public static Localizable localizableUTIL_HANDLER_ENDPOINT_INTERFACE_NO_WEBSERVICE(Object arg0) {
        return messageFactory.getMessage("util.handler.endpoint.interface.no.webservice", arg0);
    }

    /**
     * "The Endpoint Interface: {0} does not have WebService Annotation"
     *
     */
    public static String UTIL_HANDLER_ENDPOINT_INTERFACE_NO_WEBSERVICE(Object arg0) {
        return localizer.localize(localizableUTIL_HANDLER_ENDPOINT_INTERFACE_NO_WEBSERVICE(arg0));
    }

    public static Localizable localizableUTIL_HANDLER_NO_WEBSERVICE_ANNOTATION(Object arg0) {
        return messageFactory.getMessage("util.handler.no.webservice.annotation", arg0);
    }

    /**
     * "A WebService annotation is not present on class: {0}"
     *
     */
    public static String UTIL_HANDLER_NO_WEBSERVICE_ANNOTATION(Object arg0) {
        return localizer.localize(localizableUTIL_HANDLER_NO_WEBSERVICE_ANNOTATION(arg0));
    }

    public static Localizable localizableUTIL_FAILED_TO_FIND_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return messageFactory.getMessage("util.failed.to.find.handlerchain.file", arg0, arg1);
    }

    /**
     * Could not find handler chain file {1} for class {0}
     *
     */
    public static String UTIL_FAILED_TO_FIND_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return localizer.localize(localizableUTIL_FAILED_TO_FIND_HANDLERCHAIN_FILE(arg0, arg1));
    }

    public static Localizable localizableUTIL_HANDLER_CANNOT_COMBINE_SOAPMESSAGEHANDLERS() {
        return messageFactory.getMessage("util.handler.cannot.combine.soapmessagehandlers");
    }

    /**
     * You must use HanlderChain annotation, not SOAPMessageHandlers
     *
     */
    public static String UTIL_HANDLER_CANNOT_COMBINE_SOAPMESSAGEHANDLERS() {
        return localizer.localize(localizableUTIL_HANDLER_CANNOT_COMBINE_SOAPMESSAGEHANDLERS());
    }

}
