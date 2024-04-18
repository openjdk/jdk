/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @bug 8291226 8291638 8330523
 * @modules java.base/sun.net.www.http:+open
 *          java.base/sun.net.www.protocol.http:+open
 * @run main/othervm KeepAliveTest c0
 * @run main/othervm KeepAliveTest c1
 * @run main/othervm KeepAliveTest c2
 * @run main/othervm KeepAliveTest c3
 * @run main/othervm KeepAliveTest c4
 * @run main/othervm KeepAliveTest c5
 * @run main/othervm KeepAliveTest c6
 * @run main/othervm KeepAliveTest c7
 * @run main/othervm KeepAliveTest c8
 * @run main/othervm KeepAliveTest c9
 * @run main/othervm KeepAliveTest c10
 * @run main/othervm KeepAliveTest c11
 * @run main/othervm KeepAliveTest c12
 * @run main/othervm KeepAliveTest c13
 * @run main/othervm KeepAliveTest c14
 * @run main/othervm KeepAliveTest c15
 */

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.net.www.http.HttpClient;
import sun.net.www.http.KeepAliveCache;
import sun.net.www.protocol.http.HttpURLConnection;
import jdk.test.lib.net.URIBuilder;

public class KeepAliveTest {

    /*
     * This test covers 160 scenarios.
     * For every scenario there is mapping between serverScenarios[int], clientScenarios[int] and expectedValues[int]
     *
     * serverScenarios[0] clientScenarios[0] expectedValues[0]
     * serverScenarios[1] clientScenarios[1] expectedValues[1]
     * serverScenarios[2] clientScenarios[2] expectedValues[2]
     *
     * ...
     *
     * serverScenarios[159] cientScenarios[159] expectedValues[159]
     */

