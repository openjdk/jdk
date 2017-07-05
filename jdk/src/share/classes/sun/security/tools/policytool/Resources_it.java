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
public class Resources_it extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Avvertenza: non esiste una chiave pubblica per l''alias {0}. Verificare che il keystore sia configurato correttamente."},
        {"Warning.Class.not.found.class", "Avvertenza: classe non trovata: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Avvertenza: argomento o argomenti non validi per il costruttore {0}"},
        {"Illegal.Principal.Type.type", "Tipo principal non valido: {0}"},
        {"Illegal.option.option", "Opzione non valida: {0}"},
        {"Usage.policytool.options.", "Uso: policytool [opzioni]"},
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
        {"provider.name", "nome provider"},
        {"url", "url"},
        {"method.list", "lista metodi"},
        {"request.headers.list", "lista intestazioni di richiesta"},
        {"Principal.List", "Lista principal"},
        {"Permission.List", "Lista autorizzazioni"},
        {"Code.Base", "Codebase"},
        {"KeyStore.U.R.L.", "URL keystore:"},
        {"KeyStore.Password.U.R.L.", "URL password keystore:"}
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
