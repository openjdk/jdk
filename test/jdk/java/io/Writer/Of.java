/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Writer;
import java.io.StringWriter;
import java.io.IOException;
import java.util.function.Supplier;

import org.testng.annotations.*;

import static org.testng.Assert.*;

/*
 * @test
 * @bug 8353795
 * @summary Check for expected behavior of Writer.of().
 * @run testng Of
 */
public class Of {
    private static final String CONTENT = "Some Writer Test";

    private static record Config(String id, Writer writer, Supplier<String> spy) {};

    /*
     * Writers to be tested.
     */
    @DataProvider
    public static Config[] writers() {
        var sw = new StringWriter();
        var sbld = new StringBuilder();
        var w = new Writer() {
            private String s = "";
            private boolean isClosed;

            private void ensureOpen() throws IOException {
                if (isClosed)
                    throw new IOException("Stream closed");
            }

            @Override
            public Writer append(char c) throws IOException {
                ensureOpen();
                s += c;
                return this;
            }

            @Override
            public Writer append(CharSequence csq) throws IOException {
                ensureOpen();
                s += String.valueOf(csq);
                return this;
            }

            @Override
            public Writer append(CharSequence csq, int start, int end)
                    throws IOException {
                ensureOpen();
                s += String.valueOf(csq).subSequence(start, end);
                return this;
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                ensureOpen();
                s += new String(cbuf, off, len);
            }

            @Override
            public void flush() throws IOException {
                ensureOpen();
            }

            @Override
            public void close() throws IOException {
                isClosed = true;
            }

            @Override
            public String toString() {
                return s;
            }
        };
        return new Config[] {
            new Config("StringWriter", sw, sw::toString),
            new Config("StringBuilder", Writer.of(sbld), sbld::toString),
            new Config("Custom Writer", w, w::toString),
        };
    }

    @Test(dataProvider = "writers")
    public void testAppendChar(Config config) throws IOException {
        for (int i = 0; i < CONTENT.length(); i++)
            config.writer.append(CONTENT.charAt(i));
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testAppendCharSequence(Config config) throws IOException {
        config.writer.append((CharSequence) CONTENT);
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testAppendCompleteSubCharSequence(Config config) throws IOException {
        config.writer.append((CharSequence) CONTENT, 0, CONTENT.length());
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testAppendPartialSubCharSequence(Config config) throws IOException {
        config.writer.append((CharSequence) CONTENT, 1, CONTENT.length() - 1);
        assertEquals(config.spy.get(), CONTENT.substring(1, CONTENT.length() - 1));
    }

    @Test(dataProvider = "writers")
    public void testWriteCharArray(Config config) throws IOException {
        config.writer.write(CONTENT.toCharArray());
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testWriteCompleteSubCharArray(Config config) throws IOException {
        config.writer.write(CONTENT.toCharArray(), 0, CONTENT.length());
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testWritePartialSubCharArray(Config config) throws IOException {
        config.writer.write(CONTENT.toCharArray(), 1, CONTENT.length() - 2);
        assertEquals(config.spy.get(), CONTENT.substring(1, CONTENT.length() - 1));
    }

    @Test(dataProvider = "writers")
    public void testWriteChar(Config config) throws IOException {
        for (int i = 0; i < CONTENT.length(); i++)
            config.writer.write(CONTENT.charAt(i));
        assertEquals(config.spy.get(), CONTENT);
}

    @Test(dataProvider = "writers")
    public void testWriteString(Config config) throws IOException {
        config.writer.write(CONTENT);
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testWriteCompleteSubString(Config config) throws IOException {
        config.writer.write(CONTENT, 0, CONTENT.length());
        assertEquals(config.spy.get(), CONTENT);
    }

    @Test(dataProvider = "writers")
    public void testWritePartialSubString(Config config) throws IOException {
        config.writer.write(CONTENT, 1, CONTENT.length() - 2);
        assertEquals(config.spy.get(), CONTENT.substring(1, CONTENT.length() - 1));
    }

    @Test(dataProvider = "writers")
    public void testAppendCharClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testAppendChar(config);
        else
            assertThrows(IOException.class, () -> config.writer.append('x'));
    }

    @Test(dataProvider = "writers")
    public void testAppendCharSequenceClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testAppendCharSequence(config);
        else
            assertThrows(IOException.class, () ->
                    config.writer.append((CharSequence) CONTENT));
    }

    @Test(dataProvider = "writers")
    public void testAppendSubCharSequenceClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testAppendCompleteSubCharSequence(config);
        else
            assertThrows(IOException.class, () ->
                    config.writer.append((CharSequence) CONTENT, 0, CONTENT.length()));
    }

    @Test(dataProvider = "writers")
    public void testWriteCharArrayClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testWriteCharArray(config);
        else
            assertThrows(IOException.class, () ->
                    config.writer.write(CONTENT.toCharArray()));
    }

    @Test(dataProvider = "writers")
    public void testWriteSubCharArrayClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testWriteCompleteSubCharArray(config);
        else
            assertThrows(IOException.class, () ->
                    config.writer.write(CONTENT.toCharArray(), 0, CONTENT.length()));
    }

    @Test(dataProvider = "writers")
    public void testWriteCharClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testWriteChar(config);
        else
            assertThrows(IOException.class, () -> config.writer.write('x'));
    }

    @Test(dataProvider = "writers")
    public void testWriteStringClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testWriteString(config);
        else
            assertThrows(IOException.class, () -> config.writer.write(CONTENT));
    }

    @Test(dataProvider = "writers")
    public void testWriteSubStringClosed(Config config) throws IOException {
        config.writer.close();

        // StringWriter intentionally never throws exceptions
        if (config.writer instanceof StringWriter)
            testWriteCompleteSubString(config);
        else
            assertThrows(IOException.class, () ->
                    config.writer.write(CONTENT, 0, CONTENT.length()));
    }

    @Test(dataProvider = "writers")
    public void testClosedClosed(Config config) throws IOException {
        config.writer.close();
        config.writer.close();
    }
}
