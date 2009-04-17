/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

public class FileLockImpl
    extends FileLock
{
    private volatile boolean valid = true;

    FileLockImpl(FileChannel channel, long position, long size, boolean shared)
    {
        super(channel, position, size, shared);
    }

    FileLockImpl(AsynchronousFileChannel channel, long position, long size, boolean shared)
    {
        super(channel, position, size, shared);
    }

    public boolean isValid() {
        return valid;
    }

    void invalidate() {
        assert Thread.holdsLock(this);
        valid = false;
    }

    public synchronized void release() throws IOException {
        Channel ch = acquiredBy();
        if (!ch.isOpen())
            throw new ClosedChannelException();
        if (valid) {
            if (ch instanceof FileChannelImpl)
                ((FileChannelImpl)ch).release(this);
            else if (ch instanceof AsynchronousFileChannelImpl)
                ((AsynchronousFileChannelImpl)ch).release(this);
            else throw new AssertionError();
            valid = false;
        }
    }
}
