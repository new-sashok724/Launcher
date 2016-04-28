package launcher;

import java.util.Arrays;

public final class Kek {
    private Kek() {
    }

    public static void main(String... args) throws Throwable {
        char[][] sine = new char[21][250];
        for (char[] aSine : sine) {
            Arrays.fill(aSine, ' ');
        }

        for (int x = 0; x < 250; x++) {
            double pi = StrictMath.PI * 2.0D / 250.0D * x;
            sine[(int) ((StrictMath.cos(pi) + 1.0D) / 2.0D * 20)][x] = '.';
        }

        for (char[] aSine : sine) {
            System.out.println(aSine);
        }
    }
}
