/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
public class Resources_ja extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "\u8B66\u544A: \u5225\u540D{0}\u306E\u516C\u958B\u9375\u304C\u5B58\u5728\u3057\u307E\u305B\u3093\u3002\u30AD\u30FC\u30B9\u30C8\u30A2\u304C\u6B63\u3057\u304F\u69CB\u6210\u3055\u308C\u3066\u3044\u308B\u3053\u3068\u3092\u78BA\u8A8D\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"Warning.Class.not.found.class", "\u8B66\u544A: \u30AF\u30E9\u30B9\u304C\u898B\u3064\u304B\u308A\u307E\u305B\u3093: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "\u8B66\u544A: \u30B3\u30F3\u30B9\u30C8\u30E9\u30AF\u30BF\u306E\u5F15\u6570\u304C\u7121\u52B9\u3067\u3059: {0}"},
        {"Illegal.Principal.Type.type", "\u4E0D\u6B63\u306A\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u30BF\u30A4\u30D7: {0}"},
        {"Illegal.option.option", "\u4E0D\u6B63\u306A\u30AA\u30D7\u30B7\u30E7\u30F3: {0}"},
        {"Usage.policytool.options.", "\u4F7F\u7528\u65B9\u6CD5: policytool [options]"},
        {".file.file.policy.file.location",
                "  [-file <file>]  \u30DD\u30EA\u30B7\u30FC\u30FB\u30D5\u30A1\u30A4\u30EB\u306E\u5834\u6240"},
        {"New", "\u65B0\u898F"},
        {"Open", "\u958B\u304F"},
        {"Save", "\u4FDD\u5B58"},
        {"Save.As", "\u5225\u540D\u4FDD\u5B58"},
        {"View.Warning.Log", "\u8B66\u544A\u30ED\u30B0\u306E\u8868\u793A"},
        {"Exit", "\u7D42\u4E86"},
        {"Add.Policy.Entry", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30A8\u30F3\u30C8\u30EA\u306E\u8FFD\u52A0"},
        {"Edit.Policy.Entry", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30A8\u30F3\u30C8\u30EA\u306E\u7DE8\u96C6"},
        {"Remove.Policy.Entry", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30A8\u30F3\u30C8\u30EA\u306E\u524A\u9664"},
        {"Edit", "\u7DE8\u96C6"},
        {"Retain", "\u4FDD\u6301"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "\u8B66\u544A: \u30D5\u30A1\u30A4\u30EB\u540D\u306B\u30A8\u30B9\u30B1\u30FC\u30D7\u3055\u308C\u305F\u30D0\u30C3\u30AF\u30B9\u30E9\u30C3\u30B7\u30E5\u6587\u5B57\u304C\u542B\u307E\u308C\u3066\u3044\u308B\u53EF\u80FD\u6027\u304C\u3042\u308A\u307E\u3059\u3002\u30D0\u30C3\u30AF\u30B9\u30E9\u30C3\u30B7\u30E5\u6587\u5B57\u3092\u30A8\u30B9\u30B1\u30FC\u30D7\u3059\u308B\u5FC5\u8981\u306F\u3042\u308A\u307E\u305B\u3093(\u30C4\u30FC\u30EB\u306F\u30DD\u30EA\u30B7\u30FC\u5185\u5BB9\u3092\u6C38\u7D9A\u30B9\u30C8\u30A2\u306B\u66F8\u304D\u8FBC\u3080\u3068\u304D\u306B\u3001\u5FC5\u8981\u306B\u5FDC\u3058\u3066\u6587\u5B57\u3092\u30A8\u30B9\u30B1\u30FC\u30D7\u3057\u307E\u3059)\u3002\n\n\u5165\u529B\u6E08\u306E\u540D\u524D\u3092\u4FDD\u6301\u3059\u308B\u306B\u306F\u300C\u4FDD\u6301\u300D\u3092\u30AF\u30EA\u30C3\u30AF\u3057\u3001\u540D\u524D\u3092\u7DE8\u96C6\u3059\u308B\u306B\u306F\u300C\u7DE8\u96C6\u300D\u3092\u30AF\u30EA\u30C3\u30AF\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},

        {"Add.Public.Key.Alias", "\u516C\u958B\u9375\u306E\u5225\u540D\u306E\u8FFD\u52A0"},
        {"Remove.Public.Key.Alias", "\u516C\u958B\u9375\u306E\u5225\u540D\u3092\u524A\u9664"},
        {"File", "\u30D5\u30A1\u30A4\u30EB"},
        {"KeyStore", "\u30AD\u30FC\u30B9\u30C8\u30A2"},
        {"Policy.File.", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30D5\u30A1\u30A4\u30EB:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "\u30DD\u30EA\u30B7\u30FC\u30FB\u30D5\u30A1\u30A4\u30EB\u3092\u958B\u3051\u307E\u305B\u3093\u3067\u3057\u305F: {0}: {1}"},
        {"Policy.Tool", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30C4\u30FC\u30EB"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "\u30DD\u30EA\u30B7\u30FC\u69CB\u6210\u3092\u958B\u304F\u3068\u304D\u306B\u30A8\u30E9\u30FC\u304C\u767A\u751F\u3057\u307E\u3057\u305F\u3002\u8A73\u7D30\u306F\u8B66\u544A\u30ED\u30B0\u3092\u53C2\u7167\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"Error", "\u30A8\u30E9\u30FC"},
        {"OK", "OK"},
        {"Status", "\u72B6\u614B"},
        {"Warning", "\u8B66\u544A"},
        {"Permission.",
                "\u30A2\u30AF\u30BB\u30B9\u6A29:                                                       "},
        {"Principal.Type.", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u30BF\u30A4\u30D7:"},
        {"Principal.Name.", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u540D\u524D:"},
        {"Target.Name.",
                "\u30BF\u30FC\u30B2\u30C3\u30C8\u540D:                                                    "},
        {"Actions.",
                "\u30A2\u30AF\u30B7\u30E7\u30F3:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "\u65E2\u5B58\u306E\u30D5\u30A1\u30A4\u30EB{0}\u306B\u4E0A\u66F8\u304D\u3057\u307E\u3059\u304B\u3002"},
        {"Cancel", "\u53D6\u6D88"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u8FFD\u52A0"},
        {"Edit.Principal", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u7DE8\u96C6"},
        {"Remove.Principal", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u524A\u9664"},
        {"Principals.", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB:"},
        {".Add.Permission", "  \u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u8FFD\u52A0"},
        {".Edit.Permission", "  \u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u7DE8\u96C6"},
        {"Remove.Permission", "\u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u524A\u9664"},
        {"Done", "\u5B8C\u4E86"},
        {"KeyStore.URL.", "\u30AD\u30FC\u30B9\u30C8\u30A2URL:"},
        {"KeyStore.Type.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30BF\u30A4\u30D7:"},
        {"KeyStore.Provider.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D7\u30ED\u30D0\u30A4\u30C0:"},
        {"KeyStore.Password.URL.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D1\u30B9\u30EF\u30FC\u30C9URL:"},
        {"Principals", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB"},
        {".Edit.Principal.", "  \u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u7DE8\u96C6:"},
        {".Add.New.Principal.", "  \u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u65B0\u898F\u8FFD\u52A0:"},
        {"Permissions", "\u30A2\u30AF\u30BB\u30B9\u6A29"},
        {".Edit.Permission.", "  \u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u7DE8\u96C6:"},
        {".Add.New.Permission.", "  \u65B0\u898F\u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u8FFD\u52A0:"},
        {"Signed.By.", "\u7F72\u540D\u8005:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9\u540D\u306E\u306A\u3044\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9\u30FB\u30AF\u30E9\u30B9\u3092\u4F7F\u7528\u3057\u3066\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u3092\u6307\u5B9A\u3059\u308B\u3053\u3068\u306F\u3067\u304D\u307E\u305B\u3093"},
        {"Cannot.Specify.Principal.without.a.Name",
            "\u540D\u524D\u3092\u4F7F\u7528\u305B\u305A\u306B\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u3092\u6307\u5B9A\u3059\u308B\u3053\u3068\u306F\u3067\u304D\u307E\u305B\u3093"},
        {"Permission.and.Target.Name.must.have.a.value",
                "\u30A2\u30AF\u30BB\u30B9\u6A29\u3068\u30BF\u30FC\u30B2\u30C3\u30C8\u540D\u306F\u3001\u5024\u3092\u4FDD\u6301\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Remove.this.Policy.Entry.", "\u3053\u306E\u30DD\u30EA\u30B7\u30FC\u30FB\u30A8\u30F3\u30C8\u30EA\u3092\u524A\u9664\u3057\u307E\u3059\u304B\u3002"},
        {"Overwrite.File", "\u30D5\u30A1\u30A4\u30EB\u3092\u4E0A\u66F8\u304D\u3057\u307E\u3059"},
        {"Policy.successfully.written.to.filename",
                "\u30DD\u30EA\u30B7\u30FC\u306E{0}\u3078\u306E\u66F8\u8FBC\u307F\u306B\u6210\u529F\u3057\u307E\u3057\u305F"},
        {"null.filename", "\u30D5\u30A1\u30A4\u30EB\u540D\u304Cnull\u3067\u3059"},
        {"Save.changes.", "\u5909\u66F4\u3092\u4FDD\u5B58\u3057\u307E\u3059\u304B\u3002"},
        {"Yes", "\u306F\u3044"},
        {"No", "\u3044\u3044\u3048"},
        {"Policy.Entry", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30A8\u30F3\u30C8\u30EA"},
        {"Save.Changes", "\u5909\u66F4\u3092\u4FDD\u5B58\u3057\u307E\u3059"},
        {"No.Policy.Entry.selected", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30A8\u30F3\u30C8\u30EA\u304C\u9078\u629E\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "\u30AD\u30FC\u30B9\u30C8\u30A2{0}\u3092\u958B\u3051\u307E\u305B\u3093"},
        {"No.principal.selected", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u304C\u9078\u629E\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"No.permission.selected", "\u30A2\u30AF\u30BB\u30B9\u6A29\u304C\u9078\u629E\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"name", "\u540D\u524D"},
        {"configuration.type", "\u69CB\u6210\u30BF\u30A4\u30D7"},
        {"environment.variable.name", "\u74B0\u5883\u5909\u6570\u540D"},
        {"library.name", "\u30E9\u30A4\u30D6\u30E9\u30EA\u540D"},
        {"package.name", "\u30D1\u30C3\u30B1\u30FC\u30B8\u540D"},
        {"policy.type", "\u30DD\u30EA\u30B7\u30FC\u30FB\u30BF\u30A4\u30D7"},
        {"property.name", "\u30D7\u30ED\u30D1\u30C6\u30A3\u540D"},
        {"provider.name", "\u30D7\u30ED\u30D0\u30A4\u30C0\u540D"},
        {"url", "URL"},
        {"method.list", "\u30E1\u30BD\u30C3\u30C9\u30FB\u30EA\u30B9\u30C8"},
        {"request.headers.list", "\u30EA\u30AF\u30A8\u30B9\u30C8\u30FB\u30D8\u30C3\u30C0\u30FC\u30FB\u30EA\u30B9\u30C8"},
        {"Principal.List", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u30EA\u30B9\u30C8"},
        {"Permission.List", "\u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u30EA\u30B9\u30C8"},
        {"Code.Base", "\u30B3\u30FC\u30C9\u30FB\u30D9\u30FC\u30B9"},
        {"KeyStore.U.R.L.", "\u30AD\u30FC\u30B9\u30C8\u30A2U R L:"},
        {"KeyStore.Password.U.R.L.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D1\u30B9\u30EF\u30FC\u30C9U R L:"}
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
