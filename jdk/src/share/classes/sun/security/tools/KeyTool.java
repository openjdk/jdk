/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.Key;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.Principal;
import java.security.Provider;
import java.security.Identity;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.text.Collator;
import java.text.MessageFormat;
import java.util.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

import sun.misc.BASE64Encoder;
import sun.security.util.ObjectIdentifier;
import sun.security.pkcs.PKCS10;
import sun.security.provider.IdentityDatabase;
import sun.security.provider.SystemSigner;
import sun.security.provider.SystemIdentity;
import sun.security.provider.X509Factory;
import sun.security.util.DerOutputStream;
import sun.security.util.Password;
import sun.security.util.PathList;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import sun.security.x509.*;

import static java.security.KeyStore.*;

/**
 * This tool manages keystores.
 *
 * @author Jan Luehe
 *
 *
 * @see java.security.KeyStore
 * @see sun.security.provider.KeyProtector
 * @see sun.security.provider.JavaKeyStore
 *
 * @since 1.2
 */

public final class KeyTool {

    private boolean debug = false;
    private int command = -1;
    private String sigAlgName = null;
    private String keyAlgName = null;
    private boolean verbose = false;
    private int keysize = -1;
    private boolean rfc = false;
    private long validity = (long)90;
    private String alias = null;
    private String dname = null;
    private String dest = null;
    private String filename = null;
    private String srcksfname = null;

    // User-specified providers are added before any command is called.
    // However, they are not removed before the end of the main() method.
    // If you're calling KeyTool.main() directly in your own Java program,
    // please programtically add any providers you need and do not specify
    // them through the command line.

    private Set<Pair <String, String>> providers = null;
    private String storetype = null;
    private String srcProviderName = null;
    private String providerName = null;
    private String pathlist = null;
    private char[] storePass = null;
    private char[] storePassNew = null;
    private char[] keyPass = null;
    private char[] keyPassNew = null;
    private char[] oldPass = null;
    private char[] newPass = null;
    private char[] destKeyPass = null;
    private char[] srckeyPass = null;
    private String ksfname = null;
    private File ksfile = null;
    private InputStream ksStream = null; // keystore stream
    private String sslserver = null;
    private KeyStore keyStore = null;
    private boolean token = false;
    private boolean nullStream = false;
    private boolean kssave = false;
    private boolean noprompt = false;
    private boolean trustcacerts = false;
    private boolean protectedPath = false;
    private boolean srcprotectedPath = false;
    private CertificateFactory cf = null;
    private KeyStore caks = null; // "cacerts" keystore
    private char[] srcstorePass = null;
    private String srcstoretype = null;
    private Set<char[]> passwords = new HashSet<char[]> ();
    private String startDate = null;

    private static final int CERTREQ = 1;
    private static final int CHANGEALIAS = 2;
    private static final int DELETE = 3;
    private static final int EXPORTCERT = 4;
    private static final int GENKEYPAIR = 5;
    private static final int GENSECKEY = 6;
    // there is no HELP
    private static final int IDENTITYDB = 7;
    private static final int IMPORTCERT = 8;
    private static final int IMPORTKEYSTORE = 9;
    private static final int KEYCLONE = 10;
    private static final int KEYPASSWD = 11;
    private static final int LIST = 12;
    private static final int PRINTCERT = 13;
    private static final int SELFCERT = 14;
    private static final int STOREPASSWD = 15;

    private static final Class[] PARAM_STRING = { String.class };

    private static final String JKS = "jks";
    private static final String NONE = "NONE";
    private static final String P11KEYSTORE = "PKCS11";
    private static final String P12KEYSTORE = "PKCS12";
    private final String keyAlias = "mykey";

    // for i18n
    private static final java.util.ResourceBundle rb =
        java.util.ResourceBundle.getBundle("sun.security.util.Resources");
    private static final Collator collator = Collator.getInstance();
    static {
        // this is for case insensitive string comparisons
        collator.setStrength(Collator.PRIMARY);
    };

    private KeyTool() { }

    public static void main(String[] args) throws Exception {
        KeyTool kt = new KeyTool();
        kt.run(args, System.out);
    }

    private void run(String[] args, PrintStream out) throws Exception {
        try {
            parseArgs(args);
            doCommands(out);
        } catch (Exception e) {
            System.out.println(rb.getString("keytool error: ") + e);
            if (verbose) {
                e.printStackTrace(System.out);
            }
            if (!debug) {
                System.exit(1);
            } else {
                throw e;
            }
        } finally {
            for (char[] pass : passwords) {
                if (pass != null) {
                    Arrays.fill(pass, ' ');
                    pass = null;
                }
            }

            if (ksStream != null) {
                ksStream.close();
            }
        }
    }

    /**
     * Parse command line arguments.
     */
    void parseArgs(String[] args) {

        if (args.length == 0) usage();

        int i=0;

        for (i=0; (i < args.length) && args[i].startsWith("-"); i++) {

            String flags = args[i];
            /*
             * command modes
             */
            if (collator.compare(flags, "-certreq") == 0) {
                command = CERTREQ;
            } else if (collator.compare(flags, "-delete") == 0) {
                command = DELETE;
            } else if (collator.compare(flags, "-export") == 0 ||
                    collator.compare(flags, "-exportcert") == 0) {
                command = EXPORTCERT;
            } else if (collator.compare(flags, "-genkey") == 0 ||
                    collator.compare(flags, "-genkeypair") == 0) {
                command = GENKEYPAIR;
            } else if (collator.compare(flags, "-help") == 0) {
                usage();
                return;
            } else if (collator.compare(flags, "-identitydb") == 0) { // obsolete
                command = IDENTITYDB;
            } else if (collator.compare(flags, "-import") == 0 ||
                    collator.compare(flags, "-importcert") == 0) {
                command = IMPORTCERT;
            } else if (collator.compare(flags, "-keyclone") == 0) { // obsolete
                command = KEYCLONE;
            } else if (collator.compare(flags, "-changealias") == 0) {
                command = CHANGEALIAS;
            } else if (collator.compare(flags, "-keypasswd") == 0) {
                command = KEYPASSWD;
            } else if (collator.compare(flags, "-list") == 0) {
                command = LIST;
            } else if (collator.compare(flags, "-printcert") == 0) {
                command = PRINTCERT;
            } else if (collator.compare(flags, "-selfcert") == 0) {     // obsolete
                command = SELFCERT;
            } else if (collator.compare(flags, "-storepasswd") == 0) {
                command = STOREPASSWD;
            } else if (collator.compare(flags, "-importkeystore") == 0) {
                command = IMPORTKEYSTORE;
            } else if (collator.compare(flags, "-genseckey") == 0) {
                command = GENSECKEY;
            }

            /*
             * specifiers
             */
            else if (collator.compare(flags, "-keystore") == 0 ||
                    collator.compare(flags, "-destkeystore") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                ksfname = args[i];
            } else if (collator.compare(flags, "-storepass") == 0 ||
                    collator.compare(flags, "-deststorepass") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                storePass = args[i].toCharArray();
                passwords.add(storePass);
            } else if (collator.compare(flags, "-storetype") == 0 ||
                    collator.compare(flags, "-deststoretype") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                storetype = args[i];
            } else if (collator.compare(flags, "-srcstorepass") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                srcstorePass = args[i].toCharArray();
                passwords.add(srcstorePass);
            } else if (collator.compare(flags, "-srcstoretype") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                srcstoretype = args[i];
            } else if (collator.compare(flags, "-srckeypass") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                srckeyPass = args[i].toCharArray();
                passwords.add(srckeyPass);
            } else if (collator.compare(flags, "-srcprovidername") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                srcProviderName = args[i];
            } else if (collator.compare(flags, "-providername") == 0 ||
                    collator.compare(flags, "-destprovidername") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                providerName = args[i];
            } else if (collator.compare(flags, "-providerpath") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                pathlist = args[i];
            } else if (collator.compare(flags, "-keypass") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                keyPass = args[i].toCharArray();
                passwords.add(keyPass);
            } else if (collator.compare(flags, "-new") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                newPass = args[i].toCharArray();
                passwords.add(newPass);
            } else if (collator.compare(flags, "-destkeypass") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                destKeyPass = args[i].toCharArray();
                passwords.add(destKeyPass);
            } else if (collator.compare(flags, "-alias") == 0 ||
                    collator.compare(flags, "-srcalias") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                alias = args[i];
            } else if (collator.compare(flags, "-dest") == 0 ||
                    collator.compare(flags, "-destalias") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                dest = args[i];
            } else if (collator.compare(flags, "-dname") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                dname = args[i];
            } else if (collator.compare(flags, "-keysize") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                keysize = Integer.parseInt(args[i]);
            } else if (collator.compare(flags, "-keyalg") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                keyAlgName = args[i];
            } else if (collator.compare(flags, "-sigalg") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                sigAlgName = args[i];
            } else if (collator.compare(flags, "-startdate") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                startDate = args[i];
            } else if (collator.compare(flags, "-validity") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                validity = Long.parseLong(args[i]);
            } else if (collator.compare(flags, "-file") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                filename = args[i];
            } else if (collator.compare(flags, "-sslserver") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                sslserver = args[i];
            } else if (collator.compare(flags, "-srckeystore") == 0) {
                if (++i == args.length) errorNeedArgument(flags);
                srcksfname = args[i];
            } else if ((collator.compare(flags, "-provider") == 0) ||
                        (collator.compare(flags, "-providerclass") == 0)) {
                if (++i == args.length) errorNeedArgument(flags);
                if (providers == null) {
                    providers = new HashSet<Pair <String, String>> (3);
                }
                String providerClass = args[i];
                String providerArg = null;

                if (args.length > (i+1)) {
                    flags = args[i+1];
                    if (collator.compare(flags, "-providerarg") == 0) {
                        if (args.length == (i+2)) errorNeedArgument(flags);
                        providerArg = args[i+2];
                        i += 2;
                    }
                }
                providers.add(
                        new Pair<String, String>(providerClass, providerArg));
            }

            /*
             * options
             */
            else if (collator.compare(flags, "-v") == 0) {
                verbose = true;
            } else if (collator.compare(flags, "-debug") == 0) {
                debug = true;
            } else if (collator.compare(flags, "-rfc") == 0) {
                rfc = true;
            } else if (collator.compare(flags, "-noprompt") == 0) {
                noprompt = true;
            } else if (collator.compare(flags, "-trustcacerts") == 0) {
                trustcacerts = true;
            } else if (collator.compare(flags, "-protected") == 0 ||
                    collator.compare(flags, "-destprotected") == 0) {
                protectedPath = true;
            } else if (collator.compare(flags, "-srcprotected") == 0) {
                srcprotectedPath = true;
            } else  {
                System.err.println(rb.getString("Illegal option:  ") + flags);
                tinyHelp();
            }
        }

        if (i<args.length) {
            MessageFormat form = new MessageFormat
                (rb.getString("Usage error, <arg> is not a legal command"));
            Object[] source = {args[i]};
            throw new RuntimeException(form.format(source));
        }

        if (command == -1) {
            System.err.println(rb.getString("Usage error: no command provided"));
            tinyHelp();
        }
    }

