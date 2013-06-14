/*
 * @test
 * @bug 1240831
 * @summary Certain classes should have been reported as abstract, but
 *          the compiler failed to detect this.  This comes up when a
 *          subclass declares a method with the same name as an
 *          unimplemented, unaccessible method in a superclass.  Even though
 *          the method has the same name, it does not override.
 * @author turnidge
 *
 * @compile/fail one/Parent.java two/Child.java
 */
