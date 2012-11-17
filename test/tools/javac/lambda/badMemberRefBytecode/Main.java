import java.util.Collections;

public class Main {

    public static void main(String[] args) {
        Collections.<String>sort(null, String::compareTo);
    }

}
