/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Locale;

import sun.security.util.*;

/**
 * This class implements the DNSName as required by the GeneralNames
 * ASN.1 object.
 * <p>
 * [RFC2459] When the subjectAltName extension contains a domain name service
 * label, the domain name MUST be stored in the dNSName (an IA5String).
 * The name MUST be in the "preferred name syntax," as specified by RFC
 * 1034 [RFC 1034]. Note that while upper and lower case letters are
 * allowed in domain names, no signifigance is attached to the case.  In
 * addition, while the string " " is a legal domain name, subjectAltName
 * extensions with a dNSName " " are not permitted.  Finally, the use of
 * the DNS representation for Internet mail addresses (wpolk.nist.gov
 * instead of wpolk@nist.gov) is not permitted; such identities are to
 * be encoded as rfc822Name.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class DNSName implements GeneralNameInterface {
    private String name;

    private static final String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String digitsAndHyphen = "0123456789-";
    private static final String alphaDigitsAndHyphen = alpha + digitsAndHyphen;

    /**
     * Create the DNSName object from the passed encoded Der value.
     *
     * @param derValue the encoded DER DNSName.
     * @exception IOException on error.
     */
    public DNSName(DerValue derValue) throws IOException {
        name = derValue.getIA5String();
    }

    /**
     * Create the DNSName object with the specified name.
     *
     * @param name the DNSName.
     * @throws IOException if the name is not a valid DNSName subjectAltName
     */
    public DNSName(String name) throws IOException {
        if (name == null || name.length() == 0)
            throw new IOException("DNS name must not be null");
        if (name.indexOf(' ') != -1)
            throw new IOException("DNS names or NameConstraints with blank components are not permitted");
        if (name.charAt(0) == '.' || name.charAt(name.length() -1) == '.')
            throw new IOException("DNS names or NameConstraints may not begin or end with a .");
        //Name will consist of label components separated by "."
        //startIndex is the index of the first character of a component
        //endIndex is the index of the last character of a component plus 1
        for (int endIndex,startIndex=0; startIndex < name.length(); startIndex = endIndex+1) {
            endIndex = name.indexOf('.', startIndex);
            if (endIndex < 0) {
                endIndex = name.length();
            }
            if ((endIndex-startIndex) < 1)
                throw new IOException("DNSName SubjectAltNames with empty components are not permitted");

            //DNSName components must begin with a letter A-Z or a-z
            if (alpha.indexOf(name.charAt(startIndex)) < 0)
                throw new IOException("DNSName components must begin with a letter");
            //nonStartIndex: index for characters in the component beyond the first one
            for (int nonStartIndex=startIndex+1; nonStartIndex < endIndex; nonStartIndex++) {
                char x = name.charAt(nonStartIndex);
                if ((alphaDigitsAndHyphen).indexOf(x) < 0)
                    throw new IOException("DNSName components must consist of letters, digits, and hyphens");
            }
        }
        this.name = name;
    }

    /**
     * Return the type of the GeneralName.
     */
    public int getType() {
        return (GeneralNameInterface.NAME_DNS);
    }

    /**
     * Return the actual name value of the GeneralName.
     */
    public String getName() {
        return name;
    }

    /**
     * Encode the DNS name into the DerOutputStream.
     *
     * @param out the DER stream to encode the DNSName to.
     * @exception IOException on encoding errors.
     */
    public void encode(DerOutputStream out) throws IOException {
        out.putIA5String(name);
    }

    /**
     * Convert the name into user readable string.
     */
    public String toString() {
        return ("DNSName: " + name);
    }

    /**
     * Compares this name with another, for equality.
     *
     * @return true iff the names are equivalent
     * according to RFC2459.
     */
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (!(obj instanceof DNSName))
            return false;

        DNSName other = (DNSName)obj;

        // RFC2459 mandates that these names are
        // not case-sensitive
        return name.equalsIgnoreCase(other.name);
    }

    /**
     * Returns the hash code value for this object.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        return name.toUpperCase(Locale.ENGLISH).hashCode();
    }

    /**
     * Return type of constraint inputName places on this name:<ul>
     *   <li>NAME_DIFF_TYPE = -1: input name is different type from name (i.e. does not constrain).
     *   <li>NAME_MATCH = 0: input name matches name.
     *   <li>NAME_NARROWS = 1: input name narrows name (is lower in the naming subtree)
     *   <li>NAME_WIDENS = 2: input name widens name (is higher in the naming subtree)
     *   <li>NAME_SAME_TYPE = 3: input name does not match or narrow name, but is same type.
     * </ul>.  These results are used in checking NameConstraints during
     * certification path verification.
     * <p>
     * RFC2459: DNS name restrictions are expressed as foo.bar.com. Any subdomain
     * satisfies the name constraint. For example, www.foo.bar.com would
     * satisfy the constraint but bigfoo.bar.com would not.
     * <p>
     * draft-ietf-pkix-new-part1-00.txt:  DNS name restrictions are expressed as foo.bar.com.
     * Any DNS name that
     * can be constructed by simply adding to the left hand side of the name
     * satisfies the name constraint. For example, www.foo.bar.com would
     * satisfy the constraint but foo1.bar.com would not.
     * <p>
     * RFC1034: By convention, domain names can be stored with arbitrary case, but
     * domain name comparisons for all present domain functions are done in a
     * case-insensitive manner, assuming an ASCII character set, and a high
     * order zero bit.
     *
     * @param inputName to be checked for being constrained
     * @return constraint type above
     * @throws UnsupportedOperationException if name is not exact match, but narrowing and widening are
     *          not supported for this name type.
     */
    public int constrains(GeneralNameInterface inputName) throws UnsupportedOperationException {
        int constraintType;
        if (inputName == null)
            constraintType = NAME_DIFF_TYPE;
        else if (inputName.getType() != NAME_DNS)
            constraintType = NAME_DIFF_TYPE;
        else {
            String inName =
                (((DNSName)inputName).getName()).toLowerCase(Locale.ENGLISH);
            String thisName = name.toLowerCase(Locale.ENGLISH);
            if (inName.equals(thisName))
                constraintType = NAME_MATCH;
            else if (thisName.endsWith(inName)) {
                int inNdx = thisName.lastIndexOf(inName);
                if (thisName.charAt(inNdx-1) == '.' )
                    constraintType = NAME_WIDENS;
                else
                    constraintType = NAME_SAME_TYPE;
            } else if (inName.endsWith(thisName)) {
                int ndx = inName.lastIndexOf(thisName);
                if (inName.charAt(ndx-1) == '.' )
                    constraintType = NAME_NARROWS;
                else
                    constraintType = NAME_SAME_TYPE;
            } else {
                constraintType = NAME_SAME_TYPE;
            }
        }
        return constraintType;
    }

    /**
     * Return subtree depth of this name for purposes of determining
     * NameConstraints minimum and maximum bounds and for calculating
     * path lengths in name subtrees.
     *
     * @return distance of name from root
     * @throws UnsupportedOperationException if not supported for this name type
     */
    public int subtreeDepth() throws UnsupportedOperationException {
        // subtree depth is always at least 1
        int sum = 1;

        // count dots
        for (int i = name.indexOf('.'); i >= 0; i = name.indexOf('.', i + 1)) {
            ++sum;
        }

        return sum;
    }

}
