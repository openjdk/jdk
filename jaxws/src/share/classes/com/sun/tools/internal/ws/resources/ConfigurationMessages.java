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
package com.sun.tools.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class ConfigurationMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.configuration");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableCONFIGURATION_INVALID_ELEMENT(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("configuration.invalidElement", arg0, arg1, arg2);
    }

    /**
     * invalid element "{2}" in file "{0}" (line {1})
     *
     */
    public static String CONFIGURATION_INVALID_ELEMENT(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableCONFIGURATION_INVALID_ELEMENT(arg0, arg1, arg2));
    }

    public static Localizable localizableCONFIGURATION_NOT_BINDING_FILE(Object arg0) {
        return messageFactory.getMessage("configuration.notBindingFile", arg0);
    }

    /**
     * Ignoring: binding file ""{0}". It is not a jaxws or a jaxb binding file.
     *
     */
    public static String CONFIGURATION_NOT_BINDING_FILE(Object arg0) {
        return localizer.localize(localizableCONFIGURATION_NOT_BINDING_FILE(arg0));
    }

}
