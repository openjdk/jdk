/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A mutable container object for a value.
 * <p>
 * An alternative for cases where {@link AtomicReference} would be an overkill.
 * Sample usage:
 * {@snippet :
 * void foo(MessageNotifier messageNotifier) {
 *     var lastMessage = Slot.createEmpty();
 *
 *     messageNotifier.setListener(msg -> {
 *         lastMessage.set(msg);
 *     }).run();
 *
 *     lastMessage.find().ifPresentOrElse(msg -> {
 *         System.out.println(String.format("The last message: [%s]", msg));
 *     }, () -> {
 *         System.out.println("No messages received");
 *     });
 * }
 *
 * abstract class MessageNotifier {
 *     MessageNotifier setListener(Consumer<String> messageConsumer) {
 *         callback = messageConsumer;
 *         return this;
 *     }
 *
 *     void run() {
 *         for (;;) {
 *             var msg = fetchNextMessage();
 *             msg.ifPresent(callback);
 *             if (msg.isEmpty()) {
 *                 break;
 *             }
 *         }
 *     }
 *
 *     abstract Optional<String> fetchNextMessage();
 *
 *     private Consumer<String> callback;
 * }
 * }
 *
 * An alternative to the {@code Slot} would be either {@code
 * AtomicReference} or a single-element {@code String[]} or any other
 * suitable container type. {@code AtomicReference} would be an overkill if
 * thread-safety is not a concern and the use of other options would be
 * confusing.
 *
 * @param <T> value type
 */
public final class Slot<T> {

    public static <T> Slot<T> createEmpty() {

        return new Slot<>();
    }

    public T get() {
        return find().orElseThrow();
    }

    public Optional<T> find() {
        return Optional.ofNullable(value);
    }

    public void set(T v) {
        value = Objects.requireNonNull(v);
    }

    private T value;
}
