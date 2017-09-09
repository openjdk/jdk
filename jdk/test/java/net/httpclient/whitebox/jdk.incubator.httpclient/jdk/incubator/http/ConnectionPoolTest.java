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

package jdk.incubator.http;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import jdk.incubator.http.internal.common.ByteBufferReference;

/**
 * @summary Verifies that the ConnectionPool won't prevent an HttpClient
 *          from being GC'ed. Verifies that the ConnectionPool has at most
 *          one CacheCleaner thread running.
 * @bug 8187044
 * @author danielfuchs
 */
public class ConnectionPoolTest {

    static long getActiveCleaners() throws ClassNotFoundException {
        // ConnectionPool.ACTIVE_CLEANER_COUNTER.get()
        // ConnectionPoolTest.class.getModule().addReads(
        //      Class.forName("java.lang.management.ManagementFactory").getModule());
        return java.util.stream.Stream.of(ManagementFactory.getThreadMXBean()
                .dumpAllThreads(false, false))
              .filter(t -> t.getThreadName().startsWith("HTTP-Cache-cleaner"))
              .count();
    }

    public static void main(String[] args) throws Exception {
        testCacheCleaners();
    }

    public static void testCacheCleaners() throws Exception {
        ConnectionPool pool = new ConnectionPool();
        HttpClient client = new HttpClientStub(pool);
        InetSocketAddress proxy = InetSocketAddress.createUnresolved("bar", 80);
        System.out.println("Adding 10 connections to pool");
        for (int i=0; i<10; i++) {
            InetSocketAddress addr = InetSocketAddress.createUnresolved("foo"+i, 80);
            HttpConnection c1 = new HttpConnectionStub(client, addr, proxy, true);
            pool.returnToPool(c1);
        }
        while (getActiveCleaners() == 0) {
            System.out.println("Waiting for cleaner to start");
            Thread.sleep(10);
        }
        System.out.println("Active CacheCleaners: " + getActiveCleaners());
        if (getActiveCleaners() > 1) {
            throw new RuntimeException("Too many CacheCleaner active: "
                    + getActiveCleaners());
        }
        System.out.println("Removing 9 connections from pool");
        for (int i=0; i<9; i++) {
            InetSocketAddress addr = InetSocketAddress.createUnresolved("foo"+i, 80);
            HttpConnection c2 = pool.getConnection(true, addr, proxy);
            if (c2 == null) {
                throw new RuntimeException("connection not found for " + addr);
            }
        }
        System.out.println("Active CacheCleaners: " + getActiveCleaners());
        if (getActiveCleaners() != 1) {
            throw new RuntimeException("Wrong number of CacheCleaner active: "
                    + getActiveCleaners());
        }
        System.out.println("Removing last connection from pool");
        for (int i=9; i<10; i++) {
            InetSocketAddress addr = InetSocketAddress.createUnresolved("foo"+i, 80);
            HttpConnection c2 = pool.getConnection(true, addr, proxy);
            if (c2 == null) {
                throw new RuntimeException("connection not found for " + addr);
            }
        }
        System.out.println("Active CacheCleaners: " + getActiveCleaners()
                + " (may be 0 or may still be 1)");
        if (getActiveCleaners() > 1) {
            throw new RuntimeException("Too many CacheCleaner active: "
                    + getActiveCleaners());
        }
        InetSocketAddress addr = InetSocketAddress.createUnresolved("foo", 80);
        HttpConnection c = new HttpConnectionStub(client, addr, proxy, true);
        System.out.println("Adding/Removing one connection from pool 20 times in a loop");
        for (int i=0; i<20; i++) {
            pool.returnToPool(c);
            HttpConnection c2 = pool.getConnection(true, addr, proxy);
            if (c2 == null) {
                throw new RuntimeException("connection not found for " + addr);
            }
            if (c2 != c) {
                throw new RuntimeException("wrong connection found for " + addr);
            }
        }
        if (getActiveCleaners() > 1) {
            throw new RuntimeException("Too many CacheCleaner active: "
                    + getActiveCleaners());
        }
        ReferenceQueue<HttpClient> queue = new ReferenceQueue<>();
        WeakReference<HttpClient> weak = new WeakReference<>(client, queue);
        System.gc();
        Reference.reachabilityFence(pool);
        client = null; pool = null; c = null;
        while (true) {
            long cleaners = getActiveCleaners();
            System.out.println("Waiting for GC to release stub HttpClient;"
                    + " active cache cleaners: " + cleaners);
            System.gc();
            Reference<?> ref = queue.remove(1000);
            if (ref == weak) {
                System.out.println("Stub HttpClient GC'ed");
                break;
            }
        }
        while (getActiveCleaners() > 0) {
            System.out.println("Waiting for CacheCleaner to stop");
            Thread.sleep(1000);
        }
        System.out.println("Active CacheCleaners: "
                + getActiveCleaners());

        if (getActiveCleaners() > 0) {
            throw new RuntimeException("Too many CacheCleaner active: "
                    + getActiveCleaners());
        }
    }
    static <T> T error() {
        throw new InternalError("Should not reach here: wrong test assumptions!");
    }

