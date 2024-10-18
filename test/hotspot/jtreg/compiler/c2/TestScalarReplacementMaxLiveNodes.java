/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331736
 * @summary Check that C2 does not exceed max live node limit when splitting unique types of large allocation merge.
 * @library /test/lib /
 * @requires vm.debug & vm.compiler2.enabled
 * @compile -XDstringConcat=inline TestScalarReplacementMaxLiveNodes.java
 * @run main/othervm compiler.c2.TestScalarReplacementMaxLiveNodes
 * @run main/othervm -Xbatch -XX:-OptimizeStringConcat -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+ReduceAllocationMerges
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestScalarReplacementMaxLiveNodes::test
 *                   -XX:CompileCommand=compileonly,*TestScalarReplacementMaxLiveNodes*::*test*
 *                   -XX:CompileCommand=inline,*String*::*
 *                   -XX:CompileCommand=dontinline,*StringBuilder*::ensureCapacityInternal
 *                   -XX:CompileCommand=dontinline,*String*::substring
 *                   -XX:CompileCommand=MemLimit,*.*,0
 *                   -XX:NodeCountInliningCutoff=220000
 *                   -XX:DesiredMethodLimit=100000
 *                   compiler.c2.TestScalarReplacementMaxLiveNodes
 */
package compiler.c2;

public class TestScalarReplacementMaxLiveNodes {
    public static void main(String[] args) {
        TestScalarReplacementMaxLiveNodes obj = new TestScalarReplacementMaxLiveNodes();

        int result = 0;
        for (int i = 0; i < 100; i++) {
            for (int val = 0; val < 50; val++) {
                result += obj.test(val, val+10, val+20, val+30, val+40).length();
            }
        }

        System.out.println("Result is: " + result);
    }

    public String test(int param1, int param2, int param3, int param4, int param5) {
        String a = null, b = null, c = null, d = null, e = null;

        if (param1 == 0) {
            a = new String("first" + param1);
            param1 = (int) Math.cos(param1);
        } else if (param1 == 1) {
            a = new String("second" + param2);
            param1 = (int) Math.cos(param1);
        } else if (param1 == 2) {
            a = "";
            param1 = (int) Math.cos(param1);
        } else if (param1 == 3) {
            b = new String("third" + param2);
            param2 = (int) Math.sin(param2);
        } else if (param1 == 4) {
            b = new String("fourth" + param1);
            param2 = (int) Math.sin(param2);
        } else if (param1 == 5) {
            b = new String("fifth" + param2);
            param2 = (int) Math.sin(param2);
        }  else if (param1 == 6) {
            c = new String("sixth" + param2);
            param2 = (int) Math.sin(param2);
        } else if (param1 == 7) {
            c = new String("seventh" + param2);
            param3 = (int) Math.tan(param3);
        } else if (param1 == 8) {
            c = new String("eighth" + param3);
            param3 = (int) Math.tan(param3);
        } else if (param1 == 9) {
            d = new String("nineth" + param2);
            param3 = (int) Math.tan(param3);
        } else if (param1 == 10) {
            d = "";
            param3 = (int) Math.tan(param3);
        } else if (param1 == 11) {
            d = new String("tenth" + param2);
            param4 = (int) Math.atan(param1 + param2);
        } else if (param1 == 12) {
            e = new String("eleventh" + param3);
            param4 = (int) Math.atan(param1 + param2);
        } else if (param1 == 13) {
            e = "";
            param4 = (int) Math.atan(param1 + param2);
        } else if (param1 == 14) {
            e = new String("twelveth" + param1);
            b = e;
        } else if (param1 == 15) {
            a = new String("thirteenth" + param2);
            param4 = (int) Math.atan(param1 + param2);
        }  else if (param1 == 16) {
            b = "";
            param5 = (int) Math.abs(Math.sqrt(param1*param2));
        } else if (param1 == 17) {
            c = new String("fourteenth" + param2);
            param5 = (int) Math.abs(Math.sqrt(param1*param2));
        } else if (param1 == 18) {
            e = new String("fifteenth" + param3);
            param5 = (int) Math.abs(Math.sqrt(param1*param2));
        } else if (param1 == 19) {
            e = new String("sixteenth" + param2);
            a = e;
        } else if (param1 == 20) {
            a = new String("seventeenth" + param2);
            param1 = param2 + param3;
        } else if (param1 == 21) {
            b = new String("eighteenth" + param2);
            c = b;
        } else if (param1 == 22) {
            c = "";
            param3 = param4 + param5;
        } else if (param1 == 23) {
            e = new String("nineteenth" + param3);
            param4 = param5 + param1;
        } else if (param1 == 24) {
            e = new String("tweenth" + param2);
        } else if (param1 == 25) {
            c = "";
            param3 = param4 + param5;
        }

        int val1 = (a != null ?  a.length() :  10) + param1;
        int val2 = (b != null ?  b.length() :  20) + param2;
        int val3 = (c != null ?  c.length() :  20) + param3;
        int val4 = (d != null ?  d.length() :  20) + param4;
        int val5 = (e != null ?  e.length() :  20) + param5;

        String result = "Really Really Long String";
        for (int i=0; i<16; i++) {
            for (int j=0; j<4; j++) {
                val1 += (int) (val1 >= 10 ? Math.sqrt(val1*val2*val3*val4 / 10) : i * j * Math.sin(Math.cos(val1)));
                val2 += (int) (val2 >= 10 ? Math.sqrt(val1*val2*val3*val4 / 20) : i * j * Math.sin(Math.cos(val2)));
                val3 += (int) (val3 >= 10 ? Math.sqrt(val1*val2*val3*val4 / 30) : i * j * Math.sin(Math.cos(val3)));
                val4 += (int) (val4 >= 10 ? Math.sqrt(val1*val2*val3*val4 / 40) : i * j * Math.sin(Math.cos(val4)));
                val5 += (int) (val5 >= 10 ? Math.sqrt(val1*val2*val3*val4 / 50) : i * j * Math.sin(Math.cos(val5)));
                int val6 = (int) (val1 + val2 + val3 + val4 + val5);

                try {
                    result += "Result is: " + result.substring(10, 20) + val1;
                } catch (IndexOutOfBoundsException except) {
                    result += "Reaally Long String" + val6;
                }
            }
        }

        return result;
    }
}
