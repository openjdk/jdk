
import jdk.test.lib.Asserts;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.*;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @test
 * @bug 8346094
 * @library /test/lib
 * @modules java.base/sun.security.x509
 * java.base/sun.security.util
 */
public class CertificateExtensions {

    public static void main(String[] args) throws Exception {
        X509CertImpl x509Certimpl = createCertificate();

        /**
         * Certificate is created without extensions. Invoking getExtensionValue with oid must return NULL
         * else it is incorrect
         */
        try {
            Asserts.assertNull(x509Certimpl.getExtensionValue("2.5.29.17"));
        } catch (Exception e) {
            throw new RuntimeException("expected NULL with the certificate extensions as NULL, however it is not null.");
        }

        /**
         * Certificate is created with extensions. Invoking getExtensionValue with oid must not return NULL
         * else it is incorrect
         */
        try {
            System.out.println("------ cert x509Certimpl ------ " + x509Certimpl);
            System.out.println("------ cert info ------ " + x509Certimpl.getInfo());
            x509Certimpl.getInfo().setExtensions(createCertificateExtensions(x509Certimpl.getInfo().getKey().getKey()));
            System.out.println("-- ceetificate extensions --" + x509Certimpl.getInfo().getExtensions());
            Asserts.assertNotNull(x509Certimpl.getExtensionValue("2.5.29.17"));
        } catch (Exception e) {
            throw new RuntimeException("expected not NULL with the certificate extensions, however it is null.");
        }

        /**
         * Certificate is created with extensions. Invoking getExtensionValue with invalid oid must return NULL
         * else it is incorrect
         */
        try {
            x509Certimpl.getInfo().setExtensions(createCertificateExtensions(x509Certimpl.getInfo().getKey().getKey()));
            Asserts.assertNull(x509Certimpl.getExtensionValue("1.2.3.4"));
        } catch (Exception e) {
            throw new RuntimeException("expected not NULL with the certificate extensions, however it is null.");
        }


        /**
         * Certificate is created without extensions. Invoking getKeyUsage must return NULL
         * else it is incorrect
         */
        try {
            x509Certimpl.getInfo().setExtensions(null);
            Asserts.assertNull(x509Certimpl.getKeyUsage());
        } catch (Exception e) {
            throw new RuntimeException("expected NULL with the certificate extensions as NULL, however it is not null.");
        }

        /**
         * Certificate is created with extensions. Invoking getKeyUsage must not return NULL
         * else it is incorrect
         */
        try {
            x509Certimpl.getInfo().setExtensions(createCertificateExtensions(x509Certimpl.getInfo().getKey().getKey()));
            Asserts.assertNotNull(x509Certimpl.getKeyUsage());
        } catch (Exception e) {
            throw new RuntimeException("expected not NULL with the certificate extensions, however it is null.");
        }
    }

    private static X509CertImpl createCertificate() throws Exception {
        X509CertImpl x509Certimpl = null;
        try {
            X509CertInfo x509CertInfo = new X509CertInfo();
            x509CertInfo.setVersion(new CertificateVersion(CertificateVersion.V3));

            // Generate Key Pair (RSA)
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();
            x509CertInfo.setKey(new CertificateX509Key(publicKey));

            // Create and set the DN name for Subject and Issuer.
            X500Name subject = new X500Name("CN=www.Subject.com, O=MyOrg, OU=LocalBiz, L=XYZ, S=YY, C=XX");
            X500Name issuer = new X500Name("CN=www.Issuer.com, O=Oracle,OU=Java,L=XYZ,S=YY, C=XX");
            x509CertInfo.setIssuer(issuer);
            x509CertInfo.setSubject(subject);

            //create and set the subject and issuer unique identity
            byte[] issuerId = {1, 2, 3, 4, 5};
            byte[] subjectId = {6, 7, 8, 9, 10};
            x509CertInfo.setSubjectUniqueId(new UniqueIdentity(subjectId));
            x509CertInfo.setIssuerUniqueId(new UniqueIdentity(issuerId));

            // create and set the serial number
            BigInteger serialNumber = BigInteger.valueOf(new SecureRandom().nextInt(Integer.MAX_VALUE));
            x509CertInfo.setSerialNumber(new CertificateSerialNumber(serialNumber));

            // create and set the validity interval
            Date notBefore = new Date(); // Valid from now
            Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000); // Valid for 1 year
            x509CertInfo.setValidity(new CertificateValidity(notBefore, notAfter));

            // Create Certificate Info which is the representation of X509 Certificate.
            x509CertInfo.setAlgorithmId(new CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")));

            // Sign the certificate
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(x509CertInfo.getEncodedInfo());
            byte[] signedData = signature.sign();
            byte[] signedCert = {};

            x509Certimpl = new X509CertImpl(x509CertInfo, AlgorithmId.get("SHA256withRSA"), signedData, signedCert);
        } catch (Exception e) {
            System.out.println("caught exception while creating the certificate : " + e.getMessage());
            throw e;
        }
        return x509Certimpl;
    }

