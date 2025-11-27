import java.net.InetSocketAddress;
import java.net.Socket;

import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.net.IPSupport;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 *        jdk.test.lib.net.IPSupport
 * @run main/othervm ${test.main.class}
 */
public class Foo {

    public static void main(final String[] args) throws Exception {
        IPSupport.printPlatformSupport(System.out);
        NetworkConfiguration.printSystemConfiguration(System.out);
        final String host = "example.com";
        final int port = 1234;
        final int connTimeoutMillis = 40000;
        try (final Socket socket = new Socket()) {
            final InetSocketAddress dest = new InetSocketAddress(host, port);
            System.out.println("socket " + socket + " attempting to connect to " + dest);
            socket.connect(dest, connTimeoutMillis);
            System.out.println("connected successfully: " + socket);
        }
    }
}