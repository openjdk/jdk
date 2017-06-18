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
public final class SoapMessages {

    private final static String BUNDLE_NAME = "com.sun.xml.internal.ws.resources.soap";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new SoapMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableSOAP_FAULT_CREATE_ERR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("soap.fault.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP Fault due to exception: {0}
     *
     */
    public static String SOAP_FAULT_CREATE_ERR(Object arg0) {
        return LOCALIZER.localize(localizableSOAP_FAULT_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_PROTOCOL_INVALID_FAULT_CODE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("soap.protocol.invalidFaultCode", arg0);
    }

    /**
     * Invalid fault code: {0}
     *
     */
    public static String SOAP_PROTOCOL_INVALID_FAULT_CODE(Object arg0) {
        return LOCALIZER.localize(localizableSOAP_PROTOCOL_INVALID_FAULT_CODE(arg0));
    }

    public static Localizable localizableSOAP_VERSION_MISMATCH_ERR(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("soap.version.mismatch.err", arg0, arg1);
    }

    /**
     * Couldn''t create SOAP message. Expecting Envelope in namespace {0}, but got {1}
     *
     */
    public static String SOAP_VERSION_MISMATCH_ERR(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableSOAP_VERSION_MISMATCH_ERR(arg0, arg1));
    }

    public static Localizable localizableSOAP_MSG_FACTORY_CREATE_ERR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("soap.msg.factory.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP message factory due to exception: {0}
     *
     */
    public static String SOAP_MSG_FACTORY_CREATE_ERR(Object arg0) {
        return LOCALIZER.localize(localizableSOAP_MSG_FACTORY_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_MSG_CREATE_ERR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("soap.msg.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP message due to exception: {0}
     *
     */
    public static String SOAP_MSG_CREATE_ERR(Object arg0) {
        return LOCALIZER.localize(localizableSOAP_MSG_CREATE_ERR(arg0));
    }

    public static Localizable localizableSOAP_FACTORY_CREATE_ERR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("soap.factory.create.err", arg0);
    }

    /**
     * Couldn''t create SOAP factory due to exception: {0}
     *
     */
    public static String SOAP_FACTORY_CREATE_ERR(Object arg0) {
        return LOCALIZER.localize(localizableSOAP_FACTORY_CREATE_ERR(arg0));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
