/*
 * Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit https://www.oracle.com if you need any additional information
 * or have any questions.
 */

/*
 * @test
 * @bug 8360123
 * @summary Unit test for AbstractStringBuilder.appendLatin1(char, char)
 * @run testng/othervm --add-opens java.base/java.lang=ALL-UNNAMED AppendLatin1Test
 */

import java.lang.reflect.Method;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test the appendLatin1 method added to AbstractStringBuilder.
 * This test uses reflection to access the package-private method.
 */
public class AppendLatin1Test {

    @Test
    public void testAppendLatin1Basic() throws Exception {
        StringBuilder sb = new StringBuilder();
        invokeAppendLatin1(sb, 'a', 'b');
        assertEquals(sb.toString(), "ab");
    }

    @Test
    public void testAppendLatin1Multiple() throws Exception {
        StringBuilder sb = new StringBuilder();
        invokeAppendLatin1(sb, 'H', 'e');
        invokeAppendLatin1(sb, 'l', 'l');
        invokeAppendLatin1(sb, 'o', '!');
        assertEquals(sb.toString(), "Hello!");
    }

    @Test
    public void testAppendLatin1WithExistingContent() throws Exception {
        StringBuilder sb = new StringBuilder("Start:");
        invokeAppendLatin1(sb, 'E', 'N');
        invokeAppendLatin1(sb, 'D', '!');
        assertEquals(sb.toString(), "Start:END!");
    }

    @Test
    public void testAppendLatin1WithLatin1Boundaries() throws Exception {
        StringBuilder sb = new StringBuilder();
        // Test boundary values
        invokeAppendLatin1(sb, '\u0000', '\u0001');
        invokeAppendLatin1(sb, '\u007F', '\u0080');
        invokeAppendLatin1(sb, '\u00FE', '\u00FF');
        
        String result = sb.toString();
        assertEquals(result.length(), 6);
        assertEquals(result.charAt(0), '\u0000');
        assertEquals(result.charAt(1), '\u0001');
        assertEquals(result.charAt(2), '\u007F');
        assertEquals(result.charAt(3), '\u0080');
        assertEquals(result.charAt(4), '\u00FE');
        assertEquals(result.charAt(5), '\u00FF');
    }

    @Test
    public void testAppendLatin1AfterUTF16() throws Exception {
        StringBuilder sb = new StringBuilder();
        // First append a UTF16 character (this inflates the buffer)
        sb.append('\u0100');
        // Now append Latin1 chars - should work even in UTF16 mode
        invokeAppendLatin1(sb, 'a', 'b');
        
        String result = sb.toString();
        assertEquals(result.length(), 3);
        assertEquals(result.charAt(0), '\u0100');
        assertEquals(result.charAt(1), 'a');
        assertEquals(result.charAt(2), 'b');
    }

    @Test
    public void testAppendLatin1Capacity() throws Exception {
        // Test that capacity is properly expanded
        StringBuilder sb = new StringBuilder(2); // Start with small capacity
        invokeAppendLatin1(sb, 'a', 'b');
        invokeAppendLatin1(sb, 'c', 'd');
        invokeAppendLatin1(sb, 'e', 'f');
        assertEquals(sb.toString(), "abcdef");
    }

    @Test
    public void testAppendLatin1WithAppend() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append('x');
        invokeAppendLatin1(sb, 'a', 'b');
        sb.append('y');
        invokeAppendLatin1(sb, 'c', 'd');
        sb.append('z');
        assertEquals(sb.toString(), "xabycdz");
    }

    @Test
    public void testStringBuffer() throws Exception {
        StringBuffer sb = new StringBuffer();
        invokeAppendLatin1(sb, 'a', 'b');
        assertEquals(sb.toString(), "ab");
    }

    /**
     * Helper method to invoke appendLatin1 via reflection.
     */
    private void invokeAppendLatin1(Object sb, char c1, char c2) throws Exception {
        Method method = sb.getClass().getSuperclass().getDeclaredMethod(
            "appendLatin1", char.class, char.class);
        method.setAccessible(true);
        method.invoke(sb, c1, c2);
    }
}
