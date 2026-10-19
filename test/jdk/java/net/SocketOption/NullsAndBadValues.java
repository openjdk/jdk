/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8224477
 * @summary Basic test for NPE, UOE, and IAE for get/setOption
 * @run junit ${test.main.class}
 * @run junit/othervm -Dsun.net.useExclusiveBind=false ${test.main.class}
 */

import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static java.lang.Boolean.*;
import static java.net.StandardSocketOptions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NullsAndBadValues {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    @Test
    public void nulls() throws Exception {
        try (Socket s = new Socket()) {
            assertThrows(NPE, () -> s.setOption(null, null));
            assertThrows(NPE, () -> s.setOption(null, ""));
            assertThrows(NPE, () -> s.setOption(null, 1));
            assertThrows(NPE, () -> s.getOption(null));
        }
        try (ServerSocket ss = new ServerSocket()) {
            assertThrows(NPE, () -> ss.setOption(null, null));
            assertThrows(NPE, () -> ss.setOption(null, ""));
            assertThrows(NPE, () -> ss.setOption(null, 1));
            assertThrows(NPE, () -> ss.getOption(null));
        }
        try (DatagramSocket ds = new DatagramSocket()) {
            assertThrows(NPE, () -> ds.setOption(null, null));
            assertThrows(NPE, () -> ds.setOption(null, ""));
            assertThrows(NPE, () -> ds.setOption(null, 1));
            assertThrows(NPE, () -> ds.getOption(null));
        }
        try (MulticastSocket ms = new MulticastSocket()) {
            assertThrows(NPE, () -> ms.setOption(null, null));
            assertThrows(NPE, () -> ms.setOption(null, ""));
            assertThrows(NPE, () -> ms.setOption(null, 1));
            assertThrows(NPE, () -> ms.getOption(null));
        }
        try (Socket sa = SocketChannel.open().socket()) {
            assertThrows(NPE, () -> sa.setOption(null, null));
            assertThrows(NPE, () -> sa.setOption(null, ""));
            assertThrows(NPE, () -> sa.setOption(null, 1));
            assertThrows(NPE, () -> sa.getOption(null));
        }
        try (ServerSocket ssa = ServerSocketChannel.open().socket()) {
            assertThrows(NPE, () -> ssa.setOption(null, null));
            assertThrows(NPE, () -> ssa.setOption(null, ""));
            assertThrows(NPE, () -> ssa.setOption(null, 1));
            assertThrows(NPE, () -> ssa.getOption(null));
        }
        try (DatagramSocket dsa = DatagramChannel.open().socket()) {
            assertThrows(NPE, () -> dsa.setOption(null, null));
            assertThrows(NPE, () -> dsa.setOption(null, ""));
            assertThrows(NPE, () -> dsa.setOption(null, 1));
            assertThrows(NPE, () -> dsa.getOption(null));
        }
    }

    static final SocketOption<Boolean> FAKE_SOCK_OPT = new SocketOption<>() {
        @Override public String name() { return "FAKE_SOCK_OPT"; }
        @Override public Class<Boolean> type() { return Boolean.class; }
    };

    static final SocketOption RAW_SOCK_OPT = new SocketOption() {
        @Override public String name() { return "RAW_SOCK_OPT"; }
        @Override public Class type()  { return Boolean.class;  }
    };

    @Test
    public void uoe() throws Exception {
        try (Socket s = new Socket()) {
            assertThrows(UOE, () -> s.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> s.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> s.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> s.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> s.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> s.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> s.getOption(RAW_SOCK_OPT));
        }
        try (ServerSocket ss = new ServerSocket()) {
            assertThrows(UOE, () -> ss.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> ss.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> ss.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> ss.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> ss.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> ss.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> ss.getOption(RAW_SOCK_OPT));
        }
        try (DatagramSocket ds = new DatagramSocket()) {
            assertThrows(UOE, () -> ds.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> ds.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> ds.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> ds.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> ds.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> ds.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> ds.getOption(RAW_SOCK_OPT));
        }
        try (MulticastSocket ms = new MulticastSocket()) {
            assertThrows(UOE, () -> ms.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> ms.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> ms.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> ms.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> ms.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> ms.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> ms.getOption(RAW_SOCK_OPT));
        }
        try (Socket sa = SocketChannel.open().socket()) {
            assertThrows(UOE, () -> sa.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> sa.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> sa.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> sa.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> sa.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> sa.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> sa.getOption(RAW_SOCK_OPT));
        }
        try (ServerSocket ssa = ServerSocketChannel.open().socket()) {
            assertThrows(UOE, () -> ssa.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> ssa.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> ssa.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> ssa.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> ssa.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> ssa.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> ssa.getOption(RAW_SOCK_OPT));
        }
        try (DatagramSocket dsa = DatagramChannel.open().socket()) {
            assertThrows(UOE, () -> dsa.setOption(FAKE_SOCK_OPT, null));
            assertThrows(UOE, () -> dsa.setOption(FAKE_SOCK_OPT, TRUE));
            assertThrows(UOE, () -> dsa.setOption(FAKE_SOCK_OPT, FALSE));
            assertThrows(UOE, () -> dsa.setOption(RAW_SOCK_OPT, ""));
            assertThrows(UOE, () -> dsa.setOption(RAW_SOCK_OPT, 1));
            assertThrows(UOE, () -> dsa.getOption(FAKE_SOCK_OPT));
            assertThrows(UOE, () -> dsa.getOption(RAW_SOCK_OPT));
        }
    }

    static Map<SocketOption<?>,List<Object>> BAD_OPTION_VALUES = badOptionValues();

    static Map<SocketOption<?>,List<Object>> badOptionValues() {
        Map<SocketOption<?>,List<Object>> map = new HashMap<>();
        map.put(IP_MULTICAST_IF,   listOf(null)         );
        map.put(IP_MULTICAST_LOOP, listOf(null)         );
        map.put(IP_MULTICAST_TTL,  listOf(null, -1, 256));
        map.put(IP_TOS,            listOf(null, -1, 256));
        map.put(SO_BROADCAST,      listOf(null)         );
        map.put(SO_KEEPALIVE,      listOf(null)         );
        map.put(SO_LINGER,         listOf(null)         );
        map.put(SO_RCVBUF,         listOf(null, -1)     );
        map.put(SO_REUSEADDR,      listOf(null)         );
        map.put(SO_REUSEPORT,      listOf(null)         );
        map.put(SO_SNDBUF,         listOf(null, -1)     );
        map.put(TCP_NODELAY,       listOf(null)         );
        // extended options, not in the map, will get a null value
        return map;
    }

    // -- Socket

    public static Object[][] socketBadOptionValues() throws Exception {
        try (Socket s = new Socket()) {
            return s.supportedOptions().stream()
                    .flatMap(NullsAndBadValues::socketOptionToBadValues)
                    .toArray(Object[][]::new);
        }
    }

    @ParameterizedTest
    @MethodSource("socketBadOptionValues")
    public <T> void socket(SocketOption<T> option, T value)
        throws Exception
    {
        try (Socket s = new Socket()) {
            assertThrows(IAE, () -> s.setOption(option, value));
        }
    }

    @ParameterizedTest
    @MethodSource("socketBadOptionValues")
    public <T> void socketAdapter(SocketOption<T> option, T value)
        throws Exception
    {
        try (Socket s = SocketChannel.open().socket()) {
            assertThrows(IAE, () -> s.setOption(option, value));
        }
    }

    // -- ServerSocket

    public static Object[][] serverSocketBadOptionValues() throws Exception {
        try (ServerSocket ss = new ServerSocket()) {
            return ss.supportedOptions().stream()
                     .flatMap(NullsAndBadValues::socketOptionToBadValues)
                     .toArray(Object[][]::new);
        }
    }

    @ParameterizedTest
    @MethodSource("serverSocketBadOptionValues")
    public <T> void serverSocket(SocketOption<T> option, T value)
        throws Exception
    {
        try (ServerSocket ss = new ServerSocket()) {
            assertThrows(IAE, () -> ss.setOption(option, value));
        }
    }

    @ParameterizedTest
    @MethodSource("serverSocketBadOptionValues")
    public <T> void serverSocketAdapter(SocketOption<T> option, T value)
        throws Exception
    {
        if (option == IP_TOS)
            return;  // SSC does not support IP_TOS

        try (ServerSocket ss = ServerSocketChannel.open().socket()) {
            assertThrows(IAE, () -> ss.setOption(option, value));
        }
    }

    // -- DatagramSocket

    public static Object[][] datagramSocketBadOptionValues() throws Exception {
        try (DatagramSocket ds = new DatagramSocket()) {
            return ds.supportedOptions().stream()
                     .flatMap(NullsAndBadValues::socketOptionToBadValues)
                     .toArray(Object[][]::new);
        }
    }

    @ParameterizedTest
    @MethodSource("datagramSocketBadOptionValues")
    public <T> void datagramSocket(SocketOption<T> option, T value)
        throws Exception
    {
        try (DatagramSocket ds = new DatagramSocket()) {
            assertThrows(IAE, () -> ds.setOption(option, value));
        }
    }

    @ParameterizedTest
    @MethodSource("datagramSocketBadOptionValues")
    public <T> void datagramSocketAdapter(SocketOption<T> option, T value)
        throws Exception
    {
        try (DatagramSocket ds = DatagramChannel.open().socket()) {
            assertThrows(IAE, () -> ds.setOption(option, value));
        }
    }

    // -- MulticastSocket

    public static Object[][] multicastSocketBadOptionValues() throws Exception {
        try (MulticastSocket ms = new MulticastSocket()) {
            return ms.supportedOptions().stream()
                     .flatMap(NullsAndBadValues::socketOptionToBadValues)
                     .toArray(Object[][]::new);
        }
    }

    @ParameterizedTest
    @MethodSource("multicastSocketBadOptionValues")
    public <T> void multicastSocket(SocketOption<T> option, T value)
        throws Exception
    {
        try (MulticastSocket ms = new MulticastSocket()) {
            assertThrows(IAE, () -> ms.setOption(option, value));
        }
    }

    // --

    static List<Object> listOf(Object... objs) {
        List<Object> l = new ArrayList<>();
        if (objs == null)
            l.add(null);
        else
            Arrays.stream(objs).forEachOrdered(l::add);
        return l;
    }

    static Stream<Object[]> socketOptionToBadValues(SocketOption<?> socketOption) {
        List<Object> values = BAD_OPTION_VALUES.get(socketOption);
        if (values == null) {
            Object[][] a = new Object[][] { new Object[] { socketOption, null } };
            return Stream.of(a);
        }
        return values.stream()
                .flatMap(v -> Stream.of(new Object[][] { new Object[] { socketOption, v } }) );
    }
}
