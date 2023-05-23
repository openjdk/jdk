/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test 8297530
 * @summary This sanity tests that OOME is correctly thrown when
 *          the length of the array to be allocated for a concatenation.
 *          Before the fix for 8297530 an IllegalArgumentException could
 *          erroneously be thrown when the length of the string is between
 *          Integer.MAX_VALUE / 2 and Integer.MAX_VALUE and the String coder
 *          is UTF16.
 *
 * @requires sun.arch.data.model == "64"
 * @run main/othervm -Xverify:all -Xmx4g ImplicitStringConcatOOME
 * @run main/othervm -Xverify:all -Xmx4g -XX:-CompactStrings ImplicitStringConcatOOME
 */

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ImplicitStringConcatOOME {

    static String s000, s001, s002, s003, s004, s005, s006, s007, s008, s009;
    static String s010, s011, s012, s013, s014, s015, s016, s017, s018, s019;
    static String s020, s021, s022, s023, s024, s025, s026, s027, s028, s029;
    static String s030, s031, s032, s033, s034, s035, s036, s037, s038, s039;
    static String s040, s041, s042, s043, s044, s045, s046, s047, s048, s049;
    static String s050, s051, s052, s053, s054, s055, s056, s057, s058, s059;
    static String s060, s061, s062, s063, s064, s065, s066, s067, s068, s069;
    static String s070, s071, s072, s073, s074, s075, s076, s077, s078, s079;
    static String s080, s081, s082, s083, s084, s085, s086, s087, s088, s089;
    static String s090, s091, s092, s093, s094, s095, s096, s097, s098, s099;

    static String s100, s101, s102, s103, s104, s105, s106, s107, s108, s109;
    static String s110, s111, s112, s113, s114, s115, s116, s117, s118, s119;
    static String s120, s121, s122, s123, s124, s125, s126, s127, s128, s129;
    static String s130, s131, s132, s133, s134, s135, s136, s137, s138, s139;
    static String s140, s141, s142, s143, s144, s145, s146, s147, s148, s149;
    static String s150, s151, s152, s153, s154, s155, s156, s157, s158, s159;
    static String s160, s161, s162, s163, s164, s165, s166, s167, s168, s169;
    static String s170, s171, s172, s173, s174, s175, s176, s177, s178, s179;
    static String s180, s181, s182, s183, s184, s185, s186, s187, s188, s189;
    static String s190, s191, s192, s193, s194, s195, s196, s197, s198, s199;

    static String s_utf16;
    static {
        String s = "10 letters".repeat(1_073_742);
        for (Field f : ImplicitStringConcatOOME.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                try {
                    f.set(null, s);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        s_utf16 = "\u0257";
    }

    public static void main(String[] args) throws Exception {
        try {
            String res =
                    s000 + s001 + s002 + s003 + s004 + s005 + s006 + s007 + s008 + s009 +
                    s010 + s011 + s012 + s013 + s014 + s015 + s016 + s017 + s018 + s019 +
                    s020 + s021 + s022 + s023 + s024 + s025 + s026 + s027 + s028 + s029 +
                    s030 + s031 + s032 + s033 + s034 + s035 + s036 + s037 + s038 + s039 +
                    s040 + s041 + s042 + s043 + s044 + s045 + s046 + s047 + s048 + s049 +
                    s050 + s051 + s052 + s053 + s054 + s055 + s056 + s057 + s058 + s059 +
                    s060 + s061 + s062 + s063 + s064 + s065 + s066 + s067 + s068 + s069 +
                    s070 + s071 + s072 + s073 + s074 + s075 + s076 + s077 + s078 + s079 +
                    s080 + s081 + s082 + s083 + s084 + s085 + s086 + s087 + s088 + s089 +
                    s090 + s091 + s092 + s093 + s094 + s095 + s096 + s097 + s098 + s099 +

                    s100 + s101 + s102 + s103 + s104 + s105 + s106 + s107 + s108 + s109 +
                    s110 + s111 + s112 + s113 + s114 + s115 + s116 + s117 + s118 + s119 +
                    s120 + s121 + s122 + s123 + s124 + s125 + s126 + s127 + s128 + s129 +
                    s130 + s131 + s132 + s133 + s134 + s135 + s136 + s137 + s138 + s139 +
                    s140 + s141 + s142 + s143 + s144 + s145 + s146 + s147 + s148 + s149 +
                    s150 + s151 + s152 + s153 + s154 + s155 + s156 + s157 + s158 + s159 +
                    s160 + s161 + s162 + s163 + s164 + s165 + s166 + s167 + s168 + s169 +
                    s170 + s171 + s172 + s173 + s174 + s175 + s176 + s177 + s178 + s179 +
                    s180 + s181 + s182 + s183 + s184 + s185 + s186 + s187 + s188 + s189 +
                    s190 + s191 + s192 + s193 + s194 + s195 + s196 + s197 + s198 + s199;
            throw new IllegalStateException("Expected OOME");
        } catch (OutOfMemoryError e) {
            // Expected
        }
        try {
            // Compact Strings meant capacity for UTF16 strings were cut in
            // half, regardless of -XX:+CompactStrings setting
            String res =
                    s000 + s001 + s002 + s003 + s004 + s005 + s006 + s007 + s008 + s009 +
                    s010 + s011 + s012 + s013 + s014 + s015 + s016 + s017 + s018 + s019 +
                    s020 + s021 + s022 + s023 + s024 + s025 + s026 + s027 + s028 + s029 +
                    s030 + s031 + s032 + s033 + s034 + s035 + s036 + s037 + s038 + s039 +
                    s040 + s041 + s042 + s043 + s044 + s045 + s046 + s047 + s048 + s049 +
                    s050 + s051 + s052 + s053 + s054 + s055 + s056 + s057 + s058 + s059 +
                    s060 + s061 + s062 + s063 + s064 + s065 + s066 + s067 + s068 + s069 +
                    s070 + s071 + s072 + s073 + s074 + s075 + s076 + s077 + s078 + s079 +
                    s080 + s081 + s082 + s083 + s084 + s085 + s086 + s087 + s088 + s089 +
                    s090 + s091 + s092 + s093 + s094 + s095 + s096 + s097 + s098 + s099 +
                    s_utf16;
            throw new IllegalStateException("Expected OOME");
        } catch (OutOfMemoryError e) {
            // Expected
        }
    }

    public static void test(String expected, String actual) {
       // Fingers crossed: String concat should work.
       if (!expected.equals(actual)) {
          throw new IllegalStateException("Expected = " + expected + ", actual = " + actual);
       }
    }
}

