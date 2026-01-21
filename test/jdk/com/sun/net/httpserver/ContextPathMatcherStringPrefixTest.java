/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static java.net.http.HttpClient.Builder.NO_PROXY;

/*
 * @test
 * @bug 8272758
 * @summary Verifies string prefix matching configured using a system property
 * @build ContextPathMatcherPathPrefixTest
 *        EchoHandler
 * @run junit/othervm
 *      -Dsun.net.httpserver.pathMatcher=stringPrefix
 *      ${test.main.class}
 */

class ContextPathMatcherStringPrefixTest {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().proxy(NO_PROXY).build();

    @AfterAll
    static void stopClient() {
        CLIENT.shutdownNow();
    }

    @Test
    void testContextPathAtRoot() throws Exception {
        try (var infra = new ContextPathMatcherPathPrefixTest.Infra("/")) {
            infra.expect(200, "/foo", "/foo/", "/foo/bar", "/foobar");
        }
    }

    @Test
    void testContextPathAtSubDir() throws Exception {
        try (var infra = new ContextPathMatcherPathPrefixTest.Infra("/foo")) {
            infra.expect(200, "/foo", "/foo/", "/foo/bar", "/foobar");
        }
    }

    @Test
    void testContextPathAtSubDirWithTrailingSlash() throws Exception {
        try (var infra = new ContextPathMatcherPathPrefixTest.Infra("/foo/")) {
            infra.expect(200, "/foo/", "/foo/bar");
            infra.expect(404, "/foo", "/foobar");
        }
    }

}
