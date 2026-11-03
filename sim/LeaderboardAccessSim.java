package cn.bfops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pins that the 656 leaderboard can be opened at any time, and that it says where its rows came from.
 *
 * <p>The reported bug had two halves and this file checks both. The obvious half is reachability: the
 * page was gated three times over — {@code NativeKillIcons.openScoreboard} did nothing unless a match
 * was running, {@code ScoreboardTab.renderContent} returned early and printed
 * "只有在当前对局内才可以查看计分板", and the row that opens it was greyed out in the deploy screen — so a
 * player in the lobby could not reach standings at all.
 *
 * <p>The half that matters more is honesty. Making the page always open is trivial if you are willing
 * to draw whatever is in memory; the constraint is that the server only ever publishes the <em>current
 * room's</em> roster. Out of a match, the only legitimate rows are ones this client already holds, and
 * a table labelled "live" while showing a week-old snapshot is worse than the refusal it replaced. So
 * the source is a first-class value ({@link LeaderboardScope.Source}) and the ranking below is the
 * thing being tested.
 */
public final class LeaderboardAccessSim {
    private static int checks;
    private static final Path SRC = Path.of("src/main/java/cn/bfops");
    private static final Path LANG = Path.of("src/main/resources/assets/battlefield_ops/lang");

    public static void main(String[] args) throws IOException {
        scopeSelectsTheMostTrustworthySource();
        sourceLabelsAreDistinguishable();
        noScreenRefusesTheLeaderboard();
        badgeDoesNotCollideWithTheTabStrip();
        everyEntryPointIsWired();
        restoredRecordsAreNotSilentlyDropped();
        provenanceKeysExistInEveryLocale();
        System.out.println("LeaderboardAccessSim: " + checks + " checks passed");
    }

    // ------------------------------------------------------------------ the decision itself

    private static void scopeSelectsTheMostTrustworthySource() {
        // A live roster always wins, even when it is smaller: three players in the room must show three,
        // not last week's forty.
        check(LeaderboardScope.select(3, 40) == LeaderboardScope.Source.LIVE_ROOM,
                "a live roster beats a larger saved one");
        check(LeaderboardScope.select(1, 0) == LeaderboardScope.Source.LIVE_ROOM, "one live row is live");
        // No live roster but a record on disk: show the record, and label it.
        check(LeaderboardScope.select(0, 40) == LeaderboardScope.Source.LOCAL_CACHE,
                "the saved record is used when nothing is live");
        // Nothing at all. This must be its own state, not an empty LOCAL_CACHE: "we have no record" and
        // "the record is empty" lead to different words on screen.
        check(LeaderboardScope.select(0, 0) == LeaderboardScope.Source.NONE,
                "no live and no saved rows is its own state");
        // The screen is reachable in every one of those states. That is the requirement.
        check(LeaderboardScope.openable(), "the leaderboard may be opened at any time");
        checks++;
    }

    private static void sourceLabelsAreDistinguishable() {
        // Each state needs different words. If two shared a key, the badge would claim a provenance it
        // does not have, which is the one failure mode this feature exists to avoid.
        String live = LeaderboardScope.badgeKey(LeaderboardScope.Source.LIVE_ROOM);
        String cache = LeaderboardScope.badgeKey(LeaderboardScope.Source.LOCAL_CACHE);
        String none = LeaderboardScope.badgeKey(LeaderboardScope.Source.NONE);
        check(!live.equals(cache) && !cache.equals(none) && !live.equals(none),
                "every source has its own label key");
        // Only the saved record is stale, and only it may be described as such.
        check(!LeaderboardScope.Source.LIVE_ROOM.stale(), "a live table is not stale");
        check(LeaderboardScope.Source.LOCAL_CACHE.stale(), "a saved record is stale");
        check(!LeaderboardScope.Source.NONE.stale(), "an empty table is not stale, it is empty");
        check(LeaderboardScope.Source.LIVE_ROOM.hasRows() && LeaderboardScope.Source.LOCAL_CACHE.hasRows(),
                "the two populated sources report that they have rows");
        check(!LeaderboardScope.Source.NONE.hasRows(), "the empty source reports no rows");
        // The empty-table hint differs by why it is empty.
        check(!LeaderboardScope.emptyHintKey(LeaderboardScope.Source.NONE)
                        .equals(LeaderboardScope.emptyHintKey(LeaderboardScope.Source.LOCAL_CACHE)),
                "an unrecorded server and an empty grouping say different things");
        // Requests only need a connection; requiring a match is what made the tab blank between rounds.
        check(LeaderboardScope.requestable(true), "a connected client may ask for standings");
        check(!LeaderboardScope.requestable(false), "a disconnected client may not");
        checks++;
    }

    // ------------------------------------------------------------------ the gates are gone

