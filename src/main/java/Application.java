import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.*;
import org.xmlunit.xpath.JAXPXPathEngine;
import org.xmlunit.xpath.XPathEngine;
import picocli.CommandLine;

import javax.xml.transform.Source;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

enum DifferenceType {
    DIFFERENT,
    IGNORED,
    NUMERIC_NON_NEGLIGIBLE,
    NUMERIC_NEGLIGIBLE,
}

class Difference {
    DifferenceType type;
    String message;

    public Difference(DifferenceType type, String message) {
        this.type = type;
        this.message = message;
    }
}

class JsonContent {
    boolean same;
    List<String> different;
    List<String> ignored;
    List<String> numeric_negligible;
    List<String> numeric_non_negligible;

    public JsonContent() {
        same = false;
        different = new ArrayList<>();
        ignored = new ArrayList<>();
        numeric_negligible = new ArrayList<>();
        numeric_non_negligible = new ArrayList<>();
    }
}

@CommandLine.Command(name = "xmldiff", mixinStandardHelpOptions = true, version = "checksum 4.0",
        description = "xmldiff")
class Application implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The control file")
    String control;

    @CommandLine.Parameters(index = "1", description = "The test file")
    String test;

    @CommandLine.Option(names = {"--ignore"}, description = "xpaths ignored from comparison")
    List<String> ignored_xpaths = new ArrayList<>();

    @CommandLine.Option(names = {"--numeric"}, description = "xpaths with numeric tolerance")
    List<String> numeric_xpaths = new ArrayList<>();

    @CommandLine.Option(names = {"--tolerance"}, description = "numeric tolerance 0.01 = 1%")
    Double tolerance = 0.01; // 1%

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        int exitCode = new CommandLine(new Application()).execute(args);
        System.exit(exitCode);
    }

    private static Set<String> getXPaths(List<String> xpaths, Source control, XPathEngine xPathEngine) {
        return xpaths.stream().flatMap(
                (ignored_xpath) ->
                        StreamSupport.stream(xPathEngine.selectNodes(ignored_xpath, control).spliterator(), false).map(
                                Application::getXPath
                        )
        ).collect(Collectors.toSet());
    }

    private static String getXPath(Node node) {
        Node parent = node.getParentNode();
        if (parent == null) {
            return node.getNodeName();
        }
        return getXPath(parent) + "/" + node.getNodeName();
    }

    private static String formatComparison(Comparison comparison) {
        return "found: (" +
                comparison.getTestDetails().getValue().toString() +
                ") expected: (" +
                comparison.getControlDetails().getValue().toString() +
                ") at: " +
                comparison.getControlDetails().getXPath();
    }

    // let's reinvent the wheel...
    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static DifferenceType compareMaybeNumeric(String left, String right, double tolerance) {
        if (isNumeric(left) && isNumeric(right)) {
            double left_double = Double.parseDouble(left);
            double right_double = Double.parseDouble(right);
            if (Math.abs(left_double - right_double) < (tolerance * right_double)) {
                return DifferenceType.NUMERIC_NEGLIGIBLE;
            } else {
                return DifferenceType.NUMERIC_NON_NEGLIGIBLE;
            }
        }
        return DifferenceType.DIFFERENT;
    }

    private static Stream<Difference> findDifferences(Source control, Source test, XPathEngine xPathEngine, Set<String> ignored_paths, Set<String> numeric_paths, Double tolerance) {
        List<Difference> differences = new ArrayList<>();
        DiffBuilder
                .compare(control)
                .withTest(test)
                .checkForIdentical()
                .normalizeWhitespace()
                .ignoreComments()
                .ignoreWhitespace()
                .withComparisonListeners(
                        (comparison, outcome) -> {
                            if (comparison.getType() == ComparisonType.TEXT_VALUE && outcome == ComparisonResult.DIFFERENT) {
                                Iterable<Node> differing_nodes = xPathEngine.selectNodes(comparison.getControlDetails().getParentXPath(), control);
                                DifferenceType differenceType = DifferenceType.DIFFERENT;
                                if (StreamSupport.stream(differing_nodes.spliterator(), false).anyMatch((node) -> ignored_paths.contains(getXPath(node)))) {
                                    differenceType = DifferenceType.IGNORED;
                                }
                                if (StreamSupport.stream(differing_nodes.spliterator(), false).anyMatch((node) -> numeric_paths.contains(getXPath(node)))) {
                                    differenceType = compareMaybeNumeric(comparison.getTestDetails().getTarget().getTextContent(), comparison.getControlDetails().getTarget().getTextContent(), tolerance);
                                }
                                differences.add(new Difference(differenceType, formatComparison(comparison)));
                            }
                        }
                ).build();
        return differences.stream();
    }

    private static JsonContent toJson(Stream<Difference> differences) {
        JsonContent jsonContent = new JsonContent();
        differences.forEach((difference -> {
            switch (difference.type) {
                case IGNORED:
                    jsonContent.ignored.add(difference.message);
                    break;
                case NUMERIC_NEGLIGIBLE:
                    jsonContent.numeric_negligible.add(difference.message);
                    break;
                case NUMERIC_NON_NEGLIGIBLE:
                    jsonContent.numeric_non_negligible.add(difference.message);
                    break;
                default:
                    jsonContent.different.add(difference.message);
                    break;
            }
        }));
        jsonContent.same = jsonContent.different.isEmpty();
        return jsonContent;
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        Path control_path = Paths.get(control);
        Path test_path = Paths.get(test);
        if (!Files.exists(control_path)) {
            System.out.println(control_path + " not found");
            return 1;
        }
        if (!Files.exists(test_path)) {
            System.out.println(test_path + " not found");
            return 1;
        }
        // both files exist
        Source control = Input.fromFile(control_path.toFile()).build();
        Source test = Input.fromFile(test_path.toFile()).build();
        XPathEngine xPathEngine = new JAXPXPathEngine();
        Set<String> ignored_paths = getXPaths(ignored_xpaths, control, xPathEngine);
        Set<String> numeric_paths = getXPaths(numeric_xpaths, control, xPathEngine);
        Stream<Difference> differences = findDifferences(control, test, xPathEngine, ignored_paths, numeric_paths, tolerance);
        JsonContent jsonContent = toJson(differences);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(jsonContent));
        return 0;
    }
}
