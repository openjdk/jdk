/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.runtime.Source.sourceFor;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import jdk.nashorn.api.scripting.URLReader;
import org.testng.annotations.Test;

/**
 * Tests different Source representations.
 */
public class SourceTest {

    final private static String SOURCE_NAME = "source.js";
    final private static String SOURCE_STRING = "var x = 1;";
    final private static char[] SOURCE_CHARS = SOURCE_STRING.toCharArray();
    final private static String RESOURCE_PATH = "resources/load_test.js";
    final private static File SOURCE_FILE = new File("build/test/classes/jdk/nashorn/internal/runtime/" + RESOURCE_PATH);
    final private static URL  SOURCE_URL = SourceTest.class.getResource(RESOURCE_PATH);


    @Test
    public void testStringSource() {
        testSources(sourceFor(SOURCE_NAME, SOURCE_STRING), sourceFor(SOURCE_NAME, SOURCE_STRING));
        testSources(sourceFor(SOURCE_NAME, SOURCE_STRING), sourceFor(SOURCE_NAME, SOURCE_CHARS));
    }

    @Test
    public void testCharArraySource() {
        testSources(sourceFor(SOURCE_NAME, SOURCE_CHARS), sourceFor(SOURCE_NAME, SOURCE_CHARS));
        testSources(sourceFor(SOURCE_NAME, SOURCE_CHARS), sourceFor(SOURCE_NAME, SOURCE_STRING));
    }

    @Test
    public void testURLSource() {
        try {
            testSources(sourceFor(SOURCE_NAME, SOURCE_URL), sourceFor(SOURCE_NAME, SOURCE_URL));
            testSources(sourceFor(SOURCE_NAME, SOURCE_URL), sourceFor(SOURCE_NAME, new URLReader(SOURCE_URL)));

        } catch (final IOException e) {
            fail(e.toString());
        }
    }

    @Test
    public void testURLReaderSource() {
        try {
            System.err.println(SourceTest.class.getResource(""));
            testSources(sourceFor(SOURCE_NAME, new URLReader(SOURCE_URL)), sourceFor(SOURCE_NAME, new URLReader(SOURCE_URL)));
            testSources(sourceFor(SOURCE_NAME, new URLReader(SOURCE_URL)), sourceFor(SOURCE_NAME, SOURCE_URL));
        } catch (final IOException e) {
            fail(e.toString());
        }
    }

    @Test
    public void testReaderSource() {
        try {
            testSources(sourceFor(SOURCE_NAME, getReader(RESOURCE_PATH)), sourceFor(SOURCE_NAME, getReader(RESOURCE_PATH)));
        } catch (final IOException e) {
            fail(e.toString());
        }
    }

    @Test
    public void testFileSource() {
        try {
            testSources(sourceFor(SOURCE_NAME, SOURCE_FILE), sourceFor(SOURCE_NAME, SOURCE_FILE));
        } catch (final IOException e) {
            fail(e.toString());
        }
    }

    private Reader getReader(final String path) {
        return new InputStreamReader(SourceTest.class.getResourceAsStream(path));
    }

    private void testSources(final Source source1, final Source source2) {
        final char[] chars1 = source1.getContent();
        final char[] chars2 = source2.getContent();
        final String str1 = source1.getString();
        final String str2 = source2.getString();
        assertTrue(Arrays.equals(chars1, chars2));
        assertEquals(str1, str2);
        assertEquals(source1.hashCode(), source2.hashCode());
        assertTrue(source1.equals(source2));
        assertTrue(Arrays.equals(source1.getContent(), str1.toCharArray()));
        assertTrue(Arrays.equals(source1.getContent(), chars1));
        assertTrue(Arrays.equals(source1.getContent(), source2.getContent()));
    }
}
