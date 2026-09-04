package com.korabel.schedule;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny HTML helpers: tag stripping and entity decoding. No Android APIs. */
public final class Html {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0]+");

    private Html() { }

    /** Tags out, entities decoded, whitespace collapsed. */
    public static String text(String html) {
        if (html == null) return "";
        return SPACES.matcher(decode(TAG.matcher(html).replaceAll(" "))).replaceAll(" ").trim();
    }

    /** Value of an attribute in a tag's attribute string; "" when absent. */
    public static String attr(String tagAttrs, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").matcher(tagAttrs);
        if (!m.find()) return "";
        return decode(m.group(2) != null ? m.group(2) : m.group(3));
    }

    public static String decode(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        Matcher m = ENTITY.matcher(s);
        StringBuffer out = new StringBuffer(s.length());
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(entity(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String entity(String body) {
        if (body.charAt(0) == '#') {
            try {
                int code = body.charAt(1) == 'x' || body.charAt(1) == 'X'
                        ? Integer.parseInt(body.substring(2), 16)
                        : Integer.parseInt(body.substring(1));
                return new String(Character.toChars(code));
            } catch (RuntimeException e) {
                return "&" + body + ";";
            }
        }
        switch (body) {
            case "nbsp":   return " ";
            case "amp":    return "&";
            case "lt":     return "<";
            case "gt":     return ">";
            case "quot":   return "\"";
            case "apos":   return "'";
            case "laquo":  return "«";
            case "raquo":  return "»";
            case "mdash":  return "—";
            case "ndash":  return "–";
            case "shy":    return "";
            case "deg":    return "°";
            case "hellip": return "…";
            default:       return "&" + body + ";";
        }
    }
}
