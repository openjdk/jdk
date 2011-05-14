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
public class Resources_it extends java.util.ListResourceBundle {

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
// "Option" should be translated.
        {".OPTION.", " [Opzione]..."},
        {"Options.", "Opzioni:"},
        {"Use.keytool.help.for.all.available.commands",
                 "Utilizzare \"keytool -help\" per visualizzare tutti i comandi disponibili"},
        {"Key.and.Certificate.Management.Tool",
                 "Strumento di gestione di chiavi e certificati"},
        {"Commands.", "Comandi:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "Utilizzare \"keytool -command_name -help\" per informazioni sull'uso di command_name"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "Genera una richiesta di certificato"}, //-certreq
        {"Changes.an.entry.s.alias",
                "Modifica l'alias di una voce"}, //-changealias
        {"Deletes.an.entry",
                "Elimina una voce"}, //-delete
        {"Exports.certificate",
                "Esporta il certificato"}, //-exportcert
        {"Generates.a.key.pair",
                "Genera una coppia di chiavi"}, //-genkeypair
// translation of "secret" key should be different to "private" key.
        {"Generates.a.secret.key",
                "Genera una chiave segreta"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "Genera un certificato da una richiesta di certificato"}, //-gencert
        {"Generates.CRL", "Genera CRL"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "Importa le voci da un database delle identit\u00E0 di tipo JDK 1.1.x"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "Importa un certificato o una catena di certificati"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "Importa una o tutte le voci da un altro keystore"}, //-importkeystore
        {"Clones.a.key.entry",
                "Duplica una voce di chiave"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "Modifica la password della chiave per una voce"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "Elenca le voci in un keystore"}, //-list
        {"Prints.the.content.of.a.certificate",
                "Visualizza i contenuti di un certificato"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "Visualizza i contenuti di una richiesta di certificato"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "Visualizza i contenuti di un file CRL"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "Genera certificato con firma automatica"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "Modifica la password di area di memorizzazione di un keystore"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "nome alias della voce da elaborare"}, //-alias
        {"destination.alias",
                "alias di destinazione"}, //-destalias
        {"destination.key.password",
                "password chiave di destinazione"}, //-destkeypass
        {"destination.keystore.name",
                "nome keystore di destinazione"}, //-destkeystore
        {"destination.keystore.password.protected",
                "password keystore di destinazione protetta"}, //-destprotected
        {"destination.keystore.provider.name",
                "nome provider keystore di destinazione"}, //-destprovidername
        {"destination.keystore.password",
                "password keystore di destinazione"}, //-deststorepass
        {"destination.keystore.type",
                "tipo keystore di destinazione"}, //-deststoretype
        {"distinguished.name",
                "nome distinto"}, //-dname
        {"X.509.extension",
                "estensione X.509"}, //-ext
        {"output.file.name",
                "nome file di output"}, //-file and -outfile
        {"input.file.name",
                "nome file di input"}, //-file and -infile
        {"key.algorithm.name",
                "nome algoritmo chiave"}, //-keyalg
        {"key.password",
                "password chiave"}, //-keypass
        {"key.bit.size",
                "dimensione bit chiave"}, //-keysize
        {"keystore.name",
                "nome keystore"}, //-keystore
        {"new.password",
                "nuova password"}, //-new
        {"do.not.prompt",
                "non richiedere"}, //-noprompt
        {"password.through.protected.mechanism",
                "password mediante meccanismo protetto"}, //-protected
        {"provider.argument",
                "argomento provider"}, //-providerarg
        {"provider.class.name",
                "nome classe provider"}, //-providerclass
        {"provider.name",
                "nome provider"}, //-providername
        {"provider.classpath",
                "classpath provider"}, //-providerpath
        {"output.in.RFC.style",
                "output in stile RFC"}, //-rfc
        {"signature.algorithm.name",
                "nome algoritmo firma"}, //-sigalg
        {"source.alias",
                "alias origine"}, //-srcalias
        {"source.key.password",
                "password chiave di origine"}, //-srckeypass
        {"source.keystore.name",
                "nome keystore di origine"}, //-srckeystore
        {"source.keystore.password.protected",
                "password keystore di origine protetta"}, //-srcprotected
        {"source.keystore.provider.name",
                "nome provider keystore di origine"}, //-srcprovidername
        {"source.keystore.password",
                "password keystore di origine"}, //-srcstorepass
        {"source.keystore.type",
                "tipo keystore di origine"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "host e porta server SSL"}, //-sslserver
        {"signed.jar.file",
                "file jar firmato"}, //=jarfile
        {"certificate.validity.start.date.time",
                "data/ora di inizio validit\u00E0 certificato"}, //-startdate
        {"keystore.password",
                "password keystore"}, //-storepass
        {"keystore.type",
                "tipo keystore"}, //-storetype
        {"trust.certificates.from.cacerts",
                "considera sicuri i certificati da cacerts"}, //-trustcacerts
        {"verbose.output",
                "output descrittivo"}, //-v
        {"validity.number.of.days",
                "numero di giorni di validit\u00E0"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "ID seriale del certificato da revocare"}, //-id
        // keytool: Running part
        {"keytool.error.", "Errore keytool: "},
        {"Illegal.option.", "Opzione non valida:  "},
        {"Illegal.value.", "Valore non valido: "},
        {"Unknown.password.type.", "Tipo di password sconosciuto: "},
        {"Cannot.find.environment.variable.",
                "Impossibile trovare la variabile di ambiente: "},
        {"Cannot.find.file.", "Impossibile trovare il file: "},
        {"Command.option.flag.needs.an.argument.", "\u00C8 necessario specificare un argomento per l''opzione di comando {0}."},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "Avvertenza: non sono supportate password diverse di chiave e di archivio per i keystore PKCS12. Il valore {0} specificato dall''utente verr\u00E0 ignorato."},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "Se -storetype \u00E8 impostato su {0}, -keystore deve essere impostato su NONE"},
        {"Too.many.retries.program.terminated",
                 "Il numero dei tentativi consentiti \u00E8 stato superato. Il programma verr\u00E0 terminato."},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "Se -storetype \u00E8 impostato su {0}, i comandi -storepasswd e -keypasswd non sono supportati"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "Se -storetype \u00E8 impostato su PKCS12 i comandi -keypasswd non vengono supportati"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "Se -storetype \u00E8 impostato su {0}, non \u00E8 possibile specificare un valore per -keypass e -new"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "Se \u00E8 specificata l'opzione -protected, le opzioni -storepass, -keypass e -new non possono essere specificate"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "Se viene specificato -srcprotected, -srcstorepass e -srckeypass non dovranno essere specificati"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "Se il file keystore non \u00E8 protetto da password, non deve essere specificato alcun valore per -storepass, -keypass e -new"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "Se il file keystore non \u00E8 protetto da password, non deve essere specificato alcun valore per -srcstorepass e -srckeypass"},
        {"Illegal.startdate.value", "Valore di data di inizio non valido"},
        {"Validity.must.be.greater.than.zero",
                "La validit\u00E0 deve essere maggiore di zero"},
        {"provName.not.a.provider", "{0} non \u00E8 un provider"},
        {"Usage.error.no.command.provided", "Errore di utilizzo: nessun comando specificato"},
        {"Source.keystore.file.exists.but.is.empty.", "Il file keystore di origine esiste, ma \u00E8 vuoto: "},
        {"Please.specify.srckeystore", "Specificare -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "Impossibile specificare sia -v sia -rfc con il comando 'list'"},
        {"Key.password.must.be.at.least.6.characters",
                "La password della chiave deve contenere almeno 6 caratteri"},
        {"New.password.must.be.at.least.6.characters",
                "La nuova password deve contenere almeno 6 caratteri"},
        {"Keystore.file.exists.but.is.empty.",
                "Il file keystore esiste ma \u00E8 vuoto: "},
        {"Keystore.file.does.not.exist.",
                "Il file keystore non esiste: "},
        {"Must.specify.destination.alias", "\u00C8 necessario specificare l'alias di destinazione"},
        {"Must.specify.alias", "\u00C8 necessario specificare l'alias"},
        {"Keystore.password.must.be.at.least.6.characters",
                "La password del keystore deve contenere almeno 6 caratteri"},
        {"Enter.keystore.password.", "Immettere la password del keystore:  "},
        {"Enter.source.keystore.password.", "Immettere la password del keystore di origine:  "},
        {"Enter.destination.keystore.password.", "Immettere la password del keystore di destinazione:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "La password del keystore \u00E8 troppo corta - deve contenere almeno 6 caratteri"},
        {"Unknown.Entry.Type", "Tipo di voce sconosciuto"},
        {"Too.many.failures.Alias.not.changed", "Numero eccessivo di errori. L'alias non \u00E8 stato modificato."},
        {"Entry.for.alias.alias.successfully.imported.",
                 "La voce dell''alias {0} \u00E8 stata importata."},
        {"Entry.for.alias.alias.not.imported.", "La voce dell''alias {0} non \u00E8 stata importata."},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "Si \u00E8 verificato un problema durante l''importazione della voce dell''alias {0}: {1}.\nLa voce dell''alias {0} non \u00E8 stata importata."},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "Comando di importazione completato: {0} voce/i importata/e, {1} voce/i non importata/e o annullata/e"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "Avvertenza: sovrascrittura in corso dell''alias {0} nel file keystore di destinazione"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "La voce dell''alias {0} esiste gi\u00E0. Sovrascrivere? [no]:  "},
        {"Too.many.failures.try.later", "Troppi errori - riprovare"},
        {"Certification.request.stored.in.file.filename.",
                "La richiesta di certificazione \u00E8 memorizzata nel file <{0}>"},
        {"Submit.this.to.your.CA", "Sottomettere alla propria CA"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "Se l'alias non \u00E8 specificato, destalias, srckeypass e destkeypass non dovranno essere specificati"},
        {"Certificate.stored.in.file.filename.",
                "Il certificato \u00E8 memorizzato nel file <{0}>"},
        {"Certificate.reply.was.installed.in.keystore",
                "La risposta del certificato \u00E8 stata installata nel keystore"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "La risposta del certificato non \u00E8 stata installata nel keystore"},
        {"Certificate.was.added.to.keystore",
                "Il certificato \u00E8 stato aggiunto al keystore"},
        {"Certificate.was.not.added.to.keystore",
                "Il certificato non \u00E8 stato aggiunto al keystore"},
        {".Storing.ksfname.", "[Memorizzazione di {0}] in corso"},
        {"alias.has.no.public.key.certificate.",
                "{0} non dispone di chiave pubblica (certificato)"},
        {"Cannot.derive.signature.algorithm",
                "Impossibile derivare l'algoritmo di firma"},
        {"Alias.alias.does.not.exist",
                "L''alias <{0}> non esiste"},
        {"Alias.alias.has.no.certificate",
                "L''alias <{0}> non dispone di certificato"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "Non \u00E8 stata generata la coppia di chiavi, l''alias <{0}> \u00E8 gi\u00E0 esistente"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "Generazione in corso di una coppia di chiavi {1} da {0} bit e di un certificato autofirmato ({2}) con una validit\u00E0 di {3} giorni\n\tper: {4}"},
        {"Enter.key.password.for.alias.", "Immettere la password della chiave per <{0}>"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(INVIO se corrisponde alla password del keystore):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "La password della chiave \u00E8 troppo corta - deve contenere almeno 6 caratteri"},
        {"Too.many.failures.key.not.added.to.keystore",
                "Troppi errori - la chiave non \u00E8 stata aggiunta al keystore"},
        {"Destination.alias.dest.already.exists",
                "L''alias di destinazione <{0}> \u00E8 gi\u00E0 esistente"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "La password \u00E8 troppo corta - deve contenere almeno 6 caratteri"},
        {"Too.many.failures.Key.entry.not.cloned",
                "Numero eccessivo di errori. Il valore della chiave non \u00E8 stato copiato."},
        {"key.password.for.alias.", "password della chiave per <{0}>"},
        {"Keystore.entry.for.id.getName.already.exists",
                "La voce del keystore per <{0}> esiste gi\u00E0"},
        {"Creating.keystore.entry.for.id.getName.",
                "Creazione della voce del keystore per <{0}> in corso..."},
        {"No.entries.from.identity.database.added",
                "Nessuna voce aggiunta dal database delle identit\u00E0"},
        {"Alias.name.alias", "Nome alias: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "Data di creazione: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "Tipo di voce: {0}"},
        {"Certificate.chain.length.", "Lunghezza catena certificati: "},
        {"Certificate.i.1.", "Certificato[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "Impronta digitale certificato (SHA1): "},
        {"Keystore.type.", "Tipo keystore: "},
        {"Keystore.provider.", "Provider keystore: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "Il keystore contiene {0,number,integer} voce"},
        {"Your.keystore.contains.keyStore.size.entries",
                "Il keystore contiene {0,number,integer} voci"},
        {"Failed.to.parse.input", "Impossibile analizzare l'input"},
        {"Empty.input", "Input vuoto"},
        {"Not.X.509.certificate", "Il certificato non \u00E8 X.509"},
        {"alias.has.no.public.key", "{0} non dispone di chiave pubblica"},
        {"alias.has.no.X.509.certificate", "{0} non dispone di certificato X.509"},
        {"New.certificate.self.signed.", "Nuovo certificato (autofirmato):"},
        {"Reply.has.no.certificates", "La risposta non dispone di certificati"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "Impossibile importare il certificato, l''alias <{0}> \u00E8 gi\u00E0 esistente"},
        {"Input.not.an.X.509.certificate", "L'input non \u00E8 un certificato X.509"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "Il certificato esiste gi\u00E0 nel keystore con alias <{0}>"},
        {"Do.you.still.want.to.add.it.no.",
                "Aggiungerlo ugualmente? [no]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "Il certificato esiste gi\u00E0 nel keystore CA con alias <{0}>"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "Aggiungerlo al proprio keystore? [no]:  "},
        {"Trust.this.certificate.no.", "Considerare sicuro questo certificato? [no]:  "},
        {"YES", "S\u00EC"},
        {"New.prompt.", "Nuova {0}: "},
        {"Passwords.must.differ", "Le password non devono coincidere"},
        {"Re.enter.new.prompt.", "Reimmettere un nuovo valore per {0}: "},
        {"Re.enter.new.password.", "Immettere nuovamente la nuova password: "},
        {"They.don.t.match.Try.again", "Non corrispondono. Riprovare."},
        {"Enter.prompt.alias.name.", "Immettere nome alias {0}:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "Immettere un nuovo nome alias\t(premere INVIO per annullare l'importazione della voce):  "},
        {"Enter.alias.name.", "Immettere nome alias:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(INVIO se corrisponde al nome di <{0}>"},
        {".PATTERN.printX509Cert",
                "Proprietario: {0}\nAutorit\u00E0 emittente: {1}\nNumero di serie: {2}\nValido da: {3} a: {4}\nImpronte digitali certificato:\n\t MD5:  {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t Nome algoritmo firma: {8}\n\t Versione: {9}"},
        {"What.is.your.first.and.last.name.",
                "Specificare nome e cognome"},
        {"What.is.the.name.of.your.organizational.unit.",
                "Specificare il nome dell'unit\u00E0 organizzativa"},
        {"What.is.the.name.of.your.organization.",
                "Specificare il nome dell'organizzazione"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "Specificare la localit\u00E0"},
        {"What.is.the.name.of.your.State.or.Province.",
                "Specificare la provincia"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "Specificare il codice a due lettere del paese in cui si trova l'unit\u00E0"},
        {"Is.name.correct.", "Il dato {0} \u00E8 corretto?"},
        {"no", "no"},
        {"yes", "s\u00EC"},
        {"y", "s"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "All''alias <{0}> non \u00E8 associata alcuna chiave"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "L''alias <{0}> fa riferimento a un tipo di voce che non \u00E8 una voce di chiave privata. Il comando -keyclone supporta solo la copia delle voci di chiave private."},

        {".WARNING.WARNING.WARNING.",
            "*****************  Avvertenza Avvertenza Avvertenza  *****************"},
        {"Signer.d.", "Firmatario #%d:"},
        {"Timestamp.", "Indicatore orario:"},
        {"Signature.", "Firma:"},
        {"CRLs.", "CRL:"},
        {"Certificate.owner.", "Proprietario certificato: "},
        {"Not.a.signed.jar.file", "Non \u00E8 un file jar firmato"},
        {"No.certificate.from.the.SSL.server",
                "Nessun certificato dal server SSL"},

        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* L'integrit\u00E0 delle informazioni memorizzate nel keystore *\n* NON \u00E8 stata verificata. Per verificarne l'integrit\u00E0 *\n* \u00E8 necessario fornire la password del keystore.                  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* L'integrit\u00E0 delle informazioni memorizzate nel srckeystore *\n* NON \u00E8 stata verificata. Per verificarne l'integrit\u00E0 *\n* \u00E8 necessario fornire la password del srckeystore.                  *"},

        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "La risposta del certificato non contiene la chiave pubblica per <{0}>"},
        {"Incomplete.certificate.chain.in.reply",
                "Catena dei certificati incompleta nella risposta"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "La catena dei certificati nella risposta non verifica: "},
        {"Top.level.certificate.in.reply.",
                "Certificato di primo livello nella risposta:\n"},
        {".is.not.trusted.", "...non \u00E8 considerato sicuro. "},
        {"Install.reply.anyway.no.", "Installare la risposta? [no]:  "},
        {"NO", "No"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "Le chiavi pubbliche nella risposta e nel keystore non corrispondono"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "La risposta del certificato e il certificato nel keystore sono identici"},
        {"Failed.to.establish.chain.from.reply",
                "Impossibile stabilire la catena dalla risposta"},
        {"n", "n"},
        {"Wrong.answer.try.again", "Risposta errata, riprovare"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "La chiave segreta non \u00E8 stata generata; l''alias <{0}> esiste gi\u00E0"},
        {"Please.provide.keysize.for.secret.key.generation",
                "Specificare il valore -keysize per la generazione della chiave segreta"},

        {"Extensions.", "Estensioni: "},
        {".Empty.value.", "(valore vuoto)"},
        {"Extension.Request.", "Richiesta di estensione:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "Richiesta di certificato PKCS #10 (versione 1.0)\nOggetto: %s\nChiave pubblica: %s formato %s chiave\n"},
        {"Unknown.keyUsage.type.", "Tipo keyUsage sconosciuto: "},
        {"Unknown.extendedkeyUsage.type.", "Tipo extendedkeyUsage sconosciuto: "},
        {"Unknown.AccessDescription.type.", "Tipo AccessDescription sconosciuto: "},
        {"Unrecognized.GeneralName.type.", "Tipo GeneralName non riconosciuto: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "Impossibile contrassegnare questa estensione come critica. "},
        {"Odd.number.of.hex.digits.found.", "\u00C8 stato trovato un numero dispari di cifre esadecimali: "},
        {"Unknown.extension.type.", "Tipo di estensione sconosciuto: "},
        {"command.{0}.is.ambiguous.", "il comando {0} \u00E8 ambiguo:"},

        // policytool
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Avvertenza: non esiste una chiave pubblica per l''alias {0}. Verificare che il keystore sia configurato correttamente."},
        {"Warning.Class.not.found.class", "Avvertenza: classe non trovata: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Avvertenza: argomento o argomenti non validi per il costruttore {0}"},
        {"Illegal.Principal.Type.type", "Tipo principal non valido: {0}"},
        {"Illegal.option.option", "Opzione non valida: {0}"},
        {"Usage.policytool.options.", "Utilizzo: policytool [opzioni]"},
        {".file.file.policy.file.location",
                "  [-file <file>]    posizione del file dei criteri"},
        {"New", "Nuovo"},
        {"Open", "Apri"},
        {"Save", "Salva"},
        {"Save.As", "Salva con nome"},
        {"View.Warning.Log", "Visualizza registro avvertenze"},
        {"Exit", "Esci"},
        {"Add.Policy.Entry", "Aggiungi voce dei criteri"},
        {"Edit.Policy.Entry", "Modifica voce dei criteri"},
        {"Remove.Policy.Entry", "Rimuovi voce dei criteri"},
        {"Edit", "Modifica"},
        {"Retain", "Mantieni"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Avvertenza: il nome file pu\u00F2 includere barre rovesciate con escape. Non \u00E8 necessario eseguire l'escape delle barre rovesciate (se necessario lo strumento esegue l'escape dei caratteri al momento della scrittura del contenuto dei criteri nell'area di memorizzazione persistente).\n\nFare click su Mantieni per conservare il nome immesso, oppure su Modifica per modificare il nome."},

        {"Add.Public.Key.Alias", "Aggiungi alias chiave pubblica"},
        {"Remove.Public.Key.Alias", "Rimuovi alias chiave pubblica"},
        {"File", "File"},
        {"KeyStore", "Keystore"},
        {"Policy.File.", "File dei criteri:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "Impossibile aprire il file di criteri {0}: {1}"},
        {"Policy.Tool", "Strumento criteri"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Si sono verificati errori durante l'apertura della configurazione dei criteri. Consultare il registro delle avvertenze per ulteriori informazioni."},
        {"Error", "Errore"},
        {"OK", "OK"},
        {"Status", "Stato"},
        {"Warning", "Avvertenza"},
        {"Permission.",
                "Autorizzazione:                                                       "},
        {"Principal.Type.", "Tipo principal:"},
        {"Principal.Name.", "Nome principal:"},
        {"Target.Name.",
                "Nome destinazione:                                                    "},
        {"Actions.",
                "Azioni:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "OK per sovrascrivere il file {0}?"},
        {"Cancel", "Annulla"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "Aggiungi principal"},
        {"Edit.Principal", "Modifica principal"},
        {"Remove.Principal", "Rimuovi principal"},
        {"Principals.", "Principal:"},
        {".Add.Permission", "  Aggiungi autorizzazione"},
        {".Edit.Permission", "  Modifica autorizzazione"},
        {"Remove.Permission", "Rimuovi autorizzazione"},
        {"Done", "Fine"},
        {"KeyStore.URL.", "URL keystore:"},
        {"KeyStore.Type.", "Tipo keystore:"},
        {"KeyStore.Provider.", "Provider keystore:"},
        {"KeyStore.Password.URL.", "URL password keystore:"},
        {"Principals", "Principal:"},
        {".Edit.Principal.", "  Modifica principal:"},
        {".Add.New.Principal.", "  Aggiungi nuovo principal:"},
        {"Permissions", "Autorizzazioni"},
        {".Edit.Permission.", "  Modifica autorizzazione:"},
        {".Add.New.Permission.", "  Aggiungi nuova autorizzazione:"},
        {"Signed.By.", "Firmato da:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "Impossibile specificare principal con una classe carattere jolly senza un nome carattere jolly"},
        {"Cannot.Specify.Principal.without.a.Name",
            "Impossibile specificare principal senza un nome"},
        {"Permission.and.Target.Name.must.have.a.value",
                "L'autorizzazione e il nome destinazione non possono essere nulli"},
        {"Remove.this.Policy.Entry.", "Rimuovere questa voce dei criteri?"},
        {"Overwrite.File", "Sovrascrivi file"},
        {"Policy.successfully.written.to.filename",
                "I criteri sono stati scritti in {0}"},
        {"null.filename", "nome file nullo"},
        {"Save.changes.", "Salvare le modifiche?"},
        {"Yes", "S\u00EC"},
        {"No", "No"},
        {"Policy.Entry", "Voce dei criteri"},
        {"Save.Changes", "Salva le modifiche"},
        {"No.Policy.Entry.selected", "Nessuna voce dei criteri selezionata"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "Impossibile aprire il keystore: {0}"},
        {"No.principal.selected", "Nessun principal selezionato"},
        {"No.permission.selected", "Nessuna autorizzazione selezionata"},
        {"name", "nome"},
        {"configuration.type", "tipo di configurazione"},
        {"environment.variable.name", "nome variabile ambiente"},
        {"library.name", "nome libreria"},
        {"package.name", "nome package"},
        {"policy.type", "tipo di criteri"},
        {"property.name", "nome propriet\u00E0"},
        {"Principal.List", "Lista principal"},
        {"Permission.List", "Lista autorizzazioni"},
        {"Code.Base", "Codebase"},
        {"KeyStore.U.R.L.", "URL keystore:"},
        {"KeyStore.Password.U.R.L.", "URL password keystore:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "input nullo/i non valido/i"},
        {"actions.can.only.be.read.", "le azioni possono essere solamente 'lette'"},
        {"permission.name.name.syntax.invalid.",
                "sintassi [{0}] non valida per il nome autorizzazione: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "la classe di credenziali non \u00E8 seguita da un nome e una classe di principal"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "la classe di principal non \u00E8 seguita da un nome principal"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "il nome principal deve essere compreso tra apici"},
        {"Principal.Name.missing.end.quote",
                "apice di chiusura del nome principal mancante"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "la classe principal PrivateCredentialPermission non pu\u00F2 essere un valore carattere jolly (*) se il nome principal a sua volta non \u00E8 un valore carattere jolly (*)"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\tclasse Principal = {0}\n\tNome Principal = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "il nome fornito \u00E8 nullo"},
        {"provided.null.keyword.map", "specificata mappa parole chiave null"},
        {"provided.null.OID.map", "specificata mappa OID null"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "fornito un valore nullo non valido per AccessControlContext"},
        {"invalid.null.action.provided", "fornita un'azione nulla non valida"},
        {"invalid.null.Class.provided", "fornita una classe nulla non valida"},
        {"Subject.", "Oggetto:\n"},
        {".Principal.", "\tPrincipal: "},
        {".Public.Credential.", "\tCredenziale pubblica: "},
        {".Private.Credentials.inaccessible.",
                "\tImpossibile accedere alle credenziali private\n"},
        {".Private.Credential.", "\tCredenziale privata: "},
        {".Private.Credential.inaccessible.",
                "\tImpossibile accedere alla credenziale privata\n"},
        {"Subject.is.read.only", "L'oggetto \u00E8 di sola lettura"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "si \u00E8 tentato di aggiungere un oggetto che non \u00E8 un'istanza di java.security.Principal a un set principal dell'oggetto"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "si \u00E8 tentato di aggiungere un oggetto che non \u00E8 un''istanza di {0}"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "Input nullo non valido: nome"},
        {"No.LoginModules.configured.for.name",
         "Nessun LoginModules configurato per {0}"},
        {"invalid.null.Subject.provided", "fornito un valore nullo non valido per l'oggetto"},
        {"invalid.null.CallbackHandler.provided",
                "fornito un valore nullo non valido per CallbackHandler"},
        {"null.subject.logout.called.before.login",
                "oggetto nullo - il logout \u00E8 stato richiamato prima del login"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "impossibile creare un''istanza di LoginModule {0} in quanto non restituisce un argomento vuoto per il costruttore"},
        {"unable.to.instantiate.LoginModule",
                "impossibile creare un'istanza di LoginModule"},
        {"unable.to.instantiate.LoginModule.",
                "impossibile creare un'istanza di LoginModule: "},
        {"unable.to.find.LoginModule.class.",
                "impossibile trovare la classe LoginModule: "},
        {"unable.to.access.LoginModule.",
                "impossibile accedere a LoginModule "},
        {"Login.Failure.all.modules.ignored",
                "Errore di login: tutti i moduli sono stati ignorati"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: errore durante l''analisi di {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: errore durante l''aggiunta dell''autorizzazione {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: errore durante l''aggiunta della voce:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "impossibile fornire nome alias ({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "impossibile eseguire una sostituzione sull''alias, {0}"},
        {"substitution.value.prefix.unsupported",
                "valore sostituzione, {0}, non supportato"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","il tipo non pu\u00F2 essere nullo"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "Impossibile specificare keystorePasswordURL senza specificare anche il keystore"},
        {"expected.keystore.type", "tipo keystore previsto"},
        {"expected.keystore.provider", "provider di keystore previsto"},
        {"multiple.Codebase.expressions",
                "espressioni Codebase multiple"},
        {"multiple.SignedBy.expressions","espressioni SignedBy multiple"},
        {"SignedBy.has.empty.alias","SignedBy presenta un alias vuoto"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "impossibile specificare un principal con una classe carattere jolly senza un nome carattere jolly"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "previsto codeBase o SignedBy o principal"},
        {"expected.permission.entry", "prevista voce di autorizzazione"},
        {"number.", "numero "},
        {"expected.expect.read.end.of.file.",
                "previsto [{0}], letto [end of file]"},
        {"expected.read.end.of.file.",
                "previsto [;], letto [end of file]"},
        {"line.number.msg", "riga {0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "riga {0}: previsto [{1}], trovato [{2}]"},
        {"null.principalClass.or.principalName",
                "principalClass o principalName nullo"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "Password per token PKCS11 [{0}]: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "impossibile creare un'istanza dei criteri basati sull'oggetto"}
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

