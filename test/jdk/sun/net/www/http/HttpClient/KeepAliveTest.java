/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8291226 8291638
 * @modules java.base/sun.net:+open
 *          java.base/sun.net.www.http:+open
 *          java.base/sun.net.www:+open
 *          java.base/sun.net.www.protocol.http:+open
 * @run main/othervm KeepAliveTest 0
 * @run main/othervm KeepAliveTest 1
 * @run main/othervm KeepAliveTest 2
 * @run main/othervm KeepAliveTest 3
 * @run main/othervm KeepAliveTest 4
 * @run main/othervm KeepAliveTest 5
 * @run main/othervm KeepAliveTest 6
 * @run main/othervm KeepAliveTest 7
 * @run main/othervm KeepAliveTest 8
 * @run main/othervm KeepAliveTest 9
 * @run main/othervm KeepAliveTest 10
 * @run main/othervm KeepAliveTest 11
 * @run main/othervm KeepAliveTest 12
 * @run main/othervm KeepAliveTest 13
 * @run main/othervm KeepAliveTest 14
 * @run main/othervm KeepAliveTest 15
 * @run main/othervm KeepAliveTest 16
 * @run main/othervm KeepAliveTest 17
 * @run main/othervm KeepAliveTest 18
 * @run main/othervm KeepAliveTest 19
 * @run main/othervm KeepAliveTest 20
 * @run main/othervm KeepAliveTest 21
 * @run main/othervm KeepAliveTest 22
 * @run main/othervm KeepAliveTest 23
 * @run main/othervm KeepAliveTest 24
 * @run main/othervm KeepAliveTest 25
 * @run main/othervm KeepAliveTest 26
 * @run main/othervm KeepAliveTest 27
 * @run main/othervm KeepAliveTest 28
 * @run main/othervm KeepAliveTest 29
 * @run main/othervm KeepAliveTest 30
 * @run main/othervm KeepAliveTest 31
 * @run main/othervm KeepAliveTest 32
 * @run main/othervm KeepAliveTest 33
 * @run main/othervm KeepAliveTest 34
 * @run main/othervm KeepAliveTest 35
 * @run main/othervm KeepAliveTest 36
 * @run main/othervm KeepAliveTest 37
 * @run main/othervm KeepAliveTest 38
 * @run main/othervm KeepAliveTest 39
 * @run main/othervm KeepAliveTest 40
 * @run main/othervm KeepAliveTest 41
 * @run main/othervm KeepAliveTest 42
 * @run main/othervm KeepAliveTest 43
 * @run main/othervm KeepAliveTest 44
 * @run main/othervm KeepAliveTest 45
 * @run main/othervm KeepAliveTest 46
 * @run main/othervm KeepAliveTest 47
 * @run main/othervm KeepAliveTest 48
 * @run main/othervm KeepAliveTest 49
 * @run main/othervm KeepAliveTest 50
 * @run main/othervm KeepAliveTest 51
 * @run main/othervm KeepAliveTest 52
 * @run main/othervm KeepAliveTest 53
 * @run main/othervm KeepAliveTest 54
 * @run main/othervm KeepAliveTest 55
 * @run main/othervm KeepAliveTest 56
 * @run main/othervm KeepAliveTest 57
 * @run main/othervm KeepAliveTest 58
 * @run main/othervm KeepAliveTest 59
 * @run main/othervm KeepAliveTest 60
 * @run main/othervm KeepAliveTest 61
 * @run main/othervm KeepAliveTest 62
 * @run main/othervm KeepAliveTest 63
 * @run main/othervm KeepAliveTest 64
 * @run main/othervm KeepAliveTest 65
 * @run main/othervm KeepAliveTest 66
 * @run main/othervm KeepAliveTest 67
 * @run main/othervm KeepAliveTest 68
 * @run main/othervm KeepAliveTest 69
 * @run main/othervm KeepAliveTest 70
 * @run main/othervm KeepAliveTest 71
 * @run main/othervm KeepAliveTest 72
 * @run main/othervm KeepAliveTest 73
 * @run main/othervm KeepAliveTest 74
 * @run main/othervm KeepAliveTest 75
 * @run main/othervm KeepAliveTest 76
 * @run main/othervm KeepAliveTest 77
 * @run main/othervm KeepAliveTest 78
 * @run main/othervm KeepAliveTest 79
 * @run main/othervm KeepAliveTest 80
 * @run main/othervm KeepAliveTest 81
 * @run main/othervm KeepAliveTest 82
 * @run main/othervm KeepAliveTest 83
 * @run main/othervm KeepAliveTest 84
 * @run main/othervm KeepAliveTest 85
 * @run main/othervm KeepAliveTest 86
 * @run main/othervm KeepAliveTest 87
 * @run main/othervm KeepAliveTest 88
 * @run main/othervm KeepAliveTest 89
 * @run main/othervm KeepAliveTest 90
 * @run main/othervm KeepAliveTest 91
 * @run main/othervm KeepAliveTest 92
 * @run main/othervm KeepAliveTest 93
 * @run main/othervm KeepAliveTest 94
 * @run main/othervm KeepAliveTest 95
 * @run main/othervm KeepAliveTest 96
 * @run main/othervm KeepAliveTest 97
 * @run main/othervm KeepAliveTest 98
 * @run main/othervm KeepAliveTest 99
 * @run main/othervm KeepAliveTest 100
 * @run main/othervm KeepAliveTest 101
 * @run main/othervm KeepAliveTest 102
 * @run main/othervm KeepAliveTest 103
 * @run main/othervm KeepAliveTest 104
 * @run main/othervm KeepAliveTest 105
 * @run main/othervm KeepAliveTest 106
 * @run main/othervm KeepAliveTest 107
 * @run main/othervm KeepAliveTest 108
 * @run main/othervm KeepAliveTest 109
 * @run main/othervm KeepAliveTest 110
 * @run main/othervm KeepAliveTest 111
 * @run main/othervm KeepAliveTest 112
 * @run main/othervm KeepAliveTest 113
 * @run main/othervm KeepAliveTest 114
 * @run main/othervm KeepAliveTest 115
 * @run main/othervm KeepAliveTest 116
 * @run main/othervm KeepAliveTest 117
 * @run main/othervm KeepAliveTest 118
 * @run main/othervm KeepAliveTest 119
 * @run main/othervm KeepAliveTest 120
 * @run main/othervm KeepAliveTest 121
 * @run main/othervm KeepAliveTest 122
 * @run main/othervm KeepAliveTest 123
 * @run main/othervm KeepAliveTest 124
 * @run main/othervm KeepAliveTest 125
 * @run main/othervm KeepAliveTest 126
 * @run main/othervm KeepAliveTest 127
 * @run main/othervm KeepAliveTest 128
 * @run main/othervm KeepAliveTest 129
 * @run main/othervm KeepAliveTest 130
 * @run main/othervm KeepAliveTest 131
 * @run main/othervm KeepAliveTest 132
 * @run main/othervm KeepAliveTest 133
 * @run main/othervm KeepAliveTest 134
 * @run main/othervm KeepAliveTest 135
 * @run main/othervm KeepAliveTest 136
 * @run main/othervm KeepAliveTest 137
 * @run main/othervm KeepAliveTest 138
 * @run main/othervm KeepAliveTest 139
 * @run main/othervm KeepAliveTest 140
 * @run main/othervm KeepAliveTest 141
 * @run main/othervm KeepAliveTest 142
 * @run main/othervm KeepAliveTest 143
 * @run main/othervm KeepAliveTest 144
 * @run main/othervm KeepAliveTest 145
 * @run main/othervm KeepAliveTest 146
 * @run main/othervm KeepAliveTest 147
 * @run main/othervm KeepAliveTest 148
 * @run main/othervm KeepAliveTest 149
 * @run main/othervm KeepAliveTest 150
 * @run main/othervm KeepAliveTest 151
 * @run main/othervm KeepAliveTest 152
 * @run main/othervm KeepAliveTest 153
 * @run main/othervm KeepAliveTest 154
 * @run main/othervm KeepAliveTest 155
 * @run main/othervm KeepAliveTest 156
 * @run main/othervm KeepAliveTest 157
 * @run main/othervm KeepAliveTest 158
 * @run main/othervm KeepAliveTest 159
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
    private static final Logger logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
    private static final String NOT_CACHED = "NotCached";
    private static final String CLIENT_SEPARATOR = ";";
    private static final String NEW_LINE = "\r\n";
    private volatile int SERVER_PORT = 0;
    /*
     * isProxySet is shared variable between server thread and client thread(main) and it should be set and reset to false for each and
     * every scenario.
     * isProxySet variable should be set by server thread before proceeding to client thread(main).
     */
    private volatile boolean isProxySet = false;
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
    private static final String CONNECTION_KEEP_ALIVE_WITH_TIMEOUT = CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE
        + KEEP_ALIVE_TIMEOUT;
   /*
    * Following Constants represents Client Side Properties and is used as reference in below table as
    * CLIENT_INPUT_CONSTANT_NAMES
    */
    private static final String SERVER_100_NEGATIVE = CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_NEGATIVE;
    private static final String PROXY_200_NEGATIVE = CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_NEGATIVE;
    private static final String SERVER_ZERO = CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_ZERO;
    private static final String PROXY_ZERO = CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_ZERO;
    private static final String SERVER_100 = CLIENT_HTTP_KEEP_ALIVE_TIME_SERVER_POSITIVE;
    private static final String PROXY_200 = CLIENT_HTTP_KEEP_ALIVE_TIME_PROXY_POSITIVE;

   /*
    * CONSTANTS A,B,C,D,E,NI,F,G,H,I represents ServerScenarios and is used as reference in below table
    * as SERVER_RESPONSE_CONSTANT_NAME
    */
    private static final String A = CONNECTION_KEEP_ALIVE_ONLY;
    private static final String B = CONNECTION_KEEP_ALIVE_WITH_TIMEOUT;
    private static final String C = PROXY_CONNECTION_KEEP_ALIVE_ONLY;
    private static final String D = PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + CONNECTION_KEEP_ALIVE_ONLY;
    private static final String E = C + NEW_LINE + KEEP_ALIVE_PROXY_TIMEOUT;
    private static final String NI = "NO_INPUT";
    private static final String F = A + NEW_LINE + KEEP_ALIVE_TIMEOUT_NEG;
    private static final String G = A + NEW_LINE + KEEP_ALIVE_TIMEOUT_ZERO;
    private static final String H = C + NEW_LINE + KEEP_ALIVE_TIMEOUT_NEG;
    private static final String I = C + NEW_LINE + KEEP_ALIVE_TIMEOUT_ZERO;

   /*
    * There are 160 scenarios run by this program.
    * For every scenario there is mapping between serverScenarios[int],clientScenarios[int] and expectedOutput[int]
    *
    * serverScenarios[0] clientScenarios[0] expectedOutput[0]
    * serverScenarios[1] clientScenarios[1] expectedOutput[1]
    * serverScenarios[2] clientScenarios[2] expectedOutput[2]
    *
    * ...
    *
    * serverScenarios[159] cientScenarios[159] expectedOutput[159]
    *
    * whereas serverScenarios[int] is retrieved using getServerScenario(int)
    * whereas clientScenarios[int] is retrieved using clientScenario[getClientScenarioNumber[int]]
    * and
    * expectedOutput[int] is retrieved using expectedOuput[int] directly.
    *
    */

   /* Here is the complete table of server_response, client system properties input and expected cached timeout at client side */
   /* ScNo  |  SERVER RESPONSE (SERVER_RESPONSE_CONSTANT_NAME)| CLIENT SYSTEM PROPERTIES INPUT (CLIENT_INPUT_CONSTANT_NAMES) | EXPECTED CACHED TIMEOUT AT CLIENT SIDE
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

   /* private static final String[] serverScenarios = {
        A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A,
        B, B, B, B, B, B, B, B, B, B,B, B, B, B, B, B,
        C, C, C, C, C, C, C, C, C, C, C, C, C, C, C, C,
        D, D, D, D, D, D, D, D, D, D, D, D, D, D, D, D,
        E, E, E, E, E, E, E, E, E, E, E, E, E, E, E, E,
        NI, NI, NI, NI, NI, NI, NI, NI, NI, NI, NI, NI, NI, NI, NI, NI,
        F, F, F, F, F, F, F, F, F, F, F, F, F, F, F, F,
        G, G, G, G, G, G, G, G, G, G, G, G, G, G, G, G,
        H, H, H, H, H, H, H, H, H, H, H, H, H, H, H, H,
        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I
    }; */
    /*
     * following are client scenarios which are repeated.
     */
    private static final String[] a = {
        NI, SERVER_100,     PROXY_200, SERVER_100 + CLIENT_SEPARATOR + PROXY_200,    SERVER_100_NEGATIVE,
        PROXY_200_NEGATIVE, SERVER_100_NEGATIVE + CLIENT_SEPARATOR + PROXY_200_NEGATIVE,
        SERVER_ZERO,         PROXY_ZERO, SERVER_ZERO + CLIENT_SEPARATOR + PROXY_ZERO,
        SERVER_ZERO + CLIENT_SEPARATOR + PROXY_200_NEGATIVE, SERVER_100_NEGATIVE + CLIENT_SEPARATOR + PROXY_ZERO,
        SERVER_100 + CLIENT_SEPARATOR + PROXY_ZERO, SERVER_ZERO + CLIENT_SEPARATOR + PROXY_200,
        SERVER_100 + CLIENT_SEPARATOR + PROXY_200_NEGATIVE, SERVER_100_NEGATIVE + CLIENT_SEPARATOR + PROXY_200
    };

   /* private String[] clientScenarios = {
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
    }; */

    private static final String[] clientScenarios = {
        a[0] , a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15]
    };

    private static final int[] expectedValues = {
        5,  100,    5, 100,  5,  5,  5,  0,  5,   0,   0,   5,  100,    0,   100,    5,
        20,   20 ,  20,  20, 20, 20, 20, 20, 20,  20 , 20,  20,   20,   20,    20,   20,
        60,   60,  200, 200, 60, 60, 60, 60,  0,   0,  60,   0,    0,  200,    60,  200,
        60,   60,  200, 200, 60, 60, 60, 60,  0,   0,  60,   0,    0,  200,    60,  200,
        120, 120,  120, 120,120,120,120,120,120, 120, 120, 120,  120,  120,   120,  120,
        5,  100,    5, 100,  5,  5,  5,  0,  5,   0,   0,   5,  100,    0,   100,    5,
        5,  100,    5, 100,  5,  5,  5,  0,  5,   0,   0,   5,  100,    0,   100,    5,
        0,    0,    0,   0,  0,  0,  0,  0,  0,   0,   0,   0,    0,    0,     0,    0,
        60,  60,  200, 200, 60, 60, 60, 60,  0,   0,  60,   0,    0,  200,    60,  200,
        0,    0,    0,   0,  0,  0,  0,  0,  0,   0,   0,   0,    0,    0,     0,    0,
    };

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private final CountDownLatch serverCountDownLatch = new CountDownLatch(1);

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
    private int getClientScenarioNumber(int scenarioNumber) {
        return scenarioNumber % 16 ;
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
     * for scenario numbers from 112 to 127 server resonse is: Connection: keep-alive\r\nKeep-alive: timeout=0
     * for scenario numbers from 128 to 143 server response is: Proxy-connection:keep-alive\r\nKeep-alive:timeout=-20
     * for scenario numbers from 144 to 159 server response is: Proxy-connection:keep-alive\r\nKeep-alive:timeout=0
     */
    private String getServerScenario(int scenarioNumber) {
        /*
         *  ServerResponse for scenarios from 0 to 15
         *  SERVER_RESPONSE:Connection:keep-alive
         */
        if(scenarioNumber >= 0 && scenarioNumber <= 15) {
            return A;
        }
        /*
         * ServerResponse for scenarios from 16 to 31
         * SERVER_RESPONSE=Connection: keep-alive\r\nKeep-alive: timeout=20
         */
        else if (scenarioNumber >= 16 && scenarioNumber <= 31){
            return B;
        }
        /*
         * ServerResponse for scenarios from 32 to 47
         * SERVER_RESPONSE=Proxy-Connection: keep-alive
         */
        else if (scenarioNumber >= 32 && scenarioNumber <= 47){
            return C;
        }
        /*
         * ServerResponse for scenarios from 48 to 63
         * SERVER_RESPONSE=Connection:keep-alive\r\nProxy-connection:keep-alive
         */
        else if (scenarioNumber >= 48 && scenarioNumber <= 63){
            return D;
        /*
         * ServerResponse for scenarios from 64 to 79
         * SERVER_RESPONSE=Proxy-connection:keep-alive\r\nKeep-alive:timeout=120
         */
        } else if (scenarioNumber >= 64 && scenarioNumber <= 79){
            return E;
        }
        /*
         * ServerResponse for scenarios from 80 to 95
         * SERVER_RESPONSE=No Input
         */
        else if (scenarioNumber >= 80 && scenarioNumber <= 95){
            return NI;
        }
        /*
         * ServerResponse for scenarios from 96 to 111
         * SERVER_RESPONSE=Connection: keep-alive\r\nKeep-alive: timeout=-20
         */
        else if (scenarioNumber >= 96 && scenarioNumber <= 111){
            return F;
        }
        /*
         * ServerResponse for scenarios from 112 to 127
         * SERVER_RESPONSE=Connection: keep-alive\r\nKeep-alive: timeout=0
         */
        else if (scenarioNumber >= 112 && scenarioNumber <= 127){
            return G;
        }
        /*
         * ServerResponse for scenarios from 128 to 143
         * SERVER_RESPONSE=Proxy-connection:keep-alive\r\nKeep-alive:timeout=-20
         */
        else if (scenarioNumber >= 128 && scenarioNumber <= 143){
            return H;
        }
        /*
         * ServerResponse for scenarios from 144 to 159
         * SERVER_RESPONSE=Proxy-connection:keep-alive\r\nKeep-alive:timeout=0
         */
        else if (scenarioNumber >= 144 && scenarioNumber <= 159){
            return I;
        }
        /*Invalid Case*/
        return null;
    }

    private void startScenario(int scenarioNumber) throws Exception {
        System.out.println("serverScenarios[" + scenarioNumber + "]=" + getServerScenario(scenarioNumber));
        System.out.println("clientScenarios[" + scenarioNumber + "]=" + clientScenarios[getClientScenarioNumber(scenarioNumber)]);
        if(expectedValues[scenarioNumber] == 0) {
            System.out.println("ExpectedOutput=" + NOT_CACHED);
        } else {
            System.out.println("ExpectedOutput=" + expectedValues[scenarioNumber]);
        }
        System.out.println();
        startServer(scenarioNumber);
        runClient(scenarioNumber);
    }

    private void startServer(int scenarioNumber) {
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
            /*
             * isProxySet should be set before Server is moved to Listen State.
             */
            if (serverScenarioContent.contains("Proxy")) {
                isProxySet = true;
            } else {
                isProxySet = false;
            }
        }
        ServerSocket serverSocket = null;
        Socket socket = null;
        OutputStreamWriter out = null;
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(loopback, 0));
            SERVER_PORT = serverSocket.getLocalPort();
            //serverReady = true;
            this.serverCountDownLatch.countDown();
            System.out
                .println("SERVER_PORT= " + SERVER_PORT +" isProxySet=" + isProxySet);
            /*
             * Server will be waiting for clients to connect.
             */
            socket = serverSocket.accept();
            readAll(socket);
            out = new OutputStreamWriter(socket.getOutputStream());
            String BODY = "SERVER REPLY: Hello world";
            String CLEN = "Content-Length: " + BODY.length() + NEW_LINE;
            /* send the header */
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Content-Type: text/plain; charset=iso-8859-1\r\n");
            /*
             * append each scenario content from array.
             */
            if(serverScenarioContent != null) {
                out.write(serverScenarioContent);
            }
            out.write(CLEN);
            out.write(NEW_LINE);
            out.write(BODY);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    private void runClient(int scenarioNumber) throws Exception {
        try {
            connectToServerURL(scenarioNumber);
        } finally {
            System.out.println("client count down latch:" + scenarioNumber);
            this.countDownLatch.countDown();
            System.out.println();
            System.out.println();
        }
    }

    private void connectToServerURL(int scenarioNumber) throws Exception {
        //    System.setProperty("java.net.useSystemProxies", "false");
        //    System.setProperty("http.nonProxyHosts", "");
        //    System.setProperty("http.proxyHost", "localhost");
        //    System.setProperty("http.proxyPort", String.valueOf(SERVER_PORT));
        System.out.println("Following are Existing System Properties if set any");
        System.out.println("http.keepAlive.time.server:" + System.getProperty("http.keepAlive.time.server"));
        System.out.println("http.keepAlive.time.proxy:" + System.getProperty("http.keepAlive.time.proxy"));
        System.setProperty("java.net.useSystemProxies", "false");
        System.out.println("http.proxyPort:"+System.getProperty("http.proxyPort"));
        System.out.println("http.proxyHost:"+System.getProperty("http.proxyHost"));
        System.clearProperty("http.keepAlive.time.server");
        System.clearProperty("http.keepAlive.time.proxy");
        // fetch clientScenearios for each scenarioNumber from array and set it to
        // System property.
        if (!clientScenarios[getClientScenarioNumber(scenarioNumber)].equalsIgnoreCase(NI)) {
            System.out.println("Client Input Parsing");
            for (String clientScenarioString : clientScenarios[getClientScenarioNumber(scenarioNumber)].split(CLIENT_SEPARATOR)) {
                System.out.println(clientScenarioString);
                String key = clientScenarioString.split("=")[0];
                String value = clientScenarioString.split("=")[1];
                System.setProperty(key, value);
            }
        }
        // wait until ServerSocket moves to listening state.
        this.serverCountDownLatch.await();
        System.out.println("client started");
        URL url = URIBuilder.newBuilder().scheme("http").loopback().port(SERVER_PORT).toURL();
        System.out.println("connecting from client to SERVER URL:" + url);
        HttpURLConnection httpUrlConnection = null;
        /*
         * isProxySet is set to true when Expected Server Response contains Proxy-Connection header.
         */
        if (isProxySet) {
            httpUrlConnection = (sun.net.www.protocol.http.HttpURLConnection) url
                .openConnection(new Proxy(Type.HTTP, new InetSocketAddress("localhost", SERVER_PORT)));
        } else {
            httpUrlConnection = (sun.net.www.protocol.http.HttpURLConnection) url.openConnection();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(httpUrlConnection.getInputStream());
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(inputStreamReader);
            while (true) {
                String eachLine = bufferedReader.readLine();
                if (eachLine == null) {
                    break;
                }
                System.out.println(eachLine);
            }
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
        //    System.out.println("ResponseCode:" + httpUrlConnection.getResponseCode());
        //    System.out.println("ResponseMessage:" + httpUrlConnection.getResponseMessage());
        //    System.out.println("Content:" + httpUrlConnection.getContent());
        //    Thread.sleep(2000);
        for (Entry<String, List<String>> header : httpUrlConnection.getHeaderFields().entrySet()) {
            System.out.println(header.getKey() + "=" + header.getValue());
        }
        fetchInfo(scenarioNumber, httpUrlConnection);
    }

    private void fetchInfo(int scenarioNumber, sun.net.www.protocol.http.HttpURLConnection httpUrlConnection)
        throws Exception {
       Field field = Class.forName("sun.net.www.protocol.http.HttpURLConnection").getDeclaredField("http");
       field.setAccessible(true);
       HttpClient httpClient = (HttpClient) field.get(httpUrlConnection);
       //    System.out.println("httpclient=" + httpClient);
       Field keepAliveField = Class.forName("sun.net.www.http.HttpClient").getDeclaredField("kac");
       keepAliveField.setAccessible(true);
       KeepAliveCache keepAliveCache = (KeepAliveCache) keepAliveField.get(httpClient);
       System.out.println("keepAliveCache" + keepAliveCache);
       System.out.println("SERVER URL:" + httpUrlConnection.getURL());
       /*
        * create KeepAliveKey(URL,Object) object and compare created KeepAliveKey and
        * existing using equals() method: KeepAliveKey.equals()
        */
       Class keepAliveKeyClass = Class.forName("sun.net.www.http.KeepAliveKey");
       //    System.out.println("keepAliveKeyClass=" + keepAliveKeyClass);
       Constructor keepAliveKeyClassconstructor = keepAliveKeyClass.getDeclaredConstructors()[0];
       keepAliveKeyClassconstructor.setAccessible(true);
       Object expectedKeepAliveKey = keepAliveKeyClassconstructor.newInstance(httpUrlConnection.getURL(), null);
       System.out.println("ExpectedKeepAliveKey=" + expectedKeepAliveKey);
       Object clientVectorObjectInMap = keepAliveCache.get(expectedKeepAliveKey);
       System.out.println("ClientVector=" + clientVectorObjectInMap);
       HttpClient httpClientCached = keepAliveCache.get(httpUrlConnection.getURL(), null);
       System.out.println("HttpClient in Cache:" + httpClientCached);
       if(httpClientCached != null) {
            System.out.println("KeepingAlive:" + httpClientCached.isKeepingAlive());
            System.out.println("UsingProxy:" + httpClientCached.getUsingProxy());
            System.out.println("ProxiedHost:" + httpClientCached.getProxyHostUsed());
            System.out.println("ProxiedPort:" + httpClientCached.getProxyPortUsed());
            System.out.println("ProxyPortUsingSystemProperty:" + System.getProperty("http.proxyPort"));
            System.out.println("ProxyHostUsingSystemProperty:" + System.getProperty("http.proxyHost"));
            System.out.println("http.keepAlive.time.server=" + System.getProperty("http.keepAlive.time.server"));
            System.out.println("http.keepAlive.time.proxy=" + System.getProperty("http.keepAlive.time.proxy"));
            Class clientVectorClass = Class.forName("sun.net.www.http.ClientVector");
            //      System.out.println("clientVectorClass=" + clientVectorClass);
            Field napField = clientVectorClass.getDeclaredField("nap");
            napField.setAccessible(true);
            int napValue = (int) napField.get(clientVectorObjectInMap);
            int actualValue = napValue / 1000;
            //      System.out.println("nap=" + napValue / 1000);
            System.out.printf("ExpectedOutput:%d ActualOutput:%d ", expectedValues[scenarioNumber], actualValue);
            System.out.println();
            if (expectedValues[scenarioNumber] != actualValue) {
                throw new RuntimeException(
                            "ExpectedOutput:" + expectedValues[scenarioNumber] + " ActualOutput: " + actualValue);
            }
        } else {
            //executed when value is not cached.
            String expected = expectedValues[scenarioNumber] == 0 ? NOT_CACHED
                    : String.valueOf(expectedValues[scenarioNumber]);
            System.out.println("ExpectedOutput:" + expected + " ActualOutput:" + NOT_CACHED);
            if (!expected.equalsIgnoreCase(NOT_CACHED)) {
                    throw new RuntimeException("ExpectedOutput:" + expected + " ActualOutput:" + NOT_CACHED);
            }
       }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage:java KeepAliveTest.java <scenarioNumber>");
        }
        logger.setLevel(Level.FINEST);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.FINEST);
        logger.addHandler(h);
        KeepAliveTest keepAliveTest = new KeepAliveTest();
        if (args.length != 0) {
            keepAliveTest.startScenario(Integer.valueOf(args[0]));
        }
        // make main thread wait until server and client is completed.
        keepAliveTest.countDownLatch.await();
    }
}
