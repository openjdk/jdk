/**
 * @test    /nodynamiccopyright/
 * @bug     8025537
 * @author  sogoel
 * @summary enum keyword used as an identifier
 * @compile/ref=EnumAsIdentifier4.out -XDrawDiagnostics -source 1.4 EnumAsIdentifier.java
 * @compile/fail/ref=EnumAsIdentifier5.out -XDrawDiagnostics -source 1.5 EnumAsIdentifier.java
 * @compile/fail/ref=EnumAsIdentifier.out -XDrawDiagnostics EnumAsIdentifier.java
 */

public class EnumAsIdentifier {

    int enum = 0;

}

