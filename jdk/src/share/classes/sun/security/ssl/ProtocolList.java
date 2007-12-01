/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.ssl;

import java.util.*;

/**
 * A list of ProtocolVersions. Also maintains the list of supported protocols.
 * Instances of this class are immutable. Some member variables are final
 * and can be accessed directly without method accessors.
 *
 * @author  Andreas Sterbenz
 * @since   1.4.1
 */
final class ProtocolList {

    private static final ProtocolList SUPPORTED;

    private final Collection<ProtocolVersion> protocols;
    private String[] protocolNames;

    // the minimum and maximum ProtocolVersions in this list
    final ProtocolVersion min, max;

    // the format for the hello version to use
    final ProtocolVersion helloVersion;

    ProtocolList(String[] names) {
        if (names == null) {
            throw new IllegalArgumentException("Protocols may not be null");
        }
        protocols = new ArrayList<ProtocolVersion>(3);
        for (int i = 0; i < names.length; i++ ) {
            ProtocolVersion version = ProtocolVersion.valueOf(names[i]);
            if (protocols.contains(version) == false) {
                protocols.add(version);
            }
        }
        if ((protocols.size() == 1)
                && protocols.contains(ProtocolVersion.SSL20Hello)) {
            throw new IllegalArgumentException("SSLv2Hello" +
                  "cannot be enabled unless TLSv1 or SSLv3 is also enabled");
        }
        min = contains(ProtocolVersion.SSL30) ? ProtocolVersion.SSL30
                                              : ProtocolVersion.TLS10;
        max = contains(ProtocolVersion.TLS10) ? ProtocolVersion.TLS10
                                              : ProtocolVersion.SSL30;
        if (protocols.contains(ProtocolVersion.SSL20Hello)) {
            helloVersion = ProtocolVersion.SSL20Hello;
        } else {
            helloVersion = min;
        }
    }

    /**
     * Return whether this list contains the specified protocol version.
     * SSLv2Hello is not a real protocol version we support, we always
     * return false for it.
     */
    boolean contains(ProtocolVersion protocolVersion) {
        if (protocolVersion == ProtocolVersion.SSL20Hello) {
            return false;
        }
        return protocols.contains(protocolVersion);
    }

    /**
     * Return an array with the names of the ProtocolVersions in this list.
     */
    synchronized String[] toStringArray() {
        if (protocolNames == null) {
            protocolNames = new String[protocols.size()];
            int i = 0;
            for (ProtocolVersion version : protocols) {
                protocolNames[i++] = version.name;
            }
        }
        return (String[])protocolNames.clone();
    }

    public String toString() {
        return protocols.toString();
    }

    /**
     * Return the list of default enabled protocols. Currently, this
     * is identical to the supported protocols.
     */
    static ProtocolList getDefault() {
        return SUPPORTED;
    }

    /**
     * Return the list of supported protocols.
     */
    static ProtocolList getSupported() {
        return SUPPORTED;
    }

    static {
        if (SunJSSE.isFIPS()) {
            SUPPORTED = new ProtocolList(new String[] {
                ProtocolVersion.TLS10.name
            });
        } else {
            SUPPORTED = new ProtocolList(new String[] {
                ProtocolVersion.SSL20Hello.name,
                ProtocolVersion.SSL30.name,
                ProtocolVersion.TLS10.name,
            });
        }
    }

}
