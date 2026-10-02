package ultimategens.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /** Colores con & y hex (&#RRGGBB). */
    public static String color(String text) {
        if (text == null) return "";
        Matcher matcher = HEX.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    /** Reemplaza placeholders en pares: replace(texto, "%a%", "1", "%b%", "2"). */
    public static String replace(String text, String... pairs) {
        if (text == null) return "";
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace(pairs[i], pairs[i + 1]);
        }
        return text;
    }

    /** 5000 -> "5,000" */
    public static String full(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    /** 978000000000000 -> "978T" */
    public static String abbreviate(double value, List<String> suffixes) {
        int index = 0;
        while (value >= 1000 && index < suffixes.size() - 1) {
            value /= 1000;
            index++;
        }
        DecimalFormat format = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value) + suffixes.get(index);
    }

    /** 3700 -> "1h 1m 40s" */
    public static String time(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s ");
        return sb.toString().trim();
    }
              }
