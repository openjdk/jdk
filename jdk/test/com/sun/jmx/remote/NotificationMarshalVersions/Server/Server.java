import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import java.util.Random;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class Server {
    public static String start() throws Exception {
        int serverPort = 12345;
        ObjectName name = new ObjectName("test", "foo", "bar");
        MBeanServer jmxServer = ManagementFactory.getPlatformMBeanServer();
        SteMBean bean = new Ste();
        jmxServer.registerMBean(bean, name);
        boolean exported = false;
        Random rnd = new Random(System.currentTimeMillis());
        do {
            try {
                LocateRegistry.createRegistry(serverPort);
                exported = true;
            } catch (ExportException ee) {
                if (ee.getCause() instanceof BindException) {
                    serverPort = rnd.nextInt(10000) + 4096;
                } else {
                    throw ee;
                }
            }

        } while (!exported);
        JMXServiceURL serverUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + serverPort + "/test");
        JMXConnectorServer jmxConnector = JMXConnectorServerFactory.newJMXConnectorServer(serverUrl, null, jmxServer);
        jmxConnector.start();

        return serverUrl.toString();
    }
}
