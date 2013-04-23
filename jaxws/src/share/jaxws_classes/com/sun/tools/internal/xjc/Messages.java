/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 */
public class Messages
{
    /** Loads a string resource and formats it with specified arguments. */
    public static String format( String property, Object... args ) {
        String text = ResourceBundle.getBundle(Messages.class.getPackage().getName() +".MessageBundle").getString(property);
        return MessageFormat.format(text,args);
    }

//
//
// Message resources
//
//
    static final String UNKNOWN_LOCATION = // 0 args
        "ConsoleErrorReporter.UnknownLocation";

    static final String LINE_X_OF_Y = // 2 args
        "ConsoleErrorReporter.LineXOfY";

    static final String UNKNOWN_FILE = // 0 args
        "ConsoleErrorReporter.UnknownFile";

    static final String DRIVER_PUBLIC_USAGE = // 0 args
        "Driver.Public.Usage";

    static final String DRIVER_PRIVATE_USAGE = // 0 args
        "Driver.Private.Usage";

    static final String ADDON_USAGE = // 0 args
        "Driver.AddonUsage";

    static final String EXPERIMENTAL_LANGUAGE_WARNING = // 2 arg
        "Driver.ExperimentalLanguageWarning";

    static final String NON_EXISTENT_DIR = // 1 arg
        "Driver.NonExistentDir";

    // Usage not found. TODO Remove
    // static final String MISSING_RUNTIME_PACKAGENAME = // 0 args
    //     "Driver.MissingRuntimePackageName";

    static final String MISSING_MODE_OPERAND = // 0 args
            "Driver.MissingModeOperand";

    // Usage not found. TODO Remove
    // static final String MISSING_COMPATIBILITY_OPERAND = // 0 args
    //     "Driver.MissingCompatibilityOperand";

    static final String MISSING_PROXY = // 0 args
        "Driver.MISSING_PROXY";

    static final String MISSING_PROXYFILE = // 0 args
        "Driver.MISSING_PROXYFILE";

    static final String NO_SUCH_FILE = // 1 arg
        "Driver.NO_SUCH_FILE";

    static final String ILLEGAL_PROXY = // 1 arg
        "Driver.ILLEGAL_PROXY";

    static final String ILLEGAL_TARGET_VERSION = // 1 arg
        "Driver.ILLEGAL_TARGET_VERSION";

    static final String MISSING_OPERAND = // 1 arg
        "Driver.MissingOperand";

    static final String MISSING_PROXYHOST = // 0 args
        "Driver.MissingProxyHost";

    static final String MISSING_PROXYPORT = // 0 args
        "Driver.MissingProxyPort";

    static final String STACK_OVERFLOW = // 0 arg
        "Driver.StackOverflow";

    static final String UNRECOGNIZED_MODE = // 1 arg
        "Driver.UnrecognizedMode";

    static final String UNRECOGNIZED_PARAMETER = // 1 arg
        "Driver.UnrecognizedParameter";

    static final String UNSUPPORTED_ENCODING = // 1 arg
            "Driver.UnsupportedEncoding";

    static final String MISSING_GRAMMAR = // 0 args
        "Driver.MissingGrammar";

    static final String PARSING_SCHEMA = // 0 args
        "Driver.ParsingSchema";

    static final String PARSE_FAILED = // 0 args
        "Driver.ParseFailed";

    static final String COMPILING_SCHEMA = // 0 args
        "Driver.CompilingSchema";

    static final String FAILED_TO_GENERATE_CODE = // 0 args
        "Driver.FailedToGenerateCode";

    static final String FILE_PROLOG_COMMENT = // 1 arg
        "Driver.FilePrologComment";

    static final String DATE_FORMAT = // 0 args
        "Driver.DateFormat";

    static final String TIME_FORMAT = // 0 args
        "Driver.TimeFormat";

    static final String AT = // 0 args
        "Driver.At";

    static final String VERSION = // 0 args
        "Driver.Version";

    static final String FULLVERSION = // 0 args
        "Driver.FullVersion";

    static final String BUILD_ID = // 0 args
        "Driver.BuildID";

    static final String ERROR_MSG = // 1:arg
        "Driver.ErrorMessage";

    static final String WARNING_MSG = // 1:arg
        "Driver.WarningMessage";

    static final String INFO_MSG = // 1:arg
        "Driver.InfoMessage";

    static final String ERR_NOT_A_BINDING_FILE = // 2 arg
        "Driver.NotABindingFile";

    static final String ERR_TOO_MANY_SCHEMA = // 0 args
        "ModelLoader.TooManySchema";

    static final String ERR_BINDING_FILE_NOT_SUPPORTED_FOR_RNC = // 0 args
        "ModelLoader.BindingFileNotSupportedForRNC";

    static final String DEFAULT_VERSION = // 0 args
        "Driver.DefaultVersion";

    static final String DEFAULT_PACKAGE_WARNING = // 0 args
        "Driver.DefaultPackageWarning";

    static final String NOT_A_VALID_FILENAME = // 2 args
        "Driver.NotAValidFileName";
    static final String FAILED_TO_PARSE = // 2 args
        "Driver.FailedToParse";
    static final String NOT_A_FILE_NOR_URL = // 1 arg
        "Driver.NotAFileNorURL";

    static final String FIELD_RENDERER_CONFLICT = // 2 args
        "FIELD_RENDERER_CONFLICT";

    static final String NAME_CONVERTER_CONFLICT = // 2 args
        "NAME_CONVERTER_CONFLICT";
    static final String FAILED_TO_LOAD = // 2 args
        "FAILED_TO_LOAD";

    static final String PLUGIN_LOAD_FAILURE = // 1 arg
        "PLUGIN_LOAD_FAILURE";
}
