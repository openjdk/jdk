/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.servicetag;

import java.io.IOException;
import java.util.Set;

/**
 * Utility class to obtain the service tag for the Solaris Operating System.
 */
class SolarisServiceTag {
    private final static String[] SolarisProductURNs = new String[] {
        "urn:uuid:a7a38948-2bd5-11d6-98ce-9d3ac1c0cfd7", /* Solaris 8 */
        "urn:uuid:4f82caac-36f3-11d6-866b-85f428ef944e", /* Solaris 9 */
        "urn:uuid:a19de03b-48bc-11d9-9607-080020a9ed93", /* Solaris 9 sparc */
        "urn:uuid:4c35c45b-4955-11d9-9607-080020a9ed93", /* Solaris 9 x86 */
        "urn:uuid:5005588c-36f3-11d6-9cec-fc96f718e113", /* Solaris 10 */
        "urn:uuid:6df19e63-7ef5-11db-a4bd-080020a9ed93"  /* Solaris 11 */
    };

    /**
     * Returns null if not found.
     *
     * There is only one service tag for the operating system.
     */
    static ServiceTag getServiceTag() throws IOException {
        if (Registry.isSupported()) {
            Registry streg = Registry.getSystemRegistry();
            for (String parentURN : SolarisProductURNs) {
                Set<ServiceTag> instances = streg.findServiceTags(parentURN);
                for (ServiceTag st : instances) {
                    // there should have only one service tag for the OS
                    return st;
                }
            }
        }
        return null;
    }
}
