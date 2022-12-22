/**
 * @test /nodynamiccopyright/
 * @bug 8286895
 * @summary Verify that errors don't crash the compiler.
 * @compile/fail/ref=NoCrashForError.out -XDshould-stop.at=FLOW -XDdev -XDrawDiagnostics NoCrashForError.java
 */
public class NoCrashForError {
    private void JDK8286895() {
        Number n = 17;
        if (! n instanceof Integer i) {
            System.out.println("not Integer");
        }
    }
}
