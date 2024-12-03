public class Main {

    public static void main(String[] args) {
    int a, b, c, d, e, f, g, h, j2, k2;
    long startTime = System.nanoTime();
    for (int i = 0; i < 10; i++) {
        for (int k = 0; k < 10000; k++) {
            for (int j = 0; j < 10000; j++) {
                int x = 5;
                x++;
            }
        }
    }
        long endTime = System.nanoTime();
                double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;

                System.out.println("Czas wykonania : " + elapsedSeconds + " sekund");
    }
}