/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4930708 4174436 5008498
 * @library /java/text/testlib
 * @summary test Danish Collation
 * @modules jdk.localedata
 * @run junit DanishTest
 */
/*
(C) Copyright Taligent, Inc. 1996 - All Rights Reserved
(C) Copyright IBM Corp. 1996 - All Rights Reserved

  The original version of this source code and documentation is copyrighted and
owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These materials are
provided under terms of a License Agreement between Taligent and Sun. This
technology is protected by multiple US and International patents. This notice and
attribution to Taligent may not be removed.
  Taligent is a registered trademark of Taligent, Inc.
*/

import java.util.Locale;
import java.text.Collator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

// Quick dummy program for printing out test results
public class DanishTest {

    /*
     * Data for TestPrimary()
     */
    private static final String[] primarySourceData = {
        "Lvi",
        "Lävi",
        "Lübeck",
        "ANDRÉ",
        "ANDRE",
        "ANNONCERER"
    };

    private static final String[] primaryTargetData = {
            "Lwi",
            "Löwi",
            "Lybeck",
            "ANDRé",
            "ANDRÉ",
            "ANNÓNCERER"
    };

    private static final int[] primaryResults = {
            -1, -1, 0, 0, 0, 0
    };

    /*
     * Data for TestTertiary()
     */
    private static final String[] tertiarySourceData = {
            "Luc",
            "luck",
            "Lübeck",
            "Lävi",
            "Löww"
    };

    private static final String[] tertiaryTargetData = {
            "luck",
            "Lübeck",
            "lybeck",
            "Löwe",
            "mast"
    };

    private static final int[] tertiaryResults = {
            -1, -1,  1, -1, -1
    };

    /*
     * Data for TestExtra()
     */
    private static final String[] testData = {
            "A/S",
            "ANDRE",
            "ANDRÉ", // E-acute
            "ANDRÈ", // E-grave
            "ANDRé", // e-acute
            "ANDRê", // e-circ
            "Andre",
            "André", // e-acute
            "ÁNDRE", // A-acute
            "ÀNDRE", // A-grave
            "andre",
            "ándre", // a-acute
            "àndre", // a-grave
            "ANDREAS",
            "ANNONCERER",
            "ANNÓNCERER", // O-acute
            "annoncerer",
            "annóncerer", // o-acute
            "AS",
            "AæRO", // ae-ligature
            "CA",
            "ÇA", // C-cedilla
            "CB",
            "ÇC", // C-cedilla
            "D.S.B.",
            "DA",
            "DB",
            "ÐORA", // capital eth
            "DSB",
            "ÐSB", // capital eth
            "DSC",
            "EKSTRA_ARBEJDE",
            "EKSTRABUD",
            "HØST",  // could the 0x00D8 be 0x2205?
            "HAAG",
            "HÅNDBOG", // A-ring
            "HAANDVÆRKSBANKEN", // AE-ligature
            "INTERNETFORBINDELSE",
            "Internetforbindelse",
            "ÍNTERNETFORBINDELSE", // I-acute
            "internetforbindelse",
            "ínternetforbindelse", // i-acute
            "Karl",
            "karl",
            "NIELSEN",
            "NIELS JØRGEN", // O-slash
            "NIELS-JØRGEN", // O-slash
            "OERVAL",
            "ŒRVAL", // OE-ligature
            "œRVAL", // oe-ligature
            "RÉE, A", // E-acute
            "REE, B",
            "RÉE, L", // E-acute
            "REE, V",
            "SCHYTT, B",
            "SCHYTT, H",
            "SCHÜTT, H", // U-diaeresis
            "SCHYTT, L",
            "SCHÜTT, M", // U-diaeresis
            "SS",
            "ss",
            "ß", // sharp S
            "SSA",
            "ßA", // sharp S
            "STOREKÆR", // AE-ligature
            "STORE VILDMOSE",
            "STORMLY",
            "STORM PETERSEN",
            "THORVALD",
            "THORVARDUR",
            "ÞORVARĐUR", //  capital thorn, capital d-stroke(like eth) (sami)
            "THYGESEN",
            "VESTERGÅRD, A",
            "VESTERGAARD, A",
            "VESTERGÅRD, B",                // 50
            "Westmalle",
            "YALLE",
            "Yderligere",
            "Ýderligere", // Y-acute
            "Üderligere", // U-diaeresis
            "ýderligere", // y-acute
            "üderligere", // u-diaeresis
            "Üruk-hai",
            "ZORO",
            "ÆBLE",  // AE-ligature
            "æBLE",  // ae-ligature
            "ÄBLE",  // A-diaeresis
            "äBLE",  // a-diaeresis
            "ØBERG", // O-stroke
            "øBERG", // o-stroke
            "ÖBERG", // O-diaeresis
            "öBERG"  // o-diaeresis
    };

    @Test
    public void TestPrimary() {
        TestUtils.doCollatorTest(myCollation, Collator.PRIMARY,
               primarySourceData, primaryTargetData, primaryResults);
    }

    @Test
    public void TestTertiary() {
        TestUtils.doCollatorTest(myCollation, Collator.TERTIARY,
               tertiarySourceData, tertiaryTargetData, tertiaryResults);

        for (int i = 0; i < testData.length-1; i++) {
            for (int j = i+1; j < testData.length; j++) {
                TestUtils.doCollatorTest(myCollation, testData[i], testData[j], -1);
            }
        }
    }

    private final Collator myCollation = Collator.getInstance(Locale.of("da"));
}
