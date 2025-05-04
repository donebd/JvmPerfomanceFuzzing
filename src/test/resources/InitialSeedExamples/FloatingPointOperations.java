package benchmark;

public class FloatingPointOperations {
    public double simpleCalculations(int iterations) {
        double result = 0.0;

        for (int i = 0; i < iterations; i++) {
            double value = i * 0.01;
            result += Math.sin(value) * Math.cos(value) / (1.0 + Math.exp(value * 0.1));
        }

        return result;
    }

    public double complexCalculations(int iterations) {
        double result = 0.0;

        for (int i = 0; i < iterations; i++) {
            double value = i * 0.01;
            result += Math.pow(Math.sin(value), 2) + Math.sqrt(Math.abs(Math.tan(value))) +
                    Math.log(1.0 + Math.abs(value)) / Math.log10(2.0 + value);
        }

        return result;
    }

    public static void main(String[] args) {
        FloatingPointOperations test = new FloatingPointOperations();
        double simpleResult = test.simpleCalculations(1000);
        double complexResult = test.complexCalculations(1000);

        System.out.println("Simple calculations: " + simpleResult);
        System.out.println("Complex calculations: " + complexResult);
    }
}
