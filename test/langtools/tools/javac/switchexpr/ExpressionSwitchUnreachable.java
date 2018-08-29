/*
 * @test /nodynamiccopyright/
 * @bug 8206986
 * @summary Verify reachability in switch expressions.
 * @compile/fail/ref=ExpressionSwitchUnreachable.out -XDrawDiagnostics --enable-preview -source 12 ExpressionSwitchUnreachable.java
 */

public class ExpressionSwitchUnreachable {

    public static void main(String[] args) {
        int z = 42;
        int i = switch (z) {
            case 0 -> {
                break 42;
                System.out.println("Unreachable");  //Unreachable
            }
            default -> 0;
        };
        i = switch (z) {
            case 0 -> {
                break 42;
                break 42; //Unreachable
            }
            default -> 0;
        };
        i = switch (z) {
            case 0:
                System.out.println("0");
                break 42;
                System.out.println("1");    //Unreachable
            default : break 42;
        };
        i = switch (z) {
            case 0 -> 42;
            default -> {
                break 42;
                System.out.println("Unreachable"); //Unreachable
            }
        };
        i = switch (z) {
            case 0: break 42;
            default:
                System.out.println("0");
                break 42;
                System.out.println("1");    //Unreachable
        };
        i = switch (z) {
            case 0:
            default:
                System.out.println("0");
                break 42;
                System.out.println("1");    //Unreachable
        };
    }


}
