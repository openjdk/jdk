/*
 * @test /nodynamiccopyright/
 * @bug 8231622
 * @summary SuppressWarning("serial") ignored on field serialVersionUID. T8231622_2.out is intentionally blank.
 * @run compile/ref=T8231622_2.out -XDrawDiagnostics -Xlint:serial T8231622_2.java
 */

import java.io.Serializable;

@SuppressWarnings("serial")
class T8231622_2 implements Serializable {
    public static final int serialVersionUID = 1;
}
