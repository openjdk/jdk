package java.net.http;

import java.io.*;
import java.net.*;

// will be converted to a PushPromiseFrame in the writeLoop
// a thread is then created to produce the DataFrames from the InputStream
class OutgoingPushPromise extends Http2Frame {
    final HttpHeadersImpl headers;
    final URI uri;
    final InputStream is;
    final int parentStream; // not the pushed streamid

    OutgoingPushPromise(int parentStream, URI uri, HttpHeadersImpl headers, InputStream is) {
        this.uri = uri;
        this.headers = headers;
        this.is = is;
        this.parentStream = parentStream;
    }

    @Override
    void readIncomingImpl(ByteBufferConsumer bc) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    void computeLength() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
