/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;

public class SOAPVersionMismatchException extends SOAPExceptionImpl {
    /**
     * Constructs a <code>SOAPExceptionImpl</code> object with no
     * reason or embedded <code>Throwable</code> object.
     */
    public SOAPVersionMismatchException() {
        super();
    }

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object with the given
     * <code>String</code> as the reason for the exception being thrown.
     *
     * @param reason a description of what caused the exception
     */
    public SOAPVersionMismatchException(String reason) {
        super(reason);
    }

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object with the given
     * <code>String</code> as the reason for the exception being thrown
     * and the given <code>Throwable</code> object as an embedded
     * exception.
     *
     * @param reason a description of what caused the exception
     * @param cause a <code>Throwable</code> object that is to
     *        be embedded in this <code>SOAPExceptionImpl</code> object
     */
    public SOAPVersionMismatchException(String reason, Throwable cause) {
        super(reason, cause);
    }

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object initialized
     * with the given <code>Throwable</code> object.
     */
    public SOAPVersionMismatchException(Throwable cause) {
        super(cause);
    }
}
