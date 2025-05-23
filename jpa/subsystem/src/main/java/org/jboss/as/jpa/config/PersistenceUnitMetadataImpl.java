/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jpa.config;

import static org.jboss.as.jpa.messages.JpaLogger.ROOT_LOGGER;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

import org.jipijapa.plugin.spi.PersistenceUnitMetadata;
import org.jipijapa.plugin.spi.TempClassLoaderFactory;

/**
 * Represents the persistence unit definition
 *
 * @author Scott Marlow (based on work by Bill Burke)
 */
public class PersistenceUnitMetadataImpl implements PersistenceUnitMetadata {

    // required: name of the persistent unit
    private volatile String name;

    // required: name of the persistent unit scoped to deployment file
    private volatile String scopedName;

    private volatile ArrayList<String> containingModuleName;

    // optional: jndi name of non-jta datasource
    private volatile String nonJtaDataSourceName;

    // optional: jndi name of jta datasource
    private volatile String jtaDataSourceName;


    private volatile DataSource jtaDatasource;

    private volatile DataSource nonJtaDataSource;

    // optional: provider classname (must implement jakarta.persistence.spi.PersistenceProvider)
    private volatile String provider;

    // optional: specifies if EntityManagers will be JTA (default) or RESOURCE_LOCAL
    private volatile PersistenceUnitTransactionType transactionType;

    // optional: collection of individually named managed entity classes
    private volatile List<String> classes = new ArrayList<String>(1);

    // optional:
    private final List<String> packages = new ArrayList<String>(1);

    // optional:  collection of jar file names that contain entity classes
    private volatile List<String> jarFiles = new ArrayList<String>(1);

    private volatile List<URL> jarFilesUrls = new ArrayList<URL>();

    private volatile URL persistenceUnitRootUrl;

    // optional: collection of orm.xml style entity mapping files
    private volatile List<String> mappingFiles = new ArrayList<String>(1);

    // collection of properties for the persistence provider
    private volatile Properties props = new Properties();

    // optional: specifies whether to include entity classes in the root folder containing the persistence unit.
    private volatile boolean excludeUnlistedClasses;

    // optional:  validation mode can be "auto", "callback", "none".
    private volatile ValidationMode validationMode;

    // optional: version of the Jakarta Persistence specification
    private volatile String version;

    // transformers will be written to when the Jakarta Persistence persistence provider adds their transformer.
    // there should be very few calls to add transformers but potentially many calls to get the
    // transformer list (once per application class loaded).
    private final List<ClassTransformer> transformers = new CopyOnWriteArrayList<ClassTransformer>();

    private volatile SharedCacheMode sharedCacheMode;

    private volatile ClassLoader classloader;

    private volatile TempClassLoaderFactory tempClassLoaderFactory;

    private volatile ClassLoader cachedTempClassLoader;

    private final AtomicBoolean onlyCheckIfClassFileTransformerIsNeededOnce = new AtomicBoolean(false);

    @Override
    public void setPersistenceUnitName(String name) {
        this.name = name;
    }

    @Override
    public String getPersistenceUnitName() {
        return name;
    }

    @Override
    public void setScopedPersistenceUnitName(String scopedName) {
        this.scopedName = scopedName;
    }

    @Override
    public String getScopedPersistenceUnitName() {
        return scopedName;
    }

    @Override
    public void setContainingModuleName(ArrayList<String> containingModuleName) {
        this.containingModuleName = containingModuleName;
    }

    @Override
    public ArrayList<String> getContainingModuleName() {
        return containingModuleName;
    }

