/*
 * @test /nodynamiccopyright/
 * @bug 8338711
 * @summary Check for invalid cases relating to for() loop variables being effectively final in the body
 * @compile/fail/ref=ForLoopVarsEffectivelyFinalInvalidTest.out -XDrawDiagnostics ForLoopVarsEffectivelyFinalInvalidTest.java
 */

import java.util.function.*;

public class ForLoopVarsEffectivelyFinalInvalidTest {

    // If the variable is mutated in the BODY, it loses its effectively final status in the BODY
    {
        for (int i = 0; i < 3; ) {
            i++;
            ((IntSupplier)() -> f(i)).getAsInt();                       // error: ...must be final or effectively final
        }
    }

    // If the variable is mutated in the INIT, it loses its effectively final status in the INIT
    {
        for (int i = 0, j = i++ + ((IntSupplier)() -> f(i)).getAsInt(); // error: ...must be final or effectively final
            i < 3; ) {
            ((IntSupplier)() -> f(i)).getAsInt();                       // ok
        }
    }

    // If the variable is mutated in the CONDITION, it loses its effectively final status in the CONDITION
    {
        for (int i = 0;
          i++ + ((IntSupplier)() -> f(i)).getAsInt() < 3; ) {           // error: ...must be final or effectively final
            ((IntSupplier)() -> f(i)).getAsInt();                       // ok
        }
    }

    // If the variable is mutated in the STEP, it loses its effectively final status in the STEP
    {
        for (int i = 0;
          i < 3;
          i += ((IntSupplier)() -> f(i)).getAsInt()) {                  // error: ...must be final or effectively final
            ((IntSupplier)() -> f(i)).getAsInt();                       // ok
        }
    }

    // An extra level of nesting doesn't make it OK
    {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                ((IntSupplier)() -> f(i)).getAsInt();                   // error: ...must be final or effectively final
            }
            i++;
        }
    }

    // If the variable is never initialized, it can't be captured
    {
        for (int i; "".hashCode() == 0; ) {
            ((IntSupplier)() -> f(i)).getAsInt();                       // error: variable i might not have been initialized
        }
    }

    // An extra level of nesting doesn't make it OK
    {
        for (int i = 0; true; ) {
            for (int j = 0; j < 3; j++) {
                i = 42;
                Runnable r = () -> System.out.println(i);               // error: ...must be final or effectively final
            }
            break;
        }
    }

    // An extra level of nesting doesn't make it OK
    {
        for (int i = 0; true; ) {
            for (int j = 0; j < ++i; j++) {
                Runnable r = () -> System.out.println(i);               // error: ...must be final or effectively final
            }
            break;
        }
    }

    int f(int i) {
        return i * i;
    }
}
