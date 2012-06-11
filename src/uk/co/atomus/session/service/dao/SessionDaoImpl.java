/*
 * (C) Copyright Atomus Ltd 2011 - All rights reserved.
 *
 * This software is provided "as is" without warranty of any kind,
 * express or implied, including but not limited to warranties as to
 * quality and fitness for a particular purpose. Atomus Ltd
 * does not support the Software, nor does it warrant that the Software
 * will meet your requirements or that the operation of the Software will
 * be uninterrupted or error free or that any defects will be
 * corrected. Nothing in this statement is intended to limit or exclude
 * any liability for personal injury or death caused by the negligence of
 * Atomus Ltd, its employees, contractors or agents.
 */

package uk.co.atomus.session.service.dao;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import uk.co.atomus.session.TomcatSessionStorageEntity;

import com.microsoft.windowsazure.services.core.ConfigurationException;
import com.microsoft.windowsazure.services.core.storage.CloudStorageAccount;
import com.microsoft.windowsazure.services.core.storage.RetryLinearRetry;
import com.microsoft.windowsazure.services.core.storage.StorageException;
import com.microsoft.windowsazure.services.table.client.CloudTableClient;
import com.microsoft.windowsazure.services.table.client.TableBatchOperation;
import com.microsoft.windowsazure.services.table.client.TableConstants;
import com.microsoft.windowsazure.services.table.client.TableOperation;
import com.microsoft.windowsazure.services.table.client.TableQuery;
import com.microsoft.windowsazure.services.table.client.TableQuery.QueryComparisons;

/**
 * @author Simon Dingle and Chris Derham
 *
 */
public class SessionDaoImpl implements SessionDao {
    private final static Log log = LogFactory.getLog(SessionDaoImpl.class);
    private static final int DEFAULT_RETRY_POLICY_RETRIES = 7;
    private static final int DEFAULT_RETRY_POLICY_INTERVAL_SECONDS = 1;
    private String accountName;
    private String accountKey;
    private CloudTableClient tableClient;
    private CloudStorageAccount cloudStorageAccount;
    private String tableName;
    private int retryPolicyRetries = DEFAULT_RETRY_POLICY_RETRIES;
    private int retryPolicyIntervalSeconds = DEFAULT_RETRY_POLICY_INTERVAL_SECONDS;

    @Override
    public String getAccountName() {
        return accountName;
    }

    @Override
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public String getAccountKey() {
        return accountKey;
    }

    @Override
    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    @Override
    public void setRetryPolicyRetries(int retryPolicyRetries) {
        this.retryPolicyRetries = retryPolicyRetries;
    }

