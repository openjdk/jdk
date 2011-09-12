/*
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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


import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

/**
 * Handles a cycle of reading / writing on the {@code Client}.
 */
class ClientReader {
    private final DataReader callback;
    private final ChatServer chatServer;

    ClientReader(ChatServer chatServer, DataReader callback) {
        this.chatServer = chatServer;
        this.callback = callback;
    }

    public boolean acceptsMessages() {
        return callback.acceptsMessages();
    }

    /**
     * Runs a cycle of doing a beforeRead action and then enqueing a new
     * read on the client. Handles closed channels and errors while reading.
     * If the client is still connected a new round of actions are called.
     */
    public void run(final Client client) {
        callback.beforeRead(client);
        client.read(new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer buffer) {
                // if result is negative or zero the connection has been closed or something gone wrong
                if (result < 1) {
                    client.close();
                    System.out.println("Closing connection to " + client);
                    chatServer.removeClient(client);
                } else {
                    callback.onData(client, buffer, result);
                    // enqueue next round of actions
                    client.run();
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buffer) {
                client.close();
                chatServer.removeClient(client);
            }
        });
    }
}
