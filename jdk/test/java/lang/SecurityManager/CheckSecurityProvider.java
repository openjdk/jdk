/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 6997010
 * @summary Consolidate java.security files into one file with modifications
 */

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 * The main benefit of this test is to catch merge errors or other types
 * of issues where one or more of the security providers are accidentally
 * removed. This is why the known security providers have to
 * be explicitly listed below.
 */
public class CheckSecurityProvider {
    public static void main(String[] args) throws Exception {

        String os = System.getProperty("os.name");

        /*
         * This array should be updated whenever new security providers
         * are added to the the java.security file.
         * NOTE: it should be in the same order as the java.security file
         */

        List<String> expected = new ArrayList<>();

        if (os.equals("SunOS")) {
            if (!isOpenJDKOnly()) {
                expected.add("com.oracle.security.ucrypto.UcryptoProvider");
            }
            expected.add("sun.security.pkcs11.SunPKCS11");
        }
        expected.add("sun.security.provider.Sun");
        expected.add("sun.security.rsa.SunRsaSign");
        expected.add("sun.security.ec.SunEC");
        expected.add("com.sun.net.ssl.internal.ssl.Provider");
        expected.add("com.sun.crypto.provider.SunJCE");
        expected.add("sun.security.jgss.SunProvider");
        expected.add("com.sun.security.sasl.Provider");
        expected.add("org.jcp.xml.dsig.internal.dom.XMLDSigRI");
        expected.add("sun.security.smartcardio.SunPCSC");
        if (os.startsWith("Windows")) {
            expected.add("sun.security.mscapi.SunMSCAPI");
        }
        if (os.contains("OS X")) {
            expected.add("apple.security.AppleProvider");
        }

        Iterator<String> iter = expected.iterator();
        for (Provider p: Security.getProviders()) {
            if (!iter.hasNext()) {
                throw new Exception("Less expected");
            }
            String n1 = iter.next();
            String n2 = p.getClass().getName();
            if (!n1.equals(n2)) {
                throw new Exception("Expected " + n1 + ", actual " + n2);
            }
        }
        if (iter.hasNext()) {
            throw new Exception("More expected");
        }
    }

    // Copied from CheckPackageAccess.java in the same directory
    private static boolean isOpenJDKOnly() {
        String prop = System.getProperty("java.runtime.name");
        return prop != null && prop.startsWith("OpenJDK");
    }
}
