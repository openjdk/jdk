/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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
public class AuthResources_zh_TW extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // NT principals
        {"invalid.null.input.value", "\u7121\u6548\u7A7A\u503C\u8F38\u5165: {0}"},
        {"NTDomainPrincipal.name", "NTDomainPrincipal: {0}"},
        {"NTNumericCredential.name", "NTNumericCredential: {0}"},
        {"Invalid.NTSid.value", "\u7121\u6548 NTSid \u503C"},
        {"NTSid.name", "NTSid: {0}"},
        {"NTSidDomainPrincipal.name", "NTSidDomainPrincipal: {0}"},
        {"NTSidGroupPrincipal.name", "NTSidGroupPrincipal: {0}"},
        {"NTSidPrimaryGroupPrincipal.name", "NTSidPrimaryGroupPrincipal: {0}"},
        {"NTSidUserPrincipal.name", "NTSidUserPrincipal: {0}"},
        {"NTUserPrincipal.name", "NTUserPrincipal: {0}"},

        // UnixPrincipals
        {"UnixNumericGroupPrincipal.Primary.Group.name",
                "UnixNumericGroupPrincipal [\u4E3B\u7FA4\u7D44]: {0}"},
        {"UnixNumericGroupPrincipal.Supplementary.Group.name",
                "UnixNumericGroupPrincipal [\u9644\u52A0\u7FA4\u7D44]: {0}"},
        {"UnixNumericUserPrincipal.name", "UnixNumericUserPrincipal: {0}"},
        {"UnixPrincipal.name", "UnixPrincipal: {0}"},

        // com.sun.security.auth.login.ConfigFile
        {"Unable.to.properly.expand.config", "\u7121\u6CD5\u9069\u7576\u5730\u64F4\u5145 {0}"},
        {"extra.config.No.such.file.or.directory.",
                "{0} (\u6C92\u6709\u6B64\u6A94\u6848\u6216\u76EE\u9304)"},
        {"Configuration.Error.No.such.file.or.directory",
                "\u7D44\u614B\u932F\u8AA4:\n\t\u7121\u6B64\u6A94\u6848\u6216\u76EE\u9304"},
        {"Configuration.Error.Invalid.control.flag.flag",
                "\u7D44\u614B\u932F\u8AA4:\n\t\u7121\u6548\u7684\u63A7\u5236\u65D7\u6A19\uFF0C{0}"},
        {"Configuration.Error.Can.not.specify.multiple.entries.for.appName",
            "\u7D44\u614B\u932F\u8AA4: \n\t\u7121\u6CD5\u6307\u5B9A\u591A\u91CD\u9805\u76EE {0}"},
        {"Configuration.Error.expected.expect.read.end.of.file.",
                "\u7D44\u614B\u932F\u8AA4: \n\t\u9810\u671F\u7684 [{0}], \u8B80\u53D6 [end of file]"},
        {"Configuration.Error.Line.line.expected.expect.found.value.",
            "\u7D44\u614B\u932F\u8AA4: \n\t\u884C {0}: \u9810\u671F\u7684 [{1}], \u767C\u73FE [{2}]"},
        {"Configuration.Error.Line.line.expected.expect.",
            "\u7D44\u614B\u932F\u8AA4: \n\t\u884C {0}: \u9810\u671F\u7684 [{1}]"},
        {"Configuration.Error.Line.line.system.property.value.expanded.to.empty.value",
            "\u7D44\u614B\u932F\u8AA4: \n\t\u884C {0}: \u7CFB\u7D71\u5C6C\u6027 [{1}] \u64F4\u5145\u81F3\u7A7A\u503C"},

        // com.sun.security.auth.module.JndiLoginModule
        {"username.","\u4F7F\u7528\u8005\u540D\u7A31: "},
        {"password.","\u5BC6\u78BC: "},

        // com.sun.security.auth.module.KeyStoreLoginModule
        {"Please.enter.keystore.information",
                "\u8ACB\u8F38\u5165\u91D1\u9470\u5132\u5B58\u5EAB\u8CC7\u8A0A"},
        {"Keystore.alias.","\u91D1\u9470\u5132\u5B58\u5EAB\u5225\u540D: "},
        {"Keystore.password.","\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC: "},
        {"Private.key.password.optional.",
            "\u79C1\u4EBA\u91D1\u9470\u5BC6\u78BC (\u9078\u64C7\u6027\u7684): "},

        // com.sun.security.auth.module.Krb5LoginModule
        {"Kerberos.username.defUsername.",
                "Kerberos \u4F7F\u7528\u8005\u540D\u7A31 [{0}]: "},
        {"Kerberos.password.for.username.",
                "Kerberos \u5BC6\u78BC [{0}]: "},

        /***    EVERYTHING BELOW IS DEPRECATED  ***/

        // com.sun.security.auth.PolicyFile
        {".error.parsing.", ": \u5256\u6790\u932F\u8AA4 "},
        {"COLON", ": "},
        {".error.adding.Permission.", ": \u65B0\u589E\u6B0A\u9650\u932F\u8AA4 "},
        {"SPACE", " "},
        {".error.adding.Entry.", ": \u65B0\u589E\u8F38\u5165\u932F\u8AA4 "},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"attempt.to.add.a.Permission.to.a.readonly.PermissionCollection",
            "\u8A66\u8457\u65B0\u589E\u6B0A\u9650\u81F3\u552F\u8B80\u7684 PermissionCollection"},

        // com.sun.security.auth.PolicyParser
        {"expected.keystore.type", "\u9810\u671F\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "\u6C92\u6709\u842C\u7528\u5B57\u5143\u540D\u7A31\uFF0C\u7121\u6CD5\u6307\u5B9A\u542B\u6709\u842C\u7528\u5B57\u5143\u985E\u5225\u7684 Principal"},
        {"expected.codeBase.or.SignedBy", "\u9810\u671F\u7684 codeBase \u6216 SignedBy"},
        {"only.Principal.based.grant.entries.permitted",
                "\u53EA\u5141\u8A31\u4EE5 Principal \u70BA\u57FA\u790E\u7684\u6388\u6B0A\u9805\u76EE"},
        {"expected.permission.entry", "\u9810\u671F\u7684\u6B0A\u9650\u9805\u76EE"},
        {"number.", "\u865F\u78BC "},
        {"expected.expect.read.end.of.file.",
                "\u9810\u671F\u70BA {0}, \u8B80\u53D6\u6A94\u6848\u7D50\u5C3E"},
        {"expected.read.end.of.file", "\u9810\u671F\u7684 ';'\uFF0C\u8B80\u53D6\u6A94\u6848\u7D50\u5C3E"},
        {"line.", "\u884C "},
        {".expected.", ": \u9810\u671F '"},
        {".found.", "'\uFF0C\u767C\u73FE '"},
        {"QUOTE", "'"},

        // SolarisPrincipals
        {"SolarisNumericGroupPrincipal.Primary.Group.",
                "SolarisNumericGroupPrincipal [\u4E3B\u7FA4\u7D44]: "},
        {"SolarisNumericGroupPrincipal.Supplementary.Group.",
                "SolarisNumericGroupPrincipal [\u9644\u52A0\u7FA4\u7D44]: "},
        {"SolarisNumericUserPrincipal.",
                "SolarisNumericUserPrincipal: "},
        {"SolarisPrincipal.", "SolarisPrincipal: "},
        // provided.null.name is the NullPointerException message when a
        // developer incorrectly passes a null name to the constructor of
        // subclasses of java.security.Principal
        {"provided.null.name", "\u63D0\u4F9B\u7A7A\u503C\u540D\u7A31"}

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
