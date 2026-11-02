package cn.bfops.voice.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.FrontVoiceAudioManifest;
import cn.bfops.voice.common.audio.FrontVoiceSoundIds;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.network.S2CConfigSyncPayload;
import cn.bfops.voice.network.VoiceNetwork;
import cn.bfops.voice.server.audio.FrontVoiceAudioEmitter;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class FrontVoiceServerCommands {
    private static final int DEBUG_PERMISSION_LEVEL = 2;
    private static final SimpleCommandExceptionType UNKNOWN_SOUND_EVENT =
        new SimpleCommandExceptionType(Component.literal("Unknown GDFV sound event"));

    private FrontVoiceServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("gdfv")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION_LEVEL))
                .then(
                    Commands.literal("debug")
                        .then(Commands.literal("summary").executes(FrontVoiceServerCommands::runSummary))
                        .then(
                            Commands.literal("list")
                                .executes(context -> runList(context, null))
                                .then(
                                    Commands.argument("filter", StringArgumentType.greedyString())
                                        .executes(context -> runList(context, StringArgumentType.getString(context, "filter")))
                                )
                        )
                        .then(
                            Commands.literal("play")
                                .then(
                                    Commands.literal("local")
                                        .then(
                                            Commands.argument("sound_event", StringArgumentType.string())
                                                .suggests(FrontVoiceServerCommands::suggestSoundEvents)
                                                .executes(context -> runPlayLocal(context, 1.0F, 1.0F))
                                                .then(
                                                    Commands.argument("volume", FloatArgumentType.floatArg(0.0F, 4.0F))
                                                        .executes(context -> runPlayLocal(
                                                            context,
                                                            FloatArgumentType.getFloat(context, "volume"),
                                                            1.0F
                                                        ))
                                                        .then(
                                                            Commands.argument("pitch", FloatArgumentType.floatArg(0.01F, 4.0F))
                                                                .executes(context -> runPlayLocal(
                                                                    context,
                                                                    FloatArgumentType.getFloat(context, "volume"),
                                                                    FloatArgumentType.getFloat(context, "pitch")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("position")
                                        .then(
                                            Commands.argument("sound_event", StringArgumentType.string())
                                                .suggests(FrontVoiceServerCommands::suggestSoundEvents)
                                                .then(
                                                    Commands.argument("position", Vec3Argument.vec3())
                                                        .executes(context -> runPlayPosition(context, 1.0F, 1.0F, 32.0F))
                                                        .then(
                                                            Commands.argument("volume", FloatArgumentType.floatArg(0.0F, 4.0F))
                                                                .executes(context -> runPlayPosition(
                                                                    context,
                                                                    FloatArgumentType.getFloat(context, "volume"),
                                                                    1.0F,
                                                                    32.0F
                                                                ))
                                                                .then(
                                                                    Commands.argument("pitch", FloatArgumentType.floatArg(0.01F, 4.0F))
                                                                        .executes(context -> runPlayPosition(
                                                                            context,
                                                                            FloatArgumentType.getFloat(context, "volume"),
                                                                            FloatArgumentType.getFloat(context, "pitch"),
                                                                            32.0F
                                                                        ))
                                                                        .then(
                                                                            Commands.argument("distance", FloatArgumentType.floatArg(1.0F, 128.0F))
                                                                                .executes(context -> runPlayPosition(
                                                                                    context,
                                                                                    FloatArgumentType.getFloat(context, "volume"),
                                                                                    FloatArgumentType.getFloat(context, "pitch"),
                                                                                    FloatArgumentType.getFloat(context, "distance")
                                                                                ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("entity")
                                        .then(
                                            Commands.argument("sound_event", StringArgumentType.string())
                                                .suggests(FrontVoiceServerCommands::suggestSoundEvents)
                                                .then(
                                                    Commands.argument("entity", EntityArgument.entity())
                                                        .executes(context -> runPlayEntity(context, 1.0F, 1.0F, 32.0F))
                                                        .then(
                                                            Commands.argument("volume", FloatArgumentType.floatArg(0.0F, 4.0F))
                                                                .executes(context -> runPlayEntity(
                                                                    context,
                                                                    FloatArgumentType.getFloat(context, "volume"),
                                                                    1.0F,
                                                                    32.0F
                                                                ))
                                                                .then(
                                                                    Commands.argument("pitch", FloatArgumentType.floatArg(0.01F, 4.0F))
                                                                        .executes(context -> runPlayEntity(
                                                                            context,
                                                                            FloatArgumentType.getFloat(context, "volume"),
                                                                            FloatArgumentType.getFloat(context, "pitch"),
                                                                            32.0F
                                                                        ))
                                                                        .then(
                                                                            Commands.argument("distance", FloatArgumentType.floatArg(1.0F, 128.0F))
                                                                                .executes(context -> runPlayEntity(
                                                                                    context,
                                                                                    FloatArgumentType.getFloat(context, "volume"),
                                                                                    FloatArgumentType.getFloat(context, "pitch"),
                                                                                    FloatArgumentType.getFloat(context, "distance")
                                                                                ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("cue")
                                        .then(
                                            Commands.argument("voice_cue", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (VoiceCue cue : VoiceCue.values()) builder.suggest(cue.name());
                                                    return builder.buildFuture();
                                                })
                                                .then(
                                                    Commands.argument("camp", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (CampType camp : CampType.values()) builder.suggest(camp.name());
                                                            return builder.buildFuture();
                                                        })
                                                        .then(
                                                            Commands.argument("entity", EntityArgument.entity())
                                                                .executes(context -> runPlayCueEntity(context, 1.0F, 1.0F, 32.0F))
                                                                .then(
                                                                    Commands.argument("volume", FloatArgumentType.floatArg(0.0F, 4.0F))
                                                                        .executes(context -> runPlayCueEntity(
                                                                            context,
                                                                            FloatArgumentType.getFloat(context, "volume"),
                                                                            1.0F,
                                                                            32.0F
                                                                        ))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("serverreload").executes(FrontVoiceServerCommands::runServerReload))
        );
    }

    private static int runSummary(CommandContext<CommandSourceStack> context) {
        FrontVoiceAudioManifest manifest = FrontVoiceAudioManifest.get();
        context.getSource().sendSuccess(
            () -> Component.literal(
                "GDFV manifest summary: events=" + manifest.getEventCount()
                    + ", sounds=" + manifest.getImportedSoundCount()
                    + ", skipped=" + manifest.getSkippedFileCount()
            ),
            false
        );
        return 1;
    }

    private static int runList(CommandContext<CommandSourceStack> context, String filter) {
        FrontVoiceAudioManifest manifest = FrontVoiceAudioManifest.get();
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<String> matches = manifest.getEventIds()
            .stream()
            .filter(eventId -> {
                if (normalizedFilter.isBlank()) {
                    return true;
                }
                String displayName = manifest.getDisplayName(eventId).toLowerCase(Locale.ROOT);
                return eventId.toLowerCase(Locale.ROOT).contains(normalizedFilter) || displayName.contains(normalizedFilter);
            })
            .sorted()
            .toList();

        int limit = Math.min(matches.size(), 20);
        context.getSource().sendSuccess(
            () -> Component.literal("GDFV sound events: matched=" + matches.size() + ", showing=" + limit),
            false
        );
        for (int i = 0; i < limit; i++) {
            String eventId = matches.get(i);
            context.getSource().sendSuccess(
                () -> Component.literal(" - " + eventId + " <- " + manifest.getDisplayName(eventId)),
                false
            );
        }
        if (matches.size() > limit) {
            context.getSource().sendSuccess(
                () -> Component.literal(" - ... " + (matches.size() - limit) + " more"),
                false
            );
        }
        return matches.size();
    }

    private static int runPlayLocal(CommandContext<CommandSourceStack> context, float volume, float pitch) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String soundEventId = getSoundEventId(context);
        FrontVoiceAudioEmitter.playLocal(player, soundEventId, volume, pitch);
        context.getSource().sendSuccess(
            () -> Component.literal("Played local sound: " + soundEventId + " volume=" + volume + " pitch=" + pitch),
            false
        );
        return 1;
    }

    private static int runPlayPosition(CommandContext<CommandSourceStack> context, float volume, float pitch, float distance)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String soundEventId = getSoundEventId(context);
        Vec3 position = Vec3Argument.getVec3(context, "position");
        FrontVoiceAudioEmitter.playAtPosition(player, soundEventId, position, volume, pitch, distance);
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Played positioned sound: " + soundEventId + " pos=" + formatVec(position)
                    + " volume=" + volume + " pitch=" + pitch + " distance=" + distance
            ),
            false
        );
        return 1;
    }

    private static int runPlayEntity(CommandContext<CommandSourceStack> context, float volume, float pitch, float distance)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String soundEventId = getSoundEventId(context);
        Entity entity = EntityArgument.getEntity(context, "entity");
        FrontVoiceAudioEmitter.playForEntity(player, soundEventId, entity, volume, pitch, distance);
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Played entity sound: " + soundEventId + " entity=" + entity.getName().getString()
                    + "#" + entity.getId() + " volume=" + volume + " pitch=" + pitch + " distance=" + distance
            ),
            false
        );
        return 1;
    }

    private static int runPlayCueEntity(CommandContext<CommandSourceStack> context, float volume, float pitch, float distance)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String voiceCueStr = StringArgumentType.getString(context, "voice_cue");
        String campStr = StringArgumentType.getString(context, "camp");
        VoiceCue cue = VoiceCue.fromString(voiceCueStr);
        CampType camp = CampType.fromString(campStr);
        Entity entity = EntityArgument.getEntity(context, "entity");

        if (cue == null) {
            context.getSource().sendFailure(Component.literal("Unknown voice cue: " + voiceCueStr));
            return 0;
        }

        FrontVoiceAudioEmitter.playCueForEntity(player, cue, camp, entity, volume, pitch, distance);
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Played cue sound: " + cue + " camp=" + camp + " entity=" + entity.getName().getString()
                    + "#" + entity.getId() + " volume=" + volume + " pitch=" + pitch + " distance=" + distance
            ),
            false
        );
        return 1;
    }

    private static int runServerReload(CommandContext<CommandSourceStack> context) {
        S2CConfigSyncPayload syncPacket = new S2CConfigSyncPayload(
            VoiceConfig.isHudAllowCustomMessages(), VoiceConfig.isHudEnableQuickChat());
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            VoiceNetwork.sendToPlayer(player, syncPacket);
        }
        context.getSource().sendSuccess(
            () -> Component.literal("GDFV server config re-synced to clients"),
            true
        );
        return 1;
    }

    private static String getSoundEventId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String rawSoundEventId = StringArgumentType.getString(context, "sound_event");
        String normalizedEventId = FrontVoiceSoundIds.toManifestEventId(rawSoundEventId);
        if (normalizedEventId == null || !FrontVoiceAudioManifest.get().contains(normalizedEventId)) {
            throw UNKNOWN_SOUND_EVENT.create();
        }
        return normalizedEventId;
    }

    private static CompletableFuture<Suggestions> suggestSoundEvents(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        FrontVoiceAudioManifest manifest = FrontVoiceAudioManifest.get();
        for (String eventId : manifest.getEventIds()) {
            builder.suggest(eventId, Component.literal(manifest.getDisplayName(eventId)));
        }
        return builder.buildFuture();
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec.x, vec.y, vec.z);
    }
}
