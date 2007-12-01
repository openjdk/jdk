/*
 * Copyright 2001-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
public class AuthResources_es extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid null input: value", "entrada nula no v\u00e1lida: {0}"},
        {"NTDomainPrincipal: name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential: name", "NTNumericCredential: {0}"},
        {"Invalid NTSid value", "Valor de NTSid no v\u00e1lido"},
        {"NTSid: name", "NTSid: {0}"},
        {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal: name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal: name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal [Primary Group]: name",
        "UnixNumericGroupPrincipal [Grupo principal] {0}"},
        {"UnixNumericGroupPrincipal [Supplementary Group]: name",
        "UnixNumericGroupPrincipal [Grupo adicional] {0}"},
        {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal: name", "UnixPrincipal: {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable to properly expand config", "No se puede ampliar correctamente {0}"},
        {"extra_config (No such file or directory)",
        "{0} (No existe tal archivo o directorio)"},
        {"Unable to locate a login configuration",
        "No se puede localizar una configuraci\u00f3n de inicio de sesi\u00f3n"},
        {"Configuration Error:\n\tInvalid control flag, flag",
        "Error de configuraci\u00f3n:\n\tIndicador de control no v\u00e1lido, {0}"},
        {"Configuration Error:\n\tCan not specify multiple entries for appName",
        "Error de configuraci\u00f3n:\n\tNo se pueden especificar m\u00faltiples entradas para {0}"},
        {"Configuration Error:\n\texpected [expect], read [end of file]",
        "Error de configuraci\u00f3n:\n\tse esperaba [{0}], se ha le\u00eddo [end of file]"},
        {"Configuration Error:\n\tLine line: expected [expect], found [value]",
        "Error de configuraci\u00f3n:\n\tL\u00ednea {0}: se esperaba [{1}], se ha encontrado [{2}]"},
        {"Configuration Error:\n\tLine line: expected [expect]",
        "Error de configuraci\u00f3n:\n\tL\u00ednea {0}: se esperaba [{1}]"},
        {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
        "Error de configuraci\u00f3n:\n\tL\u00ednea {0}: propiedad de sistema [{1}] ampliada a valor vac\u00edo"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username: ","nombre de usuario: "},
        {"password: ","contrase\u00f1a: "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please enter keystore information",
                "Introduzca la informaci\u00f3n del almac\u00e9n de claves"},
        {"Keystore alias: ","Alias de almac\u00e9n de claves: "},
        {"Keystore password: ","Contrase\u00f1a de almac\u00e9n de claves: "},
        {"Private key password (optional): ",
        "Contrase\u00f1a de clave privada (opcional): "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos username [[defUsername]]: ",
        "Nombre de usuario de Kerberos [{0}]: "},
        {"Kerberos password for [username]: ",
            "Contrase\u00f1a de Kerberos de {0}: "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {": error parsing ", ": error de an\u00e1lisis "},
        {": ", ": "},
        {": error adding Permission ", ": error al agregar Permiso "},
        {" ", " "},
        {": error adding Entry ", ": error al agregar Entrada "},
        {"(", "("},
        {")", ")"},
        {"attempt to add a Permission to a readonly PermissionCollection",
        "se ha intentado agregar un Permiso a una Colecci\u00f3n de permisos de s\u00f3lo lectura"},

        // com.sun.security.auth.PolicyParser
        {"expected keystore type", "se esperaba un tipo de almac\u00e9n de claves"},
        {"can not specify Principal with a ",
        "no se puede especificar Principal con una "},
        {"wildcard class without a wildcard name",
        "clase comod\u00edn sin nombre de comod\u00edn"},
        {"expected codeBase or SignedBy", "se esperaba base de c\u00f3digos o SignedBy"},
        {"only Principal-based grant entries permitted",
        "s\u00f3lo se permite conceder entradas basadas en Principal"},
        {"expected permission entry", "se esperaba un permiso de entrada"},
        {"number ", "n\u00famero "},
        {"expected ", "se esperaba "},
        {", read end of file", ", se ha le\u00eddo final de archivo"},
        {"expected ';', read end of file", "se esperaba ';', se ha le\u00eddo final de archivo"},
        {"line ", "l\u00ednea "},
        {": expected '", ": se esperaba '"},
        {"', found '", "', se ha encontrado '"},
        {"'", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal [Primary Group]: ",
        "SolarisNumericGroupPrincipal [Grupo principal]: "},
        {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
        "SolarisNumericGroupPrincipal [Grupo adicional]: "},
        {"SolarisNumericUserPrincipal: ",
        "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal: ", "SolarisPrincipal: "},
        {"provided null name", "se ha proporcionado un nombre nulo"}

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
