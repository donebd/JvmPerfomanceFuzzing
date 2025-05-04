package benchmark;

public class SwitchPatternTest {
    public int switchOverIntegers(int value, int iterations) {
        int result = 0;

        for (int i = 0; i < iterations; i++) {
            int testValue = (value + i) % 10;
            switch (testValue) {
                case 0: result += 1; break;
                case 1: result += 2; break;
                case 2: result += 3; break;
                case 3: result += 4; break;
                case 4: result += 5; break;
                case 5: result += 6; break;
                case 6: result += 7; break;
                case 7: result += 8; break;
                case 8: result += 9; break;
                default: result += 10; break;
            }
        }

        return result;
    }

    public int switchOverStrings(String base, int iterations) {
        int result = 0;
        String[] strings = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};

        for (int i = 0; i < iterations; i++) {
            String testValue = strings[i % strings.length];
            switch (testValue) {
                case "one": result += 1; break;
                case "two": result += 2; break;
                case "three": result += 3; break;
                case "four": result += 4; break;
                case "five": result += 5; break;
                case "six": result += 6; break;
                case "seven": result += 7; break;
                case "eight": result += 8; break;
                case "nine": result += 9; break;
                default: result += 10; break;
            }
        }

        return result;
    }

    public static void main(String[] args) {
        SwitchPatternTest test = new SwitchPatternTest();
        int intResult = test.switchOverIntegers(5, 1000);
        int stringResult = test.switchOverStrings("test", 1000);

        System.out.println("Integer switch result: " + intResult);
        System.out.println("String switch result: " + stringResult);
    }
}
