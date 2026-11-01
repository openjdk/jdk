/*
 * @test /nodynamiccopyright/
 * @bug 8382368
 * @compile/ref=DeprecatedNoEffect.out -Xlint:deprecation -XDrawDiagnostics DeprecatedNoEffect.java
 */
public class DeprecatedNoEffect {
  void m1() {
    @Deprecated int i1;    // there should be a "has no effect" warning here
  }
  @Deprecated
  void m2() {
    @Deprecated int i2;    // there should be a "has no effect" warning here also
  }
}
