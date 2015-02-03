/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

/**
 * A helper class which performs I/O using the SSLEngine API.
 * <P>
 * Each connection has a SocketChannel and a SSLEngine that is
 * used through the lifetime of the Channel.  We allocate byte buffers
 * for use as the outbound and inbound network buffers.
 *
 * <PRE>
 *               Application Data
 *               src      requestBB
 *                |           ^
 *                |     |     |
 *                v     |     |
 *           +----+-----|-----+----+
 *           |          |          |
 *           |       SSL|Engine    |
 *   wrap()  |          |          |  unwrap()
 *           | OUTBOUND | INBOUND  |
 *           |          |          |
 *           +----+-----|-----+----+
 *                |     |     ^
 *                |     |     |
 *                v           |
 *            outNetBB     inNetBB
 *                   Net data
 * </PRE>
 *
 * These buffers handle all of the intermediary data for the SSL
 * connection.  To make things easy, we'll require outNetBB be
 * completely flushed before trying to wrap any more data, but we
 * could certainly remove that restriction by using larger buffers.
 * <P>
 * There are many, many ways to handle compute and I/O strategies.
 * What follows is a relatively simple one.  The reader is encouraged
 * to develop the strategy that best fits the application.
 * <P>
 * In most of the non-blocking operations in this class, we let the
 * Selector tell us when we're ready to attempt an I/O operation (by the
 * application repeatedly calling our methods).  Another option would be
 * to attempt the operation and return from the method when no forward
 * progress can be made.
 * <P>
 * There's lots of room for enhancements and improvement in this example.
 * <P>
 * We're checking for SSL/TLS end-of-stream truncation attacks via
 * sslEngine.closeInbound().  When you reach the end of a input stream
 * via a read() returning -1 or an IOException, we call
 * sslEngine.closeInbound() to signal to the sslEngine that no more
 * input will be available.  If the peer's close_notify message has not
 * yet been received, this could indicate a trucation attack, in which
 * an attacker is trying to prematurely close the connection.   The
 * closeInbound() will throw an exception if this condition were
 * present.
 *
 * @author Brad R. Wetmore
 * @author Mark Reinhold
 */
class ChannelIOSecure extends ChannelIO {

    private SSLEngine sslEngine = null;

    private int appBBSize;
    private int netBBSize;

    /*
     * All I/O goes through these buffers.
     * <P>
     * It might be nice to use a cache of ByteBuffers so we're
     * not alloc/dealloc'ing ByteBuffer's for each new SSLEngine.
     * <P>
     * We use our superclass' requestBB for our application input buffer.
     * Outbound application data is supplied to us by our callers.
     */
    private ByteBuffer inNetBB;
    private ByteBuffer outNetBB;

    /*
     * An empty ByteBuffer for use when one isn't available, say
     * as a source buffer during initial handshake wraps or for close
     * operations.
     */
    private static ByteBuffer hsBB = ByteBuffer.allocate(0);

    /*
     * The FileChannel we're currently transferTo'ing (reading).
     */
    private ByteBuffer fileChannelBB = null;

    /*
     * During our initial handshake, keep track of the next
     * SSLEngine operation that needs to occur:
     *
     *     NEED_WRAP/NEED_UNWRAP
     *
     * Once the initial handshake has completed, we can short circuit
     * handshake checks with initialHSComplete.
     */
    private HandshakeStatus initialHSStatus;
    private boolean initialHSComplete;

    /*
     * We have received the shutdown request by our caller, and have
     * closed our outbound side.
     */
    private boolean shutdown = false;

