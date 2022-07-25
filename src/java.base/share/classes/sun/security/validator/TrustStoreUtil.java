/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.validator;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Enumeration;

import java.security.KeyStore;
import java.security.KeyStore.Entry.Attribute;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;

/**
 * Collection of static utility methods related to trust anchor KeyStores.
 *
 * @author Andreas Sterbenz
 */
public final class TrustStoreUtil {

    private TrustStoreUtil() {
        // empty
    }

    /**
     * Return an unmodifiable Set with all trusted X509Certificates contained
     * in the specified KeyStore.
     */
    public static Set<X509Certificate> getTrustedCerts(KeyStore ks) {
        return getTrustedCerts(ks, Validator.VAR_GENERIC);
    }

    public static Set<X509Certificate> getTrustedCerts(KeyStore ks,
        String variant) {
        Set<X509Certificate> set = new HashSet<>();
        try {
            for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
                String alias = e.nextElement();
                if (ks.isCertificateEntry(alias)) {
                    var entry = (TrustedCertificateEntry)
                        ks.getEntry(alias, null);
                    if (variant != Validator.VAR_GENERIC &&
                        ks.getType().equalsIgnoreCase("PKCS12")) {
                        var attrs = entry.getAttributes();
                    }
                    Certificate cert = entry.getTrustedCertificate();
                    if (cert instanceof X509Certificate) {
                        set.add((X509Certificate)cert);
                    }
                } else if (ks.isKeyEntry(alias)) {
                    Certificate[] certs = ks.getCertificateChain(alias);
                    if ((certs != null) && (certs.length > 0) &&
                            (certs[0] instanceof X509Certificate)) {
                        set.add((X509Certificate)certs[0]);
                    }
                }
            }
        } catch (KeyStoreException | NoSuchAlgorithmException |
                 UnrecoverableEntryException e) {
            // ignore
            //
            // This should be rare, but better to log this in the future.
        }

        return Collections.unmodifiableSet(set);
    }

    private static boolean isVariantAllowed(String variant, Set<Attribute> attrs) {
        for (Attribute attr : attrs) {
            if (attr.getName().equals("2.16.840.1.113894.746875.1.1")) {
                String[] oids = attr.getValue().split(",");
                for (String oid : oids) {
                    if (isVariantAllowed(variant, oid)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isVariantAllowed(String variant, String oid) {
        switch (oid) {
            case "serverAuth":
                return (variant == Validator.VAR_GENERIC ||
                        variant == Validator.VAR_TLS_SERVER);
            case "codeSigning":
                return (variant == Validator.VAR_GENERIC ||
                        variant == Validator.VAR_CODE_SIGNING ||
                        variant == Validator.VAR_JCE_SIGNING);
            case "timeStamping":
                return (variant == Validator.VAR_GENERIC ||
                        variant == Validator.VAR_CODE_SIGNING ||
                        variant == Validator.VAR_JCE_SIGNING ||
                        variant == Validator.VAR_TSA_SERVER);
            case "clientAuth":
                return (variant == Validator.VAR_GENERIC ||
                        variant == Validator.VAR_TLS_CLIENT);
            case "anyExtendedKeyUsage":
            case "OCSPSigning":
                return true;
            default:
                return false;
        }
    }
}