    /**
     * Execute the commands.
     */
    void doCommands(PrintStream out) throws Exception {

        if (storetype == null) {
            storetype = KeyStore.getDefaultType();
        }
        storetype = KeyStoreUtil.niceStoreTypeName(storetype);

        if (srcstoretype == null) {
            srcstoretype = KeyStore.getDefaultType();
        }
        srcstoretype = KeyStoreUtil.niceStoreTypeName(srcstoretype);

        if (P11KEYSTORE.equalsIgnoreCase(storetype) ||
                KeyStoreUtil.isWindowsKeyStore(storetype)) {
            token = true;
            if (ksfname == null) {
                ksfname = NONE;
            }
        }
        if (NONE.equals(ksfname)) {
            nullStream = true;
        }

        if (token && !nullStream) {
            System.err.println(MessageFormat.format(rb.getString
                ("-keystore must be NONE if -storetype is {0}"), storetype));
            System.err.println();
            tinyHelp();
        }

        if (token &&
            (command == KEYPASSWD || command == STOREPASSWD)) {
            throw new UnsupportedOperationException(MessageFormat.format(rb.getString
                        ("-storepasswd and -keypasswd commands not supported " +
                        "if -storetype is {0}"), storetype));
        }

        if (P12KEYSTORE.equalsIgnoreCase(storetype) && command == KEYPASSWD) {
            throw new UnsupportedOperationException(rb.getString
                        ("-keypasswd commands not supported " +
                        "if -storetype is PKCS12"));
        }

        if (token && (keyPass != null || newPass != null || destKeyPass != null)) {
            throw new IllegalArgumentException(MessageFormat.format(rb.getString
                ("-keypass and -new " +
                "can not be specified if -storetype is {0}"), storetype));
        }

        if (protectedPath) {
            if (storePass != null || keyPass != null ||
                    newPass != null || destKeyPass != null) {
                throw new IllegalArgumentException(rb.getString
                        ("if -protected is specified, " +
                        "then -storepass, -keypass, and -new " +
                        "must not be specified"));
            }
        }

        if (srcprotectedPath) {
            if (srcstorePass != null || srckeyPass != null) {
                throw new IllegalArgumentException(rb.getString
                        ("if -srcprotected is specified, " +
                        "then -srcstorepass and -srckeypass " +
                        "must not be specified"));
            }
        }

        if (KeyStoreUtil.isWindowsKeyStore(storetype)) {
            if (storePass != null || keyPass != null ||
                    newPass != null || destKeyPass != null) {
                throw new IllegalArgumentException(rb.getString
                        ("if keystore is not password protected, " +
                        "then -storepass, -keypass, and -new " +
                        "must not be specified"));
            }
        }

        if (KeyStoreUtil.isWindowsKeyStore(srcstoretype)) {
            if (srcstorePass != null || srckeyPass != null) {
                throw new IllegalArgumentException(rb.getString
                        ("if source keystore is not password protected, " +
                        "then -srcstorepass and -srckeypass " +
                        "must not be specified"));
            }
        }

        if (validity <= (long)0) {
            throw new Exception
                (rb.getString("Validity must be greater than zero"));
        }

        // Try to load and install specified provider
        if (providers != null) {
            ClassLoader cl = null;
            if (pathlist != null) {
                String path = null;
                path = PathList.appendPath(
                        path, System.getProperty("java.class.path"));
                path = PathList.appendPath(
                        path, System.getProperty("env.class.path"));
                path = PathList.appendPath(path, pathlist);

                URL[] urls = PathList.pathToURLs(path);
                cl = new URLClassLoader(urls);
            } else {
                cl = ClassLoader.getSystemClassLoader();
            }

            for (Pair <String, String> provider: providers) {
                String provName = provider.fst;
                Class<?> provClass;
                if (cl != null) {
                    provClass = cl.loadClass(provName);
                } else {
                    provClass = Class.forName(provName);
                }

                String provArg = provider.snd;
                Object obj;
                if (provArg == null) {
                    obj = provClass.newInstance();
                } else {
                    Constructor<?> c = provClass.getConstructor(PARAM_STRING);
                    obj = c.newInstance(provArg);
                }
                if (!(obj instanceof Provider)) {
                    MessageFormat form = new MessageFormat
                        (rb.getString("provName not a provider"));
                    Object[] source = {provName};
                    throw new Exception(form.format(source));
                }
                Security.addProvider((Provider)obj);
            }
        }

        if (command == LIST && verbose && rfc) {
            System.err.println(rb.getString
                ("Must not specify both -v and -rfc with 'list' command"));
            tinyHelp();
        }

        // Make sure provided passwords are at least 6 characters long
        if (command == GENKEYPAIR && keyPass!=null && keyPass.length < 6) {
            throw new Exception(rb.getString
                ("Key password must be at least 6 characters"));
        }
        if (newPass != null && newPass.length < 6) {
            throw new Exception(rb.getString
                ("New password must be at least 6 characters"));
        }
        if (destKeyPass != null && destKeyPass.length < 6) {
            throw new Exception(rb.getString
                ("New password must be at least 6 characters"));
        }

        // Check if keystore exists.
        // If no keystore has been specified at the command line, try to use
        // the default, which is located in $HOME/.keystore.
        // If the command is "genkey", "identitydb", "import", or "printcert",
        // it is OK not to have a keystore.
        if (command != PRINTCERT) {
            if (ksfname == null) {
                ksfname = System.getProperty("user.home") + File.separator
                    + ".keystore";
            }

            if (!nullStream) {
                try {
                    ksfile = new File(ksfname);
                    // Check if keystore file is empty
                    if (ksfile.exists() && ksfile.length() == 0) {
                        throw new Exception(rb.getString
                        ("Keystore file exists, but is empty: ") + ksfname);
                    }
                    ksStream = new FileInputStream(ksfile);
                } catch (FileNotFoundException e) {
                    if (command != GENKEYPAIR &&
                        command != GENSECKEY &&
                        command != IDENTITYDB &&
                        command != IMPORTCERT &&
                        command != IMPORTKEYSTORE) {
                        throw new Exception(rb.getString
                                ("Keystore file does not exist: ") + ksfname);
                    }
                }
            }
        }

        if ((command == KEYCLONE || command == CHANGEALIAS)
                && dest == null) {
            dest = getAlias("destination");
            if ("".equals(dest)) {
                throw new Exception(rb.getString
                        ("Must specify destination alias"));
            }
        }

        if (command == DELETE && alias == null) {
            alias = getAlias(null);
            if ("".equals(alias)) {
                throw new Exception(rb.getString("Must specify alias"));
            }
        }

        // Create new keystore
        if (providerName == null) {
            keyStore = KeyStore.getInstance(storetype);
        } else {
            keyStore = KeyStore.getInstance(storetype, providerName);
        }

        /*
         * Load the keystore data.
         *
         * At this point, it's OK if no keystore password has been provided.
         * We want to make sure that we can load the keystore data, i.e.,
         * the keystore data has the right format. If we cannot load the
         * keystore, why bother asking the user for his or her password?
         * Only if we were able to load the keystore, and no keystore
         * password has been provided, will we prompt the user for the
         * keystore password to verify the keystore integrity.
         * This means that the keystore is loaded twice: first load operation
         * checks the keystore format, second load operation verifies the
         * keystore integrity.
         *
         * If the keystore password has already been provided (at the
         * command line), however, the keystore is loaded only once, and the
         * keystore format and integrity are checked "at the same time".
         *
         * Null stream keystores are loaded later.
         */
        if (!nullStream) {
            keyStore.load(ksStream, storePass);
            if (ksStream != null) {
                ksStream.close();
            }
        }

        // All commands that create or modify the keystore require a keystore
        // password.

        if (nullStream && storePass != null) {
            keyStore.load(null, storePass);
        } else if (!nullStream && storePass != null) {
            // If we are creating a new non nullStream-based keystore,
            // insist that the password be at least 6 characters
            if (ksStream == null && storePass.length < 6) {
                throw new Exception(rb.getString
                        ("Keystore password must be at least 6 characters"));
            }
        } else if (storePass == null) {

            // only prompt if (protectedPath == false)

            if (!protectedPath && !KeyStoreUtil.isWindowsKeyStore(storetype) &&
                (command == CERTREQ ||
                        command == DELETE ||
                        command == GENKEYPAIR ||
                        command == GENSECKEY ||
                        command == IMPORTCERT ||
                        command == IMPORTKEYSTORE ||
                        command == KEYCLONE ||
                        command == CHANGEALIAS ||
                        command == SELFCERT ||
                        command == STOREPASSWD ||
                        command == KEYPASSWD ||
                        command == IDENTITYDB)) {
                int count = 0;
                do {
                    if (command == IMPORTKEYSTORE) {
                        System.err.print
                                (rb.getString("Enter destination keystore password:  "));
                    } else {
                        System.err.print
                                (rb.getString("Enter keystore password:  "));
                    }
                    System.err.flush();
                    storePass = Password.readPassword(System.in);
                    passwords.add(storePass);

                    // If we are creating a new non nullStream-based keystore,
                    // insist that the password be at least 6 characters
                    if (!nullStream && (storePass == null || storePass.length < 6)) {
                        System.err.println(rb.getString
                                ("Keystore password is too short - " +
                                "must be at least 6 characters"));
                        storePass = null;
                    }

                    // If the keystore file does not exist and needs to be
                    // created, the storepass should be prompted twice.
                    if (storePass != null && !nullStream && ksStream == null) {
                        System.err.print(rb.getString("Re-enter new password: "));
                        char[] storePassAgain = Password.readPassword(System.in);
                        passwords.add(storePassAgain);
                        if (!Arrays.equals(storePass, storePassAgain)) {
                            System.err.println
                                (rb.getString("They don't match. Try again"));
                            storePass = null;
                        }
                    }

                    count++;
                } while ((storePass == null) && count < 3);


                if (storePass == null) {
                    System.err.println
                        (rb.getString("Too many failures - try later"));
                    return;
                }
            } else if (!protectedPath
                    && !KeyStoreUtil.isWindowsKeyStore(storetype)
                    && !(command == PRINTCERT)) {
                // here we have EXPORTCERT and LIST (info valid until STOREPASSWD)
                System.err.print(rb.getString("Enter keystore password:  "));
                System.err.flush();
                storePass = Password.readPassword(System.in);
                passwords.add(storePass);
            }

            // Now load a nullStream-based keystore,
            // or verify the integrity of an input stream-based keystore
            if (nullStream) {
                keyStore.load(null, storePass);
            } else if (ksStream != null) {
                ksStream = new FileInputStream(ksfile);
                keyStore.load(ksStream, storePass);
                ksStream.close();
            }
        }

        if (storePass != null && P12KEYSTORE.equalsIgnoreCase(storetype)) {
            MessageFormat form = new MessageFormat(rb.getString(
                "Warning:  Different store and key passwords not supported " +
                "for PKCS12 KeyStores. Ignoring user-specified <command> value."));
            if (keyPass != null && !Arrays.equals(storePass, keyPass)) {
                Object[] source = {"-keypass"};
                System.err.println(form.format(source));
                keyPass = storePass;
            }
            if (newPass != null && !Arrays.equals(storePass, newPass)) {
                Object[] source = {"-new"};
                System.err.println(form.format(source));
                newPass = storePass;
            }
            if (destKeyPass != null && !Arrays.equals(storePass, destKeyPass)) {
                Object[] source = {"-destkeypass"};
                System.err.println(form.format(source));
                destKeyPass = storePass;
            }
        }

        // Create a certificate factory
        if (command == PRINTCERT || command == IMPORTCERT
               || command == IDENTITYDB) {
            cf = CertificateFactory.getInstance("X509");
        }

        if (trustcacerts) {
            caks = getCacertsKeyStore();
        }

        // Perform the specified command
        if (command == CERTREQ) {
            PrintStream ps = null;
            if (filename != null) {
                ps = new PrintStream(new FileOutputStream
                                                 (filename));
                out = ps;
            }
            try {
                doCertReq(alias, sigAlgName, out);
            } finally {
                if (ps != null) {
                    ps.close();
                }
            }
            if (verbose && filename != null) {
                MessageFormat form = new MessageFormat(rb.getString
                        ("Certification request stored in file <filename>"));
                Object[] source = {filename};
                System.err.println(form.format(source));
                System.err.println(rb.getString("Submit this to your CA"));
            }
        } else if (command == DELETE) {
            doDeleteEntry(alias);
            kssave = true;
        } else if (command == EXPORTCERT) {
            PrintStream ps = null;
            if (filename != null) {
                ps = new PrintStream(new FileOutputStream
                                                 (filename));
                out = ps;
            }
            try {
                doExportCert(alias, out);
            } finally {
                if (ps != null) {
                    ps.close();
                }
            }
            if (filename != null) {
                MessageFormat form = new MessageFormat(rb.getString
                        ("Certificate stored in file <filename>"));
                Object[] source = {filename};
                System.err.println(form.format(source));
            }
        } else if (command == GENKEYPAIR) {
            if (keyAlgName == null) {
                keyAlgName = "DSA";
            }
            doGenKeyPair(alias, dname, keyAlgName, keysize, sigAlgName);
            kssave = true;
        } else if (command == GENSECKEY) {
            if (keyAlgName == null) {
                keyAlgName = "DES";
            }
            doGenSecretKey(alias, keyAlgName, keysize);
            kssave = true;
        } else if (command == IDENTITYDB) {
            InputStream inStream = System.in;
            if (filename != null) {
                inStream = new FileInputStream(filename);
            }
            try {
                doImportIdentityDatabase(inStream);
            } finally {
                if (inStream != System.in) {
                    inStream.close();
                }
            }
        } else if (command == IMPORTCERT) {
            InputStream inStream = System.in;
            if (filename != null) {
                inStream = new FileInputStream(filename);
            }
            try {
                String importAlias = (alias!=null)?alias:keyAlias;
                if (keyStore.entryInstanceOf(importAlias, KeyStore.PrivateKeyEntry.class)) {
                    kssave = installReply(importAlias, inStream);
                    if (kssave) {
                        System.err.println(rb.getString
                            ("Certificate reply was installed in keystore"));
                    } else {
                        System.err.println(rb.getString
                            ("Certificate reply was not installed in keystore"));
                    }
                } else if (!keyStore.containsAlias(importAlias) ||
                        keyStore.entryInstanceOf(importAlias,
                            KeyStore.TrustedCertificateEntry.class)) {
                    kssave = addTrustedCert(importAlias, inStream);
                    if (kssave) {
                        System.err.println(rb.getString
                            ("Certificate was added to keystore"));
                    } else {
                        System.err.println(rb.getString
                            ("Certificate was not added to keystore"));
                    }
                }
            } finally {
                if (inStream != System.in) {
                    inStream.close();
                }
            }
        } else if (command == IMPORTKEYSTORE) {
            doImportKeyStore();
            kssave = true;
        } else if (command == KEYCLONE) {
            keyPassNew = newPass;

            // added to make sure only key can go thru
            if (alias == null) {
                alias = keyAlias;
            }
            if (keyStore.containsAlias(alias) == false) {
                MessageFormat form = new MessageFormat
                    (rb.getString("Alias <alias> does not exist"));
                Object[] source = {alias};
                throw new Exception(form.format(source));
            }
            if (!keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                MessageFormat form = new MessageFormat(rb.getString(
                        "Alias <alias> references an entry type that is not a private key entry.  " +
                        "The -keyclone command only supports cloning of private key entries"));
                Object[] source = {alias};
                throw new Exception(form.format(source));
            }

            doCloneEntry(alias, dest, true);  // Now everything can be cloned
            kssave = true;
        } else if (command == CHANGEALIAS) {
            if (alias == null) {
                alias = keyAlias;
            }
            doCloneEntry(alias, dest, false);
            // in PKCS11, clone a PrivateKeyEntry will delete the old one
            if (keyStore.containsAlias(alias)) {
                doDeleteEntry(alias);
            }
            kssave = true;
        } else if (command == KEYPASSWD) {
            keyPassNew = newPass;
            doChangeKeyPasswd(alias);
            kssave = true;
        } else if (command == LIST) {
            if (alias != null) {
                doPrintEntry(alias, out, true);
            } else {
                doPrintEntries(out);
            }
        } else if (command == PRINTCERT) {
            doPrintCert(out);
        } else if (command == SELFCERT) {
            doSelfCert(alias, dname, sigAlgName);
            kssave = true;
        } else if (command == STOREPASSWD) {
            storePassNew = newPass;
            if (storePassNew == null) {
                storePassNew = getNewPasswd("keystore password", storePass);
            }
            kssave = true;
        }

        // If we need to save the keystore, do so.
        if (kssave) {
            if (verbose) {
                MessageFormat form = new MessageFormat
                        (rb.getString("[Storing ksfname]"));
                Object[] source = {nullStream ? "keystore" : ksfname};
                System.err.println(form.format(source));
            }

            if (token) {
                keyStore.store(null, null);
            } else {
                FileOutputStream fout = null;
                try {
                    fout = (nullStream ?
                                        (FileOutputStream)null :
                                        new FileOutputStream(ksfname));
                    keyStore.store
                        (fout,
                        (storePassNew!=null) ? storePassNew : storePass);
                } finally {
                    if (fout != null) {
                        fout.close();
                    }
                }
            }
        }
    }

