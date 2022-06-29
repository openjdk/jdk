import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

public class JDK8283082{
    public static void main(String[] args) throws Exception {
        var c = new X509CertImpl();
        c.set("x509.info", new X509CertInfo());
        c.set("x509.info.issuer", new X500Name("CN=one"));
        c.delete("x509.info.issuer");
        c.set("x509.info.issuer", new X500Name("CN=two"));
    }
}
