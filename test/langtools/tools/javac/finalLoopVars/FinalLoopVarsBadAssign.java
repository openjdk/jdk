/*
 * @test /nodynamiccopyright/
 * @bug 8338711
 * @summary Test final for() loop variable support doesn't permit illegal stuff
 * @compile/fail/ref=FinalLoopVarsBadAssign.out -XDrawDiagnostics FinalLoopVarsBadAssign.java
 * @enablePreview
 */

import java.util.function.*;

public class FinalLoopVarsBadAssign {

    // Test: final for() loop variable may not be mutated in the init block
    {
        for (final int i = 0, j = ++i;              // error: cannot assign a value to final variable i
          i < 3;
          i++)
            System.out.println(i);
    }

    // Test: final for() loop variable may not be mutated in the condition
    {
        for (final int i = 0; ++i < 3; i++)         // error: cannot assign a value to final variable i
            System.out.println(i);
    }

    // Test: final for() loop variable may not be mutated in the body
    {
        for (final int i = 0; i < 3; i++)
            i *= 2;                                 // error: cannot assign a value to final variable i
    }
}
