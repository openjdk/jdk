/*
 * @test /nodynamiccopyright/
 * @bug 6843077 8006775
 * @summary test that only Java 8 allows type annotations
 * @author Mahmood Ali
 * @compile AnnotationVersion.java
 */
import java.lang.annotation.*;

class myNumber<T extends @A Number> { }

@Target(ElementType.TYPE_USE)
@interface A { }