    /*
     * Constructor for a secure ChannelIO variant.
     */
    protected ChannelIOSecure(SocketChannel sc, boolean blocking,
            SSLContext sslc) throws IOException {
        super(sc, blocking);

        /*
         * We're a server, so no need to use host/port variant.
         *
         * The first call for a server is a NEED_UNWRAP.
         */
        sslEngine = sslc.createSSLEngine();
        sslEngine.setUseClientMode(false);
        initialHSStatus = HandshakeStatus.NEED_UNWRAP;
        initialHSComplete = false;

        // Create a buffer using the normal expected packet size we'll
        // be getting.  This may change, depending on the peer's
        // SSL implementation.
        netBBSize = sslEngine.getSession().getPacketBufferSize();
        inNetBB = ByteBuffer.allocate(netBBSize);
        outNetBB = ByteBuffer.allocate(netBBSize);
        outNetBB.position(0);
        outNetBB.limit(0);
    }

    /*
     * Static factory method for creating a secure ChannelIO object.
     * <P>
     * We need to allocate different sized application data buffers
     * based on whether we're secure or not.  We can't determine
     * this until our sslEngine is created.
     */
    static ChannelIOSecure getInstance(SocketChannel sc, boolean blocking,
            SSLContext sslc) throws IOException {

        ChannelIOSecure cio = new ChannelIOSecure(sc, blocking, sslc);

        // Create a buffer using the normal expected application size we'll
        // be getting.  This may change, depending on the peer's
        // SSL implementation.
        cio.appBBSize = cio.sslEngine.getSession().getApplicationBufferSize();
        cio.requestBB = ByteBuffer.allocate(cio.appBBSize);

        return cio;
    }

    /*
     * Calls up to the superclass to adjust the buffer size
     * by an appropriate increment.
     */
    protected void resizeRequestBB() {
        resizeRequestBB(appBBSize);
    }

    /*
     * Adjust the inbount network buffer to an appropriate size.
     */
    private void resizeResponseBB() {
        ByteBuffer bb = ByteBuffer.allocate(netBBSize);
        inNetBB.flip();
        bb.put(inNetBB);
        inNetBB = bb;
    }

    /*
     * Writes bb to the SocketChannel.
     * <P>
     * Returns true when the ByteBuffer has no remaining data.
     */
    private boolean tryFlush(ByteBuffer bb) throws IOException {
        super.write(bb);
        return !bb.hasRemaining();
    }

    /*
     * Perform any handshaking processing.
     * <P>
     * This variant is for Servers without SelectionKeys (e.g.
     * blocking).
     */
    boolean doHandshake() throws IOException {
        return doHandshake(null);
    }

