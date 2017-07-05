/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

import java.security.AccessController;
import java.security.CodeSource;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Permission;
import java.security.Permissions;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.UnresolvedPermission;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.security.auth.Subject;
import javax.security.auth.PrivateCredentialPermission;

import sun.security.provider.PolicyParser.GrantEntry;
import sun.security.provider.PolicyParser.PermissionEntry;
import sun.security.provider.PolicyParser.PrincipalEntry;
import sun.security.util.Debug;
import sun.security.util.PolicyUtil;
import sun.security.util.PropertyExpander;

/**
 * See {@code com.sun.security.auth.PolicyFile} for the class description.
 * This class is necessary in order to support a default
 * {@code javax.security.auth.Policy} implementation on the compact1 and
 * compact2 profiles.
 *
 * @deprecated As of JDK&nbsp;1.4, replaced by
 *             {@code sun.security.provider.PolicyFile}.
 *             This class is entirely deprecated.
 */
@Deprecated
public class AuthPolicyFile extends javax.security.auth.Policy {

    static final ResourceBundle rb =
        AccessController.doPrivileged(new PrivilegedAction<ResourceBundle>() {
            @Override public ResourceBundle run() {
                return (ResourceBundle.getBundle
                        ("sun.security.util.AuthResources"));
            }
        });

    private static final Debug debug = Debug.getInstance("policy",
                                                         "\t[Auth Policy]");

    private static final String AUTH_POLICY = "java.security.auth.policy";
    private static final String SECURITY_MANAGER = "java.security.manager";
    private static final String AUTH_POLICY_URL = "auth.policy.url.";

    private Vector<PolicyEntry> policyEntries;
    private Hashtable<Object, Object> aliasMapping;

    private boolean initialized = false;

    private boolean expandProperties = true;
    private boolean ignoreIdentityScope = true;

    // for use with the reflection API
    private static final Class<?>[] PARAMS = { String.class, String.class};

    /**
     * Initializes the Policy object and reads the default policy
     * configuration file(s) into the Policy object.
     */
    public AuthPolicyFile() {
        // initialize Policy if either the AUTH_POLICY or
        // SECURITY_MANAGER properties are set
        String prop = System.getProperty(AUTH_POLICY);

        if (prop == null) {
            prop = System.getProperty(SECURITY_MANAGER);
        }
        if (prop != null) {
            init();
        }
    }

    private synchronized void init() {
        if (initialized) {
            return;
        }

        policyEntries = new Vector<PolicyEntry>();
        aliasMapping = new Hashtable<Object, Object>(11);

        initPolicyFile();
        initialized = true;
    }

