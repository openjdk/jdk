/*
 * Copyright 1997-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.tools.policytool;

import java.io.*;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Vector;
import java.util.Enumeration;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.reflect.*;
import java.text.Collator;
import java.text.MessageFormat;
import sun.security.util.PropertyExpander;
import sun.security.util.PropertyExpander.ExpandException;
import java.awt.*;
import java.awt.event.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.*;
import sun.security.provider.*;
import sun.security.util.PolicyUtil;
import javax.security.auth.x500.X500Principal;

/**
 * PolicyTool may be used by users and administrators to configure the
 * overall java security policy (currently stored in the policy file).
 * Using PolicyTool administators may add and remove policies from
 * the policy file. <p>
 *
 * @see java.security.Policy
 * @since   1.2
 */

public class PolicyTool {

    // for i18n
    static final java.util.ResourceBundle rb =
        java.util.ResourceBundle.getBundle("sun.security.util.Resources");
    static final Collator collator = Collator.getInstance();
    static {
        // this is for case insensitive string comparisons
        collator.setStrength(Collator.PRIMARY);
    };

    // anyone can add warnings
    Vector<String> warnings;
    boolean newWarning = false;

    // set to true if policy modified.
    // this way upon exit we know if to ask the user to save changes
    boolean modified = false;

    private static final boolean testing = false;
    private static final Class[] TWOPARAMS = { String.class, String.class };
    private static final Class[] ONEPARAMS = { String.class };
    private static final Class[] NOPARAMS  = {};
    /*
     * All of the policy entries are read in from the
     * policy file and stored here.  Updates to the policy entries
     * using addEntry() and removeEntry() are made here.  To ultimately save
     * the policy entries back to the policy file, the SavePolicy button
     * must be clicked.
     **/
    private static String policyFileName = null;
    private Vector<PolicyEntry> policyEntries = null;
    private PolicyParser parser = null;

    /* The public key alias information is stored here.  */
    private KeyStore keyStore = null;
    private String keyStoreName = " ";
    private String keyStoreType = " ";
    private String keyStoreProvider = " ";
    private String keyStorePwdURL = " ";

    /* standard PKCS11 KeyStore type */
    private static final String P11KEYSTORE = "PKCS11";

    /* reserved word for PKCS11 KeyStores */
    private static final String NONE = "NONE";

    /**
     * default constructor
     */
    private PolicyTool() {
        policyEntries = new Vector<PolicyEntry>();
        parser = new PolicyParser();
        warnings = new Vector<String>();
    }

    /**
     * get the PolicyFileName
     */
    String getPolicyFileName() {
        return policyFileName;
    }

    /**
     * set the PolicyFileName
     */
    void setPolicyFileName(String policyFileName) {
        this.policyFileName = policyFileName;
    }

   /**
    * clear keyStore info
    */
    void clearKeyStoreInfo() {
        this.keyStoreName = null;
        this.keyStoreType = null;
        this.keyStoreProvider = null;
        this.keyStorePwdURL = null;

        this.keyStore = null;
    }

    /**
     * get the keyStore URL name
     */
    String getKeyStoreName() {
        return keyStoreName;
    }

    /**
     * get the keyStore Type
     */
    String getKeyStoreType() {
        return keyStoreType;
    }

    /**
     * get the keyStore Provider
     */
    String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    /**
     * get the keyStore password URL
     */
    String getKeyStorePwdURL() {
        return keyStorePwdURL;
    }

    /**
     * Open and read a policy file
     */
    void openPolicy(String filename) throws FileNotFoundException,
                                        PolicyParser.ParsingException,
                                        KeyStoreException,
                                        CertificateException,
                                        InstantiationException,
                                        MalformedURLException,
                                        IOException,
                                        NoSuchAlgorithmException,
                                        IllegalAccessException,
                                        NoSuchMethodException,
                                        UnrecoverableKeyException,
                                        NoSuchProviderException,
                                        ClassNotFoundException,
                                        PropertyExpander.ExpandException,
                                        InvocationTargetException {

        newWarning = false;

        // start fresh - blow away the current state
        policyEntries = new Vector<PolicyEntry>();
        parser = new PolicyParser();
        warnings = new Vector<String>();
        setPolicyFileName(null);
        clearKeyStoreInfo();

        // see if user is opening a NEW policy file
        if (filename == null) {
            modified = false;
            return;
        }

        // Read in the policy entries from the file and
        // populate the parser vector table.  The parser vector
        // table only holds the entries as strings, so it only
        // guarantees that the policies are syntactically
        // correct.
        setPolicyFileName(filename);
        parser.read(new FileReader(filename));

        // open the keystore
        openKeyStore(parser.getKeyStoreUrl(), parser.getKeyStoreType(),
                parser.getKeyStoreProvider(), parser.getStorePassURL());

        // Update the local vector with the same policy entries.
        // This guarantees that the policy entries are not only
        // syntactically correct, but semantically valid as well.
        Enumeration<PolicyParser.GrantEntry> enum_ = parser.grantElements();
        while (enum_.hasMoreElements()) {
            PolicyParser.GrantEntry ge = enum_.nextElement();

            // see if all the signers have public keys
            if (ge.signedBy != null) {

                String signers[] = parseSigners(ge.signedBy);
                for (int i = 0; i < signers.length; i++) {
                    PublicKey pubKey = getPublicKeyAlias(signers[i]);
                    if (pubKey == null) {
                        newWarning = true;
                        MessageFormat form = new MessageFormat(rb.getString
                            ("Warning: A public key for alias " +
                            "'signers[i]' does not exist.  " +
                            "Make sure a KeyStore is properly configured."));
                        Object[] source = {signers[i]};
                        warnings.addElement(form.format(source));
                    }
                }
            }

            // check to see if the Principals are valid
            ListIterator<PolicyParser.PrincipalEntry> prinList =
                                                ge.principals.listIterator(0);
            while (prinList.hasNext()) {
                PolicyParser.PrincipalEntry pe = prinList.next();
                try {
                    verifyPrincipal(pe.getPrincipalClass(),
                                pe.getPrincipalName());
                } catch (ClassNotFoundException fnfe) {
                    newWarning = true;
                    MessageFormat form = new MessageFormat(rb.getString
                                ("Warning: Class not found: class"));
                    Object[] source = {pe.getPrincipalClass()};
                    warnings.addElement(form.format(source));
                }
            }

            // check to see if the Permissions are valid
            Enumeration<PolicyParser.PermissionEntry> perms =
                                                ge.permissionElements();
            while (perms.hasMoreElements()) {
                PolicyParser.PermissionEntry pe = perms.nextElement();
                try {
                    verifyPermission(pe.permission, pe.name, pe.action);
                } catch (ClassNotFoundException fnfe) {
                    newWarning = true;
                    MessageFormat form = new MessageFormat(rb.getString
                                ("Warning: Class not found: class"));
                    Object[] source = {pe.permission};
                    warnings.addElement(form.format(source));
                } catch (InvocationTargetException ite) {
                    newWarning = true;
                    MessageFormat form = new MessageFormat(rb.getString
                        ("Warning: Invalid argument(s) for constructor: arg"));
                    Object[] source = {pe.permission};
                    warnings.addElement(form.format(source));
                }

                // see if all the permission signers have public keys
                if (pe.signedBy != null) {

                    String signers[] = parseSigners(pe.signedBy);

                    for (int i = 0; i < signers.length; i++) {
                        PublicKey pubKey = getPublicKeyAlias(signers[i]);
                        if (pubKey == null) {
                            newWarning = true;
                            MessageFormat form = new MessageFormat(rb.getString
                                ("Warning: A public key for alias " +
                                "'signers[i]' does not exist.  " +
                               "Make sure a KeyStore is properly configured."));
                            Object[] source = {signers[i]};
                            warnings.addElement(form.format(source));
                        }
                    }
                }
            }
            PolicyEntry pEntry = new PolicyEntry(this, ge);
            policyEntries.addElement(pEntry);
        }

        // just read in the policy -- nothing has been modified yet
        modified = false;
    }


    /**
     * Save a policy to a file
     */
    void savePolicy(String filename)
    throws FileNotFoundException, IOException {
        // save the policy entries to a file
        parser.setKeyStoreUrl(keyStoreName);
        parser.setKeyStoreType(keyStoreType);
        parser.setKeyStoreProvider(keyStoreProvider);
        parser.setStorePassURL(keyStorePwdURL);
        parser.write(new FileWriter(filename));
        modified = false;
    }

    /**
     * Open the KeyStore
     */
    void openKeyStore(String name,
                String type,
                String provider,
                String pwdURL) throws   KeyStoreException,
                                        NoSuchAlgorithmException,
                                        UnrecoverableKeyException,
                                        IOException,
                                        CertificateException,
                                        NoSuchProviderException,
                                        ExpandException {

        if (name == null && type == null &&
            provider == null && pwdURL == null) {

            // policy did not specify a keystore during open
            // or use wants to reset keystore values

            this.keyStoreName = null;
            this.keyStoreType = null;
            this.keyStoreProvider = null;
            this.keyStorePwdURL = null;

            // caller will set (tool.modified = true) if appropriate

            return;
        }

        URL policyURL = null;
        if (policyFileName != null) {
            File pfile = new File(policyFileName);
            policyURL = new URL("file:" + pfile.getCanonicalPath());
        }

        // although PolicyUtil.getKeyStore may properly handle
        // defaults and property expansion, we do it here so that
        // if the call is successful, we can set the proper values
        // (PolicyUtil.getKeyStore does not return expanded values)

        if (name != null && name.length() > 0) {
            name = PropertyExpander.expand(name).replace
                                        (File.separatorChar, '/');
        }
        if (type == null || type.length() == 0) {
            type = KeyStore.getDefaultType();
        }
        if (pwdURL != null && pwdURL.length() > 0) {
            pwdURL = PropertyExpander.expand(pwdURL).replace
                                        (File.separatorChar, '/');
        }

        try {
            this.keyStore = PolicyUtil.getKeyStore(policyURL,
                                                name,
                                                type,
                                                provider,
                                                pwdURL,
                                                null);
        } catch (IOException ioe) {

            // copied from sun.security.pkcs11.SunPKCS11
            String MSG = "no password provided, and no callback handler " +
                        "available for retrieving password";

            Throwable cause = ioe.getCause();
            if (cause != null &&
                cause instanceof javax.security.auth.login.LoginException &&
                MSG.equals(cause.getMessage())) {

                // throw a more friendly exception message
                throw new IOException(MSG);
            } else {
                throw ioe;
            }
        }

        this.keyStoreName = name;
        this.keyStoreType = type;
        this.keyStoreProvider = provider;
        this.keyStorePwdURL = pwdURL;

        // caller will set (tool.modified = true)
    }

    /**
     * Add a Grant entry to the overall policy at the specified index.
     * A policy entry consists of a CodeSource.
     */
    boolean addEntry(PolicyEntry pe, int index) {

        if (index < 0) {
            // new entry -- just add it to the end
            policyEntries.addElement(pe);
            parser.add(pe.getGrantEntry());
        } else {
            // existing entry -- replace old one
            PolicyEntry origPe = policyEntries.elementAt(index);
            parser.replace(origPe.getGrantEntry(), pe.getGrantEntry());
            policyEntries.setElementAt(pe, index);
        }
        return true;
    }

    /**
     * Add a Principal entry to an existing PolicyEntry at the specified index.
     * A Principal entry consists of a class, and name.
     *
     * If the principal already exists, it is not added again.
     */
    boolean addPrinEntry(PolicyEntry pe,
                        PolicyParser.PrincipalEntry newPrin,
                        int index) {

        // first add the principal to the Policy Parser entry
        PolicyParser.GrantEntry grantEntry = pe.getGrantEntry();
        if (grantEntry.contains(newPrin) == true)
            return false;

        LinkedList<PolicyParser.PrincipalEntry> prinList =
                                                grantEntry.principals;
        if (index != -1)
            prinList.set(index, newPrin);
        else
            prinList.add(newPrin);

        modified = true;
        return true;
    }

    /**
     * Add a Permission entry to an existing PolicyEntry at the specified index.
     * A Permission entry consists of a permission, name, and actions.
     *
     * If the permission already exists, it is not added again.
     */
    boolean addPermEntry(PolicyEntry pe,
                        PolicyParser.PermissionEntry newPerm,
                        int index) {

        // first add the permission to the Policy Parser Vector
        PolicyParser.GrantEntry grantEntry = pe.getGrantEntry();
        if (grantEntry.contains(newPerm) == true)
            return false;

        Vector<PolicyParser.PermissionEntry> permList =
                                                grantEntry.permissionEntries;
        if (index != -1)
            permList.setElementAt(newPerm, index);
        else
            permList.addElement(newPerm);

        modified = true;
        return true;
    }

    /**
     * Remove a Permission entry from an existing PolicyEntry.
     */
    boolean removePermEntry(PolicyEntry pe,
                        PolicyParser.PermissionEntry perm) {

        // remove the Permission from the GrantEntry
        PolicyParser.GrantEntry ppge = pe.getGrantEntry();
        modified = ppge.remove(perm);
        return modified;
    }

    /**
     * remove an entry from the overall policy
     */
    boolean removeEntry(PolicyEntry pe) {

        parser.remove(pe.getGrantEntry());
        modified = true;
        return (policyEntries.removeElement(pe));
    }

    /**
     * retrieve all Policy Entries
     */
    PolicyEntry[] getEntry() {

        if (policyEntries.size() > 0) {
            PolicyEntry entries[] = new PolicyEntry[policyEntries.size()];
            for (int i = 0; i < policyEntries.size(); i++)
                entries[i] = policyEntries.elementAt(i);
            return entries;
        }
        return null;
    }

    /**
     * Retrieve the public key mapped to a particular name.
     * If the key has expired, a KeyException is thrown.
     */
    PublicKey getPublicKeyAlias(String name) throws KeyStoreException {
        if (keyStore == null) {
            return null;
        }

        Certificate cert = keyStore.getCertificate(name);
        if (cert == null) {
            return null;
        }
        PublicKey pubKey = cert.getPublicKey();
        return pubKey;
    }

    /**
     * Retrieve all the alias names stored in the certificate database
     */
    String[] getPublicKeyAlias() throws KeyStoreException {

        int numAliases = 0;
        String aliases[] = null;

        if (keyStore == null) {
            return null;
        }
        Enumeration<String> enum_ = keyStore.aliases();

        // first count the number of elements
        while (enum_.hasMoreElements()) {
            enum_.nextElement();
            numAliases++;
        }

        if (numAliases > 0) {
            // now copy them into an array
            aliases = new String[numAliases];
            numAliases = 0;
            enum_ = keyStore.aliases();
            while (enum_.hasMoreElements()) {
                aliases[numAliases] = new String(enum_.nextElement());
                numAliases++;
            }
        }
        return aliases;
    }

    /**
     * This method parses a single string of signers separated by commas
     * ("jordan, duke, pippen") into an array of individual strings.
     */
    String[] parseSigners(String signedBy) {

        String signers[] = null;
        int numSigners = 1;
        int signedByIndex = 0;
        int commaIndex = 0;
        int signerNum = 0;

        // first pass thru "signedBy" counts the number of signers
        while (commaIndex >= 0) {
            commaIndex = signedBy.indexOf(',', signedByIndex);
            if (commaIndex >= 0) {
                numSigners++;
                signedByIndex = commaIndex + 1;
            }
        }
        signers = new String[numSigners];

        // second pass thru "signedBy" transfers signers to array
        commaIndex = 0;
        signedByIndex = 0;
        while (commaIndex >= 0) {
            if ((commaIndex = signedBy.indexOf(',', signedByIndex)) >= 0) {
                // transfer signer and ignore trailing part of the string
                signers[signerNum] =
                        signedBy.substring(signedByIndex, commaIndex).trim();
                signerNum++;
                signedByIndex = commaIndex + 1;
            } else {
                // we are at the end of the string -- transfer signer
                signers[signerNum] = signedBy.substring(signedByIndex).trim();
            }
        }
        return signers;
    }

