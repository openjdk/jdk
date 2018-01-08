/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Function;
import jdk.incubator.http.HttpRequest.BodyPublisher;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;

/*
 * @test
 * @summary Basic test for Flow adapters with generic type parameters
 * @compile FlowAdaptersCompileOnly.java
 */

public class FlowAdaptersCompileOnly {

    static void makesSureDifferentGenericSignaturesCompile() {
        BodyPublisher.fromPublisher(new BBPublisher());
        BodyPublisher.fromPublisher(new MBBPublisher());

        BodyHandler.fromSubscriber(new ListSubscriber());
        BodyHandler.fromSubscriber(new CollectionSubscriber());
        BodyHandler.fromSubscriber(new IterableSubscriber());
        BodyHandler.fromSubscriber(new ObjectSubscriber());

        BodySubscriber.fromSubscriber(new ListSubscriber());
        BodySubscriber.fromSubscriber(new CollectionSubscriber());
        BodySubscriber.fromSubscriber(new IterableSubscriber());
        BodySubscriber.fromSubscriber(new ObjectSubscriber());

        BodyPublisher.fromPublisher(new BBPublisher(), 1);
        BodyPublisher.fromPublisher(new MBBPublisher(), 1);

        BodyHandler.fromSubscriber(new ListSubscriber(), Function.identity());
        BodyHandler.fromSubscriber(new CollectionSubscriber(), Function.identity());
        BodyHandler.fromSubscriber(new IterableSubscriber(), Function.identity());
        BodyHandler.fromSubscriber(new ObjectSubscriber(), Function.identity());

        BodySubscriber.fromSubscriber(new ListSubscriber(), Function.identity());
        BodySubscriber.fromSubscriber(new CollectionSubscriber(), Function.identity());
        BodySubscriber.fromSubscriber(new IterableSubscriber(), Function.identity());
        BodySubscriber.fromSubscriber(new ObjectSubscriber(), Function.identity());
    }

    static class BBPublisher implements Flow.Publisher<ByteBuffer> {
        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) { }
    }

    static class MBBPublisher implements Flow.Publisher<MappedByteBuffer> {
        @Override
        public void subscribe(Flow.Subscriber<? super MappedByteBuffer> subscriber) { }
    }

    static class ListSubscriber implements Flow.Subscriber<List<ByteBuffer>> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(List<ByteBuffer> item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }

    static class CollectionSubscriber implements Flow.Subscriber<Collection<ByteBuffer>> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(Collection<ByteBuffer> item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }

    static class IterableSubscriber implements Flow.Subscriber<Iterable<ByteBuffer>> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(Iterable<ByteBuffer> item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }

    static class ObjectSubscriber implements Flow.Subscriber<Object> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(Object item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }
}
