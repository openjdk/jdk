/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class WsdlmodelMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.wsdlmodel");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableWSDL_PORTADDRESS_EPRADDRESS_NOT_MATCH(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("wsdl.portaddress.epraddress.not.match", arg0, arg1, arg2);
    }

    /**
     * For Port: {0}, service location {1} does not match address {2} in the EndpointReference
     *
     */
    public static String WSDL_PORTADDRESS_EPRADDRESS_NOT_MATCH(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWSDL_PORTADDRESS_EPRADDRESS_NOT_MATCH(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDL_IMPORT_SHOULD_BE_WSDL(Object arg0) {
        return messageFactory.getMessage("wsdl.import.should.be.wsdl", arg0);
    }

    /**
     * Import of {0} is violation of BP 1.1 R2001. Proceeding with a warning.
     * R2001 A DESCRIPTION must only use the WSDL "import" statement to import another WSDL description.
     *
     */
    public static String WSDL_IMPORT_SHOULD_BE_WSDL(Object arg0) {
        return localizer.localize(localizableWSDL_IMPORT_SHOULD_BE_WSDL(arg0));
    }

    public static Localizable localizableMEX_METADATA_SYSTEMID_NULL() {
        return messageFactory.getMessage("Mex.metadata.systemid.null");
    }

    /**
     * MEX WSDL metadata can not be parsed, the systemId is of the MEX source is null.
     *
     */
    public static String MEX_METADATA_SYSTEMID_NULL() {
        return localizer.localize(localizableMEX_METADATA_SYSTEMID_NULL());
    }

}
