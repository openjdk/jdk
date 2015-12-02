/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.tools.jarsigner;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.net.URI;
import java.text.Collator;
import java.text.MessageFormat;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.*;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.Map.Entry;

import jdk.security.jarsigner.JarSigner;
import jdk.security.jarsigner.JarSignerException;
import sun.security.tools.KeyStoreUtil;
import sun.security.x509.*;
import sun.security.util.*;


/**
 * <p>The jarsigner utility.
 *
 * The exit codes for the main method are:
 *
 * 0: success
 * 1: any error that the jar cannot be signed or verified, including:
 *      keystore loading error
 *      TSP communication error
 *      jarsigner command line error...
 * otherwise: error codes from -strict
 *
 * @author Roland Schemers
 * @author Jan Luehe
 */
@SuppressWarnings("deprecation")
public class Main {

    // for i18n
    private static final java.util.ResourceBundle rb =
        java.util.ResourceBundle.getBundle
        ("sun.security.tools.jarsigner.Resources");
    private static final Collator collator = Collator.getInstance();
    static {
        // this is for case insensitive string comparisions
        collator.setStrength(Collator.PRIMARY);
    }

    private static final String NONE = "NONE";
    private static final String P11KEYSTORE = "PKCS11";

    private static final long SIX_MONTHS = 180*24*60*60*1000L; //milliseconds

