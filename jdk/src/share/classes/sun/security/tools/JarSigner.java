/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.tools;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Collator;
import java.text.MessageFormat;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.*;
import java.lang.reflect.Constructor;

import com.sun.jarsigner.ContentSigner;
import com.sun.jarsigner.ContentSignerParameters;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.Map.Entry;
import sun.security.x509.*;
import sun.security.util.*;
import sun.misc.BASE64Encoder;


/**
 * <p>The jarsigner utility.
 *
 * The exit codes for the main method are:
 *
 * 0: success
 * 1: any error that the jar cannot be signed or verified, including:
 *      keystore loading error
 *      TSP communciation error
 *      jarsigner command line error...
 * otherwise: error codes from -strict
 *
 * @author Roland Schemers
 * @author Jan Luehe
 */

public class JarSigner {

    // for i18n
    private static final java.util.ResourceBundle rb =
        java.util.ResourceBundle.getBundle
        ("sun.security.tools.JarSignerResources");
    private static final Collator collator = Collator.getInstance();
    static {
        // this is for case insensitive string comparisions
        collator.setStrength(Collator.PRIMARY);
    }

    private static final String META_INF = "META-INF/";

    // prefix for new signature-related files in META-INF directory
    private static final String SIG_PREFIX = META_INF + "SIG-";

    private static final Class[] PARAM_STRING = { String.class };

    private static final String NONE = "NONE";
    private static final String P11KEYSTORE = "PKCS11";

    private static final long SIX_MONTHS = 180*24*60*60*1000L; //milliseconds

    // Attention:
    // This is the entry that get launched by the security tool jarsigner.
    public static void main(String args[]) throws Exception {
        JarSigner js = new JarSigner();
        js.run(args);
    }

    static final String VERSION = "1.0";

    static final int IN_KEYSTORE = 0x01;        // signer is in keystore
    static final int IN_SCOPE = 0x02;
    static final int NOT_ALIAS = 0x04;          // alias list is NOT empty and
                                                // signer is not in alias list
    static final int SIGNED_BY_ALIAS = 0x08;    // signer is in alias list

    X509Certificate[] certChain;    // signer's cert chain (when composing)
    PrivateKey privateKey;          // private key
    KeyStore store;                 // the keystore specified by -keystore
                                    // or the default keystore, never null

    IdentityScope scope;

    String keystore; // key store file
    boolean nullStream = false; // null keystore input stream (NONE)
    boolean token = false; // token-based keystore
    String jarfile;  // jar file to sign or verify
    String alias;    // alias to sign jar with
    List<String> ckaliases = new ArrayList<String>(); // aliases in -verify
    char[] storepass; // keystore password
    boolean protectedPath; // protected authentication path
    String storetype; // keystore type
    String providerName; // provider name
    Vector<String> providers = null; // list of providers
    // arguments for provider constructors
    HashMap<String,String> providerArgs = new HashMap<String, String>();
    char[] keypass; // private key password
    String sigfile; // name of .SF file
    String sigalg; // name of signature algorithm
    String digestalg = "SHA-256"; // name of digest algorithm
    String signedjar; // output filename
    String tsaUrl; // location of the Timestamping Authority
    String tsaAlias; // alias for the Timestamping Authority's certificate
    String altCertChain; // file to read alternative cert chain from
    boolean verify = false; // verify the jar
    String verbose = null; // verbose output when signing/verifying
    boolean showcerts = false; // show certs when verifying
    boolean debug = false; // debug
    boolean signManifest = true; // "sign" the whole manifest
    boolean externalSF = true; // leave the .SF out of the PKCS7 block
    boolean strict = false;  // treat warnings as error

    // read zip entry raw bytes
    private ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
    private byte[] buffer = new byte[8192];
    private ContentSigner signingMechanism = null;
    private String altSignerClass = null;
    private String altSignerClasspath = null;
    private ZipFile zipFile = null;

    private boolean hasExpiredCert = false;
    private boolean hasExpiringCert = false;
    private boolean notYetValidCert = false;
    private boolean chainNotValidated = false;
    private boolean notSignedByAlias = false;
    private boolean aliasNotInStore = false;
    private boolean hasUnsignedEntry = false;
    private boolean badKeyUsage = false;
    private boolean badExtendedKeyUsage = false;
    private boolean badNetscapeCertType = false;

    CertificateFactory certificateFactory;
    CertPathValidator validator;
    PKIXParameters pkixParameters;

    public void run(String args[]) {
        try {
            parseArgs(args);

            // Try to load and install the specified providers
            if (providers != null) {
                ClassLoader cl = ClassLoader.getSystemClassLoader();
                Enumeration<String> e = providers.elements();
                while (e.hasMoreElements()) {
                    String provName = e.nextElement();
                    Class<?> provClass;
                    if (cl != null) {
                        provClass = cl.loadClass(provName);
                    } else {
                        provClass = Class.forName(provName);
                    }

                    String provArg = providerArgs.get(provName);
                    Object obj;
                    if (provArg == null) {
                        obj = provClass.newInstance();
                    } else {
                        Constructor<?> c =
                                provClass.getConstructor(PARAM_STRING);
                        obj = c.newInstance(provArg);
                    }

                    if (!(obj instanceof Provider)) {
                        MessageFormat form = new MessageFormat(rb.getString
                            ("provName not a provider"));
                        Object[] source = {provName};
                        throw new Exception(form.format(source));
                    }
                    Security.addProvider((Provider)obj);
                }
            }

            if (verify) {
                try {
                    loadKeyStore(keystore, false);
                    scope = IdentityScope.getSystemScope();
                } catch (Exception e) {
                    if ((keystore != null) || (storepass != null)) {
                        System.out.println(rb.getString("jarsigner error: ") +
                                        e.getMessage());
                        System.exit(1);
                    }
                }
                /*              if (debug) {
                    SignatureFileVerifier.setDebug(true);
                    ManifestEntryVerifier.setDebug(true);
                }
                */
                verifyJar(jarfile);
            } else {
                loadKeyStore(keystore, true);
                getAliasInfo(alias);

                // load the alternative signing mechanism
                if (altSignerClass != null) {
                    signingMechanism = loadSigningMechanism(altSignerClass,
                        altSignerClasspath);
                }
                signJar(jarfile, alias, args);
            }
        } catch (Exception e) {
            System.out.println(rb.getString("jarsigner error: ") + e);
            if (debug) {
                e.printStackTrace();
            }
            System.exit(1);
        } finally {
            // zero-out private key password
            if (keypass != null) {
                Arrays.fill(keypass, ' ');
                keypass = null;
            }
            // zero-out keystore password
            if (storepass != null) {
                Arrays.fill(storepass, ' ');
                storepass = null;
            }
        }

        if (strict) {
            int exitCode = 0;
            if (hasExpiringCert) {
                exitCode |= 2;
            }
            if (chainNotValidated) {
                // hasExpiredCert and notYetValidCert included in this case
                exitCode |= 4;
            }
            if (badKeyUsage || badExtendedKeyUsage || badNetscapeCertType) {
                exitCode |= 8;
            }
            if (hasUnsignedEntry) {
                exitCode |= 16;
            }
            if (notSignedByAlias || aliasNotInStore) {
                exitCode |= 32;
            }
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        }
    }

