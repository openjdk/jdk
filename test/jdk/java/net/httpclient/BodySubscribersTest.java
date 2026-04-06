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
 * @summary Basic test for the standard BodySubscribers default behavior
 * @bug 8225583 8334028
 * @run junit BodySubscribersTest
 */

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import static java.lang.System.out;
import static java.net.http.HttpResponse.BodySubscribers.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BodySubscribersTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;

    // Supplier of BodySubscriber<?>, with a descriptive name
    static class BSSupplier implements Supplier<BodySubscriber<?>> {
        private final Supplier<BodySubscriber<?>> supplier;
        private final String name;
        private BSSupplier(Supplier<BodySubscriber<?>> supplier, String name) {
            this.supplier = supplier;
            this.name = name;
        }
        static BSSupplier create(String name, Supplier<BodySubscriber<?>> supplier) {
            return new BSSupplier(supplier, name);
        }
        @Override public BodySubscriber<?> get() { return supplier.get(); }
        @Override public String toString() { return name; }
    }

    static class LineSubscriber implements Flow.Subscriber<String> {
        @Override public void onSubscribe(Flow.Subscription subscription) {  }
        @Override public void onNext(String item) { fail(); }
        @Override public void onError(Throwable throwable) { fail(); }
        @Override public void onComplete() { fail(); }
    }

    static class BBSubscriber implements Flow.Subscriber<List<ByteBuffer>> {
        @Override public void onSubscribe(Flow.Subscription subscription) {  }
        @Override public void onNext(List<ByteBuffer> item) { fail(); }
        @Override public void onError(Throwable throwable) { fail(); }
        @Override public void onComplete() { fail(); }
    }

    public static Object[][] bodySubscriberSuppliers() { ;
        List<Supplier<BodySubscriber<?>>> list = List.of(
            BSSupplier.create("ofByteArray",   () -> ofByteArray()),
            BSSupplier.create("ofInputStream", () -> ofInputStream()),
            BSSupplier.create("ofBAConsumer",  () -> ofByteArrayConsumer(ba -> { })),
            BSSupplier.create("ofLines",       () -> ofLines(UTF_8)),
            BSSupplier.create("ofPublisher",   () -> ofPublisher()),
            BSSupplier.create("ofFile",        () -> ofFile(Path.of("f"))),
            BSSupplier.create("ofFile-opts)",  () -> ofFile(Path.of("f"), CREATE)),
            BSSupplier.create("ofString",      () -> ofString(UTF_8)),
            BSSupplier.create("buffering",     () -> buffering(ofByteArray(), 10)),
            BSSupplier.create("discarding",    () -> discarding()),
            BSSupplier.create("mapping",       () -> mapping(ofString(UTF_8), s -> s)),
            BSSupplier.create("replacing",     () -> replacing("hello")),
            BSSupplier.create("fromSubscriber-1",     () -> fromSubscriber(new BBSubscriber())),
            BSSupplier.create("fromSubscriber-2",     () -> fromSubscriber(new BBSubscriber(), s -> s)),
            BSSupplier.create("fromLineSubscriber-1", () -> fromLineSubscriber(new LineSubscriber())),
            BSSupplier.create("fromLineSubscriber-2", () -> fromLineSubscriber(new LineSubscriber(), s -> s, UTF_8, ","))
        );

        return list.stream().map(x -> new Object[] { x }).toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("bodySubscriberSuppliers")
    void nulls(Supplier<BodySubscriber<?>> bodySubscriberSupplier) {
        BodySubscriber<?> bodySubscriber = bodySubscriberSupplier.get();
        boolean subscribed = false;

        do {
            assertNotNull(bodySubscriber.getBody());
            assertNotNull(bodySubscriber.getBody());
            assertNotNull(bodySubscriber.getBody());
            Assertions.assertThrows(NPE, () -> bodySubscriber.onSubscribe(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onSubscribe(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onSubscribe(null));

            Assertions.assertThrows(NPE, () -> bodySubscriber.onNext(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onNext(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onNext(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onNext(null));

            Assertions.assertThrows(NPE, () -> bodySubscriber.onError(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onError(null));
            Assertions.assertThrows(NPE, () -> bodySubscriber.onError(null));

            if (!subscribed) {
                out.println("subscribing");
                // subscribe the Subscriber and repeat
                bodySubscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { /* do nothing */ }
                    @Override public void cancel() { fail(); }
                });
                subscribed = true;
                continue;
            }
            break;
        } while (true);
    }

    @ParameterizedTest
    @MethodSource("bodySubscriberSuppliers")
    void subscribeMoreThanOnce(Supplier<BodySubscriber<?>> bodySubscriberSupplier) {
        BodySubscriber<?> bodySubscriber = bodySubscriberSupplier.get();
        bodySubscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) { /* do nothing */ }
            @Override public void cancel() { fail(); }
        });

        for (int i = 0; i < 5; i++) {
            var subscription = new Flow.Subscription() {
                volatile boolean cancelled;
                @Override public void request(long n) { fail(); }
                @Override public void cancel() { cancelled = true; }
            };
            bodySubscriber.onSubscribe(subscription);
            assertTrue(subscription.cancelled);
        }
    }
}
