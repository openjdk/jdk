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

package sun.security.tools;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for JarSigner.
 *
 */
public class JarSignerResources_ja extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // shared (from jarsigner)
        {"SPACE", " "},
        {"2SPACE", "  "},
        {"6SPACE", "      "},
        {"COMMA", ", "},

        {"provName.not.a.provider", "{0}\u306F\u30D7\u30ED\u30D0\u30A4\u30C0\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"signerClass.is.not.a.signing.mechanism", "{0}\u306F\u7F72\u540D\u30E1\u30AB\u30CB\u30BA\u30E0\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"jarsigner.error.", "jarsigner\u30A8\u30E9\u30FC: "},
        {"Illegal.option.", "\u4E0D\u6B63\u306A\u30AA\u30D7\u30B7\u30E7\u30F3: "},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-storetype\u304C{0}\u306E\u5834\u5408\u3001-keystore\u306FNONE\u3067\u3042\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {".keypass.can.not.be.specified.if.storetype.is.{0}",
                "-storetype\u304C{0}\u306E\u5834\u5408\u3001-keypass\u306F\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093"},
        {"If.protected.is.specified.then.storepass.and.keypass.must.not.be.specified",
                "-protected\u3092\u6307\u5B9A\u3059\u308B\u5834\u5408\u306F\u3001-storepass\u304A\u3088\u3073-keypass\u3092\u6307\u5B9A\u3057\u306A\u3044\u3067\u304F\u3060\u3055\u3044"},
        {"If.keystore.is.not.password.protected.then.storepass.and.keypass.must.not.be.specified",
                 "\u30AD\u30FC\u30B9\u30C8\u30A2\u304C\u30D1\u30B9\u30EF\u30FC\u30C9\u3067\u4FDD\u8B77\u3055\u308C\u3066\u3044\u306A\u3044\u5834\u5408\u3001-storepass\u304A\u3088\u3073-keypass\u3092\u6307\u5B9A\u3057\u306A\u3044\u3067\u304F\u3060\u3055\u3044"},
        {"Usage.jarsigner.options.jar.file.alias",
                "\u4F7F\u7528\u65B9\u6CD5: jarsigner [options] jar-file alias"},
        {".jarsigner.verify.options.jar.file.alias.",
                "       jarsigner -verify [options] jar-file [alias...]"},
        {".keystore.url.keystore.location",
                "[-keystore <url>]       \u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u4F4D\u7F6E"},
        {".storepass.password.password.for.keystore.integrity",
            "[-storepass <password>]   \u30AD\u30FC\u30B9\u30C8\u30A2\u6574\u5408\u6027\u306E\u305F\u3081\u306E\u30D1\u30B9\u30EF\u30FC\u30C9"},
        {".storetype.type.keystore.type",
                "[-storetype <type>]     \u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u578B"},
        {".keypass.password.password.for.private.key.if.different.",
                "[-keypass <password>]    \u79D8\u5BC6\u9375\u306E\u30D1\u30B9\u30EF\u30FC\u30C9(\u7570\u306A\u308B\u5834\u5408)"},
        {".certchain.file.name.of.alternative.certchain.file",
                "[-certchain <file>]         \u4EE3\u66FF\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u30FB\u30D5\u30A1\u30A4\u30EB\u306E\u540D\u524D"},
        {".sigfile.file.name.of.SF.DSA.file",
                "[-sigfile <file>]       .SF/.DSA\u30D5\u30A1\u30A4\u30EB\u306E\u540D\u524D"},
        {".signedjar.file.name.of.signed.JAR.file",
                "[-signedjar <file>]     \u7F72\u540D\u4ED8\u304DJAR\u30D5\u30A1\u30A4\u30EB\u306E\u540D\u524D"},
        {".digestalg.algorithm.name.of.digest.algorithm",
                "[-digestalg <algorithm>]  \u30C0\u30A4\u30B8\u30A7\u30B9\u30C8\u30FB\u30A2\u30EB\u30B4\u30EA\u30BA\u30E0\u306E\u540D\u524D"},
        {".sigalg.algorithm.name.of.signature.algorithm",
                "[-sigalg <algorithm>]    \u7F72\u540D\u30A2\u30EB\u30B4\u30EA\u30BA\u30E0\u306E\u540D\u524D"},
        {".crl.auto.file.include.CRL.in.signed.jar",
                "[-crl[:auto| <file>]        \u7F72\u540D\u4ED8\u304Djar\u306BCRL\u3092\u542B\u3081\u308B"},
        {".verify.verify.a.signed.JAR.file",
                "[-verify]            \u7F72\u540D\u4ED8\u304DJAR\u30D5\u30A1\u30A4\u30EB\u306E\u691C\u8A3C"},
        {".verbose.suboptions.verbose.output.when.signing.verifying.",
                "[-verbose[:suboptions]]     \u7F72\u540D/\u691C\u8A3C\u6642\u306E\u8A73\u7D30\u51FA\u529B\u3002"},
        {".suboptions.can.be.all.grouped.or.summary",
                "                            \u30B5\u30D6\u30AA\u30D7\u30B7\u30E7\u30F3\u3068\u3057\u3066\u3001\u3059\u3079\u3066\u3001\u30B0\u30EB\u30FC\u30D7\u307E\u305F\u306F\u30B5\u30DE\u30EA\u30FC\u3092\u4F7F\u7528\u3067\u304D\u307E\u3059"},
        {".certs.display.certificates.when.verbose.and.verifying",
                "[-certs]             \u8A73\u7D30\u51FA\u529B\u304A\u3088\u3073\u691C\u8A3C\u6642\u306B\u8A3C\u660E\u66F8\u3092\u8868\u793A"},
        {".tsa.url.location.of.the.Timestamping.Authority",
                "[-tsa <url>]          \u30BF\u30A4\u30E0\u30B9\u30BF\u30F3\u30D7\u5C40\u306E\u5834\u6240"},
        {".tsacert.alias.public.key.certificate.for.Timestamping.Authority",
                "[-tsacert <alias>]      \u30BF\u30A4\u30E0\u30B9\u30BF\u30F3\u30D7\u5C40\u306E\u516C\u958B\u9375\u8A3C\u660E\u66F8"},
        {".altsigner.class.class.name.of.an.alternative.signing.mechanism",
                "[-altsigner <class>]     \u4EE3\u66FF\u7F72\u540D\u30E1\u30AB\u30CB\u30BA\u30E0\u306E\u30AF\u30E9\u30B9\u540D"},
        {".altsignerpath.pathlist.location.of.an.alternative.signing.mechanism",
                "[-altsignerpath <pathlist>]\u4EE3\u66FF\u7F72\u540D\u30E1\u30AB\u30CB\u30BA\u30E0\u306E\u4F4D\u7F6E"},
        {".internalsf.include.the.SF.file.inside.the.signature.block",
                "[-internalsf]         \u7F72\u540D\u30D6\u30ED\u30C3\u30AF\u306B.SF\u30D5\u30A1\u30A4\u30EB\u3092\u542B\u3081\u308B"},
        {".sectionsonly.don.t.compute.hash.of.entire.manifest",
                "[-sectionsonly]        \u30DE\u30CB\u30D5\u30A7\u30B9\u30C8\u5168\u4F53\u306E\u30CF\u30C3\u30B7\u30E5\u306F\u8A08\u7B97\u3057\u306A\u3044"},
        {".protected.keystore.has.protected.authentication.path",
                "[-protected]          \u30AD\u30FC\u30B9\u30C8\u30A2\u306B\u306F\u4FDD\u8B77\u3055\u308C\u305F\u8A8D\u8A3C\u30D1\u30B9\u304C\u3042\u308B"},
        {".providerName.name.provider.name",
                "[-providerName <name>]   \u30D7\u30ED\u30D0\u30A4\u30C0\u540D"},
        {".providerClass.class.name.of.cryptographic.service.provider.s",
                "[-providerClass <class>   \u6697\u53F7\u5316\u30B5\u30FC\u30D3\u30B9\u30FB\u30D7\u30ED\u30D0\u30A4\u30C0\u306E\u540D\u524D"},
        {".providerArg.arg.master.class.file.and.constructor.argument",
                "  [-providerArg <arg>]] ... \u30DE\u30B9\u30BF\u30FC\u30FB\u30AF\u30E9\u30B9\u30FB\u30D5\u30A1\u30A4\u30EB\u3068\u30B3\u30F3\u30B9\u30C8\u30E9\u30AF\u30BF\u306E\u5F15\u6570"},
        {".strict.treat.warnings.as.errors",
                "[-strict]                   \u8B66\u544A\u3092\u30A8\u30E9\u30FC\u3068\u3057\u3066\u51E6\u7406"},
        {"Option.lacks.argument", "\u30AA\u30D7\u30B7\u30E7\u30F3\u306B\u5F15\u6570\u304C\u3042\u308A\u307E\u305B\u3093"},
        {"Please.type.jarsigner.help.for.usage", "\u4F7F\u7528\u65B9\u6CD5\u306B\u3064\u3044\u3066\u306Fjarsigner -help\u3068\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Please.specify.jarfile.name", "jarfile\u540D\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Please.specify.alias.name", "\u5225\u540D\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Only.one.alias.can.be.specified", "\u5225\u540D\u306F1\u3064\u306E\u307F\u6307\u5B9A\u3067\u304D\u307E\u3059"},
        {"This.jar.contains.signed.entries.which.is.not.signed.by.the.specified.alias.es.",
                 "\u3053\u306Ejar\u306B\u542B\u307E\u308C\u308B\u7F72\u540D\u6E08\u30A8\u30F3\u30C8\u30EA\u306F\u3001\u6307\u5B9A\u3055\u308C\u305F\u5225\u540D\u306B\u3088\u3063\u3066\u7F72\u540D\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002"},
        {"This.jar.contains.signed.entries.that.s.not.signed.by.alias.in.this.keystore.",
                  "\u3053\u306Ejar\u306B\u542B\u307E\u308C\u308B\u7F72\u540D\u6E08\u30A8\u30F3\u30C8\u30EA\u306F\u3001\u3053\u306E\u30AD\u30FC\u30B9\u30C8\u30A2\u5185\u306E\u5225\u540D\u306B\u3088\u3063\u3066\u7F72\u540D\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002"},
        {"s", "s"},
        {"m", "m"},
        {"k", "k"},
        {"i", "i"},
        {".and.d.more.", "(\u4ED6\u306B\u3082%d\u500B)"},
        {".s.signature.was.verified.",
                "  s=\u7F72\u540D\u304C\u691C\u8A3C\u3055\u308C\u307E\u3057\u305F "},
        {".m.entry.is.listed.in.manifest",
                "  m=\u30A8\u30F3\u30C8\u30EA\u304C\u30DE\u30CB\u30D5\u30A7\u30B9\u30C8\u5185\u306B\u30EA\u30B9\u30C8\u3055\u308C\u307E\u3059"},
        {".k.at.least.one.certificate.was.found.in.keystore",
                "  k=1\u3064\u4EE5\u4E0A\u306E\u8A3C\u660E\u66F8\u304C\u30AD\u30FC\u30B9\u30C8\u30A2\u3067\u691C\u51FA\u3055\u308C\u307E\u3057\u305F"},
        {".i.at.least.one.certificate.was.found.in.identity.scope",
                "  i=1\u3064\u4EE5\u4E0A\u306E\u8A3C\u660E\u66F8\u304C\u30A2\u30A4\u30C7\u30F3\u30C6\u30A3\u30C6\u30A3\u30FB\u30B9\u30B3\u30FC\u30D7\u3067\u691C\u51FA\u3055\u308C\u307E\u3057\u305F"},
        {".X.not.signed.by.specified.alias.es.",
                "  X =\u6307\u5B9A\u3057\u305F\u5225\u540D\u3067\u7F72\u540D\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"no.manifest.", "\u30DE\u30CB\u30D5\u30A7\u30B9\u30C8\u306F\u5B58\u5728\u3057\u307E\u305B\u3093\u3002"},
        {".Signature.related.entries.","(\u7F72\u540D\u95A2\u9023\u30A8\u30F3\u30C8\u30EA)"},
        {".Unsigned.entries.", "(\u672A\u7F72\u540D\u306E\u30A8\u30F3\u30C8\u30EA)"},
        {"jar.is.unsigned.signatures.missing.or.not.parsable.",
                "jar\u306F\u7F72\u540D\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002(\u7F72\u540D\u304C\u898B\u3064\u304B\u3089\u306A\u3044\u304B\u3001\u69CB\u6587\u89E3\u6790\u3067\u304D\u307E\u305B\u3093)"},
        {"jar.verified.", "jar\u304C\u691C\u8A3C\u3055\u308C\u307E\u3057\u305F\u3002"},
        {"jarsigner.", "jarsigner: "},
        {"signature.filename.must.consist.of.the.following.characters.A.Z.0.9.or.",
                "\u7F72\u540D\u306E\u30D5\u30A1\u30A4\u30EB\u540D\u306B\u4F7F\u7528\u3067\u304D\u308B\u6587\u5B57\u306F\u3001A-Z\u30010-9\u3001_\u3001- \u306E\u307F\u3067\u3059\u3002"},
        {"unable.to.open.jar.file.", "\u6B21\u306Ejar\u30D5\u30A1\u30A4\u30EB\u3092\u958B\u304F\u3053\u3068\u304C\u3067\u304D\u307E\u305B\u3093: "},
        {"unable.to.create.", "\u4F5C\u6210\u3067\u304D\u307E\u305B\u3093: "},
        {".adding.", "   \u8FFD\u52A0\u4E2D: "},
        {".updating.", " \u66F4\u65B0\u4E2D: "},
        {".signing.", "  \u7F72\u540D\u4E2D: "},
        {"attempt.to.rename.signedJarFile.to.jarFile.failed",
                "{0}\u306E\u540D\u524D\u3092{1}\u306B\u5909\u66F4\u3057\u3088\u3046\u3068\u3057\u307E\u3057\u305F\u304C\u5931\u6557\u3057\u307E\u3057\u305F"},
        {"attempt.to.rename.jarFile.to.origJar.failed",
                "{0}\u306E\u540D\u524D\u3092{1}\u306B\u5909\u66F4\u3057\u3088\u3046\u3068\u3057\u307E\u3057\u305F\u304C\u5931\u6557\u3057\u307E\u3057\u305F"},
        {"unable.to.sign.jar.", "jar\u306B\u7F72\u540D\u3067\u304D\u307E\u305B\u3093: "},
        {"Enter.Passphrase.for.keystore.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044: "},
        {"keystore.load.", "\u30AD\u30FC\u30B9\u30C8\u30A2\u306E\u30ED\u30FC\u30C9: "},
        {"certificate.exception.", "\u8A3C\u660E\u66F8\u4F8B\u5916: "},
        {"unable.to.instantiate.keystore.class.",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u30FB\u30AF\u30E9\u30B9\u306E\u30A4\u30F3\u30B9\u30BF\u30F3\u30B9\u3092\u751F\u6210\u3067\u304D\u307E\u305B\u3093: "},
        {"Certificate.chain.not.found.for.alias.alias.must.reference.a.valid.KeyStore.key.entry.containing.a.private.key.and",
                "\u6B21\u306E\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u304C\u898B\u3064\u304B\u308A\u307E\u305B\u3093: {0}\u3002{1}\u306F\u3001\u79D8\u5BC6\u9375\u304A\u3088\u3073\u5BFE\u5FDC\u3059\u308B\u516C\u958B\u9375\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u3092\u542B\u3080\u6709\u52B9\u306AKeyStore\u9375\u30A8\u30F3\u30C8\u30EA\u3092\u53C2\u7167\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059\u3002"},
        {"File.specified.by.certchain.does.not.exist",
                "-certchain\u3067\u6307\u5B9A\u3055\u308C\u3066\u3044\u308B\u30D5\u30A1\u30A4\u30EB\u306F\u5B58\u5728\u3057\u307E\u305B\u3093"},
        {"Cannot.restore.certchain.from.file.specified",
                "\u6307\u5B9A\u3055\u308C\u305F\u30D5\u30A1\u30A4\u30EB\u304B\u3089\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u3092\u5FA9\u5143\u3067\u304D\u307E\u305B\u3093"},
        {"Certificate.chain.not.found.in.the.file.specified.",
                "\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u306F\u6307\u5B9A\u3055\u308C\u305F\u30D5\u30A1\u30A4\u30EB\u306B\u898B\u3064\u304B\u308A\u307E\u305B\u3093\u3002"},
        {"found.non.X.509.certificate.in.signer.s.chain",
                "\u7F72\u540D\u8005\u306E\u9023\u9396\u5185\u3067\u975EX.509\u8A3C\u660E\u66F8\u304C\u691C\u51FA\u3055\u308C\u307E\u3057\u305F"},
        {"incomplete.certificate.chain", "\u4E0D\u5B8C\u5168\u306A\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3"},
        {"Enter.key.password.for.alias.", "{0}\u306E\u9375\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3057\u3066\u304F\u3060\u3055\u3044: "},
        {"unable.to.recover.key.from.keystore",
                "\u30AD\u30FC\u30B9\u30C8\u30A2\u304B\u3089\u9375\u3092\u5FA9\u5143\u3067\u304D\u307E\u305B\u3093"},
        {"key.associated.with.alias.not.a.private.key",
                "{0}\u3068\u95A2\u9023\u4ED8\u3051\u3089\u308C\u305F\u9375\u306F\u3001\u79D8\u5BC6\u9375\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"you.must.enter.key.password", "\u9375\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u5165\u529B\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059"},
        {"unable.to.read.password.", "\u30D1\u30B9\u30EF\u30FC\u30C9\u3092\u8AAD\u307F\u8FBC\u3081\u307E\u305B\u3093: "},
        {"certificate.is.valid.from", "\u8A3C\u660E\u66F8\u306F{0}\u304B\u3089{1}\u307E\u3067\u6709\u52B9\u3067\u3059"},
        {"certificate.expired.on", "\u8A3C\u660E\u66F8\u306F{0}\u306B\u5931\u52B9\u3057\u307E\u3057\u305F"},
        {"certificate.is.not.valid.until",
                "\u8A3C\u660E\u66F8\u306F{0}\u307E\u3067\u6709\u52B9\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"certificate.will.expire.on", "\u8A3C\u660E\u66F8\u306F{0}\u306B\u5931\u52B9\u3057\u307E\u3059"},
        {".CertPath.not.validated.", "[CertPath\u304C\u691C\u8A3C\u3055\u308C\u3066\u3044\u307E\u305B\u3093: "},
        {"requesting.a.signature.timestamp",
                "\u7F72\u540D\u30BF\u30A4\u30E0\u30B9\u30BF\u30F3\u30D7\u306E\u30EA\u30AF\u30A8\u30B9\u30C8"},
        {"TSA.location.", "TSA\u306E\u5834\u6240: "},
        {"TSA.certificate.", "TSA\u8A3C\u660E\u66F8: "},
        {"no.response.from.the.Timestamping.Authority.",
                "\u30BF\u30A4\u30E0\u30B9\u30BF\u30F3\u30D7\u5C40\u304B\u3089\u306E\u30EC\u30B9\u30DD\u30F3\u30B9\u304C\u3042\u308A\u307E\u305B\u3093\u3002 "},
        {"When.connecting.from.behind.a.firewall.then.an.HTTP.proxy.may.need.to.be.specified.",
                "\u30D5\u30A1\u30A4\u30A2\u30A6\u30A9\u30FC\u30EB\u3092\u4ECB\u3057\u3066\u63A5\u7D9A\u3059\u308B\u3068\u304D\u306F\u3001\u5FC5\u8981\u306B\u5FDC\u3058\u3066HTTP\u30D7\u30ED\u30AD\u30B7\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044\u3002 "},
        {"Supply.the.following.options.to.jarsigner.",
                "jarsigner\u306B\u6B21\u306E\u30AA\u30D7\u30B7\u30E7\u30F3\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044: "},
        {"Certificate.not.found.for.alias.alias.must.reference.a.valid.KeyStore.entry.containing.an.X.509.public.key.certificate.for.the",
                "\u8A3C\u660E\u66F8\u304C\u898B\u3064\u304B\u308A\u307E\u305B\u3093\u3067\u3057\u305F: {0}\u3002{1}\u306F\u30BF\u30A4\u30E0\u30B9\u30BF\u30F3\u30D7\u5C40\u306EX.509\u516C\u958B\u9375\u8A3C\u660E\u66F8\u304C\u542B\u307E\u308C\u3066\u3044\u308B\u6709\u52B9\u306AKeyStore\u30A8\u30F3\u30C8\u30EA\u3092\u53C2\u7167\u3059\u308B\u5FC5\u8981\u304C\u3042\u308A\u307E\u3059\u3002"},
        {"using.an.alternative.signing.mechanism",
                "\u4EE3\u66FF\u7F72\u540D\u30E1\u30AB\u30CB\u30BA\u30E0\u306E\u4F7F\u7528"},
        {"entry.was.signed.on", "\u30A8\u30F3\u30C8\u30EA\u306F{0}\u306B\u7F72\u540D\u3055\u308C\u307E\u3057\u305F"},
        {"with.a.CRL.including.d.entries", "%d\u500B\u306E\u30A8\u30F3\u30C8\u30EA\u3092\u542B\u3080CRL\u3092\u4F7F\u7528"},
        {"Warning.", "\u8B66\u544A: "},
        {"This.jar.contains.unsigned.entries.which.have.not.been.integrity.checked.",
                "\u3053\u306Ejar\u306B\u306F\u3001\u6574\u5408\u6027\u30C1\u30A7\u30C3\u30AF\u3092\u3057\u3066\u3044\u306A\u3044\u672A\u7F72\u540D\u306E\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002 "},
        {"This.jar.contains.entries.whose.signer.certificate.has.expired.",
                "\u3053\u306Ejar\u306B\u306F\u3001\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u304C\u671F\u9650\u5207\u308C\u306E\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002 "},
        {"This.jar.contains.entries.whose.signer.certificate.will.expire.within.six.months.",
                "\u3053\u306Ejar\u306B\u306F\u3001\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u304C6\u304B\u6708\u4EE5\u5185\u306B\u671F\u9650\u5207\u308C\u3068\u306A\u308B\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002 "},
        {"This.jar.contains.entries.whose.signer.certificate.is.not.yet.valid.",
                "\u3053\u306Ejar\u306B\u306F\u3001\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u304C\u307E\u3060\u6709\u52B9\u306B\u306A\u3063\u3066\u3044\u306A\u3044\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002 "},
        {"Re.run.with.the.verbose.option.for.more.details.",
                "\u8A73\u7D30\u306F\u3001-verbose\u30AA\u30D7\u30B7\u30E7\u30F3\u3092\u4F7F\u7528\u3057\u3066\u518D\u5B9F\u884C\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"Re.run.with.the.verbose.and.certs.options.for.more.details.",
                "\u8A73\u7D30\u306F\u3001-verbose\u304A\u3088\u3073-certs\u30AA\u30D7\u30B7\u30E7\u30F3\u3092\u4F7F\u7528\u3057\u3066\u518D\u5B9F\u884C\u3057\u3066\u304F\u3060\u3055\u3044\u3002"},
        {"The.signer.certificate.has.expired.",
                "\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u306F\u671F\u9650\u5207\u308C\u3067\u3059\u3002"},
        {"The.signer.certificate.will.expire.within.six.months.",
                "\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u306F6\u304B\u6708\u4EE5\u5185\u306B\u671F\u9650\u5207\u308C\u306B\u306A\u308A\u307E\u3059\u3002"},
        {"The.signer.certificate.is.not.yet.valid.",
                "\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u306F\u307E\u3060\u6709\u52B9\u306B\u306A\u3063\u3066\u3044\u307E\u305B\u3093\u3002"},
        {"The.signer.certificate.s.KeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u7F72\u540D\u8005\u8A3C\u660E\u66F8\u306EKeyUsage\u62E1\u5F35\u6A5F\u80FD\u3067\u306F\u3001\u30B3\u30FC\u30C9\u7F72\u540D\u306F\u8A31\u53EF\u3055\u308C\u307E\u305B\u3093\u3002"},
        {"The.signer.certificate.s.ExtendedKeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u7F72\u540D\u8005\u8A3C\u660E\u66F8\u306EExtendedKeyUsage\u62E1\u5F35\u6A5F\u80FD\u3067\u306F\u3001\u30B3\u30FC\u30C9\u7F72\u540D\u306F\u8A31\u53EF\u3055\u308C\u307E\u305B\u3093\u3002"},
        {"The.signer.certificate.s.NetscapeCertType.extension.doesn.t.allow.code.signing.",
                 "\u7F72\u540D\u8005\u8A3C\u660E\u66F8\u306ENetscapeCertType\u62E1\u5F35\u6A5F\u80FD\u3067\u306F\u3001\u30B3\u30FC\u30C9\u7F72\u540D\u306F\u8A31\u53EF\u3055\u308C\u307E\u305B\u3093\u3002"},
        {"This.jar.contains.entries.whose.signer.certificate.s.KeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u3053\u306Ejar\u306B\u306F\u3001\u7F72\u540D\u8005\u8A3C\u660E\u66F8\u306EKeyUsage\u62E1\u5F35\u6A5F\u80FD\u304C\u30B3\u30FC\u30C9\u7F72\u540D\u3092\u8A31\u53EF\u3057\u306A\u3044\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002"},
        {"This.jar.contains.entries.whose.signer.certificate.s.ExtendedKeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u3053\u306Ejar\u306B\u306F\u3001\u7F72\u540D\u8005\u8A3C\u660E\u66F8\u306EExtendedKeyUsage\u62E1\u5F35\u6A5F\u80FD\u304C\u30B3\u30FC\u30C9\u7F72\u540D\u3092\u8A31\u53EF\u3057\u306A\u3044\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002"},
        {"This.jar.contains.entries.whose.signer.certificate.s.NetscapeCertType.extension.doesn.t.allow.code.signing.",
                 "\u3053\u306Ejar\u306B\u306F\u3001\u7F72\u540D\u8005\u8A3C\u660E\u66F8\u306ENetscapeCertType\u62E1\u5F35\u6A5F\u80FD\u304C\u30B3\u30FC\u30C9\u7F72\u540D\u3092\u8A31\u53EF\u3057\u306A\u3044\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002"},
        {".{0}.extension.does.not.support.code.signing.",
                 "[{0}\u62E1\u5F35\u6A5F\u80FD\u306F\u30B3\u30FC\u30C9\u7F72\u540D\u3092\u30B5\u30DD\u30FC\u30C8\u3057\u3066\u3044\u307E\u305B\u3093]"},
        {"The.signer.s.certificate.chain.is.not.validated.",
                "\u7F72\u540D\u8005\u306E\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u304C\u307E\u3060\u691C\u8A3C\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002"},
        {"This.jar.contains.entries.whose.certificate.chain.is.not.validated.",
                 "\u3053\u306Ejar\u306B\u306F\u3001\u8A3C\u660E\u66F8\u30C1\u30A7\u30FC\u30F3\u304C\u307E\u3060\u691C\u8A3C\u3055\u308C\u3066\u3044\u306A\u3044\u30A8\u30F3\u30C8\u30EA\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059\u3002"},
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
