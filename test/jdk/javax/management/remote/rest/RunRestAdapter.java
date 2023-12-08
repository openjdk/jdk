
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import javax.management.remote.rest.PlatformRestAdapter;


/**
 * @test
 * @modules java.logging
 *          java.management.rest
 * @run main RunRestAdapter
 */
public class RunRestAdapter {

    private static String sslAgentConfig;
    private static String sslClientConfig;
    private static String configFile;

    public static void main(String[] args) throws Exception {
        RunRestAdapter rr = new RunRestAdapter();
        rr.run();
    }

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
        props.setProperty("com.sun.management.jmxremote.authenticate", "false");
        props.setProperty("com.sun.management.jmxremote.rest.port", "8686");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void setupConfig() throws Exception {
        String testSrcRoot = System.getProperty("test.src") + File.separator;
        sslAgentConfig = testSrcRoot + "sslConfigAgent";
        sslClientConfig = testSrcRoot + "sslConfigClient";

        configFile = testSrcRoot + "mgmt1.properties";
        createAgentSslConfigFile(sslAgentConfig);
        createClientSslConfigFile(sslClientConfig);
        setupMgmtConfig(configFile);
    }

    public void run() throws Exception {
        setupConfig();
        File file = new File(configFile);
        Properties props = new Properties();
        props.load(new FileInputStream(file));
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }
        PlatformRestAdapter.getInstance().start();
        Thread.sleep(1000000);
    }
}
