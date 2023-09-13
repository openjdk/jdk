/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
public final class CAInterop {
    /**
     * Constructor for interoperability test with third party CA.
     *
     * @param revocationMode revocation checking mode to use
     */
    public CAInterop(String revocationMode) {
        if ("CRL".equalsIgnoreCase(revocationMode)) {
            ValidatePathWithURL.enableCRLOnly();
        } else if ("OCSP".equalsIgnoreCase(revocationMode)) {
            ValidatePathWithURL.enableOCSPOnly();
        } else {
            // OCSP and CRL check by default
            ValidatePathWithURL.enableOCSPAndCRL();
        }

        ValidatePathWithURL.logRevocationSettings();
    }

    /**
     * Validates provided URLs using <code>HttpsURLConnection</code> making sure they
     * anchor to the root CA found in <code>cacerts</code> using provided alias.
     *
     * @param caAlias        CA alis from <code>cacerts</code> file
     * @param validCertURL   valid test URL
     * @param revokedCertURL revoked test URL
     * @throws Exception thrown when certificate can't be validated as valid or revoked
     */
    public void validate(String caAlias,
                         String validCertURL,
                         String revokedCertURL) throws Exception {

        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(caAlias);

        if (validCertURL != null) {
            validatePathWithURL.validateDomain(validCertURL, false);
        }

        if (revokedCertURL != null) {
            validatePathWithURL.validateDomain(revokedCertURL, true);
        }
    }
}
