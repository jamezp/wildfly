/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.component;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.ejb3.logging.EjbLogger.ROOT_LOGGER;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import jakarta.ejb.EJBHome;
import jakarta.ejb.EJBLocalHome;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.TransactionManagementType;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.security.jacc.EJBRoleRefPermission;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.jboss.as.ee.component.BasicComponent;
import org.jboss.as.ee.component.ComponentView;
import org.jboss.as.ee.component.interceptors.InvocationType;
import org.jboss.as.ejb3.component.allowedmethods.AllowedMethodsInformation;
import org.jboss.as.ejb3.component.interceptors.ShutDownInterceptorFactory;
import org.jboss.as.ejb3.component.invocationmetrics.InvocationMetrics;
import org.jboss.as.ejb3.context.CurrentInvocationContext;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.security.EJBSecurityMetaData;
import org.jboss.as.ejb3.security.JaccInterceptor;
import org.jboss.as.ejb3.subsystem.EJBStatistics;
import org.jboss.as.ejb3.suspend.EJBSuspendHandlerService;
import org.jboss.as.ejb3.timerservice.spi.ManagedTimerService;
import org.jboss.as.ejb3.timerservice.spi.ManagedTimerServiceFactory;
import org.jboss.as.ejb3.tx.ApplicationExceptionDetails;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.context.NamespaceContextSelector;
import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.as.server.suspend.ServerActivityCallback;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBHomeLocator;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.jboss.metadata.ejb.spec.MethodInterfaceType;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.extension.requestcontroller.ControlPoint;
import org.wildfly.security.auth.principal.AnonymousPrincipal;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.authz.jacc.PolicyUtil;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.transaction.client.ContextTransactionManager;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class EJBComponent extends BasicComponent implements ServerActivityCallback {

    private static final ApplicationExceptionDetails APPLICATION_EXCEPTION = new ApplicationExceptionDetails("java.lang.Exception", true, false);

    private final Map<MethodTransactionAttributeKey, TransactionAttributeType> txAttrs;
    private final Map<MethodTransactionAttributeKey, Integer> txTimeouts;
    private final Map<MethodTransactionAttributeKey, Boolean> txExplicitAttrs;

    private final boolean isBeanManagedTransaction;
    private final Map<Class<?>, ApplicationExceptionDetails> applicationExceptions;
    private final EJBSecurityMetaData securityMetaData;
    private final Map<String, ServiceName> viewServices;
    private final ServiceName ejbLocalHomeViewServiceName;
    private final ServiceName ejbHomeViewServiceName;
    private final ServiceName ejbObjectViewServiceName;
    private final ServiceName ejbLocalObjectViewServiceName;

    private final ManagedTimerServiceFactory timerServiceFactory;
    private final Map<Method, InterceptorFactory> timeoutInterceptors;
    private final Method timeoutMethod;
    private final String applicationName;
    private final String earApplicationName;
    private final String moduleName;
    private final String distinctName;

    private final InvocationMetrics invocationMetrics = new InvocationMetrics();
    private final EJBSuspendHandlerService ejbSuspendHandlerService;
    private final ShutDownInterceptorFactory shutDownInterceptorFactory;
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
    private final UserTransaction userTransaction;
    private final ControlPoint controlPoint;
    private final AtomicBoolean exceptionLoggingEnabled;

    private final SecurityDomain securityDomain;
    private final boolean enableJacc;
    private ThreadLocal<SecurityIdentity> incomingRunAsIdentity;
    private final Function<SecurityIdentity, Set<SecurityIdentity>> identityOutflowFunction;
    private final boolean securityRequired;
    private final EJBComponentDescription componentDescription;
    private final boolean legacyCompliantPrincipalPropagation;

    private volatile ManagedTimerService timerService;

    /**
     * Construct a new instance.
     *
     * @param ejbComponentCreateService the component configuration
     */
    protected EJBComponent(final EJBComponentCreateService ejbComponentCreateService) {
        super(ejbComponentCreateService);
        this.applicationExceptions = Collections.unmodifiableMap(ejbComponentCreateService.getApplicationExceptions().getApplicationExceptions());
        final Map<MethodTransactionAttributeKey, TransactionAttributeType> txAttrs = ejbComponentCreateService.getTxAttrs();
        if (txAttrs == null || txAttrs.isEmpty()) {
            this.txAttrs = Collections.emptyMap();
            this.txExplicitAttrs = Collections.emptyMap();
        } else {
            this.txAttrs = txAttrs;
            this.txExplicitAttrs = ejbComponentCreateService.getExplicitTxAttrs();
        }
        final Map<MethodTransactionAttributeKey, Integer> txTimeouts = ejbComponentCreateService.getTxTimeouts();
        if (txTimeouts == null || txTimeouts.isEmpty()) {
            this.txTimeouts = Collections.emptyMap();
        } else {
            this.txTimeouts = txTimeouts;
        }
        isBeanManagedTransaction = TransactionManagementType.BEAN.equals(ejbComponentCreateService.getTransactionManagementType());

        // security metadata
        this.securityMetaData = ejbComponentCreateService.getSecurityMetaData();
        this.viewServices = ejbComponentCreateService.getViewServices();
        this.timerServiceFactory = ejbComponentCreateService.getTimerServiceFactory();
        this.timeoutMethod = ejbComponentCreateService.getTimeoutMethod();
        this.ejbLocalHomeViewServiceName = ejbComponentCreateService.getEjbLocalHome();
        this.ejbHomeViewServiceName = ejbComponentCreateService.getEjbHome();
        this.applicationName = ejbComponentCreateService.getApplicationName();
        this.earApplicationName = ejbComponentCreateService.getEarApplicationName();
        this.distinctName = ejbComponentCreateService.getDistinctName();
        this.moduleName = ejbComponentCreateService.getModuleName();
        this.ejbObjectViewServiceName = ejbComponentCreateService.getEjbObject();
        this.ejbLocalObjectViewServiceName = ejbComponentCreateService.getEjbLocalObject();

        this.timeoutInterceptors = Collections.unmodifiableMap(ejbComponentCreateService.getTimeoutInterceptors());
        this.shutDownInterceptorFactory = ejbComponentCreateService.getShutDownInterceptorFactory();
        this.ejbSuspendHandlerService = ejbComponentCreateService.getEJBSuspendHandler();
        this.transactionSynchronizationRegistry = ejbComponentCreateService.getTransactionSynchronizationRegistry();
        this.userTransaction = ejbComponentCreateService.getUserTransaction();
        this.controlPoint = ejbComponentCreateService.getControlPoint();
        this.exceptionLoggingEnabled = ejbComponentCreateService.getExceptionLoggingEnabled();

        this.securityDomain = ejbComponentCreateService.getSecurityDomain();
        this.enableJacc = ejbComponentCreateService.isEnableJacc();
        this.legacyCompliantPrincipalPropagation = ejbComponentCreateService.isLegacyCompliantPrincipalPropagation();
        this.incomingRunAsIdentity = new ThreadLocal<>();
        this.identityOutflowFunction = ejbComponentCreateService.getIdentityOutflowFunction();
        this.securityRequired = ejbComponentCreateService.isSecurityRequired();
        this.componentDescription = ejbComponentCreateService.getComponentDescription();
    }

    protected <T> T createViewInstanceProxy(final Class<T> viewInterface, final Map<Object, Object> contextData) {
        if (viewInterface == null)
            throw EjbLogger.ROOT_LOGGER.viewInterfaceCannotBeNull();
        if (viewServices.containsKey(viewInterface.getName())) {
            final ServiceName serviceName = viewServices.get(viewInterface.getName());
            return createViewInstanceProxy(viewInterface, contextData, serviceName);
        } else {
            throw EjbLogger.ROOT_LOGGER.viewNotFound(viewInterface.getName(), this.getComponentName());
        }
    }

    protected <T> T createViewInstanceProxy(final Class<T> viewInterface, final Map<Object, Object> contextData, final ServiceName serviceName) {
        final ServiceController<?> serviceController = currentServiceContainer().getRequiredService(serviceName);
        final ComponentView view = (ComponentView) serviceController.getValue();
        final ManagedReference instance;
        try {
            if(WildFlySecurityManager.isChecking()) {
                instance = WildFlySecurityManager.doUnchecked(new PrivilegedExceptionAction<ManagedReference>() {
                    @Override
                    public ManagedReference run() throws Exception {
                        return view.createInstance(contextData);
                    }
                });
            } else {
                instance = view.createInstance(contextData);
            }
        } catch (Exception e) {
            //TODO: do we need to let the exception propagate here?
            throw new RuntimeException(e);
        }
        return viewInterface.cast(instance.getInstance());
    }

    private static ServiceContainer currentServiceContainer() {
        if(System.getSecurityManager() == null) {
            return CurrentServiceContainer.getServiceContainer();
        }
        return AccessController.doPrivileged(CurrentServiceContainer.GET_ACTION);
    }

    public ApplicationExceptionDetails getApplicationException(Class<?> exceptionClass, Method invokedMethod) {
        ApplicationExceptionDetails applicationException = this.applicationExceptions.get(exceptionClass);
        if (applicationException != null) {
            return applicationException;
        }
        // Check if the super class of the passed exception class, is an application exception.
        Class<?> superClass = exceptionClass.getSuperclass();
        while (superClass != null && !(superClass.equals(Exception.class) || superClass.equals(Object.class))) {
            applicationException = this.applicationExceptions.get(superClass);
            // check whether the "inherited" attribute is set. A subclass of an application exception
            // is an application exception only if the inherited attribute on the parent application exception
            // is set to true.
            if (applicationException != null) {
                if (applicationException.isInherited()) {
                    return applicationException;
                }
                // Once we find a super class which is an application exception,
                // we just stop there (no need to check the grand super class), irrespective of whether the "inherited"
                // is true or false
                return null; // not an application exception, so return null
            }
            // move to next super class
            superClass = superClass.getSuperclass();
        }
        // AS7-1317: examine the throws clause of the method
        // An unchecked-exception is only an application exception if annotated (or described) as such.
        // (see Enterprise Beans 3.1 FR 14.2.1)
        if (RuntimeException.class.isAssignableFrom(exceptionClass) || Error.class.isAssignableFrom(exceptionClass))
            return null;
        if (invokedMethod != null) {
            final Class<?>[] exceptionTypes = invokedMethod.getExceptionTypes();
            for (Class<?> type : exceptionTypes) {
                if (type.isAssignableFrom(exceptionClass))
                    return APPLICATION_EXCEPTION;
            }
        }
        // not an application exception, so return null.
        return null;
    }

    public Principal getCallerPrincipal() {
        if (isSecurityDomainKnown()) {
            return getCallerSecurityIdentity().getPrincipal();
        }
        return new AnonymousPrincipal();
    }

    public SecurityIdentity getIncomingRunAsIdentity() {
        return incomingRunAsIdentity.get();
    }

    public void setIncomingRunAsIdentity(SecurityIdentity identity) {
        if (identity == null) {
            incomingRunAsIdentity.remove();
        } else {
            incomingRunAsIdentity.set(identity);
        }
    }

    protected TransactionAttributeType getCurrentTransactionAttribute() {

        final InterceptorContext invocation = CurrentInvocationContext.get();
        final MethodInterfaceType methodIntf = MethodIntfHelper.of(invocation);
        return getTransactionAttributeType(methodIntf, invocation.getMethod());
    }

    public EJBHome getEJBHome() throws IllegalStateException {
        if (ejbHomeViewServiceName == null) {
            throw EjbLogger.ROOT_LOGGER.beanHomeInterfaceIsNull(getComponentName());
        }
        final ServiceController<?> serviceController = currentServiceContainer().getRequiredService(ejbHomeViewServiceName);
        final ComponentView view = (ComponentView) serviceController.getValue();
        final String locatorAppName = earApplicationName == null ? "" : earApplicationName;
        return EJBClient.createProxy(createHomeLocator(view.getViewClass().asSubclass(EJBHome.class), locatorAppName, moduleName, getComponentName(), distinctName));
    }

    private static <T extends EJBHome> EJBHomeLocator<T> createHomeLocator(Class<T> viewClass, String appName, String moduleName, String beanName, String distinctName) {
        return new EJBHomeLocator<T>(viewClass, appName, moduleName, beanName, distinctName, Affinity.LOCAL);
    }

    public Class<?> getEjbObjectType() {
        if (ejbObjectViewServiceName == null) {
            return null;
        }
        final ServiceController<?> serviceController = currentServiceContainer().getRequiredService(ejbObjectViewServiceName);
        final ComponentView view = (ComponentView) serviceController.getValue();
        return view.getViewClass();
    }

    public Class<?> getEjbLocalObjectType() {
        if (ejbLocalObjectViewServiceName == null) {
            return null;
        }
        final ServiceController<?> serviceController = currentServiceContainer().getRequiredService(ejbLocalObjectViewServiceName);
        final ComponentView view = (ComponentView) serviceController.getValue();
        return view.getViewClass();
    }

    public EJBLocalHome getEJBLocalHome() throws IllegalStateException {
        if (ejbLocalHomeViewServiceName == null) {
            throw EjbLogger.ROOT_LOGGER.beanLocalHomeInterfaceIsNull(getComponentName());
        }
        return createViewInstanceProxy(EJBLocalHome.class, Collections.emptyMap(), ejbLocalHomeViewServiceName);
    }

    public boolean getRollbackOnly() throws IllegalStateException {
        if (isBeanManagedTransaction()) {
            throw EjbLogger.ROOT_LOGGER.failToCallgetRollbackOnly();
        }
        try {
            TransactionManager tm = this.getTransactionManager();

            // The getRollbackOnly method should be used only in the context of a transaction.
            if (tm.getTransaction() == null) {
                throw EjbLogger.ROOT_LOGGER.failToCallgetRollbackOnlyOnNoneTransaction();
            }

            // EJBTHREE-805, consider an asynchronous rollback due to timeout
            // This is counter to Enterprise Beans 3.1 where an asynchronous call does not inherit the transaction context!

            int status = tm.getStatus();
            EjbLogger.ROOT_LOGGER.tracef("Current transaction status is %d", status);
            switch (status) {
                case Status.STATUS_COMMITTED:
                case Status.STATUS_ROLLEDBACK:
                    throw EjbLogger.ROOT_LOGGER.failToCallgetRollbackOnlyAfterTxcompleted();
                case Status.STATUS_MARKED_ROLLBACK:
                case Status.STATUS_ROLLING_BACK:
                    return true;
            }
            return false;
        } catch (SystemException se) {
            EjbLogger.ROOT_LOGGER.getTxManagerStatusFailed(se);
            return true;
        }
    }

    public ManagedTimerService getTimerService() {
        return this.timerService;
    }

    public TransactionAttributeType getTransactionAttributeType(final MethodInterfaceType methodIntf, final Method method) {
        return getTransactionAttributeType(methodIntf, MethodIdentifier.getIdentifierForMethod(method));
    }

    public TransactionAttributeType getTransactionAttributeType(final MethodInterfaceType methodIntf, final MethodIdentifier method) {
        return getTransactionAttributeType(methodIntf, method, TransactionAttributeType.REQUIRED);
    }

    public TransactionAttributeType getTransactionAttributeType(final MethodInterfaceType methodIntf, final MethodIdentifier method, TransactionAttributeType defaultType) {
        TransactionAttributeType txAttr = txAttrs.get(new MethodTransactionAttributeKey(methodIntf, method));
        //fall back to type bean if not found
        if (txAttr == null && methodIntf != MethodInterfaceType.Bean) {
            txAttr = txAttrs.get(new MethodTransactionAttributeKey(MethodInterfaceType.Bean, method));
        }
        if (txAttr == null)
            return defaultType;
        return txAttr;
    }

    public boolean isTransactionAttributeTypeExplicit(final MethodInterfaceType methodIntf, final MethodIdentifier method) {
        Boolean txAttr = txExplicitAttrs.get(new MethodTransactionAttributeKey(methodIntf, method));
        //fall back to type bean if not found
        if (txAttr == null && methodIntf != MethodInterfaceType.Bean) {
            txAttr = txExplicitAttrs.get(new MethodTransactionAttributeKey(MethodInterfaceType.Bean, method));
        }
        if (txAttr == null)
            return false;
        return txAttr;
    }

    /**
     * @deprecated Use {@link ContextTransactionManager#getInstance()} instead.
     * @return the value of {@link ContextTransactionManager#getInstance()}
     */
    @Deprecated
    public TransactionManager getTransactionManager() {
        return ContextTransactionManager.getInstance();
    }

    public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
        return transactionSynchronizationRegistry;
    }

    public int getTransactionTimeout(final MethodInterfaceType methodIntf, final Method method) {
        return getTransactionTimeout(methodIntf, MethodIdentifier.getIdentifierForMethod(method));
    }

    public int getTransactionTimeout(final MethodInterfaceType methodIntf, final MethodIdentifier method) {
        Integer txTimeout = txTimeouts.get(new MethodTransactionAttributeKey(methodIntf, method));
        if (txTimeout == null && methodIntf != MethodInterfaceType.Bean) {
            txTimeout = txTimeouts.get(new MethodTransactionAttributeKey(MethodInterfaceType.Bean, method));
        }
        if (txTimeout == null)
            return -1;

        return txTimeout;
    }

    public UserTransaction getUserTransaction() throws IllegalStateException {
        return this.userTransaction;
    }

    public boolean isBeanManagedTransaction() {
        return isBeanManagedTransaction;
    }

    public boolean isCallerInRole(final String roleName) throws IllegalStateException {
        if (isSecurityDomainKnown()) {
            if (enableJacc) {
                PolicyUtil policyUtil = WildFlySecurityManager.isChecking() ? doPrivileged((PrivilegedAction<PolicyUtil>) PolicyUtil::getPolicyUtil) : PolicyUtil.getPolicyUtil();
                ProtectionDomain domain = new ProtectionDomain(null, null, null, JaccInterceptor.getGrantedRoles(getCallerSecurityIdentity()));
                return policyUtil.implies(domain, new EJBRoleRefPermission(getComponentName(), roleName));
            } else {
                boolean tmpBool = checkCallerSecurityIdentityRole(roleName); // rls debug todo remove
                if (ROOT_LOGGER.isTraceEnabled()) {
                    ROOT_LOGGER.trace("## EJBComponent isCallerInRole checkCallerSecurityIdentityRole() returned: "
                    + tmpBool);
                }
                return tmpBool;
            }
        }
        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.trace("## EJBComponent isCallerInRole No security, no role membership");
        }
        // No security, no role membership.
        return false;
    }

    public boolean isStatisticsEnabled() {
        return EJBStatistics.getInstance().isEnabled();
    }

    public Object lookup(String name) throws IllegalArgumentException {
        if (name == null) {
            throw EjbLogger.ROOT_LOGGER.jndiNameCannotBeNull();
        }
        final NamespaceContextSelector namespaceContextSelector = NamespaceContextSelector.getCurrentSelector();
        if (namespaceContextSelector == null) {
            throw EjbLogger.ROOT_LOGGER.noNamespaceContextSelectorAvailable(name);
        }
        Context jndiContext = null;
        String namespaceStrippedJndiName = name;
        // get the appropriate JNDI context and strip the lookup jndi name of the component namespace prefix
        if (name.startsWith("java:app/")) {
            jndiContext = namespaceContextSelector.getContext("app");
            namespaceStrippedJndiName = name.substring("java:app/".length());
        } else if (name.startsWith("java:module/")) {
            jndiContext = namespaceContextSelector.getContext("module");
            namespaceStrippedJndiName = name.substring("java:module/".length());
        } else if (name.startsWith("java:comp/")) {
            jndiContext = namespaceContextSelector.getContext("comp");
            namespaceStrippedJndiName = name.substring("java:comp/".length());
        } else if (!name.startsWith("java:")) { // if it *doesn't* start with java: prefix, then default it to java:comp
            jndiContext = namespaceContextSelector.getContext("comp");
            // no need to strip the name since it doesn't start with java: prefix.
            // Also prefix the "env/" to the jndi name, since a lookup without a java: namespace prefix is effectively
            // a lookup under java:comp/env/<jndi-name>
            namespaceStrippedJndiName = "env/" + name;
        } else if (name.startsWith("java:global/")) {
            // Do *not* strip the jndi name of the prefix because java:global is a global context and doesn't specifically
            // belong to the component's ENC, and hence *isn't* a component ENC relative name and has to be looked up
            // with the absolute name (including the java:global prefix)
            try {
                jndiContext = new InitialContext();
            } catch (NamingException ne) {
                throw EjbLogger.ROOT_LOGGER.failToLookupJNDI(name, ne);
            }
        } else {
            throw EjbLogger.ROOT_LOGGER.failToLookupJNDINameSpace(name);
        }
        EjbLogger.ROOT_LOGGER.debugf("Looking up %s in jndi context: %s", namespaceStrippedJndiName, jndiContext);
        try {
            return jndiContext.lookup(namespaceStrippedJndiName);
        } catch (NamingException ne) {
            throw EjbLogger.ROOT_LOGGER.failToLookupStrippedJNDI(namespaceContextSelector, jndiContext, ne);
        }
    }

    public void setRollbackOnly() throws IllegalStateException {
        if (isBeanManagedTransaction()) {
            throw EjbLogger.ROOT_LOGGER.failToCallSetRollbackOnlyOnNoneCMB();
        }
        try {
            // get the transaction manager
            TransactionManager tm = getTransactionManager();
            // check if there's a tx in progress. If not, then it's an error to call setRollbackOnly()
            if (tm.getTransaction() == null) {
                throw EjbLogger.ROOT_LOGGER.failToCallSetRollbackOnlyWithNoTx();
            }
            // set rollback
            tm.setRollbackOnly();
        } catch (SystemException se) {
            EjbLogger.ROOT_LOGGER.setRollbackOnlyFailed(se);
        }
    }

    public EJBSecurityMetaData getSecurityMetaData() {
        return this.securityMetaData;
    }

    public Method getTimeoutMethod() {
        return timeoutMethod;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEarApplicationName() {
        return this.earApplicationName;
    }

    public String getDistinctName() {
        return distinctName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public ServiceName getEjbLocalObjectViewServiceName() {
        return ejbLocalObjectViewServiceName;
    }

    public ServiceName getEjbObjectViewServiceName() {
        return ejbObjectViewServiceName;
    }

    public Map<Method, InterceptorFactory> getTimeoutInterceptors() {
        return timeoutInterceptors;
    }

    public AllowedMethodsInformation getAllowedMethodsInformation() {
        return isBeanManagedTransaction() ? AllowedMethodsInformation.INSTANCE_BMT : AllowedMethodsInformation.INSTANCE_CMT;
    }

    public InvocationMetrics getInvocationMetrics() {
        return invocationMetrics;
    }

    public ControlPoint getControlPoint() {
        return this.controlPoint;
    }

    public SecurityDomain getSecurityDomain() {
        return securityDomain;
    }

    public boolean isSecurityDomainKnown() {
        return securityDomain != null;
    }

    public Function<SecurityIdentity, Set<SecurityIdentity>> getIdentityOutflowFunction() {
        return identityOutflowFunction;
    }

    @Override
    public synchronized void init() {
        getShutDownInterceptorFactory().start();
        super.init();

        this.timerService = this.timerServiceFactory.createTimerService(this);
        this.timerService.start();
    }

    @Override
    public final void stop() {
        getShutDownInterceptorFactory().shutdown();
        this.timerService.stop();
        this.done();
        this.timerService.close();
    }

    @Override
    public void done() {
        super.stop();
    }


    public boolean isExceptionLoggingEnabled() {
        return exceptionLoggingEnabled.get();
    }


    protected ShutDownInterceptorFactory getShutDownInterceptorFactory() {
        return shutDownInterceptorFactory;
    }

    private boolean checkCallerSecurityIdentityRole(String roleName) {
        final SecurityIdentity identity = getCallerSecurityIdentity();
        if("**".equals(roleName)) {
            return !identity.isAnonymous();
        }
        Roles roles = identity.getRoles("ejb", true);
        if(roles != null) {
            if(roles.contains(roleName)) {
                if (ROOT_LOGGER.isTraceEnabled()) {
                    ROOT_LOGGER.trace("## EJBComponent checkCallerSecurityIdentityRole  roleName: " + roleName
                            + "   found in identity.roles   principal: " + identity.getPrincipal().getName());
                }
                return true;
            }
            if(securityMetaData.getSecurityRoleLinks() != null) {
                Collection<String> linked = securityMetaData.getSecurityRoleLinks().get(roleName);
                if(linked != null) {
                    for (String role : roles) {
                        if (linked.contains(role)) {
                            if (ROOT_LOGGER.isTraceEnabled()) {
                                ROOT_LOGGER.trace("## EJBComponent checkCallerSecurityIdentityRole roleName: "
                                        + roleName + "  found in securityMetaData.getSecurityRoleLinks(),"
                                        + "  runAsPrincipal: " + securityMetaData.getRunAsPrincipal());
                            }
                            return true;
                        }
                    }
                }
            }
        }
        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.trace("## EJBComponent checkCallerSecurityIdentityRole  roleName: "
                    + roleName + "  no role found    Principal: " + identity.getPrincipal().getName());
        }
        return false;
    }

    private SecurityIdentity getCallerSecurityIdentity() {
        InvocationType invocationType = CurrentInvocationContext.get().getPrivateData(InvocationType.class);
        boolean isRemote = invocationType != null && invocationType.equals(InvocationType.REMOTE);
        if (legacyCompliantPrincipalPropagation && !isRemote) {
            return (getIncomingRunAsIdentity() == null) ? securityDomain.getCurrentSecurityIdentity() : getIncomingRunAsIdentity();
        } else {
            if (getIncomingRunAsIdentity() != null) {
                return getIncomingRunAsIdentity();
            } else if (securityRequired) {
                return securityDomain.getCurrentSecurityIdentity();
            } else {
                // unsecured Jakarta Enterprise Beans
                return securityDomain.getAnonymousSecurityIdentity();
            }
        }
    }

    public EJBSuspendHandlerService getEjbSuspendHandlerService() {
        return this.ejbSuspendHandlerService;
    }

    public EJBComponentDescription getComponentDescription() {
        return componentDescription;
    }
}
