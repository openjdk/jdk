/*
 * Copyright 2001-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @test 4422738
 * @compile -source 1.4 InvalidParameters.java
 * @run main InvalidParameters
 * @summary Make sure PKIXParameters(Set) and setTrustAnchors() detects invalid
 *          parameters and throws correct exceptions
 */
import java.security.InvalidAlgorithmParameterException;
import java.security.PublicKey;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.Collections;
import java.util.Set;

public class InvalidParameters {

    public static void main(String[] args) throws Exception {

        TrustAnchor anchor = new TrustAnchor("cn=sean", new TestPublicKey(), null);
        PKIXParameters params = new PKIXParameters(Collections.singleton(anchor));

        // make sure empty Set of anchors throws InvAlgParamExc
        try {
            PKIXParameters p = new PKIXParameters(Collections.EMPTY_SET);
            throw new Exception("should have thrown InvalidAlgorithmParameterExc");
        } catch (InvalidAlgorithmParameterException iape) { }
        try {
            params.setTrustAnchors(Collections.EMPTY_SET);
            throw new Exception("should have thrown InvalidAlgorithmParameterExc");
        } catch (InvalidAlgorithmParameterException iape) { }

        // make sure null Set of anchors throws NullPointerException
        try {
            PKIXParameters p = new PKIXParameters((Set) null);
            throw new Exception("should have thrown NullPointerException");
        } catch (NullPointerException npe) { }
        try {
            params.setTrustAnchors((Set) null);
            throw new Exception("should have thrown NullPointerException");
        } catch (NullPointerException npe) { }

        // make sure Set of invalid objects throws ClassCastException
        try {
            PKIXParameters p = new PKIXParameters(Collections.singleton(new String()));
            throw new Exception("should have thrown ClassCastException");
        } catch (ClassCastException cce) { }
        try {
            params.setTrustAnchors(Collections.singleton(new String()));
            throw new Exception("should have thrown ClassCastException");
        } catch (ClassCastException cce) { }
    }

    static class TestPublicKey implements PublicKey {
        public String getAlgorithm() { return "Test"; }
        public String getFormat() { return null; }
        public byte[] getEncoded() { return null; }
    }
}
