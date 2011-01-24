/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.krb5.internal.ktab;

import sun.security.krb5.*;
import sun.security.krb5.internal.*;
import sun.security.krb5.internal.crypto.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * This class represents key table. The key table functions deal with storing
 * and retrieving service keys for use in authentication exchanges.
 *
 * @author Yanni Zhang
 */
public class KeyTab implements KeyTabConstants {
    int kt_vno;
    private static KeyTab singleton = null;
    private static final boolean DEBUG = Krb5.DEBUG;
    private static String name;
    private Vector<KeyTabEntry> entries = new Vector<>();

    private KeyTab(String filename) throws IOException, RealmException {
        init(filename);
    }

    public static KeyTab getInstance(String s) {
        name = parse(s);
        if (name == null) {
            return getInstance();
        }
        return getInstance(new File(name));
    }

    /**
     * Gets the single instance of KeyTab class.
     * @param file the key tab file.
     * @return single instance of KeyTab;
     *  return null if error occurs while reading data out of the file.
     */
    public static KeyTab getInstance(File file) {
        try {
            if (!(file.exists())) {
                singleton = null;
            } else {
                String fname = file.getAbsolutePath();
                // Since this class deals with file I/O operations,
                // we want only one class instance existing.
                if (singleton != null) {
                    File kfile = new File(singleton.name);
                    String kname = kfile.getAbsolutePath();
                    if (kname.equalsIgnoreCase(fname)) {
                       if (DEBUG) {
                          System.out.println("KeyTab instance already exists");
                       }
                    }
                } else {
                    singleton = new KeyTab(fname);
                }
            }
        } catch (Exception e) {
            singleton = null;
            if (DEBUG) {
                System.out.println("Could not obtain an instance of KeyTab" +
                                   e.getMessage());
            }
        }
        return singleton;
    }

    /**
     * Gets the single instance of KeyTab class.
     * @return single instance of KeyTab; return null if default keytab file
     *  does not exist, or error occurs while reading data from the file.
     */
    public static KeyTab getInstance() {
        try {
            name = getDefaultKeyTab();
            if (name != null) {
                singleton = getInstance(new File(name));
            }
        } catch (Exception e) {
            singleton = null;
            if (DEBUG) {
                System.out.println("Could not obtain an instance of KeyTab" +
                                   e.getMessage());
            }
        }
        return singleton;
    }

