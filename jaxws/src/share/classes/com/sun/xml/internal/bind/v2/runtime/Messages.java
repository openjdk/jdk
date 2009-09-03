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

package com.sun.xml.internal.bind.v2.runtime;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Message resources
 */
enum Messages {
    ILLEGAL_PARAMETER, // 2 args
    UNABLE_TO_FIND_CONVERSION_METHOD, // 3 args
    MISSING_ID, // 1 arg
    NOT_IMPLEMENTED_IN_2_0,
    UNRECOGNIZED_ELEMENT_NAME,
    TYPE_MISMATCH, // 3 args
    MISSING_OBJECT, // 1 arg
    NOT_IDENTIFIABLE, // 0 args
    DANGLING_IDREF, // 1 arg
    NULL_OUTPUT_RESOLVER, // 0 args
    UNABLE_TO_MARSHAL_NON_ELEMENT, // 1 arg
    UNABLE_TO_MARSHAL_UNBOUND_CLASS, // 1 arg
    UNSUPPORTED_PROPERTY, // 1 arg
    NULL_PROPERTY_NAME, // 0 args
    MUST_BE_X, // 3 args
    NOT_MARSHALLABLE, // 0 args
    UNSUPPORTED_RESULT, // 0 args
    UNSUPPORTED_ENCODING, // 1 arg
    SUBSTITUTED_BY_ANONYMOUS_TYPE, // 3 arg
    CYCLE_IN_MARSHALLER, // 1 arg
    UNABLE_TO_DISCOVER_EVENTHANDLER, // 1 arg
    ELEMENT_NEEDED_BUT_FOUND_DOCUMENT, // 1 arg
    UNKNOWN_CLASS, // 1 arg
    FAILED_TO_GENERATE_SCHEMA, // 0 args
    ;

    private static final ResourceBundle rb = ResourceBundle.getBundle(Messages.class.getName());

    public String toString() {
        return format();
    }

    public String format( Object... args ) {
        return MessageFormat.format( rb.getString(name()), args );
    }
}
