/*
 * @test /nodynamiccopyright/
 * @bug 7152104
 * @summary Make sure no warning is emitted for anonymous classes
 *          without serialVersionUID
 * @compile SerialWarn.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 SerialWarn.java
 * @compile -Werror -XDrawDiagnostics -Xlint:serial SerialWarnAnon.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 -Werror -XDrawDiagnostics -Xlint:serial SerialWarnAnon.java
 */

class SerialWarnAnon {
    interface SerialWarnAnonInterface extends java.io.Serializable { }
    Object m() {
        return new SerialWarnAnonInterface() { };
    }
}
