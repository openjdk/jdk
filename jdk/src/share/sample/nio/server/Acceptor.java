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
import java.nio.channels.*;
import javax.net.ssl.*;

/**
 * A Runnable class which sits in a loop accepting SocketChannels,
 * then registers the Channels with the read/write Selector.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
class Acceptor implements Runnable {

    private ServerSocketChannel ssc;
    private Dispatcher d;

    private SSLContext sslContext;

    Acceptor(ServerSocketChannel ssc, Dispatcher d, SSLContext sslContext) {
        this.ssc = ssc;
        this.d = d;
        this.sslContext = sslContext;
    }

    public void run() {
        for (;;) {
            try {
                SocketChannel sc = ssc.accept();

                ChannelIO cio = (sslContext != null ?
                    ChannelIOSecure.getInstance(
                        sc, false /* non-blocking */, sslContext) :
                    ChannelIO.getInstance(
                        sc, false /* non-blocking */));

                RequestHandler rh = new RequestHandler(cio);

                d.register(cio.getSocketChannel(), SelectionKey.OP_READ, rh);

            } catch (IOException x) {
                x.printStackTrace();
                break;
            }
        }
    }
}
