/*
 * @test /nodynamiccopyright/
 * @bug 8259072
 * @summary access flags inside local class are allowed for source levels previous to 16
 * @compile/fail/ref=T8259072.out -XDrawDiagnostics -source 15 T8259072.java
 */

// Add all three variants + public, protected, or private
public class T8259072 {
    private void PriA1() {
        class Foo {
            private class Test {}
        }
    }

    class PriA2 {
        public PriA2 check() {
            return new PriA2() { private class STRENGTH{}; };
        }
    }

    class PriA3 {
        Object o = new Object() { private class STRENGTH{}; };
    }

    private void PubA1() {
        class Foo {
            public class Test {}
        }
    }

    class PubA2 {
        public PubA2 check() {
            return new PubA2() { public class STRENGTH{}; };
        }
    }

    class PubA3 {
        Object o = new Object() { public class STRENGTH{}; };
    }

    private void ProA1() {
        class Foo {
            protected class Test {}
        }
    }

    class ProA2 {
        public ProA2 check() {
            return new ProA2() { protected class STRENGTH{}; };
        }
    }

    class ProA3 {
        Object o = new Object() { protected class STRENGTH{}; };
    }
}