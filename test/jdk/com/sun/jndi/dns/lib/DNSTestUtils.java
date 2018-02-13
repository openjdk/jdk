/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;

public class DNSTestUtils {
    public static final String TEST_DNS_SERVER_THREAD = "test.dns.server.thread";
    public static final String TEST_DNS_ROOT_URL = "test.dns.root.url";
    public static final int HOSTS_LOOKUP_MAX_DEPTH = 3;

    protected static boolean debug = true;

    /*
     * Check that attrs contains the mandatory attributes and the right
     * objectclass attribute
     */
    public static boolean checkSchema(Attributes attrs, String[] mandatory,
            String[] optional) {
        // Check mandatory attributes
        for (String mandatoryAttr : mandatory) {
            if (attrs.get(mandatoryAttr) == null) {
                debug("missing mandatory attribute: " + mandatoryAttr);
                return false;
            }
        }

        // Check optional attributes
        int optMissing = 0;
        for (String optionalAttr : optional) {
            if (attrs.get(optionalAttr) == null) {
                debug("warning: missing optional attribute: " + optionalAttr);
                ++optMissing;
            }
        }

        if (attrs.size() > (mandatory.length + (optional.length
                - optMissing))) {
            debug("too many attributes: " + attrs);
            return false;
        }

        return true;
    }

    /*
     * Process command line arguments and init env
     */
    public static Hashtable<Object, Object> initEnv(DatagramSocket socket,
            String testname, String[] args) {

        Hashtable<Object, Object> env = new Hashtable<>();

        // set some default parameters if no additional specified
        env.put("DNS_DOMAIN", "domain1.com.");
        env.put("FOREIGN_DOMAIN", "Central.Sun.COM.");
        env.put("FOREIGN_LEAF", "sunweb");

        // set defaults for some JNDI properties
        env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.dns.DnsContextFactory");

        boolean traceEnable = false;
        boolean loopPlayback = false;
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("-D")) && (args.length > i + 1)) {
                extractProperty(args[++i], env);
            } else if (args[i].startsWith("-D")) {
                extractProperty(args[i].substring(2), env);
            } else if (args[i].equalsIgnoreCase("-trace")) {
                traceEnable = true;
            } else if (args[i].equalsIgnoreCase("-loop")) {
                loopPlayback = true;
            }
        }

        debug = Boolean.valueOf(System.getProperty("debug", "true"));

        if (env.get("DNS_SERVER") != null) {
            String port = (String) env.get("DNS_PORT");
            String portSuffix = (port == null) ? "" : ":" + port;
            String url = "dns://" + env.get("DNS_SERVER") + portSuffix;
            env.put(Context.PROVIDER_URL, url);
            env.put(Context.PROVIDER_URL, url + "/" + env.get("DNS_DOMAIN"));
        }

        Runnable inst = null;
        if (traceEnable) {
            inst = createDNSTracer(socket, testname, env);
        } else {
            if (socket != null) {
                inst = createDNSServer(socket, testname, loopPlayback);
            } else {
                // for tests which run against remote server
                // or no server required
                debug("Skip local DNS Server creation "
                        + "since DatagramSocket is null");
            }
        }

        if (inst != null) {
            env.put(TEST_DNS_SERVER_THREAD, startServer(inst));
            String url = "dns://localhost:" + socket.getLocalPort();

            env.put(TEST_DNS_ROOT_URL, url);
            env.put(Context.PROVIDER_URL, url + "/" + env.get("DNS_DOMAIN"));
        }

        return env;
    }

    /*
     * Clean-up the directory context.
     */
    public static void cleanup(Context ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException e) {
                // ignore
            }
        }
    }

    private static void extractProperty(String propString,
            Hashtable<Object, Object> env) {
        int index;

        if ((index = propString.indexOf('=')) > 0) {
            env.put(propString.substring(0, index),
                    propString.substring(index + 1));
        } else {
            throw new RuntimeException(
                    "Failed to extract test args property from " + propString);
        }
    }

    public static DNSTracer createDNSTracer(DatagramSocket socket,
            String testname, Hashtable<Object, Object> env) {
        if (socket == null) {
            throw new RuntimeException("Error: failed to create DNSTracer "
                    + "since DatagramSocket is null");
        }

        try {
            PrintStream outStream = new PrintStream(getCaptureFile(testname));
            return new DNSTracer(socket, outStream,
                    (String) env.get("DNS_SERVER"),
                    Integer.parseInt((String) env.get("DNS_PORT")));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error: failed to create DNSTracer : " + e.getMessage(), e);
        }
    }

    public static DNSServer createDNSServer(DatagramSocket socket,
            String testname, boolean loop) {
        if (socket == null) {
            throw new RuntimeException("Error: failed to create DNSServer "
                    + "since DatagramSocket is null");
        }

        String path = getCaptureFile(testname);
        if (Files.exists(Paths.get(path))) {
            return new DNSServer(socket, path, loop);
        } else {
            throw new RuntimeException(
                    "Error: failed to create DNSServer, not found dns "
                            + "cache file " + path);
        }
    }

    public static Thread startServer(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();
        return thread;
    }

    public static String getCaptureFile(String testname) {
        return Paths.get(System.getProperty("test.src"))
                .resolve(testname + ".dns").toString();
    }

    public static void enableHostsFile(String hostsFile) {
        System.out.println("Enable jdk.net.hosts.file = " + hostsFile);
        System.setProperty("jdk.net.hosts.file", hostsFile);
    }

    public static void enableHostsFile(int depth) {
        Path path = Paths.get(System.getProperty("test.src", "."))
                .toAbsolutePath();
        for (int i = depth; i >= 0; i--) {
            Path filePath = path.resolve("hosts");
            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                enableHostsFile(filePath.toString());
                break;
            }

            path = path.getParent();
            if (path == null) {
                break;
            }
        }
    }

    public static void debug(Object object) {
        if (debug) {
            System.out.println(object);
        }
    }

    public static void verifySchema(Attributes attrs, String[] mandatory,
            String[] optional) {
        debug(attrs);
        if (!checkSchema(attrs, mandatory, optional)) {
            throw new RuntimeException("Check schema failed.");
        }
    }
}
