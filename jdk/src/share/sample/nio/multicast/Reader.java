/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 *   - Neither the name of Sun Microsystems nor the names of its
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
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.io.IOException;
import java.util.*;

public class Reader {

    static void usage() {
        System.err.println("usage: java Reader group:port@interf [-only source...] [-block source...]");
        System.exit(-1);
    }

    static void printDatagram(SocketAddress sa, ByteBuffer buf) {
        System.out.format("-- datagram from %s --\n",
            ((InetSocketAddress)sa).getAddress().getHostAddress());
        System.out.println(Charset.defaultCharset().decode(buf));
    }

    static void parseAddessList(String s, List<InetAddress> list)
        throws UnknownHostException
    {
        String[] sources = s.split(",");
        for (int i=0; i<sources.length; i++) {
            list.add(InetAddress.getByName(sources[i]));
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            usage();

        // first parameter is the multicast address (interface required)
        MulticastAddress target = MulticastAddress.parse(args[0]);
        if (target.interf() == null)
            usage();

        // addition arguments are source addresses to include or exclude
        List<InetAddress> includeList = new ArrayList<InetAddress>();
        List<InetAddress> excludeList = new ArrayList<InetAddress>();
        int argc = 1;
        while (argc < args.length) {
            String option = args[argc++];
            if (argc >= args.length)
                usage();
            String value = args[argc++];
            if (option.equals("-only")) {
                 parseAddessList(value, includeList);
                continue;
            }
            if (option.equals("-block")) {
                parseAddessList(value, excludeList);
                continue;
            }
            usage();
        }
        if (!includeList.isEmpty() && !excludeList.isEmpty()) {
            usage();
        }

        // create and bind socket
        ProtocolFamily family = StandardProtocolFamily.INET;
        if (target.group() instanceof Inet6Address) {
            family = StandardProtocolFamily.INET6;
        }
        DatagramChannel dc = DatagramChannel.open(family)
            .setOption(StandardSocketOption.SO_REUSEADDR, true)
            .bind(new InetSocketAddress(target.port()));

        if (includeList.isEmpty()) {
            // join group and block addresses on the exclude list
            MembershipKey key = dc.join(target.group(), target.interf());
            for (InetAddress source: excludeList) {
                key.block(source);
            }
        } else {
            // join with source-specific membership for each source
            for (InetAddress source: includeList) {
                dc.join(target.group(), target.interf(), source);
            }
        }

        // register socket with Selector
        Selector sel = Selector.open();
        dc.configureBlocking(false);
        dc.register(sel, SelectionKey.OP_READ);

        // print out each datagram that we receive
        ByteBuffer buf = ByteBuffer.allocateDirect(4096);
        for (;;) {
            int updated = sel.select();
            if (updated > 0) {
                Iterator<SelectionKey> iter = sel.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey sk = iter.next();
                    iter.remove();

                    DatagramChannel ch = (DatagramChannel)sk.channel();
                    SocketAddress sa = ch.receive(buf);
                    if (sa != null) {
                        buf.flip();
                        printDatagram(sa, buf);
                        buf.rewind();
                        buf.limit(buf.capacity());
                    }
                }
            }
        }
    }
}
