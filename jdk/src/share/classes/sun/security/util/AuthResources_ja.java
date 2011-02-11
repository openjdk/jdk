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
public class AuthResources_ja extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid.null.input.value", "\u7121\u52B9\u306Anull\u306E\u5165\u529B: {0}"},
        {"NTDomainPrincipal.name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential.name", "NTNumericCredential: {0}"},
        {"Invalid.NTSid.value", "\u7121\u52B9\u306ANTSid\u5024"},
        {"NTSid.name", "NTSid: {0}"},
        {"NTSidDomainPrincipal.name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal.name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal.name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal.name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal.name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal.Primary.Group.name",
                "UnixNumericGroupPrincipal [\u4E3B\u30B0\u30EB\u30FC\u30D7]: {0}"},
        {"UnixNumericGroupPrincipal.Supplementary.Group.name",
                "UnixNumericGroupPrincipal [\u88DC\u52A9\u30B0\u30EB\u30FC\u30D7]: {0}"},
        {"UnixNumericUserPrincipal.name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal.name", "UnixPrincipal: {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable.to.properly.expand.config", "{0}\u3092\u6B63\u3057\u304F\u5C55\u958B\u3067\u304D\u307E\u305B\u3093"},
        {"extra.config.No.such.file.or.directory.",
                "{0}(\u6307\u5B9A\u3055\u308C\u305F\u30D5\u30A1\u30A4\u30EB\u307E\u305F\u306F\u30C7\u30A3\u30EC\u30AF\u30C8\u30EA\u306F\u5B58\u5728\u3057\u307E\u305B\u3093)"},
        {"Configuration.Error.No.such.file.or.directory",
                "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t\u6307\u5B9A\u3055\u308C\u305F\u30D5\u30A1\u30A4\u30EB\u307E\u305F\u306F\u30C7\u30A3\u30EC\u30AF\u30C8\u30EA\u306F\u5B58\u5728\u3057\u307E\u305B\u3093"},
        {"Configuration.Error.Invalid.control.flag.flag",
                "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t\u7121\u52B9\u306A\u5236\u5FA1\u30D5\u30E9\u30B0: {0}"},
        {"Configuration.Error.Can.not.specify.multiple.entries.for.appName",
            "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t{0}\u306B\u8907\u6570\u306E\u30A8\u30F3\u30C8\u30EA\u3092\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"Configuration.Error.expected.expect.read.end.of.file.",
                "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t[{0}]\u3067\u306F\u306A\u304F\u3001[\u30D5\u30A1\u30A4\u30EB\u306E\u7D42\u308F\u308A]\u304C\u8AAD\u307F\u8FBC\u307E\u308C\u307E\u3057\u305F"},
        {"Configuration.Error.Line.line.expected.expect.found.value.",
            "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t\u884C{0}: [{1}]\u3067\u306F\u306A\u304F\u3001[{2}]\u304C\u691C\u51FA\u3055\u308C\u307E\u3057\u305F"},
        {"Configuration.Error.Line.line.expected.expect.",
            "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t\u884C{0}: [{1}]\u304C\u8981\u6C42\u3055\u308C\u307E\u3057\u305F"},
        {"Configuration.Error.Line.line.system.property.value.expanded.to.empty.value",
            "\u69CB\u6210\u30A8\u30E9\u30FC:\n\t\u884C{0}: \u30B7\u30B9\u30C6\u30E0\u30FB\u30D7\u30ED\u30D1\u30C6\u30A3[{1}]\u304C\u7A7A\u306E\u5024\u306B\u5C55\u958B\u3055\u308C\u307E\u3057\u305F"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username.","\u30E6\u30FC\u30B6\u30FC\u540D: "},
        {"password.","\u30D1\u30B9\u30EF\u30FC\u30C9: "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please.enter.keystore.information",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u60C5\u5831\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Keystore.alias.","\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u5225\u540D: "},
        {"Keystore.password.","\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9: "},
        {"Private.key.password.optional.",
            "\u79D8\u5BC6\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9(\u30AA\u30D7\u30B7\u30E7\u30F3): "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos.username.defUsername.",
                "Kerberos\u30E6\u30FC\u30B6\u30FC\u540D[{0}]: "},
        {"Kerberos.password.for.username.",
                "{0}\u306EKerberos\u30D1\u30B9\u30EF\u30FC\u30C9: "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {".error.parsing.", ": \u89E3\u6790\u30A8\u30E9\u30FC "},
        {"COLON", ": "},
        {".error.adding.Permission.", ": \u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u8FFD\u52A0\u30A8\u30E9\u30FC "},
        {"SPACE", " "},
        {".error.adding.Entry.", ": \u30A8\u30F3\u30C8\u30EA\u306E\u8FFD\u52A0\u30A8\u30E9\u30FC "},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"attempt.to.add.a.Permission.to.a.readonly.PermissionCollection",
            "\u8AAD\u53D6\u308A\u5C02\u7528\u306EPermissionCollection\u306B\u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u8FFD\u52A0\u304C\u8A66\u884C\u3055\u308C\u307E\u3057\u305F"},

        // com.sun.security.auth.PolicyParser
        {"expected.keystore.type", "\u4E88\u60F3\u3055\u308C\u305F\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30BF\u30A4\u30D7"},
        {"can.not.specify.Principal.with.a.",
                "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306F\u3001\u6B21\u306E\u3082\u306E\u3092\u4F7F\u7528\u3057\u3066\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093 "},
        {"wildcard.class.without.a.wildcard.name",
                "\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9\u540D\u306E\u306A\u3044\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9\u30FB\u30AF\u30E9\u30B9"},
        {"expected.codeBase.or.SignedBy", "\u4E88\u60F3\u3055\u308C\u305FcodeBase\u307E\u305F\u306FSignedBy"},
        {"only.Principal.based.grant.entries.permitted",
                "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u30FB\u30D9\u30FC\u30B9\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u307F\u304C\u8A31\u53EF\u3055\u308C\u307E\u3059\u3002"},
        {"expected.permission.entry", "\u4E88\u60F3\u3055\u308C\u305F\u30A2\u30AF\u30BB\u30B9\u6A29\u30A8\u30F3\u30C8\u30EA"},
        {"number.", "\u6570 "},
        {"expected.", "\u4E88\u60F3\u5024 "},
        {".read.end.of.file", ", \u30D5\u30A1\u30A4\u30EB\u306E\u7D42\u308F\u308A\u304C\u8AAD\u307F\u8FBC\u307E\u308C\u307E\u3057\u305F\u3002"},
        {"expected.read.end.of.file", "\u4E88\u60F3\u5024\u306F';'\u3067\u3059\u304C\u3001\u30D5\u30A1\u30A4\u30EB\u306E\u7D42\u308F\u308A\u304C\u8AAD\u307F\u8FBC\u307E\u308C\u307E\u3057\u305F"},
        {"line.", "\u884C\u756A\u53F7 "},
        {".expected.", ": \u4E88\u60F3\u5024'"},
        {".found.", "',\u691C\u51FA\u5024'"},
        {"QUOTE", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal.Primary.Group.",
                "SolarisNumericGroupPrincipal [\u4E3B\u30B0\u30EB\u30FC\u30D7]: "},
        {"SolarisNumericGroupPrincipal.Supplementary.Group.",
                "SolarisNumericGroupPrincipal [\u88DC\u52A9\u30B0\u30EB\u30FC\u30D7]: "},
        {"SolarisNumericUserPrincipal.",
                "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal.", "SolarisPrincipal: "},
        {"provided.null.name", "null\u306E\u540D\u524D\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"}

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
