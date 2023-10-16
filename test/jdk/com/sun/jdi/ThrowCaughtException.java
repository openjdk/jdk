/* /nodynamiccopyright/ */ public class ThrowCaughtException {  // hard coded linenumbers - DO NOT CHANGE
    public static void main(String args[]) throws Exception {
        try {
            System.out.println("Start");
            throw new Ex();
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

class Ex extends RuntimeException {
}