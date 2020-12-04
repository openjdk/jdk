/*
 * @test /nodynamiccopyright/
 * @bug 8231622
 * @summary SuppressWarning("serial") ignored on field serialVersionUID. T8231622_3.out is intentionally blank.
 * @run compile/ref=T8231622_3.out -XDrawDiagnostics -Xlint:serial T8231622_3.java
 */

import java.io.Serializable;

class T8231622_3 implements Serializable {
    @SuppressWarnings("serial")
    public static final int serialVersionUID = 1;
}
