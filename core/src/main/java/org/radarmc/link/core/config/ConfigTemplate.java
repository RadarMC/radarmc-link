package org.radarmc.link.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConfigTemplate {
    private static final Pattern KEY_LINE = Pattern.compile("^([A-Za-z0-9_-]+)\\s*:.*$");

    static final class Block {
        final List<String> comments;
        final List<String> body;

        Block(List<String> comments, List<String> body) {
            this.comments = comments;
            this.body = body;
        }
    }

    final List<String> header;
    final Map<String, Block> blocks;

    private ConfigTemplate(List<String> header, Map<String, Block> blocks) {
        this.header = header;
        this.blocks = blocks;
    }

    static ConfigTemplate parse(String text) {
        List<String> lines = Arrays.asList(text.replace("\r\n", "\n").split("\n", -1));
        List<Integer> starts = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = KEY_LINE.matcher(lines.get(i));
            if (m.matches()) {
                starts.add(i);
                keys.add(m.group(1));
            }
        }
        if (starts.isEmpty()) {
            return new ConfigTemplate(trimBlank(lines), new LinkedHashMap<String, Block>());
        }
        Split prefix = split(lines.subList(0, starts.get(0)));
        List<String> comments = prefix.next;
        Map<String, Block> blocks = new LinkedHashMap<>();
        for (int k = 0; k < starts.size(); k++) {
            int end = k + 1 < starts.size() ? starts.get(k + 1) : lines.size();
            Split rest = split(lines.subList(starts.get(k) + 1, end));
            List<String> body = new ArrayList<>();
            body.add(lines.get(starts.get(k)));
            body.addAll(rest.own);
            blocks.put(keys.get(k), new Block(comments, trimBlank(body)));
            comments = rest.next;
        }
        return new ConfigTemplate(trimBlank(prefix.own), blocks);
    }

    static String render(List<String> header, List<Map.Entry<String, Block>> ordered) {
        StringBuilder out = new StringBuilder();
        appendLines(out, header);
        for (Map.Entry<String, Block> entry : ordered) {
            if (out.length() > 0) {
                out.append('\n');
            }
            appendLines(out, entry.getValue().comments);
            appendLines(out, entry.getValue().body);
        }
        return out.toString();
    }

    private static final class Split {
        final List<String> own;
        final List<String> next;

        Split(List<String> own, List<String> next) {
            this.own = own;
            this.next = next;
        }
    }

    private static Split split(List<String> lines) {
        int tail = lines.size();
        while (tail > 0 && isBlankOrComment(lines.get(tail - 1))) {
            tail--;
        }
        int blank = tail;
        while (blank < lines.size() && !isBlank(lines.get(blank))) {
            blank++;
        }
        List<String> own = new ArrayList<>(lines.subList(0, blank));
        List<String> next = trimBlank(lines.subList(blank, lines.size()));
        return new Split(own, next);
    }

    private static void appendLines(StringBuilder out, List<String> lines) {
        for (String line : lines) {
            out.append(line).append('\n');
        }
    }

    private static List<String> trimBlank(List<String> lines) {
        int from = 0;
        int to = lines.size();
        while (from < to && isBlank(lines.get(from))) {
            from++;
        }
        while (to > from && isBlank(lines.get(to - 1))) {
            to--;
        }
        return new ArrayList<>(lines.subList(from, to));
    }

    private static boolean isBlankOrComment(String line) {
        return isBlank(line) || line.startsWith("#");
    }

    private static boolean isBlank(String line) {
        return line.trim().isEmpty();
    }
}
