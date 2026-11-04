package cn.bfops.voice.common.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import cn.bfops.voice.VoiceMain;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FrontVoiceAudioManifest {
    private static final String MANIFEST_RESOURCE_PATH = "assets/" + VoiceMain.MODID + "/frontvoice_audio_manifest.json";
    private static final ResourceLocation MANIFEST_ID =
        new ResourceLocation(VoiceMain.MODID, "frontvoice_audio_manifest.json");
    private static final FrontVoiceAudioManifest INSTANCE = new FrontVoiceAudioManifest();

    private final Set<String> eventIds = new LinkedHashSet<>();
    private final Map<String, EventMetadata> metadataByEventId = new LinkedHashMap<>();
    private int importedSoundCount;
    private int skippedFileCount;
    private boolean loaded;

    private FrontVoiceAudioManifest() {
    }

    public static FrontVoiceAudioManifest get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public boolean contains(String soundEventId) {
        ensureLoaded();
        return eventIds.contains(soundEventId);
    }

    public int getEventCount() {
        ensureLoaded();
        return eventIds.size();
    }

    public int getImportedSoundCount() {
        ensureLoaded();
        return importedSoundCount;
    }

    public int getSkippedFileCount() {
        ensureLoaded();
        return skippedFileCount;
    }

    public Set<String> getEventIds() {
        ensureLoaded();
        return Collections.unmodifiableSet(eventIds);
    }

    public String getDisplayName(String eventId) {
        ensureLoaded();
        EventMetadata metadata = metadataByEventId.get(eventId);
        return metadata == null ? eventId : metadata.displayName();
    }

    public String getPrimaryOriginalDirectory(String eventId) {
        ensureLoaded();
        EventMetadata metadata = metadataByEventId.get(eventId);
        return metadata == null ? eventId : metadata.primaryOriginalDirectory();
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }

        InputStream stream = openManifestStream();
        if (stream == null) {
            return;
        }

        try (InputStream s = stream; InputStreamReader reader = new InputStreamReader(s, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            importedSoundCount = root.has("importedOggCount") ? root.get("importedOggCount").getAsInt() : 0;
            skippedFileCount = root.has("skippedFileCount") ? root.get("skippedFileCount").getAsInt() : 0;
            JsonArray events = root.getAsJsonArray("events");
            if (events == null) {
                return;
            }

            for (JsonElement eventElement : events) {
                if (!eventElement.isJsonObject()) {
                    continue;
                }
                JsonObject eventObject = eventElement.getAsJsonObject();
                if (!eventObject.has("eventId")) {
                    continue;
                }

                String eventId = eventObject.get("eventId").getAsString();
                String primaryOriginalDirectory = readOptionalString(eventObject, "primaryOriginalDirectory", eventId);
                String displayName = readOptionalString(eventObject, "displayName", primaryOriginalDirectory);
                eventIds.add(eventId);
                metadataByEventId.put(eventId, new EventMetadata(eventId, primaryOriginalDirectory, displayName));
            }
            loaded = true;
        } catch (Exception exception) {
            VoiceMain.LOGGER.error("Failed to load front voice manifest", exception);
        }
    }

    private static String readOptionalString(JsonObject jsonObject, String key, String fallback) {
        if (!jsonObject.has(key)) {
            return fallback;
        }
        JsonElement value = jsonObject.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        String asString = value.getAsString();
        return asString == null || asString.isBlank() ? fallback : asString;
    }

    private static InputStream openManifestStream() {
        InputStream stream = FrontVoiceAudioManifest.class.getClassLoader()
            .getResourceAsStream(MANIFEST_RESOURCE_PATH);
        if (stream != null) {
            return stream;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                ResourceManager resourceManager = mc.getResourceManager();
                Optional<Resource> resource = resourceManager.getResource(MANIFEST_ID);
                if (resource.isPresent()) {
                    return resource.get().open();
                }
            }
        } catch (NoClassDefFoundError ignored) {
            // Server-side: Minecraft class not available, classloader fallback is sufficient
        } catch (Exception exception) {
            VoiceMain.LOGGER.error("Failed to open audio manifest from ResourceManager", exception);
        }
        return null;
    }

    private record EventMetadata(
        String eventId,
        String primaryOriginalDirectory,
        String displayName
    ) {
    }
}
