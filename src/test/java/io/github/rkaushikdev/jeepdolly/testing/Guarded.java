package io.github.rkaushikdev.jeepdolly.testing;

import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes that need each rung of the ladder and answer who got in, so the door can be tested without
 * a real route behind it. Lives outside the service's own package, so only a test that imports it
 * sees it.
 */
@RestController
public class Guarded {

    @GetMapping("/guarded/reader")
    @Requires(Role.READER)
    public Map<String, String> reader(Caller who) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("person", who.person());
        body.put("role", who.role().value());
        return body;
    }

    @GetMapping("/guarded/clerk")
    @Requires(Role.CLERK)
    public Map<String, String> clerk(Caller who) {
        return Map.of("person", who.person());
    }

    @GetMapping("/guarded/supervisor")
    @Requires(Role.SUPERVISOR)
    public Map<String, String> supervisor(Caller who) {
        return Map.of("person", who.person());
    }

    @GetMapping("/guarded/whoami")
    @Requires(Role.READER)
    public Map<String, Object> whoami(Caller who) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("person", who.person());
        body.put("usdot", who.usdot().orElse(null));
        body.put("carrier", who.isCarrier());
        return body;
    }
}
