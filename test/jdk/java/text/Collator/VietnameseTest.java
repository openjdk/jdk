/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4932968 5015215
 * @library /java/text/testlib
 * @summary test Vietnamese Collation
 * @modules jdk.localedata
 * @run junit VietnameseTest
 */

/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2003 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

import java.util.Locale;
import java.text.Collator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

// Quick dummy program for printing out test results
public class VietnameseTest {

    private final static String testPS[] = {
        "a",
        "a",
        "Â",
        "Cz",
        "d",
        "e",
        "e",
        "ệ",
        "gz",
        "i",
        "kz",
        "nz",
        "nh",
        "o",
        "o",
        "Ơ",
        "pz",
        "tz",
        "tr",
        "u",
        "u",
        "y"
    };

    private final static String testPT[] = {
        "à",
        "Ă",
        "Ă",
        "Ch",
        "Đ",
        "đ",
        "ẹ",
        "ẹ",
        "gi",
        "Ĩ",
        "kh",
        "ng",
        "ng",
        "ò",
        "ô",
        "ô",
        "ph",
        "th",
        "th",
        "ụ",
        "ư",
        "Ỵ"
    };

    private final static int testPR[] = {
        0,
        -1,
        1,
        1,
        -1,
        1,
        0,
        1,
        1,
        0,
        1,
        1,
        1,
        0,
        -1,
        1,
        1,
        1,
        1,
        0,
        -1,
        0
    };

    private final static String testT[] = {
        "a",
        "A",
        "à",
        "À",
        "ả",
        "Ả",
        "ã",
        "Ã",
        "á",
        "Á",
        "ạ",
        "Ạ",
        "ă",
        "Ă",
        "ằ",
        "Ằ",
        "ẳ",
        "Ẳ",
        "ẵ",
        "Ẵ",
        "ắ",
        "Ắ",
        "ặ",
        "Ặ",
        "â",
        "Â",
        "ầ",
        "Ầ",
        "ẩ",
        "Ẩ",
        "ẫ",
        "Ẫ",
        "ấ",
        "Ấ",
        "ậ",
        "Ậ",
        "b",
        "B",
        "c",
        "C",
        "ch",
        "Ch",
        "CH",
        "d",
        "D",
        "đ",
        "Đ",
        "e",
        "E",
        "è",
        "È",
        "ẻ",
        "Ẻ",
        "ẽ",
        "Ẽ",
        "é",
        "É",
        "ẹ",
        "Ẹ",
        "ê",
        "Ê",
        "ề",
        "Ề",
        "ể",
        "Ể",
        "ễ",
        "Ễ",
        "ế",
        "Ế",
        "ệ",
        "Ệ",
        "f",
        "F",
        "g",
        "G",
        "gi",
        "Gi",
        "GI",
        "gz",
        "h",
        "H",
        "i",
        "I",
        "ì",
        "Ì",
        "ỉ",
        "Ỉ",
        "ĩ",
        "Ĩ",
        "í",
        "Í",
        "ị",
        "Ị",
        "j",
        "J",
        "k",
        "K",
        "kh",
        "Kh",
        "KH",
        "kz",
        "l",
        "L",
        "m",
        "M",
        "n",
        "N",
        "ng",
        "Ng",
        "NG",
        "ngz",
        "nh",
        "Nh",
        "NH",
        "nz",
        "o",
        "O",
        "ò",
        "Ò",
        "ỏ",
        "Ỏ",
        "õ",
        "Õ",
        "ó",
        "Ó",
        "ọ",
        "Ọ",
        "ô",
        "Ô",
        "ồ",
        "Ồ",
        "ổ",
        "Ổ",
        "ỗ",
        "Ỗ",
        "ố",
        "Ố",
        "ộ",
        "Ộ",
        "ơ",
        "Ơ",
        "ờ",
        "Ờ",
        "ở",
        "Ở",
        "ỡ",
        "Ỡ",
        "ớ",
        "Ớ",
        "ợ",
        "Ợ",
        "p",
        "P",
        "ph",
        "Ph",
        "PH",
        "pz",
        "q",
        "Q",
        "r",
        "R",
        "s",
        "S",
        "t",
        "T",
        "th",
        "Th",
        "TH",
        "thz",
        "tr",
        "Tr",
        "TR",
        "tz",
        "u",
        "U",
        "ù",
        "Ù",
        "ủ",
        "Ủ",
        "ũ",
        "Ũ",
        "ú",
        "Ú",
        "ụ",
        "Ụ",
        "ư",
        "Ư",
        "ừ",
        "Ừ",
        "ử",
        "Ử",
        "ữ",
        "Ữ",
        "ứ",
        "Ứ",
        "ự",
        "Ự",
        "v",
        "V",
        "w",
        "W",
        "x",
        "X",
        "y",
        "Y",
        "ỳ",
        "Ỳ",
        "ỷ",
        "Ỷ",
        "ỹ",
        "Ỹ",
        "ý",
        "Ý",
        "ỵ",
        "Ỵ",
        "z",
        "Z"
    };

    @Test
    public void TestPrimary() {
        TestUtils.doCollatorTest(myCollation, Collator.PRIMARY, testPS, testPT, testPR);
    }

    @Test
    public void TestTertiary() {
        int testLength = testT.length;

        myCollation.setStrength(Collator.TERTIARY);
        for (int i = 0; i < testLength - 1; i++) {
            for (int j = i+1; j < testLength; j++) {
                TestUtils.doCollatorTest(myCollation, testT[i], testT[j], -1);
            }
        }
    }

    private final Collator myCollation = Collator.getInstance(Locale.of("vi", "VN"));
}
