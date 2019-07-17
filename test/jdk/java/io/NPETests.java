/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8067801
 * @run testng NPETests
 * @summary Ensure constructors throw NPE when passed a null stream
 */
public class NPETests {

    @Test
    public static void BufferedInputStreamConstructor() {
        assertThrows(NullPointerException.class,
            () -> new BufferedInputStream(null));
        assertThrows(NullPointerException.class,
            () -> new BufferedInputStream(null, 42));
    }

    @Test
    public static void DataInputStreamConstructor() {
        assertThrows(NullPointerException.class,
            () -> new DataInputStream(null));
    }

    @Test
    public static void PushbackInputStreamConstructor() {
        assertThrows(NullPointerException.class,
            () -> new PushbackInputStream(null));
        assertThrows(NullPointerException.class,
            () -> new PushbackInputStream(null, 42));
    }

    @Test
    public static void BufferedOutputStreamConstructor() {
        assertThrows(NullPointerException.class,
            () -> new BufferedOutputStream(null));
        assertThrows(NullPointerException.class,
            () -> new BufferedOutputStream(null, 42));
    }

    @Test
    public static void DataOutputStreamConstructor() {
        assertThrows(NullPointerException.class,
            () -> new DataOutputStream(null));
    }
}
