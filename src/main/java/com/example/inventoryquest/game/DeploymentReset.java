package com.example.inventoryquest.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every new deployed version starts on a fresh mountain. On boot the app compares its build marker
 * to the one stored in {@code deploy_marker}: if it differs (a new build), it wipes the world —
 * every ground item and every player — so no state from the old rules survives, then records the
 * new marker. A plain restart or reboot of the <em>same</em> build finds a matching marker and
 * leaves the world (and everyone's progress) untouched.
 */
@Component
public class DeploymentReset {

    private static final Logger log = LoggerFactory.getLogger(DeploymentReset.class);

    private final JdbcTemplate jdbc;
    private final ObjectProvider<BuildProperties> buildInfo;

    public DeploymentReset(JdbcTemplate jdbc, ObjectProvider<BuildProperties> buildInfo) {
        this.jdbc = jdbc;
        this.buildInfo = buildInfo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void wipeMountainIfNewVersion() {
        String marker = currentMarker();
        String stored = jdbc.query("SELECT marker FROM deploy_marker WHERE id = 1",
                rs -> rs.next() ? rs.getString(1) : null);
        if (marker.equals(stored)) {
            log.info("InventoryQuest build {} — same as the running world; mountain preserved.", marker);
            return;
        }
        int items = jdbc.update("DELETE FROM square_item");
        int players = jdbc.update("DELETE FROM player");
        jdbc.update("INSERT INTO deploy_marker(id, marker) VALUES (1, ?) "
                + "ON CONFLICT (id) DO UPDATE SET marker = EXCLUDED.marker", marker);
        log.info("InventoryQuest build {} is new — wiped the mountain for a fresh ladder "
                + "({} ground items, {} players cleared).", marker, items, players);
    }

    /** The current build's marker — a distinct value per build, or {@code "dev"} with no build info. */
    String currentMarker() {
        BuildProperties bp = buildInfo.getIfAvailable();
        return bp != null && bp.getTime() != null ? "build@" + bp.getTime() : "dev";
    }
}
