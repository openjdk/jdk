/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.pipe.hw;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to notify listeners about accelerated device's
 * events such as device reset or dispose that are about to occur.
 */
public class AccelDeviceEventNotifier {

    private static AccelDeviceEventNotifier theInstance;

    /**
     * A device is about to be reset. The listeners have to release all
     * resources associated with the device which are required for the device
     * to be reset.
     */
    public static final int DEVICE_RESET = 0;

    /**
     * A device is about to be disposed. The listeners have to release all
     * resources associated with the device.
     */
    public static final int DEVICE_DISPOSED = 1;

    private final Map<AccelDeviceEventListener, Integer> listeners;

    private AccelDeviceEventNotifier() {
        listeners = Collections.synchronizedMap(
            new HashMap<AccelDeviceEventListener, Integer>(1));
    }

    /**
     * Returns a singleton of AccelDeviceEventNotifier if it exists. If the
     * passed boolean is false and singleton doesn't exist yet, null is
     * returned. If the passed boolean is {@code true} and singleton doesn't
     * exist it will be created and returned.
     *
     * @param create whether to create a singleton instance if doesn't yet
     * exist
     * @return a singleton instance or null
     */
    private static synchronized
        AccelDeviceEventNotifier getInstance(boolean create)
    {
        if (theInstance == null && create) {
            theInstance = new AccelDeviceEventNotifier();
        }
        return theInstance;
    }

    /**
     * Called to indicate that a device event had occured.
     * If a singleton exists, the listeners (those associated with
     * the device) will be notified.
     *
     * @param screen a screen number of the device which is a source of
     * the event
     * @param eventType a type of the event
     * @see #DEVICE_DISPOSED
     * @see #DEVICE_RESET
     */
    public static final void eventOccured(int screen, int eventType) {
        AccelDeviceEventNotifier notifier = getInstance(false);
        if (notifier != null) {
            notifier.notifyListeners(eventType, screen);
        }
    }

    /**
     * Adds the listener associated with a device on particular screen.
     *
     * Note: the listener must be removed as otherwise it will forever
     * be referenced by the notifier.
     *
     * @param l the listener
     * @param screen the screen number indicating which device the listener is
     * interested in.
     */
    public static final void addListener(AccelDeviceEventListener l,int screen){
        getInstance(true).add(l, screen);
    }

    /**
     * Removes the listener.
     *
     * @param l the listener
     */
    public static final void removeListener(AccelDeviceEventListener l) {
        getInstance(true).remove(l);
    }

    private final void add(AccelDeviceEventListener theListener, int screen) {
        listeners.put(theListener, screen);
    }
    private final void remove(AccelDeviceEventListener theListener) {
        listeners.remove(theListener);
    }

    /**
     * Notifies the listeners associated with the screen's device about the
     * event.
     *
     * Implementation note: the current list of listeners is first duplicated
     * which allows the listeners to remove themselves during the iteration.
     *
     * @param screen a screen number with which the device which is a source of
     * the event is associated with
     * @param eventType a type of the event
     * @see #DEVICE_DISPOSED
     * @see #DEVICE_RESET
     */
    private final void notifyListeners(int deviceEventType, int screen) {
        HashMap<AccelDeviceEventListener, Integer> listClone;
        Set<AccelDeviceEventListener> cloneSet;

        synchronized(listeners) {
            listClone =
                new HashMap<AccelDeviceEventListener, Integer>(listeners);
        }

        cloneSet = listClone.keySet();
        Iterator<AccelDeviceEventListener> itr = cloneSet.iterator();
        while (itr.hasNext()) {
            AccelDeviceEventListener current = itr.next();
            Integer i = listClone.get(current);
            // only notify listeners which are interested in this device
            if (i != null && i.intValue() != screen) {
                continue;
            }
            if (deviceEventType == DEVICE_RESET) {
                current.onDeviceReset();
            } else if (deviceEventType == DEVICE_DISPOSED) {
                current.onDeviceDispose();
            }
        }
    }
}
