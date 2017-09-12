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
public final class ManagementMessages {

    private final static String BUNDLE_NAME = "com.sun.xml.internal.ws.resources.management";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new ManagementMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableWSM_1008_EXPECTED_INTEGER_DISPOSE_DELAY_VALUE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSM_1008_EXPECTED_INTEGER_DISPOSE_DELAY_VALUE", arg0);
    }

    /**
     * WSM1008: Expected an integer as value of the endpointDisposeDelay attribute, got this instead: "{0}".
     *
     */
    public static String WSM_1008_EXPECTED_INTEGER_DISPOSE_DELAY_VALUE(Object arg0) {
        return LOCALIZER.localize(localizableWSM_1008_EXPECTED_INTEGER_DISPOSE_DELAY_VALUE(arg0));
    }

    public static Localizable localizableWSM_1003_MANAGEMENT_ASSERTION_MISSING_ID(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSM_1003_MANAGEMENT_ASSERTION_MISSING_ID", arg0);
    }

    /**
     * WSM1003: Policy assertion {0} must have id attribute when management is enabled.
     *
     */
    public static String WSM_1003_MANAGEMENT_ASSERTION_MISSING_ID(Object arg0) {
        return LOCALIZER.localize(localizableWSM_1003_MANAGEMENT_ASSERTION_MISSING_ID(arg0));
    }

    public static Localizable localizableWSM_1005_EXPECTED_COMMUNICATION_CHILD() {
        return MESSAGE_FACTORY.getMessage("WSM_1005_EXPECTED_COMMUNICATION_CHILD");
    }

    /**
     * WSM1005: Expected to find a CommunicationServerImplementation tag as child node of CommunicationServerImplementations.
     *
     */
    public static String WSM_1005_EXPECTED_COMMUNICATION_CHILD() {
        return LOCALIZER.localize(localizableWSM_1005_EXPECTED_COMMUNICATION_CHILD());
    }

    public static Localizable localizableWSM_1006_CLIENT_MANAGEMENT_ENABLED() {
        return MESSAGE_FACTORY.getMessage("WSM_1006_CLIENT_MANAGEMENT_ENABLED");
    }

    /**
     * WSM1006: The management property of the ManagedClient policy assertion is set to on. Clients cannot be managed and this setting will be ignored.
     *
     */
    public static String WSM_1006_CLIENT_MANAGEMENT_ENABLED() {
        return LOCALIZER.localize(localizableWSM_1006_CLIENT_MANAGEMENT_ENABLED());
    }

    public static Localizable localizableWSM_1002_EXPECTED_MANAGEMENT_ASSERTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSM_1002_EXPECTED_MANAGEMENT_ASSERTION", arg0);
    }

    /**
     * WSM1002: Expected policy assertion {0} in this namespace.
     *
     */
    public static String WSM_1002_EXPECTED_MANAGEMENT_ASSERTION(Object arg0) {
        return LOCALIZER.localize(localizableWSM_1002_EXPECTED_MANAGEMENT_ASSERTION(arg0));
    }

    public static Localizable localizableWSM_1001_FAILED_ASSERTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSM_1001_FAILED_ASSERTION", arg0);
    }

    /**
     * WSM1001: Failed to get policy assertion {0}.
     *
     */
    public static String WSM_1001_FAILED_ASSERTION(Object arg0) {
        return LOCALIZER.localize(localizableWSM_1001_FAILED_ASSERTION(arg0));
    }

    public static Localizable localizableWSM_1007_FAILED_MODEL_TRANSLATOR_INSTANTIATION() {
        return MESSAGE_FACTORY.getMessage("WSM_1007_FAILED_MODEL_TRANSLATOR_INSTANTIATION");
    }

    /**
     * WSM1007: Failed to create a ModelTranslator instance.
     *
     */
    public static String WSM_1007_FAILED_MODEL_TRANSLATOR_INSTANTIATION() {
        return LOCALIZER.localize(localizableWSM_1007_FAILED_MODEL_TRANSLATOR_INSTANTIATION());
    }

    public static Localizable localizableWSM_1004_EXPECTED_XML_TAG(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSM_1004_EXPECTED_XML_TAG", arg0, arg1);
    }

    /**
     * WSM1004: Expected tag <{0}> but instead read <{1}>.
     *
     */
    public static String WSM_1004_EXPECTED_XML_TAG(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSM_1004_EXPECTED_XML_TAG(arg0, arg1));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
