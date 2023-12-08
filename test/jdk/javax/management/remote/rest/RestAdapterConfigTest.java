import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jdk.internal.management.remote.rest.PlatformRestAdapter;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/* @test
 * @summary Configuration test for rest adapter
 * @library /test/lib
 * @modules jdk.management.rest/jdk.internal.management.remote.rest.http
 *          jdk.management.rest/jdk.internal.management.remote.rest.json
 *          jdk.management.rest/jdk.internal.management.remote.rest.json.parser
 *          jdk.management.rest/jdk.internal.management.remote.rest.mapper
 *          jdk.management.rest/jdk.internal.management.remote.rest
 * @build RestAdapterConfigTest RestAdapterTest
 * @run testng/othervm RestAdapterConfigTest
 */

@Test
public class RestAdapterConfigTest {
    private static String sslAgentConfig;
    private static String sslClientConfig;
    private static String passwordFile;
    private static String configFile;
    private static RestAdapterTest restAdapterTest = new RestAdapterTest();
    private static final Set<Method> tests;

    static {
        tests = Stream.of(RestAdapterTest.class.getMethods())
                .filter(a -> a.getName().startsWith("test"))
                .collect(Collectors.toSet());
    }

    private void createAgentSslConfigFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }

        Properties props = new Properties();
        String testDir = System.getProperty("test.src");
        props.setProperty("javax.net.ssl.keyStore", testDir + File.separator + "keystoreAgent");
        props.setProperty("javax.net.ssl.keyStorePassword", "glopglop");
        props.setProperty("javax.net.ssl.trustStore", testDir + File.separator + "truststoreAgent");
        props.setProperty("javax.net.ssl.trustStorePassword", "glopglop");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void createClientSslConfigFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }
        Properties props = new Properties();
        String testDir = System.getProperty("test.src");
        props.setProperty("javax.net.ssl.keyStore", testDir + File.separator + "keystoreClient");
        props.setProperty("javax.net.ssl.keyStorePassword", "glopglop");
        props.setProperty("javax.net.ssl.trustStore", testDir + File.separator + "truststoreClient");
        props.setProperty("javax.net.ssl.trustStorePassword", "glopglop");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void setupMgmtConfig(String fileName, boolean isSSL, boolean isAuth) throws IOException {
        Properties props = new Properties();
        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }

        props.setProperty("com.sun.management.jmxremote.ssl", isSSL ? "true" : "false");
        if(isSSL) {
            props.setProperty("com.sun.management.jmxremote.ssl.config.file", sslAgentConfig);
        }
        props.setProperty("com.sun.management.jmxremote.authenticate", isAuth ? "true" : "false");
        if (isAuth) {
            props.setProperty("com.sun.management.jmxremote.password.file", passwordFile);
        }
        props.setProperty("com.sun.management.jmxremote.rest.port", "0");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    @BeforeClass
    public void init() throws Exception {
        String testSrcRoot = System.getProperty("test.src") + File.separator;
        sslAgentConfig = testSrcRoot + "sslConfigAgent";
        sslClientConfig = testSrcRoot + "sslConfigClient";
        passwordFile = testSrcRoot + "password.properties";
        configFile = testSrcRoot + "mgmt.properties";

        createAgentSslConfigFile(sslAgentConfig);
        createClientSslConfigFile(sslClientConfig);

        SSLContext ctx = getSSlContext(sslClientConfig);
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(
                (String hostname, javax.net.ssl.SSLSession sslSession) -> {
                    try {
                        return hostname.equals(InetAddress.getLocalHost().getHostName());
                    } catch (UnknownHostException ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    private static SSLContext getSSlContext(String sslConfigFileName) {
        final String keyStore, keyStorePassword, trustStore, trustStorePassword;

        try {
            Properties p = new Properties();
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(sslConfigFileName));
            p.load(bin);
            keyStore = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_KEYSTORE_FILE);
            keyStorePassword = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_KEYSTORE_PASSWORD);
            trustStore = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_TRUSTSTORE_FILE);
            trustStorePassword = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_TRUSTSTORE_PASSWORD);

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

    @Test
    public void testHttpNoAuth() throws Exception {
        setupMgmtConfig(configFile, false, false);
        restAdapterTest.setupServers();
        for (Method m : tests) {
            m.invoke(restAdapterTest);
        }
        restAdapterTest.tearDownServers();
    }

    public void testHttpsNoAuth() throws Exception {
        setupMgmtConfig(configFile, true, false);
        restAdapterTest.setupServers();
        for (Method m : tests) {
            m.invoke(restAdapterTest);
        }
        restAdapterTest.tearDownServers();
    }

    public void testHttpAuth() throws Exception {
        setupMgmtConfig(configFile, false, true);
        restAdapterTest.setupServers();
        for (Method m : tests) {
            m.invoke(restAdapterTest);
        }
        restAdapterTest.tearDownServers();
    }

    public void testHttpsAuth() throws Exception {
        setupMgmtConfig(configFile, true, true);
        restAdapterTest.setupServers();
        for (Method m : tests) {
            m.invoke(restAdapterTest);
        }
        restAdapterTest.tearDownServers();
    }

    @AfterClass
    public void tearDown() {
        File f = new File(sslAgentConfig);
        if (f.exists())
            f.delete();
        f = new File(sslClientConfig);
        if (f.exists())
            f.delete();
        f = new File(configFile);
        if (f.exists())
            f.delete();
    }

}
