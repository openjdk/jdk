/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.security.sasl;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * The SASL provider.
 * Provides client support for
 * - EXTERNAL
 * - PLAIN
 * - CRAM-MD5
 * - DIGEST-MD5
 * - GSSAPI/Kerberos v5
 * And server support for
 * - CRAM-MD5
 * - DIGEST-MD5
 * - GSSAPI/Kerberos v5
 */

public final class Provider extends java.security.Provider {

    private static final long serialVersionUID = 8622598936488630849L;

    private static final String info = "Sun SASL provider" +
        "(implements client mechanisms for: " +
        "DIGEST-MD5, GSSAPI, EXTERNAL, PLAIN, CRAM-MD5;" +
        " server mechanisms for: DIGEST-MD5, GSSAPI, CRAM-MD5)";

    public Provider() {
        super("SunSASL", 1.7d, info);

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                // Client mechanisms
                put("SaslClientFactory.DIGEST-MD5",
                    "com.sun.security.sasl.digest.FactoryImpl");
                put("SaslClientFactory.GSSAPI",
                    "com.sun.security.sasl.gsskerb.FactoryImpl");

                put("SaslClientFactory.EXTERNAL",
                    "com.sun.security.sasl.ClientFactoryImpl");
                put("SaslClientFactory.PLAIN",
                    "com.sun.security.sasl.ClientFactoryImpl");
                put("SaslClientFactory.CRAM-MD5",
                    "com.sun.security.sasl.ClientFactoryImpl");

                // Server mechanisms
                put("SaslServerFactory.CRAM-MD5",
                    "com.sun.security.sasl.ServerFactoryImpl");
                put("SaslServerFactory.GSSAPI",
                    "com.sun.security.sasl.gsskerb.FactoryImpl");
                put("SaslServerFactory.DIGEST-MD5",
                    "com.sun.security.sasl.digest.FactoryImpl");
                return null;
            }
        });
    }
}
