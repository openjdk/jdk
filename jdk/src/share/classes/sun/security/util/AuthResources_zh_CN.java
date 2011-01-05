/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
public class AuthResources_zh_CN extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid null input: value", "\u65e0\u6548\u7684\u7a7a\u8f93\u5165\uff1a {0}"},
        {"NTDomainPrincipal: name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential: name", "NTNumericCredential: {0}"},
        {"Invalid NTSid value", "\u65e0\u6548\u7684 NTSid \u503c"},
        {"NTSid: name", "NTSid: {0}"},
        {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal: name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal: name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal [Primary Group]: name",
                "UnixNumericGroupPrincipal [\u4e3b\u7fa4\u7ec4]\uff1a {0}"},
        {"UnixNumericGroupPrincipal [Supplementary Group]: name",
                "UnixNumericGroupPrincipal [\u9644\u52a0\u7fa4\u7ec4]\uff1a {0}"},
        {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal: name", "UnixPrincipal\uff1a {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable to properly expand config", "\u65e0\u6cd5\u5b8c\u5168\u6269\u5145 {0}"},
        {"extra_config (No such file or directory)",
                "{0} \uff08\u6ca1\u6709\u6b64\u6587\u4ef6\u6216\u76ee\u5f55\uff09"},
        {"Configuration Error:\n\tNo such file or directory",
                "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u6ca1\u6709\u6b64\u6587\u4ef6\u6216\u76ee\u5f55"},
        {"Configuration Error:\n\tInvalid control flag, flag",
                "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u65e0\u6548\u7684\u63a7\u5236\u6807\u8bb0\uff0c {0}"},
        {"Configuration Error:\n\tCan not specify multiple entries for appName",
            "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u65e0\u6cd5\u6307\u5b9a\u591a\u4e2a\u9879\u76ee {0}"},
        {"Configuration Error:\n\texpected [expect], read [end of file]",
                "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u9884\u671f\u7684 [{0}], \u8bfb\u53d6 [\u6587\u4ef6\u672b\u7aef]"},
        {"Configuration Error:\n\tLine line: expected [expect], found [value]",
            "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u884c {0}: \u9884\u671f\u7684 [{1}], \u627e\u5230 [{2}]"},
        {"Configuration Error:\n\tLine line: expected [expect]",
            "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u884c {0}: \u9884\u671f\u7684 [{1}]"},
        {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
            "\u914d\u7f6e\u9519\u8bef\uff1a\n\t\u884c {0}: \u7cfb\u7edf\u5c5e\u6027 [{1}] \u6269\u5145\u81f3\u7a7a\u503c"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username: ","\u7528\u6237\u540d\uff1a "},
        {"password: ","\u5bc6\u7801\uff1a "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please enter keystore information",
                "\u8bf7\u8f93\u5165 keystore \u4fe1\u606f"},
        {"Keystore alias: ","Keystore \u522b\u540d\uff1a "},
        {"Keystore password: ","Keystore \u5bc6\u7801\uff1a "},
        {"Private key password (optional): ",
            "\u79c1\u4eba\u5173\u952e\u5bc6\u7801\uff08\u53ef\u9009\u7684\uff09\uff1a "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos username [[defUsername]]: ",
                "Kerberos \u7528\u6237\u540d [{0}]: "},
        {"Kerberos password for [username]: ",
                " {0} \u7684 Kerberos \u5bc6\u7801: "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {": error parsing ", "\uff1a\u8bed\u6cd5\u89e3\u6790\u9519\u8bef "},
        {": ", ": "},
        {": error adding Permission ", "\uff1a\u6dfb\u52a0\u6743\u9650\u9519\u8bef "},
        {" ", " "},
        {": error adding Entry ", "\u6dfb\u52a0\u9879\u76ee\u9519\u8bef "},
        {"(", "("},
        {")", ")"},
        {"attempt to add a Permission to a readonly PermissionCollection",
            "\u8bd5\u56fe\u5c06\u6743\u9650\u6dfb\u52a0\u81f3\u53ea\u8bfb\u7684 PermissionCollection"},

        // com.sun.security.auth.PolicyParser
        {"expected keystore type", "\u9884\u671f\u7684 keystore \u7c7b\u578b"},
        {"can not specify Principal with a ",
                "\u65e0\u6cd5\u4ee5\u6b64\u6765\u6307\u5b9a Principal "},
        {"wildcard class without a wildcard name",
                "\u65e0\u901a\u914d\u5b57\u7b26\u540d\u79f0\u7684\u901a\u914d\u5b57\u7b26\u7c7b"},
        {"expected codeBase or SignedBy", "\u9884\u671f\u7684 codeBase \u6216 SignedBy"},
        {"only Principal-based grant entries permitted",
                "\u53ea\u5141\u8bb8\u57fa\u4e8e Principal \u7684\u6388\u6743\u9879\u76ee"},
        {"expected permission entry", "\u9884\u671f\u7684\u6743\u9650\u9879\u76ee"},
        {"number ", "\u53f7\u7801 "},
        {"expected ", "\u9884\u671f\u7684 "},
        {", read end of file", "\uff0c\u8bfb\u53d6\u6587\u4ef6\u672b\u7aef"},
        {"expected ';', read end of file", "\u9884\u671f\u7684 ';', \u8bfb\u53d6\u6587\u4ef6\u672b\u7aef"},
        {"line ", "\u884c "},
        {": expected '", ": \u9884\u671f\u7684 '"},
        {"', found '", "', \u627e\u5230 '"},
        {"'", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal [Primary Group]: ",
                "SolarisNumericGroupPrincipal [\u4e3b\u7fa4\u7ec4]\uff1a "},
        {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
                "SolarisNumericGroupPrincipal [\u9644\u52a0\u7fa4\u7ec4]\uff1a "},
        {"SolarisNumericUserPrincipal: ",
                "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal: ", "SolarisPrincipal: "},
        {"provided null name", "\u5df2\u63d0\u4f9b\u7684\u7a7a\u540d\u79f0"}

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
