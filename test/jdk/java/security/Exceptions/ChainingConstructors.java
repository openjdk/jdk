/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4496095 8296406
 * @summary Test constructors for exception chaining of security-related exceptions
 */

import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import javax.net.ssl.*;

public class ChainingConstructors {

    private static final String MSG = "msg";
    private static Exception cause = new Exception("cause");

    public static <E extends Exception> void test(E ex1, E ex2) throws Exception {
        String cln = ex1.getClass().getSimpleName();
        if (!ex1.getCause().equals(cause)) {
            throw new SecurityException("Cause test failed for " + cln);
        }
        if (!ex2.getMessage().equals(MSG) || !ex2.getCause().equals(cause)) {
            throw new SecurityException("Cause and message test failed for " + cln);
        }
    }

    public static void main(String[] args) throws Exception {
        test(new SecurityException(cause), new SecurityException(MSG, cause));
        test(new DigestException(cause), new DigestException(MSG, cause));
        test(new GeneralSecurityException(cause), new GeneralSecurityException(MSG, cause));
        test(new InvalidAlgorithmParameterException(cause),
             new InvalidAlgorithmParameterException(MSG, cause));
        test(new InvalidKeyException(cause), new InvalidKeyException(MSG, cause));
        test(new InvalidKeySpecException(cause), new InvalidKeySpecException(MSG, cause));
        test(new InvalidParameterException(cause), new InvalidParameterException(MSG, cause));
        test(new KeyException(cause), new KeyException(MSG, cause));
        test(new KeyManagementException(cause), new KeyManagementException(MSG, cause));
        test(new KeyStoreException(cause), new KeyStoreException(MSG, cause));
        test(new NoSuchAlgorithmException(cause), new NoSuchAlgorithmException(MSG, cause));
        test(new ProviderException(cause), new ProviderException(MSG, cause));
        test(new SignatureException(cause), new SignatureException(MSG, cause));
        test(new CRLException(cause), new CRLException(MSG, cause));
        test(new CertificateException(cause), new CertificateException(MSG, cause));
        test(new CertificateParsingException(cause), new CertificateParsingException(MSG, cause));
        test(new CertificateEncodingException(cause), new CertificateEncodingException(MSG, cause));
        test(new CertPathBuilderException(cause), new CertPathBuilderException(MSG, cause));
        test(new SSLException(cause), new SSLException(MSG, cause));
    }
}
