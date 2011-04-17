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
public class Resources_sv extends java.util.ListResourceBundle {

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
        {"Options.", "Alternativ:"},
        {"Use.keytool.help.for.all.available.commands",
                 "L\u00E4s \"Hj\u00E4lp - Nyckelverktyg\" f\u00F6r alla tillg\u00E4ngliga kommandon"},
        {"Key.and.Certificate.Management.Tool",
                 "Hanteringsverktyg f\u00F6r nycklar och certifikat"},
        {"Commands.", "Kommandon:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "L\u00E4s \"Hj\u00E4lp - Nyckelverktyg - command_name\" om anv\u00E4ndning av command_name"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "Genererar certifikatbeg\u00E4ran"}, //-certreq
        {"Changes.an.entry.s.alias",
                "\u00C4ndrar postalias"}, //-changealias
        {"Deletes.an.entry",
                "Tar bort post"}, //-delete
        {"Exports.certificate",
                "Exporterar certifikat"}, //-exportcert
        {"Generates.a.key.pair",
                "Genererar nyckelpar"}, //-genkeypair
        {"Generates.a.secret.key",
                "Genererar hemlig nyckel"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "Genererar certifikat fr\u00E5n certifikatbeg\u00E4ran"}, //-gencert
        {"Generates.CRL", "Genererar CRL"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "Importerar poster fr\u00E5n identitetsdatabas i JDK 1.1.x-format"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "Importerar ett certifikat eller en certifikatkedja"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "Importerar en eller alla poster fr\u00E5n annat nyckellager"}, //-importkeystore
        {"Clones.a.key.entry",
                "Klonar en nyckelpost"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "\u00C4ndrar nyckell\u00F6senordet f\u00F6r en post"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "Visar lista \u00F6ver poster i nyckellager"}, //-list
        {"Prints.the.content.of.a.certificate",
                "Skriver ut inneh\u00E5llet i ett certifikat"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "Skriver ut inneh\u00E5llet i en certifikatbeg\u00E4ran"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "Skriver ut inneh\u00E5llet i en CRL-fil"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "Genererar ett sj\u00E4lvsignerat certifikat"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "\u00C4ndrar lagerl\u00F6senordet f\u00F6r ett nyckellager"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "aliasnamn f\u00F6r post som ska bearbetas"}, //-alias
        {"destination.alias",
                "destinationsalias"}, //-destalias
        {"destination.key.password",
                "l\u00F6senord f\u00F6r destinationsnyckel"}, //-destkeypass
        {"destination.keystore.name",
                "namn p\u00E5 destinationsnyckellager"}, //-destkeystore
        {"destination.keystore.password.protected",
                "skyddat l\u00F6senord f\u00F6r destinationsnyckellager"}, //-destprotected
        {"destination.keystore.provider.name",
                "leverant\u00F6rsnamn f\u00F6r destinationsnyckellager"}, //-destprovidername
        {"destination.keystore.password",
                "l\u00F6senord f\u00F6r destinationsnyckellager"}, //-deststorepass
        {"destination.keystore.type",
                "typ av destinationsnyckellager"}, //-deststoretype
        {"distinguished.name",
                "unikt namn"}, //-dname
        {"X.509.extension",
                "X.509-till\u00E4gg"}, //-ext
        {"output.file.name",
                "namn p\u00E5 utdatafil"}, //-file and -outfile
        {"input.file.name",
                "namn p\u00E5 indatafil"}, //-file and -infile
        {"key.algorithm.name",
                "namn p\u00E5 nyckelalgoritm"}, //-keyalg
        {"key.password",
                "nyckell\u00F6senord"}, //-keypass
        {"key.bit.size",
                "nyckelbitstorlek"}, //-keysize
        {"keystore.name",
                "namn p\u00E5 nyckellager"}, //-keystore
        {"new.password",
                "nytt l\u00F6senord"}, //-new
        {"do.not.prompt",
                "fr\u00E5ga inte"}, //-noprompt
        {"password.through.protected.mechanism",
                "l\u00F6senord med skyddad mekanism"}, //-protected
        {"provider.argument",
                "leverant\u00F6rsargument"}, //-providerarg
        {"provider.class.name",
                "namn p\u00E5 leverant\u00F6rsklass"}, //-providerclass
        {"provider.name",
                "leverant\u00F6rsnamn"}, //-providername
        {"provider.classpath",
                "leverant\u00F6rsklass\u00F6kv\u00E4g"}, //-providerpath
        {"output.in.RFC.style",
                "utdata i RFC-format"}, //-rfc
        {"signature.algorithm.name",
                "namn p\u00E5 signaturalgoritm"}, //-sigalg
        {"source.alias",
                "k\u00E4llalias"}, //-srcalias
        {"source.key.password",
                "l\u00F6senord f\u00F6r k\u00E4llnyckel"}, //-srckeypass
        {"source.keystore.name",
                "namn p\u00E5 k\u00E4llnyckellager"}, //-srckeystore
        {"source.keystore.password.protected",
                "skyddat l\u00F6senord f\u00F6r k\u00E4llnyckellager"}, //-srcprotected
        {"source.keystore.provider.name",
                "leverant\u00F6rsnamn f\u00F6r k\u00E4llnyckellager"}, //-srcprovidername
        {"source.keystore.password",
                "l\u00F6senord f\u00F6r k\u00E4llnyckellager"}, //-srcstorepass
        {"source.keystore.type",
                "typ av k\u00E4llnyckellager"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "SSL-serverv\u00E4rd och -port"}, //-sslserver
        {"signed.jar.file",
                "signerad jar-fil"}, //=jarfile
        {"certificate.validity.start.date.time",
                "startdatum/-tid f\u00F6r certifikatets giltighet"}, //-startdate
        {"keystore.password",
                "l\u00F6senord f\u00F6r nyckellager"}, //-storepass
        {"keystore.type",
                "nyckellagertyp"}, //-storetype
        {"trust.certificates.from.cacerts",
                "tillf\u00F6rlitliga certifikat fr\u00E5n cacerts"}, //-trustcacerts
        {"verbose.output",
                "utf\u00F6rliga utdata"}, //-v
        {"validity.number.of.days",
                "antal dagar f\u00F6r giltighet"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "Seriellt ID f\u00F6r certifikat som ska \u00E5terkallas"}, //-id
        // keytool: Running part
        {"keytool.error.", "nyckelverktygsfel: "},
        {"Illegal.option.", "Otill\u00E5tet alternativ:  "},
        {"Illegal.value.", "Otill\u00E5tet v\u00E4rde: "},
        {"Unknown.password.type.", "Ok\u00E4nd l\u00F6senordstyp: "},
        {"Cannot.find.environment.variable.",
                "Kan inte hitta milj\u00F6variabel: "},
        {"Cannot.find.file.", "Hittar inte fil: "},
        {"Command.option.flag.needs.an.argument.", "Kommandoalternativet {0} beh\u00F6ver ett argument."},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "Varning!  PKCS12-nyckellager har inte st\u00F6d f\u00F6r olika l\u00F6senord f\u00F6r lagret och nyckeln. Det anv\u00E4ndarspecificerade {0}-v\u00E4rdet ignoreras."},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-keystore m\u00E5ste vara NONE om -storetype \u00E4r {0}"},
        {"Too.many.retries.program.terminated",
                 "F\u00F6r m\u00E5nga f\u00F6rs\u00F6k. Programmet avslutas"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "-storepasswd- och -keypasswd-kommandon st\u00F6ds inte om -storetype \u00E4r {0}"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "-keypasswd-kommandon st\u00F6ds inte om -storetype \u00E4r PKCS12"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "-keypass och -new kan inte anges om -storetype \u00E4r {0}"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "om -protected har angetts f\u00E5r inte -storepass, -keypass och -new anges"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "om -srcprotected anges f\u00E5r -srcstorepass och -srckeypass inte anges"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "om nyckellagret inte \u00E4r l\u00F6senordsskyddat f\u00E5r -storepass, -keypass och -new inte anges"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "om k\u00E4llnyckellagret inte \u00E4r l\u00F6senordsskyddat f\u00E5r -srcstorepass och -srckeypass inte anges"},
        {"Illegal.startdate.value", "Otill\u00E5tet v\u00E4rde f\u00F6r startdatum"},
        {"Validity.must.be.greater.than.zero",
                "Giltigheten m\u00E5ste vara st\u00F6rre \u00E4n noll"},
        {"provName.not.a.provider", "{0} \u00E4r inte en leverant\u00F6r"},
        {"Usage.error.no.command.provided", "Syntaxfel: inget kommando angivet"},
        {"Source.keystore.file.exists.but.is.empty.", "Nyckellagrets k\u00E4llfil finns, men \u00E4r tom: "},
        {"Please.specify.srckeystore", "Ange -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "Kan inte specificera b\u00E5de -v och -rfc med 'list'-kommandot"},
        {"Key.password.must.be.at.least.6.characters",
                "Nyckell\u00F6senordet m\u00E5ste inneh\u00E5lla minst 6 tecken"},
        {"New.password.must.be.at.least.6.characters",
                "Det nya l\u00F6senordet m\u00E5ste inneh\u00E5lla minst 6 tecken"},
        {"Keystore.file.exists.but.is.empty.",
                "Nyckellagerfilen finns, men \u00E4r tom: "},
        {"Keystore.file.does.not.exist.",
                "Nyckellagerfilen finns inte: "},
        {"Must.specify.destination.alias", "Du m\u00E5ste ange destinationsalias"},
        {"Must.specify.alias", "Du m\u00E5ste ange alias"},
        {"Keystore.password.must.be.at.least.6.characters",
                "Nyckellagerl\u00F6senordet m\u00E5ste inneh\u00E5lla minst 6 tecken"},
        {"Enter.keystore.password.", "Ange nyckellagerl\u00F6senord:  "},
        {"Enter.source.keystore.password.", "Ange l\u00F6senord f\u00F6r k\u00E4llnyckellagret:  "},
        {"Enter.destination.keystore.password.", "Ange nyckellagerl\u00F6senord f\u00F6r destination:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "Nyckellagerl\u00F6senordet \u00E4r f\u00F6r kort - det m\u00E5ste inneh\u00E5lla minst 6 tecken"},
        {"Unknown.Entry.Type", "Ok\u00E4nd posttyp"},
        {"Too.many.failures.Alias.not.changed", "F\u00F6r m\u00E5nga fel. Alias har inte \u00E4ndrats"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "Posten f\u00F6r alias {0} har importerats."},
        {"Entry.for.alias.alias.not.imported.", "Posten f\u00F6r alias {0} har inte importerats."},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "Ett problem uppstod vid importen av posten f\u00F6r alias {0}: {1}.\nPosten {0} har inte importerats."},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "Kommandoimporten slutf\u00F6rd: {0} poster har importerats, {1} poster var felaktiga eller annullerades"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "Varning! Det befintliga aliaset {0} i destinationsnyckellagret skrivs \u00F6ver"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "Aliaset {0} finns redan. Vill du skriva \u00F6ver det? [nej]:  "},
        {"Too.many.failures.try.later", "F\u00F6r m\u00E5nga fel - f\u00F6rs\u00F6k igen senare"},
        {"Certification.request.stored.in.file.filename.",
                "Certifikatbeg\u00E4ran har lagrats i filen <{0}>"},
        {"Submit.this.to.your.CA", "Skicka detta till certifikatutf\u00E4rdaren"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "om n\u00E5got alias inte anges f\u00E5r destalias, srckeypass och destkeypass inte anges"},
        {"Certificate.stored.in.file.filename.",
                "Certifikatet har lagrats i filen <{0}>"},
        {"Certificate.reply.was.installed.in.keystore",
                "Certifikatsvaret har installerats i nyckellagret"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "Certifikatsvaret har inte installerats i nyckellagret"},
        {"Certificate.was.added.to.keystore",
                "Certifikatet har lagts till i nyckellagret"},
        {"Certificate.was.not.added.to.keystore",
                "Certifikatet har inte lagts till i nyckellagret"},
        {".Storing.ksfname.", "[Lagrar {0}]"},
        {"alias.has.no.public.key.certificate.",
                "{0} saknar offentlig nyckel (certifikat)"},
        {"Cannot.derive.signature.algorithm",
                "Kan inte h\u00E4rleda signaturalgoritm"},
        {"Alias.alias.does.not.exist",
                "Aliaset <{0}> finns inte"},
        {"Alias.alias.has.no.certificate",
                "Aliaset <{0}> saknar certifikat"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "Nyckelparet genererades inte. Aliaset <{0}> finns redan"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "Genererar {0} bitars {1}-nyckelpar och sj\u00E4lvsignerat certifikat ({2}) med en giltighet p\u00E5 {3} dagar\n\tf\u00F6r: {4}"},
        {"Enter.key.password.for.alias.", "Ange nyckell\u00F6senord f\u00F6r <{0}>"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(RETURN om det \u00E4r identiskt med nyckellagerl\u00F6senordet):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "Nyckell\u00F6senordet \u00E4r f\u00F6r kort - det m\u00E5ste inneh\u00E5lla minst 6 tecken"},
        {"Too.many.failures.key.not.added.to.keystore",
                "F\u00F6r m\u00E5nga fel - nyckeln lades inte till i nyckellagret"},
        {"Destination.alias.dest.already.exists",
                "Destinationsaliaset <{0}> finns redan"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "L\u00F6senordet \u00E4r f\u00F6r kort - det m\u00E5ste inneh\u00E5lla minst 6 tecken"},
        {"Too.many.failures.Key.entry.not.cloned",
                "F\u00F6r m\u00E5nga fel. Nyckelposten har inte klonats"},
        {"key.password.for.alias.", "nyckell\u00F6senord f\u00F6r <{0}>"},
        {"Keystore.entry.for.id.getName.already.exists",
                "Nyckellagerpost f\u00F6r <{0}> finns redan"},
        {"Creating.keystore.entry.for.id.getName.",
                "Skapar nyckellagerpost f\u00F6r <{0}> ..."},
        {"No.entries.from.identity.database.added",
                "Inga poster fr\u00E5n identitetsdatabasen har lagts till"},
        {"Alias.name.alias", "Aliasnamn: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "Skapat den: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "Posttyp: {0}"},
        {"Certificate.chain.length.", "L\u00E4ngd p\u00E5 certifikatskedja: "},
        {"Certificate.i.1.", "Certifikat[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "Certifikatets fingeravtryck (SHA1): "},
        {"Entry.type.trustedCertEntry.", "Posttyp: trustedCertEntry\n"},
        {"trustedCertEntry.", "trustedCertEntry,"},
        {"Keystore.type.", "Nyckellagertyp: "},
        {"Keystore.provider.", "Nyckellagerleverant\u00F6r: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "Nyckellagret inneh\u00E5ller {0,number,integer} post"},
        {"Your.keystore.contains.keyStore.size.entries",
                "Nyckellagret inneh\u00E5ller {0,number,integer} poster"},
        {"Failed.to.parse.input", "Kunde inte tolka indata"},
        {"Empty.input", "Inga indata"},
        {"Not.X.509.certificate", "Inte ett X.509-certifikat"},
        {"alias.has.no.public.key", "{0} saknar offentlig nyckel"},
        {"alias.has.no.X.509.certificate", "{0} saknar X.509-certifikat"},
        {"New.certificate.self.signed.", "Nytt certifikat (sj\u00E4lvsignerat):"},
        {"Reply.has.no.certificates", "Svaret saknar certifikat"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "Certifikatet importerades inte. Aliaset <{0}> finns redan"},
        {"Input.not.an.X.509.certificate", "Indata \u00E4r inte ett X.509-certifikat"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "Certifikatet finns redan i nyckellagerfilen under aliaset <{0}>"},
        {"Do.you.still.want.to.add.it.no.",
                "Vill du fortfarande l\u00E4gga till det? [nej]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "Certifikatet finns redan i den systemomsp\u00E4nnande CA-nyckellagerfilen under aliaset <{0}>"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "Vill du fortfarande l\u00E4gga till det i ditt eget nyckellagret? [nej]:  "},
        {"Trust.this.certificate.no.", "Litar du p\u00E5 det h\u00E4r certifikatet? [nej]:  "},
        {"YES", "JA"},
        {"New.prompt.", "Nytt {0}: "},
        {"Passwords.must.differ", "L\u00F6senorden m\u00E5ste vara olika"},
        {"Re.enter.new.prompt.", "Ange nytt {0} igen: "},
        {"Re.enter.new.password.", "Ange det nya l\u00F6senordet igen: "},
        {"They.don.t.match.Try.again", "De matchar inte. F\u00F6rs\u00F6k igen"},
        {"Enter.prompt.alias.name.", "Ange aliasnamn f\u00F6r {0}:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "Ange ett nytt aliasnamn\t(skriv RETURN f\u00F6r att avbryta importen av denna post):  "},
        {"Enter.alias.name.", "Ange aliasnamn:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(RETURN om det \u00E4r det samma som f\u00F6r <{0}>)"},
        {".PATTERN.printX509Cert",
                "\u00C4gare: {0}\nUtf\u00E4rdare: {1}\nSerienummer: {2}\nGiltigt fr\u00E5n den: {3} till: {4}\nCertifikatets fingeravtryck:\n\t MD5: {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t Namn p\u00E5 signaturalgoritm: {8}\n\t Version: {9}"},
        {"What.is.your.first.and.last.name.",
                "Vad heter du i f\u00F6r- och efternamn?"},
        {"What.is.the.name.of.your.organizational.unit.",
                "Vad heter din avdelning inom organisationen?"},
        {"What.is.the.name.of.your.organization.",
                "Vad heter din organisation?"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "Vad heter din ort eller plats?"},
        {"What.is.the.name.of.your.State.or.Province.",
                "Vad heter ditt land eller din provins?"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "Vilken \u00E4r den tv\u00E5st\u00E4lliga landskoden?"},
        {"Is.name.correct.", "\u00C4r {0} korrekt?"},
        {"no", "nej"},
        {"yes", "ja"},
        {"y", "j"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "Aliaset <{0}> saknar nyckel"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "Aliaset <{0}> refererar till en posttyp som inte \u00E4r n\u00E5gon privat nyckelpost. Kommandot -keyclone har endast st\u00F6d f\u00F6r kloning av privata nyckelposter"},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "Signerare #%d:"},
        {"Timestamp.", "Tidsst\u00E4mpel:"},
        {"Signature.", "Underskrift:"},
        {"CRLs.", "CRL:er:"},
        {"Certificate.owner.", "Certifikat\u00E4gare: "},
        {"Not.a.signed.jar.file", "Ingen signerad jar-fil"},
        {"No.certificate.from.the.SSL.server",
                "Inget certifikat fr\u00E5n SSL-servern"},

        // Translators of the following 5 pairs, ATTENTION:
        // the next 5 string pairs are meant to be combined into 2 paragraphs,
        // 1+3+4 and 2+3+5. make sure your translation also does.
        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* Integriteten f\u00F6r den information som lagras i nyckellagerfilen  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* Integriteten f\u00F6r informationen som lagras i srckeystore*"},
        {".has.NOT.been.verified.In.order.to.verify.its.integrity.",
            "* har INTE verifierats!  Om du vill verifiera dess integritet, *"},
        {".you.must.provide.your.keystore.password.",
            "* m\u00E5ste du ange nyckellagerl\u00F6senord.                  *"},
        {".you.must.provide.the.srckeystore.password.",
            "* du m\u00E5ste ange l\u00F6senordet f\u00F6r srckeystore.                *"},


        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "Certifikatsvaret inneh\u00E5ller inte n\u00E5gon offentlig nyckel f\u00F6r <{0}>"},
        {"Incomplete.certificate.chain.in.reply",
                "Ofullst\u00E4ndig certifikatskedja i svaret"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "Certifikatskedjan i svaret g\u00E5r inte att verifiera: "},
        {"Top.level.certificate.in.reply.",
                "Toppniv\u00E5certifikatet i svaret:\n"},
        {".is.not.trusted.", "... \u00E4r inte betrott. "},
        {"Install.reply.anyway.no.", "Vill du installera svaret \u00E4nd\u00E5? [nej]:  "},
        {"NO", "NEJ"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "De offentliga nycklarna i svaret och nyckellagret matchar inte varandra"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "Certifikatsvaret och certifikatet i nyckellagret \u00E4r identiska"},
        {"Failed.to.establish.chain.from.reply",
                "Kunde inte uppr\u00E4tta kedja fr\u00E5n svaret"},
        {"n", "n"},
        {"Wrong.answer.try.again", "Fel svar. F\u00F6rs\u00F6k p\u00E5 nytt."},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "Den hemliga nyckeln har inte genererats eftersom aliaset <{0}> redan finns"},
        {"Please.provide.keysize.for.secret.key.generation",
                "Ange -keysize f\u00F6r att skapa hemlig nyckel"},

        {"Extensions.", "Till\u00E4gg: "},
        {".Empty.value.", "(Tomt v\u00E4rde)"},
        {"Extension.Request.", "Till\u00E4ggsbeg\u00E4ran:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "PKCS #10 certifikatbeg\u00E4ran (version 1.0)\n\u00C4mne: %s\nAllm\u00E4n nyckel: %s-format %s-nyckel\n"},
        {"Unknown.keyUsage.type.", "Ok\u00E4nd keyUsage-typ: "},
        {"Unknown.extendedkeyUsage.type.", "Ok\u00E4nd extendedkeyUsage-typ: "},
        {"Unknown.AccessDescription.type.", "Ok\u00E4nd AccessDescription-typ: "},
        {"Unrecognized.GeneralName.type.", "Ok\u00E4nd GeneralName-typ: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "Detta till\u00E4gg kan inte markeras som kritiskt. "},
        {"Odd.number.of.hex.digits.found.", "Udda antal hex-siffror p\u00E5tr\u00E4ffades: "},
        {"Unknown.extension.type.", "Ok\u00E4nd till\u00E4ggstyp: "},
        {"command.{0}.is.ambiguous.", "kommandot {0} \u00E4r tvetydigt:"},

        // policytool
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Varning! Det finns ingen offentlig nyckel f\u00F6r aliaset {0}. Kontrollera att det aktuella nyckellagret \u00E4r korrekt konfigurerat."},
        {"Warning.Class.not.found.class", "Varning! Klassen hittades inte: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Varning! Ogiltiga argument f\u00F6r konstruktor: {0}"},
        {"Illegal.Principal.Type.type", "Otill\u00E5ten identitetshavaretyp: {0}"},
        {"Illegal.option.option", "Otill\u00E5tet alternativ: {0}"},
        {"Usage.policytool.options.", "Syntax: policytool [alternativ]"},
        {".file.file.policy.file.location",
                "  [-file <fil>]    policyfilens plats"},
        {"New", "Nytt"},
        {"Open", "\u00D6ppna"},
        {"Save", "Spara"},
        {"Save.As", "Spara som"},
        {"View.Warning.Log", "Visa varningslogg"},
        {"Exit", "Avsluta"},
        {"Add.Policy.Entry", "L\u00E4gg till policypost"},
        {"Edit.Policy.Entry", "Redigera policypost"},
        {"Remove.Policy.Entry", "Ta bort policypost"},
        {"Edit", "Redigera"},
        {"Retain", "Beh\u00E5ll"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Varning! Filnamnet kan inneh\u00E5lla omv\u00E4nda snedstreck inom citattecken. Citattecken kr\u00E4vs inte f\u00F6r omv\u00E4nda snedstreck (verktyget hanterar detta n\u00E4r policyinneh\u00E5llet skrivs till det best\u00E4ndiga lagret).\n\nKlicka p\u00E5 Beh\u00E5ll f\u00F6r att beh\u00E5lla det angivna namnet, eller klicka p\u00E5 Redigera f\u00F6r att \u00E4ndra det."},

        {"Add.Public.Key.Alias", "L\u00E4gg till offentligt nyckelalias"},
        {"Remove.Public.Key.Alias", "Ta bort offentligt nyckelalias"},
        {"File", "Fil"},
        {"KeyStore", "Nyckellager"},
        {"Policy.File.", "Policyfil:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "Kan inte \u00F6ppna policyfilen: {0}: {1}"},
        {"Policy.Tool", "Policyverktyg"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Det uppstod ett fel n\u00E4r policykonfigurationen skulle \u00F6ppnas. Se varningsloggen f\u00F6r mer information."},
        {"Error", "Fel"},
        {"OK", "OK"},
        {"Status", "Status"},
        {"Warning", "Varning"},
        {"Permission.",
                "Beh\u00F6righet:                                                       "},
        {"Principal.Type.", "Identitetshavaretyp:"},
        {"Principal.Name.", "Identitetshavare:"},
        {"Target.Name.",
                "M\u00E5l:                                                    "},
        {"Actions.",
                "Funktioner:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "Ska den befintliga filen {0} skrivas \u00F6ver?"},
        {"Cancel", "Avbryt"},
        {"CodeBase.", "Kodbas:"},
        {"SignedBy.", "Signerad av:"},
        {"Add.Principal", "L\u00E4gg till identitetshavare"},
        {"Edit.Principal", "Redigera identitetshavare"},
        {"Remove.Principal", "Ta bort identitetshavare"},
        {"Principals.", "Identitetshavare:"},
        {".Add.Permission", "  L\u00E4gg till beh\u00F6righet"},
        {".Edit.Permission", "  Redigera beh\u00F6righet"},
        {"Remove.Permission", "Ta bort beh\u00F6righet"},
        {"Done", "Utf\u00F6rd"},
        {"KeyStore.URL.", "URL f\u00F6r nyckellager:"},
        {"KeyStore.Type.", "Nyckellagertyp:"},
        {"KeyStore.Provider.", "Nyckellagerleverant\u00F6r:"},
        {"KeyStore.Password.URL.", "URL f\u00F6r l\u00F6senord till nyckellager:"},
        {"Principals", "Identitetshavare"},
        {".Edit.Principal.", "  Redigera identitetshavare:"},
        {".Add.New.Principal.", "  L\u00E4gg till ny identitetshavare:"},
        {"Permissions", "Beh\u00F6righet"},
        {".Edit.Permission.", "  Redigera beh\u00F6righet:"},
        {".Add.New.Permission.", "  L\u00E4gg till ny beh\u00F6righet:"},
        {"Signed.By.", "Signerad av:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "Kan inte specificera identitetshavare med jokerteckenklass utan jokerteckennamn"},
        {"Cannot.Specify.Principal.without.a.Name",
            "Kan inte specificera identitetshavare utan namn"},
        {"Permission.and.Target.Name.must.have.a.value",
                "Beh\u00F6righet och m\u00E5lnamn m\u00E5ste ha ett v\u00E4rde"},
        {"Remove.this.Policy.Entry.", "Vill du ta bort den h\u00E4r policyposten?"},
        {"Overwrite.File", "Skriv \u00F6ver fil"},
        {"Policy.successfully.written.to.filename",
                "Policy har skrivits till {0}"},
        {"null.filename", "nullfilnamn"},
        {"Save.changes.", "Vill du spara \u00E4ndringarna?"},
        {"Yes", "Ja"},
        {"No", "Nej"},
        {"Policy.Entry", "Policyfel"},
        {"Save.Changes", "Spara \u00E4ndringar"},
        {"No.Policy.Entry.selected", "Ingen policypost har valts"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "Kan inte \u00F6ppna nyckellagret: {0}"},
        {"No.principal.selected", "Ingen identitetshavare har valts"},
        {"No.permission.selected", "Ingen beh\u00F6righet har valts"},
        {"name", "namn"},
        {"configuration.type", "konfigurationstyp"},
        {"environment.variable.name", "variabelnamn f\u00F6r milj\u00F6"},
        {"library.name", "biblioteksnamn"},
        {"package.name", "paketnamn"},
        {"policy.type", "policytyp"},
        {"property.name", "egenskapsnamn"},
        {"Principal.List", "Lista \u00F6ver identitetshavare"},
        {"Permission.List", "Beh\u00F6righetslista"},
        {"Code.Base", "Kodbas"},
        {"KeyStore.U.R.L.", "URL f\u00F6r nyckellager:"},
        {"KeyStore.Password.U.R.L.", "URL f\u00F6r l\u00F6senord till nyckellager:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "ogiltiga null-indata"},
        {"actions.can.only.be.read.", "funktioner kan endast 'l\u00E4sas'"},
        {"permission.name.name.syntax.invalid.",
                "syntaxen f\u00F6r beh\u00F6righetsnamnet [{0}] \u00E4r ogiltig: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "Inloggningsuppgiftsklassen f\u00F6ljs inte av klass eller namn f\u00F6r identitetshavare"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "Identitetshavareklassen f\u00F6ljs inte av n\u00E5got identitetshavarenamn"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "Identitetshavarenamnet m\u00E5ste anges inom citattecken"},
        {"Principal.Name.missing.end.quote",
                "Identitetshavarenamnet saknar avslutande citattecken"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "Identitetshavareklassen PrivateCredentialPermission kan inte ha n\u00E5got jokertecken (*) om inte namnet p\u00E5 identitetshavaren anges med jokertecken (*)"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\tIdentitetshavareklass = {0}\n\tIdentitetshavarenamn = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "angav null-namn"},
        {"provided.null.keyword.map", "nullnyckelordsmappning tillhandah\u00F6lls"},
        {"provided.null.OID.map", "null-OID-mappning tillhandah\u00F6lls"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "ogiltigt null-AccessControlContext"},
        {"invalid.null.action.provided", "ogiltig null-funktion"},
        {"invalid.null.Class.provided", "ogiltig null-klass"},
        {"Subject.", "Innehavare:\n"},
        {".Principal.", "\tIdentitetshavare: "},
        {".Public.Credential.", "\tOffentlig inloggning: "},
        {".Private.Credentials.inaccessible.",
                "\tPrivat inloggning \u00E4r inte tillg\u00E4nglig\n"},
        {".Private.Credential.", "\tPrivat inloggning: "},
        {".Private.Credential.inaccessible.",
                "\tPrivat inloggning \u00E4r inte tillg\u00E4nglig\n"},
        {"Subject.is.read.only", "Innehavare \u00E4r skrivskyddad"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "f\u00F6rs\u00F6k att l\u00E4gga till ett objekt som inte \u00E4r en f\u00F6rekomst av java.security.Principal till en upps\u00E4ttning av identitetshavare"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "f\u00F6rs\u00F6ker l\u00E4gga till ett objekt som inte \u00E4r en instans av {0}"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "Ogiltiga null-indata: namn"},
        {"No.LoginModules.configured.for.name",
         "Inga inloggningsmoduler har konfigurerats f\u00F6r {0}"},
        {"invalid.null.Subject.provided", "ogiltig null-innehavare"},
        {"invalid.null.CallbackHandler.provided",
                "ogiltig null-CallbackHandler"},
        {"null.subject.logout.called.before.login",
                "null-innehavare - utloggning anropades f\u00F6re inloggning"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "kan inte instansiera LoginModule, {0}, eftersom den inte tillhandah\u00E5ller n\u00E5gon icke-argumentskonstruktor"},
        {"unable.to.instantiate.LoginModule",
                "kan inte instansiera LoginModule"},
        {"unable.to.instantiate.LoginModule.",
                "kan inte instansiera LoginModule: "},
        {"unable.to.find.LoginModule.class.",
                "hittar inte LoginModule-klassen: "},
        {"unable.to.access.LoginModule.",
                "ingen \u00E5tkomst till LoginModule: "},
        {"Login.Failure.all.modules.ignored",
                "Inloggningsfel: alla moduler ignoreras"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: fel vid tolkning av {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: fel vid till\u00E4gg av beh\u00F6righet, {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: fel vid till\u00E4gg av post:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "aliasnamn ej angivet ({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "kan ej ers\u00E4tta alias, {0}"},
        {"substitution.value.prefix.unsupported",
                "ers\u00E4ttningsv\u00E4rde, {0}, st\u00F6ds ej"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","typen kan inte vara null"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "kan inte ange keystorePasswordURL utan att ange nyckellager"},
        {"expected.keystore.type", "f\u00F6rv\u00E4ntad nyckellagertyp"},
        {"expected.keystore.provider", "nyckellagerleverant\u00F6r f\u00F6rv\u00E4ntades"},
        {"multiple.Codebase.expressions",
                "flera CodeBase-uttryck"},
        {"multiple.SignedBy.expressions","flera SignedBy-uttryck"},
        {"SignedBy.has.empty.alias","SignedBy har ett tomt alias"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "kan inte ange identitetshavare med en jokerteckenklass utan ett jokerteckennamn"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "f\u00F6rv\u00E4ntad codeBase eller SignedBy eller identitetshavare"},
        {"expected.permission.entry", "f\u00F6rv\u00E4ntade beh\u00F6righetspost"},
        {"number.", "antal "},
        {"expected.expect.read.end.of.file.",
                "f\u00F6rv\u00E4ntade [{0}], l\u00E4ste [end of file]"},
        {"expected.read.end.of.file.",
                "f\u00F6rv\u00E4ntade [;], l\u00E4ste [end of file]"},
        {"line.number.msg", "rad {0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "rad {0}: f\u00F6rv\u00E4ntade [{1}], hittade [{2}]"},
        {"null.principalClass.or.principalName",
                "null-principalClass eller -principalName"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "PKCS11-tecken [{0}] L\u00F6senord: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "den innehavarbaserade policyn kan inte skapas"}
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

