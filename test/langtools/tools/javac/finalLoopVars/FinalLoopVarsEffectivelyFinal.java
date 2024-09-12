/*
 * @test /nodynamiccopyright/
 * @bug 8338711
 * @summary Test final for() loop variable support doesn't permit illegal stuff
 * @compile/fail/ref=FinalLoopVarsEffectivelyFinal.out -XDrawDiagnostics FinalLoopVarsEffectivelyFinal.java
 * @enablePreview
 */

import java.util.function.*;

public class FinalLoopVarsEffectivelyFinal {

    // Test: non-final for() loop variable modified in body is not effectively final
    {
        for (int i = 0; i < 3; i++) {
            i *= 2;
            ((Runnable)() -> System.out.println(i)).run();      // error: ...must be final or effectively final
        }
    }
}
