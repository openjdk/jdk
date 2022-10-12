/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.loom.ring;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.stream.IntStream;

public class Channels {

    static class DirectChannel<T> implements Channel<T> {
        private final BlockingQueue<T> q;

        DirectChannel(BlockingQueue<T> q) {
            this.q = q;
        }

        @Override
        public void send(T e) {
            boolean interrupted = false;
            while (true) {
                try {
                    q.put(e);
                    break;
                } catch (InterruptedException x) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }

        @Override
        public T receive() {
            boolean interrupted = false;
            T e;
            while (true) {
                try {
                    e = q.take();
                    break;
                } catch (InterruptedException x) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
            return e;
        }
    }

    static abstract class AbstractStackChannel<T> implements Channel<T> {
        private final BlockingQueue<T> q;

        protected AbstractStackChannel(BlockingQueue<T> q) {
            this.q = q;
        }

        public void sendImpl(T e) {
            boolean interrupted = false;
            while (true) {
                try {
                    q.put(e);
                    break;
                } catch (InterruptedException x) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }

        public T receiveImpl() {
            boolean interrupted = false;
            T e;
            while (true) {
                try {
                    e = q.take();
                    break;
                } catch (InterruptedException x) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
            return e;
        }

    }

    public static class ChannelFixedStackI1<T> extends AbstractStackChannel<T> {
        private final int depth;
        private T sendMsg;     // store data in heap, not on stack
        private T receiveMsg;

        public ChannelFixedStackI1(BlockingQueue<T> q, int depth) {
            super(q);
            this.depth = depth;
        }

        @Override
        public void send(T e) {
            sendMsg = e;
            recursiveSend(depth);
        }

        @Override
        public T receive() {
            recursiveReceive(depth);
            return receiveMsg;
        }

        private void recursiveSend(int depth) {
            if (depth == 0) {
                sendImpl(sendMsg);
            } else {
                recursiveSend(depth - 1);
            }
        }

        private void recursiveReceive(int depth) {
            if (depth == 0) {
                receiveMsg = receiveImpl();
            } else {
                recursiveReceive(depth - 1);
            }
        }
    }

    public static class ChannelFixedStackI2<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x2 = 42;
        private T sendMsg;     // store data in heap, not on stack
        private T receiveMsg;

        public ChannelFixedStackI2(BlockingQueue<T> q, int depth) {
            super(q);
            this.depth = depth;
        }

        @Override
        public void send(T e) {
            sendMsg = e;
            recursiveSend(depth, x2);
        }

        @Override
        public T receive() {
            recursiveReceive(depth, x2);
            return receiveMsg;
        }

        private void recursiveSend(int depth, int x2) {
            if (depth == 0) {
                sendImpl(sendMsg);
            } else {
                recursiveSend(depth - 1, x2 + 1);
            }
        }

        private void recursiveReceive(int depth, int x2) {
            if (depth == 0) {
                receiveMsg = receiveImpl();
            } else {
                recursiveReceive(depth - 1, x2 + 1);
            }
        }
    }

    public static class ChannelFixedStackI4<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x2 = 42;
        private int x3 = 43;
        private int x4 = 44;
        private T sendMsg;     // store data in heap, not on stack
        private T receiveMsg;

        public ChannelFixedStackI4(BlockingQueue<T> q, int depth) {
            super(q);
            this.depth = depth;
        }

        @Override
        public void send(T e) {
            sendMsg = e;
            recursiveSend(depth, x2, x3, x4);
        }

        @Override
        public T receive() {
            recursiveReceive(depth, x2, x3, x4);
            return receiveMsg;
        }

        private void recursiveSend(int depth, int x2, int x3, int x4) {
            if (depth == 0) {
                sendImpl(sendMsg);
            } else {
                recursiveSend(depth - 1, x2 + 1, x3 + 2, x4 + 3);
            }
        }

        private void recursiveReceive(int depth, int x2, int x3, int x4) {
            if (depth == 0) {
                receiveMsg = receiveImpl();
            } else {
                recursiveReceive(depth - 1, x2 + 1, x3 + 2, x4 + 3);
            }
        }
    }

    public static class ChannelFixedStackI8<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x2 = 42;
        private int x3 = 43;
        private int x4 = 44;
        private int x5 = 45;
        private int x6 = 46;
        private int x7 = 47;
        private int x8 = 48;
        private T sendMsg;     // store data in heap, not on stack
        private T receiveMsg;

        public ChannelFixedStackI8(BlockingQueue<T> q, int depth) {
            super(q);
            this.depth = depth;
        }

        @Override
        public void send(T e) {
            sendMsg = e;
            recursiveSend(depth, x2, x3, x4, x5, x6, x7, x8);
        }

        @Override
        public T receive() {
            recursiveReceive(depth, x2, x3, x4, x5, x6, x7, x8);
            return receiveMsg;
        }

        private void recursiveSend(int depth, int x2, int x3, int x4, int x5, int x6, int x7, int x8) {
            if (depth == 0) {
                sendImpl(sendMsg);
            } else {
                recursiveSend(depth - 1, x2 + 1, x3 + 2, x4 + 3, x5 + 4, x6 + 5, x7 + 6, x8 + 7);
            }
        }

        private void recursiveReceive(int depth, int x2, int x3, int x4, int x5, int x6, int x7, int x8) {
            if (depth == 0) {
                receiveMsg = receiveImpl();
            } else {
                recursiveReceive(depth - 1, x2 + 1, x3 + 2, x4 + 3, x5 + 4, x6 + 5, x7 + 6, x8 + 7);
            }
        }
    }

