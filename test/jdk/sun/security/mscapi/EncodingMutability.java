/*
 * @test
 * @bug 8308808
 * @requires os.family == "windows"
 * @modules jdk.crypto.mscapi/sun.security.mscapi
 * @run main EncodingMutability
 */

import java.security.*;

public class EncodingMutability {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "SunMSCAPI");
        PublicKey publicKey = keyGen.generateKeyPair().getPublic();
        byte initialByte = publicKey.getEncoded()[0];
        publicKey.getEncoded()[0] = 0;
        byte mutatedByte = publicKey.getEncoded()[0];

        if (initialByte != mutatedByte) {
            System.out.println("Was able to mutate first byte of pubkey from " + initialByte + " to " + mutatedByte);
            throw new RuntimeException("Pubkey was mutated via getEncoded");
        }
    }
}