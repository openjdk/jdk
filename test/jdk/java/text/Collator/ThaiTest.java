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
 * @library /java/text/testlib
 * @summary test Thai Collation
 * @modules jdk.localedata
 * @run junit ThaiTest
 */
/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is the proprietary information of Oracle.
 * Use is subject to license terms.
 *
 */

import java.util.Locale;
import java.text.Collator;
import java.text.RuleBasedCollator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class ThaiTest {

    /*
     * Data for TestPrimary()
     */
    private static final String[] primarySourceData = {
        "กก",
        "งโก้",
        "ฐิน",
        "ถามรรคเทศนา",
        "รมธรรม์ประกันภัย",
        "รรมสิทธิ์เครื่องหมายและยี่ห้อการค้าขาย",
        "รรลุนิติภาวะ",
        "กลือแกง",
        "มทิลแอลกอฮอล์",
        "เอี่ยมอ่อง",
        "สดาปัตติผล",
        "อนกรรมสิทธิ์",
        "ม้เท้ายายม่อม",
        "ส้ละมาน",
        "ส้ศึก",
        "ส้อั่ว",
        "ส้เดือน",
        "ส้เลื่อน",
        "ส้แขวน",
        "ส้แห้ง",
        "ส้ไก่",
        "ห",
        "หซอง",
        "หน",
        "หปลาร้า",
        "หม",
        "หมทอง",
        "หมสับปะรด",
        "หม้",
        "หรณย์",
        "หล",
        "หลน้ำ",
        "หล่",
        "หล่ถนน",
        "หล่ทวีป",
        "หล่ทาง",
        "หล่รวบ",
        "ห้",
        "อ",
        "อ้",
        "ฮโล",
        "ฮไฟ",
        "ฮ้"
    };

    private static final String[] primaryTargetData = {
        "กก",
        "งโก้",
        "ฐิน",
        "ถามรรคเทศนา",
        "รมธรรม์ประกันภัย",
        "รรมสิทธิ์เครื่องหมายและยี่ห้อการค้าขาย",
        "รรลุนิติภาวะ",
        "กลือแกง",
        "มทิลแอลกอฮอล์",
        "เอี่ยมอ่อง",
        "สดาปัตติผล",
        "อนกรรมสิทธิ์",
        "ม้เท้ายายม่อม",
        "ส้ละมาน",
        "ส้ศึก",
        "ส้อั่ว",
        "ส้เดือน",
        "ส้เลื่อน",
        "ส้แขวน",
        "ส้แห้ง",
        "ส้ไก่",
        "ห",
        "หซอง",
        "หน",
        "หปลาร้า",
        "หม",
        "หมทอง",
        "หมสับปะรด",
        "หม้",
        "หรณย์",
        "หล",
        "หลน้ำ",
        "หล่",
        "หล่ถนน",
        "หล่ทวีป",
        "หล่ทาง",
        "หล่รวบ",
        "ห้",
        "อ",
        "อ้",
        "ฮโล",
        "ฮไฟ",
        "ฮ้"
    };

    private static final int[] primaryResults = {
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
         0,  0,  0
    };

    @Test
    public void TestPrimary() {
        TestUtils.doCollatorTest(myCollation, Collator.PRIMARY,
               primarySourceData, primaryTargetData, primaryResults);
    }

    private final Collator myCollation = Collator.getInstance(Locale.of("th"));
}