    // Emulates an HttpConnection that has a strong reference to its HttpClient.
    static class HttpConnectionStub extends HttpConnection {

        public HttpConnectionStub(HttpClient client,
                InetSocketAddress address,
                InetSocketAddress proxy,
                boolean secured) {
            super(address, null);
            this.key = ConnectionPool.cacheKey(address, proxy);
            this.address = address;
            this.proxy = proxy;
            this.secured = secured;
            this.client = client;
        }

        InetSocketAddress proxy;
        InetSocketAddress address;
        boolean secured;
        ConnectionPool.CacheKey key;
        HttpClient client;

        // All these return something
        @Override boolean connected() {return true;}
        @Override boolean isSecure() {return secured;}
        @Override boolean isProxied() {return proxy!=null;}
        @Override ConnectionPool.CacheKey cacheKey() {return key;}
        @Override public void close() {}
        @Override void shutdownInput() throws IOException {}
        @Override void shutdownOutput() throws IOException {}
        public String toString() {
            return "HttpConnectionStub: " + address + " proxy: " + proxy;
        }

        // All these throw errors
        @Override
        public void connect() throws IOException, InterruptedException {error();}
        @Override public CompletableFuture<Void> connectAsync() {return error();}
        @Override SocketChannel channel() {return error();}
        @Override void flushAsync() throws IOException {error();}
        @Override
        protected ByteBuffer readImpl() throws IOException {return error();}
        @Override CompletableFuture<Void> whenReceivingResponse() {return error();}
        @Override
        long write(ByteBuffer[] buffers, int start, int number) throws IOException {
            throw (Error)error();
        }
        @Override
        long write(ByteBuffer buffer) throws IOException {throw (Error)error();}
        @Override
        void writeAsync(ByteBufferReference[] buffers) throws IOException {
            error();
        }
        @Override
        void writeAsyncUnordered(ByteBufferReference[] buffers)
                throws IOException {
            error();
        }
    }
    // Emulates an HttpClient that has a strong reference to its connection pool.
    static class HttpClientStub extends HttpClient {
        public HttpClientStub(ConnectionPool pool) {
            this.pool = pool;
        }
        final ConnectionPool pool;
        @Override public Optional<CookieManager> cookieManager() {return error();}
        @Override public HttpClient.Redirect followRedirects() {return error();}
        @Override public Optional<ProxySelector> proxy() {return error();}
        @Override public SSLContext sslContext() {return error();}
        @Override public Optional<SSLParameters> sslParameters() {return error();}
        @Override public Optional<Authenticator> authenticator() {return error();}
        @Override public HttpClient.Version version() {return HttpClient.Version.HTTP_1_1;}
        @Override public Executor executor() {return error();}
        @Override
        public <T> HttpResponse<T> send(HttpRequest req,
                HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return error();
        }
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest req,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return error();
        }
        @Override
        public <U, T> CompletableFuture<U> sendAsync(HttpRequest req,
                HttpResponse.MultiProcessor<U, T> multiProcessor) {
            return error();
        }
    }

}
