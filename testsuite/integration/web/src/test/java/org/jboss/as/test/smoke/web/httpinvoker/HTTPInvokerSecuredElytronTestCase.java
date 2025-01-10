/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.smoke.web.httpinvoker;

import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Assertions;

public class HTTPInvokerSecuredElytronTestCase extends HTTPInvokerSecuredTestCase {

    @Override
    protected void validateOperation(ModelNode operationResult) {
        Assertions.assertEquals("application-http-authentication", operationResult.get("http-authentication-factory").asString(), "The http-authentication-factory should be set");
        Assertions.assertFalse(operationResult.get("security-realm").isDefined(),
            "The security-realm should be undefined");
    }
}
