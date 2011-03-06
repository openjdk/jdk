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
 * for javax.security.auth and sun.security.
 *
 */
public class Resources_zh_CN extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // shared (from jarsigner)
        {"SPACE", " "},
        {"2SPACE", "  "},
        {"6SPACE", "      "},
        {"COMMA", ", "},
        // shared (from keytool)
        {"NEWLINE", "\n"},
        {"STAR",
                "*******************************************"},
        {"STARNN",
                "*******************************************\n\n"},

        // keytool: Help part
        {".OPTION.", " [OPTION]..."},
        {"Options.", "\u9009\u9879:"},
        {"Use.keytool.help.for.all.available.commands",
                 "\u4F7F\u7528 \"keytool -help\" \u83B7\u53D6\u6240\u6709\u53EF\u7528\u547D\u4EE4"},
        {"Key.and.Certificate.Management.Tool",
                 "\u5BC6\u94A5\u548C\u8BC1\u4E66\u7BA1\u7406\u5DE5\u5177"},
        {"Commands.", "\u547D\u4EE4:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "\u4F7F\u7528 \"keytool -command_name -help\" \u83B7\u53D6 command_name \u7684\u7528\u6CD5"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "\u751F\u6210\u8BC1\u4E66\u8BF7\u6C42"}, //-certreq
        {"Changes.an.entry.s.alias",
                "\u66F4\u6539\u6761\u76EE\u7684\u522B\u540D"}, //-changealias
        {"Deletes.an.entry",
                "\u5220\u9664\u6761\u76EE"}, //-delete
        {"Exports.certificate",
                "\u5BFC\u51FA\u8BC1\u4E66"}, //-exportcert
        {"Generates.a.key.pair",
                "\u751F\u6210\u5BC6\u94A5\u5BF9"}, //-genkeypair
        {"Generates.a.secret.key",
                "\u751F\u6210\u5BC6\u94A5"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "\u6839\u636E\u8BC1\u4E66\u8BF7\u6C42\u751F\u6210\u8BC1\u4E66"}, //-gencert
        {"Generates.CRL", "\u751F\u6210 CRL"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "\u4ECE JDK 1.1.x \u6837\u5F0F\u7684\u8EAB\u4EFD\u6570\u636E\u5E93\u5BFC\u5165\u6761\u76EE"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "\u5BFC\u5165\u8BC1\u4E66\u6216\u8BC1\u4E66\u94FE"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "\u4ECE\u5176\u4ED6\u5BC6\u94A5\u5E93\u5BFC\u5165\u4E00\u4E2A\u6216\u6240\u6709\u6761\u76EE"}, //-importkeystore
        {"Clones.a.key.entry",
                "\u514B\u9686\u5BC6\u94A5\u6761\u76EE"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "\u66F4\u6539\u6761\u76EE\u7684\u5BC6\u94A5\u53E3\u4EE4"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "\u5217\u51FA\u5BC6\u94A5\u5E93\u4E2D\u7684\u6761\u76EE"}, //-list
        {"Prints.the.content.of.a.certificate",
                "\u6253\u5370\u8BC1\u4E66\u5185\u5BB9"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "\u6253\u5370\u8BC1\u4E66\u8BF7\u6C42\u7684\u5185\u5BB9"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "\u6253\u5370 CRL \u6587\u4EF6\u7684\u5185\u5BB9"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "\u751F\u6210\u81EA\u7B7E\u540D\u8BC1\u4E66"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "\u66F4\u6539\u5BC6\u94A5\u5E93\u7684\u5B58\u50A8\u53E3\u4EE4"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "\u8981\u5904\u7406\u7684\u6761\u76EE\u7684\u522B\u540D"}, //-alias
        {"destination.alias",
                "\u76EE\u6807\u522B\u540D"}, //-destalias
        {"destination.key.password",
                "\u76EE\u6807\u5BC6\u94A5\u53E3\u4EE4"}, //-destkeypass
        {"destination.keystore.name",
                "\u76EE\u6807\u5BC6\u94A5\u5E93\u540D\u79F0"}, //-destkeystore
        {"destination.keystore.password.protected",
                "\u53D7\u4FDD\u62A4\u7684\u76EE\u6807\u5BC6\u94A5\u5E93\u53E3\u4EE4"}, //-destprotected
        {"destination.keystore.provider.name",
                "\u76EE\u6807\u5BC6\u94A5\u5E93\u63D0\u4F9B\u65B9\u540D\u79F0"}, //-destprovidername
        {"destination.keystore.password",
                "\u76EE\u6807\u5BC6\u94A5\u5E93\u53E3\u4EE4"}, //-deststorepass
        {"destination.keystore.type",
                "\u76EE\u6807\u5BC6\u94A5\u5E93\u7C7B\u578B"}, //-deststoretype
        {"distinguished.name",
                "\u552F\u4E00\u5224\u522B\u540D"}, //-dname
        {"X.509.extension",
                "X.509 \u6269\u5C55"}, //-ext
        {"output.file.name",
                "\u8F93\u51FA\u6587\u4EF6\u540D"}, //-file and -outfile
        {"input.file.name",
                "\u8F93\u5165\u6587\u4EF6\u540D"}, //-file and -infile
        {"key.algorithm.name",
                "\u5BC6\u94A5\u7B97\u6CD5\u540D\u79F0"}, //-keyalg
        {"key.password",
                "\u5BC6\u94A5\u53E3\u4EE4"}, //-keypass
        {"key.bit.size",
                "\u5BC6\u94A5\u4F4D\u5927\u5C0F"}, //-keysize
        {"keystore.name",
                "\u5BC6\u94A5\u5E93\u540D\u79F0"}, //-keystore
        {"new.password",
                "\u65B0\u53E3\u4EE4"}, //-new
        {"do.not.prompt",
                "\u4E0D\u63D0\u793A"}, //-noprompt
        {"password.through.protected.mechanism",
                "\u901A\u8FC7\u53D7\u4FDD\u62A4\u7684\u673A\u5236\u7684\u53E3\u4EE4"}, //-protected
        {"provider.argument",
                "\u63D0\u4F9B\u65B9\u53C2\u6570"}, //-providerarg
        {"provider.class.name",
                "\u63D0\u4F9B\u65B9\u7C7B\u540D"}, //-providerclass
        {"provider.name",
                "\u63D0\u4F9B\u65B9\u540D\u79F0"}, //-providername
        {"provider.classpath",
                "\u63D0\u4F9B\u65B9\u7C7B\u8DEF\u5F84"}, //-providerpath
        {"output.in.RFC.style",
                "\u4EE5 RFC \u6837\u5F0F\u8F93\u51FA"}, //-rfc
        {"signature.algorithm.name",
                "\u7B7E\u540D\u7B97\u6CD5\u540D\u79F0"}, //-sigalg
        {"source.alias",
                "\u6E90\u522B\u540D"}, //-srcalias
        {"source.key.password",
                "\u6E90\u5BC6\u94A5\u53E3\u4EE4"}, //-srckeypass
        {"source.keystore.name",
                "\u6E90\u5BC6\u94A5\u5E93\u540D\u79F0"}, //-srckeystore
        {"source.keystore.password.protected",
                "\u53D7\u4FDD\u62A4\u7684\u6E90\u5BC6\u94A5\u5E93\u53E3\u4EE4"}, //-srcprotected
        {"source.keystore.provider.name",
                "\u6E90\u5BC6\u94A5\u5E93\u63D0\u4F9B\u65B9\u540D\u79F0"}, //-srcprovidername
        {"source.keystore.password",
                "\u6E90\u5BC6\u94A5\u5E93\u53E3\u4EE4"}, //-srcstorepass
        {"source.keystore.type",
                "\u6E90\u5BC6\u94A5\u5E93\u7C7B\u578B"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "SSL \u670D\u52A1\u5668\u4E3B\u673A\u548C\u7AEF\u53E3"}, //-sslserver
        {"signed.jar.file",
                "\u5DF2\u7B7E\u540D\u7684 jar \u6587\u4EF6"}, //=jarfile
        {"certificate.validity.start.date.time",
                "\u8BC1\u4E66\u6709\u6548\u671F\u5F00\u59CB\u65E5\u671F/\u65F6\u95F4"}, //-startdate
        {"keystore.password",
                "\u5BC6\u94A5\u5E93\u53E3\u4EE4"}, //-storepass
        {"keystore.type",
                "\u5BC6\u94A5\u5E93\u7C7B\u578B"}, //-storetype
        {"trust.certificates.from.cacerts",
                "\u4FE1\u4EFB\u6765\u81EA cacerts \u7684\u8BC1\u4E66"}, //-trustcacerts
        {"verbose.output",
                "\u8BE6\u7EC6\u8F93\u51FA"}, //-v
        {"validity.number.of.days",
                "\u6709\u6548\u5929\u6570"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "\u8981\u64A4\u9500\u7684\u8BC1\u4E66\u7684\u5E8F\u5217 ID"}, //-id
        // keytool: Running part
        {"keytool.error.", "keytool \u9519\u8BEF: "},
        {"Illegal.option.", "\u975E\u6CD5\u9009\u9879:  "},
        {"Illegal.value.", "\u975E\u6CD5\u503C: "},
        {"Unknown.password.type.", "\u672A\u77E5\u53E3\u4EE4\u7C7B\u578B: "},
        {"Cannot.find.environment.variable.",
                "\u627E\u4E0D\u5230\u73AF\u5883\u53D8\u91CF: "},
        {"Cannot.find.file.", "\u627E\u4E0D\u5230\u6587\u4EF6: "},
        {"Command.option.flag.needs.an.argument.", "\u547D\u4EE4\u9009\u9879{0}\u9700\u8981\u4E00\u4E2A\u53C2\u6570\u3002"},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "\u8B66\u544A: PKCS12 KeyStores \u4E0D\u652F\u6301\u5176\u4ED6\u5B58\u50A8\u548C\u5BC6\u94A5\u53E3\u4EE4\u3002\u6B63\u5728\u5FFD\u7565\u7528\u6237\u6307\u5B9A\u7684{0}\u503C\u3002"},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u4E3A {0}, \u5219 -keystore \u5FC5\u987B\u4E3A NONE"},
        {"Too.many.retries.program.terminated",
                 "\u91CD\u8BD5\u6B21\u6570\u8FC7\u591A, \u7A0B\u5E8F\u5DF2\u7EC8\u6B62"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u4E3A {0}, \u5219\u4E0D\u652F\u6301 -storepasswd \u548C -keypasswd \u547D\u4EE4"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "\u5982\u679C -storetype \u4E3A PKCS12, \u5219\u4E0D\u652F\u6301 -keypasswd \u547D\u4EE4"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u4E3A {0}, \u5219\u4E0D\u80FD\u6307\u5B9A -keypass \u548C -new"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "\u5982\u679C\u6307\u5B9A\u4E86 -protected, \u5219\u4E0D\u80FD\u6307\u5B9A -storepass, -keypass \u548C -new"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "\u5982\u679C\u6307\u5B9A\u4E86 -srcprotected, \u5219\u4E0D\u80FD\u6307\u5B9A -srcstorepass \u548C -srckeypass"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "\u5982\u679C\u5BC6\u94A5\u5E93\u672A\u53D7\u53E3\u4EE4\u4FDD\u62A4, \u5219\u4E0D\u80FD\u6307\u5B9A -storepass, -keypass \u548C -new"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "\u5982\u679C\u6E90\u5BC6\u94A5\u5E93\u672A\u53D7\u53E3\u4EE4\u4FDD\u62A4, \u5219\u4E0D\u80FD\u6307\u5B9A -srcstorepass \u548C -srckeypass"},
        {"Illegal.startdate.value", "\u975E\u6CD5\u5F00\u59CB\u65E5\u671F\u503C"},
        {"Validity.must.be.greater.than.zero",
                "\u6709\u6548\u6027\u5FC5\u987B\u5927\u4E8E\u96F6"},
        {"provName.not.a.provider", "{0}\u4E0D\u662F\u63D0\u4F9B\u65B9"},
        {"Usage.error.no.command.provided", "\u7528\u6CD5\u9519\u8BEF: \u6CA1\u6709\u63D0\u4F9B\u547D\u4EE4"},
        {"Source.keystore.file.exists.but.is.empty.", "\u6E90\u5BC6\u94A5\u5E93\u6587\u4EF6\u5B58\u5728, \u4F46\u4E3A\u7A7A: "},
        {"Please.specify.srckeystore", "\u8BF7\u6307\u5B9A -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "\u4E0D\u80FD\u4F7F\u7528 'list' \u547D\u4EE4\u6765\u6307\u5B9A -v \u53CA -rfc"},
        {"Key.password.must.be.at.least.6.characters",
                "\u5BC6\u94A5\u53E3\u4EE4\u81F3\u5C11\u5FC5\u987B\u4E3A 6 \u4E2A\u5B57\u7B26"},
        {"New.password.must.be.at.least.6.characters",
                "\u65B0\u53E3\u4EE4\u81F3\u5C11\u5FC5\u987B\u4E3A 6 \u4E2A\u5B57\u7B26"},
        {"Keystore.file.exists.but.is.empty.",
                "\u5BC6\u94A5\u5E93\u6587\u4EF6\u5B58\u5728, \u4F46\u4E3A\u7A7A: "},
        {"Keystore.file.does.not.exist.",
                "\u5BC6\u94A5\u5E93\u6587\u4EF6\u4E0D\u5B58\u5728: "},
        {"Must.specify.destination.alias", "\u5FC5\u987B\u6307\u5B9A\u76EE\u6807\u522B\u540D"},
        {"Must.specify.alias", "\u5FC5\u987B\u6307\u5B9A\u522B\u540D"},
        {"Keystore.password.must.be.at.least.6.characters",
                "\u5BC6\u94A5\u5E93\u53E3\u4EE4\u81F3\u5C11\u5FC5\u987B\u4E3A 6 \u4E2A\u5B57\u7B26"},
        {"Enter.keystore.password.", "\u8F93\u5165\u5BC6\u94A5\u5E93\u53E3\u4EE4:  "},
        {"Enter.source.keystore.password.", "\u8F93\u5165\u6E90\u5BC6\u94A5\u5E93\u53E3\u4EE4:  "},
        {"Enter.destination.keystore.password.", "\u8F93\u5165\u76EE\u6807\u5BC6\u94A5\u5E93\u53E3\u4EE4:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "\u5BC6\u94A5\u5E93\u53E3\u4EE4\u592A\u77ED - \u81F3\u5C11\u5FC5\u987B\u4E3A 6 \u4E2A\u5B57\u7B26"},
        {"Unknown.Entry.Type", "\u672A\u77E5\u6761\u76EE\u7C7B\u578B"},
        {"Too.many.failures.Alias.not.changed", "\u6545\u969C\u592A\u591A\u3002\u672A\u66F4\u6539\u522B\u540D"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "\u5DF2\u6210\u529F\u5BFC\u5165\u522B\u540D {0} \u7684\u6761\u76EE\u3002"},
        {"Entry.for.alias.alias.not.imported.", "\u672A\u5BFC\u5165\u522B\u540D {0} \u7684\u6761\u76EE\u3002"},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "\u5BFC\u5165\u522B\u540D {0} \u7684\u6761\u76EE\u65F6\u51FA\u73B0\u95EE\u9898: {1}\u3002\n\u672A\u5BFC\u5165\u522B\u540D {0} \u7684\u6761\u76EE\u3002"},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "\u5DF2\u5B8C\u6210\u5BFC\u5165\u547D\u4EE4: {0} \u4E2A\u6761\u76EE\u6210\u529F\u5BFC\u5165, {1} \u4E2A\u6761\u76EE\u5931\u8D25\u6216\u53D6\u6D88"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "\u8B66\u544A: \u6B63\u5728\u8986\u76D6\u76EE\u6807\u5BC6\u94A5\u5E93\u4E2D\u7684\u73B0\u6709\u522B\u540D {0}"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "\u5B58\u5728\u73B0\u6709\u6761\u76EE\u522B\u540D {0}, \u662F\u5426\u8986\u76D6? [\u5426]:  "},
        {"Too.many.failures.try.later", "\u6545\u969C\u592A\u591A - \u8BF7\u7A0D\u540E\u518D\u8BD5"},
        {"Certification.request.stored.in.file.filename.",
                "\u5B58\u50A8\u5728\u6587\u4EF6 <{0}> \u4E2D\u7684\u8BA4\u8BC1\u8BF7\u6C42"},
        {"Submit.this.to.your.CA", "\u5C06\u6B64\u63D0\u4EA4\u7ED9\u60A8\u7684 CA"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "\u5982\u679C\u6CA1\u6709\u6307\u5B9A\u522B\u540D, \u5219\u4E0D\u80FD\u6307\u5B9A\u76EE\u6807\u522B\u540D, \u6E90\u5BC6\u94A5\u5E93\u53E3\u4EE4\u548C\u76EE\u6807\u5BC6\u94A5\u5E93\u53E3\u4EE4"},
        {"Certificate.stored.in.file.filename.",
                "\u5B58\u50A8\u5728\u6587\u4EF6 <{0}> \u4E2D\u7684\u8BC1\u4E66"},
        {"Certificate.reply.was.installed.in.keystore",
                "\u8BC1\u4E66\u56DE\u590D\u5DF2\u5B89\u88C5\u5728\u5BC6\u94A5\u5E93\u4E2D"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "\u8BC1\u4E66\u56DE\u590D\u672A\u5B89\u88C5\u5728\u5BC6\u94A5\u5E93\u4E2D"},
        {"Certificate.was.added.to.keystore",
                "\u8BC1\u4E66\u5DF2\u6DFB\u52A0\u5230\u5BC6\u94A5\u5E93\u4E2D"},
        {"Certificate.was.not.added.to.keystore",
                "\u8BC1\u4E66\u672A\u6DFB\u52A0\u5230\u5BC6\u94A5\u5E93\u4E2D"},
        {".Storing.ksfname.", "[\u6B63\u5728\u5B58\u50A8{0}]"},
        {"alias.has.no.public.key.certificate.",
                "{0}\u6CA1\u6709\u516C\u5171\u5BC6\u94A5 (\u8BC1\u4E66)"},
        {"Cannot.derive.signature.algorithm",
                "\u65E0\u6CD5\u6D3E\u751F\u7B7E\u540D\u7B97\u6CD5"},
        {"Alias.alias.does.not.exist",
                "\u522B\u540D <{0}> \u4E0D\u5B58\u5728"},
        {"Alias.alias.has.no.certificate",
                "\u522B\u540D <{0}> \u6CA1\u6709\u8BC1\u4E66"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "\u672A\u751F\u6210\u5BC6\u94A5\u5BF9, \u522B\u540D <{0}> \u5DF2\u7ECF\u5B58\u5728"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "\u6B63\u5728\u4E3A\u4EE5\u4E0B\u5BF9\u8C61\u751F\u6210 {0} \u4F4D{1}\u5BC6\u94A5\u5BF9\u548C\u81EA\u7B7E\u540D\u8BC1\u4E66 ({2}) (\u6709\u6548\u671F\u4E3A {3} \u5929):\n\t {4}"},
        {"Enter.key.password.for.alias.", "\u8F93\u5165 <{0}> \u7684\u5BC6\u94A5\u53E3\u4EE4"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(\u5982\u679C\u548C\u5BC6\u94A5\u5E93\u53E3\u4EE4\u76F8\u540C, \u6309\u56DE\u8F66):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "\u5BC6\u94A5\u53E3\u4EE4\u592A\u77ED - \u81F3\u5C11\u5FC5\u987B\u4E3A 6 \u4E2A\u5B57\u7B26"},
        {"Too.many.failures.key.not.added.to.keystore",
                "\u6545\u969C\u592A\u591A - \u5BC6\u94A5\u672A\u6DFB\u52A0\u5230\u5BC6\u94A5\u5E93\u4E2D"},
        {"Destination.alias.dest.already.exists",
                "\u76EE\u6807\u522B\u540D <{0}> \u5DF2\u7ECF\u5B58\u5728"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "\u53E3\u4EE4\u592A\u77ED - \u81F3\u5C11\u5FC5\u987B\u4E3A 6 \u4E2A\u5B57\u7B26"},
        {"Too.many.failures.Key.entry.not.cloned",
                "\u6545\u969C\u592A\u591A\u3002\u672A\u514B\u9686\u5BC6\u94A5\u6761\u76EE"},
        {"key.password.for.alias.", "<{0}> \u7684\u5BC6\u94A5\u53E3\u4EE4"},
        {"Keystore.entry.for.id.getName.already.exists",
                "<{0}> \u7684\u5BC6\u94A5\u5E93\u6761\u76EE\u5DF2\u7ECF\u5B58\u5728"},
        {"Creating.keystore.entry.for.id.getName.",
                "\u6B63\u5728\u521B\u5EFA <{0}> \u7684\u5BC6\u94A5\u5E93\u6761\u76EE..."},
        {"No.entries.from.identity.database.added",
                "\u672A\u4ECE\u8EAB\u4EFD\u6570\u636E\u5E93\u4E2D\u6DFB\u52A0\u4EFB\u4F55\u6761\u76EE"},
        {"Alias.name.alias", "\u522B\u540D: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "\u521B\u5EFA\u65E5\u671F: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "\u6761\u76EE\u7C7B\u578B: {0}"},
        {"Certificate.chain.length.", "\u8BC1\u4E66\u94FE\u957F\u5EA6: "},
        {"Certificate.i.1.", "\u8BC1\u4E66[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "\u8BC1\u4E66\u6307\u7EB9 (SHA1): "},
        {"Entry.type.trustedCertEntry.", "\u6761\u76EE\u7C7B\u578B: trustedCertEntry\n"},
        {"trustedCertEntry.", "trustedCertEntry,"},
        {"Keystore.type.", "\u5BC6\u94A5\u5E93\u7C7B\u578B: "},
        {"Keystore.provider.", "\u5BC6\u94A5\u5E93\u63D0\u4F9B\u65B9: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "\u60A8\u7684\u5BC6\u94A5\u5E93\u5305\u542B {0,number,integer} \u4E2A\u6761\u76EE"},
        {"Your.keystore.contains.keyStore.size.entries",
                "\u60A8\u7684\u5BC6\u94A5\u5E93\u5305\u542B {0,number,integer} \u4E2A\u6761\u76EE"},
        {"Failed.to.parse.input", "\u65E0\u6CD5\u5BF9\u8F93\u5165\u8FDB\u884C\u8BED\u6CD5\u5206\u6790"},
        {"Empty.input", "\u7A7A\u8F93\u5165"},
        {"Not.X.509.certificate", "\u975E X.509 \u8BC1\u4E66"},
        {"alias.has.no.public.key", "{0}\u6CA1\u6709\u516C\u5171\u5BC6\u94A5"},
        {"alias.has.no.X.509.certificate", "{0}\u6CA1\u6709 X.509 \u8BC1\u4E66"},
        {"New.certificate.self.signed.", "\u65B0\u8BC1\u4E66 (\u81EA\u7B7E\u540D):"},
        {"Reply.has.no.certificates", "\u56DE\u590D\u4E2D\u6CA1\u6709\u8BC1\u4E66"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "\u8BC1\u4E66\u672A\u5BFC\u5165, \u522B\u540D <{0}> \u5DF2\u7ECF\u5B58\u5728"},
        {"Input.not.an.X.509.certificate", "\u6240\u8F93\u5165\u7684\u4E0D\u662F X.509 \u8BC1\u4E66"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "\u5728\u522B\u540D <{0}> \u4E4B\u4E0B, \u8BC1\u4E66\u5DF2\u7ECF\u5B58\u5728\u4E8E\u5BC6\u94A5\u5E93\u4E2D"},
        {"Do.you.still.want.to.add.it.no.",
                "\u662F\u5426\u4ECD\u8981\u6DFB\u52A0? [\u5426]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "\u5728\u522B\u540D <{0}> \u4E4B\u4E0B, \u8BC1\u4E66\u5DF2\u7ECF\u5B58\u5728\u4E8E\u7CFB\u7EDF\u8303\u56F4\u7684 CA \u5BC6\u94A5\u5E93\u4E2D"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "\u662F\u5426\u4ECD\u8981\u5C06\u5B83\u6DFB\u52A0\u5230\u81EA\u5DF1\u7684\u5BC6\u94A5\u5E93? [\u5426]:  "},
        {"Trust.this.certificate.no.", "\u662F\u5426\u4FE1\u4EFB\u6B64\u8BC1\u4E66? [\u5426]:  "},
        {"YES", "YES"},
        {"New.prompt.", "\u65B0{0}: "},
        {"Passwords.must.differ", "\u53E3\u4EE4\u4E0D\u80FD\u76F8\u540C"},
        {"Re.enter.new.prompt.", "\u91CD\u65B0\u8F93\u5165\u65B0{0}: "},
        {"Re.enter.new.password.", "\u518D\u6B21\u8F93\u5165\u65B0\u53E3\u4EE4: "},
        {"They.don.t.match.Try.again", "\u5B83\u4EEC\u4E0D\u5339\u914D\u3002\u8BF7\u91CD\u8BD5"},
        {"Enter.prompt.alias.name.", "\u8F93\u5165{0}\u522B\u540D:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "\u5BFC\u5165\u65B0\u7684\u522B\u540D\t(\u6309\u56DE\u8F66\u4EE5\u53D6\u6D88\u5BF9\u6B64\u6761\u76EE\u7684\u5BFC\u5165):  "},
        {"Enter.alias.name.", "\u8F93\u5165\u522B\u540D:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(\u5982\u679C\u548C <{0}> \u76F8\u540C, \u5219\u6309\u56DE\u8F66)"},
        {".PATTERN.printX509Cert",
                "\u6240\u6709\u8005: {0}\n\u53D1\u5E03\u8005: {1}\n\u5E8F\u5217\u53F7: {2}\n\u6709\u6548\u671F\u5F00\u59CB\u65E5\u671F: {3}, \u622A\u6B62\u65E5\u671F: {4}\n\u8BC1\u4E66\u6307\u7EB9:\n\t MD5: {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t \u7B7E\u540D\u7B97\u6CD5\u540D\u79F0: {8}\n\t \u7248\u672C: {9}"},
        {"What.is.your.first.and.last.name.",
                "\u60A8\u7684\u540D\u5B57\u4E0E\u59D3\u6C0F\u662F\u4EC0\u4E48?"},
        {"What.is.the.name.of.your.organizational.unit.",
                "\u60A8\u7684\u7EC4\u7EC7\u5355\u4F4D\u540D\u79F0\u662F\u4EC0\u4E48?"},
        {"What.is.the.name.of.your.organization.",
                "\u60A8\u7684\u7EC4\u7EC7\u540D\u79F0\u662F\u4EC0\u4E48?"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "\u60A8\u6240\u5728\u7684\u57CE\u5E02\u6216\u533A\u57DF\u540D\u79F0\u662F\u4EC0\u4E48?"},
        {"What.is.the.name.of.your.State.or.Province.",
                "\u60A8\u6240\u5728\u7684\u7701/\u5E02/\u81EA\u6CBB\u533A\u540D\u79F0\u662F\u4EC0\u4E48?"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "\u8BE5\u5355\u4F4D\u7684\u53CC\u5B57\u6BCD\u56FD\u5BB6/\u5730\u533A\u4EE3\u7801\u662F\u4EC0\u4E48?"},
        {"Is.name.correct.", "{0}\u662F\u5426\u6B63\u786E?"},
        {"no", "\u5426"},
        {"yes", "\u662F"},
        {"y", "y"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "\u522B\u540D <{0}> \u6CA1\u6709\u5BC6\u94A5"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "\u522B\u540D <{0}> \u5F15\u7528\u4E86\u4E0D\u5C5E\u4E8E\u79C1\u6709\u5BC6\u94A5\u6761\u76EE\u7684\u6761\u76EE\u7C7B\u578B\u3002-keyclone \u547D\u4EE4\u4EC5\u652F\u6301\u5BF9\u79C1\u6709\u5BC6\u94A5\u6761\u76EE\u7684\u514B\u9686"},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "\u7B7E\u540D\u8005 #%d:"},
        {"Timestamp.", "\u65F6\u95F4\u6233:"},
        {"Signature.", "\u7B7E\u540D:"},
        {"CRLs.", "CRL:"},
        {"Certificate.owner.", "\u8BC1\u4E66\u6240\u6709\u8005: "},
        {"Not.a.signed.jar.file", "\u4E0D\u662F\u5DF2\u7B7E\u540D\u7684 jar \u6587\u4EF6"},
        {"No.certificate.from.the.SSL.server",
                "\u6CA1\u6709\u6765\u81EA SSL \u670D\u52A1\u5668\u7684\u8BC1\u4E66"},

        // Translators of the following 5 pairs, ATTENTION:
        // the next 5 string pairs are meant to be combined into 2 paragraphs,
        // 1+3+4 and 2+3+5. make sure your translation also does.
        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* \u5B58\u50A8\u5728\u60A8\u7684\u5BC6\u94A5\u5E93\u4E2D\u7684\u4FE1\u606F\u7684\u5B8C\u6574\u6027  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* \u5B58\u50A8\u5728 srckeystore \u4E2D\u7684\u4FE1\u606F\u7684\u5B8C\u6574\u6027 *"},
        {".has.NOT.been.verified.In.order.to.verify.its.integrity.",
            "* \u5C1A\u672A\u7ECF\u8FC7\u9A8C\u8BC1!  \u4E3A\u4E86\u9A8C\u8BC1\u5176\u5B8C\u6574\u6027, *"},
        {".you.must.provide.your.keystore.password.",
            "* \u5FC5\u987B\u63D0\u4F9B\u5BC6\u94A5\u5E93\u53E3\u4EE4\u3002                  *"},
        {".you.must.provide.the.srckeystore.password.",
            "* \u5FC5\u987B\u63D0\u4F9B\u6E90\u5BC6\u94A5\u5E93\u53E3\u4EE4\u3002                *"},


        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "\u8BC1\u4E66\u56DE\u590D\u4E2D\u4E0D\u5305\u542B <{0}> \u7684\u516C\u5171\u5BC6\u94A5"},
        {"Incomplete.certificate.chain.in.reply",
                "\u56DE\u590D\u4E2D\u7684\u8BC1\u4E66\u94FE\u4E0D\u5B8C\u6574"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "\u56DE\u590D\u4E2D\u7684\u8BC1\u4E66\u94FE\u672A\u9A8C\u8BC1: "},
        {"Top.level.certificate.in.reply.",
                "\u56DE\u590D\u4E2D\u7684\u9876\u7EA7\u8BC1\u4E66:\n"},
        {".is.not.trusted.", "... \u662F\u4E0D\u53EF\u4FE1\u7684\u3002"},
        {"Install.reply.anyway.no.", "\u662F\u5426\u4ECD\u8981\u5B89\u88C5\u56DE\u590D? [\u5426]:  "},
        {"NO", "NO"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "\u56DE\u590D\u4E2D\u7684\u516C\u5171\u5BC6\u94A5\u4E0E\u5BC6\u94A5\u5E93\u4E0D\u5339\u914D"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "\u8BC1\u4E66\u56DE\u590D\u4E0E\u5BC6\u94A5\u5E93\u4E2D\u7684\u8BC1\u4E66\u662F\u76F8\u540C\u7684"},
        {"Failed.to.establish.chain.from.reply",
                "\u65E0\u6CD5\u4ECE\u56DE\u590D\u4E2D\u5EFA\u7ACB\u94FE"},
        {"n", "n"},
        {"Wrong.answer.try.again", "\u9519\u8BEF\u7684\u7B54\u6848, \u8BF7\u518D\u8BD5\u4E00\u6B21"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "\u6CA1\u6709\u751F\u6210\u5BC6\u94A5, \u522B\u540D <{0}> \u5DF2\u7ECF\u5B58\u5728"},
        {"Please.provide.keysize.for.secret.key.generation",
                "\u8BF7\u63D0\u4F9B -keysize \u4EE5\u751F\u6210\u5BC6\u94A5"},

        {"Extensions.", "\u6269\u5C55: "},
        {".Empty.value.", "(\u7A7A\u503C)"},
        {"Extension.Request.", "\u6269\u5C55\u8BF7\u6C42:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "PKCS #10 \u8BC1\u4E66\u8BF7\u6C42 (\u7248\u672C 1.0)\n\u4E3B\u9898: %s\n\u516C\u5171\u5BC6\u94A5: %s \u683C\u5F0F %s \u5BC6\u94A5\n"},
        {"Unknown.keyUsage.type.", "\u672A\u77E5 keyUsage \u7C7B\u578B: "},
        {"Unknown.extendedkeyUsage.type.", "\u672A\u77E5 extendedkeyUsage \u7C7B\u578B: "},
        {"Unknown.AccessDescription.type.", "\u672A\u77E5 AccessDescription \u7C7B\u578B: "},
        {"Unrecognized.GeneralName.type.", "\u65E0\u6CD5\u8BC6\u522B\u7684 GeneralName \u7C7B\u578B: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "\u65E0\u6CD5\u5C06\u6B64\u6269\u5C55\u6807\u8BB0\u4E3A\u201C\u4E25\u91CD\u201D\u3002"},
        {"Odd.number.of.hex.digits.found.", "\u627E\u5230\u5947\u6570\u4E2A\u5341\u516D\u8FDB\u5236\u6570\u5B57: "},
        {"Unknown.extension.type.", "\u672A\u77E5\u6269\u5C55\u7C7B\u578B: "},
        {"command.{0}.is.ambiguous.", "\u547D\u4EE4{0}\u4E0D\u660E\u786E:"},

        // policytool
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "\u8B66\u544A: \u522B\u540D {0} \u7684\u516C\u5171\u5BC6\u94A5\u4E0D\u5B58\u5728\u3002\u8BF7\u786E\u4FDD\u5DF2\u6B63\u786E\u914D\u7F6E KeyStore\u3002"},
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
        {"KeyStore", "KeyStore"},
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
        {"KeyStore.URL.", "KeyStore URL:"},
        {"KeyStore.Type.", "KeyStore \u7C7B\u578B:"},
        {"KeyStore.Provider.", "KeyStore \u63D0\u4F9B\u65B9:"},
        {"KeyStore.Password.URL.", "KeyStore \u53E3\u4EE4 URL:"},
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
                "\u65E0\u6CD5\u6253\u5F00 KeyStore: {0}"},
        {"No.principal.selected", "\u672A\u9009\u62E9\u4E3B\u7528\u6237"},
        {"No.permission.selected", "\u6CA1\u6709\u9009\u62E9\u6743\u9650"},
        {"name", "\u540D\u79F0"},
        {"configuration.type", "\u914D\u7F6E\u7C7B\u578B"},
        {"environment.variable.name", "\u73AF\u5883\u53D8\u91CF\u540D"},
        {"library.name", "\u5E93\u540D\u79F0"},
        {"package.name", "\u7A0B\u5E8F\u5305\u540D\u79F0"},
        {"policy.type", "\u7B56\u7565\u7C7B\u578B"},
        {"property.name", "\u5C5E\u6027\u540D\u79F0"},
        {"Principal.List", "\u4E3B\u7528\u6237\u5217\u8868"},
        {"Permission.List", "\u6743\u9650\u5217\u8868"},
        {"Code.Base", "\u4EE3\u7801\u5E93"},
        {"KeyStore.U.R.L.", "KeyStore URL:"},
        {"KeyStore.Password.U.R.L.", "KeyStore \u53E3\u4EE4 URL:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "\u65E0\u6548\u7684\u7A7A\u8F93\u5165"},
        {"actions.can.only.be.read.", "\u64CD\u4F5C\u53EA\u80FD\u4E3A '\u8BFB\u53D6'"},
        {"permission.name.name.syntax.invalid.",
                "\u6743\u9650\u540D\u79F0 [{0}] \u8BED\u6CD5\u65E0\u6548: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "\u8EAB\u4EFD\u8BC1\u660E\u7C7B\u540E\u9762\u672A\u8DDF\u968F\u4E3B\u7528\u6237\u7C7B\u53CA\u540D\u79F0"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "\u4E3B\u7528\u6237\u7C7B\u540E\u9762\u672A\u8DDF\u968F\u4E3B\u7528\u6237\u540D\u79F0"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "\u4E3B\u7528\u6237\u540D\u79F0\u5FC5\u987B\u653E\u5728\u5F15\u53F7\u5185"},
        {"Principal.Name.missing.end.quote",
                "\u4E3B\u7528\u6237\u540D\u79F0\u7F3A\u5C11\u53F3\u5F15\u53F7"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "\u5982\u679C\u4E3B\u7528\u6237\u540D\u79F0\u4E0D\u662F\u901A\u914D\u7B26 (*) \u503C, \u90A3\u4E48 PrivateCredentialPermission \u4E3B\u7528\u6237\u7C7B\u4E0D\u80FD\u662F\u901A\u914D\u7B26 (*) \u503C"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\t\u4E3B\u7528\u6237\u7C7B = {0}\n\t\u4E3B\u7528\u6237\u540D\u79F0 = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "\u63D0\u4F9B\u7684\u540D\u79F0\u4E3A\u7A7A\u503C"},
        {"provided.null.keyword.map", "\u63D0\u4F9B\u7684\u5173\u952E\u5B57\u6620\u5C04\u4E3A\u7A7A\u503C"},
        {"provided.null.OID.map", "\u63D0\u4F9B\u7684 OID \u6620\u5C04\u4E3A\u7A7A\u503C"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "\u63D0\u4F9B\u4E86\u65E0\u6548\u7684\u7A7A AccessControlContext"},
        {"invalid.null.action.provided", "\u63D0\u4F9B\u4E86\u65E0\u6548\u7684\u7A7A\u64CD\u4F5C"},
        {"invalid.null.Class.provided", "\u63D0\u4F9B\u4E86\u65E0\u6548\u7684\u7A7A\u7C7B"},
        {"Subject.", "\u4E3B\u9898: \n"},
        {".Principal.", "\t\u4E3B\u7528\u6237: "},
        {".Public.Credential.", "\t\u516C\u5171\u8EAB\u4EFD\u8BC1\u660E: "},
        {".Private.Credentials.inaccessible.",
                "\t\u65E0\u6CD5\u8BBF\u95EE\u4E13\u7528\u8EAB\u4EFD\u8BC1\u660E\n"},
        {".Private.Credential.", "\t\u4E13\u7528\u8EAB\u4EFD\u8BC1\u660E: "},
        {".Private.Credential.inaccessible.",
                "\t\u65E0\u6CD5\u8BBF\u95EE\u4E13\u7528\u8EAB\u4EFD\u8BC1\u660E\n"},
        {"Subject.is.read.only", "\u4E3B\u9898\u4E3A\u53EA\u8BFB"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "\u6B63\u5728\u5C1D\u8BD5\u5C06\u4E00\u4E2A\u975E java.security.Principal \u5B9E\u4F8B\u7684\u5BF9\u8C61\u6DFB\u52A0\u5230\u4E3B\u9898\u7684\u4E3B\u7528\u6237\u96C6\u4E2D"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "\u6B63\u5728\u5C1D\u8BD5\u6DFB\u52A0\u4E00\u4E2A\u975E{0}\u5B9E\u4F8B\u7684\u5BF9\u8C61"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "\u65E0\u6548\u7A7A\u8F93\u5165: \u540D\u79F0"},
        {"No.LoginModules.configured.for.name",
         "\u6CA1\u6709\u4E3A{0}\u914D\u7F6E LoginModules"},
        {"invalid.null.Subject.provided", "\u63D0\u4F9B\u4E86\u65E0\u6548\u7684\u7A7A\u4E3B\u9898"},
        {"invalid.null.CallbackHandler.provided",
                "\u63D0\u4F9B\u4E86\u65E0\u6548\u7684\u7A7A CallbackHandler"},
        {"null.subject.logout.called.before.login",
                "\u7A7A\u4E3B\u9898 - \u5728\u767B\u5F55\u4E4B\u524D\u8C03\u7528\u4E86\u6CE8\u9500"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "\u65E0\u6CD5\u5B9E\u4F8B\u5316 LoginModule, {0}, \u56E0\u4E3A\u5B83\u672A\u63D0\u4F9B\u4E00\u4E2A\u65E0\u53C2\u6570\u6784\u9020\u5668"},
        {"unable.to.instantiate.LoginModule",
                "\u65E0\u6CD5\u5B9E\u4F8B\u5316 LoginModule"},
        {"unable.to.instantiate.LoginModule.",
                "\u65E0\u6CD5\u5B9E\u4F8B\u5316 LoginModule: "},
        {"unable.to.find.LoginModule.class.",
                "\u65E0\u6CD5\u627E\u5230 LoginModule \u7C7B: "},
        {"unable.to.access.LoginModule.",
                "\u65E0\u6CD5\u8BBF\u95EE LoginModule: "},
        {"Login.Failure.all.modules.ignored",
                "\u767B\u5F55\u5931\u8D25: \u5FFD\u7565\u6240\u6709\u6A21\u5757"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: \u5BF9{0}\u8FDB\u884C\u8BED\u6CD5\u5206\u6790\u65F6\u51FA\u9519:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: \u6DFB\u52A0\u6743\u9650{0}\u65F6\u51FA\u9519:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: \u6DFB\u52A0\u6761\u76EE\u65F6\u51FA\u9519:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "\u672A\u63D0\u4F9B\u522B\u540D ({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "\u65E0\u6CD5\u5728\u522B\u540D {0} \u4E0A\u6267\u884C\u66FF\u4EE3"},
        {"substitution.value.prefix.unsupported",
                "\u66FF\u4EE3\u503C{0}\u4E0D\u53D7\u652F\u6301"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","\u7C7B\u578B\u4E0D\u80FD\u4E3A\u7A7A\u503C"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "\u4E0D\u6307\u5B9A\u5BC6\u94A5\u5E93\u65F6\u65E0\u6CD5\u6307\u5B9A keystorePasswordURL"},
        {"expected.keystore.type", "\u5E94\u4E3A\u5BC6\u94A5\u5E93\u7C7B\u578B"},
        {"expected.keystore.provider", "\u5E94\u4E3A\u5BC6\u94A5\u5E93\u63D0\u4F9B\u65B9"},
        {"multiple.Codebase.expressions",
                "\u591A\u4E2A\u4EE3\u7801\u5E93\u8868\u8FBE\u5F0F"},
        {"multiple.SignedBy.expressions","\u591A\u4E2A SignedBy \u8868\u8FBE\u5F0F"},
        {"SignedBy.has.empty.alias","SignedBy \u6709\u7A7A\u522B\u540D"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "\u6CA1\u6709\u901A\u914D\u7B26\u540D\u79F0, \u65E0\u6CD5\u4F7F\u7528\u901A\u914D\u7B26\u7C7B\u6307\u5B9A\u4E3B\u7528\u6237"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "\u5E94\u4E3A codeBase, SignedBy \u6216\u4E3B\u7528\u6237"},
        {"expected.permission.entry", "\u5E94\u4E3A\u6743\u9650\u6761\u76EE"},
        {"number.", "\u7F16\u53F7 "},
        {"expected.expect.read.end.of.file.",
                "\u5E94\u4E3A [{0}], \u8BFB\u53D6\u7684\u662F [\u6587\u4EF6\u7ED3\u5C3E]"},
        {"expected.read.end.of.file.",
                "\u5E94\u4E3A [;], \u8BFB\u53D6\u7684\u662F [\u6587\u4EF6\u7ED3\u5C3E]"},
        {"line.number.msg", "\u5217{0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "\u884C\u53F7 {0}: \u5E94\u4E3A [{1}], \u627E\u5230 [{2}]"},
        {"null.principalClass.or.principalName",
                "principalClass \u6216 principalName \u4E3A\u7A7A\u503C"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "PKCS11 \u6807\u8BB0 [{0}] \u53E3\u4EE4: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "\u65E0\u6CD5\u5B9E\u4F8B\u5316\u57FA\u4E8E\u4E3B\u9898\u7684\u7B56\u7565"}
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

