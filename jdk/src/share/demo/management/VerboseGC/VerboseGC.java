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
 */

import javax.management.*;
import javax.management.remote.*;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * This VerboseGC class demonstrates the capability to get
 * the garbage collection statistics and memory usage remotely.
 */
public class VerboseGC {
    private MBeanServerConnection server;
    private JMXConnector jmxc;
    public VerboseGC(String hostname, int port) {
        System.out.println("Connecting to " + hostname + ":" + port);

        // Create an RMI connector client and connect it to
        // the RMI connector server
        String urlPath = "/jndi/rmi://" + hostname + ":" + port + "/jmxrmi";
        connect(urlPath);
   }

   public void dump(long interval, long samples) {
        try {
            PrintGCStat pstat = new PrintGCStat(server);
            for (int i = 0; i < samples; i++) {
                pstat.printVerboseGc();
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            System.err.println("\nCommunication error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Connect to a JMX agent of a given URL.
     */
    private void connect(String urlPath) {
        try {
            JMXServiceURL url = new JMXServiceURL("rmi", "", 0, urlPath);
            this.jmxc = JMXConnectorFactory.connect(url);
            this.server = jmxc.getMBeanServerConnection();
        } catch (MalformedURLException e) {
            // should not reach here
        } catch (IOException e) {
            System.err.println("\nCommunication error: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
        }

        String hostname = "";
        int port = -1;
        long interval = 5000; // default is 5 second interval
        long mins = 5;
        for (String arg: args) {
            if (arg.startsWith("-")) {
                if (arg.equals("-h") ||
                    arg.equals("-help") ||
                    arg.equals("-?")) {
                    usage();
                } else if (arg.startsWith("-interval=")) {
                    try {
                        interval = Integer.parseInt(arg.substring(10)) * 1000;
                    } catch (NumberFormatException ex) {
                        usage();
                    }
                } else if (arg.startsWith("-duration=")) {
                    try {
                        mins = Integer.parseInt(arg.substring(10));
                    } catch (NumberFormatException ex) {
                        usage();
                    }
                } else {
                    // Unknown switch
                    System.err.println("Unrecognized option: " + arg);
                    usage();
                }
            } else {
                String[] arg2 = arg.split(":");
                if (arg2.length != 2) {
                    usage();
                }
                hostname = arg2[0];
                try {
                    port = Integer.parseInt(arg2[1]);
                } catch (NumberFormatException x) {
                    usage();
                }
                if (port < 0) {
                    usage();
                }
            }
        }

        // get full thread dump and perform deadlock detection
        VerboseGC vgc = new VerboseGC(hostname, port);
        long samples = (mins * 60 * 1000) / interval;
        vgc.dump(interval, samples);

    }

    private static void usage() {
        System.out.print("Usage: java VerboseGC <hostname>:<port> ");
        System.out.println(" [-interval=seconds] [-duration=minutes]");
    }
}
