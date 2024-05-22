package org.wildfly.test.integration.observability.micrometer;

import static org.wildfly.test.integration.observability.setuptask.AbstractSetupTask.executeOp;
import static org.wildfly.test.integration.observability.setuptask.PrometheusSetupTask.PROMETHEUS_CONTEXT;
import static org.wildfly.test.integration.observability.setuptask.PrometheusSetupTask.PROMETHEUS_REGISTRY_ADDRESS;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testcontainers.api.DockerRequired;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.CdiUtils;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.util.AssumeTestGroupUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.test.integration.observability.setuptask.MicrometerSetupTask;
import org.wildfly.test.integration.observability.setuptask.PrometheusSetupTask;

@RunWith(Arquillian.class)
@ServerSetup({MicrometerSetupTask.class, PrometheusSetupTask.class})
@DockerRequired(AssumptionViolatedException.class)
@RunAsClient
public class MicrometerPrometheusTestCase {
    private static final int REQUEST_COUNT = 5;


    @ArquillianResource
    private URL url;

    @ContainerResource
    protected ManagementClient managementClient;

    // The @ServerSetup(MicrometerSetupTask.class) requires Docker to be available.
    // Otherwise the org.wildfly.extension.micrometer.registry.NoOpRegistry is installed which will result in 0 counters,
    // and cause the test fail seemingly intermittently on machines with broken Docker setup.
    @BeforeClass
    public static void checkForDocker() {
        AssumeTestGroupUtil.assumeDockerAvailable();
    }

    @Deployment
    public static Archive<?> deploy() {
        return ShrinkWrap.create(WebArchive.class, "micrometer-prometheus.war")
                .addClasses(MicrometerApplication.class, MicrometerResource.class)
                .addAsWebInfResource(CdiUtils.createBeansXml(), "beans.xml");
    }

    @Test
    public void basicPrometheusTest() throws Exception {
        makeRequests();

        String metrics = fetchPrometheusMetrics(false);
        Assert.assertTrue("'demo_counter_total " + REQUEST_COUNT + "' is expected",
                metrics.contains("demo_counter_total " + REQUEST_COUNT));
        Assert.assertTrue("'demo_timer_seconds_count' is expected",
                metrics.contains("demo_timer_seconds_count"));
    }

    @Test
    public void securedPrometheusTest() throws Exception {
        setPrometheusSecurity(true);
        makeRequests();

        String metrics = fetchPrometheusMetrics(false);
        Assert.assertTrue("'401 - Unauthorized' message is expected", metrics.contains("401 - Unauthorized"));

        metrics = fetchPrometheusMetrics(true);
        Assert.assertTrue("'demo_counter_total " + REQUEST_COUNT + "' is expected",
                metrics.contains("demo_counter_total " + REQUEST_COUNT));

        setPrometheusSecurity(false);
        makeRequests();
        metrics = fetchPrometheusMetrics(false);
        Assert.assertTrue("'demo_counter_total " + REQUEST_COUNT + "' is expected",
                metrics.contains("demo_counter_total " + REQUEST_COUNT));
    }

    private void setPrometheusSecurity(boolean enabled) throws Exception {
        executeOp(managementClient,
                Operations.createWriteAttributeOperation(PROMETHEUS_REGISTRY_ADDRESS, "security-enabled",
                        enabled));
        ServerReload.reloadIfRequired(managementClient);
    }

    private void makeRequests() throws URISyntaxException {
        try (Client client = ClientBuilder.newClient()) {
            WebTarget target = client.target(url.toURI());
            for (int i = 0; i < REQUEST_COUNT; i++) {
                target.request().get();
            }
        }
    }

    private String fetchPrometheusMetrics(boolean authenticate) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpClientContext hcContext = HttpClientContext.create();

            if (authenticate) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials("testSuite", "testSuitePassword"));
                hcContext.setCredentialsProvider(credentialsProvider);
            }

            CloseableHttpResponse resp = client.execute(
                    new HttpGet("http://" + managementClient.getMgmtAddress() + ":" +
                            managementClient.getMgmtPort() + PROMETHEUS_CONTEXT), hcContext);
            return EntityUtils.toString(resp.getEntity());
        }
    }
}
