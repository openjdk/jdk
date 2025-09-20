/*
 * @test /nodynamiccopyright/
 * @bug 8155591
 * @summary Verify cases where no AutoCloseable InterruptedException warning should be generated
 * @compile/ref=InterruptedExceptionTest2.out -XDrawDiagnostics -Xlint:try InterruptedExceptionTest2.java
 */

import java.util.function.Supplier;

// Declaration tests

interface InterruptedExceptionTest2 extends AutoCloseable {
}

interface InterruptedExceptionTest3 extends AutoCloseable {
    @Override void close();
}

interface InterruptedExceptionTest4 extends AutoCloseable {
    @Override default void close() { }
}

abstract class InterruptedExceptionTest5 implements AutoCloseable {
}

abstract class InterruptedExceptionTest6 implements AutoCloseable {
    @Override public abstract void close();
}

abstract class InterruptedExceptionTest7 implements AutoCloseable {
    @Override public void close() { }
}

// Use site tests

class UseSite2 {
    void m(Supplier<InterruptedExceptionTest2> s) throws Exception {
        try (InterruptedExceptionTest2 t = s.get()) {   // warning here
            t.hashCode();
        }
    }
}

class UseSite3 {
    void m(Supplier<InterruptedExceptionTest3> s) throws Exception {
        try (InterruptedExceptionTest3 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite4 {
    void m(Supplier<InterruptedExceptionTest4> s) throws Exception {
        try (InterruptedExceptionTest4 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite5 {
    void m(Supplier<InterruptedExceptionTest5> s) throws Exception {
        try (InterruptedExceptionTest5 t = s.get()) {   // warning here
            t.hashCode();
        }
    }
}

class UseSite6 {
    void m(Supplier<InterruptedExceptionTest6> s) throws Exception {
        try (InterruptedExceptionTest6 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite7 {
    void m(Supplier<InterruptedExceptionTest7> s) throws Exception {
        try (InterruptedExceptionTest7 t = s.get()) {
            t.hashCode();
        }
    }
}
