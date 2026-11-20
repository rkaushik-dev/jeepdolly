package io.github.rkaushikdev.jeepdolly.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.rkaushikdev.jeepdolly.Valued;
import java.io.IOException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * How the service reads and writes JSON, in one place so the application and the tests of its
 * shapes cannot drift apart.
 *
 * <p>Field names are snake case. A field nobody declared is ignored. Dates are ISO strings. The
 * office's enumerations go out as the short strings it reads, the same strings the database
 * stores. Every measure crosses as a string, so a number sent where a string belongs is refused
 * rather than quietly turned into one, and a fraction sent where a whole number belongs is refused
 * rather than cut down.
 */
public final class Json {

    private Json() {}

    /** Apply the service's JSON rules to a builder: the service's own, or a test's. */
    public static void configure(Jackson2ObjectMapperBuilder builder) {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToEnable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .modulesToInstall(module())
                .postConfigurer(Json::strictText);
    }

    /** A mapper built the way the service builds its own. */
    public static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        configure(builder);
        return builder.build();
    }

    private static void strictText(ObjectMapper mapper) {
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    /** Every enumeration that has a short value goes out as that value. */
    static SimpleModule module() {
        SimpleModule module = new SimpleModule("jeepdolly");
        module.addSerializer(Valued.class, new JsonSerializer<Valued>() {
            @Override
            public void serialize(Valued member, JsonGenerator out, SerializerProvider provider) throws IOException {
                out.writeString(member.value());
            }
        });
        return module;
    }
}
