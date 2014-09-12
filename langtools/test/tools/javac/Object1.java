/*
 * @test /nodynamiccopyright/
 * @bug 4091755
 * @summary java.lang.Object can't be redefined without crashing javac
 * @author gafter
 *
 * @compile/fail/ref=Object1.out -XDrawDiagnostics  Object1.java
 */

package java.lang;
class Object extends Throwable {
    public final native Class getClass();
    public native int hashCode();
    public native boolean equals(Object obj);
    protected native Object clone() throws CloneNotSupportedException;
    public native String toString();
    public final native void notify();
    public final native void notifyAll();
    public final native void wait(long timeout) throws InterruptedException;
    public native final void wait(long timeout, int nanos) throws InterruptedException;
    public native final void wait() throws InterruptedException;
    protected void finalize() throws Throwable { }
}
