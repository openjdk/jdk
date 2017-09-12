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

package com.sun.xml.internal.ws.addressing.model;

import com.sun.xml.internal.ws.resources.AddressingMessages;

import javax.xml.ws.WebServiceException;
import javax.xml.namespace.QName;

/**
 * This exception captures SOAP Fault information when a WS-Addressing 1.0 Message Addressing
 * Property is invalid and cannot be processed.
 *
 * @author Rama Pulavarthi
 */
public class InvalidAddressingHeaderException extends WebServiceException {
    private QName problemHeader;
    private QName subsubcode;

    /**
     * Creates a InvalidAddressingHeader exception capturing information about the invalid
     * Addressing Message Property and the reason in Subsubcode.
     * @param problemHeader
     *      represents the invalid Addressing Header.
     * @param subsubcode
     *      represents the reason why the Addressing header in question is invalid.
     */
    public InvalidAddressingHeaderException(QName problemHeader, QName subsubcode) {
        super(AddressingMessages.INVALID_ADDRESSING_HEADER_EXCEPTION(problemHeader,subsubcode));
        this.problemHeader = problemHeader;
        this.subsubcode = subsubcode;
    }

    public QName getProblemHeader() {
        return problemHeader;
    }

    public QName getSubsubcode() {
        return subsubcode;
    }
}
