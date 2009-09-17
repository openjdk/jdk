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

    public static Localizable localizableGENERATOR_CANT_WRITE(Object arg0) {
        return messageFactory.getMessage("generator.cant.write", arg0);
    }

    /**
     * can''t write file: {0}
     *
     */
    public static String GENERATOR_CANT_WRITE(Object arg0) {
        return localizer.localize(localizableGENERATOR_CANT_WRITE(arg0));
    }

}