   /* Here is the complete table of server response headers, client system properties input and expected cache timeout at client side */
   /* ScNo  |  SERVER RESPONSE HEADERS                        | CLIENT SYSTEM PROPERTIES                     | EXPECTED CACHE TIMEOUT AT CLIENT SIDE
    *****************************************************************************************************************************************
    *    0  |  Connection: keep-alive (A)                     | No Input Provided (NI)                       | Default Timeout set to 5
    *---------------------------------------------------------------------------------------------------------------------------
    *    1  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=100 (SERVER_100)| Client Timeout set to 100
    *--------------------------------------------------------------------------------------------------------------------------
    *    2  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.proxy=200 (PROXY_200)  | Default Timeout set to 5
    *---------------------------------------------------------------------------------------------------------------------------
    *    3  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=100 &&          |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=200              | Timeout set to 100
    *       |                                                 | (SERVER_100 && PROXY_200)                    |
    *---------------------------------------------------------------------------------------------------------------------------
    *    4  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=-100            | Default Timeout set to 5
    *       |                                                 | (SERVER_100_NEGATIVE)                        |
    *---------------------------------------------------------------------------------------------------------------------------
    *    5  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.proxy=-200             | Default Timeout set to 5
    *       |                                                 | (PROXY_200_NEGATIVE)                         |
    *---------------------------------------------------------------------------------------------------------------------------
    *    6  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=-100 &&         |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=-200             | Default Timeout set to 5
    *       |                                                 | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)  |
    *---------------------------------------------------------------------------------------------------------------------------
    *    7  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=0               | Connection Closed Immediately
    *       |                                                 | (SERVER_ZERO)                                |
    *---------------------------------------------------------------------------------------------------------------------------
    *    8  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.proxy=0                | Default Timeout set to 5
    *       |                                                 | (PROXY_ZERO)                                 |
    *---------------------------------------------------------------------------------------------------------------------------
    *    9  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=0 &&            |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=0                | Connection Closed Immediately
    *       |                                                 | (SERVER_ZERO && PROXY_ZERO)                  |
    *---------------------------------------------------------------------------------------------------------------------------
    *   10  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=0 &&            |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=-200             | Connection Closed Immediately
    *       |                                                 | (SERVER_ZERO && PROXY_200_NEGATIVE)          |
    *---------------------------------------------------------------------------------------------------------------------------
    *   11  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=-100 &&         |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=0                | Default Timeout set to 5
    *       |                                                 | (SERVER_100_NEGATIVE && PROXY_ZERO)          |
    *---------------------------------------------------------------------------------------------------------------------------
    *   12  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=100 &&          |
    *       |                                                 |  -Dhttp.keepAlive.time.proxy=0               | Timeout set to 100
    *       |                                                 | (SERVER_100 && PROXY_ZERO)                   |
    *---------------------------------------------------------------------------------------------------------------------------
    *   13  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=0 &&            |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=200              | Connection Closed Immediately
    *       |                                                 | (SERVER_ZERO && PROXY_200)                   |
    *---------------------------------------------------------------------------------------------------------------------------
    *   14  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=100 &&          |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=-200             | Timeout set to 100
    *       |                                                 | (SERVER_100 && PROXY_200_NEGATIVE)           |
    *---------------------------------------------------------------------------------------------------------------------------
    *   15  |  Connection: keep-alive (A)                     | -Dhttp.keepAlive.time.server=-100 &&         |
    *       |                                                 | -Dhttp.keepAlive.time.proxy=200              | Default Timeout set to 5
    *       |                                                 | (SERVER_100_NEGATIVE && PROXY_200)           |
    *---------------------------------------------------------------------------------------------------------------------------
    *   16  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | No Input Provided (NI)                   | Timeout set to 20
    *------------------------------------------------------------------------------------------------------------------------
    *   17  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=100         | Timeout set to 20
    *       |                                                     | (SERVER_100)                             |
    *---------------------------------------------------------------------------------------------------------------------------
    *   18  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.proxy=200          | Timeout set to 20
    *       |                                                     | (PROXY_200)                              |
    *---------------------------------------------------------------------------------------------------------------------------
    *   19  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=100 &&      |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=200          | Timeout set to 20
    *       |                                                     | (SERVER_100 && PROXY_200)                |
    *---------------------------------------------------------------------------------------------------------------------------
    *   20  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=-100        | Timeout set to 20
    *       |                                                     | (SERVER_100_NEGATIVE)                    |
    *---------------------------------------------------------------------------------------------------------------------------
    *   21  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.proxy=-200         | Timeout set to 20
    *       |                                                     | (PROXY_200_NEGATIVE)                     |
    *---------------------------------------------------------------------------------------------------------------------------
    *   22  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=-100 &&       |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=-200           | Timeout set to 20
    *       |                                                     | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)|
    *---------------------------------------------------------------------------------------------------------------------------
    *   23  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=0      | Timeout set to 20
    *       |                                                     | (SERVER_ZERO)                       |
    *---------------------------------------------------------------------------------------------------------------------------
    *   24  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 20
    *       |                                                     | (PROXY_ZERO)                        |
    *---------------------------------------------------------------------------------------------------------------------------
    *   25  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 20
    *       |                                                     | (SERVER_ZERO && PROXY_ZERO)         |
    *---------------------------------------------------------------------------------------------------------------------------
    *   26  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=-200    | Timeout set to 20
    *       |                                                     | (SERVER_ZERO && PROXY_200_NEGATIVE) |
    *---------------------------------------------------------------------------------------------------------------------------
    *   27  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                     | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 20
    *       |                                                     | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *---------------------------------------------------------------------------------------------------------------------------
    *   28  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 20
    *       |                                                     | (SERVER_100 && PROXY_ZERO)          |
    *---------------------------------------------------------------------------------------------------------------------------
    *   29  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 20
    *       |                                                     | (SERVER_ZERO && PROXY_200)          |
    *---------------------------------------------------------------------------------------------------------------------------
    *   30  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=-200    | Timeout set to 20
    *       |                                                     | (SERVER_100 && PROXY_200_NEGATIVE)  |
    *---------------------------------------------------------------------------------------------------------------------------
    *   31  |Connection: keep-alive\r\nKeep-alive: timeout=20 (B) |-Dhttp.keepAlive.time.server=-100 && |
    *       |                                                     |-Dhttp.keepAlive.time.proxy=200      | Timeout set to 20
    *       |                                                     | (SERVER_100_NEGATIVE && PROXY_200)  |
    *---------------------------------------------------------------------------------------------------------------------------
    *   32  |Proxy-Connection: keep-alive (C)                     | No Input Provided (NI)              | Default timeout set to 60
    *---------------------------------------------------------------------------------------------------------------------------
    *   33  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=100    | Default timeout set to 60
    *       |                                                     | (SERVER_100)                        |
    *---------------------------------------------------------------------------------------------------------------------------
    *   34  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                     | (PROXY_200)                         |
    *--------------------------------------------------------------------------------------------------------------------------
    *   35  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                     | (SERVER_100 && PROXY_200)           |
    *--------------------------------------------------------------------------------------------------------------------------
    *   36  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=-100   | Default timeout set to 60
    *       |                                                     | (SERVER_100_NEGATIVE)               |
    *---------------------------------------------------------------------------------------------------------------------------
    *   37  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.proxy=-200    | Default timeout set to 60
    *       |                                                     | (PROXY_200_NEGATIVE)                |
    *---------------------------------------------------------------------------------------------------------------------------
    *   38  |Proxy-Connection: keep-alive (C)                     |-Dhttp.keepAlive.time.server=-100 &&       |
    *       |                                                     |-Dhttp.keepAlive.time.proxy=-200           | Default timeout set to 60
    *       |                                                     |(SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)|
    *---------------------------------------------------------------------------------------------------------------------------
    *   39  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=0      | Default timeout set to 60
    *       |                                                     | (SERVER_ZERO)                       |
    *---------------------------------------------------------------------------------------------------------------------------
    *   40  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                     | (PROXY_ZERO)                        |
    *---------------------------------------------------------------------------------------------------------------------------
    *   41  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                     | (SERVER_ZERO && PROXY_ZERO)         |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   42  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=-200    | Default timeout set to 60
    *       |                                                     | (SERVER_ZERO && PROXY_200_NEGATIVE) |
    *---------------------------------------------------------------------------------------------------------------------------------
    *   43  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                     | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                     | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *---------------------------------------------------------------------------------------------------------------------------------
    *   44  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                     | (SERVER_100 && PROXY_ZERO)          |
    *---------------------------------------------------------------------------------------------------------------------------
    *   45  |Proxy-Connection: keep-alive (C)                     | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                     | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                     | (SERVER_ZERO && PROXY_200)          |
    *---------------------------------------------------------------------------------------------------------------------------
    *   46  |Proxy-Connection: keep-alive (C)                     |-Dhttp.keepAlive.time.server=100 &&  |
    *       |                                                     |-Dhttp.keepAlive.time.proxy=-200     | Default timeout set to 60
    *       |                                                     | (SERVER_100 && PROXY_200_NEGATIVE)  |
    *---------------------------------------------------------------------------------------------------------------------------
    *   47  |Proxy-Connection: keep-alive (C)                     |-Dhttp.keepAlive.time.server=-100 && |
    *       |                                                     |-Dhttp.keepAlive.time.proxy=200      | Timeout set to 200
    *       |                                                     | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *---------------------------------------------------------------------------------------------------------------------------
    *   48  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | No Input Provided (NI)              | Default timeout set to 60
    *-----------------------------------------------------------------------------------------------------------------------------
    *   49  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=100    | Default timeout set to 60
    *       |                                                         | (SERVER_100)                        |
    *---------------------------------------------------------------------------------------------------------------------------
    *   50  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                         | (PROXY_200)                         |
    *------------------------------------------------------------------------------------------------------------------------------
    *   51  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                         | (SERVER_100 && PROXY_200)           |
    *------------------------------------------------------------------------------------------------------------------------------
    *   52  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=-100   | Default timeout set to 60
    *       |                                                         | (SERVER_100_NEGATIVE)               |
    *------------------------------------------------------------------------------------------------------------------------------
    *   53  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.proxy=-200    | Default timeout set to 60
    *       |                                                         | (PROXY_200_NEGATIVE)                |
    *------------------------------------------------------------------------------------------------------------------------------
    *   54  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=-100&&        |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200           | Default timeout set to 60
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   55  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=0      | Default timeout set to 60
    *       |                                                         | (SERVER_ZERO)                       |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   56  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (PROXY_ZERO)                        |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   57  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)         |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   58  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200    | Default timeout set to 60
    *       |                                                         | (SERVER_ZERO && PROXY_200_NEGATIVE) |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   59  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   60  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_ZERO)          |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   61  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                         | (SERVER_ZERO && PROXY_200)          |
    *------------------------------------------------------------------------------------------------------------------------------
    *   62  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200    | default timeout set to 60
    *       |                                                         | (SERVER_100 && PROXY_200_NEGATIVE)  |
    *------------------------------------------------------------------------------------------------------------------------------
    *   63  |Connection:keep-alive\r\nProxy-connection:keep-alive (D) | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 200
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)  |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   64  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| No Input Provided (NI)              | Timeout set to 120
    *-------------------------------------------------------------------------------------------------------------------------------
    *   65  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=100    | Timeout set to 120
    *       |                                                         | (SERVER_100)                        |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   66  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.proxy=200     | Timeout set to 120
    *       |                                                         | (PROXY_200)                         |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   67  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 120
    *       |                                                         | (SERVER_100 && PROXY_200)           |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   68  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=-100   | Timeout set to 120
    *       |                                                         | (SERVER_100_NEGATIVE)               |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   69  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.proxy=-200    | Timeout set to 120
    *       |                                                         | (PROXY_200_NEGATIVE)                |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   70  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=-100 &&       |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200           | Timeout set to 120
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)|
    *-------------------------------------------------------------------------------------------------------------------------------
    *   71  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=0      | Timeout set to 120
    *       |                                                         | (SERVER_ZERO)                       |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   72  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.proxy=0       | Timeout set to 120
    *       |                                                         | (PROXY_ZERO)                        |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   73  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 120
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)         |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   74  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200    | Timeout set to 120
    *       |                                                         | (SERVER_ZERO && PROXY_200_NEGATIVE) |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   75  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 120
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   76  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 120
    *       |                                                         | (SERVER_100 && PROXY_ZERO)          |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   77  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 120
    *       |                                                         | (SERVER_ZERO && PROXY_200)          |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   78  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=100 &&         |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200            | Timeout set to 120
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE) |
    *-------------------------------------------------------------------------------------------------------------------------------
    *   79  |Proxy-connection:keep-alive\r\nKeep-alive:timeout=120 (E)| -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | Timeout set to 120
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)  |
    *-----------------------------------------------------------------------------------------------------------------------------
    *   80  |No Input (NI)                                            | No Input Provided (NI)              | default timeout set to 5
    *-----------------------------------------------------------------------------------------------------------------------------
    *   81  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=100    | Timeout set to 100
    *       |                                                         | (SERVER_100)                        |
    *-----------------------------------------------------------------------------------------------------------------------------
    *   82  |No Input (NI)                                            | -Dhttp.keepAlive.time.proxy=200     | default timeout set to 5
    *       |                                                         | (PROXY_200)                         |
    *-----------------------------------------------------------------------------------------------------------------------------
    *   83  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | client timeot set to 100
    *       |                                                         | (SERVER_100 && PROXY_200)           |
    *------------------------------------------------------------------------------------------------------------------------------
    *   84  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=-100   | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE)               |
    *------------------------------------------------------------------------------------------------------------------------------
    *   85  |No Input (NI)                                            | -Dhttp.keepAlive.time.proxy=-200    | default timeout set to 5
    *       |                                                         | (PROXY_200_NEGATIVE)                |
    *----------------------------------------------------------------------------------------------------------------------------
    *   86  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=-100 &&       |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200           | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)|
    *------------------------------------------------------------------------------------------------------------------------------
    *   87  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=0      | close connection immediately
    *       |                                                         | (SERVER_ZERO)                       |
    *---------------------------------------------------------------------------------------------------------------------------------
    *   88  |No Input (NI)                                            | -Dhttp.keepAlive.time.proxy=0       | default timeout set to 5
    *       |                                                         | (PROXY_ZERO)                        |
    *---------------------------------------------------------------------------------------------------------------------------------
    *   89  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)         |
    *---------------------------------------------------------------------------------------------------------------------------------
    *   90  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200    | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200_NEGATIVE) |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   91  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   92  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | Timeout set to 100
    *       |                                                         | (SERVER_100 && PROXY_ZERO)          |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   93  |No Input (NI)                                            | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200)          |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   94  |No Input (NI)                                            |-Dhttp.keepAlive.time.server=100 &&  |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200     | Timeout set to 100
    *       |                                                         | (SERVER_100 && PROXY_200_NEGATIVE)  |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   95  |No Input (NI)                                            |-Dhttp.keepAlive.time.server=-100 && |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=200      | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)  |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   96  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    | No Input Provided (NI)              | default timeout set to 5
    *--------------------------------------------------------------------------------------------------------------------------------
    *   97  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=100     | Timeout set to 100
    *       |                                                         | (SERVER_100)                        |
    *--------------------------------------------------------------------------------------------------------------------------------
    *   98  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.proxy=200      | default timeout set to 5
    *       |                                                         | (PROXY_200)                         |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *   99  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=100 &&  |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=200      | Timeout set to 100
    *       |                                                         |(SERVER_100 && PROXY_200)            |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  100  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=-100    | default timeout set to 5
    *       |                                                         |(SERVER_100_NEGATIVE)                |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  101  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.proxy=-200     | default timeout set to 5
    *       |                                                         |(PROXY_200_NEGATIVE)                 |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  102  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=-100 &&        |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200            | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)|
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  103  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=0       | close connection immediately
    *       |                                                         | (SERVER_ZERO)                       |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  104  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.proxy=0        | default timeout set to 5
    *       |                                                         | (PROXY_ZERO)                        |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  105  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=0 &&    |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)         |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  106  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=0 &&    |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200     | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO_NEGATIVE)|
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  107  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=-100 && |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=0        | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  108  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=100 &&  |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=0        | Timeout set to 100
    *       |                                                         | (SERVER_100 && PROXY_ZERO)          |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  109  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=0 &&    |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=200      | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200)          |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  110  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=100 &&  |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200     | Timeout set to 100
    *       |                                                         |(SERVER_100 && PROXY_200_NEGATIVE)   |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  111  |Connection: keep-alive\r\nKeep-alive: timeout=-20 (F)    |-Dhttp.keepAlive.time.server=-100 && |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=200      | default timeout set to 5
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)  |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  112  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | No Input Provided (NI)              | close connection immediately
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  113  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=100    | close connection immediately
    *       |                                                         | (SERVER_100)                        |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  114  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.proxy=200     | close connection immediately
    *       |                                                         | (PROXY_200)                         |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  115  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_200)           |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  116  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=-100   | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE)               |
    *------------------------------------------------------------------------------------------------------------------------------------
    *  117  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.proxy=-200    | close connection immediately
    *       |                                                         | (PROXY_200_NEGATIVE)                |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  118  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      |-Dhttp.keepAlive.time.server=-100 &&         |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200             | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE) |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  119  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=0      | close connection immediately
    *       |                                                         | (SERVER_ZERO)                       |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  120  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (PROXY_ZERO)                        |
    *------------------------------------------------------------------------------------------------------------------------------------
    *  121  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)         |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  122  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200    | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200_NEGATIVE) |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  123  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO) |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  124  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0       | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_ZERO)          |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  125  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=0 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200)          |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  126  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=100 && |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200    | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_200_NEGATIVE)  |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  127  |Connection: keep-alive\r\nKeep-alive: timeout=0 (G)      | -Dhttp.keepAlive.time.server=-100 &&|
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200     | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)  |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  128  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| No Input Provided (NI)                | default timeout set to 60
    ---------------------------------------------------------------------------------------------------------------------------------------
    *  129  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=100      | default timeout set to 60
    *       |                                                         | (SERVER_100)                          |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  130  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.proxy=200       | Timeout set to 200
    *       |                                                         | (PROXY_200)                           |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  131  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=100 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200       | Timeout set to 200
    *       |                                                         | (SERVER_100 && PROXY_200)             |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  132  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=-100     | default timeout set to 60
    *       |                                                         | (SERVER_100_NEGATIVE)                 |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  133  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.proxy=-200      | default timeout set to 60
    *       |                                                         | (PROXY_200_NEGATIVE)                  |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  134  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)|-Dhttp.keepAlive.time.server=-100 &&        |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200            | default timeout set to 60
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE)|
    *---------------------------------------------------------------------------------------------------------------------------------
    *  135  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=0        | default timeout set to 60
    *       |                                                         | (SERVER_ZERO)                         |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  136  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (PROXY_ZERO)                          |
    *----------------------------------------------------------------------------------------------------------------------------------
    *  137  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=0 &&     |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)           |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  138  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=0 &&     |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200      | default timeout set to 60
    *       |                                                         | (SERVER_ZERO && PROXY_200_NEGATIVE)   |
    *---------------------------------------------------------------------------------------------------------------------------------------
    *  139  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=-100 &&  |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO)   |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  140  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=100 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_ZERO)            |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  141  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)| -Dhttp.keepAlive.time.server=0 &&     |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200       | Timeout set to 20
    *       |                                                         | (SERVER_ZERO && PROXY_200)            |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  142  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)|-Dhttp.keepAlive.time.server=100 &&    |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=-200       | default timeout set to 60
    *       |                                                         | (SERVER_100 && PROXY_200_NEGATIVE)    |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  143  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=-20 (H)|-Dhttp.keepAlive.time.server=-100 &&   |
    *       |                                                         |-Dhttp.keepAlive.time.proxy=200        | Timeout set to 200
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)    |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  144  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | No Input Provided (NI)                | close connection immediately
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  145  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=100      | close connection immediately
    *       |                                                         | (SERVER_100)                          |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  146  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.proxy=200       | close connection immediately
    *       |                                                         | (PROXY_200)                           |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  147  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=100 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200       | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_200)             |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  148  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=-100     | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE)                 |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  149  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.proxy=-200      | close connection immediately
    *       |                                                         | (PROXY_200_NEGATIVE)                  |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  150  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=-100 &&        |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200            | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200_NEGATIVE) |
    *------------------------------------------------------------------------------------------------------------------------------------
    *  151  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=0        | close connection immediately
    *       |                                                         | (SERVER_ZERO)                         |
    *-----------------------------------------------------------------------------------------------------------------------------------
    *  152  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (PROXY_ZERO)                          |
    *---------------------------------------------------------------------------------------------------------------------------------
    *  153  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=0 &&     |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_ZERO)           |
    *------------------------------------------------------------------------------------------------------------------------------------
    *  154  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=0 &&     |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200      | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200_NEGATIVE)   |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  155  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=-100 &&  |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_ZERO)   |
    *-------------------------------------------------------------------------------------------------------------------------------------
    *  156  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=100 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=0         | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_ZERO)            |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  157  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=0 &&     |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200       | close connection immediately
    *       |                                                         | (SERVER_ZERO && PROXY_200)            |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  158  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=100 &&   |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=-200      | close connection immediately
    *       |                                                         | (SERVER_100 && PROXY_200_NEGATIVE)    |
    *--------------------------------------------------------------------------------------------------------------------------------------
    *  159  |Proxy-Connection:keep-alive\r\nKeep-alive:timeout=0 (I)  | -Dhttp.keepAlive.time.server=-100 &&  |
    *       |                                                         | -Dhttp.keepAlive.time.proxy=200       | close connection immediately
    *       |                                                         | (SERVER_100_NEGATIVE && PROXY_200)    |
    *--------------------------------------------------------------------------------------------------------------------------------------
    */

