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
public class Resources_de extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Warnung: Kein Public Key f\u00FCr Alias {0} vorhanden. Vergewissern Sie sich, dass der KeyStore ordnungsgem\u00E4\u00DF konfiguriert ist."},
        {"Warning.Class.not.found.class", "Warnung: Klasse nicht gefunden: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Warnung: Ung\u00FCltige(s) Argument(e) f\u00FCr Constructor: {0}"},
        {"Illegal.Principal.Type.type", "Ung\u00FCltiger Principal-Typ: {0}"},
        {"Illegal.option.option", "Ung\u00FCltige Option: {0}"},
        {"Usage.policytool.options.", "Verwendung: policytool [Optionen]"},
        {".file.file.policy.file.location",
                " [-file <Datei>]    Policy-Dateiverzeichnis"},
        {"New", "Neu"},
        {"Open", "\u00D6ffnen"},
        {"Save", "Speichern"},
        {"Save.As", "Speichern unter"},
        {"View.Warning.Log", "Warnungslog anzeigen"},
        {"Exit", "Beenden"},
        {"Add.Policy.Entry", "Policy-Eintrag hinzuf\u00FCgen"},
        {"Edit.Policy.Entry", "Policy-Eintrag bearbeiten"},
        {"Remove.Policy.Entry", "Policy-Eintrag entfernen"},
        {"Edit", "Bearbeiten"},
        {"Retain", "Beibehalten"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Warnung: M\u00F6glicherweise enth\u00E4lt der Dateiname Escapezeichen mit Backslash. Es ist nicht notwendig, Backslash-Zeichen zu escapen (das Tool f\u00FChrt dies automatisch beim Schreiben des Policy-Contents in den persistenten Speicher aus).\n\nKlicken Sie auf \"Beibehalten\", um den eingegebenen Namen beizubehalten oder auf \"Bearbeiten\", um den Namen zu bearbeiten."},

        {"Add.Public.Key.Alias", "Public Key-Alias hinzuf\u00FCgen"},
        {"Remove.Public.Key.Alias", "Public Key-Alias entfernen"},
        {"File", "Datei"},
        {"KeyStore", "KeyStore"},
        {"Policy.File.", "Policy-Datei:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "Policy-Datei konnte nicht ge\u00F6ffnet werden: {0}: {1}"},
        {"Policy.Tool", "Policy-Tool"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Beim \u00D6ffnen der Policy-Konfiguration sind Fehler aufgetreten. Weitere Informationen finden Sie im Warnungslog."},
        {"Error", "Fehler"},
        {"OK", "OK"},
        {"Status", "Status"},
        {"Warning", "Warnung"},
        {"Permission.",
                "Berechtigung:                                                       "},
        {"Principal.Type.", "Principal-Typ:"},
        {"Principal.Name.", "Principal-Name:"},
        {"Target.Name.",
                "Zielname:                                                    "},
        {"Actions.",
                "Aktionen:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "Vorhandene Datei {0} \u00FCberschreiben?"},
        {"Cancel", "Abbrechen"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "Principal hinzuf\u00FCgen"},
        {"Edit.Principal", "Principal bearbeiten"},
        {"Remove.Principal", "Principal entfernen"},
        {"Principals.", "Principals:"},
        {".Add.Permission", "  Berechtigung hinzuf\u00FCgen"},
        {".Edit.Permission", "  Berechtigung bearbeiten"},
        {"Remove.Permission", "Berechtigung entfernen"},
        {"Done", "Fertig"},
        {"KeyStore.URL.", "KeyStore-URL:"},
        {"KeyStore.Type.", "KeyStore-Typ:"},
        {"KeyStore.Provider.", "KeyStore-Provider:"},
        {"KeyStore.Password.URL.", "KeyStore-Kennwort-URL:"},
        {"Principals", "Principals"},
        {".Edit.Principal.", "  Principal bearbeiten:"},
        {".Add.New.Principal.", "  Neuen Principal hinzuf\u00FCgen:"},
        {"Permissions", "Berechtigungen"},
        {".Edit.Permission.", "  Berechtigung bearbeiten:"},
        {".Add.New.Permission.", "  Neue Berechtigung hinzuf\u00FCgen:"},
        {"Signed.By.", "Signiert von:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "Principal kann nicht mit einer Platzhalterklasse ohne Platzhalternamen angegeben werden"},
        {"Cannot.Specify.Principal.without.a.Name",
            "Principal kann nicht ohne einen Namen angegeben werden"},
        {"Permission.and.Target.Name.must.have.a.value",
                "Berechtigung und Zielname m\u00FCssen einen Wert haben"},
        {"Remove.this.Policy.Entry.", "Diesen Policy-Eintrag entfernen?"},
        {"Overwrite.File", "Datei \u00FCberschreiben"},
        {"Policy.successfully.written.to.filename",
                "Policy erfolgreich in {0} geschrieben"},
        {"null.filename", "Null-Dateiname"},
        {"Save.changes.", "\u00C4nderungen speichern?"},
        {"Yes", "Ja"},
        {"No", "Nein"},
        {"Policy.Entry", "Policy-Eintrag"},
        {"Save.Changes", "\u00C4nderungen speichern"},
        {"No.Policy.Entry.selected", "Kein Policy-Eintrag ausgew\u00E4hlt"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "KeyStore kann nicht ge\u00F6ffnet werden: {0}"},
        {"No.principal.selected", "Kein Principal ausgew\u00E4hlt"},
        {"No.permission.selected", "Keine Berechtigung ausgew\u00E4hlt"},
        {"name", "Name"},
        {"configuration.type", "Konfigurationstyp"},
        {"environment.variable.name", "Umgebungsvariablenname"},
        {"library.name", "Library-Name"},
        {"package.name", "Packagename"},
        {"policy.type", "Policy-Typ"},
        {"property.name", "Eigenschaftsname"},
        {"provider.name", "Providername"},
        {"url", "URL"},
        {"method.list", "Methodenliste"},
        {"request.headers.list", "Headerliste anfordern"},
        {"Principal.List", "Principal-Liste"},
        {"Permission.List", "Berechtigungsliste"},
        {"Code.Base", "Codebase"},
        {"KeyStore.U.R.L.", "KeyStore-URL:"},
        {"KeyStore.Password.U.R.L.", "KeyStore-Kennwort-URL:"}
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
