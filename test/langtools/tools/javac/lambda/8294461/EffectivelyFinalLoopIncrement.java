/*
 * @test /nodynamiccopyright/
 * @summary Verify for() loop variable not effectively final even if loop never increments
 * @bug 8294461
 * @compile/fail/ref=EffectivelyFinalLoopIncrement.out -XDrawDiagnostics EffectivelyFinalLoopIncrement.java
 */
class EffectivelyFinalLoopIncrement {
    EffectivelyFinalLoopIncrement() {
        for (int i = 0; i < 10; i++) {
            Runnable r = () -> System.out.println(i);   // variable i is NOT effectively final
            break;                                      // even though "i++" is never reached
        }
    }
}
