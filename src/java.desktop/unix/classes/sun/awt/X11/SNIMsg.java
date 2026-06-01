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
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * Lightweight wrapper around a DBusMessage* for reading and writing D-Bus arguments.
 * Used by the SNI (StatusNotifierItem) tray icon implementation.
 */
final class SNIMsg implements AutoCloseable {

    final MemorySegment ptr;       // DBusMessage*
    private final Arena arena;     // for native string allocations
    private final boolean ownsRef; // true if this SNIMsg is responsible for dbus_message_unref
    private MemorySegment readIter;
    private MemorySegment writeIter;

    /** Private constructor. */
    private SNIMsg(MemorySegment ptr, boolean ownsRef) {
        this.ptr     = ptr;
        this.arena   = Arena.ofConfined();
        this.ownsRef = ownsRef;
    }

    /** Wrap an existing DBusMessage* taking ownership. */
    static SNIMsg own(MemorySegment ptr) {
        return new SNIMsg(ptr, true);
    }

    /** Wrap an existing DBusMessage* without taking ownership. */
    static SNIMsg borrow(MemorySegment ptr) {
        return new SNIMsg(ptr, false);
    }

    /** Create a new method-return reply. */
    static SNIMsg newMethodReturn(SNIMsg request) {
        return SNIMsg.own(dbus_message_new_method_return(request.ptr));
    }

    /** Create a new method call. */
    static SNIMsg newMethodCall(Arena arena, String dest, String path,
                                String iface, String method) {
        return SNIMsg.own(dbus_message_new_method_call(
            arena.allocateFrom(dest),
            arena.allocateFrom(path),
            arena.allocateFrom(iface),
            arena.allocateFrom(method)));
    }

    /** Create a new signal. */
    static SNIMsg newSignal(Arena arena, String path, String iface, String name) {
        return SNIMsg.own(dbus_message_new_signal(
            arena.allocateFrom(path),
            arena.allocateFrom(iface),
            arena.allocateFrom(name)));
    }

    // --- Message metadata ---

    int getType() {
        return dbus_message_get_type(ptr);
    }

    String getMember()  { return readCString(SNIDBusLib.dbus_message_get_member, ptr); }
    String getPath()    { return readCString(SNIDBusLib.dbus_message_get_path,   ptr); }
    String getIface()   { return readCString(SNIDBusLib.dbus_message_get_interface, ptr); }

