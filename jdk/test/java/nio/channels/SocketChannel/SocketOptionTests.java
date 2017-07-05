/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4640544
 * @summary Unit test to check SocketChannel setOption/getOption/options
 *          methods.
 */

import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.util.*;
import static java.net.StandardSocketOption.*;

public class SocketOptionTests {

    static void checkOption(SocketChannel sc, SocketOption name, Object expectedValue)
        throws IOException
    {
        Object value = sc.getOption(name);
        if (!value.equals(expectedValue))
            throw new RuntimeException("value not as expected");
    }

    public static void main(String[] args) throws IOException {
        SocketChannel sc = SocketChannel.open();

        // check supported options
        Set<SocketOption<?>> options = sc.supportedOptions();
        List<? extends SocketOption> expected = Arrays.asList(SO_SNDBUF, SO_RCVBUF,
            SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, TCP_NODELAY);
        for (SocketOption opt: expected) {
            if (!options.contains(opt))
                throw new RuntimeException(opt.name() + " should be supported");
        }

        // check specified defaults
        int linger = sc.<Integer>getOption(SO_LINGER);
        if (linger >= 0)
            throw new RuntimeException("initial value of SO_LINGER should be < 0");
        checkOption(sc, SO_KEEPALIVE, false);
        checkOption(sc, TCP_NODELAY, false);

        // allowed to change when not bound
        sc.setOption(SO_KEEPALIVE, true);
        checkOption(sc, SO_KEEPALIVE, true);
        sc.setOption(SO_KEEPALIVE, false);
        checkOption(sc, SO_KEEPALIVE, false);
        sc.setOption(SO_SNDBUF, 128*1024);      // can't check
        sc.setOption(SO_RCVBUF, 256*1024);      // can't check
        int before, after;
        before = sc.getOption(SO_SNDBUF);
        after = sc.setOption(SO_SNDBUF, Integer.MAX_VALUE).getOption(SO_SNDBUF);
        if (after < before)
            throw new RuntimeException("setOption caused SO_SNDBUF to decrease");
        before = sc.getOption(SO_RCVBUF);
        after = sc.setOption(SO_RCVBUF, Integer.MAX_VALUE).getOption(SO_RCVBUF);
        if (after < before)
            throw new RuntimeException("setOption caused SO_RCVBUF to decrease");
        sc.setOption(SO_REUSEADDR, true);
        checkOption(sc, SO_REUSEADDR, true);
        sc.setOption(SO_REUSEADDR, false);
        checkOption(sc, SO_REUSEADDR, false);
        sc.setOption(SO_LINGER, 10);
        linger = sc.<Integer>getOption(SO_LINGER);
        if (linger < 1)
            throw new RuntimeException("expected linger to be enabled");
        sc.setOption(SO_LINGER, -1);
        linger = sc.<Integer>getOption(SO_LINGER);
        if (linger >= 0)
            throw new RuntimeException("expected linger to be disabled");
        sc.setOption(TCP_NODELAY, true);
        checkOption(sc, TCP_NODELAY, true);
        sc.setOption(TCP_NODELAY, false);       // can't check

        // bind socket
        sc.bind(new InetSocketAddress(0));

        // allow to change when bound
        sc.setOption(SO_KEEPALIVE, true);
        checkOption(sc, SO_KEEPALIVE, true);
        sc.setOption(SO_KEEPALIVE, false);
        checkOption(sc, SO_KEEPALIVE, false);

        sc.setOption(SO_LINGER, 10);
        linger = sc.<Integer>getOption(SO_LINGER);
        if (linger < 1)
            throw new RuntimeException("expected linger to be enabled");
        sc.setOption(SO_LINGER, -1);
        linger = sc.<Integer>getOption(SO_LINGER);
        if (linger >= 0)
            throw new RuntimeException("expected linger to be disabled");
        sc.setOption(TCP_NODELAY, true);        // can't check
        sc.setOption(TCP_NODELAY, false);       // can't check

        // NullPointerException
        try {
            sc.setOption(null, "value");
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException x) {
        }
        try {
            sc.getOption(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException x) {
        }

        // ClosedChannelException
        sc.close();
        try {
            sc.setOption(TCP_NODELAY, true);
            throw new RuntimeException("ClosedChannelException not thrown");
        } catch (ClosedChannelException x) {
        }
    }
}
