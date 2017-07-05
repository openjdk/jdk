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

package com.sun.tools.internal.xjc.reader;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 */
public enum Messages {
    DUPLICATE_PROPERTY, // 1 arg
    DUPLICATE_ELEMENT, // 1 arg

    ERR_UNDECLARED_PREFIX,
    ERR_UNEXPECTED_EXTENSION_BINDING_PREFIXES,
    ERR_UNSUPPORTED_EXTENSION,
    ERR_SUPPORTED_EXTENSION_IGNORED,
    ERR_RELEVANT_LOCATION,
    ERR_CLASS_NOT_FOUND,
    PROPERTY_CLASS_IS_RESERVED,
    ERR_VENDOR_EXTENSION_DISALLOWED_IN_STRICT_MODE,
    ERR_ILLEGAL_CUSTOMIZATION_TAGNAME, // 1 arg
    ERR_PLUGIN_NOT_ENABLED, // 2 args
    ;

    private static final ResourceBundle rb = ResourceBundle.getBundle(Messages.class.getPackage().getName() +".MessageBundle");

    public String toString() {
        return format();
    }

    public String format( Object... args ) {
        return MessageFormat.format( rb.getString(name()), args );
    }
}
