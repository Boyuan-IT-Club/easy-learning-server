package com.earlylearning.early_learning_server.common.code;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeValidationTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @MethodSource("validCodes")
    void allFiveCodesAcceptOpenValuesAndDatabaseLengthBoundary(String value) {
        var codes = codes(value);
        assertTrue(validator.validate(codes).isEmpty());
        assertEquals(value, codes.file_code());
        assertEquals(value, codes.grammar_code());
        assertEquals(value, codes.entry_code());
        assertEquals(value, codes.official_course_code());
        assertEquals(value, codes.official_material_code());
    }

    static Stream<String> validCodes() {
        return Stream.of("x", "FILE_IMG_1", "mixed-Case/词语:α.1", " Code ",
                "x".repeat(64), "字".repeat(64), "😀".repeat(64), "😀".repeat(32) + "x".repeat(32));
    }

    @ParameterizedTest
    @MethodSource("invalidCodes")
    void allFiveCodesRejectBlankAndOversizedValues(String value) {
        var violations = validator.validate(codes(value));
        assertEquals(5, violations.size());
        assertEquals(Set.of(FileCode.class, GrammarCode.class, EntryCode.class,
                        OfficialCourseCode.class, OfficialMaterialCode.class),
                violations.stream().map(v -> v.getConstraintDescriptor().getAnnotation().annotationType())
                        .collect(Collectors.toSet()));
    }

    static Stream<String> invalidCodes() {
        return Stream.of("", " ", "\t\r\n", "\u3000", "x".repeat(65), "字".repeat(65),
                "😀".repeat(65), "😀".repeat(33) + "x".repeat(32));
    }

    @Test
    void optionalReferencesAcceptNullAndRequiredReferencesUseNotNull() {
        assertTrue(validator.validate(codes(null)).isEmpty());
        var violations = validator.validate(new RequiredFile(null));
        assertEquals(1, violations.size());
        assertEquals(NotNull.class, violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType());
    }

    @Test
    void collectionElementsUseTheSameConstraint() {
        var violations = validator.validate(new GrammarReferences(Arrays.asList("GRAMMAR_1", "", null)));
        assertEquals(2, violations.size());
        assertTrue(validator.validate(new GrammarReferences(List.of("GRAMMAR_1", "grammar_1"))).isEmpty());
    }

    @Test
    void composedConstraintPreservesGroupsAndCustomMessage() {
        var request = new GroupedFile("");
        assertTrue(validator.validate(request).isEmpty());
        var violations = validator.validate(request, Create.class);
        assertEquals(1, violations.size());
        assertEquals("invalid file code", violations.iterator().next().getMessage());
        assertEquals(FileCode.class, violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType());
    }

    @Test
    void methodParameterCanBeValidated() throws NoSuchMethodException {
        var endpoint = new FileLookup();
        var method = FileLookup.class.getMethod("find", String.class);
        assertEquals(1, validator.forExecutables().validateParameters(endpoint, method, new Object[]{""}).size());
        assertTrue(validator.forExecutables().validateParameters(endpoint, method, new Object[]{"file_1"}).isEmpty());
    }

    @Test
    void jsonRemainsStringsAndPreservesExactValues() {
        var mapper = JsonMapper.builder().build();
        var original = new Codes("File_A", "grammar_b", "词语/1", " Course_1 ", null);
        String json = mapper.writeValueAsString(original);
        assertEquals("File_A", mapper.readTree(json).get("file_code").asString());
        assertEquals(" Course_1 ", mapper.readTree(json).get("official_course_code").asString());
        assertEquals(original, mapper.readValue(json, Codes.class));
    }

    private static Codes codes(String value) {
        return new Codes(value, value, value, value, value);
    }

    record Codes(@FileCode String file_code,
                 @GrammarCode String grammar_code,
                 @EntryCode String entry_code,
                 @OfficialCourseCode String official_course_code,
                 @OfficialMaterialCode String official_material_code) {
    }

    record RequiredFile(@NotNull @FileCode String code) {
    }

    record GrammarReferences(List<@NotNull @GrammarCode String> codes) {
    }

    record GroupedFile(@FileCode(groups = Create.class, message = "invalid file code") String code) {
    }

    interface Create {
    }

    public static class FileLookup {
        public void find(@FileCode String code) {
        }
    }
}
