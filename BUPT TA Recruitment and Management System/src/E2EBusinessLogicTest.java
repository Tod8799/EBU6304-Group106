/**
 * Simple wrapper around the {@link FeatureRegressionTest} suite,
 * intended to be run as an end-to-end smoke test.
 * <p>
 * Since all business logic is already covered by the feature regression tests,
 * this class delegates to that suite.
 * </p>
 */
public class E2EBusinessLogicTest {
    /**
     * Runs the feature regression tests as an E2E workflow check.
     * @param args not used
     * @throws Exception if any test fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Running end-to-end business flow checks...");
        FeatureRegressionTest.main(args);
    }
}