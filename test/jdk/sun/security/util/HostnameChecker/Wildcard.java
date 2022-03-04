/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7192189
 * @summary Check that wildcarded domains conform to RFC 6125
 * @modules java.base/sun.security.util java.base/sun.security.x509
 * @run main Wildcard
 */

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import sun.security.util.HostnameChecker;
import static sun.security.util.HostnameChecker.TYPE_TLS;
import sun.security.x509.X509CertImpl;

public class Wildcard {

    public static void main(String[] args) throws Exception {
        validateDomain(false, "foo.bar.example.net", "foo.*.example.net");
        validateDomain(true, "baz1.example.net", "baz*.example.net");
        validateDomain(true, "foobaz.example.net", "*baz.example.net");
        validateDomain(true, "buzz.example.net", "b*z.example.net");
        validateDomain(false, "公司.example.net", "xn--5*.example.net");
        validateDomain(true, "公司.江利子.example.net",
                       "*.xn--kcry6tjko.example.net");
    }

    static X509Certificate mock(String domain) {
        return new X509CertImpl() {
            @Override
            public Collection<List<?>> getSubjectAlternativeNames() {
                return List.of(List.of(2, domain));
            }
        };
    }

    static void validateDomain(boolean expected,
                               String domain, String wildcardedDomain)
        throws Exception {

        System.out.println("Matching domain " + domain +
            " against wildcarded domain " + wildcardedDomain);
        HostnameChecker checker = HostnameChecker.getInstance(TYPE_TLS);
        try {
            checker.match(domain, mock(wildcardedDomain));
        } catch (Exception e) {
            if (expected) {
                throw new Exception("unexpectedly failed match", e);
            }
            return;
        }
        if (!expected) {
            throw new Exception("unexpectedly passed match");
        }
    }
}
