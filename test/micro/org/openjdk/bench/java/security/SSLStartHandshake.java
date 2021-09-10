package org.openjdk.bench.java.security;

import org.openjdk.jmh.annotations.*;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(jvmArgsAppend = {"--add-exports", "java.base/sun.security.util=ALL-UNNAMED"})
public class SSLStartHandshake {



    @State(Scope.Benchmark)
    public static class SSLStartHandshakeState {
        public int port;
        SSLSocketFactory socketFactory;

        public enum Cert {

            CA_ECDSA_SECP256R1(
                    "EC",
                    // SHA256withECDSA, curve secp256r1
                    // Validity
                    //     Not Before: May 22 07:18:16 2018 GMT
                    //     Not After : May 17 07:18:16 2038 GMT
                    // Subject Key Identifier:
                    //     60:CF:BD:73:FF:FA:1A:30:D2:A4:EC:D3:49:71:46:EF:1A:35:A0:86
                    "-----BEGIN CERTIFICATE-----\n" +
                            "MIIBvjCCAWOgAwIBAgIJAIvFG6GbTroCMAoGCCqGSM49BAMCMDsxCzAJBgNVBAYT\n" +
                            "AlVTMQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
                            "ZTAeFw0xODA1MjIwNzE4MTZaFw0zODA1MTcwNzE4MTZaMDsxCzAJBgNVBAYTAlVT\n" +
                            "MQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZjZTBZ\n" +
                            "MBMGByqGSM49AgEGCCqGSM49AwEHA0IABBz1WeVb6gM2mh85z3QlvaB/l11b5h0v\n" +
                            "LIzmkC3DKlVukZT+ltH2Eq1oEkpXuf7QmbM0ibrUgtjsWH3mULfmcWmjUDBOMB0G\n" +
                            "A1UdDgQWBBRgz71z//oaMNKk7NNJcUbvGjWghjAfBgNVHSMEGDAWgBRgz71z//oa\n" +
                            "MNKk7NNJcUbvGjWghjAMBgNVHRMEBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQCG\n" +
                            "6wluh1r2/T6L31mZXRKf9JxeSf9pIzoLj+8xQeUChQIhAJ09wAi1kV8yePLh2FD9\n" +
                            "2YEHlSQUAbwwqCDEVB5KxaqP\n" +
                            "-----END CERTIFICATE-----",
                    "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg/HcHdoLJCdq3haVd\n" +
                            "XZTSKP00YzM3xX97l98vGL/RI1KhRANCAAQc9VnlW+oDNpofOc90Jb2gf5ddW+Yd\n" +
                            "LyyM5pAtwypVbpGU/pbR9hKtaBJKV7n+0JmzNIm61ILY7Fh95lC35nFp"),
            EE_ECDSA_SECP256R1(
                "EC",
                        // SHA256withECDSA, curve secp256r1
                        // Validity
                        //     Not Before: May 22 07:18:16 2018 GMT
                        //     Not After : May 17 07:18:16 2038 GMT
                        // Authority Key Identifier:
                        //     60:CF:BD:73:FF:FA:1A:30:D2:A4:EC:D3:49:71:46:EF:1A:35:A0:86
                        "-----BEGIN CERTIFICATE-----\n" +
                        "MIIBqjCCAVCgAwIBAgIJAPLY8qZjgNRAMAoGCCqGSM49BAMCMDsxCzAJBgNVBAYT\n" +
                        "AlVTMQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
                        "ZTAeFw0xODA1MjIwNzE4MTZaFw0zODA1MTcwNzE4MTZaMFUxCzAJBgNVBAYTAlVT\n" +
                        "MQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZjZTEY\n" +
                        "MBYGA1UEAwwPUmVncmVzc2lvbiBUZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD\n" +
                        "QgAEb+9n05qfXnfHUb0xtQJNS4JeSi6IjOfW5NqchvKnfJey9VkJzR7QHLuOESdf\n" +
                        "xlR7q8YIWgih3iWLGfB+wxHiOqMjMCEwHwYDVR0jBBgwFoAUYM+9c//6GjDSpOzT\n" +
                        "SXFG7xo1oIYwCgYIKoZIzj0EAwIDSAAwRQIgWpRegWXMheiD3qFdd8kMdrkLxRbq\n" +
                        "1zj8nQMEwFTUjjQCIQDRIrAjZX+YXHN9b0SoWWLPUq0HmiFIi8RwMnO//wJIGQ==\n" +
                        "-----END CERTIFICATE-----",
                        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgn5K03bpTLjEtFQRa\n" +
                        "JUtx22gtmGEvvSUSQdimhGthdtihRANCAARv72fTmp9ed8dRvTG1Ak1Lgl5KLoiM\n" +
                        "59bk2pyG8qd8l7L1WQnNHtAcu44RJ1/GVHurxghaCKHeJYsZ8H7DEeI6");
            final String keyAlgo;
            final String certStr;
            final String privKeyStr;

            Cert(String keyAlgo, String certStr, String privKeyStr) {
                this.keyAlgo = keyAlgo;
                this.certStr = certStr;
                this.privKeyStr = privKeyStr;
            }
        }