    /*
     * Perform any handshaking processing.
     * <P>
     * If a SelectionKey is passed, register for selectable
     * operations.
     * <P>
     * In the blocking case, our caller will keep calling us until
     * we finish the handshake.  Our reads/writes will block as expected.
     * <P>
     * In the non-blocking case, we just received the selection notification
     * that this channel is ready for whatever the operation is, so give
     * it a try.
     * <P>
     * return:
     *          true when handshake is done.
     *          false while handshake is in progress
     */
    boolean doHandshake(SelectionKey sk) throws IOException {

        SSLEngineResult result;

        if (initialHSComplete) {
            return initialHSComplete;
        }

        /*
         * Flush out the outgoing buffer, if there's anything left in
         * it.
         */
        if (outNetBB.hasRemaining()) {

            if (!tryFlush(outNetBB)) {
                return false;
            }

            // See if we need to switch from write to read mode.

            switch (initialHSStatus) {

            /*
             * Is this the last buffer?
             */
            case FINISHED:
                initialHSComplete = true;
                // Fall-through to reregister need for a Read.

            case NEED_UNWRAP:
                if (sk != null) {
                    sk.interestOps(SelectionKey.OP_READ);
                }
                break;
            }

            return initialHSComplete;
        }


        switch (initialHSStatus) {

        case NEED_UNWRAP:
            if (sc.read(inNetBB) == -1) {
                sslEngine.closeInbound();
                return initialHSComplete;
            }

needIO:
            while (initialHSStatus == HandshakeStatus.NEED_UNWRAP) {
                resizeRequestBB();    // expected room for unwrap
                inNetBB.flip();
                result = sslEngine.unwrap(inNetBB, requestBB);
                inNetBB.compact();

                initialHSStatus = result.getHandshakeStatus();

                switch (result.getStatus()) {

                case OK:
                    switch (initialHSStatus) {
                    case NOT_HANDSHAKING:
                        throw new IOException(
                            "Not handshaking during initial handshake");

                    case NEED_TASK:
                        initialHSStatus = doTasks();
                        break;

                    case FINISHED:
                        initialHSComplete = true;
                        break needIO;
                    }

                    break;

                case BUFFER_UNDERFLOW:
                    // Resize buffer if needed.
                    netBBSize = sslEngine.getSession().getPacketBufferSize();
                    if (netBBSize > inNetBB.capacity()) {
                        resizeResponseBB();
                    }

                    /*
                     * Need to go reread the Channel for more data.
                     */
                    if (sk != null) {
                        sk.interestOps(SelectionKey.OP_READ);
                    }
                    break needIO;

                case BUFFER_OVERFLOW:
                    // Reset the application buffer size.
                    appBBSize =
                        sslEngine.getSession().getApplicationBufferSize();
                    break;

                default: //CLOSED:
                    throw new IOException("Received" + result.getStatus() +
                        "during initial handshaking");
                }
            }  // "needIO" block.

            /*
             * Just transitioned from read to write.
             */
            if (initialHSStatus != HandshakeStatus.NEED_WRAP) {
                break;
            }

            // Fall through and fill the write buffers.

        case NEED_WRAP:
            /*
             * The flush above guarantees the out buffer to be empty
             */
            outNetBB.clear();
            result = sslEngine.wrap(hsBB, outNetBB);
            outNetBB.flip();

            initialHSStatus = result.getHandshakeStatus();

            switch (result.getStatus()) {
            case OK:

                if (initialHSStatus == HandshakeStatus.NEED_TASK) {
                    initialHSStatus = doTasks();
                }

                if (sk != null) {
                    sk.interestOps(SelectionKey.OP_WRITE);
                }

                break;

            default: // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
                throw new IOException("Received" + result.getStatus() +
                        "during initial handshaking");
            }
            break;

        default: // NOT_HANDSHAKING/NEED_TASK/FINISHED
            throw new RuntimeException("Invalid Handshaking State" +
                    initialHSStatus);
        } // switch

        return initialHSComplete;
    }

    /*
     * Do all the outstanding handshake tasks in the current Thread.
     */
    private SSLEngineResult.HandshakeStatus doTasks() {

        Runnable runnable;

        /*
         * We could run this in a separate thread, but
         * do in the current for now.
         */
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    /*
     * Read the channel for more information, then unwrap the
     * (hopefully application) data we get.
     * <P>
     * If we run out of data, we'll return to our caller (possibly using
     * a Selector) to get notification that more is available.
     * <P>
     * Each call to this method will perform at most one underlying read().
     */
    int read() throws IOException {
        SSLEngineResult result;

        if (!initialHSComplete) {
            throw new IllegalStateException();
        }

        int pos = requestBB.position();

        if (sc.read(inNetBB) == -1) {
            sslEngine.closeInbound();  // probably throws exception
            return -1;
        }

        do {
            resizeRequestBB();    // expected room for unwrap
            inNetBB.flip();
            result = sslEngine.unwrap(inNetBB, requestBB);
            inNetBB.compact();

            /*
             * Could check here for a renegotation, but we're only
             * doing a simple read/write, and won't have enough state
             * transitions to do a complete handshake, so ignore that
             * possibility.
             */
            switch (result.getStatus()) {

            case BUFFER_OVERFLOW:
                // Reset the application buffer size.
                appBBSize = sslEngine.getSession().getApplicationBufferSize();
                break;

            case BUFFER_UNDERFLOW:
                // Resize buffer if needed.
                netBBSize = sslEngine.getSession().getPacketBufferSize();
                if (netBBSize > inNetBB.capacity()) {
                    resizeResponseBB();

                    break; // break, next read will support larger buffer.
                }
            case OK:
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    doTasks();
                }
                break;

            default:
                throw new IOException("sslEngine error during data read: " +
                    result.getStatus());
            }
        } while ((inNetBB.position() != 0) &&
            result.getStatus() != Status.BUFFER_UNDERFLOW);

        return (requestBB.position() - pos);
    }

