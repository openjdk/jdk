/*
 * @test /nodynamiccopyright/
 * @summary Negative test for illegal modifier combinations, subclassing, and external instantiation of singleobject classes
 * @compile/fail/ref=SingleObjectNegTest.out -XDrawDiagnostics SingleObjectNegTest.java
 */

public class SingleObjectNegTest {

    singleobject class ValidSingleton {
    }

    // Error 1: Cannot combine abstract with singleobject
    abstract singleobject class AbstractSingleObject {
    }

    // Error 2: Cannot subclass an implicitly final singleobject class
    static class SubClass extends ValidSingleton {
    }

    // Error 3: Cannot declare a public constructor in a singleobject class
    singleobject class PublicCtorSingleObject {
        public PublicCtorSingleObject() {}
    }

    void testInstantiate() {
        // Error 4: Cannot instantiate a singleobject class via 'new' from outside
        ValidSingleton v = new ValidSingleton();
    }
}
