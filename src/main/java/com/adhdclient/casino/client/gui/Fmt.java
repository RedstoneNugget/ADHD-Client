package com.adhdclient.casino.client.gui;

import net.minecraft.client.MinecraftClient;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Zahlen-, Zeit- und Datumsformatierung passend zur eingestellten Spielsprache. */
public final class Fmt {

    private static String cachedLang = "";
    private static Locale cachedLocale = Locale.ROOT;

    private Fmt() {
    }

    public static Locale locale() {
        String lang = "en_us";
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getLanguageManager() != null) {
            lang = mc.getLanguageManager().getLanguage();
        }
        if (!lang.equals(cachedLang)) {
            cachedLang = lang;
            cachedLocale = Locale.forLanguageTag(lang.replace('_', '-'));
        }
        return cachedLocale;
    }

    /** 1234567 → "1.234.567" (de) bzw. "1,234,567" (en). */
    public static String chips(long value) {
        return NumberFormat.getIntegerInstance(locale()).format(value);
    }

    /** Mit Vorzeichen: "+1.234" / "-50". */
    public static String signed(long value) {
        return (value > 0 ? "+" : "") + chips(value);
    }

    /** 0.1234 → "12,3 %". */
    public static String percent(double fraction) {
        NumberFormat nf = NumberFormat.getPercentInstance(locale());
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(fraction);
    }

    /** 2.345 → "2,34x". */
    public static String multiplier(double m) {
        double floored = Math.floor(m * 100) / 100.0;
        NumberFormat nf = NumberFormat.getNumberInstance(locale());
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(floored) + "x";
    }

    /** Kompakte Zahl für Tabellen-Chips: 1500 → "1,5k". */
    public static String compact(long value) {
        if (value < 1_000) {
            return Long.toString(value);
        }
        NumberFormat nf = NumberFormat.getNumberInstance(locale());
        nf.setMaximumFractionDigits(1);
        if (value < 1_000_000) {
            return nf.format(value / 1_000.0) + "k";
        }
        return nf.format(value / 1_000_000.0) + "M";
    }

    /** Dauer als "h:mm:ss" bzw. "m:ss". */
    public static String duration(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    public static String date(long epochMs) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale())
                .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }
}
