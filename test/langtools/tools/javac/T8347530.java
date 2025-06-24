/*
 * @test /nodynamiccopyright/
 * @bug 8347530
 * @summary Improve error message with invalid permits clauses
 * @compile/fail/ref=T8347530.out -XDrawDiagnostics T8347530.java
 */

class T8347530 {
    // sealed interfaces
    sealed interface A0 permits B0 {}
    class B0 {}     // class {0} must implement sealed interface

    sealed interface A1 permits B1 {}
    record B1() {}  // record {0} must implement sealed interface

    sealed interface A2 permits B2 {}
    enum B2 {}      // enum {0} must implement sealed interface

    sealed interface A3 permits B3 {}
    interface B3 {} // interface {0} must implement sealed interface

    // sealed classes
    sealed class C0 permits S0 {}
    class S0 {}     // class {0} must extend sealed class

    // record cannot extend other classes in general
    // enums cannot extend other classes in general
    // interfaces cannot extend other classes in general
}