    /**
     * Check to see if the Principal contents are OK
     */
    void verifyPrincipal(String type, String name)
        throws ClassNotFoundException,
               InstantiationException
    {
        if (type.equals(PolicyParser.PrincipalEntry.WILDCARD_CLASS) ||
            type.equals(PolicyParser.REPLACE_NAME)) {
            return;
        };
        Class<?> PRIN = Class.forName("java.security.Principal");
        Class<?> pc = Class.forName(type, true,
                Thread.currentThread().getContextClassLoader());
        if (!PRIN.isAssignableFrom(pc)) {
            MessageFormat form = new MessageFormat(rb.getString
                        ("Illegal Principal Type: type"));
            Object[] source = {type};
            throw new InstantiationException(form.format(source));
        }

        if (ToolDialog.X500_PRIN_CLASS.equals(pc.getName())) {
            // PolicyParser checks validity of X500Principal name
            // - PolicyTool needs to as well so that it doesn't store
            //   an invalid name that can't be read in later
            //
            // this can throw an IllegalArgumentException
            X500Principal newP = new X500Principal(name);
        }
    }

    /**
     * Check to see if the Permission contents are OK
     */
    void verifyPermission(String type,
                                    String name,
                                    String actions)
        throws ClassNotFoundException,
               InstantiationException,
               IllegalAccessException,
               NoSuchMethodException,
               InvocationTargetException
    {

        //XXX we might want to keep a hash of created factories...
        Class<?> pc = Class.forName(type, true,
                Thread.currentThread().getContextClassLoader());
        Constructor<?> c = null;
        Vector<String> objects = new Vector<String>(2);
        if (name != null) objects.add(name);
        if (actions != null) objects.add(actions);
        switch (objects.size()) {
        case 0:
            try {
                c = pc.getConstructor(NOPARAMS);
                break;
            } catch (NoSuchMethodException ex) {
                // proceed to the one-param constructor
                objects.add(null);
            }
        case 1:
            try {
                c = pc.getConstructor(ONEPARAMS);
                break;
            } catch (NoSuchMethodException ex) {
                // proceed to the two-param constructor
                objects.add(null);
            }
        case 2:
            c = pc.getConstructor(TWOPARAMS);
            break;
        }
        Object parameters[] = objects.toArray();
        Permission p = (Permission)c.newInstance(parameters);
    }

    /*
     * Parse command line arguments.
     */
    static void parseArgs(String args[]) {
        /* parse flags */
        int n = 0;

        for (n=0; (n < args.length) && args[n].startsWith("-"); n++) {

            String flags = args[n];

            if (collator.compare(flags, "-file") == 0) {
                if (++n == args.length) usage();
                policyFileName = args[n];
            } else {
                MessageFormat form = new MessageFormat(rb.getString
                                ("Illegal option: option"));
                Object[] source = { flags };
                System.err.println(form.format(source));
                usage();
            }
        }
    }

    static void usage() {
        System.out.println(rb.getString("Usage: policytool [options]"));
        System.out.println();
        System.out.println(rb.getString
                ("  [-file <file>]    policy file location"));
        System.out.println();

        System.exit(1);
    }

    /**
     * run the PolicyTool
     */
    public static void main(String args[]) {
        parseArgs(args);
        ToolWindow tw = new ToolWindow(new PolicyTool());
        tw.displayToolWindow(args);
    }

    // split instr to words according to capitalization,
    // like, AWTControl -> A W T Control
    // this method is for easy pronounciation
    static String splitToWords(String instr) {
        return instr.replaceAll("([A-Z])", " $1");
    }

}

/**
 * Each entry in the policy configuration file is represented by a
 * PolicyEntry object.
 *
 * A PolicyEntry is a (CodeSource,Permission) pair.  The
 * CodeSource contains the (URL, PublicKey) that together identify
 * where the Java bytecodes come from and who (if anyone) signed
 * them.  The URL could refer to localhost.  The URL could also be
 * null, meaning that this policy entry is given to all comers, as
 * long as they match the signer field.  The signer could be null,
 * meaning the code is not signed.
 *
 * The Permission contains the (Type, Name, Action) triplet.
 *
 */
class PolicyEntry {

    private CodeSource codesource;
    private PolicyTool tool;
    private PolicyParser.GrantEntry grantEntry;
    private boolean testing = false;

    /**
     * Create a PolicyEntry object from the information read in
     * from a policy file.
     */
    PolicyEntry(PolicyTool tool, PolicyParser.GrantEntry ge)
    throws MalformedURLException, NoSuchMethodException,
    ClassNotFoundException, InstantiationException, IllegalAccessException,
    InvocationTargetException, CertificateException,
    IOException, NoSuchAlgorithmException, UnrecoverableKeyException {

        this.tool = tool;

        URL location = null;

        // construct the CodeSource
        if (ge.codeBase != null)
            location = new URL(ge.codeBase);
        this.codesource = new CodeSource(location,
            (java.security.cert.Certificate[]) null);

        if (testing) {
            System.out.println("Adding Policy Entry:");
            System.out.println("    CodeBase = " + location);
            System.out.println("    Signers = " + ge.signedBy);
            System.out.println("    with " + ge.principals.size() +
                    " Principals");
        }

        this.grantEntry = ge;
    }

    /**
     * get the codesource associated with this PolicyEntry
     */
    CodeSource getCodeSource() {
        return codesource;
    }

    /**
     * get the GrantEntry associated with this PolicyEntry
     */
    PolicyParser.GrantEntry getGrantEntry() {
        return grantEntry;
    }

    /**
     * convert the header portion, i.e. codebase, signer, principals, of
     * this policy entry into a string
     */
    String headerToString() {
        String pString = principalsToString();
        if (pString.length() == 0) {
            return codebaseToString();
        } else {
            return codebaseToString() + ", " + pString;
        }
    }

    /**
     * convert the Codebase/signer portion of this policy entry into a string
     */
    String codebaseToString() {

        String stringEntry = new String();

        if (grantEntry.codeBase != null &&
            grantEntry.codeBase.equals("") == false)
            stringEntry = stringEntry.concat
                                ("CodeBase \"" +
                                grantEntry.codeBase +
                                "\"");

        if (grantEntry.signedBy != null &&
            grantEntry.signedBy.equals("") == false)
            stringEntry = ((stringEntry.length() > 0) ?
                stringEntry.concat(", SignedBy \"" +
                                grantEntry.signedBy +
                                "\"") :
                stringEntry.concat("SignedBy \"" +
                                grantEntry.signedBy +
                                "\""));

        if (stringEntry.length() == 0)
            return new String("CodeBase <ALL>");
        return stringEntry;
    }

    /**
     * convert the Principals portion of this policy entry into a string
     */
    String principalsToString() {
        String result = "";
        if ((grantEntry.principals != null) &&
            (!grantEntry.principals.isEmpty())) {
            StringBuffer buffer = new StringBuffer(200);
            ListIterator<PolicyParser.PrincipalEntry> list =
                                grantEntry.principals.listIterator();
            while (list.hasNext()) {
                PolicyParser.PrincipalEntry pppe = list.next();
                buffer.append(" Principal " + pppe.getDisplayClass() + " " +
                    pppe.getDisplayName(true));
                if (list.hasNext()) buffer.append(", ");
            }
            result = buffer.toString();
        }
        return result;
    }

    /**
     * convert this policy entry into a PolicyParser.PermissionEntry
     */
    PolicyParser.PermissionEntry toPermissionEntry(Permission perm) {

        String actions = null;

        // get the actions
        if (perm.getActions() != null &&
            perm.getActions().trim() != "")
                actions = perm.getActions();

        PolicyParser.PermissionEntry pe = new PolicyParser.PermissionEntry
                        (perm.getClass().getName(),
                        perm.getName(),
                        actions);
        return pe;
    }
}

/**
 * The main window for the PolicyTool
 */
class ToolWindow extends Frame {
    // use serialVersionUID from JDK 1.2.2 for interoperability
    private static final long serialVersionUID = 5682568601210376777L;

    /* external paddings */
    public static final Insets TOP_PADDING = new Insets(25,0,0,0);
    public static final Insets BOTTOM_PADDING = new Insets(0,0,25,0);
    public static final Insets LITE_BOTTOM_PADDING = new Insets(0,0,10,0);
    public static final Insets LR_PADDING = new Insets(0,10,0,10);
    public static final Insets TOP_BOTTOM_PADDING = new Insets(15, 0, 15, 0);
    public static final Insets L_TOP_BOTTOM_PADDING = new Insets(5,10,15,0);
    public static final Insets LR_BOTTOM_PADDING = new Insets(0,10,5,10);
    public static final Insets L_BOTTOM_PADDING = new Insets(0,10,5,0);
    public static final Insets R_BOTTOM_PADDING = new Insets(0,0,5,10);

    /* buttons and menus */
    public static final String NEW_POLICY_FILE          =
                        PolicyTool.rb.getString("New");
    public static final String OPEN_POLICY_FILE         =
                        PolicyTool.rb.getString("Open");
    public static final String SAVE_POLICY_FILE         =
                        PolicyTool.rb.getString("Save");
    public static final String SAVE_AS_POLICY_FILE      =
                        PolicyTool.rb.getString("Save As");
    public static final String VIEW_WARNINGS            =
                        PolicyTool.rb.getString("View Warning Log");
    public static final String QUIT                     =
                        PolicyTool.rb.getString("Exit");
    public static final String ADD_POLICY_ENTRY         =
                        PolicyTool.rb.getString("Add Policy Entry");
    public static final String EDIT_POLICY_ENTRY        =
                        PolicyTool.rb.getString("Edit Policy Entry");
    public static final String REMOVE_POLICY_ENTRY      =
                        PolicyTool.rb.getString("Remove Policy Entry");
    public static final String EDIT_KEYSTORE            =
                        PolicyTool.rb.getString("Edit");
    public static final String ADD_PUBKEY_ALIAS         =
                        PolicyTool.rb.getString("Add Public Key Alias");
    public static final String REMOVE_PUBKEY_ALIAS      =
                        PolicyTool.rb.getString("Remove Public Key Alias");

    /* gridbag index for components in the main window (MW) */
    public static final int MW_FILENAME_LABEL           = 0;
    public static final int MW_FILENAME_TEXTFIELD       = 1;
    public static final int MW_PANEL                    = 2;
    public static final int MW_ADD_BUTTON               = 0;
    public static final int MW_EDIT_BUTTON              = 1;
    public static final int MW_REMOVE_BUTTON            = 2;
    public static final int MW_POLICY_LIST              = 3; // follows MW_PANEL

    private PolicyTool tool;

    /**
     * Constructor
     */
    ToolWindow(PolicyTool tool) {
        this.tool = tool;
    }

    /**
     * Initialize the PolicyTool window with the necessary components
     */
    private void initWindow() {

        // create the top menu bar
        MenuBar menuBar = new MenuBar();

        // create a File menu
        Menu menu = new Menu(PolicyTool.rb.getString("File"));
        menu.add(NEW_POLICY_FILE);
        menu.add(OPEN_POLICY_FILE);
        menu.add(SAVE_POLICY_FILE);
        menu.add(SAVE_AS_POLICY_FILE);
        menu.add(VIEW_WARNINGS);
        menu.add(QUIT);
        menu.addActionListener(new FileMenuListener(tool, this));
        menuBar.add(menu);
        setMenuBar(menuBar);

        // create a KeyStore menu
        menu = new Menu(PolicyTool.rb.getString("KeyStore"));
        menu.add(EDIT_KEYSTORE);
        menu.addActionListener(new MainWindowListener(tool, this));
        menuBar.add(menu);
        setMenuBar(menuBar);


        // policy entry listing
        Label label = new Label(PolicyTool.rb.getString("Policy File:"));
        addNewComponent(this, label, MW_FILENAME_LABEL,
                        0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        TOP_BOTTOM_PADDING);
        TextField tf = new TextField(50);
        tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("Policy File:"));
        tf.setEditable(false);
        addNewComponent(this, tf, MW_FILENAME_TEXTFIELD,
                        1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        TOP_BOTTOM_PADDING);


        // add ADD/REMOVE/EDIT buttons in a new panel
        Panel panel = new Panel();
        panel.setLayout(new GridBagLayout());

        Button button = new Button(ADD_POLICY_ENTRY);
        button.addActionListener(new MainWindowListener(tool, this));
        addNewComponent(panel, button, MW_ADD_BUTTON,
                        0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        LR_PADDING);

        button = new Button(EDIT_POLICY_ENTRY);
        button.addActionListener(new MainWindowListener(tool, this));
        addNewComponent(panel, button, MW_EDIT_BUTTON,
                        1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        LR_PADDING);

        button = new Button(REMOVE_POLICY_ENTRY);
        button.addActionListener(new MainWindowListener(tool, this));
        addNewComponent(panel, button, MW_REMOVE_BUTTON,
                        2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        LR_PADDING);

        addNewComponent(this, panel, MW_PANEL,
                        0, 2, 2, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        BOTTOM_PADDING);


