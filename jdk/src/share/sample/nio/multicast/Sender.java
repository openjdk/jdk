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
import java.nio.charset.Charset;
import java.net.*;
import java.io.IOException;
import java.util.*;

/**
 * Sample multicast sender to send a message in a multicast datagram
 * to a given group.
 */

public class Sender {

    private static void usage() {
        System.err.println("usage: java Sender group:port[@interface] message");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2)
            usage();

        MulticastAddress target = MulticastAddress.parse(args[0]);

        // create socket
        ProtocolFamily family = StandardProtocolFamily.INET;
        if (target.group() instanceof Inet6Address)
            family = StandardProtocolFamily.INET6;
        DatagramChannel dc = DatagramChannel.open(family).bind(new InetSocketAddress(0));
        if (target.interf() != null) {
            dc.setOption(StandardSocketOption.IP_MULTICAST_IF, target.interf());
        }

        // send multicast packet
        dc.send(Charset.defaultCharset().encode(args[1]),
                new InetSocketAddress(target.group(), target.port()));
        dc.close();
    }

}
