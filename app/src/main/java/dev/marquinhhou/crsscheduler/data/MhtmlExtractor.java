package dev.marquinhhou.crsscheduler.data;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unwraps a saved "Web Page, Single File" (.mht/.mhtml) document down to the plain HTML it
 * contains, so it can be handed to {@link ScheduleParser} exactly like a plain saved .html file.
 *
 * An .mht file is a MIME multipart/related container (RFC 2557): a small header block naming a
 * boundary string, followed by one part per resource (the page's own HTML, then its stylesheets,
 * images, etc. -- each with its own Content-Type/Content-Transfer-Encoding). This class finds the
 * boundary generically from the container's own header (never hardcoded), splits on it, locates
 * whichever part is text/html, and decodes that ONE part according to whatever transfer encoding
 * IT declares (base64, quoted-printable, or literal binary/7bit/8bit) -- covering how different
 * tools (Chrome/Blink, Firefox, Edge, email clients) each choose to encode that part, not just
 * the "binary" encoding Blink happens to use for its own saved pages.
 *
 * extractHtml() is a pure, best-effort transform: given anything that ISN'T a recognizable MHT
 * container (plain pasted HTML, a .ics calendar, garbage), or an MHT container extraction fails
 * on for any reason, it returns the original input completely unchanged. Every existing caller
 * (plain HTML paste, .ics import) keeps working exactly as before; MHT support is purely additive.
 */
public final class MhtmlExtractor {

    private MhtmlExtractor() {}

    private static final Pattern MULTIPART_HEADER = Pattern.compile(
            "Content-Type:\\s*multipart/related.{0,500}?boundary=\"?([^\"\\r\\n;]+)\"?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern PART_CONTENT_TYPE = Pattern.compile(
            "^Content-Type:\\s*([^;\\r\\n]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern PART_TRANSFER_ENCODING = Pattern.compile(
            "^Content-Transfer-Encoding:\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern PART_CHARSET = Pattern.compile(
            "charset=\"?([^\"\\r\\n;]+)\"?", Pattern.CASE_INSENSITIVE);

    /**
     * Returns the decoded HTML of the first text/html part of {@code content} if it's an MHT
     * container, or {@code content} itself unchanged otherwise (not MHT, or nothing usable found).
     */
    public static String extractHtml(String content) {
        if (content == null || content.isEmpty()) return content;

        // The multipart header is always within the first few header lines of the file --
        // bounding the search keeps this cheap even on a multi-hundred-KB saved page.
        Matcher headerMatch = MULTIPART_HEADER.matcher(
                content.substring(0, Math.min(content.length(), 8000)));
        if (!headerMatch.find()) return content; // Doesn't look like an MHT container at all.

        String boundary = headerMatch.group(1).trim();
        if (boundary.isEmpty()) return content;

        try {
            String needle = "--" + boundary;
            int firstDelim = indexOfLineStart(content, needle, 0);
            if (firstDelim == -1) return content;

            int partStart = firstDelim + needle.length();
            while (true) {
                int nextDelim = indexOfLineStart(content, needle, partStart);
                if (nextDelim == -1) break; // Ran off the end without a closing delimiter.

                String decoded = tryDecodeHtmlPart(content.substring(partStart, nextDelim));
                if (decoded != null) return decoded;

                partStart = nextDelim + needle.length();
            }
        } catch (Exception e) {
            // A real-world file doing something this parser didn't anticipate should degrade to
            // "MHT support didn't kick in", never to a crash on what's otherwise a normal import.
            return content;
        }
        return content; // Valid multipart container, but no text/html part in it.
    }

    /**
     * Finds {@code needle} at the start of a line (index 0, or right after a '\n'). RFC 2046
     * requires a delimiter line to begin at the start of a line; a plain substring search would
     * also match a boundary-shaped run of characters that happens to appear mid-line inside the
     * page's own content (or inside another part's binary data), silently truncating or
     * misdirecting the split.
     */
    private static int indexOfLineStart(String content, String needle, int fromIndex) {
        int idx = fromIndex;
        while (true) {
            idx = content.indexOf(needle, idx);
            if (idx == -1) return -1;
            if (idx == 0 || content.charAt(idx - 1) == '\n') return idx;
            idx += 1;
        }
    }

    /** Null if this part isn't text/html; otherwise its fully decoded body. */
    private static String tryDecodeHtmlPart(String rawPart) {
        String part = rawPart;
        if (part.startsWith("\r\n")) part = part.substring(2);
        else if (part.startsWith("\n")) part = part.substring(1);

        int headerEnd = part.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (headerEnd == -1) {
            headerEnd = part.indexOf("\n\n");
            sepLen = 2;
        }
        if (headerEnd == -1) return null; // No header/body split found -- not a usable part.

        String headers = part.substring(0, headerEnd);
        String body = part.substring(headerEnd + sepLen);

        Matcher typeMatch = PART_CONTENT_TYPE.matcher(headers);
        if (!typeMatch.find() || !typeMatch.group(1).trim().equalsIgnoreCase("text/html")) {
            return null;
        }

        // The line ending immediately before the next delimiter belongs to the delimiter line
        // itself, not this part's content (RFC 2046).
        if (body.endsWith("\r\n")) body = body.substring(0, body.length() - 2);
        else if (body.endsWith("\n")) body = body.substring(0, body.length() - 1);

        Matcher encMatch = PART_TRANSFER_ENCODING.matcher(headers);
        // RFC 2045 default when the header is absent.
        String encoding = encMatch.find() ? encMatch.group(1).trim() : "7bit";

        String charset = "UTF-8";
        Matcher csMatch = PART_CHARSET.matcher(headers);
        if (csMatch.find()) charset = csMatch.group(1).trim();

        try {
            switch (encoding.toLowerCase(Locale.US)) {
                case "base64":
                    String stripped = body.replaceAll("\\s+", "");
                    return new String(Base64.getDecoder().decode(stripped), charset);
                case "quoted-printable":
                    return decodeQuotedPrintable(body, charset);
                default:
                    // "binary" / "7bit" / "8bit": already literal text within the container.
                    return body;
            }
        } catch (Exception e) {
            // A malformed base64/QP body shouldn't sink the whole import -- the raw body is
            // still a reasonable best-effort fallback (ScheduleParser will simply find no table
            // in it and report that clearly, rather than this method throwing).
            return body;
        }
    }

    private static String decodeQuotedPrintable(String body, String charset) throws Exception {
        // "=<CRLF>" / "=<LF>" are soft line breaks -- join points, not content.
        String joined = body.replace("=\r\n", "").replace("=\n", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < joined.length(); i++) {
            char c = joined.charAt(i);
            if (c == '=' && i + 2 < joined.length()) {
                try {
                    out.write(Integer.parseInt(joined.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException notAnEscape) {
                    // Falls through to writing '=' literally below.
                }
            }
            out.write((byte) c);
        }
        return new String(out.toByteArray(), charset);
    }
}
