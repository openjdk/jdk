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
public class Resources_ko extends java.util.ListResourceBundle {

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
        {"Options.", "\uC635\uC158:"},
        {"Use.keytool.help.for.all.available.commands",
                 "\uC0AC\uC6A9 \uAC00\uB2A5\uD55C \uBAA8\uB4E0 \uBA85\uB839\uC5D0 \"keytool -help\" \uC0AC\uC6A9"},
        {"Key.and.Certificate.Management.Tool",
                 "\uD0A4 \uBC0F \uC778\uC99D\uC11C \uAD00\uB9AC \uD234"},
        {"Commands.", "\uBA85\uB839:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "command_name \uC0AC\uC6A9\uBC95\uC5D0 \"keytool -command_name -help\" \uC0AC\uC6A9"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "\uC778\uC99D\uC11C \uC694\uCCAD\uC744 \uC0DD\uC131\uD569\uB2C8\uB2E4."}, //-certreq
        {"Changes.an.entry.s.alias",
                "\uD56D\uBAA9\uC758 \uBCC4\uCE6D\uC744 \uBCC0\uACBD\uD569\uB2C8\uB2E4."}, //-changealias
        {"Deletes.an.entry",
                "\uD56D\uBAA9\uC744 \uC0AD\uC81C\uD569\uB2C8\uB2E4."}, //-delete
        {"Exports.certificate",
                "\uC778\uC99D\uC11C\uB97C \uC775\uC2A4\uD3EC\uD2B8\uD569\uB2C8\uB2E4."}, //-exportcert
        {"Generates.a.key.pair",
                "\uD0A4 \uC30D\uC744 \uC0DD\uC131\uD569\uB2C8\uB2E4."}, //-genkeypair
        {"Generates.a.secret.key",
                "\uBCF4\uC548 \uD0A4\uB97C \uC0DD\uC131\uD569\uB2C8\uB2E4."}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "\uC778\uC99D\uC11C \uC694\uCCAD\uC5D0\uC11C \uC778\uC99D\uC11C\uB97C \uC0DD\uC131\uD569\uB2C8\uB2E4."}, //-gencert
        {"Generates.CRL", "CRL\uC744 \uC0DD\uC131\uD569\uB2C8\uB2E4."}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "JDK 1.1.x \uC2A4\uD0C0\uC77C ID \uB370\uC774\uD130\uBCA0\uC774\uC2A4\uC5D0\uC11C \uD56D\uBAA9\uC744 \uC784\uD3EC\uD2B8\uD569\uB2C8\uB2E4."}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "\uC778\uC99D\uC11C \uB610\uB294 \uC778\uC99D\uC11C \uCCB4\uC778\uC744 \uC784\uD3EC\uD2B8\uD569\uB2C8\uB2E4."}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "\uB2E4\uB978 \uD0A4 \uC800\uC7A5\uC18C\uC5D0\uC11C \uD558\uB098 \uB610\uB294 \uBAA8\uB4E0 \uD56D\uBAA9\uC744 \uC784\uD3EC\uD2B8\uD569\uB2C8\uB2E4."}, //-importkeystore
        {"Clones.a.key.entry",
                "\uD0A4 \uD56D\uBAA9\uC744 \uBCF5\uC81C\uD569\uB2C8\uB2E4."}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "\uD56D\uBAA9\uC758 \uD0A4 \uBE44\uBC00\uBC88\uD638\uB97C \uBCC0\uACBD\uD569\uB2C8\uB2E4."}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "\uD0A4 \uC800\uC7A5\uC18C\uC758 \uD56D\uBAA9\uC744 \uB098\uC5F4\uD569\uB2C8\uB2E4."}, //-list
        {"Prints.the.content.of.a.certificate",
                "\uC778\uC99D\uC11C\uC758 \uCF58\uD150\uCE20\uB97C \uC778\uC1C4\uD569\uB2C8\uB2E4."}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "\uC778\uC99D\uC11C \uC694\uCCAD\uC758 \uCF58\uD150\uCE20\uB97C \uC778\uC1C4\uD569\uB2C8\uB2E4."}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "CRL \uD30C\uC77C\uC758 \uCF58\uD150\uCE20\uB97C \uC778\uC1C4\uD569\uB2C8\uB2E4."}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "\uC790\uCCB4 \uC11C\uBA85\uB41C \uC778\uC99D\uC11C\uB97C \uC0DD\uC131\uD569\uB2C8\uB2E4."}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "\uD0A4 \uC800\uC7A5\uC18C\uC758 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uB97C \uBCC0\uACBD\uD569\uB2C8\uB2E4."}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "\uCC98\uB9AC\uD560 \uD56D\uBAA9\uC758 \uBCC4\uCE6D \uC774\uB984"}, //-alias
        {"destination.alias",
                "\uB300\uC0C1 \uBCC4\uCE6D"}, //-destalias
        {"destination.key.password",
                "\uB300\uC0C1 \uD0A4 \uBE44\uBC00\uBC88\uD638"}, //-destkeypass
        {"destination.keystore.name",
                "\uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C \uC774\uB984"}, //-destkeystore
        {"destination.keystore.password.protected",
                "\uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uB85C \uBCF4\uD638\uB428"}, //-destprotected
        {"destination.keystore.provider.name",
                "\uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C \uC81C\uACF5\uC790 \uC774\uB984"}, //-destprovidername
        {"destination.keystore.password",
                "\uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638"}, //-deststorepass
        {"destination.keystore.type",
                "\uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C \uC720\uD615"}, //-deststoretype
        {"distinguished.name",
                "\uC2DD\uBCC4 \uC774\uB984"}, //-dname
        {"X.509.extension",
                "X.509 \uD655\uC7A5"}, //-ext
        {"output.file.name",
                "\uCD9C\uB825 \uD30C\uC77C \uC774\uB984"}, //-file and -outfile
        {"input.file.name",
                "\uC785\uB825 \uD30C\uC77C \uC774\uB984"}, //-file and -infile
        {"key.algorithm.name",
                "\uD0A4 \uC54C\uACE0\uB9AC\uC998 \uC774\uB984"}, //-keyalg
        {"key.password",
                "\uD0A4 \uBE44\uBC00\uBC88\uD638"}, //-keypass
        {"key.bit.size",
                "\uD0A4 \uBE44\uD2B8 \uD06C\uAE30"}, //-keysize
        {"keystore.name",
                "\uD0A4 \uC800\uC7A5\uC18C \uC774\uB984"}, //-keystore
        {"new.password",
                "\uC0C8 \uBE44\uBC00\uBC88\uD638"}, //-new
        {"do.not.prompt",
                "\uD655\uC778\uD558\uC9C0 \uC54A\uC74C"}, //-noprompt
        {"password.through.protected.mechanism",
                "\uBCF4\uD638\uB418\uB294 \uBA54\uCEE4\uB2C8\uC998\uC744 \uD1B5\uD55C \uBE44\uBC00\uBC88\uD638"}, //-protected
        {"provider.argument",
                "\uC81C\uACF5\uC790 \uC778\uC218"}, //-providerarg
        {"provider.class.name",
                "\uC81C\uACF5\uC790 \uD074\uB798\uC2A4 \uC774\uB984"}, //-providerclass
        {"provider.name",
                "\uC81C\uACF5\uC790 \uC774\uB984"}, //-providername
        {"provider.classpath",
                "\uC81C\uACF5\uC790 \uD074\uB798\uC2A4 \uACBD\uB85C"}, //-providerpath
        {"output.in.RFC.style",
                "RFC \uC2A4\uD0C0\uC77C\uC758 \uCD9C\uB825"}, //-rfc
        {"signature.algorithm.name",
                "\uC11C\uBA85 \uC54C\uACE0\uB9AC\uC998 \uC774\uB984"}, //-sigalg
        {"source.alias",
                "\uC18C\uC2A4 \uBCC4\uCE6D"}, //-srcalias
        {"source.key.password",
                "\uC18C\uC2A4 \uD0A4 \uBE44\uBC00\uBC88\uD638"}, //-srckeypass
        {"source.keystore.name",
                "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uC774\uB984"}, //-srckeystore
        {"source.keystore.password.protected",
                "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uB85C \uBCF4\uD638\uB428"}, //-srcprotected
        {"source.keystore.provider.name",
                "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uC81C\uACF5\uC790 \uC774\uB984"}, //-srcprovidername
        {"source.keystore.password",
                "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638"}, //-srcstorepass
        {"source.keystore.type",
                "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uC720\uD615"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "SSL \uC11C\uBC84 \uD638\uC2A4\uD2B8 \uBC0F \uD3EC\uD2B8"}, //-sslserver
        {"signed.jar.file",
                "\uC11C\uBA85\uB41C jar \uD30C\uC77C"}, //=jarfile
        {"certificate.validity.start.date.time",
                "\uC778\uC99D\uC11C \uC720\uD6A8 \uAE30\uAC04 \uC2DC\uC791 \uB0A0\uC9DC/\uC2DC\uAC04"}, //-startdate
        {"keystore.password",
                "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638"}, //-storepass
        {"keystore.type",
                "\uD0A4 \uC800\uC7A5\uC18C \uC720\uD615"}, //-storetype
        {"trust.certificates.from.cacerts",
                "cacerts\uC758 \uBCF4\uC548 \uC778\uC99D\uC11C"}, //-trustcacerts
        {"verbose.output",
                "\uC0C1\uC138 \uC815\uBCF4 \uCD9C\uB825"}, //-v
        {"validity.number.of.days",
                "\uC720\uD6A8 \uAE30\uAC04 \uC77C \uC218"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "\uCCA0\uD68C\uD560 \uC778\uC99D\uC11C\uC758 \uC77C\uB828 ID"}, //-id
        // keytool: Running part
        {"keytool.error.", "keytool \uC624\uB958: "},
        {"Illegal.option.", "\uC798\uBABB\uB41C \uC635\uC158:   "},
        {"Illegal.value.", "\uC798\uBABB\uB41C \uAC12: "},
        {"Unknown.password.type.", "\uC54C \uC218 \uC5C6\uB294 \uBE44\uBC00\uBC88\uD638 \uC720\uD615: "},
        {"Cannot.find.environment.variable.",
                "\uD658\uACBD \uBCC0\uC218\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC74C: "},
        {"Cannot.find.file.", "\uD30C\uC77C\uC744 \uCC3E\uC744 \uC218 \uC5C6\uC74C: "},
        {"Command.option.flag.needs.an.argument.", "\uBA85\uB839 \uC635\uC158 {0}\uC5D0 \uC778\uC218\uAC00 \uD544\uC694\uD569\uB2C8\uB2E4."},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "\uACBD\uACE0: \uB2E4\uB978 \uC800\uC7A5\uC18C \uBC0F \uD0A4 \uBE44\uBC00\uBC88\uD638\uB294 PKCS12 KeyStores\uC5D0 \uB300\uD574 \uC9C0\uC6D0\uB418\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4. \uC0AC\uC6A9\uC790\uAC00 \uC9C0\uC815\uD55C {0} \uAC12\uC744 \uBB34\uC2DC\uD558\uB294 \uC911\uC785\uB2C8\uB2E4."},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-storetype\uC774 {0}\uC778 \uACBD\uC6B0 -keystore\uB294 NONE\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Too.many.retries.program.terminated",
                 "\uC7AC\uC2DC\uB3C4 \uD69F\uC218\uAC00 \uB108\uBB34 \uB9CE\uC544 \uD504\uB85C\uADF8\uB7A8\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "-storetype\uC774 {0}\uC778 \uACBD\uC6B0 -storepasswd \uBC0F -keypasswd \uBA85\uB839\uC774 \uC9C0\uC6D0\uB418\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "-storetype\uC774 PKCS12\uC778 \uACBD\uC6B0 -keypasswd \uBA85\uB839\uC774 \uC9C0\uC6D0\uB418\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "-storetype\uC774 {0}\uC778 \uACBD\uC6B0 -keypass \uBC0F -new\uB97C \uC9C0\uC815\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "-protected\uB97C \uC9C0\uC815\uD55C \uACBD\uC6B0 -storepass, -keypass \uBC0F -new\uB97C \uC9C0\uC815\uD558\uC9C0 \uC54A\uC544\uC57C \uD569\uB2C8\uB2E4."},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "-srcprotected\uB97C \uC9C0\uC815\uD55C \uACBD\uC6B0 -srcstorepass \uBC0F -srckeypass\uB97C \uC9C0\uC815\uD558\uC9C0 \uC54A\uC544\uC57C \uD569\uB2C8\uB2E4."},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "\uD0A4 \uC800\uC7A5\uC18C\uAC00 \uBE44\uBC00\uBC88\uD638\uB85C \uBCF4\uD638\uB418\uC9C0 \uC54A\uB294 \uACBD\uC6B0 -storepass, -keypass \uBC0F -new\uB97C \uC9C0\uC815\uD558\uC9C0 \uC54A\uC544\uC57C \uD569\uB2C8\uB2E4."},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C\uAC00 \uBE44\uBC00\uBC88\uD638\uB85C \uBCF4\uD638\uB418\uC9C0 \uC54A\uB294 \uACBD\uC6B0 -srcstorepass \uBC0F -srckeypass\uB97C \uC9C0\uC815\uD558\uC9C0 \uC54A\uC544\uC57C \uD569\uB2C8\uB2E4."},
        {"Illegal.startdate.value", "startdate \uAC12\uC774 \uC798\uBABB\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Validity.must.be.greater.than.zero",
                "\uC720\uD6A8 \uAE30\uAC04\uC740 0\uBCF4\uB2E4 \uCEE4\uC57C \uD569\uB2C8\uB2E4."},
        {"provName.not.a.provider", "{0}\uC740(\uB294) \uC81C\uACF5\uC790\uAC00 \uC544\uB2D9\uB2C8\uB2E4."},
        {"Usage.error.no.command.provided", "\uC0AC\uC6A9\uBC95 \uC624\uB958: \uBA85\uB839\uC744 \uC785\uB825\uD558\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"Source.keystore.file.exists.but.is.empty.", "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uD30C\uC77C\uC774 \uC874\uC7AC\uD558\uC9C0\uB9CC \uBE44\uC5B4 \uC788\uC74C: "},
        {"Please.specify.srckeystore", "-srckeystore\uB97C \uC9C0\uC815\uD558\uC2ED\uC2DC\uC624."},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "'list' \uBA85\uB839\uC5D0 -v\uC640 -rfc\uB97C \uD568\uAED8 \uC9C0\uC815\uD558\uC9C0 \uC54A\uC544\uC57C \uD569\uB2C8\uB2E4."},
        {"Key.password.must.be.at.least.6.characters",
                "\uD0A4 \uBE44\uBC00\uBC88\uD638\uB294 6\uC790 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"New.password.must.be.at.least.6.characters",
                "\uC0C8 \uBE44\uBC00\uBC88\uD638\uB294 6\uC790 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Keystore.file.exists.but.is.empty.",
                "\uD0A4 \uC800\uC7A5\uC18C \uD30C\uC77C\uC774 \uC874\uC7AC\uD558\uC9C0\uB9CC \uBE44\uC5B4 \uC788\uC74C: "},
        {"Keystore.file.does.not.exist.",
                "\uD0A4 \uC800\uC7A5\uC18C \uD30C\uC77C\uC774 \uC874\uC7AC\uD558\uC9C0 \uC54A\uC74C: "},
        {"Must.specify.destination.alias", "\uB300\uC0C1 \uBCC4\uCE6D\uC744 \uC9C0\uC815\uD574\uC57C \uD569\uB2C8\uB2E4."},
        {"Must.specify.alias", "\uBCC4\uCE6D\uC744 \uC9C0\uC815\uD574\uC57C \uD569\uB2C8\uB2E4."},
        {"Keystore.password.must.be.at.least.6.characters",
                "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uB294 6\uC790 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Enter.keystore.password.", "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638 \uC785\uB825:  "},
        {"Enter.source.keystore.password.", "\uC18C\uC2A4 \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638 \uC785\uB825:  "},
        {"Enter.destination.keystore.password.", "\uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638 \uC785\uB825:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uAC00 \uB108\uBB34 \uC9E7\uC74C - 6\uC790 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Unknown.Entry.Type", "\uC54C \uC218 \uC5C6\uB294 \uD56D\uBAA9 \uC720\uD615"},
        {"Too.many.failures.Alias.not.changed", "\uC624\uB958\uAC00 \uB108\uBB34 \uB9CE\uC2B5\uB2C8\uB2E4. \uBCC4\uCE6D\uC774 \uBCC0\uACBD\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"Entry.for.alias.alias.successfully.imported.",
                 "{0} \uBCC4\uCE6D\uC5D0 \uB300\uD55C \uD56D\uBAA9\uC774 \uC131\uACF5\uC801\uC73C\uB85C \uC784\uD3EC\uD2B8\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Entry.for.alias.alias.not.imported.", "{0} \uBCC4\uCE6D\uC5D0 \uB300\uD55C \uD56D\uBAA9\uC774 \uC784\uD3EC\uD2B8\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "{0} \uBCC4\uCE6D\uC5D0 \uB300\uD55C \uD56D\uBAA9\uC744 \uC784\uD3EC\uD2B8\uD558\uB294 \uC911 \uBB38\uC81C \uBC1C\uC0DD: {1}.\n{0} \uBCC4\uCE6D\uC5D0 \uB300\uD55C \uD56D\uBAA9\uC774 \uC784\uD3EC\uD2B8\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "\uC784\uD3EC\uD2B8 \uBA85\uB839 \uC644\uB8CC: \uC131\uACF5\uC801\uC73C\uB85C \uC784\uD3EC\uD2B8\uB41C \uD56D\uBAA9\uC740 {0}\uAC1C, \uC2E4\uD328\uD558\uAC70\uB098 \uCDE8\uC18C\uB41C \uD56D\uBAA9\uC740 {1}\uAC1C\uC785\uB2C8\uB2E4."},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "\uACBD\uACE0: \uB300\uC0C1 \uD0A4 \uC800\uC7A5\uC18C\uC5D0\uC11C \uAE30\uC874 \uBCC4\uCE6D {0}\uC744(\uB97C) \uACB9\uCCD0 \uC4F0\uB294 \uC911"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "\uAE30\uC874 \uD56D\uBAA9 \uBCC4\uCE6D {0}\uC774(\uAC00) \uC874\uC7AC\uD569\uB2C8\uB2E4. \uACB9\uCCD0 \uC4F0\uACA0\uC2B5\uB2C8\uAE4C? [\uC544\uB2C8\uC624]:  "},
        {"Too.many.failures.try.later", "\uC624\uB958\uAC00 \uB108\uBB34 \uB9CE\uC74C - \uB098\uC911\uC5D0 \uC2DC\uB3C4\uD558\uC2ED\uC2DC\uC624."},
        {"Certification.request.stored.in.file.filename.",
                "\uC778\uC99D \uC694\uCCAD\uC774 <{0}> \uD30C\uC77C\uC5D0 \uC800\uC7A5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Submit.this.to.your.CA", "CA\uC5D0\uAC8C \uC81C\uCD9C\uD558\uC2ED\uC2DC\uC624."},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "\uBCC4\uCE6D\uC744 \uC9C0\uC815\uD558\uC9C0 \uC54A\uC740 \uACBD\uC6B0 destalias, srckeypass \uBC0F destkeypass\uB97C \uC9C0\uC815\uD558\uC9C0 \uC54A\uC544\uC57C \uD569\uB2C8\uB2E4."},
        {"Certificate.stored.in.file.filename.",
                "\uC778\uC99D\uC11C\uAC00 <{0}> \uD30C\uC77C\uC5D0 \uC800\uC7A5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Certificate.reply.was.installed.in.keystore",
                "\uC778\uC99D\uC11C \uD68C\uC2E0\uC774 \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uC124\uCE58\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Certificate.reply.was.not.installed.in.keystore",
                "\uC778\uC99D\uC11C \uD68C\uC2E0\uC774 \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uC124\uCE58\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"Certificate.was.added.to.keystore",
                "\uC778\uC99D\uC11C\uAC00 \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uCD94\uAC00\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Certificate.was.not.added.to.keystore",
                "\uC778\uC99D\uC11C\uAC00 \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uCD94\uAC00\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {".Storing.ksfname.", "[{0}\uC744(\uB97C) \uC800\uC7A5\uD558\uB294 \uC911]"},
        {"alias.has.no.public.key.certificate.",
                "{0}\uC5D0 \uACF5\uC6A9 \uD0A4(\uC778\uC99D\uC11C)\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Cannot.derive.signature.algorithm",
                "\uC11C\uBA85 \uC54C\uACE0\uB9AC\uC998\uC744 \uD30C\uC0DD\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Alias.alias.does.not.exist",
                "<{0}> \uBCC4\uCE6D\uC774 \uC874\uC7AC\uD558\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."},
        {"Alias.alias.has.no.certificate",
                "<{0}> \uBCC4\uCE6D\uC5D0 \uC778\uC99D\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "\uD0A4 \uC30D\uC774 \uC0DD\uC131\uB418\uC9C0 \uC54A\uC558\uC73C\uBA70 <{0}> \uBCC4\uCE6D\uC774 \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "\uB2E4\uC74C\uC5D0 \uB300\uD574 \uC720\uD6A8 \uAE30\uAC04\uC774 {3}\uC77C\uC778 {0}\uBE44\uD2B8 {1} \uD0A4 \uC30D \uBC0F \uC790\uCCB4 \uC11C\uBA85\uB41C \uC778\uC99D\uC11C({2})\uB97C \uC0DD\uC131\uD558\uB294 \uC911\n\t: {4}"},
        {"Enter.key.password.for.alias.", "<{0}>\uC5D0 \uB300\uD55C \uD0A4 \uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD558\uC2ED\uC2DC\uC624."},
        {".RETURN.if.same.as.keystore.password.",
                "\t(\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uC640 \uB3D9\uC77C\uD55C \uACBD\uC6B0 Enter \uD0A4\uB97C \uB204\uB984):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "\uD0A4 \uBE44\uBC00\uBC88\uD638\uAC00 \uB108\uBB34 \uC9E7\uC74C - 6\uC790 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Too.many.failures.key.not.added.to.keystore",
                "\uC624\uB958\uAC00 \uB108\uBB34 \uB9CE\uC74C - \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uD0A4\uAC00 \uCD94\uAC00\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"Destination.alias.dest.already.exists",
                "\uB300\uC0C1 \uBCC4\uCE6D <{0}>\uC774(\uAC00) \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "\uBE44\uBC00\uBC88\uD638\uAC00 \uB108\uBB34 \uC9E7\uC74C - 6\uC790 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Too.many.failures.Key.entry.not.cloned",
                "\uC624\uB958\uAC00 \uB108\uBB34 \uB9CE\uC2B5\uB2C8\uB2E4. \uD0A4 \uD56D\uBAA9\uC774 \uBCF5\uC81C\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4."},
        {"key.password.for.alias.", "<{0}>\uC5D0 \uB300\uD55C \uD0A4 \uBE44\uBC00\uBC88\uD638"},
        {"Keystore.entry.for.id.getName.already.exists",
                "<{0}>\uC5D0 \uB300\uD55C \uD0A4 \uC800\uC7A5\uC18C \uD56D\uBAA9\uC774 \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Creating.keystore.entry.for.id.getName.",
                "<{0}>\uC5D0 \uB300\uD55C \uD0A4 \uC800\uC7A5\uC18C \uD56D\uBAA9\uC744 \uC0DD\uC131\uD558\uB294 \uC911..."},
        {"No.entries.from.identity.database.added",
                "ID \uB370\uC774\uD130\uBCA0\uC774\uC2A4\uC5D0\uC11C \uCD94\uAC00\uB41C \uD56D\uBAA9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Alias.name.alias", "\uBCC4\uCE6D \uC774\uB984: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "\uC0DD\uC131 \uB0A0\uC9DC: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "\uD56D\uBAA9 \uC720\uD615: {0}"},
        {"Certificate.chain.length.", "\uC778\uC99D\uC11C \uCCB4\uC778 \uAE38\uC774: "},
        {"Certificate.i.1.", "\uC778\uC99D\uC11C[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "\uC778\uC99D\uC11C \uC9C0\uBB38(SHA1): "},
        {"Entry.type.trustedCertEntry.", "\uD56D\uBAA9 \uC720\uD615: trustedCertEntry\n"},
        {"trustedCertEntry.", "trustedCertEntry,"},
        {"Keystore.type.", "\uD0A4 \uC800\uC7A5\uC18C \uC720\uD615: "},
        {"Keystore.provider.", "\uD0A4 \uC800\uC7A5\uC18C \uC81C\uACF5\uC790: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "\uD0A4 \uC800\uC7A5\uC18C\uC5D0 {0,number,integer}\uAC1C\uC758 \uD56D\uBAA9\uC774 \uD3EC\uD568\uB418\uC5B4 \uC788\uC2B5\uB2C8\uB2E4."},
        {"Your.keystore.contains.keyStore.size.entries",
                "\uD0A4 \uC800\uC7A5\uC18C\uC5D0 {0,number,integer}\uAC1C\uC758 \uD56D\uBAA9\uC774 \uD3EC\uD568\uB418\uC5B4 \uC788\uC2B5\uB2C8\uB2E4."},
        {"Failed.to.parse.input", "\uC785\uB825\uAC12\uC758 \uAD6C\uBB38 \uBD84\uC11D\uC744 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4."},
        {"Empty.input", "\uC785\uB825\uAC12\uC774 \uBE44\uC5B4 \uC788\uC2B5\uB2C8\uB2E4."},
        {"Not.X.509.certificate", "X.509 \uC778\uC99D\uC11C\uAC00 \uC544\uB2D9\uB2C8\uB2E4."},
        {"alias.has.no.public.key", "{0}\uC5D0 \uACF5\uC6A9 \uD0A4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"alias.has.no.X.509.certificate", "{0}\uC5D0 X.509 \uC778\uC99D\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"New.certificate.self.signed.", "\uC0C8 \uC778\uC99D\uC11C(\uC790\uCCB4 \uC11C\uBA85):"},
        {"Reply.has.no.certificates", "\uD68C\uC2E0\uC5D0 \uC778\uC99D\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Certificate.not.imported.alias.alias.already.exists",
                "\uC778\uC99D\uC11C\uAC00 \uC784\uD3EC\uD2B8\uB418\uC9C0 \uC54A\uC558\uC73C\uBA70 <{0}> \uBCC4\uCE6D\uC774 \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Input.not.an.X.509.certificate", "\uC785\uB825\uC774 X.509 \uC778\uC99D\uC11C\uAC00 \uC544\uB2D9\uB2C8\uB2E4."},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "\uC778\uC99D\uC11C\uAC00 <{0}> \uBCC4\uCE6D \uC544\uB798\uC758 \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Do.you.still.want.to.add.it.no.",
                "\uCD94\uAC00\uD558\uACA0\uC2B5\uB2C8\uAE4C? [\uC544\uB2C8\uC624]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "\uC778\uC99D\uC11C\uAC00 <{0}> \uBCC4\uCE6D \uC544\uB798\uC5D0 \uC788\uB294 \uC2DC\uC2A4\uD15C \uCC28\uC6D0\uC758 CA \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "\uACE0\uC720\uD55C \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uCD94\uAC00\uD558\uACA0\uC2B5\uB2C8\uAE4C? [\uC544\uB2C8\uC624]:  "},
        {"Trust.this.certificate.no.", "\uC774 \uC778\uC99D\uC11C\uB97C \uC2E0\uB8B0\uD569\uB2C8\uAE4C? [\uC544\uB2C8\uC624]:  "},
        {"YES", "\uC608"},
        {"New.prompt.", "\uC0C8 {0}: "},
        {"Passwords.must.differ", "\uBE44\uBC00\uBC88\uD638\uB294 \uB2EC\uB77C\uC57C \uD569\uB2C8\uB2E4."},
        {"Re.enter.new.prompt.", "\uC0C8 {0} \uB2E4\uC2DC \uC785\uB825: "},
        {"Re.enter.new.password.", "\uC0C8 \uBE44\uBC00\uBC88\uD638 \uB2E4\uC2DC \uC785\uB825: "},
        {"They.don.t.match.Try.again", "\uC77C\uCE58\uD558\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4. \uB2E4\uC2DC \uC2DC\uB3C4\uD558\uC2ED\uC2DC\uC624."},
        {"Enter.prompt.alias.name.", "{0} \uBCC4\uCE6D \uC774\uB984 \uC785\uB825:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "\uC0C8 \uBCC4\uCE6D \uC774\uB984 \uC785\uB825\t(\uC774 \uD56D\uBAA9\uC5D0 \uB300\uD55C \uC784\uD3EC\uD2B8\uB97C \uCDE8\uC18C\uD558\uB824\uBA74 Enter \uD0A4\uB97C \uB204\uB984):  "},
        {"Enter.alias.name.", "\uBCC4\uCE6D \uC774\uB984 \uC785\uB825:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(<{0}>\uACFC(\uC640) \uB3D9\uC77C\uD55C \uACBD\uC6B0 Enter \uD0A4\uB97C \uB204\uB984)"},
        {".PATTERN.printX509Cert",
                "\uC18C\uC720\uC790: {0}\n\uBC1C\uD589\uC790: {1}\n\uC77C\uB828 \uBC88\uD638: {2}\n\uC801\uD569\uD55C \uC2DC\uC791 \uB0A0\uC9DC: {3}, \uC885\uB8CC \uB0A0\uC9DC: {4}\n\uC778\uC99D\uC11C \uC9C0\uBB38:\n\t MD5: {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t \uC11C\uBA85 \uC54C\uACE0\uB9AC\uC998 \uC774\uB984: {8}\n\t \uBC84\uC804: {9}"},
        {"What.is.your.first.and.last.name.",
                "\uC774\uB984\uACFC \uC131\uC744 \uC785\uB825\uD558\uC2ED\uC2DC\uC624."},
        {"What.is.the.name.of.your.organizational.unit.",
                "\uC870\uC9C1 \uB2E8\uC704 \uC774\uB984\uC744 \uC785\uB825\uD558\uC2ED\uC2DC\uC624."},
        {"What.is.the.name.of.your.organization.",
                "\uC870\uC9C1 \uC774\uB984\uC744 \uC785\uB825\uD558\uC2ED\uC2DC\uC624."},
        {"What.is.the.name.of.your.City.or.Locality.",
                "\uAD6C/\uAD70/\uC2DC \uC774\uB984\uC744 \uC785\uB825\uD558\uC2ED\uC2DC\uC624?"},
        {"What.is.the.name.of.your.State.or.Province.",
                "\uC2DC/\uB3C4 \uC774\uB984\uC744 \uC785\uB825\uD558\uC2ED\uC2DC\uC624."},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "\uC774 \uC870\uC9C1\uC758 \uB450 \uC790\uB9AC \uAD6D\uAC00 \uCF54\uB4DC\uB97C \uC785\uB825\uD558\uC2ED\uC2DC\uC624."},
        {"Is.name.correct.", "{0}\uC774(\uAC00) \uB9DE\uC2B5\uB2C8\uAE4C?"},
        {"no", "\uC544\uB2C8\uC624"},
        {"yes", "\uC608"},
        {"y", "y"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "<{0}> \uBCC4\uCE6D\uC5D0 \uD0A4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "<{0}> \uBCC4\uCE6D\uC740 \uC804\uC6A9 \uD0A4 \uD56D\uBAA9\uC774 \uC544\uB2CC \uD56D\uBAA9 \uC720\uD615\uC744 \uCC38\uC870\uD569\uB2C8\uB2E4. -keyclone \uBA85\uB839\uC740 \uC804\uC6A9 \uD0A4 \uD56D\uBAA9\uC758 \uBCF5\uC81C\uB9CC \uC9C0\uC6D0\uD569\uB2C8\uB2E4."},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "\uC11C\uBA85\uC790 #%d:"},
        {"Timestamp.", "\uC2DC\uAC04 \uAE30\uB85D:"},
        {"Signature.", "\uC11C\uBA85:"},
        {"CRLs.", "CRL:"},
        {"Certificate.owner.", "\uC778\uC99D\uC11C \uC18C\uC720\uC790: "},
        {"Not.a.signed.jar.file", "\uC11C\uBA85\uB41C jar \uD30C\uC77C\uC774 \uC544\uB2D9\uB2C8\uB2E4."},
        {"No.certificate.from.the.SSL.server",
                "SSL \uC11C\uBC84\uC5D0\uC11C \uAC00\uC838\uC628 \uC778\uC99D\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},

        // Translators of the following 5 pairs, ATTENTION:
        // the next 5 string pairs are meant to be combined into 2 paragraphs,
        // 1+3+4 and 2+3+5. make sure your translation also does.
        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* \uD0A4 \uC800\uC7A5\uC18C\uC5D0 \uC800\uC7A5\uB41C \uC815\uBCF4\uC758 \uBB34\uACB0\uC131\uC774 *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* srckeystore\uC5D0 \uC800\uC7A5\uB41C \uC815\uBCF4\uC758 \uBB34\uACB0\uC131\uC774*"},
        {".has.NOT.been.verified.In.order.to.verify.its.integrity.",
            "* \uD655\uC778\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4! \uBB34\uACB0\uC131\uC744 \uD655\uC778\uD558\uB824\uBA74 *"},
        {".you.must.provide.your.keystore.password.",
            "* \uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638\uB97C \uC81C\uACF5\uD574\uC57C \uD569\uB2C8\uB2E4.                  *"},
        {".you.must.provide.the.srckeystore.password.",
            "* srckeystore \uBE44\uBC00\uBC88\uD638\uB97C \uC81C\uACF5\uD574\uC57C \uD569\uB2C8\uB2E4.                *"},


        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "\uC778\uC99D\uC11C \uD68C\uC2E0\uC5D0 <{0}>\uC5D0 \uB300\uD55C \uACF5\uC6A9 \uD0A4\uAC00 \uD3EC\uD568\uB418\uC5B4 \uC788\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."},
        {"Incomplete.certificate.chain.in.reply",
                "\uD68C\uC2E0\uC5D0 \uBD88\uC644\uC804\uD55C \uC778\uC99D\uC11C \uCCB4\uC778\uC774 \uC788\uC2B5\uB2C8\uB2E4."},
        {"Certificate.chain.in.reply.does.not.verify.",
                "\uD68C\uC2E0\uC758 \uC778\uC99D\uC11C \uCCB4\uC778\uC774 \uD655\uC778\uB418\uC9C0 \uC54A\uC74C: "},
        {"Top.level.certificate.in.reply.",
                "\uD68C\uC2E0\uC5D0 \uCD5C\uC0C1\uC704 \uB808\uBCA8 \uC778\uC99D\uC11C\uAC00 \uC788\uC74C:\n"},
        {".is.not.trusted.", "...\uC744(\uB97C) \uC2E0\uB8B0\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4. "},
        {"Install.reply.anyway.no.", "\uD68C\uC2E0\uC744 \uC124\uCE58\uD558\uACA0\uC2B5\uB2C8\uAE4C? [\uC544\uB2C8\uC624]:  "},
        {"NO", "\uC544\uB2C8\uC624"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "\uD68C\uC2E0\uACFC \uD0A4 \uC800\uC7A5\uC18C\uC758 \uACF5\uC6A9 \uD0A4\uAC00 \uC77C\uCE58\uD558\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "\uD68C\uC2E0\uACFC \uD0A4 \uC800\uC7A5\uC18C\uC758 \uC778\uC99D\uC11C\uAC00 \uB3D9\uC77C\uD569\uB2C8\uB2E4."},
        {"Failed.to.establish.chain.from.reply",
                "\uD68C\uC2E0\uC758 \uCCB4\uC778 \uC124\uC815\uC744 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4."},
        {"n", "n"},
        {"Wrong.answer.try.again", "\uC798\uBABB\uB41C \uC751\uB2F5\uC785\uB2C8\uB2E4. \uB2E4\uC2DC \uC2DC\uB3C4\uD558\uC2ED\uC2DC\uC624."},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "\uBCF4\uC548 \uD0A4\uAC00 \uC0DD\uC131\uB418\uC9C0 \uC54A\uC558\uC73C\uBA70 <{0}> \uBCC4\uCE6D\uC774 \uC874\uC7AC\uD569\uB2C8\uB2E4."},
        {"Please.provide.keysize.for.secret.key.generation",
                "\uBCF4\uC548 \uD0A4\uB97C \uC0DD\uC131\uD558\uB824\uBA74 -keysize\uB97C \uC81C\uACF5\uD558\uC2ED\uC2DC\uC624."},

        {"Extensions.", "\uD655\uC7A5: "},
        {".Empty.value.", "(\uBE44\uC5B4 \uC788\uB294 \uAC12)"},
        {"Extension.Request.", "\uD655\uC7A5 \uC694\uCCAD:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "PKCS #10 \uC778\uC99D\uC11C \uC694\uCCAD(1.0 \uBC84\uC804)\n\uC81C\uBAA9: %s\n\uACF5\uC6A9 \uD0A4: %s \uD615\uC2DD %s \uD0A4\n"},
        {"Unknown.keyUsage.type.", "\uC54C \uC218 \uC5C6\uB294 keyUsage \uC720\uD615: "},
        {"Unknown.extendedkeyUsage.type.", "\uC54C \uC218 \uC5C6\uB294 extendedkeyUsage \uC720\uD615: "},
        {"Unknown.AccessDescription.type.", "\uC54C \uC218 \uC5C6\uB294 AccessDescription \uC720\uD615: "},
        {"Unrecognized.GeneralName.type.", "\uC54C \uC218 \uC5C6\uB294 GeneralName \uC720\uD615: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "\uC774 \uD655\uC7A5\uC740 \uC911\uC694\uD55C \uAC83\uC73C\uB85C \uD45C\uC2DC\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4. "},
        {"Odd.number.of.hex.digits.found.", "\uD640\uC218 \uAC1C\uC758 16\uC9C4\uC218\uAC00 \uBC1C\uACAC\uB428: "},
        {"Unknown.extension.type.", "\uC54C \uC218 \uC5C6\uB294 \uD655\uC7A5 \uC720\uD615: "},
        {"command.{0}.is.ambiguous.", "{0} \uBA85\uB839\uC774 \uBAA8\uD638\uD568:"},

        // policytool
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
        {"Principal.List", "\uC8FC\uCCB4 \uBAA9\uB85D"},
        {"Permission.List", "\uAD8C\uD55C \uBAA9\uB85D"},
        {"Code.Base", "\uCF54\uB4DC \uBCA0\uC774\uC2A4"},
        {"KeyStore.U.R.L.", "\uD0A4 \uC800\uC7A5\uC18C URL:"},
        {"KeyStore.Password.U.R.L.", "\uD0A4 \uC800\uC7A5\uC18C \uBE44\uBC00\uBC88\uD638 URL:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "\uB110 \uC785\uB825\uAC12\uC774 \uBD80\uC801\uD569\uD569\uB2C8\uB2E4."},
        {"actions.can.only.be.read.", "\uC791\uC5C5\uC740 '\uC77D\uAE30' \uC804\uC6A9\uC785\uB2C8\uB2E4."},
        {"permission.name.name.syntax.invalid.",
                "\uAD8C\uD55C \uC774\uB984 [{0}] \uAD6C\uBB38\uC774 \uBD80\uC801\uD569\uD568: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "\uC778\uC99D\uC11C \uD074\uB798\uC2A4 \uB2E4\uC74C\uC5D0 \uC8FC\uCCB4 \uD074\uB798\uC2A4\uC640 \uC774\uB984\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "\uC8FC\uCCB4 \uD074\uB798\uC2A4 \uB2E4\uC74C\uC5D0 \uC8FC\uCCB4 \uC774\uB984\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "\uC8FC\uCCB4 \uC774\uB984\uC740 \uB530\uC634\uD45C\uB85C \uBB36\uC5B4\uC57C \uD569\uB2C8\uB2E4."},
        {"Principal.Name.missing.end.quote",
                "\uC8FC\uCCB4 \uC774\uB984\uC5D0 \uB2EB\uB294 \uB530\uC634\uD45C\uAC00 \uB204\uB77D\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "\uC8FC\uCCB4 \uC774\uB984\uC774 \uC640\uC77C\uB4DC \uCE74\uB4DC \uBB38\uC790(*) \uAC12\uC774 \uC544\uB2CC \uACBD\uC6B0 PrivateCredentialPermission \uC8FC\uCCB4 \uD074\uB798\uC2A4\uB294 \uC640\uC77C\uB4DC \uCE74\uB4DC \uBB38\uC790(*) \uAC12\uC77C \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\t\uC8FC\uCCB4 \uD074\uB798\uC2A4 = {0}\n\t\uC8FC\uCCB4 \uC774\uB984 = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "\uB110 \uC774\uB984\uC744 \uC81C\uACF5\uD588\uC2B5\uB2C8\uB2E4."},
        {"provided.null.keyword.map", "\uB110 \uD0A4\uC6CC\uB4DC \uB9F5\uC744 \uC81C\uACF5\uD588\uC2B5\uB2C8\uB2E4."},
        {"provided.null.OID.map", "\uB110 OID \uB9F5\uC744 \uC81C\uACF5\uD588\uC2B5\uB2C8\uB2E4."},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "\uBD80\uC801\uD569\uD55C \uB110 AccessControlContext\uAC00 \uC81C\uACF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"invalid.null.action.provided", "\uBD80\uC801\uD569\uD55C \uB110 \uC791\uC5C5\uC774 \uC81C\uACF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"invalid.null.Class.provided", "\uBD80\uC801\uD569\uD55C \uB110 \uD074\uB798\uC2A4\uAC00 \uC81C\uACF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"Subject.", "\uC81C\uBAA9:\n"},
        {".Principal.", "\\\uC8FC\uCCB4: "},
        {".Public.Credential.", "\t\uACF5\uC6A9 \uC778\uC99D\uC11C: "},
        {".Private.Credentials.inaccessible.",
                "\t\uC804\uC6A9 \uC778\uC99D\uC11C\uC5D0 \uC561\uC138\uC2A4\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.\n"},
        {".Private.Credential.", "\t\uC804\uC6A9 \uC778\uC99D\uC11C: "},
        {".Private.Credential.inaccessible.",
                "\t\uC804\uC6A9 \uC778\uC99D\uC11C\uC5D0 \uC561\uC138\uC2A4\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.\n"},
        {"Subject.is.read.only", "\uC81C\uBAA9\uC774 \uC77D\uAE30 \uC804\uC6A9\uC785\uB2C8\uB2E4."},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "java.security.Principal\uC758 \uC778\uC2A4\uD134\uC2A4\uAC00 \uC544\uB2CC \uAC1D\uCCB4\uB97C \uC81C\uBAA9\uC758 \uC8FC\uCCB4 \uC9D1\uD569\uC5D0 \uCD94\uAC00\uD558\uB824\uACE0 \uC2DC\uB3C4\uD558\uB294 \uC911"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "{0}\uC758 \uC778\uC2A4\uD134\uC2A4\uAC00 \uC544\uB2CC \uAC1D\uCCB4\uB97C \uCD94\uAC00\uD558\uB824\uACE0 \uC2DC\uB3C4\uD558\uB294 \uC911"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "\uBD80\uC801\uD569\uD55C \uB110 \uC785\uB825\uAC12: \uC774\uB984"},
        {"No.LoginModules.configured.for.name",
         "{0}\uC5D0 \uB300\uD574 \uAD6C\uC131\uB41C LoginModules\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"invalid.null.Subject.provided", "\uBD80\uC801\uD569\uD55C \uB110 \uC81C\uBAA9\uC774 \uC81C\uACF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"invalid.null.CallbackHandler.provided",
                "\uBD80\uC801\uD569\uD55C \uB110 CallbackHandler\uAC00 \uC81C\uACF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"null.subject.logout.called.before.login",
                "\uB110 \uC81C\uBAA9 - \uB85C\uADF8\uC778 \uC804\uC5D0 \uB85C\uADF8\uC544\uC6C3\uC774 \uD638\uCD9C\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "\uC778\uC218\uAC00 \uC5C6\uB294 \uC0DD\uC131\uC790\uB97C \uC81C\uACF5\uD558\uC9C0 \uC54A\uC544 LoginModule {0}\uC744(\uB97C) \uC778\uC2A4\uD134\uC2A4\uD654\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"unable.to.instantiate.LoginModule",
                "LoginModule\uC744 \uC778\uC2A4\uD134\uC2A4\uD654\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"unable.to.instantiate.LoginModule.",
                "LoginModule\uC744 \uC778\uC2A4\uD134\uC2A4\uD654\uD560 \uC218 \uC5C6\uC74C: "},
        {"unable.to.find.LoginModule.class.",
                "LoginModule \uD074\uB798\uC2A4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC74C: "},
        {"unable.to.access.LoginModule.",
                "LoginModule\uC5D0 \uC561\uC138\uC2A4\uD560 \uC218 \uC5C6\uC74C: "},
        {"Login.Failure.all.modules.ignored",
                "\uB85C\uADF8\uC778 \uC2E4\uD328: \uBAA8\uB4E0 \uBAA8\uB4C8\uC774 \uBB34\uC2DC\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: {0}\uC758 \uAD6C\uBB38\uC744 \uBD84\uC11D\uD558\uB294 \uC911 \uC624\uB958 \uBC1C\uC0DD:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: {0} \uAD8C\uD55C\uC744 \uCD94\uAC00\uD558\uB294 \uC911 \uC624\uB958 \uBC1C\uC0DD:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: \uD56D\uBAA9\uC744 \uCD94\uAC00\uD558\uB294 \uC911 \uC624\uB958 \uBC1C\uC0DD:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "\uBCC4\uCE6D \uC774\uB984\uC774 \uC81C\uACF5\uB418\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4({0})."},
        {"unable.to.perform.substitution.on.alias.suffix",
                "{0} \uBCC4\uCE6D\uC744 \uB300\uCCB4\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"substitution.value.prefix.unsupported",
                "\uB300\uCCB4 \uAC12 {0}\uC740(\uB294) \uC9C0\uC6D0\uB418\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","\uC720\uD615\uC740 \uB110\uC77C \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "\uD0A4 \uC800\uC7A5\uC18C\uB97C \uC9C0\uC815\uD558\uC9C0 \uC54A\uACE0 keystorePasswordURL\uC744 \uC9C0\uC815\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"expected.keystore.type", "\uD0A4 \uC800\uC7A5\uC18C \uC720\uD615\uC774 \uD544\uC694\uD569\uB2C8\uB2E4."},
        {"expected.keystore.provider", "\uD0A4 \uC800\uC7A5\uC18C \uC81C\uACF5\uC790\uAC00 \uD544\uC694\uD569\uB2C8\uB2E4."},
        {"multiple.Codebase.expressions",
                "Codebase \uD45C\uD604\uC2DD\uC774 \uC5EC\uB7EC \uAC1C\uC785\uB2C8\uB2E4."},
        {"multiple.SignedBy.expressions","SignedBy \uD45C\uD604\uC2DD\uC774 \uC5EC\uB7EC \uAC1C\uC785\uB2C8\uB2E4."},
        {"SignedBy.has.empty.alias","SignedBy\uC758 \uBCC4\uCE6D\uC774 \uBE44\uC5B4 \uC788\uC2B5\uB2C8\uB2E4."},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "\uC640\uC77C\uB4DC \uCE74\uB4DC \uBB38\uC790 \uC774\uB984 \uC5C6\uC774 \uC640\uC77C\uB4DC \uCE74\uB4DC \uBB38\uC790 \uD074\uB798\uC2A4\uB97C \uC0AC\uC6A9\uD558\uB294 \uC8FC\uCCB4\uB97C \uC9C0\uC815\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "codeBase, SignedBy \uB610\uB294 \uC8FC\uCCB4\uAC00 \uD544\uC694\uD569\uB2C8\uB2E4."},
        {"expected.permission.entry", "\uAD8C\uD55C \uD56D\uBAA9\uC774 \uD544\uC694\uD569\uB2C8\uB2E4."},
        {"number.", "\uC22B\uC790 "},
        {"expected.expect.read.end.of.file.",
                "[{0}]\uC774(\uAC00) \uD544\uC694\uD558\uC9C0\uB9CC [\uD30C\uC77C\uC758 \uB05D]\uAE4C\uC9C0 \uC77D\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"expected.read.end.of.file.",
                "[;]\uC774 \uD544\uC694\uD558\uC9C0\uB9CC [\uD30C\uC77C\uC758 \uB05D]\uAE4C\uC9C0 \uC77D\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"line.number.msg", "{0} \uD589: {1}"},
        {"line.number.expected.expect.found.actual.",
                "{0} \uD589: [{1}]\uC774(\uAC00) \uD544\uC694\uD558\uC9C0\uB9CC [{2}]\uC774(\uAC00) \uBC1C\uACAC\uB418\uC5C8\uC2B5\uB2C8\uB2E4."},
        {"null.principalClass.or.principalName",
                "principalClass \uB610\uB294 principalName\uC774 \uB110\uC785\uB2C8\uB2E4."},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "PKCS11 \uD1A0\uD070 [{0}] \uBE44\uBC00\uBC88\uD638: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "\uC81C\uBAA9 \uAE30\uBC18 \uC815\uCC45\uC744 \uC778\uC2A4\uD134\uC2A4\uD654\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."}
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