    /*
     * Parse command line arguments.
     */
    void parseArgs(String args[]) {
        /* parse flags */
        int n = 0;

        if (args.length == 0) fullusage();
        for (n=0; n < args.length; n++) {

            String flags = args[n];

            if (collator.compare(flags, "-keystore") == 0) {
                if (++n == args.length) usageNoArg();
                keystore = args[n];
            } else if (collator.compare(flags, "-storepass") ==0) {
                if (++n == args.length) usageNoArg();
                storepass = args[n].toCharArray();
            } else if (collator.compare(flags, "-storetype") ==0) {
                if (++n == args.length) usageNoArg();
                storetype = args[n];
            } else if (collator.compare(flags, "-providerName") ==0) {
                if (++n == args.length) usageNoArg();
                providerName = args[n];
            } else if ((collator.compare(flags, "-provider") == 0) ||
                        (collator.compare(flags, "-providerClass") == 0)) {
                if (++n == args.length) usageNoArg();
                if (providers == null) {
                    providers = new Vector<String>(3);
                }
                providers.add(args[n]);

                if (args.length > (n+1)) {
                    flags = args[n+1];
                    if (collator.compare(flags, "-providerArg") == 0) {
                        if (args.length == (n+2)) usageNoArg();
                        providerArgs.put(args[n], args[n+2]);
                        n += 2;
                    }
                }
            } else if (collator.compare(flags, "-protected") ==0) {
                protectedPath = true;
            } else if (collator.compare(flags, "-certchain") ==0) {
                if (++n == args.length) usageNoArg();
                altCertChain = args[n];
            } else if (collator.compare(flags, "-debug") ==0) {
                debug = true;
            } else if (collator.compare(flags, "-keypass") ==0) {
                if (++n == args.length) usageNoArg();
                keypass = args[n].toCharArray();
            } else if (collator.compare(flags, "-sigfile") ==0) {
                if (++n == args.length) usageNoArg();
                sigfile = args[n];
            } else if (collator.compare(flags, "-signedjar") ==0) {
                if (++n == args.length) usageNoArg();
                signedjar = args[n];
            } else if (collator.compare(flags, "-tsa") ==0) {
                if (++n == args.length) usageNoArg();
                tsaUrl = args[n];
            } else if (collator.compare(flags, "-tsacert") ==0) {
                if (++n == args.length) usageNoArg();
                tsaAlias = args[n];
            } else if (collator.compare(flags, "-altsigner") ==0) {
                if (++n == args.length) usageNoArg();
                altSignerClass = args[n];
            } else if (collator.compare(flags, "-altsignerpath") ==0) {
                if (++n == args.length) usageNoArg();
                altSignerClasspath = args[n];
            } else if (collator.compare(flags, "-sectionsonly") ==0) {
                signManifest = false;
            } else if (collator.compare(flags, "-internalsf") ==0) {
                externalSF = false;
            } else if (collator.compare(flags, "-verify") ==0) {
                verify = true;
            } else if (collator.compare(flags, "-verbose") ==0) {
                verbose = "all";
            } else if (collator.compare(flags, "-verbose:all") ==0) {
                verbose = "all";
            } else if (collator.compare(flags, "-verbose:summary") ==0) {
                verbose = "summary";
            } else if (collator.compare(flags, "-verbose:grouped") ==0) {
                verbose = "grouped";
            } else if (collator.compare(flags, "-sigalg") ==0) {
                if (++n == args.length) usageNoArg();
                sigalg = args[n];
            } else if (collator.compare(flags, "-digestalg") ==0) {
                if (++n == args.length) usageNoArg();
                digestalg = args[n];
            } else if (collator.compare(flags, "-certs") ==0) {
                showcerts = true;
            } else if (collator.compare(flags, "-strict") ==0) {
                strict = true;
            } else if (collator.compare(flags, "-h") == 0 ||
                        collator.compare(flags, "-help") == 0) {
                fullusage();
            } else {
                if (!flags.startsWith("-")) {
                    if (jarfile == null) {
                        jarfile = flags;
                    } else {
                        alias = flags;
                        ckaliases.add(alias);
                    }
                } else {
                    System.err.println(
                            rb.getString("Illegal option: ") + flags);
                    usage();
                }
            }
        }

        // -certs must always be specified with -verbose
        if (verbose == null) showcerts = false;

        if (jarfile == null) {
            System.err.println(rb.getString("Please specify jarfile name"));
            usage();
        }
        if (!verify && alias == null) {
            System.err.println(rb.getString("Please specify alias name"));
            usage();
        }
        if (!verify && ckaliases.size() > 1) {
            System.err.println(rb.getString("Only one alias can be specified"));
            usage();
        }

        if (storetype == null) {
            storetype = KeyStore.getDefaultType();
        }
        storetype = KeyStoreUtil.niceStoreTypeName(storetype);

        try {
            if (signedjar != null && new File(signedjar).getCanonicalPath().equals(
                    new File(jarfile).getCanonicalPath())) {
                signedjar = null;
            }
        } catch (IOException ioe) {
            // File system error?
            // Just ignore it.
        }

        if (P11KEYSTORE.equalsIgnoreCase(storetype) ||
                KeyStoreUtil.isWindowsKeyStore(storetype)) {
            token = true;
            if (keystore == null) {
                keystore = NONE;
            }
        }

        if (NONE.equals(keystore)) {
            nullStream = true;
        }

        if (token && !nullStream) {
            System.err.println(MessageFormat.format(rb.getString
                ("-keystore must be NONE if -storetype is {0}"), storetype));
            usage();
        }

        if (token && keypass != null) {
            System.err.println(MessageFormat.format(rb.getString
                ("-keypass can not be specified " +
                "if -storetype is {0}"), storetype));
            usage();
        }

        if (protectedPath) {
            if (storepass != null || keypass != null) {
                System.err.println(rb.getString
                        ("If -protected is specified, " +
                        "then -storepass and -keypass must not be specified"));
                usage();
            }
        }
        if (KeyStoreUtil.isWindowsKeyStore(storetype)) {
            if (storepass != null || keypass != null) {
                System.err.println(rb.getString
                        ("If keystore is not password protected, " +
                        "then -storepass and -keypass must not be specified"));
                usage();
            }
        }
    }

    void usageNoArg() {
        System.out.println(rb.getString("Option lacks argument"));
        usage();
    }

    void usage() {
        System.out.println();
        System.out.println(rb.getString("Please type jarsigner -help for usage"));
        System.exit(1);
    }

    void fullusage() {
        System.out.println(rb.getString
                ("Usage: jarsigner [options] jar-file alias"));
        System.out.println(rb.getString
                ("       jarsigner -verify [options] jar-file [alias...]"));
        System.out.println();
        System.out.println(rb.getString
                ("[-keystore <url>]           keystore location"));
        System.out.println();
        System.out.println(rb.getString
                ("[-storepass <password>]     password for keystore integrity"));
        System.out.println();
        System.out.println(rb.getString
                ("[-storetype <type>]         keystore type"));
        System.out.println();
        System.out.println(rb.getString
                ("[-keypass <password>]       password for private key (if different)"));
        System.out.println();
        System.out.println(rb.getString
                ("[-certchain <file>]         name of alternative certchain file"));
        System.out.println();
        System.out.println(rb.getString
                ("[-sigfile <file>]           name of .SF/.DSA file"));
        System.out.println();
        System.out.println(rb.getString
                ("[-signedjar <file>]         name of signed JAR file"));
        System.out.println();
        System.out.println(rb.getString
                ("[-digestalg <algorithm>]    name of digest algorithm"));
        System.out.println();
        System.out.println(rb.getString
                ("[-sigalg <algorithm>]       name of signature algorithm"));
        System.out.println();
        System.out.println(rb.getString
                ("[-verify]                   verify a signed JAR file"));
        System.out.println();
        System.out.println(rb.getString
                ("[-verbose[:suboptions]]     verbose output when signing/verifying."));
        System.out.println(rb.getString
                ("                            suboptions can be all, grouped or summary"));
        System.out.println();
        System.out.println(rb.getString
                ("[-certs]                    display certificates when verbose and verifying"));
        System.out.println();
        System.out.println(rb.getString
                ("[-tsa <url>]                location of the Timestamping Authority"));
        System.out.println();
        System.out.println(rb.getString
                ("[-tsacert <alias>]          public key certificate for Timestamping Authority"));
        System.out.println();
        System.out.println(rb.getString
                ("[-altsigner <class>]        class name of an alternative signing mechanism"));
        System.out.println();
        System.out.println(rb.getString
                ("[-altsignerpath <pathlist>] location of an alternative signing mechanism"));
        System.out.println();
        System.out.println(rb.getString
                ("[-internalsf]               include the .SF file inside the signature block"));
        System.out.println();
        System.out.println(rb.getString
                ("[-sectionsonly]             don't compute hash of entire manifest"));
        System.out.println();
        System.out.println(rb.getString
                ("[-protected]                keystore has protected authentication path"));
        System.out.println();
        System.out.println(rb.getString
                ("[-providerName <name>]      provider name"));
        System.out.println();
        System.out.println(rb.getString
                ("[-providerClass <class>     name of cryptographic service provider's"));
        System.out.println(rb.getString
                ("  [-providerArg <arg>]] ... master class file and constructor argument"));
        System.out.println();
        System.out.println(rb.getString
                ("[-strict]                   treat warnings as errors"));
        System.out.println();

        System.exit(0);
    }