    // Attention:
    // This is the entry that get launched by the security tool jarsigner.
    public static void main(String args[]) throws Exception {
        Main js = new Main();
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

    String keystore; // key store file
    boolean nullStream = false; // null keystore input stream (NONE)
    boolean token = false; // token-based keystore
    String jarfile;  // jar files to sign or verify
    String alias;    // alias to sign jar with
    List<String> ckaliases = new ArrayList<>(); // aliases in -verify
    char[] storepass; // keystore password
    boolean protectedPath; // protected authentication path
    String storetype; // keystore type
    String providerName; // provider name
    Vector<String> providers = null; // list of providers
    // arguments for provider constructors
    HashMap<String,String> providerArgs = new HashMap<>();
    char[] keypass; // private key password
    String sigfile; // name of .SF file
    String sigalg; // name of signature algorithm
    String digestalg; // name of digest algorithm
    String signedjar; // output filename
    String tsaUrl; // location of the Timestamping Authority
    String tsaAlias; // alias for the Timestamping Authority's certificate
    String altCertChain; // file to read alternative cert chain from
    String tSAPolicyID;
    String tSADigestAlg;
    boolean verify = false; // verify the jar
    String verbose = null; // verbose output when signing/verifying
    boolean showcerts = false; // show certs when verifying
    boolean debug = false; // debug
    boolean signManifest = true; // "sign" the whole manifest
    boolean externalSF = true; // leave the .SF out of the PKCS7 block
    boolean strict = false;  // treat warnings as error

    // read zip entry raw bytes
    private String altSignerClass = null;
    private String altSignerClasspath = null;
    private ZipFile zipFile = null;

    // Informational warnings
    private boolean hasExpiringCert = false;
    private boolean noTimestamp = false;
    private Date expireDate = new Date(0L);     // used in noTimestamp warning

    // Severe warnings
    private int weakAlg = 0; // 1. digestalg, 2. sigalg, 4. tsadigestalg
    private boolean hasExpiredCert = false;
    private boolean notYetValidCert = false;
    private boolean chainNotValidated = false;
    private boolean notSignedByAlias = false;
    private boolean aliasNotInStore = false;
    private boolean hasUnsignedEntry = false;
    private boolean badKeyUsage = false;
    private boolean badExtendedKeyUsage = false;
    private boolean badNetscapeCertType = false;
    private boolean signerSelfSigned = false;

    private Throwable chainNotValidatedReason = null;

    CertificateFactory certificateFactory;
    CertPathValidator validator;
    PKIXParameters pkixParameters;

    public void run(String args[]) {
        try {
            args = parseArgs(args);

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

                    Object obj = provClass.newInstance();
                    if (!(obj instanceof Provider)) {
                        MessageFormat form = new MessageFormat(rb.getString
                            ("provName.not.a.provider"));
                        Object[] source = {provName};
                        throw new Exception(form.format(source));
                    }
                    Provider p = (Provider) obj;
                    String provArg = providerArgs.get(provName);
                    if (provArg != null) {
                        p = p.configure(provArg);
                    }
                    Security.addProvider(p);
                }
            }

            if (verify) {
                try {
                    loadKeyStore(keystore, false);
                } catch (Exception e) {
                    if ((keystore != null) || (storepass != null)) {
                        System.out.println(rb.getString("jarsigner.error.") +
                                        e.getMessage());
                        if (debug) {
                            e.printStackTrace();
                        }
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

                signJar(jarfile, alias);
            }
        } catch (Exception e) {
            System.out.println(rb.getString("jarsigner.error.") + e);
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
            if (weakAlg != 0 || chainNotValidated || hasExpiredCert || notYetValidCert || signerSelfSigned) {
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
    String[] parseArgs(String args[]) throws Exception {
        /* parse flags */
        int n = 0;

        if (args.length == 0) fullusage();

        String confFile = null;
        String command = "-sign";
        for (n=0; n < args.length; n++) {
            if (collator.compare(args[n], "-verify") == 0) {
                command = "-verify";
            } else if (collator.compare(args[n], "-conf") == 0) {
                if (n == args.length - 1) {
                    usageNoArg();
                }
                confFile = args[++n];
            }
        }

        if (confFile != null) {
            args = KeyStoreUtil.expandArgs(
                    "jarsigner", confFile, command, null, args);
        }

        debug = Arrays.stream(args).anyMatch(
                x -> collator.compare(x, "-debug") == 0);

        if (debug) {
            // No need to localize debug output
            System.out.println("Command line args: " +
                    Arrays.toString(args));
        }

        for (n=0; n < args.length; n++) {

            String flags = args[n];
            String modifier = null;

            if (flags.startsWith("-")) {
                int pos = flags.indexOf(':');
                if (pos > 0) {
                    modifier = flags.substring(pos+1);
                    flags = flags.substring(0, pos);
                }
            }

            if (!flags.startsWith("-")) {
                if (jarfile == null) {
                    jarfile = flags;
                } else {
                    alias = flags;
                    ckaliases.add(alias);
                }
            } else if (collator.compare(flags, "-conf") == 0) {
                if (++n == args.length) usageNoArg();
            } else if (collator.compare(flags, "-keystore") == 0) {
                if (++n == args.length) usageNoArg();
                keystore = args[n];
            } else if (collator.compare(flags, "-storepass") ==0) {
                if (++n == args.length) usageNoArg();
                storepass = getPass(modifier, args[n]);
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
            } else if (collator.compare(flags, "-tsapolicyid") ==0) {
                if (++n == args.length) usageNoArg();
                tSAPolicyID = args[n];
            } else if (collator.compare(flags, "-tsadigestalg") ==0) {
                if (++n == args.length) usageNoArg();
                tSADigestAlg = args[n];
            } else if (collator.compare(flags, "-debug") ==0) {
                // Already processed
            } else if (collator.compare(flags, "-keypass") ==0) {
                if (++n == args.length) usageNoArg();
                keypass = getPass(modifier, args[n]);
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
                System.err.println(
                        rb.getString("This.option.is.deprecated") +
                                "-altsigner");
            } else if (collator.compare(flags, "-altsignerpath") ==0) {
                if (++n == args.length) usageNoArg();
                altSignerClasspath = args[n];
                System.err.println(
                        rb.getString("This.option.is.deprecated") +
                                "-altsignerpath");
            } else if (collator.compare(flags, "-sectionsonly") ==0) {
                signManifest = false;
            } else if (collator.compare(flags, "-internalsf") ==0) {
                externalSF = false;
            } else if (collator.compare(flags, "-verify") ==0) {
                verify = true;
            } else if (collator.compare(flags, "-verbose") ==0) {
                verbose = (modifier != null) ? modifier : "all";
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
                System.err.println(
                        rb.getString("Illegal.option.") + flags);
                usage();
            }
        }

        // -certs must always be specified with -verbose
        if (verbose == null) showcerts = false;

        if (jarfile == null) {
            System.err.println(rb.getString("Please.specify.jarfile.name"));
            usage();
        }
        if (!verify && alias == null) {
            System.err.println(rb.getString("Please.specify.alias.name"));
            usage();
        }
        if (!verify && ckaliases.size() > 1) {
            System.err.println(rb.getString("Only.one.alias.can.be.specified"));
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
                (".keystore.must.be.NONE.if.storetype.is.{0}"), storetype));
            usage();
        }

        if (token && keypass != null) {
            System.err.println(MessageFormat.format(rb.getString
                (".keypass.can.not.be.specified.if.storetype.is.{0}"), storetype));
            usage();
        }

        if (protectedPath) {
            if (storepass != null || keypass != null) {
                System.err.println(rb.getString
                        ("If.protected.is.specified.then.storepass.and.keypass.must.not.be.specified"));
                usage();
            }
        }
        if (KeyStoreUtil.isWindowsKeyStore(storetype)) {
            if (storepass != null || keypass != null) {
                System.err.println(rb.getString
                        ("If.keystore.is.not.password.protected.then.storepass.and.keypass.must.not.be.specified"));
                usage();
            }
        }
        return args;
    }

    static char[] getPass(String modifier, String arg) {
        char[] output = KeyStoreUtil.getPassWithModifier(modifier, arg, rb);
        if (output != null) return output;
        usage();
        return null;    // Useless, usage() already exit
    }

    static void usageNoArg() {
        System.out.println(rb.getString("Option.lacks.argument"));
        usage();
    }

    static void usage() {
        System.out.println();
        System.out.println(rb.getString("Please.type.jarsigner.help.for.usage"));
        System.exit(1);
    }

    static void fullusage() {
        System.out.println(rb.getString
                ("Usage.jarsigner.options.jar.file.alias"));
        System.out.println(rb.getString
                (".jarsigner.verify.options.jar.file.alias."));
        System.out.println();
        System.out.println(rb.getString
                (".keystore.url.keystore.location"));
        System.out.println();
        System.out.println(rb.getString
                (".storepass.password.password.for.keystore.integrity"));
        System.out.println();
        System.out.println(rb.getString
                (".storetype.type.keystore.type"));
        System.out.println();
        System.out.println(rb.getString
                (".keypass.password.password.for.private.key.if.different."));
        System.out.println();
        System.out.println(rb.getString
                (".certchain.file.name.of.alternative.certchain.file"));
        System.out.println();
        System.out.println(rb.getString
                (".sigfile.file.name.of.SF.DSA.file"));
        System.out.println();
        System.out.println(rb.getString
                (".signedjar.file.name.of.signed.JAR.file"));
        System.out.println();
        System.out.println(rb.getString
                (".digestalg.algorithm.name.of.digest.algorithm"));
        System.out.println();
        System.out.println(rb.getString
                (".sigalg.algorithm.name.of.signature.algorithm"));
        System.out.println();
        System.out.println(rb.getString
                (".verify.verify.a.signed.JAR.file"));
        System.out.println();
        System.out.println(rb.getString
                (".verbose.suboptions.verbose.output.when.signing.verifying."));
        System.out.println(rb.getString
                (".suboptions.can.be.all.grouped.or.summary"));
        System.out.println();
        System.out.println(rb.getString
                (".certs.display.certificates.when.verbose.and.verifying"));
        System.out.println();
        System.out.println(rb.getString
                (".tsa.url.location.of.the.Timestamping.Authority"));
        System.out.println();
        System.out.println(rb.getString
                (".tsacert.alias.public.key.certificate.for.Timestamping.Authority"));
        System.out.println();
        System.out.println(rb.getString
                (".tsapolicyid.tsapolicyid.for.Timestamping.Authority"));
        System.out.println();
        System.out.println(rb.getString
                (".tsadigestalg.algorithm.of.digest.data.in.timestamping.request"));
        System.out.println();
        System.out.println(rb.getString
                (".altsigner.class.class.name.of.an.alternative.signing.mechanism"));
        System.out.println();
        System.out.println(rb.getString
                (".altsignerpath.pathlist.location.of.an.alternative.signing.mechanism"));
        System.out.println();
        System.out.println(rb.getString
                (".internalsf.include.the.SF.file.inside.the.signature.block"));
        System.out.println();
        System.out.println(rb.getString
                (".sectionsonly.don.t.compute.hash.of.entire.manifest"));
        System.out.println();
        System.out.println(rb.getString
                (".protected.keystore.has.protected.authentication.path"));
        System.out.println();
        System.out.println(rb.getString
                (".providerName.name.provider.name"));
        System.out.println();
        System.out.println(rb.getString
                (".providerClass.class.name.of.cryptographic.service.provider.s"));
        System.out.println(rb.getString
                (".providerArg.arg.master.class.file.and.constructor.argument"));
        System.out.println();
        System.out.println(rb.getString
                (".strict.treat.warnings.as.errors"));
        System.out.println();
        System.out.println(rb.getString
                (".conf.url.specify.a.pre.configured.options.file"));
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
            Vector<JarEntry> entriesVec = new Vector<>();
            byte[] buffer = new byte[8192];

            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = entries.nextElement();
                entriesVec.addElement(je);
                InputStream is = null;
                try {
                    is = jf.getInputStream(je);
                    while (is.read(buffer, 0, buffer.length) != -1) {
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
            Map<String,List<String>> output = new LinkedHashMap<>();

            if (man != null) {
                if (verbose != null) System.out.println();
                Enumeration<JarEntry> e = entriesVec.elements();

                String tab = rb.getString("6SPACE");

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
                    if (keystore != null) {
                        aliasNotInStore |= isSigned && (!inStore && !inScope);
                    }

                    // Only used when -verbose provided
                    StringBuffer sb = null;
                    if (verbose != null) {
                        sb = new StringBuffer();
                        boolean inManifest =
                            ((man.getAttributes(name) != null) ||
                             (man.getAttributes("./"+name) != null) ||
                             (man.getAttributes("/"+name) != null));
                        sb.append(isSigned ? rb.getString("s") : rb.getString("SPACE"))
                                .append(inManifest ? rb.getString("m") : rb.getString("SPACE"))
                                .append(inStore ? rb.getString("k") : rb.getString("SPACE"))
                                .append(inScope ? rb.getString("i") : rb.getString("SPACE"))
                                .append((inStoreOrScope & NOT_ALIAS) != 0 ? 'X' : ' ')
                                .append(rb.getString("SPACE"));
                        sb.append('|');
                    }

                    // When -certs provided, display info has extra empty
                    // lines at the beginning and end.
                    if (isSigned) {
                        if (showcerts) sb.append('\n');
                        for (CodeSigner signer: signers) {
                            // signerInfo() must be called even if -verbose
                            // not provided. The method updates various
                            // warning flags.
                            String si = signerInfo(signer, tab);
                            if (showcerts) {
                                sb.append(si);
                                sb.append('\n');
                            }
                        }
                    } else if (showcerts && !verbose.equals("all")) {
                        // Print no info for unsigned entries when -verbose:all,
                        // to be consistent with old behavior.
                        if (signatureRelated(name)) {
                            sb.append('\n')
                                    .append(tab)
                                    .append(rb
                                            .getString(".Signature.related.entries."))
                                    .append("\n\n");
                        } else {
                            sb.append('\n').append(tab)
                                    .append(rb.getString(".Unsigned.entries."))
                                    .append("\n\n");
                        }
                    }

                    if (verbose != null) {
                        String label = sb.toString();
                        if (signatureRelated(name)) {
                            // Entries inside META-INF and other unsigned
                            // entries are grouped separately.
                            label = "-" + label;
                        }

                        // The label finally contains 2 parts separated by '|':
                        // The legend displayed before the entry names, and
                        // the cert info (if -certs specified).

                        if (!output.containsKey(label)) {
                            output.put(label, new ArrayList<String>());
                        }

                        StringBuilder fb = new StringBuilder();
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
                        key = key.substring(1);
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
                                        ".and.d.more."), files.size()-1));
                            } else {
                                System.out.println(files.get(0));
                            }
                        }
                        System.out.printf(key.substring(pipe+1));
                    }
                }
                System.out.println();
                System.out.println(rb.getString(
                    ".s.signature.was.verified."));
                System.out.println(rb.getString(
                    ".m.entry.is.listed.in.manifest"));
                System.out.println(rb.getString(
                    ".k.at.least.one.certificate.was.found.in.keystore"));
                System.out.println(rb.getString(
                    ".i.at.least.one.certificate.was.found.in.identity.scope"));
                if (ckaliases.size() > 0) {
                    System.out.println(rb.getString(
                        ".X.not.signed.by.specified.alias.es."));
                }
                System.out.println();
            }
            if (man == null)
                System.out.println(rb.getString("no.manifest."));

            // If signer is a trusted cert or private entry in user's own
            // keystore, it can be self-signed.
            if (!aliasNotInStore) {
                signerSelfSigned = false;
            }

            if (!anySigned) {
                System.out.println(rb.getString(
                      "jar.is.unsigned.signatures.missing.or.not.parsable."));
            } else {
                boolean warningAppeared = false;
                boolean errorAppeared = false;
                if (badKeyUsage || badExtendedKeyUsage || badNetscapeCertType ||
                        notYetValidCert || chainNotValidated || hasExpiredCert ||
                        hasUnsignedEntry || signerSelfSigned || (weakAlg != 0) ||
                        aliasNotInStore || notSignedByAlias) {

                    if (strict) {
                        System.out.println(rb.getString("jar.verified.with.signer.errors."));
                        System.out.println();
                        System.out.println(rb.getString("Error."));
                        errorAppeared = true;
                    } else {
                        System.out.println(rb.getString("jar.verified."));
                        System.out.println();
                        System.out.println(rb.getString("Warning."));
                        warningAppeared = true;
                    }

                    if (weakAlg != 0) {
                        // In fact, jarsigner verification did not catch this
                        // since it has not read the JarFile content itself.
                        // Everything is done with JarFile API.
                    }

                    if (badKeyUsage) {
                        System.out.println(
                            rb.getString("This.jar.contains.entries.whose.signer.certificate.s.KeyUsage.extension.doesn.t.allow.code.signing."));
                    }

                    if (badExtendedKeyUsage) {
                        System.out.println(
                            rb.getString("This.jar.contains.entries.whose.signer.certificate.s.ExtendedKeyUsage.extension.doesn.t.allow.code.signing."));
                    }

                    if (badNetscapeCertType) {
                        System.out.println(
                            rb.getString("This.jar.contains.entries.whose.signer.certificate.s.NetscapeCertType.extension.doesn.t.allow.code.signing."));
                    }

                    if (hasUnsignedEntry) {
                        System.out.println(rb.getString(
                            "This.jar.contains.unsigned.entries.which.have.not.been.integrity.checked."));
                    }
                    if (hasExpiredCert) {
                        System.out.println(rb.getString(
                            "This.jar.contains.entries.whose.signer.certificate.has.expired."));
                    }
                    if (notYetValidCert) {
                        System.out.println(rb.getString(
                            "This.jar.contains.entries.whose.signer.certificate.is.not.yet.valid."));
                    }

                    if (chainNotValidated) {
                        System.out.println(String.format(
                                rb.getString("This.jar.contains.entries.whose.certificate.chain.is.not.validated.reason.1"),
                                chainNotValidatedReason.getLocalizedMessage()));
                    }

                    if (notSignedByAlias) {
                        System.out.println(
                                rb.getString("This.jar.contains.signed.entries.which.is.not.signed.by.the.specified.alias.es."));
                    }

                    if (aliasNotInStore) {
                        System.out.println(rb.getString("This.jar.contains.signed.entries.that.s.not.signed.by.alias.in.this.keystore."));
                    }

                    if (signerSelfSigned) {
                        System.out.println(rb.getString(
                                "This.jar.contains.entries.whose.signer.certificate.is.self.signed."));
                    }
                } else {
                    System.out.println(rb.getString("jar.verified."));
                }
                if (hasExpiringCert || noTimestamp) {
                    if (!warningAppeared) {
                        System.out.println();
                        System.out.println(rb.getString("Warning."));
                        warningAppeared = true;
                    }
                    if (hasExpiringCert) {
                        System.out.println(rb.getString(
                                "This.jar.contains.entries.whose.signer.certificate.will.expire.within.six.months."));
                    }
                    if (noTimestamp) {
                        System.out.println(
                                String.format(rb.getString("no.timestamp.verifying"), expireDate));
                    }
                }
                if (warningAppeared || errorAppeared) {
                    if (! (verbose != null && showcerts)) {
                        System.out.println();
                        System.out.println(rb.getString(
                                "Re.run.with.the.verbose.and.certs.options.for.more.details."));
                    }
                }
            }
            return;
        } catch (Exception e) {
            System.out.println(rb.getString("jarsigner.") + e);
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
        Date timestamp, boolean checkUsage) {

        StringBuilder certStr = new StringBuilder();
        String space = rb.getString("SPACE");
        X509Certificate x509Cert = null;

        if (c instanceof X509Certificate) {
            x509Cert = (X509Certificate) c;
            certStr.append(tab).append(x509Cert.getType())
                .append(rb.getString("COMMA"))
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
                boolean printValidity = true;
                if (timestamp == null) {
                    if (expireDate.getTime() == 0 || expireDate.after(notAfter)) {
                        expireDate = notAfter;
                    }
                    x509Cert.checkValidity();
                    // test if cert will expire within six months
                    if (notAfter.getTime() < System.currentTimeMillis() + SIX_MONTHS) {
                        hasExpiringCert = true;
                        if (expiringTimeForm == null) {
                            expiringTimeForm = new MessageFormat(
                                rb.getString("certificate.will.expire.on"));
                        }
                        Object[] source = { notAfter };
                        certStr.append(expiringTimeForm.format(source));
                        printValidity = false;
                    }
                } else {
                    x509Cert.checkValidity(timestamp);
                }
                if (printValidity) {
                    if (validityTimeForm == null) {
                        validityTimeForm = new MessageFormat(
                            rb.getString("certificate.is.valid.from"));
                    }
                    Object[] source = { x509Cert.getNotBefore(), notAfter };
                    certStr.append(validityTimeForm.format(source));
                }
            } catch (CertificateExpiredException cee) {
                hasExpiredCert = true;

                if (expiredTimeForm == null) {
                    expiredTimeForm = new MessageFormat(
                        rb.getString("certificate.expired.on"));
                }
                Object[] source = { notAfter };
                certStr.append(expiredTimeForm.format(source));

            } catch (CertificateNotYetValidException cnyve) {
                notYetValidCert = true;

                if (notYetTimeForm == null) {
                    notYetTimeForm = new MessageFormat(
                        rb.getString("certificate.is.not.valid.until"));
                }
                Object[] source = { x509Cert.getNotBefore() };
                certStr.append(notYetTimeForm.format(source));
            }
            certStr.append("]");

            if (checkUsage) {
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
                        ".{0}.extension.does.not.support.code.signing."), x));
                }
            }
        }
        return certStr.toString();
    }

    private static MessageFormat signTimeForm = null;

    private String printTimestamp(String tab, Timestamp timestamp) {

        if (signTimeForm == null) {
            signTimeForm =
                new MessageFormat(rb.getString("entry.was.signed.on"));
        }
        Object[] source = { timestamp.getTimestamp() };

        return new StringBuilder().append(tab).append("[")
            .append(signTimeForm.format(source)).append("]").toString();
    }

    private Map<CodeSigner,Integer> cacheForInKS = new IdentityHashMap<>();

    private int inKeyStoreForOneSigner(CodeSigner signer) {
        if (cacheForInKS.containsKey(signer)) {
            return cacheForInKS.get(signer);
        }

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
                        result |= IN_KEYSTORE;
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

    Hashtable<Certificate, String> storeHash = new Hashtable<>();

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

    void signJar(String jarName, String alias)
            throws Exception {

        DisabledAlgorithmConstraints dac =
                new DisabledAlgorithmConstraints(
                        DisabledAlgorithmConstraints.PROPERTY_CERTPATH_DISABLED_ALGS);

        if (digestalg != null && !dac.permits(
                Collections.singleton(CryptoPrimitive.MESSAGE_DIGEST), digestalg, null)) {
            weakAlg |= 1;
        }
        if (tSADigestAlg != null && !dac.permits(
                Collections.singleton(CryptoPrimitive.MESSAGE_DIGEST), tSADigestAlg, null)) {
            weakAlg |= 4;
        }
        if (sigalg != null && !dac.permits(
                Collections.singleton(CryptoPrimitive.SIGNATURE), sigalg, null)) {
            weakAlg |= 2;
        }

        boolean aliasUsed = false;
        X509Certificate tsaCert = null;

        if (sigfile == null) {
            sigfile = alias;
            aliasUsed = true;
        }

        if (sigfile.length() > 8) {
            sigfile = sigfile.substring(0, 8).toUpperCase(Locale.ENGLISH);
        } else {
            sigfile = sigfile.toUpperCase(Locale.ENGLISH);
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
                            ("signature.filename.must.consist.of.the.following.characters.A.Z.0.9.or."));
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
            error(rb.getString("unable.to.open.jar.file.")+jarName, ioe);
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(signedJarFile);
        } catch (IOException ioe) {
            error(rb.getString("unable.to.create.")+tmpJarName, ioe);
        }

        CertPath cp = CertificateFactory.getInstance("X.509")
                .generateCertPath(Arrays.asList(certChain));
        JarSigner.Builder builder = new JarSigner.Builder(privateKey, cp);

        if (verbose != null) {
            builder.eventHandler((action, file) -> {
                System.out.println(rb.getString("." + action + ".") + file);
            });
        }

        if (digestalg != null) {
            builder.digestAlgorithm(digestalg);
        }
        if (sigalg != null) {
            builder.signatureAlgorithm(sigalg);
        }

        URI tsaURI = null;

        if (tsaUrl != null) {
            tsaURI = new URI(tsaUrl);
        } else if (tsaAlias != null) {
            tsaCert = getTsaCert(tsaAlias);
            tsaURI = TimestampedSigner.getTimestampingURI(tsaCert);
        }

        if (tsaURI != null) {
            if (verbose != null) {
                System.out.println(
                        rb.getString("requesting.a.signature.timestamp"));
                if (tsaUrl != null) {
                    System.out.println(rb.getString("TSA.location.") + tsaUrl);
                } else if (tsaCert != null) {
                    System.out.println(rb.getString("TSA.certificate.") +
                            printCert("", tsaCert, false, null, false));
                }
            }
            builder.tsa(tsaURI);
            if (tSADigestAlg != null) {
                builder.setProperty("tsaDigestAlg", tSADigestAlg);
            }

            if (tSAPolicyID != null) {
                builder.setProperty("tsaPolicyId", tSAPolicyID);
            }
        } else {
            noTimestamp = true;
        }

        if (altSignerClass != null) {
            builder.setProperty("altSigner", altSignerClass);
            if (verbose != null) {
                System.out.println(
                        rb.getString("using.an.alternative.signing.mechanism"));
            }
        }

        if (altSignerClasspath != null) {
            builder.setProperty("altSignerPath", altSignerClasspath);
        }

        builder.signerName(sigfile);

        builder.setProperty("sectionsOnly", Boolean.toString(!signManifest));
        builder.setProperty("internalSF", Boolean.toString(!externalSF));

        try {
            builder.build().sign(zipFile, fos);
        } catch (JarSignerException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause instanceof SocketTimeoutException) {
                // Provide a helpful message when TSA is beyond a firewall
                error(rb.getString("unable.to.sign.jar.") +
                        rb.getString("no.response.from.the.Timestamping.Authority.") +
                        "\n  -J-Dhttp.proxyHost=<hostname>" +
                        "\n  -J-Dhttp.proxyPort=<portnumber>\n" +
                        rb.getString("or") +
                        "\n  -J-Dhttps.proxyHost=<hostname> " +
                        "\n  -J-Dhttps.proxyPort=<portnumber> ", e);
            } else {
                error(rb.getString("unable.to.sign.jar.")+e.getCause(), e.getCause());
            }
        } finally {
            // close the resouces
            if (zipFile != null) {
                zipFile.close();
                zipFile = null;
            }

            if (fos != null) {
                fos.close();
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
                        ("attempt.to.rename.signedJarFile.to.jarFile.failed"));
                            Object[] source = {signedJarFile, jarFile};
                            error(form.format(source));
                        }
                    } else {
                        MessageFormat form = new MessageFormat(rb.getString
                            ("attempt.to.rename.jarFile.to.origJar.failed"));
                        Object[] source = {jarFile, origJar};
                        error(form.format(source));
                    }
                }
            }

            boolean warningAppeared = false;
            if (weakAlg != 0 || badKeyUsage || badExtendedKeyUsage || badNetscapeCertType ||
                    notYetValidCert || chainNotValidated || hasExpiredCert || signerSelfSigned) {
                if (strict) {
                    System.out.println(rb.getString("jar.signed.with.signer.errors."));
                    System.out.println();
                    System.out.println(rb.getString("Error."));
                } else {
                    System.out.println(rb.getString("jar.signed."));
                    System.out.println();
                    System.out.println(rb.getString("Warning."));
                    warningAppeared = true;
                }

                if (badKeyUsage) {
                    System.out.println(
                        rb.getString("The.signer.certificate.s.KeyUsage.extension.doesn.t.allow.code.signing."));
                }

                if (badExtendedKeyUsage) {
                    System.out.println(
                        rb.getString("The.signer.certificate.s.ExtendedKeyUsage.extension.doesn.t.allow.code.signing."));
                }

                if (badNetscapeCertType) {
                    System.out.println(
                        rb.getString("The.signer.certificate.s.NetscapeCertType.extension.doesn.t.allow.code.signing."));
                }

                if (hasExpiredCert) {
                    System.out.println(
                        rb.getString("The.signer.certificate.has.expired."));
                } else if (notYetValidCert) {
                    System.out.println(
                        rb.getString("The.signer.certificate.is.not.yet.valid."));
                }

                if (chainNotValidated) {
                    System.out.println(String.format(
                            rb.getString("The.signer.s.certificate.chain.is.not.validated.reason.1"),
                            chainNotValidatedReason.getLocalizedMessage()));
                }

                if (signerSelfSigned) {
                    System.out.println(
                            rb.getString("The.signer.s.certificate.is.self.signed."));
                }

                if ((weakAlg & 1) == 1) {
                    System.out.println(String.format(
                            rb.getString("The.1.algorithm.specified.for.the.2.option.is.considered.a.security.risk."),
                            digestalg, "-digestalg"));
                }

                if ((weakAlg & 2) == 2) {
                    System.out.println(String.format(
                            rb.getString("The.1.algorithm.specified.for.the.2.option.is.considered.a.security.risk."),
                            sigalg, "-sigalg"));
                }
                if ((weakAlg & 4) == 4) {
                    System.out.println(String.format(
                            rb.getString("The.1.algorithm.specified.for.the.2.option.is.considered.a.security.risk."),
                            tSADigestAlg, "-tsadigestalg"));
                }
            } else {
                System.out.println(rb.getString("jar.signed."));
            }
            if (hasExpiringCert || noTimestamp) {
                if (!warningAppeared) {
                    System.out.println();
                    System.out.println(rb.getString("Warning."));
                }

                if (hasExpiringCert) {
                    System.out.println(
                            rb.getString("The.signer.certificate.will.expire.within.six.months."));
                }

                if (noTimestamp) {
                    System.out.println(
                            String.format(rb.getString("no.timestamp.signing"), expireDate));
                }
            }

        // no IOException thrown in the above try clause, so disable
        // the catch clause.
        // } catch(IOException ioe) {
        //     error(rb.getString("unable.to.sign.jar.")+ioe, ioe);
        // }
    }

    /**
     * signature-related files include:
     * . META-INF/MANIFEST.MF
     * . META-INF/SIG-*
     * . META-INF/*.SF
     * . META-INF/*.DSA
     * . META-INF/*.RSA
     * . META-INF/*.EC
     */
    private boolean signatureRelated(String name) {
        return SignatureFileVerifier.isSigningRelated(name);
    }

    Map<CodeSigner,String> cacheForSignerInfo = new IdentityHashMap<>();

    /**
     * Returns a string of singer info, with a newline at the end
     */
    private String signerInfo(CodeSigner signer, String tab) {
        if (cacheForSignerInfo.containsKey(signer)) {
            return cacheForSignerInfo.get(signer);
        }
        StringBuilder sb = new StringBuilder();
        List<? extends Certificate> certs = signer.getSignerCertPath().getCertificates();
        // display the signature timestamp, if present
        Date timestamp;
        Timestamp ts = signer.getTimestamp();
        if (ts != null) {
            sb.append(printTimestamp(tab, ts));
            sb.append('\n');
            timestamp = ts.getTimestamp();
        } else {
            timestamp = null;
            noTimestamp = true;
        }
        // display the certificate(sb). The first one is end-entity cert and
        // its KeyUsage should be checked.
        boolean first = true;
        for (Certificate c : certs) {
            sb.append(printCert(tab, c, true, timestamp, first));
            sb.append('\n');
            first = false;
        }
        try {
            validateCertChain(certs);
        } catch (Exception e) {
            if (debug) {
                e.printStackTrace();
            }
            if (e.getCause() != null &&
                    (e.getCause() instanceof CertificateExpiredException ||
                     e.getCause() instanceof CertificateNotYetValidException)) {
                // No more warning, we alreay have hasExpiredCert or notYetValidCert
            } else {
                chainNotValidated = true;
                chainNotValidatedReason = e;
                sb.append(tab).append(rb.getString(".CertPath.not.validated."))
                        .append(e.getLocalizedMessage()).append("]\n"); // TODO
            }
        }
        if (certs.size() == 1
                && KeyStoreUtil.isSelfSigned((X509Certificate)certs.get(0))) {
            signerSelfSigned = true;
        }
        String result = sb.toString();
        cacheForSignerInfo.put(signer, result);
        return result;
    }

    void loadKeyStore(String keyStoreName, boolean prompt) {

        if (!nullStream && keyStoreName == null) {
            keyStoreName = System.getProperty("user.home") + File.separator
                + ".keystore";
        }

        try {

            certificateFactory = CertificateFactory.getInstance("X.509");
            validator = CertPathValidator.getInstance("PKIX");
            Set<TrustAnchor> tas = new HashSet<>();
            try {
                KeyStore caks = KeyStoreUtil.getCacertsKeyStore();
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
                        (rb.getString("Enter.Passphrase.for.keystore."));
            } else if (!token && storepass == null && prompt) {
                storepass = getPass
                        (rb.getString("Enter.Passphrase.for.keystore."));
            }

            try {
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
            } finally {
                try {
                    pkixParameters = new PKIXParameters(tas);
                    pkixParameters.setRevocationEnabled(false);
                } catch (InvalidAlgorithmParameterException ex) {
                    // Only if tas is empty
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException(rb.getString("keystore.load.") +
                                        ioe.getMessage());
        } catch (java.security.cert.CertificateException ce) {
            throw new RuntimeException(rb.getString("certificate.exception.") +
                                        ce.getMessage());
        } catch (NoSuchProviderException pe) {
            throw new RuntimeException(rb.getString("keystore.load.") +
                                        pe.getMessage());
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(rb.getString("keystore.load.") +
                                        nsae.getMessage());
        } catch (KeyStoreException kse) {
            throw new RuntimeException
                (rb.getString("unable.to.instantiate.keystore.class.") +
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
                ("Certificate.not.found.for.alias.alias.must.reference.a.valid.KeyStore.entry.containing.an.X.509.public.key.certificate.for.the"));
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

                Boolean val = extn.get(NetscapeCertTypeExtension.OBJECT_SIGNING);
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
                try (FileInputStream fis = new FileInputStream(altCertChain)) {
                    cs = CertificateFactory.getInstance("X.509").
                            generateCertificates(fis).
                            toArray(new Certificate[0]);
                } catch (FileNotFoundException ex) {
                    error(rb.getString("File.specified.by.certchain.does.not.exist"));
                } catch (CertificateException | IOException ex) {
                    error(rb.getString("Cannot.restore.certchain.from.file.specified"));
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
                            ("Certificate.chain.not.found.in.the.file.specified."));
                } else {
                    MessageFormat form = new MessageFormat(rb.getString
                        ("Certificate.chain.not.found.for.alias.alias.must.reference.a.valid.KeyStore.key.entry.containing.a.private.key.and"));
                    Object[] source = {alias, alias};
                    error(form.format(source));
                }
            }

            certChain = new X509Certificate[cs.length];
            for (int i=0; i<cs.length; i++) {
                if (!(cs[i] instanceof X509Certificate)) {
                    error(rb.getString
                        ("found.non.X.509.certificate.in.signer.s.chain"));
                }
                certChain[i] = (X509Certificate)cs[i];
            }

            // We don't meant to print anything, the next call
            // checks validity and keyUsage etc
            printCert("", certChain[0], true, null, true);

            try {
                validateCertChain(Arrays.asList(certChain));
            } catch (Exception e) {
                if (debug) {
                    e.printStackTrace();
                }
                if (e.getCause() != null &&
                        (e.getCause() instanceof CertificateExpiredException ||
                        e.getCause() instanceof CertificateNotYetValidException)) {
                    // No more warning, we already have hasExpiredCert or notYetValidCert
                } else {
                    chainNotValidated = true;
                    chainNotValidatedReason = e;
                }
            }

            if (KeyStoreUtil.isSelfSigned(certChain[0])) {
                signerSelfSigned = true;
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
                        ("Enter.key.password.for.alias."));
                    Object[] source = {alias};
                    keypass = getPass(form.format(source));
                    key = store.getKey(alias, keypass);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            error(e.getMessage());
        } catch (UnrecoverableKeyException e) {
            error(rb.getString("unable.to.recover.key.from.keystore"));
        } catch (KeyStoreException kse) {
            // this never happens, because keystore has been loaded
        }

        if (!(key instanceof PrivateKey)) {
            MessageFormat form = new MessageFormat(rb.getString
                ("key.associated.with.alias.not.a.private.key"));
            Object[] source = {alias};
            error(form.format(source));
        } else {
            privateKey = (PrivateKey)key;
        }
    }

    void error(String message) {
        System.out.println(rb.getString("jarsigner.")+message);
        System.exit(1);
    }


    void error(String message, Throwable e) {
        System.out.println(rb.getString("jarsigner.")+message);
        if (debug) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    void validateCertChain(List<? extends Certificate> certs) throws Exception {
        int cpLen = 0;
        out: for (; cpLen<certs.size(); cpLen++) {
            for (TrustAnchor ta: pkixParameters.getTrustAnchors()) {
                if (ta.getTrustedCert().equals(certs.get(cpLen))) {
                    break out;
                }
            }
        }
        if (cpLen > 0) {
            CertPath cp = certificateFactory.generateCertPath(
                    (cpLen == certs.size())? certs: certs.subList(0, cpLen));
            validator.validate(cp, pkixParameters);
        }
    }

    char[] getPass(String prompt) {
        System.err.print(prompt);
        System.err.flush();
        try {
            char[] pass = Password.readPassword(System.in);

            if (pass == null) {
                error(rb.getString("you.must.enter.key.password"));
            } else {
                return pass;
            }
        } catch (IOException ioe) {
            error(rb.getString("unable.to.read.password.")+ioe.getMessage());
        }
        // this shouldn't happen
        return null;
    }
}
