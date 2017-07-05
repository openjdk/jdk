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
public class Resources_ko extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "\uACBD\uACE0: {0} \uBCC4\uCE6D\uC5D0 \uB300\uD55C \uACF5\uC6A9 \uD0A4\uAC00 \uC874\uC7AC\uD558\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4. \uD0A4 \uC800\uC7A5\uC18C\uAC00 \uC81C\uB300\uB85C \uAD6C\uC131\uB418\uC5B4 \uC788\uB294\uC9C0 \uD655\uC778\uD558\uC2ED\uC2DC\uC624."},
        {"Warning.Class.not.found.class", "\uACBD\uACE0: \uD074\uB798\uC2A4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC74C: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "\uACBD\uACE0: \uC0DD\uC131\uC790\uC5D0 \uB300\uD574 \uBD80\uC801\uD569\uD55C \uC778\uC218: {0}"},
        {"Illegal.Principal.Type.type", "\uC798\uBABB\uB41C \uC8FC\uCCB4 \uC720\uD615: {0}"},
        {"Illegal.option.option", "\uC798\uBABB\uB41C \uC635\uC158: {0}"},
        {"Usage.policytool.options.", "\uC0AC\uC6A9\uBC95: policytool [options]"},
        {".file.file.policy.file.location",
                "  [-file <file>]    \uC815\uCC45 \uD30C\uC77C \uC704\uCE58"},
        {"New", "\uC0C8\uB85C \uB9CC\uB4E4\uAE30"},
        {"Open", "\uC5F4\uAE30"},
        {"Save", "\uC800\uC7A5"},
        {"Save.As", "\uB2E4\uB978 \uC774\uB984\uC73C\uB85C \uC800\uC7A5"},
        {"View.Warning.Log", "\uACBD\uACE0 \uB85C\uADF8 \uBCF4\uAE30"},
        {"Exit", "\uC885\uB8CC"},
        {"Add.Policy.Entry", "\uC815\uCC45 \uD56D\uBAA9 \uCD94\uAC00"},
        {"Edit.Policy.Entry", "\uC815\uCC45 \uD56D\uBAA9 \uD3B8\uC9D1"},
        {"Remove.Policy.Entry", "\uC815\uCC45 \uD56D\uBAA9 \uC81C\uAC70"},
        {"Edit", "\uD3B8\uC9D1"},
        {"Retain", "\uC720\uC9C0"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "\uACBD\uACE0: \uD30C\uC77C \uC774\uB984\uC5D0 \uC774\uC2A4\uCF00\uC774\uD504\uB41C \uBC31\uC2AC\uB798\uC2DC \uBB38\uC790\uAC00 \uD3EC\uD568\uB418\uC5C8\uC744 \uC218 \uC788\uC2B5\uB2C8\uB2E4. \uBC31\uC2AC\uB798\uC2DC \uBB38\uC790\uB294 \uC774\uC2A4\uCF00\uC774\uD504\uD560 \uD544\uC694\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4. \uC601\uAD6C \uC800\uC7A5\uC18C\uC5D0 \uC815\uCC45 \uCF58\uD150\uCE20\uB97C \uC4F8 \uB54C \uD544\uC694\uC5D0 \uB530\uB77C \uC790\uB3D9\uC73C\uB85C \uBB38\uC790\uAC00 \uC774\uC2A4\uCF00\uC774\uD504\uB429\uB2C8\uB2E4.\n\n\uC785\uB825\uB41C \uC774\uB984\uC744 \uADF8\uB300\uB85C \uC720\uC9C0\uD558\uB824\uBA74 [\uC720\uC9C0]\uB97C \uB204\uB974\uACE0, \uC774\uB984\uC744 \uD3B8\uC9D1\uD558\uB824\uBA74 [\uD3B8\uC9D1]\uC744 \uB204\uB974\uC2ED\uC2DC\uC624."},

        {"Add.Public.Key.Alias", "\uACF5\uC6A9 \uD0A4 \uBCC4\uCE6D \uCD94\uAC00"},
        {"Remove.Public.Key.Alias", "\uACF5\uC6A9 \uD0A4 \uBCC4\uCE6D \uC81C\uAC70"},
        {"File", "\uD30C\uC77C"},
        {"KeyStore", "\uD0A4 \uC800\uC7A5\uC18C"},
        {"Policy.File.", "\uC815\uCC45 \uD30C\uC77C:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "\uC815\uCC45 \uD30C\uC77C\uC744 \uC5F4 \uC218 \uC5C6\uC74C: {0}: {1}"},
        {"Policy.Tool", "\uC815\uCC45 \uD234"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "\uC815\uCC45 \uAD6C\uC131\uC744 \uC5EC\uB294 \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4. \uC790\uC138\uD55C \uB0B4\uC6A9\uC740 \uACBD\uACE0 \uB85C\uADF8\uB97C \uD655\uC778\uD558\uC2ED\uC2DC\uC624."},
        {"Error", "\uC624\uB958"},
        {"OK", "\uD655\uC778"},
        {"Status", "\uC0C1\uD0DC"},
        {"Warning", "\uACBD\uACE0"},
        {"Permission.",
                "\uAD8C\uD55C:                                                       "},
        {"Principal.Type.", "\uC8FC\uCCB4 \uC720\uD615:"},
        {"Principal.Name.", "\uC8FC\uCCB4 \uC774\uB984:"},
        {"Target.Name.",
                "\uB300\uC0C1 \uC774\uB984:                                                    "},
        {"Actions.",
                "\uC791\uC5C5:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "\uAE30\uC874 \uD30C\uC77C {0}\uC744(\uB97C) \uACB9\uCCD0 \uC4F0\uACA0\uC2B5\uB2C8\uAE4C?"},
        {"Cancel", "\uCDE8\uC18C"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "\uC8FC\uCCB4 \uCD94\uAC00"},
        {"Edit.Principal", "\uC8FC\uCCB4 \uD3B8\uC9D1"},
        {"Remove.Principal", "\uC8FC\uCCB4 \uC81C\uAC70"},
        {"Principals.", "\uC8FC\uCCB4:"},
        {".Add.Permission", "  \uAD8C\uD55C \uCD94\uAC00"},
        {".Edit.Permission", "  \uAD8C\uD55C \uD3B8\uC9D1"},
        {"Remove.Permission", "\uAD8C\uD55C \uC81C\uAC70"},
        {"Done", "\uC644\uB8CC"},
        {"KeyStore.URL.", "\uD0A4 \uC800\uC7A5\uC18C URL:"},
        {"KeyStore.Type.", "\uD0A4 \uC800\uC7A5\uC18C \uC720\uD615:"},
        {"KeyStore.Provider.", "\uD0A4 \uC800\uC7A5\uC18C \uC81C\uACF5\uC790:"},
        {"KeyStore.Password.URL.", "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638 URL:"},
        {"Principals", "\uC8FC\uCCB4"},
        {".Edit.Principal.", "  \uC8FC\uCCB4 \uD3B8\uC9D1:"},
        {".Add.New.Principal.", "  \uC0C8 \uC8FC\uCCB4 \uCD94\uAC00:"},
        {"Permissions", "\uAD8C\uD55C"},
        {".Edit.Permission.", "  \uAD8C\uD55C \uD3B8\uC9D1:"},
        {".Add.New.Permission.", "  \uC0C8 \uAD8C\uD55C \uCD94\uAC00:"},
        {"Signed.By.", "\uC11C\uBA85\uC790:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "\uC640\uC77C\uB4DC \uCE74\uB4DC \uBB38\uC790 \uC774\uB984 \uC5C6\uC774 \uC640\uC77C\uB4DC \uCE74\uB4DC \uBB38\uC790 \uD074\uB798\uC2A4\uB97C \uC0AC\uC6A9\uD558\uB294 \uC8FC\uCCB4\uB97C \uC9C0\uC815\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Cannot.Specify.Principal.without.a.Name",
            "\uC774\uB984 \uC5C6\uC774 \uC8FC\uCCB4\uB97C \uC9C0\uC815\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Permission.and.Target.Name.must.have.a.value",
                "\uAD8C\uD55C\uACFC \uB300\uC0C1 \uC774\uB984\uC758 \uAC12\uC774 \uC788\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Remove.this.Policy.Entry.", "\uC774 \uC815\uCC45 \uD56D\uBAA9\uC744 \uC81C\uAC70\uD558\uACA0\uC2B5\uB2C8\uAE4C?"},
        {"Overwrite.File", "\uD30C\uC77C \uACB9\uCCD0\uC4F0\uAE30"},
        {"Policy.successfully.written.to.filename",
                "{0}\uC5D0 \uC131\uACF5\uC801\uC73C\uB85C \uC815\uCC45\uC744 \uC37C\uC2B5\uB2C8\uB2E4."},
        {"null.filename", "\uB110 \uD30C\uC77C \uC774\uB984"},
        {"Save.changes.", "\uBCC0\uACBD \uC0AC\uD56D\uC744 \uC800\uC7A5\uD558\uACA0\uC2B5\uB2C8\uAE4C?"},
        {"Yes", "\uC608"},
        {"No", "\uC544\uB2C8\uC624"},
        {"Policy.Entry", "\uC815\uCC45 \uD56D\uBAA9"},
        {"Save.Changes", "\uBCC0\uACBD \uC0AC\uD56D \uC800\uC7A5"},
        {"No.Policy.Entry.selected", "\uC120\uD0DD\uB41C \uC815\uCC45 \uD56D\uBAA9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Unable.to.open.KeyStore.ex.toString.",
                "\uD0A4 \uC800\uC7A5\uC18C\uB97C \uC5F4 \uC218 \uC5C6\uC74C: {0}"},
        {"No.principal.selected", "\uC120\uD0DD\uB41C \uC8FC\uCCB4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"No.permission.selected", "\uC120\uD0DD\uB41C \uAD8C\uD55C\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"name", "\uC774\uB984"},
        {"configuration.type", "\uAD6C\uC131 \uC720\uD615"},
        {"environment.variable.name", "\uD658\uACBD \uBCC0\uC218 \uC774\uB984"},
        {"library.name", "\uB77C\uC774\uBE0C\uB7EC\uB9AC \uC774\uB984"},
        {"package.name", "\uD328\uD0A4\uC9C0 \uC774\uB984"},
        {"policy.type", "\uC815\uCC45 \uC720\uD615"},
        {"property.name", "\uC18D\uC131 \uC774\uB984"},
        {"provider.name", "\uC81C\uACF5\uC790 \uC774\uB984"},
        {"url", "URL"},
        {"method.list", "\uBA54\uC18C\uB4DC \uBAA9\uB85D"},
        {"request.headers.list", "\uC694\uCCAD \uD5E4\uB354 \uBAA9\uB85D"},
        {"Principal.List", "\uC8FC\uCCB4 \uBAA9\uB85D"},
        {"Permission.List", "\uAD8C\uD55C \uBAA9\uB85D"},
        {"Code.Base", "\uCF54\uB4DC \uBCA0\uC774\uC2A4"},
        {"KeyStore.U.R.L.", "\uD0A4 \uC800\uC7A5\uC18C URL:"},
        {"KeyStore.Password.U.R.L.", "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638 URL:"}
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
