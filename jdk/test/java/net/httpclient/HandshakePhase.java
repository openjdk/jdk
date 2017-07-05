/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

//
// Performs a simple opening handshake and yields the channel.
//
// Client Request:
//
//    GET /chat HTTP/1.1
//    Host: server.example.com
//    Upgrade: websocket
//    Connection: Upgrade
//    Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
//    Origin: http://example.com
//    Sec-WebSocket-Protocol: chat, superchat
//    Sec-WebSocket-Version: 13
//
//
// Server Response:
//
//    HTTP/1.1 101 Switching Protocols
//    Upgrade: websocket
//    Connection: Upgrade
//    Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
//    Sec-WebSocket-Protocol: chat
//
final class HandshakePhase {

    private final ServerSocketChannel ssc;

    HandshakePhase(InetSocketAddress address) {
        requireNonNull(address);
        try {
            ssc = ServerSocketChannel.open();
            ssc.bind(address);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    //
    // Returned CF completes normally after the handshake has been performed
    //
    CompletableFuture<SocketChannel> afterHandshake(
            Function<List<String>, List<String>> mapping) {
        return CompletableFuture.supplyAsync(
                () -> {
                    SocketChannel socketChannel = accept();
                    try {
                        StringBuilder request = new StringBuilder();
                        if (!readRequest(socketChannel, request)) {
                            throw new IllegalStateException();
                        }
                        List<String> strings = Arrays.asList(
                                request.toString().split("\r\n")
                        );
                        List<String> response = mapping.apply(strings);
                        writeResponse(socketChannel, response);
                        return socketChannel;
                    } catch (Throwable t) {
                        try {
                            socketChannel.close();
                        } catch (IOException ignored) { }
                        throw t;
                    }
                });
    }

    CompletableFuture<SocketChannel> afterHandshake() {
        return afterHandshake((request) -> {
            List<String> response = new LinkedList<>();
            Iterator<String> iterator = request.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("The request is empty");
            }
            if (!"GET / HTTP/1.1".equals(iterator.next())) {
                throw new IllegalStateException
                        ("Unexpected status line: " + request.get(0));
            }
            response.add("HTTP/1.1 101 Switching Protocols");
            Map<String, String> requestHeaders = new HashMap<>();
            while (iterator.hasNext()) {
                String header = iterator.next();
                String[] split = header.split(": ");
                if (split.length != 2) {
                    throw new IllegalStateException
                            ("Unexpected header: " + header
                                    + ", split=" + Arrays.toString(split));
                }
                if (requestHeaders.put(split[0], split[1]) != null) {
                    throw new IllegalStateException
                            ("Duplicating headers: " + Arrays.toString(split));
                }
            }
            if (requestHeaders.containsKey("Sec-WebSocket-Protocol")) {
                throw new IllegalStateException("Subprotocols are not expected");
            }
            if (requestHeaders.containsKey("Sec-WebSocket-Extensions")) {
                throw new IllegalStateException("Extensions are not expected");
            }
            expectHeader(requestHeaders, "Connection", "Upgrade");
            response.add("Connection: Upgrade");
            expectHeader(requestHeaders, "Upgrade", "websocket");
            response.add("Upgrade: websocket");
            expectHeader(requestHeaders, "Sec-WebSocket-Version", "13");
            String key = requestHeaders.get("Sec-WebSocket-Key");
            if (key == null) {
                throw new IllegalStateException("Sec-WebSocket-Key is missing");
            }
            MessageDigest sha1 = null;
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new InternalError(e);
            }
            String x = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            sha1.update(x.getBytes(StandardCharsets.ISO_8859_1));
            String v = Base64.getEncoder().encodeToString(sha1.digest());
            response.add("Sec-WebSocket-Accept: " + v);
            return response;
        });
    }

    private String expectHeader(Map<String, String> headers,
                                String name,
                                String value) {
        String v = headers.get(name);
        if (!value.equals(v)) {
            throw new IllegalStateException(
                    format("Expected '%s: %s', actual: '%s: %s'",
                            name, value, name, v)
            );
        }
        return v;
    }

    URI getURI() {
        InetSocketAddress a;
        try {
            a = (InetSocketAddress) ssc.getLocalAddress();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return URI.create("ws://" + a.getHostName() + ":" + a.getPort());
    }

    private int read(SocketChannel socketChannel, ByteBuffer buffer) {
        try {
            int num = socketChannel.read(buffer);
            if (num == -1) {
                throw new IllegalStateException("Unexpected EOF");
            }
            assert socketChannel.isBlocking() && num > 0;
            return num;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SocketChannel accept() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = ssc.accept();
            socketChannel.configureBlocking(true);
        } catch (IOException e) {
            if (socketChannel != null) {
                try {
                    socketChannel.close();
                } catch (IOException ignored) { }
            }
            throw new UncheckedIOException(e);
        }
        return socketChannel;
    }

    private boolean readRequest(SocketChannel socketChannel,
                                StringBuilder request) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        read(socketChannel, buffer);
        CharBuffer decoded;
        buffer.flip();
        try {
            decoded =
                    StandardCharsets.ISO_8859_1.newDecoder().decode(buffer);
        } catch (CharacterCodingException e) {
            throw new UncheckedIOException(e);
        }
        request.append(decoded);
        return Pattern.compile("\r\n\r\n").matcher(request).find();
    }

    private void writeResponse(SocketChannel socketChannel,
                               List<String> response) {
        String s = response.stream().collect(Collectors.joining("\r\n"))
                + "\r\n\r\n";
        ByteBuffer encoded;
        try {
            encoded =
                    StandardCharsets.ISO_8859_1.newEncoder().encode(CharBuffer.wrap(s));
        } catch (CharacterCodingException e) {
            throw new UncheckedIOException(e);
        }
        write(socketChannel, encoded);
    }

    private void write(SocketChannel socketChannel, ByteBuffer buffer) {
        try {
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
        } catch (IOException e) {
            try {
                socketChannel.close();
            } catch (IOException ignored) { }
            throw new UncheckedIOException(e);
        }
    }
}
