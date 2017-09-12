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

package com.sun.tools.internal.ws.resources;

import java.util.Locale;
import java.util.ResourceBundle;
import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.LocalizableMessageFactory.ResourceBundleSupplier;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class UtilMessages {

    private final static String BUNDLE_NAME = "com.sun.tools.internal.ws.resources.util";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new UtilMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableNULL_NAMESPACE_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("null.namespace.found", arg0);
    }

    /**
     * Encountered error in wsdl. Check namespace of element <{0}>
     *
     */
    public static String NULL_NAMESPACE_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableNULL_NAMESPACE_FOUND(arg0));
    }

    public static Localizable localizableHOLDER_VALUEFIELD_NOT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("holder.valuefield.not.found", arg0);
    }

    /**
     * Could not find the field in the Holder that contains the Holder''s value: {0}
     *
     */
    public static String HOLDER_VALUEFIELD_NOT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableHOLDER_VALUEFIELD_NOT_FOUND(arg0));
    }

    public static Localizable localizableSAX_2_DOM_NOTSUPPORTED_CREATEELEMENT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("sax2dom.notsupported.createelement", arg0);
    }

    /**
     * SAX2DOMEx.DomImplDoesntSupportCreateElementNs: {0}
     *
     */
    public static String SAX_2_DOM_NOTSUPPORTED_CREATEELEMENT(Object arg0) {
        return LOCALIZER.localize(localizableSAX_2_DOM_NOTSUPPORTED_CREATEELEMENT(arg0));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
