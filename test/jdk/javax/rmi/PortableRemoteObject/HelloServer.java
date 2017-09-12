import javax.naming.InitialContext;
import javax.naming.Context;

public class HelloServer {

    static final int MAX_RETRY = 10;
    static final int ONE_SECOND = 1000;

    public static void main(String[] args) {
        int retryCount = 0;
        while (retryCount < MAX_RETRY) {
            try {
                //HelloServer.set("SETTING TEST ITL");
                // Step 1: Instantiate the Hello servant
                HelloImpl helloRef = new HelloImpl();

                // Step 2: Publish the reference in the Naming Service
                // using JNDI API
                Context initialNamingContext = new InitialContext();
                initialNamingContext.rebind("HelloService", helloRef);

                System.out.println("Hello Server: Ready...");
                break;
            } catch (Exception e) {
                System.out.println("Server initialization problem: " + e);
                e.printStackTrace();
                retryCount++;
                try {
                    Thread.sleep(ONE_SECOND);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
}
