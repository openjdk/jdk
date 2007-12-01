/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.security.spec;

/**
 * This immutable class specifies an elliptic curve public key with
 * its associated parameters.
 *
 * @see KeySpec
 * @see ECPoint
 * @see ECParameterSpec
 *
 * @author Valerie Peng
 *
 * @since 1.5
 */
public class ECPublicKeySpec implements KeySpec {

    private ECPoint w;
    private ECParameterSpec params;

    /**
     * Creates a new ECPublicKeySpec with the specified
     * parameter values.
     * @param w the public point.
     * @param params the associated elliptic curve domain
     * parameters.
     * @exception NullPointerException if <code>w</code>
     * or <code>params</code> is null.
     * @exception IllegalArgumentException if <code>w</code>
     * is point at infinity, i.e. ECPoint.POINT_INFINITY
     */
    public ECPublicKeySpec(ECPoint w, ECParameterSpec params) {
        if (w == null) {
            throw new NullPointerException("w is null");
        }
        if (params == null) {
            throw new NullPointerException("params is null");
        }
        if (w == ECPoint.POINT_INFINITY) {
            throw new IllegalArgumentException("w is ECPoint.POINT_INFINITY");
        }
        this.w = w;
        this.params = params;
    }

    /**
     * Returns the public point W.
     * @return the public point W.
     */
    public ECPoint getW() {
        return w;
    }

    /**
     * Returns the associated elliptic curve domain
     * parameters.
     * @return the EC domain parameters.
     */
    public ECParameterSpec getParams() {
        return params;
    }
}
