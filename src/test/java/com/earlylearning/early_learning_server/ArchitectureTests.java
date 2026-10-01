package com.earlylearning.early_learning_server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代码组织规范（AGENTS.md 第 3 节）的守护测试：违反即测试红。
 *
 * <pre>
 *   入口        controller/（HTTP）、filter/（Servlet 过滤器）
 *   请求与响应   dto/
 *   业务        service/
 *   数据与规则   entity/（表映射）、model/（不落库的业务对象、规则与外部能力接口）
 *   数据访问     mapper/
 *   外部适配     client/
 *   装配        config/
 * </pre>
 *
 * <ol>
 *   <li>模块边界（Spring Modulith）：模块之间只能用对方根包或 @NamedInterface 暴露的子包，不许成环。</li>
 *   <li>层间方向（ArchUnit，覆盖全部模块）：入口 → service → mapper / client；entity、model 不依赖任何上层。</li>
 *   <li>源码 import：只在 Javadoc 里出现的 import 不进字节码，ArchUnit 看不到，这里直接扫源码。</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.earlylearning.early_learning_server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/earlylearning/early_learning_server");
    private static final Pattern PACKAGE = Pattern.compile("^package com\\.earlylearning\\.early_learning_server\\.([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern PROJECT_IMPORT = Pattern.compile("^import com\\.earlylearning\\.early_learning_server\\.([\\w.]+);", Pattern.MULTILINE);
    /** 层 = 模块名之后的第一段（{@code ai.model.scoring} 属于 model 层，{@code material.model.config} 不是 config 层）。 */
    private static final String LAYER = "com.earlylearning.early_learning_server.*.";
    private static final String L_CONTROLLER = LAYER + "controller..";
    private static final String L_FILTER = LAYER + "filter..";
    private static final String L_DTO = LAYER + "dto..";
    private static final String L_SERVICE = LAYER + "service..";
    private static final String L_ENTITY = LAYER + "entity..";
    private static final String L_MODEL = LAYER + "model..";
    private static final String L_MAPPER = LAYER + "mapper..";
    private static final String L_CLIENT = LAYER + "client..";
    private static final String L_CONFIG = LAYER + "config..";

    private static final Set<String> ABOVE_ENTITY_AND_MODEL =
            Set.of("controller", "filter", "dto", "service", "mapper", "client", "config");

    @ArchTest
    void 模块间只走对方暴露的API且不成环(JavaClasses classes) {
        ApplicationModules.of(EarlyLearningServerApplication.class).verify();
    }

    @ArchTest
    ArchRule entity与model不依赖任何上层与SpringWeb =
            noClasses().that().resideInAnyPackage(L_ENTITY, L_MODEL)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            L_CONTROLLER, L_FILTER, L_DTO, L_SERVICE, L_MAPPER, L_CLIENT,
                            L_CONFIG, "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    ArchRule 除入口与装配外都不依赖入口 =
            noClasses().that().resideInAnyPackage(L_DTO, L_SERVICE, L_ENTITY, L_MODEL, L_MAPPER, L_CLIENT)
                    .should().dependOnClassesThat().resideInAnyPackage(L_CONTROLLER, L_FILTER);

    @ArchTest
    ArchRule controller不绕过service直接用mapper或client =
            noClasses().that().resideInAPackage(L_CONTROLLER)
                    .should().dependOnClassesThat().resideInAnyPackage(L_MAPPER, L_CLIENT);

    @ArchTest
    ArchRule dto与mapper不依赖业务与外部适配 =
            noClasses().that().resideInAnyPackage(L_DTO, L_MAPPER)
                    .should().dependOnClassesThat().resideInAnyPackage(L_SERVICE, L_CLIENT);

    @ArchTest
    ArchRule service不产出HTTP响应形状 =
            noClasses().that().resideInAPackage(L_SERVICE)
                    .should().dependOnClassesThat().haveFullyQualifiedName(
                            "com.earlylearning.early_learning_server.common.web.ApiResponse")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.ResponseEntity");

    @ArchTest
    ArchRule controller不把实体直接返回给客户端 =
            methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                    .and().arePublic()
                    .should(notReturnTypesFrom(L_ENTITY));

    @ArchTest
    ArchRule common不依赖任何业务模块 =
            noClasses().that().resideInAPackage("com.earlylearning.early_learning_server.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.earlylearning.early_learning_server.ai..",
                            "com.earlylearning.early_learning_server.identity..",
                            "com.earlylearning.early_learning_server.material..",
                            "com.earlylearning.early_learning_server.security..",
                            "com.earlylearning.early_learning_server.storage..");

    @Test
    void 源码里entity与model不import上层() {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String source = read(file);
            Matcher pkg = PACKAGE.matcher(source);
            if (!pkg.find() || !(isLayer(pkg.group(1), "entity") || isLayer(pkg.group(1), "model"))) {
                continue;
            }
            Matcher imports = PROJECT_IMPORT.matcher(source);
            while (imports.find()) {
                String imported = imports.group(1);
                String[] parts = imported.split("\\.");
                if (parts.length >= 2 && ABOVE_ENTITY_AND_MODEL.contains(parts[1])) {
                    violations.add(MAIN_SOURCES.relativize(file) + " → " + imported);
                }
            }
        }
        assertThat(violations).as("entity / model 源码引用了上层（含只在 Javadoc 中使用的 import）").isEmpty();
    }

    /** 返回类型及其泛型参数里不得出现给定包的类型（如 {@code ApiResponse<CloudFile>}）。 */
    private static ArchCondition<JavaMethod> notReturnTypesFrom(String packageIdentifier) {
        DescribedPredicate<JavaClass> inPackage = JavaClass.Predicates.resideInAPackage(packageIdentifier);
        return new ArchCondition<>("not return types from " + packageIdentifier) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (mentions(method.getReturnType(), inPackage)) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 返回了 " + packageIdentifier + " 里的类型"));
                }
            }
        };
    }

    private static boolean mentions(JavaType type, DescribedPredicate<JavaClass> predicate) {
        if (predicate.test(type.toErasure())) {
            return true;
        }
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                if (mentions(argument, predicate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code ai.model.scoring} 属于 model 层：模块名之后的第一段是层名。 */
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
