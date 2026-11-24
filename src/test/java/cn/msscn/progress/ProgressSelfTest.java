package cn.msscn.progress;

/**
 * Entry point for the dependency-free test suite.
 *
 * <p>Run via scripts/run-tests.sh or scripts/run-tests.ps1, or manually:</p>
 * <pre>
 * mvn -q clean test-compile
 * java -cp target/classes:target/test-classes cn.msscn.progress.ProgressSelfTest
 * </pre>
 */
public final class ProgressSelfTest {

    private ProgressSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        long started = System.nanoTime();
        System.out.println("[suite] api coverage");
        ApiCoverageTest.runAll();
        System.out.println("[suite] exceptions and invalid arguments");
        ExceptionTest.runAll();
        System.out.println("[suite] concurrency and throttling");
        ConcurrencyTest.runAll();
        System.out.println("[suite] host application integration");
        IntegrationTest.runAll();
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        System.out.println("All " + TestSupport.checks + " checks passed in "
                + elapsedMillis + " ms.");
    }
}
