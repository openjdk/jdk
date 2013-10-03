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

package sun.security.tools.jarsigner;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for JarSigner.
 *
 */
public class Resources_zh_CN extends java.util.ListResourceBundle {

    private static final Object[][] contents = {

        // shared (from jarsigner)
        {"SPACE", " "},
        {"2SPACE", "  "},
        {"6SPACE", "      "},
        {"COMMA", ", "},

        {"provName.not.a.provider", "{0}\u4E0D\u662F\u63D0\u4F9B\u65B9"},
        {"signerClass.is.not.a.signing.mechanism", "{0}\u4E0D\u662F\u7B7E\u540D\u673A\u5236"},
        {"jarsigner.error.", "jarsigner \u9519\u8BEF: "},
        {"Illegal.option.", "\u975E\u6CD5\u9009\u9879: "},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u4E3A {0}, \u5219 -keystore \u5FC5\u987B\u4E3A NONE"},
        {".keypass.can.not.be.specified.if.storetype.is.{0}",
                "\u5982\u679C -storetype \u4E3A {0}, \u5219\u4E0D\u80FD\u6307\u5B9A -keypass"},
        {"If.protected.is.specified.then.storepass.and.keypass.must.not.be.specified",
                "\u5982\u679C\u6307\u5B9A\u4E86 -protected, \u5219\u4E0D\u80FD\u6307\u5B9A -storepass \u548C -keypass"},
        {"If.keystore.is.not.password.protected.then.storepass.and.keypass.must.not.be.specified",
                 "\u5982\u679C\u5BC6\u94A5\u5E93\u672A\u53D7\u53E3\u4EE4\u4FDD\u62A4, \u5219\u4E0D\u80FD\u6307\u5B9A -storepass \u548C -keypass"},
        {"Usage.jarsigner.options.jar.file.alias",
                "\u7528\u6CD5: jarsigner [\u9009\u9879] jar \u6587\u4EF6\u522B\u540D"},
        {".jarsigner.verify.options.jar.file.alias.",
                "       jarsigner -verify [options] jar-file [alias...]"},
        {".keystore.url.keystore.location",
                "[-keystore <url>]           \u5BC6\u94A5\u5E93\u4F4D\u7F6E"},
        {".storepass.password.password.for.keystore.integrity",
            "[-storepass <\u53E3\u4EE4>]         \u7528\u4E8E\u5BC6\u94A5\u5E93\u5B8C\u6574\u6027\u7684\u53E3\u4EE4"},
        {".storetype.type.keystore.type",
                "[-storetype <\u7C7B\u578B>]         \u5BC6\u94A5\u5E93\u7C7B\u578B"},
        {".keypass.password.password.for.private.key.if.different.",
                "[-keypass <\u53E3\u4EE4>]           \u79C1\u6709\u5BC6\u94A5\u7684\u53E3\u4EE4 (\u5982\u679C\u4E0D\u540C)"},
        {".certchain.file.name.of.alternative.certchain.file",
                "[-certchain <file>]         \u66FF\u4EE3 certchain \u6587\u4EF6\u7684\u540D\u79F0"},
        {".sigfile.file.name.of.SF.DSA.file",
                "[-sigfile <\u6587\u4EF6>]           .SF/.DSA \u6587\u4EF6\u7684\u540D\u79F0"},
        {".signedjar.file.name.of.signed.JAR.file",
                "[-signedjar <\u6587\u4EF6>]         \u5DF2\u7B7E\u540D\u7684 JAR \u6587\u4EF6\u7684\u540D\u79F0"},
        {".digestalg.algorithm.name.of.digest.algorithm",
                "[-digestalg <\u7B97\u6CD5>]        \u6458\u8981\u7B97\u6CD5\u7684\u540D\u79F0"},
        {".sigalg.algorithm.name.of.signature.algorithm",
                "[-sigalg <\u7B97\u6CD5>]           \u7B7E\u540D\u7B97\u6CD5\u7684\u540D\u79F0"},
        {".verify.verify.a.signed.JAR.file",
                "[-verify]                   \u9A8C\u8BC1\u5DF2\u7B7E\u540D\u7684 JAR \u6587\u4EF6"},
        {".verbose.suboptions.verbose.output.when.signing.verifying.",
                "[-verbose[:suboptions]]     \u7B7E\u540D/\u9A8C\u8BC1\u65F6\u8F93\u51FA\u8BE6\u7EC6\u4FE1\u606F\u3002"},
        {".suboptions.can.be.all.grouped.or.summary",
                "                            \u5B50\u9009\u9879\u53EF\u4EE5\u662F all, grouped \u6216 summary"},
        {".certs.display.certificates.when.verbose.and.verifying",
                "[-certs]                    \u8F93\u51FA\u8BE6\u7EC6\u4FE1\u606F\u548C\u9A8C\u8BC1\u65F6\u663E\u793A\u8BC1\u4E66"},
        {".tsa.url.location.of.the.Timestamping.Authority",
                "[-tsa <url>]                \u65F6\u95F4\u6233\u9881\u53D1\u673A\u6784\u7684\u4F4D\u7F6E"},
        {".tsacert.alias.public.key.certificate.for.Timestamping.Authority",
                "[-tsacert <\u522B\u540D>]           \u65F6\u95F4\u6233\u9881\u53D1\u673A\u6784\u7684\u516C\u5171\u5BC6\u94A5\u8BC1\u4E66"},
        {".tsapolicyid.tsapolicyid.for.Timestamping.Authority",
                "[-tsapolicyid <oid>]        \u65F6\u95F4\u6233\u9881\u53D1\u673A\u6784\u7684 TSAPolicyID"},
        {".altsigner.class.class.name.of.an.alternative.signing.mechanism",
                "[-altsigner <\u7C7B>]           \u66FF\u4EE3\u7684\u7B7E\u540D\u673A\u5236\u7684\u7C7B\u540D"},
        {".altsignerpath.pathlist.location.of.an.alternative.signing.mechanism",
                "[-altsignerpath <\u8DEF\u5F84\u5217\u8868>] \u66FF\u4EE3\u7684\u7B7E\u540D\u673A\u5236\u7684\u4F4D\u7F6E"},
        {".internalsf.include.the.SF.file.inside.the.signature.block",
                "[-internalsf]               \u5728\u7B7E\u540D\u5757\u5185\u5305\u542B .SF \u6587\u4EF6"},
        {".sectionsonly.don.t.compute.hash.of.entire.manifest",
                "[-sectionsonly]             \u4E0D\u8BA1\u7B97\u6574\u4E2A\u6E05\u5355\u7684\u6563\u5217"},
        {".protected.keystore.has.protected.authentication.path",
                "[-protected]                \u5BC6\u94A5\u5E93\u5177\u6709\u53D7\u4FDD\u62A4\u9A8C\u8BC1\u8DEF\u5F84"},
        {".providerName.name.provider.name",
                "[-providerName <\u540D\u79F0>]      \u63D0\u4F9B\u65B9\u540D\u79F0"},
        {".providerClass.class.name.of.cryptographic.service.provider.s",
                "[-providerClass <\u7C7B>        \u52A0\u5BC6\u670D\u52A1\u63D0\u4F9B\u65B9\u7684\u540D\u79F0"},
        {".providerArg.arg.master.class.file.and.constructor.argument",
                "  [-providerArg <\u53C2\u6570>]]... \u4E3B\u7C7B\u6587\u4EF6\u548C\u6784\u9020\u5668\u53C2\u6570"},
        {".strict.treat.warnings.as.errors",
                "[-strict]                   \u5C06\u8B66\u544A\u89C6\u4E3A\u9519\u8BEF"},
        {"Option.lacks.argument", "\u9009\u9879\u7F3A\u5C11\u53C2\u6570"},
        {"Please.type.jarsigner.help.for.usage", "\u8BF7\u952E\u5165 jarsigner -help \u4EE5\u4E86\u89E3\u7528\u6CD5"},
        {"Please.specify.jarfile.name", "\u8BF7\u6307\u5B9A jarfile \u540D\u79F0"},
        {"Please.specify.alias.name", "\u8BF7\u6307\u5B9A\u522B\u540D"},
        {"Only.one.alias.can.be.specified", "\u53EA\u80FD\u6307\u5B9A\u4E00\u4E2A\u522B\u540D"},
        {"This.jar.contains.signed.entries.which.is.not.signed.by.the.specified.alias.es.",
                 "\u6B64 jar \u5305\u542B\u672A\u7531\u6307\u5B9A\u522B\u540D\u7B7E\u540D\u7684\u5DF2\u7B7E\u540D\u6761\u76EE\u3002"},
        {"This.jar.contains.signed.entries.that.s.not.signed.by.alias.in.this.keystore.",
                  "\u6B64 jar \u5305\u542B\u672A\u7531\u6B64\u5BC6\u94A5\u5E93\u4E2D\u7684\u522B\u540D\u7B7E\u540D\u7684\u5DF2\u7B7E\u540D\u6761\u76EE\u3002"},
        {"s", "s"},
        {"m", "m"},
        {"k", "k"},
        {"i", "i"},
        {".and.d.more.", "(%d \u53CA\u4EE5\u4E0A)"},
        {".s.signature.was.verified.",
                "  s = \u5DF2\u9A8C\u8BC1\u7B7E\u540D "},
        {".m.entry.is.listed.in.manifest",
                "  m = \u5728\u6E05\u5355\u4E2D\u5217\u51FA\u6761\u76EE"},
        {".k.at.least.one.certificate.was.found.in.keystore",
                "  k = \u5728\u5BC6\u94A5\u5E93\u4E2D\u81F3\u5C11\u627E\u5230\u4E86\u4E00\u4E2A\u8BC1\u4E66"},
        {".i.at.least.one.certificate.was.found.in.identity.scope",
                "  i = \u5728\u8EAB\u4EFD\u4F5C\u7528\u57DF\u5185\u81F3\u5C11\u627E\u5230\u4E86\u4E00\u4E2A\u8BC1\u4E66"},
        {".X.not.signed.by.specified.alias.es.",
                "  X = \u672A\u7531\u6307\u5B9A\u522B\u540D\u7B7E\u540D"},
        {"no.manifest.", "\u6CA1\u6709\u6E05\u5355\u3002"},
        {".Signature.related.entries.","(\u4E0E\u7B7E\u540D\u76F8\u5173\u7684\u6761\u76EE)"},
        {".Unsigned.entries.", "(\u672A\u7B7E\u540D\u6761\u76EE)"},
        {"jar.is.unsigned.signatures.missing.or.not.parsable.",
                "jar \u672A\u7B7E\u540D\u3002(\u7F3A\u5C11\u7B7E\u540D\u6216\u65E0\u6CD5\u89E3\u6790\u7B7E\u540D)"},
        {"jar.verified.", "jar \u5DF2\u9A8C\u8BC1\u3002"},
        {"jarsigner.", "jarsigner: "},
        {"signature.filename.must.consist.of.the.following.characters.A.Z.0.9.or.",
                "\u7B7E\u540D\u6587\u4EF6\u540D\u5FC5\u987B\u5305\u542B\u4EE5\u4E0B\u5B57\u7B26: A-Z, 0-9, _ \u6216 -"},
        {"unable.to.open.jar.file.", "\u65E0\u6CD5\u6253\u5F00 jar \u6587\u4EF6: "},
        {"unable.to.create.", "\u65E0\u6CD5\u521B\u5EFA: "},
        {".adding.", "   \u6B63\u5728\u6DFB\u52A0: "},
        {".updating.", " \u6B63\u5728\u66F4\u65B0: "},
        {".signing.", "  \u6B63\u5728\u7B7E\u540D: "},
        {"attempt.to.rename.signedJarFile.to.jarFile.failed",
                "\u5C1D\u8BD5\u5C06{0}\u91CD\u547D\u540D\u4E3A{1}\u65F6\u5931\u8D25"},
        {"attempt.to.rename.jarFile.to.origJar.failed",
                "\u5C1D\u8BD5\u5C06{0}\u91CD\u547D\u540D\u4E3A{1}\u65F6\u5931\u8D25"},
        {"unable.to.sign.jar.", "\u65E0\u6CD5\u5BF9 jar \u8FDB\u884C\u7B7E\u540D: "},
        {"Enter.Passphrase.for.keystore.", "\u8F93\u5165\u5BC6\u94A5\u5E93\u7684\u5BC6\u7801\u77ED\u8BED: "},
        {"keystore.load.", "\u5BC6\u94A5\u5E93\u52A0\u8F7D: "},
        {"certificate.exception.", "\u8BC1\u4E66\u5F02\u5E38\u9519\u8BEF: "},
        {"unable.to.instantiate.keystore.class.",
                "\u65E0\u6CD5\u5B9E\u4F8B\u5316\u5BC6\u94A5\u5E93\u7C7B: "},
        {"Certificate.chain.not.found.for.alias.alias.must.reference.a.valid.KeyStore.key.entry.containing.a.private.key.and",
                "\u627E\u4E0D\u5230{0}\u7684\u8BC1\u4E66\u94FE\u3002{1}\u5FC5\u987B\u5F15\u7528\u5305\u542B\u79C1\u6709\u5BC6\u94A5\u548C\u76F8\u5E94\u7684\u516C\u5171\u5BC6\u94A5\u8BC1\u4E66\u94FE\u7684\u6709\u6548\u5BC6\u94A5\u5E93\u5BC6\u94A5\u6761\u76EE\u3002"},
        {"File.specified.by.certchain.does.not.exist",
                "\u7531 -certchain \u6307\u5B9A\u7684\u6587\u4EF6\u4E0D\u5B58\u5728"},
        {"Cannot.restore.certchain.from.file.specified",
                "\u65E0\u6CD5\u4ECE\u6307\u5B9A\u7684\u6587\u4EF6\u8FD8\u539F certchain"},
        {"Certificate.chain.not.found.in.the.file.specified.",
                "\u5728\u6307\u5B9A\u7684\u6587\u4EF6\u4E2D\u627E\u4E0D\u5230\u8BC1\u4E66\u94FE\u3002"},
        {"found.non.X.509.certificate.in.signer.s.chain",
                "\u5728\u7B7E\u540D\u8005\u7684\u94FE\u4E2D\u627E\u5230\u975E X.509 \u8BC1\u4E66"},
        {"incomplete.certificate.chain", "\u8BC1\u4E66\u94FE\u4E0D\u5B8C\u6574"},
        {"Enter.key.password.for.alias.", "\u8F93\u5165{0}\u7684\u5BC6\u94A5\u53E3\u4EE4: "},
        {"unable.to.recover.key.from.keystore",
                "\u65E0\u6CD5\u4ECE\u5BC6\u94A5\u5E93\u4E2D\u6062\u590D\u5BC6\u94A5"},
        {"key.associated.with.alias.not.a.private.key",
                "\u4E0E{0}\u5173\u8054\u7684\u5BC6\u94A5\u4E0D\u662F\u79C1\u6709\u5BC6\u94A5"},
        {"you.must.enter.key.password", "\u5FC5\u987B\u8F93\u5165\u5BC6\u94A5\u53E3\u4EE4"},
        {"unable.to.read.password.", "\u65E0\u6CD5\u8BFB\u53D6\u53E3\u4EE4: "},
        {"certificate.is.valid.from", "\u8BC1\u4E66\u7684\u6709\u6548\u671F\u4E3A{0}\u81F3{1}"},
        {"certificate.expired.on", "\u8BC1\u4E66\u5230\u671F\u65E5\u671F\u4E3A {0}"},
        {"certificate.is.not.valid.until",
                "\u76F4\u5230{0}, \u8BC1\u4E66\u624D\u6709\u6548"},
        {"certificate.will.expire.on", "\u8BC1\u4E66\u5C06\u5728{0}\u5230\u671F"},
        {".CertPath.not.validated.", "[CertPath \u672A\u9A8C\u8BC1: "},
        {"requesting.a.signature.timestamp",
                "\u6B63\u5728\u8BF7\u6C42\u7B7E\u540D\u65F6\u95F4\u6233"},
        {"TSA.location.", "TSA \u4F4D\u7F6E: "},
        {"TSA.certificate.", "TSA \u8BC1\u4E66: "},
        {"no.response.from.the.Timestamping.Authority.",
                "\u65F6\u95F4\u6233\u9881\u53D1\u673A\u6784\u6CA1\u6709\u54CD\u5E94\u3002\u5982\u679C\u8981\u4ECE\u9632\u706B\u5899\u540E\u9762\u8FDE\u63A5, \u5219\u53EF\u80FD\u9700\u8981\u6307\u5B9A HTTP \u6216 HTTPS \u4EE3\u7406\u3002\u8BF7\u4E3A jarsigner \u63D0\u4F9B\u4EE5\u4E0B\u9009\u9879: "},
        {"or", "\u6216"},
        {"Certificate.not.found.for.alias.alias.must.reference.a.valid.KeyStore.entry.containing.an.X.509.public.key.certificate.for.the",
                "\u627E\u4E0D\u5230{0}\u7684\u8BC1\u4E66\u3002{1}\u5FC5\u987B\u5F15\u7528\u5305\u542B\u65F6\u95F4\u6233\u9881\u53D1\u673A\u6784\u7684 X.509 \u516C\u5171\u5BC6\u94A5\u8BC1\u4E66\u7684\u6709\u6548\u5BC6\u94A5\u5E93\u6761\u76EE\u3002"},
        {"using.an.alternative.signing.mechanism",
                "\u6B63\u5728\u4F7F\u7528\u66FF\u4EE3\u7684\u7B7E\u540D\u673A\u5236"},
        {"entry.was.signed.on", "\u6761\u76EE\u7684\u7B7E\u540D\u65E5\u671F\u4E3A {0}"},
        {"Warning.", "\u8B66\u544A: "},
        {"This.jar.contains.unsigned.entries.which.have.not.been.integrity.checked.",
                "\u6B64 jar \u5305\u542B\u5C1A\u672A\u8FDB\u884C\u5B8C\u6574\u6027\u68C0\u67E5\u7684\u672A\u7B7E\u540D\u6761\u76EE\u3002 "},
        {"This.jar.contains.entries.whose.signer.certificate.has.expired.",
                "\u6B64 jar \u5305\u542B\u7B7E\u540D\u8005\u8BC1\u4E66\u5DF2\u8FC7\u671F\u7684\u6761\u76EE\u3002 "},
        {"This.jar.contains.entries.whose.signer.certificate.will.expire.within.six.months.",
                "\u6B64 jar \u5305\u542B\u7B7E\u540D\u8005\u8BC1\u4E66\u5C06\u5728\u516D\u4E2A\u6708\u5185\u8FC7\u671F\u7684\u6761\u76EE\u3002 "},
        {"This.jar.contains.entries.whose.signer.certificate.is.not.yet.valid.",
                "\u6B64 jar \u5305\u542B\u7B7E\u540D\u8005\u8BC1\u4E66\u4ECD\u65E0\u6548\u7684\u6761\u76EE\u3002 "},
        {"Re.run.with.the.verbose.option.for.more.details.",
                "\u6709\u5173\u8BE6\u7EC6\u4FE1\u606F, \u8BF7\u4F7F\u7528 -verbose \u9009\u9879\u91CD\u65B0\u8FD0\u884C\u3002"},
        {"Re.run.with.the.verbose.and.certs.options.for.more.details.",
                "\u6709\u5173\u8BE6\u7EC6\u4FE1\u606F, \u8BF7\u4F7F\u7528 -verbose \u548C -certs \u9009\u9879\u91CD\u65B0\u8FD0\u884C\u3002"},
        {"The.signer.certificate.has.expired.",
                "\u7B7E\u540D\u8005\u8BC1\u4E66\u5DF2\u8FC7\u671F\u3002"},
        {"The.signer.certificate.will.expire.within.six.months.",
                "\u7B7E\u540D\u8005\u8BC1\u4E66\u5C06\u5728\u516D\u4E2A\u6708\u5185\u8FC7\u671F\u3002"},
        {"The.signer.certificate.is.not.yet.valid.",
                "\u7B7E\u540D\u8005\u8BC1\u4E66\u4ECD\u65E0\u6548\u3002"},
        {"The.signer.certificate.s.KeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u7531\u4E8E\u7B7E\u540D\u8005\u8BC1\u4E66\u7684 KeyUsage \u6269\u5C55\u800C\u65E0\u6CD5\u8FDB\u884C\u4EE3\u7801\u7B7E\u540D\u3002"},
        {"The.signer.certificate.s.ExtendedKeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u7531\u4E8E\u7B7E\u540D\u8005\u8BC1\u4E66\u7684 ExtendedKeyUsage \u6269\u5C55\u800C\u65E0\u6CD5\u8FDB\u884C\u4EE3\u7801\u7B7E\u540D\u3002"},
        {"The.signer.certificate.s.NetscapeCertType.extension.doesn.t.allow.code.signing.",
                 "\u7531\u4E8E\u7B7E\u540D\u8005\u8BC1\u4E66\u7684 NetscapeCertType \u6269\u5C55\u800C\u65E0\u6CD5\u8FDB\u884C\u4EE3\u7801\u7B7E\u540D\u3002"},
        {"This.jar.contains.entries.whose.signer.certificate.s.KeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u6B64 jar \u5305\u542B\u7531\u4E8E\u7B7E\u540D\u8005\u8BC1\u4E66\u7684 KeyUsage \u6269\u5C55\u800C\u65E0\u6CD5\u8FDB\u884C\u4EE3\u7801\u7B7E\u540D\u7684\u6761\u76EE\u3002"},
        {"This.jar.contains.entries.whose.signer.certificate.s.ExtendedKeyUsage.extension.doesn.t.allow.code.signing.",
                 "\u6B64 jar \u5305\u542B\u7531\u4E8E\u7B7E\u540D\u8005\u8BC1\u4E66\u7684 ExtendedKeyUsage \u6269\u5C55\u800C\u65E0\u6CD5\u8FDB\u884C\u4EE3\u7801\u7B7E\u540D\u7684\u6761\u76EE\u3002"},
        {"This.jar.contains.entries.whose.signer.certificate.s.NetscapeCertType.extension.doesn.t.allow.code.signing.",
                 "\u6B64 jar \u5305\u542B\u7531\u4E8E\u7B7E\u540D\u8005\u8BC1\u4E66\u7684 NetscapeCertType \u6269\u5C55\u800C\u65E0\u6CD5\u8FDB\u884C\u4EE3\u7801\u7B7E\u540D\u7684\u6761\u76EE\u3002"},
        {".{0}.extension.does.not.support.code.signing.",
                 "[{0} \u6269\u5C55\u4E0D\u652F\u6301\u4EE3\u7801\u7B7E\u540D]"},
        {"The.signer.s.certificate.chain.is.not.validated.",
                "\u7B7E\u540D\u8005\u7684\u8BC1\u4E66\u94FE\u672A\u9A8C\u8BC1\u3002"},
        {"This.jar.contains.entries.whose.certificate.chain.is.not.validated.",
                 "\u6B64 jar \u5305\u542B\u8BC1\u4E66\u94FE\u672A\u9A8C\u8BC1\u7684\u6761\u76EE\u3002"},
        {"Unknown.password.type.", "\u672A\u77E5\u53E3\u4EE4\u7C7B\u578B: "},
        {"Cannot.find.environment.variable.",
                "\u627E\u4E0D\u5230\u73AF\u5883\u53D8\u91CF: "},
        {"Cannot.find.file.", "\u627E\u4E0D\u5230\u6587\u4EF6: "},
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
