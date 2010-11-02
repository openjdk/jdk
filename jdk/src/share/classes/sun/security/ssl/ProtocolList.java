/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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
    private static final ProtocolList CLIENT_DEFAULT;
    private static final ProtocolList SERVER_DEFAULT;

    // the sorted protocol version list
    private final ArrayList<ProtocolVersion> protocols;

    private String[] protocolNames;

    // the minimum and maximum ProtocolVersions in this list
    final ProtocolVersion min, max;

    // the format for the hello version to use
    final ProtocolVersion helloVersion;

    ProtocolList(String[] names) {
        this(convert(names));
    }

    ProtocolList(ArrayList<ProtocolVersion> versions) {
        this.protocols = versions;

        if ((protocols.size() == 1) &&
                protocols.contains(ProtocolVersion.SSL20Hello)) {
            throw new IllegalArgumentException("SSLv2Hello cannot be " +
                "enabled unless at least one other supported version " +
                "is also enabled.");
        }

        if (protocols.size() != 0) {
            Collections.sort(protocols);
            min = protocols.get(0);
            max = protocols.get(protocols.size() - 1);
            helloVersion = protocols.get(0);
        } else {
            min = ProtocolVersion.NONE;
            max = ProtocolVersion.NONE;
            helloVersion = ProtocolVersion.NONE;
        }
    }

    private static ArrayList<ProtocolVersion> convert(String[] names) {
        if (names == null) {
            throw new IllegalArgumentException("Protocols may not be null");
        }

        ArrayList<ProtocolVersion> versions = new ArrayList<ProtocolVersion>(3);
        for (int i = 0; i < names.length; i++ ) {
            ProtocolVersion version = ProtocolVersion.valueOf(names[i]);
            if (versions.contains(version) == false) {
                versions.add(version);
            }
        }

        return versions;
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
     * Return a reference to the internal Collection of CipherSuites.
     * The Collection MUST NOT be modified.
     */
    Collection<ProtocolVersion> collection() {
        return protocols;
    }

    /**
     * Select a protocol version from the list.
     *
     * Return the lower of the protocol version of that suggested by
     * the <code>protocolVersion</code> and the highest version of this
     * protocol list, or null if no protocol version is available.
     *
     * The method is used by TLS server to negotiated the protocol
     * version between client suggested protocol version in the
     * client hello and protocol versions supported by the server.
     */
    ProtocolVersion selectProtocolVersion(ProtocolVersion protocolVersion) {
        ProtocolVersion selectedVersion = null;
        for (ProtocolVersion pv : protocols) {
            if (pv.v > protocolVersion.v) {
                break;  // Safe to break here as this.protocols is sorted
            }
            selectedVersion = pv;
        }

        return selectedVersion;
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
        return protocolNames.clone();
    }

    public String toString() {
        return protocols.toString();
    }

    /**
     * Return the list of default enabled protocols.
     */
    static ProtocolList getDefault(boolean isServer) {
        return isServer ? SERVER_DEFAULT : CLIENT_DEFAULT;
    }

    /**
     * Return whether a protocol list is the original default enabled
     * protocols.  See: SSLSocket/SSLEngine.setEnabledProtocols()
     */
    static boolean isDefaultProtocolList(ProtocolList protocols) {
        return protocols == CLIENT_DEFAULT || protocols == SERVER_DEFAULT;
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
                ProtocolVersion.TLS10.name,
                ProtocolVersion.TLS11.name,
                ProtocolVersion.TLS12.name
            });

            SERVER_DEFAULT = SUPPORTED;
            CLIENT_DEFAULT = new ProtocolList(new String[] {
                ProtocolVersion.TLS10.name
            });
        } else {
            SUPPORTED = new ProtocolList(new String[] {
                ProtocolVersion.SSL20Hello.name,
                ProtocolVersion.SSL30.name,
                ProtocolVersion.TLS10.name,
                ProtocolVersion.TLS11.name,
                ProtocolVersion.TLS12.name
            });

            SERVER_DEFAULT = SUPPORTED;

            /*
             * RFC 5246 says that sending SSLv2 backward-compatible
             * hello SHOULD NOT be done any longer.
             *
             * We are not enabling TLS 1.1/1.2 by default yet on clients
             * out of concern for interop with existing
             * SSLv3/TLS1.0-only servers.  When these versions of TLS
             * gain more traction, we'll enable them.
             */
            CLIENT_DEFAULT = new ProtocolList(new String[] {
                ProtocolVersion.SSL30.name,
                ProtocolVersion.TLS10.name
            });
        }
    }

}
