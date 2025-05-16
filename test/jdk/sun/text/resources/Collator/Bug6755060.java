/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6755060
 * @modules jdk.localedata
 * @summary updating collation tables for thai to make it consistent with CLDR 1.9
 */

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

public class Bug6755060 {

  /********************************************************
  *********************************************************/
  public static void main (String[] args) {

    Locale reservedLocale = Locale.getDefault();

    try{

        int errors=0;

        Locale loc = new Locale ("th", "TH");   // Thai

        Locale.setDefault (loc);
        Collator col = Collator.getInstance ();

        /*
        * The original data "data" are the data to be sorted provided by the submitter of the CR.
        * It's in correct order in accord with thai collation in CLDR 1.9. If we use old Java without this fix,
        * the output order will be incorrect. Correct order will be turned into incorrect order.

        * If fix is there, "data" after sorting will be unchanged, same as "sortedData". If fix is lost (regression),
        * "data" after sorting will be changed, not as "sortedData".(not correct anymore)

        * The submitter of the CR also gives a expected "sortedData" in the CR, but it's in accord with collation in CLDR 1.4.
        * His data to be sorted are actually well sorted in accord with CLDR 1.9.
        */

        String[] data = {"ก", "กฯ", "กๆ", "ก๏", "ก๚", "ก๛", "ก๎", "ก์", "ก่", "กก", "ก๋ก", "กํ", "กะ", "กัก", "กา", "กำ", "กิ", "กี", "กึ", "กื", "กุ", "กู", "เก", "เก่", "เก้", "เก๋", "แก", "โก", "ใก", "ไก", "กฺ", "ฤา", "ฤๅ", "เล", "ไฦ"};

        String[] sortedData = {"ก", "กฯ", "กๆ", "ก๏", "ก๚", "ก๛", "ก๎", "ก์", "ก่", "กก", "ก๋ก", "กํ", "กะ", "กัก", "กา", "กำ", "กิ", "กี", "กึ", "กื", "กุ", "กู", "เก", "เก่", "เก้", "เก๋", "แก", "โก", "ใก", "ไก", "กฺ", "ฤา", "ฤๅ", "เล", "ไฦ"};

        Arrays.sort (data, col);

        System.out.println ("Using " + loc.getDisplayName());
        for (int i = 0;  i < data.length;  i++) {
            System.out.println(data[i] + "  :  " + sortedData[i]);
            if (sortedData[i].compareTo(data[i]) != 0) {
                errors++;
            }
        }//end for

        if (errors > 0){
            StringBuffer expected = new StringBuffer(), actual = new StringBuffer();
            expected.append(sortedData[0]);
            actual.append(data[0]);

                for (int i=1; i<data.length; i++) {
                    expected.append(",");
                    expected.append(sortedData[i]);

                    actual.append(",");
                    actual.append(data[i]);
                }

            String errmsg = "Error is found in collation testing in Thai\n" + "expected order is: " + expected.toString() + "\n" + "actual order is: " + actual.toString() + "\n";

            throw new RuntimeException(errmsg);
        }
    }finally{
        // restore the reserved locale
        Locale.setDefault(reservedLocale);
    }

  }//end main

}//end class CollatorTest
