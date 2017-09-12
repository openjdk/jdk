/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Utility class used as a JEP 238 multi release jar versioned class.
 *
 * Version for {@code runtime >= 9}.
 */
public class MrJarUtil {

    /**
     * Get property used for disabling instance pooling of xml readers / writers.
     *
     * @param baseName Name of a {@linkplain com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory} class or
     *                 {@linkplain com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory} class.
     *
     * @return true if *.noPool system property is not set or is set to true.
     */
    public static boolean getNoPoolProperty(String baseName) {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                String noPool = System.getProperty(baseName + ".noPool");
                return noPool == null || Boolean.parseBoolean(noPool);
            }
        });
    }
}
