/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.security.mscapi;

// NOTE: this class is duplicated amongst SunJCE, SunPKCS11, and SunMSCAPI.
// All files should be kept in sync.

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.net.URL;
import java.net.JarURLConnection;
import java.net.MalformedURLException;

import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;

/**
 * This class verifies JAR files (and any supporting JAR files), and
 * determines whether they may be used in this implementation.
 *
 * The JCE in OpenJDK has an open cryptographic interface, meaning it
 * does not restrict which providers can be used.  Compliance with
 * United States export controls and with local law governing the
 * import/export of products incorporating the JCE in the OpenJDK is
 * the responsibility of the licensee.
 *
 * @since 1.7
 */
final class JarVerifier {

    private static final boolean debug = false;

    /**
     * Verify the JAR file is signed by an entity which has a certificate
     * issued by a trusted CA.
     *
     * Note: this is a temporary method and will change soon to use the
     * exception chaining mechanism, which can provide more details
     * as to why the verification failed.
     *
     * @param c the class to be verified.
     * @return true if verification is successful.
     */
    static boolean verify(final Class c) {
        return true;
    }
}
