/*
 * @test /nodynamiccopyright/
 * @bug 6563143 8008436
 * @summary javac should issue a warning for overriding equals without hashCode
 * @summary javac should not issue a warning for overriding equals without hasCode
 * if hashCode has been overriden by a superclass
 * @compile/ref=OverridesEqualsButNotHashCodeTest.out -Xlint:overrides -XDrawDiagnostics OverridesEqualsButNotHashCodeTest.java
 */

public class OverridesEqualsButNotHashCodeTest {
    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}

class SubClass extends OverridesEqualsButNotHashCodeTest {
    @Override
    public boolean equals(Object o) {
        return o == this;
    }
}

@SuppressWarnings("overrides")
class NoWarning {
    @Override
    public boolean equals(Object o) {
        return o == this;
    }
}

class DoWarnMe {
    @Override
    public boolean equals(Object o) {
        return o == this;
    }
}
