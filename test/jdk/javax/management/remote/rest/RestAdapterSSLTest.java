/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.json.parser.JSONParser;
import com.oracle.jmx.remote.rest.json.parser.ParseException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.rest.JmxRestAdapter;
import javax.management.remote.rest.PlatformRestAdapter;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test
 * @modules java.logging
 *          java.management
 *          java.management.rest
 * @run testng/othervm RestAdapterSSLTest
 * 
 */
@Test
public class RestAdapterSSLTest {

    private static final String CHARSET = "UTF-8";
    private static String sslAgentConfig;
    private static String sslClientConfig;
    private static String passwordFile;
    private static String configFile;

    private void createAgentSslConfigFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (f.exists()) {
            return;
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
            return;
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

    private void setupMgmtConfig(String fileName) throws IOException {
        Properties props = new Properties();
        
        props.setProperty("com.sun.management.jmxremote.ssl", "true");
        props.setProperty("com.sun.management.jmxremote.ssl.config.file", sslAgentConfig);
        props.setProperty("com.sun.management.jmxremote.password.file", passwordFile);
        props.setProperty("com.sun.management.jmxremote.rest.port", "0");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void setupConfig() throws Exception {
        String testSrcRoot = System.getProperty("test.src") + File.separator;
        sslAgentConfig = testSrcRoot + "sslConfigAgent";
        sslClientConfig = testSrcRoot + "sslConfigClient";
        passwordFile = testSrcRoot + "password.properties";
        configFile = testSrcRoot + "mgmt.properties";
        createAgentSslConfigFile(sslAgentConfig);
        createClientSslConfigFile(sslClientConfig);
        setupMgmtConfig(configFile);
    }

    @BeforeClass
    public void setupAdapter() throws Exception {
        setupConfig();
        File file = new File(configFile);
        Properties props = new Properties();
        props.load(new FileInputStream(file));
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }
        PlatformRestAdapter.getInstance().start();
        SSLContext ctx = getSSlContext(sslClientConfig);
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(
                (String hostname, javax.net.ssl.SSLSession sslSession) -> hostname.equals("HOSTNAME"));
        setupMbean();
    }

    @AfterClass
    public void tearDown() {
        PlatformRestAdapter.stop();
    }

    @DataProvider
    public Object[][] getUrlList() {
        Object[][] data = new Object[7][1];
        data[0][0] = "?domain=default";
        data[1][0] = "mbeans";
        data[2][0] = "domains";
        data[3][0] = "java.lang:type=Memory";
        data[4][0] = "java.lang:type=Memory/HeapMemoryUsage";
        data[5][0] = "java.lang:type=Memory/?attributes=HeapMemoryUsage,ObjectPendingFinalizationCount,NonHeapMemoryUsage";
        data[6][0] = "java.lang:type=Memory/?attributes=all";
        return data;
    }

    @DataProvider
    public Object[][] getMalformedUrlList() {
        Object[][] data = new Object[1][1];
        data[0][0] = "com.example:type=QueueSamplerMBean";
        return data;
    }

    private String executeHttpRequest(String inputUrl) throws IOException {
        return executeHttpRequest(inputUrl, null);
    }

    private String executeHttpRequest(String inputUrl, String charset) throws IOException {
        if (inputUrl != null && !inputUrl.isEmpty()) {
            JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
            URL url = new URL(adapter.getBaseUrl() + (charset != null ? URLEncoder.encode(inputUrl, charset) : inputUrl));
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setDoOutput(false);
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
            if (charset != null && !charset.isEmpty()) {
                con.setRequestProperty("Content-Type", "application/json; charset=" + charset);
            }
            try {
                int status = con.getResponseCode();
                if (status == 200) {

                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(charset != null ? URLDecoder.decode(input, charset) : input);
                        }
                    }
                    return sbuf.toString();
                } else {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getErrorStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(charset != null ? URLDecoder.decode(input, charset) : input);
                        }
                    }
                    return sbuf.toString();
                }
            } catch (IOException e) {
            }
        }
        return null;
    }

    @Test
    public void testMisc() throws IOException, ParseException {
        String mBeanInfo = executeHttpRequest("com.sun.management:type=DiagnosticCommand");
        System.out.println(mBeanInfo);
        JSONParser parser = new JSONParser(mBeanInfo);
        JSONObject response = (JSONObject) parser.parse();
        long status = (Long) ((JSONPrimitive) response.get("status")).getValue();
        Assert.assertEquals(status, 200);
    }

    @Test(enabled = false)
    public void testCharset() throws IOException {
        String result1 = executeHttpRequest("?domain=default");
        String result2 = executeHttpRequest("?domain=default", CHARSET);
        Assert.assertEquals(result1, result2);
    }

    @Test
    public void testPostMisc() throws IOException {
        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        URL url = new URL(adapter.getBaseUrl());
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        String userCredentials = "username1:password1";
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
        connection.setRequestProperty("Authorization", basicAuth);

        String req = "{\n"
                + "    \"name\":\"java.lang:type=Memory\"\n"
                + "    \"write\":\"Verbose\"\n"
                + "    \"arguments\" : [true]\n"
                + "}";
        try (OutputStreamWriter out = new OutputStreamWriter(
                connection.getOutputStream(), CHARSET)) {
            out.write(URLEncoder.encode(req, CHARSET));
            out.flush();
        }

        print_content(connection);
    }

    @Test
    public void testBasicHttps() throws Exception {
        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        URL url = new URL(adapter.getBaseUrl());
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setDoOutput(false);

        String userCredentials = "username1:password1";

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
        con.setRequestProperty("Authorization", basicAuth);
        print_https_cert(con);
        print_content(con);
    }

    @Test(dataProvider = "getUrlList")
    public void testGetRequests(String urlStr) throws Exception {
        String input = executeHttpRequest(urlStr);
        System.out.println("URL : [" + urlStr + "] ----> " + input);
        System.out.println(input);
        JSONParser parser = new JSONParser(input);
        JSONElement parse = parser.parse();
        JSONElement jstatus = ((JSONObject) parse).get("status");
        long status = (Long) ((JSONPrimitive) jstatus).getValue();
        Assert.assertEquals(status, 200);
    }

    @Test
    public void testAllGetMBeanInfo() throws Exception {
        String input = executeHttpRequest("mbeans");
        JSONParser parser = new JSONParser(input);
        JSONElement jsonObj = parser.parse();
        if (jsonObj instanceof JSONObject) {
            JSONElement jelem = ((JSONObject) jsonObj).get("response");
            if (jelem instanceof JSONArray) {
                for (JSONElement elem : ((JSONArray) jelem)) {
                    String objName = (String) ((JSONPrimitive) elem).getValue();
                    String mBeanInfo = executeHttpRequest(objName);
                    System.out.println(mBeanInfo);
                    parser = new JSONParser(mBeanInfo);
                    JSONObject response = (JSONObject) parser.parse();
                    long status = (Long) ((JSONPrimitive) response.get("status")).getValue();
                    Assert.assertEquals(status, 200);
                }
            }
        }
    }

    @Test(enabled = false, dataProvider = "getMalformedUrlList")
    public void negTestGetRequests(String urlStr) throws Exception {
        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        URL url = new URL(adapter.getBaseUrl() + urlStr);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setDoOutput(false);
        String userCredentials = "username1:password1";
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
        con.setRequestProperty("Authorization", basicAuth);
        print_content(con);
    }

    @Test
    public void testPost() throws Exception {
        JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
        URL url = new URL(adapter.getBaseUrl());
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        String userCredentials = "username1:password1";
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
        connection.setRequestProperty("Authorization", basicAuth);

        String postReq = "{\"name\":\"com.example:type=QueueSampler\",\"exec\":\"testMethod1\",\"arguments\":[[1,2,3],\"abc\",5,[\"asd\",\"3\",\"67\",\"778\"],[{date:\"2016-03-02\",size:3,head:\"head\"}],[{date:\"2016-03-02\",size:3,head:\"head\"}]]}";
        JSONArray jarr = new JSONArray();

        JSONObject jobject = new JSONObject();
        jobject.put("name", "com.example:type=QueueSampler");
        jobject.put("write", "QueueName");
        JSONArray jarr1 = new JSONArray();
        jarr1.add(new JSONPrimitive("Dequeue"));
        jobject.put("arguments", jarr1);
        jarr.add(jobject);

        jobject = new JSONObject();
        jobject.put("name", "com.example:type=QueueSampler");
        jobject.put("read", "QueueName");
        jarr.add(jobject);

        jarr.add(new JSONParser(postReq).parse());

        try (OutputStreamWriter out = new OutputStreamWriter(
                connection.getOutputStream(), CHARSET)) {
            out.write(URLEncoder.encode(jarr.toJsonString(), CHARSET));
            out.flush();
        }
        print_content(connection);
    }

    @Test(enabled = false)
    public void testMBeanFilter() throws MalformedObjectNameException {
        // Add non-compliant MBean to platform mbean server
        ObjectName mbeanName = new ObjectName("com.example:type=QueueSamplerMBean");
    }

    private void setupMbean() throws Exception {
        // Get the Platform MBean Server
//        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
//
//        // Construct the ObjectName for the QueueSampler MXBean we will register
//        ObjectName mxbeanName = new ObjectName("com.example:type=QueueSampler");
//        ObjectName mbeanName = new ObjectName("com.example:type=QueueSamplerMBean");
//
//        // Create the Queue Sampler MXBean
//        Queue<String> queue = new ArrayBlockingQueue<>(10);
//        queue.add("Request-1");
//        queue.add("Request-2");
//        queue.add("Request-3");
//        QueueSampler mxbean = new QueueSampler(queue);
//        QueueSamplerBean mbean = new QueueSamplerBean(queue);
//
//        // Register the Queue Sampler MXBean
//        mbs.registerMBean(mxbean, mxbeanName);
//        mbs.registerMBean(mbean, mbeanName);
    }

    private void print_content(HttpURLConnection con) {
        if (con != null) {
            try {
                System.out.println("****** Content of the URL ********");
                int status = con.getResponseCode();

                System.out.println("Status = " + status);
                InputStream is;
                if (status == HttpURLConnection.HTTP_OK) {
                    is = con.getInputStream();
                } else {
                    is = con.getErrorStream();
                }
                BufferedReader br
                        = new BufferedReader(new InputStreamReader(is));
                String input;
                while ((input = br.readLine()) != null) {
                    System.out.println(URLDecoder.decode(input, CHARSET));
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
            Logger.getLogger(PlatformRestAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
