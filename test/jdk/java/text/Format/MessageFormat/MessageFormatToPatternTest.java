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
 * @summary Verify that MessageFormat.toPattern() properly escapes special curly braces
 * @bug 8323699
 * @run junit MessageFormatToPatternTest
 */

import java.text.ChoiceFormat;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageFormatToPatternTest {

    private static final int NUM_RANDOM_TEST_CASES = 1000;

    // Max levels of nesting of ChoiceFormats inside MessageFormats
    private static final int MAX_FORMAT_NESTING = 3;

    private static Locale savedLocale;
    private static long randomSeed;             // set this to a non-zero value for reproducibility
    private static Random random;
    private static boolean spitSeed;
    private static int textCount;

// Setup & Teardown

    @BeforeAll
    public static void setup() {
        savedLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        if (randomSeed == 0)
            randomSeed = new Random().nextLong();
        random = new Random(randomSeed);
    }

    @AfterAll
    public static void teardown() {
        Locale.setDefault(savedLocale);
    }

// Tests

    // Test expected output when given a MessageFormat pattern string and value 1.23
    @ParameterizedTest
    @MethodSource("generateOutputTestCases")
    public void testOutput(String pattern, String expected) {

        // Test we get the expected output
        MessageFormat format = new MessageFormat(pattern);
        String actual = format.format(new Object[] { 1.23 });
        assertEquals(expected, actual);

        // Test round trip as well
        testRoundTrip(format);
    }

    public static Stream<Arguments> generateOutputTestCases() {
        return Stream.of(

            // This is the test case from JDK-8323699
            Arguments.of("{0,choice,0.0#option A: {0}|1.0#option B: {0}'}'}", "option B: 1.23}"),
            Arguments.of("{0,choice,0.0#option A: {0}|2.0#option B: {0}'}'}", "option A: 1.23"),

            // A few more test cases from the PR#17416 discussion
            Arguments.of("Test: {0,number,foo'{'#.00}", "Test: foo{1.23"),
            Arguments.of("Test: {0,number,foo'}'#.00}", "Test: foo}1.23"),
            Arguments.of("{0,number,' abc }'' ' 0.00}", " abc }'  1.23"),
            Arguments.of("Wayne ''The Great One'' Gretsky", "Wayne 'The Great One' Gretsky"),
            Arguments.of("'Wayne ''The Great One'' Gretsky'", "Wayne 'The Great One' Gretsky"),
            Arguments.of("{0,choice,0.0#'''{''curly''}'' braces'}", "{curly} braces"),
            Arguments.of("{0,choice,0.0#''{''curly''}'' braces}", "{curly} braces"),
            Arguments.of("{0,choice,0.0#'{0,choice,0.0#''{0,choice,0.0#''''{0,choice,0.0#foo}''''}''}'}", "foo"),

            // Absurd double quote examples
            Arguments.of("Foo '}''''''''}' {0,number,bar'}' '}' } baz ", "Foo }''''} bar} } 1 baz "),
            Arguments.of("'''}''{'''}''''}'", "'}'{'}''}"),

            // An absurdly complicated example
            Arguments.of("{0,choice,0.0#text2887 [] '{'1,date,YYYY-MM-DD'}' text2888 [''*'']|1.0#found|2.0#'text2901 [oog'')!''] {2,choice,0.0#''text2897 ['''']''''wq1Q] {2,choice,0.0#''''text2891 [s''''''''&''''''''] {0,number,#0.##} text2892 [8''''''''|$'''''''''''''''''''''''']''''|1.0#''''text2893 [] {0,number,#0.##} text2894 [S'''''''']'''''''']''''|2.0#text2895 [''''''''.''''''''eB] {1,date,YYYY-MM-DD} text2896 [9Y]} text2898 []''|1.0#''text2899 [xk7] {0,number,#0.##} text2900 []''} text2902 [7'':$)''O]'}{0,choice,0.0#'text2903 [] {0,number,#0.##} text2904 [S'':'']'|1.0#'me'}", "foundme")
        );
    }

    // Go roundrip from MessageFormat -> pattern string -> MessageFormat and verify equivalence
    @ParameterizedTest
    @MethodSource("generateRoundTripTestCases")
    public void testRoundTrip(MessageFormat format1) {

        // Prepare MessageFormat argument
        Object[] args = new Object[] {
            8.5,                            // argument for DecimalFormat
            new Date(1705502102677L),       // argument for SimpleDateFormat
            random.nextInt(6)               // argument for ChoiceFormat
        };

        String pattern1 = null;
        String result1 = null;
        String pattern2 = null;
        String result2 = null;
        try {

            // Format using the given MessageFormat
            pattern1 = format1.toPattern();
            result1 = format1.format(args);

            // Round-trip via toPattern() and repeat
            MessageFormat format2 = new MessageFormat(pattern1);
            pattern2 = format2.toPattern();
            result2 = format2.format(args);

            // Check equivalence
            assertEquals(result1, result2);
            assertEquals(pattern1, pattern2);

            // Debug
            //showRoundTrip(format1, pattern1, result1, pattern2, result2);
        } catch (RuntimeException | Error e) {
            System.out.println(String.format("%n********** FAILURE **********%n"));
            System.out.println(String.format("%s%n", e));
            if (!spitSeed) {
                System.out.println(String.format("*** Random seed was 0x%016xL%n", randomSeed));
                spitSeed = true;
            }
            showRoundTrip(format1, pattern1, result1, pattern2, result2);
            throw e;
        }
    }

    public static Stream<Arguments> generateRoundTripTestCases() {
        final ArrayList<Arguments> argList = new ArrayList<>();
        for (int i = 0; i < NUM_RANDOM_TEST_CASES; i++)
            argList.add(Arguments.of(randomFormat()));
        return argList.stream();
    }

    // Generate a "random" MessageFormat. We do this by creating a MessageFormat with "{0}" placeholders
    // and then substituting in random DecimalFormat, DateFormat, and ChoiceFormat subformats. The goal here
    // is to avoid using pattern strings to construct formats, because they're what we're trying to check.
    private static MessageFormat randomFormat() {

        // Create a temporary MessageFormat containing "{0}" placeholders and random text
        StringBuilder tempPattern = new StringBuilder();
        int numParts = random.nextInt(3) + 1;
        for (int i = 0; i < numParts; i++) {
            if (random.nextBoolean())
                tempPattern.append("{0}");      // temporary placeholder for a subformat
            else
                tempPattern.append(quoteText(randomText()));
        }

        // Replace all the "{0}" placeholders with random subformats
        MessageFormat format = new MessageFormat(tempPattern.toString());
        Format[] formats = format.getFormats();
        for (int i = 0; i < formats.length; i++)
            formats[i] = randomSubFormat(0);
        format.setFormats(formats);

        // Done
        return format;
    }

    // Generate some random text
    private static String randomText() {
        StringBuilder buf = new StringBuilder();
        int length = random.nextInt(6);
        for (int i = 0; i < length; i++) {
            char ch = (char)(0x20 + random.nextInt(0x5f));
            buf.append(ch);
        }
        return buf.toString();
    }

    // Quote non-alphanumeric characters in the given plain text
    private static String quoteText(String string) {
        StringBuilder buf = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            if (ch == '\'')
                buf.append("''");
            else if (!(ch == ' ' || Character.isLetter(ch) || Character.isDigit(ch))) {
                if (!quoted) {
                    buf.append('\'');
                    quoted = true;
                }
                buf.append(ch);
            } else {
                if (quoted) {
                    buf.append('\'');
                    quoted = false;
                }
                buf.append(ch);
            }
        }
        if (quoted)
            buf.append('\'');
        return buf.toString();
    }

    // Create a random subformat for a MessageFormat
    private static Format randomSubFormat(int nesting) {
        int which;
        if (nesting >= MAX_FORMAT_NESTING)
            which = random.nextInt(2);          // no more recursion
        else
            which = random.nextInt(3);
        switch (which) {
        case 0:
            return new DecimalFormat("#.##");
        case 1:
            return new SimpleDateFormat("YYYY-MM-DD");
        default:
            int numChoices = random.nextInt(3) + 1;
            assert numChoices > 0;
            final double[] limits = new double[numChoices];
            final String[] formats = new String[numChoices];
            for (int i = 0; i < limits.length; i++) {
                limits[i] = (double)i;
                formats[i] = randomMessageFormatContaining(randomSubFormat(nesting + 1));
            }
            return new ChoiceFormat(limits, formats);
        }
    }

    // Create a MessageFormat pattern string that includes the given Format as a subformat.
    // The result will be one option in a ChoiceFormat which is nested in an outer MessageFormat.
    // A ChoiceFormat option string is just a plain string; it's only when that plain string
    // bubbles up to a containing MessageFormat that it gets interpreted as a MessageFormat string,
    // and that only happens if the option string contains a '{' character. That will always
    // be the case for the strings returned by this method of course.
    private static String randomMessageFormatContaining(Format format) {
        String beforeText = quoteText(randomText().replaceAll("\\{", ""));     // avoid invalid MessageFormat syntax
        String afterText = quoteText(randomText().replaceAll("\\{", ""));      // avoid invalid MessageFormat syntax
        String middleText;
        if (format instanceof DecimalFormat dfmt)
            middleText = String.format("{0,number,%s}", dfmt.toPattern());
        else if (format instanceof SimpleDateFormat sdfmt)
            middleText = String.format("{1,date,%s}", sdfmt.toPattern());
        else if (format instanceof ChoiceFormat cfmt)
            middleText = String.format("{2,choice,%s}", cfmt.toPattern());
        else
            throw new RuntimeException("internal error");
        return String.format("text%d [%s] %s text%d [%s]", ++textCount, beforeText, middleText, ++textCount, afterText);
    }

