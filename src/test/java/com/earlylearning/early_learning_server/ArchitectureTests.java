package com.earlylearning.early_learning_server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代码组织规范（AGENTS.md 第 3 节）的守护测试：违反即测试红。
 *
 * <pre>
 *   入口        controller/（HTTP）、filter/（Servlet 过滤器）
 *   请求与响应   dto/
 *   业务        service/（只放接口）、service/impl/（实现）
 *   数据与规则   顶层 entity/（只放表映射类）、common/enums/（字段取值的枚举）、模块内 model/（不落库的业务对象与规则）
 *   数据访问     mapper/
 *   外部适配     client/
 *   装配        config/
 * </pre>
 *
 * <ol>
 *   <li>模块边界：模块之间不许成环；跨模块只能用 {@link #CROSS_MODULE_API} 里列出的包，common、entity 可被任何模块使用。</li>
 *   <li>表归属：entity 集中存放，但每个实体的 Mapper 只能出现在一个模块里，别的模块经该模块的 service 读写。</li>
 *   <li>层间方向（ArchUnit，覆盖全部模块）：入口 → service → mapper / client；entity、model 不依赖任何上层。</li>
 *   <li>源码 import：只在 Javadoc 里出现的 import 不进字节码，ArchUnit 看不到，这里直接扫源码。</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.earlylearning.early_learning_server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    private static final String BASE = "com.earlylearning.early_learning_server";
    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/earlylearning/early_learning_server");
    private static final Pattern PACKAGE = Pattern.compile("^package com\\.earlylearning\\.early_learning_server\\.([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern PROJECT_IMPORT = Pattern.compile("^import com\\.earlylearning\\.early_learning_server\\.([\\w.]+);", Pattern.MULTILINE);
    /** 层 = 模块名之后的第一段（{@code ai.model.scoring} 属于 model 层，{@code material.model.config} 不是 config 层）。 */
    private static final String LAYER = "com.earlylearning.early_learning_server.*.";
    private static final String L_CONTROLLER = LAYER + "controller..";
    private static final String L_FILTER = LAYER + "filter..";
    private static final String L_DTO = LAYER + "dto..";
    private static final String L_SERVICE = LAYER + "service..";
    private static final String L_SERVICE_IMPL = LAYER + "service..impl";
    /** 实体不在模块里：顶层 entity 包，被所有模块共享。 */
    private static final String L_ENTITY = BASE + ".entity..";
    private static final String L_MODEL = LAYER + "model..";
    private static final String L_MAPPER = LAYER + "mapper..";
    private static final String L_CLIENT = LAYER + "client..";
    private static final String L_CONFIG = LAYER + "config..";

    private static final Set<String> ABOVE_ENTITY_AND_MODEL =
            Set.of("controller", "filter", "dto", "service", "mapper", "client", "config");
    /** 不属于任何业务模块、可被所有模块使用的顶层包。 */
    private static final Set<String> SHARED = Set.of("common", "entity");
    private static final String[] BUSINESS_MODULES = {
            BASE + ".ai..", BASE + ".identity..", BASE + ".material..", BASE + ".security..", BASE + ".storage.."};

    /**
     * 跨模块可用的包（白名单）。默认只开放 service；其余每一项都要说明谁在用。
     * 新增一项 = 新增一条模块间依赖，评审时要看得见。
     */
    static final Set<String> CROSS_MODULE_API = Set.of(
            "ai.service.rubric",   // material 发布时校验评分目录
            "storage.service",     // ai 取评分图片、material 上传包内素材
            "storage.model",       // 同上：IncomingFile、ObjectStorageService
            "security.service",    // identity：签发 Token、实现 BearerAuthenticator
            "security.model");     // identity 与各模块 Controller：已认证身份类型

    @ArchTest
    ArchRule 模块之间不成环 =
            slices().matching("com.earlylearning.early_learning_server.(*)..").should().beFreeOfCycles();

    @ArchTest
    ArchRule 跨模块只用白名单里的包 =
            classes().that().resideInAPackage("com.earlylearning.early_learning_server.*..")
                    .should(onlyUseOtherModulesThrough(CROSS_MODULE_API));

    @ArchTest
    ArchRule entity包里只放表映射类 =
            classes().that().resideInAPackage(L_ENTITY).and().areTopLevelClasses()
                    .should().beAnnotatedWith(TableName.class);

    @ArchTest
    ArchRule entity只依赖common =
            noClasses().that().resideInAPackage(L_ENTITY)
                    .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES)
                    .orShould().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    ArchRule model不依赖任何上层与SpringWeb =
            noClasses().that().resideInAPackage(L_MODEL)
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
    ArchRule service包里只放接口 =
            classes().that().resideInAPackage(L_SERVICE).and().resideOutsideOfPackage(L_SERVICE_IMPL)
                    .and().areTopLevelClasses()
                    .should().beInterfaces();

    @ArchTest
    ArchRule impl包里只放service接口的实现 =
            classes().that().resideInAPackage(L_SERVICE_IMPL).and().areTopLevelClasses()
                    .should().beMetaAnnotatedWith(Component.class)
                    .andShould(implementAServiceInterface());

    @ArchTest
    ArchRule 只依赖Service接口不依赖实现 =
            noClasses().that().resideOutsideOfPackage(L_SERVICE_IMPL)
                    .should().dependOnClassesThat().resideInAPackage(L_SERVICE_IMPL);

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
    ArchRule common不依赖任何业务模块与entity =
            noClasses().that().resideInAPackage(BASE + ".common..")
                    .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES)
                    .orShould().dependOnClassesThat().resideInAPackage(L_ENTITY);

    /** 实体集中存放后，表归属靠 Mapper 守住：同一个实体的 BaseMapper 只能出现在一个模块。 */
    @ArchTest
    static void 每张表只归一个模块(JavaClasses classes) {
        Map<String, Set<String>> owners = new TreeMap<>();
        for (JavaClass mapper : classes.that(JavaClass.Predicates.resideInAPackage(L_MAPPER))) {
            for (Type type : mapper.reflect().getGenericInterfaces()) {
                if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == BaseMapper.class
                        && parameterized.getActualTypeArguments()[0] instanceof Class<?> entity) {
                    owners.computeIfAbsent(entity.getSimpleName(), k -> new TreeSet<>()).add(module(mapper.getPackageName()));
                }
            }
        }
        assertThat(owners).as("实体 → 声明了它的 Mapper 的模块").isNotEmpty()
                .allSatisfy((entity, modules) -> assertThat(modules).as(entity).hasSize(1));
    }

    @Test
    void 源码里entity_enums与model不import上层() {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String source = read(file);
            Matcher pkg = PACKAGE.matcher(source);
            if (!pkg.find()) {
                continue;
            }
            String top = pkg.group(1).split("\\.")[0];
            boolean entity = top.equals("entity");
            if (!entity && !isLayer(pkg.group(1), "model")) {
                continue;
            }
            Matcher imports = PROJECT_IMPORT.matcher(source);
            while (imports.find()) {
                String imported = imports.group(1);
                String[] parts = imported.split("\\.");
                boolean upward = entity
                        ? !SHARED.contains(parts[0])
                        : parts.length >= 2 && ABOVE_ENTITY_AND_MODEL.contains(parts[1]);
                if (upward) {
                    violations.add(MAIN_SOURCES.relativize(file) + " → " + imported);
                }
            }
        }
        assertThat(violations).as("entity / enums / model 源码引用了上层（含只在 Javadoc 中使用的 import）").isEmpty();
    }

    private static ArchCondition<JavaClass> onlyUseOtherModulesThrough(Set<String> allowed) {
        return new ArchCondition<>("only use other modules through " + allowed) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = module(origin.getPackageName());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getBaseComponentType().getPackageName();
                    String to = module(targetPackage);
                    if (to == null || from == null || to.equals(from) || SHARED.contains(to)) {
                        continue;
                    }
                    String relative = targetPackage.substring(BASE.length() + 1);
                    boolean ok = allowed.stream().anyMatch(api -> relative.equals(api) || relative.startsWith(api + "."));
                    if (!ok) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /**
     * impl 里的类必须实现某个 service 接口；{@code a.service.impl.XxxServiceImpl} 必须实现 {@code a.service.XxxService}。
     * 其它实现（如实现 security 的 {@code BearerAuthenticator}）只要求实现的是 service 包里的接口。
     */
    private static ArchCondition<JavaClass> implementAServiceInterface() {
        return new ArchCondition<>("implement a service interface") {
            @Override
            public void check(JavaClass impl, ConditionEvents events) {
                if (impl.getSimpleName().endsWith("ServiceImpl")) {
                    String parent = impl.getPackageName().substring(0, impl.getPackageName().length() - ".impl".length());
                    String expected = parent + "." + impl.getSimpleName().replaceFirst("Impl$", "");
                    if (impl.getRawInterfaces().stream().noneMatch(i -> i.getName().equals(expected))) {
                        events.add(SimpleConditionEvent.violated(impl, impl.getName() + " 没有实现 " + expected));
                    }
                    return;
                }
                boolean ok = impl.getRawInterfaces().stream().anyMatch(i -> isLayer(
                        i.getPackageName().substring(BASE.length() + 1), "service") && !i.getPackageName().endsWith(".impl"));
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(impl, impl.getName() + " 没有实现任何 service 接口"));
                }
            }
        };
    }

    /** 项目内的包所属模块（主包的直接子包名）；项目外或主包本身返回 null。 */
    private static String module(String packageName) {
        if (!packageName.startsWith(BASE + ".")) {
            return null;
        }
        return packageName.substring(BASE.length() + 1).split("\\.")[0];
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