    void verifyJar(String jarName)
        throws Exception
    {
        boolean anySigned = false;  // if there exists entry inside jar signed
        JarFile jf = null;

        try {
            jf = new JarFile(jarName, true);
            Vector<JarEntry> entriesVec = new Vector<JarEntry>();
            byte[] buffer = new byte[8192];

            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = entries.nextElement();
                entriesVec.addElement(je);
                InputStream is = null;
                try {
                    is = jf.getInputStream(je);
                    int n;
                    while ((n = is.read(buffer, 0, buffer.length)) != -1) {
                        // we just read. this will throw a SecurityException
                        // if  a signature/digest check fails.
                    }
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            }

            Manifest man = jf.getManifest();

            // The map to record display info, only used when -verbose provided
            //      key: signer info string
            //      value: the list of files with common key
            Map<String,List<String>> output =
                    new LinkedHashMap<String,List<String>>();

            if (man != null) {
                if (verbose != null) System.out.println();
                Enumeration<JarEntry> e = entriesVec.elements();

                long now = System.currentTimeMillis();
                String tab = rb.getString("      ");

                while (e.hasMoreElements()) {
                    JarEntry je = e.nextElement();
                    String name = je.getName();
                    CodeSigner[] signers = je.getCodeSigners();
                    boolean isSigned = (signers != null);
                    anySigned |= isSigned;
                    hasUnsignedEntry |= !je.isDirectory() && !isSigned
                                        && !signatureRelated(name);

                    int inStoreOrScope = inKeyStore(signers);

                    boolean inStore = (inStoreOrScope & IN_KEYSTORE) != 0;
                    boolean inScope = (inStoreOrScope & IN_SCOPE) != 0;

                    notSignedByAlias |= (inStoreOrScope & NOT_ALIAS) != 0;
                    aliasNotInStore |= isSigned && (!inStore && !inScope);

                    // Only used when -verbose provided
                    StringBuffer sb = null;
                    if (verbose != null) {
                        sb = new StringBuffer();
                        boolean inManifest =
                            ((man.getAttributes(name) != null) ||
                             (man.getAttributes("./"+name) != null) ||
                             (man.getAttributes("/"+name) != null));
                        sb.append(
                          (isSigned ? rb.getString("s") : rb.getString(" ")) +
                          (inManifest ? rb.getString("m") : rb.getString(" ")) +
                          (inStore ? rb.getString("k") : rb.getString(" ")) +
                          (inScope ? rb.getString("i") : rb.getString(" ")) +
                          ((inStoreOrScope & NOT_ALIAS) != 0 ?"X":" ") +
                          rb.getString(" "));
                        sb.append("|");
                    }

                    // When -certs provided, display info has extra empty
                    // lines at the beginning and end.
                    if (isSigned) {
                        if (showcerts) sb.append('\n');
                        for (CodeSigner signer: signers) {
                            // signerInfo() must be called even if -verbose
                            // not provided. The method updates various
                            // warning flags.
                            String si = signerInfo(signer, tab, now);
                            if (showcerts) {
                                sb.append(si);
                                sb.append('\n');
                            }
                        }
                    } else if (showcerts && !verbose.equals("all")) {
                        // Print no info for unsigned entries when -verbose:all,
                        // to be consistent with old behavior.
                        if (signatureRelated(name)) {
                            sb.append("\n" + tab + rb.getString(
                                    "(Signature related entries)") + "\n\n");
                        } else {
                            sb.append("\n" + tab + rb.getString(
                                    "(Unsigned entries)") + "\n\n");
                        }
                    }

                    if (verbose != null) {
                        String label = sb.toString();
                        if (signatureRelated(name)) {
                            // Entries inside META-INF and other unsigned
                            // entries are grouped separately.
                            label = "-" + label.substring(1);
                        }

                        // The label finally contains 2 parts separated by '|':
                        // The legend displayed before the entry names, and
                        // the cert info (if -certs specfied).

                        if (!output.containsKey(label)) {
                            output.put(label, new ArrayList<String>());
                        }

                        StringBuffer fb = new StringBuffer();
                        String s = Long.toString(je.getSize());
                        for (int i = 6 - s.length(); i > 0; --i) {
                            fb.append(' ');
                        }
                        fb.append(s).append(' ').
                                append(new Date(je.getTime()).toString());
                        fb.append(' ').append(name);

                        output.get(label).add(fb.toString());
                    }
                }
            }
            if (verbose != null) {
                for (Entry<String,List<String>> s: output.entrySet()) {
                    List<String> files = s.getValue();
                    String key = s.getKey();
                    if (key.charAt(0) == '-') { // the signature-related group
                        key = ' ' + key.substring(1);
                    }
                    int pipe = key.indexOf('|');
                    if (verbose.equals("all")) {
                        for (String f: files) {
                            System.out.println(key.substring(0, pipe) + f);
                            System.out.printf(key.substring(pipe+1));
                        }
                    } else {
                        if (verbose.equals("grouped")) {
                            for (String f: files) {
                                System.out.println(key.substring(0, pipe) + f);
                            }
                        } else if (verbose.equals("summary")) {
                            System.out.print(key.substring(0, pipe));
                            if (files.size() > 1) {
                                System.out.println(files.get(0) + " " +
                                        String.format(rb.getString(
                                        "(and %d more)"), files.size()-1));
                            } else {
                                System.out.println(files.get(0));
                            }
                        }
                        System.out.printf(key.substring(pipe+1));
                    }
                }
                System.out.println();
                System.out.println(rb.getString(
                    "  s = signature was verified "));
                System.out.println(rb.getString(
                    "  m = entry is listed in manifest"));
                System.out.println(rb.getString(
                    "  k = at least one certificate was found in keystore"));
                System.out.println(rb.getString(
                    "  i = at least one certificate was found in identity scope"));
                if (ckaliases.size() > 0) {
                    System.out.println((
                        "  X = not signed by specified alias(es)"));
                }
                System.out.println();
            }
            if (man == null)
                System.out.println(rb.getString("no manifest."));

            if (!anySigned) {
                System.out.println(rb.getString(
                      "jar is unsigned. (signatures missing or not parsable)"));
            } else {
                System.out.println(rb.getString("jar verified."));
                if (hasUnsignedEntry || hasExpiredCert || hasExpiringCert ||
                    badKeyUsage || badExtendedKeyUsage || badNetscapeCertType ||
                    notYetValidCert || chainNotValidated ||
                    aliasNotInStore || notSignedByAlias) {

                    System.out.println();
                    System.out.println(rb.getString("Warning: "));
                    if (badKeyUsage) {
                        System.out.println(
                            rb.getString("This jar contains entries whose signer certificate's KeyUsage extension doesn't allow code signing."));
                    }

                    if (badExtendedKeyUsage) {
                        System.out.println(
                            rb.getString("This jar contains entries whose signer certificate's ExtendedKeyUsage extension doesn't allow code signing."));
                    }

                    if (badNetscapeCertType) {
                        System.out.println(
                            rb.getString("This jar contains entries whose signer certificate's NetscapeCertType extension doesn't allow code signing."));
                    }

                    if (hasUnsignedEntry) {
                        System.out.println(rb.getString(
                            "This jar contains unsigned entries which have not been integrity-checked. "));
                    }
                    if (hasExpiredCert) {
                        System.out.println(rb.getString(
                            "This jar contains entries whose signer certificate has expired. "));
                    }
                    if (hasExpiringCert) {
                        System.out.println(rb.getString(
                            "This jar contains entries whose signer certificate will expire within six months. "));
                    }
                    if (notYetValidCert) {
                        System.out.println(rb.getString(
                            "This jar contains entries whose signer certificate is not yet valid. "));
                    }

                    if (chainNotValidated) {
                        System.out.println(
                                rb.getString("This jar contains entries whose certificate chain is not validated."));
                    }

                    if (notSignedByAlias) {
                        System.out.println(
                                rb.getString("This jar contains signed entries which is not signed by the specified alias(es)."));
                    }

                    if (aliasNotInStore) {
                        System.out.println(rb.getString("This jar contains signed entries that's not signed by alias in this keystore."));
                    }
                    if (! (verbose != null && showcerts)) {
                        System.out.println();
                        System.out.println(rb.getString(
                            "Re-run with the -verbose and -certs options for more details."));
                    }
                }
            }
            return;
        } catch (Exception e) {
            System.out.println(rb.getString("jarsigner: ") + e);
            if (debug) {
                e.printStackTrace();
            }
        } finally { // close the resource
            if (jf != null) {
                jf.close();
            }
        }

        System.exit(1);
    }

    private static MessageFormat validityTimeForm = null;
    private static MessageFormat notYetTimeForm = null;
    private static MessageFormat expiredTimeForm = null;
    private static MessageFormat expiringTimeForm = null;

    /*
     * Display some details about a certificate:
     *
     * [<tab>] <cert-type> [", " <subject-DN>] [" (" <keystore-entry-alias> ")"]
     * [<validity-period> | <expiry-warning>]
     *
     * Note: no newline character at the end
     */
    String printCert(String tab, Certificate c, boolean checkValidityPeriod,
        long now) {

        StringBuilder certStr = new StringBuilder();
        String space = rb.getString(" ");
        X509Certificate x509Cert = null;

        if (c instanceof X509Certificate) {
            x509Cert = (X509Certificate) c;
            certStr.append(tab).append(x509Cert.getType())
                .append(rb.getString(", "))
                .append(x509Cert.getSubjectDN().getName());
        } else {
            certStr.append(tab).append(c.getType());
        }

        String alias = storeHash.get(c);
        if (alias != null) {
            certStr.append(space).append(alias);
        }

        if (checkValidityPeriod && x509Cert != null) {

            certStr.append("\n").append(tab).append("[");
            Date notAfter = x509Cert.getNotAfter();
            try {
                x509Cert.checkValidity();
                // test if cert will expire within six months
                if (now == 0) {
                    now = System.currentTimeMillis();
                }
                if (notAfter.getTime() < now + SIX_MONTHS) {
                    hasExpiringCert = true;

                    if (expiringTimeForm == null) {
                        expiringTimeForm = new MessageFormat(
                            rb.getString("certificate will expire on"));
                    }
                    Object[] source = { notAfter };
                    certStr.append(expiringTimeForm.format(source));

                } else {
                    if (validityTimeForm == null) {
                        validityTimeForm = new MessageFormat(
                            rb.getString("certificate is valid from"));
                    }
                    Object[] source = { x509Cert.getNotBefore(), notAfter };
                    certStr.append(validityTimeForm.format(source));
                }
            } catch (CertificateExpiredException cee) {
                hasExpiredCert = true;

                if (expiredTimeForm == null) {
                    expiredTimeForm = new MessageFormat(
                        rb.getString("certificate expired on"));
                }
                Object[] source = { notAfter };
                certStr.append(expiredTimeForm.format(source));

            } catch (CertificateNotYetValidException cnyve) {
                notYetValidCert = true;

                if (notYetTimeForm == null) {
                    notYetTimeForm = new MessageFormat(
                        rb.getString("certificate is not valid until"));
                }
                Object[] source = { x509Cert.getNotBefore() };
                certStr.append(notYetTimeForm.format(source));
            }
            certStr.append("]");

            boolean[] bad = new boolean[3];
            checkCertUsage(x509Cert, bad);
            if (bad[0] || bad[1] || bad[2]) {
                String x = "";
                if (bad[0]) {
                    x ="KeyUsage";
                }
                if (bad[1]) {
                    if (x.length() > 0) x = x + ", ";
                    x = x + "ExtendedKeyUsage";
                }
                if (bad[2]) {
                    if (x.length() > 0) x = x + ", ";
                    x = x + "NetscapeCertType";
                }
                certStr.append("\n").append(tab)
                        .append(MessageFormat.format(rb.getString(
                        "[{0} extension does not support code signing]"), x));
            }
        }
        return certStr.toString();
    }

