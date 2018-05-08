/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ec;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;

public class XECParameters {

    // Naming/identification parameters
    private final ObjectIdentifier oid;
    private final String name;

    // Curve/field parameters
    private final int bits;
    private final BigInteger p;
    private final int logCofactor;
    private final int a24;
    private final byte basePoint;

    /**
     *
     * Construct an object holding the supplied parameters. No parameters are
     * checked, so this method always succeeds. This method supports
     * Montgomery curves of the form y^2 = x^3 + ax^2 + x.
     *
     * @param bits The number of relevant bits in a public/private key.
     * @param p The prime that defines the finite field.
     * @param a24 The value of (a - 2) / 4, where a is the second-degree curve
     *            coefficient.
     * @param basePoint The point that generates the desired group
     * @param logCofactor The base-2 logarithm of the cofactor of the curve
     * @param oid
     * @param name
     */
    public XECParameters(int bits, BigInteger p, int a24,
                         byte basePoint, int logCofactor,
                         ObjectIdentifier oid, String name) {

        this.bits = bits;
        this.logCofactor = logCofactor;
        this.p = p;
        this.a24 = a24;
        this.basePoint = basePoint;
        this.oid = oid;
        this.name = name;

    }

    public int getBits() {
        return bits;
    }
    public int getBytes() {
        return (bits + 7) / 8;
    }
    public int getLogCofactor() {
        return logCofactor;
    }
    public BigInteger getP() {
        return p;
    }
    public int getA24() {
        return a24;
    }
    public byte getBasePoint() {
        return basePoint;
    }
    public ObjectIdentifier getOid() {
        return oid;
    }
    public String getName() {
        return name;
    }

    private static final Map<Integer, XECParameters> SIZE_MAP;
    private static final Map<ObjectIdentifier, XECParameters> OID_MAP;
    private static final Map<String, XECParameters> NAME_MAP;

    static {
        final BigInteger TWO = BigInteger.valueOf(2);

        Map<Integer, XECParameters> bySize = new HashMap<>();
        Map<ObjectIdentifier, XECParameters> byOid = new HashMap<>();
        Map<String, XECParameters> byName = new HashMap<>();

        // set up X25519
        try {
            BigInteger p = TWO.pow(255).subtract(BigInteger.valueOf(19));
            addParameters(255, p, 121665, (byte) 0x09, 3,
                new int[]{1, 3, 101, 110}, NamedParameterSpec.X25519.getName(),
                bySize, byOid, byName);

        } catch (IOException ex) {
            // Unable to set X25519 parameters---it will be disabled
        }

        // set up X448
        try {
            BigInteger p = TWO.pow(448).subtract(TWO.pow(224))
                .subtract(BigInteger.ONE);
            addParameters(448, p, 39081, (byte) 0x05, 2,
                new int[]{1, 3, 101, 111}, NamedParameterSpec.X448.getName(),
                bySize, byOid, byName);

        } catch (IOException ex) {
            // Unable to set X448 parameters---it will be disabled
        }

        SIZE_MAP = Collections.unmodifiableMap(bySize);
        OID_MAP = Collections.unmodifiableMap(byOid);
        NAME_MAP = Collections.unmodifiableMap(byName);
    }

    private static void addParameters(int bits, BigInteger p, int a24,
        byte basePoint, int logCofactor, int[] oidBytes, String name,
        Map<Integer, XECParameters> bySize,
        Map<ObjectIdentifier, XECParameters> byOid,
        Map<String, XECParameters> byName) throws IOException {

        ObjectIdentifier oid = new ObjectIdentifier(oidBytes);
        XECParameters params =
            new XECParameters(bits, p, a24, basePoint, logCofactor, oid, name);
        bySize.put(bits, params);
        byOid.put(oid, params);
        byName.put(name, params);
    }

    public static Optional<XECParameters> getByOid(ObjectIdentifier id) {
        return Optional.ofNullable(OID_MAP.get(id));
    }
    public static Optional<XECParameters> getBySize(int size) {
        return Optional.ofNullable(SIZE_MAP.get(size));
    }
    public static Optional<XECParameters> getByName(String name) {
        return Optional.ofNullable(NAME_MAP.get(name));
    }

    boolean oidEquals(XECParameters other) {
        return oid.equals(other.getOid());
    }

    // Utility method that is used by the methods below to handle exception
    // suppliers
    private static
    <A, B> Supplier<B> apply(final Function<A, B> func, final A a) {
        return new Supplier<B>() {
            @Override
            public B get() {
                return func.apply(a);
            }
        };
    }

    /**
     * Get parameters by key size, or throw an exception if no parameters are
     * defined for the specified key size. This method is used in several
     * contexts that should throw different exceptions when the parameters
     * are not found. The first argument is a function that produces the
     * desired exception.
     *
     * @param exception a function that produces an exception from a string
     * @param size the desired key size
     * @param <T> the type of exception that is thrown
     * @return the parameters for the specified key size
     * @throws T when suitable parameters do not exist
     */
    public static
    <T extends Throwable>
    XECParameters getBySize(Function<String, T> exception,
                            int size) throws T {

        Optional<XECParameters> xecParams = getBySize(size);
        return xecParams.orElseThrow(
            apply(exception, "Unsupported size: " + size));
    }

    /**
     * Get parameters by algorithm ID, or throw an exception if no
     * parameters are defined for the specified ID. This method is used in
     * several contexts that should throw different exceptions when the
     * parameters are not found. The first argument is a function that produces
     * the desired exception.
     *
     * @param exception a function that produces an exception from a string
     * @param algId the algorithm ID
     * @param <T> the type of exception that is thrown
     * @return the parameters for the specified algorithm ID
     * @throws T when suitable parameters do not exist
     */
    public static
    <T extends Throwable>
    XECParameters get(Function<String, T> exception,
                      AlgorithmId algId) throws T {

        Optional<XECParameters> xecParams = getByOid(algId.getOID());
        return xecParams.orElseThrow(
            apply(exception, "Unsupported OID: " + algId.getOID()));
    }

    /**
     * Get parameters by algorithm parameter spec, or throw an exception if no
     * parameters are defined for the spec. This method is used in
     * several contexts that should throw different exceptions when the
     * parameters are not found. The first argument is a function that produces
     * the desired exception.
     *
     * @param exception a function that produces an exception from a string
     * @param params the algorithm parameters spec
     * @param <T> the type of exception that is thrown
     * @return the parameters for the spec
     * @throws T when suitable parameters do not exist
     */
    public static
    <T extends Throwable>
    XECParameters get(Function<String, T> exception,
                      AlgorithmParameterSpec params) throws T {

        if (params instanceof NamedParameterSpec) {
            NamedParameterSpec namedParams = (NamedParameterSpec) params;
            Optional<XECParameters> xecParams =
                getByName(namedParams.getName());
            return xecParams.orElseThrow(
                apply(exception, "Unsupported name: " + namedParams.getName()));
        } else {
            throw exception.apply("Only NamedParameterSpec is supported.");
        }
    }
}

