package com.github.mjdetullio.jenkins.plugins.multibranch.util;

import org.slf4j.helpers.MessageFormatter;

public final class FormattingUtils {

    /**
     * Static utility class.
     */
    private FormattingUtils() { }

    public static String format(final String pattern, final Object... arguments) {
        return MessageFormatter.arrayFormat(pattern, arguments).getMessage();
    }

}
