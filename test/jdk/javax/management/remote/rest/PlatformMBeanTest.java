/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest.test;

import javax.management.remote.rest.JmxRestAdapter;
import javax.management.remote.rest.PlatformRestAdapter;
import javax.management.remote.rest.json.JSONArray;
import javax.management.remote.rest.json.JSONElement;
import javax.management.remote.rest.json.JSONObject;
import javax.management.remote.rest.json.JSONPrimitive;
import javax.management.remote.rest.json.parser.JSONParser;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.remote.rest.JmxRestAdapter;
import javax.management.remote.rest.PlatformRestAdapter;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 *
 */
public class PlatformMBeanTest {

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

    private static final String CHARSET = "UTF-8";

    private String getFilePath(String filename) {
        return System.getProperty("user.dir") + File.separator + filename;
    }

    @BeforeClass
    public void setupAdapter() throws Exception {
        File file = new File(getFilePath("management.properties"));
        Properties props = new Properties();
        props.load(new FileInputStream(file));
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }
        PlatformRestAdapter.getInstance().start();
        SSLContext ctx = getSSlContext(getFilePath("sslconfigClient"));
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(
                (String hostname, javax.net.ssl.SSLSession sslSession) -> hostname.equals("HOSTNAME));
    }

    @AfterClass
    public void tearDown() {
        PlatformRestAdapter.stop();
    }

    private String executeHttpGetRequest(String inputUrl) throws MalformedURLException, IOException {
        if (inputUrl != null && !inputUrl.isEmpty()) {
            JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
            URL url = new URL(adapter.getBaseUrl() + inputUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setDoOutput(false);
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
            try {
                int status = con.getResponseCode();
                if (status == 200) {

                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
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
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                }
            } catch (IOException e) {
            }
        }
        return null;
    }

    private String executeHttpPostRequest(String postBody) throws MalformedURLException, IOException {
        if (postBody != null && !postBody.isEmpty()) {
            JmxRestAdapter adapter = PlatformRestAdapter.getInstance();
            URL url = new URL(adapter.getBaseUrl());
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            connection.setRequestProperty("Authorization", basicAuth);
            try (OutputStreamWriter out = new OutputStreamWriter(
                    connection.getOutputStream(), CHARSET)) {
                out.write(URLEncoder.encode(postBody, CHARSET));
                out.flush();
            }
            try {
                int status = connection.getResponseCode();
                if (status == 200) {

                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                } else {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Test
    public void testOperatingSystemMbean() throws Exception {
        //  Get MBeanInfo
        String osMbeanInfo = executeHttpGetRequest("java.lang:type=OperatingSystem");
        System.out.println(osMbeanInfo);

        // Read all attributes
        JSONParser parser = new JSONParser(osMbeanInfo);
        JSONObject docRoot = (JSONObject) parser.parse();
        JSONArray attrJson = (JSONArray) ((JSONObject) docRoot.get("response")).get("attributeInfo");
        for (JSONElement elem : attrJson) {
            JSONObject attrElem = (JSONObject) elem;
            JSONPrimitive a = (JSONPrimitive) attrElem.get("name");
            JSONPrimitive access = (JSONPrimitive) attrElem.get("access");
            String attrResponse = executeHttpGetRequest("java.lang:type=OperatingSystem/" + a.getValue());
            parser = new JSONParser(attrResponse);
            docRoot = (JSONObject) parser.parse();
            JSONPrimitive result = (JSONPrimitive) ((JSONObject) docRoot.get("response")).get(a.getValue());
            System.out.println("Attribute : " + a.getValue() + "(" + access.getValue() + ")" + " - " + result.getValue());
        }
    }

    @Test
    public void testHotSpotDiagMBean() throws Exception {
        String mbeanName = "com.sun.management:type=HotSpotDiagnostic";
        String osMbeanInfo = executeHttpGetRequest(mbeanName);
        System.out.println(osMbeanInfo);

        // Read all attributes
        JSONParser parser = new JSONParser(osMbeanInfo);
        JSONObject docRoot = (JSONObject) parser.parse();
        JSONArray attrJson = (JSONArray) ((JSONObject) docRoot.get("response")).get("attributeInfo");
        if (attrJson != null) {
            for (JSONElement elem : attrJson) {
                JSONObject attrElem = (JSONObject) elem;
                JSONPrimitive a = (JSONPrimitive) attrElem.get("name");
                JSONPrimitive access = (JSONPrimitive) attrElem.get("access");
                String attrResponse = executeHttpGetRequest(mbeanName + "/" + a.getValue());
                parser = new JSONParser(attrResponse);
                docRoot = (JSONObject) parser.parse();
                JSONObject response = (JSONObject) docRoot.get("response");
                if (response != null) {
                    JSONElement get = response.get(a.getValue());
                    System.out.println("Attribute : " + a.getValue() + "(" + access.getValue() + ")" + " - " + get.toJsonString());
                } else {
                    System.out.println("Attribute : " + a.getValue() + "(" + access.getValue() + ")" + " - null");
                }
            }
        }

        String dumpHeap = "{\n"
                + "  \"name\": \"com.sun.management:type=HotSpotDiagnostic\",\n"
                + "  \"exec\": \"dumpHeap\",\n"
                + "  \"arguments\": [\n"
                + "			\"heapdump.hprof\",\n"
                + "			false\n"
                + "		]\n"
                + "}";

        String responseJson = executeHttpPostRequest(dumpHeap);
        parser = new JSONParser(responseJson);
        docRoot = (JSONObject) parser.parse();
        JSONElement response = docRoot.get("response");
        System.out.println(" DumpHeap op - " + (response != null ? response.toJsonString() : null));

        String getVmOption = "{\n"
                + "  \"name\": \"com.sun.management:type=HotSpotDiagnostic\",\n"
                + "  \"exec\": \"getVMOption\",\n"
                + "  \"arguments\": [\n"
                + "	\"PrintGCDetails\"\n"
                + "	]\n"
                + "}";
        responseJson = executeHttpPostRequest(getVmOption);
        parser = new JSONParser(responseJson);
        docRoot = (JSONObject) parser.parse();
        response = docRoot.get("response");
        System.out.println(" DumpHeap op - " + (response != null ? response.toJsonString() : null));
    }
}
