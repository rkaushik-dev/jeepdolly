package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.metamodel.EntityType;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The project itself: what the build declares, and whether every class loads.
 *
 * <p>The loading test is the one that earns its place. A class whose static set up fails is a
 * command or a route that fails the first time somebody reaches it, which turns up three weeks later
 * on the server. Loading every class once, in one test, catches it the day it is written.
 */
class ProjectTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    /** An exact release: numbers and dots, nothing that a resolver could read as a range. */
    private static final Pattern EXACT = Pattern.compile("\\d+(\\.\\d+)+");

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    /** What the formatter the build runs wraps code at. */
    private static final int LINE_LENGTH = 120;

    private static Element project;
    private static Map<String, String> properties;

    @BeforeAll
    static void readTheBuildFile() throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom =
                factory.newDocumentBuilder().parse(ROOT.resolve("pom.xml").toFile());
        project = pom.getDocumentElement();
        properties = new HashMap<>();
        child(project, "properties").ifPresent(found -> {
            for (Element one : children(found)) {
                properties.put(one.getTagName(), one.getTextContent().strip());
            }
        });
        properties.put("project.version", text(project, "version"));
    }

    private static List<Element> children(Element parent) {
        List<Element> found = new ArrayList<>();
        NodeList all = parent.getChildNodes();
        for (int at = 0; at < all.getLength(); at++) {
            if (all.item(at).getNodeType() == Node.ELEMENT_NODE) {
                found.add((Element) all.item(at));
            }
        }
        return found;
    }

    private static Optional<Element> child(Element parent, String tag) {
        return children(parent).stream()
                .filter(one -> one.getTagName().equals(tag))
                .findFirst();
    }

    private static String text(Element parent, String tag) {
        return child(parent, tag).map(one -> one.getTextContent().strip()).orElse("");
    }

    /** A value with every {@code ${name}} replaced by the property it names. */
    private static String resolved(String value) {
        Matcher reference = PROPERTY.matcher(value);
        StringBuilder out = new StringBuilder();
        while (reference.find()) {
            String named = properties.get(reference.group(1));
            assertThat(named).as("property %s", reference.group(1)).isNotNull();
            reference.appendReplacement(out, Matcher.quoteReplacement(named));
        }
        reference.appendTail(out);
        return out.toString();
    }

    /** Every element with this tag anywhere in the build file. */
    private static List<Element> everywhere(String tag) {
        List<Element> found = new ArrayList<>();
        NodeList all = project.getElementsByTagName(tag);
        for (int at = 0; at < all.getLength(); at++) {
            found.add((Element) all.item(at));
        }
        return found;
    }

    private static Path compiledClasses() throws URISyntaxException {
        return Path.of(Jeepdolly.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    @Test
    void everyModuleInThePackageImportsOnItsOwn() throws IOException, URISyntaxException, ClassNotFoundException {
        Path classes = compiledClasses();
        List<String> names;
        try (Stream<Path> walked = Files.walk(classes)) {
            names = walked.filter(Files::isRegularFile)
                    .map(one -> classes.relativize(one).toString())
                    .filter(one -> one.endsWith(".class") && !one.endsWith("module-info.class"))
                    .map(one ->
                            one.substring(0, one.length() - ".class".length()).replace('/', '.'))
                    .sorted()
                    .toList();
        }
        int loaded = 0;
        for (String name : names) {
            assertThat(Class.forName(name, true, ProjectTest.class.getClassLoader()))
                    .isNotNull();
            loaded++;
        }
        assertThat(names).allMatch(one -> one.startsWith("io.github.rkaushikdev.jeepdolly."));
        assertThat(loaded).isGreaterThan(50);
    }

    @Test
    void thePackageDeclaresTheVersionTheCodeCarries() {
        assertThat(text(project, "version")).isEqualTo(Jeepdolly.VERSION);
        assertThat(text(project, "artifactId")).isEqualTo(Jeepdolly.NAME);
    }

    @Test
    void theConsoleEntryPointResolvesToSomethingCallable() throws ClassNotFoundException, NoSuchMethodException {
        Element boot = everywhere("plugin").stream()
                .filter(one -> text(one, "artifactId").equals("spring-boot-maven-plugin"))
                .findFirst()
                .orElseThrow();
        String where = resolved(text(child(boot, "configuration").orElseThrow(), "mainClass"));
        Method main = Class.forName(where).getMethod("main", String[].class);
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void thePinsAreExactSoTwoInstallsAreTheSame() throws IOException {
        Element parent = child(project, "parent").orElseThrow();
        assertThat(text(parent, "version")).matches(EXACT);
        List<String> pinned = new ArrayList<>();
        for (String tag : List.of("dependency", "plugin")) {
            for (Element one : everywhere(tag)) {
                String version = text(one, "version");
                if (!version.isEmpty()) {
                    String exact = resolved(version);
                    assertThat(exact).as(text(one, "artifactId")).matches(EXACT);
                    pinned.add(text(one, "artifactId"));
                }
            }
        }
        assertThat(pinned).contains("picocli", "spotless-maven-plugin", "jacoco-maven-plugin");
        String wrapper =
                Files.readString(ROOT.resolve(".mvn/wrapper/maven-wrapper.properties"), StandardCharsets.UTF_8);
        assertThat(wrapper)
                .containsPattern("apache-maven/\\d+\\.\\d+\\.\\d+/apache-maven-\\d+\\.\\d+\\.\\d+-bin\\.zip");
    }

    @Test
    void theRuntimeDoesNotDependOnTheTestTools() {
        List<String> tools = new ArrayList<>();
        for (Element one : everywhere("dependency")) {
            String artifact = text(one, "artifactId");
            if (artifact.matches(".*(junit|assertj|mockito|-test)$|^(junit.*|assertj.*|mockito.*)")) {
                assertThat(text(one, "scope")).as(artifact).isEqualTo("test");
                tools.add(artifact);
            }
        }
        assertThat(tools).contains("spring-boot-starter-test");
    }

    @Test
    void everyTableIsReachableFromTheModelsPackage() {
        List<Class<?>> mapped = Databases.empty().entityManagerFactory().getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .<Class<?>>map(one -> one)
                .filter(one -> !one.getSimpleName().equals("SampleRow"))
                .toList();
        assertThat(mapped).hasSizeGreaterThan(30);
        assertThat(mapped)
                .allMatch(one -> one.getPackageName().equals("io.github.rkaushikdev.jeepdolly.persistence.models"))
                .allMatch(one -> one.getSimpleName().endsWith("Row"));
    }

    @Test
    void theLineLengthIsTheOneTheOfficeReadsAt() throws IOException {
        Element spotless = everywhere("plugin").stream()
                .filter(one -> text(one, "artifactId").equals("spotless-maven-plugin"))
                .findFirst()
                .orElseThrow();
        Element java =
                child(child(spotless, "configuration").orElseThrow(), "java").orElseThrow();
        assertThat(child(java, "palantirJavaFormat")).isPresent();
        List<String> tooLong = new ArrayList<>();
        int read = 0;
        try (Stream<Path> walked = Files.walk(ROOT.resolve("src/main/java"))) {
            for (Path source : walked.filter(Files::isRegularFile)
                    .filter(one -> one.toString().endsWith(".java"))
                    .toList()) {
                read++;
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int at = 0; at < lines.size(); at++) {
                    if (lines.get(at).length() > LINE_LENGTH) {
                        tooLong.add(ROOT.relativize(source) + ":" + (at + 1));
                    }
                }
            }
        }
        assertThat(read).isGreaterThan(100);
        assertThat(tooLong).isEmpty();
    }

    @Test
    void theTestsAreInTheFolderTheConfigurationNames() throws IOException {
        Element build = child(project, "build").orElseThrow();
        assertThat(text(build, "testSourceDirectory")).isEmpty();
        Path tests = ROOT.resolve("src/test/java");
        assertThat(tests).isDirectory();
        long found;
        try (Stream<Path> walked = Files.walk(tests)) {
            found = walked.filter(Files::isRegularFile)
                    .filter(one -> one.getFileName().toString().endsWith("Test.java"))
                    .count();
        }
        assertThat(found).isGreaterThan(40);
    }
}
