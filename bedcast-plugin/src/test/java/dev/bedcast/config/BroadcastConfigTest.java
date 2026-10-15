package dev.bedcast.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BroadcastConfigTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml"),StandardCharsets.UTF_8));
    }
    @Test void invalidSeriesMetadataCannotSilentlyResetTheReportedRound() {
        YamlConfiguration config=config();
        config.set("matches.A.current-round","second");
        config.set("matches.A.total-rounds",5);
        assertThrows(IllegalArgumentException.class,()->new BroadcastConfig(config));
        config.set("matches.A.current-round",6);
        assertThrows(IllegalArgumentException.class,()->new BroadcastConfig(config));
    }
}
