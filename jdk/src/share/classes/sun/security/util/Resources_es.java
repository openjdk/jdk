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
public class Resources_es extends java.util.ListResourceBundle {

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
        {"Options.", "Opciones:"},
        {"Use.keytool.help.for.all.available.commands",
                 "Utilice\"keytool -help\" para todos los comandos disponibles"},
        {"Key.and.Certificate.Management.Tool",
                 "Herramienta de Gesti\u00F3n de Certificados y Claves"},
        {"Commands.", "Comandos:"},
        {"Use.keytool.command.name.help.for.usage.of.command.name",
                "Utilice \"keytool -command_name -help\" para la sintaxis de nombre_comando"},
        // keytool: help: commands
        {"Generates.a.certificate.request",
                "Genera una solicitud de certificado"}, //-certreq
        {"Changes.an.entry.s.alias",
                "Cambia un alias de entrada"}, //-changealias
        {"Deletes.an.entry",
                "Suprime una entrada"}, //-delete
        {"Exports.certificate",
                "Exporta el certificado"}, //-exportcert
        {"Generates.a.key.pair",
                "Genera un par de claves"}, //-genkeypair
        {"Generates.a.secret.key",
                "Genera un clave secreta"}, //-genseckey
        {"Generates.certificate.from.a.certificate.request",
                "Genera un certificado a partir de una solicitud de certificado"}, //-gencert
        {"Generates.CRL", "Genera CRL"}, //-gencrl
        {"Imports.entries.from.a.JDK.1.1.x.style.identity.database",
                "Importa entradas desde una base de datos de identidades JDK 1.1.x-style"}, //-identitydb
        {"Imports.a.certificate.or.a.certificate.chain",
                "Importa un certificado o una cadena de certificados"}, //-importcert
        {"Imports.one.or.all.entries.from.another.keystore",
                "Importa una o todas las entradas desde otro almac\u00E9n de claves"}, //-importkeystore
        {"Clones.a.key.entry",
                "Clona una entrada de clave"}, //-keyclone
        {"Changes.the.key.password.of.an.entry",
                "Cambia la contrase\u00F1a de clave de una entrada"}, //-keypasswd
        {"Lists.entries.in.a.keystore",
                "Enumera las entradas de un almac\u00E9n de claves"}, //-list
        {"Prints.the.content.of.a.certificate",
                "Imprime el contenido de un certificado"}, //-printcert
        {"Prints.the.content.of.a.certificate.request",
                "Imprime el contenido de una solicitud de certificado"}, //-printcertreq
        {"Prints.the.content.of.a.CRL.file",
                "Imprime el contenido de un archivo CRL"}, //-printcrl
        {"Generates.a.self.signed.certificate",
                "Genera un certificado autofirmado"}, //-selfcert
        {"Changes.the.store.password.of.a.keystore",
                "Cambia la contrase\u00F1a de almac\u00E9n de un almac\u00E9n de claves"}, //-storepasswd
        // keytool: help: options
        {"alias.name.of.the.entry.to.process",
                "nombre de alias de la entrada que se va a procesar"}, //-alias
        {"destination.alias",
                "alias de destino"}, //-destalias
        {"destination.key.password",
                "contrase\u00F1a de clave de destino"}, //-destkeypass
        {"destination.keystore.name",
                "nombre de almac\u00E9n de claves de destino"}, //-destkeystore
        {"destination.keystore.password.protected",
                "almac\u00E9n de claves de destino protegido por contrase\u00F1a"}, //-destprotected
        {"destination.keystore.provider.name",
                "nombre de proveedor de almac\u00E9n de claves de destino"}, //-destprovidername
        {"destination.keystore.password",
                "contrase\u00F1a de almac\u00E9n de claves de destino"}, //-deststorepass
        {"destination.keystore.type",
                "tipo de almac\u00E9n de claves de destino"}, //-deststoretype
        {"distinguished.name",
                "nombre distinguido"}, //-dname
        {"X.509.extension",
                "extensi\u00F3n X.509"}, //-ext
        {"output.file.name",
                "nombre de archivo de salida"}, //-file and -outfile
        {"input.file.name",
                "nombre de archivo de entrada"}, //-file and -infile
        {"key.algorithm.name",
                "nombre de algoritmo de clave"}, //-keyalg
        {"key.password",
                "contrase\u00F1a de clave"}, //-keypass
        {"key.bit.size",
                "tama\u00F1o de bit de clave"}, //-keysize
        {"keystore.name",
                "nombre de almac\u00E9n de claves"}, //-keystore
        {"new.password",
                "nueva contrase\u00F1a"}, //-new
        {"do.not.prompt",
                "no solicitar"}, //-noprompt
        {"password.through.protected.mechanism",
                "contrase\u00F1a a trav\u00E9s de mecanismo protegido"}, //-protected
        {"provider.argument",
                "argumento del proveedor"}, //-providerarg
        {"provider.class.name",
                "nombre de clase del proveedor"}, //-providerclass
        {"provider.name",
                "nombre del proveedor"}, //-providername
        {"provider.classpath",
                "classpath de proveedor"}, //-providerpath
        {"output.in.RFC.style",
                "salida en estilo RFC"}, //-rfc
        {"signature.algorithm.name",
                "nombre de algoritmo de firma"}, //-sigalg
        {"source.alias",
                "alias de origen"}, //-srcalias
        {"source.key.password",
                "contrase\u00F1a de clave de origen"}, //-srckeypass
        {"source.keystore.name",
                "nombre de almac\u00E9n de claves de origen"}, //-srckeystore
        {"source.keystore.password.protected",
                "almac\u00E9n de claves de origen protegido por contrase\u00F1a"}, //-srcprotected
        {"source.keystore.provider.name",
                "nombre de proveedor de almac\u00E9n de claves de origen"}, //-srcprovidername
        {"source.keystore.password",
                "contrase\u00F1a de almac\u00E9n de claves de origen"}, //-srcstorepass
        {"source.keystore.type",
                "tipo de almac\u00E9n de claves de origen"}, //-srcstoretype
        {"SSL.server.host.and.port",
                "puerto y host del servidor SSL"}, //-sslserver
        {"signed.jar.file",
                "archivo jar firmado"}, //=jarfile
        {"certificate.validity.start.date.time",
                "fecha/hora de inicio de validez del certificado"}, //-startdate
        {"keystore.password",
                "contrase\u00F1a de almac\u00E9n de claves"}, //-storepass
        {"keystore.type",
                "tipo de almac\u00E9n de claves"}, //-storetype
        {"trust.certificates.from.cacerts",
                "certificados de protecci\u00F3n de cacerts"}, //-trustcacerts
        {"verbose.output",
                "salida detallada"}, //-v
        {"validity.number.of.days",
                "n\u00FAmero de validez de d\u00EDas"}, //-validity
        {"Serial.ID.of.cert.to.revoke",
                 "identificador de serie del certificado que se va a revocar"}, //-id
        // keytool: Running part
        {"keytool.error.", "error de herramienta de claves: "},
        {"Illegal.option.", "Opci\u00F3n no permitida:  "},
        {"Illegal.value.", "Valor no permitido: "},
        {"Unknown.password.type.", "Tipo de contrase\u00F1a desconocido: "},
        {"Cannot.find.environment.variable.",
                "No se ha encontrado la variable del entorno: "},
        {"Cannot.find.file.", "No se ha encontrado el archivo: "},
        {"Command.option.flag.needs.an.argument.", "La opci\u00F3n de comando {0} necesita un argumento."},
        {"Warning.Different.store.and.key.passwords.not.supported.for.PKCS12.KeyStores.Ignoring.user.specified.command.value.",
                "Advertencia: los almacenes de claves en formato PKCS12 no admiten contrase\u00F1as de clave y almacenamiento distintas. Se ignorar\u00E1 el valor especificado por el usuario, {0}."},
        {".keystore.must.be.NONE.if.storetype.is.{0}",
                "-keystore debe ser NONE si -storetype es {0}"},
        {"Too.many.retries.program.terminated",
                 "Ha habido demasiados intentos, se ha cerrado el programa"},
        {".storepasswd.and.keypasswd.commands.not.supported.if.storetype.is.{0}",
                "Los comandos -storepasswd y -keypasswd no est\u00E1n soportados si -storetype es {0}"},
        {".keypasswd.commands.not.supported.if.storetype.is.PKCS12",
                "Los comandos -keypasswd no est\u00E1n soportados si -storetype es PKCS12"},
        {".keypass.and.new.can.not.be.specified.if.storetype.is.{0}",
                "-keypass y -new no se pueden especificar si -storetype es {0}"},
        {"if.protected.is.specified.then.storepass.keypass.and.new.must.not.be.specified",
                "si se especifica -protected, no deben especificarse -storepass, -keypass ni -new"},
        {"if.srcprotected.is.specified.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "Si se especifica -srcprotected, no se puede especificar -srcstorepass ni -srckeypass"},
        {"if.keystore.is.not.password.protected.then.storepass.keypass.and.new.must.not.be.specified",
                "Si keystore no est\u00E1 protegido por contrase\u00F1a, no se deben especificar -storepass, -keypass ni -new"},
        {"if.source.keystore.is.not.password.protected.then.srcstorepass.and.srckeypass.must.not.be.specified",
                "Si el almac\u00E9n de claves de origen no est\u00E1 protegido por contrase\u00F1a, no se deben especificar -srcstorepass ni -srckeypass"},
        {"Illegal.startdate.value", "Valor de fecha de inicio no permitido"},
        {"Validity.must.be.greater.than.zero",
                "La validez debe ser mayor que cero"},
        {"provName.not.a.provider", "{0} no es un proveedor"},
        {"Usage.error.no.command.provided", "Error de sintaxis: no se ha proporcionado ning\u00FAn comando"},
        {"Source.keystore.file.exists.but.is.empty.", "El archivo de almac\u00E9n de claves de origen existe, pero est\u00E1 vac\u00EDo: "},
        {"Please.specify.srckeystore", "Especifique -srckeystore"},
        {"Must.not.specify.both.v.and.rfc.with.list.command",
                "No se deben especificar -v y -rfc simult\u00E1neamente con el comando 'list'"},
        {"Key.password.must.be.at.least.6.characters",
                "La contrase\u00F1a de clave debe tener al menos 6 caracteres"},
        {"New.password.must.be.at.least.6.characters",
                "La nueva contrase\u00F1a debe tener al menos 6 caracteres"},
        {"Keystore.file.exists.but.is.empty.",
                "El archivo de almac\u00E9n de claves existe, pero est\u00E1 vac\u00EDo: "},
        {"Keystore.file.does.not.exist.",
                "El archivo de almac\u00E9n de claves no existe: "},
        {"Must.specify.destination.alias", "Se debe especificar un alias de destino"},
        {"Must.specify.alias", "Se debe especificar un alias"},
        {"Keystore.password.must.be.at.least.6.characters",
                "La contrase\u00F1a del almac\u00E9n de claves debe tener al menos 6 caracteres"},
        {"Enter.keystore.password.", "Introduzca la contrase\u00F1a del almac\u00E9n de claves:  "},
        {"Enter.source.keystore.password.", "Introduzca la contrase\u00F1a de almac\u00E9n de claves de origen:  "},
        {"Enter.destination.keystore.password.", "Introduzca la contrase\u00F1a de almac\u00E9n de claves de destino:  "},
        {"Keystore.password.is.too.short.must.be.at.least.6.characters",
         "La contrase\u00F1a del almac\u00E9n de claves es demasiado corta, debe tener al menos 6 caracteres"},
        {"Unknown.Entry.Type", "Tipo de Entrada Desconocido"},
        {"Too.many.failures.Alias.not.changed", "Demasiados fallos. No se ha cambiado el alias"},
        {"Entry.for.alias.alias.successfully.imported.",
                 "La entrada del alias {0} se ha importado correctamente."},
        {"Entry.for.alias.alias.not.imported.", "La entrada del alias {0} no se ha importado."},
        {"Problem.importing.entry.for.alias.alias.exception.Entry.for.alias.alias.not.imported.",
                 "Problema al importar la entrada del alias {0}: {1}.\nNo se ha importado la entrada del alias {0}."},
        {"Import.command.completed.ok.entries.successfully.imported.fail.entries.failed.or.cancelled",
                 "Comando de importaci\u00F3n completado: {0} entradas importadas correctamente, {1} entradas incorrectas o canceladas"},
        {"Warning.Overwriting.existing.alias.alias.in.destination.keystore",
                 "Advertencia: se sobrescribir\u00E1 el alias {0} en el almac\u00E9n de claves de destino"},
        {"Existing.entry.alias.alias.exists.overwrite.no.",
                 "El alias de entrada existente {0} ya existe, \u00BFdesea sobrescribirlo? [no]:  "},
        {"Too.many.failures.try.later", "Demasiados fallos; int\u00E9ntelo m\u00E1s adelante"},
        {"Certification.request.stored.in.file.filename.",
                "Solicitud de certificaci\u00F3n almacenada en el archivo <{0}>"},
        {"Submit.this.to.your.CA", "Enviar a la CA"},
        {"if.alias.not.specified.destalias.srckeypass.and.destkeypass.must.not.be.specified",
            "si no se especifica el alias, no se puede especificar destalias, srckeypass ni destkeypass"},
        {"Certificate.stored.in.file.filename.",
                "Certificado almacenado en el archivo <{0}>"},
        {"Certificate.reply.was.installed.in.keystore",
                "Se ha instalado la respuesta del certificado en el almac\u00E9n de claves"},
        {"Certificate.reply.was.not.installed.in.keystore",
                "No se ha instalado la respuesta del certificado en el almac\u00E9n de claves"},
        {"Certificate.was.added.to.keystore",
                "Se ha agregado el certificado al almac\u00E9n de claves"},
        {"Certificate.was.not.added.to.keystore",
                "No se ha agregado el certificado al almac\u00E9n de claves"},
        {".Storing.ksfname.", "[Almacenando {0}]"},
        {"alias.has.no.public.key.certificate.",
                "{0} no tiene clave p\u00FAblica (certificado)"},
        {"Cannot.derive.signature.algorithm",
                "No se puede derivar el algoritmo de firma"},
        {"Alias.alias.does.not.exist",
                "El alias <{0}> no existe"},
        {"Alias.alias.has.no.certificate",
                "El alias <{0}> no tiene certificado"},
        {"Key.pair.not.generated.alias.alias.already.exists",
                "No se ha generado el par de claves, el alias <{0}> ya existe"},
        {"Generating.keysize.bit.keyAlgName.key.pair.and.self.signed.certificate.sigAlgName.with.a.validity.of.validality.days.for",
                "Generando par de claves {1} de {0} bits para certificado autofirmado ({2}) con una validez de {3} d\u00EDas\n\tpara: {4}"},
        {"Enter.key.password.for.alias.", "Introduzca la contrase\u00F1a de clave para <{0}>"},
        {".RETURN.if.same.as.keystore.password.",
                "\t(INTRO si es la misma contrase\u00F1a que la del almac\u00E9n de claves):  "},
        {"Key.password.is.too.short.must.be.at.least.6.characters",
                "La contrase\u00F1a de clave es demasiado corta; debe tener al menos 6 caracteres"},
        {"Too.many.failures.key.not.added.to.keystore",
                "Demasiados fallos; no se ha agregado la clave al almac\u00E9n de claves"},
        {"Destination.alias.dest.already.exists",
                "El alias de destino <{0}> ya existe"},
        {"Password.is.too.short.must.be.at.least.6.characters",
                "La contrase\u00F1a es demasiado corta; debe tener al menos 6 caracteres"},
        {"Too.many.failures.Key.entry.not.cloned",
                "Demasiados fallos. No se ha clonado la entrada de clave"},
        {"key.password.for.alias.", "contrase\u00F1a de clave para <{0}>"},
        {"Keystore.entry.for.id.getName.already.exists",
                "La entrada de almac\u00E9n de claves para <{0}> ya existe"},
        {"Creating.keystore.entry.for.id.getName.",
                "Creando entrada de almac\u00E9n de claves para <{0}> ..."},
        {"No.entries.from.identity.database.added",
                "No se han agregado entradas de la base de datos de identidades"},
        {"Alias.name.alias", "Nombre de Alias: {0}"},
        {"Creation.date.keyStore.getCreationDate.alias.",
                "Fecha de Creaci\u00F3n: {0,date}"},
        {"alias.keyStore.getCreationDate.alias.",
                "{0}, {1,date}, "},
        {"alias.", "{0}, "},
        {"Entry.type.type.", "Tipo de Entrada: {0}"},
        {"Certificate.chain.length.", "Longitud de la Cadena de Certificado: "},
        {"Certificate.i.1.", "Certificado[{0,number,integer}]:"},
        {"Certificate.fingerprint.SHA1.", "Huella Digital de Certificado (SHA1): "},
        {"Entry.type.trustedCertEntry.", "Tipo de Entrada: trustedCertEntry\n"},
        {"trustedCertEntry.", "trustedCertEntry,"},
        {"Keystore.type.", "Tipo de Almac\u00E9n de Claves: "},
        {"Keystore.provider.", "Proveedor de Almac\u00E9n de Claves: "},
        {"Your.keystore.contains.keyStore.size.entry",
                "Su almac\u00E9n de claves contiene {0,number,integer} entrada"},
        {"Your.keystore.contains.keyStore.size.entries",
                "Su almac\u00E9n de claves contiene {0,number,integer} entradas"},
        {"Failed.to.parse.input", "Fallo al analizar la entrada"},
        {"Empty.input", "Entrada vac\u00EDa"},
        {"Not.X.509.certificate", "No es un certificado X.509"},
        {"alias.has.no.public.key", "{0} no tiene clave p\u00FAblica"},
        {"alias.has.no.X.509.certificate", "{0} no tiene certificado X.509"},
        {"New.certificate.self.signed.", "Nuevo Certificado (Autofirmado):"},
        {"Reply.has.no.certificates", "La respuesta no tiene certificados"},
        {"Certificate.not.imported.alias.alias.already.exists",
                "Certificado no importado, el alias <{0}> ya existe"},
        {"Input.not.an.X.509.certificate", "La entrada no es un certificado X.509"},
        {"Certificate.already.exists.in.keystore.under.alias.trustalias.",
                "El certificado ya existe en el almac\u00E9n de claves con el alias <{0}>"},
        {"Do.you.still.want.to.add.it.no.",
                "\u00BFA\u00FAn desea agregarlo? [no]:  "},
        {"Certificate.already.exists.in.system.wide.CA.keystore.under.alias.trustalias.",
                "El certificado ya existe en el almac\u00E9n de claves de la CA del sistema, con el alias <{0}>"},
        {"Do.you.still.want.to.add.it.to.your.own.keystore.no.",
                "\u00BFA\u00FAn desea agregarlo a su propio almac\u00E9n de claves? [no]:  "},
        {"Trust.this.certificate.no.", "\u00BFConfiar en este certificado? [no]:  "},
        {"YES", "S\u00CD"},
        {"New.prompt.", "Nuevo {0}: "},
        {"Passwords.must.differ", "Las contrase\u00F1as deben ser distintas"},
        {"Re.enter.new.prompt.", "Vuelva a escribir el nuevo {0}: "},
        {"Re.enter.new.password.", "Volver a escribir la contrase\u00F1a nueva: "},
        {"They.don.t.match.Try.again", "No coinciden. Int\u00E9ntelo de nuevo"},
        {"Enter.prompt.alias.name.", "Escriba el nombre de alias de {0}:  "},
        {"Enter.new.alias.name.RETURN.to.cancel.import.for.this.entry.",
                 "Indique el nuevo nombre de alias\t(INTRO para cancelar la importaci\u00F3n de esta entrada):  "},
        {"Enter.alias.name.", "Introduzca el nombre de alias:  "},
        {".RETURN.if.same.as.for.otherAlias.",
                "\t(INTRO si es el mismo que para <{0}>)"},
        {".PATTERN.printX509Cert",
                "Propietario: {0}\nEmisor: {1}\nN\u00FAmero de serie: {2}\nV\u00E1lido desde: {3} hasta: {4}\nHuellas digitales del Certificado:\n\t MD5: {5}\n\t SHA1: {6}\n\t SHA256: {7}\n\t Nombre del Algoritmo de Firma: {8}\n\t Versi\u00F3n: {9}"},
        {"What.is.your.first.and.last.name.",
                "\u00BFCu\u00E1les son su nombre y su apellido?"},
        {"What.is.the.name.of.your.organizational.unit.",
                "\u00BFCu\u00E1l es el nombre de su unidad de organizaci\u00F3n?"},
        {"What.is.the.name.of.your.organization.",
                "\u00BFCu\u00E1l es el nombre de su organizaci\u00F3n?"},
        {"What.is.the.name.of.your.City.or.Locality.",
                "\u00BFCu\u00E1l es el nombre de su ciudad o localidad?"},
        {"What.is.the.name.of.your.State.or.Province.",
                "\u00BFCu\u00E1l es el nombre de su estado o provincia?"},
        {"What.is.the.two.letter.country.code.for.this.unit.",
                "\u00BFCu\u00E1l es el c\u00F3digo de pa\u00EDs de dos letras de la unidad?"},
        {"Is.name.correct.", "\u00BFEs correcto {0}?"},
        {"no", "no"},
        {"yes", "s\u00ED"},
        {"y", "s"},
        {".defaultValue.", "  [{0}]:  "},
        {"Alias.alias.has.no.key",
                "El alias <{0}> no tiene clave"},
        {"Alias.alias.references.an.entry.type.that.is.not.a.private.key.entry.The.keyclone.command.only.supports.cloning.of.private.key",
                 "El alias <{0}> hace referencia a un tipo de entrada que no es una clave privada. El comando -keyclone s\u00F3lo permite la clonaci\u00F3n de entradas de claves privadas"},

        {".WARNING.WARNING.WARNING.",
            "*****************  WARNING WARNING WARNING  *****************"},
        {"Signer.d.", "#%d de Firmante:"},
        {"Timestamp.", "Registro de Hora:"},
        {"Signature.", "Firma:"},
        {"CRLs.", "CRL:"},
        {"Certificate.owner.", "Propietario del Certificado: "},
        {"Not.a.signed.jar.file", "No es un archivo jar firmado"},
        {"No.certificate.from.the.SSL.server",
                "Ning\u00FAn certificado del servidor SSL"},

        // Translators of the following 5 pairs, ATTENTION:
        // the next 5 string pairs are meant to be combined into 2 paragraphs,
        // 1+3+4 and 2+3+5. make sure your translation also does.
        {".The.integrity.of.the.information.stored.in.your.keystore.",
            "* La integridad de la informaci\u00F3n almacenada en su almac\u00E9n de claves  *"},
        {".The.integrity.of.the.information.stored.in.the.srckeystore.",
            "* La totalidad de la informaci\u00F3n almacenada en srckeystore*"},
        {".has.NOT.been.verified.In.order.to.verify.its.integrity.",
            "* NO se ha comprobado. Para comprobar dicha integridad, *"},
        {".you.must.provide.your.keystore.password.",
            "* deber\u00E1 proporcionar su contrase\u00F1a de almac\u00E9n de claves.                  *"},
        {".you.must.provide.the.srckeystore.password.",
            "* deber\u00E1 indicar la contrase\u00F1a de srckeystore.                *"},


        {"Certificate.reply.does.not.contain.public.key.for.alias.",
                "La respuesta de certificado no contiene una clave p\u00FAblica para <{0}>"},
        {"Incomplete.certificate.chain.in.reply",
                "Cadena de certificado incompleta en la respuesta"},
        {"Certificate.chain.in.reply.does.not.verify.",
                "La cadena de certificado de la respuesta no verifica: "},
        {"Top.level.certificate.in.reply.",
                "Certificado de nivel superior en la respuesta:\n"},
        {".is.not.trusted.", "... no es de confianza. "},
        {"Install.reply.anyway.no.", "\u00BFInstalar respuesta de todos modos? [no]:  "},
        {"NO", "NO"},
        {"Public.keys.in.reply.and.keystore.don.t.match",
                "Las claves p\u00FAblicas en la respuesta y en el almac\u00E9n de claves no coinciden"},
        {"Certificate.reply.and.certificate.in.keystore.are.identical",
                "La respuesta del certificado y el certificado en el almac\u00E9n de claves son id\u00E9nticos"},
        {"Failed.to.establish.chain.from.reply",
                "No se ha podido definir una cadena a partir de la respuesta"},
        {"n", "n"},
        {"Wrong.answer.try.again", "Respuesta incorrecta, vuelva a intentarlo"},
        {"Secret.key.not.generated.alias.alias.already.exists",
                "No se ha generado la clave secreta, el alias <{0}> ya existe"},
        {"Please.provide.keysize.for.secret.key.generation",
                "Proporcione el valor de -keysize para la generaci\u00F3n de claves secretas"},

        {"Extensions.", "Extensiones: "},
        {".Empty.value.", "(Valor vac\u00EDo)"},
        {"Extension.Request.", "Solicitud de Extensi\u00F3n:"},
        {"PKCS.10.Certificate.Request.Version.1.0.Subject.s.Public.Key.s.format.s.key.",
                "Solicitud de Certificado PKCS #10 (Versi\u00F3n 1.0)\nAsunto: %s\nClave P\u00FAblica: %s formato %s clave\n"},
        {"Unknown.keyUsage.type.", "Tipo de uso de clave desconocido: "},
        {"Unknown.extendedkeyUsage.type.", "Tipo de uso de clave extendida desconocido: "},
        {"Unknown.AccessDescription.type.", "Tipo de descripci\u00F3n de acceso desconocido: "},
        {"Unrecognized.GeneralName.type.", "Tipo de nombre general no reconocido: "},
        {"This.extension.cannot.be.marked.as.critical.",
                 "Esta extensi\u00F3n no se puede marcar como cr\u00EDtica. "},
        {"Odd.number.of.hex.digits.found.", "Se ha encontrado un n\u00FAmero impar de d\u00EDgitos hexadecimales: "},
        {"Unknown.extension.type.", "Tipo de extensi\u00F3n desconocida: "},
        {"command.{0}.is.ambiguous.", "El comando {0} es ambiguo:"},

        // policytool
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Advertencia: no hay clave p\u00FAblica para el alias {0}. Aseg\u00FArese de que se ha configurado correctamente un almac\u00E9n de claves."},
        {"Warning.Class.not.found.class", "Advertencia: no se ha encontrado la clase: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Advertencia: argumento(s) no v\u00E1lido(s) para el constructor: {0}"},
        {"Illegal.Principal.Type.type", "Tipo de principal no permitido: {0}"},
        {"Illegal.option.option", "Opci\u00F3n no permitida: {0}"},
        {"Usage.policytool.options.", "Sintaxis: policytool [opciones]"},
        {".file.file.policy.file.location",
                "  [-file <archivo>]    ubicaci\u00F3n del archivo de normas"},
        {"New", "Nuevo"},
        {"Open", "Abrir"},
        {"Save", "Guardar"},
        {"Save.As", "Guardar como"},
        {"View.Warning.Log", "Ver Log de Advertencias"},
        {"Exit", "Salir"},
        {"Add.Policy.Entry", "Agregar Entrada de Pol\u00EDtica"},
        {"Edit.Policy.Entry", "Editar Entrada de Pol\u00EDtica"},
        {"Remove.Policy.Entry", "Eliminar Entrada de Pol\u00EDtica"},
        {"Edit", "Editar"},
        {"Retain", "Mantener"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Advertencia: el nombre del archivo puede contener caracteres de barra invertida de escape. No es necesario utilizar barras invertidas de escape (la herramienta aplica caracteres de escape seg\u00FAn sea necesario al escribir el contenido de las pol\u00EDticas en el almac\u00E9n persistente).\n\nHaga clic en Mantener para conservar el nombre introducido o en Editar para modificarlo."},

        {"Add.Public.Key.Alias", "Agregar Alias de Clave P\u00FAblico"},
        {"Remove.Public.Key.Alias", "Eliminar Alias de Clave P\u00FAblico"},
        {"File", "Archivo"},
        {"KeyStore", "Almac\u00E9n de Claves"},
        {"Policy.File.", "Archivo de Pol\u00EDtica:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "No se ha podido abrir el archivo de pol\u00EDtica: {0}: {1}"},
        {"Policy.Tool", "Herramienta de Pol\u00EDticas"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Ha habido errores al abrir la configuraci\u00F3n de pol\u00EDticas. V\u00E9ase el log de advertencias para obtener m\u00E1s informaci\u00F3n."},
        {"Error", "Error"},
        {"OK", "Aceptar"},
        {"Status", "Estado"},
        {"Warning", "Advertencia"},
        {"Permission.",
                "Permiso:                                                       "},
        {"Principal.Type.", "Tipo de Principal:"},
        {"Principal.Name.", "Nombre de Principal:"},
        {"Target.Name.",
                "Nombre de Destino:                                                    "},
        {"Actions.",
                "Acciones:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "\u00BFSobrescribir el archivo existente {0}?"},
        {"Cancel", "Cancelar"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "Agregar Principal"},
        {"Edit.Principal", "Editar Principal"},
        {"Remove.Principal", "Eliminar Principal"},
        {"Principals.", "Principales:"},
        {".Add.Permission", "  Agregar Permiso"},
        {".Edit.Permission", "  Editar Permiso"},
        {"Remove.Permission", "Eliminar Permiso"},
        {"Done", "Listo"},
        {"KeyStore.URL.", "URL de Almac\u00E9n de Claves:"},
        {"KeyStore.Type.", "Tipo de Almac\u00E9n de Claves:"},
        {"KeyStore.Provider.", "Proveedor de Almac\u00E9n de Claves:"},
        {"KeyStore.Password.URL.", "URL de Contrase\u00F1a de Almac\u00E9n de Claves:"},
        {"Principals", "Principales"},
        {".Edit.Principal.", "  Editar Principal:"},
        {".Add.New.Principal.", "  Agregar Nuevo Principal:"},
        {"Permissions", "Permisos"},
        {".Edit.Permission.", "  Editar Permiso:"},
        {".Add.New.Permission.", "  Agregar Permiso Nuevo:"},
        {"Signed.By.", "Firmado Por:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "No se puede especificar un principal con una clase de comod\u00EDn sin un nombre de comod\u00EDn"},
        {"Cannot.Specify.Principal.without.a.Name",
            "No se puede especificar el principal sin un nombre"},
        {"Permission.and.Target.Name.must.have.a.value",
                "Permiso y Nombre de Destino deben tener un valor"},
        {"Remove.this.Policy.Entry.", "\u00BFEliminar esta entrada de pol\u00EDtica?"},
        {"Overwrite.File", "Sobrescribir Archivo"},
        {"Policy.successfully.written.to.filename",
                "Pol\u00EDtica escrita correctamente en {0}"},
        {"null.filename", "nombre de archivo nulo"},
        {"Save.changes.", "\u00BFGuardar los cambios?"},
        {"Yes", "S\u00ED"},
        {"No", "No"},
        {"Policy.Entry", "Entrada de Pol\u00EDtica"},
        {"Save.Changes", "Guardar Cambios"},
        {"No.Policy.Entry.selected", "No se ha seleccionado la entrada de pol\u00EDtica"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "No se ha podido abrir el almac\u00E9n de claves: {0}"},
        {"No.principal.selected", "No se ha seleccionado un principal"},
        {"No.permission.selected", "No se ha seleccionado un permiso"},
        {"name", "nombre"},
        {"configuration.type", "tipo de configuraci\u00F3n"},
        {"environment.variable.name", "nombre de variable de entorno"},
        {"library.name", "nombre de la biblioteca"},
        {"package.name", "nombre del paquete"},
        {"policy.type", "tipo de pol\u00EDtica"},
        {"property.name", "nombre de la propiedad"},
        {"Principal.List", "Lista de Principales"},
        {"Permission.List", "Lista de Permisos"},
        {"Code.Base", "Base de C\u00F3digo"},
        {"KeyStore.U.R.L.", "URL de Almac\u00E9n de Claves:"},
        {"KeyStore.Password.U.R.L.", "URL de Contrase\u00F1a de Almac\u00E9n de Claves:"},


        // javax.security.auth.PrivateCredentialPermission
        {"invalid.null.input.s.", "entradas nulas no v\u00E1lidas"},
        {"actions.can.only.be.read.", "las acciones s\u00F3lo pueden 'leerse'"},
        {"permission.name.name.syntax.invalid.",
                "sintaxis de nombre de permiso [{0}] no v\u00E1lida: "},
        {"Credential.Class.not.followed.by.a.Principal.Class.and.Name",
                "La clase de credencial no va seguida de una clase y nombre de principal"},
        {"Principal.Class.not.followed.by.a.Principal.Name",
                "La clase de principal no va seguida de un nombre de principal"},
        {"Principal.Name.must.be.surrounded.by.quotes",
                "El nombre de principal debe ir entre comillas"},
        {"Principal.Name.missing.end.quote",
                "Faltan las comillas finales en el nombre de principal"},
        {"PrivateCredentialPermission.Principal.Class.can.not.be.a.wildcard.value.if.Principal.Name.is.not.a.wildcard.value",
                "La clase de principal PrivateCredentialPermission no puede ser un valor comod\u00EDn (*) si el nombre de principal no lo es tambi\u00E9n"},
        {"CredOwner.Principal.Class.class.Principal.Name.name",
                "CredOwner:\n\tClase de Principal = {0}\n\tNombre de Principal = {1}"},

        // javax.security.auth.x500
        {"provided.null.name", "se ha proporcionado un nombre nulo"},
        {"provided.null.keyword.map", "mapa de palabras clave proporcionado nulo"},
        {"provided.null.OID.map", "mapa de OID proporcionado nulo"},

        // javax.security.auth.Subject
        {"invalid.null.AccessControlContext.provided",
                "se ha proporcionado un AccessControlContext nulo no v\u00E1lido"},
        {"invalid.null.action.provided", "se ha proporcionado una acci\u00F3n nula no v\u00E1lida"},
        {"invalid.null.Class.provided", "se ha proporcionado una clase nula no v\u00E1lida"},
        {"Subject.", "Asunto:\n"},
        {".Principal.", "\tPrincipal: "},
        {".Public.Credential.", "\tCredencial P\u00FAblica: "},
        {".Private.Credentials.inaccessible.",
                "\tCredenciales Privadas Inaccesibles\n"},
        {".Private.Credential.", "\tCredencial Privada: "},
        {".Private.Credential.inaccessible.",
                "\tCredencial Privada Inaccesible\n"},
        {"Subject.is.read.only", "El asunto es de s\u00F3lo lectura"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set",
                "intentando agregar un objeto que no es una instancia de java.security.Principal al juego principal de un asunto"},
        {"attempting.to.add.an.object.which.is.not.an.instance.of.class",
                "intentando agregar un objeto que no es una instancia de {0}"},

        // javax.security.auth.login.AppConfigurationEntry
        {"LoginModuleControlFlag.", "LoginModuleControlFlag: "},

        // javax.security.auth.login.LoginContext
        {"Invalid.null.input.name", "Entrada nula no v\u00E1lida: nombre"},
        {"No.LoginModules.configured.for.name",
         "No se han configurado LoginModules para {0}"},
        {"invalid.null.Subject.provided", "se ha proporcionado un asunto nulo no v\u00E1lido"},
        {"invalid.null.CallbackHandler.provided",
                "se ha proporcionado CallbackHandler nulo no v\u00E1lido"},
        {"null.subject.logout.called.before.login",
                "asunto nulo - se ha llamado al cierre de sesi\u00F3n antes del inicio de sesi\u00F3n"},
        {"unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor",
                "no se ha podido instanciar LoginModule, {0}, porque no incluye un constructor sin argumentos"},
        {"unable.to.instantiate.LoginModule",
                "no se ha podido instanciar LoginModule"},
        {"unable.to.instantiate.LoginModule.",
                "no se ha podido instanciar LoginModule: "},
        {"unable.to.find.LoginModule.class.",
                "no se ha encontrado la clase LoginModule: "},
        {"unable.to.access.LoginModule.",
                "no se ha podido acceder a LoginModule: "},
        {"Login.Failure.all.modules.ignored",
                "Fallo en inicio de sesi\u00F3n: se han ignorado todos los m\u00F3dulos"},

        // sun.security.provider.PolicyFile

        {"java.security.policy.error.parsing.policy.message",
                "java.security.policy: error de an\u00E1lisis de {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Permission.perm.message",
                "java.security.policy: error al agregar un permiso, {0}:\n\t{1}"},
        {"java.security.policy.error.adding.Entry.message",
                "java.security.policy: error al agregar una entrada:\n\t{0}"},
        {"alias.name.not.provided.pe.name.", "no se ha proporcionado el nombre de alias ({0})"},
        {"unable.to.perform.substitution.on.alias.suffix",
                "no se puede realizar la sustituci\u00F3n en el alias, {0}"},
        {"substitution.value.prefix.unsupported",
                "valor de sustituci\u00F3n, {0}, no soportado"},
        {"LPARAM", "("},
        {"RPARAM", ")"},
        {"type.can.t.be.null","el tipo no puede ser nulo"},

        // sun.security.provider.PolicyParser
        {"keystorePasswordURL.can.not.be.specified.without.also.specifying.keystore",
                "keystorePasswordURL no puede especificarse sin especificar tambi\u00E9n el almac\u00E9n de claves"},
        {"expected.keystore.type", "se esperaba un tipo de almac\u00E9n de claves"},
        {"expected.keystore.provider", "se esperaba un proveedor de almac\u00E9n de claves"},
        {"multiple.Codebase.expressions",
                "expresiones m\u00FAltiples de CodeBase"},
        {"multiple.SignedBy.expressions","expresiones m\u00FAltiples de SignedBy"},
        {"SignedBy.has.empty.alias","SignedBy tiene un alias vac\u00EDo"},
        {"can.not.specify.Principal.with.a.wildcard.class.without.a.wildcard.name",
                "no se puede especificar Principal con una clase de comod\u00EDn sin un nombre de comod\u00EDn"},
        {"expected.codeBase.or.SignedBy.or.Principal",
                "se esperaba codeBase o SignedBy o Principal"},
        {"expected.permission.entry", "se esperaba una entrada de permiso"},
        {"number.", "n\u00FAmero "},
        {"expected.expect.read.end.of.file.",
                "se esperaba [{0}], se ha le\u00EDdo [final de archivo]"},
        {"expected.read.end.of.file.",
                "se esperaba [;], se ha le\u00EDdo [final de archivo]"},
        {"line.number.msg", "l\u00EDnea {0}: {1}"},
        {"line.number.expected.expect.found.actual.",
                "l\u00EDnea {0}: se esperaba [{1}], se ha encontrado [{2}]"},
        {"null.principalClass.or.principalName",
                "principalClass o principalName nulos"},

        // sun.security.pkcs11.SunPKCS11
        {"PKCS11.Token.providerName.Password.",
                "Contrase\u00F1a del Elemento PKCS11 [{0}]: "},

        /* --- DEPRECATED --- */
        // javax.security.auth.Policy
        {"unable.to.instantiate.Subject.based.policy",
                "no se ha podido instanciar una pol\u00EDtica basada en asunto"}
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

