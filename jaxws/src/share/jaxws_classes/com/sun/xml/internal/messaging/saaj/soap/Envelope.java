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

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.soap.SOAPEnvelope;
import javax.xml.transform.Source;

/**
 * Different implementations for SOAP Envelope must all implement this
 * interface.
 *
 * @author Anil Vijendran (akv@eng.sun.com)
 */
public interface Envelope extends SOAPEnvelope {
    /**
     * Get the content as a JAXP Source.
     */
    Source getContent();

    /**
     * Output the content.
     */
    void output(OutputStream out) throws IOException;

    /**
     * Output the content.
     */
    void output(OutputStream out, boolean isFastInfoset) throws IOException;
}