    private static final String NOT_CACHED = "NotCached";
    private static final String CLIENT_SEPARATOR = ";";
    private static final String NEW_LINE = "\r\n";

    private static final String NI = "NO_INPUT";

    private static final String CONNECTION_KEEP_ALIVE_ONLY = "Connection: keep-alive";
    private static final String PROXY_CONNECTION_KEEP_ALIVE_ONLY = "Proxy-Connection: keep-alive";
    private static final String KEEP_ALIVE_TIMEOUT_NEG = "Keep-alive: timeout=-20";
    private static final String KEEP_ALIVE_TIMEOUT_ZERO = "Keep-alive: timeout=0";
    private static final String KEEP_ALIVE_TIMEOUT = "Keep-alive: timeout=20";
    private static final String KEEP_ALIVE_PROXY_TIMEOUT = "Keep-alive: timeout=120";
    private static final String CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_NEGATIVE = "http.keepAlive.time.server=-100";
    private static final String CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_NEGATIVE = "http.keepAlive.time.proxy=-200";
    private static final String CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_ZERO = "http.keepAlive.time.server=0";
    private static final String CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_ZERO = "http.keepAlive.time.proxy=0";
    private static final String CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_POSITIVE = "http.keepAlive.time.server=100";
    private static final String CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_POSITIVE = "http.keepAlive.time.proxy=200";
    private static final String CONNECTION_KEEP_ALIVE_WITH_TIMEOUT = CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT;

