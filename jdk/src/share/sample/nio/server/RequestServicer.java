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

/**
 * Primary driver class used by blocking Servers to receive,
 * prepare, send, and shutdown requests.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
class RequestServicer implements Runnable {

    private ChannelIO cio;

    private static int created = 0;

    RequestServicer(ChannelIO cio) {
        this.cio = cio;

        // Simple heartbeat to let user know we're alive.
        synchronized (RequestServicer.class) {
            created++;
            if ((created % 50) == 0) {
                System.out.println(".");
                created = 0;
            } else {
                System.out.print(".");
            }
        }
    }

    private void service() throws IOException {
        Reply rp = null;
        try {
            ByteBuffer rbb = receive();         // Receive
            Request rq = null;
            try {                               // Parse
                rq = Request.parse(rbb);
            } catch (MalformedRequestException x) {
                rp = new Reply(Reply.Code.BAD_REQUEST,
                               new StringContent(x));
            }
            if (rp == null) rp = build(rq);     // Build
            do {} while (rp.send(cio));         // Send
            do {} while (!cio.shutdown());
            cio.close();
            rp.release();
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
            if (rp != null) {
                rp.release();
            }
        }
    }

    public void run() {
        try {
            service();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    ByteBuffer receive() throws IOException {

        do {} while (!cio.doHandshake());

        for (;;) {
            int read = cio.read();
            ByteBuffer bb = cio.getReadBuf();
            if ((read < 0) || (Request.isComplete(bb))) {
                bb.flip();
                return bb;
            }
        }
    }

    Reply build(Request rq) throws IOException {

        Reply rp = null;
        Request.Action action = rq.action();
        if ((action != Request.Action.GET) &&
                (action != Request.Action.HEAD))
            rp = new Reply(Reply.Code.METHOD_NOT_ALLOWED,
                           new StringContent(rq.toString()));
        else
            rp = new Reply(Reply.Code.OK,
                           new FileContent(rq.uri()), action);
        try {
            rp.prepare();
        } catch (IOException x) {
            rp.release();
            rp = new Reply(Reply.Code.NOT_FOUND,
                           new StringContent(x));
            rp.prepare();
        }
        return rp;
    }
}
