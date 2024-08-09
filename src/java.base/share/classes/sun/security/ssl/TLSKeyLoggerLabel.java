/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A TLS key logger labels which will be output in a SSLKEYLOGFILE format file.
 * <p>
 * This follows the SSLKEYLOGFILE format propsed in draft RFC:
 * <a href="https://datatracker.ietf.org/doc/draft-ietf-tls-keylogfile/"
 * <p>
 * Note well, this facility is for development/testing only, and should not be
 * used in production.
 */
public enum TLSKeyLoggerLabel {
    // TLSv1.2
    CLIENT_RANDOM,

    // TLSv1.3
    CLIENT_EARLY_TRAFFIC_SECRET,
    EARLY_EXPORTER_MASTER_SECRET,
    CLIENT_HANDSHAKE_TRAFFIC_SECRET,
    SERVER_HANDSHAKE_TRAFFIC_SECRET,
    CLIENT_TRAFFIC_SECRET,
    SERVER_TRAFFIC_SECRET,
    EXPORTER_SECRET
}