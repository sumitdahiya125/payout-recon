package com.chronon.recon.domain.quality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects everything the parsers had to skip, coerce or guess. This is what makes the result
 * auditable: nothing is silently dropped.
 */
public class DataQualityLog {

    private final List<Issue> issues = new ArrayList<>();

    public void add(Issue issue) {
        issues.add(issue);
    }

    public void error(String source, int row, String field, String message) {
        add(Issue.error(source, row, field, message));
    }

    public void warn(String source, int row, String field, String message) {
        add(Issue.warn(source, row, field, message));
    }

    public void info(String source, int row, String field, String message) {
        add(Issue.info(source, row, field, message));
    }

    public List<Issue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public long count(Severity severity) {
        return issues.stream().filter(i -> i.severity() == severity).count();
    }

    public boolean hasErrors() {
        return count(Severity.ERROR) > 0;
    }

    public int size() {
        return issues.size();
    }

    public void merge(DataQualityLog other) {
        issues.addAll(other.issues);
    }
}
