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
package com.sun.xml.internal.ws.encoding.fastinfoset;

/**
 * MIME types for Infosets encoded as fast infoset documents.
 *
 * @author Paul.Sandoz@Sun.Com
 */
public final class FastInfosetMIMETypes {
    /**
     * MIME type for a generic Infoset encoded as a fast infoset document.
     */
    static public final String INFOSET = "application/fastinfoset";
    /**
     * MIME type for a SOAP 1.1 Infoset encoded as a fast infoset document.
     */
    static public final String SOAP_11 = "application/fastinfoset";
    /**
     * MIME type for a SOAP 1.2 Infoset encoded as a fast infoset document.
     */
    static public final String SOAP_12 = "application/soap+fastinfoset";

    /**
     * MIME type for a generic Infoset encoded as a stateful fast infoset document.
     */
    static public final String STATEFUL_INFOSET = "application/vnd.sun.stateful.fastinfoset";
    /**
     * MIME type for a SOAP 1.1 Infoset encoded as a stateful fast infoset document.
     */
    static public final String STATEFUL_SOAP_11 = "application/vnd.sun.stateful.fastinfoset";
    /**
     * MIME type for a SOAP 1.2 Infoset encoded as a stateful fast infoset document.
     */
    static public final String STATEFUL_SOAP_12 = "application/vnd.sun.stateful.soap+fastinfoset";
}
