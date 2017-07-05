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

package com.sun.xml.internal.bind.unmarshaller;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 *
 * @since JAXB1.0
 */
public class Messages
{
    public static String format( String property ) {
        return format( property, null );
    }

    public static String format( String property, Object arg1 ) {
        return format( property, new Object[]{arg1} );
    }

    public static String format( String property, Object arg1, Object arg2 ) {
        return format( property, new Object[]{arg1,arg2} );
    }

    public static String format( String property, Object arg1, Object arg2, Object arg3 ) {
        return format( property, new Object[]{arg1,arg2,arg3} );
    }

    // add more if necessary.

    /** Loads a string resource and formats it with specified arguments. */
    public static String format( String property, Object[] args ) {
        String text = ResourceBundle.getBundle(Messages.class.getName()).getString(property);
        return MessageFormat.format(text,args);
    }

//
//
// Message resources
//
//
    public static final String UNEXPECTED_ENTER_ELEMENT =  // arg:2
        "ContentHandlerEx.UnexpectedEnterElement";

    public static final String UNEXPECTED_LEAVE_ELEMENT =  // arg:2
        "ContentHandlerEx.UnexpectedLeaveElement";

    public static final String UNEXPECTED_ENTER_ATTRIBUTE =// arg:2
        "ContentHandlerEx.UnexpectedEnterAttribute";

    public static final String UNEXPECTED_LEAVE_ATTRIBUTE =// arg:2
        "ContentHandlerEx.UnexpectedLeaveAttribute";

    public static final String UNEXPECTED_TEXT =// arg:1
        "ContentHandlerEx.UnexpectedText";

    public static final String UNEXPECTED_LEAVE_CHILD = // 0 args
        "ContentHandlerEx.UnexpectedLeaveChild";

    public static final String UNEXPECTED_ROOT_ELEMENT = // 1 arg
        "SAXUnmarshallerHandlerImpl.UnexpectedRootElement";

    public static final String UNEXPECTED_ROOT_ELEMENT2 = // 3 arg
        "SAXUnmarshallerHandlerImpl.UnexpectedRootElement2";

    public static final String UNDEFINED_PREFIX = // 1 arg
        "Util.UndefinedPrefix";

    public static final String NULL_READER = // 0 args
        "Unmarshaller.NullReader";

    public static final String ILLEGAL_READER_STATE = // 1 arg
        "Unmarshaller.IllegalReaderState";

}