        String policyFile = tool.getPolicyFileName();
        if (policyFile == null) {
            String userHome;
            userHome = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("user.home"));
            policyFile = userHome + File.separatorChar + ".java.policy";
        }

        try {
            // open the policy file
            tool.openPolicy(policyFile);

            // display the policy entries via the policy list textarea
            List list = new List(40, false);
            list.addActionListener(new PolicyListListener(tool, this));
            PolicyEntry entries[] = tool.getEntry();
            if (entries != null) {
                for (int i = 0; i < entries.length; i++)
                    list.add(entries[i].headerToString());
            }
            TextField newFilename = (TextField)
                                getComponent(MW_FILENAME_TEXTFIELD);
            newFilename.setText(policyFile);
            initPolicyList(list);

        } catch (FileNotFoundException fnfe) {
            // add blank policy listing
            List list = new List(40, false);
            list.addActionListener(new PolicyListListener(tool, this));
            initPolicyList(list);
            tool.setPolicyFileName(null);
            tool.modified = false;
            setVisible(true);

            // just add warning
            tool.warnings.addElement(fnfe.toString());

        } catch (Exception e) {
            // add blank policy listing
            List list = new List(40, false);
            list.addActionListener(new PolicyListListener(tool, this));
            initPolicyList(list);
            tool.setPolicyFileName(null);
            tool.modified = false;
            setVisible(true);

            // display the error
            MessageFormat form = new MessageFormat(PolicyTool.rb.getString
                ("Could not open policy file: policyFile: e.toString()"));
            Object[] source = {policyFile, e.toString()};
            displayErrorDialog(null, form.format(source));
        }
    }


    /**
     * Add a component to the PolicyTool window
     */
    void addNewComponent(Container container, Component component,
        int index, int gridx, int gridy, int gridwidth, int gridheight,
        double weightx, double weighty, int fill, Insets is) {

        // add the component at the specified gridbag index
        container.add(component, index);

        // set the constraints
        GridBagLayout gbl = (GridBagLayout)container.getLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.gridwidth = gridwidth;
        gbc.gridheight = gridheight;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.fill = fill;
        if (is != null) gbc.insets = is;
        gbl.setConstraints(component, gbc);
    }


    /**
     * Add a component to the PolicyTool window without external padding
     */
    void addNewComponent(Container container, Component component,
        int index, int gridx, int gridy, int gridwidth, int gridheight,
        double weightx, double weighty, int fill) {

        // delegate with "null" external padding
        addNewComponent(container, component, index, gridx, gridy,
                        gridwidth, gridheight, weightx, weighty,
                        fill, null);
    }


    /**
     * Init the policy_entry_list TEXTAREA component in the
     * PolicyTool window
     */
    void initPolicyList(List policyList) {

        // add the policy list to the window
        addNewComponent(this, policyList, MW_POLICY_LIST,
                        0, 3, 2, 1, 1.0, 1.0, GridBagConstraints.BOTH);
    }

    /**
     * Replace the policy_entry_list TEXTAREA component in the
     * PolicyTool window with an updated one.
     */
    void replacePolicyList(List policyList) {

        // remove the original list of Policy Entries
        // and add the new list of entries
        List list = (List)getComponent(MW_POLICY_LIST);
        list.removeAll();
        String newItems[] = policyList.getItems();
        for (int i = 0; i < newItems.length; i++)
            list.add(newItems[i]);
    }

    /**
     * display the main PolicyTool window
     */
    void displayToolWindow(String args[]) {

        setTitle(PolicyTool.rb.getString("Policy Tool"));
        setResizable(true);
        addWindowListener(new ToolWindowListener(this));
        setBounds(135, 80, 500, 500);
        setLayout(new GridBagLayout());

        initWindow();

        // display it
        setVisible(true);

        if (tool.newWarning == true) {
            displayStatusDialog(this, PolicyTool.rb.getString
                ("Errors have occurred while opening the " +
                "policy configuration.  View the Warning Log " +
                "for more information."));
        }
    }

    /**
     * displays a dialog box describing an error which occurred.
     */
    void displayErrorDialog(Window w, String error) {
        ToolDialog ed = new ToolDialog
                (PolicyTool.rb.getString("Error"), tool, this, true);

        // find where the PolicyTool gui is
        Point location = ((w == null) ?
                getLocationOnScreen() : w.getLocationOnScreen());
        ed.setBounds(location.x + 50, location.y + 50, 600, 100);
        ed.setLayout(new GridBagLayout());

        Label label = new Label(error);
        addNewComponent(ed, label, 0,
                        0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);

        Button okButton = new Button(PolicyTool.rb.getString("OK"));
        okButton.addActionListener(new ErrorOKButtonListener(ed));
        addNewComponent(ed, okButton, 1,
                        0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL);

        ed.pack();
        ed.setVisible(true);
    }

    /**
     * displays a dialog box describing an error which occurred.
     */
    void displayErrorDialog(Window w, Throwable t) {
        if (t instanceof NoDisplayException) {
            return;
        }
        displayErrorDialog(w, t.toString());
    }

    /**
     * displays a dialog box describing the status of an event
     */
    void displayStatusDialog(Window w, String status) {
        ToolDialog sd = new ToolDialog
                (PolicyTool.rb.getString("Status"), tool, this, true);

        // find the location of the PolicyTool gui
        Point location = ((w == null) ?
                getLocationOnScreen() : w.getLocationOnScreen());
        sd.setBounds(location.x + 50, location.y + 50, 500, 100);
        sd.setLayout(new GridBagLayout());

        Label label = new Label(status);
        addNewComponent(sd, label, 0,
                        0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);

        Button okButton = new Button(PolicyTool.rb.getString("OK"));
        okButton.addActionListener(new StatusOKButtonListener(sd));
        addNewComponent(sd, okButton, 1,
                        0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL);
        sd.pack();
        sd.setVisible(true);
    }

    /**
     * display the warning log
     */
    void displayWarningLog(Window w) {

        ToolDialog wd = new ToolDialog
                (PolicyTool.rb.getString("Warning"), tool, this, true);

        // find the location of the PolicyTool gui
        Point location = ((w == null) ?
                getLocationOnScreen() : w.getLocationOnScreen());
        wd.setBounds(location.x + 50, location.y + 50, 500, 100);
        wd.setLayout(new GridBagLayout());

        TextArea ta = new TextArea();
        ta.setEditable(false);
        for (int i = 0; i < tool.warnings.size(); i++) {
            ta.append(tool.warnings.elementAt(i));
            ta.append(PolicyTool.rb.getString("\n"));
        }
        addNewComponent(wd, ta, 0,
                        0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                        BOTTOM_PADDING);
        ta.setFocusable(false);

        Button okButton = new Button(PolicyTool.rb.getString("OK"));
        okButton.addActionListener(new CancelButtonListener(wd));
        addNewComponent(wd, okButton, 1,
                        0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                        LR_PADDING);

        wd.pack();
        wd.setVisible(true);
    }

    char displayYesNoDialog(Window w, String title, String prompt, String yes, String no) {

        final ToolDialog tw = new ToolDialog
                (title, tool, this, true);
        Point location = ((w == null) ?
                getLocationOnScreen() : w.getLocationOnScreen());
        tw.setBounds(location.x + 75, location.y + 100, 400, 150);
        tw.setLayout(new GridBagLayout());

        TextArea ta = new TextArea(prompt, 10, 50, TextArea.SCROLLBARS_VERTICAL_ONLY);
        ta.setEditable(false);
        addNewComponent(tw, ta, 0,
                0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);
        ta.setFocusable(false);

        Panel panel = new Panel();
        panel.setLayout(new GridBagLayout());

        // StringBuffer to store button press. Must be final.
        final StringBuffer chooseResult = new StringBuffer();

        Button button = new Button(yes);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooseResult.append('Y');
                tw.setVisible(false);
                tw.dispose();
            }
        });
        addNewComponent(panel, button, 0,
                           0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           LR_PADDING);

        button = new Button(no);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooseResult.append('N');
                tw.setVisible(false);
                tw.dispose();
            }
        });
        addNewComponent(panel, button, 1,
                           1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           LR_PADDING);

        addNewComponent(tw, panel, 1,
                0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL);

        tw.pack();
        tw.setVisible(true);
        if (chooseResult.length() > 0) {
            return chooseResult.charAt(0);
        } else {
            // I did encounter this once, don't why.
            return 'N';
        }
    }

}

/**
 * General dialog window
 */
class ToolDialog extends Dialog {
    // use serialVersionUID from JDK 1.2.2 for interoperability
    private static final long serialVersionUID = -372244357011301190L;

    /* necessary constants */
    public static final int NOACTION            = 0;
    public static final int QUIT                = 1;
    public static final int NEW                 = 2;
    public static final int OPEN                = 3;

    public static final String ALL_PERM_CLASS   =
                "java.security.AllPermission";
    public static final String FILE_PERM_CLASS  =
                "java.io.FilePermission";

    public static final String X500_PRIN_CLASS         =
                "javax.security.auth.x500.X500Principal";

    /* popup menus */
    public static final String PERM             =
        PolicyTool.rb.getString
        ("Permission:                                                       ");

    public static final String PRIN_TYPE        =
        PolicyTool.rb.getString("Principal Type:");
    public static final String PRIN_NAME        =
        PolicyTool.rb.getString("Principal Name:");

    /* more popu menus */
    public static final String PERM_NAME        =
        PolicyTool.rb.getString
        ("Target Name:                                                    ");

    /* and more popup menus */
    public static final String PERM_ACTIONS             =
      PolicyTool.rb.getString
      ("Actions:                                                             ");

    /* gridbag index for display OverWriteFile (OW) components */
    public static final int OW_LABEL                    = 0;
    public static final int OW_OK_BUTTON                = 1;
    public static final int OW_CANCEL_BUTTON            = 2;

    /* gridbag index for display PolicyEntry (PE) components */
    public static final int PE_CODEBASE_LABEL           = 0;
    public static final int PE_CODEBASE_TEXTFIELD       = 1;
    public static final int PE_SIGNEDBY_LABEL           = 2;
    public static final int PE_SIGNEDBY_TEXTFIELD       = 3;

    public static final int PE_PANEL0                   = 4;
    public static final int PE_ADD_PRIN_BUTTON          = 0;
    public static final int PE_EDIT_PRIN_BUTTON         = 1;
    public static final int PE_REMOVE_PRIN_BUTTON       = 2;

    public static final int PE_PRIN_LABEL               = 5;
    public static final int PE_PRIN_LIST                = 6;

    public static final int PE_PANEL1                   = 7;
    public static final int PE_ADD_PERM_BUTTON          = 0;
    public static final int PE_EDIT_PERM_BUTTON         = 1;
    public static final int PE_REMOVE_PERM_BUTTON       = 2;

    public static final int PE_PERM_LIST                = 8;

    public static final int PE_PANEL2                   = 9;
    public static final int PE_CANCEL_BUTTON            = 1;
    public static final int PE_DONE_BUTTON              = 0;

    /* the gridbag index for components in the Principal Dialog (PRD) */
    public static final int PRD_DESC_LABEL              = 0;
    public static final int PRD_PRIN_CHOICE             = 1;
    public static final int PRD_PRIN_TEXTFIELD          = 2;
    public static final int PRD_NAME_LABEL              = 3;
    public static final int PRD_NAME_TEXTFIELD          = 4;
    public static final int PRD_CANCEL_BUTTON           = 6;
    public static final int PRD_OK_BUTTON               = 5;

    /* the gridbag index for components in the Permission Dialog (PD) */
    public static final int PD_DESC_LABEL               = 0;
    public static final int PD_PERM_CHOICE              = 1;
    public static final int PD_PERM_TEXTFIELD           = 2;
    public static final int PD_NAME_CHOICE              = 3;
    public static final int PD_NAME_TEXTFIELD           = 4;
    public static final int PD_ACTIONS_CHOICE           = 5;
    public static final int PD_ACTIONS_TEXTFIELD        = 6;
    public static final int PD_SIGNEDBY_LABEL           = 7;
    public static final int PD_SIGNEDBY_TEXTFIELD       = 8;
    public static final int PD_CANCEL_BUTTON            = 10;
    public static final int PD_OK_BUTTON                = 9;

    /* modes for KeyStore */
    public static final int EDIT_KEYSTORE               = 0;

    /* the gridbag index for components in the Change KeyStore Dialog (KSD) */
    public static final int KSD_NAME_LABEL              = 0;
    public static final int KSD_NAME_TEXTFIELD          = 1;
    public static final int KSD_TYPE_LABEL              = 2;
    public static final int KSD_TYPE_TEXTFIELD          = 3;
    public static final int KSD_PROVIDER_LABEL          = 4;
    public static final int KSD_PROVIDER_TEXTFIELD      = 5;
    public static final int KSD_PWD_URL_LABEL           = 6;
    public static final int KSD_PWD_URL_TEXTFIELD       = 7;
    public static final int KSD_CANCEL_BUTTON           = 9;
    public static final int KSD_OK_BUTTON               = 8;

    /* the gridbag index for components in the User Save Changes Dialog (USC) */
    public static final int USC_LABEL                   = 0;
    public static final int USC_PANEL                   = 1;
    public static final int USC_YES_BUTTON              = 0;
    public static final int USC_NO_BUTTON               = 1;
    public static final int USC_CANCEL_BUTTON           = 2;

    /* gridbag index for the ConfirmRemovePolicyEntryDialog (CRPE) */
    public static final int CRPE_LABEL1                 = 0;
    public static final int CRPE_LABEL2                 = 1;
    public static final int CRPE_PANEL                  = 2;
    public static final int CRPE_PANEL_OK               = 0;
    public static final int CRPE_PANEL_CANCEL           = 1;

    /* some private static finals */
    private static final int PERMISSION                 = 0;
    private static final int PERMISSION_NAME            = 1;
    private static final int PERMISSION_ACTIONS         = 2;
    private static final int PERMISSION_SIGNEDBY        = 3;
    private static final int PRINCIPAL_TYPE             = 4;
    private static final int PRINCIPAL_NAME             = 5;

    public static java.util.ArrayList<Perm> PERM_ARRAY;
    public static java.util.ArrayList<Prin> PRIN_ARRAY;
    PolicyTool tool;
    ToolWindow tw;

    static {

        // set up permission objects

        PERM_ARRAY = new java.util.ArrayList<Perm>();
        PERM_ARRAY.add(new AllPerm());
        PERM_ARRAY.add(new AudioPerm());
        PERM_ARRAY.add(new AuthPerm());
        PERM_ARRAY.add(new AWTPerm());
        PERM_ARRAY.add(new DelegationPerm());
        PERM_ARRAY.add(new FilePerm());
        PERM_ARRAY.add(new InqSecContextPerm());
        PERM_ARRAY.add(new LogPerm());
        PERM_ARRAY.add(new MgmtPerm());
        PERM_ARRAY.add(new MBeanPerm());
        PERM_ARRAY.add(new MBeanSvrPerm());
        PERM_ARRAY.add(new MBeanTrustPerm());
        PERM_ARRAY.add(new NetPerm());
        PERM_ARRAY.add(new PrivCredPerm());
        PERM_ARRAY.add(new PropPerm());
        PERM_ARRAY.add(new ReflectPerm());
        PERM_ARRAY.add(new RuntimePerm());
        PERM_ARRAY.add(new SecurityPerm());
        PERM_ARRAY.add(new SerialPerm());
        PERM_ARRAY.add(new ServicePerm());
        PERM_ARRAY.add(new SocketPerm());
        PERM_ARRAY.add(new SQLPerm());
        PERM_ARRAY.add(new SSLPerm());
        PERM_ARRAY.add(new SubjDelegPerm());

        // set up principal objects

        PRIN_ARRAY = new java.util.ArrayList<Prin>();
        PRIN_ARRAY.add(new KrbPrin());
        PRIN_ARRAY.add(new X500Prin());
    }

    ToolDialog(String title, PolicyTool tool, ToolWindow tw, boolean modal) {
        super(tw, modal);
        setTitle(title);
        this.tool = tool;
        this.tw = tw;
        addWindowListener(new ChildWindowListener(this));
    }

    /**
     * get the Perm instance based on either the (shortened) class name
     * or the fully qualified class name
     */
    static Perm getPerm(String clazz, boolean fullClassName) {
        for (int i = 0; i < PERM_ARRAY.size(); i++) {
            Perm next = PERM_ARRAY.get(i);
            if (fullClassName) {
                if (next.FULL_CLASS.equals(clazz)) {
                    return next;
                }
            } else {
                if (next.CLASS.equals(clazz)) {
                    return next;
                }
            }
        }
        return null;
    }

    /**
     * get the Prin instance based on either the (shortened) class name
     * or the fully qualified class name
     */
    static Prin getPrin(String clazz, boolean fullClassName) {
        for (int i = 0; i < PRIN_ARRAY.size(); i++) {
            Prin next = PRIN_ARRAY.get(i);
            if (fullClassName) {
                if (next.FULL_CLASS.equals(clazz)) {
                    return next;
                }
            } else {
                if (next.CLASS.equals(clazz)) {
                    return next;
                }
            }
        }
        return null;
    }

    /**
     * ask user if they want to overwrite an existing file
     */
    void displayOverWriteFileDialog(String filename, int nextEvent) {

        // find where the PolicyTool gui is
        Point location = tw.getLocationOnScreen();
        setBounds(location.x + 75, location.y + 100, 400, 150);
        setLayout(new GridBagLayout());

        // ask the user if they want to over write the existing file
        MessageFormat form = new MessageFormat(PolicyTool.rb.getString
                ("OK to overwrite existing file filename?"));
        Object[] source = {filename};
        Label label = new Label(form.format(source));
        tw.addNewComponent(this, label, OW_LABEL,
                           0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.TOP_PADDING);

        // OK button
        Button button = new Button(PolicyTool.rb.getString("OK"));
        button.addActionListener(new OverWriteFileOKButtonListener
                (tool, tw, this, filename, nextEvent));
        tw.addNewComponent(this, button, OW_OK_BUTTON,
                           0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.TOP_PADDING);

        // Cancel button
        // -- if the user hits cancel, do NOT go on to the next event
        button = new Button(PolicyTool.rb.getString("Cancel"));
        button.addActionListener(new CancelButtonListener(this));
        tw.addNewComponent(this, button, OW_CANCEL_BUTTON,
                           1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.TOP_PADDING);

        setVisible(true);
    }

