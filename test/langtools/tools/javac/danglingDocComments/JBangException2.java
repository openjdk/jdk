/** /usr/bin/env jbang "$0" "$@" ; exit $? */
// /nodynamiccopyright/

/** A class comment */
public class JBangException2 {

    /**
     * A method comment
     *
     * @param args a parameter comment
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Hello World!");
        } else {
            System.out.println("Hello " + args[0]);
        }
    }
}