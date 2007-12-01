/*
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
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5.internal.tools;

import sun.security.krb5.*;
import sun.security.krb5.internal.*;
import sun.security.krb5.internal.ktab.*;
import sun.security.krb5.KrbCryptoException;
import java.lang.RuntimeException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Arrays;
/**
 * This class can execute as a command-line tool to help the user manage
 * entires in the key table.
 * Available functions include list/add/update/delete service key(s).
 *
 * @author Yanni Zhang
 * @author Ram Marti
 */

public class Ktab {
    // KeyTabAdmin admin;
    KeyTab table;
    char action;
    String name;   // name and directory of key table
    String principal;
    char[] password = null;

    /**
     * The main program that can be invoked at command line.
     * <br>Usage: ktab <options>
     * <br>available options to Ktab:
     * <ul>
     * <li><b>-l</b>  list the keytab name and entries
     * <li><b>-a</b>  &lt;<i>principal name</i>&gt;
     * (&lt;<i>password</i>&gt;)  add an entry to the keytab.
     * The entry is added only to the keytab. No changes are made to the
     * Kerberos database.
     * <li><b>-d</b>  &lt;<i>principal name</i>&gt;
     * delete an entry from the keytab
     * The entry is deleted only from the keytab. No changes are made to the
     * Kerberos database.
     * <li><b>-k</b>  &lt;<i>keytab name</i> &gt;
     * specify keytab name and path with prefix FILE:
     * <li><b>-help</b> display instructions.
     */
    public static void main(String[] args) {
        Ktab ktab = new Ktab();
        if ((args.length == 1) && (args[0].equalsIgnoreCase("-help"))) {
            ktab.printHelp();
            System.exit(0);
        } else if ((args == null) || (args.length == 0)) {
            ktab.action = 'l';
        } else {
            ktab.processArgs(args);
        }
        try {
            if (ktab.name == null) {
                //  ktab.admin = new KeyTabAdmin();    // use the default keytab.
                ktab.table = KeyTab.getInstance();
                if (ktab.table == null) {
                    if (ktab.action == 'a') {
                        ktab.table = KeyTab.create();
                    } else {
                        System.out.println("No default key table exists.");
                        System.exit(-1);
                    }
                }
            } else {
                if ((ktab.action != 'a') &&
                    !(new File(ktab.name)).exists()) {
                    System.out.println("Key table " +
                                ktab.name + " does not exist.");
                    System.exit(-1);
                } else {
                    ktab.table = KeyTab.getInstance(ktab.name);
                }
                if (ktab.table == null) {
                    if (ktab.action == 'a') {
                        ktab.table = KeyTab.create(ktab.name);
                    } else {
                        System.out.println("The format of key table " +
                                ktab.name + " is incorrect.");
                        System.exit(-1);
                    }
                }
            }
        } catch (RealmException e) {
            System.err.println("Error loading key table.");
            System.exit(-1);
        } catch (IOException e) {
            System.err.println("Error loading key table.");
            System.exit(-1);
        }
        switch (ktab.action) {
        case 'l':
            ktab.listKt();
            break;
        case 'a':
            ktab.addEntry();
            break;
        case 'd':
            ktab.deleteEntry();
            break;
        default:
            ktab.printHelp();
            System.exit(-1);
        }
    }

    /**
     * Parses the command line arguments.
     */
    void processArgs(String[] args) {
        Character arg = null;
        for (int i = 0; i < args.length; i++) {
            if ((args[i].length() == 2) && (args[i].startsWith("-"))) {
                arg = new Character(args[i].charAt(1));
            } else {
                printHelp();
                System.exit(-1);
            }
            switch (arg.charValue()) {
            case 'l':
            case 'L':
                action = 'l';    // list keytab location, name and entries
                break;
            case 'a':
            case 'A':
                action = 'a'; // add a new entry to keytab.
                i++;
                if ((i < args.length) && (!args[i].startsWith("-"))) {
                    principal = args[i];
                } else {
                    System.out.println("Please specify the principal name"+
                                       " after -a option.");
                    printHelp();
                    System.exit(-1);
                }
                if ((i + 1 < args.length) &&
                    (!args[i + 1].startsWith("-"))) {
                    password = args[i + 1].toCharArray();
                    i++;
                } else {
                    password = null; // prompt user for password later.
                }
                break;
            case 'd':
            case 'D':
                action = 'd'; // delete an entry.
                i++;
                if ((i < args.length) && (!args[i].startsWith("-"))) {
                    principal = args[i];
                } else {
                    System.out.println("Please specify the principal" +
                                       "name of the entry you want to " +
                                       " delete after -d option.");
                    printHelp();
                    System.exit(-1);
                }
                break;
            case 'k':
            case 'K':
                i++;
                if ((i < args.length) && (!args[i].startsWith("-"))) {
                    if (args[i].length() >= 5 &&
                        args[i].substring(0, 5).equalsIgnoreCase("FILE:")) {
                        name = args[i].substring(5);
                    } else
                        name = args[i];
                } else {
                    System.out.println("Please specify the keytab "+
                                       "file name and location " +
                                       "after -k option");
                    printHelp();
                    System.exit(-1);
                }
                break;
            default:
                printHelp();
                System.exit(-1);
            }
        }
    }

