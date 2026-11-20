package io.github.rkaushikdev.jeepdolly.testing;

import io.github.rkaushikdev.jeepdolly.Blocked;
import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.OverLimit;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A route that raises one refusal of each kind, so the error shape can be checked without a real one. */
@RestController
public class Raiser {

    @GetMapping("/testing/{code}")
    public Map<String, String> raiser(@PathVariable String code) {
        throw switch (code) {
            case "not_on_file" -> new NotOnFile("no carrier with usdot 9999", Facts.of("usdot", 9999));
            case "conflict" -> new Conflict("already on file");
            case "impossible" -> new Impossible("the overhangs are longer than the load");
            case "over_limit" -> new OverLimit("the tandem is 4,000 lb over", Facts.of("over", 4000));
            case "blocked" -> new Blocked("US 2 is closed", Facts.of("segment", "US2-1"));
            case "not_allowed" -> new NotAllowed("that permit is void");
            default -> new IllegalArgumentException("no refusal called " + code);
        };
    }
}
