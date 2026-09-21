/*
 * @test /nodynamiccopyright/
 * @bug 8329951
 * @summary Check that "var" variable synthetic types have a source position
 * @compile/process/ref=VarWarnPosition.out -Xlint:deprecation -XDrawDiagnostics VarWarnPosition.java
 */

import java.util.*;
import java.util.function.*;

public class VarWarnPosition {

    VarWarnPosition() {

        // Test 1
        @SuppressWarnings("deprecation")
        List<Depr> deprecatedList = null;
        for (var deprValue : deprecatedList) { }

        // Test 2
        Consumer<Depr> c = d -> { };

        // Test 3
        Consumer<Depr> c2 = (var d) -> { };

        // Test 4
        Consumer<Depr> c3 = (final var d) -> { };

        // Test 5
        var d = deprecatedList.get(0);
    }
}

@Deprecated
class Depr {}