    /*
     * Try to write out as much as possible from the src buffer.
     */
    int write(ByteBuffer src) throws IOException {

        if (!initialHSComplete) {
            throw new IllegalStateException();
        }

        return doWrite(src);
    }

    /*
     * Try to flush out any existing outbound data, then try to wrap
     * anything new contained in the src buffer.
     * <P>
     * Return the number of bytes actually consumed from the buffer,
     * but the data may actually be still sitting in the output buffer,
     * waiting to be flushed.
     */
    private int doWrite(ByteBuffer src) throws IOException {
        int retValue = 0;

        if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
            return retValue;
        }

        /*
         * The data buffer is empty, we can reuse the entire buffer.
         */
        outNetBB.clear();

        SSLEngineResult result = sslEngine.wrap(src, outNetBB);
        retValue = result.bytesConsumed();

        outNetBB.flip();

        switch (result.getStatus()) {

        case OK:
            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                doTasks();
            }
            break;

        default:
            throw new IOException("sslEngine error during data write: " +
                result.getStatus());
        }

        /*
         * Try to flush the data, regardless of whether or not
         * it's been selected.  Odds of a write buffer being full
         * is less than a read buffer being empty.
         */
        if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return retValue;
    }

    /*
     * Perform a FileChannel.TransferTo on the socket channel.
     * <P>
     * We have to copy the data into an intermediary app ByteBuffer
     * first, then send it through the SSLEngine.
     * <P>
     * We return the number of bytes actually read out of the
     * filechannel.  However, the data may actually be stuck
     * in the fileChannelBB or the outNetBB.  The caller
     * is responsible for making sure to call dataFlush()
     * before shutting down.
     */
    long transferTo(FileChannel fc, long pos, long len) throws IOException {

        if (!initialHSComplete) {
            throw new IllegalStateException();
        }

        if (fileChannelBB == null) {
            fileChannelBB = ByteBuffer.allocate(appBBSize);
            fileChannelBB.limit(0);
        }

        fileChannelBB.compact();
        int fileRead = fc.read(fileChannelBB);
        fileChannelBB.flip();

        /*
         * We ignore the return value here, we return the
         * number of bytes actually consumed from the file.
         * We'll flush the output buffer before we start shutting down.
         */
        doWrite(fileChannelBB);

        return fileRead;
    }

    /*
     * Flush any remaining data.
     * <P>
     * Return true when the fileChannelBB and outNetBB are empty.
     */
    boolean dataFlush() throws IOException {
        boolean fileFlushed = true;

        if ((fileChannelBB != null) && fileChannelBB.hasRemaining()) {
            doWrite(fileChannelBB);
            fileFlushed = !fileChannelBB.hasRemaining();
        } else if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return (fileFlushed && !outNetBB.hasRemaining());
    }

    /*
     * Begin the shutdown process.
     * <P>
     * Close out the SSLEngine if not already done so, then
     * wrap our outgoing close_notify message and try to send it on.
     * <P>
     * Return true when we're done passing the shutdown messsages.
     */
    boolean shutdown() throws IOException {

        if (!shutdown) {
            sslEngine.closeOutbound();
            shutdown = true;
        }

        if (outNetBB.hasRemaining() && tryFlush(outNetBB)) {
            return false;
        }

        /*
         * By RFC 2616, we can "fire and forget" our close_notify
         * message, so that's what we'll do here.
         */
        outNetBB.clear();
        SSLEngineResult result = sslEngine.wrap(hsBB, outNetBB);
        if (result.getStatus() != Status.CLOSED) {
            throw new SSLException("Improper close state");
        }
        outNetBB.flip();

        /*
         * We won't wait for a select here, but if this doesn't work,
         * we'll cycle back through on the next select.
         */
        if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return (!outNetBB.hasRemaining() &&
                (result.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }

    /*
     * close() is not overridden
     */
}
