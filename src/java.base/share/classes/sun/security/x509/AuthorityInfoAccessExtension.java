/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

/**
 * The Authority Information Access Extension (OID = 1.3.6.1.5.5.7.1.1).
 * <p>
 * The AIA extension identifies how to access CA information and services
 * for the certificate in which it appears. It enables CAs to issue their
 * certificates pre-configured with the URLs appropriate for contacting
 * services relevant to those certificates. For example, a CA may issue a
 * certificate that identifies the specific OCSP Responder to use when
 * performing on-line validation of that certificate.
 * <p>
 * This extension is defined in <a href="https://tools.ietf.org/html/rfc5280">
 * Internet X.509 PKI Certificate and Certificate Revocation List
 * (CRL) Profile</a>. The profile permits
 * the extension to be included in end-entity or CA certificates,
 * and it must be marked as non-critical. Its ASN.1 definition is as follows:
 * <pre>
 *   id-pe-authorityInfoAccess OBJECT IDENTIFIER ::= { id-pe 1 }
 *
 *   AuthorityInfoAccessSyntax  ::=
 *         SEQUENCE SIZE (1..MAX) OF AccessDescription
 *
 *   AccessDescription  ::=  SEQUENCE {
 *         accessMethod          OBJECT IDENTIFIER,
 *         accessLocation        GeneralName  }
 * </pre>
 *
 * @see Extension
 */

public class AuthorityInfoAccessExtension extends Extension {

    public static final String NAME = "AuthorityInfoAccess";

    /**
     * The List of AccessDescription objects.
     */
    private List<AccessDescription> accessDescriptions;

    /**
     * Create an AuthorityInfoAccessExtension from a List of
     * AccessDescription; the criticality is set to false.
     *
     * @param accessDescriptions the List of AccessDescription,
     *                           cannot be null or empty.
     */
    public AuthorityInfoAccessExtension(
            List<AccessDescription> accessDescriptions) {
        if (accessDescriptions == null || accessDescriptions.isEmpty()) {
            throw new IllegalArgumentException("accessDescriptions is null or empty");
        }
        this.extensionId = PKIXExtensions.AuthInfoAccess_Id;
        this.critical = false;
        this.accessDescriptions = accessDescriptions;
        encodeThis();
    }

    /**
     * Create the extension from the passed DER encoded value of the same.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value Array of DER encoded bytes of the actual value.
     * @exception IOException on error.
     */
    public AuthorityInfoAccessExtension(Boolean critical, Object value)
            throws IOException {
        this.extensionId = PKIXExtensions.AuthInfoAccess_Id;
        this.critical = critical.booleanValue();

        if (!(value instanceof byte[])) {
            throw new IOException("Illegal argument type");
        }

        extensionValue = (byte[])value;
        DerValue val = new DerValue(extensionValue);
        if (val.tag != DerValue.tag_Sequence) {
            throw new IOException("Invalid encoding for " +
                                  "AuthorityInfoAccessExtension.");
        }
        accessDescriptions = new ArrayList<>();
        while (val.data.available() != 0) {
            DerValue seq = val.data.getDerValue();
            AccessDescription accessDescription = new AccessDescription(seq);
            accessDescriptions.add(accessDescription);
        }
    }

    /**
     * Return the list of AccessDescription objects.
     */
    public List<AccessDescription> getAccessDescriptions() {
        return accessDescriptions;
    }

    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Write the extension to the DerOutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     */
    @Override
    public void encode(DerOutputStream out) {
        if (this.extensionValue == null) {
            this.extensionId = PKIXExtensions.AuthInfoAccess_Id;
            this.critical = false;
            encodeThis();
        }
        super.encode(out);
    }

    // Encode this extension value
    private void encodeThis() {
        if (accessDescriptions.isEmpty()) {
            this.extensionValue = null;
        } else {
            DerOutputStream ads = new DerOutputStream();
            for (AccessDescription accessDescription : accessDescriptions) {
                accessDescription.encode(ads);
            }
            DerOutputStream seq = new DerOutputStream();
            seq.write(DerValue.tag_Sequence, ads);
            this.extensionValue = seq.toByteArray();
        }
    }

    /**
     * Return the extension as user readable string.
     */
    public String toString() {
        return super.toString() + "AuthorityInfoAccess [\n  "
               + accessDescriptions + "\n]\n";
    }

}
