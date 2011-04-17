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
 * for javax.security.auth and sun.security.
 *
 */
public class Resources_zh_TW extends java.util.ListResourceBundle {

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
        {"Options.", "\u9078\u9805:"},
        {"Use.keytool.help.for.all.available.commands",
                 "\u4F7F\u7528 \"keytool -help\" \u53D6\u5F97\u6240\u6709\u53EF\u7528\u7684\u547D\u4EE4"},
        {"Key.and.Certificate.Management.Tool",
                 "\u91D1\u9470\u8207\u6191\u8B49\u7BA1\u7406\u5DE5\u5177"},
        {"Commands.", "\u547D\u4EE4:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "\u4F7F\u7528 \"keytool -command_name -help\" \u53D6\u5F97 command_name \u7684\u7528\u6CD5"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "\u7522\u751F\u6191\u8B49\u8981\u6C42"}, //-certreq
        {"Changes.an.entry.s.alias",
                "\u8B8A\u66F4\u9805\u76EE\u7684\u5225\u540D"}, //-changealias
        {"Deletes.an.entry",
                "\u522A\u9664\u9805\u76EE"}, //-delete
        {"Exports.certificate",
                "\u532F\u51FA\u6191\u8B49"}, //-exportcert
        {"Generates.a.key.pair",
                "\u7522\u751F\u91D1\u9470\u7D44"}, //-genkeypair
        {"Generates.a.secret.key",
                "\u7522\u751F\u79D8\u5BC6\u91D1\u9470"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "\u5F9E\u6191\u8B49\u8981\u6C42\u7522\u751F\u6191\u8B49"}, //-gencert
        {"Generates.CRL", "\u7522\u751F CRL"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "\u5F9E JDK 1.1.x-style \u8B58\u5225\u8CC7\u6599\u5EAB\u532F\u5165\u9805\u76EE"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "\u532F\u5165\u6191\u8B49\u6216\u6191\u8B49\u93C8"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "\u5F9E\u5176\u4ED6\u91D1\u9470\u5132\u5B58\u5EAB\u532F\u5165\u4E00\u500B\u6216\u5168\u90E8\u9805\u76EE"}, //-importkeystore
        {"Clones.a.key.entry",
                "\u8907\u88FD\u91D1\u9470\u9805\u76EE"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "\u8B8A\u66F4\u9805\u76EE\u7684\u91D1\u9470\u5BC6\u78BC"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "\u5217\u793A\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D\u7684\u9805\u76EE"}, //-list
        {"Prints.the.content.of.a.certificate",
                "\u5217\u5370\u6191\u8B49\u7684\u5167\u5BB9"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "\u5217\u5370\u6191\u8B49\u8981\u6C42\u7684\u5167\u5BB9"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "\u5217\u5370 CRL \u6A94\u6848\u7684\u5167\u5BB9"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "\u7522\u751F\u81EA\u884C\u7C3D\u7F72\u7684\u6191\u8B49"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "\u8B8A\u66F4\u91D1\u9470\u5132\u5B58\u5EAB\u7684\u5132\u5B58\u5BC6\u78BC"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "\u8981\u8655\u7406\u9805\u76EE\u7684\u5225\u540D\u540D\u7A31"}, //-alias
        {"destination.alias",
                "\u76EE\u7684\u5730\u5225\u540D"}, //-destalias
        {"destination.key.password",
                "\u76EE\u7684\u5730\u91D1\u9470\u5BC6\u78BC"}, //-destkeypass
        {"destination.keystore.name",
                "\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u540D\u7A31"}, //-destkeystore
        {"destination.keystore.password.protected",
                "\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC\u4FDD\u8B77"}, //-destprotected
        {"destination.keystore.provider.name",
                "\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u63D0\u4F9B\u8005\u540D\u7A31"}, //-destprovidername
        {"destination.keystore.password",
                "\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC"}, //-deststorepass
        {"destination.keystore.type",
                "\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B"}, //-deststoretype
        {"distinguished.name",
                "\u8FA8\u5225\u540D\u7A31"}, //-dname
        {"X.509.extension",
                "X.509 \u64F4\u5145\u5957\u4EF6"}, //-ext
        {"output.file.name",
                "\u8F38\u51FA\u6A94\u6848\u540D\u7A31"}, //-file and -outfile
        {"input.file.name",
                "\u8F38\u5165\u6A94\u6848\u540D\u7A31"}, //-file and -infile
        {"key.algorithm.name",
                "\u91D1\u9470\u6F14\u7B97\u6CD5\u540D\u7A31"}, //-keyalg
        {"key.password",
                "\u91D1\u9470\u5BC6\u78BC"}, //-keypass
        {"key.bit.size",
                "\u91D1\u9470\u4F4D\u5143\u5927\u5C0F"}, //-keysize
        {"keystore.name",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u540D\u7A31"}, //-keystore
        {"new.password",
                "\u65B0\u5BC6\u78BC"}, //-new
        {"do.not.prompt",
                "\u4E0D\u8981\u63D0\u793A"}, //-noprompt
        {"password.through.protected.mechanism",
                "\u7D93\u7531\u4FDD\u8B77\u6A5F\u5236\u7684\u5BC6\u78BC"}, //-protected
        {"provider.argument",
                "\u63D0\u4F9B\u8005\u5F15\u6578"}, //-providerarg
        {"provider.class.name",
                "\u63D0\u4F9B\u8005\u985E\u5225\u540D\u7A31"}, //-providerclass
        {"provider.name",
                "\u63D0\u4F9B\u8005\u540D\u7A31"}, //-providername
        {"provider.classpath",
                "\u63D0\u4F9B\u8005\u985E\u5225\u8DEF\u5F91"}, //-providerpath
        {"output.in.RFC.style",
                "\u4EE5 RFC \u6A23\u5F0F\u8F38\u51FA"}, //-rfc
        {"signature.algorithm.name",
                "\u7C3D\u7AE0\u6F14\u7B97\u6CD5\u540D\u7A31"}, //-sigalg
        {"source.alias",
                "\u4F86\u6E90\u5225\u540D"}, //-srcalias
        {"source.key.password",
                "\u4F86\u6E90\u91D1\u9470\u5BC6\u78BC"}, //-srckeypass
        {"source.keystore.name",
                "\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u540D\u7A31"}, //-srckeystore
        {"source.keystore.password.protected",
                "\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC\u4FDD\u8B77"}, //-srcprotected
        {"source.keystore.provider.name",
                "\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u63D0\u4F9B\u8005\u540D\u7A31"}, //-srcprovidername
        {"source.keystore.password",
                "\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC"}, //-srcstorepass
        {"source.keystore.type",
                "\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "SSL \u4F3A\u670D\u5668\u4E3B\u6A5F\u8207\u9023\u63A5\u57E0"}, //-sslserver
        {"signed.jar.file",
                "\u7C3D\u7F72\u7684 jar \u6A94\u6848"}, //=jarfile
        {"certificate.validity.start.date.time",
                "\u6191\u8B49\u6709\u6548\u6027\u958B\u59CB\u65E5\u671F/\u6642\u9593"}, //-startdate
        {"keystore.password",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC"}, //-storepass
        {"keystore.type",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B"}, //-storetype
        {"trust.certificates.from.cacerts",
                "\u4F86\u81EA cacerts \u7684\u4FE1\u4EFB\u6191\u8B49"}, //-trustcacerts
        {"verbose.output",
                "\u8A73\u7D30\u8CC7\u8A0A\u8F38\u51FA"}, //-v
        {"validity.number.of.days",
                "\u6709\u6548\u6027\u65E5\u6578"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "\u8981\u64A4\u92B7\u6191\u8B49\u7684\u5E8F\u5217 ID"}, //-id
        // keytool: Running part
        {"keytool.error.", "\u91D1\u9470\u5DE5\u5177\u932F\u8AA4: "},
        {"Illegal.option.", "\u7121\u6548\u7684\u9078\u9805:"},
        {"Illegal.value.", "\u7121\u6548\u503C: "},
        {"Unknown.password.type.", "\u4E0D\u660E\u7684\u5BC6\u78BC\u985E\u578B: "},
        {"Cannot.find.environment.variable.",
                "\u627E\u4E0D\u5230\u74B0\u5883\u8B8A\u6578: "},
        {"Cannot.find.file.", "\u627E\u4E0D\u5230\u6A94\u6848: "},
        {"Command.option.flag.needs.an.argument.", "\u547D\u4EE4\u9078\u9805 {0} \u9700\u8981\u5F15\u6578\u3002"},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "\u8B66\u544A: PKCS12 \u91D1\u9470\u5132\u5B58\u5EAB\u4E0D\u652F\u63F4\u4E0D\u540C\u7684\u5132\u5B58\u5EAB\u548C\u91D1\u9470\u5BC6\u78BC\u3002\u5FFD\u7565\u4F7F\u7528\u8005\u6307\u5B9A\u7684 {0} \u503C\u3002"},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u70BA {0}\uFF0C\u5247 -keystore \u5FC5\u9808\u70BA NONE"},
        {"Too.many.retries.program.terminated",
                 "\u91CD\u8A66\u6B21\u6578\u592A\u591A\uFF0C\u7A0B\u5F0F\u5DF2\u7D42\u6B62"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u70BA {0}\uFF0C\u5247\u4E0D\u652F\u63F4 -storepasswd \u548C -keypasswd \u547D\u4EE4"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "\u5982\u679C -storetype \u70BA PKCS12\uFF0C\u5247\u4E0D\u652F\u63F4 -keypasswd \u547D\u4EE4"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u70BA {0}\uFF0C\u5247\u4E0D\u80FD\u6307\u5B9A -keypass \u548C -new"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "\u5982\u679C\u6307\u5B9A -protected\uFF0C\u5247\u4E0D\u80FD\u6307\u5B9A -storepass\u3001-keypass \u548C -new"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "\u5982\u679C\u6307\u5B9A -srcprotected\uFF0C\u5247\u4E0D\u80FD\u6307\u5B9A -srcstorepass \u548C -srckeypass"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "\u5982\u679C\u91D1\u9470\u5132\u5B58\u5EAB\u4E0D\u53D7\u5BC6\u78BC\u4FDD\u8B77\uFF0C\u5247\u4E0D\u80FD\u6307\u5B9A -storepass\u3001-keypass \u548C -new"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "\u5982\u679C\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u4E0D\u53D7\u5BC6\u78BC\u4FDD\u8B77\uFF0C\u5247\u4E0D\u80FD\u6307\u5B9A -srcstorepass \u548C -srckeypass"},
        {"Illegal.startdate.value", "\u7121\u6548\u7684 startdate \u503C"},
        {"Validity.must.be.greater.than.zero",
                "\u6709\u6548\u6027\u5FC5\u9808\u5927\u65BC\u96F6"},
        {"provName.not.a.provider", "{0} \u4E0D\u662F\u4E00\u500B\u63D0\u4F9B\u8005"},
        {"Usage.error.no.command.provided", "\u7528\u6CD5\u932F\u8AA4: \u672A\u63D0\u4F9B\u547D\u4EE4"},
        {"Source.keystore.file.exists.but.is.empty.", "\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u6A94\u6848\u5B58\u5728\uFF0C\u4F46\u70BA\u7A7A: "},
        {"Please.specify.srckeystore", "\u8ACB\u6307\u5B9A -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                " 'list' \u547D\u4EE4\u4E0D\u80FD\u540C\u6642\u6307\u5B9A -v \u53CA -rfc"},
        {"Key.password.must.be.at.least.6.characters",
                "\u91D1\u9470\u5BC6\u78BC\u5FC5\u9808\u81F3\u5C11\u70BA 6 \u500B\u5B57\u5143"},
        {"New.password.must.be.at.least.6.characters",
                "\u65B0\u7684\u5BC6\u78BC\u5FC5\u9808\u81F3\u5C11\u70BA 6 \u500B\u5B57\u5143"},
        {"Keystore.file.exists.but.is.empty.",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u6A94\u6848\u5B58\u5728\uFF0C\u4F46\u70BA\u7A7A\u767D: "},
        {"Keystore.file.does.not.exist.",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u6A94\u6848\u4E0D\u5B58\u5728: "},
        {"Must.specify.destination.alias", "\u5FC5\u9808\u6307\u5B9A\u76EE\u7684\u5730\u5225\u540D"},
        {"Must.specify.alias", "\u5FC5\u9808\u6307\u5B9A\u5225\u540D"},
        {"Keystore.password.must.be.at.least.6.characters",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC\u5FC5\u9808\u81F3\u5C11\u70BA 6 \u500B\u5B57\u5143"},
        {"Enter.keystore.password.", "\u8F38\u5165\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC:  "},
        {"Enter.source.keystore.password.", "\u8ACB\u8F38\u5165\u4F86\u6E90\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC: "},
        {"Enter.destination.keystore.password.", "\u8ACB\u8F38\u5165\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC: "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC\u592A\u77ED - \u5FC5\u9808\u81F3\u5C11\u70BA 6 \u500B\u5B57\u5143"},
        {"Unknown.Entry.Type", "\u4E0D\u660E\u7684\u9805\u76EE\u985E\u578B"},
        {"Too.many.failures.Alias.not.changed", "\u592A\u591A\u932F\u8AA4\u3002\u672A\u8B8A\u66F4\u5225\u540D"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "\u5DF2\u6210\u529F\u532F\u5165\u5225\u540D {0} \u7684\u9805\u76EE\u3002"},
        {"Entry.for.alias.alias.not.imported.", "\u672A\u532F\u5165\u5225\u540D {0} \u7684\u9805\u76EE\u3002"},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "\u532F\u5165\u5225\u540D {0} \u7684\u9805\u76EE\u6642\u51FA\u73FE\u554F\u984C: {1}\u3002\n\u672A\u532F\u5165\u5225\u540D {0} \u7684\u9805\u76EE\u3002"},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "\u5DF2\u5B8C\u6210\u532F\u5165\u547D\u4EE4: \u6210\u529F\u532F\u5165 {0} \u500B\u9805\u76EE\uFF0C{1} \u500B\u9805\u76EE\u5931\u6557\u6216\u5DF2\u53D6\u6D88"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "\u8B66\u544A: \u6B63\u5728\u8986\u5BEB\u76EE\u7684\u5730\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D\u7684\u73FE\u6709\u5225\u540D {0}"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "\u73FE\u6709\u9805\u76EE\u5225\u540D {0} \u5B58\u5728\uFF0C\u662F\u5426\u8986\u5BEB\uFF1F[\u5426]:  "},
        {"Too.many.failures.try.later", "\u592A\u591A\u932F\u8AA4 - \u8ACB\u7A0D\u5F8C\u518D\u8A66"},
        {"Certification.request.stored.in.file.filename.",
                "\u8A8D\u8B49\u8981\u6C42\u5132\u5B58\u5728\u6A94\u6848 <{0}>"},
        {"Submit.this.to.your.CA", "\u5C07\u6B64\u9001\u51FA\u81F3\u60A8\u7684 CA"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "\u5982\u679C\u672A\u6307\u5B9A\u5225\u540D\uFF0C\u5247\u4E0D\u80FD\u6307\u5B9A destalias\u3001srckeypass \u53CA destkeypass"},
        {"Certificate.stored.in.file.filename.",
                "\u6191\u8B49\u5132\u5B58\u5728\u6A94\u6848 <{0}>"},
        {"Certificate.reply.was.installed.in.keystore",
                "\u6191\u8B49\u56DE\u8986\u5DF2\u5B89\u88DD\u5728\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "\u6191\u8B49\u56DE\u8986\u672A\u5B89\u88DD\u5728\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D"},
        {"Certificate.was.added.to.keystore",
                "\u6191\u8B49\u5DF2\u65B0\u589E\u81F3\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D"},
        {"Certificate.was.not.added.to.keystore",
                "\u6191\u8B49\u672A\u65B0\u589E\u81F3\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D"},
        {".Storing.ksfname.", "[\u5132\u5B58 {0}]"},
        {"alias.has.no.public.key.certificate.",
                "{0} \u6C92\u6709\u516C\u958B\u91D1\u9470 (\u6191\u8B49)"},
        {"Cannot.derive.signature.algorithm",
                "\u7121\u6CD5\u53D6\u5F97\u7C3D\u7AE0\u6F14\u7B97\u6CD5"},
        {"Alias.alias.does.not.exist",
                "\u5225\u540D <{0}> \u4E0D\u5B58\u5728"},
        {"Alias.alias.has.no.certificate",
                "\u5225\u540D <{0}> \u6C92\u6709\u6191\u8B49"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "\u6C92\u6709\u5EFA\u7ACB\u91D1\u9470\u7D44\uFF0C\u5225\u540D <{0}> \u5DF2\u7D93\u5B58\u5728"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "\u91DD\u5C0D {4} \u7522\u751F\u6709\u6548\u671F {3} \u5929\u7684 {0} \u4F4D\u5143 {1} \u91D1\u9470\u7D44\u4EE5\u53CA\u81EA\u6211\u7C3D\u7F72\u6191\u8B49 ({2})\n\t"},
        {"Enter.key.password.for.alias.", "\u8F38\u5165 <{0}> \u7684\u91D1\u9470\u5BC6\u78BC"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(RETURN \u5982\u679C\u548C\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC\u76F8\u540C):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "\u91D1\u9470\u5BC6\u78BC\u592A\u77ED - \u5FC5\u9808\u81F3\u5C11\u70BA 6 \u500B\u5B57\u5143"},
        {"Too.many.failures.key.not.added.to.keystore",
                "\u592A\u591A\u932F\u8AA4 - \u91D1\u9470\u672A\u65B0\u589E\u81F3\u91D1\u9470\u5132\u5B58\u5EAB"},
        {"Destination.alias.dest.already.exists",
                "\u76EE\u7684\u5730\u5225\u540D <{0}> \u5DF2\u7D93\u5B58\u5728"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "\u5BC6\u78BC\u592A\u77ED - \u5FC5\u9808\u81F3\u5C11\u70BA 6 \u500B\u5B57\u5143"},
        {"Too.many.failures.Key.entry.not.cloned",
                "\u592A\u591A\u932F\u8AA4\u3002\u672A\u8907\u88FD\u91D1\u9470\u9805\u76EE"},
        {"key.password.for.alias.", "<{0}> \u7684\u91D1\u9470\u5BC6\u78BC"},
        {"Keystore.entry.for.id.getName.already.exists",
                "<{0}> \u7684\u91D1\u9470\u5132\u5B58\u5EAB\u9805\u76EE\u5DF2\u7D93\u5B58\u5728"},
        {"Creating.keystore.entry.for.id.getName.",
                "\u5EFA\u7ACB <{0}> \u7684\u91D1\u9470\u5132\u5B58\u5EAB\u9805\u76EE..."},
        {"No.entries.from.identity.database.added",
                "\u6C92\u6709\u65B0\u589E\u4F86\u81EA\u8B58\u5225\u8CC7\u6599\u5EAB\u7684\u9805\u76EE"},
        {"Alias.name.alias", "\u5225\u540D\u540D\u7A31: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "\u5EFA\u7ACB\u65E5\u671F: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "\u9805\u76EE\u985E\u578B: {0}"},
        {"Certificate.chain.length.", "\u6191\u8B49\u93C8\u9577\u5EA6: "},
        {"Certificate.i.1.", "\u6191\u8B49 [{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "\u6191\u8B49\u6307\u7D0B (SHA1): "},
        {"Entry.type.trustedCertEntry.", "\u8F38\u5165\u985E\u578B: trustedCertEntry\n"},
        {"trustedCertEntry.", "trustedCertEntry,"},
        {"Keystore.type.", "\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B: "},
        {"Keystore.provider.", "\u91D1\u9470\u5132\u5B58\u5EAB\u63D0\u4F9B\u8005: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "\u60A8\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u5305\u542B {0,number,integer} \u9805\u76EE"},
        {"Your.keystore.contains.keyStore.size.entries",
                "\u60A8\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u5305\u542B {0,number,integer} \u9805\u76EE"},
        {"Failed.to.parse.input", "\u7121\u6CD5\u5256\u6790\u8F38\u5165"},
        {"Empty.input", "\u7A7A\u8F38\u5165"},
        {"Not.X.509.certificate", "\u975E X.509 \u6191\u8B49"},
        {"alias.has.no.public.key", "{0} \u7121\u516C\u958B\u91D1\u9470"},
        {"alias.has.no.X.509.certificate", "{0} \u7121 X.509 \u6191\u8B49"},
        {"New.certificate.self.signed.", "\u65B0\u6191\u8B49 (\u81EA\u6211\u7C3D\u7F72): "},
        {"Reply.has.no.certificates", "\u56DE\u8986\u4E0D\u542B\u6191\u8B49"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "\u6191\u8B49\u672A\u8F38\u5165\uFF0C\u5225\u540D <{0}> \u5DF2\u7D93\u5B58\u5728"},
        {"Input.not.an.X.509.certificate", "\u8F38\u5165\u7684\u4E0D\u662F X.509 \u6191\u8B49"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D\u7684 <{0}> \u5225\u540D\u4E4B\u4E0B\uFF0C\u6191\u8B49\u5DF2\u7D93\u5B58\u5728"},
        {"Do.you.still.want.to.add.it.no.",
                "\u60A8\u4ECD\u7136\u60F3\u8981\u5C07\u4E4B\u65B0\u589E\u55CE\uFF1F [\u5426]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "\u6574\u500B\u7CFB\u7D71 CA \u91D1\u9470\u5132\u5B58\u5EAB\u4E2D\u7684 <{0}> \u5225\u540D\u4E4B\u4E0B\uFF0C\u6191\u8B49\u5DF2\u7D93\u5B58\u5728"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "\u60A8\u4ECD\u7136\u60F3\u8981\u5C07\u4E4B\u65B0\u589E\u81F3\u81EA\u5DF1\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u55CE\uFF1F [\u5426]:  "},
        {"Trust.this.certificate.no.", "\u4FE1\u4EFB\u9019\u500B\u6191\u8B49\uFF1F [\u5426]:  "},
        {"YES", "\u662F"},
        {"New.prompt.", "\u65B0 {0}: "},
        {"Passwords.must.differ", "\u5FC5\u9808\u662F\u4E0D\u540C\u7684\u5BC6\u78BC"},
        {"Re.enter.new.prompt.", "\u91CD\u65B0\u8F38\u5165\u65B0 {0}: "},
        {"Re.enter.new.password.", "\u91CD\u65B0\u8F38\u5165\u65B0\u5BC6\u78BC: "},
        {"They.don.t.match.Try.again", "\u5B83\u5011\u4E0D\u76F8\u7B26\u3002\u8ACB\u91CD\u8A66"},
        {"Enter.prompt.alias.name.", "\u8F38\u5165 {0} \u5225\u540D\u540D\u7A31:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "\u8ACB\u8F38\u5165\u65B0\u7684\u5225\u540D\u540D\u7A31\t(RETURN \u4EE5\u53D6\u6D88\u532F\u5165\u6B64\u9805\u76EE):"},
        {"Enter.alias.name.", "\u8F38\u5165\u5225\u540D\u540D\u7A31:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(RETURN \u5982\u679C\u548C <{0}> \u7684\u76F8\u540C)"},
        {".PATTERN.printX509Cert",
                "\u64C1\u6709\u8005: {0}\n\u767C\u51FA\u8005: {1}\n\u5E8F\u865F: {2}\n\u6709\u6548\u671F\u81EA: {3} \u5230: {4}\n\u6191\u8B49\u6307\u7D0B:\n\t MD5:  {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t \u7C3D\u7AE0\u6F14\u7B97\u6CD5\u540D\u7A31: {8}\n\t \u7248\u672C: {9}"},
        {"What.is.your.first.and.last.name.",
                "\u60A8\u7684\u540D\u5B57\u8207\u59D3\u6C0F\u70BA\u4F55\uFF1F"},
        {"What.is.the.name.of.your.organizational.unit.",
                "\u60A8\u7684\u7D44\u7E54\u55AE\u4F4D\u540D\u7A31\u70BA\u4F55\uFF1F"},
        {"What.is.the.name.of.your.organization.",
                "\u60A8\u7684\u7D44\u7E54\u540D\u7A31\u70BA\u4F55\uFF1F"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "\u60A8\u6240\u5728\u7684\u57CE\u5E02\u6216\u5730\u5340\u540D\u7A31\u70BA\u4F55\uFF1F"},
        {"What.is.the.name.of.your.State.or.Province.",
                "\u60A8\u6240\u5728\u7684\u5DDE\u53CA\u7701\u4EFD\u540D\u7A31\u70BA\u4F55\uFF1F"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "\u6B64\u55AE\u4F4D\u7684\u5169\u500B\u5B57\u6BCD\u570B\u5225\u4EE3\u78BC\u70BA\u4F55\uFF1F"},
        {"Is.name.correct.", "{0} \u6B63\u78BA\u55CE\uFF1F"},
        {"no", "\u5426"},
        {"yes", "\u662F"},
        {"y", "y"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "\u5225\u540D <{0}> \u6C92\u6709\u91D1\u9470"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "\u5225\u540D <{0}> \u6240\u53C3\u7167\u7684\u9805\u76EE\u4E0D\u662F\u79C1\u5BC6\u91D1\u9470\u985E\u578B\u3002-keyclone \u547D\u4EE4\u50C5\u652F\u63F4\u79C1\u5BC6\u91D1\u9470\u9805\u76EE\u7684\u8907\u88FD"},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "\u7C3D\u7F72\u8005 #%d:"},
        {"Timestamp.", "\u6642\u6233:"},
        {"Signature.", "\u7C3D\u7AE0:"},
        {"CRLs.", "CRL:"},
        {"Certificate.owner.", "\u6191\u8B49\u64C1\u6709\u8005: "},
        {"Not.a.signed.jar.file", "\u4E0D\u662F\u7C3D\u7F72\u7684 jar \u6A94\u6848"},
        {"No.certificate.from.the.SSL.server",
                "\u6C92\u6709\u4F86\u81EA SSL \u4F3A\u670D\u5668\u7684\u6191\u8B49"},

        // Translators of the following 5 pairs, ATTENTION:
        // the next 5 string pairs are meant to be combined into 2 paragraphs,
        // 1+3+4 and 2+3+5. make sure your translation also does.
        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* \u8CC7\u8A0A\u7684\u5B8C\u6574\u6027\u5DF2\u5132\u5B58\u5728\u60A8\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* \u8CC7\u8A0A\u7684\u5B8C\u6574\u6027\u5DF2\u5132\u5B58\u5728 srckeystore \u4E2D *"},
        {".has.NOT.been.verified.In.order.to.verify.its.integrity.",
            "* \u5C1A\u672A\u88AB\u9A57\u8B49\uFF01\u70BA\u4E86\u9A57\u8B49\u5176\u5B8C\u6574\u6027\uFF0C*"},
        {".you.must.provide.your.keystore.password.",
            "* \u60A8\u5FC5\u9808\u63D0\u4F9B\u60A8\u91D1\u9470\u5132\u5B58\u5EAB\u7684\u5BC6\u78BC\u3002                 *"},
        {".you.must.provide.the.srckeystore.password.",
            "* \u60A8\u5FC5\u9808\u63D0\u4F9B srckeystore \u5BC6\u78BC\u3002               *"},


        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "\u6191\u8B49\u56DE\u8986\u4E26\u672A\u5305\u542B <{0}> \u7684\u516C\u958B\u91D1\u9470"},
        {"Incomplete.certificate.chain.in.reply",
                "\u56DE\u8986\u6642\u7684\u6191\u8B49\u93C8\u4E0D\u5B8C\u6574"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "\u56DE\u8986\u6642\u7684\u6191\u8B49\u93C8\u672A\u9A57\u8B49: "},
        {"Top.level.certificate.in.reply.",
                "\u56DE\u8986\u6642\u7684\u6700\u9AD8\u7D1A\u6191\u8B49:\\n"},
        {".is.not.trusted.", "... \u662F\u4E0D\u88AB\u4FE1\u4EFB\u7684\u3002"},
        {"Install.reply.anyway.no.", "\u9084\u662F\u8981\u5B89\u88DD\u56DE\u8986\uFF1F [\u5426]:  "},
        {"NO", "\u5426"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "\u56DE\u8986\u6642\u7684\u516C\u958B\u91D1\u9470\u8207\u91D1\u9470\u5132\u5B58\u5EAB\u4E0D\u7B26"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "\u6191\u8B49\u56DE\u8986\u8207\u91D1\u9470\u5132\u5B58\u5EAB\u4E2D\u7684\u6191\u8B49\u662F\u76F8\u540C\u7684"},
        {"Failed.to.establish.chain.from.reply",
                "\u7121\u6CD5\u5F9E\u56DE\u8986\u4E2D\u5C07\u93C8\u5EFA\u7ACB\u8D77\u4F86"},
        {"n", "n"},
        {"Wrong.answer.try.again", "\u932F\u8AA4\u7684\u7B54\u6848\uFF0C\u8ACB\u518D\u8A66\u4E00\u6B21"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "\u672A\u7522\u751F\u79D8\u5BC6\u91D1\u9470\uFF0C\u5225\u540D <{0}> \u5DF2\u5B58\u5728"},
        {"Please.provide.keysize.for.secret.key.generation",
                "\u8ACB\u63D0\u4F9B -keysize \u4EE5\u7522\u751F\u79D8\u5BC6\u91D1\u9470"},

        {"Extensions.", "\u64F4\u5145\u5957\u4EF6: "},
        {".Empty.value.", "(\u7A7A\u767D\u503C)"},
        {"Extension.Request.", "\u64F4\u5145\u5957\u4EF6\u8981\u6C42:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "PKCS #10 \u6191\u8B49\u8981\u6C42 (\u7248\u672C 1.0)\n\u4E3B\u9AD4: %s\n\u516C\u7528\u91D1\u9470: %s \u683C\u5F0F %s \u91D1\u9470\n"},
        {"Unknown.keyUsage.type.", "\u4E0D\u660E\u7684 keyUsage \u985E\u578B: "},
        {"Unknown.extendedkeyUsage.type.", "\u4E0D\u660E\u7684 extendedkeyUsage \u985E\u578B: "},
        {"Unknown.AccessDescription.type.", "\u4E0D\u660E\u7684 AccessDescription \u985E\u578B: "},
        {"Unrecognized.GeneralName.type.", "\u7121\u6CD5\u8FA8\u8B58\u7684 GeneralName \u985E\u578B: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "\u6B64\u64F4\u5145\u5957\u4EF6\u7121\u6CD5\u6A19\u793A\u70BA\u95DC\u9375\u3002"},
        {"Odd.number.of.hex.digits.found.", "\u627E\u5230\u5341\u516D\u9032\u4F4D\u6578\u5B57\u7684\u5947\u6578: "},
        {"Unknown.extension.type.", "\u4E0D\u660E\u7684\u64F4\u5145\u5957\u4EF6\u985E\u578B: "},
        {"command.{0}.is.ambiguous.", "\u547D\u4EE4 {0} \u4E0D\u660E\u78BA:"},

        // policytool
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "\u8B66\u544A: \u5225\u540D {0} \u7684\u516C\u958B\u91D1\u9470\u4E0D\u5B58\u5728\u3002\u8ACB\u78BA\u5B9A\u91D1\u9470\u5132\u5B58\u5EAB\u914D\u7F6E\u6B63\u78BA\u3002"},
        {"Warning.Class.not.found.class", "\u8B66\u544A: \u627E\u4E0D\u5230\u985E\u5225 {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "\u8B66\u544A: \u7121\u6548\u7684\u5EFA\u69CB\u5B50\u5F15\u6578: {0}"},
        {"Illegal.Principal.Type.type", "\u7121\u6548\u7684 Principal \u985E\u578B: {0}"},
        {"Illegal.option.option", "\u7121\u6548\u7684\u9078\u9805: {0}"},
        {"Usage.policytool.options.", "\u7528\u6CD5: policytool [options]"},
        {".file.file.policy.file.location",
                "  [-file <file>]    \u539F\u5247\u6A94\u6848\u4F4D\u7F6E"},
        {"New", "\u65B0\u589E"},
        {"Open", "\u958B\u555F"},
        {"Save", "\u5132\u5B58"},
        {"Save.As", "\u53E6\u5B58\u65B0\u6A94"},
        {"View.Warning.Log", "\u6AA2\u8996\u8B66\u544A\u8A18\u9304"},
        {"Exit", "\u7D50\u675F"},
        {"Add.Policy.Entry", "\u65B0\u589E\u539F\u5247\u9805\u76EE"},
        {"Edit.Policy.Entry", "\u7DE8\u8F2F\u539F\u5247\u9805\u76EE"},
        {"Remove.Policy.Entry", "\u79FB\u9664\u539F\u5247\u9805\u76EE"},
        {"Edit", "\u7DE8\u8F2F"},
        {"Retain", "\u4FDD\u7559"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "\u8B66\u544A: \u6A94\u6848\u540D\u7A31\u5305\u542B\u9041\u96E2\u53CD\u659C\u7DDA\u5B57\u5143\u3002\u4E0D\u9700\u8981\u9041\u96E2\u53CD\u659C\u7DDA\u5B57\u5143 (\u64B0\u5BEB\u539F\u5247\u5167\u5BB9\u81F3\u6C38\u4E45\u5B58\u653E\u5340\u6642\u9700\u8981\u5DE5\u5177\u9041\u96E2\u5B57\u5143)\u3002\n\n\u6309\u4E00\u4E0B\u300C\u4FDD\u7559\u300D\u4EE5\u4FDD\u7559\u8F38\u5165\u7684\u540D\u7A31\uFF0C\u6216\u6309\u4E00\u4E0B\u300C\u7DE8\u8F2F\u300D\u4EE5\u7DE8\u8F2F\u540D\u7A31\u3002"},

        {"Add.Public.Key.Alias", "\u65B0\u589E\u516C\u958B\u91D1\u9470\u5225\u540D"},
        {"Remove.Public.Key.Alias", "\u79FB\u9664\u516C\u958B\u91D1\u9470\u5225\u540D"},
        {"File", "\u6A94\u6848"},
        {"KeyStore", "\u91D1\u9470\u5132\u5B58\u5EAB"},
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
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "\u65B0\u589E Principal"},
        {"Edit.Principal", "\u7DE8\u8F2F Principal"},
        {"Remove.Principal", "\u79FB\u9664 Principal"},
        {"Principals.", "Principal:"},
        {".Add.Permission", "  \u65B0\u589E\u6B0A\u9650"},
        {".Edit.Permission", "  \u7DE8\u8F2F\u6B0A\u9650"},
        {"Remove.Permission", "\u79FB\u9664\u6B0A\u9650"},
        {"Done", "\u5B8C\u6210"},
        {"KeyStore.URL.", "\u91D1\u9470\u5132\u5B58\u5EAB URL: "},
        {"KeyStore.Type.", "\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B:"},
        {"KeyStore.Provider.", "\u91D1\u9470\u5132\u5B58\u5EAB\u63D0\u4F9B\u8005:"},
        {"KeyStore.Password.URL.", "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC URL: "},
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
        {"Yes", "\u662F"},
        {"No", "\u5426"},
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
        {"Principal.List", "Principal \u6E05\u55AE"},
        {"Permission.List", "\u6B0A\u9650\u6E05\u55AE"},
        {"Code.Base", "\u4EE3\u78BC\u57FA\u6E96"},
        {"KeyStore.U.R.L.", "\u91D1\u9470\u5132\u5B58\u5EAB URL:"},
        {"KeyStore.Password.U.R.L.", "\u91D1\u9470\u5132\u5B58\u5EAB\u5BC6\u78BC URL:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "\u7121\u6548\u7A7A\u503C\u8F38\u5165"},
        {"actions.can.only.be.read.", "\u52D5\u4F5C\u53EA\u80FD\u88AB\u300C\u8B80\u53D6\u300D"},
        {"permission.name.name.syntax.invalid.",
                "\u6B0A\u9650\u540D\u7A31 [{0}] \u662F\u7121\u6548\u7684\u8A9E\u6CD5: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "Credential \u985E\u5225\u5F8C\u9762\u4E0D\u662F Principal \u985E\u5225\u53CA\u540D\u7A31"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "Principal \u985E\u5225\u5F8C\u9762\u4E0D\u662F Principal \u540D\u7A31"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "Principal \u540D\u7A31\u5FC5\u9808\u4EE5\u5F15\u865F\u5708\u4F4F"},
        {"Principal.Name.missing.end.quote",
                "Principal \u540D\u7A31\u7F3A\u5C11\u4E0B\u5F15\u865F"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "\u5982\u679C Principal \u540D\u7A31\u4E0D\u662F\u4E00\u500B\u842C\u7528\u5B57\u5143 (*) \u503C\uFF0C\u90A3\u9EBC PrivateCredentialPermission Principal \u985E\u5225\u5C31\u4E0D\u80FD\u662F\u842C\u7528\u5B57\u5143 (*) \u503C"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\tPrincipal \u985E\u5225 = {0}\n\tPrincipal \u540D\u7A31 = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "\u63D0\u4F9B\u7A7A\u503C\u540D\u7A31"},
        {"provided.null.keyword.map", "\u63D0\u4F9B\u7A7A\u503C\u95DC\u9375\u5B57\u5C0D\u6620"},
        {"provided.null.OID.map", "\u63D0\u4F9B\u7A7A\u503C OID \u5C0D\u6620"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "\u63D0\u4F9B\u7121\u6548\u7684\u7A7A\u503C AccessControlContext"},
        {"invalid.null.action.provided", "\u63D0\u4F9B\u7121\u6548\u7684\u7A7A\u503C\u52D5\u4F5C"},
        {"invalid.null.Class.provided", "\u63D0\u4F9B\u7121\u6548\u7684\u7A7A\u503C\u985E\u5225"},
        {"Subject.", "\u4E3B\u984C:\n"},
        {".Principal.", "\tPrincipal: "},
        {".Public.Credential.", "\t\u516C\u7528\u8B49\u660E\u8CC7\u6599: "},
        {".Private.Credentials.inaccessible.",
                "\t\u79C1\u4EBA\u8B49\u660E\u8CC7\u6599\u7121\u6CD5\u5B58\u53D6\n"},
        {".Private.Credential.", "\t\u79C1\u4EBA\u8B49\u660E\u8CC7\u6599: "},
        {".Private.Credential.inaccessible.",
                "\t\u79C1\u4EBA\u8B49\u660E\u8CC7\u6599\u7121\u6CD5\u5B58\u53D6\n"},
        {"Subject.is.read.only", "\u4E3B\u984C\u70BA\u552F\u8B80"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "\u8A66\u5716\u65B0\u589E\u4E00\u500B\u975E java.security.Principal \u57F7\u884C\u8655\u7406\u7684\u7269\u4EF6\u81F3\u4E3B\u984C\u7684 Principal \u7FA4\u4E2D"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "\u8A66\u5716\u65B0\u589E\u4E00\u500B\u975E {0} \u57F7\u884C\u8655\u7406\u7684\u7269\u4EF6"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "\u7121\u6548\u7A7A\u503C\u8F38\u5165: \u540D\u7A31"},
        {"No.LoginModules.configured.for.name",
         "\u7121\u91DD\u5C0D {0} \u914D\u7F6E\u7684 LoginModules"},
        {"invalid.null.Subject.provided", "\u63D0\u4F9B\u7121\u6548\u7A7A\u503C\u4E3B\u984C"},
        {"invalid.null.CallbackHandler.provided",
                "\u63D0\u4F9B\u7121\u6548\u7A7A\u503C CallbackHandler"},
        {"null.subject.logout.called.before.login",
                "\u7A7A\u503C\u4E3B\u984C - \u5728\u767B\u5165\u4E4B\u524D\u5373\u547C\u53EB\u767B\u51FA"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "\u7121\u6CD5\u5275\u8A2D LoginModule\uFF0C{0}\uFF0C\u56E0\u70BA\u5B83\u4E26\u672A\u63D0\u4F9B\u975E\u5F15\u6578\u7684\u5EFA\u69CB\u5B50"},
        {"unable.to.instantiate.LoginModule",
                "\u7121\u6CD5\u5EFA\u7ACB LoginModule"},
        {"unable.to.instantiate.LoginModule.",
                "\u7121\u6CD5\u5EFA\u7ACB LoginModule: "},
        {"unable.to.find.LoginModule.class.",
                "\u627E\u4E0D\u5230 LoginModule \u985E\u5225: "},
        {"unable.to.access.LoginModule.",
                "\u7121\u6CD5\u5B58\u53D6 LoginModule: "},
        {"Login.Failure.all.modules.ignored",
                "\u767B\u5165\u5931\u6557: \u5FFD\u7565\u6240\u6709\u6A21\u7D44"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: \u5256\u6790\u932F\u8AA4 {0}: \n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: \u65B0\u589E\u6B0A\u9650\u932F\u8AA4 {0}: \n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: \u65B0\u589E\u9805\u76EE\u932F\u8AA4: \n\t{0}"},
        {"alias.name.not.provided.pe.name.", "\u672A\u63D0\u4F9B\u5225\u540D\u540D\u7A31 ({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "\u7121\u6CD5\u5C0D\u5225\u540D\u57F7\u884C\u66FF\u63DB\uFF0C{0}"},
        {"substitution.value.prefix.unsupported",
                "\u4E0D\u652F\u63F4\u7684\u66FF\u63DB\u503C\uFF0C{0}"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","\u8F38\u5165\u4E0D\u80FD\u70BA\u7A7A\u503C"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "\u6307\u5B9A keystorePasswordURL \u9700\u8981\u540C\u6642\u6307\u5B9A\u91D1\u9470\u5132\u5B58\u5EAB"},
        {"expected.keystore.type", "\u9810\u671F\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u985E\u578B"},
        {"expected.keystore.provider", "\u9810\u671F\u7684\u91D1\u9470\u5132\u5B58\u5EAB\u63D0\u4F9B\u8005"},
        {"multiple.Codebase.expressions",
                "\u591A\u91CD Codebase \u8868\u793A\u5F0F"},
        {"multiple.SignedBy.expressions","\u591A\u91CD SignedBy \u8868\u793A\u5F0F"},
        {"SignedBy.has.empty.alias","SignedBy \u6709\u7A7A\u5225\u540D"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "\u6C92\u6709\u842C\u7528\u5B57\u5143\u540D\u7A31\uFF0C\u7121\u6CD5\u6307\u5B9A\u542B\u6709\u842C\u7528\u5B57\u5143\u985E\u5225\u7684 Principal"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "\u9810\u671F\u7684 codeBase \u6216 SignedBy \u6216 Principal"},
        {"expected.permission.entry", "\u9810\u671F\u7684\u6B0A\u9650\u9805\u76EE"},
        {"number.", "\u865F\u78BC "},
        {"expected.expect.read.end.of.file.",
                "\u9810\u671F\u7684 [{0}], \u8B80\u53D6 [end of file]"},
        {"expected.read.end.of.file.",
                "\u9810\u671F\u7684 [;], \u8B80\u53D6 [end of file]"},
        {"line.number.msg", "\u884C {0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "\u884C {0}: \u9810\u671F\u7684 [{1}]\uFF0C\u767C\u73FE [{2}]"},
        {"null.principalClass.or.principalName",
                "\u7A7A\u503C principalClass \u6216 principalName"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "PKCS11 \u8A18\u865F [{0}] \u5BC6\u78BC: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "\u7121\u6CD5\u5EFA\u7ACB\u4E3B\u984C\u5F0F\u7684\u539F\u5247"}
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