    @Override
    public void setRetryPolicyInterval(int retryPolicyInterval) {
        this.retryPolicyIntervalSeconds = retryPolicyInterval;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    // Set the number of retries that this client will make on its operation
    // This Method will get the tableClient and also creates the table if not exists
    private CloudTableClient getTableClient() {
        if (tableClient == null) {

            tableClient = getCloudStorageAccount().createCloudTableClient();
            tableClient.setRetryPolicyFactory(new RetryLinearRetry(retryPolicyIntervalSeconds,retryPolicyRetries));
            
            try {
                tableClient.createTableIfNotExists(getTableName());
            } catch (com.microsoft.windowsazure.services.core.storage.StorageException ste) {
                ste.printStackTrace();
                throw new RuntimeException(String.format("table '%s' was not created.", getTableName()), ste);
            }
        }
        return tableClient;
    }
    
    // Method to return the cloud storage account
    private CloudStorageAccount getCloudStorageAccount() {
        if (cloudStorageAccount == null) {
            String storageConnectionString =
                    "DefaultEndpointsProtocol=https;" +
                    "AccountName="+ accountName + ";" + 
                    "AccountKey=" + accountKey;
            try {
                cloudStorageAccount = CloudStorageAccount.parse(storageConnectionString);
            } catch (InvalidKeyException e) {
                e.printStackTrace();
                throw new ConfigurationException(String.format("Invalid AccountKey '%s' specified", accountKey));
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new ConfigurationException(String.format("Invalid connection string URI '%s' specified", storageConnectionString));
            }
        }
        return cloudStorageAccount;
    }

    @Override
    public void removeAll(String partitionKey) {
        List<TomcatSessionStorageEntity> entities = queryEntitiesByKeys(partitionKey, null);
        deleteBatch(entities);
    }

    @Override
    public int countEntities(String partitionKey, String rowKey) {
        List<TomcatSessionStorageEntity> entities = queryEntitiesByKeys(partitionKey, rowKey);
        return entities.size();
    }
    
    /**
     * sessionStorageEntity here is updated een for last accessed time
     */
    @Override
    public void updateStorageEntity(TomcatSessionStorageEntity sessionStorageEntity) {
        CloudTableClient tableClient = getTableClient();

        TableOperation updateOperation = TableOperation.insertOrMerge(sessionStorageEntity);
        try {
            tableClient.execute(tableName, updateOperation);
        } catch (com.microsoft.windowsazure.services.core.storage.StorageException ue) {
            ue.printStackTrace();
            throw new RuntimeException(
                    String.format(
                            "Exception while updating the sessionEntity with SessionID '%s'in table storage",
                            sessionStorageEntity.getSessionId()), ue);
        }
    }

    @Override
    public void insertStorageEntity(TomcatSessionStorageEntity sessionStorageEntity) throws StorageException {
        TableOperation insertOperation = TableOperation.insert(sessionStorageEntity);
            getTableClient().execute(getTableName(), insertOperation);
    }
    
    /* (non-Javadoc)
     * @see uk.co.atomus.session.service.dao.SessionDao#queryEntitiesByKeys(java.lang.String, java.lang.String)
     */
    @Override
    public List<TomcatSessionStorageEntity> queryEntitiesByKeys(String partitionKey, String rowKey) {
        
        List<TomcatSessionStorageEntity> storageEntities = new ArrayList<TomcatSessionStorageEntity>();
        CloudTableClient tableClient = getTableClient();
        
        if (null == partitionKey || partitionKey.isEmpty()) {
            // Get list of all Entities in Table 
            TableQuery<TomcatSessionStorageEntity> getAllQuery = TableQuery.from(tableName, TomcatSessionStorageEntity.class);
            for(TomcatSessionStorageEntity entity: tableClient.execute(getAllQuery)) {
                storageEntities.add(entity);
            }
        } else if (null != partitionKey && !partitionKey.isEmpty() && (null == rowKey || rowKey.isEmpty())) {
            // Get list based on Partition Key 
            String partitionFilter =
                TableQuery.generateFilterCondition( TableConstants.PARTITION_KEY, QueryComparisons.EQUAL, partitionKey);
            
            TableQuery<TomcatSessionStorageEntity> getByPartitionQuery =
                    TableQuery.from(tableName, TomcatSessionStorageEntity.class).where(partitionFilter);
            
            for(TomcatSessionStorageEntity entity: tableClient.execute(getByPartitionQuery)) {
                storageEntities.add(entity);
            }
        } else if (null != partitionKey && !partitionKey.isEmpty() && null != rowKey && !rowKey.isEmpty()) {
         // Get list based on partition Key & Row Key
            TomcatSessionStorageEntity entity = retrieveEntity(partitionKey, rowKey);
            if (null != entity ) {
                storageEntities.add(entity);
            }
        } 
        return storageEntities;
    }
    /**
     * 
     * returns null if not found
     */
    @Override
    public TomcatSessionStorageEntity retrieveEntity(String partitionKey, String rowKey) {
        TomcatSessionStorageEntity storageEntity = null;
        TableOperation reterieveOperation = TableOperation.retrieve(partitionKey, rowKey, TomcatSessionStorageEntity.class);
        try {
            storageEntity = getTableClient().execute(tableName, reterieveOperation).getResultAsType();
        } catch (com.microsoft.windowsazure.services.core.storage.StorageException e) {
            e.printStackTrace();
        }
        return storageEntity;
    }
    

    @Override
    public void remove(String partitionKey, String rowKey) {
        TomcatSessionStorageEntity entity = retrieveEntity(partitionKey, rowKey);
        if (null == entity) {
            log.debug("record not deleted as not found with rowKey " + rowKey);
            return;
        }
        deleteTableEntity(entity);
    }

    private void deleteBatch(List<TomcatSessionStorageEntity> entities) {
        if (entities.isEmpty()) {
            return;
        }
        TableBatchOperation batchOperation = new TableBatchOperation();

        for (TomcatSessionStorageEntity entity : entities) {
            log.debug("Added record with rowKey " + entity.getRowKey() + " to batch deletion");
            batchOperation.delete(entity);
        }
        try {
            getTableClient().execute(tableName, batchOperation);
            log.debug("Batch of records deleted successfully");
        } catch (com.microsoft.windowsazure.services.core.storage.StorageException e) {
            e.printStackTrace();
            log.debug("Error while deleting the batch of records");
        }
    }

    private void deleteTableEntity(TomcatSessionStorageEntity entity) {

        if (null != entity) {
            log.debug("deleting record with rowKey" + entity.getRowKey());
            // Create operation to delete the entity
            TableOperation deleteEntity = TableOperation.delete(entity);

            // Call execute method on table client
            try {
                getTableClient().execute(tableName, deleteEntity);
            } catch (com.microsoft.windowsazure.services.core.storage.StorageException e) {
                e.printStackTrace();
                throw new RuntimeException(
                        String.format(
                                "Exception while deleting the sessionEntity with SessionID '%s'in table storage",
                                entity.getSessionId()));
            }
        } else // null
            return;
    }

    @Override
    public void removeExpired(String partitionKey) {
        List<TomcatSessionStorageEntity> entities = queryEntitiesByKeys(partitionKey, null);
        log.debug("found " + entities.size() + " records");
        for (TomcatSessionStorageEntity entity : entities) {
            TomcatSessionStorageEntity sessionStorageEntity = (TomcatSessionStorageEntity) entity;
            if (sessionStorageEntity.hasExpired()) {
                log.debug("found expired record with rowKey" + entity.getRowKey());
                deleteTableEntity(sessionStorageEntity);
            }
        }
    }
}