/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.node.NodeInfo;
import io.trino.Session;
import io.trino.connector.CatalogName;
import io.trino.metadata.SessionPropertyManager;
import io.trino.spi.QueryId;
import io.trino.spi.resourcegroups.ResourceGroupId;
import io.trino.spi.security.Identity;
import io.trino.spi.session.PropertyMetadata;
import io.trino.spi.session.SessionPropertyConfigurationManagerFactory;
import io.trino.spi.session.TestingSessionPropertyConfigurationManagerFactory;
import io.trino.testing.AllowAllAccessControlManager;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.trino.SystemSessionProperties.HASH_PARTITION_COUNT;
import static io.trino.SystemSessionProperties.JOIN_DISTRIBUTION_TYPE;
import static io.trino.SystemSessionProperties.QUERY_MAX_MEMORY;
import static io.trino.SystemSessionProperties.QUERY_MAX_TOTAL_MEMORY;
import static org.testng.Assert.assertEquals;

public class TestSessionPropertyDefaults
{
    private static final ResourceGroupId TEST_RESOURCE_GROUP_ID = new ResourceGroupId("test");
    private static final NodeInfo TEST_NODE_INFO = new NodeInfo("test");

    @Test
    public void testApplyDefaultProperties()
    {
        SessionPropertyDefaults sessionPropertyDefaults = new SessionPropertyDefaults(TEST_NODE_INFO, new AllowAllAccessControlManager());

        SessionPropertyManager sessionPropertyManager = new SessionPropertyManager();
        sessionPropertyManager.addConnectorSessionProperties(new CatalogName("testCatalog"), ImmutableList.of(
                PropertyMetadata.stringProperty("explicit_set", "Test property", null, false),
                PropertyMetadata.stringProperty("catalog_default", "Test property", null, false)));

        SessionPropertyConfigurationManagerFactory factory = new TestingSessionPropertyConfigurationManagerFactory(
                ImmutableMap.<String, String>builder()
                        .put(QUERY_MAX_MEMORY, "2GB") //Will be overridden
                        .put(QUERY_MAX_TOTAL_MEMORY, "2GB") //Will remain default
                        .build(),
                ImmutableMap.of(
                        "testCatalog",
                        ImmutableMap.<String, String>builder()
                                .put("explicit_set", "override") // Will be overridden
                                .put("catalog_default", "catalog_default") // Will remain default
                                .build()));
        sessionPropertyDefaults.addConfigurationManagerFactory(factory);
        sessionPropertyDefaults.setConfigurationManager(factory.getName(), ImmutableMap.of());

        Session session = Session.builder(sessionPropertyManager)
                .setQueryId(new QueryId("test_query_id"))
                .setIdentity(Identity.ofUser("testUser"))
                .setSystemProperty(QUERY_MAX_MEMORY, "1GB") // Override this default system property
                .setSystemProperty(JOIN_DISTRIBUTION_TYPE, "partitioned")
                .setSystemProperty(HASH_PARTITION_COUNT, "43")
                .setCatalogSessionProperty("testCatalog", "explicit_set", "explicit_set") // Override this default catalog property
                .build();

        assertEquals(session.getSystemProperties(), ImmutableMap.<String, String>builder()
                .put(QUERY_MAX_MEMORY, "1GB")
                .put(JOIN_DISTRIBUTION_TYPE, "partitioned")
                .put(HASH_PARTITION_COUNT, "43")
                .build());
        assertEquals(
                session.getUnprocessedCatalogProperties(),
                ImmutableMap.of(
                        "testCatalog",
                        ImmutableMap.<String, String>builder()
                                .put("explicit_set", "explicit_set")
                                .build()));

        session = sessionPropertyDefaults.newSessionWithDefaultProperties(session, Optional.empty(), TEST_RESOURCE_GROUP_ID);

        assertEquals(session.getSystemProperties(), ImmutableMap.<String, String>builder()
                .put(QUERY_MAX_MEMORY, "1GB") // User provided value overrides default value
                .put(JOIN_DISTRIBUTION_TYPE, "partitioned") // User provided value is used
                .put(HASH_PARTITION_COUNT, "43") // User provided value is used
                .put(QUERY_MAX_TOTAL_MEMORY, "2GB") // Default value is used
                .build());
        assertEquals(
                session.getUnprocessedCatalogProperties(),
                ImmutableMap.of(
                        "testCatalog",
                        ImmutableMap.<String, String>builder()
                                .put("explicit_set", "explicit_set") // User provided value overrides default value
                                .put("catalog_default", "catalog_default") // Default value is used
                                .build()));
    }
}
