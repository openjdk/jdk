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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * Panama FFM bindings for libdbus-1.so, used by the SNI (StatusNotifierItem)
 * tray icon implementation.
 *
 * Struct sizes verified against dbus/dbus-types.h:
 *   DBusError = 32 bytes, DBusMessageIter = 72 bytes, DBusObjectPathVTable = 48 bytes
 */
final class SNIDBusLib {

    // D-Bus type constants (dbus/dbus-protocol.h)
    static final int DBUS_TYPE_INVALID       = 0;
    static final int DBUS_TYPE_BOOLEAN       = 'b';
    static final int DBUS_TYPE_INT32         = 'i';
    static final int DBUS_TYPE_UINT32        = 'u';
    static final int DBUS_TYPE_STRING        = 's';
    static final int DBUS_TYPE_OBJECT_PATH   = 'o';
    static final int DBUS_TYPE_ARRAY         = 'a';
    static final int DBUS_TYPE_VARIANT       = 'v';
    static final int DBUS_TYPE_STRUCT        = 'r';
    static final int DBUS_TYPE_DICT_ENTRY    = 'e';
    static final int DBUS_TYPE_BYTE          = 'y';

    static final int DBUS_BUS_SESSION        = 0;

    static final int DBUS_MESSAGE_TYPE_METHOD_CALL   = 1;
    static final int DBUS_MESSAGE_TYPE_METHOD_RETURN = 2;
    static final int DBUS_MESSAGE_TYPE_SIGNAL        = 4;

    static final int DBUS_HANDLER_RESULT_HANDLED         = 0;
    static final int DBUS_HANDLER_RESULT_NOT_YET_HANDLED = 1;

    static final int DBUS_NAME_FLAG_REPLACE_EXISTING  = 0x1;
    static final int DBUS_NAME_FLAG_DO_NOT_QUEUE      = 0x4;
    static final int DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER = 1;

    // Maximum length for D-Bus names (bus, interface, member, error names).
    // Defined as DBUS_MAXIMUM_NAME_LENGTH in dbus/dbus-protocol.h.
    static final int DBUS_MAXIMUM_NAME_LENGTH = 255;

    // --- Struct layouts ---

    /** DBusError: 32 bytes */
    static final MemoryLayout ERROR_LAYOUT = MemoryLayout.structLayout(
        ADDRESS.withName("name"),
        ADDRESS.withName("message"),
        JAVA_INT.withName("bits"),
        JAVA_INT.withName("pad"),
        ADDRESS.withName("padding1")
    );

    /** DBusMessageIter: 72 bytes */
    static final MemoryLayout ITER_LAYOUT = MemoryLayout.structLayout(
        ADDRESS.withName("dummy1"),
        ADDRESS.withName("dummy2"),
        JAVA_INT.withName("dummy3"),
        JAVA_INT.withName("dummy4"),
        JAVA_INT.withName("dummy5"),
        JAVA_INT.withName("dummy6"),
        JAVA_INT.withName("dummy7"),
        JAVA_INT.withName("dummy8"),
        JAVA_INT.withName("dummy9"),
        JAVA_INT.withName("dummy10"),
        JAVA_INT.withName("dummy11"),
        JAVA_INT.withName("pad1"),
        ADDRESS.withName("pad2"),
        ADDRESS.withName("pad3")
    );

    /** DBusObjectPathVTable: 48 bytes — 6 function pointers */
    static final MemoryLayout VTABLE_LAYOUT = MemoryLayout.structLayout(
        ADDRESS.withName("unregister_function"),
        ADDRESS.withName("message_function"),
        ADDRESS.withName("pad1"),
        ADDRESS.withName("pad2"),
        ADDRESS.withName("pad3"),
        ADDRESS.withName("pad4")
    );

    // --- FFM plumbing ---

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup DBUS;

    /** True if libdbus-1.so.3 was successfully loaded; false means SNI is unavailable. */
    static final boolean AVAILABLE;

