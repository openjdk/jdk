
import jdk.jfr.Event;
import jdk.jfr.Description;
import jdk.jfr.Label;
import java.io.OutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public class NetworkJFRTestHelper {

    @Label("Event Test Class")
    @Description("A sample JFR Event Class")
    private static class TestEvent extends Event {
        @Label("Event Message")
        String _message;

        TestEvent(String message) {
            _message = message;
        }

        public static void commitOrComplain(String msg) throws RuntimeException {
            TestEvent event = new TestEvent("New TestEvent: " + msg);

            if (!event.isEnabled() || !event.shouldCommit()) {
                throw new RuntimeException("shouldCommit returns false. Is JFR running?");
            }

            event.commit();
        }
    }

    private static class Fac {
        static BigInteger compute(int n) {
            if (n <= 1) {
                return BigInteger.ONE;
            }

            return compute(n-1).multiply(new BigInteger(Integer.toString(n)));
        }
    }

    public static void main(String[] args) throws Exception {
        TestEvent.commitOrComplain("Beginning network test");
        int PORT = 8080;
        String HOST = "localhost";
        int TIMEOUT = 1000;// ms
        int MIN_N = 100;
        int MAX_N = 1000;

        Thread tClient = new Thread(() -> {
                try {
                    Socket client = new Socket(HOST, PORT);

                    String start_msg = "Sending numbers";
                    TestEvent.commitOrComplain(start_msg);

                    for ( int n = MIN_N; n <= MAX_N; n++) {
                        String msg = "Fac(" + n + ") = " + Fac.compute(n).intValue() + " (low-order bits only) ";
                        OutputStream out = client.getOutputStream();
                        out.write(msg.getBytes());
                    }

                    String end_msg = "Sending complete.";
                    TestEvent.commitOrComplain(end_msg);

                    client.close();
                } catch (Exception e) {
                    Runtime.getRuntime().exit(-1);
                }
        });

        System.out.println("Starting server...");
        ServerSocket serv = new ServerSocket(PORT);
        serv.setSoTimeout(TIMEOUT);

        System.out.println("Starting client...");
        tClient.start();

        Socket recv = serv.accept();
        String msg = new String(recv.getInputStream().readAllBytes());
        TestEvent.commitOrComplain(msg);
    }
}
