/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.X11;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.*;

import sun.util.logging.PlatformLogger;

/**
 * Manages a D-Bus session connection for the SNI tray icon implementation.
 *
 * Threading model: the dispatch loop must run on a platform thread (not a
 * virtual thread) because native blocking calls pin the carrier thread in
 * JDK 24+, and the upcall stub is invoked synchronously from within the
 * native dispatch call.
 *
 * Registration with StatusNotifierWatcher must be fire-and-forget (no blocking
 * reply wait) to avoid deadlocking with the already-running dispatch loop.
 */
final class SNIDBusConn {

    private static final PlatformLogger log =
        PlatformLogger.getLogger("sun.awt.X11.SNIDBusConn");

    // Passed to dbus_connection_read_write_dispatch as the blocking timeout.
    // 100 ms gives ~10 iterations/s, keeping CPU idle while remaining
    // responsive to the closed flag without busy-waiting.
    private static final int DISPATCH_POLL_MS = 100;

    // Upper bound for the dispatch thread to become ready after start().
    // A platform thread scheduled by the OS should be runnable within
    // milliseconds; 2 s is generous enough to survive a loaded CI machine.
    private static final int DISPATCH_START_TIMEOUT_S = 2;

    // Method handles for the native callback functions, resolved once at
    // class init. Both have the standard DBusHandleMessageFunction signature
    // (int)(DBusConnection*, DBusMessage*, void*).
    private static final MethodHandle DISPATCH_MH;
    private static final MethodHandle FILTER_MH;
    static {
        try {
            MethodType cb = MethodType.methodType(int.class,
                MemorySegment.class, MemorySegment.class, MemorySegment.class);
            DISPATCH_MH = MethodHandles.lookup()
                .findStatic(SNIDBusConn.class, "dispatchCallback", cb);
            FILTER_MH = MethodHandles.lookup()
                .findStatic(SNIDBusConn.class, "filterCallback", cb);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final MemorySegment conn;      // DBusConnection*
    private final Arena arena;     // lives for the lifetime of this connection
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch dispatchReady = new CountDownLatch(1);
    private final AtomicBoolean filterInstalled = new AtomicBoolean();

    /**
     * Handler for incoming D-Bus messages on a registered object path.
     * Invoked synchronously from the dispatch loop on a platform thread.
     * Implementations must not block indefinitely or propagate checked exceptions.
     *
     * @return {@code true} if the message was handled,
     *         {@code false} to pass it to the next handler
     */
    @FunctionalInterface
    interface MessageHandler {
        boolean handle(SNIMsg msg);
    }

    private SNIDBusConn(MemorySegment conn, Arena arena) {
        this.conn  = conn;
        this.arena = arena;
    }

    /**
     * Connect to the D-Bus session bus using a private connection.
     * Each call returns a new independent connection, allowing multiple
     * SNITrayIconPeer instances to register the same object paths concurrently.
     * Returns null if libdbus-1 is unavailable or the connection fails.
     */
    @SuppressWarnings("restricted")
    static SNIDBusConn connectSession() {
        if (!SNIDBusLib.AVAILABLE) return null;
        Arena arena = Arena.ofShared();
        try {
            MemorySegment error = arena.allocate(SNIDBusLib.ERROR_LAYOUT);
            dbus_error_init(error);
            MemorySegment c = dbus_bus_get_private(SNIDBusLib.DBUS_BUS_SESSION, error);
            checkError(error, "dbus_bus_get_private");
            if (c == null || c.equals(MemorySegment.NULL)) {
                arena.close();
                return null;
            }
            return new SNIDBusConn(c, arena);
        } catch (Throwable t) {
            arena.close();
            return null;
        }
    }

    /** Request a well-known bus name. Throws RuntimeException on failure. */
    void requestName(String name) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment error = tmp.allocate(SNIDBusLib.ERROR_LAYOUT);
            dbus_error_init(error);
            int result = dbus_bus_request_name(
                conn,
                tmp.allocateFrom(name),
                SNIDBusLib.DBUS_NAME_FLAG_REPLACE_EXISTING | SNIDBusLib.DBUS_NAME_FLAG_DO_NOT_QUEUE,
                error);
            checkError(error, "dbus_bus_request_name");
            if (result != SNIDBusLib.DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER) {
                throw new RuntimeException("Could not acquire bus name '" + name + "': " + result);
            }
        }
    }