    /**
     * None of the three removed refusals may come back, in any form.
     *
     * <p>Checked as source text rather than as behaviour because the failure mode is a re-added early
     * return, and there is no JVM-only way to render a screen. The two message strings are checked
     * separately: a refusal that reuses the old wording is the same bug regardless of which method holds
     * it.
     */
    private static void noScreenRefusesTheLeaderboard() throws IOException {
        Path tab = SRC.resolve("killicon/client/gui/tabs/ScoreboardTab.java");
        Path deploy = SRC.resolve("BattleScreen.java");
        Path native_ = SRC.resolve("NativeKillIcons.java");

        // Comments are stripped first. The retired wording is quoted in the Javadoc that explains why it
        // was removed, and that documentation is the point — the assertion is about what the code can
        // still print, not about what the source mentions.
        check(!code(tab).contains("只有在当前对局内才可以查看计分板"),
                "the leaderboard no longer prints a match requirement");
        check(!code(deploy).contains("只有在本局内的玩家可以查看计分板"),
                "the in-match statistics pane no longer refuses outside a match");
        // And the entry point must not consult the match state when opening.
        check(!read(native_).contains("openScoreboard(Screen parent){init();if(inMatch())"),
                "opening the leaderboard is not conditional on a running match");
        check(!code(native_).contains("if(inMatch())Minecraft.getInstance().setScreen"),
                "no match check guards the window being set");

        // The render path must consult the source rather than a match check. This is the positive form
        // of the same assertion: something out-of-match has to decide what to show.
        check(code(tab).contains("renderSourceBadge"), "the tab states where its rows came from");
        check(code(tab).contains("LeaderboardScope.select"), "the tab resolves a source instead of refusing");
        // The refusal and the destructive clear travelled together; neither may return.
        check(!code(tab).contains("if(!cn.bfops.NativeKillIcons.inMatch())"),
                "the render path has no match-state early return");
        checks++;
    }

    /**
     * The provenance badge must sit under the tab strip, not on it.
     *
     * <p>Caught while adding it: the badge was drawn at {@code area2Y1 - 12} while {@code area2Y1} was
     * 34, which put it at y=22 — inside the tab strip, whose hit-test reaches y=30. The text would have
     * been drawn over the tab bodies and the whole strip would have been unreadable.
     */
    private static void badgeDoesNotCollideWithTheTabStrip() throws IOException {
        String tab = code(SRC.resolve("killicon/client/gui/tabs/ScoreboardTab.java"));
        int badgeTop = intConstant(tab, "BADGE_TOP");
        int badgeHeight = intConstant(tab, "BADGE_HEIGHT");
        // The tab strip is 30px tall in ConfigScreenHeader; the measured screen height the boards get
        // must start below the badge.
        check(badgeTop >= 30, "the badge starts below the tab strip, got " + badgeTop);
        check(badgeHeight > 0 && badgeHeight <= 14, "the badge is one line tall, got " + badgeHeight);
        // area2Y1 is derived from the badge, so the boards cannot overlap it either.
        check(tab.contains("this.area2Y1 = BADGE_TOP + BADGE_HEIGHT"),
                "the board area is derived from the badge rather than hardcoded");
        checks++;
    }

