package net.aquadc.mike.plugin.test;

import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class BadCyrillicRegexpJava {
    private static final Pattern A_YA_UPPER = Pattern.compile("[А-Я]");
    private static final Pattern A_YA_LOWER = Pattern.compile("[а-я]");
    private static final Pattern A_YA_LOWER_UPPER = Pattern.compile("[а-яА-ЯA-Z]");
    private static final Pattern A_YA_UPPER_LOWER = Pattern.compile("[a-zА-Яа-я\\d]");
    private static final Pattern A_YA_u = Pattern.compile("[\\u0410-\\u042F]");

    private static final Pattern A_YA_u_AND_ESCAPED = Pattern.compile("[\\u0410-\\u042F]\\Q[а-я]\\E");

    private static final Pattern A_YA_ESCAPED = Pattern.compile("\\Q[а-я]\\E");
    private static final Pattern A_H = Pattern.compile("[а-х]");
    private static final Pattern H_YA = Pattern.compile("[х-я]");
}
