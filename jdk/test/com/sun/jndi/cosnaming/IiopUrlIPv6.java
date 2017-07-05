/* @test
 * @bug 5042453
 * @summary Ipv6 address throws Non-numeric port number error
 */

import com.sun.jndi.cosnaming.*;
import com.sun.jndi.cosnaming.IiopUrl.Address;
import java.util.*;
import java.net.MalformedURLException;

public class IiopUrlIPv6 {

    public static void main(String[] args) {

        String[] urls = {"iiop://[::1]:2809",
                        "iiop://[::1]",
                        "iiop://:2890",
                        "iiop://129.158.2.2:80"
                      };

        for (int u = 0; u < urls.length; u++) {
            try {
                IiopUrl url = new IiopUrl(urls[u]);
                Vector addrs = url.getAddresses();

                for (int i = 0; i < addrs.size(); i++) {
                    Address addr = (Address)addrs.elementAt(i);
                    System.out.println("================");
                    System.out.println("url: " + urls[u]);
                    System.out.println("host: " + addr.host);
                    System.out.println("port: " + addr.port);
                    System.out.println("version: " + addr.major
                                + " " + addr.minor);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }
}
