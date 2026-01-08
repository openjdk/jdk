/*
 * @test /nodynamiccopyright/
 * @bug 8155591
 * @summary Verify cases where no AutoCloseable InterruptedException warning should be generated
 * @compile/ref=InterruptedExceptionTest2.out -XDrawDiagnostics -Xlint:try InterruptedExceptionTest2.java
 */

import java.util.function.Supplier;

// Here's an interface and a class that we can inherit declaring offending close() methods

interface HasClose {
    void close() throws Exception;
}

class WithConcreteClose {
    public void close() throws Exception { }
}

abstract class WithAbstractClose {
    public abstract void close() throws Exception;
}

// Interface declaration tests

interface InterruptedExceptionTest_I1 extends AutoCloseable {
}

interface InterruptedExceptionTest_I2 extends AutoCloseable {
    @Override void close();
}

interface InterruptedExceptionTest_I3 extends AutoCloseable {
    @Override void close() throws InterruptedException;                     // warning here
}

interface InterruptedExceptionTest_I4 extends AutoCloseable {
    @Override void close() throws Exception;                                // warning here
}

interface InterruptedExceptionTest_I5 extends AutoCloseable {
    @Override default void close() { }
}

interface InterruptedExceptionTest_I6 extends AutoCloseable {
    @Override default void close() throws InterruptedException { }          // warning here
}

interface InterruptedExceptionTest_I7 extends AutoCloseable {
    @Override default void close() throws Exception { }                     // warning here
}

interface InterruptedExceptionTest_I8 extends HasClose, AutoCloseable {
}

interface InterruptedExceptionTest_I9 extends HasClose, AutoCloseable {
    @Override void close();
}

// Abstract class declaration tests

abstract class InterruptedExceptionTest_C1 implements AutoCloseable {
}

abstract class InterruptedExceptionTest_C2 implements AutoCloseable {
    @Override public abstract void close();
}

abstract class InterruptedExceptionTest_C3 implements AutoCloseable {
    @Override public abstract void close() throws InterruptedException;     // warning here
}

abstract class InterruptedExceptionTest_C4 implements AutoCloseable {
    @Override public abstract void close() throws Exception;                // warning here
}

abstract class InterruptedExceptionTest_C5 implements AutoCloseable {
    @Override public void close() { }
}

abstract class InterruptedExceptionTest_C6 implements AutoCloseable {
    @Override public void close() throws InterruptedException { }           // warning here
}

abstract class InterruptedExceptionTest_C7 implements AutoCloseable {
    @Override public void close() throws Exception { }                      // warning here
}

abstract class InterruptedExceptionTest_C8 implements HasClose, AutoCloseable {
}

abstract class InterruptedExceptionTest_C9 implements HasClose, AutoCloseable {
    @Override public void close() { }
}

abstract class InterruptedExceptionTest_C10 extends WithConcreteClose implements AutoCloseable { // warning here
}

abstract class InterruptedExceptionTest_C11 extends WithAbstractClose implements AutoCloseable {
}

// Interface use site tests

class UseSite_I1 {
    void m(Supplier<InterruptedExceptionTest_I1> s) throws Exception {
        try (InterruptedExceptionTest_I1 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_I2 {
    void m(Supplier<InterruptedExceptionTest_I2> s) throws Exception {
        try (InterruptedExceptionTest_I2 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite_I3 {
    void m(Supplier<InterruptedExceptionTest_I3> s) throws Exception {
        try (InterruptedExceptionTest_I3 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_I4 {
    void m(Supplier<InterruptedExceptionTest_I4> s) throws Exception {
        try (InterruptedExceptionTest_I4 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_I5 {
    void m(Supplier<InterruptedExceptionTest_I5> s) throws Exception {
        try (InterruptedExceptionTest_I5 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite_I6 {
    void m(Supplier<InterruptedExceptionTest_I6> s) throws Exception {
        try (InterruptedExceptionTest_I6 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_I7 {
    void m(Supplier<InterruptedExceptionTest_I7> s) throws Exception {
        try (InterruptedExceptionTest_I7 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_I8 {
    void m(Supplier<InterruptedExceptionTest_I8> s) throws Exception {
        try (InterruptedExceptionTest_I8 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_I9 {
    void m(Supplier<InterruptedExceptionTest_I9> s) throws Exception {
        try (InterruptedExceptionTest_I9 t = s.get()) {
            t.hashCode();
        }
    }
}

// Abstract class use site tests

class UseSite_C1 {
    void m(Supplier<InterruptedExceptionTest_C1> s) throws Exception {
        try (InterruptedExceptionTest_C1 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_C2 {
    void m(Supplier<InterruptedExceptionTest_C2> s) throws Exception {
        try (InterruptedExceptionTest_C2 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite_C3 {
    void m(Supplier<InterruptedExceptionTest_C3> s) throws Exception {
        try (InterruptedExceptionTest_C3 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_C4 {
    void m(Supplier<InterruptedExceptionTest_C4> s) throws Exception {
        try (InterruptedExceptionTest_C4 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_C5 {
    void m(Supplier<InterruptedExceptionTest_C5> s) throws Exception {
        try (InterruptedExceptionTest_C5 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite_C6 {
    void m(Supplier<InterruptedExceptionTest_C6> s) throws Exception {
        try (InterruptedExceptionTest_C6 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_C7 {
    void m(Supplier<InterruptedExceptionTest_C7> s) throws Exception {
        try (InterruptedExceptionTest_C7 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_C8 {
    void m(Supplier<InterruptedExceptionTest_C8> s) throws Exception {
        try (InterruptedExceptionTest_C8 t = s.get()) {                     // warning here
            t.hashCode();
        }
    }
}

class UseSite_C9 {
    void m(Supplier<InterruptedExceptionTest_C9> s) throws Exception {
        try (InterruptedExceptionTest_C9 t = s.get()) {
            t.hashCode();
        }
    }
}

class UseSite_C10 {
    void m(Supplier<InterruptedExceptionTest_C10> s) throws Exception {
        try (InterruptedExceptionTest_C10 t = s.get()) {                    // warning here
            t.hashCode();
        }
    }
}
