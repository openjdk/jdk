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
public class AuthResources_zh_TW extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid null input: value", "\u7121\u6548\u7a7a\u8f38\u5165\uff1a {0}"},
        {"NTDomainPrincipal: name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential: name", "NTNumericCredential: {0}"},
        {"Invalid NTSid value", "\u7121\u6548 NTSid \u503c"},
        {"NTSid: name", "NTSid: {0}"},
        {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal: name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal: name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal [Primary Group]: name",
                "UnixNumericGroupPrincipal [\u4e3b\u7fa4\u7d44]\uff1a {0}"},
        {"UnixNumericGroupPrincipal [Supplementary Group]: name",
                "UnixNumericGroupPrincipal [\u9644\u52a0\u7fa4\u7d44]\uff1a {0}"},
        {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal: name", "UnixPrincipal\uff1a {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable to properly expand config", "\u7121\u6cd5\u5b8c\u5168\u64f4\u5145 {0}"},
        {"extra_config (No such file or directory)",
                "{0} \uff08\u6c92\u6709\u6b64\u6a94\u6848\u6216\u76ee\u9304\uff09"},
        {"Configuration Error:\n\tNo such file or directory",
                "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u6c92\u6709\u9019\u985e\u7684\u6a94\u6848\u6216\u76ee\u9304"},
        {"Configuration Error:\n\tInvalid control flag, flag",
                "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u7121\u6548\u7684\u63a7\u5236\u65d7\u865f\uff0c {0}"},
        {"Configuration Error:\n\tCan not specify multiple entries for appName",
            "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u7121\u6cd5\u6307\u5b9a\u591a\u91cd\u9805\u76ee {0}"},
        {"Configuration Error:\n\texpected [expect], read [end of file]",
                "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u9810\u671f\u7684 [{0}], \u8b80\u53d6 [\u6a94\u6848\u672b\u7aef]"},
        {"Configuration Error:\n\tLine line: expected [expect], found [value]",
            "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u884c {0}: \u9810\u671f\u7684 [{1}], \u767c\u73fe [{2}]"},
        {"Configuration Error:\n\tLine line: expected [expect]",
            "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u884c {0}: \u9810\u671f\u7684 [{1}]"},
        {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
            "\u914d\u7f6e\u932f\u8aa4\uff1a\n\t\u884c {0}: \u7cfb\u7d71\u5c6c\u6027 [{1}] \u64f4\u5145\u81f3\u7a7a\u503c"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username: ","\u4f7f\u7528\u8005\u540d\u7a31\uff1a "},
        {"password: ","\u5bc6\u78bc\uff1a "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please enter keystore information",
                "\u8acb\u8f38\u5165 keystore \u8cc7\u8a0a"},
        {"Keystore alias: ","Keystore \u5225\u540d\uff1a "},
        {"Keystore password: ","Keystore \u5bc6\u78bc\uff1a "},
        {"Private key password (optional): ",
            "\u79c1\u4eba\u95dc\u9375\u5bc6\u78bc\uff08\u9078\u64c7\u6027\u7684\uff09\uff1a "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos username [[defUsername]]: ",
                "Kerberos \u4f7f\u7528\u8005\u540d\u7a31 [{0}]: "},
        {"Kerberos password for [username]: ",
                "Kerberos \u7684 {0} \u5bc6\u78bc\uff1a  "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {": error parsing ", "\uff1a\u8a9e\u6cd5\u932f\u8aa4 "},
        {": ", ": "},
        {": error adding Permission ", "\uff1a\u65b0\u589e\u8a31\u53ef\u6b0a\u932f\u8aa4 "},
        {" ", " "},
        {": error adding Entry ", "\u65b0\u589e\u8f38\u5165\u932f\u8aa4 "},
        {"(", "("},
        {")", ")"},
        {"attempt to add a Permission to a readonly PermissionCollection",
            "\u8a66\u8457\u65b0\u589e\u8a31\u53ef\u6b0a\u81f3\u552f\u8b80\u7684 PermissionCollection"},

        // com.sun.security.auth.PolicyParser
        {"expected keystore type", "\u9810\u671f\u7684 keystore \u985e\u578b"},
        {"can not specify Principal with a ",
                "\u7121\u6cd5\u4ee5\u6b64\u4f86\u6307\u5b9a Principal "},
        {"wildcard class without a wildcard name",
                "\u842c\u7528\u5b57\u5143\u985e\u5225\u672a\u9644\u842c\u7528\u5b57\u5143\u540d\u7a31"},
        {"expected codeBase or SignedBy", "\u9810\u671f\u7684 codeBase \u6216 SignedBy"},
        {"only Principal-based grant entries permitted",
                "\u53ea\u5141\u8a31\u4ee5 Principal \u70ba\u57fa\u790e\u7684\u6388\u6b0a\u8f38\u5165"},
        {"expected permission entry", "\u9810\u671f\u8a31\u53ef\u8f38\u5165"},
        {"number ", "\u865f\u78bc "},
        {"expected ", "\u9810\u671f\u7684 "},
        {", read end of file", "\uff0c\u8b80\u53d6\u6a94\u6848\u672b\u7aef"},
        {"expected ';', read end of file", "\u9810\u671f\u7684 ';', \u8b80\u53d6\u6a94\u6848\u672b\u7aef"},
        {"line ", "\u884c "},
        {": expected '", ": \u9810\u671f '"},
        {"', found '", "', \u767c\u73fe '"},
        {"'", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal [Primary Group]: ",
                "SolarisNumericGroupPrincipal [\u4e3b\u7fa4\u7d44]\uff1a "},
        {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
                "SolarisNumericGroupPrincipal [\u9644\u52a0\u7fa4\u7d44]\uff1a "},
        {"SolarisNumericUserPrincipal: ",
                "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal: ", "SolarisPrincipal: "},
        {"provided null name", "\u63d0\u4f9b\u7684\u7a7a\u540d\u7a31"}

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