// Debug printing

    private void showRoundTrip(MessageFormat format1, String pattern1, String result1, String pattern2, String result2) {
        print(0, format1);
        System.out.println();
        if (pattern1 != null)
            System.out.println(String.format("  pattern1 = %s", javaLiteral(pattern1)));
        if (result1 != null)
            System.out.println(String.format("   result1 = %s", javaLiteral(result1)));
        if (pattern2 != null)
            System.out.println(String.format("  pattern2 = %s", javaLiteral(pattern2)));
        if (result2 != null)
            System.out.println(String.format("   result2 = %s", javaLiteral(result2)));
        System.out.println();
    }

    private static void print(int depth, Object format) {
        if (format == null)
            return;
        if (format instanceof String)
            System.out.println(String.format("%s- %s", indent(depth), javaLiteral((String)format)));
        else if (format instanceof MessageFormat)
            print(depth, (MessageFormat)format);
        else if (format instanceof DecimalFormat)
            print(depth, (DecimalFormat)format);
        else if (format instanceof SimpleDateFormat)
            print(depth, (SimpleDateFormat)format);
        else if (format instanceof ChoiceFormat)
            print(depth, (ChoiceFormat)format);
        else
            throw new RuntimeException("internal error: " + format.getClass());
    }

    private static void print(int depth, MessageFormat format) {
        System.out.println(String.format("%s- %s: %s", indent(depth), "MessageFormat", javaLiteral(format.toPattern())));
        for (Format subformat : format.getFormats())
            print(depth + 1, subformat);
    }

    private static void print(int depth, DecimalFormat format) {
        System.out.println(String.format("%s- %s: %s", indent(depth), "DecimalFormat", javaLiteral(format.toPattern())));
    }

    private static void print(int depth, SimpleDateFormat format) {
        System.out.println(String.format("%s- %s: %s", indent(depth), "SimpleDateFormat", javaLiteral(format.toPattern())));
    }

    private static void print(int depth, ChoiceFormat format) {
        System.out.println(String.format("%s- %s: %s", indent(depth), "ChoiceFormat", javaLiteral(format.toPattern())));
        for (Object subformat : format.getFormats())
            print(depth + 1, subformat);
    }

    private static String indent(int depth) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < depth; i++)
            buf.append("    ");
        return buf.toString();
    }

    // Print a Java string in double quotes so it looks like a String literal (for easy pasting into jshell)
    private static String javaLiteral(String string) {
        StringBuilder buf = new StringBuilder();
        buf.append('"');
        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            switch (ch) {
            case '"':
            case '\\':
                buf.append('\\');
                // FALLTHROUGH
            default:
                buf.append(ch);
                break;
            }
        }
        return buf.append('"').toString();
    }
}
