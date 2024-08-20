/*
 * @test
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.provider
 */
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.NamedKeyPairGenerator;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

// java --add-exports java.base/sun.security.provider=ALL-UNNAMED --add-exports java.base/sun.security.x509=ALL-UNNAMED NamedKeyFactoryTest.java
public class NamedKeyFactoryTest {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new ProviderImpl());

        var k = new NamedX509Key("sHa", "shA-256", new byte[2]);
        var kf = KeyFactory.getInstance("ShA");

        v(k.getEncoded());
        v(k.getRawBytes());
        System.out.println(k.getAlgorithm());
        System.out.println(k.getFormat());
        System.out.println(k.getParams().getName());
        var spec = kf.getKeySpec(k, X509EncodedKeySpec.class);
        v(spec.getEncoded());
        v(kf.generatePublic(spec).getEncoded());

        var kf2 = KeyFactory.getInstance("Sha256");
        var pk = new PublicKey() {
            @Override
            public String getAlgorithm() {
                return "SHA";
            }

            @Override
            public String getFormat() {
                return "RAW";
            }

            @Override
            public byte[] getEncoded() {
                return new byte[2];
            }
        };
        v(kf2.translateKey(pk).getEncoded());

        System.out.println("KPG");
        var kpg = KeyPairGenerator.getInstance("SHA");
        try {
            kpg.generateKeyPair();
            throw new RuntimeException();
        } catch (IllegalStateException e) {
            // good
        }
        kpg.initialize(new NamedParameterSpec("SHA256"));
        v(kpg.generateKeyPair().getPublic().getEncoded());
        kpg.initialize(new NamedParameterSpec("SHA512"));
        v(kpg.generateKeyPair().getPublic().getEncoded());

        var kpg1 = KeyPairGenerator.getInstance("SHA256");
        v(kpg1.generateKeyPair().getPublic().getEncoded());

        var kpg2 = KeyPairGenerator.getInstance("SHA512");
        v(kpg2.generateKeyPair().getPublic().getEncoded());
    }

    static void v(byte[] bb) {
        System.out.println(HexFormat.ofDelimiter(":").formatHex(bb));
    }

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("P", "1", "...");
            put("KeyFactory.SHA", KF.class.getName());
            put("KeyFactory.SHA256", KF1.class.getName());
            put("KeyFactory.SHA512", KF2.class.getName());
            put("KeyPairGenerator.SHA", KPG.class.getName());
            put("KeyPairGenerator.SHA256", KPG1.class.getName());
            put("KeyPairGenerator.SHA512", KPG2.class.getName());
        }
    }
    public static class KF extends NamedKeyFactory {
        public KF() {
            super("SHA", null);
        }
    }
    public static class KF1 extends NamedKeyFactory {
        public KF1() {
            super("SHA", "SHA256");
        }
    }
    public static class KF2 extends NamedKeyFactory {
        public KF2() {
            super("SHA", "SHA512");
        }
    }
    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            this(null);
        }

        public KPG(String pname) {
            super("SHA", pname);
        }

        @Override
        public byte[][] generateKeyPair0(String name, SecureRandom sr) {
            var out = new byte[2][];
            out[0] = new byte[name.endsWith("256") ? 2 : 4];
            out[1] = new byte[name.endsWith("256") ? 2 : 4];
            return out;
        }
    }
    public static class KPG1 extends KPG {
        public KPG1() {
            super("SHA256");
        }
    }
    public static class KPG2 extends KPG {
        public KPG2() {
            super("SHA512");
        }
    }
}