    /**
     * Adds a service key to key table. If the specified key table does not
     * exist, the program will automatically generate
     * a new key table.
     */
    void addEntry() {
        PrincipalName pname = null;
        try {
            pname = new PrincipalName(principal);
            if (pname.getRealm() == null) {
                pname.setRealm(Config.getInstance().getDefaultRealm());
            }
        } catch (KrbException e) {
            System.err.println("Failed to add " + principal +
                               " to keytab.");
            e.printStackTrace();
            System.exit(-1);
        }
        if (password == null) {
            try {
                BufferedReader cis =
                    new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Password for " + pname.toString() + ":");
                System.out.flush();
                password = cis.readLine().toCharArray();
            } catch (IOException e) {
                System.err.println("Failed to read the password.");
                e.printStackTrace();
                System.exit(-1);
            }

        }
        try {
            // admin.addEntry(pname, password);
            table.addEntry(pname, password);
            Arrays.fill(password, '0');  // clear password
            // admin.save();
            table.save();
            System.out.println("Done!");
            System.out.println("Service key for " + principal +
                               " is saved in " + table.tabName());

        } catch (KrbException e) {
            System.err.println("Failed to add " + principal + " to keytab.");
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            System.err.println("Failed to save new entry.");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Lists key table name and entries in it.
     */
    void listKt() {
        int version;
        String principal;
        // System.out.println("Keytab name: " + admin.getKeyTabName());
        System.out.println("Keytab name: " + table.tabName());
        // KeyTabEntry[] entries = admin.getEntries();
        KeyTabEntry[] entries = table.getEntries();
        if ((entries != null) && (entries.length > 0)) {
            System.out.println("KVNO    Principal");
            for (int i = 0; i < entries.length; i++) {
                version = entries[i].getKey().getKeyVersionNumber().intValue();
                principal = entries[i].getService().toString();
                if (i == 0) {
                    StringBuffer separator = new StringBuffer();
                    for (int j = 0; j < 9 + principal.length(); j++) {
                        separator.append("-");
                    }
                    System.out.println(separator.toString());
                }
                System.out.println("  " + version + "     " + principal);
            }
        } else {
            System.out.println("0 entry.");
        }
    }

    /**
     * Deletes an entry from the key table.
     */
    void deleteEntry() {
        PrincipalName pname = null;
        try {
            pname = new PrincipalName(principal);
            if (pname.getRealm() == null) {
                pname.setRealm(Config.getInstance().getDefaultRealm());
            }
            String answer;
            BufferedReader cis =
                new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Are you sure you want to "+
                             " delete service key for " + pname.toString() +
                             " in " + table.tabName() + "?(Y/N) :");

            System.out.flush();
            answer = cis.readLine();
            if (answer.equalsIgnoreCase("Y") ||
                answer.equalsIgnoreCase("Yes"));
            else {
                // no error, the user did not want to delete the entry
                System.exit(0);
            }

        } catch (KrbException e) {
            System.err.println("Error occured while deleting the entry. "+
                               "Deletion failed.");
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            System.err.println("Error occured while deleting the entry. "+
                               " Deletion failed.");
            e.printStackTrace();
            System.exit(-1);
        }
        // admin.deleteEntry(pname);
        table.deleteEntry(pname);

        try {
            table.save();
        } catch (IOException e) {
            System.err.println("Error occurs while saving the keytab." +
                               "Deletion fails.");
            e.printStackTrace();
            System.exit(-1);
        }
        System.out.println("Done!");

    }

    /**
     * Prints out the help information.
     */
    void printHelp() {
        System.out.println("\nUsage: ktab " +
                           "<options>");
        System.out.println("available options to Ktab:");
        System.out.println("-l\t\t\t\tlist the keytab name and entries");
        System.out.println("-a <principal name> (<password>)add an entry " +
                           "to the keytab");
        System.out.println("-d <principal name>\t\tdelete an entry from "+
                           "the keytab");
        System.out.println("-k <keytab name>\t\tspecify keytab name and "+
                           " path with prefix FILE:");
    }
}
