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

package sun.security.tools.policytool;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the policytool.
 *
 */
public class Resources_es extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
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
        {"provider.name", "nombre del proveedor"},
        {"url", "url"},
        {"method.list", "lista de m\u00E9todos"},
        {"request.headers.list", "lista de cabeceras de solicitudes"},
        {"Principal.List", "Lista de Principales"},
        {"Permission.List", "Lista de Permisos"},
        {"Code.Base", "Base de C\u00F3digo"},
        {"KeyStore.U.R.L.", "URL de Almac\u00E9n de Claves:"},
        {"KeyStore.Password.U.R.L.", "URL de Contrase\u00F1a de Almac\u00E9n de Claves:"}
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
