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
 * @version 1.6, 01/23/01
 */
public class AuthResources_pt_BR extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

    // NT principals
    {"invalid null input: value", "entrada nula inv\u00e1lida: {0}"},
    {"NTDomainPrincipal: name", "NTDomainPrincipal: {0}"},
    {"NTNumericCredential: name", "NTNumericCredential: {0}"},
    {"Invalid NTSid value", "Valor de NTSid inv\u00e1lido"},
    {"NTSid: name", "NTSid: {0}"},
    {"NTSidDomainPrincipal: name", "NTSidDomainPrincipal: {0}"},
    {"NTSidGroupPrincipal: name", "NTSidGroupPrincipal: {0}"},
    {"NTSidPrimaryGroupPrincipal: name", "NTSidPrimaryGroupPrincipal: {0}"},
    {"NTSidUserPrincipal: name", "NTSidUserPrincipal: {0}"},
    {"NTUserPrincipal: name", "NTUserPrincipal: {0}"},

    // UnixPrincipals
    {"UnixNumericGroupPrincipal [Primary Group]: name",
        "UnixNumericGroupPrincipal [grupo principal]: {0}"},
    {"UnixNumericGroupPrincipal [Supplementary Group]: name",
        "UnixNumericGroupPrincipal [grupo complement\u00e1rio]: {0}"},
    {"UnixNumericUserPrincipal: name", "UnixNumericUserPrincipal: {0}"},
    {"UnixPrincipal: name", "UnixPrincipal: {0}"},

    // com.sun.security.auth.login.ConfigFile
    {"Unable to properly expand config", "N\u00e3o \u00e9 poss\u00edvel expandir corretamente {0}"},
    {"extra_config (No such file or directory)",
        "{0} (tal arquivo ou diret\u00f3rio n\u00e3o existe)"},
    {"Unable to locate a login configuration",
        "N\u00e3o \u00e9 poss\u00edvel localizar uma configura\u00e7\u00e3o de logon"},
    {"Configuration Error:\n\tInvalid control flag, flag",
        "Erro de configura\u00e7\u00e3o:\n\tSinalizador de controle inv\u00e1lido, {0}"},
    {"Configuration Error:\n\tCan not specify multiple entries for appName",
        "Erro de configura\u00e7\u00e3o:\n\tN\u00e3o \u00e9 poss\u00edvel especificar v\u00e1rias entradas para {0}"},
    {"Configuration Error:\n\texpected [expect], read [end of file]",
        "Erro de configura\u00e7\u00e3o:\n\tesperado [{0}], lido [fim do arquivo]"},
    {"Configuration Error:\n\tLine line: expected [expect], found [value]",
        "Erro de configura\u00e7\u00e3o:\n\tlinha {0}: esperado [{1}], encontrado [{2}]"},
    {"Configuration Error:\n\tLine line: expected [expect]",
        "Erro de configura\u00e7\u00e3o:\n\tlinha {0}: esperado [{1}]"},
    {"Configuration Error:\n\tLine line: system property [value] expanded to empty value",
        "Erro de configura\u00e7\u00e3o:\n\tlinha {0}: propriedade do sistema [{1}] expandida para valor vazio"},

    // com.sun.security.auth.module.JndiLoginModule
    {"username: ","nome de usu\u00e1rio: "},
    {"password: ","senha: "},

    // com.sun.security.auth.module.KeyStoreLoginModule
    {"Please enter keystore information",
        "Insira as informa\u00e7\u00f5es do armazenamento de chaves"},
    {"Keystore alias: ","Alias do armazenamento de chaves: "},
    {"Keystore password: ","Senha do armazenamento de chaves: "},
    {"Private key password (optional): ",
        "Senha da chave privada (opcional): "},

    // com.sun.security.auth.module.Krb5LoginModule
    {"Kerberos username [[defUsername]]: ",
        "Nome de usu\u00e1rio de Kerberos [{0}]: "},
    {"Kerberos password for [username]: ",
            "Senha de Kerberos de {0}: "},

    /***    EVERYTHING BELOW IS DEPRECATED    ***/

    // com.sun.security.auth.PolicyFile
    {": error parsing ", ": erro ao analisar "},
    {": ", ": "},
    {": error adding Permission ", ": erro ao adicionar permiss\u00e3o "},
    {" ", " "},
    {": error adding Entry ", ": erro ao adicionar entrada "},
    {"(", "("},
    {")", ")"},
    {"attempt to add a Permission to a readonly PermissionCollection",
        "tentativa de adicionar uma permiss\u00e3o ao um PermissionCollection de somente leitura"},

    // com.sun.security.auth.PolicyParser
    {"expected keystore type", "tipo de armazenamento de chaves esperado"},
    {"can not specify Principal with a ",
        "n\u00e3o \u00e9 poss\u00edvel especificar um principal com uma "},
    {"wildcard class without a wildcard name",
        "de coringa sem um nome de coringa"},
    {"expected codeBase or SignedBy", "CodeBase ou SignedBy esperado"},
    {"only Principal-based grant entries permitted",
        "somente \u00e9 permitido conceder entradas com base no principal"},
    {"expected permission entry", "entrada de permiss\u00e3o esperada"},
    {"number ", "n\u00famero "},
    {"expected ", "esperado "},
    {", read end of file", ", fim de arquivo lido"},
    {"expected ';', read end of file", "esperado ';', fim de arquivo lido"},
    {"line ", "linha "},
    {": expected '", ": esperado '"},
    {"', found '", "', encontrado '"},
    {"'", "'"},

    // SolarisPrincipals
    {"SolarisNumericGroupPrincipal [Primary Group]: ",
        "SolarisNumericGroupPrincipal [grupo principal]: "},
    {"SolarisNumericGroupPrincipal [Supplementary Group]: ",
        "SolarisNumericGroupPrincipal [grupo complement\u00e1rio]: "},
    {"SolarisNumericUserPrincipal: ",
        "SolarisNumericUserPrincipal: "},
    {"SolarisPrincipal: ", "SolarisPrincipal: "},
    {"provided null name", "nome nulo fornecido"}

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