    /**
     * The location of keytab file will be read from the configuration file
     * If it is not specified, consider user.home as the keytab file's
     * default location.
     */
    private static String getDefaultKeyTab() {
        if (name != null) {
            return name;
        } else {
            String kname = null;
            try {
                String keytab_names = Config.getInstance().getDefault
                    ("default_keytab_name", "libdefaults");
                if (keytab_names != null) {
                    StringTokenizer st = new StringTokenizer(keytab_names, " ");
                    while (st.hasMoreTokens()) {
                        kname = parse(st.nextToken());
                        if (kname != null) {
                            break;
                        }
                    }
                }
            } catch (KrbException e) {
                kname = null;
            }

            if (kname == null) {
                String user_home =
                        java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("user.home"));

                if (user_home == null) {
                    user_home =
                        java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("user.dir"));
                }

                if (user_home != null) {
                    kname = user_home + File.separator  + "krb5.keytab";
                }
            }
            return kname;
        }
    }

    private static String parse(String name) {
        String kname = null;
        if (name == null) {
            return null;
        }
        if ((name.length() >= 5) &&
            (name.substring(0, 5).equalsIgnoreCase("FILE:"))) {
            kname = name.substring(5);
        } else if ((name.length() >= 9) &&
                (name.substring(0, 9).equalsIgnoreCase("ANY:FILE:"))) {
            // this format found in MIT's krb5.ini.
            kname = name.substring(9);
        } else if ((name.length() >= 7) &&
                (name.substring(0, 7).equalsIgnoreCase("SRVTAB:"))) {
            // this format found in MIT's krb5.ini.
            kname = name.substring(7);
        } else
            kname = name;
        return kname;
    }

    private synchronized void init(String filename)
        throws IOException, RealmException {

        if (filename != null) {
            KeyTabInputStream kis =
                new KeyTabInputStream(new FileInputStream(filename));
            load(kis);
            kis.close();
            name = filename;
        }
    }

    private void load(KeyTabInputStream kis)
        throws IOException, RealmException {

        entries.clear();
        kt_vno = kis.readVersion();
        if (kt_vno == KRB5_KT_VNO_1) {
            kis.setNativeByteOrder();
        }
        int entryLength = 0;
        KeyTabEntry entry;
        while (kis.available() > 0) {
            entryLength = kis.readEntryLength();
            entry = kis.readEntry(entryLength, kt_vno);
            if (DEBUG) {
                System.out.println(">>> KeyTab: load() entry length: " +
                        entryLength + "; type: " +
                        (entry != null? entry.keyType : 0));
            }
            if (entry != null)
                entries.addElement(entry);
        }
    }

    /**
     * Reads all keys for a service from the keytab file that have
     * etypes that have been configured for use. If there are multiple
     * keys with same etype, the one with the highest kvno is returned.
     * @param service the PrincipalName of the requested service
     * @return an array containing all the service keys
     */
    public EncryptionKey[] readServiceKeys(PrincipalName service) {
        KeyTabEntry entry;
        EncryptionKey key;
        int size = entries.size();
        ArrayList<EncryptionKey> keys = new ArrayList<>(size);

        for (int i = size-1; i >= 0; i--) {
            entry = entries.elementAt(i);
            if (entry.service.match(service)) {
                if (EType.isSupported(entry.keyType)) {
                    key = new EncryptionKey(entry.keyblock,
                                        entry.keyType,
                                        new Integer(entry.keyVersion));
                    keys.add(key);
                    if (DEBUG) {
                        System.out.println("Added key: " + entry.keyType +
                            "version: " + entry.keyVersion);
                    }
                } else if (DEBUG) {
                    System.out.println("Found unsupported keytype (" +
                        entry.keyType + ") for " + service);
                }
            }
        }

        size = keys.size();
        if (size == 0)
            return null;
        EncryptionKey[] retVal = keys.toArray(new EncryptionKey[size]);

        // Sort keys according to default_tkt_enctypes
        if (DEBUG) {
            System.out.println("Ordering keys wrt default_tkt_enctypes list");
        }

        final int[] etypes = EType.getDefaults("default_tkt_enctypes");

        // Sort the keys, k1 is preferred than k2 if:
        // 1. k1's etype appears earlier in etypes than k2's
        // 2. If same, k1's KVNO is higher
        Arrays.sort(retVal, new Comparator<EncryptionKey>() {
            @Override
            public int compare(EncryptionKey o1, EncryptionKey o2) {
                if (etypes != null) {
                    int o1EType = o1.getEType();
                    int o2EType = o2.getEType();
                    if (o1EType != o2EType) {
                        for (int i=0; i<etypes.length; i++) {
                            if (etypes[i] == o1EType) {
                                return -1;
                            } else if (etypes[i] == o2EType) {
                                return 1;
                            }
                        }
                        // Neither o1EType nor o2EType in default_tkt_enctypes,
                        // therefore won't be used in AS-REQ. We do not care
                        // about their order, use kvno is OK.
                    }
                }
                return o2.getKeyVersionNumber().intValue()
                        - o1.getKeyVersionNumber().intValue();
            }
        });

        return retVal;
    }



    /**
     * Searches for the service entry in the keytab file.
     * The etype of the key must be one that has been configured
     * to be used.
     * @param service the PrincipalName of the requested service.
     * @return true if the entry is found, otherwise, return false.
     */
    public boolean findServiceEntry(PrincipalName service) {
        KeyTabEntry entry;
        for (int i = 0; i < entries.size(); i++) {
            entry = entries.elementAt(i);
            if (entry.service.match(service)) {
                if (EType.isSupported(entry.keyType)) {
                    return true;
                } else if (DEBUG) {
                    System.out.println("Found unsupported keytype (" +
                        entry.keyType + ") for " + service);
                }
            }
        }
        return false;
    }

    public static String tabName() {
        return name;
    }

    /**
     * Adds a new entry in the key table.
     * @param service the service which will have a new entry in the key table.
     * @param psswd the password which generates the key.
     * @param kvno the kvno to use, -1 means automatic increasing
     * @param append false if entries with old kvno would be removed.
     * Note: if kvno is not -1, entries with the same kvno are always removed
     */
    public void addEntry(PrincipalName service, char[] psswd,
            int kvno, boolean append) throws KrbException {

        EncryptionKey[] encKeys = EncryptionKey.acquireSecretKeys(
            psswd, service.getSalt());

        // There should be only one maximum KVNO value for all etypes, so that
        // all added keys can have the same KVNO.

        int maxKvno = 0;    // only useful when kvno == -1
        for (int i = entries.size()-1; i >= 0; i--) {
            KeyTabEntry e = entries.get(i);
            if (e.service.match(service)) {
                if (e.keyVersion > maxKvno) {
                    maxKvno = e.keyVersion;
                }
                if (!append || e.keyVersion == kvno) {
                    entries.removeElementAt(i);
                }
            }
        }
        if (kvno == -1) {
            kvno = maxKvno + 1;
        }

        for (int i = 0; encKeys != null && i < encKeys.length; i++) {
            int keyType = encKeys[i].getEType();
            byte[] keyValue = encKeys[i].getBytes();

            KeyTabEntry newEntry = new KeyTabEntry(service,
                            service.getRealm(),
                            new KerberosTime(System.currentTimeMillis()),
                                               kvno, keyType, keyValue);
            entries.addElement(newEntry);
        }
    }

    /**
     * Gets the list of service entries in key table.
     * @return array of <code>KeyTabEntry</code>.
     */
    public KeyTabEntry[] getEntries() {
        KeyTabEntry[] kentries = new KeyTabEntry[entries.size()];
        for (int i = 0; i < kentries.length; i++) {
            kentries[i] = entries.elementAt(i);
        }
        return kentries;
    }

    /**
     * Creates a new default key table.
     */
    public synchronized static KeyTab create()
        throws IOException, RealmException {
        String dname = getDefaultKeyTab();
        return create(dname);
    }

    /**
     * Creates a new default key table.
     */
    public synchronized static KeyTab create(String name)
        throws IOException, RealmException {

        KeyTabOutputStream kos =
                new KeyTabOutputStream(new FileOutputStream(name));
        kos.writeVersion(KRB5_KT_VNO);
        kos.close();
        singleton = new KeyTab(name);
        return singleton;
    }

    /**
     * Saves the file at the directory.
     */
    public synchronized void save() throws IOException {
        KeyTabOutputStream kos =
                new KeyTabOutputStream(new FileOutputStream(name));
        kos.writeVersion(kt_vno);
        for (int i = 0; i < entries.size(); i++) {
            kos.writeEntry(entries.elementAt(i));
        }
        kos.close();
    }

    /**
     * Removes entries from the key table.
     * @param service the service <code>PrincipalName</code>.
     * @param etype the etype to match, remove all if -1
     * @param kvno what kvno to remove, -1 for all, -2 for old
     * @return the number of entries deleted
     */
    public int deleteEntries(PrincipalName service, int etype, int kvno) {
        int count = 0;

        // Remember the highest KVNO for each etype. Used for kvno == -2
        Map<Integer,Integer> highest = new HashMap<>();

        for (int i = entries.size()-1; i >= 0; i--) {
            KeyTabEntry e = entries.get(i);
            if (service.match(e.getService())) {
                if (etype == -1 || e.keyType == etype) {
                    if (kvno == -2) {
                        // Two rounds for kvno == -2. In the first round (here),
                        // only find out highest KVNO for each etype
                        if (highest.containsKey(e.keyType)) {
                            int n = highest.get(e.keyType);
                            if (e.keyVersion > n) {
                                highest.put(e.keyType, e.keyVersion);
                            }
                        } else {
                            highest.put(e.keyType, e.keyVersion);
                        }
                    } else if (kvno == -1 || e.keyVersion == kvno) {
                        entries.removeElementAt(i);
                        count++;
                    }
                }
            }
        }

        // Second round for kvno == -2, remove old entries
        if (kvno == -2) {
            for (int i = entries.size()-1; i >= 0; i--) {
                KeyTabEntry e = entries.get(i);
                if (service.match(e.getService())) {
                    if (etype == -1 || e.keyType == etype) {
                        int n = highest.get(e.keyType);
                        if (e.keyVersion != n) {
                            entries.removeElementAt(i);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Creates key table file version.
     * @param file the key table file.
     * @exception IOException.
     */
    public synchronized void createVersion(File file) throws IOException {
        KeyTabOutputStream kos =
                new KeyTabOutputStream(new FileOutputStream(file));
        kos.write16(KRB5_KT_VNO);
        kos.close();
    }

    public static void refresh() {
        if (singleton != null) {
            if (DEBUG) {
                System.out.println("Refreshing Keytab");
            }
            singleton = null;
        }
    }
}
