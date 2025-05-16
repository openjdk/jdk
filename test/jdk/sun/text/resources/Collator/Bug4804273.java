/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4804273
 * @modules jdk.localedata
 * @summary updating collation tables for swedish
 */

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

public class Bug4804273 {

  /********************************************************
  *********************************************************/
  public static void main (String[] args) {
      Locale reservedLocale = Locale.getDefault();
      try {
        int errors=0;

        Locale loc = new Locale ("sv", "se");   // Swedish

        Locale.setDefault (loc);
        Collator col = Collator.getInstance ();

        String[] data = {"A",
                         "Aa",
                         "Ae",
                         "B",
                         "Y",
                         "Ü", // U-umlaut
                         "Z",
                         "Å", // A-ring
                         "Ä", // A-umlaut
                         "Æ", // AE ligature
                         "Ö", // O-umlaut
                         "a̋", // a-double-acute
                         "Ø", // O-stroke
                         "a",
                         "aa",
                         "ae",
                         "b",
                         "y",
                         "ü", // u-umlaut
                         "z",
                         "A̋", // A-double-acute
                         "å", // a-ring
                         "ä", // a-umlaut
                         "æ", // ae ligature
                         "ö", // o-umlaut
                         "ø", // o-stroke
        };


        String[] sortedData = {"a",
                               "A",
                               "aa",
                               "Aa",
                               "ae",
                               "Ae",
                               "b",
                               "B",
                               "y",
                               "Y",
                               "ü", // o-umlaut
                               "Ü", // o-umlaut
                               "z",
                               "Z",
                               "å", // a-ring
                               "Å", // A-ring
                               "ä", // a-umlaut
                               "Ä", // A-umlaut
                               "a̋", // a-double-acute
                               "A̋", // A-double-acute
                               "æ", // ae ligature
                               "Æ", // AE ligature
                               "ö", // o-umlaut
                               "Ö", // O-umlaut
                               "ø", // o-stroke
                               "Ø", // O-stroke
        };

        Arrays.sort (data, col);

        System.out.println ("Using " + loc.getDisplayName());
        for (int i = 0;  i < data.length;  i++) {
            System.out.println(data[i] + "  :  " + sortedData[i]);
            if (sortedData[i].compareTo(data[i]) != 0) {
                errors++;
            }
        }//end for

        if (errors > 0)
            throw new RuntimeException("There are " + errors +
                        " words sorted incorrectly!");
      } finally {
          // restore the reserved locale
          Locale.setDefault(reservedLocale);
      }
  }//end main

}//end class CollatorTest
