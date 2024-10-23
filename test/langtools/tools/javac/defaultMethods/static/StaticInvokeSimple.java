/* @test /nodynamiccopyright/
 * @bug 8037385
 * @summary Must not allow static interface method invocation in legacy code
 * @compile -Xlint:-options StaticInvokeSimple.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 -Xlint:-options StaticInvokeSimple.java
 */
import java.util.stream.Stream;

class StaticInvokeSimple {
    void test() {
        Stream.empty();
    }
}
