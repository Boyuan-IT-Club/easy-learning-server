package com.earlylearning.early_learning_server.common.secret;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CrockfordCodeTests {

    @RepeatedTest(20)
    void 生成的码总能被解析回来() {
        String code = CrockfordCode.generate(16);
        assertThat(code).hasSize(16).matches("[0-9A-HJKMNP-TV-Z]+");
        assertThat(CrockfordCode.parse(CrockfordCode.format(code), 16)).contains(code);
    }

    @Test
    void 容忍小写_空白_连字符与易混字符() {
        String code = CrockfordCode.generate(8);
        String messy = " " + code.toLowerCase().substring(0, 4) + " - " + code.substring(4) + " ";
        assertThat(CrockfordCode.parse(messy, 8)).contains(code);

        String withZero = "0" + CrockfordCode.generate(8).substring(1);
        // O 当 0：只要校验位按 0 算得出，写成 O 也能解析
        String payload = withZero.substring(0, 7);
        String valid = payload + CrockfordCode.checkChar(payload);
        assertThat(CrockfordCode.parse("O" + valid.substring(1), 8)).contains(valid);
    }

    @Test
    void 单字符抄错会被校验位拦下() {
        String code = CrockfordCode.generate(16);
        char original = code.charAt(5);
        char replacement = original == 'A' ? 'B' : 'A';
        String typo = code.substring(0, 5) + replacement + code.substring(6);
        assertThat(CrockfordCode.parse(typo, 16)).isEmpty();
    }

    @Test
    void 长度不符或非法字符为空() {
        assertThat(CrockfordCode.parse("ABC", 8)).isEmpty();
        assertThat(CrockfordCode.parse("UUUUUUUU", 8)).isEmpty();
        assertThat(CrockfordCode.parse(null, 8)).isEmpty();
    }

    @Test
    void 格式化每4位一个连字符() {
        assertThat(CrockfordCode.format("7K2QM9XD")).isEqualTo("7K2Q-M9XD");
    }
}
