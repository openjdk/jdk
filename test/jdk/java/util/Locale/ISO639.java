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
 * @summary test ISO639-2 language codes
 * @library /java/text/testlib
 * @compile -encoding ascii ISO639.java
 * @bug 4175998 8303917
 * @run junit ISO639
 */

/*
 *
 *
 * (C) Copyright IBM Corp. 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 *
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;
import java.util.stream.Stream;


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ISO639 {

    /**
     * This test verifies for a given locale created from the ISO639 2-letter code,
     * the correct ISO639 3-letter code is returned when calling getISO3Language().
     */
    @ParameterizedTest
    @MethodSource("expectedISO639Codes")
    public void ISO3LetterTest(String ISO2, String expectedISO3) {
        Locale loc = Locale.of(ISO2);
        String actualISO3 = loc.getISO3Language();
        assertEquals(actualISO3, expectedISO3,
                String.format("The Locale '%s' returned a bad ISO3 language code. " +
                        "Got '%s' instead of '%s'", loc, actualISO3, expectedISO3));
    }

    // expectedISO639Codes generated from https://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt
    // on March 9th, 2023.
    private static Stream<Arguments> expectedISO639Codes() {
        return Stream.of(
             Arguments.of("aa","aar","aar"),
             Arguments.of("ab","abk","abk"),
             Arguments.of("af","afr","afr"),
             Arguments.of("ak","aka","aka"),
             Arguments.of("sq","sqi","alb"),
             Arguments.of("am","amh","amh"),
             Arguments.of("ar","ara","ara"),
             Arguments.of("an","arg","arg"),
             Arguments.of("hy","hye","arm"),
             Arguments.of("as","asm","asm"),
             Arguments.of("av","ava","ava"),
             Arguments.of("ae","ave","ave"),
             Arguments.of("ay","aym","aym"),
             Arguments.of("az","aze","aze"),
             Arguments.of("ba","bak","bak"),
             Arguments.of("bm","bam","bam"),
             Arguments.of("eu","eus","baq"),
             Arguments.of("be","bel","bel"),
             Arguments.of("bn","ben","ben"),
             Arguments.of("bh","bih","bih"),
             Arguments.of("bi","bis","bis"),
             Arguments.of("bs","bos","bos"),
             Arguments.of("br","bre","bre"),
             Arguments.of("bg","bul","bul"),
             Arguments.of("my","mya","bur"),
             Arguments.of("ca","cat","cat"),
             Arguments.of("ch","cha","cha"),
             Arguments.of("ce","che","che"),
             Arguments.of("zh","zho","chi"),
             Arguments.of("cu","chu","chu"),
             Arguments.of("cv","chv","chv"),
             Arguments.of("kw","cor","cor"),
             Arguments.of("co","cos","cos"),
             Arguments.of("cr","cre","cre"),
             Arguments.of("cs","ces","cze"),
             Arguments.of("da","dan","dan"),
             Arguments.of("dv","div","div"),
             Arguments.of("nl","nld","dut"),
             Arguments.of("dz","dzo","dzo"),
             Arguments.of("en","eng","eng"),
             Arguments.of("eo","epo","epo"),
             Arguments.of("et","est","est"),
             Arguments.of("ee","ewe","ewe"),
             Arguments.of("fo","fao","fao"),
             Arguments.of("fj","fij","fij"),
             Arguments.of("fi","fin","fin"),
             Arguments.of("fr","fra","fre"),
             Arguments.of("fy","fry","fry"),
             Arguments.of("ff","ful","ful"),
             Arguments.of("ka","kat","geo"),
             Arguments.of("de","deu","ger"),
             Arguments.of("gd","gla","gla"),
             Arguments.of("ga","gle","gle"),
             Arguments.of("gl","glg","glg"),
             Arguments.of("gv","glv","glv"),
             Arguments.of("el","ell","gre"),
             Arguments.of("gn","grn","grn"),
             Arguments.of("gu","guj","guj"),
             Arguments.of("ht","hat","hat"),
             Arguments.of("ha","hau","hau"),
             Arguments.of("he","heb","heb"),
             Arguments.of("hz","her","her"),
             Arguments.of("hi","hin","hin"),
             Arguments.of("ho","hmo","hmo"),
             Arguments.of("hr","hrv","hrv"),
             Arguments.of("hu","hun","hun"),
             Arguments.of("ig","ibo","ibo"),
             Arguments.of("is","isl","ice"),
             Arguments.of("io","ido","ido"),
             Arguments.of("ii","iii","iii"),
             Arguments.of("iu","iku","iku"),
             Arguments.of("ie","ile","ile"),
             Arguments.of("ia","ina","ina"),
             Arguments.of("id","ind","ind"),
             Arguments.of("ik","ipk","ipk"),
             Arguments.of("it","ita","ita"),
             Arguments.of("jv","jav","jav"),
             Arguments.of("ja","jpn","jpn"),
             Arguments.of("kl","kal","kal"),
             Arguments.of("kn","kan","kan"),
             Arguments.of("ks","kas","kas"),
             Arguments.of("kr","kau","kau"),
             Arguments.of("kk","kaz","kaz"),
             Arguments.of("km","khm","khm"),
             Arguments.of("ki","kik","kik"),
             Arguments.of("rw","kin","kin"),
             Arguments.of("ky","kir","kir"),
             Arguments.of("kv","kom","kom"),
             Arguments.of("kg","kon","kon"),
             Arguments.of("ko","kor","kor"),
             Arguments.of("kj","kua","kua"),
             Arguments.of("ku","kur","kur"),
             Arguments.of("lo","lao","lao"),
             Arguments.of("la","lat","lat"),
             Arguments.of("lv","lav","lav"),
             Arguments.of("li","lim","lim"),
             Arguments.of("ln","lin","lin"),
             Arguments.of("lt","lit","lit"),
             Arguments.of("lb","ltz","ltz"),
             Arguments.of("lu","lub","lub"),
             Arguments.of("lg","lug","lug"),
             Arguments.of("mk","mkd","mac"),
             Arguments.of("mh","mah","mah"),
             Arguments.of("ml","mal","mal"),
             Arguments.of("mi","mri","mao"),
             Arguments.of("mr","mar","mar"),
             Arguments.of("ms","msa","may"),
             Arguments.of("mg","mlg","mlg"),
             Arguments.of("mt","mlt","mlt"),
             Arguments.of("mn","mon","mon"),
             Arguments.of("na","nau","nau"),
             Arguments.of("nv","nav","nav"),
             Arguments.of("nr","nbl","nbl"),
             Arguments.of("nd","nde","nde"),
             Arguments.of("ng","ndo","ndo"),
             Arguments.of("ne","nep","nep"),
             Arguments.of("nn","nno","nno"),
             Arguments.of("nb","nob","nob"),
             Arguments.of("no","nor","nor"),
             Arguments.of("ny","nya","nya"),
             Arguments.of("oc","oci","oci"),
             Arguments.of("oj","oji","oji"),
             Arguments.of("or","ori","ori"),
             Arguments.of("om","orm","orm"),
             Arguments.of("os","oss","oss"),
             Arguments.of("pa","pan","pan"),
             Arguments.of("fa","fas","per"),
             Arguments.of("pi","pli","pli"),
             Arguments.of("pl","pol","pol"),
             Arguments.of("pt","por","por"),
             Arguments.of("ps","pus","pus"),
             Arguments.of("qu","que","que"),
             Arguments.of("rm","roh","roh"),
             Arguments.of("ro","ron","rum"),
             Arguments.of("rn","run","run"),
             Arguments.of("ru","rus","rus"),
             Arguments.of("sg","sag","sag"),
             Arguments.of("sa","san","san"),
             Arguments.of("si","sin","sin"),
             Arguments.of("sk","slk","slo"),
             Arguments.of("sl","slv","slv"),
             Arguments.of("se","sme","sme"),
             Arguments.of("sm","smo","smo"),
             Arguments.of("sn","sna","sna"),
             Arguments.of("sd","snd","snd"),
             Arguments.of("so","som","som"),
             Arguments.of("st","sot","sot"),
             Arguments.of("es","spa","spa"),
             Arguments.of("sc","srd","srd"),
             Arguments.of("sr","srp","srp"),
             Arguments.of("ss","ssw","ssw"),
             Arguments.of("su","sun","sun"),
             Arguments.of("sw","swa","swa"),
             Arguments.of("sv","swe","swe"),
             Arguments.of("ty","tah","tah"),
             Arguments.of("ta","tam","tam"),
             Arguments.of("tt","tat","tat"),
             Arguments.of("te","tel","tel"),
             Arguments.of("tg","tgk","tgk"),
             Arguments.of("tl","tgl","tgl"),
             Arguments.of("th","tha","tha"),
             Arguments.of("bo","bod","tib"),
             Arguments.of("ti","tir","tir"),
             Arguments.of("to","ton","ton"),
             Arguments.of("tn","tsn","tsn"),
             Arguments.of("ts","tso","tso"),
             Arguments.of("tk","tuk","tuk"),
             Arguments.of("tr","tur","tur"),
             Arguments.of("tw","twi","twi"),
             Arguments.of("ug","uig","uig"),
             Arguments.of("uk","ukr","ukr"),
             Arguments.of("ur","urd","urd"),
             Arguments.of("uz","uzb","uzb"),
             Arguments.of("ve","ven","ven"),
             Arguments.of("vi","vie","vie"),
             Arguments.of("vo","vol","vol"),
             Arguments.of("cy","cym","wel"),
             Arguments.of("wa","wln","wln"),
             Arguments.of("wo","wol","wol"),
             Arguments.of("xh","xho","xho"),
             Arguments.of("yi","yid","yid"),
             Arguments.of("yo","yor","yor"),
             Arguments.of("za","zha","zha"),
             Arguments.of("zu","zul","zul")
        );
    }

    @Test
    @Disabled("For updating expected ISO data, NOT an actual test")
    public void getISOData() {
        // Remove @Disabled to generate new ISO Data
        generateTables();
    }

    private static final String ISO639 = "ISO-639-2_utf-8.txt";
    private static void generateTables() {
        try {
            BufferedReader ISO639File = new BufferedReader(new FileReader(ISO639));
            for (String line = ISO639File.readLine(); line != null; line = ISO639File.readLine()) {
                String[] tokens= line.split("\\|");
                String iso639_1 = tokens[2];
                String iso639_2B = tokens[1];
                String iso639_2T = tokens[0];
                if (iso639_1.isEmpty()){
                    continue; // Skip if not both a 639-1 and 639-2 code
                }
                if (iso639_2B.isEmpty()){
                    iso639_2B = iso639_2T; // Default 639/B to 639/T if empty
                }
                System.out.printf("""
                        Arguments.of("%s","%s","%s"),
                        """, iso639_1, iso639_2B, iso639_2T);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
