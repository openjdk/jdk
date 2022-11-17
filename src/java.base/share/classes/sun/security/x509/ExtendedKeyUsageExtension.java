/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;

/**
 * This class defines the Extended Key Usage Extension, which
 * indicates one or more purposes for which the certified public key
 * may be used, in addition to or in place of the basic purposes
 * indicated in the key usage extension field.  This field is defined
 * as follows:<p>
 *
 * id-ce-extKeyUsage OBJECT IDENTIFIER ::= {id-ce 37}<p>
 *
 * ExtKeyUsageSyntax ::= SEQUENCE SIZE (1..MAX) OF KeyPurposeId<p>
 *
 * KeyPurposeId ::= OBJECT IDENTIFIER<p>
 *
 * Key purposes may be defined by any organization with a need. Object
 * identifiers used to identify key purposes shall be assigned in
 * accordance with IANA or ITU-T Rec. X.660 | ISO/IEC/ITU 9834-1.<p>
 *
 * This extension may, at the option of the certificate issuer, be
 * either critical or non-critical.<p>
 *
 * If the extension is flagged critical, then the certificate MUST be
 * used only for one of the purposes indicated.<p>
 *
 * If the extension is flagged non-critical, then it indicates the
 * intended purpose or purposes of the key, and may be used in finding
 * the correct key/certificate of an entity that has multiple
 * keys/certificates. It is an advisory field and does not imply that
 * usage of the key is restricted by the certification authority to
 * the purpose indicated. Certificate using applications may
 * nevertheless require that a particular purpose be indicated in
 * order for the certificate to be acceptable to that application.<p>
 *
 * If a certificate contains both a critical key usage field and a
 * critical extended key usage field, then both fields MUST be
 * processed independently and the certificate MUST only be used for a
 * purpose consistent with both fields.  If there is no purpose
 * consistent with both fields, then the certificate MUST NOT be used
 * for any purpose.
 *
 * @since       1.4
 */
public class ExtendedKeyUsageExtension extends Extension {

    public static final String NAME = "ExtendedKeyUsage";

    /**
     * Vector of KeyUsages for this object.
     */
    private Vector<ObjectIdentifier> keyUsages;

    // Encode this extension value.
    private void encodeThis() throws IOException {
        if (keyUsages == null || keyUsages.isEmpty()) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream os = new DerOutputStream();
        DerOutputStream tmp = new DerOutputStream();

        for (int i = 0; i < keyUsages.size(); i++) {
            tmp.putOID(keyUsages.elementAt(i));
        }

        os.write(DerValue.tag_Sequence, tmp);
        this.extensionValue = os.toByteArray();
    }

    /**
     * Create a ExtendedKeyUsageExtension object from
     * a Vector of Key Usages; the criticality is set to false.
     *
     * @param keyUsages the Vector of KeyUsages (ObjectIdentifiers)
     */
    public ExtendedKeyUsageExtension(Vector<ObjectIdentifier> keyUsages)
    throws IOException {
        this(Boolean.FALSE, keyUsages);
    }

    /**
     * Create a ExtendedKeyUsageExtension object from
     * a Vector of KeyUsages with specified criticality.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param keyUsages the Vector of KeyUsages (ObjectIdentifiers)
     */
    public ExtendedKeyUsageExtension(Boolean critical, Vector<ObjectIdentifier> keyUsages)
    throws IOException {
        this.keyUsages = keyUsages;
        this.extensionId = PKIXExtensions.ExtendedKeyUsage_Id;
        this.critical = critical.booleanValue();
        encodeThis();
    }

    /**
     * Create the extension from its DER encoded value and criticality.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value an array of DER encoded bytes of the actual value.
     * @exception ClassCastException if value is not an array of bytes
     * @exception IOException on error.
     */
    public ExtendedKeyUsageExtension(Boolean critical, Object value)
    throws IOException {
        this.extensionId = PKIXExtensions.ExtendedKeyUsage_Id;
        this.critical = critical.booleanValue();
        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        if (val.tag != DerValue.tag_Sequence) {
            throw new IOException("Invalid encoding for " +
                                   "ExtendedKeyUsageExtension.");
        }
        keyUsages = new Vector<>();
        while (val.data.available() != 0) {
            DerValue seq = val.data.getDerValue();
            ObjectIdentifier usage = seq.getOID();
            keyUsages.addElement(usage);
        }
    }

    /**
     * Return the extension as user readable string.
     */
    public String toString() {
        if (keyUsages == null) return "";
        String usage = "  ";
        boolean first = true;
        for (ObjectIdentifier oid: keyUsages) {
            if(!first) {
                usage += "\n  ";
            }

            String res = oid.toString();
            KnownOIDs os = KnownOIDs.findMatch(res);
            if (os != null) {
                usage += os.stdName();
            } else {
                usage += res;
            }
            first = false;
        }
        return super.toString() + "ExtendedKeyUsages [\n"
               + usage + "\n]\n";
    }

    /**
     * Write the extension to the DerOutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     * @exception IOException on encoding errors.
     */
    @Override
    public void encode(DerOutputStream out) throws IOException {
        if (extensionValue == null) {
          extensionId = PKIXExtensions.ExtendedKeyUsage_Id;
          critical = false;
          encodeThis();
        }
        super.encode(out);
    }

    /**
     * Get the keyUsages value.
     */
    public Vector<ObjectIdentifier> getUsages() {
        return keyUsages;
    }



    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
        return NAME;
    }

    public List<String> getExtendedKeyUsage() {
        List<String> al = new ArrayList<>(keyUsages.size());
        for (ObjectIdentifier oid : keyUsages) {
            al.add(oid.toString());
        }
        return al;
    }

}
