/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.x509;

import java.io.IOException;
import java.util.Vector;
import java.util.List;
import java.util.Collections;

import sun.security.util.*;

/**
 * This class defines the certificate policy set ASN.1 object.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class CertificatePolicySet {

    private final Vector<CertificatePolicyId> ids;

    /**
     * The default constructor for this class.
     *
     * @param ids the sequence of CertificatePolicyId's.
     */
    public CertificatePolicySet(Vector<CertificatePolicyId> ids) {
        this.ids = ids;
    }

    /**
     * Create the object from the DerValue.
     *
     * @param in the passed DerInputStream.
     * @exception IOException on decoding errors.
     */
    public CertificatePolicySet(DerInputStream in) throws IOException {
        ids = new Vector<CertificatePolicyId>();
        DerValue[] seq = in.getSequence(5);

        for (int i = 0; i < seq.length; i++) {
            CertificatePolicyId id = new CertificatePolicyId(seq[i]);
            ids.addElement(id);
        }
    }

    /**
     * Return printable form of the object.
     */
    public String toString() {
        String s = "CertificatePolicySet:[\n"
                 + ids.toString()
                 + "]\n";

        return (s);
    }

    /**
     * Encode the policy set to the output stream.
     *
     * @param out the DerOutputStream to encode the data to.
     */
    public void encode(DerOutputStream out) throws IOException {
        DerOutputStream tmp = new DerOutputStream();

        for (int i = 0; i < ids.size(); i++) {
            ((CertificatePolicyId)ids.elementAt(i)).encode(tmp);
        }
        out.write(DerValue.tag_Sequence,tmp);
    }

    /**
     * Return the sequence of CertificatePolicyIds.
     *
     * @return A List containing the CertificatePolicyId objects.
     *
     */
    public List<CertificatePolicyId> getCertPolicyIds() {
        return Collections.unmodifiableList(ids);
    }
}
