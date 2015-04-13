import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.rmi.PortableRemoteObject;

public class HelloImpl extends PortableRemoteObject implements HelloInterface {
    public HelloImpl() throws java.rmi.RemoteException {
        super(); // invoke rmi linking and remote object initialization
    }

    public String sayHello(String from) throws java.rmi.RemoteException {
        System.out.println("Hello from " + from + "!!");
        System.out.flush();
        String reply = "Hello from us to you " + from;
        return reply;
    }

    @Override
    public String sayHelloToTest(Test test) throws RemoteException {
        return "Test says Hello";
       }

    @Override
    public String sayHelloWithInetAddress(InetAddress ipAddr)
            throws RemoteException {
        String response = "Hello with InetAddress " + ipAddr.toString();
        return response;
    }

    @Override
    public String sayHelloWithHashMap(ConcurrentHashMap<String, String> receivedHashMap)
            throws RemoteException {
        int hashMapSize = 0;

        hashMapSize = receivedHashMap.size();
        String response = "Hello with hashMapSize == " + hashMapSize;
        return response;
    }

    @Override
    public String sayHelloWithHashMap2(HashMap<String, String> receivedHashMap)
            throws RemoteException {
        int hashMapSize = 0;

        hashMapSize = receivedHashMap.size();
        String response = "Hello with hashMapSize == " + hashMapSize;
        return response;
    }

    @Override
    public String sayHelloWithReentrantLock(ReentrantLock receivedLock)
            throws RemoteException {

        String response = "Hello with lock == " + receivedLock.isLocked();
        return response;
    }
}