    /**
     * pop up a dialog so the user can enter info to add a new PolicyEntry
     * - if edit is TRUE, then the user is editing an existing entry
     *   and we should display the original info as well.
     *
     * - the other reason we need the 'edit' boolean is we need to know
     *   when we are adding a NEW policy entry.  in this case, we can
     *   not simply update the existing entry, because it doesn't exist.
     *   we ONLY update the GUI listing/info, and then when the user
     *   finally clicks 'OK' or 'DONE', then we can collect that info
     *   and add it to the policy.
     */
    void displayPolicyEntryDialog(boolean edit) {

        int listIndex = 0;
        PolicyEntry entries[] = null;
        TaggedList prinList = new TaggedList(3, false);
        prinList.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("Principal List"));
        prinList.addActionListener
                (new EditPrinButtonListener(tool, tw, this, edit));
        TaggedList permList = new TaggedList(10, false);
        permList.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("Permission List"));
        permList.addActionListener
                (new EditPermButtonListener(tool, tw, this, edit));

        // find where the PolicyTool gui is
        Point location = tw.getLocationOnScreen();
        setBounds(location.x + 75, location.y + 200, 650, 500);
        setLayout(new GridBagLayout());
        setResizable(true);

        if (edit) {
            // get the selected item
            entries = tool.getEntry();
            List policyList = (List)tw.getComponent(tw.MW_POLICY_LIST);
            listIndex = policyList.getSelectedIndex();

            // get principal list
            LinkedList principals =
                entries[listIndex].getGrantEntry().principals;
            for (int i = 0; i < principals.size(); i++) {
                String prinString = null;
                PolicyParser.PrincipalEntry nextPrin =
                        (PolicyParser.PrincipalEntry)principals.get(i);
                prinList.addTaggedItem(PrincipalEntryToUserFriendlyString(nextPrin), nextPrin);
            }

            // get permission list
            Vector<PolicyParser.PermissionEntry> permissions =
                entries[listIndex].getGrantEntry().permissionEntries;
            for (int i = 0; i < permissions.size(); i++) {
                String permString = null;
                PolicyParser.PermissionEntry nextPerm =
                                                permissions.elementAt(i);
                permList.addTaggedItem(ToolDialog.PermissionEntryToUserFriendlyString(nextPerm), nextPerm);
            }
        }

        // codebase label and textfield
        Label label = new Label(PolicyTool.rb.getString("CodeBase:"));
        tw.addNewComponent(this, label, PE_CODEBASE_LABEL,
                0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);
        TextField tf;
        tf = (edit ?
                new TextField(entries[listIndex].getGrantEntry().codeBase, 60) :
                new TextField(60));
        tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("Code Base"));
        tw.addNewComponent(this, tf, PE_CODEBASE_TEXTFIELD,
                1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);

        // signedby label and textfield
        label = new Label(PolicyTool.rb.getString("SignedBy:"));
        tw.addNewComponent(this, label, PE_SIGNEDBY_LABEL,
                           0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);
        tf = (edit ?
                new TextField(entries[listIndex].getGrantEntry().signedBy, 60) :
                new TextField(60));
        tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("Signed By:"));
        tw.addNewComponent(this, tf, PE_SIGNEDBY_TEXTFIELD,
                           1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);

        // panel for principal buttons
        Panel panel = new Panel();
        panel.setLayout(new GridBagLayout());

        Button button = new Button(PolicyTool.rb.getString("Add Principal"));
        button.addActionListener
                (new AddPrinButtonListener(tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_ADD_PRIN_BUTTON,
                0, 0, 1, 1, 100.0, 0.0, GridBagConstraints.HORIZONTAL);

        button = new Button(PolicyTool.rb.getString("Edit Principal"));
        button.addActionListener(new EditPrinButtonListener
                                                (tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_EDIT_PRIN_BUTTON,
                1, 0, 1, 1, 100.0, 0.0, GridBagConstraints.HORIZONTAL);

        button = new Button(PolicyTool.rb.getString("Remove Principal"));
        button.addActionListener(new RemovePrinButtonListener
                                        (tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_REMOVE_PRIN_BUTTON,
                2, 0, 1, 1, 100.0, 0.0, GridBagConstraints.HORIZONTAL);

        tw.addNewComponent(this, panel, PE_PANEL0,
                1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);

        // principal label and list
        label = new Label(PolicyTool.rb.getString("Principals:"));
        tw.addNewComponent(this, label, PE_PRIN_LABEL,
                           0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.BOTTOM_PADDING);
        tw.addNewComponent(this, prinList, PE_PRIN_LIST,
                           1, 3, 3, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.BOTTOM_PADDING);

        // panel for permission buttons
        panel = new Panel();
        panel.setLayout(new GridBagLayout());

        button = new Button(PolicyTool.rb.getString("  Add Permission"));
        button.addActionListener(new AddPermButtonListener
                                                (tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_ADD_PERM_BUTTON,
                0, 0, 1, 1, 100.0, 0.0, GridBagConstraints.HORIZONTAL);

        button = new Button(PolicyTool.rb.getString("  Edit Permission"));
        button.addActionListener(new EditPermButtonListener
                                                (tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_EDIT_PERM_BUTTON,
                1, 0, 1, 1, 100.0, 0.0, GridBagConstraints.HORIZONTAL);


        button = new Button(PolicyTool.rb.getString("Remove Permission"));
        button.addActionListener(new RemovePermButtonListener
                                        (tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_REMOVE_PERM_BUTTON,
                2, 0, 1, 1, 100.0, 0.0, GridBagConstraints.HORIZONTAL);

        tw.addNewComponent(this, panel, PE_PANEL1,
                0, 4, 2, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL,
                tw.LITE_BOTTOM_PADDING);

        // permission list
        tw.addNewComponent(this, permList, PE_PERM_LIST,
                           0, 5, 3, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.BOTTOM_PADDING);


        // panel for Done and Cancel buttons
        panel = new Panel();
        panel.setLayout(new GridBagLayout());

        // Done Button
        button = new Button(PolicyTool.rb.getString("Done"));
        button.addActionListener
                (new AddEntryDoneButtonListener(tool, tw, this, edit));
        tw.addNewComponent(panel, button, PE_DONE_BUTTON,
                           0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.LR_PADDING);

        // Cancel Button
        button = new Button(PolicyTool.rb.getString("Cancel"));
        button.addActionListener(new CancelButtonListener(this));
        tw.addNewComponent(panel, button, PE_CANCEL_BUTTON,
                           1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.LR_PADDING);

        // add the panel
        tw.addNewComponent(this, panel, PE_PANEL2,
                0, 6, 2, 1, 0.0, 0.0, GridBagConstraints.VERTICAL);

        setVisible(true);
    }

    /**
     * Read all the Policy information data in the dialog box
     * and construct a PolicyEntry object with it.
     */
    PolicyEntry getPolicyEntryFromDialog()
        throws InvalidParameterException, MalformedURLException,
        NoSuchMethodException, ClassNotFoundException, InstantiationException,
        IllegalAccessException, InvocationTargetException,
        CertificateException, IOException, Exception {

        // get the Codebase
        TextField tf = (TextField)getComponent(PE_CODEBASE_TEXTFIELD);
        String codebase = null;
        if (tf.getText().trim().equals("") == false)
                codebase = new String(tf.getText().trim());

        // get the SignedBy
        tf = (TextField)getComponent(PE_SIGNEDBY_TEXTFIELD);
        String signedby = null;
        if (tf.getText().trim().equals("") == false)
                signedby = new String(tf.getText().trim());

        // construct a new GrantEntry
        PolicyParser.GrantEntry ge =
                        new PolicyParser.GrantEntry(signedby, codebase);

        // get the new Principals
        LinkedList<PolicyParser.PrincipalEntry> prins =
                                new LinkedList<PolicyParser.PrincipalEntry>();
        TaggedList prinList = (TaggedList)getComponent(PE_PRIN_LIST);
        for (int i = 0; i < prinList.getItemCount(); i++) {
            prins.add((PolicyParser.PrincipalEntry)prinList.getObject(i));
        }
        ge.principals = prins;

        // get the new Permissions
        Vector<PolicyParser.PermissionEntry> perms =
                        new Vector<PolicyParser.PermissionEntry>();
        TaggedList permList = (TaggedList)getComponent(PE_PERM_LIST);
        for (int i = 0; i < permList.getItemCount(); i++) {
            perms.addElement((PolicyParser.PermissionEntry)permList.getObject(i));
        }
        ge.permissionEntries = perms;

        // construct a new PolicyEntry object
        PolicyEntry entry = new PolicyEntry(tool, ge);

        return entry;
    }

    /**
     * display a dialog box for the user to enter KeyStore information
     */
    void keyStoreDialog(int mode) {

        // find where the PolicyTool gui is
        Point location = tw.getLocationOnScreen();
        setBounds(location.x + 25, location.y + 100, 500, 300);
        setLayout(new GridBagLayout());

        if (mode == EDIT_KEYSTORE) {

            // KeyStore label and textfield
            Label label = new Label
                        (PolicyTool.rb.getString("KeyStore URL:"));
            tw.addNewComponent(this, label, KSD_NAME_LABEL,
                               0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);
            TextField tf = new TextField(tool.getKeyStoreName(), 30);

            // URL to U R L, so that accessibility reader will pronounce well
            tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("KeyStore U R L:"));
            tw.addNewComponent(this, tf, KSD_NAME_TEXTFIELD,
                               1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);

            // KeyStore type and textfield
            label = new Label(PolicyTool.rb.getString("KeyStore Type:"));
            tw.addNewComponent(this, label, KSD_TYPE_LABEL,
                               0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);
            tf = new TextField(tool.getKeyStoreType(), 30);
            tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("KeyStore Type:"));
            tw.addNewComponent(this, tf, KSD_TYPE_TEXTFIELD,
                               1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);

            // KeyStore provider and textfield
            label = new Label(PolicyTool.rb.getString
                                ("KeyStore Provider:"));
            tw.addNewComponent(this, label, KSD_PROVIDER_LABEL,
                               0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);
            tf = new TextField(tool.getKeyStoreProvider(), 30);
            tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("KeyStore Provider:"));
            tw.addNewComponent(this, tf, KSD_PROVIDER_TEXTFIELD,
                               1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);

            // KeyStore password URL and textfield
            label = new Label(PolicyTool.rb.getString
                                ("KeyStore Password URL:"));
            tw.addNewComponent(this, label, KSD_PWD_URL_LABEL,
                               0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);
            tf = new TextField(tool.getKeyStorePwdURL(), 30);
            tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("KeyStore Password U R L:"));
            tw.addNewComponent(this, tf, KSD_PWD_URL_TEXTFIELD,
                               1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.BOTTOM_PADDING);

            // OK button
            Button okButton = new Button(PolicyTool.rb.getString("OK"));
            okButton.addActionListener
                        (new ChangeKeyStoreOKButtonListener(tool, tw, this));
            tw.addNewComponent(this, okButton, KSD_OK_BUTTON,
                        0, 4, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL);

            // cancel button
            Button cancelButton = new Button(PolicyTool.rb.getString("Cancel"));
            cancelButton.addActionListener(new CancelButtonListener(this));
            tw.addNewComponent(this, cancelButton, KSD_CANCEL_BUTTON,
                        1, 4, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL);

        }
        setVisible(true);
    }

    /**
     * display a dialog box for the user to input Principal info
     *
     * if editPolicyEntry is false, then we are adding Principals to
     * a new PolicyEntry, and we only update the GUI listing
     * with the new Principal.
     *
     * if edit is true, then we are editing an existing Policy entry.
     */
    void displayPrincipalDialog(boolean editPolicyEntry, boolean edit) {

        PolicyParser.PrincipalEntry editMe = null;

        // get the Principal selected from the Principal List
        TaggedList prinList = (TaggedList)getComponent(PE_PRIN_LIST);
        int prinIndex = prinList.getSelectedIndex();

        if (edit) {
            editMe = (PolicyParser.PrincipalEntry)prinList.getObject(prinIndex);
        }

        ToolDialog newTD = new ToolDialog
                (PolicyTool.rb.getString("Principals"), tool, tw, true);
        newTD.addWindowListener(new ChildWindowListener(newTD));

        // find where the PolicyTool gui is
        Point location = getLocationOnScreen();
        newTD.setBounds(location.x + 50, location.y + 100, 650, 190);
        newTD.setLayout(new GridBagLayout());
        newTD.setResizable(true);

        // description label
        Label label = (edit ?
                new Label(PolicyTool.rb.getString("  Edit Principal:")) :
                new Label(PolicyTool.rb.getString("  Add New Principal:")));
        tw.addNewComponent(newTD, label, PRD_DESC_LABEL,
                           0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.TOP_BOTTOM_PADDING);

        // principal choice
        Choice choice = new Choice();
        choice.add(PRIN_TYPE);
        choice.getAccessibleContext().setAccessibleName(PRIN_TYPE);
        for (int i = 0; i < PRIN_ARRAY.size(); i++) {
            Prin next = PRIN_ARRAY.get(i);
            choice.add(next.CLASS);
        }

        choice.addItemListener(new PrincipalTypeMenuListener(newTD));
        if (edit) {
            if (PolicyParser.PrincipalEntry.WILDCARD_CLASS.equals
                                (editMe.getPrincipalClass())) {
                choice.select(PRIN_TYPE);
            } else {
                Prin inputPrin = getPrin(editMe.getPrincipalClass(), true);
                if (inputPrin != null) {
                    choice.select(inputPrin.CLASS);
                }
            }
        }

        tw.addNewComponent(newTD, choice, PRD_PRIN_CHOICE,
                           0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // principal textfield
        TextField tf;
        tf = (edit ?
                new TextField(editMe.getDisplayClass(), 30) :
                new TextField(30));
        tf.getAccessibleContext().setAccessibleName(PRIN_TYPE);
        tw.addNewComponent(newTD, tf, PRD_PRIN_TEXTFIELD,
                           1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // name label and textfield
        label = new Label(PRIN_NAME);
        tf = (edit ?
                new TextField(editMe.getDisplayName(), 40) :
                new TextField(40));
        tf.getAccessibleContext().setAccessibleName(PRIN_NAME);

        tw.addNewComponent(newTD, label, PRD_NAME_LABEL,
                           0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);
        tw.addNewComponent(newTD, tf, PRD_NAME_TEXTFIELD,
                           1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // OK button
        Button okButton = new Button(PolicyTool.rb.getString("OK"));
        okButton.addActionListener(
            new NewPolicyPrinOKButtonListener
                                        (tool, tw, this, newTD, edit));
        tw.addNewComponent(newTD, okButton, PRD_OK_BUTTON,
                           0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.TOP_BOTTOM_PADDING);
        // cancel button
        Button cancelButton = new Button(PolicyTool.rb.getString("Cancel"));
        cancelButton.addActionListener(new CancelButtonListener(newTD));
        tw.addNewComponent(newTD, cancelButton, PRD_CANCEL_BUTTON,
                           1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.TOP_BOTTOM_PADDING);

        newTD.setVisible(true);
    }

    /**
     * display a dialog box for the user to input Permission info
     *
     * if editPolicyEntry is false, then we are adding Permissions to
     * a new PolicyEntry, and we only update the GUI listing
     * with the new Permission.
     *
     * if edit is true, then we are editing an existing Permission entry.
     */
    void displayPermissionDialog(boolean editPolicyEntry, boolean edit) {

        PolicyParser.PermissionEntry editMe = null;

        // get the Permission selected from the Permission List
        TaggedList permList = (TaggedList)getComponent(PE_PERM_LIST);
        int permIndex = permList.getSelectedIndex();

        if (edit) {
            editMe = (PolicyParser.PermissionEntry)permList.getObject(permIndex);
        }

        ToolDialog newTD = new ToolDialog
                (PolicyTool.rb.getString("Permissions"), tool, tw, true);
        newTD.addWindowListener(new ChildWindowListener(newTD));

        // find where the PolicyTool gui is
        Point location = getLocationOnScreen();
        newTD.setBounds(location.x + 50, location.y + 100, 700, 250);
        newTD.setLayout(new GridBagLayout());
        newTD.setResizable(true);

        // description label
        Label label = (edit ?
                new Label(PolicyTool.rb.getString("  Edit Permission:")) :
                new Label(PolicyTool.rb.getString("  Add New Permission:")));
        tw.addNewComponent(newTD, label, PD_DESC_LABEL,
                           0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.TOP_BOTTOM_PADDING);

        // permission choice (added in alphabetical order)
        Choice choice = new Choice();
        choice.add(PERM);
        choice.getAccessibleContext().setAccessibleName(PERM);
        for (int i = 0; i < PERM_ARRAY.size(); i++) {
            Perm next = PERM_ARRAY.get(i);
            choice.add(next.CLASS);
        }
        choice.addItemListener(new PermissionMenuListener(newTD));
        tw.addNewComponent(newTD, choice, PD_PERM_CHOICE,
                           0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // permission textfield
        TextField tf;
        tf = (edit ? new TextField(editMe.permission, 30) : new TextField(30));
        tf.getAccessibleContext().setAccessibleName(PERM);
        if (edit) {
            Perm inputPerm = getPerm(editMe.permission, true);
            if (inputPerm != null) {
                choice.select(inputPerm.CLASS);
            }
        }
        tw.addNewComponent(newTD, tf, PD_PERM_TEXTFIELD,
                           1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // name label and textfield
        choice = new Choice();
        choice.add(PERM_NAME);
        choice.getAccessibleContext().setAccessibleName(PERM_NAME);
        choice.addItemListener(new PermissionNameMenuListener(newTD));
        tf = (edit ? new TextField(editMe.name, 40) : new TextField(40));
        tf.getAccessibleContext().setAccessibleName(PERM_NAME);
        if (edit) {
            setPermissionNames(getPerm(editMe.permission, true), choice, tf);
        }
        tw.addNewComponent(newTD, choice, PD_NAME_CHOICE,
                           0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);
        tw.addNewComponent(newTD, tf, PD_NAME_TEXTFIELD,
                           1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // actions label and textfield
        choice = new Choice();
        choice.add(PERM_ACTIONS);
        choice.getAccessibleContext().setAccessibleName(PERM_ACTIONS);
        choice.addItemListener(new PermissionActionsMenuListener(newTD));
        tf = (edit ? new TextField(editMe.action, 40) : new TextField(40));
        tf.getAccessibleContext().setAccessibleName(PERM_ACTIONS);
        if (edit) {
            setPermissionActions(getPerm(editMe.permission, true), choice, tf);
        }
        tw.addNewComponent(newTD, choice, PD_ACTIONS_CHOICE,
                           0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);
        tw.addNewComponent(newTD, tf, PD_ACTIONS_TEXTFIELD,
                           1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // signedby label and textfield
        label = new Label(PolicyTool.rb.getString("Signed By:"));
        tw.addNewComponent(newTD, label, PD_SIGNEDBY_LABEL,
                           0, 4, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);
        tf = (edit ? new TextField(editMe.signedBy, 40) : new TextField(40));
        tf.getAccessibleContext().setAccessibleName(
                PolicyTool.rb.getString("Signed By:"));
        tw.addNewComponent(newTD, tf, PD_SIGNEDBY_TEXTFIELD,
                           1, 4, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.LR_PADDING);

        // OK button
        Button okButton = new Button(PolicyTool.rb.getString("OK"));
        okButton.addActionListener(
            new NewPolicyPermOKButtonListener
                                    (tool, tw, this, newTD, edit));
        tw.addNewComponent(newTD, okButton, PD_OK_BUTTON,
                           0, 5, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.TOP_BOTTOM_PADDING);

        // cancel button
        Button cancelButton = new Button(PolicyTool.rb.getString("Cancel"));
        cancelButton.addActionListener(new CancelButtonListener(newTD));
        tw.addNewComponent(newTD, cancelButton, PD_CANCEL_BUTTON,
                           1, 5, 1, 1, 0.0, 0.0, GridBagConstraints.VERTICAL,
                           tw.TOP_BOTTOM_PADDING);

        newTD.setVisible(true);
    }

    /**
     * construct a Principal object from the Principal Info Dialog Box
     */
    PolicyParser.PrincipalEntry getPrinFromDialog() throws Exception {

        TextField tf = (TextField)getComponent(PRD_PRIN_TEXTFIELD);
        String pclass = new String(tf.getText().trim());
        tf = (TextField)getComponent(PRD_NAME_TEXTFIELD);
        String pname = new String(tf.getText().trim());
        if (pclass.equals("*")) {
            pclass = PolicyParser.PrincipalEntry.WILDCARD_CLASS;
        }
        if (pname.equals("*")) {
            pname = PolicyParser.PrincipalEntry.WILDCARD_NAME;
        }

        PolicyParser.PrincipalEntry pppe = null;

        if ((pclass.equals(PolicyParser.PrincipalEntry.WILDCARD_CLASS)) &&
            (!pname.equals(PolicyParser.PrincipalEntry.WILDCARD_NAME))) {
            throw new Exception
                        (PolicyTool.rb.getString("Cannot Specify Principal " +
                        "with a Wildcard Class without a Wildcard Name"));
        } else if (pname.equals("")) {
            throw new Exception
                        (PolicyTool.rb.getString("Cannot Specify Principal " +
                        "without a Name"));
        } else if (pclass.equals("")) {
            // make this consistent with what PolicyParser does
            // when it sees an empty principal class
            pclass = PolicyParser.REPLACE_NAME;
            tool.warnings.addElement(
                        "Warning: Principal name '" + pname +
                                "' specified without a Principal class.\n" +
                        "\t'" + pname + "' will be interpreted " +
                                "as a key store alias.\n" +
                        "\tThe final principal class will be " +
                                ToolDialog.X500_PRIN_CLASS + ".\n" +
                        "\tThe final principal name will be " +
                                "determined by the following:\n" +
                        "\n" +
                        "\tIf the key store entry identified by '"
                                + pname + "'\n" +
                        "\tis a key entry, then the principal name will be\n" +
                        "\tthe subject distinguished name from the first\n" +
                        "\tcertificate in the entry's certificate chain.\n" +
                        "\n" +
                        "\tIf the key store entry identified by '" +
                                pname + "'\n" +
                        "\tis a trusted certificate entry, then the\n" +
                        "\tprincipal name will be the subject distinguished\n" +
                        "\tname from the trusted public key certificate.");
            tw.displayStatusDialog(this,
                        "'" + pname + "' will be interpreted as a key " +
                        "store alias.  View Warning Log for details.");
        }
        return new PolicyParser.PrincipalEntry(pclass, pname);
    }


    /**
     * construct a Permission object from the Permission Info Dialog Box
     */
    PolicyParser.PermissionEntry getPermFromDialog() {

        TextField tf = (TextField)getComponent(PD_PERM_TEXTFIELD);
        String permission = new String(tf.getText().trim());
        tf = (TextField)getComponent(PD_NAME_TEXTFIELD);
        String name = null;
        if (tf.getText().trim().equals("") == false)
            name = new String(tf.getText().trim());
        if (permission.equals("") ||
            (!permission.equals(ALL_PERM_CLASS) && name == null)) {
            throw new InvalidParameterException(PolicyTool.rb.getString
                ("Permission and Target Name must have a value"));
        }

        // When the permission is FilePermission, we need to check the name
        // to make sure it's not escaped. We believe --
        //
        // String             name.lastIndexOf("\\\\")
        // ----------------   ------------------------
        // c:\foo\bar         -1, legal
        // c:\\foo\\bar       2, illegal
        // \\server\share     0, legal
        // \\\\server\share   2, illegal

        if (permission.equals(FILE_PERM_CLASS) && name.lastIndexOf("\\\\") > 0) {
            char result = tw.displayYesNoDialog(this,
                    PolicyTool.rb.getString("Warning"),
                    PolicyTool.rb.getString(
                        "Warning: File name may include escaped backslash characters. " +
                        "It is not necessary to escape backslash characters " +
                        "(the tool escapes characters as necessary when writing " +
                        "the policy contents to the persistent store).\n\n" +
                        "Click on Retain to retain the entered name, or click on " +
                        "Edit to edit the name."),
                    PolicyTool.rb.getString("Retain"),
                    PolicyTool.rb.getString("Edit")
                    );
            if (result != 'Y') {
                // an invisible exception
                throw new NoDisplayException();
            }
        }
        // get the Actions
        tf = (TextField)getComponent(PD_ACTIONS_TEXTFIELD);
        String actions = null;
        if (tf.getText().trim().equals("") == false)
            actions = new String(tf.getText().trim());

        // get the Signed By
        tf = (TextField)getComponent(PD_SIGNEDBY_TEXTFIELD);
        String signedBy = null;
        if (tf.getText().trim().equals("") == false)
            signedBy = new String(tf.getText().trim());

        PolicyParser.PermissionEntry pppe = new PolicyParser.PermissionEntry
                                (permission, name, actions);
        pppe.signedBy = signedBy;

        // see if the signers have public keys
        if (signedBy != null) {
                String signers[] = tool.parseSigners(pppe.signedBy);
                for (int i = 0; i < signers.length; i++) {
                try {
                    PublicKey pubKey = tool.getPublicKeyAlias(signers[i]);
                    if (pubKey == null) {
                        MessageFormat form = new MessageFormat
                            (PolicyTool.rb.getString
                            ("Warning: A public key for alias " +
                            "'signers[i]' does not exist.  " +
                            "Make sure a KeyStore is properly configured."));
                        Object[] source = {signers[i]};
                        tool.warnings.addElement(form.format(source));
                        tw.displayStatusDialog(this, form.format(source));
                    }
                } catch (Exception e) {
                    tw.displayErrorDialog(this, e);
                }
            }
        }
        return pppe;
    }

    /**
     * confirm that the user REALLY wants to remove the Policy Entry
     */
    void displayConfirmRemovePolicyEntry() {

        // find the entry to be removed
        List list = (List)tw.getComponent(tw.MW_POLICY_LIST);
        int index = list.getSelectedIndex();
        PolicyEntry entries[] = tool.getEntry();

        // find where the PolicyTool gui is
        Point location = tw.getLocationOnScreen();
        setBounds(location.x + 25, location.y + 100, 600, 400);
        setLayout(new GridBagLayout());

        // ask the user do they really want to do this?
        Label label = new Label
                (PolicyTool.rb.getString("Remove this Policy Entry?"));
        tw.addNewComponent(this, label, CRPE_LABEL1,
                           0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                           tw.BOTTOM_PADDING);

        // display the policy entry
        label = new Label(entries[index].codebaseToString());
        tw.addNewComponent(this, label, CRPE_LABEL2,
                        0, 1, 2, 1, 0.0, 0.0, GridBagConstraints.BOTH);
        label = new Label(entries[index].principalsToString().trim());
        tw.addNewComponent(this, label, CRPE_LABEL2+1,
                        0, 2, 2, 1, 0.0, 0.0, GridBagConstraints.BOTH);
        Vector<PolicyParser.PermissionEntry> perms =
                        entries[index].getGrantEntry().permissionEntries;
        for (int i = 0; i < perms.size(); i++) {
            PolicyParser.PermissionEntry nextPerm = perms.elementAt(i);
            String permString = ToolDialog.PermissionEntryToUserFriendlyString(nextPerm);
            label = new Label("    " + permString);
            if (i == (perms.size()-1)) {
                tw.addNewComponent(this, label, CRPE_LABEL2 + 2 + i,
                                 1, 3 + i, 1, 1, 0.0, 0.0,
                                 GridBagConstraints.BOTH, tw.BOTTOM_PADDING);
            } else {
                tw.addNewComponent(this, label, CRPE_LABEL2 + 2 + i,
                                 1, 3 + i, 1, 1, 0.0, 0.0,
                                 GridBagConstraints.BOTH);
            }
        }


        // add OK/CANCEL buttons in a new panel
        Panel panel = new Panel();
        panel.setLayout(new GridBagLayout());

        // OK button
        Button okButton = new Button(PolicyTool.rb.getString("OK"));
        okButton.addActionListener
                (new ConfirmRemovePolicyEntryOKButtonListener(tool, tw, this));
        tw.addNewComponent(panel, okButton, CRPE_PANEL_OK,
                           0, 0, 1, 1, 0.0, 0.0,
                           GridBagConstraints.VERTICAL, tw.LR_PADDING);

        // cancel button
        Button cancelButton = new Button(PolicyTool.rb.getString("Cancel"));
        cancelButton.addActionListener(new CancelButtonListener(this));
        tw.addNewComponent(panel, cancelButton, CRPE_PANEL_CANCEL,
                           1, 0, 1, 1, 0.0, 0.0,
                           GridBagConstraints.VERTICAL, tw.LR_PADDING);

        tw.addNewComponent(this, panel, CRPE_LABEL2 + 2 + perms.size(),
                           0, 3 + perms.size(), 2, 1, 0.0, 0.0,
                           GridBagConstraints.VERTICAL, tw.TOP_BOTTOM_PADDING);

        pack();
        setVisible(true);
    }

    /**
     * perform SAVE AS
     */
    void displaySaveAsDialog(int nextEvent) {

        // pop up a dialog box for the user to enter a filename.
        FileDialog fd = new FileDialog
                (tw, PolicyTool.rb.getString("Save As"), FileDialog.SAVE);
        fd.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                e.getWindow().setVisible(false);
            }
        });
        fd.setVisible(true);

        // see if the user hit cancel
        if (fd.getFile() == null ||
            fd.getFile().equals(""))
            return;

        // get the entered filename
        String filename = new String(fd.getDirectory() + fd.getFile());
        fd.dispose();

        // see if the file already exists
        File saveAsFile = new File(filename);
        if (saveAsFile.exists()) {
            // display a dialog box for the user to enter policy info
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Overwrite File"), tool, tw, true);
            td.displayOverWriteFileDialog(filename, nextEvent);
        } else {
            try {
                // save the policy entries to a file
                tool.savePolicy(filename);

                // display status
                MessageFormat form = new MessageFormat(PolicyTool.rb.getString
                        ("Policy successfully written to filename"));
                Object[] source = {filename};
                tw.displayStatusDialog(null, form.format(source));

                // display the new policy filename
                TextField newFilename = (TextField)tw.getComponent
                                (tw.MW_FILENAME_TEXTFIELD);
                newFilename.setText(filename);
                tw.setVisible(true);

                // now continue with the originally requested command
                // (QUIT, NEW, or OPEN)
                userSaveContinue(tool, tw, this, nextEvent);

            } catch (FileNotFoundException fnfe) {
                if (filename == null || filename.equals("")) {
                    tw.displayErrorDialog(null, new FileNotFoundException
                                (PolicyTool.rb.getString("null filename")));
                } else {
                    tw.displayErrorDialog(null, fnfe);
                }
            } catch (Exception ee) {
                tw.displayErrorDialog(null, ee);
            }
        }
    }

    /**
     * ask user if they want to save changes
     */
    void displayUserSave(int select) {

        if (tool.modified == true) {

            // find where the PolicyTool gui is
            Point location = tw.getLocationOnScreen();
            setBounds(location.x + 75, location.y + 100, 400, 150);
            setLayout(new GridBagLayout());

            Label label = new Label
                (PolicyTool.rb.getString("Save changes?"));
            tw.addNewComponent(this, label, USC_LABEL,
                               0, 0, 3, 1, 0.0, 0.0, GridBagConstraints.BOTH,
                               tw.L_TOP_BOTTOM_PADDING);

            Panel panel = new Panel();
            panel.setLayout(new GridBagLayout());

            Button yesButton = new Button(PolicyTool.rb.getString("Yes"));
            yesButton.addActionListener
                        (new UserSaveYesButtonListener(this, tool, tw, select));
            tw.addNewComponent(panel, yesButton, USC_YES_BUTTON,
                               0, 0, 1, 1, 0.0, 0.0,
                               GridBagConstraints.VERTICAL,
                               tw.LR_BOTTOM_PADDING);
            Button noButton = new Button(PolicyTool.rb.getString("No"));
            noButton.addActionListener
                        (new UserSaveNoButtonListener(this, tool, tw, select));
            tw.addNewComponent(panel, noButton, USC_NO_BUTTON,
                               1, 0, 1, 1, 0.0, 0.0,
                               GridBagConstraints.VERTICAL,
                               tw.LR_BOTTOM_PADDING);
            Button cancelButton = new Button(PolicyTool.rb.getString("Cancel"));
            cancelButton.addActionListener
                        (new UserSaveCancelButtonListener(this));
            tw.addNewComponent(panel, cancelButton, USC_CANCEL_BUTTON,
                               2, 0, 1, 1, 0.0, 0.0,
                               GridBagConstraints.VERTICAL,
                               tw.LR_BOTTOM_PADDING);

            tw.addNewComponent(this, panel, USC_PANEL,
                               0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BOTH);

            pack();
            setVisible(true);
        } else {
            // just do the original request (QUIT, NEW, or OPEN)
            userSaveContinue(tool, tw, this, select);
        }
    }

    /**
     * when the user sees the 'YES', 'NO', 'CANCEL' buttons on the
     * displayUserSave dialog, and the click on one of them,
     * we need to continue the originally requested action
     * (either QUITting, opening NEW policy file, or OPENing an existing
     * policy file.  do that now.
     */
    void userSaveContinue(PolicyTool tool, ToolWindow tw,
                        ToolDialog us, int select) {

        // now either QUIT, open a NEW policy file, or OPEN an existing policy
        switch(select) {
        case ToolDialog.QUIT:

            tw.setVisible(false);
            tw.dispose();
            System.exit(0);

        case ToolDialog.NEW:

            try {
                tool.openPolicy(null);
            } catch (Exception ee) {
                tool.modified = false;
                tw.displayErrorDialog(null, ee);
            }

            // display the policy entries via the policy list textarea
            List list = new List(40, false);
            list.addActionListener(new PolicyListListener(tool, tw));
            tw.replacePolicyList(list);

            // display null policy filename and keystore
            TextField newFilename = (TextField)
                                tw.getComponent(tw.MW_FILENAME_TEXTFIELD);
            newFilename.setText("");
            tw.setVisible(true);
            break;

        case ToolDialog.OPEN:

            // pop up a dialog box for the user to enter a filename.
            FileDialog fd = new FileDialog
                (tw, PolicyTool.rb.getString("Open"), FileDialog.LOAD);
            fd.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    e.getWindow().setVisible(false);
                }
            });
            fd.setVisible(true);

            // see if the user hit 'cancel'
            if (fd.getFile() == null ||
                fd.getFile().equals(""))
                return;

            // get the entered filename
            String policyFile = new String(fd.getDirectory() + fd.getFile());

            try {
                // open the policy file
                tool.openPolicy(policyFile);

                // display the policy entries via the policy list textarea
                list = new List(40, false);
                list.addActionListener(new PolicyListListener(tool, tw));
                PolicyEntry entries[] = tool.getEntry();
                if (entries != null) {
                    for (int i = 0; i < entries.length; i++)
                        list.add(entries[i].headerToString());
                }
                tw.replacePolicyList(list);
                tool.modified = false;

                // display the new policy filename
                newFilename = (TextField)
                                tw.getComponent(tw.MW_FILENAME_TEXTFIELD);
                newFilename.setText(policyFile);
                tw.setVisible(true);

                // inform user of warnings
                if (tool.newWarning == true) {
                    tw.displayStatusDialog(null, PolicyTool.rb.getString
                        ("Errors have occurred while opening the " +
                        "policy configuration.  View the Warning Log " +
                        "for more information."));
                }

            } catch (Exception e) {
                // add blank policy listing
                list = new List(40, false);
                list.addActionListener(new PolicyListListener(tool, tw));
                tw.replacePolicyList(list);
                tool.setPolicyFileName(null);
                tool.modified = false;

                // display a null policy filename
                newFilename = (TextField)
                                tw.getComponent(tw.MW_FILENAME_TEXTFIELD);
                newFilename.setText("");
                tw.setVisible(true);

                // display the error
                MessageFormat form = new MessageFormat(PolicyTool.rb.getString
                    ("Could not open policy file: policyFile: e.toString()"));
                Object[] source = {policyFile, e.toString()};
                tw.displayErrorDialog(null, form.format(source));
            }
            break;
        }
    }

    /**
     * Return a Menu list of names for a given permission
     *
     * If inputPerm's TARGETS are null, then this means TARGETS are
     * not allowed to be entered (and the TextField is set to be
     * non-editable).
     *
     * If TARGETS are valid but there are no standard ones
     * (user must enter them by hand) then the TARGETS array may be empty
     * (and of course non-null).
     */
    void setPermissionNames(Perm inputPerm, Choice names, TextField field) {
        names.removeAll();
        names.add(PERM_NAME);

        if (inputPerm == null) {
            // custom permission
            field.setEditable(true);
        } else if (inputPerm.TARGETS == null) {
            // standard permission with no targets
            field.setEditable(false);
        } else {
            // standard permission with standard targets
            field.setEditable(true);
            for (int i = 0; i < inputPerm.TARGETS.length; i++) {
                names.add(inputPerm.TARGETS[i]);
            }
        }
    }

    /**
     * Return a Menu list of actions for a given permission
     *
     * If inputPerm's ACTIONS are null, then this means ACTIONS are
     * not allowed to be entered (and the TextField is set to be
     * non-editable).  This is typically true for BasicPermissions.
     *
     * If ACTIONS are valid but there are no standard ones
     * (user must enter them by hand) then the ACTIONS array may be empty
     * (and of course non-null).
     */
    void setPermissionActions(Perm inputPerm, Choice actions, TextField field) {
        actions.removeAll();
        actions.add(PERM_ACTIONS);

        if (inputPerm == null) {
            // custom permission
            field.setEditable(true);
        } else if (inputPerm.ACTIONS == null) {
            // standard permission with no actions
            field.setEditable(false);
        } else {
            // standard permission with standard actions
            field.setEditable(true);
            for (int i = 0; i < inputPerm.ACTIONS.length; i++) {
                actions.add(inputPerm.ACTIONS[i]);
            }
        }
    }

    static String PermissionEntryToUserFriendlyString(PolicyParser.PermissionEntry pppe) {
        String result = pppe.permission;
        if (pppe.name != null) {
            result += " " + pppe.name;
        }
        if (pppe.action != null) {
            result += ", \"" + pppe.action + "\"";
        }
        if (pppe.signedBy != null) {
            result += ", signedBy " + pppe.signedBy;
        }
        return result;
    }

    static String PrincipalEntryToUserFriendlyString(PolicyParser.PrincipalEntry pppe) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pppe.write(pw);
        return sw.toString();
    }
}

