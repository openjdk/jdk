
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Client {
    public static void main(String[] argv) throws Exception {
        if (argv.length != 1) throw new IllegalArgumentException("Expecting exactly one jmx url argument");

        JMXServiceURL serverUrl = new JMXServiceURL(argv[0]);

        ObjectName name = new ObjectName("test", "foo", "bar");
        JMXConnector jmxConnector = JMXConnectorFactory.connect(serverUrl);
        System.out.println("client connected");
        jmxConnector.addConnectionNotificationListener(new NotificationListener() {
            public void handleNotification(Notification notification, Object handback) {
                System.err.println("no!" + notification);
            }
        }, null, null);
        MBeanServerConnection jmxServer = jmxConnector.getMBeanServerConnection();

        jmxServer.addNotificationListener(name, new NotificationListener() {
            public void handleNotification(Notification notification, Object handback) {
                System.out.println("client got:" + notification);
            }
        }, null, null);

        for(int i=0;i<10;i++) {
            System.out.println("client invoking foo");
            jmxServer.invoke(name, "foo", new Object[]{}, new String[]{});
            Thread.sleep(50);
        }

        System.err.println("happy!");
    }
}
