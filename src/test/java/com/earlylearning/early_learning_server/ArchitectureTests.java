package com.earlylearning.early_learning_server;

import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 代码组织规范（{@code reference/adr/0008}）的守护测试：违反即测试红。
 *
 * <p>第一条是 Spring Modulith 的模块边界验证：模块（主包的直接子包）之间只能引用对方根包暴露的类型，
 * 不许伸进内部子包，也不许成环。common 暂以共享模块对待（见主类上的 {@code @Modulithic}）。
 *
 * <p>后两条是模块内方向，覆盖全部四层化模块（common 按设计是共享模块，无四层）。
 */
@AnalyzeClasses(packages = "com.earlylearning.early_learning_server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    private static final String ROOT = "com.earlylearning.early_learning_server";

    @ArchTest
    void 模块间只走对方根包暴露的API且不成环(JavaClasses classes) {
        ApplicationModules.of(EarlyLearningServerApplication.class).verify();
    }

    /*
     * 两条层内规则按层名通配，而不是逐个列模块：新加的四层模块自动受约束，不必记得回来改这里。
     * 跨模块的 domain → domain 引用（如 teacher.domain 用 auth.domain.TokenPair）不受限，
     * 那是模块边界的事，由上面的 Modulith 验证负责。
     */
    @ArchTest
    ArchRule domain不依赖模块内外层与SpringWeb =
            noClasses().that().resideInAPackage(ROOT + "..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            ROOT + "..interfaces..", ROOT + "..application..", ROOT + "..infrastructure..",
                            "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    ArchRule application与infrastructure不依赖interfaces =
            noClasses().that().resideInAnyPackage(ROOT + "..application..", ROOT + "..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + "..interfaces..");
}