    public static class ChannelFixedStackR1<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x1;

        public ChannelFixedStackR1(BlockingQueue<T> q, int depth, int start) {
            super(q);
            this.depth = depth;
            x1 = start + 1;
        }

        @Override
        public void send(T e) {
            recursiveSend(depth, e, Ref.of(x1));
        }

        @Override
        public T receive() {
            return recursiveReceive(depth, Ref.of(x1));
        }

        private void recursiveSend(int depth, T msg, Ref x1) {
            if (depth == 0) {
                sendImpl(msg);
            } else {
                recursiveSend(depth - 1, msg, x1.inc());
            }
        }

        private T recursiveReceive(int depth, Ref x1) {
            if (depth == 0) {
                return receiveImpl();
            } else {
                return recursiveReceive(depth - 1, x1.inc());
            }
        }
    }

    public static class ChannelFixedStackR2<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x1;
        private int x2;

        public ChannelFixedStackR2(BlockingQueue<T> q, int depth, int start) {
            super(q);
            this.depth = depth;
            x1 = start + 1;
            x2 = start + 2;
        }

        @Override
        public void send(T e) {
            recursiveSend(depth, e, Ref.of(x1), Ref.of(x2));
        }

        @Override
        public T receive() {
            return recursiveReceive(depth, Ref.of(x1), Ref.of(x2));
        }

        private void recursiveSend(int depth, T msg, Ref x1, Ref x2) {
            if (depth == 0) {
                sendImpl(msg);
            } else {
                recursiveSend(depth - 1, msg, x1.inc(), x2.inc());
            }
        }

