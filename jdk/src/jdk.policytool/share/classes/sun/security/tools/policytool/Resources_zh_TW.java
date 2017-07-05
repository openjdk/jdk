/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.tools.policytool;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the policytool.
 *
 */
public class Resources_zh_TW extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "\u8B66\u544A: \u5225\u540D {0} \u7684\u516C\u958B\u91D1\u9470\u4E0D\u5B58\u5728\u3002\u8ACB\u78BA\u5B9A\u91D1\u9470\u5132\u5B58\u5EAB\u8A2D\u5B9A\u6B63\u78BA\u3002"},
        {"Warning.Class.not.found.class", "\u8B66\u544A: \u627E\u4E0D\u5230\u985E\u5225 {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "\u8B66\u544A: \u7121\u6548\u7684\u5EFA\u69CB\u5B50\u5F15\u6578: {0}"},
        {"Illegal.Principal.Type.type", "\u7121\u6548\u7684 Principal \u985E\u578B: {0}"},
        {"Illegal.option.option", "\u7121\u6548\u7684\u9078\u9805: {0}"},
        {"Usage.policytool.options.", "\u7528\u6CD5: policytool [options]"},
        {".file.file.policy.file.location",
                "  [-file <file>]    \u539F\u5247\u6A94\u6848\u4F4D\u7F6E"},
        {"New", "\u65B0\u5EFA(&N)"},
        {"Open", "\u958B\u555F(&O)..."},
        {"Save", "\u5132\u5B58(&S)"},
        {"Save.As", "\u53E6\u5B58\u65B0\u6A94(&A)..."},
        {"View.Warning.Log", "\u6AA2\u8996\u8B66\u544A\u8A18\u9304(&W)"},
        {"Exit", "\u7D50\u675F(&X)"},
        {"Add.Policy.Entry", "\u65B0\u589E\u539F\u5247\u9805\u76EE(&A)"},
        {"Edit.Policy.Entry", "\u7DE8\u8F2F\u539F\u5247\u9805\u76EE(&E)"},
        {"Remove.Policy.Entry", "\u79FB\u9664\u539F\u5247\u9805\u76EE(&R)"},
        {"Edit", "\u7DE8\u8F2F(&E)"},
        {"Retain", "\u4FDD\u7559"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "\u8B66\u544A: \u6A94\u6848\u540D\u7A31\u5305\u542B\u9041\u96E2\u53CD\u659C\u7DDA\u5B57\u5143\u3002\u4E0D\u9700\u8981\u9041\u96E2\u53CD\u659C\u7DDA\u5B57\u5143 (\u64B0\u5BEB\u539F\u5247\u5167\u5BB9\u81F3\u6C38\u4E45\u5B58\u653E\u5340\u6642\u9700\u8981\u5DE5\u5177\u9041\u96E2\u5B57\u5143)\u3002\n\n\u6309\u4E00\u4E0B\u300C\u4FDD\u7559\u300D\u4EE5\u4FDD\u7559\u8F38\u5165\u7684\u540D\u7A31\uFF0C\u6216\u6309\u4E00\u4E0B\u300C\u7DE8\u8F2F\u300D\u4EE5\u7DE8\u8F2F\u540D\u7A31\u3002"},

        {"Add.Public.Key.Alias", "\u65B0\u589E\u516C\u958B\u91D1\u9470\u5225\u540D"},
        {"Remove.Public.Key.Alias", "\u79FB\u9664\u516C\u958B\u91D1\u9470\u5225\u540D"},
        {"File", "\u6A94\u6848(&F)"},
        {"KeyStore", "\u91D1\u9470\u5132\u5B58\u5EAB(&K)"},
        {"Policy.File.", "\u539F\u5247\u6A94\u6848: "},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "\u7121\u6CD5\u958B\u555F\u539F\u5247\u6A94\u6848: {0}: {1}"},
        {"Policy.Tool", "\u539F\u5247\u5DE5\u5177"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "\u958B\u555F\u539F\u5247\u8A18\u7F6E\u6642\u767C\u751F\u932F\u8AA4\u3002\u8ACB\u6AA2\u8996\u8B66\u544A\u8A18\u9304\u4EE5\u53D6\u5F97\u66F4\u591A\u7684\u8CC7\u8A0A"},
        {"Error", "\u932F\u8AA4"},
        {"OK", "\u78BA\u5B9A"},
        {"Status", "\u72C0\u614B"},
        {"Warning", "\u8B66\u544A"},
        {"Permission.",
                "\u6B0A\u9650:                                                       "},
        {"Principal.Type.", "Principal \u985E\u578B: "},
        {"Principal.Name.", "Principal \u540D\u7A31: "},
        {"Target.Name.",
                "\u76EE\u6A19\u540D\u7A31:                                                    "},
        {"Actions.",
                "\u52D5\u4F5C:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "\u78BA\u8A8D\u8986\u5BEB\u73FE\u5B58\u7684\u6A94\u6848 {0}\uFF1F"},
        {"Cancel", "\u53D6\u6D88"},
        {"CodeBase.", "CodeBase(&C):"},
        {"SignedBy.", "SignedBy(&S):"},
        {"Add.Principal", "\u65B0\u589E Principal(&A)"},
        {"Edit.Principal", "\u7DE8\u8F2F Principal(&E)"},
        {"Remove.Principal", "\u79FB\u9664 Principal(&R)"},
        {"Principals.", "Principal(&P):"},
        {".Add.Permission", "  \u65B0\u589E\u6B0A\u9650(&D)"},
        {".Edit.Permission", "  \u7DE8\u8F2F\u6B0A\u9650(&I)"},
        {"Remove.Permission", "\u79FB\u9664\u6B0A\u9650(&M)"},
        {"Done", "\u5B8C\u6210"},
        {"KeyStore.URL.", "\u91D1\u9470\u5132\u5B58\u5EAB URL(&U): "},
        {"KeyStore.Type.", "\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B(&T):"},
        {"KeyStore.Provider.", "\u91D1\u9470\u5132\u5B58\u5EAB\u63D0\u4F9B\u8005(&P):"},
        {"KeyStore.Password.URL.", "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC URL(&W): "},
        {"Principals", "Principal"},
        {".Edit.Principal.", "  \u7DE8\u8F2F Principal: "},
        {".Add.New.Principal.", "  \u65B0\u589E Principal: "},
        {"Permissions", "\u6B0A\u9650"},
        {".Edit.Permission.", "  \u7DE8\u8F2F\u6B0A\u9650:"},
        {".Add.New.Permission.", "  \u65B0\u589E\u6B0A\u9650:"},
        {"Signed.By.", "\u7C3D\u7F72\u4EBA: "},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "\u6C92\u6709\u842C\u7528\u5B57\u5143\u540D\u7A31\uFF0C\u7121\u6CD5\u6307\u5B9A\u542B\u6709\u842C\u7528\u5B57\u5143\u985E\u5225\u7684 Principal"},
        {"Cannot.Specify.Principal.without.a.Name",
            "\u6C92\u6709\u540D\u7A31\uFF0C\u7121\u6CD5\u6307\u5B9A Principal"},
        {"Permission.and.Target.Name.must.have.a.value",
                "\u6B0A\u9650\u53CA\u76EE\u6A19\u540D\u7A31\u5FC5\u9808\u6709\u4E00\u500B\u503C\u3002"},
        {"Remove.this.Policy.Entry.", "\u79FB\u9664\u9019\u500B\u539F\u5247\u9805\u76EE\uFF1F"},
        {"Overwrite.File", "\u8986\u5BEB\u6A94\u6848"},
        {"Policy.successfully.written.to.filename",
                "\u539F\u5247\u6210\u529F\u5BEB\u5165\u81F3 {0}"},
        {"null.filename", "\u7A7A\u503C\u6A94\u540D"},
        {"Save.changes.", "\u5132\u5B58\u8B8A\u66F4\uFF1F"},
        {"Yes", "\u662F(&Y)"},
        {"No", "\u5426(&N)"},
        {"Policy.Entry", "\u539F\u5247\u9805\u76EE"},
        {"Save.Changes", "\u5132\u5B58\u8B8A\u66F4"},
        {"No.Policy.Entry.selected", "\u6C92\u6709\u9078\u53D6\u539F\u5247\u9805\u76EE"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "\u7121\u6CD5\u958B\u555F\u91D1\u9470\u5132\u5B58\u5EAB: {0}"},
        {"No.principal.selected", "\u672A\u9078\u53D6 Principal"},
        {"No.permission.selected", "\u6C92\u6709\u9078\u53D6\u6B0A\u9650"},
        {"name", "\u540D\u7A31"},
        {"configuration.type", "\u7D44\u614B\u985E\u578B"},
        {"environment.variable.name", "\u74B0\u5883\u8B8A\u6578\u540D\u7A31"},
        {"library.name", "\u7A0B\u5F0F\u5EAB\u540D\u7A31"},
        {"package.name", "\u5957\u88DD\u7A0B\u5F0F\u540D\u7A31"},
        {"policy.type", "\u539F\u5247\u985E\u578B"},
        {"property.name", "\u5C6C\u6027\u540D\u7A31"},
        {"provider.name", "\u63D0\u4F9B\u8005\u540D\u7A31"},
        {"url", "URL"},
        {"method.list", "\u65B9\u6CD5\u6E05\u55AE"},
        {"request.headers.list", "\u8981\u6C42\u6A19\u982D\u6E05\u55AE"},
        {"Principal.List", "Principal \u6E05\u55AE"},
        {"Permission.List", "\u6B0A\u9650\u6E05\u55AE"},
        {"Code.Base", "\u4EE3\u78BC\u57FA\u6E96"},
        {"KeyStore.U.R.L.", "\u91D1\u9470\u5132\u5B58\u5EAB URL:"},
        {"KeyStore.Password.U.R.L.", "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC URL:"}
    };


    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     *
     * <p>
     *
     * @return the contents of this <code>ResourceBundle</code>.
     */
    @Override
    public Object[][] getContents() {
        return contents;
    }
}
