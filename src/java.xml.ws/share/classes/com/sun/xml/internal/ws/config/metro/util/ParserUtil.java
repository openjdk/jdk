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

package com.sun.xml.internal.ws.config.metro.util;

import com.sun.istack.internal.logging.Logger;

import javax.xml.ws.WebServiceException;

/**
 *
 * @author Fabian Ritzmann
 */
public class ParserUtil {

    private static final Logger LOGGER = Logger.getLogger(ParserUtil.class);

    private ParserUtil() {
    }

    /**
     * Return true if the value is "true" or "1". Return false if the value is
     * "false" or "0". Throw an exception otherwise. The test is case sensitive.
     *
     * @param value The String representation of the value. Must not be null.
     * @return True if the value is "true" or "1". False if the value is
     *   "false" or "0".
     * @throws PolicyException If the value is not "true", "false", "0" or "1".
     */
    public static boolean parseBooleanValue(String value) throws WebServiceException {
        if ("true".equals(value) || "1".equals(value)) {
            return true;
        }
        else if ("false".equals(value) || "0".equals(value)) {
            return false;
        }
        // TODO logging message
        throw LOGGER.logSevereException(new WebServiceException("invalid boolean value"));
    }

}
