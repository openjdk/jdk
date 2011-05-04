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

import java.nio.ByteBuffer;

/**
 * Writes all messages in our buffer to the other clients
 * and appends new data read from the socket to our buffer
 */
class MessageReader implements DataReader {
    private final ChatServer chatServer;

    public MessageReader(ChatServer chatServer) {
        this.chatServer = chatServer;
    }

    public boolean acceptsMessages() {
        return true;
    }

    /**
     * Write all full messages in our buffer to
     * the other clients
     *
     * @param client the client to read messages from
     */
    @Override
    public void beforeRead(Client client) {
        // Check if we have any messages buffered and send them
        String message = client.nextMessage();
        while (message != null) {
            chatServer.writeMessageToClients(client, message);
            message = client.nextMessage();
        }
    }

    /**
     * Append the read buffer to the clients message buffer
     * @param client the client to append messages to
     * @param buffer the buffer we received from the socket
     * @param bytes the number of bytes read into the buffer
     */
    @Override
    public void onData(Client client, ByteBuffer buffer, int bytes) {
        buffer.flip();
        // Just append the message on the buffer
        client.appendMessage(new String(buffer.array(), 0, bytes));
    }
}
