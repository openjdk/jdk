 /*
  * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
  * Copyright (c) 2023, Alibaba Group Holding Limited. All rights reserved.
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
 * @summary Basic tests of toString & toEngineeringString method
 * @run main ToStringTests
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+EliminateAutoBox -XX:AutoBoxCacheMax=20000 ToStringTests
 * @author shaojin.wensj@alibaba-inc.com
 */

import java.math.BigDecimal;

public class ToStringTests {
    public static void main(String argv[]) {
        String[][] testCases = {
                {"0", "0", "0"},
                {"1", "1", "1"},
                {"10", "10", "10"},
                {"2e1", "2E+1", "20"},
                {"3e2", "3E+2", "300"},
                {"4e3", "4E+3", "4E+3"},
                {"5e4", "5E+4", "50E+3"},
                {"6e5", "6E+5", "600E+3"},
                {"7e6", "7E+6", "7E+6"},
                {"8e7", "8E+7", "80E+6"},
                {"9e8", "9E+8", "900E+6"},
                {"1e9", "1E+9", "1E+9"},

                {".0", "0.0", "0.0"},
                {".1", "0.1", "0.1"},
                {".10", "0.10", "0.10"},
                {"1e-1", "0.1", "0.1"},
                {"1e-1", "0.1", "0.1"},
                {"2e-2", "0.02", "0.02"},
                {"3e-3", "0.003", "0.003"},
                {"4e-4", "0.0004", "0.0004"},
                {"5e-5", "0.00005", "0.00005"},
                {"6e-6", "0.000006", "0.000006"},
                {"7e-7", "7E-7", "700E-9"},
                {"8e-8", "8E-8", "80E-9"},
                {"9e-9", "9E-9", "9E-9"},
                {"9000e-12", "9.000E-9", "9.000E-9"},

                {"9000e-22", "9.000E-19", "900.0E-21"},
                {"12345678901234567890", "12345678901234567890", "12345678901234567890"},
                {"12345678901234567890e22", "1.2345678901234567890E+41", "123.45678901234567890E+39"},
                {"12345678901234567890e-22", "0.0012345678901234567890", "0.0012345678901234567890"},
                {"9223372036854775808", "9223372036854775808", "9223372036854775808"},
                {"12345678901234567890", "12345678901234567890", "12345678901234567890"},
                {"12345678901234567890.45", "12345678901234567890.45", "12345678901234567890.45"},
                {"123.45", "123.45", "123.45"},
                {"1.0E+2147483649", "1.0E+2147483649", "1.0E+2147483649"},
                {
                        "1234567890000012345678900000001234567890000000123456789000000000000000000000000.45",
                        "1234567890000012345678900000001234567890000000123456789000000000000000000000000.45",
                        "1234567890000012345678900000001234567890000000123456789000000000000000000000000.45"
                }
        };

        int errors = 0;
        for (String[] testCase : testCases) {
            BigDecimal bd = new BigDecimal(testCase[0]);
            String s;

            if (!(s = bd.toString()).equals(testCase[1])) {
                errors++;
                System.err.println("Unexpected result ``" +
                        s + "'', expect ``" + testCase[1] + "'' from BigDecimal " +
                        bd);
            }
            if (!(s = bd.toEngineeringString()).equals(testCase[2])) {
                errors++;
                System.err.println("Unexpected engineering result ``" +
                        s + "'', expect ``" + testCase[2] + "'' from BigDecimal " +
                        testCase[0]);
            }

            bd = new BigDecimal("-" + testCase[0]);
            if (bd.signum() != 0 && !(s = (bd.toString())).equals("-" + testCase[1])) {
                errors++;
                System.err.println("Unexpected result ``" +
                        s + "'', expect ``" + testCase[1] + "'' from BigDecimal " +
                        bd);
            }

            if (bd.signum() != 0 && !(s = (bd.toEngineeringString())).equals("-" + testCase[2])) {
                errors++;
                System.err.println("Unexpected engineering result ``" +
                        s + "'', expect ``" + testCase[2] + "'' from BigDecimal " +
                        bd);
            }
        }

        if (errors > 0) {
            throw new RuntimeException(errors + " errors during run.");
        }
    }
}
