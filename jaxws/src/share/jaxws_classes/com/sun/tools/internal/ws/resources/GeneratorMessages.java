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

package com.sun.tools.internal.ws.resources;

import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class GeneratorMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.generator");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableGENERATOR_SERVICE_CLASS_ALREADY_EXIST(Object arg0, Object arg1) {
        return messageFactory.getMessage("generator.service.classAlreadyExist", arg0, arg1);
    }

    /**
     * Could not generate Service, class: {0} already exists. Rename wsdl:Service "{1}" using JAX-WS customization
     *
     */
    public static String GENERATOR_SERVICE_CLASS_ALREADY_EXIST(Object arg0, Object arg1) {
        return localizer.localize(localizableGENERATOR_SERVICE_CLASS_ALREADY_EXIST(arg0, arg1));
    }

    public static Localizable localizableGENERATOR_SEI_CLASS_ALREADY_EXIST(Object arg0, Object arg1) {
        return messageFactory.getMessage("generator.sei.classAlreadyExist", arg0, arg1);
    }

    /**
     * Could not generate SEI, class: {0} already exists. Rename wsdl:portType "{1}" using JAX-WS customization
     *
     */
    public static String GENERATOR_SEI_CLASS_ALREADY_EXIST(Object arg0, Object arg1) {
        return localizer.localize(localizableGENERATOR_SEI_CLASS_ALREADY_EXIST(arg0, arg1));
    }

    public static Localizable localizableGENERATOR_NESTED_GENERATOR_ERROR(Object arg0) {
        return messageFactory.getMessage("generator.nestedGeneratorError", arg0);
    }

    /**
     * generator error: {0}
     *
     */
    public static String GENERATOR_NESTED_GENERATOR_ERROR(Object arg0) {
        return localizer.localize(localizableGENERATOR_NESTED_GENERATOR_ERROR(arg0));
    }

    public static Localizable localizableGENERATOR_INTERNAL_ERROR_SHOULD_NOT_HAPPEN(Object arg0) {
        return messageFactory.getMessage("generator.internal.error.should.not.happen", arg0);
    }

    /**
     * internal error (should not happen): {0}
     *
     */
    public static String GENERATOR_INTERNAL_ERROR_SHOULD_NOT_HAPPEN(Object arg0) {
        return localizer.localize(localizableGENERATOR_INTERNAL_ERROR_SHOULD_NOT_HAPPEN(arg0));
    }

    public static Localizable localizableGENERATOR_INDENTINGWRITER_CHARSET_CANTENCODE(Object arg0) {
        return messageFactory.getMessage("generator.indentingwriter.charset.cantencode", arg0);
    }

    /**
     * WSDL has some characters which native java encoder can''t encode: "{0}"
     *
     */
    public static String GENERATOR_INDENTINGWRITER_CHARSET_CANTENCODE(Object arg0) {
        return localizer.localize(localizableGENERATOR_INDENTINGWRITER_CHARSET_CANTENCODE(arg0));
    }

    public static Localizable localizableGENERATOR_CANNOT_CREATE_DIR(Object arg0) {
        return messageFactory.getMessage("generator.cannot.create.dir", arg0);
    }

    /**
     * can''t create directory: {0}
     *
     */
    public static String GENERATOR_CANNOT_CREATE_DIR(Object arg0) {
        return localizer.localize(localizableGENERATOR_CANNOT_CREATE_DIR(arg0));
    }

}
