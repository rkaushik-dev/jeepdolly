# jeepdolly

[![CI](https://github.com/rkaushik-dev/jeepdolly/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/jeepdolly/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Maven](https://img.shields.io/badge/build-Maven-C71A36)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Oversize and overweight highway permits for a state motor carrier office.

A hauler who cannot make a load legal by loading it differently comes here for
a permit. The office decides whether the combination can be moved at all, over
which roads, under what conditions, and for how much.

The name is the axle set a hauler drops between the tractor and a lowboy to
spread the weight, which is the thing this system spends most of its time
reasoning about.

Nothing here is legal advice and none of the tables ship as law. The rule
tables are data because they change by legislature and by district order.

## Tech stack

- Java 21, built with Maven (the wrapper pins Maven 3.9.16)
- Spring Boot 3.5 (Web MVC, Bean Validation) for the http service
- JPA with Hibernate over H2, the schema written by Flyway migrations
- picocli for the `jeepdolly` command line
- springdoc for the interface description
- JUnit 5 and AssertJ for the tests, Spotless (palantir-java-format) and a
  JaCoCo line coverage gate of 80% in the build

## Build, test, run

```
./mvnw -B verify                 # compile, format check, every test, coverage gate
./mvnw -B test -Dtest=PermitTest # one test class
./mvnw spotless:apply            # format the sources
```

`verify` leaves a runnable jar in `target/`:

```
java -jar target/jeepdolly-0.1.0.jar --help
java -jar target/jeepdolly-0.1.0.jar build          # migrate the schema, seed the tariff
java -jar target/jeepdolly-0.1.0.jar serve --port 8000
```

## Configuration

Everything configurable is read once, at start, from `JEEPDOLLY_*` environment
variables. Nothing else reads the environment.

| variable | default | what it sets |
| --- | --- | --- |
| `JEEPDOLLY_DATABASE_URL` | `jdbc:h2:file:./jeepdolly` | the database (a file in the working directory) |
| `JEEPDOLLY_OFFICE_NAME` | `Motor Carrier Permit Office` | the name on letters and sheets |
| `JEEPDOLLY_MINIMUM_MILES` | `10` | the shortest distance a permit is priced on |
| `JEEPDOLLY_REVIEW_DAYS` | `14` | the turnaround promised for an engineering study |
| `JEEPDOLLY_ECHO_SQL` | `false` | write every statement to the log |
| `JEEPDOLLY_FREE_COPIES` | `3` | copies a clerk may print before a supervisor is wanted |
| `JEEPDOLLY_RENEWAL_DAYS` | `30` | how far ahead the renewal list and letters look |
| `JEEPDOLLY_CITATION_GRACE` | `30` | days a citation may go unpaid before a carrier is held |
| `JEEPDOLLY_LINE_SURVEY_YEARS` | `5` | how often a low wire is walked |

`--database <url>` on the command line overrides the database for one command.
A password in the URL never reaches the log.

The schema is built by `jeepdolly build`, not by the service, which checks the
tables it finds against its mapping the first time it reaches the database. The
migrations live in `src/main/resources/db/migration`, and the stored formats are
unchanged, so an existing database loads.

## Command line

`jeepdolly [--database URL] [--quiet] <command>`. Every command prints plain
lines, one record to a line, the fields two spaces apart, so the output reads
for a person and pipes into grep and awk. Exit codes: `0` done, `1` a refusal
(its code and sentence on standard error, as in `over_limit: ...`), `2` the
command used wrongly. Anything else is a defect and comes out as a stack trace.

| command | what it does |
| --- | --- |
| `health` | say whether the database is reachable |
| `build` | migrate the schema and seed the published tables |
| `serve [--host H] [--port P]` | run the http service until it is stopped |
| `permits on-the-road / expiring / show / void / carrier` | the permits out on the road |
| `renewal due / letters / renew` | the annual renewal run |
| `citations owing / carrier / show / report / watch / file / pay` | enforcement tickets and the chase list |
| `pilots companies / cards / expiring / company / certify / suspend / available` | the pilot car register |
| `roads export / load / segment / district / corners / turn` | the road log and the corner card index |
| `wires list / owners / owner / add / measure / survey / clearance` | overhead lines |
| `report issuance / revenue / carrier / roads / standing` | the office's reports |
| `export federal / verify` | the monthly federal return and its check |
| `nightly` | the nightly sweep (`--dry-run` writes nothing) |

`jeepdolly <command> --help` describes each one.

## The http service

Every route but the service routes needs a key in the `X-Permit-Key` header.
The table holds only the key's SHA-256 hash, so a key is shown once, when it is
issued. Roles are a ladder: `reader` may look at anything, `clerk` files and
issues, `engineer` also signs studies, `supervisor` also voids, suspends and
edits the road log. A carrier's own key sees only that carrier's permits.

Every refusal comes back in one shape, `{"code": ..., "message": ..., "facts":
{...}}`: `not_on_file` is 404, `conflict` and `not_allowed` are 409, and
`unreadable`, `impossible`, `over_limit` and `blocked` are 422. A body that does
not fit the route's shape is a 422 whose `detail` lists where and what. Every
measure crosses as a string that reads the way the permit prints it
(`"14 ft 6 in"`, `"132,400 lb"`, `"$188.72"`).

The interface description is at `/openapi.json`, with a page at `/docs`.

| method | path | rung |
| --- | --- | --- |
| GET | `/health`, `/version`, `/metrics`, `/metrics.json` | none |
| GET | `/settings` | reader |
| POST | `/applications` | clerk |
| GET | `/applications`, `/applications/{reference}`, `/applications/{reference}/assessment` | reader |
| POST | `/applications/{reference}/submit`, `/pilots`, `/withdraw`, `/refuse` | clerk |
| POST | `/permits`, `/permits/walk-in` | clerk |
| GET | `/permits`, `/permits/{number}`, `/permits/expiring/soon`, `/permits/carrier/{usdot}` | reader |
| POST | `/permits/{number}/void` | supervisor |
| POST | `/permits/{number}/copies` | clerk |
| GET | `/permits/{number}/copies`, `/permits/copies/free-limit` | reader |
| POST | `/annual/eligibility` | reader |
| POST | `/annual` | clerk |
| GET | `/renewals`, `/renewals/letters` | reader |
| POST | `/renewals/{usdot}` | clerk |
| POST | `/routes/check`, `/routes/find`, `/routes/reachable` | reader |
| POST | `/carriers`, `/carriers/{usdot}/insurance`, `/carriers/{usdot}/bond` | clerk |
| GET | `/carriers`, `/carriers/{usdot}` | reader |
| POST | `/carriers/{usdot}/suspend`, `/carriers/{usdot}/restore` | supervisor |
| POST | `/citations`, `/citations/{number}/contest`, `/citations/{number}/payment` | clerk |
| GET | `/citations/next-number` | clerk |
| GET | `/citations/owing`, `/citations/carrier/{usdot}`, `/citations/{number}` | reader |
| POST | `/citations/{number}/uphold`, `/dismiss`, `/void` | supervisor |
| POST | `/receipts` | clerk |
| GET | `/receipts/drawer` | clerk |
| GET | `/receipts/owing/{number}`, `/receipts/outstanding`, `/receipts/carrier/{usdot}` | reader |
| GET | `/reports/issuance`, `/reports/revenue`, `/reports/roads` | reader |
| POST | `/restrictions` | supervisor |
| DELETE | `/restrictions/{ident}` | supervisor |
| GET | `/restrictions/on/{segment}` | reader |
| POST | `/seasons`, `/seasons/{number}/lift` | supervisor |
| GET | `/seasons`, `/seasons/{number}`, `/seasons/in-force/{region}` | reader |
| GET | `/structures/on/{segment}`, `/structures/posted`, `/structures/studies/waiting` | reader |
| POST | `/structures/{ident}/posting` | supervisor |
| POST | `/structures/studies/{reference}/assign`, `/clear`, `/refuse` | engineer |
| GET | `/lines`, `/lines/{ident}`, `/lines/owners`, `/lines/low`, `/lines/clearance` | reader |
| POST | `/lines`, `/lines/owners` | supervisor |
| PUT | `/lines/{ident}/measurement` | supervisor |
| GET | `/pilots/companies`, `/pilots/companies/{ident}`, `/pilots/cards`, `/pilots/cards/{card}`, `/pilots/expiring` | reader |
| POST | `/pilots/companies`, `/pilots/cards` | clerk |
| POST | `/pilots/companies/{ident}/suspension`, `/pilots/cards/{card}/suspension` | supervisor |
| GET | `/tariff/statutory`, `/tariff/statutory/{highway_class}` | reader |
| POST | `/tariff/statutory` | supervisor |

## Project layout

```
src/main/java/io/github/rkaushikdev/jeepdolly/
  (root)          refusals, measures, money, the office log, seasons, the travel calendar, settings
  domain/         axles, axle streams, envelopes, the road network, carriers, commodities
  limits/         statutory, size and bridge formula limits, and the permit ladder
  routing/        the corner card index, itineraries, mileage, the router and detours
  structures/     the structure inventory, ratings, span loading and the review queue
  escorts/        the pilot car register and the escort table
  enforcement/    penalties, citations and a carrier's standing
  conditions/     the condition library and condition assembly
  travel/         daylight and travel windows
  fees/           the fee schedule and pricing
  utilities/      overhead lines and the lift notice
  permits/        applications, assessment, issue, amendment, the register, reprints, annuals
  services/       the counter, renewals, the till, the nightly sweep, audit, metrics, notices
  reports/        the office's reports
  exports/        the monthly federal return
  persistence/    the database, the unit of work, rows, repositories and stores
  api/            the http service: the door, the error shape, the wire shapes, the routes
  cli/            the jeepdolly command line
src/main/resources/db/migration/   the Flyway migrations, V1 to V11
src/test/java/                     the tests, one class per subject, with shared fixtures in support/
```