    private static MessageFormat signTimeForm = null;

    private String printTimestamp(String tab, Timestamp timestamp) {

        if (signTimeForm == null) {
            signTimeForm =
                new MessageFormat(rb.getString("entry was signed on"));
        }
        Object[] source = { timestamp.getTimestamp() };

        return new StringBuilder().append(tab).append("[")
            .append(signTimeForm.format(source)).append("]").toString();
    }

    private Map<CodeSigner,Integer> cacheForInKS =
            new IdentityHashMap<CodeSigner,Integer>();

    private int inKeyStoreForOneSigner(CodeSigner signer) {
        if (cacheForInKS.containsKey(signer)) {
            return cacheForInKS.get(signer);
        }

        boolean found = false;
        int result = 0;
        List<? extends Certificate> certs = signer.getSignerCertPath().getCertificates();
        for (Certificate c : certs) {
            String alias = storeHash.get(c);
            if (alias != null) {
                if (alias.startsWith("(")) {
                    result |= IN_KEYSTORE;
                } else if (alias.startsWith("[")) {
                    result |= IN_SCOPE;
                }
                if (ckaliases.contains(alias.substring(1, alias.length() - 1))) {
                    result |= SIGNED_BY_ALIAS;
                }
            } else {
                if (store != null) {
                    try {
                        alias = store.getCertificateAlias(c);
                    } catch (KeyStoreException kse) {
                        // never happens, because keystore has been loaded
                    }
                    if (alias != null) {
                        storeHash.put(c, "(" + alias + ")");
                        found = true;
                        result |= IN_KEYSTORE;
                    }
                }
                if (!found && (scope != null)) {
                    Identity id = scope.getIdentity(c.getPublicKey());
                    if (id != null) {
                        result |= IN_SCOPE;
                        storeHash.put(c, "[" + id.getName() + "]");
                    }
                }
                if (ckaliases.contains(alias)) {
                    result |= SIGNED_BY_ALIAS;
                }
            }
        }
        cacheForInKS.put(signer, result);
        return result;
    }

    Hashtable<Certificate, String> storeHash =
                                new Hashtable<Certificate, String>();

    int inKeyStore(CodeSigner[] signers) {

        if (signers == null)
            return 0;

        int output = 0;

        for (CodeSigner signer: signers) {
            int result = inKeyStoreForOneSigner(signer);
            output |= result;
        }
        if (ckaliases.size() > 0 && (output & SIGNED_BY_ALIAS) == 0) {
            output |= NOT_ALIAS;
        }
        return output;
    }

    void signJar(String jarName, String alias, String[] args)
        throws Exception {
        boolean aliasUsed = false;
        X509Certificate tsaCert = null;

        if (sigfile == null) {
            sigfile = alias;
            aliasUsed = true;
        }

        if (sigfile.length() > 8) {
            sigfile = sigfile.substring(0, 8).toUpperCase();
        } else {
            sigfile = sigfile.toUpperCase();
        }

        StringBuilder tmpSigFile = new StringBuilder(sigfile.length());
        for (int j = 0; j < sigfile.length(); j++) {
            char c = sigfile.charAt(j);
            if (!
                ((c>= 'A' && c<= 'Z') ||
                (c>= '0' && c<= '9') ||
                (c == '-') ||
                (c == '_'))) {
                if (aliasUsed) {
                    // convert illegal characters from the alias to be _'s
                    c = '_';
                } else {
                 throw new
                   RuntimeException(rb.getString
                        ("signature filename must consist of the following characters: A-Z, 0-9, _ or -"));
                }
            }
            tmpSigFile.append(c);
        }

        sigfile = tmpSigFile.toString();

        String tmpJarName;
        if (signedjar == null) tmpJarName = jarName+".sig";
        else tmpJarName = signedjar;

        File jarFile = new File(jarName);
        File signedJarFile = new File(tmpJarName);

        // Open the jar (zip) file
        try {
            zipFile = new ZipFile(jarName);
        } catch (IOException ioe) {
            error(rb.getString("unable to open jar file: ")+jarName, ioe);
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(signedJarFile);
        } catch (IOException ioe) {
            error(rb.getString("unable to create: ")+tmpJarName, ioe);
        }

        PrintStream ps = new PrintStream(fos);
        ZipOutputStream zos = new ZipOutputStream(ps);

        /* First guess at what they might be - we don't xclude RSA ones. */
        String sfFilename = (META_INF + sigfile + ".SF").toUpperCase();
        String bkFilename = (META_INF + sigfile + ".DSA").toUpperCase();

        Manifest manifest = new Manifest();
        Map<String,Attributes> mfEntries = manifest.getEntries();

        // The Attributes of manifest before updating
        Attributes oldAttr = null;

        boolean mfModified = false;
        boolean mfCreated = false;
        byte[] mfRawBytes = null;

        try {
            MessageDigest digests[] = { MessageDigest.getInstance(digestalg) };

            // Check if manifest exists
            ZipEntry mfFile;
            if ((mfFile = getManifestFile(zipFile)) != null) {
                // Manifest exists. Read its raw bytes.
                mfRawBytes = getBytes(zipFile, mfFile);
                manifest.read(new ByteArrayInputStream(mfRawBytes));
                oldAttr = (Attributes)(manifest.getMainAttributes().clone());
            } else {
                // Create new manifest
                Attributes mattr = manifest.getMainAttributes();
                mattr.putValue(Attributes.Name.MANIFEST_VERSION.toString(),
                               "1.0");
                String javaVendor = System.getProperty("java.vendor");
                String jdkVersion = System.getProperty("java.version");
                mattr.putValue("Created-By", jdkVersion + " (" +javaVendor
                               + ")");
                mfFile = new ZipEntry(JarFile.MANIFEST_NAME);
                mfCreated = true;
            }

            /*
             * For each entry in jar
             * (except for signature-related META-INF entries),
             * do the following:
             *
             * - if entry is not contained in manifest, add it to manifest;
             * - if entry is contained in manifest, calculate its hash and
             *   compare it with the one in the manifest; if they are
             *   different, replace the hash in the manifest with the newly
             *   generated one. (This may invalidate existing signatures!)
             */
            BASE64Encoder encoder = new JarBASE64Encoder();
            Vector<ZipEntry> mfFiles = new Vector<ZipEntry>();

            for (Enumeration<? extends ZipEntry> enum_=zipFile.entries();
                        enum_.hasMoreElements();) {
                ZipEntry ze = enum_.nextElement();

                if (ze.getName().startsWith(META_INF)) {
                    // Store META-INF files in vector, so they can be written
                    // out first
                    mfFiles.addElement(ze);

                    if (signatureRelated(ze.getName())) {
                        // ignore signature-related and manifest files
                        continue;
                    }
                }

                if (manifest.getAttributes(ze.getName()) != null) {
                    // jar entry is contained in manifest, check and
                    // possibly update its digest attributes
                    if (updateDigests(ze, zipFile, digests, encoder,
                                      manifest) == true) {
                        mfModified = true;
                    }
                } else if (!ze.isDirectory()) {
                    // Add entry to manifest
                    Attributes attrs = getDigestAttributes(ze, zipFile,
                                                           digests,
                                                           encoder);
                    mfEntries.put(ze.getName(), attrs);
                    mfModified = true;
                }
            }

            // Recalculate the manifest raw bytes if necessary
            if (mfModified) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                manifest.write(baos);
                byte[] newBytes = baos.toByteArray();
                if (mfRawBytes != null
                        && oldAttr.equals(manifest.getMainAttributes())) {

                    /*
                     * Note:
                     *
                     * The Attributes object is based on HashMap and can handle
                     * continuation columns. Therefore, even if the contents are
                     * not changed (in a Map view), the bytes that it write()
                     * may be different from the original bytes that it read()
                     * from. Since the signature on the main attributes is based
                     * on raw bytes, we must retain the exact bytes.
                     */

                    int newPos = findHeaderEnd(newBytes);
                    int oldPos = findHeaderEnd(mfRawBytes);

                    if (newPos == oldPos) {
                        System.arraycopy(mfRawBytes, 0, newBytes, 0, oldPos);
                    } else {
                        // cat oldHead newTail > newBytes
                        byte[] lastBytes = new byte[oldPos +
                                newBytes.length - newPos];
                        System.arraycopy(mfRawBytes, 0, lastBytes, 0, oldPos);
                        System.arraycopy(newBytes, newPos, lastBytes, oldPos,
                                newBytes.length - newPos);
                        newBytes = lastBytes;
                    }
                }
                mfRawBytes = newBytes;
            }

            // Write out the manifest
            if (mfModified) {
                // manifest file has new length
                mfFile = new ZipEntry(JarFile.MANIFEST_NAME);
            }
            if (verbose != null) {
                if (mfCreated) {
                    System.out.println(rb.getString("   adding: ") +
                                        mfFile.getName());
                } else if (mfModified) {
                    System.out.println(rb.getString(" updating: ") +
                                        mfFile.getName());
                }
            }
            zos.putNextEntry(mfFile);
            zos.write(mfRawBytes);

            // Calculate SignatureFile (".SF") and SignatureBlockFile
            ManifestDigester manDig = new ManifestDigester(mfRawBytes);
            SignatureFile sf = new SignatureFile(digests, manifest, manDig,
                                                 sigfile, signManifest);

            if (tsaAlias != null) {
                tsaCert = getTsaCert(tsaAlias);
            }

            SignatureFile.Block block = null;

            try {
                block =
                    sf.generateBlock(privateKey, sigalg, certChain,
                        externalSF, tsaUrl, tsaCert, signingMechanism, args,
                        zipFile);
            } catch (SocketTimeoutException e) {
                // Provide a helpful message when TSA is beyond a firewall
                error(rb.getString("unable to sign jar: ") +
                rb.getString("no response from the Timestamping Authority. ") +
                rb.getString("When connecting from behind a firewall then an HTTP proxy may need to be specified. ") +
                rb.getString("Supply the following options to jarsigner: ") +
                "\n  -J-Dhttp.proxyHost=<hostname> " +
                "\n  -J-Dhttp.proxyPort=<portnumber> ", e);
            }

            sfFilename = sf.getMetaName();
            bkFilename = block.getMetaName();

            ZipEntry sfFile = new ZipEntry(sfFilename);
            ZipEntry bkFile = new ZipEntry(bkFilename);

            long time = System.currentTimeMillis();
            sfFile.setTime(time);
            bkFile.setTime(time);

            // signature file
            zos.putNextEntry(sfFile);
            sf.write(zos);
            if (verbose != null) {
                if (zipFile.getEntry(sfFilename) != null) {
                    System.out.println(rb.getString(" updating: ") +
                                sfFilename);
                } else {
                    System.out.println(rb.getString("   adding: ") +
                                sfFilename);
                }
            }

            if (verbose != null) {
                if (tsaUrl != null || tsaCert != null) {
                    System.out.println(
                        rb.getString("requesting a signature timestamp"));
                }
                if (tsaUrl != null) {
                    System.out.println(rb.getString("TSA location: ") + tsaUrl);
                }
                if (tsaCert != null) {
                    String certUrl =
                        TimestampedSigner.getTimestampingUrl(tsaCert);
                    if (certUrl != null) {
                        System.out.println(rb.getString("TSA location: ") +
                            certUrl);
                    }
                    System.out.println(rb.getString("TSA certificate: ") +
                        printCert("", tsaCert, false, 0));
                }
                if (signingMechanism != null) {
                    System.out.println(
                        rb.getString("using an alternative signing mechanism"));
                }
            }

            // signature block file
            zos.putNextEntry(bkFile);
            block.write(zos);
            if (verbose != null) {
                if (zipFile.getEntry(bkFilename) != null) {
                    System.out.println(rb.getString(" updating: ") +
                        bkFilename);
                } else {
                    System.out.println(rb.getString("   adding: ") +
                        bkFilename);
                }
            }

            // Write out all other META-INF files that we stored in the
            // vector
            for (int i=0; i<mfFiles.size(); i++) {
                ZipEntry ze = mfFiles.elementAt(i);
                if (!ze.getName().equalsIgnoreCase(JarFile.MANIFEST_NAME)
                    && !ze.getName().equalsIgnoreCase(sfFilename)
                    && !ze.getName().equalsIgnoreCase(bkFilename)) {
                    writeEntry(zipFile, zos, ze);
                }
            }

            // Write out all other files
            for (Enumeration<? extends ZipEntry> enum_=zipFile.entries();
                        enum_.hasMoreElements();) {
                ZipEntry ze = enum_.nextElement();

                if (!ze.getName().startsWith(META_INF)) {
                    if (verbose != null) {
                        if (manifest.getAttributes(ze.getName()) != null)
                          System.out.println(rb.getString("  signing: ") +
                                ze.getName());
                        else
                          System.out.println(rb.getString("   adding: ") +
                                ze.getName());
                    }
                    writeEntry(zipFile, zos, ze);
                }
            }
        } catch(IOException ioe) {
            error(rb.getString("unable to sign jar: ")+ioe, ioe);
        } finally {
            // close the resouces
            if (zipFile != null) {
                zipFile.close();
                zipFile = null;
            }

            if (zos != null) {
                zos.close();
            }
        }

