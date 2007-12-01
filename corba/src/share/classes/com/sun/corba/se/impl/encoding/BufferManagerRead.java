/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.encoding;

import java.nio.ByteBuffer;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

public interface BufferManagerRead
{
    /**
     * Case: Called from ReaderThread on complete message or fragments.
     *       The given buf may be entire message or a fragment.
     *
     *  The ReaderThread finds the ReadBufferManager instance either in
     *  in a fragment map (when collecting - GIOP 1.2 phase 1) or
     *  in an active server requests map (when streaming - GIOP 1.2 phase 2).
     *
     *  As a model for implementation see IIOPInputStream's
     *  constructor of the same name. There are going to be some variations.
     *
     */

    public void processFragment ( ByteBuffer byteBuffer,
        FragmentMessage header);


    /**
     * Case: called from CDRInputStream constructor before unmarshaling.
     *
     * Does:
     *
     *  this.bufQ.get()
     *
     *  If streaming then sync on bufQ and wait if empty.
     */


    /**
     * Case: called from CDRInputStream.grow.
     *
     * Does:
     *
     *  this.bufQ.get()
     *
     *  If streaming then sync on bufQ and wait if empty.
     */

    public ByteBufferWithInfo underflow (ByteBufferWithInfo bbwi);

    /**
     * Called once after creating this buffer manager and before
     * it begins processing.
     */
    public void init(Message header);

    /**
     * Returns the mark/reset handler for this stream.
     */
    public MarkAndResetHandler getMarkAndResetHandler();

    /*
     * Signals that the processing be cancelled.
     */
    public void cancelProcessing(int requestId);

    /*
     * Close BufferManagerRead and perform any oustanding cleanup.
     */
    public void close(ByteBufferWithInfo bbwi);
}
