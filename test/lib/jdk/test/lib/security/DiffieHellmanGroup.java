/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.security;

import java.math.BigInteger;

/**
 * An enumeration of DH groups for tests.
 */
public enum DiffieHellmanGroup {

    /**
     * RFC 7919 - ffdhe2048.
     */
    ffdhe2048(new BigInteger("FFFFFFFFFFFFFFFFADF85458A2BB4A9AAFDC5620273D3CF1" +
                    "D8B9C583CE2D3695A9E13641146433FBCC939DCE249B3EF9" +
                    "7D2FE363630C75D8F681B202AEC4617AD3DF1ED5D5FD6561" +
                    "2433F51F5F066ED0856365553DED1AF3B557135E7F57C935" +
                    "984F0C70E0E68B77E2A689DAF3EFE8721DF158A136ADE735" +
                    "30ACCA4F483A797ABC0AB182B324FB61D108A94BB2C8E3FB" +
                    "B96ADAB760D7F4681D4F42A3DE394DF4AE56EDE76372BB19" +
                    "0B07A7C8EE0A6D709E02FCE1CDF7E2ECC03404CD28342F61" +
                    "9172FE9CE98583FF8E4F1232EEF28183C3FE3B1B4C6FAD73" +
                    "3BB5FCBC2EC22005C58EF1837D1683B2C6F34A26C1B2EFFA" +
                    "886B423861285C97FFFFFFFFFFFFFFFF", 16), 2),
    /**
     * RFC 7919 - ffdhe3072.
     */
    ffdhe3072(new BigInteger("FFFFFFFFFFFFFFFFADF85458A2BB4A9AAFDC5620273D3CF1" +
                    "D8B9C583CE2D3695A9E13641146433FBCC939DCE249B3EF9" +
                    "7D2FE363630C75D8F681B202AEC4617AD3DF1ED5D5FD6561" +
                    "2433F51F5F066ED0856365553DED1AF3B557135E7F57C935" +
                    "984F0C70E0E68B77E2A689DAF3EFE8721DF158A136ADE735" +
                    "30ACCA4F483A797ABC0AB182B324FB61D108A94BB2C8E3FB" +
                    "B96ADAB760D7F4681D4F42A3DE394DF4AE56EDE76372BB19" +
                    "0B07A7C8EE0A6D709E02FCE1CDF7E2ECC03404CD28342F61" +
                    "9172FE9CE98583FF8E4F1232EEF28183C3FE3B1B4C6FAD73" +
                    "3BB5FCBC2EC22005C58EF1837D1683B2C6F34A26C1B2EFFA" +
                    "886B4238611FCFDCDE355B3B6519035BBC34F4DEF99C0238" +
                    "61B46FC9D6E6C9077AD91D2691F7F7EE598CB0FAC186D91C" +
                    "AEFE130985139270B4130C93BC437944F4FD4452E2D74DD3" +
                    "64F2E21E71F54BFF5CAE82AB9C9DF69EE86D2BC522363A0D" +
                    "ABC521979B0DEADA1DBF9A42D5C4484E0ABCD06BFA53DDEF" +
                    "3C1B20EE3FD59D7C25E41D2B66C62E37FFFFFFFFFFFFFFFF", 16), 2),
    /**
     * RFC 7919 - ffdhe4096.
     */
    ffdhe4096(new BigInteger("FFFFFFFFFFFFFFFFADF85458A2BB4A9AAFDC5620273D3CF1" +
                    "D8B9C583CE2D3695A9E13641146433FBCC939DCE249B3EF9" +
                    "7D2FE363630C75D8F681B202AEC4617AD3DF1ED5D5FD6561" +
                    "2433F51F5F066ED0856365553DED1AF3B557135E7F57C935" +
                    "984F0C70E0E68B77E2A689DAF3EFE8721DF158A136ADE735" +
                    "30ACCA4F483A797ABC0AB182B324FB61D108A94BB2C8E3FB" +
                    "B96ADAB760D7F4681D4F42A3DE394DF4AE56EDE76372BB19" +
                    "0B07A7C8EE0A6D709E02FCE1CDF7E2ECC03404CD28342F61" +
                    "9172FE9CE98583FF8E4F1232EEF28183C3FE3B1B4C6FAD73" +
                    "3BB5FCBC2EC22005C58EF1837D1683B2C6F34A26C1B2EFFA" +
                    "886B4238611FCFDCDE355B3B6519035BBC34F4DEF99C0238" +
                    "61B46FC9D6E6C9077AD91D2691F7F7EE598CB0FAC186D91C" +
                    "AEFE130985139270B4130C93BC437944F4FD4452E2D74DD3" +
                    "64F2E21E71F54BFF5CAE82AB9C9DF69EE86D2BC522363A0D" +
                    "ABC521979B0DEADA1DBF9A42D5C4484E0ABCD06BFA53DDEF" +
                    "3C1B20EE3FD59D7C25E41D2B669E1EF16E6F52C3164DF4FB" +
                    "7930E9E4E58857B6AC7D5F42D69F6D187763CF1D55034004" +
                    "87F55BA57E31CC7A7135C886EFB4318AED6A1E012D9E6832" +
                    "A907600A918130C46DC778F971AD0038092999A333CB8B7A" +
                    "1A1DB93D7140003C2A4ECEA9F98D0ACC0A8291CDCEC97DCF" +
                    "8EC9B55A7F88A46B4DB5A851F44182E1C68A007E5E655F6A" +
                    "FFFFFFFFFFFFFFFF", 16), 2);


    public BigInteger getPrime() {
        return prime;
    }

    private final BigInteger prime;

    public BigInteger getBase() {
        return base;
    }

    private final BigInteger base;

    DiffieHellmanGroup(BigInteger prime, int base) {
        this.prime = prime;
        this.base = BigInteger.valueOf(base);
    }
}
