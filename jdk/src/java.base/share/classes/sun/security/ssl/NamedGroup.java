/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.security.spec.ECParameterSpec;
import java.security.spec.ECGenParameterSpec;
import static sun.security.ssl.NamedGroupType.*;

enum NamedGroup {
    // Elliptic Curves (RFC 4492)
    //
    // See sun.security.util.CurveDB for the OIDs

    // NIST K-163
    SECT163_K1(1, NAMED_GROUP_ECDHE, "sect163k1", "1.3.132.0.1", true),

    SECT163_R1(2, NAMED_GROUP_ECDHE, "sect163r1", "1.3.132.0.2", false),

    // NIST B-163
    SECT163_R2(3, NAMED_GROUP_ECDHE, "sect163r2", "1.3.132.0.15", true),

    SECT193_R1(4, NAMED_GROUP_ECDHE, "sect193r1", "1.3.132.0.24", false),
    SECT193_R2(5, NAMED_GROUP_ECDHE, "sect193r2", "1.3.132.0.25", false),

    // NIST K-233
    SECT233_K1(6, NAMED_GROUP_ECDHE, "sect233k1", "1.3.132.0.26", true),

    // NIST B-233
    SECT233_R1(7, NAMED_GROUP_ECDHE, "sect233r1", "1.3.132.0.27", true),

    SECT239_K1(8, NAMED_GROUP_ECDHE, "sect239k1", "1.3.132.0.3", false),

    // NIST K-283
    SECT283_K1(9, NAMED_GROUP_ECDHE, "sect283k1", "1.3.132.0.16", true),

    // NIST B-283
    SECT283_R1(10, NAMED_GROUP_ECDHE, "sect283r1", "1.3.132.0.17", true),

    // NIST K-409
    SECT409_K1(11, NAMED_GROUP_ECDHE, "sect409k1", "1.3.132.0.36", true),

    // NIST B-409
    SECT409_R1(12, NAMED_GROUP_ECDHE, "sect409r1", "1.3.132.0.37", true),

    // NIST K-571
    SECT571_K1(13, NAMED_GROUP_ECDHE, "sect571k1", "1.3.132.0.38", true),

    // NIST B-571
    SECT571_R1(14, NAMED_GROUP_ECDHE, "sect571r1", "1.3.132.0.39", true),

    SECP160_K1(15, NAMED_GROUP_ECDHE, "secp160k1", "1.3.132.0.9", false),
    SECP160_R1(16, NAMED_GROUP_ECDHE, "secp160r1", "1.3.132.0.8", false),
    SECP160_R2(17, NAMED_GROUP_ECDHE, "secp160r2", "1.3.132.0.30", false),
    SECP192_K1(18, NAMED_GROUP_ECDHE, "secp192k1", "1.3.132.0.31", false),

    // NIST P-192
    SECP192_R1(19, NAMED_GROUP_ECDHE, "secp192r1", "1.2.840.10045.3.1.1", true),

    SECP224_K1(20, NAMED_GROUP_ECDHE, "secp224k1", "1.3.132.0.32", false),
    // NIST P-224
    SECP224_R1(21, NAMED_GROUP_ECDHE, "secp224r1", "1.3.132.0.33", true),

    SECP256_K1(22, NAMED_GROUP_ECDHE, "secp256k1", "1.3.132.0.10", false),

    // NIST P-256
    SECP256_R1(23, NAMED_GROUP_ECDHE, "secp256r1", "1.2.840.10045.3.1.7", true),

    // NIST P-384
    SECP384_R1(24, NAMED_GROUP_ECDHE, "secp384r1", "1.3.132.0.34", true),

    // NIST P-521
    SECP521_R1(25, NAMED_GROUP_ECDHE, "secp521r1", "1.3.132.0.35", true),

    // Finite Field Diffie-Hellman Ephemeral Parameters (RFC 7919)
    FFDHE_2048(256, NAMED_GROUP_FFDHE, "ffdhe2048",  true),
    FFDHE_3072(257, NAMED_GROUP_FFDHE, "ffdhe3072",  true),
    FFDHE_4096(258, NAMED_GROUP_FFDHE, "ffdhe4096",  true),
    FFDHE_6144(259, NAMED_GROUP_FFDHE, "ffdhe6144",  true),
    FFDHE_8192(260, NAMED_GROUP_FFDHE, "ffdhe8192",  true);

    int             id;
    NamedGroupType  type;
    String          name;
    String          oid;
    String          algorithm;
    boolean         isFips;

    // Constructor used for Elliptic Curve Groups (ECDHE)
    NamedGroup(int id, NamedGroupType type,
                String name, String oid, boolean isFips) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.oid = oid;
        this.algorithm = "EC";
        this.isFips = isFips;
    }

    // Constructor used for Finite Field Diffie-Hellman Groups (FFDHE)
    NamedGroup(int id, NamedGroupType type, String name, boolean isFips) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.oid = null;
        this.algorithm = "DiffieHellman";
        this.isFips = isFips;
    }

    static NamedGroup valueOf(int id) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.id == id) {
                return group;
            }
        }

        return null;
    }

    static NamedGroup nameOf(String name) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.name.equals(name)) {
                return group;
            }
        }

        return null;
    }

    static NamedGroup valueOf(ECParameterSpec params) {
        String oid = JsseJce.getNamedCurveOid(params);
        if ((oid != null) && (!oid.isEmpty())) {
            for (NamedGroup group : NamedGroup.values()) {
                if (oid.equals(group.oid)) {
                    return group;
                }
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
