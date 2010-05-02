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
public class JarSignerResources_zh_CN extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // shared (from jarsigner)
        {" ", " "},
        {"  ", "  "},
        {"      ", "      "},
        {", ", ", "},

        {"provName not a provider", "{0} \u4e0d\u662f\u63d0\u4f9b\u8005"},
        {"signerClass is not a signing mechanism", "{0} \u4e0d\u662f\u7b7e\u540d\u673a\u5236"},
        {"jarsigner error: ", "jarsigner \u9519\u8bef\uff1a "},
        {"Illegal option: ", "\u975e\u6cd5\u9009\u9879\uff1a "},
        {"-keystore must be NONE if -storetype is {0}",
                "\u5982\u679c -storetype \u4e3a {0}\uff0c\u5219 -keystore \u5fc5\u987b\u4e3a NONE"},
        {"-keypass can not be specified if -storetype is {0}",
                "\u5982\u679c -storetype \u4e3a {0}\uff0c\u5219\u4e0d\u80fd\u6307\u5b9a -keypass"},
        {"If -protected is specified, then -storepass and -keypass must not be specified",
                "\u5982\u679c\u6307\u5b9a\u4e86 -protected\uff0c\u5219\u4e0d\u80fd\u6307\u5b9a -storepass \u548c -keypass"},
        {"If keystore is not password protected, then -storepass and -keypass must not be specified",
                 "\u5982\u679c\u5bc6\u94a5\u5e93\u672a\u53d7\u5bc6\u7801\u4fdd\u62a4\uff0c\u5219\u8bf7\u52ff\u6307\u5b9a -storepass \u548c -keypass"},
        {"Usage: jarsigner [options] jar-file alias",
                "\u7528\u6cd5\uff1ajarsigner [\u9009\u9879] jar \u6587\u4ef6\u522b\u540d"},
        {"       jarsigner -verify [options] jar-file [alias...]",
                "       jarsigner -verify [\u9009\u9879] jar \u6587\u4ef6 [\u522b\u540d...]"},
        {"[-keystore <url>]           keystore location",
                "[-keystore <url>]           \u5bc6\u94a5\u5e93\u4f4d\u7f6e"},
        {"[-storepass <password>]     password for keystore integrity",
            "[-storepass <\u53e3\u4ee4>]         \u7528\u4e8e\u5bc6\u94a5\u5e93\u5b8c\u6574\u6027\u7684\u53e3\u4ee4"},
        {"[-storetype <type>]         keystore type",
                "[-storetype <\u7c7b\u578b>]         \u5bc6\u94a5\u5e93\u7c7b\u578b"},
        {"[-keypass <password>]       password for private key (if different)",
                "[-keypass <\u53e3\u4ee4>]           \u4e13\u7528\u5bc6\u94a5\u7684\u53e3\u4ee4\uff08\u5982\u679c\u4e0d\u540c\uff09"},
        {"[-certchain <file>]         name of alternative certchain file",
                "[-certchain <\u6587\u4ef6>]         \u66ff\u4ee3\u8bc1\u4e66\u94fe\u6587\u4ef6\u7684\u540d\u79f0"},
        {"[-sigfile <file>]           name of .SF/.DSA file",
                "[-sigfile <\u6587\u4ef6>]           .SF/.DSA \u6587\u4ef6\u7684\u540d\u79f0"},
        {"[-signedjar <file>]         name of signed JAR file",
                "[-signedjar <\u6587\u4ef6>]         \u5df2\u7b7e\u540d\u7684 JAR \u6587\u4ef6\u7684\u540d\u79f0"},
        {"[-digestalg <algorithm>]    name of digest algorithm",
                "[-digestalg <\u7b97\u6cd5>]    \u6458\u8981\u7b97\u6cd5\u7684\u540d\u79f0"},
        {"[-sigalg <algorithm>]       name of signature algorithm",
                "[-sigalg <\u7b97\u6cd5>]       \u7b7e\u540d\u7b97\u6cd5\u7684\u540d\u79f0"},
        {"[-verify]                   verify a signed JAR file",
                "[-verify]                   \u9a8c\u8bc1\u5df2\u7b7e\u540d\u7684 JAR \u6587\u4ef6"},
        {"[-verbose[:suboptions]]     verbose output when signing/verifying.",
                "[-verbose[:\u5b50\u9009\u9879]]     \u7b7e\u540d/\u9a8c\u8bc1\u65f6\u8f93\u51fa\u8be6\u7ec6\u4fe1\u606f\u3002"},
        {"                            suboptions can be all, grouped or summary",
                "                            \u5b50\u9009\u9879\u53ef\u4ee5\u4e3a all\u3001grouped \u6216 summary"},
        {"[-certs]                    display certificates when verbose and verifying",
                "[-certs]                    \u8f93\u51fa\u8be6\u7ec6\u4fe1\u606f\u548c\u9a8c\u8bc1\u65f6\u663e\u793a\u8bc1\u4e66"},
        {"[-tsa <url>]                location of the Timestamping Authority",
                "[-tsa <url>]                \u65f6\u95f4\u6233\u673a\u6784\u7684\u4f4d\u7f6e"},
        {"[-tsacert <alias>]          public key certificate for Timestamping Authority",
                "[-tsacert <\u522b\u540d>]           \u65f6\u95f4\u6233\u673a\u6784\u7684\u516c\u5171\u5bc6\u94a5\u8bc1\u4e66"},
        {"[-altsigner <class>]        class name of an alternative signing mechanism",
                "[-altsigner <\u7c7b>]           \u66ff\u4ee3\u7684\u7b7e\u540d\u673a\u5236\u7684\u7c7b\u540d"},
        {"[-altsignerpath <pathlist>] location of an alternative signing mechanism",
                "[-altsignerpath <\u8def\u5f84\u5217\u8868>] \u66ff\u4ee3\u7684\u7b7e\u540d\u673a\u5236\u7684\u4f4d\u7f6e"},
        {"[-internalsf]               include the .SF file inside the signature block",
                "[-internalsf]               \u5728\u7b7e\u540d\u5757\u5185\u5305\u542b .SF \u6587\u4ef6"},
        {"[-sectionsonly]             don't compute hash of entire manifest",
                "[-sectionsonly]             \u4e0d\u8ba1\u7b97\u6574\u4e2a\u6e05\u5355\u7684\u6563\u5217"},
        {"[-protected]                keystore has protected authentication path",
                "[-protected]                \u5bc6\u94a5\u5e93\u5df2\u4fdd\u62a4\u9a8c\u8bc1\u8def\u5f84"},
        {"[-providerName <name>]      provider name",
                "[-providerName <\u540d\u79f0>]      \u63d0\u4f9b\u8005\u540d\u79f0"},
        {"[-providerClass <class>     name of cryptographic service provider's",
                "[-providerClass <\u7c7b>        \u52a0\u5bc6\u670d\u52a1\u63d0\u4f9b\u8005\u7684\u540d\u79f0"},
        {"  [-providerArg <arg>]] ... master class file and constructor argument",
                "  [-providerArg <\u53c2\u6570>]] ... \u4e3b\u7c7b\u6587\u4ef6\u548c\u6784\u9020\u51fd\u6570\u53c2\u6570"},
        {"[-strict]                   treat warnings as errors",
                "[-strict]                   \u5c06\u8b66\u544a\u89c6\u4e3a\u9519\u8bef"},
        {"Option lacks argument", "\u9009\u9879\u7f3a\u5c11\u53c2\u6570"},
        {"Please type jarsigner -help for usage", "\u6709\u5173\u7528\u6cd5\uff0c\u8bf7\u952e\u5165 jarsigner -help"},
        {"Please specify jarfile name", "\u8bf7\u6307\u5b9a jarfile \u540d\u79f0"},
        {"Please specify alias name", "\u8bf7\u6307\u5b9a\u522b\u540d"},
        {"Only one alias can be specified", "\u53ea\u80fd\u6307\u5b9a\u4e00\u4e2a\u522b\u540d"},
        {"This jar contains signed entries which is not signed by the specified alias(es).",
                 "\u6b64 jar \u5305\u542b\u6307\u5b9a\u522b\u540d\u672a\u7b7e\u540d\u7684\u7b7e\u540d\u6761\u76ee\u3002"},
        {"This jar contains signed entries that's not signed by alias in this keystore.",
                  "\u6b64 jar \u5305\u542b\u6b64\u5bc6\u94a5\u5e93\u4e2d\u522b\u540d\u672a\u7b7e\u540d\u7684\u7b7e\u540d\u6761\u76ee\u3002"},
        {"s", "s"},
        {"m", "m"},
        {"k", "k"},
        {"i", "i"},
        {"(and %d more)", "\uff08\u8fd8\u6709 %d\uff09"},
        {"  s = signature was verified ",
                "  s = \u5df2\u9a8c\u8bc1\u7b7e\u540d "},
        {"  m = entry is listed in manifest",
                "  m = \u5728\u6e05\u5355\u4e2d\u5217\u51fa\u6761\u76ee"},
        {"  k = at least one certificate was found in keystore",
                "  k = \u5728\u5bc6\u94a5\u5e93\u4e2d\u81f3\u5c11\u627e\u5230\u4e86\u4e00\u4e2a\u8bc1\u4e66"},
        {"  i = at least one certificate was found in identity scope",
                "  i = \u5728\u8eab\u4efd\u4f5c\u7528\u57df\u5185\u81f3\u5c11\u627e\u5230\u4e86\u4e00\u4e2a\u8bc1\u4e66"},
        {"  X = not signed by specified alias(es)",
                "  X = \u672a\u7ecf\u6307\u5b9a\u522b\u540d\u7b7e\u540d"},
        {"no manifest.", "\u6ca1\u6709\u6e05\u5355\u3002"},
        {"(Signature related entries)","\uff08\u4e0e\u7b7e\u540d\u6709\u5173\u7684\u6761\u76ee\uff09"},
        {"(Unsigned entries)", "\uff08\u672a\u7b7e\u540d\u7684\u6761\u76ee\uff09"},
        {"jar is unsigned. (signatures missing or not parsable)",
                "jar \u672a\u7b7e\u540d\u3002\uff08\u7f3a\u5c11\u7b7e\u540d\u6216\u7b7e\u540d\u65e0\u6cd5\u89e3\u6790\uff09"},
        {"jar verified.", "jar \u5df2\u9a8c\u8bc1\u3002"},
        {"jarsigner: ", "jarsigner\uff1a "},
        {"signature filename must consist of the following characters: A-Z, 0-9, _ or -",
                "\u7b7e\u540d\u6587\u4ef6\u540d\u5fc5\u987b\u5305\u542b\u4ee5\u4e0b\u5b57\u7b26\uff1aA-Z\u30010-9\u3001_ \u6216 -"},
        {"unable to open jar file: ", "\u65e0\u6cd5\u6253\u5f00 jar \u6587\u4ef6\uff1a "},
        {"unable to create: ", "\u65e0\u6cd5\u521b\u5efa\uff1a "},
        {"   adding: ", "   \u6b63\u5728\u6dfb\u52a0\uff1a "},
        {" updating: ", " \u6b63\u5728\u66f4\u65b0\uff1a "},
        {"  signing: ", "  \u6b63\u5728\u7b7e\u540d\uff1a "},
        {"attempt to rename signedJarFile to jarFile failed",
                "\u5c1d\u8bd5\u5c06 {0} \u91cd\u547d\u540d\u4e3a {1} \u5931\u8d25"},
        {"attempt to rename jarFile to origJar failed",
                "\u5c1d\u8bd5\u5c06 {0} \u91cd\u547d\u540d\u4e3a {1} \u5931\u8d25"},
        {"unable to sign jar: ", "\u65e0\u6cd5\u5bf9 jar \u8fdb\u884c\u7b7e\u540d\uff1a "},
        {"Enter Passphrase for keystore: ", "\u8f93\u5165\u5bc6\u94a5\u5e93\u7684\u53e3\u4ee4\u77ed\u8bed\uff1a "},
        {"keystore load: ", "\u5bc6\u94a5\u5e93\u88c5\u5165\uff1a "},
        {"certificate exception: ", "\u8bc1\u4e66\u5f02\u5e38\uff1a "},
        {"unable to instantiate keystore class: ",
                "\u65e0\u6cd5\u5b9e\u4f8b\u5316\u5bc6\u94a5\u5e93\u7c7b\uff1a "},
        {"Certificate chain not found for: alias.  alias must reference a valid KeyStore key entry containing a private key and corresponding public key certificate chain.",
                "\u627e\u4e0d\u5230 {0} \u7684\u8bc1\u4e66\u94fe\u3002{1} \u5fc5\u987b\u5f15\u7528\u5305\u542b\u4e13\u7528\u5bc6\u94a5\u548c\u76f8\u5e94\u7684\u516c\u5171\u5bc6\u94a5\u8bc1\u4e66\u94fe\u7684\u6709\u6548\u5bc6\u94a5\u5e93\u5bc6\u94a5\u6761\u76ee\u3002"},
        {"File specified by -certchain does not exist",
                "-certchain \u6307\u5b9a\u7684\u6587\u4ef6\u4e0d\u5b58\u5728"},
        {"Cannot restore certchain from file specified",
                "\u65e0\u6cd5\u4ece\u6307\u5b9a\u6587\u4ef6\u6062\u590d\u8bc1\u4e66\u94fe"},
        {"Certificate chain not found in the file specified.",
                "\u5728\u6307\u5b9a\u6587\u4ef6\u4e2d\u672a\u627e\u5230\u8bc1\u4e66\u94fe\u3002"},
        {"found non-X.509 certificate in signer's chain",
                "\u5728\u7b7e\u540d\u8005\u7684\u94fe\u4e2d\u627e\u5230\u975e X.509 \u8bc1\u4e66"},
        {"incomplete certificate chain", "\u8bc1\u4e66\u94fe\u4e0d\u5b8c\u6574"},
        {"Enter key password for alias: ", "\u8f93\u5165 {0} \u7684\u5bc6\u94a5\u53e3\u4ee4\uff1a "},
        {"unable to recover key from keystore",
                "\u65e0\u6cd5\u4ece\u5bc6\u94a5\u5e93\u4e2d\u6062\u590d\u5bc6\u94a5"},
        {"key associated with alias not a private key",
                "\u4e0e {0} \u76f8\u5173\u7684\u5bc6\u94a5\u4e0d\u662f\u4e13\u7528\u5bc6\u94a5"},
        {"you must enter key password", "\u60a8\u5fc5\u987b\u8f93\u5165\u5bc6\u94a5\u53e3\u4ee4"},
        {"unable to read password: ", "\u65e0\u6cd5\u8bfb\u53d6\u53e3\u4ee4\uff1a "},
        {"certificate is valid from", "\u8bc1\u4e66\u7684\u6709\u6548\u671f\u4e3a {0} \u81f3 {1}"},
        {"certificate expired on", "\u8bc1\u4e66\u5230\u671f\u65e5\u671f\u4e3a {0}"},
        {"certificate is not valid until",
                "\u76f4\u5230 {0}\uff0c\u8bc1\u4e66\u624d\u6709\u6548"},
        {"certificate will expire on", "\u8bc1\u4e66\u5c06\u5728 {0} \u5230\u671f"},
        {"[CertPath not validated: ", "[\u8bc1\u4e66\u8def\u5f84\u672a\u7ecf\u8fc7\u9a8c\u8bc1\uff1a"},
        {"requesting a signature timestamp",
                "\u6b63\u5728\u8bf7\u6c42\u7b7e\u540d\u65f6\u95f4\u6233"},
        {"TSA location: ", "TSA \u4f4d\u7f6e\uff1a "},
        {"TSA certificate: ", "TSA \u8bc1\u4e66\uff1a "},
        {"no response from the Timestamping Authority. ",
                "\u65f6\u95f4\u6233\u673a\u6784\u6ca1\u6709\u54cd\u5e94\u3002 "},
        {"When connecting from behind a firewall then an HTTP proxy may need to be specified. ",
                "\u5982\u679c\u8981\u4ece\u9632\u706b\u5899\u540e\u9762\u8fde\u63a5\uff0c\u5219\u53ef\u80fd\u9700\u8981\u6307\u5b9a HTTP \u4ee3\u7406\u3002 "},
        {"Supply the following options to jarsigner: ",
                "\u8bf7\u4e3a jarsigner \u63d0\u4f9b\u4ee5\u4e0b\u9009\u9879\uff1a "},
        {"Certificate not found for: alias.  alias must reference a valid KeyStore entry containing an X.509 public key certificate for the Timestamping Authority.",
                "\u627e\u4e0d\u5230 {0} \u7684\u8bc1\u4e66\u3002{1} \u5fc5\u987b\u5f15\u7528\u5305\u542b\u65f6\u95f4\u6233\u673a\u6784\u7684 X.509 \u516c\u5171\u5bc6\u94a5\u8bc1\u4e66\u7684\u6709\u6548\u5bc6\u94a5\u5e93\u6761\u76ee\u3002"},
        {"using an alternative signing mechanism",
                "\u6b63\u5728\u4f7f\u7528\u66ff\u4ee3\u7684\u7b7e\u540d\u673a\u5236"},
        {"entry was signed on", "\u6761\u76ee\u7684\u7b7e\u540d\u65e5\u671f\u4e3a {0}"},
        {"Warning: ", "\u8b66\u544a\uff1a "},
        {"This jar contains unsigned entries which have not been integrity-checked. ",
                "\u6b64 jar \u5305\u542b\u5c1a\u672a\u8fdb\u884c\u5b8c\u6574\u6027\u68c0\u67e5\u7684\u672a\u7b7e\u540d\u6761\u76ee\u3002 "},
        {"This jar contains entries whose signer certificate has expired. ",
                "\u6b64 jar \u5305\u542b\u7b7e\u540d\u8005\u8bc1\u4e66\u5df2\u8fc7\u671f\u7684\u6761\u76ee\u3002 "},
        {"This jar contains entries whose signer certificate will expire within six months. ",
                "\u6b64 jar \u5305\u542b\u7b7e\u540d\u8005\u8bc1\u4e66\u5c06\u5728\u516d\u4e2a\u6708\u5185\u8fc7\u671f\u7684\u6761\u76ee\u3002 "},
        {"This jar contains entries whose signer certificate is not yet valid. ",
                "\u6b64 jar \u5305\u542b\u7b7e\u540d\u8005\u8bc1\u4e66\u4ecd\u65e0\u6548\u7684\u6761\u76ee\u3002 "},
        {"Re-run with the -verbose option for more details.",
                "\u8981\u4e86\u89e3\u8be6\u7ec6\u4fe1\u606f\uff0c\u8bf7\u4f7f\u7528 -verbose \u9009\u9879\u91cd\u65b0\u8fd0\u884c\u3002"},
        {"Re-run with the -verbose and -certs options for more details.",
                "\u8981\u4e86\u89e3\u8be6\u7ec6\u4fe1\u606f\uff0c\u8bf7\u4f7f\u7528 -verbose \u548c -certs \u9009\u9879\u91cd\u65b0\u8fd0\u884c\u3002"},
        {"The signer certificate has expired.",
                "\u7b7e\u540d\u8005\u8bc1\u4e66\u5df2\u8fc7\u671f\u3002"},
        {"The signer certificate will expire within six months.",
                "\u7b7e\u540d\u8005\u8bc1\u4e66\u5c06\u5728\u516d\u4e2a\u6708\u5185\u8fc7\u671f\u3002"},
        {"The signer certificate is not yet valid.",
                "\u7b7e\u540d\u8005\u8bc1\u4e66\u4ecd\u65e0\u6548\u3002"},
        {"The signer certificate's KeyUsage extension doesn't allow code signing.",
                 "\u7531\u4e8e\u7b7e\u540d\u8005\u8bc1\u4e66\u7684 KeyUsage \u6269\u5c55\u800c\u65e0\u6cd5\u8fdb\u884c\u4ee3\u7801\u7b7e\u540d\u3002"},
        {"The signer certificate's ExtendedKeyUsage extension doesn't allow code signing.",
                 "\u7531\u4e8e\u7b7e\u540d\u8005\u8bc1\u4e66\u7684 ExtendedKeyUsage \u6269\u5c55\u800c\u65e0\u6cd5\u8fdb\u884c\u4ee3\u7801\u7b7e\u540d\u3002"},
        {"The signer certificate's NetscapeCertType extension doesn't allow code signing.",
                 "\u7531\u4e8e\u7b7e\u540d\u8005\u8bc1\u4e66\u7684 NetscapeCertType \u6269\u5c55\u800c\u65e0\u6cd5\u8fdb\u884c\u4ee3\u7801\u7b7e\u540d\u3002"},
        {"This jar contains entries whose signer certificate's KeyUsage extension doesn't allow code signing.",
                 "\u6b64 jar \u5305\u542b\u7531\u4e8e\u7b7e\u540d\u8005\u8bc1\u4e66\u7684 KeyUsage \u6269\u5c55\u800c\u65e0\u6cd5\u8fdb\u884c\u4ee3\u7801\u7b7e\u540d\u7684\u6761\u76ee\u3002"},
        {"This jar contains entries whose signer certificate's ExtendedKeyUsage extension doesn't allow code signing.",
                 "\u6b64 jar \u5305\u542b\u7531\u4e8e\u7b7e\u540d\u8005\u8bc1\u4e66\u7684 ExtendedKeyUsage \u6269\u5c55\u800c\u65e0\u6cd5\u8fdb\u884c\u4ee3\u7801\u7b7e\u540d\u7684\u6761\u76ee\u3002"},
        {"This jar contains entries whose signer certificate's NetscapeCertType extension doesn't allow code signing.",
                 "\u6b64 jar \u5305\u542b\u7531\u4e8e\u7b7e\u540d\u8005\u8bc1\u4e66\u7684 NetscapeCertType \u6269\u5c55\u800c\u65e0\u6cd5\u8fdb\u884c\u4ee3\u7801\u7b7e\u540d\u7684\u6761\u76ee\u3002"},
        {"[{0} extension does not support code signing]",
                 "[{0} \u6269\u5c55\u4e0d\u652f\u6301\u4ee3\u7801\u7b7e\u540d]"},
        {"The signer's certificate chain is not validated.",
                "\u7b7e\u540d\u8005\u7684\u8bc1\u4e66\u94fe\u672a\u7ecf\u8fc7\u9a8c\u8bc1\u3002"},
        {"This jar contains entries whose certificate chain is not validated.",
                 "\u6b64 jar \u5305\u542b\u8bc1\u4e66\u94fe\u672a\u7ecf\u8fc7\u9a8c\u8bc1\u7684\u6761\u76ee\u3002"},
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
