/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
public class Resources_fr extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Avertissement\u00A0: il n''existe pas de cl\u00E9 publique pour l''alias {0}. V\u00E9rifiez que le fichier de cl\u00E9s d''acc\u00E8s est correctement configur\u00E9."},
        {"Warning.Class.not.found.class", "Avertissement : classe introuvable - {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Avertissement\u00A0: arguments non valides pour le constructeur\u00A0- {0}"},
        {"Illegal.Principal.Type.type", "Type de principal non admis : {0}"},
        {"Illegal.option.option", "Option non admise : {0}"},
        {"Usage.policytool.options.", "Syntaxe : policytool [options]"},
        {".file.file.policy.file.location",
                "  [-file <file>]    emplacement du fichier de r\u00E8gles"},
        {"New", "Nouveau"},
        {"Open", "Ouvrir"},
        {"Save", "Enregistrer"},
        {"Save.As", "Enregistrer sous"},
        {"View.Warning.Log", "Afficher le journal des avertissements"},
        {"Exit", "Quitter"},
        {"Add.Policy.Entry", "Ajouter une r\u00E8gle"},
        {"Edit.Policy.Entry", "Modifier une r\u00E8gle"},
        {"Remove.Policy.Entry", "Enlever une r\u00E8gle"},
        {"Edit", "Modifier"},
        {"Retain", "Conserver"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Avertissement : il se peut que le nom de fichier contienne des barres obliques inverses avec caract\u00E8re d'\u00E9chappement. Il n'est pas n\u00E9cessaire d'ajouter un caract\u00E8re d'\u00E9chappement aux barres obliques inverses. L'outil proc\u00E8de \u00E0 l'\u00E9chappement si n\u00E9cessaire lorsqu'il \u00E9crit le contenu des r\u00E8gles dans la zone de stockage persistant).\n\nCliquez sur Conserver pour garder le nom saisi ou sur Modifier pour le remplacer."},

        {"Add.Public.Key.Alias", "Ajouter un alias de cl\u00E9 publique"},
        {"Remove.Public.Key.Alias", "Enlever un alias de cl\u00E9 publique"},
        {"File", "Fichier"},
        {"KeyStore", "Fichier de cl\u00E9s"},
        {"Policy.File.", "Fichier de r\u00E8gles :"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "Impossible d''ouvrir le fichier de r\u00E8gles\u00A0: {0}: {1}"},
        {"Policy.Tool", "Policy Tool"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Des erreurs se sont produites \u00E0 l'ouverture de la configuration de r\u00E8gles. Pour plus d'informations, consultez le journal des avertissements."},
        {"Error", "Erreur"},
        {"OK", "OK"},
        {"Status", "Statut"},
        {"Warning", "Avertissement"},
        {"Permission.",
                "Droit :                                                       "},
        {"Principal.Type.", "Type de principal :"},
        {"Principal.Name.", "Nom de principal :"},
        {"Target.Name.",
                "Nom de cible :                                                    "},
        {"Actions.",
                "Actions :                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "Remplacer le fichier existant {0} ?"},
        {"Cancel", "Annuler"},
        {"CodeBase.", "Base de code :"},
        {"SignedBy.", "Sign\u00E9 par :"},
        {"Add.Principal", "Ajouter un principal"},
        {"Edit.Principal", "Modifier un principal"},
        {"Remove.Principal", "Enlever un principal"},
        {"Principals.", "Principaux :"},
        {".Add.Permission", "  Ajouter un droit"},
        {".Edit.Permission", "  Modifier un droit"},
        {"Remove.Permission", "Enlever un droit"},
        {"Done", "Termin\u00E9"},
        {"KeyStore.URL.", "URL du fichier de cl\u00E9s :"},
        {"KeyStore.Type.", "Type du fichier de cl\u00E9s :"},
        {"KeyStore.Provider.", "Fournisseur du fichier de cl\u00E9s :"},
        {"KeyStore.Password.URL.", "URL du mot de passe du fichier de cl\u00E9s :"},
        {"Principals", "Principaux"},
        {".Edit.Principal.", "  Modifier un principal :"},
        {".Add.New.Principal.", "  Ajouter un principal :"},
        {"Permissions", "Droits"},
        {".Edit.Permission.", "  Modifier un droit :"},
        {".Add.New.Permission.", "  Ajouter un droit :"},
        {"Signed.By.", "Sign\u00E9 par :"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "Impossible de sp\u00E9cifier un principal avec une classe g\u00E9n\u00E9rique sans nom g\u00E9n\u00E9rique"},
        {"Cannot.Specify.Principal.without.a.Name",
            "Impossible de sp\u00E9cifier un principal sans nom"},
        {"Permission.and.Target.Name.must.have.a.value",
                "Le droit et le nom de cible doivent avoir une valeur"},
        {"Remove.this.Policy.Entry.", "Enlever cette r\u00E8gle ?"},
        {"Overwrite.File", "Remplacer le fichier"},
        {"Policy.successfully.written.to.filename",
                "R\u00E8gle \u00E9crite dans {0}"},
        {"null.filename", "nom de fichier NULL"},
        {"Save.changes.", "Enregistrer les modifications ?"},
        {"Yes", "Oui"},
        {"No", "Non"},
        {"Policy.Entry", "R\u00E8gle"},
        {"Save.Changes", "Enregistrer les modifications"},
        {"No.Policy.Entry.selected", "Aucune r\u00E8gle s\u00E9lectionn\u00E9e"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "Impossible d''ouvrir le fichier de cl\u00E9s d''acc\u00E8s : {0}"},
        {"No.principal.selected", "Aucun principal s\u00E9lectionn\u00E9"},
        {"No.permission.selected", "Aucun droit s\u00E9lectionn\u00E9"},
        {"name", "nom"},
        {"configuration.type", "type de configuration"},
        {"environment.variable.name", "Nom de variable d'environnement"},
        {"library.name", "nom de biblioth\u00E8que"},
        {"package.name", "nom de package"},
        {"policy.type", "type de r\u00E8gle"},
        {"property.name", "nom de propri\u00E9t\u00E9"},
        {"provider.name", "nom du fournisseur"},
        {"url", "url"},
        {"method.list", "liste des m\u00E9thodes"},
        {"request.headers.list", "liste des en-t\u00EAtes de demande"},
        {"Principal.List", "Liste de principaux"},
        {"Permission.List", "Liste de droits"},
        {"Code.Base", "Base de code"},
        {"KeyStore.U.R.L.", "URL du fichier de cl\u00E9s :"},
        {"KeyStore.Password.U.R.L.", "URL du mot de passe du fichier de cl\u00E9s :"}
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
