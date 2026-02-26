/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112 8180044 8256459
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 * @build ManyRequests ManyRequests2
 * @run main/othervm/timeout=400 -Dsun.net.httpserver.idleInterval=400 -Dtest.XFixed=true
 *                              -Djdk.httpclient.HttpClient.log=channel ManyRequests2
 * @run main/othervm/timeout=400 -Dsun.net.httpserver.idleInterval=400 -Dtest.XFixed=true -Dtest.insertDelay=true
 *                              -Djdk.httpclient.HttpClient.log=channel ManyRequests2
 * @run main/othervm/timeout=400 -Dsun.net.httpserver.idleInterval=400 -Dtest.XFixed=true -Dtest.chunkSize=64
 *                              -Djdk.httpclient.HttpClient.log=channel ManyRequests2
 * @run main/othervm/timeout=400 -Dsun.net.httpserver.idleInterval=400 -Djdk.internal.httpclient.debug=true
 *                              -Djdk.httpclient.HttpClient.log=channel
 *                              -Dtest.XFixed=true -Dtest.insertDelay=true
 *                              -Dtest.chunkSize=64 ManyRequests2
 * @summary Send a large number of requests asynchronously.
 *          The server echoes back using known content length.
 */
 // * @run main/othervm/timeout=40 -Djdk.httpclient.HttpClient.log=ssl ManyRequests

public class ManyRequests2 {

    public static void main(String[] args) throws Exception {
        ManyRequests.main(args);
    }
}
