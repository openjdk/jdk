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
public final class TubelineassemblyMessages {

    private final static String BUNDLE_NAME = "com.sun.xml.internal.ws.resources.tubelineassembly";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new TubelineassemblyMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableMASM_0001_DEFAULT_CFG_FILE_NOT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0001_DEFAULT_CFG_FILE_NOT_FOUND", arg0);
    }

    /**
     * MASM0001: Default configuration file [ {0} ] was not found
     *
     */
    public static String MASM_0001_DEFAULT_CFG_FILE_NOT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0001_DEFAULT_CFG_FILE_NOT_FOUND(arg0));
    }

    public static Localizable localizableMASM_0011_LOADING_RESOURCE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("MASM0011_LOADING_RESOURCE", arg0, arg1);
    }

    /**
     * MASM0011: Trying to load [ {0} ] via parent resouce loader [ {1} ]
     *
     */
    public static String MASM_0011_LOADING_RESOURCE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMASM_0011_LOADING_RESOURCE(arg0, arg1));
    }

    public static Localizable localizableMASM_0012_LOADING_VIA_SERVLET_CONTEXT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("MASM0012_LOADING_VIA_SERVLET_CONTEXT", arg0, arg1);
    }

    /**
     * MASM0012: Trying to load [ {0} ] via servlet context [ {1} ]
     *
     */
    public static String MASM_0012_LOADING_VIA_SERVLET_CONTEXT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMASM_0012_LOADING_VIA_SERVLET_CONTEXT(arg0, arg1));
    }

    public static Localizable localizableMASM_0002_DEFAULT_CFG_FILE_LOCATED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("MASM0002_DEFAULT_CFG_FILE_LOCATED", arg0, arg1);
    }

    /**
     * MASM0002: Default [ {0} ] configuration file located at [ {1} ]
     *
     */
    public static String MASM_0002_DEFAULT_CFG_FILE_LOCATED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMASM_0002_DEFAULT_CFG_FILE_LOCATED(arg0, arg1));
    }

    public static Localizable localizableMASM_0007_APP_CFG_FILE_NOT_FOUND() {
        return MESSAGE_FACTORY.getMessage("MASM0007_APP_CFG_FILE_NOT_FOUND");
    }

    /**
     * MASM0007: No application metro.xml configuration file found.
     *
     */
    public static String MASM_0007_APP_CFG_FILE_NOT_FOUND() {
        return LOCALIZER.localize(localizableMASM_0007_APP_CFG_FILE_NOT_FOUND());
    }

    public static Localizable localizableMASM_0006_APP_CFG_FILE_LOCATED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0006_APP_CFG_FILE_LOCATED", arg0);
    }

    /**
     * MASM0006: Application metro.xml configuration file located at [ {0} ]
     *
     */
    public static String MASM_0006_APP_CFG_FILE_LOCATED(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0006_APP_CFG_FILE_LOCATED(arg0));
    }

    public static Localizable localizableMASM_0018_MSG_LOGGING_SYSTEM_PROPERTY_SET_TO_VALUE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("MASM0018_MSG_LOGGING_SYSTEM_PROPERTY_SET_TO_VALUE", arg0, arg1);
    }

    /**
     * MASM0018: Message logging {0} system property detected to be set to value {1}
     *
     */
    public static String MASM_0018_MSG_LOGGING_SYSTEM_PROPERTY_SET_TO_VALUE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMASM_0018_MSG_LOGGING_SYSTEM_PROPERTY_SET_TO_VALUE(arg0, arg1));
    }

    public static Localizable localizableMASM_0003_DEFAULT_CFG_FILE_NOT_LOADED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0003_DEFAULT_CFG_FILE_NOT_LOADED", arg0);
    }

    /**
     * MASM0003: Default [ {0} ] configuration file was not loaded
     *
     */
    public static String MASM_0003_DEFAULT_CFG_FILE_NOT_LOADED(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0003_DEFAULT_CFG_FILE_NOT_LOADED(arg0));
    }

    public static Localizable localizableMASM_0004_NO_TUBELINES_SECTION_IN_DEFAULT_CFG_FILE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0004_NO_TUBELINES_SECTION_IN_DEFAULT_CFG_FILE", arg0);
    }

    /**
     * MASM0004: No <tubelines> section found in the default [ {0} ] configuration file
     *
     */
    public static String MASM_0004_NO_TUBELINES_SECTION_IN_DEFAULT_CFG_FILE(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0004_NO_TUBELINES_SECTION_IN_DEFAULT_CFG_FILE(arg0));
    }

    public static Localizable localizableMASM_0015_CLASS_DOES_NOT_IMPLEMENT_INTERFACE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("MASM0015_CLASS_DOES_NOT_IMPLEMENT_INTERFACE", arg0, arg1);
    }

    /**
     * MASM0015: Class [ {0} ] does not implement [ {1} ] interface
     *
     */
    public static String MASM_0015_CLASS_DOES_NOT_IMPLEMENT_INTERFACE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMASM_0015_CLASS_DOES_NOT_IMPLEMENT_INTERFACE(arg0, arg1));
    }

    public static Localizable localizableMASM_0020_ERROR_CREATING_URI_FROM_GENERATED_STRING(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0020_ERROR_CREATING_URI_FROM_GENERATED_STRING", arg0);
    }

    /**
     * MASM0020: Unable to create a new URI instance for generated endpoint URI string [ {0} ]
     *
     */
    public static String MASM_0020_ERROR_CREATING_URI_FROM_GENERATED_STRING(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0020_ERROR_CREATING_URI_FROM_GENERATED_STRING(arg0));
    }

    public static Localizable localizableMASM_0008_INVALID_URI_REFERENCE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0008_INVALID_URI_REFERENCE", arg0);
    }

    /**
     * MASM0008: Invalid URI reference [ {0} ]
     *
     */
    public static String MASM_0008_INVALID_URI_REFERENCE(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0008_INVALID_URI_REFERENCE(arg0));
    }

    public static Localizable localizableMASM_0016_UNABLE_TO_INSTANTIATE_TUBE_FACTORY(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0016_UNABLE_TO_INSTANTIATE_TUBE_FACTORY", arg0);
    }

    /**
     * MASM0016: Unable to instantiate Tube factory class [ {0} ]
     *
     */
    public static String MASM_0016_UNABLE_TO_INSTANTIATE_TUBE_FACTORY(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0016_UNABLE_TO_INSTANTIATE_TUBE_FACTORY(arg0));
    }

    public static Localizable localizableMASM_0010_ERROR_READING_CFG_FILE_FROM_LOCATION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0010_ERROR_READING_CFG_FILE_FROM_LOCATION", arg0);
    }

    /**
     * MASM0010: Unable to unmarshall metro config file from location [ {0} ]
     *
     */
    public static String MASM_0010_ERROR_READING_CFG_FILE_FROM_LOCATION(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0010_ERROR_READING_CFG_FILE_FROM_LOCATION(arg0));
    }

    public static Localizable localizableMASM_0005_NO_DEFAULT_TUBELINE_IN_DEFAULT_CFG_FILE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0005_NO_DEFAULT_TUBELINE_IN_DEFAULT_CFG_FILE", arg0);
    }

    /**
     * MASM0005: No default tubeline is defined in the default [ {0} ] configuration file
     *
     */
    public static String MASM_0005_NO_DEFAULT_TUBELINE_IN_DEFAULT_CFG_FILE(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0005_NO_DEFAULT_TUBELINE_IN_DEFAULT_CFG_FILE(arg0));
    }

    public static Localizable localizableMASM_0014_UNABLE_TO_LOAD_CLASS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0014_UNABLE_TO_LOAD_CLASS", arg0);
    }

    /**
     * MASM0014: Unable to load [ {0} ] class
     *
     */
    public static String MASM_0014_UNABLE_TO_LOAD_CLASS(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0014_UNABLE_TO_LOAD_CLASS(arg0));
    }

    public static Localizable localizableMASM_0013_ERROR_INVOKING_SERVLET_CONTEXT_METHOD(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0013_ERROR_INVOKING_SERVLET_CONTEXT_METHOD", arg0);
    }

    /**
     * MASM0013: Unable to invoke {0} method on servlet context instance
     *
     */
    public static String MASM_0013_ERROR_INVOKING_SERVLET_CONTEXT_METHOD(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0013_ERROR_INVOKING_SERVLET_CONTEXT_METHOD(arg0));
    }

    public static Localizable localizableMASM_0019_MSG_LOGGING_SYSTEM_PROPERTY_ILLEGAL_VALUE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("MASM0019_MSG_LOGGING_SYSTEM_PROPERTY_ILLEGAL_VALUE", arg0, arg1);
    }

    /**
     * MASM0019: Illegal logging level value "{1}" stored in the {0} message logging system property. Using default logging level.
     *
     */
    public static String MASM_0019_MSG_LOGGING_SYSTEM_PROPERTY_ILLEGAL_VALUE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMASM_0019_MSG_LOGGING_SYSTEM_PROPERTY_ILLEGAL_VALUE(arg0, arg1));
    }

    public static Localizable localizableMASM_0009_CANNOT_FORM_VALID_URL(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0009_CANNOT_FORM_VALID_URL", arg0);
    }

    /**
     * MASM0009: Cannot form a valid URL from the resource name "{0}". For more details see the nested exception.
     *
     */
    public static String MASM_0009_CANNOT_FORM_VALID_URL(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0009_CANNOT_FORM_VALID_URL(arg0));
    }

    public static Localizable localizableMASM_0017_UNABLE_TO_LOAD_TUBE_FACTORY_CLASS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("MASM0017_UNABLE_TO_LOAD_TUBE_FACTORY_CLASS", arg0);
    }

    /**
     * MASM0017: Unable to load Tube factory class [ {0} ]
     *
     */
    public static String MASM_0017_UNABLE_TO_LOAD_TUBE_FACTORY_CLASS(Object arg0) {
        return LOCALIZER.localize(localizableMASM_0017_UNABLE_TO_LOAD_TUBE_FACTORY_CLASS(arg0));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
