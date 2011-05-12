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
public class Resources_pt_BR extends java.util.ListResourceBundle {

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
        {".OPTION.", " [Op\u00E7\u00E3o]..."},
        {"Options.", "Op\u00E7\u00F5es:"},
        {"Use.keytool.help.for.all.available.commands",
                 "Use \"keytool -help\" para todos os comandos dispon\u00EDveis"},
        {"Key.and.Certificate.Management.Tool",
                 "Ferramenta de Gerenciamento de Chave e Certificado"},
        {"Commands.", "Comandos:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "Use \"keytool -command_name -help\" para uso de command_name"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "Gera uma solicita\u00E7\u00E3o de certificado"}, //-certreq
        {"Changes.an.entry.s.alias",
                "Altera um alias de entrada"}, //-changealias
        {"Deletes.an.entry",
                "Deleta uma entrada"}, //-delete
        {"Exports.certificate",
                "Exporta o certificado"}, //-exportcert
        {"Generates.a.key.pair",
                "Gera um par de chaves"}, //-genkeypair
// translation of "secret" key should be different to "private" key.
        {"Generates.a.secret.key",
                "Gera uma chave Secreta"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "Gera um certificado de uma solicita\u00E7\u00E3o de certificado"}, //-gencert
        {"Generates.CRL", "Gera CRL"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "Importa entradas de um banco de dados de identidade JDK 1.1.x-style"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "Importa um certificado ou uma cadeia de certificados"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "Importa uma ou todas as entradas de outra \u00E1rea de armazenamento de chaves"}, //-importkeystore
        {"Clones.a.key.entry",
                "Clona uma entrada de chave"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "Altera a senha da chave de uma entrada"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "Lista entradas em uma \u00E1rea de armazenamento de chaves"}, //-list
        {"Prints.the.content.of.a.certificate",
                "Imprime o conte\u00FAdo de um certificado"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "Imprime o conte\u00FAdo de uma solicita\u00E7\u00E3o de certificado"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "Imprime o conte\u00FAdo de um arquivo CRL"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "Gera um certificado autoassinado"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "Altera a senha de armazenamento de uma \u00E1rea de armazenamento de chaves"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "nome do alias da entrada a ser processada"}, //-alias
        {"destination.alias",
                "alias de destino"}, //-destalias
        {"destination.key.password",
                "senha da chave de destino"}, //-destkeypass
        {"destination.keystore.name",
                "nome da \u00E1rea de armazenamento de chaves de destino"}, //-destkeystore
        {"destination.keystore.password.protected",
                "senha protegida da \u00E1rea de armazenamento de chaves de destino"}, //-destprotected
        {"destination.keystore.provider.name",
                "nome do fornecedor da \u00E1rea de armazenamento de chaves de destino"}, //-destprovidername
        {"destination.keystore.password",
                "senha da \u00E1rea de armazenamento de chaves de destino"}, //-deststorepass
        {"destination.keystore.type",
                "tipo de \u00E1rea de armazenamento de chaves de destino"}, //-deststoretype
        {"distinguished.name",
                "nome distinto"}, //-dname
        {"X.509.extension",
                "extens\u00E3o X.509"}, //-ext
        {"output.file.name",
                "nome do arquivo de sa\u00EDda"}, //-file and -outfile
        {"input.file.name",
                "nome do arquivo de entrada"}, //-file and -infile
        {"key.algorithm.name",
                "nome do algoritmo da chave"}, //-keyalg
        {"key.password",
                "senha da chave"}, //-keypass
        {"key.bit.size",
                "tamanho do bit da chave"}, //-keysize
        {"keystore.name",
                "nome da \u00E1rea de armazenamento de chaves"}, //-keystore
        {"new.password",
                "nova senha"}, //-new
        {"do.not.prompt",
                "n\u00E3o perguntar"}, //-noprompt
        {"password.through.protected.mechanism",
                "senha por meio de mecanismo protegido"}, //-protected
        {"provider.argument",
                "argumento do fornecedor"}, //-providerarg
        {"provider.class.name",
                "nome da classe do fornecedor"}, //-providerclass
        {"provider.name",
                "nome do fornecedor"}, //-providername
        {"provider.classpath",
                "classpath do fornecedor"}, //-providerpath
        {"output.in.RFC.style",
                "sa\u00EDda no estilo RFC"}, //-rfc
        {"signature.algorithm.name",
                "nome do algoritmo de assinatura"}, //-sigalg
        {"source.alias",
                "alias de origem"}, //-srcalias
        {"source.key.password",
                "senha da chave de origem"}, //-srckeypass
        {"source.keystore.name",
                "nome da \u00E1rea de armazenamento de chaves de origem"}, //-srckeystore
        {"source.keystore.password.protected",
                "senha protegida da \u00E1rea de armazenamento de chaves de origem"}, //-srcprotected
        {"source.keystore.provider.name",
                "nome do fornecedor da \u00E1rea de armazenamento de chaves de origem"}, //-srcprovidername
        {"source.keystore.password",
                "senha da \u00E1rea de armazenamento de chaves de origem"}, //-srcstorepass
        {"source.keystore.type",
                "tipo de \u00E1rea de armazenamento de chaves de origem"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "porta e host do servidor SSL"}, //-sslserver
        {"signed.jar.file",
                "arquivo jar assinado"}, //=jarfile
        {"certificate.validity.start.date.time",
                "data/hora inicial de validade do certificado"}, //-startdate
        {"keystore.password",
                "senha da \u00E1rea de armazenamento de chaves"}, //-storepass
        {"keystore.type",
                "tipo de \u00E1rea de armazenamento de chaves"}, //-storetype
        {"trust.certificates.from.cacerts",
                "certificados confi\u00E1veis do cacerts"}, //-trustcacerts
        {"verbose.output",
                "sa\u00EDda detalhada"}, //-v
        {"validity.number.of.days",
                "n\u00FAmero de dias da validade"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "ID de s\u00E9rie do certificado a ser revogado"}, //-id
        // keytool: Running part
        {"keytool.error.", "erro de keytool: "},
        {"Illegal.option.", "Op\u00E7\u00E3o inv\u00E1lida:  "},
        {"Illegal.value.", "Valor inv\u00E1lido: "},
        {"Unknown.password.type.", "Tipo de senha desconhecido: "},
        {"Cannot.find.environment.variable.",
                "N\u00E3o \u00E9 poss\u00EDvel localizar a vari\u00E1vel do ambiente: "},
        {"Cannot.find.file.", "N\u00E3o \u00E9 poss\u00EDvel localizar o arquivo: "},
        {"Command.option.flag.needs.an.argument.", "A op\u00E7\u00E3o de comando {0} precisa de um argumento."},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "Advert\u00EAncia: Senhas de chave e de armazenamento diferentes n\u00E3o suportadas para KeyStores PKCS12. Ignorando valor {0} especificado pelo usu\u00E1rio."},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-keystore deve ser NONE se -storetype for {0}"},
        {"Too.many.retries.program.terminated",
                 "Excesso de tentativas de repeti\u00E7\u00E3o; programa finalizado"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "comandos -storepasswd e -keypasswd n\u00E3o suportados se -storetype for {0}"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "comandos -keypasswd n\u00E3o suportados se -storetype for PKCS12"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "-keypass e -new n\u00E3o podem ser especificados se -storetype for {0}"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "se -protected for especificado, ent\u00E3o -storepass, -keypass e -new n\u00E3o dever\u00E3o ser especificados"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "se -srcprotected for especificado, ent\u00E3o -srcstorepass e -srckeypass n\u00E3o dever\u00E3o ser especificados"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "se a \u00E1rea de armazenamento de chaves n\u00E3o estiver protegida por senha, ent\u00E3o -storepass, -keypass e -new n\u00E3o dever\u00E3o ser especificados"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "se a \u00E1rea de armazenamento de chaves de origem n\u00E3o estiver protegida por senha, ent\u00E3o -srcstorepass e -srckeypass n\u00E3o dever\u00E3o ser especificados"},
        {"Illegal.startdate.value", "valor da data inicial inv\u00E1lido"},
        {"Validity.must.be.greater.than.zero",
                "A validade deve ser maior do que zero"},
        {"provName.not.a.provider", "{0} n\u00E3o \u00E9 um fornecedor"},
        {"Usage.error.no.command.provided", "Erro de uso: nenhum comando fornecido"},
        {"Source.keystore.file.exists.but.is.empty.", "O arquivo da \u00E1rea de armazenamento de chaves de origem existe, mas est\u00E1 vazio: "},
        {"Please.specify.srckeystore", "Especifique -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "N\u00E3o devem ser especificados -v e -rfc com o comando 'list'"},
        {"Key.password.must.be.at.least.6.characters",
                "A senha da chave deve ter, no m\u00EDnimo, 6 caracteres"},
        {"New.password.must.be.at.least.6.characters",
                "A nova senha deve ter, no m\u00EDnimo, 6 caracteres"},
        {"Keystore.file.exists.but.is.empty.",
                "O arquivo da \u00E1rea de armazenamento de chaves existe, mas est\u00E1 vazio: "},
        {"Keystore.file.does.not.exist.",
                "O arquivo da \u00E1rea de armazenamento de chaves n\u00E3o existe. "},
        {"Must.specify.destination.alias", "Deve ser especificado um alias de destino"},
        {"Must.specify.alias", "Deve ser especificado um alias"},
        {"Keystore.password.must.be.at.least.6.characters",
                "A senha da \u00E1rea de armazenamento de chaves deve ter, no m\u00EDnimo, 6 caracteres"},
        {"Enter.keystore.password.", "Informe a senha da \u00E1rea de armazenamento de chaves:  "},
        {"Enter.source.keystore.password.", "Informe a senha da \u00E1rea de armazenamento de chaves de origem:  "},
        {"Enter.destination.keystore.password.", "Informe a senha da \u00E1rea de armazenamento de chaves de destino:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "A senha da \u00E1rea de armazenamento de chaves \u00E9 muito curta - ela deve ter, no m\u00EDnimo, 6 caracteres"},
        {"Unknown.Entry.Type", "Tipo de Entrada Desconhecido"},
        {"Too.many.failures.Alias.not.changed", "Excesso de falhas. Alias n\u00E3o alterado"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "Entrada do alias {0} importada com \u00EAxito."},
        {"Entry.for.alias.alias.not.imported.", "Entrada do alias {0} n\u00E3o importada."},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "Problema ao importar a entrada do alias {0}: {1}.\nEntrada do alias {0} n\u00E3o importada."},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "Comando de importa\u00E7\u00E3o conclu\u00EDdo:  {0} entradas importadas com \u00EAxito, {1} entradas falharam ou foram canceladas"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "Advert\u00EAncia: Substitui\u00E7\u00E3o do alias {0} existente na \u00E1rea de armazenamento de chaves de destino"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "Entrada j\u00E1 existente no alias {0}, substituir? [n\u00E3o]:  "},
        {"Too.many.failures.try.later", "Excesso de falhas - tente mais tarde"},
        {"Certification.request.stored.in.file.filename.",
                "Solicita\u00E7\u00E3o de certificado armazenada no arquivo <{0}>"},
        {"Submit.this.to.your.CA", "Submeter \u00E0 CA"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "se o alias n\u00E3o estiver especificado, destalias, srckeypass e destkeypass n\u00E3o dever\u00E3o ser especificados"},
        {"Certificate.stored.in.file.filename.",
                "Certificado armazenado no arquivo <{0}>"},
        {"Certificate.reply.was.installed.in.keystore",
                "A resposta do certificado foi instalada na \u00E1rea de armazenamento de chaves"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "A resposta do certificado n\u00E3o foi instalada na \u00E1rea de armazenamento de chaves"},
        {"Certificate.was.added.to.keystore",
                "O certificado foi adicionado \u00E0 \u00E1rea de armazenamento de chaves"},
        {"Certificate.was.not.added.to.keystore",
                "O certificado n\u00E3o foi adicionado \u00E0 \u00E1rea de armazenamento de chaves"},
        {".Storing.ksfname.", "[Armazenando {0}]"},
        {"alias.has.no.public.key.certificate.",
                "{0} n\u00E3o tem chave p\u00FAblica (certificado)"},
        {"Cannot.derive.signature.algorithm",
                "N\u00E3o \u00E9 poss\u00EDvel obter um algoritmo de assinatura"},
        {"Alias.alias.does.not.exist",
                "O alias <{0}> n\u00E3o existe"},
        {"Alias.alias.has.no.certificate",
                "O alias <{0}> n\u00E3o tem certificado"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "Par de chaves n\u00E3o gerado; o alias <{0}> j\u00E1 existe"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "Gerando o par de chaves {1} de {0} bit e o certificado autoassinado ({2}) com uma validade de {3} dias\n\tpara: {4}"},
        {"Enter.key.password.for.alias.", "Informar a senha da chave de <{0}>"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(RETURN se for igual \u00E0 senha da \u00E1rea do armazenamento de chaves):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "A senha da chave \u00E9 muito curta - deve ter, no m\u00EDnimo, 6 caracteres"},
        {"Too.many.failures.key.not.added.to.keystore",
                "Excesso de falhas - chave n\u00E3o adicionada a \u00E1rea de armazenamento de chaves"},
        {"Destination.alias.dest.already.exists",
                "O alias de destino <{0}> j\u00E1 existe"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "A senha \u00E9 muito curta - deve ter, no m\u00EDnimo, 6 caracteres"},
        {"Too.many.failures.Key.entry.not.cloned",
                "Excesso de falhas. Entrada da chave n\u00E3o clonada"},
        {"key.password.for.alias.", "senha da chave de <{0}>"},
        {"Keystore.entry.for.id.getName.already.exists",
                "A entrada da \u00E1rea do armazenamento de chaves de <{0}> j\u00E1 existe"},
        {"Creating.keystore.entry.for.id.getName.",
                "Criando entrada da \u00E1rea do armazenamento de chaves para <{0}> ..."},
        {"No.entries.from.identity.database.added",
                "Nenhuma entrada adicionada do banco de dados de identidades"},
        {"Alias.name.alias", "Nome do alias: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "Data de cria\u00E7\u00E3o: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "Tipo de entrada: {0}"},
        {"Certificate.chain.length.", "Comprimento da cadeia de certificados: "},
        {"Certificate.i.1.", "Certificado[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "Fingerprint (MD5) do certificado: "},
        {"Keystore.type.", "Tipo de \u00E1rea de armazenamento de chaves: "},
        {"Keystore.provider.", "Fornecedor da \u00E1rea de armazenamento de chaves: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "Sua \u00E1rea de armazenamento de chaves cont\u00E9m {0,number,integer} entrada"},
        {"Your.keystore.contains.keyStore.size.entries",
                "Sua \u00E1rea de armazenamento de chaves cont\u00E9m {0,number,integer} entradas"},
        {"Failed.to.parse.input", "Falha durante o parse da entrada"},
        {"Empty.input", "Entrada vazia"},
        {"Not.X.509.certificate", "N\u00E3o \u00E9 um certificado X.509"},
        {"alias.has.no.public.key", "{0} n\u00E3o tem chave p\u00FAblica"},
        {"alias.has.no.X.509.certificate", "{0} n\u00E3o tem certificado X.509"},
        {"New.certificate.self.signed.", "Novo certificado (autoassinado):"},
        {"Reply.has.no.certificates", "A resposta n\u00E3o tem certificado"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "Certificado n\u00E3o importado, o alias <{0}> j\u00E1 existe"},
        {"Input.not.an.X.509.certificate", "A entrada n\u00E3o \u00E9 um certificado X.509"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "O certificado j\u00E1 existe no armazenamento de chaves no alias <{0}>"},
        {"Do.you.still.want.to.add.it.no.",
                "Ainda deseja adicion\u00E1-lo? [n\u00E3o]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "O certificado j\u00E1 existe na \u00E1rea de armazenamento de chaves da CA em todo o sistema no alias <{0}>"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "Ainda deseja adicion\u00E1-lo \u00E0 sua \u00E1rea de armazenamento de chaves? [n\u00E3o]:  "},
        {"Trust.this.certificate.no.", "Confiar neste certificado? [n\u00E3o]:  "},
        {"YES", "Sim"},
        {"New.prompt.", "Nova {0}: "},
        {"Passwords.must.differ", "As senhas devem ser diferentes"},
        {"Re.enter.new.prompt.", "Informe novamente a nova {0}: "},
        {"Re.enter.new.password.", "Informe novamente a nova senha: "},
        {"They.don.t.match.Try.again", "Elas n\u00E3o correspondem. Tente novamente"},
        {"Enter.prompt.alias.name.", "Informe o nome do alias {0}:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "Informe o novo nome do alias\t(RETURN para cancelar a importa\u00E7\u00E3o desta entrada):  "},
        {"Enter.alias.name.", "Informe o nome do alias:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(RETURN se for igual ao de <{0}>)"},
        {".PATTERN.printX509Cert",
                "Propriet\u00E1rio: {0}\nEmissor: {1}\nN\u00FAmero de s\u00E9rie: {2}\nV\u00E1lido de: {3} a: {4}\nFingerprints do certificado:\n\t MD5:  {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t Nome do algoritmo de assinatura: {8}\n\t Vers\u00E3o: {9}"},
        {"What.is.your.first.and.last.name.",
                "Qual \u00E9 o seu nome e o seu sobrenome?"},
        {"What.is.the.name.of.your.organizational.unit.",
                "Qual \u00E9 o nome da sua unidade organizacional?"},
        {"What.is.the.name.of.your.organization.",
                "Qual \u00E9 o nome da sua empresa?"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "Qual \u00E9 o nome da sua Cidade ou Localidade?"},
        {"What.is.the.name.of.your.State.or.Province.",
                "Qual \u00E9 o nome do seu Estado ou Munic\u00EDpio?"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "Quais s\u00E3o as duas letras do c\u00F3digo do pa\u00EDs desta unidade?"},
        {"Is.name.correct.", "{0} Est\u00E1 correto?"},
        {"no", "n\u00E3o"},
        {"yes", "sim"},
        {"y", "s"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "O alias <{0}> n\u00E3o tem chave"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "O alias <{0}> faz refer\u00EAncia a um tipo de entrada que n\u00E3o \u00E9 uma entrada de chave privada. O comando -keyclone oferece suporte somente \u00E0 clonagem de entradas de chave privada"},

        {".WARNING.WARNING.WARNING.",
            "*****************  Advert\u00EAncia Advert\u00EAncia Advert\u00EAncia  *****************"},
        {"Signer.d.", "Signat\u00E1rio #%d:"},
        {"Timestamp.", "Timestamp:"},
        {"Signature.", "Assinatura:"},
        {"CRLs.", "CRLs:"},
        {"Certificate.owner.", "Propriet\u00E1rio do certificado: "},
        {"Not.a.signed.jar.file", "N\u00E3o \u00E9 um arquivo jar assinado"},
        {"No.certificate.from.the.SSL.server",
                "N\u00E3o \u00E9 um certificado do servidor SSL"},

        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* A integridade das informa\u00E7\u00F5es armazenadas na sua \u00E1rea de armazenamento de chaves  *\n* N\u00C3O foi verificada!  Para que seja poss\u00EDvel verificar sua integridade, *\n* voc\u00EA deve fornecer a senha da \u00E1rea de armazenamento de chaves.                  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* A integridade das informa\u00E7\u00F5es armazenadas no srckeystore  *\n* N\u00C3O foi verificada!  Para que seja poss\u00EDvel verificar sua integridade, *\n* voc\u00EA deve fornecer a senha do srckeystore.                  *"},

        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "A resposta do certificado n\u00E3o cont\u00E9m a chave p\u00FAblica de <{0}>"},
        {"Incomplete.certificate.chain.in.reply",
                "Cadeia de certificados incompleta na resposta"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "A cadeia de certificados da resposta n\u00E3o verifica: "},
        {"Top.level.certificate.in.reply.",
                "Certificado de n\u00EDvel superior na resposta:\n"},
        {".is.not.trusted.", "... n\u00E3o \u00E9 confi\u00E1vel. "},
        {"Install.reply.anyway.no.", "Instalar resposta assim mesmo? [n\u00E3o]:  "},
        {"NO", "N\u00E3o"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "As chaves p\u00FAblicas da resposta e da \u00E1rea de armazenamento de chaves n\u00E3o correspondem"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "O certificado da resposta e o certificado da \u00E1rea de armazenamento de chaves s\u00E3o id\u00EAnticos"},
        {"Failed.to.establish.chain.from.reply",
                "Falha ao estabelecer a cadeia a partir da resposta"},
        {"n", "n"},
        {"Wrong.answer.try.again", "Resposta errada; tente novamente"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "Chave secreta n\u00E3o gerada; o alias <{0}> j\u00E1 existe"},
        {"Please.provide.keysize.for.secret.key.generation",
                "Forne\u00E7a o -keysize para a gera\u00E7\u00E3o da chave secreta"},

        {"Extensions.", "Extens\u00F5es: "},
        {".Empty.value.", "(Valor vazio)"},
        {"Extension.Request.", "Solicita\u00E7\u00E3o de Extens\u00E3o:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "Solicita\u00E7\u00E3o do Certificado PKCS #10 (Vers\u00E3o 1.0)\nAssunto: %s\nChave P\u00FAblica: %s formato %s chave\n"},
        {"Unknown.keyUsage.type.", "Tipo de keyUsage desconhecido: "},
        {"Unknown.extendedkeyUsage.type.", "Tipo de extendedkeyUsage desconhecido: "},
        {"Unknown.AccessDescription.type.", "Tipo de AccessDescription desconhecido: "},
        {"Unrecognized.GeneralName.type.", "Tipo de GeneralName n\u00E3o reconhecido: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "Esta extens\u00E3o n\u00E3o pode ser marcada como cr\u00EDtica. "},
        {"Odd.number.of.hex.digits.found.", "Encontrado n\u00FAmero \u00EDmpar de seis d\u00EDgitos: "},
        {"Unknown.extension.type.", "Tipo de extens\u00E3o desconhecido: "},
        {"command.{0}.is.ambiguous.", "o comando {0} \u00E9 amb\u00EDguo:"},

        // policytool
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Advert\u00EAncia: N\u00E3o existe uma chave p\u00FAblica para o alias {0}. Certifique-se de que um KeyStore esteja configurado adequadamente."},
        {"Warning.Class.not.found.class", "Advert\u00EAncia: Classe n\u00E3o encontrada: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Advert\u00EAncia: Argumento(s) inv\u00E1lido(s) para o construtor: {0}"},
        {"Illegal.Principal.Type.type", "Tipo Principal Inv\u00E1lido: {0}"},
        {"Illegal.option.option", "Op\u00E7\u00E3o inv\u00E1lida: {0}"},
        {"Usage.policytool.options.", "Uso: policytool [op\u00E7\u00F5es]"},
        {".file.file.policy.file.location",
                "  [-file <arquivo>]    localiza\u00E7\u00E3o do arquivo de pol\u00EDtica"},
        {"New", "Novo"},
        {"Open", "Abrir"},
        {"Save", "Salvar"},
        {"Save.As", "Salvar Como"},
        {"View.Warning.Log", "Exibir Log de Advert\u00EAncias"},
        {"Exit", "Sair"},
        {"Add.Policy.Entry", "Adicionar Entrada de Pol\u00EDtica"},
        {"Edit.Policy.Entry", "Editar Entrada de Pol\u00EDtica"},
        {"Remove.Policy.Entry", "Remover Entrada de Pol\u00EDtica"},
        {"Edit", "Editar"},
        {"Retain", "Reter"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Advert\u00EAncia: O nome do arquivo pode conter caracteres de escape barra invertida. N\u00E3o \u00E9 necess\u00E1rio fazer o escape dos caracteres de barra invertida (a ferramenta faz o escape dos caracteres conforme necess\u00E1rio ao gravar o conte\u00FAdo da pol\u00EDtica no armazenamento persistente).\n\nClique em Reter para reter o nome da entrada ou clique em Editar para edit\u00E1-lo."},

        {"Add.Public.Key.Alias", "Adicionar Alias de Chave P\u00FAblica"},
        {"Remove.Public.Key.Alias", "Remover Alias de Chave P\u00FAblica"},
        {"File", "Arquivo"},
        {"KeyStore", "KeyStore"},
        {"Policy.File.", "Arquivo de Pol\u00EDtica:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "N\u00E3o foi poss\u00EDvel abrir o arquivo de pol\u00EDtica: {0}: {1}"},
        {"Policy.Tool", "Ferramenta de Pol\u00EDtica"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Erros durante a abertura da configura\u00E7\u00E3o da pol\u00EDtica. Consulte o Log de Advert\u00EAncias para obter mais informa\u00E7\u00F5es."},
        {"Error", "Erro"},
        {"OK", "OK"},
        {"Status", "Status"},
        {"Warning", "Advert\u00EAncia"},
        {"Permission.",
                "Permiss\u00E3o:                                                       "},
        {"Principal.Type.", "Tipo do Principal:"},
        {"Principal.Name.", "Nome do Principal:"},
        {"Target.Name.",
                "Nome do Alvo:                                                    "},
        {"Actions.",
                "A\u00E7\u00F5es:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "Est\u00E1 correto substituir o arquivo existente {0}?"},
        {"Cancel", "Cancelar"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "Adicionar Principal"},
        {"Edit.Principal", "Editar Principal"},
        {"Remove.Principal", "Remover Principal"},
        {"Principals.", "Principais:"},
        {".Add.Permission", "  Adicionar Permiss\u00E3o"},
        {".Edit.Permission", "  Editar Permiss\u00E3o"},
        {"Remove.Permission", "Remover Permiss\u00E3o"},
        {"Done", "Conclu\u00EDdo"},
        {"KeyStore.URL.", "URL do KeyStore:"},
        {"KeyStore.Type.", "Tipo de KeyStore:"},
        {"KeyStore.Provider.", "Fornecedor de KeyStore:"},
        {"KeyStore.Password.URL.", "URL da Senha do KeyStore:"},
        {"Principals", "Principais"},
        {".Edit.Principal.", "  Editar Principal:"},
        {".Add.New.Principal.", "  Adicionar Novo Principal:"},
        {"Permissions", "Permiss\u00F5es"},
        {".Edit.Permission.", "  Editar Permiss\u00E3o:"},
        {".Add.New.Permission.", "  Adicionar Nova Permiss\u00E3o:"},
        {"Signed.By.", "Assinado por:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "N\u00E3o \u00E9 Poss\u00EDvel Especificar um Principal com uma Classe de Curinga sem um Nome de Curinga"},
        {"Cannot.Specify.Principal.without.a.Name",
            "N\u00E3o \u00E9 Poss\u00EDvel Especificar um Principal sem um Nome"},
        {"Permission.and.Target.Name.must.have.a.value",
                "O Nome de Destino e a Permiss\u00E3o devem ter um Valor"},
        {"Remove.this.Policy.Entry.", "Remover esta Entrada de Pol\u00EDtica?"},
        {"Overwrite.File", "Substituir Arquivo"},
        {"Policy.successfully.written.to.filename",
                "Pol\u00EDtica gravada com \u00EAxito em {0}"},
        {"null.filename", "nome de arquivo nulo"},
        {"Save.changes.", "Salvar altera\u00E7\u00F5es?"},
        {"Yes", "Sim"},
        {"No", "N\u00E3o"},
        {"Policy.Entry", "Entrada de Pol\u00EDtica"},
        {"Save.Changes", "Salvar Altera\u00E7\u00F5es"},
        {"No.Policy.Entry.selected", "Nenhuma Entrada de Pol\u00EDtica Selecionada"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "N\u00E3o \u00E9 poss\u00EDvel abrir a KeyStore: {0}"},
        {"No.principal.selected", "Nenhum principal selecionado"},
        {"No.permission.selected", "Nenhuma permiss\u00E3o selecionada"},
        {"name", "nome"},
        {"configuration.type", "tipo de configura\u00E7\u00E3o"},
        {"environment.variable.name", "nome da vari\u00E1vel de ambiente"},
        {"library.name", "nome da biblioteca"},
        {"package.name", "nome do pacote"},
        {"policy.type", "tipo de pol\u00EDtica"},
        {"property.name", "nome da propriedade"},
        {"Principal.List", "Lista de Principais"},
        {"Permission.List", "Lista de Permiss\u00F5es"},
        {"Code.Base", "Base de C\u00F3digo"},
        {"KeyStore.U.R.L.", "U R L da KeyStore:"},
        {"KeyStore.Password.U.R.L.", "U R L da Senha do KeyStore:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "entrada(s) nula(s) inv\u00E1lida(s)"},
        {"actions.can.only.be.read.", "as a\u00E7\u00F5es s\u00F3 podem ser 'lidas'"},
        {"permission.name.name.syntax.invalid.",
                "sintaxe inv\u00E1lida do nome da permiss\u00E3o [{0}]: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "Classe da Credencial n\u00E3o seguida por um Nome e uma Classe do Principal"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "Classe do Principal n\u00E3o seguida por um Nome do Principal"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "O Nome do Principal deve estar entre aspas"},
        {"Principal.Name.missing.end.quote",
                "Faltam as aspas finais no Nome do Principal"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "A Classe do Principal PrivateCredentialPermission n\u00E3o pode ser um valor curinga (*) se o Nome do Principal n\u00E3o for um valor curinga (*)"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\tClasse do Principal = {0}\n\tNome do Principal = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "nome nulo fornecido"},
        {"provided.null.keyword.map", "mapa de palavra-chave nulo fornecido"},
        {"provided.null.OID.map", "mapa OID nulo fornecido"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "AccessControlContext nulo inv\u00E1lido fornecido"},
        {"invalid.null.action.provided", "a\u00E7\u00E3o nula inv\u00E1lida fornecida"},
        {"invalid.null.Class.provided", "Classe nula inv\u00E1lida fornecida"},
        {"Subject.", "Assunto:\n"},
        {".Principal.", "\tPrincipal: "},
        {".Public.Credential.", "\tCredencial P\u00FAblica: "},
        {".Private.Credentials.inaccessible.",
                "\tCredenciais Privadas inacess\u00EDveis\n"},
        {".Private.Credential.", "\tCredencial Privada: "},
        {".Private.Credential.inaccessible.",
                "\tCredencial Privada inacess\u00EDvel\n"},
        {"Subject.is.read.only", "O Assunto \u00E9 somente para leitura"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "tentativa de adicionar um objeto que n\u00E3o \u00E9 uma inst\u00E2ncia de java.security.Principal a um conjunto de principais do Subject"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "tentativa de adicionar um objeto que n\u00E3o \u00E9 uma inst\u00E2ncia de {0}"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "Entrada nula inv\u00E1lida: nome"},
        {"No.LoginModules.configured.for.name",
         "Nenhum LoginModule configurado para {0}"},
        {"invalid.null.Subject.provided", "Subject nulo inv\u00E1lido fornecido"},
        {"invalid.null.CallbackHandler.provided",
                "CallbackHandler nulo inv\u00E1lido fornecido"},
        {"null.subject.logout.called.before.login",
                "Subject nulo - log-out chamado antes do log-in"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "n\u00E3o \u00E9 poss\u00EDvel instanciar LoginModule, {0}, porque ele n\u00E3o fornece um construtor sem argumento"},
        {"unable.to.instantiate.LoginModule",
                "n\u00E3o \u00E9 poss\u00EDvel instanciar LoginModule"},
        {"unable.to.instantiate.LoginModule.",
                "n\u00E3o \u00E9 poss\u00EDvel instanciar LoginModule: "},
        {"unable.to.find.LoginModule.class.",
                "n\u00E3o \u00E9 poss\u00EDvel localizar a classe LoginModule: "},
        {"unable.to.access.LoginModule.",
                "n\u00E3o \u00E9 poss\u00EDvel acessar LoginModule: "},
        {"Login.Failure.all.modules.ignored",
                "Falha de Log-in: todos os m\u00F3dulos ignorados"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: erro durante o parse de {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: erro ao adicionar a permiss\u00E3o, {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: erro ao adicionar a Entrada:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "nome de alias n\u00E3o fornecido ({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "n\u00E3o \u00E9 poss\u00EDvel realizar a substitui\u00E7\u00E3o no alias, {0}"},
        {"substitution.value.prefix.unsupported",
                "valor da substitui\u00E7\u00E3o, {0}, n\u00E3o suportado"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","o tipo n\u00E3o pode ser nulo"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "keystorePasswordURL n\u00E3o pode ser especificado sem que a \u00E1rea de armazenamento de chaves tamb\u00E9m seja especificada"},
        {"expected.keystore.type", "tipo de armazenamento de chaves esperado"},
        {"expected.keystore.provider", "fornecedor da \u00E1rea de armazenamento de chaves esperado"},
        {"multiple.Codebase.expressions",
                "v\u00E1rias express\u00F5es CodeBase"},
        {"multiple.SignedBy.expressions","v\u00E1rias express\u00F5es SignedBy"},
        {"SignedBy.has.empty.alias","SignedBy tem alias vazio"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "n\u00E3o \u00E9 poss\u00EDvel especificar um principal com uma classe curinga sem um nome curinga"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "CodeBase ou SignedBy ou Principal esperado"},
        {"expected.permission.entry", "entrada de permiss\u00E3o esperada"},
        {"number.", "n\u00FAmero "},
        {"expected.expect.read.end.of.file.",
                "esperado [{0}], lido [fim do arquivo]"},
        {"expected.read.end.of.file.",
                "esperado [;], lido [fim do arquivo]"},
        {"line.number.msg", "linha {0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "linha {0}: esperada [{1}], encontrada [{2}]"},
        {"null.principalClass.or.principalName",
                "principalClass ou principalName nulo"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "Senha PKCS11 de Token [{0}]: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "n\u00E3o \u00E9 poss\u00EDvel instanciar a pol\u00EDtica com base em Subject"}
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

