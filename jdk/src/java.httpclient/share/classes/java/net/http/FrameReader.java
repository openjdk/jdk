/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package java.net.http;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents one frame. May be initialized with a leftover buffer from previous
 * frame. Call {@code haveFrame()} to determine if buffers contains at least one
 * frame. If false, the obtain another buffer and call {@code}input(ByteBuffer)}.
 * There may be additional bytes at end of the frame list.
 */
class FrameReader {

    final List<ByteBuffer> buffers;

    FrameReader() {
        buffers = new LinkedList<>();
    }

    FrameReader(FrameReader that) {
        this.buffers = that.buffers;
    }

    FrameReader(ByteBuffer remainder) {
        buffers = new LinkedList<>();
        if (remainder != null) {
            buffers.add(remainder);
        }
    }

    public synchronized void input(ByteBuffer buffer) {
        buffers.add(buffer);
    }

    public synchronized boolean haveFrame() {
        //buffers = Utils.superCompact(buffers, () -> ByteBuffer.allocate(Utils.BUFSIZE));
        int size = 0;
        for (ByteBuffer buffer : buffers) {
            size += buffer.remaining();
        }
        if (size < 3) {
            return false; // don't have length yet
        }
        // we at least have length field
        int length = 0;
        int j = 0;
        ByteBuffer b = buffers.get(j);
        b.mark();
        for (int i=0; i<3; i++) {
            while (!b.hasRemaining()) {
                b.reset();
                b = buffers.get(++j);
                b.mark();
            }
            length = (length << 8) + (b.get() & 0xff);
        }
        b.reset();
        return (size >= length + 9); // frame length
    }

    synchronized List<ByteBuffer> frame() {
        return buffers;
    }
}
