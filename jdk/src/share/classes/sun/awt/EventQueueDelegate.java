/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.awt;

import java.awt.AWTEvent;
import java.awt.EventQueue;


public class EventQueueDelegate {
    private static final Object EVENT_QUEUE_DELEGATE_KEY =
        new StringBuilder("EventQueueDelegate.Delegate");

    public static void setDelegate(Delegate delegate) {
        AppContext.getAppContext().put(EVENT_QUEUE_DELEGATE_KEY, delegate);
    }
    public static Delegate getDelegate() {
        return
          (Delegate) AppContext.getAppContext().get(EVENT_QUEUE_DELEGATE_KEY);
    }
    public interface Delegate {
        /**
         * This method allows for changing {@code EventQueue} events order.
         *
         * @param eventQueue current {@code EventQueue}
         * @return next {@code event} for the {@code EventDispatchThread}
         */

        public AWTEvent getNextEvent(EventQueue eventQueue) throws InterruptedException;

        /**
         * Notifies delegate before EventQueue.dispatch method.
         *
         * Note: this method may mutate the event
         *
         * @param event  to be dispatched by {@code dispatch} method
         * @return handle to be passed to {@code afterDispatch} method
         */
        public Object beforeDispatch(AWTEvent event) throws InterruptedException;

        /**
         * Notifies delegate after EventQueue.dispatch method.
         *
         * @param event {@code event} dispatched by the {@code dispatch} method
         * @param handle object which came from {@code beforeDispatch} method
         */
        public void afterDispatch(AWTEvent event, Object handle) throws InterruptedException;
    }
}
