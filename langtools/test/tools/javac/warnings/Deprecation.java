/**
 * @test  /nodynamiccopyright/
 * @bug 4986256
 * @compile/ref=Deprecation.noLint.out                             -XDrawDiagnostics Deprecation.java
 * @compile/ref=Deprecation.lintDeprecation.out -Xlint:deprecation -XDrawDiagnostics Deprecation.java
 * @compile/ref=Deprecation.lintAll.out         -Xlint:all,-path   -XDrawDiagnostics Deprecation.java
 */

@Deprecated
class Deprecation
{
}

// control: this class should generate warnings
class Deprecation2
{
    void m() {
        Object d = new Deprecation();
    }
}

// tests: the warnings that would otherwise be generated should all be suppressed
@SuppressWarnings("deprecation")
class Deprecation3
{
    void m() {
        Object d = new Deprecation();
    }
}

class Deprecation4
{
    @SuppressWarnings("deprecation")
    void m() {
        Object d = new Deprecation();
    }
}

class Deprecation5
{
    void m() {
        @SuppressWarnings("deprecation")
            class Inner {
                void m() {
                    Object d = new Deprecation();
                }
            }
    }
}

// this class should produce warnings because @SuppressWarnings should not be inherited
class Deprecation6 extends Deprecation3
{
    void m() {
        Object d = new Deprecation();
    }
}