    /**
     * Creates a PKCS#10 cert signing request, corresponding to the
     * keys (and name) associated with a given alias.
     */
    private void doCertReq(String alias, String sigAlgName, PrintStream out)
        throws Exception
    {
        if (alias == null) {
            alias = keyAlias;
        }

        Object[] objs = recoverKey(alias, storePass, keyPass);
        PrivateKey privKey = (PrivateKey)objs[0];
        if (keyPass == null) {
            keyPass = (char[])objs[1];
        }

        Certificate cert = keyStore.getCertificate(alias);
        if (cert == null) {
            MessageFormat form = new MessageFormat
                (rb.getString("alias has no public key (certificate)"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }
        PKCS10 request = new PKCS10(cert.getPublicKey());

        // Construct an X500Signer object, so that we can sign the request
        if (sigAlgName == null) {
            // If no signature algorithm was specified at the command line,
            // we choose one that is compatible with the selected private key
            String keyAlgName = privKey.getAlgorithm();
            if ("DSA".equalsIgnoreCase(keyAlgName)
                   || "DSS".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1WithDSA";
            } else if ("RSA".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1WithRSA";
            } else {
                throw new Exception(rb.getString
                        ("Cannot derive signature algorithm"));
            }
        }

        Signature signature = Signature.getInstance(sigAlgName);
        signature.initSign(privKey);
        X500Name subject =
            new X500Name(((X509Certificate)cert).getSubjectDN().toString());
        X500Signer signer = new X500Signer(signature, subject);

        // Sign the request and base-64 encode it
        request.encodeAndSign(signer);
        request.print(out);
    }

    /**
     * Deletes an entry from the keystore.
     */
    private void doDeleteEntry(String alias) throws Exception {
        if (keyStore.containsAlias(alias) == false) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> does not exist"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }
        keyStore.deleteEntry(alias);
    }

    /**
     * Exports a certificate from the keystore.
     */
    private void doExportCert(String alias, PrintStream out)
        throws Exception
    {
        if (storePass == null
                && !KeyStoreUtil.isWindowsKeyStore(storetype)) {
            printWarning();
        }
        if (alias == null) {
            alias = keyAlias;
        }
        if (keyStore.containsAlias(alias) == false) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> does not exist"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        X509Certificate cert = (X509Certificate)keyStore.getCertificate(alias);
        if (cert == null) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> has no certificate"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }
        dumpCert(cert, out);
    }

    /**
     * Prompt the user for a keypass when generating a key entry.
     * @param alias the entry we will set password for
     * @param orig the original entry of doing a dup, null if generate new
     * @param origPass the password to copy from if user press ENTER
     */
    private char[] promptForKeyPass(String alias, String orig, char[] origPass) throws Exception{
        if (P12KEYSTORE.equalsIgnoreCase(storetype)) {
            return origPass;
        } else if (!token) {
            // Prompt for key password
            int count;
            for (count = 0; count < 3; count++) {
                MessageFormat form = new MessageFormat(rb.getString
                        ("Enter key password for <alias>"));
                Object[] source = {alias};
                System.err.println(form.format(source));
                if (orig == null) {
                    System.err.print(rb.getString
                            ("\t(RETURN if same as keystore password):  "));
                } else {
                    form = new MessageFormat(rb.getString
                            ("\t(RETURN if same as for <otherAlias>)"));
                    Object[] src = {orig};
                    System.err.print(form.format(src));
                }
                System.err.flush();
                char[] entered = Password.readPassword(System.in);
                passwords.add(entered);
                if (entered == null) {
                    return origPass;
                } else if (entered.length >= 6) {
                    System.err.print(rb.getString("Re-enter new password: "));
                    char[] passAgain = Password.readPassword(System.in);
                    passwords.add(passAgain);
                    if (!Arrays.equals(entered, passAgain)) {
                        System.err.println
                            (rb.getString("They don't match. Try again"));
                        continue;
                    }
                    return entered;
                } else {
                    System.err.println(rb.getString
                        ("Key password is too short - must be at least 6 characters"));
                }
            }
            if (count == 3) {
                if (command == KEYCLONE) {
                    throw new Exception(rb.getString
                        ("Too many failures. Key entry not cloned"));
                } else {
                    throw new Exception(rb.getString
                            ("Too many failures - key not added to keystore"));
                }
            }
        }
        return null;    // PKCS11
    }
    /**
     * Creates a new secret key.
     */
    private void doGenSecretKey(String alias, String keyAlgName,
                              int keysize)
        throws Exception
    {
        if (alias == null) {
            alias = keyAlias;
        }
        if (keyStore.containsAlias(alias)) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Secret key not generated, alias <alias> already exists"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        SecretKey secKey = null;
        KeyGenerator keygen = KeyGenerator.getInstance(keyAlgName);
        if (keysize != -1) {
            keygen.init(keysize);
        } else if ("DES".equalsIgnoreCase(keyAlgName)) {
            keygen.init(56);
        } else if ("DESede".equalsIgnoreCase(keyAlgName)) {
            keygen.init(168);
        } else {
            throw new Exception(rb.getString
                ("Please provide -keysize for secret key generation"));
        }

        secKey = keygen.generateKey();
        if (keyPass == null) {
            keyPass = promptForKeyPass(alias, null, storePass);
        }
        keyStore.setKeyEntry(alias, secKey, keyPass, null);
    }

    /**
     * Creates a new key pair and self-signed certificate.
     */
    private void doGenKeyPair(String alias, String dname, String keyAlgName,
                              int keysize, String sigAlgName)
        throws Exception
    {
        if (keysize == -1) {
            if ("EC".equalsIgnoreCase(keyAlgName)) {
                keysize = 256;
            } else {
                keysize = 1024;
            }
        }

        if (alias == null) {
            alias = keyAlias;
        }

        if (keyStore.containsAlias(alias)) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Key pair not generated, alias <alias> already exists"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        if (sigAlgName == null) {
            if ("DSA".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1WithDSA";
            } else if ("RSA".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1WithRSA";
            } else if ("EC".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1withECDSA";
            } else {
                throw new Exception(rb.getString
                        ("Cannot derive signature algorithm"));
            }
        }
        CertAndKeyGen keypair =
                new CertAndKeyGen(keyAlgName, sigAlgName, providerName);


        // If DN is provided, parse it. Otherwise, prompt the user for it.
        X500Name x500Name;
        if (dname == null) {
            x500Name = getX500Name();
        } else {
            x500Name = new X500Name(dname);
        }

        keypair.generate(keysize);
        PrivateKey privKey = keypair.getPrivateKey();

        X509Certificate[] chain = new X509Certificate[1];
        chain[0] = keypair.getSelfCertificate(
                x500Name, getStartDate(startDate), validity*24L*60L*60L);

        if (verbose) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Generating keysize bit keyAlgName key pair and self-signed certificate " +
                    "(sigAlgName) with a validity of validality days\n\tfor: x500Name"));
            Object[] source = {new Integer(keysize),
                                privKey.getAlgorithm(),
                                chain[0].getSigAlgName(),
                                new Long(validity),
                                x500Name};
            System.err.println(form.format(source));
        }

        if (keyPass == null) {
            keyPass = promptForKeyPass(alias, null, storePass);
        }
        keyStore.setKeyEntry(alias, privKey, keyPass, chain);
    }

    /**
     * Clones an entry
     * @param orig original alias
     * @param dest destination alias
     * @changePassword if the password can be changed
     */
    private void doCloneEntry(String orig, String dest, boolean changePassword)
        throws Exception
    {
        if (orig == null) {
            orig = keyAlias;
        }

        if (keyStore.containsAlias(dest)) {
            MessageFormat form = new MessageFormat
                (rb.getString("Destination alias <dest> already exists"));
            Object[] source = {dest};
            throw new Exception(form.format(source));
        }

        Object[] objs = recoverEntry(keyStore, orig, storePass, keyPass);
        Entry entry = (Entry)objs[0];
        keyPass = (char[])objs[1];

        PasswordProtection pp = null;

        if (keyPass != null) {  // protected
            if (!changePassword || P12KEYSTORE.equalsIgnoreCase(storetype)) {
                keyPassNew = keyPass;
            } else {
                if (keyPassNew == null) {
                    keyPassNew = promptForKeyPass(dest, orig, keyPass);
                }
            }
            pp = new PasswordProtection(keyPassNew);
        }
        keyStore.setEntry(dest, entry, pp);
    }

    /**
     * Changes a key password.
     */
    private void doChangeKeyPasswd(String alias) throws Exception
    {

        if (alias == null) {
            alias = keyAlias;
        }
        Object[] objs = recoverKey(alias, storePass, keyPass);
        Key privKey = (Key)objs[0];
        if (keyPass == null) {
            keyPass = (char[])objs[1];
        }

        if (keyPassNew == null) {
            MessageFormat form = new MessageFormat
                (rb.getString("key password for <alias>"));
            Object[] source = {alias};
            keyPassNew = getNewPasswd(form.format(source), keyPass);
        }
        keyStore.setKeyEntry(alias, privKey, keyPassNew,
                             keyStore.getCertificateChain(alias));
    }

    /**
     * Imports a JDK 1.1-style identity database. We can only store one
     * certificate per identity, because we use the identity's name as the
     * alias (which references a keystore entry), and aliases must be unique.
     */
    private void doImportIdentityDatabase(InputStream in)
        throws Exception
    {
        byte[] encoded;
        ByteArrayInputStream bais;
        java.security.cert.X509Certificate newCert;
        java.security.cert.Certificate[] chain = null;
        PrivateKey privKey;
        boolean modified = false;

        IdentityDatabase idb = IdentityDatabase.fromStream(in);
        for (Enumeration<Identity> enum_ = idb.identities();
                                        enum_.hasMoreElements();) {
            Identity id = enum_.nextElement();
            newCert = null;
            // only store trusted identities in keystore
            if ((id instanceof SystemSigner && ((SystemSigner)id).isTrusted())
                || (id instanceof SystemIdentity
                    && ((SystemIdentity)id).isTrusted())) {
                // ignore if keystore entry with same alias name already exists
                if (keyStore.containsAlias(id.getName())) {
                    MessageFormat form = new MessageFormat
                        (rb.getString("Keystore entry for <id.getName()> already exists"));
                    Object[] source = {id.getName()};
                    System.err.println(form.format(source));
                    continue;
                }
                java.security.Certificate[] certs = id.certificates();
                if (certs!=null && certs.length>0) {
                    // we can only store one user cert per identity.
                    // convert old-style to new-style cert via the encoding
                    DerOutputStream dos = new DerOutputStream();
                    certs[0].encode(dos);
                    encoded = dos.toByteArray();
                    bais = new ByteArrayInputStream(encoded);
                    newCert = (X509Certificate)cf.generateCertificate(bais);
                    bais.close();

                    // if certificate is self-signed, make sure it verifies
                    if (isSelfSigned(newCert)) {
                        PublicKey pubKey = newCert.getPublicKey();
                        try {
                            newCert.verify(pubKey);
                        } catch (Exception e) {
                            // ignore this cert
                            continue;
                        }
                    }

                    if (id instanceof SystemSigner) {
                        MessageFormat form = new MessageFormat(rb.getString
                            ("Creating keystore entry for <id.getName()> ..."));
                        Object[] source = {id.getName()};
                        System.err.println(form.format(source));
                        if (chain==null) {
                            chain = new java.security.cert.Certificate[1];
                        }
                        chain[0] = newCert;
                        privKey = ((SystemSigner)id).getPrivateKey();
                        keyStore.setKeyEntry(id.getName(), privKey, storePass,
                                             chain);
                    } else {
                        keyStore.setCertificateEntry(id.getName(), newCert);
                    }
                    kssave = true;
                }
            }
        }
        if (!kssave) {
            System.err.println(rb.getString
                ("No entries from identity database added"));
        }
    }

    /**
     * Prints a single keystore entry.
     */
    private void doPrintEntry(String alias, PrintStream out,
                              boolean printWarning)
        throws Exception
    {
        if (storePass == null && printWarning
                && !KeyStoreUtil.isWindowsKeyStore(storetype)) {
            printWarning();
        }

        if (keyStore.containsAlias(alias) == false) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> does not exist"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        if (verbose || rfc || debug) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias name: alias"));
            Object[] source = {alias};
            out.println(form.format(source));

            if (!token) {
                form = new MessageFormat(rb.getString
                    ("Creation date: keyStore.getCreationDate(alias)"));
                Object[] src = {keyStore.getCreationDate(alias)};
                out.println(form.format(src));
            }
        } else {
            if (!token) {
                MessageFormat form = new MessageFormat
                    (rb.getString("alias, keyStore.getCreationDate(alias), "));
                Object[] source = {alias, keyStore.getCreationDate(alias)};
                out.print(form.format(source));
            } else {
                MessageFormat form = new MessageFormat
                    (rb.getString("alias, "));
                Object[] source = {alias};
                out.print(form.format(source));
            }
        }

        if (keyStore.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
            if (verbose || rfc || debug) {
                Object[] source = {"SecretKeyEntry"};
                out.println(new MessageFormat(
                        rb.getString("Entry type: <type>")).format(source));
            } else {
                out.println("SecretKeyEntry, ");
            }
        } else if (keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
            if (verbose || rfc || debug) {
                Object[] source = {"PrivateKeyEntry"};
                out.println(new MessageFormat(
                        rb.getString("Entry type: <type>")).format(source));
            } else {
                out.println("PrivateKeyEntry, ");
            }

            // Get the chain
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain != null) {
                if (verbose || rfc || debug) {
                    out.println(rb.getString
                        ("Certificate chain length: ") + chain.length);
                    for (int i = 0; i < chain.length; i ++) {
                        MessageFormat form = new MessageFormat
                                (rb.getString("Certificate[(i + 1)]:"));
                        Object[] source = {new Integer((i + 1))};
                        out.println(form.format(source));
                        if (verbose && (chain[i] instanceof X509Certificate)) {
                            printX509Cert((X509Certificate)(chain[i]), out);
                        } else if (debug) {
                            out.println(chain[i].toString());
                        } else {
                            dumpCert(chain[i], out);
                        }
                    }
                } else {
                    // Print the digest of the user cert only
                    out.println
                        (rb.getString("Certificate fingerprint (SHA1): ") +
                        getCertFingerPrint("SHA1", chain[0]));
                }
            }
        } else if (keyStore.entryInstanceOf(alias,
                KeyStore.TrustedCertificateEntry.class)) {
            // We have a trusted certificate entry
            Certificate cert = keyStore.getCertificate(alias);
            if (verbose && (cert instanceof X509Certificate)) {
                out.println(rb.getString("Entry type: trustedCertEntry\n"));
                printX509Cert((X509Certificate)cert, out);
            } else if (rfc) {
                out.println(rb.getString("Entry type: trustedCertEntry\n"));
                dumpCert(cert, out);
            } else if (debug) {
                out.println(cert.toString());
            } else {
                out.println(rb.getString("trustedCertEntry,"));
                out.println(rb.getString("Certificate fingerprint (SHA1): ")
                            + getCertFingerPrint("SHA1", cert));
            }
        } else {
            out.println(rb.getString("Unknown Entry Type"));
        }
    }

    /**
     * Load the srckeystore from a stream, used in -importkeystore
     * @returns the src KeyStore
     */
    KeyStore loadSourceKeyStore() throws Exception {
        boolean isPkcs11 = false;

        InputStream is = null;

        if (P11KEYSTORE.equalsIgnoreCase(srcstoretype) ||
                KeyStoreUtil.isWindowsKeyStore(srcstoretype)) {
            if (!NONE.equals(srcksfname)) {
                System.err.println(MessageFormat.format(rb.getString
                    ("-keystore must be NONE if -storetype is {0}"), srcstoretype));
                System.err.println();
                tinyHelp();
            }
            isPkcs11 = true;
        } else {
            if (srcksfname != null) {
                File srcksfile = new File(srcksfname);
                    if (srcksfile.exists() && srcksfile.length() == 0) {
                        throw new Exception(rb.getString
                                ("Source keystore file exists, but is empty: ") +
                                srcksfname);
                }
                is = new FileInputStream(srcksfile);
            } else {
                throw new Exception(rb.getString
                        ("Please specify -srckeystore"));
            }
        }

        KeyStore store;
        try {
            if (srcProviderName == null) {
                store = KeyStore.getInstance(srcstoretype);
            } else {
                store = KeyStore.getInstance(srcstoretype, srcProviderName);
            }

            if (srcstorePass == null
                    && !srcprotectedPath
                    && !KeyStoreUtil.isWindowsKeyStore(srcstoretype)) {
                System.err.print(rb.getString("Enter source keystore password:  "));
                System.err.flush();
                srcstorePass = Password.readPassword(System.in);
                passwords.add(srcstorePass);
            }

            // always let keypass be storepass when using pkcs12
            if (P12KEYSTORE.equalsIgnoreCase(srcstoretype)) {
                if (srckeyPass != null && srcstorePass != null &&
                        !Arrays.equals(srcstorePass, srckeyPass)) {
                    MessageFormat form = new MessageFormat(rb.getString(
                        "Warning:  Different store and key passwords not supported " +
                        "for PKCS12 KeyStores. Ignoring user-specified <command> value."));
                    Object[] source = {"-srckeypass"};
                    System.err.println(form.format(source));
                    srckeyPass = srcstorePass;
                }
            }

            store.load(is, srcstorePass);   // "is" already null in PKCS11
        } finally {
            if (is != null) {
                is.close();
            }
        }

        if (srcstorePass == null
                && !KeyStoreUtil.isWindowsKeyStore(srcstoretype)) {
            // anti refactoring, copied from printWarning(),
            // but change 2 lines
            System.err.println();
            System.err.println(rb.getString
                ("*****************  WARNING WARNING WARNING  *****************"));
            System.err.println(rb.getString
                ("* The integrity of the information stored in the srckeystore*"));
            System.err.println(rb.getString
                ("* has NOT been verified!  In order to verify its integrity, *"));
            System.err.println(rb.getString
                ("* you must provide the srckeystore password.                *"));
            System.err.println(rb.getString
                ("*****************  WARNING WARNING WARNING  *****************"));
            System.err.println();
        }

        return store;
    }

    /**
     * import all keys and certs from importkeystore.
     * keep alias unchanged if no name conflict, otherwise, prompt.
     * keep keypass unchanged for keys
     */
    private void doImportKeyStore() throws Exception {

        if (alias != null) {
            doImportKeyStoreSingle(loadSourceKeyStore(), alias);
        } else {
            if (dest != null || srckeyPass != null || destKeyPass != null) {
                throw new Exception(rb.getString(
                        "if alias not specified, destalias, srckeypass, " +
                        "and destkeypass must not be specified"));
            }
            doImportKeyStoreAll(loadSourceKeyStore());
        }
        /*
         * Information display rule of -importkeystore
         * 1. inside single, shows failure
         * 2. inside all, shows sucess
         * 3. inside all where there is a failure, prompt for continue
         * 4. at the final of all, shows summary
         */
    }

    /**
     * Import a single entry named alias from srckeystore
     * @returns 1 if the import action succeed
     *          0 if user choose to ignore an alias-dumplicated entry
     *          2 if setEntry throws Exception
     */
    private int doImportKeyStoreSingle(KeyStore srckeystore, String alias)
            throws Exception {

        String newAlias = (dest==null) ? alias : dest;

        if (keyStore.containsAlias(newAlias)) {
            Object[] source = {alias};
            if (noprompt) {
                System.err.println(new MessageFormat(rb.getString(
                        "Warning: Overwriting existing alias <alias> in destination keystore")).format(source));
            } else {
                String reply = getYesNoReply(new MessageFormat(rb.getString(
                        "Existing entry alias <alias> exists, overwrite? [no]:  ")).format(source));
                if ("NO".equals(reply)) {
                    newAlias = inputStringFromStdin(rb.getString
                            ("Enter new alias name\t(RETURN to cancel import for this entry):  "));
                    if ("".equals(newAlias)) {
                        System.err.println(new MessageFormat(rb.getString(
                                "Entry for alias <alias> not imported.")).format(
                                source));
                        return 0;
                    }
                }
            }
        }

        Object[] objs = recoverEntry(srckeystore, alias, srcstorePass, srckeyPass);
        Entry entry = (Entry)objs[0];

        PasswordProtection pp = null;

        // According to keytool.html, "The destination entry will be protected
        // using destkeypass. If destkeypass is not provided, the destination
        // entry will be protected with the source entry password."
        // so always try to protect with destKeyPass.
        if (destKeyPass != null) {
            pp = new PasswordProtection(destKeyPass);
        } else if (objs[1] != null) {
            pp = new PasswordProtection((char[])objs[1]);
        }

        try {
            keyStore.setEntry(newAlias, entry, pp);
            return 1;
        } catch (KeyStoreException kse) {
            Object[] source2 = {alias, kse.toString()};
            MessageFormat form = new MessageFormat(rb.getString(
                    "Problem importing entry for alias <alias>: <exception>.\nEntry for alias <alias> not imported."));
            System.err.println(form.format(source2));
            return 2;
        }
    }

    private void doImportKeyStoreAll(KeyStore srckeystore) throws Exception {

        int ok = 0;
        int count = srckeystore.size();
        for (Enumeration<String> e = srckeystore.aliases();
                                        e.hasMoreElements(); ) {
            String alias = e.nextElement();
            int result = doImportKeyStoreSingle(srckeystore, alias);
            if (result == 1) {
                ok++;
                Object[] source = {alias};
                MessageFormat form = new MessageFormat(rb.getString("Entry for alias <alias> successfully imported."));
                System.err.println(form.format(source));
            } else if (result == 2) {
                if (!noprompt) {
                    String reply = getYesNoReply("Do you want to quit the import process? [no]:  ");
                    if ("YES".equals(reply)) {
                        break;
                    }
                }
            }
        }
        Object[] source = {ok, count-ok};
        MessageFormat form = new MessageFormat(rb.getString(
                "Import command completed:  <ok> entries successfully imported, <fail> entries failed or cancelled"));
        System.err.println(form.format(source));
    }

    /**
     * Prints all keystore entries.
     */
    private void doPrintEntries(PrintStream out)
        throws Exception
    {
        if (storePass == null
                && !KeyStoreUtil.isWindowsKeyStore(storetype)) {
            printWarning();
        } else {
            out.println();
        }

        out.println(rb.getString("Keystore type: ") + keyStore.getType());
        out.println(rb.getString("Keystore provider: ") +
                keyStore.getProvider().getName());
        out.println();

        MessageFormat form;
        form = (keyStore.size() == 1) ?
                new MessageFormat(rb.getString
                        ("Your keystore contains keyStore.size() entry")) :
                new MessageFormat(rb.getString
                        ("Your keystore contains keyStore.size() entries"));
        Object[] source = {new Integer(keyStore.size())};
        out.println(form.format(source));
        out.println();

        for (Enumeration<String> e = keyStore.aliases();
                                        e.hasMoreElements(); ) {
            String alias = e.nextElement();
            doPrintEntry(alias, out, false);
            if (verbose || rfc) {
                out.println(rb.getString("\n"));
                out.println(rb.getString
                        ("*******************************************"));
                out.println(rb.getString
                        ("*******************************************\n\n"));
            }
        }
    }

    /**
     * Reads a certificate (or certificate chain) and prints its contents in
     * a human readbable format.
     */
    private void printCertFromStream(InputStream in, PrintStream out)
        throws Exception
    {
        Collection<? extends Certificate> c = null;
        try {
            c = cf.generateCertificates(in);
        } catch (CertificateException ce) {
            throw new Exception(rb.getString("Failed to parse input"), ce);
        }
        if (c.isEmpty()) {
            throw new Exception(rb.getString("Empty input"));
        }
        Certificate[] certs = c.toArray(new Certificate[c.size()]);
        for (int i=0; i<certs.length; i++) {
            X509Certificate x509Cert = null;
            try {
                x509Cert = (X509Certificate)certs[i];
            } catch (ClassCastException cce) {
                throw new Exception(rb.getString("Not X.509 certificate"));
            }
            if (certs.length > 1) {
                MessageFormat form = new MessageFormat
                        (rb.getString("Certificate[(i + 1)]:"));
                Object[] source = {new Integer(i + 1)};
                out.println(form.format(source));
            }
            if (rfc) dumpCert(x509Cert, out);
            else printX509Cert(x509Cert, out);
            if (i < (certs.length-1)) {
                out.println();
            }
        }
    }

    private void doPrintCert(final PrintStream out) throws Exception {
        if (sslserver != null) {
            SSLContext sc = SSLContext.getInstance("SSL");
            final boolean[] certPrinted = new boolean[1];
            sc.init(null, new TrustManager[] {
                new X509TrustManager() {

                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        for (int i=0; i<certs.length; i++) {
                            X509Certificate cert = certs[i];
                            try {
                                if (rfc) {
                                    dumpCert(cert, out);
                                } else {
                                    out.println("Certificate #" + i);
                                    out.println("====================================");
                                    printX509Cert(cert, out);
                                    out.println();
                                }
                            } catch (Exception e) {
                                if (debug) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // Set to true where there's something to print
                        if (certs.length > 0) {
                            certPrinted[0] = true;
                        }
                    }
                }
            }, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(
                    new HostnameVerifier() {
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
            // HTTPS instead of raw SSL, so that -Dhttps.proxyHost and
            // -Dhttps.proxyPort can be used. Since we only go through
            // the handshake process, an HTTPS server is not needed.
            // This program should be able to deal with any SSL-based
            // network service.
            Exception ex = null;
            try {
                new URL("https://" + sslserver).openConnection().connect();
            } catch (Exception e) {
                ex = e;
            }
            // If the certs are not printed out, we consider it an error even
            // if the URL connection is successful.
            if (!certPrinted[0]) {
                Exception e = new Exception(
                        rb.getString("No certificate from the SSL server"));
                if (ex != null) {
                    e.initCause(ex);
                }
                throw e;
            }
        } else {
            InputStream inStream = System.in;
            if (filename != null) {
                inStream = new FileInputStream(filename);
            }
            try {
                printCertFromStream(inStream, out);
            } finally {
                if (inStream != System.in) {
                    inStream.close();
                }
            }
        }
    }
    /**
     * Creates a self-signed certificate, and stores it as a single-element
     * certificate chain.
     */
    private void doSelfCert(String alias, String dname, String sigAlgName)
        throws Exception
    {
        if (alias == null) {
            alias = keyAlias;
        }

        Object[] objs = recoverKey(alias, storePass, keyPass);
        PrivateKey privKey = (PrivateKey)objs[0];
        if (keyPass == null)
            keyPass = (char[])objs[1];

        // Determine the signature algorithm
        if (sigAlgName == null) {
            // If no signature algorithm was specified at the command line,
            // we choose one that is compatible with the selected private key
            String keyAlgName = privKey.getAlgorithm();
            if ("DSA".equalsIgnoreCase(keyAlgName)
                   || "DSS".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1WithDSA";
            } else if ("RSA".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1WithRSA";
            } else if ("EC".equalsIgnoreCase(keyAlgName)) {
                sigAlgName = "SHA1withECDSA";
            } else {
                throw new Exception
                        (rb.getString("Cannot derive signature algorithm"));
            }
        }

        // Get the old certificate
        Certificate oldCert = keyStore.getCertificate(alias);
        if (oldCert == null) {
            MessageFormat form = new MessageFormat
                (rb.getString("alias has no public key"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }
        if (!(oldCert instanceof X509Certificate)) {
            MessageFormat form = new MessageFormat
                (rb.getString("alias has no X.509 certificate"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        // convert to X509CertImpl, so that we can modify selected fields
        // (no public APIs available yet)
        byte[] encoded = oldCert.getEncoded();
        X509CertImpl certImpl = new X509CertImpl(encoded);
        X509CertInfo certInfo = (X509CertInfo)certImpl.get(X509CertImpl.NAME
                                                           + "." +
                                                           X509CertImpl.INFO);

        // Extend its validity
        Date firstDate = getStartDate(startDate);
        Date lastDate = new Date();
        lastDate.setTime(firstDate.getTime() + validity*1000L*24L*60L*60L);
        CertificateValidity interval = new CertificateValidity(firstDate,
                                                               lastDate);
        certInfo.set(X509CertInfo.VALIDITY, interval);

        // Make new serial number
        certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber
                     ((int)(firstDate.getTime()/1000)));

        // Set owner and issuer fields
        X500Name owner;
        if (dname == null) {
            // Get the owner name from the certificate
            owner = (X500Name)certInfo.get(X509CertInfo.SUBJECT + "." +
                                           CertificateSubjectName.DN_NAME);
        } else {
            // Use the owner name specified at the command line
            owner = new X500Name(dname);
            certInfo.set(X509CertInfo.SUBJECT + "." +
                         CertificateSubjectName.DN_NAME, owner);
        }
        // Make issuer same as owner (self-signed!)
        certInfo.set(X509CertInfo.ISSUER + "." +
                     CertificateIssuerName.DN_NAME, owner);

        // The inner and outer signature algorithms have to match.
        // The way we achieve that is really ugly, but there seems to be no
        // other solution: We first sign the cert, then retrieve the
        // outer sigalg and use it to set the inner sigalg
        X509CertImpl newCert = new X509CertImpl(certInfo);
        newCert.sign(privKey, sigAlgName);
        AlgorithmId sigAlgid = (AlgorithmId)newCert.get(X509CertImpl.SIG_ALG);
        certInfo.set(CertificateAlgorithmId.NAME + "." +
                     CertificateAlgorithmId.ALGORITHM, sigAlgid);

        // first upgrade to version 3

        certInfo.set(X509CertInfo.VERSION,
                        new CertificateVersion(CertificateVersion.V3));

        // Sign the new certificate
        newCert = new X509CertImpl(certInfo);
        newCert.sign(privKey, sigAlgName);

        // Store the new certificate as a single-element certificate chain
        keyStore.setKeyEntry(alias, privKey,
                             (keyPass != null) ? keyPass : storePass,
                             new Certificate[] { newCert } );

        if (verbose) {
            System.err.println(rb.getString("New certificate (self-signed):"));
            System.err.print(newCert.toString());
            System.err.println();
        }
    }

    /**
     * Processes a certificate reply from a certificate authority.
     *
     * <p>Builds a certificate chain on top of the certificate reply,
     * using trusted certificates from the keystore. The chain is complete
     * after a self-signed certificate has been encountered. The self-signed
     * certificate is considered a root certificate authority, and is stored
     * at the end of the chain.
     *
     * <p>The newly generated chain replaces the old chain associated with the
     * key entry.
     *
     * @return true if the certificate reply was installed, otherwise false.
     */
    private boolean installReply(String alias, InputStream in)
        throws Exception
    {
        if (alias == null) {
            alias = keyAlias;
        }

        Object[] objs = recoverKey(alias, storePass, keyPass);
        PrivateKey privKey = (PrivateKey)objs[0];
        if (keyPass == null) {
            keyPass = (char[])objs[1];
        }

        Certificate userCert = keyStore.getCertificate(alias);
        if (userCert == null) {
            MessageFormat form = new MessageFormat
                (rb.getString("alias has no public key (certificate)"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        // Read the certificates in the reply
        Collection<? extends Certificate> c = cf.generateCertificates(in);
        if (c.isEmpty()) {
            throw new Exception(rb.getString("Reply has no certificates"));
        }
        Certificate[] replyCerts = c.toArray(new Certificate[c.size()]);
        Certificate[] newChain;
        if (replyCerts.length == 1) {
            // single-cert reply
            newChain = establishCertChain(userCert, replyCerts[0]);
        } else {
            // cert-chain reply (e.g., PKCS#7)
            newChain = validateReply(alias, userCert, replyCerts);
        }

        // Now store the newly established chain in the keystore. The new
        // chain replaces the old one.
        if (newChain != null) {
            keyStore.setKeyEntry(alias, privKey,
                                 (keyPass != null) ? keyPass : storePass,
                                 newChain);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Imports a certificate and adds it to the list of trusted certificates.
     *
     * @return true if the certificate was added, otherwise false.
     */
    private boolean addTrustedCert(String alias, InputStream in)
        throws Exception
    {
        if (alias == null) {
            throw new Exception(rb.getString("Must specify alias"));
        }
        if (keyStore.containsAlias(alias)) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Certificate not imported, alias <alias> already exists"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        // Read the certificate
        X509Certificate cert = null;
        try {
            cert = (X509Certificate)cf.generateCertificate(in);
        } catch (ClassCastException cce) {
            throw new Exception(rb.getString("Input not an X.509 certificate"));
        } catch (CertificateException ce) {
            throw new Exception(rb.getString("Input not an X.509 certificate"));
        }

        // if certificate is self-signed, make sure it verifies
        boolean selfSigned = false;
        if (isSelfSigned(cert)) {
            cert.verify(cert.getPublicKey());
            selfSigned = true;
        }

        if (noprompt) {
            keyStore.setCertificateEntry(alias, cert);
            return true;
        }

        // check if cert already exists in keystore
        String reply = null;
        String trustalias = keyStore.getCertificateAlias(cert);
        if (trustalias != null) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Certificate already exists in keystore under alias <trustalias>"));
            Object[] source = {trustalias};
            System.err.println(form.format(source));
            reply = getYesNoReply
                (rb.getString("Do you still want to add it? [no]:  "));
        } else if (selfSigned) {
            if (trustcacerts && (caks != null) &&
                    ((trustalias=caks.getCertificateAlias(cert)) != null)) {
                MessageFormat form = new MessageFormat(rb.getString
                        ("Certificate already exists in system-wide CA keystore under alias <trustalias>"));
                Object[] source = {trustalias};
                System.err.println(form.format(source));
                reply = getYesNoReply
                        (rb.getString("Do you still want to add it to your own keystore? [no]:  "));
            }
            if (trustalias == null) {
                // Print the cert and ask user if they really want to add
                // it to their keystore
                printX509Cert(cert, System.out);
                reply = getYesNoReply
                        (rb.getString("Trust this certificate? [no]:  "));
            }
        }
        if (reply != null) {
            if ("YES".equals(reply)) {
                keyStore.setCertificateEntry(alias, cert);
                return true;
            } else {
                return false;
            }
        }

        // Try to establish trust chain
        try {
            Certificate[] chain = establishCertChain(null, cert);
            if (chain != null) {
                keyStore.setCertificateEntry(alias, cert);
                return true;
            }
        } catch (Exception e) {
            // Print the cert and ask user if they really want to add it to
            // their keystore
            printX509Cert(cert, System.out);
            reply = getYesNoReply
                (rb.getString("Trust this certificate? [no]:  "));
            if ("YES".equals(reply)) {
                keyStore.setCertificateEntry(alias, cert);
                return true;
            } else {
                return false;
            }
        }

        return false;
    }

    /**
     * Prompts user for new password. New password must be different from
     * old one.
     *
     * @param prompt the message that gets prompted on the screen
     * @param oldPasswd the current (i.e., old) password
     */
    private char[] getNewPasswd(String prompt, char[] oldPasswd)
        throws Exception
    {
        char[] entered = null;
        char[] reentered = null;

        for (int count = 0; count < 3; count++) {
            MessageFormat form = new MessageFormat
                (rb.getString("New prompt: "));
            Object[] source = {prompt};
            System.err.print(form.format(source));
            entered = Password.readPassword(System.in);
            passwords.add(entered);
            if (entered == null || entered.length < 6) {
                System.err.println(rb.getString
                    ("Password is too short - must be at least 6 characters"));
            } else if (Arrays.equals(entered, oldPasswd)) {
                System.err.println(rb.getString("Passwords must differ"));
            } else {
                form = new MessageFormat
                        (rb.getString("Re-enter new prompt: "));
                Object[] src = {prompt};
                System.err.print(form.format(src));
                reentered = Password.readPassword(System.in);
                passwords.add(reentered);
                if (!Arrays.equals(entered, reentered)) {
                    System.err.println
                        (rb.getString("They don't match. Try again"));
                } else {
                    Arrays.fill(reentered, ' ');
                    return entered;
                }
            }
            if (entered != null) {
                Arrays.fill(entered, ' ');
                entered = null;
            }
            if (reentered != null) {
                Arrays.fill(reentered, ' ');
                reentered = null;
            }
        }
        throw new Exception(rb.getString("Too many failures - try later"));
    }

    /**
     * Prompts user for alias name.
     * @param prompt the {0} of "Enter {0} alias name:  " in prompt line
     * @returns the string entered by the user, without the \n at the end
     */
    private String getAlias(String prompt) throws Exception {
        if (prompt != null) {
            MessageFormat form = new MessageFormat
                (rb.getString("Enter prompt alias name:  "));
            Object[] source = {prompt};
            System.err.print(form.format(source));
        } else {
            System.err.print(rb.getString("Enter alias name:  "));
        }
        return (new BufferedReader(new InputStreamReader(
                                        System.in))).readLine();
    }

    /**
     * Prompts user for an input string from the command line (System.in)
     * @prompt the prompt string printed
     * @returns the string entered by the user, without the \n at the end
     */
    private String inputStringFromStdin(String prompt) throws Exception {
        System.err.print(prompt);
        return (new BufferedReader(new InputStreamReader(
                                        System.in))).readLine();
    }

    /**
     * Prompts user for key password. User may select to choose the same
     * password (<code>otherKeyPass</code>) as for <code>otherAlias</code>.
     */
    private char[] getKeyPasswd(String alias, String otherAlias,
                                char[] otherKeyPass)
        throws Exception
    {
        int count = 0;
        char[] keyPass = null;

        do {
            if (otherKeyPass != null) {
                MessageFormat form = new MessageFormat(rb.getString
                        ("Enter key password for <alias>"));
                Object[] source = {alias};
                System.err.println(form.format(source));

                form = new MessageFormat(rb.getString
                        ("\t(RETURN if same as for <otherAlias>)"));
                Object[] src = {otherAlias};
                System.err.print(form.format(src));
            } else {
                MessageFormat form = new MessageFormat(rb.getString
                        ("Enter key password for <alias>"));
                Object[] source = {alias};
                System.err.print(form.format(source));
            }
            System.err.flush();
            keyPass = Password.readPassword(System.in);
            passwords.add(keyPass);
            if (keyPass == null) {
                keyPass = otherKeyPass;
            }
            count++;
        } while ((keyPass == null) && count < 3);

        if (keyPass == null) {
            throw new Exception(rb.getString("Too many failures - try later"));
        }

        return keyPass;
    }

    /**
     * Prints a certificate in a human readable format.
     */
    private void printX509Cert(X509Certificate cert, PrintStream out)
        throws Exception
    {
        /*
        out.println("Owner: "
                    + cert.getSubjectDN().toString()
                    + "\n"
                    + "Issuer: "
                    + cert.getIssuerDN().toString()
                    + "\n"
                    + "Serial number: " + cert.getSerialNumber().toString(16)
                    + "\n"
                    + "Valid from: " + cert.getNotBefore().toString()
                    + " until: " + cert.getNotAfter().toString()
                    + "\n"
                    + "Certificate fingerprints:\n"
                    + "\t MD5:  " + getCertFingerPrint("MD5", cert)
                    + "\n"
                    + "\t SHA1: " + getCertFingerPrint("SHA1", cert));
        */

        MessageFormat form = new MessageFormat
                (rb.getString("*PATTERN* printX509Cert"));
        Object[] source = {cert.getSubjectDN().toString(),
                        cert.getIssuerDN().toString(),
                        cert.getSerialNumber().toString(16),
                        cert.getNotBefore().toString(),
                        cert.getNotAfter().toString(),
                        getCertFingerPrint("MD5", cert),
                        getCertFingerPrint("SHA1", cert),
                        cert.getSigAlgName(),
                        cert.getVersion()
                        };
        out.println(form.format(source));

        int extnum = 0;
        if (cert instanceof X509CertImpl) {
            X509CertImpl impl = (X509CertImpl)cert;
            if (cert.getCriticalExtensionOIDs() != null) {
                for (String extOID : cert.getCriticalExtensionOIDs()) {
                    if (extnum == 0) {
                        out.println();
                        out.println(rb.getString("Extensions: "));
                        out.println();
                    }
                    out.println("#"+(++extnum)+": "+
                            impl.getExtension(new ObjectIdentifier(extOID)));
                }
            }
            if (cert.getNonCriticalExtensionOIDs() != null) {
                for (String extOID : cert.getNonCriticalExtensionOIDs()) {
                    if (extnum == 0) {
                        out.println();
                        out.println(rb.getString("Extensions: "));
                        out.println();
                    }
                    Extension ext = impl.getExtension(new ObjectIdentifier(extOID));
                    if (ext != null) {
                        out.println("#"+(++extnum)+": "+ ext);
                    } else {
                        out.println("#"+(++extnum)+": "+
                                impl.getUnparseableExtension(new ObjectIdentifier(extOID)));
                    }
                }
            }
        }
    }

    /**
     * Returns true if the certificate is self-signed, false otherwise.
     */
    private boolean isSelfSigned(X509Certificate cert) {
        return cert.getSubjectDN().equals(cert.getIssuerDN());
    }

    /**
     * Returns true if the given certificate is trusted, false otherwise.
     */
    private boolean isTrusted(Certificate cert)
        throws Exception
    {
        if (keyStore.getCertificateAlias(cert) != null) {
            return true; // found in own keystore
        }
        if (trustcacerts && (caks != null) &&
                (caks.getCertificateAlias(cert) != null)) {
            return true; // found in CA keystore
        }
        return false;
    }

    /**
     * Gets an X.500 name suitable for inclusion in a certification request.
     */
    private X500Name getX500Name() throws IOException {
        BufferedReader in;
        in = new BufferedReader(new InputStreamReader(System.in));
        String commonName = "Unknown";
        String organizationalUnit = "Unknown";
        String organization = "Unknown";
        String city = "Unknown";
        String state = "Unknown";
        String country = "Unknown";
        X500Name name;
        String userInput = null;

        int maxRetry = 20;
        do {
            if (maxRetry-- < 0) {
                throw new RuntimeException(rb.getString(
                        "Too may retries, program terminated"));
            }
            commonName = inputString(in,
                    rb.getString("What is your first and last name?"),
                    commonName);
            organizationalUnit = inputString(in,
                    rb.getString
                        ("What is the name of your organizational unit?"),
                    organizationalUnit);
            organization = inputString(in,
                    rb.getString("What is the name of your organization?"),
                    organization);
            city = inputString(in,
                    rb.getString("What is the name of your City or Locality?"),
                    city);
            state = inputString(in,
                    rb.getString("What is the name of your State or Province?"),
                    state);
            country = inputString(in,
                    rb.getString
                        ("What is the two-letter country code for this unit?"),
                    country);
            name = new X500Name(commonName, organizationalUnit, organization,
                                city, state, country);
            MessageFormat form = new MessageFormat
                (rb.getString("Is <name> correct?"));
            Object[] source = {name};
            userInput = inputString
                (in, form.format(source), rb.getString("no"));
        } while (collator.compare(userInput, rb.getString("yes")) != 0 &&
                 collator.compare(userInput, rb.getString("y")) != 0);

        System.err.println();
        return name;
    }

    private String inputString(BufferedReader in, String prompt,
                               String defaultValue)
        throws IOException
    {
        System.err.println(prompt);
        MessageFormat form = new MessageFormat
                (rb.getString("  [defaultValue]:  "));
        Object[] source = {defaultValue};
        System.err.print(form.format(source));
        System.err.flush();

        String value = in.readLine();
        if (value == null || collator.compare(value, "") == 0) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Writes an X.509 certificate in base64 or binary encoding to an output
     * stream.
     */
    private void dumpCert(Certificate cert, PrintStream out)
        throws IOException, CertificateException
    {
        if (rfc) {
            BASE64Encoder encoder = new BASE64Encoder();
            out.println(X509Factory.BEGIN_CERT);
            encoder.encodeBuffer(cert.getEncoded(), out);
            out.println(X509Factory.END_CERT);
        } else {
            out.write(cert.getEncoded()); // binary
        }
    }

    /**
     * Converts a byte to hex digit and writes to the supplied buffer
     */
    private void byte2hex(byte b, StringBuffer buf) {
        char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
                            '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        int high = ((b & 0xf0) >> 4);
        int low = (b & 0x0f);
        buf.append(hexChars[high]);
        buf.append(hexChars[low]);
    }

    /**
     * Converts a byte array to hex string
     */
    private String toHexString(byte[] block) {
        StringBuffer buf = new StringBuffer();
        int len = block.length;
        for (int i = 0; i < len; i++) {
             byte2hex(block[i], buf);
             if (i < len-1) {
                 buf.append(":");
             }
        }
        return buf.toString();
    }

    /**
     * Recovers (private) key associated with given alias.
     *
     * @return an array of objects, where the 1st element in the array is the
     * recovered private key, and the 2nd element is the password used to
     * recover it.
     */
    private Object[] recoverKey(String alias, char[] storePass,
                                       char[] keyPass)
        throws Exception
    {
        Key key = null;

        if (keyStore.containsAlias(alias) == false) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> does not exist"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }
        if (!keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class) &&
                !keyStore.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> has no key"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        if (keyPass == null) {
            // Try to recover the key using the keystore password
            try {
                key = keyStore.getKey(alias, storePass);

                keyPass = storePass;
                passwords.add(keyPass);
            } catch (UnrecoverableKeyException e) {
                // Did not work out, so prompt user for key password
                if (!token) {
                    keyPass = getKeyPasswd(alias, null, null);
                    key = keyStore.getKey(alias, keyPass);
                } else {
                    throw e;
                }
            }
        } else {
            key = keyStore.getKey(alias, keyPass);
        }

        return new Object[] {key, keyPass};
    }

    /**
     * Recovers entry associated with given alias.
     *
     * @return an array of objects, where the 1st element in the array is the
     * recovered entry, and the 2nd element is the password used to
     * recover it (null if no password).
     */
    private Object[] recoverEntry(KeyStore ks,
                            String alias,
                            char[] pstore,
                            char[] pkey) throws Exception {

        if (ks.containsAlias(alias) == false) {
            MessageFormat form = new MessageFormat
                (rb.getString("Alias <alias> does not exist"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        PasswordProtection pp = null;
        Entry entry;

        try {
            // First attempt to access entry without key password
            // (PKCS11 entry or trusted certificate entry, for example)

            entry = ks.getEntry(alias, pp);
            pkey = null;
        } catch (UnrecoverableEntryException une) {

            if(P11KEYSTORE.equalsIgnoreCase(ks.getType()) ||
                KeyStoreUtil.isWindowsKeyStore(ks.getType())) {
                // should not happen, but a possibility
                throw une;
            }

            // entry is protected

            if (pkey != null) {

                // try provided key password

                pp = new PasswordProtection(pkey);
                entry = ks.getEntry(alias, pp);

            } else {

                // try store pass

                try {
                    pp = new PasswordProtection(pstore);
                    entry = ks.getEntry(alias, pp);
                    pkey = pstore;
                } catch (UnrecoverableEntryException une2) {
                    if (P12KEYSTORE.equalsIgnoreCase(ks.getType())) {

                        // P12 keystore currently does not support separate
                        // store and entry passwords

                        throw une2;
                    } else {

                        // prompt for entry password

                        pkey = getKeyPasswd(alias, null, null);
                        pp = new PasswordProtection(pkey);
                        entry = ks.getEntry(alias, pp);
                    }
                }
            }
        }

        return new Object[] {entry, pkey};
    }
    /**
     * Gets the requested finger print of the certificate.
     */
    private String getCertFingerPrint(String mdAlg, Certificate cert)
        throws Exception
    {
        byte[] encCertInfo = cert.getEncoded();
        MessageDigest md = MessageDigest.getInstance(mdAlg);
        byte[] digest = md.digest(encCertInfo);
        return toHexString(digest);
    }

    /**
     * Prints warning about missing integrity check.
     */
    private void printWarning() {
        System.err.println();
        System.err.println(rb.getString
            ("*****************  WARNING WARNING WARNING  *****************"));
        System.err.println(rb.getString
            ("* The integrity of the information stored in your keystore  *"));
        System.err.println(rb.getString
            ("* has NOT been verified!  In order to verify its integrity, *"));
        System.err.println(rb.getString
            ("* you must provide your keystore password.                  *"));
        System.err.println(rb.getString
            ("*****************  WARNING WARNING WARNING  *****************"));
        System.err.println();
    }

    /**
     * Validates chain in certification reply, and returns the ordered
     * elements of the chain (with user certificate first, and root
     * certificate last in the array).
     *
     * @param alias the alias name
     * @param userCert the user certificate of the alias
     * @param replyCerts the chain provided in the reply
     */
    private Certificate[] validateReply(String alias,
                                        Certificate userCert,
                                        Certificate[] replyCerts)
        throws Exception
    {
        // order the certs in the reply (bottom-up).
        // we know that all certs in the reply are of type X.509, because
        // we parsed them using an X.509 certificate factory
        int i;
        PublicKey userPubKey = userCert.getPublicKey();
        for (i=0; i<replyCerts.length; i++) {
            if (userPubKey.equals(replyCerts[i].getPublicKey())) {
                break;
            }
        }
        if (i == replyCerts.length) {
            MessageFormat form = new MessageFormat(rb.getString
                ("Certificate reply does not contain public key for <alias>"));
            Object[] source = {alias};
            throw new Exception(form.format(source));
        }

        Certificate tmpCert = replyCerts[0];
        replyCerts[0] = replyCerts[i];
        replyCerts[i] = tmpCert;
        Principal issuer = ((X509Certificate)replyCerts[0]).getIssuerDN();

        for (i=1; i < replyCerts.length-1; i++) {
            // find a cert in the reply whose "subject" is the same as the
            // given "issuer"
            int j;
            for (j=i; j<replyCerts.length; j++) {
                Principal subject;
                subject = ((X509Certificate)replyCerts[j]).getSubjectDN();
                if (subject.equals(issuer)) {
                    tmpCert = replyCerts[i];
                    replyCerts[i] = replyCerts[j];
                    replyCerts[j] = tmpCert;
                    issuer = ((X509Certificate)replyCerts[i]).getIssuerDN();
                    break;
                }
            }
            if (j == replyCerts.length) {
                throw new Exception
                    (rb.getString("Incomplete certificate chain in reply"));
            }
        }

        // now verify each cert in the ordered chain
        for (i=0; i<replyCerts.length-1; i++) {
            PublicKey pubKey = replyCerts[i+1].getPublicKey();
            try {
                replyCerts[i].verify(pubKey);
            } catch (Exception e) {
                throw new Exception(rb.getString
                        ("Certificate chain in reply does not verify: ") +
                        e.getMessage());
            }
        }

        if (noprompt) {
            return replyCerts;
        }

        // do we trust the (root) cert at the top?
        Certificate topCert = replyCerts[replyCerts.length-1];
        if (!isTrusted(topCert)) {
            boolean verified = false;
            Certificate rootCert = null;
            if (trustcacerts && (caks!= null)) {
                for (Enumeration<String> aliases = caks.aliases();
                     aliases.hasMoreElements(); ) {
                    String name = aliases.nextElement();
                    rootCert = caks.getCertificate(name);
                    if (rootCert != null) {
                        try {
                            topCert.verify(rootCert.getPublicKey());
                            verified = true;
                            break;
                        } catch (Exception e) {
                        }
                    }
                }
            }
            if (!verified) {
                System.err.println();
                System.err.println
                        (rb.getString("Top-level certificate in reply:\n"));
                printX509Cert((X509Certificate)topCert, System.out);
                System.err.println();
                System.err.print(rb.getString("... is not trusted. "));
                String reply = getYesNoReply
                        (rb.getString("Install reply anyway? [no]:  "));
                if ("NO".equals(reply)) {
                    return null;
                }
            } else {
                if (!isSelfSigned((X509Certificate)topCert)) {
                    // append the (self-signed) root CA cert to the chain
                    Certificate[] tmpCerts =
                        new Certificate[replyCerts.length+1];
                    System.arraycopy(replyCerts, 0, tmpCerts, 0,
                                     replyCerts.length);
                    tmpCerts[tmpCerts.length-1] = rootCert;
                    replyCerts = tmpCerts;
                }
            }
        }

        return replyCerts;
    }

    /**
     * Establishes a certificate chain (using trusted certificates in the
     * keystore), starting with the user certificate
     * and ending at a self-signed certificate found in the keystore.
     *
     * @param userCert the user certificate of the alias
     * @param certToVerify the single certificate provided in the reply
     */
    private Certificate[] establishCertChain(Certificate userCert,
                                             Certificate certToVerify)
        throws Exception
    {
        if (userCert != null) {
            // Make sure that the public key of the certificate reply matches
            // the original public key in the keystore
            PublicKey origPubKey = userCert.getPublicKey();
            PublicKey replyPubKey = certToVerify.getPublicKey();
            if (!origPubKey.equals(replyPubKey)) {
                throw new Exception(rb.getString
                        ("Public keys in reply and keystore don't match"));
            }

            // If the two certs are identical, we're done: no need to import
            // anything
            if (certToVerify.equals(userCert)) {
                throw new Exception(rb.getString
                        ("Certificate reply and certificate in keystore are identical"));
            }
        }

        // Build a hash table of all certificates in the keystore.
        // Use the subject distinguished name as the key into the hash table.
        // All certificates associated with the same subject distinguished
        // name are stored in the same hash table entry as a vector.
        Hashtable<Principal, Vector<Certificate>> certs = null;
        if (keyStore.size() > 0) {
            certs = new Hashtable<Principal, Vector<Certificate>>(11);
            keystorecerts2Hashtable(keyStore, certs);
        }
        if (trustcacerts) {
            if (caks!=null && caks.size()>0) {
                if (certs == null) {
                    certs = new Hashtable<Principal, Vector<Certificate>>(11);
                }
                keystorecerts2Hashtable(caks, certs);
            }
        }

        // start building chain
        Vector<Certificate> chain = new Vector<Certificate>(2);
        if (buildChain((X509Certificate)certToVerify, chain, certs)) {
            Certificate[] newChain = new Certificate[chain.size()];
            // buildChain() returns chain with self-signed root-cert first and
            // user-cert last, so we need to invert the chain before we store
            // it
            int j=0;
            for (int i=chain.size()-1; i>=0; i--) {
                newChain[j] = chain.elementAt(i);
                j++;
            }
            return newChain;
        } else {
            throw new Exception
                (rb.getString("Failed to establish chain from reply"));
        }
    }

    /**
     * Recursively tries to establish chain from pool of trusted certs.
     *
     * @param certToVerify the cert that needs to be verified.
     * @param chain the chain that's being built.
     * @param certs the pool of trusted certs
     *
     * @return true if successful, false otherwise.
     */
    private boolean buildChain(X509Certificate certToVerify,
                        Vector<Certificate> chain,
                        Hashtable<Principal, Vector<Certificate>> certs) {
        Principal subject = certToVerify.getSubjectDN();
        Principal issuer = certToVerify.getIssuerDN();
        if (subject.equals(issuer)) {
            // reached self-signed root cert;
            // no verification needed because it's trusted.
            chain.addElement(certToVerify);
            return true;
        }

        // Get the issuer's certificate(s)
        Vector<Certificate> vec = certs.get(issuer);
        if (vec == null) {
            return false;
        }

        // Try out each certificate in the vector, until we find one
        // whose public key verifies the signature of the certificate
        // in question.
        for (Enumeration<Certificate> issuerCerts = vec.elements();
             issuerCerts.hasMoreElements(); ) {
            X509Certificate issuerCert
                = (X509Certificate)issuerCerts.nextElement();
            PublicKey issuerPubKey = issuerCert.getPublicKey();
            try {
                certToVerify.verify(issuerPubKey);
            } catch (Exception e) {
                continue;
            }
            if (buildChain(issuerCert, chain, certs)) {
                chain.addElement(certToVerify);
                return true;
            }
        }
        return false;
    }

    /**
     * Prompts user for yes/no decision.
     *
     * @return the user's decision, can only be "YES" or "NO"
     */
    private String getYesNoReply(String prompt)
        throws IOException
    {
        String reply = null;
        int maxRetry = 20;
        do {
            if (maxRetry-- < 0) {
                throw new RuntimeException(rb.getString(
                        "Too may retries, program terminated"));
            }
            System.err.print(prompt);
            System.err.flush();
            reply = (new BufferedReader(new InputStreamReader
                                        (System.in))).readLine();
            if (collator.compare(reply, "") == 0 ||
                collator.compare(reply, rb.getString("n")) == 0 ||
                collator.compare(reply, rb.getString("no")) == 0) {
                reply = "NO";
            } else if (collator.compare(reply, rb.getString("y")) == 0 ||
                       collator.compare(reply, rb.getString("yes")) == 0) {
                reply = "YES";
            } else {
                System.err.println(rb.getString("Wrong answer, try again"));
                reply = null;
            }
        } while (reply == null);
        return reply;
    }

    /**
     * Returns the keystore with the configured CA certificates.
     */
    private KeyStore getCacertsKeyStore()
        throws Exception
    {
        String sep = File.separator;
        File file = new File(System.getProperty("java.home") + sep
                             + "lib" + sep + "security" + sep
                             + "cacerts");
        if (!file.exists()) {
            return null;
        }
        FileInputStream fis = null;
        KeyStore caks = null;
        try {
            fis = new FileInputStream(file);
            caks = KeyStore.getInstance(JKS);
            caks.load(fis, null);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        return caks;
    }

    /**
     * Stores the (leaf) certificates of a keystore in a hashtable.
     * All certs belonging to the same CA are stored in a vector that
     * in turn is stored in the hashtable, keyed by the CA's subject DN
     */
    private void keystorecerts2Hashtable(KeyStore ks,
                Hashtable<Principal, Vector<Certificate>> hash)
        throws Exception {

        for (Enumeration<String> aliases = ks.aliases();
                                        aliases.hasMoreElements(); ) {
            String alias = aliases.nextElement();
            Certificate cert = ks.getCertificate(alias);
            if (cert != null) {
                Principal subjectDN = ((X509Certificate)cert).getSubjectDN();
                Vector<Certificate> vec = hash.get(subjectDN);
                if (vec == null) {
                    vec = new Vector<Certificate>();
                    vec.addElement(cert);
                } else {
                    if (!vec.contains(cert)) {
                        vec.addElement(cert);
                    }
                }
                hash.put(subjectDN, vec);
            }
        }
    }

    /**
     * Returns the issue time that's specified the -startdate option
     * @param s the value of -startdate option
     */
    private static Date getStartDate(String s) throws IOException {
        Calendar c = new GregorianCalendar();
        if (s != null) {
            IOException ioe = new IOException(
                    rb.getString("Illegal startdate value"));
            int len = s.length();
            if (len == 0) {
                throw ioe;
            }
            if (s.charAt(0) == '-' || s.charAt(0) == '+') {
                // Form 1: ([+-]nnn[ymdHMS])+
                int start = 0;
                while (start < len) {
                    int sign = 0;
                    switch (s.charAt(start)) {
                        case '+': sign = 1; break;
                        case '-': sign = -1; break;
                        default: throw ioe;
                    }
                    int i = start+1;
                    for (; i<len; i++) {
                        char ch = s.charAt(i);
                        if (ch < '0' || ch > '9') break;
                    }
                    if (i == start+1) throw ioe;
                    int number = Integer.parseInt(s.substring(start+1, i));
                    if (i >= len) throw ioe;
                    int unit = 0;
                    switch (s.charAt(i)) {
                        case 'y': unit = Calendar.YEAR; break;
                        case 'm': unit = Calendar.MONTH; break;
                        case 'd': unit = Calendar.DATE; break;
                        case 'H': unit = Calendar.HOUR; break;
                        case 'M': unit = Calendar.MINUTE; break;
                        case 'S': unit = Calendar.SECOND; break;
                        default: throw ioe;
                    }
                    c.add(unit, sign * number);
                    start = i + 1;
                }
            } else  {
                // Form 2: [yyyy/mm/dd] [HH:MM:SS]
                String date = null, time = null;
                if (len == 19) {
                    date = s.substring(0, 10);
                    time = s.substring(11);
                    if (s.charAt(10) != ' ')
                        throw ioe;
                } else if (len == 10) {
                    date = s;
                } else if (len == 8) {
                    time = s;
                } else {
                    throw ioe;
                }
                if (date != null) {
                    if (date.matches("\\d\\d\\d\\d\\/\\d\\d\\/\\d\\d")) {
                        c.set(Integer.valueOf(date.substring(0, 4)),
                                Integer.valueOf(date.substring(5, 7))-1,
                                Integer.valueOf(date.substring(8, 10)));
                    } else {
                        throw ioe;
                    }
                }
                if (time != null) {
                    if (time.matches("\\d\\d:\\d\\d:\\d\\d")) {
                        c.set(Calendar.HOUR_OF_DAY, Integer.valueOf(time.substring(0, 2)));
                        c.set(Calendar.MINUTE, Integer.valueOf(time.substring(0, 2)));
                        c.set(Calendar.SECOND, Integer.valueOf(time.substring(0, 2)));
                        c.set(Calendar.MILLISECOND, 0);
                    } else {
                        throw ioe;
                    }
                }
            }
        }
        return c.getTime();
    }

    /**
     * Prints the usage of this tool.
     */
    private void usage() {
        System.err.println(rb.getString("keytool usage:\n"));

        System.err.println(rb.getString
                ("-certreq     [-v] [-protected]"));
        System.err.println(rb.getString
                ("\t     [-alias <alias>] [-sigalg <sigalg>]"));
        System.err.println(rb.getString
                ("\t     [-file <csr_file>] [-keypass <keypass>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-changealias [-v] [-protected] -alias <alias> -destalias <destalias>"));
        System.err.println(rb.getString
                ("\t     [-keypass <keypass>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-delete      [-v] [-protected] -alias <alias>"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-exportcert  [-v] [-rfc] [-protected]"));
        System.err.println(rb.getString
                ("\t     [-alias <alias>] [-file <cert_file>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-genkeypair  [-v] [-protected]"));
        System.err.println(rb.getString
                ("\t     [-alias <alias>]"));
        System.err.println(rb.getString
                ("\t     [-keyalg <keyalg>] [-keysize <keysize>]"));
        System.err.println(rb.getString
                ("\t     [-sigalg <sigalg>] [-dname <dname>]"));
        System.err.println(rb.getString
                ("\t     [-startdate <startdate>]"));
        System.err.println(rb.getString
                ("\t     [-validity <valDays>] [-keypass <keypass>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-genseckey   [-v] [-protected]"));
        System.err.println(rb.getString
                ("\t     [-alias <alias>] [-keypass <keypass>]"));
        System.err.println(rb.getString
                ("\t     [-keyalg <keyalg>] [-keysize <keysize>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString("-help"));
        System.err.println();

        System.err.println(rb.getString
                ("-importcert  [-v] [-noprompt] [-trustcacerts] [-protected]"));
        System.err.println(rb.getString
                ("\t     [-alias <alias>]"));
        System.err.println(rb.getString
                ("\t     [-file <cert_file>] [-keypass <keypass>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-importkeystore [-v] "));
        System.err.println(rb.getString
                ("\t     [-srckeystore <srckeystore>] [-destkeystore <destkeystore>]"));
        System.err.println(rb.getString
                ("\t     [-srcstoretype <srcstoretype>] [-deststoretype <deststoretype>]"));
        System.err.println(rb.getString
                ("\t     [-srcstorepass <srcstorepass>] [-deststorepass <deststorepass>]"));
        System.err.println(rb.getString
                ("\t     [-srcprotected] [-destprotected]"));
        System.err.println(rb.getString
                ("\t     [-srcprovidername <srcprovidername>]\n\t     [-destprovidername <destprovidername>]"));
        System.err.println(rb.getString
                ("\t     [-srcalias <srcalias> [-destalias <destalias>]"));
        System.err.println(rb.getString
                ("\t       [-srckeypass <srckeypass>] [-destkeypass <destkeypass>]]"));
        System.err.println(rb.getString
                ("\t     [-noprompt]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-keypasswd   [-v] [-alias <alias>]"));
        System.err.println(rb.getString
                ("\t     [-keypass <old_keypass>] [-new <new_keypass>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-list        [-v | -rfc] [-protected]"));
        System.err.println(rb.getString
                ("\t     [-alias <alias>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-printcert   [-v] [-rfc] [-file <cert_file> | -sslserver <host[:port]>]"));
        System.err.println();

        System.err.println(rb.getString
                ("-storepasswd [-v] [-new <new_storepass>]"));
        System.err.println(rb.getString
                ("\t     [-keystore <keystore>] [-storepass <storepass>]"));
        System.err.println(rb.getString
                ("\t     [-storetype <storetype>] [-providername <name>]"));
        System.err.println(rb.getString
                ("\t     [-providerclass <provider_class_name> [-providerarg <arg>]] ..."));
        System.err.println(rb.getString
                ("\t     [-providerpath <pathlist>]"));

        if (debug) {
            throw new RuntimeException("NO ERROR, SORRY");
        } else {
            System.exit(1);
        }
    }

    private void tinyHelp() {
        System.err.println(rb.getString("Try keytool -help"));

        // do not drown user with the help lines.
        if (debug) {
            throw new RuntimeException("NO BIG ERROR, SORRY");
        } else {
            System.exit(1);
        }
    }

    private void errorNeedArgument(String flag) {
        Object[] source = {flag};
        System.err.println(new MessageFormat(
                rb.getString("Command option <flag> needs an argument.")).format(source));
        tinyHelp();
    }
}

// This class is exactly the same as com.sun.tools.javac.util.Pair,
// it's copied here since the original one is not included in JRE.
class Pair<A, B> {

    public final A fst;
    public final B snd;

    public Pair(A fst, B snd) {
        this.fst = fst;
        this.snd = snd;
    }

    public String toString() {
        return "Pair[" + fst + "," + snd + "]";
    }

    private static boolean equals(Object x, Object y) {
        return (x == null && y == null) || (x != null && x.equals(y));
    }

    public boolean equals(Object other) {
        return
            other instanceof Pair &&
            equals(fst, ((Pair)other).fst) &&
            equals(snd, ((Pair)other).snd);
    }

    public int hashCode() {
        if (fst == null) return (snd == null) ? 0 : snd.hashCode() + 1;
        else if (snd == null) return fst.hashCode() + 2;
        else return fst.hashCode() * 17 + snd.hashCode();
    }
}
