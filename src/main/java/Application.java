import org.xmlunit.builder.Input;
import org.xmlunit.diff.*;
import picocli.CommandLine;

import javax.xml.transform.Source;

class DiffArgs {
    @CommandLine.Parameters(index = "0", description = "The control file")
    String control;

    @CommandLine.Parameters(index = "1", description = "The test file")
    String test;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested;
}


public class Application {
    public static void main(String[] args) {
        DiffArgs diffArgs = new DiffArgs();
        try {
            new CommandLine(diffArgs).parseArgs(args);
        } catch (CommandLine.ParameterException e) {
            System.out.println(e.getCommandLine() + " failed");
        }

        System.out.println("starting diff");
        Source control = Input.fromFile(diffArgs.control).build();
        Source test = Input.fromFile(diffArgs.test).build();
        DifferenceEngine diff = new DOMDifferenceEngine();
        diff.addDifferenceListener((comparison, outcome) -> {
            if (comparison.getType() == ComparisonType.TEXT_VALUE) {
                System.out.println(comparison.getControlDetails().getValue());
                System.out.println(comparison.getTestDetails().getValue());
                System.out.println("found a difference: " + comparison);
            }
        });
        diff.compare(control, test);
    }
}
