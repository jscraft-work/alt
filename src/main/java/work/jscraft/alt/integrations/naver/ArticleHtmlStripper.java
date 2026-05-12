package work.jscraft.alt.integrations.naver;

import java.util.Set;

import org.springframework.web.util.HtmlUtils;

/**
 * Naver 기사 본문 HTML 에서 텍스트만 추출하는 간이 파서.
 *
 * <ul>
 *     <li>{@code <script>, <style>, <noscript>, <svg>, <iframe>} 안 텍스트는 무시한다.</li>
 *     <li>{@code <article>} 또는 {@code <main>} 안 텍스트가 있으면 우선 사용하고,
 *         없으면 전체 본문 텍스트로 폴백한다.</li>
 *     <li>HTML 엔티티는 {@link HtmlUtils#htmlUnescape(String)} 으로 풀고
 *         모든 공백은 단일 스페이스로 collapse 한다.</li>
 * </ul>
 */
final class ArticleHtmlStripper {

    private static final Set<String> SKIP_TAGS = Set.of(
            "script", "style", "noscript", "svg", "iframe");
    private static final Set<String> PRIORITY_TAGS = Set.of("article", "main");

    private ArticleHtmlStripper() {
    }

    static String extract(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        StringBuilder priorityBuf = new StringBuilder();
        StringBuilder bodyBuf = new StringBuilder();
        StringBuilder dataBuf = new StringBuilder();

        int skipDepth = 0;
        int priorityDepth = 0;
        int i = 0;
        int len = html.length();

        while (i < len) {
            char c = html.charAt(i);
            if (c == '<') {
                // flush accumulated text
                flushData(dataBuf, skipDepth, priorityDepth, priorityBuf, bodyBuf);
                // Handle HTML comment <!-- ... --> by scanning to the proper terminator first,
                // because the comment body may contain '>' characters.
                if (i + 3 < len
                        && html.charAt(i + 1) == '!'
                        && html.charAt(i + 2) == '-'
                        && html.charAt(i + 3) == '-') {
                    int endComment = html.indexOf("-->", i + 4);
                    if (endComment < 0) {
                        return finalize(priorityBuf, bodyBuf);
                    }
                    i = endComment + 3;
                    continue;
                }
                int gt = html.indexOf('>', i + 1);
                if (gt < 0) {
                    // malformed; treat the rest as text
                    break;
                }
                String rawTag = html.substring(i + 1, gt);
                i = gt + 1;
                if (rawTag.startsWith("!")) {
                    // doctype etc.
                    continue;
                }
                boolean isClose = rawTag.startsWith("/");
                String tagBody = isClose ? rawTag.substring(1) : rawTag;
                boolean selfClose = tagBody.endsWith("/");
                if (selfClose) {
                    tagBody = tagBody.substring(0, tagBody.length() - 1);
                }
                String tagName = extractTagName(tagBody).toLowerCase();
                if (tagName.isEmpty()) {
                    continue;
                }
                if (isClose) {
                    if (SKIP_TAGS.contains(tagName)) {
                        if (skipDepth > 0) {
                            skipDepth--;
                        }
                    } else if (PRIORITY_TAGS.contains(tagName)) {
                        if (priorityDepth > 0) {
                            priorityDepth--;
                        }
                    }
                } else {
                    if (SKIP_TAGS.contains(tagName)) {
                        if (!selfClose) {
                            skipDepth++;
                        }
                    } else if (PRIORITY_TAGS.contains(tagName)) {
                        if (!selfClose) {
                            priorityDepth++;
                        }
                    }
                }
                continue;
            }
            dataBuf.append(c);
            i++;
        }
        flushData(dataBuf, skipDepth, priorityDepth, priorityBuf, bodyBuf);
        return finalize(priorityBuf, bodyBuf);
    }

    private static void flushData(StringBuilder dataBuf,
                                  int skipDepth,
                                  int priorityDepth,
                                  StringBuilder priorityBuf,
                                  StringBuilder bodyBuf) {
        if (dataBuf.length() == 0) {
            return;
        }
        String data = dataBuf.toString();
        dataBuf.setLength(0);
        if (skipDepth > 0) {
            return;
        }
        String trimmed = data.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        if (priorityDepth > 0) {
            appendWithSpace(priorityBuf, trimmed);
        }
        appendWithSpace(bodyBuf, trimmed);
    }

    private static void appendWithSpace(StringBuilder buf, String text) {
        if (buf.length() > 0) {
            buf.append(' ');
        }
        buf.append(text);
    }

    private static String finalize(StringBuilder priorityBuf, StringBuilder bodyBuf) {
        String raw = priorityBuf.length() > 0 ? priorityBuf.toString() : bodyBuf.toString();
        if (raw.isEmpty()) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(raw);
        StringBuilder sb = new StringBuilder(unescaped.length());
        boolean lastSpace = false;
        for (int i = 0; i < unescaped.length(); i++) {
            char c = unescaped.charAt(i);
            if (Character.isWhitespace(c) || c == ' ') {
                if (!lastSpace && sb.length() > 0) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') {
            end--;
        }
        return sb.substring(0, end);
    }

    private static String extractTagName(String tagBody) {
        int len = tagBody.length();
        int i = 0;
        while (i < len) {
            char c = tagBody.charAt(i);
            if (Character.isWhitespace(c) || c == '/') {
                break;
            }
            i++;
        }
        return tagBody.substring(0, i);
    }
}
