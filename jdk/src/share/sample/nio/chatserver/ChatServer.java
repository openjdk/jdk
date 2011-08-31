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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implements a chat server, this class holds the list of {@code clients} connected to the server.
 * It sets up a server socket using AsynchronousServerSocketChannel listening to a specified port.
 */
public class ChatServer implements Runnable {
    private final List<Client> connections = Collections.synchronizedList(new ArrayList<Client>());
    private int port;
    private final AsynchronousServerSocketChannel listener;
    private final AsynchronousChannelGroup channelGroup;

    /**
     *
     * @param port to listen to
     * @throws java.io.IOException when failing to start the server
     */
    public ChatServer(int port) throws IOException {
        channelGroup = AsynchronousChannelGroup.withFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                Executors.defaultThreadFactory());
        this.port = port;
        listener = createListener(channelGroup);
    }

    /**
     *
     * @return The socket address that the server is bound to
     * @throws java.io.IOException if an I/O error occurs
     */
    public SocketAddress getSocketAddress() throws IOException {
        return listener.getLocalAddress();
    }

    /**
     * Start accepting connections
     */
    public void run() {

        // call accept to wait for connections, tell it to call our CompletionHandler when there
        // is a new incoming connection
        listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel result, Void attachment) {
                // request a new accept and handle the incoming connection
                listener.accept(null, this);
                handleNewConnection(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
            }
        });
    }

    /**
     * Shuts down the server
     * @throws InterruptedException if terminated while waiting for shutdown
     * @throws IOException if failing to shutdown the channel group
     */
    public void shutdown() throws InterruptedException, IOException {
        channelGroup.shutdownNow();
        channelGroup.awaitTermination(1, TimeUnit.SECONDS);
    }

    /*
    * Creates a listener and starts accepting connections
    */
    private AsynchronousServerSocketChannel createListener(AsynchronousChannelGroup channelGroup) throws IOException {
        final AsynchronousServerSocketChannel listener = openChannel(channelGroup);
        listener.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        listener.bind(new InetSocketAddress(port));
        return listener;
    }

    private AsynchronousServerSocketChannel openChannel(AsynchronousChannelGroup channelGroup) throws IOException {
        return AsynchronousServerSocketChannel.open(channelGroup);
    }

    /**
     * Creates a new client and adds it to the list of connections.
     * Sets the clients handler to the initial state of NameReader
     *
     * @param channel the newly accepted channel
     */
    private void handleNewConnection(AsynchronousSocketChannel channel) {
        Client client = new Client(channel, new ClientReader(this, new NameReader(this)));
        try {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        } catch (IOException e) {
            // ignore
        }
        connections.add(client);
        client.run();
    }

    /**
     * Sends a message to all clients except the source.
     * The method is synchronized as it is desired that messages are sent to
     * all clients in the same order as received.
     *
     * @param client the message source
     * @param message the message to be sent
     */
    public void writeMessageToClients(Client client, String message) {
        synchronized (connections) {
            for (Client clientConnection : connections) {
                if (clientConnection != client) {
                    clientConnection.writeMessageFrom(client, message);
                }
            }
        }
    }

    public void removeClient(Client client) {
        connections.remove(client);
    }

    private static void usage() {
        System.err.println("ChatServer [-port <port number>]");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        int port = 5000;
        if (args.length != 0 && args.length != 2) {
            usage();
        } else if (args.length == 2) {
            try {
                if (args[0].equals("-port")) {
                    port = Integer.parseInt(args[1]);
                } else {
                    usage();
                }
            } catch (NumberFormatException e) {
                usage();
            }
        }
        System.out.println("Running on port " + port);
        new ChatServer(port).run();
    }
}