/**
 * Event handler for the PolicyTool window
 */
class ToolWindowListener implements WindowListener {

    private ToolWindow tw;

    ToolWindowListener(ToolWindow tw) {
        this.tw = tw;
    }

    public void windowOpened(WindowEvent we) {
    }

    public void windowClosing(WindowEvent we) {

        // XXX
        // should we ask user if they want to save changes?
        // (we do if they choose the Menu->Exit)
        // seems that if they kill the application by hand,
        // we don't have to ask.

        tw.setVisible(false);
        tw.dispose();
        System.exit(0);
    }

    public void windowClosed(WindowEvent we) {
        System.exit(0);
    }

    public void windowIconified(WindowEvent we) {
    }

    public void windowDeiconified(WindowEvent we) {
    }

    public void windowActivated(WindowEvent we) {
    }

    public void windowDeactivated(WindowEvent we) {
    }
}

/**
 * Event handler for the Policy List
 */
class PolicyListListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;

    PolicyListListener(PolicyTool tool, ToolWindow tw) {
        this.tool = tool;
        this.tw = tw;

    }

    public void actionPerformed(ActionEvent e) {

        // display the permission list for a policy entry
        ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Policy Entry"), tool, tw, true);
        td.displayPolicyEntryDialog(true);
    }
}

/**
 * Event handler for the File Menu
 */
class FileMenuListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;

    FileMenuListener(PolicyTool tool, ToolWindow tw) {
        this.tool = tool;
        this.tw = tw;
    }

    public void actionPerformed(ActionEvent e) {

        if (PolicyTool.collator.compare(e.getActionCommand(), tw.QUIT) == 0) {

            // ask user if they want to save changes
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Save Changes"), tool, tw, true);
            td.displayUserSave(td.QUIT);

            // the above method will perform the QUIT as long as the
            // user does not CANCEL the request

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.NEW_POLICY_FILE) == 0) {

            // ask user if they want to save changes
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Save Changes"), tool, tw, true);
            td.displayUserSave(td.NEW);

            // the above method will perform the NEW as long as the
            // user does not CANCEL the request

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.OPEN_POLICY_FILE) == 0) {

            // ask user if they want to save changes
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Save Changes"), tool, tw, true);
            td.displayUserSave(td.OPEN);

            // the above method will perform the OPEN as long as the
            // user does not CANCEL the request

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.SAVE_POLICY_FILE) == 0) {

            // get the previously entered filename
            String filename = ((TextField)
                    tw.getComponent(tw.MW_FILENAME_TEXTFIELD)).getText();

            // if there is no filename, do a SAVE_AS
            if (filename == null || filename.length() == 0) {
                // user wants to SAVE AS
                ToolDialog td = new ToolDialog
                        (PolicyTool.rb.getString("Save As"), tool, tw, true);
                td.displaySaveAsDialog(td.NOACTION);
            } else {
                try {
                    // save the policy entries to a file
                    tool.savePolicy(filename);

                    // display status
                    MessageFormat form = new MessageFormat
                        (PolicyTool.rb.getString
                        ("Policy successfully written to filename"));
                    Object[] source = {filename};
                    tw.displayStatusDialog(null, form.format(source));
                } catch (FileNotFoundException fnfe) {
                    if (filename == null || filename.equals("")) {
                        tw.displayErrorDialog(null, new FileNotFoundException
                                (PolicyTool.rb.getString("null filename")));
                    } else {
                        tw.displayErrorDialog(null, fnfe);
                    }
                } catch (Exception ee) {
                    tw.displayErrorDialog(null, ee);
                }
            }
        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                                tw.SAVE_AS_POLICY_FILE) == 0) {

            // user wants to SAVE AS
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Save As"), tool, tw, true);
            td.displaySaveAsDialog(td.NOACTION);

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                                tw.VIEW_WARNINGS) == 0) {
            tw.displayWarningLog(null);
        }
    }
}

/**
 * Event handler for the main window buttons and Edit Menu
 */
class MainWindowListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;

    MainWindowListener(PolicyTool tool, ToolWindow tw) {
        this.tool = tool;
        this.tw = tw;
    }

    public void actionPerformed(ActionEvent e) {

        if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.ADD_POLICY_ENTRY) == 0) {

            // display a dialog box for the user to enter policy info
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Policy Entry"), tool, tw, true);
            td.displayPolicyEntryDialog(false);

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.REMOVE_POLICY_ENTRY) == 0) {

            // get the selected entry
            List list = (List)tw.getComponent(tw.MW_POLICY_LIST);
            int index = list.getSelectedIndex();
            if (index < 0) {
                tw.displayErrorDialog(null, new Exception
                        (PolicyTool.rb.getString("No Policy Entry selected")));
                return;
            }

            // ask the user if they really want to remove the policy entry
            ToolDialog td = new ToolDialog(PolicyTool.rb.getString
                ("Remove Policy Entry"), tool, tw, true);
            td.displayConfirmRemovePolicyEntry();

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.EDIT_POLICY_ENTRY) == 0) {

            // get the selected entry
            List list = (List)tw.getComponent(tw.MW_POLICY_LIST);
            int index = list.getSelectedIndex();
            if (index < 0) {
                tw.displayErrorDialog(null, new Exception
                        (PolicyTool.rb.getString("No Policy Entry selected")));
                return;
            }

            // display the permission list for a policy entry
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("Policy Entry"), tool, tw, true);
            td.displayPolicyEntryDialog(true);

        } else if (PolicyTool.collator.compare(e.getActionCommand(),
                                        tw.EDIT_KEYSTORE) == 0) {

            // display a dialog box for the user to enter keystore info
            ToolDialog td = new ToolDialog
                (PolicyTool.rb.getString("KeyStore"), tool, tw, true);
            td.keyStoreDialog(td.EDIT_KEYSTORE);
        }
    }
}

/**
 * Event handler for OverWriteFileOKButton button
 */
class OverWriteFileOKButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private String filename;
    private int nextEvent;

    OverWriteFileOKButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, String filename, int nextEvent) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.filename = filename;
        this.nextEvent = nextEvent;
    }

    public void actionPerformed(ActionEvent e) {
        try {
            // save the policy entries to a file
            tool.savePolicy(filename);

            // display status
            MessageFormat form = new MessageFormat
                (PolicyTool.rb.getString
                ("Policy successfully written to filename"));
            Object[] source = {filename};
            tw.displayStatusDialog(null, form.format(source));

            // display the new policy filename
            TextField newFilename = (TextField)tw.getComponent
                                (tw.MW_FILENAME_TEXTFIELD);
            newFilename.setText(filename);
            tw.setVisible(true);

            // now continue with the originally requested command
            // (QUIT, NEW, or OPEN)
            td.setVisible(false);
            td.dispose();
            td.userSaveContinue(tool, tw, td, nextEvent);

        } catch (FileNotFoundException fnfe) {
            if (filename == null || filename.equals("")) {
                tw.displayErrorDialog(null, new FileNotFoundException
                                (PolicyTool.rb.getString("null filename")));
            } else {
                tw.displayErrorDialog(null, fnfe);
            }
            td.setVisible(false);
            td.dispose();
        } catch (Exception ee) {
            tw.displayErrorDialog(null, ee);
            td.setVisible(false);
            td.dispose();
        }
    }
}

/**
 * Event handler for AddEntryDoneButton button
 *
 * -- if edit is TRUE, then we are EDITing an existing PolicyEntry
 *    and we need to update both the policy and the GUI listing.
 *    if edit is FALSE, then we are ADDing a new PolicyEntry,
 *    so we only need to update the GUI listing.
 */
class AddEntryDoneButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean edit;

    AddEntryDoneButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean edit) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.edit = edit;
    }

    public void actionPerformed(ActionEvent e) {

        try {
            // get a PolicyEntry object from the dialog policy info
            PolicyEntry newEntry = td.getPolicyEntryFromDialog();
            PolicyParser.GrantEntry newGe = newEntry.getGrantEntry();

            // see if all the signers have public keys
            if (newGe.signedBy != null) {
                String signers[] = tool.parseSigners(newGe.signedBy);
                for (int i = 0; i < signers.length; i++) {
                    PublicKey pubKey = tool.getPublicKeyAlias(signers[i]);
                    if (pubKey == null) {
                        MessageFormat form = new MessageFormat
                            (PolicyTool.rb.getString
                            ("Warning: A public key for alias " +
                            "'signers[i]' does not exist.  " +
                            "Make sure a KeyStore is properly configured."));
                        Object[] source = {signers[i]};
                        tool.warnings.addElement(form.format(source));
                        tw.displayStatusDialog(td, form.format(source));
                    }
                }
            }

            // add the entry
            List policyList = (List)tw.getComponent(tw.MW_POLICY_LIST);
            if (edit) {
                int listIndex = policyList.getSelectedIndex();
                tool.addEntry(newEntry, listIndex);
                String newCodeBaseStr = newEntry.headerToString();
                if (PolicyTool.collator.compare
                        (newCodeBaseStr, policyList.getItem(listIndex)) != 0)
                    tool.modified = true;
                policyList.replaceItem(newCodeBaseStr, listIndex);
            } else {
                tool.addEntry(newEntry, -1);
                policyList.add(newEntry.headerToString());
                tool.modified = true;
            }
            td.setVisible(false);
            td.dispose();

        } catch (Exception eee) {
            tw.displayErrorDialog(td, eee);
        }
    }
}

