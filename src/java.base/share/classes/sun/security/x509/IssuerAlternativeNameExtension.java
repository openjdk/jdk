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

import sun.security.util.*;

/**
 * This represents the Issuer Alternative Name Extension.
 *
 * This extension, if present, allows the issuer to specify multiple
 * alternative names.
 *
 * <p>Extensions are represented as a sequence of the extension identifier
 * (Object Identifier), a boolean flag stating whether the extension is to
 * be treated as being critical and the extension value itself (this is again
 * a DER encoding of the extension value).
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see Extension
 */
public class IssuerAlternativeNameExtension extends Extension {

    public static final String NAME = "IssuerAlternativeName";

    // private data members
    GeneralNames names;

    // Encode this extension
    private void encodeThis() throws IOException {
        if (names == null || names.isEmpty()) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream os = new DerOutputStream();
        names.encode(os);
        this.extensionValue = os.toByteArray();
    }

    /**
     * Create a IssuerAlternativeNameExtension with the passed GeneralNames.
     *
     * @param names the GeneralNames for the issuer.
     * @exception IOException on error.
     */
    public IssuerAlternativeNameExtension(GeneralNames names)
    throws IOException {
        this.names = names;
        this.extensionId = PKIXExtensions.IssuerAlternativeName_Id;
        this.critical = false;
        encodeThis();
    }

    /**
     * Create a IssuerAlternativeNameExtension with the passed criticality
     * and GeneralNames.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param names the GeneralNames for the issuer.
     * @exception IOException on error.
     */
    public IssuerAlternativeNameExtension(Boolean critical, GeneralNames names)
    throws IOException {
        this.names = names;
        this.extensionId = PKIXExtensions.IssuerAlternativeName_Id;
        this.critical = critical.booleanValue();
        encodeThis();
    }

    /**
     * Create a default IssuerAlternativeNameExtension.
     */
    public IssuerAlternativeNameExtension() {
        extensionId = PKIXExtensions.IssuerAlternativeName_Id;
        critical = false;
        names = new GeneralNames();
    }

    /**
     * Create the extension from the passed DER encoded value.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value an array of DER encoded bytes of the actual value.
     * @exception ClassCastException if value is not an array of bytes
     * @exception IOException on error.
     */
    public IssuerAlternativeNameExtension(Boolean critical, Object value)
    throws IOException {
        this.extensionId = PKIXExtensions.IssuerAlternativeName_Id;
        this.critical = critical.booleanValue();
        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        if (val.data == null) {
            names = new GeneralNames();
            return;
        }

        names = new GeneralNames(val);
    }

    /**
     * Returns a printable representation of the IssuerAlternativeName.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
            .append("IssuerAlternativeName [\n");
        if (names == null) {
            sb.append("  null\n");
        } else {
            for (GeneralName name : names.names()) {
                sb.append("  ")
                    .append(name)
                    .append('\n');
            }
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * Write the extension to the OutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     * @exception IOException on encoding error.
     */
    @Override
    public void encode(DerOutputStream out) throws IOException {
        if (extensionValue == null) {
            extensionId = PKIXExtensions.IssuerAlternativeName_Id;
            critical = false;
            encodeThis();
        }
        super.encode(out);
    }

    public GeneralNames getNames() {
        return names;
    }

    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
        return NAME;
    }
}
