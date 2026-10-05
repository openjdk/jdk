/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8246707
 * @summary Reading or Writing to a closed SocketChannel should throw a ClosedChannelException
 * @run junit/othervm ReadWriteAfterClose
 */

public class ReadWriteAfterClose {

    private static ServerSocketChannel listener;
    private static SocketAddress saddr;
    private static final int bufCapacity = 4;
    private static final int bufArraySize = 4;
    private static final Class<ClosedChannelException> CCE = ClosedChannelException.class;

    @BeforeAll
    public static void setUp() throws IOException {
        listener = ServerSocketChannel.open();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        saddr = listener.getLocalAddress();
    }

    @AfterAll
    public static void tearDown() throws IOException {
        if (listener != null) listener.close();
    }

    @Test
    public void testWriteAfterClose1() throws IOException {
        SocketChannel sc = SocketChannel.open(saddr);
        sc.close();
        ByteBuffer bufWrite = ByteBuffer.allocate(bufCapacity);
        Throwable ex = assertThrows(CCE, () -> sc.write(bufWrite));
        assertSame(CCE, ex.getClass());
    }

    @Test
    public void testWriteAfterClose2() throws IOException {
        SocketChannel sc = SocketChannel.open(saddr);
        sc.close();
        ByteBuffer[] bufArrayWrite = allocateBufArray();
        Throwable ex = assertThrows(CCE, () -> sc.write(bufArrayWrite));
        assertSame(CCE, ex.getClass());
    }

    @Test
    public void testWriteAfterClose3() throws IOException {
        SocketChannel sc = SocketChannel.open(saddr);
        sc.close();
        ByteBuffer[] bufArrayWrite = allocateBufArray();
        Throwable ex = assertThrows(CCE, () -> sc.write(bufArrayWrite, 0, bufArraySize));
        assertSame(CCE, ex.getClass());
    }

    @Test
    public void testReadAfterClose1() throws IOException {
        SocketChannel sc = SocketChannel.open(saddr);
        sc.close();
        ByteBuffer dst = ByteBuffer.allocate(bufCapacity);
        Throwable ex = assertThrows(CCE, () -> sc.read(dst));
        assertSame(CCE, ex.getClass());
    }

    @Test
    public void testReadAfterClose2() throws IOException {
        SocketChannel sc = SocketChannel.open(saddr);
        sc.close();
        ByteBuffer[] dstArray = allocateBufArray();
        Throwable ex = assertThrows(CCE, () -> sc.read(dstArray));
        assertSame(CCE, ex.getClass());
    }

    @Test
    public void testReadAfterClose3() throws IOException {
        SocketChannel sc = SocketChannel.open(saddr);
        sc.close();
        ByteBuffer[] dstArray = allocateBufArray();
        Throwable ex = assertThrows(CCE, () -> sc.read(dstArray, 0, bufArraySize));
        assertSame(CCE, ex.getClass());
    }

    public ByteBuffer[] allocateBufArray() {
        ByteBuffer[] bufArr = new ByteBuffer[bufArraySize];
        for (int i = 0; i < bufArraySize; i++)
            bufArr[i] = ByteBuffer.allocate(bufCapacity);
        return bufArr;
    }

}