package com.bubbleanyway.data;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

/** Creates the bundled theme catalog without overwriting an existing user file. */
public final class BubbleThemeDefaults {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "config/bubble_anyway/themes.json";

    private BubbleThemeDefaults() {
    }

    public static void ensure(Path configDirectory) {
        Path target = configDirectory.resolve("bubble_anyway/themes.json");
        if (Files.isRegularFile(target)) {
            return;
        }

        try (InputStream input = BubbleThemeDefaults.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                LOGGER.warn("Bundled Bubble Anyway theme catalog is missing: {}", RESOURCE);
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(input, target);
            LOGGER.info("Created default Bubble Anyway theme catalog at {}", target);
        } catch (IOException exception) {
            LOGGER.warn("Could not create default Bubble Anyway theme catalog at {}", target, exception);
        }
    }
}
