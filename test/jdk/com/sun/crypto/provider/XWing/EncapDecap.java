import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.security.pkcs.NamedPKCS8Key;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.util.Arrays;

/*
 * @test
 * @summary Test encapsulation and decapsulation of X-Wing keys
 * @modules java.base/sun.security.pkcs
 * @run junit EncapDecap
 */
public class EncapDecap {

    @RepeatedTest(1000)
    public void testEncapDecap() throws GeneralSecurityException {
        var keyPair = KeyPairGenerator.getInstance("X-Wing", "SunJCE").generateKeyPair();
        var kem = KEM.getInstance("X-Wing", "SunJCE");

        var enc = kem.newEncapsulator(keyPair.getPublic()).encapsulate();
        var dec = kem.newDecapsulator(keyPair.getPrivate()).decapsulate(enc.encapsulation());

        Assertions.assertArrayEquals(enc.key().getEncoded(), dec.getEncoded(), "Decapsulated key does not match encapsulated key");
    }

    @Test
    public void testNewEncapsulationsOnEachInvocation() throws GeneralSecurityException {
        var keyPair = KeyPairGenerator.getInstance("X-Wing", "SunJCE").generateKeyPair();
        var kem = KEM.getInstance("X-Wing", "SunJCE");

        var enc1 = kem.newEncapsulator(keyPair.getPublic()).encapsulate();
        var enc2 = kem.newEncapsulator(keyPair.getPublic()).encapsulate();

        Assertions.assertFalse(Arrays.equals(enc1.encapsulation(), enc2.encapsulation()), "Encapsulated message should differ");
        Assertions.assertFalse(Arrays.equals(enc1.key().getEncoded(), enc2.key().getEncoded()), "Encapsulated keys should differ");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1119, 1121}) // correct size is 1120 bytes
    public void decapsulateWithInvalidMessage(int invalidEncapsulationSize) throws GeneralSecurityException {
        var keyPair = KeyPairGenerator.getInstance("X-Wing", "SunJCE").generateKeyPair();
        var kem = KEM.getInstance("X-Wing", "SunJCE");

        var decapsulator = kem.newDecapsulator(keyPair.getPrivate());

        Assertions.assertThrows(DecapsulateException.class, () -> {
            decapsulator.decapsulate(new byte[invalidEncapsulationSize]);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2431, 2433}) // correct size is 2400 + 32 bytes
    public void newDecapsulatorWithInvalidKey(int invalidKeySize) throws GeneralSecurityException {
        var kem = KEM.getInstance("X-Wing", "SunJCE");

        var privateKey = NamedPKCS8Key.internalCreate("X-Wing", "X-Wing", new byte[invalidKeySize], null);

        Assertions.assertThrows(InvalidKeyException.class, () -> {
            kem.newDecapsulator(privateKey);
        });
    }

}
