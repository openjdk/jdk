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

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.WebSocket;

/*
 * THE CONTENTS OF THIS FILE HAVE TO BE IN SYNC WITH THE EXAMPLES USED IN THE
 * JAVADOC OF WEBSOCKET TYPE
 *
 * @test
 * @compile WebSocketExample.java
 */
public class WebSocketExample {

    WebSocket.Listener listener = new WebSocket.Listener() {
        // ...
    };

    public void newBuilderExample0() {
        HttpClient client = HttpClient.newHttpClient();
        WebSocket.Builder builder = client.newWebSocketBuilder(
                URI.create("ws://websocket.example.com"),
                listener);
    }

    public void newBuilderExample1() {
        InetSocketAddress addr = new InetSocketAddress("proxy.example.com", 80);
        HttpClient client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(addr))
                .build();
        WebSocket.Builder builder = client.newWebSocketBuilder(
                URI.create("ws://websocket.example.com"),
                listener);
    }
}