        // no IOException thrown in the follow try clause, so disable
        // the try clause.
        // try {
            if (signedjar == null) {
                // attempt an atomic rename. If that fails,
                // rename the original jar file, then the signed
                // one, then delete the original.
                if (!signedJarFile.renameTo(jarFile)) {
                    File origJar = new File(jarName+".orig");

                    if (jarFile.renameTo(origJar)) {
                        if (signedJarFile.renameTo(jarFile)) {
                            origJar.delete();
                        } else {
                            MessageFormat form = new MessageFormat(rb.getString
                        ("attempt to rename signedJarFile to jarFile failed"));
                            Object[] source = {signedJarFile, jarFile};
                            error(form.format(source));
                        }
                    } else {
                        MessageFormat form = new MessageFormat(rb.getString
                            ("attempt to rename jarFile to origJar failed"));
                        Object[] source = {jarFile, origJar};
                        error(form.format(source));
                    }
                }
            }

            if (hasExpiredCert || hasExpiringCert || notYetValidCert
                    || badKeyUsage || badExtendedKeyUsage
                    || badNetscapeCertType || chainNotValidated) {
                System.out.println();

                System.out.println(rb.getString("Warning: "));
                if (badKeyUsage) {
                    System.out.println(
                        rb.getString("The signer certificate's KeyUsage extension doesn't allow code signing."));
                }

                if (badExtendedKeyUsage) {
                    System.out.println(
                        rb.getString("The signer certificate's ExtendedKeyUsage extension doesn't allow code signing."));
                }

                if (badNetscapeCertType) {
                    System.out.println(
                        rb.getString("The signer certificate's NetscapeCertType extension doesn't allow code signing."));
                }

                if (hasExpiredCert) {
                    System.out.println(
                        rb.getString("The signer certificate has expired."));
                } else if (hasExpiringCert) {
                    System.out.println(
                        rb.getString("The signer certificate will expire within six months."));
                } else if (notYetValidCert) {
                    System.out.println(
                        rb.getString("The signer certificate is not yet valid."));
                }

                if (chainNotValidated) {
                    System.out.println(
                            rb.getString("The signer's certificate chain is not validated."));
                }
            }

