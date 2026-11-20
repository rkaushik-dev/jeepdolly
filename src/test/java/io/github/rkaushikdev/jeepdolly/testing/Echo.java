package io.github.rkaushikdev.jeepdolly.testing;

import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicantIn;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicationIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Routes that read a body or a query and say back what they read, to test how requests are read. */
@RestController
public class Echo {

    @PostMapping("/echo/application")
    public Map<String, Object> application(@Valid @RequestBody ApplicationIn body) {
        return Map.of("usdot", body.usdot(), "axles", body.axles().size(), "notes", body.notes());
    }

    @PostMapping("/echo/applicant")
    public ApplicantIn applicant(@Valid @RequestBody ApplicantIn body) {
        return body;
    }

    @GetMapping("/echo/days")
    public Map<String, Object> days(@RequestParam @Min(1) @Max(90) int days, @RequestParam LocalDate day) {
        return Map.of("days", days, "day", day);
    }
}
