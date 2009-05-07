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
package com.sun.xml.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class SenderMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.sender");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableSENDER_REQUEST_ILLEGAL_VALUE_FOR_CONTENT_NEGOTIATION(Object arg0) {
        return messageFactory.getMessage("sender.request.illegalValueForContentNegotiation", arg0);
    }

    /**
     * illegal value for content negotiation property "{0}"
     *
     */
    public static String SENDER_REQUEST_ILLEGAL_VALUE_FOR_CONTENT_NEGOTIATION(Object arg0) {
        return localizer.localize(localizableSENDER_REQUEST_ILLEGAL_VALUE_FOR_CONTENT_NEGOTIATION(arg0));
    }

    public static Localizable localizableSENDER_RESPONSE_CANNOT_DECODE_FAULT_DETAIL() {
        return messageFactory.getMessage("sender.response.cannotDecodeFaultDetail");
    }

    /**
     * fault detail cannot be decoded
     *
     */
    public static String SENDER_RESPONSE_CANNOT_DECODE_FAULT_DETAIL() {
        return localizer.localize(localizableSENDER_RESPONSE_CANNOT_DECODE_FAULT_DETAIL());
    }

    public static Localizable localizableSENDER_NESTED_ERROR(Object arg0) {
        return messageFactory.getMessage("sender.nestedError", arg0);
    }

    /**
     * sender error: {0}
     *
     */
    public static String SENDER_NESTED_ERROR(Object arg0) {
        return localizer.localize(localizableSENDER_NESTED_ERROR(arg0));
    }

    public static Localizable localizableSENDER_REQUEST_MESSAGE_NOT_READY() {
        return messageFactory.getMessage("sender.request.messageNotReady");
    }

    /**
     * message not ready to be sent
     *
     */
    public static String SENDER_REQUEST_MESSAGE_NOT_READY() {
        return localizer.localize(localizableSENDER_REQUEST_MESSAGE_NOT_READY());
    }

}
