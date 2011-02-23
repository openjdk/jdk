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
public class Resources_ja extends java.util.ListResourceBundle {

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
        {"Options.", "\u30AA\u30D7\u30B7\u30E7\u30F3:"},
        {"Use.keytool.help.for.all.available.commands",
                 "\u4F7F\u7528\u53EF\u80FD\u306A\u3059\u3079\u3066\u306E\u30B3\u30DE\u30F3\u30C9\u306B\u3064\u3044\u3066\u306F\"keytool -help\"\u3092\u4F7F\u7528\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Key.and.Certificate.Management.Tool",
                 "\u30AD\u30FC\u304A\u3088\u3073\u8A3C\u660E\u66F8\u7BA1\u7406\u30C4\u30FC\u30EB"},
        {"Commands.", "\u30B3\u30DE\u30F3\u30C9:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "command_name\u306E\u4F7F\u7528\u65B9\u6CD5\u306B\u3064\u3044\u3066\u306F\"keytool -command_name -help\"\u3092\u4F7F\u7528\u3057\u3066\u304F\u3060\u3055\u3044"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "\u8A3C\u660E\u66F8\u30EA\u30AF\u30A8\u30B9\u30C8\u3092\u751F\u6210\u3057\u307E\u3059"}, //-certreq
        {"Changes.an.entry.s.alias",
                "\u30A8\u30F3\u30C8\u30EA\u306E\u5225\u540D\u3092\u5909\u66F4\u3057\u307E\u3059"}, //-changealias
        {"Deletes.an.entry",
                "\u30A8\u30F3\u30C8\u30EA\u3092\u524A\u9664\u3057\u307E\u3059"}, //-delete
        {"Exports.certificate",
                "\u8A3C\u660E\u66F8\u3092\u30A8\u30AF\u30B9\u30DD\u30FC\u30C8\u3057\u307E\u3059"}, //-exportcert
        {"Generates.a.key.pair",
                "\u9375\u30DA\u30A2\u3092\u751F\u6210\u3057\u307E\u3059"}, //-genkeypair
        {"Generates.a.secret.key",
                "\u79D8\u5BC6\u9375\u3092\u751F\u6210\u3057\u307E\u3059"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "\u8A3C\u660E\u66F8\u30EA\u30AF\u30A8\u30B9\u30C8\u304B\u3089\u8A3C\u660E\u66F8\u3092\u751F\u6210\u3057\u307E\u3059"}, //-gencert
        {"Generates.CRL", "CRL\u3092\u751F\u6210\u3057\u307E\u3059"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "JDK 1.1.x-style\u30A2\u30A4\u30C7\u30F3\u30C6\u30A3\u30C6\u30A3\u30FB\u30C7\u30FC\u30BF\u30D9\u30FC\u30B9\u304B\u3089\u30A8\u30F3\u30C8\u30EA\u3092\u30A4\u30F3\u30DD\u30FC\u30C8\u3057\u307E\u3059"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "\u8A3C\u660E\u66F8\u307E\u305F\u306F\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u3092\u30A4\u30F3\u30DD\u30FC\u30C8\u3057\u307E\u3059"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "\u5225\u306E\u30AD\u30FC\u30B9\u30C8\u30A2\u304B\u30891\u3064\u307E\u305F\u306F\u3059\u3079\u3066\u306E\u30A8\u30F3\u30C8\u30EA\u3092\u30A4\u30F3\u30DD\u30FC\u30C8\u3057\u307E\u3059"}, //-importkeystore
        {"Clones.a.key.entry",
                "\u9375\u30A8\u30F3\u30C8\u30EA\u306E\u30AF\u30ED\u30FC\u30F3\u3092\u4F5C\u6210\u3057\u307E\u3059"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "\u30A8\u30F3\u30C8\u30EA\u306E\u9375\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5909\u66F4\u3057\u307E\u3059"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u5185\u306E\u30A8\u30F3\u30C8\u30EA\u3092\u30EA\u30B9\u30C8\u3057\u307E\u3059"}, //-list
        {"Prints.the.content.of.a.certificate",
                "\u8A3C\u660E\u66F8\u306E\u5185\u5BB9\u3092\u51FA\u529B\u3057\u307E\u3059"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "\u8A3C\u660E\u66F8\u30EA\u30AF\u30A8\u30B9\u30C8\u306E\u5185\u5BB9\u3092\u51FA\u529B\u3057\u307E\u3059"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "CRL\u30D5\u30A1\u30A4\u30EB\u306E\u5185\u5BB9\u3092\u51FA\u529B\u3057\u307E\u3059"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "\u81EA\u5DF1\u7F72\u540D\u578B\u8A3C\u660E\u66F8\u3092\u751F\u6210\u3057\u307E\u3059"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30B9\u30C8\u30A2\u30FB\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5909\u66F4\u3057\u307E\u3059"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "\u51E6\u7406\u3059\u308B\u30A8\u30F3\u30C8\u30EA\u306E\u5225\u540D"}, //-alias
        {"destination.alias",
                "\u51FA\u529B\u5148\u306E\u5225\u540D"}, //-destalias
        {"destination.key.password",
                "\u51FA\u529B\u5148\u30AD\u30FC\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-destkeypass
        {"destination.keystore.name",
                "\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u540D"}, //-destkeystore
        {"destination.keystore.password.protected",
                "\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u4FDD\u8B77\u5BFE\u8C61\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-destprotected
        {"destination.keystore.provider.name",
                "\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D7\u30ED\u30D0\u30A4\u30C0\u540D"}, //-destprovidername
        {"destination.keystore.password",
                "\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-deststorepass
        {"destination.keystore.type",
                "\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30BF\u30A4\u30D7"}, //-deststoretype
        {"distinguished.name",
                "\u8B58\u5225\u540D"}, //-dname
        {"X.509.extension",
                "X.509\u62E1\u5F35"}, //-ext
        {"output.file.name",
                "\u51FA\u529B\u30D5\u30A1\u30A4\u30EB\u540D"}, //-file and -outfile
        {"input.file.name",
                "\u5165\u529B\u30D5\u30A1\u30A4\u30EB\u540D"}, //-file and -infile
        {"key.algorithm.name",
                "\u9375\u30A2\u30EB\u30B4\u30EA\u30BA\u30E0\u540D"}, //-keyalg
        {"key.password",
                "\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-keypass
        {"key.bit.size",
                "\u9375\u306E\u30D3\u30C3\u30C8\u30FB\u30B5\u30A4\u30BA"}, //-keysize
        {"keystore.name",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u540D"}, //-keystore
        {"new.password",
                "\u65B0\u898F\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-new
        {"do.not.prompt",
                "\u30D7\u30ED\u30F3\u30D7\u30C8\u3092\u8868\u793A\u3057\u306A\u3044"}, //-noprompt
        {"password.through.protected.mechanism",
                "\u4FDD\u8B77\u30E1\u30AB\u30CB\u30BA\u30E0\u306B\u3088\u308B\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-protected
        {"provider.argument",
                "\u30D7\u30ED\u30D0\u30A4\u30C0\u5F15\u6570"}, //-providerarg
        {"provider.class.name",
                "\u30D7\u30ED\u30D0\u30A4\u30C0\u30FB\u30AF\u30E9\u30B9\u540D"}, //-providerclass
        {"provider.name",
                "\u30D7\u30ED\u30D0\u30A4\u30C0\u540D"}, //-providername
        {"provider.classpath",
                "\u30D7\u30ED\u30D0\u30A4\u30C0\u30FB\u30AF\u30E9\u30B9\u30D1\u30B9"}, //-providerpath
        {"output.in.RFC.style",
                "RFC\u30B9\u30BF\u30A4\u30EB\u306E\u51FA\u529B"}, //-rfc
        {"signature.algorithm.name",
                "\u7F72\u540D\u30A2\u30EB\u30B4\u30EA\u30BA\u30E0\u540D"}, //-sigalg
        {"source.alias",
                "\u30BD\u30FC\u30B9\u5225\u540D"}, //-srcalias
        {"source.key.password",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-srckeypass
        {"source.keystore.name",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u540D"}, //-srckeystore
        {"source.keystore.password.protected",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u4FDD\u8B77\u5BFE\u8C61\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-srcprotected
        {"source.keystore.provider.name",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D7\u30ED\u30D0\u30A4\u30C0\u540D"}, //-srcprovidername
        {"source.keystore.password",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-srcstorepass
        {"source.keystore.type",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30BF\u30A4\u30D7"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "SSL\u30B5\u30FC\u30D0\u30FC\u306E\u30DB\u30B9\u30C8\u3068\u30DD\u30FC\u30C8"}, //-sslserver
        {"signed.jar.file",
                "\u7F72\u540D\u4ED8\u304DJAR\u30D5\u30A1\u30A4\u30EB"}, //=jarfile
        {"certificate.validity.start.date.time",
                "\u8A3C\u660E\u66F8\u306E\u6709\u52B9\u958B\u59CB\u65E5\u6642"}, //-startdate
        {"keystore.password",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"}, //-storepass
        {"keystore.type",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30BF\u30A4\u30D7"}, //-storetype
        {"trust.certificates.from.cacerts",
                "cacerts\u304B\u3089\u306E\u8A3C\u660E\u66F8\u3092\u4FE1\u983C\u3059\u308B"}, //-trustcacerts
        {"verbose.output",
                "\u8A73\u7D30\u51FA\u529B"}, //-v
        {"validity.number.of.days",
                "\u59A5\u5F53\u6027\u65E5\u6570"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "\u5931\u52B9\u3059\u308B\u8A3C\u660E\u66F8\u306E\u30B7\u30EA\u30A2\u30EBID"}, //-id
        // keytool: Running part
        {"keytool.error.", "keytool\u30A8\u30E9\u30FC: "},
        {"Illegal.option.", "\u4E0D\u6B63\u306A\u30AA\u30D7\u30B7\u30E7\u30F3:  "},
        {"Illegal.value.", "\u4E0D\u6B63\u306A\u5024: "},
        {"Unknown.password.type.", "\u4E0D\u660E\u306A\u30D1\u30B9\u30EF\u30FC\u30C9\u30FB\u30BF\u30A4\u30D7: "},
        {"Cannot.find.environment.variable.",
                "\u74B0\u5883\u5909\u6570\u304C\u898B\u3064\u304B\u308A\u307E\u305B\u3093: "},
        {"Cannot.find.file.", "\u30D5\u30A1\u30A4\u30EB\u304C\u898B\u3064\u304B\u308A\u307E\u305B\u3093: "},
        {"Command.option.flag.needs.an.argument.", "\u30B3\u30DE\u30F3\u30C9\u30FB\u30AA\u30D7\u30B7\u30E7\u30F3{0}\u306B\u306F\u5F15\u6570\u304C\u5FC5\u8981\u3067\u3059\u3002"},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "\u8B66\u544A: PKCS12\u30AD\u30FC\u30B9\u30C8\u30A2\u3067\u306F\u3001\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3068\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u304C\u7570\u306A\u308B\u72B6\u6CC1\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u307E\u305B\u3093\u3002\u30E6\u30FC\u30B6\u30FC\u304C\u6307\u5B9A\u3057\u305F{0}\u306E\u5024\u306F\u7121\u8996\u3057\u307E\u3059\u3002"},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-storetype\u304C{0}\u306E\u5834\u5408\u3001-keystore\u306FNONE\u3067\u3042\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Too.many.retries.program.terminated",
                 "\u518D\u8A66\u884C\u304C\u591A\u3059\u304E\u307E\u3059\u3002\u30D7\u30ED\u30B0\u30E9\u30E0\u304C\u7D42\u4E86\u3057\u307E\u3057\u305F"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "-storetype\u304C{0}\u306E\u5834\u5408\u3001-storepasswd\u30B3\u30DE\u30F3\u30C9\u304A\u3088\u3073-keypasswd\u30B3\u30DE\u30F3\u30C9\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u307E\u305B\u3093"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "-storetype\u304CPKCS12\u306E\u5834\u5408\u3001-keypasswd\u30B3\u30DE\u30F3\u30C9\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u307E\u305B\u3093"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "-storetype\u304C{0}\u306E\u5834\u5408\u3001-keypass\u3068-new\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "-protected\u304C\u6307\u5B9A\u3055\u308C\u3066\u3044\u308B\u5834\u5408\u3001-storepass\u3001-keypass\u304A\u3088\u3073-new\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "-srcprotected\u304C\u6307\u5B9A\u3055\u308C\u3066\u3044\u308B\u5834\u5408\u3001-srcstorepass\u304A\u3088\u3073-srckeypass\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u304C\u30D1\u30B9\u30EF\u30FC\u30C9\u3067\u4FDD\u8B77\u3055\u308C\u3066\u3044\u306A\u3044\u5834\u5408\u3001-storepass\u3001-keypass\u304A\u3088\u3073-new\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u304C\u30D1\u30B9\u30EF\u30FC\u30C9\u3067\u4FDD\u8B77\u3055\u308C\u3066\u3044\u306A\u3044\u5834\u5408\u3001-srcstorepass\u304A\u3088\u3073-srckeypass\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"Illegal.startdate.value", "startdate\u5024\u304C\u7121\u52B9\u3067\u3059"},
        {"Validity.must.be.greater.than.zero",
                "\u59A5\u5F53\u6027\u306F\u30BC\u30ED\u3088\u308A\u5927\u304D\u3044\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"provName.not.a.provider", "{0}\u306F\u30D7\u30ED\u30D0\u30A4\u30C0\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"Usage.error.no.command.provided", "\u4F7F\u7528\u30A8\u30E9\u30FC: \u30B3\u30DE\u30F3\u30C9\u304C\u6307\u5B9A\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"Source.keystore.file.exists.but.is.empty.", "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D5\u30A1\u30A4\u30EB\u306F\u3001\u5B58\u5728\u3057\u307E\u3059\u304C\u7A7A\u3067\u3059: "},
        {"Please.specify.srckeystore", "-srckeystore\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "'list'\u30B3\u30DE\u30F3\u30C9\u306B-v\u3068-rfc\u306E\u4E21\u65B9\u3092\u6307\u5B9A\u3059\u308B\u3053\u3068\u306F\u3067\u304D\u307E\u305B\u3093"},
        {"Key.password.must.be.at.least.6.characters",
                "\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u306F6\u6587\u5B57\u4EE5\u4E0A\u3067\u3042\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"New.password.must.be.at.least.6.characters",
                "\u65B0\u898F\u30D1\u30B9\u30EF\u30FC\u30C9\u306F6\u6587\u5B57\u4EE5\u4E0A\u3067\u3042\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Keystore.file.exists.but.is.empty.",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D5\u30A1\u30A4\u30EB\u306F\u5B58\u5728\u3057\u307E\u3059\u304C\u3001\u7A7A\u3067\u3059: "},
        {"Keystore.file.does.not.exist.",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D5\u30A1\u30A4\u30EB\u306F\u5B58\u5728\u3057\u307E\u305B\u3093: "},
        {"Must.specify.destination.alias", "\u51FA\u529B\u5148\u306E\u5225\u540D\u3092\u6307\u5B9A\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Must.specify.alias", "\u5225\u540D\u3092\u6307\u5B9A\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Keystore.password.must.be.at.least.6.characters",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u306F6\u6587\u5B57\u4EE5\u4E0A\u3067\u3042\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Enter.keystore.password.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044:  "},
        {"Enter.source.keystore.password.", "\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044:  "},
        {"Enter.destination.keystore.password.", "\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u304C\u77ED\u3059\u304E\u307E\u3059 - 6\u6587\u5B57\u4EE5\u4E0A\u306B\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Unknown.Entry.Type", "\u4E0D\u660E\u306A\u30A8\u30F3\u30C8\u30EA\u30FB\u30BF\u30A4\u30D7"},
        {"Too.many.failures.Alias.not.changed", "\u969C\u5BB3\u304C\u591A\u3059\u304E\u307E\u3059\u3002\u5225\u540D\u306F\u5909\u66F4\u3055\u308C\u307E\u305B\u3093"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "\u5225\u540D{0}\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u30A4\u30F3\u30DD\u30FC\u30C8\u306B\u6210\u529F\u3057\u307E\u3057\u305F\u3002"},
        {"Entry.for.alias.alias.not.imported.", "\u5225\u540D{0}\u306E\u30A8\u30F3\u30C8\u30EA\u306F\u30A4\u30F3\u30DD\u30FC\u30C8\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002"},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "\u5225\u540D{0}\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u30A4\u30F3\u30DD\u30FC\u30C8\u4E2D\u306B\u554F\u984C\u304C\u767A\u751F\u3057\u307E\u3057\u305F: {1}\u3002\n\u5225\u540D{0}\u306E\u30A8\u30F3\u30C8\u30EA\u306F\u30A4\u30F3\u30DD\u30FC\u30C8\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002"},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "\u30A4\u30F3\u30DD\u30FC\u30C8\u30FB\u30B3\u30DE\u30F3\u30C9\u304C\u5B8C\u4E86\u3057\u307E\u3057\u305F: {0}\u4EF6\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u30A4\u30F3\u30DD\u30FC\u30C8\u304C\u6210\u529F\u3057\u307E\u3057\u305F\u3002{1}\u4EF6\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u30A4\u30F3\u30DD\u30FC\u30C8\u304C\u5931\u6557\u3057\u305F\u304B\u53D6\u308A\u6D88\u3055\u308C\u307E\u3057\u305F"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "\u8B66\u544A: \u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u5185\u306E\u65E2\u5B58\u306E\u5225\u540D{0}\u3092\u4E0A\u66F8\u304D\u3057\u3066\u3044\u307E\u3059"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "\u65E2\u5B58\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u5225\u540D{0}\u304C\u5B58\u5728\u3057\u3066\u3044\u307E\u3059\u3002\u4E0A\u66F8\u304D\u3057\u307E\u3059\u304B\u3002[\u3044\u3044\u3048]:  "},
        {"Too.many.failures.try.later", "\u969C\u5BB3\u304C\u591A\u3059\u304E\u307E\u3059 - \u5F8C\u3067\u5B9F\u884C\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Certification.request.stored.in.file.filename.",
                "\u8A3C\u660E\u66F8\u30EA\u30AF\u30A8\u30B9\u30C8\u304C\u30D5\u30A1\u30A4\u30EB<{0}>\u306B\u4FDD\u5B58\u3055\u308C\u307E\u3057\u305F"},
        {"Submit.this.to.your.CA", "\u3053\u308C\u3092CA\u306B\u63D0\u51FA\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "\u5225\u540D\u3092\u6307\u5B9A\u3057\u306A\u3044\u5834\u5408\u3001\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u5225\u540D\u3001\u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u304A\u3088\u3073\u51FA\u529B\u5148\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"Certificate.stored.in.file.filename.",
                "\u8A3C\u660E\u66F8\u304C\u30D5\u30A1\u30A4\u30EB<{0}>\u306B\u4FDD\u5B58\u3055\u308C\u307E\u3057\u305F"},
        {"Certificate.reply.was.installed.in.keystore",
                "\u8A3C\u660E\u66F8\u5FDC\u7B54\u304C\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u30A4\u30F3\u30B9\u30C8\u30FC\u30EB\u3055\u308C\u307E\u3057\u305F"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "\u8A3C\u660E\u66F8\u5FDC\u7B54\u304C\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u30A4\u30F3\u30B9\u30C8\u30FC\u30EB\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"Certificate.was.added.to.keystore",
                "\u8A3C\u660E\u66F8\u304C\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u8FFD\u52A0\u3055\u308C\u307E\u3057\u305F"},
        {"Certificate.was.not.added.to.keystore",
                "\u8A3C\u660E\u66F8\u304C\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u8FFD\u52A0\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {".Storing.ksfname.", "[{0}\u3092\u683C\u7D0D\u4E2D]"},
        {"alias.has.no.public.key.certificate.",
                "{0}\u306B\u306F\u516C\u958B\u9375(\u8A3C\u660E\u66F8)\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Cannot.derive.signature.algorithm",
                "\u7F72\u540D\u30A2\u30EB\u30B4\u30EA\u30BA\u30E0\u3092\u53D6\u5F97\u3067\u304D\u307E\u305B\u3093"},
        {"Alias.alias.does.not.exist",
                "\u5225\u540D<{0}>\u306F\u5B58\u5728\u3057\u307E\u305B\u3093"},
        {"Alias.alias.has.no.certificate",
                "\u5225\u540D<{0}>\u306B\u306F\u8A3C\u660E\u66F8\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "\u9375\u30DA\u30A2\u306F\u751F\u6210\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u5225\u540D<{0}>\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "{3}\u65E5\u9593\u6709\u52B9\u306A{0}\u30D3\u30C3\u30C8\u306E{1}\u306E\u9375\u30DA\u30A2\u3068\u81EA\u5DF1\u7F72\u540D\u578B\u8A3C\u660E\u66F8({2})\u3092\u751F\u6210\u3057\u3066\u3044\u307E\u3059\n\t\u30C7\u30A3\u30EC\u30AF\u30C8\u30EA\u540D: {4}"},
        {"Enter.key.password.for.alias.", "<{0}>\u306E\u9375\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3068\u540C\u3058\u5834\u5408\u306FRETURN\u3092\u62BC\u3057\u3066\u304F\u3060\u3055\u3044):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u304C\u77ED\u3059\u304E\u307E\u3059 - 6\u6587\u5B57\u4EE5\u4E0A\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Too.many.failures.key.not.added.to.keystore",
                "\u969C\u5BB3\u304C\u591A\u3059\u304E\u307E\u3059 - \u9375\u306F\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u8FFD\u52A0\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"Destination.alias.dest.already.exists",
                "\u51FA\u529B\u5148\u306E\u5225\u540D<{0}>\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "\u30D1\u30B9\u30EF\u30FC\u30C9\u304C\u77ED\u3059\u304E\u307E\u3059 - 6\u6587\u5B57\u4EE5\u4E0A\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Too.many.failures.Key.entry.not.cloned",
                "\u969C\u5BB3\u304C\u591A\u3059\u304E\u307E\u3059\u3002\u9375\u30A8\u30F3\u30C8\u30EA\u306E\u30AF\u30ED\u30FC\u30F3\u306F\u4F5C\u6210\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"key.password.for.alias.", "<{0}>\u306E\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"},
        {"Keystore.entry.for.id.getName.already.exists",
                "<{0}>\u306E\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30A8\u30F3\u30C8\u30EA\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Creating.keystore.entry.for.id.getName.",
                "<{0}>\u306E\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30A8\u30F3\u30C8\u30EA\u3092\u4F5C\u6210\u4E2D..."},
        {"No.entries.from.identity.database.added",
                "\u30A2\u30A4\u30C7\u30F3\u30C6\u30A3\u30C6\u30A3\u30FB\u30C7\u30FC\u30BF\u30D9\u30FC\u30B9\u304B\u3089\u8FFD\u52A0\u3055\u308C\u305F\u30A8\u30F3\u30C8\u30EA\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"Alias.name.alias", "\u5225\u540D: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "\u4F5C\u6210\u65E5: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0},{1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "\u30A8\u30F3\u30C8\u30EA\u30FB\u30BF\u30A4\u30D7: {0}"},
        {"Certificate.chain.length.", "\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u306E\u9577\u3055: "},
        {"Certificate.i.1.", "\u8A3C\u660E\u66F8[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "\u8A3C\u660E\u66F8\u306E\u30D5\u30A3\u30F3\u30AC\u30D7\u30EA\u30F3\u30C8(SHA1): "},
        {"Entry.type.trustedCertEntry.", "\u30A8\u30F3\u30C8\u30EA\u306E\u30BF\u30A4\u30D7: trustedCertEntry\n"},
        {"trustedCertEntry.", "trustedCertEntry,"},
        {"Keystore.type.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30BF\u30A4\u30D7: "},
        {"Keystore.provider.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D7\u30ED\u30D0\u30A4\u30C0: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u306F{0,number,integer}\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u307E\u3059"},
        {"Your.keystore.contains.keyStore.size.entries",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u306F{0,number,integer}\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u307E\u3059"},
        {"Failed.to.parse.input", "\u5165\u529B\u306E\u69CB\u6587\u89E3\u6790\u306B\u5931\u6557\u3057\u307E\u3057\u305F"},
        {"Empty.input", "\u5165\u529B\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Not.X.509.certificate", "X.509\u8A3C\u660E\u66F8\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"alias.has.no.public.key", "{0}\u306B\u306F\u516C\u958B\u9375\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"alias.has.no.X.509.certificate", "{0}\u306B\u306FX.509\u8A3C\u660E\u66F8\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"New.certificate.self.signed.", "\u65B0\u3057\u3044\u8A3C\u660E\u66F8(\u81EA\u5DF1\u7F72\u540D\u578B):"},
        {"Reply.has.no.certificates", "\u5FDC\u7B54\u306B\u306F\u8A3C\u660E\u66F8\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "\u8A3C\u660E\u66F8\u306F\u30A4\u30F3\u30DD\u30FC\u30C8\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u5225\u540D<{0}>\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Input.not.an.X.509.certificate", "\u5165\u529B\u306FX.509\u8A3C\u660E\u66F8\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "\u8A3C\u660E\u66F8\u306F\u3001\u5225\u540D<{0}>\u306E\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Do.you.still.want.to.add.it.no.",
                "\u8FFD\u52A0\u3057\u307E\u3059\u304B\u3002[\u3044\u3044\u3048]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "\u8A3C\u660E\u66F8\u306F\u3001\u5225\u540D<{0}>\u306E\u30B7\u30B9\u30C6\u30E0\u898F\u6A21\u306ECA\u30AD\u30FC\u30B9\u30C8\u30A2\u5185\u306B\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u8FFD\u52A0\u3057\u307E\u3059\u304B\u3002 [\u3044\u3044\u3048]:  "},
        {"Trust.this.certificate.no.", "\u3053\u306E\u8A3C\u660E\u66F8\u3092\u4FE1\u983C\u3057\u307E\u3059\u304B\u3002 [\u3044\u3044\u3048]:  "},
        {"YES", "\u306F\u3044"},
        {"New.prompt.", "\u65B0\u898F{0}: "},
        {"Passwords.must.differ", "\u30D1\u30B9\u30EF\u30FC\u30C9\u306F\u7570\u306A\u3063\u3066\u3044\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Re.enter.new.prompt.", "\u65B0\u898F{0}\u3092\u518D\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044: "},
        {"Re.enter.new.password.", "\u65B0\u898F\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u518D\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044: "},
        {"They.don.t.match.Try.again", "\u4E00\u81F4\u3057\u307E\u305B\u3093\u3002\u3082\u3046\u4E00\u5EA6\u5B9F\u884C\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Enter.prompt.alias.name.", "{0}\u306E\u5225\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "\u65B0\u3057\u3044\u5225\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\t(\u3053\u306E\u30A8\u30F3\u30C8\u30EA\u306E\u30A4\u30F3\u30DD\u30FC\u30C8\u3092\u53D6\u308A\u6D88\u3059\u5834\u5408\u306FRETURN\u3092\u62BC\u3057\u3066\u304F\u3060\u3055\u3044):  "},
        {"Enter.alias.name.", "\u5225\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(<{0}>\u3068\u540C\u3058\u5834\u5408\u306FRETURN\u3092\u62BC\u3057\u3066\u304F\u3060\u3055\u3044)"},
        {".PATTERN.printX509Cert",
                "\u6240\u6709\u8005: {0}\n\u767A\u884C\u8005: {1}\n\u30B7\u30EA\u30A2\u30EB\u756A\u53F7: {2}\n\u6709\u52B9\u671F\u9593\u306E\u958B\u59CB\u65E5: {3}\u7D42\u4E86\u65E5: {4}\n\u8A3C\u660E\u66F8\u306E\u30D5\u30A3\u30F3\u30AC\u30D7\u30EA\u30F3\u30C8:\n\t MD5:  {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t \u7F72\u540D\u30A2\u30EB\u30B4\u30EA\u30BA\u30E0\u540D: {8}\n\t \u30D0\u30FC\u30B8\u30E7\u30F3: {9}"},
        {"What.is.your.first.and.last.name.",
                "\u59D3\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"What.is.the.name.of.your.organizational.unit.",
                "\u7D44\u7E54\u5358\u4F4D\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"What.is.the.name.of.your.organization.",
                "\u7D44\u7E54\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "\u90FD\u5E02\u540D\u307E\u305F\u306F\u5730\u57DF\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"What.is.the.name.of.your.State.or.Province.",
                "\u90FD\u9053\u5E9C\u770C\u540D\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "\u3053\u306E\u5358\u4F4D\u306B\u8A72\u5F53\u3059\u308B2\u6587\u5B57\u306E\u56FD\u30B3\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"Is.name.correct.", "{0}\u3067\u3088\u308D\u3057\u3044\u3067\u3059\u304B\u3002"},
        {"no", "\u3044\u3044\u3048"},
        {"yes", "\u306F\u3044"},
        {"y", "y"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "\u5225\u540D<{0}>\u306B\u306F\u9375\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "\u5225\u540D<{0}>\u304C\u53C2\u7167\u3057\u3066\u3044\u308B\u30A8\u30F3\u30C8\u30EA\u30FB\u30BF\u30A4\u30D7\u306F\u79D8\u5BC6\u9375\u30A8\u30F3\u30C8\u30EA\u3067\u306F\u3042\u308A\u307E\u305B\u3093\u3002-keyclone\u30B3\u30DE\u30F3\u30C9\u306F\u79D8\u5BC6\u9375\u30A8\u30F3\u30C8\u30EA\u306E\u30AF\u30ED\u30FC\u30F3\u4F5C\u6210\u306E\u307F\u3092\u30B5\u30DD\u30FC\u30C8\u3057\u307E\u3059"},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "\u7F72\u540D\u8005\u756A\u53F7%d:"},
        {"Timestamp.", "\u30BF\u30A4\u30E0\u30B9\u30BF\u30F3\u30D7:"},
        {"Signature.", "\u7F72\u540D:"},
        {"CRLs.", "CRL:"},
        {"Certificate.owner.", "\u8A3C\u660E\u66F8\u306E\u6240\u6709\u8005: "},
        {"Not.a.signed.jar.file", "\u7F72\u540D\u4ED8\u304DJAR\u30D5\u30A1\u30A4\u30EB\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"No.certificate.from.the.SSL.server",
                "SSL\u30B5\u30FC\u30D0\u30FC\u304B\u3089\u306E\u8A3C\u660E\u66F8\u304C\u3042\u308A\u307E\u305B\u3093"},

        // Translators of the following 5 pairs, ATTENTION:
        // the next 5 string pairs are meant to be combined into 2 paragraphs,
        // 1+3+4 and 2+3+5. make sure your translation also does.
        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* \u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u4FDD\u5B58\u3055\u308C\u305F\u60C5\u5831\u306E\u6574\u5408\u6027 *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* \u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u4FDD\u5B58\u3055\u308C\u305F\u60C5\u5831\u306E\u5B8C\u5168\u6027 *"},
        {".has.NOT.been.verified.In.order.to.verify.its.integrity.",
            "* \u691C\u8A3C\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002\u6574\u5408\u6027\u3092\u691C\u8A3C\u3059\u308B\u306B\u306F  *"},
        {".you.must.provide.your.keystore.password.",
            "* \u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059\u3002        *"},
        {".you.must.provide.the.srckeystore.password.",
            "* \u30BD\u30FC\u30B9\u30FB\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059\u3002          *"},


        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "\u8A3C\u660E\u66F8\u5FDC\u7B54\u306B\u306F\u3001<{0}>\u306E\u516C\u958B\u9375\u306F\u542B\u307E\u308C\u307E\u305B\u3093"},
        {"Incomplete.certificate.chain.in.reply",
                "\u5FDC\u7B54\u3057\u305F\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u306F\u4E0D\u5B8C\u5168\u3067\u3059"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "\u5FDC\u7B54\u3057\u305F\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u306F\u691C\u8A3C\u3055\u308C\u3066\u3044\u307E\u305B\u3093: "},
        {"Top.level.certificate.in.reply.",
                "\u5FDC\u7B54\u3057\u305F\u30C8\u30C3\u30D7\u30EC\u30D9\u30EB\u306E\u8A3C\u660E\u66F8:\n"},
        {".is.not.trusted.", "... \u306F\u4FE1\u983C\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002 "},
        {"Install.reply.anyway.no.", "\u5FDC\u7B54\u3092\u30A4\u30F3\u30B9\u30C8\u30FC\u30EB\u3057\u307E\u3059\u304B\u3002[\u3044\u3044\u3048]:  "},
        {"NO", "\u3044\u3044\u3048"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "\u5FDC\u7B54\u3057\u305F\u516C\u958B\u9375\u3068\u30AD\u30FC\u30B9\u30C8\u30A2\u304C\u4E00\u81F4\u3057\u307E\u305B\u3093"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "\u8A3C\u660E\u66F8\u5FDC\u7B54\u3068\u30AD\u30FC\u30B9\u30C8\u30A2\u5185\u306E\u8A3C\u660E\u66F8\u304C\u540C\u3058\u3067\u3059"},
        {"Failed.to.establish.chain.from.reply",
                "\u5FDC\u7B54\u304B\u3089\u9023\u9396\u3092\u78BA\u7ACB\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"n", "n"},
        {"Wrong.answer.try.again", "\u5FDC\u7B54\u304C\u9593\u9055\u3063\u3066\u3044\u307E\u3059\u3002\u3082\u3046\u4E00\u5EA6\u5B9F\u884C\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "\u79D8\u5BC6\u9375\u306F\u751F\u6210\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u5225\u540D<{0}>\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Please.provide.keysize.for.secret.key.generation",
                "\u79D8\u5BC6\u9375\u306E\u751F\u6210\u6642\u306B\u306F -keysize\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"},

        {"Extensions.", "\u62E1\u5F35: "},
        {".Empty.value.", "(\u7A7A\u306E\u5024)"},
        {"Extension.Request.", "\u62E1\u5F35\u30EA\u30AF\u30A8\u30B9\u30C8:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "PKCS #10\u8A3C\u660E\u66F8\u30EA\u30AF\u30A8\u30B9\u30C8(\u30D0\u30FC\u30B8\u30E7\u30F31.0)\n\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8: %s\n\u516C\u958B\u9375: %s \u30D5\u30A9\u30FC\u30DE\u30C3\u30C8 %s \u30AD\u30FC\n"},
        {"Unknown.keyUsage.type.", "\u4E0D\u660E\u306AkeyUsage\u30BF\u30A4\u30D7: "},
        {"Unknown.extendedkeyUsage.type.", "\u4E0D\u660E\u306AextendedkeyUsage\u30BF\u30A4\u30D7: "},
        {"Unknown.AccessDescription.type.", "\u4E0D\u660E\u306AAccessDescription\u30BF\u30A4\u30D7: "},
        {"Unrecognized.GeneralName.type.", "\u8A8D\u8B58\u3055\u308C\u306A\u3044GeneralName\u30BF\u30A4\u30D7: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "\u3053\u306E\u62E1\u5F35\u306F\u30AF\u30EA\u30C6\u30A3\u30AB\u30EB\u3068\u3057\u3066\u30DE\u30FC\u30AF\u4ED8\u3051\u3067\u304D\u307E\u305B\u3093\u3002 "},
        {"Odd.number.of.hex.digits.found.", "\u5947\u6570\u306E16\u9032\u6570\u304C\u898B\u3064\u304B\u308A\u307E\u3057\u305F: "},
        {"Unknown.extension.type.", "\u4E0D\u660E\u306A\u62E1\u5F35\u30BF\u30A4\u30D7: "},
        {"command.{0}.is.ambiguous.", "\u30B3\u30DE\u30F3\u30C9{0}\u306F\u3042\u3044\u307E\u3044\u3067\u3059:"},

        // policytool
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
        {"Principal.List", "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u306E\u30EA\u30B9\u30C8"},
        {"Permission.List", "\u30A2\u30AF\u30BB\u30B9\u6A29\u306E\u30EA\u30B9\u30C8"},
        {"Code.Base", "\u30B3\u30FC\u30C9\u30FB\u30D9\u30FC\u30B9"},
        {"KeyStore.U.R.L.", "\u30AD\u30FC\u30B9\u30C8\u30A2U R L:"},
        {"KeyStore.Password.U.R.L.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D1\u30B9\u30EF\u30FC\u30C9U R L:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "null\u306E\u5165\u529B\u306F\u7121\u52B9\u3067\u3059"},
        {"actions.can.only.be.read.", "\u30A2\u30AF\u30B7\u30E7\u30F3\u306F'\u8AAD\u8FBC\u307F'\u306E\u307F\u53EF\u80FD\u3067\u3059"},
        {"permission.name.name.syntax.invalid.",
                "\u30A2\u30AF\u30BB\u30B9\u6A29\u540D[{0}]\u306E\u69CB\u6587\u304C\u7121\u52B9\u3067\u3059: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "Credential\u30AF\u30E9\u30B9\u306E\u6B21\u306BPrincipal\u30AF\u30E9\u30B9\u304A\u3088\u3073\u540D\u524D\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "Principal\u30AF\u30E9\u30B9\u306E\u6B21\u306B\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u540D\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u540D\u306F\u5F15\u7528\u7B26\u3067\u56F2\u3080\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"Principal.Name.missing.end.quote",
                "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u540D\u306E\u6700\u5F8C\u306B\u5F15\u7528\u7B26\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u540D\u304C\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9(*)\u5024\u3067\u306A\u3044\u5834\u5408\u3001PrivateCredentialPermission\u306EPrincipal\u30AF\u30E9\u30B9\u3092\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9(*)\u5024\u306B\u3059\u308B\u3053\u3068\u306F\u3067\u304D\u307E\u305B\u3093"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\tPrincipal\u30AF\u30E9\u30B9={0}\n\t\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u540D={1}"},

        // javax.security.auth.x500
        {"provided.null.name", "null\u306E\u540D\u524D\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"provided.null.keyword.map", "null\u306E\u30AD\u30FC\u30EF\u30FC\u30C9\u30FB\u30DE\u30C3\u30D7\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"provided.null.OID.map", "null\u306EOID\u30DE\u30C3\u30D7\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "\u7121\u52B9\u306Anull AccessControlContext\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"invalid.null.action.provided", "\u7121\u52B9\u306Anull\u30A2\u30AF\u30B7\u30E7\u30F3\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"invalid.null.Class.provided", "\u7121\u52B9\u306Anull\u30AF\u30E9\u30B9\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"Subject.", "\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8:\n"},
        {".Principal.", "\t\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB: "},
        {".Public.Credential.", "\t\u516C\u958B\u8CC7\u683C: "},
        {".Private.Credentials.inaccessible.",
                "\t\u975E\u516C\u958B\u8CC7\u683C\u306B\u306F\u30A2\u30AF\u30BB\u30B9\u3067\u304D\u307E\u305B\u3093\n"},
        {".Private.Credential.", "\t\u975E\u516C\u958B\u8CC7\u683C: "},
        {".Private.Credential.inaccessible.",
                "\t\u975E\u516C\u958B\u8CC7\u683C\u306B\u306F\u30A2\u30AF\u30BB\u30B9\u3067\u304D\u307E\u305B\u3093\n"},
        {"Subject.is.read.only", "\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8\u306F\u8AAD\u53D6\u308A\u5C02\u7528\u3067\u3059"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "java.security.Principal\u306E\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3067\u306F\u306A\u3044\u30AA\u30D6\u30B8\u30A7\u30AF\u30C8\u3092\u3001\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8\u306E\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u30FB\u30BB\u30C3\u30C8\u306B\u8FFD\u52A0\u3057\u3088\u3046\u3068\u3057\u307E\u3057\u305F"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "{0}\u306E\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3067\u306F\u306A\u3044\u30AA\u30D6\u30B8\u30A7\u30AF\u30C8\u3092\u8FFD\u52A0\u3057\u3088\u3046\u3068\u3057\u307E\u3057\u305F"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "\u7121\u52B9\u306Anull\u5165\u529B: \u540D\u524D"},
        {"No.LoginModules.configured.for.name",
         "{0}\u7528\u306B\u69CB\u6210\u3055\u308C\u305FLoginModules\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"invalid.null.Subject.provided", "\u7121\u52B9\u306Anull\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"invalid.null.CallbackHandler.provided",
                "\u7121\u52B9\u306Anull CallbackHandler\u304C\u6307\u5B9A\u3055\u308C\u307E\u3057\u305F"},
        {"null.subject.logout.called.before.login",
                "null\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8 - \u30ED\u30B0\u30A4\u30F3\u3059\u308B\u524D\u306B\u30ED\u30B0\u30A2\u30A6\u30C8\u304C\u547C\u3073\u51FA\u3055\u308C\u307E\u3057\u305F"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "LoginModule {0}\u306F\u5F15\u6570\u3092\u53D6\u3089\u306A\u3044\u30B3\u30F3\u30B9\u30C8\u30E9\u30AF\u30BF\u3092\u6307\u5B9A\u3067\u304D\u306A\u3044\u305F\u3081\u3001\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3092\u751F\u6210\u3067\u304D\u307E\u305B\u3093"},
        {"unable.to.instantiate.LoginModule",
                "LoginModule\u306E\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3092\u751F\u6210\u3067\u304D\u307E\u305B\u3093"},
        {"unable.to.instantiate.LoginModule.",
                "LoginModule\u306E\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3092\u751F\u6210\u3067\u304D\u307E\u305B\u3093: "},
        {"unable.to.find.LoginModule.class.",
                "LoginModule\u30AF\u30E9\u30B9\u3092\u691C\u51FA\u3067\u304D\u307E\u305B\u3093: "},
        {"unable.to.access.LoginModule.",
                "LoginModule\u306B\u30A2\u30AF\u30BB\u30B9\u3067\u304D\u307E\u305B\u3093: "},
        {"Login.Failure.all.modules.ignored",
                "\u30ED\u30B0\u30A4\u30F3\u5931\u6557: \u3059\u3079\u3066\u306E\u30E2\u30B8\u30E5\u30FC\u30EB\u306F\u7121\u8996\u3055\u308C\u307E\u3059"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: {0}\u306E\u69CB\u6587\u89E3\u6790\u30A8\u30E9\u30FC:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: \u30A2\u30AF\u30BB\u30B9\u6A29{0}\u306E\u8FFD\u52A0\u30A8\u30E9\u30FC:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: \u30A8\u30F3\u30C8\u30EA\u306E\u8FFD\u52A0\u30A8\u30E9\u30FC:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "\u5225\u540D\u306E\u6307\u5B9A\u304C\u3042\u308A\u307E\u305B\u3093({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "\u5225\u540D{0}\u306B\u5BFE\u3057\u3066\u7F6E\u63DB\u64CD\u4F5C\u304C\u3067\u304D\u307E\u305B\u3093"},
        {"substitution.value.prefix.unsupported",
                "\u7F6E\u63DB\u5024{0}\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","\u5165\u529B\u3092null\u306B\u3059\u308B\u3053\u3068\u306F\u3067\u304D\u307E\u305B\u3093"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u3092\u6307\u5B9A\u3057\u306A\u3044\u5834\u5408\u3001keystorePasswordURL\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"expected.keystore.type", "\u4E88\u60F3\u3055\u308C\u305F\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30BF\u30A4\u30D7"},
        {"expected.keystore.provider", "\u4E88\u60F3\u3055\u308C\u305F\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30D7\u30ED\u30D0\u30A4\u30C0"},
        {"multiple.Codebase.expressions",
                "\u8907\u6570\u306ECodebase\u5F0F"},
        {"multiple.SignedBy.expressions","\u8907\u6570\u306ESignedBy\u5F0F"},
        {"SignedBy.has.empty.alias","SignedBy\u306F\u7A7A\u306E\u5225\u540D\u3092\u4FDD\u6301\u3057\u307E\u3059"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9\u540D\u306E\u306A\u3044\u30EF\u30A4\u30EB\u30C9\u30AB\u30FC\u30C9\u30FB\u30AF\u30E9\u30B9\u3092\u4F7F\u7528\u3057\u3066\u3001\u30D7\u30EA\u30F3\u30B7\u30D1\u30EB\u3092\u6307\u5B9A\u3059\u308B\u3053\u3068\u306F\u3067\u304D\u307E\u305B\u3093"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "\u4E88\u60F3\u3055\u308C\u305FcodeBase\u3001SignedBy\u307E\u305F\u306FPrincipal"},
        {"expected.permission.entry", "\u4E88\u60F3\u3055\u308C\u305F\u30A2\u30AF\u30BB\u30B9\u6A29\u30A8\u30F3\u30C8\u30EA"},
        {"number.", "\u6570 "},
        {"expected.expect.read.end.of.file.",
                "[{0}]\u3067\u306F\u306A\u304F[\u30D5\u30A1\u30A4\u30EB\u306E\u7D42\u308F\u308A]\u304C\u8AAD\u307F\u8FBC\u307E\u308C\u307E\u3057\u305F"},
        {"expected.read.end.of.file.",
                "[;]\u3067\u306F\u306A\u304F[\u30D5\u30A1\u30A4\u30EB\u306E\u7D42\u308F\u308A]\u304C\u8AAD\u307F\u8FBC\u307E\u308C\u307E\u3057\u305F"},
        {"line.number.msg", "\u884C{0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "\u884C{0}: [{1}]\u3067\u306F\u306A\u304F[{2}]\u304C\u691C\u51FA\u3055\u308C\u307E\u3057\u305F"},
        {"null.principalClass.or.principalName",
                "null\u306EprincipalClass\u307E\u305F\u306FprincipalName"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "PKCS11\u30C8\u30FC\u30AF\u30F3[{0}]\u30D1\u30B9\u30EF\u30FC\u30C9: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "\u30B5\u30D6\u30B8\u30A7\u30AF\u30C8\u30FB\u30D9\u30FC\u30B9\u306E\u30DD\u30EA\u30B7\u30FC\u306E\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3092\u751F\u6210\u3067\u304D\u307E\u305B\u3093"}
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

