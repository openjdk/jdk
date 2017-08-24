/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.incubator.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.common.Log;

class ResponseProcessors {

    abstract static class AbstractProcessor<T>
        implements HttpResponse.BodyProcessor<T>
    {
        HttpClientImpl client;

        synchronized void setClient(HttpClientImpl client) {
            this.client = client;
        }

        synchronized HttpClientImpl getClient() {
            return client;
        }
    }

    static class ConsumerProcessor extends AbstractProcessor<Void> {
        private final Consumer<Optional<byte[]>> consumer;
        private Flow.Subscription subscription;
        private final CompletableFuture<Void> result = new MinimalFuture<>();

        ConsumerProcessor(Consumer<Optional<byte[]>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public CompletionStage<Void> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] buf = new byte[item.remaining()];
            item.get(buf);
            consumer.accept(Optional.of(buf));
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            consumer.accept(Optional.empty());
            result.complete(null);
        }

    }

    static class PathProcessor extends AbstractProcessor<Path> {

        private final Path file;
        private final CompletableFuture<Path> result = new MinimalFuture<>();

        private Flow.Subscription subscription;
        private FileChannel out;
        private final OpenOption[] options;

        PathProcessor(Path file, OpenOption... options) {
            this.file = file;
            this.options = options;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            try {
                out = FileChannel.open(file, options);
            } catch (IOException e) {
                result.completeExceptionally(e);
                subscription.cancel();
                return;
            }
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            try {
                out.write(item);
            } catch (IOException ex) {
                Utils.close(out);
                subscription.cancel();
                result.completeExceptionally(ex);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable e) {
            result.completeExceptionally(e);
            Utils.close(out);
        }

        @Override
        public void onComplete() {
            Utils.close(out);
            result.complete(file);
        }

        @Override
        public CompletionStage<Path> getBody() {
            return result;
        }
    }

    static class ByteArrayProcessor<T> extends AbstractProcessor<T> {
        private final Function<byte[], T> finisher;
        private final CompletableFuture<T> result = new MinimalFuture<>();
        private final List<ByteBuffer> received = new ArrayList<>();

        private Flow.Subscription subscription;

        ByteArrayProcessor(Function<byte[],T> finisher) {
            this.finisher = finisher;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            // We can handle whatever you've got
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            // incoming buffers are allocated by http client internally,
            // and won't be used anywhere except this place.
            // So it's free simply to store them for further processing.
            if(item.hasRemaining()) {
                received.add(item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            received.clear();
            result.completeExceptionally(throwable);
        }

        static private byte[] join(List<ByteBuffer> bytes) {
            int size = Utils.remaining(bytes);
            byte[] res = new byte[size];
            int from = 0;
            for (ByteBuffer b : bytes) {
                int l = b.remaining();
                b.get(res, from, l);
                from += l;
            }
            return res;
        }

        @Override
        public void onComplete() {
            try {
                result.complete(finisher.apply(join(received)));
                received.clear();
            } catch (IllegalArgumentException e) {
                result.completeExceptionally(e);
            }
        }

        @Override
        public CompletionStage<T> getBody() {
            return result;
        }
    }

    static class MultiProcessorImpl<V> implements HttpResponse.MultiProcessor<MultiMapResult<V>,V> {
        private final MultiMapResult<V> results;
        private final Function<HttpRequest,Optional<HttpResponse.BodyHandler<V>>> pushHandler;
        private final boolean completion; // aggregate completes on last PP received or overall completion

        MultiProcessorImpl(Function<HttpRequest,Optional<HttpResponse.BodyHandler<V>>> pushHandler, boolean completion) {
            this.results = new MultiMapResult<V>(new ConcurrentHashMap<>());
            this.pushHandler = pushHandler;
            this.completion = completion;
        }

        @Override
        public Optional<HttpResponse.BodyHandler<V>> onRequest(HttpRequest request) {
            return pushHandler.apply(request);
        }

        @Override
        public void onResponse(HttpResponse<V> response) {
            results.put(response.request(), CompletableFuture.completedFuture(response));
        }

        @Override
        public void onError(HttpRequest request, Throwable t) {
            results.put(request, MinimalFuture.failedFuture(t));
        }

        @Override
        public CompletableFuture<MultiMapResult<V>> completion(
                CompletableFuture<Void> onComplete, CompletableFuture<Void> onFinalPushPromise) {
            if (completion)
                return onComplete.thenApply((ignored)-> results);
            else
                return onFinalPushPromise.thenApply((ignored) -> results);
        }
    }

    static class MultiFile {

        final Path pathRoot;

        MultiFile(Path destination) {
            if (!destination.toFile().isDirectory())
                throw new UncheckedIOException(new IOException("destination is not a directory"));
            pathRoot = destination;
        }

        Optional<HttpResponse.BodyHandler<Path>> handlePush(HttpRequest request) {
            final URI uri = request.uri();
            String path = uri.getPath();
            while (path.startsWith("/"))
                path = path.substring(1);
            Path p = pathRoot.resolve(path);
            if (Log.trace()) {
                Log.logTrace("Creating file body processor for URI={0}, path={1}",
                             uri, p);
            }
            try {
                Files.createDirectories(p.getParent());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }

            final HttpResponse.BodyHandler<Path> proc =
                 HttpResponse.BodyHandler.asFile(p);

            return Optional.of(proc);
        }
    }

    /**
     * Currently this consumes all of the data and ignores it
     */
    static class NullProcessor<T> extends AbstractProcessor<T> {

        Flow.Subscription subscription;
        final CompletableFuture<T> cf = new MinimalFuture<>();
        final Optional<T> result;

        NullProcessor(Optional<T> result) {
            this.result = result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            // TODO: check whether this should consume the buffer, as in:
            item.position(item.limit());
        }

        @Override
        public void onError(Throwable throwable) {
            cf.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (result.isPresent()) {
                cf.complete(result.get());
            } else {
                cf.complete(null);
            }
        }

        @Override
        public CompletionStage<T> getBody() {
            return cf;
        }
    }
}
