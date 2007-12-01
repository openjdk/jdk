/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.channels.spi.*;


/**
 * An implementation of SelectionKey for Solaris.
 */

class SelectionKeyImpl
    extends AbstractSelectionKey
{

    final SelChImpl channel;                            // package-private
    final SelectorImpl selector;                        // package-private

    // Index for a pollfd array in Selector that this key is registered with
    private int index;

    private volatile int interestOps;
    private int readyOps;

    SelectionKeyImpl(SelChImpl ch, SelectorImpl sel) {
        channel = ch;
        selector = sel;
    }

    public SelectableChannel channel() {
        return (SelectableChannel)channel;
    }

    public Selector selector() {
        return selector;
    }

    int getIndex() {                                    // package-private
        return index;
    }

    void setIndex(int i) {                              // package-private
        index = i;
    }

    private void ensureValid() {
        if (!isValid())
            throw new CancelledKeyException();
    }

    public int interestOps() {
        ensureValid();
        return interestOps;
    }

    public SelectionKey interestOps(int ops) {
        ensureValid();
        return nioInterestOps(ops);
    }

    public int readyOps() {
        ensureValid();
        return readyOps;
    }

    // The nio versions of these operations do not care if a key
    // has been invalidated. They are for internal use by nio code.

    void nioReadyOps(int ops) {                 // package-private
        readyOps = ops;
    }

    int nioReadyOps() {                         // package-private
        return readyOps;
    }

    SelectionKey nioInterestOps(int ops) {      // package-private
        if ((ops & ~channel().validOps()) != 0)
            throw new IllegalArgumentException();
        channel.translateAndSetInterestOps(ops, this);
        interestOps = ops;
        return this;
    }

    int nioInterestOps() {                       // package-private
        return interestOps;
    }

}
