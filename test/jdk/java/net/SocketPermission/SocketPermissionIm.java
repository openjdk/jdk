/*
 * @test
 * @bug 8243376
 * @summary SocketPermission implies(Permission p) spec allows, If the object was initialized with a single IP address and one of p's IP addresses is equal to this object's IP addr
 * @run main/othervm -Djdk.net.hosts.file=${test.src}/Host.txt
 *      -Dsun.net.inetaddr.ttl=0 SocketPermissionIm
 */

import java.net.SocketPermission;
import java.net.InetAddress;
import java.io.*;
import java.io.File;

public class SocketPermissionIm {
    public static void main(String[] args) throws Exception {
        String hostname = "www.exmp.com";

        int testPass = 0;
        SocketPermission sp = new SocketPermission(hostname, "connect,resolve");

        do {
              if (!sp.implies(new SocketPermission(hostname, "connect,resolve"))) {
                       System.out.println("Expected true, returned false");
                       break;
                  }
                  addIpToHostsFile(hostname, "1.2.3."+testPass, hostsFileName);
                  Thread.sleep(1000);
                  testPass++;
        } while (testPass <= 2);
    }

    private  static void addIpToHostsFile(String host, String addr, String hostsFileName)
                                                                      throws Exception {
       String mapping = addr + " " + host;
       RandomAccessFile f = new RandomAccessFile(new File(hostsFileName), "rw");
       f.seek(0);
       f.write(mapping.getBytes());
       f.close();
     }
}
