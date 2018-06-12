/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8203827
 * @summary Verify that escape sequences intepretation (used by Windows Terminal) works properly.
 * @modules jdk.internal.le/jdk.internal.jline.extra
 * @build AnsiInterpretingOutputStreamTest
 * @run testng AnsiInterpretingOutputStreamTest
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import jdk.internal.jline.extra.AnsiInterpretingOutputStream;
import jdk.internal.jline.extra.AnsiInterpretingOutputStream.BufferState;
import jdk.internal.jline.extra.AnsiInterpretingOutputStream.Performer;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test
public class AnsiInterpretingOutputStreamTest {

    public void testAnsiInterpretation() throws IOException {
        BufferState[] state = new BufferState[] {new BufferState(5, 5, 10, 10)};
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        OutputStream test = new AnsiInterpretingOutputStream("UTF-8", result, new Performer() {
            @Override
            public BufferState getBufferState() {
                return state[0];
            }
            @Override
            public void setCursorPosition(int cursorX, int cursorY) {
                state[0] = new BufferState(cursorX, cursorY, state[0].sizeX, state[0].sizeY);
                try {
                    result.write(("<setCursorPosition(" + cursorX + ", " + cursorY + ")>").getBytes("UTF-8"));
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
        });

        Writer testWriter = new OutputStreamWriter(test, "UTF-8");

        //cursor move:
        testWriter.write("\033[A\033[3A\033[15A\n");
        testWriter.write("\033[B\033[3B\033[15B\n");
        testWriter.write("\033[D\033[3D\033[15D\n");
        testWriter.write("\033[C\033[3C\033[15C\n");

        //clearing line:
        testWriter.write("\033[5D\n");
        testWriter.write("\033[K\n");
        testWriter.write("\033[1K\n");
        testWriter.write("\033[2K\n");

        testWriter.flush();

        String expected = "<setCursorPosition(5, 4)><setCursorPosition(5, 1)><setCursorPosition(5, 0)>\n" +
                          "<setCursorPosition(5, 1)><setCursorPosition(5, 4)><setCursorPosition(5, 9)>\n" +
                          "<setCursorPosition(4, 9)><setCursorPosition(1, 9)><setCursorPosition(0, 9)>\n" +
                          "<setCursorPosition(1, 9)><setCursorPosition(4, 9)><setCursorPosition(9, 9)>\n" +
                          "<setCursorPosition(4, 9)>\n" +
                          "     <setCursorPosition(4, 9)>\n" +
                          "<setCursorPosition(0, 9)>    \n" +
                          "         <setCursorPosition(0, 9)>\n";
        String actual = new String(result.toByteArray(), "UTF-8");

        assertEquals(actual, expected);
    }
}
