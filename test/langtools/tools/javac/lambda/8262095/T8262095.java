/*
 * @test /nodynamiccopyright/
 * @bug 8262095
 * @summary Report diagnostics produced by incorrect lambdas
 * @compile/fail/ref=T8262095.out -XDrawDiagnostics T8262095.java
 */

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

class T8262095 {

    void f(Stream<Entry<Long, List<String>>> stream) {
        stream.sorted(Entry.comparingByKey()
                           .thenComparing((Map.Entry<Long, List<String>> e) -> e.getValue().hashCode()))
              .count();
    }
}
