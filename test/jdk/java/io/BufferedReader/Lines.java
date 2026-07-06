/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003258 8029434
 * @run junit Lines
 */

import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Lines {
    private static final Map<String, Integer> cases = new HashMap<>();

    static {
        cases.put("", 0);
        cases.put("Line 1", 1);
        cases.put("Line 1\n", 1);
        cases.put("Line 1\n\n\n", 3);
        cases.put("Line 1\nLine 2\nLine 3", 3);
        cases.put("Line 1\nLine 2\nLine 3\n", 3);
        cases.put("Line 1\n\nLine 3\n\nLine5", 5);
    }

    /**
     * Helper Reader class which generate specified number of lines contents
     * with each line will be "<code>Line &lt;line_number&gt;</code>".
     *
     * <p>This class also support to simulate {@link IOException} when read pass
     * a specified line number.
     */
    private static class MockLineReader extends Reader {
        final int line_count;
        boolean closed = false;
        int line_no = 0;
        String line = null;
        int pos = 0;
        int inject_ioe_after_line;

        MockLineReader(int cnt) {
            this(cnt, cnt);
        }

        MockLineReader(int cnt, int inject_ioe) {
            line_count = cnt;
            inject_ioe_after_line = inject_ioe;
        }

        public void reset() {
            synchronized(lock) {
                line = null;
                line_no = 0;
                pos = 0;
                closed = false;
            }
        }

        public void inject_ioe() {
            inject_ioe_after_line = line_no;
        }

        public int getLineNumber() {
            synchronized(lock) {
                return line_no;
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public int read(char[] buf, int off, int len) throws IOException {
            synchronized(lock) {
                if (closed) {
                    throw new IOException("Stream is closed.");
                }

                if (line == null) {
                    if (line_count > line_no) {
                        line_no += 1;
                        if (line_no > inject_ioe_after_line) {
                            throw new IOException("Failed to read line " + line_no);
                        }
                        line = "Line " + line_no + "\n";
                        pos = 0;
                    } else {
                        return -1; // EOS reached
                    }
                }

                int cnt = line.length() - pos;
                assert(cnt != 0);
                // try to fill with remaining
                if (cnt >= len) {
                    line.getChars(pos, pos + len, buf, off);
                    pos += len;
                    if (cnt == len) {
                        assert(pos == line.length());
                        line = null;
                    }
                    return len;
                } else {
                    line.getChars(pos, pos + cnt, buf, off);
                    off += cnt;
                    len -= cnt;
                    line = null;
                    /* hold for next read, so we won't IOE during fill buffer
                    int more = read(buf, off, len);
                    return (more == -1) ? cnt : cnt + more;
                    */
                    return cnt;
                }
            }
        }
    }

    private static void verify(Map.Entry<String, Integer> e) {
        final String data = e.getKey();
        final int total_lines = e.getValue();
        assertDoesNotThrow
            (() -> {
                try (BufferedReader br =
                     new BufferedReader(new StringReader(data))) {
                    assertEquals(total_lines,
                                 br.lines().mapToInt(l -> 1).reduce(0, (x, y) -> x + y),
                                 data + " should produce " + total_lines + " lines.");
                }
            });
    }

    @Test
    public void testLinesBasic() {
        // Basic test cases
        cases.entrySet().stream().forEach(Lines::verify);
        // Similar test, also verify MockLineReader is correct
        assertDoesNotThrow
            (() -> {
                for (int i = 0; i < 10; i++) {
                    try (BufferedReader br =
                         new BufferedReader(new MockLineReader(i))) {
                        assertEquals(i,
                                     br.lines()
                                     .peek(l -> assertTrue(l.matches("^Line \\d+$")))
                                     .mapToInt(l -> 1).reduce(0, (x, y) -> x + y),
                                     "MockLineReader(" + i + ") should produce " + i + " lines.");
                    }
                }
            });
    }

    @Test
    public void testUncheckedIOException() throws IOException {
        MockLineReader r = new MockLineReader(10, 3);
        ArrayList<String> ar = new ArrayList<>();
        assertDoesNotThrow
            (() -> {
                try (BufferedReader br = new BufferedReader(r)) {
                    br.lines().limit(3L).forEach(ar::add);
                    assertEquals(3, ar.size(), "Should be able to read 3 lines.");
                }
            });
        r.reset();
        assertThrows(UncheckedIOException.class,
                     () -> {
                         try (BufferedReader br = new BufferedReader(r)) {
                             br.lines().forEach(ar::add);
                         }
                     });
        assertEquals(4, r.getLineNumber(), "should fail to read 4th line");
        assertEquals(6, ar.size(), "3 + 3 lines read");
        for (int i = 0; i < ar.size(); i++) {
            assertEquals("Line " + (i % 3 + 1), ar.get(i));
        }
    }

    @Test
    public void testIterator() throws IOException {
        MockLineReader r = new MockLineReader(6);
        BufferedReader br = new BufferedReader(r);
        String line = br.readLine();
        assertEquals(1, r.getLineNumber(), "Read one line");
        Stream<String> s = br.lines();
        Iterator<String> it = s.iterator();
        // Ensure iterate with only next works
        for (int i = 0; i < 5; i++) {
            String str = it.next();
            assertEquals("Line " + (i + 2), str, "Addtional five lines");
        }
        // NoSuchElementException
        assertThrows(NoSuchElementException.class, () -> it.next(),
                     "Should have run out of lines.");
    }

    @Test
    public void testPartialReadAndLineNo() throws IOException {
        MockLineReader r = new MockLineReader(5);
        LineNumberReader lr = new LineNumberReader(r);
        char[] buf = new char[5];
        lr.read(buf, 0, 5);
        assertEquals(0, lr.getLineNumber(), "LineNumberReader start with line 0");
        assertEquals(1, r.getLineNumber(), "MockLineReader start with line 1");
        assertEquals("Line ", new String(buf));
        String l1 = lr.readLine();
        assertEquals("1", l1, "Remaining of the first line");
        assertEquals(1, lr.getLineNumber(), "Line 1 is read");
        assertEquals(1, r.getLineNumber(), "MockLineReader not yet go next line");
        lr.read(buf, 0, 4);
        assertEquals(1, lr.getLineNumber(), "In the middle of line 2");
        assertEquals("Line", new String(buf, 0, 4));
        ArrayList<String> ar = lr.lines()
             .peek(l -> assertEquals(lr.getLineNumber(), r.getLineNumber()))
             .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        assertEquals(" 2", ar.get(0), "Remaining in the second line");
        for (int i = 1; i < ar.size(); i++) {
            assertEquals("Line " + (i + 2), ar.get(i), "Rest are full lines");
        }
    }

    @Test
    public void testInterlacedRead() throws IOException {
        MockLineReader r = new MockLineReader(10);
        BufferedReader br = new BufferedReader(r);
        char[] buf = new char[5];
        Stream<String> s = br.lines();
        Iterator<String> it = s.iterator();

        br.read(buf);
        assertEquals("Line ", new String(buf));
        assertEquals("1", it.next());
        assertThrows(IllegalStateException.class, () -> s.iterator().next(),
                     "Should fail on second call to Iterator next method");
        br.read(buf, 0, 2);
        assertEquals("Li", new String(buf, 0, 2));
        // Get stream again should continue from where left
        // Only read remaining of the line
        br.lines().limit(1L).forEach(line -> assertEquals(line, "ne 2"));
        br.read(buf, 0, 2);
        assertEquals("Li", new String(buf, 0, 2));
        br.read(buf, 0, 2);
        assertEquals("ne", new String(buf, 0, 2));
        assertEquals(" 3", it.next());
        // Line 4
        br.readLine();
        // interator pick
        assertEquals("Line 5", it.next());
        // Another stream instantiated by lines()
        AtomicInteger line_no = new AtomicInteger(6);
        br.lines().forEach(l -> assertEquals(l, "Line " + line_no.getAndIncrement()));
        // Read after EOL
        assertFalse(it.hasNext());
    }

    @Test
    public void testCharacteristics() {
        assertDoesNotThrow
            (() -> {
                try (BufferedReader br =
                     new BufferedReader(new StringReader(""))) {
                    Spliterator<String> instance = br.lines().spliterator();
                    assertTrue(instance.hasCharacteristics(Spliterator.NONNULL));
                    assertTrue(instance.hasCharacteristics(Spliterator.ORDERED));
                }
            });
    }
}
