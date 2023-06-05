import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.security.KeyStore;

public class EncodingMutability {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "SunMSCAPI");
        PublicKey publicKey = keyGen.generateKeyPair().getPublic();
        byte initialByte = publicKey.getEncoded()[0];
        publicKey.getEncoded()[0] = 0;
        byte mutatedByte = publicKey.getEncoded()[0];

        System.out.println(initialByte + " " + mutatedByte);

        if (initialByte != mutatedByte) {
            System.out.println("Was able to mutate first byte of pubkey from " + initialByte + "to " + mutatedByte);
            throw new RuntimeException("Pubkey was mutated via getEncoded");
        }
    }
}