    private static final String SERVER_100_NEGATIVE = CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_NEGATIVE;
    private static final String PROXY_200_NEGATIVE = CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_NEGATIVE;
    private static final String SERVER_ZERO = CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_ZERO;
    private static final String PROXY_ZERO = CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_ZERO;
    private static final String SERVER_100 = CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_POSITIVE;
    private static final String PROXY_200 = CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_POSITIVE;

    private static final String[] serverScenarios = {
        CONNECTION_KEEP_ALIVE_ONLY,
        CONNECTION_KEEP_ALIVE_WITH_TIMEOUT,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + CONNECTION_KEEP_ALIVE_ONLY,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_PROXY_TIMEOUT,
        NI,
        CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_NEG,
        CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_ZERO,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_NEG,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_ZERO
    };

    private static final String[] clientScenarios = {
        NI,
        SERVER_100,
        PROXY_200,
        SERVER_100 + CLIENT_SEPARATOR + PROXY_200,
        SERVER_100_NEGATIVE,
        PROXY_200_NEGATIVE,
        SERVER_100_NEGATIVE + CLIENT_SEPARATOR + PROXY_200_NEGATIVE,
        SERVER_ZERO,
        PROXY_ZERO,
        SERVER_ZERO + CLIENT_SEPARATOR + PROXY_ZERO,
        SERVER_ZERO + CLIENT_SEPARATOR + PROXY_200_NEGATIVE,
        SERVER_100_NEGATIVE + CLIENT_SEPARATOR + PROXY_ZERO,
        SERVER_100 + CLIENT_SEPARATOR + PROXY_ZERO,
        SERVER_ZERO + CLIENT_SEPARATOR + PROXY_200,
        SERVER_100 + CLIENT_SEPARATOR + PROXY_200_NEGATIVE,
        SERVER_100_NEGATIVE + CLIENT_SEPARATOR + PROXY_200
    };

