/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import sun.misc.HexDumpEncoder;

/**
 * A class to help abstract away SSLEngine writing synchronization.
 */
final class EngineWriter {

    /*
     * Outgoing handshake Data waiting for a ride is stored here.
     * Normal application data is written directly into the outbound
     * buffer, but handshake data can be written out at any time,
     * so we have buffer it somewhere.
     *
     * When wrap is called, we first check to see if there is
     * any data waiting, then if we're in a data transfer state,
     * we try to write app data.
     *
     * This will contain either ByteBuffers, or the marker
     * HandshakeStatus.FINISHED to signify that a handshake just completed.
     */
    private LinkedList<Object> outboundList;

    private boolean outboundClosed = false;

    /* Class and subclass dynamic debugging support */
    private static final Debug debug = Debug.getInstance("ssl");

    EngineWriter() {
        outboundList = new LinkedList<Object>();
    }

    /*
     * Upper levels assured us we had room for at least one packet of data.
     * As per the SSLEngine spec, we only return one SSL packets worth of
     * data.
     */
    private HandshakeStatus getOutboundData(ByteBuffer dstBB) {

        Object msg = outboundList.removeFirst();
        assert(msg instanceof ByteBuffer);

        ByteBuffer bbIn = (ByteBuffer) msg;
        assert(dstBB.remaining() >= bbIn.remaining());

        dstBB.put(bbIn);

        /*
         * If we have more data in the queue, it's either
         * a finished message, or an indication that we need
         * to call wrap again.
         */
        if (hasOutboundDataInternal()) {
            msg = outboundList.getFirst();
            if (msg == HandshakeStatus.FINISHED) {
                outboundList.removeFirst();     // consume the message
                return HandshakeStatus.FINISHED;
            } else {
                return HandshakeStatus.NEED_WRAP;
            }
        } else {
            return null;
        }
    }

    /*
     * Properly orders the output of the data written to the wrap call.
     * This is only handshake data, application data goes through the
     * other writeRecord.
     */
    synchronized void writeRecord(EngineOutputRecord outputRecord,
            MAC writeMAC, CipherBox writeCipher) throws IOException {

        /*
         * Only output if we're still open.
         */
        if (outboundClosed) {
            throw new IOException("writer side was already closed.");
        }

        outputRecord.write(writeMAC, writeCipher);

        /*
         * Did our handshakers notify that we just sent the
         * Finished message?
         *
         * Add an "I'm finished" message to the queue.
         */
        if (outputRecord.isFinishedMsg()) {
            outboundList.addLast(HandshakeStatus.FINISHED);
        }
    }

    /*
     * Output the packet info.
     */
    private void dumpPacket(EngineArgs ea, boolean hsData) {
        try {
            HexDumpEncoder hd = new HexDumpEncoder();

            ByteBuffer bb = ea.netData.duplicate();

            int pos = bb.position();
            bb.position(pos - ea.deltaNet());
            bb.limit(pos);

            System.out.println("[Raw write" +
                (hsData ? "" : " (bb)") + "]: length = " +
                bb.remaining());
            hd.encodeBuffer(bb, System.out);
        } catch (IOException e) { }
    }

    /*
     * Properly orders the output of the data written to the wrap call.
     * Only app data goes through here, handshake data goes through
     * the other writeRecord.
     *
     * Shouldn't expect to have an IOException here.
     *
     * Return any determined status.
     */
    synchronized HandshakeStatus writeRecord(
            EngineOutputRecord outputRecord, EngineArgs ea, MAC writeMAC,
            CipherBox writeCipher) throws IOException {

        /*
         * If we have data ready to go, output this first before
         * trying to consume app data.
         */
        if (hasOutboundDataInternal()) {
            HandshakeStatus hss = getOutboundData(ea.netData);

            if (debug != null && Debug.isOn("packet")) {
                /*
                 * We could have put the dump in
                 * OutputRecord.write(OutputStream), but let's actually
                 * output when it's actually output by the SSLEngine.
                 */
                dumpPacket(ea, true);
            }

            return hss;
        }

        /*
         * If we are closed, no more app data can be output.
         * Only existing handshake data (above) can be obtained.
         */
        if (outboundClosed) {
            throw new IOException("The write side was already closed");
        }

        outputRecord.write(ea, writeMAC, writeCipher);

        if (debug != null && Debug.isOn("packet")) {
            dumpPacket(ea, false);
        }

        /*
         * No way new outbound handshake data got here if we're
         * locked properly.
         *
         * We don't have any status we can return.
         */
        return null;
    }

    /*
     * We already hold "this" lock, this is the callback from the
     * outputRecord.write() above.  We already know this
     * writer can accept more data (outboundClosed == false),
     * and the closure is sync'd.
     */
    void putOutboundData(ByteBuffer bytes) {
        outboundList.addLast(bytes);
    }

    /*
     * This is for the really rare case that someone is writing from
     * the *InputRecord* before we know what to do with it.
     */
    synchronized void putOutboundDataSync(ByteBuffer bytes)
            throws IOException {

        if (outboundClosed) {
            throw new IOException("Write side already closed");
        }

        outboundList.addLast(bytes);
    }

    /*
     * Non-synch'd version of this method, called by internals
     */
    private boolean hasOutboundDataInternal() {
        return (outboundList.size() != 0);
    }

    synchronized boolean hasOutboundData() {
        return hasOutboundDataInternal();
    }

    synchronized boolean isOutboundDone() {
        return outboundClosed && !hasOutboundDataInternal();
    }

    synchronized void closeOutbound() {
        outboundClosed = true;
    }

}
