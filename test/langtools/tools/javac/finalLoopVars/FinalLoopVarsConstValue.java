/*
 * @test /nodynamiccopyright/
 * @bug 8338711
 * @summary Test final for() loop variable support doesn't permit illegal stuff
 * @compile/fail/ref=FinalLoopVarsConstValue.out -XDrawDiagnostics FinalLoopVarsConstValue.java
 * @enablePreview
 */

import java.util.function.*;

public class FinalLoopVarsConstValue {

    // Test: a final for() loop variable that's not mutated has a constant value
    {
        for (final int i = 0; i != 0; /* no change */)
            System.out.println("impossible");   // error: unreachable statement
        System.out.println("ok");
    }

    // Test: a final for() loop variable that's not mutated has a constant value
    {
        for (final int i = 0; i == 0; /* no change */)
            System.out.println("ok");
        System.out.println("impossible");       // error: unreachable statement
    }

    // Test: weird nesting doesn't confuse the analysis logic
    {
        for (final int i = 0, j = new IntSupplier() {
                                    public int getAsInt() {
                                        for (int i = 0; i < 3; i++)     // mutate a different "i" here
                                            System.out.println(i++);
                                        return 0;
                                    }
                                  }.getAsInt();
          i != 0;
          /* no change */)
            System.out.println("impossible");   // error: unreachable statement
    }
}
