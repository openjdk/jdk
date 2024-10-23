/* @test /nodynamiccopyright/
 * @compile HasBrokenConstantValue.jcod
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 HasBrokenConstantValue.jcod
 * @compile/fail/ref=BrokenConstantValue.out -XDrawDiagnostics BrokenConstantValue.java
 */
public class BrokenConstantValue {
     void t() {
         String s = HasBrokenConstantValue.VALUE;
     }
}
