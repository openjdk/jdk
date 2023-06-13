/*
 * @test /nodynamiccopyright/
 * @bug 4869999 8193302
 * @summary Verify that the compiler does not prematurely decide a package is not observable.
 * @compile --release 8 ImportsObservable.java
 * @compile/fail/ref=ImportsObservable.out -XDrawDiagnostics ImportsObservable.java
 */

import javax.*;
import javax.swing.*;
public class ImportsObservable {
}
