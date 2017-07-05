/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Enumeration;

import sun.security.util.*;

/**
 * Represents the Delta CRL Indicator Extension.
 *
 * <p>
 * The extension identifies a CRL as being a delta CRL.
 * Delta CRLs contain updates to revocation information previously distributed,
 * rather than all the information that would appear in a complete CRL.
 * The extension contains a CRL number that identifies the CRL, complete for a
 * given scope, that was used as the starting point in the generation of
 * this delta CRL.
 *
 * <p>
 * The extension is defined in Section 5.2.4 of
 * <a href="http://www.ietf.org/rfc/rfc3280.txt">Internet X.509 PKI Certific
ate and Certificate Revocation List (CRL) Profile</a>.
 *
 * <p>
 * Its ASN.1 definition is as follows:
 * <pre>
 *     id-ce-deltaCRLIndicator OBJECT IDENTIFIER ::= { id-ce 27 }
 *
 *     BaseCRLNumber ::= CRLNumber
 *     CRLNumber ::= INTEGER (0..MAX)
 * </pre>
 *
 * @since 1.6
 */
public class DeltaCRLIndicatorExtension extends CRLNumberExtension {

    /**
     * Attribute name.
     */
    public static final String NAME = "DeltaCRLIndicator";

    private static final String LABEL = "Base CRL Number";

    /**
     * Creates a delta CRL indicator extension with the integer value .
     * The criticality is set to true.
     *
     * @param crlNum the value to be set for the extension.
     */
    public DeltaCRLIndicatorExtension(int crlNum) throws IOException {
        super(PKIXExtensions.DeltaCRLIndicator_Id, true,
            BigInteger.valueOf(crlNum), NAME, LABEL);
    }

    /**
     * Creates a delta CRL indictor extension with the BigInteger value .
     * The criticality is set to true.
     *
     * @param crlNum the value to be set for the extension.
     */
    public DeltaCRLIndicatorExtension(BigInteger crlNum) throws IOException {
        super(PKIXExtensions.DeltaCRLIndicator_Id, true, crlNum, NAME, LABEL);
    }

    /**
     * Creates the extension from the passed DER encoded value of the same.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value an array of DER encoded bytes of the actual value.
     * @exception ClassCastException if value is not an array of bytes
     * @exception IOException on decoding error.
     */
    public DeltaCRLIndicatorExtension(Boolean critical, Object value)
    throws IOException {
        super(PKIXExtensions.DeltaCRLIndicator_Id, critical.booleanValue(),
            value, NAME, LABEL);
    }

    /**
     * Writes the extension to the DerOutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     * @exception IOException on encoding errors.
     */
    public void encode(OutputStream out) throws IOException {
       DerOutputStream  tmp = new DerOutputStream();
        super.encode(out, PKIXExtensions.DeltaCRLIndicator_Id, true);
    }
}
