package benchmark;

public class ConditionalExpressionTest {
    public int evaluateMultipleConditions(int[] values) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0 && values[i] % 2 == 0 && values[i] < 100 && (values[i] % 3 == 0 || values[i] % 5 == 0)) {
                count++;
            }
        }
        return count;
    }

    public int evaluateNestedConditions(int[] values) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0) {
                if (values[i] % 2 == 0) {
                    if (values[i] < 100) {
                        if (values[i] % 3 == 0 || values[i] % 5 == 0) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public int evaluateWithTernary(int[] values) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            count += (values[i] > 0 && values[i] % 2 == 0 && values[i] < 100 &&
                    (values[i] % 3 == 0 || values[i] % 5 == 0)) ? 1 : 0;
        }
        return count;
    }

    public static void main(String[] args) {
        ConditionalExpressionTest test = new ConditionalExpressionTest();
        int[] values = new int[1000];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }

        int result1 = test.evaluateMultipleConditions(values);
        int result2 = test.evaluateNestedConditions(values);
        int result3 = test.evaluateWithTernary(values);

        System.out.println("Result with multiple conditions: " + result1);
        System.out.println("Result with nested conditions: " + result2);
        System.out.println("Result with ternary operator: " + result3);
    }
}
