/*
 * @test /nodynamiccopyright/
 * @bug 8206986
 * @summary Check types inferred for switch expressions.
 * @compile/fail/ref=ExpressionSwitchInfer.out -XDrawDiagnostics --enable-preview -source ${jdk.version} ExpressionSwitchInfer.java
 */

import java.util.ArrayList;
import java.util.List;

public class ExpressionSwitchInfer {

    private static final String NULL = "null";

    private <T> T test(List<T> l, Class<T> c, String param) {
        test(param == NULL ? new ArrayList<>() : new ArrayList<>(), CharSequence.class, param).charAt(0);
        test(param == NULL ? new ArrayList<>() : new ArrayList<>(), CharSequence.class, param).substring(0);

        test(switch (param) {
            case NULL -> new ArrayList<>();
            default -> new ArrayList<>();
        }, CharSequence.class, param).charAt(0);
        test(switch (param) {
            case NULL -> new ArrayList<>();
            default -> new ArrayList<>();
        }, CharSequence.class, param).substring(0);

        String str = switch (param) {
            case "" -> {
                break 0;
            } default ->"default";
        };

        return null;
    }

}
