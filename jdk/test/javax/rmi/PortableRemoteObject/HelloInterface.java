import java.net.InetAddress;
import java.rmi.Remote;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public interface HelloInterface extends Remote {
   public String sayHello( String from ) throws java.rmi.RemoteException;
   public String sayHelloToTest( Test test ) throws java.rmi.RemoteException;
   public String sayHelloWithInetAddress( InetAddress ipAddr ) throws java.rmi.RemoteException;
   public String sayHelloWithHashMap(ConcurrentHashMap<String, String> hashMap ) throws java.rmi.RemoteException;
   public String sayHelloWithHashMap2(HashMap<String, String> hashMap ) throws java.rmi.RemoteException;
   public String sayHelloWithReentrantLock(ReentrantLock lock ) throws java.rmi.RemoteException;
}
