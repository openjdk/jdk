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

package com.sun.xml.internal.ws.util;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Represents the version information.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Version {
    /**
     * Represents the build id, which is a string like "b13" or "hudson-250".
     */
    public final String BUILD_ID;
    /**
     * Represents the complete version string, such as "JAX-WS RI 2.0-b19"
     */
    public final String BUILD_VERSION;
    /**
     * Represents the major JAX-WS version, such as "2.0".
     */
    public final String MAJOR_VERSION;

    private Version(String buildId, String buildVersion, String majorVersion) {
        this.BUILD_ID = fixNull(buildId);
        this.BUILD_VERSION = fixNull(buildVersion);
        this.MAJOR_VERSION = fixNull(majorVersion);
    }

    public static Version create(InputStream is) {
        Properties props = new Properties();
        try {
            props.load(is);
        } catch (IOException e) {
            // ignore even if the property was not found. we'll treat everything as unknown
        } catch (Exception e) {
            //ignore even if property not found
        }

        return new Version(
            props.getProperty("build-id"),
            props.getProperty("build-version"),
            props.getProperty("major-version"));
    }

    private String fixNull(String v) {
        if(v==null) return "unknown";
        return v;
    }

    public String toString() {
        return BUILD_VERSION;
    }
}
