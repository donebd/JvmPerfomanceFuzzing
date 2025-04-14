package benchmark;

import java.util.Random;

public class Loop {
    public static void main(String[] args) {
        // Использование случайных значений
        Random random = new Random();

        int a = 3;
        // Использование случайных значений
        System.out.println("AHAHAH");
        while (true) {
            a += 1;
            double value = a + random.nextDouble();
            if (value == 0) {
                System.out.println("GG");
            }
        }
    }
}