package com.jdeploy.cli;

import java.util.ArrayList;
import java.util.List;

public final class CliTableFormatter {

    private CliTableFormatter() {
    }

    public static String format(List<String> headers, List<List<String>> rows) {
        List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int max = headers.get(i).length();
            for (List<String> row : rows) {
                max = Math.max(max, row.get(i).length());
            }
            widths.add(max);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(line(headers, widths)).append("\n");
        sb.append(separator(widths)).append("\n");
        for (List<String> row : rows) {
            sb.append(line(row, widths)).append("\n");
        }
        return sb.toString();
    }

    private static String line(List<String> values, List<Integer> widths) {
        StringBuilder row = new StringBuilder("| ");
        for (int i = 0; i < values.size(); i++) {
            row.append(values.get(i));
            row.append(" ".repeat(widths.get(i) - values.get(i).length()));
            row.append(" | ");
        }
        return row.toString().trim();
    }

    private static String separator(List<Integer> widths) {
        StringBuilder sep = new StringBuilder("|-");
        for (int width : widths) {
            sep.append("-".repeat(width)).append("-|-");
        }
        return sep.substring(0, sep.length() - 1);
    }
}
