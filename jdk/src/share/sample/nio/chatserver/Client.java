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


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client represents a remote connection to the chat server.
 * It contains methods for reading and writing messages from the
 * channel.
 * Messages are considered to be separated by newline, so incomplete
 * messages are buffered in the {@code Client}.
 *
 * All reads and writes are asynchronous and uses the nio2 asynchronous
 * elements.
 */
class Client {
    private final AsynchronousSocketChannel channel;
    private AtomicReference<ClientReader> reader;
    private String userName;
    private final StringBuilder messageBuffer = new StringBuilder();

    private final Queue<ByteBuffer> queue = new LinkedList<ByteBuffer>();
    private boolean writing = false;

    public Client(AsynchronousSocketChannel channel, ClientReader reader) {
        this.channel = channel;
        this.reader = new AtomicReference<ClientReader>(reader);
    }

    /**
     * Enqueues a write of the buffer to the channel.
     * The call is asynchronous so the buffer is not safe to modify after
     * passing the buffer here.
     *
     * @param buffer the buffer to send to the channel
     */
    private void writeMessage(final ByteBuffer buffer) {
        boolean threadShouldWrite = false;

        synchronized(queue) {
            queue.add(buffer);
            // Currently no thread writing, make this thread dispatch a write
            if (!writing) {
                writing = true;
                threadShouldWrite = true;
            }
        }

        if (threadShouldWrite) {
            writeFromQueue();
        }
    }

    private void writeFromQueue() {
        ByteBuffer buffer;

        synchronized (queue) {
            buffer = queue.poll();
            if (buffer == null) {
                writing = false;
            }
        }

        // No new data in buffer to write
        if (writing) {
            writeBuffer(buffer);
        }
    }

    private void writeBuffer(ByteBuffer buffer) {
        channel.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer buffer) {
                if (buffer.hasRemaining()) {
                    channel.write(buffer, buffer, this);
                } else {
                    // Go back and check if there is new data to write
                    writeFromQueue();
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
            }
        });
    }

    /**
     * Sends a message
     * @param string the message
     */
    public void writeStringMessage(String string) {
        writeMessage(ByteBuffer.wrap(string.getBytes()));
    }

    /**
     * Send a message from a specific client
     * @param client the message is sent from
     * @param message to send
     */
    public void writeMessageFrom(Client client, String message) {
        if (reader.get().acceptsMessages()) {
            writeStringMessage(client.getUserName() + ": " + message);
        }
    }

    /**
     * Enqueue a read
     * @param completionHandler callback on completed read
     */
    public void read(CompletionHandler<Integer, ? super ByteBuffer> completionHandler) {
        ByteBuffer input = ByteBuffer.allocate(256);
        if (!channel.isOpen()) {
            return;
        }
        channel.read(input, input, completionHandler);
    }

    /**
     * Closes the channel
     */
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the current states actions.
     */
    public void run() {
        reader.get().run(this);
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setReader(ClientReader reader) {
        this.reader.set(reader);
    }

    public String getUserName() {
        return userName;
    }

    public void appendMessage(String message) {
        synchronized (messageBuffer) {
            messageBuffer.append(message);
        }
    }

    /**
     * @return the next newline separated message in the buffer. null is returned if the buffer
     * doesn't contain any newline.
     */
    public String nextMessage() {
        synchronized(messageBuffer) {
            int nextNewline = messageBuffer.indexOf("\n");
            if (nextNewline == -1) {
                return null;
            }
            String message = messageBuffer.substring(0, nextNewline + 1);
            messageBuffer.delete(0, nextNewline + 1);
            return message;
        }
    }
}
