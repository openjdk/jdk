/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.IOException;
import java.math.BigInteger;

import java.security.spec.*;


/**
 * Contains Elliptic Curve parameters.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
public final class NamedCurve extends ECParameterSpec {

    // friendly name for toString() output
    private final String name;

    // well known OID
    private final String oid;

    // encoded form (as NamedCurve identified via OID)
    private final byte[] encoded;

    NamedCurve(String name, String oid, EllipticCurve curve,
            ECPoint g, BigInteger n, int h) {
        super(curve, g, n, h);
        this.name = name;
        this.oid = oid;

        DerOutputStream out = new DerOutputStream();

        try {
            out.putOID(new ObjectIdentifier(oid));
        } catch (IOException e) {
            throw new RuntimeException("Internal error", e);
        }

        encoded = out.toByteArray();
    }

    public String getName() {
        return name;
    }

    public byte[] getEncoded() {
        return encoded.clone();
    }

    public String getObjectId() {
        return oid;
    }

    public String toString() {
        return name + " (" + oid + ")";
    }
}