/**
 * Event handler for ChangeKeyStoreOKButton button
 */
class ChangeKeyStoreOKButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;

    ChangeKeyStoreOKButtonListener(PolicyTool tool, ToolWindow tw,
                ToolDialog td) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
    }

    public void actionPerformed(ActionEvent e) {

        String URLString = ((TextField)
                td.getComponent(td.KSD_NAME_TEXTFIELD)).getText().trim();
        String type = ((TextField)
                td.getComponent(td.KSD_TYPE_TEXTFIELD)).getText().trim();
        String provider = ((TextField)
                td.getComponent(td.KSD_PROVIDER_TEXTFIELD)).getText().trim();
        String pwdURL = ((TextField)
                td.getComponent(td.KSD_PWD_URL_TEXTFIELD)).getText().trim();

        try {
            tool.openKeyStore
                        ((URLString.length() == 0 ? null : URLString),
                        (type.length() == 0 ? null : type),
                        (provider.length() == 0 ? null : provider),
                        (pwdURL.length() == 0 ? null : pwdURL));
            tool.modified = true;
        } catch (Exception ex) {
            MessageFormat form = new MessageFormat(PolicyTool.rb.getString
                ("Unable to open KeyStore: ex.toString()"));
            Object[] source = {ex.toString()};
            tw.displayErrorDialog(td, form.format(source));
            return;
        }

        td.dispose();
    }
}

/**
 * Event handler for AddPrinButton button
 */
class AddPrinButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean editPolicyEntry;

    AddPrinButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean editPolicyEntry) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.editPolicyEntry = editPolicyEntry;
    }

    public void actionPerformed(ActionEvent e) {

        // display a dialog box for the user to enter principal info
        td.displayPrincipalDialog(editPolicyEntry, false);
    }
}

/**
 * Event handler for AddPermButton button
 */
class AddPermButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean editPolicyEntry;

    AddPermButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean editPolicyEntry) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.editPolicyEntry = editPolicyEntry;
    }

    public void actionPerformed(ActionEvent e) {

        // display a dialog box for the user to enter permission info
        td.displayPermissionDialog(editPolicyEntry, false);
    }
}

/**
 * Event handler for AddPrinOKButton button
 */
class NewPolicyPrinOKButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog listDialog;
    private ToolDialog infoDialog;
    private boolean edit;

    NewPolicyPrinOKButtonListener(PolicyTool tool,
                                ToolWindow tw,
                                ToolDialog listDialog,
                                ToolDialog infoDialog,
                                boolean edit) {
        this.tool = tool;
        this.tw = tw;
        this.listDialog = listDialog;
        this.infoDialog = infoDialog;
        this.edit = edit;
    }

    public void actionPerformed(ActionEvent e) {

        try {
            // read in the new principal info from Dialog Box
            PolicyParser.PrincipalEntry pppe =
                        infoDialog.getPrinFromDialog();
            if (pppe != null) {
                try {
                    tool.verifyPrincipal(pppe.getPrincipalClass(),
                                        pppe.getPrincipalName());
                } catch (ClassNotFoundException cnfe) {
                    MessageFormat form = new MessageFormat
                                (PolicyTool.rb.getString
                                ("Warning: Class not found: class"));
                    Object[] source = {pppe.getPrincipalClass()};
                    tool.warnings.addElement(form.format(source));
                    tw.displayStatusDialog(infoDialog, form.format(source));
                }

                // add the principal to the GUI principal list
                TaggedList prinList =
                    (TaggedList)listDialog.getComponent(listDialog.PE_PRIN_LIST);

                String prinString = ToolDialog.PrincipalEntryToUserFriendlyString(pppe);
                if (edit) {
                    // if editing, replace the original principal
                    int index = prinList.getSelectedIndex();
                    prinList.replaceTaggedItem(prinString, pppe, index);
                } else {
                    // if adding, just add it to the end
                    prinList.addTaggedItem(prinString, pppe);
                }
            }
            infoDialog.dispose();
        } catch (Exception ee) {
            tw.displayErrorDialog(infoDialog, ee);
        }
    }
}

/**
 * Event handler for AddPermOKButton button
 */
class NewPolicyPermOKButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog listDialog;
    private ToolDialog infoDialog;
    private boolean edit;

    NewPolicyPermOKButtonListener(PolicyTool tool,
                                ToolWindow tw,
                                ToolDialog listDialog,
                                ToolDialog infoDialog,
                                boolean edit) {
        this.tool = tool;
        this.tw = tw;
        this.listDialog = listDialog;
        this.infoDialog = infoDialog;
        this.edit = edit;
    }

    public void actionPerformed(ActionEvent e) {

        try {
            // read in the new permission info from Dialog Box
            PolicyParser.PermissionEntry pppe =
                        infoDialog.getPermFromDialog();

            try {
                tool.verifyPermission(pppe.permission, pppe.name, pppe.action);
            } catch (ClassNotFoundException cnfe) {
                MessageFormat form = new MessageFormat(PolicyTool.rb.getString
                                ("Warning: Class not found: class"));
                Object[] source = {pppe.permission};
                tool.warnings.addElement(form.format(source));
                tw.displayStatusDialog(infoDialog, form.format(source));
            }

            // add the permission to the GUI permission list
            TaggedList permList =
                (TaggedList)listDialog.getComponent(listDialog.PE_PERM_LIST);

            String permString = ToolDialog.PermissionEntryToUserFriendlyString(pppe);
            if (edit) {
                // if editing, replace the original permission
                int which = permList.getSelectedIndex();
                permList.replaceTaggedItem(permString, pppe, which);
            } else {
                // if adding, just add it to the end
                permList.addTaggedItem(permString, pppe);
            }
            infoDialog.dispose();

        } catch (InvocationTargetException ite) {
            tw.displayErrorDialog(infoDialog, ite.getTargetException());
        } catch (Exception ee) {
            tw.displayErrorDialog(infoDialog, ee);
        }
    }
}

/**
 * Event handler for RemovePrinButton button
 */
class RemovePrinButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean edit;

    RemovePrinButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean edit) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.edit = edit;
    }

    public void actionPerformed(ActionEvent e) {

        // get the Principal selected from the Principal List
        TaggedList prinList = (TaggedList)td.getComponent(td.PE_PRIN_LIST);
        int prinIndex = prinList.getSelectedIndex();

        if (prinIndex < 0) {
            tw.displayErrorDialog(td, new Exception
                (PolicyTool.rb.getString("No principal selected")));
            return;
        }
        // remove the principal from the display
        prinList.removeTaggedItem(prinIndex);
    }
}

/**
 * Event handler for RemovePermButton button
 */
class RemovePermButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean edit;

    RemovePermButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean edit) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.edit = edit;
    }

    public void actionPerformed(ActionEvent e) {

        // get the Permission selected from the Permission List
        TaggedList permList = (TaggedList)td.getComponent(td.PE_PERM_LIST);
        int permIndex = permList.getSelectedIndex();

        if (permIndex < 0) {
            tw.displayErrorDialog(td, new Exception
                (PolicyTool.rb.getString("No permission selected")));
            return;
        }
        // remove the permission from the display
        permList.removeTaggedItem(permIndex);

    }
}

/**
 * Event handler for Edit Principal button
 *
 * We need the editPolicyEntry boolean to tell us if the user is
 * adding a new PolicyEntry at this time, or editing an existing entry.
 * If the user is adding a new PolicyEntry, we ONLY update the
 * GUI listing.  If the user is editing an existing PolicyEntry, we
 * update both the GUI listing and the actual PolicyEntry.
 */
class EditPrinButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean editPolicyEntry;

    EditPrinButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean editPolicyEntry) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.editPolicyEntry = editPolicyEntry;
    }

    public void actionPerformed(ActionEvent e) {

        // get the Principal selected from the Principal List
        TaggedList list = (TaggedList)td.getComponent(td.PE_PRIN_LIST);
        int prinIndex = list.getSelectedIndex();

        if (prinIndex < 0) {
            tw.displayErrorDialog(td, new Exception
                (PolicyTool.rb.getString("No principal selected")));
            return;
        }
        td.displayPrincipalDialog(editPolicyEntry, true);
    }
}

/**
 * Event handler for Edit Permission button
 *
 * We need the editPolicyEntry boolean to tell us if the user is
 * adding a new PolicyEntry at this time, or editing an existing entry.
 * If the user is adding a new PolicyEntry, we ONLY update the
 * GUI listing.  If the user is editing an existing PolicyEntry, we
 * update both the GUI listing and the actual PolicyEntry.
 */
class EditPermButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog td;
    private boolean editPolicyEntry;

    EditPermButtonListener(PolicyTool tool, ToolWindow tw,
                                ToolDialog td, boolean editPolicyEntry) {
        this.tool = tool;
        this.tw = tw;
        this.td = td;
        this.editPolicyEntry = editPolicyEntry;
    }

    public void actionPerformed(ActionEvent e) {

        // get the Permission selected from the Permission List
        List list = (List)td.getComponent(td.PE_PERM_LIST);
        int permIndex = list.getSelectedIndex();

        if (permIndex < 0) {
            tw.displayErrorDialog(td, new Exception
                (PolicyTool.rb.getString("No permission selected")));
            return;
        }
        td.displayPermissionDialog(editPolicyEntry, true);
    }
}

/**
 * Event handler for Principal Popup Menu
 */
class PrincipalTypeMenuListener implements ItemListener {

    private ToolDialog td;

    PrincipalTypeMenuListener(ToolDialog td) {
        this.td = td;
    }

    public void itemStateChanged(ItemEvent e) {

        Choice prin = (Choice)td.getComponent(td.PRD_PRIN_CHOICE);
        TextField prinField =
                        (TextField)td.getComponent(td.PRD_PRIN_TEXTFIELD);
        TextField nameField =
                        (TextField)td.getComponent(td.PRD_NAME_TEXTFIELD);

        prin.getAccessibleContext().setAccessibleName(
            PolicyTool.splitToWords((String)e.getItem()));
        if (((String)e.getItem()).equals(td.PRIN_TYPE)) {
            // ignore if they choose "Principal Type:" item
            if (prinField.getText() != null &&
                prinField.getText().length() > 0) {
                Prin inputPrin = td.getPrin(prinField.getText(), true);
                prin.select(inputPrin.CLASS);
            }
            return;
        }

        // if you change the principal, clear the name
        if (prinField.getText().indexOf((String)e.getItem()) == -1) {
            nameField.setText("");
        }

        // set the text in the textfield and also modify the
        // pull-down choice menus to reflect the correct possible
        // set of names and actions
        Prin inputPrin = td.getPrin((String)e.getItem(), false);
        if (inputPrin != null) {
            prinField.setText(inputPrin.FULL_CLASS);
        }
    }
}

/**
 * Event handler for Permission Popup Menu
 */
class PermissionMenuListener implements ItemListener {

    private ToolDialog td;

    PermissionMenuListener(ToolDialog td) {
        this.td = td;
    }

    public void itemStateChanged(ItemEvent e) {

        Choice perms = (Choice)td.getComponent(td.PD_PERM_CHOICE);
        Choice names = (Choice)td.getComponent(td.PD_NAME_CHOICE);
        Choice actions = (Choice)td.getComponent(td.PD_ACTIONS_CHOICE);
        TextField nameField =
                        (TextField)td.getComponent(td.PD_NAME_TEXTFIELD);
        TextField actionsField =
                        (TextField)td.getComponent(td.PD_ACTIONS_TEXTFIELD);
        TextField permField = (TextField)td.getComponent(td.PD_PERM_TEXTFIELD);
        TextField signedbyField =
                        (TextField)td.getComponent(td.PD_SIGNEDBY_TEXTFIELD);

        perms.getAccessibleContext().setAccessibleName(
            PolicyTool.splitToWords((String)e.getItem()));

        // ignore if they choose the 'Permission:' item
        if (PolicyTool.collator.compare((String)e.getItem(), td.PERM) == 0) {
            if (permField.getText() != null &&
                permField.getText().length() > 0) {

                Perm inputPerm = td.getPerm(permField.getText(), true);
                if (inputPerm != null) {
                    perms.select(inputPerm.CLASS);
                }
            }
            return;
        }

        // if you change the permission, clear the name, actions, and signedBy
        if (permField.getText().indexOf((String)e.getItem()) == -1) {
            nameField.setText("");
            actionsField.setText("");
            signedbyField.setText("");
        }

        // set the text in the textfield and also modify the
        // pull-down choice menus to reflect the correct possible
        // set of names and actions

        Perm inputPerm = td.getPerm((String)e.getItem(), false);
        if (inputPerm == null) {
            permField.setText("");
        } else {
            permField.setText(inputPerm.FULL_CLASS);
        }
        td.setPermissionNames(inputPerm, names, nameField);
        td.setPermissionActions(inputPerm, actions, actionsField);
    }
}

/**
 * Event handler for Permission Name Popup Menu
 */
class PermissionNameMenuListener implements ItemListener {

    private ToolDialog td;

    PermissionNameMenuListener(ToolDialog td) {
        this.td = td;
    }

    public void itemStateChanged(ItemEvent e) {

        Choice names = (Choice)td.getComponent(td.PD_NAME_CHOICE);
        names.getAccessibleContext().setAccessibleName(
            PolicyTool.splitToWords((String)e.getItem()));

        if (((String)e.getItem()).indexOf(td.PERM_NAME) != -1)
            return;

        TextField tf = (TextField)td.getComponent(td.PD_NAME_TEXTFIELD);
        tf.setText((String)e.getItem());
    }
}

/**
 * Event handler for Permission Actions Popup Menu
 */
class PermissionActionsMenuListener implements ItemListener {

    private ToolDialog td;

    PermissionActionsMenuListener(ToolDialog td) {
        this.td = td;
    }

    public void itemStateChanged(ItemEvent e) {

        Choice actions = (Choice)td.getComponent(td.PD_ACTIONS_CHOICE);
        actions.getAccessibleContext().setAccessibleName((String)e.getItem());

        if (((String)e.getItem()).indexOf(td.PERM_ACTIONS) != -1)
            return;

        TextField tf = (TextField)td.getComponent(td.PD_ACTIONS_TEXTFIELD);
        if (tf.getText() == null || tf.getText().equals("")) {
            tf.setText((String)e.getItem());
        } else {
            if (tf.getText().indexOf((String)e.getItem()) == -1)
                tf.setText(tf.getText() + ", " + (String)e.getItem());
        }
    }
}

/**
 * Event handler for all the children dialogs/windows
 */
class ChildWindowListener implements WindowListener {

    private ToolDialog td;

    ChildWindowListener(ToolDialog td) {
        this.td = td;
    }

    public void windowOpened(WindowEvent we) {
    }

    public void windowClosing(WindowEvent we) {
        // same as pressing the "cancel" button
        td.setVisible(false);
        td.dispose();
    }

    public void windowClosed(WindowEvent we) {
    }

    public void windowIconified(WindowEvent we) {
    }

    public void windowDeiconified(WindowEvent we) {
    }

    public void windowActivated(WindowEvent we) {
    }

    public void windowDeactivated(WindowEvent we) {
    }
}

