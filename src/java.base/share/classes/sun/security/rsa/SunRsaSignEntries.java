/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.rsa;

import java.util.*;
import java.security.Provider;
import static sun.security.provider.SunEntries.createAliasesWithOid;

/**
 * Defines the entries of the SunRsaSign provider.
 *
 * @author  Andreas Sterbenz
 */
public final class SunRsaSignEntries {

    private void add(Provider p, String type, String algo, String cn,
             List<String> aliases, HashMap<String, String> attrs) {
         services.add(new Provider.Service(p, type, algo, cn, aliases, attrs));
    }

    // extend LinkedHashSet for consistency with SunEntries
    // used by sun.security.provider.VerificationProvider
    public SunRsaSignEntries(Provider p) {
        services = new LinkedHashSet<>(20, 0.9f);

        // start populating content using the specified provider

        // common oids
        String rsaOid = "1.2.840.113549.1.1";
        List<String> rsaAliases = createAliasesWithOid(rsaOid);
        List<String> rsapssAliases = createAliasesWithOid(rsaOid + ".10");
        String sha1withRSAOid2 = "1.3.14.3.2.29";

        // common attribute map
        HashMap<String, String> attrs = new HashMap<>(3);
        attrs.put("SupportedKeyClasses",
                "java.security.interfaces.RSAPublicKey" +
                "|java.security.interfaces.RSAPrivateKey");

        add(p, "KeyFactory", "RSA",
                "sun.security.rsa.RSAKeyFactory$Legacy",
                rsaAliases, null);
        add(p, "KeyPairGenerator", "RSA",
                "sun.security.rsa.RSAKeyPairGenerator$Legacy",
                rsaAliases, null);
        add(p, "Signature", "MD2withRSA",
                "sun.security.rsa.RSASignature$MD2withRSA",
                createAliasesWithOid(rsaOid + ".2"), attrs);
        add(p, "Signature", "MD5withRSA",
                "sun.security.rsa.RSASignature$MD5withRSA",
                createAliasesWithOid(rsaOid + ".4"), attrs);
        add(p, "Signature", "SHA1withRSA",
                "sun.security.rsa.RSASignature$SHA1withRSA",
                createAliasesWithOid(rsaOid + ".5", sha1withRSAOid2), attrs);
        add(p, "Signature", "SHA224withRSA",
                "sun.security.rsa.RSASignature$SHA224withRSA",
                createAliasesWithOid(rsaOid + ".14"), attrs);
        add(p, "Signature", "SHA256withRSA",
                "sun.security.rsa.RSASignature$SHA256withRSA",
                createAliasesWithOid(rsaOid + ".11"), attrs);
        add(p, "Signature", "SHA384withRSA",
                "sun.security.rsa.RSASignature$SHA384withRSA",
                createAliasesWithOid(rsaOid + ".12"), attrs);
        add(p, "Signature", "SHA512withRSA",
                "sun.security.rsa.RSASignature$SHA512withRSA",
                createAliasesWithOid(rsaOid + ".13"), attrs);
        add(p, "Signature", "SHA512/224withRSA",
                "sun.security.rsa.RSASignature$SHA512_224withRSA",
                createAliasesWithOid(rsaOid + ".15"), attrs);
        add(p, "Signature", "SHA512/256withRSA",
                "sun.security.rsa.RSASignature$SHA512_256withRSA",
                createAliasesWithOid(rsaOid + ".16"), attrs);

        add(p, "KeyFactory", "RSASSA-PSS",
                "sun.security.rsa.RSAKeyFactory$PSS",
                rsapssAliases, null);
        add(p, "KeyPairGenerator", "RSASSA-PSS",
                "sun.security.rsa.RSAKeyPairGenerator$PSS",
                rsapssAliases, null);
        add(p, "Signature", "RSASSA-PSS",
                "sun.security.rsa.RSAPSSSignature",
                rsapssAliases, attrs);
        add(p, "AlgorithmParameters", "RSASSA-PSS",
                "sun.security.rsa.PSSParameters",
                rsapssAliases, null);
    }

    public Iterator<Provider.Service> iterator() {
        return services.iterator();
    }

    private LinkedHashSet<Provider.Service> services;
}
