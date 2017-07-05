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
public final class SoapMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.soap");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableSOAP_FAULT_CREATE_ERR(Object arg0) {
        return messageFactory.getMessage("soap.fault.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP Fault due to exception: {0}
     *
     */
    public static String SOAP_FAULT_CREATE_ERR(Object arg0) {
        return localizer.localize(localizableSOAP_FAULT_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_MSG_FACTORY_CREATE_ERR(Object arg0) {
        return messageFactory.getMessage("soap.msg.factory.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP message factory due to exception: {0}
     *
     */
    public static String SOAP_MSG_FACTORY_CREATE_ERR(Object arg0) {
        return localizer.localize(localizableSOAP_MSG_FACTORY_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_MSG_CREATE_ERR(Object arg0) {
        return messageFactory.getMessage("soap.msg.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP message due to exception: {0}
     *
     */
    public static String SOAP_MSG_CREATE_ERR(Object arg0) {
        return localizer.localize(localizableSOAP_MSG_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_FACTORY_CREATE_ERR(Object arg0) {
        return messageFactory.getMessage("soap.factory.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP factory due to exception: {0}
     *
     */
    public static String SOAP_FACTORY_CREATE_ERR(Object arg0) {
        return localizer.localize(localizableSOAP_FACTORY_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_PROTOCOL_INVALID_FAULT_CODE(Object arg0) {
        return messageFactory.getMessage("soap.protocol.invalidFaultCode", arg0);
    }

    /**
     * Invalid fault code: {0}
     *
     */
    public static String SOAP_PROTOCOL_INVALID_FAULT_CODE(Object arg0) {
        return localizer.localize(localizableSOAP_PROTOCOL_INVALID_FAULT_CODE(arg0));
    }

    public static Localizable localizableSOAP_VERSION_MISMATCH_ERR(Object arg0, Object arg1) {
        return messageFactory.getMessage("soap.version.mismatch.err", arg0, arg1);
    }

    /**
     * Couldn''t create SOAP message. Expecting Envelope in namespace {0}, but got {1}
     *
     */
    public static String SOAP_VERSION_MISMATCH_ERR(Object arg0, Object arg1) {
        return localizer.localize(localizableSOAP_VERSION_MISMATCH_ERR(arg0, arg1));
    }

}
