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
public class AuthResources_fr extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid null input: value", "entr\u00e9e Null non valide {0}"},
        {"NTDomainPrincipal: name", "NTDomainPrincipal : {0}"},
        {"NTNumericCredential: name", "NTNumericCredential : {0}"},
        {"Invalid NTSid value", "Valeur de NTSid non valide"},
        {"NTSid: name", "NTSid : {0}"},
        {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal : {0}"},
        {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal : {0}"},
        {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal : {0}"},
        {"NTSidUserPrincipal: name", "NTSidUserPrincipal : {0}"},
        {"NTUserPrincipal: name", "NTUserPrincipal : {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal [Primary Group]: name",
                "UnixNumericGroupPrincipal [groupe principal] : {0}"},
        {"UnixNumericGroupPrincipal [Supplementary Group]: name",
                "UnixNumericGroupPrincipal [groupe suppl\u00e9mentaire] : {0}"},
        {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal : {0}"},
        {"UnixPrincipal: name", "UnixPrincipal : {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable to properly expand config", "Impossible de d\u00e9velopper {0} correctement"},
        {"extra_config (No such file or directory)",
                "{0} (fichier ou r\u00e9pertoire introuvable)"},
        {"Configuration Error:\n\tNo such file or directory",
                "Erreur de configuration\u00a0:\n\tAucun fichier ou r\u00e9pertoire de ce type"},
        {"Configuration Error:\n\tInvalid control flag, flag",
                "Erreur de configuration :\n\tIndicateur de contr\u00f4le non valide, {0}"},
        {"Configuration Error:\n\tCan not specify multiple entries for appName",
            "Erreur de configuration :\n\tImpossible de sp\u00e9cifier des entr\u00e9es multiples pour {0}"},
        {"Configuration Error:\n\texpected [expect], read [end of file]",
                "Erreur de configuration :\n\tattendu [{0}], lecture [fin de fichier]"},
        {"Configuration Error:\n\tLine line: expected [expect], found [value]",
            "Erreur de configuration :\n\tLigne {0} : attendu [{1}], trouv\u00e9 [{2}]"},
        {"Configuration Error:\n\tLine line: expected [expect]",
            "Erreur de configuration :\n\tLigne {0} : attendu [{1}]"},
        {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
            "Erreur de configuration :\n\tLigne {0} : propri\u00e9t\u00e9 syst\u00e8me [{1}] d\u00e9velopp\u00e9e en valeur vide"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username: ","Nom d'utilisateur : "},
        {"password: ","Mot de passe : "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please enter keystore information",
                "Veuillez entrer les informations relatives \u00e0 Keystore"},
        {"Keystore alias: ","Alias pour Keystore : "},
        {"Keystore password: ","Mot de passe pour Keystore : "},
        {"Private key password (optional): ",
            "Mot de passe de cl\u00e9 priv\u00e9e (facultatif) : "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos username [[defUsername]]: ",
                "Nom d''utilisateur Kerberos [{0}] : "},
        {"Kerberos password for [username]: ",
                "Mot de pass\u00e9 Kerberos pour {0} : "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {": error parsing ", " : erreur d'analyse "},
        {": ", ": "},
        {": error adding Permission ", " : erreur d'ajout de permission "},
        {" ", " "},
        {": error adding Entry ", " : erreur d'ajout d'entr\u00e9e "},
        {"(", "("},
        {")", ")"},
        {"attempt to add a Permission to a readonly PermissionCollection",
            "tentative d'ajout de permission \u00e0 un ensemble de permissions en lecture seule"},

        // com.sun.security.auth.PolicyParser
        {"expected keystore type", "type de Keystore attendu"},
        {"can not specify Principal with a ",
                "impossible de sp\u00e9cifier Principal avec une "},
        {"wildcard class without a wildcard name",
                "classe g\u00e9n\u00e9rique sans nom g\u00e9n\u00e9rique"},
        {"expected codeBase or SignedBy", "codeBase ou SignedBy attendu"},
        {"only Principal-based grant entries permitted",
                "seules les entr\u00e9es bas\u00e9es sur Principal sont autoris\u00e9es"},
        {"expected permission entry", "entr\u00e9e de permission attendue"},
        {"number ", "nombre "},
        {"expected ", "attendu "},
        {", read end of file", ", lecture de fin de fichier"},
        {"expected ';', read end of file", "attendu ';', lecture de fin de fichier"},
        {"line ", "ligne "},
        {": expected '", " : attendu '"},
        {"', found '", "', trouv\u00e9 '"},
        {"'", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal [Primary Group]: ",
                "SolarisNumericGroupPrincipal [groupe principal] : "},
        {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
                "SolarisNumericGroupPrincipal [groupe suppl\u00e9mentaire] : "},
        {"SolarisNumericUserPrincipal: ",
                "SolarisNumericUserPrincipal : "},
        {"SolarisPrincipal: ", "SolarisPrincipal : "},
        {"provided null name", "nom Null sp\u00e9cifi\u00e9"}

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
