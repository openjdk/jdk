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
 * @compile -encoding ascii Bug4175998Test.java
 * @run main Bug4175998Test
 * @bug 4175998 8303917
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
import java.util.*;

/**
 *  Bug4175998Test verifies that the following bug has been fixed:
 *  Bug 4175998 - The java.util.Locale.getISO3Language() returns wrong result for a locale with
 *           language code 'ta'(Tamil).
 */
public class Bug4175998Test extends IntlTest {
    public static void main(String[] args) throws Exception {
        new Bug4175998Test().run(args);
        //generateTables();    //uncomment this to regenerate data tables
    }

    public void testIt() throws Exception {
        boolean bad = false;
        for (final String[] localeCodes : CODES) {
            final Locale l = Locale.of(localeCodes[0]);
            final String iso3 = l.getISO3Language();
            if (!iso3.equals(localeCodes[1])) {
                logln("Locale(" + l + ") returned bad ISO3 language code."
                        + "   Got '" + iso3 + "' instead of '" + localeCodes[1] + "'");
                bad = true;
            }
        }
        if (bad) {
            errln("Bad ISO3 language codes detected.");
        }
    }

     private static final String[][] CODES = {
         {"aa","aar","aar"},
         {"ab","abk","abk"},
         {"af","afr","afr"},
         {"ak","aka","aka"},
         {"sq","sqi","alb"},
         {"am","amh","amh"},
         {"ar","ara","ara"},
         {"an","arg","arg"},
         {"hy","hye","arm"},
         {"as","asm","asm"},
         {"av","ava","ava"},
         {"ae","ave","ave"},
         {"ay","aym","aym"},
         {"az","aze","aze"},
         {"ba","bak","bak"},
         {"bm","bam","bam"},
         {"eu","eus","baq"},
         {"be","bel","bel"},
         {"bn","ben","ben"},
         {"bh","bih","bih"},
         {"bi","bis","bis"},
         {"bs","bos","bos"},
         {"br","bre","bre"},
         {"bg","bul","bul"},
         {"my","mya","bur"},
         {"ca","cat","cat"},
         {"ch","cha","cha"},
         {"ce","che","che"},
         {"zh","zho","chi"},
         {"cu","chu","chu"},
         {"cv","chv","chv"},
         {"kw","cor","cor"},
         {"co","cos","cos"},
         {"cr","cre","cre"},
         {"cs","ces","cze"},
         {"da","dan","dan"},
         {"dv","div","div"},
         {"nl","nld","dut"},
         {"dz","dzo","dzo"},
         {"en","eng","eng"},
         {"eo","epo","epo"},
         {"et","est","est"},
         {"ee","ewe","ewe"},
         {"fo","fao","fao"},
         {"fj","fij","fij"},
         {"fi","fin","fin"},
         {"fr","fra","fre"},
         {"fy","fry","fry"},
         {"ff","ful","ful"},
         {"ka","kat","geo"},
         {"de","deu","ger"},
         {"gd","gla","gla"},
         {"ga","gle","gle"},
         {"gl","glg","glg"},
         {"gv","glv","glv"},
         {"el","ell","gre"},
         {"gn","grn","grn"},
         {"gu","guj","guj"},
         {"ht","hat","hat"},
         {"ha","hau","hau"},
         {"he","heb","heb"},
         {"hz","her","her"},
         {"hi","hin","hin"},
         {"ho","hmo","hmo"},
         {"hr","hrv","hrv"},
         {"hu","hun","hun"},
         {"ig","ibo","ibo"},
         {"is","isl","ice"},
         {"io","ido","ido"},
         {"ii","iii","iii"},
         {"iu","iku","iku"},
         {"ie","ile","ile"},
         {"ia","ina","ina"},
         {"id","ind","ind"},
         {"ik","ipk","ipk"},
         {"it","ita","ita"},
         {"jv","jav","jav"},
         {"ja","jpn","jpn"},
         {"kl","kal","kal"},
         {"kn","kan","kan"},
         {"ks","kas","kas"},
         {"kr","kau","kau"},
         {"kk","kaz","kaz"},
         {"km","khm","khm"},
         {"ki","kik","kik"},
         {"rw","kin","kin"},
         {"ky","kir","kir"},
         {"kv","kom","kom"},
         {"kg","kon","kon"},
         {"ko","kor","kor"},
         {"kj","kua","kua"},
         {"ku","kur","kur"},
         {"lo","lao","lao"},
         {"la","lat","lat"},
         {"lv","lav","lav"},
         {"li","lim","lim"},
         {"ln","lin","lin"},
         {"lt","lit","lit"},
         {"lb","ltz","ltz"},
         {"lu","lub","lub"},
         {"lg","lug","lug"},
         {"mk","mkd","mac"},
         {"mh","mah","mah"},
         {"ml","mal","mal"},
         {"mi","mri","mao"},
         {"mr","mar","mar"},
         {"ms","msa","may"},
         {"mg","mlg","mlg"},
         {"mt","mlt","mlt"},
         {"mn","mon","mon"},
         {"na","nau","nau"},
         {"nv","nav","nav"},
         {"nr","nbl","nbl"},
         {"nd","nde","nde"},
         {"ng","ndo","ndo"},
         {"ne","nep","nep"},
         {"nn","nno","nno"},
         {"nb","nob","nob"},
         {"no","nor","nor"},
         {"ny","nya","nya"},
         {"oc","oci","oci"},
         {"oj","oji","oji"},
         {"or","ori","ori"},
         {"om","orm","orm"},
         {"os","oss","oss"},
         {"pa","pan","pan"},
         {"fa","fas","per"},
         {"pi","pli","pli"},
         {"pl","pol","pol"},
         {"pt","por","por"},
         {"ps","pus","pus"},
         {"qu","que","que"},
         {"rm","roh","roh"},
         {"ro","ron","rum"},
         {"rn","run","run"},
         {"ru","rus","rus"},
         {"sg","sag","sag"},
         {"sa","san","san"},
         {"si","sin","sin"},
         {"sk","slk","slo"},
         {"sl","slv","slv"},
         {"se","sme","sme"},
         {"sm","smo","smo"},
         {"sn","sna","sna"},
         {"sd","snd","snd"},
         {"so","som","som"},
         {"st","sot","sot"},
         {"es","spa","spa"},
         {"sc","srd","srd"},
         {"sr","srp","srp"},
         {"ss","ssw","ssw"},
         {"su","sun","sun"},
         {"sw","swa","swa"},
         {"sv","swe","swe"},
         {"ty","tah","tah"},
         {"ta","tam","tam"},
         {"tt","tat","tat"},
         {"te","tel","tel"},
         {"tg","tgk","tgk"},
         {"tl","tgl","tgl"},
         {"th","tha","tha"},
         {"bo","bod","tib"},
         {"ti","tir","tir"},
         {"to","ton","ton"},
         {"tn","tsn","tsn"},
         {"ts","tso","tso"},
         {"tk","tuk","tuk"},
         {"tr","tur","tur"},
         {"tw","twi","twi"},
         {"ug","uig","uig"},
         {"uk","ukr","ukr"},
         {"ur","urd","urd"},
         {"uz","uzb","uzb"},
         {"ve","ven","ven"},
         {"vi","vie","vie"},
         {"vo","vol","vol"},
         {"cy","cym","wel"},
         {"wa","wln","wln"},
         {"wo","wol","wol"},
         {"xh","xho","xho"},
         {"yi","yid","yid"},
         {"yo","yor","yor"},
         {"za","zha","zha"},
         {"zu","zul","zul"},
    };

    // The following code was used to generate the table above from the two ISO standards.
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
                        {"%s","%s","%s"},
                        """, iso639_1, iso639_2B, iso639_2T);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

// CODES generated from https://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt
// on March 9th, 2023.
