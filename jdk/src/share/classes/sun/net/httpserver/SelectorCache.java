/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.httpserver;

import java.util.*;
import java.nio.*;
import java.net.*;
import java.io.*;
import java.security.*;
import java.nio.channels.*;

/*
 * Implements a cache of java.nio.channels.Selector
 * where Selectors are allocated on demand and placed
 * in a temporary cache for a period of time, so they
 * can be reused. If a period of between 2 and 4 minutes
 * elapses without being used, then they are closed.
 */
public class SelectorCache {

    static SelectorCache cache = null;

    private SelectorCache () {
        freeSelectors = new LinkedList<SelectorWrapper>();
        CacheCleaner c = AccessController.doPrivileged(
            new PrivilegedAction<CacheCleaner>() {
            public CacheCleaner run() {
                CacheCleaner cleaner = new CacheCleaner();
                cleaner.setDaemon (true);
                return cleaner;
            }
        });
        c.start();
    }

    /**
     * factory method for creating single instance
     */
    public static SelectorCache getSelectorCache () {
        synchronized (SelectorCache.class) {
            if (cache == null) {
                cache = new SelectorCache ();
            }
        }
        return cache;
    }

    private static class SelectorWrapper {
        private Selector sel;
        private boolean deleteFlag;
        private SelectorWrapper (Selector sel) {
            this.sel = sel;
            this.deleteFlag = false;
        }
        public Selector getSelector() { return sel;}
        public boolean getDeleteFlag () {return deleteFlag;}
        public void setDeleteFlag (boolean b) {deleteFlag = b;}
    }

    /* list of free selectors. Can be re-allocated for a period
     * of time, after which if not allocated will be closed
     * and removed from the list (by CacheCleaner thread)
     */
    LinkedList<SelectorWrapper> freeSelectors;

    synchronized Selector getSelector () throws IOException {
        SelectorWrapper wrapper = null;
        Selector selector;

        if (freeSelectors.size() > 0) {
            wrapper = freeSelectors.remove();
            selector = wrapper.getSelector();
        } else {
            selector = Selector.open();
        }
        return selector;
    }

    synchronized void freeSelector (Selector selector) {
        freeSelectors.add (new SelectorWrapper (selector));
    }

    /* Thread ensures that entries on freeSelector list
     * remain there for at least 2 minutes and no longer
     * than 4 minutes.
     */
    class CacheCleaner extends Thread {
        public void run () {
            long timeout = ServerConfig.getSelCacheTimeout() * 1000;
            while (true) {
                try {Thread.sleep (timeout); } catch (Exception e) {}
                synchronized (freeSelectors) {
                    ListIterator<SelectorWrapper> l = freeSelectors.listIterator();
                    while (l.hasNext()) {
                        SelectorWrapper w = l.next();
                        if (w.getDeleteFlag()) {
                            /* 2nd pass. Close the selector */
                            try {
                                w.getSelector().close();
                            } catch (IOException e) {}
                            l.remove();
                        } else {
                            /* 1st pass. Set the flag */
                            w.setDeleteFlag (true);
                        }
                    }
                }
            }
        }
    }
}
