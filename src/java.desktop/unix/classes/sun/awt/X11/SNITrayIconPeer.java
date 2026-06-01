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

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.awt.peer.TrayIconPeer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import sun.util.logging.PlatformLogger;

/**
 * TrayIconPeer implementation using the StatusNotifierItem (SNI) D-Bus protocol.
 * Used on Linux desktops that provide org.kde.StatusNotifierWatcher (Ubuntu 24+,
 * KDE Plasma, and others running the AppIndicator GNOME extension).
 *
 * The icon is exposed as a D-Bus service named
 * {@code org.kde.StatusNotifierItem-<pid>-<n>} with two object paths:
 * <ul>
 *   <li>{@code /StatusNotifierItem} — SNI properties and actions</li>
 *   <li>{@code /MenuBar} — com.canonical.dbusmenu context menu</li>
 * </ul>
 *
 * <p><b>Known limitation — MouseListener and JPopupMenu workaround:</b><br>
 * On desktops that use DBusMenu to render the context menu (e.g. GNOME with
 * ubuntu-appindicators), right-click events are intercepted by the desktop shell
 * and never delivered as {@code MouseEvent} to the {@code TrayIcon}. The common
 * workaround of adding a {@code MouseListener} and showing a {@code JPopupMenu}
 * manually on right-click therefore does not work on those desktops.
 * The official {@code TrayIcon.setPopupMenu(PopupMenu)} API is fully supported.
 * On desktops that call {@code ContextMenu(x,y)} directly (e.g. KDE Plasma),
 * mouse events are delivered normally and the workaround continues to work.
 * On Windows, mouse events are always delivered regardless of popup menu usage.
 *
 * See JDK-8035556.
 */
public final class SNITrayIconPeer implements TrayIconPeer {

    private static final PlatformLogger log =
        PlatformLogger.getLogger("sun.awt.X11.SNITrayIconPeer");

    private static final String SNI_WATCHER_BUS   = "org.kde.StatusNotifierWatcher";
    private static final String SNI_WATCHER_PATH  = "/StatusNotifierWatcher";
    private static final String SNI_WATCHER_IFACE = "org.kde.StatusNotifierWatcher";

    private static final String SNI_PATH   = "/StatusNotifierItem";
    private static final String SNI_IFACE  = "org.kde.StatusNotifierItem";
    private static final String PROP_IFACE = "org.freedesktop.DBus.Properties";
    private static final String INTRO_IFACE = "org.freedesktop.DBus.Introspectable";

    private static final String MENU_PATH  = "/MenuBar";
    private static final String MENU_IFACE = "com.canonical.dbusmenu";

    private static final AtomicInteger counter = new AtomicInteger(1);

    // Timeout for the synchronous D-Bus probe in isWatcherAvailable().
    // Short enough to keep SystemTray.isSupported() responsive on the EDT,
    // yet long enough for a healthy session bus to reply.
    private static final int WATCHER_PROBE_TIMEOUT_MS = 500;

    private final TrayIcon target;
    private final SNIDBusConn conn;
    private final String serviceName;
    private volatile boolean disposed;

    // Current state — updated by updateImage() and setToolTip()
    private volatile String iconName = "java";
    private volatile IconData iconData;  // null until first updateImage()
    private volatile String tooltip = "";

    /** Immutable snapshot of icon pixel data — published atomically via volatile write. */
    private record IconData(int width, int height, int[] pixels) {}

    SNITrayIconPeer(TrayIcon target) throws AWTException {
        this.target = target;

        long pid = ProcessHandle.current().pid();
        int n = counter.getAndIncrement();
        this.serviceName = "org.kde.StatusNotifierItem-" + pid + "-" + n;
        log.fine("SNI creating peer: " + serviceName);

        SNIDBusConn c = SNIDBusConn.connectSession();
        if (c == null) {
            throw new AWTException("SNI: cannot connect to D-Bus session bus");
        }
        this.conn = c;

        try {
            conn.requestName(serviceName);
            conn.registerObject(SNI_PATH,  this::handleSNI);
            conn.registerObject(MENU_PATH, this::handleMenu);

            Thread.ofPlatform().daemon(true)
                .name("SNI-dispatch-" + n)
                .start(conn::runDispatchLoop);

            conn.awaitDispatch();

            registerWithWatcher();
            installWatcherRestartListener();
            updateImage();
            log.fine("SNI peer ready: " + serviceName);
        } catch (RuntimeException e) {
            log.severe("SNI D-Bus setup failed: " + e.getMessage());
            conn.close(serviceName, SNI_PATH, MENU_PATH);
            throw new AWTException("SNI: D-Bus setup failed: " + e.getMessage());
        }
    }