    /**
     * Register an object path with the given message handler.
     * The vtable and upcall stub are kept alive by the connection arena.
     */
    void registerObject(String path, MessageHandler handler) {
        HandlerRegistry.register(conn, path, handler);

        @SuppressWarnings("restricted")
        MemorySegment stub = Linker.nativeLinker().upcallStub(
            DISPATCH_MH,
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
            arena);

        MemorySegment vtable = arena.allocate(SNIDBusLib.VTABLE_LAYOUT);
        vtable.set(ADDRESS, 0,  MemorySegment.NULL); // unregister_function
        vtable.set(ADDRESS, 8,  stub);               // message_function
        vtable.set(ADDRESS, 16, MemorySegment.NULL);
        vtable.set(ADDRESS, 24, MemorySegment.NULL);
        vtable.set(ADDRESS, 32, MemorySegment.NULL);
        vtable.set(ADDRESS, 40, MemorySegment.NULL);

        int ok = dbus_connection_register_object_path(conn, arena.allocateFrom(path), vtable, MemorySegment.NULL);
        if (ok == 0) {
            throw new RuntimeException("dbus_connection_register_object_path failed: " + path);
        }
    }

    /** Run the message dispatch loop. Must be called from a platform thread. */
    void runDispatchLoop() {
        if (log.isLoggable(PlatformLogger.Level.FINE)) {
            log.fine("SNI dispatch loop running on: " + Thread.currentThread().getName());
        }
        dispatchReady.countDown();
        try {
            int ok;
            do {
                ok = dbus_connection_read_write_dispatch(conn, DISPATCH_POLL_MS);
            } while (ok != 0 && !closed.get());
            log.fine("SNI dispatch loop ended");
        } catch (Throwable t) {
            log.severe("SNI dispatch loop crashed: " + t);
            throw new RuntimeException(t);
        }
    }

    /**
     * Block until the dispatch loop is running, with a 2-second timeout.
     * Must be called before registering with the SNI watcher to ensure
     * incoming D-Bus messages can be received immediately after registration.
     *
     * @throws RuntimeException if the dispatch loop does not start in time
     */
    void awaitDispatch() {
        try {
            if (!dispatchReady.await(DISPATCH_START_TIMEOUT_S, TimeUnit.SECONDS)) {
                throw new RuntimeException("SNI dispatch loop did not start within "
                        + DISPATCH_START_TIMEOUT_S + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for SNI dispatch loop", e);
        }
    }

    /**
     * Unregister object paths, release the bus name, and close the connection.
     * The dispatch loop will exit naturally when the connection is closed.
     * Calls after the first one are no-ops.
     */
    void close(String busName, String... paths) {
        if (!closed.compareAndSet(false, true)) return;
        try (Arena tmp = Arena.ofConfined()) {
            for (String path : paths) {
                HandlerRegistry.unregister(conn, path);
                dbus_connection_unregister_object_path(conn, tmp.allocateFrom(path));
            }
            FilterRegistry.unregister(conn);
            MemorySegment error = tmp.allocate(SNIDBusLib.ERROR_LAYOUT);
            dbus_error_init(error);
            dbus_bus_release_name(conn, tmp.allocateFrom(busName), error);
            dbus_error_free(error);
            // private connection — must close() before unref()
            dbus_connection_close(conn);
            dbus_connection_unref(conn);
        } catch (Throwable t) {
            log.warning("SNI close failed: " + t);
        } finally {
            arena.close();
        }
    }

    /**
     * Close a private connection that has no registered paths or bus names
     * (e.g. a one-shot connection used only for a D-Bus query).
     * Calls after the first one are no-ops.
     */
    void closePrivate() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            dbus_connection_close(conn);
            dbus_connection_unref(conn);
        } catch (Throwable t) {
            log.warning("SNI closePrivate failed: " + t);
        } finally {
            arena.close();
        }
    }