    private static final int[] expectedValues = {
          5, 100,   5, 100,   5,   5,   5,   0,   5,   0,   0,   5,  100,    0,   100,    5,
         20,  20,  20,  20,  20,  20,  20,  20,  20,  20 , 20,  20,   20,   20,    20,   20,
         60,  60, 200, 200,  60,  60,  60,  60,   0,   0,  60,   0,    0,  200,    60,  200,
         60,  60, 200, 200,  60,  60,  60,  60,   0,   0,  60,   0,    0,  200,    60,  200,
        120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120,  120,  120,   120,  120,
          5, 100,   5, 100,   5,   5,   5,   0,   5,   0,   0,   5,  100,    0,   100,    5,
          5, 100,   5, 100,   5,   5,   5,   0,   5,   0,   0,   5,  100,    0,   100,    5,
          0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,    0,    0,     0,    0,
         60,  60, 200, 200,  60,  60,  60,  60,   0,   0,  60,   0,    0,  200,    60,  200,
          0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,    0,    0,     0,    0,
    };

    private static KeepAliveCache keepAliveCache;

    private static Constructor<?> keepAliveKeyClassconstructor;

    // variables set by server thread
    private volatile int serverPort;
    private volatile boolean isProxySet;

    private CountDownLatch serverLatch = new CountDownLatch(1);

