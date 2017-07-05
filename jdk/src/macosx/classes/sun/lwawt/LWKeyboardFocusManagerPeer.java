/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;

import java.util.Map;
import java.util.HashMap;

import sun.awt.AWTAccessor;
import sun.awt.AppContext;
import sun.awt.KeyboardFocusManagerPeerImpl;

public class LWKeyboardFocusManagerPeer extends KeyboardFocusManagerPeerImpl {

    private Object lock = new Object();
    private LWWindowPeer focusedWindow;
    private LWComponentPeer focusOwner;

    private static Map<KeyboardFocusManager, LWKeyboardFocusManagerPeer> instances =
        new HashMap<KeyboardFocusManager, LWKeyboardFocusManagerPeer>();

    public static synchronized LWKeyboardFocusManagerPeer getInstance(AppContext ctx) {
        return getInstance(AWTAccessor.getKeyboardFocusManagerAccessor().
                           getCurrentKeyboardFocusManager(ctx));
    }

    public static synchronized LWKeyboardFocusManagerPeer getInstance(KeyboardFocusManager manager) {
        LWKeyboardFocusManagerPeer instance = instances.get(manager);
        if (instance == null) {
            instance = new LWKeyboardFocusManagerPeer(manager);
            instances.put(manager, instance);
        }
        return instance;
    }

    public LWKeyboardFocusManagerPeer(KeyboardFocusManager manager) {
        super(manager);
    }

    @Override
    public Window getCurrentFocusedWindow() {
        synchronized (lock) {
            return (focusedWindow != null) ? (Window)focusedWindow.getTarget() : null;
        }
    }

    @Override
    public Component getCurrentFocusOwner() {
        synchronized (lock) {
            return (focusOwner != null) ? focusOwner.getTarget() : null;
        }
    }

    @Override
    public void setCurrentFocusOwner(Component comp) {
        synchronized (lock) {
            focusOwner = (comp != null) ? (LWComponentPeer)comp.getPeer() : null;
        }
    }

    void setFocusedWindow(LWWindowPeer peer) {
        synchronized (lock) {
            focusedWindow = peer;
        }
    }

    LWWindowPeer getFocusedWindow() {
        synchronized (lock) {
            return focusedWindow;
        }
    }

    void setFocusOwner(LWComponentPeer peer) {
        synchronized (lock) {
            focusOwner = peer;
        }
    }

    LWComponentPeer getFocusOwner() {
        synchronized (lock) {
            return focusOwner;
        }
    }
}
