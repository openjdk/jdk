/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8016846 8024341
 * @summary Unit tests for wrapping classes should delegate to default methods
 * @library ../stream/bootlib
 * @build java.util.stream.OpTestCase
 * @run testng/othervm PatternStreamTest
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.LambdaTestHelpers;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.TestData;

@Test
public class PatternStreamTest extends OpTestCase {

    @DataProvider(name = "Stream<String>")
    public static Object[][] makeStreamTestData() {
        List<Object[]> data = new ArrayList<>();

        String description = "";
        String input = "awgqwefg1fefw4vssv1vvv1";
        Pattern pattern = Pattern.compile("4");
        List<String> expected = new ArrayList<>();
        expected.add("awgqwefg1fefw");
        expected.add("vssv1vvv1");

        // Must match the type signature of the consumer of this data, testStrings
        // String, String, Pattern, List<String>
        data.add(new Object[]{description, input, pattern, expected});

        input = "afbfq\u00a3abgwgb\u00a3awngnwggw\u00a3a\u00a3ahjrnhneerh";
        pattern = Pattern.compile("\u00a3a");
        expected = new ArrayList<>();
        expected.add("afbfq");
        expected.add("bgwgb");
        expected.add("wngnwggw");
        expected.add("");
        expected.add("hjrnhneerh");

        data.add(new Object[]{description, input, pattern, expected});


        input = "awgqwefg1fefw4vssv1vvv1";
        pattern = Pattern.compile("1");
        expected = new ArrayList<>();
        expected.add("awgqwefg");
        expected.add("fefw4vssv");
        expected.add("vvv");

        data.add(new Object[]{description, input, pattern, expected});


        input = "a\u4ebafg1fefw\u4eba4\u9f9cvssv\u9f9c1v\u672c\u672cvv";
        pattern = Pattern.compile("1");
        expected = new ArrayList<>();
        expected.add("a\u4ebafg");
        expected.add("fefw\u4eba4\u9f9cvssv\u9f9c");
        expected.add("v\u672c\u672cvv");

        data.add(new Object[]{description, input, pattern, expected});


        input = "1\u56da23\u56da456\u56da7890";
        pattern = Pattern.compile("\u56da");
        expected = new ArrayList<>();
        expected.add("1");
        expected.add("23");
        expected.add("456");
        expected.add("7890");

        data.add(new Object[]{description, input, pattern, expected});


        input = "1\u56da23\u9f9c\u672c\u672c\u56da456\u56da\u9f9c\u672c7890";
        pattern = Pattern.compile("\u56da");
        expected = new ArrayList<>();
        expected.add("1");
        expected.add("23\u9f9c\u672c\u672c");
        expected.add("456");
        expected.add("\u9f9c\u672c7890");

        data.add(new Object[]{description, input, pattern, expected});


        input = "";
        pattern = Pattern.compile("\u56da");
        expected = new ArrayList<>();

        data.add(new Object[]{description, input, pattern, expected});


        description = "Multiple separators";
        input = "This is,testing: with\tdifferent separators.";
        pattern = Pattern.compile("[ \t,:.]");
        expected = new ArrayList<>();
        expected.add("This");
        expected.add("is");
        expected.add("testing");
        expected.add("");
        expected.add("with");
        expected.add("different");
        expected.add("separators");


        description = "Repeated separators within and at end";
        input = "boo:and:foo";
        pattern = Pattern.compile("o");
        expected = new ArrayList<>();
        expected.add("b");
        expected.add("");
        expected.add(":and:f");


        description = "Many repeated separators within and at end";
        input = "booooo:and:fooooo";
        pattern = Pattern.compile("o");
        expected = new ArrayList<>();
        expected.add("b");
        expected.add("");
        expected.add("");
        expected.add("");
        expected.add("");
        expected.add(":and:f");

        description = "Many repeated separators before last match";
        input = "fooooo:";
        pattern = Pattern.compile("o");
        expected = new ArrayList<>();
        expected.add("f");
        expected.add("");
        expected.add("");
        expected.add("");
        expected.add("");
        expected.add(":");

        data.add(new Object[] {description, input, pattern, expected});
        return data.toArray(new Object[0][]);
    }

    @Test(dataProvider = "Stream<String>")
    public void testStrings(String description, String input, Pattern pattern, List<String> expected) {
        Supplier<Stream<String>> ss =  () -> pattern.splitAsStream(input);
        withData(TestData.Factory.ofSupplier(description, ss))
                .stream(LambdaTestHelpers.identity())
                .expectedResult(expected)
                .exercise();
    }
}
