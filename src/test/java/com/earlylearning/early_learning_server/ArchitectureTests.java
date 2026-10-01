package com.earlylearning.early_learning_server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代码组织规范（{@code reference/adr/0008}，要点见 AGENTS.md 第 3 节）的守护测试：违反即测试红。
 *
 * <ol>
 *   <li>模块边界（Spring Modulith）：模块（主包的直接子包）之间只能引用对方根包或 @NamedInterface 暴露的子包，
 *       不许伸进内部，也不许成环。common 的每个子包都是 @NamedInterface。</li>
 *   <li>模块内方向（ArchUnit，覆盖全部模块）：{@code interfaces → application → domain ← infrastructure}。</li>
 *   <li>源码 import（补字节码分析的盲区）：只在 Javadoc 里出现的 import 不会进字节码，ArchUnit 看不到，
 *       但它照样让 domain 在源码上指向外层；这里直接扫源码。</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.earlylearning.early_learning_server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/earlylearning/early_learning_server");
    private static final Pattern PACKAGE = Pattern.compile("^package com\\.earlylearning\\.early_learning_server\\.([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern PROJECT_IMPORT = Pattern.compile("^import com\\.earlylearning\\.early_learning_server\\.([\\w.]+);", Pattern.MULTILINE);

    @ArchTest
    void 模块间只走对方根包暴露的API且不成环(JavaClasses classes) {
        ApplicationModules.of(EarlyLearningServerApplication.class).verify();
    }

    @ArchTest
    ArchRule domain不依赖其他层与SpringWeb =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaces..", "..application..", "..infrastructure..",
                            "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    ArchRule application与infrastructure不依赖interfaces =
            noClasses().that().resideInAnyPackage("..application..", "..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    ArchRule interfaces不绕过application直接用infrastructure =
            noClasses().that().resideInAPackage("..interfaces..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    ArchRule application不产出HTTP响应形状 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().haveFullyQualifiedName(
                            "com.earlylearning.early_learning_server.common.web.ApiResponse")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.ResponseEntity");

    @ArchTest
    ArchRule common不依赖任何业务模块 =
            noClasses().that().resideInAPackage("com.earlylearning.early_learning_server.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.earlylearning.early_learning_server.admin..",
                            "com.earlylearning.early_learning_server.ai..",
                            "com.earlylearning.early_learning_server.auth..",
                            "com.earlylearning.early_learning_server.license..",
                            "com.earlylearning.early_learning_server.material..",
                            "com.earlylearning.early_learning_server.storage..",
                            "com.earlylearning.early_learning_server.teacher..");

    @org.junit.jupiter.api.Test
    void 源码里domain不import其他层() {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String source = read(file);
            Matcher pkg = PACKAGE.matcher(source);
            if (!pkg.find() || !isLayer(pkg.group(1), "domain")) {
                continue;
            }
            Matcher imports = PROJECT_IMPORT.matcher(source);
            while (imports.find()) {
                String imported = imports.group(1);
                if (isLayer(imported, "interfaces") || isLayer(imported, "application") || isLayer(imported, "infrastructure")) {
                    violations.add(MAIN_SOURCES.relativize(file) + " → " + imported);
                }
            }
        }
        assertThat(violations).as("domain 层源码引用了外层（含只在 Javadoc 中使用的 import）").isEmpty();
    }

    /** {@code ai.domain.scoring} 属于 domain 层：模块名之后的第一段是层名。 */
    private static boolean isLayer(String packageName, String layer) {
        String[] parts = packageName.split("\\.");
        return parts.length >= 2 && parts[1].equals(layer);
    }

    private static List<Path> javaSources() {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