    /** Send fire-and-forget (no reply expected). */
    void sendNoReply(SNIMsg msg) {
        dbus_connection_send(conn, msg.ptr, MemorySegment.NULL);
        dbus_connection_flush(conn);
    }

    // --- Callback invoked by libdbus for registered paths ---

    @SuppressWarnings("unused")
    private static int dispatchCallback(MemorySegment conn, MemorySegment msgPtr,
                                        MemorySegment userData) {
        try (SNIMsg msg = SNIMsg.borrow(msgPtr)) {
            if (log.isLoggable(PlatformLogger.Level.FINER)) {
                log.finer("SNI dispatch: path=" + msg.getPath()
                    + " iface=" + msg.getIface() + " member=" + msg.getMember());
            }
            MessageHandler handler = HandlerRegistry.get(conn, msg.getPath());
            if (handler != null && handler.handle(msg)) {
                return SNIDBusLib.DBUS_HANDLER_RESULT_HANDLED;
            }
        } catch (Throwable e) {
            // Must not propagate Throwable across FFM upcall boundary.
            // Note: malformed D-Bus messages cause libdbus to call abort()
            // (SIGABRT), which kills the JVM before any Java handler runs.
            // The only defense is correct message construction.
            log.severe("SNI dispatch error: " + e);
        }
        return SNIDBusLib.DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    // --- Error checking ---

    @SuppressWarnings("restricted")
    private static void checkError(MemorySegment error, String context) {
        int set = dbus_error_is_set(error);
        if (set != 0) {
            MemorySegment msgPtr = error.get(ADDRESS, 8);
            String errMsg = msgPtr.equals(MemorySegment.NULL)
                ? "(no message)" : msgPtr.reinterpret(SNIDBusLib.DBUS_MAXIMUM_NAME_LENGTH + 1L).getString(0);
            dbus_error_free(error);
            throw new RuntimeException(context + " failed: " + errMsg);
        }
    }

    /**
     * Install a match rule and a filter handler for signals received on this
     * connection. Signals do not flow through registered object paths, so a
     * filter is the only way to receive them. The handler is called from the
     * dispatch thread for every incoming message that matches.
     *
     * <p>The match rule is added via {@code dbus_bus_add_match} (see the
     * D-Bus specification for the rule syntax). Only one filter handler is
     * supported per connection; subsequent calls replace the previous handler
     * and add the new match rule.
     *
     * @param matchRule the D-Bus match rule (e.g.
     *     {@code "type='signal',interface='org.freedesktop.DBus',member='NameOwnerChanged'"})
     * @param filter handler invoked for incoming messages matching the rule
     */
    @SuppressWarnings("restricted")
    void installFilter(String matchRule, MessageHandler filter) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment error = tmp.allocate(SNIDBusLib.ERROR_LAYOUT);
            dbus_error_init(error);
            dbus_bus_add_match(conn, tmp.allocateFrom(matchRule), error);
            checkError(error, "dbus_bus_add_match");
        }

        FilterRegistry.register(conn, filter);

