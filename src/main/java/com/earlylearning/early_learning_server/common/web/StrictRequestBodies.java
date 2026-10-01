package com.earlylearning.early_learning_server.common.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 让 {@link RejectUnknownFields} 生效：Jackson 遇到未知字段时先问问题处理器，再看全局开关，
 * 所以这里对带注解的类型抛出异常，其余类型交回默认行为（忽略）。
 *
 * <p>异常被 Spring 转成 HttpMessageNotReadableException，由全局异常处理返回 400 INVALID_REQUEST。
 */
@Configuration(proxyBeanMethods = false)
public class StrictRequestBodies {

    @Bean
    JsonMapperBuilderCustomizer rejectUnknownFieldsOnAnnotatedTypes() {
        return builder -> builder.addHandler(new Handler());
    }

    static final class Handler extends DeserializationProblemHandler {

        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                                             ValueDeserializer<?> deserializer, Object beanOrClass,
                                             String propertyName) {
            Class<?> type = beanOrClass instanceof Class<?> c ? c : beanOrClass.getClass();
            if (type.isAnnotationPresent(RejectUnknownFields.class)) {
                throw UnrecognizedPropertyException.from(p, beanOrClass, propertyName,
                        deserializer.getKnownPropertyNames());
            }
            return false;
        }
    }
}
