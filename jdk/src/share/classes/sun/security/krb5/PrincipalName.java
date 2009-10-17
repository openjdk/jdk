/*
 * Portions Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5;

import sun.security.krb5.internal.*;
import sun.security.util.*;
import java.net.*;
import java.util.Vector;
import java.io.IOException;
import java.math.BigInteger;
import sun.security.krb5.internal.ccache.CCacheOutputStream;
import sun.security.krb5.internal.util.KerberosString;


/**
 * This class encapsulates a Kerberos principal.
 */
public class PrincipalName
    implements Cloneable {

    //name types

    /**
     * Name type not known
     */
    public static final int KRB_NT_UNKNOWN =   0;

    /**
     * Just the name of the principal as in DCE, or for users
     */
    public static final int KRB_NT_PRINCIPAL = 1;

    /**
     * Service and other unique instance (krbtgt)
     */
    public static final int KRB_NT_SRV_INST =  2;

    /**
     * Service with host name as instance (telnet, rcommands)
     */
    public static final int KRB_NT_SRV_HST =   3;

    /**
     * Service with host as remaining components
     */
    public static final int KRB_NT_SRV_XHST =  4;

    /**
     * Unique ID
     */
    public static final int KRB_NT_UID = 5;



    /**
     * TGS Name
     */
    public static final String TGS_DEFAULT_SRV_NAME = "krbtgt";
    public static final int TGS_DEFAULT_NT = KRB_NT_SRV_INST;

    public static final char NAME_COMPONENT_SEPARATOR = '/';
    public static final char NAME_REALM_SEPARATOR = '@';
    public static final char REALM_COMPONENT_SEPARATOR = '.';

    public static final String NAME_COMPONENT_SEPARATOR_STR = "/";
    public static final String NAME_REALM_SEPARATOR_STR = "@";
    public static final String REALM_COMPONENT_SEPARATOR_STR = ".";

    private int nameType;
    private String[] nameStrings;  // Principal names don't mutate often

    private Realm nameRealm;  // optional; a null realm means use default
    // Note: the nameRealm is not included in the default ASN.1 encoding

    // salt for principal
    private String salt = null;

    protected PrincipalName() {
    }

    public PrincipalName(String[] nameParts, int type)
        throws IllegalArgumentException, IOException {
        if (nameParts == null) {
            throw new IllegalArgumentException("Null input not allowed");
        }
        nameStrings = new String[nameParts.length];
        System.arraycopy(nameParts, 0, nameStrings, 0, nameParts.length);
        nameType = type;
        nameRealm = null;
    }

    public PrincipalName(String[] nameParts) throws IOException {
        this(nameParts, KRB_NT_UNKNOWN);
    }

    public Object clone() {
        PrincipalName pName = new PrincipalName();
        pName.nameType = nameType;
        if (nameStrings != null) {
            pName.nameStrings =
                new String[nameStrings.length];
                System.arraycopy(nameStrings,0,pName.nameStrings,0,
                                nameStrings.length);
        }
        if (nameRealm != null) {
            pName.nameRealm = (Realm)nameRealm.clone();
        }
        return pName;
    }

    /*
     * Added to workaround a bug where the equals method that takes a
     * PrincipalName is not being called but Object.equals(Object) is
     * being called.
     */
    public boolean equals(Object o) {
        if (o instanceof PrincipalName)
            return equals((PrincipalName)o);
        else
            return false;
    }

    public boolean equals(PrincipalName other) {


        if (!equalsWithoutRealm(other)) {
            return false;
        }

        if ((nameRealm != null && other.nameRealm == null) ||
            (nameRealm == null && other.nameRealm != null)) {
            return false;
        }

        if (nameRealm != null && other.nameRealm != null) {
            if (!nameRealm.equals(other.nameRealm)) {
                return false;
            }
        }

        return true;
    }

    boolean equalsWithoutRealm(PrincipalName other) {


        if (nameType != KRB_NT_UNKNOWN &&
            other.nameType != KRB_NT_UNKNOWN &&
            nameType != other.nameType)
            return false;

        if ((nameStrings != null && other.nameStrings == null) ||
            (nameStrings == null && other.nameStrings != null))
            return false;

        if (nameStrings != null && other.nameStrings != null) {
            if (nameStrings.length != other.nameStrings.length)
                return false;
            for (int i = 0; i < nameStrings.length; i++)
                if (!nameStrings[i].equals(other.nameStrings[i]))
                    return false;
        }

        return true;

    }

    /**
     * Returns the ASN.1 encoding of the
     * <xmp>
     * PrincipalName    ::= SEQUENCE {
     *          name-type       [0] Int32,
     *          name-string     [1] SEQUENCE OF KerberosString
     * }
     *
     * KerberosString   ::= GeneralString (IA5String)
     * </xmp>
     *
     * <p>
     * This definition reflects the Network Working Group RFC 4120
     * specification available at
     * <a href="http://www.ietf.org/rfc/rfc4120.txt">
     * http://www.ietf.org/rfc/rfc4120.txt</a>.
     *
     * @param encoding a Der-encoded data.
     * @exception Asn1Exception if an error occurs while decoding
     * an ASN1 encoded data.
     * @exception Asn1Exception if there is an ASN1 encoding error
     * @exception IOException if an I/O error occurs
     * @exception IllegalArgumentException if encoding is null
     * reading encoded data.
     *
     */
    public PrincipalName(DerValue encoding)
        throws Asn1Exception, IOException {
        nameRealm = null;
        DerValue der;
        if (encoding == null) {
            throw new IllegalArgumentException("Null input not allowed");
        }
        if (encoding.getTag() != DerValue.tag_Sequence) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
        der = encoding.getData().getDerValue();
        if ((der.getTag() & 0x1F) == 0x00) {
            BigInteger bint = der.getData().getBigInteger();
            nameType = bint.intValue();
        } else {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
        der = encoding.getData().getDerValue();
        if ((der.getTag() & 0x01F) == 0x01) {
            DerValue subDer = der.getData().getDerValue();
            if (subDer.getTag() != DerValue.tag_SequenceOf) {
                throw new Asn1Exception(Krb5.ASN1_BAD_ID);
            }
            Vector<String> v = new Vector<String> ();
            DerValue subSubDer;
            while(subDer.getData().available() > 0) {
                subSubDer = subDer.getData().getDerValue();
                v.addElement(new KerberosString(subSubDer).toString());
            }
            if (v.size() > 0) {
                nameStrings = new String[v.size()];
                v.copyInto(nameStrings);
            } else {
                nameStrings = new String[] {""};
            }
        } else  {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
    }

    /**
     * Parse (unmarshal) a <code>PrincipalName</code> from a DER
     * input stream.  This form
     * parsing might be used when expanding a value which is part of
     * a constructed sequence and uses explicitly tagged type.
     *
     * @exception Asn1Exception on error.
     * @param data the Der input stream value, which contains one or
     * more marshaled value.
     * @param explicitTag tag number.
     * @param optional indicate if this data field is optional
     * @return an instance of <code>PrincipalName</code>.
     *
     */
    public static PrincipalName parse(DerInputStream data,
                                      byte explicitTag, boolean
                                      optional)
        throws Asn1Exception, IOException {

        if ((optional) && (((byte)data.peekByte() & (byte)0x1F) !=
                           explicitTag))
            return null;
        DerValue der = data.getDerValue();
        if (explicitTag != (der.getTag() & (byte)0x1F))
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        else {
            DerValue subDer = der.getData().getDerValue();
            return new PrincipalName(subDer);
        }
    }


    // This is protected because the definition of a principal
    // string is fixed
    // XXX Error checkin consistent with MIT krb5_parse_name
    // Code repetition, realm parsed again by class Realm
    protected static String[] parseName(String name) {

        Vector<String> tempStrings = new Vector<String> ();
        String temp = name;
        int i = 0;
        int componentStart = 0;
        String component;

        while (i < temp.length()) {
            if (temp.charAt(i) == NAME_COMPONENT_SEPARATOR) {
                /*
                 * If this separator is escaped then don't treat it
                 * as a separator
                 */
                if (i > 0 && temp.charAt(i - 1) == '\\') {
                    temp = temp.substring(0, i - 1) +
                        temp.substring(i, temp.length());
                    continue;
                }
                else {
                    if (componentStart < i) {
                        component = temp.substring(componentStart, i);
                        tempStrings.addElement(component);
                    }
                    componentStart = i + 1;
                }
            } else
                if (temp.charAt(i) == NAME_REALM_SEPARATOR) {
                    /*
                     * If this separator is escaped then don't treat it
                     * as a separator
                     */
                    if (i > 0 && temp.charAt(i - 1) == '\\') {
                        temp = temp.substring(0, i - 1) +
                            temp.substring(i, temp.length());
                        continue;
                    } else {
                        if (componentStart < i) {
                            component = temp.substring(componentStart, i);
                            tempStrings.addElement(component);
                        }
                        componentStart = i + 1;
                        break;
                    }
                }
            i++;
        }

        if (i == temp.length())
        if (componentStart < i) {
            component = temp.substring(componentStart, i);
            tempStrings.addElement(component);
        }

        String[] result = new String[tempStrings.size()];
        tempStrings.copyInto(result);
        return result;
    }

    public PrincipalName(String name, int type)
        throws RealmException {
        if (name == null) {
            throw new IllegalArgumentException("Null name not allowed");
        }
        String[] nameParts = parseName(name);
        Realm tempRealm = null;
        String realmString = Realm.parseRealmAtSeparator(name);

        if (realmString == null) {
            try {
                Config config = Config.getInstance();
                realmString = config.getDefaultRealm();
            } catch (KrbException e) {
                RealmException re =
                    new RealmException(e.getMessage());
                re.initCause(e);
                throw re;
            }
        }

        if (realmString != null)
            tempRealm = new Realm(realmString);

        switch (type) {
        case KRB_NT_SRV_HST:
            if (nameParts.length >= 2) {
                String hostName = nameParts[1];
                try {
                    // RFC4120 does not recommend canonicalizing a hostname.
                    // However, for compatibility reason, we will try
                    // canonicalize it and see if the output looks better.

                    String canonicalized = (InetAddress.getByName(hostName)).
                            getCanonicalHostName();

                    // Looks if canonicalized is a longer format of hostName,
                    // we accept cases like
                    //     bunny -> bunny.rabbit.hole
                    if (canonicalized.toLowerCase()
                            .startsWith(hostName.toLowerCase()+".")) {
                        hostName = canonicalized;
                    }
                } catch (UnknownHostException e) {
                    // no canonicalization, use old
                }
                nameParts[1] = hostName.toLowerCase();
            }
            nameStrings = nameParts;
            nameType = type;
                // We will try to get realm name from the mapping in
                // the configuration. If it is not specified
                // we will use the default realm. This nametype does
                // not allow a realm to be specified. The name string must of
                // the form service@host and this is internally changed into
                // service/host by Kerberos

            String mapRealm =  mapHostToRealm(nameParts[1]);
            if (mapRealm != null) {
                nameRealm = new Realm(mapRealm);
            } else {
                nameRealm = tempRealm;
            }
            break;
        case KRB_NT_UNKNOWN:
        case KRB_NT_PRINCIPAL:
        case KRB_NT_SRV_INST:
        case KRB_NT_SRV_XHST:
        case KRB_NT_UID:
            nameStrings = nameParts;
            nameType = type;
            nameRealm = tempRealm;
            break;
        default:
            throw new IllegalArgumentException("Illegal name type");
        }
    }

    public PrincipalName(String name) throws RealmException {
        this(name, KRB_NT_UNKNOWN);
    }

    public PrincipalName(String name, String realm) throws RealmException {
        this(name, KRB_NT_UNKNOWN);
        nameRealm = new Realm(realm);
    }

    public String getRealmAsString() {
        return getRealmString();
    }

    public String getPrincipalNameAsString() {
        StringBuffer temp = new StringBuffer(nameStrings[0]);
        for (int i = 1; i < nameStrings.length; i++)
            temp.append(nameStrings[i]);
        return temp.toString();
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public String getName() {
        return toString();
    }

    public int getNameType() {
        return nameType;
    }

    public String[] getNameStrings() {
        return nameStrings;
    }

    public byte[][] toByteArray() {
        byte[][] result = new byte[nameStrings.length][];
        for (int i = 0; i < nameStrings.length; i++) {
            result[i] = new byte[nameStrings[i].length()];
            result[i] = nameStrings[i].getBytes();
        }
        return result;
    }

    public String getRealmString() {
        if (nameRealm != null)
            return nameRealm.toString();
        return null;
    }

    public Realm getRealm() {
        return nameRealm;
    }

    public void setRealm(Realm new_nameRealm) throws RealmException {
        nameRealm = new_nameRealm;
    }

    public void setRealm(String realmsString) throws RealmException {
        nameRealm = new Realm(realmsString);
    }

    public String getSalt() {
        if (salt == null) {
            StringBuffer salt = new StringBuffer();
            if (nameRealm != null) {
                salt.append(nameRealm.toString());
            }
            for (int i = 0; i < nameStrings.length; i++) {
                salt.append(nameStrings[i]);
            }
            return salt.toString();
        }
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String toString() {
        StringBuffer str = new StringBuffer();
        for (int i = 0; i < nameStrings.length; i++) {
            if (i > 0)
                str.append("/");
            str.append(nameStrings[i]);
        }
        if (nameRealm != null) {
            str.append("@");
            str.append(nameRealm.toString());
        }

        return str.toString();
    }

    public String getNameString() {
        StringBuffer str = new StringBuffer();
        for (int i = 0; i < nameStrings.length; i++) {
            if (i > 0)
                str.append("/");
            str.append(nameStrings[i]);
        }
        return str.toString();
    }

    /**
     * Encodes a <code>PrincipalName</code> object.
     * @return the byte array of the encoded PrncipalName object.
     * @exception Asn1Exception if an error occurs while decoding an ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     *
     */
    public byte[] asn1Encode() throws Asn1Exception, IOException {
        DerOutputStream bytes = new DerOutputStream();
        DerOutputStream temp = new DerOutputStream();
        BigInteger bint = BigInteger.valueOf(this.nameType);
        temp.putInteger(bint);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true, (byte)0x00), temp);
        temp = new DerOutputStream();
        DerValue der[] = new DerValue[nameStrings.length];
        for (int i = 0; i < nameStrings.length; i++) {
            der[i] = new KerberosString(nameStrings[i]).toDerValue();
        }
        temp.putSequence(der);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true, (byte)0x01), temp);
        temp = new DerOutputStream();
        temp.write(DerValue.tag_Sequence, bytes);
        return temp.toByteArray();
    }


    /**
     * Checks if two <code>PrincipalName</code> objects have identical values in their corresponding data fields.
     *
     * @param pname the other <code>PrincipalName</code> object.
     * @return true if two have identical values, otherwise, return false.
     */
    // It is used in <code>sun.security.krb5.internal.ccache</code> package.
    public boolean match(PrincipalName pname) {
        boolean matched = true;
        //name type is just a hint, no two names can be the same ignoring name type.
        // if (this.nameType != pname.nameType) {
        //      matched = false;
        // }
        if ((this.nameRealm != null) && (pname.nameRealm != null)) {
            if (!(this.nameRealm.toString().equalsIgnoreCase(pname.nameRealm.toString()))) {
                matched = false;
            }
        }
        if (this.nameStrings.length != pname.nameStrings.length) {
            matched = false;
        } else {
            for (int i = 0; i < this.nameStrings.length; i++) {
                if (!(this.nameStrings[i].equalsIgnoreCase(pname.nameStrings[i]))) {
                    matched = false;
                }
            }
        }
        return matched;
    }

    /**
     * Writes data field values of <code>PrincipalName</code> in FCC format to an output stream.
     *
     * @param cos a <code>CCacheOutputStream</code> for writing data.
     * @exception IOException if an I/O exception occurs.
     * @see sun.security.krb5.internal.ccache.CCacheOutputStream
     */
    public void writePrincipal(CCacheOutputStream cos) throws IOException {
        cos.write32(nameType);
        cos.write32(nameStrings.length);
        if (nameRealm != null) {
            byte[] realmBytes = null;
            realmBytes = nameRealm.toString().getBytes();
            cos.write32(realmBytes.length);
            cos.write(realmBytes, 0, realmBytes.length);
        }
        byte[] bytes = null;
        for (int i = 0; i < nameStrings.length; i++) {
            bytes = nameStrings[i].getBytes();
            cos.write32(bytes.length);
            cos.write(bytes, 0, bytes.length);
        }
    }

    /**
     * Creates a KRB_NT_SRV_INST name from the supplied
     * name components and realm.
     * @param primary the primary component of the name
     * @param instance the instance component of the name
     * @param realm the realm
     * @throws KrbException
     */
    protected PrincipalName(String primary, String instance, String realm,
                            int type)
        throws KrbException {

        if (type != KRB_NT_SRV_INST) {
            throw new KrbException(Krb5.KRB_ERR_GENERIC, "Bad name type");
        }

        String[] nParts = new String[2];
        nParts[0] = primary;
        nParts[1] = instance;

        this.nameStrings = nParts;
        this.nameRealm = new Realm(realm);
        this.nameType = type;
    }

    /**
     * Returns the instance component of a name.
     * In a multi-component name such as a KRB_NT_SRV_INST
     * name, the second component is returned.
     * Null is returned if there are not two or more
     * components in the name.
     * @returns instance component of a multi-component name.
     */
    public String getInstanceComponent()
    {
        if (nameStrings != null && nameStrings.length >= 2)
            {
                return new String(nameStrings[1]);
            }

        return null;
    }

    static String mapHostToRealm(String name) {
        String result = null;
        try {
            String subname = null;
            Config c = Config.getInstance();
            if ((result = c.getDefault(name, "domain_realm")) != null)
                return result;
            else {
                for (int i = 1; i < name.length(); i++) {
                    if ((name.charAt(i) == '.') && (i != name.length() - 1)) { //mapping could be .ibm.com = AUSTIN.IBM.COM
                        subname = name.substring(i);
                        result = c.getDefault(subname, "domain_realm");
                        if (result != null) {
                            break;
                        }
                        else {
                            subname = name.substring(i + 1);      //or mapping could be ibm.com = AUSTIN.IBM.COM
                            result = c.getDefault(subname, "domain_realm");
                            if (result != null) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (KrbException e) {
        }
        return result;
    }

}