    /*
     * setting of client properties -Dhttp.keepAlive.time.server and -Dhttp.keepAlive.time.proxy is handled through this method.
     * There are 16 client scenarios in total starting with scenarioNumber 0(zero) and ending with 15.
     * Server Scenarios are grouped into batch of 16 scenarios.
     * There are 10 batches in total and each batch contains 16 scenarios so 10 * 16 = 160 scenarios in total.
     * 16 Client Scenarios are used repeatedly for every server scenario batch.
     * for serverscenario[0],serverscenario[16],serverscenario[32] ... serverscenario[144] is mapped to clientscenario[0]
     * for serverscenario[1],serverscenario[17],serverscenario[33] ... serverscenario[145] is mapped to clientscenario[1]
     * for serverscenario[2],serverscenario[18],serverscenario[34] ... serverscenario[146] is mapped to clientscenario[2]
     * ...
     * for serverscenario[15],serverscenario[31],serverscenario[47] ... serverscenario[159] is mapped to clientscenario[15]
     */
    private static String getClientScenario(int scenarioNumber) {
        return clientScenarios[scenarioNumber % 16];
    }

    /*
     * Returns SERVER_RESPONSE as String based on integer inputParameter scenarioNumber.
     * Server Scenarios are grouped into batch of 16 scenarios starting with scenarioNumber 0 (zero)
     * so there are 10 batches in total and each batch contains 16 scenarios so 10 * 16 = 160 scenarios in total.
     * For each batch of 16 scenarios, there will be common SERVER_RESPONSE for all 16 scenarios in batch.
     * for scenario numbers from 0 to 15 server  response is: Connection:keep-alive
     * for scenario numbers from 16 to 31 server response is: SERVER_RESPONSE=Connection: keep-alive\r\nKeep-alive: timeout=20
     * for scenario numbers from 32 to 47 server response is: SERVER_RESPONSE=Proxy-Connection: keep-alive
     * for scenario numbers from 48 to 63 server response is: SERVER_RESPONSE=Connection:keep-alive\r\nProxy-connection:keep-alive
     * for scenario numbers from 64 to 79 server response is: SERVER_RESPONSE=Proxy-connection:keep-alive\r\nKeep-alive:timeout=120
     * for scenario numbers from 80 to 95 server response is: SERVER_RESPONSE=No Input
     * for scenario numbers from 96 to 111 server response is: SERVER_RESPONSE=Connection: keep-alive\r\nKeep-alive: timeout=-20
     * for scenario numbers from 112 to 127 server response is: Connection: keep-alive\r\nKeep-alive: timeout=0
     * for scenario numbers from 128 to 143 server response is: Proxy-connection:keep-alive\r\nKeep-alive:timeout=-20
     * for scenario numbers from 144 to 159 server response is: Proxy-connection:keep-alive\r\nKeep-alive:timeout=0
     */
    private static String getServerScenario(int scenarioNumber) {
        return serverScenarios[scenarioNumber >> 4];
    }