    // --- TrayIconPeer ---

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        conn.close(serviceName, SNI_PATH, MENU_PATH);
    }

    @Override
    public void setToolTip(String tooltip) {
        this.tooltip = (tooltip != null) ? tooltip : "";
        emitSignal("NewTitle");
        emitSignal("NewToolTip");
    }

    @Override
    public void updateImage() {
        Image img = target.getImage();
        if (img != null) {
            convertImage(img);
        }
        emitSignal("NewIcon");
    }

    @Override
    public void displayMessage(String caption, String text, String messageType) {
        try (Arena tmp = Arena.ofConfined();
             SNIMsg call = SNIMsg.newMethodCall(tmp,
                 "org.freedesktop.Notifications",
                 "/org/freedesktop/Notifications",
                 "org.freedesktop.Notifications",
                 "Notify")) {
            call.appendString("java");           // app_name
            call.appendUInt32(0);                // replaces_id (0 = new notification)
            call.appendString("");               // app_icon
            call.appendString(caption != null ? caption : "");
            call.appendString(text    != null ? text    : "");
            // actions: empty array of strings
            var actions = call.openContainer(SNIDBusLib.DBUS_TYPE_ARRAY, "s");
            call.closeContainer(actions);
            // hints: urgency byte
            var hints = call.openContainer(SNIDBusLib.DBUS_TYPE_ARRAY, "{sv}");
            appendUrgencyHint(call, hints, urgencyFor(messageType));
            call.closeContainer(hints);
            call.appendInt32(notificationTimeout(messageType));
            conn.sendNoReply(call);
        } catch (Exception e) {
            log.warning("SNI displayMessage failed: " + e.getMessage());
        }
    }

    private static byte urgencyFor(String messageType) {
        return switch (messageType != null ? messageType : "") {
            case "ERROR"   -> (byte) 2;  // critical
            case "WARNING" -> (byte) 1;  // normal
            default        -> (byte) 1;  // normal (INFO, NONE)
        };
    }

    private static int notificationTimeout(String messageType) {
        // -1 = server default; ERROR stays until dismissed
        return "ERROR".equals(messageType) ? 0 : -1;
    }

    private static void appendUrgencyHint(SNIMsg msg, MemorySegment hintsIter, byte urgency) {
        Arena a = msg.arena();
        var entry = SNIMsg.openContainerOn(a, hintsIter, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, "urgency");
        var variant = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "y");
        MemorySegment holder = a.allocate(java.lang.foreign.ValueLayout.JAVA_BYTE);
        holder.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0, urgency);
        try { SNIDBusLib.dbus_message_iter_append_basic
                .invoke(variant, SNIDBusLib.DBUS_TYPE_BYTE, holder); }
        catch (Throwable t) { throw new RuntimeException(t); }
        SNIMsg.closeContainerOn(entry, variant);
        SNIMsg.closeContainerOn(hintsIter, entry);
    }

    @Override
    public void showPopupMenu(int x, int y) {
        // With SNI + DBusMenu the tray host (GNOME Shell, KDE) handles the
        // popup directly by calling GetLayout on /MenuBar. No X11 popup needed.
    }

    // --- Watcher registration ---

    private void registerWithWatcher() {
        try (Arena tmp = Arena.ofConfined();
             SNIMsg call = SNIMsg.newMethodCall(tmp,
                 SNI_WATCHER_BUS, SNI_WATCHER_PATH,
                 SNI_WATCHER_IFACE, "RegisterStatusNotifierItem")) {
            call.appendString(serviceName);
            conn.sendNoReply(call);
            log.fine("SNI RegisterStatusNotifierItem sent");
        } catch (Exception e) {
            log.warning("SNI registerWithWatcher failed: " + e.getMessage());
        }
    }

    /**
     * Subscribe to {@code NameOwnerChanged} signals for the SNI watcher bus
     * name. If the watcher service disappears and reappears (for example when
     * the GNOME shell extension is reloaded), the icon is re-registered
     * automatically so that it stays visible in the tray.
     */
    private void installWatcherRestartListener() {
        String matchRule = "type='signal',sender='org.freedesktop.DBus',"
            + "interface='org.freedesktop.DBus',member='NameOwnerChanged',"
            + "arg0='" + SNI_WATCHER_BUS + "'";
        try {
            conn.installFilter(matchRule, this::handleNameOwnerChanged);
        } catch (Exception e) {
            log.warning("SNI watcher restart listener install failed: " + e.getMessage());
        }
    }

    /**
     * Filter handler for {@code NameOwnerChanged}. When the watcher's
     * {@code new_owner} argument is non-empty the service has just appeared
     * on the bus, so we re-send our {@code RegisterStatusNotifierItem} call.
     */
    private boolean handleNameOwnerChanged(SNIMsg msg) {
        if (disposed) return false;
        // The libdbus filter sees every message on the connection. Restrict to
        // the actual NameOwnerChanged signal before parsing arguments — reading
        // string args from a method call with a different signature would make
        // libdbus call abort() and kill the JVM.
        if (msg.getType() != SNIDBusLib.DBUS_MESSAGE_TYPE_SIGNAL) return false;
        if (!"org.freedesktop.DBus".equals(msg.getIface())) return false;
        if (!"NameOwnerChanged".equals(msg.getMember())) return false;
        try {
            String name = msg.readString();
            if (!SNI_WATCHER_BUS.equals(name)) return false;
            msg.readString(); // old_owner, unused
            String newOwner = msg.readString();
            if (newOwner != null && !newOwner.isEmpty()) {
                log.fine("SNI watcher reappeared as " + newOwner + ", re-registering");
                registerWithWatcher();
            }
        } catch (Throwable t) {
            log.fine("SNI NameOwnerChanged parse failed: " + t);
        }
        // Don't consume — the message may be of interest to other handlers.
        return false;
    }

    // --- D-Bus message handlers ---

    private boolean handleSNI(SNIMsg msg) {
        if (disposed) return false;
        String member = msg.getMember();
        if (member == null) return false;

        try {
            return switch (member) {
                case "Introspect"        -> { replyIntrospect(msg, SNI_INTROSPECT_XML); yield true; }
                case "Get"               -> { replyGet(msg); yield true; }
                case "GetAll"            -> { replyGetAll(msg); yield true; }
                case "Activate"          -> { handleActivate(msg); yield true; }
                case "SecondaryActivate" -> { replyVoid(msg); yield true; }
                case "ContextMenu"       -> { replyVoid(msg); yield true; }
                case "Scroll"            -> { replyVoid(msg); yield true; }
                default -> false;
            };
        } catch (Throwable t) {
            log.severe("SNI handler failed for member=" + member + ": " + t);
            return false;
        }
    }

    private boolean handleMenu(SNIMsg msg) {
        if (disposed) return false;
        String member = msg.getMember();
        if (member == null) return false;

        try {
            return switch (member) {
                case "Introspect"         -> { replyIntrospect(msg, MENU_INTROSPECT_XML); yield true; }
                case "GetLayout"          -> { replyGetLayout(msg); yield true; }
                case "AboutToShow"        -> { replyAboutToShow(msg); yield true; }
                case "GetGroupProperties" -> { replyGetGroupProperties(msg); yield true; }
                case "Event"              -> { handleMenuEvent(msg); yield true; }
                case "EventGroup"         -> { replyVoid(msg); yield true; }
                default -> false;
            };
        } catch (Throwable t) {
            log.severe("SNI menu handler failed for member=" + member + ": " + t);
            return false;
        }
    }

    // --- SNI property replies ---

    private void replyGet(SNIMsg req) {
        req.readString(); // skip interface arg
        String prop = req.readString();
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            appendVariantProperty(reply, prop);
            reply.send(conn.conn);
        }
    }

    private void replyGetAll(SNIMsg req) {
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            var arr = reply.openContainer(SNIDBusLib.DBUS_TYPE_ARRAY, "{sv}");
            appendDictStr(reply, arr,  "Id",       serviceName);
            appendDictStr(reply, arr,  "Title",    getTitle());
            appendDictStr(reply, arr,  "Status",   "Active");
            appendDictStr(reply, arr,  "Category", "ApplicationStatus");
            appendDictStr(reply, arr,  "IconName", iconName);
            appendDictObj(reply, arr,  "Menu",     MENU_PATH);
            appendDictBool(reply, arr, "ItemIsMenu", false);
            appendIconPixmap(reply, arr);
            appendToolTip(reply, arr);
            reply.closeContainer(arr);
            reply.send(conn.conn);
        }
    }

    private void appendVariantProperty(SNIMsg reply, String prop) {
        Arena a = reply.arena();
        switch (prop) {
            case "Id" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "s");
                SNIMsg.appendStringOn(a, v, serviceName);
                reply.closeContainer(v);
            }
            case "Title" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "s");
                SNIMsg.appendStringOn(a, v, getTitle());
                reply.closeContainer(v);
            }
            case "Status" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "s");
                SNIMsg.appendStringOn(a, v, "Active");
                reply.closeContainer(v);
            }
            case "Category" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "s");
                SNIMsg.appendStringOn(a, v, "ApplicationStatus");
                reply.closeContainer(v);
            }
            case "IconName" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "s");
                SNIMsg.appendStringOn(a, v, iconName);
                reply.closeContainer(v);
            }
            case "Menu" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "o");
                SNIMsg.appendObjectPathOn(a, v, MENU_PATH);
                reply.closeContainer(v);
            }
            case "ItemIsMenu" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "b");
                SNIMsg.appendBoolOn(a, v, false);
                reply.closeContainer(v);
            }
            case "IconPixmap" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "a(iiay)");
                appendPixmapArray(a, v);
                reply.closeContainer(v);
            }
            case "ToolTip" -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "(sa(iiay)ss)");
                appendToolTipStruct(a, v);
                reply.closeContainer(v);
            }
            default -> {
                var v = reply.openContainer(SNIDBusLib.DBUS_TYPE_VARIANT, "s");
                SNIMsg.appendStringOn(a, v, "");
                reply.closeContainer(v);
            }
        }
    }

    private void appendPixmapArray(Arena a, MemorySegment variantIter) {
        appendPixmapArrayFrom(a, variantIter, iconData);
    }

    private static void appendPixmapArrayFrom(Arena a, MemorySegment iter, IconData data) {
        var pixArr = SNIMsg.openContainerOn(a, iter, SNIDBusLib.DBUS_TYPE_ARRAY, "(iiay)");
        if (data != null) {
            var pixStruct = SNIMsg.openContainerOn(a, pixArr, SNIDBusLib.DBUS_TYPE_STRUCT, null);
            SNIMsg.appendInt32On(a, pixStruct, data.width());
            SNIMsg.appendInt32On(a, pixStruct, data.height());
            appendPixelBytes(a, pixStruct, data.pixels());
            SNIMsg.closeContainerOn(pixArr, pixStruct);
        }
        SNIMsg.closeContainerOn(iter, pixArr);
    }

    private void appendToolTipStruct(Arena a, MemorySegment variantIter) {
        String tt = tooltip;
        var ttStruct = SNIMsg.openContainerOn(a, variantIter, SNIDBusLib.DBUS_TYPE_STRUCT, null);
        SNIMsg.appendStringOn(a, ttStruct, "");
        var emptyArr = SNIMsg.openContainerOn(a, ttStruct, SNIDBusLib.DBUS_TYPE_ARRAY, "(iiay)");
        SNIMsg.closeContainerOn(ttStruct, emptyArr);
        SNIMsg.appendStringOn(a, ttStruct, (tt != null) ? tt : "");
        SNIMsg.appendStringOn(a, ttStruct, "");
        SNIMsg.closeContainerOn(variantIter, ttStruct);
    }

    @SuppressWarnings("restricted")
    private static void appendPixelBytes(Arena a, MemorySegment structIter, int[] pixels) {
        var byteArr = SNIMsg.openContainerOn(a, structIter, SNIDBusLib.DBUS_TYPE_ARRAY, "y");
        MemorySegment bHolder = a.allocate(java.lang.foreign.ValueLayout.JAVA_BYTE);
        for (int argb : pixels) {
            for (int shift = 24; shift >= 0; shift -= 8) {
                bHolder.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0, (byte)((argb >> shift) & 0xff));
                try { SNIDBusLib.dbus_message_iter_append_basic
                        .invoke(byteArr, SNIDBusLib.DBUS_TYPE_BYTE, bHolder); }
                catch (Throwable t) { throw new RuntimeException(t); }
            }
        }
        SNIMsg.closeContainerOn(structIter, byteArr);
    }

    // --- DBusMenu replies ---

    private void replyGetLayout(SNIMsg req) {
        // Read and discard input args (parentId, recursionDepth, propertyNames)
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            reply.appendUInt32(1); // revision
            var root = reply.openContainer(SNIDBusLib.DBUS_TYPE_STRUCT, null);
            SNIMsg.appendInt32On(reply.arena(), root, 0); // root id
            // root properties
            var rootProps = SNIMsg.openContainerOn(reply.arena(), root,
                SNIDBusLib.DBUS_TYPE_ARRAY, "{sv}");
            SNIMsg.closeContainerOn(root, rootProps);
            // children
            buildMenuChildren(reply, root);
            reply.closeContainer(root);
            reply.send(conn.conn);
        }
    }

    private void replyAboutToShow(SNIMsg req) {
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            reply.appendBool(false);
            reply.send(conn.conn);
        }
    }

    private void replyGetGroupProperties(SNIMsg req) {
        // Return empty array — optional method
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            var arr = reply.openContainer(SNIDBusLib.DBUS_TYPE_ARRAY, "(ia{sv})");
            reply.closeContainer(arr);
            reply.send(conn.conn);
        }
    }

    private void handleMenuEvent(SNIMsg req) {
        int id       = req.readInt32();
        String event = req.readString();
        replyVoid(req);

        if ("clicked".equals(event)) {
            PopupMenu pm = target.getPopupMenu();
            if (pm != null) {
                MenuItem item = findMenuItem(pm, id);
                if (item != null && item.isEnabled()) {
                    if (item instanceof CheckboxMenuItem cb) {
                        boolean newState = !cb.getState();
                        cb.setState(newState);
                        ItemEvent ie = new ItemEvent(cb,
                            ItemEvent.ITEM_STATE_CHANGED,
                            cb.getLabel(),
                            newState ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
                        XToolkit.postEvent(ie);
                        emitLayoutUpdated();
                    } else {
                        ActionEvent ae = new ActionEvent(item,
                            ActionEvent.ACTION_PERFORMED,
                            item.getActionCommand());
                        XToolkit.postEvent(ae);
                    }
                }
            }
        }
    }

    private void handleActivate(SNIMsg req) {
        replyVoid(req);
        ActionEvent ae = new ActionEvent(target,
            ActionEvent.ACTION_PERFORMED,
            target.getActionCommand());
        XToolkit.postEvent(ae);
    }

    // --- Menu building from PopupMenu ---

    private void buildMenuChildren(SNIMsg msg, MemorySegment structIter) {
        var arr = SNIMsg.openContainerOn(msg.arena(), structIter,
            SNIDBusLib.DBUS_TYPE_ARRAY, "v");
        PopupMenu pm = target.getPopupMenu();
        if (pm != null) {
            appendMenuItems(msg, arr, pm, new int[]{1});
        }
        SNIMsg.closeContainerOn(structIter, arr);
    }

    private void appendMenuItems(SNIMsg msg, MemorySegment arrIter,
                                 Menu menu, int[] idCounter) {
        for (MenuItem item : snapshotMenu(menu)) {
            int id = idCounter[0]++;
            appendMenuItem(msg, arrIter, id, item, idCounter);
        }
    }

    /**
     * Returns a best-effort snapshot of the items currently in {@code menu}.
     *
     * <p>The EDT may mutate the menu concurrently via {@code Menu.add}/
     * {@code Menu.remove}, which lock on the AWT tree lock
     * ({@code Component.LOCK}). That lock is package-private to
     * {@code java.awt} and cannot be acquired from this peer, so iterating
     * the menu with {@code getItemCount()} + {@code getItem(i)} can race
     * with a concurrent remove and throw {@code ArrayIndexOutOfBoundsException}.
     *
     * <p>We swallow that exception and return whatever we collected so far —
     * the desktop will see a slightly truncated menu for one call, and a
     * subsequent {@code LayoutUpdated} signal (or the next {@code GetLayout})
     * will refresh it with the consistent state.
     */
    private static List<MenuItem> snapshotMenu(Menu menu) {
        int count = menu.getItemCount();
        List<MenuItem> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            try {
                result.add(menu.getItem(i));
            } catch (ArrayIndexOutOfBoundsException ex) {
                // Menu shrunk concurrently; stop here.
                break;
            }
        }
        return result;
    }

    private void appendMenuItem(SNIMsg msg, MemorySegment arrIter,
                                int id, MenuItem item, int[] idCounter) {
        Arena a = msg.arena();
        var variant = SNIMsg.openContainerOn(a, arrIter,
            SNIDBusLib.DBUS_TYPE_VARIANT, "(ia{sv}av)");
        var node = SNIMsg.openContainerOn(a, variant, SNIDBusLib.DBUS_TYPE_STRUCT, null);
        SNIMsg.appendInt32On(a, node, id);

        var props = SNIMsg.openContainerOn(a, node, SNIDBusLib.DBUS_TYPE_ARRAY, "{sv}");
        if ("-".equals(item.getLabel())) {
            appendDictEntryStrOn(a, props, "type", "separator");
        } else {
            appendDictEntryStrOn(a, props, "label",   item.getLabel());
            appendDictEntryBoolOn(a, props, "enabled", item.isEnabled());
            appendDictEntryBoolOn(a, props, "visible", true);
            if (item instanceof CheckboxMenuItem cb) {
                appendDictEntryStrOn(a, props, "toggle-type", "checkmark");
                appendDictEntryInt32On(a, props, "toggle-state", cb.getState() ? 1 : 0);
            }
            if (item instanceof Menu) {
                appendDictEntryStrOn(a, props, "children-display", "submenu");
            }
        }
        SNIMsg.closeContainerOn(node, props);

        var children = SNIMsg.openContainerOn(a, node, SNIDBusLib.DBUS_TYPE_ARRAY, "v");
        if (item instanceof Menu subMenu) {
            appendMenuItems(msg, children, subMenu, idCounter);
        }
        SNIMsg.closeContainerOn(node, children);

        SNIMsg.closeContainerOn(variant, node);
        SNIMsg.closeContainerOn(arrIter, variant);
    }

    // --- MenuItem lookup by DBusMenu id ---

    private MenuItem findMenuItem(PopupMenu pm, int targetId) {
        return findInMenu(pm, targetId, new int[]{1});
    }

    private MenuItem findInMenu(Menu menu, int targetId, int[] counter) {
        for (MenuItem item : snapshotMenu(menu)) {
            int id = counter[0]++;
            if (id == targetId) return item;
            if (item instanceof Menu sub) {
                MenuItem found = findInMenu(sub, targetId, counter);
                if (found != null) return found;
            }
        }
        return null;
    }

    // --- Image conversion ---

    private void convertImage(Image img) {
        int size = SNISystemTrayPeer.ICON_SIZE;
        BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        try {
            g.drawImage(img, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        iconData = new IconData(size, size, bi.getRGB(0, 0, size, size, null, 0, size));
        iconName = "";  // force desktop to use IconPixmap instead of a named icon
    }

    // --- IconPixmap and ToolTip property appenders ---

    private void appendIconPixmap(SNIMsg msg, MemorySegment arrIter) {
        IconData data = iconData;
        if (data == null) return;

        Arena a = msg.arena();
        var entry = SNIMsg.openContainerOn(a, arrIter, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, "IconPixmap");
        var variant = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "a(iiay)");
        appendPixmapArrayFrom(a, variant, data);
        SNIMsg.closeContainerOn(entry, variant);
        SNIMsg.closeContainerOn(arrIter, entry);
    }

    private void appendToolTip(SNIMsg msg, MemorySegment arrIter) {
        String tt = tooltip;
        if (tt == null || tt.isEmpty()) return;
        Arena a = msg.arena();
        var entry = SNIMsg.openContainerOn(a, arrIter, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, "ToolTip");
        var variant = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "(sa(iiay)ss)");
        var ttStruct = SNIMsg.openContainerOn(a, variant, SNIDBusLib.DBUS_TYPE_STRUCT, null);
        SNIMsg.appendStringOn(a, ttStruct, "");             // icon name (unused)
        var emptyPixArr = SNIMsg.openContainerOn(a, ttStruct, SNIDBusLib.DBUS_TYPE_ARRAY, "(iiay)");
        SNIMsg.closeContainerOn(ttStruct, emptyPixArr);
        SNIMsg.appendStringOn(a, ttStruct, tt);             // title
        SNIMsg.appendStringOn(a, ttStruct, "");             // description
        SNIMsg.closeContainerOn(variant, ttStruct);
        SNIMsg.closeContainerOn(entry, variant);
        SNIMsg.closeContainerOn(arrIter, entry);
    }

    // --- Signal emission ---

    private void emitLayoutUpdated() {
        try (Arena tmp = Arena.ofConfined();
             SNIMsg sig = SNIMsg.newSignal(tmp, MENU_PATH, MENU_IFACE, "LayoutUpdated")) {
            sig.appendUInt32(1); // revision
            sig.appendInt32(0);  // parent id (root)
            conn.sendNoReply(sig);
        } catch (Exception e) {
            log.fine("SNI: failed to emit LayoutUpdated: " + e.getMessage());
        }
    }

    private void emitSignal(String signalName) {
        try (Arena tmp = Arena.ofConfined();
             SNIMsg sig = SNIMsg.newSignal(tmp, SNI_PATH, SNI_IFACE, signalName)) {
            conn.sendNoReply(sig);
        } catch (Exception e) {
            if (log.isLoggable(PlatformLogger.Level.FINE)) {
                log.fine("SNI: failed to emit signal " + signalName + ": " + e.getMessage());
            }
        }
    }

    // --- Generic reply helpers ---

    private void replyVoid(SNIMsg req) {
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            reply.send(conn.conn);
        }
    }

    private void replyIntrospect(SNIMsg req, String xml) {
        try (SNIMsg reply = SNIMsg.newMethodReturn(req)) {
            reply.appendString(xml);
            reply.send(conn.conn);
        }
    }

    // --- Dict-entry appenders for GetAll / openContainer variants ---

    private void appendDictStr(SNIMsg msg, MemorySegment arr, String key, String value) {
        Arena a = msg.arena();
        var entry = SNIMsg.openContainerOn(a, arr, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, key);
        var v = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "s");
        SNIMsg.appendStringOn(a, v, value);
        SNIMsg.closeContainerOn(entry, v);
        SNIMsg.closeContainerOn(arr, entry);
    }

    private void appendDictObj(SNIMsg msg, MemorySegment arr, String key, String value) {
        Arena a = msg.arena();
        var entry = SNIMsg.openContainerOn(a, arr, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, key);
        var v = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "o");
        SNIMsg.appendObjectPathOn(a, v, value);
        SNIMsg.closeContainerOn(entry, v);
        SNIMsg.closeContainerOn(arr, entry);
    }

    private void appendDictBool(SNIMsg msg, MemorySegment arr, String key, boolean value) {
        Arena a = msg.arena();
        var entry = SNIMsg.openContainerOn(a, arr, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, key);
        var v = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "b");
        SNIMsg.appendBoolOn(a, v, value);
        SNIMsg.closeContainerOn(entry, v);
        SNIMsg.closeContainerOn(arr, entry);
    }

    private void appendDictEntryStrOn(Arena a, MemorySegment propsArr,
                                      String key, String value) {
        var entry = SNIMsg.openContainerOn(a, propsArr, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, key);
        var v = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "s");
        SNIMsg.appendStringOn(a, v, value);
        SNIMsg.closeContainerOn(entry, v);
        SNIMsg.closeContainerOn(propsArr, entry);
    }

    private void appendDictEntryBoolOn(Arena a, MemorySegment propsArr,
                                       String key, boolean value) {
        var entry = SNIMsg.openContainerOn(a, propsArr, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, key);
        var v = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "b");
        SNIMsg.appendBoolOn(a, v, value);
        SNIMsg.closeContainerOn(entry, v);
        SNIMsg.closeContainerOn(propsArr, entry);
    }

    private void appendDictEntryInt32On(Arena a, MemorySegment propsArr,
                                        String key, int value) {
        var entry = SNIMsg.openContainerOn(a, propsArr, SNIDBusLib.DBUS_TYPE_DICT_ENTRY, null);
        SNIMsg.appendStringOn(a, entry, key);
        var v = SNIMsg.openContainerOn(a, entry, SNIDBusLib.DBUS_TYPE_VARIANT, "i");
        SNIMsg.appendInt32On(a, v, value);
        SNIMsg.closeContainerOn(entry, v);
        SNIMsg.closeContainerOn(propsArr, entry);
    }

    // --- Utility ---

    private String getTitle() {
        String tt = tooltip;
        return (tt != null && !tt.isEmpty()) ? tt : "Java";
    }

    /**
     * Probes the session bus for {@code org.kde.StatusNotifierWatcher}.
     * Opens a temporary private connection, sends a synchronous
     * {@code GetNameOwner} call with a {@link #WATCHER_PROBE_TIMEOUT_MS}
     * timeout, and closes the connection immediately afterwards.
     * Called from the EDT during {@code SystemTray.isSupported()}, so the
     * timeout must be short enough to keep the UI responsive.
     *
     * @return {@code true} if the watcher service is present on the bus
     */
    static boolean isWatcherAvailable() {
        SNIDBusConn c = SNIDBusConn.connectSession();
        if (c == null) return false;
        try (Arena tmp = Arena.ofConfined();
             SNIMsg call = SNIMsg.newMethodCall(tmp,
                 "org.freedesktop.DBus", "/org/freedesktop/DBus",
                 "org.freedesktop.DBus", "GetNameOwner")) {
            call.appendString(SNI_WATCHER_BUS);
            try (Arena errArena = Arena.ofConfined()) {
                MemorySegment error = errArena.allocate(SNIDBusLib.ERROR_LAYOUT);
                SNIDBusLib.dbus_error_init.invoke(error);
                MemorySegment replyPtr = (MemorySegment)
                    SNIDBusLib.dbus_connection_send_with_reply_and_block
                        .invoke(c.conn, call.ptr, WATCHER_PROBE_TIMEOUT_MS, error);
                int errSet = (int) SNIDBusLib.dbus_error_is_set.invoke(error);
                if (errSet != 0) {
                    SNIDBusLib.dbus_error_free.invoke(error);
                    return false;
                }
                if (replyPtr == null || replyPtr.equals(MemorySegment.NULL)) return false;
                try (SNIMsg _ = SNIMsg.own(replyPtr)) {
                    return true;
                }
            }
        } catch (Throwable e) {
            return false;
        } finally {
            c.closePrivate();
        }
    }

    // --- Introspection XML ---

    private static final String SNI_INTROSPECT_XML = """
        <!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
         "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
        <node>
          <interface name="org.kde.StatusNotifierItem">
            <method name="Activate">
              <arg type="i" direction="in"/><arg type="i" direction="in"/>
            </method>
            <method name="SecondaryActivate">
              <arg type="i" direction="in"/><arg type="i" direction="in"/>
            </method>
            <method name="ContextMenu">
              <arg type="i" direction="in"/><arg type="i" direction="in"/>
            </method>
            <method name="Scroll">
              <arg type="i" direction="in"/><arg type="s" direction="in"/>
            </method>
            <property name="Id"          type="s" access="read"/>
            <property name="Title"       type="s" access="read"/>
            <property name="Status"      type="s" access="read"/>
            <property name="Category"    type="s" access="read"/>
            <property name="IconName"    type="s" access="read"/>
            <property name="IconPixmap"  type="a(iiay)" access="read"/>
            <property name="ToolTip"     type="(sa(iiay)ss)" access="read"/>
            <property name="Menu"        type="o" access="read"/>
            <property name="ItemIsMenu"  type="b" access="read"/>
            <signal name="NewIcon"/>
            <signal name="NewTitle"/>
            <signal name="NewStatus">  <arg type="s"/></signal>
            <signal name="NewToolTip"/>
          </interface>
          <interface name="org.freedesktop.DBus.Properties">
            <method name="Get">
              <arg type="s" direction="in"/><arg type="s" direction="in"/>
              <arg type="v" direction="out"/>
            </method>
            <method name="GetAll">
              <arg type="s" direction="in"/>
              <arg type="a{sv}" direction="out"/>
            </method>
          </interface>
          <interface name="org.freedesktop.DBus.Introspectable">
            <method name="Introspect"><arg type="s" direction="out"/></method>
          </interface>
        </node>
        """;

    private static final String MENU_INTROSPECT_XML = """
        <!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
         "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
        <node>
          <interface name="com.canonical.dbusmenu">
            <method name="GetLayout">
              <arg type="i" direction="in"/>
              <arg type="i" direction="in"/>
              <arg type="as" direction="in"/>
              <arg type="u" direction="out"/>
              <arg type="(ia{sv}av)" direction="out"/>
            </method>
            <method name="GetGroupProperties">
              <arg type="ai" direction="in"/>
              <arg type="as" direction="in"/>
              <arg type="a(ia{sv})" direction="out"/>
            </method>
            <method name="Event">
              <arg type="i" direction="in"/>
              <arg type="s" direction="in"/>
              <arg type="v" direction="in"/>
              <arg type="u" direction="in"/>
            </method>
            <method name="EventGroup">
              <arg type="a(isvu)" direction="in"/>
              <arg type="ai"      direction="out"/>
            </method>
            <method name="AboutToShow">
              <arg type="i" direction="in"/>
              <arg type="b" direction="out"/>
            </method>
            <signal name="LayoutUpdated">
              <arg type="u"/><arg type="i"/>
            </signal>
            <signal name="ItemsPropertiesUpdated">
              <arg type="a(ia{sv})"/>
              <arg type="a(ias)"/>
            </signal>
          </interface>
          <interface name="org.freedesktop.DBus.Introspectable">
            <method name="Introspect"><arg type="s" direction="out"/></method>
          </interface>
        </node>
        """;
}
