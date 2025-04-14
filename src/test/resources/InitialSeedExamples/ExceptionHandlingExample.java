package benchmark;

public class ExceptionHandlingExample {

    public double processWithExceptions(double[] values) {
        double sum = 0.0;

        for (int i = 0; i <= values.length; i++) {
            try {
                sum += 100.0 / values[i];
            } catch (ArrayIndexOutOfBoundsException e) {
                sum -= 5.0;
            } catch (ArithmeticException e) {
                sum = Double.NaN;
            }
        }

        return sum;
    }

    public static void main(String[] args) {
        ExceptionHandlingExample ex = new ExceptionHandlingExample();

        double[] input1 = {2.0, 0.0, 4.0};
        double[] input2 = {1.5, 3.0};

        double result1 = ex.processWithExceptions(input1);
        double result2 = ex.processWithExceptions(input2);

        System.out.println("Result 1: " + result1);
        System.out.println("Result 2: " + result2);
    }
}
