/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.helpers;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 */
class Messages
{
    static String format( String property ) {
        return format( property, null );
    }

    static String format( String property, Object arg1 ) {
        return format( property, new Object[]{arg1} );
    }

    static String format( String property, Object arg1, Object arg2 ) {
        return format( property, new Object[]{arg1,arg2} );
    }

    static String format( String property, Object arg1, Object arg2, Object arg3 ) {
        return format( property, new Object[]{arg1,arg2,arg3} );
    }

    // add more if necessary.

    /** Loads a string resource and formats it with specified arguments. */
    static String format( String property, Object[] args ) {
        String text = ResourceBundle.getBundle(Messages.class.getName()).getString(property);
        return MessageFormat.format(text,args);
    }

//
//
// Message resources
//
//
    static final String INPUTSTREAM_NOT_NULL = // 0 args
        "AbstractUnmarshallerImpl.ISNotNull";

    static final String MUST_BE_BOOLEAN = // 1 arg
        "AbstractMarshallerImpl.MustBeBoolean";

    static final String MUST_BE_STRING = // 1 arg
        "AbstractMarshallerImpl.MustBeString";

    static final String SEVERITY_MESSAGE = // 3 args
        "DefaultValidationEventHandler.SeverityMessage";

    static final String LOCATION_UNAVAILABLE = // 0 args
        "DefaultValidationEventHandler.LocationUnavailable";

    static final String UNRECOGNIZED_SEVERITY = // 1 arg
        "DefaultValidationEventHandler.UnrecognizedSeverity";

    static final String WARNING = // 0 args
        "DefaultValidationEventHandler.Warning";

    static final String ERROR = // 0 args
        "DefaultValidationEventHandler.Error";

    static final String FATAL_ERROR = // 0 args
        "DefaultValidationEventHandler.FatalError";

    static final String ILLEGAL_SEVERITY = // 0 args
        "ValidationEventImpl.IllegalSeverity";

    static final String MUST_NOT_BE_NULL = // 1 arg
        "Shared.MustNotBeNull";
}
