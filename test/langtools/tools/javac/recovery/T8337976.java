/**
 * @test /nodynamiccopyright/
 * @bug 8337976
 * @summary Verify javac does not crash and produces nice errors for certain erroneous code.
 * @compile/fail/ref=T8337976.out -XDrawDiagnostics -XDshould-stop.at=FLOW -XDdev T8337976.java
 */
public class T8337976 {
    switch (0) { default: undefined u;}
    if (true) { undefined u; }
}
