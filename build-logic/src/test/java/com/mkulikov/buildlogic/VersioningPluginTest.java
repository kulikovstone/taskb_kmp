package com.mkulikov.buildlogic;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VersioningPluginTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void versionAndConfigurationCacheFollowCommitsAndBaseChanges() throws Exception {
        Path project = temporary.newFolder("versioned project").toPath();
        Files.writeString(
            project.resolve("settings.gradle"),
            "rootProject.name = 'version-test'\ninclude 'app'\n"
        );
        Files.writeString(
            project.resolve("build.gradle"),
            """
                plugins { id 'taskb.versioning' }
                println "configuredCode=${taskbVersion.androidVersionCode}"
                """
        );
        Files.createDirectory(project.resolve("app"));
        Files.writeString(
            project.resolve("app/build.gradle"),
            "println \"appVersion=${project.version}\"\n"
        );
        Files.writeString(project.resolve(".gitignore"), ".gradle/\n**/build/\n");
        Files.writeString(project.resolve("version-major.txt"), "1.38\n");
        git(project, "init", "--quiet", "--initial-branch=master");
        commit(project);
        String head = git(project, "rev-parse", "HEAD");

        var runner =
                GradleRunner.create()
                        .withProjectDir(project.toFile())
                        .withTestKitDir(temporary.newFolder("testkit"))
                        .withPluginClasspath()
                        .withArguments(
                            "printVersion",
                            "--configuration-cache",
                            "--console=plain",
                            "--stacktrace"
                        );

        BuildResult first = runner.build();
        assertVersion(first, "1.38");
        assertTrue(first.getOutput().contains("appVersion=1.38"));
        assertTrue(first.getOutput().contains("configuredCode=1"));
        BuildResult repeated = runner.build();
        assertVersion(repeated, "1.38");
        assertTrue(repeated.getOutput().contains("Reusing configuration cache."));
        assertEquals(head, git(project, "rev-parse", "HEAD"));
        assertEquals("", git(project, "status", "--porcelain"));
        BuildResult quiet =
                runner.withArguments(
                    "printVersion",
                    "--configuration-cache",
                    "--console=plain",
                    "--quiet"
                )
                        .build();
        assertEquals("1.38", quiet.getOutput().strip());
        runner.withArguments(
            "printVersion",
            "--configuration-cache",
            "--console=plain",
            "--stacktrace"
        );

        Files.writeString(project.resolve("change.txt"), "Next commit");
        commit(project);
        BuildResult nextCommit = runner.build();
        assertVersion(nextCommit, "1.39");
        assertTrue(nextCommit.getOutput().contains("appVersion=1.39"));
        assertTrue(nextCommit.getOutput().contains("configuredCode=2"));

        Files.writeString(project.resolve("version-major.txt"), "2.0\n");
        BuildResult newBase = runner.build();
        assertVersion(newBase, "2.0");
        assertTrue(newBase.getOutput().contains("configuredCode=2"));
        commit(project);
        BuildResult committedBase = runner.build();
        assertVersion(committedBase, "2.0");
        assertTrue(committedBase.getOutput().contains("configuredCode=3"));
    }

    private static void assertVersion(BuildResult result, String version) {
        assertTrue(result.getOutput(), result.getOutput().lines().anyMatch(version::equals));
    }

    private static void commit(Path project) throws Exception {
        git(project, "add", "--all");
        git(project, "commit", "--quiet", "-m", "Version test commit");
    }

    private static String git(Path project, String... arguments) throws Exception {
        var command =
                new ArrayList<>(
                    List.of(
                            "git",
                            "-c",
                            "commit.gpgsign=false",
                            "-c",
                            "core.hooksPath=" + project.resolve("disabled-hooks")
                        )
                );
        command.addAll(List.of(arguments));
        var builder =
                new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
        builder.environment()
                .putAll(
                    java.util.Map.of(
                            "GIT_AUTHOR_NAME",
                            "Version test",
                            "GIT_AUTHOR_EMAIL",
                            "version@example.com",
                            "GIT_COMMITTER_NAME",
                            "Version test",
                            "GIT_COMMITTER_EMAIL",
                            "version@example.com"
                        )
                );
        Process process = builder.start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        assertEquals(output, 0, process.waitFor());
        return output;
    }
}
