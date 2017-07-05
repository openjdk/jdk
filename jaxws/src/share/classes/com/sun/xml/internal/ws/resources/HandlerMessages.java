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
public final class HandlerMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.handler");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableHANDLER_MESSAGE_CONTEXT_INVALID_CLASS(Object arg0, Object arg1) {
        return messageFactory.getMessage("handler.messageContext.invalid.class", arg0, arg1);
    }

    /**
     * "{0}" is not an allowed value for the property "{1}"
     *
     */
    public static String HANDLER_MESSAGE_CONTEXT_INVALID_CLASS(Object arg0, Object arg1) {
        return localizer.localize(localizableHANDLER_MESSAGE_CONTEXT_INVALID_CLASS(arg0, arg1));
    }

    public static Localizable localizableCANNOT_EXTEND_HANDLER_DIRECTLY(Object arg0) {
        return messageFactory.getMessage("cannot.extend.handler.directly", arg0);
    }

    /**
     * Handler {0} must implement LogicalHandler or SOAPHandler.
     *
     */
    public static String CANNOT_EXTEND_HANDLER_DIRECTLY(Object arg0) {
        return localizer.localize(localizableCANNOT_EXTEND_HANDLER_DIRECTLY(arg0));
    }

    public static Localizable localizableHANDLER_NOT_VALID_TYPE(Object arg0) {
        return messageFactory.getMessage("handler.not.valid.type", arg0);
    }

    /**
     * {0} does not implement one of the handler interfaces.
     *
     */
    public static String HANDLER_NOT_VALID_TYPE(Object arg0) {
        return localizer.localize(localizableHANDLER_NOT_VALID_TYPE(arg0));
    }

    public static Localizable localizableCANNOT_INSTANTIATE_HANDLER(Object arg0, Object arg1) {
        return messageFactory.getMessage("cannot.instantiate.handler", arg0, arg1);
    }

    /**
     * Unable to instantiate handler: {0} because: {1}
     *
     */
    public static String CANNOT_INSTANTIATE_HANDLER(Object arg0, Object arg1) {
        return localizer.localize(localizableCANNOT_INSTANTIATE_HANDLER(arg0, arg1));
    }

    public static Localizable localizableHANDLER_CHAIN_CONTAINS_HANDLER_ONLY(Object arg0) {
        return messageFactory.getMessage("handler.chain.contains.handler.only", arg0);
    }

    /**
     * A HandlerChain can only contain Handler instances: {0}
     *
     */
    public static String HANDLER_CHAIN_CONTAINS_HANDLER_ONLY(Object arg0) {
        return localizer.localize(localizableHANDLER_CHAIN_CONTAINS_HANDLER_ONLY(arg0));
    }

    public static Localizable localizableHANDLER_NESTED_ERROR(Object arg0) {
        return messageFactory.getMessage("handler.nestedError", arg0);
    }

    /**
     * handler error: {0}
     *
     */
    public static String HANDLER_NESTED_ERROR(Object arg0) {
        return localizer.localize(localizableHANDLER_NESTED_ERROR(arg0));
    }

    public static Localizable localizableHANDLER_PREDESTROY_IGNORE(Object arg0) {
        return messageFactory.getMessage("handler.predestroy.ignore", arg0);
    }

    /**
     * Exception ignored from invoking handler @PreDestroy method: {0}
     *
     */
    public static String HANDLER_PREDESTROY_IGNORE(Object arg0) {
        return localizer.localize(localizableHANDLER_PREDESTROY_IGNORE(arg0));
    }

}
