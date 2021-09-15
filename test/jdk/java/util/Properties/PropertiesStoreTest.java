/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/*
 * @test
 * @summary tests the order in which the Properties.store() method writes out the properties
 * @bug 8231640
 * @run testng PropertiesStoreTest
 */
public class PropertiesStoreTest {

    private static final String DATE_FORMAT_PATTERN = "EEE MMM dd HH:mm:ss zzz uuuu";

    @DataProvider(name = "propsProvider")
    private Object[][] createProps() {
        final Properties simple = new Properties();
        simple.setProperty("1", "one");
        simple.setProperty("2", "two");
        simple.setProperty("10", "ten");
        simple.setProperty("02", "zero-two");
        simple.setProperty("3", "three");
        simple.setProperty("0", "zero");
        simple.setProperty("00", "zero-zero");
        simple.setProperty("0", "zero-again");

        final Properties specialChars = new Properties();
        // some special chars
        simple.setProperty(" 1", "space-one");
        simple.setProperty("\t 3 7 \n", "tab-space-three-space-seven-space-newline");
        // add some simple chars
        simple.setProperty("3", "three");
        simple.setProperty("0", "zero");

        final Properties overrideCallsSuper = new OverridesEntrySetCallsSuper();
        overrideCallsSuper.putAll(simple);

        final OverridesEntrySet overridesEntrySet = new OverridesEntrySet();
        overridesEntrySet.putAll(simple);

        final Properties doesNotOverrideEntrySet = new DoesNotOverrideEntrySet();
        doesNotOverrideEntrySet.putAll(simple);

        return new Object[][]{
                {simple, naturalOrder(simple)},
                {specialChars, naturalOrder(specialChars)},
                {overrideCallsSuper, naturalOrder(overrideCallsSuper)},
                {overridesEntrySet, overridesEntrySet.expectedKeyOrder()},
                {doesNotOverrideEntrySet, naturalOrder(doesNotOverrideEntrySet)}
        };
    }

    /**
     * Tests that the {@link Properties#store(Writer, String)} API writes out the properties
     * in the expected order
     */
    @Test(dataProvider = "propsProvider")
    public void testStoreWriterKeyOrder(final Properties props, final String[] expectedOrder) throws Exception {
        // Properties.store(...) to a temp file
        final Path tmpFile = Files.createTempFile("8231640", "props");
        try (final Writer writer = Files.newBufferedWriter(tmpFile)) {
            props.store(writer, null);
        }
        testStoreKeyOrder(props, tmpFile, expectedOrder);
    }

    /**
     * Tests that the {@link Properties#store(OutputStream, String)} API writes out the properties
     * in the expected order
     */
    @Test(dataProvider = "propsProvider")
    public void testStoreOutputStreamKeyOrder(final Properties props, final String[] expectedOrder) throws Exception {
        // Properties.store(...) to a temp file
        final Path tmpFile = Files.createTempFile("8231640", "props");
        try (final OutputStream os = Files.newOutputStream(tmpFile)) {
            props.store(os, null);
        }
        testStoreKeyOrder(props, tmpFile, expectedOrder);
    }

    /**
     * {@link Properties#load(InputStream) Loads a Properties instance} from the passed
     * {@code Path} and then verifies that:
     * - the loaded properties instance "equals" the passed (original) "props" instance
     * - the order in which the properties appear in the file represented by the path
     * is the same as the passed "expectedOrder"
     */
    private void testStoreKeyOrder(final Properties props, final Path storedProps,
                                   final String[] expectedOrder) throws Exception {
        // Properties.load(...) from that stored file and verify that the loaded
        // Properties has expected content
        final Properties loaded = new Properties();
        try (final InputStream is = Files.newInputStream(storedProps)) {
            loaded.load(is);
        }
        Assert.assertEquals(loaded, props, "Unexpected properties loaded from stored state");

        // now read lines from the stored file and keep track of the order in which the keys were
        // found in that file. Compare that order with the expected store order of the keys.
        final List<String> actualOrder;
        try (final BufferedReader reader = Files.newBufferedReader(storedProps)) {
            actualOrder = readInOrder(reader);
        }
        Assert.assertEquals(actualOrder.size(), expectedOrder.length,
                "Unexpected number of keys read from stored properties");
        if (!Arrays.equals(actualOrder.toArray(new String[0]), expectedOrder)) {
            Assert.fail("Unexpected order of stored property keys. Expected order: " + Arrays.toString(expectedOrder)
                    + ", found order: " + actualOrder);
        }
    }

