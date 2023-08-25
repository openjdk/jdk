/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4152725
 * @summary Verify that the default locale can be specified from the
 * command line.
 * @run main/othervm -Duser.language=de -Duser.country=DE -Duser.variant=EURO
 *      LocaleShouldSetFromCLI de_DE_EURO
 * @run main/othervm -Duser.language=ja -Duser.country= -Duser.variant=
 *      LocaleShouldSetFromCLI ja
 * @run main/othervm -Duser.language=en -Duser.country=SG -Duser.variant=
 *      LocaleShouldSetFromCLI en_SG
 * @run main/othervm -Duser.language= -Duser.country=DE -Duser.variant=EURO
 *      LocaleShouldSetFromCLI _DE_EURO
 * @run main/othervm -Duser.language=ja -Duser.country= -Duser.variant=YOMI
 *      LocaleShouldSetFromCLI ja__YOMI
 * @run main/othervm -Duser.language= -Duser.country= -Duser.variant=EURO
 *      LocaleShouldSetFromCLI __EURO
 * @run main/othervm -Duser.language=de -Duser.region=DE_EURO
 *      LocaleShouldSetFromCLI de_DE_EURO
 */

import java.util.Locale;

public class LocaleShouldSetFromCLI {

    public static void main(String[] args) {

        if (args.length != 1) {
            throw new RuntimeException("expected locale needs to be specified");
        }

        Locale locale = Locale.getDefault();

        // don't use Locale.toString - it's bogus
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String variant = locale.getVariant();
        String localeID = null;
        if (variant.length() > 0) {
            localeID = language + "_" + country + "_" + variant;
        } else if (country.length() > 0) {
            localeID = language + "_" + country;
        } else {
            localeID = language;
        }

        if (localeID.equals(args[0])) {
            System.out.println("Correctly set from command line: " + localeID);
        } else {
            throw new RuntimeException("expected default locale: " + args[0]
                    + ", actual default locale: " + localeID);
        }
    }
}
