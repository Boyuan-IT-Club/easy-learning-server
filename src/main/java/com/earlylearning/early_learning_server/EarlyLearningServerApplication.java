package com.earlylearning.early_learning_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
@SpringBootApplication
// 扫描 @ConfigurationProperties 记录并做构造器绑定。缺了它，带构造参数的配置记录会被当成普通组件，
// Spring 会去自动装配构造参数（例如给 long 找一个 bean）从而启动失败。
@ConfigurationPropertiesScan
public class EarlyLearningServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EarlyLearningServerApplication.class, args);
	}

}
