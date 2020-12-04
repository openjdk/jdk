/*
 * @test /nodynamiccopyright/
 * @bug 8231622
 * @summary SuppressWarning("serial") ignored on field serialVersionUID.
 * @run compile/ref=T8231622_1.out -XDrawDiagnostics -Xlint:serial T8231622_1.java
 */

import java.io.Serializable;

class T8231622_1 implements Serializable {
    public static final int serialVersionUID = 1;
}
