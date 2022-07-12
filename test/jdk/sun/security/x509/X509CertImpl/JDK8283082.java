/*
 * Copyright (c) 2022, Red Hat, Inc.
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
 * @bug 8283082
 * @modules java.base/sun.security.x509
 * @summary This test is to confirm that
 * sun.security.x509.X509CertImpl.delete("x509.info.validity") doesn't
 * null out info field as reported by bug 8283082
 */

import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

public class JDK8283082{
    public static void main(String[] args) throws Exception {
        var c = new X509CertImpl();
        c.set("x509.info", new X509CertInfo());
        c.set("x509.info.issuer", new X500Name("CN=one"));
        c.delete("x509.info.issuer");
        c.set("x509.info.issuer", new X500Name("CN=two"));
    }
}
