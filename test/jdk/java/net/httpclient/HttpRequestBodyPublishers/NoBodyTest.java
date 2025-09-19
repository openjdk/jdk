/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::noBody` behavior
 * @build RecordingSubscriber
 * @run junit NoBodyTest
 */

class NoBodyTest {

    @Test
    void test() throws InterruptedException {

        // Create the publisher
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, 0);

        // Verify the state after `request()`
        subscription.request(Long.MAX_VALUE);
        assertEquals("onComplete", subscriber.invocations.take());

    }

}
