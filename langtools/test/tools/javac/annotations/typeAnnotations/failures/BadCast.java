/*
 * @test /nodynamiccopyright/
 * @bug 8006775
 * @summary A cast cannot consist of only an annotation.
 * @author Werner Dietl
 * @compile/fail/ref=BadCast.out -XDrawDiagnostics BadCast.java
 */
class BadCast {
  static void main() {
    Object o = (@A) "";
  }
}

@interface A { }
