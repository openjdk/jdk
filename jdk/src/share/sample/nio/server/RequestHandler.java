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

/**
 * Primary driver class used by non-blocking Servers to receive,
 * prepare, send, and shutdown requests.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
class RequestHandler implements Handler {

    private ChannelIO cio;
    private ByteBuffer rbb = null;

    private boolean requestReceived = false;
    private Request request = null;
    private Reply reply = null;

    private static int created = 0;

    RequestHandler(ChannelIO cio) {
        this.cio = cio;

        // Simple heartbeat to let user know we're alive.
        synchronized (RequestHandler.class) {
            created++;
            if ((created % 50) == 0) {
                System.out.println(".");
                created = 0;
            } else {
                System.out.print(".");
            }
        }
    }

    // Returns true when request is complete
    // May expand rbb if more room required
    //
    private boolean receive(SelectionKey sk) throws IOException {
        ByteBuffer tmp = null;

        if (requestReceived) {
            return true;
        }

        if (!cio.doHandshake(sk)) {
            return false;
        }

        if ((cio.read() < 0) || Request.isComplete(cio.getReadBuf())) {
            rbb = cio.getReadBuf();
            return (requestReceived = true);
        }
        return false;
    }

    // When parse is successfull, saves request and returns true
    //
    private boolean parse() throws IOException {
        try {
            request = Request.parse(rbb);
            return true;
        } catch (MalformedRequestException x) {
            reply = new Reply(Reply.Code.BAD_REQUEST,
                              new StringContent(x));
        }
        return false;
    }

    // Ensures that reply field is non-null
    //
    private void build() throws IOException {
        Request.Action action = request.action();
        if ((action != Request.Action.GET) &&
                (action != Request.Action.HEAD)) {
            reply = new Reply(Reply.Code.METHOD_NOT_ALLOWED,
                              new StringContent(request.toString()));
        }
        reply = new Reply(Reply.Code.OK,
                          new FileContent(request.uri()), action);
    }

    public void handle(SelectionKey sk) throws IOException {
        try {

            if (request == null) {
                if (!receive(sk))
                    return;
                rbb.flip();
                if (parse())
                    build();
                try {
                    reply.prepare();
                } catch (IOException x) {
                    reply.release();
                    reply = new Reply(Reply.Code.NOT_FOUND,
                                      new StringContent(x));
                    reply.prepare();
                }
                if (send()) {
                    // More bytes remain to be written
                    sk.interestOps(SelectionKey.OP_WRITE);
                } else {
                    // Reply completely written; we're done
                    if (cio.shutdown()) {
                        cio.close();
                        reply.release();
                    }
                }
            } else {
                if (!send()) {  // Should be rp.send()
                    if (cio.shutdown()) {
                        cio.close();
                        reply.release();
                    }
                }
            }
        } catch (IOException x) {
            String m = x.getMessage();
            if (!m.equals("Broken pipe") &&
                    !m.equals("Connection reset by peer")) {
                System.err.println("RequestHandler: " + x.toString());
            }

            try {
                /*
                 * We had a failure here, so we'll try to be nice
                 * before closing down and send off a close_notify,
                 * but if we can't get the message off with one try,
                 * we'll just shutdown.
                 */
                cio.shutdown();
            } catch (IOException e) {
                // ignore
            }

            cio.close();
            if (reply !=  null) {
                reply.release();
            }
        }

    }

    private boolean send() throws IOException {
        try {
            return reply.send(cio);
        } catch (IOException x) {
            if (x.getMessage().startsWith("Resource temporarily")) {
                System.err.println("## RTA");
                return true;
            }
            throw x;
        }
    }
}
