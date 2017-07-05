/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.tools.keytool;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the keytool.
 *
 */
public class Resources extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"STAR",
                "*******************************************"},
        {"STARNN",
                "*******************************************\n\n"},

        // keytool: Help part
        {".OPTION.", " [OPTION]..."},
        {"Options.", "Options:"},
        {"Use.keytool.help.for.all.available.commands",
                 "Use \"keytool -help\" for all available commands"},
        {"Key.and.Certificate.Management.Tool",
                 "Key and Certificate Management Tool"},
        {"Commands.", "Commands:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "Use \"keytool -command_name -help\" for usage of command_name.\n" +
                "Use the -conf <url> option to specify a pre-configured options file."},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "Generates a certificate request"}, //-certreq
        {"Changes.an.entry.s.alias",
                "Changes an entry's alias"}, //-changealias
        {"Deletes.an.entry",
                "Deletes an entry"}, //-delete
        {"Exports.certificate",
                "Exports certificate"}, //-exportcert
        {"Generates.a.key.pair",
                "Generates a key pair"}, //-genkeypair
        {"Generates.a.secret.key",
                "Generates a secret key"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "Generates certificate from a certificate request"}, //-gencert
        {"Generates.CRL", "Generates CRL"}, //-gencrl
        {"Generated.keyAlgName.secret.key",
                "Generated {0} secret key"}, //-genseckey
        {"Generated.keysize.bit.keyAlgName.secret.key",
                "Generated {0}-bit {1} secret key"}, //-genseckey
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "Imports entries from a JDK 1.1.x-style identity database"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "Imports a certificate or a certificate chain"}, //-importcert
        {"Imports.a.password",
                "Imports a password"}, //-importpass
        {"Imports.one.or.all.entries.from.another.keystore",
                "Imports one or all entries from another keystore"}, //-importkeystore
        {"Clones.a.key.entry",
                "Clones a key entry"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "Changes the key password of an entry"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "Lists entries in a keystore"}, //-list
        {"Prints.the.content.of.a.certificate",
                "Prints the content of a certificate"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "Prints the content of a certificate request"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "Prints the content of a CRL file"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "Generates a self-signed certificate"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "Changes the store password of a keystore"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "alias name of the entry to process"}, //-alias
        {"destination.alias",
                "destination alias"}, //-destalias
        {"destination.key.password",
                "destination key password"}, //-destkeypass
        {"destination.keystore.name",
                "destination keystore name"}, //-destkeystore
        {"destination.keystore.password.protected",
                "destination keystore password protected"}, //-destprotected
        {"destination.keystore.provider.name",
                "destination keystore provider name"}, //-destprovidername
        {"destination.keystore.password",
                "destination keystore password"}, //-deststorepass
        {"destination.keystore.type",
                "destination keystore type"}, //-deststoretype
        {"distinguished.name",
                "distinguished name"}, //-dname
        {"X.509.extension",
                "X.509 extension"}, //-ext
        {"output.file.name",
                "output file name"}, //-file and -outfile
        {"input.file.name",
                "input file name"}, //-file and -infile
        {"key.algorithm.name",
                "key algorithm name"}, //-keyalg
        {"key.password",
                "key password"}, //-keypass
        {"key.bit.size",
                "key bit size"}, //-keysize
        {"keystore.name",
                "keystore name"}, //-keystore
        {"new.password",
                "new password"}, //-new
        {"do.not.prompt",
                "do not prompt"}, //-noprompt
        {"password.through.protected.mechanism",
                "password through protected mechanism"}, //-protected
        {"provider.argument",
                "provider argument"}, //-providerarg
        {"provider.class.name",
                "provider class name"}, //-providerclass
        {"provider.name",
                "provider name"}, //-providername
        {"provider.classpath",
                "provider classpath"}, //-providerpath
        {"output.in.RFC.style",
                "output in RFC style"}, //-rfc
        {"signature.algorithm.name",
                "signature algorithm name"}, //-sigalg
        {"source.alias",
                "source alias"}, //-srcalias
        {"source.key.password",
                "source key password"}, //-srckeypass
        {"source.keystore.name",
                "source keystore name"}, //-srckeystore
        {"source.keystore.password.protected",
                "source keystore password protected"}, //-srcprotected
        {"source.keystore.provider.name",
                "source keystore provider name"}, //-srcprovidername
        {"source.keystore.password",
                "source keystore password"}, //-srcstorepass
        {"source.keystore.type",
                "source keystore type"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "SSL server host and port"}, //-sslserver
        {"signed.jar.file",
                "signed jar file"}, //=jarfile
        {"certificate.validity.start.date.time",
                "certificate validity start date/time"}, //-startdate
        {"keystore.password",
                "keystore password"}, //-storepass
        {"keystore.type",
                "keystore type"}, //-storetype
        {"trust.certificates.from.cacerts",
                "trust certificates from cacerts"}, //-trustcacerts
        {"verbose.output",
                "verbose output"}, //-v
        {"validity.number.of.days",
                "validity number of days"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "Serial ID of cert to revoke"}, //-id
        // keytool: Running part
        {"keytool.error.", "keytool error: "},
        {"Illegal.option.", "Illegal option:  "},
        {"Illegal.value.", "Illegal value: "},
        {"Unknown.password.type.", "Unknown password type: "},
        {"Cannot.find.environment.variable.",
                "Cannot find environment variable: "},
        {"Cannot.find.file.", "Cannot find file: "},
        {"Command.option.flag.needs.an.argument.", "Command option {0} needs an argument."},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "Warning:  Different store and key passwords not supported for PKCS12 KeyStores. Ignoring user-specified {0} value."},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-keystore must be NONE if -storetype is {0}"},
        {"Too.many.retries.program.terminated",
                 "Too many retries, program terminated"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "-storepasswd and -keypasswd commands not supported if -storetype is {0}"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "-keypasswd commands not supported if -storetype is PKCS12"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "-keypass and -new can not be specified if -storetype is {0}"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "if -protected is specified, then -storepass, -keypass, and -new must not be specified"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "if -srcprotected is specified, then -srcstorepass and -srckeypass must not be specified"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "if keystore is not password protected, then -storepass, -keypass, and -new must not be specified"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "if source keystore is not password protected, then -srcstorepass and -srckeypass must not be specified"},
        {"Illegal.startdate.value", "Illegal startdate value"},
        {"Validity.must.be.greater.than.zero",
                "Validity must be greater than zero"},
        {"provName.not.a.provider", "{0} not a provider"},
        {"Usage.error.no.command.provided", "Usage error: no command provided"},
        {"Source.keystore.file.exists.but.is.empty.", "Source keystore file exists, but is empty: "},
        {"Please.specify.srckeystore", "Please specify -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "Must not specify both -v and -rfc with 'list' command"},
        {"Key.password.must.be.at.least.6.characters",
                "Key password must be at least 6 characters"},
        {"New.password.must.be.at.least.6.characters",
                "New password must be at least 6 characters"},
        {"Keystore.file.exists.but.is.empty.",
                "Keystore file exists, but is empty: "},
        {"Keystore.file.does.not.exist.",
                "Keystore file does not exist: "},
        {"Must.specify.destination.alias", "Must specify destination alias"},
        {"Must.specify.alias", "Must specify alias"},
        {"Keystore.password.must.be.at.least.6.characters",
                "Keystore password must be at least 6 characters"},
        {"Enter.the.password.to.be.stored.",
                "Enter the password to be stored:  "},
        {"Enter.keystore.password.", "Enter keystore password:  "},
        {"Enter.source.keystore.password.", "Enter source keystore password:  "},
        {"Enter.destination.keystore.password.", "Enter destination keystore password:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "Keystore password is too short - must be at least 6 characters"},
        {"Unknown.Entry.Type", "Unknown Entry Type"},
        {"Too.many.failures.Alias.not.changed", "Too many failures. Alias not changed"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "Entry for alias {0} successfully imported."},
        {"Entry.for.alias.alias.not.imported.", "Entry for alias {0} not imported."},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "Problem importing entry for alias {0}: {1}.\nEntry for alias {0} not imported."},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "Import command completed:  {0} entries successfully imported, {1} entries failed or cancelled"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "Warning: Overwriting existing alias {0} in destination keystore"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "Existing entry alias {0} exists, overwrite? [no]:  "},
        {"Too.many.failures.try.later", "Too many failures - try later"},
        {"Certification.request.stored.in.file.filename.",
                "Certification request stored in file <{0}>"},
        {"Submit.this.to.your.CA", "Submit this to your CA"},
        {"if.alias.not.specified.destalias.and.srckeypass.must.not.be.specified",
            "if alias not specified, destalias and srckeypass must not be specified"},
        {"The.destination.pkcs12.keystore.has.different.storepass.and.keypass.Please.retry.with.destkeypass.specified.",
            "The destination pkcs12 keystore has different storepass and keypass. Please retry with -destkeypass specified."},
        {"Certificate.stored.in.file.filename.",
                "Certificate stored in file <{0}>"},
        {"Certificate.reply.was.installed.in.keystore",
                "Certificate reply was installed in keystore"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "Certificate reply was not installed in keystore"},
        {"Certificate.was.added.to.keystore",
                "Certificate was added to keystore"},
        {"Certificate.was.not.added.to.keystore",
                "Certificate was not added to keystore"},
        {".Storing.ksfname.", "[Storing {0}]"},
        {"alias.has.no.public.key.certificate.",
                "{0} has no public key (certificate)"},
        {"Cannot.derive.signature.algorithm",
                "Cannot derive signature algorithm"},
        {"Alias.alias.does.not.exist",
                "Alias <{0}> does not exist"},
        {"Alias.alias.has.no.certificate",
                "Alias <{0}> has no certificate"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "Key pair not generated, alias <{0}> already exists"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "Generating {0} bit {1} key pair and self-signed certificate ({2}) with a validity of {3} days\n\tfor: {4}"},
        {"Enter.key.password.for.alias.", "Enter key password for <{0}>"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(RETURN if same as keystore password):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "Key password is too short - must be at least 6 characters"},
        {"Too.many.failures.key.not.added.to.keystore",
                "Too many failures - key not added to keystore"},
        {"Destination.alias.dest.already.exists",
                "Destination alias <{0}> already exists"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "Password is too short - must be at least 6 characters"},
        {"Too.many.failures.Key.entry.not.cloned",
                "Too many failures. Key entry not cloned"},
        {"key.password.for.alias.", "key password for <{0}>"},
        {"Keystore.entry.for.id.getName.already.exists",
                "Keystore entry for <{0}> already exists"},
        {"Creating.keystore.entry.for.id.getName.",
                "Creating keystore entry for <{0}> ..."},
        {"No.entries.from.identity.database.added",
                "No entries from identity database added"},
        {"Alias.name.alias", "Alias name: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "Creation date: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "Entry type: {0}"},
        {"Certificate.chain.length.", "Certificate chain length: "},
        {"Certificate.i.1.", "Certificate[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "Certificate fingerprint (SHA1): "},
        {"Keystore.type.", "Keystore type: "},
        {"Keystore.provider.", "Keystore provider: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "Your keystore contains {0,number,integer} entry"},
        {"Your.keystore.contains.keyStore.size.entries",
                "Your keystore contains {0,number,integer} entries"},
        {"Failed.to.parse.input", "Failed to parse input"},
        {"Empty.input", "Empty input"},
        {"Not.X.509.certificate", "Not X.509 certificate"},
        {"alias.has.no.public.key", "{0} has no public key"},
        {"alias.has.no.X.509.certificate", "{0} has no X.509 certificate"},
        {"New.certificate.self.signed.", "New certificate (self-signed):"},
        {"Reply.has.no.certificates", "Reply has no certificates"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "Certificate not imported, alias <{0}> already exists"},
        {"Input.not.an.X.509.certificate", "Input not an X.509 certificate"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "Certificate already exists in keystore under alias <{0}>"},
        {"Do.you.still.want.to.add.it.no.",
                "Do you still want to add it? [no]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "Certificate already exists in system-wide CA keystore under alias <{0}>"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "Do you still want to add it to your own keystore? [no]:  "},
        {"Trust.this.certificate.no.", "Trust this certificate? [no]:  "},
        {"YES", "YES"},
        {"New.prompt.", "New {0}: "},
        {"Passwords.must.differ", "Passwords must differ"},
        {"Re.enter.new.prompt.", "Re-enter new {0}: "},
        {"Re.enter.password.", "Re-enter password: "},
        {"Re.enter.new.password.", "Re-enter new password: "},
        {"They.don.t.match.Try.again", "They don't match. Try again"},
        {"Enter.prompt.alias.name.", "Enter {0} alias name:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "Enter new alias name\t(RETURN to cancel import for this entry):  "},
        {"Enter.alias.name.", "Enter alias name:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(RETURN if same as for <{0}>)"},
        {".PATTERN.printX509Cert",
                "Owner: {0}\nIssuer: {1}\nSerial number: {2}\nValid from: {3} until: {4}\nCertificate fingerprints:\n\t MD5:  {5}\n\t SHA1: {6}\n\t SHA256: {7}\nSignature algorithm name: {8}\nSubject Public Key Algorithm: {9} ({10,number,#})\nVersion: {11}"},
        {"What.is.your.first.and.last.name.",
                "What is your first and last name?"},
        {"What.is.the.name.of.your.organizational.unit.",
                "What is the name of your organizational unit?"},
        {"What.is.the.name.of.your.organization.",
                "What is the name of your organization?"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "What is the name of your City or Locality?"},
        {"What.is.the.name.of.your.State.or.Province.",
                "What is the name of your State or Province?"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "What is the two-letter country code for this unit?"},
        {"Is.name.correct.", "Is {0} correct?"},
        {"no", "no"},
        {"yes", "yes"},
        {"y", "y"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "Alias <{0}> has no key"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "Alias <{0}> references an entry type that is not a private key entry.  The -keyclone command only supports cloning of private key entries"},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "Signer #%d:"},
        {"Timestamp.", "Timestamp:"},
        {"Signature.", "Signature:"},
        {"CRLs.", "CRLs:"},
        {"Certificate.owner.", "Certificate owner: "},
        {"Not.a.signed.jar.file", "Not a signed jar file"},
        {"No.certificate.from.the.SSL.server",
                "No certificate from the SSL server"},

        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* The integrity of the information stored in your keystore  *\n" +
            "* has NOT been verified!  In order to verify its integrity, *\n" +
            "* you must provide your keystore password.                  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* The integrity of the information stored in the srckeystore*\n" +
            "* has NOT been verified!  In order to verify its integrity, *\n" +
            "* you must provide the srckeystore password.                *"},

        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "Certificate reply does not contain public key for <{0}>"},
        {"Incomplete.certificate.chain.in.reply",
                "Incomplete certificate chain in reply"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "Certificate chain in reply does not verify: "},
        {"Top.level.certificate.in.reply.",
                "Top-level certificate in reply:\n"},
        {".is.not.trusted.", "... is not trusted. "},
        {"Install.reply.anyway.no.", "Install reply anyway? [no]:  "},
        {"NO", "NO"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "Public keys in reply and keystore don't match"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "Certificate reply and certificate in keystore are identical"},
        {"Failed.to.establish.chain.from.reply",
                "Failed to establish chain from reply"},
        {"n", "n"},
        {"Wrong.answer.try.again", "Wrong answer, try again"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "Secret Key not generated, alias <{0}> already exists"},
        {"Please.provide.keysize.for.secret.key.generation",
                "Please provide -keysize for secret key generation"},

        {"verified.by.s.in.s", "Verified by %s in %s"},
        {"warning.not.verified.make.sure.keystore.is.correct",
            "WARNING: not verified. Make sure -keystore is correct."},

        {"Extensions.", "Extensions: "},
        {".Empty.value.", "(Empty value)"},
        {"Extension.Request.", "Extension Request:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "PKCS #10 Certificate Request (Version 1.0)\n" +
                "Subject: %s\nPublic Key: %s format %s key\n"},
        {"Unknown.keyUsage.type.", "Unknown keyUsage type: "},
        {"Unknown.extendedkeyUsage.type.", "Unknown extendedkeyUsage type: "},
        {"Unknown.AccessDescription.type.", "Unknown AccessDescription type: "},
        {"Unrecognized.GeneralName.type.", "Unrecognized GeneralName type: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "This extension cannot be marked as critical. "},
        {"Odd.number.of.hex.digits.found.", "Odd number of hex digits found: "},
        {"Unknown.extension.type.", "Unknown extension type: "},
        {"command.{0}.is.ambiguous.", "command {0} is ambiguous:"}
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
