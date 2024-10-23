/*
 * @test
 * @bug 8168854
 * @summary javac erroneously reject a service interface inner class in a provides clause
 * @compile module-info.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 module-info.java
 */
module mod {
    exports pack1;
    provides pack1.Outer.Inter with pack1.Outer1.Implem;
}
