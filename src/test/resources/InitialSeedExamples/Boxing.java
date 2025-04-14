package benchmark;

import java.util.Random;

public class Boxing {
    public static void main(String[] args) {
        // Использование случайных значений
        Random random = new Random();

        double value = 3.14 + random.nextDouble();
        double a = value + random.nextDouble();
        double b = a + random.nextDouble();
        // Недетерминированное ветвление
        if (value > 3.15) {
            System.out.println("Path 1: " + value);
            System.out.println(a);

            double result1 = Math.pow(value, 2);
            System.out.println("Result 1: " + result1);
        } else if (value < 2) {
            System.out.println("Path 2: " + value);
            System.out.println(b);

            double average = value / 3;
            System.out.println("Average: " + average);
        } else {
            System.out.println("AHAHAH");
        }
    }
}