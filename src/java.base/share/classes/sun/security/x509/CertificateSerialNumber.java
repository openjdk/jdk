/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.x509;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Random;

import sun.security.util.*;

/**
 * This class defines the SerialNumber attribute for the Certificate.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see DerEncoder
 */
public class CertificateSerialNumber implements DerEncoder {

    public static final String NAME = "serialNumber";

    private SerialNumber        serial;

    /**
     * Default constructor for the certificate attribute.
     *
     * @param num the serial number for the certificate.
     */
    public CertificateSerialNumber(BigInteger num) {
      this.serial = new SerialNumber(num);
    }

    /**
     * Default constructor for the certificate attribute.
     *
     * @param num the serial number for the certificate.
     */
    public CertificateSerialNumber(int num) {
      this.serial = new SerialNumber(num);
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param in the DerInputStream to read the serial number from.
     * @exception IOException on decoding errors.
     */
    public CertificateSerialNumber(DerInputStream in) throws IOException {
        serial = new SerialNumber(in);
    }

    /**
     * Create the object, decoding the values from the passed stream.
     *
     * @param in the InputStream to read the serial number from.
     * @exception IOException on decoding errors.
     */
    public CertificateSerialNumber(InputStream in) throws IOException {
        serial = new SerialNumber(in);
    }

    /**
     * Create the object, decoding the values from the passed DerValue.
     *
     * @param val the DER encoded value.
     * @exception IOException on decoding errors.
     */
    public CertificateSerialNumber(DerValue val) throws IOException {
        serial = new SerialNumber(val);
    }

    /**
     * Return the serial number as user readable string.
     */
    public String toString() {
        if (serial == null) return "";
        return serial.toString();
    }

    /**
     * Encode the serial number in DER form to the stream.
     *
     * @param out the DerOutputStream to marshal the contents to.
     * @exception IOException on errors.
     */
    @Override
    public void encode(DerOutputStream out) throws IOException {
        serial.encode(out);
    }

    public SerialNumber getSerial() {
        return serial;
    }

    /**
     * Generates a new random serial number.
     */
    public static CertificateSerialNumber newRandom64bit(Random rand) {
        while (true) {
            BigInteger b = new BigInteger(64, rand);
            if (b.signum() != 0) {
                return new CertificateSerialNumber(b);
            }
        }
    }
}
