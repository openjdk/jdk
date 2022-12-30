/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @compile/ref=ThisEscapeCtors.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeCtors.java
 * @summary Verify pruning of 'this' escape warnings for various constructors
 */

public class ThisEscapeCtors {

    // This constructor should NOT generate a warning because it would be a
    // duplicate of the warning already generated for ThisEscapeCtors(short).
    public ThisEscapeCtors(char x) {
        this((short)x);
    }

    // This constructor should generate a warning because it invokes leaky this()
    // and is accessible to subclasses.
    public ThisEscapeCtors(short x) {
        this();
    }

    // This constructor should generate a warning because it invokes leaky this()
    // and is accessible to subclasses.
    public ThisEscapeCtors(int x) {
        this();
    }

    // This constructor should NOT generate a warning because it is not accessbile
    // to subclasses. However, other constructors do invoke it, and that should cause
    // them to generate an indirect warning.
    private ThisEscapeCtors() {
        this.mightLeak();
    }

    public void mightLeak() {
    }
}
