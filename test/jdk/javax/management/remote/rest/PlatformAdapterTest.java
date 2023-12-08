
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Properties;
import javax.management.remote.rest.JmxRestAdapter;
import javax.management.remote.rest.PlatformRestAdapter;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

/**
 * @test 
 * @modules java.management.rest
 * @run testng/othervm PlatformAdapterTest
 */
@Test
public class PlatformAdapterTest {

    private static final String MBEANS = "mbeans";
    private static String sslAgentConfig;
    private static String sslClientConfig;
    private static String passwordFile;
    

    @BeforeClass
    public void setup() throws IOException {
        String testSrcRoot = System.getProperty("test.src") + File.separator;
        sslAgentConfig = testSrcRoot + "sslConfigAgent";
        sslClientConfig = testSrcRoot + "sslConfigClient";
        passwordFile = testSrcRoot + "password.properties";
        createAgentSslConfigFile(sslAgentConfig);
        createClientSslConfigFile(sslClientConfig);
    }
    
    @Test
    public void testHttpNoAuth() throws Exception {
        Properties props = new Properties();
        props.setProperty("com.sun.management.jmxremote.rest.port", "8686");
        props.setProperty("com.sun.management.jmxremote.ssl", "false");
        props.setProperty("com.sun.management.jmxremote.authenticate", "false");
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }

        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        adapter.start();
        URL url = new URL(adapter.getBaseUrl() + MBEANS);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(false);
        print_content(con);
        PlatformRestAdapter.stop();
    }

    @Test
    public void testHttpAuth() throws Exception {
        Properties props = new Properties();
        props.setProperty("com.sun.management.jmxremote.rest.port", "8686");
        props.setProperty("com.sun.management.jmxremote.ssl", "false");
        props.setProperty("com.sun.management.jmxremote.authenticate", "true");
        props.setProperty("com.sun.management.jmxremote.password.file", passwordFile);
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }

        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        adapter.start();
        URL url = new URL(adapter.getBaseUrl() + MBEANS);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(false);

        String userCredentials = "username1:password1";

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
        con.setRequestProperty("Authorization", basicAuth);
        print_content(con);

        PlatformRestAdapter.stop();
    }

    private void createAgentSslConfigFile(String fileName) throws IOException {
        Properties props = new Properties();
        String testDir = System.getProperty("test.src");
        props.setProperty("javax.net.ssl.keyStore", testDir + File.separator + "keystoreAgent");
        props.setProperty("javax.net.ssl.keyStorePassword", "glopglop");
        props.setProperty("javax.net.ssl.trustStore", testDir + File.separator + "truststoreAgent");
        props.setProperty("javax.net.ssl.trustStorePassword","glopglop");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void createClientSslConfigFile(String fileName) throws IOException {
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

    @Test
    public void testHttpsNoAuth() throws Exception {
        Properties props = new Properties();
        props.setProperty("com.sun.management.jmxremote.rest.port", "8686");
        props.setProperty("com.sun.management.jmxremote.ssl", "true");
        props.setProperty("com.sun.management.jmxremote.ssl.config.file", sslAgentConfig);
        props.setProperty("com.sun.management.jmxremote.authenticate", "false");
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }
        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        adapter.start();
        SSLContext ctx = getSSlContext(sslClientConfig);
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(
                (String hostname, javax.net.ssl.SSLSession sslSession) -> hostname.equals("HOSTNAME"));

        URL url = new URL(adapter.getBaseUrl() + MBEANS);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setDoOutput(false);
        print_https_cert(con);
        print_content(con);
        PlatformRestAdapter.stop();
    }

    @Test
    public void testHttpsAuth() throws Exception {
        Properties props = new Properties();
        props.setProperty("com.sun.management.jmxremote.rest.port", "8686");
        props.setProperty("com.sun.management.jmxremote.ssl", "true");
        props.setProperty("com.sun.management.jmxremote.ssl.config.file", sslAgentConfig);
        props.setProperty("com.sun.management.jmxremote.authenticate", "true");
        props.setProperty("com.sun.management.jmxremote.password.file", passwordFile);
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }

        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        adapter.start();
        SSLContext ctx = getSSlContext(sslClientConfig);
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(
                (String hostname, javax.net.ssl.SSLSession sslSession) -> hostname.equals("HOSTNAME"));

        URL url = new URL(adapter.getBaseUrl() + MBEANS);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setDoOutput(false);

        String userCredentials = "username1:password1";

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
        con.setRequestProperty("Authorization", basicAuth);
        print_https_cert(con);
        print_content(con);
        PlatformRestAdapter.stop();
    }

    private void print_content(HttpURLConnection con) {
        if (con != null) {
            try {
                System.out.println("****** Content of the URL ********");
                int status = con.getResponseCode();
                System.out.println("Status = " + status);
                BufferedReader br
                        = new BufferedReader(
                                new InputStreamReader(con.getInputStream()));
                String input;
                while ((input = br.readLine()) != null) {
                    System.out.println(input);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void print_https_cert(HttpsURLConnection con) {
        if (con != null) {
            try {
                System.out.println("Response Code : " + con.getResponseCode());
                System.out.println("Cipher Suite : " + con.getCipherSuite());
                System.out.println("\n");

                Certificate[] certs = con.getServerCertificates();
                for (Certificate cert : certs) {
                    System.out.println("Cert Type : " + cert.getType());
                    System.out.println("Cert Hash Code : " + cert.hashCode());
                    System.out.println("Cert Public Key Algorithm : "
                            + cert.getPublicKey().getAlgorithm());
                    System.out.println("Cert Public Key Format : "
                            + cert.getPublicKey().getFormat());
                    System.out.println("\n");
                }

            } catch (SSLPeerUnverifiedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static SSLContext getSSlContext(String sslConfigFileName) {
        String keyStore, keyStorePassword, trustStore, trustStorePassword;
        System.out.println("SSL Config file : " + sslConfigFileName);

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
}