    public static sun.security.x509.CertificateExtensions createCertificateExtensions(PublicKey publicKey) throws IOException, NoSuchAlgorithmException {
        // Create Extensions
        sun.security.x509.CertificateExtensions certificateExtensions = new sun.security.x509.CertificateExtensions();

        GeneralNameInterface mailInf = new RFC822Name("test@Oracle.com");
        GeneralName mail = new GeneralName(mailInf);
        GeneralNameInterface dnsInf = new DNSName("Oracle.com");
        GeneralName dns = new GeneralName(dnsInf);
        GeneralNameInterface uriInf = new URIName("http://www.Oracle.com");
        GeneralName uri = new GeneralName(uriInf);

        // localhost
        byte[] address = new byte[]{127, 0, 0, 1};

        GeneralNameInterface ipInf = new IPAddressName(address);
        GeneralName ip = new GeneralName(ipInf);

        GeneralNameInterface oidInf = new OIDName(ObjectIdentifier.of("1.2.3.4"));
        GeneralName oid = new GeneralName(oidInf);


        GeneralNames subjectNames = new GeneralNames();
        subjectNames.add(mail);
        subjectNames.add(dns);
        subjectNames.add(uri);
        SubjectAlternativeNameExtension subjectName = new SubjectAlternativeNameExtension(subjectNames);

        GeneralNames issuerNames = new GeneralNames();
        issuerNames.add(ip);
        issuerNames.add(oid);
        IssuerAlternativeNameExtension issuerName = new IssuerAlternativeNameExtension(issuerNames);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
        cal.set(2014, 03, 10, 12, 30, 30);
        cal.set(2000, 11, 15, 12, 30, 30);
        Date lastDate = cal.getTime();
        Date firstDate = new Date();
        PrivateKeyUsageExtension pkusage = new PrivateKeyUsageExtension(firstDate, lastDate);

        KeyUsageExtension usage = new KeyUsageExtension();
        usage.set(KeyUsageExtension.CRL_SIGN, true);
        usage.set(KeyUsageExtension.DIGITAL_SIGNATURE, true);
        usage.set(KeyUsageExtension.NON_REPUDIATION, true);
        MessageDigest md = MessageDigest.getInstance("SHA");

        byte[] keyId = md.digest(publicKey.getEncoded());
        KeyIdentifier kid = new KeyIdentifier(keyId);
        SerialNumber sn = new SerialNumber(42);
        AuthorityKeyIdentifierExtension aki = new AuthorityKeyIdentifierExtension(kid, subjectNames, sn);

        SubjectKeyIdentifierExtension ski = new SubjectKeyIdentifierExtension(keyId);

        BasicConstraintsExtension cons = new BasicConstraintsExtension(true, 10);

        PolicyConstraintsExtension pce = new PolicyConstraintsExtension(2, 4);

        certificateExtensions.setExtension(SubjectAlternativeNameExtension.NAME, subjectName);
        certificateExtensions.setExtension(IssuerAlternativeNameExtension.NAME, issuerName);
        certificateExtensions.setExtension(PrivateKeyUsageExtension.NAME, pkusage);
        certificateExtensions.setExtension(KeyUsageExtension.NAME, usage);
        certificateExtensions.setExtension(AuthorityKeyIdentifierExtension.NAME, aki);
        certificateExtensions.setExtension(SubjectKeyIdentifierExtension.NAME, ski);
        certificateExtensions.setExtension(BasicConstraintsExtension.NAME, cons);
        certificateExtensions.setExtension(PolicyConstraintsExtension.NAME, pce);
        return certificateExtensions;
    }
}
