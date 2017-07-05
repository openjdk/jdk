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

import java.nio.channels.*;
import java.util.concurrent.*;

/**
 * A multi-threaded server which creates a pool of threads for use
 * by the server.  The Thread pool decides how to schedule those threads.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
public class BP extends Server {

    private static final int POOL_MULTIPLE = 4;

    BP(int port, int backlog, boolean secure) throws Exception {
        super(port, backlog, secure);
    }

    void runServer() throws Exception {

        ExecutorService xec = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * POOL_MULTIPLE);

        for (;;) {

            SocketChannel sc = ssc.accept();

            ChannelIO cio = (sslContext != null ?
                ChannelIOSecure.getInstance(
                    sc, true /* blocking */, sslContext) :
                ChannelIO.getInstance(
                    sc, true /* blocking */));

            RequestServicer svc = new RequestServicer(cio);
            xec.execute(svc);
        }
    }
}