    /**
     * Tests that {@link Properties#store(Writer, String)} writes out a proper date comment
     */
    @Test
    public void testStoreWriterDateComment() throws Exception {
        final Properties props = new Properties();
        props.setProperty("a", "b");
        final Path tmpFile = Files.createTempFile("8231640", "props");
        try (final Writer writer = Files.newBufferedWriter(tmpFile)) {
            props.store(writer, null);
        }
        testDateComment(tmpFile);
    }

    /**
     * Tests that {@link Properties#store(OutputStream, String)} writes out a proper date comment
     */
    @Test
    public void testStoreOutputStreamDateComment() throws Exception {
        final Properties props = new Properties();
        props.setProperty("a", "b");
        final Path tmpFile = Files.createTempFile("8231640", "props");
        try (final Writer writer = Files.newBufferedWriter(tmpFile)) {
            props.store(writer, null);
        }
        testDateComment(tmpFile);
    }

    /**
     * Reads each line in the {@code file} and verifies that there is only one comment line
     * and that comment line can be parsed into a {@link java.util.Date}
     */
    private void testDateComment(Path file) throws Exception {
        String comment = null;
        try (final BufferedReader reader = Files.newBufferedReader(file)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    if (comment != null) {
                        Assert.fail("More than one comment line found in the stored properties file " + file);
                    }
                    comment = line.substring(1);
                }
            }
        }
        if (comment == null) {
            Assert.fail("No comment line found in the stored properties file " + file);
        }
        try {
            DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN).parse(comment);
        } catch (DateTimeParseException pe) {
            Assert.fail("Unexpected date comment: " + comment, pe);
        }
    }

    // returns the property keys in their natural order
    private static String[] naturalOrder(final Properties props) {
        return new TreeSet<>(props.stringPropertyNames()).toArray(new String[0]);
    }

    // reads each non-comment line and keeps track of the order in which the property key lines
    // were read
    private static List<String> readInOrder(final BufferedReader reader) throws IOException {
        final List<String> readKeys = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            final String key = line.substring(0, line.indexOf("="));
            // the Properties.store(...) APIs write out the keys in a specific format for certain
            // special characters. Our test uses some of the keys which have those special characters.
            // Here we handle such special character conversion (for only those characters that this test uses).
            // replace the backslash character followed by the t character with the tab character
            String replacedKey = key.replace("\\t", "\t");
            // replace the backslash character followed by the n character with the newline character
            replacedKey = replacedKey.replace("\\n", "\n");
            // replace backslash character followed by the space character with the space character
            replacedKey = replacedKey.replace("\\ ", " ");
            readKeys.add(replacedKey);
        }
        return readKeys;
    }

    // Extends java.util.Properties and overrides entrySet() to return a reverse
    // sorted entries set
    private static class OverridesEntrySet extends Properties {
        @Override
        @SuppressWarnings("unchecked")
        public Set<Map.Entry<Object, Object>> entrySet() {
            // return a reverse sorted entries set
            var entries = super.entrySet();
            Comparator<Map.Entry<String, String>> comparator = Map.Entry.comparingByKey(Comparator.reverseOrder());
            TreeSet<Map.Entry<String, String>> reverseSorted = new TreeSet<>(comparator);
            reverseSorted.addAll((Set) entries);
            return (Set) reverseSorted;
        }

        String[] expectedKeyOrder() {
            // returns in reverse order of the property keys' natural ordering
            var keys = new ArrayList<>(stringPropertyNames());
            keys.sort(Comparator.reverseOrder());
            return keys.toArray(new String[0]);
        }
    }

    // Extends java.util.Properties and overrides entrySet() to just return "super.entrySet()"
    private static class OverridesEntrySetCallsSuper extends Properties {
        @Override
        public Set<Map.Entry<Object, Object>> entrySet() {
            return super.entrySet();
        }
    }

    // Extends java.util.Properties but doesn't override entrySet() method
    private static class DoesNotOverrideEntrySet extends Properties {

        @Override
        public String toString() {
            return "DoesNotOverrideEntrySet - " + super.toString();
        }
    }
}
