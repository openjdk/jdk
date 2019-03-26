/**
 * @test /nodynamiccopyright/
 * @bug 8214031
 * @summary Verify that definite assignment when true works (illegal code)
 * @compile/fail/ref=DefiniteAssignment2.out --enable-preview --source ${jdk.version} -XDrawDiagnostics DefiniteAssignment2.java
 */
public class DefiniteAssignment2 {

    public static void main(String[] args) {
        int a = 0;
        boolean b = true;
        boolean t;

        {
            int x;

            t = (b && switch(a) {
                case 0: break (x = 1) == 1 || true;
                default: break false;
            }) || x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break (x = 1) == 1;
                default: break false;
            }) || x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: x = 1; break true;
                case 1: break (x = 1) == 1;
                default: break false;
            }) || x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break true;
                case 1: break (x = 1) == 1;
                default: break false;
            }) && x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break false;
                case 1: break isTrue() || (x = 1) == 1;
                default: break false;
            }) && x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break false;
                case 1: break isTrue() ? true : (x = 1) == 1;
                default: break false;
            }) && x == 1;
        }

        {
            final int x;

            t = (switch(a) {
                case 0: break false;
                case 1: break isTrue() ? true : (x = 1) == 1;
                default: break false;
            }) && (x = 1) == 1;
        }
    }

    private static boolean isTrue() {
        return true;
    }

}
