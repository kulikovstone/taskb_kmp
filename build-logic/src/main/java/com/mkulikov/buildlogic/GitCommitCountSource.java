package com.mkulikov.buildlogic;

import org.gradle.api.provider.ValueSource;

import java.nio.file.Path;

/** A version code that does not reset when version-major.txt changes. */
public abstract class GitCommitCountSource implements ValueSource<Integer, GitVersionSource.Parameters> {
    @Override
    public Integer obtain() {
        return calculate(getParameters().getRepositoryDirectory().get().getAsFile().toPath());
    }

    public static int calculate(Path directory) {
        if (GitVersionSource.git(directory, "rev-parse", "--is-shallow-repository").equals("true")) {
            throw new IllegalStateException("Для вычисления versionCode нужна полная история Git.");
        }
        int count = Integer.parseInt(GitVersionSource.git(directory, "rev-list", "--first-parent", "--count", "HEAD"));
        if (count < 1 || count > 2_100_000_000) {
            throw new IllegalStateException("Android versionCode должен быть в диапазоне от 1 до 2100000000.");
        }
        return count;
    }
}
