package co.churchyouth.pos.util;

import java.util.Locale;

/**
 * Every monetary value in this app is a long, counted in cents. Never a
 * float or double — those introduce rounding errors that eventually show
 * up as a receipt that doesn't quite add up. All arithmetic (totals,
 * profit, balances) happens on these longs; this class only formats them
 * for display and does the one conversion from a user-typed decimal string.
 */
public final class Money {

    private Money() { }

    /** Format cents as "KSh 1,234.50" using the given currency symbol. */
    public static String format(long cents, String currencySymbol) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        long whole = abs / 100;
        long frac = abs % 100;
        String grouped = groupThousands(whole);
        return (negative ? "-" : "") + currencySymbol + " " + grouped + "." + String.format(Locale.US, "%02d", frac);
    }

    /** Parse a user-typed amount like "50", "50.5", or "1,200.75" into cents. Returns 0 for blank/invalid input. */
    public static long parseToCents(String input) {
        if (input == null) return 0;
        String cleaned = input.trim().replace(",", "");
        if (cleaned.isEmpty()) return 0;
        try {
            // Parse as a fixed-point decimal manually so we never touch double.
            boolean negative = cleaned.startsWith("-");
            if (negative) cleaned = cleaned.substring(1);
            String[] parts = cleaned.split("\\.", 2);
            long whole = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
            long frac = 0;
            if (parts.length > 1) {
                String fracStr = (parts[1] + "00").substring(0, 2);
                frac = Long.parseLong(fracStr);
            }
            long cents = whole * 100 + frac;
            return negative ? -cents : cents;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String groupThousands(long value) {
        String digits = Long.toString(value);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) sb.append(',');
        }
        return sb.reverse().toString();
    }
}