    /** Reads an {@code int NAME = value;} constant out of stripped source. */
    private static int intConstant(String source, String name) {
        int at = source.indexOf("int " + name + " =");
        check(at >= 0, "constant present: " + name);
        int end = source.indexOf(';', at);
        String value = source.substring(source.indexOf('=', at) + 1, end).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new AssertionError(name + " is not a literal int, got " + value);
        }
    }

    /**
     * The feature has to be reachable without knowing it exists: a key, the lobby, the deploy screen and
     * the post-match summary. Each is checked where it lives, because "openable in theory" is what the
     * previous state was — the method existed and nothing called it.
     */
    private static void everyEntryPointIsWired() throws IOException {
        // A registered key mapping with a real default binding. Both parts failed before: OPEN_SCOREBOARD
        // was declared with key -1 and never registered with RegisterKeyMappingsEvent, so even a player
        // who bound it by hand got nothing.
        String keys = read(SRC.resolve("killicon/client/KeyBindings.java"));
        check(keys.contains("OPEN_SCOREBOARD"), "a dedicated leaderboard mapping exists");
        check(!keys.contains("OPEN_SCOREBOARD = new KeyMapping(\"key.battlefield_ops.open_scoreboard\", Type.KEYSYM, -1"),
                "the leaderboard mapping has a default key rather than none");
        check(keys.contains("public static KeyMapping[] all()"), "the mappings are exposed for registration");
        String client = read(SRC.resolve("Client.java"));
        check(client.contains("KeyBindings.all()"), "the client registers the leaderboard mapping");
        check(client.contains("KeyBindings.poll("), "the client polls the leaderboard mapping each tick");
        check(!client.contains("KeyBindings.poll(mapMc);\n            if(!inMatch())")
                        && !client.contains("if (Client.inMatch()) cn.bfops.killicon.client.KeyBindings.poll"),
                "polling is not gated on a match");

        // The lobby row and the deploy-screen row. Both were previously either absent or disabled.
        // These are display strings, so the raw text is what matters — `code()` strips literals.
        check(read(SRC.resolve("LobbyScreen.java")).contains("656 详细排行榜"),
                "the lobby offers the leaderboard");
        String deploy = read(SRC.resolve("BattleScreen.java"));
        check(deploy.contains("NativeKillIcons.openScoreboard(this)"), "the deploy screen opens it");
        check(!deploy.contains("\"656 详细排行榜\",\"击杀 / 得分 / 助攻 / 救援与个人分类榜\",inMatch()"),
                "that row is no longer disabled outside a match");
        // Post-match: the summary keeps its buttons and gains a third, so the standings are one click
        // away from the moment the round ends.
        String result = read(SRC.resolve("ResultScreen.java"));
        check(result.contains("NativeKillIcons.openScoreboard(this)"),
                "the result screen opens the leaderboard");
        check(result.contains("Layout.columns(layout.bar.inset(2),3,"),
                "the result screen's action bar has room for three buttons");
        checks++;
    }

    /**
     * The badge and empty-state strings must exist in all four shipped locales.
     *
     * <p>Missing keys do not crash: Minecraft renders the raw key, so the header would read
     * "battlefield_ops.client.gui.config.tab.scoreboard.source.cache" across the top of the table. That
     * is a visible defect and it is cheap to catch here.
     */
    private static void provenanceKeysExistInEveryLocale() throws IOException {
        List<String> keys = List.of(
                LeaderboardScope.badgeKey(LeaderboardScope.Source.LIVE_ROOM),
                LeaderboardScope.badgeKey(LeaderboardScope.Source.LOCAL_CACHE),
                LeaderboardScope.badgeKey(LeaderboardScope.Source.NONE),
                LeaderboardScope.emptyHintKey(LeaderboardScope.Source.NONE),
                LeaderboardScope.emptyHintKey(LeaderboardScope.Source.LOCAL_CACHE));
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "lzh")) {
            String text = read(LANG.resolve(locale + ".json"));
            for (String key : keys) {
                check(text.contains('"' + key + '"'), locale + " translates " + key);
            }
        }
        // The cached badge takes a player count and a timestamp; a translation that drops a placeholder
        // would show "Saved record · %s players" verbatim.
        String en = read(LANG.resolve("en_us.json"));
        check(en.contains("source.cache\": \"Saved record · %s players · last synced %s\""),
                "the cached badge names both the count and the time");
        checks++;
    }

    /**
     * The restored record must survive the two ways the old code silently emptied it.
     *
     * <p>First: {@code LocalLeaderboardCache.mergeAndSave} used to do nothing when the key was null, and
     * the key is only set by {@code load}. The archive path — the one that runs when a room closes —
     * never loads, so on a fresh client it wrote nothing and the feature appeared to have no memory.
     *
     * <p>Second: saved entries carry {@code online=false}, because presence cannot be known for a closed
     * room. The panels filter on that flag, so a player who had once enabled "hide offline" would find
     * every historical table blank, with nothing on screen explaining why.
     */
    private static void restoredRecordsAreNotSilentlyDropped() throws IOException {
        String cache = code(Path.of("src/main/java/cn/bfops/killicon/client/cache/LocalLeaderboardCache.java"));
        check(cache.contains("currentServerKey = resolveServerKey()"),
                "saving resolves its own server key instead of requiring a prior load");
        check(!cache.contains("if (currentServerKey != null) {"),
                "saving no longer refuses when no key was loaded");

        String tab = code(SRC.resolve("killicon/client/gui/tabs/ScoreboardTab.java"));
        check(tab.contains("hideOffline && source == cn.bfops.LeaderboardScope.Source.LIVE_ROOM"),
                "the offline filter applies to a live table only");
        // The archive itself has to be wired, or the record is only written when the tab was opened.
        String client = code(SRC.resolve("Client.java"));
        check(client.contains("NativeKillIcons.archiveRoom()"),
                "the roster is archived when a room closes");
        // And it must run before the state is replaced, or there is nothing left to save.
        int archive = client.indexOf("NativeKillIcons.archiveRoom()");
        int assign = client.indexOf("state = payload;");
        check(archive > 0 && assign > 0 && archive < assign,
                "the archive happens before the outgoing state is overwritten");
        checks++;
    }

    // ------------------------------------------------------------------ plumbing

    private static String read(Path path) throws IOException {
        check(Files.isRegularFile(path), "source file exists: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Source with comments and string literals removed, for assertions about what the code can do.
     *
     * <p>Both matter here: the retired refusal is quoted in the documentation of its own removal, and
     * several Javadocs mention {@code inMatch} while explaining why it no longer gates the tab. Matching
     * raw text would either fail on correct code or, if loosened, stop catching a re-added gate.
     */
    private static String code(Path path) throws IOException {
        String text = read(path);
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                i += 2;
            } else if (c == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\') i++;
                    i++;
                }
                i++;
                out.append("\"\"");
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
