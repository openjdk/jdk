/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.nio.channels.AsynchronousChannel;
import java.util.concurrent.Future;

/**
 * Base implementation of Future used for asynchronous I/O
 */

abstract class AbstractFuture<V,A>
    implements Future<V>
{
    private final AsynchronousChannel channel;
    private final A attachment;

    protected AbstractFuture(AsynchronousChannel channel, A attachment) {
        this.channel = channel;
        this.attachment = attachment;
    }

    final AsynchronousChannel channel() {
        return channel;
    }

    final A attachment() {
        return attachment;
    }

    /**
     * Returns the result of the operation if it has completed successfully.
     */
    abstract V value();

    /**
     * Returns the exception if the operation has failed.
     */
    abstract Throwable exception();
}
