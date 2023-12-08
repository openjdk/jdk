/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.management.remote.rest;

import jdk.internal.management.remote.rest.http.MBeanServerCollectionResource;
import jdk.internal.management.remote.rest.http.MBeanServerResource;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.management.*;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is the root class that initializes the HTTPServer and
 * REST adapter for platform mBeanServer.
 */
public final class PlatformRestAdapter {

    /*
     * Initializes HTTPServer with settings from config file
     * acts as container for platform rest adapter
     */
    private static HttpServer httpServer = null;

    // Save configuration to be used for other MBeanServers
    private static Map<String, Object> env;
    private static List<MBeanServerResource> restAdapters = new CopyOnWriteArrayList<>();
    private static final int maxThreadCount = 5;

    private PlatformRestAdapter() {
    }

    @SuppressWarnings("removal")
    private static class HttpThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "http-thread-";

        HttpThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * Starts the HTTP server with the input configuration properties
     * The configuration properties are Interface name/IP, port and SSL configuration
     * By default the server binds to address '0.0.0.0' and port '0'. SSL is off by default. [TODO]The
     * keyStore will be created one if not configured and the private key and a public certificate will
     * be generated[/TODO].
     * Below properties are used to configure the HTTP server.
     * com.sun.management.jmxremote.rest.port
     * com.sun.management.jmxremote.rest.host
     * com.sun.management.jmxremote.ssl
     * com.sun.management.jmxremote.ssl.config.file
     * javax.net.ssl.keyStore
     * javax.net.ssl.trustStore
     * javax.net.ssl.keyStorePassword
     * javax.net.ssl.trustStorePassword
     *
     * @param properties Config properties for the HTTP server.
     *                   If null or if any of the properties is not specified, default values will be assumed.
     * @throws IOException If the server could not be created
     */
    public static synchronized void init(Properties properties) throws IOException {
        if (httpServer == null) {
            if (properties == null || properties.isEmpty()) {
                properties = new Properties();
                properties.setProperty("com.sun.management.jmxremote.ssl", "false");
                properties.setProperty("com.sun.management.jmxremote.authenticate", "false");
                properties.setProperty("com.sun.management.jmxremote.rest.port", "0");
            }
            final int port;
            try {
                port = Integer.parseInt(properties.getProperty(PropertyNames.PORT, DefaultValues.PORT));
            } catch (NumberFormatException x) {
                throw new IllegalArgumentException("Invalid string for port");
            }
            if (port < 0) {
                throw new IllegalArgumentException("Invalid string for port");
            }

            String host = properties.getProperty(PropertyNames.HOST, DefaultValues.HOST);

            boolean useSSL = Boolean.parseBoolean(properties.getProperty(
                    PropertyNames.USE_SSL, DefaultValues.USE_SSL));
            if (useSSL) {
                final String sslConfigFileName
                        = properties.getProperty(PropertyNames.SSL_CONFIG_FILE_NAME);
                SSLContext ctx = getSSlContext(sslConfigFileName);
                if (ctx != null) {
                    HttpsServer server = HttpsServer.create(new InetSocketAddress(host, port), 0);
                    server.setHttpsConfigurator(new HttpsConfigurator(ctx));
                    httpServer = server;
                } else {
                    httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
                }
            } else {
                httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            }

            new MBeanServerCollectionResource(restAdapters, httpServer);
            httpServer.setExecutor(Executors.newFixedThreadPool(maxThreadCount, new HttpThreadFactory()));
            httpServer.start();
            startDefaultRestAdapter(properties);
        }
    }

    public static boolean isStarted() {
        return httpServer!=null;
    }

    private static void startDefaultRestAdapter(Properties properties) {
        env = new HashMap<>();
        // Do we use authentication?
        final String useAuthenticationStr
                = properties.getProperty(PropertyNames.USE_AUTHENTICATION,
                DefaultValues.USE_AUTHENTICATION);
        final boolean useAuthentication
                = Boolean.valueOf(useAuthenticationStr);

        String loginConfigName;
        String passwordFileName;

        if (useAuthentication) {
            env.put("jmx.remote.x.authentication", Boolean.TRUE);
            // Get non-default login configuration
            loginConfigName
                    = properties.getProperty(PropertyNames.LOGIN_CONFIG_NAME);
            env.put("jmx.remote.x.login.config", loginConfigName);

            if (loginConfigName == null) {
                // Get password file
                passwordFileName
                        = properties.getProperty(PropertyNames.PASSWORD_FILE_NAME);
                env.put("jmx.remote.x.password.file", passwordFileName);
            }
        }
        MBeanServerResource adapter = new MBeanServerResource(httpServer, ManagementFactory.getPlatformMBeanServer(), "platform", env);
        adapter.start();
        restAdapters.add(adapter);
    }

    /**
     * Wraps the mbeanServer in a REST adapter. The mBeanServer will be accessible over REST APIs
     * at supplied context. env parameter configures authentication parameters for the MBeanServer.
     *
     * @param mbeanServer The MBeanServer to be wrapped in REST adapter
     * @param context     The context in HTTP server under which this MBeanServer will be available over REST
     *                    If it is null or empty, a context will be generated
     * @param env         configures authemtication parameters for accessing the MBeanServer over this adapter
     *                    If null, configuration from default rest adapter will be used.
     *                    Below is the list of properties.
     *                    <p>
     *                    jmx.remote.x.authentication : enable/disable user authentication
     *                    jmx.remote.authenticator :  Instance of a JMXAuthenticator
     *                    jmx.remote.x.login.config : JAAS login conguration
     *                    jmx.remote.x.password.file : file name for default JAAS login configuration
     * @return an Instance of REST adapter that allows to start/stop the adapter
     */
    public static synchronized JmxRestAdapter newRestAdapter(MBeanServer mbeanServer, String context, Map<String, ?> env) {
        if (httpServer == null) {
            throw new IllegalStateException("Platform Adapter not initialized");
        }

        MBeanServerResource server = restAdapters.stream()
                .filter(s -> areMBeanServersEqual(s.getMBeanServer(), mbeanServer))
                .findFirst()
                .get();
        if (server == null) {
            MBeanServerResource adapter = new MBeanServerResource(httpServer, mbeanServer, context, env);
            adapter.start();
            restAdapters.add(adapter);
            return adapter;
        } else {
            throw new IllegalArgumentException("MBeanServer already registered at " + server.getUrl());
        }
    }