    @Override
    public synchronized void refresh() {

        java.lang.SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new javax.security.auth.AuthPermission
                                ("refreshPolicy"));
        }

        // XXX
        //
        // 1)   if code instantiates PolicyFile directly, then it will need
        //      all the permissions required for the PolicyFile initialization
        // 2)   if code calls Policy.getPolicy, then it simply needs
        //      AuthPermission(getPolicy), and the javax.security.auth.Policy
        //      implementation instantiates PolicyFile in a doPrivileged block
        // 3)   if after instantiating a Policy (either via #1 or #2),
        //      code calls refresh, it simply needs
        //      AuthPermission(refreshPolicy).  then PolicyFile wraps
        //      the refresh in a doPrivileged block.
        initialized = false;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override public Void run() {
                init();
                return null;
            }
        });
    }

    private KeyStore initKeyStore(URL policyUrl, String keyStoreName,
                                  String keyStoreType) {
        if (keyStoreName != null) {
            try {
                /*
                 * location of keystore is specified as absolute URL in policy
                 * file, or is relative to URL of policy file
                 */
                URL keyStoreUrl = null;
                try {
                    keyStoreUrl = new URL(keyStoreName);
                    // absolute URL
                } catch (java.net.MalformedURLException e) {
                    // relative URL
                    keyStoreUrl = new URL(policyUrl, keyStoreName);
                }

                if (debug != null) {
                    debug.println("reading keystore"+keyStoreUrl);
                }

                InputStream inStream = new BufferedInputStream(
                    PolicyUtil.getInputStream(keyStoreUrl));

                KeyStore ks;
                if (keyStoreType != null)
                    ks = KeyStore.getInstance(keyStoreType);
                else
                    ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(inStream, null);
                inStream.close();
                return ks;
            } catch (Exception e) {
                // ignore, treat it like we have no keystore
                if (debug != null) {
                    e.printStackTrace();
                }
                return null;
            }
        }
        return null;
    }

    private void initPolicyFile() {

        String prop = Security.getProperty("policy.expandProperties");
        if (prop != null) {
            expandProperties = prop.equalsIgnoreCase("true");
        }

        String iscp = Security.getProperty("policy.ignoreIdentityScope");
        if (iscp != null) {
            ignoreIdentityScope = iscp.equalsIgnoreCase("true");
        }

        String allowSys = Security.getProperty("policy.allowSystemProperty");
        if (allowSys != null && allowSys.equalsIgnoreCase("true")) {
            String extra_policy = System.getProperty(AUTH_POLICY);
            if (extra_policy != null) {
                boolean overrideAll = false;
                if (extra_policy.startsWith("=")) {
                    overrideAll = true;
                    extra_policy = extra_policy.substring(1);
                }
                try {
                    extra_policy = PropertyExpander.expand(extra_policy);
                    URL policyURL;
                    File policyFile = new File(extra_policy);
                    if (policyFile.exists()) {
                        policyURL =
                            new URL("file:" + policyFile.getCanonicalPath());
                    } else {
                        policyURL = new URL(extra_policy);
                    }
                    if (debug != null) {
                        debug.println("reading " + policyURL);
                    }
                    init(policyURL);
                } catch (Exception e) {
                    // ignore.
                    if (debug != null) {
                        debug.println("caught exception: " + e);
                    }

                }
                if (overrideAll) {
                    if (debug != null) {
                        debug.println("overriding other policies!");
                    }
                    return;
                }
            }
        }

        int n = 1;
        boolean loaded_one = false;
        String policy_url;

        while ((policy_url = Security.getProperty(AUTH_POLICY_URL+n)) != null) {
            try {
                policy_url = PropertyExpander.expand(policy_url).replace
                                                (File.separatorChar, '/');
                if (debug != null) {
                    debug.println("reading " + policy_url);
                }
                init(new URL(policy_url));
                loaded_one = true;
            } catch (Exception e) {
                if (debug != null) {
                    debug.println("error reading policy " + e);
                    e.printStackTrace();
                }
                // ignore that policy
            }
            n++;
        }

        if (loaded_one == false) {
            // do not load a static policy
        }
    }

    /**
     * Checks public key. If it is marked as trusted in
     * the identity database, add it to the policy
     * with the AllPermission.
     */
    private boolean checkForTrustedIdentity(final Certificate cert) {
        return false;
    }

    /**
     * Reads a policy configuration into the Policy object using a
     * Reader object.
     *
     * @param policyFile the policy Reader object.
     */
    private void init(URL policy) {
        PolicyParser pp = new PolicyParser(expandProperties);
        try (InputStreamReader isr
                = new InputStreamReader(PolicyUtil.getInputStream(policy))) {
            pp.read(isr);
            KeyStore keyStore = initKeyStore(policy, pp.getKeyStoreUrl(),
                                             pp.getKeyStoreType());
            Enumeration<GrantEntry> enum_ = pp.grantElements();
            while (enum_.hasMoreElements()) {
                GrantEntry ge = enum_.nextElement();
                addGrantEntry(ge, keyStore);
            }
        } catch (PolicyParser.ParsingException pe) {
            System.err.println(AUTH_POLICY +
                               rb.getString(".error.parsing.") + policy);
            System.err.println(AUTH_POLICY + rb.getString("COLON") +
                               pe.getMessage());
            if (debug != null) {
                pe.printStackTrace();
            }
        } catch (Exception e) {
            if (debug != null) {
                debug.println("error parsing " + policy);
                debug.println(e.toString());
                e.printStackTrace();
            }
        }
    }

    /**
     * Given a PermissionEntry, create a codeSource.
     *
     * @return null if signedBy alias is not recognized
     */
    CodeSource getCodeSource(GrantEntry ge, KeyStore keyStore)
            throws java.net.MalformedURLException
    {
        Certificate[] certs = null;
        if (ge.signedBy != null) {
            certs = getCertificates(keyStore, ge.signedBy);
            if (certs == null) {
                // we don't have a key for this alias,
                // just return
                if (debug != null) {
                    debug.println(" no certs for alias " +
                                       ge.signedBy + ", ignoring.");
                }
                return null;
            }
        }

        URL location;
        if (ge.codeBase != null) {
            location = new URL(ge.codeBase);
        } else {
            location = null;
        }

        if (ge.principals == null || ge.principals.size() == 0) {
            return (canonicalizeCodebase
                        (new CodeSource(location, certs),
                        false));
        } else {
            return (canonicalizeCodebase
                (new SubjectCodeSource(null, ge.principals, location, certs),
                false));
        }
    }

    /**
     * Add one policy entry to the vector.
     */
    private void addGrantEntry(GrantEntry ge, KeyStore keyStore) {

        if (debug != null) {
            debug.println("Adding policy entry: ");
            debug.println("  signedBy " + ge.signedBy);
            debug.println("  codeBase " + ge.codeBase);
            if (ge.principals != null) {
                for (PrincipalEntry pppe : ge.principals) {
                    debug.println("  " + pppe.getPrincipalClass() +
                                        " " + pppe.getPrincipalName());
                }
            }
            debug.println();
        }

        try {
            CodeSource codesource = getCodeSource(ge, keyStore);
            // skip if signedBy alias was unknown...
            if (codesource == null) return;

            PolicyEntry entry = new PolicyEntry(codesource);
            Enumeration<PermissionEntry> enum_ = ge.permissionElements();
            while (enum_.hasMoreElements()) {
                PermissionEntry pe = enum_.nextElement();
                try {
                    // XXX special case PrivateCredentialPermission-SELF
                    Permission perm;
                    if (pe.permission.equals
                        ("javax.security.auth.PrivateCredentialPermission") &&
                        pe.name.endsWith(" self")) {
                        perm = getInstance(pe.permission,
                                         pe.name + " \"self\"",
                                         pe.action);
                    } else {
                        perm = getInstance(pe.permission,
                                         pe.name,
                                         pe.action);
                    }
                    entry.add(perm);
                    if (debug != null) {
                        debug.println("  "+perm);
                    }
                } catch (ClassNotFoundException cnfe) {
                    Certificate[] certs;
                    if (pe.signedBy != null) {
                        certs = getCertificates(keyStore, pe.signedBy);
                    } else {
                        certs = null;
                    }

                    // only add if we had no signer or we had a
                    // a signer and found the keys for it.
                    if (certs != null || pe.signedBy == null) {
                            Permission perm = new UnresolvedPermission(
                                             pe.permission,
                                             pe.name,
                                             pe.action,
                                             certs);
                            entry.add(perm);
                            if (debug != null) {
                                debug.println("  "+perm);
                            }
                    }
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    System.err.println
                        (AUTH_POLICY +
                        rb.getString(".error.adding.Permission.") +
                        pe.permission +
                        rb.getString("SPACE") +
                        ite.getTargetException());
                } catch (Exception e) {
                    System.err.println
                        (AUTH_POLICY +
                        rb.getString(".error.adding.Permission.") +
                        pe.permission +
                        rb.getString("SPACE") +
                        e);
                }
            }
            policyEntries.addElement(entry);
        } catch (Exception e) {
            System.err.println
                (AUTH_POLICY +
                rb.getString(".error.adding.Entry.") +
                ge +
                rb.getString("SPACE") +
                e);
        }

        if (debug != null) {
            debug.println();
        }
    }

    /**
     * Returns a new Permission object of the given Type. The Permission is
     * created by getting the
     * Class object using the <code>Class.forName</code> method, and using
     * the reflection API to invoke the (String name, String actions)
     * constructor on the
     * object.
     *
     * @param type the type of Permission being created.
     * @param name the name of the Permission being created.
     * @param actions the actions of the Permission being created.
     *
     * @exception  ClassNotFoundException  if the particular Permission
     *             class could not be found.
     *
     * @exception  IllegalAccessException  if the class or initializer is
     *               not accessible.
     *
     * @exception  InstantiationException  if getInstance tries to
     *               instantiate an abstract class or an interface, or if the
     *               instantiation fails for some other reason.
     *
     * @exception  NoSuchMethodException if the (String, String) constructor
     *               is not found.
     *
     * @exception  InvocationTargetException if the underlying Permission
     *               constructor throws an exception.
     *
     */
    private static final Permission getInstance(String type,
                                    String name,
                                    String actions)
        throws ClassNotFoundException,
               InstantiationException,
               IllegalAccessException,
               NoSuchMethodException,
               InvocationTargetException
    {
        //XXX we might want to keep a hash of created factories...
        Class<?> pc = Class.forName(type);
        Constructor<?> c = pc.getConstructor(PARAMS);
        return (Permission) c.newInstance(new Object[] { name, actions });
    }

    /**
     * Fetch all certs associated with this alias.
     */
    Certificate[] getCertificates(KeyStore keyStore, String aliases) {

        Vector<Certificate> vcerts = null;

        StringTokenizer st = new StringTokenizer(aliases, ",");
        int n = 0;

        while (st.hasMoreTokens()) {
            String alias = st.nextToken().trim();
            n++;
            Certificate cert = null;
            // See if this alias's cert has already been cached
            cert = (Certificate) aliasMapping.get(alias);
            if (cert == null && keyStore != null) {

                try {
                    cert = keyStore.getCertificate(alias);
                } catch (KeyStoreException kse) {
                    // never happens, because keystore has already been loaded
                    // when we call this
                }
                if (cert != null) {
                    aliasMapping.put(alias, cert);
                    aliasMapping.put(cert, alias);
                }
            }

            if (cert != null) {
                if (vcerts == null) {
                    vcerts = new Vector<Certificate>();
                }
                vcerts.addElement(cert);
            }
        }

        // make sure n == vcerts.size, since we are doing a logical *and*
        if (vcerts != null && n == vcerts.size()) {
            Certificate[] certs = new Certificate[vcerts.size()];
            vcerts.copyInto(certs);
            return certs;
        } else {
            return null;
        }
    }

    /**
     * Enumerate all the entries in the global policy object.
     * This method is used by policy admin tools.   The tools
     * should use the Enumeration methods on the returned object
     * to fetch the elements sequentially.
     */
    private final synchronized Enumeration<PolicyEntry> elements() {
        return policyEntries.elements();
    }

    @Override
    public PermissionCollection getPermissions(final Subject subject,
                                               final CodeSource codesource) {

        // 1)   if code instantiates PolicyFile directly, then it will need
        //      all the permissions required for the PolicyFile initialization
        // 2)   if code calls Policy.getPolicy, then it simply needs
        //      AuthPermission(getPolicy), and the javax.security.auth.Policy
        //      implementation instantiates PolicyFile in a doPrivileged block
        // 3)   if after instantiating a Policy (either via #1 or #2),
        //      code calls getPermissions, PolicyFile wraps the call
        //      in a doPrivileged block.
        return AccessController.doPrivileged
            (new PrivilegedAction<PermissionCollection>() {
            @Override public PermissionCollection run() {
                SubjectCodeSource scs = new SubjectCodeSource(
                    subject, null,
                    codesource == null ? null : codesource.getLocation(),
                    codesource == null ? null : codesource.getCertificates());
                if (initialized) {
                    return getPermissions(new Permissions(), scs);
                } else {
                    return new PolicyPermissions(AuthPolicyFile.this, scs);
                }
            }
        });
    }

    /**
     * Examines the global policy for the specified CodeSource, and
     * creates a PermissionCollection object with
     * the set of permissions for that principal's protection domain.
     *
     * @param CodeSource the codesource associated with the caller.
     * This encapsulates the original location of the code (where the code
     * came from) and the public key(s) of its signer.
     *
     * @return the set of permissions according to the policy.
     */
    PermissionCollection getPermissions(CodeSource codesource) {

        if (initialized) {
            return getPermissions(new Permissions(), codesource);
        } else {
            return new PolicyPermissions(this, codesource);
        }
    }

    /**
     * Examines the global policy for the specified CodeSource, and
     * creates a PermissionCollection object with
     * the set of permissions for that principal's protection domain.
     *
     * @param permissions the permissions to populate
     * @param codesource the codesource associated with the caller.
     * This encapsulates the original location of the code (where the code
     * came from) and the public key(s) of its signer.
     *
     * @return the set of permissions according to the policy.
     */
    Permissions getPermissions(final Permissions perms,
                               final CodeSource cs)
    {
        if (!initialized) {
            init();
        }

        final CodeSource[] codesource = {null};

        codesource[0] = canonicalizeCodebase(cs, true);

        if (debug != null) {
            debug.println("evaluate(" + codesource[0] + ")\n");
        }

        // needs to be in a begin/endPrivileged block because
        // codesource.implies calls URL.equals which does an
        // InetAddress lookup

        for (int i = 0; i < policyEntries.size(); i++) {

           PolicyEntry entry = policyEntries.elementAt(i);

           if (debug != null) {
                debug.println("PolicyFile CodeSource implies: " +
                              entry.codesource.toString() + "\n\n" +
                              "\t" + codesource[0].toString() + "\n\n");
           }

           if (entry.codesource.implies(codesource[0])) {
               for (int j = 0; j < entry.permissions.size(); j++) {
                    Permission p = entry.permissions.elementAt(j);
                    if (debug != null) {
                        debug.println("  granting " + p);
                    }
                    if (!addSelfPermissions(p, entry.codesource,
                                            codesource[0], perms)) {
                        // we could check for duplicates
                        // before adding new permissions,
                        // but the SubjectDomainCombiner
                        // already checks for duplicates later
                        perms.add(p);
                    }
                }
            }
        }

        // now see if any of the keys are trusted ids.

        if (!ignoreIdentityScope) {
            Certificate[] certs = codesource[0].getCertificates();
            if (certs != null) {
                for (int k=0; k < certs.length; k++) {
                    if (aliasMapping.get(certs[k]) == null &&
                        checkForTrustedIdentity(certs[k])) {
                        // checkForTrustedIdentity added it
                        // to the policy for us. next time
                        // around we'll find it. This time
                        // around we need to add it.
                        perms.add(new java.security.AllPermission());
                    }
                }
            }
        }
        return perms;
    }

    /**
     * Returns true if 'Self' permissions were added to the provided
     * 'perms', and false otherwise.
     *
     * <p>
     *
     * @param p check to see if this Permission is a "SELF"
     *                  PrivateCredentialPermission. <p>
     *
     * @param entryCs the codesource for the Policy entry.
     *
     * @param accCs the codesource for from the current AccessControlContext.
     *
     * @param perms the PermissionCollection where the individual
     *                  PrivateCredentialPermissions will be added.
     */
    private boolean addSelfPermissions(final Permission p,
                                       CodeSource entryCs,
                                       CodeSource accCs,
                                       Permissions perms) {

        if (!(p instanceof PrivateCredentialPermission)) {
            return false;
        }

        if (!(entryCs instanceof SubjectCodeSource)) {
            return false;
        }

        PrivateCredentialPermission pcp = (PrivateCredentialPermission)p;
        SubjectCodeSource scs = (SubjectCodeSource)entryCs;

        // see if it is a SELF permission
        String[][] pPrincipals = pcp.getPrincipals();
        if (pPrincipals.length <= 0 ||
            !pPrincipals[0][0].equalsIgnoreCase("self") ||
            !pPrincipals[0][1].equalsIgnoreCase("self")) {

            // regular PrivateCredentialPermission
            return false;
        } else {

            // granted a SELF permission - create a
            // PrivateCredentialPermission for each
            // of the Policy entry's CodeSource Principals

            if (scs.getPrincipals() == null) {
                // XXX SubjectCodeSource has no Subject???
                return true;
            }

            for (PrincipalEntry principal : scs.getPrincipals()) {

                //      if the Policy entry's Principal does not contain a
                //              WILDCARD for the Principal name, then a
                //              new PrivateCredentialPermission is created
                //              for the Principal listed in the Policy entry.
                //      if the Policy entry's Principal contains a WILDCARD
                //              for the Principal name, then a new
                //              PrivateCredentialPermission is created
                //              for each Principal associated with the Subject
                //              in the current ACC.

                String[][] principalInfo = getPrincipalInfo(principal, accCs);

                for (int i = 0; i < principalInfo.length; i++) {

                    // here's the new PrivateCredentialPermission

                    PrivateCredentialPermission newPcp =
                        new PrivateCredentialPermission
                                (pcp.getCredentialClass() +
                                        " " +
                                        principalInfo[i][0] +
                                        " " +
                                        "\"" + principalInfo[i][1] + "\"",
                                "read");

                    if (debug != null) {
                        debug.println("adding SELF permission: " +
                                        newPcp.toString());
                    }

                    perms.add(newPcp);
                }
            }
        }
        return true;
    }

    /**
     * return the principal class/name pair in the 2D array.
     * array[x][y]:     x corresponds to the array length.
     *                  if (y == 0), it's the principal class.
     *                  if (y == 1), it's the principal name.
     */
    private String[][] getPrincipalInfo(PrincipalEntry principal,
                                        final CodeSource accCs) {

        // there are 3 possibilities:
        // 1) the entry's Principal class and name are not wildcarded
        // 2) the entry's Principal name is wildcarded only
        // 3) the entry's Principal class and name are wildcarded

        if (!principal.getPrincipalClass().equals
                (PrincipalEntry.WILDCARD_CLASS) &&
            !principal.getPrincipalName().equals
                (PrincipalEntry.WILDCARD_NAME)) {

            // build a PrivateCredentialPermission for the principal
            // from the Policy entry
            String[][] info = new String[1][2];
            info[0][0] = principal.getPrincipalClass();
            info[0][1] = principal.getPrincipalName();
            return info;

        } else if (!principal.getPrincipalClass().equals
                (PrincipalEntry.WILDCARD_CLASS) &&
            principal.getPrincipalName().equals
                (PrincipalEntry.WILDCARD_NAME)) {

            // build a PrivateCredentialPermission for all
            // the Subject's principals that are instances of principalClass

            // the accCs is guaranteed to be a SubjectCodeSource
            // because the earlier CodeSource.implies succeeded
            SubjectCodeSource scs = (SubjectCodeSource)accCs;

            Set<? extends Principal> principalSet = null;
            try {
                // principal.principalClass should extend Principal
                // If it doesn't, we should stop here with a ClassCastException.
                @SuppressWarnings("unchecked")
                Class<? extends Principal> pClass = (Class<? extends Principal>)
                        Class.forName(principal.getPrincipalClass(), false,
                                      ClassLoader.getSystemClassLoader());
                principalSet = scs.getSubject().getPrincipals(pClass);
            } catch (Exception e) {
                if (debug != null) {
                    debug.println("problem finding Principal Class " +
                                  "when expanding SELF permission: " +
                                  e.toString());
                }
            }

            if (principalSet == null) {
                // error
                return new String[0][0];
            }

            String[][] info = new String[principalSet.size()][2];

            int i = 0;
            for (Principal p : principalSet) {
                info[i][0] = p.getClass().getName();
                info[i][1] = p.getName();
                i++;
            }
            return info;

        } else {

            // build a PrivateCredentialPermission for every
            // one of the current Subject's principals

            // the accCs is guaranteed to be a SubjectCodeSource
            // because the earlier CodeSource.implies succeeded
            SubjectCodeSource scs = (SubjectCodeSource)accCs;
            Set<Principal> principalSet = scs.getSubject().getPrincipals();

            String[][] info = new String[principalSet.size()][2];

            int i = 0;
            for (Principal p : principalSet) {
                info[i][0] = p.getClass().getName();
                info[i][1] = p.getName();
                i++;
            }
            return info;
        }
    }

    /*
     * Returns the signer certificates from the list of certificates associated
     * with the given code source.
     *
     * The signer certificates are those certificates that were used to verify
     * signed code originating from the codesource location.
     *
     * This method assumes that in the given code source, each signer
     * certificate is followed by its supporting certificate chain
     * (which may be empty), and that the signer certificate and its
     * supporting certificate chain are ordered bottom-to-top (i.e., with the
     * signer certificate first and the (root) certificate authority last).
     */
    Certificate[] getSignerCertificates(CodeSource cs) {
        Certificate[] certs = null;
        if ((certs = cs.getCertificates()) == null) {
            return null;
        }
        for (int i = 0; i < certs.length; i++) {
            if (!(certs[i] instanceof X509Certificate))
                return cs.getCertificates();
        }

        // Do we have to do anything?
        int i = 0;
        int count = 0;
        while (i < certs.length) {
            count++;
            while (((i+1) < certs.length)
                   && ((X509Certificate)certs[i]).getIssuerDN().equals(
                           ((X509Certificate)certs[i+1]).getSubjectDN())) {
                i++;
            }
            i++;
        }
        if (count == certs.length) {
            // Done
            return certs;
        }

        ArrayList<Certificate> userCertList = new ArrayList<>();
        i = 0;
        while (i < certs.length) {
            userCertList.add(certs[i]);
            while (((i+1) < certs.length)
                   && ((X509Certificate)certs[i]).getIssuerDN().equals(
                           ((X509Certificate)certs[i+1]).getSubjectDN())) {
                i++;
            }
            i++;
        }
        Certificate[] userCerts = new Certificate[userCertList.size()];
        userCertList.toArray(userCerts);
        return userCerts;
    }

    private CodeSource canonicalizeCodebase(CodeSource cs,
                                            boolean extractSignerCerts) {
        CodeSource canonCs = cs;
        if (cs.getLocation() != null &&
            cs.getLocation().getProtocol().equalsIgnoreCase("file")) {
            try {
                String path = cs.getLocation().getFile().replace
                                                        ('/',
                                                        File.separatorChar);
                URL csUrl = null;
                if (path.endsWith("*")) {
                    // remove trailing '*' because it causes canonicalization
                    // to fail on win32
                    path = path.substring(0, path.length()-1);
                    boolean appendFileSep = false;
                    if (path.endsWith(File.separator)) {
                        appendFileSep = true;
                    }
                    if (path.equals("")) {
                        path = System.getProperty("user.dir");
                    }
                    File f = new File(path);
                    path = f.getCanonicalPath();
                    StringBuilder sb = new StringBuilder(path);
                    // reappend '*' to canonicalized filename (note that
                    // canonicalization may have removed trailing file
                    // separator, so we have to check for that, too)
                    if (!path.endsWith(File.separator) &&
                        (appendFileSep || f.isDirectory())) {
                        sb.append(File.separatorChar);
                    }
                    sb.append('*');
                    path = sb.toString();
                } else {
                    path = new File(path).getCanonicalPath();
                }
                csUrl = new File(path).toURL();

                if (cs instanceof SubjectCodeSource) {
                    SubjectCodeSource scs = (SubjectCodeSource)cs;
                    if (extractSignerCerts) {
                        canonCs = new SubjectCodeSource(scs.getSubject(),
                                                        scs.getPrincipals(),
                                                        csUrl,
                                                        getSignerCertificates(scs));
                    } else {
                        canonCs = new SubjectCodeSource(scs.getSubject(),
                                                        scs.getPrincipals(),
                                                        csUrl,
                                                        scs.getCertificates());
                    }
                } else {
                    if (extractSignerCerts) {
                        canonCs = new CodeSource(csUrl,
                                                 getSignerCertificates(cs));
                    } else {
                        canonCs = new CodeSource(csUrl,
                                                 cs.getCertificates());
                    }
                }
            } catch (IOException ioe) {
                // leave codesource as it is, unless we have to extract its
                // signer certificates
                if (extractSignerCerts) {
                    if (!(cs instanceof SubjectCodeSource)) {
                        canonCs = new CodeSource(cs.getLocation(),
                                                getSignerCertificates(cs));
                    } else {
                        SubjectCodeSource scs = (SubjectCodeSource)cs;
                        canonCs = new SubjectCodeSource(scs.getSubject(),
                                                scs.getPrincipals(),
                                                scs.getLocation(),
                                                getSignerCertificates(scs));
                    }
                }
            }
        } else {
            if (extractSignerCerts) {
                if (!(cs instanceof SubjectCodeSource)) {
                    canonCs = new CodeSource(cs.getLocation(),
                                        getSignerCertificates(cs));
                } else {
                    SubjectCodeSource scs = (SubjectCodeSource)cs;
                    canonCs = new SubjectCodeSource(scs.getSubject(),
                                        scs.getPrincipals(),
                                        scs.getLocation(),
                                        getSignerCertificates(scs));
                }
            }
        }
        return canonCs;
    }

    /**
     * Each entry in the policy configuration file is represented by a
     * PolicyEntry object.  <p>
     *
     * A PolicyEntry is a (CodeSource,Permission) pair.  The
     * CodeSource contains the (URL, PublicKey) that together identify
     * where the Java bytecodes come from and who (if anyone) signed
     * them.  The URL could refer to localhost.  The URL could also be
     * null, meaning that this policy entry is given to all comers, as
     * long as they match the signer field.  The signer could be null,
     * meaning the code is not signed. <p>
     *
     * The Permission contains the (Type, Name, Action) triplet. <p>
     *
     * For now, the Policy object retrieves the public key from the
     * X.509 certificate on disk that corresponds to the signedBy
     * alias specified in the Policy config file.  For reasons of
     * efficiency, the Policy object keeps a hashtable of certs already
     * read in.  This could be replaced by a secure internal key
     * store.
     *
     * <p>
     * For example, the entry
     * <pre>
     *          permission java.io.File "/tmp", "read,write",
     *          signedBy "Duke";
     * </pre>
     * is represented internally
     * <pre>
     *
     * FilePermission f = new FilePermission("/tmp", "read,write");
     * PublicKey p = publickeys.get("Duke");
     * URL u = InetAddress.getLocalHost();
     * CodeBase c = new CodeBase( p, u );
     * pe = new PolicyEntry(f, c);
     * </pre>
     *
     * @author Marianne Mueller
     * @author Roland Schemers
     * @see java.security.CodeSource
     * @see java.security.Policy
     * @see java.security.Permissions
     * @see java.security.ProtectionDomain
     */
    private static class PolicyEntry {

        CodeSource codesource;
        Vector<Permission> permissions;

        /**
         * Given a Permission and a CodeSource, create a policy entry.
         *
         * XXX Decide if/how to add validity fields and "purpose" fields to
         * XXX policy entries
         *
         * @param cs the CodeSource, which encapsulates the URL and the public
         *        key attributes from the policy config file. Validity checks
         *        are performed on the public key before PolicyEntry is called.
         *
         */
        PolicyEntry(CodeSource cs) {
            this.codesource = cs;
            this.permissions = new Vector<Permission>();
        }

        /**
         * add a Permission object to this entry.
         */
        void add(Permission p) {
            permissions.addElement(p);
        }

        /**
         * Return the CodeSource for this policy entry
         */
        CodeSource getCodeSource() {
            return this.codesource;
        }

        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder();
            sb.append(rb.getString("LPARAM"));
            sb.append(getCodeSource());
            sb.append("\n");
            for (int j = 0; j < permissions.size(); j++) {
                Permission p = permissions.elementAt(j);
                sb.append(rb.getString("SPACE"));
                sb.append(rb.getString("SPACE"));
                sb.append(p);
                sb.append(rb.getString("NEWLINE"));
            }
            sb.append(rb.getString("RPARAM"));
            sb.append(rb.getString("NEWLINE"));
            return sb.toString();
        }

    }
}

