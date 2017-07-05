/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

/**
 * A helper class for properly sizing inbound byte buffers and
 * redirecting I/O calls to the proper SocketChannel call.
 * <P>
 * Many of these calls may seem unnecessary until you consider
 * that they are placeholders for the secure variant, which is much
 * more involved.  See ChannelIOSecure for more information.
 *
 * @author Brad R. Wetmore
 * @author Mark Reinhold
 */
class ChannelIO {

    protected SocketChannel sc;

    /*
     * All of the inbound request data lives here until we determine
     * that we've read everything, then we pass that data back to the
     * caller.
     */
    protected ByteBuffer requestBB;
    static private int requestBBSize = 4096;

    protected ChannelIO(SocketChannel sc, boolean blocking)
            throws IOException {
        this.sc = sc;
        sc.configureBlocking(blocking);
    }

    static ChannelIO getInstance(SocketChannel sc, boolean blocking)
            throws IOException {
        ChannelIO cio = new ChannelIO(sc, blocking);
        cio.requestBB = ByteBuffer.allocate(requestBBSize);

        return cio;
    }

    SocketChannel getSocketChannel() {
        return sc;
    }

    /*
     * Return a ByteBuffer with "remaining" space to work.  If you have to
     * reallocate the ByteBuffer, copy the existing info into the new buffer.
     */
    protected void resizeRequestBB(int remaining) {
        if (requestBB.remaining() < remaining) {
            // Expand buffer for large request
            ByteBuffer bb = ByteBuffer.allocate(requestBB.capacity() * 2);
            requestBB.flip();
            bb.put(requestBB);
            requestBB = bb;
        }
    }

    /*
     * Perform any handshaking processing.
     * <P>
     * This variant is for Servers without SelectionKeys (e.g.
     * blocking).
     * <P>
     * return true when we're done with handshaking.
     */
    boolean doHandshake() throws IOException {
        return true;
    }

    /*
     * Perform any handshaking processing.
     * <P>
     * This variant is for Servers with SelectionKeys, so that
     * we can register for selectable operations (e.g. selectable
     * non-blocking).
     * <P>
     * return true when we're done with handshaking.
     */
    boolean doHandshake(SelectionKey sk) throws IOException {
        return true;
    }

    /*
     * Resize (if necessary) the inbound data buffer, and then read more
     * data into the read buffer.
     */
    int read() throws IOException {
        /*
         * Allocate more space if less than 5% remains
         */
        resizeRequestBB(requestBBSize/20);
        return sc.read(requestBB);
    }

    /*
     * All data has been read, pass back the request in one buffer.
     */
    ByteBuffer getReadBuf() {
        return requestBB;
    }

    /*
     * Write the src buffer into the socket channel.
     */
    int write(ByteBuffer src) throws IOException {
        return sc.write(src);
    }

    /*
     * Perform a FileChannel.TransferTo on the socket channel.
     */
    long transferTo(FileChannel fc, long pos, long len) throws IOException {
        return fc.transferTo(pos, len, sc);
    }

    /*
     * Flush any outstanding data to the network if possible.
     * <P>
     * This isn't really necessary for the insecure variant, but needed
     * for the secure one where intermediate buffering must take place.
     * <P>
     * Return true if successful.
     */
    boolean dataFlush() throws IOException {
        return true;
    }

    /*
     * Start any connection shutdown processing.
     * <P>
     * This isn't really necessary for the insecure variant, but needed
     * for the secure one where intermediate buffering must take place.
     * <P>
     * Return true if successful, and the data has been flushed.
     */
    boolean shutdown() throws IOException {
        return true;
    }

    /*
     * Close the underlying connection.
     */
    void close() throws IOException {
        sc.close();
    }

}
