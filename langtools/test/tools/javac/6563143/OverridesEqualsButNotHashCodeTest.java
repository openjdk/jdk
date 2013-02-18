/*
 * @test /nodynamiccopyright/
 * @bug 6563143
 * @summary javac should issue a warning for overriding equals without hashCode
 * @compile/ref=OverridesEqualsButNotHashCodeTest.out -Xlint:overrides -XDrawDiagnostics OverridesEqualsButNotHashCodeTest.java
 */

@SuppressWarnings("overrides")
public class OverridesEqualsButNotHashCodeTest {
    @Override
    public boolean equals(Object o) {
        return o == this;
    }
}

class Other {
    @Override
    public boolean equals(Object o) {
        return o == this;
    }
}

