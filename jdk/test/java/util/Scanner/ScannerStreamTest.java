/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.LambdaTestHelpers;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.TestData;

import static org.testng.Assert.*;

/**
 * @test
 * @bug 8072722
 * @summary Tests of stream support in java.util.Scanner
 * @library ../stream/bootlib/java.base
 * @build java.util.stream.OpTestCase
 * @run testng/othervm ScannerStreamTest
 */

@Test
public class ScannerStreamTest extends OpTestCase {

    static File inputFile = new File(System.getProperty("test.src", "."), "input.txt");

    @DataProvider(name = "Patterns")
    public static Object[][] makeStreamTestData() {
        // each inner array is [String description, String input, String delimiter]
        // delimiter may be null
        List<Object[]> data = new ArrayList<>();

        data.add(new Object[] { "default delimiter", "abc def ghi",           null });
        data.add(new Object[] { "fixed delimiter",   "abc,def,,ghi",          "," });
        data.add(new Object[] { "regexp delimiter",  "###abc##def###ghi###j", "#+" });

        return data.toArray(new Object[0][]);
    }

    Scanner makeScanner(String input, String delimiter) {
        Scanner sc = new Scanner(input);
        if (delimiter != null) {
            sc.useDelimiter(delimiter);
        }
        return sc;
    }

    @Test(dataProvider = "Patterns")
    public void tokensTest(String description, String input, String delimiter) {
        // derive expected result by using conventional loop
        Scanner sc = makeScanner(input, delimiter);
        List<String> expected = new ArrayList<>();
        while (sc.hasNext()) {
            expected.add(sc.next());
        }

        Supplier<Stream<String>> ss = () -> makeScanner(input, delimiter).tokens();
        withData(TestData.Factory.ofSupplier(description, ss))
                .stream(LambdaTestHelpers.identity())
                .expectedResult(expected)
                .exercise();
    }

    Scanner makeFileScanner(File file) {
        try {
            return new Scanner(file, "UTF-8");
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public void findAllTest() {
        // derive expected result by using conventional loop
        Pattern pat = Pattern.compile("[A-Z]{7,}");
        List<String> expected = new ArrayList<>();

        try (Scanner sc = makeFileScanner(inputFile)) {
            String match;
            while ((match = sc.findWithinHorizon(pat, 0)) != null) {
                expected.add(match);
            }
        }

        Supplier<Stream<String>> ss =
            () -> makeFileScanner(inputFile).findAll(pat).map(MatchResult::group);

        withData(TestData.Factory.ofSupplier("findAllTest", ss))
                .stream(LambdaTestHelpers.identity())
                .expectedResult(expected)
                .exercise();
    }

}