    @Override
    public void setPersistenceProviderClassName(String provider) {
        if (provider != null && provider.endsWith(".class")) {
            this.provider = provider.substring(0, provider.length() - 6);
        }
        this.provider = provider;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return provider;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public DataSource getJtaDataSource() {
        return jtaDatasource;
    }

    @Override
    public void setJtaDataSource(DataSource jtaDataSource) {
        this.jtaDatasource = jtaDataSource;
    }

    @Override
    public void setNonJtaDataSource(DataSource nonJtaDataSource) {
        this.nonJtaDataSource = nonJtaDataSource;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return nonJtaDataSource;
    }

    @Override
    public void setJtaDataSourceName(String jtaDatasource) {
        this.jtaDataSourceName = jtaDatasource;
    }

    @Override
    public String getJtaDataSourceName() {
        return jtaDataSourceName;
    }

    @Override
    public void setNonJtaDataSourceName(String nonJtaDatasource) {
        this.nonJtaDataSourceName = nonJtaDatasource;
    }

    @Override
    public String getNonJtaDataSourceName() {
        return this.nonJtaDataSourceName;
    }

    @Override
    public void setPersistenceUnitRootUrl(URL persistenceUnitRootUrl) {
        this.persistenceUnitRootUrl = persistenceUnitRootUrl;
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return persistenceUnitRootUrl;
    }

    @Override
    public List<String> getManagedClassNames() {
        return classes;
    }

    @Override
    public void setManagedClassNames(List<String> classes) {
        this.classes = classes;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return excludeUnlistedClasses;
    }

    @Override
    public void setExcludeUnlistedClasses(boolean excludeUnlistedClasses) {
        this.excludeUnlistedClasses = excludeUnlistedClasses;
    }

    @Override
    public void setTransactionType(PersistenceUnitTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    @Override
    public void setMappingFiles(List<String> mappingFiles) {
        this.mappingFiles = mappingFiles;
    }

    @Override
    public List<String> getMappingFileNames() {
        return mappingFiles;
    }

    @Override
    public List<URL> getJarFileUrls() {
        return jarFilesUrls;
    }

    @Override
    public void setJarFileUrls(List<URL> jarFilesUrls) {
        this.jarFilesUrls = jarFilesUrls;
    }


    @Override
    public List<String> getJarFiles() {
        return jarFiles;
    }

    @Override
    public void setJarFiles(List<String> jarFiles) {
        this.jarFiles = jarFiles;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PersistenceUnitMetadataImpl(version=")
            .append(version)
            .append(") [\n")
            .append("\tname: ").append(name).append("\n")
            .append("\tjtaDataSource: ").append(jtaDataSourceName).append("\n")
            .append("\tnonJtaDataSource: ").append(nonJtaDataSourceName).append("\n")
            .append("\ttransactionType: ").append(transactionType).append("\n")
            .append("\tprovider: ").append(provider).append("\n")
            .append("\tclasses[\n");
        if (classes != null) {
            for (String elt : classes) {
                sb.append("\t\t").append(elt);
            }
        }
        sb.append("\t]\n")
            .append("\tpackages[\n");
        if (packages != null) {
            for (String elt : packages) {
                sb.append("\t\t").append(elt).append("\n");
            }
        }
        sb.append("\t]\n")
            .append("\tmappingFiles[\n");
        if (mappingFiles != null) {
            for (String elt : mappingFiles) {
                sb.append("\t\t").append(elt).append("\n");
            }
        }
        sb.append("\t]\n")
            .append("\tjarFiles[\n");
        if (jarFiles != null) {
            for (String elt : jarFiles) {
                sb.append("\t\t").append(elt).append("\n");
            }
        }
        sb.append("\t]\n");
        if (validationMode != null) {
            sb.append("\tvalidation-mode: ").append(validationMode).append("\n");
        }
        if (sharedCacheMode != null) {
            sb.append("\tshared-cache-mode: ").append(sharedCacheMode).append("\n");
        }

        sb.append("\tproperties[\n");

        if (props != null) {
            for (Entry<Object, Object> elt : props.entrySet()) {
                sb.append("\t\t").append(elt.getKey()).append(": ").append(elt.getValue()).append("\n");
            }
        }
        sb.append("\t]").append("]");

        return sb.toString();
    }

    @Override
    public void setValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
    }

    @Override
    public ValidationMode getValidationMode() {
        return validationMode;
    }


    @Override
    public void setProperties(Properties props) {
        this.props = props;
    }

    @Override
    public Properties getProperties() {
        return props;
    }

    @Override
    public void setPersistenceXMLSchemaVersion(String version) {
        this.version = version;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return version;
    }

    @Override
    public void setClassLoader(ClassLoader cl) {
        classloader = cl;
    }

    /**
     * Return a classloader that the provider can use to load the entity classes.
     * <p/>
     * Note from Jakarta Persistence 8.2:
     * All persistence classes defined at the level of the Jakarta EE EAR must be accessible to other Java EE
     * components in the application—i.e. loaded by the application classloader—such that if the same entity
     * class is referenced by two different Jakarta EE components (which may be using different persistence
     * units), the referenced class is the same identical class.
     *
     * @return
     */
    @Override
    public ClassLoader getClassLoader() {
        return classloader;
    }

    @Override
    public List<ClassTransformer> getTransformers() {
        return transformers;
    }

    @Override
    public boolean needsJPADelegatingClassFileTransformer() {
        // WFLY-20393 Ensure that only one internal JPADelegatingClassFileTransformer bytecode transformer is configured for each Persistence Unit
        if (onlyCheckIfClassFileTransformerIsNeededOnce.compareAndSet(false, true)) {
            return Configuration.needClassFileTransformer(this);
        }
        return false;
    }

    @Override
    public void addTransformer(ClassTransformer classTransformer) {
        transformers.add(classTransformer);
        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.tracef("added entity class transformer '%s' for '%s'",
                    classTransformer.getClass().getName(),
                    getScopedPersistenceUnitName());
        }
    }

    @Override
    public void setTempClassLoaderFactory(TempClassLoaderFactory tempClassloaderFactory) {
        this.tempClassLoaderFactory = tempClassloaderFactory;
        cachedTempClassLoader = null;  // always clear the cached temp classloader when a new tempClassloaderFactory is set.
    }

    @Override
    public ClassLoader cacheTempClassLoader() {
        if(cachedTempClassLoader == null && tempClassLoaderFactory != null) {
            cachedTempClassLoader = tempClassLoaderFactory.createNewTempClassLoader();
        }
        return cachedTempClassLoader;
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return tempClassLoaderFactory != null ?
            tempClassLoaderFactory.createNewTempClassLoader() : null;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return sharedCacheMode;
    }

    @Override
    public void setSharedCacheMode(SharedCacheMode sharedCacheMode) {
        this.sharedCacheMode = sharedCacheMode;
    }
}