        /*
         * The parameters used to configure SSLContext.
         */
        protected static final class ContextParameters {
            final String contextProtocol;
            final String tmAlgorithm;
            final String kmAlgorithm;

            ContextParameters(String contextProtocol,
                              String tmAlgorithm, String kmAlgorithm) {

                this.contextProtocol = contextProtocol;
                this.tmAlgorithm = tmAlgorithm;
                this.kmAlgorithm = kmAlgorithm;
            }
        }

        /*
         * =======================================
         * Certificates and keys used in the test.
         */
        // Trusted certificates.
        protected final static Cert[] TRUSTED_CERTS = {
                Cert.CA_ECDSA_SECP256R1 };

        // End entity certificate.
        protected final static Cert[] END_ENTITY_CERTS = {
                Cert.EE_ECDSA_SECP256R1 };

        /*
         * Create an instance of SSLContext with the specified trust/key materials.
         */
        public static SSLContext    createSSLContext(
                Cert[] trustedCerts,
                Cert[] endEntityCerts,
                ContextParameters params) throws Exception {

            KeyStore ts = null;     // trust store
            KeyStore ks = null;     // key store
            char passphrase[] = "passphrase".toCharArray();

            // Generate certificate from cert string.
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            // Import the trused certs.
            ByteArrayInputStream is;
            if (trustedCerts != null && trustedCerts.length != 0) {
                ts = KeyStore.getInstance("JKS");
                ts.load(null, null);

                Certificate[] trustedCert = new Certificate[trustedCerts.length];
                for (int i = 0; i < trustedCerts.length; i++) {
                    is = new ByteArrayInputStream(trustedCerts[i].certStr.getBytes());
                    try {
                        trustedCert[i] = cf.generateCertificate(is);
                    } finally {
                        is.close();
                    }

                    ts.setCertificateEntry(
                            "trusted-cert-" + trustedCerts[i].name(), trustedCert[i]);
                }
            }

            // Import the key materials.
            if (endEntityCerts != null && endEntityCerts.length != 0) {
                ks = KeyStore.getInstance("JKS");
                ks.load(null, null);

                for (int i = 0; i < endEntityCerts.length; i++) {
                    // generate the private key.
                    PKCS8EncodedKeySpec priKeySpec = new PKCS8EncodedKeySpec(
                            Base64.getMimeDecoder().decode(endEntityCerts[i].privKeyStr));
                    KeyFactory kf =
                            KeyFactory.getInstance(
                                    endEntityCerts[i].keyAlgo);
                    PrivateKey priKey = kf.generatePrivate(priKeySpec);

                    // generate certificate chain
                    is = new ByteArrayInputStream(
                            endEntityCerts[i].certStr.getBytes());
                    Certificate keyCert = null;
                    try {
                        keyCert = cf.generateCertificate(is);
                    } finally {
                        is.close();
                    }

                    Certificate[] chain = new Certificate[] { keyCert };

                    // import the key entry.
                    ks.setKeyEntry("cert-" + endEntityCerts[i].name(),
                            priKey, passphrase, chain);
                }
            }

            // Create an SSLContext object.
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(params.tmAlgorithm);
            tmf.init(ts);

            SSLContext context = SSLContext.getInstance(params.contextProtocol);
            if (endEntityCerts != null && endEntityCerts.length != 0 && ks != null) {
                KeyManagerFactory kmf =
                        KeyManagerFactory.getInstance(params.kmAlgorithm);
                kmf.init(ks, passphrase);

                context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            } else {
                context.init(null, tmf.getTrustManagers(), null);
            }

            return context;
        }

        @Setup
        public void startServer() throws Exception {
            SSLContext context = createSSLContext(TRUSTED_CERTS, END_ENTITY_CERTS,
                    new ContextParameters("TLS", "PKIX", "NewSunX509"));
            SSLServerSocketFactory ssf = context.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(0);
            serverSocket.setEnabledProtocols(new String[] { "TLSv1.3" });
            serverSocket.setEnabledCipherSuites(new String[] {"TLS_AES_256_GCM_SHA384"});
            this.socketFactory = context.getSocketFactory();
            this.port = serverSocket.getLocalPort();
            new ServerThread(serverSocket).start();
        }
        public class ServerProcessor extends Thread {
            SSLSocket socket;

            ServerProcessor(SSLSocket socket) {
                this.socket = socket;
            }

            public void run() {
                try {
                    socket.startHandshake();
                } catch (IOException e) {
                    throw new RuntimeException("Exception handshaking request", e);
                }
            }
        }

        public class ServerThread extends Thread {
            SSLServerSocket socket;

            ServerThread(SSLServerSocket socket) {
                this.socket = socket;
            }

            public void run() {
                try {
                    while(true) {
                        new ServerProcessor((SSLSocket) this.socket.accept()).start();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Exception when accepting connection", e);
                }
            }
        }

        public SSLSocket getSSLSocket() throws IOException {
            return (SSLSocket) socketFactory.createSocket("localhost", this.port);
        }
    }

    @Benchmark
    public void handshakeBenchmark(SSLStartHandshakeState state) throws IOException {
        SSLSocket socket = state.getSSLSocket();
        socket.startHandshake();
    }


}