@SuppressWarnings("deprecation")
class PolicyPermissions extends PermissionCollection {

    private static final long serialVersionUID = -1954188373270545523L;

    private CodeSource codesource;
    private Permissions perms;
    private AuthPolicyFile policy;
    private boolean notInit; // have we pulled in the policy permissions yet?
    private Vector<Permission> additionalPerms;

    PolicyPermissions(AuthPolicyFile policy,
                      CodeSource codesource)
    {
        this.codesource = codesource;
        this.policy = policy;
        this.perms = null;
        this.notInit = true;
        this.additionalPerms = null;
    }

    @Override
    public void add(Permission permission) {
        if (isReadOnly())
            throw new SecurityException
            (AuthPolicyFile.rb.getString
            ("attempt.to.add.a.Permission.to.a.readonly.PermissionCollection"));

        if (perms == null) {
            if (additionalPerms == null) {
                additionalPerms = new Vector<Permission>();
            }
            additionalPerms.add(permission);
        } else {
            perms.add(permission);
        }
    }

    private synchronized void init() {
        if (notInit) {
            if (perms == null) {
                perms = new Permissions();
            }
            if (additionalPerms != null) {
                Enumeration<Permission> e = additionalPerms.elements();
                while (e.hasMoreElements()) {
                    perms.add(e.nextElement());
                }
                additionalPerms = null;
            }
            policy.getPermissions(perms, codesource);
            notInit = false;
        }
    }

    @Override
    public boolean implies(Permission permission) {
        if (notInit) {
            init();
        }
        return perms.implies(permission);
    }

    @Override
    public Enumeration<Permission> elements() {
        if (notInit) {
            init();
        }
        return perms.elements();
    }

    @Override
    public String toString() {
        if (notInit) {
            init();
        }
        return perms.toString();
    }
}