/**
 * Event handler for CancelButton button
 */
class CancelButtonListener implements ActionListener {

    private ToolDialog td;

    CancelButtonListener(ToolDialog td) {
        this.td = td;
    }

    public void actionPerformed(ActionEvent e) {
        td.setVisible(false);
        td.dispose();
    }
}

/**
 * Event handler for ErrorOKButton button
 */
class ErrorOKButtonListener implements ActionListener {

    private ToolDialog ed;

    ErrorOKButtonListener(ToolDialog ed) {
        this.ed = ed;
    }

    public void actionPerformed(ActionEvent e) {
        ed.setVisible(false);
        ed.dispose();
    }
}

/**
 * Event handler for StatusOKButton button
 */
class StatusOKButtonListener implements ActionListener {

    private ToolDialog sd;

    StatusOKButtonListener(ToolDialog sd) {
        this.sd = sd;
    }

    public void actionPerformed(ActionEvent e) {
        sd.setVisible(false);
        sd.dispose();
    }
}

/**
 * Event handler for UserSaveYes button
 */
class UserSaveYesButtonListener implements ActionListener {

    private ToolDialog us;
    private PolicyTool tool;
    private ToolWindow tw;
    private int select;

    UserSaveYesButtonListener(ToolDialog us, PolicyTool tool,
                        ToolWindow tw, int select) {
        this.us = us;
        this.tool = tool;
        this.tw = tw;
        this.select = select;
    }

    public void actionPerformed(ActionEvent e) {

        // first get rid of the window
        us.setVisible(false);
        us.dispose();

        try {
            String filename = ((TextField)
                    tw.getComponent(tw.MW_FILENAME_TEXTFIELD)).getText();
            if (filename == null || filename.equals("")) {
                us.displaySaveAsDialog(select);

                // the above dialog will continue with the originally
                // requested command if necessary
            } else {
                // save the policy entries to a file
                tool.savePolicy(filename);

                // display status
                MessageFormat form = new MessageFormat
                        (PolicyTool.rb.getString
                        ("Policy successfully written to filename"));
                Object[] source = {filename};
                tw.displayStatusDialog(null, form.format(source));

                // now continue with the originally requested command
                // (QUIT, NEW, or OPEN)
                us.userSaveContinue(tool, tw, us, select);
            }
        } catch (Exception ee) {
            // error -- just report it and bail
            tw.displayErrorDialog(null, ee);
        }
    }
}

/**
 * Event handler for UserSaveNoButton
 */
class UserSaveNoButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog us;
    private int select;

    UserSaveNoButtonListener(ToolDialog us, PolicyTool tool,
                        ToolWindow tw, int select) {
        this.us = us;
        this.tool = tool;
        this.tw = tw;
        this.select = select;
    }

    public void actionPerformed(ActionEvent e) {
        us.setVisible(false);
        us.dispose();

        // now continue with the originally requested command
        // (QUIT, NEW, or OPEN)
        us.userSaveContinue(tool, tw, us, select);
    }
}

/**
 * Event handler for UserSaveCancelButton
 */
class UserSaveCancelButtonListener implements ActionListener {

    private ToolDialog us;

    UserSaveCancelButtonListener(ToolDialog us) {
        this.us = us;
    }

    public void actionPerformed(ActionEvent e) {
        us.setVisible(false);
        us.dispose();

        // do NOT continue with the originally requested command
        // (QUIT, NEW, or OPEN)
    }
}

/**
 * Event handler for ConfirmRemovePolicyEntryOKButtonListener
 */
class ConfirmRemovePolicyEntryOKButtonListener implements ActionListener {

    private PolicyTool tool;
    private ToolWindow tw;
    private ToolDialog us;

    ConfirmRemovePolicyEntryOKButtonListener(PolicyTool tool,
                                ToolWindow tw, ToolDialog us) {
        this.tool = tool;
        this.tw = tw;
        this.us = us;
    }

    public void actionPerformed(ActionEvent e) {
        // remove the entry
        List list = (List)tw.getComponent(tw.MW_POLICY_LIST);
        int index = list.getSelectedIndex();
        PolicyEntry entries[] = tool.getEntry();
        tool.removeEntry(entries[index]);

        // redraw the window listing
        list = new List(40, false);
        list.addActionListener(new PolicyListListener(tool, tw));
        entries = tool.getEntry();
        if (entries != null) {
                for (int i = 0; i < entries.length; i++)
                    list.add(entries[i].headerToString());
        }
        tw.replacePolicyList(list);
        us.setVisible(false);
        us.dispose();
    }
}

/**
 * Just a special name, so that the codes dealing with this exception knows
 * it's special, and does not pop out a warning box.
 */
class NoDisplayException extends RuntimeException {

}

/**
 * This is a java.awt.List that bind an Object to each String it holds.
 */
class TaggedList extends List {
    private java.util.List<Object> data = new LinkedList<Object>();
    public TaggedList(int i, boolean b) {
        super(i, b);
    }

    public Object getObject(int index) {
        return data.get(index);
    }

    @Override @Deprecated public void add(String string) {
        throw new AssertionError("should not call add in TaggedList");
    }
    public void addTaggedItem(String string, Object object) {
        super.add(string);
        data.add(object);
    }

    @Override @Deprecated public void replaceItem(String string, int index) {
        throw new AssertionError("should not call replaceItem in TaggedList");
    }
    public void replaceTaggedItem(String string, Object object, int index) {
        super.replaceItem(string, index);
        data.set(index, object);
    }

    @Override @Deprecated public void remove(int index) {
        // Cannot throw AssertionError, because replaceItem() call remove() internally
        super.remove(index);
    }
    public void removeTaggedItem(int index) {
        super.remove(index);
        data.remove(index);
    }
}

/**
 * Convenience Principal Classes
 */

class Prin {
    public final String CLASS;
    public final String FULL_CLASS;

    public Prin(String clazz, String fullClass) {
        this.CLASS = clazz;
        this.FULL_CLASS = fullClass;
    }
}

class KrbPrin extends Prin {
    public KrbPrin() {
        super("KerberosPrincipal",
                "javax.security.auth.kerberos.KerberosPrincipal");
    }
}

class X500Prin extends Prin {
    public X500Prin() {
        super("X500Principal",
                "javax.security.auth.x500.X500Principal");
    }
}

/**
 * Convenience Permission Classes
 */

class Perm {
    public final String CLASS;
    public final String FULL_CLASS;
    public final String[] TARGETS;
    public final String[] ACTIONS;

    public Perm(String clazz, String fullClass,
                String[] targets, String[] actions) {

        this.CLASS = clazz;
        this.FULL_CLASS = fullClass;
        this.TARGETS = targets;
        this.ACTIONS = actions;
    }
}

class AllPerm extends Perm {
    public AllPerm() {
        super("AllPermission", "java.security.AllPermission", null, null);
    }
}

class AudioPerm extends Perm {
    public AudioPerm() {
        super("AudioPermission",
        "javax.sound.sampled.AudioPermission",
        new String[]    {
                "play",
                "record"
                },
        null);
    }
}

class AuthPerm extends Perm {
    public AuthPerm() {
    super("AuthPermission",
        "javax.security.auth.AuthPermission",
        new String[]    {
                "doAs",
                "doAsPrivileged",
                "getSubject",
                "getSubjectFromDomainCombiner",
                "setReadOnly",
                "modifyPrincipals",
                "modifyPublicCredentials",
                "modifyPrivateCredentials",
                "refreshCredential",
                "destroyCredential",
                "createLoginContext.<" + PolicyTool.rb.getString("name") + ">",
                "getLoginConfiguration",
                "setLoginConfiguration",
                "createLoginConfiguration.<" +
                        PolicyTool.rb.getString("configuration type") + ">",
                "refreshLoginConfiguration"
                },
        null);
    }
}

class AWTPerm extends Perm {
    public AWTPerm() {
    super("AWTPermission",
        "java.awt.AWTPermission",
        new String[]    {
                "accessClipboard",
                "accessEventQueue",
                "accessSystemTray",
                "createRobot",
                "fullScreenExclusive",
                "listenToAllAWTEvents",
                "readDisplayPixels",
                "replaceKeyboardFocusManager",
                "setAppletStub",
                "setWindowAlwaysOnTop",
                "showWindowWithoutWarningBanner",
                "toolkitModality",
                "watchMousePointer"
        },
        null);
    }
}

class DelegationPerm extends Perm {
    public DelegationPerm() {
    super("DelegationPermission",
        "javax.security.auth.kerberos.DelegationPermission",
        new String[]    {
                // allow user input
                },
        null);
    }
}

class FilePerm extends Perm {
    public FilePerm() {
    super("FilePermission",
        "java.io.FilePermission",
        new String[]    {
                "<<ALL FILES>>"
                },
        new String[]    {
                "read",
                "write",
                "delete",
                "execute"
                });
    }
}

class InqSecContextPerm extends Perm {
    public InqSecContextPerm() {
    super("InquireSecContextPermission",
        "com.sun.security.jgss.InquireSecContextPermission",
        new String[]    {
                "KRB5_GET_SESSION_KEY",
                "KRB5_GET_TKT_FLAGS",
                "KRB5_GET_AUTHZ_DATA",
                "KRB5_GET_AUTHTIME"
                },
        null);
    }
}

class LogPerm extends Perm {
    public LogPerm() {
    super("LoggingPermission",
        "java.util.logging.LoggingPermission",
        new String[]    {
                "control"
                },
        null);
    }
}

class MgmtPerm extends Perm {
    public MgmtPerm() {
    super("ManagementPermission",
        "java.lang.management.ManagementPermission",
        new String[]    {
                "control",
                "monitor"
                },
        null);
    }
}

class MBeanPerm extends Perm {
    public MBeanPerm() {
    super("MBeanPermission",
        "javax.management.MBeanPermission",
        new String[]    {
                // allow user input
                },
        new String[]    {
                "addNotificationListener",
                "getAttribute",
                "getClassLoader",
                "getClassLoaderFor",
                "getClassLoaderRepository",
                "getDomains",
                "getMBeanInfo",
                "getObjectInstance",
                "instantiate",
                "invoke",
                "isInstanceOf",
                "queryMBeans",
                "queryNames",
                "registerMBean",
                "removeNotificationListener",
                "setAttribute",
                "unregisterMBean"
                });
    }
}

class MBeanSvrPerm extends Perm {
    public MBeanSvrPerm() {
    super("MBeanServerPermission",
        "javax.management.MBeanServerPermission",
        new String[]    {
                "createMBeanServer",
                "findMBeanServer",
                "newMBeanServer",
                "releaseMBeanServer"
                },
        null);
    }
}

class MBeanTrustPerm extends Perm {
    public MBeanTrustPerm() {
    super("MBeanTrustPermission",
        "javax.management.MBeanTrustPermission",
        new String[]    {
                "register"
                },
        null);
    }
}

class NetPerm extends Perm {
    public NetPerm() {
    super("NetPermission",
        "java.net.NetPermission",
        new String[]    {
                "setDefaultAuthenticator",
                "requestPasswordAuthentication",
                "specifyStreamHandler",
                "setProxySelector",
                "getProxySelector",
                "setCookieHandler",
                "getCookieHandler",
                "setResponseCache",
                "getResponseCache"
                },
        null);
    }
}

class PrivCredPerm extends Perm {
    public PrivCredPerm() {
    super("PrivateCredentialPermission",
        "javax.security.auth.PrivateCredentialPermission",
        new String[]    {
                // allow user input
                },
        new String[]    {
                "read"
                });
    }
}

class PropPerm extends Perm {
    public PropPerm() {
    super("PropertyPermission",
        "java.util.PropertyPermission",
        new String[]    {
                // allow user input
                },
        new String[]    {
                "read",
                "write"
                });
    }
}

class ReflectPerm extends Perm {
    public ReflectPerm() {
    super("ReflectPermission",
        "java.lang.reflect.ReflectPermission",
        new String[]    {
                "suppressAccessChecks"
                },
        null);
    }
}

class RuntimePerm extends Perm {
    public RuntimePerm() {
    super("RuntimePermission",
        "java.lang.RuntimePermission",
        new String[]    {
                "createClassLoader",
                "getClassLoader",
                "setContextClassLoader",
                "enableContextClassLoaderOverride",
                "setSecurityManager",
                "createSecurityManager",
                "getenv.<" +
                    PolicyTool.rb.getString("environment variable name") + ">",
                "exitVM",
                "shutdownHooks",
                "setFactory",
                "setIO",
                "modifyThread",
                "stopThread",
                "modifyThreadGroup",
                "getProtectionDomain",
                "readFileDescriptor",
                "writeFileDescriptor",
                "loadLibrary.<" +
                    PolicyTool.rb.getString("library name") + ">",
                "accessClassInPackage.<" +
                    PolicyTool.rb.getString("package name")+">",
                "defineClassInPackage.<" +
                    PolicyTool.rb.getString("package name")+">",
                "accessDeclaredMembers",
                "queuePrintJob",
                "getStackTrace",
                "setDefaultUncaughtExceptionHandler",
                "preferences",
                "usePolicy",
                // "inheritedChannel"
                },
        null);
    }
}

class SecurityPerm extends Perm {
    public SecurityPerm() {
    super("SecurityPermission",
        "java.security.SecurityPermission",
        new String[]    {
                "createAccessControlContext",
                "getDomainCombiner",
                "getPolicy",
                "setPolicy",
                "createPolicy.<" +
                    PolicyTool.rb.getString("policy type") + ">",
                "getProperty.<" +
                    PolicyTool.rb.getString("property name") + ">",
                "setProperty.<" +
                    PolicyTool.rb.getString("property name") + ">",
                "insertProvider.<" +
                    PolicyTool.rb.getString("provider name") + ">",
                "removeProvider.<" +
                    PolicyTool.rb.getString("provider name") + ">",
                //"setSystemScope",
                //"setIdentityPublicKey",
                //"setIdentityInfo",
                //"addIdentityCertificate",
                //"removeIdentityCertificate",
                //"printIdentity",
                "clearProviderProperties.<" +
                    PolicyTool.rb.getString("provider name") + ">",
                "putProviderProperty.<" +
                    PolicyTool.rb.getString("provider name") + ">",
                "removeProviderProperty.<" +
                    PolicyTool.rb.getString("provider name") + ">",
                //"getSignerPrivateKey",
                //"setSignerKeyPair"
                },
        null);
    }
}

class SerialPerm extends Perm {
    public SerialPerm() {
    super("SerializablePermission",
        "java.io.SerializablePermission",
        new String[]    {
                "enableSubclassImplementation",
                "enableSubstitution"
                },
        null);
    }
}

class ServicePerm extends Perm {
    public ServicePerm() {
    super("ServicePermission",
        "javax.security.auth.kerberos.ServicePermission",
        new String[]    {
                // allow user input
                },
        new String[]    {
                "initiate",
                "accept"
                });
    }
}

class SocketPerm extends Perm {
    public SocketPerm() {
    super("SocketPermission",
        "java.net.SocketPermission",
        new String[]    {
                // allow user input
                },
        new String[]    {
                "accept",
                "connect",
                "listen",
                "resolve"
                });
    }
}

class SQLPerm extends Perm {
    public SQLPerm() {
    super("SQLPermission",
        "java.sql.SQLPermission",
        new String[]    {
                "setLog"
                },
        null);
    }
}

class SSLPerm extends Perm {
    public SSLPerm() {
    super("SSLPermission",
        "javax.net.ssl.SSLPermission",
        new String[]    {
                "setHostnameVerifier",
                "getSSLSessionContext"
                },
        null);
    }
}

class SubjDelegPerm extends Perm {
    public SubjDelegPerm() {
    super("SubjectDelegationPermission",
        "javax.management.remote.SubjectDelegationPermission",
        new String[]    {
                // allow user input
                },
        null);
    }
}
