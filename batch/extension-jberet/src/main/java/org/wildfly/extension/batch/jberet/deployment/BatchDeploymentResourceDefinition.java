/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.batch.jberet.deployment;

import javax.batch.operations.JobExecutionAlreadyCompleteException;
import javax.batch.operations.JobExecutionNotMostRecentException;
import javax.batch.operations.JobExecutionNotRunningException;
import javax.batch.operations.JobOperator;
import javax.batch.operations.JobRestartException;
import javax.batch.operations.JobSecurityException;
import javax.batch.operations.JobStartException;
import javax.batch.operations.NoSuchJobExecutionException;
import java.util.Properties;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.annotations.Description;
import org.wildfly.annotations.Descriptions;
import org.wildfly.annotations.ResourceDescriptions;
import org.wildfly.annotations.ResourcePath;
import org.wildfly.extension.batch.jberet.BatchResourceDescriptionResolver;
import org.wildfly.extension.batch.jberet.BatchSubsystemDefinition;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@ResourceDescriptions(packageName = "org.wildfly.extension.batch.jberet")
@ResourcePath("batch.jberet.deployment")
@Description("Information about the batch subsystem for the deployment.")
public class BatchDeploymentResourceDefinition extends SimpleResourceDefinition {

    private static final ResourceDescriptionResolver DEFAULT_RESOLVER = BatchResourceDescriptionResolver.getResourceDescriptionResolver("deployment");

    private static final SimpleAttributeDefinition EXECUTION_ID = SimpleAttributeDefinitionBuilder.create("execution-id", ModelType.LONG, false)
            .setValidator(new LongRangeValidator(1L, false))
            .build();

    private static final SimpleAttributeDefinition JOB_XML_NAME = SimpleAttributeDefinitionBuilder.create("job-xml-name", ModelType.STRING, false)
            .build();

    private static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", ModelType.STRING, true)
            .build();

    @Descriptions({
            @Description("Starts a batch job."),
            @Description(name = "start-job.job-xml-name", value = "The name of the job XML file to use when starting the job."),
            @Description(name = "start-job.properties", value = "Optional properties to use when starting the batch job."),
    })
    private static final SimpleOperationDefinition START_JOB = new SimpleOperationDefinitionBuilder("start-job", DEFAULT_RESOLVER)
            .setParameters(JOB_XML_NAME, PROPERTIES)
            .setReplyType(ModelType.LONG)
            .setRuntimeOnly()
            .build();


    @Descriptions({
            @Description("Restarts a batch job. Only jobs in a STOPPED or FAILED state can be restarted."),
            @Description(name = "restart-job.execution-id", value = "The execution id of the job to restart. This must be the most recent job execution id."),
            @Description(name = "restart-job.properties", value = "Optional properties to use when restarting the batch job."),
    })
    private static final SimpleOperationDefinition RESTART_JOB = new SimpleOperationDefinitionBuilder("restart-job", DEFAULT_RESOLVER)
            .setParameters(EXECUTION_ID, PROPERTIES)
            .setReplyType(ModelType.LONG)
            .setRuntimeOnly()
            .build();


    @Descriptions({
            @Description("Stops a running batch job."),
            @Description(name = "stop-job.job-xml-name", value = "The execution id of the job to be stopped.")
    })
    private static final SimpleOperationDefinition STOP_JOB = new SimpleOperationDefinitionBuilder("stop-job", DEFAULT_RESOLVER)
            .setParameters(EXECUTION_ID)
            .setRuntimeOnly()
            .build();

    public BatchDeploymentResourceDefinition() {
        super(new Parameters(BatchSubsystemDefinition.SUBSYSTEM_PATH, DEFAULT_RESOLVER).setRuntime());
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(START_JOB, new JobOperationStepHandler() {
            @Override
            protected void execute(final OperationContext context, final ModelNode operation, final JobOperator jobOperator) throws OperationFailedException {
                // Resolve the job XML name
                final String jobName = resolveValue(context, operation, JOB_XML_NAME).asString();
                final Properties properties = resolvePropertyValue(context, operation, PROPERTIES);
                try {
                    final long executionId = jobOperator.start(jobName, properties);
                    context.getResult().set(executionId);
                } catch (JobStartException | JobSecurityException e) {
                    throw createOperationFailure(e);
                }
            }
        });

        resourceRegistration.registerOperationHandler(STOP_JOB, new JobOperationStepHandler() {
            @Override
            protected void execute(final OperationContext context, final ModelNode operation, final JobOperator jobOperator) throws OperationFailedException {
                // Resolve the execution id
                final long executionId = resolveValue(context, operation, EXECUTION_ID).asLong();
                try {
                    jobOperator.stop(executionId);
                } catch (NoSuchJobExecutionException | JobExecutionNotRunningException | JobSecurityException e) {
                    throw createOperationFailure(e);
                }
            }
        });

        resourceRegistration.registerOperationHandler(RESTART_JOB, new JobOperationStepHandler() {
            @Override
            protected void execute(final OperationContext context, final ModelNode operation, final JobOperator jobOperator) throws OperationFailedException {
                // Resolve the execution id
                final long executionId = resolveValue(context, operation, EXECUTION_ID).asLong();
                final Properties properties = resolvePropertyValue(context, operation, PROPERTIES);
                try {
                    final long newExecutionId = jobOperator.restart(executionId, properties);
                    context.getResult().set(newExecutionId);
                } catch (JobExecutionAlreadyCompleteException | NoSuchJobExecutionException | JobExecutionNotMostRecentException | JobRestartException | JobSecurityException e) {
                    throw createOperationFailure(e);
                }
            }
        });
    }
}
