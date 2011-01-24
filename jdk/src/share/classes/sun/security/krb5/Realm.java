/*
 * Copyright (c) 2000, 2009, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5;

import sun.security.krb5.internal.Krb5;
import sun.security.util.*;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Stack;
import java.util.EmptyStackException;
import sun.security.krb5.internal.util.KerberosString;

/**
 * Implements the ASN.1 Realm type.
 *
 * <xmp>
 * Realm ::= GeneralString
 * </xmp>
 */
public class Realm implements Cloneable {
    private String realm;
    private static boolean DEBUG = Krb5.DEBUG;

    private Realm() {
    }

    public Realm(String name) throws RealmException {
        realm = parseRealm(name);
    }

    public Object clone() {
        Realm new_realm = new Realm();
        if (realm != null) {
            new_realm.realm = new String(realm);
        }
        return new_realm;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Realm)) {
            return false;
        }

        Realm that = (Realm)obj;
        if (this.realm != null && that.realm != null ) {
            return this.realm.equals(that.realm);
        } else {
            return (this.realm == null && that.realm == null);
        }
    }

    public int hashCode() {
        int result = 17 ;

        if( realm != null ) {
            result = 37 * result + realm.hashCode();
        }

        return result;
    }

    /**
     * Constructs a Realm object.
     * @param encoding a Der-encoded data.
     * @exception Asn1Exception if an error occurs while decoding an ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     * @exception RealmException if an error occurs while parsing a Realm object.
     */
    public Realm(DerValue encoding)
        throws Asn1Exception, RealmException, IOException {
        if (encoding == null) {
            throw new IllegalArgumentException("encoding can not be null");
        }
        realm = new KerberosString(encoding).toString();
        if (realm == null || realm.length() == 0)
            throw new RealmException(Krb5.REALM_NULL);
        if (!isValidRealmString(realm))
            throw new RealmException(Krb5.REALM_ILLCHAR);
    }

    public String toString() {
        return realm;
    }

    public static String parseRealmAtSeparator(String name)
        throws RealmException {
        if (name == null) {
            throw new IllegalArgumentException
                ("null input name is not allowed");
        }
        String temp = new String(name);
        String result = null;
        int i = 0;
        while (i < temp.length()) {
            if (temp.charAt(i) == PrincipalName.NAME_REALM_SEPARATOR) {
                if (i == 0 || temp.charAt(i - 1) != '\\') {
                    if (i + 1 < temp.length())
                        result = temp.substring(i + 1, temp.length());
                    break;
                }
            }
            i++;
        }
        if (result != null) {
            if (result.length() == 0)
                throw new RealmException(Krb5.REALM_NULL);
            if (!isValidRealmString(result))
                throw new RealmException(Krb5.REALM_ILLCHAR);
        }
        return result;
    }

    public static String parseRealmComponent(String name) {
        if (name == null) {
            throw new IllegalArgumentException
                ("null input name is not allowed");
        }
        String temp = new String(name);
        String result = null;
        int i = 0;
        while (i < temp.length()) {
            if (temp.charAt(i) == PrincipalName.REALM_COMPONENT_SEPARATOR) {
                if (i == 0 || temp.charAt(i - 1) != '\\') {
                    if (i + 1 < temp.length())
                        result = temp.substring(i + 1, temp.length());
                    break;
                }
            }
            i++;
        }
        return result;
    }

    protected static String parseRealm(String name) throws RealmException {
        String result = parseRealmAtSeparator(name);
        if (result == null)
            result = name;
        if (result == null || result.length() == 0)
            throw new RealmException(Krb5.REALM_NULL);
        if (!isValidRealmString(result))
            throw new RealmException(Krb5.REALM_ILLCHAR);
        return result;
    }

    // This is protected because the definition of a realm
    // string is fixed
    protected static boolean isValidRealmString(String name) {
        if (name == null)
            return false;
        if (name.length() == 0)
            return false;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '/' ||
                name.charAt(i) == ':' ||
                name.charAt(i) == '\0') {
                return false;
            }
        }
        return true;
    }

    /**
     * Encodes a Realm object.
     * @return the byte array of encoded KrbCredInfo object.
     * @exception Asn1Exception if an error occurs while decoding an ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     *
     */
    public byte[] asn1Encode() throws Asn1Exception, IOException {
        DerOutputStream out = new DerOutputStream();
        out.putDerValue(new KerberosString(this.realm).toDerValue());
        return out.toByteArray();
    }


    /**
     * Parse (unmarshal) a realm from a DER input stream.  This form
     * parsing might be used when expanding a value which is part of
     * a constructed sequence and uses explicitly tagged type.
     *
     * @exception Asn1Exception on error.
     * @param data the Der input stream value, which contains one or more marshaled value.
     * @param explicitTag tag number.
     * @param optional indicate if this data field is optional
     * @return an instance of Realm.
     *
     */
    public static Realm parse(DerInputStream data, byte explicitTag, boolean optional) throws Asn1Exception, IOException, RealmException {
        if ((optional) && (((byte)data.peekByte() & (byte)0x1F) != explicitTag)) {
            return null;
        }
        DerValue der = data.getDerValue();
        if (explicitTag != (der.getTag() & (byte)0x1F))  {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        } else {
            DerValue subDer = der.getData().getDerValue();
            return new Realm(subDer);
        }
    }

    /*
     * First leg of realms parsing. Used by getRealmsList.
     */
    private static String[] doInitialParse(String cRealm, String sRealm)
        throws KrbException {
            if (cRealm == null || sRealm == null){
                throw new KrbException(Krb5.API_INVALID_ARG);
            }
            if (DEBUG) {
                System.out.println(">>> Realm doInitialParse: cRealm=["
                                   + cRealm + "], sRealm=[" +sRealm + "]");
            }
            if (cRealm.equals(sRealm)) {
                String[] retList = null;
                retList = new String[1];
                retList[0] = new String(cRealm);

                if (DEBUG) {
                    System.out.println(">>> Realm doInitialParse: "
                                       + retList[0]);
                }
                return retList;
            }
            return null;
        }

    /**
     * Returns an array of realms that may be traversed to obtain
     * a TGT from the initiating realm cRealm to the target realm
     * sRealm.
     * <br>
     * There may be an arbitrary number of intermediate realms
     * between cRealm and sRealm. The realms may be organized
     * organized hierarchically, or the paths between them may be
     * specified in the [capaths] stanza of the caller's
     * Kerberos configuration file. The configuration file is consulted
     * first. Then a hirarchical organization is assumed if no realms
     * are found in the configuration file.
     * <br>
     * The returned list, if not null, contains cRealm as the first
     * entry. sRealm is not included unless it is mistakenly listed
     * in the configuration file as an intermediary realm.
     *
     * @param cRealm the initiating realm
     * @param sRealm the target realm
     * @returns array of realms
     * @thows KrbException
     */
    public static String[] getRealmsList(String cRealm, String sRealm)
        throws KrbException {
            String[] retList = doInitialParse(cRealm, sRealm);
            if (retList != null && retList.length != 0) {
                return retList;
            }
            /*
             * Try [capaths].
             */
            retList = parseCapaths(cRealm, sRealm);
            if (retList != null && retList.length != 0) {
                return retList;
            }
            /*
             * Now assume the realms are organized hierarchically.
             */
            retList = parseHierarchy(cRealm, sRealm);
            return retList;
        }

    /**
     * Parses the [capaths] stanza of the configuration file
     * for a list of realms to traverse
     * to obtain credentials from the initiating realm cRealm to
     * the target realm sRealm.
     * @param cRealm the initiating realm
     * @param sRealm the target realm
     * @returns array of realms
     * @ throws KrbException
     */

    /*
     * parseCapaths works for a capaths organized such that
     * for a given client realm C there is a tag C that
     * contains subtags Ci ... Cn that completely define intermediate
     * realms from C to target T. For example:
     *
     * [capaths]
     *    TIVOLI.COM = {
     *        IBM.COM = IBM_LDAPCENTRAL.COM MOONLITE.ORG
     *        IBM_LDAPCENTRAL.COM = LDAPCENTRAL.NET
     *        LDAPCENTRAL.NET = .
     *    }
     *
     * The tag TIVOLI.COM contains subtags IBM.COM, IBM_LDAPCENTRAL.COM
     * and LDAPCENTRAL.NET that completely define the path from TIVOLI.COM
     * to IBM.COM (TIVOLI.COM->LADAPCENTRAL.NET->IBM_LDAPCENTRAL.COM->IBM
     * or TIVOLI.COM->MOONLITE.ORG->IBM.COM).
     *
     * A direct path is assumed for an intermediary whose entry is not
     * "closed" by a "." In the above example, TIVOLI.COM is assumed
     * to have a direct path to MOONLITE.ORG and MOONLITE.COM
     * in turn to IBM.COM.
     */

    private static String[] parseCapaths(String cRealm, String sRealm) throws KrbException {
        String[] retList = null;

        Config cfg = null;
        try {
            cfg = Config.getInstance();
        } catch (Exception exc) {
            if (DEBUG) {
                System.out.println ("Configuration information can not be " +
                                    "obtained " + exc.getMessage());
            }
            return null;
        }

        String intermediaries = cfg.getDefault(sRealm, cRealm);

        if (intermediaries == null) {
            if (DEBUG) {
                System.out.println(">>> Realm parseCapaths: no cfg entry");
            }
            return null;
        }

        String tempTarget = null, tempRealm = null;
        Stack<String> iStack = new Stack<>();

        /*
         * I don't expect any more than a handful of intermediaries.
         */
        Vector<String> tempList = new Vector<>(8, 8);

        /*
         * The initiator at first location.
         */
        tempList.add(cRealm);

        int count = 0; // For debug only
        if (DEBUG) {
            tempTarget = sRealm;
        }

        out: do {
            if (DEBUG) {
                count++;
                System.out.println(">>> Realm parseCapaths: loop " +
                                   count + ": target=" + tempTarget);
            }

            if (intermediaries != null &&
                !intermediaries.equals(PrincipalName.REALM_COMPONENT_SEPARATOR_STR))
            {
                if (DEBUG) {
                    System.out.println(">>> Realm parseCapaths: loop " +
                                       count + ": intermediaries=[" +
                                       intermediaries + "]");
                }

                /*
                 * We have one or more space-separated intermediary realms.
                 * Stack them. A null is always added between intermedies of
                 * different targets. When this null is popped, it means none
                 * of the intermedies for this target is useful (because of
                 * infinite loop), the target is then removed from the partial
                 * tempList, and the next possible intermediary is tried.
                 */
                iStack.push(null);
                String[] ints = intermediaries.split("\\s+");
                for (int i = ints.length-1; i>=0; i--)
                {
                    tempRealm = ints[i];
                    if (tempRealm.equals(PrincipalName.REALM_COMPONENT_SEPARATOR_STR)) {
                        break out;
                    }
                    if (!tempList.contains(tempRealm)) {
                        iStack.push(tempRealm);
                        if (DEBUG) {
                            System.out.println(">>> Realm parseCapaths: loop " +
                                               count +
                                               ": pushed realm on to stack: " +
                                               tempRealm);
                        }
                    } else if (DEBUG) {
                        System.out.println(">>> Realm parseCapaths: loop " +
                                           count +
                                           ": ignoring realm: [" +
                                           tempRealm + "]");
                    }
                }
            } else {
                if (DEBUG) {
                    System.out.println(">>> Realm parseCapaths: loop " +
                                       count +
                                       ": no intermediaries");
                }
                break;
            }

            /*
             * Get next intermediary realm from the stack
             */

            try {
                while ((tempTarget = iStack.pop()) == null) {
                    tempList.removeElementAt(tempList.size()-1);
                    if (DEBUG) {
                        System.out.println(">>> Realm parseCapaths: backtrack, remove tail");
                    }
                }
            } catch (EmptyStackException exc) {
                tempTarget = null;
            }

            if (tempTarget == null) {
                /*
                 * No more intermediaries. We're done.
                 */
                break;
            }

            tempList.add(tempTarget);

            if (DEBUG) {
                System.out.println(">>> Realm parseCapaths: loop " + count +
                                   ": added intermediary to list: " +
                                   tempTarget);
            }

            intermediaries = cfg.getDefault(tempTarget, cRealm);

        } while (true);

        retList = new String[tempList.size()];
        try {
            retList = tempList.toArray(retList);
        } catch (ArrayStoreException exc) {
            retList = null;
        }

        if (DEBUG && retList != null) {
            for (int i = 0; i < retList.length; i++) {
                System.out.println(">>> Realm parseCapaths [" + i +
                                   "]=" + retList[i]);
            }
        }

        return retList;
    }

    /**
     * Build a list of realm that can be traversed
     * to obtain credentials from the initiating realm cRealm
     * for a service in the target realm sRealm.
     * @param cRealm the initiating realm
     * @param sRealm the target realm
     * @returns array of realms
     * @throws KrbException
     */
    private static String[] parseHierarchy(String cRealm, String sRealm)
        throws KrbException
    {
        String[] retList = null;

        // Parse the components and determine common part, if any.

        String[] cComponents = null;
        String[] sComponents = null;

        StringTokenizer strTok =
        new StringTokenizer(cRealm,
                            PrincipalName.REALM_COMPONENT_SEPARATOR_STR);

        // Parse cRealm

        int cCount = strTok.countTokens();
        cComponents = new String[cCount];

        for (cCount = 0; strTok.hasMoreTokens(); cCount++) {
            cComponents[cCount] = strTok.nextToken();
        }

        if (DEBUG) {
            System.out.println(">>> Realm parseHierarchy: cRealm has " +
                               cCount + " components:");
            int j = 0;
            while (j < cCount) {
                System.out.println(">>> Realm parseHierarchy: " +
                                   "cComponents["+j+"]=" + cComponents[j++]);
            }
        }

        // Parse sRealm

        strTok = new StringTokenizer(sRealm,
                                     PrincipalName.REALM_COMPONENT_SEPARATOR_STR);

        int sCount = strTok.countTokens();
        sComponents = new String[sCount];

        for (sCount = 0; strTok.hasMoreTokens(); sCount++) {
            sComponents[sCount] = strTok.nextToken();
        }

        if (DEBUG) {
            System.out.println(">>> Realm parseHierarchy: sRealm has " +
                               sCount + " components:");
            int j = 0;
            while (j < sCount) {
                System.out.println(">>> Realm parseHierarchy: sComponents["+j+
                                   "]=" + sComponents[j++]);
            }
        }

        // Determine common components, if any.

        int commonComponents = 0;

        //while (sCount > 0 && cCount > 0 &&
        //          sComponents[--sCount].equals(cComponents[--cCount]))

        for (sCount--, cCount--; sCount >=0 && cCount >= 0 &&
                 sComponents[sCount].equals(cComponents[cCount]);
             sCount--, cCount--) {
            commonComponents++;
        }

        int cCommonStart = -1;
        int sCommonStart = -1;

        int links = 0;

        if (commonComponents > 0) {
            sCommonStart = sCount+1;
            cCommonStart = cCount+1;

            // components from common to ancestors
            links += sCommonStart;
            links += cCommonStart;
        } else {
            links++;
        }

        if (DEBUG) {
            if (commonComponents > 0) {
                System.out.println(">>> Realm parseHierarchy: " +
                                   commonComponents + " common component" +
                                   (commonComponents > 1 ? "s" : " "));

                System.out.println(">>> Realm parseHierarchy: common part "
                                   +
                                   "in cRealm (starts at index " +
                                   cCommonStart + ")");
                System.out.println(">>> Realm parseHierarchy: common part in sRealm (starts at index " +
                                   sCommonStart + ")");


                String commonPart = substring(cRealm, cCommonStart);
                System.out.println(">>> Realm parseHierarchy: common part in cRealm=" +
                                   commonPart);

                commonPart = substring(sRealm, sCommonStart);
                System.out.println(">>> Realm parseHierarchy: common part in sRealm=" +
                                   commonPart);

            } else
            System.out.println(">>> Realm parseHierarchy: no common part");
        }

        if (DEBUG) {
            System.out.println(">>> Realm parseHierarchy: total links=" + links);
        }

        retList = new String[links];

        retList[0] = new String(cRealm);

        if (DEBUG) {
            System.out.println(">>> Realm parseHierarchy A: retList[0]=" +
                               retList[0]);
        }

        // For an initiator realm A.B.C.D.COM,
        // build a list krbtgt/B.C.D.COM@A.B.C.D.COM up to the common part,
        // ie the issuer realm is the immediate descendant
        // of the target realm.

        String cTemp = null, sTemp = null;
        int i;
        for (i = 1, cCount = 0; i < links && cCount < cCommonStart; cCount++) {
            sTemp = substring(cRealm, cCount+1);
            //cTemp = substring(cRealm, cCount);
            retList[i++] = new String(sTemp);

            if (DEBUG) {
                System.out.println(">>> Realm parseHierarchy B: retList[" +
                                   (i-1) +"]="+retList[i-1]);
            }
        }


        for (sCount = sCommonStart; i < links && sCount - 1 > 0; sCount--) {
            sTemp = substring(sRealm, sCount-1);
            //cTemp = substring(sRealm, sCount);
            retList[i++] = new String(sTemp);
            if (DEBUG) {
                System.out.println(">>> Realm parseHierarchy D: retList[" +
                                   (i-1) +"]="+retList[i-1]);
            }
        }

        return retList;
    }

    private static String substring(String realm, int componentIndex)
    {
        int i = 0 , j = 0, len = realm.length();

        while(i < len && j != componentIndex) {
            if (realm.charAt(i++) != PrincipalName.REALM_COMPONENT_SEPARATOR)
                continue;
            j++;
        }

        return realm.substring(i);
    }

    static int getRandIndex(int arraySize) {
        return (int)(Math.random() * 16384.0) % arraySize;
    }

    static void printNames(String[] names) {
        if (names == null || names.length == 0)
            return;

        int len = names.length;
        int i = 0;
        System.out.println("List length = " + len);
        while (i < names.length) {
            System.out.println("["+ i +"]=" + names[i]);
            i++;
        }
    }

}
