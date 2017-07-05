/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.developer;

import com.sun.xml.internal.ws.api.message.Packet;
import org.xml.sax.ErrorHandler;

import javax.xml.ws.handler.MessageContext;
import javax.xml.validation.Validator;

/**
 * An {@link ErrorHandler} to receive errors encountered during the
 * {@link Validator#validate} method invocation. Specify
 * a custom handler in {@link SchemaValidation}, {@link SchemaValidationFeature}
 * to customize the error handling process during validation.
 *
 * @see SchemaValidation
 * @author Jitendra Kotamraju
 */
public abstract class ValidationErrorHandler implements ErrorHandler {
    protected Packet packet;

    /**
     * Use it to communicate validation errors with the application.
     *
     * For e.g validation exceptions can be stored in {@link Packet#invocationProperties}
     * during request processing and can be accessed in the endpoint
     * via {@link MessageContext}
     *
     * @param packet for request or response message
     */
    public void setPacket(Packet packet) {
        this.packet = packet;
    }

}