    static {
        SymbolLookup lib = null;
        boolean ok = false;
        try {
            @SuppressWarnings("restricted")
            SymbolLookup l = SymbolLookup.libraryLookup("libdbus-1.so.3", Arena.global());
            lib = l;
            ok = true;
        } catch (Throwable t) {
            // libdbus-1.so.3 not present; SNI tray support will be disabled
        }
        DBUS = lib;
        AVAILABLE = ok;
    }

    @SuppressWarnings("restricted")
    private static MethodHandle fn(String name, FunctionDescriptor desc) {
        if (DBUS == null) return null;
        return LINKER.downcallHandle(DBUS.findOrThrow(name), desc);
    }

    // --- Function handles ---

    static final MethodHandle dbus_error_init = fn("dbus_error_init",
        FunctionDescriptor.ofVoid(ADDRESS));

    static final MethodHandle dbus_error_free = fn("dbus_error_free",
        FunctionDescriptor.ofVoid(ADDRESS));

    static final MethodHandle dbus_error_is_set = fn("dbus_error_is_set",
        FunctionDescriptor.of(JAVA_INT, ADDRESS));

    static final MethodHandle dbus_bus_get = fn("dbus_bus_get",
        FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle dbus_bus_get_private = fn("dbus_bus_get_private",
        FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle dbus_bus_request_name = fn("dbus_bus_request_name",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle dbus_bus_get_unique_name = fn("dbus_bus_get_unique_name",
        FunctionDescriptor.of(ADDRESS, ADDRESS));

    static final MethodHandle dbus_bus_add_match = fn("dbus_bus_add_match",
        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_connection_add_filter =
        fn("dbus_connection_add_filter",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_connection_register_object_path =
        fn("dbus_connection_register_object_path",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_connection_read_write_dispatch =
        fn("dbus_connection_read_write_dispatch",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    static final MethodHandle dbus_connection_send = fn("dbus_connection_send",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_connection_send_with_reply_and_block =
        fn("dbus_connection_send_with_reply_and_block",
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle dbus_connection_flush = fn("dbus_connection_flush",
        FunctionDescriptor.ofVoid(ADDRESS));

    static final MethodHandle dbus_message_get_type = fn("dbus_message_get_type",
        FunctionDescriptor.of(JAVA_INT, ADDRESS));

    static final MethodHandle dbus_message_get_member = fn("dbus_message_get_member",
        FunctionDescriptor.of(ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_get_path = fn("dbus_message_get_path",
        FunctionDescriptor.of(ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_get_interface = fn("dbus_message_get_interface",
        FunctionDescriptor.of(ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_new_method_return =
        fn("dbus_message_new_method_return",
        FunctionDescriptor.of(ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_new_method_call =
        fn("dbus_message_new_method_call",
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_new_signal = fn("dbus_message_new_signal",
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_unref = fn("dbus_message_unref",
        FunctionDescriptor.ofVoid(ADDRESS));

    static final MethodHandle dbus_message_iter_init = fn("dbus_message_iter_init",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_iter_get_arg_type =
        fn("dbus_message_iter_get_arg_type",
        FunctionDescriptor.of(JAVA_INT, ADDRESS));

    static final MethodHandle dbus_message_iter_get_basic =
        fn("dbus_message_iter_get_basic",
        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_iter_next = fn("dbus_message_iter_next",
        FunctionDescriptor.of(JAVA_INT, ADDRESS));

    static final MethodHandle dbus_message_iter_init_append =
        fn("dbus_message_iter_init_append",
        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_iter_append_basic =
        fn("dbus_message_iter_append_basic",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    static final MethodHandle dbus_message_iter_open_container =
        fn("dbus_message_iter_open_container",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    static final MethodHandle dbus_message_iter_close_container =
        fn("dbus_message_iter_close_container",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    static final MethodHandle dbus_bus_release_name = fn("dbus_bus_release_name",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    static final MethodHandle dbus_connection_unregister_object_path =
        fn("dbus_connection_unregister_object_path",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    static final MethodHandle dbus_connection_close = fn("dbus_connection_close",
        FunctionDescriptor.ofVoid(ADDRESS));

    static final MethodHandle dbus_connection_unref = fn("dbus_connection_unref",
        FunctionDescriptor.ofVoid(ADDRESS));

    private SNIDBusLib() {}
}
