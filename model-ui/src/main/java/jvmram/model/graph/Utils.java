package jvmram.model.graph;

import java.time.Instant;

public class Utils {

    public static <T extends Comparable<T>> T max(T left, T right) {
        return left.compareTo(right) > 0
                ? left
                : right;
    }

    public static <T extends Comparable<T>> T min(T left, T right) {
        return left.compareTo(right) <= 0
                ? left
                : right;
    }

    private Utils() {
    }

    static void main() {
        var left = Instant.MIN;
        var right = Instant.MAX;
        
        System.out.printf("min(%s, %s) = %s%n", left, right, min(left, right));
        System.out.printf("max(%s, %s) = %s%n", left, right, max(left, right));
    }
}