    private static boolean areMBeanServersEqual(MBeanServer server1, MBeanServer server2) {
        MBeanServerDelegateMBean bean1 = JMX.newMBeanProxy(server1, MBeanServerDelegate.DELEGATE_NAME, MBeanServerDelegateMBean.class);
        MBeanServerDelegateMBean bean2 = JMX.newMBeanProxy(server2, MBeanServerDelegate.DELEGATE_NAME, MBeanServerDelegateMBean.class);
        return bean1.getMBeanServerId().equalsIgnoreCase(bean2.getMBeanServerId());
    }

    public synchronized static void stop() {
        restAdapters.forEach(r -> r.stop());
        restAdapters.clear();
        if (httpServer != null) {
            ExecutorService executor = (ExecutorService) httpServer.getExecutor();
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
            httpServer.stop(0);
            httpServer = null;
        }
    }

    public static synchronized String getDomain() {
        if (httpServer == null) {
            throw new IllegalStateException("Platform rest adapter not initialized");
        }
        try {
            if (httpServer instanceof HttpsServer) {
                return "https://" + InetAddress.getLocalHost().getCanonicalHostName() + ":" + httpServer.getAddress().getPort();
            }
            return "http://" + InetAddress.getLocalHost().getCanonicalHostName() + ":" + httpServer.getAddress().getPort();
        } catch (UnknownHostException ex) {
            return "http://localhost" + ":" + httpServer.getAddress().getPort();
        }
    }

    public static synchronized String getBaseURL() {
        return getDomain() + "/jmx/servers";
    }

    private static SSLContext getSSlContext(String sslConfigFileName) {
        final String keyStore, keyStorePassword, trustStore, trustStorePassword;

        try {
            if (sslConfigFileName == null || sslConfigFileName.isEmpty()) {
                keyStore = System.getProperty(PropertyNames.SSL_KEYSTORE_FILE);
                keyStorePassword = System.getProperty(PropertyNames.SSL_KEYSTORE_PASSWORD);
                trustStore = System.getProperty(PropertyNames.SSL_TRUSTSTORE_FILE);
                trustStorePassword = System.getProperty(PropertyNames.SSL_TRUSTSTORE_PASSWORD);
            } else {
                Properties p = new Properties();
                BufferedInputStream bin = new BufferedInputStream(new FileInputStream(sslConfigFileName));
                p.load(bin);
                keyStore = p.getProperty(PropertyNames.SSL_KEYSTORE_FILE);
                keyStorePassword = p.getProperty(PropertyNames.SSL_KEYSTORE_PASSWORD);
                trustStore = p.getProperty(PropertyNames.SSL_TRUSTSTORE_FILE);
                trustStorePassword = p.getProperty(PropertyNames.SSL_TRUSTSTORE_PASSWORD);
            }

            char[] keyStorePasswd = null;
            if (keyStorePassword.length() != 0) {
                keyStorePasswd = keyStorePassword.toCharArray();
            }

            char[] trustStorePasswd = null;
            if (trustStorePassword.length() != 0) {
                trustStorePasswd = trustStorePassword.toCharArray();
            }

            KeyStore ks = null;
            if (keyStore != null) {
                ks = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream ksfis = new FileInputStream(keyStore);
                ks.load(ksfis, keyStorePasswd);

            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyStorePasswd);

            KeyStore ts = null;
            if (trustStore != null) {
                ts = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream tsfis = new FileInputStream(trustStore);
                ts.load(tsfis, trustStorePasswd);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception ex) {
        }
        return null;
    }

    /**
     * Default values for JMX configuration properties.
     */
    static interface DefaultValues {

        public static final String PORT = "0";
        public static final String HOST = "0.0.0.0";
        public static final String USE_SSL = "false";
        public static final String USE_AUTHENTICATION = "false";
        public static final String PASSWORD_FILE_NAME = "jmxremote.password";
    }

    /**
     * Names of JMX configuration properties.
     */
    public static interface PropertyNames {

        public static final String PORT
                = "com.sun.management.jmxremote.rest.port";
        public static final String HOST
                = "com.sun.management.jmxremote.host";
        public static final String USE_SSL
                = "com.sun.management.jmxremote.ssl";
        public static final String SSL_CONFIG_FILE_NAME
                = "com.sun.management.jmxremote.ssl.config.file";
        public static final String SSL_KEYSTORE_FILE
                = "javax.net.ssl.keyStore";
        public static final String SSL_TRUSTSTORE_FILE
                = "javax.net.ssl.trustStore";
        public static final String SSL_KEYSTORE_PASSWORD
                = "javax.net.ssl.keyStorePassword";
        public static final String SSL_TRUSTSTORE_PASSWORD
                = "javax.net.ssl.trustStorePassword";
        public static final String USE_AUTHENTICATION
                = "com.sun.management.jmxremote.authenticate";
        public static final String PASSWORD_FILE_NAME
                = "com.sun.management.jmxremote.password.file";
        public static final String LOGIN_CONFIG_NAME
                = "com.sun.management.jmxremote.login.config";
    }
}