    @SuppressWarnings("restricted")
    private static String readCString(MethodHandle mh, MemorySegment msg) {
        try {
            MemorySegment s = (MemorySegment) mh.invoke(msg);
            if (s == null || s.equals(MemorySegment.NULL)) return null;
            return s.reinterpret(SNIDBusLib.DBUS_MAXIMUM_NAME_LENGTH + 1L).getString(0);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    // --- Reading ---

    private MemorySegment readIter() {
        if (readIter == null) {
            readIter = arena.allocate(SNIDBusLib.ITER_LAYOUT);
            dbus_message_iter_init(ptr, readIter);
        }
        return readIter;
    }

    int readArgType() {
        return dbus_message_iter_get_arg_type(readIter());
    }

    @SuppressWarnings("restricted")
    String readString() {
        MemorySegment holder = arena.allocate(ADDRESS);
        dbus_message_iter_get_basic(readIter(), holder);
        String result = holder.get(ADDRESS, 0).reinterpret(SNIDBusLib.DBUS_MAXIMUM_NAME_LENGTH + 1L).getString(0);
        dbus_message_iter_next(readIter());
        return result;
    }

    int readInt32() {
        MemorySegment holder = arena.allocate(JAVA_INT);
        dbus_message_iter_get_basic(readIter(), holder);
        int result = holder.get(JAVA_INT, 0);
        dbus_message_iter_next(readIter());
        return result;
    }

    // --- Writing ---

    private MemorySegment writeIter() {
        if (writeIter == null) {
            writeIter = arena.allocate(SNIDBusLib.ITER_LAYOUT);
            dbus_message_iter_init_append(ptr, writeIter);
        }
        return writeIter;
    }

    void appendString(String value) {
        appendStringOnIter(writeIter(), value);
    }

    void appendUInt32(int value) {
        MemorySegment holder = arena.allocate(JAVA_INT);
        holder.set(JAVA_INT, 0, value);
        dbus_message_iter_append_basic(writeIter(), SNIDBusLib.DBUS_TYPE_UINT32, holder);
    }

    void appendInt32(int value) {
        MemorySegment holder = arena.allocate(JAVA_INT);
        holder.set(JAVA_INT, 0, value);
        dbus_message_iter_append_basic(writeIter(), SNIDBusLib.DBUS_TYPE_INT32, holder);
    }

    void appendBool(boolean value) {
        MemorySegment holder = arena.allocate(JAVA_INT);
        holder.set(JAVA_INT, 0, value ? 1 : 0);
        dbus_message_iter_append_basic(writeIter(), SNIDBusLib.DBUS_TYPE_BOOLEAN, holder);
    }

    MemorySegment openContainer(int type, String signature) {
        MemorySegment sub = arena.allocate(SNIDBusLib.ITER_LAYOUT);
        MemorySegment sig = signature != null
            ? arena.allocateFrom(signature) : MemorySegment.NULL;
        dbus_message_iter_open_container(writeIter(), type, sig, sub);
        return sub;
    }

    void closeContainer(MemorySegment sub) {
        dbus_message_iter_close_container(writeIter(), sub);
    }

    // --- Static iter helpers (for nested containers) ---

    static MemorySegment openContainerOn(Arena arena, MemorySegment iter,
                                         int type, String signature) {
        MemorySegment sub = arena.allocate(SNIDBusLib.ITER_LAYOUT);
        MemorySegment sig = signature != null
            ? arena.allocateFrom(signature) : MemorySegment.NULL;
        dbus_message_iter_open_container(iter, type, sig, sub);
        return sub;
    }

    static void closeContainerOn(MemorySegment iter, MemorySegment sub) {
        dbus_message_iter_close_container(iter, sub);
    }

    static void appendStringOn(Arena arena, MemorySegment iter, String value) {
        MemorySegment s = arena.allocateFrom(value);
        MemorySegment holder = arena.allocate(ADDRESS);
        holder.set(ADDRESS, 0, s);
        dbus_message_iter_append_basic(iter, SNIDBusLib.DBUS_TYPE_STRING, holder);
    }

    static void appendObjectPathOn(Arena arena, MemorySegment iter, String value) {
        MemorySegment s = arena.allocateFrom(value);
        MemorySegment holder = arena.allocate(ADDRESS);
        holder.set(ADDRESS, 0, s);
        dbus_message_iter_append_basic(iter, SNIDBusLib.DBUS_TYPE_OBJECT_PATH, holder);
    }

    static void appendInt32On(Arena arena, MemorySegment iter, int value) {
        MemorySegment holder = arena.allocate(JAVA_INT);
        holder.set(JAVA_INT, 0, value);
        dbus_message_iter_append_basic(iter, SNIDBusLib.DBUS_TYPE_INT32, holder);
    }

    static void appendUInt32On(Arena arena, MemorySegment iter, int value) {
        MemorySegment holder = arena.allocate(JAVA_INT);
        holder.set(JAVA_INT, 0, value);
        dbus_message_iter_append_basic(iter, SNIDBusLib.DBUS_TYPE_UINT32, holder);
    }

    static void appendBoolOn(Arena arena, MemorySegment iter, boolean value) {
        MemorySegment holder = arena.allocate(JAVA_INT);
        holder.set(JAVA_INT, 0, value ? 1 : 0);
        dbus_message_iter_append_basic(iter, SNIDBusLib.DBUS_TYPE_BOOLEAN, holder);
    }

    private void appendStringOnIter(MemorySegment iter, String value) {
        MemorySegment s = arena.allocateFrom(value);
        MemorySegment holder = arena.allocate(ADDRESS);
        holder.set(ADDRESS, 0, s);
        dbus_message_iter_append_basic(iter, SNIDBusLib.DBUS_TYPE_STRING, holder);
    }

    /** Send this message and flush. */
    void send(MemorySegment conn) {
        dbus_connection_send(conn, ptr, MemorySegment.NULL);
        dbus_connection_flush(conn);
    }

    /** Expose the arena for callers that need to allocate native memory. */
    Arena arena() { return arena; }

    @Override
    public void close() {
        if (ownsRef) {
            dbus_message_unref(ptr);
        }
        arena.close();
    }

    // --- Private MethodHandle wrappers ---

    private static MemorySegment dbus_message_new_method_return(MemorySegment ptr) {
        try { return (MemorySegment) SNIDBusLib.dbus_message_new_method_return.invoke(ptr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static MemorySegment dbus_message_new_method_call(MemorySegment dest, MemorySegment path,
                                                              MemorySegment iface, MemorySegment method) {
        try { return (MemorySegment) SNIDBusLib.dbus_message_new_method_call.invoke(dest, path, iface, method); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static MemorySegment dbus_message_new_signal(MemorySegment path, MemorySegment iface, MemorySegment name) {
        try { return (MemorySegment) SNIDBusLib.dbus_message_new_signal.invoke(path, iface, name); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_message_get_type(MemorySegment ptr) {
        try { return (int) SNIDBusLib.dbus_message_get_type.invoke(ptr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_init(MemorySegment msg, MemorySegment iter) {
        try { SNIDBusLib.dbus_message_iter_init.invoke(msg, iter); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static int dbus_message_iter_get_arg_type(MemorySegment iter) {
        try { return (int) SNIDBusLib.dbus_message_iter_get_arg_type.invoke(iter); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_get_basic(MemorySegment iter, MemorySegment holder) {
        try { SNIDBusLib.dbus_message_iter_get_basic.invoke(iter, holder); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_next(MemorySegment iter) {
        try { SNIDBusLib.dbus_message_iter_next.invoke(iter); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_init_append(MemorySegment msg, MemorySegment iter) {
        try { SNIDBusLib.dbus_message_iter_init_append.invoke(msg, iter); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_append_basic(MemorySegment iter, int type, MemorySegment value) {
        try { SNIDBusLib.dbus_message_iter_append_basic.invoke(iter, type, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_open_container(MemorySegment iter, int type, MemorySegment sig, MemorySegment sub) {
        try { SNIDBusLib.dbus_message_iter_open_container.invoke(iter, type, sig, sub); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void dbus_message_iter_close_container(MemorySegment iter, MemorySegment sub) {
        try { SNIDBusLib.dbus_message_iter_close_container.invoke(iter, sub); }
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

    private static void dbus_message_unref(MemorySegment ptr) {
        try { SNIDBusLib.dbus_message_unref.invoke(ptr); }
        catch (Throwable t) { /* ignore */ }
    }
}
