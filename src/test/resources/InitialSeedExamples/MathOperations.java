package benchmark;

public class MathOperations {

    public double processValues(double a, double b) {
        // Инициализация переменных
        double result = 0.0;
        double temp1 = a * 2;
        double temp2 = b + 5;

        // Операции с числами
        result = temp1 + temp2;
        result = result * 1.5;

        if (result > 50.0) {
            result = result - 10;
        } else {
            result = result + 10;
        }

        temp1 = result / 3;
        temp2 = Math.sin(result);
        result = temp1 + temp2;

        return result;
    }

    public static void main(String[] args) {
        MathOperations ops = new MathOperations();

        // Тестовые вызовы
        double result1 = ops.processValues(10.0, 15.0);
        double result2 = ops.processValues(5.0, 8.0);

        System.out.println("Result 1: " + result1);
        System.out.println("Result 2: " + result2);
    }
}
