/*
 * @test /nodynamiccopyright/
 * @bug 5059679
 * @summary Verify proper error reporting of extra semicolon before import statement
 * @compile/fail/ref=ExtraImportSemicolon.out1 -XDrawDiagnostics ExtraImportSemicolon.java
 * @compile/ref=ExtraImportSemicolon.out2 --release 20 -XDrawDiagnostics ExtraImportSemicolon.java
 */

import java.util.Map;;      // NOTE: extra semicolon
import java.util.Set;

class ExtraImportSemicolon {
}
