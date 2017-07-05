/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
public class Resources_zh_CN extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "\u8B66\u544A: \u522B\u540D {0} \u7684\u516C\u5171\u5BC6\u94A5\u4E0D\u5B58\u5728\u3002\u8BF7\u786E\u4FDD\u5DF2\u6B63\u786E\u914D\u7F6E\u5BC6\u94A5\u5E93\u3002"},
        {"Warning.Class.not.found.class", "\u8B66\u544A: \u627E\u4E0D\u5230\u7C7B: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "\u8B66\u544A: \u6784\u9020\u5668\u7684\u53C2\u6570\u65E0\u6548: {0}"},
        {"Illegal.Principal.Type.type", "\u975E\u6CD5\u7684\u4E3B\u7528\u6237\u7C7B\u578B: {0}"},
        {"Illegal.option.option", "\u975E\u6CD5\u9009\u9879: {0}"},
        {"Usage.policytool.options.", "\u7528\u6CD5: policytool [\u9009\u9879]"},
        {".file.file.policy.file.location",
                "  [-file <file>]    \u7B56\u7565\u6587\u4EF6\u4F4D\u7F6E"},
        {"New", "\u65B0\u5EFA"},
        {"Open", "\u6253\u5F00"},
        {"Save", "\u4FDD\u5B58"},
        {"Save.As", "\u53E6\u5B58\u4E3A"},
        {"View.Warning.Log", "\u67E5\u770B\u8B66\u544A\u65E5\u5FD7"},
        {"Exit", "\u9000\u51FA"},
        {"Add.Policy.Entry", "\u6DFB\u52A0\u7B56\u7565\u6761\u76EE"},
        {"Edit.Policy.Entry", "\u7F16\u8F91\u7B56\u7565\u6761\u76EE"},
        {"Remove.Policy.Entry", "\u5220\u9664\u7B56\u7565\u6761\u76EE"},
        {"Edit", "\u7F16\u8F91"},
        {"Retain", "\u4FDD\u7559"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "\u8B66\u544A: \u6587\u4EF6\u540D\u5305\u542B\u8F6C\u4E49\u7684\u53CD\u659C\u6760\u5B57\u7B26\u3002\u4E0D\u9700\u8981\u5BF9\u53CD\u659C\u6760\u5B57\u7B26\u8FDB\u884C\u8F6C\u4E49 (\u8BE5\u5DE5\u5177\u5728\u5C06\u7B56\u7565\u5185\u5BB9\u5199\u5165\u6C38\u4E45\u5B58\u50A8\u65F6\u4F1A\u6839\u636E\u9700\u8981\u5BF9\u5B57\u7B26\u8FDB\u884C\u8F6C\u4E49)\u3002\n\n\u5355\u51FB\u201C\u4FDD\u7559\u201D\u53EF\u4FDD\u7559\u8F93\u5165\u7684\u540D\u79F0, \u6216\u8005\u5355\u51FB\u201C\u7F16\u8F91\u201D\u53EF\u7F16\u8F91\u8BE5\u540D\u79F0\u3002"},

        {"Add.Public.Key.Alias", "\u6DFB\u52A0\u516C\u5171\u5BC6\u94A5\u522B\u540D"},
        {"Remove.Public.Key.Alias", "\u5220\u9664\u516C\u5171\u5BC6\u94A5\u522B\u540D"},
        {"File", "\u6587\u4EF6"},
        {"KeyStore", "\u5BC6\u94A5\u5E93"},
        {"Policy.File.", "\u7B56\u7565\u6587\u4EF6:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "\u65E0\u6CD5\u6253\u5F00\u7B56\u7565\u6587\u4EF6: {0}: {1}"},
        {"Policy.Tool", "\u7B56\u7565\u5DE5\u5177"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "\u6253\u5F00\u7B56\u7565\u914D\u7F6E\u65F6\u51FA\u9519\u3002\u6709\u5173\u8BE6\u7EC6\u4FE1\u606F, \u8BF7\u67E5\u770B\u8B66\u544A\u65E5\u5FD7\u3002"},
        {"Error", "\u9519\u8BEF"},
        {"OK", "\u786E\u5B9A"},
        {"Status", "\u72B6\u6001"},
        {"Warning", "\u8B66\u544A"},
        {"Permission.",
                "\u6743\u9650:                                                       "},
        {"Principal.Type.", "\u4E3B\u7528\u6237\u7C7B\u578B:"},
        {"Principal.Name.", "\u4E3B\u7528\u6237\u540D\u79F0:"},
        {"Target.Name.",
                "\u76EE\u6807\u540D\u79F0:                                                    "},
        {"Actions.",
                "\u64CD\u4F5C:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "\u786E\u8BA4\u8986\u76D6\u73B0\u6709\u7684\u6587\u4EF6{0}?"},
        {"Cancel", "\u53D6\u6D88"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "\u6DFB\u52A0\u4E3B\u7528\u6237"},
        {"Edit.Principal", "\u7F16\u8F91\u4E3B\u7528\u6237"},
        {"Remove.Principal", "\u5220\u9664\u4E3B\u7528\u6237"},
        {"Principals.", "\u4E3B\u7528\u6237:"},
        {".Add.Permission", "  \u6DFB\u52A0\u6743\u9650"},
        {".Edit.Permission", "  \u7F16\u8F91\u6743\u9650"},
        {"Remove.Permission", "\u5220\u9664\u6743\u9650"},
        {"Done", "\u5B8C\u6210"},
        {"KeyStore.URL.", "\u5BC6\u94A5\u5E93 URL:"},
        {"KeyStore.Type.", "\u5BC6\u94A5\u5E93\u7C7B\u578B:"},
        {"KeyStore.Provider.", "\u5BC6\u94A5\u5E93\u63D0\u4F9B\u65B9:"},
        {"KeyStore.Password.URL.", "\u5BC6\u94A5\u5E93\u53E3\u4EE4 URL:"},
        {"Principals", "\u4E3B\u7528\u6237"},
        {".Edit.Principal.", "  \u7F16\u8F91\u4E3B\u7528\u6237:"},
        {".Add.New.Principal.", "  \u6DFB\u52A0\u65B0\u4E3B\u7528\u6237:"},
        {"Permissions", "\u6743\u9650"},
        {".Edit.Permission.", "  \u7F16\u8F91\u6743\u9650:"},
        {".Add.New.Permission.", "  \u52A0\u5165\u65B0\u7684\u6743\u9650:"},
        {"Signed.By.", "\u7B7E\u7F72\u4EBA: "},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "\u6CA1\u6709\u901A\u914D\u7B26\u540D\u79F0, \u65E0\u6CD5\u4F7F\u7528\u901A\u914D\u7B26\u7C7B\u6307\u5B9A\u4E3B\u7528\u6237"},
        {"Cannot.Specify.Principal.without.a.Name",
            "\u6CA1\u6709\u540D\u79F0, \u65E0\u6CD5\u6307\u5B9A\u4E3B\u7528\u6237"},
        {"Permission.and.Target.Name.must.have.a.value",
                "\u6743\u9650\u53CA\u76EE\u6807\u540D\u5FC5\u987B\u6709\u4E00\u4E2A\u503C"},
        {"Remove.this.Policy.Entry.", "\u662F\u5426\u5220\u9664\u6B64\u7B56\u7565\u6761\u76EE?"},
        {"Overwrite.File", "\u8986\u76D6\u6587\u4EF6"},
        {"Policy.successfully.written.to.filename",
                "\u7B56\u7565\u5DF2\u6210\u529F\u5199\u5165\u5230{0}"},
        {"null.filename", "\u7A7A\u6587\u4EF6\u540D"},
        {"Save.changes.", "\u662F\u5426\u4FDD\u5B58\u6240\u505A\u7684\u66F4\u6539?"},
        {"Yes", "\u662F"},
        {"No", "\u5426"},
        {"Policy.Entry", "\u7B56\u7565\u6761\u76EE"},
        {"Save.Changes", "\u4FDD\u5B58\u66F4\u6539"},
        {"No.Policy.Entry.selected", "\u6CA1\u6709\u9009\u62E9\u7B56\u7565\u6761\u76EE"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "\u65E0\u6CD5\u6253\u5F00\u5BC6\u94A5\u5E93: {0}"},
        {"No.principal.selected", "\u672A\u9009\u62E9\u4E3B\u7528\u6237"},
        {"No.permission.selected", "\u6CA1\u6709\u9009\u62E9\u6743\u9650"},
        {"name", "\u540D\u79F0"},
        {"configuration.type", "\u914D\u7F6E\u7C7B\u578B"},
        {"environment.variable.name", "\u73AF\u5883\u53D8\u91CF\u540D"},
        {"library.name", "\u5E93\u540D\u79F0"},
        {"package.name", "\u7A0B\u5E8F\u5305\u540D\u79F0"},
        {"policy.type", "\u7B56\u7565\u7C7B\u578B"},
        {"property.name", "\u5C5E\u6027\u540D\u79F0"},
        {"provider.name", "\u63D0\u4F9B\u65B9\u540D\u79F0"},
        {"url", "URL"},
        {"method.list", "\u65B9\u6CD5\u5217\u8868"},
        {"request.headers.list", "\u8BF7\u6C42\u6807\u5934\u5217\u8868"},
        {"Principal.List", "\u4E3B\u7528\u6237\u5217\u8868"},
        {"Permission.List", "\u6743\u9650\u5217\u8868"},
        {"Code.Base", "\u4EE3\u7801\u5E93"},
        {"KeyStore.U.R.L.", "\u5BC6\u94A5\u5E93 URL:"},
        {"KeyStore.Password.U.R.L.", "\u5BC6\u94A5\u5E93\u53E3\u4EE4 URL:"}
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
