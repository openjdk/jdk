/*
 * @test  /nodynamiccopyright/
 * @bug 6911256 6964740 6965277
 * @author Maurizio Cimadamore
 * @summary Check that resources of an intersection type forces union of exception types
 *          to be caught outside twr block
 * @compile/fail/ref=TwrIntersection02.out -XDrawDiagnostics TwrIntersection02.java
 */

class TwrIntersection02 {

    static class Exception1 extends Exception {}
    static class Exception2 extends Exception {}


    interface MyResource1 extends AutoCloseable {
       void close() throws Exception1;
    }

    interface MyResource2 extends AutoCloseable {
       void close() throws Exception2;
    }

    public void test1() throws Exception1 {
        try(getX()) {
            //do something
        }
    }

    public void test2() throws Exception2 {
        try(getX()) {
            //do something
        }
    }

    <X extends MyResource1 & MyResource2> X getX() { return null; }
}
