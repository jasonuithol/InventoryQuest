package com.example.inventoryquest.game;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The world resets on a new build marker, and is left alone on a matching one. */
class DeploymentResetTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @SuppressWarnings("unchecked")
    private DeploymentReset reset() {
        // No BuildProperties available → the current marker is "dev".
        ObjectProvider<BuildProperties> noBuildInfo = mock(ObjectProvider.class);
        when(noBuildInfo.getIfAvailable()).thenReturn(null);
        return new DeploymentReset(jdbc, noBuildInfo);
    }

    @SuppressWarnings("unchecked")
    private void storedMarker(String value) {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn(value);
    }

    @Test
    void aDifferentBuildWipesTheWorldAndRecordsTheNewMarker() {
        storedMarker("build@some-old-time"); // world belongs to a previous build; current is "dev"
        reset().wipeMountainIfNewVersion();

        verify(jdbc).update("DELETE FROM square_item");
        verify(jdbc).update("DELETE FROM player");
        verify(jdbc).update(startsWith("INSERT INTO deploy_marker"), eq("dev"));
    }

    @Test
    void theSameBuildLeavesTheWorldAlone() {
        storedMarker("dev"); // matches the current marker
        reset().wipeMountainIfNewVersion();

        verify(jdbc, never()).update(startsWith("DELETE"));
    }
}
