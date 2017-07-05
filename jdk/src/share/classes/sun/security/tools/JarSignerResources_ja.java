/*
 * Copyright 2001-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
public class JarSignerResources_ja extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // shared (from jarsigner)
        {" ", " "},
        {"  ", "  "},
        {"      ", "      "},
        {", ", ", "},

        {"provName not a provider", "{0} \u306f\u30d7\u30ed\u30d0\u30a4\u30c0\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"signerClass is not a signing mechanism", "{0} \u306f\u7f72\u540d\u6a5f\u69cb\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"jarsigner error: ", "jarsigner \u30a8\u30e9\u30fc: "},
        {"Illegal option: ", "\u4e0d\u6b63\u306a\u30aa\u30d7\u30b7\u30e7\u30f3: "},
        {"-keystore must be NONE if -storetype is {0}",
                "-storetype \u304c {0} \u306e\u5834\u5408 -keystore \u306f NONE \u3067\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093"},
        {"-keypass can not be specified if -storetype is {0}",
                "-storetype \u304c {0} \u306e\u5834\u5408 -keypass \u306f\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093"},
        {"If -protected is specified, then -storepass and -keypass must not be specified",
                "-protected \u3092\u6307\u5b9a\u3059\u308b\u5834\u5408\u306f -storepass \u304a\u3088\u3073 -keypass \u3092\u6307\u5b9a\u3057\u306a\u3044\u3067\u304f\u3060\u3055\u3044\u3002"},
        {"If keystore is not password protected, then -storepass and -keypass must not be specified",
                 "\u30ad\u30fc\u30b9\u30c8\u30a2\u304c\u30d1\u30b9\u30ef\u30fc\u30c9\u3067\u4fdd\u8b77\u3055\u308c\u3066\u3044\u306a\u3044\u5834\u5408 -storepass \u304a\u3088\u3073 -keypass \u3092\u6307\u5b9a\u3057\u306a\u3044\u3067\u304f\u3060\u3055\u3044"},
        {"Usage: jarsigner [options] jar-file alias",
                "\u4f7f\u3044\u65b9: jarsigner [options] jar-file alias"},
        {"       jarsigner -verify [options] jar-file",
                "       jarsigner -verify [options] jar-file"},
        {"[-keystore <url>]           keystore location",
                "[-keystore <url>]           \u30ad\u30fc\u30b9\u30c8\u30a2\u306e\u4f4d\u7f6e"},
        {"[-storepass <password>]     password for keystore integrity",
            "[-storepass <password>]     \u30ad\u30fc\u30b9\u30c8\u30a2\u5b8c\u5168\u6027\u306e\u30d1\u30b9\u30ef\u30fc\u30c9"},
        {"[-storetype <type>]         keystore type",
                "[-storetype <type>]         \u30ad\u30fc\u30b9\u30c8\u30a2\u306e\u578b"},
        {"[-keypass <password>]       password for private key (if different)",
                "[-keypass <password>]       \u975e\u516c\u958b\u9375\u306e\u30d1\u30b9\u30ef\u30fc\u30c9 (\u7570\u306a\u308b\u5834\u5408)"},
        {"[-sigfile <file>]           name of .SF/.DSA file",
                "[-sigfile <file>]           .SF/.DSA \u30d5\u30a1\u30a4\u30eb\u306e\u540d\u524d"},
        {"[-signedjar <file>]         name of signed JAR file",
                "[-signedjar <file>]         \u7f72\u540d\u4ed8\u304d JAR \u30d5\u30a1\u30a4\u30eb\u306e\u540d\u524d"},
        {"[-digestalg <algorithm>]    name of digest algorithm",
                "[-digestalg <algorithm>]    \u30c0\u30a4\u30b8\u30a7\u30b9\u30c8\u30a2\u30eb\u30b4\u30ea\u30ba\u30e0\u306e\u540d\u524d"},
        {"[-sigalg <algorithm>]       name of signature algorithm",
                "[-sigalg <algorithm>]       \u7f72\u540d\u30a2\u30eb\u30b4\u30ea\u30ba\u30e0\u306e\u540d\u524d"},
        {"[-verify]                   verify a signed JAR file",
                "[-verify]                   \u7f72\u540d\u4ed8\u304d JAR \u30d5\u30a1\u30a4\u30eb\u306e\u691c\u8a3c"},
        {"[-verbose]                  verbose output when signing/verifying",
                "[-verbose]                  \u7f72\u540d/\u691c\u8a3c\u6642\u306e\u8a73\u7d30\u51fa\u529b"},
        {"[-certs]                    display certificates when verbose and verifying",
                "[-certs]                    \u8a73\u7d30\u51fa\u529b\u304a\u3088\u3073\u691c\u8a3c\u6642\u306b\u8a3c\u660e\u66f8\u3092\u8868\u793a"},
        {"[-tsa <url>]                location of the Timestamping Authority",
                "[-tsa <url>]                \u30bf\u30a4\u30e0\u30b9\u30bf\u30f3\u30d7\u5c40\u306e\u5834\u6240"},
        {"[-tsacert <alias>]          public key certificate for Timestamping Authority",
                "[-tsacert <alias>]          \u30bf\u30a4\u30e0\u30b9\u30bf\u30f3\u30d7\u5c40\u306e\u516c\u958b\u9375\u8a3c\u660e\u66f8"},
        {"[-altsigner <class>]        class name of an alternative signing mechanism",
                "[-altsigner <class>]        \u4ee3\u66ff\u7f72\u540d\u6a5f\u69cb\u306e\u30af\u30e9\u30b9\u540d"},
        {"[-altsignerpath <pathlist>] location of an alternative signing mechanism",
                "[-altsignerpath <pathlist>] \u4ee3\u66ff\u7f72\u540d\u6a5f\u69cb\u306e\u4f4d\u7f6e"},
        {"[-internalsf]               include the .SF file inside the signature block",
                "[-internalsf]               \u7f72\u540d\u30d6\u30ed\u30c3\u30af\u306b .SF \u30d5\u30a1\u30a4\u30eb\u3092\u542b\u3081\u308b"},
        {"[-sectionsonly]             don't compute hash of entire manifest",
                "[-sectionsonly]             \u30de\u30cb\u30d5\u30a7\u30b9\u30c8\u5168\u4f53\u306e\u30cf\u30c3\u30b7\u30e5\u306f\u8a08\u7b97\u3057\u306a\u3044"},
        {"[-protected]                keystore has protected authentication path",
                "[-protected]                \u8a8d\u8a3c\u30d1\u30b9\u304c\u4fdd\u8b77\u3055\u308c\u305f\u30ad\u30fc\u30b9\u30c8\u30a2"},
        {"[-providerName <name>]      provider name",
                "[-providerName <name>]      \u30d7\u30ed\u30d0\u30a4\u30c0\u540d"},
        {"[-providerClass <class>     name of cryptographic service provider's",
                "[-providerClass <class>     \u6697\u53f7\u5316\u30b5\u30fc\u30d3\u30b9\u30d7\u30ed\u30d0\u30a4\u30c0\u306e\u540d\u524d"},
        {"  [-providerArg <arg>]] ... master class file and constructor argument",
                "  [-providerArg <arg>]] ... \u30de\u30b9\u30bf\u30fc\u30af\u30e9\u30b9\u30d5\u30a1\u30a4\u30eb\u3068\u30b3\u30f3\u30b9\u30c8\u30e9\u30af\u30bf\u306e\u5f15\u6570"},
        {"s", "s"},
        {"m", "m"},
        {"k", "k"},
        {"i", "i"},
        {"  s = signature was verified ",
                "  s = \u7f72\u540d\u304c\u691c\u8a3c\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"  m = entry is listed in manifest",
                "  m = \u30a8\u30f3\u30c8\u30ea\u304c\u30de\u30cb\u30d5\u30a7\u30b9\u30c8\u5185\u306b\u30ea\u30b9\u30c8\u3055\u308c\u307e\u3059\u3002"},
        {"  k = at least one certificate was found in keystore",
                "  k = 1 \u3064\u4ee5\u4e0a\u306e\u8a3c\u660e\u66f8\u304c\u30ad\u30fc\u30b9\u30c8\u30a2\u3067\u691c\u51fa\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"  i = at least one certificate was found in identity scope",
                "  i = 1 \u3064\u4ee5\u4e0a\u306e\u8a3c\u660e\u66f8\u304c\u30a2\u30a4\u30c7\u30f3\u30c6\u30a3\u30c6\u30a3\u30b9\u30b3\u30fc\u30d7\u3067\u691c\u51fa\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"no manifest.", "\u30de\u30cb\u30d5\u30a7\u30b9\u30c8\u306f\u5b58\u5728\u3057\u307e\u305b\u3093\u3002"},
        {"jar is unsigned. (signatures missing or not parsable)",
                "jar \u306f\u7f72\u540d\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002(\u7f72\u540d\u304c\u898b\u3064\u304b\u3089\u306a\u3044\u304b\u3001\u69cb\u6587\u89e3\u6790\u3067\u304d\u307e\u305b\u3093)"},
        {"jar verified.", "jar \u304c\u691c\u8a3c\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"jarsigner: ", "jarsigner: "},
        {"signature filename must consist of the following characters: A-Z, 0-9, _ or -",
                "\u7f72\u540d\u306e\u30d5\u30a1\u30a4\u30eb\u540d\u306b\u4f7f\u7528\u3067\u304d\u308b\u6587\u5b57\u306f\u3001A-Z\u30010-9\u3001_\u3001- \u3060\u3051\u3067\u3059\u3002"},
        {"unable to open jar file: ", "\u6b21\u306e jar \u30d5\u30a1\u30a4\u30eb\u3092\u958b\u304f\u3053\u3068\u304c\u3067\u304d\u307e\u305b\u3093: "},
        {"unable to create: ", "\u4f5c\u6210\u3067\u304d\u307e\u305b\u3093: "},
        {"   adding: ", "   \u8ffd\u52a0\u4e2d: "},
        {" updating: ", " \u66f4\u65b0\u4e2d: "},
        {"  signing: ", "  \u7f72\u540d\u4e2d: "},
        {"attempt to rename signedJarFile to jarFile failed",
                "{0} \u3092 {1} \u306b\u5909\u66f4\u3057\u3088\u3046\u3068\u3057\u307e\u3057\u305f\u304c\u5931\u6557\u3057\u307e\u3057\u305f\u3002"},
        {"attempt to rename jarFile to origJar failed",
                "{0} \u3092 {1} \u306b\u5909\u66f4\u3057\u3088\u3046\u3068\u3057\u307e\u3057\u305f\u304c\u5931\u6557\u3057\u307e\u3057\u305f\u3002"},
        {"unable to sign jar: ", "jar \u306b\u7f72\u540d\u3067\u304d\u307e\u305b\u3093: "},
        {"Enter Passphrase for keystore: ", "\u30ad\u30fc\u30b9\u30c8\u30a2\u306e\u30d1\u30b9\u30ef\u30fc\u30c9\u3092\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044: "},
        {"keystore load: ", "\u30ad\u30fc\u30b9\u30c8\u30a2\u306e\u30ed\u30fc\u30c9: "},
        {"certificate exception: ", "\u8a3c\u660e\u66f8\u4f8b\u5916: "},
        {"unable to instantiate keystore class: ",
                "\u30ad\u30fc\u30b9\u30c8\u30a2\u30af\u30e9\u30b9\u306e\u30a4\u30f3\u30b9\u30bf\u30f3\u30b9\u3092\u751f\u6210\u3067\u304d\u307e\u305b\u3093: "},
        {"Certificate chain not found for: alias.  alias must reference a valid KeyStore key entry containing a private key and corresponding public key certificate chain.",
                "\u6b21\u306e\u8a3c\u660e\u9023\u9396\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: {0}\u3002  {1} \u306f\u3001\u975e\u516c\u958b\u9375\u304a\u3088\u3073\u5bfe\u5fdc\u3059\u308b\u516c\u958b\u9375\u8a3c\u660e\u9023\u9396\u3092\u542b\u3080\u6709\u52b9\u306a KeyStore \u9375\u30a8\u30f3\u30c8\u30ea\u3092\u53c2\u7167\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059\u3002"},
        {"found non-X.509 certificate in signer's chain",
                "\u7f72\u540d\u8005\u306e\u9023\u9396\u5185\u3067\u975e X.509 \u8a3c\u660e\u66f8\u304c\u691c\u51fa\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"incomplete certificate chain", "\u4e0d\u5b8c\u5168\u306a\u8a3c\u660e\u9023\u9396"},
        {"Enter key password for alias: ", "{0} \u306e\u9375\u30d1\u30b9\u30ef\u30fc\u30c9\u3092\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044: "},
        {"unable to recover key from keystore",
                "\u30ad\u30fc\u30b9\u30c8\u30a2\u304b\u3089\u9375\u3092\u5fa9\u5143\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"key associated with alias not a private key",
                "{0} \u3068\u95a2\u9023\u4ed8\u3051\u3089\u308c\u305f\u9375\u306f\u3001\u975e\u516c\u958b\u9375\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"you must enter key password", "\u9375\u30d1\u30b9\u30ef\u30fc\u30c9\u3092\u5165\u529b\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059\u3002"},
        {"unable to read password: ", "\u30d1\u30b9\u30ef\u30fc\u30c9\u3092\u8aad\u307f\u8fbc\u3081\u307e\u305b\u3093: "},
        {"certificate is valid from", "\u8a3c\u660e\u66f8\u306f {0} \u304b\u3089 {1} \u307e\u3067\u6709\u52b9\u3067\u3059"},
        {"certificate expired on", "\u8a3c\u660e\u66f8\u306f {0} \u306b\u5931\u52b9\u3057\u307e\u3057\u305f"},
        {"certificate is not valid until",
                "\u8a3c\u660e\u66f8\u306f {0} \u307e\u3067\u6709\u52b9\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"certificate will expire on", "\u8a3c\u660e\u66f8\u306f {0} \u306b\u5931\u52b9\u3057\u307e\u3059"},
        {"requesting a signature timestamp",
                "\u7f72\u540d\u30bf\u30a4\u30e0\u30b9\u30bf\u30f3\u30d7\u306e\u8981\u6c42"},
        {"TSA location: ", "TSA \u306e\u5834\u6240: "},
        {"TSA certificate: ", "TSA \u8a3c\u660e\u66f8: "},
        {"no response from the Timestamping Authority. ",
                "\u30bf\u30a4\u30e0\u30b9\u30bf\u30f3\u30d7\u5c40\u304b\u3089\u306e\u5fdc\u7b54\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"When connecting from behind a firewall then an HTTP proxy may need to be specified. ",
                "\u30d5\u30a1\u30a4\u30a2\u30a6\u30a9\u30fc\u30eb\u3092\u4ecb\u3057\u3066\u63a5\u7d9a\u3059\u308b\u3068\u304d\u306f\u3001\u5fc5\u8981\u306b\u5fdc\u3058\u3066 HTTP \u30d7\u30ed\u30ad\u30b7\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"Supply the following options to jarsigner: ",
                "jarsigner \u306b\u6b21\u306e\u30aa\u30d7\u30b7\u30e7\u30f3\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044: "},
        {"Certificate not found for: alias.  alias must reference a valid KeyStore entry containing an X.509 public key certificate for the Timestamping Authority.",
                "\u8a3c\u660e\u66f8\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3067\u3057\u305f: {0}\u3002{1} \u306f\u30bf\u30a4\u30e0\u30b9\u30bf\u30f3\u30d7\u5c40\u306e X.509 \u516c\u958b\u9375\u8a3c\u660e\u66f8\u304c\u542b\u307e\u308c\u3066\u3044\u308b\u6709\u52b9\u306a KeyStore \u30a8\u30f3\u30c8\u30ea\u3092\u6307\u3057\u3066\u3044\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059\u3002"},
        {"using an alternative signing mechanism",
                "\u4ee3\u66ff\u7f72\u540d\u6a5f\u69cb\u306e\u4f7f\u7528"},
        {"entry was signed on", "\u30a8\u30f3\u30c8\u30ea\u306f {0} \u306b\u7f72\u540d\u3055\u308c\u307e\u3057\u305f"},
        {"Warning: ", "\u8b66\u544a: "},
        {"This jar contains unsigned entries which have not been integrity-checked. ",
                "\u3053\u306e jar \u306b\u306f\u3001\u6574\u5408\u6027\u30c1\u30a7\u30c3\u30af\u3092\u3057\u3066\u3044\u306a\u3044\u672a\u7f72\u540d\u306e\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002 "},
        {"This jar contains entries whose signer certificate has expired. ",
                "\u3053\u306e jar \u306b\u306f\u3001\u7f72\u540d\u8005\u306e\u8a3c\u660e\u66f8\u304c\u671f\u9650\u5207\u308c\u306e\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002 "},
        {"This jar contains entries whose signer certificate will expire within six months. ",
                "\u3053\u306e jar \u306b\u306f\u3001\u7f72\u540d\u8005\u306e\u8a3c\u660e\u66f8\u304c 6 \u304b\u6708\u4ee5\u5185\u306b\u671f\u9650\u5207\u308c\u3068\u306a\u308b\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002 "},
        {"This jar contains entries whose signer certificate is not yet valid. ",
                "\u3053\u306e jar \u306b\u306f\u3001\u7f72\u540d\u8005\u306e\u8a3c\u660e\u66f8\u304c\u307e\u3060\u6709\u52b9\u306b\u306a\u3063\u3066\u3044\u306a\u3044\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002 "},
        {"Re-run with the -verbose option for more details.",
                "\u8a73\u7d30\u306b\u3064\u3044\u3066\u306f -verbose \u30aa\u30d7\u30b7\u30e7\u30f3\u3092\u4f7f\u7528\u3057\u3066\u518d\u5b9f\u884c\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"Re-run with the -verbose and -certs options for more details.",
                "\u8a73\u7d30\u306b\u3064\u3044\u3066\u306f -verbose \u304a\u3088\u3073 -certs \u30aa\u30d7\u30b7\u30e7\u30f3\u3092\u4f7f\u7528\u3057\u3066\u518d\u5b9f\u884c\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"The signer certificate has expired.",
                "\u7f72\u540d\u8005\u306e\u8a3c\u660e\u66f8\u306f\u671f\u9650\u5207\u308c\u3067\u3059\u3002"},
        {"The signer certificate will expire within six months.",
                "\u7f72\u540d\u8005\u306e\u8a3c\u660e\u66f8\u306f 6 \u304b\u6708\u4ee5\u5185\u306b\u671f\u9650\u5207\u308c\u306b\u306a\u308a\u307e\u3059\u3002"},
        {"The signer certificate is not yet valid.",
                "\u7f72\u540d\u8005\u306e\u8a3c\u660e\u66f8\u306f\u307e\u3060\u6709\u52b9\u306b\u306a\u3063\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"The signer certificate's KeyUsage extension doesn't allow code signing.",
                 "\u7f72\u540d\u8005\u8a3c\u660e\u66f8\u306e KeyUsage \u62e1\u5f35\u6a5f\u80fd\u3067\u306f\u3001\u30b3\u30fc\u30c9\u7f72\u540d\u306f\u8a31\u53ef\u3055\u308c\u307e\u305b\u3093\u3002"},
        {"The signer certificate's ExtendedKeyUsage extension doesn't allow code signing.",
                 "\u7f72\u540d\u8005\u8a3c\u660e\u66f8\u306e ExtendedKeyUsage \u62e1\u5f35\u6a5f\u80fd\u3067\u306f\u3001\u30b3\u30fc\u30c9\u7f72\u540d\u306f\u8a31\u53ef\u3055\u308c\u307e\u305b\u3093\u3002"},
        {"The signer certificate's NetscapeCertType extension doesn't allow code signing.",
                 "\u7f72\u540d\u8005\u8a3c\u660e\u66f8\u306e NetscapeCertType \u62e1\u5f35\u6a5f\u80fd\u3067\u306f\u3001\u30b3\u30fc\u30c9\u7f72\u540d\u306f\u8a31\u53ef\u3055\u308c\u307e\u305b\u3093\u3002"},
         {"This jar contains entries whose signer certificate's KeyUsage extension doesn't allow code signing.",
                  "\u3053\u306e jar \u306b\u306f\u3001\u7f72\u540d\u8005\u8a3c\u660e\u66f8\u306e KeyUsage \u62e1\u5f35\u6a5f\u80fd\u304c\u30b3\u30fc\u30c9\u7f72\u540d\u3092\u8a31\u53ef\u3057\u306a\u3044\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002"},
         {"This jar contains entries whose signer certificate's ExtendedKeyUsage extension doesn't allow code signing.",
                  "\u3053\u306e jar \u306b\u306f\u3001\u7f72\u540d\u8005\u8a3c\u660e\u66f8\u306e ExtendedKeyUsage \u62e1\u5f35\u6a5f\u80fd\u304c\u30b3\u30fc\u30c9\u7f72\u540d\u3092\u8a31\u53ef\u3057\u306a\u3044\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002"},
         {"This jar contains entries whose signer certificate's NetscapeCertType extension doesn't allow code signing.",
                  "\u3053\u306e jar \u306b\u306f\u3001\u7f72\u540d\u8005\u8a3c\u660e\u66f8\u306e NetscapeCertType \u62e1\u5f35\u6a5f\u80fd\u304c\u30b3\u30fc\u30c9\u7f72\u540d\u3092\u8a31\u53ef\u3057\u306a\u3044\u30a8\u30f3\u30c8\u30ea\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059\u3002"},
        {"[{0} extension does not support code signing]",
                 "[{0} \u62e1\u5f35\u6a5f\u80fd\u306f\u30b3\u30fc\u30c9\u7f72\u540d\u3092\u30b5\u30dd\u30fc\u30c8\u3057\u3066\u3044\u307e\u305b\u3093]"},
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
