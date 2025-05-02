package sun.security.ssl;

import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import javax.net.ssl.SSLEngine;

public class SunX509KeyManagerImpl extends SunX509ConstraintsKeyManagerImpl {

    SunX509KeyManagerImpl(KeyStore ks, char[] password)
            throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException {
        super(ks, password);
    }

    @Override
    public AlgorithmConstraints getAlgorithmConstraints(Socket socket) {
        return null;
    }

    @Override
    public AlgorithmConstraints getAlgorithmConstraints(SSLEngine engine) {
        return null;
    }

    @Override
    public boolean conformsToAlgorithmConstraints(
            AlgorithmConstraints constraints, Certificate[] chain,
            String variant) {
        return true;
    }
}
