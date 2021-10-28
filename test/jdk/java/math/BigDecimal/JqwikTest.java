import jdk.test.lib.artifacts.Artifact;

/**
 * @test
 * @run main JqwikTest
 */

@Artifact(organization = "net.jqwik", name = "jqwik", revision = "1.5.5")
public class JqwikTest {

    public static void main(String[] args) {
        System.out.println("tu");
    }

}
