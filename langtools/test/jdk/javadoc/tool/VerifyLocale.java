/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8035473
 * @summary Verify that init method works correctly.
 * @ignore 8149565
 * @modules jdk.javadoc
 */

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic.Kind;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.DocletEnvironment;

public class VerifyLocale implements Doclet {
    static String language;
    static String country;
    static String variant;

    Locale locale;
    Reporter reporter;

    public static void main(String[] args) {
        String thisFile = "" +
            new java.io.File(System.getProperty("test.src", "."),
                             "VerifyLocale.java");

        for (Locale loc : Locale.getAvailableLocales()) {
            language = loc.getLanguage();
            country = loc.getCountry();
            variant = loc.getVariant();
            if (!language.equals("")) {
                String[] command_line = {
                    // jumble the options in some weird order
                    "-doclet", "VerifyLocale",
                    "-locale", language + (country.equals("") ? "" : ("_" + country + (variant.equals("") ? "" : "_" + variant))),
                    "-docletpath", System.getProperty("test.classes", "."),
                    thisFile
                };
                if (jdk.javadoc.internal.tool.Main.execute(command_line) != 0)
                    throw new Error("Javadoc encountered warnings or errors.");
            }
        }
    }

    public boolean run(DocletEnvironment root) {
        reporter.print(Kind.NOTE, "just a test: Locale is: " + locale.getDisplayName());
        return language.equals(locale.getLanguage())
               && country.equals(locale.getCountry())
               && variant.equals(locale.getVariant());
    }

    @Override
    public String getName() {
        return "Test";
    }

    @Override
    public Set<Option> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    public void init(Locale locale, Reporter reporter) {
        this.locale = locale;
        this.reporter = reporter;
    }
}
