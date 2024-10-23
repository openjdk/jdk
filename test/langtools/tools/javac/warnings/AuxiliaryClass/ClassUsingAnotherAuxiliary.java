/**
 * @test /nodynamiccopyright/
 * @bug 7153951
 * @compile ClassUsingAnotherAuxiliary.java NotAClassName.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 ClassUsingAnotherAuxiliary.java NotAClassName.java
 * @compile -Xlint:auxiliaryclass ClassUsingAnotherAuxiliary.java NotAClassName.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 -Xlint:auxiliaryclass ClassUsingAnotherAuxiliary.java NotAClassName.java
 * @compile/fail/ref=ClassUsingAnotherAuxiliary.out -XDrawDiagnostics -Werror -Xlint:auxiliaryclass ClassUsingAnotherAuxiliary.java NotAClassName.java
 */

class ClassUsingAnotherAuxiliary {
    AnAuxiliaryClass ahem;
}