        // no IOException thrown in the above try clause, so disable
        // the catch clause.
        // } catch(IOException ioe) {
        //     error(rb.getString("unable to sign jar: ")+ioe, ioe);
        // }
    }

    /**
     * Find the position of an empty line inside bs
     */
    private int findHeaderEnd(byte[] bs) {
        // An empty line can be at the beginning...
        if (bs.length > 1 && bs[0] == '\r' && bs[1] == '\n') {
            return 0;
        }
        // ... or after another line
        for (int i=0; i<bs.length-3; i++) {
            if (bs[i] == '\r' && bs[i+1] == '\n' &&
                    bs[i+2] == '\r' && bs[i+3] == '\n') {
               return i;
            }
        }
        // If header end is not found, return 0,
        // which means no behavior change.
        return 0;
    }

    /**
     * signature-related files include:
     * . META-INF/MANIFEST.MF
     * . META-INF/SIG-*
     * . META-INF/*.SF
     * . META-INF/*.DSA
     * . META-INF/*.RSA
     */
    private boolean signatureRelated(String name) {
        String ucName = name.toUpperCase();
        if (ucName.equals(JarFile.MANIFEST_NAME) ||
            ucName.equals(META_INF) ||
            (ucName.startsWith(SIG_PREFIX) &&
                ucName.indexOf("/") == ucName.lastIndexOf("/"))) {
            return true;
        }

        if (ucName.startsWith(META_INF) &&
            SignatureFileVerifier.isBlockOrSF(ucName)) {
            // .SF/.DSA/.RSA files in META-INF subdirs
            // are not considered signature-related
            return (ucName.indexOf("/") == ucName.lastIndexOf("/"));
        }

        return false;
    }

    Map<CodeSigner,String> cacheForSignerInfo = new IdentityHashMap<CodeSigner,String>();

    /**
     * Returns a string of singer info, with a newline at the end
     */
    private String signerInfo(CodeSigner signer, String tab, long now) {
        if (cacheForSignerInfo.containsKey(signer)) {
            return cacheForSignerInfo.get(signer);
        }
        StringBuffer s = new StringBuffer();
        List<? extends Certificate> certs = signer.getSignerCertPath().getCertificates();
        // display the signature timestamp, if present
        Timestamp timestamp = signer.getTimestamp();
        if (timestamp != null) {
            s.append(printTimestamp(tab, timestamp));
        }
        // display the certificate(s)
        for (Certificate c : certs) {
            s.append(printCert(tab, c, true, now));
            s.append('\n');
        }
        try {
            CertPath cp = certificateFactory.generateCertPath(certs);
            validator.validate(cp, pkixParameters);
        } catch (Exception e) {
            chainNotValidated = true;
            s.append(tab + rb.getString("[CertPath not validated: ") +
                    e.getLocalizedMessage() + "]\n");   // TODO
        }
        String result = s.toString();
        cacheForSignerInfo.put(signer, result);
        return result;
    }

    private void writeEntry(ZipFile zf, ZipOutputStream os, ZipEntry ze)
    throws IOException
    {
        ZipEntry ze2 = new ZipEntry(ze.getName());
        ze2.setMethod(ze.getMethod());
        ze2.setTime(ze.getTime());
        ze2.setComment(ze.getComment());
        ze2.setExtra(ze.getExtra());
        if (ze.getMethod() == ZipEntry.STORED) {
            ze2.setSize(ze.getSize());
            ze2.setCrc(ze.getCrc());
        }
        os.putNextEntry(ze2);
        writeBytes(zf, ze, os);
    }

    /**
     * Writes all the bytes for a given entry to the specified output stream.
     */
    private synchronized void writeBytes
        (ZipFile zf, ZipEntry ze, ZipOutputStream os) throws IOException {
        int n;

        InputStream is = null;
        try {
            is = zf.getInputStream(ze);
            long left = ze.getSize();

            while((left > 0) && (n = is.read(buffer, 0, buffer.length)) != -1) {
                os.write(buffer, 0, n);
                left -= n;
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    void loadKeyStore(String keyStoreName, boolean prompt) {

        if (!nullStream && keyStoreName == null) {
            keyStoreName = System.getProperty("user.home") + File.separator
                + ".keystore";
        }

        try {
            if (providerName == null) {
                store = KeyStore.getInstance(storetype);
            } else {
                store = KeyStore.getInstance(storetype, providerName);
            }

            // Get pass phrase
            // XXX need to disable echo; on UNIX, call getpass(char *prompt)Z
            // and on NT call ??
            if (token && storepass == null && !protectedPath
                    && !KeyStoreUtil.isWindowsKeyStore(storetype)) {
                storepass = getPass
                        (rb.getString("Enter Passphrase for keystore: "));
            } else if (!token && storepass == null && prompt) {
                storepass = getPass
                        (rb.getString("Enter Passphrase for keystore: "));
            }

            if (nullStream) {
                store.load(null, storepass);
            } else {
                keyStoreName = keyStoreName.replace(File.separatorChar, '/');
                URL url = null;
                try {
                    url = new URL(keyStoreName);
                } catch (java.net.MalformedURLException e) {
                    // try as file
                    url = new File(keyStoreName).toURI().toURL();
                }
                InputStream is = null;
                try {
                    is = url.openStream();
                    store.load(is, storepass);
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            }
            Set<TrustAnchor> tas = new HashSet<TrustAnchor>();
            try {
                KeyStore caks = KeyTool.getCacertsKeyStore();
                if (caks != null) {
                    Enumeration<String> aliases = caks.aliases();
                    while (aliases.hasMoreElements()) {
                        String a = aliases.nextElement();
                        try {
                            tas.add(new TrustAnchor((X509Certificate)caks.getCertificate(a), null));
                        } catch (Exception e2) {
                            // ignore, when a SecretkeyEntry does not include a cert
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore, if cacerts cannot be loaded
            }
            if (store != null) {
                Enumeration<String> aliases = store.aliases();
                while (aliases.hasMoreElements()) {
                    String a = aliases.nextElement();
                    try {
                        X509Certificate c = (X509Certificate)store.getCertificate(a);
                        // Only add TrustedCertificateEntry and self-signed
                        // PrivateKeyEntry
                        if (store.isCertificateEntry(a) ||
                                c.getSubjectDN().equals(c.getIssuerDN())) {
                            tas.add(new TrustAnchor(c, null));
                        }
                    } catch (Exception e2) {
                        // ignore, when a SecretkeyEntry does not include a cert
                    }
                }
            }
            certificateFactory = CertificateFactory.getInstance("X.509");
            validator = CertPathValidator.getInstance("PKIX");
            try {
                pkixParameters = new PKIXParameters(tas);
                pkixParameters.setRevocationEnabled(false);
            } catch (InvalidAlgorithmParameterException ex) {
                // Only if tas is empty
            }
        } catch (IOException ioe) {
            throw new RuntimeException(rb.getString("keystore load: ") +
                                        ioe.getMessage());
        } catch (java.security.cert.CertificateException ce) {
            throw new RuntimeException(rb.getString("certificate exception: ") +
                                        ce.getMessage());
        } catch (NoSuchProviderException pe) {
            throw new RuntimeException(rb.getString("keystore load: ") +
                                        pe.getMessage());
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(rb.getString("keystore load: ") +
                                        nsae.getMessage());
        } catch (KeyStoreException kse) {
            throw new RuntimeException
                (rb.getString("unable to instantiate keystore class: ") +
                kse.getMessage());
        }
    }

    X509Certificate getTsaCert(String alias) {

        java.security.cert.Certificate cs = null;

        try {
            cs = store.getCertificate(alias);
        } catch (KeyStoreException kse) {
            // this never happens, because keystore has been loaded
        }
        if (cs == null || (!(cs instanceof X509Certificate))) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Certificate not found for: alias.  alias must reference a valid KeyStore entry containing an X.509 public key certificate for the Timestamping Authority."));
            Object[] source = {alias, alias};
            error(form.format(source));
        }
        return (X509Certificate) cs;
    }

    /**
     * Check if userCert is designed to be a code signer
     * @param userCert the certificate to be examined
     * @param bad 3 booleans to show if the KeyUsage, ExtendedKeyUsage,
     *            NetscapeCertType has codeSigning flag turned on.
     *            If null, the class field badKeyUsage, badExtendedKeyUsage,
     *            badNetscapeCertType will be set.
     */
    void checkCertUsage(X509Certificate userCert, boolean[] bad) {

        // Can act as a signer?
        // 1. if KeyUsage, then [0:digitalSignature] or
        //    [1:nonRepudiation] should be true
        // 2. if ExtendedKeyUsage, then should contains ANY or CODE_SIGNING
        // 3. if NetscapeCertType, then should contains OBJECT_SIGNING
        // 1,2,3 must be true

        if (bad != null) {
            bad[0] = bad[1] = bad[2] = false;
        }

        boolean[] keyUsage = userCert.getKeyUsage();
        if (keyUsage != null) {
            keyUsage = Arrays.copyOf(keyUsage, 9);
            if (!keyUsage[0] && !keyUsage[1]) {
                if (bad != null) {
                    bad[0] = true;
                    badKeyUsage = true;
                }
            }
        }

        try {
            List<String> xKeyUsage = userCert.getExtendedKeyUsage();
            if (xKeyUsage != null) {
                if (!xKeyUsage.contains("2.5.29.37.0") // anyExtendedKeyUsage
                        && !xKeyUsage.contains("1.3.6.1.5.5.7.3.3")) {  // codeSigning
                    if (bad != null) {
                        bad[1] = true;
                        badExtendedKeyUsage = true;
                    }
                }
            }
        } catch (java.security.cert.CertificateParsingException e) {
            // shouldn't happen
        }

        try {
            // OID_NETSCAPE_CERT_TYPE
            byte[] netscapeEx = userCert.getExtensionValue
                    ("2.16.840.1.113730.1.1");
            if (netscapeEx != null) {
                DerInputStream in = new DerInputStream(netscapeEx);
                byte[] encoded = in.getOctetString();
                encoded = new DerValue(encoded).getUnalignedBitString()
                        .toByteArray();

                NetscapeCertTypeExtension extn =
                        new NetscapeCertTypeExtension(encoded);

                Boolean val = (Boolean)extn.get(
                        NetscapeCertTypeExtension.OBJECT_SIGNING);
                if (!val) {
                    if (bad != null) {
                        bad[2] = true;
                        badNetscapeCertType = true;
                    }
                }
            }
        } catch (IOException e) {
            //
        }
    }

    void getAliasInfo(String alias) {

        Key key = null;

        try {
            java.security.cert.Certificate[] cs = null;
            if (altCertChain != null) {
                try {
                    cs = CertificateFactory.getInstance("X.509").
                            generateCertificates(new FileInputStream(altCertChain)).
                            toArray(new Certificate[0]);
                } catch (CertificateException ex) {
                    error(rb.getString("Cannot restore certchain from file specified"));
                } catch (FileNotFoundException ex) {
                    error(rb.getString("File specified by -certchain does not exist"));
                }
            } else {
                try {
                    cs = store.getCertificateChain(alias);
                } catch (KeyStoreException kse) {
                    // this never happens, because keystore has been loaded
                }
            }
            if (cs == null || cs.length == 0) {
                if (altCertChain != null) {
                    error(rb.getString
                            ("Certificate chain not found in the file specified."));
                } else {
                    MessageFormat form = new MessageFormat(rb.getString
                        ("Certificate chain not found for: alias.  alias must" +
                        " reference a valid KeyStore key entry containing a" +
                        " private key and corresponding public key certificate chain."));
                    Object[] source = {alias, alias};
                    error(form.format(source));
                }
            }

            certChain = new X509Certificate[cs.length];
            for (int i=0; i<cs.length; i++) {
                if (!(cs[i] instanceof X509Certificate)) {
                    error(rb.getString
                        ("found non-X.509 certificate in signer's chain"));
                }
                certChain[i] = (X509Certificate)cs[i];
            }

            // We don't meant to print anything, the next call
            // checks validity and keyUsage etc
            printCert("", certChain[0], true, 0);

            try {
                CertPath cp = certificateFactory.generateCertPath(Arrays.asList(certChain));
                validator.validate(cp, pkixParameters);
            } catch (Exception e) {
                chainNotValidated = true;
            }

            try {
                if (!token && keypass == null)
                    key = store.getKey(alias, storepass);
                else
                    key = store.getKey(alias, keypass);
            } catch (UnrecoverableKeyException e) {
                if (token) {
                    throw e;
                } else if (keypass == null) {
                    // Did not work out, so prompt user for key password
                    MessageFormat form = new MessageFormat(rb.getString
                        ("Enter key password for alias: "));
                    Object[] source = {alias};
                    keypass = getPass(form.format(source));
                    key = store.getKey(alias, keypass);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            error(e.getMessage());
        } catch (UnrecoverableKeyException e) {
            error(rb.getString("unable to recover key from keystore"));
        } catch (KeyStoreException kse) {
            // this never happens, because keystore has been loaded
        }

        if (!(key instanceof PrivateKey)) {
            MessageFormat form = new MessageFormat(rb.getString
                ("key associated with alias not a private key"));
            Object[] source = {alias};
            error(form.format(source));
        } else {
            privateKey = (PrivateKey)key;
        }
    }

    void error(String message)
    {
        System.out.println(rb.getString("jarsigner: ")+message);
        System.exit(1);
    }


    void error(String message, Exception e)
    {
        System.out.println(rb.getString("jarsigner: ")+message);
        if (debug) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    char[] getPass(String prompt)
    {
        System.err.print(prompt);
        System.err.flush();
        try {
            char[] pass = Password.readPassword(System.in);

            if (pass == null) {
                error(rb.getString("you must enter key password"));
            } else {
                return pass;
            }
        } catch (IOException ioe) {
            error(rb.getString("unable to read password: ")+ioe.getMessage());
        }
        // this shouldn't happen
        return null;
    }

    /*
     * Reads all the bytes for a given zip entry.
     */
    private synchronized byte[] getBytes(ZipFile zf,
                                         ZipEntry ze) throws IOException {
        int n;

        InputStream is = null;
        try {
            is = zf.getInputStream(ze);
            baos.reset();
            long left = ze.getSize();

            while((left > 0) && (n = is.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, n);
                left -= n;
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }

        return baos.toByteArray();
    }

    /*
     * Returns manifest entry from given jar file, or null if given jar file
     * does not have a manifest entry.
     */
    private ZipEntry getManifestFile(ZipFile zf) {
        ZipEntry ze = zf.getEntry(JarFile.MANIFEST_NAME);
        if (ze == null) {
            // Check all entries for matching name
            Enumeration<? extends ZipEntry> enum_ = zf.entries();
            while (enum_.hasMoreElements() && ze == null) {
                ze = enum_.nextElement();
                if (!JarFile.MANIFEST_NAME.equalsIgnoreCase
                    (ze.getName())) {
                    ze = null;
                }
            }
        }
        return ze;
    }

    /*
     * Computes the digests of a zip entry, and returns them as an array
     * of base64-encoded strings.
     */
    private synchronized String[] getDigests(ZipEntry ze, ZipFile zf,
                                             MessageDigest[] digests,
                                             BASE64Encoder encoder)
        throws IOException {

        int n, i;
        InputStream is = null;
        try {
            is = zf.getInputStream(ze);
            long left = ze.getSize();
            while((left > 0)
                && (n = is.read(buffer, 0, buffer.length)) != -1) {
                for (i=0; i<digests.length; i++) {
                    digests[i].update(buffer, 0, n);
                }
                left -= n;
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }

        // complete the digests
        String[] base64Digests = new String[digests.length];
        for (i=0; i<digests.length; i++) {
            base64Digests[i] = encoder.encode(digests[i].digest());
        }
        return base64Digests;
    }

    /*
     * Computes the digests of a zip entry, and returns them as a list of
     * attributes
     */
    private Attributes getDigestAttributes(ZipEntry ze, ZipFile zf,
                                           MessageDigest[] digests,
                                           BASE64Encoder encoder)
        throws IOException {

        String[] base64Digests = getDigests(ze, zf, digests, encoder);
        Attributes attrs = new Attributes();

        for (int i=0; i<digests.length; i++) {
            attrs.putValue(digests[i].getAlgorithm()+"-Digest",
                           base64Digests[i]);
        }
        return attrs;
    }

    /*
     * Updates the digest attributes of a manifest entry, by adding or
     * replacing digest values.
     * A digest value is added if the manifest entry does not contain a digest
     * for that particular algorithm.
     * A digest value is replaced if it is obsolete.
     *
     * Returns true if the manifest entry has been changed, and false
     * otherwise.
     */
    private boolean updateDigests(ZipEntry ze, ZipFile zf,
                                  MessageDigest[] digests,
                                  BASE64Encoder encoder,
                                  Manifest mf) throws IOException {
        boolean update = false;

        Attributes attrs = mf.getAttributes(ze.getName());
        String[] base64Digests = getDigests(ze, zf, digests, encoder);

        for (int i=0; i<digests.length; i++) {
            // The entry name to be written into attrs
            String name = null;
            try {
                // Find if the digest already exists
                AlgorithmId aid = AlgorithmId.get(digests[i].getAlgorithm());
                for (Object key: attrs.keySet()) {
                    if (key instanceof Attributes.Name) {
                        String n = ((Attributes.Name)key).toString();
                        if (n.toUpperCase(Locale.ENGLISH).endsWith("-DIGEST")) {
                            String tmp = n.substring(0, n.length() - 7);
                            if (AlgorithmId.get(tmp).equals(aid)) {
                                name = n;
                                break;
                            }
                        }
                    }
                }
            } catch (NoSuchAlgorithmException nsae) {
                // Ignored. Writing new digest entry.
            }

            if (name == null) {
                name = digests[i].getAlgorithm()+"-Digest";
                attrs.putValue(name, base64Digests[i]);
                update=true;
            } else {
                // compare digests, and replace the one in the manifest
                // if they are different
                String mfDigest = attrs.getValue(name);
                if (!mfDigest.equalsIgnoreCase(base64Digests[i])) {
                    attrs.putValue(name, base64Digests[i]);
                    update=true;
                }
            }
        }
        return update;
    }

    /*
     * Try to load the specified signing mechanism.
     * The URL class loader is used.
     */
    private ContentSigner loadSigningMechanism(String signerClassName,
        String signerClassPath) throws Exception {

        // construct class loader
        String cpString = null;   // make sure env.class.path defaults to dot

        // do prepends to get correct ordering
        cpString = PathList.appendPath(System.getProperty("env.class.path"), cpString);
        cpString = PathList.appendPath(System.getProperty("java.class.path"), cpString);
        cpString = PathList.appendPath(signerClassPath, cpString);
        URL[] urls = PathList.pathToURLs(cpString);
        ClassLoader appClassLoader = new URLClassLoader(urls);

        // attempt to find signer
        Class signerClass = appClassLoader.loadClass(signerClassName);

        // Check that it implements ContentSigner
        Object signer = signerClass.newInstance();
        if (!(signer instanceof ContentSigner)) {
            MessageFormat form = new MessageFormat(
                rb.getString("signerClass is not a signing mechanism"));
            Object[] source = {signerClass.getName()};
            throw new IllegalArgumentException(form.format(source));
        }
        return (ContentSigner)signer;
    }
}

/**
 * This is a BASE64Encoder that does not insert a default newline at the end of
 * every output line. This is necessary because java.util.jar does its own
 * line management (see Manifest.make72Safe()). Inserting additional new lines
 * can cause line-wrapping problems (see CR 6219522).
 */
class JarBASE64Encoder extends BASE64Encoder {
    /**
     * Encode the suffix that ends every output line.
     */
    protected void encodeLineSuffix(OutputStream aStream) throws IOException { }
}

class SignatureFile {

    /** SignatureFile */
    Manifest sf;

    /** .SF base name */
    String baseName;

    public SignatureFile(MessageDigest digests[],
                         Manifest mf,
                         ManifestDigester md,
                         String baseName,
                         boolean signManifest)

    {
        this.baseName = baseName;

        String version = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");

        sf = new Manifest();
        Attributes mattr = sf.getMainAttributes();
        BASE64Encoder encoder = new JarBASE64Encoder();

        mattr.putValue(Attributes.Name.SIGNATURE_VERSION.toString(), "1.0");
        mattr.putValue("Created-By", version + " (" + javaVendor + ")");

        if (signManifest) {
            // sign the whole manifest
            for (int i=0; i < digests.length; i++) {
                mattr.putValue(digests[i].getAlgorithm()+"-Digest-Manifest",
                               encoder.encode(md.manifestDigest(digests[i])));
            }
        }

        // create digest of the manifest main attributes
        ManifestDigester.Entry mde =
                md.get(ManifestDigester.MF_MAIN_ATTRS, false);
        if (mde != null) {
            for (int i=0; i < digests.length; i++) {
                mattr.putValue(digests[i].getAlgorithm() +
                        "-Digest-" + ManifestDigester.MF_MAIN_ATTRS,
                        encoder.encode(mde.digest(digests[i])));
            }
        } else {
            throw new IllegalStateException
                ("ManifestDigester failed to create " +
                "Manifest-Main-Attribute entry");
        }

        /* go through the manifest entries and create the digests */

        Map<String,Attributes> entries = sf.getEntries();
        Iterator<Map.Entry<String,Attributes>> mit =
                                mf.getEntries().entrySet().iterator();
        while(mit.hasNext()) {
            Map.Entry<String,Attributes> e = mit.next();
            String name = e.getKey();
            mde = md.get(name, false);
            if (mde != null) {
                Attributes attr = new Attributes();
                for (int i=0; i < digests.length; i++) {
                    attr.putValue(digests[i].getAlgorithm()+"-Digest",
                                  encoder.encode(mde.digest(digests[i])));
                }
                entries.put(name, attr);
            }
        }
    }

    /**
     * Writes the SignatureFile to the specified OutputStream.
     *
     * @param out the output stream
     * @exception IOException if an I/O error has occurred
     */

    public void write(OutputStream out) throws IOException
    {
        sf.write(out);
    }

    /**
     * get .SF file name
     */
    public String getMetaName()
    {
        return "META-INF/"+ baseName + ".SF";
    }

    /**
     * get base file name
     */
    public String getBaseName()
    {
        return baseName;
    }

    /*
     * Generate a signed data block.
     * If a URL or a certificate (containing a URL) for a Timestamping
     * Authority is supplied then a signature timestamp is generated and
     * inserted into the signed data block.
     *
     * @param sigalg signature algorithm to use, or null to use default
     * @param tsaUrl The location of the Timestamping Authority. If null
     *               then no timestamp is requested.
     * @param tsaCert The certificate for the Timestamping Authority. If null
     *               then no timestamp is requested.
     * @param signingMechanism The signing mechanism to use.
     * @param args The command-line arguments to jarsigner.
     * @param zipFile The original source Zip file.
     */
    public Block generateBlock(PrivateKey privateKey,
                               String sigalg,
                               X509Certificate[] certChain,
                               boolean externalSF, String tsaUrl,
                               X509Certificate tsaCert,
                               ContentSigner signingMechanism,
                               String[] args, ZipFile zipFile)
        throws NoSuchAlgorithmException, InvalidKeyException, IOException,
            SignatureException, CertificateException
    {
        return new Block(this, privateKey, sigalg, certChain, externalSF,
                tsaUrl, tsaCert, signingMechanism, args, zipFile);
    }


    public static class Block {

        private byte[] block;
        private String blockFileName;

        /*
         * Construct a new signature block.
         */
        Block(SignatureFile sfg, PrivateKey privateKey, String sigalg,
            X509Certificate[] certChain, boolean externalSF, String tsaUrl,
            X509Certificate tsaCert, ContentSigner signingMechanism,
            String[] args, ZipFile zipFile)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException,
            SignatureException, CertificateException {

            Principal issuerName = certChain[0].getIssuerDN();
            if (!(issuerName instanceof X500Name)) {
                // must extract the original encoded form of DN for subsequent
                // name comparison checks (converting to a String and back to
                // an encoded DN could cause the types of String attribute
                // values to be changed)
                X509CertInfo tbsCert = new
                    X509CertInfo(certChain[0].getTBSCertificate());
                issuerName = (Principal)
                    tbsCert.get(CertificateIssuerName.NAME + "." +
                                CertificateIssuerName.DN_NAME);
            }
            BigInteger serial = certChain[0].getSerialNumber();

            String digestAlgorithm;
            String signatureAlgorithm;
            String keyAlgorithm = privateKey.getAlgorithm();
            /*
             * If no signature algorithm was specified, we choose a
             * default that is compatible with the private key algorithm.
             */
            if (sigalg == null) {

                if (keyAlgorithm.equalsIgnoreCase("DSA"))
                    digestAlgorithm = "SHA1";
                else if (keyAlgorithm.equalsIgnoreCase("RSA"))
                    digestAlgorithm = "SHA256";
                else {
                    throw new RuntimeException("private key is not a DSA or "
                                               + "RSA key");
                }
                signatureAlgorithm = digestAlgorithm + "with" + keyAlgorithm;
            } else {
                signatureAlgorithm = sigalg;
            }

            // check common invalid key/signature algorithm combinations
            String sigAlgUpperCase = signatureAlgorithm.toUpperCase();
            if ((sigAlgUpperCase.endsWith("WITHRSA") &&
                !keyAlgorithm.equalsIgnoreCase("RSA")) ||
                (sigAlgUpperCase.endsWith("WITHDSA") &&
                !keyAlgorithm.equalsIgnoreCase("DSA"))) {
                throw new SignatureException
                    ("private key algorithm is not compatible with signature algorithm");
            }

            blockFileName = "META-INF/"+sfg.getBaseName()+"."+keyAlgorithm;

            AlgorithmId sigAlg = AlgorithmId.get(signatureAlgorithm);
            AlgorithmId digEncrAlg = AlgorithmId.get(keyAlgorithm);

            Signature sig = Signature.getInstance(signatureAlgorithm);
            sig.initSign(privateKey);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sfg.write(baos);

            byte[] content = baos.toByteArray();

            sig.update(content);
            byte[] signature = sig.sign();

            // Timestamp the signature and generate the signature block file
            if (signingMechanism == null) {
                signingMechanism = new TimestampedSigner();
            }
            URI tsaUri = null;
            try {
                if (tsaUrl != null) {
                    tsaUri = new URI(tsaUrl);
                }
            } catch (URISyntaxException e) {
                IOException ioe = new IOException();
                ioe.initCause(e);
                throw ioe;
            }

            // Assemble parameters for the signing mechanism
            ContentSignerParameters params =
                new JarSignerParameters(args, tsaUri, tsaCert, signature,
                    signatureAlgorithm, certChain, content, zipFile);

            // Generate the signature block
            block = signingMechanism.generateSignedData(
                    params, externalSF, (tsaUrl != null || tsaCert != null));
        }

        /*
         * get block file name.
         */
        public String getMetaName()
        {
            return blockFileName;
        }

        /**
         * Writes the block file to the specified OutputStream.
         *
         * @param out the output stream
         * @exception IOException if an I/O error has occurred
         */

        public void write(OutputStream out) throws IOException
        {
            out.write(block);
        }
    }
}


/*
 * This object encapsulates the parameters used to perform content signing.
 */
class JarSignerParameters implements ContentSignerParameters {

    private String[] args;
    private URI tsa;
    private X509Certificate tsaCertificate;
    private byte[] signature;
    private String signatureAlgorithm;
    private X509Certificate[] signerCertificateChain;
    private byte[] content;
    private ZipFile source;

    /**
     * Create a new object.
     */
    JarSignerParameters(String[] args, URI tsa, X509Certificate tsaCertificate,
        byte[] signature, String signatureAlgorithm,
        X509Certificate[] signerCertificateChain, byte[] content,
        ZipFile source) {

        if (signature == null || signatureAlgorithm == null ||
            signerCertificateChain == null) {
            throw new NullPointerException();
        }
        this.args = args;
        this.tsa = tsa;
        this.tsaCertificate = tsaCertificate;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.signerCertificateChain = signerCertificateChain;
        this.content = content;
        this.source = source;
    }

    /**
     * Retrieves the command-line arguments.
     *
     * @return The command-line arguments. May be null.
     */
    public String[] getCommandLine() {
        return args;
    }

    /**
     * Retrieves the identifier for a Timestamping Authority (TSA).
     *
     * @return The TSA identifier. May be null.
     */
    public URI getTimestampingAuthority() {
        return tsa;
    }

    /**
     * Retrieves the certificate for a Timestamping Authority (TSA).
     *
     * @return The TSA certificate. May be null.
     */
    public X509Certificate getTimestampingAuthorityCertificate() {
        return tsaCertificate;
    }

    /**
     * Retrieves the signature.
     *
     * @return The non-null signature bytes.
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Retrieves the name of the signature algorithm.
     *
     * @return The non-null string name of the signature algorithm.
     */
    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * Retrieves the signer's X.509 certificate chain.
     *
     * @return The non-null array of X.509 public-key certificates.
     */
    public X509Certificate[] getSignerCertificateChain() {
        return signerCertificateChain;
    }

    /**
     * Retrieves the content that was signed.
     *
     * @return The content bytes. May be null.
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * Retrieves the original source ZIP file before it was signed.
     *
     * @return The original ZIP file. May be null.
     */
    public ZipFile getSource() {
        return source;
    }
}