        private T recursiveReceive(int depth, Ref x1, Ref x2) {
            if (depth == 0) {
                return receiveImpl();
            } else {
                return recursiveReceive(depth - 1, x1.inc(), x2.inc());
            }
        }
    }

    public static class ChannelFixedStackR4<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x1;
        private int x2;
        private int x3;
        private int x4;

        public ChannelFixedStackR4(BlockingQueue<T> q, int depth, int start) {
            super(q);
            this.depth = depth;
            x1 = start + 1;
            x2 = start + 2;
            x3 = start + 3;
            x4 = start + 4;

        }

        @Override
        public void send(T e) {
            recursiveSend(depth, e, Ref.of(x1), Ref.of(x2), Ref.of(x3), Ref.of(x4));
        }

        @Override
        public T receive() {
            return recursiveReceive(depth, Ref.of(x1), Ref.of(x2), Ref.of(x3), Ref.of(x4));
        }

        private void recursiveSend(int depth, T msg, Ref x1, Ref x2, Ref x3, Ref x4) {
            if (depth == 0) {
                sendImpl(msg);
            } else {
                recursiveSend(depth - 1, msg, x1.inc(), x2.inc(), x3.inc(), x4.inc());
            }
        }

        private T recursiveReceive(int depth, Ref x1, Ref x2, Ref x3, Ref x4) {
            if (depth == 0) {
                return receiveImpl();
            } else {
                return recursiveReceive(depth - 1, x1.inc(), x2.inc(), x3.inc(), x4.inc());
            }
        }
    }

    public static class ChannelFixedStackR8<T> extends AbstractStackChannel<T> {
        private final int depth;
        private int x1;
        private int x2;
        private int x3;
        private int x4;
        private int x5;
        private int x6;
        private int x7;
        private int x8;

        public ChannelFixedStackR8(BlockingQueue<T> q, int depth, int start) {
            super(q);
            this.depth = depth;
            x1 = start + 1;
            x2 = start + 2;
            x3 = start + 3;
            x4 = start + 4;
            x5 = start + 5;
            x6 = start + 6;
            x7 = start + 7;
            x8 = start + 8;
        }

        @Override
        public void send(T e) {
            recursiveSend(depth, e, Ref.of(x1), Ref.of(x2), Ref.of(x3), Ref.of(x4), Ref.of(x5), Ref.of(x6), Ref.of(x7), Ref.of(x8));
        }

        @Override
        public T receive() {
            return recursiveReceive(depth, Ref.of(x1), Ref.of(x2), Ref.of(x3), Ref.of(x4), Ref.of(x5), Ref.of(x6), Ref.of(x7), Ref.of(x8));
        }

        private void recursiveSend(int depth, T msg, Ref x1, Ref x2, Ref x3, Ref x4, Ref x5, Ref x6, Ref x7, Ref x8) {
            if (depth == 0) {
                sendImpl(msg);
            } else {
                recursiveSend(depth - 1, msg, x1.inc(), x2.inc(), x3.inc(), x4.inc(), x5.inc(), x6.inc(), x7.inc(), x8.inc());
            }
        }

        private T recursiveReceive(int depth, Ref x1, Ref x2, Ref x3, Ref x4, Ref x5, Ref x6, Ref x7, Ref x8) {
            if (depth == 0) {
                return receiveImpl();
            } else {
                return recursiveReceive(depth - 1, x1.inc(), x2.inc(), x3.inc(), x4.inc(), x5.inc(), x6.inc(), x7.inc(), x8.inc());
            }
        }
    }



    static class Ref {
        private final int v;

        Ref(int v) {
            this.v = v;
        }

        public static Ref of(int v) {
            return ((v >= 0) && (v < CACHE_MAX)) ? cache[v] : new Ref(v);
        }

        private static final int CACHE_MAX = 1024*2;
        private static final Ref[] cache = IntStream.range(0, CACHE_MAX).mapToObj(Ref::new).toArray(Ref[]::new);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Ref)) return false;
            Ref ref = (Ref) o;
            return v == ref.v;
        }

        @Override
        public int hashCode() {
            return Objects.hash(v);
        }

        @Override
        public String toString() {
            return "Ref{" +
                    "v=" + v +
                    '}';
        }

        public Ref inc() {
            return of(v + 1);
        }
    }

//    static class Ref {
//        private final int v;
//
//        Ref(int v) {
//            this.v = v;
//        }
//
//        public static Ref of(int v) {
//            return getRef(new Ref(v));
//        }
//
//        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
//        private static Ref getRef(Ref x) {
//            Ref y = cache.get(x);
//            return y == null ? x : y;
//        }
//
//        private static final int CACHE_MAX = 1024*2;
//
//        private static final Map<Ref, Ref> cache = IntStream.range(0, CACHE_MAX).mapToObj(Ref::new).collect(Collectors.toMap( k -> k, v-> v));
//
//        @Override
//        public boolean equals(Object o) {
//            if (this == o) return true;
//            if (!(o instanceof Ref)) return false;
//            Ref ref = (Ref) o;
//            return v == ref.v;
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(v);
//        }
//
//        @Override
//        public String toString() {
//            return "Ref{" +
//                    "v=" + v +
//                    '}';
//        }
//
//        public Ref inc() {
//            return of(v + 1);
//        }
//    }


}
