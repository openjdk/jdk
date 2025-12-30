import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;
import java.util.HexFormat;

/*
 * @test
 * @summary Validates correctness of the X-Wing key encodings.
 * @modules java.base/com.sun.crypto.provider
 * @run junit KeyEncodings
 */
public class KeyEncodings {

    private static final HexFormat HEX = HexFormat.of();

    // test vectors from https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-08#appendix-D
    private static final byte[] SEED = HEX.parseHex("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
    private static final byte[] EXPECTED_PRIVATE_KEY_ENCODING = Base64.getDecoder().decode("""
            MDQCAQAwDQYLKwYBBAGD5i2ByHoEIAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZ
            GhscHR4f
            """.replaceAll("\\s+", ""));
    private static final byte[] EXPECTED_PUBLIC_KEY_ENCODING = Base64.getDecoder().decode("""
            MIIE1DANBgsrBgEEAYPmLYHIegOCBMEAb1QJigoOZBFGYUtpYLpg2GA9YvRH+atJ
            m0e9aQbMQLBh2GNKPoiQbyhJWOdEHKbHJcu5cJW3ZxpGK2aByeZYC7yNYLFJ+mAm
            EEOvu6UvIFpgKDhIUVlq3zcavqmNM0c4PSu2c0OPZ4NhK/hwFPe5Gol0AmU0XfZ5
            NARz0cTBdohuXim48Fi7fHNTFmhs/1w764wmHLAJcKacGvzFS5TLhuHOY7pjbjlc
            pFEB4hx70EwxPqGa8kFB79KtREFqJbpPZZEO99iAnDCT8EqvAOPNluNcSqPIAsGK
            1vOdpLS42YyL15Atg6B7pFOWZ0pgJDyrk+gP2bHId3N2qcwNb6EV4mOTgLnGvnhI
            vRNYjGRwOgU10ZoPgWM6l2oKEFtm7ihdD9JV6CwDMZJfQ4O278dh72CZI1oLmHJj
            WKqdAbi4llGfkhR0u3wUuyIlK1wvENQSRsmyPnZEhJNn9UGhX2O8koo5u3vHPwe2
            ZcSWu2VYyPRUiacuxLrNNOnFlMM4cbcj8DSV6ItDkasm5DBD3rYRezkZ5FxMGxar
            KOR93XI2Y4VHZhkvwYBspwq7eGy9swky5oyKNwvPsHmDoBLDJmuT76YmV/S4ODdM
            sLuV4OwGVBsHZdmc8VO8a5YTXKeApVs2R3ieMZFeRig8+ce7boRT+2aCEFFB8dwN
            ANhe7XA7bGyWH3nIRSdrQkiUnAZ4LlE+spkbldlgQuOMvto1JEmytQhOvaUiamIG
            QAeJEwowlkSYSLYp/upKLCp0PEoN3Jyz89Z2/FY3MbJsShpm3IRZFwBW1XaX8UQ7
            gamjRBK7e/BfMydXWlkR3TAdYFOGfzwwgHEfG/EVh7C7KYQnayaF53ViEOSz+JVT
            hCMeVYxvUQyR4PxWtdGIX/KUnpWka8G+4fpx9QJ+EMRDsOkdD9dED0Z6JyISEuiP
            XGumQpbK4NIHv8YPiMfPtcRaoYOdGMs3xFhD5UJqSpDIArZCj5U8NZxKwGA0UvrA
            tzYeL9NdzIhakhRdT8oBWPG31wtLzRGOSipBVEON8xDESpobmepBWQcmeoiwYkJB
            V5wXIvRu1hwuPspUXJlwUXF1OZuADbJdo5WT0GSQ1xQsAOiNLbBH6YmL23rLftkH
            9uMEFswN5UokLAohJjAvXVTIW8Zqwvg8eXlFtQZ8qkK9LgwZypdQblB6sKXJ9WM3
            CEmcGfJK7FE705A6XXO27EmR98cuuZHBw3iJgFyx6jigzAIXayfFjWOM5aMmaEV8
            +bm+AnygIUBXlxcl1UEC6JlnFusq2CNFO2BbhVNwsbIbOTLN7UFgqplzx+uuWsR2
            TZTPfMlQbwd7rXMBLbtKyBQKOHRkEuszyVFFliBfcHY1hiIX2bYJGMYmjZNEkVuE
            eiR2waJw8VSlyEI0FlrPyGk5hwLOqemgfnsOmeqb3LeEH+nA+iXIM4CSVho+3dxw
            AfR4rWV4GmAkqtFl2baXmtrESKRGL1ZGhVJ/diQ0/ppCWoRDe0VzkuyoDJE1BhUe
            OhMjnzQvynZVtuquhFoiHOs+Z/VjnGGT9v3u9X45m4CLfzqitXQKre2QFj3F13XJ
            +vfx+9B12rNE6dfRRmRygfu6ezxWyv1YM7epMOxCBufDptd2T+gdeg==
            """.replaceAll("\\s+", ""));

    @Test
    public void testPrivateKeyEncoding() throws GeneralSecurityException {
        var kpg = KeyPairGenerator.getInstance("X-Wing", "SunJCE");
        kpg.initialize(NamedParameterSpec.X_WING, new DerandomizedRandom());
        var keyPair = kpg.generateKeyPair();
        var encoded = keyPair.getPrivate().getEncoded();

        Assertions.assertArrayEquals(EXPECTED_PRIVATE_KEY_ENCODING, encoded, "PKCS#8 private key encoding does not match expected value: " + Base64.getEncoder().encodeToString(encoded));
    }

    @Test
    public void testPublicKeyEncoding() throws GeneralSecurityException {
        var kpg = KeyPairGenerator.getInstance("X-Wing", "SunJCE");
        kpg.initialize(NamedParameterSpec.X_WING, new DerandomizedRandom());
        var keyPair = kpg.generateKeyPair();
        var encoded = keyPair.getPublic().getEncoded();

        Assertions.assertArrayEquals(EXPECTED_PUBLIC_KEY_ENCODING, encoded, "X.509 public key encoding does not match expected value: " + Base64.getEncoder().encodeToString(encoded));
    }

    // Mock used to inject a derandomized seed into the KPG
    private static class DerandomizedRandom extends SecureRandom {

        @Override
        public void nextBytes(byte[] bytes) {
            System.arraycopy(SEED, 0, bytes, 0, SEED.length);
        }
    }

}
