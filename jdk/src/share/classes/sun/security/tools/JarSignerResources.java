/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for JarSigner.
 *
 */
public class JarSignerResources extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // shared (from jarsigner)
        {" ", " "},
        {"  ", "  "},
        {"      ", "      "},
        {", ", ", "},

        {"provName not a provider", "{0} not a provider"},
        {"signerClass is not a signing mechanism", "{0} is not a signing mechanism"},
        {"jarsigner error: ", "jarsigner error: "},
        {"Illegal option: ", "Illegal option: "},
        {"-keystore must be NONE if -storetype is {0}",
                "-keystore must be NONE if -storetype is {0}"},
        {"-keypass can not be specified if -storetype is {0}",
                "-keypass can not be specified if -storetype is {0}"},
        {"If -protected is specified, then -storepass and -keypass must not be specified",
                "If -protected is specified, then -storepass and -keypass must not be specified"},
        {"If keystore is not password protected, then -storepass and -keypass must not be specified",
                 "If keystore is not password protected, then -storepass and -keypass must not be specified"},
        {"Usage: jarsigner [options] jar-file alias",
                "Usage: jarsigner [options] jar-file alias"},
        {"       jarsigner -verify [options] jar-file [alias...]",
                "       jarsigner -verify [options] jar-file [alias...]"},
        {"[-keystore <url>]           keystore location",
                "[-keystore <url>]           keystore location"},
        {"[-storepass <password>]     password for keystore integrity",
            "[-storepass <password>]     password for keystore integrity"},
        {"[-storetype <type>]         keystore type",
                "[-storetype <type>]         keystore type"},
        {"[-keypass <password>]       password for private key (if different)",
                "[-keypass <password>]       password for private key (if different)"},
        {"[-certchain <file>]         name of alternative certchain file",
                "[-certchain <file>]         name of alternative certchain file"},
        {"[-sigfile <file>]           name of .SF/.DSA file",
                "[-sigfile <file>]           name of .SF/.DSA file"},
        {"[-signedjar <file>]         name of signed JAR file",
                "[-signedjar <file>]         name of signed JAR file"},
        {"[-digestalg <algorithm>]    name of digest algorithm",
                "[-digestalg <algorithm>]    name of digest algorithm"},
        {"[-sigalg <algorithm>]       name of signature algorithm",
                "[-sigalg <algorithm>]       name of signature algorithm"},
        {"[-verify]                   verify a signed JAR file",
                "[-verify]                   verify a signed JAR file"},
        {"[-verbose[:suboptions]]     verbose output when signing/verifying.",
                "[-verbose[:suboptions]]     verbose output when signing/verifying."},
        {"                            suboptions can be all, grouped or summary",
                "                            suboptions can be all, grouped or summary"},
        {"[-certs]                    display certificates when verbose and verifying",
                "[-certs]                    display certificates when verbose and verifying"},
        {"[-tsa <url>]                location of the Timestamping Authority",
                "[-tsa <url>]                location of the Timestamping Authority"},
        {"[-tsacert <alias>]          public key certificate for Timestamping Authority",
                "[-tsacert <alias>]          public key certificate for Timestamping Authority"},
        {"[-altsigner <class>]        class name of an alternative signing mechanism",
                "[-altsigner <class>]        class name of an alternative signing mechanism"},
        {"[-altsignerpath <pathlist>] location of an alternative signing mechanism",
                "[-altsignerpath <pathlist>] location of an alternative signing mechanism"},
        {"[-internalsf]               include the .SF file inside the signature block",
                "[-internalsf]               include the .SF file inside the signature block"},
        {"[-sectionsonly]             don't compute hash of entire manifest",
                "[-sectionsonly]             don't compute hash of entire manifest"},
        {"[-protected]                keystore has protected authentication path",
                "[-protected]                keystore has protected authentication path"},
        {"[-providerName <name>]      provider name",
                "[-providerName <name>]      provider name"},
        {"[-providerClass <class>     name of cryptographic service provider's",
                "[-providerClass <class>     name of cryptographic service provider's"},
        {"  [-providerArg <arg>]] ... master class file and constructor argument",
                "  [-providerArg <arg>]] ... master class file and constructor argument"},
        {"[-strict]                   treat warnings as errors",
                "[-strict]                   treat warnings as errors"},
        {"Option lacks argument", "Option lacks argument"},
        {"Please type jarsigner -help for usage", "Please type jarsigner -help for usage"},
        {"Please specify jarfile name", "Please specify jarfile name"},
        {"Please specify alias name", "Please specify alias name"},
        {"Only one alias can be specified", "Only one alias can be specified"},
        {"This jar contains signed entries which is not signed by the specified alias(es).",
                 "This jar contains signed entries which is not signed by the specified alias(es)."},
        {"This jar contains signed entries that's not signed by alias in this keystore.",
                  "This jar contains signed entries that's not signed by alias in this keystore."},
        {"s", "s"},
        {"m", "m"},
        {"k", "k"},
        {"i", "i"},
        {"(and %d more)", "(and %d more)"},
        {"  s = signature was verified ",
                "  s = signature was verified "},
        {"  m = entry is listed in manifest",
                "  m = entry is listed in manifest"},
        {"  k = at least one certificate was found in keystore",
                "  k = at least one certificate was found in keystore"},
        {"  i = at least one certificate was found in identity scope",
                "  i = at least one certificate was found in identity scope"},
        {"  X = not signed by specified alias(es)",
                "  X = not signed by specified alias(es)"},
        {"no manifest.", "no manifest."},
        {"(Signature related entries)","(Signature related entries)"},
        {"(Unsigned entries)", "(Unsigned entries)"},
        {"jar is unsigned. (signatures missing or not parsable)",
                "jar is unsigned. (signatures missing or not parsable)"},
        {"jar verified.", "jar verified."},
        {"jarsigner: ", "jarsigner: "},
        {"signature filename must consist of the following characters: A-Z, 0-9, _ or -",
                "signature filename must consist of the following characters: A-Z, 0-9, _ or -"},
        {"unable to open jar file: ", "unable to open jar file: "},
        {"unable to create: ", "unable to create: "},
        {"   adding: ", "   adding: "},
        {" updating: ", " updating: "},
        {"  signing: ", "  signing: "},
        {"attempt to rename signedJarFile to jarFile failed",
                "attempt to rename {0} to {1} failed"},
        {"attempt to rename jarFile to origJar failed",
                "attempt to rename {0} to {1} failed"},
        {"unable to sign jar: ", "unable to sign jar: "},
        {"Enter Passphrase for keystore: ", "Enter Passphrase for keystore: "},
        {"keystore load: ", "keystore load: "},
        {"certificate exception: ", "certificate exception: "},
        {"unable to instantiate keystore class: ",
                "unable to instantiate keystore class: "},
        {"Certificate chain not found for: alias.  alias must reference a valid KeyStore key entry containing a private key and corresponding public key certificate chain.",
                "Certificate chain not found for: {0}.  {1} must reference a valid KeyStore key entry containing a private key and corresponding public key certificate chain."},
        {"File specified by -certchain does not exist",
                "File specified by -certchain does not exist"},
        {"Cannot restore certchain from file specified",
                "Cannot restore certchain from file specified"},
        {"Certificate chain not found in the file specified.",
                "Certificate chain not found in the file specified."},
        {"found non-X.509 certificate in signer's chain",
                "found non-X.509 certificate in signer's chain"},
        {"incomplete certificate chain", "incomplete certificate chain"},
        {"Enter key password for alias: ", "Enter key password for {0}: "},
        {"unable to recover key from keystore",
                "unable to recover key from keystore"},
        {"key associated with alias not a private key",
                "key associated with {0} not a private key"},
        {"you must enter key password", "you must enter key password"},
        {"unable to read password: ", "unable to read password: "},
        {"certificate is valid from", "certificate is valid from {0} to {1}"},
        {"certificate expired on", "certificate expired on {0}"},
        {"certificate is not valid until",
                "certificate is not valid until {0}"},
        {"certificate will expire on", "certificate will expire on {0}"},
        {"[CertPath not validated: ", "[CertPath not validated: "},
        {"requesting a signature timestamp",
                "requesting a signature timestamp"},
        {"TSA location: ", "TSA location: "},
        {"TSA certificate: ", "TSA certificate: "},
        {"no response from the Timestamping Authority. ",
                "no response from the Timestamping Authority. "},
        {"When connecting from behind a firewall then an HTTP proxy may need to be specified. ",
                "When connecting from behind a firewall then an HTTP proxy may need to be specified. "},
        {"Supply the following options to jarsigner: ",
                "Supply the following options to jarsigner: "},
        {"Certificate not found for: alias.  alias must reference a valid KeyStore entry containing an X.509 public key certificate for the Timestamping Authority.",
                "Certificate not found for: {0}.  {1} must reference a valid KeyStore entry containing an X.509 public key certificate for the Timestamping Authority."},
        {"using an alternative signing mechanism",
                "using an alternative signing mechanism"},
        {"entry was signed on", "entry was signed on {0}"},
        {"Warning: ", "Warning: "},
        {"This jar contains unsigned entries which have not been integrity-checked. ",
                "This jar contains unsigned entries which have not been integrity-checked. "},
        {"This jar contains entries whose signer certificate has expired. ",
                "This jar contains entries whose signer certificate has expired. "},
        {"This jar contains entries whose signer certificate will expire within six months. ",
                "This jar contains entries whose signer certificate will expire within six months. "},
        {"This jar contains entries whose signer certificate is not yet valid. ",
                "This jar contains entries whose signer certificate is not yet valid. "},
        {"Re-run with the -verbose option for more details.",
                "Re-run with the -verbose option for more details."},
        {"Re-run with the -verbose and -certs options for more details.",
                "Re-run with the -verbose and -certs options for more details."},
        {"The signer certificate has expired.",
                "The signer certificate has expired."},
        {"The signer certificate will expire within six months.",
                "The signer certificate will expire within six months."},
        {"The signer certificate is not yet valid.",
                "The signer certificate is not yet valid."},
        {"The signer certificate's KeyUsage extension doesn't allow code signing.",
                 "The signer certificate's KeyUsage extension doesn't allow code signing."},
        {"The signer certificate's ExtendedKeyUsage extension doesn't allow code signing.",
                 "The signer certificate's ExtendedKeyUsage extension doesn't allow code signing."},
        {"The signer certificate's NetscapeCertType extension doesn't allow code signing.",
                 "The signer certificate's NetscapeCertType extension doesn't allow code signing."},
        {"This jar contains entries whose signer certificate's KeyUsage extension doesn't allow code signing.",
                 "This jar contains entries whose signer certificate's KeyUsage extension doesn't allow code signing."},
        {"This jar contains entries whose signer certificate's ExtendedKeyUsage extension doesn't allow code signing.",
                 "This jar contains entries whose signer certificate's ExtendedKeyUsage extension doesn't allow code signing."},
        {"This jar contains entries whose signer certificate's NetscapeCertType extension doesn't allow code signing.",
                 "This jar contains entries whose signer certificate's NetscapeCertType extension doesn't allow code signing."},
        {"[{0} extension does not support code signing]",
                 "[{0} extension does not support code signing]"},
        {"The signer's certificate chain is not validated.",
                "The signer's certificate chain is not validated."},
        {"This jar contains entries whose certificate chain is not validated.",
                 "This jar contains entries whose certificate chain is not validated."},
    };

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     *
     * <p>
     *
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
        return contents;
    }
}
