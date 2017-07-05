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
public class Resources_sv extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
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
        {"provider.name", "leverant\u00F6rsnamn"},
        {"url", "url"},
        {"method.list", "metodlista"},
        {"request.headers.list", "beg\u00E4ranrubriklista"},
        {"Principal.List", "Lista \u00F6ver identitetshavare"},
        {"Permission.List", "Beh\u00F6righetslista"},
        {"Code.Base", "Kodbas"},
        {"KeyStore.U.R.L.", "URL f\u00F6r nyckellager:"},
        {"KeyStore.Password.U.R.L.", "URL f\u00F6r l\u00F6senord till nyckellager:"}
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
