/*
 * @test /nodynamiccopyright/
 * @bug 4500240
 * @summary javac throws StackOverflowError for recursive inheritance
 *
 * @compile ClassCycle2a.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 ClassCycle2a.java
 * @compile/fail/ref=ClassCycle2a.out -XDrawDiagnostics  ClassCycle2b.java
 */

class ClassCycle2b {}
class ClassCycle2a extends ClassCycle2b {}
