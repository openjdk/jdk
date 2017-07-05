/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.util;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the following packages:
 *
 * <ol>
 * <li> com.sun.security.auth
 * <li> com.sun.security.auth.login
 * </ol>
 *
 */
public class AuthResources_ja extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid null input: value", "\u7121\u52b9\u306a null \u306e\u5165\u529b: {0}"},
        {"NTDomainPrincipal: name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential: name", "NTNumericCredential: {0}"},
        {"Invalid NTSid value", "\u7121\u52b9\u306a NTSid \u5024"},
        {"NTSid: name", "NTSid: {0}"},
        {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal: name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal: name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal [Primary Group]: name",
                "UnixNumericGroupPrincipal [\u4e3b\u30b0\u30eb\u30fc\u30d7]: {0}"},
        {"UnixNumericGroupPrincipal [Supplementary Group]: name",
                "UnixNumericGroupPrincipal [\u88dc\u52a9\u30b0\u30eb\u30fc\u30d7]: {0}"},
        {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal: name", "UnixPrincipal: {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable to properly expand config", "{0} \u3092\u6b63\u3057\u304f\u5c55\u958b\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"extra_config (No such file or directory)",
                "{0} (\u6307\u5b9a\u3055\u308c\u305f\u30d5\u30a1\u30a4\u30eb\u307e\u305f\u306f\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u306f\u5b58\u5728\u3057\u307e\u305b\u3093)"},
        {"Configuration Error:\n\tNo such file or directory",
                "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t\u6307\u5b9a\u3055\u308c\u305f\u30d5\u30a1\u30a4\u30eb\u307e\u305f\u306f\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u306f\u5b58\u5728\u3057\u307e\u305b\u3093\u3002"},
        {"Configuration Error:\n\tInvalid control flag, flag",
                "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t\u7121\u52b9\u306a\u5236\u5fa1\u30d5\u30e9\u30b0: {0}"},
        {"Configuration Error:\n\tCan not specify multiple entries for appName",
            "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t{0} \u306b\u8907\u6570\u306e\u30a8\u30f3\u30c8\u30ea\u3092\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"Configuration Error:\n\texpected [expect], read [end of file]",
                "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t[{0}] \u3067\u306f\u306a\u304f\u3001[\u30d5\u30a1\u30a4\u30eb\u306e\u7d42\u308f\u308a] \u304c\u8aad\u307f\u8fbc\u307e\u308c\u307e\u3057\u305f\u3002"},
        {"Configuration Error:\n\tLine line: expected [expect], found [value]",
            "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t\u884c {0}: [{1}] \u3067\u306f\u306a\u304f\u3001[{2}] \u304c\u691c\u51fa\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"Configuration Error:\n\tLine line: expected [expect]",
            "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t\u884c {0}: [{1}] \u304c\u8981\u6c42\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
            "\u69cb\u6210\u30a8\u30e9\u30fc:\n\t\u884c {0}: \u30b7\u30b9\u30c6\u30e0\u30d7\u30ed\u30d1\u30c6\u30a3\u30fc [{1}] \u304c\u7a7a\u306e\u5024\u306b\u5c55\u958b\u3055\u308c\u307e\u3057\u305f\u3002"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username: ","\u30e6\u30fc\u30b6\u540d: "},
        {"password: ","\u30d1\u30b9\u30ef\u30fc\u30c9: "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please enter keystore information",
                "\u30ad\u30fc\u30b9\u30c8\u30a2\u60c5\u5831\u3092\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"Keystore alias: ","\u30ad\u30fc\u30b9\u30c8\u30a2\u306e\u5225\u540d: "},
        {"Keystore password: ","\u30ad\u30fc\u30b9\u30c8\u30a2\u306e\u30d1\u30b9\u30ef\u30fc\u30c9: "},
        {"Private key password (optional): ",
            "\u975e\u516c\u958b\u9375\u306e\u30d1\u30b9\u30ef\u30fc\u30c9 (\u30aa\u30d7\u30b7\u30e7\u30f3): "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos username [[defUsername]]: ",
                "Kerberos \u30e6\u30fc\u30b6\u540d [{0}]: "},
        {"Kerberos password for [username]: ",
                "{0} \u306e Kerberos \u30d1\u30b9\u30ef\u30fc\u30c9: "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {": error parsing ", ": \u69cb\u6587\u89e3\u6790\u30a8\u30e9\u30fc "},
        {": ", ": "},
        {": error adding Permission ", ": \u30a2\u30af\u30bb\u30b9\u6a29\u306e\u8ffd\u52a0\u30a8\u30e9\u30fc "},
        {" ", " "},
        {": error adding Entry ", ": \u30a8\u30f3\u30c8\u30ea\u306e\u8ffd\u52a0\u30a8\u30e9\u30fc "},
        {"(", "("},
        {")", ")"},
        {"attempt to add a Permission to a readonly PermissionCollection",
            "\u8aad\u307f\u53d6\u308a\u5c02\u7528\u306e PermissionCollection \u306b\u30a2\u30af\u30bb\u30b9\u6a29\u306e\u8ffd\u52a0\u304c\u8a66\u884c\u3055\u308c\u307e\u3057\u305f\u3002"},

        // com.sun.security.auth.PolicyParser
        {"expected keystore type", "\u671f\u5f85\u3055\u308c\u305f\u30ad\u30fc\u30b9\u30c8\u30a2\u578b"},
        {"can not specify Principal with a ",
                "\u30ef\u30a4\u30eb\u30c9\u30ab\u30fc\u30c9\u540d\u306e\u306a\u3044\u30ef\u30a4\u30eb\u30c9\u30ab\u30fc\u30c9\u30af\u30e9\u30b9\u3092"},
        {"wildcard class without a wildcard name",
                "\u4f7f\u3063\u3066 Principal \u3092\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"expected codeBase or SignedBy", "\u671f\u5f85\u3055\u308c\u305f codeBase \u307e\u305f\u306f SignedBy"},
        {"only Principal-based grant entries permitted",
                "Principal \u30d9\u30fc\u30b9\u306e\u30a8\u30f3\u30c8\u30ea\u3060\u3051\u304c\u8a31\u53ef\u3055\u308c\u307e\u3059\u3002"},
        {"expected permission entry", "\u671f\u5f85\u3055\u308c\u305f\u30a2\u30af\u30bb\u30b9\u6a29\u306e\u30a8\u30f3\u30c8\u30ea"},
        {"number ", "\u6570 "},
        {"expected ", "\u671f\u5f85\u5024 "},
        {", read end of file", ", \u30d5\u30a1\u30a4\u30eb\u306e\u7d42\u308f\u308a\u304c\u8aad\u307f\u8fbc\u307e\u308c\u307e\u3057\u305f\u3002"},
        {"expected ';', read end of file", "\u671f\u5f85\u5024 ';', \u30d5\u30a1\u30a4\u30eb\u306e\u7d42\u308f\u308a\u304c\u8aad\u307f\u8fbc\u307e\u308c\u307e\u3057\u305f"},
        {"line ", "\u884c\u756a\u53f7 "},
        {": expected '", ": \u671f\u5f85\u5024 '"},
        {"', found '", "', \u691c\u51fa\u5024 '"},
        {"'", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal [Primary Group]: ",
                "SolarisNumericGroupPrincipal [\u4e3b\u30b0\u30eb\u30fc\u30d7]: "},
        {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
                "SolarisNumericGroupPrincipal [\u88dc\u52a9\u30b0\u30eb\u30fc\u30d7]: "},
        {"SolarisNumericUserPrincipal: ",
                "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal: ", "SolarisPrincipal: "},
        {"provided null name", "\u6307\u5b9a\u3055\u308c\u305f null \u540d"}

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