    private void readAll(Socket s) throws IOException {
        byte[] buf = new byte[128];
        int c;
        String request = "";
        InputStream is = s.getInputStream();
        while ((c = is.read(buf)) > 0) {
            request += new String(buf, 0, c, StandardCharsets.US_ASCII);
            if (request.contains("\r\n\r\n")) {
                return;
            }
        }
        if (c == -1) {
            throw new IOException("Socket closed");
        }
    }

    private void executeServer(int scenarioNumber) throws IOException {
        String serverScenarioContent = null;
        if (!getServerScenario(scenarioNumber).equalsIgnoreCase(NI)) {
            serverScenarioContent = getServerScenario(scenarioNumber) + NEW_LINE;
            // isProxySet should be set before Server is moved to Listen State.
            isProxySet = serverScenarioContent.contains("Proxy");
        }
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            serverPort = serverSocket.getLocalPort();
            serverLatch.countDown();

            // Server will be waiting for clients to connect.
            try (Socket socket = serverSocket.accept()) {
                readAll(socket);
                try (OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream())) {
                    String BODY = "SERVER REPLY: Hello world";
                    String CLEN = "Content-Length: " + BODY.length() + NEW_LINE;

                    // send the header
                    out.write("HTTP/1.1 200 OK\r\n");
                    out.write("Content-Type: text/plain; charset=iso-8859-1\r\n");

                    // append each scenario content from array.
                    if (serverScenarioContent != null) {
                        out.write(serverScenarioContent);
                    }
                    out.write(CLEN);
                    out.write(NEW_LINE);
                    out.write(BODY);
                }
            }
        }
    }

    private Thread startServer(int scenarioNumber) {
        Thread server = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                   executeServer(scenarioNumber);
                } catch (IOException e) {
                   e.printStackTrace();
                }
            }
        }, "SERVER");
        server.start();
        return server;
    }

    private void fetchInfo(int scenarioNumber, HttpURLConnection httpUrlConnection) throws Exception {
        Object expectedKeepAliveKey = keepAliveKeyClassconstructor.newInstance(httpUrlConnection.getURL(), null);
        Object clientVectorObjectInMap = keepAliveCache.get(expectedKeepAliveKey);
        System.out.println("ClientVector for KeepAliveKey:" + clientVectorObjectInMap);
        HttpClient httpClientCached = keepAliveCache.get(httpUrlConnection.getURL(), null);
        System.out.println("HttpClient in Cache:" + httpClientCached);

        if (httpClientCached != null) {
            System.out.println("KeepingAlive:" + httpClientCached.isKeepingAlive());
            System.out.println("UsingProxy:" + httpClientCached.getUsingProxy());
            System.out.println("ProxiedHost:" + httpClientCached.getProxyHostUsed());
            System.out.println("ProxiedPort:" + httpClientCached.getProxyPortUsed());
            Class<?> clientVectorClass = Class.forName("sun.net.www.http.KeepAliveCache$ClientVector");
            Field napField = clientVectorClass.getDeclaredField("nap");
            napField.setAccessible(true);
            int napValue = (int) napField.get(clientVectorObjectInMap);
            int actualValue = napValue / 1000;
            if (expectedValues[scenarioNumber] == actualValue) {
                System.out.printf("Cache time:%d\n", actualValue);
            } else {
                throw new RuntimeException("Sleep time of " + actualValue + " not expected (" + expectedValues[scenarioNumber] + ")");
            }
        } else {
            if (expectedValues[scenarioNumber] == 0) {
                System.out.println("Connection not cached.");
            } else {
                throw new RuntimeException("Connection was not cached although expected with sleep time of:" + expectedValues[scenarioNumber]);
            }
        }
    }

    private void connectToServerURL(int scenarioNumber) throws Exception {
        // wait until ServerSocket moves to listening state.
        serverLatch.await();
        URL url = URIBuilder.newBuilder().scheme("http").loopback().port(serverPort).toURL();
        System.out.println("connecting client to server URL: " + url + ", isProxySet: " + isProxySet);
        HttpURLConnection httpUrlConnection = null;

        // isProxySet is set to true when Expected Server Response contains Proxy-Connection header.
        if (isProxySet) {
            httpUrlConnection = (HttpURLConnection) url
                .openConnection(new Proxy(Type.HTTP, new InetSocketAddress("localhost", serverPort)));
        } else {
            httpUrlConnection = (HttpURLConnection) url.openConnection();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(httpUrlConnection.getInputStream());
        try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while (true) {
                String eachLine = bufferedReader.readLine();
                if (eachLine == null) {
                    break;
                }
                System.out.println(eachLine);
            }
        }
        for (Entry<String, List<String>> header : httpUrlConnection.getHeaderFields().entrySet()) {
            System.out.println(header.getKey() + "=" + header.getValue());
        }
        fetchInfo(scenarioNumber, httpUrlConnection);
    }

    private void runScenario(int scenarioNumber) throws Exception {
        System.out.println("Testing scenario " + scenarioNumber);
        System.out.println("Server: " + getServerScenario(scenarioNumber));
        System.out.println("Client: " + getClientScenario(scenarioNumber));
        System.out.println("Expected Cache Time: " + (expectedValues[scenarioNumber] == 0 ? NOT_CACHED : expectedValues[scenarioNumber]));
        System.out.println();
        Thread serverThread = startServer(scenarioNumber);
        connectToServerURL(scenarioNumber);
        serverThread.join();
        System.out.println();
    }

    private static void collectPropertyValue(List<String> props, String prop) {
        var val = System.getProperty(prop);
        if (val != null) {
            props.add(prop + "=" + val);
        }
    }

    private static void initialize(int scenarioNumber) throws Exception {
        List<String> props = new ArrayList<>();

        collectPropertyValue(props, "http.keepAlive.time.server");
        collectPropertyValue(props, "http.keepAlive.time.proxy");
        collectPropertyValue(props, "http.proxyPort");
        collectPropertyValue(props, "http.proxyHost");

        if (props.size() > 0) {
            System.out.println("Existing System Properties:");
            for (String prop : props) {
                System.out.println(prop);
            }
        }
        System.setProperty("java.net.useSystemProxies", "false");
        System.clearProperty("http.keepAlive.time.server");
        System.clearProperty("http.keepAlive.time.proxy");

        // fetch clientScenario and set system properties
        if (!getClientScenario(scenarioNumber).equalsIgnoreCase(NI)) {
            for (String clientScenarioString : getClientScenario(scenarioNumber).split(CLIENT_SEPARATOR)) {
                String[] kv = clientScenarioString.split("=");
                System.setProperty(kv[0], kv[1]);
            }
        }

        Field keepAliveField = sun.net.www.http.HttpClient.class.getDeclaredField("kac");
        keepAliveField.setAccessible(true);
        keepAliveCache = (KeepAliveCache) keepAliveField.get(null);
        System.out.println("KeepAliveCache: " + keepAliveCache);
        keepAliveKeyClassconstructor = Class.forName("sun.net.www.http.KeepAliveKey").getDeclaredConstructors()[0];
        keepAliveKeyClassconstructor.setAccessible(true);

        Logger logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
        logger.setLevel(Level.FINEST);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.FINEST);
        logger.addHandler(h);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage:java KeepAliveTest.java <scenarioNumber>");
        }
        if (args[0].startsWith("c")) {
            // all subtests of a specific client scenario
            int clientScenarioNumber = Integer.valueOf(args[0].substring(1));
            if (clientScenarioNumber < 0 || clientScenarioNumber > 15) {
                throw new IllegalArgumentException("Client Scenario " + clientScenarioNumber + " does not exist");
            }
            initialize(clientScenarioNumber);
            for (int i = clientScenarioNumber; i < 160; i += 16) {
                new KeepAliveTest().runScenario(i);
            }
        } else {
            // an individual test scenario
            int scenarioNumber = Integer.valueOf(args[0]);
            initialize(scenarioNumber);
            new KeepAliveTest().runScenario(scenarioNumber);
        }
    }
}
