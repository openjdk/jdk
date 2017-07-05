/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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
public class AuthResources_ko extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid null input: value", "\uc798\ubabb\ub41c \ub110 \uc785\ub825:  {0}"},
        {"NTDomainPrincipal: name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential: name", "NTNumericCredential: {0}"},
        {"Invalid NTSid value", "\uc798\ubabb\ub41c NTSid \uac12"},
        {"NTSid: name", "NTSid: {0}"},
        {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal: name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal: name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal [Primary Group]: name",
                "UnixNumericGroupPrincipal [\uae30\ubcf8 \uadf8\ub8f9]:  {0}"},
        {"UnixNumericGroupPrincipal [Supplementary Group]: name",
                "UnixNumericGroupPrincipal [\ubcf4\uc870 \uadf8\ub8f9]:  {0}"},
        {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal: name", "UnixPrincipal: {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable to properly expand config", "\uc801\uc808\ud788 \ud655\uc7a5\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. {0}"},
        {"extra_config (No such file or directory)",
                "{0} (\ud574\ub2f9 \ud30c\uc77c\uc774\ub098 \ub514\ub809\ud1a0\ub9ac\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.)"},
        {"Configuration Error:\n\tNo such file or directory",
                "\uad6c\uc131 \uc624\ub958:\n\t\ud574\ub2f9 \ud30c\uc77c\uc774\ub098 \ub514\ub809\ud1a0\ub9ac\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},
        {"Configuration Error:\n\tInvalid control flag, flag",
                "\uad6c\uc131 \uc624\ub958:\n\t\uc798\ubabb\ub41c \ucee8\ud2b8\ub864 \ud50c\ub798\uadf8, {0}"},
        {"Configuration Error:\n\tCan not specify multiple entries for appName",
            "\uad6c\uc131 \uc624\ub958:\n\t{0}\uc5d0 \ub300\ud574 \uc5ec\ub7ec \ud56d\ubaa9\uc744 \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},
        {"Configuration Error:\n\texpected [expect], read [end of file]",
                "\uad6c\uc131 \uc624\ub958:\n\t\uc608\uc0c1 [{0}], \uc77d\uc74c [\ud30c\uc77c\uc758 \ub05d]"},
        {"Configuration Error:\n\tLine line: expected [expect], found [value]",
            "\uad6c\uc131 \uc624\ub958:\n\t\uc904 {0}: \uc608\uc0c1 [{1}], \ubc1c\uacac [{2}]"},
        {"Configuration Error:\n\tLine line: expected [expect]",
            "\uad6c\uc131 \uc624\ub958:\n\t\uc904 {0}: \uc608\uc0c1 [{1}]"},
        {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
            "\uad6c\uc131 \uc624\ub958:\n\t\uc904 {0}: \uc2dc\uc2a4\ud15c \ub4f1\ub85d \uc815\ubcf4 [{1}]\uc774(\uac00) \ube48 \uac12\uc73c\ub85c \ud655\uc7a5\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        // com.sun.security.auth.module.JndiLoginModule
        {"username: ","\uc0ac\uc6a9\uc790 \uc774\ub984: "},
        {"password: ","\uc554\ud638: "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please enter keystore information",
                "Keystore \uc815\ubcf4\ub97c \uc785\ub825\ud558\uc2ed\uc2dc\uc624."},
        {"Keystore alias: ","Keystore \ubcc4\uba85: "},
        {"Keystore password: ","Keystore \uc554\ud638: "},
        {"Private key password (optional): ",
            "\uac1c\uc778 \ud0a4 \uc554\ud638(\uc120\ud0dd \uc0ac\ud56d): "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos username [[defUsername]]: ",
                "Kerberos \uc0ac\uc6a9\uc790 \uc774\ub984 [{0}]: "},
        {"Kerberos password for [username]: ",
                "{0}\uc758 Kerberos \uc554\ud638: "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {": error parsing ", ": \uad6c\ubb38 \ubd84\uc11d \uc624\ub958 "},
        {": ", ": "},
        {": error adding Permission ", ": \uc0ac\uc6a9 \uad8c\ud55c \ucd94\uac00 \uc911 \uc624\ub958 "},
        {" ", " "},
        {": error adding Entry ", ": \uc785\ub825 \ud56d\ubaa9 \ucd94\uac00 \uc911 \uc624\ub958 "},
        {"(", "("},
        {")", ")"},
        {"attempt to add a Permission to a readonly PermissionCollection",
            "\uc77d\uae30 \uc804\uc6a9 PermissionCollection\uc5d0 \uc0ac\uc6a9 \uad8c\ud55c\uc744 \ucd94\uac00\ud558\ub824\uace0 \uc2dc\ub3c4\ud588\uc2b5\ub2c8\ub2e4."},

        // com.sun.security.auth.PolicyParser
        {"expected keystore type", "Keystore \uc720\ud615\uc774 \ud544\uc694\ud569\ub2c8\ub2e4."},
        {"can not specify Principal with a ",
                "\uc640\uc77c\ub4dc\uce74\ub4dc \ud074\ub798\uc2a4\ub97c \uc640\uc77c\ub4dc\uce74\ub4dc \uc774\ub984\uc774 \uc5c6\uc774 "},
        {"wildcard class without a wildcard name",
                "\uae30\ubcf8\uac12\uc744 \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},
        {"expected codeBase or SignedBy", "codeBase \ub610\ub294 SignedBy\uac00 \ud544\uc694\ud569\ub2c8\ub2e4."},
        {"only Principal-based grant entries permitted",
                "\uae30\ubcf8\uac12 \uae30\ubc18 \ubd80\uc5ec \uc785\ub825 \ud56d\ubaa9\ub9cc \ud5c8\uc6a9\ub429\ub2c8\ub2e4."},
        {"expected permission entry", "\uc0ac\uc6a9 \uad8c\ud55c \uc785\ub825 \ud56d\ubaa9\uc774 \ud544\uc694\ud569\ub2c8\ub2e4."},
        {"number ", "\uc22b\uc790 "},
        {"expected ", "\ud544\uc694\ud569\ub2c8\ub2e4. "},
        {", read end of file", ", \ud30c\uc77c\uc758 \ub05d\uc744 \uc77d\uc5c8\uc2b5\ub2c8\ub2e4."},
        {"expected ';', read end of file", "';'\uc774 \ud544\uc694\ud569\ub2c8\ub2e4. \ud30c\uc77c\uc758 \ub05d\uc744 \uc77d\uc5c8\uc2b5\ub2c8\ub2e4."},
        {"line ", "\uc904 "},
        {": expected '", ":  '\uc774 \ud544\uc694\ud569\ub2c8\ub2e4."},
        {"', found '", "', '\uc744 \ucc3e\uc558\uc2b5\ub2c8\ub2e4."},
        {"'", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal [Primary Group]: ",
                "SolarisNumericGroupPrincipal [\uae30\ubcf8 \uadf8\ub8f9]: "},
        {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
                "SolarisNumericGroupPrincipal [\ubcf4\uc870 \uadf8\ub8f9]: "},
        {"SolarisNumericUserPrincipal: ",
                "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal: ", "SolarisPrincipal: "},
        {"provided null name", "\uc81c\uacf5\ub41c \ub110 \uc774\ub984"}

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