        if (filterInstalled.compareAndSet(false, true)) {
            try {
                @SuppressWarnings("restricted")
                MemorySegment stub = Linker.nativeLinker().upcallStub(
                    FILTER_MH,
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
                    arena);

                int ok = dbus_connection_add_filter(conn, stub, MemorySegment.NULL, MemorySegment.NULL);
                if (ok == 0) {
                    throw new RuntimeException("dbus_connection_add_filter failed");
                }
            } catch (Throwable t) {
                // Roll back so a subsequent installFilter() can retry.
                filterInstalled.set(false);
                FilterRegistry.unregister(conn);
                if (t instanceof RuntimeException re) throw re;
                throw new RuntimeException(t);
            }
        }
    }

    @SuppressWarnings("unused")
    private static int filterCallback(MemorySegment conn, MemorySegment msgPtr,
                                      MemorySegment userData) {
        try (SNIMsg msg = SNIMsg.borrow(msgPtr)) {
            MessageHandler filter = FilterRegistry.get(conn);
            if (filter != null && filter.handle(msg)) {
                return SNIDBusLib.DBUS_HANDLER_RESULT_HANDLED;
            }
        } catch (Throwable e) {
            log.severe("SNI filter error: " + e);
        }
        return SNIDBusLib.DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    // --- Private MethodHandle wrappers ---

    private static void dbus_error_init(MemorySegment error) {
        try { SNIDBusLib.dbus_error_init.invoke(error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_error_free(MemorySegment error) {
        try { SNIDBusLib.dbus_error_free.invoke(error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_error_is_set(MemorySegment error) {
        try { return (int) SNIDBusLib.dbus_error_is_set.invoke(error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static MemorySegment dbus_bus_get_private(int bus, MemorySegment error) {
        try { return (MemorySegment) SNIDBusLib.dbus_bus_get_private.invoke(bus, error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_bus_request_name(MemorySegment conn, MemorySegment name, int flags, MemorySegment error) {
        try { return (int) SNIDBusLib.dbus_bus_request_name.invoke(conn, name, flags, error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_bus_add_match(MemorySegment conn, MemorySegment rule, MemorySegment error) {
        try { SNIDBusLib.dbus_bus_add_match.invoke(conn, rule, error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_connection_register_object_path(MemorySegment conn, MemorySegment path, MemorySegment vtable, MemorySegment userData) {
        try { return (int) SNIDBusLib.dbus_connection_register_object_path.invoke(conn, path, vtable, userData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_connection_read_write_dispatch(MemorySegment conn, int timeout) {
        try { return (int) SNIDBusLib.dbus_connection_read_write_dispatch.invoke(conn, timeout); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_connection_unregister_object_path(MemorySegment conn, MemorySegment path) {
        try { SNIDBusLib.dbus_connection_unregister_object_path.invoke(conn, path); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_bus_release_name(MemorySegment conn, MemorySegment name, MemorySegment error) {
        try { return (int) SNIDBusLib.dbus_bus_release_name.invoke(conn, name, error); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_connection_close(MemorySegment conn) {
        try { SNIDBusLib.dbus_connection_close.invoke(conn); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_connection_unref(MemorySegment conn) {
        try { SNIDBusLib.dbus_connection_unref.invoke(conn); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_connection_send(MemorySegment conn, MemorySegment msg, MemorySegment serial) {
        try { SNIDBusLib.dbus_connection_send.invoke(conn, msg, serial); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_connection_flush(MemorySegment conn) {
        try { SNIDBusLib.dbus_connection_flush.invoke(conn); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_connection_add_filter(MemorySegment conn, MemorySegment stub, MemorySegment userData, MemorySegment freeData) {
        try { return (int) SNIDBusLib.dbus_connection_add_filter.invoke(conn, stub, userData, freeData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /**
     * Registry of filter handlers, keyed on connection address so that
     * multiple connections may install independent filters concurrently.
     */
    private static final class FilterRegistry {
        private static final ConcurrentHashMap<Long, MessageHandler> MAP
            = new ConcurrentHashMap<>();

        static void register(MemorySegment conn, MessageHandler h) {
            MAP.put(conn.address(), h);
        }
        static void unregister(MemorySegment conn) {
            MAP.remove(conn.address());
        }
        static MessageHandler get(MemorySegment conn) {
            return MAP.get(conn.address());
        }
    }

    /**
     * Map from (connection address, object path) to handler.
     * Keyed on connection address so that multiple SNITrayIconPeer instances
     * (each with their own private connection) can register the same path
     * concurrently without colliding.
     */
    private static final class HandlerRegistry {
        private static final ConcurrentHashMap<String, MessageHandler> MAP
            = new ConcurrentHashMap<>();

        static void register(MemorySegment conn, String path, MessageHandler h) {
            MAP.put(key(conn, path), h);
        }
        static void unregister(MemorySegment conn, String path) {
            MAP.remove(key(conn, path));
        }
        static MessageHandler get(MemorySegment conn, String path) {
            return MAP.get(key(conn, path));
        }
        private static String key(MemorySegment conn, String path) {
            return conn.address() + ":" + path;
        }
    }
}
