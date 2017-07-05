import java.rmi.RemoteException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.NamingException;
import javax.naming.InitialContext;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

import org.omg.CORBA.Any;
import org.omg.CORBA.ORB;

public class HelloClient implements Runnable {
    static final int MAX_RETRY = 10;
    static final int ONE_SECOND = 1000;
    private static boolean responseReceived;

    public static void main(String args[]) throws Exception {
        executeRmiClientCall();
    }

    @Override
    public void run() {
        try {
            executeRmiClientCall();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static boolean isResponseReceived () {
        return responseReceived;
    }

    public static void executeRmiClientCall() throws Exception {
        Context ic;
        Object objref;
        HelloInterface helloSvc;
        String response;
        int retryCount = 0;

        Test test = new Test();
        System.out.println("HelloClient.main: enter ...");
        while (retryCount < MAX_RETRY) {
            try {
                ic = new InitialContext();
                System.out.println("HelloClient.main: HelloService lookup ...");
                // STEP 1: Get the Object reference from the Name Service
                // using JNDI call.
                objref = ic.lookup("HelloService");
                System.out.println("HelloClient: Obtained a ref. to Hello server.");

                // STEP 2: Narrow the object reference to the concrete type and
                // invoke the method.
                helloSvc = (HelloInterface) PortableRemoteObject.narrow(objref,
                    HelloInterface.class);
                System.out.println("HelloClient: Invoking on remote server with ConcurrentHashMap parameter");
                ConcurrentHashMap <String, String> testConcurrentHashMap = new ConcurrentHashMap<String, String>();
                response = helloSvc.sayHelloWithHashMap(testConcurrentHashMap);
                System.out.println("HelloClient: Server says:  " + response);
                if (!response.contains("Hello with hashMapSize ==")) {
                    System.out.println("HelloClient: expected response not received");
                    throw new RuntimeException("Expected Response Hello with hashMapSize == 0 not received");
                }
                responseReceived = true;
                break;
            } catch (NameNotFoundException nnfEx) {
                System.err.println("NameNotFoundException Caught  .... try again");
                retryCount++;
                try {
                    Thread.sleep(ONE_SECOND);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } catch (Exception e) {
                System.err.println("Exception " + e + "Caught");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        System.err.println("HelloClient terminating ");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
