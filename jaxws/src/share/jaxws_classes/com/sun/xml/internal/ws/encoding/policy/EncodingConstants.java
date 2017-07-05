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

package com.sun.xml.internal.ws.encoding.policy;

import javax.xml.namespace.QName;

/**
 * File holding all encoding constants
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public final class EncodingConstants {
    /** Prevents creation of new EncodingConstants instance */
    private EncodingConstants() {
    }

    public static final String SUN_FI_SERVICE_NS = "http://java.sun.com/xml/ns/wsit/2006/09/policy/fastinfoset/service";
    public static final QName OPTIMIZED_FI_SERIALIZATION_ASSERTION = new QName(SUN_FI_SERVICE_NS, "OptimizedFastInfosetSerialization");

    public static final String SUN_ENCODING_CLIENT_NS = "http://java.sun.com/xml/ns/wsit/2006/09/policy/encoding/client";
    public static final QName SELECT_OPTIMAL_ENCODING_ASSERTION = new QName(SUN_ENCODING_CLIENT_NS, "AutomaticallySelectOptimalEncoding");

    public static final String OPTIMIZED_MIME_NS = "http://schemas.xmlsoap.org/ws/2004/09/policy/optimizedmimeserialization";
    public static final QName OPTIMIZED_MIME_SERIALIZATION_ASSERTION = new QName(OPTIMIZED_MIME_NS, "OptimizedMimeSerialization");

    public static final String ENCODING_NS = "http://schemas.xmlsoap.org/ws/2004/09/policy/encoding";
    public static final QName UTF816FFFE_CHARACTER_ENCODING_ASSERTION = new QName(ENCODING_NS, "Utf816FFFECharacterEncoding");
}
